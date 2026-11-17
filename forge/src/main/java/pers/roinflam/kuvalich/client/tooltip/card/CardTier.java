package pers.roinflam.kuvalich.client.tooltip.card;

/**
 * 展开卡的六档边框与配色。各档主色取自对应图标贴图的边框色（白银是蓝色），执刑官是红金一套。
 */
enum CardTier {
    BRONZE("bronze", 0xf8d3ae, 0xb86e40, 0x4c2214, 0x130706, 0x3a1810, 0xe27a4c, 0.42F, 0x1b120d, 0xd08a4c,
            0xF6D2A8, 0x5aa8ff, 0x9a7560, 0xffd9b8, 0.45F, 0xffd2a8, 4, 4, 7),
    SILVER("silver", 0xdfe0fa, 0x7e75c8, 0x3b357c, 0x0d0b22, 0x544c9e, 0x8a80ee, 0.45F, 0x121126, 0x9a93e2,
            0xD5D6F8, 0x8a8cff, 0x9891d8, 0xeeeeff, 0.7F, 0xc9ceff, 4, 4, 7),
    GOLD("gold", 0xfff9a6, 0xf2b82a, 0x7a4408, 0x1a0c02, 0xc88418, 0xffc832, 0.62F, 0x20160a, 0xe6b450,
            0xFFEC9C, 0x5aa8ff, 0xe4b840, 0xfff0a0, 0.95F, 0xfff0a0, 4, 4, 7),
    PRIME("prime", 0xffffff, 0xe6ebf0, 0x8a949e, 0x1c232b, 0xffffff, 0xbfeaff, 0.8F, 0x0b1219, 0xc8ecf8,
            0xFFFFFF, 0x5ac8ff, 0xf4f7fa, 0xffffff, 1.0F, 0xe2f9ff, 4, 4, 7),
    RIVEN("riven", 0xffe4ff, 0xc66cf0, 0x47247e, 0x130522, 0x8a46c8, 0xf070ff, 0.6F, 0x160a1e, 0xd47ae8,
            0xFBC6FF, 0xd868ff, 0xe2a0f4, 0xffdcff, 0.8F, 0xb4eeff, 11, 10, 11),
    ARCHON("archon", 0xffe7ae, 0xcf2a2c, 0x5e0c10, 0x1c0505, 0x8c1518, 0xffae48, 0.55F, 0x1d140c, 0xe2a252,
            0xF6DCA2, 0xffc452, 0xe9bb62, 0xffe9b4, 0.9F, 0xffe9b4, 7, 7, 8);

    final String id;
    final int hi, mid, lo, edge, sd, halo;
    final float haloA;
    final int body, net, name, groove, line, star;
    final float spec;
    /** 未揭示卡面上光点的颜色 */
    final int mote;
    /** 边框伸出卡身的距离（卡内单位）：左右、上、下 */
    final int el, et, eb;

    CardTier(String id, int hi, int mid, int lo, int edge, int sd, int halo, float haloA, int body, int net,
             int name, int groove, int line, int star, float spec, int mote, int el, int et, int eb) {
        this.id = id;
        this.hi = hi;
        this.mid = mid;
        this.lo = lo;
        this.edge = edge;
        this.sd = sd;
        this.halo = halo;
        this.haloA = haloA;
        this.body = body;
        this.net = net;
        this.name = name;
        this.groove = groove;
        this.line = line;
        this.star = star;
        this.spec = spec;
        this.mote = mote;
        this.el = el;
        this.et = et;
        this.eb = eb;
    }

    // 执刑官另有的几种颜色
    static final int ARCHON_GOLD = 0xf4cc72;
    static final int ARCHON_GOLD2 = 0x9c6c22;
    static final int ARCHON_TXT = 0xD9C592;
    static final int ARCHON_VAL = 0xFFE9B4;
    static final int ARCHON_PIP = 0xFFE8A0;

    int valueColor() {
        return this == ARCHON ? ARCHON_VAL : 0xF2F4F8;
    }

    int textColor() {
        return this == ARCHON ? ARCHON_TXT : 0xC2C8D2;
    }

    /** 未揭示卡没有 veil 贴图的档（执刑官不会出未揭示）退回 Prime 那张 */
    String veilId() {
        return this == ARCHON ? PRIME.id : id;
    }
}
