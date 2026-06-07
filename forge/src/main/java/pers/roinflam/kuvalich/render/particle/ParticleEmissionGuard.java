package pers.roinflam.kuvalich.render.particle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 元素粒子特效发射限流门控（仅服务端主线程使用）
 * Element Particle Effect Emission Throttle Guard (server main thread only)
 *
 * <p>背景：SlashBlade 群体斩击 / 多元素武器 / 多重射击 在一帧内可能触发数百次
 * {@code ElementParticleEffects.spawn*Burst}，每次最终落到 {@code ServerLevel.sendParticles}
 * 上做包广播，曾经造成主线程在该方法链上 RUNNABLE 卡死并触发 Watchdog。</p>
 *
 * <p>本类提供两种节流粒度：
 * <ol>
 *   <li>{@link #tryAcquire}：实体粒度——同一实体一帧内仅允许一次粒子爆发，
 *       同时全局每 tick 总数受 {@link #GLOBAL_PER_TICK_BUDGET} 限制。</li>
 *   <li>{@link #tryAcquireGlobal}：全局粒度——只受总预算限制，
 *       适用于无关联实体的场景（如 DOT 残留云雾）。</li>
 * </ol></p>
 *
 * <p>实现要点：
 * <ul>
 *   <li>每跨入新 tick 自动重置全局计数和实体计数表，无需外部清理。</li>
 *   <li>仅在服务端主线程调用，无并发；客户端调用方不应使用本类。</li>
 *   <li>计数命中限制时返回 false，调用方应跳过粒子调用——但绝不应跳过伤害 / debuff 逻辑。</li>
 * </ul></p>
 *
 * @author RoinFlam
 */
public final class ParticleEmissionGuard {

    /** 全局每 tick 粒子爆发预算（达到则拒绝后续请求）/ Global per-tick burst budget */
    private static final int GLOBAL_PER_TICK_BUDGET = 30;

    /** 同一实体每 tick 触发上限 / Per-entity per-tick limit */
    private static final int PER_ENTITY_PER_TICK = 1;

    /** 当前 tick 全局已发射计数 / Current tick global emission count */
    private static int globalEmissionCount = 0;

    /** 上次记录的 gameTime tick，用于检测跨 tick 重置 / Last recorded gameTime for tick rollover */
    private static long lastTick = -1L;

    /** 实体 entityId → 当前 tick 已触发计数 / Per-entity emission count this tick */
    private static final Map<Integer, Integer> ENTITY_EMISSION_COUNT = new HashMap<>();

    /**
     * 检查并占用一次"实体级"粒子发射名额。
     * <p>必须在调用 {@code ElementParticleEffects.spawn*} 之前调用，仅在返回 true 时才发射。
     * 即使返回 false，元素 debuff 应用和伤害逻辑仍应继续执行——只跳过视觉粒子。</p>
     *
     * @param level  服务端世界（用于获取 gameTime 做跨 tick 判断）
     * @param entity 触发粒子的实体（受击者），用于实体级别去重
     * @return true 表示允许发射并已占用名额；false 表示已超限制，调用方应跳过粒子
     */
    public static boolean tryAcquire(Level level, LivingEntity entity) {
        if (level == null || entity == null) {
            return false;
        }
        rollOverIfNewTick(level.getGameTime());

        // 全局预算检查
        if (globalEmissionCount >= GLOBAL_PER_TICK_BUDGET) {
            return false;
        }

        // 实体级别检查
        int entityId = entity.getId();
        int entityCount = ENTITY_EMISSION_COUNT.getOrDefault(entityId, 0);
        if (entityCount >= PER_ENTITY_PER_TICK) {
            return false;
        }

        // 占名额
        globalEmissionCount++;
        ENTITY_EMISSION_COUNT.put(entityId, entityCount + 1);
        return true;
    }

    /**
     * 检查并占用一次"全局级"粒子发射名额（不做实体去重）。
     * <p>用于无明确受击实体的场景，例如毒气云雾在中心点持续残留的视觉。</p>
     *
     * @param level 服务端世界
     * @return 是否允许发射
     */
    public static boolean tryAcquireGlobal(Level level) {
        if (level == null) {
            return false;
        }
        rollOverIfNewTick(level.getGameTime());

        if (globalEmissionCount >= GLOBAL_PER_TICK_BUDGET) {
            return false;
        }
        globalEmissionCount++;
        return true;
    }

    /**
     * 跨 tick 重置：检测到 currentTick 与 lastTick 不一致时清空所有计数。
     * 单线程调用，无需同步。
     *
     * @param currentTick 当前 tick 时间
     */
    private static void rollOverIfNewTick(long currentTick) {
        if (currentTick != lastTick) {
            lastTick = currentTick;
            globalEmissionCount = 0;
            ENTITY_EMISSION_COUNT.clear();
        }
    }

    /** 工具类禁止实例化 / Utility class, no instantiation */
    private ParticleEmissionGuard() {
    }
}
