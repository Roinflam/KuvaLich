package pers.roinflam.kuvalich.client.fx.script;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;

/**
 * 暴击的演出（以及处决、死亡抵抗、冲刺、溅射与净化这几个效果包的入口，画面在 {@link WarframeFx}）
 *
 * <p>暴击分档全在伤害数字上（颜色、字号、描边、第一帧放大），世界里不另加特效：一档、二档什么都不画；
 * 三档只在打击点留一个很小的命中闪——白芯一闪（2 tick）加四道短星芒（3 tick），没有暗角、光环和闪光盘。</p>
 *
 * <p>声音：三档放一层很薄的亮边（同一个攻击者 60 ms 内只放一次）；一档、二档的声音只在「战斗声增强」打开时放。
 * 服务端照旧发三档的包，客户端要靠档位决定放不放声。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CombatFx {

    /** 原来三档轻量版的编号；轻量版已经删掉，演示命令还会投这个编号，命中总线按三档处理 */
    public static final int LITE = 4;

    private static final RandomSource RANDOM = RandomSource.create();
    /** 本地玩家打出的命中声：表值 × 0.63（−4 dB）× 距离插值；别人的 × 0.4（−8 dB） */
    static final float LOCAL_GAIN = 0.63F;
    static final float OTHER_GAIN = 0.4F;
    /** 三档的声音按攻击者限频 */
    private static final int RED_SOUND_GATE_MS = 60;

    private CombatFx() {
    }

    /** 暴击（先进命中总线，同一 tick 末尾合并后再放）。a = 档位 1/2/3，b = 攻击者 id + 1 */
    public static void crit(KuvaFxPacket p) {
        int tier = Math.max(1, Math.min(3, p.a));
        HitBus.post(p.b - 1, p.entityId(), HitBus.Kind.CRIT, tier, p);
    }

    /** 自己打出的命中（本地玩家是攻击者） */
    static boolean isLocal(int attackerId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == attackerId;
    }

    /** 这一档此刻能不能出声：三档总能，一档、二档要开「战斗声增强」 */
    static boolean critCanSound(int tier) {
        return tier >= 3 || EffectsDisplayConfig.combatSoundPlus();
    }

    /** 命中总线合并完以后起演出 */
    static void playCrit(HitBus.Hit h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int tier = h.variant;
        boolean local = isLocal(h.attackerId);
        boolean wantsSound = h.sound && (local || tier != 1);
        boolean wantsVisual = tier == 3 && h.visuals;
        if (!wantsSound && !wantsVisual) {
            return;
        }
        Entity victim = h.victimId >= 0 ? mc.level.getEntity(h.victimId) : null;
        Entity attacker = h.attackerId >= 0 ? mc.level.getEntity(h.attackerId) : null;
        Vec3 hit = hitPoint(victim, attacker, h.packet.pos());
        double d = KuvaFxDirector.camDist(hit);

        FxTimeline tl = KuvaFxDirector.start("V10 crit " + tier, hit, tier == 3 ? FxTier.B : FxTier.C);
        if (wantsSound) {
            boolean gateOk = tier != 3 || KuvaSoundPlayer.gate("crit3:" + h.attackerId, RED_SOUND_GATE_MS);
            if (gateOk) {
                tl.at(h.soundDelayMs, () -> playCritSound(tier, hit, d, local, h.soundGain));
                if (local && h.soundGain >= 1.0F) {
                    KuvaMixer.duckVictim(h.victimId, 150);
                }
            }
        }
        if (wantsVisual && d <= KuvaFxDirector.LOD_CULL_DIST) {
            tl.at(0, () -> redFlash(hit));
        }
    }

    /** 打击点：受击者中心往攻击者方向偏半个身宽，高度取 0.6 身高；受击者不在客户端时用包里的坐标 */
    static Vec3 hitPoint(Entity victim, Entity attacker, Vec3 fallback) {
        if (victim == null) {
            return fallback;
        }
        Vec3 c = victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D);
        Vec3 toward = null;
        if (attacker != null && attacker != victim) {
            toward = new Vec3(attacker.getX() - victim.getX(), 0.0D, attacker.getZ() - victim.getZ());
        } else {
            Camera cam = KuvaFxDirector.camera();
            if (cam != null) {
                toward = new Vec3(cam.getPosition().x - victim.getX(), 0.0D, cam.getPosition().z - victim.getZ());
            }
        }
        if (toward == null || toward.lengthSqr() < 1.0E-4D) {
            return c;
        }
        return c.add(toward.normalize().scale(victim.getBbWidth() * 0.5D));
    }

    private static void playCritSound(int tier, Vec3 hit, double d, boolean local, float gain) {
        SoundEvent ev;
        float vol;
        float pitch;
        switch (tier) {
            case 1 -> {
                ev = KuvaLichSounds.COMBAT_CRIT_YELLOW.get();
                vol = 0.5F;
                pitch = KuvaSoundPlayer.jitter(1.01F, 0.05F);
            }
            case 2 -> {
                ev = KuvaLichSounds.COMBAT_CRIT_ORANGE.get();
                vol = 0.7F;
                pitch = KuvaSoundPlayer.jitter(1.0F, 0.035F);
            }
            default -> {
                ev = KuvaLichSounds.COMBAT_CRIT_RED.get();
                vol = 0.35F;
                pitch = KuvaSoundPlayer.jitter(1.0F, 0.03F);
            }
        }
        playCombat(ev, hit, d, local, vol * gain, pitch, FxTier.B);
    }

    /**
     * 战斗声的统一放法：自己打出的定位在打击点但不随距离衰减（远程武器 20 格外打中照样听得到、方向也对），
     * 音量 × 0.63 × 距离插值；别人打的按普通世界声衰减，× 0.4
     */
    static void playCombat(SoundEvent ev, Vec3 hit, double d, boolean local, float vol, float pitch, FxTier tier) {
        KuvaSoundInstance s;
        if (local) {
            s = KuvaSoundPlayer.atFixed(ev, SoundSource.PLAYERS, hit,
                    vol * LOCAL_GAIN * KuvaSoundPlayer.distFalloff(d, 8.0D, 64.0D, 0.7F), pitch);
        } else {
            s = KuvaSoundPlayer.at(ev, SoundSource.PLAYERS, hit, vol * OTHER_GAIN, pitch);
        }
        KuvaSoundPlayer.play(s.tier(tier));
    }

    /** 三档的小闪：白芯 0.2 格、2 tick，加相机平面里四道短星芒（白热 → 暴击红，长 0.25，3 tick）；5 个粒子，无几何 */
    private static void redFlash(Vec3 hit) {
        Camera cam = KuvaFxDirector.camera();
        Vec3 left = cam != null ? new Vec3(cam.getLeftVector()) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = cam != null ? new Vec3(cam.getUpVector()) : new Vec3(0.0D, 1.0D, 0.0D);
        KuvaParticles.glow(hit.x, hit.y, hit.z).color(FxPalette.CRIT_3_CORE).alpha(1.0F).size(0.2F, 0.14F)
                .sizeCurve(FxCurve.OUT_CUBIC).life(2).spawn();
        double off = RANDOM.nextDouble() * Math.PI * 0.5D;
        for (int i = 0; i < 4; i++) {
            double a = off + Math.PI * 0.5D * i;
            Vec3 dir = left.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 p = hit.add(dir.scale(0.125D));
            KuvaParticles.spark(p.x, p.y, p.z).vel(dir.x * 0.04D, dir.y * 0.04D, dir.z * 0.04D).drag(0.6F)
                    .color(FxPalette.CRIT_3_CORE).color2(FxPalette.CRIT_R).alpha(1.0F).size(0.25F, 0.1F)
                    .sizeCurve(FxCurve.OUT_CUBIC).orient(KuvaParticles.Orient.VELOCITY).life(3).spawn();
        }
    }

    // ==================== 其余效果包（画面在 WarframeFx） ====================

    /** 收割 / 斩杀 */
    public static void execute(KuvaFxPacket p) {
        WarframeFx.execute(p);
    }

    /** 死亡抵抗 */
    public static void lastStand(KuvaFxPacket p) {
        WarframeFx.lastStand(p);
    }

    /** 溅射 */
    public static void splash(KuvaFxPacket p) {
        WarframeFx.splash(p);
    }

    /** 净化 */
    public static void purge(KuvaFxPacket p) {
        WarframeFx.purge(p);
    }

    /** 冲刺武器 */
    public static void dash(KuvaFxPacket p) {
        WarframeFx.dash(p);
    }
}
