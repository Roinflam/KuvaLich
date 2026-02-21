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
import net.minecraft.world.phys.Vec3;
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
 * 赤毒玄骸实体
 * Kuva Master Entity
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

        attackNearbyEntities(finalDamage, target);

        if (target.hurt(this.damageSources().mobAttack(this), finalDamage)) {
            applyKnockback(target);
            this.heal(attackDamage * HEAL_PER_ATTACK);
            return true;
        }

        return false;
    }

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

    /**
     * 修复：先保存目标的原始速度，再分别计算 target 和 self 的新速度
     * Fix: save target's original velocity before modification, then calculate both independently
     */
    private void applyKnockback(Entity target) {
        // 保存原始速度，避免 self 的计算依赖被修改后的 target 速度
        // Save original velocity to avoid self calculation depending on already-modified target velocity
        Vec3 originalTargetVelocity = target.getDeltaMovement();

        target.setDeltaMovement(
                originalTargetVelocity.x * KNOCKBACK_MULTIPLIER,
                originalTargetVelocity.y * VERTICAL_KNOCKBACK,
                originalTargetVelocity.z * KNOCKBACK_MULTIPLIER
        );

        this.setDeltaMovement(
                originalTargetVelocity.x * SELF_KNOCKBACK_MULTIPLIER,
                originalTargetVelocity.y * SELF_KNOCKBACK_MULTIPLIER,
                originalTargetVelocity.z * SELF_KNOCKBACK_MULTIPLIER
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

    private void handlePlayerKill(Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            BlockPos pos = this.blockPosition();

            if (requiemCard.isReadyCard()) {
                if (requiemCard.isCorrectAnswer()) {
                    handleSuccessfulDecryption(player, requiemCard, pos);
                    return;
                }

                handleFailedDecryption(player, requiemCard);
            }

            giveDecryptionProgress(player, requiemCard);
        });
    }

    private void handleSuccessfulDecryption(Player player, RequiemCard requiemCard, BlockPos pos) {
        sendDeathMessage(player);

        dropConfiscatedItems(player, requiemCard, pos);

        List<ItemStack> survivingCards = requiemCard.consumeCardsAndGetSurvivors();
        requiemCard.reset();

        for (ItemStack survivingCard : survivingCards) {
            if (!player.getInventory().add(survivingCard)) {
                spawnItem(pos, survivingCard);
            }
        }

        ItemStack weapon = KuvaWeapon.getItem(
                KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1)),
                requiemCard.getMinimumLevelWeapon(),
                requiemCard.getMaximumLevelWeapon()
        );
        spawnItem(pos, weapon);

        spawnItem(pos, new ItemStack(KuvaLichItems.LICH_RELIQUARY.get()));

        spawnItem(pos, new ItemStack(
                KuvaLichItems.RIVEN_SLIVER.get(),
                RandomUtil.getInt(
                        ModConfig.KUVA_LICH.masterRivenSliverMinAmount.get(),
                        ModConfig.KUVA_LICH.masterRivenSliverMaxAmount.get()
                )
        ));

        spawnItem(pos, new ItemStack(
                KuvaLichItems.KUVA.get(),
                RandomUtil.getInt(
                        ModConfig.KUVA_LICH.masterKuvaMinAmount.get(),
                        ModConfig.KUVA_LICH.masterKuvaMaxAmount.get()
                )
        ));

        dropAdvancedModule(pos);

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.requiemUltimatumDropChance.get())) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_ULTIMATUM.get(), 1));
        }

        upgradeWeaponLevelCap(player, requiemCard);
    }

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

    /**
     * 掉落高级模组，Prime与裂罅的概率从配置读取
     * Drop advanced module, Prime vs Riven ratio read from config
     *
     * 修复：统一使用 RandomUtil，不再混用 Math.random()
     * Fix: use RandomUtil consistently instead of mixing with Math.random()
     */
    private void dropAdvancedModule(BlockPos pos) {
        int weaponRatio = ModConfig.KUVA_LICH.moduleWeaponRatio.get();
        ItemStack module;

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.masterPrimeModuleChance.get())) {
            module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemPrimeModule.getRandomModule()
                    : WarframePrimeModule.getRandomModule();
        } else {
            module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemRivenModule.getRandomModule()
                    : WarframeRivenModule.getRandomModule();
        }
        spawnItem(pos, module);
    }

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