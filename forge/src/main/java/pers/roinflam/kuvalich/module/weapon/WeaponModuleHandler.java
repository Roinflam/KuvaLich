package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import java.util.*;

/**
 * 武器模组数据层
 * 负责NBT读写、属性缓存、Forma锁定、Tooltip显示、公共API
 *
 * ⭐ 新增：模组等级缩放 — 属性值 × (level / maxLevel)
 * ⭐ NEW: Module level scaling — attribute value × (level / maxLevel)
 *
 * ⭐ 第三批新词条面板显示：true_bullet / gun_loot_drop / execute_threshold / purge_buff / execute_chance。
 *    其中 execute_chance 数值极小（如 0.01%），用小数格式显示避免取整为 0%。
 */
@Mod.EventBusSubscriber
public class WeaponModuleHandler {

    /** Forma锁定的NBT键名 */
    private static final String FORMA_LOCK_KEY = Reference.MOD_ID + "_formaLocked";

    // ========== 武器属性缓存系统 / Weapon Attribute Cache System ==========

    private static final Map<UUID, HashMap<String, Double>> WEAPON_ATTRIBUTE_CACHE = new HashMap<>();
    private static long weaponCacheTick = -1;

    /**
     * 获取带缓存的武器模组属性
     * ⭐ 内部 collectItemAttributes 已包含等级缩放
     */
    static HashMap<String, Double> getCachedWeaponAttributes(LivingEntity entity, ItemStack weapon) {
        long currentTick = entity.level().getGameTime();
        if (currentTick != weaponCacheTick) {
            WEAPON_ATTRIBUTE_CACHE.clear();
            weaponCacheTick = currentTick;
        }
        HashMap<String, Double> base = WEAPON_ATTRIBUTE_CACHE.computeIfAbsent(
                entity.getUUID(), uuid -> collectItemAttributes(getModules(weapon)));
        return new HashMap<>(base);
    }

    // ========== Forma锁定 / Forma Lock ==========

    public static boolean isFormaLocked(ItemStack itemStack) {
        var nbt = itemStack.getTag();
        if (nbt == null) { return false; }
        return nbt.getBoolean(FORMA_LOCK_KEY);
    }

    public static void setFormaLocked(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateTag();
        nbt.putBoolean(FORMA_LOCK_KEY, true);
        itemStack.setTag(nbt);
    }

    // ========== 运行时属性收集 / Runtime Attribute Collection ==========

    /**
     * 收集武器模组的运行时属性
     *
     * ⭐ 每个模组的属性值在叠加前会乘以等级缩放倍率：
     *    value × (level / maxLevel)
     *    系统关闭时倍率为1.0，效果与原逻辑完全一致。
     *
     * @param modules 武器装备的模组列表
     * @return 经过约束和等级缩放处理的运行时属性 Map
     */
    private static HashMap<String, Double> collectItemAttributes(List<ItemStack> modules) {
        HashMap<String, Double> attributes = new HashMap<>();

        for (ItemStack module : modules) {
            // ⭐ 模组等级缩放：获取当前模组的有效倍率
            double levelMultiplier = ModuleLevelHelper.getEffectiveMultiplier(module);

            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                String key = entry.getKey();
                double value = ModuleConfig.clampAttributeValue(key, entry.getValue());
                // ⭐ 应用等级缩放
                value *= levelMultiplier;
                attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
            }
        }

        for (String key : new ArrayList<>(attributes.keySet())) {
            attributes.put(key, ModuleConfig.clampAttributeTotal(key, attributes.get(key)));
        }

        return attributes;
    }

    // ========== NBT 读写工具方法 / NBT Utility Methods ==========

    public static List<ItemStack> getModules(ItemStack weaponItemStack) {
        List<ItemStack> itemStacks = new ArrayList<>();
        var nbt = weaponItemStack.getTag();
        if (nbt == null) return itemStacks;
        var weaponModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        var itemList = weaponModule.getList("modules", 10);
        for (int i = 0; i < 8; i++) {
            var itemTag = itemList.getCompound(i);
            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty()) { itemStacks.add(stack); }
        }
        return itemStacks;
    }

    public static boolean hasBase(ItemStack itemStack) {
        var nbt = itemStack.getTag();
        return nbt != null && nbt.contains(Reference.MOD_ID + "_weaponModules");
    }

    public static double getBaseAttribute(ItemStack itemStack, String attributeType) {
        var nbt = itemStack.getTag();
        if (nbt == null) return 0;
        var kuvalichModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        return kuvalichModule.getDouble(attributeType);
    }

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
            if (RandomUtil.percentageChance(60)) { damage = 1.0; }
            double criticalStrikeProbability = RandomUtil.getInt(10, 25) / 100.0;
            if (RandomUtil.percentageChance(30)) { criticalStrikeProbability = RandomUtil.getInt(25, 40) / 100.0; }
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

    public static boolean clearBaseAttribute(ItemStack itemStack) {
        if (isFormaLocked(itemStack)) { return false; }
        var nbt = itemStack.getTag();
        if (nbt == null) { return false; }
        String key = Reference.MOD_ID + "_weaponModules";
        if (!nbt.contains(key)) { return false; }
        var weaponModule = nbt.getCompound(key);
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
     * ⭐ 已包含等级缩放（通过 collectItemAttributes）
     */
    public static HashMap<String, Double> getWeaponAttributes(ItemStack weapon) {
        return collectItemAttributes(getModules(weapon));
    }

    // ========== Tooltip ==========

    /**
     * 物品提示事件 - 显示武器最终面板属性
     * ⭐ 面板属性收集时已包含等级缩放，显示的是缩放后的数值
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        if (hasBase(itemStack)) {
            List<Component> tooltip = evt.getToolTip();
            List<ItemStack> modules = getModules(itemStack);
            int index = 1;

            // Forma锁定提示
            if (isFormaLocked(itemStack)) {
                tooltip.add(index++, Component.translatable("item.kuvalich.forma_locked")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            }

            if (modules.size() > 0) {
                HashMap<String, Double> attributes = new HashMap<>();
                // ⭐ Tooltip属性收集也应用等级缩放，与运行时逻辑一致
                for (ItemStack module : modules) {
                    double levelMultiplier = ModuleLevelHelper.getEffectiveMultiplier(module);
                    for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                        double scaledValue = entry.getValue() * levelMultiplier;
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + scaledValue);
                    }
                }

                // ⭐ 合并额外槽位属性到面板
                ExtraSlotTooltipHelper.mergeExtraSlotIntoAttributes(evt.getEntity(), itemStack, attributes);

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
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackRange") + " ").append(Component.literal((int) (attributes.get("attackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
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
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashAttackRange") + " ").append(Component.literal((int) (attributes.get("dashAttackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
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
                if (attributes.getOrDefault("gun_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.gun_damage") + " ").append(Component.literal((int) (attributes.get("gun_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("headshot_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.headshot_damage") + " ").append(Component.literal((int) (attributes.get("headshot_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("aim_time", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.aim_time") + " ").append(Component.literal((int) (attributes.get("aim_time") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("accuracy", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.accuracy") + " ").append(Component.literal((int) (attributes.get("accuracy") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                // ⭐ 第三批新词条面板显示
                if (attributes.getOrDefault("true_bullet", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.true_bullet") + " ").append(Component.literal((int) (attributes.get("true_bullet") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("gun_loot_drop", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.gun_loot_drop") + " ").append(Component.literal((int) (attributes.get("gun_loot_drop") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("execute_threshold", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.execute_threshold") + " ").append(Component.literal((int) (attributes.get("execute_threshold") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("purge_buff", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.purge_buff") + " ").append(Component.literal((int) (attributes.get("purge_buff") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("execute_chance", 0.0) != 0) {
                    // ⭐ 秒杀概率：保留小数避免取整为0%，并去掉末尾多余的0（0.010%→0.01%）
                    String executeChancePercent = String.format("%.3f", attributes.get("execute_chance") * 100);
                    if (executeChancePercent.indexOf('.') >= 0) {
                        executeChancePercent = executeChancePercent.replaceAll("0+$", "").replaceAll("\\.$", "");
                    }
                    tooltip.add(index++, Component.literal(I18n.get("item.module.execute_chance") + " ").append(Component.literal(executeChancePercent + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                // 击杀叠层词条
                if (attributes.getOrDefault("killStackBaseDamage", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBaseDamage", ModConfig.KUVA_LICH.maxStacksBaseDamage.get()) + " ").append(Component.literal((int) (attributes.get("killStackBaseDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackMultishot", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMultishot", ModConfig.KUVA_LICH.maxStacksMultishot.get()) + " ").append(Component.literal((int) (attributes.get("killStackMultishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackMeleeCriticalMultiplier", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMeleeCriticalMultiplier", ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get()) + " ").append(Component.literal((int) (attributes.get("killStackMeleeCriticalMultiplier") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackTriggerChance", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackTriggerChance", ModConfig.KUVA_LICH.maxStacksTriggerChance.get()) + " ").append(Component.literal((int) (attributes.get("killStackTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackAttackRange", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackRange", ModConfig.KUVA_LICH.maxStacksAttackRange.get()) + " ").append(Component.literal((int) (attributes.get("killStackAttackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackAttackSpeed", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackSpeed", ModConfig.KUVA_LICH.maxStacksAttackSpeed.get()) + " ").append(Component.literal((int) (attributes.get("killStackAttackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackBurstingRadius", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBurstingRadius", ModConfig.KUVA_LICH.maxStacksBurstingRadius.get()) + " ").append(Component.literal((int) (attributes.get("killStackBurstingRadius") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackFiringRate", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackFiringRate", ModConfig.KUVA_LICH.maxStacksFiringRate.get()) + " ").append(Component.literal((int) (attributes.get("killStackFiringRate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }

                // 元素显示
                double elementDamage = 0;
                if (KuvaWeaponUtil.hasType(itemStack)) { elementDamage += WeaponElementSystem.getKuvaWeaponElementDamage(itemStack); }
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

                HashMap<String, String> elements = WeaponElementSystem.getTriggerElements(itemStack);
                if (elements.size() > 0) {
                    Component triggerElements = Component.literal(I18n.get("item.module.triggerType") + " ").withStyle(net.minecraft.ChatFormatting.WHITE);
                    for (String element : elements.keySet()) {
                        triggerElements = triggerElements.copy().append(Component.literal(I18n.get("kuvaweapon.type." + element) + elements.get(element) + " ").withStyle(KuvaWeaponUtil.getColor(element), net.minecraft.ChatFormatting.BOLD));
                    }
                    tooltip.add(index++, triggerElements);
                }

                // 额外装备槽位加成
                index += ExtraSlotTooltipHelper.appendExtraSlotTooltip(tooltip, evt.getEntity(), itemStack, index);

                tooltip.add(index++, Component.translatable("kuvaweapon.item_module_info").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
                List<ItemStack> itemStacks = getModules(itemStack);
                for (ItemStack module : itemStacks) {
                    tooltip.add(index++, Component.literal(" - ").append(module.getHoverName()).append(" ").withStyle(net.minecraft.ChatFormatting.WHITE));
                }
            } else {
                // 无模组时显示基础面板
                tooltip.add(index++, Component.translatable("item.base").withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.base.damage") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeProbability") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "criticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeMultiplier") + " ").append(Component.literal("x" + getBaseAttribute(itemStack, "criticalStrikeMultiplier")).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.triggerChance") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "triggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
            }
        }
    }
}
