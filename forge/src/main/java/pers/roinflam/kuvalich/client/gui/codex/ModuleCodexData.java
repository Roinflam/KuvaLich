package pers.roinflam.kuvalich.client.gui.codex;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 模组图鉴数据提供器
 * Module Codex Data Provider
 *
 * ⭐ 每个条目预计算 searchableText（名称+全部Tooltip文本，小写），
 * 供图鉴搜索框做关键词匹配，避免每帧重复获取Tooltip。
 *
 * ⭐ discoveryKey = type + ":" + rarityOrder，区分不同品质的同type模组。
 */
public class ModuleCodexData {

    public static final int RARITY_ORDER_COMMON = 0;
    public static final int RARITY_ORDER_UNCOMMON = 1;
    public static final int RARITY_ORDER_RARE = 2;
    public static final int RARITY_ORDER_EPIC = 3;

    private static List<CodexEntry> cachedWeaponModules = null;
    private static List<CodexEntry> cachedWarframeModules = null;
    private static boolean initialized = false;

    /**
     * 图鉴条目
     */
    public static class CodexEntry {
        /** 满级展示用ItemStack */
        public final ItemStack displayStack;
        /** 模组type唯一标识 */
        public final String moduleType;
        /** 稀有度 */
        public final Rarity rarity;
        /** 稀有度排序权重 */
        public final int rarityOrder;
        /**
         * ⭐ 发现记录键（type:rarityOrder 格式，区分不同品质的同type模组）
         * Discovery key in "type:rarityOrder" format, distinguishes same type across tiers
         */
        public final String discoveryKey;
        /**
         * ⭐ 可搜索文本（小写，包含名称+全部Tooltip行）
         * 用于搜索框关键词匹配
         */
        public final String searchableText;

        public CodexEntry(ItemStack stack, int rarityOrder) {
            this.displayStack = stack.copy();
            if (ModuleLevelHelper.isLevelSystemEnabled()) {
                ModuleLevelHelper.setModuleLevel(this.displayStack, ModuleLevelHelper.getMaxLevel());
            }
            this.moduleType = AbstractModule.getType(stack);
            this.rarity = stack.getRarity();
            this.rarityOrder = rarityOrder;
            // ⭐ 组合键：type + ":" + rarityOrder / Compound key
            this.discoveryKey = this.moduleType + ":" + rarityOrder;
            this.searchableText = buildSearchText(this.displayStack);
        }

        /**
         * 构建可搜索文本：物品名称 + 全部Tooltip行，小写连接
         * Build searchable text: item name + all tooltip lines, lowercased
         */
        private static String buildSearchText(ItemStack stack) {
            StringBuilder sb = new StringBuilder();
            // 物品显示名称 / Item display name
            sb.append(stack.getHoverName().getString().toLowerCase());
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                    // 获取完整Tooltip（与游戏内一致，其他模组的修改也会包含）
                    // Get full tooltip (same as in-game, includes other mod modifications)
                    List<Component> lines = stack.getTooltipLines(
                            mc.player, TooltipFlag.Default.NORMAL);
                    for (Component line : lines) {
                        sb.append(' ').append(line.getString().toLowerCase());
                    }
                }
            } catch (Exception ignored) {
                // 获取Tooltip失败时仅用名称搜索
            }
            return sb.toString();
        }
    }

    public static List<CodexEntry> getWeaponModules() {
        ensureInitialized();
        return cachedWeaponModules;
    }

    public static List<CodexEntry> getWarframeModules() {
        ensureInitialized();
        return cachedWarframeModules;
    }

    public static void invalidateCache() {
        initialized = false;
        cachedWeaponModules = null;
        cachedWarframeModules = null;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        try {
            forceInitModuleLists();
            cachedWeaponModules = buildWeaponModuleList();
            cachedWarframeModules = buildWarframeModuleList();
            initialized = true;
            LogUtil.debug("模组图鉴数据初始化完成 - 武器: " + cachedWeaponModules.size()
                    + ", 战甲: " + cachedWarframeModules.size());
        } catch (Exception e) {
            LogUtil.error("模组图鉴数据初始化失败", e);
            cachedWeaponModules = new ArrayList<>();
            cachedWarframeModules = new ArrayList<>();
        }
    }

    /**
     * ⭐ 强制初始化各模组类的静态 itemStackList
     * 远程客户端可能尚未触发创造标签构建，列表为空
     */
    private static void forceInitModuleLists() {
        CreativeModeTab.Output noOp = (stack, visibility) -> {};
        if (ItemCommonModule.itemStackList.isEmpty()) ItemCommonModule.registerCreativeTabItems(noOp);
        if (ItemUncommonModule.itemStackList.isEmpty()) ItemUncommonModule.registerCreativeTabItems(noOp);
        if (ItemRareModule.itemStackList.isEmpty()) ItemRareModule.registerCreativeTabItems(noOp);
        if (ItemPrimeModule.itemStackList.isEmpty()) ItemPrimeModule.registerCreativeTabItems(noOp);
        if (WarframeCommonModule.itemStackList.isEmpty()) WarframeCommonModule.registerCreativeTabItems(noOp);
        if (WarframeUncommonModule.itemStackList.isEmpty()) WarframeUncommonModule.registerCreativeTabItems(noOp);
        if (WarframeRareModule.itemStackList.isEmpty()) WarframeRareModule.registerCreativeTabItems(noOp);
        if (WarframePrimeModule.itemStackList.isEmpty()) WarframePrimeModule.registerCreativeTabItems(noOp);
    }

    // ==================== 列表构建 ====================

    private static List<CodexEntry> buildWeaponModuleList() {
        List<CodexEntry> entries = new ArrayList<>();
        collectFromList(ItemCommonModule.itemStackList, RARITY_ORDER_COMMON, entries);
        collectFromList(ItemUncommonModule.itemStackList, RARITY_ORDER_UNCOMMON, entries);
        collectFromList(ItemRareModule.itemStackList, RARITY_ORDER_RARE, entries);
        collectFromList(ItemPrimeModule.itemStackList, RARITY_ORDER_EPIC, entries);
        try {
            CustomModuleManager mgr = CustomModuleManager.getInstance();
            for (int r = 0; r <= 3; r++) {
                Rarity rarity = r == 0 ? Rarity.COMMON : r == 1 ? Rarity.UNCOMMON : r == 2 ? Rarity.RARE : Rarity.EPIC;
                List<ItemStack> custom = new ArrayList<>();
                mgr.addCustomItemModulesToCreativeTab(custom, rarity);
                collectFromList(custom, r, entries);
            }
        } catch (Exception ignored) {}
        entries.sort(Comparator.comparingInt(e -> e.rarityOrder));
        return entries;
    }

    private static List<CodexEntry> buildWarframeModuleList() {
        List<CodexEntry> entries = new ArrayList<>();
        collectFromList(WarframeCommonModule.itemStackList, RARITY_ORDER_COMMON, entries);
        collectFromList(WarframeUncommonModule.itemStackList, RARITY_ORDER_UNCOMMON, entries);
        collectFromList(WarframeRareModule.itemStackList, RARITY_ORDER_RARE, entries);
        collectFromList(WarframePrimeModule.itemStackList, RARITY_ORDER_EPIC, entries);
        try {
            CustomModuleManager mgr = CustomModuleManager.getInstance();
            for (int r = 0; r <= 3; r++) {
                Rarity rarity = r == 0 ? Rarity.COMMON : r == 1 ? Rarity.UNCOMMON : r == 2 ? Rarity.RARE : Rarity.EPIC;
                List<ItemStack> custom = new ArrayList<>();
                mgr.addCustomWarframeModulesToCreativeTab(custom, rarity);
                collectFromList(custom, r, entries);
            }
        } catch (Exception ignored) {}
        entries.sort(Comparator.comparingInt(e -> e.rarityOrder));
        return entries;
    }

    private static void collectFromList(List<ItemStack> src, int rarityOrder, List<CodexEntry> target) {
        if (src == null) return;
        for (ItemStack stack : src) {
            if (stack == null || stack.isEmpty()) continue;
            if (AbstractModule.isRandom(stack)) continue;
            String type = AbstractModule.getType(stack);
            if (type == null || type.isEmpty()) continue;
            target.add(new CodexEntry(stack, rarityOrder));
        }
    }

    // ==================== 工具 ====================

    /** 稀有度显示名 */
    public static String getRarityName(int order) {
        switch (order) {
            case 0: return "\u00A7e\u9752\u94DC";   // §e青铜
            case 1: return "\u00A79\u767D\u94F6";    // §9白银
            case 2: return "\u00A76\u9EC4\u91D1";    // §6黄金
            case 3: return "\u00A7bPrime";            // §bPrime
            default: return "\u00A7f???";
        }
    }

    /** 稀有度指示器颜色（已发现）*/
    public static int getRarityColor(int order) {
        switch (order) {
            case 0: return 0xFFCD7F32;   // 青铜
            case 1: return 0xFFC0C0C0;   // 银
            case 2: return 0xFFFFD700;   // 金
            case 3: return 0xFF55FFFF;   // 青
            default: return 0xFFFFFFFF;
        }
    }

    /** 稀有度指示器颜色（未发现，暗色）*/
    public static int getRarityColorDim(int order) {
        switch (order) {
            case 0: return 0xFF3D2610;
            case 1: return 0xFF303030;
            case 2: return 0xFF4D4000;
            case 3: return 0xFF1A4D4D;
            default: return 0xFF333333;
        }
    }
}
