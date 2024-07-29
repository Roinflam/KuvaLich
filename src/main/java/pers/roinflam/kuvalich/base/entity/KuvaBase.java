package pers.roinflam.kuvalich.base.entity;

import com.google.common.base.Predicate;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Biomes;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.IAnimationTickable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * KuvaBase 是 KuvaLich mod 中特殊怪物实体的抽象基类。
 * 这个类继承自 Minecraft 的 EntityMob，并实现了 IAnimatable 和 IAnimationTickable 接口以支持高级实体动画。
 * <p>
 * 特性：
 * - 不死族属性
 * - 自定义 AI 行为
 * - 动态战斗状态追踪
 * - 自我治疗能力
 * - 特殊的伤害处理机制
 * - 使用 GeckoLib 的高级动画系统
 * <p>
 * 生成群系：
 * KuvaBase 实体可以在大多数主流群系中生成，包括但不限于：
 * - 各种类型的森林（普通、桦木、针叶林等）
 * - 平原和草原
 * - 沙漠和沙漠丘陵
 * - 丛林和丛林边缘
 * - 沼泽
 * - 各种山地和丘陵
 * - 海滩
 * - 河流
 * <p>
 * 被排除的特殊群系：
 * - 蘑菇岛
 * - 末地
 * - 下界
 * - 深海
 * - 冰刺平原
 * <p>
 * 注意：实际生成可能受到其他因素影响，如温度和潮湿度。
 */
@Mod.EventBusSubscriber
public abstract class KuvaBase extends EntityMob implements IAnimatable, IAnimationTickable {

    /**
     * 实体可以生成的生物群系
     */
    public static final Biome[] BIOMES = {
            // 森林类群系
            Biomes.FOREST, Biomes.FOREST_HILLS, Biomes.BIRCH_FOREST, Biomes.BIRCH_FOREST_HILLS,
            Biomes.ROOFED_FOREST, Biomes.TAIGA, Biomes.TAIGA_HILLS, Biomes.REDWOOD_TAIGA,
            Biomes.REDWOOD_TAIGA_HILLS,

            // 平原和草原
            Biomes.PLAINS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,

            // 沙漠
            Biomes.DESERT, Biomes.DESERT_HILLS,

            // 丛林
            Biomes.JUNGLE, Biomes.JUNGLE_HILLS, Biomes.JUNGLE_EDGE,

            // 沼泽
            Biomes.SWAMPLAND,

            // 山地和丘陵
            Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_WITH_TREES,

            // 海滩
            Biomes.BEACH, Biomes.STONE_BEACH, Biomes.COLD_BEACH,

            // 河流
            Biomes.RIVER,

            // 寒冷群系
            Biomes.ICE_PLAINS, Biomes.COLD_TAIGA, Biomes.COLD_TAIGA_HILLS,

            // 温和海洋（但不包括深海）
            Biomes.OCEAN,

            // 蘑菇群系的边缘（如果你想让它们在靠近蘑菇岛的地方出现）
            Biomes.MUSHROOM_ISLAND_SHORE,

            // 各种变种群系
            Biomes.MUTATED_FOREST, Biomes.MUTATED_TAIGA, Biomes.MUTATED_SWAMPLAND,
            Biomes.MUTATED_ICE_FLATS, Biomes.MUTATED_JUNGLE, Biomes.MUTATED_JUNGLE_EDGE,
            Biomes.MUTATED_BIRCH_FOREST, Biomes.MUTATED_BIRCH_FOREST_HILLS,
            Biomes.MUTATED_ROOFED_FOREST, Biomes.MUTATED_REDWOOD_TAIGA,
            Biomes.MUTATED_REDWOOD_TAIGA_HILLS, Biomes.MUTATED_EXTREME_HILLS,
            Biomes.MUTATED_SAVANNA, Biomes.MUTATED_SAVANNA_ROCK
    };

    private static final DataParameter<Boolean> HAS_TARGET = EntityDataManager.createKey(KuvaBase.class, DataSerializers.BOOLEAN);
    private static final Predicate<EntityMob> VALID_TARGET_PREDICATE =
            entityMob -> entityMob != null && IMob.VISIBLE_MOB_SELECTOR.apply(entityMob)
                    && !(entityMob instanceof EntityCreeper)
                    && !(entityMob instanceof KuvaBase);

    private final AnimationFactory factory = new AnimationFactory(this);
    protected int battleTick = 0;

    public KuvaBase(@NotNull World worldIn) {
        super(worldIn);
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;

        DamageSource damageSource = evt.getSource();
        EntityLivingBase target = evt.getEntityLiving();

        if (damageSource.getTrueSource() instanceof KuvaBase) {
            handleKuvaBaseDamage(evt, (KuvaBase) damageSource.getTrueSource());
        } else if (damageSource.getTrueSource() instanceof EntityPlayer) {
            handlePlayerDamage(evt, (EntityPlayer) damageSource.getTrueSource());
        }

        if (target instanceof KuvaBase) {
            handleDamageToKuvaBase(evt, damageSource);
        } else if (target instanceof EntityPlayer) {
            handleDamageToPlayer(evt, (EntityPlayer) target);
        }
    }

    private static void handleKuvaBaseDamage(LivingHurtEvent evt, KuvaBase kuvaBase) {
        evt.setAmount(evt.getAmount() * (1 + (kuvaBase.getBattleTick() / 20f) * ConfigKuvaLich.battleBoost));
    }

    private static void handlePlayerDamage(LivingHurtEvent evt, EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        float reduction = Math.min(0.999f, requiemCard.getKuvaLevel() * ConfigKuvaLich.reducedDamage);
        evt.setAmount(evt.getAmount() * (1 - reduction));

        if (KuvaWeapon.hasType(player.getHeldItem(player.getActiveHand()))) {
            evt.setAmount(evt.getAmount() * 0.25f);
        }
    }

    private static void handleDamageToKuvaBase(LivingHurtEvent evt, DamageSource damageSource) {
        if (damageSource.getImmediateSource() instanceof EntityArrow) {
            evt.setAmount(evt.getAmount() * 1.25f);
        } else if (damageSource.isProjectile()) {
            evt.setAmount(evt.getAmount() * 0.75f);
        }
        if (damageSource.isMagicDamage() && !damageSource.isProjectile()) {
            evt.setAmount(evt.getAmount() * 0.75f);
        }
    }

    private static void handleDamageToPlayer(LivingHurtEvent evt, EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        evt.setAmount(evt.getAmount() * (1 + requiemCard.getKuvaLevel() * ConfigKuvaLich.increaseDamage));
    }

    @Override
    public @NotNull EnumCreatureAttribute getCreatureAttribute() {
        return EnumCreatureAttribute.UNDEAD;
    }

    @Override
    public void registerControllers(@Nonnull AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Nonnull
    private <E extends IAnimatable> PlayState predicate(@Nonnull AnimationEvent<E> event) {
        AnimationController<?> controller = event.getController();
        AnimationBuilder builder = new AnimationBuilder();

        if (this.isSwingInProgress) {
            builder.addAnimation("attack", true);
        } else if (event.isMoving()) {
            builder.addAnimation(this.getDataManager().get(HAS_TARGET) ? "run" : "walk", true);
        } else {
            builder.addAnimation("stay", true);
        }

        controller.setAnimation(builder);
        return PlayState.CONTINUE;
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIWanderAvoidWater(this, 0.7));
        this.tasks.addTask(2, new EntityAIWatchClosest(this, EntityMob.class, 32));
        this.tasks.addTask(3, new EntityAIAttackMelee(this, 1, true));
        this.tasks.addTask(4, new EntityAIWander(this, 0.7));
        this.tasks.addTask(5, new EntityAILookIdle(this));

        this.targetTasks.addTask(0, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(1, new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, 32, true, true, null));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityMob.class, 16, true, true, VALID_TARGET_PREDICATE));
    }

    @Override
    public void setAttackTarget(@Nullable EntityLivingBase target) {
        boolean hasTarget = target != null && target.isEntityAlive();
        this.getDataManager().set(HAS_TARGET, hasTarget);
        super.setAttackTarget(target);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.getDataManager().register(HAS_TARGET, false);
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!this.world.isRemote) {
            updateTargetStatus();
            handleHealing();
        }
        updateBattleTick();
    }

    private void updateTargetStatus() {
        EntityLivingBase target = this.getAttackTarget();
        boolean hasTarget = target != null && target.isEntityAlive();
        this.getDataManager().set(HAS_TARGET, hasTarget);
    }

    private void handleHealing() {
        if (world.getTotalWorldTime() % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(this.getDataManager().get(HAS_TARGET) ? getHasTargetTickHeal() : getTickHeal());
        }
    }

    private void updateBattleTick() {
        if (this.getDataManager().get(HAS_TARGET)) {
            battleTick++;
        } else if (battleTick > 0) {
            battleTick--;
        }
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        return source.damageType.equalsIgnoreCase("fall") ? false : super.attackEntityFrom(source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource damageSourceIn) {
        return SoundEvents.ENTITY_WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ENTITY_IRONGOLEM_STEP;
    }

    @Override
    public void tick() {
        super.onUpdate();
    }

    @Override
    public int tickTimer() {
        return ticksExisted;
    }

    public int getBattleTick() {
        return battleTick;
    }

    /**
     * 获取普通状态下的治疗量。
     *
     * @return 每 tick 的治疗量
     */
    protected abstract float getTickHeal();

    /**
     * 获取有目标状态下的治疗量。
     *
     * @return 每 tick 的治疗量
     */
    protected abstract float getHasTargetTickHeal();
}