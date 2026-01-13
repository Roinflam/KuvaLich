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
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 巨剑Prime（1.20.1版本，业务逻辑100%不变）
 * Gram Prime (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class GramPrime extends KuvaWeaponBase {

    public GramPrime(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.6, 0.32);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        LivingEntity hurter = (LivingEntity) event.getTarget();
        Player attacker = event.getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, GramPrime.class, 0.75);

        if (weapon != null) {
            float cooldownProgress = EntityLivingUtil.getTicksSinceLastSwing(attacker);

            @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                    LivingEntity.class, hurter,
                    KuvaWeapon.getMagnification(weapon, 4, 6),
                    e -> !e.equals(hurter) && !e.equals(attacker)
            );

            for (@Nonnull LivingEntity nearbyEnemy : entities) {
                float mainTargetDamage = EntityPlayerUtil.getAttackDamage(attacker, nearbyEnemy);
                float aoeDamage = mainTargetDamage * 0.66f * cooldownProgress;

                nearbyEnemy.hurt(attacker.level().damageSources().playerAttack(attacker), aoeDamage);
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageGramPrime.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedGramPrime.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedGramPrime.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}