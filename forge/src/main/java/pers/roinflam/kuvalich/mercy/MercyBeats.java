package pers.roinflam.kuvalich.mercy;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * 处决的节拍表，服务端和客户端共用一份。毫秒从处决开始那一帧算，每个套路一份。
 *
 * <p>服务端的 tick 一律从毫秒派生，不手抄：和客户端同时发生的拍四舍五入；必须落在客户端那一拍之后的
 * （挣开时的转身、奴仆落地换站位）向上取整；服务端独有、客户端没有对应画面的拍（第一刀后的通讯、判错后的台词、
 * 移除、聊天、进度）直接写 tick。</p>
 *
 * <p>判错 / 挣开之后的各拍都从「那一格填满」的时刻 g 算。成功段跟着第三刀走，第三刀挪了整段一起挪。</p>
 *
 * <p>{@code tools/anim/check_mercy_anim.py} 用正则读下面 {@code lich(...)} 那几行的刀时刻，和动画脚本里的表比，
 * 改行的写法要一起改脚本。</p>
 */
public final class MercyBeats {

    public static final int TICK_MS = 50;

    // ==================== 玄骸：相对量 ====================

    /** 起手抓住之后硬切进本套机位 */
    private static final int CUT_IN = 400;

    // 成功段，相对第三刀刺入
    /** 不和谐垫音、压混音 */
    private static final int VERDICT = 550;
    /** 刀口小火花 */
    private static final int LAST_SPARK = 570;
    /** 收玄骸的通讯窗 */
    private static final int COMM_DISMISS = 1000;
    /** 切跪地特写，选择窗口也在这一刻开 */
    private static final int CLOSEUP = 2000;
    /** 界面整组淡出 */
    private static final int HUD_SUCCESS_FADE = 2730;
    /** 切膝高第三人称（短版在这交还） */
    private static final int THIRD = 4650;
    /** 抽刀、消散 */
    private static final int DRAW_OUT = 4860;
    /** 服务端让它死（客户端从这一刻起不把实体消失当中断） */
    private static final int DEATH = 4880;
    /** 归还没收物品的光点；服务端同一刻发奖励、解锁 */
    private static final int RETURN_LIGHT = 5060;
    /** 交还控制；服务端同一刻发解密界面的完成提示 */
    private static final int SUCCESS_END = 5560;

    // 界面，相对第三格填满
    /** 格下小点出现 */
    private static final int HUD_DOTS = -130;
    /** 用掉的那颗小点开始画斜杠 */
    private static final int HUD_DOT_SLASH = 100;

    // 踏背下刺，相对第二刀刺入
    /** 拔刀后化作一道红光 */
    private static final int E_BLINK = 470;
    /** 在它正面现身 */
    private static final int E_APPEAR = 790;

    // 判错，相对判错那一格填满（g）
    /** 挣脱的低吼声（客户端），和服务端的台词不是一回事 */
    private static final int FAIL_GROWL = 300;
    /** 挣脱、处决者被推开（客户端换挣脱片段；服务端同一刻开始推） */
    private static final int FAIL_BREAKFREE = 700;
    private static final int FAIL_WIDE = 2000;
    /** 暖色冲击、化雾；服务端同一刻进离场 */
    private static final int FAIL_ESCAPE = 2400;
    /** 交还；服务端同一刻解锁 */
    private static final int FAIL_END = 2750;
    /** 晋升横幅出来 */
    private static final int FAIL_BANNER = 2900;
    /** 原 HUD 回来（横幅出来以后） */
    private static final int FAIL_HUD = 3400;
    /** 服务端独有：台词、移除、给 Oull、记进度（tick，相对 g 那一 tick） */
    private static final int FAIL_TAUNT_LINE_TICKS = 26, FAIL_REMOVE_TICKS = 58, FAIL_CHAT_TICKS = 60,
            FAIL_PROGRESS_TICKS = 70;

    // 挣开站起，相对最后那一格填满（g）
    private static final int ADVANCE_GROWL = 300;
    /** 挣开、处决者被推开，开始站起来；服务端同一刻推人、进起身 */
    private static final int ADVANCE_BREAKFREE = 700;
    /** 背对处决者的套路边站边转过来（对着起身动作的 0.12~0.62 秒）；服务端在转完之后才跟上 */
    private static final int ADVANCE_TURN_FROM = 820, ADVANCE_TURN_TO = 1320;
    private static final int ADVANCE_WIDE = 1200;
    /** 站稳、交还；服务端同一刻回到能打的状态、解锁 */
    private static final int ADVANCE_END = 2300;

    /** 服务端独有：第一刀之后玄骸在通讯窗里说一句（tick，从处决开始算） */
    private static final int STAB_COMM_TICK = 20;

    // ==================== 奴仆 ====================

    /** 服务端让它死 */
    private static final int THRALL_DEATH = 1500;
    /** 收势时刀模先缩回 */
    private static final int THRALL_BLADE_GONE = 2050;
    /** 交还；服务端同一刻解锁 */
    private static final int THRALL_END = 2200;

    private static final Map<MercyVariant, Lich> LICH = new EnumMap<>(MercyVariant.class);
    private static final Map<MercyVariant, Thrall> THRALL = new EnumMap<>(MercyVariant.class);

    static {
        // 相邻两刀间隔的下限：玄骸挨刀片段要在刺入后 0.7 秒内回到末姿势（判错时从那里挣脱），再留一点余量。
        // 踏背下刺第二、三刀之间夹着化光（+470）、闪过去（320）和现身后的蓄力（200），所以是 990
        //    套路                    刺入                     填满                     刀间隔下限
        lich(MercyVariant.LICH_A, stabs(790, 1680, 2670), fills(830, 1680, 2670), gaps(850, 850));
        lich(MercyVariant.LICH_B, stabs(790, 1680, 2670), fills(830, 1680, 2670), gaps(850, 850));
        lich(MercyVariant.LICH_C, stabs(790, 1680, 2670), fills(830, 1680, 2670), gaps(850, 850));
        lich(MercyVariant.LICH_D, stabs(790, 1680, 2670), fills(830, 1680, 2670), gaps(850, 850));
        lich(MercyVariant.LICH_E, stabs(790, 1680, 2670), fills(830, 1680, 2670), gaps(850, 990));

        // 落地：绕到身后的两套整身轨道走到头的时刻，服务端这之后才把站位换过去
        //      套路                     刺入  落地
        thrall(MercyVariant.THRALL_1, 900, -1);
        thrall(MercyVariant.THRALL_2, 950, -1);
        thrall(MercyVariant.THRALL_3, 1000, -1);
        thrall(MercyVariant.THRALL_4, 800, 1640);
        thrall(MercyVariant.THRALL_5, 800, 1620);
    }

    private MercyBeats() {
    }

    /** 玄骸套路的节拍；传奴仆套路会抛异常 */
    public static Lich lich(MercyVariant v) {
        Lich b = LICH.get(v);
        if (b == null) {
            throw new IllegalArgumentException("not a lich variant: " + v);
        }
        return b;
    }

    /** 奴仆套路的节拍；传玄骸套路会抛异常 */
    public static Thrall thrall(MercyVariant v) {
        Thrall b = THRALL.get(v);
        if (b == null) {
            throw new IllegalArgumentException("not a thrall variant: " + v);
        }
        return b;
    }

    /** 每一刀刺入的时刻（副本）：玄骸三个，奴仆一个 */
    public static int[] stabMs(MercyVariant v) {
        return v.thrall ? new int[]{thrall(v).stab} : lich(v).stabMs();
    }

    /** 毫秒换成 tick，四舍五入（和客户端同时发生的拍） */
    public static int roundTicks(int ms) {
        return Math.round(ms / (float) TICK_MS);
    }

    /** 毫秒换成 tick，向上取整（必须落在客户端那一拍之后） */
    public static int ceilTicks(int ms) {
        return (ms + TICK_MS - 1) / TICK_MS;
    }

    public static final class Lich {
        public final MercyVariant variant;
        private final int[] stab;
        private final int[] fill;
        private final int[] minGap;
        private final int[] fillTick;

        public final int cutIn;
        public final int verdict, lastSpark, commDismiss, closeup, hudSuccessFade, third, drawOut, death, returnLight,
                successEnd;
        /** 选择窗口开（先等于跪地特写） */
        public final int choiceOpen;
        public final int hudDots, hudDotSlash;
        /** 踏背下刺的化光、现身；别的套路是 −1 */
        public final int blink, appear;

        public final int failGrowl = FAIL_GROWL, failBreakFree = FAIL_BREAKFREE, failWide = FAIL_WIDE,
                failEscape = FAIL_ESCAPE, failEnd = FAIL_END, failBanner = FAIL_BANNER, failHud = FAIL_HUD;
        public final int advanceGrowl = ADVANCE_GROWL, advanceBreakFree = ADVANCE_BREAKFREE,
                advanceTurnFrom = ADVANCE_TURN_FROM, advanceTurnTo = ADVANCE_TURN_TO, advanceWide = ADVANCE_WIDE,
                advanceEnd = ADVANCE_END;

        // 服务端（tick，从处决开始算；判错、挣开的相对 g 那一 tick）
        /** 定案 = 第一格填满（客户端同一刻画第一格） */
        public final int settleTick;
        /** 第一刀之后的通讯，在第一格填满之后 */
        public final int stabCommTick;
        /** 背对处决者的套路成功时，切特写那一刻把它转过来 */
        public final int closeupTurnTick;
        public final int successDeathTick, successRewardTick, successCompleteTick;
        public final int failPushTicks, failTauntLineTicks, failLeaveTicks, failUnlockTicks, failRemoveTicks,
                failChatTicks, failProgressTicks;
        /** 挣开：推人和起身同一拍；转身向上取整，落在客户端转完之后，免得交还时又拧回去 */
        public final int advanceRiseTicks, advanceTurnTicks, advanceStandTicks;

        private Lich(MercyVariant variant, int[] stab, int[] fill, int[] minGap) {
            this.variant = variant;
            this.stab = stab;
            this.fill = fill;
            this.minGap = minGap;
            int s3 = stab[2];
            cutIn = CUT_IN;
            verdict = s3 + VERDICT;
            lastSpark = s3 + LAST_SPARK;
            commDismiss = s3 + COMM_DISMISS;
            closeup = s3 + CLOSEUP;
            choiceOpen = closeup;
            hudSuccessFade = s3 + HUD_SUCCESS_FADE;
            third = s3 + THIRD;
            drawOut = s3 + DRAW_OUT;
            death = s3 + DEATH;
            returnLight = s3 + RETURN_LIGHT;
            successEnd = s3 + SUCCESS_END;
            hudDots = fill[2] + HUD_DOTS;
            hudDotSlash = fill[2] + HUD_DOT_SLASH;
            boolean e = variant == MercyVariant.LICH_E;
            blink = e ? stab[1] + E_BLINK : -1;
            appear = e ? stab[1] + E_APPEAR : -1;

            fillTick = new int[3];
            for (int i = 0; i < 3; i++) {
                fillTick[i] = roundTicks(fill[i]);
            }
            settleTick = fillTick[0];
            stabCommTick = STAB_COMM_TICK;
            closeupTurnTick = roundTicks(closeup);
            successDeathTick = roundTicks(death);
            successRewardTick = roundTicks(returnLight);
            successCompleteTick = roundTicks(successEnd);
            failPushTicks = roundTicks(FAIL_BREAKFREE);
            failTauntLineTicks = FAIL_TAUNT_LINE_TICKS;
            failLeaveTicks = roundTicks(FAIL_ESCAPE);
            failUnlockTicks = roundTicks(FAIL_END);
            failRemoveTicks = FAIL_REMOVE_TICKS;
            failChatTicks = FAIL_CHAT_TICKS;
            failProgressTicks = FAIL_PROGRESS_TICKS;
            advanceRiseTicks = roundTicks(ADVANCE_BREAKFREE);
            advanceTurnTicks = ceilTicks(ADVANCE_TURN_TO);
            advanceStandTicks = roundTicks(ADVANCE_END);
        }

        public int[] stabMs() {
            return stab.clone();
        }

        /** 第 {@code i} 刀（从 0 起）刺入 */
        public int stab(int i) {
            return stab[i];
        }

        /** 第 {@code k} 格（1~3）填满发光 */
        public int fill(int k) {
            return fill[Math.max(1, Math.min(3, k)) - 1];
        }

        /** 第 {@code k} 格（1~3）填满的那一 tick */
        public int fillTick(int k) {
            return fillTick[Math.max(1, Math.min(3, k)) - 1];
        }

        /** 第 {@code i} 刀和第 {@code i + 1} 刀（从 0 起）之间至少隔多久 */
        public int minGap(int i) {
            return minGap[i];
        }

        /**
         * 这一场服务端会动手的各个时刻（毫秒，tick × 50，升序）：定案、通讯、推开、转身、离场、解锁、死亡、发奖等。
         * 客户端的演出钟只能在这些时刻之间拧，到了这些时刻要回到原样
         */
        public int[] serverVisibleMs(int stabs, int prefix) {
            int[] t;
            if (prefix < stabs) {
                int g = fillTick(prefix + 1);
                t = new int[]{settleTick, prefix >= 1 ? stabCommTick : settleTick, g + failPushTicks,
                        g + failTauntLineTicks, g + failLeaveTicks, g + failUnlockTicks, g + failRemoveTicks,
                        g + failChatTicks, g + failProgressTicks};
            } else if (stabs < 3) {
                int g = fillTick(stabs);
                t = new int[]{settleTick, stabCommTick, g + advanceRiseTicks, g + advanceTurnTicks, g + advanceStandTicks};
            } else {
                t = new int[]{settleTick, stabCommTick, closeupTurnTick, successDeathTick, successRewardTick,
                        successCompleteTick};
            }
            int[] ms = Arrays.stream(t).distinct().sorted().toArray();
            for (int i = 0; i < ms.length; i++) {
                ms[i] *= TICK_MS;
            }
            return ms;
        }
    }

    public static final class Thrall {
        public final MercyVariant variant;
        public final int stab;
        public final int death = THRALL_DEATH, bladeGone = THRALL_BLADE_GONE, end = THRALL_END;
        /** 整身轨道走到身后的时刻；不换站位的套路是 −1 */
        public final int land;
        /** 定案 = 刺入那一 tick */
        public final int settleTick;
        public final int deathTick, unlockTick;
        /** 落地换站位，向上取整，落在整身轨道走完之后；不换站位的套路是 −1 */
        public final int landTick;

        private Thrall(MercyVariant variant, int stab, int land) {
            this.variant = variant;
            this.stab = stab;
            this.land = land;
            settleTick = Math.max(1, roundTicks(stab));
            deathTick = roundTicks(death);
            unlockTick = roundTicks(end);
            landTick = land < 0 ? -1 : ceilTicks(land);
        }
    }

    private static void lich(MercyVariant v, int[] stab, int[] fill, int[] minGap) {
        LICH.put(v, new Lich(v, stab, fill, minGap));
    }

    private static void thrall(MercyVariant v, int stab, int land) {
        THRALL.put(v, new Thrall(v, stab, land));
    }

    private static int[] stabs(int... ms) {
        return ms;
    }

    private static int[] fills(int... ms) {
        return ms;
    }

    private static int[] gaps(int... ms) {
        return ms;
    }
}
