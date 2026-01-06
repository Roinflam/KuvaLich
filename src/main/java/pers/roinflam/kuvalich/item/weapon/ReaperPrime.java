package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 收割者Prime（Reaper Prime）
 *
 * 武器特性：
 * 1. 攻击吸血10%
 * 2. 右键蓄力（0.5-2秒）：
 *    - 向前高速冲刺
 *    - 冲刺距离随蓄力时间增加（0.5x - 1.5x）
 *    - 冷却时间随蓄力时间增加（1.5-4.5秒）
 *
 * 战术思路：
 * - 持续战斗能力强（吸血维持）
 * - 机动性高（冲刺技能）
 * - 可快速接近或撤离
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：35%
 * - 暴击倍率：2.5x
 * - 触发几率：25%
 */
@Mod.EventBusSubscriber
public class ReaperPrime extends KuvaWeaponBase {

    public ReaperPrime(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.35, 2.5, 0.25);
    }

    /**
     * 攻击吸血10%
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, ReaperPrime.class);

        if (weapon != null) {
            // 治疗 = 造成伤害的10%
            float healAmount = KuvaWeapon.getMagnification(weapon, evt.getAmount() * 0.1f);
            attacker.heal(healAmount);
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
     * 主动技能：冲刺
     * 蓄力0.5-2秒，冲刺距离和CD随蓄力时间增加
     */
    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, EntityLivingBase entityLiving, int timeLeft) {
        if (!(entityLiving instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) entityLiving;
        int chargeTime = (getMaxItemUseDuration(stack) - timeLeft) * 2;

        // 至少蓄力0.5秒（10 ticks）
        if (chargeTime >= 10) {
            // 冲刺倍率：0.5x - 1.5x（随蓄力时间线性增长）
            double magnification = MathHelper.clamp(
                    (chargeTime - 10) / 20.0,  // 0.5秒=0, 2秒=1.5
                    0.5,  // 最小0.5倍
                    1.5   // 最大1.5倍
            );

            // 获取移动速度并冲刺
            double speed = KuvaWeapon.getMagnification(
                    stack,
                    player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue(),
                    3
            );

            player.addVelocity(
                    player.getLookVec().x * speed * 4.25 * 5 * magnification,
                    0,
                    player.getLookVec().z * speed * 4.25 * 5 * magnification
            );

            // 冷却时间：1.5-4.5秒（随蓄力时间增加）
            player.getCooldownTracker().setCooldown(stack.getItem(), (int) (60 * magnification));
            player.addStat(StatList.getObjectUseStats(stack.getItem()));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageReaperPrime));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedReaperPrime, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedReaperPrime, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}