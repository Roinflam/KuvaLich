package pers.roinflam.kuvalich.itemstack;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.itemstack.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品模组系统（1.20.1版本，使用动态属性系统）
 * Item Module System (1.20.1 version, using dynamic attribute system)
 *
 * 性能优化：
 * 1. 武器属性每tick缓存，同一tick内多次事件不重复读取NBT
 *    Weapon attributes cached per tick, no redundant NBT reads within same tick
 * 2. pendingDisplays 改用 ConcurrentHashMap<entityId> 替代 WeakHashMap<LivingEntity>
 *    pendingDisplays uses ConcurrentHashMap<entityId> instead of WeakHashMap<LivingEntity>
 */
@Mod.EventBusSubscriber
public class ItemModule {

    /** Forma锁定的NBT键名 / Forma lock NBT key */
    private static final String FORMA_LOCK_KEY = Reference.MOD_ID + "_formaLocked";

    /**
     * 临时存储待显示的伤害信息（颜色和触发元素）
     * Temporarily store damage display info (color and triggered elements)
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
     * Use ConcurrentHashMap to store pending display info per entity, keyed by entity ID
     *
     * 性能优化：替代原 WeakHashMap<LivingEntity, ...>
     * - WeakHashMap 不是线程安全的，高频伤害场景下可能出问题
     * - 使用 int entityId 作为 key 避免持有 LivingEntity 强引用
     * - 条目在 LivingDamageEvent 中被立即消费，不会长期积累
     *
     * Performance: replaces WeakHashMap<LivingEntity, ...>
     * - WeakHashMap is not thread-safe, may fail under high-frequency damage
     * - Uses int entityId as key to avoid holding LivingEntity strong reference
     * - Entries are consumed immediately in LivingDamageEvent, no long-term accumulation
     */
    private static final Map<Integer, Deque<DamageDisplayInfo>> pendingDisplays = new ConcurrentHashMap<>();

    // ========== 武器属性缓存系统 / Weapon Attribute Cache System ==========

    /**
     * 每tick武器属性缓存（仅缓存 collectItemAttributes 的结果，不含 killStack 效果）
     * Per-tick weapon attribute cache (only caches collectItemAttributes result, without killStack effects)
     *
     * key = 玩家UUID（同一tick内同一玩家的武器不会变化）
     * key = player UUID (weapon doesn't change for same player within same tick)
     */
    private static final Map<UUID, HashMap<String, Double>> WEAPON_ATTRIBUTE_CACHE = new HashMap<>();

    /**
     * 缓存对应的 gameTick
     * Cache tick
     */
    private static long weaponCacheTick = -1;

    /**
     * 获取带缓存的武器模组属性
     * 每tick只执行一次 collectItemAttributes，后续调用返回副本
     *
     * 返回副本的原因：applyKillStackEffects 和 processDamage 会修改 map
     * Returns copy because applyKillStackEffects and processDamage mutate the map
     *
     * @param player 持有武器的玩家
     * @param weapon 武器物品栈
     * @return 属性副本（已应用 clamp，未应用 killStack 效果）
     */
    private static HashMap<String, Double> getCachedWeaponAttributes(Player player, ItemStack weapon) {
        long currentTick = player.level().getGameTime();

        // 新tick → 清空缓存
        // New tick → clear cache
        if (currentTick != weaponCacheTick) {
            WEAPON_ATTRIBUTE_CACHE.clear();
            weaponCacheTick = currentTick;
        }

        HashMap<String, Double> base = WEAPON_ATTRIBUTE_CACHE.computeIfAbsent(
                player.getUUID(), uuid -> collectItemAttributes(getModules(weapon)));

        // 返回副本：调用方（processDamage、applyKillStackEffects）会修改 map
        // Return copy: callers (processDamage, applyKillStackEffects) mutate the map
        return new HashMap<>(base);
    }

    // ========== Forma锁定相关方法 / Forma Lock Methods ==========

    /**
     * 检查物品是否被Forma锁定面板
     * 锁定后无法再使用塑形块重置基础属性
     *
     * @param itemStack 要检查的物品
     * @return 是否已锁定
     */
    public static boolean isFormaLocked(ItemStack itemStack) {
        var nbt = itemStack.getTag();
        if (nbt == null) {
            return false;
        }
        return nbt.getBoolean(FORMA_LOCK_KEY);
    }

    /**
     * 为物品设置Forma锁定标记
     * 锁定后该物品的基础面板属性将永久固定，无法再被塑形块重置
     *
     * @param itemStack 要锁定的物品
     */
    public static void setFormaLocked(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateTag();
        nbt.putBoolean(FORMA_LOCK_KEY, true);
        itemStack.setTag(nbt);
    }

    // ========== 运行时属性收集工具方法 / Runtime Attribute Collection Utility ==========

    /**
     * 收集武器模组的运行时属性（仅用于伤害、效果等逻辑计算，不用于 Tooltip 展示）
     * Collect weapon module attributes for runtime logic (damage, effects, etc.), NOT for tooltip display
     *
     * 注意：此方法开销较大（遍历模组、读取NBT、执行clamp），
     * 事件处理器中应通过 getCachedWeaponAttributes() 调用以获得每tick缓存。
     * Note: This method is expensive (module iteration, NBT read, clamp).
     * Event handlers should use getCachedWeaponAttributes() for per-tick caching.
     *
     * @param modules 武器装备的模组列表 / list of modules equipped on the weapon
     * @return 经过约束处理的运行时属性 Map / runtime attribute map with constraints applied
     */
    private static HashMap<String, Double> collectItemAttributes(List<ItemStack> modules) {
        HashMap<String, Double> attributes = new HashMap<>();

        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();

                double value = ModuleConfig.clampAttributeValue(key, entry.getValue());

                attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
            }
        }

        for (String key : new ArrayList<>(attributes.keySet())) {
            attributes.put(key, ModuleConfig.clampAttributeTotal(key, attributes.get(key)));
        }

        return attributes;
    }

    // ========== 赤毒武器元素伤害 / Kuva Weapon Element Damage ==========

    /**
     * 获取赤毒武器自带的元素伤害值
     * Get Kuva weapon's innate element damage value
     *
     * @param weapon 赤毒武器
     * @return 元素伤害百分比（例如35级 = 0.35即35%）
     */
    private static double getKuvaWeaponElementDamage(ItemStack weapon) {
        if (!KuvaWeapon.hasType(weapon)) {
            return 0.0;
        }
        int number = KuvaWeapon.getNumber(weapon);
        return number / 100.0;
    }

    // ========== Tooltip ==========

    /**
     * 物品提示事件 - 显示武器最终面板属性
     * Item tooltip event - display weapon final stats
     *
     * 注意：Tooltip 使用原始属性值（不走 clamp 缓存），与运行时计算逻辑不同
     * Note: Tooltip uses raw attribute values (no clamp cache), different from runtime calculation
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        if (hasBase(itemStack)) {
            List<Component> tooltip = evt.getToolTip();
            List<ItemStack> modules = getModules(itemStack);
            int index = 1;

            // 显示Forma锁定提示 / Show Forma lock indicator
            if (isFormaLocked(itemStack)) {
                tooltip.add(index++, Component.translatable("item.kuvalich.forma_locked")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            }

            if (modules.size() > 0) {
                HashMap<String, Double> attributes = new HashMap<>();
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                tooltip.add(index++, Component.literal(I18n.get("item.module")).withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.module.damage") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("meleeDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.meleeDamage") + " ").append(Component.literal((int) (attributes.get("meleeDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("remoteDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.remoteDamage") + " ").append(Component.literal((int) (attributes.get("remoteDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("arrowDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.arrowDamage") + " ").append(Component.literal((int) (attributes.get("arrowDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("projectileDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.projectileDamage") + " ").append(Component.literal((int) (attributes.get("projectileDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("magicDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.magicDamage") + " ").append(Component.literal((int) (attributes.get("magicDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.baseDamageWhenNotCriticalStrike") + " ").append(Component.literal((int) (attributes.get("baseDamageWhenNotCriticalStrike") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackRange") + " ").append(Component.literal(String.format("%.1f", attributes.get("attackRange")) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bursting_radius", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bursting_radius") + " ").append(Component.literal(String.format("%.1f", 1 + attributes.get("bursting_radius") * 2) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackSpeed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackSpeed") + " ").append(Component.literal(String.format("%.1f", attributes.get("attackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("firing_rate", 0.0) != 0) {
                    if (itemStack.getItem() instanceof BowItem) {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) (attributes.get("firing_rate") * 2 * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    } else {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) (attributes.get("firing_rate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    }
                }

                double baseCriticalStrikeProbability = getBaseAttribute(itemStack, "criticalStrikeProbability");
                double meleeCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
                double remoteCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeProbability") + " ").append(Component.literal((int) (meleeCriticalStrikeProbability * 100) + "% / " + (int) (remoteCriticalStrikeProbability * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                double baseCriticalStrikeMultiplier = getBaseAttribute(itemStack, "criticalStrikeMultiplier");
                double meleeCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
                double remoteCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeMultiplier") + " ").append(Component.literal("x" + String.format("%.1f", meleeCriticalStrikeMultiplier) + " / x" + String.format("%.1f", remoteCriticalStrikeMultiplier)).withStyle(net.minecraft.ChatFormatting.GRAY)));

                if (attributes.getOrDefault("multishot", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.multishot") + " ").append(Component.literal((int) (attributes.get("multishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                tooltip.add(index++, Component.literal(I18n.get("item.module.triggerChance") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "triggerChance") * (1 + attributes.getOrDefault("triggerChance", 0.0)) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("triggerTime", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerTime") + " ").append(Component.literal((int) ((1 + attributes.get("triggerTime")) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                if (attributes.getOrDefault("first_bullet_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.first_bullet_damage") + " ").append(Component.literal((int) (attributes.get("first_bullet_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                if (attributes.getOrDefault("bane_of_undefined", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_undefined") + " ").append(Component.literal((int) (attributes.get("bane_of_undefined") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_undead", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_undead") + " ").append(Component.literal((int) (attributes.get("bane_of_undead") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_arthropod", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_arthropod") + " ").append(Component.literal((int) (attributes.get("bane_of_arthropod") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_illager", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_illager") + " ").append(Component.literal((int) (attributes.get("bane_of_illager") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                if (attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashMeleeCriticalStrikeProbability") + " ").append(Component.literal((int) (attributes.get("dashMeleeCriticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("dashAttackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashAttackRange") + " ").append(Component.literal(String.format("%.1f", attributes.get("dashAttackRange")) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("dashTriggerChance", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashTriggerChance") + " ").append(Component.literal((int) (attributes.get("dashTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                if (attributes.getOrDefault("reload_speed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.reload_speed") + " ").append(Component.literal((int) (attributes.get("reload_speed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("magazine_size", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.magazine_size") + " ").append(Component.literal((int) (attributes.get("magazine_size") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("projectile_speed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.projectile_speed") + " ").append(Component.literal((int) (attributes.get("projectile_speed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("recoil_reduction", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.recoil_reduction") + " ").append(Component.literal((int) (attributes.get("recoil_reduction") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                if (attributes.getOrDefault("killStackBaseDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackBaseDamage"), ModConfig.KUVA_LICH.maxStacksBaseDamage.get()) + " ").append(Component.literal((int) (attributes.get("killStackBaseDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackMultishot", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackMultishot"), ModConfig.KUVA_LICH.maxStacksMultishot.get()) + " ").append(Component.literal((int) (attributes.get("killStackMultishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackMeleeCriticalMultiplier", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackMeleeCriticalMultiplier"), ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get()) + " ").append(Component.literal((int) (attributes.get("killStackMeleeCriticalMultiplier") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackTriggerChance", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackTriggerChance"), ModConfig.KUVA_LICH.maxStacksTriggerChance.get()) + " ").append(Component.literal((int) (attributes.get("killStackTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackAttackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackAttackRange"), ModConfig.KUVA_LICH.maxStacksAttackRange.get()) + " ").append(Component.literal(String.format("%.1f", attributes.get("killStackAttackRange")) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackAttackSpeed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackAttackSpeed"), ModConfig.KUVA_LICH.maxStacksAttackSpeed.get()) + " ").append(Component.literal((int) (attributes.get("killStackAttackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackBurstingRadius", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackBurstingRadius"), ModConfig.KUVA_LICH.maxStacksBurstingRadius.get()) + " ").append(Component.literal((int) (attributes.get("killStackBurstingRadius") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackFiringRate", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(String.format(I18n.get("item.module.killStackFiringRate"), ModConfig.KUVA_LICH.maxStacksFiringRate.get()) + " ").append(Component.literal((int) (attributes.get("killStackFiringRate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                double elementDamage = 0;
                if (KuvaWeapon.hasType(itemStack)) {
                    elementDamage += getKuvaWeaponElementDamage(itemStack);
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

                if (elementDamage != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerDamage") + " ").append(Component.literal((int) (elementDamage * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                HashMap<String, String> elements = getTriggerElements(itemStack);
                if (elements.size() > 0) {
                    Component triggerElements = Component.literal(I18n.get("item.module.triggerType") + " ").withStyle(net.minecraft.ChatFormatting.WHITE);
                    for (String element : elements.keySet()) {
                        triggerElements = triggerElements.copy().append(Component.literal(I18n.get("kuvaweapon.type." + element) + elements.get(element) + " ").withStyle(KuvaWeapon.getColor(element), net.minecraft.ChatFormatting.BOLD));
                    }
                    tooltip.add(index++, triggerElements);
                }

                tooltip.add(index++, Component.translatable("kuvaweapon.item_module_info").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
                List<ItemStack> itemStacks = getModules(itemStack);
                for (ItemStack module : itemStacks) {
                    tooltip.add(index++, Component.literal(" - ").append(module.getHoverName()).append(" ").withStyle(net.minecraft.ChatFormatting.WHITE));
                }
            } else {
                tooltip.add(index++, Component.translatable("item.base").withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.base.damage") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeProbability") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "criticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeMultiplier") + " ").append(Component.literal("x" + getBaseAttribute(itemStack, "criticalStrikeMultiplier")).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.triggerChance") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "triggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
            }
        }
    }

    // ========== NBT 读写工具方法 / NBT Utility Methods ==========

    /**
     * 获取武器上装载的所有模组
     */
    public static List<ItemStack> getModules(ItemStack weaponItemStack) {
        List<ItemStack> itemStacks = new ArrayList<>();
        var nbt = weaponItemStack.getTag();
        if (nbt == null) return itemStacks;

        var weaponModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        var itemList = weaponModule.getList("modules", 10);

        for (int i = 0; i < 8; i++) {
            var itemTag = itemList.getCompound(i);
            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty()) {
                itemStacks.add(stack);
            }
        }
        return itemStacks;
    }

    /**
     * 检查物品是否有武器模组基础数据
     */
    public static boolean hasBase(ItemStack itemStack) {
        var nbt = itemStack.getTag();
        return nbt != null && nbt.contains(Reference.MOD_ID + "_weaponModules");
    }

    /**
     * 获取武器基础属性值
     */
    public static double getBaseAttribute(ItemStack itemStack, String attributeType) {
        var nbt = itemStack.getTag();
        if (nbt == null) return 0;
        var kuvalichModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        return kuvalichModule.getDouble(attributeType);
    }

    /**
     * 为武器设置随机的基础属性
     */
    public static void setBaseAttribute(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateTag();
        var weaponModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");

        if (RandomUtil.percentageChance(0.1)) {
            weaponModule.putDouble("damage", RandomUtil.getInt(150, 200) / 100.0);
            weaponModule.putDouble("criticalStrikeProbability", RandomUtil.getInt(40, 60) / 100.0);
            weaponModule.putDouble("criticalStrikeMultiplier", RandomUtil.getInt(30, 40) / 10.0);
            weaponModule.putDouble("triggerChance", RandomUtil.getInt(30, 40) / 100.0);
        } else if (RandomUtil.percentageChance(0.1)) {
            weaponModule.putDouble("damage", RandomUtil.getInt(50, 80) / 100.0);
            weaponModule.putDouble("criticalStrikeProbability", RandomUtil.getInt(5, 10) / 100.0);
            weaponModule.putDouble("criticalStrikeMultiplier", RandomUtil.getInt(12, 15) / 10.0);
            weaponModule.putDouble("triggerChance", RandomUtil.getInt(5, 10) / 100.0);
        } else {
            double damage = RandomUtil.getInt(80, 120) / 100.0;
            if (RandomUtil.percentageChance(60)) {
                damage = 1.0;
            }

            double criticalStrikeProbability = RandomUtil.getInt(10, 25) / 100.0;
            if (RandomUtil.percentageChance(30)) {
                criticalStrikeProbability = RandomUtil.getInt(25, 40) / 100.0;
            }

            double criticalStrikeMultiplier = RandomUtil.getInt(18, 25) / 10.0;
            if (criticalStrikeProbability >= 0.3) {
                criticalStrikeMultiplier = RandomUtil.getInt(25, 30) / 10.0;
                damage = RandomUtil.getInt(80, 90) / 100.0;
            } else if (criticalStrikeProbability <= 0.15) {
                if (RandomUtil.percentageChance(10)) {
                    criticalStrikeMultiplier = RandomUtil.getInt(30, 35) / 10.0;
                    damage = RandomUtil.getInt(70, 80) / 100.0;
                }
            }

            double triggerChance = RandomUtil.getInt(5, 20) / 100.0;
            if (criticalStrikeProbability > 0.3 && RandomUtil.percentageChance(60)) {
                triggerChance = RandomUtil.getInt(1, 5) / 100.0;
            } else if (criticalStrikeProbability <= 0.15 && RandomUtil.percentageChance(60)) {
                criticalStrikeMultiplier = RandomUtil.getInt(25, 30) / 10.0;
                damage = RandomUtil.getInt(110, 120) / 100.0;
            }

            weaponModule.putDouble("damage", damage);
            weaponModule.putDouble("criticalStrikeProbability", criticalStrikeProbability);
            weaponModule.putDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
            weaponModule.putDouble("triggerChance", triggerChance);
        }

        nbt.put(Reference.MOD_ID + "_weaponModules", weaponModule);
        itemStack.setTag(nbt);
    }

    /**
     * 清除武器的基础面板属性（damage/critProb/critMult/triggerChance）
     * 保留模组列表等其他数据不动，供Forma洗面板后重新调用setBaseAttribute生成新值
     *
     * ⚠ 如果物品已被Forma锁定，则拒绝清除，返回false
     *
     * @param itemStack 要清除基础属性的武器
     * @return true=清除成功，false=物品已锁定无法清除
     */
    public static boolean clearBaseAttribute(ItemStack itemStack) {
        // 检查Forma锁定 / Check Forma lock
        if (isFormaLocked(itemStack)) {
            return false;
        }

        var nbt = itemStack.getTag();
        if (nbt == null) {
            return false;
        }

        String key = Reference.MOD_ID + "_weaponModules";
        if (!nbt.contains(key)) {
            return false;
        }

        var weaponModule = nbt.getCompound(key);

        // 只移除四个基础面板键，保留modules等其他数据
        // Only remove the 4 base stat keys, preserve modules and other data
        weaponModule.remove("damage");
        weaponModule.remove("criticalStrikeProbability");
        weaponModule.remove("criticalStrikeMultiplier");
        weaponModule.remove("triggerChance");

        nbt.put(key, weaponModule);
        itemStack.setTag(nbt);
        return true;
    }

    // ========== 公共接口 / Public API ==========

    /**
     * 公共接口：获取武器运行时属性（供外部兼容模组使用）
     *
     * 注意：此方法不使用缓存，因为外部调用方可能不在事件处理tick上下文中
     * Note: This method does NOT use cache, as external callers may not be in event tick context
     *
     * @param weapon 武器物品栈
     * @return 运行时属性 Map（已应用 clamp）
     */
    public static HashMap<String, Double> getWeaponAttributes(ItemStack weapon) {
        return collectItemAttributes(getModules(weapon));
    }

    // ========== 以下为伤害事件和元素系统，业务逻辑100%不变 ==========
    // ========== Below are damage events and element systems, business logic 100% unchanged ==========

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
                if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                    if (isMelee) {
                        processDamage(evt, player, weapon, damageSource, 1, isMelee);
                    } else {
                        processDamage(evt, player, weapon, damageSource, 1.0f, isMelee);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        LivingEntity hurter = evt.getEntity();
        int entityId = hurter.getId();

        // 使用 entityId 查找待显示信息（原为 WeakHashMap<LivingEntity>，现为 ConcurrentHashMap<Integer>）
        // Use entityId to find pending display info
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
                        displayText.append(getElementEmoji(element));
                    }
                    Vec3 position = getRandomDamagePosition(hurter);
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

    private static void processDamage(LivingHurtEvent evt, Player player, ItemStack weapon,
                                      DamageSource damageSource, float attackStrength, boolean isMelee) {
        LivingEntity hurter = evt.getEntity();
        double baseDamage = 1;

        double criticalStrikeProbability = getBaseAttribute(weapon, "criticalStrikeProbability") * 100;
        double criticalStrikeMultiplier = getBaseAttribute(weapon, "criticalStrikeMultiplier");
        double triggerChance = getBaseAttribute(weapon, "triggerChance") * 100;

        if (isMelee) {
            criticalStrikeProbability *= attackStrength;
        }

        // 使用缓存获取武器属性（避免重复 NBT 读取）
        // Use cache to get weapon attributes (avoid repeated NBT reads)
        List<ItemStack> modules = getModules(weapon);
        HashMap<String, Double> attributes = getCachedWeaponAttributes(player, weapon);

        applyKillStackEffects(player, weapon, attributes, hurter);

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

            if (!damageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
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

        double elementDamage = 0;
        if (KuvaWeapon.hasType(weapon)) {
            elementDamage += getKuvaWeaponElementDamage(weapon);
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

        if (getBaseAttribute(weapon, "damage") > 0) {
            if (getBaseAttribute(weapon, "damage") >= 1) {
                baseDamage *= getBaseAttribute(weapon, "damage");
                elementDamage *= getBaseAttribute(weapon, "damage");
            } else {
                baseDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
                elementDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
            }
        }

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

        float originalDamage = evt.getAmount();
        double physicalDamage = originalDamage * baseDamage * baneMultiplier;
        double elementalDamage = originalDamage * elementDamage * baneMultiplier;
        float totalDamage = (float) (physicalDamage + elementalDamage);
        double coreDamage = physicalDamage;

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
                String element = triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
            if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                String element = triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                        coreDamage, attributes, baneMultiplier);
                if (element != null) triggeredElements.add(element);
            }
        } else if (RandomUtil.percentageChance(triggerChance)) {
            String element = triggerElementEffect(damageSource, hurter, player, weapon, triggerTime,
                    coreDamage, attributes, baneMultiplier);
            if (element != null) triggeredElements.add(element);
        }

        totalDamage = Math.max(totalDamage, 0);
        evt.setAmount(totalDamage);

        // 使用 entityId 作为 key（原为 LivingEntity 引用）
        // Use entityId as key (was LivingEntity reference)
        pendingDisplays.computeIfAbsent(hurter.getId(), k -> new ArrayDeque<>())
                .addLast(new DamageDisplayInfo(colorCode, triggeredElements));
    }

    private static void displayDamage(LivingDamageEvent evt, Player player) {
        LivingEntity hurter = evt.getEntity();
        float damage = evt.getAmount();
        if (damage > 0 && !Float.isNaN(damage) && !Float.isInfinite(damage)) {
            String displayText = "§f" + DamagePacket.formatDamage(damage);
            Vec3 position = getRandomDamagePosition(hurter);
            DamagePacket.sendToPlayer((ServerPlayer) player, displayText, position);
        }
    }

    private static void applyKillStackEffects(Player player, ItemStack weapon,
                                              HashMap<String, Double> attributes,
                                              LivingEntity target) {
        if (!ItemModule.hasBase(weapon)) return;

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

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                Player player = evt.player;
                if (player.level().getGameTime() % 20 == 0 && player.isAlive()) {
                    ItemStack weapon = player.getMainHandItem();
                    if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                        // 使用缓存获取武器属性
                        // Use cache to get weapon attributes
                        HashMap<String, Double> attributes = getCachedWeaponAttributes(player, weapon);

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

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            if (evt.getTarget() instanceof LivingEntity) {
                LivingEntity hurter = (LivingEntity) evt.getTarget();
                Player player = evt.getEntity();

                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                    float attackStrength = player.getAttackStrengthScale(0.5F);
                    if (attackStrength <= 0.8F) return;

                    // 使用缓存获取武器属性
                    // Use cache to get weapon attributes
                    HashMap<String, Double> attributes = getCachedWeaponAttributes(player, weapon);

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

    // ========== 元素组合计算 / Element Composition ==========

    public static HashMap<String, String> getTriggerElements(ItemStack weapon) {
        Map<String, Double> elementValues = new LinkedHashMap<>();

        if (KuvaWeapon.hasType(weapon)) {
            String kuvaType = KuvaWeapon.getType(weapon);
            double kuvaValue = getKuvaWeaponElementDamage(weapon);
            elementValues.put(kuvaType, kuvaValue);
        }

        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
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

    private static boolean isElemental(String element) {
        return element.equals("fire") || element.equals("ice") ||
                element.equals("poison") || element.equals("electricity");
    }

    private static boolean isPhysical(String element) {
        return element.equals("slash") || element.equals("puncture") || element.equals("impact");
    }

    private static boolean isCompound(String element) {
        return element.equals("gas") || element.equals("radiation") ||
                element.equals("magnetic") || element.equals("corrosion") ||
                element.equals("explosion") || element.equals("virus");
    }

    private static String getCompoundElement(String first, String second) {
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

    public static String getTriggerElement(ItemStack weapon) {
        HashMap<String, String> elements = getTriggerElements(weapon);
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

    private static double getElementDamageValue(String element, HashMap<String, Double> attributes, ItemStack weapon) {
        double value = 0.0;

        if (KuvaWeapon.hasType(weapon)) {
            String kuvaType = KuvaWeapon.getType(weapon);
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

    private static Vec3 getRandomDamagePosition(LivingEntity entity) {
        double offsetX = (Math.random() - 0.5) * entity.getBbWidth() * 1.2;
        double offsetZ = (Math.random() - 0.5) * entity.getBbWidth() * 1.2;
        return new Vec3(
                entity.getX() + offsetX,
                entity.getY() + entity.getBbHeight() * (-0.2 + Math.random() * 0.4),
                entity.getZ() + offsetZ
        );
    }

    private static String triggerElementEffect(DamageSource damageSource, LivingEntity hurter,
                                               Player attacker, ItemStack itemStack,
                                               double triggerTime, double coreDamage,
                                               HashMap<String, Double> attributes,
                                               double baneMultiplier) {
        String type = getTriggerElement(itemStack);
        if (type == null) return null;

        Level level = hurter.level();
        double elementValue = getElementDamageValue(type, attributes, itemStack);

        switch (type) {
            case "fire": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 1.5;
                else if (hurter.getMobType().equals(MobType.UNDEAD)) typeDamageMultiplier = 0.5;
                if (!DynamicAttributeManager.has(hurter, DynamicAttributes.FIRE)) {
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.FIRE.createInstance((int) (120 * triggerTime), 0));
                } else {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.FIRE);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.FIRE.createInstance((int) (120 * triggerTime), currentLevel));
                }
                hurter.setSecondsOnFire(6);
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier);
                if (dotDamage > 0) {
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;
                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }
                            hurter.setSecondsOnFire(6);
                            hurter.hurt(hurter.damageSources().inFire(), dotDamage);
                            String displayText = "§f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("fire");
                            DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(hurter));
                        }
                    }.start();
                }
                return null;
            }
            case "poison": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ILLAGER)) typeDamageMultiplier = 1.5;
                else if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier);
                if (dotDamage > 0) {
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;
                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }
                            boolean hasShield = hurter.getAbsorptionAmount() > 0;
                            String displayText = "§f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("poison");
                            DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(hurter));
                            if (hasShield) {
                                if (hurter.getHealth() - dotDamage > 0.01f) { EntityLivingUtil.damageHealthDirectly(hurter, dotDamage); }
                                else { EntityLivingUtil.kill(hurter, attacker.damageSources().playerAttack(attacker)); this.cancel(); }
                            } else { hurter.hurt(attacker.damageSources().magic(), dotDamage); }
                        }
                    }.start();
                }
                return null;
            }
            case "ice": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.ICE)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.ICE);
                    int newLevel = Math.min(8, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.ICE.createInstance((int) (120 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.ICE.createInstance((int) (120 * triggerTime), 0)); }
                return "ice";
            }
            case "electricity": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.UNDEAD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                float lightningDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier);
                if (lightningDamage > 0) {
                    net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) { lightning.moveTo(hurter.getX(), hurter.getY(), hurter.getZ()); lightning.setVisualOnly(true); level.addFreshEntity(lightning); }
                    hurter.hurt(level.damageSources().lightningBolt(), lightningDamage);
                    String displayText = "§f" + DamagePacket.formatDamage(lightningDamage) + getElementEmoji("electricity");
                    DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(hurter));
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.ELECTRICITY_PARALYSIS.createInstance((int) (10 * triggerTime), 0));
                }
                return null;
            }
            case "slash": {
                float dotDamage = (float) (0.35 * coreDamage * baneMultiplier);
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.VIRUS)) {
                    int virusLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.VIRUS);
                    double virusMultiplier = 1.0 + (1.0 + Math.min(virusLevel, 9) * 0.25);
                    dotDamage *= (float) virusMultiplier;
                }
                if (dotDamage > 0) {
                    float finalDotDamage = dotDamage;
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;
                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime || hurter.isDeadOrDying()) { this.cancel(); return; }
                            String displayText = "§f" + DamagePacket.formatDamage(finalDotDamage) + getElementEmoji("slash");
                            DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(hurter));
                            if (hurter.getHealth() - finalDotDamage > 0.01f) { EntityLivingUtil.damageHealthDirectly(hurter, finalDotDamage); }
                            else { EntityLivingUtil.kill(hurter, attacker.damageSources().playerAttack(attacker)); this.cancel(); }
                        }
                    }.start();
                }
                return null;
            }
            case "puncture": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.PUNCTURE)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.PUNCTURE);
                    int newLevel = Math.min(3, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.PUNCTURE.createInstance((int) (120 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.PUNCTURE.createInstance((int) (120 * triggerTime), 0)); }
                return "puncture";
            }
            case "impact": {
                double impactValue = attributes.getOrDefault("impact", 0.0);
                if (KuvaWeapon.hasType(itemStack) && KuvaWeapon.getType(itemStack).equals("impact")) { impactValue += getKuvaWeaponElementDamage(itemStack); }
                float knockbackStrength = (float) (impactValue * 3.0);
                if (knockbackStrength > 0) {
                    double dx = hurter.getX() - attacker.getX();
                    double dz = hurter.getZ() - attacker.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 0) { dx = dx / distance; dz = dz / distance; hurter.knockback(knockbackStrength, -dx, -dz); hurter.setDeltaMovement(hurter.getDeltaMovement().add(0, 0.2 * knockbackStrength, 0)); }
                }
                return "impact";
            }
            case "magnetic": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.MAGNETIC)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.MAGNETIC);
                    int newLevel = Math.min(9, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.MAGNETIC.createInstance((int) (120 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.MAGNETIC.createInstance((int) (120 * triggerTime), 0)); }
                return "magnetic";
            }
            case "radiation": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.RADIATION)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.RADIATION);
                    int newLevel = Math.min(9, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.RADIATION.createInstance((int) (240 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.RADIATION.createInstance((int) (240 * triggerTime), 0)); }
                return "radiation";
            }
            case "virus": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.VIRUS)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.VIRUS);
                    int newLevel = Math.min(9, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.VIRUS.createInstance((int) (120 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.VIRUS.createInstance((int) (120 * triggerTime), 0)); }
                return "virus";
            }
            case "corrosion": {
                if (DynamicAttributeManager.has(hurter, DynamicAttributes.CORROSION)) {
                    int currentLevel = DynamicAttributeManager.getAmplifier(hurter, DynamicAttributes.CORROSION);
                    int newLevel = Math.min(9, currentLevel + 1);
                    DynamicAttributeManager.apply(hurter, DynamicAttributes.CORROSION.createInstance((int) (160 * triggerTime), newLevel));
                } else { DynamicAttributeManager.apply(hurter, DynamicAttributes.CORROSION.createInstance((int) (160 * triggerTime), 0)); }
                return "corrosion";
            }
            case "explosion": {
                double armorMultiplier = hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                float explosionDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * armorMultiplier);
                if (explosionDamage > 0) {
                    level.explode(null, hurter.getX(), hurter.getY(), hurter.getZ(), 3.0F, Level.ExplosionInteraction.NONE);
                    hurter.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                    String displayText = "§f" + DamagePacket.formatDamage(explosionDamage) + getElementEmoji("explosion");
                    DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(hurter));
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, 3, e -> !e.equals(hurter) && !e.equals(attacker));
                    for (LivingEntity entity : entities) {
                        entity.hurt(level.damageSources().explosion((Explosion) null), explosionDamage);
                        String aoeDisplayText = "§f" + DamagePacket.formatDamage(explosionDamage) + getElementEmoji("explosion");
                        DamagePacket.sendToPlayer((ServerPlayer) attacker, aoeDisplayText, getRandomDamagePosition(entity));
                    }
                }
                return null;
            }
            case "gas": {
                double typeDamageMultiplier = 1.0;
                if (hurter.getMobType().equals(MobType.ARTHROPOD)) typeDamageMultiplier = 1.5;
                if (hurter.getAbsorptionAmount() > 0) typeDamageMultiplier *= 0.5;
                final float dotDamage = (float) (0.5 * coreDamage * elementValue * baneMultiplier * typeDamageMultiplier);
                final Vec3 gasCenter = new Vec3(hurter.getX(), hurter.getY(), hurter.getZ());
                if (dotDamage > 0) {
                    new SynchronizationTask(20, 20) {
                        private int ticks = 0;
                        @Override
                        public void run() {
                            if (ticks++ >= 6 * triggerTime) { this.cancel(); return; }
                            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                                    new net.minecraft.world.phys.AABB(gasCenter.x - 3, gasCenter.y - 3, gasCenter.z - 3, gasCenter.x + 3, gasCenter.y + 3, gasCenter.z + 3),
                                    e -> !e.equals(attacker) && e.distanceToSqr(gasCenter) <= 9);
                            for (LivingEntity entity : entities) {
                                if (entity.isDeadOrDying()) continue;
                                entity.hurt(attacker.damageSources().magic(), dotDamage);
                                String displayText = "§f" + DamagePacket.formatDamage(dotDamage) + getElementEmoji("gas");
                                DamagePacket.sendToPlayer((ServerPlayer) attacker, displayText, getRandomDamagePosition(entity));
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

    // ========== 弓 / 射速事件 ==========

    @SubscribeEvent
    public static void onLivingEntityUseItemTick(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        ItemStack usingItem = evt.getItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !ItemModule.hasBase(weapon)) return;

        // 使用缓存获取武器属性
        // Use cache to get weapon attributes
        HashMap<String, Double> attributes = getCachedWeaponAttributes(player, weapon);

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

    @SubscribeEvent
    public static void onLivingTickForFiringRate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        if (!entity.isUsingItem()) return;
        ItemStack usingItem = entity.getUseItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !ItemModule.hasBase(weapon)) return;

        // 使用缓存获取武器属性
        // Use cache to get weapon attributes
        HashMap<String, Double> attributes = getCachedWeaponAttributes(player, weapon);

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

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        Player player = evt.getEntity();
        if (!evt.getEntity().level().isClientSide()) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && ItemModule.hasBase(bow)) {
                // 使用缓存获取武器属性
                // Use cache to get weapon attributes
                HashMap<String, Double> attributes = getCachedWeaponAttributes(player, bow);

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

    private static float getBowVelocity(int charge) {
        float f = (float) charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }

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

    private static void applyBowEnchantments(AbstractArrow arrow, ItemStack bow) {
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        int knockback = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (knockback > 0) arrow.setKnockback(knockback);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) { arrow.setSecondsOnFire(100); }
    }

    private static String getElementEmoji(String element) {
        switch (element) {
            case "fire": return "§c🔥";
            case "ice": return "§3❄";
            case "poison": return "§2☠";
            case "electricity": return "§1⚡";
            case "slash": return "§7☾";
            case "puncture": return "§f†";
            case "impact": return "§f🔨";
            case "gas": return "§a\uD83D\uDCA8";
            case "radiation": return "§e☢";
            case "magnetic": return "§b🧲";
            case "corrosion": return "§2🧪";
            case "explosion": return "§4💥";
            case "virus": return "§a🦠";
            default: return "";
        }
    }
}