package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 收割者Prime（1.20.1版本，业务逻辑100%不变）
 * Reaper Prime (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class ReaperPrime extends KuvaWeaponBase {

    public ReaperPrime(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.35, 2.5, 0.25);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, ReaperPrime.class);

        if (weapon != null) {
            float healAmount = KuvaWeapon.getMagnification(weapon, event.getAmount() * 0.1f);
            attacker.heal(healAmount);
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.success(itemstack);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72000;
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player)) return;

        Player player = (Player) entity;
        int chargeTime = (getUseDuration(stack) - timeLeft) * 2;

        if (chargeTime >= 10) {
            double magnification = Mth.clamp(
                    (chargeTime - 10) / 20.0,
                    0.5,
                    1.5
            );

            double speed = KuvaWeapon.getMagnification(
                    stack,
                    player.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),
                    3
            );

            Vec3 lookVec = player.getLookAngle();
            player.push(
                    lookVec.x * speed * 4.25 * 5 * magnification,
                    0,
                    lookVec.z * speed * 4.25 * 5 * magnification
            );

            player.getCooldowns().addCooldown(stack.getItem(), (int) (60 * magnification));
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageReaperPrime.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedReaperPrime.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedReaperPrime.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}