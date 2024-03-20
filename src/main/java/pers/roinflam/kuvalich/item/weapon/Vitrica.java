package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class Vitrica extends KuvaWeaponBase {

    public Vitrica(@NotNull String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

        double damage = RandomUtil.getInt(80, 120) / 100.0;
        if (RandomUtil.percentageChance(95)) {
            damage = 1;
        }
        double criticalStrikeProbability = 23 / 100.0;
        double criticalStrikeMultiplier = 23 / 10.0;
        double triggerChance = 33 / 100.0;

        weaponModule.setDouble("damage", damage);
        weaponModule.setDouble("criticalStrikeProbability", criticalStrikeProbability);
        weaponModule.setDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
        weaponModule.setDouble("triggerChance", triggerChance);

        tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
        return itemStack;
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof Vitrica) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (hurter.getActivePotionEffect(KuvaLichPotion.VITRICA) != null) {
                            hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.VITRICA, (int) KuvaWeapon.getMagnification(itemStack, 200), Math.min(7, hurter.getActivePotionEffect(KuvaLichPotion.VITRICA).getAmplifier() + 1)));
                        } else {
                            hurter.addPotionEffect(new PotionEffect(KuvaLichPotion.VITRICA, (int) KuvaWeapon.getMagnification(itemStack, 200), 0));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof Vitrica) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (hurter.getActivePotionEffect(KuvaLichPotion.VITRICA) != null) {
                            evt.setAmount(KuvaWeapon.getMagnification(itemStack, evt.getAmount() * 1.25f, 2));
                        } else {
                            evt.setAmount(KuvaWeapon.getMagnification(itemStack, evt.getAmount() * 0.75f, 2));
                        }
                    }
                }
            }
        }
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageVitrica));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedVitrica, 2));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ConfigKuvaWeapon.movementSpeedVitrica, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

