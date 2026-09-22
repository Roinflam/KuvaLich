package pers.roinflam.kuvalich.dynamicattribute;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态属性定义
 * 支持属性修改器和Tick回调
 *
 * <p>⭐ 本版移除了 {@code withEventHandler}（运行时往 Forge 事件总线动态注册监听器）。
 * 原因：每施加一次 debuff 就往总线上 register 一个匿名对象，反注册只发生在
 * 「自然过期 / 显式 remove」两条路径上，一旦走到别的路径（见 {@code DynamicAttributeManager.apply}
 * 里的旧 orphan 分支）监听器就永久泄漏，之后每一次 {@code LivingHurtEvent} 都要遍历这些僵尸监听器。
 * 现在元素 debuff 的战斗效果改由 {@code DynamicAttributes.ElementCombatHandler}
 * 里<b>固定数量</b>的静态监听器承担，语义完全等价，总线规模恒定。</p>
 */
public class DynamicAttribute {

    /**
     * ⭐ 所有已构造的动态属性定义（构造时自注册）。
     *
     * <p>用途：让 {@code DynamicAttributeManager} 能汇总出「本模组真正写过的 Attribute 集合」，
     * 从而不必在实体入世界时遍历整个属性注册表（那里动辄上百个属性，而本模组只碰不到 10 个）。</p>
     *
     * <p>本项目的所有实例都是 {@code DynamicAttributes} 里的 static final 常量，
     * 在该类初始化时一次性全部构造完成；若日后新增运行时构造的定义，
     * 需注意管理器侧的索引是首次使用时快照的。</p>
     */
    private static final List<DynamicAttribute> REGISTRY = new CopyOnWriteArrayList<>();

    private final String registryName;
    private final Map<Attribute, ModifierConfig> modifierConfigs = new HashMap<>();
    private EffectCallback onTickCallback;
    private int tickInterval = 20;

    /**
     * 构造动态属性
     *
     * @param registryName 注册名,用于唯一标识此属性
     * @throws IllegalArgumentException 如果注册名为空
     */
    public DynamicAttribute(@Nonnull String registryName) {
        if (registryName == null || registryName.trim().isEmpty()) {
            throw new IllegalArgumentException("注册名不能为空");
        }
        this.registryName = registryName;
        REGISTRY.add(this);
    }

    /**
     * 获取所有已构造的动态属性定义
     *
     * @return 只读视图（CopyOnWriteArrayList，遍历期间不会抛 ConcurrentModificationException）
     */
    @Nonnull
    public static List<DynamicAttribute> getAll() {
        return Collections.unmodifiableList(REGISTRY);
    }

    // ========== 属性修改器 ==========

    /**
     * 添加属性修改器(固定值)
     *
     * @param attribute 目标属性
     * @param baseValue 基础值,会根据amplifier放大: baseValue * (amplifier + 1)
     * @param operation 运算方式
     * @return this,支持链式调用
     */
    public DynamicAttribute addModifier(Attribute attribute, double baseValue, AttributeModifier.Operation operation) {
        modifierConfigs.put(attribute, new ModifierConfig(baseValue, operation, null));
        return this;
    }

    /**
     * 添加属性修改器(动态计算)
     *
     * @param attribute 目标属性
     * @param operation 运算方式
     * @param calculator 自定义计算器,根据amplifier计算最终值
     * @return this,支持链式调用
     */
    public DynamicAttribute addModifier(Attribute attribute, AttributeModifier.Operation operation,
                                        @Nonnull ValueCalculator calculator) {
        modifierConfigs.put(attribute, new ModifierConfig(0, operation, calculator));
        return this;
    }

    // ========== Tick回调 ==========

    /**
     * 设置Tick回调函数
     *
     * @param callback 回调函数,会按tickInterval间隔触发
     * @return this,支持链式调用
     */
    public DynamicAttribute onTick(@Nonnull EffectCallback callback) {
        this.onTickCallback = callback;
        return this;
    }

    /**
     * 设置Tick触发间隔
     *
     * @param ticks 间隔tick数,必须≥1
     * @return this,支持链式调用
     */
    public DynamicAttribute setTickInterval(int ticks) {
        this.tickInterval = Math.max(1, ticks);
        return this;
    }

    // ========== 实例创建 ==========

    /**
     * 创建动态属性实例
     *
     * @param duration 持续时间(tick)
     * @param amplifier 等级(0开始)
     * @return 新的实例
     */
    public DynamicAttributeInstance createInstance(int duration, int amplifier) {
        return new DynamicAttributeInstance(this, duration, amplifier);
    }

    /**
     * 创建0级动态属性实例
     *
     * @param duration 持续时间(tick)
     * @return 新的实例
     */
    public DynamicAttributeInstance createInstance(int duration) {
        return createInstance(duration, 0);
    }

    // ========== Getter方法 ==========

    public String getRegistryName() {
        return registryName;
    }

    public Map<Attribute, ModifierConfig> getModifierConfigs() {
        return modifierConfigs;
    }

    public int getTickInterval() {
        return tickInterval;
    }

    @Nullable
    public EffectCallback getOnTickCallback() {
        return onTickCallback;
    }

    // ========== 内部类和接口 ==========

    /**
     * 属性修改器配置
     */
    public static class ModifierConfig {
        public final double baseValue;
        public final AttributeModifier.Operation operation;
        public final ValueCalculator calculator;

        public ModifierConfig(double baseValue, AttributeModifier.Operation operation,
                              @Nullable ValueCalculator calculator) {
            this.baseValue = baseValue;
            this.operation = operation;
            this.calculator = calculator;
        }

        /**
         * 根据amplifier计算最终修改值
         *
         * @param amplifier 等级
         * @return 最终修改值
         */
        public double calculate(int amplifier) {
            if (calculator != null) {
                return calculator.calculate(amplifier);
            }
            return baseValue * (amplifier + 1);
        }
    }

    /**
     * 值计算器接口
     */
    @FunctionalInterface
    public interface ValueCalculator {
        /**
         * 根据amplifier计算修改值
         *
         * @param amplifier 等级
         * @return 修改值
         */
        double calculate(int amplifier);
    }

    /**
     * Tick回调接口
     */
    @FunctionalInterface
    public interface EffectCallback {
        /**
         * Tick触发时调用
         *
         * @param context 效果上下文
         */
        void accept(EffectContext context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicAttribute)) return false;
        return registryName.equals(((DynamicAttribute) o).registryName);
    }

    @Override
    public int hashCode() {
        return registryName.hashCode();
    }
}