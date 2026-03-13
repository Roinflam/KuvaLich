package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器战斗事件处理器
 * 负责所有伤害事件、近战溅射、弓箭多重射击、射速加速、攻击速度tick
 *
 * Weapon Combat Event Handler
 * Handles all damage events, melee splash, bow multishot, firing rate, attack speed tick
 */
@Mod.EventBusSubscriber
public class WeaponCombatHandler {

    // ========== 伤害显示缓冲 / Damage Display Buffer ==========

    /**
     * 临时存储待显示的伤害信息（颜色和触发元素）
     */
    private static class DamageDisplayInfo {
        String colorCode;
        Set<String> triggeredElements;

        DamageDisplayInfo(String colorCode, Set<String> triggeredElements) {
            this.colorCode = colorCode;
            this.triggeredElements = triggeredElements;
        }
    }

    /**
     * 使用 ConcurrentHashMap 存储每个实体的待显示伤害信息，key 为实体 ID
     * 条目在 LivingDamageEvent 中被立即消费，不会长期积累
     */
    private static final Map<Integer, Deque<DamageDisplayInfo>> pendingDisplays = new ConcurrentHashMap<>();

    // ========== 伤害事件 / Damage Events ==========

    /**
     * LivingHurt 事件入口：玩家造成伤害时触发武器模组伤害计算
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        DamageSource damageSource = evt.getSource();

        if (!evt.getEntity().level().isClientSide() && (damageSource.getDirectEntity() instanceof Player || damageSource.getEntity() instanceof Player)) {
            Player player;
            boolean isMelee = false;

            if (damageSource.getDirectEntity() instanceof Player) {
                player = (Player) damageSource.getDirectEntity();
                isMelee = true;
            } else if (damageSource.getEntity() instanceof Player) {
                player = (Player) damageSource.getEntity();
                isMelee = false;
            } else {
                player = null;
            }

            if (player != null) {
                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty() && WeaponModuleHandler.hasBase(weapon)) {
                    if (isMelee) {
                        processDamage(evt, player, weapon, damageSource, 1, isMelee);
                    } else {
                        processDamage(evt, player, weapon, damageSource, 1.0f, isMelee);
                    }
                }
            }
        }
    }

    /**
     * LivingDamage 事件：读取待显示信息，发送伤害数字到客户端
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        LivingEntity hurter = evt.getEntity();
        int entityId = hurter.getId();

        Deque<DamageDisplayInfo> queue = pendingDisplays.get(entityId);
        if (queue != null && !queue.isEmpty()) {
            DamageDisplayInfo displayInfo = queue.pollFirst();

            if (queue.isEmpty()) {
                pendingDisplays.remove(entityId);
            }

            Player player = null;
            DamageSource damageSource = evt.getSource();

            if (damageSource.getDirectEntity() instanceof Player) {
                player = (Player) damageSource.getDirectEntity();
            } else if (damageSource.getEntity() instanceof Player) {
                player = (Player) damageSource.getEntity();
            }

            if (player != null && ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
                float finalDamage = evt.getAmount();

                if (finalDamage > 0 && !Float.isNaN(finalDamage) && !Float.isInfinite(finalDamage)) {
                    StringBuilder displayText = new StringBuilder(displayInfo.colorCode + DamagePacket.formatDamage(finalDamage));
                    for (String element : displayInfo.triggeredElements) {
                        displayText.append(WeaponElementSystem.getElementEmoji(element));
                    }
                    Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
                    DamagePacket.sendToPlayer((ServerPlayer) player, displayText.toString(), position);
                }
            }
        } else {
            DamageSource damageSource = evt.getSource();
            Player player = null;

            if (damageSource.getDirectEntity() instanceof Player) {
                player = (Player) damageSource.getDirectEntity();
            } else if (damageSource.getEntity() instanceof Player) {
                player = (Player) damageSource.getEntity();
            }

            if (player != null && ModConfig.KUVA_LICH.damageDisplay.get()) {
                displayDamage(evt, player);
            }
        }
    }

    // ========== 核心伤害计算 / Core Damage Calculation ==========

    /**
     * 核心伤害处理流程：
     * 基础伤害 → 暴击计算 → 克制倍率 → 元素伤害 → 元素触发 → 最终伤害
     */
    private static void processDamage(LivingHurtEvent evt, Player player, ItemStack weapon,
                                      DamageSource damageSource, float attackStrength, boolean isMelee) {
        LivingEntity hurter = evt.getEntity();
        double baseDamage = 1;

        double criticalStrikeProbability = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeProbability") * 100;
        double criticalStrikeMultiplier = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeMultiplier");
        double triggerChance = WeaponModuleHandler.getBaseAttribute(weapon, "triggerChance") * 100;

        if (isMelee) {
            criticalStrikeProbability *= attackStrength;
        }

        List<ItemStack> modules = WeaponModuleHandler.getModules(weapon);
        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, weapon);

        applyKillStackEffects(player, weapon, attributes, hurter);

        // ========== 伤害类型加成 ==========
        if (damageSource.getDirectEntity() instanceof Player) {
            baseDamage += attributes.getOrDefault("meleeDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));

            if (attributes.containsKey("killStackMeleeCriticalMultiplier")) {
                int stacks = KillStackManager.getStacks(player, StackType.MELEE_CRIT_MULT);
                double stackValue = attributes.get("killStackMeleeCriticalMultiplier");
                criticalStrikeMultiplier *= (1 + stackValue * stacks);
            }

            if (player.isSprinting()) {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0) + attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0));
            } else {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
            }
        } else if (damageSource.getEntity() instanceof Player) {
            baseDamage += attributes.getOrDefault("remoteDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));

            if (damageSource.getDirectEntity() instanceof Arrow) {
                baseDamage += attributes.getOrDefault("arrowDamage", 0.0);
            } else if (damageSource.getDirectEntity() instanceof Projectile) {
                baseDamage += attributes.getOrDefault("projectileDamage", 0.0);
            }

            // 爆炸半径溅射
            double range = 1 + attributes.getOrDefault("bursting_radius", 0.0) * 2;
            if (attributes.containsKey("killStackBurstingRadius")) {
                int stacks = KillStackManager.getStacks(player, StackType.BURSTING_RADIUS);
                double stackValue = attributes.get("killStackBurstingRadius");
                range += stackValue * stacks * 2;
            }

            if (WarframeTaczBridge.isBurstRadiusSuppressed()) {
                range = 1;
                WarframeTaczBridge.clearBurstRadiusSuppressed();
            }

            if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                if (range > 1) {
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                            e -> !e.equals(hurter) && !e.equals(player));
                    for (LivingEntity entity : entities) {
                        entity.hurt(player.damageSources().playerAttack(player), evt.getAmount() * 0.5f);
                    }
                }
            } else if (range < 0) {
                evt.setCanceled(true);
                return;
            }
        }

        if (damageSource.getMsgId().toLowerCase().contains("magic") || damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            baseDamage += attributes.getOrDefault("magicDamage", 0.0);
        }

        // ========== 元素伤害总量 ==========
        double elementDamage = 0;
        if (KuvaWeaponUtil.hasType(weapon)) {
            elementDamage += WeaponElementSystem.getKuvaWeaponElementDamage(weapon);
        }
        elementDamage += attributes.getOrDefault("fire", 0.0);
        elementDamage += attributes.getOrDefault("ice", 0.0);
        elementDamage += attributes.getOrDefault("poison", 0.0);
        elementDamage += attributes.getOrDefault("electricity", 0.0);
        elementDamage += attributes.getOrDefault("slash", 0.0);
        elementDamage += attributes.getOrDefault("puncture", 0.0);
        elementDamage += attributes.getOrDefault("impact", 0.0);
        elementDamage += attributes.getOrDefault("gas", 0.0);
        elementDamage += attributes.getOrDefault("radiation", 0.0);
        elementDamage += attributes.getOrDefault("magnetic", 0.0);
        elementDamage += attributes.getOrDefault("corrosion", 0.0);
        elementDamage += attributes.getOrDefault("explosion", 0.0);
        elementDamage += attributes.getOrDefault("virus", 0.0);

        // ========== 基础面板倍率 ==========
        if (WeaponModuleHandler.getBaseAttribute(weapon, "damage") > 0) {
            if (WeaponModuleHandler.getBaseAttribute(weapon, "damage") >= 1) {
                baseDamage *= WeaponModuleHandler.getBaseAttribute(weapon, "damage");
                elementDamage *= WeaponModuleHandler.getBaseAttribute(weapon, "damage");
            } else {
                baseDamage *= Math.pow(WeaponModuleHandler.getBaseAttribute(weapon, "damage"), 2);
                elementDamage *= Math.pow(WeaponModuleHandler.getBaseAttribute(weapon, "damage"), 2);
            }
        }

        // ========== 暴击计算 ==========
        String colorCode;
        if (criticalStrikeProbability > 300) {
            baseDamage *= criticalStrikeMultiplier * 3;
            colorCode = "§c";
        } else if (criticalStrikeProbability > 200) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 200)) {
                baseDamage *= criticalStrikeMultiplier * 3;
                colorCode = "§c";
            } else {
                baseDamage *= criticalStrikeMultiplier * 2;
                colorCode = "§6";
            }
        } else if (criticalStrikeProbability > 100) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 100)) {
                baseDamage *= criticalStrikeMultiplier * 2;
                colorCode = "§6";
            } else {
                baseDamage *= criticalStrikeMultiplier;
                colorCode = "§e";
            }
        } else {
            if (RandomUtil.percentageChance(criticalStrikeProbability)) {
                baseDamage *= criticalStrikeMultiplier;
                colorCode = "§e";
            } else {
                if (hurter.getAbsorptionAmount() > 0) {
                    colorCode = "§b";
                } else {
                    colorCode = "§f";
                }
                baseDamage += attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0);
            }
        }

        // ========== 克制倍率 ==========
        double baneMultiplier = 1.0;
        if (hurter.getMobType().equals(MobType.UNDEFINED)) {
            baneMultiplier += attributes.getOrDefault("bane_of_undefined", 0.0);
        } else if (hurter.getMobType().equals(MobType.UNDEAD)) {
            baneMultiplier += attributes.getOrDefault("bane_of_undead", 0.0);
        } else if (hurter.getMobType().equals(MobType.ARTHROPOD)) {
            baneMultiplier += attributes.getOrDefault("bane_of_arthropod", 0.0);
        } else {
            baneMultiplier += attributes.getOrDefault("bane_of_illager", 0.0);
        }

        // ========== 最终伤害合算 ==========
        float originalDamage = evt.getAmount();
        double physicalDamage = originalDamage * baseDamage * baneMultiplier;
        double elementalDamage = originalDamage * elementDamage * baneMultiplier;
        float totalDamage = (float) (physicalDamage + elementalDamage);
        double coreDamage = physicalDamage;

        // ========== 元素触发 ==========
        double triggerTime = 1 + attributes.getOrDefault("triggerTime", 0.0);

        if (player.isSprinting()) {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0) + attributes.getOrDefault("dashTriggerChance", 0.0));
        } else {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0));
        }

        if (attributes.containsKey("killStackTriggerChance")) {
            int stacks = KillStackManager.getStacks(player, StackType.TRIGGER_CHANCE);
            double stackValue = attributes.get("killStackTriggerChance");
            triggerChance *= (1 + stackValue * stacks);
        }

        Set<String> triggeredElements = new LinkedHashSet<>();

        if (triggerChance > 100) {
            int number = (int) triggerChance / 100;
            for (int i = 0; i < number; i++) {
                String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
            if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
        } else if (RandomUtil.percentageChance(triggerChance)) {
            String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                    coreDamage, attributes, baneMultiplier);
            if (element != null) triggeredElements.add(element);
        }

        totalDamage = Math.max(totalDamage, 0);
        evt.setAmount(totalDamage);

        // 写入待显示信息，供 onLivingDamage 读取
        pendingDisplays.computeIfAbsent(hurter.getId(), k -> new ArrayDeque<>())
                .addLast(new DamageDisplayInfo(colorCode, triggeredElements));
    }

    /**
     * 普通伤害显示（无模组武器的默认白色伤害数字）
     */
    private static void displayDamage(LivingDamageEvent evt, Player player) {
        LivingEntity hurter = evt.getEntity();
        float damage = evt.getAmount();
        if (damage > 0 && !Float.isNaN(damage) && !Float.isInfinite(damage)) {
            String displayText = "§f" + DamagePacket.formatDamage(damage);
            Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
            DamagePacket.sendToPlayer((ServerPlayer) player, displayText, position);
        }
    }

    // ========== 击杀叠层效果 / Kill Stack Effects ==========

    /**
     * 将击杀叠层的增益应用到武器属性上
     * 传入的 attributes 是缓存副本，修改不会影响缓存原数据
     */
    private static void applyKillStackEffects(Player player, ItemStack weapon,
                                              HashMap<String, Double> attributes,
                                              LivingEntity target) {
        if (!WeaponModuleHandler.hasBase(weapon)) return;

        if (attributes.containsKey("killStackBaseDamage")) {
            int stacks = KillStackManager.getStacks(player, StackType.BASE_DAMAGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackBaseDamage");
                int debuffCount = 0;
                for (MobEffectInstance effect : target.getActiveEffects()) {
                    if (effect.getEffect().getCategory().equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL)) {
                        debuffCount++;
                    }
                }
                if (debuffCount > 0) {
                    double bonusDamage = stackValue * stacks * debuffCount;
                    attributes.put("meleeDamage", attributes.getOrDefault("meleeDamage", 0.0) + bonusDamage);
                    attributes.put("remoteDamage", attributes.getOrDefault("remoteDamage", 0.0) + bonusDamage);
                }
            }
        }

        if (attributes.containsKey("killStackMultishot")) {
            int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackMultishot");
                attributes.put("multishot", attributes.getOrDefault("multishot", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackSpeed")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackSpeed");
                attributes.put("attackSpeed", attributes.getOrDefault("attackSpeed", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackRange")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_RANGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackRange");
                attributes.put("attackRange", attributes.getOrDefault("attackRange", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFiringRate");
                attributes.put("firing_rate", attributes.getOrDefault("firing_rate", 0.0) + stackValue * stacks);
            }
        }
    }

    // ========== 攻击速度Tick / Attack Speed Tick ==========

    /**
     * 每秒检测一次武器攻击速度属性，通过动态属性系统应用到玩家
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                Player player = evt.player;
                if (player.level().getGameTime() % 20 == 0 && player.isAlive()) {
                    ItemStack weapon = player.getMainHandItem();
                    if (!weapon.isEmpty() && WeaponModuleHandler.hasBase(weapon)) {
                        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, weapon);

                        if (attributes.containsKey("killStackAttackSpeed")) {
                            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_SPEED);
                            if (stacks > 0) {
                                double stackValue = attributes.get("killStackAttackSpeed");
                                attributes.put("attackSpeed", attributes.getOrDefault("attackSpeed", 0.0) + stackValue * stacks);
                            }
                        }

                        double attackSpeed = attributes.getOrDefault("attackSpeed", 0.0);
                        if (attackSpeed >= 0.1) {
                            int level = (int) (attackSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.ATTACK_SPEED.createInstance(30, level));
                        } else if (attackSpeed <= -0.1) {
                            int level = (int) (-attackSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_ATTACK_SPEED.createInstance(30, level));
                        }
                    }
                }
            }
        }
    }

    // ========== 近战溅射 / Melee Splash ==========

    /**
     * 近战攻击时根据攻击范围属性对周围敌人造成溅射伤害
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            if (evt.getTarget() instanceof LivingEntity) {
                LivingEntity hurter = (LivingEntity) evt.getTarget();
                Player player = evt.getEntity();

                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty() && WeaponModuleHandler.hasBase(weapon)) {
                    float attackStrength = player.getAttackStrengthScale(0.5F);
                    if (attackStrength <= 0.8F) return;

                    HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, weapon);

                    double range = attributes.getOrDefault("attackRange", 0.0);
                    if (player.isSprinting()) {
                        range += attributes.getOrDefault("dashAttackRange", 0.0);
                    }
                    if (attributes.containsKey("killStackAttackRange")) {
                        int stacks = KillStackManager.getStacks(player, StackType.ATTACK_RANGE);
                        double stackValue = attributes.get("killStackAttackRange");
                        range += stackValue * stacks;
                    }

                    if (range > 0) {
                        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                                e -> !e.equals(hurter) && !e.equals(player));
                        for (LivingEntity entity : entities) {
                            float damage = EntityPlayerUtil.getAttackDamage(player, entity);
                            entity.hurt(player.damageSources().playerAttack(player), damage * 0.5f * attackStrength);
                        }
                    } else if (range < 0) {
                        evt.setCanceled(true);
                    }
                }
            }
        }
    }

    // ========== 弓箭 / 射速事件 / Bow & Firing Rate ==========

    /**
     * 使用物品Tick事件：处理射速减速（负射速时概率取消tick或停止使用）
     */
    @SubscribeEvent
    public static void onLivingEntityUseItemTick(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        ItemStack usingItem = evt.getItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, weapon);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) { firingRate *= 2.0; }
        if (Math.abs(firingRate) < 0.001) return;
        if (firingRate <= -1.0) { player.stopUsingItem(); }
        else if (firingRate < 0) { if (RandomUtil.percentageChance(Math.abs(firingRate) * 100)) { evt.setCanceled(true); } }
    }

    /**
     * 生物Tick事件：处理射速加速（正射速时额外更新使用进度）
     */
    @SubscribeEvent
    public static void onLivingTickForFiringRate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        if (!entity.isUsingItem()) return;
        ItemStack usingItem = entity.getUseItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, weapon);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) { firingRate *= 2.0; }
        if (firingRate <= 0) return;
        int extraUpdates = (int) firingRate;
        for (int i = 0; i < extraUpdates; i++) { EntityLivingUtil.updateHeld(entity); }
        double fractionalPart = firingRate - extraUpdates;
        if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) { EntityLivingUtil.updateHeld(entity); }
    }

    /**
     * 弓箭释放事件：处理多重射击（multishot）
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        Player player = evt.getEntity();
        if (!evt.getEntity().level().isClientSide()) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && WeaponModuleHandler.hasBase(bow)) {
                HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, bow);

                double multishot = attributes.getOrDefault("multishot", 0.0);
                if (attributes.containsKey("killStackMultishot")) {
                    int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
                    double stackValue = attributes.get("killStackMultishot");
                    multishot += stackValue * stacks;
                }
                if (multishot > 0) {
                    int charge = evt.getCharge();
                    float velocity = getBowVelocity(charge);
                    if (velocity < 0.1f) return;
                    if (multishot > 1) {
                        int number = (int) multishot;
                        for (int i = 0; i < number; i++) { fireArrow(player, player.level(), velocity, bow, true); }
                        if (RandomUtil.percentageChance((multishot - number) * 100)) { fireArrow(player, player.level(), velocity, bow, true); }
                    } else {
                        if (RandomUtil.percentageChance(multishot * 100)) { fireArrow(player, player.level(), velocity, bow, true); }
                    }
                } else if (multishot < 0 && multishot > -1) {
                    if (RandomUtil.percentageChance(Math.abs(multishot) * 100)) { evt.setCanceled(true); }
                } else if (multishot <= -1) { evt.setCanceled(true); }
            }
        }
    }

    /**
     * 计算弓箭蓄力速度
     */
    private static float getBowVelocity(int charge) {
        float f = (float) charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }

    /**
     * 发射一支额外箭矢（多重射击用）
     */
    private static void fireArrow(Player player, Level level, float velocity, ItemStack bow, boolean infiniteArrows) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        ArrowItem arrowItem = (arrowStack.getItem() instanceof ArrowItem) ? (ArrowItem) arrowStack.getItem() : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, arrowStack, player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity * 3.0F, 5.0F);
        arrow.addTag("multishot");
        if (velocity == 1.0F) arrow.setCritArrow(true);
        applyBowEnchantments(arrow, bow);
        if (infiniteArrows) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        level.addFreshEntity(arrow);
    }

    /**
     * 将弓的附魔效果应用到额外箭矢上
     */
    private static void applyBowEnchantments(AbstractArrow arrow, ItemStack bow) {
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        int knockback = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (knockback > 0) arrow.setKnockback(knockback);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) { arrow.setSecondsOnFire(100); }
    }
}
