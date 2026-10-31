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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import pers.roinflam.kuvalich.client.fx.script.ItemFx;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.AttributesUtil;
import pers.roinflam.kuvalich.utils.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 收割者Prime
 * Reaper Prime
 */
@Mod.EventBusSubscriber
public class ReaperPrime extends AbstractKuvaWeapon {

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
            float healAmount = KuvaWeaponUtil.getMagnification(weapon, event.getAmount() * 0.1f);
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

            double speed = KuvaWeaponUtil.getMagnification(
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

            // 冲刺的拖尾和起点地面环（a = 1 收割者·Prime；这一下不带范围伤害，命中数恒为 0）
            KuvaFx.tracking(player, FxId.WEAPON_DASH, 1, 0);

            player.getCooldowns().addCooldown(stack.getItem(), (int) (60 * magnification));
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }

    /** 用满这么多 tick 时蓄力正好满（放开时 {@code (用时) * 2 >= 40}） */
    private static final int CHARGE_FULL_TICKS = 20;

    /**
     * 蓄满提示：客户端在数到满蓄力的那一 tick 给持有者本人一个小光环和提示音。
     * 这一 tick 的剩余时长要减一以后才算进放开时的用时，所以看的是第 19 个 tick
     */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        if (level.isClientSide && getUseDuration(stack) - remaining == CHARGE_FULL_TICKS - 1) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ItemFx.chargeReady(entity));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageReaperPrime.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedReaperPrime.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedReaperPrime.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}