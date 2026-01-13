package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 圣教权杖（1.20.1版本，业务逻辑100%不变）
 * Sancti Magistar (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class SanctiMagistar extends KuvaWeaponBase {

    public SanctiMagistar(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.30, 2.0, 0.20);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        DamageSource damageSource = event.getSource();
        LivingEntity hurter = event.getEntity();

        if (damageSource.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) damageSource.getEntity();
            ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, SanctiMagistar.class);

            if (weapon != null) {
                float healAmount = KuvaWeapon.getMagnification(weapon, event.getAmount() * 0.05f, 0.5f) / 50;

                new SynchronizationTask(5, 1) {
                    private int ticks = 0;

                    @Override
                    public void run() {
                        if (++ticks > 50) {
                            this.cancel();
                            return;
                        }

                        @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                                LivingEntity.class, attacker, 15,
                                e -> e.getClass() == attacker.getClass() && !e.equals(hurter)
                        );

                        for (@Nonnull LivingEntity ally : entities) {
                            ally.heal(healAmount);
                        }
                    }
                }.start();
            }
        } else if (damageSource.getDirectEntity() == null) {
            ItemStack weapon = WeaponEventUtil.getActiveWeapon(hurter);

            if (weapon != null && weapon.getItem() instanceof SanctiMagistar) {
                event.setAmount(event.getAmount() - KuvaWeapon.getMagnification(weapon, event.getAmount() * 0.5f, 2));
            }
        }
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageSanctiMagistar.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedSanctiMagistar.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedSanctiMagistar.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}