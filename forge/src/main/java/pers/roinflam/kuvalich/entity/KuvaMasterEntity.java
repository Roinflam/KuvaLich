package pers.roinflam.kuvalich.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.nemesis.EncounterEnd;
import pers.roinflam.kuvalich.nemesis.LichComms;
import pers.roinflam.kuvalich.nemesis.Finale;
import pers.roinflam.kuvalich.nemesis.FinaleSettlement;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.nemesis.LichLine;
import pers.roinflam.kuvalich.nemesis.LichPersona;
import pers.roinflam.kuvalich.nemesis.LichTraits;
import pers.roinflam.kuvalich.mercy.KuvaStateServer;
import pers.roinflam.kuvalich.mercy.MercyRules;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.item.module.weapon.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.world.inventory.RequiemGateMenu;

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
public class KuvaMasterEntity extends AbstractKuva {

    // ==================== 专属玄骸的同步数据 ====================

    /** 主人的 UUID，没有主人时为空 */
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 主人的名字：别人看到时写成「某某的玄骸」 */
    private static final EntityDataAccessor<String> OWNER_NAME =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.STRING);
    /** 玄骸自己的名字，首字母大写 */
    private static final EntityDataAccessor<String> LICH_NAME =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.STRING);
    /** 玄骸等级（kuvaLevel + 1），Boss 条徽章用 */
    private static final EntityDataAccessor<Integer> LICH_RANK =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.INT);
    /** 外观和嗓音的种子 */
    private static final EntityDataAccessor<Integer> LICH_SEED =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.INT);
    /** 手里那把武器在赤毒武器列表里的下标，-1 = 没有。只用来显示，不真的装备 */
    private static final EntityDataAccessor<Integer> LICH_WEAPON =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.INT);
    /** 正在离场：客户端据此淡出、撤掉 Boss 条 */
    private static final EntityDataAccessor<Boolean> DEPARTING =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.BOOLEAN);
    /** 无主的野生玄骸 */
    private static final EntityDataAccessor<Boolean> WILD =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.BOOLEAN);
    /** 护盾现值和上限（原版不同步怪物的吸收值，Boss 条要画） */
    private static final EntityDataAccessor<Float> SHIELD =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHIELD_MAX =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.FLOAT);
    /** 打空了几段血（0~2），Boss 条认出倒地时正在回满的是哪一段 */
    private static final EntityDataAccessor<Byte> LICH_PHASE =
            SynchedEntityData.defineId(KuvaMasterEntity.class, EntityDataSerializers.BYTE);

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
    /** 每次攻击命中的自愈比例（没有专属面板时；有的按它的吸血档位） */
    private static final float HEAL_PER_ATTACK = 0.2f;
    /** AOE范围攻击半径（没有专属面板时；有的按它的震荡档位） */
    private static final float AOE_RADIUS = 3.0f;

    // ==================== 适应性抗性参数 ====================

    // 每类至多几层、每层减多少、多久掉一层看 LichTraits 的适应档位；没有专属面板的照中档（每层 5%、6 层、4 秒）
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

    // ==================== 专属玄骸：运行时 ====================

    /** 现身、换位后的显形时长（tick）：这段时间关 AI、不出手、不挨打，和客户端 600 ms 的显形对上 */
    public static final int APPEAR_GRACE_TICKS = 12;
    /** 离场演出开始到实体移除的 tick 数 */
    public static final int DEPART_TICKS = 8;
    /** 被别人打了以后，多久没再挨这人的打就转回去追主人（tick） */
    private static final int RETARGET_OWNER_AFTER = 200;
    /** 追主人的最远距离（格），和追踪距离加成后的 FOLLOW_RANGE 一致 */
    private static final double OWNER_HUNT_RANGE = 96.0D;
    /** 没开怜悯之击时主人离它多远以内才按主人的卡判定（格） */
    private static final double RESOLVE_RANGE = 64.0D;
    /** 离场包的 a：无主玄骸被原版自然消失截下来演的离场 */
    public static final int NATURAL_LEAVE_FX = 98;
    /** 倒地后碰撞箱高度的倍率 */
    private static final float DOWNED_HEIGHT_SCALE = 0.70F;
    /** 直接判定失败：实体留到暖色冲击和化雾演完（最长 1.78 秒） */
    private static final int FAIL_HOLD_TICKS = 38;
    /** 直接判定成功：REQUIEM 消散 1.5 秒 + 100 毫秒 */
    private static final int SUCCESS_HOLD_TICKS = 32;
    /** 直接判定失败：聊天和嘲讽等演出走完再发 */
    private static final int FAIL_CHAT_DELAY_TICKS = 60;
    /** 直接判定失败：玄骸的嘲讽（判错那一下之后约 1.3 秒） */
    private static final int FAIL_TAUNT_DELAY_TICKS = 26;
    /** 判定结果包里卡位「不知道」的值 */
    private static final int UNKNOWN_CARD = 15;

    /** 专属面板的属性修饰符：生命、攻击、护甲、击退抗性、移速（只在内存里，有主玄骸不存盘） */
    private static final UUID TRAIT_HEALTH_ID = UUID.fromString("0f5a8d7e-2c41-4f3b-9e6d-5a1c7b3e8d21");
    private static final UUID TRAIT_ATTACK_ID = UUID.fromString("7b2e4c9a-8d13-4a6f-b5e2-1c9d3f7a6e42");
    private static final UUID TRAIT_ARMOR_ID = UUID.fromString("c3d81f6b-5a27-4e9c-8b4d-2e6f1a9c7d53");
    private static final UUID TRAIT_KNOCKBACK_ID = UUID.fromString("9e6c2a4d-1b58-4d7e-a3f9-6b2d8e1c5f64");
    private static final UUID TRAIT_SPEED_ID = UUID.fromString("4a9f7e1c-6d32-4b8a-9c5e-3f7b2d6a1e75");
    private static final String TRAIT_MODIFIER_NAME = "kuvalich:lich_traits";

    /** 有主玄骸的追踪距离加成：64 + 32 = 96 */
    private static final UUID NEMESIS_FOLLOW_ID = UUID.fromString("6c1d7f0e-3b52-4b7a-9a43-2f5e8d1c0b96");
    private static final String NEMESIS_FOLLOW_NAME = "kuvalich:nemesis_follow";

    /** 显形剩余 tick，0 = 不在显形。只在内存里：有主玄骸不存盘 */
    private int appearGraceTicks;
    /** 离场剩余 tick，-1 = 没在离场 */
    private int departTicks = -1;
    /** 这一次遭遇已经按什么原因结算过；结算过的不再重复结算 */
    @Nullable
    private EncounterEnd encounterEnd;
    /** 完整的 64 位种子（同步数据里只有低 32 位）；只在服务端 */
    private long lichSeedFull;
    /** 按种子和等级算出来的战斗面板；无主的、演示用的、客户端上都是 null */
    @Nullable
    private LichTraits traits;
    /** finalizeSpawn 时记下的生成方式；不存盘，读档、带 NBT 的命令、直接加进世界的都是 null */
    @Nullable
    private MobSpawnType freshSpawnType;
    /** 读档时 NBT 里带着 KuvaWild 标记 */
    private boolean wildTagLoaded;
    /** 三刀成功的结算已经在定案那一刻做过：死的时候不再按直接击杀判一次 */
    private boolean mercySuccessApplied;

    // ==================== 分段血条与护盾 ====================

    /** 血条分几段；每打空一段倒地一次，第 k 次处决捅 k 刀 */
    public static final int SEGMENTS = 3;
    /** 打空了几段（0~2）。只在内存里：有主玄骸不存盘 */
    private int lichPhase;
    /** 连着几次倒地没人处决；有人处决过就清零 */
    private int unclaimedDowns;
    /** 护盾还要等多少 tick 才开始恢复 */
    private int shieldDelayTicks;

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

    public KuvaMasterEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // 预初始化所有伤害类别的适应层数，避免运行时computeIfAbsent创建对象
        for (DamageCategory category : DamageCategory.values()) {
            adaptiveResistance.put(category, new AdaptiveStack());
        }

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(OWNER_NAME, "");
        this.entityData.define(LICH_NAME, "");
        this.entityData.define(LICH_RANK, 0);
        this.entityData.define(LICH_SEED, 0);
        this.entityData.define(LICH_WEAPON, -1);
        this.entityData.define(DEPARTING, false);
        this.entityData.define(WILD, false);
        this.entityData.define(SHIELD, 0.0F);
        this.entityData.define(SHIELD_MAX, 0.0F);
        this.entityData.define(LICH_PHASE, (byte) 0);
    }

    // ==================== 专属玄骸：主人与身份 ====================

    /** 主人的 UUID；没有主人返回 {@code null} */
    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    /** 是不是某个玩家专属的玄骸 */
    public boolean isOwned() {
        return this.entityData.get(OWNER).isPresent();
    }

    /** 是不是无主的野生玄骸 */
    public boolean isWild() {
        return this.entityData.get(WILD);
    }

    /** 这个玩家是不是它的主人 */
    public boolean isOwner(Player player) {
        UUID owner = getOwnerUUID();
        return owner != null && owner.equals(player.getUUID());
    }

    /** 服务端：主人此刻在线时返回他；不在线或在客户端返回 {@code null} */
    @Nullable
    public ServerPlayer getOwnerPlayer() {
        UUID owner = getOwnerUUID();
        if (owner == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(owner);
    }

    /** 玄骸自己的名字，首字母大写；无主的返回空串 */
    public String getLichName() {
        return this.entityData.get(LICH_NAME);
    }

    /** 主人的名字（别人看到「某某的玄骸」用），主人离线时也能读到 */
    public String getOwnerName() {
        return this.entityData.get(OWNER_NAME);
    }

    /**
     * 玄骸名字的文本组件
     *
     * @param upper 是否转成全大写（通讯窗、Boss 条用）
     */
    public Component getLichNameComponent(boolean upper) {
        String name = getLichName();
        if (name.isEmpty()) {
            return Component.translatable("entity.kuvalich.kuva_master");
        }
        return Component.literal(upper ? name.toUpperCase(Locale.ROOT) : name);
    }

    /** 玄骸等级（kuvaLevel + 1），所有给玩家看的「玄骸等级」都用它 */
    public int getLichRank() {
        return this.entityData.get(LICH_RANK);
    }

    public int getLichSeed() {
        return this.entityData.get(LICH_SEED);
    }

    /** 手里那把武器的下标，-1 = 没有 */
    public int getLichWeaponIndex() {
        return this.entityData.get(LICH_WEAPON);
    }

    /** 这个玩家能不能处决它：只有主人，无主的一律不行。倒地、距离、朝向另外判 */
    public boolean canBeExecutedBy(Player player) {
        return isOwned() && isOwner(player);
    }

    /** 致命伤要不要先倒地：有主的倒地，无主的直接死 */
    public boolean shouldEnterDowned() {
        return isOwned();
    }

    /** 正在离场 */
    public boolean isDeparting() {
        return this.entityData.get(DEPARTING);
    }

    /**
     * 开始离场演出（服务端）：标记离场、关 AI、撤 Boss 条、发赤毒雾瞬移的效果包，{@link #DEPART_TICKS} tick 后移除。
     * 离场期间不挨打（不用 setInvulnerable：那是存盘字段）。已经在离场、已经死了的不再处理。
     *
     * <p>倒地、处决中的先交给怜悯之击处理：已经定案、三刀全对的直接快进到死（不再离场），
     * 其余的照常离场。</p>
     */
    public void beginDeparture(EncounterEnd reason) {
        if (this.level().isClientSide || this.departTicks >= 0 || this.isRemoved() || !this.isAlive()) {
            return;
        }
        if (MercyServer.interceptDeparture(this)) {
            return;
        }
        if (this.encounterEnd == null) {
            this.encounterEnd = reason;
        }
        departNow(reason.ordinal());
    }

    /**
     * 不经玄骸导演、不问怜悯之击，直接演赤毒雾离场，{@link #DEPART_TICKS} tick 后移除（无主、演示用的玄骸）。
     *
     * @param fxArg 离场包的 a：结束原因的序号，或 {@link #NATURAL_LEAVE_FX}
     */
    public void departNow(int fxArg) {
        if (this.level().isClientSide || this.departTicks >= 0 || this.isRemoved() || !this.isAlive()) {
            return;
        }
        this.entityData.set(DEPARTING, true);
        this.setNoAi(true);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.appearGraceTicks = 0;
        this.departTicks = DEPART_TICKS;
        LichDirector.onDepartureStarted(this);
        if (this.level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chestPosition(), 64.0D, FxId.LICH_DEPART, this, fxArg, 0);
        }
    }

    /**
     * 绑定主人并按他的卡写入名字、等级、种子、武器（服务端，只由玄骸导演在加进世界之前调）。
     *
     * <p>目标 AI 在这里换：{@code registerGoals()} 是在构造里调的，那时还没有主人，注册的永远是无主那一套。
     * 有主的不再自己找任意玩家、不打别的怪，只留「谁打我就回头」和「追主人」两条。
     * 寻路的节点上限是构造时按 64 格追踪距离算死的，追踪距离加到 96 格后要同时放大它，否则远处寻不到路。</p>
     */
    public void bindOwner(ServerPlayer owner, RequiemCard card) {
        card.ensureEphemeraRolled();
        this.entityData.set(OWNER, Optional.of(owner.getUUID()));
        this.entityData.set(OWNER_NAME, owner.getGameProfile().getName());
        this.entityData.set(LICH_NAME, card.getLichName());
        this.entityData.set(LICH_RANK, card.getLichRank());
        this.entityData.set(LICH_SEED, (int) card.getLichSeed());
        this.entityData.set(LICH_WEAPON, card.getLichWeapon());
        this.entityData.set(WILD, false);
        this.lichSeedFull = card.getLichSeed();
        applyTraits(LichTraits.current(this.lichSeedFull, card.getLichRank()));
        this.setPersistenceRequired();
        this.markAlerted();

        this.targetSelector.removeAllGoals(goal -> true);
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new OwnerHuntGoal(this));

        AttributeInstance follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getModifier(NEMESIS_FOLLOW_ID) == null) {
            follow.addTransientModifier(new AttributeModifier(NEMESIS_FOLLOW_ID, NEMESIS_FOLLOW_NAME, 32.0D,
                    AttributeModifier.Operation.ADDITION));
        }
        this.getNavigation().setMaxVisitedNodesMultiplier(1.5F);

        setLichPhase(0);
        this.unclaimedDowns = 0;
        this.shieldDelayTicks = 0;
        this.entityData.set(SHIELD_MAX, shieldCap());
        this.setAbsorptionAmount(shieldCap());
    }

    // ==================== 分段血条 ====================

    /** 有主的真玄骸（开着怜悯之击）血条分三段；无主的、演示用的照旧打到 0 才倒地 */
    @Override
    public boolean isSegmented() {
        return isOwned() && !isDemo() && MercyRules.enabledFor(this);
    }

    @Override
    public float segmentFloor() {
        return isSegmented() ? this.getMaxHealth() * (SEGMENTS - 1 - this.lichPhase) / SEGMENTS : 0.0F;
    }

    @Override
    public float segmentCeil() {
        // 第一段的上沿就是最大生命：按 ×3÷3 算会有浮点误差（212.16 算回来是 212.15999），新玄骸就差一丝不算满血
        if (!isSegmented() || this.lichPhase <= 0) {
            return this.getMaxHealth();
        }
        return this.getMaxHealth() * (SEGMENTS - this.lichPhase) / SEGMENTS;
    }

    /** 这次处决捅几刀、验前几张卡：打空了几段就是第几次处决；不分段的一次三刀（演示用的按设的段数） */
    public int mercyStabs() {
        return isSegmented() || isDemo() ? this.lichPhase + 1 : SEGMENTS;
    }

    /** 打空了几段（0~2）；客户端读同步数据 */
    public int getLichPhase() {
        return this.level().isClientSide ? Mth.clamp(this.entityData.get(LICH_PHASE), 0, SEGMENTS - 1) : this.lichPhase;
    }

    /** 直接设打空了几段（开发命令、测试用）；没倒地时血量压进新的这一段 */
    public void setLichPhase(int phase) {
        this.lichPhase = Mth.clamp(phase, 0, SEGMENTS - 1);
        this.entityData.set(LICH_PHASE, (byte) this.lichPhase);
        if (!this.level().isClientSide && isSegmented() && getMercyState() == MercyState.NONE) {
            this.setHealth(Mth.clamp(this.getHealth(), Math.min(segmentFloor() + 1.0F, segmentCeil()), segmentCeil()));
        }
    }

    /**
     * 这次验的几刀全对、还没到第三刀：进下一段，血量回到下一段的顶（不分段的回满），护盾回满
     */
    public void advanceSegment() {
        setLichPhase(this.lichPhase + 1);
        this.setHealth(isSegmented() ? segmentCeil() : this.getMaxHealth());
        this.shieldDelayTicks = 0;
        this.setAbsorptionAmount(shieldCap());
    }

    /** 倒地到时没人处决：这段血回满 */
    public void refillSegment() {
        this.setHealth(segmentCeil());
    }

    /** 又一次倒地没人处决，返回连着第几次 */
    public int noteUnclaimedDown() {
        return ++this.unclaimedDowns;
    }

    public void clearUnclaimedDowns() {
        this.unclaimedDowns = 0;
    }

    public int getUnclaimedDowns() {
        return this.unclaimedDowns;
    }

    // ==================== 护盾 ====================

    /** 护盾上限：有主的真玄骸是最大生命 × 配置倍数，其余没有 */
    public float shieldCap() {
        if (!isOwned() || isDemo()) {
            return 0.0F;
        }
        return (float) (this.getMaxHealth() * ModConfig.KUVA_LICH.lichShieldRatio.get());
    }

    /** 护盾现值；客户端读同步数据 */
    public float getShield() {
        return this.level().isClientSide ? this.entityData.get(SHIELD) : this.getAbsorptionAmount();
    }

    /** 护盾上限；客户端读同步数据 */
    public float getShieldMax() {
        return this.level().isClientSide ? this.entityData.get(SHIELD_MAX) : shieldCap();
    }

    /** 护盾就是原版的吸收值：伤害先扣它、磁力伤害认它；改了顺手写进同步数据 */
    @Override
    public void setAbsorptionAmount(float amount) {
        super.setAbsorptionAmount(amount);
        if (!this.level().isClientSide) {
            this.entityData.set(SHIELD, this.getAbsorptionAmount());
        }
    }

    /** 打掉护盾、这么多 tick 内不恢复（开发命令、测试用） */
    public void breakShield(int holdTicks) {
        this.setAbsorptionAmount(0.0F);
        this.shieldDelayTicks = Math.max(0, holdTicks);
    }

    /** 护盾还要等多少 tick 才开始恢复（测试用） */
    public int getShieldDelayTicks() {
        return this.shieldDelayTicks;
    }

    /**
     * 挨了打：护盾的恢复重新等。规则和玩家的基础护盾一样，这一下之后护盾空了，等的时间乘倍数
     */
    @Override
    protected void actuallyHurt(@NotNull DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        if (this.level().isClientSide || shieldCap() <= 0.0F) {
            return;
        }
        int delay = ModConfig.KUVA_LICH.lichShieldDelaySeconds.get() * 20;
        if (this.getAbsorptionAmount() <= 0.0F) {
            delay = (int) Math.round(delay * ModConfig.KUVA_LICH.lichShieldBrokenDelayMultiplier.get());
        }
        this.shieldDelayTicks = delay;
    }

    /** 每 tick：等待倒数；能打的时候每秒回一截，超过上限的压回上限 */
    private void tickShield() {
        float cap = shieldCap();
        this.entityData.set(SHIELD_MAX, cap);
        float shield = this.getAbsorptionAmount();
        if (shield > cap) {
            this.setAbsorptionAmount(cap);
            return;
        }
        if (this.shieldDelayTicks > 0) {
            this.shieldDelayTicks--;
            return;
        }
        if (shield < cap && getMercyState() == MercyState.NONE && this.tickCount % 20 == 0) {
            float step = (float) (cap * ModConfig.KUVA_LICH.lichShieldRegenPercent.get() / 100.0D);
            this.setAbsorptionAmount(Math.min(cap, shield + step));
        }
    }

    /** 等级变了（处决失败等）时把同步数据和面板跟上 */
    public void refreshRank(RequiemCard card) {
        this.entityData.set(LICH_RANK, card.getLichRank());
        if (this.traits != null) {
            applyTraits(LichTraits.current(this.lichSeedFull, card.getLichRank()));
        }
    }

    /**
     * 换上一套面板：生命、攻击、护甲、击退抗性、移速用临时修饰符改（基础值仍是 {@link #createAttributes}），
     * 血量按原来的比例跟着最大生命缩放
     */
    private void applyTraits(LichTraits next) {
        float ratio = this.getMaxHealth() > 0.0F ? this.getHealth() / this.getMaxHealth() : 1.0F;
        this.traits = next;
        setTraitModifier(Attributes.MAX_HEALTH, TRAIT_HEALTH_ID, next.maxHealth - LichTraits.BASE_HEALTH,
                AttributeModifier.Operation.ADDITION);
        setTraitModifier(Attributes.ATTACK_DAMAGE, TRAIT_ATTACK_ID, next.attack - LichTraits.BASE_ATTACK,
                AttributeModifier.Operation.ADDITION);
        setTraitModifier(Attributes.ARMOR, TRAIT_ARMOR_ID, next.armor - LichTraits.BASE_ARMOR,
                AttributeModifier.Operation.ADDITION);
        setTraitModifier(Attributes.KNOCKBACK_RESISTANCE, TRAIT_KNOCKBACK_ID,
                next.knockbackResist - LichTraits.BASE_KNOCKBACK_RESIST, AttributeModifier.Operation.ADDITION);
        setTraitModifier(Attributes.MOVEMENT_SPEED, TRAIT_SPEED_ID, next.speedScale - 1.0D,
                AttributeModifier.Operation.MULTIPLY_BASE);
        this.setHealth(Math.max(1.0F, this.getMaxHealth() * Mth.clamp(ratio, 0.0F, 1.0F)));
    }

    private void setTraitModifier(Attribute attribute, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0E-6D) {
            instance.addTransientModifier(new AttributeModifier(id, TRAIT_MODIFIER_NAME, amount, op));
        }
    }

    /** 专属玄骸的战斗面板（服务端）；无主的、演示用的为 null */
    @Override
    @Nullable
    public LichTraits combatTraits() {
        return this.traits;
    }

    /** 一刀的伤害（服务端算法）：攻击 × 难度序号 × 0.85，不含交战增幅和玩家身上的等级加成 */
    public static double hitDamage(double attack, Difficulty difficulty) {
        return attack * difficulty.getId() * ATTACK_DAMAGE_MULTIPLIER;
    }

    /**
     * 开始显形：{@code ticks} 个 tick 内关 AI、不出手、不挨打（/kill、真掉出世界除外，见 {@link #isHardDeath}）。
     * 狩猎到场、换位、三种立即召唤都走这一步
     */
    public void beginAppearGrace(int ticks) {
        if (this.departTicks >= 0) {
            return;
        }
        this.appearGraceTicks = Math.max(this.appearGraceTicks, ticks);
        this.setNoAi(true);
        this.getNavigation().stop();
    }

    /** 还在显形 */
    public boolean isAppearing() {
        return this.appearGraceTicks > 0;
    }

    /** 这次遭遇已经按什么原因结算过，没有为 null */
    @Nullable
    public EncounterEnd getEncounterEnd() {
        return this.encounterEnd;
    }

    /** 由玄骸导演在结算时写入，防止同一次遭遇被结算两次 */
    public void setEncounterEnd(EncounterEnd reason) {
        if (this.encounterEnd == null) {
            this.encounterEnd = reason;
        }
    }

    /** 标记成无主的野生玄骸（刷怪蛋、命令、别的模组直接加进世界的） */
    public void markWild() {
        this.entityData.set(WILD, true);
    }

    /** 读档时带着 KuvaWild 标记（以后存下来的野生玄骸）；没有的是以前自然生成的 */
    public boolean hasWildTag() {
        return this.wildTagLoaded;
    }

    /** finalizeSpawn 记下的生成方式；没经过 finalizeSpawn 的为 null */
    @Nullable
    public MobSpawnType getFreshSpawnType() {
        return this.freshSpawnType;
    }

    private Vec3 chestPosition() {
        return position().add(0.0D, getBbHeight() * 0.62D, 0.0D);
    }

    /**
     * 给演示用的玄骸写上名字和等级，不绑主人
     */
    public void setDemoIdentity(String name, int rank) {
        this.entityData.set(LICH_NAME, name);
        this.entityData.set(LICH_RANK, rank);
    }

    /** 演示玄骸手里拿哪把武器（只管画面；掉落照旧看主人的档案） */
    public void setDemoWeapon(int weaponIndex) {
        this.entityData.set(LICH_WEAPON, weaponIndex);
    }

    /**
     * 创建玄骸属性
     *
     * @return 属性构建器
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, LichTraits.BASE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, LichTraits.BASE_ATTACK)
                .add(Attributes.KNOCKBACK_RESISTANCE, LichTraits.BASE_KNOCKBACK_RESIST)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ARMOR, LichTraits.BASE_ARMOR);
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
        int layersBefore = adaptiveResistance.get(category).stacks;
        damage = applyAdaptiveResistance(category, currentTick, damage);
        int layers = adaptiveResistance.get(category).stacks;
        KuvaStateServer.onAdapt(this, source, category, layers, layers > layersBefore);

        // 第二层：连击递减（短时间内高频攻击递减）
        float beforeGuard = damage;
        damage = applyBurstDiminish(currentTick, damage);
        float afterBurst = damage;

        // 第三层：单次伤害上限（对数软上限防秒杀）
        damage = applySoftDamageCap(damage);
        KuvaStateServer.onGuard(this, source, beforeGuard, afterBurst, damage);

        // 记录本次受击时间（用于全局重置判断）
        lastAnyHitTick = currentTick;

        return damage;
    }

    /**
     * 第一层：适应性抗性
     * 先用当前层数计算减伤，再叠一层
     * 第一击无减伤（0层），后续同类伤害逐渐增强抗性
     * 每层多少、至多几层看适应档位（中档每层 5%、满 6 层 30%）
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
        float resistance = stack.stacks * adaptPercent() / 100.0F;
        damage *= (1.0f - resistance);

        // 再叠一层（为下次同类伤害准备）
        if (stack.stacks < adaptStackCap()) {
            stack.stacks++;
        }
        stack.lastHitTick = currentTick;
        syncAdaptPacked();

        return damage;
    }

    /** 把各类适应层数压进同步数据（每类 3 位，按伤害类别序号 × 3 排），客户端画状态行用；值没变时不会发包 */
    private void syncAdaptPacked() {
        int packed = 0;
        for (Map.Entry<DamageCategory, AdaptiveStack> entry : adaptiveResistance.entrySet()) {
            int stacks = Math.max(0, Math.min(7, entry.getValue().stacks));
            packed |= stacks << (entry.getKey().ordinal() * 3);
        }
        setAdaptPacked(packed);
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
     * - 单类一段时间无命中 → 该类逐层衰减（间隔看适应档位，中档 4 秒）
     */
    @Override
    protected void tickResistance() {
        long currentTick = this.level().getGameTime();

        // 全局重置：10秒未受到任何伤害，清空所有适应层数
        if (lastAnyHitTick > 0 && currentTick - lastAnyHitTick > FULL_RESET_TICKS) {
            for (Map.Entry<DamageCategory, AdaptiveStack> entry : adaptiveResistance.entrySet()) {
                entry.getValue().stacks = 0;
                KuvaStateServer.onAdaptDecayed(this, entry.getKey(), 0);
            }
            syncAdaptPacked();
            return;
        }

        // 单类衰减：某类伤害一段时间内无命中，每隔这么久衰减1层
        int decayTicks = this.traits != null ? this.traits.adaptDecayTicks()
                : LichTraits.ADAPT_DECAY_TICKS[LichTraits.NORMAL];
        for (Map.Entry<DamageCategory, AdaptiveStack> entry : adaptiveResistance.entrySet()) {
            AdaptiveStack stack = entry.getValue();
            if (stack.stacks > 0 && currentTick - stack.lastHitTick > decayTicks) {
                stack.stacks--;
                // 重置该类的计时器，下一层衰减需要再等一个间隔
                stack.lastHitTick = currentTick;
                KuvaStateServer.onAdaptDecayed(this, entry.getKey(), stack.stacks);
            }
        }
        syncAdaptPacked();
    }

    // ==================== 回血 ====================

    /** 每层适应减多少（百分数） */
    private int adaptPercent() {
        return this.traits != null ? this.traits.adaptPercent() : LichTraits.ADAPT_PCT[LichTraits.NORMAL];
    }

    @Override
    protected float getTickHeal() {
        return Math.max(MIN_HEAL_NO_TARGET, (this.getMaxHealth() - this.getHealth()) * BASE_HEAL_MULTIPLIER)
                * regenScale();
    }

    @Override
    protected float getHasTargetTickHeal() {
        return Math.max(MIN_HEAL_HAS_TARGET, (this.getMaxHealth() - this.getHealth()) * TARGET_HEAL_MULTIPLIER)
                * regenScale();
    }

    private float regenScale() {
        return this.traits != null ? (float) this.traits.regenScale() : 1.0F;
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
        // 挥砍声、命中声都由客户端按挥臂和实体事件 103 放，服务端不再直接放
        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float finalDamage = (float) hitDamage(attackDamage, this.level().getDifficulty());
        float lifesteal = this.traits != null ? (float) this.traits.lifesteal() : HEAL_PER_ATTACK;

        int swept = attackNearbyEntities(finalDamage, target);

        if (target.hurt(this.damageSources().mobAttack(this), finalDamage)) {
            applyKnockback(target);
            float before = this.getHealth();
            // 吸血是专长时，震荡扫到的每一个也回一份
            int heals = this.traits != null && this.traits.isSignature(LichTraits.LIFESTEAL) ? 1 + swept : 1;
            this.heal(attackDamage * lifesteal * heals);
            this.level().broadcastEntityEvent(this, EV_HIT);
            KuvaStateServer.onStrike(this, target, this.getHealth() - before);
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
     * @return 扫到并打中了几个
     */
    private int attackNearbyEntities(float damage, Entity excludeEntity) {
        double radius = this.traits != null ? this.traits.quakeRadius() : AOE_RADIUS;
        AABB aabb = this.getBoundingBox().inflate(radius);
        List<LivingEntity> nearbyEntities = this.level().getEntitiesOfClass(
                LivingEntity.class,
                aabb,
                entity -> !(entity instanceof AbstractKuva) && !entity.equals(excludeEntity)
        );

        int hit = 0;
        for (LivingEntity nearby : nearbyEntities) {
            if (nearby.hurt(this.damageSources().mobAttack(this), damage)) {
                applyKnockback(nearby);
                hit++;
            }
        }
        return hit;
    }

    /**
     * 对目标和自身施加击退效果
     * 先保存目标原始速度，避免计算互相干扰
     *
     * @param target 被击退的目标
     */
    private void applyKnockback(Entity target) {
        Vec3 originalTargetVelocity = target.getDeltaMovement();
        double knock = this.traits != null ? this.traits.quakeKnock() : 1.0D;

        target.setDeltaMovement(
                originalTargetVelocity.x * KNOCKBACK_MULTIPLIER * knock,
                originalTargetVelocity.y * VERTICAL_KNOCKBACK * knock,
                originalTargetVelocity.z * KNOCKBACK_MULTIPLIER * knock
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
     * <p>无主的（刷怪蛋、命令造出来的、演示用的）什么都不给：不判定、不给进度、不掉东西。
     * 有主的：怜悯之击开着时，只有处决走完的那一下才结算（三刀全对的在定案那一刻已经结算过，这里跳过）；
     * 绕过倒地的死亡（{@code /kill}、虚空、别的模组秒杀）按「被别的东西打死」结束这次遭遇，什么都不给。
     * 怜悯之击关着时照旧在死的这一刻按主人的卡直接判定。</p>
     */
    @Override
    protected void onKilled(DamageSource source) {
        if (!isOwned()) {
            return;
        }
        if (isMercyFinishing() && this.mercySuccessApplied) {
            return;
        }
        if (!isMercyFinishing() && MercyRules.enabledFor(this)) {
            LichDirector.endEncounter(getOwnerPlayer(), this, EncounterEnd.KILLED_OTHER, false);
            return;
        }
        resolveForOwner(source.getEntity());
    }

    /** 没有别的路发过死亡包时的默认那个：处决收尾 b = 1，其余 b = 0（三刀成功的收尾自己发 b = 2，不走这里） */
    @Override
    protected void sendDeathFx(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel) {
            Entity killer = source.getEntity();
            KuvaFx.near(serverLevel, chestPosition(), 48.0D, FxId.LICH_DEATH, this,
                    killer instanceof Player ? killer.getId() + 1 : 0, isMercyFinishing() ? 1 : 0);
        }
    }

    /** 有主的才掉原版战利品表的东西（怜悯之击那一层的条件在父类里） */
    @Override
    protected boolean shouldDropLoot() {
        return isOwned() && super.shouldDropLoot();
    }

    /** 同 {@link #resolveForOwner(Entity)}，不知道是谁打的最后一击 */
    public void resolveForOwner() {
        resolveForOwner(null);
    }

    /**
     * 玄骸死了：按<b>主人</b>的卡判定，谁打出最后一击不重要（帮打的人不吃亏，主人也不会被别人「代捅」掉三张卡）。
     *
     * <p>主人不在线、不在同一个世界、或离它超过 64 格：这次遭遇按「被别的东西打死」结束，不判定、不给进度。
     * 三张卡放齐了就判定并记一行处决记录（直接判定，不是怜悯之击）；没放齐只给主人解密进度。</p>
     *
     * @param killer 打出最后一击的实体（效果包里的朝向用），不知道为 null
     */
    public void resolveForOwner(@Nullable Entity killer) {
        if (level().isClientSide) {
            return;
        }
        ServerPlayer owner = getOwnerPlayer();
        RequiemCard card = owner == null ? null
                : owner.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (owner == null || card == null || owner.level() != level()
                || owner.distanceToSqr(this) > RESOLVE_RANGE * RESOLVE_RANGE) {
            LichDirector.endEncounter(owner, this, EncounterEnd.KILLED_OTHER, false);
            return;
        }

        int requiemArg = (killer instanceof Player ? killer : owner).getId() + 1;
        if (card.isReadyCard() && !isMercyFinishing()) {
            int prefix = card.correctPrefixLength();
            LichDirector.onMercyResult(owner, this, packDirectAttempt(card, prefix), prefix >= 3);
            if (card.isCorrectAnswer()) {
                directSuccess(owner, card, requiemArg);
            } else {
                directFailure(owner, card, prefix, requiemArg);
            }
            return;
        }
        giveDecryptionProgress(owner, card);
        LichDirector.endEncounter(owner, this, EncounterEnd.KILLED_OTHER, false);
        if (level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chestPosition(), 48.0D, FxId.LICH_DEATH, this,
                    killer instanceof Player ? killer.getId() + 1 : 0, 1);
        }
        this.deathFxSent = true;
    }

    /** 击杀时直接判定的那一行记录：按位置顺序、遇错即停，错的那一位之后记「没判到」 */
    private static int packDirectAttempt(RequiemCard card, int prefix) {
        int[] ids = placedCardIds(card);
        byte[] results = new byte[3];
        for (int i = 0; i < 3; i++) {
            if (i < prefix) {
                results[i] = RequiemCard.ATTEMPT_RIGHT;
            } else if (i == prefix) {
                results[i] = RequiemCard.ATTEMPT_WRONG;
            } else {
                results[i] = RequiemCard.ATTEMPT_UNJUDGED;
            }
        }
        return RequiemCard.packAttempt(ids, results, false);
    }

    /** 三个卡位上放的卡 id，空位为 -1 */
    private static int[] placedCardIds(RequiemCard card) {
        ItemStack[] placed = {card.getOneCard(), card.getTwoCard(), card.getThreeCard()};
        int[] ids = new int[3];
        for (int i = 0; i < 3; i++) {
            ids[i] = placed[i] != null && placed[i].getItem() instanceof AbstractRequiemCard c ? c.getID() : -1;
        }
        return ids;
    }

    /** 判定结果包 b 的 bit8~19：三张卡的 id，每张 4 位，空位和认不出的记 15 */
    private static int cardIdBits(RequiemCard card) {
        int[] ids = placedCardIds(card);
        int bits = 0;
        for (int i = 0; i < 3; i++) {
            int id = ids[i] >= 0 && ids[i] < UNKNOWN_CARD ? ids[i] : UNKNOWN_CARD;
            bits |= id << (8 + 4 * i);
        }
        return bits;
    }

    /**
     * 直接判定成功：演出先行，效果包一开头就发，客户端 420 ms 时晶核碎裂。定案和落定都当场做（没有演出就没有选择，
     * 一律按消灭），聊天和投递放到碎裂之后（+12 tick），HUD 的完成提示再晚一点（+18 tick），
     * 免得东西先进背包、聊天栏先刷四五行，然后才是碎裂那一声
     */
    private void directSuccess(ServerPlayer owner, RequiemCard card, int requiemArg) {
        int fxFlags = 0;
        if (card.hasConfiscatedItems()) {
            fxFlags |= 1 << 1;
        }
        fxFlags |= Math.min(3, countCardsWornOut(card)) << 2;
        fxFlags |= cardIdBits(card);
        if (level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chestPosition(), 64.0D, FxId.REQUIEM_SUCCESS, this, requiemArg, fxFlags);
        }
        this.deathFxSent = true;
        holdDeathTicks(SUCCESS_HOLD_TICKS);

        SuccessResult result;
        try {
            result = computeAndApplySuccess(owner, card, this.blockPosition());
        } catch (RuntimeException e) {
            // 定案先算后改，抛在这里卡上什么都没动，玄骸下次还会来
            LogUtil.error("直接判定成功时定案出错", e);
            return;
        }
        FinaleSettlement.Settled settled = null;
        try {
            settled = FinaleSettlement.resolve(owner, result.generation(), FinaleSettlement.Choice.VANQUISH);
        } catch (RuntimeException e) {
            LogUtil.error("直接判定成功时落定出错，账留着等巡检或登录时补", e);
        }
        FinaleSettlement.Settled announce = settled;
        ServerFxQueue.schedule(REWARD_DELAY_TICKS, owner, () -> {
            result.sayLastWords(owner);
            if (announce != null) {
                announce.announce(owner);
            }
        });
        ServerFxQueue.schedule(COMPLETE_DELAY_TICKS, owner, () -> {
            if (!owner.hasDisconnected()) {
                DecryptionHudPacket.sendComplete(owner, result.riddles(), result.usesAfter(), result.lichName());
            }
        });
    }

    /**
     * 直接判定失败：发暖色冲击（REQUIEM_FAIL），玄骸升一级、给主人发升级横幅，进度先给（遭遇还没结束，怒气不跟着涨），
     * 再结束遭遇；猜对几位、升级的聊天和玄骸的嘲讽等冲击和化雾演完再发
     */
    private void directFailure(ServerPlayer owner, RequiemCard card, int prefix, int requiemArg) {
        if (level() instanceof ServerLevel serverLevel) {
            int b = Math.min(3, prefix) | cardIdBits(card);
            KuvaFx.near(serverLevel, chestPosition(), 64.0D, FxId.REQUIEM_FAIL, this, requiemArg, b);
        }
        this.deathFxSent = true;
        holdDeathTicks(FAIL_HOLD_TICKS);

        boolean firstCorrect = card.isFirstCorrectAnswer();
        boolean secondCorrect = firstCorrect && card.isTwoCorrectAnswer();
        int rank = applyFailure(card);
        KuvaFx.player(owner, FxId.RANK_UP, chestPosition(), this.getId(), rank, (prefix + 1) << 1);

        giveDecryptionProgress(owner, card);
        LichDirector.endEncounter(owner, this, EncounterEnd.MERCY_FAILED, false);

        // 嘲讽在判错那一下之后约 1.3 秒（冲击前后），聊天再晚一些，别和嘲讽抢
        ServerFxQueue.schedule(FAIL_TAUNT_DELAY_TICKS, owner, () -> {
            if (!owner.hasDisconnected()) {
                LichComms.say(owner, LichLine.FAIL);
            }
        });
        ServerFxQueue.schedule(FAIL_CHAT_DELAY_TICKS, owner, () -> {
            if (owner.hasDisconnected()) {
                return;
            }
            if (firstCorrect) {
                // 猜对一部分是半好半坏，用 WARN 与整条失败的 DANGER 分开
                PlayerFeedback.chat(owner, KuvaPalette.WARN, "message.kuvalich.firstCorrect");
                if (secondCorrect) {
                    PlayerFeedback.chat(owner, KuvaPalette.WARN, "message.kuvalich.secondCorrect");
                }
            }
            // 给玩家看的「玄骸等级」一律是 kuvaLevel + 1，和档案、Boss 条对得上
            PlayerFeedback.chat(owner, KuvaPalette.DANGER, "message.kuvalich.failedToDecrypt", rank);
        });
    }

    /**
     * 判错一次：玄骸等级 +1，同步数据跟上
     *
     * @return 新的玄骸等级（kuvaLevel + 1）
     */
    public int applyFailure(RequiemCard card) {
        card.setKuvaLevel(card.getKuvaLevel() + 1);
        refreshRank(card);
        return card.getLichRank();
    }

    /**
     * 三刀全对的定案：算好全部掉落、扣卡、终结这一轮，把要给玩家的东西整份记进玩家账本（{@link PlayerLedger}），
     * 什么都不发。东西由 {@link FinaleSettlement#resolve} 落定后才进收件箱。直接判定和怜悯之击都走这里。
     * 调用前调用方已经记过那一行处决记录（这一轮结束会清掉记录）。
     *
     * <p>先算后改：会抛的（补掷幻纹、造掉落、落定旧账、造账并序列化）都在动卡之前，抛出去时卡上什么都没变，玄骸还在。</p>
     *
     * @param player 主人
     * @param card   主人的卡（判定完开始新一轮，扉上的卡扣过一次留着）
     * @param pos    玄骸倒下的位置（记进账里）
     */
    public SuccessResult computeAndApplySuccess(ServerPlayer player, RequiemCard card, BlockPos pos) {
        Finale stale = PlayerLedger.getFinale(player);
        if (stale != null) {
            FinaleSettlement.resolveNow(player, stale.generation(), FinaleSettlement.Choice.VANQUISH);
            if (PlayerLedger.hasFinale(player)) {
                throw new IllegalStateException(player.getGameProfile().getName() + " 的旧账落定不了，这次不定案");
            }
        }

        int wornOut = countCardsWornOut(card);
        // 完成提示要显示三枚密语和名字，卡马上要重置，先取快照
        byte[] riddles = {(byte) card.getOneRiddle(), (byte) card.getTwoRiddle(), (byte) card.getThreeRiddle()};
        String lichName = getLichName();
        int generation = card.getLichGeneration();
        long seed = card.getLichSeed();
        int rank = card.getLichRank();
        // 结束这一轮会清掉幻纹，快照要在那之前取；幻纹出现之前诞生、还没补掷过的这里补
        card.ensureEphemeraRolled();
        String ephemera = card.getLichEphemera();

        // 手里那把就是成功时掉的那把：结束这一轮会清掉武器下标和它定下的等级、元素，所以先取
        int weaponIndex = card.getLichWeapon();
        int weaponLevel = card.getLichWeaponLevel();
        String weaponType = card.getLichWeaponType();
        Item weapon = weaponIndex >= 0 && weaponIndex < KuvaLichItems.KUVA_WEAPONS.size()
                ? KuvaLichItems.KUVA_WEAPONS.get(weaponIndex)
                : KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1));

        List<ItemStack> drops = new ArrayList<>();
        // 诞生时定下的等级和元素（幼体倒地时亮出来的就是这些）；老存档里没定过的照旧在武器等级范围里随机
        drops.add(weaponLevel > 0 && !weaponType.isEmpty()
                ? KuvaWeaponUtil.getItem(weapon, weaponType, weaponLevel)
                : KuvaWeaponUtil.getItem(weapon, card.getMinimumLevelWeapon(), card.getMaximumLevelWeapon()));

        drops.add(new ItemStack(KuvaLichItems.LICH_RELIQUARY.get()));

        drops.add(new ItemStack(
                KuvaLichItems.RIVEN_SLIVER.get(),
                RandomUtil.getInt(
                        ModConfig.KUVA_LICH.masterRivenSliverMinAmount.get(),
                        ModConfig.KUVA_LICH.masterRivenSliverMaxAmount.get()
                )
        ));

        drops.add(new ItemStack(
                KuvaLichItems.KUVA.get(),
                RandomUtil.getInt(
                        ModConfig.KUVA_LICH.masterKuvaMinAmount.get(),
                        ModConfig.KUVA_LICH.masterKuvaMaxAmount.get()
                )
        ));

        drops.add(rollAdvancedModule());

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.requiemUltimatumDropChance.get())) {
            drops.add(new ItemStack(KuvaLichItems.REQUIEM_ULTIMATUM.get(), 1));
        }

        String dim = level().dimension().location().toString();

        // 拆堆和存 NBT 会跑到别的模组的物品代码，账要在动卡之前整份造好
        List<ItemStack> returns = card.getConfiscatedItems();
        CompoundTag finale = new Finale(generation, seed, lichName, rank, KuvaLichItems.KUVA_WEAPONS.indexOf(weapon),
                weaponLevel, weaponType, ephemera, drops, returns, dim, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)
                .save();

        // 以下只剩改卡和写账，不能再加会抛的调用
        // 主人开着灭骸之扉（玄骸被同伴 / 别的东西打死时可能开着）：先关，不然卡槽会把扣掉的卡写回去
        RequiemGateMenu.closeBeforeCardsChange(player);
        this.mercySuccessApplied = true;
        // 卡原地扣一次、留在扉上原来那一格，用完的消失；不再退回背包。完成提示要按密语的显示顺序画每张卡剩几次，
        // 答案马上要随 resetKeepingCards 清掉，先按「这一格的答案是哪枚密语」对好
        int[] answers = {card.getOneAnswer(), card.getTwoAnswer(), card.getThreeAnswer()};
        int[] usesByGate = card.wearPlacedCards();
        byte[] usesAfter = usesByRiddle(riddles, answers, usesByGate);
        card.clearAndGetConfiscatedItems();
        // 这一轮结束要在 resetKeepingCards 之前：历史里记的等级来自 kuvaLevel，它会把它清零
        LichDirector.onVanquished(player, this);
        card.resetKeepingCards();
        PlayerLedger.putFinaleTag(player, finale);

        return new SuccessResult(generation, this, riddles, usesAfter, lichName, returns.size(), wornOut);
    }

    /**
     * 把按扉上位置排的「扣完剩几次」换成按密语显示顺序排：显示第 i 枚的密语是第 j 格的答案，就取第 j 格的卡
     * （放的是 Oull 也一样，那张 Oull 就是顶这一枚的卡）。对不上的记 {@link RequiemCard#USES_NO_CARD}
     */
    private static byte[] usesByRiddle(byte[] riddles, int[] answers, int[] usesByGate) {
        byte[] out = new byte[3];
        for (int i = 0; i < 3; i++) {
            out[i] = (byte) RequiemCard.USES_NO_CARD;
            for (int j = 0; j < 3; j++) {
                if (riddles[i] >= 0 && answers[j] == riddles[i]) {
                    out[i] = (byte) usesByGate[j];
                    break;
                }
            }
        }
        return out;
    }

    /**
     * 定案之后的演出要用的快照；要发的东西在账上，不在这里
     *
     * @param generation   这只的代数，落定时核账用
     * @param lich         说遗言的那只实体（这时主人卡上的这一轮已经结束、名字清空了）
     * @param riddles      三枚密语的快照，完成提示用
     * @param usesAfter    三枚密语（同 {@code riddles} 的顺序）各自那张卡扣完以后剩几次，0 = 用完消失，
     *                     {@link RequiemCard#USES_NO_CARD} = 没有对应的卡
     * @param lichName     玄骸名字的快照
     * @param returnCount  归还的没收物品件数
     * @param cardsWornOut 用完消失的安魂卡张数
     */
    public record SuccessResult(int generation, KuvaMasterEntity lich, byte[] riddles, byte[] usesAfter, String lichName,
                                int returnCount, int cardsWornOut) {

        /** 遗言：主人在线才说 */
        public void sayLastWords(@Nullable ServerPlayer owner) {
            if (owner != null && !owner.hasDisconnected()) {
                lich.sendDeathMessage(owner);
            }
        }
    }

    /** 安魂成功后聊天和掉落延后的 tick 数（客户端晶核在 420 ms 碎裂，之后 180 ms） */
    private static final int REWARD_DELAY_TICKS = 12;
    /** 安魂成功后 HUD 完成提示延后的 tick 数 */
    private static final int COMPLETE_DELAY_TICKS = 18;

    /**
     * 这次判定会用完消失几张安魂卡（只读，和 {@link RequiemCard#wearPlacedCards} 的判据一致：
     * 不可损耗的卡用一次就没，可损耗的再磨一点就到上限的也没）
     */
    private static int countCardsWornOut(RequiemCard requiemCard) {
        int n = 0;
        for (ItemStack card : new ItemStack[]{requiemCard.getOneCard(), requiemCard.getTwoCard(), requiemCard.getThreeCard()}) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            if (!card.isDamageableItem() || card.getDamageValue() + 1 >= card.getMaxDamage()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 玄骸的遗言：走通讯窗（带聊天回显），说话的是这只实体本身——这时主人卡上的这一轮已经结束、名字清空了。
     * 通讯没能发出去时退回聊天栏，从同一套遗言里随便挑一句（按这只的性格）
     *
     * @param player 玩家
     */
    private void sendDeathMessage(Player player) {
        if (player instanceof ServerPlayer serverPlayer && LichComms.say(serverPlayer, LichLine.VANQUISHED, this)) {
            return;
        }
        LichLine line = LichLine.VANQUISHED;
        String messageKey = line.key(LichPersona.of(getLichSeed()), RandomUtil.getInt(0, line.count() - 1));
        PlayerFeedback.chat(player, KuvaPalette.LICH, messageKey, getLichName(), getOwnerName(), "");
    }

    /**
     * 掷一个高级模组（Prime或裂罅）
     * 统一使用RandomUtil
     */
    private ItemStack rollAdvancedModule() {
        int weaponRatio = ModConfig.KUVA_LICH.moduleWeaponRatio.get();

        if (RandomUtil.percentageChance(ModConfig.KUVA_LICH.masterPrimeModuleChance.get())) {
            return RandomUtil.percentageChance(weaponRatio)
                    ? WeaponPrimeModule.getRandomModule()
                    : WarframePrimeModule.getRandomModule();
        }
        return RandomUtil.percentageChance(weaponRatio)
                ? WeaponRivenModule.getRandomModule()
                : WarframeRivenModule.getRandomModule();
    }

    /**
     * 给予安魂密语解密进度（玄骸给的进度比奴仆多）
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
     */
    public void giveDecryptionProgress(Player player, RequiemCard requiemCard) {
        int addPotion = RandomUtil.getInt(
                (int) (ModConfig.KUVA_LICH.minDecryptionProgress.get() * ModConfig.KUVA_LICH.masterPotionMultiplier.get()),
                (int) (ModConfig.KUVA_LICH.maxDecryptionProgress.get() * ModConfig.KUVA_LICH.masterPotionMultiplier.get())
        );

        // 记录加成前的阶段，用于判断本次是否揭示了新线索
        int levelBefore = requiemCard.getUnlockedCardStatus();

        if (requiemCard.addPotion(addPotion)) {
            // 同步顶部解密HUD（带过渡动画；阶段推进时触发揭示提示）
            // 顶部解密 HUD 已经把这次加了多少、现在多少写清楚了，不再另发动作栏
            DecryptionHudPacket.sendProgress(player, requiemCard, addPotion, levelBefore);
            if (player instanceof ServerPlayer serverPlayer) {
                LichDirector.onDecryptionProgress(serverPlayer, requiemCard, addPotion, levelBefore);
            }
        } else {
            // 已满级（线索全部揭示）：进度未变化，但仍补发一个 added=0 的进度包，
            // 使顶部HUD在满级后继续击杀时也能弹出（显示满进度条与提示，不触发闪光）
            DecryptionHudPacket.sendProgress(player, requiemCard, 0, levelBefore);
            if (player instanceof ServerPlayer serverPlayer) {
                LichDirector.onDecryptionProgress(serverPlayer, requiemCard, 0, levelBefore);
            }
        }
    }

    // ==================== 怜悯之击：碰撞箱、自然消失、声音 ====================

    @Override
    protected float downedHeightScale() {
        return DOWNED_HEIGHT_SCALE;
    }

    /**
     * 原版自然消失被截下来时演的离场：有主的只会在和平难度遇到，按「和平难度」结束这次遭遇；
     * 无主的、演示用的直接演赤毒雾离开
     */
    @Override
    protected boolean startNaturalLeave() {
        if (isOwned() && !isDemo()) {
            LichDirector.endEncounter(getOwnerPlayer(), this, EncounterEnd.PEACEFUL, true);
            return true;
        }
        setMercyState(MercyState.LEAVING);
        departNow(NATURAL_LEAVE_FX);
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return KuvaLichSounds.ENTITY_LICH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return KuvaLichSounds.ENTITY_LICH_HURT.get();
    }

    /** 玄骸的嗓门比原版怪物大：环境声、受击声都传得更远 */
    @Override
    protected float getSoundVolume() {
        return 1.5F;
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
        // 有主的根本不存盘（shouldBeSaved），存下来的只会是无主的；带上标记，读档时和以前自然生成的区分开
        if (isWild()) {
            compound.putBoolean("KuvaWild", true);
        }
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
        syncAdaptPacked();
        // 主人只能由玄骸导演绑定，NBT 里的任何主人信息一律不认
        this.wildTagLoaded = compound.getBoolean("KuvaWild");
        if (this.wildTagLoaded) {
            markWild();
        }
    }

    // ==================== 专属玄骸：生命周期 ====================

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance difficulty,
                                        @NotNull MobSpawnType spawnType, @Nullable SpawnGroupData groupData,
                                        @Nullable CompoundTag tag) {
        // 记下生成方式，加入世界时据此拦掉刷怪笼、自然生成等来源
        this.freshSpawnType = spawnType;
        return super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        // 显形、离场期间半透明，被打、被打退都很怪；只放过 /kill、真掉出世界（别的模组借「绕过无敌」的真伤也挡）
        if ((this.appearGraceTicks > 0 || this.departTicks >= 0) && !isHardDeath(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        // 计时放在 tick 里：关了 AI 以后照样走
        if (this.departTicks > 0 && --this.departTicks == 0) {
            this.discard();
            return;
        }
        if (this.appearGraceTicks > 0 && --this.appearGraceTicks == 0) {
            endAppearGrace();
        }
        tickShield();
    }

    /** 演示用的一次三刀（用 {@link #setLichPhase} 改成少刀数来演挣开站起） */
    @Override
    public void markDemo() {
        super.markDemo();
        setLichPhase(SEGMENTS - 1);
    }

    /** 显形结束：开 AI，直接盯上主人往他那边走 */
    private void endAppearGrace() {
        if (this.departTicks >= 0) {
            return;
        }
        this.setNoAi(false);
        ServerPlayer owner = getOwnerPlayer();
        if (owner != null && canHuntOwner(owner)) {
            this.setTarget(owner);
            this.getNavigation().moveTo(owner, 1.2D);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && isOwned() && this.departTicks < 0 && this.appearGraceTicks == 0
                && this.tickCount % 20 == 0) {
            refocusOnOwner();
        }
    }

    /**
     * 帮打的人会被它回头砍；但这人 10 秒没再打它，它就转回去追主人
     */
    private void refocusOnOwner() {
        ServerPlayer owner = getOwnerPlayer();
        if (owner == null || !canHuntOwner(owner)) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == owner) {
            return;
        }
        if (target != null && target.isAlive() && target == this.getLastHurtByMob()
                && this.tickCount - this.getLastHurtByMobTimestamp() <= RETARGET_OWNER_AFTER) {
            return;
        }
        this.setTarget(owner);
    }

    /** 主人在同一个世界、活着、能被当成敌人（不是创造 / 旁观）、在 96 格内 */
    private boolean canHuntOwner(ServerPlayer owner) {
        return owner.level() == this.level() && owner.isAlive() && owner.canBeSeenAsEnemy()
                && owner.distanceToSqr(this) <= OWNER_HUNT_RANGE * OWNER_HUNT_RANGE;
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.level().isClientSide && isOwned()) {
            LichDirector.onLichRemoved(this, this.getRemovalReason());
        }
    }

    /**
     * 有主的不存盘：区块卸载、关服时直接丢，这次遭遇由玄骸导演结算（关服的那次登录时补算）。
     * 正在演赤毒雾离场的也不存（关着 AI，读回来会是一只不会动的玄骸）
     */
    @Override
    public boolean shouldBeSaved() {
        return !isOwned() && this.departTicks < 0 && super.shouldBeSaved();
    }

    /** 有主的不走传送门 */
    @Override
    public boolean canChangeDimensions() {
        return !isOwned() && super.canChangeDimensions();
    }

    /** 有主的叫它自己的名字：原版死亡消息「被 Drokag Tuzz 杀死了」就自动换成名字 */
    @Override
    public @NotNull Component getName() {
        if (isOwned()) {
            String name = getLichName();
            if (!name.isEmpty()) {
                return Component.literal(name);
            }
        }
        return super.getName();
    }

    /** 追主人：主人在 96 格内、能被当成敌人就盯上他；被别人打的时候让给优先级更高的「回头砍」 */
    private static final class OwnerHuntGoal extends TargetGoal {
        private final KuvaMasterEntity lich;
        private final TargetingConditions conditions;

        OwnerHuntGoal(KuvaMasterEntity lich) {
            super(lich, false);
            this.lich = lich;
            this.conditions = TargetingConditions.forCombat().range(OWNER_HUNT_RANGE).ignoreLineOfSight();
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = lich.getOwnerPlayer();
            if (owner == null || lich.getTarget() == owner || !lich.canHuntOwner(owner)) {
                return false;
            }
            if (!conditions.test(lich, owner)) {
                return false;
            }
            this.targetMob = owner;
            return true;
        }

        @Override
        public void start() {
            this.mob.setTarget(this.targetMob);
            super.start();
        }
    }

    @Override
    public @NotNull EntityType<?> getType() {
        return KuvaLichEntities.KUVA_MASTER.get();
    }
}
