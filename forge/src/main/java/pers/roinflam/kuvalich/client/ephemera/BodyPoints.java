package pers.roinflam.kuvalich.client.ephemera;

/**
 * 身上的几个参考点，全从包围盒推（身高 H、身宽 W、朝向），不读骨骼，换模型的模组也站得住。
 * 数字照原型 kit.js 的 pts；手腕照原版玩家模型的摆臂公式估（limbSwing、attackAnim），
 * 宿主不是原版人形模型时退回 hand 那两个估计点、前臂朝下。
 */
public final class BodyPoints {

    public static final class Pt {
        public double x, y, z;

        void set(double px, double py, double pz) {
            x = px;
            y = py;
            z = pz;
        }
    }

    public final Pt headTop = new Pt(), headCenter = new Pt(), headBack = new Pt(), neck = new Pt();
    public final Pt shoulderL = new Pt(), shoulderR = new Pt(), handL = new Pt(), handR = new Pt();
    public final Pt back = new Pt(), waist = new Pt(), ground = new Pt();
    /** 手腕位置，和前臂朝手的方向（单位向量）；R 是主手（挥砍的那只） */
    public final Pt wristL = new Pt(), wristR = new Pt(), downL = new Pt(), downR = new Pt();

    private FxMotion m;

    void update(FxMotion mo) {
        m = mo;
        double H = mo.H, W = mo.W;
        at(headTop, H * 1.05, 0, 0);
        at(headCenter, H * 0.89, 0, 0);
        at(headBack, H * 0.9, -W * 0.62, 0);
        at(neck, H * 0.76, 0, 0);
        at(shoulderL, H * 0.8, 0, -W * 0.72);
        at(shoulderR, H * 0.8, 0, W * 0.72);
        at(handL, H * 0.42, 0, -W * 0.8);
        at(handR, H * 0.42, 0, W * 0.8);
        at(back, H * 0.62, -W * 0.5, 0);
        at(waist, H * 0.48, 0, 0);
        ground.set(mo.x, mo.groundY, mo.z);
        if (mo.arms) {
            wrist(1, wristR, downR);
            wrist(-1, wristL, downL);
        } else {
            wristR.set(handR.x, handR.y, handR.z);
            wristL.set(handL.x, handL.y, handL.z);
            downR.set(0, -1, 0);
            downL.set(0, -1, 0);
        }
    }

    /** 脚底往上 up、往前 fwd、往右 right 的点 */
    private void at(Pt p, double up, double fwd, double right) {
        p.set(m.x + m.fwdX * fwd + m.rightX * right, m.y + up, m.z + m.fwdZ * fwd + m.rightZ * right);
    }

    private void wrist(int side, Pt out, Pt down) {
        final float px = 1.8F / 32;
        float sy = m.sc, sx = m.W / EphemeraHost.VANILLA_WIDTH;
        boolean run = m.speed > 4.9F;
        float lean = m.sneak ? 0.5F : (run ? 0.08F * Math.min(1, m.limbAmp) : 0) - (m.hurtP > 0 ? 0.25F * (1 - m.hurtP) : 0);
        float cl = (float) Math.cos(lean), sl = (float) Math.sin(lean);
        // 身体前倾后的「上」和「前」：都在 (fwd, up) 平面里
        double upX = m.fwdX * sl, upY = cl, upZ = m.fwdZ * sl;
        double fwX = m.fwdX * cl, fwY = -sl, fwZ = m.fwdZ * cl;
        float hipY = 12 * px - (m.sneak ? 1.5F * px : 0);
        double shX = m.x + m.rightX * side * 6 * px * sx + upX * 10 * px * sy;
        double shY = m.y + hipY * sy + upY * 10 * px * sy;
        double shZ = m.z + m.rightZ * side * 6 * px * sx + upZ * 10 * px * sy;
        // 原版：右臂 xRot = cos(limbSwing·0.6662 + π)·amount，左臂反相；挥砍时主手往前上方抡
        float a = -side * (float) Math.cos(m.limbPhase) * m.limbAmp - (m.sneak ? 0.35F : 0);
        if (side == 1 && m.swingP > 0) {
            a = -2.3F * (float) Math.sin(Math.PI * Math.pow(m.swingP, 0.7)) + 0.6F * m.swingP;
        }
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        double dx = -upX * ca - fwX * sa, dy = -upY * ca - fwY * sa, dz = -upZ * ca - fwZ * sa;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l;
        dy /= l;
        dz /= l;
        down.set(dx, dy, dz);
        double len = 7.5F * px * sy;
        out.set(shX + dx * len, shY + dy * len, shZ + dz * len);
    }
}
