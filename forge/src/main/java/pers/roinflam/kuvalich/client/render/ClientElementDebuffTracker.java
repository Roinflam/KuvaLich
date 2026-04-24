package pers.roinflam.kuvalich.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端元素 debuff 追踪器
 * Client-side Element Debuff Tracker
 *
 * <p>存储从服务端通过 {@code ElementDebuffPacket} 推送过来的元素 debuff 信息，
 * 供 {@link ElementRenderHandler} 和 {@link ElementGeometryRenderer} 在每帧
 * 渲染时查询。相比基于 {@code AttributeModifier} 的反查，本追踪器实现零延迟的
 * 视觉响应。</p>
 *
 * <p>生命周期：
 * <ul>
 *     <li>{@link #apply}：服务端 packet 到达时写入（或覆盖）debuff 条目。</li>
 *     <li>{@link #onClientTick}：每个客户端 tick 清理过期 debuff。</li>
 *     <li>{@link #onEntityLeave}：实体离开世界时清理对应条目，避免内存泄漏。</li>
 * </ul></p>
 *
 * <p>线程安全：外层 {@link ConcurrentHashMap} 保证多线程 put/get 不冲突；
 * 内层 {@link HashMap} 只在客户端主线程访问（packet handle 在 enqueueWork 里，
 * tick 清理在客户端 tick，都是主线程），无需进一步加锁。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ClientElementDebuffTracker {

    /**
     * entityId → (element → DebuffData)
     * 外层 ConcurrentHashMap 保证 packet 线程和 tick 线程并发访问安全
     */
    private static final Map<Integer, Map<String, DebuffData>> DEBUFFS = new ConcurrentHashMap<>();

    /**
     * 单个 debuff 的数据
     */
    public static final class DebuffData {
        /** 过期时的客户端 gameTime / Client gameTime when this debuff expires */
        public final long expiresAtTick;

        /** 元素等级 / Element amplifier */
        public final int amplifier;

        /**
         * 构造函数
         *
         * @param expiresAtTick 过期时的 gameTime
         * @param amplifier     元素等级
         */
        public DebuffData(long expiresAtTick, int amplifier) {
            this.expiresAtTick = expiresAtTick;
            this.amplifier = amplifier;
        }
    }

    /**
     * 应用一个 debuff（服务端 packet 到达时调用）
     * <p>如果该实体该元素的 debuff 已存在，会被覆盖（符合"续期"语义）。</p>
     *
     * @param entityId      目标实体 ID
     * @param element       元素类型名
     * @param durationTicks 持续 tick 数
     * @param amplifier     元素等级
     */
    public static void apply(int entityId, String element, int durationTicks, int amplifier) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentTick = mc.level.getGameTime();
        long expiresAt = currentTick + durationTicks;
        DEBUFFS.computeIfAbsent(entityId, k -> new HashMap<>())
                .put(element, new DebuffData(expiresAt, amplifier));
    }

    /**
     * 查询实体是否存在指定元素的 debuff
     * <p>过期的 debuff 会在本方法中懒清理。</p>
     *
     * @param entity  目标实体
     * @param element 元素类型名
     * @return 是否存在未过期的该元素 debuff
     */
    public static boolean has(LivingEntity entity, String element) {
        Map<String, DebuffData> debuffs = DEBUFFS.get(entity.getId());
        if (debuffs == null) return false;

        DebuffData data = debuffs.get(element);
        if (data == null) return false;

        long currentTick = entity.level().getGameTime();
        if (currentTick >= data.expiresAtTick) {
            debuffs.remove(element);
            return false;
        }
        return true;
    }

    /**
     * 获取实体指定元素 debuff 的等级
     *
     * @param entity  目标实体
     * @param element 元素类型名
     * @return 等级，不存在时返回 -1
     */
    public static int getAmplifier(LivingEntity entity, String element) {
        Map<String, DebuffData> debuffs = DEBUFFS.get(entity.getId());
        if (debuffs == null) return -1;

        DebuffData data = debuffs.get(element);
        if (data == null) return -1;

        long currentTick = entity.level().getGameTime();
        if (currentTick >= data.expiresAtTick) {
            debuffs.remove(element);
            return -1;
        }
        return data.amplifier;
    }

    /**
     * 获取实体身上所有未过期的元素 debuff 名列表
     * <p>用于多色混合染色：{@code ElementRenderHandler} 根据返回列表遍历
     * 各元素对应的颜色并做等权平均。</p>
     *
     * <p>遍历时做懒清理：遇到过期条目直接从 map 移除，避免下次重复判断。
     * 对 map 做迭代前构造快照避免 ConcurrentModificationException。</p>
     *
     * @param entity 目标实体
     * @return 元素名列表，不存在任何 debuff 时返回空列表
     */
    public static List<String> getAllActiveElements(LivingEntity entity) {
        Map<String, DebuffData> debuffs = DEBUFFS.get(entity.getId());
        if (debuffs == null || debuffs.isEmpty()) {
            return Collections.emptyList();
        }

        long currentTick = entity.level().getGameTime();
        List<String> active = new ArrayList<>(debuffs.size());
        // 构造快照遍历，允许在遍历中清理过期条目
        for (Map.Entry<String, DebuffData> entry : new ArrayList<>(debuffs.entrySet())) {
            if (currentTick >= entry.getValue().expiresAtTick) {
                debuffs.remove(entry.getKey());
            } else {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    /**
     * 清理过期 debuff 的定期任务
     *
     * @param event 客户端 tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // 离开世界时清空所有数据
            if (!DEBUFFS.isEmpty()) {
                DEBUFFS.clear();
            }
            return;
        }

        long currentTick = mc.level.getGameTime();

        // 清理所有过期 debuff
        Iterator<Map.Entry<Integer, Map<String, DebuffData>>> entityIter = DEBUFFS.entrySet().iterator();
        while (entityIter.hasNext()) {
            Map.Entry<Integer, Map<String, DebuffData>> entityEntry = entityIter.next();
            Map<String, DebuffData> debuffs = entityEntry.getValue();
            debuffs.entrySet().removeIf(e -> currentTick >= e.getValue().expiresAtTick);
            if (debuffs.isEmpty()) {
                entityIter.remove();
            }
        }
    }

    /**
     * 实体离开世界时清理对应条目
     *
     * @param event 实体离开事件
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        DEBUFFS.remove(event.getEntity().getId());
    }

    /** 工具类禁止实例化 */
    private ClientElementDebuffTracker() {
    }
}