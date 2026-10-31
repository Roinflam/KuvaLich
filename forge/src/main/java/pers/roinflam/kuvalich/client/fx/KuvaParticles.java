package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 自制粒子的统一入口，链式构造：
 *
 * <pre>
 * KuvaParticles.glow(x, y, z)
 *     .vel(vx, vy, vz).color(0xFF3B52).color2(0x8E0A1E)   // 寿命内从 color 插到 color2
 *     .alpha(0.9f).size(0.15f, 0.6f).life(12)             // 尺寸按曲线从 s0 到 s1，寿命单位是 tick
 *     .sizeCurve(FxCurve.OUT_CUBIC).alphaCurve(FxCurve.FADE_SQ)
 *     .gravity(0f).drag(0.92f).spin(0.3f)
 *     .orient(Orient.BILLBOARD)
 *     .home(entity, 0.25f)                                // 向实体靠拢
 *     .core()                                             // 远处、预算紧时也保留
 *     .spawn();
 * </pre>
 *
 * <p>尺寸是整张贴图的边长（格）；拉长朝向时是长度。重力、阻力是每 tick 的量。</p>
 *
 * <p><b>自动分流</b>：大而软的光（{@code glow}、{@code ring} 的末尺寸 {@code max(s0, s1)} 超过 0.45 格）和 {@code flare}
 * 不建粒子，在 {@code spawn()} 里改建成同色、同曲线、同寿命的几何图元（{@link KuvaFxGeometry} 的大光晕、朝相机的环、横向光晕），
 * 近处不会露出粒子图集最近邻放大的像素阶梯。调用方照样写粒子的参数。</p>
 *
 * <p>演出里的粒子都从这里直接构造、直接交给粒子引擎，不经过 {@code ParticleOptions} 和网络。
 * 时间轴的阶段里生成时，档位默认取那条时间轴的档。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaParticles {

    private KuvaParticles() {
    }

    /** 光晕、光环的末尺寸超过这个值（格）就改走几何 */
    public static final float SPLIT_SIZE = 0.45F;

    /** 粒子的朝向 */
    public enum Orient {
        /** 始终朝相机 */
        BILLBOARD,
        /** 沿速度方向在屏幕上拉长 */
        VELOCITY,
        /** 水平贴地 */
        GROUND
    }

    /**
     * 安魂符号粒子的画法，图样是那张卡自己的符号（取自物品贴图，颜色烘在贴图里）：
     * 点亮 = 深红填充 + 粉白线 + 红光；判错 = 同一枚符号的红色霓虹 + 一道左下到右上的斜杠，不碎不灰
     */
    public enum GlyphKind {
        LIT, WRONG
    }

    /** {@code kl_glyph} 的贴图张数：8 张卡 + 占位，点亮、判错各一套 */
    public static final int GLYPH_SPRITES = 18;
    /** 卡的种数；卡 id 不在 0~7 时画中性占位（圆角框 + 中间一个小环） */
    public static final int GLYPH_CARDS = 8;

    /** 粒子的种类，和 {@code KuvaLichParticles} 的 11 种一一对应，外加走几何的 {@link #FLARE} */
    public enum Kind {
        GLOW, SPARK, SHARD, RING, HEX, DROP, SPLAT, MIST, BUBBLE, GLYPH, MOTE, FLARE
    }

    public static Builder glow(double x, double y, double z) {
        return new Builder(Kind.GLOW, x, y, z);
    }

    public static Builder spark(double x, double y, double z) {
        return new Builder(Kind.SPARK, x, y, z);
    }

    public static Builder shard(double x, double y, double z) {
        return new Builder(Kind.SHARD, x, y, z);
    }

    public static Builder ring(double x, double y, double z) {
        return new Builder(Kind.RING, x, y, z);
    }

    public static Builder hex(double x, double y, double z) {
        return new Builder(Kind.HEX, x, y, z);
    }

    public static Builder drop(double x, double y, double z) {
        return new Builder(Kind.DROP, x, y, z);
    }

    public static Builder splat(double x, double y, double z) {
        return new Builder(Kind.SPLAT, x, y, z);
    }

    public static Builder mist(double x, double y, double z) {
        return new Builder(Kind.MIST, x, y, z);
    }

    public static Builder bubble(double x, double y, double z) {
        return new Builder(Kind.BUBBLE, x, y, z);
    }

    public static Builder mote(double x, double y, double z) {
        return new Builder(Kind.MOTE, x, y, z);
    }

    /**
     * 安魂符号：{@code cardId} 是那张卡的 id（0~7），认不出（越界、包里给的 15）就画中性占位。
     * 颜色已经在贴图里，顶点色一般给白色，想整体偏色再给别的
     */
    public static Builder glyph(int cardId, GlyphKind kind, double x, double y, double z) {
        Builder b = new Builder(Kind.GLYPH, x, y, z);
        b.glyphKind = kind;
        b.glyphCard = cardId;
        return b;
    }

    /** 符号粒子在 {@code kl_glyph} 贴图表里的下标：0~7 卡、8 占位，判错的再加 9 */
    static int glyphSprite(GlyphKind kind, int cardId) {
        int c = cardId >= 0 && cardId < GLYPH_CARDS ? cardId : GLYPH_CARDS;
        return (kind == GlyphKind.WRONG ? GLYPH_CARDS + 1 : 0) + c;
    }

    /** 每种粒子的默认行为（{@code /particle} 命令和演示项用） */
    public static Builder defaults(Kind kind, double x, double y, double z) {
        Builder b = new Builder(kind, x, y, z);
        switch (kind) {
            case GLOW -> b.color(FxPalette.KUVA).size(0.12F, 0.3F).life(12).drag(0.9F);
            case SPARK -> b.color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.5F, 0.2F).life(7)
                    .drag(0.86F).gravity(0.02F).orient(Orient.VELOCITY);
            case SHARD -> b.color(FxPalette.KUVA).size(0.3F, 0.3F).life(18).gravity(0.05F).spin(0.3F);
            case RING -> b.color(FxPalette.KUVA).size(0.1F, 0.4F).life(9).sizeCurve(FxCurve.OUT_QUART);
            case GLYPH -> b.color(FxPalette.WHITE).size(1.1F, 0.8F).life(30)
                    .sizeCurve(FxCurve.POP).alphaCurve(FxCurve.HOLD_THEN_FADE);
            case MOTE -> b.color(FxPalette.KUVA).size(0.08F, 0.08F).life(28).vel(0.0D, 0.03D, 0.0D).drag(0.98F)
                    .alphaCurve(FxCurve.HOLD_THEN_FADE);
            case MIST -> b.color(FxPalette.KUVA_MIST).alpha(0.45F).size(1.0F, 2.2F).life(45).vel(0.0D, 0.01D, 0.0D)
                    .spin(0.02F);
            case HEX -> b.color(FxPalette.TECH).size(0.3F, 0.3F).life(6).alphaCurve(FxCurve.FLICKER);
            case DROP -> b.color(FxPalette.KUVA_DEEP).color2(FxPalette.KUVA).size(0.14F, 0.14F).life(40).gravity(0.06F)
                    .drag(0.98F).orient(Orient.VELOCITY).alphaCurve(FxCurve.HOLD_THEN_FADE);
            case SPLAT -> b.color(FxPalette.KUVA_DEEP).alpha(0.9F).size(0.5F, 0.5F).life(30).orient(Orient.GROUND);
            case BUBBLE -> b.color(FxPalette.KUVA).alpha(0.85F).size(0.16F, 0.16F).life(14).vel(0.0D, 0.02D, 0.0D)
                    .alphaCurve(FxCurve.HOLD_THEN_FADE);
            default -> b.size(0.2F, 0.2F).life(10);
        }
        return b;
    }

    /** 横向光晕：一律走几何，{@code size(s0, s1)} 当作宽度 */
    public static Builder flare(double x, double y, double z) {
        return new Builder(Kind.FLARE, x, y, z);
    }

    /** 一个粒子的参数。{@link #spawn()} 之前什么都不会发生 */
    public static final class Builder {
        final Kind kind;
        double x, y, z;
        boolean rawAdditive;
        GlyphKind glyphKind;
        int glyphCard = -1;
        int variant = -1;
        double vx, vy, vz;
        int color = 0xFFFFFF;
        int color2 = -1;
        float alpha = 1.0F;
        float size0 = 0.1F, size1 = 0.1F;
        int life = 20;
        FxCurve sizeCurve = FxCurve.OUT_CUBIC;
        FxCurve alphaCurve = FxCurve.FADE_SQ;
        float gravity;
        float drag = 1.0F;
        float spin;
        Orient orient = Orient.BILLBOARD;
        Entity homeTarget;
        float homeStrength;
        boolean core;
        FxTier tier = FxTier.B;
        boolean tierSet;

        Builder(Kind kind, double x, double y, double z) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Builder vel(double vx, double vy, double vz) {
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            return this;
        }

        /** 起始颜色 0xRRGGBB */
        public Builder color(int rgb) {
            this.color = rgb;
            return this;
        }

        /** 结束颜色，寿命内从 {@link #color} 插过来；不设就一直是起始颜色 */
        public Builder color2(int rgb) {
            this.color2 = rgb;
            return this;
        }

        public Builder alpha(float a) {
            this.alpha = a;
            return this;
        }

        public Builder size(float s0, float s1) {
            this.size0 = s0;
            this.size1 = s1;
            return this;
        }

        /** 寿命，单位 tick */
        public Builder life(int ticks) {
            this.life = ticks;
            return this;
        }

        public Builder sizeCurve(FxCurve c) {
            this.sizeCurve = c;
            return this;
        }

        public Builder alphaCurve(FxCurve c) {
            this.alphaCurve = c;
            return this;
        }

        public Builder gravity(float g) {
            this.gravity = g;
            return this;
        }

        public Builder drag(float d) {
            this.drag = d;
            return this;
        }

        public Builder spin(float s) {
            this.spin = s;
            return this;
        }

        public Builder orient(Orient o) {
            this.orient = o;
            return this;
        }

        /** 每 tick 向实体靠拢一点（密语光点用） */
        public Builder home(Entity e, float strength) {
            this.homeTarget = e;
            this.homeStrength = strength;
            return this;
        }

        /**
         * 指定贴图变体（碎晶 0~3、六边形 0 空心 / 1 填充、雾 0~3）；不设就随机挑一张。
         * 溅痕、泡泡的几帧按年龄自己播，不看这个
         */
        public Builder variant(int v) {
            this.variant = v;
            return this;
        }

        /** 改生成位置（演示项在默认行为上挪一挪用） */
        public Builder pos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        /** 加法混合时不乘白天补偿（只给对比演示用） */
        public Builder rawAdditive(boolean raw) {
            this.rawAdditive = raw;
            return this;
        }

        /** 核心层：远处、预算紧时也保留 */
        public Builder core() {
            this.core = true;
            return this;
        }

        /** 所属档位（默认 B；时间轴里的脚本一般传时间轴的档） */
        public Builder tier(FxTier t) {
            this.tier = t;
            this.tierSet = true;
            return this;
        }

        /** 生成；预算满了（或太远、强度选项不让画）返回 false，脚本不必关心 */
        public boolean spawn() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return false;
            }
            FxTimeline tl = KuvaFxDirector.current();
            FxTier t = tierSet || tl == null ? tier : tl.tier();
            float sMax = Math.max(size0, size1);
            if (kind == Kind.FLARE) {
                return KuvaFxGeometry.flare(new Vec3(x, y, z)).width(size0, size1, sizeCurve)
                        .color(color).color2(color2).alpha(alpha, alphaCurve).life(life * 50).tier(t).coreIf(core).rawAdditive(rawAdditive).add();
            }
            if (kind == Kind.GLOW && sMax > SPLIT_SIZE) {
                return KuvaFxGeometry.glow(new Vec3(x, y, z)).size(size0, size1, sizeCurve)
                        .color(color).color2(color2).alpha(alpha, alphaCurve).life(life * 50).tier(t).coreIf(core).rawAdditive(rawAdditive).add();
            }
            if (kind == Kind.RING && sMax > SPLIT_SIZE) {
                // 贴图里的环在半边长 0.78 处：几何环的半径取同一个位置，厚度跟着末尺寸、越扩越细
                return KuvaFxGeometry.ring(new Vec3(x, y, z)).normal(KuvaFxGeometry.Normal.CAMERA)
                        .radius(size0 * 0.39F, size1 * 0.39F, sizeCurve).thick(sMax * 0.14F, sMax * 0.04F)
                        .color(color).color2(color2).alpha(alpha, alphaCurve).life(life * 50).tier(t).coreIf(core).rawAdditive(rawAdditive).add();
            }
            SpriteSet sprites = KuvaSprites.of(kind);
            if (sprites == null) {
                return false;
            }
            if (!FxBudget.tryParticle(t, core, new Vec3(x, y, z))) {
                return false;
            }
            KuvaParticle p = new KuvaParticle(mc.level, this, sprites);
            mc.particleEngine.add(p);
            FxBudget.track(p);
            if (tl != null) {
                tl.particleCount++;
            }
            return true;
        }
    }
}
