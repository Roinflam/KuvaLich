package pers.roinflam.kuvalich.client.tooltip.card;

/**
 * 某一帧的开合状态：大小、显示范围、各层不透明度、倾斜。由开合进度 p 算出来，每帧重算、不分配。
 */
final class CardState {

    static final int OPEN_MS = 200;
    static final int CLOSE_MS = 170;
    static final float S0 = 16.0F / 102.0F;
    /** 合拢时卡露在图标里的那一段：图标第 15 行到第 49 行 */
    static final float SPAN = 34.0F * 102.0F / 64.0F;
    static final float TILT_MAX = (float) Math.toRadians(12);
    static final float SCALE_OVER = 0.04F;

    float p, e, s, v0, v1;
    float txt, pillA, frameA, shadow, zoom, pipT;
    float rx, ry, dx, dy;
    float fs;

    void compute(CardLayout l, float p, boolean opening, float bump, long sinceDone, float tiltX, float tiltY, boolean tilt) {
        this.p = p;
        this.e = ease(p);
        this.fs = l.ps / l.gs;
        this.s = (float) (S0 * Math.pow(fs / S0, e)) * (1 + SCALE_OVER * bump);
        float c0 = clamp(l.hf / 2 - SPAN / 2, 0, l.hf - SPAN);
        v0 = lerp(c0, 0, e);
        v1 = lerp(c0 + SPAN, l.hf, e);
        float size = fs / CardLayout.FLOAT_S;
        float rel = s / size;
        float tx0 = Math.max(0.36F, S0 / size + 0.1F);
        txt = smooth(tx0, tx0 + 0.3F, rel);
        pillA = smooth(tx0 + 0.06F, tx0 + 0.39F, rel);
        frameA = smooth(0.15F, 0.55F, p);
        shadow = smooth(0.2F, 1, e);
        zoom = 1.05F + 0.07F * e;
        float tk = tilt ? smooth(0.45F, 1, p) : 0;
        rx = -tiltY * TILT_MAX * tk;
        ry = tiltX * TILT_MAX * tk;
        dx = -tiltX * 2.5F * tk;
        dy = -tiltY * 2.5F * tk;
        if (opening) {
            pipT = p >= 1 ? 2.4F + sinceDone / 32.0F : (p > 0.8F ? (p - 0.8F) * 12 : 0);
        } else {
            pipT = 10 * smooth(0.5F, 0.9F, p);
        }
    }

    boolean flat() {
        return p >= 1 && Math.abs(rx) < 1e-4F && Math.abs(ry) < 1e-4F;
    }

    /** 开合曲线 cubic-bezier(0.25, 0.6, 0.3, 1) */
    static float ease(float v) {
        v = clamp(v, 0, 1);
        final float x1 = 0.25F, y1 = 0.6F, x2 = 0.3F, y2 = 1;
        float cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
        float cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
        float t = v;
        for (int i = 0; i < 8; i++) {
            float d = (3 * ax * t + 2 * bx) * t + cx;
            if (Math.abs(d) < 1e-6F) {
                break;
            }
            t -= (((ax * t + bx) * t + cx) * t - v) / d;
        }
        return ((ay * t + by) * t + cy) * t;
    }

    /** 回弹：p 在 0.5～1 之间叠一个起止都没有速度的小鼓包 */
    static float hump(float p) {
        float q = clamp((p - 0.5F) / 0.5F, 0, 1);
        float s = (float) Math.sin(Math.PI * q);
        return s * s;
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float smooth(float a, float b, float t) {
        float x = clamp((t - a) / (b - a), 0, 1);
        return x * x * (3 - 2 * x);
    }
}
