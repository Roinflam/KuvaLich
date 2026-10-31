package pers.roinflam.kuvalich.mercy;

/**
 * 玄骸 / 奴仆在怜悯之击里的状态，序号就是实体同步数据里的编码
 */
public enum MercyState {
    /** 没有任何怜悯之击相关的状态 */
    NONE,
    /** 倒地，等人动手 */
    DOWNED,
    /** 正在被处决 */
    EXECUTING,
    /** 消散或逃走的演出中，即将移除 */
    LEAVING;

    private static final MercyState[] VALUES = values();

    /** 按序号取；越界按 {@link #NONE} 处理 */
    public static MercyState byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
    }
}
