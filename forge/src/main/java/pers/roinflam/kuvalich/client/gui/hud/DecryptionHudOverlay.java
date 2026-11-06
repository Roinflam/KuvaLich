package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.MurmurTriangle;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils;
import pers.roinflam.kuvalich.client.lich.BossBarTracker;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.client.lich.LichArchiveScreen;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * 解密进度 HUD「赤毒菱晶」（客户端）
 *
 * <p>打奴仆攒解密进度时，屏幕上方正中弹出一枚竖长的赤毒菱晶：外圈从上顶点顺时针点亮、里面的赤毒液面同步升高，
 * 两样都是这一阶段的密语进度。下面一行「安魂密语」，右边三颗小菱表示已经揭示了几条，有玄骸时再下面一行是它的名字。
 * 整块只垫一团墨烟，没有方框、没有横条和数字。淡入 500 ms、停 3.15 s、淡出 350 ms，不滑动、不缩放、不闪、不出声。</p>
 *
 * <p>揭示一条密语时：菱晶先走满，菱内的东西淡掉、只留一圈淡外框慢慢放大，这张卡自己的符号在原位 750 ms 淡入，
 * 下面换成卡名；揭示第三条时换成三枚并排；破解（成功终结玄骸）时三枚并排、菱框灌满赤毒，名字行写刚终结的那只。
 * 这三种都有自己的声音，进度增长本身不出声。</p>
 *
 * <p>破解那一排比「全部揭示」的大一半（1080p 下菱框约 68 像素高），每枚下面三颗次数点是顶这一枚的那张卡还剩几次：
 * 还剩的亮粉、这次用掉的那颗 {@link #BROKEN_SLASH_MS} 起变猩红并划一道斜杠、早就用掉的暗灰（和处决时三格下面的小点同一套画法），
 * 没有对应卡的那枚不画点。「密语已破解」和名字行跟着往下让，墨烟也跟着加长。</p>
 *
 * <p>位置：中心固定在屏幕高 0.143 处，屏幕上方有 Boss 条（原版的、别的模组的都算）时往下让到最下面那条之下 14 个单位，
 * 显示中 Boss 条出现、消失时 250 ms 缓过去。界面隐藏（F1）、怜悯之击运镜中、开着玄骸档案时不画。</p>
 *
 * <p>数据全部来自服务端的 {@link DecryptionHudPacket}（阈值随包下发，比例始终和服务端一致）。
 * 尺寸都按屏幕高算：以 480×270 的 GUI 为准定的数，别的分辨率等比缩放；线宽按物理像素（1080p 是 2 px），任何 GUI 缩放下粗细一样。
 * 时间走演出时钟 {@link KuvaFxDirector#clockMs()}，暂停时停住。画的时候不分配对象。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DecryptionHudOverlay {

    // ==================== 贴图与文字 ====================

    private static final ResourceLocation SMOKE = new ResourceLocation(Reference.MOD_ID, "textures/gui/hud/murmur_smoke.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Reference.MOD_ID, "textures/particle/glow.png");

    private static final String KEY_LABEL = "kuvalich.hud.murmur.label";
    private static final String KEY_ALL = "kuvalich.hud.murmur.all";
    private static final String KEY_BROKEN = "kuvalich.hud.murmur.broken";

    /** 卡名（拉丁专名，各语言一样），按卡 id */
    private static final String[] CARD_NAMES = {"Khra", "Jahu", "Fass", "Lohk", "Netra", "Vome", "Xata", "Ris"};

    // ==================== 版面（按屏幕高 H、宽 W 的比例） ====================

    /** 菱晶中心的高度 */
    private static final float CY = 0.143F;
    /** 菱晶半高 */
    private static final float HALF_H = 0.030F;
    /** Boss 条下面留的空 */
    private static final int BOSS_GAP = 14;
    /** 墨烟：中心下移、宽、高；揭示时放大到的宽、高、下移 */
    private static final float SMOKE_DY = 0.025F, SMOKE_W = 0.20F, SMOKE_H = 0.12F;
    private static final float SMOKE_W_REVEAL = 0.23F, SMOKE_H_REVEAL = 0.17F, SMOKE_DY_REVEAL = 0.045F;
    /** 背后柔光的边长（× W）和透明度 */
    private static final float GLOW_SIZE = 0.03F, GLOW_ALPHA = 0.35F;
    /** 标签顶、标签和名字行的间隔 */
    private static final float LABEL_DY = 0.044F, NAME_GAP = 0.006F;
    /** 字号：大写字母高 0.016H（字体的大写字母高 7） */
    private static final float TEXT_CAP = 0.016F;
    /** 揭示：符号高、卡名顶、下面那行小字的顶、卡名相对标签字号的倍数 */
    private static final float SIGIL_H = 0.075F, CARD_NAME_DY = 0.050F, CARD_SUB_DY = 0.077F, CARD_NAME_SCALE = 1.2F;
    /** 三枚并排：菱框半高（× a）、间距（× W）；符号高和菱框高之比跟单枚揭示时一样（0.075H : 2.8a） */
    private static final float ROW_HALF = 0.7F, ROW_STEP = 0.05F;
    private static final float ROW_SIGIL = SIGIL_H / (2.8F * HALF_H) * 2.0F * ROW_HALF;
    /** 破解那一排：菱框半高（× a）、间距（× W）、符号高，都是全部揭示那一排的 1.5 倍 */
    private static final float BROKEN_ROW_HALF = ROW_HALF * 1.5F, BROKEN_ROW_STEP = ROW_STEP * 1.5F;
    private static final float BROKEN_ROW_SIGIL = ROW_SIGIL * 1.5F;
    /** 破解的次数点：点心在菱框下角之下多远、直径、点距（× H）；斜杠半长是半径的几倍 */
    private static final float BROKEN_DOT_GAP = 0.016F, BROKEN_DOT_D = 0.013F, BROKEN_DOT_DX = 0.022F, BROKEN_SLASH = 1.7F;
    /** 破解时的标签顶（× H），比 {@link #LABEL_DY} 低出来的一截是给放大的菱框和次数点让的 */
    private static final float BROKEN_LABEL_DY = 0.068F;

    // ==================== 节奏（毫秒） ====================

    private static final long FADE_IN_MS = 500L;
    private static final long PROGRESS_MS = 600L;
    private static final long HOLD_MS = 3150L;
    private static final long FADE_OUT_MS = 350L;
    private static final long ALL_HOLD_MS = 2000L;
    /** 揭示：走满、菱内淡掉、符号淡入的分界；停留 */
    private static final long REVEAL_FILL_END = 600L, REVEAL_BODY_END = 900L, REVEAL_SIGIL_MS = 750L, REVEAL_HOLD_MS = 3000L;
    /** 破解：一起淡入、停留 */
    private static final long BROKEN_IN_MS = 750L, BROKEN_HOLD_MS = 3000L;
    /** 破解：这次用掉的那颗次数点从这一刻起变红、划斜杠，划完用多久（淡入走完再停一下才划，看得清是哪一颗） */
    private static final long BROKEN_SLASH_MS = 950L, BROKEN_SLASH_DRAW = 160L;
    /** 被新内容顶掉的那一版淡出 */
    private static final long RETIRE_MS = 250L;
    /** Boss 条变化时位置缓过去 */
    private static final long MOVE_MS = 250L;
    /** 揭示声对准符号淡入完成（声音的爆发点在 750 ms），所以在符号开始淡入时放 */
    private static final int REVEAL_SOUND_DELAY_TICKS = (int) (REVEAL_BODY_END / 50L);

    // ==================== 面板 ====================

    private static final int MODE_PROGRESS = 0;
    private static final int MODE_REVEAL = 1;
    private static final int MODE_REVEAL_ALL = 2;
    private static final int MODE_ALL = 3;
    private static final int MODE_BROKEN = 4;

    /** 一次弹出的全部状态。两份轮着用：新内容顶掉旧的时，旧的那份照原样画着淡出 */
    private static final class Panel {
        boolean live;
        int mode;
        /** 收到包的时刻 */
        long start;
        /** 淡入：起点、起始透明度、时长、是否用正弦曲线（破解用） */
        long fadeInAt;
        float fadeInFrom;
        long fadeInMs;
        boolean sineIn;
        /** 停留到几时，之后 350 ms 淡出 */
        long holdUntil;
        /** 被顶掉的时刻和当时的透明度，没有为 −1 */
        long retireAt = -1L;
        float retireFrom;
        /** 进度补间 */
        float progFrom;
        float progTo;
        long progAt;
        /** 阶段（已揭示几条） */
        int stage;
        int revealedCard = -1;
        final int[] riddles = {-1, -1, -1};
        /** 破解时三枚各自那张卡扣完剩几次（0 = 这次用完，−1 = 没有卡） */
        final int[] usesAfter = {-1, -1, -1};
        /** 破解时的名字（已转大写） */
        String brokenName = "";

        void copyFrom(Panel o) {
            live = o.live;
            mode = o.mode;
            start = o.start;
            fadeInAt = o.fadeInAt;
            fadeInFrom = o.fadeInFrom;
            fadeInMs = o.fadeInMs;
            sineIn = o.sineIn;
            holdUntil = o.holdUntil;
            retireAt = o.retireAt;
            retireFrom = o.retireFrom;
            progFrom = o.progFrom;
            progTo = o.progTo;
            progAt = o.progAt;
            stage = o.stage;
            revealedCard = o.revealedCard;
            System.arraycopy(o.riddles, 0, riddles, 0, 3);
            System.arraycopy(o.usesAfter, 0, usesAfter, 0, 3);
            brokenName = o.brokenName;
        }

        float progress(long now) {
            float t = Mth.clamp((now - progAt) / (float) PROGRESS_MS, 0.0F, 1.0F);
            return progFrom + (progTo - progFrom) * outCubic(t);
        }

        /** 整体透明度（淡入 × 淡出 × 被顶掉）；完全淡完返回 0 */
        float alpha(long now) {
            if (!live) {
                return 0.0F;
            }
            float tin = Mth.clamp((now - fadeInAt) / (float) fadeInMs, 0.0F, 1.0F);
            float in = fadeInFrom + (1.0F - fadeInFrom) * (sineIn ? MurmurTriangle.easeInOutSine(tin) : inOutCubic(tin));
            float out = 1.0F;
            if (now > holdUntil) {
                out = 1.0F - (now - holdUntil) / (float) FADE_OUT_MS;
            }
            float a = in * out;
            if (retireAt >= 0L) {
                a = Math.min(a, retireFrom * (1.0F - (now - retireAt) / (float) RETIRE_MS));
            }
            return Mth.clamp(a, 0.0F, 1.0F);
        }

        boolean finished(long now) {
            return !live || now > holdUntil + FADE_OUT_MS || (retireAt >= 0L && now > retireAt + RETIRE_MS);
        }
    }

    private static final Panel CUR = new Panel();
    private static final Panel OLD = new Panel();

    /** 揭示、破解期间又来的进度包：先记着，那一版放完再弹 */
    private static boolean pending;
    private static int pendingLevel;
    private static float pendingProgress;
    private static final int[] PENDING_RIDDLES = {-1, -1, -1};

    /** 中心高度的缓动 */
    private static float cyFrom = -1.0F;
    private static float cyTo = -1.0F;
    private static long cyAt;

    private static WeakReference<Level> lastLevelRef = null;

    // ==================== 调试（只给开发环境的命令用） ====================

    /** 假装屏幕上有 Boss 条，底在这里；−1 = 不假装 */
    private static int debugBossBottom = -1;
    /** 把当前这一版定在收到包后的第几毫秒；−1 = 不定 */
    private static long debugFreezeMs = -1L;
    /** 不淡出，一直停着 */
    private static boolean debugHold;

    // ==================== 排版缓存 ====================

    /** 一行带字距的字：拆成单字、算好每个字的横坐标（字体单位）。只在文字变了时重算 */
    private static final class Spaced {
        String src;
        float spacing;
        boolean cjkRule;
        String[] glyphs = new String[0];
        float[] xs = new float[0];
        float width;
    }

    private static final Spaced[] LAYOUTS = new Spaced[12];
    private static int layoutNext;
    private static String lichTitleSeen;
    private static String lichUpper = "";

    private DecryptionHudOverlay() {
    }

    // ==================== 收包 ====================

    /**
     * 服务端下发的解密进度到了。协议 10 追加的揭示卡号、三枚密语、玄骸名字在这一次调用期间从
     * {@link ClientPacketHandlers#currentDecryption()} 读出来、当场拷下（它只在这一刻有值）
     *
     * @param eventType 事件（{@link DecryptionHudPacket} 的 EVENT_*）
     * @param level     当前阶段（已揭示几条）
     * @param progress  当前阶段的进度
     * @param threshold 当前阶段的阈值（满了为 −1）
     * @param added     这次加了多少
     */
    public static void onPacket(int eventType, int level, int progress, int threshold, int added) {
        DecryptionHudPacket full = ClientPacketHandlers.currentDecryption();
        int revealed = -1;
        int[] riddles = null;
        int[] uses = null;
        String name = "";
        if (full != null) {
            revealed = full.revealedCard();
            riddles = full.riddles();
            uses = full.usesAfter();
            name = full.lichName();
        }
        show(eventType, level, progress, threshold, added, revealed, riddles, name, uses);
    }

    /**
     * 按收到一个解密进度包的样子弹一次（{@link #onPacket} 和调试命令共用）
     *
     * @param revealedCard 这次新揭示的卡 id，没有为 −1
     * @param riddles      三枚密语（卡 id，−1 为未揭示），可以为 {@code null}
     * @param lichName     破解时刚终结的玄骸名字
     * @param usesAfter    破解时三枚各自那张卡扣完剩几次（0 = 这次用完，−1 = 没有卡），可以为 {@code null}
     */
    public static void show(int eventType, int level, int progress, int threshold, int added,
                            int revealedCard, int[] riddles, String lichName, int[] usesAfter) {
        long now = clock();
        float p = threshold > 0 ? Mth.clamp(progress / (float) threshold, 0.0F, 1.0F) : (level >= 3 ? 1.0F : 0.0F);
        int stage = Mth.clamp(level, 0, 3);

        if (eventType == DecryptionHudPacket.EVENT_COMPLETE) {
            Panel n = begin(now, MODE_BROKEN, 3);
            copyRiddles(riddles, n.riddles);
            copyRiddles(usesAfter, n.usesAfter);
            n.brokenName = lichName == null ? "" : lichName.toUpperCase(Locale.ROOT);
            n.fadeInAt = now;
            n.fadeInFrom = 0.0F;
            n.fadeInMs = BROKEN_IN_MS;
            n.sineIn = true;
            n.holdUntil = now + BROKEN_IN_MS + BROKEN_HOLD_MS;
            pending = false;
            playUi(KuvaLichSounds.UI_DECRYPT_COMPLETE.get(), 0);
            return;
        }

        if (eventType == DecryptionHudPacket.EVENT_REVEAL) {
            boolean visible = CUR.live && !CUR.finished(now);
            float fromAlpha = visible ? CUR.alpha(now) : 0.0F;
            // 揭示前那一阶段已经显示到哪了：同一版接着走，否则从空的开始
            float fromProgress = visible && CUR.mode == MODE_PROGRESS ? CUR.progress(now) : 0.0F;
            boolean continueSame = visible && CUR.mode == MODE_PROGRESS;
            int mode = stage >= 3 ? MODE_REVEAL_ALL : MODE_REVEAL;
            Panel n = continueSame ? reuse(now, mode, stage) : begin(now, mode, stage);
            n.revealedCard = revealedCard;
            copyRiddles(riddles, n.riddles);
            n.progFrom = fromProgress;
            n.progTo = 1.0F;
            n.progAt = now;
            n.fadeInAt = now;
            n.fadeInFrom = continueSame ? fromAlpha : 0.0F;
            n.fadeInMs = FADE_IN_MS;
            n.sineIn = false;
            n.holdUntil = now + REVEAL_BODY_END + REVEAL_SIGIL_MS + REVEAL_HOLD_MS;
            pending = false;
            playUi(revealSound(stage), REVEAL_SOUND_DELAY_TICKS);
            return;
        }

        // 普通进度
        if (stage >= 3) {
            // 三条都揭示了还在杀：三枚并排停 2 秒
            if (CUR.live && !CUR.finished(now) && isShowcase(CUR.mode)) {
                return;
            }
            boolean visible = CUR.live && !CUR.finished(now) && CUR.mode == MODE_ALL;
            Panel n = visible ? reuse(now, MODE_ALL, 3) : begin(now, MODE_ALL, 3);
            copyRiddles(riddles, n.riddles);
            if (!visible) {
                n.fadeInAt = now;
                n.fadeInFrom = 0.0F;
                n.fadeInMs = FADE_IN_MS;
                n.sineIn = false;
            }
            n.holdUntil = Math.max(now, n.fadeInAt + FADE_IN_MS) + ALL_HOLD_MS;
            return;
        }

        if (CUR.live && !CUR.finished(now) && isShowcase(CUR.mode)) {
            // 揭示 / 破解还在放：记下来，放完再弹新阶段的菱晶
            pending = true;
            pendingLevel = stage;
            pendingProgress = p;
            copyRiddles(riddles, PENDING_RIDDLES);
            return;
        }
        boolean visible = CUR.live && !CUR.finished(now) && CUR.mode == MODE_PROGRESS && CUR.stage == stage;
        if (visible) {
            CUR.progFrom = CUR.progress(now);
            CUR.progTo = p;
            CUR.progAt = now;
            if (now > CUR.holdUntil) {
                // 正在淡出：从当前透明度重新淡入
                CUR.fadeInFrom = CUR.alpha(now);
                CUR.fadeInAt = now;
            }
            CUR.holdUntil = Math.max(now, CUR.fadeInAt + CUR.fadeInMs) + HOLD_MS;
            copyRiddles(riddles, CUR.riddles);
            return;
        }
        startProgress(now, stage, p, riddles);
    }

    private static void startProgress(long now, int stage, float p, int[] riddles) {
        Panel n = begin(now, MODE_PROGRESS, stage);
        copyRiddles(riddles, n.riddles);
        n.progFrom = 0.0F;
        n.progTo = p;
        n.progAt = now;
        n.fadeInAt = now;
        n.fadeInFrom = 0.0F;
        n.fadeInMs = FADE_IN_MS;
        n.sineIn = false;
        n.holdUntil = now + FADE_IN_MS + HOLD_MS;
    }

    /** 开一版新的；当前那一版还看得见就让它淡出 */
    private static Panel begin(long now, int mode, int stage) {
        if (CUR.live && !CUR.finished(now)) {
            float a = CUR.alpha(now);
            OLD.copyFrom(CUR);
            OLD.retireAt = now;
            OLD.retireFrom = a;
        } else {
            OLD.live = false;
        }
        if (!CUR.live || CUR.finished(now)) {
            // 隐藏中弹出：位置直接到位，不从上一次的地方滑过来
            cyFrom = -1.0F;
        }
        reset(CUR, now, mode, stage);
        return CUR;
    }

    /** 接着当前这一版画（揭示接在进度后面、全部揭示续停），不淡出重来 */
    private static Panel reuse(long now, int mode, int stage) {
        CUR.mode = mode;
        CUR.stage = stage;
        CUR.start = now;
        CUR.retireAt = -1L;
        return CUR;
    }

    private static void reset(Panel n, long now, int mode, int stage) {
        n.live = true;
        n.mode = mode;
        n.stage = stage;
        n.start = now;
        n.retireAt = -1L;
        n.revealedCard = -1;
        n.brokenName = "";
        n.progFrom = 0.0F;
        n.progTo = 0.0F;
        n.progAt = now;
        n.riddles[0] = n.riddles[1] = n.riddles[2] = -1;
        n.usesAfter[0] = n.usesAfter[1] = n.usesAfter[2] = -1;
    }

    private static boolean isShowcase(int mode) {
        return mode == MODE_REVEAL || mode == MODE_REVEAL_ALL || mode == MODE_BROKEN;
    }

    private static void copyRiddles(int[] src, int[] dst) {
        for (int i = 0; i < 3; i++) {
            dst[i] = src != null && i < src.length ? src[i] : -1;
        }
    }

    private static SoundEvent revealSound(int stage) {
        if (stage >= 3) {
            return KuvaLichSounds.UI_DECRYPT_REVEAL3.get();
        }
        return stage == 2 ? KuvaLichSounds.UI_DECRYPT_REVEAL2.get() : KuvaLichSounds.UI_DECRYPT_REVEAL1.get();
    }

    private static void playUi(SoundEvent ev, int delayTicks) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, 1.0F, 1.0F), delayTicks);
    }

    // ==================== 注册 ====================

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("requiem_decryption_hud", DecryptionHudOverlay::render);
    }

    // ==================== 画 ====================

    /**
     * 每帧
     *
     * @param width  GUI 宽
     * @param height GUI 高
     */
    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (lastLevelRef == null || lastLevelRef.get() != level) {
            lastLevelRef = new WeakReference<>(level);
            clearAll();
        }
        if (level == null || mc.player == null) {
            return;
        }
        long now = clock();
        if (CUR.live && CUR.finished(now)) {
            CUR.live = false;
            if (pending) {
                pending = false;
                startProgress(now, pendingLevel, pendingProgress, PENDING_RIDDLES);
            }
        }
        if (OLD.live && OLD.finished(now)) {
            OLD.live = false;
        }
        if (!CUR.live && !OLD.live) {
            return;
        }
        if (mc.options.hideGui || MercyClient.hidesHud() || archiveOpen(mc.screen)) {
            return;
        }
        if (debugHold && CUR.live) {
            CUR.holdUntil = Math.max(CUR.holdUntil, now + FADE_OUT_MS + 1000L);
        }

        float a = Math.max(3.0F, Math.round(HALF_H * height));
        float cx = width * 0.5F;
        // 给 Boss 条让位按画得最高的那一版算：破解那一排的菱框比单枚菱晶高一点
        boolean broken = (CUR.live && CUR.mode == MODE_BROKEN) || (OLD.live && OLD.mode == MODE_BROKEN);
        float cy = centerY(now, height, broken ? Math.max(a, BROKEN_ROW_HALF * a) : a);
        float lw = GuiShapes.pixels(g, GuiShapes.lineWidthPx());

        if (OLD.live) {
            drawPanel(g, mc.font, OLD, now, cx, cy, a, width, height, lw);
        }
        if (CUR.live) {
            drawPanel(g, mc.font, CUR, now, cx, cy, a, width, height, lw);
        }
        g.flush();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 中心高度：固定在 0.143H，有 Boss 条时让到它下面；显示中目标变了 250 ms 缓过去
     *
     * @param top 从中心往上画到多高（菱晶半高；破解那一排是放大后菱框的半高）
     */
    private static float centerY(long now, int height, float top) {
        int bottom = Math.max(BossBarTracker.bottomY(), debugBossBottom);
        float target = Math.max(Math.round(CY * height), bottom > 0 ? bottom + BOSS_GAP + top : 0.0F);
        if (cyFrom < 0.0F || cyTo < 0.0F) {
            cyFrom = cyTo = target;
            cyAt = now;
        } else if (Math.abs(target - cyTo) > 0.01F) {
            cyFrom = currentCy(now);
            cyTo = target;
            cyAt = now;
        }
        return currentCy(now);
    }

    private static float currentCy(long now) {
        float t = Mth.clamp((now - cyAt) / (float) MOVE_MS, 0.0F, 1.0F);
        return cyFrom + (cyTo - cyFrom) * outCubic(t);
    }

    private static void drawPanel(GuiGraphics g, Font font, Panel pn, long now, float cx, float cy, float a,
                                  int width, int height, float lw) {
        float alpha = pn.alpha(now);
        if (alpha <= 0.004F) {
            return;
        }
        long t = now - pn.start;
        float ts = TEXT_CAP * height / 7.0F;

        // 下面有两行字、或者标签比较长时墨烟放大一点，好把字都垫住（亮天、雪地上露在烟外的字看不清）：
        // 揭示时跟着符号淡入慢慢放大；三枚并排、有名字行的直接用大的
        float grow = 0.0F;
        if (pn.mode == MODE_REVEAL || pn.mode == MODE_REVEAL_ALL) {
            grow = MurmurTriangle.easeInOutSine((t - REVEAL_BODY_END) / (float) REVEAL_SIGIL_MS);
        } else if (pn.mode == MODE_ALL || pn.mode == MODE_BROKEN || ClientLichState.hasLich()) {
            grow = 1.0F;
        }
        float sw = width * Mth.lerp(grow, SMOKE_W, SMOKE_W_REVEAL);
        float sh = height * Mth.lerp(grow, SMOKE_H, SMOKE_H_REVEAL);
        float sy = cy + height * Mth.lerp(grow, SMOKE_DY, SMOKE_DY_REVEAL);
        if (pn.mode == MODE_BROKEN) {
            // 破解那一排更宽更高、标签往下让了一截：墨烟加宽，下沿跟着标签往下让，上沿按菱框多出来的高度往上长，
            // 不然放大的符号上半截露在烟外，亮天上看不清
            float shift = (BROKEN_LABEL_DY - LABEL_DY) * height;
            float up = 2.0F * (BROKEN_ROW_HALF - ROW_HALF) * a;
            sw = Math.max(sw, width * (2.0F * BROKEN_ROW_STEP + 0.12F));
            sh += shift + up;
            sy += (shift - up) * 0.5F;
        }
        texQuad(g, SMOKE, cx - sw * 0.5F, sy - sh * 0.5F, cx + sw * 0.5F, sy + sh * 0.5F, 0xFFFFFF, alpha);
        float gs = width * GLOW_SIZE * 0.5F;
        texQuad(g, GLOW, cx - gs, cy - gs, cx + gs, cy + gs, FxPalette.KUVA_DEEP, GLOW_ALPHA * alpha);

        switch (pn.mode) {
            case MODE_PROGRESS -> {
                MurmurTriangle.drawCrystal(g, cx, cy, a, pn.progress(now), alpha, 0.0F, lw, now);
                drawTicks(g, cx, cy, a, lw, alpha);
                float labelTop = cy + LABEL_DY * height;
                float right = drawText(g, font, label(KEY_LABEL), cx, labelTop, ts, FxPalette.TEXT_MID, alpha, true, 0.0F);
                drawMinis(g, right + 4.0F * unitScale(height), labelTop + 4.0F * ts, height, pn.stage, alpha, lw);
                drawLichName(g, font, cx, labelTop, ts, height, alpha);
            }
            case MODE_REVEAL, MODE_REVEAL_ALL -> drawReveal(g, font, pn, t, now, cx, cy, a, width, height, ts, lw, alpha);
            case MODE_ALL -> {
                drawRow(g, pn.riddles, false, cx, cy, a, width, lw, alpha);
                float labelTop = cy + LABEL_DY * height;
                drawText(g, font, label(KEY_ALL), cx, labelTop, ts, FxPalette.TEXT_MID, alpha, true, 0.0F);
                drawLichName(g, font, cx, labelTop, ts, height, alpha);
            }
            case MODE_BROKEN -> {
                drawBrokenRow(g, pn, t, cx, cy, a, width, height, lw, alpha);
                float labelTop = cy + BROKEN_LABEL_DY * height;
                drawText(g, font, label(KEY_BROKEN), cx, labelTop, ts, FxPalette.TEXT_MID, alpha, true, 0.0F);
                if (!pn.brokenName.isEmpty()) {
                    drawText(g, font, pn.brokenName, cx, labelTop + 9.0F * ts + NAME_GAP * height, ts,
                            FxPalette.ASH, 0.85F * alpha, false, 1.0F);
                }
            }
            default -> {
            }
        }
    }

    /** 揭示一枚 / 揭示第三枚的时间轴（相对收到包） */
    private static void drawReveal(GuiGraphics g, Font font, Panel pn, long t, long now, float cx, float cy, float a,
                                   int width, int height, float ts, float lw, float alpha) {
        // 0~600：走满；600~900：菱内淡掉、外框留到 0.3；900 起：外框放大到 1.4a，符号和新文字 750 ms 淡入
        float body = 1.0F;
        if (t > REVEAL_FILL_END) {
            float k = Mth.clamp((t - REVEAL_FILL_END) / (float) (REVEAL_BODY_END - REVEAL_FILL_END), 0.0F, 1.0F);
            body = 1.0F - k * k * k;
        }
        float in = MurmurTriangle.easeInOutSine((t - REVEAL_BODY_END) / (float) REVEAL_SIGIL_MS);
        float frame = 0.3F * (1.0F - body);
        float hh = a * (1.0F + 0.4F * in);
        if (pn.mode == MODE_REVEAL_ALL) {
            // 第三枚：900 起单枚的外框让位给三枚并排
            frame *= 1.0F - Mth.clamp((t - REVEAL_BODY_END) / 300.0F, 0.0F, 1.0F);
        }
        MurmurTriangle.drawCrystal(g, cx, cy, hh, pn.progress(now), body * alpha, frame * alpha, lw, now);
        drawTicks(g, cx, cy, a, lw, body * alpha);

        float labelTop = cy + LABEL_DY * height;
        if (body > 0.004F) {
            float right = drawText(g, font, label(KEY_LABEL), cx, labelTop, ts, FxPalette.TEXT_MID, body * alpha, true, 0.0F);
            drawMinis(g, right + 4.0F * unitScale(height), labelTop + 4.0F * ts, height, pn.stage - 1, body * alpha, lw);
            drawLichName(g, font, cx, labelTop, ts, height, body * alpha);
        }
        if (in <= 0.0F) {
            return;
        }
        if (pn.mode == MODE_REVEAL_ALL) {
            drawRow(g, pn.riddles, false, cx, cy, a, width, lw, in * alpha);
            drawText(g, font, label(KEY_ALL), cx, labelTop, ts, FxPalette.TEXT_MID, in * alpha, true, 0.0F);
            drawLichName(g, font, cx, labelTop, ts, height, in * alpha);
            return;
        }
        drawCardSigil(g, pn.revealedCard, cx, cy, SIGIL_H * height, in * alpha);
        int id = pn.revealedCard;
        if (id >= 0 && id < CARD_NAMES.length) {
            drawText(g, font, CARD_NAMES[id], cx, cy + CARD_NAME_DY * height, ts * CARD_NAME_SCALE,
                    FxPalette.BONE, 0.95F * in * alpha, false, -0.6F);
        }
        drawText(g, font, label(KEY_LABEL), cx, cy + CARD_SUB_DY * height, ts, FxPalette.TEXT_MID, in * alpha, true, 0.0F);
    }

    /**
     * 破解那一排：放大的三枚 + 每枚下面的次数点
     *
     * @param t 收到包后多少毫秒
     */
    private static void drawBrokenRow(GuiGraphics g, Panel pn, long t, float cx, float cy, float a, int width, int height,
                                      float lw, float alpha) {
        float hh = BROKEN_ROW_HALF * a;
        float step = BROKEN_ROW_STEP * width;
        drawRow(g, pn.riddles, true, cx, cy, hh, step, BROKEN_ROW_SIGIL * a, lw, alpha);

        float y = cy + hh + BROKEN_DOT_GAP * height;
        float r = BROKEN_DOT_D * height * 0.5F;
        float dx = BROKEN_DOT_DX * height;
        float slash = Mth.clamp((t - BROKEN_SLASH_MS) / (float) BROKEN_SLASH_DRAW, 0.0F, 1.0F);
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            int left = pn.usesAfter[i];
            if (left < 0) {
                continue;
            }
            // 和上面的菱框共用同一个横坐标
            float x0 = cx + (i - 1) * step;
            for (int j = 0; j < 3; j++) {
                float x = x0 + (j - 1) * dx;
                if (j == left && slash > 0.0F) {
                    // 这次用掉的那颗：划斜杠的同时由亮粉变猩红；划之前照还剩的画
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxCurve.lerpColor(FxPalette.KUVA_PALE, FxPalette.KUVA, slash), alpha));
                    float s = r * BROKEN_SLASH;
                    GuiShapes.hairline(g, x - s, y + s, x - s + 2.0F * s * slash, y + s - 2.0F * s * slash, lw,
                            GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                } else if (j <= left) {
                    GuiShapes.disc(g, x, y, r + lw, GuiShapes.argb(FxPalette.KUVA, 0.45F * alpha));
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                } else {
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxPalette.ASH, 0.35F * alpha));
                }
            }
        }
        GuiShapes.end();
    }

    /** 三枚并排（全部揭示那一排的尺寸） */
    private static void drawRow(GuiGraphics g, int[] riddles, boolean broken, float cx, float cy, float a, int width,
                                float lw, float alpha) {
        drawRow(g, riddles, broken, cx, cy, ROW_HALF * a, ROW_STEP * width, ROW_SIGIL * a, lw, alpha);
    }

    /**
     * 三枚并排：破解时菱框灌满赤毒、符号只描浅粉白线；全部揭示时菱框是空的、符号是卡面的样子
     *
     * @param hh    菱框半高
     * @param step  相邻两枚的间距
     * @param sigil 符号高
     */
    private static void drawRow(GuiGraphics g, int[] riddles, boolean broken, float cx, float cy, float hh, float step,
                                float sigil, float lw, float alpha) {
        float hw = hh * GuiShapes.DIAMOND_ASPECT;
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * step;
            if (broken) {
                GuiShapes.diamondFill(g, x, cy, hw, hh, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F * alpha));
                GuiShapes.diamondOutline(g, x, cy, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F * alpha));
            } else {
                GuiShapes.diamondFill(g, x, cy, hw, hh, GuiShapes.argb(FxPalette.KUVA_MIST, 0.45F * alpha));
                GuiShapes.diamondOutline(g, x, cy, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.3F * alpha));
            }
        }
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * step;
            if (broken) {
                RequiemSigils.draw(g, riddles[i], x, cy, sigil, GuiShapes.argb(FxPalette.KUVA_PALE, alpha), 0);
            } else {
                drawCardSigil(g, riddles[i], x, cy, sigil, alpha);
            }
        }
        GuiShapes.end();
    }

    /** 揭示的符号：深红填充 + 粉白描边 + 沿线一圈红光 */
    private static void drawCardSigil(GuiGraphics g, int cardId, float x, float y, float size, float alpha) {
        if (alpha <= 0.002F) {
            return;
        }
        RequiemSigils.drawLayers(g, cardId, x, y, size, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F * alpha), 0,
                GuiShapes.argb(FxPalette.KUVA, 0.35F * alpha), GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
    }

    /** 两侧刻痕：左右各三道 2 个单位长的横线，越往外越淡 */
    private static void drawTicks(GuiGraphics g, float cx, float cy, float a, float lw, float alpha) {
        if (alpha <= 0.004F) {
            return;
        }
        float k = a / 8.0F;
        float b = a * GuiShapes.DIAMOND_ASPECT;
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            float off = b + (4.0F + 3.0F * i) * k;
            int c = GuiShapes.argb(FxPalette.KUVA_PALE, TICK_ALPHA[i] * alpha);
            GuiShapes.hairline(g, cx + off, cy, cx + off + 2.0F * k, cy, lw, c);
            GuiShapes.hairline(g, cx - off - 2.0F * k, cy, cx - off, cy, lw, c);
        }
        GuiShapes.end();
    }

    private static final float[] TICK_ALPHA = {0.55F, 0.35F, 0.18F};

    /** 标签右边三颗小菱：已揭示的实心，没揭示的只描边 */
    private static void drawMinis(GuiGraphics g, float x0, float midY, int height, int lit, float alpha, float lw) {
        if (alpha <= 0.004F) {
            return;
        }
        float u = unitScale(height);
        float hh = Math.max(3.0F, 0.011F * height) * 0.5F;
        float hw = hh * GuiShapes.DIAMOND_ASPECT;
        float step = 5.0F * u;
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            float x = x0 + hw + i * step;
            if (i < lit) {
                GuiShapes.diamondFill(g, x, midY, hw, hh, GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F * alpha));
            } else {
                GuiShapes.diamondOutline(g, x, midY, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * alpha));
            }
        }
        GuiShapes.end();
    }

    /** 有玄骸时标签下面一行它的名字（全大写） */
    private static void drawLichName(GuiGraphics g, Font font, float cx, float labelTop, float ts, int height, float alpha) {
        if (alpha <= 0.004F || !ClientLichState.hasLich()) {
            return;
        }
        String title = ClientLichState.nameTitle();
        if (title != lichTitleSeen) {
            lichTitleSeen = title;
            lichUpper = title.toUpperCase(Locale.ROOT);
        }
        if (lichUpper.isEmpty()) {
            return;
        }
        drawText(g, font, lichUpper, cx, labelTop + 9.0F * ts + NAME_GAP * height, ts,
                FxPalette.ASH, 0.85F * alpha, false, 1.0F);
    }

    /** 参照 480×270 的一个单位在这块屏幕上有多长 */
    private static float unitScale(int height) {
        return height / 270.0F;
    }

    // ==================== 文字 ====================

    private static String label(String key) {
        return Language.getInstance().getOrDefault(key);
    }

    /**
     * 居中画一行带字距的字
     *
     * @param top     字顶
     * @param scale   字号（相对原版字体）
     * @param cjkRule true：汉字之间加 0.35 个字宽、其余字符加 1；false：一律加 {@code spacing}
     * @param spacing cjkRule 为 false 时的字距：正数是字体单位，负数是「多少个字宽」（−0.6 = 0.6 个字宽）
     * @return 这行字右端的 x
     */
    private static float drawText(GuiGraphics g, Font font, String text, float cx, float top, float scale,
                                  int rgb, float alpha, boolean cjkRule, float spacing) {
        if (text == null || text.isEmpty()) {
            return cx;
        }
        Spaced s = layout(font, text, cjkRule, spacing);
        float w = s.width * scale;
        float x0 = cx - w * 0.5F;
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (a < 4) {
            return x0 + w;
        }
        int color = (a << 24) | (rgb & 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(x0, top, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        for (int i = 0; i < s.glyphs.length; i++) {
            g.drawString(font, s.glyphs[i], s.xs[i], 0.0F, color, false);
        }
        g.pose().popPose();
        return x0 + w;
    }

    private static Spaced layout(Font font, String text, boolean cjkRule, float spacing) {
        for (Spaced s : LAYOUTS) {
            if (s != null && s.src == text && s.cjkRule == cjkRule && s.spacing == spacing) {
                return s;
            }
        }
        Spaced s = LAYOUTS[layoutNext];
        if (s == null) {
            s = new Spaced();
            LAYOUTS[layoutNext] = s;
        }
        layoutNext = (layoutNext + 1) % LAYOUTS.length;
        s.src = text;
        s.cjkRule = cjkRule;
        s.spacing = spacing;
        int n = text.codePointCount(0, text.length());
        s.glyphs = new String[n];
        s.xs = new float[n];
        float x = 0.0F;
        int i = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            String glyph = new String(Character.toChars(cp));
            float gw = font.width(glyph);
            s.glyphs[i] = glyph;
            s.xs[i] = x;
            float gap;
            if (cjkRule) {
                gap = isCjk(cp) ? 0.35F * gw : 1.0F;
            } else {
                gap = spacing < 0.0F ? -spacing * gw : spacing;
            }
            x += gw + gap;
            i++;
            off += Character.charCount(cp);
        }
        // 最后一个字后面的字距不算进宽度，居中才准
        float last = n > 0 ? font.width(s.glyphs[n - 1]) : 0.0F;
        s.width = n > 0 ? s.xs[n - 1] + last : 0.0F;
        return s;
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL;
    }

    // ==================== 贴图 ====================

    /** 一张整贴图染色画到矩形里（墨烟、柔光），普通透明混合 */
    private static void texQuad(GuiGraphics g, ResourceLocation tex, float x0, float y0, float x1, float y1,
                                int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        if (a <= 0) {
            return;
        }
        GuiShapes.flush(g);
        Matrix4f m = g.pose().last().pose();
        int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        buf.vertex(m, x0, y0, 0.0F).color(r, gr, b, a).uv(0.0F, 0.0F).endVertex();
        buf.vertex(m, x0, y1, 0.0F).color(r, gr, b, a).uv(0.0F, 1.0F).endVertex();
        buf.vertex(m, x1, y1, 0.0F).color(r, gr, b, a).uv(1.0F, 1.0F).endVertex();
        buf.vertex(m, x1, y0, 0.0F).color(r, gr, b, a).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    // ==================== 其他 ====================

    /** 档案开着时 HUD 不画（档案自己会显示三枚密语） */
    private static boolean archiveOpen(Screen screen) {
        return screen instanceof LichArchiveScreen;
    }

    private static long clock() {
        long now = KuvaFxDirector.clockMs();
        if (debugFreezeMs >= 0L && CUR.live) {
            return CUR.start + debugFreezeMs;
        }
        return now;
    }

    private static void clearAll() {
        CUR.live = false;
        OLD.live = false;
        pending = false;
        cyFrom = cyTo = -1.0F;
    }

    private static float outCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static float inOutCubic(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) * 0.5F;
    }

    // ==================== 调试入口 ====================

    /** 假装屏幕上方有 Boss 条、底在 {@code bottom}（GUI 坐标）；−1 取消 */
    public static void debugBossBottom(int bottom) {
        debugBossBottom = bottom;
    }

    /** 把画面定在这一版收到包后的第 {@code ms} 毫秒（截图逐帧对照用）；−1 取消 */
    public static void debugFreeze(long ms) {
        debugFreezeMs = ms;
    }

    /** 一直停着不淡出 */
    public static void debugHold(boolean hold) {
        debugHold = hold;
    }

    /** 立刻收掉 */
    public static void debugClear() {
        clearAll();
    }
}
