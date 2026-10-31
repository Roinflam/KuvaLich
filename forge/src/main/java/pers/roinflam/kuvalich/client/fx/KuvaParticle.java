package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;

/**
 * 本模组所有自制粒子的统一实现，参数全由 {@link KuvaParticles.Builder} 给
 *
 * <ul>
 *   <li>颜色寿命内从 color 插到 color2；尺寸按曲线从 s0 走到 s1（尺寸是整张贴图的边长，单位格）；
 *       透明度 = alpha × 透明度曲线。曲线按毫秒取，弹出、闪烁这类按绝对时间走的不会被寿命拉慢。</li>
 *   <li>重力、阻力直接是每 tick 的量（速度单位格/tick），不经过原版的 0.04 系数。</li>
 *   <li>光效（光点、火花、光环、印记、数据光点）走加法混合 + 全亮；雾走柔和透明混合、亮度不低于 8；
 *       碎晶等「发光的物质」走原版透明混合 + 全亮。关掉加法发光、或自制着色器没建成时一律退回原版透明混合，透明度 ×1.3。</li>
 *   <li>不做方块碰撞（液滴除外：落地时在原地留一个同色的溅痕再消失）；包围盒每 tick 按当前尺寸设，粒子引擎拿它做视锥剔除。
 *       拉长的火花中心出屏也不整条消失。</li>
 *   <li>溅痕寿命内顺播四帧、最后 10 tick 淡出；泡泡存活期两帧交替、最后 3 tick 换成破开的两帧，边上浮边左右摆。</li>
 *   <li>加法混合的粒子透明度再乘白天补偿（{@link KuvaFxDirector#additiveScale}），生成时按位置算一次。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class KuvaParticle extends TextureSheetParticle {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float MS_PER_TICK = 50.0F;
    /** 数据光点的横向摆动：0.6 Hz、0.05 格 */
    private static final float SWAY_OMEGA = (float) (Math.PI * 2.0D * 0.6D / 20.0D);
    private static final float SWAY_AMP = 0.05F;

    /** 拉长朝向画四个顶点用的临时量（只在渲染线程用，不在每帧分配） */
    private static final Vector3f TMP_D = new Vector3f();
    private static final Vector3f TMP_VIEW = new Vector3f();
    private static final Vector3f TMP_SIDE = new Vector3f();
    private static final Vector3f TMP_E1 = new Vector3f();
    private static final Vector3f TMP_E2 = new Vector3f();
    private static final float[][] TMP_QUAD = new float[4][5];

    /** 液滴落地留下的溅痕比液滴大多少 */
    private static final float SPLAT_FROM_DROP = 3.0F;

    final KuvaParticles.Kind kind;
    private final KuvaParticles.Orient orient;
    private final SpriteSet sprites;
    private final int color;
    private final int color2;
    private final float alpha0;
    private final float s0;
    private final float s1;
    private final FxCurve sizeCurve;
    private final FxCurve alphaCurve;
    private final float drag;
    private final float spin;
    private final Entity home;
    private final float homeStrength;
    private final ParticleRenderType renderType;
    private final float alphaMul;
    private final float swayPhase;

    /** 拉长朝向用的方向（速度接近 0 时沿用上一次的方向） */
    private float dirX, dirY, dirZ;
    /** 最近一次被粒子引擎 tick 时的预算计数，用来判断是不是已经被挤掉了 */
    int lastTicked;

    KuvaParticle(ClientLevel level, KuvaParticles.Builder b, SpriteSet sprites) {
        super(level, b.x, b.y, b.z);
        this.kind = b.kind;
        this.orient = b.orient;
        this.sprites = sprites;
        this.color = b.color;
        this.color2 = b.color2 < 0 ? b.color : b.color2;
        this.s0 = b.size0;
        this.s1 = b.size1;
        this.sizeCurve = b.sizeCurve;
        this.alphaCurve = b.alphaCurve;
        this.drag = b.drag;
        this.spin = b.spin;
        this.home = b.homeTarget;
        this.homeStrength = b.homeStrength;
        this.gravity = b.gravity;
        this.lifetime = Math.max(1, b.life);
        this.xd = b.vx;
        this.yd = b.vy;
        this.zd = b.vz;
        this.hasPhysics = b.kind == KuvaParticles.Kind.DROP;
        this.friction = b.drag;
        this.roll = b.spin != 0.0F || b.kind == KuvaParticles.Kind.SPLAT ? random.nextFloat() * Mth.TWO_PI : 0.0F;
        this.oRoll = this.roll;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.lastTicked = FxBudget.tickCounter();

        float len = Mth.sqrt((float) (b.vx * b.vx + b.vy * b.vy + b.vz * b.vz));
        if (len > 1.0E-5F) {
            dirX = (float) b.vx / len;
            dirY = (float) b.vy / len;
            dirZ = (float) b.vz / len;
        } else {
            dirX = 1.0F;
        }

        switch (b.kind) {
            case GLYPH -> setSprite(sprites.get(KuvaParticles.glyphSprite(b.glyphKind, b.glyphCard),
                    KuvaParticles.GLYPH_SPRITES - 1));
            case SHARD, MIST -> pickVariant(sprites, b.variant, 4);
            case HEX -> pickVariant(sprites, b.variant, 2);
            case SPLAT, BUBBLE -> setSprite(sprites.get(0, 3));
            default -> setSprite(sprites.get(0, 1));
        }

        boolean glow = EffectsDisplayConfig.additiveGlow();
        ParticleRenderType type;
        switch (b.kind) {
            case MIST -> type = glow && KuvaParticleShaders.soft() != null ? KuvaParticleRenderTypes.SOFT : null;
            case SHARD, DROP, SPLAT, BUBBLE -> type = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
            default -> type = glow && KuvaParticleShaders.additive() != null ? KuvaParticleRenderTypes.ADDITIVE : null;
        }
        if (type == null) {
            this.renderType = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
            this.alphaMul = 1.3F;
        } else {
            this.renderType = type;
            // 加法层在白天露天的地方压暗一些（透明混合的雾不乘）；按生成时的位置算一次，粒子活不了几秒
            this.alphaMul = type == KuvaParticleRenderTypes.ADDITIVE && !b.rawAdditive
                    ? KuvaFxDirector.additiveScale(b.x, b.y, b.z) : 1.0F;
        }
        this.alpha0 = b.alpha;
        setColorAt(0.0F);
        updateBox(sizeAt(0.0F));
    }

    /** 指定了变体就取那一张，没指定就随机 */
    private void pickVariant(SpriteSet set, int variant, int count) {
        if (variant < 0) {
            pickSprite(set);
        } else {
            setSprite(set.get(Mth.clamp(variant, 0, count - 1), count - 1));
        }
    }

    // ==================== 每 tick ====================

    @Override
    public void tick() {
        lastTicked = FxBudget.tickCounter();
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        if (home != null && home.isAlive() && homeStrength > 0.0F) {
            double tx = home.getX() - x;
            double ty = home.getY() + home.getBbHeight() * 0.62D - y;
            double tz = home.getZ() - z;
            // 期望速度：剩下的距离的四分之一，按强度往那边拐
            xd += (tx * 0.25D - xd) * homeStrength;
            yd += (ty * 0.25D - yd) * homeStrength;
            zd += (tz * 0.25D - zd) * homeStrength;
        } else {
            yd -= gravity;
        }
        if (kind == KuvaParticles.Kind.DROP) {
            if (tickDrop()) {
                return;
            }
        } else {
            double nx = x + xd;
            double ny = y + yd;
            double nz = z + zd;
            if ((kind == KuvaParticles.Kind.MOTE || kind == KuvaParticles.Kind.BUBBLE) && home == null) {
                float a = SWAY_AMP * (Mth.sin(SWAY_OMEGA * age + swayPhase) - Mth.sin(SWAY_OMEGA * (age - 1) + swayPhase));
                nx += a;
                nz += a * 0.6F;
            }
            x = nx;
            y = ny;
            z = nz;
        }
        if (kind == KuvaParticles.Kind.SPLAT) {
            // 寿命内顺播四帧
            setSprite(sprites.get(Math.min(3, age * 4 / Math.max(1, lifetime)), 3));
        } else if (kind == KuvaParticles.Kind.BUBBLE) {
            // 存活期 0 / 1 两帧每 2 tick 交替，最后 3 tick 换成破开的 2、3 帧
            int left = lifetime - age;
            int frame = left > 3 ? (age >> 1) & 1 : (left > 1 ? 2 : 3);
            setSprite(sprites.get(frame, 3));
        }
        xd *= drag;
        yd *= drag;
        zd *= drag;
        roll += spin;

        float len = Mth.sqrt((float) (xd * xd + yd * yd + zd * zd));
        if (len > 1.0E-4F) {
            dirX = (float) xd / len;
            dirY = (float) yd / len;
            dirZ = (float) zd / len;
        }
        updateBox(sizeAt(age * MS_PER_TICK));
    }

    /**
     * 液滴：按方块碰撞移动，落地就在原地留一个同色溅痕、自己消失。返回 true 表示已经移除
     */
    private boolean tickDrop() {
        setBoundingBox(new AABB(x - 0.04D, y, z - 0.04D, x + 0.04D, y + 0.08D, z + 0.04D));
        move(xd, yd, zd);
        if (!onGround) {
            return false;
        }
        float t = FxCurve.clamp01(age * MS_PER_TICK / lifeMs());
        int c = color == color2 ? color : FxCurve.lerpColor(color, color2, t);
        float s = sizeAt(age * MS_PER_TICK) * SPLAT_FROM_DROP;
        KuvaParticles.splat(x, y, z).color(c).alpha(Math.min(1.0F, alpha0 * 0.95F)).size(s, s * 1.15F).life(30)
                .orient(KuvaParticles.Orient.GROUND).spawn();
        remove();
        return true;
    }

    private void updateBox(float size) {
        float h = Math.max(0.1F, orient == KuvaParticles.Orient.VELOCITY ? size : size * 0.5F);
        setBoundingBox(new AABB(x - h, y - h, z - h, x + h, y + h, z + h));
    }

    @Override
    public boolean shouldCull() {
        // 拉长的火花：中心出屏时整条还该看得见，干脆不剔除（数量本来就少）
        return orient != KuvaParticles.Orient.VELOCITY;
    }

    // ==================== 外观 ====================

    private float lifeMs() {
        return lifetime * MS_PER_TICK;
    }

    private float sizeAt(float ageMs) {
        return s0 + (s1 - s0) * sizeCurve.eval(ageMs, lifeMs());
    }

    private void setColorAt(float ageMs) {
        float t = FxCurve.clamp01(ageMs / lifeMs());
        int c = color == color2 ? color : FxCurve.lerpColor(color, color2, t);
        rCol = ((c >> 16) & 0xFF) / 255.0F;
        gCol = ((c >> 8) & 0xFF) / 255.0F;
        bCol = (c & 0xFF) / 255.0F;
        float curve = kind == KuvaParticles.Kind.SPLAT
                // 溅痕：一直实着，最后 10 tick 线性淡掉
                ? FxCurve.clamp01((lifeMs() - ageMs) / (10.0F * MS_PER_TICK))
                : alphaCurve.eval(ageMs, lifeMs());
        alpha = Mth.clamp(alpha0 * curve * alphaMul, 0.0F, 1.0F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return sizeAt((age + partialTick) * MS_PER_TICK) * 0.5F;
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float partialTick) {
        float ageMs = (age + partialTick) * MS_PER_TICK;
        setColorAt(ageMs);
        if (alpha <= 0.002F) {
            return;
        }
        switch (orient) {
            case VELOCITY -> renderStretched(vc, camera, partialTick, ageMs);
            case GROUND -> renderGround(vc, camera, partialTick, ageMs);
            default -> super.render(vc, camera, partialTick);
        }
    }

    /** 沿速度方向在屏幕上拉长：长 = 当前尺寸，宽按长度取 0.03~0.08 格 */
    private void renderStretched(VertexConsumer vc, Camera camera, float pt, float ageMs) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(pt, xo, x) - cam.x);
        float py = (float) (Mth.lerp(pt, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(pt, zo, z) - cam.z);
        float len = sizeAt(ageMs);
        if (kind == KuvaParticles.Kind.DROP) {
            len *= 1.5F;
        }
        float half = len * 0.5F;
        // 贴图里的亮线只占条带高度的三成左右，条带本身得宽一些：太窄时最近邻采样会跳过亮线，星芒看着像虚线
        float w = Mth.clamp(len * 0.3F, 0.08F, 0.16F) * 0.5F;
        if (kind == KuvaParticles.Kind.DROP) {
            w = sizeAt(ageMs) * 0.35F;
        }

        Vector3f d = TMP_D.set(dirX, dirY, dirZ);
        Vector3f view = TMP_VIEW.set(px, py, pz);
        if (view.lengthSquared() < 1.0E-6F) {
            view.set(camera.getLookVector());
        }
        view.normalize();
        Vector3f side = TMP_SIDE.set(d).cross(view);
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(camera.getUpVector());
        }
        side.normalize().mul(w);
        d.mul(half);

        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(pt);
        // p0 = -d -s, p1 = -d +s, p2 = +d +s, p3 = +d -s；法线朝相机才是正面（逆时针）
        Vector3f e1 = TMP_E1.set(side).mul(2.0F);
        Vector3f e2 = TMP_E2.set(d).mul(2.0F).add(side.x * 2.0F, side.y * 2.0F, side.z * 2.0F);
        boolean flip = e1.cross(e2).dot(view) > 0.0F;
        float[][] p = TMP_QUAD;
        setQ(p[0], px - d.x - side.x, py - d.y - side.y, pz - d.z - side.z, u0, v1);
        setQ(p[1], px - d.x + side.x, py - d.y + side.y, pz - d.z + side.z, u0, v0);
        setQ(p[2], px + d.x + side.x, py + d.y + side.y, pz + d.z + side.z, u1, v0);
        setQ(p[3], px + d.x - side.x, py + d.y - side.y, pz + d.z - side.z, u1, v1);
        for (int i = 0; i < 4; i++) {
            float[] q = p[flip ? 3 - i : i];
            vc.vertex(q[0], q[1], q[2]).uv(q[3], q[4]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }
    }

    private static void setQ(float[] q, float x, float y, float z, float u, float v) {
        q[0] = x;
        q[1] = y;
        q[2] = z;
        q[3] = u;
        q[4] = v;
    }

    /** 水平贴地（离地 0.01 格），朝上为正面 */
    private void renderGround(VertexConsumer vc, Camera camera, float pt, float ageMs) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(pt, xo, x) - cam.x);
        float py = (float) (Mth.lerp(pt, yo, y) - cam.y) + 0.01F;
        float pz = (float) (Mth.lerp(pt, zo, z) - cam.z);
        float h = sizeAt(ageMs) * 0.5F;
        float r = Mth.lerp(pt, oRoll, roll);
        float c = Mth.cos(r) * h, s = Mth.sin(r) * h;
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(pt);
        vc.vertex(px - c - s, py, pz - s + c).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        vc.vertex(px + c - s, py, pz + s + c).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        vc.vertex(px + c + s, py, pz + s - c).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        vc.vertex(px - c + s, py, pz - s - c).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    @Override
    protected int getLightColor(float partialTick) {
        if (kind == KuvaParticles.Kind.MIST || kind == KuvaParticles.Kind.SPLAT) {
            // 半吃光照：亮度不低于 8，夜里不会黑成剪影
            int l = super.getLightColor(partialTick);
            int block = Math.max(l & 0xFFFF, 0x80);
            int sky = Math.max((l >> 16) & 0xFFFF, 0x80);
            return (sky << 16) | block;
        }
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return renderType;
    }

    // ==================== /particle 命令用的提供者 ====================

    /** 只给 {@code /particle kuvalich:kl_xxx} 调试用：按这种粒子的默认行为生成一个 */
    @OnlyIn(Dist.CLIENT)
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final KuvaParticles.Kind kind;

        public Provider(SpriteSet sprites, KuvaParticles.Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            KuvaParticles.Builder b = KuvaParticles.defaults(kind, x, y, z).vel(vx, vy, vz);
            KuvaParticle p = new KuvaParticle(level, b, sprites);
            FxBudget.track(p);
            return p;
        }
    }
}
