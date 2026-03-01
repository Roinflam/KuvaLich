package pers.roinflam.kuvalich.itemstack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Warframe模组系统（1.20.1版本，使用动态属性系统）
 * Warframe Module System (1.20.1 version, using dynamic attribute system)
 *
 * 主要功能：
 * 1. 护盾系统：支持固定护盾和百分比护盾两种模式
 * 2. 生命值/护甲系统：支持固定上限和动态属性两种模式
 * 3. 击杀叠层系统：战甲和武器的击杀增益效果
 * 4. 挖掘距离：通过Forge的BLOCK_REACH和ENTITY_REACH属性实现
 */
@Mod.EventBusSubscriber
public class WarframeModule {

    /**
     * 护盾恢复冷却（UUID → 剩余ticks）
     * Shield recovery cooldown (UUID → remaining ticks)
     */
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

    /**
     * 固定属性 AttributeModifier 的 UUID
     * Fixed attribute AttributeModifier UUIDs
     */
    private static final UUID FIXED_HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final UUID FIXED_ARMOR_MODIFIER_UUID = UUID.fromString("a1b2c3d4-5555-6666-7777-888888888888");

    /**
     * 乘算属性类型定义
     * 这些属性使用乘法叠加，不参与总值 clamp（乘法结构与加法 cap 语义不兼容）
     * Multiplicative attribute types
     * These attributes use multiplicative stacking and do NOT participate in total cap
     * (multiplicative structure is semantically incompatible with additive cap)
     */
    private static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection",
            "electricProtection",
            "homologousProtection",
            "fallProtection",
            "shieldRecoveryDelay"
    ));

    /**
     * 击杀叠层抗性属性
     * Kill stack protection attributes
     */
    private static final Set<String> KILL_STACK_PROTECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "killStackFireProtection",
            "killStackElectricProtection",
            "killStackHomologousProtection",
            "killStackShieldRecoveryDelay"
    ));

    /**
     * 获取玩家装备的所有模组
     * Get all modules equipped by player
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

    /**
     * 添加有效的ItemStack到列表
     * Add valid ItemStack to list
     */
    private static void addIfValid(List<ItemStack> list, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            list.add(stack);
        }
    }

    /**
     * 收集玩家所有模组的运行时属性（区分加算、乘算、固定上限）
     * Collect all module runtime attributes from player (separate additive, multiplicative, fixed cap)
     *
     * 约束规则（不修改模组卡 NBT，仅在运行时生效）：
     * Constraint rules (does NOT modify module NBT, only applied at runtime):
     *   1. 单值 clamp：每个模组的词条原始读取值限制在配置的 [min, max] 范围内
     *      Single value clamp: each module's attribute raw value is clamped to configured [min, max]
     *   2. 总值 clamp：仅对加算属性（非乘算属性）的叠加总值应用 totalCap 上限
     *      Total cap clamp: only applied to ADDITIVE attributes (not multiplicative)
     *      乘算属性（如 fireProtection）使用乘法结构，语义上不适合加法总值 cap
     *      Multiplicative attributes (e.g. fireProtection) use multiplicative structure,
     *      semantically incompatible with additive total cap
     */
    private static HashMap<String, Double> collectAttributes(Player player) {
        HashMap<String, Double> attributes = new HashMap<>();

        // 初始化乘算属性为1.0（基准值）
        // Initialize multiplicative attributes to 1.0 (baseline)
        HashMap<String, Double> multiplicativeAttributes = new HashMap<>();
        multiplicativeAttributes.put("fireProtection", 1.0);
        multiplicativeAttributes.put("electricProtection", 1.0);
        multiplicativeAttributes.put("homologousProtection", 1.0);
        multiplicativeAttributes.put("fallProtection", 1.0);
        multiplicativeAttributes.put("shieldRecoveryDelay", 1.0);

        List<ItemStack> modules = getModules(player);

        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();

                // ── 单值 clamp：不修改卡 NBT，仅限制本次读取值的区间 ──
                // Single value clamp: does NOT modify card NBT, only limits the value read this time
                double value = ModuleConfig.clampAttributeValue(key, entry.getValue());

                if (MULTIPLICATIVE_ATTRIBUTES.contains(key)) {
                    // 乘算属性处理（不参与总值 clamp，乘法结构语义不兼容）
                    // Multiplicative attribute processing (no total cap, incompatible semantics)
                    if (key.equals("shieldRecoveryDelay")) {
                        // 护盾恢复延迟是增加的（越高越慢）
                        // Shield recovery delay is additive (higher = slower)
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 + value));
                    } else {
                        // 抗性是减少的（越高越少伤害）
                        // Resistance is subtractive (higher = less damage)
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 - value));
                    }
                } else if (key.equals("fixedHealth") || key.equals("fixedShield") || key.equals("fixedArmor")) {
                    // 固定上限属性：直接加算
                    // Fixed cap attributes: direct addition
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                } else if (KILL_STACK_PROTECTION_ATTRIBUTES.contains(key)) {
                    // 击杀叠层抗性属性：先收集基础值
                    // Kill stack protection attributes: collect base values first
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                } else {
                    // 加算属性：直接加算
                    // Additive attributes: direct addition
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                }
            }
        }

        // ── 总值 clamp：对加算属性的叠加总值应用上限（乘算属性跳过）──
        // Total cap clamp: apply upper limit to summed additive attributes (multiplicative skipped)
        for (String key : new ArrayList<>(attributes.keySet())) {
            // 乘算属性的相关 killStack key 也参与总值 clamp
            // Kill stack protection keys also participate in total cap
            attributes.put(key, ModuleConfig.clampAttributeTotal(key, attributes.get(key)));
        }

        // 将乘算属性合并到总属性表（乘算属性已在内部处理，无需再 clamp）
        // Merge multiplicative attributes into total attributes (already handled internally)
        attributes.putAll(multiplicativeAttributes);
        return attributes;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Player) {
            // 预留位置：可以在这里初始化玩家数据
            // Reserved: can initialize player data here
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();

            // 情况1：玩家受伤
            // Case 1: Player being hurt
            if (evt.getEntity() instanceof Player) {
                Player player = (Player) evt.getEntity();

                HashMap<String, Double> attributes = collectAttributes(player);
                applyWarframeKillStackEffects(player, attributes);

                // 设置护盾恢复冷却
                // Set shield recovery cooldown
                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    coolding *= 3; // 护盾破碎时冷却时间x3
                }
                cooldingHashMap.put(player.getUUID(), coolding);

                // 应用各种抗性
                // Apply various resistances
                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                    double damageMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
                    double damageMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.getEntity() instanceof Player) {
                    double damageMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                    double damageMultiplier = attributes.getOrDefault("fallProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }
            }

            // 情况2：玩家攻击
            // Case 2: Player attacking
            if (damageSource.getEntity() instanceof Player) {
                Player player = (Player) damageSource.getEntity();

                HashMap<String, Double> attributes = collectAttributes(player);
                applyWarframeKillStackEffects(player, attributes);

                // 设置护盾恢复冷却
                // Set shield recovery cooldown
                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    coolding *= 3;
                }
                cooldingHashMap.put(player.getUUID(), coolding);

                // 击杀检测：如果这次伤害会杀死目标，增加叠层
                // Kill detection: if this damage will kill target, add stacks
                if (evt.getEntity().getHealth() - evt.getAmount() <= 0) {
                    addWarframeKillStacks(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getEntity() instanceof Player) {
            Player player = (Player) evt.getEntity();

            HashMap<String, Double> attributes = collectAttributes(player);
            applyWarframeKillStackEffects(player, attributes);

            Double jumpBoostObj = attributes.get("jumpBoost");

            if (jumpBoostObj != null && jumpBoostObj != 0.0) {
                double jumpBoost = jumpBoostObj;

                // 应用跳跃增益：新速度 = 原速度 * sqrt(1 + jumpBoost)
                // Apply jump boost: newSpeed = originalSpeed * sqrt(1 + jumpBoost)
                player.setDeltaMovement(
                        player.getDeltaMovement().x,
                        player.getDeltaMovement().y * Math.sqrt(1.0 + jumpBoost),
                        player.getDeltaMovement().z
                );

                // 同步到客户端
                // Sync to client
                if (player instanceof ServerPlayer) {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    serverPlayer.connection.send(
                            new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player)
                    );
                }
            }
        }
    }

    /**
     * 应用战甲击杀叠层效果
     * Apply warframe kill stack effects
     *
     * 传入的 attributes 已经过单值 clamp 和总值 clamp，无需再次处理
     * Passed attributes are already single-value and total-cap clamped, no further processing needed
     */
    private static void applyWarframeKillStackEffects(Player player, HashMap<String, Double> attributes) {
        // 加算属性处理
        // Additive attribute processing
        if (attributes.containsKey("killStackHealth")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HEALTH);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHealth");
                double currentHealth = attributes.getOrDefault("health", 0.0);
                attributes.put("health", currentHealth + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackShield")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShield");
                double currentShield = attributes.getOrDefault("shield", 0.0);
                attributes.put("shield", currentShield + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackArmor")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ARMOR);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackArmor");
                double currentArmor = attributes.getOrDefault("armor", 0.0);
                attributes.put("armor", currentArmor + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackSprintSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackSprintSpeed");
                double currentSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                attributes.put("sprintSpeed", currentSpeed + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryRate");
                double currentRate = attributes.getOrDefault("shieldRecoveryRate", 0.0);
                attributes.put("shieldRecoveryRate", currentRate + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackResponseRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackResponseRate");
                double currentRate = attributes.getOrDefault("responseRate", 0.0);
                attributes.put("responseRate", currentRate + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackItemDropMultiplier")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackItemDropMultiplier");
                double currentMultiplier = attributes.getOrDefault("itemDropMultiplier", 0.0);
                attributes.put("itemDropMultiplier", currentMultiplier + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackDiggingSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackDiggingSpeed");
                double currentSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
                attributes.put("diggingSpeed", currentSpeed + stackValue * stacks);
            }
        }

        // 乘算属性处理（击杀叠层抗性）
        // Multiplicative attribute processing (kill stack resistances)
        if (attributes.containsKey("killStackFireProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFireProtection");
                double totalStackProtection = stackValue * stacks;
                double baseMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                attributes.put("fireProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        if (attributes.containsKey("killStackElectricProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackElectricProtection");
                double totalStackProtection = stackValue * stacks;
                double baseMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                attributes.put("electricProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        if (attributes.containsKey("killStackHomologousProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHomologousProtection");
                double totalStackProtection = stackValue * stacks;
                double baseMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                attributes.put("homologousProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryDelay");
                double totalStackDelay = stackValue * stacks;
                double baseMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                attributes.put("shieldRecoveryDelay", baseMultiplier * (1.0 + totalStackDelay));
            }
        }
    }

    /**
     * 战甲击杀时添加叠层
     * Add warframe kill stacks
     *
     * 此处仅检测是否拥有对应 killStack 属性的模组，不需要读取具体数值，
     * 所以直接读卡原始值即可（不影响叠层触发逻辑）
     * Only checks if player has modules with killStack attributes; raw values are fine here
     * as we only need key existence, not the actual amount
     */
    private static void addWarframeKillStacks(Player player) {
        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(player);

        // 收集所有击杀叠层属性（仅用于判断 key 是否存在，值大小不影响此处逻辑）
        // Collect all kill stack attributes (only for key existence check)
        for (ItemStack module : modules) {
            if (module == null || module.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();
                if (key.startsWith("killStack")) {
                    double oldValue = attributes.getOrDefault(key, 0.0);
                    double newValue = oldValue + entry.getValue();
                    attributes.put(key, newValue);
                }
            }
        }

        // 根据拥有的击杀叠层属性添加叠层
        // Add stacks based on possessed kill stack attributes
        if (attributes.containsKey("killStackHealth")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HEALTH);
        }
        if (attributes.containsKey("killStackShield")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD);
        }
        if (attributes.containsKey("killStackArmor")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ARMOR);
        }
        if (attributes.containsKey("killStackSprintSpeed")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
        }
        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
        }
        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
        }
        if (attributes.containsKey("killStackFireProtection")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
        }
        if (attributes.containsKey("killStackElectricProtection")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
        }
        if (attributes.containsKey("killStackHomologousProtection")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
        }
        if (attributes.containsKey("killStackResponseRate")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
        }
        if (attributes.containsKey("killStackItemDropMultiplier")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
        }
        if (attributes.containsKey("killStackDiggingSpeed")) {
            KillStackManager.addStack(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getSource().getEntity() instanceof Player) {
            if (evt.getEntity() instanceof Animal || evt.getEntity() instanceof Monster) {
                Player player = (Player) evt.getSource().getEntity();

                HashMap<String, Double> attributes = collectAttributes(player);
                applyWarframeKillStackEffects(player, attributes);

                // 应用掉落物倍率
                // Apply item drop multiplier
                double itemDropMultiplier = 1 + attributes.getOrDefault("itemDropMultiplier", 0.0);
                Collection<ItemEntity> drops = evt.getDrops();
                for (ItemEntity drop : drops) {
                    ItemStack dropStack = drop.getItem();
                    // 不影响装备掉落
                    // Don't affect equipment drops
                    if (!(dropStack.getItem() instanceof ArmorItem) &&
                            !(dropStack.getItem() instanceof SwordItem) &&
                            !(dropStack.getItem() instanceof TieredItem)) {
                        dropStack.setCount((int) (dropStack.getCount() * itemDropMultiplier));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getEntity() instanceof Player) {
            Player player = (Player) evt.getEntity();

            HashMap<String, Double> attributes = collectAttributes(player);
            applyWarframeKillStackEffects(player, attributes);

            // 应用恢复倍率
            // Apply heal multiplier
            double responseRate = 1 + attributes.getOrDefault("responseRate", 0.0);
            if (responseRate <= 0) {
                evt.setCanceled(true); // 恢复倍率为负时取消治疗
            } else {
                evt.setAmount((float) (evt.getAmount() * responseRate));
            }
        }
    }

    /**
     * 挖掘速度事件处理（使用 LOWEST 优先级确保最后执行）
     * Break speed event handler (using LOWEST priority to ensure last execution)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        Player player = evt.getEntity();

        // 客户端：优先使用网络包缓存的数据
        // Client: Prefer cached data from network packet
        if (player.level().isClientSide()) {
            float cachedIncrement = DiggingSpeedPacket.getDiggingSpeedIncrement(player.getUUID());

            // 应用速度增量：newSpeed = currentSpeed * (1 + increment)
            // Apply speed increment: newSpeed = currentSpeed * (1 + increment)
            float speedMultiplier = 1.0f + cachedIncrement;
            float finalSpeed = evt.getNewSpeed() * speedMultiplier;

            evt.setNewSpeed(finalSpeed);
            return;
        }

        // 服务端：从属性计算（collectAttributes 已应用 clamp）
        // Server: Calculate from attributes (collectAttributes already applied clamp)
        HashMap<String, Double> attributes = collectAttributes(player);
        applyWarframeKillStackEffects(player, attributes);

        double diggingSpeed = attributes.getOrDefault("diggingSpeed", 0.0);

        // 应用速度增量
        // Apply speed increment
        float speedMultiplier = (float) (1.0 + diggingSpeed);
        float finalSpeed = evt.getNewSpeed() * speedMultiplier;

        evt.setNewSpeed(finalSpeed);
    }

    /**
     * 应用固定生命值上限
     * Apply fixed health cap
     *
     * 重要：当fixedHealth > 0时，会覆盖所有其他生命值加成
     * Important: When fixedHealth > 0, it overrides all other health bonuses
     */
    private static void applyFixedHealthCap(Player player, double fixedHealth) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) return;

        // 移除旧的固定上限修改器
        // Remove old fixed cap modifier
        AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
        if (oldModifier != null) {
            maxHealthAttribute.removeModifier(oldModifier);
        }

        if (fixedHealth > 0) {
            // 确保生命值至少为1
            // Ensure health is at least 1
            double cappedHealth = Math.max(1.0, fixedHealth);
            double baseHealth = maxHealthAttribute.getBaseValue();
            double operation = cappedHealth - baseHealth;

            // 添加新的固定上限修改器
            // Add new fixed cap modifier
            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_HEALTH_MODIFIER_UUID,
                    "Warframe Fixed Health Cap",
                    operation,
                    AttributeModifier.Operation.ADDITION
            );

            maxHealthAttribute.addPermanentModifier(newModifier);

            // 关键修复：如果当前生命值超过上限，将其限制在上限内
            // Critical fix: If current health exceeds cap, limit it to cap
            if (player.getHealth() > cappedHealth) {
                player.setHealth((float) cappedHealth);
            }
        }
    }

    /**
     * 应用固定护甲上限
     * Apply fixed armor cap
     *
     * 重要：当fixedArmor > 0时，会覆盖所有其他护甲加成
     * Important: When fixedArmor > 0, it overrides all other armor bonuses
     */
    private static void applyFixedArmorCap(Player player, double fixedArmor) {
        AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
        if (armorAttribute == null) return;

        // 移除旧的固定上限修改器
        // Remove old fixed cap modifier
        AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
        if (oldModifier != null) {
            armorAttribute.removeModifier(oldModifier);
        }

        if (fixedArmor > 0) {
            double cappedArmor = Math.max(0.0, fixedArmor);
            double currentTotalArmor = armorAttribute.getValue();
            double operation = cappedArmor - currentTotalArmor;

            // 添加新的固定上限修改器
            // Add new fixed cap modifier
            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_ARMOR_MODIFIER_UUID,
                    "Warframe Fixed Armor Cap",
                    operation,
                    AttributeModifier.Operation.ADDITION
            );

            armorAttribute.addPermanentModifier(newModifier);
        }
    }

    /**
     * 限制生命值不超过当前最大生命值
     * Limit health to not exceed current max health
     *
     * 用途：防止玩家通过先获取生命值加成再移除模组来无限刷生命值
     * Purpose: Prevent infinite health exploit by gaining health bonus then removing modules
     */
    private static void limitHealthToMax(Player player) {
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();

        if (currentHealth > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull Player player = evt.player;
                if (player.isAlive()) {

                    // ═══════════════════════════════════════════════════════════════
                    // 每秒处理：护盾恢复系统
                    // Every second: Shield recovery system
                    // ═══════════════════════════════════════════════════════════════
                    if (player.level().getGameTime() % 20 == 0) {
                        if (cooldingHashMap.containsKey(player.getUUID())) {
                            // 冷却中，减少计数
                            // In cooldown, decrease counter
                            if (cooldingHashMap.get(player.getUUID()) > 1) {
                                cooldingHashMap.put(player.getUUID(),
                                        cooldingHashMap.get(player.getUUID()) - 1);
                            } else {
                                cooldingHashMap.remove(player.getUUID());
                            }
                        } else {
                            // 冷却结束，开始恢复护盾（collectAttributes 内已应用 clamp）
                            // Cooldown ended, start recovering shield (clamp applied inside collectAttributes)
                            HashMap<String, Double> attributes = collectAttributes(player);
                            applyWarframeKillStackEffects(player, attributes);

                            double shield = attributes.getOrDefault("shield", 0.0);
                            double fixedShield = attributes.getOrDefault("fixedShield", 0.0);

                            if (fixedShield > 0) {
                                // ═══ 固定护盾模式 ═══
                                // Fixed shield mode
                                double cappedShield = Math.max(0.0, fixedShield);
                                if (cappedShield > 0) {
                                    float currentShield = player.getAbsorptionAmount();
                                    if (currentShield < cappedShield) {
                                        // 护盾未满，恢复护盾
                                        // Shield not full, recover shield
                                        double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                        player.setAbsorptionAmount((float) Math.min(
                                                cappedShield,
                                                currentShield + cappedShield * 0.01 * shieldRecoveryRate
                                        ));
                                    } else if (currentShield > cappedShield) {
                                        // 护盾超过上限，限制到上限
                                        // Shield exceeds cap, limit to cap
                                        player.setAbsorptionAmount((float) cappedShield);
                                    }
                                }
                            } else if (shield > 0) {
                                // ═══ 百分比护盾模式 ═══
                                // Percentage shield mode
                                // 护盾上限 = 最大生命值 * shield属性值 / 2
                                // Shield cap = max health * shield value / 2
                                double shieldCap = player.getMaxHealth() * shield / 2;
                                float currentShield = player.getAbsorptionAmount();

                                if (currentShield < shieldCap) {
                                    // 护盾未满，恢复护盾
                                    // Shield not full, recover shield
                                    double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                    player.setAbsorptionAmount((float) Math.min(
                                            shieldCap,
                                            currentShield + shieldCap * 0.01 * shieldRecoveryRate
                                    ));
                                } else if (currentShield > shieldCap) {
                                    // 关键修复：护盾超过上限时，限制到上限
                                    // Critical fix: When shield exceeds cap, limit to cap
                                    player.setAbsorptionAmount((float) shieldCap);
                                }
                            }
                        }
                    }

                    // ═══════════════════════════════════════════════════════════════
                    // 每0.25秒处理：属性效果 + 挖掘速度同步 + 固定上限
                    // Every 0.25s: Attribute effects + digging speed sync + fixed caps
                    // ═══════════════════════════════════════════════════════════════
                    if (player.level().getGameTime() % 5 == 0) {
                        // collectAttributes 内已应用单值 clamp + 总值 clamp
                        // collectAttributes already applies single value clamp + total cap clamp
                        HashMap<String, Double> attributes = collectAttributes(player);
                        applyWarframeKillStackEffects(player, attributes);

                        // ═══ 生命值系统 ═══
                        // Health system
                        double fixedHealth = attributes.getOrDefault("fixedHealth", 0.0);
                        if (fixedHealth > 0) {
                            // 使用固定生命值上限
                            // Use fixed health cap
                            applyFixedHealthCap(player, fixedHealth);
                        } else {
                            // 移除固定生命值修改器（如果存在）
                            // Remove fixed health modifier (if exists)
                            AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
                            if (maxHealthAttribute != null) {
                                AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
                                if (oldModifier != null) {
                                    maxHealthAttribute.removeModifier(oldModifier);
                                }
                            }

                            // 使用动态属性系统
                            // Use dynamic attribute system
                            double health = attributes.getOrDefault("health", 0.0);
                            if (health >= 0.1) {
                                int level = (int) (health / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.HEALTH.createInstance(6, level));
                            } else if (health <= -0.1) {
                                int level = (int) (-health / 0.1) - 1;
                                level = Math.min(level, 8);
                                DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_HEALTH.createInstance(6, level));
                            }

                            // 关键修复：使用动态属性时也要限制生命值
                            // Critical fix: Also limit health when using dynamic attributes
                            limitHealthToMax(player);
                        }

                        // ═══ 护甲系统 ═══
                        // Armor system
                        double fixedArmor = attributes.getOrDefault("fixedArmor", 0.0);
                        if (fixedArmor > 0) {
                            // 使用固定护甲上限
                            // Use fixed armor cap
                            applyFixedArmorCap(player, fixedArmor);
                        } else {
                            // 移除固定护甲修改器（如果存在）
                            // Remove fixed armor modifier (if exists)
                            AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
                            if (armorAttribute != null) {
                                AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
                                if (oldModifier != null) {
                                    armorAttribute.removeModifier(oldModifier);
                                }
                            }

                            // 使用动态属性系统
                            // Use dynamic attribute system
                            double armor = attributes.getOrDefault("armor", 0.0);
                            if (armor >= 0.1) {
                                int level = (int) (armor / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.ARMOR.createInstance(6, level));
                            } else if (armor <= -0.1) {
                                int level = (int) (-armor / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_ARMOR.createInstance(6, level));
                            }
                        }

                        // ═══ 其他属性处理 ═══
                        // Other attribute processing
                        double sprintSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                        if (sprintSpeed >= 0.1) {
                            int level = (int) (sprintSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.MOVEMENT_SPEED.createInstance(6, level));
                        } else if (sprintSpeed <= -0.1) {
                            int level = (int) (-sprintSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_MOVEMENT_SPEED.createInstance(6, level));
                        }

                        double knockbackResistance = attributes.getOrDefault("knockbackResistance", 0.0);
                        if (knockbackResistance >= 0.1) {
                            int level = (int) (knockbackResistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.KNOCKBACK_RESISTANCE.createInstance(6, level));
                        } else if (knockbackResistance <= -0.1) {
                            int level = (int) (-knockbackResistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_KNOCKBACK_RESISTANCE.createInstance(6, level));
                        }

                        double reachDistance = attributes.getOrDefault("reachDistance", 0.0);
                        if (reachDistance >= 0.1) {
                            int level = (int) (reachDistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.REACH_DISTANCE.createInstance(6, level));
                        } else if (reachDistance <= -0.1) {
                            int level = (int) (-reachDistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_REACH_DISTANCE.createInstance(6, level));
                        }

                        // ═══ 同步挖掘速度到客户端 ═══
                        // Sync digging speed to client
                        // 只在值变化时才发包，避免每5tick无意义重复发送
                        // Only send when value changes, avoid meaningless repeated sends every 5 ticks
                        double diggingSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
                        if (player instanceof ServerPlayer) {
                            if (DiggingSpeedPacket.shouldSend(player.getUUID(), (float) diggingSpeed)) {
                                KuvaLich.network.send(
                                        PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                                        new DiggingSpeedPacket((float) diggingSpeed)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 玩家退出时清理缓存，防止内存泄漏
     * Clean up caches when player logs out to prevent memory leak
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        UUID uuid = evt.getEntity().getUUID();
        // 清理护盾冷却缓存
        // Clean shield cooldown cache
        cooldingHashMap.remove(uuid);
        // 清理挖掘速度发送记录
        // Clean digging speed send record
        DiggingSpeedPacket.cleanupPlayer(uuid);
    }
}