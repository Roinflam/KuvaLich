package pers.roinflam.kuvalich.utils;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * 玩家提示文本的配色 —— 24 位 RGB，不受原版那 16 个 {@code ChatFormatting} 限制
 *
 * <p><b>为什么要换掉 ChatFormatting</b>：原版 16 色里能用的本来就少，
 * 而这个模组之前把它们用成了「什么都是红的」——玄骸台词 {@code DARK_RED}、
 * 没收嘲讽 {@code DARK_RED}、解密失败 {@code RED}、解密进度也 {@code RED}。
 * 玩家在聊天框里看到一片同色，分不出「这是剧情台词」「这是我亏了」「这是我赚了」。
 * {@link net.minecraft.network.chat.Style#withColor(net.minecraft.network.chat.TextColor)}
 * 支持完整 RGB，按语义各给一个色就行。</p>
 *
 * <p><b>与面板配色的关系</b>：色相刻意与
 * {@code client.tooltip.PanelPalette} 对齐，让 tooltip 与聊天/动作栏看起来是一套东西。
 * 两边没有合并成一个类，因为 {@code PanelPalette} 是 {@code @OnlyIn(Dist.CLIENT)} 的
 * （它同时提供 {@code Style} 构造方法），而提示文本是在服务端拼好再发给客户端的，
 * 碰不得客户端专有类。对应关系：</p>
 * <ul>
 *   <li>{@link #SUCCESS} ↔ {@code PanelPalette.BONUS}</li>
 *   <li>{@link #DANGER} ↔ {@code PanelPalette.PENALTY}</li>
 *   <li>{@link #WARN} ↔ {@code PanelPalette.WARN}</li>
 *   <li>{@link #PROGRESS} ↔ {@code PanelPalette.STACKED}</li>
 *   <li>{@link #ACCENT} ↔ {@code PanelPalette.STACK_ACTIVE}</li>
 *   <li>{@link #VALUE} ↔ {@code PanelPalette.VALUE}</li>
 *   <li>{@link #INFO} ↔ {@code PanelPalette.LABEL}</li>
 * </ul>
 *
 * @author RoinFlam
 */
public final class KuvaPalette {

    /**
     * 玄骸的声音：死亡台词、没收嘲讽
     *
     * <p>原先用 {@code DARK_REDOLD}（{@code #AA0000}）—— 在深色聊天背景上偏暗、
     * 而且和「解密失败」的 {@code RED} 糊在一起。这个偏品红，既能读出「赤毒」的调性，
     * 又和下面的 {@link #DANGER} 明确分得开。</p>
     */
    public static final int LICH = 0xD9455F;

    /** 好事：解密成功、等级上限提升、被没收的物品还回来了 */
    public static final int SUCCESS = 0x7FE08A;

    /** 坏事：解密失败、谜语还没有答案 */
    public static final int DANGER = 0xFF7B7B;

    /** 半好半坏：猜对了一部分答案 */
    public static final int WARN = 0xFFB066;

    /** 进度推进：解密进度 +N */
    public static final int PROGRESS = 0x6FD8E8;

    /** 需要留意但不紧急：答案已全部解出、死亡抵抗触发 */
    public static final int ACCENT = 0xFFC861;

    /** 句子里被强调的那个值（数字、卡片名） */
    public static final int VALUE = 0xF2F4F8;

    /** 中性叙述 */
    public static final int INFO = 0xC2C8D2;

    /**
     * 物品说明这类「读得到、但不该抢镜」的次要文本
     *
     * <p>换掉原版 {@code DARK_GRAY}（{@code #555555}）—— 它在深色 tooltip 背景上
     * 几乎糊成一团，说明文字等于写了没人看得清。这个明确亮一档。
     * 对应 {@code PanelPalette.MUTED}。</p>
     */
    public static final int MUTED = 0x8A93A3;

    // ==================== 模组品质 ====================

    /**
     * 模组品质档位色
     *
     * <p><b>这里修了一个真 bug。</b>改造前同一件事有三套互相打架的表：</p>
     * <table border="1">
     *   <caption>改造前的三套品质色</caption>
     *   <tr><th>档位</th><th>lang 里的叫法</th><th>图鉴指示器</th><th>图鉴分组标题（当时的 § 码）</th><th>tooltip 词条色</th></tr>
     *   <tr><td>0</td><td>青铜</td><td>#CD7F32 青铜</td><td>§e 黄</td><td>GOLD #FFAA00</td></tr>
     *   <tr><td>1</td><td>白银</td><td>#C0C0C0 银</td><td>§9 蓝</td><td>AQUA #55FFFF</td></tr>
     *   <tr><td>2</td><td>黄金</td><td>#FFD700 金</td><td>§6 金</td><td>YELLOW #FFFF55</td></tr>
     *   <tr><td>3</td><td>Prime</td><td>#55FFFF 青</td><td>§b 青</td><td>WHITE</td></tr>
     * </table>
     *
     * <p>只有图鉴指示器那一套对得上模组自己的中文命名。tooltip 把「青铜」涂成金色、
     * 「黄金」涂成黄色，两者几乎同色，玩家根本分不出手上这张是铜卡还是金卡。
     * 现在三处统一走本方法，以图鉴那套（也就是与命名一致的那套）为准。</p>
     *
     * @param order 品质序号：0=青铜 1=白银 2=黄金 3=Prime 4=裂罅
     * @return 该档位的 RGB
     */
    public static int rarity(int order) {
        return switch (order) {
            case 0 -> 0xCD7F32;   // 青铜
            case 1 -> 0xC0C0C0;   // 白银
            case 2 -> 0xFFD700;   // 黄金
            case 3 -> 0x55FFFF;   // Prime：青
            case 4 -> 0xFF55FF;   // 裂罅：亮紫（沿用原版 LIGHT_PURPLE 的值，它在深色背景上本来就够亮）
            default -> VALUE;
        };
    }

    // ==================== 元素 / 伤害类型 ====================

    /**
     * 元素与物理伤害类型的标识色
     *
     * <p><b>这张表原先长在 {@code client.tooltip.PanelPalette} 里</b>，
     * 但 {@code PanelPalette} 是 {@code @OnlyIn(Dist.CLIENT)} 的，而需要同一批颜色的
     * {@code KuvaWeaponUtil}、{@code AbstractWeaponModule} 都是双端类 ——
     * 从双端类 import 一个客户端专属类会破坏项目自己用包结构 + {@code @OnlyIn}
     * 表达的两端隔离。所以把表搬到这里（本类不碰任何客户端专属类），
     * 由 {@code PanelPalette.element(String)} 反向委托过来，全仓库只此一份。</p>
     *
     * <p>原版的 DARK_RED / DARK_GREEN / DARK_BLUE / DARK_GRAY 在深色 tooltip 背景上
     * 几乎糊成一团，尤其 puncture 用的 DARK_GRAY 基本等于隐形 —— 这正是当初要换 RGB 的起因。</p>
     *
     * @param type 伤害类型名
     * @return 该类型的 RGB；未知类型返回 {@link #INFO}
     */
    public static int element(String type) {
        return switch (type) {
            case "fire" -> 0xFF7A4A;         // 火焰：橙红
            case "ice" -> 0x6FD8F0;          // 冰冻：冰蓝
            case "poison" -> 0x6BD96B;       // 毒素：绿
            case "electricity" -> 0x8296FF;  // 电击：蓝紫
            case "slash" -> 0xD8DCE2;        // 切割：亮灰
            case "puncture" -> 0x9AA2AE;     // 穿刺：中灰（原版 DARK_GRAY 完全看不见）
            case "impact" -> 0xF0F0F0;       // 冲击：白
            case "gas" -> 0xA8E86B;          // 毒气：黄绿
            case "radiation" -> 0xF0E060;    // 辐射：黄
            case "magnetic" -> 0x7A8CFF;     // 磁力：蓝
            case "corrosion" -> 0xC4DB4A;    // 腐蚀：黄绿偏亮
            case "explosion" -> 0xFF9B4A;    // 爆炸：橙
            case "virus" -> 0xFF7AB0;        // 病毒：粉
            default -> INFO;
        };
    }

    // ==================== 赤毒等级梯度 ====================

    /**
     * 赤毒武器等级相对「基准等级」的五档强度梯度色（弱 → 强）
     *
     * <p>档位本身由 {@code KuvaWeaponUtil.getColor(int)} 按
     * {@code ModConfig.KUVA_LICH.benchmarkLevel} 的比值划分，这里只负责给色。</p>
     *
     * <p>最高档刻意避开 {@link #DANGER}（{@code 0xFF7B7B}）：那是「负面词条」的颜色，
     * 而这一档表达的是「这把武器很强」，撞色会让玩家把顶级武器误读成惩罚。</p>
     */
    public static final int[] TIER = {
            MUTED,      // 低于基准 77.8%：最弱
            PROGRESS,   // 77.8% ~ 100%：接近基准
            0xC896F0,   // 100% ~ 122.2%：达标（原 DARK_PURPLE 偏暗，提亮提饱和）
            ACCENT,     // 122.2% ~ 133.3%：强
            0xFF6A3D,   // ≥ 133.3%：顶级（偏暖橙红，与 DANGER 分开）
    };

    // ==================== Style 辅助 ====================

    /**
     * 纯颜色 Style。
     *
     * <p>{@code Style} 与 {@code TextColor} 都在 {@code net.minecraft.network.chat} 包下，
     * 是两端共用的类，所以本类提供这组辅助方法是双端安全的 ——
     * {@code PanelPalette} 里那份同名方法只能给客户端代码用。</p>
     *
     * @param rgb 颜色
     * @return 只设了颜色的 Style
     */
    public static Style style(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    /**
     * 颜色 + 粗体的 Style。
     *
     * <p>需要它是因为 {@code MutableComponent#withStyle(ChatFormatting...)} 那个变长参数重载
     * 只吃 {@code ChatFormatting}：原先「颜色 + BOLD」可以一次传两个枚举值，
     * 颜色换成 int 之后就必须先拼出一个 Style 再传。</p>
     *
     * @param rgb 颜色
     * @return 颜色 + 粗体的 Style
     */
    public static Style bold(int rgb) {
        return style(rgb).withBold(true);
    }

    /**
     * 颜色 + 斜体的 Style。
     *
     * @param rgb 颜色
     * @return 颜色 + 斜体的 Style
     */
    public static Style italic(int rgb) {
        return style(rgb).withItalic(true);
    }

    private KuvaPalette() {}
}
