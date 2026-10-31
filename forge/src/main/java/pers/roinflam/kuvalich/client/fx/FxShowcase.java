package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.script.CombatFx;
import pers.roinflam.kuvalich.client.fx.script.HitBus;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.fx.script.RequiemFx;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境里单独展示特效的演示项（只由调试命令 {@code /kuvafx DEV_SHOWCASE <序号> [b]} 触发，原点是准星指着的地方）
 *
 * <ul>
 *   <li>0~10：每种粒子的默认行为各一小簇——光点、火花、碎晶、光环、印记（五种图样）、数据光点、雾、六边形、液滴（落地变溅痕）、溅痕、泡泡；</li>
 *   <li>20~28：环、闪光、大光晕、横向光晕、连线、扫描环、弧（三道平行）、光柱、电弧（带两个叉）；</li>
 *   <li>30 / 31 / 32：暗角、闪白、四角括号；</li>
 *   <li>40：对准星附近的玄骸 / 奴仆放一次登场警示（不经过服务端的冷却，方便反复看）；</li>
 *   <li>41：对准星附近的实体放一次安魂成功，击杀者是自己，b 照效果包的标志位（1 怜悯之击后、2 有归还、4~12 用完的卡张数）；</li>
 *   <li>42 / 43 / 44：自己打出的一 / 二 / 三档暴击（三档照常走动态降级）；45：直接放三档轻量版；</li>
 *   <li>50：给准星附近的生物套 4 秒外壳描边（奴仆描边色，b ≠ 0 时换成赤毒红），看描边只在轮廓外、不透墙；
 *       只对原版渲染器画的生物有效（玄骸 / 奴仆的描边由它们自己的渲染器画）；</li>
 *   <li>51：白天加法补偿的对比：左边一组照常乘补偿，右边同样一组不乘；动作栏显示此处的系数。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class FxShowcase {

    /** 外壳描边演示的目标和截止时刻 */
    private static int rimTarget = -1;
    private static long rimUntil;
    private static int rimRgb = FxPalette.THRALL_RIM;

    private FxShowcase() {
    }

    public static void play(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 o = p.pos();
        int a = p.a;
        if (a >= 0 && a <= 10) {
            particles(a, o);
        } else if (a >= 20 && a <= 28) {
            geometry(a, o);
        } else if (a == 30) {
            KuvaScreenFx.vignette(FxPalette.KUVA_DEEP, 0.25F, 120, 200, 800, FxTier.S);
        } else if (a == 31) {
            KuvaScreenFx.flash(FxPalette.WHITE, 0.22F, 140, FxTier.S);
        } else if (a == 32) {
            KuvaScreenFx.edgeBrackets(FxPalette.TECH, 600, FxTier.S);
        } else if (a == 40) {
            Entity e = nearest(o, AbstractKuva.class);
            if (e instanceof AbstractKuva k) {
                LichFx.onEntityEvent(k, AbstractKuva.EV_ALERT);
            }
        } else if (a == 41) {
            Entity e = nearest(o, LivingEntity.class);
            Vec3 c = e != null ? e.position().add(0.0D, e.getBbHeight() * 0.62D, 0.0D) : o;
            RequiemFx.success(new KuvaFxPacket(FxId.REQUIEM_SUCCESS.id, e != null ? e.getId() + 1 : 0,
                    (float) c.x, (float) c.y, (float) c.z, mc.player.getId() + 1, p.b));
        } else if (a >= 42 && a <= 45) {
            Entity e = nearest(o, LivingEntity.class);
            Vec3 c = e != null ? e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D) : o;
            KuvaFxPacket crit = new KuvaFxPacket(FxId.CRIT.id, e != null ? e.getId() + 1 : 0,
                    (float) c.x, (float) c.y, (float) c.z, Math.min(3, a - 41), mc.player.getId() + 1);
            if (a == 45) {
                HitBus.post(mc.player.getId(), crit.entityId(), HitBus.Kind.CRIT, CombatFx.LITE, crit);
            } else {
                CombatFx.crit(crit);
            }
        } else if (a == 50) {
            Entity e = nearest(o, LivingEntity.class);
            if (e != null) {
                rimTarget = e.getId();
                rimUntil = KuvaFxDirector.clockMs() + 4000L;
                rimRgb = p.b != 0 ? FxPalette.KUVA : FxPalette.THRALL_RIM;
            }
        } else if (a == 51) {
            additiveCompare(o);
        }
    }

    private static <T extends Entity> Entity nearest(Vec3 o, Class<T> type) {
        Minecraft mc = Minecraft.getInstance();
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (T e : mc.level.getEntitiesOfClass(type, new AABB(o, o).inflate(4.0D))) {
            if (e == mc.player) {
                continue;
            }
            double d = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D).distanceToSqr(o);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static void particles(int a, Vec3 ground) {
        // 准星多半指着地面：贴地的溅痕、往下掉的液滴照原点，其余抬高一点，免得一半埋在方块里
        Vec3 o = a == 8 || a == 9 ? ground : ground.add(0.0D, 0.6D, 0.0D);
        FxTimeline tl = KuvaFxDirector.start("showcase particle " + a, o, FxTier.A);
        KuvaParticles.Kind kind = switch (a) {
            case 0 -> KuvaParticles.Kind.GLOW;
            case 1 -> KuvaParticles.Kind.SPARK;
            case 2 -> KuvaParticles.Kind.SHARD;
            case 3 -> KuvaParticles.Kind.RING;
            case 4 -> KuvaParticles.Kind.GLYPH;
            case 5 -> KuvaParticles.Kind.MOTE;
            case 6 -> KuvaParticles.Kind.MIST;
            case 7 -> KuvaParticles.Kind.HEX;
            case 8 -> KuvaParticles.Kind.DROP;
            case 9 -> KuvaParticles.Kind.SPLAT;
            default -> KuvaParticles.Kind.BUBBLE;
        };
        tl.at(0, () -> {
            // 符号：8 张卡 + 占位各一枚，判错的那套在上面一圈
            int n = kind == KuvaParticles.Kind.GLYPH ? (KuvaParticles.GLYPH_CARDS + 1) * 2 : 6;
            for (int i = 0; i < n; i++) {
                double ang = Math.PI * 2.0D * i / n;
                double cx = o.x + Math.cos(ang) * 0.6D, cz = o.z + Math.sin(ang) * 0.6D;
                KuvaParticles.Builder b = KuvaParticles.defaults(kind, cx, o.y, cz);
                switch (kind) {
                    case SPARK, SHARD -> b.vel(Math.cos(ang) * 0.2D, 0.1D, Math.sin(ang) * 0.2D);
                    case GLYPH -> {
                        int per = KuvaParticles.GLYPH_CARDS + 1;
                        boolean wrong = i >= per;
                        double ga = Math.PI * 2.0D * (i % per) / per;
                        double gx = o.x + Math.cos(ga) * 2.2D, gy = o.y + (wrong ? 1.7D : 0.5D), gz = o.z + Math.sin(ga) * 2.2D;
                        b = KuvaParticles.glyph(i % per, wrong ? KuvaParticles.GlyphKind.WRONG : KuvaParticles.GlyphKind.LIT,
                                        gx, gy, gz)
                                .color(FxPalette.WHITE).size(1.3F, 0.95F)
                                .sizeCurve(FxCurve.POP).alphaCurve(FxCurve.HOLD_THEN_FADE).life(60);
                        if (wrong) {
                            // 判错符号外圈的红晕
                            KuvaParticles.glow(gx, gy, gz).color(FxPalette.KUVA).alpha(0.6F).size(0.4F, 0.42F).life(60)
                                    .alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
                        }
                    }
                    case HEX -> b.variant(i & 1).life(40).alphaCurve(FxCurve.HOLD_THEN_FADE).size(0.35F, 0.35F)
                            .vel(0.0D, 0.01D, 0.0D);
                    case DROP -> b.pos(cx, o.y + 2.0D, cz).vel(Math.cos(ang) * 0.03D, 0.05D, Math.sin(ang) * 0.03D);
                    case SPLAT -> b.pos(cx, Math.floor(o.y) + 0.02D, cz);
                    case BUBBLE -> b.life(14 + i);
                    default -> {
                    }
                }
                b.spawn();
            }
        });
    }

    private static void geometry(int a, Vec3 ground) {
        // 地面环、光柱、扫描环贴着原点，其余朝相机的图元抬到胸口高度
        Vec3 o = a == 20 || a == 25 || a == 27 ? ground : ground.add(0.0D, 1.0D, 0.0D);
        FxTimeline tl = KuvaFxDirector.start("showcase geometry " + a, o, FxTier.A);
        tl.at(0, () -> {
            switch (a) {
                case 20 -> KuvaFxGeometry.ring(o).normal(KuvaFxGeometry.Normal.UP).radius(0.5F, 4.0F, FxCurve.OUT_QUART)
                        .thick(0.14F, 0.02F).color(FxPalette.KUVA).alpha(0.9F, FxCurve.FADE_SQ).life(900).add();
                case 21 -> KuvaFxGeometry.flash(o).radius(1.5F, 70).core(FxPalette.WHITE).color(FxPalette.KUVA).life(260).add();
                case 22 -> KuvaFxGeometry.glow(o).size(0.6F, 1.6F, FxCurve.OUT_CUBIC).color(FxPalette.KUVA)
                        .alpha(0.9F, FxCurve.FADE_SQ).life(1200).add();
                case 23 -> KuvaFxGeometry.flare(o).width(4.0F, 2.0F, FxCurve.OUT_CUBIC).color(FxPalette.KUVA_HOT)
                        .alpha(0.95F, FxCurve.FADE_SQ).life(600).add();
                case 24 -> KuvaFxGeometry.tether(o, o.add(1.4D, 0.8D, 0.0D), o.add(-1.4D, 0.8D, 0.0D), o.add(0.0D, -1.5D, 0.0D))
                        .draw(0, 0, 120).draw(1, 200, 120).draw(2, 400, 120).snap(-1, 900).width(0.035F)
                        .color(FxPalette.KUVA_PALE).alpha(0.9F, FxCurve.HOLD_THEN_FADE).life(1400).add();
                case 25 -> {
                    Entity e = nearest(o, LivingEntity.class);
                    if (e != null) {
                        KuvaFxGeometry.scan(e).radius(e.getBbWidth() * 0.9F).thick(0.06F).sweep(400)
                                .color(FxPalette.KUVA).alpha(0.9F, FxCurve.FADE_SQ).life(900).add();
                    }
                }
                case 26 -> {
                    // 朝着玩家视线方向的水平斩击：从左后扫到右前
                    float yaw = Minecraft.getInstance().player.getYRot();
                    KuvaFxGeometry.arc(o).normal(KuvaFxGeometry.Normal.UP).start(yaw + 20.0F)
                            .sweep(140.0F, 150, FxCurve.OUT_CUBIC).radius(1.4F).thick(0.16F).parallel(3, 0.08F)
                            .color(FxPalette.KUVA).alpha(0.95F, FxCurve.FADE_SQ).life(450).add();
                }
                case 27 -> KuvaFxGeometry.column(o).radius(0.15F, 0.6F, FxCurve.OUT_CUBIC).height(0.0F, 8.0F, 220, FxCurve.OUT_QUART)
                        .core(FxPalette.KUVA_PALE).color(FxPalette.KUVA).alpha(0.9F, FxCurve.HOLD_THEN_FADE).life(1400).add();
                default -> KuvaFxGeometry.bolt(o.add(-1.6D, 1.0D, 0.0D), o.add(1.6D, -0.2D, 0.4D)).segments(8).jitter(0.25F)
                        .width(0.05F).forks(2).rejitter(50).color(FxPalette.ELEC).core(FxPalette.ELEC_CORE)
                        .alpha(1.0F, FxCurve.HOLD_THEN_FADE).life(700).add();
            }
        });
    }

    /** 51：左右两组同样的加法光（光晕、光环、火花），左边照常乘白天补偿，右边不乘 */
    private static void additiveCompare(Vec3 ground) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 o = ground.add(0.0D, 1.0D, 0.0D);
        float k = KuvaFxDirector.additiveScale(o);
        mc.player.displayClientMessage(Component.literal(String.format("additiveScale = %.2f（左：乘补偿 / 右：不乘）", k)), true);
        Vec3 side = Vec3.directionFromRotation(0.0F, mc.player.getYRot() - 90.0F).scale(1.3D);
        FxTimeline tl = KuvaFxDirector.start("showcase additive", o, FxTier.A);
        tl.at(0, () -> {
            for (int s = 0; s < 2; s++) {
                boolean raw = s == 1;
                Vec3 c = s == 0 ? o.add(side) : o.subtract(side);
                KuvaFxGeometry.glow(c).size(0.9F, 1.2F, FxCurve.OUT_CUBIC).color(FxPalette.KUVA).alpha(0.9F, FxCurve.HOLD_THEN_FADE)
                        .life(2500).rawAdditive(raw).add();
                KuvaFxGeometry.ring(c).normal(KuvaFxGeometry.Normal.CAMERA).radius(0.6F, 0.6F, FxCurve.LINEAR).thick(0.08F, 0.08F)
                        .color(FxPalette.KUVA_PALE).alpha(0.9F, FxCurve.HOLD_THEN_FADE).life(2500).rawAdditive(raw).add();
                for (int i = 0; i < 6; i++) {
                    double ang = Math.PI * 2.0D * i / 6.0D;
                    KuvaParticles.glow(c.x + Math.cos(ang) * 0.35D, c.y + Math.sin(ang) * 0.35D, c.z)
                            .color(FxPalette.KUVA_PALE).size(0.18F, 0.22F).life(50).alphaCurve(FxCurve.HOLD_THEN_FADE)
                            .rawAdditive(raw).spawn();
                }
            }
        });
    }

    /** 外壳描边演示：在原版生物画完以后，用同一个模型、同一套姿态按中心放大再画一遍描边 */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (rimTarget < 0) {
            return;
        }
        LivingEntity e = event.getEntity();
        if (e.getId() != rimTarget) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (now > rimUntil) {
            rimTarget = -1;
            return;
        }
        EntityModel<?> model = event.getRenderer().getModel();
        PoseStack ps = event.getPoseStack();
        float pt = event.getPartialTick();
        float t = 1.0F - (rimUntil - now) / 4000.0F;
        float alpha = 0.85F * KuvaRenderTypes.rimAlphaScale() * (t > 0.85F ? (1.0F - t) / 0.15F : 1.0F);
        // 照原版生物渲染器的变换：转身体朝向、翻转、往下挪 1.501，模型的各部件姿态已经在这一帧摆好了
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        // 以身体中心为原点放大一点
        float cy = 1.501F - e.getBbHeight() * 0.5F;
        ps.translate(0.0F, cy, 0.0F);
        ps.scale(KuvaRenderTypes.RIM_HULL_SCALE, KuvaRenderTypes.RIM_HULL_SCALE, KuvaRenderTypes.RIM_HULL_SCALE);
        ps.translate(0.0F, -cy, 0.0F);
        float r = ((rimRgb >> 16) & 0xFF) / 255.0F, g = ((rimRgb >> 8) & 0xFF) / 255.0F, b = (rimRgb & 0xFF) / 255.0F;
        model.renderToBuffer(ps, event.getMultiBufferSource().getBuffer(KuvaRenderTypes.rimHull()), 0xF000F0,
                OverlayTexture.NO_OVERLAY, r, g, b, alpha);
        ps.popPose();
    }
}
