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

/**
 * 战甲模组数据层
 * 负责模组获取、属性收集与缓存、击杀叠层效果计算
 *
 * ⭐ 模组等级缩放 — 属性值 × (level / maxLevel)
 */
public class WarframeModuleHandler {

    static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection", "electricProtection", "homologousProtection",
            "fallProtection", "shieldRecoveryDelay"
    ));

    static final Set<String> KILL_STACK_PROTECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "killStackFireProtection", "killStackElectricProtection",
            "killStackHomologousProtection", "killStackShieldRecoveryDelay"
    ));

    // ========== 属性缓存系统 / Attribute Cache System ==========

    private static final Map<UUID, HashMap<String, Double>> ATTRIBUTE_CACHE = new HashMap<>();
    private static long attributeCacheTick = -1;

    /**
     * 获取带缓存的玩家战甲属性
     * ⭐ 内部 collectAttributes 已包含等级缩放
     * ⭐ public：供 EndoDropHandler 等跨包类调用
     */
    public static HashMap<String, Double> getCachedAttributes(Player player) {
        long currentTick = player.level().getGameTime();
        if (currentTick != attributeCacheTick) {
            ATTRIBUTE_CACHE.clear();
            attributeCacheTick = currentTick;
        }
        HashMap<String, Double> base = ATTRIBUTE_CACHE.computeIfAbsent(
                player.getUUID(), uuid -> collectAttributes(player));
        return new HashMap<>(base);
    }

    static void cleanupCache(UUID uuid) {
        ATTRIBUTE_CACHE.remove(uuid);
    }

    // ========== 模组获取 / Module Retrieval ==========

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

    // ========== 属性收集 / Attribute Collection ==========

    /**
     * 收集玩家所有模组的运行时属性（区分加算、乘算、固定上限）
     * ⭐ 每个模组的属性值在叠加前会乘以等级缩放倍率
     */
    private static HashMap<String, Double> collectAttributes(Player player) {
        HashMap<String, Double> attributes = new HashMap<>();

        HashMap<String, Double> multiplicativeAttributes = new HashMap<>();
        multiplicativeAttributes.put("fireProtection", 1.0);
        multiplicativeAttributes.put("electricProtection", 1.0);
        multiplicativeAttributes.put("homologousProtection", 1.0);
        multiplicativeAttributes.put("fallProtection", 1.0);
        multiplicativeAttributes.put("shieldRecoveryDelay", 1.0);

        List<ItemStack> modules = getModules(player);

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

    // ========== 击杀叠层效果 / Kill Stack Effects ==========

    /**
     * 应用战甲击杀叠层效果到属性Map
     * ⭐ public：供 EndoDropHandler 等跨包类调用
     */
    public static void applyWarframeKillStackEffects(Player player, HashMap<String, Double> attributes) {
        if (attributes.containsKey("killStackHealth")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HEALTH);
            if (stacks > 0) { attributes.put("health", attributes.getOrDefault("health", 0.0) + attributes.get("killStackHealth") * stacks); }
        }
        if (attributes.containsKey("killStackShield")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD);
            if (stacks > 0) { attributes.put("shield", attributes.getOrDefault("shield", 0.0) + attributes.get("killStackShield") * stacks); }
        }
        if (attributes.containsKey("killStackArmor")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ARMOR);
            if (stacks > 0) { attributes.put("armor", attributes.getOrDefault("armor", 0.0) + attributes.get("killStackArmor") * stacks); }
        }
        if (attributes.containsKey("killStackSprintSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
            if (stacks > 0) { attributes.put("sprintSpeed", attributes.getOrDefault("sprintSpeed", 0.0) + attributes.get("killStackSprintSpeed") * stacks); }
        }
        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
            if (stacks > 0) { attributes.put("shieldRecoveryRate", attributes.getOrDefault("shieldRecoveryRate", 0.0) + attributes.get("killStackShieldRecoveryRate") * stacks); }
        }
        if (attributes.containsKey("killStackResponseRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
            if (stacks > 0) { attributes.put("responseRate", attributes.getOrDefault("responseRate", 0.0) + attributes.get("killStackResponseRate") * stacks); }
        }
        if (attributes.containsKey("killStackItemDropMultiplier")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
            if (stacks > 0) { attributes.put("itemDropMultiplier", attributes.getOrDefault("itemDropMultiplier", 0.0) + attributes.get("killStackItemDropMultiplier") * stacks); }
        }
        if (attributes.containsKey("killStackDiggingSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
            if (stacks > 0) { attributes.put("diggingSpeed", attributes.getOrDefault("diggingSpeed", 0.0) + attributes.get("killStackDiggingSpeed") * stacks); }
        }
        // 乘算属性（击杀叠层抗性）
        if (attributes.containsKey("killStackFireProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
            if (stacks > 0) { attributes.put("fireProtection", attributes.getOrDefault("fireProtection", 1.0) * (1.0 - attributes.get("killStackFireProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackElectricProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
            if (stacks > 0) { attributes.put("electricProtection", attributes.getOrDefault("electricProtection", 1.0) * (1.0 - attributes.get("killStackElectricProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackHomologousProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
            if (stacks > 0) { attributes.put("homologousProtection", attributes.getOrDefault("homologousProtection", 1.0) * (1.0 - attributes.get("killStackHomologousProtection") * stacks)); }
        }
        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
            if (stacks > 0) { attributes.put("shieldRecoveryDelay", attributes.getOrDefault("shieldRecoveryDelay", 1.0) * (1.0 + attributes.get("killStackShieldRecoveryDelay") * stacks)); }
        }
    }

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