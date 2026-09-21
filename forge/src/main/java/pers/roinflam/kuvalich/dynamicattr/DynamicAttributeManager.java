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
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态属性管理器
 * 负责应用、移除、更新实体的动态属性
 *
 * <p>⭐ 内存泄漏修复（历史，两处，均会随服务器运行时长累积）：</p>
 *
 * <p><b>泄漏一：事件处理器永久驻留 Forge 事件总线（已从根上消除）。</b><br>
 * {@code MAGNETIC} / {@code RADIATION} / {@code PUNCTURE} 曾通过
 * {@code withEventHandler} 在施加 debuff 时<b>动态</b>往总线 register 一个匿名监听器，
 * 反注册只发生在「自然过期」「显式 remove」两条路径上。
 * 但实体死亡后不再 tick，{@link #processEntityTick} 不会执行；
 * 而 {@link #apply} 的旧 orphan 分支（见下）连反注册的机会都没有。
 * 累积后，<b>每一次伤害事件都要遍历这些僵尸监听器</b>，
 * 表现为 MSPT 随在线时长单调上升、重启后恢复。<br>
 * 修复：动态注册整体删除，元素战斗效果改由
 * {@code DynamicAttributes.ElementCombatHandler} 里固定数量的静态监听器承担。
 * apply/remove 退化成纯 map 操作，总线规模恒定。
 * {@link #onLivingDeath} / {@link #onEntityLeaveLevel} 保留，
 * 但职责只剩「清 map + 摘属性修改器」。</p>
 *
 * <p><b>泄漏一之二：{@link #apply} 的 orphan 分支（本次修复）。</b><br>
 * 同一 debuff 二次触发、且该实体身上<b>只有</b>这一个动态属性时，
 * 旧的 {@code remove(entity, old)} 会把内层列表清空并顺手把外层 key 也删掉
 * （{@link #dropIfEmpty}），随后 apply 把新实例 add 进了一个<b>已经脱离 map 的列表</b>。
 * 后果：监听器泄漏，且 {@link #getAmplifier} 一律返回 -1 ——
 * 磁力护盾增伤、辐射同类增伤、穿刺减伤<b>静默失效</b>，
 * 而客户端特效仍在转，玩家看到「特效在转但 debuff 不生效」。<br>
 * 修复：覆盖分支不再走会删 key 的 {@code remove}，只摘修改器 + 从列表里移除旧实例。</p>
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
    /**
     * 实体 UUID → 该实体身上的动态属性实例
     *
     * <p>⭐ 内层换成 {@link CopyOnWriteArrayList}：
     * 内层列表会在被 stream / for 遍历的过程中被改写——最典型的是
     * {@code FIRE} 的 onTick 回调里回调 {@link #apply} 给自己升一级，
     * 而外层正在 {@link #processEntityTick} 里遍历同一实体的列表。
     * 原来靠「每 tick 每实体 new 一个 ArrayList 快照」来回避 CME，
     * 换成 COW 之后遍历天然是快照语义，那个每 tick 的临时对象也一并省掉了
     * （30 只怪 = 每 tick 少 30 次分配）。列表长度常年个位数，写时拷贝的成本可以忽略。</p>
     */
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
        List<DynamicAttributeInstance> instances =
                ENTITY_ATTRIBUTES.computeIfAbsent(entityId, k -> new CopyOnWriteArrayList<>());

        // 检查是否已存在相同属性
        DynamicAttributeInstance old = findInstance(instances, instance.getAttribute());

        if (old != null) {
            if (instance.shouldOverride(old)) {
                // 新实例等级更高或时间更长,覆盖旧实例
                // ⭐ 这里绝不能调用 remove(entity, old)：那条路径在列表变空时会把外层 key 一起删掉
                //    （dropIfEmpty），于是下面的 instances.add 加进了一个脱离 map 的孤儿列表，
                //    导致 getAmplifier 恒为 -1、磁力/辐射/穿刺静默失效。只摘修改器、只从列表里移除。
                removeModifiers(entity, old);
                instances.remove(old);
            } else {
                // 旧实例更优,只刷新时间
                old.refresh(Math.max(old.getDuration(), instance.getDuration()));
                return;
            }
        }

        instances.add(instance);
        applyModifiers(entity, instance);
    }

    /**
     * 在实例列表里查找指定属性的实例
     *
     * <p>用普通 for 循环而非 stream：本方法处在伤害事件的热路径上
     * （{@code DynamicAttributes.ElementCombatHandler} 每次 LivingHurtEvent 都会查几次），
     * 列表长度常年个位数，stream 的管道对象分配纯属浪费。</p>
     *
     * @param instances 实例列表,可为 null
     * @param attribute 目标属性
     * @return 匹配的实例,没有则返回 null
     */
    @Nullable
    private static DynamicAttributeInstance findInstance(@Nullable List<DynamicAttributeInstance> instances,
                                                         @Nonnull DynamicAttribute attribute) {
        if (instances == null) return null;
        for (DynamicAttributeInstance instance : instances) {
            if (instance.getAttribute().equals(attribute)) {
                return instance;
            }
        }
        return null;
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

        DynamicAttributeInstance target = findInstance(instances, attribute);
        if (target != null) {
            removeModifiers(entity, target);
            instances.remove(target);
        }

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
        return findInstance(ENTITY_ATTRIBUTES.get(entity.getUUID()), attribute) != null;
    }

    /**
     * 获取实体指定动态属性的等级
     *
     * @param entity 目标实体
     * @param attribute 要查询的属性
     * @return 等级,如果不存在返回-1
     */
    public static int getAmplifier(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        return getAmplifier(ENTITY_ATTRIBUTES.get(entity.getUUID()), attribute);
    }

    /**
     * 在已取出的实例列表里查询等级
     *
     * <p>供伤害热路径使用：一次 {@link #getInstances} 之后连续查多个属性
     * （磁力 + 穿刺），避免重复的 map 查找。</p>
     *
     * @param instances 实例列表,可为 null
     * @param attribute 要查询的属性
     * @return 等级,如果不存在返回-1
     */
    public static int getAmplifier(@Nullable List<DynamicAttributeInstance> instances,
                                   @Nonnull DynamicAttribute attribute) {
        DynamicAttributeInstance instance = findInstance(instances, attribute);
        return instance == null ? -1 : instance.getAmplifier();
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
     * <p>摘掉所有属性修改器并把该实体的 map 条目整体删除。
     * 幂等，重复调用无副作用。</p>
     *
     * @param entity 目标实体
     */
    public static void clearAll(@Nonnull LivingEntity entity) {
        UUID entityId = entity.getUUID();
        List<DynamicAttributeInstance> instances = ENTITY_ATTRIBUTES.remove(entityId);
        if (instances != null) {
            for (DynamicAttributeInstance instance : instances) {
                removeModifiers(entity, instance);
            }
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
                    MODIFIER_NAME_PREFIX + attribute.getRegistryName(),
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
     * <p>怪物几乎总是在元素 debuff 到期前被打死，死后不再 tick，
     * 自然过期逻辑永远不会执行，map 条目会一直挂着。
     * 使用 LOWEST 优先级，确保在其它模组的死亡处理（掉落、经验等）之后再清理属性，
     * 避免属性修改器被提前移除影响它们的判定。</p>
     *
     * <p>注：监听器泄漏已由「静态监听器」方案从根上消除，本方法现在只负责 map 与修改器的清理。</p>
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
     * 移除实体身上所有本系统写入的残留修饰符
     *
     * <p>⭐ 性能修复：本方法对<b>每一个</b>加入世界的实体各跑一次（刷怪笼、区块加载时是密集调用）。
     * 原实现遍历 {@code ForgeRegistries.ATTRIBUTES} 的<b>全部</b>属性——整合包里轻松上百个，
     * 且对每个属性还要把 {@code getModifiers()} 整张表拉出来做字符串前缀比对。
     * 而本模组真正写过的属性不到 10 个，写过的 (属性, 修饰符 UUID) 组合也就几十个，
     * 且 UUID 是由「属性名 + 目标属性 descriptionId」<b>确定性</b>生成的（见 {@link #getModifierUUID}），
     * 可以直接 {@code getModifier(uuid)} 精确命中，既不用扫注册表也不用扫修饰符表。</p>
     *
     * @param entity 目标实体
     */
    private static void purgeStaleModifiers(@Nonnull LivingEntity entity) {
        int removed = 0;
        for (Map.Entry<Attribute, List<UUID>> entry : TrackedModifiers.INDEX.entrySet()) {
            AttributeInstance attrInstance = entity.getAttribute(entry.getKey());
            if (attrInstance == null) {
                continue;
            }
            for (UUID modifierId : entry.getValue()) {
                AttributeModifier modifier = attrInstance.getModifier(modifierId);
                if (modifier != null) {
                    attrInstance.removeModifier(modifier);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            LogUtil.debug("[动态属性] 实体 " + entity.getName().getString() + " 加载时清理了 " + removed + " 个残留修饰符");
        }
    }

    /**
     * ⭐ 本模组写过的「目标属性 → 该属性下所有可能的修饰符 UUID」索引（懒加载）
     *
     * <p>用 holder 类做懒加载而不是直接写成外层的 static final：
     * {@link DynamicAttributeManager} 带 {@code @Mod.EventBusSubscriber}，
     * 会在 mod 构造期就被类加载；而 {@code DynamicAttributes} 的常量里有
     * {@code ForgeMod.BLOCK_REACH.get()} 这类 RegistryObject 取值，
     * 那时候还没注册完，提前初始化会直接抛异常。
     * 放进 holder 后，索引在第一次实体入世界（此时注册表早已就绪）时才构建，构建一次后永久复用。</p>
     */
    private static final class TrackedModifiers {

        static final Map<Attribute, List<UUID>> INDEX = build();

        private static Map<Attribute, List<UUID>> build() {
            // ⭐ 必须"读一个静态字段"来强制 DynamicAttributes 完成静态初始化
            //（用 .class 字面量只会加载不会初始化），否则 DynamicAttribute.getAll()
            //  可能只拿到零星几个碰巧被别处引用过的定义，索引就是残缺的。
            DynamicAttributes.HEALTH.getRegistryName();
            Map<Attribute, List<UUID>> index = new IdentityHashMap<>();
            for (DynamicAttribute definition : DynamicAttribute.getAll()) {
                for (Attribute targetAttr : definition.getModifierConfigs().keySet()) {
                    index.computeIfAbsent(targetAttr, k -> new ArrayList<>(2))
                            .add(getModifierUUID(definition.getRegistryName(), targetAttr));
                }
            }
            return index;
        }

        private TrackedModifiers() {
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

        // ⭐ 直接遍历：内层已是 CopyOnWriteArrayList，迭代器本身就是拿到的那一刻的快照，
        //    onTick 回调里的 apply/remove 改的是新数组，不会影响本次遍历，也不会抛 CME。
        //    原来每 tick 每实体 new 一个 ArrayList 做快照，那份临时对象现在省掉了。
        List<DynamicAttributeInstance> expired = null;

        for (DynamicAttributeInstance instance : instances) {
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
