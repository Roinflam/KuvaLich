package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 把一个活物包成宿主；渲染器每帧复用同一个，绑到哪只就读哪只 */
public final class EntityHost implements EphemeraHost {

    /** 腾空时往下找地面的深度（格）。更深的坑由 FxMotion 当悬空处理，贴地层跟着脚走 */
    private static final double GROUND_PROBE = 4.0D;

    private LivingEntity e;
    private float pt;
    private boolean self;
    private double x, y, z;
    private boolean humanoid;

    public EntityHost bind(LivingEntity entity, float partialTick, boolean isSelf) {
        this.e = entity;
        this.pt = partialTick;
        this.self = isSelf;
        this.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        this.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        this.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        this.humanoid = false;
        return this;
    }

    /** 渲染器判断过这只画的是原版人形模型时再调；手腕挂点才按原版摆臂公式算 */
    public EntityHost humanoid(boolean v) {
        this.humanoid = v;
        return this;
    }

    @Override
    public int id() {
        return e.getId();
    }

    @Override
    public double x() {
        return x;
    }

    @Override
    public double y() {
        return y;
    }

    @Override
    public double z() {
        return z;
    }

    @Override
    public float bodyYaw() {
        return Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
    }

    @Override
    public float width() {
        return e.getBbWidth();
    }

    @Override
    public float height() {
        return e.getBbHeight();
    }

    @Override
    public double vx() {
        return e.getX() - e.xOld;
    }

    @Override
    public double vy() {
        return e.getY() - e.yOld;
    }

    @Override
    public double vz() {
        return e.getZ() - e.zOld;
    }

    @Override
    public Pose pose() {
        return switch (e.getPose()) {
            case CROUCHING -> Pose.CROUCH;
            case SWIMMING -> Pose.SWIM;
            case FALL_FLYING, SPIN_ATTACK -> Pose.FLY;
            case SLEEPING -> Pose.SLEEP;
            default -> Pose.STAND;
        };
    }

    /** 骑乘时看坐骑着没着地：人坐在鞍上永远是腾空的 */
    @Override
    public boolean onGround() {
        return e.isPassenger() ? e.getRootVehicle().onGround() : e.onGround();
    }

    @Override
    public double groundY() {
        if (e.onGround()) {
            return e.getY();
        }
        // 按脚底整块包围盒往下找：站在两格之间、半砖、栅栏、地毯上，或者脚底这一列刚好是坑时，只看中心一列会判错
        double hw = Math.max(0.05D, e.getBbWidth() * 0.5D - 1.0E-3D);
        AABB probe = new AABB(x - hw, y - GROUND_PROBE, z - hw, x + hw, y + 0.01D, z + hw);
        double best = Double.NaN;
        for (VoxelShape shape : e.level().getBlockCollisions(e, probe)) {
            double top = shape.max(Direction.Axis.Y);
            if (top <= y + 1.0E-3D && (Double.isNaN(best) || top > best)) {
                best = top;
            }
        }
        return best;
    }

    @Override
    public boolean riding() {
        return e.isPassenger();
    }

    @Override
    public boolean flying() {
        return e instanceof Player p && p.getAbilities().flying;
    }

    @Override
    public boolean inFluid() {
        return e.isInWater() || e.isInLava();
    }

    @Override
    public boolean climbing() {
        return e.onClimbable();
    }

    @Override
    public double vehicleY() {
        if (!e.isPassenger()) {
            return Double.NaN;
        }
        Entity v = e.getRootVehicle();
        return Mth.lerp(pt, v.yOld, v.getY());
    }

    @Override
    public float limbSwing() {
        return e.walkAnimation.position(pt);
    }

    @Override
    public float limbSwingAmount() {
        return Math.min(1.0F, e.walkAnimation.speed(pt));
    }

    @Override
    public boolean humanoidArms() {
        return humanoid;
    }

    @Override
    public float swing() {
        return e.getAttackAnim(pt);
    }

    @Override
    public float hurt() {
        return e.hurtDuration > 0 ? (float) e.hurtTime / e.hurtDuration : 0.0F;
    }

    @Override
    public boolean self() {
        return self;
    }
}
