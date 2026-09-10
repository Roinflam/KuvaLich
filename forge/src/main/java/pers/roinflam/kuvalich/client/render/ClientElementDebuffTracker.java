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
 * 供 {@link ElementRenderHandler} 和 {@code ElementGeometryRenderer} 在每帧
 * 渲染时查询。相比基于 {@code AttributeModifier} 的反查，本追踪器实现零延迟的
 * 视觉响应。</p>
 *
 * <p>⭐ 性能优化（本次）：新增 {@link #forEachActiveElement}，
 * 供渲染层以<b>零分配</b>方式遍历实体身上的全部有效元素。
 * 原 {@link #getAllActiveElements} 每次调用都要 {@code new ArrayList}
 * 做快照 + 再 {@code new ArrayList} 遍历，而它在 {@code RenderLivingEvent}
 * 的 Pre / Post 各被调用一次，即每实体每帧 2~4 次分配。
 * 密集战斗（20+ 带 debuff 的怪）会给客户端制造持续的 GC 压力。</p>
 *
 * <p>生命周期：
 * <ul>
 *     <li>{@link #apply}：服务端 packet 到达时写入（或覆盖）debuff 条目。</li>
 *     <li>{@link #onClientTick}：每个客户端 tick 清理过期 debuff。</li>
 *     <li>{@link #onEntityLeave}：实体离开世界时清理对应条目，避免内存泄漏。</li>
 * </ul></p>
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
     * ⭐ 元素访问器（零分配遍历用）
     * Element visitor for allocation-free iteration
     */
    @FunctionalInterface
    public interface ElementVisitor {
        /**
         * 访问一个未过期的元素 debuff
         *
         * @param element   元素类型名
         * @param amplifier 元素等级
         */
        void accept(String element, int amplifier);
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
     * ⭐ 零分配遍历实体身上所有未过期的元素 debuff
     * Allocation-free iteration over all active element debuffs
     *
     * <p>遍历过程中顺便懒清理过期条目（使用 {@code Iterator.remove}，
     * 无需先构造快照）。</p>
     *
     * <p><b>约束</b>：{@code visitor} 内部<b>不得</b>再修改本追踪器的数据
     * （不得调用 {@link #apply}），否则会抛 {@code ConcurrentModificationException}。
     * 渲染层只做颜色累加，满足该约束。</p>
     *
     * @param entity  目标实体
     * @param visitor 元素访问器
     */
    public static void forEachActiveElement(LivingEntity entity, ElementVisitor visitor) {
        Map<String, DebuffData> debuffs = DEBUFFS.get(entity.getId());
        if (debuffs == null || debuffs.isEmpty()) {
            return;
        }

        long currentTick = entity.level().getGameTime();
        Iterator<Map.Entry<String, DebuffData>> iterator = debuffs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, DebuffData> entry = iterator.next();
            DebuffData data = entry.getValue();
            if (currentTick >= data.expiresAtTick) {
                iterator.remove();
            } else {
                visitor.accept(entry.getKey(), data.amplifier);
            }
        }
    }

    /**
     * 获取实体身上所有未过期的元素 debuff 名列表
     *
     * <p>会产生 List 分配，热路径请优先使用 {@link #forEachActiveElement}。
     * 此方法保留供非每帧场景（调试、外部扩展）使用。</p>
     *
     * @param entity 目标实体
     * @return 元素名列表，不存在任何 debuff 时返回空列表
     */
    public static List<String> getAllActiveElements(LivingEntity entity) {
        Map<String, DebuffData> debuffs = DEBUFFS.get(entity.getId());
        if (debuffs == null || debuffs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> active = new ArrayList<>(debuffs.size());
        forEachActiveElement(entity, (element, amplifier) -> active.add(element));
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
