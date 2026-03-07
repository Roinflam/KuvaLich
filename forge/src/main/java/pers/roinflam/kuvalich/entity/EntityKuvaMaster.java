// EntityKuvaMaster.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/entity/EntityKuvaMaster.java
package pers.roinflam.kuvalich.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

import java.util.*;

/**
 * 赤毒玄骸实体
 * Boss级敌人，拥有三层动态抗性机制：
 * 1. 适应性抗性 - 连续受同类伤害逐渐适应，超时衰减/全局重置
 * 2. 连击伤害递减 - 短时间内高频攻击递减（环形缓冲区实现，零GC）
 * 3. 单次伤害上限 - 对数软上限防止秒杀
 *
 * Kuva Master Entity
 * Boss-level enemy with three-layer dynamic resistance:
 * 1. Adaptive resistance - adapts to repeated same-type damage, decays/resets over time
 * 2. Burst diminishing - ring buffer implementation, zero GC
 * 3. Soft damage cap - logarithmic cap prevents one-shots
 */
public class EntityKuvaMaster extends KuvaBase {

    // ==================== 回血参数（已砍半） ====================

    /** 无目标时基础回血倍率（基于缺失血量） */
    private static final float BASE_HEAL_MULTIPLIER = 0.01f;
    /** 有目标时基础回血倍率（基于缺失血量） */
    private static final float TARGET_HEAL_MULTIPLIER = 0.02f;
    /** 无目标时最低回血量 */
    private static final float MIN_HEAL_NO_TARGET = 1.0f;
    /** 有目标时最低回血量 */
    private static final float MIN_HEAL_HAS_TARGET = 2.0f;

    // ==================== 攻击参数 ====================

    /** 攻击伤害系数（受难度影响） */
    private static final float ATTACK_DAMAGE_MULTIPLIER = 0.85f;
    /** 击退目标的水平倍率 */
    private static final float KNOCKBACK_MULTIPLIER = 3.15f;
    /** 击退目标的垂直倍率 */
    private static final float VERTICAL_KNOCKBACK = 2.15f;
    /** 自身受到的击退倍率 */
    private static final float SELF_KNOCKBACK_MULTIPLIER = 1.15f;
    /** 每次攻击命中的自愈比例 */
    private static final float HEAL_PER_ATTACK = 0.2f;
    /** AOE范围攻击半径 */
    private static final float AOE_RADIUS = 3.0f;

    // ==================== 适应性抗性参数 ====================

    /** 每种伤害类别的最大适应层数 */
    private static final int MAX_ADAPTIVE_STACKS = 6;
    /** 每层提供的伤害减免比例（5%每层，满层30%） */
    private static final float RESISTANCE_PER_STACK = 0.05f;
    /** 某类伤害无命中后单层衰减间隔（80tick = 4秒） */
    private static final int STACK_DECAY_TICKS = 80;
    /** 完全无受击后全部层数重置的时间（200tick = 10秒） */
    private static final int FULL_RESET_TICKS = 200;

    // ==================== 连击递减参数 ====================

    /** 连击统计的时间窗口（40tick = 2秒） */
    private static final int HIT_WINDOW_TICKS = 40;
    /** 窗口内每多一次命中的额外减伤比例（5%） */
    private static final float DIMINISH_PER_HIT = 0.05f;
    /** 连击递减的最低伤害倍率（不低于40%） */
    private static final float MIN_DAMAGE_MULTIPLIER = 0.4f;
    /** 连击记录环形缓冲区容量（2秒窗口内不可能超过此数） */
    private static final int HIT_BUFFER_SIZE = 32;

    // ==================== 单次伤害上限参数 ====================

    /** 单次伤害不超过最大生命值的此比例（15%） */
    private static final float MAX_DAMAGE_PERCENT = 0.15f;
    /** 超出阈值部分的对数衰减系数，越大衰减越慢 */
    private static final float SOFT_CAP_SCALE = 0.5f;

    // ==================== 死亡语音 ====================

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

    // ==================== 抗性运行时数据 ====================

    /**
     * 适应性抗性层数记录
     * 每种伤害类别独立追踪层数和最后命中时间
     */
    private static class AdaptiveStack {
        /** 当前适应层数 */
        int stacks = 0;
        /** 最后一次受到该类伤害的gameTick */
        long lastHitTick = 0;
    }

    /** 各伤害类别的适应性层数（构造时预初始化，避免运行时创建对象） */
    private final EnumMap<DamageCategory, AdaptiveStack> adaptiveResistance = new EnumMap<>(DamageCategory.class);

    /** 连击记录环形缓冲区（固定长度原始数组，零GC零装箱） */
    private final long[] hitBuffer = new long[HIT_BUFFER_SIZE];
    /** 环形缓冲区写入位置 */
    private int hitBufferIndex = 0;
    /** 缓冲区中有效记录数 */
    private int hitBufferCount = 0;

    /** 最后一次受到任何伤害的gameTick（用于全局重置判断） */
    private long lastAnyHitTick = 0;

    public EntityKuvaMaster(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // 预初始化所有伤害类别的适应层数，避免运行时computeIfAbsent创建对象
        for (DamageCategory category : DamageCategory.values()) {
            adaptiveResistance.put(category, new AdaptiveStack());
        }
    }

    /**
     * 创建玄骸属性
     *
     * @return 属性构建器
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ATTACK_DAMAGE, 13.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ARMOR, 8.0);
    }

    // ==================== 三层动态抗性 ====================

    /**
     * 玄骸的高级抗性处理
     * 按顺序执行三层过滤：适应性抗性 → 连击递减 → 单次伤害上限
     *
     * @param source 伤害来源
     * @param damage 经过基础类型修正后的伤害值
     * @return 最终伤害值
     */
    @Override
    protected float applyAdvancedResistance(DamageSource source, float damage) {
        long currentTick = this.level().getGameTime();
        DamageCategory category = classifyDamage(source);

        // 第一层：适应性抗性（连续同类伤害逐渐减免）
        damage = applyAdaptiveResistance(category, currentTick, damage);

        // 第二层：连击递减（短时间内高频攻击递减）
        damage = applyBurstDiminish(currentTick, damage);

        // 第三层：单次伤害上限（对数软上限防秒杀）
        damage = applySoftDamageCap(damage);

        // 记录本次受击时间（用于全局重置判断）
        lastAnyHitTick = currentTick;

        return damage;
    }

    /**
     * 第一层：适应性抗性
     * 先用当前层数计算减伤，再叠一层
     * 第一击无减伤（0层），后续同类伤害逐渐增强抗性
     * 满6层时该类伤害减免30%
     *
     * @param category 伤害类别
     * @param currentTick 当前gameTick
     * @param damage 伤害值
     * @return 减伤后的伤害值
     */
    private float applyAdaptiveResistance(DamageCategory category, long currentTick, float damage) {
        // 构造时已预初始化，直接get不会为null
        AdaptiveStack stack = adaptiveResistance.get(category);

        // 先用当前层数计算减伤（第一击层数为0，不减伤）
        float resistance = stack.stacks * RESISTANCE_PER_STACK;
        damage *= (1.0f - resistance);

        // 再叠一层（为下次同类伤害准备）
        if (stack.stacks < MAX_ADAPTIVE_STACKS) {
            stack.stacks++;
        }
        stack.lastHitTick = currentTick;

        return damage;
    }

    /**
     * 第二层：连击伤害递减
     * 使用环形缓冲区实现滑动窗口，零GC零装箱
     * 利用缓冲区按时间单调递增的特性，遇到过期记录即停止遍历
     * 2秒内打12次以上触底到40%
     *
     * @param currentTick 当前gameTick
     * @param damage 伤害值
     * @return 递减后的伤害值
     */
    private float applyBurstDiminish(long currentTick, float damage) {
        // 统计窗口内命中次数（从最新记录向前遍历，遇到过期即停）
        int hits = 0;
        for (int i = 0; i < hitBufferCount; i++) {
            int idx = (hitBufferIndex - 1 - i + HIT_BUFFER_SIZE) % HIT_BUFFER_SIZE;
            if (currentTick - hitBuffer[idx] <= HIT_WINDOW_TICKS) {
                hits++;
            } else {
                // 环形缓冲区按时间递增，遇到过期的后面都过期
                break;
            }
        }

        // 计算减伤倍率
        float multiplier = Math.max(MIN_DAMAGE_MULTIPLIER, 1.0f - hits * DIMINISH_PER_HIT);
        damage *= multiplier;

        // 写入本次命中时间
        hitBuffer[hitBufferIndex] = currentTick;
        hitBufferIndex = (hitBufferIndex + 1) % HIT_BUFFER_SIZE;
        if (hitBufferCount < HIT_BUFFER_SIZE) {
            hitBufferCount++;
        }

        return damage;
    }

    /**
     * 第三层：单次伤害软上限
     * 低伤害原样通过，超出阈值（最大血量的15%=30点）部分按对数衰减
     * 公式：threshold + ln(1 + excess * scale) / scale
     * 举例：200伤害 → 约39伤害
     *
     * @param damage 伤害值
     * @return 限制后的伤害值
     */
    private float applySoftDamageCap(float damage) {
        float threshold = this.getMaxHealth() * MAX_DAMAGE_PERCENT;
        if (damage <= threshold) {
            return damage;
        }

        float excess = damage - threshold;
        float softened = (float) (Math.log(1.0 + excess * SOFT_CAP_SCALE) / SOFT_CAP_SCALE);
        return threshold + softened;
    }

    /**
     * 定期处理适应性抗性的时效衰减逻辑（由基类每秒调用一次）
     * - 10秒完全无受击 → 所有层数清零（全局重置）
     * - 单类4秒无命中 → 该类逐层衰减（每4秒掉1层）
     */
    @Override
    protected void tickResistance() {
        long currentTick = this.level().getGameTime();

        // 全局重置：10秒未受到任何伤害，清空所有适应层数
        if (lastAnyHitTick > 0 && currentTick - lastAnyHitTick > FULL_RESET_TICKS) {
            for (AdaptiveStack stack : adaptiveResistance.values()) {
                stack.stacks = 0;
            }
            return;
        }

        // 单类衰减：某类伤害4秒内无命中，每4秒衰减1层
        for (AdaptiveStack stack : adaptiveResistance.values()) {
            if (stack.stacks > 0 && currentTick - stack.lastHitTick > STACK_DECAY_TICKS) {
                stack.stacks--;
                // 重置该类的计时器，下一层衰减需要再等4秒
                stack.lastHitTick = currentTick;
            }
        }
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
     * 对目标造成伤害，附带AOE范围攻击、击退和自愈
     *
     * @param target 主要攻击目标
     * @return 是否命中成功
     */
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

    /**
     * AOE范围攻击，对主目标以外的周围实体造成伤害
     * 排除其他赤毒实体和主目标
     *
     * @param damage 伤害值
     * @param excludeEntity 排除的实体（主目标）
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
     * 处理玩家击杀玄骸的逻辑
     * 根据安魂卡片状态决定成功解密/失败/给予进度
     *
     * @param player 击杀的玩家
     */
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

    /**
     * 安魂密语解密成功时的处理
     * 掉落武器、归还没收物品、掉落高级模组等
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
     * @param pos 掉落位置
     */
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

    /**
     * 归还玄骸没收的物品
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
     * @param pos 掉落位置
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

    /**
     * 发送玄骸死亡台词
     *
     * @param player 玩家
     */
    private void sendDeathMessage(Player player) {
        String messageKey = DEATH_MESSAGES[RandomUtil.getInt(0, DEATH_MESSAGES.length - 1)];
        player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED));
    }

    /**
     * 掉落高级模组（Prime或裂罅）
     * 统一使用RandomUtil
     *
     * @param pos 掉落位置
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

    /**
     * 提升武器等级上限
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
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
     * 安魂密语解密失败时的处理
     * 提示已猜对的位数，玄骸等级+1
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
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
     * 给予安魂密语解密进度（玄骸给的进度比奴仆多）
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
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

    // ==================== 工具方法 ====================

    private void sendMessage(Player player, String key, Object... args) {
        sendMessage(player, key, ChatFormatting.GREEN, args);
    }

    private void sendMessage(Player player, String key, ChatFormatting color, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args).withStyle(color));
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

    // ==================== NBT存档 ====================

    /**
     * 保存适应性抗性层数到NBT
     * 连击缓冲区和单次上限是纯运行时数据不需要存盘
     *
     * @param compound NBT标签
     */
    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        // 保存适应性抗性层数
        CompoundTag resistanceTag = new CompoundTag();
        for (Map.Entry<DamageCategory, AdaptiveStack> entry : adaptiveResistance.entrySet()) {
            CompoundTag stackTag = new CompoundTag();
            stackTag.putInt("Stacks", entry.getValue().stacks);
            stackTag.putLong("LastHit", entry.getValue().lastHitTick);
            resistanceTag.put(entry.getKey().name(), stackTag);
        }
        compound.put("AdaptiveResistance", resistanceTag);
        compound.putLong("LastAnyHitTick", lastAnyHitTick);
    }

    /**
     * 从NBT读取适应性抗性层数
     *
     * @param compound NBT标签
     */
    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("AdaptiveResistance")) {
            CompoundTag resistanceTag = compound.getCompound("AdaptiveResistance");
            for (DamageCategory category : DamageCategory.values()) {
                if (resistanceTag.contains(category.name())) {
                    CompoundTag stackTag = resistanceTag.getCompound(category.name());
                    AdaptiveStack stack = adaptiveResistance.get(category);
                    stack.stacks = stackTag.getInt("Stacks");
                    stack.lastHitTick = stackTag.getLong("LastHit");
                }
            }
        }
        lastAnyHitTick = compound.getLong("LastAnyHitTick");
    }

    @Override
    public @NotNull EntityType<?> getType() {
        return KuvaLichEntities.KUVA_MASTER.get();
    }
}