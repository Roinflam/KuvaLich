package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.util.Mth;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;

/**
 * 画一个幻纹时手边的东西：往哪画（暂存区）、相机在哪、这一个按完整还是精简档、强度系数、取色。
 * 渲染器每画一个幻纹前填一遍，画法只管往里写四边形。
 */
public final class EphemeraDraw {

    public final EphemeraStaging out;

    public double camX, camY, camZ;
    /** 相机的右、上（世界坐标的单位向量），朝向相机的面片用 */
    public float rightX, rightY, rightZ, upX, upY, upZ;
    public long timeMs;

    public EphemeraLook look = EphemeraLook.NONE;
    public boolean full = true;
    public EphemeraTier tier = EphemeraTier.CONVERGED;
    /** 客户端 intensity 再乘在发射率上 */
    public float emitScale = 1.0F;
    public float glowAlpha = EphemeraTier.GLOW_DAY;
    /** 第一人称看自己：环身、头顶不画 */
    public boolean firstPersonSelf;

    public EphemeraDraw(EphemeraStaging out) {
        this.out = out;
    }

    public boolean allows(EphemeraAnchor anchor) {
        return !firstPersonSelf || anchor.visibleInFirstPerson();
    }

    /** 这一帧的发射率倍数：档位 × 强度，精简档再减半 */
    public float emit() {
        return tier.emit * emitScale * (full ? 1.0F : 0.5F);
    }

    /**
     * 按物品上的渐变方式取色。v = 从下到上 0~1，r = 从内到外 0~1；返回不带透明度的 RGB
     */
    public int color(float v, float r) {
        int c1 = look.color1();
        int c2 = look.color2();
        float t = switch (look.gradient()) {
            case EphemeraItem.GRADIENT_VERTICAL -> v;
            case EphemeraItem.GRADIENT_RADIAL -> r;
            case EphemeraItem.GRADIENT_FLOW -> 0.5F + 0.5F * Mth.sin((timeMs % 3000L) / 3000.0F * Mth.TWO_PI + v * Mth.PI);
            default -> 0.0F;
        };
        return lerpRgb(c1, c2, Mth.clamp(t, 0.0F, 1.0F));
    }

    public static int lerpRgb(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    public static int argb(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }

    /** 四个世界坐标点的面片 */
    public void quad(int category, int layer, double x0, double y0, double z0, double x1, double y1, double z1,
                     double x2, double y2, double z2, double x3, double y3, double z3,
                     int c0, int c1, int c2, int c3) {
        out.quad(category, layer,
                (float) (x0 - camX), (float) (y0 - camY), (float) (z0 - camZ),
                (float) (x1 - camX), (float) (y1 - camY), (float) (z1 - camZ),
                (float) (x2 - camX), (float) (y2 - camY), (float) (z2 - camZ),
                (float) (x3 - camX), (float) (y3 - camY), (float) (z3 - camZ),
                c0, c1, c2, c3);
    }

    /** 朝向相机的方片，half = 半边长 */
    public void billboard(int category, int layer, double x, double y, double z, float half, int argb) {
        float rx = rightX * half, ry = rightY * half, rz = rightZ * half;
        float ux = upX * half, uy = upY * half, uz = upZ * half;
        quad(category, layer,
                x - rx - ux, y - ry - uy, z - rz - uz,
                x + rx - ux, y + ry - uy, z + rz - uz,
                x + rx + ux, y + ry + uy, z + rz + uz,
                x - rx + ux, y - ry + uy, z - rz + uz,
                argb, argb, argb, argb);
    }
}
