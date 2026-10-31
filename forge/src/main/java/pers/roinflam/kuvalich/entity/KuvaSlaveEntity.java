package pers.roinflam.kuvalich.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.KuvaStateServer;
import pers.roinflam.kuvalich.mercy.MercyRules;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.item.module.weapon.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;

/**
 * 赤毒奴仆实体
 * 普通小怪，拥有25%固定伤害减免
 *
 * Kuva Slave Entity
 * Regular minion with flat 25% damage reduction
 */
public class KuvaSlaveEntity extends AbstractKuva {

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

    // ==================== 怜悯之击 ====================

    /** 倒地后碰撞箱高度的倍率（2.9 → 1.885 格） */
    private static final float DOWNED_HEIGHT_SCALE = 0.65F;
    /** 死亡效果包的范围（格） */
    private static final double DEATH_FX_RANGE = 32.0D;
    /** 死亡包 b：这次给了密语进度 */
    private static final int DEATH_GAVE_PROGRESS = 1;
    /** 死亡包 b：处决走完的那次死亡 */
    private static final int DEATH_AFTER_MERCY = 1 << 1;

    public KuvaSlaveEntity(EntityType<? extends Monster> entityType, Level level) {
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
        // 挥砍声、命中声都由客户端按挥臂和实体事件 103 放，服务端不再直接放
        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float finalDamage = attackDamage * this.level().getDifficulty().getId() * ATTACK_DAMAGE_MULTIPLIER;

        if (target.hurt(this.damageSources().mobAttack(this), finalDamage)) {
            applyKnockback(target);
            float before = this.getHealth();
            this.heal(attackDamage * HEAL_PER_ATTACK);
            this.level().broadcastEntityEvent(this, EV_HIT);
            KuvaStateServer.onStrike(this, target, this.getHealth() - before);
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

    /**
     * 确实死了（服务端，原版死亡流程已经走完、没被别的模组取消）。
     *
     * <p>怜悯之击开着时只有处决走完的那次死亡给东西；{@code /kill}、虚空、摔死、被别的怪打死这类绕过倒地的死亡什么都不给。
     * 关着时照旧：被玩家打死就给。演示用的奴仆一律不给。</p>
     *
     * <p>先发死亡包、再给进度和掉落：客户端的密语光点和解密 HUD 的进度音靠这个先后对齐。</p>
     */
    @Override
    protected void onKilled(DamageSource source) {
        boolean reward = (isMercyFinishing() || !MercyRules.enabledFor(this)) && !isDemo();
        Player player = source.getEntity() instanceof Player p ? p : null;
        boolean gaveProgress = reward && player != null
                && player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).isPresent();
        int b = (gaveProgress ? DEATH_GAVE_PROGRESS : 0) | (isMercyFinishing() ? DEATH_AFTER_MERCY : 0);
        sendThrallDeath(player, b);
        markDeathFxSent();
        if (!reward || player == null) {
            return;
        }
        // 先记处决的怒气、再给进度：这一下要是揭开了第一条密语，玄骸是在这之后才诞生的，
        // 不该把这只奴仆的处决算到刚出生的玄骸头上（出生时的怒气照配置的初值）
        if (player instanceof ServerPlayer serverPlayer) {
            LichDirector.onThrallExecuted(serverPlayer, this);
        }
        handlePlayerKill(player);
    }

    /** 没有别的路发过死亡包时的默认那个（什么都没给） */
    @Override
    protected void sendDeathFx(DamageSource source) {
        sendThrallDeath(source.getEntity() instanceof Player p ? p : null, 0);
    }

    private void sendThrallDeath(@Nullable Player killer, int b) {
        if (level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chest(), DEATH_FX_RANGE, FxId.THRALL_DEATH, this,
                    killer == null ? 0 : killer.getId() + 1, b);
        }
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

        // 记录加成前的阶段，用于判断本次是否揭示了新线索
        int levelBefore = requiemCard.getUnlockedCardStatus();

        if (requiemCard.addPotion(addPotion)) {
            // 同步顶部解密HUD（带过渡动画；阶段推进时触发揭示提示）
            DecryptionHudPacket.sendProgress(player, requiemCard, addPotion, levelBefore);
            // 和玄骸那份一样紧跟着通知导演：没有玄骸时揭开第一条密语就在这里诞生，有玄骸时进度和揭示加怒气
            if (player instanceof ServerPlayer serverPlayer) {
                LichDirector.onDecryptionProgress(serverPlayer, requiemCard, addPotion, levelBefore);
            }

            boolean max = requiemCard.getPointsRequired() == -1;
            // 走动作栏而不是聊天框：刷奴仆时这条每杀一只发一次，
            // 而顶部的解密 HUD 已经在显示同一件事，进聊天历史纯属刷屏。
            PlayerFeedback.actionBar(player, KuvaPalette.PROGRESS,
                    "message.kuvalich.getPoints",
                    addPotion,
                    max ? "Max" : requiemCard.getDecryptionProgress(),
                    max ? "Max" : requiemCard.getPointsRequired());
        } else {
            // 已满级（线索全部揭示）：进度未变化，但仍补发一个 added=0 的进度包，
            // 使顶部HUD在满级后继续击杀时也能弹出（显示满进度条与提示，不触发闪光）
            DecryptionHudPacket.sendProgress(player, requiemCard, 0, levelBefore);
            if (player instanceof ServerPlayer serverPlayer) {
                LichDirector.onDecryptionProgress(serverPlayer, requiemCard, 0, levelBefore);
            }
            PlayerFeedback.actionBar(player, KuvaPalette.ACCENT, "message.kuvalich.maxLevel");
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
                    ? WeaponCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < commonChance + uncommonChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? WeaponUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
            spawnItem(pos, module);
        } else if (roll < commonChance + uncommonChance + rareChance) {
            ItemStack module = RandomUtil.percentageChance(weaponRatio)
                    ? WeaponRareModule.getRandomModule()
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

    // ==================== 怜悯之击：碰撞箱、自然消失、声音 ====================

    @Override
    protected float downedHeightScale() {
        return DOWNED_HEIGHT_SCALE;
    }

    /** 原版自然消失被截下来时：原地慢慢散掉（和倒地超时一样），什么都不给 */
    @Override
    protected boolean startNaturalLeave() {
        beginExpire();
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return KuvaLichSounds.ENTITY_THRALL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return KuvaLichSounds.ENTITY_THRALL_HURT.get();
    }

    @Override
    public @NotNull EntityType<?> getType() {
        return KuvaLichEntities.KUVA_SLAVE.get();
    }
}
