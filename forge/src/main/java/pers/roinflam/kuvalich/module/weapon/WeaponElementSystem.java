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
import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.network.ElementEffectNetwork;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.ElementDebuffPacket;
import pers.roinflam.kuvalich.render.particle.ElementParticleEffects;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import java.util.*;

/**
 * 武器元素系统 · v6
 * 负责元素组合计算、元素效果触发、伤害位置和元素表情符号
 *
 * ⭐ v6 变更：
 *  - 病毒 emoji 颜色码：§a 绿 → §d 亮紫粉（LIGHT_PURPLE）
 *  - 毒气 emoji 颜色码：§a 绿 → §b 青色（AQUA）
 *  - 磁力 case 移除 ElementParticleEffects.spawnMagneticBurst 调用
 *    （磁力视觉改由 ElementGeometryRenderer 的纯几何双环线条承担，无粒子）
 *
 * Weapon Element System (v6)
 * Handles element composition, effect triggering, damage position and emoji display.
 */
public class WeaponElementSystem {

    // ========== 赤毒武器元素伤害 / Kuva Weapon Element Damage ==========

    /**
     * 获取赤毒武器自带的元素伤害值
     *
     * @param weapon 赤毒武器
     * @return 元素伤害百分比（例如35级 = 0.35即35%）
     */
    static double getKuvaWeaponElementDamage(ItemStack weapon) {
        if (!KuvaWeaponUtil.hasType(weapon)) {
            return 0.0;
        }
        int number = KuvaWeaponUtil.getNumber(weapon);
        return number / 100.0;
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

    /**
     * 如果攻击者是玩家，发送伤害数字到客户端
     *
     * @param attacker    攻击者
     * @param displayText 显示文本
     * @param position    显示位置
     */
    private static void sendDamageDisplayIfPlayer(LivingEntity attacker, String displayText, Vec3 position) {
        if (attacker instanceof ServerPlayer serverPlayer) {
            DamagePacket.sendToPlayer(serverPlayer, displayText, position);
        }
    }

    /**
     * 将视觉 debuff 立即同步到所有追踪该实体的客户端
     *
     * @param target        被附加 debuff 的目标
     * @param element       元素名
     * @param durationTicks 持续 tick 数
     * @param amplifier     元素等级
     */
    private static void syncVisualDebuff(LivingEntity target, String element, int durationTicks, int amplifier) {
        ElementEffectNetwork.sendToTrackers(target,
                new ElementDebuffPacket(target.getId(), element, durationTicks, amplifier));
    }

    // ========== 元素组合计算 / Element Composition ==========

    /**
     * 获取武器的最终元素组合及各元素占比
     *
     * @param weapon  武器物品栈
     * @param modules 已解析的模组列表
     * @return 元素名→百分比字符串的映射
     */
    public static HashMap<String, String> getTriggerElements(ItemStack weapon, List<ItemStack> modules) {
        Map<String, Double> elementValues = new LinkedHashMap<>();

        if (KuvaWeaponUtil.hasType(weapon)) {
            String kuvaType = KuvaWeaponUtil.getType(weapon);
            double kuvaValue = getKuvaWeaponElementDamage(weapon);
            elementValues.put(kuvaType, kuvaValue);
        }

        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                String key = entry.getKey();
                double value = entry.getValue();
                if (isElemental(key) || isPhysical(key) || isCompound(key)) {
                    elementValues.merge(key, value, Double::sum);
                }
            }
        }

        elementValues.entrySet().removeIf(entry -> entry.getValue() <= 0);

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

        double totalValue = combinedElements.values().stream().mapToDouble(Double::doubleValue).sum();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : combinedElements.entrySet()) {
            double percentage = (entry.getValue() / totalValue) * 100;
            result.put(entry.getKey(), String.format("%.0f%%", percentage));
        }

        return result;
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
        HashMap<String, String> elements = getTriggerElements(weapon, modules);
        if (elements.isEmpty()) return null;

        List<Map.Entry<String, Double>> elementList = new ArrayList<>();
        for (Map.Entry<String, String> entry : elements.entrySet()) {
            double probability = Double.parseDouble(entry.getValue().replace("%", "")) / 100.0;
            elementList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), probability));
        }

        double random = Math.random();
        double cumulativeProbability = 0.0;
        for (Map.Entry<String, Double> entry : elementList) {
            cumulativeProbability += entry.getValue();
            if (random <= cumulativeProbability) return entry.getKey();
        }

        return elementList.get(elementList.size() - 1).getKey();
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
     * @param damageSource   伤害来源
     * @param hurter         受害者
     * @param attacker       攻击者
     * @param itemStack      武器物品栈
     * @param modules        已解析的模组列表
     * @param triggerTime    触发时间倍率
     * @param coreDamage     核心物理伤害
     * @param attributes     武器运行时属性
     * @param baneMultiplier 克制倍率
     * @return 被触发的元素名
     */
    static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                       LivingEntity attacker, ItemStack itemStack,
                                       List<ItemStack> modules,
                                       double triggerTime, double coreDamage,
                                       HashMap<String, Double> attributes,
                                       double baneMultiplier) {
        String type = getTriggerElement(itemStack, modules);
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
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnFireBurst(hurter, sl);
                }

                hurter.setSecondsOnFire(6);
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementFireDamageMultiplier.get());
                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }
                            hurter.setSecondsOnFire(6);
                            hurter.hurt(hurter.damageSources().inFire(), dotDamage);
                            String displayText = "\u00a7f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("fire");
                            sendDamageDisplayIfPlayer(dotAttacker, displayText, getRandomDamagePosition(hurter));
                        }
                    }.start();
                }
                return null;
            }
            case "poison": {
                int duration = (int) (120 * triggerTime);
                DynamicAttributeManager.apply(hurter, DynamicAttributes.POISON.createInstance(duration, 0));
                syncVisualDebuff(hurter, "poison", duration, 0);
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnPoisonBurst(hurter, sl);
                }

                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ILLAGER)) typeDamageMultiplier = 1.5;
                else if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementPoisonDamageMultiplier.get());
                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    final DamageSource attackSource = getAttackDamageSource(attacker);
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }

                            boolean hasShield = hurter.getAbsorptionAmount() > 0;
                            String displayText = "\u00a7f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("poison");
                            sendDamageDisplayIfPlayer(dotAttacker, displayText, getRandomDamagePosition(hurter));
                            if (hasShield) {
                                if (hurter.getHealth() - dotDamage > 0.01f) { EntityLivingUtil.damageHealthDirectly(hurter, dotDamage); }
                                else { EntityLivingUtil.kill(hurter, attackSource); this.cancel(); }
                            } else { hurter.hurt(dotAttacker.damageSources().magic(), dotDamage); }
                        }
                    }.start();
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
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnIceBurst(hurter, sl);
                }
                return "ice";
            }
            case "electricity": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.UNDEAD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                float lightningDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementElectricityDamageMultiplier.get());
                if (lightningDamage > 0) {
                    net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) { lightning.moveTo(hurter.getX(), hurter.getY(), hurter.getZ()); lightning.setVisualOnly(true); level.addFreshEntity(lightning); }
                    hurter.hurt(level.damageSources().lightningBolt(), lightningDamage);
                    String displayText = "\u00a7f" + DamagePacket.formatDamage(lightningDamage) + getElementEmoji("electricity");
                    sendDamageDisplayIfPlayer(attacker, displayText, getRandomDamagePosition(hurter));
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.ELECTRICITY_PARALYSIS.createInstance((int) (10 * triggerTime), 0));
                }
                return null;
            }
            case "slash": {
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnSlashBurst(hurter, sl);
                }

                float dotDamage = (float) (0.35 * coreDamage * baneMultiplier
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
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }

                            if (hurter.level() instanceof ServerLevel sl) {
                                ElementParticleEffects.spawnSlashEffect(hurter, sl);
                            }

                            String displayText = "\u00a7f" + DamagePacket.formatDamage(finalDotDamage) + getElementEmoji("slash");
                            sendDamageDisplayIfPlayer(dotAttacker, displayText, getRandomDamagePosition(hurter));
                            if (hurter.getHealth() - finalDotDamage > 0.01f) { EntityLivingUtil.damageHealthDirectly(hurter, finalDotDamage); }
                            else { EntityLivingUtil.kill(hurter, attackSource); this.cancel(); }
                        }
                    }.start();
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
                if (level instanceof ServerLevel sl) {
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
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnImpactShockwave(sl, hurter);
                }
                return "impact";
            }
            case "magnetic": {
                // ⭐ v6：磁力粒子已全部移除，视觉由 ElementGeometryRenderer 的几何双环承担
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
                // ⭐ v6：spawnMagneticBurst 调用已移除
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
                if (level instanceof ServerLevel sl) {
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
                if (level instanceof ServerLevel sl) {
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
                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnCorrosionBurst(hurter, sl);
                }
                return "corrosion";
            }
            case "explosion": {
                double armorMultiplier = hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                float explosionDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * armorMultiplier
                        * ModConfig.KUVA_LICH.elementExplosionDamageMultiplier.get());
                if (explosionDamage > 0) {
                    level.explode(null, hurter.getX(), hurter.getY(), hurter.getZ(), 3.0F, Level.ExplosionInteraction.NONE);
                    hurter.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                    String displayText = "\u00a7f" + DamagePacket.formatDamage(explosionDamage) + getElementEmoji("explosion");
                    sendDamageDisplayIfPlayer(attacker, displayText, getRandomDamagePosition(hurter));
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, 3, e -> !e.equals(hurter) && !e.equals(attacker));
                    for (LivingEntity entity : entities) {
                        entity.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                        String aoeDisplayText = "\u00a7f" + DamagePacket.formatDamage(explosionDamage) + getElementEmoji("explosion");
                        sendDamageDisplayIfPlayer(attacker, aoeDisplayText, getRandomDamagePosition(entity));
                    }
                }
                return null;
            }
            case "gas": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier
                        * ModConfig.KUVA_LICH.elementGasDamageMultiplier.get());
                final Vec3 gasCenter = new Vec3(hurter.getX(), hurter.getY(), hurter.getZ());
                final double gasRadius = 3.0;

                if (level instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnGasBurst(sl, gasCenter, gasRadius);
                }

                if (dotDamage > 0) {
                    final LivingEntity dotAttacker = attacker;
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;

                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime) { this.cancel(); return; }

                            if (level instanceof ServerLevel sl) {
                                ElementParticleEffects.spawnGasCloudEffect(sl, gasCenter, gasRadius);
                            }

                            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(gasCenter.x - gasRadius, gasCenter.y - gasRadius, gasCenter.z - gasRadius,
                                            gasCenter.x + gasRadius, gasCenter.y + gasRadius, gasCenter.z + gasRadius),
                                    e -> !e.equals(dotAttacker) && e.distanceToSqr(gasCenter) <= gasRadius * gasRadius);
                            for (LivingEntity entity : entities) {
                                if (entity.isDeadOrDying()) continue;
                                entity.hurt(dotAttacker.damageSources().magic(), dotDamage);
                                String displayText = "\u00a7f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("gas");
                                sendDamageDisplayIfPlayer(dotAttacker, displayText, getRandomDamagePosition(entity));
                            }
                        }
                    }.start();
                }
                return null;
            }
            default:
                return null;
        }
    }

    /**
     * 触发一次元素效果（兼容旧调用）
     *
     * @deprecated 优先使用带 modules 参数的重载版本
     */
    @Deprecated
    static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                       LivingEntity attacker, ItemStack itemStack,
                                       double triggerTime, double coreDamage,
                                       HashMap<String, Double> attributes,
                                       double baneMultiplier) {
        return triggerElementEffect(damageSource, hurter, attacker, itemStack,
                WeaponModuleHandler.getModules(itemStack),
                triggerTime, coreDamage, attributes, baneMultiplier);
    }

    // ========== 工具方法 / Utility ==========

    /**
     * 在实体附近生成随机偏移的伤害数字显示位置
     *
     * @param entity 受击实体
     * @return 随机偏移后的位置向量
     */
    static Vec3 getRandomDamagePosition(LivingEntity entity) {
        double offsetX = (Math.random() - 0.5) * entity.getBbWidth() * 1.2;
        double offsetZ = (Math.random() - 0.5) * entity.getBbWidth() * 1.2;
        return new Vec3(
                entity.getX() + offsetX,
                entity.getY() + entity.getBbHeight() * (-0.2 + Math.random() * 0.4),
                entity.getZ() + offsetZ
        );
    }

    /**
     * 获取元素对应的表情符号（用于伤害数字显示）
     * <p>⭐ v6 颜色码调整：
     * <ul>
     *     <li>gas（毒气）：§a 绿 → §b 青色（AQUA），对应粒子青色</li>
     *     <li>virus（病毒）：§a 绿 → §d 亮紫粉（LIGHT_PURPLE），对应粒子粉色</li>
     * </ul></p>
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
            case "gas": return "\u00a7b\uD83D\uDCA8";          // v6：§a → §b 青色
            case "radiation": return "\u00a7e\u2622";
            case "magnetic": return "\u00a7b\ud83e\uddf2";
            case "corrosion": return "\u00a72\ud83e\uddea";
            case "explosion": return "\u00a74\ud83d\udca5";
            case "virus": return "\u00a7d\ud83e\udda0";         // v6：§a → §d 亮紫粉
            default: return "";
        }
    }
}
