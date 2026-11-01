package pers.roinflam.kuvalich.mercy;

/**
 * 一场处决的「计划位」：服务端在开始时把整场处决算好，压成一个 int 发给客户端
 * （也写进实体同步数据，给晚进视野的客户端选姿势）。
 *
 * <pre>
 *  bit 0~1    刀数（1 或 3）
 *  bit 2~3    前缀长度 p（0~3；单刀时 0）
 *  bit 4~7    套路编号（{@link MercyVariant#id}）
 *  bit 8~11   第 1 张卡的 id + 1（0 = 没放）
 *  bit 12~15  第 2 张
 *  bit 16~19  第 3 张
 *  bit 20~21  第 1 张卡处决之前还剩几次（0~3）
 *  bit 22~23  第 2 张
 *  bit 24~25  第 3 张
 *  bit 26     有没收物品要归还
 *  bit 27~28  这次会用完消失的卡张数（0~3）
 *  bit 29     演示（开发命令摆出来的）
 *  bit 30     绕背的方向反过来（往目标的右手边绕）
 *  bit 31     保留，写 0
 * </pre>
 */
public final class MercyPlan {

    private MercyPlan() {
    }

    /**
     * 解开后的计划
     *
     * @param stabs        刀数，1 或 3
     * @param prefix       猜对的前缀长度，3 = 成功，小于 3 在第 prefix + 1 刀判错
     * @param variant      套路
     * @param cardIds      三张卡的 id，-1 = 没放；长度 3
     * @param remaining    三张卡处决之前还剩几次；长度 3
     * @param returnsItems 有没收物品要归还
     * @param consumed     这次会用完消失的卡张数
     * @param demo         演示模式
     * @param mirror       绕背往目标的右手边走（左手边有方块挡着时）
     */
    public record Plan(int stabs, int prefix, MercyVariant variant, int[] cardIds, int[] remaining,
                       boolean returnsItems, int consumed, boolean demo, boolean mirror) {
    }

    /** 压成一个 int；各项越界时夹到能表示的范围 */
    public static int encode(Plan p) {
        int bits = 0;
        bits |= (p.stabs() == 1 ? 1 : 3);
        bits |= clamp(p.prefix(), 0, 3) << 2;
        bits |= (p.variant() == null ? 0 : p.variant().id & 0xF) << 4;
        for (int i = 0; i < 3; i++) {
            int id = i < p.cardIds().length ? p.cardIds()[i] : -1;
            bits |= clamp(id + 1, 0, 15) << (8 + 4 * i);
            int left = i < p.remaining().length ? p.remaining()[i] : 0;
            bits |= clamp(left, 0, 3) << (20 + 2 * i);
        }
        if (p.returnsItems()) {
            bits |= 1 << 26;
        }
        bits |= clamp(p.consumed(), 0, 3) << 27;
        if (p.demo()) {
            bits |= 1 << 29;
        }
        if (p.mirror()) {
            bits |= 1 << 30;
        }
        return bits;
    }

    /** 解开；套路编号不认识时按玄骸的 {@link MercyVariant#LICH_A} 处理 */
    public static Plan decode(int bits) {
        int stabs = (bits & 3) == 1 ? 1 : 3;
        int prefix = (bits >> 2) & 3;
        MercyVariant variant = MercyVariant.byId((bits >> 4) & 0xF);
        if (variant == null) {
            variant = MercyVariant.LICH_A;
        }
        int[] cardIds = new int[3];
        int[] remaining = new int[3];
        for (int i = 0; i < 3; i++) {
            cardIds[i] = ((bits >> (8 + 4 * i)) & 0xF) - 1;
            remaining[i] = (bits >> (20 + 2 * i)) & 3;
        }
        boolean returnsItems = ((bits >> 26) & 1) != 0;
        int consumed = (bits >> 27) & 3;
        boolean demo = ((bits >> 29) & 1) != 0;
        boolean mirror = ((bits >> 30) & 1) != 0;
        return new Plan(stabs, prefix, variant, cardIds, remaining, returnsItems, consumed, demo, mirror);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
