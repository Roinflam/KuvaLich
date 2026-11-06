package pers.roinflam.kuvalich.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.MercyRules;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 赤毒幼体：还没有玄骸的玩家附近隔一阵出现一只，处决它，这个人的玄骸就诞生了
 *
 * <p>骨架、动作、倒地和处决都和奴仆同一套（所以直接继承奴仆，怜悯之击按奴仆的路数走），不同的是：</p>
 * <ul>
 *   <li>刷出来就掷好将来那只玄骸的种子和武器，手里拿的、倒地时头顶亮出来的就是这把，诞生后玄骸拿的也是这把；</li>
 *   <li>倒地等 {@code larvlingDownedSeconds} 秒（默认 30），不是奴仆的那个时长；</li>
 *   <li>倒地期间无敌、只能处决，时间到了没人处决就就地消散，什么都不给；</li>
 *   <li>被处决（怜悯之击关着时是被玩家打死）才诞生玄骸，给处决的人；不给解密进度、不掉东西、不加怒气；</li>
 *   <li>不存盘，不会自然消失（由幼体导演管它什么时候走）。</li>
 * </ul>
 *
 * <p>谁能处决：倒地那一刻按顺序找——打倒它的玩家（含宠物、延后伤害的归属，同奴仆）自己还没有玄骸就是他；
 * 不行就是它的主人（导演为他刷的那个人），主人得在线、在同一个世界、也还没有玄骸；都不行就没人能处决，到时消散。
 * 已经有玄骸的人永远处决不了幼体。</p>
 */
public class KuvaLarvlingEntity extends KuvaSlaveEntity {

    /** 死亡包 b：怜悯之击之后（同奴仆的位，幼体从不给进度） */
    private static final int DEATH_AFTER_MERCY = 1 << 1;
    private static final double DEATH_FX_RANGE = 32.0D;

    /** 整只的缩放：模型照奴仆的骨架摆（40.25 像素），画的时候缩到这么大，站着约 1.89 格；渲染和处决动作共用 */
    public static final float MODEL_SCALE = 0.75F;

    /** 将来那只玄骸的武器下标，-1 = 还没掷（倒地时亮给玩家看的就是这把；幼体自己空着手，原作如此） */
    private static final EntityDataAccessor<Integer> DATA_WEAPON =
            SynchedEntityData.defineId(KuvaLarvlingEntity.class, EntityDataSerializers.INT);
    /** 那把武器定下的等级，0 = 还没掷 */
    private static final EntityDataAccessor<Integer> DATA_WEAPON_LEVEL =
            SynchedEntityData.defineId(KuvaLarvlingEntity.class, EntityDataSerializers.INT);
    /** 那把武器定下的元素在 {@link KuvaWeaponUtil#LICH_ELEMENTS} 里的下标，-1 = 还没掷 */
    private static final EntityDataAccessor<Integer> DATA_WEAPON_TYPE =
            SynchedEntityData.defineId(KuvaLarvlingEntity.class, EntityDataSerializers.INT);

    /** 为谁刷的（导演刷的才有；刷怪蛋、命令造的没有）。服务端 */
    @Nullable
    private UUID owner;
    /** 将来那只玄骸的种子。服务端 */
    private long seed;
    private boolean seeded;
    /** 进过倒地（之后不管处决还是消散，都算这一只「打过了」）。服务端 */
    private boolean everDowned;
    /** 已经诞生过玄骸（同一只不会给两次）。服务端 */
    private boolean birthGiven;

    public KuvaLarvlingEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    /** 比奴仆弱一截：血少、刀轻、甲薄，一个人也打得过 */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WEAPON, -1);
        this.entityData.define(DATA_WEAPON_LEVEL, 0);
        this.entityData.define(DATA_WEAPON_TYPE, -1);
    }

    // ==================== 种子与武器 ====================

    /**
     * 导演刷之前调：定下主人、种子，武器、元素、等级都按种子算（和老规矩「种子定武器」一致）。
     * 等级在主人当前的武器等级范围里取，主人不在线、没有主人时按新玩家的初始范围；谁处决诞生的玄骸，拿的都是这里定的这一把
     */
    public void setup(@Nullable UUID owner, long seed) {
        this.owner = owner;
        this.seed = seed;
        this.seeded = true;
        this.entityData.set(DATA_WEAPON, LichProfile.weaponIndex(seed, KuvaLichItems.KUVA_WEAPONS.size()));
        int min = ModConfig.KUVA_LICH.baseMinimumLevel.get();
        int max = ModConfig.KUVA_LICH.baseMaximumLevel.get();
        if (owner != null && this.level() instanceof ServerLevel server) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(owner);
            RequiemCard card = p == null ? null : p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
            if (card != null) {
                min = card.getMinimumLevelWeapon();
                max = card.getMaximumLevelWeapon();
            }
        }
        this.entityData.set(DATA_WEAPON_LEVEL, Math.max(1, LichProfile.weaponLevel(seed, min, max)));
        this.entityData.set(DATA_WEAPON_TYPE, KuvaWeaponUtil.LICH_ELEMENTS.indexOf(LichProfile.weaponElement(seed)));
    }

    /** 刷怪蛋、命令造的：进世界时自己掷一个 */
    private void ensureSeeded() {
        if (!this.seeded) {
            setup(this.owner, ThreadLocalRandom.current().nextLong() ^ System.nanoTime());
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide) {
            ensureSeeded();
        }
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.owner;
    }

    public long getLichSeed() {
        return this.seed;
    }

    /** 手里那把（也是将来玄骸的那把）在武器列表里的下标，-1 = 没有。两端都能读 */
    public int getWeaponIndex() {
        return this.entityData.get(DATA_WEAPON);
    }

    /** 那把武器定下的等级，0 = 还没掷。两端都能读 */
    public int getWeaponLevel() {
        return this.entityData.get(DATA_WEAPON_LEVEL);
    }

    /** 那把武器定下的元素（如 {@code fire}），还没掷是空串。两端都能读 */
    public String getWeaponType() {
        int i = this.entityData.get(DATA_WEAPON_TYPE);
        return i >= 0 && i < KuvaWeaponUtil.LICH_ELEMENTS.size() ? KuvaWeaponUtil.LICH_ELEMENTS.get(i) : "";
    }

    /** 武器物品，下标无效返回 null */
    @Nullable
    public Item getWeaponItem() {
        int i = getWeaponIndex();
        return i >= 0 && i < KuvaLichItems.KUVA_WEAPONS.size() ? KuvaLichItems.KUVA_WEAPONS.get(i) : null;
    }

    public boolean wasEverDowned() {
        return this.everDowned;
    }

    public boolean isBirthGiven() {
        return this.birthGiven;
    }

    // ==================== 谁能处决 ====================

    /** 这个玩家能不能靠处决幼体得到玄骸：有安魂数据、还没有玄骸、不是旁观 */
    public static boolean canReceiveLich(@Nullable Player player) {
        if (!(player instanceof ServerPlayer sp) || sp.isSpectator() || !sp.isAlive()) {
            return false;
        }
        RequiemCard card = sp.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        return card != null && !card.hasLich();
    }

    /**
     * 倒地那一刻定资格人：{@code hitter}（打倒它的人，按奴仆的规矩算出来的）能接就是他，
     * 不能就退给主人，主人也不行返回 null（没人能处决）
     */
    @Nullable
    public UUID pickClaimant(@Nullable UUID hitter) {
        if (hitter != null && canReceiveLich(this.level().getPlayerByUUID(hitter))) {
            return hitter;
        }
        if (this.owner != null && canReceiveLich(this.level().getPlayerByUUID(this.owner))) {
            return this.owner;
        }
        return null;
    }

    // ==================== 倒地 ====================

    @Override
    public int downedSeconds() {
        return ModConfig.KUVA_LICH.larvlingDownedSeconds.get();
    }

    /** 声音同奴仆，事件分开只为字幕写「幼体」 */
    @Override
    protected SoundEvent getAmbientSound() {
        return KuvaLichSounds.ENTITY_LARVLING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return KuvaLichSounds.ENTITY_LARVLING_HURT.get();
    }

    /** 处决用奴仆那几套，处决者的刀位、跳起的高度照模型的缩放一起缩 */
    @Override
    public float mercyBodyScale() {
        return MODEL_SCALE;
    }

    /**
     * 胸口按缩小的奴仆算：奴仆的碰撞箱比模型高出一截，处决动作是照它的碰撞箱定的；
     * 幼体的碰撞箱贴着模型，照自己碰撞箱的比例算会比缩小的奴仆低约 0.17 格
     */
    @Override
    public double mercyChestHeight() {
        return KuvaLichEntities.KUVA_SLAVE.get().getHeight() * MERCY_CHEST_FRAC * MODEL_SCALE;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && getMercyState() != MercyState.NONE) {
            this.everDowned = true;
        }
    }

    // 倒地期间不另做受伤判定：照基类，什么伤害都不吃（/kill、虚空除外），只能处决，没人处决就等时间到了消散。
    // 以前倒地后再挨玩家一下就当场消散，连发枪、霰弹枪打倒它的同一梭子后面几发必定把它打散，根本来不及处决

    // ==================== 死亡 ====================

    /**
     * 确实死了：处决走完的那一下（怜悯之击关着时是被玩家打死）才诞生玄骸，给处决的人；
     * 他这时已经有玄骸了（倒地以后又从别处得了一只）就什么都不给。
     * 别的死法（/kill、虚空、摔死、被怪打死）什么都没有
     */
    @Override
    protected void onKilled(DamageSource source) {
        boolean finishing = isMercyFinishing() || !MercyRules.enabledFor(this);
        Player killer = source.getEntity() instanceof Player p ? p : null;
        if (this.level() instanceof ServerLevel serverLevel) {
            KuvaFx.near(serverLevel, chest(), DEATH_FX_RANGE, FxId.THRALL_DEATH, this,
                    killer == null ? 0 : killer.getId() + 1, isMercyFinishing() ? DEATH_AFTER_MERCY : 0);
        }
        markDeathFxSent();
        if (!finishing || isDemo() || this.birthGiven || !(killer instanceof ServerPlayer executor)) {
            return;
        }
        if (!canReceiveLich(executor)) {
            return;
        }
        this.birthGiven = true;
        LichDirector.birthFromLarvling(executor, this.seed, getWeaponIndex(), getWeaponLevel(), getWeaponType());
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    // ==================== 存盘、消失 ====================

    /** 不存盘：区块卸载、关服就没了，由导演按「走丢了」处理 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** 走远了不自己消失，导演按主人的位置收它 */
    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    /** 导演让它走（主人下线、走远、换维度、死了、已经有了玄骸）：消散，什么都不给。倒地、处决中的不动 */
    public boolean dismiss() {
        if (this.level().isClientSide || !this.isAlive() || this.isRemoved() || isLeaving()
                || getMercyState() != MercyState.NONE) {
            return false;
        }
        beginExpire();
        return true;
    }

    @Override
    public @NotNull EntityType<?> getType() {
        return KuvaLichEntities.KUVA_LARVLING.get();
    }
}
