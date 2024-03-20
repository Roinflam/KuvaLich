package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
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
public class Pennant extends KuvaWeaponBase {

    public Pennant(@NotNull String name) {
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
        double triggerChance = 10 / 100.0;

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
                    if (itemStack.getItem() instanceof Pennant) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        if (RandomUtil.percentageChance(32)) {
                            evt.setAmount(evt.getAmount() * 2.4f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public @NotNull ActionResult<ItemStack> onItemRightClick(World worldIn, @NotNull EntityPlayer playerIn, EnumHand handIn) {
        @NotNull ItemStack itemstack = playerIn.getHeldItem(handIn);
        playerIn.setActiveHand(handIn);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
    }

    @Override
    public @NotNull EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.BOW;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, EntityLivingBase entityLiving, int timeLeft) {
        if (entityLiving instanceof EntityPlayer) {
            @NotNull EntityPlayer entityplayer = (EntityPlayer) entityLiving;
            if ((getMaxItemUseDuration(stack) - timeLeft) * 2 >= 40) {
//                entityplayer.setLocationAndAngles(0, 0, 0, 0, 0);
                double speed = entityplayer.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
                entityplayer.addVelocity(entityplayer.getLookVec().x * speed * 4.25 * 10, 0, entityplayer.getLookVec().z * speed * 4.25 * 10);
                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, entityplayer, KuvaWeapon.getMagnification(stack, 12, 4), entityLivingBase -> !entityLivingBase.equals(entityplayer));
                for (@Nonnull EntityLivingBase hurter : entities) {
                    if (hurter.attackEntityFrom(DamageSource.causePlayerDamage(entityplayer).setDamageBypassesArmor(), hurter.getHealth() * 0.2f)) {
                        new SynchronizationTask(5, 5) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                if (++tick > 20 || hurter.isDead) {
                                    this.cancel();
                                    return;
                                }
                                float damage = KuvaWeapon.getMagnification(stack, (hurter.getMaxHealth() - hurter.getHealth()) * 0.25f / 20);
                                damage = damage * 0.3f + damage * tick / 10 * 0.7f;

                                double offsetX = (Math.random() - 0.5) * hurter.width;
                                double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                                double offsetZ = (Math.random() - 0.5) * hurter.width;
                                Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);

                                KuvaLich.network.sendTo(new DamagePacket(damage, position, DamageInfo.DamageColor.WHITE.getColor()), (EntityPlayerMP) entityplayer);

                                if (hurter.getHealth() - damage * 2 > 0) {
                                    hurter.setHealth(hurter.getHealth() - damage);
                                } else {
                                    EntityLivingUtil.kill(hurter, DamageSource.causePlayerDamage(entityplayer).setDamageBypassesArmor());
                                    this.cancel();
                                }
                            }

                        }.start();
                    }
                }
                entityplayer.resetCooldown();
                entityplayer.getCooldownTracker().setCooldown(stack.getItem(), 200);
                entityplayer.addStat(StatList.DAMAGE_DEALT);
            }
        }
    }

    @Override
    public double getAttackDamageAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackDamagePennant));
    }

    @Override
    public double getAttackSpeedAmount(@NotNull ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ConfigKuvaWeapon.attackSpeedPennant, 2));
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

