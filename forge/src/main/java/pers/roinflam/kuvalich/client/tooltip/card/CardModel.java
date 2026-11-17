package pers.roinflam.kuvalich.client.tooltip.card;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeCommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframePrimeModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponCommonModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponPrimeModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRareModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponUncommonModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一张模组卡要画的内容：档位、卡名、词条、裂罅倾向性和循环次数、等级点、卡面图主题。悬停开始时建一次。
 */
final class CardModel {

    private static final String ARCHON_KEY_PREFIX = "kuvaweapon.warframe_module.executioner_";
    private static final String WEAPON_KEY_PREFIX = "kuvaweapon.item_module.";
    private static final String WARFRAME_KEY_PREFIX = "kuvaweapon.warframe_module.";

    record Attr(String value, String name, boolean negative) {
    }

    final CardTier tier;
    final boolean warframe;
    /** 图标贴图的品质名：common / uncommon / rare / prime / riven */
    final String rarity;
    final boolean veiled;
    final String name;
    final List<Attr> attrs;
    final boolean riven;
    final int trend;
    final int cycle;
    /** 点亮几颗等级点（0～10） */
    final int level;
    /** 卡面图主题，紫卡按词条去重后轮播，其余只有一个 */
    final List<String> themes;

    private CardModel(CardTier tier, boolean warframe, String rarity, boolean veiled, String name, List<Attr> attrs,
                      boolean riven, int trend, int cycle, int level, List<String> themes) {
        this.tier = tier;
        this.warframe = warframe;
        this.rarity = rarity;
        this.veiled = veiled;
        this.name = name;
        this.attrs = attrs;
        this.riven = riven;
        this.trend = trend;
        this.cycle = cycle;
        this.level = level;
        this.themes = themes;
    }

    /** 不是模组卡返回 null */
    static CardModel of(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof AbstractModule module)) {
            return null;
        }
        boolean warframe = module.isWarframe();
        String rarity;
        CardTier tier;
        if (item instanceof WeaponCommonModule || item instanceof WarframeCommonModule) {
            rarity = "common";
            tier = CardTier.BRONZE;
        } else if (item instanceof WeaponUncommonModule || item instanceof WarframeUncommonModule) {
            rarity = "uncommon";
            tier = CardTier.SILVER;
        } else if (item instanceof WeaponRareModule || item instanceof WarframeRareModule) {
            rarity = "rare";
            tier = CardTier.GOLD;
        } else if (item instanceof WeaponPrimeModule || item instanceof WarframePrimeModule) {
            rarity = "prime";
            tier = CardTier.PRIME;
        } else if (item instanceof WeaponRivenModule || item instanceof WarframeRivenModule) {
            rarity = "riven";
            tier = CardTier.RIVEN;
        } else {
            return null;
        }

        Component hover = stack.getHoverName();
        if (hover.getContents() instanceof TranslatableContents tc && tc.getKey().startsWith(ARCHON_KEY_PREFIX)) {
            tier = CardTier.ARCHON;
        }
        String name = ChatFormatting.stripFormatting(hover.getString());
        if (name == null) {
            name = "";
        }

        boolean veiled = AbstractModule.isRandom(stack);
        boolean riven = tier == CardTier.RIVEN;
        int trend = 0;
        int cycle = 0;
        if (riven) {
            trend = warframe ? WarframeRivenModule.getTrend(stack) : WeaponRivenModule.getTrend(stack);
            cycle = warframe ? WarframeRivenModule.getCycle(stack) : WeaponRivenModule.getCycle(stack);
        }

        int level = 10;
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            int max = Math.max(1, ModuleLevelHelper.getMaxLevel());
            level = Math.max(0, Math.min(10, Math.round(10.0F * ModuleLevelHelper.getModuleLevel(stack) / max)));
        }

        List<Attr> attrs = new ArrayList<>();
        List<String> themes = new ArrayList<>();
        if (!veiled) {
            double levelMult = ModuleLevelHelper.getEffectiveMultiplier(stack);
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(stack)) {
                String key = e.getKey();
                double v = e.getValue() * levelMult;
                attrs.add(new Attr(formatValue(key, v, warframe), attributeName(key, warframe), v < 0));
                String theme = HoverCardArt.themeOf(key);
                if (theme != null && !themes.contains(theme)) {
                    themes.add(theme);
                }
            }
        }
        String own = riven || veiled ? null : HoverCardArt.cardArt(cardId(hover));
        if (own != null) {
            themes.clear();
            themes.add(own);
        } else if (themes.isEmpty()) {
            themes.add(HoverCardArt.defaultTheme(tier));
        } else if (!riven && themes.size() > 1) {
            themes.subList(1, themes.size()).clear();
        }
        return new CardModel(tier, warframe, rarity, veiled, name, attrs, riven, trend, cycle, level, themes);
    }

    // 卡 id 取名字的语言键末段，不能用 kuvalich_modules.type：Prime / 执刑官版和普通版的 type 是同一个
    private static String cardId(Component hover) {
        if (hover.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key.startsWith(WEAPON_KEY_PREFIX)) {
                return key.substring(WEAPON_KEY_PREFIX.length());
            }
            if (key.startsWith(WARFRAME_KEY_PREFIX)) {
                return key.substring(WARFRAME_KEY_PREFIX.length());
            }
        }
        return null;
    }

    /** 和物品自带 tooltip 的数值写法一致（AbstractWeaponModule / AbstractWarframeModule） */
    private static String formatValue(String key, double v, boolean warframe) {
        if (warframe && (key.equals("fixedHealth") || key.equals("fixedShield") || key.equals("fixedArmor"))) {
            return "+" + (int) Math.round(v);
        }
        String prefix = v >= 0 ? "+" : "";
        if (!warframe && key.equals("execute_chance")) {
            String p = String.format("%.3f", v * 100);
            if (p.indexOf('.') >= 0) {
                p = p.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return prefix + p + "%";
        }
        return prefix + (int) Math.round(v * 100) + "%";
    }

    private static String attributeName(String key, boolean warframe) {
        String prefix = warframe ? "kuvaweapon.warframe_attribute_type." : "kuvaweapon.item_attribute_type.";
        if (key.startsWith("killStack")) {
            int max = warframe ? AbstractWarframeModule.getMaxStacksForAttribute(key)
                    : AbstractWeaponModule.getMaxStacksForAttribute(key);
            return ChatFormatting.stripFormatting(Component.translatable(prefix + key, max).getString());
        }
        return ChatFormatting.stripFormatting(Component.translatable(prefix + key).getString());
    }
}
