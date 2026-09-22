package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.item.module.weapon.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 图鉴创造模式给予物品包（客户端 → 服务器）
 * Codex creative mode give item packet (Client → Server)
 *
 * 创造模式玩家在图鉴中点击模组时，发送此包请求服务器给予对应模组。
 * 服务端验证：1.必须创造模式 2.moduleType必须在已注册列表中（防伪造）
 * 背包满则掉落在玩家脚下。
 *
 * ⭐ 修复：增加 rarityOrder 字段，优先搜索对应品质列表，
 *    避免不同品质共享同一type时返回低品质模组。
 */
public class CodexGiveItemPacket {

    /** 目标模组的type标识 / Target module type identifier */
    private final String moduleType;
    /** 是否为武器模组（false=战甲）/ Is weapon module (false=warframe) */
    private final boolean isWeapon;
    /** ⭐ 模组品质序号（0=青铜 1=白银 2=黄金 3=Prime）/ Rarity order */
    private final int rarityOrder;

    public CodexGiveItemPacket(String moduleType, boolean isWeapon, int rarityOrder) {
        this.moduleType = moduleType;
        this.isWeapon = isWeapon;
        this.rarityOrder = rarityOrder;
    }

    // ==================== 编解码 / Encode & Decode ====================

    public static void encode(CodexGiveItemPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.moduleType);
        buf.writeBoolean(msg.isWeapon);
        buf.writeInt(msg.rarityOrder);
    }

    public static CodexGiveItemPacket decode(FriendlyByteBuf buf) {
        return new CodexGiveItemPacket(buf.readUtf(256), buf.readBoolean(), buf.readInt());
    }

    // ==================== 服务端处理 / Server Handler ====================

    public static void handle(CodexGiveItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ⭐ 安全校验：必须是创造模式
            if (!player.isCreative()) {
                LogUtil.warn("非创造模式玩家 " + player.getName().getString()
                        + " 尝试通过图鉴获取物品，已拒绝");
                return;
            }

            // ⭐ 安全校验：rarityOrder 合法性
            if (msg.rarityOrder < 0 || msg.rarityOrder > 3) {
                LogUtil.debug("图鉴给予：非法品质序号 rarityOrder=" + msg.rarityOrder);
                return;
            }

            // 查找对应模组 / Find matching module
            ItemStack found = findModule(msg.moduleType, msg.isWeapon, msg.rarityOrder);
            if (found.isEmpty()) {
                LogUtil.debug("图鉴给予：未找到模组 type=" + msg.moduleType
                        + " rarityOrder=" + msg.rarityOrder);
                return;
            }

            // 给予物品：优先放背包，满了掉脚下
            // Give item: prefer inventory, drop at feet if full
            ItemStack give = found.copy();
            if (!player.getInventory().add(give)) {
                ItemEntity drop = new ItemEntity(
                        player.level(),
                        player.getX(), player.getY(), player.getZ(),
                        give);
                drop.setNoPickUpDelay();
                player.level().addFreshEntity(drop);
                LogUtil.debug("图鉴给予：背包已满，物品掉落在脚下");
            }

            LogUtil.debugEvent("图鉴创造给予", player.getName().getString(),
                    "type=" + msg.moduleType + " rarity=" + msg.rarityOrder
                            + " item=" + found.getHoverName().getString());
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== 模组查找 / Module Lookup ====================

    /**
     * 从已注册的静态列表中查找指定type和品质的模组
     * Find module with given type and rarity from registered static lists
     *
     * ⭐ 优先搜索 rarityOrder 对应的品质列表，找不到再搜索全部列表。
     * ⭐ Prioritizes the list matching rarityOrder, falls back to full search.
     *
     * 只返回已注册的合法模组，防止伪造type字符串获取非法物品。
     * Only returns legitimately registered modules, prevents forged type strings.
     *
     * @param type        模组type标识
     * @param isWeapon    是否搜索武器模组列表
     * @param rarityOrder 品质序号（0=青铜 1=白银 2=黄金 3=Prime）
     * @return 匹配的模组ItemStack，未找到返回EMPTY
     */
    /**
     * 按 type 在**服务端自己的**注册列表里找权威实例
     *
     * <p>包内可见是为了让 {@link CodexInstallModulePacket} 复用同一套查找 ——
     * 两个包都必须走「绝不根据客户端传来的字符串或 NBT 直接构造物品」这条底线，
     * 各写一份迟早漂移。</p>
     *
     * <p>⭐ 修复：原先只搜八个内置原型池，漏了自定义模组。
     * {@code ModuleCodexData.buildWeaponModuleList/buildWarframeModuleList} 会通过
     * {@code CustomModuleManager} 把整合包作者在 custom_modules.json 里配的模组
     * 一并放进图鉴，也就是**图鉴里点得到**；但服务端这边找不到，于是创造模式点击自定义模组
     * 完全没反应、也没有任何提示。现在与图鉴的取数口径对齐。</p>
     *
     * @param type        模组 type 标识
     * @param isWeapon    true=武器模组 false=战甲模组
     * @param rarityOrder 品质序号 0~3
     * @return 服务端的权威实例副本；找不到返回 {@link ItemStack#EMPTY}
     */
    static ItemStack findModule(String type, boolean isWeapon, int rarityOrder) {
        if (type == null || type.isEmpty()) return ItemStack.EMPTY;

        // 确保列表已初始化 / Ensure lists are initialized
        ensureListsInitialized(isWeapon);

        // ⭐ 优先搜索指定品质的列表 / Priority: search the target rarity list first
        List<ItemStack> targetList = isWeapon
                ? getWeaponListByRarity(rarityOrder)
                : getWarframeListByRarity(rarityOrder);
        if (targetList != null) {
            ItemStack result = searchInList(targetList, type);
            if (!result.isEmpty()) return result;
        }

        // ⭐ 自定义模组：与 ModuleCodexData 的取数口径一致（先查目标品质，再兜底全品质）
        ItemStack custom = searchCustomModules(type, isWeapon, rarityOrder);
        if (!custom.isEmpty()) return custom;

        // 兜底：全列表搜索（rarityOrder不匹配或目标列表中没找到时）
        // Fallback: search all lists
        List<List<ItemStack>> lists = isWeapon ? getWeaponLists() : getWarframeLists();
        for (List<ItemStack> list : lists) {
            if (list == targetList) continue; // 跳过已搜索的列表 / Skip already searched list
            ItemStack result = searchInList(list, type);
            if (!result.isEmpty()) return result;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 在自定义模组里按 type 查找
     *
     * <p>品质序号到 {@code Rarity} 的映射与
     * {@code ModuleCodexData.buildWeaponModuleList} 逐字一致（0=COMMON 1=UNCOMMON
     * 2=RARE 3=EPIC），否则会出现「图鉴里是金卡、服务端按铜卡去找」的错位。</p>
     *
     * @param type        模组 type 标识
     * @param isWeapon    true=武器模组
     * @param rarityOrder 优先搜索的品质序号
     * @return 找到的实例；没有返回 {@link ItemStack#EMPTY}
     */
    private static ItemStack searchCustomModules(String type, boolean isWeapon, int rarityOrder) {
        try {
            pers.roinflam.kuvalich.config.custom.CustomModuleManager mgr =
                    pers.roinflam.kuvalich.config.custom.CustomModuleManager.getInstance();
            // 先目标品质，再其余品质兜底
            for (int pass = 0; pass < 2; pass++) {
                for (int r = 0; r <= 3; r++) {
                    boolean isTarget = (r == rarityOrder);
                    if (pass == 0 && !isTarget) continue;
                    if (pass == 1 && isTarget) continue;
                    net.minecraft.world.item.Rarity rarity = r == 0 ? net.minecraft.world.item.Rarity.COMMON
                            : r == 1 ? net.minecraft.world.item.Rarity.UNCOMMON
                            : r == 2 ? net.minecraft.world.item.Rarity.RARE
                            : net.minecraft.world.item.Rarity.EPIC;
                    List<ItemStack> custom = new ArrayList<>();
                    if (isWeapon) {
                        mgr.addCustomItemModulesToCreativeTab(custom, rarity);
                    } else {
                        mgr.addCustomWarframeModulesToCreativeTab(custom, rarity);
                    }
                    ItemStack result = searchInList(custom, type);
                    if (!result.isEmpty()) return result;
                }
            }
        } catch (Exception e) {
            LogUtil.error("在自定义模组中查找 " + type + " 时出错", e);
        }
        return ItemStack.EMPTY;
    }

    /**
     * 在单个列表中搜索指定type的模组
     * Search for a module with given type in a single list
     */
    private static ItemStack searchInList(List<ItemStack> list, String type) {
        for (ItemStack stack : list) {
            if (stack == null || stack.isEmpty()) continue;
            if (AbstractModule.isRandom(stack)) continue;
            String t = AbstractModule.getType(stack);
            if (type.equals(t)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 根据品质序号获取对应的武器模组列表
     * Get weapon module list by rarity order
     */
    private static List<ItemStack> getWeaponListByRarity(int rarityOrder) {
        switch (rarityOrder) {
            case 0: return WeaponCommonModule.itemStackList;
            case 1: return WeaponUncommonModule.itemStackList;
            case 2: return WeaponRareModule.itemStackList;
            case 3: return WeaponPrimeModule.itemStackList;
            default: return null;
        }
    }

    /**
     * 根据品质序号获取对应的战甲模组列表
     * Get warframe module list by rarity order
     */
    private static List<ItemStack> getWarframeListByRarity(int rarityOrder) {
        switch (rarityOrder) {
            case 0: return WarframeCommonModule.itemStackList;
            case 1: return WarframeUncommonModule.itemStackList;
            case 2: return WarframeRareModule.itemStackList;
            case 3: return WarframePrimeModule.itemStackList;
            default: return null;
        }
    }

    /**
     * 确保模组静态列表已初始化（服务端可能未触发创造标签构建）
     */
    static void ensureListsInitialized(boolean isWeapon) {
        CreativeModeTab.Output noOp = (stack, v) -> {};
        if (isWeapon) {
            if (WeaponCommonModule.itemStackList.isEmpty()) WeaponCommonModule.registerCreativeTabItems(noOp);
            if (WeaponUncommonModule.itemStackList.isEmpty()) WeaponUncommonModule.registerCreativeTabItems(noOp);
            if (WeaponRareModule.itemStackList.isEmpty()) WeaponRareModule.registerCreativeTabItems(noOp);
            if (WeaponPrimeModule.itemStackList.isEmpty()) WeaponPrimeModule.registerCreativeTabItems(noOp);
        } else {
            if (WarframeCommonModule.itemStackList.isEmpty()) WarframeCommonModule.registerCreativeTabItems(noOp);
            if (WarframeUncommonModule.itemStackList.isEmpty()) WarframeUncommonModule.registerCreativeTabItems(noOp);
            if (WarframeRareModule.itemStackList.isEmpty()) WarframeRareModule.registerCreativeTabItems(noOp);
            if (WarframePrimeModule.itemStackList.isEmpty()) WarframePrimeModule.registerCreativeTabItems(noOp);
        }
    }

    private static List<List<ItemStack>> getWeaponLists() {
        List<List<ItemStack>> lists = new ArrayList<>();
        lists.add(WeaponCommonModule.itemStackList);
        lists.add(WeaponUncommonModule.itemStackList);
        lists.add(WeaponRareModule.itemStackList);
        lists.add(WeaponPrimeModule.itemStackList);
        return lists;
    }

    private static List<List<ItemStack>> getWarframeLists() {
        List<List<ItemStack>> lists = new ArrayList<>();
        lists.add(WarframeCommonModule.itemStackList);
        lists.add(WarframeUncommonModule.itemStackList);
        lists.add(WarframeRareModule.itemStackList);
        lists.add(WarframePrimeModule.itemStackList);
        return lists;
    }
}
