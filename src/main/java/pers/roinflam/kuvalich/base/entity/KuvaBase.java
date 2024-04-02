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

@Mod.EventBusSubscriber
public abstract class KuvaBase extends EntityMob implements IAnimatable, IAnimationTickable {
    public static final Biome[] BIOMES = new Biome[]{Biomes.PLAINS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.MUTATED_PLAINS};
    private static final DataParameter<Boolean> HASTARGET = EntityDataManager.createKey(KuvaBase.class, DataSerializers.BOOLEAN);
    private final AnimationFactory factory = new AnimationFactory(this);
    protected int battleTick = 0;

    public KuvaBase(@NotNull World worldIn) {
        super(worldIn);
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof KuvaBase) {
                @org.jetbrains.annotations.Nullable KuvaBase kuvaBase = (KuvaBase) damageSource.getTrueSource();
                evt.setAmount(evt.getAmount() + evt.getAmount() * (kuvaBase.getBattleTick() / 20) * ConfigKuvaLich.battleBoost);
            } else if (damageSource.getTrueSource() instanceof EntityPlayer) {
                @org.jetbrains.annotations.Nullable EntityPlayer entityPlayer = (EntityPlayer) evt.getSource().getTrueSource();
                @org.jetbrains.annotations.Nullable RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                evt.setAmount(evt.getAmount() - evt.getAmount() * Math.min(0.999f, requiemCard.getKuvaLevel() * ConfigKuvaLich.reducedDamage));

                if (KuvaWeapon.hasType(entityPlayer.getHeldItem(entityPlayer.getActiveHand()))) {
                    evt.setAmount(evt.getAmount() * 0.25f);
                }
            }
            if (evt.getEntityLiving() instanceof KuvaBase) {
                if (damageSource.getImmediateSource() instanceof EntityArrow) {
                    evt.setAmount(evt.getAmount() * 1.25f);
                } else if (damageSource.isProjectile()) {
                    evt.setAmount(evt.getAmount() * 0.75f);
                }
                if (damageSource.isMagicDamage() && !damageSource.isProjectile()) {
                    evt.setAmount(evt.getAmount() * 0.75f);
                }
            } else if (evt.getEntityLiving() instanceof EntityPlayer) {
                EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();
                @org.jetbrains.annotations.Nullable RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                evt.setAmount(evt.getAmount() + evt.getAmount() * requiemCard.getKuvaLevel() * ConfigKuvaLich.increaseDamage);
            }
        }
    }

    public @NotNull EnumCreatureAttribute getCreatureAttribute() {
        return EnumCreatureAttribute.UNDEAD;
    }

    @Override
    public void registerControllers(@Nonnull AnimationData data) {
        data.addAnimationController(new AnimationController(this, "controller", 0, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Nonnull
    private <E extends IAnimatable> PlayState predicate(@Nonnull AnimationEvent<E> evt) {
        if (this.isSwingInProgress) {
            evt.getController().setAnimation(new AnimationBuilder().addAnimation("attack", true));
        } else if (evt.isMoving()) {
            if (this.getDataManager().get(HASTARGET)) {
                evt.getController().setAnimation(new AnimationBuilder().addAnimation("run", true));
            } else {
                evt.getController().setAnimation(new AnimationBuilder().addAnimation("walk", true));
            }
        } else {
            evt.getController().setAnimation(new AnimationBuilder().addAnimation("stay", true));
        }
        return PlayState.CONTINUE;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ENTITY_IRONGOLEM_STEP;
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
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>(this, EntityMob.class, 16, true, true, new Predicate<EntityMob>() {

            @Override
            public boolean apply(@Nullable EntityMob entityMob) {
                return entityMob != null && IMob.VISIBLE_MOB_SELECTOR.apply(entityMob) && !(entityMob instanceof EntityCreeper) && !(entityMob instanceof KuvaBase);
            }

        }));
    }

    @Override
    public void setAttackTarget(@Nullable EntityLivingBase livingBase) {
        if (livingBase != null && livingBase.isEntityAlive()) {
            this.getDataManager().set(HASTARGET, livingBase != null);
        }
        super.setAttackTarget(livingBase);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.getDataManager().register(HASTARGET, false);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT;
    }

    @Override
    public void tick() {
        super.onUpdate();
    }

    @Override
    public int tickTimer() {
        return ticksExisted;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!this.world.isRemote) {
            if (this.getAttackTarget() == null || !this.getAttackTarget().isEntityAlive()) {
                this.getDataManager().set(HASTARGET, false);
            }
        }
        if (this.getDataManager().get(HASTARGET)) {
            battleTick++;
            if (world.getTotalWorldTime() % 20 == 0) {
                if (this.getMaxHealth() > this.getHealth()) {
                    this.heal(getTickHeal());
                }
            }
        } else {
            if (battleTick > 0) {
                battleTick--;
            }
            if (world.getTotalWorldTime() % 20 == 0) {
                if (this.getMaxHealth() > this.getHealth()) {
                    this.heal(getTickHeal());
                }
            }
        }
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        if (source.damageType.equalsIgnoreCase("fall")) {
            return false;
        } else {
            return super.attackEntityFrom(source, amount);
        }
    }

    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource damageSourceIn) {
        return SoundEvents.ENTITY_WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WITHER_SKELETON_DEATH;
    }

    public int getBattleTick() {
        return battleTick;
    }

    protected abstract float getTickHeal();

    protected abstract float getHasTargetTickHeal();
}
