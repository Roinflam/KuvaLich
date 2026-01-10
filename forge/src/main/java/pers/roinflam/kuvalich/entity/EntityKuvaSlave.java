// 文件：EntityKuvaSlave.java
// 路径：src/main/java/pers/roinflam/kuvalich/entity/EntityKuvaSlave.java
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

import pers.roinflam.kuvalich.base.entity.KuvaBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
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

public class EntityKuvaSlave extends KuvaBase {
    public static final String NAME = "kuva_slave";
    public static final String ID = Reference.MOD_ID + ":" + NAME;

    // 常量定义
    private static final float SIZE_WIDTH = 1.5F;
    private static final float SIZE_HEIGHT = 2.9F;
    private static final float BASE_HEAL_MULTIPLIER = 0.01f;
    private static final float TARGET_HEAL_MULTIPLIER = 0.02f;
    private static final float MIN_HEAL_NO_TARGET = 1.0f;
    private static final float MIN_HEAL_HAS_TARGET = 2.0f;
    private static final float ATTACK_DAMAGE_MULTIPLIER = 0.85f;
    private static final float KNOCKBACK_MULTIPLIER = 3.0f;
    private static final float VERTICAL_KNOCKBACK = 2.0f;
    private static final float SELF_KNOCKBACK_MULTIPLIER = 1.15f;
    private static final float HEAL_PER_ATTACK = 0.1f;

    public EntityKuvaSlave(@Nonnull World worldIn) {
        super(worldIn);
        this.setSize(SIZE_WIDTH, SIZE_HEIGHT);
    }

    @Override
    protected float getTickHeal() {
        return Math.max(MIN_HEAL_NO_TARGET, (this.getMaxHealth() - this.getHealth()) * BASE_HEAL_MULTIPLIER);
    }

    @Override
    protected float getHasTargetTickHeal() {
        return Math.max(MIN_HEAL_HAS_TARGET, (this.getMaxHealth() - this.getHealth()) * TARGET_HEAL_MULTIPLIER);
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        this.playSound(SoundEvents.ENTITY_IRONGOLEM_ATTACK, 1.0F, 1.0F);

        float attackDamage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        float finalDamage = attackDamage * this.world.getDifficulty().getDifficultyId() * ATTACK_DAMAGE_MULTIPLIER;

        if (entityIn.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), finalDamage)) {
            applyKnockback(entityIn);
            this.heal(attackDamage * HEAL_PER_ATTACK);
            return true;
        }

        return false;
    }

    /**
     * 应用击退效果
     */
    private void applyKnockback(Entity target) {
        target.motionX *= KNOCKBACK_MULTIPLIER;
        target.motionY *= VERTICAL_KNOCKBACK;
        target.motionZ *= KNOCKBACK_MULTIPLIER;

        this.motionX = target.motionX * SELF_KNOCKBACK_MULTIPLIER;
        this.motionY = target.motionY * SELF_KNOCKBACK_MULTIPLIER;
        this.motionZ = target.motionZ * SELF_KNOCKBACK_MULTIPLIER;
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
                handlePlayerKill((EntityPlayer) cause.getTrueSource());
            }
        }
        super.onDeath(cause);
    }

    /**
     * 处理玩家击杀奖励
     */
    private void handlePlayerKill(EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        if (requiemCard == null) {
            return;
        }

        // 给予解密进度
        giveDecryptionProgress(player, requiemCard);

        // 掉落物品
        dropLoot(player);
    }

    /**
     * 给予解密进度
     */
    private void giveDecryptionProgress(EntityPlayer player, RequiemCard requiemCard) {
        int addPotion = RandomUtil.getInt(ModConfig.KUVA_LICH.minDecryptionProgress, ModConfig.KUVA_LICH.maxDecryptionProgress);

        if (requiemCard.addPotion(addPotion)) {
            boolean max = requiemCard.getPointsRequired() == -1;
            TextComponentTranslation message = new TextComponentTranslation(
                    "message.kuvalich.getPoints",
                    addPotion,
                    max ? "Max" : requiemCard.getDecryptionProgress(),
                    max ? "Max" : requiemCard.getPointsRequired()
            );
            message.getStyle().setColor(TextFormatting.RED);
            player.sendMessage(message);
        } else {
            TextComponentTranslation message = new TextComponentTranslation("message.kuvalich.maxLevel");
            message.getStyle().setColor(TextFormatting.RED);
            player.sendMessage(message);
        }
    }

    /**
     * 掉落战利品
     */
    private void dropLoot(EntityPlayer player) {
        BlockPos pos = this.getPosition();

        // 掉落模组
        dropModule(pos);

        // 掉落Kuva
        if (RandomUtil.percentageChance(10)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(2, 8)));
        }

        // 掉落Riven碎片
        if (RandomUtil.percentageChance(10)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.RivenSliver, 1));
        }

        // 掉落安魂宝石（受抢夺附魔影响）
        int lootingLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, player.getHeldItemMainhand());
        if (RandomUtil.percentageChance(25 + 2.5 * lootingLevel)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_GEM, 1));
        }
    }

    /**
     * 掉落模组
     */
    private void dropModule(BlockPos pos) {
        double roll = Math.random() * 100;

        if (roll < 15) {
            // 15% 掉落普通模组
            ItemStack module = RandomUtil.percentageChance(75)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < 25) {
            // 10% 掉落稀有模组
            ItemStack module = RandomUtil.percentageChance(75)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < 30) {
            // 5% 掉落史诗模组
            ItemStack module = RandomUtil.percentageChance(75)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
            spawnItem(pos, module);
        }
    }

    /**
     * 生成掉落物
     */
    private void spawnItem(BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        EntityItem entityItem = new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), itemStack);
        world.spawnEntity(entityItem);
    }
}