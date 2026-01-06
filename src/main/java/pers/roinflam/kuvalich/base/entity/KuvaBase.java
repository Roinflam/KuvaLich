// 文件：KuvaBase.java
// 路径：src/main/java/pers/roinflam/kuvalich/base/entity/KuvaBase.java
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

import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
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

@Mod.EventBusSubscriber
public abstract class KuvaBase extends EntityMob implements IAnimatable, IAnimationTickable {
    private static final float ARROW_DAMAGE_MULTIPLIER = 1.25f;
    private static final float PROJECTILE_DAMAGE_MULTIPLIER = 0.75f;
    private static final float MAGIC_DAMAGE_MULTIPLIER = 0.75f;
    private static final float KUVA_WEAPON_DAMAGE_MULTIPLIER = 0.25f;

    public static final Biome[] BIOMES = {
            Biomes.FOREST, Biomes.FOREST_HILLS, Biomes.BIRCH_FOREST, Biomes.BIRCH_FOREST_HILLS,
            Biomes.ROOFED_FOREST, Biomes.TAIGA, Biomes.TAIGA_HILLS, Biomes.REDWOOD_TAIGA,
            Biomes.REDWOOD_TAIGA_HILLS, Biomes.PLAINS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU,
            Biomes.DESERT, Biomes.DESERT_HILLS, Biomes.JUNGLE, Biomes.JUNGLE_HILLS, Biomes.JUNGLE_EDGE,
            Biomes.SWAMPLAND, Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_WITH_TREES,
            Biomes.BEACH, Biomes.STONE_BEACH, Biomes.COLD_BEACH, Biomes.RIVER,
            Biomes.ICE_PLAINS, Biomes.COLD_TAIGA, Biomes.COLD_TAIGA_HILLS, Biomes.OCEAN,
            Biomes.MUSHROOM_ISLAND_SHORE, Biomes.MUTATED_FOREST, Biomes.MUTATED_TAIGA,
            Biomes.MUTATED_SWAMPLAND, Biomes.MUTATED_ICE_FLATS, Biomes.MUTATED_JUNGLE,
            Biomes.MUTATED_JUNGLE_EDGE, Biomes.MUTATED_BIRCH_FOREST, Biomes.MUTATED_BIRCH_FOREST_HILLS,
            Biomes.MUTATED_ROOFED_FOREST, Biomes.MUTATED_REDWOOD_TAIGA, Biomes.MUTATED_REDWOOD_TAIGA_HILLS,
            Biomes.MUTATED_EXTREME_HILLS, Biomes.MUTATED_SAVANNA, Biomes.MUTATED_SAVANNA_ROCK
    };

    private static final DataParameter<Boolean> HAS_TARGET =
            EntityDataManager.createKey(KuvaBase.class, DataSerializers.BOOLEAN);

    private static final Predicate<EntityMob> VALID_TARGET_PREDICATE =
            entityMob -> entityMob != null && IMob.VISIBLE_MOB_SELECTOR.apply(entityMob)
                    && !(entityMob instanceof EntityCreeper)
                    && !(entityMob instanceof KuvaBase);

    private final AnimationFactory factory = new AnimationFactory(this);
    protected int battleTick = 0;

    public KuvaBase(World worldIn) {
        super(worldIn);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        // 服务端处理
        if (evt.getEntity() == null || evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (damageSource == null) {
            return;
        }

        EntityLivingBase target = evt.getEntityLiving();
        if (target == null) {
            return;
        }

        if (damageSource.getTrueSource() instanceof KuvaBase) {
            applyKuvaBaseDamageBonus(evt, (KuvaBase) damageSource.getTrueSource());
        } else if (damageSource.getTrueSource() instanceof EntityPlayer) {
            applyPlayerDamageModifier(evt, (EntityPlayer) damageSource.getTrueSource());
        }

        if (target instanceof KuvaBase) {
            applyKuvaBaseDefense(evt, damageSource);
        } else if (target instanceof EntityPlayer) {
            applyPlayerDefense(evt, (EntityPlayer) target);
        }
    }

    private static void applyKuvaBaseDamageBonus(LivingHurtEvent evt, KuvaBase kuvaBase) {
        float multiplier = 1.0f + (kuvaBase.getBattleTick() / 20f) * ModConfig.KUVA_LICH.battleBoost;
        evt.setAmount(evt.getAmount() * multiplier);
    }

    private static void applyPlayerDamageModifier(LivingHurtEvent evt, EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        if (requiemCard == null) {
            return;
        }

        float reduction = Math.min(0.999f, requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.reducedDamage);
        float damage = evt.getAmount() * (1.0f - reduction);

        if (KuvaWeapon.hasType(player.getHeldItem(player.getActiveHand()))) {
            damage *= KUVA_WEAPON_DAMAGE_MULTIPLIER;
        }

        evt.setAmount(damage);
    }

    private static void applyKuvaBaseDefense(LivingHurtEvent evt, DamageSource damageSource) {
        float damage = evt.getAmount();

        if (damageSource.getImmediateSource() instanceof EntityArrow) {
            damage *= ARROW_DAMAGE_MULTIPLIER;
        } else if (damageSource.isProjectile()) {
            damage *= PROJECTILE_DAMAGE_MULTIPLIER;
        }

        if (damageSource.isMagicDamage() && !damageSource.isProjectile()) {
            damage *= MAGIC_DAMAGE_MULTIPLIER;
        }

        evt.setAmount(damage);
    }

    private static void applyPlayerDefense(LivingHurtEvent evt, EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        if (requiemCard == null) {
            return;
        }

        float multiplier = 1.0f + requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.increaseDamage;
        evt.setAmount(evt.getAmount() * multiplier);
    }

    @Override
    public EnumCreatureAttribute getCreatureAttribute() {
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
    public void setAttackTarget(EntityLivingBase target) {
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
        if (world.getTotalWorldTime() % 20 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }

        float healAmount = this.getDataManager().get(HAS_TARGET)
                ? getHasTargetTickHeal()
                : getTickHeal();

        this.heal(healAmount);
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
        if ("fall".equalsIgnoreCase(source.damageType)) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
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

    protected abstract float getTickHeal();
    protected abstract float getHasTargetTickHeal();
}