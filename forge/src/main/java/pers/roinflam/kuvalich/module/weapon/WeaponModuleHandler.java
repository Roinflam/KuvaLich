package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器模组数据层
 * 负责NBT读写、属性缓存、Forma锁定、公共API
 *
 * <p>⭐ 本类**不再参与 tooltip 渲染**，类级 {@code @Mod.EventBusSubscriber} 也随之移除。
 * 面板的「有哪些词条、怎么算、怎么显示」现在是一张数据表
 * （{@code module.weapon.panel.WeaponPanelCatalog}），渲染在
 * {@code client.tooltip.WeaponPanelComposer}，事件入口统一到
 * {@code client.tooltip.KuvaTooltipCoordinator}。
 * 这么拆还顺带消掉一个隐患：改造前本类的 {@code @Mod.EventBusSubscriber} 没有限定
 * {@code Dist}，而方法体里用了客户端专用的 {@code I18n} ——
 * 专用服务端不炸完全依赖 RuntimeDistCleaner 把带 {@code @OnlyIn} 的方法剥干净，
 * 哪天有人加个不带注解的辅助方法就会炸服。现在数据层里一个客户端类都不 import。</p>
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
     * 武器属性每tick缓存（UUID → 缓存条目），<b>两端各一份</b>
     *
     * <p>⭐ 必须按端分开。单人游戏里客户端与服务端在同一个 JVM，玩家 UUID 两端相同，
     * 而缓存条目里的 {@code weaponKey} 用的是 {@code System.identityHashCode(weapon)} ——
     * 两端拿到的是<b>不同的 ItemStack 对象</b>，identityHashCode 必然不同。
     * 共用一张表的后果是两端互相顶掉对方的条目，缓存命中率归零；
     * 更糟的是任一端 tick 翻转都会 {@code clear()} 整张表，把对端本 tick 的缓存也清掉。</p>
     *
     * <p>返回值本身是对的（键不匹配就重算），所以这不是数据串号，
     * 而是「缓存在它专门为之而写的场景里完全失效」—— 蓄力与射速这条每 tick 双端都跑的
     * 最热路径，等于每 tick 白算两遍。</p>
     */
    private static final Map<UUID, CachedWeaponAttributes> WEAPON_ATTRIBUTE_CACHE_SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedWeaponAttributes> WEAPON_ATTRIBUTE_CACHE_CLIENT = new ConcurrentHashMap<>();

    /**
     * 缓存对应的 gameTick，两端各记一个
     */
    private static volatile long weaponCacheTickServer = -1;
    private static volatile long weaponCacheTickClient = -1;

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
        boolean clientSide = entity.level().isClientSide();
        Map<UUID, CachedWeaponAttributes> cache =
                clientSide ? WEAPON_ATTRIBUTE_CACHE_CLIENT : WEAPON_ATTRIBUTE_CACHE_SERVER;

        long currentTick = entity.level().getGameTime();
        long lastTick = clientSide ? weaponCacheTickClient : weaponCacheTickServer;
        if (currentTick != lastTick) {
            cache.clear();
            if (clientSide) {
                weaponCacheTickClient = currentTick;
            } else {
                weaponCacheTickServer = currentTick;
            }
        }

        int weaponKey = System.identityHashCode(weapon);
        UUID entityId = entity.getUUID();

        CachedWeaponAttributes cached = cache.get(entityId);
        if (cached != null && cached.weaponKey == weaponKey) {
            return new HashMap<>(cached.attributes);
        }

        HashMap<String, Double> base = collectItemAttributes(getModules(weapon));
        cache.put(entityId, new CachedWeaponAttributes(weaponKey, base));
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

    // ========== Tooltip 数据入口 ==========

    /**
     * 公共接口：用已解析好的模组列表算属性（避免重复反序列化）
     *
     * <p>⭐ 面板渲染已迁出本类，见
     * {@code client.tooltip.WeaponPanelComposer} 与 {@code module.weapon.panel.WeaponPanelCatalog}。
     * 改造前这里有一个约 200 行、包含 40 多个重复 {@code if} 块的 {@code onItemTooltip}，
     * 每加一条词条都要复制粘贴一整段；现在那份信息以数据表的形式集中在
     * {@code WeaponPanelCatalog.SPECS} 里，本类只负责 NBT 与属性汇总。</p>
     *
     * @param weapon  武器物品栈
     * @param modules 已解析的模组列表
     * @return 经过等级缩放与上下限裁剪的运行时属性
     */
    public static HashMap<String, Double> getWeaponAttributes(ItemStack weapon, List<ItemStack> modules) {
        return collectItemAttributes(modules);
    }
}
