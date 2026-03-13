// EntityKuvaSlave.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/entity/EntityKuvaSlave.java
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

/**
 * 赤毒奴仆实体
 * 普通小怪，拥有25%固定伤害减免
 *
 * Kuva Slave Entity
 * Regular minion with flat 25% damage reduction
 */
public class EntityKuvaSlave extends AbstractKuva {

    // ==================== 回血参数（已砍半） ====================

    /** 无目标时基础回血倍率（基于缺失血量） */
    private static final float BASE_HEAL_MULTIPLIER = 0.005f;
    /** 有目标时基础回血倍率（基于缺失血量） */
    private static final float TARGET_HEAL_MULTIPLIER = 0.01f;
    /** 无目标时最低回血量 */
    private static final float MIN_HEAL_NO_TARGET = 0.5f;
    /** 有目标时最低回血量 */
    private static final float MIN_HEAL_HAS_TARGET = 1.0f;

    // ==================== 攻击参数 ====================

    /** 攻击伤害系数（受难度影响） */
    private static final float ATTACK_DAMAGE_MULTIPLIER = 0.85f;
    /** 击退目标的水平倍率 */
    private static final float KNOCKBACK_MULTIPLIER = 3.0f;
    /** 击退目标的垂直倍率 */
    private static final float VERTICAL_KNOCKBACK = 2.0f;
    /** 自身受到的击退倍率 */
    private static final float SELF_KNOCKBACK_MULTIPLIER = 1.15f;
    /** 每次攻击命中的自愈比例 */
    private static final float HEAL_PER_ATTACK = 0.1f;

    // ==================== 抗性参数 ====================

    /** 固定伤害减免倍率（25%减伤，即乘以0.75） */
    private static final float FLAT_DAMAGE_REDUCTION = 0.75f;

    public EntityKuvaSlave(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * 创建奴仆属性
     *
     * @return 属性构建器
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0);
    }

    // ==================== 抗性：固定25%减伤 ====================

    /**
     * 奴仆的高级抗性：简单的25%固定减伤
     * 在基础伤害类型修正之后应用
     *
     * @param source 伤害来源
     * @param damage 伤害值
     * @return 减伤后的伤害值
     */
    @Override
    protected float applyAdvancedResistance(DamageSource source, float damage) {
        return damage * FLAT_DAMAGE_REDUCTION;
    }

    // ==================== 回血 ====================

    @Override
    protected float getTickHeal() {
        return Math.max(MIN_HEAL_NO_TARGET, (this.getMaxHealth() - this.getHealth()) * BASE_HEAL_MULTIPLIER);
    }

    @Override
    protected float getHasTargetTickHeal() {
        return Math.max(MIN_HEAL_HAS_TARGET, (this.getMaxHealth() - this.getHealth()) * TARGET_HEAL_MULTIPLIER);
    }

    // ==================== 攻击 ====================

    /**
     * 对目标造成伤害，附带击退和自愈
     *
     * @param target 攻击目标
     * @return 是否命中成功
     */
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

    /**
     * 对目标和自身施加击退效果
     * 先保存目标原始速度，避免计算互相干扰
     *
     * @param target 被击退的目标
     */
    private void applyKnockback(Entity target) {
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

    // ==================== 死亡与掉落 ====================

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
     * 处理玩家击杀奴仆的逻辑
     * 给予解密进度并掉落物品
     *
     * @param player 击杀的玩家
     */
    private void handlePlayerKill(Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            giveDecryptionProgress(player, requiemCard);
            dropLoot(player);
        });
    }

    /**
     * 给予安魂密语解密进度
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片能力
     */
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

    /**
     * 掉落战利品（赤毒、裂罅碎片、安魂宝石、模组）
     *
     * @param player 击杀的玩家
     */
    private void dropLoot(Player player) {
        BlockPos pos = this.blockPosition();

        dropModule(pos);

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.slaveKuvaDropChance.get())) {
            spawnItem(pos, new ItemStack(
                    KuvaLichItems.KUVA.get(),
                    RandomUtil.getInt(
                            ModConfig.KUVA_LICH.slaveKuvaMinAmount.get(),
                            ModConfig.KUVA_LICH.slaveKuvaMaxAmount.get()
                    )
            ));
        }

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.slaveRivenSliverDropChance.get())) {
            spawnItem(pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 1));
        }

        int lootingLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MOB_LOOTING, player.getMainHandItem());
        double gemChance = ModConfig.KUVA_LICH.slaveRequiemGemBaseChance.get()
                + ModConfig.KUVA_LICH.slaveRequiemGemLootingBonus.get() * lootingLevel;
        if (RandomUtil.percentageChance(gemChance)) {
            spawnItem(pos, new ItemStack(KuvaLichItems.REQUIEM_GEM.get(), 1));
        }
    }

    /**
     * 掉落模组，按品质概率分层
     * 统一使用RandomUtil
     *
     * @param pos 掉落位置
     */
    private void dropModule(BlockPos pos) {
        int commonChance = ModConfig.KUVA_LICH.commonModuleDropChance.get();
        int uncommonChance = ModConfig.KUVA_LICH.uncommonModuleDropChance.get();
        int rareChance = ModConfig.KUVA_LICH.rareModuleDropChance.get();
        int weaponRatio = ModConfig.KUVA_LICH.moduleWeaponRatio.get();

        int roll = RandomUtil.getInt(0, 99);

        if (roll < commonChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < commonChance + uncommonChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < commonChance + uncommonChance + rareChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
            spawnItem(pos, module);
        }
    }

    /**
     * 在指定位置生成掉落物实体
     *
     * @param pos 位置
     * @param itemStack 物品
     */
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