package pers.roinflam.kuvalich.client.fx.script;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 元素触发（13 种）与毒气云的演出
 *
 * <p>服务端每次元素触发只发一个效果包（{@code a = 元素序号 | 等级 << 8 | 持续量 << 16}，{@code b = 攻击者 id + 1}），
 * 这里先投进命中总线，同一 tick 末尾和暴击合并后再起演出：同一次命中只有排在最前的元素能画几何，声音只留一个主音。
 * 持续量对切割是流血的轮数（每轮 20 tick），对电击是麻痹的 tick 数，其余元素用不到。</p>
 *
 * <p>每种元素的爆发都按「单次粒子 ≤ 16、几何 ≤ 1」排过。颜色是降过饱和度的一套（白天加法叠在亮背景上不像荧光棒），
 * 三种绿按色相分开（毒素偏黄绿、毒气偏青、腐蚀偏橄榄且更暗），火、爆炸、切割、赤毒四种红橙按明度和色相分开，
 * 电和磁力是青色（电是折线电弧，磁力是横向断续光带，冰是冰晶，靠形状分开）。</p>
 *
 * <p>持续状态（贴身的火苗、冰晶、电弧、磁力光带……）在 {@link ElementStatusFx}，这里只管触发那一下。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementFx {

    // ==================== 元素色：主色 / 白热芯 / 暗色，按元素序号排 ====================

    public static final int FIRE = 0, ICE = 1, POISON = 2, ELECTRICITY = 3, SLASH = 4, PUNCTURE = 5, IMPACT = 6,
            GAS = 7, RADIATION = 8, MAGNETIC = 9, CORROSION = 10, EXPLOSION = 11, VIRUS = 12;

    static final int[] MAIN = {
            0xF28A3A, 0x9ED8F2, 0x86D93A, FxPalette.ELEC, 0xC42F45, 0xD9D9F2, 0xE6EEF4,
            0x4FD9B8, 0xE6E86A, FxPalette.MAG, 0x9AA83A, 0xE0603E, 0xE36AB2
    };
    static final int[] CORE = {
            0xFFD9A0, 0xEAFAFF, 0xE0F8C0, FxPalette.ELEC_CORE, 0xFFB8C0, 0xFFFFFF, 0xFFFFFF,
            0xD0FFF2, 0xFBFFD8, FxPalette.MAG_CORE, 0xE2EAB0, 0xFFE0A0, 0xFFD0EC
    };
    static final int[] DARK = {
            0xB8400F, 0x4A90C8, 0x4E9A1E, FxPalette.ELEC_DARK, 0x7A1020, 0x8E96A8, 0x9AA7B4,
            0x1F9C80, 0x9AA040, FxPalette.MAG_EDGE, 0x5A6420, 0x2A2422, 0x7A1A4A
    };
    /** 烟（火、爆炸） */
    static final int SMOKE = 0x2A2422;

    /** 元素主色（越界取火） */
    public static int mainColor(int idx) {
        return MAIN[idx >= 0 && idx < MAIN.length ? idx : 0];
    }

    /** 病毒触发时描边闪的颜色 */
    public static int virusColor() {
        return MAIN[VIRUS];
    }

    /** 同一攻击者 400 ms 内再爆：只放尾音和几个火花 */
    private static final int EXPLOSION_CHAIN_MS = 400;

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Int2LongOpenHashMap LAST_EXPLOSION = new Int2LongOpenHashMap();

    // ==================== 毒气云 ====================

    /** 一朵云同时最多这么多团雾，所有云合计最多 */
    private static final int CLOUD_MAX_MISTS = 12;
    private static final int CLOUD_SHARED_MISTS = 36;
    private static final int CLOUD_MIST_LIFE = 30;
    private static final double CLOUD_RADIUS = 3.0D;

    private static final class Cloud {
        final Vec3 center;
        final long endTick;
        /** 最近 {@link #CLOUD_MIST_LIFE} tick 里每 tick 放了几团（环形），用来数还活着的雾 */
        final int[] spawned = new int[CLOUD_MIST_LIFE];
        long nextTick;

        Cloud(Vec3 center, long endTick) {
            this.center = center;
            this.endTick = endTick;
        }

        int alive() {
            int n = 0;
            for (int v : spawned) {
                n += v;
            }
            return n;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private ElementFx() {
    }

    // ==================== 入口 ====================

    /** 元素触发：先记持续状态要用的时长，再投进命中总线 */
    public static void proc(KuvaFxPacket p) {
        int idx = p.a & 0xFF;
        int amp = (p.a >> 8) & 0xFF;
        int extra = p.a >>> 16;
        ElementStatusFx.onProc(p.entityId(), idx, amp, extra);
        HitBus.post(p.b - 1, p.entityId(), HitBus.Kind.ELEMENT, idx, p);
    }

    /** 毒气云：原点是云心，a = 持续 tick */
    public static void gasCloud(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.a <= 0) {
            return;
        }
        Vec3 c = p.pos();
        if (KuvaFxDirector.camDist(c) > 48.0D) {
            return;
        }
        if (CLOUDS.size() >= 8) {
            CLOUDS.remove(0);
        }
        long now = mc.level.getGameTime();
        Cloud cloud = new Cloud(c, now + Math.min(p.a, 20 * 60));
        CLOUDS.add(cloud);
        // 一开始先铺 8 团
        int n = Math.min(8, CLOUD_SHARED_MISTS - sharedMists());
        for (int i = 0; i < n; i++) {
            if (spawnCloudMist(cloud)) {
                cloud.spawned[(int) (now % CLOUD_MIST_LIFE)]++;
            }
        }
        cloud.nextTick = now + 10;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            CLOUDS.clear();
            LAST_EXPLOSION.clear();
            return;
        }
        if (mc.isPaused() || CLOUDS.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        int slot = (int) (now % CLOUD_MIST_LIFE);
        for (int i = CLOUDS.size() - 1; i >= 0; i--) {
            CLOUDS.get(i).spawned[slot] = 0;
        }
        int shared = sharedMists();
        for (int i = CLOUDS.size() - 1; i >= 0; i--) {
            Cloud cloud = CLOUDS.get(i);
            if (now >= cloud.endTick) {
                CLOUDS.remove(i);
                continue;
            }
            if (now < cloud.nextTick) {
                continue;
            }
            cloud.nextTick = now + 10;
            for (int k = 0; k < 2; k++) {
                if (cloud.alive() >= CLOUD_MAX_MISTS || shared >= CLOUD_SHARED_MISTS) {
                    break;
                }
                if (spawnCloudMist(cloud)) {
                    cloud.spawned[slot]++;
                    shared++;
                }
            }
        }
    }

    private static int sharedMists() {
        int n = 0;
        for (int i = 0; i < CLOUDS.size(); i++) {
            n += CLOUDS.get(i).alive();
        }
        return n;
    }

    /** 一团淡色毒云：撒在半径 3 的圆盘里，带一点切向旋转 */
    private static boolean spawnCloudMist(Cloud cloud) {
        double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
        double r = Math.sqrt(RANDOM.nextDouble()) * CLOUD_RADIUS;
        double x = cloud.center.x + Math.cos(ang) * r;
        double z = cloud.center.z + Math.sin(ang) * r;
        double y = cloud.center.y + 0.3D + RANDOM.nextDouble() * 1.2D;
        double tx = -Math.sin(ang) * 0.02D, tz = Math.cos(ang) * 0.02D;
        float s = 2.0F + RANDOM.nextFloat() * 0.6F;
        return KuvaParticles.mist(x, y, z).vel(tx, 0.004D, tz).color(MAIN[GAS]).color2(DARK[GAS]).alpha(0.22F)
                .size(s * 0.75F, s * 1.25F).sizeCurve(FxCurve.OUT_CUBIC).alphaCurve(FxCurve.HOLD_THEN_FADE)
                .life(CLOUD_MIST_LIFE).tier(FxTier.C).spawn();
    }

    // ==================== 命中总线合并后 ====================

    /** 命中总线合并完以后起演出：画面 + （被选中时）声音 */
    static void playProc(HitBus.Hit h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int idx = Mth.clamp(h.variant, 0, MAIN.length - 1);
        Entity victim = h.victimId >= 0 ? mc.level.getEntity(h.victimId) : null;
        Entity attacker = h.attackerId >= 0 ? mc.level.getEntity(h.attackerId) : null;
        Vec3 center = victim != null ? victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D) : h.packet.pos();
        double d = KuvaFxDirector.camDist(center);
        boolean local = CombatFx.isLocal(h.attackerId);

        boolean chained = false;
        if (idx == EXPLOSION) {
            long now = KuvaFxDirector.clockMs();
            long last = LAST_EXPLOSION.getOrDefault(h.attackerId, Long.MIN_VALUE / 2);
            chained = now - last < EXPLOSION_CHAIN_MS;
            LAST_EXPLOSION.put(h.attackerId, now);
            if (LAST_EXPLOSION.size() > 64) {
                LAST_EXPLOSION.clear();
            }
        }

        FxTimeline tl = KuvaFxDirector.start("V11 " + idx, center, FxTier.B);
        if (h.sound) {
            SoundEvent ev = chained ? KuvaLichSounds.ELEMENT_EXPLOSION_TAIL.get() : KuvaLichSounds.elementProc(idx);
            float vol = idx == EXPLOSION && !chained ? 0.9F : 0.6F;
            float pitch = KuvaSoundPlayer.jitter(1.0F, chained ? 0.05F : 0.06F);
            float gain = h.soundGain;
            tl.at(h.soundDelayMs, () -> CombatFx.playCombat(ev, center, d, local, vol * gain, pitch, FxTier.B));
        }
        if (d > KuvaFxDirector.LOD_CULL_DIST) {
            return;
        }
        final boolean geo = h.geometry;
        final boolean chain = chained;
        tl.at(0, () -> burst(idx, victim, attacker, center, geo, chain));
        if (idx == VIRUS) {
            virusHelix(victim, center, tl);
        }
    }

    /** 一种元素的爆发 */
    private static void burst(int idx, Entity victim, Entity attacker, Vec3 c, boolean geo, boolean chained) {
        float bw = victim != null ? victim.getBbWidth() : 0.6F;
        float bh = victim != null ? victim.getBbHeight() : 1.8F;
        Vec3 dir = attackDir(victim, attacker);
        switch (idx) {
            case FIRE -> fire(c, geo);
            case ICE -> ice(c);
            case POISON -> poison(c, dir, bw);
            case ELECTRICITY -> electricity(c, geo);
            case SLASH -> slash(c, dir, bw, geo);
            case PUNCTURE -> puncture(c, dir, bw);
            case IMPACT -> impact(victim, c, geo);
            case GAS -> gas(c);
            case RADIATION -> radiation(victim, c, bw, geo);
            case MAGNETIC -> magnetic(c);
            case CORROSION -> corrosion(c, bw);
            case EXPLOSION -> explosion(victim, c, geo, chained);
            case VIRUS -> virus(c);
            default -> {
            }
        }
    }

    /** 攻击者 → 受击者的水平方向；没有攻击者时取相机 → 受击者 */
    private static Vec3 attackDir(Entity victim, Entity attacker) {
        if (victim != null) {
            Vec3 from = null;
            if (attacker != null && attacker != victim) {
                from = attacker.position();
            } else {
                Camera cam = KuvaFxDirector.camera();
                if (cam != null) {
                    from = cam.getPosition();
                }
            }
            if (from != null) {
                Vec3 v = new Vec3(victim.getX() - from.x, 0.0D, victim.getZ() - from.z);
                if (v.lengthSqr() > 1.0E-4D) {
                    return v.normalize();
                }
            }
        }
        return new Vec3(1.0D, 0.0D, 0.0D);
    }

    /** 粒子个数按特效强度缩 */
    static int n(int base) {
        return Math.max(1, Math.round(base * EffectsDisplayConfig.intensity().budgetScale));
    }

    private static double rnd(double half) {
        return (RANDOM.nextDouble() * 2.0D - 1.0D) * half;
    }

    // ==================== 13 种爆发 ====================

    /** 火：闪光盘 + 6 个余烬 + 4 道向上火花 + 1 缕烟（11 / 1） */
    private static void fire(Vec3 c, boolean geo) {
        if (geo) {
            KuvaFxGeometry.flash(c).radius(0.5F, 50).core(CORE[FIRE]).color(MAIN[FIRE]).life(170).add();
        }
        for (int i = 0, k = n(6); i < k; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            KuvaParticles.glow(c.x, c.y, c.z).vel(Math.cos(a) * 0.08D, 0.06D + RANDOM.nextDouble() * 0.03D,
                            Math.sin(a) * 0.08D).drag(0.88F)
                    .color(MAIN[FIRE]).color2(DARK[FIRE]).alpha(0.9F).size(0.2F, 0.05F).alphaCurve(FxCurve.FLICKER)
                    .life(10 + RANDOM.nextInt(6)).spawn();
        }
        for (int i = 0, k = n(4); i < k; i++) {
            KuvaParticles.spark(c.x + rnd(0.15D), c.y, c.z + rnd(0.15D)).vel(rnd(0.02D), 0.12D + RANDOM.nextDouble() * 0.05D,
                            rnd(0.02D)).drag(0.85F).color(CORE[FIRE]).color2(MAIN[FIRE]).size(0.22F, 0.08F)
                    .orient(KuvaParticles.Orient.VELOCITY).life(6 + RANDOM.nextInt(3)).spawn();
        }
        KuvaParticles.mist(c.x, c.y + 0.2D, c.z).vel(0.0D, 0.03D, 0.0D).color(SMOKE).alpha(0.3F).size(0.5F, 0.9F)
                .life(24).spawn();
    }

    /** 冰：小光环 + 6 片冰晶（自旋、轻微下落）+ 4 个霜光点（11 / 0） */
    private static void ice(Vec3 c) {
        KuvaParticles.ring(c.x, c.y, c.z).color(MAIN[ICE]).alpha(0.85F).size(0.2F, 0.45F).sizeCurve(FxCurve.OUT_QUART)
                .life(6).spawn();
        for (int i = 0, k = n(6); i < k; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            float s = 0.12F + RANDOM.nextFloat() * 0.06F;
            KuvaParticles.shard(c.x, c.y, c.z).vel(Math.cos(a) * 0.09D, 0.05D + RANDOM.nextDouble() * 0.04D,
                            Math.sin(a) * 0.09D).gravity(0.04F).drag(0.92F).spin((RANDOM.nextBoolean() ? 1 : -1) * 0.25F)
                    .color(CORE[ICE]).color2(MAIN[ICE]).alpha(0.95F).size(s, s * 0.7F).variant(RANDOM.nextInt(2))
                    .life(14 + RANDOM.nextInt(6)).spawn();
        }
        for (int i = 0, k = n(4); i < k; i++) {
            KuvaParticles.mote(c.x + rnd(0.35D), c.y + rnd(0.4D), c.z + rnd(0.35D)).vel(0.0D, -0.01D, 0.0D)
                    .color(CORE[ICE]).alpha(0.8F).size(0.06F, 0.03F).life(16 + RANDOM.nextInt(8)).spawn();
        }
    }

    /** 毒素：命中处冒 3~4 团黄绿毒雾，约 1 秒散开（4 / 0） */
    private static void poison(Vec3 c, Vec3 dir, float bw) {
        Vec3 p = c.subtract(dir.scale(bw * 0.5D));
        for (int i = 0, k = n(3 + RANDOM.nextInt(2)); i < k; i++) {
            KuvaParticles.mist(p.x + rnd(0.2D), p.y + rnd(0.25D), p.z + rnd(0.2D))
                    .vel(rnd(0.012D), 0.008D, rnd(0.012D)).color(MAIN[POISON]).color2(DARK[POISON]).alpha(0.35F)
                    .size(1.0F, 1.8F).sizeCurve(FxCurve.OUT_CUBIC).life(20).spawn();
        }
    }

    /** 电：一道带两个分叉的电弧（芯 + 晕，一个图元）+ 6 个短火花 + 中心一点亮光（7 / 1） */
    private static void electricity(Vec3 c, boolean geo) {
        if (geo) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = 1.0D + RANDOM.nextDouble() * 0.5D;
            Vec3 end = c.add(Math.cos(a) * r, rnd(0.6D), Math.sin(a) * r);
            KuvaFxGeometry.bolt(c, end).segments(7).jitter(0.22F).width(0.05F).forks(2).rejitter(60)
                    .color(FxPalette.ELEC).core(FxPalette.ELEC_CORE).alpha(1.0F, FxCurve.FADE_SQ).life(200).add();
        }
        for (int i = 0, k = n(6); i < k; i++) {
            KuvaParticles.spark(c.x, c.y, c.z).vel(rnd(0.18D), rnd(0.18D), rnd(0.18D)).drag(0.7F)
                    .color(FxPalette.ELEC_CORE).color2(FxPalette.ELEC).size(0.16F, 0.05F)
                    .orient(KuvaParticles.Orient.VELOCITY).life(3 + RANDOM.nextInt(3)).spawn();
        }
        KuvaParticles.glow(c.x, c.y, c.z).color(FxPalette.ELEC).alpha(0.9F).size(0.4F, 0.3F).life(2).spawn();
    }

    /** 切割：两条平行的斩痕弧（一个图元）+ 5 个血滴沿切线喷出 + 3 团血雾（8 / 1） */
    private static void slash(Vec3 c, Vec3 dir, float bw, boolean geo) {
        float start = 30.0F + RANDOM.nextFloat() * 30.0F;
        if (geo) {
            KuvaFxGeometry.arc(c).normal(dir).start(start).sweep(100.0F, 80, FxCurve.OUT_CUBIC)
                    .radius(Math.max(0.35F, bw * 0.9F)).thick(0.06F).parallel(2, 0.08F)
                    .color(CORE[SLASH]).color2(MAIN[SLASH]).alpha(1.0F, FxCurve.FADE_SQ).life(260).add();
        }
        Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
        for (int i = 0, k = n(5); i < k; i++) {
            double s = RANDOM.nextBoolean() ? 1.0D : -1.0D;
            KuvaParticles.drop(c.x, c.y, c.z).vel(side.x * s * 0.12D + rnd(0.03D), 0.06D + RANDOM.nextDouble() * 0.05D,
                            side.z * s * 0.12D + rnd(0.03D)).gravity(0.05F).color(DARK[SLASH]).alpha(0.95F)
                    .size(0.08F, 0.06F).life(14 + RANDOM.nextInt(6)).spawn();
        }
        for (int i = 0, k = n(3); i < k; i++) {
            KuvaParticles.mist(c.x + rnd(0.15D), c.y + rnd(0.15D), c.z + rnd(0.15D)).vel(rnd(0.01D), 0.004D, rnd(0.01D))
                    .color(DARK[SLASH]).alpha(0.5F).size(0.35F, 0.7F).life(12).spawn();
        }
    }

    /** 穿刺：一道长火花沿攻击方向穿过 + 朝攻击者的小环 + 3 片钢色碎片从背面飞出（5 / 0） */
    private static void puncture(Vec3 c, Vec3 dir, float bw) {
        Vec3 front = c.subtract(dir.scale(bw * 0.6D));
        KuvaParticles.spark(front.x, front.y, front.z).vel(dir.x * 0.6D, 0.0D, dir.z * 0.6D).drag(0.5F)
                .color(CORE[PUNCTURE]).color2(MAIN[PUNCTURE]).size(1.2F, 0.4F).orient(KuvaParticles.Orient.VELOCITY)
                .life(4).spawn();
        KuvaParticles.ring(front.x, front.y, front.z).color(MAIN[PUNCTURE]).alpha(0.8F).size(0.1F, 0.45F)
                .sizeCurve(FxCurve.OUT_QUART).life(5).spawn();
        Vec3 back = c.add(dir.scale(bw * 0.5D));
        for (int i = 0, k = n(3); i < k; i++) {
            KuvaParticles.shard(back.x, back.y, back.z).vel(dir.x * 0.15D + rnd(0.05D), 0.05D + rnd(0.03D),
                            dir.z * 0.15D + rnd(0.05D)).gravity(0.05F).spin(0.3F).color(MAIN[PUNCTURE])
                    .color2(DARK[PUNCTURE]).size(0.1F, 0.07F).life(12).spawn();
        }
    }

    /** 冲击：地面冲击环 + 一个白色小环 + 4 团低矮尘雾（5 / 1） */
    private static void impact(Entity victim, Vec3 c, boolean geo) {
        Vec3 foot = victim != null ? victim.position().add(0.0D, 0.05D, 0.0D) : c;
        if (geo) {
            KuvaFxGeometry.ring(foot).normal(KuvaFxGeometry.Normal.UP).radius(0.3F, 1.6F, FxCurve.OUT_QUART)
                    .thick(0.08F, 0.02F).color(MAIN[IMPACT]).alpha(0.9F, FxCurve.FADE_SQ).life(260).add();
        }
        KuvaParticles.ring(c.x, c.y, c.z).color(FxPalette.WHITE).alpha(0.8F).size(0.2F, 0.45F)
                .sizeCurve(FxCurve.OUT_QUART).life(5).spawn();
        for (int i = 0, k = n(4); i < k; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            KuvaParticles.mist(foot.x, foot.y + 0.1D, foot.z).vel(Math.cos(a) * 0.06D, 0.005D, Math.sin(a) * 0.06D)
                    .drag(0.9F).color(DARK[IMPACT]).alpha(0.35F).size(0.4F, 0.8F).life(18).spawn();
        }
    }

    /** 毒气：6 个泡泡 + 一团青绿的光（7 / 0）；云另外走效果包 */
    private static void gas(Vec3 c) {
        for (int i = 0, k = n(6); i < k; i++) {
            KuvaParticles.bubble(c.x + rnd(0.25D), c.y + rnd(0.3D), c.z + rnd(0.25D)).vel(rnd(0.03D), 0.02D, rnd(0.03D))
                    .drag(0.94F).color(MAIN[GAS]).alpha(0.85F).size(0.1F, 0.14F).variant(RANDOM.nextInt(4))
                    .life(16 + RANDOM.nextInt(8)).spawn();
        }
        KuvaParticles.glow(c.x, c.y, c.z).color(DARK[GAS]).alpha(0.35F).size(0.35F, 0.45F).life(8).spawn();
    }

    /** 辐射：一圈倾斜 35° 的细环 + 6 个乱跳的光点（6 / 1） */
    private static void radiation(Entity victim, Vec3 c, float bw, boolean geo) {
        if (geo) {
            double az = RANDOM.nextDouble() * Math.PI * 2.0D;
            double tilt = Math.toRadians(35.0D);
            Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(az), Math.cos(tilt), Math.sin(tilt) * Math.sin(az));
            KuvaFxGeometry.Ring ring = KuvaFxGeometry.ring(c).normal(normal)
                    .radius(Math.max(0.45F, bw * 0.8F), Math.max(0.5F, bw * 0.9F), FxCurve.OUT_CUBIC).thick(0.03F, 0.03F)
                    .color(MAIN[RADIATION]).alpha(0.9F, FxCurve.HOLD_THEN_FADE).life(420);
            if (victim != null) {
                ring.follow(victim, 0.5F);
            }
            ring.add();
        }
        for (int i = 0, k = n(6); i < k; i++) {
            KuvaParticles.mote(c.x + rnd(0.3D), c.y + rnd(0.4D), c.z + rnd(0.3D)).vel(rnd(0.05D), rnd(0.05D), rnd(0.05D))
                    .color(MAIN[RADIATION]).color2(CORE[RADIATION]).alpha(0.9F).size(0.07F, 0.04F)
                    .alphaCurve(FxCurve.FLICKER).life(10 + RANDOM.nextInt(6)).spawn();
        }
    }

    /** 磁力：一个青色小环 + 6 道往外旋的火花（7 / 0）；持续期间的横向光带在别处画 */
    private static void magnetic(Vec3 c) {
        KuvaParticles.ring(c.x, c.y, c.z).color(FxPalette.MAG).alpha(0.85F).size(0.2F, 0.45F).sizeCurve(FxCurve.OUT_QUART)
                .life(6).spawn();
        double off = RANDOM.nextDouble() * Math.PI * 2.0D;
        for (int i = 0, k = n(6); i < k; i++) {
            double a = off + Math.PI * 2.0D * i / 6.0D;
            // 径向 + 切向：拉长后看着像往外旋出去
            double vx = Math.cos(a) * 0.08D - Math.sin(a) * 0.1D;
            double vz = Math.sin(a) * 0.08D + Math.cos(a) * 0.1D;
            KuvaParticles.spark(c.x, c.y + rnd(0.2D), c.z).vel(vx, rnd(0.02D), vz).drag(0.82F)
                    .color(FxPalette.MAG_CORE).color2(FxPalette.MAG).size(0.2F, 0.06F)
                    .orient(KuvaParticles.Orient.VELOCITY).life(6 + RANDOM.nextInt(3)).spawn();
        }
    }

    /** 腐蚀：6 个酸液滴下落 + 4 个泡泡 + 1 缕蒸汽（11 / 0） */
    private static void corrosion(Vec3 c, float bw) {
        for (int i = 0, k = n(6); i < k; i++) {
            KuvaParticles.drop(c.x + rnd(bw * 0.4D), c.y + rnd(0.2D), c.z + rnd(bw * 0.4D))
                    .vel(rnd(0.04D), 0.03D, rnd(0.04D)).gravity(0.05F).color(MAIN[CORROSION]).color2(DARK[CORROSION])
                    .size(0.07F, 0.05F).life(14 + RANDOM.nextInt(6)).spawn();
        }
        for (int i = 0, k = n(4); i < k; i++) {
            KuvaParticles.bubble(c.x + rnd(0.25D), c.y + rnd(0.3D), c.z + rnd(0.25D)).vel(rnd(0.02D), 0.015D, rnd(0.02D))
                    .color(MAIN[CORROSION]).alpha(0.8F).size(0.08F, 0.11F).variant(RANDOM.nextInt(4)).life(14).spawn();
        }
        KuvaParticles.mist(c.x, c.y + 0.2D, c.z).vel(0.0D, 0.025D, 0.0D).color(CORE[CORROSION]).alpha(0.2F)
                .size(0.4F, 0.8F).life(20).spawn();
    }

    /**
     * 爆炸：地面冲击环 + 中心一团热光 + 8 道放射火花 + 3 个余烬 + 4 团烟（16 / 1）；不画横向光晕。
     * 同一个攻击者 400 ms 内连着爆：只放尾音和 6 个火花、不画地面环
     */
    private static void explosion(Entity victim, Vec3 c, boolean geo, boolean chained) {
        if (!chained) {
            Vec3 foot = victim != null ? victim.position().add(0.0D, 0.05D, 0.0D) : c;
            if (geo) {
                KuvaFxGeometry.ring(foot).normal(KuvaFxGeometry.Normal.UP).radius(0.5F, 3.0F, FxCurve.OUT_QUART)
                        .thick(0.15F, 0.02F).color(MAIN[EXPLOSION]).alpha(0.9F, FxCurve.FADE_SQ).life(340).add();
            }
            KuvaParticles.glow(c.x, c.y, c.z).color(CORE[EXPLOSION]).color2(MAIN[EXPLOSION]).alpha(1.0F)
                    .size(0.45F, 0.3F).life(3).spawn();
            for (int i = 0, k = n(3); i < k; i++) {
                KuvaParticles.glow(c.x, c.y, c.z).vel(rnd(0.08D), 0.05D + RANDOM.nextDouble() * 0.04D, rnd(0.08D))
                        .drag(0.9F).color(MAIN[FIRE]).color2(DARK[FIRE]).size(0.16F, 0.04F)
                        .alphaCurve(FxCurve.FLICKER).life(12).spawn();
            }
            for (int i = 0, k = n(4); i < k; i++) {
                KuvaParticles.mist(c.x + rnd(0.3D), c.y + rnd(0.3D), c.z + rnd(0.3D)).vel(rnd(0.02D), 0.02D, rnd(0.02D))
                        .color(SMOKE).alpha(0.4F).size(1.0F, 2.5F).sizeCurve(FxCurve.OUT_CUBIC).life(40).spawn();
            }
        }
        for (int i = 0, k = n(chained ? 6 : 8); i < k; i++) {
            Vec3 v = new Vec3(rnd(1.0D), rnd(1.0D), rnd(1.0D));
            if (v.lengthSqr() < 1.0E-3D) {
                v = new Vec3(0.0D, 1.0D, 0.0D);
            }
            v = v.normalize().scale(0.2D + RANDOM.nextDouble() * 0.12D);
            float len = 0.4F + RANDOM.nextFloat() * 0.4F;
            KuvaParticles.spark(c.x, c.y, c.z).vel(v.x, v.y, v.z).drag(0.82F).color(CORE[EXPLOSION])
                    .color2(MAIN[EXPLOSION]).size(len, len * 0.3F).orient(KuvaParticles.Orient.VELOCITY)
                    .life(6 + RANDOM.nextInt(4)).spawn();
        }
    }

    /** 病毒：5 个泡泡 + 一团暗光；描边闪在状态层 */
    private static void virus(Vec3 c) {
        for (int i = 0, k = n(5); i < k; i++) {
            KuvaParticles.bubble(c.x + rnd(0.25D), c.y + rnd(0.3D), c.z + rnd(0.25D)).vel(rnd(0.02D), 0.012D, rnd(0.02D))
                    .color(MAIN[VIRUS]).alpha(0.85F).size(0.09F, 0.12F).variant(RANDOM.nextInt(4)).life(16).spawn();
        }
        KuvaParticles.glow(c.x, c.y, c.z).color(DARK[VIRUS]).alpha(0.4F).size(0.35F, 0.45F).life(8).spawn();
    }

    /** 病毒的双螺旋：两股各 4 个光点在 400 ms 里绕着受击者转一圈上升（合计 14 / 0） */
    private static void virusHelix(Entity victim, Vec3 c, FxTimeline tl) {
        float bh = victim != null ? victim.getBbHeight() : 1.8F;
        double base = victim != null ? victim.getY() + bh * 0.15D : c.y - 0.5D;
        double h = Math.max(0.8D, bh * 0.7D);
        double off = RANDOM.nextDouble() * Math.PI * 2.0D;
        int per = n(4);
        tl.every(0, 350, 50, step -> {
            int i = step / 50;
            if (i >= per) {
                return;
            }
            double t = (double) i / Math.max(1, per - 1);
            double cx = victim != null ? victim.getX() : c.x;
            double cz = victim != null ? victim.getZ() : c.z;
            for (int s = 0; s < 2; s++) {
                double a = off + s * Math.PI + t * Math.PI * 2.0D;
                KuvaParticles.mote(cx + Math.cos(a) * 0.4D, base + t * h, cz + Math.sin(a) * 0.4D)
                        .vel(0.0D, 0.01D, 0.0D).color(CORE[VIRUS]).color2(MAIN[VIRUS]).alpha(0.9F).size(0.08F, 0.05F)
                        .life(10).spawn();
            }
        });
    }
}
