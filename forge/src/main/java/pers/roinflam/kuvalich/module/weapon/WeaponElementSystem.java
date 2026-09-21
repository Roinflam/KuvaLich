package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.network.ElementSyncGuard;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.particle.ElementParticleEffects;
import pers.roinflam.kuvalich.render.particle.ParticleEmissionGuard;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 武器元素系统 · v9
 * 负责元素组合计算、元素效果触发、伤害位置和元素表情符号
 *
 * <p>⭐ v9 伤害数字改为「实际扣了多少就显示多少」：</p>
 * <ul>
 *   <li><b>走 hurt 的元素伤害</b>（火焰 DoT、无盾毒素、电击、毒气）：改用
 *       {@link DamageDisplayTracker#hurtWithDisplay}，数字取自最终伤害回调。
 *       免疫、受击无敌、护甲、女巫魔法抗性与其他模组减伤都会如实体现，被整个挡掉就不跳字；
 *       打到吸收护盾时，护盾吃掉的部分计入数字并追加护盾图标。</li>
 *   <li><b>直接改血量的元素伤害</b>（切割、带盾毒素）：扣血前后对比，显示实际掉血；
 *       目标因此死亡时按溢出规则显示完整预算值。这两种本来就绕过护盾，不带护盾图标。</li>
 *   <li><b>爆炸</b>：原版爆炸冲击与补充的元素伤害在同一处同步打完，
 *       用 {@link DamageDisplayTracker.HealthSnapshot} 前后对比，每个目标只跳一个合计数字；
 *       原先完全没有数字的原版冲击部分（6 格内）也一并计入。</li>
 *   <li>元素数字统一走附加通道限流（{@link DamagePacket.Channel#SECONDARY}），不挤占主伤害的额度。</li>
 * </ul>
 *
 * <p>⭐ v8 性能修复：新增 {@link ElementPool} 预计算元素池。</p>
 *
 * <p>问题：原 {@code triggerElementEffect} 每次触发第一行都调用
 * {@code getTriggerElement} → {@code getTriggerElements}，后者会完整重建元素池：
 * 遍历全部模组的全部属性 → 建 LinkedHashMap → 做复合元素合并 →
 * 用 {@code String.format("%.0f%%")} 把权重格式化成百分比字符串 →
 * 返回后调用方又 {@code Double.parseDouble(v.replace("%",""))} 解析回 double。<br>
 * 元素池在<b>一次攻击内是常量</b>，但单次伤害最多触发 20 次
 * （{@code MAX_ELEMENT_TRIGGER_PER_HIT}），SlashBlade 群体斩击命中 20 个目标
 * 就是 400 次完整重建 + 800 次字符串格式化/解析。</p>
 *
 * <p>修复：{@link #buildElementPool} 在 {@code processDamage} 里对每次攻击算<b>一次</b>，
 * 结果以 {@code String[] + double[]} 形式持有，{@link ElementPool#pick()} 只做一次
 * 加权随机采样，零分配、零字符串操作。Tooltip 侧的百分比展示由
 * {@link ElementPool#toPercentMap()} 提供，行为与原来完全一致。</p>
 *
 * <p>v7 修复保留：所有 {@code syncVisualDebuff} 调用走 {@link ElementSyncGuard#trySend} 节流。</p>
 * <p>v6.1 修复保留：所有 {@code spawn*Burst / spawn*Effect} 调用前经过
 * {@link ParticleEmissionGuard} 门控。</p>
 *
 * @author RoinFlam
 */
public class WeaponElementSystem {

    // ========== 赤毒武器元素伤害 / Kuva Weapon Element Damage ==========

    /**
     * 获取赤毒武器自带的元素伤害值
     *
     * @param weapon 赤毒武器
     * @return 元素伤害百分比（例如35级 = 0.35即35%）
     */
    public static double getKuvaWeaponElementDamage(ItemStack weapon) {
        if (!KuvaWeaponUtil.hasType(weapon)) {
            return 0.0;
        }
        int number = KuvaWeaponUtil.getNumber(weapon);
        return number / 100.0;
    }

    // ========== ⭐ 元素池 / Element Pool ==========

    /**
     * 预计算好的元素池（一次攻击内复用）
     * Pre-computed element pool, reused within a single attack
     *
     * <p>持有最终合并后的元素名数组与对应权重数组。
     * 构造完成后不可变，{@link #pick()} 只做一次线性加权采样，
     * 不产生任何对象分配和字符串操作。</p>
     */
    public static final class ElementPool {

        /** 空池单例，避免为无元素武器重复分配 */
        private static final ElementPool EMPTY = new ElementPool(new String[0], new double[0], 0.0);

        /** 元素名数组 / Element names */
        private final String[] elements;

        /** 对应权重数组（未归一化）/ Corresponding weights (not normalized) */
        private final double[] values;

        /** 权重总和 / Sum of weights */
        private final double total;

        private ElementPool(String[] elements, double[] values, double total) {
            this.elements = elements;
            this.values = values;
            this.total = total;
        }

        /**
         * 元素池是否为空（武器没有任何元素属性）
         *
         * @return 为空返回 true
         */
        public boolean isEmpty() {
            return elements.length == 0 || total <= 0;
        }

        /**
         * 按权重随机选取一个触发元素
         *
         * @return 被选中的元素名，池为空时返回 null
         */
        public String pick() {
            if (isEmpty()) {
                return null;
            }
            if (elements.length == 1) {
                return elements[0];
            }
            double random = Math.random() * total;
            double cumulative = 0.0;
            for (int i = 0; i < elements.length; i++) {
                cumulative += values[i];
                if (random <= cumulative) {
                    return elements[i];
                }
            }
            return elements[elements.length - 1];
        }

        /**
         * 转换为「元素名 → 百分比字符串」映射，供 Tooltip 展示
         *
         * @return 百分比映射，与旧版 {@code getTriggerElements} 输出格式完全一致
         */
        public HashMap<String, String> toPercentMap() {
            HashMap<String, String> result = new HashMap<>();
            if (isEmpty()) {
                return result;
            }
            for (int i = 0; i < elements.length; i++) {
                double percentage = (values[i] / total) * 100;
                result.put(elements[i], String.format("%.0f%%", percentage));
            }
            return result;
        }
    }

    /**
     * 构建武器的最终元素池（含复合元素合并）
     *
     * <p>合并规则与旧版 {@code getTriggerElements} 完全一致：
     * <ol>
     *   <li>汇总赤毒武器自带元素 + 所有模组的元素/物理/复合元素属性；</li>
     *   <li>剔除权重 &le; 0 的项；</li>
     *   <li>复合元素直接累加；基础元素尝试与已存在的基础元素两两合成复合元素。</li>
     * </ol></p>
     *
     * @param weapon  武器物品栈
     * @param modules 已解析的模组列表
     * @return 元素池，无任何元素时返回空池
     */
    public static ElementPool buildElementPool(ItemStack weapon, List<ItemStack> modules) {
        Map<String, Double> elementValues = new LinkedHashMap<>();

        if (KuvaWeaponUtil.hasType(weapon)) {
            String kuvaType = KuvaWeaponUtil.getType(weapon);
            double kuvaValue = getKuvaWeaponElementDamage(weapon);
            elementValues.put(kuvaType, kuvaValue);
        }

        if (modules != null) {
            for (ItemStack module : modules) {
                for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                    String key = entry.getKey();
                    double value = entry.getValue();
                    if (isElemental(key) || isPhysical(key) || isCompound(key)) {
                        elementValues.merge(key, value, Double::sum);
                    }
                }
            }
        }

        elementValues.entrySet().removeIf(entry -> entry.getValue() <= 0);
        if (elementValues.isEmpty()) {
            return ElementPool.EMPTY;
        }

        Map<String, Double> combinedElements = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : elementValues.entrySet()) {
            String currentElement = entry.getKey();
            double currentValue = entry.getValue();

            if (isCompound(currentElement)) {
                combinedElements.merge(currentElement, currentValue, Double::sum);
                continue;
            }

            boolean combined = false;
            for (String existingElement : new ArrayList<>(combinedElements.keySet())) {
                if (isCompound(existingElement)) continue;
                String compoundElement = getCompoundElement(existingElement, currentElement);
                if (compoundElement != null) {
                    double existingValue = combinedElements.remove(existingElement);
                    combinedElements.merge(compoundElement, existingValue + currentValue, Double::sum);
                    combined = true;
                    break;
                }
            }

            if (!combined) {
                combinedElements.put(currentElement, currentValue);
            }
        }

        if (combinedElements.isEmpty()) {
            return ElementPool.EMPTY;
        }

        int size = combinedElements.size();
        String[] names = new String[size];
        double[] weights = new double[size];
        double total = 0.0;
        int index = 0;
        for (Map.Entry<String, Double> entry : combinedElements.entrySet()) {
            names[index] = entry.getKey();
            weights[index] = entry.getValue();
            total += entry.getValue();
            index++;
        }

        return new ElementPool(names, weights, total);
    }

    // ========== 元素组合计算（Tooltip 兼容接口）/ Element Composition ==========

    /**
     * 获取武器的最终元素组合及各元素占比
     *
     * @param weapon  武器物品栈
     * @param modules 已解析的模组列表
     * @return 元素名→百分比字符串的映射
     */
    public static HashMap<String, String> getTriggerElements(ItemStack weapon, List<ItemStack> modules) {
        return buildElementPool(weapon, modules).toPercentMap();
    }

    /**
     * 获取武器的最终元素组合及各元素占比（兼容旧调用）
     *
     * @param weapon 武器物品栈
     * @return 元素名→百分比字符串的映射
     */
    public static HashMap<String, String> getTriggerElements(ItemStack weapon) {
        return getTriggerElements(weapon, WeaponModuleHandler.getModules(weapon));
    }

    /**
     * 根据元素占比概率随机选取一个触发元素
     *
     * @param weapon  武器物品栈
     * @param modules 已解析的模组列表
     * @return 被选中的元素名，无元素时返回null
     */
    public static String getTriggerElement(ItemStack weapon, List<ItemStack> modules) {
        return buildElementPool(weapon, modules).pick();
    }

    /**
     * 根据元素占比概率随机选取一个触发元素（兼容旧调用）
     *
     * @param weapon 武器物品栈
     * @return 被选中的元素名
     */
    public static String getTriggerElement(ItemStack weapon) {
        return getTriggerElement(weapon, WeaponModuleHandler.getModules(weapon));
    }

    // ========== 通用工具方法 / Utility ==========

    /**
     * 获取攻击者的通用攻击伤害源
     *
     * @param attacker 攻击者实体
     * @return 对应类型的伤害源
     */
    private static DamageSource getAttackDamageSource(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return player.damageSources().playerAttack(player);
        } else if (attacker instanceof Mob mob) {
            return mob.damageSources().mobAttack(mob);
        } else {
            return attacker.damageSources().generic();
        }
    }

    /** 元素伤害数字的颜色码（白字，元素图标自带颜色） */
    private static final String ELEMENT_COLOR = "\u00a7f";

    /** 爆炸元素的原版爆炸半径（与 level.explode 传入的值保持一致） */
    private static final float ELEMENT_EXPLOSION_RADIUS = 3.0F;

    /**
     * 元素伤害数字的接收者：仅攻击者本人是玩家时显示（与改动前一致，宠物、女仆的元素伤害不显示）
     *
     * @param attacker 攻击者，可为 null
     * @return 服务端玩家；不是玩家时返回 null
     */
    @Nullable
    private static ServerPlayer viewerOf(@Nullable LivingEntity attacker) {
        return attacker instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    /**
     * ⭐ 造成一次元素伤害并按实际扣除量显示数字
     *
     * <p>数字取自最终伤害回调：免疫、受击无敌、护甲、其他模组减伤都会如实体现；
     * 这一下被整个挡掉时不跳字；打到护盾时计入护盾部分并追加护盾图标。</p>
     *
     * @param victim   受击实体
     * @param source   伤害来源（直接传给 {@code hurt}）
     * @param amount   伤害值（直接传给 {@code hurt}）
     * @param attacker 攻击者（决定数字发给谁）
     * @param element  元素名（决定数字后缀图标，同时作为合并分组：只与同元素数字合并）
     * @return {@code hurt} 的返回值
     */
    private static boolean hurtWithElementDisplay(LivingEntity victim, DamageSource source, float amount,
                                                  @Nullable LivingEntity attacker, String element) {
        return DamageDisplayTracker.hurtWithDisplay(victim, source, amount, viewerOf(attacker),
                ELEMENT_COLOR, "", getElementEmoji(element), DamagePacket.Channel.SECONDARY, element);
    }

    /**
     * ⭐ 发送一条「直接改血量」类元素伤害的数字
     *
     * @param victim   受击实体
     * @param amount   应显示的数值（由 {@link DamageDisplayTracker#resolveDirectLoss} 算出）
     * @param attacker 攻击者（决定数字发给谁）
     * @param element  元素名（决定数字后缀图标，同时作为合并分组：只与同元素数字合并）
     */
    private static void sendElementDirect(LivingEntity victim, float amount,
                                          @Nullable LivingEntity attacker, String element) {
        DamageDisplayTracker.sendDirect(viewerOf(attacker), victim, amount,
                ELEMENT_COLOR, "", getElementEmoji(element), false, DamagePacket.Channel.SECONDARY, element);
    }

    /**
     * ⭐ 为爆炸元素拍一份血量快照
     *
     * <p>原版爆炸会对「中心 ±（半径 × 2 + 1）格」方盒内的生物结算伤害，这里取同样的方盒；
     * 排除攻击者本人（自伤不给攻击者跳字），并跳过已死亡的实体。</p>
     *
     * @param level    所在世界
     * @param center   爆炸中心实体（主目标）
     * @param attacker 攻击者
     * @return 快照；无需显示（攻击者不是玩家、跳字关闭、范围内无目标）时返回 null
     */
    @Nullable
    private static DamageDisplayTracker.HealthSnapshot captureBlastSnapshot(Level level, LivingEntity center,
                                                                           LivingEntity attacker) {
        if (viewerOf(attacker) == null || !ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return null;
        }
        double reach = ELEMENT_EXPLOSION_RADIUS * 2.0 + 1.0;
        AABB box = new AABB(center.getX() - reach, center.getY() - reach, center.getZ() - reach,
                center.getX() + reach, center.getY() + reach, center.getZ() + reach);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != attacker && e.isAlive() && !e.isDeadOrDying());
        return targets.isEmpty() ? null : DamageDisplayTracker.HealthSnapshot.capture(targets);
    }

    /**
     * 将视觉 debuff 同步到所有追踪该实体的客户端（受 ElementSyncGuard 节流）
     *
     * @param target        被附加 debuff 的目标
     * @param element       元素名
     * @param durationTicks 持续 tick 数
     * @param amplifier     元素等级
     */
    private static void syncVisualDebuff(LivingEntity target, String element, int durationTicks, int amplifier) {
        ElementSyncGuard.trySend(target, element, durationTicks, amplifier);
    }

    // ========== 元素类型判断 / Element Type Check ==========

    /** 是否为基础元素（火/冰/毒/电）*/
    static boolean isElemental(String element) {
        return element.equals("fire") || element.equals("ice") ||
                element.equals("poison") || element.equals("electricity");
    }

    /** 是否为物理元素（切割/穿刺/冲击）*/
    static boolean isPhysical(String element) {
        return element.equals("slash") || element.equals("puncture") || element.equals("impact");
    }

    /** 是否为复合元素 */
    static boolean isCompound(String element) {
        return element.equals("gas") || element.equals("radiation") ||
                element.equals("magnetic") || element.equals("corrosion") ||
                element.equals("explosion") || element.equals("virus");
    }

    /**
     * 获取两个基础元素的复合结果
     *
     * @param first  第一个元素
     * @param second 第二个元素
     * @return 复合元素名，无法组合时返回null
     */
    static String getCompoundElement(String first, String second) {
        if ((first.equals("fire") && second.equals("poison")) || (first.equals("poison") && second.equals("fire")))
            return "gas";
        if ((first.equals("fire") && second.equals("electricity")) || (first.equals("electricity") && second.equals("fire")))
            return "radiation";
        if ((first.equals("ice") && second.equals("electricity")) || (first.equals("electricity") && second.equals("ice")))
            return "magnetic";
        if ((first.equals("poison") && second.equals("electricity")) || (first.equals("electricity") && second.equals("poison")))
            return "corrosion";
        if ((first.equals("fire") && second.equals("ice")) || (first.equals("ice") && second.equals("fire")))
            return "explosion";
        if ((first.equals("poison") && second.equals("ice")) || (first.equals("ice") && second.equals("poison")))
            return "virus";
        return null;
    }

    // ========== 元素伤害值计算 / Element Damage Value ==========

    /**
     * 获取指定元素的总伤害值
     *
     * @param element    元素名
     * @param attributes 武器模组属性
     * @param weapon     武器物品栈
     * @return 总元素伤害值
     */
    static double getElementDamageValue(String element, HashMap<String, Double> attributes, ItemStack weapon) {
        double value = 0.0;

        if (KuvaWeaponUtil.hasType(weapon)) {
            String kuvaType = KuvaWeaponUtil.getType(weapon);
            if (kuvaType.equals(element)) {
                value += getKuvaWeaponElementDamage(weapon);
            }
            if (isCompound(element)) {
                switch (element) {
                    case "gas":
                        if (kuvaType.equals("fire") || kuvaType.equals("poison"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                    case "radiation":
                        if (kuvaType.equals("fire") || kuvaType.equals("electricity"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                    case "magnetic":
                        if (kuvaType.equals("ice") || kuvaType.equals("electricity"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                    case "corrosion":
                        if (kuvaType.equals("poison") || kuvaType.equals("electricity"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                    case "explosion":
                        if (kuvaType.equals("fire") || kuvaType.equals("ice"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                    case "virus":
                        if (kuvaType.equals("poison") || kuvaType.equals("ice"))
                            value += getKuvaWeaponElementDamage(weapon);
                        break;
                }
            }
        }

        if (isCompound(element)) {
            switch (element) {
                case "gas":
                    value += attributes.getOrDefault("fire", 0.0) + attributes.getOrDefault("poison", 0.0);
                    break;
                case "radiation":
                    value += attributes.getOrDefault("fire", 0.0) + attributes.getOrDefault("electricity", 0.0);
                    break;
                case "magnetic":
                    value += attributes.getOrDefault("ice", 0.0) + attributes.getOrDefault("electricity", 0.0);
                    break;
                case "corrosion":
                    value += attributes.getOrDefault("poison", 0.0) + attributes.getOrDefault("electricity", 0.0);
                    break;
                case "explosion":
                    value += attributes.getOrDefault("fire", 0.0) + attributes.getOrDefault("ice", 0.0);
                    break;
                case "virus":
                    value += attributes.getOrDefault("poison", 0.0) + attributes.getOrDefault("ice", 0.0);
                    break;
                default:
                    break;
            }
        } else {
            value += attributes.getOrDefault(element, 0.0);
        }

        return value;
    }

    // ========== 元素效果触发 / Element Effect Trigger ==========

    /**
     * 触发一次元素效果
     *
     * <p>⭐ v8：元素池由调用方预计算并传入，本方法内不再重建元素池。</p>
     *
     * <p>⭐ 本方法内的伤害公式<b>不能</b>再乘克制倍率。传进来的 {@code coreDamage} 就是
     * {@code WeaponCombatHandler} 里的 {@code physicalDamage}
     * （= 原始伤害 × baseDamage × baneMultiplier），克制已经包含在内。
     * 改造前六条 DOT 公式各自又乘了一次，克制按<b>平方</b>生效 ——
     * 堆满四张克制卡时 DOT 吃 5.76 倍而不是设计的 2.40 倍。
     * {@code baneMultiplier} 参数也一并移除了，免得日后有人看见它又乘回去。</p>
     *
     * @param damageSource   伤害来源
     * @param hurter         受害者
     * @param attacker       攻击者
     * @param itemStack      武器物品栈
     * @param elementPool    预计算好的元素池
     * @param triggerTime    触发时间倍率
     * @param coreDamage     核心物理伤害（<b>已含克制倍率</b>，见下）
     * @param attributes     武器运行时属性
     * @return 被触发的元素名（仅用于伤害数字后缀，无后缀时返回 null）
     */
    static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                       LivingEntity attacker, ItemStack itemStack,
                                       ElementPool elementPool,
                                       double triggerTime, double coreDamage,
                                       HashMap<String, Double> attributes) {
        if (elementPool == null) {
            return null;
        }
        String type = elementPool.pick();
        if (type == null) return null;

        Level level = hurter.level();
        double elementValue = getElementDamageValue(type, attributes, itemStack);

        switch (type) {
            case "fire": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 1.5;
                else if (hurter.getMobType().equals(MobType.UNDEAD)) typeDamageMultiplier = 0.5;

                int duration = (int) (120 * triggerTime);
                int fireLevel;
                if (!DynamicAttributeManager.has(hurter, DynamicAttributes.FIRE)) {
                    fireLevel = 0;
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.FIRE.createInstance(duration, 0));
                } else {
                    fireLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.FIRE);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.FIRE.createInstance(duration, fireLevel));
                }
                syncVisualDebuff(hurter, "fire", duration, fireLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnFireBurst(hurter, sl);
                }

                hurter.setSecondsOnFire(6);
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementFireDamageMultiplier.get());
                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    // ⭐ 走去重注册表：同一只怪身上每种元素永远只有一条 DOT 在跑，
                    //    重复触发取 max 并刷新轮数，而不是再起一个独立任务。
                    WeaponCombatHandler.ElementDotRegistry.apply(hurter, "fire", dotDamage,
                            (int) Math.ceil(6 * triggerTime), (t, d) -> {
                                t.setSecondsOnFire(6);
                                // ⭐ 按实际扣除量显示：免疫火焰、受击无敌、其他模组减伤都会如实体现
                                hurtWithElementDisplay(t, t.damageSources().inFire(), d, dotAttacker, "fire");
                                return true;
                            });
                }
                return null;
            }
            case "poison": {
                int duration = (int) (120 * triggerTime);
                DynamicAttributeManager.apply(hurter, DynamicAttributes.POISON.createInstance(duration, 0));
                syncVisualDebuff(hurter, "poison", duration, 0);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnPoisonBurst(hurter, sl);
                }

                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ILLAGER)) typeDamageMultiplier = 1.5;
                else if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementPoisonDamageMultiplier.get());
                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    final DamageSource attackSource = getAttackDamageSource(attacker);
                    // ⭐ 走去重注册表（带盾时毒素绕过无敌帧，重复 DOT 是真·线性叠加，
                    //    一次命中触发六次就是六份同时扣血 —— 这是平衡问题，比性能更要紧）
                    WeaponCombatHandler.ElementDotRegistry.apply(hurter, "poison", dotDamage,
                            (int) Math.ceil(6 * triggerTime), (t, d) -> {
                                if (t.getAbsorptionAmount() > 0) {
                                    // ⭐ 有护盾：毒素绕过护盾直接扣血，显示「扣之前 − 扣之后」的实际掉血
                                    float healthBefore = t.getHealth();
                                    boolean lethal = !(healthBefore - d > 0.01f);
                                    if (lethal) { EntityLivingUtil.kill(t, attackSource); }
                                    else { EntityLivingUtil.damageHealthDirectly(t, d); }
                                    sendElementDirect(t, DamageDisplayTracker.resolveDirectLoss(t, healthBefore, d),
                                            dotAttacker, "poison");
                                    return !lethal;
                                }
                                // ⭐ 无护盾：走魔法伤害，按实际扣除量显示（女巫等魔法抗性会如实体现）
                                hurtWithElementDisplay(t, dotAttacker.damageSources().magic(), d, dotAttacker, "poison");
                                return true;
                            });
                }
                return null;
            }
            case "ice": {
                int duration = (int) (120 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.ICE)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.ICE);
                    newLevel = Math.min(8, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.ICE.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "ice", duration, newLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnIceBurst(hurter, sl);
                }
                return "ice";
            }
            case "electricity": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.UNDEAD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                float lightningDamage = (float) (0.5 * coreDamage * elementValue * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementElectricityDamageMultiplier.get());
                if (lightningDamage > 0) {
                    net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) { lightning.moveTo(hurter.getX(), hurter.getY(), hurter.getZ()); lightning.setVisualOnly(true); level.addFreshEntity(lightning); }
                    // ⭐ 按实际扣除量显示。注意这一下嵌套在主目标自己的受击事件里，
                    //    原版此时已给主目标上了受击无敌，电击伤害常被部分或全部吃掉，数字会如实变小或不显示
                    hurtWithElementDisplay(hurter, level.damageSources().lightningBolt(), lightningDamage, attacker, "electricity");
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.ELECTRICITY_PARALYSIS.createInstance((int) (10 * triggerTime), 0));
                }
                return null;
            }
            case "slash": {
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnSlashBurst(hurter, sl);
                }

                float dotDamage = (float) (0.35 * coreDamage
                        * ModConfig.KUVA_LICH.elementSlashDamageMultiplier.get());
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.VIRUS)) {
                    int virusLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.VIRUS);
                    double virusMultiplier = 1.0 + (1.0 + Math.min(virusLevel, 9) * 0.25);
                    dotDamage *= (float) virusMultiplier;
                }
                if (dotDamage > 0) {
                    float finalDotDamage = dotDamage;
                    final LivingEntity dotAttacker = attacker;
                    final DamageSource attackSource = getAttackDamageSource(attacker);
                    // ⭐ 走去重注册表：切割走 damageHealthDirectly 绕过无敌帧，
                    //    不去重的话一次命中触发六次就是六份同时扣血，直接秒杀。
                    WeaponCombatHandler.ElementDotRegistry.apply(hurter, "slash", finalDotDamage,
                            (int) Math.ceil(6 * triggerTime), (t, d) -> {
                                if (t.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, t)) {
                                    ElementParticleEffects.spawnSlashEffect(t, sl);
                                }

                                // ⭐ 切割直接扣血：先扣、再显示「扣之前 − 扣之后」的实际掉血
                                float healthBefore = t.getHealth();
                                boolean lethal = !(healthBefore - d > 0.01f);
                                if (lethal) { EntityLivingUtil.kill(t, attackSource); }
                                else { EntityLivingUtil.damageHealthDirectly(t, d); }
                                sendElementDirect(t, DamageDisplayTracker.resolveDirectLoss(t, healthBefore, d),
                                        dotAttacker, "slash");
                                return !lethal;
                            });
                }
                return null;
            }
            case "puncture": {
                int duration = (int) (120 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.PUNCTURE)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.PUNCTURE);
                    newLevel = Math.min(3, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.PUNCTURE.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "puncture", duration, newLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnPunctureBurst(hurter, sl);
                }
                return "puncture";
            }
            case "impact": {
                double impactValue = attributes.getOrDefault("impact", 0.0);
                if (KuvaWeaponUtil.hasType(itemStack) && KuvaWeaponUtil.getType(itemStack).equals("impact")) { impactValue += getKuvaWeaponElementDamage(itemStack); }
                float knockbackStrength = (float) (impactValue * 3.0);
                if (knockbackStrength > 0) {
                    double dx = hurter.getX() - attacker.getX();
                    double dz = hurter.getZ() - attacker.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 0) {
                        dx = dx / distance; dz = dz / distance;
                        hurter.knockback(knockbackStrength, -dx, -dz);
                        hurter.setDeltaMovement(hurter.getDeltaMovement().add(0, 0.2 * knockbackStrength, 0));
                    }
                }
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnImpactShockwave(sl, hurter);
                }
                return "impact";
            }
            case "magnetic": {
                int duration = (int) (120 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.MAGNETIC)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.MAGNETIC);
                    newLevel = Math.min(9, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.MAGNETIC.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "magnetic", duration, newLevel);
                return "magnetic";
            }
            case "radiation": {
                int duration = (int) (240 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.RADIATION)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.RADIATION);
                    newLevel = Math.min(9, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.RADIATION.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "radiation", duration, newLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnRadiationBurst(hurter, sl);
                }
                return "radiation";
            }
            case "virus": {
                int duration = (int) (120 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.VIRUS)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.VIRUS);
                    newLevel = Math.min(9, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.VIRUS.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "virus", duration, newLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnVirusBurst(hurter, sl);
                }
                return "virus";
            }
            case "corrosion": {
                int duration = (int) (160 * triggerTime);
                int newLevel;
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.CORROSION)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.CORROSION);
                    newLevel = Math.min(9, currentLevel + 1);
                } else {
                    newLevel = 0;
                }
                DynamicAttributeManager.apply(hurter, DynamicAttributes.CORROSION.createInstance(duration, newLevel));
                syncVisualDebuff(hurter, "corrosion", duration, newLevel);
                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnCorrosionBurst(hurter, sl);
                }
                return "corrosion";
            }
            case "explosion": {
                double armorMultiplier = hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                float explosionDamage = (float) (0.5 * coreDamage * elementValue * armorMultiplier
                        * ModConfig.KUVA_LICH.elementExplosionDamageMultiplier.get());
                if (explosionDamage > 0) {
                    // ⭐ 爆炸元素的实际伤害 = level.explode 的原版冲击 + 下面补的元素伤害（主目标 + 3 格范围），
                    //    两段都在这里同步打完：先给冲击范围内的生物拍快照，打完再逐个算「扣之前 − 扣之后」，
                    //    每个目标只跳一个合计数字；受击无敌、护甲、其他模组减伤、护盾都会如实体现
                    DamageDisplayTracker.HealthSnapshot blastSnapshot = captureBlastSnapshot(level, hurter, attacker);

                    // ⭐ 以前这里是 level.explode(null, ..., NONE)：虽然不炸方块，但原版爆炸本身会对范围内
                    //    所有实体（包括近战时站在旁边的攻击者自己）造成威力 3（苦力怕级）的伤害和击退，
                    //    目标还会被"原版爆炸 + 下面的自定义伤害"打两次，且原版那份不受伤害倍率配置控制。
                    //    现改为只播放爆炸特效和音效，伤害全部走下面排除了攻击者的自定义计算。
                    if (level instanceof ServerLevel blastLevel) {
                        blastLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                                hurter.getX(), hurter.getY() + hurter.getBbHeight() * 0.5, hurter.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                        blastLevel.playSound(null, hurter.getX(), hurter.getY(), hurter.getZ(),
                                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE,
                                4.0F, (1.0F + (blastLevel.random.nextFloat() - blastLevel.random.nextFloat()) * 0.2F) * 0.7F);
                    }
                    hurter.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, 3, e -> !e.equals(hurter) && !e.equals(attacker));
                    for (LivingEntity entity : entities) {
                        entity.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                    }

                    if (blastSnapshot != null) {
                        // 合并分组传 "explosion"：爆炸数字只与同一只怪身上的其他爆炸数字合并
                        blastSnapshot.sendDeltas(viewerOf(attacker), explosionDamage, ELEMENT_COLOR, "",
                                getElementEmoji("explosion"), DamagePacket.Channel.SECONDARY, "explosion");
                    }
                }
                return null;
            }
            case "gas": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementGasDamageMultiplier.get());
                final Vec3 gasCenter = new Vec3(hurter.getX(), hurter.getY(), hurter.getZ());
                final double gasRadius = 3.0;

                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, hurter)) {
                    ElementParticleEffects.spawnGasBurst(sl, gasCenter, gasRadius);
                }

                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    // ⭐ 走去重注册表：毒气每轮都要做一次 AABB 实体扫描，
                    //    是刷怪场里唯一能拉出可测 tick 时间的一条路径，去重收益最大。
                    //    ⭐ stopOnDeath = false：毒气是以命中位置为中心的 AoE 云，
                    //       锚点怪死了云还要继续（改造前这条任务的判定也只有 isRemoved()）。
                    WeaponCombatHandler.ElementDotRegistry.apply(hurter, "gas", dotDamage,
                            (int) Math.ceil(6 * triggerTime), (t, d) -> {
                                if (level instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquireGlobal(sl)) {
                                    ElementParticleEffects.spawnGasCloudEffect(sl, gasCenter, gasRadius);
                                }

                                List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                                        new AABB(gasCenter.x - gasRadius, gasCenter.y - gasRadius, gasCenter.z - gasRadius,
                                                gasCenter.x + gasRadius, gasCenter.y + gasRadius, gasCenter.z + gasRadius),
                                        e -> !e.equals(dotAttacker) && e.distanceToSqr(gasCenter) <= gasRadius * gasRadius);
                                for (LivingEntity entity : entities) {
                                    if (entity.isDeadOrDying()) continue;
                                    // ⭐ 按实际扣除量显示：处于受击无敌、免疫魔法的目标不再凭空跳字
                                    hurtWithElementDisplay(entity, dotAttacker.damageSources().magic(), d, dotAttacker, "gas");
                                }
                                return true;
                            }, false);
                }
                return null;
            }
            default:
                return null;
        }
    }

    /**
     * 触发一次元素效果（兼容旧调用，内部即时构建元素池）
     *
     * @deprecated 优先使用带 {@link ElementPool} 参数的重载版本，避免重复构建元素池
     */
    @Deprecated
    static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                       LivingEntity attacker, ItemStack itemStack,
                                       List<ItemStack> modules,
                                       double triggerTime, double coreDamage,
                                       HashMap<String, Double> attributes) {
        return triggerElementEffect(damageSource, hurter, attacker, itemStack,
                buildElementPool(itemStack, modules),
                triggerTime, coreDamage, attributes);
    }

    /**
     * 触发一次元素效果（兼容旧调用）
     *
     * @deprecated 优先使用带 {@link ElementPool} 参数的重载版本
     */
    @Deprecated
    static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                       LivingEntity attacker, ItemStack itemStack,
                                       double triggerTime, double coreDamage,
                                       HashMap<String, Double> attributes) {
        return triggerElementEffect(damageSource, hurter, attacker, itemStack,
                buildElementPool(itemStack, WeaponModuleHandler.getModules(itemStack)),
                triggerTime, coreDamage, attributes);
    }

    // ========== 工具方法 / Utility ==========

    /**
     * 在实体附近生成随机偏移的伤害数字显示位置
     *
     * <p>⭐ 已统一委托给 {@link DamageDisplayTracker#randomDisplayPosition}（高度改为身体上半截，
     * 配合客户端改为以相机为原点渲染）。保留本方法仅为兼容，新代码请直接调用委托目标。</p>
     *
     * @param entity 受击实体
     * @return 随机偏移后的位置向量
     */
    static Vec3 getRandomDamagePosition(LivingEntity entity) {
        return DamageDisplayTracker.randomDisplayPosition(entity);
    }

    /**
     * 获取元素对应的表情符号（用于伤害数字显示）
     *
     * @param element 元素名
     * @return 带颜色代码的表情符号字符串
     */
    static String getElementEmoji(String element) {
        switch (element) {
            case "fire": return "\u00a7c\ud83d\udd25";
            case "ice": return "\u00a73\u2744";
            case "poison": return "\u00a72\u2620";
            case "electricity": return "\u00a71\u26a1";
            case "slash": return "\u00a77\u263e";
            case "puncture": return "\u00a7f\u2020";
            case "impact": return "\u00a7f\ud83d\udd28";
            case "gas": return "\u00a7b\uD83D\uDCA8";
            case "radiation": return "\u00a7e\u2622";
            case "magnetic": return "\u00a7b\ud83e\uddf2";
            case "corrosion": return "\u00a72\ud83e\uddea";
            case "explosion": return "\u00a74\ud83d\udca5";
            case "virus": return "\u00a7d\ud83e\udda0";
            default: return "";
        }
    }
}
