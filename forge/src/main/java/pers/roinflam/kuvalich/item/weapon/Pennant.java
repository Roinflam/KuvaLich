package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import pers.roinflam.kuvalich.client.fx.script.ItemFx;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.module.weapon.DamageDisplayTracker;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.network.packet.DamageInfo;
import pers.roinflam.kuvalich.utils.SynchronizationTask;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.AttributesUtil;
import pers.roinflam.kuvalich.utils.LivingEntityUtil;
import pers.roinflam.kuvalich.utils.EntityUtil;
import pers.roinflam.kuvalich.utils.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class Pennant extends AbstractKuvaWeapon {

    public Pennant(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.4, 0.10);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Pennant.class);

        if (weapon != null && RandomUtil.percentageChance(32)) {
            event.setAmount(event.getAmount() * 2.4f);
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

        if (chargeTime >= 40) {
            double speed = player.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            Vec3 lookVec = player.getLookAngle();
            player.push(
                    lookVec.x * speed * 4.25 * 10,
                    0,
                    lookVec.z * speed * 4.25 * 10
            );

            @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                    LivingEntity.class, player,
                    KuvaWeaponUtil.getMagnification(stack, 12, 4),
                    e -> !e.equals(player)
            );

            int hits = 0;
            for (@Nonnull LivingEntity hurter : entities) {
                // ✅ 使用indirectMagic（原版自带无视护甲效果）
                if (hurter.hurt(
                        level.damageSources().indirectMagic(player, player),
                        hurter.getHealth() * 0.2f)) {
                    hits++;

                    new SynchronizationTask(5, 5) {
                        private int tick = 0;

                        @Override
                        public void run() {
                            // ⭐ 修复 3：实体被移除（卸载 / 消失 / 传送走）时也停止，不再对脱离世界的实体扣血
                            if (++tick > 20 || hurter.isDeadOrDying() || hurter.isRemoved()) {
                                this.cancel();
                                return;
                            }

                            float damage = KuvaWeaponUtil.getMagnification(stack,
                                    (hurter.getMaxHealth() - hurter.getHealth()) * 0.25f / 20);
                            damage = damage * 0.3f + damage * tick / 10 * 0.7f;

                            // ⭐ 先扣血、再显示「扣之前 − 扣之后」的实际掉血：
                            //    Boss 锁血、伤害上限等拦截直接扣血时，数字如实变小或不显示
                            float healthBefore = hurter.getHealth();
                            if (healthBefore - damage > 0.01f) {
                                LivingEntityUtil.damageHealthDirectly(hurter, damage);
                            } else {
                                // ✅ 使用indirectMagic
                                LivingEntityUtil.kill(hurter, level.damageSources().indirectMagic(player, player));
                                this.cancel();
                            }

                            // 持续伤害走附加通道限流；直接改血量不经过护盾，因此不带护盾图标
                            if (player instanceof ServerPlayer serverPlayer) {
                                DamageDisplayTracker.sendDirect(serverPlayer, hurter,
                                        DamageDisplayTracker.resolveDirectLoss(hurter, healthBefore, damage),
                                        "§f", "", "", false, DamagePacket.Channel.SECONDARY);
                            }
                        }
                    }.start();
                }
            }

            // 冲刺的拖尾、起点地面环，打中了再加冲击环（a = 0 旗帜，b = 打中的个数）
            KuvaFx.tracking(player, FxId.WEAPON_DASH, 0, hits);

            player.resetAttackStrengthTicker();
            player.getCooldowns().addCooldown(stack.getItem(), 200);
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
                ModConfig.KUVA_WEAPON.attackDamagePennant.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedPennant.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedPennant.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}
