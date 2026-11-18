package pers.roinflam.kuvalich.client.tooltip.card;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 画卡用的几样最基本的东西：纯色 / 渐变四边形、贴图四边形、几种混合方式。顶点坐标统一加上 (ox, oy)。
 * 同一种着色器、同一张贴图的四边形攒成一批再画。
 */
final class CardDraw {

    static float ox;
    static float oy;

    private static final int NONE = 0;
    private static final int COLOR = 1;
    private static final int TEX = 2;
    private static int batch = NONE;
    private static BufferBuilder buf;

    private CardDraw() {
    }

    // ---------------------------------------------------------------- 混合

    /** 离屏画布里存的是预乘色：颜色按 alpha 叠，alpha 按 1 - srcA 衰减 */
    static void blendNormal() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /** 加亮：只加颜色、不动 alpha，贴屏幕时按预乘色叠上去就是加亮 */
    static void blendAdd() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    /** 滤色：顶点色要先乘好不透明度，alpha 给 1（着色器丢弃 alpha 为 0 的片元） */
    static void blendScreen() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    /** dst + dst × src：只把已经亮的地方再提亮，暗处不动；alpha 不动 */
    static void blendMulAdd() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    static void blendPremultiplied() {
        flush();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    // ---------------------------------------------------------------- 批次

    private static void beginColor() {
        if (batch == COLOR) {
            return;
        }
        flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        batch = COLOR;
    }

    /** 换贴图前把上一批画掉 */
    static void texture(ResourceLocation tex, boolean smooth) {
        flush();
        CardTextures.bind(tex, smooth);
        beginTex();
    }

    /** 平铺用：超出 0～1 的贴图坐标重复 */
    static void textureRepeat(ResourceLocation tex) {
        texture(tex, true);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    /** 已经绑好的贴图（离屏画布自己的颜色贴图） */
    static void textureId(int id) {
        flush();
        RenderSystem.setShaderTexture(0, id);
        beginTex();
    }

    private static void beginTex() {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        batch = TEX;
    }

    static void flush() {
        if (batch != NONE) {
            BufferUploader.drawWithShader(buf.end());
            batch = NONE;
        }
    }

    /** 出错后丢掉没画完的一批，不再画 */
    static void abort() {
        if (batch != NONE) {
            batch = NONE;
            try {
                buf.end().release();
            } catch (RuntimeException ignored) {
                // 缓冲已经坏了，下一次 begin 会重新开始
            }
        }
        ox = 0;
        oy = 0;
    }

    // ---------------------------------------------------------------- 纯色

    private static void cv(float x, float y, int rgb, float a) {
        buf.vertex(x + ox, y + oy, 0).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha(a)).endVertex();
    }

    private static int alpha(float a) {
        return Math.max(0, Math.min(255, Math.round(a * 255)));
    }

    static void rect(float x0, float y0, float x1, float y1, int rgb, float a) {
        if (a <= 0.002F || x1 <= x0 || y1 <= y0) {
            return;
        }
        beginColor();
        cv(x0, y0, rgb, a);
        cv(x0, y1, rgb, a);
        cv(x1, y1, rgb, a);
        cv(x1, y0, rgb, a);
    }

    /** 竖向渐变：上 (rgb0, a0) 到下 (rgb1, a1) */
    static void vgrad(float x0, float y0, float x1, float y1, int rgb0, float a0, int rgb1, float a1) {
        if ((a0 <= 0.002F && a1 <= 0.002F) || x1 <= x0 || y1 <= y0) {
            return;
        }
        beginColor();
        cv(x0, y0, rgb0, a0);
        cv(x0, y1, rgb1, a1);
        cv(x1, y1, rgb1, a1);
        cv(x1, y0, rgb0, a0);
    }

    /** 横向渐变：左 (rgb0, a0) 到右 (rgb1, a1) */
    static void hgrad(float x0, float y0, float x1, float y1, int rgb0, float a0, int rgb1, float a1) {
        if ((a0 <= 0.002F && a1 <= 0.002F) || x1 <= x0 || y1 <= y0) {
            return;
        }
        beginColor();
        cv(x0, y0, rgb0, a0);
        cv(x0, y1, rgb0, a0);
        cv(x1, y1, rgb1, a1);
        cv(x1, y0, rgb1, a1);
    }

    /** 竖向多段渐变，stops 是 0～1 的位置 */
    static void vstops(float x0, float y0, float x1, float y1, float[] stops, int[] rgb, float[] a) {
        for (int i = 0; i + 1 < stops.length; i++) {
            float ya = y0 + (y1 - y0) * stops[i];
            float yb = y0 + (y1 - y0) * stops[i + 1];
            vgrad(x0, ya, x1, yb, rgb[i], a[i], rgb[i + 1], a[i + 1]);
        }
    }

    /** 任意四边形（三角形就让两个顶点重合），顶点按顺时针或逆时针给 */
    static void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                     int rgb0, float a0, int rgb1, float a1, int rgb2, float a2, int rgb3, float a3) {
        beginColor();
        cv(x0, y0, rgb0, a0);
        cv(x1, y1, rgb1, a1);
        cv(x2, y2, rgb2, a2);
        cv(x3, y3, rgb3, a3);
    }

    /** 滤色用：颜色乘上不透明度，alpha 固定为 1 */
    static void screenQuad(float x0, float y0, float x1, float y1, int rgb, float aTL, float aTR, float aBR, float aBL) {
        if (aTL <= 0.002F && aTR <= 0.002F && aBR <= 0.002F && aBL <= 0.002F) {
            return;
        }
        beginColor();
        sv(x0, y0, rgb, aTL);
        sv(x0, y1, rgb, aBL);
        sv(x1, y1, rgb, aBR);
        sv(x1, y0, rgb, aTR);
    }

    private static void sv(float x, float y, int rgb, float a) {
        float k = Math.max(0, Math.min(1, a));
        buf.vertex(x + ox, y + oy, 0)
                .color(Math.round(((rgb >> 16) & 255) * k), Math.round(((rgb >> 8) & 255) * k), Math.round((rgb & 255) * k), 255)
                .endVertex();
    }

    // ---------------------------------------------------------------- 贴图（先调 texture）

    private static void tv(float x, float y, float u, float v, int rgb, float a) {
        buf.vertex(x + ox, y + oy, 0).uv(u, v).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha(a)).endVertex();
    }

    static void blit(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int rgb, float a) {
        blitV(x0, y0, x1, y1, u0, v0, u1, v1, rgb, a, a);
    }

    /** 上下两端不透明度不同 */
    static void blitV(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                      int rgb, float aTop, float aBot) {
        // 贴图批次已被纯色四边形打断时不画，免得往已结束的缓冲里写
        if (batch != TEX || (aTop <= 0.002F && aBot <= 0.002F) || x1 <= x0 || y1 <= y0) {
            return;
        }
        tv(x0, y0, u0, v0, rgb, aTop);
        tv(x0, y1, u0, v1, rgb, aBot);
        tv(x1, y1, u1, v1, rgb, aBot);
        tv(x1, y0, u1, v0, rgb, aTop);
    }

    /** 上下两端颜色不同（给 blendMulAdd 用，那里起作用的是颜色不是 alpha） */
    static void blitC(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int rgbTop, int rgbBot) {
        if (batch != TEX || x1 <= x0 || y1 <= y0) {
            return;
        }
        tv(x0, y0, u0, v0, rgbTop, 1);
        tv(x0, y1, u0, v1, rgbBot, 1);
        tv(x1, y1, u1, v1, rgbBot, 1);
        tv(x1, y0, u1, v0, rgbTop, 1);
    }

    /** 整张贴图放进一个方框 */
    static void blitAll(float x0, float y0, float x1, float y1, int rgb, float a) {
        blit(x0, y0, x1, y1, 0, 0, 1, 1, rgb, a);
    }
}
