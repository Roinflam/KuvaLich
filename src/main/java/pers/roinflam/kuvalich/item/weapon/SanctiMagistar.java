package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber
public class SanctiMagistar extends KuvaWeaponBase {

    public SanctiMagistar(@NotNull String name) {
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
        double criticalStrikeProbability = 30 / 100.0;
        double criticalStrikeMultiplier = 20 / 10.0;
        double triggerChance = 20 / 100.0;

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
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            EntityLivingBase hurter = evt.getEntityLiving();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof SanctiMagistar) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        float heal = KuvaWeapon.getMagnification(itemStack, evt.getAmount() * 0.05f, 0.5) / 50;
                        new SynchronizationTask(5, 1) {
                            private int ticks = 0;

                            @Override
                            public void run() {
                                if (++ticks > 50) {
                                    this.cancel();
                                    return;
                                }
                                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, attacker, 15, entityLivingBase -> entityLivingBase.getClass() == attacker.getClass() && !entityLivingBase.equals(hurter));
                                for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                    entityLivingBase.heal(heal);
                                }
                            }

                        }.start();
                    }
                }
            } else if (damageSource.getTrueSource() == null) {
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                    if (itemStack.getItem() instanceof SanctiMagistar) {
                        evt.setAmount(evt.getAmount() - KuvaWeapon.getMagnification(itemStack, evt.getAmount() * 0.5f, 2));
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
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageSanctiMagistar));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedSanctiMagistar, 2));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ConfigKuvaWeapon.movementSpeedSanctiMagistar, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

