package pers.roinflam.kuvalich.client.mercy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 倒地和倒地超时消散的画面与声音
 *
 * <ul>
 *   <li>倒地那一下：玄骸脚下一圈尘环 + 几团雾、一声倒地；奴仆同样但小一号、声音轻而高。</li>
 *   <li>倒地期间：发光层切到倒地节奏；玄骸每 2.4 s 脚下一道很淡的环，1.6 s 后起一个跟着它的低鸣循环，
 *       音量随剩余时间从 0.8 降到 0.3。奴仆不放循环（一次倒一片会吵）。</li>
 *   <li>低鸣最多同时 4 个，只给离镜头最近的 4 只玄骸，每秒重排一次。</li>
 *   <li>状态离开倒地（被处决、逃走、消失）时发光层回正常，低鸣按情况淡出。</li>
 * </ul>
 *
 * <p>晚进视野、没收到倒地包的客户端每秒扫一次附近的倒地目标，补上发光节奏和低鸣（不补「那一下」）。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyFx {

    /** 同时最多几个低鸣循环 */
    private static final int MAX_LOOPS = 4;
    /** 倒地后多久起低鸣 */
    private static final int LOOP_DELAY_MS = 1600;
    /** 玄骸倒地期间脚下淡环的间隔 */
    private static final int RING_PERIOD_MS = 2400;
    /** 包常比同步数据先到：收到倒地包后这么久内同步状态还不是「倒地」也先等着 */
    private static final int SYNC_GRACE_MS = 1500;
    /** 补扫的范围（格） */
    private static final double SCAN_RADIUS = 48.0D;

    private static final RandomSource RANDOM = RandomSource.create();

    /** 一只倒地目标在这个客户端上的状态 */
    private static final class Downed {
        final int id;
        final boolean lich;
        final long sinceMs;
        /** 到这个时刻起低鸣；小于 0 = 不起（奴仆、或者处决已经开始） */
        long loopAtMs;
        long nextRingMs;
        @Nullable
        KuvaSoundInstance loop;

        Downed(int id, boolean lich, long now) {
            this.id = id;
            this.lich = lich;
            this.sinceMs = now;
            this.nextRingMs = now + RING_PERIOD_MS;
        }
    }

    private static final Int2ObjectOpenHashMap<Downed> DOWNED = new Int2ObjectOpenHashMap<>();
    /** 这一秒允许放低鸣的目标（离镜头最近的几只） */
    private static final IntOpenHashSet LOOP_ALLOWED = new IntOpenHashSet();
    private static int tickCounter;

    private MercyFx() {
    }

    /** 目标倒地：a = 资格人 id + 1；b 低 16 位剩余 tick，bit 16 = 处决被打断后续上（不重放那一下，只重起低鸣） */
    public static void downed(KuvaFxPacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity e = level.getEntity(p.entityId());
        if (e == null) {
            return;
        }
        boolean resume = (p.b & (1 << 16)) != 0;
        if (resume) {
            MercyClient.interrupt(e.getId());
        }
        long now = KuvaFxDirector.clockMs();
        Downed old = DOWNED.remove(e.getId());
        if (old != null && old.loop != null) {
            old.loop.end();
        }
        boolean lich = !(e instanceof KuvaSlaveEntity);
        Downed d = new Downed(e.getId(), lich, now);
        d.loopAtMs = lich ? now + (resume ? 0 : LOOP_DELAY_MS) : -1L;
        DOWNED.put(d.id, d);
        KuvaGlow.mode(d.id, KuvaGlow.Mode.DOWNED);
        if (!resume) {
            burst(e, lich);
        }
        if (lich) {
            LOOP_ALLOWED.add(d.id);
        }
    }

    /** 倒地超时，消散（奴仆；玄骸超时走离场包） */
    public static void expire(KuvaFxPacket p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity e = level.getEntity(p.entityId());
        Downed d = DOWNED.remove(p.entityId());
        if (d != null && d.loop != null) {
            d.loop.fadeOut(300);
        }
        if (e != null) {
            LichFx.dissolve(e, LichFx.Kind.EXPIRE);
        }
    }

    /** 处决开始：低鸣 {@code ms} 毫秒淡出，之后不再起 */
    static void stopLoop(int entityId, int ms) {
        Downed d = DOWNED.get(entityId);
        if (d == null) {
            return;
        }
        if (d.loop != null) {
            d.loop.fadeOut(ms);
            d.loop = null;
        }
        d.loopAtMs = -1L;
    }

    /** 下线、换维度：全部掐掉 */
    static void clearAll() {
        for (Downed d : DOWNED.values()) {
            if (d.loop != null) {
                d.loop.end();
            }
        }
        DOWNED.clear();
        LOOP_ALLOWED.clear();
    }

    // ==================== 倒地那一下 ====================

    private static void burst(Entity e, boolean lich) {
        Vec3 feet = e.position().add(0.0D, 0.05D, 0.0D);
        KuvaFxGeometry.ring(feet).radius(lich ? 0.4F : 0.3F, lich ? 1.6F : 1.0F, FxCurve.OUT_CUBIC)
                .thick(0.12F, 0.04F).color(FxPalette.ASH).alpha(0.5F, FxCurve.FADE_SQ).life(lich ? 250 : 220)
                .tier(FxTier.A).add();
        int n = Math.max(1, Math.round((lich ? 6 : 3) * EffectsDisplayConfig.intensity().budgetScale));
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double sp = 0.02D + RANDOM.nextDouble() * 0.03D;
            KuvaParticles.mist(feet.x, feet.y + 0.1D, feet.z).vel(Math.cos(a) * sp, 0.005D, Math.sin(a) * sp)
                    .color(FxPalette.KUVA_MIST).alpha(0.45F).size(0.4F, 0.8F).life(18 + RANDOM.nextInt(10))
                    .tier(FxTier.A).spawn();
        }
        Vec3 c = MercyTimeline.chest(e);
        if (lich) {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.MERCY_DOWNED.get(), SoundSource.HOSTILE, c, 1.0F, 1.0F)
                    .follow(e).tier(FxTier.A));
        } else {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.MERCY_DOWNED_THRALL.get(), SoundSource.HOSTILE, c, 0.5F, 1.3F)
                    .tier(FxTier.B));
        }
    }

    // ==================== 倒地期间 ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            if (!DOWNED.isEmpty()) {
                clearAll();
            }
            return;
        }
        if (++tickCounter % 20 == 0) {
            scan(mc, level);
        }
        if (DOWNED.isEmpty()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        float pt = mc.getFrameTime();
        for (Downed d : DOWNED.values().toArray(new Downed[0])) {
            Entity e = level.getEntity(d.id);
            if (e == null || e.isRemoved()) {
                leave(d, 300, false);
                continue;
            }
            if (!(e instanceof AbstractKuva kuva)) {
                leave(d, 300, false);
                continue;
            }
            MercyState st = kuva.getMercyState();
            if (st != MercyState.DOWNED) {
                if (st == MercyState.NONE && now - d.sinceMs < SYNC_GRACE_MS) {
                    continue;
                }
                leave(d, st == MercyState.EXECUTING ? 150 : 300, true);
                if (st == MercyState.RISING && d.lich) {
                    // 倒地到时没人处决，自己站起来：低吼一声
                    KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_TAUNT.get(), SoundSource.HOSTILE,
                            MercyTimeline.chest(e), 0.9F, 1.0F).follow(e).tier(FxTier.A));
                }
                continue;
            }
            if (d.lich && now >= d.nextRingMs) {
                d.nextRingMs = now + RING_PERIOD_MS;
                KuvaFxGeometry.ring(e.position().add(0.0D, 0.04D, 0.0D)).radius(0.5F, 2.0F, FxCurve.OUT_CUBIC)
                        .thick(0.06F, 0.02F).color(FxPalette.KUVA).alpha(0.25F, FxCurve.FADE_SQ).life(1100)
                        .tier(FxTier.C).add();
            }
            boolean allowed = LOOP_ALLOWED.contains(d.id);
            if (d.loop == null && d.loopAtMs >= 0L && now >= d.loopAtMs && allowed) {
                d.loop = KuvaSoundPlayer.at(KuvaLichSounds.MERCY_DOWNED_LOOP.get(), SoundSource.HOSTILE,
                        MercyTimeline.chest(e), loopVolume(kuva, pt), 1.0F).follow(e).loop(true).tier(FxTier.A);
                KuvaSoundPlayer.play(d.loop);
            } else if (d.loop != null && !allowed) {
                d.loop.fadeOut(300);
                d.loop = null;
            }
            if (d.loop != null) {
                d.loop.setBaseVolume(loopVolume(kuva, pt));
            }
        }
    }

    /** 低鸣音量：剩余时间满的时候 0.8，快到点时 0.3 */
    private static float loopVolume(AbstractKuva e, float pt) {
        int total = Math.max(1, e.getMercyTotalSeconds() * 20);
        float k = Math.max(0.0F, Math.min(1.0F, e.getMercyRemainingTicks(pt) / total));
        return 0.3F + 0.5F * k;
    }

    /** 离开倒地：发光层回正常、低鸣淡出 */
    private static void leave(Downed d, int fadeMs, boolean entityAlive) {
        DOWNED.remove(d.id);
        LOOP_ALLOWED.remove(d.id);
        if (d.loop != null) {
            d.loop.fadeOut(fadeMs);
            d.loop = null;
        }
        if (entityAlive) {
            KuvaGlow.mode(d.id, KuvaGlow.Mode.NORMAL);
        }
    }

    /** 每秒一次：补上没收到倒地包的目标、重排谁可以放低鸣 */
    private static void scan(Minecraft mc, ClientLevel level) {
        Camera cam = KuvaFxDirector.camera();
        Vec3 eye = cam != null ? cam.getPosition() : (mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO);
        double r2 = SCAN_RADIUS * SCAN_RADIUS;
        long now = KuvaFxDirector.clockMs();
        List<Downed> liches = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof AbstractKuva kuva) || e.distanceToSqr(eye) > r2) {
                continue;
            }
            if (kuva.getMercyState() == MercyState.DOWNED && !DOWNED.containsKey(e.getId()) && kuva.isAlive()) {
                boolean lich = !(e instanceof KuvaSlaveEntity);
                Downed d = new Downed(e.getId(), lich, now);
                d.loopAtMs = lich ? now : -1L;
                DOWNED.put(d.id, d);
                KuvaGlow.mode(d.id, KuvaGlow.Mode.DOWNED);
            }
        }
        for (Downed d : DOWNED.values()) {
            if (d.lich && d.loopAtMs >= 0L) {
                liches.add(d);
            }
        }
        LOOP_ALLOWED.clear();
        if (liches.size() <= MAX_LOOPS) {
            for (Downed d : liches) {
                LOOP_ALLOWED.add(d.id);
            }
            return;
        }
        liches.sort((a, b) -> Double.compare(dist2(level, a.id, eye), dist2(level, b.id, eye)));
        for (int i = 0; i < MAX_LOOPS; i++) {
            LOOP_ALLOWED.add(liches.get(i).id);
        }
    }

    private static double dist2(ClientLevel level, int id, Vec3 eye) {
        Entity e = level.getEntity(id);
        return e == null ? Double.MAX_VALUE : e.distanceToSqr(eye);
    }
}
