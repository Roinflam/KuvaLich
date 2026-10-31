package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 加法发光的几何图元：环、弧、光柱、闪光、电弧、连线、大光晕、横向光晕、扫描环
 *
 * <p>位置都是世界坐标，可以用 {@code follow(entity, yFrac)} 跟随实体；颜色 0xRRGGBB；
 * 时间单位是毫秒。共有的收尾调用是 {@code .color(rgb).color2(rgb).alpha(a0, curve).life(ms).tier(t).core().add()}，
 * {@code add()} 返回 false 表示预算满了（或太远、强度选项不让画）。</p>
 *
 * <p>大的、软的光都交给这里：软边全靠顶点色插值（环、弧画成「内缘 0 → 中线 1 → 外缘 0」的两条带，圆盘按半径分圈），
 * 不用贴图，近看没有像素阶梯。渲染类型是位置 + 颜色、四边形、加法混合、不写深度、不剔背面，深度测试照常（会被方块挡住）；
 * 关掉加法发光时改普通透明混合。由导演在 {@code AFTER_WEATHER}（云和雨雪之后）推进完时间轴后同一帧调 {@link #render}，相机相对坐标，64 格外不画。</p>
 *
 * <p>九种图元：环、弧、光柱、闪光、电弧、连线、大光晕、横向光晕、扫描环。加法混合时每个图元的透明度再乘
 * {@link KuvaFxDirector#additiveScale}（加进场景时按位置算一次，白天露天压暗），脚本不用自己乘。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaFxGeometry {

    /** 环、扫描环的分段数 */
    private static final int RING_SEGMENTS = 48;
    /** 圆盘的扇形数 */
    private static final int DISC_SEGMENTS = 24;
    /** 连线断开后两端回缩的时长 */
    private static final int TETHER_RETRACT_MS = 160;

    private static final List<Prim<?>> ACTIVE = new ArrayList<>();

    private static RenderType typeAdd;
    private static RenderType typeTranslucent;
    /** 每帧复用的绘制上下文（只在渲染线程用） */
    private static final Ctx CTX = new Ctx();

    private KuvaFxGeometry() {
    }

    /** 环 / 弧所在平面的法线的两种特殊取法 */
    public enum Normal {
        /** 水平（法线朝上） */
        UP,
        /** 朝相机 */
        CAMERA
    }

    /** 按毫秒年龄给出一个量（自定义透明度、尺寸曲线用） */
    @FunctionalInterface
    public interface MsFn {
        float at(float ageMs);
    }

    public static Ring ring(Vec3 center) {
        return new Ring(center);
    }

    public static Arc arc(Vec3 center) {
        return new Arc(center);
    }

    public static Column column(Vec3 base) {
        return new Column(base);
    }

    public static Flash flash(Vec3 center) {
        return new Flash(center);
    }

    public static Bolt bolt(Vec3 a, Vec3 b) {
        return new Bolt(a, b);
    }

    public static Tether tether(Vec3 a, Vec3... bs) {
        return new Tether(a, bs);
    }

    public static Glow glow(Vec3 center) {
        return new Glow(center);
    }

    public static Flare flare(Vec3 center) {
        return new Flare(center);
    }

    public static Scan scan(Entity entity) {
        return new Scan(entity);
    }

    /** 当前活跃的图元数 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    static void clear() {
        ACTIVE.clear();
    }

    // ==================== 渲染 ====================

    /** 一帧的绘制上下文 */
    static final class Ctx {
        VertexConsumer vc;
        Matrix4f m;
        double cx, cy, cz;
        Vec3 left, up, look;
        float pt;
        float alphaMul;
        long now;

        void v(double x, double y, double z, int rgb, float a) {
            float aa = Mth.clamp(a * alphaMul, 0.0F, 1.0F);
            vc.vertex(m, (float) (x - cx), (float) (y - cy), (float) (z - cz))
                    .color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, aa)
                    .endVertex();
        }

        void quad(Vec3 p0, int c0, float a0, Vec3 p1, int c1, float a1, Vec3 p2, int c2, float a2, Vec3 p3, int c3, float a3) {
            v(p0.x, p0.y, p0.z, c0, a0);
            v(p1.x, p1.y, p1.z, c1, a1);
            v(p2.x, p2.y, p2.z, c2, a2);
            v(p3.x, p3.y, p3.z, c3, a3);
        }
    }

    static void render(RenderLevelStageEvent event, long now) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera cam = event.getCamera();
        boolean additive = EffectsDisplayConfig.additiveGlow() && KuvaParticleShaders.geometry() != null;
        RenderType type = additive ? typeAdd() : typeTranslucent();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        Ctx ctx = CTX;
        ctx.vc = buffers.getBuffer(type);
        ctx.m = event.getPoseStack().last().pose();
        Vec3 cp = cam.getPosition();
        ctx.cx = cp.x;
        ctx.cy = cp.y;
        ctx.cz = cp.z;
        ctx.left = new Vec3(cam.getLeftVector());
        ctx.up = new Vec3(cam.getUpVector());
        ctx.look = new Vec3(cam.getLookVector());
        ctx.pt = event.getPartialTick();
        ctx.alphaMul = additive ? 1.0F : 1.3F;
        ctx.now = now;

        // 天气阶段里原版已经把相机旋转乘进了全局模型视图矩阵（画云时压的栈，画完边界才弹），
        // 顶点又按事件给的姿态矩阵变换一遍就转了两次；这里临时把全局的换成单位阵，哪个阶段调都一样
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            double cull2 = KuvaFxDirector.LOD_CULL_DIST * KuvaFxDirector.LOD_CULL_DIST;
            Iterator<Prim<?>> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Prim<?> p = it.next();
                float age = now - p.startMs;
                if (age >= p.lifeMs) {
                    it.remove();
                    continue;
                }
                Vec3 pos = p.posAt(ctx.pt);
                if (pos.distanceToSqr(cp) > cull2) {
                    continue;
                }
                ctx.alphaMul = additive ? p.addScale : 1.3F;
                p.draw(ctx, Math.max(0.0F, age), pos);
            }
            buffers.endBatch(type);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static RenderType typeAdd() {
        if (typeAdd == null) {
            typeAdd = Types.createAdditive();
        }
        return typeAdd;
    }

    private static RenderType typeTranslucent() {
        if (typeTranslucent == null) {
            typeTranslucent = Types.createTranslucent();
        }
        return typeTranslucent;
    }

    /** 借 RenderType 的子类拿到受保护的渲染状态 */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        static RenderType createAdditive() {
            return RenderType.create("kuvalich_fx_add", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    8192, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(KuvaParticleShaders::geometry))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        static RenderType createTranslucent() {
            return RenderType.create("kuvalich_fx_translucent", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    8192, false, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }
    }

    // ==================== 小工具 ====================

    private static Vec3 lerpPos(Entity e, float pt, float yFrac) {
        double x = Mth.lerp(pt, e.xOld, e.getX());
        double y = Mth.lerp(pt, e.yOld, e.getY()) + e.getBbHeight() * yFrac;
        double z = Mth.lerp(pt, e.zOld, e.getZ());
        return new Vec3(x, y, z);
    }

    /** 和法线 n 垂直的两个单位向量 */
    private static Vec3[] basis(Vec3 n) {
        Vec3 a = Math.abs(n.y) < 0.99D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = n.cross(a).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    private static int white(int rgb, float k) {
        return FxCurve.lerpColor(rgb, 0xFFFFFF, k);
    }

    /**
     * 圆盘：中心 → 内圈 → 边缘三圈，按 (半径比例, 颜色, alpha) 给。
     * 每帧都要画，所以直接按分量算顶点、不建 {@link Vec3}；上一段的终点角就是下一段的起点角
     */
    private static void disc(Ctx ctx, Vec3 c, Vec3 u, Vec3 v, float r,
                             int cCenter, float aCenter, float midFrac, int cMid, float aMid, int cEdge) {
        double step = Math.PI * 2.0D / DISC_SEGMENTS;
        double rm = r * midFrac;
        double cos0 = 1.0D, sin0 = 0.0D;
        for (int i = 0; i < DISC_SEGMENTS; i++) {
            double t1 = (i + 1) * step;
            double cos1 = Math.cos(t1), sin1 = Math.sin(t1);
            double d0x = u.x * cos0 + v.x * sin0, d0y = u.y * cos0 + v.y * sin0, d0z = u.z * cos0 + v.z * sin0;
            double d1x = u.x * cos1 + v.x * sin1, d1y = u.y * cos1 + v.y * sin1, d1z = u.z * cos1 + v.z * sin1;
            ctx.v(c.x, c.y, c.z, cCenter, aCenter);
            ctx.v(c.x, c.y, c.z, cCenter, aCenter);
            ctx.v(c.x + d1x * rm, c.y + d1y * rm, c.z + d1z * rm, cMid, aMid);
            ctx.v(c.x + d0x * rm, c.y + d0y * rm, c.z + d0z * rm, cMid, aMid);
            ctx.v(c.x + d0x * rm, c.y + d0y * rm, c.z + d0z * rm, cMid, aMid);
            ctx.v(c.x + d1x * rm, c.y + d1y * rm, c.z + d1z * rm, cMid, aMid);
            ctx.v(c.x + d1x * r, c.y + d1y * r, c.z + d1z * r, cEdge, 0.0F);
            ctx.v(c.x + d0x * r, c.y + d0y * r, c.z + d0z * r, cEdge, 0.0F);
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    /** 软边圆环：内缘 0 → 中线 a → 外缘 0；和 {@link #disc} 一样按分量算，不建 {@link Vec3} */
    private static void softRing(Ctx ctx, Vec3 c, Vec3 u, Vec3 v, float r, float th, int rgb, float a, int innerRgb) {
        double ri = Math.max(0.0F, r - th * 0.5F), ro = r + th * 0.5F;
        double step = Math.PI * 2.0D / RING_SEGMENTS;
        double cos0 = 1.0D, sin0 = 0.0D;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double t1 = (i + 1) * step;
            double cos1 = Math.cos(t1), sin1 = Math.sin(t1);
            double d0x = u.x * cos0 + v.x * sin0, d0y = u.y * cos0 + v.y * sin0, d0z = u.z * cos0 + v.z * sin0;
            double d1x = u.x * cos1 + v.x * sin1, d1y = u.y * cos1 + v.y * sin1, d1z = u.z * cos1 + v.z * sin1;
            ctx.v(c.x + d0x * ri, c.y + d0y * ri, c.z + d0z * ri, innerRgb, 0.0F);
            ctx.v(c.x + d1x * ri, c.y + d1y * ri, c.z + d1z * ri, innerRgb, 0.0F);
            ctx.v(c.x + d1x * r, c.y + d1y * r, c.z + d1z * r, rgb, a);
            ctx.v(c.x + d0x * r, c.y + d0y * r, c.z + d0z * r, rgb, a);
            ctx.v(c.x + d0x * r, c.y + d0y * r, c.z + d0z * r, rgb, a);
            ctx.v(c.x + d1x * r, c.y + d1y * r, c.z + d1z * r, rgb, a);
            ctx.v(c.x + d1x * ro, c.y + d1y * ro, c.z + d1z * ro, rgb, 0.0F);
            ctx.v(c.x + d0x * ro, c.y + d0y * ro, c.z + d0z * ro, rgb, 0.0F);
            cos0 = cos1;
            sin0 = sin1;
        }
    }

    /** 朝相机的软边细带：两侧 0、中线 a */
    private static void softLine(Ctx ctx, Vec3 p, Vec3 q, float w, int rgb, float a) {
        Vec3 dir = q.subtract(p);
        if (dir.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 mid = p.add(q).scale(0.5D);
        Vec3 toCam = new Vec3(ctx.cx, ctx.cy, ctx.cz).subtract(mid);
        Vec3 side = dir.cross(toCam);
        if (side.lengthSqr() < 1.0E-8D) {
            side = ctx.up;
        }
        side = side.normalize().scale(w * 0.5D);
        ctx.quad(p.subtract(side), rgb, 0.0F, q.subtract(side), rgb, 0.0F, q, rgb, a, p, rgb, a);
        ctx.quad(p, rgb, a, q, rgb, a, q.add(side), rgb, 0.0F, p.add(side), rgb, 0.0F);
    }

    /** 朝相机的软边细带（坐标直接给，不建向量）：两侧 0、中线最亮；两端颜色、透明度可以不同 */
    private static void softLineD(Ctx ctx, double ax, double ay, double az, double bx, double by, double bz,
                                  float w, int rgbA, float aA, int rgbB, float aB) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5D - ctx.cx, my = (ay + by) * 0.5D - ctx.cy, mz = (az + bz) * 0.5D - ctx.cz;
        // 侧向 = 线方向 × 视线
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-9D) {
            return;
        }
        double k = w * 0.5D / sl;
        sx *= k;
        sy *= k;
        sz *= k;
        ctx.v(ax - sx, ay - sy, az - sz, rgbA, 0.0F);
        ctx.v(bx - sx, by - sy, bz - sz, rgbB, 0.0F);
        ctx.v(bx, by, bz, rgbB, aB);
        ctx.v(ax, ay, az, rgbA, aA);
        ctx.v(ax, ay, az, rgbA, aA);
        ctx.v(bx, by, bz, rgbB, aB);
        ctx.v(bx + sx, by + sy, bz + sz, rgbB, 0.0F);
        ctx.v(ax + sx, ay + sy, az + sz, rgbA, 0.0F);
    }

    /** 由法线取一组平面基（写进 out[0..5]：u 的 xyz、v 的 xyz） */
    private static void planeBasis(Ctx ctx, Vec3 normalVec, Normal kind, double[] out) {
        if (normalVec != null) {
            Vec3[] b = basis(normalVec.normalize());
            out[0] = b[0].x;
            out[1] = b[0].y;
            out[2] = b[0].z;
            out[3] = b[1].x;
            out[4] = b[1].y;
            out[5] = b[1].z;
        } else if (kind == Normal.CAMERA) {
            out[0] = ctx.left.x;
            out[1] = ctx.left.y;
            out[2] = ctx.left.z;
            out[3] = ctx.up.x;
            out[4] = ctx.up.y;
            out[5] = ctx.up.z;
        } else {
            out[0] = 1.0D;
            out[1] = 0.0D;
            out[2] = 0.0D;
            out[3] = 0.0D;
            out[4] = 0.0D;
            out[5] = 1.0D;
        }
    }

    /** 0~1 之间的确定性伪随机（电弧抖动用：同样的输入同样的结果，不建随机数对象） */
    private static float hash01(int a, int b) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    // ==================== 图元 ====================

    /** 所有图元共有的参数 */
    public abstract static class Prim<T extends Prim<T>> {
        Vec3 pos;
        Entity followEntity;
        float followYFrac;
        int color = 0xFFFFFF;
        int color2 = -1;
        float alpha0 = 1.0F;
        FxCurve alphaCurve = FxCurve.FADE_SQ;
        MsFn alphaFn;
        int lifeMs = 400;
        FxTier tier = FxTier.B;
        boolean tierSet;
        boolean core;
        long startMs;
        /** 加法混合时额外乘的系数（白天露天压暗，加进场景时按位置算一次） */
        float addScale = 1.0F;
        boolean rawAdditive;

        Prim(Vec3 pos) {
            this.pos = pos;
        }

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        /** 位置跟随实体，{@code yFrac} 是身体高度的比例（0 脚、1 头顶） */
        public T follow(Entity e, float yFrac) {
            this.followEntity = e;
            this.followYFrac = yFrac;
            return self();
        }

        public T color(int rgb) {
            this.color = rgb;
            return self();
        }

        /** 结束颜色，寿命内从 {@link #color} 插过来 */
        public T color2(int rgb) {
            this.color2 = rgb;
            return self();
        }

        public T alpha(float a0, FxCurve curve) {
            this.alpha0 = a0;
            this.alphaCurve = curve;
            return self();
        }

        /** 自定义透明度：按毫秒年龄直接给出 alpha（盖过 {@link #alpha}），预兆的心跳、绷紧这类多段变化用 */
        public T alphaFn(MsFn fn) {
            this.alphaFn = fn;
            return self();
        }

        /** 寿命（毫秒） */
        public T life(int ms) {
            this.lifeMs = Math.max(1, ms);
            return self();
        }

        public T tier(FxTier t) {
            this.tier = t;
            this.tierSet = true;
            return self();
        }

        /** 核心层：远处、预算紧时也保留 */
        public T core() {
            this.core = true;
            return self();
        }

        /** 加法混合时不乘白天补偿（只给对比演示用） */
        public T rawAdditive(boolean raw) {
            this.rawAdditive = raw;
            return self();
        }

        /** 按条件标核心层（从粒子参数转过来时用） */
        public T coreIf(boolean c) {
            this.core |= c;
            return self();
        }

        /** 加进场景；预算满了返回 false */
        public boolean add() {
            if (!supported()) {
                return false;
            }
            FxTimeline tl = KuvaFxDirector.current();
            if (!tierSet && tl != null) {
                tier = tl.tier();
            }
            if (!FxBudget.tryGeometry(tier, core, posAt(1.0F))) {
                return false;
            }
            startMs = KuvaFxDirector.clockMs();
            Vec3 at = posAt(1.0F);
            addScale = rawAdditive ? 1.0F : KuvaFxDirector.additiveScale(at.x, at.y, at.z);
            ACTIVE.add(this);
            if (tl != null) {
                tl.geometryCount++;
            }
            return true;
        }

        boolean supported() {
            return true;
        }

        Vec3 posAt(float pt) {
            if (followEntity != null && !followEntity.isRemoved()) {
                pos = lerpPos(followEntity, pt, followYFrac);
            }
            return pos;
        }

        float alphaAt(float ageMs) {
            return alphaFn != null ? alphaFn.at(ageMs) : alpha0 * alphaCurve.eval(ageMs, lifeMs);
        }

        int colorAt(float ageMs) {
            return color2 < 0 ? color : FxCurve.lerpColor(color, color2, ageMs / lifeMs);
        }

        abstract void draw(Ctx ctx, float ageMs, Vec3 p);
    }

    /** 冲击环、地面印记圈、扫描环 */
    public static final class Ring extends Prim<Ring> {
        Vec3 normalVec;
        Normal normalKind = Normal.UP;
        float r0 = 0.5F, r1 = 1.0F;
        FxCurve radiusCurve = FxCurve.OUT_QUART;
        float t0 = 0.05F, t1 = 0.05F;

        Ring(Vec3 c) {
            super(c);
        }

        public Ring normal(Vec3 n) {
            this.normalVec = n;
            return this;
        }

        public Ring normal(Normal n) {
            this.normalKind = n;
            this.normalVec = null;
            return this;
        }

        public Ring radius(float r0, float r1, FxCurve curve) {
            this.r0 = r0;
            this.r1 = r1;
            this.radiusCurve = curve;
            return this;
        }

        public Ring thick(float t0, float t1) {
            this.t0 = t0;
            this.t1 = t1;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 c) {
            float a = alphaAt(age);
            if (a <= 0.002F) {
                return;
            }
            float k = radiusCurve.eval(age, lifeMs);
            float r = r0 + (r1 - r0) * k;
            float th = t0 + (t1 - t0) * FxCurve.clamp01(k);
            Vec3 u, v;
            if (normalVec != null) {
                Vec3[] b = basis(normalVec.normalize());
                u = b[0];
                v = b[1];
            } else if (normalKind == Normal.CAMERA) {
                u = ctx.left;
                v = ctx.up;
            } else {
                u = new Vec3(1.0D, 0.0D, 0.0D);
                v = new Vec3(0.0D, 0.0D, 1.0D);
            }
            int rgb = colorAt(age);
            softRing(ctx, c, u, v, r, th, rgb, a, white(rgb, 0.35F));
        }
    }

    /**
     * 斩击弧、玄骸扫击、切割元素：在法线平面里从 {@code start} 角开始，{@code sweepMs} 内扫过 {@code sweep} 度。
     * 头部 15% 角度最亮（偏白），往尾巴线性降到主题色、再到 0；截面是软边细带（内外缘 0、中线最亮）。
     * {@code parallel(n, gap)} 画 n 道同心弧，半径依次差 gap（中间那道最粗）
     */
    public static final class Arc extends Prim<Arc> {
        private static final double[] BASIS = new double[6];

        Vec3 normalVec;
        Normal normalKind = Normal.UP;
        float startDeg;
        float sweepDeg = 180.0F;
        int sweepMs = 150;
        FxCurve sweepCurve = FxCurve.OUT_CUBIC;
        float radius = 1.0F;
        float thick = 0.1F;
        int parallelN = 1;
        float parallelGap;

        Arc(Vec3 c) {
            super(c);
        }

        public Arc normal(Vec3 n) {
            this.normalVec = n;
            return this;
        }

        public Arc normal(Normal n) {
            this.normalKind = n;
            this.normalVec = null;
            return this;
        }

        /** 起始角（度）：0 度是平面基的第一个方向（水平弧是 +X），角度朝第二个方向（水平弧是 +Z）增大 */
        public Arc start(float deg) {
            this.startDeg = deg;
            return this;
        }

        /** 弧在 {@code ms} 毫秒内扫过 {@code deg} 度（负数反方向扫） */
        public Arc sweep(float deg, int ms, FxCurve curve) {
            this.sweepDeg = deg;
            this.sweepMs = ms;
            this.sweepCurve = curve;
            return this;
        }

        public Arc radius(float r) {
            this.radius = r;
            return this;
        }

        public Arc thick(float t) {
            this.thick = t;
            return this;
        }

        /** 平行的 {@code n} 道弧，相隔 {@code gap} 格 */
        public Arc parallel(int n, float gap) {
            this.parallelN = Math.max(1, n);
            this.parallelGap = gap;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 c) {
            float a = alphaAt(age);
            if (a <= 0.002F) {
                return;
            }
            float k = sweepMs <= 0 ? 1.0F : sweepCurve.eval(FxCurve.clamp01(age / sweepMs));
            float swept = sweepDeg * k;
            if (Math.abs(swept) < 0.5F) {
                return;
            }
            planeBasis(ctx, normalVec, normalKind, BASIS);
            int rgb = colorAt(age);
            int head = white(rgb, 0.8F);
            int segs = Mth.clamp((int) Math.ceil(Math.abs(swept) / 360.0F * RING_SEGMENTS), 4, RING_SEGMENTS);
            double a0 = Math.toRadians(startDeg), da = Math.toRadians(swept) / segs;
            int mid = (parallelN - 1) / 2;
            for (int line = 0; line < parallelN; line++) {
                float r = radius + (line - (parallelN - 1) * 0.5F) * parallelGap;
                float th = thick * (line == mid ? 1.0F : 0.7F);
                float ri = Math.max(0.0F, r - th * 0.5F), ro = r + th * 0.5F;
                for (int i = 0; i < segs; i++) {
                    float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
                    double t0 = a0 + da * i, t1 = a0 + da * (i + 1);
                    int c0 = arcColor(u0, rgb, head), c1 = arcColor(u1, rgb, head);
                    float al0 = a * arcAlpha(u0), al1 = a * arcAlpha(u1);
                    double cos0 = Math.cos(t0), sin0 = Math.sin(t0), cos1 = Math.cos(t1), sin1 = Math.sin(t1);
                    double d0x = BASIS[0] * cos0 + BASIS[3] * sin0, d0y = BASIS[1] * cos0 + BASIS[4] * sin0;
                    double d0z = BASIS[2] * cos0 + BASIS[5] * sin0;
                    double d1x = BASIS[0] * cos1 + BASIS[3] * sin1, d1y = BASIS[1] * cos1 + BASIS[4] * sin1;
                    double d1z = BASIS[2] * cos1 + BASIS[5] * sin1;
                    // 内缘 → 中线
                    ctx.v(c.x + d0x * ri, c.y + d0y * ri, c.z + d0z * ri, c0, 0.0F);
                    ctx.v(c.x + d1x * ri, c.y + d1y * ri, c.z + d1z * ri, c1, 0.0F);
                    ctx.v(c.x + d1x * r, c.y + d1y * r, c.z + d1z * r, c1, al1);
                    ctx.v(c.x + d0x * r, c.y + d0y * r, c.z + d0z * r, c0, al0);
                    // 中线 → 外缘
                    ctx.v(c.x + d0x * r, c.y + d0y * r, c.z + d0z * r, c0, al0);
                    ctx.v(c.x + d1x * r, c.y + d1y * r, c.z + d1z * r, c1, al1);
                    ctx.v(c.x + d1x * ro, c.y + d1y * ro, c.z + d1z * ro, c1, 0.0F);
                    ctx.v(c.x + d0x * ro, c.y + d0y * ro, c.z + d0z * ro, c0, 0.0F);
                }
            }
        }

        /** u = 0 尾、1 头：头部 15% 偏白，往尾巴走回主题色 */
        private static int arcColor(float u, int rgb, int head) {
            return u >= 0.85F ? head : FxCurve.lerpColor(rgb, head, u / 0.85F * 0.5F);
        }

        /** 头部 15% 满亮，往尾巴线性降到 0 */
        private static float arcAlpha(float u) {
            return u >= 0.85F ? 1.0F : u / 0.85F;
        }
    }

    /**
     * 召唤光柱：竖直的光带，横向始终朝相机（看上去是一根圆柱）。截面中间 35% 半径是芯色，往外到主题色、边缘 0；
     * 顶端 30% 高度内渐隐到 0。半径按寿命走 {@code radius} 曲线，高度在 {@code heightMs} 内按 {@code height} 曲线长出来
     */
    public static final class Column extends Prim<Column> {
        /** 横向五道顶点（半径的比例）和各自的透明度 */
        private static final float[] XS = {-1.0F, -0.35F, 0.0F, 0.35F, 1.0F};
        private static final float[] XA = {0.0F, 0.75F, 1.0F, 0.75F, 0.0F};
        /** 竖向三道：底、70% 高、顶 */
        private static final float[] YS = {0.0F, 0.7F, 1.0F};
        private static final float[] YA = {1.0F, 1.0F, 0.0F};

        float r0 = 0.3F, r1 = 0.3F;
        FxCurve radiusCurve = FxCurve.OUT_CUBIC;
        float h0, h1 = 8.0F;
        int heightMs = 200;
        FxCurve heightCurve = FxCurve.OUT_QUART;
        int coreRgb = 0xFFFFFF;

        Column(Vec3 b) {
            super(b);
        }

        public Column radius(float r0, float r1, FxCurve curve) {
            this.r0 = r0;
            this.r1 = r1;
            this.radiusCurve = curve;
            return this;
        }

        public Column height(float h0, float h1, int ms, FxCurve curve) {
            this.h0 = h0;
            this.h1 = h1;
            this.heightMs = ms;
            this.heightCurve = curve;
            return this;
        }

        /** 芯色（边色用 {@link #color}） */
        public Column core(int rgb) {
            this.coreRgb = rgb;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 b) {
            float a = alphaAt(age);
            if (a <= 0.002F) {
                return;
            }
            float r = r0 + (r1 - r0) * radiusCurve.eval(age, lifeMs);
            float h = h0 + (h1 - h0) * (heightMs <= 0 ? 1.0F : heightCurve.eval(FxCurve.clamp01(age / heightMs)));
            if (r <= 0.001F || h <= 0.001F) {
                return;
            }
            // 横向：水平面里和视线垂直的方向
            double vx = b.x - ctx.cx, vz = b.z - ctx.cz;
            double vl = Math.sqrt(vx * vx + vz * vz);
            double sx, sz;
            if (vl < 1.0E-6D) {
                sx = ctx.left.x;
                sz = ctx.left.z;
                double l = Math.sqrt(sx * sx + sz * sz);
                if (l < 1.0E-6D) {
                    sx = 1.0D;
                    sz = 0.0D;
                } else {
                    sx /= l;
                    sz /= l;
                }
            } else {
                sx = -vz / vl;
                sz = vx / vl;
            }
            int edge = colorAt(age);
            for (int yi = 0; yi < YS.length - 1; yi++) {
                double y0 = b.y + h * YS[yi], y1 = b.y + h * YS[yi + 1];
                for (int xi = 0; xi < XS.length - 1; xi++) {
                    double ox0 = XS[xi] * r, ox1 = XS[xi + 1] * r;
                    int c0 = Math.abs(XS[xi]) < 0.36F ? coreRgb : edge;
                    int c1 = Math.abs(XS[xi + 1]) < 0.36F ? coreRgb : edge;
                    ctx.v(b.x + sx * ox0, y0, b.z + sz * ox0, c0, a * XA[xi] * YA[yi]);
                    ctx.v(b.x + sx * ox1, y0, b.z + sz * ox1, c1, a * XA[xi + 1] * YA[yi]);
                    ctx.v(b.x + sx * ox1, y1, b.z + sz * ox1, c1, a * XA[xi + 1] * YA[yi + 1]);
                    ctx.v(b.x + sx * ox0, y1, b.z + sz * ox0, c0, a * XA[xi] * YA[yi + 1]);
                }
            }
        }
    }

    /**
     * 爆发帧白闪：朝相机的径向渐变圆盘，中心芯色 → 60% 半径处主题色（0.6）→ 边缘 0。
     * 半径在 {@code peakMs} 内缓出涨到峰值，之后收缩、同时按透明度曲线淡出，寿命末尾归零
     */
    public static final class Flash extends Prim<Flash> {
        float rPeak = 1.0F;
        int peakMs = 60;
        int coreRgb = 0xFFFFFF;

        Flash(Vec3 c) {
            super(c);
        }

        public Flash radius(float rPeak, int peakMs) {
            this.rPeak = rPeak;
            this.peakMs = peakMs;
            return this;
        }

        public Flash core(int rgb) {
            this.coreRgb = rgb;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 c) {
            float r, a;
            if (age < peakMs) {
                r = rPeak * FxCurve.easeOutCubic(age / Math.max(1.0F, peakMs));
                a = alpha0;
            } else {
                float k = FxCurve.clamp01((age - peakMs) / Math.max(1.0F, lifeMs - peakMs));
                r = rPeak * (1.0F - 0.65F * k * k * k);
                a = alphaFn != null ? alphaFn.at(age) : alpha0 * alphaCurve.eval(k);
            }
            if (a <= 0.002F || r <= 0.001F) {
                return;
            }
            int edge = colorAt(age);
            disc(ctx, c, ctx.left, ctx.up, r, coreRgb, a, 0.6F, edge, a * 0.6F, edge);
        }
    }

    /**
     * 电弧：起点到终点折成 6~9 段，中间各点在垂直方向上随机抖开（两端固定，中间抖得最开），
     * 每隔 {@code rejitterMs} 重新抖一次；可以分 0~2 个叉。两层叠画：芯（{@code core} 色、宽 × 0.8）+ 晕（主题色、宽 × 2、α 0.5）
     */
    public static final class Bolt extends Prim<Bolt> {
        private static final int MAX_POINTS = 10;
        private static final double[] PX = new double[MAX_POINTS];
        private static final double[] PY = new double[MAX_POINTS];
        private static final double[] PZ = new double[MAX_POINTS];

        Vec3 end;
        int segments = 7;
        float jitter = 0.25F;
        float width = 0.05F;
        int forks;
        int rejitterMs = 50;
        int coreRgb = FxPalette.ELEC_CORE;
        private final int seed = (int) (Math.random() * Integer.MAX_VALUE);

        Bolt(Vec3 a, Vec3 b) {
            super(a);
            this.end = b;
        }

        public Bolt segments(int n) {
            this.segments = Mth.clamp(n, 2, MAX_POINTS - 1);
            return this;
        }

        public Bolt jitter(float f) {
            this.jitter = f;
            return this;
        }

        public Bolt width(float w) {
            this.width = w;
            return this;
        }

        public Bolt forks(int n) {
            this.forks = Mth.clamp(n, 0, 2);
            return this;
        }

        /** 每隔 {@code ms} 毫秒重新抖一次 */
        public Bolt rejitter(int ms) {
            this.rejitterMs = ms;
            return this;
        }

        /** 芯色（默认电击的白热芯） */
        public Bolt core(int rgb) {
            this.coreRgb = rgb;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 a) {
            float al = alphaAt(age);
            if (al <= 0.002F) {
                return;
            }
            int frame = rejitterMs > 0 ? (int) (age / rejitterMs) : 0;
            int rgb = colorAt(age);
            path(a.x, a.y, a.z, end.x, end.y, end.z, segments, jitter, seed, frame);
            strokePath(ctx, segments + 1, width, rgb, al);
            for (int f = 0; f < forks; f++) {
                // 叉从主干三分之一 / 三分之二处分出去，长度是主干的四成，往一侧偏
                int at = Mth.clamp(segments * (f + 1) / 3, 1, segments - 1);
                path(a.x, a.y, a.z, end.x, end.y, end.z, segments, jitter, seed, frame);
                double sx = PX[at], sy = PY[at], sz = PZ[at];
                double dx = end.x - a.x, dy = end.y - a.y, dz = end.z - a.z;
                float side = (hash01(seed + f * 31, frame) - 0.5F) * 2.0F;
                double ex = sx + dx * 0.4D + dz * 0.3D * side;
                double ey = sy + dy * 0.4D + jitter * side;
                double ez = sz + dz * 0.4D - dx * 0.3D * side;
                path(sx, sy, sz, ex, ey, ez, 3, jitter * 0.6F, seed + 101 * (f + 1), frame);
                strokePath(ctx, 4, width * 0.6F, rgb, al * 0.8F);
            }
        }

        /** 算一条折线写进 PX / PY / PZ：两端固定，中间按 sin 包络往垂直方向抖 */
        private static void path(double ax, double ay, double az, double bx, double by, double bz, int n, float jit,
                                 int seed, int frame) {
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-6D) {
                len = 1.0D;
            }
            // 垂直于主方向的两个单位向量
            double ux, uy, uz;
            if (Math.abs(dy / len) < 0.99D) {
                ux = -dz;
                uy = 0.0D;
                uz = dx;
            } else {
                ux = 0.0D;
                uy = -dz;
                uz = dy;
            }
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul < 1.0E-9D) {
                ux = 1.0D;
                uy = 0.0D;
                uz = 0.0D;
                ul = 1.0D;
            }
            ux /= ul;
            uy /= ul;
            uz /= ul;
            double vx = (dy * uz - dz * uy) / len, vy = (dz * ux - dx * uz) / len, vz = (dx * uy - dy * ux) / len;
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                double env = Math.sin(Math.PI * t);
                float j1 = (hash01(seed + i * 7, frame) - 0.5F) * 2.0F * jit;
                float j2 = (hash01(seed + i * 13 + 5, frame) - 0.5F) * 2.0F * jit;
                PX[i] = ax + dx * t + (ux * j1 + vx * j2) * env;
                PY[i] = ay + dy * t + (uy * j1 + vy * j2) * env;
                PZ[i] = az + dz * t + (uz * j1 + vz * j2) * env;
            }
        }

        private void strokePath(Ctx ctx, int n, float w, int rgb, float al) {
            for (int i = 0; i < n - 1; i++) {
                softLineD(ctx, PX[i], PY[i], PZ[i], PX[i + 1], PY[i + 1], PZ[i + 1], w * 2.0F, rgb, al * 0.5F, rgb, al * 0.5F);
            }
            for (int i = 0; i < n - 1; i++) {
                softLineD(ctx, PX[i], PY[i], PZ[i], PX[i + 1], PY[i + 1], PZ[i + 1], w * 0.8F, coreRgb, al, coreRgb, al);
            }
        }
    }

    /**
     * 安魂印记连线：一个公共端点 A 连向 1~3 个端点 B，三段算一个图元。
     * 每段从 B 往 A 描出；断开时从中点断开、两半各自往自己的端点回缩并淡掉
     */
    public static final class Tether extends Prim<Tether> {
        Vec3[] ends;
        int drawSeg = -1, drawStartMs, drawMs = 80;
        int snapSeg = -1, snapAtMs;
        private final int[] segStart;
        private final int[] segDraw;
        private final int[] segSnap;
        private float width = 0.03F;
        private int boostAt = -1;
        private float boostFactor = 1.0F;
        private int boostMs = 60;

        Tether(Vec3 a, Vec3... bs) {
            super(a);
            this.ends = bs;
            this.segStart = new int[bs.length];
            this.segDraw = new int[bs.length];
            this.segSnap = new int[bs.length];
            java.util.Arrays.fill(segSnap, -1);
        }

        /** 第 {@code seg} 段在 {@code startMs} 开始、用 {@code ms} 毫秒从 0 描到 1（每段各调一次） */
        public Tether draw(int seg, int startMs, int ms) {
            this.drawSeg = seg;
            this.drawStartMs = startMs;
            this.drawMs = ms;
            if (seg >= 0 && seg < ends.length) {
                segStart[seg] = startMs;
                segDraw[seg] = ms;
            }
            return this;
        }

        /** 第 {@code seg} 段在 {@code atMs} 从中点断开、两端回缩；{@code seg} 小于 0 时所有段一起断 */
        public Tether snap(int seg, int atMs) {
            this.snapSeg = seg;
            this.snapAtMs = atMs;
            for (int i = 0; i < ends.length; i++) {
                if (seg < 0 || seg == i) {
                    segSnap[i] = atMs;
                }
            }
            return this;
        }

        /** 线宽（格），默认 0.03 */
        public Tether width(float w) {
            this.width = w;
            return this;
        }

        /** 从 {@code atMs} 起用 {@code ms} 毫秒把亮度提到 {@code factor} 倍（「绷紧」） */
        public Tether boost(int atMs, float factor, int ms) {
            this.boostAt = atMs;
            this.boostFactor = factor;
            this.boostMs = Math.max(1, ms);
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 a) {
            float alpha = alphaAt(age);
            if (boostAt >= 0 && age > boostAt) {
                alpha *= 1.0F + (boostFactor - 1.0F) * FxCurve.easeOutCubic(FxCurve.clamp01((age - boostAt) / boostMs));
            }
            if (alpha <= 0.002F) {
                return;
            }
            int rgb = colorAt(age);
            for (int i = 0; i < ends.length; i++) {
                if (age < segStart[i]) {
                    continue;
                }
                Vec3 b = ends[i];
                float drawn = segDraw[i] <= 0 ? 1.0F : FxCurve.easeOutCubic(FxCurve.clamp01((age - segStart[i]) / segDraw[i]));
                if (segSnap[i] >= 0 && age >= segSnap[i]) {
                    float k = FxCurve.clamp01((age - segSnap[i]) / TETHER_RETRACT_MS);
                    if (k >= 1.0F) {
                        continue;
                    }
                    float keep = 0.5F * (1.0F - FxCurve.easeOutCubic(k));
                    float fa = alpha * (1.0F - k);
                    Vec3 d = a.subtract(b);
                    softLine(ctx, b, b.add(d.scale(keep)), width, rgb, fa);
                    softLine(ctx, a, a.subtract(d.scale(keep)), width, rgb, fa);
                } else {
                    softLine(ctx, b, b.add(a.subtract(b).scale(drawn)), width, rgb, Math.min(1.0F, alpha));
                    if (alpha > 1.0F) {
                        // 超过 1 的部分（绷紧）叠一条更宽更淡的光晕，而不是被截掉
                        softLine(ctx, b, b.add(a.subtract(b).scale(drawn)), width * 3.0F, rgb, (alpha - 1.0F) * 0.5F);
                    }
                }
            }
        }
    }

    /**
     * 大光晕（从粒子 {@code glow} 分流来的）：朝相机的圆盘，中心 1 → 35% 半径处 0.55 → 边缘 0。
     * 尺寸是直径（格）
     */
    public static final class Glow extends Prim<Glow> {
        float s0 = 0.5F, s1 = 0.5F;
        FxCurve sizeCurve = FxCurve.OUT_CUBIC;
        MsFn sizeFn;

        Glow(Vec3 c) {
            super(c);
        }

        public Glow size(float s0, float s1, FxCurve curve) {
            this.s0 = s0;
            this.s1 = s1;
            this.sizeCurve = curve;
            return this;
        }

        /** 自定义尺寸：按毫秒年龄直接给出直径（盖过 {@link #size}） */
        public Glow sizeFn(MsFn fn) {
            this.sizeFn = fn;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 c) {
            float a = alphaAt(age);
            float s = sizeFn != null ? sizeFn.at(age) : s0 + (s1 - s0) * sizeCurve.eval(age, lifeMs);
            if (a <= 0.002F || s <= 0.001F) {
                return;
            }
            int rgb = colorAt(age);
            float hot = Math.max(0.0F, a - 1.0F);
            float aa = Math.min(1.0F, a);
            disc(ctx, c, ctx.left, ctx.up, s * 0.5F, white(rgb, 0.25F + 0.5F * Math.min(1.0F, hot)), aa, 0.35F, rgb, aa * 0.55F, rgb);
        }
    }

    /**
     * 横向光晕：只取相机左右方向的水平光条，高 = 宽 × 0.12；横向按 {@code (1-|x|)^1.6}、纵向按高斯降到 0，
     * 中线颜色偏白。降低特效强度后不画
     */
    public static final class Flare extends Prim<Flare> {
        private static final float[] ROWS = {-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};
        private static final float[] ROW_ALPHA = {0.0F, 0.29F, 1.0F, 0.29F, 0.0F};
        private static final int COLS = 12;

        float w0 = 1.0F, w1 = 1.0F;
        FxCurve widthCurve = FxCurve.OUT_CUBIC;

        Flare(Vec3 c) {
            super(c);
        }

        public Flare width(float w0, float w1, FxCurve curve) {
            this.w0 = w0;
            this.w1 = w1;
            this.widthCurve = curve;
            return this;
        }

        @Override
        boolean supported() {
            return EffectsDisplayConfig.intensity() == EffectsDisplayConfig.Intensity.FULL;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 c) {
            float a = alphaAt(age);
            float w = w0 + (w1 - w0) * widthCurve.eval(age, lifeMs);
            if (a <= 0.002F || w <= 0.001F) {
                return;
            }
            int rgb = colorAt(age);
            int mid = white(rgb, 0.6F);
            double hw = w * 0.5D, hh = w * 0.06D;
            for (int r = 0; r < ROWS.length - 1; r++) {
                for (int i = 0; i < COLS; i++) {
                    float x0 = -1.0F + 2.0F * i / COLS, x1 = -1.0F + 2.0F * (i + 1) / COLS;
                    float ax0 = (float) Math.pow(1.0F - Math.abs(x0), 1.6F), ax1 = (float) Math.pow(1.0F - Math.abs(x1), 1.6F);
                    float y0 = ROWS[r], y1 = ROWS[r + 1];
                    int c0 = y0 == 0.0F ? mid : rgb, c1 = y1 == 0.0F ? mid : rgb;
                    Vec3 p00 = c.add(ctx.left.scale(x0 * hw)).add(ctx.up.scale(y0 * hh));
                    Vec3 p10 = c.add(ctx.left.scale(x1 * hw)).add(ctx.up.scale(y0 * hh));
                    Vec3 p11 = c.add(ctx.left.scale(x1 * hw)).add(ctx.up.scale(y1 * hh));
                    Vec3 p01 = c.add(ctx.left.scale(x0 * hw)).add(ctx.up.scale(y1 * hh));
                    ctx.quad(p00, c0, a * ax0 * ROW_ALPHA[r], p10, c0, a * ax1 * ROW_ALPHA[r],
                            p11, c1, a * ax1 * ROW_ALPHA[r + 1], p01, c1, a * ax0 * ROW_ALPHA[r + 1]);
                }
            }
        }
    }

    /**
     * 扫描环：绕实体的一圈竖直细光带，高度从脚扫到头（缓出）；扫的过程中亮度降到三分之一，扫完在余下的寿命里淡出
     */
    public static final class Scan extends Prim<Scan> {
        final Entity entity;
        float radius = 0.8F;
        float thick = 0.05F;
        int sweepMs = 500;

        Scan(Entity e) {
            super(e.position());
            this.entity = e;
            this.followEntity = e;
            this.followYFrac = 0.0F;
        }

        public Scan radius(float r) {
            this.radius = r;
            return this;
        }

        public Scan thick(float t) {
            this.thick = t;
            return this;
        }

        public Scan sweep(int ms) {
            this.sweepMs = ms;
            return this;
        }

        @Override
        void draw(Ctx ctx, float age, Vec3 feet) {
            float h = entity.getBbHeight();
            float k = FxCurve.clamp01(age / Math.max(1, sweepMs));
            float a;
            if (alphaFn != null) {
                a = alphaFn.at(age);
            } else if (age < sweepMs) {
                a = alpha0 * (1.0F - 0.67F * k);
            } else {
                a = alpha0 * 0.33F * FxCurve.fadeOut((age - sweepMs) / Math.max(1.0F, lifeMs - sweepMs));
            }
            if (a <= 0.002F) {
                return;
            }
            double y = h * FxCurve.easeOutCubic(k);
            int rgb = colorAt(age);
            Vec3 c = feet.add(0.0D, y, 0.0D);
            double step = Math.PI * 2.0D / RING_SEGMENTS;
            Vec3 up = new Vec3(0.0D, thick, 0.0D);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double t0 = i * step, t1 = (i + 1) * step;
                Vec3 m0 = c.add(Math.cos(t0) * radius, 0.0D, Math.sin(t0) * radius);
                Vec3 m1 = c.add(Math.cos(t1) * radius, 0.0D, Math.sin(t1) * radius);
                ctx.quad(m0.subtract(up), rgb, 0.0F, m1.subtract(up), rgb, 0.0F, m1, white(rgb, 0.3F), a, m0, white(rgb, 0.3F), a);
                ctx.quad(m0, white(rgb, 0.3F), a, m1, white(rgb, 0.3F), a, m1.add(up), rgb, 0.0F, m0.add(up), rgb, 0.0F);
            }
        }
    }
}
