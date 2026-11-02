package pers.roinflam.kuvalich.client.gui.requiem;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 八张安魂卡的符号：档案、处决界面、灭骸之扉、揭示共用
 *
 * <p>每张卡画的都是它自己物品贴图上那枚玛雅式符号块（{@code tools/fx/make_sigils.py} 抽成贴图），分三层：
 * 线（48 / 96 / 192 三档分辨率，按这次画出来的物理像素挑档，每档的线宽按那个尺寸调过，小尺寸也认得出）、
 * 填（最外一圈轮廓围起来的整块，卡面上深红的那一片）、沿线的光（线预先模糊好）、整块的光晕（填预先模糊好）。
 * 颜色全在运行时乘上去。</p>
 *
 * <p>{@code size} 是符号高；贴图格子宽 1.5 × size、高 1.375 × size，符号中心对准 (cx, cy)，
 * 符号块本身近似方形，留位按宽 = 高 × {@link #ASPECT}。卡 id 认不出（越界、包里给的 15）时画中性占位：
 * 圆角框 + 中间一个小环。</p>
 *
 * <p>Oull（id 8）没有自己的符号：在八张卡的符号之间按时间轮流切换，每 {@link #OULL_FRAME_MS} 毫秒换一张，
 * 和它物品贴图的动画同一个节奏（贴图每帧 10 tick）；换的时候最后 {@link #OULL_FADE_MS} 毫秒交叉淡化。</p>
 *
 * <p>两层光按加法叠（关掉「加法发光」时退回普通透明混合）；没开界面（画在 HUD 上）时再乘相机处的白天补偿，
 * 白天露天不会整片发白。画的时候不分配：顶点走 {@link Tesselator} 的公用缓冲；混合、剔除用完还原成默认
 * （关混合、开剔除）。和 {@link GuiShapes} 混着画时先把它批里攒着的提交掉，前后顺序和调用顺序一致。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RequiemSigils {

    /** 符号块的宽高比（留位用；八张卡的符号块宽高比在 1.04~1.15 之间） */
    public static final float ASPECT = 20.0F / 18.0F;
    /** 卡的种数（不含 Oull） */
    public static final int CARD_COUNT = 8;
    /** Oull 的卡 id：轮流画八张卡的符号 */
    public static final int OULL_ID = 8;
    /** Oull 每张符号停多久、换下一张前交叉淡化多久（毫秒） */
    private static final int OULL_FRAME_MS = 500;
    private static final int OULL_FADE_MS = 120;

    /** 贴图里每行的格数：8 张卡 + 占位 */
    private static final int COLS = CARD_COUNT + 1;
    /** 格子相对符号高的宽、高 */
    private static final float CELL_W = 1.5F;
    private static final float CELL_H = 1.375F;
    /** 线的三档（符号高，像素） */
    private static final int[] LINE_TIERS = {48, 96, 192};
    private static final ResourceLocation[] LINE_TEX = {
            tex("sigils_line_48"), tex("sigils_line_96"), tex("sigils_line_192")
    };
    /** 三行：填、沿线的光、整块的光晕 */
    private static final ResourceLocation SOFT_TEX = tex("sigils_soft");
    /** 软图集每行占的 v 高 */
    private static final float ROW = 1.0F / 3.0F;
    /** 挑档时允许把一档缩小到多少：画 80 px 时用 96 档（缩一点），不用 48 档放大 */
    private static final float TIER_SLACK = 0.85F;

    /** 点亮的外发光透明度（{@code KUVA_PALE}） */
    private static final float LIT_GLOW = 0.3F;
    /** 判错的外发光透明度（{@code KUVA}），外加整块的红色光晕 */
    private static final float WRONG_GLOW = 0.75F;
    private static final float WRONG_HALO = 0.3F;

    /** 处决界面里符号的几种画法 */
    public enum SigilLook {
        /** 外框阶段的暗细线：灰蓝 α 0.7，不填、不发光 */
        OUTLINE_DARK,
        /** 填满发光：线骨白，外面一圈淡红白的光 */
        LIT,
        /** 发光之后回落：线往米灰混 12%，光 α × 0.88 */
        LIT_SETTLED,
        /** 判错：红色霓虹（线粉白、猩红的光）加一道从左下到右上的斜杠 */
        WRONG
    }

    private RequiemSigils() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Reference.MOD_ID, "textures/gui/requiem/" + name + ".png");
    }

    /**
     * 画一张卡的符号：线 + 填，不发光（档案的线描、灭骸之扉之类）
     *
     * @param cardId   卡 id（0~7，8 = Oull 轮流切换）；认不出画中性占位
     * @param cx       中心 x
     * @param cy       中心 y
     * @param size     符号的高
     * @param lineArgb 线的颜色
     * @param fillArgb 外轮廓围起来那一整块的颜色（卡面上的深红）；只要线描就给 0
     */
    public static void draw(GuiGraphics g, int cardId, float cx, float cy, float size, int lineArgb, int fillArgb) {
        drawLayers(g, cardId, cx, cy, size, fillArgb, 0, 0, lineArgb);
    }

    /**
     * 卡面的样子：深红填充 + 粉白线 + 一圈红光（揭示一枚、卡面、悬停放大之类）
     *
     * @param alpha 整体透明度（淡入淡出用）
     */
    public static void drawCard(GuiGraphics g, int cardId, float cx, float cy, float size, float alpha) {
        if (alpha <= 0.002F) {
            return;
        }
        drawLayers(g, cardId, cx, cy, size, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.92F * alpha),
                GuiShapes.argb(FxPalette.KUVA, 0.45F * alpha), GuiShapes.argb(FxPalette.KUVA, 0.25F * alpha),
                GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
    }

    /**
     * 按处决界面的画法画一张卡的符号
     *
     * <ul>
     *   <li>{@code OUTLINE_DARK}：灰蓝 α 0.7 的线；</li>
     *   <li>{@code LIT}：骨白的线 + {@code KUVA_PALE} α 0.3 的外发光；</li>
     *   <li>{@code LIT_SETTLED}：同上，线往米灰混 12%、光 α × 0.88；</li>
     *   <li>{@code WRONG}：粉白的线 + 猩红 α 0.75 的外发光，再加一道左下 → 右上 45° 的斜杠（两端伸出符号框 10%，
     *       同样的线色 + 两遍猩红光晕）。</li>
     * </ul>
     *
     * <p>符号的线宽跟着贴图档位走（每档按那个尺寸调好的），{@code lineWidthPx} 只管斜杠的粗细。</p>
     *
     * @param alpha       整体透明度（淡入淡出用）
     * @param lineWidthPx 基础线宽，单位是物理像素（处决界面传 {@code lw}）
     */
    public static void drawLook(GuiGraphics g, int cardId, float cx, float cy, float size, SigilLook look,
                                float alpha, float lineWidthPx) {
        if (alpha <= 0.002F) {
            return;
        }
        switch (look) {
            case OUTLINE_DARK -> drawLayers(g, cardId, cx, cy, size, 0, 0, 0, GuiShapes.argb(FxPalette.ASH, 0.7F * alpha));
            case LIT -> drawLayers(g, cardId, cx, cy, size, 0, 0, GuiShapes.argb(FxPalette.KUVA_PALE, LIT_GLOW * alpha),
                    GuiShapes.argb(FxPalette.BONE, alpha));
            case LIT_SETTLED -> drawLayers(g, cardId, cx, cy, size, 0, 0,
                    GuiShapes.argb(FxPalette.KUVA_PALE, LIT_GLOW * 0.88F * alpha),
                    GuiShapes.argb(FxCurve.lerpColor(FxPalette.BONE, FxPalette.TEXT_MID, 0.12F), alpha));
            case WRONG -> {
                drawLayers(g, cardId, cx, cy, size, 0, GuiShapes.argb(FxPalette.KUVA, WRONG_HALO * alpha),
                        GuiShapes.argb(FxPalette.KUVA, WRONG_GLOW * alpha), GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                float lw = GuiShapes.pixels(g, lineWidthPx);
                GuiShapes.begin(g);
                // 斜杠的光晕合起来 α 0.75：两遍按 0.28 : 0.47 分
                slash(g, cx, cy, size, lw * 3.0F, GuiShapes.argb(FxPalette.KUVA, 0.28F * alpha));
                slash(g, cx, cy, size, lw * 2.0F, GuiShapes.argb(FxPalette.KUVA, 0.47F * alpha));
                slash(g, cx, cy, size, lw * 1.5F, GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                GuiShapes.end();
            }
        }
    }

    /** 判错斜杠：过中心、左下到右上 45°，两端伸出符号框（以高为准）10% */
    public static void slash(GuiGraphics g, float cx, float cy, float size, float lineW, int argb) {
        float h = size * 0.5F * 1.1F;
        GuiShapes.hairline(g, cx - h, cy + h, cx + h, cy - h, lineW, argb);
    }

    /** 符号框（宽 × 高）在 {@code size} 下的宽 */
    public static float width(float size) {
        return size * ASPECT;
    }

    /**
     * 四层分别给颜色画一张卡的符号，从下往上：整块的光晕（加法）、填、沿线的光（加法）、线；某层的 α 是 0 就不画那层
     */
    public static void drawLayers(GuiGraphics g, int cardId, float cx, float cy, float size,
                                  int fillArgb, int haloArgb, int glowArgb, int lineArgb) {
        if (cardId == OULL_ID) {
            long t = Util.getMillis();
            int frame = (int) ((t / OULL_FRAME_MS) % CARD_COUNT);
            int into = (int) (t % OULL_FRAME_MS);
            float mix = into <= OULL_FRAME_MS - OULL_FADE_MS ? 0.0F
                    : (into - (OULL_FRAME_MS - OULL_FADE_MS)) / (float) OULL_FADE_MS;
            if (mix <= 0.0F) {
                drawLayers(g, frame, cx, cy, size, fillArgb, haloArgb, glowArgb, lineArgb);
                return;
            }
            // 交叉淡化：两张按 1 − mix : mix 各画一遍
            float keep = 1.0F - mix;
            drawLayers(g, frame, cx, cy, size, GuiShapes.mulAlpha(fillArgb, keep), GuiShapes.mulAlpha(haloArgb, keep),
                    GuiShapes.mulAlpha(glowArgb, keep), GuiShapes.mulAlpha(lineArgb, keep));
            drawLayers(g, (frame + 1) % CARD_COUNT, cx, cy, size, GuiShapes.mulAlpha(fillArgb, mix),
                    GuiShapes.mulAlpha(haloArgb, mix), GuiShapes.mulAlpha(glowArgb, mix), GuiShapes.mulAlpha(lineArgb, mix));
            return;
        }
        boolean fill = (fillArgb >>> 24) != 0;
        boolean halo = (haloArgb >>> 24) != 0;
        boolean glow = (glowArgb >>> 24) != 0;
        boolean line = (lineArgb >>> 24) != 0;
        if (!(fill || halo || glow || line) || size <= 0.0F) {
            return;
        }
        int col = cardId >= 0 && cardId < CARD_COUNT ? cardId : CARD_COUNT;
        float u0 = col / (float) COLS, u1 = (col + 1) / (float) COLS;
        float hw = size * CELL_W * 0.5F, hh = size * CELL_H * 0.5F;
        float x0 = cx - hw, x1 = cx + hw, y0 = cy - hh, y1 = cy + hh;

        GuiShapes.flush(g);
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        // 光晕垫在填的下面：卡面里面是深红，光只从外轮廓往外透；没有填的时候（判错）整块透着红
        float k = 1.0F;
        boolean additive = EffectsDisplayConfig.additiveGlow();
        if (additive) {
            k = hudGlowScale();
        }
        RenderSystem.setShaderTexture(0, SOFT_TEX);
        if (halo) {
            glowBlend(additive);
            quad(m, x0, y0, x1, y1, u0, ROW * 2.0F, u1, 1.0F, GuiShapes.mulAlpha(haloArgb, k));
        }
        if (fill) {
            RenderSystem.defaultBlendFunc();
            quad(m, x0, y0, x1, y1, u0, 0.0F, u1, ROW, fillArgb);
        }
        if (glow) {
            glowBlend(additive);
            quad(m, x0, y0, x1, y1, u0, ROW, u1, ROW * 2.0F, GuiShapes.mulAlpha(glowArgb, k));
        }
        if (line) {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderTexture(0, LINE_TEX[tier(size / GuiShapes.pixel(g))]);
            quad(m, x0, y0, x1, y1, u0, 0.0F, u1, 1.0F, lineArgb);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void glowBlend(boolean additive) {
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 按符号画出来的物理像素高挑线的档：取够大的最小一档（最多缩小到 1 / 0.85），都不够就用最大的 */
    private static int tier(float physicalPx) {
        for (int i = 0; i < LINE_TIERS.length; i++) {
            if (LINE_TIERS[i] >= physicalPx * TIER_SLACK) {
                return i;
            }
        }
        return LINE_TIERS.length - 1;
    }

    /** 画在 HUD 上（没开界面）时，辉光按相机处的白天补偿压暗；开着界面时背景是界面自己的，不压 */
    private static float hudGlowScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return 1.0F;
        }
        Camera cam = KuvaFxDirector.camera();
        if (cam == null) {
            return 1.0F;
        }
        Vec3 p = cam.getPosition();
        return KuvaFxDirector.additiveScale(p.x, p.y, p.z);
    }

    private static void quad(Matrix4f m, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, gr = (argb >> 8) & 255, b = argb & 255;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        buf.vertex(m, x0, y0, 0.0F).color(r, gr, b, a).uv(u0, v0).endVertex();
        buf.vertex(m, x0, y1, 0.0F).color(r, gr, b, a).uv(u0, v1).endVertex();
        buf.vertex(m, x1, y1, 0.0F).color(r, gr, b, a).uv(u1, v1).endVertex();
        buf.vertex(m, x1, y0, 0.0F).color(r, gr, b, a).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    /** 进世界时先把四张图集载进来，免得第一次处决的那一帧才去读图、卡一下 */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
    public static final class Preload {

        private Preload() {
        }

        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation r : LINE_TEX) {
                tm.getTexture(r);
            }
            tm.getTexture(SOFT_TEX);
        }
    }
}
