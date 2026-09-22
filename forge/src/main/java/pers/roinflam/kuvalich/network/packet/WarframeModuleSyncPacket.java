package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 战甲模组完整同步包（服务器 → 客户端）
 * Warframe Module Full Sync Packet (Server → Client)
 *
 * 将玩家当前装备的8个战甲模组槽 + 所有击杀叠层计数一次性同步到客户端。
 * 客户端使用同步的原始数据本地计算属性，与服务端使用完全相同的计算逻辑。
 *
 * <p>⭐ 性能修复（本次）：脏检测不再对 8 个 ItemStack 做 {@code tag.hashCode()} 全树递归。</p>
 *
 * <p>问题：原 {@code computeStateHash} 每 5 tick、每玩家都要深度遍历 8 个模组的完整 NBT 树。
 * 60 人在线约为每秒 1920 次深度 hash，纯粹是为了「判断有没有变」而烧的 CPU。</p>
 *
 * <p>修复：模组侧改用 {@link WarframeModules#getVersion()} 脏标记（内容变化时才递增，
 * 比较只是一次 int），叠层侧仍用 {@code Arrays.hashCode(int[])}（12 个 int，可忽略）。</p>
 *
 * 同步时机：
 * 1. 玩家登录
 * 2. 玩家重生/维度传送（Clone事件后）
 * 3. 击杀叠层变化（击杀时立即同步）
 * 4. 每5tick定期脏检测（兜底：模组槽变更、叠层衰减等）
 */
public class WarframeModuleSyncPacket {

    /** 模组槽数量 */
    private static final int MODULE_SLOT_COUNT = 8;

    /** capability 缺失时使用的版本号占位值（保证每次都判定为脏并强制同步） */
    private static final int VERSION_UNAVAILABLE = Integer.MIN_VALUE;

    // ==================== 包字段 ====================

    /** 8个模组槽数据 */
    private final ItemStack[] modules;

    /**
     * 击杀叠层计数，索引对应 {@link WarframeModuleHandler#WARFRAME_STACK_TYPES}
     */
    private final int[] killStacks;
    /** ⭐ 武器类叠层数量，顺序与 {@link KillStackManager#WEAPON_STACK_TYPES} 一致 */
    private final int[] weaponStacks;

    // ==================== 服务端状态追踪 ====================

    /**
     * 每个玩家的上次同步状态（仅服务端使用）
     * <p>[0] = 模组版本号，[1] = 击杀叠层 hash</p>
     */
    private static final Map<UUID, int[]> SERVER_STATE = new ConcurrentHashMap<>();

    // ==================== 构造 ====================

    /**
     * 构造同步包
     *
     * @param modules    8个模组槽数据
     * @param killStacks 击杀叠层计数数组，索引对应 WARFRAME_STACK_TYPES
     */
    public WarframeModuleSyncPacket(ItemStack[] modules, int[] killStacks) {
        this(modules, killStacks, new int[KillStackManager.WEAPON_STACK_TYPES.length]);
    }

    /**
     * 完整构造函数
     *
     * @param modules      战甲模组
     * @param killStacks   战甲类叠层数量（顺序与 {@code WARFRAME_STACK_TYPES} 一致）
     * @param weaponStacks 武器类叠层数量（顺序与 {@link KillStackManager#WEAPON_STACK_TYPES} 一致）
     */
    public WarframeModuleSyncPacket(ItemStack[] modules, int[] killStacks, int[] weaponStacks) {
        this.modules = modules;
        this.killStacks = killStacks;
        this.weaponStacks = weaponStacks != null ? weaponStacks : new int[KillStackManager.WEAPON_STACK_TYPES.length];
    }

    // ==================== 编解码 ====================

    /**
     * 编码到字节缓冲
     */
    public static void encode(WarframeModuleSyncPacket pkt, FriendlyByteBuf buf) {
        for (int i = 0; i < MODULE_SLOT_COUNT; i++) {
            buf.writeItem(pkt.modules[i] != null ? pkt.modules[i] : ItemStack.EMPTY);
        }
        int stackTypeCount = WarframeModuleHandler.WARFRAME_STACK_TYPES.length;
        buf.writeVarInt(stackTypeCount);
        for (int i = 0; i < stackTypeCount; i++) {
            buf.writeVarInt(i < pkt.killStacks.length ? pkt.killStacks[i] : 0);
        }

        // ⭐ 武器类叠层：数量 + 各项，顺序见 KillStackManager.WEAPON_STACK_TYPES
        int weaponTypeCount = KillStackManager.WEAPON_STACK_TYPES.length;
        buf.writeVarInt(weaponTypeCount);
        for (int i = 0; i < weaponTypeCount; i++) {
            buf.writeVarInt(i < pkt.weaponStacks.length ? pkt.weaponStacks[i] : 0);
        }
    }

    /**
     * 从字节缓冲解码
     */
    public static WarframeModuleSyncPacket decode(FriendlyByteBuf buf) {
        ItemStack[] modules = new ItemStack[MODULE_SLOT_COUNT];
        for (int i = 0; i < MODULE_SLOT_COUNT; i++) {
            modules[i] = buf.readItem();
        }
        int stackTypeCount = buf.readVarInt();
        // 防御性限制：叠层类型数量必须在合理范围内，避免恶意包造成巨量分配
        if (stackTypeCount < 0 || stackTypeCount > 256) {
            stackTypeCount = 0;
        }
        int[] killStacks = new int[stackTypeCount];
        for (int i = 0; i < stackTypeCount; i++) {
            killStacks[i] = buf.readVarInt();
        }

        // ⭐ 武器类叠层
        int weaponTypeCount = buf.readVarInt();
        if (weaponTypeCount < 0 || weaponTypeCount > 256) {
            weaponTypeCount = 0;
        }
        int[] weaponStacks = new int[weaponTypeCount];
        for (int i = 0; i < weaponTypeCount; i++) {
            weaponStacks[i] = buf.readVarInt();
        }
        return new WarframeModuleSyncPacket(modules, killStacks, weaponStacks);
    }

    /**
     * 处理网络包
     */
    public static void handle(WarframeModuleSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(pkt));
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 客户端处理：将数据交给 WarframeModuleHandler 管理
     */
    private static void handleClient(WarframeModuleSyncPacket pkt) {
        // 过滤空槽位，生成计算用列表
        List<ItemStack> validModules = new ArrayList<>();
        for (ItemStack stack : pkt.modules) {
            if (stack != null && !stack.isEmpty()) {
                validModules.add(stack.copy());
            }
        }
        // 交给 Handler 管理客户端缓存
        WarframeModuleHandler.onClientSyncReceived(validModules, pkt.killStacks);

        // ⭐ 武器类叠层镜像：本地玩家 UUID 只能在客户端取，本方法已由 DistExecutor 限定在客户端执行
        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer != null) {
            KillStackManager.onClientSyncReceived(localPlayer.getUUID(), pkt.weaponStacks);
        } else {
            LogUtil.debug("[叠层同步] 收到同步包时本地玩家为空，已跳过武器叠层镜像更新");
        }
    }

    // ==================== 服务端发送接口 ====================

    /**
     * 立即向指定玩家发送完整同步包（无条件发送）
     * 用于登录、重生、击杀叠层变化等确定需要同步的场景
     *
     * @param player 目标玩家
     */
    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) return;

        ItemStack[] modules = collectServerModules(player);
        int[] stacks = collectServerKillStacks(player);
        int[] weaponStacks = KillStackManager.collectWeaponStacks(player);
        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WarframeModuleSyncPacket(modules, stacks, weaponStacks)
        );
        SERVER_STATE.put(player.getUUID(), new int[]{getModuleVersion(player), Arrays.hashCode(stacks),
                Arrays.hashCode(weaponStacks)});
    }

    /**
     * 检测数据变化，仅在变化时发送同步包
     * 用于定期轮询场景（每5tick调用一次）
     *
     * <p>⭐ 模组侧用版本号（O(1) int 比较），叠层侧用 12 个 int 的数组 hash，
     * 不再做任何 NBT 深度遍历。</p>
     *
     * @param player 目标玩家
     * @return true=有变化并已同步，false=无变化
     */
    public static boolean syncIfChanged(ServerPlayer player) {
        if (player == null) return false;

        int version = getModuleVersion(player);
        int[] stacks = collectServerKillStacks(player);
        int stackHash = Arrays.hashCode(stacks);
        // ⭐ 武器类叠层也纳入"有没有变化"的判断，叠层增减或衰减时才重发
        int[] weaponStacks = KillStackManager.collectWeaponStacks(player);
        int weaponHash = Arrays.hashCode(weaponStacks);
        int[] last = SERVER_STATE.get(player.getUUID());
        if (last != null && last.length >= 3 && last[0] == version && last[1] == stackHash
                && last[2] == weaponHash && version != VERSION_UNAVAILABLE) {
            return false;
        }
        ItemStack[] modules = collectServerModules(player);
        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WarframeModuleSyncPacket(modules, stacks, weaponStacks)
        );
        SERVER_STATE.put(player.getUUID(), new int[]{version, stackHash, weaponHash});
        return true;
    }

    /**
     * 清理服务端指定玩家的状态追踪数据（退出时调用）
     *
     * @param playerUUID 玩家UUID
     */
    public static void cleanupPlayer(UUID playerUUID) {
        SERVER_STATE.remove(playerUUID);
    }

    // ==================== 内部方法 ====================

    /**
     * ⭐ 读取玩家战甲模组 capability 的版本号（脏标记）
     *
     * @param player 目标玩家
     * @return 版本号；capability 不可用时返回 {@link #VERSION_UNAVAILABLE}
     */
    private static int getModuleVersion(ServerPlayer player) {
        WarframeModules wm = player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElse(null);
        return wm != null ? wm.getVersion() : VERSION_UNAVAILABLE;
    }

    /**
     * 从服务端 capability 收集8个模组槽数据
     */
    private static ItemStack[] collectServerModules(ServerPlayer player) {
        ItemStack[] result = new ItemStack[MODULE_SLOT_COUNT];
        Arrays.fill(result, ItemStack.EMPTY);

        WarframeModules wm = player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElse(null);
        if (wm == null) return result;

        result[0] = safe(wm.getOne());
        result[1] = safe(wm.getTwo());
        result[2] = safe(wm.getThree());
        result[3] = safe(wm.getFour());
        result[4] = safe(wm.getFive());
        result[5] = safe(wm.getSix());
        result[6] = safe(wm.getSeven());
        result[7] = safe(wm.getEight());

        return result;
    }

    /**
     * 从服务端收集所有战甲击杀叠层计数
     */
    private static int[] collectServerKillStacks(ServerPlayer player) {
        KillStackManager.StackType[] types = WarframeModuleHandler.WARFRAME_STACK_TYPES;
        int[] counts = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            counts[i] = KillStackManager.getStacks(player, types[i]);
        }
        return counts;
    }

    private static ItemStack safe(ItemStack stack) {
        return stack != null ? stack : ItemStack.EMPTY;
    }
}
