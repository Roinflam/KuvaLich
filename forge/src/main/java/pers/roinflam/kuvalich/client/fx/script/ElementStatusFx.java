package pers.roinflam.kuvalich.client.fx.script;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.renderer.ElementDebuffTracker;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.DamageInfo;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 元素持续状态：贴在身上、跟着状态时长走的那一层
 *
 * <p>每个客户端 tick 扫相机 32 格内的活实体，从元素状态同步表里取它身上的状态，按元素各画各的：</p>
 * <ul>
 *   <li>火：每 4 tick 在身体下部七成的表面冒 2 个往上飘的火苗（每只每秒 10 个）；</li>
 *   <li>冰：每 6 tick 一片贴身冰晶（4 个 / 秒），解冻那一刻掉 6 片碎冰；整只的冰色调在染色那边；</li>
 *   <li>电：麻痹期间每 6~10 tick 身上一道折线电弧；4 格内还有别的被麻痹的，每 3 道里有 1 道跳过去连成链；</li>
 *   <li>磁力：身上 3~5 条横向断续光带（在 {@code ElementGeometryRenderer} 里画，这里不出粒子）；</li>
 *   <li>辐射：每 8 tick 2 团半径 1.5 格内的黄绿光雾（5 个 / 秒）；</li>
 *   <li>病毒：触发那一刻描边闪 120 ms（在 {@code ElementRenderHandler} 里画），持续期间每 8 tick 一个暗色光点贴着表面爬；</li>
 *   <li>穿刺：每 8 tick 身上一个空心六边形闪一下；</li>
 *   <li>切割：流血期间每 6 tick 一簇 2~3 滴血。</li>
 * </ul>
 * <p>毒素、毒气、冲击、爆炸只有触发那一下；腐蚀的持续层去掉了（原作里几乎看不出）。</p>
 *
 * <p>电击和切割不在元素状态同步表里（服务端不发那种包），这里拿元素触发效果包带来的麻痹 tick / 流血轮数自己倒计时。</p>
 *
 * <p>预算：全局每秒至多 400 个粒子（每 tick 20 个，再乘特效强度），超了从远处开始砍：先按距离排好，近的先花。
 * 身上的电弧同屏至多 8 道。tick 里不分配：实体和距离放在预先开好的数组里排序。</p>
 *
 * <p>开着「战斗声增强」时，离相机最近的两只被麻痹的实体各挂一个电流循环声，状态结束时 200 ms 淡出。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementStatusFx {

    private static final double RANGE = 32.0D;
    private static final int MAX_ENTITIES = 64;
    /** 每 tick 的粒子预算（400 / 秒） */
    private static final int PARTICLES_PER_TICK = 20;
    /** 身上的电弧同屏上限 */
    private static final int MAX_BOLTS = 8;
    private static final int BOLT_MS = 150;
    private static final double CHAIN_RANGE = 4.0D;
    private static final int VIRUS_RIM_MS = 120;
    private static final int LOOP_COUNT = 2;

    private static final RandomSource RANDOM = RandomSource.create();

    /** 实体 id → 麻痹到哪个 gameTime */
    private static final Int2LongOpenHashMap SHOCK_UNTIL = new Int2LongOpenHashMap();
    /** 实体 id → 流血到哪个 gameTime */
    private static final Int2LongOpenHashMap BLEED_UNTIL = new Int2LongOpenHashMap();
    /** 实体 id → 病毒描边闪到哪个演出毫秒 */
    private static final Int2LongOpenHashMap VIRUS_RIM_UNTIL = new Int2LongOpenHashMap();
    /** 上一 tick 身上有冰的实体（用来抓解冻那一刻） */
    private static IntOpenHashSet frozenLast = new IntOpenHashSet();
    private static IntOpenHashSet frozenNow = new IntOpenHashSet();

    /** 这一 tick 要处理的实体（按距离排好） */
    private static final LivingEntity[] NEAR = new LivingEntity[MAX_ENTITIES];
    private static final double[] NEAR_D2 = new double[MAX_ENTITIES];
    private static int nearCount;

    /** 正在画的身上电弧各自的结束时刻 */
    private static final long[] BOLT_END = new long[MAX_BOLTS];
    /** 每只麻痹实体自己的下一道电弧时刻、已画道数（决定第几道跳链） */
    private static final Int2LongOpenHashMap NEXT_ARC = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap ARC_COUNT = new Int2LongOpenHashMap();

    /** 电流循环声：挂在哪只实体上 */
    private static final KuvaSoundInstance[] LOOPS = new KuvaSoundInstance[LOOP_COUNT];
    private static final int[] LOOP_TARGET = {-1, -1};

    private static int budget;
    private static long tick;

    private ElementStatusFx() {
    }

    // ==================== 元素触发带来的时长 ====================

    /** 元素触发效果包到达时记下要倒计时的状态（电击麻痹、切割流血、病毒描边闪） */
    static void onProc(int victimId, int idx, int amp, int extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || victimId < 0) {
            return;
        }
        long now = mc.level.getGameTime();
        switch (idx) {
            case ElementFx.ELECTRICITY -> SHOCK_UNTIL.put(victimId, Math.max(SHOCK_UNTIL.get(victimId),
                    now + Math.max(10, extra)));
            case ElementFx.SLASH -> {
                if (extra > 0) {
                    BLEED_UNTIL.put(victimId, Math.max(BLEED_UNTIL.get(victimId), now + extra * 20L));
                }
            }
            case ElementFx.VIRUS -> VIRUS_RIM_UNTIL.put(victimId, KuvaFxDirector.clockMs() + VIRUS_RIM_MS);
            default -> {
            }
        }
    }

    /** 这只实体此刻在不在麻痹中（客户端倒计时） */
    public static boolean isShocked(LivingEntity e) {
        return SHOCK_UNTIL.get(e.getId()) > e.level().getGameTime();
    }

    /** 这只实体此刻在不在流血（客户端倒计时） */
    public static boolean isBleeding(LivingEntity e) {
        return BLEED_UNTIL.get(e.getId()) > e.level().getGameTime();
    }

    /** 病毒触发后描边闪的透明度（0 = 不画），按演出钟走 */
    public static float virusRimAlpha(int entityId) {
        long until = VIRUS_RIM_UNTIL.get(entityId);
        if (until == 0L) {
            return 0.0F;
        }
        long left = until - KuvaFxDirector.clockMs();
        if (left <= 0L) {
            VIRUS_RIM_UNTIL.remove(entityId);
            return 0.0F;
        }
        return Math.min(1.0F, left / (float) VIRUS_RIM_MS * 1.4F);
    }

    /** 调试命令用：直接给一只实体挂上电击 / 流血的倒计时 */
    public static void devApply(LivingEntity e, int idx, int ticks) {
        long now = e.level().getGameTime();
        if (idx == ElementFx.ELECTRICITY) {
            SHOCK_UNTIL.put(e.getId(), now + ticks);
        } else if (idx == ElementFx.SLASH) {
            BLEED_UNTIL.put(e.getId(), now + ticks);
        } else if (idx == ElementFx.VIRUS) {
            VIRUS_RIM_UNTIL.put(e.getId(), KuvaFxDirector.clockMs() + VIRUS_RIM_MS);
        }
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        tick++;
        long now = level.getGameTime();
        if ((tick & 63) == 0) {
            pruneBefore(SHOCK_UNTIL, now);
            pruneBefore(BLEED_UNTIL, now);
            pruneBefore(VIRUS_RIM_UNTIL, KuvaFxDirector.clockMs());
            pruneNotShocked(NEXT_ARC);
            pruneNotShocked(ARC_COUNT);
        }
        Camera cam = KuvaFxDirector.camera();
        if (cam == null) {
            return;
        }
        collect(level, cam.getPosition());
        budget = Math.max(2, Math.round(PARTICLES_PER_TICK * EffectsDisplayConfig.intensity().budgetScale));

        frozenNow.clear();
        for (int i = 0; i < nearCount; i++) {
            LivingEntity e = NEAR[i];
            drawStatus(e, now);
        }
        // 解冻那一刻掉一把碎冰（冰没了、实体还在附近）
        IntIterator it = frozenLast.iterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            if (!frozenNow.contains(id)) {
                Entity e = level.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) {
                    thawShatter(le);
                }
            }
        }
        IntOpenHashSet swap = frozenLast;
        frozenLast = frozenNow;
        frozenNow = swap;

        updateLoops();
        for (int i = 0; i < nearCount; i++) {
            NEAR[i] = null;
        }
    }

    private static void pruneBefore(Int2LongOpenHashMap m, long t) {
        ObjectIterator<Int2LongMap.Entry> it = m.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            if (it.next().getLongValue() <= t) {
                it.remove();
            }
        }
    }

    private static void pruneNotShocked(Int2LongOpenHashMap m) {
        ObjectIterator<Int2LongMap.Entry> it = m.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            if (!SHOCK_UNTIL.containsKey(it.next().getIntKey())) {
                it.remove();
            }
        }
    }

    /** 收集 32 格内的活实体，按距离从近到远排（插入排序，最多 64 只） */
    private static void collect(ClientLevel level, Vec3 cam) {
        nearCount = 0;
        double r2 = RANGE * RANGE;
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || !e.isAlive()) {
                continue;
            }
            if (firstPerson && e == mc.getCameraEntity()) {
                continue;
            }
            double d2 = e.distanceToSqr(cam);
            if (d2 > r2) {
                continue;
            }
            int pos = nearCount;
            if (pos >= MAX_ENTITIES) {
                if (d2 >= NEAR_D2[MAX_ENTITIES - 1]) {
                    continue;
                }
                pos = MAX_ENTITIES - 1;
            } else {
                nearCount++;
            }
            while (pos > 0 && NEAR_D2[pos - 1] > d2) {
                NEAR[pos] = NEAR[pos - 1];
                NEAR_D2[pos] = NEAR_D2[pos - 1];
                pos--;
            }
            NEAR[pos] = e;
            NEAR_D2[pos] = d2;
        }
    }

    private static boolean take(int n) {
        if (budget < n) {
            return false;
        }
        budget -= n;
        return true;
    }

    private static void drawStatus(LivingEntity e, long now) {
        int id = e.getId();
        long phase = tick + id;
        if (ElementDebuffTracker.has(e, "ice")) {
            frozenNow.add(id);
            if (phase % 6 == 0 && take(1)) {
                iceCrystal(e);
            }
        }
        if (ElementDebuffTracker.has(e, "fire") && phase % 4 == 0 && take(2)) {
            fire(e);
        }
        if (ElementDebuffTracker.has(e, "radiation") && phase % 8 == 0 && take(2)) {
            radiation(e);
        }
        if (ElementDebuffTracker.has(e, "virus") && phase % 8 == 3 && take(1)) {
            virusCrawl(e);
        }
        if (ElementDebuffTracker.has(e, "puncture") && phase % 8 == 5 && take(1)) {
            punctureHex(e);
        }
        if (BLEED_UNTIL.get(id) > now && phase % 6 == 1 && take(3)) {
            bleed(e);
        }
        if (SHOCK_UNTIL.get(id) > now) {
            shock(e, now);
        }
    }

    // ==================== 各元素 ====================

    /** 包围盒表面（当作竖直圆柱）上的一个随机点；yLo / yHi 是身高的比例 */
    private static Vec3 surface(LivingEntity e, double yLo, double yHi, double radiusScale) {
        double a = RANDOM.nextDouble() * Math.PI * 2.0D;
        double r = e.getBbWidth() * 0.5D * radiusScale;
        double y = e.getY() + e.getBbHeight() * (yLo + RANDOM.nextDouble() * (yHi - yLo));
        return new Vec3(e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r);
    }

    private static void fire(LivingEntity e) {
        for (int i = 0; i < 2; i++) {
            Vec3 p = surface(e, 0.0D, 0.7D, 1.0D);
            KuvaParticles.glow(p.x, p.y, p.z).vel(0.0D, 0.04D, 0.0D).color(ElementFx.MAIN[ElementFx.FIRE])
                    .color2(ElementFx.DARK[ElementFx.FIRE]).alpha(0.85F).size(0.25F, 0.08F)
                    .alphaCurve(FxCurve.FLICKER).life(10).tier(FxTier.C).spawn();
        }
    }

    private static void iceCrystal(LivingEntity e) {
        Vec3 p = surface(e, 0.1D, 0.9D, 1.0D);
        float s = 0.1F + RANDOM.nextFloat() * 0.06F;
        KuvaParticles.shard(p.x, p.y, p.z).color(ElementFx.CORE[ElementFx.ICE]).color2(ElementFx.MAIN[ElementFx.ICE])
                .alpha(0.9F).size(s, s).spin(0.0F).variant(RANDOM.nextInt(2)).alphaCurve(FxCurve.HOLD_THEN_FADE)
                .life(20).tier(FxTier.C).spawn();
    }

    private static void thawShatter(LivingEntity e) {
        for (int i = 0; i < 6; i++) {
            Vec3 p = surface(e, 0.2D, 0.9D, 1.0D);
            float s = 0.09F + RANDOM.nextFloat() * 0.06F;
            KuvaParticles.shard(p.x, p.y, p.z).vel((p.x - e.getX()) * 0.15D, 0.03D, (p.z - e.getZ()) * 0.15D)
                    .gravity(0.06F).drag(0.95F).spin((RANDOM.nextBoolean() ? 1 : -1) * 0.3F)
                    .color(ElementFx.CORE[ElementFx.ICE]).color2(ElementFx.MAIN[ElementFx.ICE]).alpha(0.95F)
                    .size(s, s * 0.8F).variant(RANDOM.nextInt(2)).life(16 + RANDOM.nextInt(6)).tier(FxTier.C).spawn();
        }
    }

    private static void radiation(LivingEntity e) {
        double cy = e.getY() + e.getBbHeight() * 0.5D;
        for (int i = 0; i < 2; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double r = RANDOM.nextDouble() * 1.5D;
            KuvaParticles.mist(e.getX() + Math.cos(a) * r, cy + (RANDOM.nextDouble() - 0.5D) * e.getBbHeight() * 0.8D,
                            e.getZ() + Math.sin(a) * r).vel(0.0D, 0.004D, 0.0D)
                    .color(ElementFx.MAIN[ElementFx.RADIATION]).alpha(0.2F).size(0.8F, 1.3F).life(24)
                    .tier(FxTier.C).spawn();
        }
    }

    private static void virusCrawl(LivingEntity e) {
        Vec3 p = surface(e, 0.1D, 0.9D, 1.0D);
        KuvaParticles.mote(p.x, p.y, p.z).vel(0.0D, 0.012D, 0.0D).color(ElementFx.DARK[ElementFx.VIRUS])
                .color2(ElementFx.MAIN[ElementFx.VIRUS]).alpha(0.9F).size(0.07F, 0.05F).life(16).tier(FxTier.C).spawn();
    }

    private static void punctureHex(LivingEntity e) {
        Vec3 p = surface(e, 0.2D, 0.85D, 1.05D);
        KuvaParticles.hex(p.x, p.y, p.z).color(ElementFx.MAIN[ElementFx.PUNCTURE]).alpha(0.8F).size(0.16F, 0.2F)
                .variant(1).life(6).tier(FxTier.C).spawn();
    }

    private static void bleed(LivingEntity e) {
        Vec3 p = surface(e, 0.35D, 0.8D, 1.0D);
        int k = 2 + RANDOM.nextInt(2);
        for (int i = 0; i < k; i++) {
            KuvaParticles.drop(p.x, p.y, p.z).vel((RANDOM.nextDouble() - 0.5D) * 0.04D, 0.01D,
                            (RANDOM.nextDouble() - 0.5D) * 0.04D).gravity(0.05F)
                    .color(ElementFx.DARK[ElementFx.SLASH]).alpha(0.95F).size(0.07F, 0.05F).life(14 + RANDOM.nextInt(4))
                    .tier(FxTier.C).spawn();
        }
    }

    /** 麻痹：每 6~10 tick 一道身上的电弧；每 3 道里 1 道跳到 4 格内另一只被麻痹的实体身上 */
    private static void shock(LivingEntity e, long now) {
        int id = e.getId();
        long next = NEXT_ARC.get(id);
        if (now < next) {
            return;
        }
        NEXT_ARC.put(id, now + 6 + RANDOM.nextInt(5));
        long count = ARC_COUNT.get(id) + 1;
        ARC_COUNT.put(id, count);
        int slot = freeBoltSlot();
        if (slot < 0) {
            return;
        }
        Vec3 a = surface(e, 0.2D, 0.9D, 1.0D);
        Vec3 b = null;
        if (count % 3 == 0) {
            LivingEntity other = nearestShocked(e, now);
            if (other != null) {
                b = other.position().add(0.0D, other.getBbHeight() * 0.5D, 0.0D);
            }
        }
        if (b == null) {
            b = surface(e, 0.2D, 0.9D, 1.0D);
        }
        boolean added = KuvaFxGeometry.bolt(a, b).segments(b.distanceTo(a) > 1.5D ? 8 : 5).jitter(0.12F).width(0.035F)
                .rejitter(50).color(FxPalette.ELEC).core(FxPalette.ELEC_CORE).alpha(0.95F, FxCurve.FADE_SQ)
                .life(BOLT_MS).tier(FxTier.C).add();
        if (added) {
            BOLT_END[slot] = KuvaFxDirector.clockMs() + BOLT_MS;
            if (take(1)) {
                KuvaParticles.spark(a.x, a.y, a.z).vel((RANDOM.nextDouble() - 0.5D) * 0.1D, 0.04D,
                                (RANDOM.nextDouble() - 0.5D) * 0.1D).drag(0.7F).color(FxPalette.ELEC_CORE)
                        .color2(FxPalette.ELEC).size(0.12F, 0.04F).orient(KuvaParticles.Orient.VELOCITY).life(3)
                        .tier(FxTier.C).spawn();
            }
        }
    }

    private static int freeBoltSlot() {
        long ms = KuvaFxDirector.clockMs();
        for (int i = 0; i < MAX_BOLTS; i++) {
            if (BOLT_END[i] <= ms) {
                return i;
            }
        }
        return -1;
    }

    private static LivingEntity nearestShocked(LivingEntity self, long now) {
        LivingEntity best = null;
        double bestD = CHAIN_RANGE * CHAIN_RANGE;
        for (int i = 0; i < nearCount; i++) {
            LivingEntity o = NEAR[i];
            if (o == self || SHOCK_UNTIL.get(o.getId()) <= now) {
                continue;
            }
            double d = o.distanceToSqr(self);
            if (d <= bestD) {
                bestD = d;
                best = o;
            }
        }
        return best;
    }

    // ==================== 电流循环声 ====================

    /** 离相机最近的两只被麻痹的实体各挂一个循环声；不再是那两只了就 200 ms 淡出 */
    private static void updateLoops() {
        boolean on = EffectsDisplayConfig.combatSoundPlus();
        int found = 0;
        int t0 = -1, t1 = -1;
        if (on) {
            long now = Minecraft.getInstance().level.getGameTime();
            for (int i = 0; i < nearCount && found < LOOP_COUNT; i++) {
                if (SHOCK_UNTIL.get(NEAR[i].getId()) > now) {
                    if (found == 0) {
                        t0 = NEAR[i].getId();
                    } else {
                        t1 = NEAR[i].getId();
                    }
                    found++;
                }
            }
        }
        for (int s = 0; s < LOOP_COUNT; s++) {
            int target = LOOP_TARGET[s];
            if (target >= 0 && target != t0 && target != t1) {
                if (LOOPS[s] != null) {
                    LOOPS[s].fadeOut(200);
                }
                LOOPS[s] = null;
                LOOP_TARGET[s] = -1;
            }
        }
        startLoop(t0);
        startLoop(t1);
    }

    private static void startLoop(int target) {
        if (target < 0 || LOOP_TARGET[0] == target || LOOP_TARGET[1] == target) {
            return;
        }
        int s = LOOP_TARGET[0] < 0 ? 0 : LOOP_TARGET[1] < 0 ? 1 : -1;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(target);
        if (s < 0 || e == null) {
            return;
        }
        KuvaSoundInstance snd = KuvaSoundPlayer.at(KuvaLichSounds.ELEMENT_ELECTRICITY_LOOP.get(), SoundSource.HOSTILE,
                e.position(), 0.5F, 1.0F).follow(e).loop(true).fadeIn(150).tier(FxTier.C);
        KuvaSoundPlayer.play(snd);
        LOOPS[s] = snd;
        LOOP_TARGET[s] = target;
    }

    private static void clearAll() {
        SHOCK_UNTIL.clear();
        BLEED_UNTIL.clear();
        VIRUS_RIM_UNTIL.clear();
        NEXT_ARC.clear();
        ARC_COUNT.clear();
        frozenLast.clear();
        frozenNow.clear();
        for (int s = 0; s < LOOP_COUNT; s++) {
            if (LOOPS[s] != null) {
                LOOPS[s].end();
            }
            LOOPS[s] = null;
            LOOP_TARGET[s] = -1;
        }
        nearCount = 0;
    }

    /** 状态行用：这只实体身上第 {@code idx} 种元素的层数（0 = 没有；电击、切割只有 0 / 1） */
    public static int stacks(LivingEntity e, int idx) {
        if (idx == ElementFx.ELECTRICITY) {
            return isShocked(e) ? 1 : 0;
        }
        if (idx == ElementFx.SLASH) {
            return isBleeding(e) ? 1 : 0;
        }
        int amp = ElementDebuffTracker.getAmplifier(e, DamageInfo.elementName(idx));
        return amp < 0 ? 0 : amp + 1;
    }
}
