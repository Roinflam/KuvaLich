package pers.roinflam.kuvalich.entity;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
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
import pers.roinflam.kuvalich.item.module.item.ItemCommonModule;
import pers.roinflam.kuvalich.item.module.item.ItemRareModule;
import pers.roinflam.kuvalich.item.module.item.ItemUncommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeCommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EntityKuvaSlave extends KuvaBase {
    public static final String NAME = "kuva_slave";
    public static final String ID = Reference.MOD_ID + ":" + NAME;

    public EntityKuvaSlave(@Nonnull World worldIn) {
        super(worldIn);
        this.setSize(1.5F, 2.9F);
    }

    @Override
    protected float getTickHeal() {
        return Math.max(1f, (this.getMaxHealth() - this.getHealth()) * 0.01f);
    }

    @Override
    protected float getHasTargetTickHeal() {
        return Math.max(2f, (this.getMaxHealth() - this.getHealth()) * 0.02f);
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F, 1.0F);
        float attackDamage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        if (entityIn.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), (float) (attackDamage * this.world.getDifficulty().getDifficultyId() * 0.85))) {
            entityIn.motionX *= 3;
            entityIn.motionY *= 2;
            entityIn.motionZ *= 3;
            this.motionX = entityIn.motionX * 1.15;
            this.motionY = entityIn.motionY * 1.15;
            this.motionZ = entityIn.motionZ * 1.15;
            this.heal(attackDamage * 0.1f);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setAbsorptionAmount(100);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.4);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(4);
    }

    @Override
    public void onDeath(@Nonnull DamageSource cause) {
        if (!world.isRemote) {
            this.experienceValue = RandomUtil.getInt(10, 50);
            if (cause.getTrueSource() instanceof EntityPlayer) {
                @Nullable EntityPlayer player = (EntityPlayer) cause.getTrueSource();
                @org.jetbrains.annotations.Nullable RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                int addPotion = RandomUtil.getInt(ConfigKuvaLich.minDecryptionProgress, ConfigKuvaLich.maxDecryptionProgress);

                @NotNull BlockPos blockPos = this.getPosition();
                EntityItem entityItem;
                if (RandomUtil.percentageChance(15)) {
                    entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemCommonModule.getRandomModule() : WarframeCommonModule.getRandomModule());
                    world.spawnEntity(entityItem);
                } else if (RandomUtil.percentageChance(10)) {
                    entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemUncommonModule.getRandomModule() : WarframeUncommonModule.getRandomModule());
                    world.spawnEntity(entityItem);
                } else if (RandomUtil.percentageChance(5)) {
                    entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemRareModule.getRandomModule() : WarframeRareModule.getRandomModule());
                    world.spawnEntity(entityItem);
                }

                if (RandomUtil.percentageChance(10)) {
                    entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(2, 8)));
                    world.spawnEntity(entityItem);
                }
                if (RandomUtil.percentageChance(10)) {
                    entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.RivenSliver, 1));
                    world.spawnEntity(entityItem);
                }

                if (requiemCard.addPotion(addPotion)) {
                    boolean max = requiemCard.getPointsRequired() == -1;
                    @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.getPoints", addPotion, max ? "Max" : requiemCard.getDecryptionProgress(), max ? "Max" : requiemCard.getPointsRequired());
                    textComponentString.getStyle().setColor(TextFormatting.RED);
                    player.sendMessage(textComponentString);
                } else {
                    @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.maxLevel");
                    textComponentString.getStyle().setColor(TextFormatting.RED);
                    player.sendMessage(textComponentString);
                }
                @Nonnull BlockPos pos = this.getPosition();
                if (RandomUtil.percentageChance(25 + 2.5 * EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, player.getHeldItemMainhand()))) {
                    entityItem = new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(KuvaLichItems.REQUIEM_GEM, 1));
                    world.spawnEntity(entityItem);
                }
            }
        }
        super.onDeath(cause);
    }
}
