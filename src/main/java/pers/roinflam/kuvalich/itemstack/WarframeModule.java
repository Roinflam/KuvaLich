package pers.roinflam.kuvalich.itemstack;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Warframe模组系统
 * <p>
 * 提供玩家模组装备系统，包括：
 * - 护盾系统（吸收伤害 + 自动恢复）
 * - 属性加成（生命、护甲、移速、挖掘速度、跳跃高度等）
 * - 固定上限属性（fixedHealth、fixedShield、fixedArmor）- 绝对锁定上限值
 * - 元素抗性（火焰、闪电、同类伤害、摔落伤害）- 依次乘算机制
 * - 掉落增幅
 * - 战甲击杀叠层系统（执刑官系列MOD）
 * <p>
 * 乘算机制：
 * - 多个抗性模组依次相乘：50%抗性 × 30%抗性 = 受到35%伤害（减伤65%）
 * - 击杀叠层抗性同样依次乘算：先加总单层加成，再与基础抗性相乘
 * <p>
 * 固定上限机制（绝对锁定）：
 * - 装备 +3 和 +5 固定生命值模组 → 生命上限绝对锁定为 8 血（不是 20+8）
 * - 固定生命值加总 ≤1 时，锁定为 1 血（最低保护）
 * - 固定护盾/护甲加总 ≤0 时，不生效（最低为0）
 * - 如果没有固定属性模组，则使用原生属性（百分比加成）
 */
@Mod.EventBusSubscriber
public class WarframeModule {

    /**
     * 护盾恢复冷却（UUID -> 剩余ticks）
     * 受伤后进入冷却，冷却期间不恢复护盾
     */
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

    /**
     * 固定属性 AttributeModifier 的 UUID（用于识别和移除）
     */
    private static final UUID FIXED_HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final UUID FIXED_ARMOR_MODIFIER_UUID = UUID.fromString("a1b2c3d4-5555-6666-7777-888888888888");

    /**
     * 乘算属性类型定义
     * 这些属性会依次相乘而不是相加
     */
    private static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection",
            "electricProtection",
            "homologousProtection",
            "fallProtection",
            "shieldRecoveryDelay"
    ));

    /**
     * 击杀叠层抗性属性（需要先加总再乘算）
     */
    private static final Set<String> KILL_STACK_PROTECTION_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "killStackFireProtection",
            "killStackElectricProtection",
            "killStackHomologousProtection",
            "killStackShieldRecoveryDelay"
    ));

    /**
     * 获取玩家装备的所有模组
     */
    public static List<ItemStack> getModules(EntityPlayer entityPlayer) {
        List<ItemStack> itemStacks = new ArrayList<>();
        WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

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
     * 只添加有效的ItemStack
     */
    private static void addIfValid(List<ItemStack> list, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            list.add(stack);
        }
    }

    /**
     * ✅ 收集玩家所有模组属性（区分加算、乘算、固定上限）
     * <p>
     * 加算属性：生命、护甲、移速等 - 直接相加
     * 乘算属性：抗性、延迟等 - 依次相乘
     * 固定上限：fixedHealth、fixedShield、fixedArmor - 直接相加（用于绝对锁定）
     * <p>
     * 示例：
     * - 50%火抗 + 30%火抗 = 1.0 × (1-0.5) × (1-0.3) = 0.35 倍伤害（减伤65%）
     * - +3固定生命 + +5固定生命 = 8点生命上限（绝对锁定为8血）
     */
    private static HashMap<String, Double> collectAttributes(EntityPlayer player) {
        HashMap<String, Double> attributes = new HashMap<>();

        // 乘算属性初始值为1.0（代表100%，即无加成）
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
                double value = entry.getValue();

                if (MULTIPLICATIVE_ATTRIBUTES.contains(key)) {
                    // 乘算属性
                    if (key.equals("shieldRecoveryDelay")) {
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 + value));
                    } else {
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 - value));
                    }
                } else if (key.equals("fixedHealth") || key.equals("fixedShield") || key.equals("fixedArmor")) {
                    // ✅ 固定上限属性：直接相加（用于绝对锁定）
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + value);
                } else if (KILL_STACK_PROTECTION_ATTRIBUTES.contains(key)) {
                    // 击杀叠层抗性：加算
                    attributes.put(key,
                            attributes.getOrDefault(key, 0.0) + value);
                } else {
                    // 普通加算属性
                    attributes.put(key,
                            attributes.getOrDefault(key, 0.0) + value);
                }
            }
        }

        attributes.putAll(multiplicativeAttributes);

        return attributes;
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(@Nonnull EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote && evt.getEntity() instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntity();
            // 预留位置：可以在这里初始化玩家数据
        }
    }

    /**
     * 伤害事件处理
     * 1. 受伤时：计算元素抗性（包括摔落抗性）、触发护盾冷却
     * 2. 攻击时：触发攻击者的护盾冷却（防止边打边回血）
     * 3. ✅ 击杀时：添加战甲叠层
     *
     * ⚠️ 使用 LOWEST 优先级确保在武器伤害计算完成后再检测击杀
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntityLiving().getEntityWorld().isRemote) {
            DamageSource damageSource = evt.getSource();

            // 情况1：玩家受伤 - 计算抗性 + 护盾冷却
            if (evt.getEntityLiving() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();

                HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                applyWarframeKillStackEffects(entityPlayer, attributes);

                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (entityPlayer.getAbsorptionAmount() <= 0) {
                    coolding *= 3;
                }
                cooldingHashMap.put(entityPlayer.getUniqueID(), coolding);

                if (damageSource.isFireDamage()) {
                    double damageMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.equals(DamageSource.LIGHTNING_BOLT)) {
                    double damageMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.getTrueSource() instanceof EntityPlayer) {
                    double damageMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.equals(DamageSource.FALL)) {
                    double damageMultiplier = attributes.getOrDefault("fallProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }
            }

            // 情况2：玩家攻击 - 触发攻击者护盾冷却 + 击杀检测
            if (damageSource.getTrueSource() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) damageSource.getTrueSource();

                HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                applyWarframeKillStackEffects(entityPlayer, attributes);

                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (entityPlayer.getAbsorptionAmount() <= 0) {
                    coolding *= 3;
                }
                cooldingHashMap.put(entityPlayer.getUniqueID(), coolding);

                // ✅ 击杀检测：此时伤害已经被武器系统计算完毕
                if (evt.getEntityLiving().getHealth() - evt.getAmount() <= 0) {
                    addWarframeKillStacks(entityPlayer);
                }
            }
        }
    }

    /**
     * ✅ 跳跃事件处理 - 增加跳跃高度
     * 使用速度包强制同步到客户端
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent evt) {
        if (!evt.getEntity().world.isRemote && evt.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();

            HashMap<String, Double> attributes = collectAttributes(entityPlayer);
            applyWarframeKillStackEffects(entityPlayer, attributes);

            Double jumpBoostObj = attributes.get("jumpBoost");

            if (jumpBoostObj != null && jumpBoostObj != 0.0) {
                double jumpBoost = jumpBoostObj;

                entityPlayer.motionY *= Math.sqrt(1.0 + jumpBoost);

                if (entityPlayer instanceof EntityPlayerMP) {
                    EntityPlayerMP playerMP = (EntityPlayerMP) entityPlayer;
                    playerMP.connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketEntityVelocity(
                                    entityPlayer.getEntityId(),
                                    entityPlayer.motionX,
                                    entityPlayer.motionY,
                                    entityPlayer.motionZ
                            )
                    );
                }
            }
        }
    }

    /**
     * ✅ 应用战甲击杀叠层效果
     * <p>
     * 处理规则：
     * 1. 普通叠层属性（生命、护甲等）：直接加算
     * 2. 击杀叠层抗性：先加总单层加成，乘以层数，然后与基础抗性依次乘算
     * <p>
     * 示例：
     * 装备：执刑官火焰防护（基础+60%，每层+2.5%，20层满）
     * 基础抗性：1.0 × (1-0.6) = 0.4 倍伤害
     * 击杀叠层：0.4 × (1-0.5) = 0.2 倍伤害（减伤80%）
     */
    private static void applyWarframeKillStackEffects(EntityPlayer player, HashMap<String, Double> attributes) {
        // ========== 加算属性 ==========

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

        // ========== 乘算属性（击杀叠层抗性）==========

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
     * ✅ 战甲击杀时添加叠层（带详细日志）
     * 检查玩家装备的模组，为所有包含战甲击杀叠层词条的模组添加对应叠层
     * <p>
     * 注意：多个同词条的单层加成会相加
     * 例如：卡A每层+1%，卡B每层+3% → 实际每层+4%
     */
    private static void addWarframeKillStacks(EntityPlayer player) {
        // 收集所有击杀叠层词条
        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(player);

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

        // 检查并添加各种战甲击杀叠层
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

    /**
     * 掉落增幅
     * 根据模组属性增加掉落物品数量（不包括装备）
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntityLiving().world.isRemote && evt.getSource().getTrueSource() instanceof EntityPlayer) {
            // ✅ 只对动物和怪物应用掉落增幅
            if (evt.getEntityLiving() instanceof EntityAnimal || evt.getEntityLiving() instanceof EntityMob) {
                if (evt.getEntityLiving().isNonBoss()) {
                    EntityPlayer entityPlayer = (EntityPlayer) evt.getSource().getTrueSource();

                    HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                    applyWarframeKillStackEffects(entityPlayer, attributes);

                    double itemDropMultiplier = 1 + attributes.getOrDefault("itemDropMultiplier", 0.0);
                    Collection<EntityItem> drops = evt.getDrops();
                    for (EntityItem drop : drops) {
                        ItemStack dropStack = drop.getItem();
                        if (!(dropStack.getItem() instanceof ItemArmor) &&
                                !(dropStack.getItem() instanceof ItemSword) &&
                                !(dropStack.getItem() instanceof ItemTool)) {
                            dropStack.setCount((int) (dropStack.getCount() * itemDropMultiplier));
                        }
                    }
                }
            }
        }
    }

    /**
     * 治疗增幅
     * 根据模组属性调整治疗效果
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntityLiving().getEntityWorld().isRemote && evt.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();

            HashMap<String, Double> attributes = collectAttributes(entityPlayer);
            applyWarframeKillStackEffects(entityPlayer, attributes);

            double responseRate = 1 + attributes.getOrDefault("responseRate", 0.0);
            if (responseRate <= 0) {
                evt.setCanceled(true);
            } else {
                evt.setAmount((float) (evt.getAmount() * responseRate));
            }
        }
    }

    /**
     * 挖掘速度增幅
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        EntityPlayer entityPlayer = evt.getEntityPlayer();
        if (entityPlayer.swingingHand != null) {
            HashMap<String, Double> attributes = collectAttributes(entityPlayer);
            applyWarframeKillStackEffects(entityPlayer, attributes);

            double diggingSpeed = 1 + DiggingSpeedPacket.getDiggingSpeed(entityPlayer);
            evt.setNewSpeed((float) (evt.getNewSpeed() * diggingSpeed));
        }
    }

    /**
     * ✅ 应用固定生命值上限（绝对锁定）
     * 使用 AttributeModifier 直接修改最大生命值属性
     *
     * @param player 玩家
     * @param fixedHealth 固定生命值（加总后的值）
     */
    private static void applyFixedHealthCap(EntityPlayer player, double fixedHealth) {
        IAttributeInstance maxHealthAttribute = player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);

        // 移除旧的修改器
        AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
        if (oldModifier != null) {
            maxHealthAttribute.removeModifier(oldModifier);
        }

        if (fixedHealth > 0) {
            // ✅ 固定生命值最低为 1
            double cappedHealth = Math.max(1.0, fixedHealth);

            // 计算需要的操作值：目标值 = 基础值 + 操作值
            // 操作值 = 目标值 - 基础值
            double baseHealth = maxHealthAttribute.getBaseValue(); // 获取当前基础生命值（通常是20）
            double operation = cappedHealth - baseHealth; // 例如：8 - 20 = -12

            // 创建新的修改器（Operation 0 = ADD）
            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_HEALTH_MODIFIER_UUID,
                    "Warframe Fixed Health Cap",
                    operation,
                    0 // Operation 0: 基础值 + 操作值
            );

            maxHealthAttribute.applyModifier(newModifier);

            // 如果当前生命值超过新上限，调整到上限
            if (player.getHealth() > cappedHealth) {
                player.setHealth((float) cappedHealth);
            }
        }
    }

    /**
     * ✅ 应用固定护甲上限（绝对锁定）
     * 使用 AttributeModifier 直接修改护甲属性
     *
     * @param player 玩家
     * @param fixedArmor 固定护甲值（加总后的值）
     */
    private static void applyFixedArmorCap(EntityPlayer player, double fixedArmor) {
        IAttributeInstance armorAttribute = player.getEntityAttribute(SharedMonsterAttributes.ARMOR);

        // 移除旧的修改器
        AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
        if (oldModifier != null) {
            armorAttribute.removeModifier(oldModifier);
        }

        if (fixedArmor > 0) {
            // ✅ 固定护甲最低为 0
            double cappedArmor = Math.max(0.0, fixedArmor);

            // ✅ 修复：获取当前总护甲值（包括装备）
            double currentTotalArmor = armorAttribute.getAttributeValue();
            double operation = cappedArmor - currentTotalArmor;

            // 创建新的修改器（Operation 0 = ADD）
            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_ARMOR_MODIFIER_UUID,
                    "Warframe Fixed Armor Cap",
                    operation,
                    0
            );

            armorAttribute.applyModifier(newModifier);
        }
    }

    /**
     * 玩家Tick事件
     * 每秒处理：护盾恢复
     * 每0.25秒处理：属性药水效果、挖掘速度同步、固定上限应用
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isEntityAlive()) {

                    // ========== 每秒处理：护盾恢复 ==========
                    if (entityPlayer.getEntityWorld().getTotalWorldTime() % 20 == 0) {
                        if (cooldingHashMap.containsKey(entityPlayer.getUniqueID())) {
                            if (cooldingHashMap.get(entityPlayer.getUniqueID()) > 1) {
                                cooldingHashMap.put(entityPlayer.getUniqueID(),
                                        cooldingHashMap.get(entityPlayer.getUniqueID()) - 1);
                            } else {
                                cooldingHashMap.remove(entityPlayer.getUniqueID());
                            }
                        } else {
                            HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                            applyWarframeKillStackEffects(entityPlayer, attributes);

                            double shield = attributes.getOrDefault("shield", 0.0);

                            // ✅ 如果有固定护盾，使用固定护盾容量
                            double fixedShield = attributes.getOrDefault("fixedShield", 0.0);
                            if (fixedShield > 0) {
                                // 固定护盾容量（最低为0）
                                double cappedShield = Math.max(0.0, fixedShield);

                                // ✅ 修复：强制锁定护盾值
                                if (cappedShield > 0) {
                                    float currentShield = entityPlayer.getAbsorptionAmount();

                                    // 如果护盾低于上限，恢复护盾
                                    if (currentShield < cappedShield) {
                                        double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                        entityPlayer.setAbsorptionAmount((float) Math.min(
                                                cappedShield,
                                                currentShield + cappedShield * 0.01 * shieldRecoveryRate
                                        ));
                                    }
                                    // ✅ 如果护盾超过上限，强制降到上限
                                    else if (currentShield > cappedShield) {
                                        entityPlayer.setAbsorptionAmount((float) cappedShield);
                                    }
                                }
                            } else if (shield > 0) {
                                // 使用百分比护盾
                                if (entityPlayer.getAbsorptionAmount() < (int) (entityPlayer.getMaxHealth() * shield / 2)) {
                                    double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                    entityPlayer.setAbsorptionAmount((float) Math.min(
                                            (int) (entityPlayer.getMaxHealth() * shield / 2),
                                            entityPlayer.getAbsorptionAmount() + entityPlayer.getMaxHealth() * shield / 2 * 0.01 * shieldRecoveryRate
                                    ));
                                }
                            }
                        }
                    }

                    // ========== 每0.25秒处理：属性药水效果 + 挖掘速度同步 + 固定上限 ==========
                    if (entityPlayer.getEntityWorld().getTotalWorldTime() % 5 == 0) {
                        HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                        applyWarframeKillStackEffects(entityPlayer, attributes);

                        // ✅ 应用固定生命值上限（绝对锁定）
                        double fixedHealth = attributes.getOrDefault("fixedHealth", 0.0);
                        if (fixedHealth > 0) {
                            applyFixedHealthCap(entityPlayer, fixedHealth);
                        } else {
                            // 如果没有固定生命值，移除修改器（恢复原生属性）
                            IAttributeInstance maxHealthAttribute = entityPlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
                            AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
                            if (oldModifier != null) {
                                maxHealthAttribute.removeModifier(oldModifier);
                            }

                            // 应用百分比生命值加成
                            double health = attributes.getOrDefault("health", 0.0);
                            if (health >= 0.1) {
                                int level = (int) (health / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.HEALTH, 6, level));
                            } else if (health <= -0.1) {
                                int level = (int) (-health / 0.1) - 1;
                                level = Math.min(level, 8);
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_HEALTH, 6, level));
                            }
                        }

                        // ✅ 应用固定护甲上限（绝对锁定）
                        double fixedArmor = attributes.getOrDefault("fixedArmor", 0.0);
                        if (fixedArmor > 0) {
                            applyFixedArmorCap(entityPlayer, fixedArmor);
                        } else {
                            // 如果没有固定护甲，移除修改器（恢复原生属性）
                            IAttributeInstance armorAttribute = entityPlayer.getEntityAttribute(SharedMonsterAttributes.ARMOR);
                            AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
                            if (oldModifier != null) {
                                armorAttribute.removeModifier(oldModifier);
                            }

                            // 应用百分比护甲加成
                            double armor = attributes.getOrDefault("armor", 0.0);
                            if (armor >= 0.1) {
                                int level = (int) (armor / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.ARMOR, 6, level));
                            } else if (armor <= -0.1) {
                                int level = (int) (-armor / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_ARMOR, 6, level));
                            }
                        }

                        // ========== 其他属性处理 ==========

                        double sprintSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                        if (sprintSpeed >= 0.1) {
                            int level = (int) (sprintSpeed / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.MOVEMENT_SPEED, 6, level));
                        } else if (sprintSpeed <= -0.1) {
                            int level = (int) (-sprintSpeed / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_MOVEMENT_SPEED, 6, level));
                        }

                        double knockbackResistance = attributes.getOrDefault("knockbackResistance", 0.0);
                        if (knockbackResistance >= 0.1) {
                            int level = (int) (knockbackResistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.KNOCKBACK_RESISTANCE, 6, level));
                        } else if (knockbackResistance <= -0.1) {
                            int level = (int) (-knockbackResistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_KNOCKBACK_RESISTANCE, 6, level));
                        }

                        double reachDistance = attributes.getOrDefault("reachDistance", 0.0);
                        if (reachDistance >= 0.1) {
                            int level = (int) (reachDistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                        } else if (reachDistance <= -0.1) {
                            int level = (int) (-reachDistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                        }

                        double diggingSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
                        KuvaLich.network.sendTo(
                                new DiggingSpeedPacket(entityPlayer.getUniqueID(), diggingSpeed),
                                (EntityPlayerMP) entityPlayer
                        );
                    }
                }
            }
        }
    }
}