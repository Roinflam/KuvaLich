package pers.roinflam.kuvalich.client.fx;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.lich.LichVoice;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 玄骸发光层的亮度控制：按实体 id 记状态，渲染层每帧问一次乘子
 *
 * <p>乘子 = 底子 × 提亮 × 持续乘子：</p>
 * <ul>
 *   <li><b>底子</b>：正常是赤毒心跳 {@code 0.55 + 0.45 × heartbeat}，周期 2.4 s（有主玄骸按种子 2.2~2.8 s），
 *       正在追人时减半；说话时在心跳上再加 {@code 0.6 × 音节包络}，封顶 1.6，面甲和胸口跟着音节一亮一暗。
 *       倒地时周期 1.6 s，亮度随倒地剩余时间从 1.0 降到 0.4；消散时不跳，稳在 1，由提亮把它推上去。</li>
 *   <li><b>提亮</b> {@link #boost}：一段一段叠，同一时刻取最大的那段；每段 40 ms 涨起，末尾按长度的三成落回。</li>
 *   <li><b>持续乘子</b> {@link #sustain}：一直有效到再调，改值时 1 s 内线性过渡过去。</li>
 * </ul>
 *
 * <p>心跳的相位按实体的 {@code tickCount} 算（肖像的假实体按墙钟设它，暂停时照样在跳）；提亮、模式切换按导演的演出钟。
 * 肖像里的假实体用负数 id，和世界里的实体不冲突。实体离开客户端世界时清掉它的记录，退出世界时全清。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaGlow {

    /** 发光的节奏 */
    public enum Mode {
        /** 正常心跳 */
        NORMAL,
        /** 倒地：心跳变慢，亮度随剩余时间往下掉 */
        DOWNED,
        /** 消散中 */
        DISSOLVE
    }

    /** 正常心跳的周期（毫秒） */
    public static final float PERIOD_MS = 2400.0F;
    /** 倒地时的心跳周期（毫秒） */
    public static final float DOWNED_PERIOD_MS = 1600.0F;
    /** 说话把乘子顶到的上限 */
    public static final float TALK_CAP = 1.6F;
    /** 乘子总上限：再往上也只是更白，没有意义 */
    public static final float MAX = 4.0F;

    /** 每只最多同时记几段提亮 */
    private static final int BOOST_SLOTS = 4;
    private static final int BOOST_ATTACK_MS = 40;
    private static final int SUSTAIN_RAMP_MS = 1000;
    private static final int PRUNE_SIZE = 96;
    /** 没在用的记录多久之后可以清 */
    private static final long IDLE_FORGET_MS = 60_000L;

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();
    /** 种子 → 心跳周期（毫秒）；按种子算一次要建随机数对象，每帧算不划算 */
    private static final Int2FloatOpenHashMap PERIOD_BY_SEED = new Int2FloatOpenHashMap();

    private KuvaGlow() {
    }

    private static final class State {
        Mode mode = Mode.NORMAL;
        final float[] boostFactor = new float[BOOST_SLOTS];
        final long[] boostStart = new long[BOOST_SLOTS];
        final int[] boostMs = new int[BOOST_SLOTS];
        float sustainFrom = 1.0F;
        float sustainTo = 1.0F;
        long sustainStart;
        long touchedMs;

        float boost(long now) {
            float best = 1.0F;
            for (int i = 0; i < BOOST_SLOTS; i++) {
                int ms = boostMs[i];
                if (ms <= 0) {
                    continue;
                }
                long t = now - boostStart[i];
                if (t < 0L || t >= ms) {
                    if (t >= ms) {
                        boostMs[i] = 0;
                    }
                    continue;
                }
                float env = boostEnvelope(t, ms);
                float v = 1.0F + (boostFactor[i] - 1.0F) * env;
                if (v > best) {
                    best = v;
                }
            }
            return best;
        }

        float sustain(long now) {
            if (sustainFrom == sustainTo) {
                return sustainTo;
            }
            float t = FxCurve.clamp01((now - sustainStart) / (float) SUSTAIN_RAMP_MS);
            if (t >= 1.0F) {
                sustainFrom = sustainTo;
                return sustainTo;
            }
            return sustainFrom + (sustainTo - sustainFrom) * t;
        }

        boolean idle(long now) {
            if (mode != Mode.NORMAL || sustainTo != 1.0F || sustainFrom != 1.0F) {
                return false;
            }
            for (int i = 0; i < BOOST_SLOTS; i++) {
                if (boostMs[i] > 0 && now - boostStart[i] < boostMs[i]) {
                    return false;
                }
            }
            return now - touchedMs > IDLE_FORGET_MS;
        }
    }

    /** 一段提亮的包络：40 ms 涨起、末尾三成（最多 150 ms）落回 */
    private static float boostEnvelope(long t, int ms) {
        int attack = Math.min(BOOST_ATTACK_MS, ms / 3);
        if (t < attack) {
            float x = t / (float) attack;
            return x * x * (3.0F - 2.0F * x);
        }
        int release = Math.min(150, Math.round(ms * 0.3F));
        long fromEnd = ms - t;
        if (fromEnd < release) {
            float x = fromEnd / (float) release;
            return x * x * (3.0F - 2.0F * x);
        }
        return 1.0F;
    }

    /** 在 {@code ms} 毫秒内叠加一段亮度（{@code factor} 是这段的峰值乘子），和已有的提亮取较大值 */
    public static void boost(int entityId, float factor, int ms) {
        if (ms <= 0 || factor <= 1.0F) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        State s = obtain(entityId, now);
        // 先找空位；没有就顶掉此刻最弱的那段
        int slot = -1;
        float weakest = Float.MAX_VALUE;
        for (int i = 0; i < BOOST_SLOTS; i++) {
            long t = now - s.boostStart[i];
            if (s.boostMs[i] <= 0 || t >= s.boostMs[i]) {
                slot = i;
                break;
            }
            float v = 1.0F + (s.boostFactor[i] - 1.0F) * boostEnvelope(Math.max(0L, t), s.boostMs[i]);
            if (v < weakest) {
                weakest = v;
                slot = i;
            }
        }
        s.boostFactor[slot] = Math.min(MAX, factor);
        s.boostStart[slot] = now;
        s.boostMs[slot] = ms;
    }

    /** 切换发光节奏 */
    public static void mode(int entityId, Mode mode) {
        long now = KuvaFxDirector.clockMs();
        if (mode == Mode.NORMAL) {
            State s = STATES.get(entityId);
            if (s != null) {
                s.mode = Mode.NORMAL;
                s.touchedMs = now;
            }
            return;
        }
        obtain(entityId, now).mode = mode;
    }

    /** 这只此刻的发光节奏（调试显示用） */
    public static Mode modeOf(int entityId) {
        State s = STATES.get(entityId);
        return s == null ? Mode.NORMAL : s.mode;
    }

    /**
     * 持续的亮度乘子，一直有效到再次调用改值，和 {@link #boost} 相乘；改值时 1 s 内线性过渡过去，
     * 调用方在档位变化时调一次即可。实体离开世界时清掉
     */
    public static void sustain(int entityId, float factor) {
        long now = KuvaFxDirector.clockMs();
        float f = Math.max(0.0F, Math.min(MAX, factor));
        State s = STATES.get(entityId);
        if (s == null) {
            if (f == 1.0F) {
                return;
            }
            s = obtain(entityId, now);
        }
        if (s.sustainTo == f) {
            return;
        }
        s.sustainFrom = s.sustain(now);
        s.sustainTo = f;
        s.sustainStart = now;
    }

    /** 此刻的总亮度乘子，1 = 不变 */
    public static float multiplier(Entity entity, float partialTick) {
        long now = KuvaFxDirector.clockMs();
        State s = STATES.get(entity.getId());
        Mode mode = s == null ? Mode.NORMAL : s.mode;

        float ticks = entity.tickCount + partialTick;
        float base;
        if (mode == Mode.DISSOLVE) {
            base = 1.0F;
        } else if (mode == Mode.DOWNED) {
            float phi = ticks * 50.0F / DOWNED_PERIOD_MS;
            base = (0.55F + 0.45F * FxCurve.heartbeat(phi, DOWNED_PERIOD_MS)) * (0.4F + 0.6F * downedLeft(entity, partialTick));
        } else {
            float period = periodMs(entity);
            float phi = ticks * 50.0F / period;
            base = 0.55F + 0.45F * FxCurve.heartbeat(phi, period);
        }
        if (entity instanceof KuvaMasterEntity) {
            float env = LichVoice.envelope(entity, now);
            if (env > 0.0F) {
                base = Math.min(TALK_CAP, base + 0.6F * env);
            }
        }
        // 没走消散演出的死亡（收不到离场包时的兜底）：前 4 tick 亮一下，之后随模型透明度熄灭
        if (entity instanceof LivingEntity living && living.deathTime > 0 && living.deathTime < 4
                && !LichFx.isDissolving(entity.getId())) {
            base = Math.max(base, 1.6F);
        }
        if (s == null) {
            return base;
        }
        s.touchedMs = now;
        return Math.min(MAX, base * s.boost(now) * s.sustain(now));
    }

    /** 心跳周期：有主玄骸按种子，其余 2.4 s；正在追人时减半 */
    private static float periodMs(Entity entity) {
        float period = PERIOD_MS;
        if (entity instanceof KuvaMasterEntity lich) {
            int seed = lich.getLichSeed();
            if (seed != 0) {
                period = PERIOD_BY_SEED.get(seed);
                if (period <= 0.0F) {
                    if (PERIOD_BY_SEED.size() > 64) {
                        PERIOD_BY_SEED.clear();
                    }
                    period = LichProfile.glowPeriodSeconds(seed) * 1000.0F;
                    PERIOD_BY_SEED.put(seed, period);
                }
            }
        }
        if (entity instanceof Mob mob && mob.isAggressive()) {
            period *= 0.5F;
        }
        return period;
    }

    /** 倒地剩余时间的比例 0~1；没有倒地计时时按 1 */
    private static float downedLeft(Entity entity, float partialTick) {
        if (!(entity instanceof AbstractKuva kuva)) {
            return 1.0F;
        }
        int total = kuva.getMercyTotalSeconds() * 20;
        if (total <= 0) {
            return 1.0F;
        }
        return FxCurve.clamp01(kuva.getMercyRemainingTicks(partialTick) / total);
    }

    private static State obtain(int id, long now) {
        State s = STATES.get(id);
        if (s == null) {
            if (STATES.size() > PRUNE_SIZE) {
                prune(now);
            }
            s = new State();
            STATES.put(id, s);
        }
        s.touchedMs = now;
        return s;
    }

    private static void prune(long now) {
        var it = STATES.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<State> e = it.next();
            if (e.getValue().idle(now)) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            STATES.remove(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 肖像的假实体（负数 id）不随世界走，留着
        STATES.int2ObjectEntrySet().removeIf(e -> e.getIntKey() >= 0);
    }
}
