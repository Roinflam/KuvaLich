package pers.roinflam.kuvalich.mercy;

/**
 * 玄骸 / 奴仆身上「特殊状态」特效的种类与参数编码（效果包 {@code KUVA_STATE} 的 a、b 字段）
 *
 * <p>{@code a} = 种类（低 8 位）| 参数 1 &lt;&lt; 8，{@code b} = 参数 2（多为攻击者实体 id + 1）。各种类的参数 1：</p>
 * <pre>
 *  ADAPT   bit 0~2 叠完之后的层数 L（1~6）/ bit 3~5 伤害类别序号 / bit 6 这一下加了层 / bit 7 这一类第一次叠满
 *  GUARD   bit 0~6 被削掉的比例 × 100（10~99）/ bit 7 来源（0 软上限 / 1 连击递减）
 *  STRIKE  bit 0~7 这一下吸的血 × 10（封顶 255）/ bit 8 击退明显；参数 2 是受击者 id + 1
 *  WEAK    无（参数 2 是攻击者 id + 1，调试命令发的是 0）
 *  RESIST  无（参数 2 是攻击者 id + 1）
 * </pre>
 */
public final class KuvaStateKind {

    /** 适应性抗性叠层 */
    public static final int ADAPT = 0;
    /** 伤害被「挡住」（连击递减或软上限削掉了一大块） */
    public static final int GUARD = 1;
    /** 玄骸 / 奴仆命中后的吸血与击退反馈 */
    public static final int STRIKE = 2;
    /** 这一下打中了弱点（按伤害类型吃到的倍率大于 1），发给附近的人；调试命令也发这个 */
    public static final int WEAK = 3;
    /** 这一下被抗性削了（按伤害类型吃到的倍率小于 1），只发给打它的那个玩家 */
    public static final int RESIST = 4;

    private static final int KIND_MASK = 0xFF;

    private KuvaStateKind() {
    }

    /** 拼出 {@code a} 字段 */
    public static int pack(int kind, int param1) {
        return (kind & KIND_MASK) | (param1 << 8);
    }

    /** 从 {@code a} 取种类 */
    public static int kind(int a) {
        return a & KIND_MASK;
    }

    /** 从 {@code a} 取参数 1 */
    public static int param(int a) {
        return a >>> 8;
    }

    // ==================== ADAPT ====================

    public static int adaptParam(int layers, int category, boolean added, boolean firstFull) {
        return (layers & 7) | ((category & 7) << 3) | (added ? 1 << 6 : 0) | (firstFull ? 1 << 7 : 0);
    }

    public static int adaptLayers(int param) {
        return param & 7;
    }

    public static int adaptCategory(int param) {
        return (param >> 3) & 7;
    }

    public static boolean adaptAdded(int param) {
        return (param & (1 << 6)) != 0;
    }

    public static boolean adaptFirstFull(int param) {
        return (param & (1 << 7)) != 0;
    }

    // ==================== GUARD ====================

    public static int guardParam(int percent, boolean fromBurst) {
        return (Math.max(0, Math.min(99, percent)) & 0x7F) | (fromBurst ? 1 << 7 : 0);
    }

    public static int guardPercent(int param) {
        return param & 0x7F;
    }

    /** true = 连击递减，false = 软上限 */
    public static boolean guardFromBurst(int param) {
        return (param & (1 << 7)) != 0;
    }

    // ==================== STRIKE ====================

    public static int strikeParam(int drainTenths, boolean heavyKnockback) {
        return (Math.max(0, Math.min(255, drainTenths)) & 0xFF) | (heavyKnockback ? 1 << 8 : 0);
    }

    /** 吸的血 × 10 */
    public static int strikeDrainTenths(int param) {
        return param & 0xFF;
    }

    public static boolean strikeHeavyKnockback(int param) {
        return (param & (1 << 8)) != 0;
    }
}
