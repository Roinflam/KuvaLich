package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
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

import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 战旗（Pennant）
 *
 * 武器特性：
 * 1. 被动：32%概率造成2.4倍暴击伤害
 * 2. 主动（右键蓄力2秒+）：
 *    - 向前高速冲锋
 *    - 对12格范围内敌人造成20%当前生命值伤害
 *    - 附加持续4秒流血效果（总伤害=损失生命的25%）
 *    - 冷却时间：10秒
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：32%
 * - 暴击倍率：2.4x
 * - 触发几率：10%
 */
@Mod.EventBusSubscriber
public class Pennant extends KuvaWeaponBase {

    public Pennant(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.4, 0.10);
    }

    /**
     * 被动效果：32%概率2.4倍暴击
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        // ✅ 使用工具类检查武器和攻击时机
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Pennant.class);

        if (weapon != null && RandomUtil.percentageChance(32)) {
            // 触发额外暴击
            evt.setAmount(evt.getAmount() * 2.4f);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        playerIn.setActiveHand(handIn);
        return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.BOW;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    /**
     * 主动技能：冲锋突刺
     * 蓄力2秒后松开，向前冲刺并对范围敌人造成流血
     */
    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, EntityLivingBase entityLiving, int timeLeft) {
        if (!(entityLiving instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) entityLiving;
        int chargeTime = (getMaxItemUseDuration(stack) - timeLeft) * 2;

        // 至少蓄力2秒（40 ticks）
        if (chargeTime >= 40) {
            // 向前冲刺
            double speed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
            player.addVelocity(
                    player.getLookVec().x * speed * 4.25 * 10,
                    0,
                    player.getLookVec().z * speed * 4.25 * 10
            );

            // 获取范围内敌人
            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class, player,
                    KuvaWeapon.getMagnification(stack, 12, 4),
                    e -> !e.equals(player)
            );

            for (@Nonnull EntityLivingBase hurter : entities) {
                // 造成20%当前生命值伤害
                if (hurter.attackEntityFrom(
                        DamageSource.causePlayerDamage(player).setDamageBypassesArmor(),
                        hurter.getHealth() * 0.2f)) {

                    // 附加持续流血效果（4秒，每0.25秒触发）
                    new SynchronizationTask(5, 5) {
                        private int tick = 0;

                        @Override
                        public void run() {
                            // 持续4秒（20 ticks）或目标死亡
                            if (++tick > 20 || hurter.isDead) {
                                this.cancel();
                                return;
                            }

                            // 伤害 = (最大生命-当前生命) * 0.25 / 20
                            // 前期低，越到后期越高（随损失生命递增）
                            float damage = KuvaWeapon.getMagnification(stack,
                                    (hurter.getMaxHealth() - hurter.getHealth()) * 0.25f / 20);
                            damage = damage * 0.3f + damage * tick / 10 * 0.7f; // 伤害递增

                            // 显示伤害数字（客户端）
                            double offsetX = (Math.random() - 0.5) * hurter.width;
                            double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                            double offsetZ = (Math.random() - 0.5) * hurter.width;
                            Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);

                            KuvaLich.network.sendTo(
                                    new DamagePacket(damage, position, DamageInfo.DamageColor.WHITE.getColor()),
                                    (EntityPlayerMP) player
                            );

                            // 造成真实伤害
                            if (hurter.getHealth() - damage * 2 > 0) {
                                hurter.setHealth(hurter.getHealth() - damage);
                            } else {
                                EntityLivingUtil.kill(hurter, DamageSource.causePlayerDamage(player).setDamageBypassesArmor());
                                this.cancel();
                            }
                        }
                    }.start();
                }
            }

            // 重置攻击冷却并设置技能CD（10秒）
            player.resetCooldown();
            player.getCooldownTracker().setCooldown(stack.getItem(), 200);
            player.addStat(StatList.DAMAGE_DEALT);
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamagePennant));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedPennant, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedPennant, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}