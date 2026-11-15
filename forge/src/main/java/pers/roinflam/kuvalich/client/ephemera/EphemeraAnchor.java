package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.util.Mth;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;

/**
 * 四类挂点。一款画法可以叠几层，每层各取一个挂点的坐标系：原点、朝向、整体缩放、环身半径缩放。
 * 高度全按包围盒的比例算，物品上的「高度」直接加在原点上，「大小」乘在两个缩放上。
 */
public enum EphemeraAnchor {
    /** 脚下，贴地；骑乘时不画 */
    FEET,
    /** 身后，肩胛高度、往后退半个身宽 */
    BACK,
    /** 环身，腰的高度 */
    BODY,
    /** 头顶 */
    HEAD;

    /** 第一人称看自己时只画这两类，环身和头顶会挡视线 */
    public boolean visibleInFirstPerson() {
        return this == FEET || this == BACK;
    }

    public static final class Frame {
        public double x, y, z;
        /** 身体朝向（弧度），前方 = (-sin, cos) */
        public float yaw;
        public float scale;
        public float radius;

        public double forwardX() {
            return -Mth.sin(yaw);
        }

        public double forwardZ() {
            return Mth.cos(yaw);
        }
    }

    /** 骑乘时脚下那类返回 false，别的永远 true */
    public boolean frame(EphemeraHost h, EphemeraLook look, Frame out) {
        if (this == FEET && h.riding()) {
            return false;
        }
        EphemeraHost.Pose pose = h.pose();
        float size = look.scale();
        out.scale = h.height() / pose.vanillaHeight * size;
        out.radius = Math.max(h.width(), 0.05F) / EphemeraHost.VANILLA_WIDTH * size;
        out.yaw = h.bodyYaw() * Mth.DEG_TO_RAD;
        out.x = h.x();
        out.z = h.z();
        double base = h.y() + look.yOffset();
        float hgt = h.height();
        switch (this) {
            case FEET -> out.y = base + 0.02D;
            case BODY -> out.y = base + hgt * 0.5D;
            case HEAD -> out.y = base + hgt + 0.12D * out.scale;
            case BACK -> {
                // 趴着（游泳、鞘翅）时身后就是脚那头，高度取包围盒中线
                boolean lying = pose == EphemeraHost.Pose.SWIM || pose == EphemeraHost.Pose.FLY
                        || pose == EphemeraHost.Pose.SLEEP;
                out.y = base + hgt * (lying ? 0.5D : 0.72D);
                double back = h.width() * 0.5D + 0.08D * out.scale;
                out.x -= out.forwardX() * back;
                out.z -= out.forwardZ() * back;
            }
        }
        return true;
    }
}
