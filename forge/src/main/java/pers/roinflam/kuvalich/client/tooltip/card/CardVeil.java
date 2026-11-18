package pers.roinflam.kuvalich.client.tooltip.card;

/**
 * 未揭示卡面：底色 + 六层图案按倾斜角各自错开（纵深），再叠全息格纹、暗角、光点和星屑闪点。
 * 贴图由 docs-local/hover-card/impl/www/export_veil_layers.html 从原型导出，图案改了要重新导出。
 */
final class CardVeil {

    private static final float W = CardLayout.W;

    /** 原型里定下的强度：纵深 70%、格纹 50%、闪点 60% */
    private static final float DEPTH_K = 0.7F;
    private static final float GRID_K = 0.5F;
    private static final float SPARK_K = 0.6F;
    /** 一层的偏移 = 倾斜 × 深度 × 卡面宽的 5% × 强度 */
    private static final float DEPTH_PX = 0.05F;
    /** 格纹贴图 96 像素对应原型里 260 宽卡面的一格 */
    private static final float GRID_REP = 260 / 96.0F;

    private static final String[] LAYER = {"dust", "far", "back", "main", "inner", "core"};
    /** + 在卡面前，- 在卡面后；dust 是普通混合，其余加色 */
    private static final float[] DEPTH = {-1.3F, -1.0F, -0.6F, -0.15F, 0.4F, 0.9F};

    private static final float[][] MOTES = {
            {0.10F, 0.72F, 1}, {0.905F, 0.77F, 0.9F}, {0.90F, 0.93F, 0.7F}, {0.17F, 0.95F, 0.75F},
            {0.85F, 0.27F, 0.6F}, {0.13F, 0.34F, 0.55F}, {0.72F, 0.9F, 0.45F}};

    /** x、y（卡面比例），朝向（弧度），大小，闪动相位；和原型 holo.js 的 SPARKS 同一张表 */
    private static final float[][] SPARKS = {
            {0.5430F, 0.2963F, 5.8511F, 1.0072F, 4.1999F}, {0.3048F, 0.2789F, 2.5222F, 0.8442F, 3.6909F},
            {0.7127F, 0.7341F, 3.8177F, 1.4942F, 2.1172F}, {0.7311F, 0.8311F, 1.2599F, 1.1422F, 0.6721F},
            {0.2121F, 0.2456F, 0.7753F, 0.7103F, 0.7859F}, {0.2212F, 0.4563F, 2.6970F, 1.0658F, 3.0892F},
            {0.8896F, 0.3723F, 0.7842F, 1.3543F, 0.5031F}, {0.6472F, 0.6694F, 1.9689F, 1.3494F, 0.1486F},
            {0.9557F, 0.1328F, 3.0701F, 1.1007F, 4.8693F}, {0.4761F, 0.6985F, 3.8475F, 1.4455F, 1.4041F},
            {0.8909F, 0.9538F, 0.5536F, 0.8666F, 4.8185F}, {0.2123F, 0.8984F, 4.2937F, 1.1384F, 1.2706F},
            {0.7780F, 0.8342F, 4.7364F, 1.2086F, 0.6901F}, {0.7536F, 0.8142F, 2.6527F, 1.3618F, 6.1884F},
            {0.2009F, 0.7655F, 5.9371F, 1.1862F, 4.7528F}, {0.5921F, 0.4219F, 5.1211F, 0.5255F, 2.0996F},
            {0.6642F, 0.7306F, 1.0634F, 0.9216F, 4.1468F}, {0.4245F, 0.1913F, 4.3768F, 1.0697F, 0.2834F},
            {0.6175F, 0.9587F, 1.7191F, 1.0487F, 5.5783F}, {0.6263F, 0.0519F, 4.0130F, 0.8180F, 4.7042F},
            {0.0484F, 0.6868F, 5.3065F, 1.3624F, 4.3434F}, {0.5272F, 0.0766F, 5.3707F, 0.9337F, 4.7857F},
            {0.8054F, 0.8380F, 6.0173F, 0.7362F, 0.1933F}, {0.4910F, 0.4797F, 5.4786F, 1.2399F, 2.6484F},
            {0.3547F, 0.1995F, 4.7557F, 1.4653F, 0.5989F}, {0.3957F, 0.5416F, 3.1978F, 1.1487F, 0.6865F},
            {0.2018F, 0.1860F, 5.5578F, 0.6239F, 1.2576F}, {0.0915F, 0.5299F, 0.3706F, 0.5743F, 4.8053F},
            {0.0832F, 0.8711F, 2.2886F, 0.6452F, 2.8114F}, {0.2648F, 0.2722F, 1.2628F, 0.9213F, 3.8369F},
            {0.2357F, 0.1513F, 0.3724F, 1.3072F, 3.0162F}, {0.4541F, 0.5471F, 4.2747F, 0.8644F, 0.6364F},
            {0.4866F, 0.2890F, 3.4144F, 0.5510F, 6.0619F}, {0.7013F, 0.1494F, 4.4018F, 1.2017F, 1.3740F},
            {0.6629F, 0.2863F, 1.1126F, 1.0413F, 1.0496F}, {0.6543F, 0.9437F, 0.0716F, 1.2211F, 1.1851F},
            {0.0784F, 0.3933F, 2.2503F, 1.2992F, 0.6535F}, {0.8794F, 0.1266F, 0.9643F, 0.9081F, 5.3499F},
            {0.9144F, 0.3865F, 2.8351F, 0.7640F, 5.9000F}, {0.3252F, 0.8344F, 4.2761F, 1.4628F, 3.5634F},
            {0.7551F, 0.3438F, 3.6324F, 1.1450F, 3.8510F}, {0.0874F, 0.7402F, 3.8869F, 1.0076F, 1.4583F},
            {0.2398F, 0.5454F, 5.0444F, 1.2612F, 3.9595F}, {0.7848F, 0.5028F, 1.4286F, 1.2873F, 4.2968F},
            {0.3332F, 0.2303F, 3.6393F, 0.6186F, 0.4094F}, {0.3066F, 0.3770F, 4.9242F, 0.9180F, 0.9367F}
    };

    private static final float[] BRIGHT = new float[SPARKS.length];
    private static float u0, v0, uw, vh;

    private CardVeil() {
    }

    static void draw(CardLayout l, CardState st, long now) {
        CardTier t = l.model.tier;
        float ps = l.ps;
        float boxW = W * ps, boxH = l.artEnd * ps;
        // 倾斜换回原型里的 -1..1（指针在格内的偏移），关了倾斜时恒为 0
        float tx = st.ry / CardState.TILT_MAX, ty = -st.rx / CardState.TILT_MAX;
        float scale = Math.max(boxW / 400, boxH / 320);
        uw = boxW / scale / 400;
        vh = boxH / scale / 320;
        u0 = 0.5F - uw / 2;
        v0 = 0.5F - vh / 2;
        float sh = DEPTH_K * DEPTH_PX * boxW;

        CardDraw.blendNormal();
        CardDraw.texture(CardTextures.veil(t, "bg"), true);
        cover(l, 0, 0, 0xFFFFFF);
        for (int i = 0; i < LAYER.length; i++) {
            if (i == 1) {
                CardDraw.blendAdd();
            }
            CardDraw.texture(CardTextures.veil(t, LAYER[i]), true);
            cover(l, -uw * tx * DEPTH[i] * sh / boxW, -vh * ty * DEPTH[i] * sh / boxH, 0xFFFFFF);
        }
        grid(l, tx, ty, boxW, boxH);
        CardDraw.blendNormal();
        CardDraw.texture(CardTextures.VEIL_VIGNETTE, true);
        cover(l, 0, 0, 0x000000);
        motes(l, now, boxW, boxH, tx * sh * 1.1F, ty * sh * 1.1F);
        sparks(l, now, boxW, boxH, tx, ty);
        CardDraw.blendNormal();
    }

    /** 等比铺满卡面框，贴图坐标再挪 (su, sv)；下沿照卡面图一样渐隐 */
    private static void cover(CardLayout l, float su, float sv, int rgb) {
        float ps = l.ps, ae = l.artEnd;
        for (int i = 0; i < 3; i++) {
            float ya = CardPainter.band(ae, i), yb = CardPainter.band(ae, i + 1);
            if (yb <= ya) {
                continue;
            }
            float va = v0 + sv + vh * ya / ae, vb = v0 + sv + vh * yb / ae;
            CardDraw.blitV(0, ya * ps, W * ps, yb * ps, u0 + su, va, u0 + su + uw, vb, rgb,
                    CardPainter.artFade(ya, ae), CardPainter.artFade(yb, ae));
        }
    }

    /** 彩色细斜格平铺，坐标随倾斜滑动：先 dst × src + dst 只染亮处，再加一点点本色 */
    private static void grid(CardLayout l, float tx, float ty, float boxW, float boxH) {
        float ps = l.ps, ae = l.artEnd;
        float gu0 = GRID_REP * 0.5F * tx, gu1 = GRID_REP * (1 + 0.5F * tx);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                CardDraw.blendMulAdd();
            } else {
                CardDraw.blendAdd();
            }
            CardDraw.textureRepeat(CardTextures.VEIL_LATTICE);
            for (int i = 0; i < 3; i++) {
                float ya = CardPainter.band(ae, i), yb = CardPainter.band(ae, i + 1);
                if (yb <= ya) {
                    continue;
                }
                float fa = CardPainter.artFade(ya, ae), fb = CardPainter.artFade(yb, ae);
                float va = GRID_REP * (ya * ps + 0.5F * ty * boxH) / boxW;
                float vb = GRID_REP * (yb * ps + 0.5F * ty * boxH) / boxW;
                if (pass == 0) {
                    CardDraw.blitC(0, ya * ps, boxW, yb * ps, gu0, va, gu1, vb, grey(1.3F * GRID_K * fa), grey(1.3F * GRID_K * fb));
                } else {
                    CardDraw.blitV(0, ya * ps, boxW, yb * ps, gu0, va, gu1, vb, 0xFFFFFF, 0.07F * GRID_K * fa, 0.07F * GRID_K * fb);
                }
            }
        }
    }

    private static void motes(CardLayout l, long now, float w, float h, float sx, float sy) {
        CardTier t = l.model.tier;
        float m = Math.min(w, 1.35F * h);
        CardDraw.blendAdd();
        CardDraw.texture(CardTextures.MOTE, true);
        for (int i = 0; i < MOTES.length; i++) {
            float[] mo = MOTES[i];
            float cx = mo[0] * w + sx, cy = mo[1] * h + sy;
            float tw = 0.6F + 0.4F * (float) Math.sin(now / 430.0 + 2.1 * i);
            float r = Math.max(3.5F, m * 0.09F * mo[2]);
            CardDraw.blitAll(cx - r, cy - r, cx + r, cy + r, t.mote, tw * fade(l, cy));
        }
        for (float[] mo : MOTES) {
            float cx = mo[0] * w + sx, cy = mo[1] * h + sy;
            float d = Math.max(0.8F, m * 0.011F * mo[2]) * 1.6F;
            CardDraw.blitAll(cx - d, cy - d, cx + d, cy + d, 0xFFFFFF, fade(l, cy));
        }
    }

    /** 每颗闪点有自己的朝向，卡转向它时才亮；摆正时只有少数几颗微微亮着 */
    private static void sparks(CardLayout l, long now, float w, float h, float tx, float ty) {
        float mag = Math.min(1, (float) Math.hypot(tx, ty));
        double th = Math.atan2(ty, tx);
        float m = Math.min(w, 1.35F * h);
        for (int i = 0; i < SPARKS.length; i++) {
            float[] s = SPARKS[i];
            double c = Math.max(0, Math.cos(th - s[2] - (s[0] - 0.5) * 1.2));
            float f = (float) Math.pow(c, 8) * (0.25F + 0.75F * mag);
            BRIGHT[i] = Math.min(1, f * (0.75F + 0.25F * (float) Math.sin(now / 260.0 + s[4])) * SPARK_K);
        }
        CardDraw.blendAdd();
        CardDraw.texture(CardTextures.VEIL_SPARK, true);
        for (int i = 0; i < SPARKS.length; i++) {
            float[] s = SPARKS[i];
            if (BRIGHT[i] >= 0.02F) {
                float cx = s[0] * w, cy = s[1] * h, r = m * 0.05F * s[3];
                CardDraw.blitAll(cx - r, cy - r, cx + r, cy + r, 0xFFFFFF, BRIGHT[i] * fade(l, cy));
            }
        }
        // 中心一粒彩色的芯，原型里是实心小圆，这里借光点贴图
        CardDraw.texture(CardTextures.MOTE, true);
        for (int i = 0; i < SPARKS.length; i++) {
            float[] s = SPARKS[i];
            if (BRIGHT[i] >= 0.02F) {
                float cx = s[0] * w, cy = s[1] * h, r = m * 0.05F * s[3] * 0.2F;
                float hue = ((s[0] * 300 + s[1] * 120 + tx * 90) % 360 + 360) % 360;
                CardDraw.blitAll(cx - r, cy - r, cx + r, cy + r, pale(hue), BRIGHT[i] * fade(l, cy));
            }
        }
    }

    private static float fade(CardLayout l, float yPx) {
        return CardPainter.artFade(yPx / l.ps, l.artEnd);
    }

    private static int grey(float k) {
        int g = Math.max(0, Math.min(255, Math.round(k * 255)));
        return g << 16 | g << 8 | g;
    }

    /** hsl(hue, 100%, 85%) */
    private static int pale(float hue) {
        float c = 0.3F, x = c * (1 - Math.abs(hue / 60 % 2 - 1)), m = 0.7F;
        float r, g, b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
