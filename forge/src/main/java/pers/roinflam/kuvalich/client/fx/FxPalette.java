package pers.roinflam.kuvalich.client.fx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 世界特效统一用的颜色（0xRRGGBB）。界面已有的常量（指南的猩红、科技青、余烬等）原样搬过来，
 * 世界里的光效向它们看齐；新加的几个是同一色相的深 / 亮 / 白热档。
 *
 * <p>特效里不出现这张表以外的颜色，也不用纯红 0xFF0000。</p>
 *
 * <p><b>挨得近、但有意留着的色对</b>（{@code tools/fx/palette_de.py} 按同屏出现的几组算 CIEDE2000，ΔE &lt; 15 的都在这里；
 * 改了颜色要重跑）：</p>
 * <ul>
 *   <li>电击 / 科技青（5.1）、磁力 / 科技青（7.7）、电击 / 冰（9.8）、冰 / 科技青（10.2）、冰 / 磁力（10.6）、
 *       电击 / 磁力（11.2）、毒气 / 磁力（13.5）：靠形状和明度分——电是折线电弧、磁力是横向断续带、冰是冰壳加冰晶、
 *       毒气是大团云、护盾是球面；电击和科技青还差一点就低于 5，再往青里挪就要把电改到 {@code #2CB8ED}；</li>
 *   <li>密语金 / 二档暴击（5.3）、强调金 / 二档暴击（8.8）、强调金 / 密语金（10.9）：都是「好事」，
 *       密语是飞行的小数据光点，暴击是星芒，金色的满级、死亡抵抗和它们不同时出现；</li>
 *   <li>火焰 / 余烬（6.4）：余烬本来就是火的颜色；</li>
 *   <li>一档暴击 / 冲击（8.1）、冲击 / 穿刺（10.6）、冰 / 冲击（14.4）：同一族中性白，冲击是地面环加尘，暴击是星芒；</li>
 *   <li>赤毒 / 爆炸（12.2）、三档暴击边 / 爆炸（13.1）、赤毒 / 切割（13.3）、赤毒 / 三档暴击边（14.7）：
 *       刚好在线下，靠明度和形状再拉开（切割暗、爆炸带烟、暴击是白芯）；</li>
 *   <li>腐蚀 / 毒素（15.0）：正好在线上，腐蚀偏橄榄、明显更暗。</li>
 * </ul>
 * <p>奴仆描边 {@link #THRALL_RIM} 和病毒差 26.7，和赤毒、科技青、其余元素都在 30 以上。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FxPalette {

    private FxPalette() {
    }

    /** 赤毒猩红：赤毒、玄骸、安魂的一切（和指南的 KUVA 同色） */
    public static final int KUVA = 0xFF3B52;
    /** 赤毒液体本体、暗角 */
    public static final int KUVA_DEEP = 0x8E0A1E;
    /** 赤毒高光、横向光晕 */
    public static final int KUVA_HOT = 0xFF7A5A;
    /** 赤毒雾（透明混合，用来压暗） */
    public static final int KUVA_MIST = 0x3A0610;
    /** 赤毒光柱、碎裂的白热芯（带一点红） */
    public static final int KUVA_PALE = 0xFFE0E4;

    /** 科技青：Lotus、界面、护盾 */
    public static final int TECH = 0x4FD6E8;
    public static final int TECH_PALE = 0xBFF6FF;
    public static final int TECH_DEEP = 0x0E5A66;

    /** 友军绿：盟友的名牌 */
    public static final int ALLY = 0x7DFF9A;

    /** 密语金：解密进度、奖励 */
    public static final int MURMUR = 0xFFE8A0;
    /** 强调金：满级、死亡抵抗 */
    public static final int ACCENT = 0xFFC861;
    public static final int EMBER = 0xFFA24D;
    public static final int BONE = 0xDCE6EA;
    public static final int ASH = 0x8496A0;

    /** 白 */
    public static final int WHITE = 0xFFFFFF;

    /** 暴击一档：中性白 */
    public static final int CRIT_1 = 0xF4F1E8;
    /** 暴击二档：淡金 */
    public static final int CRIT_2 = 0xFFDCA0;
    /** 暴击二档星芒 */
    public static final int CRIT_2_RAY = 0xFFF0D0;
    /** 暴击三档的芯 */
    public static final int CRIT_3_CORE = 0xFFF0EA;
    /** 暴击三档的边（只在边上带一点赤毒红） */
    public static final int CRIT_3_EDGE = 0xFF8C7A;

    // ==================== 元素：电击与磁力 ====================

    /** 电击主色 */
    public static final int ELEC = 0x2CCCED;
    /** 电击芯（白热） */
    public static final int ELEC_CORE = 0xF0FBFF;
    /** 电击暗部 */
    public static final int ELEC_DARK = 0x2B71B2;
    /** 磁力主色 */
    public static final int MAG = 0x8ED0D1;
    /** 磁力芯 */
    public static final int MAG_CORE = 0xE6F4FF;
    /** 磁力暗边 */
    public static final int MAG_EDGE = 0x4DAAC0;
    /** 磁力横带的亮芯 */
    public static final int MAG_BAND_CORE = 0xB4F9F0;

    // ==================== 奴仆与暴击数字 ====================

    /** 奴仆描边：色相约 244°，和赤毒红、科技青都拉开 */
    public static final int THRALL_RIM = 0x6C64D8;
    /** 暴击数字一档：黄 */
    public static final int CRIT_Y = 0xF8FA2E;
    /** 暴击数字二档：橙 */
    public static final int CRIT_O = 0xE8833A;
    /** 暴击数字三档：红 */
    public static final int CRIT_R = 0xE6131E;
    /** 普通伤害数字：带一点点冷的白 */
    public static final int DMG_WHITE = 0xFCFBFC;

    // ==================== 档案与通讯的文字 / 底色 ====================

    /** 米灰字：骨白与灰蓝的中间一档，档案和提示里的次要文字用 */
    public static final int TEXT_MID = 0xB0BEC5;
    /** 墨烟底：把赤毒雾色压暗，面板和 HUD 的底 */
    public static final int INK = 0x0C0204;
}
