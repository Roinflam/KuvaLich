package pers.roinflam.kuvalich.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.entity.KuvaBase;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.ItemPrimeModule;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframePrimeModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EntityKuvaMaster extends KuvaBase {
    public static final String NAME = "kuva_master";
    public static final String ID = Reference.MOD_ID + ":" + NAME;

    public EntityKuvaMaster(@Nonnull World worldIn) {
        super(worldIn);
        this.setSize(1.75F, 3.4F);
    }

    @Override
    protected float getTickHeal() {
        return Math.max(1 / 20.0f, (this.getMaxHealth() - this.getHealth()) * 0.05f / 20);
    }

    @Override
    protected float getHasTargetTickHeal() {
        return Math.max(20 / 20.0f, (this.getMaxHealth() - this.getHealth()) * 0.07f);
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F, 1.0F);
        float attackDamage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        for (@NotNull EntityLivingBase entityLivingBase : EntityUtil.getNearbyEntities(EntityLivingBase.class, this, 3, entity -> !(entity instanceof KuvaBase) && !entity.equals(entityIn))) {
            if (entityLivingBase.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), (float) (attackDamage * this.world.getDifficulty().getDifficultyId() * 0.85))) {
                entityLivingBase.motionX *= 3.15;
                entityLivingBase.motionY *= 2.15;
                entityLivingBase.motionZ *= 3.15;
            }
        }
        if (entityIn.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), (float) (attackDamage * this.world.getDifficulty().getDifficultyId() * 0.85))) {
            entityIn.motionX *= 3.15;
            entityIn.motionY *= 2.15;
            entityIn.motionZ *= 3.15;
            this.motionX = entityIn.motionX * 1.15;
            this.motionY = entityIn.motionY * 1.15;
            this.motionZ = entityIn.motionZ * 1.15;
            this.heal(attackDamage * 0.2f);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setAbsorptionAmount(200);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(18);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.65);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(15);
    }

    @Override
    public void onDeath(@Nonnull DamageSource cause) {
        if (!world.isRemote) {
            this.experienceValue = RandomUtil.getInt(50, 200);
            @NotNull BlockPos blockPos = this.getPosition();
            if (cause.getTrueSource() instanceof EntityPlayer) {
                @Nullable EntityPlayer player = (EntityPlayer) cause.getTrueSource();
                @org.jetbrains.annotations.Nullable RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                if (requiemCard.isReadyCard()) {
                    if (requiemCard.isCorrectAnswer()) {
                        requiemCard.reset();

                        @Nonnull EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), KuvaWeapon.getItem(KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1)), requiemCard.getMinimumLevelWeapon(), requiemCard.getMaximumLevelWeapon()));
                        int randomNum = RandomUtil.getInt(ConfigKuvaLich.minimumLevelCapIncrease, ConfigKuvaLich.maximumLevelCapIncrease);
                        world.spawnEntity(entityItem);
                        entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.LICH_RELIQUARY));
                        world.spawnEntity(entityItem);

                        entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.RivenSliver, RandomUtil.getInt(2, 4)));
                        world.spawnEntity(entityItem);

                        entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(16, 32)));
                        world.spawnEntity(entityItem);

                        if (RandomUtil.percentageChance(25)) {
                            entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemPrimeModule.getRandomModule() : WarframePrimeModule.getRandomModule());
                            world.spawnEntity(entityItem);
                        } else if (RandomUtil.percentageChance(25)) {
                            entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemRivenModule.getRandomModule() : WarframeRivenModule.getRandomModule());
                            world.spawnEntity(entityItem);
                        }


                        if (requiemCard.getMinimumLevelWeapon() < ConfigKuvaLich.minimumLevel) {
                            requiemCard.setMinimumLevelWeapon(Math.min(ConfigKuvaLich.minimumLevel, requiemCard.getMinimumLevelWeapon() + randomNum));

                            @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.minimumLevelWeapon", requiemCard.getMinimumLevelWeapon());
                            textComponentString.getStyle().setColor(TextFormatting.GREEN);
                            player.sendMessage(textComponentString);
                        }
                        if (requiemCard.getMaximumLevelWeapon() < ConfigKuvaLich.maximumLevel) {
                            requiemCard.setMaximumLevelWeapon(Math.min(ConfigKuvaLich.maximumLevel, requiemCard.getMaximumLevelWeapon() + randomNum));

                            @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.maximumLevelWeapon", requiemCard.getMaximumLevelWeapon());
                            textComponentString.getStyle().setColor(TextFormatting.GREEN);
                            player.sendMessage(textComponentString);
                        }

                        return;
                    }
                    requiemCard.setKuvaLevel(requiemCard.getKuvaLevel() + 1);
                    @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.failedToDecrypt", requiemCard.getKuvaLevel());
                    textComponentString.getStyle().setColor(TextFormatting.RED);
                    player.sendMessage(textComponentString);
                }
                int addPotion = RandomUtil.getInt((int) (ConfigKuvaLich.minDecryptionProgress * ConfigKuvaLich.masterPotionMultiplier), (int) (ConfigKuvaLich.maxDecryptionProgress * ConfigKuvaLich.masterPotionMultiplier));
                if (requiemCard.addPotion(addPotion)) {
                    boolean max = requiemCard.getPointsRequired() == -1;
                    @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.getPoints", addPotion, max ? "Max" : requiemCard.getDecryptionProgress(), max ? "Max" : requiemCard.getPointsRequired());
                    textComponentString.getStyle().setColor(TextFormatting.RED);
                    player.sendMessage(textComponentString);
                } else if (!requiemCard.isCorrectAnswer() && !requiemCard.isReadyCard()) {
                    @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.maxLevel");
                    textComponentString.getStyle().setColor(TextFormatting.RED);
                    player.sendMessage(textComponentString);
                }
            }
        }
        super.onDeath(cause);
    }
}
