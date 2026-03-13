package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 关刀Prime（1.20.1版本，使用动态属性系统）
 * Guandao Prime (1.20.1 version, using dynamic attribute system)
 */
@Mod.EventBusSubscriber
public class GuandaoPrime extends AbstractKuvaWeapon {

    public GuandaoPrime(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.4, 0.20);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, GuandaoPrime.class);

        if (weapon != null) {
            // ✅ 替换为动态属性系统（简化处理）
            int currentLevel = DynamicAttributeManager.has(attacker, DynamicAttributes.GUANDAO_PRIME)
                    ? 10
                    : 5;

            DynamicAttributeManager.apply(
                    attacker,
                    DynamicAttributes.GUANDAO_PRIME.createInstance(
                            (int) KuvaWeaponUtil.getMagnification(weapon, 100),
                            currentLevel + 5
                    )
            );

            float baseDamage = event.getAmount();
            float maxHealthDamage = hurter.getMaxHealth() * 0.025f;
            float dotDamage = KuvaWeaponUtil.getMagnification(weapon, (baseDamage + maxHealthDamage) / 20);

            new SynchronizationTask(5, 5) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > 20 || hurter.isDeadOrDying()) {
                        this.cancel();
                        return;
                    }

                    if (attacker instanceof ServerPlayer) {
                        double entityWidth = hurter.getBbWidth();
                        double entityHeight = hurter.getBbHeight();
                        double entityY = hurter.getY();

                        double offsetX = (Math.random() - 0.5) * entityWidth * 1.2;
                        double offsetZ = (Math.random() - 0.5) * entityWidth * 1.2;

                        Vec3 position = new Vec3(
                                hurter.getX() + offsetX,
                                entityY + entityHeight * (-0.2 + Math.random() * 0.4),
                                hurter.getZ() + offsetZ
                        );

                        DamagePacket.sendToPlayer((ServerPlayer) attacker, dotDamage, position, "§f");
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
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageGuandaoPrime.get(), 2));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedGuandaoPrime.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedGuandaoPrime.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}