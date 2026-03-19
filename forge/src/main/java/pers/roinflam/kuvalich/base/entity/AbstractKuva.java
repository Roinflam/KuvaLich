// AbstractKuva.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/base/entity/AbstractKuva.java
package pers.roinflam.kuvalich.base.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 赤毒玄骸基类（1.20.1版本）
 * 包含基础伤害类型修正和抗性框架，子类通过覆写实现具体抗性逻辑
 *
 * Kuva Lich Base Class (1.20.1)
 * Contains base damage type modifiers and resistance framework,
 * subclasses override to implement specific resistance logic
 */
@Mod.EventBusSubscriber
public abstract class AbstractKuva extends Monster implements GeoEntity {

    // ==================== 基础伤害类型倍率 ====================

    /** 弓箭伤害倍率（弱点） */
    private static final float ARROW_DAMAGE_MULTIPLIER = 1.25f;
    /** 其他弹射物伤害倍率（抗性） */
    private static final float PROJECTILE_DAMAGE_MULTIPLIER = 0.75f;
    /** 魔法伤害倍率（抗性） */
    private static final float MAGIC_DAMAGE_MULTIPLIER = 0.75f;

    /** 抗性衰减检查间隔（20tick = 1秒，无需每tick检查） */
    private static final int RESISTANCE_CHECK_INTERVAL = 20;

    // ==================== 同步数据 ====================

    private static final EntityDataAccessor<Boolean> HAS_TARGET =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.BOOLEAN);

    // ==================== 动画 ====================

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    // ==================== 战斗数据 ====================

    /** 战斗持续tick数，影响伤害加成 */
    protected int battleTick = 0;

    /**
     * 伤害类别枚举，用于适应性抗性的伤害来源分类
     * 判定优先级：弓箭 > 弹射物 > 魔法 > 近战 > 其他
     *
     * Damage category enum for adaptive resistance classification
     * Priority: ARROW > PROJECTILE > MAGIC > MELEE > OTHER
     */
    public enum DamageCategory {
        /** 近战攻击 */
        MELEE,
        /** 弓箭攻击 */
        ARROW,
        /** 其他弹射物攻击 */
        PROJECTILE,
        /** 魔法攻击 */
        MAGIC,
        /** 其他类型攻击 */
        OTHER
    }

    public AbstractKuva(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    // ==================== 伤害事件处理 ====================

    /**
     * 全局伤害事件监听器
     * 处理赤毒实体的攻击加成和防御减伤
     *
     * @param event 生物受伤事件
     */
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

        // 赤毒实体造成伤害时的加成
        if (damageSource.getEntity() instanceof AbstractKuva kuvaBase) {
            applyKuvaBaseDamageBonus(event, kuvaBase);
        } else if (damageSource.getEntity() instanceof Player player) {
            applyPlayerDamageModifier(event, player);
        }

        // 赤毒实体受到伤害时的减伤（委托给实例方法）
        if (target instanceof AbstractKuva kuva) {
            event.setAmount(kuva.applyResistance(damageSource, event.getAmount()));
        } else if (target instanceof Player player) {
            applyPlayerDefense(event, player);
        }
    }

    /**
     * 赤毒实体攻击加成：随战斗时间增长
     *
     * @param event 受伤事件
     * @param kuvaBase 攻击方赤毒实体
     */
    private static void applyKuvaBaseDamageBonus(LivingHurtEvent event, AbstractKuva kuvaBase) {
        float multiplier = (float) (1.0f + (kuvaBase.getBattleTick() / 20f) * ModConfig.KUVA_LICH.battleBoost.get());
        event.setAmount(event.getAmount() * multiplier);
    }

    /**
     * 玩家对赤毒实体的伤害修正：受玄骸等级影响
     *
     * @param event 受伤事件
     * @param player 攻击方玩家
     */
    private static void applyPlayerDamageModifier(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            float reduction = (float) Math.min(0.999f, requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.reducedDamage.get());
            float damage = event.getAmount() * (1.0f - reduction);

            event.setAmount(damage);
        });
    }

    /**
     * 玩家受到赤毒实体攻击时的伤害增幅
     *
     * @param event 受伤事件
     * @param player 受击方玩家
     */
    private static void applyPlayerDefense(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            float multiplier = (float) (1.0f + requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.increaseDamage.get());
            event.setAmount(event.getAmount() * multiplier);
        });
    }

    // ==================== 抗性框架 ====================

    /**
     * 根据伤害来源判断伤害类别
     * 判定优先级：弓箭 > 弹射物 > 魔法 > 近战 > 其他
     *
     * @param source 伤害来源
     * @return 伤害类别
     */
    public static DamageCategory classifyDamage(DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) {
            return DamageCategory.ARROW;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PROJECTILE;
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return DamageCategory.MAGIC;
        }
        if (source.getDirectEntity() instanceof LivingEntity) {
            return DamageCategory.MELEE;
        }
        return DamageCategory.OTHER;
    }

    /**
     * 抗性计算入口
     * 执行顺序：基础伤害类型修正 → 高级抗性（子类实现）
     *
     * @param source 伤害来源
     * @param damage 原始伤害值
     * @return 最终伤害值
     */
    public float applyResistance(DamageSource source, float damage) {
        // 第一步：基础伤害类型修正（弓箭增伤、弹射物/魔法减伤）
        damage = applyBaseDamageTypeModifier(source, damage);
        // 第二步：高级抗性（子类覆写实现）
        damage = applyAdvancedResistance(source, damage);
        return damage;
    }

    /**
     * 基础伤害类型修正，所有赤毒实体共有
     * 弓箭增伤125%，其他弹射物减伤75%，魔法减伤75%
     *
     * @param source 伤害来源
     * @param damage 伤害值
     * @return 修正后伤害值
     */
    protected float applyBaseDamageTypeModifier(DamageSource source, float damage) {
        if (source.getDirectEntity() instanceof AbstractArrow) {
            damage *= ARROW_DAMAGE_MULTIPLIER;
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            damage *= PROJECTILE_DAMAGE_MULTIPLIER;
        }

        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO) &&
                !source.is(DamageTypeTags.IS_PROJECTILE)) {
            damage *= MAGIC_DAMAGE_MULTIPLIER;
        }

        return damage;
    }

    /**
     * 高级抗性处理入口，默认不做任何事
     * Slave覆写为固定25%减伤，Master覆写为三层动态抗性
     *
     * @param source 伤害来源
     * @param damage 伤害值
     * @return 处理后伤害值
     */
    protected float applyAdvancedResistance(DamageSource source, float damage) {
        return damage;
    }

    /**
     * 抗性系统的定期更新，在aiStep中每秒调用一次
     * 默认不做任何事，Master覆写处理适应性抗性的衰减
     */
    protected void tickResistance() {
        // 默认无操作
    }

    // ==================== 动画 ====================

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

    // ==================== AI目标 ====================

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
                entity -> !(entity instanceof Creeper) && !(entity instanceof AbstractKuva)));
    }

    // ==================== 状态管理 ====================

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

            // 抗性衰减每秒检查一次即可，无需每tick
            if (level().getGameTime() % RESISTANCE_CHECK_INTERVAL == 0) {
                tickResistance();
            }
        }

        updateBattleTick();
    }

    /**
     * 更新目标存在状态的同步数据
     */
    private void updateTargetStatus() {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        this.entityData.set(HAS_TARGET, hasTarget);
    }

    /**
     * 处理每秒自愈逻辑
     * 有目标时回复更快
     */
    private void handleHealing() {
        if (level().getGameTime() % 20 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }

        float healAmount = this.entityData.get(HAS_TARGET)
                ? getHasTargetTickHeal()
                : getTickHeal();

        this.heal(healAmount);
    }

    /**
     * 更新战斗tick计数
     * 有目标时递增，无目标时递减
     */
    private void updateBattleTick() {
        if (this.entityData.get(HAS_TARGET)) {
            battleTick++;
        } else if (battleTick > 0) {
            battleTick--;
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // 免疫摔落伤害
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    // ==================== 音效 ====================

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

    // ==================== NBT存档 ====================

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

    // ==================== Getter ====================

    public int getBattleTick() {
        return battleTick;
    }

    // ==================== 子类必须实现 ====================

    /**
     * 无目标时每秒回血量
     *
     * @return 回血量
     */
    protected abstract float getTickHeal();

    /**
     * 有目标时每秒回血量
     *
     * @return 回血量
     */
    protected abstract float getHasTargetTickHeal();
}