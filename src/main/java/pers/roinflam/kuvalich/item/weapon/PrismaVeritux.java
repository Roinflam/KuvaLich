package pers.roinflam.kuvalich.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class PrismaVeritux extends KuvaWeaponBase {

    public PrismaVeritux(@NotNull String name) {
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
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof PrismaVeritux) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (hurter.getTotalArmorValue() > 0) {
                            damageSource.setDamageBypassesArmor();
                        } else {
                            evt.setAmount(evt.getAmount() * KuvaWeapon.getMagnification(itemStack, 1.25f));
                        }
                    }
                }
            }
        }
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(EntityPlayer.REACH_DISTANCE.getName(), new AttributeModifier(KuvaWeaponBase.REACH_DISTANCE, Reference.MOD_ID + ":" + EntityPlayer.REACH_DISTANCE.getName(), KuvaWeapon.getMagnification(stack, 1, 2), 2));
        }
        return multimap;
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamagePrismaVeritux));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedPrismaVeritux));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ConfigKuvaWeapon.movementSpeedPrismaVeritux));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, EntityLivingBase entity, EntityLivingBase attacker) {
        return true;
    }
}

