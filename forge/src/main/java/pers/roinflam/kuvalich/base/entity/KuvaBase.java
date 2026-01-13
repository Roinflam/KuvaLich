package pers.roinflam.kuvalich.base.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 赤毒玄骸基类（1.20.1版本，配置值修正）
 * Kuva Lich Base Class (1.20.1 version, config values fixed)
 */
@Mod.EventBusSubscriber
public abstract class KuvaBase extends Monster implements GeoEntity {

    private static final float ARROW_DAMAGE_MULTIPLIER = 1.25f;
    private static final float PROJECTILE_DAMAGE_MULTIPLIER = 0.75f;
    private static final float MAGIC_DAMAGE_MULTIPLIER = 0.75f;
    private static final float KUVA_WEAPON_DAMAGE_MULTIPLIER = 0.25f;

    private static final EntityDataAccessor<Boolean> HAS_TARGET =
            SynchedEntityData.defineId(KuvaBase.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    protected int battleTick = 0;

    public KuvaBase(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = event.getSource();
        if (damageSource == null) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target == null) {
            return;
        }

        if (damageSource.getEntity() instanceof KuvaBase kuvaBase) {
            applyKuvaBaseDamageBonus(event, kuvaBase);
        } else if (damageSource.getEntity() instanceof Player player) {
            applyPlayerDamageModifier(event, player);
        }

        if (target instanceof KuvaBase) {
            applyKuvaBaseDefense(event, damageSource);
        } else if (target instanceof Player player) {
            applyPlayerDefense(event, player);
        }
    }

    private static void applyKuvaBaseDamageBonus(LivingHurtEvent event, KuvaBase kuvaBase) {
        // ✅ 修正：调用.get()获取配置值
        float multiplier = (float) (1.0f + (kuvaBase.getBattleTick() / 20f) * ModConfig.KUVA_LICH.battleBoost.get());
        event.setAmount(event.getAmount() * multiplier);
    }

    private static void applyPlayerDamageModifier(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            // ✅ 修正：调用.get()获取配置值
            float reduction = (float) Math.min(0.999f, requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.reducedDamage.get());
            float damage = event.getAmount() * (1.0f - reduction);

            if (KuvaWeapon.hasType(player.getItemInHand(player.getUsedItemHand()))) {
                damage *= KUVA_WEAPON_DAMAGE_MULTIPLIER;
            }

            event.setAmount(damage);
        });
    }

    private static void applyKuvaBaseDefense(LivingHurtEvent event, DamageSource damageSource) {
        float damage = event.getAmount();

        if (damageSource.getDirectEntity() instanceof AbstractArrow) {
            damage *= ARROW_DAMAGE_MULTIPLIER;
        } else if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            damage *= PROJECTILE_DAMAGE_MULTIPLIER;
        }

        if (damageSource.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO) &&
                !damageSource.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            damage *= MAGIC_DAMAGE_MULTIPLIER;
        }

        event.setAmount(damage);
    }

    private static void applyPlayerDefense(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            // ✅ 修正：调用.get()获取配置值
            float multiplier = (float) (1.0f + requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.increaseDamage.get());
            event.setAmount(event.getAmount() * multiplier);
        });
    }

    @Override
    public @NotNull MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            if (this.swinging) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("attack"));
            } else if (state.isMoving()) {
                String animation = this.entityData.get(HAS_TARGET) ? "run" : "walk";
                return state.setAndContinue(RawAnimation.begin().thenLoop(animation));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenLoop("stay"));
            }
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Monster.class, 32.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 16, true, true,
                entity -> !(entity instanceof Creeper) && !(entity instanceof KuvaBase)));
    }

    @Override
    public void setTarget(LivingEntity target) {
        boolean hasTarget = target != null && target.isAlive();
        this.entityData.set(HAS_TARGET, hasTarget);
        super.setTarget(target);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HAS_TARGET, false);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            updateTargetStatus();
            handleHealing();
        }

        updateBattleTick();
    }

    private void updateTargetStatus() {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        this.entityData.set(HAS_TARGET, hasTarget);
    }

    private void handleHealing() {
        if (level().getGameTime() % 20 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }

        float healAmount = this.entityData.get(HAS_TARGET)
                ? getHasTargetTickHeal()
                : getTickHeal();

        this.heal(healAmount);
    }

    private void updateBattleTick() {
        if (this.entityData.get(HAS_TARGET)) {
            battleTick++;
        } else if (battleTick > 0) {
            battleTick--;
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_SKELETON_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BattleTick", this.battleTick);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.battleTick = compound.getInt("BattleTick");
    }

    public int getBattleTick() {
        return battleTick;
    }

    protected abstract float getTickHeal();
    protected abstract float getHasTargetTickHeal();
}