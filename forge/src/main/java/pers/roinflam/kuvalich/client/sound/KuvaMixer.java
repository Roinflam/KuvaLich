package pers.roinflam.kuvalich.client.sound;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 混音器
 *
 * <ul>
 *   <li><b>主角让位</b>：声源 32 格内有 S 级演出在跑时，B / C 档的声音压到一半（−6 dB），约 0.15 s 内平滑过去，
 *       演出结束再平滑回来。本模组客户端放的声音（{@link KuvaSoundInstance}）每 tick 向 {@link #gain} 要这个值；
 *       服务端放的本模组声音（玄骸 / 奴仆的环境、受击、脚步，机器方块）按 B 档算，在音量混入点直接乘。</li>
 *   <li><b>命中闪避</b>：命中总线放本地玩家的主音时登记 {@link #duckVictim}，这段时间里那只实体身上的受击声压一半。</li>
 *   <li><b>全局压混</b>：{@link #duck} / {@link #release} 把<b>除豁免以外的所有声音</b>（原版环境声、怪叫、音乐、别的模组、
 *       本模组的其它声音）一起压低，用在怜悯之击特写这种「世界一下子静下去」的时刻。
 *       <ul>
 *         <li>每个 key 各自有一条增益曲线：同一个 key 再调 {@code duck} 就是从当前值改去新目标；</li>
 *         <li>多个 key 同时压时取<b>最低</b>的那个，不叠乘（两处都压 −12 dB 结果还是 −12 dB）；</li>
 *         <li>豁免按声音事件 id 的前缀：{@code kuvalich:mercy.*}、{@code kuvalich:requiem.*}、{@code kuvalich:ui.comm.*}，
 *             处决本身的声音、安魂的声音和通讯声照原音量；</li>
 *         <li>原版只在开播时、以及会 tick 的声音每 tick 重算音量，所以增益变化期间每个客户端 tick 让声音管理器把所有在播的声音
 *             重算一遍（{@code updateSourceVolume} 对非主音量类别就是这么做的），已经在播的雨声、音乐也会跟着变小；增益不变时不刷；</li>
 *         <li>关掉「屏幕效果」时整套不生效。</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>所有声音的最终音量都在声音引擎的音量计算出口乘上 {@link #gainFor}（{@code MixinSoundEngineMixer}）。
 * 时间走导演的单调时钟，游戏暂停时曲线也停。</p>
 *
 * <p>自己订阅事件的客户端类必须写 {@code value = Dist.CLIENT}：只写 {@code @OnlyIn} 不够，
 * 专用服上照样会被扫描注册，一加载就因为被剥掉的客户端类而崩。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaMixer {

    /** 被主角压制时的倍数 */
    private static final float HERO_DUCK = 0.5F;
    /** 每 tick 往目标增益走的比例（3 tick ≈ 0.15 s 走完） */
    private static final float SMOOTH_STEP = 1.0F / 3.0F;
    /** 受击声被闪避时的倍数 */
    private static final float VICTIM_DUCK = 0.5F;
    /** 受击声离被闪避的实体多近算它的（格²） */
    private static final double VICTIM_RADIUS_SQ = 2.0D * 2.0D;
    /** 压混最多压到多少分贝（再低原版会把音量当成 0 直接停掉声音） */
    private static final float MIN_DB = -60.0F;
    /** 增益变化小于这个就不再刷新在播的声音 */
    private static final float REFRESH_EPS = 1.0E-3F;

    private static final Map<KuvaSoundInstance, float[]> CURRENT = new WeakHashMap<>();
    private static final Int2LongOpenHashMap DUCKED = new Int2LongOpenHashMap();

    /** 全局压混的各个 key，按加入顺序 */
    private static final List<Duck> DUCKS = new ArrayList<>();
    /** 这一 tick 算好的全局压混倍数（1 = 不压），混入点直接读 */
    private static float globalGain = 1.0F;
    /** 上一次刷新在播声音时用的倍数 */
    private static float appliedGain = 1.0F;
    /** 调试命令用：还要往日志里记几个 tick 的音量变化 */
    private static int traceLeft;
    private static int traceTick = -1;
    private static int tickCount;

    private KuvaMixer() {
    }

    /** 一个 key 的压混曲线：在分贝上从 fromDb 线性走到 toDb */
    private static final class Duck {
        final String key;
        float fromDb;
        float toDb;
        long startMs;
        int durMs;
        boolean releasing;

        Duck(String key) {
            this.key = key;
        }

        float dbAt(long now) {
            if (durMs <= 0) {
                return toDb;
            }
            float t = Mth.clamp((now - startMs) / (float) durMs, 0.0F, 1.0F);
            return fromDb + (toDb - fromDb) * t;
        }

        boolean finished(long now) {
            return releasing && now - startMs >= durMs;
        }
    }

    // ==================== 主角让位、命中闪避 ====================

    /** 这个实例此刻的增益（0~1 的倍数），不含全局压混（那部分在混入点另乘） */
    public static float gain(KuvaSoundInstance sound) {
        FxTier tier = sound.tier();
        float target = 1.0F;
        if ((tier == FxTier.B || tier == FxTier.C) && !sound.isRelative()
                && KuvaFxDirector.heroActiveNear(sound.getX(), sound.getY(), sound.getZ())) {
            target = HERO_DUCK;
        }
        float[] cur = CURRENT.computeIfAbsent(sound, s -> new float[]{-1.0F});
        if (cur[0] < 0.0F) {
            cur[0] = target;
        } else if (cur[0] != target) {
            float step = (1.0F - HERO_DUCK) * SMOOTH_STEP;
            cur[0] = cur[0] < target ? Math.min(target, cur[0] + step) : Math.max(target, cur[0] - step);
        }
        return cur[0];
    }

    /** 命中总线放本地玩家的主音时调：{@code ms} 毫秒内这只实体的受击声压低 */
    public static void duckVictim(int entityId, int ms) {
        if (DUCKED.size() > 64) {
            long now = KuvaFxDirector.clockMs();
            DUCKED.int2LongEntrySet().removeIf(e -> e.getLongValue() < now);
        }
        DUCKED.put(entityId, KuvaFxDirector.clockMs() + ms);
    }

    /** 这只实体的受击声此刻是否该压低 */
    public static boolean isVictimDucked(int entityId) {
        return DUCKED.get(entityId) > KuvaFxDirector.clockMs();
    }

    // ==================== 全局压混 ====================

    /**
     * 全局压混：把除豁免之外的所有声音压低 {@code gainDb} 分贝（负数），{@code attackMs} 毫秒内走到位。
     * 同一个 {@code key} 再调就是从当前值改去新目标；多个 key 同时压时取最低的，不叠乘
     */
    public static void duck(String key, float gainDb, int attackMs) {
        long now = KuvaFxDirector.clockMs();
        Duck d = find(key);
        if (d == null) {
            d = new Duck(key);
            d.fromDb = 0.0F;
            DUCKS.add(d);
        } else {
            d.fromDb = d.dbAt(now);
        }
        d.toDb = Mth.clamp(gainDb, MIN_DB, 0.0F);
        d.startMs = now;
        d.durMs = Math.max(0, attackMs);
        d.releasing = false;
    }

    /** 放开某个 key 的压混，{@code releaseMs} 毫秒内回到原音量；这个 key 没在压就什么都不做 */
    public static void release(String key, int releaseMs) {
        Duck d = find(key);
        if (d == null) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        d.fromDb = d.dbAt(now);
        d.toDb = 0.0F;
        d.startMs = now;
        d.durMs = Math.max(0, releaseMs);
        d.releasing = true;
    }

    /** 调试：接下来 {@code n} 个客户端 tick 里，每 tick 把第一次被混音器改动的音量前后值打进日志 */
    public static void traceNext(int n) {
        traceLeft = Math.max(0, n);
        traceTick = -1;
    }

    /** 混入点用：这次要不要记日志 */
    public static boolean tracing() {
        return traceLeft > 0;
    }

    /** 混入点用：记一次音量计算前后的值（每 tick 只记第一次） */
    public static void trace(SoundInstance sound, float before, float after) {
        if (traceLeft <= 0 || before == after || traceTick == tickCount) {
            return;
        }
        traceTick = tickCount;
        traceLeft--;
        LogUtil.info("[KuvaMixer] " + sound.getLocation() + " (" + sound.getSource().getName() + ") calculateVolume "
                + String.format("%.4f -> %.4f", before, after));
    }

    /** 全局压混此刻的倍数（1 = 不压）；调试叠层显示用 */
    public static float globalGain() {
        return globalGain;
    }

    /** 此刻在压的 key，逗号隔开；没有就是 "-"（调试叠层显示用） */
    public static String activeKeys() {
        if (DUCKS.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Duck d : DUCKS) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(d.key);
        }
        return sb.toString();
    }

    /**
     * 混音器对这个声音的线性倍数，1 = 不变；声音引擎每次算音量时都乘它
     *
     * <ul>
     *   <li>全局压混：不在豁免前缀里的声音乘当前的压混倍数；</li>
     *   <li>服务端放的本模组声音（不是 {@link KuvaSoundInstance} 的）：附近有 S 级演出时压一半；
     *       受击声离一只被闪避的实体很近时再压一半。客户端自己放的那些已经在 {@link #gain} 里算过。</li>
     * </ul>
     */
    public static float gainFor(SoundInstance sound) {
        ResourceLocation loc = sound.getLocation();
        boolean ours = Reference.MOD_ID.equals(loc.getNamespace());
        float g = 1.0F;
        if (globalGain < 1.0F && !(ours && isExempt(loc.getPath()))) {
            g = globalGain;
        }
        if (ours && !(sound instanceof KuvaSoundInstance) && !sound.isRelative()) {
            double x = sound.getX(), y = sound.getY(), z = sound.getZ();
            if (KuvaFxDirector.heroActiveNear(x, y, z)) {
                g *= HERO_DUCK;
            }
            if (!DUCKED.isEmpty() && loc.getPath().contains("hurt") && nearDuckedVictim(x, y, z)) {
                g *= VICTIM_DUCK;
            }
        }
        return g;
    }

    private static boolean isExempt(String path) {
        return path.startsWith("mercy.") || path.startsWith("requiem.") || path.startsWith("ui.comm.");
    }

    private static boolean nearDuckedVictim(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        long now = KuvaFxDirector.clockMs();
        ObjectIterator<Int2LongMap.Entry> it = DUCKED.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2LongMap.Entry e = it.next();
            if (e.getLongValue() <= now) {
                continue;
            }
            Entity ent = mc.level.getEntity(e.getIntKey());
            if (ent != null && ent.distanceToSqr(x, y - ent.getBbHeight() * 0.5D, z) <= VICTIM_RADIUS_SQ) {
                return true;
            }
        }
        return false;
    }

    private static Duck find(String key) {
        for (int i = 0, n = DUCKS.size(); i < n; i++) {
            Duck d = DUCKS.get(i);
            if (d.key.equals(key)) {
                return d;
            }
        }
        return null;
    }

    /** 按导演时钟重算全局压混倍数；放完的 key 删掉 */
    private static float computeGlobal() {
        if (DUCKS.isEmpty()) {
            return 1.0F;
        }
        long now = KuvaFxDirector.clockMs();
        float lowestDb = 0.0F;
        Iterator<Duck> it = DUCKS.iterator();
        while (it.hasNext()) {
            Duck d = it.next();
            if (d.finished(now)) {
                it.remove();
                continue;
            }
            lowestDb = Math.min(lowestDb, d.dbAt(now));
        }
        if (!EffectsDisplayConfig.screenEffects()) {
            return 1.0F;
        }
        return lowestDb >= 0.0F ? 1.0F : (float) Math.pow(10.0D, lowestDb / 20.0D);
    }

    /**
     * 每个客户端 tick 末尾：更新全局压混倍数；变了就让声音管理器把在播的声音全部重算一次音量。
     * 离开世界时全部放开
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        tickCount++;
        if (mc.level == null) {
            DUCKS.clear();
            DUCKED.clear();
        }
        globalGain = computeGlobal();
        if (Math.abs(globalGain - appliedGain) > REFRESH_EPS || (globalGain == 1.0F && appliedGain != 1.0F)) {
            appliedGain = globalGain;
            // 对主音量以外的类别，原版会把所有在播的声音按各自的类别重算一遍音量，传哪个类别、什么值都只是触发
            mc.getSoundManager().updateSourceVolume(SoundSource.HOSTILE, mc.options.getSoundSourceVolume(SoundSource.HOSTILE));
        }
    }
}
