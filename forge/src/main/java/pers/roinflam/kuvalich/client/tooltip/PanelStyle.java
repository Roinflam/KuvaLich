package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.config.TooltipConfig;
import pers.roinflam.kuvalich.module.weapon.panel.AttrSpec;
import pers.roinflam.kuvalich.module.weapon.panel.PanelGroup;

/**
 * 面板的视觉基元：装订线、组前缀、名称查找、颜色
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class PanelStyle {

    /**
     * 面板符号
     *
     * <p>⭐ 这几个字符靠原版的 unifont 回退渲染（项目里已在用的 █ ░ ● ○ 同理）。
     * 整合包若自带字体包并去掉了 unifont 回退，它们会变成豆腐块 ——
     * 所以提供 ASCII 备选，由 {@code panel.symbolStyle} 配置切换。</p>
     */
    private static final String GUTTER_UNICODE = "▎";
    private static final String GUTTER_ASCII = "|";
    private static final String ARROW_UNICODE = "→";
    private static final String ARROW_ASCII = "->";
    private static final char DOT_FILLED_UNICODE = '●';
    private static final char DOT_FILLED_ASCII = '*';
    private static final char DOT_EMPTY_UNICODE = '○';
    private static final char DOT_EMPTY_ASCII = 'o';
    private static final String MODULE_SEP_UNICODE = "·";
    private static final String MODULE_SEP_ASCII = ", ";

    private static boolean ascii() {
        return TooltipConfig.PANEL.symbolStyle.get() == TooltipConfig.SymbolStyle.ASCII;
    }

    /** 装订线字符 */
    public static String gutter() {
        return ascii() ? GUTTER_ASCII : GUTTER_UNICODE;
    }

    /** 「基础 → 含叠层」的箭头 */
    public static String arrow() {
        return ascii() ? ARROW_ASCII : ARROW_UNICODE;
    }

    /** 叠层进度条：实心 */
    public static char dotFilled() {
        return ascii() ? DOT_FILLED_ASCII : DOT_FILLED_UNICODE;
    }

    /** 叠层进度条：空心 */
    public static char dotEmpty() {
        return ascii() ? DOT_EMPTY_ASCII : DOT_EMPTY_UNICODE;
    }

    /** 模组名之间的分隔符 */
    public static String moduleSeparator() {
        return ascii() ? MODULE_SEP_ASCII : MODULE_SEP_UNICODE;
    }

    /** 组前缀：装订线 + 组标题 + 两个空格 */
    public static Component groupPrefix(PanelGroup group) {
        MutableComponent c = Component.empty();
        if (TooltipConfig.PANEL.showGutter.get()) {
            c.append(Component.literal(gutter()).withStyle(group.gutterColor()));
        }
        c.append(Component.translatable(group.titleKey()).withStyle(group.titleColor()));
        c.append(Component.literal("  "));
        return c;
    }

    /** 续行前缀：与组前缀等宽的空白（保留装订线，让整组在视觉上连成一块） */
    public static Component groupContinuation(PanelGroup group) {
        Component gutterComponent = TooltipConfig.PANEL.showGutter.get()
                ? Component.literal(gutter()).withStyle(group.gutterColor())
                : Component.empty();
        return ChipPacker.matchingIndent(groupPrefix(group), gutterComponent);
    }

    /**
     * 属性短名（紧凑视图用）
     *
     * <p>短名缺失时逐级回落，并截断到第一个换行符前 ——
     * 项目里 {@code kuvaweapon.item_attribute_type.*} 有不少是整句描述，
     * 直接拿来当 chip 名会一行吃掉半个屏幕。</p>
     */
    public static String shortName(AttrSpec spec) {
        return shortNameOf(spec.key());
    }

    /**
     * 按属性 key 查短名，三级回落
     *
     * <p>⭐ 回落链必须包含 {@code kuvaweapon.item_attribute_type.<key>}：
     * {@code meleeCriticalStrikeProbability} 这类「成对 spec 的输入 key」
     * 以及整合包自定义词条，在 {@code kuvalich.attr.short.*} 和 {@code item.module.*}
     * 两个命名空间里都没有条目，只有这个命名空间有。
     * 少了这一级的话 CTRL 来源视图会把
     * {@code item.module.meleeCriticalStrikeProbability} 这串原文当名字画出来。</p>
     *
     * <p>三级都查不到时返回**裸 key**（如 {@code myCustomStat}）而不是完整的翻译键路径 ——
     * 对整合包作者来说，看到自己写的 key 名比看到一串 {@code item.module.xxx} 有用得多。</p>
     */
    public static String shortNameOf(String key) {
        String shortKey = "kuvalich.attr.short." + key;
        if (I18n.exists(shortKey)) {
            return I18n.get(shortKey);
        }
        String longKey = "item.module." + key;
        if (I18n.exists(longKey)) {
            return firstLine(I18n.get(longKey));
        }
        String descKey = "kuvaweapon.item_attribute_type." + key;
        if (I18n.exists(descKey)) {
            return firstLine(I18n.get(descKey));
        }
        String warframeKey = "kuvaweapon.warframe_attribute_type." + key;
        if (I18n.exists(warframeKey)) {
            return firstLine(I18n.get(warframeKey));
        }
        return key;
    }

    /** 兼容旧签名：显式给一对 key 时仍按原来的两级回落 */
    public static String shortName(String shortKey, String longKey) {
        if (I18n.exists(shortKey)) {
            return I18n.get(shortKey);
        }
        if (I18n.exists(longKey)) {
            return firstLine(I18n.get(longKey));
        }
        return longKey;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl > 0 ? s.substring(0, nl) : s;
    }

    /** 叠层进度条 */
    public static Component progress(int current, int max) {
        int cur = Math.max(0, current);
        int cap = Math.max(1, max);
        // ⚠️ 分母来自客户端本地配置（ModConfig 是 COMMON，不随服同步），
        //    服主改过上限时可能出现 cur > cap，这里钳制以免画出 8/5 这种自相矛盾的进度。
        cur = Math.min(cur, cap);

        if (TooltipConfig.PANEL.stackStyle.get() == TooltipConfig.StackStyle.NUMERIC || cap > 10) {
            // 上限超过 10 时圆点会把行撑宽，退化成纯数字
            return Component.literal(cur + "/" + cap)
                    .withStyle(PanelPalette.bold(cur >= cap ? PanelPalette.STACK_ACTIVE : PanelPalette.VALUE));
        }

        StringBuilder filled = new StringBuilder();
        for (int i = 0; i < cur; i++) {
            filled.append(dotFilled());
        }
        StringBuilder empty = new StringBuilder();
        for (int i = cur; i < cap; i++) {
            empty.append(dotEmpty());
        }

        MutableComponent bar = Component.empty();
        if (filled.length() > 0) {
            bar.append(Component.literal(filled.toString())
                    .withStyle(PanelPalette.style(PanelPalette.DOT_ON)));
        }
        if (empty.length() > 0) {
            // ⭐ 空心圆点要能看见「还剩几格」，原版 DARK_GRAY 在深色背景上直接消失
            bar.append(Component.literal(empty.toString())
                    .withStyle(PanelPalette.style(PanelPalette.DOT_OFF)));
        }
        bar.append(Component.literal(" " + cur + "/" + cap)
                .withStyle(PanelPalette.bold(cur >= cap ? PanelPalette.STACK_ACTIVE : PanelPalette.VALUE)));
        return bar;
    }

    private PanelStyle() {
    }
}
