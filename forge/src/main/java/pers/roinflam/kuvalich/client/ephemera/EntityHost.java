package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 把一个活物包成宿主；渲染器每帧复用同一个，绑到哪只就读哪只 */
public final class EntityHost implements EphemeraHost {

    private LivingEntity e;
    private float pt;
    private boolean self;
    private double x, y, z;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public EntityHost bind(LivingEntity entity, float partialTick, boolean isSelf) {
        this.e = entity;
        this.pt = partialTick;
        this.self = isSelf;
        this.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        this.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        this.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
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

    @Override
    public boolean onGround() {
        return e.onGround();
    }

    @Override
    public double groundY() {
        if (e.onGround()) {
            return y;
        }
        // 只看脚底那一列方块，6 格以内没有能站的就当悬空
        Level level = e.level();
        int top = Mth.floor(y);
        for (int dy = 0; dy <= 6; dy++) {
            cursor.set(Mth.floor(x), top - dy, Mth.floor(z));
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double h = cursor.getY() + shape.max(Direction.Axis.Y);
                if (h <= y + 1.0E-3D) {
                    return h;
                }
            }
        }
        return Double.NaN;
    }

    @Override
    public boolean riding() {
        return e.isPassenger();
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
