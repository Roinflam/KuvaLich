package pers.roinflam.kuvalich.client.gui.hud;

/**
 * 提示条的版面算术：尺寸、堆叠的上下界、最宽多宽、怎么折行。全是 GUI 像素的整数，不碰任何游戏类，
 * 几何可以脱离游戏单独拿出来核对（{@link NoticeHud} 只管时间轴和画）
 *
 * <h2>一条的尺寸</h2>
 * <pre>
 *   x0      x0+13                         x1−7   x1
 *   ╱‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾‾|   y0
 *   |  ◇   第一行字（字形格 8 行，行距 10）       |   y0+3 ~ y0+11
 *   |      第二行字                               |
 *   |___ˈˈ________________________________________╱   y1
 * </pre>
 * <p>左上、右下各切一个 {@link #CUT} 的角；左边 {@link #MARK_X} 处是这一类颜色的小菱（对准第一行的竖直中线），
 * 左下框线上挂两道冷色小刻度。字从 x0 + {@link #PAD_L} 起左对齐，右边留 {@link #PAD_R}；上下各留 3，
 * 一行高 14、两行 24、三行 34、四行 44。字的「宽」= 各字步进之和 − 1（最后一个字自带的 1 像素字距不算）。</p>
 *
 * <h2>放在哪</h2>
 * <p>横向居中，整叠的底在原版动作栏那行字上面 {@link #ACTIONBAR_GAP}（Forge 的动作栏跟着生命、护甲、饥饿、氧气条的行数上移，
 * 这里一起跟），顶不超过准星下沿再往下 {@link #CROSSHAIR_GAP}。最宽 {@link #MAX_BOX_W}、不超过屏宽的
 * {@link #MAX_BOX_FRAC}，并且保证只靠一侧的空地就放得下：屏幕两侧的通讯窗（含字幕）同时只开一扇，开着时整叠往另一侧让。</p>
 */
public final class NoticeLayout {

    public static final int PAD_L = 13;
    public static final int PAD_R = 7;
    public static final int PAD_T = 3;
    public static final int PAD_B = 3;
    /** 字形格高：原版像素字和 unifont 都画在 y ~ y + 8 */
    public static final int LINE_H = 8;
    public static final int PITCH = 10;
    public static final int CUT = 4;
    /** 小菱中心离左框多远、半高（半宽 = 半高 × 0.6） */
    public static final int MARK_X = 7;
    public static final float MARK_HH = 3.5F;
    /** 左下框线上两道刻度的位置（离左框）和长度 */
    public static final int TICK_A = 9;
    public static final int TICK_B = 12;
    public static final int TICK_LEN = 2;

    /** 两条之间 */
    public static final int STACK_GAP = 3;
    /** 和原版动作栏那行字之间 */
    public static final int ACTIONBAR_GAP = 3;
    /** 原版准星图标 15 × 15，画在 ((W − 15) / 2, (H − 15) / 2) */
    public static final int CROSSHAIR = 15;
    public static final int CROSSHAIR_GAP = 3;
    /** 离屏幕左右边、离通讯窗至少空这么多 */
    public static final int MARGIN = 4;
    public static final int COMM_GAP = 4;
    public static final int MAX_BOX_W = 260;
    public static final float MAX_BOX_FRAC = 0.62F;
    /** 再窄的屏幕，字区也至少留这么宽 */
    public static final int MIN_TEXT_W = 60;
    public static final int MAX_LINES = 4;

    /** 折行时优先在分句标点后面断：断在这里的那一行至少要有最宽的这个比例 */
    private static final float CLAUSE_FILL = 0.6F;

    // 字的类别（低 4 位）加「宽字」标记
    public static final byte SPACE = 0;
    public static final byte WORD = 1;
    public static final byte OPEN = 2;
    public static final byte CLOSE = 3;
    /** 分句标点：逗号、句号、冒号、分号、叹号、问号、顿号（也是 CLOSE，后面优先断行） */
    public static final byte CLAUSE = 4;
    public static final byte WIDE_BIT = 0x10;

    private NoticeLayout() {
    }

    // ==================== 尺寸 ====================

    public static int boxHeight(int lines) {
        return PAD_T + LINE_H + PITCH * (Math.max(1, lines) - 1) + PAD_B;
    }

    public static int boxWidth(int textW) {
        return PAD_L + textW + PAD_R;
    }

    /** 动作栏那行字的顶：Forge 把它画在 H − max(max(左列, 右列) + 9, 68) 往上 4 */
    public static int actionBarTop(int guiH, int leftHeight, int rightHeight) {
        return guiH - Math.max(Math.max(leftHeight, rightHeight) + 9, 68) - 4;
    }

    /** 整叠最下面那条的下沿 */
    public static int stackBottom(int guiH, int leftHeight, int rightHeight) {
        return actionBarTop(guiH, leftHeight, rightHeight) - ACTIONBAR_GAP;
    }

    /** 整叠最上面那条的上沿不能高过这里 */
    public static int stackTop(int guiH) {
        return (guiH - CROSSHAIR) / 2 + CROSSHAIR + CROSSHAIR_GAP;
    }

    /** 这么高的空地里一条最多几行（至少 1 行） */
    public static int maxLines(int avail) {
        int extra = avail - boxHeight(1);
        return Math.max(1, Math.min(MAX_LINES, 1 + (extra > 0 ? extra / PITCH : 0)));
    }

    /**
     * 一条最宽多宽（框外沿）
     *
     * @param leftInner  左侧通讯窗占位的右沿（GUI x），没有通讯窗传 {@code Float.NaN}
     * @param rightInner 右侧通讯窗占位的左沿，没有传 {@code Float.NaN}
     */
    public static int maxBoxWidth(int guiW, float leftInner, float rightInner) {
        int w = Math.min(MAX_BOX_W, Math.round(guiW * MAX_BOX_FRAC));
        w = Math.min(w, guiW - 2 * MARGIN);
        if (!Float.isNaN(rightInner)) {
            w = Math.min(w, (int) Math.floor(rightInner - COMM_GAP - MARGIN));
        }
        if (!Float.isNaN(leftInner)) {
            w = Math.min(w, (int) Math.floor(guiW - MARGIN - (leftInner + COMM_GAP)));
        }
        return Math.max(w, boxWidth(MIN_TEXT_W));
    }

    /** 框的左沿：居中取整（屏宽、框宽都是整数，落在整像素上） */
    public static int boxLeft(int guiW, int boxW) {
        return (guiW - boxW) / 2;
    }

    // ==================== 折行 ====================

    /** 字的类别；汉字、假名、谚文和全角标点带 {@link #WIDE_BIT} */
    public static byte classify(int cp) {
        byte wide = isWide(cp) ? WIDE_BIT : 0;
        if (cp == ' ' || cp == 0x3000) {
            return (byte) (SPACE | wide);
        }
        switch (cp) {
            case '，', '。', '、', '：', '；', '！', '？', ',', ';', ':', '!', '?', '．' -> {
                return (byte) (CLAUSE | wide);
            }
            case '）', '」', '』', '】', '〉', '》', '”', '’', ')', ']', '}', '.', '%', '…', '·' -> {
                return (byte) (CLOSE | wide);
            }
            case '（', '「', '『', '【', '〈', '《', '“', '‘', '(', '[', '{' -> {
                return (byte) (OPEN | wide);
            }
            default -> {
                return (byte) (WORD | wide);
            }
        }
    }

    public static boolean isWide(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        if (sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL) {
            return true;
        }
        // CJK 符号与标点、全角形式（unifont 按 16 像素宽画）
        return (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF01 && cp <= 0xFF5E);
    }

    private static int kind(byte c) {
        return c & 0x0F;
    }

    private static boolean wide(byte c) {
        return (c & WIDE_BIT) != 0;
    }

    /** 第 i − 1 和第 i 个字之间能不能断行 */
    static boolean breakable(byte[] cls, int i) {
        int a = kind(cls[i - 1]);
        int b = kind(cls[i]);
        if (a == SPACE || b == SPACE) {
            return true;
        }
        if (b == CLOSE || b == CLAUSE || a == OPEN) {
            return false;
        }
        return wide(cls[i - 1]) || wide(cls[i]);
    }

    /** 断在 b 前面时，这一行最后一个不是空格的字是不是分句标点 */
    private static boolean afterClause(byte[] cls, int s, int b) {
        int j = b - 1;
        while (j > s && kind(cls[j]) == SPACE) {
            j--;
        }
        return j >= s && kind(cls[j]) == CLAUSE;
    }

    /** [s, e) 这一段去掉行尾空格后的宽：步进之和 − 1 */
    public static int lineWidth(int[] adv, byte[] cls, int s, int e) {
        int end = trimEnd(cls, s, e);
        if (end <= s) {
            return 0;
        }
        int w = 0;
        for (int i = s; i < end; i++) {
            w += adv[i];
        }
        return w - 1;
    }

    private static int trimEnd(byte[] cls, int s, int e) {
        while (e > s && kind(cls[e - 1]) == SPACE) {
            e--;
        }
        return e;
    }

    /**
     * 贪心折行：一行装到放不下为止，往回找最近能断的地方；那一段里有分句标点、并且断在那里这一行也不算太短时，优先断在标点后面。
     * 一个词比整行还宽时硬断
     *
     * @param starts 每行第一个字（行首空格已跳过），可以传 {@code null} 只数行数
     * @param ends   每行末尾（不含行尾空格），可以传 {@code null}
     * @return 行数
     */
    public static int wrap(int[] adv, byte[] cls, int n, int maxW, int[] starts, int[] ends) {
        int lines = 0;
        int s = 0;
        while (true) {
            while (s < n && kind(cls[s]) == SPACE) {
                s++;
            }
            if (s >= n) {
                return lines;
            }
            int width = 0;
            int i = s;
            while (i < n) {
                int next = width + adv[i];
                // 空格可以挂在行尾，不算超宽
                if (next - 1 > maxW && kind(cls[i]) != SPACE) {
                    break;
                }
                width = next;
                i++;
            }
            int b;
            if (i >= n) {
                b = n;
            } else {
                int plain = -1;
                int clause = -1;
                for (int k = i; k > s; k--) {
                    if (!breakable(cls, k)) {
                        continue;
                    }
                    if (plain < 0) {
                        plain = k;
                    }
                    if (afterClause(cls, s, k) && lineWidth(adv, cls, s, k) >= CLAUSE_FILL * maxW) {
                        clause = k;
                        break;
                    }
                }
                b = clause >= 0 ? clause : plain >= 0 ? plain : Math.max(i, s + 1);
            }
            if (starts != null && lines < starts.length) {
                starts[lines] = s;
                ends[lines] = trimEnd(cls, s, b);
            }
            lines++;
            s = b;
        }
    }

    /**
     * 行数不变的前提下最窄能折到多宽（两三行时各行差不多长，不会一行满、一行只剩两个字）。
     * 最窄的那个宽度常把名字从中间劈开（「钻 / 石剑」），所以再往宽放一点（最多 {@link #NICE_SLACK} 倍），
     * 有哪个宽度能让每一处断行都落在空格或标点后面就用它
     *
     * @return 折行用的宽度，不超过 {@code maxW}
     */
    public static int balancedWidth(int[] adv, byte[] cls, int n, int maxW) {
        int lines = wrap(adv, cls, n, maxW, null, null);
        if (lines <= 1) {
            return maxW;
        }
        int lo = 1;
        int hi = maxW;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (wrap(adv, cls, n, mid, null, null) <= lines) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        if (wrap(adv, cls, n, lo, null, null) > lines) {
            return maxW;
        }
        int[] starts = new int[lines + 1];
        int[] ends = new int[lines + 1];
        int limit = Math.min(maxW, (int) (lo * NICE_SLACK));
        for (int w = lo; w <= limit; w++) {
            int k = wrap(adv, cls, n, w, starts, ends);
            if (k <= lines && niceBreaks(cls, n, starts, k)) {
                return w;
            }
        }
        return lo;
    }

    /** 找「断在空格、标点后面」的折法时，最多比最窄宽多少倍 */
    private static final float NICE_SLACK = 1.25F;

    /** 每一处断行前后是空格，或者上一行以标点收尾 */
    private static boolean niceBreaks(byte[] cls, int n, int[] starts, int lines) {
        for (int k = 1; k < lines; k++) {
            int b = starts[k];
            int j = b - 1;
            while (j > 0 && kind(cls[j]) == SPACE) {
                j--;
            }
            if (j < b - 1 || (b < n && kind(cls[b]) == SPACE)) {
                continue;
            }
            int last = kind(cls[j]);
            if (last != CLAUSE && last != CLOSE) {
                return false;
            }
        }
        return true;
    }
}
