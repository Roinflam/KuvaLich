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
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import java.util.*;

/**
 * 武器模组数据层
 * 负责NBT读写、属性缓存、Forma锁定、Tooltip显示、公共API
 *
 * Weapon Module Data Layer
 * Handles NBT read/write, attribute caching, Forma lock, tooltip display, public API
 */
@Mod.EventBusSubscriber
public class WeaponModuleHandler {

    /** Forma锁定的NBT键名 */
    private static final String FORMA_LOCK_KEY = Reference.MOD_ID + "_formaLocked";

    // ========== 武器属性缓存系统 / Weapon Attribute Cache System ==========

    /**
     * 每tick武器属性缓存（仅缓存 collectItemAttributes 的结果，不含 killStack 效果）
     * key = 实体UUID（同一tick内同一实体的武器不会变化）
     */
    private static final Map<UUID, HashMap<String, Double>> WEAPON_ATTRIBUTE_CACHE = new HashMap<>();

    /** 缓存对应的 gameTick */
    private static long weaponCacheTick = -1;

    /**
     * 获取带缓存的武器模组属性
     * 每tick只执行一次 collectItemAttributes，后续调用返回副本
     *
     * 返回副本的原因：applyKillStackEffects 和 processDamage 会修改 map
     *
     * 已扩展为支持所有 LivingEntity（不再限定 Player）
     *
     * @param entity 持有武器的实体（玩家或怪物等）
     * @param weapon 武器物品栈
     * @return 属性副本（已应用 clamp，未应用 killStack 效果）
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

    /**
     * 检查物品是否被Forma锁定面板
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
     *
     * @param itemStack 要锁定的物品
     */
    public static void setFormaLocked(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateTag();
        nbt.putBoolean(FORMA_LOCK_KEY, true);
        itemStack.setTag(nbt);
    }

    // ========== 运行时属性收集 / Runtime Attribute Collection ==========

    /**
     * 收集武器模组的运行时属性（仅用于伤害、效果等逻辑计算，不用于 Tooltip 展示）
     *
     * 注意：此方法开销较大，事件处理器中应通过 getCachedWeaponAttributes() 调用
     *
     * @param modules 武器装备的模组列表
     * @return 经过约束处理的运行时属性 Map
     */
    private static HashMap<String, Double> collectItemAttributes(List<ItemStack> modules) {
        HashMap<String, Double> attributes = new HashMap<>();

        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
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
     * 清除武器的基础面板属性
     * 保留模组列表等其他数据不动
     *
     * @param itemStack 要清除基础属性的武器
     * @return true=清除成功，false=物品已锁定无法清除
     */
    public static boolean clearBaseAttribute(ItemStack itemStack) {
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
     * 注意：此方法不使用缓存
     *
     * @param weapon 武器物品栈
     * @return 运行时属性 Map（已应用 clamp）
     */
    public static HashMap<String, Double> getWeaponAttributes(ItemStack weapon) {
        return collectItemAttributes(getModules(weapon));
    }

    // ========== Tooltip ==========

    /**
     * 物品提示事件 - 显示武器最终面板属性
     * <p>
     * ⭐ 面板数字仅在查看主手武器时包含额外槽位合并值
     * ⭐ 额外装备加成明细显示在"已装备以下模组"上方
     *
     * 注意：Tooltip 使用原始属性值（不走 clamp 缓存），与运行时计算逻辑不同
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
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                // ⭐ 合并额外槽位属性到面板（仅主手武器，副手/护甲/饰品不合并）
                // ⭐ Merge extra slot attributes (main hand only, others unaffected)
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

                // ===== TACZ 枪械新属性（第二批）Tooltip 显示 =====
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

                // 击杀叠层词条
                if (attributes.getOrDefault("killStackBaseDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBaseDamage", ModConfig.KUVA_LICH.maxStacksBaseDamage.get()) + " ").append(Component.literal((int) (attributes.get("killStackBaseDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackMultishot", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMultishot", ModConfig.KUVA_LICH.maxStacksMultishot.get()) + " ").append(Component.literal((int) (attributes.get("killStackMultishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackMeleeCriticalMultiplier", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMeleeCriticalMultiplier", ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get()) + " ").append(Component.literal((int) (attributes.get("killStackMeleeCriticalMultiplier") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackTriggerChance", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackTriggerChance", ModConfig.KUVA_LICH.maxStacksTriggerChance.get()) + " ").append(Component.literal((int) (attributes.get("killStackTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackAttackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackRange", ModConfig.KUVA_LICH.maxStacksAttackRange.get()) + " ").append(Component.literal((int) (attributes.get("killStackAttackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackAttackSpeed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackSpeed", ModConfig.KUVA_LICH.maxStacksAttackSpeed.get()) + " ").append(Component.literal((int) (attributes.get("killStackAttackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackBurstingRadius", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBurstingRadius", ModConfig.KUVA_LICH.maxStacksBurstingRadius.get()) + " ").append(Component.literal((int) (attributes.get("killStackBurstingRadius") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("killStackFiringRate", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.killStackFiringRate", ModConfig.KUVA_LICH.maxStacksFiringRate.get()) + " ").append(Component.literal((int) (attributes.get("killStackFiringRate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                // ========== 元素显示 ==========
                double elementDamage = 0;
                if (KuvaWeaponUtil.hasType(itemStack)) {
                    elementDamage += WeaponElementSystem.getKuvaWeaponElementDamage(itemStack);
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

                HashMap<String, String> elements = WeaponElementSystem.getTriggerElements(itemStack);
                if (elements.size() > 0) {
                    Component triggerElements = Component.literal(I18n.get("item.module.triggerType") + " ").withStyle(net.minecraft.ChatFormatting.WHITE);
                    for (String element : elements.keySet()) {
                        triggerElements = triggerElements.copy().append(Component.literal(I18n.get("kuvaweapon.type." + element) + elements.get(element) + " ").withStyle(KuvaWeaponUtil.getColor(element), net.minecraft.ChatFormatting.BOLD));
                    }
                    tooltip.add(index++, triggerElements);
                }

                // ⭐ 额外装备槽位加成（在"已装备以下模组"上方，仅主手武器显示）
                // ⭐ Extra slot bonuses (above "Equipped modules", main hand only)
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