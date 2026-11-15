package pers.roinflam.kuvalich.client.ephemera;

/**
 * 强度档的系数。默认观感是原型的「收敛」档；「全开」是上限，客户端 intensity 再怎么调也不超过它。
 * 数值照原型定稿，各款画法只乘这里的系数，不自己另定一套。
 */
public final class EphemeraTier {

    /** 发射率 */
    public final float emit;
    /** 粒子大小 */
    public final float particleSize;
    /** 拖尾宽度、透明度（长度不变） */
    public final float trailWidth;
    public final float trailAlpha;
    /** 起跑、落地、挥砍这类事件的爆发持续时间 */
    public final float burstTime;
    /** 爆发范围 */
    public final float burst;
    /** 站着不动时流动幅度和外扩范围 */
    public final float idle;

    private EphemeraTier(float emit, float particleSize, float trailWidth, float trailAlpha, float burstTime,
                         float burst, float idle) {
        this.emit = emit;
        this.particleSize = particleSize;
        this.trailWidth = trailWidth;
        this.trailAlpha = trailAlpha;
        this.burstTime = burstTime;
        this.burst = burst;
        this.idle = idle;
    }

    public static final EphemeraTier FULL_OPEN = new EphemeraTier(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F);
    public static final EphemeraTier CONVERGED = new EphemeraTier(0.6F, 0.85F, 0.6F, 0.7F, 0.55F, 0.55F, 0.4F);

    /** 发光层透明度：夜晚 / 白天 / 雪地，按环境亮度在三者之间取 */
    public static final float GLOW_NIGHT = 0.66F;
    public static final float GLOW_DAY = 0.78F;
    public static final float GLOW_SNOW = 0.82F;

    /** 每个幻纹每帧的四边形上限 */
    public static final int CAP_FULL = 600;
    public static final int CAP_REDUCED = 150;
    /** 超过这个距离（格）一律按精简档画 */
    public static final double REDUCED_BEYOND = 24.0D;

    public static float glowAlpha(float daylight, boolean snowy) {
        if (snowy) {
            return GLOW_SNOW;
        }
        return GLOW_NIGHT + (GLOW_DAY - GLOW_NIGHT) * Math.max(0.0F, Math.min(1.0F, daylight));
    }
}
