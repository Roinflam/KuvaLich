package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class ArcaTitron extends KuvaWeaponBase {

    public ArcaTitron(@NotNull String name) {
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
        double criticalStrikeProbability = 24 / 100.0;
        double criticalStrikeMultiplier = 20 / 10.0;
        double triggerChance = 38 / 100.0;

        weaponModule.setDouble("damage", damage);
        weaponModule.setDouble("criticalStrikeProbability", criticalStrikeProbability);
        weaponModule.setDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
        weaponModule.setDouble("triggerChance", triggerChance);

        tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
        return itemStack;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof ArcaTitron) {
                        if (attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON) != null) {
                            attacker.addPotionEffect(new PotionEffect(KuvaLichPotion.ARCA_TITRON, (int) KuvaWeapon.getMagnification(itemStack, 400), Math.min(9, attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON).getAmplifier() + 1)));
                        } else {
                            attacker.addPotionEffect(new PotionEffect(KuvaLichPotion.ARCA_TITRON, (int) KuvaWeapon.getMagnification(itemStack, 400), 5));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() instanceof EntityLivingBase) {
                EntityLivingBase hurter = (EntityLivingBase) evt.getTarget();
                @Nullable EntityPlayer attacker = evt.getEntityPlayer();
                if (attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON) != null) {
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                        if (itemStack.getItem() instanceof ArcaTitron) {
                            int level = (attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON).getAmplifier() + 1);
                            evt.setDamageModifier(evt.getDamageModifier() + KuvaWeapon.getMagnification(itemStack, evt.getDamageModifier() * level * 0.05f));
                        }
                    }
                }
            }
        }
    }


    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, EntityLivingBase entity, EntityLivingBase attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageArcaTitron));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedArcaTitron, 2));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ConfigKuvaWeapon.movementSpeedArcaTitron, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

