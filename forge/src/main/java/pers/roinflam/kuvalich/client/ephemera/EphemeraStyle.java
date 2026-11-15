package pers.roinflam.kuvalich.client.ephemera;

/**
 * 一款幻纹的画法。每帧对每个要画的实体调一次 {@link #draw}，往 {@code d.out} 里写四边形：
 * 主体标 BASE，拖尾标 TRAIL（从新往旧画），粒子标 PARTICLE；超预算时渲染器按粒子、拖尾的顺序砍。
 * 挂点坐标用 {@link EphemeraAnchor#frame} 取，尺寸一律乘 frame 的 scale / radius，不写死玩家尺寸。
 */
public interface EphemeraStyle {

    void draw(EphemeraInstance inst, EphemeraHost host, EphemeraDraw d);

    /** 缩放为 1 时往包围盒外最远铺开几格，视锥裁剪按它放大包围盒（粒子、拖尾都要算进去） */
    float extent();

    /** 画完以后由渲染器按物品上的渐变方式统一换顶点色（画法里只用主色） */
    default boolean postGradient() {
        return false;
    }
}
