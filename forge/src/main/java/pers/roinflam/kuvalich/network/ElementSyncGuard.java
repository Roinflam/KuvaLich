package pers.roinflam.kuvalich.network;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.network.packet.ElementDebuffPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元素 debuff 视觉同步包节流器（服务端专用）
 * Element debuff visual sync packet throttler (server-side)
 *
 * <p>解决场景：SlashBlade 群体斩击、Goety AOE、爆炸半径溅射等一 tick 命中
 * 大量目标时，每个目标都会触发若干次 {@code triggerElementEffect}，
 * 每次都通过 {@link ElementEffectNetwork#sendToTrackers} 给该目标的所有 tracker
 * 广播 ElementDebuffPacket。N 目标 × M tracker × K 触发 = 一帧内成百上千次
 * {@code IOUtil.write1} 系统调用，主线程被卡死被 Watchdog 终止。</p>
 *
 * <p>节流策略：
 * <ol>
 *   <li><b>同 tick 同 (target, element) 仅发一次</b>：
 *       吸收同一目标在同一 tick 内的多次重复同步请求。</li>
 *   <li><b>跨 tick 至少间隔 {@link #MIN_SEND_INTERVAL_MS} 毫秒才允许同一组合再发</b>：
 *       元素 debuff 持续 120-240 tick（6~12 秒），
 *       客户端染色每 0.5 秒刷新一次体感无差异。</li>
 *   <li><b>每 tick 末清空"本 tick 已发"集合</b>：避免无限增长。</li>
 *   <li><b>实体离开 / 玩家退出</b>：清理对应条目，防内存泄漏。</li>
 * </ol></p>
 *
 * <p>⭐ 正确性修复（本次）：原 {@code cleanupStaleRecords} 把两套时间基准混用——
 * {@code now} 取 {@code System.currentTimeMillis()/50}（1970 起算，约 3.4×10¹⁰），
 * 而 {@code latestTick} 存的是 {@code level.getGameTime()}（世界启动起算，通常几百万），
 * 于是 {@code now - latestTick > 600} <b>恒为 true</b>，每次触发清理都把整张表清空。
 * 现在统一改用 {@code System.currentTimeMillis()}，节流与清理共用同一基准；
 * 清理触发也从原来不可靠的
 * {@code (System.currentTimeMillis()/50) % 200 == 0} 改为显式计数器。</p>
 *
 * <p>线程安全：仅在服务端主线程访问；外层用 ConcurrentHashMap 保险，
 * 内层 HashMap 因为单线程访问无需加锁。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ElementSyncGuard {

    /** 同一 (target, element) 跨 tick 同步的最小间隔（毫秒），500ms ≈ 10 tick */
    private static final long MIN_SEND_INTERVAL_MS = 500L;

    /** 记录超过此毫秒数未更新即视为过期，可安全清理（元素 debuff 最长 240 tick = 12 秒） */
    private static final long STALE_RECORD_MS = 30000L;

    /** 清理扫描间隔（tick）：200 tick = 10 秒 */
    private static final int CLEANUP_INTERVAL_TICKS = 200;

    /**
     * 本 tick 内已发送的 (entityId, element) 集合
     * 每 tick 末由 {@link #onServerTickEnd} 清空
     */
    private static final Map<Long, Boolean> SENT_THIS_TICK = new ConcurrentHashMap<>();

    /**
     * 上次发送时间记录：entityId → (element → lastSentMillis)
     * <p>⭐ 值统一为 {@link System#currentTimeMillis()}，与节流判定、过期清理共用同一基准。</p>
     */
    private static final Map<Integer, HashMap<String, Long>> LAST_SENT_MS = new ConcurrentHashMap<>();

    /** 清理扫描计时器 */
    private static int cleanupCounter = 0;

    /**
     * 尝试发送视觉 debuff 同步包，受节流策略门控。
     *
     * <p>节流命中（同 tick 重复 / 间隔不足）时返回 false，调用方无需关心，
     * debuff 应用与 DOT 伤害逻辑不受影响——网络包延迟到下次允许时机即可。</p>
     *
     * @param target        被附加 debuff 的目标
     * @param element       元素名（fire/ice/poison/...）
     * @param durationTicks 持续 tick 数
     * @param amplifier     元素等级
     * @return true 表示本次实际发送了包，false 表示被节流
     */
    public static boolean trySend(LivingEntity target, String element, int durationTicks, int amplifier) {
        if (target == null || target.level().isClientSide()) {
            return false;
        }

        int entityId = target.getId();
        long nowMs = System.currentTimeMillis();

        // 第 1 道：同 tick 同 (target, element) 去重
        // 把 entityId 和 element 的 hash 拼成 long key，避免 String 拼接开销
        long tickKey = ((long) entityId << 32) | (element.hashCode() & 0xFFFFFFFFL);
        if (SENT_THIS_TICK.putIfAbsent(tickKey, Boolean.TRUE) != null) {
            return false;
        }

        // 第 2 道：跨 tick 间隔节流（毫秒基准）
        HashMap<String, Long> entityRecord = LAST_SENT_MS.computeIfAbsent(
                entityId, k -> new HashMap<>());
        Long lastSent = entityRecord.get(element);
        if (lastSent != null && nowMs - lastSent < MIN_SEND_INTERVAL_MS) {
            return false;
        }
        entityRecord.put(element, nowMs);

        // 通过节流，实际发送
        ElementEffectNetwork.sendToTrackers(target,
                new ElementDebuffPacket(entityId, element, durationTicks, amplifier));
        return true;
    }

    /**
     * 服务端 tick 末清空"本 tick 已发"集合，并按固定间隔做过期记录清理
     *
     * @param event Forge 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SENT_THIS_TICK.clear();

        // ⭐ 显式计数器控制清理频率，替代原先基于系统时钟取模的不可靠写法
        if (++cleanupCounter >= CLEANUP_INTERVAL_TICKS) {
            cleanupCounter = 0;
            cleanupStaleRecords();
        }
    }

    /**
     * 清理超过 {@link #STALE_RECORD_MS} 未更新的实体记录，防止内存泄漏
     * （{@link EntityLeaveLevelEvent} 在某些场景可能漏触发，此处是兜底）
     *
     * <p>⭐ 与 {@link #trySend} 共用 {@link System#currentTimeMillis()} 基准，
     * 不再出现「条件恒真、整表被清空」的问题。</p>
     */
    private static void cleanupStaleRecords() {
        if (LAST_SENT_MS.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, HashMap<String, Long>>> it = LAST_SENT_MS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, HashMap<String, Long>> entry = it.next();
            HashMap<String, Long> record = entry.getValue();
            if (record.isEmpty()) {
                it.remove();
                continue;
            }
            // 找出该实体所有元素中最近一次发送的时间戳
            long latestMs = 0L;
            for (Long ms : record.values()) {
                if (ms != null && ms > latestMs) {
                    latestMs = ms;
                }
            }
            if (nowMs - latestMs > STALE_RECORD_MS) {
                it.remove();
            }
        }
    }

    /**
     * 实体离开世界时清理对应记录
     *
     * @param event 实体离开事件
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        LAST_SENT_MS.remove(event.getEntity().getId());
    }

    /**
     * 玩家退出时清理对应记录
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT_MS.remove(event.getEntity().getId());
    }

    /** 工具类禁止实例化 */
    private ElementSyncGuard() {
    }
}
