package pers.roinflam.kuvalich.network.message;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 战甲模组完整同步包（服务器 → 客户端）
 * Warframe Module Full Sync Packet (Server → Client)
 *
 * 将玩家当前装备的8个战甲模组槽 + 所有击杀叠层计数一次性同步到客户端。
 * 客户端使用同步的原始数据本地计算属性，与服务端使用完全相同的计算逻辑，
 * 从根本上消除客户端/服务端属性值不一致导致的回弹问题。
 *
 * 替代原 DiggingSpeedPacket 的逐属性定时同步方案。
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

    // ==================== 包字段 ====================

    /** 8个模组槽数据 */
    private final ItemStack[] modules;

    /**
     * 击杀叠层计数，索引对应 {@link WarframeModuleHandler#WARFRAME_STACK_TYPES}
     */
    private final int[] killStacks;

    // ==================== 服务端状态追踪 ====================

    /** 每个玩家的上次同步状态哈希（用于脏检测，仅服务端使用） */
    private static final Map<UUID, Integer> SERVER_STATE_HASH = new ConcurrentHashMap<>();

    // ==================== 构造 ====================

    /**
     * 构造同步包
     *
     * @param modules    8个模组槽数据
     * @param killStacks 击杀叠层计数数组，索引对应 WARFRAME_STACK_TYPES
     */
    public WarframeModuleSyncPacket(ItemStack[] modules, int[] killStacks) {
        this.modules = modules;
        this.killStacks = killStacks;
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
        int[] killStacks = new int[stackTypeCount];
        for (int i = 0; i < stackTypeCount; i++) {
            killStacks[i] = buf.readVarInt();
        }
        return new WarframeModuleSyncPacket(modules, killStacks);
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

        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WarframeModuleSyncPacket(modules, stacks)
        );

        // 更新状态哈希
        SERVER_STATE_HASH.put(player.getUUID(), computeStateHash(modules, stacks));
    }

    /**
     * 检测数据变化，仅在变化时发送同步包
     * 用于定期轮询场景（每5tick调用一次）
     *
     * @param player 目标玩家
     * @return true=有变化并已同步，false=无变化
     */
    public static boolean syncIfChanged(ServerPlayer player) {
        if (player == null) return false;

        ItemStack[] modules = collectServerModules(player);
        int[] stacks = collectServerKillStacks(player);
        int currentHash = computeStateHash(modules, stacks);

        Integer lastHash = SERVER_STATE_HASH.get(player.getUUID());
        if (lastHash != null && lastHash == currentHash) {
            return false;
        }

        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WarframeModuleSyncPacket(modules, stacks)
        );

        SERVER_STATE_HASH.put(player.getUUID(), currentHash);
        return true;
    }

    /**
     * 清理服务端指定玩家的状态追踪数据（退出时调用）
     *
     * @param playerUUID 玩家UUID
     */
    public static void cleanupPlayer(UUID playerUUID) {
        SERVER_STATE_HASH.remove(playerUUID);
    }

    // ==================== 内部方法 ====================

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

    /**
     * 计算状态哈希（用于脏检测）
     * 结合模组槽 NBT 和叠层计数，碰撞概率极低
     */
    private static int computeStateHash(ItemStack[] modules, int[] stacks) {
        int hash = 17;
        for (ItemStack stack : modules) {
            if (stack != null && !stack.isEmpty()) {
                hash = 31 * hash + Item.getId(stack.getItem());
                CompoundTag tag = stack.getTag();
                if (tag != null) {
                    hash = 31 * hash + tag.hashCode();
                }
            } else {
                hash = 31 * hash;
            }
        }
        hash = 31 * hash + Arrays.hashCode(stacks);
        return hash;
    }

    private static ItemStack safe(ItemStack stack) {
        return stack != null ? stack : ItemStack.EMPTY;
    }
}
