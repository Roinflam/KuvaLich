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
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class Pennant extends KuvaWeaponBase {

    public Pennant(@Nonnull Item.Properties properties) {
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
                    KuvaWeapon.getMagnification(stack, 12, 4),
                    e -> !e.equals(player)
            );

            for (@Nonnull LivingEntity hurter : entities) {
                // ✅ 使用indirectMagic（原版自带无视护甲效果）
                if (hurter.hurt(
                        level.damageSources().indirectMagic(player, player),
                        hurter.getHealth() * 0.2f)) {

                    new SynchronizationTask(5, 5) {
                        private int tick = 0;

                        @Override
                        public void run() {
                            if (++tick > 20 || hurter.isDeadOrDying()) {
                                this.cancel();
                                return;
                            }

                            float damage = KuvaWeapon.getMagnification(stack,
                                    (hurter.getMaxHealth() - hurter.getHealth()) * 0.25f / 20);
                            damage = damage * 0.3f + damage * tick / 10 * 0.7f;

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

                            // ✅ 正确的网络包发送方式
                            KuvaLich.network.send(
                                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                                    new DamagePacket(damage, position, DamageInfo.DamageColor.WHITE.getColor())
                            );

                            if (hurter.getHealth() - damage * 2 > 0) {
                                hurter.setHealth(hurter.getHealth() - damage);
                            } else {
                                // ✅ 使用indirectMagic
                                EntityLivingUtil.kill(hurter, level.damageSources().indirectMagic(player, player));
                                this.cancel();
                            }
                        }
                    }.start();
                }
            }

            player.resetAttackStrengthTicker();
            player.getCooldowns().addCooldown(stack.getItem(), 200);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamagePennant.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedPennant.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedPennant.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}