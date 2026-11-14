package pers.roinflam.kuvalich.nemesis;

import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.util.List;
import java.util.SplittableRandom;

/**
 * 从玄骸的种子派生出外观、嗓音上的小差异（纯函数）；战斗面板见 {@link LichTraits}
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
    private static final long WEAPON_ELEMENT = 0x57EBL;
    private static final long WEAPON_LEVEL = 0x57ECL;
    private static final long VOICE_PITCH = 0x7011L;
    private static final long VOICE_ACCENT = 0x7012L;
    private static final long GLOW_PERIOD = 0x61F0L;
    private static final long HEAD_TILT = 0x1D1EL;
    private static final long EPHEMERA_ROLL = 0xE9E1L;
    private static final long EPHEMERA_PICK = 0xE9E2L;

    private LichProfile() {
    }

    /** 手里那把赤毒武器在武器列表里的下标；列表为空返回 -1 */
    public static int weaponIndex(long seed, int weaponCount) {
        if (weaponCount <= 0) {
            return -1;
        }
        return new SplittableRandom(seed ^ WEAPON).nextInt(weaponCount);
    }

    /** 那把武器的元素：赤毒武器那 7 种之一（{@link KuvaWeaponUtil#LICH_ELEMENTS}） */
    public static String weaponElement(long seed) {
        List<String> all = KuvaWeaponUtil.LICH_ELEMENTS;
        return all.get(new SplittableRandom(seed ^ WEAPON_ELEMENT).nextInt(all.size()));
    }

    /** 那把武器的等级：在 [min, max] 里按种子取一个（max 比 min 小时按 min） */
    public static int weaponLevel(long seed, int min, int max) {
        int hi = Math.max(min, max);
        return min + new SplittableRandom(seed ^ WEAPON_LEVEL).nextInt(hi - min + 1);
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

    /** 带不带幻纹：chance 取 0 必不带、取 1 必带 */
    public static boolean ephemeraRolls(long seed, double chance) {
        return new SplittableRandom(seed ^ EPHEMERA_ROLL).nextDouble() < chance;
    }

    /** 一个元素对应好几款幻纹时挑哪一款（下标） */
    public static int ephemeraPick(long seed, int count) {
        if (count <= 1) {
            return 0;
        }
        return new SplittableRandom(seed ^ EPHEMERA_PICK).nextInt(count);
    }
}
