package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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
import java.util.Collection;

@Mod.EventBusSubscriber
public class Machete extends KuvaWeaponBase {

    public Machete(@NotNull String name) {
        super(name);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntityLiving().world.isRemote && evt.getSource().getImmediateSource() instanceof EntityPlayer) {
            if ((evt.getEntityLiving() instanceof EntityAnimal || evt.getEntityLiving() instanceof EntityMob) && evt.getEntityLiving().isNonBoss()) {
                @Nullable EntityPlayer entityPlayer = (EntityPlayer) evt.getSource().getImmediateSource();
                if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = entityPlayer.getHeldItem(entityPlayer.getActiveHand());
                    if (itemStack.getItem() instanceof Machete) {
                        Collection<EntityItem> drops = evt.getDrops();
                        for (EntityItem drop : drops) {
                            ItemStack dropStack = drop.getItem();
                            if (!(dropStack.getItem() instanceof ItemArmor) && !(dropStack.getItem() instanceof ItemSword) && !(dropStack.getItem() instanceof ItemTool)) {
                                dropStack.setCount(dropStack.getCount() * RandomUtil.getInt(1, 3));
                            }
                        }
                    }
                }
            }
        }
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
        double criticalStrikeProbability = 10 / 100.0;
        double criticalStrikeMultiplier = 15 / 10.0;
        double triggerChance = 15 / 100.0;

        weaponModule.setDouble("damage", damage);
        weaponModule.setDouble("criticalStrikeProbability", criticalStrikeProbability);
        weaponModule.setDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
        weaponModule.setDouble("triggerChance", triggerChance);

        tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
        return itemStack;
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageMachete));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedMachete, 2));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.movementSpeedPennant, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

