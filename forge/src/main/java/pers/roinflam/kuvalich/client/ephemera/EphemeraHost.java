package pers.roinflam.kuvalich.client.ephemera;

/**
 * 幻纹挂在谁身上。渲染器只认这几项，不读骨骼、不碰模型：换了玩家模型的模组（YSM 之类）不影响挂点，
 * 改体型的（Pehkui 之类）改的是包围盒，尺寸自动跟着走。
 *
 * <p>这个包里除了 {@link EphemeraRenderer} 和开发命令，其余类都不引用客户端类，GameTest 直接拿假宿主测。</p>
 */
public interface EphemeraHost {

    /** 原版各姿态的包围盒高度：整体缩放 = 实际高 ÷ 这个值 */
    enum Pose {
        STAND(1.8F),
        CROUCH(1.5F),
        SWIM(0.6F),
        FLY(0.6F),
        SLEEP(0.2F);

        public final float vanillaHeight;

        Pose(float vanillaHeight) {
            this.vanillaHeight = vanillaHeight;
        }
    }

    /** 原版玩家的包围盒宽，环身半径按实际宽 ÷ 这个值放大 */
    float VANILLA_WIDTH = 0.6F;

    int id();

    /** 脚底中心（已按帧插值） */
    double x();

    double y();

    double z();

    /** 身体朝向，角度，和原版 yBodyRot 一样 */
    float bodyYaw();

    float width();

    float height();

    /** 每 tick 的位移 */
    double vx();

    double vy();

    double vz();

    Pose pose();

    boolean onGround();

    /** 脚下地面的高度；着地时就是脚底，腾空时按脚底整块往下找几格，找不到给 NaN */
    default double groundY() {
        return onGround() ? y() : Double.NaN;
    }

    boolean riding();

    /** 创造飞行 */
    default boolean flying() {
        return false;
    }

    /** 泡在水或岩浆里 */
    default boolean inFluid() {
        return false;
    }

    /** 扒在梯子、藤蔓上 */
    default boolean climbing() {
        return false;
    }

    /** 骑乘时最底下那只坐骑的脚底高度（已插值）；没骑是 NaN */
    default double vehicleY() {
        return Double.NaN;
    }

    /** 挥砍动画进度 0~1，没在挥是 0 */
    float swing();

    /** 受击后剩余的红闪 0~1 */
    float hurt();

    /** 本机玩家自己 */
    boolean self();
}
