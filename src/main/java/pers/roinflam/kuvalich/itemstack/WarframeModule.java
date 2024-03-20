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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.WarframeModules;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public class WarframeModule {
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

    public static List<ItemStack> getModules(EntityPlayer entityPlayer) {
        List<ItemStack> itemStacks = new ArrayList<>();
        @Nullable WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
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
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntityLiving().getEntityWorld().isRemote) {
            DamageSource damageSource = evt.getSource();
            if (evt.getEntityLiving() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();
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
                        if (!(dropStack.getItem() instanceof ItemArmor) && !(dropStack.getItem() instanceof ItemSword) && !(dropStack.getItem() instanceof ItemTool)) {
                            dropStack.setCount((int) (dropStack.getCount() * itemDropMultiplier));
                        }
                    }
                }
            }
        }
    }


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

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.@NotNull BreakSpeed evt) {
        EntityPlayer entityPlayer = evt.getEntityPlayer();
        if (entityPlayer.swingingHand != null) {
            HashMap<String, Double> attributes = new HashMap<>();
            List<ItemStack> modules = getModules(entityPlayer);
            for (ItemStack module : modules) {
                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                }
            }
            double diggingSpeed = 1 + DiggingSpeedPacket.DIGGING_SPEED;
            evt.setNewSpeed((float) (evt.getNewSpeed() * diggingSpeed));
//            System.out.println(evt.getNewSpeed());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isEntityAlive()) {
                    if (entityPlayer.getEntityWorld().getTotalWorldTime() % 20 == 0) {
                        if (cooldingHashMap.containsKey(entityPlayer.getUniqueID())) {
                            if (cooldingHashMap.get(entityPlayer.getUniqueID()) > 1) {
                                cooldingHashMap.put(entityPlayer.getUniqueID(), cooldingHashMap.get(entityPlayer.getUniqueID()) - 1);
                            } else {
                                cooldingHashMap.remove(entityPlayer.getUniqueID());
                            }
                        } else {
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
                                entityPlayer.setAbsorptionAmount((float) Math.min((int) (entityPlayer.getMaxHealth() * shield / 2), entityPlayer.getAbsorptionAmount() + entityPlayer.getMaxHealth() * shield / 2 * 0.01 * shieldRecoveryRate));
                            }
                        }
                    }
                    if (entityPlayer.getEntityWorld().getTotalWorldTime() % 5 == 0) {
                        HashMap<String, Double> attributes = new HashMap<>();
                        List<ItemStack> modules = getModules(entityPlayer);
                        for (ItemStack module : modules) {
                            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                            }
                        }
                        double health = attributes.getOrDefault("health", 0.0);
                        if (health >= 0.1) {
                            int level = (int) (health / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.HEALTH, 6, level));
                        } else {
                            if (health <= -0.1) {
                                int level = (int) (-health / 0.1) - 1;
                                level = Math.min(level, 8);
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_HEALTH, 6, level));
                            }
                        }
                        double armor = attributes.getOrDefault("armor", 0.0);
                        if (armor >= 0.1) {
                            int level = (int) (armor / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.ARMOR, 6, level));
                        } else {
                            if (armor <= -0.1) {
                                int level = (int) (-armor / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_ARMOR, 6, level));
                            }
                        }
                        double sprintSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                        if (sprintSpeed >= 0.1) {
                            int level = (int) (sprintSpeed / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.MOVEMENT_SPEED, 6, level));
                        } else {
                            if (sprintSpeed <= -0.1) {
                                int level = (int) (-sprintSpeed / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_MOVEMENT_SPEED, 6, level));
                            }
                        }
                        double knockbackResistance = attributes.getOrDefault("knockbackResistance", 0.0);
                        if (knockbackResistance >= 0.1) {
                            int level = (int) (knockbackResistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.KNOCKBACK_RESISTANCE, 6, level));
                        } else {
                            if (knockbackResistance <= -0.1) {
                                int level = (int) (-knockbackResistance / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_KNOCKBACK_RESISTANCE, 6, level));
                            }
                        }
                        double reachDistance = attributes.getOrDefault("reachDistance", 0.0);
                        if (reachDistance >= 0.1) {
                            int level = (int) (reachDistance / 0.1) - 1;
                            entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                        } else {
                            if (reachDistance <= -0.1) {
                                int level = (int) (-reachDistance / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.REACH_DISTANCE, 6, level));
                            }
                        }
                        DiggingSpeedPacket.DIGGING_SPEED = attributes.getOrDefault("diggingSpeed", 0.0);
                        KuvaLich.network.sendTo(new DiggingSpeedPacket(DiggingSpeedPacket.DIGGING_SPEED), (EntityPlayerMP) entityPlayer);
                    }
                }
            }
        }
    }
}
