package pers.roinflam.kuvalich.base.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.script.KuvaAnimFx;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.client.mercy.KuvaStateFx;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.nemesis.LichTraits;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.KuvaStateServer;
import pers.roinflam.kuvalich.mercy.MercyAnimSelector;
import pers.roinflam.kuvalich.mercy.MercyRules;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.module.weapon.MagicDamageClassifier;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.worldgen.KuvaSpawnLimiter;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 赤毒玄骸基类（1.20.1版本）
 * 包含基础伤害类型修正和抗性框架，子类通过覆写实现具体抗性逻辑
 *
 * Kuva Lich Base Class (1.20.1)
 * Contains base damage type modifiers and resistance framework,
 * subclasses override to implement specific resistance logic
 */
@Mod.EventBusSubscriber
public abstract class AbstractKuva extends Monster implements GeoEntity {

    // ==================== 基础伤害类型倍率 ====================

    /** 弓箭伤害倍率（弱点） */
    private static final float ARROW_DAMAGE_MULTIPLIER = 1.25f;
    /** 其他弹射物伤害倍率（抗性） */
    private static final float PROJECTILE_DAMAGE_MULTIPLIER = 0.75f;
    /** 魔法伤害倍率（抗性） */
    private static final float MAGIC_DAMAGE_MULTIPLIER = 0.75f;

    /** 抗性衰减检查间隔（20tick = 1秒，无需每tick检查） */
    private static final int RESISTANCE_CHECK_INTERVAL = 20;

    /** 处决时的胸口在碰撞箱多高处（比例） */
    protected static final double MERCY_CHEST_FRAC = 0.62D;

    // ==================== 同步数据 ====================

    private static final EntityDataAccessor<Boolean> HAS_TARGET =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.BOOLEAN);

    /** 怜悯之击状态，{@link MercyState} 的序号 */
    private static final EntityDataAccessor<Byte> DATA_MERCY_STATE =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.BYTE);
    /** 倒地时是资格人的实体 id，处决时是处决者的实体 id，-1 = 没有 */
    private static final EntityDataAccessor<Integer> DATA_MERCY_ACTOR =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.INT);
    /**
     * bit 0~15：倒地时的剩余 tick（处决时冻结），消散时的剩余 tick；
     * bit 16~23：可处决距离 × 10；bit 24~30：倒地总时长 ÷ 5 秒（画倒计时圈用）。
     * 只在状态切换和每 20 tick 写一次，别每 tick 写
     */
    private static final EntityDataAccessor<Integer> DATA_MERCY_TIMER =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.INT);
    /** 处决时是开始处决那个效果包的计划位（见 MercyPlan），给晚进视野的客户端选姿势用 */
    private static final EntityDataAccessor<Integer> DATA_MERCY_PLAN =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.INT);
    /** 终结的选择窗口：0 没有 / 1 等待中 / 2 消灭 / 3 感化。放同步数据而不是效果包，晚进视野的人也拿得到 */
    private static final EntityDataAccessor<Byte> DATA_MERCY_CHOICE =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.BYTE);
    /** 适应性抗性层数：5 类伤害 × 每类 3 位，按 {@link DamageCategory} 的序号 × 3 排 */
    private static final EntityDataAccessor<Integer> ADAPT_PACKED =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.INT);
    /** 交战增幅千分比 = 交战秒数 × 每秒增幅 × 1000 */
    private static final EntityDataAccessor<Integer> FURY_PERMILLE =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.INT);
    /** 起势：下一刀快出手了，客户端摆蓄力姿势（{@link KuvaMeleeGoal} 写） */
    private static final EntityDataAccessor<Boolean> STRIKE_READY =
            SynchedEntityData.defineId(AbstractKuva.class, EntityDataSerializers.BOOLEAN);

    // ==================== 动画 ====================

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /** 走、跑、待机和起势 / 出手怎么挑（客户端状态都在里面） */
    private final KuvaBodyAnim bodyAnim = new KuvaBodyAnim(this);

    // ==================== 音画事件 ====================

    /** 实体事件：登场警示（第一次盯上 64 格内的玩家，每只一辈子只发一次） */
    public static final byte EV_ALERT = 96;
    /** 原版的死亡白烟（死亡第 20 tick），客户端拦下来换成赤毒的小消散 */
    public static final byte EV_DEATH_POOF = 60;
    /** 实体事件：近战打中了（客户端把命中声排到挥臂起点之后） */
    public static final byte EV_HIT = 103;

    /** 登场警示离玩家多远才发 */
    private static final double ALERT_RANGE = 64.0D;

    /**
     * 已经宣告过登场（发过登场警示，或者召唤、到场演出已经交代过它的出现）。
     * 存盘：只放内存的话，区块卸载重载、重进服务器后同一只会再演一遍到场
     */
    private boolean arrivalShown;

    /** 已经发过死亡效果包（安魂成功 / 失败那一路发过，就不再发普通的死亡效果） */
    protected boolean deathFxSent;

    // ==================== 怜悯之击：服务端状态 ====================

    /** 死亡后默认留多少 tick 再移除（和原版一样） */
    public static final int DEFAULT_DEATH_HOLD_TICKS = 20;
    /** 自然消失时，多远以内有玩家才演一遍离场（格） */
    private static final double NATURAL_LEAVE_RANGE = 64.0D;
    /** 奴仆消散留多少 tick 再移除：1.2 秒的消散演出 + 300 毫秒余量 */
    public static final int EXPIRE_TICKS = 30;
    /** 有主玄骸自然消失时交给玄骸导演离场，这么多 tick 后还在就照原样移除 */
    private static final int OWNED_LEAVE_FALLBACK_TICKS = 40;

    /** 倒地剩余 tick（处决中冻结）。服务端 */
    private int mercyRemaining;
    /** 资格人的 UUID，重登后实体 id 会变，所以按 UUID 记、定期重新解析。服务端 */
    @Nullable
    private UUID mercyQualified;
    /** 处决走完的那次死亡：怜悯之击开着时只有它给奖励。服务端 */
    private boolean mercyFinishing;
    /** 正在结算 /kill 命令或真掉出世界的那一下（见 {@link #isHardDeath}）：倒地保护和血量下限这时都让开。服务端 */
    private boolean hardDeathBlow;
    /** 死亡后至少留多少 tick（给离场演出），取各方要求的最大值 */
    private int deathHoldTicks = DEFAULT_DEATH_HOLD_TICKS;
    /** 只在 checkDespawn 执行的那一刻为真，用来认出原版自然消失发出的移除 */
    private boolean inDespawnCheck;
    /** ≥ 0：消散 / 自然离开的演出中，到 0 移除。服务端 */
    private int leaveTicks = -1;
    /** 演示目标被判错过几次（升级横幅的等级 = 1 + 这个数）。只在内存里 */
    private int demoFailures;
    /** 起身剩余 tick（{@link MercyState#RISING}），到 0 回到能打的状态。服务端 */
    private int riseTicks;
    /** 交战增幅的临时值（演示命令用），{@link #furyOverrideTicks} 到 0 后恢复按交战时长算 */
    private int furyOverride;
    private int furyOverrideTicks;
    /** 状态特效的限流记录（只在服务端用） */
    public final KuvaStateServer.Track stateTrack = new KuvaStateServer.Track();

    /** 客户端：上一 tick 的血量，-1 = 还没记；回血检测用 */
    private float lastClientHealth = -1.0F;
    /** 客户端：上一次报回血的 tickCount */
    private int lastRegenTick = -1000;

    // ==================== 客户端状态与钩子 ====================

    /**
     * 客户端的演出钟（毫秒）。实体类两端都加载，不能直接引用客户端类，所以留成静态钩子，
     * 默认恒为 0，客户端启动完成后换成真正的演出钟
     */
    public static LongSupplier CLIENT_CLOCK = () -> 0L;

    /** 客户端字段：说话动画播到哪个演出钟时刻为止，由通讯窗的包处理写 */
    public long talkUntilMs;

    /** 客户端字段：这一句是喊出来的（台词以感叹号结尾），开口时先播一下 talk_shout 再循环 talk。和 talkUntilMs 一起写 */
    public boolean talkShout;

    /** 客户端字段：talk 控制器这一句已经起过头了，不说话时清掉 */
    private boolean talkStarted;

    /** 客户端字段：收到过几次挥臂包，出手那层动画按它变了没有来起播 */
    int swingSeq;

    /** 客户端字段：进入倒地时动画计时器的时刻，没有为 NaN；选倒地进入片段时记 */
    public double downedEnterTick = Double.NaN;
    /** 主控制器眼下在播的怜悯之击片段，和它开播时处决的真实钟（毫秒）；不在播是 null / −1。客户端顿帧算速度用 */
    @Nullable
    public RawAnimation mercyClip;
    public long mercyClipRaw = -1L;

    /** 客户端字段：倒地剩余时间那份同步数据最近一次到达时的 {@link #tickCount} */
    private int mercyTimerSyncTick;

    /** 演示用的玄骸：只在内存里，不跟区块存盘，重进世界后不会变成一只无主的真玄骸 */
    private boolean demo;

    // ==================== 战斗数据 ====================

    /** 战斗持续tick数，影响伤害加成 */
    protected int battleTick = 0;

    /**
     * 伤害类别枚举，用于适应性抗性的伤害来源分类
     * 判定优先级：弓箭 > 弹射物 > 魔法 > 近战 > 其他
     *
     * Damage category enum for adaptive resistance classification
     * Priority: ARROW > PROJECTILE > MAGIC > MELEE > OTHER
     */
    public enum DamageCategory {
        /** 近战攻击 */
        MELEE,
        /** 弓箭攻击 */
        ARROW,
        /** 其他弹射物攻击 */
        PROJECTILE,
        /** 魔法攻击 */
        MAGIC,
        /** 其他类型攻击 */
        OTHER
    }

    public AbstractKuva(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    // ==================== 伤害事件处理 ====================

    /**
     * 全局伤害事件监听器
     * 处理赤毒实体的攻击加成和防御减伤
     *
     * @param event 生物受伤事件
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = event.getSource();
        if (damageSource == null) {
            return;
        }

        LivingEntity target = event.getEntity();

        // 赤毒实体造成伤害时的加成；打的是玩家时再吃玄骸等级的受伤增幅
        // 2026-09-25：玄骸等级的两项惩罚（受伤增幅、输出削减）原先对玩家受到 / 造成的<b>所有</b>伤害生效
        //    （掉落、摔伤、打僵尸都算），和配置说明「玄骸对你」「你对玄骸」不符，现只在对手是赤毒实体时生效
        if (damageSource.getEntity() instanceof AbstractKuva kuvaBase) {
            applyKuvaBaseDamageBonus(event, kuvaBase);
            if (target instanceof Player player) {
                applyPlayerDefense(event, player);
            }
        } else if (damageSource.getEntity() instanceof Player player && target instanceof AbstractKuva) {
            // 爆炸半径溅射以主目标最终伤害为基数：主目标也是赤毒实体时，这项削减在主目标那一下已经乘过，
            // 溅射到另一个赤毒实体时再乘就成了 (1−r)²。主目标是普通怪时基数里没乘过，照常乘一次。
            // （不能见溅射就跳过：那样打玄骸旁边的普通怪，溅射就能绕开玄骸等级削减满额打玄骸）
            if (!(WeaponCombatHandler.isApplyingSplash()
                    && WeaponCombatHandler.splashMainTarget() instanceof AbstractKuva)) {
                applyPlayerDamageModifier(event, player);
            }
        }

        // 赤毒实体受到伤害时的减伤（委托给实例方法）
        if (target instanceof AbstractKuva kuva) {
            event.setAmount(kuva.applyResistance(damageSource, event.getAmount()));
        }
    }

    /**
     * 赤毒实体攻击加成：随战斗时间增长
     *
     * @param event 受伤事件
     * @param kuvaBase 攻击方赤毒实体
     */
    private static void applyKuvaBaseDamageBonus(LivingHurtEvent event, AbstractKuva kuvaBase) {
        float multiplier = (float) (1.0D + kuvaBase.getBattleTick() / 20.0D * kuvaBase.furyPerSecond());
        event.setAmount(event.getAmount() * multiplier);
    }

    /** 交战中每秒的增幅（配置值 × 这只自己的增幅档位） */
    public double furyPerSecond() {
        LichTraits traits = combatTraits();
        return ModConfig.KUVA_LICH.battleBoost.get() * (traits != null ? traits.furyScale() : 1.0D);
    }

    /**
     * 玩家对赤毒实体的伤害修正：受玄骸等级影响（只在目标是赤毒实体时调用）
     *
     * @param event 受伤事件
     * @param player 攻击方玩家
     */
    private static void applyPlayerDamageModifier(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            float reduction = (float) Math.min(0.999f, requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.reducedDamage.get());
            float damage = event.getAmount() * (1.0f - reduction);

            event.setAmount(damage);
        });
    }

    /**
     * 玩家受到赤毒实体攻击时的伤害增幅（只在伤害归属于赤毒实体时调用，含它的弹射物）
     *
     * @param event 受伤事件
     * @param player 受击方玩家
     */
    private static void applyPlayerDefense(LivingHurtEvent event, Player player) {
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            float multiplier = (float) (1.0f + requiemCard.getKuvaLevel() * ModConfig.KUVA_LICH.increaseDamage.get());
            event.setAmount(event.getAmount() * multiplier);
        });
    }

    // ==================== 抗性框架 ====================

    /**
     * 根据伤害来源判断伤害类别
     * 判定优先级：弓箭 > 弹射物 > 魔法 > 近战 > 其他
     *
     * <p>魔法判定改用 {@link MagicDamageClassifier#isMagic}：
     * 原来只认 {@code witch_resistant_to} tag，Ars Nouveau / Goety / SweetMagic 的大部分法术
     * 在这里都会落到「近战」或「其他」；现在与 {@code magicDamage} 词条用同一套规则，
     * 召唤物替玩家挥砍的命中仍按近战记层。</p>
     *
     * @param source 伤害来源
     * @return 伤害类别
     */
    public static DamageCategory classifyDamage(DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) {
            return DamageCategory.ARROW;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return DamageCategory.PROJECTILE;
        }
        if (MagicDamageClassifier.isMagic(source)) {
            return DamageCategory.MAGIC;
        }
        if (source.getDirectEntity() instanceof LivingEntity) {
            return DamageCategory.MELEE;
        }
        return DamageCategory.OTHER;
    }

    /**
     * 抗性计算入口
     * 执行顺序：基础伤害类型修正 → 高级抗性（子类实现）。/kill、虚空这类绕过无敌的不吃抗性：
     * 单次伤害软上限会把它压成几十点，玄骸有护盾、倒地压在段底时就打不死了
     *
     * @param source 伤害来源
     * @param damage 原始伤害值
     * @return 最终伤害值
     */
    public float applyResistance(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return damage;
        }
        // 第一步：基础伤害类型修正（弓箭增伤、弹射物/魔法减伤）
        damage = applyBaseDamageTypeModifier(source, damage);
        // 第二步：高级抗性（子类覆写实现）
        damage = applyAdvancedResistance(source, damage);
        return damage;
    }

    /**
     * 基础伤害类型修正，所有赤毒实体共有
     * 弓箭增伤125%，其他弹射物减伤75%，魔法减伤75%
     *
     * <p>魔法判定与 {@link #classifyDamage} 保持一致，改用 {@link MagicDamageClassifier#isMagic}。
     * 这意味着第三方模组的法术现在也会吃到魔法减伤。</p>
     *
     * @param source 伤害来源
     * @param damage 伤害值
     * @return 修正后伤害值
     */
    protected float applyBaseDamageTypeModifier(DamageSource source, float damage) {
        float multiplier = damageTypeMultiplier(source);
        // 弱点裂纹、抗性偏转闪按这一下实际吃到的倍率发，客户端不再自己按伤害类型猜
        KuvaStateServer.onAffinityHit(this, source, multiplier);
        return damage * multiplier;
    }

    /**
     * 这一下按伤害类型吃到的倍率：大于 1 是打中了弱点，小于 1 是被抗性削了，等于 1 是都不沾。
     * 固定倍率是弓箭 ×1.25、其他弹射物 ×0.75、魔法（弹射物以外的）×0.75；
     * 按种子抽过面板的专属玄骸（{@link #combatTraits}）按它自己那四类的倍率算，「其他」类不受影响
     */
    public float damageTypeMultiplier(DamageSource source) {
        LichTraits traits = combatTraits();
        if (traits != null && traits.varied) {
            return (float) traits.affinity(classifyDamage(source).ordinal());
        }
        float multiplier = 1.0F;
        if (source.getDirectEntity() instanceof AbstractArrow) {
            multiplier *= ARROW_DAMAGE_MULTIPLIER;
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            multiplier *= PROJECTILE_DAMAGE_MULTIPLIER;
        }
        if (MagicDamageClassifier.isMagic(source) && !source.is(DamageTypeTags.IS_PROJECTILE)) {
            multiplier *= MAGIC_DAMAGE_MULTIPLIER;
        }
        return multiplier;
    }

    /**
     * 专属玄骸按种子、等级算出来的战斗面板；奴仆、无主玄骸、客户端上都没有，返回 null（照固定数值）
     */
    @Nullable
    public LichTraits combatTraits() {
        return null;
    }

    /** 每类适应至多叠几层（特效判「叠满」用） */
    public int adaptStackCap() {
        LichTraits traits = combatTraits();
        return traits != null ? traits.adaptStacks() : LichTraits.ADAPT_STACKS[LichTraits.NORMAL];
    }

    /**
     * 高级抗性处理入口，默认不做任何事
     * Slave覆写为固定25%减伤，Master覆写为三层动态抗性
     *
     * @param source 伤害来源
     * @param damage 伤害值
     * @return 处理后伤害值
     */
    protected float applyAdvancedResistance(DamageSource source, float damage) {
        return damage;
    }

    /**
     * 抗性系统的定期更新，在aiStep中每秒调用一次
     * 默认不做任何事，Master覆写处理适应性抗性的衰减
     */
    protected void tickResistance() {
        // 默认无操作
    }

    // ==================== 动画 ====================

    @Override
    public @NotNull MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<AbstractKuva> main = new AnimationController<>(this, "controller", 0, state -> {
            // 怜悯之击的片段之间不插值；走、跑、待机的过渡在 locomotion 里另设
            state.getController().transitionLength(0);
            // 倒地、处决、消散期间由怜悯之击挑片段；不在这些状态时返回 null，照原来的走路 / 待机
            RawAnimation mercy = MercyAnimSelector.pick(this, state);
            MercyAnimSelector.track(this, mercy);
            if (mercy != null) {
                // 只有从倒地循环站起来那一段要淡进来（循环停在哪一帧不一定）
                state.getController().transitionLength(MercyAnimSelector.blendTicks(mercy));
                return state.setAndContinue(mercy);
            }
            return this.bodyAnim.locomotion(state, this.entityData.get(HAS_TARGET));
        });
        // 顿帧时玄骸跟着停：速度按比值给（见 MercyHitstop.clipSpeed），不在处决里是 1
        main.setAnimationSpeedHandler(MercyAnimSelector::speed);
        controllers.add(main);
        // 说话：后注册的控制器盖住同一根骨骼，说话时头部不再跟着走路 / 跑步摆。
        // 计时用客户端演出钟（静态钩子），不用 tickCount：服务端节流时 tickCount 会和声音对不上。
        // 奴仆的 talkUntilMs 永远是 0，等于不播
        controllers.add(new AnimationController<>(this, "talk", 4, state -> {
            if (this.talkUntilMs > CLIENT_CLOCK.getAsLong()) {
                if (!this.talkStarted) {
                    this.talkStarted = true;
                    state.getController().forceAnimationReset();
                    return state.setAndContinue(this.talkShout ? TALK_SHOUT : TALK);
                }
                return PlayState.CONTINUE;
            }
            this.talkStarted = false;
            return PlayState.STOP;
        }));
        // 起势 / 出手：排在最后，出手时盖住走路和说话的上身；站着出手连腿一起，边走边打只管上身（见 KuvaBodyAnim）
        controllers.add(new AnimationController<>(this, "attack", 0, state -> this.bodyAnim.attackLayer(state)));
    }

    /** 走、跑、待机和起势 / 出手的选片状态，模型那边淡出这层动画时要读 */
    public KuvaBodyAnim getBodyAnim() {
        return this.bodyAnim;
    }

    /** 起势中：下一刀快出手了 */
    public boolean isStrikeReady() {
        return this.entityData.get(STRIKE_READY);
    }

    void setStrikeReady(boolean ready) {
        this.entityData.set(STRIKE_READY, ready);
    }

    @Override
    public void swing(@NotNull InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (this.level().isClientSide) {
            this.swingSeq++;
        }
    }

    private static final RawAnimation TALK = RawAnimation.begin().thenLoop("talk");
    private static final RawAnimation TALK_SHOUT = RawAnimation.begin().thenPlay("talk_shout").thenLoop("talk");

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    // ==================== AI目标 ====================

    @Override
    protected void registerGoals() {
        // 近战排在闲逛、张望前面：闲逛的优先级要是更高，打着打着会被它抢走移动权，停下来溜达几秒
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new KuvaMeleeGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Monster.class, 32.0F));
        this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 16, true, true,
                entity -> !(entity instanceof Creeper) && !(entity instanceof AbstractKuva)));
    }

    // ==================== 在场名单（自然生成密度限制用） ====================

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        KuvaSpawnLimiter.track(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        KuvaSpawnLimiter.untrack(this);
    }

    // ==================== 状态管理 ====================

    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity previous = getTarget();
        boolean hadTarget = previous != null && previous.isAlive();
        boolean hasTarget = target != null && target.isAlive();
        this.entityData.set(HAS_TARGET, hasTarget);
        super.setTarget(target);

        // 登场警示：每只一辈子只发一次——第一次以 64 格内的玩家为目标时。
        // 召唤、到场演出生成的实体在生成时就 markAlerted() 了，不会再发
        if (!level().isClientSide && !arrivalShown && !hadTarget && target instanceof Player
                && target.distanceToSqr(this) <= ALERT_RANGE * ALERT_RANGE) {
            arrivalShown = true;
            level().broadcastEntityEvent(this, EV_ALERT);
        }
    }

    /** 它的登场已经由别的演出（召唤、到场）交代过了，之后不再单独发登场警示 */
    public void markAlerted() {
        arrivalShown = true;
    }

    /** 登场警示发过没有 */
    public boolean isArrivalShown() {
        return arrivalShown;
    }

    @Override
    public void handleEntityEvent(byte id) {
        // 登场警示、近战命中和死亡第 20 tick 的白烟都换成自制演出；原版白烟不画（不调 super）
        if (id == EV_ALERT || id == EV_HIT || id == EV_DEATH_POOF) {
            if (level().isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LichFx.onEntityEvent(this, id));
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HAS_TARGET, false);
        this.entityData.define(DATA_MERCY_STATE, (byte) 0);
        this.entityData.define(DATA_MERCY_ACTOR, -1);
        this.entityData.define(DATA_MERCY_TIMER, 0);
        this.entityData.define(DATA_MERCY_PLAN, 0);
        this.entityData.define(DATA_MERCY_CHOICE, (byte) 0);
        this.entityData.define(ADAPT_PACKED, 0);
        this.entityData.define(FURY_PERMILLE, 0);
        this.entityData.define(STRIKE_READY, false);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MERCY_TIMER.equals(key)) {
            // 倒地剩余时间每 20 tick 才同步一次，记下到达时刻，客户端据此往下推
            this.mercyTimerSyncTick = this.tickCount;
        } else if (DATA_MERCY_STATE.equals(key)) {
            // 碰撞箱随倒地变矮：服务端的 refreshDimensions 不会把新碰撞箱同步过去，
            // 两端都在同步数据变化时各刷一次（原版史莱姆改大小也是这么做的），准星和攻击判定才对得上
            refreshDimensions();
        }
    }

    // ==================== 怜悯之击：倒地期间的行为 ====================

    /** 倒地后碰撞箱高度的比例（眼高同比例） */
    protected abstract float downedHeightScale();

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        EntityDimensions base = super.getDimensions(pose);
        // 构造早期同步数据还没定义完时按站姿算
        if (this.entityData == null || !this.entityData.hasItem(DATA_MERCY_STATE)) {
            return base;
        }
        return getMercyState() == MercyState.NONE ? base : base.scale(1.0F, downedHeightScale());
    }

    @Override
    protected boolean isImmobile() {
        // 原版据此跳过 serverAiStep：目标选择、寻路、近战都停
        return super.isImmobile() || getMercyState() != MercyState.NONE;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (getMercyState() != MercyState.NONE) {
            return;
        }
        super.knockback(strength, x, z);
    }

    @Override
    public boolean ignoreExplosion() {
        return getMercyState() != MercyState.NONE || super.ignoreExplosion();
    }

    @Override
    protected void pushEntities() {
        // 倒地的玄骸会把贴身的处决者推开
        if (getMercyState() != MercyState.NONE) {
            return;
        }
        super.pushEntities();
    }

    @Override
    public boolean isPushable() {
        return getMercyState() == MercyState.NONE && super.isPushable();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return getMercyState() == MercyState.NONE && this.leaveTicks < 0 && super.removeWhenFarAway(distanceSq);
    }

    // ==================== 怜悯之击：同步数据 ====================

    /** 当前的怜悯之击状态 */
    public MercyState getMercyState() {
        return MercyState.byOrdinal(this.entityData.get(DATA_MERCY_STATE));
    }

    /** 倒地时的资格人 / 处决时的处决者的实体 id，没有为 -1 */
    public int getMercyActorId() {
        return this.entityData.get(DATA_MERCY_ACTOR);
    }

    /** 最近一次同步的剩余 tick（倒地剩余、消散剩余；处决时是冻结的值） */
    public int getMercyRemainingTicks() {
        return this.entityData.get(DATA_MERCY_TIMER) & 0xFFFF;
    }

    /**
     * 此刻的剩余 tick，客户端按上次同步之后经过的时间往下推；处决中不动。画倒计时圈用
     */
    public float getMercyRemainingTicks(float partialTick) {
        float remaining = getMercyRemainingTicks();
        MercyState state = getMercyState();
        if (this.level().isClientSide && (state == MercyState.DOWNED || state == MercyState.LEAVING)) {
            remaining -= (this.tickCount - this.mercyTimerSyncTick) + partialTick;
        }
        return Math.max(0.0F, remaining);
    }

    /** 可以处决这只实体的最大距离（格） */
    public float getMercyReach() {
        return ((this.entityData.get(DATA_MERCY_TIMER) >> 16) & 0xFF) / 10.0F;
    }

    /** 倒地的总时长（秒），按 5 秒一档 */
    public int getMercyTotalSeconds() {
        return ((this.entityData.get(DATA_MERCY_TIMER) >> 24) & 0x7F) * 5;
    }

    /** 处决时的计划位（见 {@code MercyPlan}），不在处决时是上一次的残值 */
    public int getMercyPlan() {
        return this.entityData.get(DATA_MERCY_PLAN);
    }

    /** 把倒地的剩余 tick、可处决距离、倒地总时长压成计时字段的值 */
    public static int packMercyTimer(int remainingTicks, float reach, int totalSeconds) {
        int remaining = Math.max(0, Math.min(0xFFFF, remainingTicks));
        int reachTenths = Math.max(0, Math.min(0xFF, Math.round(reach * 10.0F)));
        int fiveSecondSteps = Math.max(0, Math.min(0x7F, totalSeconds / 5));
        return remaining | (reachTenths << 16) | (fiveSecondSteps << 24);
    }

    public void setMercyState(MercyState state) {
        this.entityData.set(DATA_MERCY_STATE, (byte) state.ordinal());
    }

    public void setMercyActorId(int entityId) {
        this.entityData.set(DATA_MERCY_ACTOR, entityId);
    }

    /** 写计时字段，值用 {@link #packMercyTimer} 拼 */
    public void setMercyTimerBits(int bits) {
        this.entityData.set(DATA_MERCY_TIMER, bits);
    }

    public void setMercyPlanBits(int bits) {
        this.entityData.set(DATA_MERCY_PLAN, bits);
    }

    public static final byte CHOICE_NONE = 0, CHOICE_WAITING = 1, CHOICE_VANQUISH = 2, CHOICE_CONVERT = 3;

    public byte getMercyChoice() {
        return this.entityData.get(DATA_MERCY_CHOICE);
    }

    public void setMercyChoice(byte choice) {
        this.entityData.set(DATA_MERCY_CHOICE, choice);
    }

    // ==================== 适应层数与交战增幅：同步数据 ====================

    /** 适应性抗性层数，5 类 × 3 位，按 {@link DamageCategory} 的序号 × 3 排 */
    public int getAdaptPacked() {
        return this.entityData.get(ADAPT_PACKED);
    }

    /** 交战增幅千分比 */
    public int getFuryPermille() {
        return this.entityData.get(FURY_PERMILLE);
    }

    public void setAdaptPacked(int packed) {
        this.entityData.set(ADAPT_PACKED, packed);
    }

    public void setFuryPermille(int permille) {
        this.entityData.set(FURY_PERMILLE, permille);
    }

    // ==================== 怜悯之击：服务端入口 ====================

    /**
     * 让这只实体立刻进入倒地状态（管理命令、演示用）。{@code qualified} 是有资格处决的人：
     * 奴仆和演示目标就是他，有主玄骸永远是主人（传什么都一样），无主的野生玄骸不倒地。
     * 血量直接压到这一段的底（最后一段留 1）；已经倒地、正在处决、起身中、已经死了的不处理
     */
    public void forceDowned(@Nullable ServerPlayer qualified) {
        if (this.level().isClientSide || !this.isAlive() || this.dead || getMercyState() != MercyState.NONE) {
            return;
        }
        MercyServer.forceDowned(this, qualified);
    }

    /**
     * 死亡后实体至少再留这么多 tick 才移除（给离场演出用）。谁发了更长的演出谁调，取最大值；默认 20
     */
    public void holdDeathTicks(int ticks) {
        this.deathHoldTicks = Math.max(this.deathHoldTicks, ticks);
    }

    /** 死亡后会留多少 tick 再移除 */
    public int getDeathHoldTicks() {
        return this.deathHoldTicks;
    }

    /** 倒地剩余 tick（服务端） */
    public int getMercyRemainingServer() {
        return this.mercyRemaining;
    }

    /** 改倒地剩余 tick（服务端），同时刷新同步给客户端的计时字段 */
    public void setMercyRemaining(int ticks) {
        this.mercyRemaining = Math.max(0, ticks);
        syncMercyTimer();
    }

    /** 资格人的 UUID（服务端），没有为 null */
    @Nullable
    public UUID getMercyQualified() {
        return this.mercyQualified;
    }

    public void setMercyQualified(@Nullable UUID qualified) {
        this.mercyQualified = qualified;
    }

    /**
     * 最近 5 秒内打过它的玩家（原版掉经验用的那份记录），没有为 null。
     * 烧伤、摔进岩浆这类延后的致命伤，资格人按它算
     */
    @Nullable
    public Player recentPlayerAttacker() {
        return this.lastHurtByPlayerTime > 0 ? this.lastHurtByPlayer : null;
    }

    /** 这次死亡是处决走完的那一下（只有它给奖励） */
    public boolean isMercyFinishing() {
        return this.mercyFinishing;
    }

    public void setMercyFinishing(boolean finishing) {
        this.mercyFinishing = finishing;
    }

    /** 标记已经发过死亡效果包：之后的死亡流程不再发默认的那个 */
    public void markDeathFxSent() {
        this.deathFxSent = true;
    }

    /** 演示目标被判错的次数 */
    public int getDemoFailures() {
        return this.demoFailures;
    }

    public void addDemoFailure() {
        this.demoFailures++;
    }

    /** 把倒地剩余 tick、可处决距离、倒地总时长写进同步数据 */
    public void syncMercyTimer() {
        setMercyTimerBits(packMercyTimer(this.mercyRemaining, MercyRules.reach(), downedSeconds()));
    }

    /** 倒地一次等多少秒：玄骸、奴仆照 {@code mercyDownedSeconds}，幼体有自己的 */
    public int downedSeconds() {
        return ModConfig.KUVA_LICH.mercyDownedSeconds.get();
    }

    /** 处决时胸口离脚底多高（格）：处决者看向这里，处决特效从这里出 */
    public double mercyChestHeight() {
        return getBbHeight() * MERCY_CHEST_FRAC;
    }

    /**
     * 身材相对处决动作原本按的那只缩了多少：处决者的刀位、跳起的高度按它缩。
     * 玄骸、奴仆是 1；幼体借奴仆的动作，模型又缩小过
     */
    public float mercyBodyScale() {
        return 1.0F;
    }

    /**
     * 开始消散（奴仆倒地超时、自然消失）：状态改成消散中、关 AI、发 {@code LICH_EXPIRE}，
     * {@link #EXPIRE_TICKS} tick 后移除。不触发死亡事件，什么都不给
     */
    public void beginExpire() {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        setMercyState(MercyState.LEAVING);
        this.setNoAi(true);
        this.getNavigation().stop();
        this.mercyRemaining = EXPIRE_TICKS;
        setMercyTimerBits(packMercyTimer(EXPIRE_TICKS, MercyRules.reach(), 0));
        this.leaveTicks = EXPIRE_TICKS;
        if (this.level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chest(), 32.0D, FxId.LICH_EXPIRE, this, 0, EXPIRE_TICKS);
        }
    }

    /** 正在消散 / 自然离开的演出中 */
    public boolean isLeaving() {
        return this.leaveTicks >= 0;
    }

    /** 胸口位置（效果包的原点） */
    public Vec3 chest() {
        return position().add(0.0D, getBbHeight() * MercyRules.LICH_ANCHOR, 0.0D);
    }

    /**
     * 交战增幅临时设成某个千分比（演示命令用），{@code ticks} 后恢复按交战时长算；不改真实的伤害加成
     */
    public void setFuryOverride(int permille, int ticks) {
        this.furyOverride = Math.max(0, permille);
        this.furyOverrideTicks = Math.max(0, ticks);
        if (!this.level().isClientSide) {
            setFuryPermille(this.furyOverrideTicks > 0 ? this.furyOverride : furyFromBattle());
        }
    }

    /** 管理命令一键复位：回到满血、没有任何怜悯之击状态（处决由调用方先收掉） */
    public void resetMercy() {
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        this.mercyRemaining = 0;
        this.mercyQualified = null;
        this.mercyFinishing = false;
        this.leaveTicks = -1;
        this.riseTicks = 0;
        this.setNoAi(false);
        setMercyActorId(-1);
        setMercyTimerBits(0);
        setMercyPlanBits(0);
        // 演示目标会被反复处决，残留的 2 / 3 会让下一场的客户端以为已经落定
        setMercyChoice(CHOICE_NONE);
        setMercyState(MercyState.NONE);
        this.setHealth(segmentCeil());
    }

    /**
     * 进起身（服务端）：{@code ticks} 后自己回到能打的状态。倒地到时没人处决、处决还没到第三刀全对时由怜悯之击调；
     * 计划位留着，客户端按它挑从哪个姿势站起来
     */
    public void beginRise(int ticks) {
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        this.riseTicks = Math.max(1, ticks);
        this.setTarget(null);
        this.getNavigation().stop();
        setMercyRemaining(0);
        setMercyState(MercyState.RISING);
    }

    /** 起身演完：回到能打的状态 */
    private void finishRise() {
        this.riseTicks = 0;
        this.mercyQualified = null;
        setMercyActorId(-1);
        setMercyPlanBits(0);
        setMercyState(MercyState.NONE);
    }

    // ==================== 怜悯之击：分段血条 ====================

    /** 血条分不分段：分段的每打空一段倒地一次（有主玄骸），不分段的打到 0 才倒地 */
    public boolean isSegmented() {
        return false;
    }

    /** 这一段的下沿：伤害最多打到这里，打到就倒地 */
    public float segmentFloor() {
        return 0.0F;
    }

    /** 这一段的上沿：自己回血最多回到这里 */
    public float segmentCeil() {
        return this.getMaxHealth();
    }

    /** 倒地时的血量：这一段的底，最后一段（底是 0）留 1 */
    public float downedHealth() {
        return Math.max(1.0F, segmentFloor());
    }

    /** 分段的：这一下（已经扣过护盾和血）把这一段打空了 */
    private boolean reachedSegmentFloor(DamageSource source) {
        if (this.level().isClientSide || this.dead || this.isRemoved() || getMercyState() != MercyState.NONE
                || !isSegmented() || isHardDeath(source) || !MercyRules.canDown(this)) {
            return false;
        }
        float floor = segmentFloor();
        return floor > 0.0F && this.getHealth() <= floor + 1.0E-3F;
    }

    /** 分段的回血不越过这一段的上沿 */
    @Override
    public void heal(float amount) {
        if (!this.level().isClientSide && isSegmented() && getMercyState() == MercyState.NONE) {
            amount = Math.min(amount, segmentCeil() - this.getHealth());
            if (amount <= 0.0F) {
                return;
            }
        }
        super.heal(amount);
    }

    /**
     * 分段兜底（能打的时候每 tick）：别的路线直接写血量（毒素、真伤、别的模组回血）也越不过这一段的上下沿，
     * 打穿下沿的照样在下沿倒地
     */
    private void holdSegment() {
        float ceil = segmentCeil();
        if (this.getHealth() > ceil + 1.0E-3F) {
            this.setHealth(ceil);
            return;
        }
        float floor = segmentFloor();
        if (floor > 0.0F && this.getHealth() <= floor + 1.0E-3F && MercyRules.canDown(this)) {
            DamageSource last = this.getLastDamageSource();
            this.setHealth(floor);
            MercyServer.enterDowned(this, last != null ? last : this.damageSources().generic());
        }
    }

    // ==================== 演示用实体 ====================

    /** 标记成演示用：不存盘、不参与专属玄骸的任何检查 */
    public void markDemo() {
        this.demo = true;
    }

    public boolean isDemo() {
        return demo;
    }

    /**
     * 演示用的不存盘；正在消散 / 自然离开的也不存：这段时间关着 AI、状态又不进存档，
     * 这时候存盘读回来就是一只不会动的怪，反正一两秒后就要移除，干脆不存
     */
    @Override
    public boolean shouldBeSaved() {
        return !demo && this.leaveTicks < 0 && super.shouldBeSaved();
    }

    // ==================== 致命伤先倒地 ====================

    /** /kill 命令的类：运行时类名就是这个（Forge 1.17 起类名不混淆，只有方法、字段是 SRG 名） */
    private static final String KILL_COMMAND_CLASS = "net.minecraft.server.commands.KillCommand";

    /**
     * 能真打死它、不先倒地也不受倒地保护的只有两种：{@code /kill} 命令（见 {@link #kill()}），
     * 和真的掉出了世界（脚底低于世界底 64 格，原版每 tick 扣 4 点的那个）。
     * <p>
     * 不按「绕过无敌」的标签认：别的模组的真伤、斩杀、持续伤害的致死一击常借 {@code genericKill}、
     * {@code fellOutOfWorld} 或者自己挂这个标签的伤害类型（卡利亚式附魔的命定之死、处决类附魔扣不动血量时
     * 就补一下 {@code genericKill}），按标签放行的话倒地的玄骸、奴仆、幼体会被它们直接打死，怜悯之击就白打了
     */
    protected boolean isHardDeath(DamageSource source) {
        return this.hardDeathBlow
                || source.is(DamageTypes.FELL_OUT_OF_WORLD) && this.getY() < this.level().getMinBuildHeight() - 64;
    }

    /**
     * 原版 {@code LivingEntity.kill()} 就是一下 {@code hurt(genericKill, Float.MAX_VALUE)}。
     * 只有 {@code /kill} 命令调过来的那次按真死结算；别的模组拿它当斩杀用的，照普通致命伤处理：
     * 没倒地的先倒地，倒地、处决、消散期间打不动
     */
    @Override
    public void kill() {
        if (this.level().isClientSide || this.hardDeathBlow || !calledFromKillCommand()) {
            super.kill();
            return;
        }
        this.hardDeathBlow = true;
        try {
            super.kill();
        } finally {
            this.hardDeathBlow = false;
        }
    }

    /** 调用栈前几层里有没有 /kill 命令（kill() 很少调，看一眼栈不费事） */
    private static boolean calledFromKillCommand() {
        return StackWalker.getInstance().walk(frames -> frames.limit(16)
                .anyMatch(f -> KILL_COMMAND_CLASS.equals(f.getClassName())));
    }

    /**
     * 致命伤先倒地：满足条件就把血量压回这一段的底（最后一段留 1）、进倒地，返回 true，调用方不要再往下走。
     * 只有 {@link #isHardDeath} 认的（{@code /kill}、真掉出世界）照常真死
     */
    private boolean interceptLethal(DamageSource source) {
        if (this.level().isClientSide || this.dead || this.isRemoved() || getMercyState() != MercyState.NONE
                || isHardDeath(source) || !MercyRules.canDown(this)) {
            return false;
        }
        this.setHealth(downedHealth());
        MercyServer.enterDowned(this, source);
        return true;
    }

    /**
     * 倒地、处决、起身、消散、自然离开期间不吃「不经 hurt、直接写血量」的伤害（这时 hurt 已经全部挡掉，这几条是漏网的）：
     * 真实子弹 1 tick 后的真伤、处决词条的强杀、流血 / 毒素持续伤害、武器自带斩杀，以及别的模组的真伤、持续伤害。
     * 处决走完的那一下（{@link #isMercyFinishing()}）和 {@link #isHardDeath} 认的（{@code /kill}、真掉出世界）照常
     */
    public boolean isDamageProtected() {
        return !this.mercyFinishing && !this.hardDeathBlow
                && (getMercyState() != MercyState.NONE || this.leaveTicks >= 0);
    }

    /**
     * 受保护期间血量不许压到受保护的值以下（倒地时的值，起身中是这一段满血）：别的模组的真伤、持续掉血、斩杀
     * 多半直接写血量，写完再调 {@code die}、或者看血量没归零就补一下 {@code genericKill}，写不下去就都落空。
     * 只拦往下压的；往上回血（倒地期间这一段跟着计时回涨）照常
     */
    @Override
    public void setHealth(float health) {
        // 先比大小：构造期间 LivingEntity 设满血时子类字段还没初始化，往上设的不往下看
        if (health < this.getHealth() && !this.level().isClientSide && isDamageProtected()) {
            float keep = protectedHealth();
            if (health < keep) {
                health = Math.min(this.getHealth(), keep);
            }
        }
        super.setHealth(health);
    }

    /**
     * 受保护期间被直接写血量再调 die 的路线：血量补回倒地时的值、留在原状态。
     * 不拦的话会按「绕过倒地的死亡」结算，不判定、不给进度，怜悯之击就白打了
     */
    private boolean keepAliveWhileProtected(DamageSource source) {
        if (this.level().isClientSide || this.dead || this.isRemoved() || !isDamageProtected()
                || isHardDeath(source)) {
            return false;
        }
        float keep = protectedHealth();
        if (this.getHealth() < keep) {
            this.setHealth(keep);
        }
        return true;
    }

    /** 受保护期间血量被写坏时补回多少：起身中是这一段满血，其余是倒地时的值 */
    private float protectedHealth() {
        return getMercyState() == MercyState.RISING ? segmentCeil() : downedHealth();
    }

    /**
     * 主路线：扣完血看一眼，到 0 就拦下；分段的打到这一段的底就压在底上、进倒地。
     * 血量不到 0，原版 hurt 就不会放死亡音、不调 die
     */
    @Override
    protected void actuallyHurt(@NotNull DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (this.getHealth() <= 0.0F) {
            interceptLethal(source);
        } else if (reachedSegmentFloor(source)) {
            this.setHealth(segmentFloor());
            MercyServer.enterDowned(this, source);
        }
    }

    /**
     * 死亡：开头兜底「先直接写血量再调 die」的路线（受保护期间留在原状态，没倒地的先倒地）；原版死亡流程走完、确实死了，交给子类发奖励，
     * 再补发默认的死亡效果包（前面没有哪一路发过的话）
     */
    @Override
    public void die(@NotNull DamageSource source) {
        if (keepAliveWhileProtected(source) || interceptLethal(source)) {
            return;
        }
        // Forge 的 LivingDeathEvent 在 super.die() 里触发，被别的模组取消时 this.dead 保持 false，那时什么都不给
        super.die(source);
        if (this.level().isClientSide || !this.dead) {
            return;
        }
        onKilled(source);
        if (!this.deathFxSent) {
            sendDeathFx(source);
            this.deathFxSent = true;
        }
        MercyServer.onTargetDied(this);
    }

    /** 确实死了（服务端）：子类在这里发奖励。需要特殊死亡效果包的，自己发并调 {@link #markDeathFxSent()} */
    protected void onKilled(DamageSource source) {
    }

    /** 默认的死亡效果包（前面没人发过时才调） */
    protected abstract void sendDeathFx(DamageSource source);

    /**
     * 死亡计时：不调原版，原版第 20 tick 发实体事件 60（白烟）再移除；这里只按留的时长移除，演出全由死亡 / 离场包驱动。
     * 开头兜底「直接把血量写成 0、没调 die」的路线：受保护期间补回倒地时的值，没倒地的照常先倒地
     */
    @Override
    protected void tickDeath() {
        if (!this.level().isClientSide && !this.dead) {
            // 受保护期间血量被直接写成 0、又没走 die：补回来，不然这里数满就悄悄移除了
            if (isDamageProtected()) {
                this.setHealth(protectedHealth());
                return;
            }
            if (getMercyState() == MercyState.NONE) {
                DamageSource last = this.getLastDamageSource();
                if (interceptLethal(last != null ? last : this.damageSources().generic())) {
                    return;
                }
            }
        }
        ++this.deathTime;
        if (this.deathTime >= this.deathHoldTicks && !this.level().isClientSide && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        // 怜悯之击开着时，绕过倒地的死亡（/kill、虚空）不掉原版战利品；演示用的一律不掉
        return !isDemo() && (this.mercyFinishing || !MercyRules.enabledFor(this)) && super.shouldDropLoot();
    }

    /** 演示用的不掉经验（录像时满地经验球） */
    @Override
    public boolean shouldDropExperience() {
        return !isDemo() && super.shouldDropExperience();
    }

    // ==================== 自然消失的截停 ====================

    @Override
    public void checkDespawn() {
        if (this.leaveTicks >= 0) {
            return;
        }
        this.inDespawnCheck = true;
        try {
            super.checkDespawn();
        } finally {
            this.inDespawnCheck = false;
        }
    }

    /**
     * 原版自然消失（和平难度清怪、走远后随机消失、事件放行）都是在 checkDespawn 里直接 discard，
     * 不复制它的判断，只把那一下移除截下来：附近有人就先演一遍离场再移除
     */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (this.inDespawnCheck && reason == RemovalReason.DISCARDED && !this.level().isClientSide
                && beginNaturalLeave()) {
            return;
        }
        // 受保护期间没死就被按「被杀」直接移除（别的模组的抹杀，不走死亡）：不认，留在原状态。
        // 自己死后的移除（tickDeath）这时已经 dead；消散、离开到时走的是 discard
        if (reason == RemovalReason.KILLED && !this.dead && !this.level().isClientSide && isDamageProtected()) {
            return;
        }
        super.remove(reason);
    }

    private boolean beginNaturalLeave() {
        if (!anyPlayerWithin(NATURAL_LEAVE_RANGE)) {
            return false;
        }
        if (getMercyState() == MercyState.EXECUTING) {
            // 正在处决：先按中断收掉这一场（定案后会快进到死），死了就不用再演离场
            MercyServer.onTargetLeaving(this);
            if (!this.isAlive() || this.isRemoved()) {
                return true;
            }
        }
        if (!startNaturalLeave()) {
            return false;
        }
        if (this.leaveTicks < 0) {
            this.leaveTicks = OWNED_LEAVE_FALLBACK_TICKS;
        }
        return true;
    }

    /**
     * 自然消失时演的离场（附近有人时才调）。返回 false 表示不演、照原样移除
     */
    protected abstract boolean startNaturalLeave();

    private boolean anyPlayerWithin(double range) {
        // 旁观者也算：自己看着它消失的人不分模式
        for (Player p : this.level().players()) {
            if (p.distanceToSqr(this) <= range * range) {
                return true;
            }
        }
        return false;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
            return;
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.furyOverrideTicks > 0 && --this.furyOverrideTicks == 0) {
            setFuryPermille(furyFromBattle());
        }
        if (this.leaveTicks >= 0) {
            if (--this.leaveTicks <= 0) {
                this.leaveTicks = -1;
                this.discard();
            }
            return;
        }
        if (!this.isAlive()) {
            return;
        }
        MercyState state = getMercyState();
        if (state == MercyState.DOWNED) {
            tickDowned();
        } else if (state == MercyState.RISING) {
            if (--this.riseTicks <= 0) {
                finishRise();
            }
        } else if (state == MercyState.NONE && isSegmented()) {
            holdSegment();
        }
    }

    /**
     * 倒地计时：冻结时不走；每 20 tick 同步一次剩余时间、按 UUID 重新找资格人（重登后实体 id 会变）。
     * 分段的这段血跟着计时从底回到顶，到时正好满
     */
    private void tickDowned() {
        if (!MercyServer.isFrozen(this.level())) {
            this.mercyRemaining--;
        }
        if (isSegmented()) {
            int total = Math.max(1, downedSeconds() * 20);
            float t = Mth.clamp(1.0F - (float) this.mercyRemaining / total, 0.0F, 1.0F);
            float floor = segmentFloor();
            float refill = Math.max(1.0F, floor + (segmentCeil() - floor) * t);
            if (refill > this.getHealth()) {
                this.setHealth(refill);
            }
        }
        if (this.tickCount % 20 == 0) {
            setMercyActorId(MercyServer.resolveQualifiedId(this));
            syncMercyTimer();
        }
        if (this.mercyRemaining <= 0) {
            MercyServer.onDownedTimeout(this);
        }
    }

    /** 客户端：挥臂检测、回血检测。实体类两端都加载，客户端的事只经 DistExecutor 的桥过去 */
    private void clientTick() {
        // 挥臂包到了以后 swingTime 先是 -1，aiStep 里加到 0：这一刻就是挥臂的第一 tick，每次挥臂只成立一次
        if (this.swinging && this.swingTime == 0) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KuvaAnimFx.onSwing(this));
        }
        float health = this.getHealth();
        if (this.lastClientHealth >= 0.0F && health - this.lastClientHealth >= this.getMaxHealth() * 0.01F
                && this.tickCount - this.lastRegenTick >= 20 && getMercyState() == MercyState.NONE
                && !this.isDeadOrDying()) {
            this.lastRegenTick = this.tickCount;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KuvaStateFx.onRegen(this));
        }
        this.lastClientHealth = health;
    }

    @Override
    public void handleDamageEvent(@NotNull DamageSource source) {
        super.handleDamageEvent(source);
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KuvaStateFx.onDamageEvent(this, source));
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide) {
            updateTargetStatus();
            handleHealing();

            // 抗性衰减每秒检查一次即可，无需每tick
            if (level().getGameTime() % RESISTANCE_CHECK_INTERVAL == 0) {
                tickResistance();
            }
        }

        updateBattleTick();
    }

    /**
     * 更新目标存在状态的同步数据
     */
    private void updateTargetStatus() {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        this.entityData.set(HAS_TARGET, hasTarget);
    }

    /**
     * 处理每秒自愈逻辑
     * 有目标时回复更快
     */
    private void handleHealing() {
        // 倒地、处决、起身、消散期间不自己回血（倒地时分段的回血在倒地计时里）
        if (getMercyState() != MercyState.NONE) {
            return;
        }
        if (level().getGameTime() % 20 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }

        float healAmount = this.entityData.get(HAS_TARGET)
                ? getHasTargetTickHeal()
                : getTickHeal();

        this.heal(healAmount);
    }

    /**
     * 更新战斗tick计数
     * 有目标时递增，无目标时递减；倒地期间冻结（计数保留，不增不减）。
     * 服务端每 20 tick 把交战增幅千分比写进同步数据，客户端画增幅档位用
     */
    private void updateBattleTick() {
        if (getMercyState() != MercyState.NONE) {
            return;
        }
        if (this.entityData.get(HAS_TARGET)) {
            battleTick++;
        } else if (battleTick > 0 && (this.tickCount % 2 == 0 || !furyLingers())) {
            battleTick--;
        }
        if (!this.level().isClientSide && this.tickCount % 20 == 0 && this.furyOverrideTicks <= 0) {
            setFuryPermille(furyFromBattle());
        }
    }

    private int furyFromBattle() {
        return (int) Math.round(this.battleTick / 20.0D * furyPerSecond() * 1000.0D);
    }

    /** 增幅是专长：丢了目标后增幅掉得慢一半 */
    private boolean furyLingers() {
        LichTraits traits = combatTraits();
        return traits != null && traits.isSignature(LichTraits.FURY);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // 免疫摔落伤害
        if (source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        // 倒地、处决、起身、消散、自然离开期间不受伤；只有 /kill、真掉出世界照常（别的模组借「绕过无敌」的真伤也挡）
        boolean hard = isHardDeath(source);
        if ((getMercyState() != MercyState.NONE || this.leaveTicks >= 0) && !hard) {
            return false;
        }
        if (!hard || this.hardDeathBlow) {
            return super.hurt(source, amount);
        }
        // 真掉出世界的这一下：结算期间血量下限、死亡兜底都让开（/kill 在 kill() 里已经标上了）
        this.hardDeathBlow = true;
        try {
            return super.hurt(source, amount);
        } finally {
            this.hardDeathBlow = false;
        }
    }

    // ==================== 音效 ====================

    /** 死亡声由死亡效果包的演出放（哪种死法放哪种声），原版钩子不再叠一声 */
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    /** 方块脚步照常，再叠一层赤毒的金属步声 */
    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
        super.playStepSound(pos, state);
        this.playSound(KuvaLichSounds.ENTITY_KUVA_STEP.get(), 0.5F, 1.0F);
    }

    // ==================== NBT存档 ====================

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BattleTick", this.battleTick);
        if (this.arrivalShown) {
            compound.putBoolean("KuvaArrivalShown", true);
        }
        // 倒地中（处决中按倒地存，处决本身不存）：读回来接着倒地、剩余时间接着走
        MercyState state = getMercyState();
        if ((state == MercyState.DOWNED || state == MercyState.EXECUTING) && this.isAlive()) {
            CompoundTag mercy = new CompoundTag();
            mercy.putByte("State", (byte) MercyState.DOWNED.ordinal());
            mercy.putInt("Remaining", Math.max(1, this.mercyRemaining));
            if (this.mercyQualified != null) {
                mercy.putUUID("ActorUUID", this.mercyQualified);
            }
            compound.put("Mercy", mercy);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.battleTick = compound.getInt("BattleTick");
        this.arrivalShown = compound.getBoolean("KuvaArrivalShown");
        if (compound.contains("Mercy", Tag.TAG_COMPOUND)) {
            CompoundTag mercy = compound.getCompound("Mercy");
            this.mercyQualified = mercy.hasUUID("ActorUUID") ? mercy.getUUID("ActorUUID") : null;
            this.mercyRemaining = Math.max(1, mercy.getInt("Remaining"));
            setMercyActorId(-1);
            syncMercyTimer();
            setMercyState(MercyState.DOWNED);
        }
    }

    // ==================== Getter ====================

    public int getBattleTick() {
        return battleTick;
    }

    // ==================== 子类必须实现 ====================

    /**
     * 无目标时每秒回血量
     *
     * @return 回血量
     */
    protected abstract float getTickHeal();

    /**
     * 有目标时每秒回血量
     *
     * @return 回血量
     */
    protected abstract float getHasTargetTickHeal();
}
