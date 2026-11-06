package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.utils.KuvaPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 裂罅循环的新旧词条对照：两张裂罅按词条键一一对上，算出每条是变好、变差、一样、新出现还是没了。
 * 数值和卡面一样先乘等级倍率、按卡面的写法取整（百分比取整、固定值取整、秒杀概率留三位小数），
 * 「变好 / 变差」按取整后的数比，卡面上看着一样的不算涨跌。数值越大越好（负面词条本来就是负数）
 */
@OnlyIn(Dist.CLIENT)
final class RivenCompare {

    static final int UP = 0;
    static final int DOWN = 1;
    static final int SAME = 2;
    static final int NEW = 3;
    static final int LOST = 4;

    /** 一条词条：键、名字、乘过等级倍率的数值 */
    record Attr(String key, String name, double value) {
    }

    /** 对照的一行：旧值 / 新值没有时是 null */
    record Row(String key, String name, Double old, Double nw, int kind) {
    }

    private RivenCompare() {
    }

    /** 一张裂罅的词条（卡面顺序） */
    static List<Attr> attrs(ItemStack s) {
        List<Attr> out = new ArrayList<>();
        if (s.isEmpty()) {
            return out;
        }
        boolean warframe = s.getItem() instanceof WarframeRivenModule;
        double mult = ModuleLevelHelper.getEffectiveMultiplier(s);
        for (Map.Entry<String, Double> e : AbstractModule.getAttributes(s)) {
            String key = e.getKey();
            String base = warframe ? "kuvaweapon.warframe_attribute_type." : "kuvaweapon.item_attribute_type.";
            Component name;
            if (key.startsWith("killStack")) {
                int max = warframe ? AbstractWarframeModule.getMaxStacksForAttribute(key)
                        : AbstractWeaponModule.getMaxStacksForAttribute(key);
                name = Component.translatable(base + key, max);
            } else {
                name = Component.translatable(base + key);
            }
            out.add(new Attr(key, name.getString(), e.getValue() * mult));
        }
        return out;
    }

    /** 新卡对旧卡逐条对照：新卡的词条按新卡顺序在前，旧卡有、新卡没有的排在后面 */
    static List<Row> compare(List<Attr> orig, List<Attr> cand) {
        List<Row> rows = new ArrayList<>(cand.size() + 2);
        for (Attr a : cand) {
            Attr o = find(orig, a.key());
            if (o == null) {
                rows.add(new Row(a.key(), a.name(), null, a.value(), NEW));
                continue;
            }
            long d = shown(a.key(), a.value()) - shown(a.key(), o.value());
            rows.add(new Row(a.key(), a.name(), o.value(), a.value(), d > 0 ? UP : d < 0 ? DOWN : SAME));
        }
        for (Attr o : orig) {
            if (find(cand, o.key()) == null) {
                rows.add(new Row(o.key(), o.name(), o.value(), null, LOST));
            }
        }
        return rows;
    }

    private static Attr find(List<Attr> list, String key) {
        for (Attr a : list) {
            if (a.key().equals(key)) {
                return a;
            }
        }
        return null;
    }

    private static boolean fixed(String key) {
        return key.equals("fixedHealth") || key.equals("fixedShield") || key.equals("fixedArmor");
    }

    /** 卡面上显示的那个整数（秒杀概率按千分之一个百分点） */
    private static long shown(String key, double v) {
        if (fixed(key)) {
            return Math.round(v);
        }
        if (key.equals("execute_chance")) {
            return Math.round(v * 100000);
        }
        return Math.round(v * 100);
    }

    /** 卡面写法：「+142%」「-35%」「+120」 */
    static String fmt(String key, double v) {
        String sign = v >= 0 ? "+" : "";
        return sign + num(key, v) + (fixed(key) ? "" : "%");
    }

    /** 不带正负号和单位的数（涨跌幅用绝对值） */
    static String num(String key, double v) {
        if (fixed(key)) {
            return String.valueOf(Math.round(v));
        }
        if (key.equals("execute_chance")) {
            String t = String.format("%.3f", v * 100);
            if (t.indexOf('.') >= 0) {
                t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return t;
        }
        return String.valueOf(Math.round(v * 100));
    }

    /** 涨跌幅（绝对值，卡面单位，不带 %） */
    static String delta(Row r) {
        if (r.old() == null || r.nw() == null) {
            return "";
        }
        return num(r.key(), Math.abs(r.nw() - r.old()));
    }

    /** 一行对照的颜色：变好绿、变差 / 负面红、新词条紫、失去灰 */
    static int rgb(Row r) {
        if (r.kind() == LOST) {
            return FxPalette.ASH;
        }
        if (r.nw() != null && r.nw() < 0) {
            return KuvaPalette.DANGER;
        }
        return switch (r.kind()) {
            case UP -> KuvaPalette.SUCCESS;
            case DOWN -> KuvaPalette.DANGER;
            case NEW -> RivenCycleView.RIVEN;
            default -> FxPalette.TEXT_MID;
        };
    }
}
