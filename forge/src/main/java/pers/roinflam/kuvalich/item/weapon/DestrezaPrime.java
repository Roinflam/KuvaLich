package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 德斯特拉Prime（1.20.1版本，业务逻辑100%不变）
 * Destreza Prime (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class DestrezaPrime extends KuvaWeaponBase {

    public DestrezaPrime(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 3.0, 0.20);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        LivingEntity hurter = (LivingEntity) event.getTarget();
        Player attacker = event.getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, DestrezaPrime.class, 0.66);

        if (weapon != null) {
            float cooldownProgress = attacker.getAttackStrengthScale(0.5F);
            float mainDamage = EntityPlayerUtil.getAttackDamage(attacker, hurter);
            float extraDamage = mainDamage * 0.333f * cooldownProgress;

            if (hurter.hurt(attacker.level().damageSources().playerAttack(attacker), extraDamage)) {
                hurter.invulnerableTime = hurter.invulnerableDuration / 2;
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageDestrezaPrime.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedDestrezaPrime.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedDestrezaPrime.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}