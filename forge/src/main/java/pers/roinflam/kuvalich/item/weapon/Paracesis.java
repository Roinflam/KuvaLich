package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 帕拉西斯（1.20.1版本，业务逻辑100%不变）
 * Paracesis (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class Paracesis extends KuvaWeaponBase {

    public Paracesis(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.31, 2.6, 0.22);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Paracesis.class);

        if (weapon != null) {
            if (hurter instanceof Player) {
                event.setAmount(event.getAmount() * 2);
            }

            if (RandomUtil.percentageChance(31)) {
                event.setAmount(event.getAmount() * 2.6f);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Paracesis.class);

        if (weapon != null) {
            float totalDamage = event.getAmount();
            float dotDamage = KuvaWeapon.getMagnification(weapon, totalDamage * 0.7f / 100);
            event.setAmount(totalDamage * 0.3f);

            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > 100 || hurter.isDeadOrDying()) {
                        this.cancel();
                        return;
                    }

                    if (hurter.getHealth() - dotDamage > 0.01f) {
                        EntityLivingUtil.damageHealthDirectly(hurter, dotDamage);
                    } else {
                        EntityLivingUtil.kill(hurter, attacker.level().damageSources().indirectMagic(attacker, attacker));
                        this.cancel();
                    }
                }
            }.start();
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageParacesis.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedParacesis.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedParacesis.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}