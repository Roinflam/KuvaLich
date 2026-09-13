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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器模组数据层
 * 负责NBT读写、属性缓存、Forma锁定、Tooltip显示、公共API
 *
 * <p>⭐ 并发安全修复：{@code WEAPON_ATTRIBUTE_CACHE} 使用 {@link ConcurrentHashMap}。
 * 该 Map 是 static 的、跨玩家共享的，且通过 {@code computeIfAbsent} 与 {@code clear} 并发读写。
 * 使用普通 HashMap 时，在 Mohist 这类混合端上一旦被插件线程或客户端线程触碰，
 * 就可能在扩容时形成链表环，表现为主线程 CPU 100% 且不抛任何异常。
 * {@code weaponCacheTick} 加 {@code volatile} 保证 tick 翻转跨线程可见。</p>
 *
 * <p>⭐ 正确性修复（本次）：缓存键原先<b>只有实体 UUID</b>，
 * 同 tick 内对同一实体传入不同武器（主手 / 副手 / 饰品）会错误返回第一次的结果。
 * 现在缓存条目额外记录武器标识，标识不符视为未命中重新计算。</p>
 *
 * <p>⭐ 性能修复（本次）：Tooltip 原先每帧调用三次 {@link #getModules}
 * （自身一次、{@code getTriggerElements(itemStack)} 内部一次、末尾列模组名一次），
 * 每次都要做 8 个 {@code ItemStack.of()} 的 NBT 反序列化，
 * 即每帧 24 次——悬停武器时纯客户端掉帧。现在只解析一次并全程复用。</p>
 */
@Mod.EventBusSubscriber
public class WeaponModuleHandler {

    /** Forma锁定的NBT键名 */
    private static final String FORMA_LOCK_KEY = Reference.MOD_ID + "_formaLocked";

    // ========== 武器属性缓存系统 / Weapon Attribute Cache System ==========

    /**
     * 缓存条目：属性表 + 对应的武器标识
     *
     * <p>武器标识用 {@link System#identityHashCode(Object)}：
     * 同 tick 内 {@code getMainHandItem()} / {@code getOffhandItem()} 等返回的都是
     * 背包槽中的稳定对象引用，足以区分「主手 vs 副手 vs 饰品」这类不同武器；
     * 且是 O(1) 的，不会像 {@code tag.hashCode()} 那样递归遍历整棵 NBT 树，
     * 完全不给热路径增加开销。</p>
     */
    private static final class CachedWeaponAttributes {
        /** 武器对象标识 */
        final int weaponKey;
        /** 计算好的属性表（基准副本，返回时再拷贝一份给调用方修改）*/
        final HashMap<String, Double> attributes;

        CachedWeaponAttributes(int weaponKey, HashMap<String, Double> attributes) {
            this.weaponKey = weaponKey;
            this.attributes = attributes;
        }
    }

    /**
     * 武器属性每tick缓存（UUID → 缓存条目）
     */
    private static final Map<UUID, CachedWeaponAttributes> WEAPON_ATTRIBUTE_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存对应的 gameTick
     */
    private static volatile long weaponCacheTick = -1;

    /**
     * 获取带缓存的武器模组属性
     *
     * <p>⭐ 命中条件由「同实体 + 同 tick」收紧为「同实体 + 同 tick + 同武器对象」。</p>
     *
     * @param entity 持有武器的实体
     * @param weapon 武器物品栈
     * @return 属性表副本（调用方可自由修改，不影响缓存）
     */
    static HashMap<String, Double> getCachedWeaponAttributes(LivingEntity entity, ItemStack weapon) {
        long currentTick = entity.level().getGameTime();
        if (currentTick != weaponCacheTick) {
            WEAPON_ATTRIBUTE_CACHE.clear();
            weaponCacheTick = currentTick;
        }

        int weaponKey = System.identityHashCode(weapon);
        UUID entityId = entity.getUUID();

        CachedWeaponAttributes cached = WEAPON_ATTRIBUTE_CACHE.get(entityId);
        if (cached != null && cached.weaponKey == weaponKey) {
            return new HashMap<>(cached.attributes);
        }

        HashMap<String, Double> base = collectItemAttributes(getModules(weapon));
        WEAPON_ATTRIBUTE_CACHE.put(entityId, new CachedWeaponAttributes(weaponKey, base));
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
     * <p>⭐ 每个模组的属性值在叠加前会乘以等级缩放倍率：value × (level / maxLevel)。
     * 系统关闭时倍率为 1.0，效果与原逻辑完全一致。</p>
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
     *
     * <p>⭐ 面板属性收集时已包含等级缩放，显示的是缩放后的数值。</p>
     * <p>⭐ modules 只解析一次并全程复用，不再每帧三次反序列化。</p>
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        if (hasBase(itemStack)) {
            List<Component> tooltip = evt.getToolTip();
            // ⭐ 全方法唯一一次 getModules 调用
            List<ItemStack> modules = getModules(itemStack);
            int index = 1;

            // Forma锁定提示
            if (isFormaLocked(itemStack)) {
                tooltip.add(index++, Component.translatable("item.kuvalich.forma_locked")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
            }

            if (modules.size() > 0) {
                // ⭐ Tooltip 直接复用战斗用的汇总方法：等级缩放、单条上下限、总量上限全部一致。
                //    以前 tooltip 自己累加、不做上下限裁剪，服务端配置了属性上限时面板显示会高于实际生效值
                HashMap<String, Double> attributes = collectItemAttributes(modules);

                // ⭐ 合并额外槽位属性到面板
                ExtraSlotTooltipHelper.mergeExtraSlotIntoAttributes(evt.getEntity(), itemStack, attributes);

                tooltip.add(index++, Component.literal(I18n.get("item.module")).withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.module.damage") + " ").append(Component.literal((int) Math.round(getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("meleeDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.meleeDamage") + " ").append(Component.literal((int) Math.round(attributes.get("meleeDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("remoteDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.remoteDamage") + " ").append(Component.literal((int) Math.round(attributes.get("remoteDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("arrowDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.arrowDamage") + " ").append(Component.literal((int) Math.round(attributes.get("arrowDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("projectileDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.projectileDamage") + " ").append(Component.literal((int) Math.round(attributes.get("projectileDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("magicDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.magicDamage") + " ").append(Component.literal((int) Math.round(attributes.get("magicDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.baseDamageWhenNotCriticalStrike") + " ").append(Component.literal((int) Math.round(attributes.get("baseDamageWhenNotCriticalStrike") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackRange") + " ").append(Component.literal((int) Math.round(attributes.get("attackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bursting_radius", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bursting_radius") + " ").append(Component.literal(String.format("%.1f", 1 + attributes.get("bursting_radius") * 2) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackSpeed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackSpeed") + " ").append(Component.literal(String.format("%.1f", attributes.get("attackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("firing_rate", 0.0) != 0) {
                    if (itemStack.getItem() instanceof BowItem) {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) Math.round(attributes.get("firing_rate") * 2 * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    } else {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) Math.round(attributes.get("firing_rate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    }
                }

                double baseCriticalStrikeProbability = getBaseAttribute(itemStack, "criticalStrikeProbability");
                double meleeCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
                double remoteCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeProbability") + " ").append(Component.literal((int) Math.round(meleeCriticalStrikeProbability * 100) + "% / " + (int) Math.round(remoteCriticalStrikeProbability * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                double baseCriticalStrikeMultiplier = getBaseAttribute(itemStack, "criticalStrikeMultiplier");
                double meleeCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
                double remoteCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeMultiplier") + " ").append(Component.literal("x" + String.format("%.1f", meleeCriticalStrikeMultiplier) + " / x" + String.format("%.1f", remoteCriticalStrikeMultiplier)).withStyle(net.minecraft.ChatFormatting.GRAY)));

                if (attributes.getOrDefault("multishot", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.multishot") + " ").append(Component.literal((int) Math.round(attributes.get("multishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                tooltip.add(index++, Component.literal(I18n.get("item.module.triggerChance") + " ").append(Component.literal((int) Math.round(getBaseAttribute(itemStack, "triggerChance") * (1 + attributes.getOrDefault("triggerChance", 0.0)) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("triggerTime", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerTime") + " ").append(Component.literal((int) Math.round((1 + attributes.get("triggerTime")) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("first_bullet_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.first_bullet_damage") + " ").append(Component.literal((int) Math.round(attributes.get("first_bullet_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_undefined", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_undefined") + " ").append(Component.literal((int) Math.round(attributes.get("bane_of_undefined") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_undead", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_undead") + " ").append(Component.literal((int) Math.round(attributes.get("bane_of_undead") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_arthropod", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_arthropod") + " ").append(Component.literal((int) Math.round(attributes.get("bane_of_arthropod") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bane_of_illager", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bane_of_illager") + " ").append(Component.literal((int) Math.round(attributes.get("bane_of_illager") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashMeleeCriticalStrikeProbability") + " ").append(Component.literal((int) Math.round(attributes.get("dashMeleeCriticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("dashAttackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashAttackRange") + " ").append(Component.literal((int) Math.round(attributes.get("dashAttackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("dashTriggerChance", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.dashTriggerChance") + " ").append(Component.literal((int) Math.round(attributes.get("dashTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("reload_speed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.reload_speed") + " ").append(Component.literal((int) Math.round(attributes.get("reload_speed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("magazine_size", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.magazine_size") + " ").append(Component.literal((int) Math.round(attributes.get("magazine_size") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("projectile_speed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.projectile_speed") + " ").append(Component.literal((int) Math.round(attributes.get("projectile_speed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("recoil_reduction", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.recoil_reduction") + " ").append(Component.literal((int) Math.round(attributes.get("recoil_reduction") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("gun_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.gun_damage") + " ").append(Component.literal((int) Math.round(attributes.get("gun_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("headshot_damage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.headshot_damage") + " ").append(Component.literal((int) Math.round(attributes.get("headshot_damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("aim_time", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.aim_time") + " ").append(Component.literal((int) Math.round(attributes.get("aim_time") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("accuracy", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.accuracy") + " ").append(Component.literal((int) Math.round(attributes.get("accuracy") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                // ⭐ 第三批新词条面板显示
                if (attributes.getOrDefault("true_bullet", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.true_bullet") + " ").append(Component.literal((int) Math.round(attributes.get("true_bullet") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("gun_loot_drop", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.gun_loot_drop") + " ").append(Component.literal((int) Math.round(attributes.get("gun_loot_drop") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("execute_threshold", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.execute_threshold") + " ").append(Component.literal((int) Math.round(attributes.get("execute_threshold") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("purge_buff", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.purge_buff") + " ").append(Component.literal((int) Math.round(attributes.get("purge_buff") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
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
                if (attributes.getOrDefault("killStackBaseDamage", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBaseDamage", ModConfig.KUVA_LICH.maxStacksBaseDamage.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackBaseDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackMultishot", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMultishot", ModConfig.KUVA_LICH.maxStacksMultishot.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackMultishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackMeleeCriticalMultiplier", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackMeleeCriticalMultiplier", ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackMeleeCriticalMultiplier") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackTriggerChance", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackTriggerChance", ModConfig.KUVA_LICH.maxStacksTriggerChance.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackTriggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackAttackRange", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackRange", ModConfig.KUVA_LICH.maxStacksAttackRange.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackAttackRange") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackAttackSpeed", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackAttackSpeed", ModConfig.KUVA_LICH.maxStacksAttackSpeed.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackAttackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackBurstingRadius", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackBurstingRadius", ModConfig.KUVA_LICH.maxStacksBurstingRadius.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackBurstingRadius") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }
                if (attributes.getOrDefault("killStackFiringRate", 0.0) != 0) { tooltip.add(index++, Component.literal(I18n.get("item.module.killStackFiringRate", ModConfig.KUVA_LICH.maxStacksFiringRate.get()) + " ").append(Component.literal((int) Math.round(attributes.get("killStackFiringRate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD))); }

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
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerDamage") + " ").append(Component.literal((int) Math.round(elementDamage * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                // ⭐ 复用已解析的 modules，不再重复反序列化
                HashMap<String, String> elements = WeaponElementSystem.getTriggerElements(itemStack, modules);
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
                // ⭐ 复用已解析的 modules，不再第三次调用 getModules
                for (ItemStack module : modules) {
                    tooltip.add(index++, Component.literal(" - ").append(module.getHoverName()).append(" ").withStyle(net.minecraft.ChatFormatting.WHITE));
                }
            } else {
                // 无模组时显示基础面板
                tooltip.add(index++, Component.translatable("item.base").withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.base.damage") + " ").append(Component.literal((int) Math.round(getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeProbability") + " ").append(Component.literal((int) Math.round(getBaseAttribute(itemStack, "criticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeMultiplier") + " ").append(Component.literal("x" + getBaseAttribute(itemStack, "criticalStrikeMultiplier")).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.triggerChance") + " ").append(Component.literal((int) Math.round(getBaseAttribute(itemStack, "triggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
            }
        }
    }
}
