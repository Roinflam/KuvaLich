package pers.roinflam.kuvalich.client.gui.requiem;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 界面里用的抗锯齿线框图元：菱形、圆环弧、三角、发丝线、凸多边形和小圆点
 *
 * <p>全部用位置 + 颜色的三角形直接画（{@code GameRenderer::getPositionColorShader}），不用贴图。
 * 抗锯齿靠顶点色：线和填充的边缘按 1 个物理像素做一道透明度过渡（以真实边缘为中线，里外各半个像素），
 * 所以 GUI 缩放 4 时 1 px 的细线也是干净的，斜线没有锯齿。不足 1 个物理像素的线按覆盖比例降透明度。</p>
 *
 * <p>坐标、线宽都是调用时 {@code g.pose()} 下的单位：没缩放就是 GUI 像素；先 {@code pose().scale(1 / guiScale)}
 * 再画就是物理像素。过渡带宽度按当前姿态矩阵的缩放自动换算，两种画法都对。颜色都是带 α 的 ARGB。</p>
 *
 * <p>每个调用默认自己开一批、画完就提交。一帧要画很多个时，用 {@link #begin} / {@link #end} 包起来合成一次提交
 * （中间姿态矩阵可以变，顶点在加进去的时候就变换好了）。提交前会先把 {@link GuiGraphics} 攒着的东西画掉，
 * 前后顺序和调用顺序一致。混合在提交时打开、提交完关掉。</p>
 *
 * <p>只在渲染线程用；内部用固定大小的临时数组，不在画的时候分配对象。一条折线最多 {@link #MAX_POINTS} 个点。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuiShapes {

    /** 菱形默认的半宽 / 半高：和赤毒菱晶、安魂印记同一个比例 */
    public static final float DIAMOND_ASPECT = 0.6F;
    /** 一条折线 / 一个多边形最多的点数 */
    public static final int MAX_POINTS = 192;

    /** 尖角接头的最大延长倍数，再尖就截掉，免得细长尖角伸出去很远 */
    private static final float MITER_LIMIT = 4.0F;

    private static final float[] PTS = new float[MAX_POINTS * 2];
    private static final float[] CLIP = new float[MAX_POINTS * 2];
    private static final float[] MX = new float[MAX_POINTS];
    private static final float[] MY = new float[MAX_POINTS];

    private static BufferBuilder buffer;
    private static int depth;
    private static boolean building;
    private static Matrix4f mat;
    /** 当前坐标下 1 个物理像素有多长 */
    private static float px = 1.0F;

    private GuiShapes() {
    }

    // ==================== 批量 ====================

    /** 开一批：之后的调用都攒着，到配对的 {@link #end} 才一次提交。可以嵌套 */
    public static void begin(GuiGraphics g) {
        if (depth++ == 0) {
            g.flush();
            open();
        }
    }

    /** 结束一批并提交 */
    public static void end() {
        if (depth <= 0) {
            depth = 0;
            return;
        }
        if (--depth == 0) {
            submit();
        }
    }

    /**
     * 中途要插一段别的画法（比如贴图画的安魂符号）时先调：批开着就把已经攒下的图元提交掉、接着攒，
     * 再把 {@link GuiGraphics} 攒着的东西画掉，前后顺序才和调用顺序一致
     */
    public static void flush(GuiGraphics g) {
        if (depth > 0 && building) {
            submit();
            open();
        }
        g.flush();
    }

    private static void open() {
        if (buffer == null) {
            buffer = new BufferBuilder(4096);
        }
        if (!building) {
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            building = true;
        }
    }

    private static void submit() {
        if (!building) {
            return;
        }
        building = false;
        BufferBuilder.RenderedBuffer rb = buffer.endOrDiscardIfEmpty();
        if (rb == null) {
            return;
        }
        // 三角形的绕向不统一（接头、过渡带两侧方向相反），提交时关掉背面剔除
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(rb);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** 单个调用的开头：没在批里就自己开一批；同时取当前姿态矩阵和像素宽度 */
    private static boolean enter(GuiGraphics g) {
        mat = g.pose().last().pose();
        px = physicalPixel(mat);
        if (depth > 0) {
            return false;
        }
        g.flush();
        open();
        return true;
    }

    private static void leave(boolean own) {
        if (own) {
            submit();
        }
    }

    // ==================== 尺寸换算 ====================

    /** 当前 {@code g.pose()} 下 1 个物理像素的长度 */
    public static float pixel(GuiGraphics g) {
        return physicalPixel(g.pose().last().pose());
    }

    /** {@code n} 个物理像素在当前 {@code g.pose()} 下的长度（线宽换算用） */
    public static float pixels(GuiGraphics g, float n) {
        return n * pixel(g);
    }

    /** 界面细线的物理像素粗细：{@code max(1, round(窗口物理高度 / 720))}，1080p 是 2 */
    public static int lineWidthPx() {
        return Math.max(1, Math.round(Minecraft.getInstance().getWindow().getHeight() / 720.0F));
    }

    private static float physicalPixel(Matrix4f m) {
        float scale = Mth.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        float s = (float) (scale * gui);
        return s > 1.0E-4F ? 1.0F / s : 1.0F;
    }

    // ==================== 公开图元 ====================

    /**
     * 竖长的菱形，半宽 = 半高 × {@link #DIAMOND_ASPECT}
     *
     * @param h     菱形的高
     * @param lineW 线宽
     * @param fill  从下往上用同一个颜色填满的比例 0~1，0 = 只描边
     */
    public static void diamond(GuiGraphics g, float cx, float cy, float h, float lineW, int argb, float fill) {
        float hh = h * 0.5F;
        float hw = hh * DIAMOND_ASPECT;
        boolean own = enter(g);
        if (fill > 0.0F) {
            fillDiamondBelowRaw(cx, cy, hw, hh, cy + hh - 2.0F * hh * Math.min(1.0F, fill), argb);
        }
        if (lineW > 0.0F) {
            loadDiamond(cx, cy, hw, hh);
            strokeRaw(4, true, lineW, argb);
        }
        leave(own);
    }

    /** 菱形描边，半宽、半高分开给（{@code hw == hh} 就是正方形转 45°） */
    public static void diamondOutline(GuiGraphics g, float cx, float cy, float hw, float hh, float lineW, int argb) {
        boolean own = enter(g);
        loadDiamond(cx, cy, hw, hh);
        strokeRaw(4, true, lineW, argb);
        leave(own);
    }

    /** 实心菱形 */
    public static void diamondFill(GuiGraphics g, float cx, float cy, float hw, float hh, int argb) {
        boolean own = enter(g);
        loadDiamond(cx, cy, hw, hh);
        fillRaw(4, argb);
        leave(own);
    }

    /** 菱形在 {@code y >= yCut} 的那一截（液面以下）；{@code yCut} 是当前坐标下的绝对 y */
    public static void diamondFillBelow(GuiGraphics g, float cx, float cy, float hw, float hh, float yCut, int argb) {
        boolean own = enter(g);
        fillDiamondBelowRaw(cx, cy, hw, hh, yCut, argb);
        leave(own);
    }

    /**
     * 沿菱形外圈从上顶点开始顺时针（上 → 右 → 下 → 左）描出 {@code progress} 比例的周长；1 = 整圈闭合
     */
    public static void diamondTrace(GuiGraphics g, float cx, float cy, float hw, float hh, float lineW, int argb,
                                    float progress) {
        if (progress <= 0.0F) {
            return;
        }
        boolean own = enter(g);
        if (progress >= 1.0F) {
            loadDiamond(cx, cy, hw, hh);
            strokeRaw(4, true, lineW, argb);
        } else {
            loadDiamond(cx, cy, hw, hh);
            PTS[8] = PTS[0];
            PTS[9] = PTS[1];
            float e = progress * 4.0F;
            int whole = (int) e;
            float frac = e - whole;
            int n = whole + 1;
            if (frac > 1.0E-4F) {
                float ax = PTS[whole * 2], ay = PTS[whole * 2 + 1];
                float bx = PTS[whole * 2 + 2], by = PTS[whole * 2 + 3];
                PTS[n * 2] = ax + (bx - ax) * frac;
                PTS[n * 2 + 1] = ay + (by - ay) * frac;
                n++;
            }
            if (n >= 2) {
                strokeRaw(n, false, lineW, argb);
            }
        }
        leave(own);
    }

    /** 菱形在离中心 {@code dy}（向下为正）那一行的半宽，画液面那条线用 */
    public static float diamondHalfWidthAt(float hw, float hh, float dy) {
        return hh <= 0.0F ? 0.0F : hw * Math.max(0.0F, 1.0F - Math.abs(dy) / hh);
    }

    /**
     * 圆环弧
     *
     * @param r     半径
     * @param sweep 从上顶点起顺时针画多少圈 0~1，1 = 整圈
     */
    public static void ring(GuiGraphics g, float cx, float cy, float r, float lineW, int argb, float sweep) {
        if (sweep <= 0.0F || r <= 0.0F) {
            return;
        }
        boolean own = enter(g);
        boolean full = sweep >= 1.0F;
        int segs = arcSegments(r, full ? 1.0F : sweep);
        int n = full ? segs : segs + 1;
        float total = (float) (Math.PI * 2.0D) * Math.min(1.0F, sweep);
        for (int i = 0; i < n; i++) {
            float a = total * i / segs;
            PTS[i * 2] = cx + Mth.sin(a) * r;
            PTS[i * 2 + 1] = cy - Mth.cos(a) * r;
        }
        strokeRaw(n, full, lineW, argb);
        leave(own);
    }

    /** 实心小圆点（格下的次数点之类），边缘同样有 1 物理像素的过渡 */
    public static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        if (r <= 0.0F) {
            return;
        }
        boolean own = enter(g);
        int n = Math.max(12, Math.min(MAX_POINTS, arcSegments(r, 1.0F)));
        for (int i = 0; i < n; i++) {
            float a = (float) (Math.PI * 2.0D) * i / n;
            PTS[i * 2] = cx + Mth.sin(a) * r;
            PTS[i * 2 + 1] = cy - Mth.cos(a) * r;
        }
        fillRaw(n, argb);
        leave(own);
    }

    /**
     * 尖朝上的等边三角形，以外接框中心为 (cx, cy)
     *
     * @param h    高
     * @param fill 从下往上用同一个颜色填满的比例 0~1
     */
    public static void triangle(GuiGraphics g, float cx, float cy, float h, float lineW, int argb, float fill) {
        float hh = h * 0.5F;
        float hw = h / Mth.sqrt(3.0F);
        boolean own = enter(g);
        if (fill > 0.0F) {
            loadTriangle(cx, cy, hw, hh);
            int n = clipBelow(3, cy + hh - h * Math.min(1.0F, fill));
            if (n >= 3) {
                fillRaw(n, argb);
            }
        }
        if (lineW > 0.0F) {
            loadTriangle(cx, cy, hw, hh);
            strokeRaw(3, true, lineW, argb);
        }
        leave(own);
    }

    /** 一条发丝线 */
    public static void hairline(GuiGraphics g, float x0, float y0, float x1, float y1, float lineW, int argb) {
        hairline(g, x0, y0, x1, y1, lineW, argb, argb);
    }

    /** 一条从 {@code argb0} 渐变到 {@code argb1} 的发丝线（一端淡出之类） */
    public static void hairline(GuiGraphics g, float x0, float y0, float x1, float y1, float lineW, int argb0, int argb1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-5F) {
            return;
        }
        boolean own = enter(g);
        float tx = dx / len, ty = dy / len;
        float nx = -ty, ny = tx;
        float[] w = bands(lineW);
        float core = w[0], outer = w[1], aMul = w[2];
        float cap = px * 0.5F;
        float sx = x0 - tx * cap, sy = y0 - ty * cap, ex = x1 + tx * cap, ey = y1 + ty * cap;
        int c0 = mulAlpha(argb0, aMul), c1 = mulAlpha(argb1, aMul);
        int z0 = c0 & 0x00FFFFFF, z1 = c1 & 0x00FFFFFF;
        // 中间实心带
        quad(x0 + nx * core, y0 + ny * core, c0, x1 + nx * core, y1 + ny * core, c1,
                x1 - nx * core, y1 - ny * core, c1, x0 - nx * core, y0 - ny * core, c0);
        // 两侧过渡
        quad(x0 + nx * outer, y0 + ny * outer, z0, x1 + nx * outer, y1 + ny * outer, z1,
                x1 + nx * core, y1 + ny * core, c1, x0 + nx * core, y0 + ny * core, c0);
        quad(x0 - nx * core, y0 - ny * core, c0, x1 - nx * core, y1 - ny * core, c1,
                x1 - nx * outer, y1 - ny * outer, z1, x0 - nx * outer, y0 - ny * outer, z0);
        // 两端各半个像素的过渡（平头，不画圆头）
        quad(sx + nx * core, sy + ny * core, z0, x0 + nx * core, y0 + ny * core, c0,
                x0 - nx * core, y0 - ny * core, c0, sx - nx * core, sy - ny * core, z0);
        quad(x1 + nx * core, y1 + ny * core, c1, ex + nx * core, ey + ny * core, z1,
                ex - nx * core, ey - ny * core, z1, x1 - nx * core, y1 - ny * core, c1);
        leave(own);
    }

    /**
     * 折线：{@code xy} 依次是 x0, y0, x1, y1…，取前 {@code n} 个点；{@code closed} 时首尾相连。
     * 转角是尖角接头（太尖的截到 {@link #MITER_LIMIT} 倍），线端是平头
     */
    public static void polyline(GuiGraphics g, float[] xy, int n, boolean closed, float lineW, int argb) {
        if (n < 2) {
            return;
        }
        boolean own = enter(g);
        int m = Math.min(n, MAX_POINTS);
        System.arraycopy(xy, 0, PTS, 0, m * 2);
        strokeRaw(m, closed, lineW, argb);
        leave(own);
    }

    /**
     * 折线，点先按 {@code scale} 缩放再平移 (ox, oy)：{@code (x, y) → (ox + x·scale, oy + y·scale)}。
     * 卡符号这类存成网格坐标的线稿直接传原表，不用另外拷一份
     */
    public static void polyline(GuiGraphics g, float[] xy, int n, boolean closed, float ox, float oy, float scale,
                                float lineW, int argb) {
        if (n < 2) {
            return;
        }
        boolean own = enter(g);
        int m = Math.min(n, MAX_POINTS);
        for (int i = 0; i < m; i++) {
            PTS[i * 2] = ox + xy[i * 2] * scale;
            PTS[i * 2 + 1] = oy + xy[i * 2 + 1] * scale;
        }
        strokeRaw(m, closed, lineW, argb);
        leave(own);
    }

    /** 实心凸多边形（点按顺时针或逆时针给都行），边缘有过渡 */
    public static void convexFill(GuiGraphics g, float[] xy, int n, int argb) {
        if (n < 3) {
            return;
        }
        boolean own = enter(g);
        int m = Math.min(n, MAX_POINTS);
        System.arraycopy(xy, 0, PTS, 0, m * 2);
        fillRaw(m, argb);
        leave(own);
    }

    /** 实心矩形，四边有过渡（坐标可以是小数，不会像 {@code fill} 那样对齐到整像素） */
    public static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        boolean own = enter(g);
        PTS[0] = x0;
        PTS[1] = y0;
        PTS[2] = x1;
        PTS[3] = y0;
        PTS[4] = x1;
        PTS[5] = y1;
        PTS[6] = x0;
        PTS[7] = y1;
        fillRaw(4, argb);
        leave(own);
    }

    // ==================== 内部：路径 ====================

    private static void loadDiamond(float cx, float cy, float hw, float hh) {
        PTS[0] = cx;
        PTS[1] = cy - hh;
        PTS[2] = cx + hw;
        PTS[3] = cy;
        PTS[4] = cx;
        PTS[5] = cy + hh;
        PTS[6] = cx - hw;
        PTS[7] = cy;
    }

    private static void loadTriangle(float cx, float cy, float hw, float hh) {
        PTS[0] = cx;
        PTS[1] = cy - hh;
        PTS[2] = cx + hw;
        PTS[3] = cy + hh;
        PTS[4] = cx - hw;
        PTS[5] = cy + hh;
    }

    private static void fillDiamondBelowRaw(float cx, float cy, float hw, float hh, float yCut, int argb) {
        loadDiamond(cx, cy, hw, hh);
        int n = clipBelow(4, yCut);
        if (n >= 3) {
            fillRaw(n, argb);
        }
    }

    /** 把 PTS 里的凸多边形裁成 y ≥ yCut 的部分，结果写回 PTS，返回点数 */
    private static int clipBelow(int n, float yCut) {
        int out = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = PTS[i * 2], ay = PTS[i * 2 + 1];
            float bx = PTS[j * 2], by = PTS[j * 2 + 1];
            boolean ain = ay >= yCut, bin = by >= yCut;
            if (ain) {
                CLIP[out * 2] = ax;
                CLIP[out * 2 + 1] = ay;
                out++;
            }
            if (ain != bin && out < MAX_POINTS) {
                float t = (yCut - ay) / (by - ay);
                CLIP[out * 2] = ax + (bx - ax) * t;
                CLIP[out * 2 + 1] = yCut;
                out++;
            }
        }
        System.arraycopy(CLIP, 0, PTS, 0, out * 2);
        return out;
    }

    private static int arcSegments(float r, float sweep) {
        // 弦长大约 3 个物理像素一段
        float circ = (float) (Math.PI * 2.0D) * r * sweep / px;
        return Mth.clamp((int) Math.ceil(circ / 3.0F), 8, MAX_POINTS - 1);
    }

    /**
     * 线宽换算成三个量：实心带半宽、过渡带外缘半宽、透明度系数。
     * 过渡带以真实边缘为中线、宽 1 个物理像素；线比 1 个像素还细时按覆盖比例降透明度
     */
    private static final float[] BANDS = new float[3];

    private static float[] bands(float lineW) {
        float half = lineW * 0.5F;
        float hp = px * 0.5F;
        if (lineW >= px) {
            BANDS[0] = half - hp;
            BANDS[1] = half + hp;
            BANDS[2] = 1.0F;
        } else {
            BANDS[0] = 0.0F;
            BANDS[1] = px;
            BANDS[2] = Math.max(0.0F, lineW / px);
        }
        return BANDS;
    }

    /** 算每个点的尖角接头方向（单位法线之和再按夹角放大），写进 MX / MY */
    private static void miters(int n, boolean closed) {
        for (int i = 0; i < n; i++) {
            boolean hasPrev = closed || i > 0;
            boolean hasNext = closed || i < n - 1;
            float n0x = 0, n0y = 0, n1x = 0, n1y = 0;
            if (hasPrev) {
                int p = (i - 1 + n) % n;
                float dx = PTS[i * 2] - PTS[p * 2], dy = PTS[i * 2 + 1] - PTS[p * 2 + 1];
                float l = Mth.sqrt(dx * dx + dy * dy);
                if (l > 1.0E-6F) {
                    n0x = -dy / l;
                    n0y = dx / l;
                }
            }
            if (hasNext) {
                int q = (i + 1) % n;
                float dx = PTS[q * 2] - PTS[i * 2], dy = PTS[q * 2 + 1] - PTS[i * 2 + 1];
                float l = Mth.sqrt(dx * dx + dy * dy);
                if (l > 1.0E-6F) {
                    n1x = -dy / l;
                    n1y = dx / l;
                }
            }
            if (!hasPrev || (n0x == 0 && n0y == 0)) {
                n0x = n1x;
                n0y = n1y;
            }
            if (!hasNext || (n1x == 0 && n1y == 0)) {
                n1x = n0x;
                n1y = n0y;
            }
            float mx = n0x + n1x, my = n0y + n1y;
            float ml = Mth.sqrt(mx * mx + my * my);
            if (ml < 1.0E-6F) {
                MX[i] = n1x;
                MY[i] = n1y;
                continue;
            }
            mx /= ml;
            my /= ml;
            float d = mx * n1x + my * n1y;
            float s = d > 1.0F / MITER_LIMIT ? 1.0F / d : MITER_LIMIT;
            MX[i] = mx * s;
            MY[i] = my * s;
        }
    }

    /** 把 PTS 里的 n 个点描成线 */
    private static void strokeRaw(int n, boolean closed, float lineW, int argb) {
        if (lineW <= 0.0F || (argb >>> 24) == 0) {
            return;
        }
        float[] w = bands(lineW);
        float core = w[0], outer = w[1];
        int c = mulAlpha(argb, w[2]);
        int z = c & 0x00FFFFFF;
        miters(n, closed);
        int segs = closed ? n : n - 1;
        for (int s = 0; s < segs; s++) {
            int i = s, j = (s + 1) % n;
            float ax = PTS[i * 2], ay = PTS[i * 2 + 1], bx = PTS[j * 2], by = PTS[j * 2 + 1];
            float amx = MX[i], amy = MY[i], bmx = MX[j], bmy = MY[j];
            if (core > 0.0F) {
                quad(ax + amx * core, ay + amy * core, c, bx + bmx * core, by + bmy * core, c,
                        bx - bmx * core, by - bmy * core, c, ax - amx * core, ay - amy * core, c);
            }
            quad(ax + amx * outer, ay + amy * outer, z, bx + bmx * outer, by + bmy * outer, z,
                    bx + bmx * core, by + bmy * core, c, ax + amx * core, ay + amy * core, c);
            quad(ax - amx * core, ay - amy * core, c, bx - bmx * core, by - bmy * core, c,
                    bx - bmx * outer, by - bmy * outer, z, ax - amx * outer, ay - amy * outer, z);
        }
        if (!closed) {
            cap(0, 1, core, outer, c, z);
            cap(n - 1, n - 2, core, outer, c, z);
        }
    }

    /** 开口折线的端头：沿切线往外半个像素的过渡 */
    private static void cap(int end, int inner, float core, float outer, int c, int z) {
        float ex = PTS[end * 2], ey = PTS[end * 2 + 1];
        float dx = ex - PTS[inner * 2], dy = ey - PTS[inner * 2 + 1];
        float l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 1.0E-6F) {
            return;
        }
        float tx = dx / l * px * 0.5F, ty = dy / l * px * 0.5F;
        float nx = -dy / l, ny = dx / l;
        float lim = Math.max(core, 0.0F);
        quad(ex + nx * lim, ey + ny * lim, c, ex + tx + nx * lim, ey + ty + ny * lim, z,
                ex + tx - nx * lim, ey + ty - ny * lim, z, ex - nx * lim, ey - ny * lim, c);
        if (outer > lim) {
            quad(ex + nx * outer, ey + ny * outer, z, ex + tx + nx * outer, ey + ty + ny * outer, z,
                    ex + tx + nx * lim, ey + ty + ny * lim, z, ex + nx * lim, ey + ny * lim, c);
            quad(ex - nx * lim, ey - ny * lim, c, ex + tx - nx * lim, ey + ty - ny * lim, z,
                    ex + tx - nx * outer, ey + ty - ny * outer, z, ex - nx * outer, ey - ny * outer, z);
        }
    }

    /** 把 PTS 里的凸多边形填实：往里缩半个像素的实心扇形 + 一圈 1 像素的过渡 */
    private static void fillRaw(int n, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        // 按面积符号定朝外的方向
        float area = 0.0F;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += PTS[i * 2] * PTS[j * 2 + 1] - PTS[j * 2] * PTS[i * 2 + 1];
        }
        if (Math.abs(area) < 1.0E-8F) {
            return;
        }
        miters(n, true);
        // 屏幕坐标 y 向下：面积为正时点是顺时针，(−dy, dx) 指向里面
        float outSign = area > 0.0F ? -1.0F : 1.0F;
        float hp = px * 0.5F;
        int z = argb & 0x00FFFFFF;
        float ox0 = PTS[0] - MX[0] * outSign * hp, oy0 = PTS[1] - MY[0] * outSign * hp;
        for (int i = 1; i < n - 1; i++) {
            float ax = PTS[i * 2] - MX[i] * outSign * hp, ay = PTS[i * 2 + 1] - MY[i] * outSign * hp;
            float bx = PTS[i * 2 + 2] - MX[i + 1] * outSign * hp, by = PTS[i * 2 + 3] - MY[i + 1] * outSign * hp;
            tri(ox0, oy0, argb, ax, ay, argb, bx, by, argb);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float aix = PTS[i * 2] - MX[i] * outSign * hp, aiy = PTS[i * 2 + 1] - MY[i] * outSign * hp;
            float bix = PTS[j * 2] - MX[j] * outSign * hp, biy = PTS[j * 2 + 1] - MY[j] * outSign * hp;
            float aox = PTS[i * 2] + MX[i] * outSign * hp, aoy = PTS[i * 2 + 1] + MY[i] * outSign * hp;
            float box = PTS[j * 2] + MX[j] * outSign * hp, boy = PTS[j * 2 + 1] + MY[j] * outSign * hp;
            quad(aix, aiy, argb, bix, biy, argb, box, boy, z, aox, aoy, z);
        }
    }

    // ==================== 内部：顶点 ====================

    private static void quad(float x0, float y0, int c0, float x1, float y1, int c1,
                             float x2, float y2, int c2, float x3, float y3, int c3) {
        tri(x0, y0, c0, x1, y1, c1, x2, y2, c2);
        tri(x0, y0, c0, x2, y2, c2, x3, y3, c3);
    }

    private static void tri(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        vtx(x0, y0, c0);
        vtx(x1, y1, c1);
        vtx(x2, y2, c2);
    }

    private static void vtx(float x, float y, int argb) {
        buffer.vertex(mat, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    /** 把 ARGB 的 α 乘上一个系数 */
    public static int mulAlpha(int argb, float k) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(k, 0.0F, 1.0F));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 0xRRGGBB 加上 0~1 的透明度拼成 ARGB */
    public static int argb(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0x00FFFFFF);
    }
}
