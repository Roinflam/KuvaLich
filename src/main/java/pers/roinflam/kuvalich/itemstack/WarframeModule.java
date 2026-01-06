package pers.roinflam.kuvalich.itemstack;

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
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
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
 *
 * 提供玩家模组装备系统，包括：
 * - 护盾系统（吸收伤害 + 自动恢复）
 * - 属性加成（生命、护甲、移速、挖掘速度等）
 * - 元素抗性（火焰、闪电、同类伤害）- 依次乘算机制
 * - 掉落增幅
 * - 战甲击杀叠层系统（执刑官系列MOD）
 *
 * 乘算机制：
 * - 多个抗性模组依次相乘：50%抗性 × 30%抗性 = 受到35%伤害（减伤65%）
 * - 击杀叠层抗性同样依次乘算：先加总单层加成，再与基础抗性相乘
 */
@Mod.EventBusSubscriber
public class WarframeModule {

    /**
     * 护盾恢复冷却（UUID -> 剩余ticks）
     * 受伤后进入冷却，冷却期间不恢复护盾
     */
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

    /**
     * 乘算属性类型定义
     * 这些属性会依次相乘而不是相加
     */
    private static final Set<String> MULTIPLICATIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "fireProtection",           // 火焰抗性
            "electricProtection",       // 电击抗性
            "homologousProtection",     // 同源抗性
            "shieldRecoveryDelay"       // 护盾恢复延迟
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
        itemStacks.add(warframeModules.getOne());
        itemStacks.add(warframeModules.getTwo());
        itemStacks.add(warframeModules.getThree());
        itemStacks.add(warframeModules.getFour());
        itemStacks.add(warframeModules.getFive());
        itemStacks.add(warframeModules.getSix());
        itemStacks.add(warframeModules.getSeven());
        itemStacks.add(warframeModules.getEight());
        return itemStacks;
    }

    /**
     * ✅ 收集玩家所有模组属性（区分加算和乘算）
     *
     * 加算属性：生命、护甲、移速等 - 直接相加
     * 乘算属性：抗性、延迟等 - 依次相乘
     *
     * 示例：
     * - 50%火抗 + 30%火抗 = 1.0 × (1-0.5) × (1-0.3) = 0.35 倍伤害（减伤65%）
     * - -60%延迟 + -40%延迟 = 1.0 × (1-0.6) × (1-0.4) = 0.24 倍延迟（延迟2.4秒）
     */
    private static HashMap<String, Double> collectAttributes(EntityPlayer player) {
        HashMap<String, Double> attributes = new HashMap<>();

        // 乘算属性初始值为1.0（代表100%，即无加成）
        HashMap<String, Double> multiplicativeAttributes = new HashMap<>();
        multiplicativeAttributes.put("fireProtection", 1.0);
        multiplicativeAttributes.put("electricProtection", 1.0);
        multiplicativeAttributes.put("homologousProtection", 1.0);
        multiplicativeAttributes.put("shieldRecoveryDelay", 1.0);

        List<ItemStack> modules = getModules(player);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();
                double value = entry.getValue();

                // 判断是否为乘算属性
                if (MULTIPLICATIVE_ATTRIBUTES.contains(key)) {
                    if (key.equals("shieldRecoveryDelay")) {
                        // ✅ 护盾恢复延迟：负数表示减少延迟
                        // 例如：-60% 就是 1.0 × (1 + (-0.6)) = 0.4 倍延迟
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 + value));
                    } else {
                        // ✅ 抗性：正数表示减伤
                        // 例如：+50% 就是 1.0 × (1 - 0.5) = 0.5 倍伤害
                        multiplicativeAttributes.put(key,
                                multiplicativeAttributes.get(key) * (1.0 - value));
                    }
                } else if (KILL_STACK_PROTECTION_ATTRIBUTES.contains(key)) {
                    // ✅ 击杀叠层抗性：先加总，稍后单独处理
                    attributes.put(key,
                            attributes.getOrDefault(key, 0.0) + value);
                } else {
                    // ✅ 加算属性（生命、护甲、移速等）
                    attributes.put(key,
                            attributes.getOrDefault(key, 0.0) + value);
                }
            }
        }

        // 将乘算属性合并到结果中
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
     * 1. 受伤时：计算元素抗性、触发护盾冷却
     * 2. 攻击时：触发攻击者的护盾冷却（防止边打边回血）
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntityLiving().getEntityWorld().isRemote) {
            DamageSource damageSource = evt.getSource();

            // 情况1：玩家受伤 - 计算抗性 + 护盾冷却
            if (evt.getEntityLiving() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();

                // ✅ 使用新的收集方法（区分加算和乘算）
                HashMap<String, Double> attributes = collectAttributes(entityPlayer);

                // ✅ 应用战甲击杀叠层效果（包括击杀叠层抗性）
                applyWarframeKillStackEffects(entityPlayer, attributes);

                // ✅ 护盾恢复延迟（attributes里已经是最终乘算结果，无下限）
                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (entityPlayer.getAbsorptionAmount() <= 0) {
                    coolding *= 3;  // 护盾破碎时延迟x3
                }
                cooldingHashMap.put(entityPlayer.getUniqueID(), coolding);

                // ✅ 火焰抗性（attributes里已经是最终伤害倍率）
                if (damageSource.isFireDamage()) {
                    double damageMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                // ✅ 电击抗性
                if (damageSource.equals(DamageSource.LIGHTNING_BOLT)) {
                    double damageMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                // ✅ 同源抗性（玩家攻击玩家）
                if (damageSource.getTrueSource() instanceof EntityPlayer) {
                    double damageMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }
            }

            // 情况2：玩家攻击 - 触发攻击者护盾冷却
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
            }
        }
    }

    /**
     * ✅ 应用战甲击杀叠层效果
     *
     * 处理规则：
     * 1. 普通叠层属性（生命、护甲等）：直接加算
     * 2. 击杀叠层抗性：先加总单层加成，乘以层数，然后与基础抗性依次乘算
     *
     * 示例：
     * 装备：执刑官火焰防护（基础+60%，每层+2.5%，20层满）
     * 基础抗性：1.0 × (1-0.6) = 0.4 倍伤害
     * 击杀叠层：0.4 × (1-0.5) = 0.2 倍伤害（减伤80%）
     */
    private static void applyWarframeKillStackEffects(EntityPlayer player, HashMap<String, Double> attributes) {
        // ========== 加算属性 ==========

        // 生命值叠层
        if (attributes.containsKey("killStackHealth")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HEALTH);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHealth");
                double currentHealth = attributes.getOrDefault("health", 0.0);
                attributes.put("health", currentHealth + stackValue * stacks);
            }
        }

        // 护盾容量叠层
        if (attributes.containsKey("killStackShield")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShield");
                double currentShield = attributes.getOrDefault("shield", 0.0);
                attributes.put("shield", currentShield + stackValue * stacks);
            }
        }

        // 护甲叠层
        if (attributes.containsKey("killStackArmor")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ARMOR);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackArmor");
                double currentArmor = attributes.getOrDefault("armor", 0.0);
                attributes.put("armor", currentArmor + stackValue * stacks);
            }
        }

        // 冲刺速度叠层
        if (attributes.containsKey("killStackSprintSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SPRINT_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackSprintSpeed");
                double currentSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                attributes.put("sprintSpeed", currentSpeed + stackValue * stacks);
            }
        }

        // 护盾恢复速率叠层
        if (attributes.containsKey("killStackShieldRecoveryRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryRate");
                double currentRate = attributes.getOrDefault("shieldRecoveryRate", 0.0);
                attributes.put("shieldRecoveryRate", currentRate + stackValue * stacks);
            }
        }

        // 恢复生命值倍率叠层
        if (attributes.containsKey("killStackResponseRate")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_RESPONSE_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackResponseRate");
                double currentRate = attributes.getOrDefault("responseRate", 0.0);
                attributes.put("responseRate", currentRate + stackValue * stacks);
            }
        }

        // 战利品掉落倍率叠层
        if (attributes.containsKey("killStackItemDropMultiplier")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackItemDropMultiplier");
                double currentMultiplier = attributes.getOrDefault("itemDropMultiplier", 0.0);
                attributes.put("itemDropMultiplier", currentMultiplier + stackValue * stacks);
            }
        }

        // 挖掘速度叠层
        if (attributes.containsKey("killStackDiggingSpeed")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_DIGGING_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackDiggingSpeed");
                double currentSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
                attributes.put("diggingSpeed", currentSpeed + stackValue * stacks);
            }
        }

        // ========== 乘算属性（击杀叠层抗性）==========
        // ✅ 规则：先加总单层加成，乘以层数，然后依次乘算

        // 火焰抗性叠层
        if (attributes.containsKey("killStackFireProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_FIRE_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFireProtection");
                double totalStackProtection = stackValue * stacks;

                // ✅ 依次乘算：与基础抗性一样的公式
                double baseMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                attributes.put("fireProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        // 电击抗性叠层
        if (attributes.containsKey("killStackElectricProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_ELECTRIC_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackElectricProtection");
                double totalStackProtection = stackValue * stacks;

                double baseMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                attributes.put("electricProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        // 同源抗性叠层
        if (attributes.containsKey("killStackHomologousProtection")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackHomologousProtection");
                double totalStackProtection = stackValue * stacks;

                double baseMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                attributes.put("homologousProtection", baseMultiplier * (1.0 - totalStackProtection));
            }
        }

        // 护盾恢复延迟叠层
        if (attributes.containsKey("killStackShieldRecoveryDelay")) {
            int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackShieldRecoveryDelay");
                double totalStackDelay = stackValue * stacks;

                // ✅ 延迟是负数，用 (1 + totalStackDelay)
                double baseMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                attributes.put("shieldRecoveryDelay", baseMultiplier * (1.0 + totalStackDelay));
            }
        }
    }

    /**
     * ✅ 战甲击杀时添加叠层
     * 检查玩家装备的模组，为所有包含战甲击杀叠层词条的模组添加对应叠层
     *
     * 注意：多个同词条的单层加成会相加
     * 例如：卡A每层+1%，卡B每层+3% → 实际每层+4%
     */
    private static void addWarframeKillStacks(EntityPlayer player) {
        if (player == null) {
            return;
        }

        // 收集所有击杀叠层词条（多卡同词条会加在一起）
        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(player);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();
                // 只关心击杀叠层词条
                if (key.startsWith("killStack")) {
                    attributes.put(key, attributes.getOrDefault(key, 0.0) + entry.getValue());
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
     * ✅ 增加：击杀时触发战甲叠层
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntityLiving().world.isRemote && evt.getSource().getTrueSource() instanceof EntityPlayer) {
            if (evt.getEntityLiving() instanceof EntityAnimal || evt.getEntityLiving() instanceof EntityMob) {
                if (evt.getEntityLiving().isNonBoss()) {
                    EntityPlayer entityPlayer = (EntityPlayer) evt.getSource().getTrueSource();

                    // ✅ 击杀时添加战甲叠层
                    addWarframeKillStacks(entityPlayer);

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
     * 玩家Tick事件
     * 每秒处理：护盾恢复
     * 每0.25秒处理：属性药水效果、挖掘速度同步
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
                            // 冷却中：减少冷却时间
                            if (cooldingHashMap.get(entityPlayer.getUniqueID()) > 1) {
                                cooldingHashMap.put(entityPlayer.getUniqueID(),
                                        cooldingHashMap.get(entityPlayer.getUniqueID()) - 1);
                            } else {
                                cooldingHashMap.remove(entityPlayer.getUniqueID());
                            }
                        } else {
                            // 冷却结束：恢复护盾
                            HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                            applyWarframeKillStackEffects(entityPlayer, attributes);

                            double shield = attributes.getOrDefault("shield", 0.0);
                            if (shield > 0 && entityPlayer.getAbsorptionAmount() < (int) (
                                    entityPlayer.getMaxHealth() * shield / 2)) {
                                double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                entityPlayer.setAbsorptionAmount((float) Math.min(
                                        (int) (entityPlayer.getMaxHealth() * shield / 2),
                                        entityPlayer.getAbsorptionAmount() + entityPlayer.getMaxHealth() * shield / 2 * 0.01 * shieldRecoveryRate
                                ));
                            }
                        }
                    }

                    // ========== 每0.25秒处理：属性药水效果 + 挖掘速度同步 ==========
                    if (entityPlayer.getEntityWorld().getTotalWorldTime() % 5 == 0) {
                        HashMap<String, Double> attributes = collectAttributes(entityPlayer);
                        applyWarframeKillStackEffects(entityPlayer, attributes);

                        // 生命值加成/减少
                        double health = attributes.getOrDefault("health", 0.0);
                        if (health >= 0.1) {
                            int level = (int) (health / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.HEALTH, 6, level));
                        } else if (health <= -0.1) {
                            int level = (int) (-health / 0.1) - 1;
                            level = Math.min(level, 8);
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_HEALTH, 6, level));
                        }

                        // 护甲加成/减少
                        double armor = attributes.getOrDefault("armor", 0.0);
                        if (armor >= 0.1) {
                            int level = (int) (armor / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.ARMOR, 6, level));
                        } else if (armor <= -0.1) {
                            int level = (int) (-armor / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_ARMOR, 6, level));
                        }

                        // 移速加成/减少
                        double sprintSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                        if (sprintSpeed >= 0.1) {
                            int level = (int) (sprintSpeed / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.MOVEMENT_SPEED, 6, level));
                        } else if (sprintSpeed <= -0.1) {
                            int level = (int) (-sprintSpeed / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_MOVEMENT_SPEED, 6, level));
                        }

                        // 击退抗性
                        double knockbackResistance = attributes.getOrDefault("knockbackResistance", 0.0);
                        if (knockbackResistance >= 0.1) {
                            int level = (int) (knockbackResistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.KNOCKBACK_RESISTANCE, 6, level));
                        } else if (knockbackResistance <= -0.1) {
                            int level = (int) (-knockbackResistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_KNOCKBACK_RESISTANCE, 6, level));
                        }

                        // 攻击距离
                        double reachDistance = attributes.getOrDefault("reachDistance", 0.0);
                        if (reachDistance >= 0.1) {
                            int level = (int) (reachDistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                        } else if (reachDistance <= -0.1) {
                            int level = (int) (-reachDistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                        }

                        // 挖掘速度同步
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