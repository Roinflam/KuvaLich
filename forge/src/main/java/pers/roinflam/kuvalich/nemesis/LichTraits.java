package pers.roinflam.kuvalich.nemesis;

import pers.roinflam.kuvalich.config.ModConfig;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * 一只专属玄骸的战斗面板：由种子、等级、性格现算（纯函数，不存盘）
 *
 * <p>性格定打法：{@link LichPersona#JOVIAL 活泼}攻高血薄、两个弱点一个抗性，交战增幅、震荡、吸血偏强；
 * {@link LichPersona#LOGICAL 理性}血厚甲硬、一个弱点两个抗性（抗得更狠），适应、自愈偏强。
 * 五项技能各抽一档（弱 / 中 / 强），再抽一项当专长；中档就是改版前所有玄骸的数值。</p>
 *
 * <p>等级只改生命和护甲：2 级 = 100%，每级 ± {@code rankStep}，6 级封顶。伤害那头照旧走玩家身上的
 * 「被赤毒敌人打 +X%/级」，所以攻击不随等级涨。</p>
 *
 * <p>每一项各用 {@code new SplittableRandom(seed ^ 常量)} 取随机数，互不影响，以后加新项不会让已有玄骸变样。
 * 「每只不同」关着时（{@link #uniform}）所有专属玄骸都是改版前的固定面板，只剩等级曲线。</p>
 */
public final class LichTraits {

    /** 技能序号，和档案里能力图标的顺序一致 */
    public static final int ADAPT = 0;
    public static final int FURY = 1;
    public static final int REGEN = 2;
    public static final int QUAKE = 3;
    public static final int LIFESTEAL = 4;
    public static final int SKILLS = 5;

    /** 技能档位：弱、中、强、专长 */
    public static final int WEAK = 0;
    public static final int NORMAL = 1;
    public static final int STRONG = 2;
    public static final int SIGNATURE = 3;

    /** 伤害类别：近战、弓箭、其他弹射物、魔法（和实体那边伤害分类的前四项同序） */
    public static final int CATEGORIES = 4;
    public static final int CAT_MELEE = 0;

    /** 改版前所有玄骸共用的面板，也是「中性」2 级的基准 */
    public static final double BASE_HEALTH = 200.0D;
    public static final double BASE_ATTACK = 13.0D;
    public static final double BASE_ARMOR = 8.0D;
    public static final double BASE_KNOCKBACK_RESIST = 0.65D;

    /** 等级曲线从几级起不再涨 */
    public static final int RANK_SCALE_CAP = 6;

    /** 各档位的数值，下标是档位 */
    public static final int[] ADAPT_PCT = {3, 5, 6, 7};
    public static final int[] ADAPT_STACKS = {4, 6, 6, 6};
    public static final int[] ADAPT_DECAY_TICKS = {60, 80, 100, 120};
    public static final double[] FURY_SCALE = {0.6D, 1.0D, 1.35D, 1.6D};
    public static final double[] REGEN_SCALE = {0.5D, 1.0D, 1.5D, 2.2D};
    public static final double[] QUAKE_RADIUS = {2.5D, 3.0D, 3.5D, 4.5D};
    public static final double[] QUAKE_KNOCK = {0.8D, 1.0D, 1.2D, 1.5D};
    public static final double[] LIFESTEAL_RATIO = {0.10D, 0.20D, 0.30D, 0.45D};

    /** 改版前的固定倍率：近战不变、弱弓箭、抗其他弹射物和魔法 */
    private static final double[] FIXED_AFFINITY = {1.0D, 1.25D, 0.75D, 0.75D};
    /** 近战抗性最低到这里：多数人主力是近战，再低就只能换武器 */
    public static final double MELEE_RESIST_FLOOR = 0.85D;

    private static final long SPREAD_HEALTH = 0x5E41L;
    private static final long SPREAD_ATTACK = 0x5E42L;
    private static final long AFFINITY_CATS = 0xAFF2L;
    private static final long AFFINITY_MULT = 0xAFF3L;
    private static final long SKILL_TIERS = 0x5C11L;
    private static final long SIGNATURE_PICK = 0x5C12L;

    /** 档位概率（弱、中、强）：性格偏强的项、不偏不倚的项、偏弱的项 */
    private static final int[] FAVORED = {10, 40, 50};
    private static final int[] EVEN = {30, 40, 30};
    private static final int[] DISFAVORED = {50, 40, 10};

    /** 一种性格的打法底子 */
    private record Leaning(double health, double attack, double armor, double knockbackResist, double speed,
                           int weakCount, int resistCount, double resistLow, int resistSteps,
                           int[][] tierWeights, int[] signatureWeights) {
    }

    private static final Leaning JOVIAL = new Leaning(0.85D, 1.20D, 6.0D, 0.50D, 1.08D,
            2, 1, 0.75D, 3,
            // 适应、增幅、自愈、震荡、吸血
            new int[][]{DISFAVORED, FAVORED, DISFAVORED, FAVORED, FAVORED},
            new int[]{0, 30, 0, 40, 30});

    private static final Leaning LOGICAL = new Leaning(1.20D, 0.85D, 11.0D, 0.80D, 0.95D,
            1, 2, 0.60D, 4,
            new int[][]{FAVORED, DISFAVORED, FAVORED, EVEN, DISFAVORED},
            new int[]{40, 0, 35, 25, 0});

    public final LichPersona persona;
    public final int rank;
    /** 按种子抽的（false = 改版前的固定面板） */
    public final boolean varied;
    /** 等级倍率（生命、护甲） */
    public final double rankScale;
    public final double maxHealth;
    public final double attack;
    public final double armor;
    public final double knockbackResist;
    /** 移速倍率 */
    public final double speedScale;
    private final double[] affinity;
    private final int[] tiers;

    private LichTraits(LichPersona persona, int rank, boolean varied, double rankScale, double maxHealth,
                       double attack, double armor, double knockbackResist, double speedScale,
                       double[] affinity, int[] tiers) {
        this.persona = persona;
        this.rank = rank;
        this.varied = varied;
        this.rankScale = rankScale;
        this.maxHealth = maxHealth;
        this.attack = attack;
        this.armor = armor;
        this.knockbackResist = knockbackResist;
        this.speedScale = speedScale;
        this.affinity = affinity;
        this.tiers = tiers;
    }

    /** 按服务端当前配置算；配置还没加载（极早期）时按默认值 */
    public static LichTraits current(long seed, int rank) {
        boolean varied = true;
        double step = 0.15D;
        try {
            varied = ModConfig.KUVA_LICH.lichVariedTraits.get();
            step = ModConfig.KUVA_LICH.lichRankHealthStep.get();
        } catch (RuntimeException ignored) {
            // 配置没加载，用默认值
        }
        return varied ? seeded(seed, rank, step) : uniform(seed, rank, step);
    }

    /** 等级倍率：2 级 = 1，每级 ± step，{@link #RANK_SCALE_CAP} 级封顶，最低 0.1 */
    public static double rankScale(int rank, double step) {
        int r = Math.max(1, Math.min(RANK_SCALE_CAP, rank));
        return Math.max(0.1D, 1.0D + step * (r - 2));
    }

    /** 改版前的固定面板（只剩等级曲线）；性格照种子算，只管台词 */
    public static LichTraits uniform(long seed, int rank, double step) {
        double scale = rankScale(rank, step);
        int[] tiers = new int[SKILLS];
        java.util.Arrays.fill(tiers, NORMAL);
        return new LichTraits(LichPersona.of((int) seed), rank, false, scale, BASE_HEALTH * scale, BASE_ATTACK,
                BASE_ARMOR * scale, BASE_KNOCKBACK_RESIST, 1.0D, FIXED_AFFINITY.clone(), tiers);
    }

    /** 按种子抽，不看配置：同一个种子、等级、步长永远同一个结果 */
    public static LichTraits seeded(long seed, int rank, double step) {
        LichPersona persona = LichPersona.of((int) seed);
        Leaning lean = persona == LichPersona.JOVIAL ? JOVIAL : LOGICAL;
        double scale = rankScale(rank, step);

        // 生命、攻击在性格底子上再各浮动 ±8%
        double health = BASE_HEALTH * lean.health * (1.0D + spread(seed ^ SPREAD_HEALTH));
        double attack = BASE_ATTACK * lean.attack * (1.0D + spread(seed ^ SPREAD_ATTACK));

        // 弱点抗性：先把四类洗一遍，前几个当弱点、接着几个当抗性，剩下的不变
        int[] cats = {0, 1, 2, 3};
        SplittableRandom pick = new SplittableRandom(seed ^ AFFINITY_CATS);
        for (int i = cats.length - 1; i > 0; i--) {
            int j = pick.nextInt(i + 1);
            int t = cats[i];
            cats[i] = cats[j];
            cats[j] = t;
        }
        double[] affinity = {1.0D, 1.0D, 1.0D, 1.0D};
        SplittableRandom mult = new SplittableRandom(seed ^ AFFINITY_MULT);
        for (int i = 0; i < lean.weakCount; i++) {
            affinity[cats[i]] = 1.25D + 0.05D * mult.nextInt(4);
        }
        for (int i = lean.weakCount; i < lean.weakCount + lean.resistCount; i++) {
            double r = lean.resistLow + 0.05D * mult.nextInt(lean.resistSteps);
            affinity[cats[i]] = cats[i] == CAT_MELEE ? Math.max(MELEE_RESIST_FLOOR, r) : r;
        }

        // 技能档位按性格的概率抽，再抽一项当专长
        int[] tiers = new int[SKILLS];
        SplittableRandom tierRng = new SplittableRandom(seed ^ SKILL_TIERS);
        for (int i = 0; i < SKILLS; i++) {
            tiers[i] = weighted(tierRng, lean.tierWeights[i]);
        }
        tiers[weighted(new SplittableRandom(seed ^ SIGNATURE_PICK), lean.signatureWeights)] = SIGNATURE;

        return new LichTraits(persona, rank, true, scale, health * scale, attack, lean.armor * scale,
                lean.knockbackResist, lean.speed, affinity, tiers);
    }

    /** −0.08 ~ +0.08，步长 0.01 */
    private static double spread(long salt) {
        return (new SplittableRandom(salt).nextInt(17) - 8) / 100.0D;
    }

    private static int weighted(SplittableRandom rng, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    // ==================== 取值 ====================

    /** 第 {@code category} 类伤害打它的倍率（四类：近战、弓箭、其他弹射物、魔法） */
    public double affinity(int category) {
        return category >= 0 && category < CATEGORIES ? this.affinity[category] : 1.0D;
    }

    /** 四类倍率的百分数（125 = ×1.25），档案用 */
    public int[] affinityPercents() {
        int[] out = new int[CATEGORIES];
        for (int i = 0; i < CATEGORIES; i++) {
            out[i] = (int) Math.round(this.affinity[i] * 100.0D);
        }
        return out;
    }

    /** 低 4 位弱点、4~7 位抗性的位掩码 */
    public int affinityMask() {
        int mask = 0;
        for (int i = 0; i < CATEGORIES; i++) {
            if (this.affinity[i] > 1.0D) {
                mask |= 1 << i;
            } else if (this.affinity[i] < 1.0D) {
                mask |= 1 << (i + 4);
            }
        }
        return mask;
    }

    public int tier(int skill) {
        return this.tiers[skill];
    }

    /** 专长是哪一项；固定面板没有专长，返回 −1 */
    public int signature() {
        for (int i = 0; i < SKILLS; i++) {
            if (this.tiers[i] == SIGNATURE) {
                return i;
            }
        }
        return -1;
    }

    public boolean isSignature(int skill) {
        return this.tiers[skill] == SIGNATURE;
    }

    public int adaptPercent() {
        return ADAPT_PCT[tier(ADAPT)];
    }

    public int adaptStacks() {
        return ADAPT_STACKS[tier(ADAPT)];
    }

    public int adaptDecayTicks() {
        return ADAPT_DECAY_TICKS[tier(ADAPT)];
    }

    public double furyScale() {
        return FURY_SCALE[tier(FURY)];
    }

    public double regenScale() {
        return REGEN_SCALE[tier(REGEN)];
    }

    public double quakeRadius() {
        return QUAKE_RADIUS[tier(QUAKE)];
    }

    public double quakeKnock() {
        return QUAKE_KNOCK[tier(QUAKE)];
    }

    public double lifesteal() {
        return LIFESTEAL_RATIO[tier(LIFESTEAL)];
    }

    /** 五项档位压成一个整数：每项 2 位，按技能序号排；第 10 位 = 按种子抽的 */
    public int packedTiers() {
        int packed = this.varied ? 1 << (SKILLS * 2) : 0;
        for (int i = 0; i < SKILLS; i++) {
            packed |= (this.tiers[i] & 3) << (i * 2);
        }
        return packed;
    }

    /** 调试命令用的一行摘要：生命、攻击、护甲、四类倍率、五项档位（W 弱 / M 中 / S 强 / X 专长） */
    public String describe() {
        StringBuilder sb = new StringBuilder(this.varied ? "varied" : "uniform");
        sb.append(" hp=").append(Math.round(this.maxHealth))
                .append(" atk=").append(String.format(Locale.ROOT, "%.1f", this.attack))
                .append(" armor=").append(String.format(Locale.ROOT, "%.1f", this.armor))
                .append(" aff=");
        int[] pct = affinityPercents();
        for (int i = 0; i < CATEGORIES; i++) {
            sb.append(i == 0 ? "" : "/").append(pct[i]);
        }
        String[] names = {"adapt", "fury", "regen", "quake", "lifesteal"};
        for (int i = 0; i < SKILLS; i++) {
            sb.append(' ').append(names[i]).append(':').append("WMSX".charAt(this.tiers[i]));
        }
        return sb.toString();
    }

    public static int unpackTier(int packed, int skill) {
        return (packed >> (skill * 2)) & 3;
    }

    public static boolean unpackVaried(int packed) {
        return (packed & (1 << (SKILLS * 2))) != 0;
    }
}
