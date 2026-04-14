package pers.roinflam.kuvalich.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.item.module.item.*;
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
    private static ItemStack findModule(String type, boolean isWeapon, int rarityOrder) {
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
            case 0: return ItemCommonModule.itemStackList;
            case 1: return ItemUncommonModule.itemStackList;
            case 2: return ItemRareModule.itemStackList;
            case 3: return ItemPrimeModule.itemStackList;
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
    private static void ensureListsInitialized(boolean isWeapon) {
        CreativeModeTab.Output noOp = (stack, v) -> {};
        if (isWeapon) {
            if (ItemCommonModule.itemStackList.isEmpty()) ItemCommonModule.registerCreativeTabItems(noOp);
            if (ItemUncommonModule.itemStackList.isEmpty()) ItemUncommonModule.registerCreativeTabItems(noOp);
            if (ItemRareModule.itemStackList.isEmpty()) ItemRareModule.registerCreativeTabItems(noOp);
            if (ItemPrimeModule.itemStackList.isEmpty()) ItemPrimeModule.registerCreativeTabItems(noOp);
        } else {
            if (WarframeCommonModule.itemStackList.isEmpty()) WarframeCommonModule.registerCreativeTabItems(noOp);
            if (WarframeUncommonModule.itemStackList.isEmpty()) WarframeUncommonModule.registerCreativeTabItems(noOp);
            if (WarframeRareModule.itemStackList.isEmpty()) WarframeRareModule.registerCreativeTabItems(noOp);
            if (WarframePrimeModule.itemStackList.isEmpty()) WarframePrimeModule.registerCreativeTabItems(noOp);
        }
    }

    private static List<List<ItemStack>> getWeaponLists() {
        List<List<ItemStack>> lists = new ArrayList<>();
        lists.add(ItemCommonModule.itemStackList);
        lists.add(ItemUncommonModule.itemStackList);
        lists.add(ItemRareModule.itemStackList);
        lists.add(ItemPrimeModule.itemStackList);
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
