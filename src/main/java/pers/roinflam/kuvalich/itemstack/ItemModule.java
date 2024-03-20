package pers.roinflam.kuvalich.itemstack;

import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArrow;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@Mod.EventBusSubscriber
public class ItemModule {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        if (hasBase(itemStack)) {
            List<ItemStack> modules = getModules(itemStack);
            int index = 1;
            if (modules.size() > 0) {
                HashMap<String, Double> attributes = new HashMap<>();
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                evt.getToolTip().add(index++, TextFormatting.WHITE + "" + TextFormatting.BOLD + I18n.format("item.module"));
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.damage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (getBaseAttribute(itemStack, "damage") * 100) + "%");
                if (attributes.getOrDefault("meleeDamage", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.meleeDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("meleeDamage") * 100) + "%");
                }
                if (attributes.getOrDefault("remoteDamage", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.remoteDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("remoteDamage") * 100) + "%");
                }
                if (attributes.getOrDefault("arrowDamage", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.arrowDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("arrowDamage") * 100) + "%");
                }
                if (attributes.getOrDefault("projectileDamage", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.projectileDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("projectileDamage") * 100) + "%");
                }
                if (attributes.getOrDefault("magicDamage", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.magicDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("magicDamage") * 100) + "%");
                }
                if (attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.baseDamageWhenNotCriticalStrike") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("baseDamageWhenNotCriticalStrike") * 100) + "%");
                }
                if (attributes.getOrDefault("attackRange", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.attackRange") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + String.format("%.1f", attributes.get("attackRange")) + "m");
                }
                if (attributes.getOrDefault("bursting_radius", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.bursting_radius") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + String.format("%.1f", 1 + attributes.get("bursting_radius") * 2) + "m");
                }
                if (attributes.getOrDefault("attackSpeed", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.attackSpeed") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + String.format("%.1f", attributes.get("attackSpeed") * 100) + "%");
                }
                if (attributes.getOrDefault("firing_rate", 0.0) != 0) {
                    if (itemStack.getItem() instanceof ItemBow) {
                        evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.firing_rate") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("firing_rate") * 2 * 100) + "%");
                    } else {
                        evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.firing_rate") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("firing_rate") * 100) + "%");
                    }
                }
                double baseCriticalStrikeProbability = getBaseAttribute(itemStack, "criticalStrikeProbability");
                double meleeCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
                double remoteCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.criticalStrikeProbability") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + "" + (int) (meleeCriticalStrikeProbability * 100) + "% / " + (int) (remoteCriticalStrikeProbability * 100) + "%");
                double baseCriticalStrikeMultiplier = getBaseAttribute(itemStack, "criticalStrikeMultiplier");
                double meleeCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
                double remoteCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.criticalStrikeMultiplier") + " " + TextFormatting.GRAY + "x" + TextFormatting.BOLD + String.format("%.1f", meleeCriticalStrikeMultiplier) + " / x" + String.format("%.1f", remoteCriticalStrikeMultiplier));
                if (attributes.getOrDefault("multishot", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.multishot") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (attributes.get("multishot") * 100) + "%");
                }
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.triggerChance") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (getBaseAttribute(itemStack, "triggerChance") * (1 + attributes.getOrDefault("triggerChance", 0.0)) * 100) + "%");
                if (attributes.getOrDefault("triggerTime", 0.0) != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.triggerTime") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) ((1 + attributes.get("triggerTime")) * 100) + "%");
                }

                double elementDamage = 0;
                elementDamage += attributes.getOrDefault("fire", 0.0);
                elementDamage += attributes.getOrDefault("ice", 0.0);
                elementDamage += attributes.getOrDefault("poison", 0.0);
                elementDamage += attributes.getOrDefault("electricity", 0.0);
                elementDamage += attributes.getOrDefault("slash", 0.0);
                elementDamage += attributes.getOrDefault("puncture", 0.0);
                elementDamage += attributes.getOrDefault("impact", 0.0);
                if (elementDamage != 0) {
                    evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.module.triggerDamage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (elementDamage * 100) + "%");
                }

                HashMap<String, String> elements = getTriggerElements(itemStack);
                if (elements.size() > 0) {
                    String triggerElements = TextFormatting.WHITE + I18n.format("item.module.triggerType") + " ";
                    for (String element : elements.keySet()) {
                        triggerElements += KuvaWeapon.getColor(element) + "" + TextFormatting.BOLD + I18n.format("kuvaweapon.type." + element) + elements.get(element) + " ";
                    }
                    evt.getToolTip().add(index++, triggerElements);
                }

                evt.getToolTip().add(index++, TextFormatting.GOLD + "" + TextFormatting.BOLD + I18n.format("kuvaweapon.item_module_info"));
                List<ItemStack> itemStacks = getModules(itemStack);
                for (int i = 0; i < itemStacks.size(); i++) {
                    ItemStack module = itemStacks.get(i);
                    evt.getToolTip().add(index++, TextFormatting.WHITE + " - " + module.getDisplayName() + " ");
                }
            } else {
                evt.getToolTip().add(index++, TextFormatting.WHITE + "" + TextFormatting.BOLD + I18n.format("item.base"));
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.base.damage") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (getBaseAttribute(itemStack, "damage") * 100) + "%");
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.base.criticalStrikeProbability") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (getBaseAttribute(itemStack, "criticalStrikeProbability") * 100) + "%");
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.base.criticalStrikeMultiplier") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + "x" + getBaseAttribute(itemStack, "criticalStrikeMultiplier"));
                evt.getToolTip().add(index++, TextFormatting.WHITE + I18n.format("item.base.triggerChance") + " " + TextFormatting.GRAY + "" + TextFormatting.BOLD + (int) (getBaseAttribute(itemStack, "triggerChance") * 100) + "%");
            }
        }
    }

    public static List<ItemStack> getModules(ItemStack weaponItemStack) {
        List<ItemStack> itemStacks = new ArrayList<>();
        @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.copy().serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");
        NBTTagList itemList = weaponModule.getTagList("modules", 10);
        for (int i = 0; i < 8; i++) {
            NBTTagCompound itemTag = itemList.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(itemTag);
            if (!stack.isEmpty()) {
                itemStacks.add(stack);
            }
        }
        return itemStacks;
    }

    public static boolean hasBase(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        return tag.hasKey(Reference.MOD_ID + "_weaponModules");
    }

    public static double getBaseAttribute(ItemStack itemStack, String attributeType) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

        return kuvalichModule.getDouble(attributeType);
    }

    public static void setBaseAttribute(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

        double damage = RandomUtil.getInt(80, 120) / 100.0;
        if (RandomUtil.percentageChance(75)) {
            damage = 1;
        }
        double criticalStrikeProbability = RandomUtil.getInt(5, 20) / 100.0;
        if (RandomUtil.percentageChance(25)) {
            criticalStrikeProbability = RandomUtil.getInt(15, 33) / 100.0;
        }
        double criticalStrikeMultiplier = RandomUtil.getInt(15, 22) / 10.0;
        if (criticalStrikeProbability >= 0.25) {
            criticalStrikeMultiplier = RandomUtil.getInt(20, 33) / 10.0;
            damage = RandomUtil.getInt(60, 100) / 100.0;
        } else if (criticalStrikeProbability <= 0.1) {
            if (RandomUtil.percentageChance(5)) {
                criticalStrikeMultiplier = RandomUtil.getInt(33, 100) / 10.0;
                damage = RandomUtil.getInt(60, 100) / 100.0;
            } else {
                criticalStrikeMultiplier = RandomUtil.getInt(12, 20) / 10.0;
            }
        }
        double triggerChance = RandomUtil.getInt(10, 25) / 100.0;
        if (criticalStrikeProbability > 0.2 && RandomUtil.percentageChance(75)) {
            triggerChance = RandomUtil.getInt(2, 5) / 100.0;
        } else if (criticalStrikeProbability <= 0.12 && RandomUtil.percentageChance(75)) {
            criticalStrikeMultiplier = RandomUtil.getInt(20, 50) / 10.0;
            damage = RandomUtil.getInt(100, 140) / 100.0;
        }

        weaponModule.setDouble("damage", damage);
        weaponModule.setDouble("criticalStrikeProbability", criticalStrikeProbability);
        weaponModule.setDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
        weaponModule.setDouble("triggerChance", triggerChance);

        tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }
//
//    @SubscribeEvent(priority = EventPriority.HIGHEST)
//    public static void LivingDamageEvent(@Nonnull LivingDamageEvent evt) {
//        System.out.println(evt.getAmount() + ":LivingDamageEvent:HIGHEST");
//    }
//
//    @SubscribeEvent(priority = EventPriority.LOWEST)
//    public static void LivingDamageEvents(@Nonnull LivingDamageEvent evt) {
//        System.out.println(evt.getAmount() + ":LivingDamageEvent:LOWEST");
//    }
//
//    @SubscribeEvent(priority = EventPriority.HIGHEST)
//    public static void onLivingHurts(@Nonnull LivingHurtEvent evt) {
//        System.out.println(evt.getAmount() + ":LivingHurtEvent:HIGHEST");
//    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
//        System.out.println(evt.getAmount() + ":LivingHurtEvent:LOWEST");
        DamageSource damageSource = evt.getSource();
        if (!evt.getEntityLiving().getEntityWorld().isRemote && (damageSource.getImmediateSource() instanceof EntityPlayer || damageSource.getTrueSource() instanceof EntityPlayer)) {
            EntityPlayer entityPlayer;
            if (damageSource.getImmediateSource() instanceof EntityPlayer) {
                entityPlayer = (EntityPlayer) damageSource.getImmediateSource();
            } else if (damageSource.getTrueSource() instanceof EntityPlayer) {
                entityPlayer = (EntityPlayer) damageSource.getTrueSource();
            } else {
                entityPlayer = null;
            }
            if (entityPlayer != null && EntityLivingUtil.getTicksSinceLastSwing(entityPlayer) >= 0.25) {
                ItemStack weapon = entityPlayer.getHeldItemMainhand();
                if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    double baseDamage = 1;
//                    System.out.println("基础伤害：" + baseDamage);
                    double criticalStrikeProbability = getBaseAttribute(weapon, "criticalStrikeProbability") * 100 * EntityLivingUtil.getTicksSinceLastSwing(entityPlayer);
                    double criticalStrikeMultiplier = getBaseAttribute(weapon, "criticalStrikeMultiplier");
                    double triggerChance = getBaseAttribute(weapon, "triggerChance") * 100;
//                    System.out.println("基础暴击几率：" + criticalStrikeProbability);
//                    System.out.println("基础暴击倍率：" + criticalStrikeMultiplier);
//                    System.out.println("基础触发几率：" + triggerChance);
                    HashMap<String, Double> attributes = new HashMap<>();
                    List<ItemStack> modules = getModules(weapon);
                    for (ItemStack module : modules) {
                        for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                            attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                        }
                    }
                    if (damageSource.getImmediateSource() instanceof EntityPlayer) {
//                        System.out.println(attributes.getOrDefault("meleeDamage", 0.0));
                        baseDamage += attributes.getOrDefault("meleeDamage", 0.0);
                        criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
                        if (entityPlayer.isSprinting()) {
                            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0) + attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0));
                        } else {
                            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
                        }
                    } else if (damageSource.getTrueSource() instanceof EntityPlayer) {
                        baseDamage += attributes.getOrDefault("remoteDamage", 0.0);
                        criticalStrikeProbability *= (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
                        criticalStrikeMultiplier *= (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));
                        if (damageSource.getImmediateSource() instanceof EntityArrow) {
                            baseDamage += attributes.getOrDefault("arrowDamage", 0.0);
                        } else if (damageSource.getImmediateSource() instanceof IProjectile) {
                            baseDamage += attributes.getOrDefault("projectileDamage", 0.0);
                        }
                        double range = 1 + attributes.getOrDefault("bursting_radius", 0.0) * 2;
                        if (!damageSource.isExplosion()) {
                            if (range > 1) {
                                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, hurter, range, entityLivingBase -> !entityLivingBase.equals(hurter) && !entityLivingBase.equals(entityPlayer));
                                for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                    entityLivingBase.attackEntityFrom(DamageSource.causePlayerDamage(entityPlayer).setExplosion(), evt.getAmount() * 0.5f);
                                }
                            }
                        } else if (range < 0) {
                            evt.setCanceled(true);
                            return;
                        }
                    }
                    if (damageSource.isMagicDamage()) {
                        baseDamage += attributes.getOrDefault("magicDamage", 0.0);
                    }
                    if (entityPlayer.isSprinting()) {
                        triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0) + attributes.getOrDefault("dashTriggerChance", 0.0));
                    } else {
                        triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0));
                    }

//                    System.out.println("伤害：" + baseDamage);
//                    System.out.println("暴击几率：" + criticalStrikeProbability);
//                    System.out.println("暴击倍率：" + criticalStrikeMultiplier);
//                    System.out.println("触发几率：" + triggerChance);

                    double elementDamage = 0;
                    elementDamage += attributes.getOrDefault("fire", 0.0);
                    elementDamage += attributes.getOrDefault("ice", 0.0);
                    elementDamage += attributes.getOrDefault("poison", 0.0);
                    elementDamage += attributes.getOrDefault("electricity", 0.0);
                    elementDamage += attributes.getOrDefault("slash", 0.0);
                    elementDamage += attributes.getOrDefault("puncture", 0.0);
                    elementDamage += attributes.getOrDefault("impact", 0.0);

                    if (getBaseAttribute(weapon, "damage") > 0) {
                        if (getBaseAttribute(weapon, "damage") >= 1) {
                            baseDamage *= getBaseAttribute(weapon, "damage");
                            elementDamage *= getBaseAttribute(weapon, "damage");
                        } else {
                            baseDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
                            elementDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
                        }
                    }

//                    System.out.println("元素伤害：" + elementDamage);

                    int color = DamageInfo.DamageColor.WHITE.getColor();
                    if (criticalStrikeProbability > 300) {
                        baseDamage *= criticalStrikeMultiplier * 3;
                        color = DamageInfo.DamageColor.RED.getColor();
                    } else if (criticalStrikeProbability > 200) {
                        if (RandomUtil.percentageChance(criticalStrikeProbability - 200)) {
                            baseDamage *= criticalStrikeMultiplier * 3;
                            color = DamageInfo.DamageColor.RED.getColor();
                        } else {
                            baseDamage *= criticalStrikeMultiplier * 2;
                            color = DamageInfo.DamageColor.ORANGE.getColor();
                        }
                    } else if (criticalStrikeProbability > 100) {
                        if (RandomUtil.percentageChance(criticalStrikeProbability - 100)) {
                            baseDamage *= criticalStrikeMultiplier * 2;
                            color = DamageInfo.DamageColor.ORANGE.getColor();
                        } else {
                            baseDamage *= criticalStrikeMultiplier;
                            color = DamageInfo.DamageColor.YELLOW.getColor();
                        }
                    } else {
                        if (RandomUtil.percentageChance(criticalStrikeProbability)) {
                            baseDamage *= criticalStrikeMultiplier;
                            color = DamageInfo.DamageColor.YELLOW.getColor();
                        } else {
                            if (hurter.getAbsorptionAmount() > 0) {
                                color = DamageInfo.DamageColor.BLUE.getColor();
                            }
                            baseDamage += attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0);
                        }
                    }
//                    System.out.println("普通伤害：" + baseDamage);
                    float damage = evt.getAmount();
                    damage = (float) (damage * baseDamage + damage * elementDamage);
//                    System.out.println("歧视前伤害：" + damage);
                    if (hurter.getCreatureAttribute().equals(EnumCreatureAttribute.UNDEFINED)) {
                        damage *= 1 + (attributes.getOrDefault("bane_of_undefined", 0.0));
                    } else if (hurter.getCreatureAttribute().equals(EnumCreatureAttribute.UNDEAD)) {
                        damage *= 1 + (attributes.getOrDefault("bane_of_undead", 0.0));
                    } else if (hurter.getCreatureAttribute().equals(EnumCreatureAttribute.ARTHROPOD)) {
                        damage *= 1 + (attributes.getOrDefault("bane_of_arthropod", 0.0));
                    } else {
                        damage *= 1 + (attributes.getOrDefault("bane_of_illager", 0.0));
                    }
//                    System.out.println("歧视后伤害：" + damage);

                    double triggerTime = 1 + (attributes.getOrDefault("triggerTime", 0.0));
                    if (triggerChance > 100) {
                        int number = (int) triggerTime / 100;
                        for (int i = 0; i < number; i++) {
                            damage = triggerElementEffect(damageSource, hurter, entityPlayer, weapon, damage, triggerTime);
                        }
                        if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                            damage = triggerElementEffect(damageSource, hurter, entityPlayer, weapon, damage, triggerTime);
                        }
                    } else if (RandomUtil.percentageChance(triggerChance)) {
                        damage = triggerElementEffect(damageSource, hurter, entityPlayer, weapon, damage, triggerTime);
                    }
//                    System.out.println("属性后伤害：" + damage);
                    damage = Math.max(damage, 0);
                    evt.setAmount(damage);

                    double offsetX = (Math.random() - 0.5) * hurter.width;
                    double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                    double offsetZ = (Math.random() - 0.5) * hurter.width;
                    Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);
                    KuvaLich.network.sendTo(new DamagePacket(damage, position, color), (EntityPlayerMP) entityPlayer);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.getEntityWorld().getTotalWorldTime() % 20 == 0 && entityPlayer.isEntityAlive()) {
                    if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                        ItemStack weapon = entityPlayer.getHeldItemMainhand();
                        if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                            HashMap<String, Double> attributes = new HashMap<>();
                            List<ItemStack> modules = getModules(weapon);
                            for (ItemStack module : modules) {
                                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                                }
                            }
                            double attackSpeed = attributes.getOrDefault("attackSpeed", 0.0);
                            if (attackSpeed >= 0.1) {
                                int level = (int) (attackSpeed / 0.1) - 1;
                                entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.ATTACK_SPEED, 30, level));
                            } else {
                                if (attackSpeed <= -0.1) {
                                    int level = (int) (-attackSpeed / 0.1) - 1;
                                    entityPlayer.addPotionEffect(new PotionEffect(KuvaLichPotion.NEGATIVE_ATTACK_SPEED, 30, level));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(@NotNull AttackEntityEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() instanceof EntityLivingBase) {
                EntityLivingBase hurter = (EntityLivingBase) evt.getTarget();
                @Nullable EntityPlayer entityPlayer = evt.getEntityPlayer();
                if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                    ItemStack weapon = entityPlayer.getHeldItemMainhand();
                    if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                        if (EntityLivingUtil.getTicksSinceLastSwing(entityPlayer) <= 0.8) {
                            return;
                        }
                        HashMap<String, Double> attributes = new HashMap<>();
                        List<ItemStack> modules = getModules(weapon);
                        for (ItemStack module : modules) {
                            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                            }
                        }
                        double range = attributes.getOrDefault("attackRange", 0.0);
                        if (entityPlayer.isSprinting()) {
                            range += attributes.getOrDefault("dashAttackRange", 0.0);
                        }
                        if (range > 0) {
                            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, hurter, range, entityLivingBase -> !entityLivingBase.equals(hurter) && !entityLivingBase.equals(entityPlayer));
                            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                float damage = EntityPlayerUtil.getAttackDamage(entityPlayer, entityLivingBase);
                                entityLivingBase.attackEntityFrom(DamageSource.causePlayerDamage(entityPlayer), damage * 0.5f * EntityLivingUtil.getTicksSinceLastSwing(entityPlayer));
                            }
                        } else if (range < 0) {
                            evt.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

//    @SubscribeEvent
//    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
//        if (evt.getEntityLiving() instanceof EntityPlayer && evt.getEntityLiving().isHandActive()) {
//            EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();
//            ItemStack weapon = entityPlayer.getHeldItem(entityPlayer.getActiveHand());
//            if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
//                HashMap<String, Double> attributes = new HashMap<>();
//                List<ItemStack> modules = getModules(weapon);
//                for (ItemStack module : modules) {
//                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
//                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
//                    }
//                }
//                double firingRate = attributes.getOrDefault("firing_rate", 0.0);
//                System.out.println(firingRate + ":LivingUpdateEvent");
//                if (firingRate != 0) {
////                    if (weapon.getItem() instanceof ItemBow) {
////                        firingRate *= 2;
////                    }
//                    if (firingRate > 1) {
//                        int number = (int) (firingRate);
//                        for (int i = 0; i < number; i++) {
//                            EntityLivingUtil.updateHeld(entityPlayer);
//                        }
//                        if (RandomUtil.percentageChance((firingRate - number) * 100)) {
//                            EntityLivingUtil.updateHeld(entityPlayer);
//                        }
//                    }
//                }
//            }
//        }
//    }

    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        EntityLivingBase entityLivingBase = evt.getEntityLiving();
        @Nonnull ItemStack weapon = evt.getItem();
        if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
            HashMap<String, Double> attributes = new HashMap<>();
            List<ItemStack> modules = getModules(weapon);
            for (ItemStack module : modules) {
                for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                    attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                }
            }
            double firingRate = 1 + attributes.getOrDefault("firing_rate", 0.0) * (weapon.getItem() instanceof ItemBow ? 2 : 1);
            if (firingRate != 0) {
                if (firingRate < 1) {
                    if (RandomUtil.percentageChance((1 - firingRate) * 100)) {
                        evt.setDuration(evt.getDuration() + 1);
                    }
                } else {
                    int number = (int) (firingRate - 1);
                    for (int i = 0; i < number; i++) {
                        evt.setDuration(evt.getDuration() - 1);
                    }
                    if (RandomUtil.percentageChance((firingRate - 1 - number) * 100)) {
                        evt.setDuration(evt.getDuration() - 1);
                    }
                }
            }
        }
    }


    public static HashMap<String, String> getTriggerElements(ItemStack weapon) {
        List<String> usableElement = new ArrayList<>();

        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                if (entry.getValue() > 0) {
                    switch (entry.getKey()) {
                        case "fire":
                            usableElement.add("fire");
                            break;
                        case "ice":
                            usableElement.add("ice");
                            break;
                        case "poison":
                            usableElement.add("poison");
                            break;
                        case "electricity":
                            usableElement.add("electricity");
                            break;
                        case "slash":
                            usableElement.add("slash");
                            break;
                        case "puncture":
                            usableElement.add("puncture");
                            break;
                        case "impact":
                            usableElement.add("impact");
                            break;
                    }
                }
            }
        }
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                if (entry.getValue() < 0) {
                    usableElement.remove(entry.getKey());
                }
            }
        }

        if (KuvaWeapon.hasType(weapon)) {
            usableElement.add(KuvaWeapon.getType(weapon));
        }
        if (usableElement.contains("fire") && usableElement.contains("ice")) {
            usableElement.add("explosion");
        }
        if (usableElement.contains("fire") && usableElement.contains("poison")) {
            usableElement.add("gas");
        }
        if (usableElement.contains("fire") && usableElement.contains("poison")) {
            usableElement.add("radiation");
        }
        if (usableElement.contains("ice") && usableElement.contains("poison")) {
            usableElement.add("virus");
        }
        if (usableElement.contains("ice") && usableElement.contains("electricity")) {
            usableElement.add("magnetic");
        }
        if (usableElement.contains("poison") && usableElement.contains("electricity")) {
            usableElement.add("corrosion");
        }

        HashMap<String, String> hashMap = new HashMap<>();
        for (String element : usableElement) {
            if (!hashMap.containsKey(element)) {
                hashMap.put(element, (int) ((double) Collections.frequency(usableElement, element) / usableElement.size() * 100) + "%");
            }
        }
        return hashMap;
    }

    public static String getTriggerElement(ItemStack weapon) {
        List<String> usableElement = new ArrayList<>();

        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                if (entry.getValue() > 0) {
                    switch (entry.getKey()) {
                        case "fire":
                            usableElement.add("fire");
                            break;
                        case "ice":
                            usableElement.add("ice");
                            break;
                        case "poison":
                            usableElement.add("poison");
                            break;
                        case "electricity":
                            usableElement.add("electricity");
                            break;
                        case "slash":
                            usableElement.add("slash");
                            break;
                        case "puncture":
                            usableElement.add("puncture");
                            break;
                        case "impact":
                            usableElement.add("impact");
                            break;
                    }
                }
            }
        }
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                if (entry.getValue() < 0) {
                    usableElement.remove(entry.getKey());
                }
            }
        }

        if (KuvaWeapon.hasType(weapon)) {
            usableElement.add(KuvaWeapon.getType(weapon));
        }
        if (usableElement.contains("fire") && usableElement.contains("ice")) {
            usableElement.add("explosion");
        }
        if (usableElement.contains("fire") && usableElement.contains("poison")) {
            usableElement.add("gas");
        }
        if (usableElement.contains("fire") && usableElement.contains("poison")) {
            usableElement.add("radiation");
        }
        if (usableElement.contains("ice") && usableElement.contains("poison")) {
            usableElement.add("virus");
        }
        if (usableElement.contains("ice") && usableElement.contains("electricity")) {
            usableElement.add("magnetic");
        }
        if (usableElement.contains("poison") && usableElement.contains("electricity")) {
            usableElement.add("corrosion");
        }
        if (usableElement.size() > 0) {
            return usableElement.get(RandomUtil.getInt(0, usableElement.size() - 1));
        } else {
            return null;
        }
    }

    public static float triggerElementEffect(DamageSource damageSource, EntityLivingBase hurter, EntityPlayer
            attacker, ItemStack itemStack, double damage, double triggerTime) {
        String type = getTriggerElement(itemStack);
        if (type == null) {
            return (float) damage;
        }
//        System.out.println(type);
        switch (type) {
            case "fire": {
                damageSource.setFireDamage();
                damage *= (EntityUtil.getFire(hurter) > 0 ? 1 : 0.5);
                if (hurter.getAbsorptionAmount() > 0) {
                    damage *= 0.5f;
                }
                if (EntityUtil.getFire(hurter) > 0) {
                    if (hurter.getActivePotionEffect(KuvaLichPotion.FIRE) != null) {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (120 * triggerTime), Math.min(5 - 1, hurter.getActivePotionEffect(KuvaLichPotion.FIRE).getAmplifier() + 1)));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (120 * triggerTime), 0));
                    }
                } else {
                    if (hurter.getActivePotionEffect(KuvaLichPotion.FIRE) != null) {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (60 * triggerTime), Math.min(5 - 1, hurter.getActivePotionEffect(KuvaLichPotion.FIRE).getAmplifier() + 1)));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (60 * triggerTime), 0));
                    }
                }
                hurter.setFire((int) (10 * triggerTime));
                break;
            }
            case "poison": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1;
                hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.POISON, (int) (150 * triggerTime), 0));
                break;
            }
            case "ice": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                if (hurter.getActivePotionEffect(KuvaLichPotion.ICE) != null) {
                    hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.ICE, (int) (120 * triggerTime), Math.min(90 - 1, hurter.getActivePotionEffect(KuvaLichPotion.ICE).getAmplifier() + 10)));
                } else {
                    hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.ICE, (int) (120 * triggerTime), 10 - 1));
                }
                break;
            }
            case "electricity": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                World world = hurter.world;
                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, hurter, 5 * triggerTime, entityLivingBase -> !entityLivingBase.equals(hurter) && !entityLivingBase.equals(attacker));
                for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                    world.addWeatherEffect(new EntityLightningBolt(world, entityLivingBase.posX, entityLivingBase.posY, entityLivingBase.posZ, false));
                    entityLivingBase.attackEntityFrom(DamageSource.LIGHTNING_BOLT, (float) (damage * 0.5f));

                    double offsetX = (Math.random() - 0.5) * entityLivingBase.width;
                    double offsetY = entityLivingBase.height * 0.25 + (Math.random() * entityLivingBase.height * 0.75);
                    double offsetZ = (Math.random() - 0.5) * entityLivingBase.width;
                    Vec3d position = new Vec3d(entityLivingBase.posX + offsetX, entityLivingBase.posY + offsetY, entityLivingBase.posZ + offsetZ);
                    KuvaLich.network.sendTo(new DamagePacket((float) (damage * 0.5f), position, DamageInfo.DamageColor.WHITE.getColor()), (EntityPlayerMP) attacker);
                }
                break;
            }
            case "slash": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.25 : 1.5;

                if (hurter.isPotionActive(KuvaLichPotion.VIRUS)) {
                    int amplifier = hurter.getActivePotionEffect(KuvaLichPotion.VIRUS).getAmplifier();
                    damage = damage + damage * 0.25f * (amplifier + 1);
                }

                float slashDamage = (float) (damage * 0.35f);
                new SynchronizationTask(20, 20) {
                    private int time = 0;

                    @Override
                    public void run() {
                        if (time++ >= 6 * triggerTime || hurter.isDead) {
                            this.cancel();
                            return;
                        }

                        double offsetX = (Math.random() - 0.5) * hurter.width;
                        double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                        double offsetZ = (Math.random() - 0.5) * hurter.width;
                        Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);
                        KuvaLich.network.sendTo(new DamagePacket(slashDamage, position, DamageInfo.DamageColor.WHITE.getColor()), (EntityPlayerMP) attacker);

                        if (hurter.getHealth() - slashDamage * 2 > 0) {
                            hurter.setHealth(hurter.getHealth() - slashDamage);
                        } else {
                            EntityLivingUtil.kill(hurter, DamageSource.causeMobDamage(attacker));
                            this.cancel();
                        }
                    }

                }.start();
                break;
            }
            case "puncture": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1.25;
                hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.PUNCTURE, (int) (120 * triggerTime), 0));
                break;
            }
            case "impact": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 1;
                LivingKnockBackEvent livingKnockBackEvent;
                if (attacker instanceof EntityPlayer) {
                    livingKnockBackEvent = ForgeHooks.onLivingKnockBack(hurter, attacker, 1.25f * EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker), (hurter.posX - attacker.posX) / 6, (hurter.posZ - attacker.posZ) / 6);
                } else {
                    livingKnockBackEvent = ForgeHooks.onLivingKnockBack(hurter, attacker, 1.25f, (hurter.posX - attacker.posX) / 6, (hurter.posZ - attacker.posZ) / 6);
                }
                if (!livingKnockBackEvent.isCanceled()) {
                    hurter.motionX = livingKnockBackEvent.getRatioX();
                    hurter.motionZ = livingKnockBackEvent.getRatioZ();
                    hurter.motionY = livingKnockBackEvent.getStrength();
                }
                break;
            }
            case "magnetic": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 2.0 : 0.25;
                hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.MAGNETIC, (int) (120 * triggerTime), 0));
                break;
            }
            case "radiation": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1.5;
                damage = damage + hurter.getHealth() * 0.02 + hurter.getMaxHealth() * 0.01;
                break;
            }
            case "virus": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.1 : 1.0;
                if (hurter.getAbsorptionAmount() <= 0) {
                    if (hurter.getActivePotionEffect(KuvaLichPotion.VIRUS) != null) {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.VIRUS, (int) (120 * triggerTime), Math.min(13 - 1, hurter.getActivePotionEffect(KuvaLichPotion.VIRUS).getAmplifier() + 1)));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.VIRUS, (int) (120 * triggerTime), 4 - 1));
                    }
                }
                break;
            }
            case "corrosion": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.1 : 1.25;
                if (hurter.getAbsorptionAmount() <= 0) {
                    if (hurter.getActivePotionEffect(KuvaLichPotion.CORROSION) != null) {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.CORROSION, (int) (160 * triggerTime), Math.min(4 - 1, hurter.getActivePotionEffect(KuvaLichPotion.CORROSION).getAmplifier() + 1)));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.CORROSION, (int) (160 * triggerTime), 0));
                    }
                }
                break;
            }
            case "explosion": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.75 : 1.5;
                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, hurter, 6, entityLivingBase -> !entityLivingBase.equals(hurter) && !entityLivingBase.equals(attacker));
                for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                    entityLivingBase.attackEntityFrom(DamageSource.causeExplosionDamage(entityLivingBase), (float) damage);

                    double offsetX = (Math.random() - 0.5) * entityLivingBase.width;
                    double offsetY = entityLivingBase.height * 0.25 + (Math.random() * entityLivingBase.height * 0.75);
                    double offsetZ = (Math.random() - 0.5) * entityLivingBase.width;
                    Vec3d position = new Vec3d(entityLivingBase.posX + offsetX, entityLivingBase.posY + offsetY, entityLivingBase.posZ + offsetZ);
                    KuvaLich.network.sendTo(new DamagePacket((float) damage, position, DamageInfo.DamageColor.WHITE.getColor()), (EntityPlayerMP) attacker);
                }
                @Nonnull Explosion explosion = attacker.world.createExplosion(null, hurter.posX, hurter.posY, hurter.posZ, 3, false);
                break;
            }
            case "gas": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                EntityUtil.getNearbyEntities(EntityLivingBase.class, attacker, RandomUtil.getInt(3, 6), entityLivingBase -> !entityLivingBase.equals(attacker)).forEach((entityLivingBase) -> {
                    if (hurter.getActivePotionEffect(KuvaLichPotion.FIRE) != null) {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (120 * triggerTime), Math.min(5 - 1, hurter.getActivePotionEffect(KuvaLichPotion.FIRE).getAmplifier() + 1)));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.FIRE, (int) (120 * triggerTime), 0));
                    }
                });
                break;
            }
            default: {
                break;
            }
        }
        return (float) damage;
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        EntityPlayer entityPlayer = evt.getEntityPlayer();
        if (!evt.getEntity().world.isRemote) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && ItemModule.hasBase(bow)) {
                HashMap<String, Double> attributes = new HashMap<>();
                List<ItemStack> modules = getModules(bow);
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }
                double multishot = attributes.getOrDefault("multishot", 0.0);
                if (multishot > 0) {
                    int charge = evt.getCharge();
                    float velocity = getBowVelocity(charge);
                    if (velocity < 0.1f) {
                        return;
                    }
                    if (multishot > 1) {
                        int number = (int) multishot;
                        for (int i = 0; i < number; i++) {
                            fireArrow(entityPlayer, entityPlayer.world, velocity, bow, true);
                        }
                        if (RandomUtil.percentageChance((multishot - number) * 100)) {
                            fireArrow(entityPlayer, entityPlayer.world, velocity, bow, true);
                        }
                    } else {
                        if (RandomUtil.percentageChance(multishot * 100)) {
                            fireArrow(entityPlayer, entityPlayer.world, velocity, bow, true);
                        }
                    }
                } else if (multishot < 0 && multishot > -1) {
                    if (RandomUtil.percentageChance(Math.abs(multishot) * 100)) {
                        evt.setCanceled(true);
                    }
                } else if (multishot <= -1) {
                    evt.setCanceled(true);
                }
            }
        }
    }

    private static float getBowVelocity(int charge) {
        float f = (float) charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    private static void fireArrow(EntityPlayer player, World world, float velocity, ItemStack bow,
                                  boolean infiniteArrows) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        ItemArrow itemarrow = (arrowStack.getItem() instanceof ItemArrow) ? (ItemArrow) arrowStack.getItem() : (ItemArrow) Items.ARROW;
        EntityArrow arrow = itemarrow.createArrow(world, arrowStack, player);
        arrow.shoot(player, player.rotationPitch, player.rotationYaw, 0.0F, velocity * 3.0F, 5.0F);
        arrow.addTag("multishot");
        if (velocity == 1.0F) {
            arrow.setIsCritical(true);
        }

        applyBowEnchantments(arrow, bow);

        if (infiniteArrows) {
            arrow.pickupStatus = EntityArrow.PickupStatus.CREATIVE_ONLY;
        }

        world.spawnEntity(arrow);
    }

    private static void applyBowEnchantments(EntityArrow arrow, ItemStack bow) {
        int power = EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, bow);
        if (power > 0) {
            arrow.setDamage(arrow.getDamage() + (double) power * 0.5D + 0.5D);
        }

        int knockback = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, bow);
        if (knockback > 0) {
            arrow.setKnockbackStrength(knockback);
        }

        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAME, bow) > 0) {
            arrow.setFire(100);
        }
    }
}
