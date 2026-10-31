package pers.roinflam.kuvalich.nemesis;

import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * 玄骸的名字：按种子拼、校验、转大写
 *
 * <p>名（1~2 个音节）+ 空格 + 姓（1 个音节，收尾是叠写辅音），音节表是自己编的，只用拉丁字母，
 * 不用 q / w / y / c / j：读起来不够粗，也省得拼出现成的英文词。拼出来的名字不合规（太长、三连字母、
 * 三个元音连写、不雅、撞上原作玄骸的名字）就用同一个随机数发生器接着抽，最多 {@link #MAX_TRIES} 次，
 * 还不行就用保底名 {@link #FALLBACK}。</p>
 *
 * <p><b>确定性</b>：同一个种子在任何机器上都拼出同一个名字，只用 {@link SplittableRandom}，
 * 下面几张表的顺序和权重定下以后不再改。要改表就另起一套、加版本号；名字本身存在玩家的存档里，
 * 所以老玩家现有的玄骸不会因为改表改名。</p>
 *
 * <p>在线玩家之间、和这个玩家最近几只的重名由诞生的地方另外躲（换种子重拼），这里只管单个名字合不合规。</p>
 */
public final class LichNames {

    /** 名字最长几个字符（含空格），管理命令起的名字也按这个查 */
    public static final int MAX_LENGTH = 15;
    /** 名、姓各自最长几个字母 */
    private static final int MAX_GIVEN = 8;
    private static final int MAX_FAMILY = 6;
    /** 拒收后最多再抽几次 */
    private static final int MAX_TRIES = 32;
    /** 几乎用不上的保底名 */
    public static final String FALLBACK = "Grax Vokk";
    /** 名有第二个音节的概率 */
    private static final float SECOND_SYLLABLE = 0.45F;

    // 每张表写成「内容 + 权重」，权重不写就是 1；空串表示这一位不出字母
    private static final Table G_ON = table(
            "dr", 1, "gr", 1, "kr", 1, "vr", 1, "zh", 1, "br", 1, "tr", 1, "sk", 1, "kh", 1, "gh", 1, "th", 1, "sl", 1,
            "v", 2, "z", 2, "d", 2, "r", 2, "t", 2, "g", 2, "k", 2, "h", 1);
    private static final Table G_V = table(
            "a", 4, "o", 4, "u", 3, "e", 2, "aa", 1, "oo", 1, "au", 1, "i", 1);
    private static final Table G_CODA = table(
            "k", 3, "g", 3, "z", 2, "r", 2, "x", 1, "th", 1, "sk", 1, "rg", 1, "ld", 1, "sh", 1, "m", 1, "n", 1, "", 2);
    private static final Table G2_ON = table(
            "k", 1, "g", 1, "r", 1, "z", 1, "d", 1, "t", 1, "v", 1, "b", 1, "gr", 1, "dr", 1);
    private static final Table G2_V = table(
            "a", 3, "o", 3, "u", 2, "e", 1, "i", 1);
    private static final Table G2_CODA = table(
            "k", 2, "g", 2, "r", 2, "z", 1, "sh", 1, "t", 1, "d", 1, "x", 1, "", 3);
    private static final Table F_ON = table(
            "b", 1, "d", 1, "f", 1, "g", 1, "h", 1, "k", 1, "l", 1, "m", 1, "r", 1, "s", 1, "t", 1, "v", 1, "z", 1,
            "kr", 1, "gr", 1, "br", 1);
    private static final Table F_V = table(
            "a", 3, "o", 3, "u", 3, "e", 2, "i", 1);
    private static final Table F_CODA = table(
            "kk", 3, "gg", 3, "rr", 2, "ss", 2, "zz", 1, "tt", 1, "bb", 1, "dd", 1, "mm", 1, "ll", 1);

    /**
     * 不雅词（小写子串，整串去掉空格后比对），中英都有。命中就重抽；管理命令起名也查这张表
     */
    public static final Set<String> BLOCK = Set.of(
            "fuk", "fuck", "fag", "nig", "cum", "sex", "dik", "dick", "cok", "cock", "tit", "ass", "sht", "shit", "kkk",
            "nazi", "hitl", "rape", "porn", "cunt", "kunt", "slut", "whor", "bitch", "bich", "puss", "jew", "gay",
            "nmsl", "cnm", "tmd", "sb", "jb", "diao", "cao");

    /**
     * 原作里出现过的玄骸名字（小写整词）。我们的姓是「首辅音 + 元音 + 叠写尾」，和原作的姓同构，
     * 像 Fabb、Medd、Fett、Furr、Lorr、Tubb 都能从表里拼出来；名或姓整个词撞上这张表就重抽。
     * 以后再见到原作的名字往这里加，音节表不动
     */
    public static final Set<String> ORIGINAL = Set.of(
            "axegikk", "fabb", "olikigg", "rapp", "hahko", "medd", "nubb", "hiamoagg", "tubb", "lorr", "voronuk",
            "furr", "bhekk", "fahloo", "bigb", "fett");

    private LichNames() {
    }

    /** 同一个种子永远得到同一个名字，名和姓都是首字母大写，如 {@code Drokag Tuzz} */
    public static String generate(long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        for (int i = 0; i < MAX_TRIES; i++) {
            String given = G_ON.pick(rng) + G_V.pick(rng) + G_CODA.pick(rng);
            if (rng.nextFloat() < SECOND_SYLLABLE) {
                given = given + G2_ON.pick(rng) + G2_V.pick(rng) + G2_CODA.pick(rng);
            }
            String family = F_ON.pick(rng) + F_V.pick(rng) + F_CODA.pick(rng);
            String name = cap(given) + " " + cap(family);
            if (passesRules(name)) {
                return name;
            }
        }
        return FALLBACK;
    }

    /**
     * 拼出来的名字合不合规：名 ≤ 8 个字母、姓 ≤ 6 个字母、整串 ≤ 15；没有同一个字母连着出现三次；
     * 名里没有三个元音连写；不含不雅词；名、姓都不是原作玄骸的名字
     */
    public static boolean passesRules(String name) {
        if (name == null || name.length() > MAX_LENGTH) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int space = lower.indexOf(' ');
        if (space <= 0 || space != lower.lastIndexOf(' ') || space == lower.length() - 1) {
            return false;
        }
        String given = lower.substring(0, space);
        String family = lower.substring(space + 1);
        if (given.length() > MAX_GIVEN || family.length() > MAX_FAMILY) {
            return false;
        }
        if (hasTripleLetter(lower) || hasTripleVowel(given) || isProfane(lower)) {
            return false;
        }
        return !ORIGINAL.contains(given) && !ORIGINAL.contains(family);
    }

    /**
     * 名字能不能用（诞生时、管理命令起名时都查）：不为空、首尾没有空白、不超过 {@link #MAX_LENGTH}、
     * 不含控制字符和格式代码 {@code §}、不含不雅词。音节规则只约束自动拼出来的名字，管理命令起名不查
     */
    public static boolean isAllowed(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_LENGTH || !name.equals(name.strip())) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '§' || Character.isISOControl(ch)) {
                return false;
            }
        }
        return !isProfane(name.toLowerCase(Locale.ROOT));
    }

    /** 名牌、Boss 条、档案、通讯名牌用的全大写写法；台词里、聊天里用原样（首字母大写） */
    public static String upper(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }

    // ==================== 规则 ====================

    private static boolean isProfane(String lower) {
        String squeezed = lower.replace(" ", "");
        for (String bad : BLOCK) {
            if (squeezed.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTripleLetter(String lower) {
        for (int i = 2; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c != ' ' && c == lower.charAt(i - 1) && c == lower.charAt(i - 2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTripleVowel(String word) {
        int run = 0;
        for (int i = 0; i < word.length(); i++) {
            run = isVowel(word.charAt(i)) ? run + 1 : 0;
            if (run >= 3) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ==================== 带权重的表 ====================

    /** 一张带权重的音节表：按 {@code rng.nextInt(总权重)} 落在哪一格取哪一格 */
    private static final class Table {
        private final String[] parts;
        private final int[] cumulative;
        private final int total;

        private Table(String[] parts, int[] weights) {
            this.parts = parts;
            this.cumulative = new int[weights.length];
            int sum = 0;
            for (int i = 0; i < weights.length; i++) {
                sum += weights[i];
                cumulative[i] = sum;
            }
            this.total = sum;
        }

        String pick(SplittableRandom rng) {
            int r = rng.nextInt(total);
            for (int i = 0; i < cumulative.length; i++) {
                if (r < cumulative[i]) {
                    return parts[i];
                }
            }
            return parts[parts.length - 1];
        }
    }

    /** 参数按「内容, 权重, 内容, 权重…」排 */
    private static Table table(Object... pairs) {
        int n = pairs.length / 2;
        String[] parts = new String[n];
        int[] weights = new int[n];
        for (int i = 0; i < n; i++) {
            parts[i] = (String) pairs[2 * i];
            weights[i] = (Integer) pairs[2 * i + 1];
        }
        return new Table(parts, weights);
    }
}
