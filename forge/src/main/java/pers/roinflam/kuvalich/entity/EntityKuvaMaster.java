// EntityKuvaMaster.java
package pers.roinflam.kuvalich.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.entity.KuvaBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.List;

/**
 * 赤毒玄骸实体（1.20.1版本，业务逻辑100%不变）
 * Kuva Master Entity (1.20.1 version, business logic 100% unchanged)
 */
public class EntityKuvaMaster extends KuvaBase {

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

    // 死亡语录（业务逻辑100%不变）/ Death messages (business logic 100% unchanged)
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

    public EntityKuvaMaster(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ATTACK_DAMAGE, 13.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ARMOR, 8.0);
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
    public boolean doHurtTarget(@NotNull Entity target) {
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.0F);

        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float finalDamage = attackDamage * this.level().getDifficulty().getId() * ATTACK_DAMAGE_MULTIPLIER;

        // AOE攻击周围敌人 / AOE attack nearby enemies
        attackNearbyEntities(finalDamage, target);

        // 攻击主目标 / Attack main target
        if (target.hurt(this.damageSources().mobAttack(this), finalDamage)) {
            applyKnockback(target);
            this.heal(attackDamage * HEAL_PER_ATTACK);
            return true;
        }

        return false;
    }

    /**
     * 攻击周围实体（AOE，业务逻辑100%不变）
     * Attack nearby entities (AOE, business logic 100% unchanged)
     */
    private void attackNearbyEntities(float damage, Entity excludeEntity) {
        AABB aabb = this.getBoundingBox().inflate(AOE_RADIUS);
        List<LivingEntity> nearbyEntities = this.level().getEntitiesOfClass(
                LivingEntity.class,
                aabb,
                entity -> !(entity instanceof KuvaBase) && !entity.equals(excludeEntity)
        );

        for (LivingEntity nearby : nearbyEntities) {
            if (nearby.hurt(this.damageSources().mobAttack(this), damage)) {
                applyKnockback(nearby);
            }
        }
    }

    private void applyKnockback(Entity target) {
        target.setDeltaMovement(
                target.getDeltaMovement().x * KNOCKBACK_MULTIPLIER,
                target.getDeltaMovement().y * VERTICAL_KNOCKBACK,
                target.getDeltaMovement().z * KNOCKBACK_MULTIPLIER
        );

        this.setDeltaMovement(
                target.getDeltaMovement().x * SELF_KNOCKBACK_MULTIPLIER,
                target.getDeltaMovement().y * SELF_KNOCKBACK_MULTIPLIER,
                target.getDeltaMovement().z * SELF_KNOCKBACK_MULTIPLIER
        );
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        if (!level().isClientSide) {
            if (damageSource.getEntity() instanceof Player player) {
                handlePlayerKill(player);
            }
        }
        super.die(damageSource);
    }

    /**
     * 处理玩家击杀（业务逻辑100%不变）
     * Handle player kill (business logic 100% unchanged)
     */
    private void handlePlayerKill(Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            BlockPos pos = this.blockPosition();

            // 检查是否正确破解 / Check if correctly decrypted
            if (requiemCard.isReadyCard()) {
                if (requiemCard.isCorrectAnswer()) {
                    handleSuccessfulDecryption(player, requiemCard, pos);
                    return;
                }

                handleFailedDecryption(player, requiemCard);
            }

            // 给予解密进度 / Give decryption progress
            giveDecryptionProgress(player, requiemCard);
        });
    }

    /**
     * 处理成功破解
     * Handle successful decryption
     *
     * 修改说明：卡片不再直接消失，而是每次消耗1点耐久
     * Change notes: Cards no longer disappear directly, instead consume 1 durability each time
     * - 仍有耐久的卡片会归还到玩家背包
     *   Cards with remaining durability are returned to player inventory
     * - 耐久耗尽的卡片才会消失
     *   Cards only disappear when durability is depleted
     */
    private void handleSuccessfulDecryption(Player player, RequiemCard requiemCard, BlockPos pos) {
        // 发送死亡语录 / Send death message
        sendDeathMessage(player);

        // 掉落被没收的物品 / Drop confiscated items
        dropConfiscatedItems(player, requiemCard, pos);

        // ★ 先消耗卡片耐久，获取仍有耐久的存活卡片
        // ★ Consume card durability first, get surviving cards that still have durability
        List<ItemStack> survivingCards = requiemCard.consumeCardsAndGetSurvivors();

        // 重置安魂卡片数据（谜语、答案、进度等，卡片槽已在consume中清空）
        // Reset requiem card data (riddles, answers, progress, etc. Card slots already cleared in consume)
        requiemCard.reset();

        // ★ 将仍有耐久的卡片归还给玩家
        // ★ Return cards that still have durability to the player
        for (ItemStack survivingCard : survivingCards) {
            if (!player.getInventory().add(survivingCard)) {
                // 背包满时掉落到地上
                // Drop on ground if inventory is full
                spawnItem(pos, survivingCard);
            }
        }

        // 掉落Kuva武器 / Drop Kuva weapon
        ItemStack weapon = KuvaWeapon.getItem(
                KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1)),
                requiemCard.getMinimumLevelWeapon(),
                requiemCard.getMaximumLevelWeapon()
        );
        spawnItem(pos, weapon);

        // 掉落其他战利品 / Drop other loot
        spawnItem(pos, new ItemStack(KuvaLichItems.LICH_RELIQUARY.get()));
        spawnItem(pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), RandomUtil.getInt(4, 8)));
        spawnItem(pos, new ItemStack(KuvaLichItems.KUVA.get(), RandomUtil.getInt(32, 64)));

        // 掉落高级模组 / Drop advanced module
        dropAdvancedModule(pos);

        // 掉落安魂通牒（可配置几率）/ Drop Requiem Ultimatum (configurable chance)
        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.requiemUltimatumDropChance.get())) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_ULTIMATUM.get(), 1));
        }

        // 提升武器等级上限 / Upgrade weapon level cap
        upgradeWeaponLevelCap(player, requiemCard);
    }

    /**
     * 掉落被没收的物品（业务逻辑100%不变）
     * Drop confiscated items (business logic 100% unchanged)
     */
    private void dropConfiscatedItems(Player player, RequiemCard requiemCard, BlockPos pos) {
        if (!requiemCard.hasConfiscatedItems()) {
            return;
        }

        int itemCount = requiemCard.getConfiscatedItemCount();
        player.sendSystemMessage(Component.translatable("message.kuvalich.confiscation.return", itemCount)
                .withStyle(ChatFormatting.DARK_RED));

        List<ItemStack> confiscatedItems = requiemCard.clearAndGetConfiscatedItems();

        for (ItemStack item : confiscatedItems) {
            spawnItem(pos, item);
        }
    }

    private void sendDeathMessage(Player player) {
        String messageKey = DEATH_MESSAGES[RandomUtil.getInt(0, DEATH_MESSAGES.length - 1)];
        player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED));
    }

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
     * 提升武器等级上限（业务逻辑100%不变）
     * Upgrade weapon level cap (business logic 100% unchanged)
     */
    private void upgradeWeaponLevelCap(Player player, RequiemCard requiemCard) {
        int randomNum = RandomUtil.getInt(
                ModConfig.KUVA_LICH.minimumLevelCapIncrease.get(),
                ModConfig.KUVA_LICH.maximumLevelCapIncrease.get()
        );

        if (requiemCard.getMinimumLevelWeapon() < ModConfig.KUVA_LICH.minimumLevel.get()) {
            requiemCard.setMinimumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.minimumLevel.get(),
                            requiemCard.getMinimumLevelWeapon() + randomNum)
            );
            sendMessage(player, "message.kuvalich.minimumLevelWeapon",
                    requiemCard.getMinimumLevelWeapon());
        }

        if (requiemCard.getMaximumLevelWeapon() < ModConfig.KUVA_LICH.maximumLevel.get()) {
            requiemCard.setMaximumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.maximumLevel.get(),
                            requiemCard.getMaximumLevelWeapon() + randomNum)
            );
            sendMessage(player, "message.kuvalich.maximumLevelWeapon",
                    requiemCard.getMaximumLevelWeapon());
        }
    }

    /**
     * 处理破解失败（业务逻辑100%不变）
     * Handle failed decryption (business logic 100% unchanged)
     */
    private void handleFailedDecryption(Player player, RequiemCard requiemCard) {
        if (requiemCard.isFirstCorrectAnswer()) {
            sendMessage(player, "message.kuvalich.firstCorrect", ChatFormatting.RED);
            if (requiemCard.isTwoCorrectAnswer()) {
                sendMessage(player, "message.kuvalich.secondCorrect", ChatFormatting.RED);
            }
        }

        requiemCard.setKuvaLevel(requiemCard.getKuvaLevel() + 1);
        sendMessage(player, "message.kuvalich.failedToDecrypt",
                ChatFormatting.RED, requiemCard.getKuvaLevel());
    }

    /**
     * 给予解密进度（业务逻辑100%不变）
     * Give decryption progress (business logic 100% unchanged)
     */
    private void giveDecryptionProgress(Player player, RequiemCard requiemCard) {
        int addPotion = RandomUtil.getInt(
                (int) (ModConfig.KUVA_LICH.minDecryptionProgress.get() * ModConfig.KUVA_LICH.masterPotionMultiplier.get()),
                (int) (ModConfig.KUVA_LICH.maxDecryptionProgress.get() * ModConfig.KUVA_LICH.masterPotionMultiplier.get())
        );

        if (requiemCard.addPotion(addPotion)) {
            boolean max = requiemCard.getPointsRequired() == -1;
            sendMessage(player, "message.kuvalich.getPoints",
                    ChatFormatting.RED,
                    addPotion,
                    max ? "Max" : requiemCard.getDecryptionProgress(),
                    max ? "Max" : requiemCard.getPointsRequired());
        } else if (!requiemCard.isCorrectAnswer() && !requiemCard.isReadyCard()) {
            sendMessage(player, "message.kuvalich.maxLevel", ChatFormatting.RED);
        }
    }

    private void sendMessage(Player player, String key, Object... args) {
        sendMessage(player, key, ChatFormatting.GREEN, args);
    }

    private void sendMessage(Player player, String key, ChatFormatting color, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
    }

    private void spawnItem(BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        ItemEntity entityItem = new ItemEntity(level(), pos.getX(), pos.getY(), pos.getZ(), itemStack);
        level().addFreshEntity(entityItem);
    }

    @Override
    public @NotNull EntityType<?> getType() {
        return KuvaLichEntities.KUVA_MASTER.get();
    }
}