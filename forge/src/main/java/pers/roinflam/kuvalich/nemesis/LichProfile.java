package pers.roinflam.kuvalich.nemesis;

import pers.roinflam.kuvalich.config.ModConfig;

import java.util.SplittableRandom;

/**
 * 从玄骸的种子派生出外观、嗓音和性格上的小差异（纯函数）
 *
 * <p>每一项都用 {@code new SplittableRandom(seed ^ 常量)} 单独取随机数，互不影响，
 * 所以以后加新的派生项不会让已有玄骸的旧属性变样。</p>
 */
public final class LichProfile {

    /** 嗓音一共有多少个音节变体文件 */
    public static final int VOICE_VARIANTS = 12;
    /** 每只玄骸固定使用其中几个 */
    public static final int VOICE_ACCENT_SIZE = 7;

    private static final long WEAPON = 0x57EAL;
    private static final long VOICE_PITCH = 0x7011L;
    private static final long VOICE_ACCENT = 0x7012L;
    private static final long GLOW_PERIOD = 0x61F0L;
    private static final long HEAD_TILT = 0x1D1EL;
    private static final long AFFINITY = 0xAFF1L;

    /** 伤害类别的位：近战、弓箭、其他弹射物、魔法（和实体那边伤害分类的前四项同序） */
    public static final int CAT_MELEE = 1;
    public static final int CAT_ARROW = 1 << 1;
    public static final int CAT_PROJECTILE = 1 << 2;
    public static final int CAT_MAGIC = 1 << 3;
    /** 所有玄骸一样时的弱点 / 抗性：弱点弓箭，抗性其他弹射物和魔法，近战不变 */
    public static final int FIXED_AFFINITIES = CAT_ARROW | ((CAT_PROJECTILE | CAT_MAGIC) << 4);

    private LichProfile() {
    }

    /** 手里那把赤毒武器在武器列表里的下标；列表为空返回 -1 */
    public static int weaponIndex(long seed, int weaponCount) {
        if (weaponCount <= 0) {
            return -1;
        }
        return new SplittableRandom(seed ^ WEAPON).nextInt(weaponCount);
    }

    /** 嘟囔的基础音高（播放 pitch），0.80~1.25 */
    public static float voicePitch(long seed) {
        return (float) (0.80D + 0.45D * new SplittableRandom(seed ^ VOICE_PITCH).nextDouble());
    }

    /**
     * 这只玄骸的「口音」：12 个音节文件里固定用哪 7 个。返回的是文件序号（1~12，
     * 对应 {@code ui/comm/lich/mutter/N.ogg}），互不重复
     */
    public static int[] voiceAccent(long seed) {
        SplittableRandom rng = new SplittableRandom(seed ^ VOICE_ACCENT);
        int[] pool = new int[VOICE_VARIANTS];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = i + 1;
        }
        // 只洗前 7 个位置
        for (int i = 0; i < VOICE_ACCENT_SIZE; i++) {
            int j = i + rng.nextInt(pool.length - i);
            int t = pool[i];
            pool[i] = pool[j];
            pool[j] = t;
        }
        int[] accent = new int[VOICE_ACCENT_SIZE];
        System.arraycopy(pool, 0, accent, 0, VOICE_ACCENT_SIZE);
        return accent;
    }

    /** 发光心跳的周期（秒），2.2~2.8 */
    public static float glowPeriodSeconds(long seed) {
        return (float) (2.2D + 0.6D * new SplittableRandom(seed ^ GLOW_PERIOD).nextDouble());
    }

    /** 待机时头偏的角度，-4°~+4° */
    public static float headTiltDegrees(long seed) {
        return (float) (-4.0D + 8.0D * new SplittableRandom(seed ^ HEAD_TILT).nextDouble());
    }

    /**
     * 这只玄骸的弱点 / 抗性位掩码：低 4 位是弱点（伤害 ×1.25），4~7 位是抗性（×0.75），
     * 位序见 {@link #CAT_MELEE} 等常量。
     *
     * <p>「每只玄骸弱点抗性不同」关着（默认）时所有玄骸都是 {@link #FIXED_AFFINITIES}，
     * 和奴仆、无主玄骸的固定倍率一样；开着时按种子抽，见 {@link #seededAffinities}。
     * 配置还没加载（极早期）时按关着算。</p>
     */
    public static int affinities(long seed) {
        return seededEnabled() ? seededAffinities(seed) : FIXED_AFFINITIES;
    }

    /**
     * 按种子抽：四类里抽 1 个当弱点，剩下 3 个里抽 2 个当抗性，余下 1 个不变，结构和固定值一样（1 弱 2 抗 1 平）。
     * 不看配置，同一个种子永远同一个结果
     */
    public static int seededAffinities(long seed) {
        SplittableRandom rng = new SplittableRandom(seed ^ AFFINITY);
        int weak = rng.nextInt(4);
        // 剩下三类里不当抗性的那一个
        int plain = rng.nextInt(3);
        int resist = 0;
        int k = 0;
        for (int bit = 0; bit < 4; bit++) {
            if (bit == weak) {
                continue;
            }
            if (k != plain) {
                resist |= 1 << bit;
            }
            k++;
        }
        return (1 << weak) | (resist << 4);
    }

    /** 弱点那几类的位掩码（低 4 位） */
    public static int weakMask(int affinities) {
        return affinities & 0xF;
    }

    /** 抗性那几类的位掩码（已移到低 4 位） */
    public static int resistMask(int affinities) {
        return (affinities >> 4) & 0xF;
    }

    private static boolean seededEnabled() {
        try {
            return ModConfig.KUVA_LICH.lichSeededAffinities.get();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
