package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMobEffects;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.HiddenEffectHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 腐蚀镰刀（1.20.1版本，业务逻辑100%不变）
 * Caustacyst (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class Caustacyst extends KuvaWeaponBase {

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
            String weaponType = KuvaWeapon.getType(weapon);

            if (weaponType.equalsIgnoreCase("poison")) {
                // ✅ 使用 HiddenEffectHelper
                HiddenEffectHelper.apply(
                        hurter,
                        KuvaLichMobEffects.POISON.get(),
                        (int) KuvaWeapon.getMagnification(weapon, 150),
                        1
                );
            } else {
                event.setAmount(KuvaWeapon.getMagnification(weapon, event.getAmount() * 0.75f));
                // ✅ 使用 HiddenEffectHelper
                HiddenEffectHelper.apply(
                        hurter,
                        KuvaLichMobEffects.POISON.get(),
                        (int) KuvaWeapon.getMagnification(weapon, 150),
                        0
                );
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageCaustacyst.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedCaustacyst.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedCaustacyst.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}