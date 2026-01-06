package pers.roinflam.kuvalich.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 棱晶维利图（Prisma Veritux）- 长矛
 *
 * 武器特性：
 * 1. 目标有护甲：无视护甲
 * 2. 目标无护甲：伤害x1.25
 * 3. 攻击距离+1格（长矛优势）
 * 4. 可以破盾
 *
 * 战术思路：
 * - 类似KuvaShildeg但攻击距离更远
 * - 适合保持距离作战
 * - 全局收益武器
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：30%
 * - 暴击倍率：2.0x
 * - 触发几率：20%
 */
@Mod.EventBusSubscriber
public class PrismaVeritux extends KuvaWeaponBase {

    public PrismaVeritux(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.30, 2.0, 0.20);
    }

    /**
     * 护甲检测与伤害调整
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, PrismaVeritux.class);

        if (weapon != null) {
            if (hurter.getTotalArmorValue() > 0) {
                // 有护甲：无视护甲
                evt.getSource().setDamageBypassesArmor();
            } else {
                // 无护甲：伤害提升25%
                evt.setAmount(evt.getAmount() * KuvaWeapon.getMagnification(weapon, 1.25f));
            }
        }
    }

    /**
     * 增加攻击距离
     */
    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);
        if (slot == EntityEquipmentSlot.MAINHAND) {
            // 攻击距离+1格
            multimap.put(
                    EntityPlayer.REACH_DISTANCE.getName(),
                    new AttributeModifier(
                            KuvaWeaponBase.REACH_DISTANCE,
                            Reference.MOD_ID + ":" + EntityPlayer.REACH_DISTANCE.getName(),
                            KuvaWeapon.getMagnification(stack, 1, 2),  // +1格
                            2  // 操作符2：加法
                    )
            );
        }
        return multimap;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamagePrismaVeritux));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedPrismaVeritux));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedPrismaVeritux));
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