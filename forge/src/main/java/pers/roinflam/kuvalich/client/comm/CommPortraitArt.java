package pers.roinflam.kuvalich.client.comm;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.lich.LichPuppet;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

import java.io.InputStream;
import java.util.Optional;

/**
 * 通讯窗「窗内画面」：三位发言人各自的底、烟、光带、微粒和原画层，画进 {@link CommPortraits} 绑好的离屏缓冲
 *
 * <p>坐标是缓冲像素（左上角 (0, 0)，往下 y 变大）；底的 alpha 0.9，没被人物占住的地方合成后透出一成游戏画面。
 * 往透明缓冲里正常混合画出来的颜色就是预乘色，合成时按预乘叠。</p>
 *
 * <p>动的东西（烟、光带、散点、微粒、绕转碎块）都是时间的纯函数：按墙钟和下标算哈希，不存状态、不分配，
 * 指南的静止态把时间冻住画一次就行。原画 192×128，按 96×64 的逻辑像素量位置（贴图里一格是 2×2）。</p>
 */
@OnlyIn(Dist.CLIENT)
final class CommPortraitArt {

    static final ResourceLocation SMOKE = tex("smoke");
    static final ResourceLocation NOISE = tex("noise");
    static final ResourceLocation LOTUS = tex("lotus");
    static final ResourceLocation ORDIS = tex("ordis");

    /** 原画的逻辑像素 */
    private static final int ART_W = 96;
    private static final int ART_H = 64;

    // ---- 玄骸 ----
    private static final int LICH_BG_TOP = 0x000000;
    private static final int LICH_BG_MID = 0x2A0510;
    private static final int LICH_BG_BOTTOM = 0x5E0A18;
    private static final float LICH_BG_MID_AT = 0.6F;
    /** 烟：颜色、透明度、两层的上升速度（窗高 / 秒） */
    private static final int LICH_SMOKE = 0x9A1C26;
    private static final float LICH_SMOKE_ALPHA = 0.25F;
    private static final float LICH_SMOKE_SPEED_A = 0.03F;
    private static final float LICH_SMOKE_SPEED_B = 0.045F;
    /** 两侧的赤毒烟团 */
    private static final int LICH_SIDE = 0x7A0F14;
    private static final float LICH_SIDE_ALPHA = 0.5F;
    /** 两侧烟团的亮红芯子（加法）：颜色、两层合计强度、中心高度、横纵半径（窗高比例）、上升速度（窗高 / 秒） */
    private static final int LICH_EMBER = 0xE0203A;
    private static final float LICH_EMBER_ALPHA = 0.55F;
    private static final float LICH_EMBER_Y = 0.8F;
    private static final float LICH_EMBER_RX = 0.5F;
    private static final float LICH_EMBER_RY = 0.45F;
    private static final float LICH_EMBER_SPEED = 0.06F;
    /** 模型压暗 */
    private static final float LICH_TINT_R = 0.62F;
    private static final float LICH_TINT_G = 0.45F;
    private static final float LICH_TINT_B = 0.45F;
    /** 模型之后整窗叠的自下而上暖光（加法） */
    private static final int LICH_WARM = 0xFF5A3A;
    private static final float LICH_WARM_ALPHA = 0.18F;
    private static final int LICH_MOTE = 0xFF3B52;
    private static final int LICH_MOTES = 8;

    // ---- Lotus ----
    private static final int LOTUS_BG_TOP = 0x0A2229;
    private static final int LOTUS_BG_BOTTOM = 0x0B2A30;
    private static final int LOTUS_BAND = 0x2AA8D8;
    private static final float[] LOTUS_BAND_Y = {0.24F, 0.41F, 0.58F, 0.76F};
    private static final float[] LOTUS_BAND_A = {0.45F, 0.6F, 0.35F, 0.5F};
    private static final float[] LOTUS_BAND_PHASE = {0.0F, 0.37F, 0.71F, 0.13F};
    private static final int LOTUS_DOT = 0x4FD6E8;
    private static final int LOTUS_HI = 0xBFF6FF;
    /** 说话时擦出期间整瓣提亮的中瓣：逻辑像素 [x, y, 长]，头顶以上露出的那一片（不含头和瓣之间的暗缝） */
    private static final int[] LOTUS_TOP_PETAL = {
            49, 0, 1, 49, 1, 2, 48, 2, 4, 47, 3, 5, 46, 4, 6, 46, 5, 7, 45, 6, 8, 45, 7, 8, 44, 8, 10,
            44, 9, 10, 43, 10, 11, 43, 11, 12, 42, 12, 13, 42, 13, 13, 42, 14, 2, 54, 14, 1, 42, 15, 1, 55, 15, 1};

    // ---- Ordis ----
    private static final int ORDIS_BG_TOP = 0x06081A;
    private static final int ORDIS_BG_BOTTOM = 0x1E2450;
    private static final int ORDIS_MAIN = 0xB9A0FF;
    private static final int ORDIS_PALE = 0xE4DAFF;
    private static final int ORDIS_DEEP = 0x6E5FA8;
    /** 立方体中心、半宽（逻辑像素）；核心光点中心和半径 */
    private static final float ORDIS_CX = 44.0F;
    private static final float ORDIS_CY = 28.5F;
    private static final float ORDIS_CORE_X = 44.0F;
    private static final float ORDIS_CORE_Y = 29.0F;
    /** 右上碎块的像素行：逻辑像素 [x, y, 长]，每行一段、从上到下连着排（故障段那两拍再往右上错 (2, -2)） */
    private static final int[] ORDIS_CHUNK = {
            47, 0, 1, 48, 1, 2, 48, 2, 4, 49, 3, 5, 49, 4, 7, 49, 5, 9, 50, 6, 10, 50, 7, 12, 51, 8, 13,
            51, 9, 15, 52, 10, 16, 52, 11, 18, 52, 12, 19, 53, 13, 18, 53, 14, 18, 53, 15, 18, 54, 16, 17,
            55, 17, 16, 56, 18, 15, 57, 19, 14, 58, 20, 13, 61, 21, 10, 63, 22, 8, 66, 23, 5, 68, 24, 3};

    // ---- 原画轮廓（从贴图 alpha 现算，资源重载后重算） ----
    private static final int MAX_CONTOUR = 2048;
    private static short[] contour = new short[0];
    private static byte[] contourDir = new byte[0];
    private static int contourCount;
    private static boolean contourReady;
    private static int lotusState;   // 0 没查过、1 有、2 没有
    private static int ordisState;

    private CommPortraitArt() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Reference.MOD_ID, "textures/gui/comm/" + name + ".png");
    }

    /** 资源重载后重新查原画在不在、重算轮廓 */
    static void invalidate() {
        contourReady = false;
        contourCount = 0;
        lotusState = 0;
        ordisState = 0;
    }

    /** 发言人的底色（缓冲还没画好的那一帧只画这个色块） */
    static int baseColor(CommSpeaker who) {
        return switch (who) {
            case LICH -> 0x1A030A;
            case LOTUS -> 0x0A2229;
            case ORDIS -> 0x10143A;
        };
    }

    // ==================== 入口 ====================

    /**
     * 画一个窗的窗内画面到当前绑定的缓冲
     *
     * @param w      缓冲宽（像素）
     * @param h      缓冲高
     * @param px     「1 像素」在这个屏幕上是几像素（按帧缓冲高 / 1080 取整，至少 1）
     * @param pose   玄骸的外观和说话状态（别的发言人不用）
     * @param timeMs 墙钟毫秒（静止态传冻住的那一刻）
     */
    static void render(CommSpeaker who, LichPuppet.Pose pose, CommPortraits.Frame f, int w, int h, int px,
                       long timeMs, float partialTick, PoseStack ps, MultiBufferSource.BufferSource buffers) {
        switch (who) {
            case LICH -> renderLich(pose, f, w, h, px, timeMs, partialTick, ps, buffers);
            case LOTUS -> renderLotus(f, w, h, px, timeMs);
            case ORDIS -> renderOrdis(f, w, h, px, timeMs);
        }
    }

    // ==================== 玄骸 ====================

    private static void renderLich(LichPuppet.Pose pose, CommPortraits.Frame f, int w, int h, int px, long t,
                                   float partialTick, PoseStack ps, MultiBufferSource.BufferSource buffers) {
        // 底：黑 → 暗赤（60% 高处）→ 深赤毒红（底边）
        BufferBuilder b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float mid = h * LICH_BG_MID_AT;
        gradient(b, 0, 0, w, mid, argb(LICH_BG_TOP, 0.9F), argb(LICH_BG_MID, 0.9F));
        gradient(b, 0, mid, w, h, argb(LICH_BG_MID, 0.9F), argb(LICH_BG_BOTTOM, 0.9F));
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        // 两层烟往上流，横向错开半张
        smokeLayer(w, h, t, LICH_SMOKE_SPEED_A, 0.0F, LICH_SMOKE, LICH_SMOKE_ALPHA);
        smokeLayer(w, h, t, LICH_SMOKE_SPEED_B, 0.5F, LICH_SMOKE, LICH_SMOKE_ALPHA);

        // 左右各一团赤毒烟，中心在 0.75 窗高
        b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        radial(b, 0.0F, h * 0.75F, h * 0.42F, h * 0.36F, argb(LICH_SIDE, LICH_SIDE_ALPHA), argb(LICH_SIDE, 0.0F));
        radial(b, w, h * 0.75F, h * 0.42F, h * 0.36F, argb(LICH_SIDE, LICH_SIDE_ALPHA), argb(LICH_SIDE, 0.0F));
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        // 两侧烟团的芯子：烟贴图做径向扇、加法叠亮红，两层不同速度往上流，叠出来的亮红区域起伏小一些。
        // 只靠上面的暗红烟，窗里没有一处够亮的红，压暗的模型前面就缺了「火光」
        bindLinear(SMOKE);
        float va = (float) ((t / 1000.0D * LICH_EMBER_SPEED) % 1.0D);
        float vb = (float) ((t / 1000.0D * LICH_EMBER_SPEED * 0.67D) % 1.0D);
        int c0 = argb(LICH_EMBER, LICH_EMBER_ALPHA * 0.5F);
        int c1 = argb(LICH_EMBER, 0.0F);
        float ey = h * LICH_EMBER_Y;
        float erx = h * LICH_EMBER_RX;
        float ery = h * LICH_EMBER_RY;
        b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        radialTex(b, 0.0F, ey, erx, ery, h, 0.13F, va, c0, c1);
        radialTex(b, 0.0F, ey, erx, ery, h * 0.7F, 0.58F, vb + 0.21F, c0, c1);
        radialTex(b, w, ey, erx, ery, h, 0.61F, va + 0.37F, c0, c1);
        radialTex(b, w, ey, erx, ery, h * 0.7F, 0.29F, vb + 0.73F, c0, c1);
        CommShaders.draw(b, GameRenderer.getPositionTexColorShader(), CommShaders.BLEND_ADD);

        // 模型：清深度，压暗画，朝屏幕中间
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        float yaw = f.facing > 0 ? -LichPuppet.BUST_YAW : LichPuppet.BUST_YAW;
        LichPuppet.renderBustInto(ps, buffers, w, h, partialTick, pose, yaw, LICH_TINT_R, LICH_TINT_G, LICH_TINT_B);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        // 整窗自下而上的暖光（加法）
        b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        gradient(b, 0, 0, w, h, argb(LICH_WARM, 0.0F), argb(LICH_WARM, LICH_WARM_ALPHA));
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_ADD);

        // 红色微粒：开框 400 ms 后才淡入
        float fadeIn = 1.0F;
        if (f.openMs >= 0) {
            fadeIn = clamp01((f.openMs - CommPortraits.PARTICLES_IN_MS) / 300.0F);
        }
        if (fadeIn > 0.0F) {
            int count = Math.max(3, Math.round(LICH_MOTES * EffectsDisplayConfig.intensity().budgetScale));
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < count; i++) {
                mote(b, i, w, h, px, t, fadeIn);
            }
            CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);
        }
    }

    /** 一张平铺的烟贴图，贴图一格 = 窗高，往上流 */
    private static void smokeLayer(int w, int h, long t, float speed, float uShift, int rgb, float alpha) {
        bindLinear(SMOKE);
        float v0 = (float) ((t / 1000.0D * speed) % 1.0D);
        float u1 = (float) w / h;
        BufferBuilder b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texQuad(b, 0, 0, w, h, uShift, v0, uShift + u1, v0 + 1.0F, argb(rgb, alpha));
        CommShaders.draw(b, GameRenderer.getPositionTexColorShader(), CommShaders.BLEND_NORMAL);
    }

    /** 第 i 颗红点：寿命 2~4 s 往上飘 0.02~0.05 窗高 / s，直径 1~2 个「像素」，α 0.3~0.7，头尾各淡一段 */
    private static void mote(BufferBuilder b, int i, int w, int h, int px, long t, float fadeIn) {
        int life = 2000 + (int) (unit(hash(i, 7)) * 2000.0F);
        long shifted = t + (long) (unit(hash(i, 11)) * life);
        long cycle = shifted / life;
        float tau = (shifted % life) / (float) life;
        int k = hash(i, (int) cycle);
        float x = unit(k) * w;
        float y0 = h * (0.45F + unit(k * 31 + 5) * 0.55F);
        float speed = (0.02F + unit(k * 17 + 3) * 0.03F) * h;
        float y = y0 - speed * tau * life / 1000.0F;
        float size = px * (unit(k * 13 + 1) < 0.5F ? 1.0F : 2.0F);
        float a = (0.3F + unit(k * 7 + 2) * 0.4F) * fadeIn * Math.min(1.0F, Math.min(tau / 0.15F, (1.0F - tau) / 0.25F));
        if (a <= 0.01F) {
            return;
        }
        rect(b, x, y, x + size, y + size, argb(LICH_MOTE, a));
    }

    // ==================== Lotus ====================

    private static void renderLotus(CommPortraits.Frame f, int w, int h, int px, long t) {
        BufferBuilder b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        gradient(b, 0, 0, w, h, argb(LOTUS_BG_TOP, 0.9F), argb(LOTUS_BG_BOTTOM, 0.9F));
        // 身后分段虚线光带：厚 0.025 窗高，段长 0.12 窗宽、间隔 0.05，每秒往左流 0.3 窗宽
        float thick = Math.max(px, h * 0.025F);
        float dash = w * 0.12F;
        float period = dash + w * 0.05F;
        float flow = (float) ((t / 1000.0D * 0.3D * w) % period);
        for (int band = 0; band < LOTUS_BAND_Y.length; band++) {
            float y = h * LOTUS_BAND_Y[band] - thick * 0.5F;
            int c = argb(LOTUS_BAND, LOTUS_BAND_A[band]);
            float start = -period + ((LOTUS_BAND_PHASE[band] * period - flow) % period + period) % period;
            for (float x = start; x < w; x += period) {
                float x0 = Math.max(0.0F, x);
                float x1 = Math.min(w, x + dash);
                if (x1 > x0) {
                    rect(b, x0, y, x1, y + thick, c);
                }
            }
        }
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        if (!hasLotus()) {
            return;
        }
        bindLinear(LOTUS);
        b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texQuad(b, 0, 0, w, h, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
        CommShaders.draw(b, GameRenderer.getPositionTexColorShader(), CommShaders.BLEND_NORMAL);

        float cellW = (float) w / ART_W;
        float cellH = (float) h / ART_H;

        // 说话时擦出期间，最上一瓣整瓣亮到最亮一阶
        if (f.wiping) {
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            runs(b, LOTUS_TOP_PETAL, cellW, cellH, 0.0F, 0.0F, argb(LOTUS_HI, 0.95F));
            CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);
        }

        // 全息边缘：轮廓外 2~4 个像素撒散点，约 30 ms 重新随机一次，越往外越稀
        ensureContour();
        if (contourCount > 0) {
            int step = (int) (t / 33L);
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < contourCount; i++) {
                int k = hash(i, step);
                float r = unit(k);
                int dist;
                float a;
                if (r < 0.22F) {
                    dist = 2;
                    a = 0.55F;
                } else if (r < 0.34F) {
                    dist = 3;
                    a = 0.38F;
                } else if (r < 0.40F) {
                    dist = 4;
                    a = 0.22F;
                } else {
                    continue;
                }
                int packed = contour[i];
                byte dir = contourDir[i];
                float cx = ((packed & 0x7F) + 0.5F) * cellW;
                float cy = ((packed >> 7) + 0.5F) * cellH;
                float dx = (dir & 3) - 1;
                float dy = ((dir >> 2) & 3) - 1;
                float x = cx + dx * (cellW * 0.5F + dist * px);
                float y = cy + dy * (cellH * 0.5F + dist * px);
                rect(b, x, y, x + px, y + px, argb(LOTUS_DOT, a));
            }
            CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

            // 每 1~2 s 冒 1~2 颗青色小火花，往外飘 350 ms
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            long slot = t / 1500L;
            for (long s = slot - 1; s <= slot; s++) {
                int ks = hash((int) s, 977);
                int n = 1 + (ks & 1);
                for (int j = 0; j < n; j++) {
                    int kj = hash(ks, j + 1);
                    long born = s * 1500L + (long) (unit(kj) * 1500.0F);
                    float age = (t - born) / 350.0F;
                    if (age < 0.0F || age > 1.0F) {
                        continue;
                    }
                    int i = (int) (unit(kj * 7 + 3) * contourCount) % contourCount;
                    int packed = contour[i];
                    byte dir = contourDir[i];
                    float dx = (dir & 3) - 1;
                    float dy = ((dir >> 2) & 3) - 1;
                    float x = ((packed & 0x7F) + 0.5F) * cellW + dx * (cellW + age * 6.0F * px);
                    float y = ((packed >> 7) + 0.5F) * cellH + dy * (cellH + age * 6.0F * px);
                    float a = 1.0F - age;
                    float arm = px * 2.0F;
                    rect(b, x - arm, y - px * 0.5F, x + arm, y + px * 0.5F, argb(LOTUS_DOT, 0.8F * a));
                    rect(b, x - px * 0.5F, y - arm, x + px * 0.5F, y + arm, argb(LOTUS_DOT, 0.8F * a));
                    rect(b, x - px * 0.5F, y - px * 0.5F, x + px * 0.5F, y + px * 0.5F, argb(LOTUS_HI, a));
                }
            }
            CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_ADD);
        }

        // 说话态：每 700 ms 一条亮线自上而下扫过人像（只亮在人像上），后面拖两个像素渐暗
        if (f.talking) {
            float y = (t % 700L) / 700.0F * h;
            bindLinear(LOTUS);
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            scanStrip(b, w, h, y, px, 0.9F);
            scanStrip(b, w, h, y - px, px, 0.5F);
            scanStrip(b, w, h, y - 2 * px, px, 0.25F);
            CommShaders.draw(b, GameRenderer.getPositionTexColorShader(), CommShaders.BLEND_ADD);
        }
    }

    /** 原画里 [y, y + hgt) 这一条，染成最亮一阶加上去：透明处贴图 alpha 为 0，加不上去，亮线就只落在人像上 */
    private static void scanStrip(BufferBuilder b, int w, int h, float y, float hgt, float a) {
        float y0 = Math.max(0.0F, y);
        float y1 = Math.min(h, y + hgt);
        if (y1 <= y0) {
            return;
        }
        texQuad(b, 0, y0, w, y1, 0.0F, y0 / h, 1.0F, y1 / h, argb(LOTUS_HI, a));
    }

    // ==================== Ordis ====================

    private static void renderOrdis(CommPortraits.Frame f, int w, int h, int px, long t) {
        float cellW = (float) w / ART_W;
        float cellH = (float) h / ART_H;
        float cx = ORDIS_CX * cellW;
        float cy = ORDIS_CY * cellH;

        BufferBuilder b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        gradient(b, 0, 0, w, h, argb(ORDIS_BG_TOP, 0.9F), argb(ORDIS_BG_BOTTOM, 0.9F));
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        // 立方体外辉
        b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        radial(b, cx, cy, h * 0.62F, h * 0.56F, argb(ORDIS_MAIN, 0.3F), argb(ORDIS_MAIN, 0.0F));
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        // 三片绕转碎块：平时 4 s 一圈，说话时三倍快；绕到后面的先画，被立方体挡住
        double turns = t / (f.talking ? 4000.0D / 3.0D : 4000.0D);
        b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            shard(b, i, turns, cx, cy, cellW, cellH, false);
        }
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        if (hasOrdis()) {
            bindLinear(ORDIS);
            b = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            if (!f.kick) {
                texQuad(b, 0, 0, w, h, 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF);
            } else {
                // 故障段那两拍：右上碎块再往右上错 (2, -2)。原位要挖空，不然看到的是两块：
                // 碎块占的那几行拆成碎块左右两段画，上下其余部分整块画
                int top = ORDIS_CHUNK[1];
                int bottom = ORDIS_CHUNK[ORDIS_CHUNK.length - 2] + 1;
                texQuad(b, 0, 0, w, top * cellH, 0.0F, 0.0F, 1.0F, (float) top / ART_H, 0xFFFFFFFF);
                texQuad(b, 0, bottom * cellH, w, h, 0.0F, (float) bottom / ART_H, 1.0F, 1.0F, 0xFFFFFFFF);
                for (int i = 0; i < ORDIS_CHUNK.length; i += 3) {
                    int cx0 = ORDIS_CHUNK[i];
                    int cx1 = cx0 + ORDIS_CHUNK[i + 2];
                    int row = ORDIS_CHUNK[i + 1];
                    float v0 = (float) row / ART_H;
                    float v1 = (float) (row + 1) / ART_H;
                    texQuad(b, 0, row * cellH, cx0 * cellW, (row + 1) * cellH, 0.0F, v0, (float) cx0 / ART_W, v1,
                            0xFFFFFFFF);
                    texQuad(b, cx1 * cellW, row * cellH, w, (row + 1) * cellH, (float) cx1 / ART_W, v0, 1.0F, v1,
                            0xFFFFFFFF);
                }
                for (int i = 0; i < ORDIS_CHUNK.length; i += 3) {
                    float x0 = ORDIS_CHUNK[i] * cellW;
                    float y0 = ORDIS_CHUNK[i + 1] * cellH;
                    float x1 = x0 + ORDIS_CHUNK[i + 2] * cellW;
                    float y1 = y0 + cellH;
                    texQuad(b, x0 + 2 * cellW, y0 - 2 * cellH, x1 + 2 * cellW, y1 - 2 * cellH,
                            x0 / w, y0 / h, x1 / w, y1 / h, 0xFFFFFFFF);
                }
            }
            CommShaders.draw(b, GameRenderer.getPositionTexColorShader(), CommShaders.BLEND_NORMAL);
        }

        b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            shard(b, i, turns, cx, cy, cellW, cellH, true);
        }
        CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_NORMAL);

        if (f.talking) {
            // 核心 4 Hz 明暗：一圈光晕忽亮忽暗
            float core = 0.25F + 0.15F * (float) Math.sin(t / 1000.0D * Math.PI * 8.0D);
            b = begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            radial(b, ORDIS_CORE_X * cellW, ORDIS_CORE_Y * cellH, cellW * 7.0F, cellH * 7.0F,
                    argb(ORDIS_PALE, core), argb(ORDIS_PALE, 0.0F));
            // 右侧涟漪：每 240 ms 外扩一道，三道同时在
            for (int k = 0; k < 3; k++) {
                float age = ((t % 240L) + k * 240L) / 720.0F;
                float r = (27.0F + age * 19.0F);
                float a = 0.75F * (1.0F - age);
                arc(b, cx, cy, r * cellW, r * cellH, Math.max(px, cellW * 0.9F), -0.62F, 0.62F, argb(ORDIS_PALE, a));
            }
            CommShaders.draw(b, GameRenderer.getPositionColorShader(), CommShaders.BLEND_ADD);
        }
    }

    /** 第 i 片碎块：绕立方体的扁椭圆轨道，一头亮一头暗的小菱形，自己也慢慢转 */
    private static void shard(BufferBuilder b, int i, double turns, float cx, float cy, float cellW, float cellH,
                              boolean front) {
        double ang = (turns + i / 3.0D) * Math.PI * 2.0D;
        float depth = (float) Math.sin(ang);
        if ((depth >= 0.0F) != front) {
            return;
        }
        float ox = cx + (float) Math.cos(ang) * 34.0F * cellW;
        float oy = cy + 4.0F * cellH + depth * 9.0F * cellH - (float) Math.cos(ang) * 3.0F * cellH;
        float scale = 0.85F + 0.25F * (depth + 1.0F) * 0.5F;
        float a = front ? 1.0F : 0.55F;
        float spin = (float) (turns * Math.PI * 2.0D * (i == 1 ? -1.6D : 1.3D)) + i;
        float rx = (2.6F + i * 0.4F) * cellW * scale;
        float ry = (1.6F + (i % 2) * 0.5F) * cellH * scale;
        float c = (float) Math.cos(spin);
        float s = (float) Math.sin(spin);
        // 菱形四个角：上、右、下、左（自转后）
        float tx = ox - s * ry;
        float ty = oy - c * ry;
        float rxp = ox + c * rx;
        float ryp = oy - s * rx;
        float bx = ox + s * ry;
        float by = oy + c * ry;
        float lx = ox - c * rx;
        float ly = oy + s * rx;
        int hi = argb(ORDIS_PALE, a);
        int mid = argb(ORDIS_MAIN, a);
        int deep = argb(ORDIS_DEEP, a);
        tri(b, lx, ly, tx, ty, rxp, ryp, hi, hi, mid);
        tri(b, lx, ly, rxp, ryp, bx, by, mid, mid, deep);
    }

    // ==================== 原画 ====================

    private static boolean hasLotus() {
        if (lotusState == 0) {
            lotusState = exists(LOTUS) ? 1 : 2;
        }
        return lotusState == 1;
    }

    private static boolean hasOrdis() {
        if (ordisState == 0) {
            ordisState = exists(ORDIS) ? 1 : 2;
        }
        return ordisState == 1;
    }

    private static boolean exists(ResourceLocation loc) {
        return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent();
    }

    /**
     * 从 Lotus 原画的 alpha 算轮廓点：本身不透明、四邻里有透明的、而且那个透明邻居在图内
     * （肩和手臂在下沿、两侧直接出画，那是被窗裁掉的，不算轮廓，在那撒点会出一条线）
     */
    private static void ensureContour() {
        if (contourReady) {
            return;
        }
        contourReady = true;
        contourCount = 0;
        if (!hasLotus()) {
            return;
        }
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(LOTUS);
        if (res.isEmpty()) {
            return;
        }
        boolean[] solid = new boolean[ART_W * ART_H];
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            float sx = img.getWidth() / (float) ART_W;
            float sy = img.getHeight() / (float) ART_H;
            for (int y = 0; y < ART_H; y++) {
                for (int x = 0; x < ART_W; x++) {
                    int px = img.getPixelRGBA((int) (x * sx), (int) (y * sy));
                    solid[y * ART_W + x] = ((px >>> 24) & 0xFF) > 127;
                }
            }
        } catch (Exception e) {
            return;
        }
        short[] pts = new short[MAX_CONTOUR];
        byte[] dirs = new byte[MAX_CONTOUR];
        int n = 0;
        for (int y = 0; y < ART_H && n < MAX_CONTOUR; y++) {
            for (int x = 0; x < ART_W && n < MAX_CONTOUR; x++) {
                if (!solid[y * ART_W + x]) {
                    continue;
                }
                int dx = 0;
                int dy = 0;
                if (x > 0 && !solid[y * ART_W + x - 1]) {
                    dx--;
                }
                if (x < ART_W - 1 && !solid[y * ART_W + x + 1]) {
                    dx++;
                }
                if (y > 0 && !solid[(y - 1) * ART_W + x]) {
                    dy--;
                }
                if (y < ART_H - 1 && !solid[(y + 1) * ART_W + x]) {
                    dy++;
                }
                if (dx == 0 && dy == 0) {
                    continue;
                }
                pts[n] = (short) (x | (y << 7));
                dirs[n] = (byte) ((dx + 1) | ((dy + 1) << 2));
                n++;
            }
        }
        contour = pts;
        contourDir = dirs;
        contourCount = n;
    }

    // ==================== 小工具 ====================

    static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(mode, format);
        return b;
    }

    /** 绑定贴图并设成线性过滤（全息、烟本来就该是软的） */
    static void bindLinear(ResourceLocation loc) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(loc);
        tex.setFilter(true, false);
        RenderSystem.setShaderTexture(0, tex.getId());
    }

    static int argb(int rgb, float a) {
        int ai = Math.round(clamp01(a) * 255.0F);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    static void gradient(BufferBuilder b, float x0, float y0, float x1, float y1, int top, int bottom) {
        b.vertex(x0, y0, 0).color(top).endVertex();
        b.vertex(x0, y1, 0).color(bottom).endVertex();
        b.vertex(x1, y1, 0).color(bottom).endVertex();
        b.vertex(x1, y0, 0).color(top).endVertex();
    }

    static void rect(BufferBuilder b, float x0, float y0, float x1, float y1, int c) {
        gradient(b, x0, y0, x1, y1, c, c);
    }

    static void texQuad(BufferBuilder b, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                        int c) {
        b.vertex(x0, y0, 0).uv(u0, v0).color(c).endVertex();
        b.vertex(x0, y1, 0).uv(u0, v1).color(c).endVertex();
        b.vertex(x1, y1, 0).uv(u1, v1).color(c).endVertex();
        b.vertex(x1, y0, 0).uv(u1, v0).color(c).endVertex();
    }

    private static void tri(BufferBuilder b, float x0, float y0, float x1, float y1, float x2, float y2,
                            int c0, int c1, int c2) {
        b.vertex(x0, y0, 0).color(c0).endVertex();
        b.vertex(x1, y1, 0).color(c1).endVertex();
        b.vertex(x2, y2, 0).color(c2).endVertex();
    }


    /** 椭圆径向渐变，带平铺贴图（贴图一格 = tile 像素，uv 跟着位置走） */
    static void radialTex(BufferBuilder b, float cx, float cy, float rx, float ry, float tile, float u0, float v0,
                          int center, int edge) {
        int seg = 28;
        float px0 = cx + rx;
        float py0 = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2.0D * i / seg;
            float px1 = cx + (float) Math.cos(a) * rx;
            float py1 = cy + (float) Math.sin(a) * ry;
            b.vertex(cx, cy, 0).uv(u0 + cx / tile, v0 + cy / tile).color(center).endVertex();
            b.vertex(px1, py1, 0).uv(u0 + px1 / tile, v0 + py1 / tile).color(edge).endVertex();
            b.vertex(px0, py0, 0).uv(u0 + px0 / tile, v0 + py0 / tile).color(edge).endVertex();
            px0 = px1;
            py0 = py1;
        }
    }

    /** 椭圆径向渐变（三角形扇，拆成独立三角形） */
    static void radial(BufferBuilder b, float cx, float cy, float rx, float ry, int center, int edge) {
        int seg = 28;
        float px0 = cx + rx;
        float py0 = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2.0D * i / seg;
            float px1 = cx + (float) Math.cos(a) * rx;
            float py1 = cy + (float) Math.sin(a) * ry;
            tri(b, cx, cy, px1, py1, px0, py0, center, edge, edge);
            px0 = px1;
            py0 = py1;
        }
    }

    /** 一段等宽的椭圆弧（角度弧度，0 = 正右，往下为正） */
    private static void arc(BufferBuilder b, float cx, float cy, float rx, float ry, float thick, float a0, float a1,
                            int c) {
        int seg = 12;
        float half = thick * 0.5F;
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + (a1 - a0) * i / seg;
            double t1 = a0 + (a1 - a0) * (i + 1) / seg;
            float c0 = (float) Math.cos(t0);
            float s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1);
            float s1 = (float) Math.sin(t1);
            float ix0 = cx + c0 * (rx - half);
            float iy0 = cy + s0 * (ry - half);
            float ox0 = cx + c0 * (rx + half);
            float oy0 = cy + s0 * (ry + half);
            float ix1 = cx + c1 * (rx - half);
            float iy1 = cy + s1 * (ry - half);
            float ox1 = cx + c1 * (rx + half);
            float oy1 = cy + s1 * (ry + half);
            tri(b, ix0, iy0, ox0, oy0, ox1, oy1, c, c, c);
            tri(b, ix0, iy0, ox1, oy1, ix1, iy1, c, c, c);
        }
    }

    /** 逻辑像素行表（[x, y, 长] 一组）画成色块 */
    private static void runs(BufferBuilder b, int[] runs, float cellW, float cellH, float ox, float oy, int c) {
        for (int i = 0; i < runs.length; i += 3) {
            float x0 = (runs[i] + ox) * cellW;
            float y0 = (runs[i + 1] + oy) * cellH;
            rect(b, x0, y0, x0 + runs[i + 2] * cellW, y0 + cellH, c);
        }
    }

    static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    /** 两个整数混成一个哈希 */
    static int hash(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** 哈希 → [0, 1) */
    static float unit(int h) {
        return ((h >>> 8) & 0xFFFFFF) / 16777216.0F;
    }
}
