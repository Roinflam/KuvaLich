package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.HoloShapes;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.network.packet.NoticePacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 玩家提示条（客户端）：{@link NoticePacket} 带来的提示画成一条条短的全息读数，叠在物品栏和原版动作栏上面，
 * 取代以前的聊天框 / 动作栏文字。版面算术在 {@link NoticeLayout}
 *
 * <h2>样子</h2>
 * <p>墨烟底的切角小框（左上、右下切角），外圈一层很淡的软阴影，亮天空上也读得清；框线是这一类提示的颜色，
 * 平时很淡，两个切角连着一小段边是亮的；左边一颗同色小菱，左下框线挂两道科技青小刻度，左侧再垫一层往右淡掉的同色底。
 * 正文骨白，句子里的值（名字、数字、物品）用这一类的颜色。字用读数字体 {@link HoloShapes#HUD}：
 * 「强制使用 Unicode 字体」开着也是原版像素字，宽度、行高两种设置下一样。里程碑（原来进聊天框的那一类）
 * 框线亮一些、小菱是实心带外圈，进场时一道光从左扫到右。</p>
 * <table border="1">
 *   <caption>颜色（服务端给的类别色 → 框线、值）</caption>
 *   <tr><th>类别</th><th>框线 / 小菱</th><th>值</th></tr>
 *   <tr><td>玄骸 LICH</td><td>{@link FxPalette#KUVA}</td><td>{@link KuvaPalette#LICH}</td></tr>
 *   <tr><td>好事 SUCCESS</td><td>{@link KuvaPalette#SUCCESS}</td><td>同左</td></tr>
 *   <tr><td>坏事 DANGER</td><td>{@link KuvaPalette#DANGER}</td><td>同左</td></tr>
 *   <tr><td>半好半坏 WARN</td><td>{@link FxPalette#EMBER}</td><td>{@link KuvaPalette#WARN}</td></tr>
 *   <tr><td>进度 PROGRESS</td><td>{@link FxPalette#TECH}</td><td>{@link KuvaPalette#PROGRESS}</td></tr>
 *   <tr><td>留意 ACCENT</td><td>{@link FxPalette#ACCENT}</td><td>同左</td></tr>
 *   <tr><td>次要 MUTED</td><td>{@link FxPalette#ASH}</td><td>{@link FxPalette#TEXT_MID}</td></tr>
 * </table>
 *
 * <h2>时间轴（毫秒，0 = 这一条开始）</h2>
 * <ul>
 *   <li>0–180：框从中间往两边展开，底 0–140 淡入；90–250 小菱弹出来；</li>
 *   <li>150 起逐字解码：第 i 个字在 150 + i × 步长 锁定（步长 = 300 / 字数，夹在 6~24），锁定前 60 ms 先出现——
 *       拉丁字母、数字先滚乱码，汉字和标点从这一类的颜色淡入；锁定后 160 ms 从暖白落到自己的颜色；</li>
 *   <li>解码完停 H：普通 clamp(1800 + 110 × 字量, 2600, 6000)，里程碑 clamp(3400 + 130 × 字量, 5000, 9000)，
 *       字量 = 汉字数 + 0.35 × 其余字数；里程碑 240–800 一道光扫过；</li>
 *   <li>退场 260：字 140 ms 淡掉，框 80 起收回中间。</li>
 * </ul>
 *
 * <h2>排队</h2>
 * <p>最多同时三条，新的在最下面（贴着动作栏），旧的往上让；整叠高不超过准星以下的空地，放不下的先排着。
 * 相邻两条进场至少隔 110 ms，同时到的几条依次解码，不一起闪。排着的时候把最早那条的停留缩短到「解码完再看 1.2 s」
 * （里程碑 2.5 s）。一模一样的一句还在屏幕上时不再加一条，只把它的停留续上、框闪一下。排队最多 8 条，满了先丢最早的普通提示；
 * 普通提示排了 30 s、里程碑排了 120 s 还没轮到就丢掉。</p>
 *
 * <h2>什么时候不画</h2>
 * <p>开着任何界面（背包、聊天栏、死亡界面……）、按 F1 藏界面、本地玩家在处决中时不画，并且整套时间轴停住：
 * 界面盖着时看不清，停住就不会错过；回来后 150 ms 淡回原处、接着走。处决运镜时到的奖励提示等运镜结束再出。</p>
 *
 * <p>屏幕两侧的通讯窗开着、它的窗或字幕和提示条上下重叠时，整叠往另一侧平移让开（110 ms 缓过去），窗收了再回到正中。
 * 整个覆盖层抬到 z = 200，压在原版聊天行（z = 50、开着深度测试）上面。</p>
 *
 * <p>关掉 {@code hud_notices} 时不画，收到的提示按原来的通道交回原版：里程碑进聊天栏，其余进动作栏。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class NoticeHud {

    /** 覆盖层 id */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "notice");
    private static final ResourceLocation HUD_FONT = new ResourceLocation(Reference.MOD_ID, "hud");
    private static final Style HUD = Style.EMPTY.withFont(HUD_FONT);
    /** 原版聊天行画在 z = 50、开着深度测试，要比它高 */
    private static final float ABOVE_CHAT_Z = 200.0F;

    // ==================== 时间（毫秒，可见时钟） ====================

    private static final int FRAME_MS = 180;
    private static final int PLATE_IN_MS = 140;
    private static final int MARK_AT = 90;
    private static final int MARK_MS = 160;
    private static final int DECODE_AT = 150;
    private static final int DECODE_BUDGET = 300;
    private static final int STEP_MIN = 6;
    private static final int STEP_MAX = 24;
    private static final int LEAD_MS = 60;
    private static final int SETTLE_MS = 160;
    private static final int EXIT_MS = 260;
    private static final int EXIT_TEXT_MS = 140;
    private static final int EXIT_FRAME_AT = 80;
    private static final int GLINT_AT = 240;
    private static final int GLINT_MS = 560;
    private static final int PULSE_MS = 340;
    private static final int STAGGER_MS = 110;
    private static final int UNHIDE_MS = 150;
    private static final float FOLLOW_MS = 70.0F;
    private static final float SHIFT_MS = 110.0F;
    private static final int SCRAMBLE_STEP_MS = 40;

    private static final int HOLD_BASE = 1800;
    private static final int HOLD_PER = 110;
    private static final int HOLD_MIN = 2600;
    private static final int HOLD_MAX = 6000;
    private static final int MILE_BASE = 3400;
    private static final int MILE_PER = 130;
    private static final int MILE_MIN = 5000;
    private static final int MILE_MAX = 9000;
    /** 后面有排队的时候，最早那条解码完至少再看这么久 */
    private static final int MIN_SHOW = 1200;
    private static final int MIN_SHOW_MILESTONE = 2500;

    private static final int MAX_VISIBLE = 3;
    private static final int QUEUE_MAX = 8;
    /** 排着还没轮到、按演出钟过了这么久就丢掉 */
    private static final long STALE_MS = 30_000L;
    private static final long STALE_MILESTONE_MS = 120_000L;

    // ==================== 颜色与透明度 ====================

    private static final int BODY = FxPalette.BONE;
    /** 字刚锁定那一下的暖白 */
    private static final int FLASH = FxPalette.KUVA_PALE;
    private static final float PLATE_A = 0.74F;
    private static final float SHADOW_A = 0.35F;
    private static final float TINT_A = 0.16F;
    private static final int TINT_W = 36;
    private static final float DIM_A = 0.38F;
    private static final float DIM_A_MILESTONE = 0.55F;
    private static final float BRIGHT_A = 0.95F;
    private static final float TICK_A = 0.75F;
    /** 亮角顺着两条边各伸出多长 */
    private static final int BRIGHT_H = 6;
    private static final int BRIGHT_V = 3;

    /** 拉丁字母解码时滚的乱码字 */
    private static final String SCRAMBLE = "ABCDEFGHJKLMNPQRSTVWXZ0123456789#%&?";

    // ==================== 状态 ====================

    /** 一条提示：拆好的字、折好的行、时间轴 */
    private static final class Entry {
        String plain;
        int type;
        boolean milestone;
        int line;
        int n;
        FormattedCharSequence[] seq;
        int[] cp;
        int[] adv;
        byte[] cls;
        int[] rgb;
        int hold;
        long bornMs;
        /** 在哪个世界收到的：换了世界只丢旧世界的，进服时紧跟着到的提示不能跟着清掉 */
        WeakReference<Level> level;

        // 版面（按什么参数折的）
        int layoutW = -1;
        int layoutLines = -1;
        int lines;
        int textW;
        int boxW;
        int boxH;
        int[] gx;
        int[] gl;
        int ellX = -1;
        int ellLine;

        // 时间轴（可见时钟）
        long startMs;
        int step;
        int decodeEnd;
        long holdUntil;
        long exitAt = -1L;
        long pulseAt = Long.MIN_VALUE / 4;
        float y = Float.NaN;
        int targetY;
    }

    private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>(QUEUE_MAX + 1);
    private static final List<Entry> SHOWN = new ArrayList<>(MAX_VISIBLE + 1);

    /** 只在画得出来时才走的钟 */
    private static long vis;
    private static long lastDirector = Long.MIN_VALUE;
    private static long lastStart = Long.MIN_VALUE / 4;
    private static boolean hidden;
    private static long unhideAt = Long.MIN_VALUE / 4;
    private static float shift;
    private static WeakReference<Level> lastLevelRef;

    // 调试
    private static boolean debugHold;
    private static boolean debugFrozen;

    // 乱码字、省略号（第一次用到时按字体量好）
    private static FormattedCharSequence[] scrambleSeq;
    private static int[] scrambleAdv;
    private static FormattedCharSequence ellipsisSeq;
    private static int ellipsisAdv;

    private static final float[] POLY = new float[12];
    private static final float[] PATH = new float[8];
    private static final float[] RES = new float[4];

    private NoticeHud() {
    }

    // ==================== 收包 ====================

    /** 一条提示到了：开着提示条就排进去，关着就照原来的通道交给原版 */
    public static void accept(NoticePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (!EffectsDisplayConfig.hudNotices()) {
            Component line = PlayerFeedback.translate(p.rgb, p.key, (Object[]) p.args);
            mc.getChatListener().handleSystemMessage(line, !p.isChat());
            return;
        }
        push(p.rgb, p.isChat(), p.key, p.args);
    }

    /**
     * 排一条（收包和调试命令共用）
     *
     * @param type      类别色（{@link KuvaPalette} 里的值）
     * @param milestone 原来进聊天框的那一类
     */
    static void push(int type, boolean milestone, String key, Object[] args) {
        Minecraft mc = Minecraft.getInstance();
        int valueRgb = valueColor(type);
        Object[] colored = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            colored[i] = PlayerFeedback.value(args[i], valueRgb);
        }
        Component line = Component.translatable(key, colored);
        Entry e = build(mc.font, line, type, milestone);
        if (e == null) {
            return;
        }
        for (Entry s : SHOWN) {
            if (s.exitAt < 0L && s.type == type && s.plain.equals(e.plain)) {
                // 同一句还在屏幕上：续上停留、框闪一下
                s.holdUntil = Math.max(s.holdUntil, vis + s.hold);
                s.pulseAt = vis;
                return;
            }
        }
        for (Entry q : QUEUE) {
            if (q.type == type && q.plain.equals(e.plain)) {
                return;
            }
        }
        if (QUEUE.size() >= QUEUE_MAX) {
            Entry drop = null;
            for (Entry q : QUEUE) {
                if (!q.milestone) {
                    drop = q;
                    break;
                }
            }
            QUEUE.remove(drop != null ? drop : QUEUE.peekFirst());
        }
        QUEUE.addLast(e);
    }

    /** 把一句拆成一个个字：码点、读数字体下的步进、颜色、断行类别 */
    private static Entry build(Font font, Component line, int type, boolean milestone) {
        ensureGlyphs(font);
        List<int[]> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        line.getVisualOrderText().accept((index, style, cp) -> {
            cps.add(new int[]{cp == '\n' || cp == '\t' ? ' ' : cp});
            styles.add(style);
            return true;
        });
        int n = cps.size();
        if (n == 0) {
            return null;
        }
        Entry e = new Entry();
        e.plain = line.getString();
        e.type = type;
        e.milestone = milestone;
        e.line = lineColor(type);
        e.n = n;
        e.seq = new FormattedCharSequence[n];
        e.cp = new int[n];
        e.adv = new int[n];
        e.cls = new byte[n];
        e.rgb = new int[n];
        e.gx = new int[n];
        e.gl = new int[n];
        float units = 0.0F;
        for (int i = 0; i < n; i++) {
            int cp = cps.get(i)[0];
            Style st = styles.get(i);
            TextColor c = st.getColor();
            Style plainStyle = st.withColor((TextColor) null).withFont(HUD_FONT);
            e.cp[i] = cp;
            e.seq[i] = FormattedCharSequence.codepoint(cp, plainStyle);
            e.adv[i] = font.width(e.seq[i]);
            e.cls[i] = NoticeLayout.classify(cp);
            e.rgb[i] = c != null ? c.getValue() & 0xFFFFFF : BODY;
            if (cp != ' ') {
                units += NoticeLayout.isWide(cp) ? 1.0F : 0.35F;
            }
        }
        e.hold = milestone
                ? Mth.clamp(Math.round(MILE_BASE + MILE_PER * units), MILE_MIN, MILE_MAX)
                : Mth.clamp(Math.round(HOLD_BASE + HOLD_PER * units), HOLD_MIN, HOLD_MAX);
        e.bornMs = KuvaFxDirector.clockMs();
        e.level = new WeakReference<>(Minecraft.getInstance().level);
        return e;
    }

    private static void ensureGlyphs(Font font) {
        if (scrambleSeq != null) {
            return;
        }
        int[] cps = SCRAMBLE.codePoints().toArray();
        scrambleSeq = new FormattedCharSequence[cps.length];
        scrambleAdv = new int[cps.length];
        for (int i = 0; i < cps.length; i++) {
            scrambleSeq[i] = FormattedCharSequence.codepoint(cps[i], HUD);
            scrambleAdv[i] = font.width(scrambleSeq[i]);
        }
        ellipsisSeq = FormattedCharSequence.forward("...", HUD);
        ellipsisAdv = font.width(ellipsisSeq);
    }

    // ==================== 类别色 ====================

    /** 框线和小菱的颜色 */
    static int lineColor(int type) {
        if (type == KuvaPalette.LICH) {
            return FxPalette.KUVA;
        }
        if (type == KuvaPalette.WARN) {
            return FxPalette.EMBER;
        }
        if (type == KuvaPalette.PROGRESS) {
            return FxPalette.TECH;
        }
        if (type == KuvaPalette.ACCENT) {
            return FxPalette.ACCENT;
        }
        if (type == KuvaPalette.MUTED) {
            return FxPalette.ASH;
        }
        if (type == KuvaPalette.INFO || type == KuvaPalette.VALUE) {
            return FxPalette.TEXT_MID;
        }
        // SUCCESS、DANGER 和别的颜色：原样
        return type & 0xFFFFFF;
    }

    /** 句子里的值用的颜色 */
    static int valueColor(int type) {
        if (type == KuvaPalette.MUTED || type == KuvaPalette.INFO) {
            return FxPalette.TEXT_MID;
        }
        return type & 0xFFFFFF;
    }

    // ==================== 版面 ====================

    /** 按最宽、最多几行折一遍；参数没变就不重折 */
    private static void layout(Entry e, int maxBoxW, int maxLines) {
        if (e.layoutW == maxBoxW && e.layoutLines == maxLines) {
            return;
        }
        e.layoutW = maxBoxW;
        e.layoutLines = maxLines;
        int maxText = maxBoxW - NoticeLayout.PAD_L - NoticeLayout.PAD_R;
        int full = NoticeLayout.wrap(e.adv, e.cls, e.n, maxText, null, null);
        int wrapW = full <= maxLines ? NoticeLayout.balancedWidth(e.adv, e.cls, e.n, maxText) : maxText;
        int[] starts = new int[e.n + 1];
        int[] ends = new int[e.n + 1];
        int lines = NoticeLayout.wrap(e.adv, e.cls, e.n, wrapW, starts, ends);
        e.ellX = -1;
        if (lines > maxLines) {
            // 放不下：留前几行，最后一行截到放得下省略号
            lines = maxLines;
            int s = starts[lines - 1];
            int end = ends[lines - 1];
            while (end > s && sum(e.adv, s, end) + ellipsisAdv - 1 > maxText) {
                end--;
            }
            ends[lines - 1] = end;
            e.ellX = sum(e.adv, s, end);
            e.ellLine = lines - 1;
        }
        for (int i = 0; i < e.n; i++) {
            e.gl[i] = -1;
        }
        int textW = 0;
        for (int k = 0; k < lines; k++) {
            int x = 0;
            for (int i = starts[k]; i < ends[k]; i++) {
                e.gx[i] = x;
                e.gl[i] = k;
                x += e.adv[i];
            }
            textW = Math.max(textW, NoticeLayout.lineWidth(e.adv, e.cls, starts[k], ends[k]));
        }
        if (e.ellX >= 0) {
            textW = Math.max(textW, e.ellX + ellipsisAdv - 1);
        }
        e.lines = lines;
        e.textW = textW;
        e.boxW = NoticeLayout.boxWidth(textW);
        e.boxH = NoticeLayout.boxHeight(lines);
    }

    private static int sum(int[] adv, int s, int e) {
        int w = 0;
        for (int i = s; i < e; i++) {
            w += adv[i];
        }
        return w;
    }

    private static void start(Entry e) {
        e.startMs = vis;
        int drawn = 0;
        for (int i = 0; i < e.n; i++) {
            if (e.gl[i] >= 0) {
                drawn++;
            }
        }
        e.step = Mth.clamp(DECODE_BUDGET / Math.max(1, drawn), STEP_MIN, STEP_MAX);
        e.decodeEnd = DECODE_AT + Math.max(0, drawn - 1) * e.step + SETTLE_MS;
        e.holdUntil = vis + e.decodeEnd + e.hold;
        e.exitAt = -1L;
        e.y = Float.NaN;
    }

    // ==================== 注册与清场 ====================

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll(OVERLAY_ID.getPath(), NoticeHud::render);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** 换了世界：丢掉在别的世界收到的（正在显示的、排着的都算） */
    private static void dropOtherWorlds(Level level) {
        SHOWN.removeIf(e -> e.level.get() != level);
        QUEUE.removeIf(e -> e.level.get() != level);
        if (SHOWN.isEmpty()) {
            hidden = false;
            shift = 0.0F;
        }
    }

    /** 收掉全部（退出、调试命令） */
    static void clear() {
        QUEUE.clear();
        SHOWN.clear();
        hidden = false;
        shift = 0.0F;
    }

    // ==================== 每帧 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (lastLevelRef == null || lastLevelRef.get() != level) {
            lastLevelRef = new WeakReference<>(level);
            dropOtherWorlds(level);
        }
        long director = KuvaFxDirector.clockMs();
        long dt = lastDirector == Long.MIN_VALUE ? 0L : Math.max(0L, Math.min(director - lastDirector, 50L));
        lastDirector = director;
        if (SHOWN.isEmpty() && QUEUE.isEmpty()) {
            hidden = false;
            return;
        }
        if (!EffectsDisplayConfig.hudNotices()) {
            clear();
            return;
        }
        if (level == null || mc.player == null || mc.options.hideGui || mc.screen != null || MercyClient.isActive()) {
            // 盖着、藏着、处决中：不画，时间也停住
            hidden = true;
            return;
        }
        if (hidden) {
            hidden = false;
            unhideAt = vis;
            for (Entry e : SHOWN) {
                e.y = Float.NaN;
            }
        }
        if (!debugFrozen) {
            vis += dt;
        }
        dropStale(director);

        int bottom = NoticeLayout.stackBottom(height, gui.leftHeight, gui.rightHeight);
        int avail = bottom - NoticeLayout.stackTop(height);
        int maxLines = NoticeLayout.maxLines(avail);
        float leftInner = Float.NaN;
        float rightInner = Float.NaN;
        if (EffectsDisplayConfig.commHud()) {
            CommHud.reservedGui(true, RES);
            leftInner = RES[2];
            CommHud.reservedGui(false, RES);
            rightInner = RES[0];
        }
        int maxW = NoticeLayout.maxBoxWidth(width, leftInner, rightInner);
        for (Entry e : SHOWN) {
            layout(e, maxW, maxLines);
        }
        advance(maxW, maxLines, avail);
        if (SHOWN.isEmpty()) {
            return;
        }

        // 从下往上排，新的在最下面
        int y = bottom;
        for (int i = SHOWN.size() - 1; i >= 0; i--) {
            Entry e = SHOWN.get(i);
            e.targetY = y - e.boxH;
            y = e.targetY - NoticeLayout.STACK_GAP;
            if (Float.isNaN(e.y)) {
                e.y = e.targetY;
            } else {
                e.y += (e.targetY - e.y) * (1.0F - (float) Math.exp(-dt / FOLLOW_MS));
                if (Math.abs(e.targetY - e.y) < 0.3F) {
                    e.y = e.targetY;
                }
            }
        }
        float target = shiftTarget(width);
        shift += (target - shift) * (1.0F - (float) Math.exp(-dt / SHIFT_MS));
        if (Math.abs(target - shift) < 0.3F) {
            shift = target;
        }

        float global = Mth.clamp((vis - unhideAt) / (float) UNHIDE_MS, 0.0F, 1.0F);
        int lwPx = GuiShapes.lineWidthPx();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ABOVE_CHAT_Z);
        for (Entry e : SHOWN) {
            int x0 = NoticeLayout.boxLeft(width, e.boxW) + Math.round(shift);
            drawEntry(g, mc.font, e, x0, Math.round(e.y), global, lwPx);
        }
        g.pose().popPose();
        g.flush();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 退场、收走、进场、催促 */
    private static void advance(int maxW, int maxLines, int avail) {
        for (Entry e : SHOWN) {
            if (debugHold && e.exitAt < 0L) {
                e.holdUntil = Math.max(e.holdUntil, vis + 1000L);
            }
            if (e.exitAt < 0L && vis >= e.holdUntil) {
                e.exitAt = vis;
            }
        }
        SHOWN.removeIf(e -> e.exitAt >= 0L && vis >= e.exitAt + EXIT_MS);
        Entry head = QUEUE.peekFirst();
        if (head == null) {
            return;
        }
        layout(head, maxW, maxLines);
        int used = 0;
        for (Entry e : SHOWN) {
            used += e.boxH + NoticeLayout.STACK_GAP;
        }
        boolean room = SHOWN.size() < MAX_VISIBLE && (SHOWN.isEmpty() || used + head.boxH <= avail);
        if (room) {
            if (vis - lastStart >= STAGGER_MS) {
                QUEUE.pollFirst();
                start(head);
                SHOWN.add(head);
                lastStart = vis;
            }
            return;
        }
        // 放不下：最早那条还没在退场的，缩短到解码完再看一会儿
        for (Entry e : SHOWN) {
            if (e.exitAt < 0L) {
                long minEnd = e.startMs + e.decodeEnd + (e.milestone ? MIN_SHOW_MILESTONE : MIN_SHOW);
                e.holdUntil = Math.min(e.holdUntil, Math.max(minEnd, vis));
                break;
            }
        }
    }

    private static void dropStale(long director) {
        Iterator<Entry> it = QUEUE.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (director - e.bornMs > (e.milestone ? STALE_MILESTONE_MS : STALE_MS)) {
                it.remove();
            }
        }
    }

    /** 通讯窗开着、和某一条上下重叠时整叠要横移多少（右边有窗往左、左边有窗往右），不出屏幕 */
    private static float shiftTarget(int width) {
        int side = CommHud.openSide();
        float target = 0.0F;
        if (side >= 0) {
            CommHud.reservedGui(side == 0, RES);
            for (Entry e : SHOWN) {
                int y0 = e.targetY;
                int y1 = y0 + e.boxH;
                if (y1 + NoticeLayout.COMM_GAP <= RES[1] || y0 - NoticeLayout.COMM_GAP >= RES[3]) {
                    continue;
                }
                int bx0 = NoticeLayout.boxLeft(width, e.boxW);
                int bx1 = bx0 + e.boxW;
                // 窗的边是小数，往离开窗的方向取整，留够 COMM_GAP
                if (side == 1) {
                    target = Math.min(target, (float) Math.floor((RES[0] - NoticeLayout.COMM_GAP) - bx1));
                } else {
                    target = Math.max(target, (float) Math.ceil((RES[2] + NoticeLayout.COMM_GAP) - bx0));
                }
            }
        }
        for (Entry e : SHOWN) {
            int bx0 = NoticeLayout.boxLeft(width, e.boxW);
            target = Mth.clamp(target, NoticeLayout.MARGIN - bx0, width - NoticeLayout.MARGIN - (bx0 + e.boxW));
        }
        return target;
    }

    // ==================== 画一条 ====================

    private static void drawEntry(GuiGraphics g, Font font, Entry e, int x0, int y0, float global, int lwPx) {
        long t = vis - e.startMs;
        long te = e.exitAt >= 0L ? vis - e.exitAt : -1L;
        float frameK = easeOutCubic(seg(t, 0, FRAME_MS));
        float plateK = seg(t, 0, PLATE_IN_MS);
        float textK = 1.0F;
        if (te >= 0L) {
            float out = 1.0F - easeInCubic(seg(te, EXIT_FRAME_AT, EXIT_MS));
            frameK = Math.min(frameK, out);
            plateK = Math.min(plateK, out);
            textK = 1.0F - seg(te, 0, EXIT_TEXT_MS);
        }
        plateK *= global;
        textK *= global;
        if (plateK <= 0.004F && textK <= 0.004F) {
            return;
        }
        int x1 = x0 + e.boxW;
        int y1 = y0 + e.boxH;
        float cx = (x0 + x1) * 0.5F;
        float lx = cx - (cx - x0) * frameK;
        float rx = cx + (x1 - cx) * frameK;
        float c = Math.min(NoticeLayout.CUT, Math.min((rx - lx) / 3.0F, e.boxH * 0.5F));
        boolean full = frameK >= 0.999F;
        float pulse = 1.0F - seg(vis - e.pulseAt, 0, PULSE_MS);
        Matrix4f m = g.pose().last().pose();

        // ---- 底：软阴影、墨烟板、左侧同色底、续上时的一闪 ----
        if (rx - lx > 1.0F) {
            chamfer(POLY, lx, y0, rx, y1, c);
            HoloShapes.softPolygon(g, m, POLY, 6, 1.0F, 2.5F, GuiShapes.argb(FxPalette.INK, SHADOW_A * plateK));
            GuiShapes.convexFill(g, POLY, 6, GuiShapes.argb(FxPalette.INK, PLATE_A * plateK));
            if (rx - lx > TINT_W + c) {
                leftTint(g, m, lx, y0, y1, c, e.line, TINT_A * plateK);
            }
            if (pulse > 0.0F) {
                GuiShapes.convexFill(g, POLY, 6, GuiShapes.argb(e.line, 0.22F * pulse * plateK));
            }
            if (e.milestone && t >= GLINT_AT && t < GLINT_AT + GLINT_MS && full) {
                glint(g, m, e, x0, y0, x1, y1, seg(t, GLINT_AT, GLINT_AT + GLINT_MS), plateK);
            }
        }

        // ---- 框线 ----
        float g1 = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float off = (lwPx & 1) == 1 ? 0.5F / g1 : 0.0F;
        float lw = GuiShapes.pixels(g, lwPx);
        int dim = GuiShapes.argb(e.line, (e.milestone ? DIM_A_MILESTONE : DIM_A) * plateK);
        int bright = GuiShapes.argb(lerpRgb(e.line, FxPalette.WHITE, 0.6F * pulse), BRIGHT_A * plateK);
        GuiShapes.begin(g);
        GuiShapes.hline(g, lx + c, rx, y0, lwPx, dim);
        GuiShapes.hline(g, lx, rx - c, y1, lwPx, dim);
        if (full) {
            GuiShapes.vline(g, x0, y0 + c, y1, lwPx, dim);
            GuiShapes.vline(g, x1, y0, y1 - c, lwPx, dim);
        }
        GuiShapes.hairline(g, lx + off, y0 + c + off, lx + c + off, y0 + off, lw, dim);
        GuiShapes.hairline(g, rx + off, y1 - c + off, rx - c + off, y1 + off, lw, dim);
        // 两个切角连着一小段边是亮的
        PATH[0] = lx + off;
        PATH[1] = y0 + c + BRIGHT_V * frameK + off;
        PATH[2] = lx + off;
        PATH[3] = y0 + c + off;
        PATH[4] = lx + c + off;
        PATH[5] = y0 + off;
        PATH[6] = Math.min(lx + c + BRIGHT_H, cx) + off;
        PATH[7] = y0 + off;
        GuiShapes.polyline(g, PATH, 4, false, lw, bright);
        PATH[0] = rx + off;
        PATH[1] = y1 - c - BRIGHT_V * frameK + off;
        PATH[2] = rx + off;
        PATH[3] = y1 - c + off;
        PATH[4] = rx - c + off;
        PATH[5] = y1 + off;
        PATH[6] = Math.max(rx - c - BRIGHT_H, cx) + off;
        PATH[7] = y1 + off;
        GuiShapes.polyline(g, PATH, 4, false, lw, bright);
        if (full) {
            int tick = GuiShapes.argb(FxPalette.TECH, TICK_A * plateK);
            GuiShapes.vline(g, x0 + NoticeLayout.TICK_A, y1 - NoticeLayout.TICK_LEN, y1, lwPx, tick);
            GuiShapes.vline(g, x0 + NoticeLayout.TICK_B, y1 - NoticeLayout.TICK_LEN, y1, lwPx, tick);
        }
        GuiShapes.end();

        // ---- 小菱 ----
        float mk = easeOutBack(seg(t, MARK_AT, MARK_AT + MARK_MS), 1.7F);
        if (mk > 0.01F && full) {
            float mx = x0 + NoticeLayout.MARK_X;
            float my = y0 + NoticeLayout.PAD_T + NoticeLayout.LINE_H * 0.5F;
            float hh = NoticeLayout.MARK_HH * mk;
            float hw = hh * GuiShapes.DIAMOND_ASPECT;
            GuiShapes.begin(g);
            if (e.milestone) {
                GuiShapes.diamondFill(g, mx, my, hw, hh, GuiShapes.argb(e.line, 0.95F * plateK));
                float rh = hh + 1.6F;
                GuiShapes.diamondOutline(g, mx, my, rh * GuiShapes.DIAMOND_ASPECT, rh, lw,
                        GuiShapes.argb(e.line, 0.5F * plateK));
            } else {
                GuiShapes.diamondFill(g, mx, my, hw, hh, GuiShapes.argb(e.line, 0.3F * plateK));
                GuiShapes.diamondOutline(g, mx, my, hw, hh, lw, GuiShapes.argb(e.line, 0.95F * plateK));
            }
            GuiShapes.end();
        }

        // ---- 字 ----
        if (textK > 0.004F && t >= DECODE_AT - LEAD_MS) {
            boolean clip = !full;
            if (clip) {
                g.enableScissor((int) Math.floor(lx), y0, (int) Math.ceil(rx), y1);
            }
            drawText(g, font, e, x0 + NoticeLayout.PAD_L, y0 + NoticeLayout.PAD_T, t, textK);
            if (clip) {
                g.disableScissor();
            }
        }
    }

    /** 逐字解码：锁定前 60 ms 出现（拉丁字母滚乱码，其余从类别色淡入），锁定后从暖白落到自己的颜色 */
    private static void drawText(GuiGraphics g, Font font, Entry e, int tx, int ty, long t, float alpha) {
        int bucket = (int) (t / SCRAMBLE_STEP_MS);
        int order = 0;
        int lastLock = 0;
        for (int i = 0; i < e.n; i++) {
            if (e.gl[i] < 0) {
                continue;
            }
            int lock = DECODE_AT + order * e.step;
            order++;
            lastLock = lock;
            if ((e.cls[i] & 0x0F) == NoticeLayout.SPACE || t < lock - LEAD_MS) {
                continue;
            }
            int x = tx + e.gx[i];
            int y = ty + e.gl[i] * NoticeLayout.PITCH;
            if (t < lock) {
                float k = (t - (lock - LEAD_MS)) / (float) LEAD_MS;
                boolean letter = (e.cls[i] & 0x0F) == NoticeLayout.WORD && (e.cls[i] & NoticeLayout.WIDE_BIT) == 0;
                if (letter) {
                    int s = scrambleIndex(bucket, i);
                    int sx = x + (e.adv[i] - scrambleAdv[s]) / 2;
                    glyph(g, font, scrambleSeq[s], sx, y, e.line, 0.75F * alpha);
                } else {
                    glyph(g, font, e.seq[i], x, y, e.line, 0.8F * k * alpha);
                }
                continue;
            }
            int rgb = lerpRgb(FLASH, e.rgb[i], seg(t, lock, lock + SETTLE_MS));
            glyph(g, font, e.seq[i], x, y, rgb, alpha);
        }
        if (e.ellX >= 0 && t >= lastLock) {
            glyph(g, font, ellipsisSeq, tx + e.ellX, ty + e.ellLine * NoticeLayout.PITCH, BODY, alpha);
        }
    }

    private static void glyph(GuiGraphics g, Font font, FormattedCharSequence s, int x, int y, int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        // 原版字体把 α < 4 当成不透明，太淡的直接不画
        if (a < 5) {
            return;
        }
        g.drawString(font, s, x, y, (a << 24) | (rgb & 0xFFFFFF), false);
    }

    /** 左上、右下切角的六边形：左上切角后 → 右上 → 右下切角前 → 右下切角后 → 左下 → 左上切角前 */
    private static void chamfer(float[] out, float x0, float y0, float x1, float y1, float c) {
        out[0] = x0 + c;
        out[1] = y0;
        out[2] = x1;
        out[3] = y0;
        out[4] = x1;
        out[5] = y1 - c;
        out[6] = x1 - c;
        out[7] = y1;
        out[8] = x0;
        out[9] = y1;
        out[10] = x0;
        out[11] = y0 + c;
    }

    /** 左侧往右淡掉的同色底，按切角裁好（透明度只随 x 线性变，三角形插值是准的） */
    private static void leftTint(GuiGraphics g, Matrix4f m, float x0, float y0, float y1, float c, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        float xr = x0 + TINT_W;
        float ac = a * (1.0F - c / TINT_W);
        BufferBuilder buf = HoloShapes.open(g, false);
        HoloShapes.vtx(buf, m, x0, y0 + c, rgb, a);
        HoloShapes.vtx(buf, m, x0 + c, y0, rgb, ac);
        HoloShapes.vtx(buf, m, xr, y0, rgb, 0.0F);
        HoloShapes.vtx(buf, m, x0, y0 + c, rgb, a);
        HoloShapes.vtx(buf, m, xr, y0, rgb, 0.0F);
        HoloShapes.vtx(buf, m, xr, y1, rgb, 0.0F);
        HoloShapes.vtx(buf, m, x0, y0 + c, rgb, a);
        HoloShapes.vtx(buf, m, xr, y1, rgb, 0.0F);
        HoloShapes.vtx(buf, m, x0, y1, rgb, a);
        HoloShapes.close(buf);
    }

    /** 里程碑进场的一道扫光：竖向软边光带从左扫到右，裁在框里 */
    private static void glint(GuiGraphics g, Matrix4f m, Entry e, int x0, int y0, int x1, int y1, float p, float a) {
        float k = (float) Math.sin(Math.PI * p);
        float bx = x0 - 10.0F + (x1 - x0 + 20.0F) * easeInOutSine(p);
        boolean additive = EffectsDisplayConfig.additiveGlow();
        float strength = 0.22F * k * a * glowScale();
        if (strength <= 0.004F) {
            return;
        }
        g.enableScissor(x0, y0, x1, y1);
        HoloShapes.softRect(g, m, bx - 2.0F, y0, bx + 2.0F, y1, 5.0F,
                GuiShapes.argb(lerpRgb(e.line, FxPalette.WHITE, 0.5F), strength), additive);
        g.disableScissor();
    }

    // ==================== 小工具 ====================

    private static float seg(long t, int a, int b) {
        return Mth.clamp((t - a) / (float) (b - a), 0.0F, 1.0F);
    }

    private static float easeOutCubic(float x) {
        float u = 1.0F - x;
        return 1.0F - u * u * u;
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    private static float easeInOutSine(float x) {
        return (float) (-(Math.cos(Math.PI * x) - 1.0D) * 0.5D);
    }

    private static float easeOutBack(float x, float s) {
        if (x <= 0.0F) {
            return 0.0F;
        }
        float u = x - 1.0F;
        return 1.0F + (s + 1.0F) * u * u * u + s * u * u;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int gg = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (gg << 8) | bl;
    }

    /** 第 i 个字在第 bucket 个 40 ms 里显示哪个乱码字（固定种子，同一时刻画出来一样） */
    private static int scrambleIndex(int bucket, int i) {
        int h = bucket * 7919 + 13 + i * 104729;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return Math.floorMod(h, scrambleSeq.length);
    }

    /** 画在 HUD 上的加法光该乘的系数：白天露天按相机处压暗；关了加法发光时改普通混合、再淡一些 */
    private static float glowScale() {
        if (!EffectsDisplayConfig.additiveGlow()) {
            return 0.6F;
        }
        Camera cam = KuvaFxDirector.camera();
        if (cam == null) {
            return 1.0F;
        }
        Vec3 p = cam.getPosition();
        return KuvaFxDirector.additiveScale(p.x, p.y, p.z);
    }

    // ==================== 调试入口（开发环境的命令用） ====================

    /** 一直停着不退场 */
    static void debugHold(boolean on) {
        debugHold = on;
    }

    /** 定住可见时钟（逐帧截图对动效） */
    static void debugFreeze(boolean on) {
        debugFrozen = on;
    }

    /** 把可见时钟往前拨 */
    static void debugStep(long ms) {
        vis += ms;
    }

    /** 现在的状态，一行 */
    static String debugState() {
        return "shown " + SHOWN.size() + ", queued " + QUEUE.size() + ", clock " + vis + (hidden ? " (hidden)" : "");
    }
}
