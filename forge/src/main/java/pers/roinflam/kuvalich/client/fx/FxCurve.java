package pers.roinflam.kuvalich.client.fx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 演出用的缓动与曲线
 *
 * <p>输入是归一化的年龄 {@code t}（0 = 刚出生，1 = 寿命结束），输出是一个倍数：
 * 用作尺寸 / 位置曲线时是「走了多少」（{@code s0 + (s1 - s0) * eval(t)}，0 起步、1 收尾，过冲曲线会短暂超过 1），
 * 用作透明度曲线时直接是 alpha 的倍数（{@link #FADE_SQ} 从 1 降到 0）。</p>
 *
 * <p>扩张用缓出，卡入用回弹，淡出用平方，不用线性扩散和线性淡出。
 * {@link #POP} 和 {@link #FLICKER} 是按绝对毫秒走的（弹出 90 ms、闪烁 38 ms 一格），
 * 寿命再长也不会被拉慢，所以要用 {@link #eval(float, float)} 传毫秒；只传 {@code t} 的版本按 1000 ms 寿命折算。</p>
 */
@OnlyIn(Dist.CLIENT)
public enum FxCurve {
    /** 线性（只用于非视觉量，视觉上的扩散和淡出别用） */
    LINEAR,
    /** 缓出三次：展开 */
    OUT_CUBIC,
    /** 缓出四次：冲击环 */
    OUT_QUART,
    /** 缓出回弹（s = 1.2，过冲约 4%）：卡入 */
    OUT_BACK,
    /** 缓入三次 */
    IN_CUBIC,
    /** 缓入缓出三次 */
    IN_OUT_CUBIC,
    /** 淡出：{@code (1 - t)²}，1 → 0 */
    FADE_SQ,
    /** 弹出：前 {@value #POP_MS} ms 走一次缓出回弹，之后保持 1。尺寸 1.4 → 1.0 之类的「砸一下」 */
    POP,
    /** 闪烁：每 {@value #FLICKER_STEP_MS} ms 一格，按 [0.35, 0, 0.7, 0.4, 1.0] 循环 */
    FLICKER,
    /** 先保持满值，到 {@value #HOLD_END} 的寿命处开始按平方淡出到 0 */
    HOLD_THEN_FADE;

    /** {@link #POP} 的时长（毫秒） */
    public static final int POP_MS = 90;
    /** {@link #FLICKER} 一格的时长（毫秒），和指南开场的闪烁节奏一致 */
    public static final int FLICKER_STEP_MS = 38;
    /** {@link #HOLD_THEN_FADE} 开始淡出的寿命比例 */
    public static final float HOLD_END = 0.6F;

    private static final float[] FLICKER_TABLE = {0.35F, 0.0F, 0.7F, 0.4F, 1.0F};

    /** 只知道归一化年龄时用；{@link #POP}、{@link #FLICKER} 按 1000 ms 寿命折算 */
    public float eval(float t) {
        return eval(t * 1000.0F, 1000.0F);
    }

    /**
     * @param ageMs  已经活了多少毫秒
     * @param lifeMs 寿命（毫秒）
     */
    public float eval(float ageMs, float lifeMs) {
        float t = lifeMs <= 0.0F ? 1.0F : clamp01(ageMs / lifeMs);
        switch (this) {
            case LINEAR:
                return t;
            case OUT_CUBIC:
                return easeOutCubic(t);
            case OUT_QUART:
                return easeOutQuart(t);
            case OUT_BACK:
                return easeOutBack(t);
            case IN_CUBIC:
                return t * t * t;
            case IN_OUT_CUBIC:
                return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
            case FADE_SQ:
                return fadeOut(t);
            case POP:
                return easeOutBack(clamp01(ageMs / POP_MS));
            case FLICKER:
                return flicker(ageMs);
            case HOLD_THEN_FADE:
                return t <= HOLD_END ? 1.0F : fadeOut((t - HOLD_END) / (1.0F - HOLD_END));
            default:
                return t;
        }
    }

    // ==================== 单独可用的缓动 ====================

    public static float clamp01(float t) {
        return t < 0.0F ? 0.0F : Math.min(t, 1.0F);
    }

    public static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static float easeOutQuart(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u * u;
    }

    /** 过冲约 4%（s = 1.2） */
    public static float easeOutBack(float t) {
        float u = t - 1.0F;
        return 1.0F + 2.2F * u * u * u + 1.2F * u * u;
    }

    /** 淡出：{@code (1 - t)²} */
    public static float fadeOut(float t) {
        float u = 1.0F - clamp01(t);
        return u * u;
    }

    /** 按 38 ms 一格查表的闪烁（全息上屏） */
    public static float flicker(float ms) {
        int step = (int) Math.floor(Math.max(0.0F, ms) / FLICKER_STEP_MS);
        return FLICKER_TABLE[step % FLICKER_TABLE.length];
    }

    // ==================== 赤毒心跳 ====================

    /** 心跳周期没特别指定时用的参考长度（毫秒），取各处用到的最短周期 */
    public static final float HEARTBEAT_REF_PERIOD_MS = 1200.0F;

    /**
     * 赤毒脉动：{@code phi} ∈ [0, 1) 是一个周期里的位置。周期一开始就是第一个尖峰（幅度 1，70 ms 内按平方衰减），
     * 170 ms 处第二个（幅度 0.6，同样 70 ms），其余时间 0.15 的底光。
     * 尖峰的宽度按毫秒定，所以要告诉它周期有多长；不传按 {@link #HEARTBEAT_REF_PERIOD_MS}。
     */
    public static float heartbeat(float phi) {
        return heartbeat(phi, HEARTBEAT_REF_PERIOD_MS);
    }

    public static float heartbeat(float phi, float periodMs) {
        float ms = (phi - (float) Math.floor(phi)) * periodMs;
        float v = 0.15F;
        if (ms < 70.0F) {
            v = Math.max(v, fadeOut(ms / 70.0F));
        } else if (ms >= 170.0F && ms < 240.0F) {
            v = Math.max(v, 0.6F * fadeOut((ms - 170.0F) / 70.0F));
        }
        return v;
    }

    // ==================== 颜色 ====================

    /** 两个 0xRRGGBB 颜色按 {@code t} 线性插值 */
    public static int lerpColor(int a, int b, float t) {
        float k = clamp01(t);
        int r = Math.round(((a >> 16) & 0xFF) * (1.0F - k) + ((b >> 16) & 0xFF) * k);
        int g = Math.round(((a >> 8) & 0xFF) * (1.0F - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1.0F - k) + (b & 0xFF) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
