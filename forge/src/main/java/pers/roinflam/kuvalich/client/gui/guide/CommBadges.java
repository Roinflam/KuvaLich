package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.CommWho;

/**
 * 通讯框的兜底徽记（28×28，原创几何）
 *
 * <p>通讯框平时画发言人的肖像窗；画不出肖像时（标题画面没有世界、玄骸模型画不了，或者一篇里第三条起的玄骸通讯）
 * 才退回这枚徽记，居中画在肖像窗里。</p>
 *
 * <p>Lotus 是五瓣花 + 水线 + 引路星点，Ordis 是碎掉一角的等轴测立方体（说话时右侧有声波涟漪），
 * 玄骸是带裂纹的竖长赤毒晶体。外圈都是 3px 斜切方框，单独一层。</p>
 *
 * <h3>为什么用色段而不是贴图</h3>
 * <p>指南整个 render 在 {@code drawManaged} 里，{@code fill} 走缓冲区延后提交，{@code blit} 却是立即画的，
 * 会抢在前面的底板之前上屏、被底板盖住。所以徽记拆成 1px 高的横向色段，逐段 {@code fill}，
 * 和指南其它几何一样吃得到换篇淡入的透明度。一段打包成一个 int：{@code x | y << 5 | w << 10 | 色号 << 15}，
 * 色号 0 框、1 主、2 高光、3 暗，颜色按发言人取（{@link #palette}）。
 * 数据由 {@code tools/fx/make_comm_badges.py} 生成，改造型改那个脚本再重跑。</p>
 *
 * <p>绘制路径零分配：颜色由调用方算好放进复用的数组里传进来。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class CommBadges {

    static final int SIZE = 28;

    /** 色号 */
    static final int C_FRAME = 0;
    static final int C_MAIN = 1;
    static final int C_HI = 2;
    static final int C_DIM = 3;

    private static final int[] LOTUS_PALETTE = {0xFF1D5F69, 0xFF4FD6E8, 0xFFBFF6FF, 0xFF2A8A98};
    private static final int[] ORDIS_PALETTE = {0xFF4A3F73, 0xFFB9A0FF, 0xFFE4DAFF, 0xFF6E5FA8};
    private static final int[] LICH_PALETTE = {0xFF5E0A18, 0xFFFF3B52, 0xFFFF7A5A, 0xFF8E0A1E};

    private CommBadges() {}

    /** 发言人的四色：框 / 主 / 高光 / 暗（只读，别改里面的值） */
    static int[] palette(CommWho who) {
        return switch (who) {
            case LOTUS -> LOTUS_PALETTE;
            case ORDIS -> ORDIS_PALETTE;
            case LICH -> LICH_PALETTE;
        };
    }

    static int[] frame(CommWho who) {
        return switch (who) {
            case LOTUS -> LOTUS_FRAME;
            case ORDIS -> ORDIS_FRAME;
            case LICH -> LICH_FRAME;
        };
    }

    static int[] base(CommWho who) {
        return switch (who) {
            case LOTUS -> LOTUS_BASE;
            case ORDIS -> ORDIS_BASE;
            case LICH -> LICH_BASE;
        };
    }

    /** 静止态在 BASE 之上再叠的那一层：Lotus 的星点、Ordis 的碎块、玄骸的裂纹 */
    static int[] accent(CommWho who) {
        return switch (who) {
            case LOTUS -> LOTUS_DOT;
            case ORDIS -> ORDIS_SHARD;
            case LICH -> LICH_CRACK;
        };
    }

    /** Ordis 的第 i 道涟漪（半径 10 / 12 / 14） */
    static int[] ripple(int i) {
        return i <= 0 ? ORDIS_RIP0 : i == 1 ? ORDIS_RIP1 : ORDIS_RIP2;
    }

    /**
     * 按色号逐段画
     *
     * @param colors 色号 → 颜色（已乘好透明度），长度 4
     */
    static void draw(GuiGraphics g, int[] layer, int x, int y, int[] colors) {
        for (int v : layer) {
            int sx = x + (v & 31);
            int sy = y + ((v >> 5) & 31);
            g.fill(sx, sy, sx + ((v >> 10) & 31), sy + 1, colors[(v >> 15) & 3]);
        }
    }

    /** 整层一个颜色（待机态的暗框、涟漪、色差副本） */
    static void drawMono(GuiGraphics g, int[] layer, int x, int y, int color) {
        for (int v : layer) {
            int sx = x + (v & 31);
            int sy = y + ((v >> 5) & 31);
            g.fill(sx, sy, sx + ((v >> 10) & 31), sy + 1, color);
        }
    }

    /**
     * 只重画落在某一行上的色段（Lotus 说话时那道扫过徽记的亮线）
     *
     * @param row    徽记内的行号
     * @param colors 色号 → 这一行该用的颜色
     */
    static void drawRow(GuiGraphics g, int[] layer, int x, int y, int row, int[] colors) {
        for (int v : layer) {
            if (((v >> 5) & 31) != row) {
                continue;
            }
            int sx = x + (v & 31);
            g.fill(sx, y + row, sx + ((v >> 10) & 31), y + row + 1, colors[(v >> 15) & 3]);
        }
    }

    // <badge-data>（tools/fx/make_comm_badges.py 生成，别手改）
    /** LOTUS FRAME：54 段 */
    static final int[] LOTUS_FRAME = {
            22531, 1058, 1081, 1089, 1114, 1120, 1147, 1152, 1179, 1184, 1211, 1216,
            1243, 1248, 1275, 1280, 1307, 1312, 1339, 1344, 1371, 1376, 1403, 1408,
            1435, 1440, 1467, 1472, 1499, 1504, 1531, 1536, 1563, 1568, 1595, 1600,
            1627, 1632, 1659, 1664, 1691, 1696, 1723, 1728, 1755, 1760, 1787, 1792,
            1819, 1825, 1850, 1858, 1881, 23395,
    };
    /** LOTUS BASE：64 段 */
    static final int[] LOTUS_BASE = {
            33998, 34029, 34031, 34060, 34064, 34092, 34096, 34123, 34129, 34155, 34161, 34187,
            34193, 36262, 34219, 34225, 36276, 34246, 36297, 36305, 34262, 34278, 34283, 34289,
            34294, 34310, 35339, 35344, 34326, 34343, 34347, 34349, 34351, 34353, 34357, 101959,
            35404, 35407, 101971, 99942, 35432, 99946, 35436, 35439, 99954, 35443, 99958, 99973,
            34442, 99979, 34445, 34447, 99985, 34450, 99991, 100006, 100012, 36525, 100016, 100022,
            104135, 104145, 116454, 12073,
    };
    /** LOTUS DOT：1 段 */
    static final int[] LOTUS_DOT = {
            66670,
    };
    /** ORDIS FRAME：54 段 */
    static final int[] ORDIS_FRAME = {
            22531, 1058, 1081, 1089, 1114, 1120, 1147, 1152, 1179, 1184, 1211, 1216,
            1243, 1248, 1275, 1280, 1307, 1312, 1339, 1344, 1371, 1376, 1403, 1408,
            1435, 1440, 1467, 1472, 1499, 1504, 1531, 1536, 1563, 1568, 1595, 1600,
            1627, 1632, 1659, 1664, 1691, 1696, 1723, 1728, 1755, 1760, 1787, 1792,
            1819, 1825, 1850, 1858, 1881, 23395,
    };
    /** ORDIS BASE：34 段 */
    static final int[] ORDIS_BASE = {
            33995, 35049, 35079, 35109, 34116, 36196, 34180, 35207, 34212, 35241, 34244, 34251,
            104908, 34258, 34276, 34283, 34290, 34308, 34315, 34322, 34340, 34347, 34354, 34372,
            34379, 34386, 35429, 34411, 35440, 35463, 34443, 35470, 38569, 34507,
    };
    /** ORDIS SHARD：14 段 */
    static final int[] ORDIS_SHARD = {
            33933, 34990, 35024, 35058, 99603, 34068, 99634, 34100, 100688, 34132, 99695, 34164,
            34196, 34228,
    };
    /** ORDIS RIP0：13 段 */
    static final int[] ORDIS_RIP0 = {
            34067, 35123, 34132, 35188, 34197, 34229, 34261, 34293, 34325, 35380, 34388, 34420,
            34451,
    };
    /** ORDIS RIP1：15 段 */
    static final int[] ORDIS_RIP1 = {
            100596, 99605, 99638, 99670, 99703, 99735, 99767, 99799, 99831, 99863, 99895, 99926,
            99958, 101013, 100021,
    };
    /** ORDIS RIP2：18 段 */
    static final int[] ORDIS_RIP2 = {
            99510, 99542, 99575, 99608, 99640, 99672, 99705, 99737, 99769, 99801, 99833, 99865,
            99897, 99928, 99960, 99992, 100023, 101078,
    };
    /** LICH FRAME：54 段 */
    static final int[] LICH_FRAME = {
            22531, 1058, 1081, 1089, 1114, 1120, 1147, 1152, 1179, 1184, 1211, 1216,
            1243, 1248, 1275, 1280, 1307, 1312, 1339, 1344, 1371, 1376, 1403, 1408,
            1435, 1440, 1467, 1472, 1499, 1504, 1531, 1536, 1563, 1568, 1595, 1600,
            1627, 1632, 1659, 1664, 1691, 1696, 1723, 1728, 1755, 1760, 1787, 1792,
            1819, 1825, 1850, 1858, 1881, 23395,
    };
    /** LICH BASE：58 段 */
    static final int[] LICH_BASE = {
            33902, 33933, 33935, 33965, 33967, 33996, 34000, 34006, 34028, 34032, 34039, 99595,
            34065, 34090, 99627, 34098, 34122, 99659, 34130, 34153, 99691, 34163, 34185, 99723,
            34195, 34216, 99755, 34228, 34248, 99787, 34260, 34281, 99819, 34291, 34314, 99851,
            34322, 34346, 99883, 34354, 34378, 99915, 34386, 34405, 34411, 34417, 34436, 34444,
            34448, 34476, 34480, 34508, 34512, 34541, 34543, 34574, 34606, 100206,
    };
    /** LICH CRACK：16 段 */
    static final int[] LICH_CRACK = {
            66766, 66798, 66829, 66861, 66892, 66924, 66957, 66990, 67022, 67055, 67087, 67118,
            67150, 67182, 67213, 67245,
    };
    // </badge-data>
}
