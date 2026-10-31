package pers.roinflam.kuvalich.client.comm;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.lich.LichVoice;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.LichLook;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.config.GuideDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 游戏内通讯窗：Lotus、Ordis、玄骸说话时在屏幕侧边开的一扇带肖像的窗
 *
 * <h2>版面</h2>
 * <p>全按帧缓冲的物理像素算（S = 高、W = 宽），覆盖层先把姿态缩放 1/GUI 倍数再画，所以换分辨率、换 GUI 倍数，
 * 窗在屏幕上的比例不变：窗高 {@code max(0.19S, 120)}、宽 = 高 × 1.5；玄骸和 Ordis 在右（右缘 0.955W、下缘 0.656S），
 * Lotus 在左（左缘 0.02W、下缘 0.61S，比右窗高 0.045S）。字幕在窗下方左对齐，宽 = 窗宽 + 0.01W，最多 3 行；
 * 名牌压在窗下缘以内、窗的外侧下角。字号是整数倍：{@code max(round(0.015S / 8), ceil(GUI 倍数 / 2), 1)}。</p>
 *
 * <h2>时间轴（0 = 窗开始出现，演出钟）</h2>
 * <ul>
 *   <li>0：开框。友方 100 ms 从「高 25%、宽 130%、α 0.6」的扁横带拉开；玄骸 130 ms 横向拖影，三段到位。接通声起播；</li>
 *   <li>带「到场」标记的：开框之后接 200 ms 青色故障；</li>
 *   <li>330（友方）/ 670（玄骸）：字幕整句擦出，用时 clamp(汉字 × 7 + 拉丁 × 3.5, 150, 700) ms；玄骸同时开始嘟囔、垫静电；</li>
 *   <li>字幕开始 + 550：名牌淡入 100 ms；</li>
 *   <li>字幕开始 + H：字幕直接消失，H = clamp(1600 + 120 × (汉字 + 0.4 × 拉丁), 2500, 7000)，玄骸还不短于嘟囔时长 + 300 ms；</li>
 *   <li>再 500 ms 收起：友方压成一条线 130 ms；玄骸先亮 60 ms 再溶解 270 ms；收完再 150 ms，名牌 100 ms 淡出。</li>
 * </ul>
 * <p>同一个人接着说：窗不收，旧字幕消失后空 900 ms 直接擦出下一句，名牌不重放。换人：前一句字幕消失 1800 ms 后才开下一扇窗
 * （队列里还压着两句以上时 1400 ms）。全局只开一扇窗。字幕超过 3 行时按句号、逗号拆成同一个人连说的几段。</p>
 *
 * <h2>队列</h2>
 * <p>最多压 3 句；不是插队的句子进队 8 s 还没轮到就丢掉。插队（{@code URGENT}）：当前窗快速收起（字幕立刻消失、直接收 130 ms），
 * 400 ms 后开新窗。随机嘲讽（{@code AMBIENT}）：窗忙或队列里有东西就直接丢。台词默认只在窗里，下面几种情况在本地聊天栏补一行
 * 「[名牌] 台词」：关了通讯窗、这句被丢掉（随机嘲讽除外）、服务端要求同时进聊天栏、或者打开了 {@code comm_chat_copy}。</p>
 *
 * <p>时间轴走演出钟，单人暂停时停住，继续后接着走。死亡界面上也画（覆盖层在死亡界面底下会被染红，所以死亡界面打开时改在界面画完之后画）。
 * 窗不在屏时不建任何东西、不跑音节表。</p>
 *
 * <h2>肖像</h2>
 * <p>窗里的画面交给 {@link CommPortraits#draw}：传进去的框永远是满尺寸的窗，开框的压扁、收起的压线、到场的横向错位都由这里用
 * 姿态矩阵做（肖像按当前姿态矩阵把缓冲画上去即可），玄骸开框的重影是第二次调用（错开 d、α 另给）。溶解、整窗提亮、
 * 下半窗的青色故障这几样要在合成时做，调用期间从 {@link #currentDrawFx()} 读；肖像画不出来（返回 false）时这里画一块暗色底兜底。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CommHud {

    /** 覆盖层的 id，处决运镜时用它把通讯窗放进白名单 */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "comm");

    /**
     * 当前这扇窗的时刻表，全是演出钟毫秒
     *
     * @param openMs          开始出现
     * @param textStartMs     字幕开始擦出
     * @param textEndMs       字幕消失
     * @param closeMs         开始收起（同一个人还可能接着说时是预计值）
     * @param syllableOnsetMs 嘟囔每个音节的起点，相对 {@code textStartMs}；不是玄骸、或这句不嘟囔时为空数组
     */
    public record Timeline(long openMs, long textStartMs, long textEndMs, long closeMs, int[] syllableOnsetMs) {
    }

    // ==================== 时间轴常量（毫秒） ====================

    static final int OPEN_FRIEND_MS = 100;
    static final int OPEN_LICH_MS = 130;
    static final int TEXT_FRIEND_MS = 330;
    static final int TEXT_LICH_MS = 670;
    static final int GLITCH_MS = 200;
    static final int GLITCH_STEP_MS = 40;
    static final int NAME_DELAY_MS = 550;
    static final int NAME_FADE_MS = 100;
    static final int NAME_LINGER_MS = 150;
    static final int HOLD_BASE_MS = 1600;
    static final int HOLD_PER_UNIT_MS = 120;
    static final int HOLD_MIN_MS = 2500;
    static final int HOLD_MAX_MS = 7000;
    static final int MUTTER_TAIL_MS = 300;
    static final int AFTER_TEXT_MS = 500;
    static final int CLOSE_FRIEND_MS = 130;
    static final int CLOSE_LICH_FLASH_MS = 60;
    static final int CLOSE_LICH_DISSOLVE_MS = 270;
    static final int CLOSE_QUICK_MS = 130;
    static final int SAME_SPEAKER_GAP_MS = 900;
    static final int SWITCH_GAP_MS = 1800;
    static final int SWITCH_GAP_BUSY_MS = 1400;
    static final int URGENT_DELAY_MS = 400;
    static final int SPAM_GAP_MS = 400;
    static final int QUEUE_MAX = 3;
    static final int QUEUE_EXPIRE_MS = 8000;
    static final int MAX_LINES = 3;
    static final int VOICE_FADE_MS = 80;
    static final int STATIC_FADE_MS = 150;
    static final float STATIC_VOLUME = 0.5F;
    static final float OPEN_SOUND_VOLUME = 1.0F;
    /** Ordis 故障段的「两拍」：擦出前沿走到 {@code <x>} 段时肖像碎块错开、整窗色差，一拍这么长，共两拍 */
    static final int KICK_BEAT_MS = 38;

    /** 玄骸收起前提亮的倍数 */
    static final float CLOSE_FLASH = 1.35F;
    /** 到场故障下半窗染青的起始强度 */
    static final float GLITCH_TINT = 0.35F;
    static final int GLITCH_RGB = 0x4FE8FF;
    /** 窗底的不透明度（没被人物占住的地方透出一成游戏画面） */
    static final float PLATE_ALPHA = 0.9F;

    // ==================== 状态 ====================

    /** 队列里的一句 */
    private static final class Entry {
        final CommLine line;
        /** 已经补过聊天行 */
        boolean copied;
        /** 插队的句子最早什么时候开窗 */
        long urgentAt;
        /** 从这一刻起它排在队首、前面也没有窗在说话（过期按这个算）；{@link Long#MIN_VALUE} = 还没轮到 */
        long frontSinceMs = Long.MIN_VALUE;

        Entry(CommLine line) {
            this.line = line;
        }
    }

    /** 一扇窗 */
    private static final class Win {
        CommSpeaker who;
        @Nullable
        LichLook look;
        Component name;
        boolean lich;
        boolean arrival;
        long openMs;
        int openDur;
        boolean opened;
        /** 当前这一段字幕；两段之间、或者已经在收的时候为 null */
        @Nullable
        Seg seg;
        /** 同一句拆出来、还没轮到的几段（带故障段标记的原文） */
        final ArrayDeque<String> parts = new ArrayDeque<>(2);
        /** 拆段的那一句（后几段沿用它的标志位和外观） */
        CommLine partsOf;
        /** 名牌开始淡入 */
        long nameInMs = Long.MAX_VALUE;
        long closeMs = Long.MAX_VALUE;
        int closeDur;
        boolean quick;

        long closeEndMs() {
            return closeMs == Long.MAX_VALUE ? Long.MAX_VALUE : closeMs + closeDur;
        }

        long doneMs() {
            return closeMs == Long.MAX_VALUE ? Long.MAX_VALUE : closeMs + closeDur + NAME_LINGER_MS + NAME_FADE_MS;
        }
    }

    /** 一段字幕 */
    private static final class Seg {
        CommLine src;
        String raw;
        String plain;
        MutableComponent text;
        @Nullable
        List<FormattedCharSequence> lines;
        int wrapWidth = -1;
        long textStartMs;
        long textEndMs;
        int wipeMs;
        int han;
        int latin;
        boolean shout;
        /** 擦出前沿走到每个故障段起点的时刻（相对字幕开始），和 {@link #lines} 一起按折行宽度算；没有故障段为空数组 */
        @Nullable
        float[] kickAtMs;
        @Nullable
        LichVoice.Schedule schedule;
        boolean started;
        boolean ended;
        @Nullable
        KuvaSoundInstance staticLoop;
        int talkEntityId = -1;
    }

    private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>(QUEUE_MAX + 1);
    @Nullable
    private static Win win;
    /** 上一句字幕消失的时刻；换人间隔从这里算 */
    private static long lastTextEndMs = Long.MIN_VALUE / 4;
    /** 上一扇窗开的时刻（全局 400 ms 防刷屏） */
    private static long lastOpenMs = Long.MIN_VALUE / 4;
    /** 调试：把时间轴冻在开窗后的第几毫秒；负数 = 不冻 */
    private static long frozenAt = -1L;
    @Nullable
    private static Timeline cachedTimeline;

    /** 肖像合成时读的效果参数；只在本类调用 {@link CommPortraits#draw} 期间有值 */
    private static final DrawFx FX = new DrawFx();
    private static boolean drawingFx;

    private CommHud() {
    }

    // ==================== 对外 ====================

    /** 服务端发来的一句台词，排进队列 */
    public static void enqueue(CommLine line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || line == null) {
            return;
        }
        Entry e = new Entry(line);
        if (line.chatCopy() || EffectsDisplayConfig.commChatCopy() || !EffectsDisplayConfig.commHud()) {
            toChat(line);
            e.copied = true;
        }
        if (!EffectsDisplayConfig.commHud()) {
            talkWithoutHud(line);
            return;
        }
        long clock = KuvaFxDirector.clockMs();
        if (line.ambient() && !line.urgent() && (win != null || !QUEUE.isEmpty())) {
            return;
        }
        if (line.urgent()) {
            // 开着的窗快速收起（已经在收的让它收完），400 ms 后开新窗；同一时间只会有一扇窗
            Win w = win;
            if (w != null && !closing(w, now(clock))) {
                quickClose(w, now(clock));
            }
            e.urgentAt = w != null ? clock + URGENT_DELAY_MS : clock;
            if (QUEUE.size() >= QUEUE_MAX) {
                dropLastNonUrgent();
            }
            QUEUE.addFirst(e);
            return;
        }
        if (QUEUE.size() >= QUEUE_MAX) {
            drop(e);
            return;
        }
        QUEUE.addLast(e);
    }

    /** 本地直接开一句（调试命令、以后单机提示用），内部组一个 {@link CommLine} 走同一条路 */
    public static void show(CommSpeaker who, @Nullable Component name, Component line, int flags,
                            @Nullable LichLook look) {
        LichLook l = who == CommSpeaker.LICH ? (look != null ? look : LichLook.NONE) : null;
        enqueue(new CommLine(who, flags, name, line, l, 0, KuvaFxDirector.clockMs()));
    }

    /** 这个人正在说话就快速收起当前窗，不开新窗；不是他在说话什么都不做 */
    public static void dismiss(CommSpeaker who) {
        Win w = win;
        if (w == null || w.who != who) {
            return;
        }
        long now = now(KuvaFxDirector.clockMs());
        if (!closing(w, now)) {
            quickClose(w, now);
        }
    }

    /** 当前窗的时刻表；没有窗时为 {@code null} */
    @Nullable
    public static Timeline timeline() {
        Win w = win;
        if (w == null) {
            return null;
        }
        if (cachedTimeline == null) {
            Seg s = w.seg;
            long textStart = s != null ? s.textStartMs : w.openMs + (w.lich ? TEXT_LICH_MS : TEXT_FRIEND_MS);
            long textEnd = s != null ? s.textEndMs : Math.min(lastTextEndMs, w.closeMs);
            long close = w.closeMs != Long.MAX_VALUE ? w.closeMs : textEnd + AFTER_TEXT_MS;
            int[] onsets = s != null && s.schedule != null ? s.schedule.onsetMs() : new int[0];
            cachedTimeline = new Timeline(w.openMs, textStart, textEnd, close, onsets);
        }
        return cachedTimeline;
    }

    /** 现在有没有窗开着 */
    public static boolean isOpen() {
        return win != null;
    }

    /** 当前窗是谁在说话；没有窗时为 {@code null}（肖像提前准备离屏缓冲时用） */
    @Nullable
    public static CommSpeaker currentSpeaker() {
        Win w = win;
        return w != null ? w.who : null;
    }

    /** 当前窗的玄骸外观；不是玄骸或没有窗时为 {@code null} */
    @Nullable
    public static LichLook currentLook() {
        Win w = win;
        return w != null ? w.look : null;
    }

    /** 当前窗里的人正在说话（字幕在屏） */
    public static boolean isTalking() {
        Win w = win;
        if (w == null || w.seg == null) {
            return false;
        }
        long now = now(KuvaFxDirector.clockMs());
        return now >= w.seg.textStartMs && now < w.seg.textEndMs;
    }

    /** 当前分辨率下通讯窗的宽、高（物理像素） */
    public static int windowWidthPx() {
        return Math.round(1.5F * windowHeightPx());
    }

    public static int windowHeightPx() {
        return Math.max(Math.round(0.19F * Minecraft.getInstance().getWindow().getHeight()), 120);
    }

    /**
     * 肖像合成时要做的效果；只在本类调用 {@link CommPortraits#draw} 的那一次期间返回非空（指南里画肖像时为空）
     */
    @Nullable
    public static DrawFx currentDrawFx() {
        return drawingFx ? FX : null;
    }

    /**
     * 通讯窗这一帧要肖像合成时做的几样效果。框的缩放、横向错位已经在姿态矩阵里，透明度已经在 {@code alpha} 参数里，
     * 这里只放合成着色器才做得出来的
     */
    public static final class DrawFx {
        float dissolve;
        float brighten = 1.0F;
        float glitch;
        boolean ghostPass;
        boolean lich;
        boolean kick;

        /**
         * 按噪声溶解的进度 0~1（玄骸收起）。{@code alpha} 参数里不含它；画不出溶解（退路）时请把不透明度再乘 {@code 1 − dissolve}
         */
        public float dissolve() {
            return dissolve;
        }

        /** 整窗提亮倍数（玄骸收起前那 60 ms 是 1.35，平时 1） */
        public float brighten() {
            return brighten;
        }

        /** 到场故障：下半窗染 {@code #4FE8FF} 的强度 0~0.35 */
        public float glitch() {
            return glitch;
        }

        /** 这一次画的是玄骸开框时右边那份重影（错开量已经在姿态矩阵里），常驻的错色重影、错位带这一次可以不画 */
        public boolean ghostPass() {
            return ghostPass;
        }

        /** 窗里是玄骸 */
        public boolean lich() {
            return lich;
        }

        /**
         * 故障段的两拍：字幕擦出前沿走到一段 {@code <x>} 的那一刻起 {@code 2 × 38} ms 为真（Ordis 肖像碎块再错开、整窗品 / 青色差）。
         * 只有 Ordis 的台词带故障段，别人恒为假
         */
        public boolean kick() {
            return kick;
        }
    }

    // ==================== 调试 ====================

    /** 把时间轴冻在开窗后第 {@code ms} 毫秒；现在没有窗的话，下一扇窗一开就冻在这一刻 */
    public static void debugFreeze(long ms) {
        Win w = win;
        if (w != null && frozenAt < 0L) {
            // 冻住期间不出嘟囔
            if (w.seg != null && w.seg.schedule != null) {
                LichVoice.stop(w.seg.schedule, VOICE_FADE_MS);
            }
        }
        frozenAt = Math.max(0L, ms);
        cachedTimeline = null;
    }

    /** 冻在某个阶段：{@code sub} 字幕开始、{@code name} 名牌出现、{@code close} 收起到一半；没有窗返回 -1 */
    public static long debugPhaseMs(String phase) {
        Win w = win;
        if (w == null) {
            return -1L;
        }
        Seg s = w.seg;
        return switch (phase) {
            case "sub" -> s != null ? s.textStartMs - w.openMs : -1L;
            case "name" -> w.nameInMs == Long.MAX_VALUE ? -1L : w.nameInMs + NAME_FADE_MS - w.openMs;
            case "close" -> {
                long close = w.closeMs != Long.MAX_VALUE ? w.closeMs : (s != null ? s.textEndMs + AFTER_TEXT_MS : -1L);
                int dur = w.closeMs != Long.MAX_VALUE ? w.closeDur : (w.lich ? CLOSE_LICH_FLASH_MS + CLOSE_LICH_DISSOLVE_MS : CLOSE_FRIEND_MS);
                yield close < 0L ? -1L : close + dur / 2 - w.openMs;
            }
            default -> -1L;
        };
    }

    /** 解冻：从冻住的那一刻接着往下走 */
    public static void debugUnfreeze() {
        if (frozenAt < 0L) {
            return;
        }
        Win w = win;
        long t = frozenAt;
        frozenAt = -1L;
        if (w == null) {
            return;
        }
        long clock = KuvaFxDirector.clockMs();
        long delta = clock - (w.openMs + t);
        shift(w, delta);
        Seg s = w.seg;
        if (s != null && s.started && !s.ended && s.src.speaker() == CommSpeaker.LICH) {
            // 后半句接着念：同一句排出来的表一样，已经过去的音节不会补放
            s.schedule = LichVoice.speak(seedOf(w), s.plain, s.textStartMs, talkEntity(s), true, audible(s));
        }
        cachedTimeline = null;
    }

    /** 冻住了没有 */
    public static boolean isFrozen() {
        return frozenAt >= 0L;
    }

    private static void shift(Win w, long delta) {
        if (delta == 0L) {
            return;
        }
        w.openMs += delta;
        if (w.nameInMs != Long.MAX_VALUE) {
            w.nameInMs += delta;
        }
        if (w.closeMs != Long.MAX_VALUE) {
            w.closeMs += delta;
        }
        Seg s = w.seg;
        if (s != null) {
            s.textStartMs += delta;
            s.textEndMs += delta;
        }
        lastTextEndMs += delta;
    }

    // ==================== 每帧推进 ====================

    private static long now(long clock) {
        Win w = win;
        return frozenAt >= 0L && w != null ? w.openMs + frozenAt : clock;
    }

    private static boolean closing(Win w, long now) {
        return w.closeMs != Long.MAX_VALUE && now >= w.closeMs;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (win == null && QUEUE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        if (!EffectsDisplayConfig.commHud()) {
            // 中途关掉：窗直接撤，排着的进聊天栏
            closeSilently();
            for (Entry e : QUEUE) {
                if (!e.copied) {
                    toChat(e.line);
                }
            }
            QUEUE.clear();
            return;
        }
        long clock = KuvaFxDirector.clockMs();
        if (frozenAt >= 0L && win != null) {
            advance(win, now(clock));
            return;
        }
        expire(clock);
        if (win != null) {
            advance(win, clock);
        }
        if (win == null) {
            tryOpen(clock);
        }
    }

    /**
     * 排太久的丢掉。等多久从「排到队首、前一扇窗开始收（或者本来就没窗）」那一刻算，不从收到算：
     * 同一刻连发的几句（诞生时 Lotus 一长段、紧跟着玄骸一句）后面那句要等前面整段说完，按收到算会白白过期
     */
    private static void expire(long clock) {
        Win w = win;
        boolean free = w == null || closing(w, now(clock));
        boolean first = true;
        Iterator<Entry> it = QUEUE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!first || !free) {
                // 前面还有人（插队的句子会把它挤回后面），重新等
                e.frontSinceMs = Long.MIN_VALUE;
            } else if (e.frontSinceMs == Long.MIN_VALUE) {
                e.frontSinceMs = clock;
            }
            first = false;
            if (!e.line.urgent() && e.frontSinceMs != Long.MIN_VALUE && clock - e.frontSinceMs > QUEUE_EXPIRE_MS) {
                it.remove();
                drop(e);
            }
        }
    }

    private static void tryOpen(long clock) {
        Entry head = QUEUE.peekFirst();
        if (head == null) {
            return;
        }
        long earliest;
        if (head.line.urgent()) {
            earliest = head.urgentAt;
        } else {
            int gap = QUEUE.size() >= 2 ? SWITCH_GAP_BUSY_MS : SWITCH_GAP_MS;
            earliest = lastTextEndMs + gap;
        }
        earliest = Math.max(earliest, lastOpenMs + SPAM_GAP_MS);
        if (clock < earliest) {
            return;
        }
        QUEUE.pollFirst();
        long openMs = Math.min(clock, Math.max(earliest, head.line.receivedMs()));
        open(head.line, openMs);
    }

    private static void open(CommLine line, long openMs) {
        Win w = new Win();
        w.who = line.speaker();
        w.lich = w.who == CommSpeaker.LICH;
        w.look = w.lich ? (line.look() != null ? line.look() : LichLook.NONE) : null;
        w.name = nameOf(line);
        w.arrival = line.arrival();
        w.openMs = openMs;
        w.openDur = w.lich ? OPEN_LICH_MS : OPEN_FRIEND_MS;
        win = w;
        log("开窗 %s，%s，队列还有 %d 句", w.who,
                lastTextEndMs < 0L ? "之前没有字幕" : "距上一句字幕消失 " + (openMs - lastTextEndMs) + " ms", QUEUE.size());
        lastOpenMs = openMs;
        beginLine(w, line, openMs + (w.lich ? TEXT_LICH_MS : TEXT_FRIEND_MS));
        w.nameInMs = w.seg != null ? w.seg.textStartMs + NAME_DELAY_MS : Long.MAX_VALUE;
        cachedTimeline = null;
    }

    /** 一句台词排上：超过 3 行的拆成几段，第一段从 {@code textStart} 开始 */
    private static void beginLine(Win w, CommLine line, long textStart) {
        String raw = CommText.stripColors(line.line().getString());
        List<String> pieces = splitForLines(raw);
        w.parts.clear();
        for (int i = 1; i < pieces.size(); i++) {
            w.parts.addLast(pieces.get(i));
        }
        w.partsOf = line;
        if (line.look() != null && w.lich) {
            w.look = line.look();
        }
        w.seg = makeSeg(w, line, pieces.get(0), textStart, pieces.size() == 1);
    }

    private static Seg makeSeg(Win w, CommLine line, String raw, long textStart, boolean lastPiece) {
        Seg s = new Seg();
        s.src = line;
        s.raw = raw;
        s.text = CommText.parse(raw);
        s.plain = CommText.plain(raw);
        s.han = CommText.hanCount(s.plain);
        s.latin = CommText.latinCount(s.plain);
        s.shout = isShout(s.plain);
        s.textStartMs = textStart;
        s.wipeMs = CommText.wipeDurationMs(s.plain);
        int hold = Mth.clamp(Math.round(HOLD_BASE_MS + HOLD_PER_UNIT_MS * (s.han + 0.4F * s.latin)), HOLD_MIN_MS, HOLD_MAX_MS);
        if (w.lich) {
            s.schedule = LichVoice.schedule(seedOf(w), s.plain, textStart);
            hold = Math.max(hold, s.schedule.lengthMs() + MUTTER_TAIL_MS);
        }
        if (lastPiece && line.holdMs() > 0) {
            hold = Math.max(line.holdMs(), s.wipeMs + MUTTER_TAIL_MS);
        }
        s.textEndMs = textStart + hold;
        return s;
    }

    private static void advance(Win w, long now) {
        if (!w.opened && now >= w.openMs) {
            w.opened = true;
            playOpenSound(w);
        }
        Seg s = w.seg;
        if (s != null && !s.started && now >= s.textStartMs) {
            startSeg(w, s);
        }
        if (s != null && s.started && !s.ended && now >= s.textEndMs) {
            endSeg(w, s, false);
            lastTextEndMs = s.textEndMs;
            afterSeg(w, s, now);
        }
        if (w.seg == null && !w.quick && w.closeMs != Long.MAX_VALUE && now < w.closeMs && frozenAt < 0L) {
            tryContinue(w, now);
        }
        if (now >= w.doneMs()) {
            if (win == w) {
                log("窗收完 +%d ms", now - w.openMs);
                win = null;
                cachedTimeline = null;
            }
        }
    }

    private static void startSeg(Win w, Seg s) {
        s.started = true;
        cachedTimeline = null;
        log("字幕开始 +%d ms：擦出 %d ms，停留 %d ms，汉字 %d、拉丁 %d", s.textStartMs - w.openMs, s.wipeMs,
                s.textEndMs - s.textStartMs, s.han, s.latin);
        if (!w.lich) {
            return;
        }
        Entity talker = findTalker(w);
        s.talkEntityId = talker != null ? talker.getId() : -1;
        s.schedule = LichVoice.speak(seedOf(w), s.plain, s.textStartMs, talker, true, audible(s));
        log("嘟囔 %d 个音节，%d ms，跟随实体 %d", s.schedule.count(), s.schedule.lengthMs(), s.talkEntityId);
        if (talker instanceof AbstractKuva kuva) {
            kuva.talkUntilMs = Math.max(s.schedule.endMs(), s.textStartMs + s.wipeMs);
            // 这一句以感叹号结尾时世界里那只播喊话动作，否则普通说话
            kuva.talkShout = s.shout;
        }
        if (EffectsDisplayConfig.commStatic() && frozenAt < 0L) {
            s.staticLoop = KuvaSoundPlayer.play(KuvaSoundPlayer.uiLoop(KuvaLichSounds.UI_COMM_STATIC.get(),
                    STATIC_VOLUME, STATIC_FADE_MS));
        }
    }

    /** 字幕消失：静电淡出；嘟囔还没念完（插队、被收起、服务端给的停留比嘟囔短）就 80 ms 淡出掐掉 */
    private static void endSeg(Win w, Seg s, boolean early) {
        s.ended = true;
        log("字幕消失 +%d ms%s", s.textEndMs - w.openMs, early ? "（提前）" : "");
        cachedTimeline = null;
        if (s.staticLoop != null) {
            s.staticLoop.fadeOut(STATIC_FADE_MS);
            s.staticLoop = null;
        }
        if (s.schedule != null && (early || s.schedule.endMs() > s.textEndMs)) {
            LichVoice.stop(s.schedule, VOICE_FADE_MS);
        }
        if (early && s.talkEntityId >= 0) {
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level != null ? mc.level.getEntity(s.talkEntityId) : null;
            if (e instanceof AbstractKuva kuva && kuva.talkUntilMs > s.textEndMs) {
                kuva.talkUntilMs = s.textEndMs;
            }
        }
    }

    /** 一段说完：同一句还有下一段就接着说；否则看队列头是不是同一个人；都不是就排收起 */
    private static void afterSeg(Win w, Seg s, long now) {
        if (!w.parts.isEmpty()) {
            String raw = w.parts.pollFirst();
            w.seg = makeSeg(w, w.partsOf, raw, s.textEndMs + SAME_SPEAKER_GAP_MS, w.parts.isEmpty());
            return;
        }
        w.seg = null;
        w.closeMs = s.textEndMs + AFTER_TEXT_MS;
        w.closeDur = w.lich ? CLOSE_LICH_FLASH_MS + CLOSE_LICH_DISSOLVE_MS : CLOSE_FRIEND_MS;
        tryContinue(w, now);
    }

    /** 窗还没开始收、队列头又是同一个人：不收窗，空 900 ms 接着说 */
    private static void tryContinue(Win w, long now) {
        Entry head = QUEUE.peekFirst();
        if (head == null || head.line.urgent() || head.line.speaker() != w.who) {
            return;
        }
        QUEUE.pollFirst();
        log("%s 接着说", w.who);
        w.closeMs = Long.MAX_VALUE;
        w.closeDur = 0;
        Component name = nameOf(head.line);
        if (head.line.name() != null) {
            w.name = name;
        }
        beginLine(w, head.line, Math.max(lastTextEndMs + SAME_SPEAKER_GAP_MS, now));
        cachedTimeline = null;
    }

    /** 快速收起：字幕立刻消失，不等 500 ms，直接收 130 ms */
    private static void quickClose(Win w, long now) {
        log("快速收起 +%d ms", now - w.openMs);
        Seg s = w.seg;
        if (s != null) {
            if (s.started && !s.ended) {
                s.textEndMs = Math.min(s.textEndMs, now);
                endSeg(w, s, true);
            }
            lastTextEndMs = Math.min(now, s.started ? s.textEndMs : now);
        }
        w.seg = null;
        w.parts.clear();
        w.quick = true;
        w.closeMs = now;
        w.closeDur = CLOSE_QUICK_MS;
        cachedTimeline = null;
    }

    /** 不演收起动画直接撤（关掉通讯窗选项、换世界） */
    private static void closeSilently() {
        Win w = win;
        if (w != null && w.seg != null && w.seg.started && !w.seg.ended) {
            endSeg(w, w.seg, true);
        }
        win = null;
        cachedTimeline = null;
    }

    private static void reset() {
        closeSilently();
        QUEUE.clear();
        lastTextEndMs = Long.MIN_VALUE / 4;
        lastOpenMs = Long.MIN_VALUE / 4;
    }

    private static void playOpenSound(Win w) {
        // 「通讯接通声」开关指南和游戏里的通讯窗一起管
        if (!GuideDisplayConfig.commSound()) {
            return;
        }
        var ev = switch (w.who) {
            case LOTUS -> KuvaLichSounds.UI_COMM_LOTUS;
            case ORDIS -> KuvaLichSounds.UI_COMM_ORDIS;
            case LICH -> KuvaLichSounds.UI_COMM_LICH;
        };
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev.get(), OPEN_SOUND_VOLUME, 1.0F));
    }

    private static void drop(Entry e) {
        log("丢掉一句 %s（排太久或队列满）", e.line.speaker());
        if (!e.copied && !e.line.ambient()) {
            toChat(e.line);
            e.copied = true;
        }
    }

    private static void dropLastNonUrgent() {
        Iterator<Entry> it = QUEUE.descendingIterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.line.urgent()) {
                it.remove();
                drop(e);
                return;
            }
        }
    }

    // ==================== 小工具 ====================

    /** 详细日志打开时记一行（只在开窗、换段、收起这些时刻，不在每帧） */
    private static void log(String fmt, Object... args) {
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("[通讯窗] " + String.format(fmt, args));
        }
    }

    private static Component nameOf(CommLine line) {
        return line.name() != null ? line.name() : Component.translatable(line.speaker().nameKey());
    }

    /** 台词以全角或半角感叹号结尾：世界里的玄骸播喊话动作 */
    private static boolean isShout(String plain) {
        String t = plain.trim();
        return t.endsWith("!") || t.endsWith("！");
    }

    private static int seedOf(Win w) {
        return w.look != null ? w.look.seed() : 0;
    }

    private static boolean audible(Seg s) {
        return EffectsDisplayConfig.lichMutter() && !s.src.noMutter() && frozenAt < 0L;
    }

    @Nullable
    private static Entity talkEntity(Seg s) {
        Minecraft mc = Minecraft.getInstance();
        return s.talkEntityId >= 0 && mc.level != null ? mc.level.getEntity(s.talkEntityId) : null;
    }

    @Nullable
    private static Entity findTalker(Win w) {
        Minecraft mc = Minecraft.getInstance();
        if (w.look == null || w.look.entityId() < 0 || mc.level == null) {
            return null;
        }
        return mc.level.getEntity(w.look.entityId());
    }

    /**
     * 关了通讯窗时世界里那只玄骸照样张嘴：收到就按这句排一张不出声的音节表，点头和发光读它，表念完就闭嘴
     */
    private static void talkWithoutHud(CommLine line) {
        Minecraft mc = Minecraft.getInstance();
        LichLook look = line.look();
        if (line.speaker() != CommSpeaker.LICH || look == null || look.entityId() < 0 || mc.level == null) {
            return;
        }
        if (!(mc.level.getEntity(look.entityId()) instanceof AbstractKuva kuva)) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        String plain = CommText.plain(line.line().getString());
        LichVoice.Schedule sch = LichVoice.speak(look.seed(), plain, now, kuva, false, false);
        kuva.talkUntilMs = Math.max(kuva.talkUntilMs, sch.endMs());
        kuva.talkShout = isShout(plain);
        log("通讯窗关着，实体 %d 照样张嘴 %d ms", kuva.getId(), sch.lengthMs());
    }

    /** 本地聊天栏补一行「[名牌] 台词」，整行用发言人色 */
    private static void toChat(CommLine line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) {
            return;
        }
        String name = nameOf(line).getString().toUpperCase(Locale.ROOT);
        String text = CommText.plain(line.line().getString());
        int rgb = line.speaker().argb() & 0xFFFFFF;
        mc.gui.getChat().addMessage(Component.translatable("kuvalich.comm.chat", name, text)
                .withStyle(st -> st.withColor(rgb)));
    }

    // ==================== 拆段 ====================

    private static final String STOPS = "。！？!?…";
    private static final String COMMAS = "，,、；;：:—";

    /** 折成超过 3 行的台词按句号、逗号拆成几段；实在没有标点就在第 3 行末尾硬拆 */
    private static List<String> splitForLines(String raw) {
        List<String> out = new ArrayList<>(2);
        Minecraft mc = Minecraft.getInstance();
        int wrap = Layout.wrapWidthFontPx(mc, CommText.hanCount(raw) > 0);
        String rest = raw;
        int guard = 0;
        while (lineCount(mc.font, rest, wrap) > MAX_LINES && guard++ < 8) {
            int cut = findCut(mc.font, rest, wrap, STOPS);
            if (cut <= 0) {
                cut = findCut(mc.font, rest, wrap, COMMAS);
            }
            if (cut <= 0) {
                cut = hardCut(mc.font, rest, wrap);
            }
            if (cut <= 0 || cut >= rest.length()) {
                break;
            }
            String head = rest.substring(0, cut);
            String tail = rest.substring(cut).stripLeading();
            // 拆在故障段中间：前一段补上收尾标记，后一段补上开头标记
            if (glitchOpenAt(rest, cut)) {
                head = head + "</x>";
                tail = "<x>" + tail;
            }
            out.add(head);
            rest = tail;
        }
        out.add(rest);
        return out;
    }

    private static int findCut(Font font, String s, int wrap, String marks) {
        for (int i = s.length() - 1; i >= 1; i--) {
            if (marks.indexOf(s.charAt(i - 1)) >= 0 && !insideTag(s, i)
                    && lineCount(font, s.substring(0, i), wrap) <= MAX_LINES) {
                // 连着的标点（「！？」「……」）一起留在前一段
                int j = i;
                while (j < s.length() && marks.indexOf(s.charAt(j)) >= 0) {
                    j++;
                }
                return lineCount(font, s.substring(0, j), wrap) <= MAX_LINES ? j : i;
            }
        }
        return -1;
    }

    private static int hardCut(Font font, String s, int wrap) {
        int lo = 1;
        int hi = s.length() - 1;
        int best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lineCount(font, s.substring(0, mid), wrap) <= MAX_LINES) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (best > 0 && Character.isLowSurrogate(s.charAt(best))) {
            best--;
        }
        while (best > 0 && insideTag(s, best)) {
            best--;
        }
        return best;
    }

    private static int lineCount(Font font, String raw, int wrap) {
        return CommText.wrap(font, CommText.parse(raw), wrap).size();
    }

    /** 位置 {@code i} 是不是落在 {@code <x>} / {@code </x>} 标记的中间 */
    private static boolean insideTag(String s, int i) {
        int lt = s.lastIndexOf('<', i - 1);
        if (lt < 0) {
            return false;
        }
        int gt = s.indexOf('>', lt);
        return gt >= i && (s.startsWith("<x>", lt) || s.startsWith("</x>", lt));
    }

    /** 拆点之前有没有没闭合的 {@code <x>} */
    private static boolean glitchOpenAt(String s, int cut) {
        int open = 0;
        int i = 0;
        while (i < cut) {
            if (s.startsWith("<x>", i)) {
                open = 1;
                i += 3;
            } else if (s.startsWith("</x>", i)) {
                open = 0;
                i += 4;
            } else {
                i++;
            }
        }
        return open > 0;
    }

    // ==================== 版面 ====================

    /** 本帧的版面，物理像素；每帧按窗口尺寸重算，免得改分辨率后错位 */
    static final class Layout {
        static int W, S, t, h, w, x0, y0, x1, y1, sx, sw, sy, p, nameTop, nameRight, nameLeft;
        static double g;

        static void compute(Minecraft mc, boolean left) {
            Window wnd = mc.getWindow();
            W = wnd.getWidth();
            S = wnd.getHeight();
            g = wnd.getGuiScale();
            h = Math.max(Math.round(0.19F * S), 120);
            w = Math.round(1.5F * h);
            if (left) {
                x0 = Math.round(0.02F * W);
                x1 = x0 + w;
                y1 = Math.round(0.61F * S);
            } else {
                x1 = Math.round(0.955F * W);
                x0 = x1 - w;
                y1 = Math.round(0.656F * S);
            }
            y0 = y1 - h;
            t = textScale(S, g);
            sx = x0 - Math.round(0.005F * W);
            sw = w + Math.round(0.01F * W);
            sy = y1 + 2 * t;
            p = Math.max(10 * t, Math.round(0.0194F * S));
            nameTop = y1 - Math.round(0.03F * S);
            nameRight = Math.round(0.962F * W);
            nameLeft = x0 + Math.round(0.003F * W);
        }

        static int textScale(int s, double guiScale) {
            return Math.max(Math.max(Math.round(0.015F * s / 8.0F), (int) Math.ceil(guiScale / 2.0D)), 1);
        }

        /** 字幕的字号：带汉字（日文、韩文同理）时至少 2 倍，1 倍下笔画糊成一团；纯拉丁字母照 {@link #textScale} */
        static int subtitleScale(int base, boolean wide) {
            return wide ? Math.max(base, SUBTITLE_MIN_WIDE_SCALE) : base;
        }

        /**
         * 字幕区宽（物理像素）：一般和窗一样宽；小窗口里字号被抬到 2 倍后一行放不下 {@link #SUBTITLE_MIN_WRAP} 个字体像素时，
         * 往屏幕中间加宽（最多到屏宽的 {@link #SUBTITLE_MAX_W}），再放不下就多折几行、多拆几段，不把字缩回去
         */
        static int subtitleWidth(int screenW, int windowSubW, int scale) {
            int need = SUBTITLE_MIN_WRAP * scale;
            return Math.max(windowSubW, Math.min(need, Math.round(SUBTITLE_MAX_W * screenW)));
        }

        /** 字幕区左边：窗在右边时右沿对齐窗、往左加宽；窗在左边时左沿对齐 */
        static int subtitleX(boolean left, int subW) {
            return left ? sx : sx + sw - subW;
        }

        /** 字幕行距（物理像素） */
        static int subtitlePitch(int scale) {
            return Math.max(10 * scale, Math.round(0.0194F * S));
        }

        /** 字幕折行宽度（字体像素）；{@code wide} = 这一句带汉字 */
        static int wrapWidthFontPx(Minecraft mc, boolean wide) {
            Window wnd = mc.getWindow();
            int s = wnd.getHeight();
            int hh = Math.max(Math.round(0.19F * s), 120);
            int ww = Math.round(1.5F * hh) + Math.round(0.01F * wnd.getWidth());
            int ts = subtitleScale(textScale(s, wnd.getGuiScale()), wide);
            return subtitleWidth(wnd.getWidth(), ww, ts) / ts;
        }
    }

    /** 带汉字的字幕最小字号 */
    static final int SUBTITLE_MIN_WIDE_SCALE = 2;
    /** 字幕一行至少容得下这么宽（字体像素，约 16 个汉字） */
    static final int SUBTITLE_MIN_WRAP = 144;
    /** 字幕区最宽占屏宽的比例（从窗那一侧往中间伸，不越过准星） */
    static final float SUBTITLE_MAX_W = 0.42F;

    // ==================== 画 ====================

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            // 在其它 HUD 之上、聊天栏之下
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), OVERLAY_ID.getPath(), CommHud::renderOverlay);
        }
    }

    private static void renderOverlay(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeathScreen) {
            // 死亡界面会把 HUD 整片染红，改在界面画完之后画
            return;
        }
        render(g, partialTick);
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof DeathScreen && !Minecraft.getInstance().options.hideGui) {
            render(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        frozenAt = -1L;
    }

    private static void render(GuiGraphics g, float partialTick) {
        Win w = win;
        if (w == null || !EffectsDisplayConfig.commHud()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = now(KuvaFxDirector.clockMs());
        long t = now - w.openMs;
        if (t < 0L) {
            return;
        }
        Layout.compute(mc, w.who.isLeft());
        float inv = (float) (1.0D / Layout.g);

        g.pose().pushPose();
        g.pose().scale(inv, inv, 1.0F);

        drawWindow(g, w, now, t, partialTick);
        drawNameplate(g, mc.font, w, now);
        drawSubtitle(g, mc.font, w, now);

        g.pose().popPose();
        if (frozenAt >= 0L) {
            CommDevClientCommands.drawDebug(g, mc.font, debugView(w, now));
        }
    }

    private static void drawWindow(GuiGraphics g, Win w, long now, long t, float partialTick) {
        float alpha = 1.0F;
        float scaleX = 1.0F;
        float scaleY = 1.0F;
        float ghostDx = 0.0F;
        float ghostAlpha = 0.0F;
        float shift = 0.0F;
        FX.dissolve = 0.0F;
        FX.brighten = 1.0F;
        FX.glitch = 0.0F;
        FX.ghostPass = false;
        FX.lich = w.lich;
        FX.kick = kickNow(w, now);

        // 开框
        if (t < w.openDur) {
            if (w.lich) {
                int d0 = Math.round(0.06F * Layout.w);
                if (t < 45L) {
                    alpha = 0.4F;
                    ghostDx = d0;
                    ghostAlpha = 0.4F;
                } else if (t < 90L) {
                    alpha = 0.75F;
                    ghostDx = d0 / 2.0F;
                    ghostAlpha = 0.25F;
                }
            } else {
                float k = FxCurve.easeOutCubic(t / (float) w.openDur);
                scaleY = Mth.lerp(k, 0.25F, 1.0F);
                scaleX = Mth.lerp(k, 1.3F, 1.0F);
                alpha = Mth.lerp(k, 0.6F, 1.0F);
            }
        }
        // 到场：开框之后 200 ms 的青色故障，每 40 ms 换一次横向错位
        if (w.arrival && t >= w.openDur && t < w.openDur + GLITCH_MS) {
            float k = (t - w.openDur) / (float) GLITCH_MS;
            FX.glitch = GLITCH_TINT * (1.0F - k);
            long step = (t - w.openDur) / GLITCH_STEP_MS;
            int hash = (int) (step * 0x9E3779B1L ^ w.openMs) * 0x85EBCA6B;
            int mag = 1 + ((hash >>> 8) & 1);
            int unit = Math.max(1, Math.round(Layout.S / 1080.0F));
            shift = ((hash >>> 16) & 1) == 0 ? mag * unit : -mag * unit;
        }
        // 收起
        if (closing(w, now)) {
            long c = now - w.closeMs;
            if (c >= w.closeDur) {
                return;
            }
            if (w.lich) {
                if (w.quick) {
                    FX.dissolve = c / (float) w.closeDur;
                } else if (c < CLOSE_LICH_FLASH_MS) {
                    FX.brighten = CLOSE_FLASH;
                } else {
                    FX.dissolve = (c - CLOSE_LICH_FLASH_MS) / (float) CLOSE_LICH_DISSOLVE_MS;
                }
            } else {
                float k = c / (float) w.closeDur;
                scaleY = Mth.lerp(k, 1.0F, 0.04F);
                alpha = 1.0F - k;
            }
        }
        if (alpha < 0.004F) {
            return;
        }

        boolean talking = w.seg != null && now >= w.seg.textStartMs && now < w.seg.textEndMs;
        float cx = Layout.x0 + Layout.w * 0.5F;
        float cy = Layout.y0 + Layout.h * 0.5F;
        g.pose().pushPose();
        g.pose().translate(cx + shift, cy, 0.0F);
        g.pose().scale(scaleX, scaleY, 1.0F);
        g.pose().translate(-cx, -cy, 0.0F);
        portrait(g, w, alpha, talking, partialTick);
        if (ghostAlpha > 0.0F && ghostDx != 0.0F) {
            g.pose().translate(ghostDx, 0.0F, 0.0F);
            FX.ghostPass = true;
            portrait(g, w, ghostAlpha, talking, partialTick);
            FX.ghostPass = false;
        }
        g.pose().popPose();
    }

    private static void portrait(GuiGraphics g, Win w, float alpha, boolean talking, float partialTick) {
        drawingFx = true;
        boolean drawn;
        try {
            drawn = CommPortraits.draw(g, w.who, w.look, Layout.x0, Layout.y0, Layout.w, Layout.h, alpha, talking,
                    partialTick);
        } finally {
            drawingFx = false;
        }
        if (!drawn) {
            drawPlate(g, w.who, alpha * (1.0F - FX.dissolve));
        }
    }

    /** 肖像画不出来时的底：发言人自己的暗色竖向渐变，四边分几层淡出代替羽化 */
    private static void drawPlate(GuiGraphics g, CommSpeaker who, float alpha) {
        if (alpha < 0.004F) {
            return;
        }
        int top;
        int mid;
        int bottom;
        switch (who) {
            case LOTUS -> {
                top = 0x05141A;
                mid = 0x081F25;
                bottom = 0x0B2A30;
            }
            case ORDIS -> {
                top = 0x06081A;
                mid = 0x121635;
                bottom = 0x1E2450;
            }
            default -> {
                top = 0x000000;
                mid = 0x2A0510;
                bottom = 0x5E0A18;
            }
        }
        // 四层叠到中间是 0.9：每层 1 − 0.1^(1/4)
        float layer = 0.438F * alpha;
        int a = Math.max(1, Math.round(layer * 255.0F)) << 24;
        int fx = Math.round(0.09F * Layout.w / 4.0F);
        int fy = Math.round(0.14F * Layout.h / 4.0F);
        int split = Layout.y0 + Math.round(Layout.h * 0.6F);
        for (int i = 0; i < 4; i++) {
            int l = Layout.x0 + i * fx;
            int r = Layout.x1 - i * fx;
            int tp = Layout.y0 + i * fy;
            int bt = Layout.y1 - i * fy;
            g.fillGradient(l, tp, r, split, a | top, a | mid);
            g.fillGradient(l, split, r, bt, a | mid, a | bottom);
        }
        if (FX.glitch > 0.0F) {
            int ga = Math.round(FX.glitch * alpha * 255.0F) << 24;
            g.fill(Layout.x0 + 2 * fx, Layout.y0 + Layout.h / 2, Layout.x1 - 2 * fx, Layout.y1 - 2 * fy, ga | GLITCH_RGB);
        }
    }

    private static void drawNameplate(GuiGraphics g, Font font, Win w, long now) {
        if (w.nameInMs == Long.MAX_VALUE || now < w.nameInMs) {
            return;
        }
        float a = Math.min(1.0F, (now - w.nameInMs) / (float) NAME_FADE_MS);
        long closeEnd = w.closeEndMs();
        if (closeEnd != Long.MAX_VALUE && now >= closeEnd + NAME_LINGER_MS) {
            a = Math.min(a, 1.0F - (now - closeEnd - NAME_LINGER_MS) / (float) NAME_FADE_MS);
        }
        if (a < CommText.MIN_GLYPH_ALPHA) {
            return;
        }
        int t = Layout.t;
        int widthPx = CommText.nameplateWidth(font, w.name) * t;
        int x = w.who.isLeft() ? Layout.nameLeft : Layout.nameRight - widthPx;
        g.pose().pushPose();
        g.pose().translate(x, Layout.nameTop, 0.0F);
        g.pose().scale(t, t, 1.0F);
        CommText.drawNameplate(g, font, w.name, w.who, 0, 0, a);
        g.pose().popPose();
    }

    private static void drawSubtitle(GuiGraphics g, Font font, Win w, long now) {
        Seg s = w.seg;
        if (s == null || now < s.textStartMs || now >= s.textEndMs) {
            return;
        }
        int t = Layout.subtitleScale(Layout.t, s.han > 0);
        int subW = Layout.subtitleWidth(Layout.W, Layout.sw, t);
        ensureLines(s, font, subW / t);
        float progress = CommText.progressFor(now - s.textStartMs, s.wipeMs);
        g.pose().pushPose();
        g.pose().translate(Layout.subtitleX(w.who.isLeft(), subW), Layout.sy, 0.0F);
        g.pose().scale(t, t, 1.0F);
        CommText.drawWipe(g, font, s.lines, 0.0F, 0.0F, Layout.subtitlePitch(t) / (float) t,
                0xFF000000 | CommText.SUBTITLE_RGB, progress, CommText.FEATHER, true);
        g.pose().popPose();
    }

    /** 按当前字幕区宽度折行；宽度变了重折，顺带算好每个故障段被擦到的时刻 */
    private static void ensureLines(Seg s, Font font, int wrap) {
        if (s.lines != null && s.wrapWidth == wrap) {
            return;
        }
        s.lines = CommText.wrap(font, s.text, wrap);
        s.wrapWidth = wrap;
        s.kickAtMs = kickTimes(font, s.lines, s.wipeMs);
    }

    /**
     * 擦出前沿 {@code f = (总宽 + F) × τ / D} 走到每个故障段第一个字的时刻 τ（毫秒）。
     * 几行接成一条算 x，和 {@link CommText} 擦出时的算法一致；只在折行时算一次
     */
    private static float[] kickTimes(Font font, List<FormattedCharSequence> lines, int wipeMs) {
        float[] starts = new float[4];
        int[] n = {0};
        float[] x = {0.0F};
        boolean[] inGlitch = {false};
        for (FormattedCharSequence line : lines) {
            line.accept((idx, style, cp) -> {
                boolean gli = CommText.isGlitch(style);
                if (gli && !inGlitch[0] && n[0] < starts.length) {
                    starts[n[0]++] = x[0];
                }
                inGlitch[0] = gli;
                x[0] += font.width(FormattedCharSequence.codepoint(cp, style));
                return true;
            });
        }
        float span = x[0] + CommText.FEATHER;
        float[] out = new float[n[0]];
        for (int i = 0; i < out.length; i++) {
            out[i] = starts[i] * wipeMs / span;
        }
        return out;
    }

    /** 这一帧是不是在某个故障段的两拍里 */
    private static boolean kickNow(Win w, long now) {
        Seg s = w.seg;
        if (s == null || w.who != CommSpeaker.ORDIS || now < s.textStartMs || now >= s.textEndMs) {
            return false;
        }
        int t = Layout.subtitleScale(Layout.t, s.han > 0);
        ensureLines(s, Minecraft.getInstance().font, Layout.subtitleWidth(Layout.W, Layout.sw, t) / t);
        float e = now - s.textStartMs;
        for (float k : s.kickAtMs) {
            if (e >= k && e < k + 2 * KICK_BEAT_MS) {
                return true;
            }
        }
        return false;
    }

    // ==================== 调试画线用的快照 ====================

    /** 冻住时调试线要画的东西（只在开发环境的调试命令打开时才建） */
    record DebugView(long t, long openDur, long textStart, int wipeMs, long nameIn, long textEnd, long close,
                     long closeEnd, long done, @Nullable LichVoice.Schedule schedule, int han, int latin,
                     String text, boolean staticActive, boolean lich, int syllables) {
    }

    private static DebugView debugView(Win w, long now) {
        Seg s = w.seg;
        long o = w.openMs;
        long textStart = s != null ? s.textStartMs - o : -1L;
        long textEnd = s != null ? s.textEndMs - o : lastTextEndMs - o;
        long close = w.closeMs != Long.MAX_VALUE ? w.closeMs - o : textEnd + AFTER_TEXT_MS;
        int closeDur = w.closeMs != Long.MAX_VALUE ? w.closeDur
                : (w.lich ? CLOSE_LICH_FLASH_MS + CLOSE_LICH_DISSOLVE_MS : CLOSE_FRIEND_MS);
        LichVoice.Schedule sch = s != null ? s.schedule : null;
        boolean staticActive = s != null && s.staticLoop != null
                && Minecraft.getInstance().getSoundManager().isActive(s.staticLoop);
        return new DebugView(now - o, w.openDur, textStart, s != null ? s.wipeMs : 0,
                w.nameInMs == Long.MAX_VALUE ? -1L : w.nameInMs - o, textEnd, close, close + closeDur,
                close + closeDur + NAME_LINGER_MS + NAME_FADE_MS, sch, s != null ? s.han : 0, s != null ? s.latin : 0,
                s != null ? s.plain : "", staticActive, w.lich, sch != null ? sch.count() : 0);
    }
}
