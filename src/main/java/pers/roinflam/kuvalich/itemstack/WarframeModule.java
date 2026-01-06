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
 * - 元素抗性（火焰、闪电、同类伤害）
 * - 掉落增幅
 */
@Mod.EventBusSubscriber
public class WarframeModule {

    /**
     * 护盾恢复冷却（UUID -> 剩余ticks）
     * 受伤后进入冷却，冷却期间不恢复护盾
     */
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

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
                HashMap<String, Double> attributes = new HashMap<>();
                List<ItemStack> modules = getModules(entityPlayer);
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                // 计算护盾恢复延迟
                int coolding = 10;
                double shieldRecoveryDelay = Math.max(1 + attributes.getOrDefault("shieldRecoveryDelay", 0.0), 0.2);
                if (entityPlayer.getAbsorptionAmount() <= 0) {
                    shieldRecoveryDelay *= 3;  // 护盾破碎时延迟x3
                }
                coolding *= shieldRecoveryDelay;
                cooldingHashMap.put(entityPlayer.getUniqueID(), coolding);

                // 元素抗性计算
                if (damageSource.isFireDamage()) {
                    double fireProtection = 1 - attributes.getOrDefault("fireProtection", 0.0);
                    evt.setAmount((float) (evt.getAmount() * fireProtection));
                }
                if (damageSource.equals(DamageSource.LIGHTNING_BOLT)) {
                    double electricProtection = 1 - attributes.getOrDefault("electricProtection", 0.0);
                    evt.setAmount((float) (evt.getAmount() * electricProtection));
                }
                if (damageSource.getTrueSource() instanceof EntityPlayer) {
                    double homologousProtection = 1 - attributes.getOrDefault("homologousProtection", 0.0);
                    evt.setAmount((float) (evt.getAmount() * homologousProtection));
                }
            }

            // 情况2：玩家攻击 - 触发攻击者护盾冷却
            if (damageSource.getTrueSource() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) damageSource.getTrueSource();
                HashMap<String, Double> attributes = new HashMap<>();
                List<ItemStack> modules = getModules(entityPlayer);
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                int coolding = 10;
                double shieldRecoveryDelay = Math.max(1 + attributes.getOrDefault("shieldRecoveryDelay", 0.0), 0.2);
                if (entityPlayer.getAbsorptionAmount() <= 0) {
                    shieldRecoveryDelay *= 3;
                }
                coolding *= shieldRecoveryDelay;
                cooldingHashMap.put(entityPlayer.getUniqueID(), coolding);
            }
        }
    }

    /**
     * 掉落增幅
     * 根据模组属性增加掉落物品数量（不包括装备）
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntityLiving().world.isRemote && evt.getSource().getTrueSource() instanceof EntityPlayer) {
            if (evt.getEntityLiving() instanceof EntityAnimal || evt.getEntityLiving() instanceof EntityMob) {
                if (evt.getEntityLiving().isNonBoss()) {
                    EntityPlayer entityPlayer = (EntityPlayer) evt.getSource().getTrueSource();
                    HashMap<String, Double> attributes = new HashMap<>();
                    List<ItemStack> modules = getModules(entityPlayer);
                    for (ItemStack module : modules) {
                        for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                            attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                        }
                    }
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
            HashMap<String, Double> attributes = new HashMap<>();
            List<ItemStack> modules = getModules(entityPlayer);
            for (ItemStack module : modules) {
                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                }
            }
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
            HashMap<String, Double> attributes = new HashMap<>();
            List<ItemStack> modules = getModules(entityPlayer);
            for (ItemStack module : modules) {
                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                }
            }
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
                            HashMap<String, Double> attributes = new HashMap<>();
                            List<ItemStack> modules = getModules(entityPlayer);
                            for (ItemStack module : modules) {
                                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                                }
                            }
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
                        HashMap<String, Double> attributes = new HashMap<>();
                        List<ItemStack> modules = getModules(entityPlayer);
                        for (ItemStack module : modules) {
                            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                            }
                        }

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

                        // ✅ 修复：挖掘速度同步（改为UUID-based）
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