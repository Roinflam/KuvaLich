package pers.roinflam.kuvalich.client.gui.requiem;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 全息读数用的顶点色图元：渐变、软边、加法混合（{@link GuiShapes} 只有单色、普通混合）。
 * 升级横幅和玄骸 Boss 条共用；坐标是调用方当前姿态下的（这两处都先把姿态缩成 1 / 界面缩放、按物理像素画）
 */
@OnlyIn(Dist.CLIENT)
public final class HoloShapes {

    /** 暗红玻璃板的底色 */
    public static final int GLASS = 0x2A050C;

    /**
     * 读数用的字体：和原版 default 同样三组字形，但「强制使用 Unicode 字体」打开时也照样是像素字
     * （原版那时把 default 整个换成 uniform，字母数字换成另一套字形，高矮、上下位置都变，按像素字算的居中就偏了）。
     * 大写字母和数字占字形格的第 0~6 行，竖直中线在顶上往下 3.5 个字像素
     */
    public static final Style HUD = Style.EMPTY.withFont(new ResourceLocation(Reference.MOD_ID, "hud"));

    private static final float[] RING_IN = new float[16];
    private static final float[] RING_OUT = new float[16];

    private HoloShapes() {
    }

    /** 读数条的外形：左右两个尖、上下两条平边，顺序是左尖 → 左上 → 右上 → 右尖 → 右下 → 左下 */
    public static void hexagon(float[] out, float lx, float rx, float cy, float hh, float ch) {
        out[0] = lx;
        out[1] = cy;
        out[2] = lx + ch;
        out[3] = cy - hh;
        out[4] = rx - ch;
        out[5] = cy - hh;
        out[6] = rx;
        out[7] = cy;
        out[8] = rx - ch;
        out[9] = cy + hh;
        out[10] = lx + ch;
        out[11] = cy + hh;
    }

    public static BufferBuilder open(GuiGraphics g, boolean additive) {
        GuiShapes.flush(g);
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    public static void close(BufferBuilder buf) {
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void vtx(BufferBuilder buf, Matrix4f m, float x, float y, int rgb, float a) {
        int al = Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        buf.vertex(m, x, y, 0.0F).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al).endVertex();
    }

    /**
     * 六边形玻璃板按 {@code alpha[i]}（每个顶点的透明度）横向渐变：从中线劈成左右两半各自扇形三角化，
     * 中线上两点的透明度是 {@code mid}（透明度只随 x 线性变，三角形插值是准的）
     */
    public static void glass(GuiGraphics g, Matrix4f m, float[] p, float[] alpha, float cx, float cy, float hh,
                             float mid, int rgb) {
        BufferBuilder buf = open(g, false);
        // 左半：左尖、左上、中上、中下、左下
        tri(buf, m, rgb, p[0], p[1], alpha[0], p[2], p[3], alpha[1], cx, cy - hh, mid);
        tri(buf, m, rgb, p[0], p[1], alpha[0], cx, cy - hh, mid, cx, cy + hh, mid);
        tri(buf, m, rgb, p[0], p[1], alpha[0], cx, cy + hh, mid, p[10], p[11], alpha[5]);
        // 右半：右尖、右上、中上、中下、右下
        tri(buf, m, rgb, p[6], p[7], alpha[3], p[4], p[5], alpha[2], cx, cy - hh, mid);
        tri(buf, m, rgb, p[6], p[7], alpha[3], cx, cy - hh, mid, cx, cy + hh, mid);
        tri(buf, m, rgb, p[6], p[7], alpha[3], cx, cy + hh, mid, p[8], p[9], alpha[4]);
        close(buf);
    }

    private static void tri(BufferBuilder buf, Matrix4f m, int rgb, float x0, float y0, float a0, float x1, float y1,
                            float a1, float x2, float y2, float a2) {
        vtx(buf, m, x0, y0, rgb, a0);
        vtx(buf, m, x1, y1, rgb, a1);
        vtx(buf, m, x2, y2, rgb, a2);
    }

    /** 实心菱形，上、中、下三档透明度（中间那一档给左右两个顶点），竖向渐变 */
    public static void diamondFill(GuiGraphics g, Matrix4f m, float cx, float cy, float hw, float hh, int rgb,
                                   float aTop, float aMid, float aBottom, boolean additive) {
        if (hh <= 0.0F || hw <= 0.0F || Math.max(aTop, Math.max(aMid, aBottom)) <= 0.002F) {
            return;
        }
        BufferBuilder buf = open(g, additive);
        vtx(buf, m, cx, cy - hh, rgb, aTop);
        vtx(buf, m, cx + hw, cy, rgb, aMid);
        vtx(buf, m, cx, cy + hh, rgb, aBottom);
        vtx(buf, m, cx, cy - hh, rgb, aTop);
        vtx(buf, m, cx, cy + hh, rgb, aBottom);
        vtx(buf, m, cx - hw, cy, rgb, aMid);
        close(buf);
    }

    /**
     * 凸多边形的软边：往里缩 {@code in} 那一圈实色，往外扩 {@code out} 那一圈透明，中间顶点色插值。
     * 当阴影用，原多边形本身随后会被玻璃板盖住
     */
    public static void softPolygon(GuiGraphics g, Matrix4f m, float[] p, int n, float in, float out, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        offset(p, n, -in, RING_IN);
        offset(p, n, out, RING_OUT);
        int rgb = argb & 0xFFFFFF;
        float a = (argb >>> 24) / 255.0F;
        BufferBuilder buf = open(g, false);
        for (int i = 1; i < n - 1; i++) {
            vtx(buf, m, RING_IN[0], RING_IN[1], rgb, a);
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_IN[i * 2 + 2], RING_IN[i * 2 + 3], rgb, a);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_OUT[i * 2], RING_OUT[i * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_OUT[j * 2], RING_OUT[j * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_OUT[j * 2], RING_OUT[j * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_IN[j * 2], RING_IN[j * 2 + 1], rgb, a);
        }
        close(buf);
    }

    /** 凸多边形每条边往外平移 {@code d}（负数往里），转角按斜接算，太尖的截到 3 倍 */
    private static void offset(float[] p, int n, float d, float[] out) {
        float area = 0.0F;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        // 屏幕坐标 y 朝下：面积为正是顺时针，边的外法线是 (dy, -dx)
        float sign = area >= 0.0F ? 1.0F : -1.0F;
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            float n1x = p[i * 2 + 1] - p[a * 2 + 1], n1y = -(p[i * 2] - p[a * 2]);
            float n2x = p[b * 2 + 1] - p[i * 2 + 1], n2y = -(p[b * 2] - p[i * 2]);
            float l1 = Mth.sqrt(n1x * n1x + n1y * n1y), l2 = Mth.sqrt(n2x * n2x + n2y * n2y);
            if (l1 < 1.0E-4F || l2 < 1.0E-4F) {
                out[i * 2] = p[i * 2];
                out[i * 2 + 1] = p[i * 2 + 1];
                continue;
            }
            n1x = n1x / l1 * sign;
            n1y = n1y / l1 * sign;
            n2x = n2x / l2 * sign;
            n2y = n2y / l2 * sign;
            float bx = n1x + n2x, by = n1y + n2y;
            float bl = Mth.sqrt(bx * bx + by * by);
            if (bl < 1.0E-4F) {
                bx = n1x;
                by = n1y;
                bl = 1.0F;
            }
            bx /= bl;
            by /= bl;
            float cos = bx * n1x + by * n1y;
            float len = d / Math.max(cos, 1.0F / 3.0F);
            out[i * 2] = p[i * 2] + bx * len;
            out[i * 2 + 1] = p[i * 2 + 1] + by * len;
        }
    }

    /** 矩形，四周再各有 {@code edge} 宽从实色渐到透明；{@code edge} 为 0 就是硬边 */
    public static void softRect(GuiGraphics g, Matrix4f m, float x0, float y0, float x1, float y1, float edge,
                                int argb, boolean additive) {
        if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        float a = (argb >>> 24) / 255.0F;
        BufferBuilder buf = open(g, additive);
        if (edge <= 0.0F) {
            quad(buf, m, x0, y0, x1, y1, rgb, a, a, a, a);
        } else {
            float ox0 = x0 - edge, oy0 = y0 - edge, ox1 = x1 + edge, oy1 = y1 + edge;
            quad(buf, m, x0, y0, x1, y1, rgb, a, a, a, a);
            quad(buf, m, ox0, y0, x0, y1, rgb, 0.0F, a, a, 0.0F);
            quad(buf, m, x1, y0, ox1, y1, rgb, a, 0.0F, 0.0F, a);
            quad(buf, m, x0, oy0, x1, y0, rgb, 0.0F, 0.0F, a, a);
            quad(buf, m, x0, y1, x1, oy1, rgb, a, a, 0.0F, 0.0F);
            quad(buf, m, ox0, oy0, x0, y0, rgb, 0.0F, 0.0F, a, 0.0F);
            quad(buf, m, x1, oy0, ox1, y0, rgb, 0.0F, 0.0F, 0.0F, a);
            quad(buf, m, x1, y1, ox1, oy1, rgb, a, 0.0F, 0.0F, 0.0F);
            quad(buf, m, ox0, y1, x0, oy1, rgb, 0.0F, a, 0.0F, 0.0F);
        }
        close(buf);
    }

    /** 横向渐变的矩形：左边透明度 {@code a0}、右边 {@code a1} */
    public static void hgrad(GuiGraphics g, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, float a0,
                             float a1, boolean additive) {
        if (x1 <= x0 || y1 <= y0 || Math.max(a0, a1) <= 0.002F) {
            return;
        }
        BufferBuilder buf = open(g, additive);
        quad(buf, m, x0, y0, x1, y1, rgb, a0, a1, a1, a0);
        close(buf);
    }

    /** 竖向渐变的矩形：上边透明度 {@code a0}、下边 {@code a1} */
    public static void vgrad(GuiGraphics g, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, float a0,
                             float a1, boolean additive) {
        if (x1 <= x0 || y1 <= y0 || Math.max(a0, a1) <= 0.002F) {
            return;
        }
        BufferBuilder buf = open(g, additive);
        quad(buf, m, x0, y0, x1, y1, rgb, a0, a0, a1, a1);
        close(buf);
    }

    /** 轴对齐四边形：四个角的透明度依次是 左上、右上、右下、左下 */
    public static void quad(BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int rgb,
                            float aTL, float aTR, float aBR, float aBL) {
        vtx(buf, m, x0, y0, rgb, aTL);
        vtx(buf, m, x1, y0, rgb, aTR);
        vtx(buf, m, x1, y1, rgb, aBR);
        vtx(buf, m, x0, y0, rgb, aTL);
        vtx(buf, m, x1, y1, rgb, aBR);
        vtx(buf, m, x0, y1, rgb, aBL);
    }

    /** 读数字体下这串字的宽（字像素，含末字后面那 1 像素字距） */
    public static int hudWidth(Font font, String s) {
        return font.width(FormattedCharSequence.forward(s, HUD));
    }

    /** 用读数字体、按整数倍物理像素画字 */
    public static void hudText(GuiGraphics g, Font font, String s, float x, float y, int p, int argb) {
        if ((argb >>> 24) < 5) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, FormattedCharSequence.forward(s, HUD), 0, 0, argb, false);
        g.pose().popPose();
    }

    /** 按整数倍物理像素画字（一个字像素 = {@code p} 个物理像素），不糊 */
    public static void text(GuiGraphics g, Font font, String s, float x, float y, int p, int argb) {
        if ((argb >>> 24) < 5) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }
}
