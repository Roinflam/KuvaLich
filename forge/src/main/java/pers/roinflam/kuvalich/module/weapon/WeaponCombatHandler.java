// 文件：WeaponCombatHandler.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/module/weapon/WeaponCombatHandler.java
package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.compat.curios.CuriosCompat;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器战斗事件处理器
 * 负责所有伤害事件、弓箭多重射击、射速加速、攻击速度/攻击距离tick
 * 支持所有LivingEntity持有开光武器享受加成和元素触发
 * 伤害数字：玩家直接显示，驯服生物同维度内转发给主人（带🎀前缀，继承暴击颜色）
 * <p>
 * ⭐ 多槽位支持：主手提供基础面板，副手/护甲/饰品栏的模组属性额外叠加
 * ⭐ Multi-slot support: main hand provides base panel, off-hand/armor/curios contribute bonus module attributes
 *
 * Weapon Combat Event Handler
 * Supports all LivingEntity with modded weapons.
 * Damage numbers: direct for players, forwarded to same-dimension owner for tamed entities (with 🎀 prefix inheriting crit color).
 */
@Mod.EventBusSubscriber
public class WeaponCombatHandler {

    // ========== 伤害显示缓冲 / Damage Display Buffer ==========

    /**
     * 临时存储待显示的伤害信息
     * 直接存储 ServerPlayer 引用而非实体ID，避免二次查找失败
     * onLivingHurt 和 onLivingDamage 在同一tick同步执行，引用必然有效
     */
    private static class DamageDisplayInfo {
        /** 暴击颜色代码 */
        String colorCode;
        /** 触发的元素集合 */
        Set<String> triggeredElements;
        /** 显示前缀（玩家为空，宠物/女仆为🎀，无颜色代码，继承暴击颜色） */
        String prefix;
        /** 接收伤害数字的玩家（直接引用，不做ID反查） */
        ServerPlayer displayTarget;

        DamageDisplayInfo(String colorCode, Set<String> triggeredElements,
                          String prefix, ServerPlayer displayTarget) {
            this.colorCode = colorCode;
            this.triggeredElements = triggeredElements;
            this.prefix = prefix;
            this.displayTarget = displayTarget;
        }
    }

    /**
     * 使用 ConcurrentHashMap 存储每个受害者实体的待显示伤害信息
     * 条目在 LivingDamageEvent 中被立即消费，不会长期积累
     */
    private static final Map<Integer, Deque<DamageDisplayInfo>> pendingDisplays = new ConcurrentHashMap<>();

    // ========== 多槽位属性缓存 / Multi-Slot Attribute Cache ==========

    /**
     * 额外槽位属性缓存（已含倍率预计算）
     * Extra slot attribute cache (multipliers pre-applied)
     * <p>
     * key = 实体UUID，value = 所有额外槽位模组属性合并后的结果（倍率已乘入）。
     * 每 EXTRA_SLOT_CACHE_INTERVAL tick 清空一次，期间同一实体不会重复计算。
     * 因为玩家很少在战斗中频繁更换模组，2秒刷新一次足够。
     */
    private static final Map<UUID, HashMap<String, Double>> EXTRA_SLOT_CACHE = new ConcurrentHashMap<>();

    /** 上次清空额外槽位缓存的 gameTick / Last tick when extra slot cache was cleared */
    private static long extraSlotCacheTick = -1;

    /** 额外槽位缓存刷新间隔（tick）：40 tick = 2秒 / Extra slot cache refresh interval */
    private static final long EXTRA_SLOT_CACHE_INTERVAL = 40L;

    // ========== 元素触发硬上限 / Element Trigger Hard Cap ==========

    /**
     * 单次伤害事件中元素效果的最大触发次数（硬上限）
     * Maximum number of element trigger effects per single damage event (hard cap)
     * <p>
     * 防止超高 triggerChance（来自模组堆叠、击杀叠层、冲刺加成等）在群体攻击场景下
     * 触发数百次元素效果，导致服务器主线程卡死被 Watchdog 终止。
     * <p>
     * 即使最终 triggerChance 计算结果超过 2000%（=20 次），也强制截断为 20 次。
     * 体感上 20 次已经足以让所有元素 debuff 叠满层数（最高 9 层）+ DOT 元素打出爆发伤害。
     * <p>
     * 取值理由：
     * - 单次 20 次爆炸 ≈ 1-5ms，单目标完全可接受
     * - 群体场景（20 目标 × 20 次 = 400 次）仍需依赖 ElementSyncGuard /
     *   ParticleEmissionGuard 节流网络包和粒子，但 DOT 任务和爆炸调用不会失控
     * - 比 100 次降低 5 倍负载，单帧最坏开销可控
     */
    private static final int MAX_ELEMENT_TRIGGER_COUNT = 20;

    /**
     * 获取缓存的额外槽位属性（含倍率已预计算）
     * Get cached extra slot attributes (multipliers pre-applied)
     * <p>
     * 同包可见，供 WeaponModuleHandler 的 Tooltip 复用。
     * Package-private for reuse by WeaponModuleHandler's tooltip.
     *
     * @param entity 实体 / entity
     * @return 额外槽位属性表（只读使用，不要修改）/ extra slot attribute map (read-only, do not modify)
     */
    static HashMap<String, Double> getCachedExtraSlotAttributes(LivingEntity entity) {
        long currentTick = entity.level().getGameTime();
        // 超过刷新间隔则清空全部缓存，所有实体下次访问时重新计算
        // Clear all cache entries when interval expires, all entities recompute on next access
        if (Math.abs(currentTick - extraSlotCacheTick) >= EXTRA_SLOT_CACHE_INTERVAL) {
            EXTRA_SLOT_CACHE.clear();
            extraSlotCacheTick = currentTick;
        }
        return EXTRA_SLOT_CACHE.computeIfAbsent(entity.getUUID(), uuid -> computeExtraSlotAttributes(entity));
    }

    /**
     * 计算实体所有额外槽位的模组属性合并结果（含倍率）
     * Compute merged module attributes from all extra slots (with multipliers applied)
     * <p>
     * 仅在缓存未命中时被调用，每个实体每2秒最多执行一次。
     * Only called on cache miss, at most once per 2 seconds per entity.
     *
     * @param entity 实体 / entity
     * @return 合并后的额外属性表 / merged extra attribute map
     */
    private static HashMap<String, Double> computeExtraSlotAttributes(LivingEntity entity) {
        HashMap<String, Double> result = new HashMap<>();

        // 副手 / Off-hand
        if (ModConfig.KUVA_LICH.enableOffhandModule.get()) {
            double offhandMult = ModConfig.KUVA_LICH.offhandEffectMultiplier.get() / 100.0;
            mergeSlotAttributes(entity.getOffhandItem(), result, offhandMult);
        }

        // 护甲统一倍率 / Armor unified multiplier
        double armorMult = ModConfig.KUVA_LICH.armorEffectMultiplier.get() / 100.0;

        // 头盔 / Helmet
        if (ModConfig.KUVA_LICH.enableHelmetModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.HEAD), result, armorMult);
        }

        // 胸甲 / Chestplate
        if (ModConfig.KUVA_LICH.enableChestplateModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.CHEST), result, armorMult);
        }

        // 护腿 / Leggings
        if (ModConfig.KUVA_LICH.enableLeggingsModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.LEGS), result, armorMult);
        }

        // 靴子 / Boots
        if (ModConfig.KUVA_LICH.enableBootsModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.FEET), result, armorMult);
        }

        // Curios饰品栏（需要Curios模组） / Curios trinket slots (requires Curios mod)
        if (ModConfig.KUVA_LICH.enableCuriosModule.get()) {
            double curiosMult = ModConfig.KUVA_LICH.curiosEffectMultiplier.get() / 100.0;
            int maxSlots = ModConfig.KUVA_LICH.curiosModuleMaxSlots.get();
            List<ItemStack> curiosItems = CuriosCompat.getEquippedCurios(entity, maxSlots);
            for (ItemStack curio : curiosItems) {
                mergeSlotAttributes(curio, result, curiosMult);
            }
        }

        return result;
    }

    /**
     * 将缓存的额外槽位属性合并到战斗属性表
     * Merge cached extra slot attributes into combat attribute map
     * <p>
     * 所有事件处理器的调用入口。内部使用2秒缓存，大幅减少NBT读取次数。
     * Entry point for all event handlers. Uses 2-second cache to drastically reduce NBT reads.
     *
     * @param entity     实体 / entity
     * @param attributes 要合并到的属性表 / target attribute map
     */
    private static void mergeAdditionalSlotAttributes(LivingEntity entity, HashMap<String, Double> attributes) {
        HashMap<String, Double> cached = getCachedExtraSlotAttributes(entity);
        for (Map.Entry<String, Double> entry : cached.entrySet()) {
            attributes.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    /**
     * 将单个物品的模组属性按倍率合并到目标属性表
     * Merge a single item's module attributes into the target attribute map with effectiveness multiplier
     * <p>
     * 物品必须非空且已开光（hasBase）才会被处理。
     * 未开光、空栈或null均静默跳过，不抛出异常。
     *
     * @param itemStack          要合并的物品（可为null或空） / item to merge (may be null or empty)
     * @param attributes         目标属性表 / target attribute map
     * @param effectMultiplier   生效倍率（0.0~1.0） / effectiveness multiplier
     */
    private static void mergeSlotAttributes(ItemStack itemStack, HashMap<String, Double> attributes, double effectMultiplier) {
        if (itemStack == null || itemStack.isEmpty() || !WeaponModuleHandler.hasBase(itemStack)) {
            return;
        }
        if (effectMultiplier <= 0.0) {
            return;
        }
        // 读取该物品的基础伤害面板作为额外乘数（100%=1.0正常，80%=0.8打折，120%=1.2加成）
        // Read item's base damage panel as additional multiplier (100%=1.0, 80%=0.8, 120%=1.2)
        double baseDamage = WeaponModuleHandler.getBaseAttribute(itemStack, "damage");
        if (baseDamage <= 0.0) {
            baseDamage = 1.0;
        }
        double finalMultiplier = effectMultiplier * baseDamage;
        HashMap<String, Double> slotAttrs = WeaponModuleHandler.getWeaponAttributes(itemStack);
        for (Map.Entry<String, Double> entry : slotAttrs.entrySet()) {
            attributes.merge(entry.getKey(), entry.getValue() * finalMultiplier, Double::sum);
        }
    }

    // ========== 通用工具方法 / Utility ==========

    /**
     * 获取攻击者的通用攻击伤害源
     * 玩家使用 playerAttack，Mob 使用 mobAttack，其他使用 generic
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
     * 查找应该接收伤害数字的玩家
     * 1. 攻击者本身是 ServerPlayer → 返回自身
     * 2. 攻击者是驯服生物（女仆/狼等） → 查找主人，同维度在线即可，不限距离
     * 3. 其他情况 → 返回 null（不显示伤害数字）
     *
     * @param attacker 攻击者实体
     * @return 应接收伤害数字的玩家，无则返回null
     */
    @Nullable
    static ServerPlayer findDamageDisplayTarget(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        if (attacker instanceof TamableAnimal tamable) {
            LivingEntity owner = tamable.getOwner();
            if (owner instanceof ServerPlayer player && attacker.level() == player.level()) {
                return player;
            }
        }
        return null;
    }

    /**
     * 获取伤害数字的显示前缀
     * 玩家自身攻击无前缀
     * 驯服生物攻击加🎀前缀
     *
     * @param attacker 攻击者实体
     * @return 显示前缀字符串
     */
    static String getDamageDisplayPrefix(LivingEntity attacker) {
        return (attacker instanceof Player) ? "" : "🎀";
    }

    // ========== 伤害事件 / Damage Events ==========

    /**
     * LivingHurt 事件入口：任何LivingEntity持有开光武器造成伤害时触发武器模组伤害计算
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        DamageSource damageSource = evt.getSource();

        if (!evt.getEntity().level().isClientSide()) {
            LivingEntity attacker = null;
            boolean isMelee = false;

            if (damageSource.getDirectEntity() instanceof LivingEntity direct) {
                attacker = direct;
                isMelee = true;
            } else if (damageSource.getEntity() instanceof LivingEntity indirect) {
                attacker = indirect;
                isMelee = false;
            }

            if (attacker != null) {
                ItemStack weapon = attacker.getMainHandItem();
                if (!weapon.isEmpty() && WeaponModuleHandler.hasBase(weapon)) {
                    processDamage(evt, attacker, weapon, damageSource, 1.0f, isMelee);
                }
            }
        }
    }

    /**
     * LivingDamage 事件：读取待显示信息，发送伤害数字到客户端
     * 直接使用 DamageDisplayInfo 中缓存的 ServerPlayer 引用发包
     * 拼接顺序：颜色代码 → 🎀前缀 → 伤害数字 → 元素emoji
     * 使🎀和数字都继承同一个暴击颜色
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

            ServerPlayer serverPlayer = displayInfo.displayTarget;
            if (serverPlayer != null && serverPlayer.isAlive()
                    && ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
                float finalDamage = evt.getAmount();

                if (finalDamage > 0 && !Float.isNaN(finalDamage) && !Float.isInfinite(finalDamage)) {
                    StringBuilder displayText = new StringBuilder();
                    displayText.append(displayInfo.colorCode);
                    displayText.append(displayInfo.prefix);
                    displayText.append(DamagePacket.formatDamage(finalDamage));
                    for (String element : displayInfo.triggeredElements) {
                        displayText.append(WeaponElementSystem.getElementEmoji(element));
                    }
                    Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
                    DamagePacket.sendToPlayer(serverPlayer, displayText.toString(), position);
                }
            }
        } else {
            // 非模组武器的普通伤害显示（仅玩家攻击者）
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
     * 基础伤害 → 暴击计算 → 克制倍率 → 元素伤害 → 元素触发 → 最终伤害 → 击杀叠层
     * <p>
     * ⭐ 新增：在获取主手武器模组属性后，合并额外槽位（副手/护甲/饰品栏）的模组属性
     * ⭐ 性能优化：modules 列表只解析一次，透传给元素触发系统避免重复 NBT 反序列化
     * ⭐ 元素触发硬上限：最终 triggerChance 被截断到 MAX_ELEMENT_TRIGGER_COUNT * 100，
     *    单次伤害事件最多触发 20 次元素效果，避免高几率堆叠造成服务器卡死
     *
     * 支持所有 LivingEntity 攻击者
     * 击杀叠层效果仅对玩家生效
     * 伤害数字：玩家直接显示，驯服生物同维度内转发给主人（带🎀前缀）
     */
    private static void processDamage(LivingHurtEvent evt, LivingEntity attacker, ItemStack weapon,
                                      DamageSource damageSource, float attackStrength, boolean isMelee) {
        LivingEntity hurter = evt.getEntity();
        double baseDamage = 1;

        double criticalStrikeProbability = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeProbability") * 100;
        double criticalStrikeMultiplier = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeMultiplier");
        double triggerChance = WeaponModuleHandler.getBaseAttribute(weapon, "triggerChance") * 100;

        if (isMelee) {
            criticalStrikeProbability *= attackStrength;
        }

        // ⭐ 性能优化：modules 只解析一次，后续元素触发复用此列表
        // ⭐ Performance: parse modules once, reuse for element triggers
        List<ItemStack> modules = WeaponModuleHandler.getModules(weapon);
        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(attacker, weapon);

        // ⭐ 新增：合并额外装备槽位的模组属性（副手/护甲/Curios饰品栏）
        // ⭐ NEW: Merge module attributes from additional equipment slots (off-hand/armor/curios)
        mergeAdditionalSlotAttributes(attacker, attributes);

        boolean isPlayer = attacker instanceof Player;
        if (isPlayer) {
            applyKillStackEffects((Player) attacker, weapon, attributes, hurter);
        }

        // ========== 伤害类型加成 ==========
        if (damageSource.getDirectEntity() == attacker) {
            baseDamage += attributes.getOrDefault("meleeDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));

            if (isPlayer && attributes.containsKey("killStackMeleeCriticalMultiplier")) {
                int stacks = KillStackManager.getStacks((Player) attacker, StackType.MELEE_CRIT_MULT);
                double stackValue = attributes.get("killStackMeleeCriticalMultiplier");
                criticalStrikeMultiplier *= (1 + stackValue * stacks);
            }

            if (attacker.isSprinting()) {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0) + attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0));
            } else {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
            }
        } else if (damageSource.getEntity() == attacker) {
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
            if (isPlayer && attributes.containsKey("killStackBurstingRadius")) {
                int stacks = KillStackManager.getStacks((Player) attacker, StackType.BURSTING_RADIUS);
                double stackValue = attributes.get("killStackBurstingRadius");
                range += stackValue * stacks * 2;
            }

            if (WarframeTaczBridge.isBurstRadiusSuppressed()) {
                range = 1;
                WarframeTaczBridge.clearBurstRadiusSuppressed();
            }

            if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                if (range > 1) {
                    DamageSource splashSource = getAttackDamageSource(attacker);
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                            e -> !e.equals(hurter) && !e.equals(attacker));
                    for (LivingEntity entity : entities) {
                        entity.hurt(splashSource, evt.getAmount() * 0.5f);
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

        if (attacker.isSprinting()) {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0) + attributes.getOrDefault("dashTriggerChance", 0.0));
        } else {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0));
        }

        if (isPlayer && attributes.containsKey("killStackTriggerChance")) {
            int stacks = KillStackManager.getStacks((Player) attacker, StackType.TRIGGER_CHANCE);
            double stackValue = attributes.get("killStackTriggerChance");
            triggerChance *= (1 + stackValue * stacks);
        }

        // ⭐ 元素触发硬上限：所有加成乘算完成后，强制截断到 MAX_ELEMENT_TRIGGER_COUNT * 100。
        // ⭐ Element trigger hard cap: clamp triggerChance after all multipliers applied.
        // 即使理论值超过 2000%（=20 次），也只会触发 20 次元素效果，避免群体场景下
        // N 目标 × M 次的爆炸式调用使主线程超时被 Watchdog 终止。
        // Even if theoretical value exceeds 2000% (=20 triggers), only 20 effects are triggered,
        // preventing exploding N-target × M-trigger scenarios from causing main thread timeout.
        triggerChance = Math.min(triggerChance, MAX_ELEMENT_TRIGGER_COUNT * 100.0);

        Set<String> triggeredElements = new LinkedHashSet<>();

        // ⭐ 性能优化：将已解析的 modules 列表传入 triggerElementEffect，
        //    避免每次触发都重新调用 getModules() 进行 NBT 反序列化（ItemStack.of）。
        //    SlashBlade 范围攻击一 tick 命中大量实体时，此优化可消除数百次冗余 ItemStack 创建。
        // ⭐ Performance: pass pre-parsed modules list to triggerElementEffect,
        //    avoiding repeated getModules() → ItemStack.of() NBT deserialization per trigger.
        if (triggerChance > 100) {
            int number = (int) triggerChance / 100;
            for (int i = 0; i < number; i++) {
                String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon, modules, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
            if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon, modules, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
        } else if (RandomUtil.percentageChance(triggerChance)) {
            String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon, modules, triggerTime,
                    coreDamage, attributes, baneMultiplier);
            if (element != null) triggeredElements.add(element);
        }

        totalDamage = Math.max(totalDamage, 0);
        evt.setAmount(totalDamage);

        // ========== 武器击杀叠层累加（击杀检测）==========
        if (isPlayer && hurter.getHealth() - totalDamage <= 0) {
            addWeaponKillStacks((Player) attacker, attributes);
        }

        // 查找伤害数字接收者并直接存储引用
        ServerPlayer displayTarget = findDamageDisplayTarget(attacker);
        if (displayTarget != null) {
            String prefix = getDamageDisplayPrefix(attacker);
            pendingDisplays.computeIfAbsent(hurter.getId(), k -> new ArrayDeque<>())
                    .addLast(new DamageDisplayInfo(colorCode, triggeredElements, prefix, displayTarget));
        }
    }

    /**
     * 普通伤害显示（无模组武器的默认白色伤害数字，仅玩家可见）
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
     * 将击杀叠层的增益应用到武器属性上（仅对玩家生效）
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

    // ========== 武器击杀叠层累加 / Weapon Kill Stack Addition ==========

    /**
     * 击杀时为玩家累加武器击杀叠层。
     *
     * @param player     击杀者
     * @param attributes 当前武器的运行时属性（已含模组词条的合并值）
     */
    private static void addWeaponKillStacks(Player player, HashMap<String, Double> attributes) {
        if (attributes.containsKey("killStackBaseDamage")) {
            KillStackManager.addStack(player, StackType.BASE_DAMAGE);
        }
        if (attributes.containsKey("killStackMultishot")) {
            KillStackManager.addStack(player, StackType.MULTISHOT);
        }
        if (attributes.containsKey("killStackMeleeCriticalMultiplier")) {
            KillStackManager.addStack(player, StackType.MELEE_CRIT_MULT);
        }
        if (attributes.containsKey("killStackTriggerChance")) {
            KillStackManager.addStack(player, StackType.TRIGGER_CHANCE);
        }
        if (attributes.containsKey("killStackAttackRange")) {
            KillStackManager.addStack(player, StackType.ATTACK_RANGE);
        }
        if (attributes.containsKey("killStackAttackSpeed")) {
            KillStackManager.addStack(player, StackType.ATTACK_SPEED);
        }
        if (attributes.containsKey("killStackBurstingRadius")) {
            KillStackManager.addStack(player, StackType.BURSTING_RADIUS);
        }
        if (attributes.containsKey("killStackFiringRate")) {
            KillStackManager.addStack(player, StackType.FIRING_RATE);
        }
    }

    // ========== 攻击速度 & 攻击距离 Tick / Attack Speed & Attack Range Tick ==========

    /**
     * 每秒检测一次武器攻击速度和攻击距离属性，通过动态属性系统应用
     * 支持所有持有开光武器的LivingEntity，击杀叠层加成仅对玩家生效
     * <p>
     * ⭐ 新增：合并额外槽位的模组属性
     */
    @SubscribeEvent
    public static void onLivingTick(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity.level().isClientSide()) return;
        if (!entity.isAlive()) return;
        if (entity.level().getGameTime() % 20 != 0) return;

        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        // ⭐ 合并额外槽位属性 / Merge additional slot attributes
        mergeAdditionalSlotAttributes(entity, attributes);

        boolean isPlayer = entity instanceof Player;

        // ========== 攻击速度 ==========
        if (isPlayer && attributes.containsKey("killStackAttackSpeed")) {
            int stacks = KillStackManager.getStacks((Player) entity, StackType.ATTACK_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackSpeed");
                attributes.put("attackSpeed", attributes.getOrDefault("attackSpeed", 0.0) + stackValue * stacks);
            }
        }

        double attackSpeed = attributes.getOrDefault("attackSpeed", 0.0);
        if (attackSpeed >= 0.1) {
            int level = (int) (attackSpeed / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.ATTACK_SPEED.createInstance(30, level));
        } else if (attackSpeed <= -0.1) {
            int level = (int) (-attackSpeed / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.NEGATIVE_ATTACK_SPEED.createInstance(30, level));
        }

        // ========== 攻击距离 ==========
        double attackRange = attributes.getOrDefault("attackRange", 0.0);
        if (entity.isSprinting()) {
            attackRange += attributes.getOrDefault("dashAttackRange", 0.0);
        }
        if (isPlayer && attributes.containsKey("killStackAttackRange")) {
            int rangeStacks = KillStackManager.getStacks((Player) entity, StackType.ATTACK_RANGE);
            if (rangeStacks > 0) {
                double rangeStackValue = attributes.get("killStackAttackRange");
                attackRange += rangeStackValue * rangeStacks;
            }
        }
        if (attackRange >= 0.1) {
            int rangeLevel = (int) (attackRange / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.ATTACK_RANGE.createInstance(30, rangeLevel));
        } else if (attackRange <= -0.1) {
            int rangeLevel = (int) (-attackRange / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.NEGATIVE_ATTACK_RANGE.createInstance(30, rangeLevel));
        }
    }

    // ========== 弓箭 / 射速事件 / Bow & Firing Rate ==========

    /**
     * 使用物品Tick事件：处理射速减速
     * ⭐ 新增：合并额外槽位的模组属性
     */
    @SubscribeEvent
    public static void onLivingEntityUseItemTick(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();
        ItemStack usingItem = evt.getItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        // ⭐ 合并额外槽位属性 / Merge additional slot attributes
        mergeAdditionalSlotAttributes(entity, attributes);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (entity instanceof Player player && attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) { firingRate *= 2.0; }
        if (Math.abs(firingRate) < 0.001) return;
        if (firingRate <= -1.0) { entity.stopUsingItem(); }
        else if (firingRate < 0) { if (RandomUtil.percentageChance(Math.abs(firingRate) * 100)) { evt.setCanceled(true); } }
    }

    /**
     * 生物Tick事件：处理射速加速
     * ⭐ 新增：合并额外槽位的模组属性
     */
    @SubscribeEvent
    public static void onLivingTickForFiringRate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (!entity.isUsingItem()) return;
        ItemStack usingItem = entity.getUseItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        // ⭐ 合并额外槽位属性 / Merge additional slot attributes
        mergeAdditionalSlotAttributes(entity, attributes);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (entity instanceof Player player && attributes.containsKey("killStackFiringRate")) {
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
     * 弓箭释放事件：处理多重射击（仅Player触发）
     * ⭐ 新增：合并额外槽位的模组属性
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        Player player = evt.getEntity();
        if (!evt.getEntity().level().isClientSide()) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && WeaponModuleHandler.hasBase(bow)) {
                HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, bow);
                // ⭐ 合并额外槽位属性 / Merge additional slot attributes
                mergeAdditionalSlotAttributes(player, attributes);

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