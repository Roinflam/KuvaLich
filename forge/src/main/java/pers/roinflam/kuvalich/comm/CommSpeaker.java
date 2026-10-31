package pers.roinflam.kuvalich.comm;

/**
 * 通讯窗里说话的人
 *
 * <p>序号就是网络包里的编码，只追加不改。通用代码，不碰任何客户端类，所以发言人色直接写成数值，
 * 和客户端色板里的 {@code TECH}、{@code KUVA} 是同一组颜色。</p>
 */
public enum CommSpeaker {
    /** Lotus：窗在左边，青色 */
    LOTUS(true, 0xFF4FD6E8, "kuvalich.guide.comm.lotus"),
    /** Ordis：窗在右边，淡紫 */
    ORDIS(false, 0xFFB9A0FF, "kuvalich.guide.comm.ordis"),
    /** 玄骸：窗在右边，赤毒猩红；名字以包里带的为准，没带时用实体名 */
    LICH(false, 0xFFFF3B52, "entity.kuvalich.kuva_master");

    private static final CommSpeaker[] VALUES = values();

    private final boolean left;
    private final int argb;
    private final String nameKey;

    CommSpeaker(boolean left, int argb, String nameKey) {
        this.left = left;
        this.argb = argb;
        this.nameKey = nameKey;
    }

    /** 窗开在屏幕左边（否则右边） */
    public boolean isLeft() {
        return left;
    }

    /** 发言人色，带不透明 α 的 ARGB */
    public int argb() {
        return argb;
    }

    /** 名牌的语言键（没有单独给名字时用它） */
    public String nameKey() {
        return nameKey;
    }

    /** 按序号取；越界按 Lotus 处理，免得畸形包把客户端带崩 */
    public static CommSpeaker byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : LOTUS;
    }
}
