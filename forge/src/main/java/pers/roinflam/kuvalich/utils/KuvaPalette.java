package pers.roinflam.kuvalich.utils;

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

    private KuvaPalette() {}
}
