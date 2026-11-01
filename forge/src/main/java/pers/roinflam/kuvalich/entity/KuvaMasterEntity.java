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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichLine;
import pers.roinflam.kuvalich.nemesis.LichProfile;
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
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.LogUtil;

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
    /** 完整的 64 位种子（同步数据里只有低 32 位），按种子派生弱点抗性时用；只在服务端 */
    private long lichSeedFull;
    /** finalizeSpawn 时记下的生成方式；不存盘，读档、带 NBT 的命令、直接加进世界的都是 null */
    @Nullable
    private MobSpawnType freshSpawnType;
    /** 读档时 NBT 里带着 KuvaWild 标记 */
    private boolean wildTagLoaded;
    /** 三刀成功的结算已经在定案那一刻做过：死的时候不再按直接击杀判一次 */
    private boolean mercySuccessApplied;

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
     * <p>倒地、处决中的先交给怜悯之击处理：已经定案、三刀全对或单刀的直接快进到死（不再离场），
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
        this.entityData.set(OWNER, Optional.of(owner.getUUID()));
        this.entityData.set(OWNER_NAME, owner.getGameProfile().getName());
        this.entityData.set(LICH_NAME, card.getLichName());
        this.entityData.set(LICH_RANK, card.getLichRank());
        this.entityData.set(LICH_SEED, (int) card.getLichSeed());
        this.entityData.set(LICH_WEAPON, card.getLichWeapon());
        this.entityData.set(WILD, false);
        this.lichSeedFull = card.getLichSeed();
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
    }

    /** 等级变了（处决失败等）时把同步数据跟上 */
    public void refreshRank(RequiemCard card) {
        this.entityData.set(LICH_RANK, card.getLichRank());
    }

    /**
     * 开始显形：{@code ticks} 个 tick 内关 AI、不出手、不挨打（{@code BYPASSES_INVULNERABILITY} 的伤害除外）。
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
     * - 单类4秒无命中 → 该类逐层衰减（每4秒掉1层）
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

        // 单类衰减：某类伤害4秒内无命中，每4秒衰减1层
        for (Map.Entry<DamageCategory, AdaptiveStack> entry : adaptiveResistance.entrySet()) {
            AdaptiveStack stack = entry.getValue();
            if (stack.stacks > 0 && currentTick - stack.lastHitTick > STACK_DECAY_TICKS) {
                stack.stacks--;
                // 重置该类的计时器，下一层衰减需要再等4秒
                stack.lastHitTick = currentTick;
                KuvaStateServer.onAdaptDecayed(this, entry.getKey(), stack.stacks);
            }
        }
        syncAdaptPacked();
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
        // 挥砍声、命中声都由客户端按挥臂和实体事件 103 放，服务端不再直接放
        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float finalDamage = attackDamage * this.level().getDifficulty().getId() * ATTACK_DAMAGE_MULTIPLIER;

        attackNearbyEntities(finalDamage, target);

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
                entity -> !(entity instanceof AbstractKuva) && !entity.equals(excludeEntity)
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

    /** 没有别的路发过死亡包时的默认那个：处决单刀收尾 b = 1（卡没放齐），其余 b = 0 */
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
     * 三张卡放齐了就判定并记一行处决记录（直接判定，不是怜悯之击）；没放齐只给主人解密进度。
     * 怜悯之击的单刀收尾也走这里，那时按开始处决那一刻「卡没放齐」算，途中放齐了也不补判。</p>
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
        if (card.isReadyCard()) {
            LogUtil.warn("玄骸 " + getLichName() + " 按卡没放齐开始的处决收尾时，主人的卡已经放齐，这次不判定");
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
     * 直接判定成功：演出先行，效果包一开头就发，客户端 420 ms 时晶核碎裂。判定、扣卡、记账照旧当场做，
     * 但聊天和掉落放到碎裂之后（+12 tick），HUD 的完成提示再晚一点（+18 tick），
     * 免得东西先蹦出来、聊天栏先刷四五行，然后才是碎裂那一声
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

        SuccessResult result = computeAndApplySuccess(owner, card, this.blockPosition());
        ServerFxQueue.schedule(REWARD_DELAY_TICKS, owner, () -> result.deliver(owner));
        ServerFxQueue.schedule(COMPLETE_DELAY_TICKS, owner, () -> {
            if (!owner.hasDisconnected()) {
                DecryptionHudPacket.sendComplete(owner, result.riddles(), result.lichName());
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
     * 三刀全对的结算：扣卡、终结这一轮、提升武器等级上限，并把要给玩家的聊天和掉落算好装进结果里
     * （调用方决定什么时候 {@link SuccessResult#deliver} 发出去）。直接判定和怜悯之击都走这里。
     * 调用前调用方已经记过那一行处决记录（这一轮结束会清掉记录）。
     *
     * @param player 主人
     * @param card   主人的卡（判定完会被重置）
     * @param pos    掉落位置
     */
    public SuccessResult computeAndApplySuccess(ServerPlayer player, RequiemCard card, BlockPos pos) {
        this.mercySuccessApplied = true;
        List<Runnable> chats = new ArrayList<>();
        List<ItemStack> drops = new ArrayList<>();

        int returnCount = card.hasConfiscatedItems() ? card.getConfiscatedItemCount() : 0;
        int wornOut = countCardsWornOut(card);
        // 完成提示要显示三枚密语和名字，卡马上要重置，先取快照
        byte[] riddles = {(byte) card.getOneRiddle(), (byte) card.getTwoRiddle(), (byte) card.getThreeRiddle()};
        String lichName = getLichName();

        // 手里那把就是成功时掉的那把：结束这一轮会清掉武器下标，所以先取
        int weaponIndex = card.getLichWeapon();
        Item weapon = weaponIndex >= 0 && weaponIndex < KuvaLichItems.KUVA_WEAPONS.size()
                ? KuvaLichItems.KUVA_WEAPONS.get(weaponIndex)
                : KuvaLichItems.KUVA_WEAPONS.get(RandomUtil.getInt(0, KuvaLichItems.KUVA_WEAPONS.size() - 1));

        chats.add(() -> sendDeathMessage(player));

        if (card.hasConfiscatedItems()) {
            // 物品被还回来了是好消息，不该和玄骸的嘲讽同色
            chats.add(() -> PlayerFeedback.chat(player, KuvaPalette.SUCCESS, "message.kuvalich.confiscation.return", returnCount));
            drops.addAll(card.clearAndGetConfiscatedItems());
        }

        List<ItemStack> survivingCards = card.consumeCardsAndGetSurvivors();
        // 这一轮结束要在 reset 之前：历史里记的等级来自 kuvaLevel，reset 会把它清零
        LichDirector.onVanquished(player, this);
        card.reset();

        for (ItemStack survivingCard : survivingCards) {
            // 掉落方式和别处不同（用 spawnItem 落在死亡点而不是玩家脚下），
            // 所以只借用空间判断，不用 giveOrDrop
            if (InventoryUtil.hasRoomFor(player, survivingCard)) {
                player.getInventory().add(survivingCard);
            } else {
                drops.add(survivingCard);
            }
        }

        drops.add(KuvaWeaponUtil.getItem(
                weapon,
                card.getMinimumLevelWeapon(),
                card.getMaximumLevelWeapon()
        ));

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

        upgradeWeaponLevelCap(player, card, chats);

        return new SuccessResult(chats, drops, level(), pos, riddles, lichName, returnCount, wornOut);
    }

    /**
     * 三刀全对算好、还没发出去的东西
     *
     * @param chats        给主人的聊天（含遗言）
     * @param drops        掉在 {@code pos} 的物品
     * @param level        掉落的世界（发的时候实体可能已经没了）
     * @param riddles      三枚密语的快照，完成提示用
     * @param lichName     玄骸名字的快照
     * @param returnCount  归还的没收物品件数
     * @param cardsWornOut 用完消失的安魂卡张数
     */
    public record SuccessResult(List<Runnable> chats, List<ItemStack> drops, Level level, BlockPos pos,
                                byte[] riddles, String lichName, int returnCount, int cardsWornOut) {

        /** 发出去：主人在线才发聊天，掉落照样落在原世界的死亡点 */
        public void deliver(@Nullable ServerPlayer owner) {
            if (owner == null || !owner.hasDisconnected()) {
                chats.forEach(Runnable::run);
            }
            for (ItemStack stack : drops) {
                spawnItem(level, pos, stack);
            }
        }
    }

    /** 安魂成功后聊天和掉落延后的 tick 数（客户端晶核在 420 ms 碎裂，之后 180 ms） */
    private static final int REWARD_DELAY_TICKS = 12;
    /** 安魂成功后 HUD 完成提示延后的 tick 数 */
    private static final int COMPLETE_DELAY_TICKS = 18;

    /**
     * 这次判定会用完消失几张安魂卡（只读，和 {@link RequiemCard#consumeCardsAndGetSurvivors} 的判据一致：
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
     * 通讯没能发出去时退回原来的聊天栏一句
     *
     * @param player 玩家
     */
    private void sendDeathMessage(Player player) {
        if (player instanceof ServerPlayer serverPlayer && LichComms.say(serverPlayer, LichLine.VANQUISHED, this)) {
            return;
        }
        String messageKey = DEATH_MESSAGES[RandomUtil.getInt(0, DEATH_MESSAGES.length - 1)];
        PlayerFeedback.chat(player, KuvaPalette.LICH, messageKey);
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
     * 提升武器等级上限（数值当场改，提示聊天放进 {@code chats} 随奖励一起延后发）
     *
     * @param player 玩家
     * @param requiemCard 安魂卡片
     * @param chats 延后发送的聊天
     */
    private void upgradeWeaponLevelCap(Player player, RequiemCard requiemCard, List<Runnable> chats) {
        int randomNum = RandomUtil.getInt(
                ModConfig.KUVA_LICH.minimumLevelCapIncrease.get(),
                ModConfig.KUVA_LICH.maximumLevelCapIncrease.get()
        );

        if (requiemCard.getMinimumLevelWeapon() < ModConfig.KUVA_LICH.minimumLevel.get()) {
            requiemCard.setMinimumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.minimumLevel.get(),
                            requiemCard.getMinimumLevelWeapon() + randomNum)
            );
            int newMin = requiemCard.getMinimumLevelWeapon();
            chats.add(() -> PlayerFeedback.chat(player, KuvaPalette.SUCCESS, "message.kuvalich.minimumLevelWeapon", newMin));
        }

        if (requiemCard.getMaximumLevelWeapon() < ModConfig.KUVA_LICH.maximumLevel.get()) {
            requiemCard.setMaximumLevelWeapon(
                    Math.min(ModConfig.KUVA_LICH.maximumLevel.get(),
                            requiemCard.getMaximumLevelWeapon() + randomNum)
            );
            int newMax = requiemCard.getMaximumLevelWeapon();
            chats.add(() -> PlayerFeedback.chat(player, KuvaPalette.SUCCESS, "message.kuvalich.maximumLevelWeapon", newMax));
        }
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
            DecryptionHudPacket.sendProgress(player, requiemCard, addPotion, levelBefore);
            if (player instanceof ServerPlayer serverPlayer) {
                LichDirector.onDecryptionProgress(serverPlayer, requiemCard, addPotion, levelBefore);
            }

            boolean max = requiemCard.getPointsRequired() == -1;
            // 与奴仆那条同通道：顶部解密 HUD 已经在显示，这里只做一次性确认
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
            if (!requiemCard.isCorrectAnswer() && !requiemCard.isReadyCard()) {
                PlayerFeedback.actionBar(player, KuvaPalette.ACCENT, "message.kuvalich.maxLevel");
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

    // ==================== 工具方法 ====================

    /**
     * 在指定位置生成掉落物实体
     *
     * @param pos 位置
     * @param itemStack 物品
     */
    private void spawnItem(BlockPos pos, ItemStack itemStack) {
        spawnItem(level(), pos, itemStack);
    }

    /** 同上，但世界由调用方给（延时任务执行时实体已经死了、可能已被移除） */
    private static void spawnItem(Level level, BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        ItemEntity entityItem = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
        level.addFreshEntity(entityItem);
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
        // 显形、离场期间半透明，被打、被打退都很怪；只放过 /kill、虚空这类本来就无视无敌的
        if ((this.appearGraceTicks > 0 || this.departTicks >= 0)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
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

    /** 开了「每只弱点抗性不同」时，有主的按种子取；奴仆和无主的照固定倍率 */
    @Override
    protected int seededAffinities() {
        if (isOwned() && ModConfig.KUVA_LICH.lichSeededAffinities.get()) {
            return LichProfile.affinities(this.lichSeedFull) & 0xFF;
        }
        return -1;
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
