package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributes;
import pers.roinflam.kuvalich.module.weapon.DamageDisplayTracker;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.utils.SynchronizationTask;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.AttributesUtil;
import pers.roinflam.kuvalich.utils.LivingEntityUtil;
import pers.roinflam.kuvalich.utils.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 关刀Prime（1.20.1版本，使用动态属性系统）
 * Guandao Prime (1.20.1 version, using dynamic attribute system)
 */
@Mod.EventBusSubscriber
public class GuandaoPrime extends AbstractKuvaWeapon {

    public GuandaoPrime(@Nonnull Properties properties) {
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
                    // ⭐ 修复 3：实体被移除（卸载 / 消失 / 传送走）时也停止，不再对脱离世界的实体扣血
                    if (++tick > 20 || hurter.isDeadOrDying() || hurter.isRemoved()) {
                        this.cancel();
                        return;
                    }

                    // ⭐ 先扣血、再显示「扣之前 − 扣之后」的实际掉血：
                    //    Boss 锁血、伤害上限等拦截直接扣血时，数字如实变小或不显示
                    float healthBefore = hurter.getHealth();
                    if (healthBefore - dotDamage > 0.01f) {
                        LivingEntityUtil.damageHealthDirectly(hurter, dotDamage);
                    } else {
                        LivingEntityUtil.kill(hurter, attacker.level().damageSources().indirectMagic(attacker, attacker));
                        this.cancel();
                    }

                    // 持续伤害走附加通道限流；直接改血量不经过护盾，因此不带护盾图标
                    if (attacker instanceof ServerPlayer serverPlayer) {
                        DamageDisplayTracker.sendDirect(serverPlayer, hurter,
                                DamageDisplayTracker.resolveDirectLoss(hurter, healthBefore, dotDamage),
                                "§f", "", "", false, DamagePacket.Channel.SECONDARY);
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
