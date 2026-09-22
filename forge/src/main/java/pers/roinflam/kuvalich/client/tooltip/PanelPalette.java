package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.module.weapon.panel.PanelGroup;
import pers.roinflam.kuvalich.utils.KuvaPalette;

/**
 * 面板配色 —— 24 位 RGB，不受原版那 16 个 {@code ChatFormatting} 限制
 *
 * <p><b>为什么要换掉 ChatFormatting</b>：原版只有 16 个固定颜色，
 * 其中能当「次要信息」用的只有 {@code DARK_GRAY}（{@code #555555}）和
 * {@code GRAY}（{@code #AAAAAA}）。前者在深色 tooltip 背景上几乎糊成一团 ——
 * 分组标题、单位、未激活的叠层这些本该「能读但不抢眼」的内容全都变成了「读不出来」。
 * {@code Style#withColor(TextColor)} 支持完整 RGB，直接选几个对比度合适的就行。</p>
 *
 * <p><b>配色思路</b>：三档明度构成层次 ——
 * <ul>
 *   <li><b>数值</b>（最亮）：玩家扫视时第一眼要看到的</li>
 *   <li><b>属性名</b>（中）：读得清，但不与数值抢</li>
 *   <li><b>标题 / 单位 / 提示</b>（次要，但仍然读得清）：
 *       比原版 {@code DARK_GRAY} 亮一大截，这是本次修的核心问题</li>
 * </ul>
 * 每个分组再给一个自己的色相，让标题除了「是个标题」之外还能一眼区分是哪一组。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class PanelPalette {

    // ==================== 基础三档 ====================

    /** 属性名：读得清、但不与数值抢（原版 GRAY #AAAAAA 偏灰，这个偏冷、对比更好） */
    public static final int LABEL = 0xC2C8D2;

    /** 次要信息：单位、分隔符、未激活状态、按键提示正文 —— 明确比 DARK_GRAY(#555) 亮 */
    public static final int MUTED = 0x8A93A3;

    /** 更次要：括号里的补充、已被压到背景的内容 */
    public static final int FAINT = 0x6B7382;

    // ==================== 数值 ====================

    /** 面板核心数值（基伤 / 暴击 / 暴伤 / 触发） */
    public static final int VALUE = 0xF2F4F8;

    /** 正向增益 */
    public static final int BONUS = 0x7FE08A;

    /** 负向词条（裂罅惩罚） */
    public static final int PENALTY = 0xFF7B7B;

    /** 叠层抬高后的值（箭头右边那个数） */
    public static final int STACKED = 0x6FD8E8;

    /** 叠层当前加成 / 满层 */
    public static final int STACK_ACTIVE = 0xFFC861;

    /** 警告：被配置上限截断、行数超限 */
    public static final int WARN = 0xFFB066;

    /** Forma 锁定 */
    public static final int LOCKED = 0xE05C5C;

    /**
     * 分组背景条
     *
     * <p>ARGB，透明度刻意压得很低：它的作用是「让相邻两组一眼分得开」，
     * 不是要成为一个视觉元素。</p>
     *
     * <p>⚠️ 这条是在 {@code renderImage} 里画的，而 1.20.1 的
     * {@code GuiGraphics#renderTooltipInternal} 是先 {@code renderText} 再
     * {@code renderImage}（两者同在 z=400 的 pose 里，文字进的是延迟批次）。
     * 所以绘制时要把 z 往后挪一点让它落到文字下面；即便哪天被别的模组换掉渲染流程、
     * z 没压住，这个透明度下也只是一层很淡的洗色，不会糊住字。</p>
     */
    public static final int GROUP_BAND = 0x16FFFFFF;

    // ==================== 叠层进度条 ====================

    /** 已点亮的圆点 */
    public static final int DOT_ON = 0xFFC861;
    /** 未点亮的圆点 —— 要能看见「还剩几格」，不能像原版 DARK_GRAY 那样直接消失 */
    public static final int DOT_OFF = 0x5A6270;

    // ==================== 分组标题 ====================

    /**
     * 各分组标题的色相
     *
     * <p>标题统一比数值暗一档（它是结构不是内容），但各有色相，
     * 扫视时不用读字就知道翻到哪一组了。</p>
     */
    public static int header(PanelGroup group) {
        return switch (group) {
            case PANEL -> 0xA8C8F0;      // 淡蓝：最终面板
            case STACK -> 0xE8B75C;      // 琥珀：击杀叠层
            case ELEMENT -> 0xC0A0E8;    // 淡紫：元素
            case DAMAGE -> 0xE89898;     // 淡红：伤害增益
            case GUN -> 0x88C4D8;        // 青：枪械
            case RARE -> 0xE8CC80;       // 金：稀有效果
            case EXTRA -> 0x9CD8A8;      // 淡绿：额外槽位
            case MODULES -> 0xA0A8B4;    // 灰蓝：模组名单
            case OTHER -> 0xA0A8B4;      // 灰蓝：未登记词条
        };
    }

    /** 分组内数值的默认色（负值一律走 {@link #PENALTY}） */
    public static int value(PanelGroup group) {
        return switch (group) {
            case PANEL -> VALUE;
            case STACK -> STACK_ACTIVE;
            case RARE -> 0xE8CC80;
            case MODULES, OTHER -> LABEL;
            default -> BONUS;
        };
    }

    // ==================== 元素 ====================

    /**
     * 元素专属色
     *
     * <p>⭐ 起因：{@code KuvaWeaponUtil.getColor} 原先返回 {@code ChatFormatting}，
     * 里面有 {@code DARK_RED}（#AA0000）、{@code DARK_GREEN}（#00AA00）、
     * {@code DARK_GRAY}（#555555）、{@code DARK_BLUE}（#0000AA）这几个在深色 tooltip 背景上
     * 几乎读不出来的颜色 —— 而元素恰恰是玩家靠颜色认的。</p>
     *
     * <p>现在那边也改成 RGB 了，并且两边共用同一张表：表本体在双端安全的
     * {@link pers.roinflam.kuvalich.utils.KuvaPalette#element(String)}，本方法只做委托。
     * 必须这样是因为 {@code KuvaWeaponUtil} 与 {@code AbstractWeaponModule} 都是双端类，
     * 而本类是 {@code @OnlyIn(Dist.CLIENT)} 的，它们不能反过来依赖本类。</p>
     *
     * <p>色相沿用原来的语义（火=橙红、冰=青、毒=绿、电=蓝紫…），
     * 只是把明度提上来，所以不会破坏玩家已有的辨认习惯。</p>
     */
    public static int element(String type) {
        // 表已搬到双端安全的 KuvaPalette，这里只委托：
        // KuvaWeaponUtil / AbstractWeaponModule 都是双端类，用不了本类（@OnlyIn(Dist.CLIENT)），
        // 而它们需要的正是同一批元素色。全仓库只留一份表，避免两处各自漂移。
        return KuvaPalette.element(type);
    }

    // ==================== 工具 ====================

    public static Style style(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    public static Style bold(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true);
    }

    public static Style italic(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(true);
    }

    private PanelPalette() {
    }
}
