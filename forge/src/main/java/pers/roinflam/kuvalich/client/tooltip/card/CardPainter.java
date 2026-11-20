package pers.roinflam.kuvalich.client.tooltip.card;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.config.HoverCardConfig;

import java.util.List;

/**
 * 把一张卡按当前开合状态整张画进离屏帧缓冲（物理像素，一单位 ps 像素），再由 {@link HoverCardController} 贴到屏幕上。
 * 画布里存预乘色。
 */
final class CardPainter {

    private static final float W = CardLayout.W;
    private static final float BOT_H = 29;
    private static final float PILL_V = 6.5F;
    private static final float LANE_V = 30.6F;
    private static final float FADE = 20;

    private static final int ROT_MS = 2500;
    private static final int XFADE_MS = 400;

    private static final float[] HAZE_STOPS = {0, 0.12F, 0.34F, 0.64F, 1};
    private static final float[] HAZE_A = {1, 0.74F, 0.36F, 0.10F, 0};

    private static final float[] RAMP_PRIME_S = {0, 0.5F, 1};
    private static final int[] RAMP_PRIME_C = {0x7c868f, 0xd6dde3, 0xffffff};
    private static final float[] RAMP_ARCHON_S = {0, 0.35F, 0.7F, 1};
    private static final float[] RAMP_S = {0, 0.45F, 0.8F, 1};
    private static final float[] SHADE_S = {0, 0.35F, 0.7F, 1};
    private static final int[] SHADE_C = {0, 0, 0, 0};
    private static final float[] GLINT_S = {0, 0.5F, 0.85F, 1};
    private static final float[] SCRATCH_A = new float[4];
    private static final int[] SCRATCH_C = new int[4];
    private static final int[] SCRATCH_RAMP = new int[4];

    private static final int MAX_CANVAS = 4096;

    private static TextureTarget target;
    private static int ss = 1;
    private static GuiGraphics graphics;
    private static final Matrix4f PROJ = new Matrix4f();

    private CardPainter() {
    }

    static TextureTarget target() {
        return target;
    }

    static void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        graphics = null;
    }

    /** 画布只增不减，换卡时不重建 */
    private static void ensureTarget(int w, int h) {
        if (target == null) {
            target = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (target.width < w || target.height < h) {
            target.resize(Math.max(w, target.width), Math.max(h, target.height), Minecraft.ON_OSX);
        }
    }

    /**
     * 这次画布放大了几倍。倾斜开着时卡几乎总是带角度贴到屏幕上，一比一的画布重采样后字会发虚，
     * 所以按两倍画、贴的时候缩回去；不倾斜时一比一最锐利，不放大。
     */
    static int ss() {
        return ss;
    }

    static void paint(CardLayout l, CardState st, long now, long hoverStart) {
        Minecraft mc = Minecraft.getInstance();
        ss = HoverCardConfig.tilt() && Math.max(l.cw, l.ch) * 2 <= MAX_CANVAS ? 2 : 1;
        ensureTarget(l.cw * ss, l.ch * ss);
        if (graphics == null) {
            graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        }
        // glClear 受裁剪框限制，先关掉
        RenderSystem.disableScissor();
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        PROJ.setOrtho(0, target.width, target.height, 0, -1000, 1000);
        RenderSystem.setProjectionMatrix(PROJ, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.scale(ss, ss, 1);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        try {
            CardDraw.ox = l.ox;
            CardDraw.oy = l.oy;
            CardFonts.ss = ss;
            draw(l, st, now, hoverStart);
            CardDraw.flush();
        } finally {
            CardFonts.ss = 1;
            RenderSystem.disableScissor();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            CardDraw.ox = 0;
            CardDraw.oy = 0;
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void draw(CardLayout l, CardState st, long now, long hoverStart) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        int wp = l.wp;
        float v0 = st.v0, v1 = st.v1, bt = v1 - BOT_H;
        CardDraw.blendNormal();

        // 1. 投影：九宫格的竖向三段，上 28、下 38 单位，中间拉伸
        if (st.shadow > 0.01F) {
            float a = 0.8F * st.shadow;
            float sy = 2.5F;
            float x0 = -8 * ps, x1 = (W + 8) * ps;
            float top = (v0 - 8 + sy) * ps, bot = (v1 + 8 + sy) * ps;
            float tH = 28 * ps, bH = 38 * ps;
            float midA = top + tH, midB = bot - bH;
            if (midB < midA) {
                float k = (bot - top) / (tH + bH);
                midA = top + tH * k;
                midB = midA;
            }
            CardDraw.texture(CardTextures.SHADOW, true);
            CardDraw.blit(x0, top, x1, midA, 0, 0, 1, 28 / 160.0F, 0x000000, a);
            CardDraw.blit(x0, midA, x1, midB, 0, 28 / 160.0F, 1, 122 / 160.0F, 0x000000, a);
            CardDraw.blit(x0, midB, x1, bot, 0, 122 / 160.0F, 1, 1, 0x000000, a);
        }

        // 2. 底板
        float plateTop = v0 * ps, plateMid = (v0 + 4) * ps, plateLow = (bt + 14) * ps, plateBot = (bt + 27) * ps;
        CardDraw.texture(CardTextures.PLATE_TOP, true);
        CardDraw.blitAll(0, plateTop, wp, plateMid, t.body, 1);
        CardDraw.texture(CardTextures.PLATE_BOT, true);
        CardDraw.blitAll(0, plateLow, wp, plateBot, t.body, 1);
        CardDraw.rect(0, plateMid, wp, plateLow, t.body, 1);
        // 底部泛上来的一点品质色：bt-34 到 bt+27 透明到 0.12
        float g0 = (bt - 34) * ps, g1 = plateBot;
        float gs = Math.max(g0, plateMid);
        if (gs < plateLow) {
            CardDraw.vgrad(0, gs, wp, plateLow, t.net, 0.12F * (gs - g0) / (g1 - g0), t.net, 0.12F * (plateLow - g0) / (g1 - g0));
        }
        CardDraw.texture(CardTextures.PLATE_BOT, true);
        CardDraw.blitV(0, plateLow, wp, plateBot, 0, 0, 1, 1, t.net, 0.12F * (plateLow - g0) / (g1 - g0), 0.12F);

        // 3～5. 卡面图和光雾，裁在 v0..artEnd
        float clipTop = Math.max(0, v0 + 0.4F);
        float clipBot = Math.min(l.artEnd, bt + 27);
        if (clipTop < clipBot) {
            scissor(l, clipTop * ps, clipBot * ps);
            drawArt(l, st, now, hoverStart);
            drawHaze(l);
            RenderSystem.disableScissor();
        }

        // 6. 文字，裁在底板里
        if (st.txt > 0.003F) {
            CardDraw.flush();
            scissor(l, v0 * ps, plateBot);
            drawText(l, st, bt);
            RenderSystem.disableScissor();
        }
        CardDraw.blendNormal();

        // 7、8. 两侧和上框、底托
        if (st.frameA > 0.003F) {
            if (l.compact) {
                sideRails(l, st, v0, bt);
                CardTextures.Compact c = CardTextures.compact(t, wp);
                if (c != null) {
                    CardDraw.texture(CardTextures.compactTop(t, wp), false);
                    float y = Math.round(v0 * ps) - c.topOy();
                    CardDraw.blitAll(-c.ex(), y, -c.ex() + c.tw(), y + c.th(), 0xFFFFFF, st.frameA);
                    CardDraw.texture(CardTextures.compactBot(t, wp), false);
                    float yb = Math.round(bt * ps) - c.botOy();
                    CardDraw.blitAll(-c.ex(), yb, -c.ex() + c.bw(), yb + c.bh(), 0xFFFFFF, st.frameA);
                }
                cornerLights(l, st, v0, bt);
            } else {
                sideLights(l, st, v0, bt);
                CardTextures.Large g = CardTextures.large(t, l.thin);
                if (g != null) {
                    float x0 = -g.el() * ps, x1 = x0 + g.fw() * ps;
                    CardDraw.texture(CardTextures.largeTop(t, l.thin), true);
                    float y = (v0 - g.et()) * ps;
                    CardDraw.blitAll(x0, y, x1, y + g.th() / (float) g.ts() * ps, 0xFFFFFF, st.frameA);
                    CardDraw.texture(CardTextures.largeBot(t, l.thin), true);
                    float yb = (bt - g.up()) * ps;
                    CardDraw.blitAll(x0, yb, x1, yb + g.bh() / (float) g.ts() * ps, 0xFFFFFF, st.frameA);
                }
            }
            CardDraw.blendNormal();
        }

        // 9. 类型条与未揭示角标
        if (st.pillA > 0.01F) {
            drawPill(l, st, bt + PILL_V - l.pillUp);
            if (l.model.veiled) {
                drawTag(l, st, v0);
            }
        }

        // 10. 等级点
        if (!l.model.veiled) {
            drawPips(l, st, bt);
        }
        CardDraw.flush();
    }

    /** 画布里的裁剪：卡身横向全宽，纵向 y0..y1（相对卡左上，物理像素）。GL 的裁剪框原点在左下 */
    private static void scissor(CardLayout l, float y0, float y1) {
        CardDraw.flush();
        int top = Math.round(l.oy + y0);
        int bot = Math.round(l.oy + y1);
        RenderSystem.enableScissor(l.ox * ss, target.height - bot * ss, l.wp * ss, Math.max(0, bot - top) * ss);
    }

    // ---------------------------------------------------------------- 卡面图

    /** 卡面图按渐隐分的三段：0、artEnd-20、artEnd-10、artEnd */
    static float band(float artEnd, int i) {
        switch (i) {
            case 0:
                return 0;
            case 1:
                return Math.max(0, artEnd - FADE);
            case 2:
                return Math.max(0, artEnd - FADE / 2);
            default:
                return artEnd;
        }
    }

    static float artFade(float yUnits, float artEnd) {
        float d = artEnd - yUnits;
        if (d >= FADE) {
            return 1;
        }
        if (d >= FADE / 2) {
            return 0.6F + 0.4F * (d - FADE / 2) / (FADE / 2);
        }
        return Math.max(0, 0.6F * d / (FADE / 2));
    }

    private static void drawArt(CardLayout l, CardState st, long now, long hoverStart) {
        CardModel m = l.model;
        if (m.veiled) {
            CardVeil.draw(l, st, now);
            return;
        }
        List<String> themes = m.themes;
        if (themes.size() == 1) {
            CardDraw.texture(HoverCardArt.texture(themes.get(0)), true);
            artCover(l, 392, 448, st.zoom, st.dx, st.dy, 1);
            return;
        }
        long tt = Math.max(0, now - hoverStart);
        int idx = (int) ((tt / ROT_MS) % themes.size());
        long inSlot = tt % ROT_MS;
        CardDraw.texture(HoverCardArt.texture(themes.get(idx)), true);
        artCover(l, 392, 448, st.zoom, st.dx, st.dy, 1);
        if (inSlot > ROT_MS - XFADE_MS) {
            float f = (inSlot - (ROT_MS - XFADE_MS)) / (float) XFADE_MS;
            CardDraw.texture(HoverCardArt.texture(themes.get((idx + 1) % themes.size())), true);
            artCover(l, 392, 448, st.zoom, st.dx, st.dy, f * f * (3 - 2 * f));
        }
    }

    /**
     * 等比铺满卡面框（裁掉多出的部分），以中心放大 zoom，再平移 (dx, dy) 单位。
     * 下沿 20 单位渐隐，分三段画，每段顶点带不同的不透明度。
     */
    private static void artCover(CardLayout l, int texW, int texH, float zoom, float dxU, float dyU, float alpha) {
        float ps = l.ps;
        float boxW = W * ps, boxH = l.artEnd * ps;
        float scale = Math.max(boxW / texW, boxH / texH) * zoom;
        float uw = boxW / scale / texW, vh = boxH / scale / texH;
        float u0 = 0.5F - uw / 2 - dxU * ps / scale / texW;
        float v0 = 0.5F - vh / 2 - dyU * ps / scale / texH;
        for (int i = 0; i < 3; i++) {
            float ya = band(l.artEnd, i), yb = band(l.artEnd, i + 1);
            if (yb <= ya) {
                continue;
            }
            float va = v0 + vh * ya / l.artEnd, vb = v0 + vh * yb / l.artEnd;
            CardDraw.blitV(0, ya * ps, boxW, yb * ps, u0, va, u0 + uw, vb, 0xFFFFFF,
                    alpha * artFade(ya, l.artEnd), alpha * artFade(yb, l.artEnd));
        }
    }

    /** 光雾：卡面图左右和上沿三条滤色叠加的品质色，向内渐隐 */
    private static void drawHaze(CardLayout l) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        boolean prime = t == CardTier.PRIME;
        int col = CardLayout.mix(t.halo, t.hi, 0.3F);
        float a = 0.8F * (prime ? 1.15F : 1) * (l.model.veiled ? 0.3F : 1);
        float sw = 0.27F * W * (prime ? 1.3F : 1);
        float sh = 0.27F * 0.8F * W * (prime ? 1.3F : 1);
        float ae = l.artEnd;
        CardDraw.blendScreen();
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i + 1 < HAZE_STOPS.length; i++) {
                float xa = HAZE_STOPS[i] * sw, xb = HAZE_STOPS[i + 1] * sw;
                float pa = HAZE_A[i] * a, pb = HAZE_A[i + 1] * a;
                float x0 = side == 0 ? xa : W - xb, x1 = side == 0 ? xb : W - xa;
                float aL = side == 0 ? pa : pb, aR = side == 0 ? pb : pa;
                for (int j = 0; j < 3; j++) {
                    float ya = band(ae, j), yb = band(ae, j + 1);
                    if (yb <= ya) {
                        continue;
                    }
                    float fa = artFade(ya, ae), fb = artFade(yb, ae);
                    CardDraw.screenQuad(x0 * ps, ya * ps, x1 * ps, yb * ps, col, aL * fa, aR * fa, aR * fb, aL * fb);
                }
            }
        }
        for (int i = 0; i + 1 < HAZE_STOPS.length; i++) {
            float ya = HAZE_STOPS[i] * sh, yb = HAZE_STOPS[i + 1] * sh;
            float pa = HAZE_A[i] * a * artFade(ya, ae), pb = HAZE_A[i + 1] * a * artFade(yb, ae);
            CardDraw.screenQuad(0, ya * ps, W * ps, yb * ps, col, pa, pa, pb, pb);
        }
        if (prime) {
            for (int j = 0; j < 3; j++) {
                float ya = band(ae, j), yb = band(ae, j + 1);
                if (yb > ya) {
                    float fa = 0.13F * artFade(ya, ae), fb = 0.13F * artFade(yb, ae);
                    CardDraw.screenQuad(0, ya * ps, W * ps, yb * ps, col, fa, fa, fb, fb);
                }
            }
        }
        CardDraw.blendNormal();
    }

    // ---------------------------------------------------------------- 文字

    private static void drawText(CardLayout l, CardState st, float bt) {
        GuiGraphics g = graphics;
        g.pose().pushPose();
        g.pose().translate(l.ox, l.oy, 0);
        float clipY = bt + PILL_V - l.pillUp;
        for (CardLayout.Line ln : l.lines) {
            float k = st.txt;
            if (!ln.name) {
                k *= CardState.clamp((clipY - ln.yUnits - 3) / 8, 0, 1);
            }
            if (k <= 0.02F) {
                continue;
            }
            if (ln.name) {
                ln.run.draw(g, ln.x + 1, ln.boxTop + 1, 0x000000, 0.6F * k);
                ln.run.draw(g, ln.x, ln.boxTop, ln.color, k);
            } else {
                ln.run.draw(g, ln.x, ln.boxTop, ln.color, k);
            }
        }
        g.flush();
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- 两侧

    /** 小卡：金属侧边，外暗内亮，竖向中段压暗，外侧一圈品质色淡光 */
    private static void sideRails(CardLayout l, CardState st, float v0, float bt) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        int wp = l.wp;
        int y0 = Math.round((v0 + 3) * ps), y1 = Math.round((bt + 6) * ps), len = y1 - y0;
        if (len <= 4) {
            return;
        }
        float fa = st.frameA;
        int rw = Math.max(2, Math.round(2 * ps));
        int hw = Math.max(2, Math.min(4, (int) Math.floor(t.el * ps) - rw));
        boolean prime = t == CardTier.PRIME, archon = t == CardTier.ARCHON;
        float[] rs;
        int[] rc = SCRATCH_RAMP;
        if (prime) {
            rs = RAMP_PRIME_S;
            rc = RAMP_PRIME_C;
        } else if (archon) {
            rs = RAMP_ARCHON_S;
            rc[0] = CardTier.ARCHON_GOLD2;
            rc[1] = t.sd;
            rc[2] = t.mid;
            rc[3] = CardTier.ARCHON_GOLD;
        } else {
            rs = RAMP_S;
            rc[0] = CardLayout.mix(t.edge, t.lo, 0.6F);
            rc[1] = t.lo;
            rc[2] = CardLayout.mix(t.lo, t.mid, 0.7F);
            rc[3] = t.hi;
        }
        float haloA = 0.28F * (t.haloA + 0.4F) * fa;
        for (int side = 0; side < 2; side++) {
            boolean right = side == 1;
            int x0 = right ? wp : -rw;
            int hx0 = right ? wp + rw : -rw - hw;
            if (right) {
                CardDraw.hgrad(hx0, y0, hx0 + hw, y1, t.halo, haloA, t.halo, 0);
            } else {
                CardDraw.hgrad(hx0, y0, hx0 + hw, y1, t.halo, 0, t.halo, haloA);
            }
            // 每一列按外到内的位置取色
            for (int j = 0; j < rw; j++) {
                float f = (j + 0.5F) / rw;
                int col = ramp(rs, rc, f);
                int x = right ? x0 + rw - 1 - j : x0 + j;
                CardDraw.rect(x, y0, x + 1, y1, col, fa);
            }
            float[] sa = SCRATCH_A;
            sa[0] = 0.15F * fa;
            sa[1] = (prime ? 0.18F : 0.45F) * fa;
            sa[2] = 0.25F * fa;
            sa[3] = 0;
            CardDraw.vstops(x0, y0, x0 + rw, y1, SHADE_S, SHADE_C, sa);
            int gx = right ? wp - 1 : 0;
            int[] gc = SCRATCH_C;
            gc[0] = gc[1] = gc[2] = gc[3] = t.hi;
            sa[0] = 0.25F * fa;
            sa[1] = 0.12F * fa;
            sa[2] = 0.6F * fa;
            sa[3] = 0.85F * fa;
            CardDraw.vstops(gx, y0, gx + 1, y1, GLINT_S, gc, sa);
            int sx = right ? x0 + rw : x0 - 1;
            CardDraw.rect(sx, y0, sx + 1, y1, t.edge, 0.7F * fa);
        }
    }

    private static int ramp(float[] stops, int[] cols, float f) {
        for (int i = 0; i + 1 < stops.length; i++) {
            if (f <= stops[i + 1]) {
                float k = (f - stops[i]) / (stops[i + 1] - stops[i]);
                return CardLayout.mix(cols[i], cols[i + 1], k);
            }
        }
        return cols[cols.length - 1];
    }

    private static void cornerLights(CardLayout l, CardState st, float v0, float bt) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        int rw = Math.max(2, Math.round(2 * ps));
        float y = Math.round((bt - 1.5F) * ps);
        if (y - Math.round(v0 * ps) < 12 * ps) {
            return;
        }
        float r = Math.max(2.5F, 2.2F * ps);
        float a = (0.55F + 0.3F * t.spec) * st.frameA;
        CardDraw.blendAdd();
        CardDraw.texture(CardTextures.STAR, true);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? -rw / 2.0F : l.wp + rw / 2.0F;
            CardDraw.blitAll(x - r, y - r, x + r, y + r, t.star, a);
        }
        CardDraw.blendNormal();
    }

    /** 大卡：每侧一道品质色淡光和一条下端最亮的细光 */
    private static void sideLights(CardLayout l, CardState st, float v0, float bt) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        float len = Math.min(70, bt + 4 - v0 - 8);
        if (len <= 0) {
            return;
        }
        float fa = st.frameA;
        float y1 = (bt + 4) * ps, y0 = y1 - len * ps, ym = (y0 + y1) / 2;
        for (int side = 0; side < 2; side++) {
            float cx = (side == 0 ? 0.3F : W - 0.3F) * ps;
            CardDraw.vgrad(cx - 1.2F * ps, y0, cx + 1.2F * ps, y1, t.halo, 0, t.halo, 0.2F * fa);
            float hb = 0.4F * ps, hm = hb / 2;
            CardDraw.quad(cx, y0, cx - hm, ym, cx + hm, ym, cx, y0, t.hi, 0, t.hi, 0.35F * fa, t.hi, 0.35F * fa, t.hi, 0);
            CardDraw.quad(cx - hm, ym, cx - hb, y1, cx + hb, y1, cx + hm, ym,
                    t.hi, 0.35F * fa, t.hi, 0.95F * fa, t.hi, 0.95F * fa, t.hi, 0.35F * fa);
        }
    }

    // ---------------------------------------------------------------- 类型条

    private static void drawPill(CardLayout l, CardState st, float pyU) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        int wp = l.wp;
        float a = st.pillA;
        int top = Math.round(pyU * ps);
        int h = Math.round(l.pillH * ps);
        int pw = 2 * Math.round((W - 20) * ps / 2);
        int x0 = (wp - pw) / 2;
        int e = Math.round(5 * ps);
        float mid = top + h / 2.0F;
        // 逐行填，斜边落在整像素上
        for (int r = 0; r < h; r++) {
            float d = Math.abs(r + 0.5F - h / 2.0F) / (h / 2.0F);
            int inset = Math.round(e * d);
            int lx = x0 + inset, rx = x0 + pw - inset;
            CardDraw.rect(lx, top + r, rx, top + r + 1, 0x040508, 0.9F * a);
            float f = h <= 1 ? 0 : r / (float) (h - 1);
            int sc = f < 0.5F ? CardLayout.mix(t.hi, t.line, f / 0.5F) : t.line;
            float sa = f < 0.5F ? 1 : 1 - 0.4F * (f - 0.5F) / 0.5F;
            if (r == 0 || r == h - 1) {
                CardDraw.rect(lx, top + r, rx, top + r + 1, sc, sa * a);
            } else {
                CardDraw.rect(lx, top + r, lx + 1, top + r + 1, sc, sa * a);
                CardDraw.rect(rx - 1, top + r, rx, top + r + 1, sc, sa * a);
            }
        }
        int tc = CardLayout.mix(t.name, 0xFFFFFF, 0.2F);
        CardFonts.Run txt = l.pillText;
        // 条里的字和点差半像素时都往上偏
        int boxTop = top + (h - l.fP) / 2;
        int tx = Math.round((wp - txt.width) / 2.0F);
        CardModel m = l.model;
        // 没揭示的裂罅卡还没有倾向和循环次数，条里只放字
        if (!m.riven || m.veiled) {
            textAt(l, txt, tx, boxTop, tc, a);
            return;
        }
        int ds = ps >= 1 ? 3 : 2, dp = ds + 2, dx0 = x0 + e + 1, dEnd = dx0 + 4 * dp + ds;
        int dy = top + (h - ds) / 2;
        for (int i = 0; i < 5; i++) {
            if (i < m.trend) {
                CardDraw.rect(dx0 + i * dp, dy, dx0 + i * dp + ds, dy + ds, tc, a);
            } else {
                CardDraw.rect(dx0 + i * dp, dy, dx0 + i * dp + ds, dy + ds, t.line, 0.4F * a);
            }
        }
        int right = x0 + pw - e - 1;
        if (l.cycleText != null) {
            int c0 = right - l.cycleText.width;
            right = c0 - 3;
            textAt(l, l.cycleText, c0, boxTop, tc, a);
            float r = 3.5F, ix = c0 - 4 - r;
            if (ix - r - 3 >= dEnd) {
                CardDraw.texture(CardTextures.CYCLE, true);
                CardDraw.blitAll(ix - r, mid - r, ix + r, mid + r, tc, a);
            }
        }
        if (tx >= dEnd + 3 && tx + txt.width <= right - 1) {
            textAt(l, txt, tx, boxTop, tc, a);
        }
    }

    /** 未揭示卡右上角「???」小牌：切掉右上、左下两角 */
    private static void drawTag(CardLayout l, CardState st, float v0) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        float a = st.pillA;
        CardFonts.Run txt = l.tagText;
        int tf = l.fT;
        float tu = tf / 7.0F;
        int padX = Math.max(2, Math.round(tf * 0.3F)), padY = Math.max(1, Math.round(tf * 0.15F));
        int w = txt.width + 2 * padX, h = tf + 2 * padY;
        int x1 = l.wp - Math.max(3, Math.round(3 * ps));
        int y0 = Math.round(v0 * ps) + Math.max(3, Math.round(3.4F * ps));
        int x0 = x1 - w;
        int c = Math.max(2, Math.round(1.6F * tu));
        int lw = Math.max(1, Math.round(tu * 0.6F));
        for (int r = 0; r < h; r++) {
            int rcut = Math.max(0, c - r);
            int lcut = Math.max(0, r - (h - 1 - c));
            int lx = x0 + lcut, rx = x1 - rcut;
            boolean edge = r < lw || r >= h - lw;
            CardDraw.rect(lx, y0 + r, rx, y0 + r + 1, 0x090812, 0.86F * a);
            if (edge) {
                CardDraw.rect(lx, y0 + r, rx, y0 + r + 1, t.line, 0.95F * a);
            } else {
                CardDraw.rect(lx, y0 + r, lx + lw, y0 + r + 1, t.line, 0.95F * a);
                CardDraw.rect(rx - lw, y0 + r, rx, y0 + r + 1, t.line, 0.95F * a);
            }
        }
        textAt(l, txt, x0 + padX, y0 + padY, CardLayout.mix(t.name, 0xFFFFFF, 0.15F), a);
    }

    private static void textAt(CardLayout l, CardFonts.Run run, int x, int boxTop, int rgb, float a) {
        CardDraw.flush();
        GuiGraphics g = graphics;
        g.pose().pushPose();
        g.pose().translate(l.ox, l.oy, 0);
        run.draw(g, x, boxTop, rgb, a);
        g.flush();
        g.pose().popPose();
        CardDraw.blendNormal();
    }

    // ---------------------------------------------------------------- 等级点

    private static void drawPips(CardLayout l, CardState st, float bt) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        int lp = Math.max(4, Math.round(4.6F * ps));
        int d = lp % 2 == 1 ? 3 : 2;
        int y = Math.round(bt * ps) + Math.round(LANE_V * ps - d / 2.0F);
        int x0 = (l.wp - (9 * lp + d)) / 2;
        int lv = l.model.level;
        boolean archon = t == CardTier.ARCHON;
        // 先画亮点的辉光，再画方点
        CardDraw.texture(CardTextures.GLOW, true);
        for (int i = 0; i < 10; i++) {
            float lit = i < lv ? CardState.clamp(st.pipT - i, 0, 1) : 0;
            if (lit > 0) {
                float cx = x0 + i * lp + d / 2.0F, cy = y + d / 2.0F, r = d + 2.5F;
                CardDraw.blitAll(cx - r, cy - r, cx + r, cy + r, t.groove, 0.7F * lit);
            }
        }
        for (int i = 0; i < 10; i++) {
            boolean on = i < lv;
            float lit = on ? CardState.clamp(st.pipT - i, 0, 1) : 0;
            int px = x0 + i * lp;
            if (lit > 0) {
                int col = archon ? CardTier.ARCHON_PIP
                        : ((200 + (int) (55 * lit)) << 16) | ((235 + (int) (20 * lit)) << 8) | 255;
                CardDraw.rect(px, y, px + d, y + d, col, 0.45F + 0.55F * lit);
            } else {
                CardDraw.rect(px, y, px + d, y + d, t.groove, on ? 0.3F : 0.22F);
            }
        }
    }
}
