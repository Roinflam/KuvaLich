package pers.roinflam.kuvalich.module.warframe;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * 战甲模组数据层
 * 负责模组获取、属性收集与缓存、击杀叠层效果计算
 *
 * ⭐ 重构：客户端/服务端共用同一套属性计算逻辑
 *    服务端从 Capability + KillStackManager 取数据
 *    客户端从 WarframeModuleSyncPacket 同步的缓存取数据
 *    两端调用方代码完全一致，无需区分侧
 */
public class WarframeModuleHandler {

    // ==================== 属性分类常量 ====================

    /** 乘算属性集合（抗性类，使用 1-value 或 1+value 连乘） */
    static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection", "electricProtection", "homologousProtection",
            "fallProtection", "shieldRecoveryDelay"
    ));

    /** 击杀叠层中属于乘算的保护属性 */
    static final Set<String> KILL_STACK_PROTECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "killStackFireProtection", "killStackElectricProtection",
            "killStackHomologousProtection", "killStackShieldRecoveryDelay"
    ));

    /**
     * 战甲相关击杀叠层类型（固定顺序，用于同步包序列化）
     * 新增叠层类型时必须追加到末尾，不可插入中间，否则协议不兼容
     */
    public static final KillStackManager.StackType[] WARFRAME_STACK_TYPES = {
            KillStackManager.StackType.WARFRAME_HEALTH,
            KillStackManager.StackType.WARFRAME_SHIELD,
            KillStackManager.StackType.WARFRAME_ARMOR,
            KillStackManager.StackType.WARFRAME_SPRINT_SPEED,
            KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE,
            KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY,
            KillStackManager.StackType.WARFRAME_FIRE_PROTECTION,
            KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION,
            KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION,
            KillStackManager.StackType.WARFRAME_RESPONSE_RATE,
            KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER,
            KillStackManager.StackType.WARFRAME_DIGGING_SPEED
    };

    // ==================== 服务端属性缓存 ====================

    /** 服务端每tick属性缓存（UUID → 基础属性Map） */
    private static final Map<UUID, HashMap<String, Double>> ATTRIBUTE_CACHE = new HashMap<>();
    /** 缓存对应的tick */
    private static long attributeCacheTick = -1;

    // ==================== 客户端同步缓存 ====================

    /** 客户端缓存的模组列表（非空模组，由同步包更新） */
    private static volatile List<ItemStack> clientSyncedModules = Collections.emptyList();

    /** 客户端缓存的击杀叠层计数（索引对应 WARFRAME_STACK_TYPES，由同步包更新） */
    private static volatile int[] clientSyncedKillStacks = new int[WARFRAME_STACK_TYPES.length];

    /** 客户端缓存的基础属性（同步包到达时清空，首次使用时惰性计算） */
    private static volatile HashMap<String, Double> clientBaseAttrCache = null;

    // ==================== 客户端同步接口（由同步包调用）====================

    /**
     * 同步包到达客户端时调用，更新本地缓存
     * 由 WarframeModuleSyncPacket.handleClient 调用
     *
     * @param modules    非空模组列表（已过滤空槽位、已copy）
     * @param killStacks 击杀叠层计数数组
     */
    public static void onClientSyncReceived(List<ItemStack> modules, int[] killStacks) {
        clientSyncedModules = modules != null ? modules : Collections.emptyList();
        clientSyncedKillStacks = killStacks != null
                ? Arrays.copyOf(killStacks, killStacks.length)
                : new int[WARFRAME_STACK_TYPES.length];
        // 清空属性缓存，下次 getCachedAttributes 时重新计算
        clientBaseAttrCache = null;
    }

    /**
     * 获取客户端缓存的指定叠层计数
     *
     * @param type 叠层类型
     * @return 叠层计数，无数据返回0
     */
    public static int getClientKillStacks(KillStackManager.StackType type) {
        int[] counts = clientSyncedKillStacks;
        for (int i = 0; i < WARFRAME_STACK_TYPES.length; i++) {
            if (WARFRAME_STACK_TYPES[i] == type) {
                return i < counts.length ? counts[i] : 0;
            }
        }
        return 0;
    }

    /**
     * 清理客户端缓存（退出世界时调用）
     */
    public static void clearClientCache() {
        clientSyncedModules = Collections.emptyList();
        clientSyncedKillStacks = new int[WARFRAME_STACK_TYPES.length];
        clientBaseAttrCache = null;
    }

    // ==================== 属性获取（双端透明）====================

    /**
     * 获取玩家战甲基础属性（不含击杀叠层）
     * 服务端：从 Capability 实时计算 + 每tick缓存
     * 客户端：从同步包缓存的模组数据计算 + 惰性缓存
     *
     * ⭐ 调用方无需区分客户端/服务端，接口完全一致
     *
     * @param player 玩家
     * @return 属性Map的副本
     */
    public static HashMap<String, Double> getCachedAttributes(Player player) {
        // 客户端：使用同步包缓存数据
        if (player.level().isClientSide()) {
            HashMap<String, Double> cached = clientBaseAttrCache;
            if (cached != null) {
                return new HashMap<>(cached);
            }
            HashMap<String, Double> computed = computeAttributes(clientSyncedModules);
            clientBaseAttrCache = computed;
            return new HashMap<>(computed);
        }

        // 服务端：每tick缓存
        long currentTick = player.level().getGameTime();
        if (currentTick != attributeCacheTick) {
            ATTRIBUTE_CACHE.clear();
            attributeCacheTick = currentTick;
        }
        HashMap<String, Double> base = ATTRIBUTE_CACHE.computeIfAbsent(
                player.getUUID(), uuid -> computeAttributes(getModules(player)));
        return new HashMap<>(base);
    }

    /**
     * 清理服务端指定玩家的属性缓存（退出时调用）
     */
    static void cleanupCache(UUID uuid) {
        ATTRIBUTE_CACHE.remove(uuid);
    }

    // ==================== 模组获取 ====================

    /**
     * 从 Capability 获取玩家已装备的非空模组列表
     *
     * @param player 玩家
     * @return 非空模组列表
     */
    public static List<ItemStack> getModules(Player player) {
        List<ItemStack> itemStacks = new ArrayList<>();
        WarframeModules warframeModules = player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElse(null);
        if (warframeModules == null) { return itemStacks; }
        addIfValid(itemStacks, warframeModules.getOne());
        addIfValid(itemStacks, warframeModules.getTwo());
        addIfValid(itemStacks, warframeModules.getThree());
        addIfValid(itemStacks, warframeModules.getFour());
        addIfValid(itemStacks, warframeModules.getFive());
        addIfValid(itemStacks, warframeModules.getSix());
        addIfValid(itemStacks, warframeModules.getSeven());
        addIfValid(itemStacks, warframeModules.getEight());
        return itemStacks;
    }

    private static void addIfValid(List<ItemStack> list, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) { list.add(stack); }
    }

    // ==================== 属性计算（纯函数，双端共用）====================

    /**
     * 从模组列表计算战甲基础属性（不含击杀叠层）
     * 纯计算函数，无副作用，服务端和客户端使用完全相同的逻辑
     *
     * ⭐ 区分加算、乘算、固定上限三种属性类型
     * ⭐ 每个模组的属性值在叠加前会乘以等级缩放倍率
     *
     * @param modules 非空模组列表
     * @return 计算后的属性Map
     */
    public static HashMap<String, Double> computeAttributes(List<ItemStack> modules) {
        HashMap<String, Double> attributes = new HashMap<>();

        // 乘算属性初始值（连乘，初始1.0）
        HashMap<String, Double> multiplicativeAttributes = new HashMap<>();
        multiplicativeAttributes.put("fireProtection", 1.0);
        multiplicativeAttributes.put("electricProtection", 1.0);
        multiplicativeAttributes.put("homologousProtection", 1.0);
        multiplicativeAttributes.put("fallProtection", 1.0);
        multiplicativeAttributes.put("shieldRecoveryDelay", 1.0);

        for (ItemStack module : modules) {
            // ⭐ 模组等级缩放
            double levelMultiplier = ModuleLevelHelper.getEffectiveMultiplier(module);

            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                String key = entry.getKey();
                double value = ModuleConfig.clampAttributeValue(key, entry.getValue());
                // ⭐ 应用等级缩放
                value *= levelMultiplier;

                if (MULTIPLICATIVE_ATTRIBUTES.contains(key)) {
                    if (key.equals("shieldRecoveryDelay")) {
                        multiplicativeAttributes.put(key, multiplicativeAttributes.get(key) * (1.0 + value));
                    } else {
                        multiplicativeAttributes.put(key, multiplicativeAttributes.get(key) * (1.0 - value));
                    }
                } else if (key.equals("fixedHealth") || key.equals("fixedShield") || key.equals("fixedArmor")) {
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                } else if (KILL_STACK_PROTECTION_ATTRIBUTES.contains(key)) {
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                } else {
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                }
            }
        }

        // 总值 clamp（仅加算属性）
        for (String key : new ArrayList<>(attributes.keySet())) {
            attributes.put(key, ModuleConfig.clampAttributeTotal(key, attributes.get(key)));
        }

        // 合并乘算属性
        attributes.putAll(multiplicativeAttributes);
        return attributes;
    }

    // ==================== 击杀叠层效果（双端透明）====================

    /**
     * 应用战甲击杀叠层效果到属性Map
     * 服务端：从 KillStackManager 读取实时叠层计数
     * 客户端：从同步包缓存读取叠层计数
     *
     * ⭐ 调用方无需区分客户端/服务端，接口完全一致
     *
     * @param player     玩家
     * @param attributes 属性Map（会被原地修改）
     */
    public static void applyWarframeKillStackEffects(Player player, HashMap<String, Double> attributes) {
        if (player.level().isClientSide()) {
            // 客户端：使用同步包缓存的叠层数据
            applyKillStackEffectsInternal(attributes,
                    type -> getClientKillStacks(type));
        } else {
            // 服务端：使用 KillStackManager 实时数据
            applyKillStackEffectsInternal(attributes,
                    type -> KillStackManager.getStacks(player, type));
        }
    }

    /**
     * 击杀叠层效果计算核心（双端共用）
     * 通过 stackGetter 函数抽象数据源，服务端和客户端使用不同的数据源但相同的计算逻辑
     *
     * @param attributes  属性Map（会被原地修改）
     * @param stackGetter 叠层计数获取函数
     */
    private static void applyKillStackEffectsInternal(HashMap<String, Double> attributes,
                                                      ToIntFunction<KillStackManager.StackType> stackGetter) {
        // ═══ 加算叠层 ═══
        if (attributes.containsKey("killStackHealth")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_HEALTH);
            if (stacks > 0) { attributes.put("health", attributes.getOrDefault("health", 0.0) + attributes.get("killStackHealth") * stacks); }
        }
        if (attributes.containsKey("killStackShield")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_SHIELD);
            if (stacks > 0) { attributes.put("shield", attributes.getOrDefault("shield", 0.0) + attributes.get("killStackShield") * stacks); }
        }
        if (attributes.containsKey("killStackArmor")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_ARMOR);
            if (stacks > 0) { attributes.put("armor", attributes.getOrDefault("armor", 0.0) + attributes.get("killStackArmor") * stacks); }
        }
        if (attributes.containsKey("killStackSprintSpeed")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
            if (stacks > 0) { attributes.put("sprintSpeed", attributes.getOrDefault("sprintSpeed", 0.0) + attributes.get("killStackSprintSpeed") * stacks); }
        }
        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
            if (stacks > 0) { attributes.put("shieldRecoveryRate", attributes.getOrDefault("shieldRecoveryRate", 0.0) + attributes.get("killStackShieldRecoveryRate") * stacks); }
        }
        if (attributes.containsKey("killStackResponseRate")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
            if (stacks > 0) { attributes.put("responseRate", attributes.getOrDefault("responseRate", 0.0) + attributes.get("killStackResponseRate") * stacks); }
        }
        if (attributes.containsKey("killStackItemDropMultiplier")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
            if (stacks > 0) { attributes.put("itemDropMultiplier", attributes.getOrDefault("itemDropMultiplier", 0.0) + attributes.get("killStackItemDropMultiplier") * stacks); }
        }
        if (attributes.containsKey("killStackDiggingSpeed")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
            if (stacks > 0) { attributes.put("diggingSpeed", attributes.getOrDefault("diggingSpeed", 0.0) + attributes.get("killStackDiggingSpeed") * stacks); }
        }

        // ═══ 乘算叠层（保护类）═══
        if (attributes.containsKey("killStackFireProtection")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
            if (stacks > 0) { attributes.put("fireProtection", attributes.getOrDefault("fireProtection", 1.0) * (1.0 - attributes.get("killStackFireProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackElectricProtection")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
            if (stacks > 0) { attributes.put("electricProtection", attributes.getOrDefault("electricProtection", 1.0) * (1.0 - attributes.get("killStackElectricProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackHomologousProtection")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
            if (stacks > 0) { attributes.put("homologousProtection", attributes.getOrDefault("homologousProtection", 1.0) * (1.0 - attributes.get("killStackHomologousProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            int stacks = stackGetter.applyAsInt(KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
            if (stacks > 0) { attributes.put("shieldRecoveryDelay", attributes.getOrDefault("shieldRecoveryDelay", 1.0) * (1.0 + attributes.get("killStackShieldRecoveryDelay") * stacks)); }
        }
    }

    /**
     * 击杀时增加战甲叠层（仅服务端调用）
     * ⭐ 不在此处触发同步，由 WarframeEffectHandler 的击杀逻辑负责
     *    因为 WarframeEffectHandler 知道触发时机且已引用 WarframeModuleSyncPacket
     */
    static void addWarframeKillStacks(Player player) {
        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(player);
        for (ItemStack module : modules) {
            if (module == null || module.isEmpty()) continue;
            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                String key = entry.getKey();
                if (key.startsWith("killStack")) {
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + entry.getValue());
                }
            }
        }
        if (attributes.containsKey("killStackHealth")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HEALTH);
        if (attributes.containsKey("killStackShield")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD);
        if (attributes.containsKey("killStackArmor")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ARMOR);
        if (attributes.containsKey("killStackSprintSpeed")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
        if (attributes.containsKey("killStackShieldRecoveryRate")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
        if (attributes.containsKey("killStackShieldRecoveryDelay")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
        if (attributes.containsKey("killStackFireProtection")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
        if (attributes.containsKey("killStackElectricProtection")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
        if (attributes.containsKey("killStackHomologousProtection")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
        if (attributes.containsKey("killStackResponseRate")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
        if (attributes.containsKey("killStackItemDropMultiplier")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
        if (attributes.containsKey("killStackDiggingSpeed")) KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
    }
}
