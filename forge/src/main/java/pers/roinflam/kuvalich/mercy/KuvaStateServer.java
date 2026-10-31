package pers.roinflam.kuvalich.mercy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;

import java.util.Optional;

/**
 * 玄骸 / 奴仆身上机制的「看得见」：服务端在适应叠层、伤害被挡住、打中弱点 / 被抗性削、命中吸血这几个时刻发
 * {@code KUVA_STATE}，客户端在命中点画护壁、偏转闪、裂纹、吸血雾
 *
 * <p>只演画面，不改任何伤害数值。每种都按实体限流（记录放在实体的 {@link AbstractKuva#stateTrack} 里），
 * 刷怪、连击时包量有上限：适应「加了层」每类每轮最多 6 次，没加层的同一类 4 tick 一次，挡住和命中各 10 tick 一次。</p>
 */
public final class KuvaStateServer {

    /** 发包范围（格），原点是命中点 */
    public static final double RANGE = 32.0D;
    /** 适应满层后「没加层」的同一类最少隔多少 tick 再发 */
    private static final int ADAPT_REPEAT_TICKS = 4;
    /** 「挡住了」同一只最少隔多少 tick */
    private static final int GUARD_TICKS = 10;
    /** 命中反馈同一只最少隔多少 tick */
    private static final int STRIKE_TICKS = 10;
    /** 弱点同一只最少隔多少 tick */
    private static final int WEAK_TICKS = 4;
    /** 抗性同一只最少隔多少 tick */
    private static final int RESIST_TICKS = 10;
    /** 削掉多少才算「挡住了」 */
    private static final float GUARD_MIN_CUT = 0.10F;
    /** 受击者击退后的水平速度超过这个（格 / tick）算「击退明显」 */
    private static final double HEAVY_KNOCKBACK = 0.6D;
    /** 适应满层 */
    private static final int MAX_LAYERS = 6;
    /** 命中点往胸口方向收多少（格），免得正好落在包围盒面上被深度测试吃掉 */
    private static final double INSET = 0.05D;

    /** 各种类实际发出去的包数（GameTest 核限流用；只增不减，测试前后自己取差） */
    private static final long[] SENT = new long[5];
    /** 适应包里「这一下加了层」的那部分 */
    private static long sentAdaptAdded;

    private KuvaStateServer() {
    }

    /** 每只实体自己的限流记录 */
    public static final class Track {
        /** 每类伤害上一次「没加层」的适应包的 gameTime */
        private final long[] adaptRepeatAt = new long[AbstractKuva.DamageCategory.values().length];
        /** 每类伤害已经宣布过「第一次叠满」：层数掉到满层以下时清掉 */
        private int fullAnnounced;
        private long guardAt = Long.MIN_VALUE / 2;
        private long strikeAt = Long.MIN_VALUE / 2;
        private long weakAt = Long.MIN_VALUE / 2;
        private long resistAt = Long.MIN_VALUE / 2;

        public Track() {
            java.util.Arrays.fill(adaptRepeatAt, Long.MIN_VALUE / 2);
        }
    }

    // ==================== 适应性抗性 ====================

    /**
     * 适应叠层之后调（玄骸）
     *
     * @param layers   叠完之后的层数
     * @param added    这一下加了层
     */
    public static void onAdapt(AbstractKuva target, DamageSource source, AbstractKuva.DamageCategory category,
                               int layers, boolean added) {
        if (!(target.level() instanceof ServerLevel level) || layers <= 0) {
            return;
        }
        Track t = target.stateTrack;
        int bit = 1 << category.ordinal();
        long now = level.getGameTime();
        boolean firstFull = false;
        if (layers >= MAX_LAYERS) {
            if ((t.fullAnnounced & bit) == 0) {
                t.fullAnnounced |= bit;
                firstFull = true;
            }
        }
        if (!added && !firstFull) {
            if (now - t.adaptRepeatAt[category.ordinal()] < ADAPT_REPEAT_TICKS) {
                return;
            }
            t.adaptRepeatAt[category.ordinal()] = now;
        }
        int param = KuvaStateKind.adaptParam(Math.min(layers, MAX_LAYERS), category.ordinal(), added, firstFull);
        send(level, target, hitPoint(target, source), KuvaStateKind.ADAPT, param, attackerArg(source));
        if (added) {
            sentAdaptAdded++;
        }
    }

    /** 某类适应层数衰减 / 全清后调：掉到满层以下就允许下次再宣布一次「第一次叠满」 */
    public static void onAdaptDecayed(AbstractKuva target, AbstractKuva.DamageCategory category, int layers) {
        if (layers < MAX_LAYERS) {
            target.stateTrack.fullAnnounced &= ~(1 << category.ordinal());
        }
    }

    // ==================== 挡住了 ====================

    /**
     * 连击递减 + 软上限之后调（玄骸）
     *
     * @param before    进这两层之前的伤害
     * @param afterBurst 过了连击递减之后的伤害
     * @param after     两层都过完的伤害
     */
    public static void onGuard(AbstractKuva target, DamageSource source, float before, float afterBurst, float after) {
        if (!(target.level() instanceof ServerLevel level) || before <= 0.0F) {
            return;
        }
        float cut = 1.0F - after / before;
        if (cut < GUARD_MIN_CUT) {
            return;
        }
        long now = level.getGameTime();
        Track t = target.stateTrack;
        if (now - t.guardAt < GUARD_TICKS) {
            return;
        }
        t.guardAt = now;
        float burstCut = 1.0F - afterBurst / before;
        float capCut = afterBurst <= 0.0F ? 0.0F : 1.0F - after / afterBurst;
        int param = KuvaStateKind.guardParam(Math.round(cut * 100.0F), burstCut >= capCut);
        send(level, target, hitPoint(target, source), KuvaStateKind.GUARD, param, attackerArg(source));
    }

    // ==================== 弱点 / 抗性 ====================

    /**
     * 按伤害类型的倍率算完之后调（玄骸、奴仆）：倍率大于 1 是打中了弱点，给附近的人发弱点；小于 1 是被抗性削了，
     * 只发给打它的那个玩家。客户端只认这个包、不自己按伤害类型猜：开了「每只弱点抗性不同」以后，
     * 弱点不一定是弓箭，弓箭也可能被抗性削
     *
     * @param multiplier 这一下按伤害类型吃到的倍率
     */
    public static void onAffinityHit(AbstractKuva target, DamageSource source, float multiplier) {
        if (!(target.level() instanceof ServerLevel level) || multiplier == 1.0F) {
            return;
        }
        long now = level.getGameTime();
        Track t = target.stateTrack;
        if (multiplier > 1.0F) {
            if (now - t.weakAt < WEAK_TICKS) {
                return;
            }
            t.weakAt = now;
            send(level, target, hitPoint(target, source), KuvaStateKind.WEAK, 0, attackerArg(source));
            return;
        }
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer sp ? sp
                : source.getDirectEntity() instanceof ServerPlayer direct ? direct : null;
        if (attacker == null || now - t.resistAt < RESIST_TICKS) {
            return;
        }
        t.resistAt = now;
        // 弹射物从它此刻的位置闪，近战、手里放的法术从胸口闪（客户端再夹到包围盒上）
        Entity hitter = source.getDirectEntity();
        Vec3 at = hitter != null && hitter != attacker ? hitter.position() : target.chest();
        KuvaFx.player(attacker, FxId.KUVA_STATE, at, target.getId(),
                KuvaStateKind.pack(KuvaStateKind.RESIST, 0), attacker.getId() + 1);
        SENT[KuvaStateKind.RESIST]++;
    }

    // ==================== 命中反馈 ====================

    /**
     * 玄骸 / 奴仆近战打中以后调：吸了多少血、受击者是不是被打飞了
     *
     * @param drained 这一下实际回了多少血
     */
    public static void onStrike(AbstractKuva attacker, Entity victim, float drained) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Track t = attacker.stateTrack;
        if (now - t.strikeAt < STRIKE_TICKS) {
            return;
        }
        Vec3 v = victim.getDeltaMovement();
        boolean heavy = v.x * v.x + v.z * v.z > HEAVY_KNOCKBACK * HEAVY_KNOCKBACK;
        int drainTenths = Math.round(Math.max(0.0F, drained) * 10.0F);
        if (drainTenths <= 0 && !heavy) {
            return;
        }
        t.strikeAt = now;
        int param = KuvaStateKind.strikeParam(drainTenths, heavy);
        send(level, attacker, victim.position(), KuvaStateKind.STRIKE, param, victim.getId() + 1);
    }

    // ==================== 演示命令 ====================

    /**
     * 演示用：对一只实体直接播一次某种状态（不改任何数值）。{@code param} 照 {@link KuvaStateKind} 的编码，
     * 原点取胸口；{@code attacker} 给了就按它的位置算朝向
     */
    public static void demo(AbstractKuva target, int kind, int param, @Nullable Entity attacker) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = attacker == null ? target.chest() : towardChest(target, attacker.getEyePosition());
        int b = kind == KuvaStateKind.WEAK ? 0 : attacker == null ? 0 : attacker.getId() + 1;
        send(level, target, at, kind, kind == KuvaStateKind.WEAK ? 0 : param, b);
    }

    // ==================== 公用 ====================

    private static void send(ServerLevel level, AbstractKuva target, Vec3 at, int kind, int param, int b) {
        KuvaFx.near(level, at, RANGE, FxId.KUVA_STATE, target, KuvaStateKind.pack(kind, param), b);
        if (kind >= 0 && kind < SENT.length) {
            SENT[kind]++;
        }
    }

    private static int attackerArg(DamageSource source) {
        Entity e = source.getEntity();
        return e == null ? 0 : e.getId() + 1;
    }

    /**
     * 命中点：弹射物取它此刻的位置夹到包围盒上；生物取「攻击者眼睛 → 目标胸口」和包围盒的交点，
     * 没有交点就用胸口；最后都往胸口收一点
     */
    public static Vec3 hitPoint(AbstractKuva target, DamageSource source) {
        Vec3 chest = target.chest();
        Entity direct = source.getDirectEntity();
        AABB box = target.getBoundingBox();
        Vec3 p;
        if (direct instanceof Projectile) {
            Vec3 at = direct.position();
            p = new Vec3(clamp(at.x, box.minX, box.maxX), clamp(at.y, box.minY, box.maxY), clamp(at.z, box.minZ, box.maxZ));
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            return towardChest(target, attacker.getEyePosition());
        } else {
            return chest;
        }
        return inset(p, chest);
    }

    private static Vec3 towardChest(AbstractKuva target, Vec3 from) {
        Vec3 chest = target.chest();
        Optional<Vec3> hit = target.getBoundingBox().clip(from, chest);
        return hit.map(v -> inset(v, chest)).orElse(chest);
    }

    private static Vec3 inset(Vec3 p, Vec3 chest) {
        Vec3 d = chest.subtract(p);
        double len = d.length();
        if (len <= INSET) {
            return chest;
        }
        return p.add(d.scale(INSET / len));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** 某种类累计发出的包数（测试用） */
    public static long sentCount(int kind) {
        return kind >= 0 && kind < SENT.length ? SENT[kind] : 0L;
    }

    /** 适应包里「加了层」的那部分累计数（测试用） */
    public static long sentAdaptAddedCount() {
        return sentAdaptAdded;
    }
}
