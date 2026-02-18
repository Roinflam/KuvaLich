// EntityKuvaSlave.java
package pers.roinflam.kuvalich.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.entity.KuvaBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

public class EntityKuvaSlave extends KuvaBase {

    private static final float BASE_HEAL_MULTIPLIER = 0.01f;
    private static final float TARGET_HEAL_MULTIPLIER = 0.02f;
    private static final float MIN_HEAL_NO_TARGET = 1.0f;
    private static final float MIN_HEAL_HAS_TARGET = 2.0f;
    private static final float ATTACK_DAMAGE_MULTIPLIER = 0.85f;
    private static final float KNOCKBACK_MULTIPLIER = 3.0f;
    private static final float VERTICAL_KNOCKBACK = 2.0f;
    private static final float SELF_KNOCKBACK_MULTIPLIER = 1.15f;
    private static final float HEAL_PER_ATTACK = 0.1f;

    public EntityKuvaSlave(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0);
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

        if (target.hurt(this.damageSources().mobAttack(this), finalDamage)) {
            applyKnockback(target);
            this.heal(attackDamage * HEAL_PER_ATTACK);
            return true;
        }

        return false;
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

    private void handlePlayerKill(Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            giveDecryptionProgress(player, requiemCard);
            dropLoot(player);
        });
    }

    private void giveDecryptionProgress(Player player, pers.roinflam.kuvalich.capability.RequiemCard requiemCard) {
        int addPotion = RandomUtil.getInt(
                ModConfig.KUVA_LICH.minDecryptionProgress.get(),
                ModConfig.KUVA_LICH.maxDecryptionProgress.get()
        );

        if (requiemCard.addPotion(addPotion)) {
            boolean max = requiemCard.getPointsRequired() == -1;
            player.sendSystemMessage(Component.translatable(
                    "message.kuvalich.getPoints",
                    addPotion,
                    max ? "Max" : requiemCard.getDecryptionProgress(),
                    max ? "Max" : requiemCard.getPointsRequired()
            ));
        } else {
            player.sendSystemMessage(Component.translatable("message.kuvalich.maxLevel"));
        }
    }

    private void dropLoot(Player player) {
        BlockPos pos = this.blockPosition();

        // 根据配置概率掉落模组 / Drop module based on config chance
        dropModule(pos);

        // 根据配置概率掉落赤毒 / Drop Kuva based on config chance
        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.slaveKuvaDropChance.get())) {
            spawnItem(pos, new ItemStack(
                    KuvaLichItems.KUVA.get(),
                    RandomUtil.getInt(
                            ModConfig.KUVA_LICH.slaveKuvaMinAmount.get(),
                            ModConfig.KUVA_LICH.slaveKuvaMaxAmount.get()
                    )
            ));
        }

        // 根据配置概率掉落裂罅碎块 / Drop Riven Sliver based on config chance
        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.slaveRivenSliverDropChance.get())) {
            spawnItem(pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 1));
        }

        // 安魂宝石：基础概率 + 每级时运加成 / Requiem Gem: base chance + looting bonus per level
        int lootingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, player.getMainHandItem());
        double gemChance = ModConfig.KUVA_LICH.slaveRequiemGemBaseChance.get()
                + ModConfig.KUVA_LICH.slaveRequiemGemLootingBonus.get() * lootingLevel;
        if (RandomUtil.percentageChance(gemChance)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_GEM.get(), 1));
        }
    }

    /**
     * 掉落模组，概率从配置文件读取
     * Drop module, chances read from config
     *
     * 按照 青铜 → 白银 → 黄金 的优先级滚动，每个模组类型使用独立配置概率
     * Rolls common → uncommon → rare in priority order, each type uses independent config chance
     */
    private void dropModule(BlockPos pos) {
        // 读取各级别概率 / Read each tier's chance from config
        int commonChance = ModConfig.KUVA_LICH.commonModuleDropChance.get();
        int uncommonChance = ModConfig.KUVA_LICH.uncommonModuleDropChance.get();
        int rareChance = ModConfig.KUVA_LICH.rareModuleDropChance.get();
        // 武器模组比例 / Weapon module ratio
        int weaponRatio = ModConfig.KUVA_LICH.moduleWeaponRatio.get();

        double roll = Math.random() * 100;

        // 先判断是否掉落青铜模组 / Check common module first
        if (roll < commonChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
            spawnItem(pos, module);
            // 再判断是否掉落白银模组（青铜+白银的总范围内）
            // Then check uncommon module (within common + uncommon range)
        } else if (roll < commonChance + uncommonChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
            spawnItem(pos, module);
            // 最后判断是否掉落黄金模组（青铜+白银+黄金的总范围内）
            // Finally check rare module (within common + uncommon + rare range)
        } else if (roll < commonChance + uncommonChance + rareChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
            spawnItem(pos, module);
        }
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
        return KuvaLichEntities.KUVA_SLAVE.get();
    }
}