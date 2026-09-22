package pers.roinflam.kuvalich.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.event.ModuleDiscoveryHandler;
import pers.roinflam.kuvalich.module.ModulePlacementValidator;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.function.Supplier;

/**
 * 图鉴创造模式一键装配包（客户端 → 服务器）
 *
 * <p>创造模式玩家在图鉴里 Shift+点击一个模组时，请求服务端把它装到
 * <b>当前主手武器</b>（武器模组）或<b>玩家自己</b>（战甲模组）的第一个空槽。
 * 没有可用空槽就什么也不做。</p>
 *
 * <h3>为什么每一层校验都必须在服务端做</h3>
 * <p>客户端说「看起来有空位」不能信 —— 改装客户端可以随便发这个包。所以：</p>
 * <ol>
 *   <li><b>创造模式</b>由服务端判定（照搬 {@link CodexGiveItemPacket} 的做法）。</li>
 *   <li><b>物品实例</b>只从服务端自己的注册列表里按 type 查（复用
 *       {@code CodexGiveItemPacket.findModule}），绝不根据客户端传来的字符串或 NBT
 *       构造物品。这条是整套安全模型的底线。</li>
 *   <li><b>装配目标</b>服务端自己定位：武器取 {@code player.getMainHandItem()}，
 *       战甲取玩家 Capability。包里<b>不带</b>「装到哪个槽 / 哪把武器」这类参数，
 *       没有可伪造的余地。</li>
 *   <li><b>空槽与冲突</b>走 {@link ModulePlacementValidator}，与两个军械库菜单同一套判定 ——
 *       否则会出现「军械库里装不上、一键装却能装进去」这种能写坏存档的不一致。</li>
 *   <li><b>类型与容器匹配</b>再校验一次 {@code instanceof}，不能只信客户端传的
 *       {@code isWeapon} 布尔来决定往哪个容器里塞。</li>
 * </ol>
 *
 * <h3>装完之后必须做的刷新</h3>
 * <p>这个包是直接改 NBT / Capability，没走标准的 {@code Slot.set()} 流程，
 * 所以刷新要自己触发：</p>
 * <ul>
 *   <li>武器侧显式 {@code broadcastChanges()}，否则客户端物品栏与 tooltip
 *       会停留在装之前的样子 —— 看起来像「点了没反应」。</li>
 *   <li>战甲侧主动 {@code WarframeModuleSyncPacket.syncToPlayer}，而不是等
 *       5-tick 轮询兜底。</li>
 *   <li>两侧都要记一次模组发现：一键装配直接进槽位、不经过背包，
 *       不会触发原有的「拾取时发现」路径，漏了图鉴会少标一个「已发现」。</li>
 * </ul>
 *
 * @author RoinFlam
 */
public class CodexInstallModulePacket {

    /** 目标模组的 type 标识 */
    private final String moduleType;
    /** 是否为武器模组（false=战甲） */
    private final boolean isWeapon;
    /** 模组品质序号（0=青铜 1=白银 2=黄金 3=Prime） */
    private final int rarityOrder;

    public CodexInstallModulePacket(String moduleType, boolean isWeapon, int rarityOrder) {
        this.moduleType = moduleType;
        this.isWeapon = isWeapon;
        this.rarityOrder = rarityOrder;
    }

    // ==================== 编解码 ====================

    public static void encode(CodexInstallModulePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.moduleType);
        buf.writeBoolean(msg.isWeapon);
        buf.writeInt(msg.rarityOrder);
    }

    public static CodexInstallModulePacket decode(FriendlyByteBuf buf) {
        return new CodexInstallModulePacket(buf.readUtf(256), buf.readBoolean(), buf.readInt());
    }

    // ==================== 服务端处理 ====================

    public static void handle(CodexInstallModulePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!player.isCreative()) {
                LogUtil.debug("非创造模式玩家 " + player.getName().getString() + " 请求一键装配，已拒绝");
                return;
            }
            if (msg.rarityOrder < 0 || msg.rarityOrder > 3) {
                LogUtil.debug("一键装配收到非法品质序号 " + msg.rarityOrder + "，已丢弃");
                return;
            }

            // 权威实例只从服务端列表里取
            ItemStack found = CodexGiveItemPacket.findModule(msg.moduleType, msg.isWeapon, msg.rarityOrder);
            if (found.isEmpty()) {
                LogUtil.debug("一键装配找不到模组 type=" + msg.moduleType
                        + " isWeapon=" + msg.isWeapon + " rarity=" + msg.rarityOrder);
                return;
            }
            ItemStack module = found.copy();

            boolean installed = msg.isWeapon
                    ? installIntoWeapon(player, module)
                    : installIntoWarframe(player, module);

            if (installed) {
                // 一键装配不经过背包，不会触发「拾取时发现」，这里补记一次
                ModuleDiscoveryHandler.tryDiscoverSingle(player, module);
                LogUtil.debugEvent("图鉴一键装配", player.getName().getString(),
                        (msg.isWeapon ? "武器" : "战甲") + "模组 " + msg.moduleType);
            }
        });
    }

    // ==================== 武器侧 ====================

    /**
     * 把模组写进当前主手武器的第一个可用空槽
     *
     * <p>存储路径与 {@code RequiemWeaponTableMenu.syncWeaponNBT()} 一致：
     * 顶层 NBT 的 {@code <modid>_weaponModules} compound 下的 {@code modules} 列表，
     * 固定 8 项，空槽是一个空 CompoundTag。</p>
     *
     * @param player 玩家
     * @param module 服务端权威实例
     * @return 是否真的装上了
     */
    private static boolean installIntoWeapon(ServerPlayer player, ItemStack module) {
        // 二次确认类型与容器匹配，不信客户端传的 isWeapon
        if (!(module.getItem() instanceof AbstractWeaponModule)) {
            LogUtil.debug("一键装配：客户端声称是武器模组，实际不是，已拒绝");
            return false;
        }

        ItemStack weapon = player.getMainHandItem();
        // hasBase 确认这确实是一把已初始化过的赤毒武器，而不是随手拿的普通物品
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) {
            return false;
        }

        CompoundTag tag = weapon.getOrCreateTag();
        CompoundTag weaponModules = tag.getCompound(Reference.MOD_ID + "_weaponModules");
        final ListTag list = weaponModules.getList("modules", Tag.TAG_COMPOUND);
        // 归一化到 8 项：老武器的列表可能短于 8，直接按下标 set 会越界
        while (list.size() < ModulePlacementValidator.SLOT_COUNT) {
            list.add(new CompoundTag());
        }

        int limit = ModulePlacementValidator.readModuleLimit(weapon);
        int slot = ModulePlacementValidator.firstPlaceableWeaponSlot(
                module, i -> ItemStack.of(list.getCompound(i)), limit);
        if (slot < 0) {
            // 没空位，或与已装的模组冲突 —— 这就是「有空位才行」的服务端判定点
            return false;
        }

        CompoundTag saved = new CompoundTag();
        module.save(saved);
        list.set(slot, saved);
        weaponModules.put("modules", list);
        tag.put(Reference.MOD_ID + "_weaponModules", weaponModules);

        // 直接改 NBT 没走 Slot.set()，必须显式广播，否则客户端物品栏与 tooltip 不会刷新
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
        return true;
    }

    // ==================== 战甲侧 ====================

    /**
     * 把模组写进玩家 Capability 的第一个可用空槽
     *
     * <p>战甲模组不挂在任何护甲物品上，而是挂在玩家身上 —— 没穿甲也照样生效。</p>
     *
     * @param player 玩家
     * @param module 服务端权威实例
     * @return 是否真的装上了
     */
    private static boolean installIntoWarframe(ServerPlayer player, ItemStack module) {
        if (!(module.getItem() instanceof AbstractWarframeModule)) {
            LogUtil.debug("一键装配：客户端声称是战甲模组，实际不是，已拒绝");
            return false;
        }

        final ItemStack finalModule = module;
        final boolean[] ok = { false };
        player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(caps -> {
            int slot = ModulePlacementValidator.firstPlaceableWarframeSlot(
                    finalModule, i -> slotOf(caps, i));
            if (slot < 0) return;
            setSlot(caps, slot, finalModule);
            ok[0] = true;
        });

        if (ok[0]) {
            // 主动同步，不等 5-tick 轮询兜底
            WarframeModuleSyncPacket.syncToPlayer(player);
        }
        return ok[0];
    }

    /**
     * Capability 的八个具名字段按下标读
     *
     * @param caps 战甲模组 Capability
     * @param i    槽位下标 0~7
     * @return 该槽内容
     */
    private static ItemStack slotOf(WarframeModules caps, int i) {
        switch (i) {
            case 0: return caps.getOne();
            case 1: return caps.getTwo();
            case 2: return caps.getThree();
            case 3: return caps.getFour();
            case 4: return caps.getFive();
            case 5: return caps.getSix();
            case 6: return caps.getSeven();
            default: return caps.getEight();
        }
    }

    /**
     * Capability 的八个具名字段按下标写
     *
     * <p>setter 内部用 {@code ItemStack.matches} 做变更检测并 {@code markDirty()}，
     * 这正是 {@code WarframeModuleSyncPacket} 的脏标记来源。</p>
     *
     * @param caps  战甲模组 Capability
     * @param i     槽位下标 0~7
     * @param stack 要写入的模组
     */
    private static void setSlot(WarframeModules caps, int i, ItemStack stack) {
        switch (i) {
            case 0: caps.setOne(stack); break;
            case 1: caps.setTwo(stack); break;
            case 2: caps.setThree(stack); break;
            case 3: caps.setFour(stack); break;
            case 4: caps.setFive(stack); break;
            case 5: caps.setSix(stack); break;
            case 6: caps.setSeven(stack); break;
            default: caps.setEight(stack); break;
        }
    }
}
