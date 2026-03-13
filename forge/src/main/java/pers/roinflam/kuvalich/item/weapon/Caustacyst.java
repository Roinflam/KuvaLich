package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * 腐蚀镰刀（1.20.1版本，使用动态属性系统）
 * Caustacyst (1.20.1 version, using dynamic attribute system)
 */
@Mod.EventBusSubscriber
public class Caustacyst extends AbstractKuvaWeapon {

    public Caustacyst(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.09, 2.0, 0.37);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Caustacyst.class);

        if (weapon != null) {
            String weaponType = KuvaWeaponUtil.getType(weapon);

            if (weaponType.equalsIgnoreCase("poison")) {
                // ✅ 替换为动态属性系统
                DynamicAttributeManager.apply(
                        hurter,
                        DynamicAttributes.VIRUS.createInstance(
                                (int) KuvaWeaponUtil.getMagnification(weapon, 150),
                                9
                        )
                );
            } else {
                event.setAmount(KuvaWeaponUtil.getMagnification(weapon, event.getAmount() * 0.75f));
                // ✅ 替换为动态属性系统
                DynamicAttributeManager.apply(
                        hurter,
                        DynamicAttributes.VIRUS.createInstance(
                                (int) KuvaWeaponUtil.getMagnification(weapon, 150),
                                0
                        )
                );
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageCaustacyst.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedCaustacyst.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedCaustacyst.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}