package pers.roinflam.kuvalich.module.warframe;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.module.KillStackManager;

import java.util.*;

/**
 * 战甲模组数据层
 * 负责模组获取、属性收集与缓存、击杀叠层效果计算
 *
 * Warframe Module Data Layer
 * Handles module retrieval, attribute collection & caching, kill stack effect calculation
 */
public class WarframeModuleHandler {

    /**
     * 乘算属性类型定义
     * 使用乘法叠加，不参与总值 clamp（乘法结构与加法 cap 语义不兼容）
     */
    static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection",
            "electricProtection",
            "homologousProtection",
            "fallProtection",
            "shieldRecoveryDelay"
    ));

    /**
     * 击杀叠层抗性属性
     */
    static final Set<String> KILL_STACK_PROTECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "killStackFireProtection",
            "killStackElectricProtection",
            "killStackHomologousProtection",
            "killStackShieldRecoveryDelay"
    ));

    // ========== 属性缓存系统 / Attribute Cache System ==========

    /**
     * 每tick属性缓存（仅缓存 collectAttributes 的基础结果，不含 killStack 效果）
     */
    private static final Map<UUID, HashMap<String, Double>> ATTRIBUTE_CACHE = new HashMap<>();

    /** 缓存对应的 gameTick */
    private static long attributeCacheTick = -1;

    /**
     * 获取带缓存的玩家战甲属性
     * 每tick只执行一次 collectAttributes，后续调用返回副本
     *
     * @param player 玩家
     * @return 属性副本（未应用 killStack 效果，调用方需自行调用 applyWarframeKillStackEffects）
     */
    static HashMap<String, Double> getCachedAttributes(Player player) {
        long currentTick = player.level().getGameTime();

        if (currentTick != attributeCacheTick) {
            ATTRIBUTE_CACHE.clear();
            attributeCacheTick = currentTick;
        }

        HashMap<String, Double> base = ATTRIBUTE_CACHE.computeIfAbsent(
                player.getUUID(), uuid -> collectAttributes(player));

        return new HashMap<>(base);
    }

    /**
     * 清理指定玩家的缓存（退出时调用）
     */
    static void cleanupCache(UUID uuid) {
        ATTRIBUTE_CACHE.remove(uuid);
    }

    // ========== 模组获取 / Module Retrieval ==========

    /**
     * 获取玩家装备的所有战甲模组
     */
    public static List<ItemStack> getModules(Player player) {
        List<ItemStack> itemStacks = new ArrayList<>();
        WarframeModules warframeModules = player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElse(null);

        if (warframeModules == null) {
            return itemStacks;
        }

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
        if (stack != null && !stack.isEmpty()) {
            list.add(stack);
        }
    }

    // ========== 属性收集 / Attribute Collection ==========

    /**
     * 收集玩家所有模组的运行时属性（区分加算、乘算、固定上限）
     *
     * 约束规则（不修改模组卡 NBT，仅在运行时生效）：
     *   1. 单值 clamp：每个模组的词条原始读取值限制在配置的 [min, max]
     *   2. 总值 clamp：仅对加算属性（非乘算）的叠加总值应用 totalCap
     */
    private static HashMap<String, Double> collectAttributes(Player player) {
        HashMap<String, Double> attributes = new HashMap<>();

        // 初始化乘算属性为1.0（基准值）
        HashMap<String, Double> multiplicativeAttributes = new HashMap<>();
        multiplicativeAttributes.put("fireProtection", 1.0);
        multiplicativeAttributes.put("electricProtection", 1.0);
        multiplicativeAttributes.put("homologousProtection", 1.0);
        multiplicativeAttributes.put("fallProtection", 1.0);
        multiplicativeAttributes.put("shieldRecoveryDelay", 1.0);

        List<ItemStack> modules = getModules(player);

        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : AbstractModule.getAttributes(module)) {
                String key = entry.getKey();
                double value = ModuleConfig.clampAttributeValue(key, entry.getValue());

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
     * 将击杀叠层的增益应用到战甲属性上
     * 传入的 attributes 是缓存副本，修改不会影响缓存原数据
     */
    static void applyWarframeKillStackEffects(Player player, HashMap<String, Double> attributes) {
        // ========== 加算属性 ==========
        if (attributes.containsKey("killStackHealth")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HEALTH);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHealth");
                attributes.put("health", attributes.getOrDefault("health", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackShield")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShield");
                attributes.put("shield", attributes.getOrDefault("shield", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackArmor")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ARMOR);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackArmor");
                attributes.put("armor", attributes.getOrDefault("armor", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackSprintSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackSprintSpeed");
                attributes.put("sprintSpeed", attributes.getOrDefault("sprintSpeed", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryRate");
                attributes.put("shieldRecoveryRate", attributes.getOrDefault("shieldRecoveryRate", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackResponseRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackResponseRate");
                attributes.put("responseRate", attributes.getOrDefault("responseRate", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackItemDropMultiplier")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackItemDropMultiplier");
                attributes.put("itemDropMultiplier", attributes.getOrDefault("itemDropMultiplier", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackDiggingSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackDiggingSpeed");
                attributes.put("diggingSpeed", attributes.getOrDefault("diggingSpeed", 0.0) + stackValue * stacks);
            }
        }

        // ========== 乘算属性（击杀叠层抗性）==========
        if (attributes.containsKey("killStackFireProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFireProtection");
                attributes.put("fireProtection", attributes.getOrDefault("fireProtection", 1.0) * (1.0 - stackValue * stacks));
            }
        }

        if (attributes.containsKey("killStackElectricProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackElectricProtection");
                attributes.put("electricProtection", attributes.getOrDefault("electricProtection", 1.0) * (1.0 - stackValue * stacks));
            }
        }

        if (attributes.containsKey("killStackHomologousProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHomologousProtection");
                attributes.put("homologousProtection", attributes.getOrDefault("homologousProtection", 1.0) * (1.0 - stackValue * stacks));
            }
        }

        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryDelay");
                attributes.put("shieldRecoveryDelay", attributes.getOrDefault("shieldRecoveryDelay", 1.0) * (1.0 + stackValue * stacks));
            }
        }
    }

    /**
     * 击杀时添加战甲叠层（仅检测 key 存在性，不需要精确数值）
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

        if (attributes.containsKey("killStackHealth"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HEALTH);
        if (attributes.containsKey("killStackShield"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD);
        if (attributes.containsKey("killStackArmor"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ARMOR);
        if (attributes.containsKey("killStackSprintSpeed"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
        if (attributes.containsKey("killStackShieldRecoveryRate"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
        if (attributes.containsKey("killStackShieldRecoveryDelay"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
        if (attributes.containsKey("killStackFireProtection"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
        if (attributes.containsKey("killStackElectricProtection"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
        if (attributes.containsKey("killStackHomologousProtection"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
        if (attributes.containsKey("killStackResponseRate"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
        if (attributes.containsKey("killStackItemDropMultiplier"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
        if (attributes.containsKey("killStackDiggingSpeed"))
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
    }
}
