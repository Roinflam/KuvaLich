package pers.roinflam.kuvalich.dynamicattr;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态属性管理器
 * 负责应用、移除、更新实体的动态属性
 *
 * <p>⭐ 内存泄漏修复（本次，两处，均会随服务器运行时长累积）：</p>
 *
 * <p><b>泄漏一：事件处理器永久驻留 Forge 事件总线。</b><br>
 * {@code MAGNETIC} / {@code RADIATION} / {@code PUNCTURE} 通过
 * {@code withEventHandler} 注册了 {@code LivingHurtEvent} 监听器，
 * 注销只发生在「属性自然过期」或「被显式 remove」两条路径上。
 * 但实体死亡后不再 tick，{@link #processEntityTick} 不会执行，
 * 未过期的 handler 就永久留在事件总线上。
 * 磁力 debuff 仅 120 tick、辐射 240 tick，怪基本都在 debuff 结束前就被打死，
 * 于是每打死一只带元素 debuff 的怪就漏一个监听器。
 * 累积后，<b>每一次伤害事件都要遍历这些僵尸监听器</b>，
 * 表现为 MSPT 随在线时长单调上升、重启后恢复。<br>
 * 修复：新增 {@link #onLivingDeath} 与 {@link #onEntityLeaveLevel} 兜底清理。</p>
 *
 * <p><b>泄漏二：{@code ENTITY_ATTRIBUTES} 的 key 永不删除。</b><br>
 * 原 {@code remove} 只从内层 List 移除实例，List 空了之后
 * 「UUID → 空 ArrayList」的条目仍留在 map 里。
 * 每只被元素打过的怪都会留下一条。<br>
 * 修复：内层 List 空时同步移除外层 key（见 {@link #dropIfEmpty}）。</p>
 *
 * <p>关于清理时机的说明：玩家跨维度同样会触发 {@link EntityLeaveLevelEvent}，
 * 此时清理是<b>更正确</b>的行为——跨维度会重建玩家实体，
 * {@code AttributeModifier} 本来就留在旧实体上已经失效，
 * 而旧的 map 记录会让 {@link #has} 误报 true、并阻止 {@link #apply} 重新施加修改器。
 * 清理后，战甲属性由 {@code WarframeEffectHandler}（每 5 tick）、
 * 武器属性由 {@code WeaponCombatHandler}（每 20 tick）自动重建，不会有可感知的丢失。</p>
 */
@Mod.EventBusSubscriber
public class DynamicAttributeManager {
    // 移除UUID池，改用确定性生成
    private static final Map<UUID, List<DynamicAttributeInstance>> ENTITY_ATTRIBUTES = new ConcurrentHashMap<>();

    /**
     * 应用动态属性到实体
     *
     * @param entity 目标实体
     * @param instance 属性实例
     */
    public static void apply(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.computeIfAbsent(entityId, k -> new ArrayList<>());

        // 检查是否已存在相同属性
        Optional<DynamicAttributeInstance> existing = instances.stream()
                .filter(i -> i.getAttribute().equals(instance.getAttribute()))
                .findFirst();

        if (existing.isPresent()) {
            DynamicAttributeInstance old = existing.get();
            if (instance.shouldOverride(old)) {
                // 新实例等级更高或时间更长,覆盖旧实例
                remove(entity, old);
                instances.remove(old);
            } else {
                // 旧实例更优,只刷新时间
                old.refresh(Math.max(old.getDuration(), instance.getDuration()));
                return;
            }
        }

        instances.add(instance);
        applyModifiers(entity, instance);

        // 注册事件处理器
        if (instance.getAttribute().hasEventHandler()) {
            Object handler = instance.getAttribute().createEventHandler(entity);
            instance.setEventHandler(handler);
        }
    }

    /**
     * 移除实体的指定动态属性
     *
     * @param entity 目标实体
     * @param attribute 要移除的属性
     */
    public static void remove(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);
        if (instances == null) return;

        instances.stream()
                .filter(i -> i.getAttribute().equals(attribute))
                .findFirst()
                .ifPresent(instance -> {
                    removeModifiers(entity, instance);
                    instance.unregisterEventHandler();
                    instances.remove(instance);
                });

        // ⭐ 泄漏修复：列表空了就把外层 key 一并删掉
        dropIfEmpty(entityId, instances);
    }

    /**
     * 移除动态属性实例(内部方法)
     *
     * @param entity 目标实体
     * @param instance 要移除的实例
     */
    private static void remove(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        removeModifiers(entity, instance);
        instance.unregisterEventHandler();
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);
        if (instances != null) {
            instances.remove(instance);
            // ⭐ 泄漏修复：列表空了就把外层 key 一并删掉
            dropIfEmpty(entityId, instances);
        }
    }

    /**
     * ⭐ 内层列表为空时移除外层 map 条目，避免「UUID → 空 List」无限累积
     *
     * @param entityId  实体 UUID
     * @param instances 该实体的属性实例列表
     */
    private static void dropIfEmpty(@Nonnull UUID entityId, @Nullable List<DynamicAttributeInstance> instances) {
        if (instances != null && instances.isEmpty()) {
            ENTITY_ATTRIBUTES.remove(entityId);
        }
    }

    /**
     * 检查实体是否拥有指定动态属性
     *
     * @param entity 目标实体
     * @param attribute 要检查的属性
     * @return true表示拥有
     */
    public static boolean has(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return false;
        return instances.stream().anyMatch(i -> i.getAttribute().equals(attribute));
    }

    /**
     * 获取实体指定动态属性的等级
     *
     * @param entity 目标实体
     * @param attribute 要查询的属性
     * @return 等级,如果不存在返回-1
     */
    public static int getAmplifier(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entity.getUUID());
        if (instances == null) return -1;

        return instances.stream()
                .filter(i -> i.getAttribute().equals(attribute))
                .findFirst()
                .map(DynamicAttributeInstance::getAmplifier)
                .orElse(-1);
    }

    /**
     * 获取实体身上的所有动态属性实例
     *
     * @param entity 目标实体
     * @return 动态属性实例列表,如果没有则返回null
     */
    @Nullable
    public static List<DynamicAttributeInstance> getInstances(@Nonnull LivingEntity entity) {
        return ENTITY_ATTRIBUTES.get(entity.getUUID());
    }

    /**
     * 清除实体的所有动态属性
     *
     * <p>会同时注销所有已注册到 Forge 事件总线的处理器，
     * 这是防止监听器泄漏的关键路径。</p>
     *
     * @param entity 目标实体
     */
    public static void clearAll(@Nonnull LivingEntity entity) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.remove(entityId);
        if (instances != null) {
            instances.forEach(instance -> {
                removeModifiers(entity, instance);
                instance.unregisterEventHandler();
            });
            instances.clear();
        }
    }

    /**
     * 应用属性修改器到实体
     *
     * @param entity 目标实体
     * @param instance 属性实例
     */
    private static void applyModifiers(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        DynamicAttribute attribute = instance.getAttribute();

        for (Map.Entry<Attribute, DynamicAttribute.ModifierConfig> entry : attribute.getModifierConfigs().entrySet()) {
            Attribute targetAttr = entry.getKey();
            DynamicAttribute.ModifierConfig config = entry.getValue();

            AttributeInstance attrInstance = entity.getAttribute(targetAttr);
            if (attrInstance == null) continue;

            UUID modifierId = getModifierUUID(attribute.getRegistryName(), targetAttr);

            // 移除旧修改器
            AttributeModifier oldModifier = attrInstance.getModifier(modifierId);
            if (oldModifier != null) {
                attrInstance.removeModifier(oldModifier);
            }

            // 计算最终值
            double finalValue = config.calculate(instance.getAmplifier());

            // 添加新修改器
            AttributeModifier newModifier = new AttributeModifier(
                    modifierId,
                    "DynamicAttribute:" + attribute.getRegistryName(),
                    finalValue,
                    config.operation
            );

            attrInstance.addPermanentModifier(newModifier);
        }
    }

    /**
     * 移除属性修改器
     *
     * @param entity 目标实体
     * @param instance 属性实例
     */
    private static void removeModifiers(@Nonnull LivingEntity entity, @Nonnull DynamicAttributeInstance instance) {
        DynamicAttribute attribute = instance.getAttribute();

        for (Attribute targetAttr : attribute.getModifierConfigs().keySet()) {
            AttributeInstance attrInstance = entity.getAttribute(targetAttr);
            if (attrInstance == null) continue;

            UUID modifierId = getModifierUUID(attribute.getRegistryName(), targetAttr);
            AttributeModifier modifier = attrInstance.getModifier(modifierId);

            if (modifier != null) {
                attrInstance.removeModifier(modifier);
            }
        }
    }

    /**
     * 使用确定性方法生成修改器UUID
     * 基于属性名和目标属性名生成固定的UUID，确保游戏重启后UUID保持一致
     *
     * @param attributeName 动态属性名
     * @param targetAttr 目标属性
     * @return 固定的UUID
     */
    private static UUID getModifierUUID(String attributeName, Attribute targetAttr) {
        // 使用命名空间作为前缀，确保唯一性
        String namespace = "kuvalich:dynamic_attribute:";
        String fullName = namespace + attributeName + ":" + targetAttr.getDescriptionId();

        // 使用UUID.nameUUIDFromBytes生成确定性UUID
        // 相同的输入永远生成相同的UUID
        return UUID.nameUUIDFromBytes(fullName.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 玩家Tick事件监听
     * 处理动态属性的时间流逝和Tick回调
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide()) {
            return;
        }

        processEntityTick(event.player);
    }

    /**
     * 所有生物实体Tick事件监听（包括Mob、动物等）
     * 处理非玩家实体的动态属性
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();

        // 跳过玩家（玩家由onPlayerTick处理）
        if (entity.level().isClientSide() || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }

        processEntityTick(entity);
    }

    /**
     * ⭐ 实体死亡时清理全部动态属性
     *
     * <p>这是修复监听器泄漏的<b>主路径</b>：怪物几乎总是在元素 debuff 到期前被打死，
     * 死后不再 tick，自然过期逻辑永远不会执行。
     * 使用 LOWEST 优先级，确保在其它模组的死亡处理（掉落、经验等）之后再清理属性，
     * 避免属性修改器被提前移除影响它们的判定。</p>
     *
     * @param event 生物死亡事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        clearAll(entity);
    }

    /**
     * ⭐ 实体离开世界时清理全部动态属性（兜底）
     *
     * <p>覆盖死亡事件之外的移除场景：区块卸载、指令 kill、维度传送、实体 discard 等。
     * 与 {@link #onLivingDeath} 双保险，{@link #clearAll} 幂等，重复调用无副作用。</p>
     *
     * @param event 实体离开世界事件
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            clearAll(living);
        }
    }

    /** 本系统写入实体属性的修饰符名称前缀，清理残留时按它识别 */
    private static final String MODIFIER_NAME_PREFIX = "DynamicAttribute:";

    /**
     * ⭐ 实体进入世界时清掉存档里残留的临时修饰符
     *
     * <p>问题背景：修饰符是用 {@code addPermanentModifier} 加的，会随实体一起写进存档
     * （这样玩家重新登录时最大生命值还在，血量不会被截断）。但效果的"还剩几秒"只记在内存里：
     * 玩家在效果期间下线、怪物在效果期间随区块卸载、服务器重启，存档里的修饰符就成了没人管的孤儿——
     * 临时加血 / 加速 / 减甲会永久留在实体身上，直到同一个效果再次触发并自然到期。</p>
     *
     * <p>实体刚进入世界时内存里一定没有它的记录，所以此时身上所有本系统的修饰符都是残留，直接清掉。
     * 战甲那类"每 5 tick 重新施加"的效果几 tick 后会自己补回来。</p>
     *
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        // 内存里已有记录说明不是"从存档加载"而是同一世界内的重新加入，不动
        if (ENTITY_ATTRIBUTES.containsKey(living.getUUID())) {
            return;
        }
        purgeStaleModifiers(living);
    }

    /**
     * 移除实体身上所有名称以 {@link #MODIFIER_NAME_PREFIX} 开头的修饰符
     *
     * @param entity 目标实体
     */
    private static void purgeStaleModifiers(@Nonnull LivingEntity entity) {
        int removed = 0;
        for (Attribute attr : ForgeRegistries.ATTRIBUTES.getValues()) {
            AttributeInstance attrInstance = entity.getAttribute(attr);
            if (attrInstance == null) {
                continue;
            }
            List<AttributeModifier> stale = null;
            for (AttributeModifier modifier : attrInstance.getModifiers()) {
                if (modifier.getName().startsWith(MODIFIER_NAME_PREFIX)) {
                    if (stale == null) {
                        stale = new ArrayList<>(2);
                    }
                    stale.add(modifier);
                }
            }
            if (stale != null) {
                for (AttributeModifier modifier : stale) {
                    attrInstance.removeModifier(modifier);
                }
                removed += stale.size();
            }
        }
        if (removed > 0) {
            LogUtil.debug("[动态属性] 实体 " + entity.getName().getString() + " 加载时清理了 " + removed + " 个残留修饰符");
        }
    }

    /**
     * 处理实体的Tick逻辑（提取公共方法）
     *
     * @param entity 要处理的实体
     */
    private static void processEntityTick(LivingEntity entity) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.get(entityId);

        if (instances == null || instances.isEmpty()) return;

        // 创建快照副本以避免 ConcurrentModificationException
        // 因为 onTick 回调可能会调用 apply/remove 修改原列表
        List<DynamicAttributeInstance> snapshot = new ArrayList<>(instances);
        List<DynamicAttributeInstance> expired = null;

        for (DynamicAttributeInstance instance : snapshot) {
            // 时间流逝
            if (instance.tick(1)) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(instance);
                continue;
            }

            // 检查是否应该触发Tick回调
            if (instance.shouldTriggerTick()) {
                DynamicAttribute.EffectCallback onTick = instance.getAttribute().getOnTickCallback();
                if (onTick != null) {
                    try {
                        EffectContext context = new EffectContext(
                                entity,
                                instance,
                                instance.calculateTotalTicks(),
                                instance.getTotalTicksTriggered()
                        );
                        onTick.accept(context);
                    } catch (Exception e) {
                        LogUtil.error("[动态属性] tick 回调抛出异常: " + instance.getAttribute().getRegistryName()
                                + "，本次跳过，效果继续计时", e);
                    }
                }
            }
        }

        // 移除过期实例
        if (expired != null) {
            for (DynamicAttributeInstance instance : expired) {
                remove(entity, instance);
            }
        }
    }

    /**
     * 实体移除时清理数据
     *
     * @param entity 被移除的实体
     */
    public static void onEntityRemove(@Nonnull LivingEntity entity) {
        clearAll(entity);
    }
}
