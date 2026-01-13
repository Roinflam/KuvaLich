package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMobEffects;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.HiddenEffectHelper;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 关刀Prime（1.20.1版本，业务逻辑100%不变）
 * Guandao Prime (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class GuandaoPrime extends KuvaWeaponBase {

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
            MobEffectInstance existingEffect = attacker.getEffect(KuvaLichMobEffects.GUANDAO_PRIME.get());
            int currentLevel = existingEffect != null ? existingEffect.getAmplifier() : 5;

            // ✅ 使用 HiddenEffectHelper
            HiddenEffectHelper.apply(
                    attacker,
                    KuvaLichMobEffects.GUANDAO_PRIME.get(),
                    (int) KuvaWeapon.getMagnification(weapon, 100),
                    currentLevel + 5
            );

            float baseDamage = event.getAmount();
            float maxHealthDamage = hurter.getMaxHealth() * 0.025f;
            float dotDamage = KuvaWeapon.getMagnification(weapon, (baseDamage + maxHealthDamage) / 20);

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
                        double entityHeight = hurter.getBbHeight();  // ✅ 使用实体总高度
                        double entityY = hurter.getY();

                        double offsetX = (Math.random() - 0.5) * entityWidth * 1.2;
                        double offsetZ = (Math.random() - 0.5) * entityWidth * 1.2;

                        Vec3 position = new Vec3(
                                hurter.getX() + offsetX,
                                entityY + entityHeight * (-0.2 + Math.random() * 0.4),
                                hurter.getZ() + offsetZ
                        );

                        KuvaLich.network.send(
                                PacketDistributor.PLAYER.with(() -> (ServerPlayer) attacker),
                                new DamagePacket(dotDamage, position, DamageInfo.DamageColor.WHITE.getColor())
                        );
                    }

                    if (hurter.getHealth() - dotDamage * 2 > 0) {
                        hurter.setHealth(hurter.getHealth() - dotDamage);
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
                ModConfig.KUVA_WEAPON.attackDamageGuandaoPrime.get(), 2));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedGuandaoPrime.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedGuandaoPrime.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}