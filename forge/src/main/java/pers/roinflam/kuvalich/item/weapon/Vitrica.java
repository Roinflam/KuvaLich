package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 维特利卡（1.20.1版本，使用动态属性系统）
 * Vitrica (1.20.1 version, using dynamic attribute system)
 */
@Mod.EventBusSubscriber
public class Vitrica extends AbstractKuvaWeapon {

    public Vitrica(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.23, 2.3, 0.33);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Vitrica.class);

        if (weapon != null) {
            // ✅ 替换为动态属性系统
            int newAmplifier = DynamicAttributeManager.has(hurter, DynamicAttributes.VITRICA)
                    ? Math.min(7, 1)
                    : 0;

            DynamicAttributeManager.apply(
                    hurter,
                    DynamicAttributes.VITRICA.createInstance(
                            (int) KuvaWeaponUtil.getMagnification(weapon, 200),
                            newAmplifier
                    )
            );
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Vitrica.class);

        if (weapon != null) {
            // ✅ 使用动态属性检测
            float multiplier = DynamicAttributeManager.has(hurter, DynamicAttributes.VITRICA)
                    ? 1.25f
                    : 0.75f;

            event.setAmount(KuvaWeaponUtil.getMagnification(weapon, event.getAmount() * multiplier, 2));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageVitrica.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedVitrica.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeaponUtil.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedVitrica.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}