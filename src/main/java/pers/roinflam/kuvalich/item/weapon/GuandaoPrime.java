package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class GuandaoPrime extends KuvaWeaponBase {

    public GuandaoPrime(@NotNull String name) {
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
        double criticalStrikeProbability = 32 / 100.0;
        double criticalStrikeMultiplier = 24 / 10.0;
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
            if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    @NotNull ItemStack itemStack = attacker.getHeldItem(attacker.getActiveHand());
                    if (itemStack.getItem() instanceof GuandaoPrime) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (attacker.getActivePotionEffect(KuvaLichPotion.GUANDAO_PRIME) != null) {
                            attacker.addPotionEffect(new PotionEffect(KuvaLichPotion.GUANDAO_PRIME, (int) KuvaWeapon.getMagnification(itemStack, 100), attacker.getActivePotionEffect(KuvaLichPotion.GUANDAO_PRIME).getAmplifier() + 5));
                        } else {
                            attacker.addPotionEffect(new PotionEffect(KuvaLichPotion.GUANDAO_PRIME, (int) KuvaWeapon.getMagnification(itemStack, 100), 5));
                        }
                        float damage = KuvaWeapon.getMagnification(itemStack, (evt.getAmount() + hurter.getMaxHealth() * 0.025) / 20);
                        new SynchronizationTask(5, 5) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 20 || hurter.isDead) {
                                    this.cancel();
                                    return;
                                }
                                if (attacker instanceof EntityPlayer) {
                                    double offsetX = (Math.random() - 0.5) * hurter.width;
                                    double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                                    double offsetZ = (Math.random() - 0.5) * hurter.width;
                                    Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);

                                    KuvaLich.network.sendTo(new DamagePacket(damage, position, DamageInfo.DamageColor.WHITE.getColor()), (EntityPlayerMP) attacker);
                                }
                                if (hurter.getHealth() - damage * 2 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    EntityLivingUtil.kill(hurter, DamageSource.causeMobDamage(attacker).setDamageBypassesArmor());
                                    this.cancel();
                                }
                            }

                        }.start();
                    }
                }
            }
        }
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamageGuandaoPrime, 2));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedGuandaoPrime));
    }

    @Override
    public double getMovementSpeedAmount(@NotNull ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.movementSpeedGuandaoPrime));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}

