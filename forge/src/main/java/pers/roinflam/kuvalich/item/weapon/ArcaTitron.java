package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
 * 阿卡提龙（1.20.1版本，业务逻辑100%不变）
 * Arca Titron (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class ArcaTitron extends KuvaWeaponBase {

    public ArcaTitron(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.24, 2.0, 0.38);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            MobEffectInstance existingEffect = attacker.getEffect(KuvaLichMobEffects.ARCA_TITRON.get());
            int newAmplifier;

            if (existingEffect != null) {
                newAmplifier = Math.min(9, existingEffect.getAmplifier() + 1);
            } else {
                newAmplifier = 5;
            }

            // ✅ 使用 HiddenEffectHelper
            HiddenEffectHelper.apply(
                    attacker,
                    KuvaLichMobEffects.ARCA_TITRON.get(),
                    (int) KuvaWeapon.getMagnification(weapon, 400),
                    newAmplifier
            );
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        Player attacker = event.getEntity();
        MobEffectInstance buffEffect = attacker.getEffect(KuvaLichMobEffects.ARCA_TITRON.get());

        if (buffEffect == null) return;

        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            int level = buffEffect.getAmplifier() + 1;
            float bonusDamage = KuvaWeapon.getMagnification(weapon,
                    event.getDamageModifier() * level * 0.05f);

            event.setDamageModifier(event.getDamageModifier() + bonusDamage);
        }
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageArcaTitron.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedArcaTitron.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedArcaTitron.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}