// 路径：src/main/java/pers/roinflam/kuvalich/entity/EntityKuvaMaster.java
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

import pers.roinflam.kuvalich.base.entity.KuvaBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
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
import java.util.List;

/**
 * 赤毒玄骸实体类
 *
 * 高级敌对生物，拥有AOE攻击能力
 * 击杀后可获得高级战利品和解密进度
 */
public class EntityKuvaMaster extends KuvaBase {
    public static final String NAME = "kuva_master";
    public static final String ID = Reference.MOD_ID + ":" + NAME;

    // 常量定义
    private static final float SIZE_WIDTH = 1.75F;
    private static final float SIZE_HEIGHT = 3.4F;
    private static final float BASE_HEAL_MULTIPLIER = 0.02f;
    private static final float TARGET_HEAL_MULTIPLIER = 0.04f;
    private static final float MIN_HEAL_NO_TARGET = 2.0f;
    private static final float MIN_HEAL_HAS_TARGET = 4.0f;
    private static final float ATTACK_DAMAGE_MULTIPLIER = 0.85f;
    private static final float KNOCKBACK_MULTIPLIER = 3.15f;
    private static final float VERTICAL_KNOCKBACK = 2.15f;
    private static final float SELF_KNOCKBACK_MULTIPLIER = 1.15f;
    private static final float HEAL_PER_ATTACK = 0.2f;
    private static final float AOE_RADIUS = 3.0f;

    // 解密成功时的死亡语录key
    private static final String[] DEATH_MESSAGES = {
            "message.kuvalich.death.hurts",
            "message.kuvalich.death.fair",
            "message.kuvalich.death.nexttime",
            "message.kuvalich.death.winsome",
            "message.kuvalich.death.comeon",
            "message.kuvalich.death.forgive",
            "message.kuvalich.death.failed",
            "message.kuvalich.death.unacceptable"
    };

    public EntityKuvaMaster(@Nonnull World worldIn) {
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

        // AOE攻击周围敌人
        attackNearbyEntities(finalDamage, entityIn);

        // 攻击主目标
        if (entityIn.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), finalDamage)) {
            applyKnockback(entityIn);
            this.heal(attackDamage * HEAL_PER_ATTACK);
            return true;
        }

        return false;
    }

    /**
     * 攻击周围实体（AOE）
     *
     * @param damage 伤害值
     * @param excludeEntity 排除的实体（主目标）
     */
    private void attackNearbyEntities(float damage, Entity excludeEntity) {
        for (EntityLivingBase nearby : EntityUtil.getNearbyEntities(
                EntityLivingBase.class, this, AOE_RADIUS,
                entity -> !(entity instanceof KuvaBase) && !entity.equals(excludeEntity))) {

            if (nearby.attackEntityFrom(DamageSource.causeMobDamage(this).setDifficultyScaled(), damage)) {
                applyKnockback(nearby);
            }
        }
    }

    /**
     * 应用击退效果
     *
     * @param target 目标实体
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
        this.setAbsorptionAmount(200);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(13);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0.65);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.35);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(64);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(8);
    }

    @Override
    public void onDeath(@Nonnull DamageSource cause) {
        if (!world.isRemote) {
            this.experienceValue = RandomUtil.getInt(50, 200);

            if (cause.getTrueSource() instanceof EntityPlayer) {
                handlePlayerKill((EntityPlayer) cause.getTrueSource());
            }
        }
        super.onDeath(cause);
    }

    /**
     * 处理玩家击杀
     *
     * @param player 击杀玩家
     */
    private void handlePlayerKill(EntityPlayer player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        if (requiemCard == null) {
            return;
        }

        BlockPos pos = this.getPosition();

        // 检查是否正确破解
        if (requiemCard.isReadyCard()) {
            if (requiemCard.isCorrectAnswer()) {
                handleSuccessfulDecryption(player, requiemCard, pos);
                return;
            }

            handleFailedDecryption(player, requiemCard);
        }

        // 给予解密进度
        giveDecryptionProgress(player, requiemCard);
    }

    /**
     * 处理成功破解
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片数据
     * @param pos 掉落位置
     */
    private void handleSuccessfulDecryption(EntityPlayer player, RequiemCard requiemCard, BlockPos pos) {
        // 发送死亡语录
        sendDeathMessage(player);

        // 先掉落被没收的物品
        dropConfiscatedItems(player, requiemCard, pos);

        requiemCard.reset();

        // 掉落Kuva武器
        ItemStack weapon = KuvaWeapon.getItem(
                KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1)),
                requiemCard.getMinimumLevelWeapon(),
                requiemCard.getMaximumLevelWeapon()
        );
        spawnItem(pos, weapon);

        // 掉落其他战利品
        spawnItem(pos, new ItemStack(KuvaLichItems.LICH_RELIQUARY));
        spawnItem(pos, new ItemStack(KuvaLichItems.RivenSliver, RandomUtil.getInt(4, 8)));
        spawnItem(pos, new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(32, 64)));

        // 掉落高级模组
        dropAdvancedModule(pos);

        // 掉落安魂通牒（可配置几率）
        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.requiemUltimatumDropChance)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_ULTIMATUM, 1));
        }

        // 提升武器等级上限
        upgradeWeaponLevelCap(player, requiemCard);
    }

    /**
     * 掉落被没收的物品
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片数据
     * @param pos 掉落位置
     */
    private void dropConfiscatedItems(EntityPlayer player, RequiemCard requiemCard, BlockPos pos) {
        if (!requiemCard.hasConfiscatedItems()) {
            return;
        }

        // 发送返还物品提示
        int itemCount = requiemCard.getConfiscatedItemCount();
        TextComponentTranslation message = new TextComponentTranslation("message.kuvalich.confiscation.return", itemCount);
        message.getStyle().setColor(TextFormatting.DARK_RED);
        player.sendMessage(message);

        // 获取并清空没收物品列表
        List<ItemStack> confiscatedItems = requiemCard.clearAndGetConfiscatedItems();

        // 掉落所有被没收的物品
        for (ItemStack item : confiscatedItems) {
            spawnItem(pos, item);
        }
    }

    /**
     * 发送死亡语录
     *
     * @param player 玩家
     */
    private void sendDeathMessage(EntityPlayer player) {
        String messageKey = DEATH_MESSAGES[RandomUtil.getInt(0, DEATH_MESSAGES.length - 1)];
        TextComponentTranslation message = new TextComponentTranslation(messageKey);
        message.getStyle().setColor(TextFormatting.DARK_RED);
        player.sendMessage(message);
    }

    /**
     * 掉落高级模组
     *
     * @param pos 掉落位置
     */
    private void dropAdvancedModule(BlockPos pos) {
        ItemStack module;
        if (RandomUtil.percentageChance(50)) {
            module = RandomUtil.percentageChance(75)
                    ? ItemPrimeModule.getRandomModule()
                    : WarframePrimeModule.getRandomModule();
        } else {
            module = RandomUtil.percentageChance(75)
                    ? ItemRivenModule.getRandomModule()
                    : WarframeRivenModule.getRandomModule();
        }
        spawnItem(pos, module);
    }

    /**
     * 提升武器等级上限
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片数据
     */
    private void upgradeWeaponLevelCap(EntityPlayer player, RequiemCard requiemCard) {
        int randomNum = RandomUtil.getInt(
                ModConfig.KUVA_LICH.minimumLevelCapIncrease,
                ModConfig.KUVA_LICH.maximumLevelCapIncrease
        );

        if (requiemCard.getMinimumLevelWeapon() < ModConfig.KUVA_LICH.minimumLevel) {
            requiemCard.setMinimumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.minimumLevel, requiemCard.getMinimumLevelWeapon() + randomNum)
            );
            sendMessage(player, "message.kuvalich.minimumLevelWeapon", requiemCard.getMinimumLevelWeapon());
        }

        if (requiemCard.getMaximumLevelWeapon() < ModConfig.KUVA_LICH.maximumLevel) {
            requiemCard.setMaximumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.maximumLevel, requiemCard.getMaximumLevelWeapon() + randomNum)
            );
            sendMessage(player, "message.kuvalich.maximumLevelWeapon", requiemCard.getMaximumLevelWeapon());
        }
    }

    /**
     * 处理破解失败
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片数据
     */
    private void handleFailedDecryption(EntityPlayer player, RequiemCard requiemCard) {
        if (requiemCard.isFirstCorrectAnswer()) {
            sendMessage(player, "message.kuvalich.firstCorrect", TextFormatting.RED);
            if (requiemCard.isTwoCorrectAnswer()) {
                sendMessage(player, "message.kuvalich.secondCorrect", TextFormatting.RED);
            }
        }

        requiemCard.setKuvaLevel(requiemCard.getKuvaLevel() + 1);
        sendMessage(player, "message.kuvalich.failedToDecrypt", requiemCard.getKuvaLevel(), TextFormatting.RED);
    }

    /**
     * 给予解密进度
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片数据
     */
    private void giveDecryptionProgress(EntityPlayer player, RequiemCard requiemCard) {
        int addPotion = RandomUtil.getInt(
                (int) (ModConfig.KUVA_LICH.minDecryptionProgress * ModConfig.KUVA_LICH.masterPotionMultiplier),
                (int) (ModConfig.KUVA_LICH.maxDecryptionProgress * ModConfig.KUVA_LICH.masterPotionMultiplier)
        );

        if (requiemCard.addPotion(addPotion)) {
            boolean max = requiemCard.getPointsRequired() == -1;
            sendMessage(player, "message.kuvalich.getPoints",
                    addPotion,
                    max ? "Max" : requiemCard.getDecryptionProgress(),
                    max ? "Max" : requiemCard.getPointsRequired(),
                    TextFormatting.RED);
        } else if (!requiemCard.isCorrectAnswer() && !requiemCard.isReadyCard()) {
            sendMessage(player, "message.kuvalich.maxLevel", TextFormatting.RED);
        }
    }

    /**
     * 发送消息给玩家（默认绿色）
     *
     * @param player 玩家
     * @param key 本地化键
     * @param args 参数
     */
    private void sendMessage(EntityPlayer player, String key, Object... args) {
        sendMessage(player, key, TextFormatting.GREEN, args);
    }

    /**
     * 发送带颜色的消息给玩家
     *
     * @param player 玩家
     * @param key 本地化键
     * @param color 文本颜色
     * @param args 参数
     */
    private void sendMessage(EntityPlayer player, String key, TextFormatting color, Object... args) {
        TextComponentTranslation message = new TextComponentTranslation(key, args);
        message.getStyle().setColor(color);
        player.sendMessage(message);
    }

    /**
     * 生成掉落物
     *
     * @param pos 掉落位置
     * @param itemStack 物品堆
     */
    private void spawnItem(BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        EntityItem entityItem = new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), itemStack);
        world.spawnEntity(entityItem);
    }
}