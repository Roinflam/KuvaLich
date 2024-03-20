package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class DestrezaPrime extends KuvaWeaponBase {

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

        double damage = RandomUtil.getInt(80, 120) / 100.0;
        if (RandomUtil.percentageChance(95)) {
            damage = 1;
        }
        double criticalStrikeProbability = 32 / 100.0;
        double criticalStrikeMultiplier = 30 / 10.0;
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

    public DestrezaPrime(@NotNull String name) {
        super(name);
    }

    @SubscribeEvent
    public static void onAttackEntity(@NotNull AttackEntityEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getTarget() instanceof EntityLivingBase) {
                EntityLivingBase hurter = (EntityLivingBase) evt.getTarget();
                @Nullable EntityPlayer attacker = evt.getEntityPlayer();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof DestrezaPrime) {
                        if (EntityLivingUtil.getTicksSinceLastSwing(attacker) <= 0.66) {
                            return;
                        }
                        float damage = EntityPlayerUtil.getAttackDamage(attacker, hurter);
                        if (hurter.attackEntityFrom(DamageSource.causePlayerDamage(attacker), damage * 0.333f * EntityLivingUtil.getTicksSinceLastSwing(attacker))) {
                            hurter.hurtResistantTime = hurter.maxHurtResistantTime / 2;
                        }
                    }
                }
            }
        }
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageDestrezaPrime));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedDestrezaPrime));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.movementSpeedDestrezaPrime));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

