package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.comm.CommText;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.MurmurTriangle;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.RageTier;
import pers.roinflam.kuvalich.nemesis.WaitReason;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 玄骸档案（K 键）：只看不改的一整屏
 *
 * <p>左栏是这只玄骸本身：等级徽章、名字、第几只、能力、怒气条、属性表、被它拿走的东西；中间是它的全身像，
 * 能按住左键拖着转身，开档案时它会说一句；右栏是玩家这边：当前放着的安魂卡、处决记录、已揭示的密语。
 * 全屏不画外框，只有两条竖的分隔线。</p>
 *
 * <p>打开动画：先压暗、铺墨烟（0~250 ms），标题、分隔线、右栏、状态行淡入（300~600 ms），
 * 左栏和模型最后出来（1500~1900 ms，模型从暗剪影里亮起来）。客户端选项 {@code archive_intro} 关掉、
 * 或者 10 秒内重开，所有东西 150 ms 一起淡入。底下一直放着灭骸之扉那条循环底噪（400 ms 淡入、关时 300 ms 淡出）。</p>
 *
 * <p>数据：打开时向服务端要一份档案，开着时每 5 秒再要一次；包回来之前用上次的，从没收到过的地方显示「……」。
 * 名字、等级、怒气、状态行读推送的状态（{@link ClientLichState}），比档案包更新得勤。
 * 调试命令可以塞一份假数据进来（{@link #demo}），那时完全不看服务端。</p>
 *
 * <p>不暂停游戏：交战中也能开，世界照常跑。窗口很小（宽 &lt; 360 或高 &lt; 200 个界面单位）时不画模型和能力图标，
 * 左右两栏各占一半宽。</p>
 */
@OnlyIn(Dist.CLIENT)
public class LichArchiveScreen extends Screen {

    private static final ResourceLocation SMOKE = new ResourceLocation(Reference.MOD_ID, "textures/gui/archive/smoke.png");
    private static final ResourceLocation BACKLIGHT = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/archive/backlight.png");

    /**
     * 整张档案往屏幕外推这么多；模型、物品、提示框都在这之上再往外推，跟着一起挪。
     * 聊天栏、热栏透出来的问题不靠它，见 {@link HideHud}
     */
    private static final float ARCHIVE_Z = 200.0F;

    /** 打开动画的几个时刻（ms） */
    private static final int T_DIM = 250;
    private static final int T_FRAME_IN = 300;
    private static final int T_FRAME_DONE = 600;
    private static final int T_BODY_IN = 1500;
    private static final int T_BODY_DONE = 1900;
    /** 快速淡入（不放打开动画时）用多久 */
    private static final int T_QUICK = 150;
    /** 模型开口后多久字幕开始擦出 */
    private static final int SUBTITLE_DELAY = 670;
    /** 字幕停留：clamp(1600 + 120 × (汉字 + 0.4 × 拉丁字符), 2500, 7000)，不短于嘟囔时长 + 300 ms */
    private static final int HOLD_BASE_MS = 1600;
    private static final int HOLD_PER_UNIT_MS = 120;
    private static final int HOLD_MIN_MS = 2500;
    private static final int HOLD_MAX_MS = 7000;
    private static final int MUTTER_TAIL_MS = 300;
    private static final int SUBTITLE_FADE_MS = 250;
    /** 10 秒内重开不放打开动画；同一只玄骸 10 分钟内只在档案里说一次话 */
    private static final long QUICK_REOPEN_MS = 10_000L;
    private static final long TALK_COOLDOWN_MS = 10L * 60L * 1000L;
    /** 开着时多久重新要一次档案 */
    private static final long REFRESH_MS = 5000L;

    /** 模型默认往画面左边偏多少度；没拖的时候在这附近 ±6° 慢摆，8 秒一个来回 */
    private static final float DEFAULT_YAW = 20.0F;
    private static final float SWAY_DEG = 6.0F;
    private static final float SWAY_PERIOD_MS = 8000.0F;
    /** 松手后转回默认朝向用多久 */
    private static final int YAW_RETURN_MS = 1500;

    /** 弱点、抗性的倍率（和实体里的固定倍率一致；按种子抽的那套也只换类别、不换倍率） */
    private static final String WEAK_MULT = "1.25";
    private static final String RESIST_MULT = "0.75";
    private static final String[] CATEGORY_KEYS = {
            "kuvalich.archive.cat.melee", "kuvalich.archive.cat.arrow",
            "kuvalich.archive.cat.projectile", "kuvalich.archive.cat.magic"
    };
    private static final String[] ABILITY_KEYS = {
            "kuvalich.archive.ability.adaptive", "kuvalich.archive.ability.fury",
            "kuvalich.archive.ability.regen", "kuvalich.archive.ability.quake"
    };
    /** 怒气档位的分界（和 {@link RageTier} 一致） */
    private static final float[] RAGE_TICKS = {0.12F, 0.5F, 0.8F, 0.96F};

    /** 上次关档案的时刻（真实时钟），判断 10 秒内重开 */
    private static long lastCloseMs = Long.MIN_VALUE / 4;
    /** 每只玄骸（按种子）上次在档案里说话的时刻（真实时钟），只在内存里 */
    private static final Map<Integer, Long> TALKED = new HashMap<>();
    /** 档案嘲讽有几句（第一次用时数语言文件里的键） */
    private static int tauntCount = -1;

    @Nullable
    private final LichArchivePacket.Data demo;

    private long openClock;
    private boolean quick;
    private boolean started;
    @Nullable
    private KuvaSoundInstance ambience;
    private long nextRefreshMs;

    // ---- 模型朝向 ----
    private boolean dragging;
    private double dragStartX;
    private float dragStartYaw;
    private float yawNow = DEFAULT_YAW;
    private float returnFromYaw;
    private long returnStartClock = Long.MIN_VALUE / 4;

    // ---- 嘲讽 ----
    private boolean talkPlanned;
    private long talkStartClock;
    private long subtitleStartClock;
    private long subtitleEndClock;
    private int talkSeed;
    @Nullable
    private List<FormattedCharSequence> subtitleLines;
    private int subtitleWidth;
    private int wipeMs;
    @Nullable
    private LichVoice.Schedule voice;

    // ---- 缓存 ----
    @Nullable
    private ClientLichState.State stateSource;
    private LichStatePacket stateView = LichStatePacket.EMPTY;
    @Nullable
    private LichArchivePacket.Data rowsFor;
    private String rowsLang = "";
    private final List<String> rowLabels = new ArrayList<>();
    private final List<String> rowValues = new ArrayList<>();
    @Nullable
    private LichPuppet.Pose pose;

    // ---- 三角 ----
    private final ArchiveTriangles triangles = new ArchiveTriangles();

    // ---- 这一帧的悬停 ----
    @Nullable
    private List<Component> hoverLines;
    private ItemStack hoverStack = ItemStack.EMPTY;

    public LichArchiveScreen() {
        this(null);
    }

    /**
     * @param demo 调试用的假档案；给了就完全不看服务端的数据
     */
    public LichArchiveScreen(@Nullable LichArchivePacket.Data demo) {
        super(Component.translatable("kuvalich.archive.title"));
        this.demo = demo;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (started) {
            return;
        }
        started = true;
        openClock = KuvaFxDirector.clockMs();
        long real = Util.getMillis();
        boolean reopen = real - lastCloseMs < QUICK_REOPEN_MS;
        quick = !EffectsDisplayConfig.archiveIntro() || reopen;
        if (demo == null) {
            ClientLichArchive.request();
        }
        nextRefreshMs = real + REFRESH_MS;
        ambience = KuvaSoundPlayer.play(KuvaSoundPlayer.uiLoop(KuvaLichSounds.BLOCK_REQUIEM_GATE_AMBIENCE.get(), 1.0F, 400));
        planTalk(EffectsDisplayConfig.archiveIntro() || !reopen, real);
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        long real = Util.getMillis();
        if (demo == null && real >= nextRefreshMs) {
            nextRefreshMs = real + REFRESH_MS;
            ClientLichArchive.request();
        }
    }

    @Override
    public void removed() {
        lastCloseMs = Util.getMillis();
        if (ambience != null) {
            ambience.fadeOut(300);
            ambience = null;
        }
        if (voice != null) {
            LichVoice.stop(voice, 150);
            voice = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KuvaKeyMappings.ARCHIVE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !small() && mx > width * 0.29F && mx < width * 0.75F && my < height * 0.88F) {
            dragging = true;
            dragStartX = mx;
            dragStartYaw = yawNow;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            yawNow = dragStartYaw - (float) (mx - dragStartX);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            returnFromYaw = yawNow;
            returnStartClock = KuvaFxDirector.clockMs();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // ==================== 数据 ====================

    @Nullable
    private LichArchivePacket.Data data() {
        return demo != null ? demo : ClientLichArchive.get();
    }

    /** 状态那一套：调试数据 → 推送的状态 → 档案包里那份 → 空 */
    private LichStatePacket state(@Nullable LichArchivePacket.Data data) {
        if (demo != null) {
            return demo.state();
        }
        ClientLichState.State s = ClientLichState.get();
        if (s != ClientLichState.NONE) {
            if (s != stateSource) {
                stateSource = s;
                stateView = new LichStatePacket(s.state(), s.waitReason(), s.name(), s.seed(), s.weapon(), s.rank(),
                        s.ragePermille(), s.tier(), s.clues(), s.cooldownSec(), s.generation(), s.paused());
            }
            return stateView;
        }
        return data != null ? data.state() : LichStatePacket.EMPTY;
    }

    private static boolean hasLich(LichStatePacket st) {
        return st.state != LichState.UNBORN && st.name != null && !st.name.isEmpty();
    }

    // ==================== 嘲讽 ====================

    /** 开档案时决定这次要不要说话：有玄骸、不是 10 秒内重开（或者开着打开动画）、这只 10 分钟内没在档案里说过 */
    private void planTalk(boolean allowed, long real) {
        LichStatePacket st = state(data());
        if (!allowed || !hasLich(st)) {
            return;
        }
        Long last = TALKED.get(st.seed);
        if (last != null && real - last < TALK_COOLDOWN_MS) {
            return;
        }
        int n = tauntCount();
        if (n <= 0) {
            return;
        }
        int pick = Math.floorMod((int) (real / 997L) ^ st.seed, n) + 1;
        String raw = Component.translatable("kuvalich.comm.lich.archive." + pick).getString();
        String plain = CommText.plain(raw);
        MutableComponent text = CommText.parse(raw);
        talkSeed = st.seed;
        talkStartClock = openClock + (quick ? T_QUICK : T_BODY_IN);
        subtitleStartClock = talkStartClock + SUBTITLE_DELAY;
        wipeMs = CommText.wipeDurationMs(plain);
        int han = CommText.hanCount(plain);
        int latin = CommText.latinCount(plain);
        int hold = Mth.clamp(Math.round(HOLD_BASE_MS + HOLD_PER_UNIT_MS * (han + 0.4F * latin)), HOLD_MIN_MS, HOLD_MAX_MS);
        LichVoice.Schedule sch = LichVoice.schedule(talkSeed, plain, subtitleStartClock);
        hold = Math.max(hold, sch.lengthMs() + MUTTER_TAIL_MS);
        subtitleEndClock = subtitleStartClock + hold;
        pendingRaw = text;
        pendingPlain = plain;
        talkPlanned = true;
        TALKED.put(st.seed, real);
    }

    private MutableComponent pendingRaw;
    private String pendingPlain;
    private boolean voiceStarted;

    private static int tauntCount() {
        if (tauntCount < 0) {
            int n = 0;
            while (n < 16 && I18n.exists("kuvalich.comm.lich.archive." + (n + 1))) {
                n++;
            }
            tauntCount = n;
        }
        return tauntCount;
    }

    // ==================== 画 ====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        hoverLines = null;
        hoverStack = ItemStack.EMPTY;
        long now = KuvaFxDirector.clockMs();
        long t = now - openClock;
        int w = width;
        int h = height;
        Font f = font;
        float lw = GuiShapes.pixels(g, GuiShapes.lineWidthPx());

        float eDim = quick ? FxCurve.clamp01(t / (float) T_QUICK) : FxCurve.easeOutCubic(FxCurve.clamp01(t / (float) T_DIM));
        float eFrame = quick ? eDim : inOutCubic(FxCurve.clamp01((t - T_FRAME_IN) / (float) (T_FRAME_DONE - T_FRAME_IN)));
        float eBody = quick ? eDim : inOutCubic(FxCurve.clamp01((t - T_BODY_IN) / (float) (T_BODY_DONE - T_BODY_IN)));

        LichArchivePacket.Data data = data();
        LichStatePacket st = state(data);
        boolean lich = hasLich(st);
        boolean small = small();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ARCHIVE_Z);

        // ---- 压暗 + 墨烟 ----
        g.fill(0, 0, w, h, GuiShapes.argb(0x000000, 0.85F * eDim));
        drawSmoke(g, w, h, now, 0.4F * eDim);

        // ---- 列分隔 ----
        GuiShapes.begin(g);
        int sep = GuiShapes.argb(FxPalette.KUVA_PALE, 0.12F * eFrame);
        if (small) {
            GuiShapes.hairline(g, w * 0.5F, h * 0.08F, w * 0.5F, h * 0.88F, lw, sep);
        } else {
            GuiShapes.hairline(g, w * 0.290F, h * 0.08F, w * 0.290F, h * 0.88F, lw, sep);
            GuiShapes.hairline(g, w * 0.750F, h * 0.08F, w * 0.750F, h * 0.88F, lw, sep);
        }
        GuiShapes.end();

        // ---- 中间：背光、地面线、模型、微粒 ----
        if (!small) {
            drawStage(g, w, h, now, t, st, lich, eFrame, eBody, partialTick, lw);
        }

        // ---- 左栏 ----
        float lx0 = small ? w * 0.04F : w * 0.077F;
        float lx1 = small ? w * 0.48F : w * 0.265F;
        drawTitle(g, f, lx0, lx1, h, eFrame, lw);
        drawLeft(g, f, mx, my, lx0, lx1, w, h, now, st, lich, data, eBody, small, lw);

        // ---- 右栏 ----
        float rx0 = small ? w * 0.52F : w * 0.775F;
        float rx1 = small ? w * 0.96F : w * 0.930F;
        drawRight(g, f, mx, my, rx0, rx1, w, h, now, st, data, eFrame, lw);

        // ---- 状态行、上一只、关闭提示 ----
        drawStatus(g, f, w, h, st, data, lich, small, eFrame, now);

        // ---- 字幕 ----
        if (!small) {
            drawSubtitle(g, f, w, h, now, st);
        }
        g.flush();

        // ---- 悬停提示 ----
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(f, hoverStack, mx, my);
        } else if (hoverLines != null) {
            g.renderComponentTooltip(f, hoverLines, mx, my);
        }
        g.pose().popPose();
    }

    private boolean small() {
        return width < 360 || height < 200;
    }

    private static float inOutCubic(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }

    /**
     * 字号按窗高取比例；窗口小时（如 1280×720）每个字体单位不到 2 个物理像素，汉字的 16 点阵会丢笔画，
     * 这时往上补到 2 个像素，但最多放大到 1.35 倍，免得更小的窗口排版挤爆
     */
    private static float tsOf(int h, float frac) {
        float ts = SpacedText.round8(h * frac / 7.0F, 0.5F);
        float crisp = (float) (2.0D / Minecraft.getInstance().getWindow().getGuiScale());
        return ts < crisp ? Math.min(crisp, ts * 1.35F) : ts;
    }

    /** 墨烟铺满全屏，按高度缩放、每秒往左飘 0.01 屏宽；贴图四边无缝，靠重复铺开 */
    private static void drawSmoke(GuiGraphics g, int w, int h, long now, float alpha) {
        if (alpha <= 0.004F) {
            return;
        }
        float tileW = h * 2.0F;
        float drift = (now / 1000.0F) * 0.01F * w;
        float u = (drift / tileW) * 256.0F % 256.0F;
        int uw = Math.max(1, Math.round(256.0F * w / tileW));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(SMOKE, 0, 0, w, h, u, 0.0F, uw, 128, 256, 128);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ---------- 中间 ----------

    private void drawStage(GuiGraphics g, int w, int h, long now, long t, LichStatePacket st, boolean lich,
                           float eFrame, float eBody, float pt, float lw) {
        float cx = w * 0.5F;
        float feet = h * 0.80F;
        // 背光
        if (eFrame > 0.004F) {
            int bw = Math.round(w * 0.30F);
            int bh = Math.round(h * 0.70F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, eFrame);
            g.blit(BACKLIGHT, Math.round(cx - bw * 0.5F), Math.round(h * 0.52F - bh * 0.5F), bw, bh, 0.0F, 0.0F,
                    128, 256, 128, 256);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        // 地面线：两端各 20% 渐隐
        GuiShapes.begin(g);
        float gw = w * 0.16F;
        int ground = GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * eFrame);
        int clear = ground & 0x00FFFFFF;
        float gx0 = cx - gw * 0.5F;
        GuiShapes.hairline(g, gx0, feet, gx0 + gw * 0.2F, feet, lw, clear, ground);
        GuiShapes.hairline(g, gx0 + gw * 0.2F, feet, gx0 + gw * 0.8F, feet, lw, ground);
        GuiShapes.hairline(g, gx0 + gw * 0.8F, feet, gx0 + gw, feet, lw, ground, clear);
        GuiShapes.end();

        // 模型：显形那 400 ms 从暗剪影亮起来；没有玄骸时一直是剪影、不说话
        if (eBody > 0.0F) {
            float dim = lich ? 1.0F - eBody : 1.0F;
            boolean talking = talkPlanned && now >= talkStartClock && now < subtitleEndClock;
            int seed = lich ? st.seed : 0;
            int weapon = lich ? st.weapon : -1;
            LichPuppet.Pose p = pose;
            if (p == null || p.seed() != seed || p.weapon() != weapon || p.talking() != talking
                    || Math.abs(p.dim() - dim) > 1.0E-3F) {
                p = new LichPuppet.Pose(seed, weapon, talking, dim);
                pose = p;
            }
            int heightPx = Math.round(h * 0.58F);
            LichPuppet.renderFull(g, Math.round(cx), Math.round(feet), heightPx, modelYaw(now), pt, p);
        }

        // 漂浮微粒：8 个 1 像素的赤毒点，在模型周围往上飘、淡入淡出
        GuiShapes.begin(g);
        float boxW = w * 0.22F;
        float boxH = h * 0.6F;
        float top = feet - boxH;
        for (int i = 0; i < 8; i++) {
            float life = 2000.0F + 2000.0F * hash(i, 7);
            float phase = hash(i, 3) * life;
            float tt = now + phase;
            int cycle = (int) (tt / life);
            float u = (tt - cycle * life) / life;
            float x = cx + (hash(i, cycle * 2 + 11) - 0.5F) * boxW;
            float y = top + boxH * (0.15F + 0.85F * hash(i, cycle * 2 + 12)) - u * (life / 1000.0F) * 0.03F * h;
            float a = (0.3F + 0.4F * hash(i, cycle + 5)) * Mth.sin(u * Mth.PI) * eFrame;
            if (a > 0.01F) {
                GuiShapes.rect(g, x, y, x + lw, y + lw, GuiShapes.argb(FxPalette.KUVA, a));
            }
        }
        GuiShapes.end();
    }

    /** 模型此刻朝向：拖动中跟手；松手后 1.5 s 缓回默认朝向；其余时间在默认朝向附近慢摆 */
    private float modelYaw(long now) {
        float idle = DEFAULT_YAW + SWAY_DEG * Mth.sin((float) (now % (long) SWAY_PERIOD_MS) / SWAY_PERIOD_MS * Mth.TWO_PI);
        if (dragging) {
            return yawNow;
        }
        long since = now - returnStartClock;
        if (since >= 0 && since < YAW_RETURN_MS) {
            float k = FxCurve.easeOutCubic(since / (float) YAW_RETURN_MS);
            yawNow = returnFromYaw + (idle - returnFromYaw) * k;
            return yawNow;
        }
        yawNow = idle;
        return idle;
    }

    /** 0~1 的伪随机数，只由两个整数决定（微粒用，免得每帧建随机数对象） */
    private static float hash(int a, int b) {
        int x = a * 0x9E3779B1 + b * 0x85EBCA77;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        x *= 0x297A2D39;
        x ^= x >>> 15;
        return (x >>> 8) / 16777216.0F;
    }

    // ---------- 左栏 ----------

    private void drawTitle(GuiGraphics g, Font f, float x0, float x1, int h, float a, float lw) {
        if (a <= 0.01F) {
            return;
        }
        float ts = tsOf(h, 0.018F) * 1.2F;
        text(g, f, title.getString(), x0, h * 0.060F, ts, FxPalette.TEXT_MID, a, 1.0F, -1);
        GuiShapes.begin(g);
        GuiShapes.hairline(g, x0, h * 0.087F, x1, h * 0.087F, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * a));
        GuiShapes.end();
    }

    private void drawLeft(GuiGraphics g, Font f, int mx, int my, float x0, float x1, int w, int h, long now,
                          LichStatePacket st, boolean lich, @Nullable LichArchivePacket.Data data, float a,
                          boolean small, float lw) {
        if (a <= 0.01F) {
            return;
        }
        float cx = (x0 + x1) * 0.5F;
        float colW = x1 - x0;
        float tsS = tsOf(h, 0.016F);
        float tsN = tsOf(h, 0.026F);
        float tsR = tsOf(h, 0.032F);
        boolean known = demo != null || data != null || ClientLichState.get() != ClientLichState.NONE;

        // 等级徽章
        float by = h * 0.195F;
        float ba = h * 0.040F;
        float bhw = ba * 0.75F;
        GuiShapes.begin(g);
        GuiShapes.diamondFill(g, cx, by, bhw, ba, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F * a));
        GuiShapes.diamondOutline(g, cx, by, bhw, ba, lw, GuiShapes.argb(FxPalette.KUVA_PALE, a));
        int vein = GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * a);
        GuiShapes.hairline(g, cx - bhw, by, cx - bhw * 0.6F, by, lw, vein);
        GuiShapes.hairline(g, cx + bhw, by, cx + bhw * 0.6F, by, lw, vein);
        GuiShapes.hairline(g, cx, by - ba, cx, by - ba * 0.5F, lw, vein);
        GuiShapes.end();
        String rank = !known ? unknown() : lich ? Integer.toString(st.rank) : "—";
        float rs = rank.length() >= 3 ? tsR * 0.8F : tsR;
        text(g, f, rank, cx, by - 3.5F * rs, rs, FxPalette.BONE, a, 0.0F, 0);
        if (lich && inRect(mx, my, cx - bhw, by - ba, cx + bhw, by + ba)) {
            hover(Component.translatable("kuvalich.archive.rank.tooltip", st.rank));
        }

        // 名字、第几只
        float ny = h * 0.278F;
        if (!known) {
            text(g, f, unknown(), cx, ny - 3.5F * tsN, tsN, FxPalette.KUVA, a, 1.0F, 0);
        } else if (lich) {
            String name = upper(st.name);
            float nw = SpacedText.width(f, name, 1.0F) * tsN;
            float s = nw > colW ? tsN * colW / nw : tsN;
            text(g, f, name, cx, ny - 3.5F * s, s, FxPalette.KUVA, a, 1.0F, 0);
            String gen = I18n.get("kuvalich.archive.generation", Math.max(1, st.generation));
            text(g, f, gen, cx, h * 0.305F - 3.5F * tsS, tsS, FxPalette.ASH, a, 0.0F, 0);
        } else {
            text(g, f, I18n.get("kuvalich.lich.state.unborn"), cx, ny - 3.5F * tsN, tsN, FxPalette.TEXT_MID, a, 1.0F, 0);
        }

        if (lich) {
            // 能力图标
            if (!small) {
                drawAbilities(g, mx, my, cx, h * 0.345F, h, data, a, lw);
            }
            drawRage(g, f, mx, my, x0, x1, h, now, st, data, a, tsS, lw);
            drawAttributes(g, f, x0, x1, h, data, a, tsS);
        }
        drawConfiscated(g, f, mx, my, x0, x1, h, data, known, a, tsS);
    }

    private void drawAbilities(GuiGraphics g, int mx, int my, float cx, float cy, int h,
                               @Nullable LichArchivePacket.Data data, float a, float lw) {
        boolean fury = data != null && data.furyPermille() > 0;
        int count = fury ? 4 : 3;
        float step = h * 0.045F;
        float size = h * 0.030F;
        float x = cx - step * (count - 1) * 0.5F;
        GuiShapes.begin(g);
        int base = GuiShapes.argb(FxPalette.KUVA_MIST, 0.6F * a);
        int line = GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * a);
        int hovered = -1;
        for (int i = 0; i < 4; i++) {
            if (i == 1 && !fury) {
                continue;
            }
            float r = size * 0.5F;
            GuiShapes.diamondFill(g, x, cy, r * 0.75F, r, base);
            ArchiveIcons.ability(g, i, x, cy, size * 0.62F, lw, line);
            if (inRect(mx, my, x - r, cy - r, x + r, cy + r)) {
                hovered = i;
            }
            x += step;
        }
        GuiShapes.end();
        if (hovered >= 0) {
            hover(Component.translatable(ABILITY_KEYS[hovered]));
        }
    }

    private void drawRage(GuiGraphics g, Font f, int mx, int my, float x0, float x1, int h, long now,
                          LichStatePacket st, @Nullable LichArchivePacket.Data data, float a, float tsS, float lw) {
        float ly = h * 0.372F;
        text(g, f, I18n.get("kuvalich.archive.rage"), x0, ly, tsS, FxPalette.ASH, a, 0.0F, -1);
        int tier = Mth.clamp(st.tier, 0, RageTier.values().length - 1);
        text(g, f, tier + " / " + (RageTier.values().length - 1), x1, ly, tsS, FxPalette.ASH, 0.6F * a, 0.0F, 1);

        float y0 = h * 0.395F;
        float y1 = h * 0.425F;
        float rage = Mth.clamp(st.ragePermille / 1000.0F, 0.0F, 1.0F);
        float fillA = 0.95F;
        if (st.state == LichState.INCOMING) {
            float k = 0.5F + 0.5F * Mth.sin((now % 1600L) / 1600.0F * Mth.TWO_PI);
            fillA = 0.8F + 0.2F * k;
        }
        GuiShapes.begin(g);
        GuiShapes.rect(g, x0, y0, x1, y1, GuiShapes.argb(FxPalette.KUVA_MIST, 0.5F * a));
        if (rage > 0.0F) {
            GuiShapes.rect(g, x0, y0, x0 + (x1 - x0) * rage, y1, GuiShapes.argb(FxPalette.KUVA_DEEP, fillA * a));
        }
        int tick = GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * a);
        for (float rt : RAGE_TICKS) {
            float x = x0 + (x1 - x0) * rt;
            GuiShapes.hairline(g, x, y0, x, y1, lw, tick);
        }
        int frame = GuiShapes.argb(FxPalette.KUVA_PALE, 0.6F * a);
        GuiShapes.hairline(g, x0, y0, x1, y0, lw, frame);
        GuiShapes.hairline(g, x0, y1, x1, y1, lw, frame);
        GuiShapes.hairline(g, x0, y0, x0, y1, lw, frame);
        GuiShapes.hairline(g, x1, y0, x1, y1, lw, frame);
        GuiShapes.end();
        String word = I18n.get(RageTier.values()[tier].langKey());
        text(g, f, word, (x0 + x1) * 0.5F, (y0 + y1) * 0.5F - 3.5F * tsS, tsS, FxPalette.BONE, a, 0.0F, 0);

        if (inRect(mx, my, x0, y0 - h * 0.02F, x1, y1)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("kuvalich.archive.rage.tooltip.head"));
            int src = data != null ? data.rageSources() : 0;
            if (src != 0) {
                lines.add(Component.translatable("kuvalich.archive.rage.tooltip.sources"));
                String[] keys = {"thrall", "progress", "reveal", "time"};
                for (int i = 0; i < 4; i++) {
                    if ((src & (1 << i)) != 0) {
                        lines.add(Component.literal("· ").append(Component.translatable("kuvalich.archive.rage.src." + keys[i])));
                    }
                }
            }
            hoverLines = lines;
        }
    }

    private void drawAttributes(GuiGraphics g, Font f, float x0, float x1, int h,
                                @Nullable LichArchivePacket.Data data, float a, float tsS) {
        float y = h * 0.448F;
        if (data == null) {
            text(g, f, unknown(), x0, y, tsS, FxPalette.ASH, a, 0.0F, -1);
            return;
        }
        buildRows(data);
        // 行多了（弱点、抗性逐类各占一行）就压行距，字也跟着缩，不能压到下面「被它拿走的东西」
        int n = rowLabels.size();
        float row = n == 0 ? h * 0.025F : Math.min(h * 0.025F, (h * 0.690F - y) / n);
        float ts = Math.min(tsS, row / 8.0F);
        float colW = x1 - x0;
        for (int i = 0; i < n; i++) {
            String label = rowLabels.get(i);
            String value = rowValues.get(i);
            float lwid = label.isEmpty() ? 0.0F : f.width(label) * ts + 4.0F;
            text(g, f, label, x0, y, ts, FxPalette.ASH, a, 0.0F, -1);
            float vw = f.width(value) * ts;
            float room = colW - lwid;
            float s = vw > room && vw > 0.0F ? ts * room / vw : ts;
            text(g, f, value, x1, y + (ts - s) * 3.5F, s, FxPalette.TEXT_MID, a, 0.0F, 1);
            y += row;
        }
    }

    /** 属性表：只列它有的行；数据或语言变了才重算 */
    private void buildRows(LichArchivePacket.Data d) {
        String lang = Minecraft.getInstance().options.languageCode;
        if (d == rowsFor && lang.equals(rowsLang)) {
            return;
        }
        rowsFor = d;
        rowsLang = lang;
        rowLabels.clear();
        rowValues.clear();
        int weak = d.affinity() & 0xF;
        int resist = (d.affinity() >> 4) & 0xF;
        addMaskRows("kuvalich.archive.attr.weakness", weak, WEAK_MULT);
        addMaskRows("kuvalich.archive.attr.resistance", resist, RESIST_MULT);
        if (d.adaptPct() > 0 && d.adaptMax() > 0) {
            addRow("kuvalich.archive.attr.adaptive",
                    I18n.get("kuvalich.archive.val.adaptive", d.adaptPct(), Math.max(1, d.adaptMax() / d.adaptPct())));
        }
        if (!d.weaponStack().isEmpty()) {
            addRow("kuvalich.archive.attr.weapon", d.weaponStack().getHoverName().getString());
            if (d.weaponLvMax() > 0) {
                rowLabels.add("");
                rowValues.add(I18n.get("kuvalich.archive.val.weapon_levels", d.weaponLvMin(), d.weaponLvMax()));
            }
        }
        if (!d.birthBiome().isEmpty() || !d.birthDim().isEmpty()) {
            addRow("kuvalich.archive.attr.birthplace",
                    I18n.get("kuvalich.archive.val.birthplace", biomeName(d.birthBiome()), dimName(d.birthDim())));
        }
        if (d.furyPermille() > 0) {
            addRow("kuvalich.archive.attr.fury", I18n.get("kuvalich.archive.val.fury", pct1(d.furyPermille())));
        }
        if (d.dealtPct() > 0) {
            addRow("kuvalich.archive.attr.dealt", I18n.get("kuvalich.archive.val.minus", d.dealtPct()));
        }
        if (d.takenPct() > 0) {
            addRow("kuvalich.archive.attr.taken", I18n.get("kuvalich.archive.val.plus", d.takenPct()));
        }
        addRow("kuvalich.archive.attr.encounters", I18n.get("kuvalich.archive.val.encounters", d.encounters()));
    }

    /** 位掩码按「近战 / 弓箭 / 其他弹射物 / 魔法」的顺序逐类列一行，标签只写在第一行 */
    private void addMaskRows(String labelKey, int mask, String mult) {
        boolean first = true;
        for (int c = 0; c < CATEGORY_KEYS.length; c++) {
            if ((mask & (1 << c)) == 0) {
                continue;
            }
            rowLabels.add(first ? I18n.get(labelKey) : "");
            rowValues.add(I18n.get("kuvalich.archive.val.mult", I18n.get(CATEGORY_KEYS[c]), mult));
            first = false;
        }
    }

    private void addRow(String labelKey, String value) {
        rowLabels.add(I18n.get(labelKey));
        rowValues.add(value);
    }

    /** 千分比写成百分比，整数不带小数（75 → 7.5，100 → 10） */
    private static String pct1(int permille) {
        return permille % 10 == 0 ? Integer.toString(permille / 10)
                : String.format(Locale.ROOT, "%.1f", permille / 10.0F);
    }

    private static String biomeName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        String key = "biome." + rl.getNamespace() + "." + rl.getPath();
        return I18n.exists(key) ? I18n.get(key) : rl.getPath();
    }

    private static String dimName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        String key = "kuvalich.dim." + rl.getNamespace() + "." + rl.getPath();
        return I18n.exists(key) ? I18n.get(key) : rl.getPath();
    }

    private void drawConfiscated(GuiGraphics g, Font f, int mx, int my, float x0, float x1, int h,
                                 @Nullable LichArchivePacket.Data data, boolean known, float a, float tsS) {
        float ly = h * 0.700F;
        if (data == null) {
            if (known) {
                text(g, f, I18n.get("kuvalich.archive.confiscated", unknown()), x0, ly, tsS, FxPalette.ASH, a, 0.0F, -1);
            }
            return;
        }
        int total = data.confiscatedCount();
        text(g, f, I18n.get("kuvalich.archive.confiscated", total), x0, ly, tsS, FxPalette.ASH, a, 0.0F, -1);
        float y = h * 0.725F;
        List<ItemStack> items = data.confiscated();
        if (total <= 0 || items.isEmpty()) {
            text(g, f, I18n.get("kuvalich.archive.confiscated.none"), x0, y, tsS, FxPalette.TEXT_MID, 0.7F * a, 0.0F, -1);
            return;
        }
        if (a < 0.5F) {
            // 物品图标没法跟着半透明，等左栏亮到一半再一起出来
            return;
        }
        float cell = h * 0.044F;
        int perRow = Math.max(1, (int) ((x1 - x0 + 1.0F) / (cell + 1.0F)));
        int slots = perRow * 2;
        int shown = Math.min(items.size(), total > slots ? slots - 1 : slots);
        float scale = cell / 16.0F;
        PoseStack ps = g.pose();
        for (int i = 0; i < shown; i++) {
            float cx = x0 + (i % perRow) * (cell + 1.0F);
            float cy = y + (i / perRow) * (cell + 1.0F);
            ps.pushPose();
            ps.translate(cx, cy, 0.0F);
            ps.scale(scale, scale, 1.0F);
            g.renderItem(items.get(i), 0, 0);
            ps.popPose();
            if (inRect(mx, my, cx, cy, cx + cell, cy + cell)) {
                hoverStack = items.get(i);
            }
        }
        if (total > shown) {
            float cx = x0 + (shown % perRow) * (cell + 1.0F);
            float cy = y + (shown / perRow) * (cell + 1.0F);
            String more = I18n.get("kuvalich.archive.confiscated.more", total - shown);
            text(g, f, more, cx + cell * 0.5F, cy + cell * 0.5F - 3.5F * tsS, tsS, FxPalette.ASH, a, 0.0F, 0);
        }
    }

    // ---------- 右栏 ----------

    private void drawRight(GuiGraphics g, Font f, int mx, int my, float x0, float x1, int w, int h, long now,
                           LichStatePacket st, @Nullable LichArchivePacket.Data data, float a, float lw) {
        if (a <= 0.01F) {
            return;
        }
        float span = x1 - x0;
        // 原排版里右栏宽 0.155W，三列中心在 0.800 / 0.850 / 0.900W，折成栏内比例，小窗口时照样排
        float[] col = {x0 + span * 0.161F, x0 + span * 0.484F, x0 + span * 0.806F};
        float tsS = tsOf(h, 0.016F);
        float sigil = w * 0.026F;
        boolean small = small();
        if (small) {
            sigil = Math.min(sigil * 1.6F, span * 0.18F);
        }

        // 当前安魂卡
        text(g, f, I18n.get("kuvalich.archive.cards"), x0, h * 0.085F, tsS, FxPalette.ASH, a, 0.0F, -1);
        float cardY = Math.max(h * 0.140F, h * 0.085F + 9.0F * tsS + sigil * 0.5F + Math.max(2.0F, sigil * 0.3F));
        if (data == null) {
            text(g, f, unknown(), x0, cardY - 3.5F * tsS, tsS, FxPalette.ASH, a, 0.0F, -1);
        } else if (allEmpty(data.cards())) {
            text(g, f, I18n.get("kuvalich.archive.cards.none"), x0, cardY - 3.5F * tsS, tsS, FxPalette.TEXT_MID,
                    0.7F * a, 0.0F, -1);
        } else {
            drawCards(g, mx, my, col, cardY, w, h, data.cards(), sigil, a, lw);
        }

        // 处决记录：第一行从标题底下排起，符号放大（小窗口）时跟着往下让，不压标题
        float attemptsY = h * 0.215F;
        text(g, f, I18n.get("kuvalich.archive.attempts"), x0, attemptsY, tsS, FxPalette.ASH, a, 0.0F, -1);
        float rowY0 = attemptsY + 9.0F * tsS + sigil * 0.5F + Math.max(2.0F, sigil * 0.3F);
        if (data == null) {
            text(g, f, unknown(), x0, rowY0 - 3.5F * tsS, tsS, FxPalette.ASH, a, 0.0F, -1);
        } else if (data.attempts().length == 0) {
            text(g, f, I18n.get("kuvalich.archive.attempts.none"), x0, rowY0 - 3.5F * tsS, tsS,
                    FxPalette.TEXT_MID, 0.7F * a, 0.0F, -1);
        } else {
            drawAttempts(g, f, mx, my, x0, x1, col, h, rowY0, data.attempts(), sigil, a, tsS, lw);
        }

        // 已揭示的密语
        float my0 = h * 0.642F;
        text(g, f, I18n.get("kuvalich.archive.murmurs"), x0, my0, tsS, FxPalette.ASH, a, 0.0F, -1);
        GuiShapes.begin(g);
        float hlY = my0 + 9.0F * tsS + 1.0F;
        GuiShapes.hairline(g, x0, hlY, x0 + Math.min(span, w * 0.15F), hlY, lw,
                GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * a), 0);
        GuiShapes.end();
        if (inRect(mx, my, x0, my0, x0 + span, hlY + 2.0F)) {
            hover(Component.translatable("kuvalich.archive.murmur.order"));
        }
        int[] riddles = data != null ? data.riddles() : null;
        float stageProgress = 0.0F;
        int progress = 0;
        int threshold = 0;
        if (data != null) {
            progress = data.progress();
            threshold = data.threshold();
            stageProgress = threshold < 0 ? 1.0F : threshold == 0 ? 0.0F : Mth.clamp(progress / (float) threshold, 0.0F, 1.0F);
        }
        float size = w * 0.035F;
        if (small) {
            size = Math.min(size * 1.4F, span * 0.2F);
        }
        float tx0 = x0 + span * 0.161F;
        float tx1 = x0 + span * 0.477F;
        float tx2 = x0 + span * 0.790F;
        triangles.update(riddles, now);
        int hovered = triangles.render(g, tx0, h * 0.753F, tx1, h * 0.709F, tx2, h * 0.752F, size, stageProgress, now,
                a, lw, mx, my);
        if (hovered >= 0 && data != null && riddles != null && riddles[hovered] < 0) {
            hover(Component.translatable("kuvalich.archive.murmur.unknown", progress,
                    threshold < 0 ? progress : threshold));
        }
    }

    private static boolean allEmpty(LichArchivePacket.Card[] cards) {
        for (LichArchivePacket.Card c : cards) {
            if (c != null && c.id() >= 0) {
                return false;
            }
        }
        return true;
    }

    private void drawCards(GuiGraphics g, int mx, int my, float[] col, float cy, int w, int h,
                           LichArchivePacket.Card[] cards, float sigil, float a, float lw) {
        float fw = Math.max(w * 0.034F, RequiemSigils.width(sigil) + 3.0F);
        float fh = Math.max(h * 0.060F, sigil + 3.0F);
        float dotY = cy + h * 0.040F;
        float dotStep = h * 0.016F;
        float dot = Math.max(lw * 1.5F, 1.0F);
        for (int i = 0; i < 3; i++) {
            LichArchivePacket.Card c = i < cards.length ? cards[i] : LichArchivePacket.Card.EMPTY;
            float x = col[i];
            float l = x - fw * 0.5F;
            float r = x + fw * 0.5F;
            float t = cy - fh * 0.5F;
            float b = cy + fh * 0.5F;
            GuiShapes.begin(g);
            if (c == null || c.id() < 0) {
                dashedRect(g, l, t, r, b, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * a));
                GuiShapes.end();
                continue;
            }
            GuiShapes.rect(g, l, t, r, b, GuiShapes.argb(FxPalette.INK, 0.85F * a));
            frameRect(g, l, t, r, b, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F * a));
            int max = Math.min(3, Math.max(0, c.maxDamage()));
            int left = Math.max(0, Math.min(max, c.maxDamage() - c.damage()));
            for (int k = 0; k < max; k++) {
                float dx = x + (k - (max - 1) * 0.5F) * dotStep;
                if (k < left) {
                    GuiShapes.rect(g, dx - dot - lw, dotY - dot - lw, dx + dot + lw, dotY + dot + lw,
                            GuiShapes.argb(FxPalette.KUVA_DEEP, 0.8F * a));
                    GuiShapes.rect(g, dx - dot, dotY - dot, dx + dot, dotY + dot, GuiShapes.argb(FxPalette.KUVA, 0.95F * a));
                } else {
                    GuiShapes.rect(g, dx - dot, dotY - dot, dx + dot, dotY + dot, GuiShapes.argb(FxPalette.ASH, 0.3F * a));
                }
            }
            GuiShapes.end();
            RequiemSigils.drawCard(g, c.id(), x, cy, sigil, a);
            if (inRect(mx, my, l, t, r, dotY + dot + 2.0F)) {
                hover(Component.translatable("kuvalich.archive.cards.uses", left));
            }
        }
    }

    private void drawAttempts(GuiGraphics g, Font f, int mx, int my, float x0, float x1, float[] col, int h,
                              float y0, int[] attempts, float sigil, float a, float tsS, float lw) {
        int rows = Math.min(RequiemCard.MAX_ATTEMPTS, attempts.length);
        float rowH = h * 0.067F;
        float span = x1 - x0;
        float markX = x0 + span * 0.968F;
        // 行号右对齐在第一列符号左边，留一点空
        float numX = col[0] - RequiemSigils.width(sigil) * 0.5F - Math.max(2.0F, sigil * 0.2F);
        for (int i = 0; i < rows; i++) {
            int packed = attempts[i];
            float cy = y0 + i * rowH;
            text(g, f, Integer.toString(i + 1), numX, cy - 3.5F * tsS, tsS, FxPalette.ASH, 0.7F * a, 0.0F, 1);
            if (i > 0) {
                GuiShapes.begin(g);
                GuiShapes.hairline(g, x0, cy - rowH * 0.5F, x1, cy - rowH * 0.5F, lw,
                        GuiShapes.argb(FxPalette.KUVA_PALE, 0.12F * a));
                GuiShapes.end();
            }
            for (int pos = 0; pos < 3; pos++) {
                int id = RequiemCard.attemptCardId(packed, pos);
                int result = RequiemCard.attemptResult(packed, pos);
                float x = col[pos];
                switch (result) {
                    case RequiemCard.ATTEMPT_RIGHT -> RequiemSigils.drawLayers(g, id, x, cy, sigil, 0, 0,
                            GuiShapes.argb(0xFFFFFF, 0.4F * a), GuiShapes.argb(FxPalette.KUVA_PALE, a));
                    case RequiemCard.ATTEMPT_WRONG -> {
                        // 暗红底 + 赤毒色线：线和底同色时整块糊成一片红，看不出是哪个符号
                        RequiemSigils.drawLayers(g, id, x, cy, sigil, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F * a), 0, 0,
                                GuiShapes.argb(FxPalette.KUVA, 0.9F * a));
                        // 斜杠：左下 → 右上，长 = 符号对角线的 0.8 倍
                        float hx = RequiemSigils.width(sigil) * 0.4F;
                        float hy = sigil * 0.4F;
                        GuiShapes.begin(g);
                        GuiShapes.hairline(g, x - hx, cy + hy, x + hx, cy - hy, lw * 1.5F, GuiShapes.argb(FxPalette.KUVA, a));
                        GuiShapes.end();
                    }
                    default -> RequiemSigils.drawLayers(g, id, x, cy, sigil, 0, 0, 0,
                            GuiShapes.argb(FxPalette.ASH, 0.5F * a));
                }
                float hw = RequiemSigils.width(sigil) * 0.5F;
                if (inRect(mx, my, x - hw, cy - sigil * 0.5F, x + hw, cy + sigil * 0.5F)) {
                    String key = result == RequiemCard.ATTEMPT_RIGHT ? "kuvalich.archive.attempt.correct"
                            : result == RequiemCard.ATTEMPT_WRONG ? "kuvalich.archive.attempt.wrong"
                            : "kuvalich.archive.attempt.untried";
                    hover(Component.translatable(key, pos + 1));
                }
            }
            boolean mercy = RequiemCard.attemptIsMercy(packed);
            float mh = Math.max(2.0F, sigil * 0.22F);
            GuiShapes.begin(g);
            if (mercy) {
                GuiShapes.diamondFill(g, markX, cy, mh * 0.7F, mh, GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * a));
            } else {
                GuiShapes.diamondOutline(g, markX, cy, mh * 0.7F, mh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * a));
            }
            GuiShapes.end();
            if (inRect(mx, my, markX - mh - 1.0F, cy - mh - 1.0F, markX + mh + 1.0F, cy + mh + 1.0F)) {
                hover(Component.translatable(mercy ? "kuvalich.archive.attempts.mercy" : "kuvalich.archive.attempts.direct"));
            }
        }
    }

    private static void frameRect(GuiGraphics g, float l, float t, float r, float b, float lw, int argb) {
        GuiShapes.hairline(g, l, t, r, t, lw, argb);
        GuiShapes.hairline(g, l, b, r, b, lw, argb);
        GuiShapes.hairline(g, l, t, l, b, lw, argb);
        GuiShapes.hairline(g, r, t, r, b, lw, argb);
    }

    /** 虚线框（空卡槽）：每段 2、间隔 2 */
    private static void dashedRect(GuiGraphics g, float l, float t, float r, float b, float lw, int argb) {
        dashed(g, l, t, r, t, lw, argb);
        dashed(g, l, b, r, b, lw, argb);
        dashed(g, l, t, l, b, lw, argb);
        dashed(g, r, t, r, b, lw, argb);
    }

    private static void dashed(GuiGraphics g, float x0, float y0, float x1, float y1, float lw, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len <= 0.0F) {
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        for (float s = 0.0F; s < len; s += 4.0F) {
            float e = Math.min(len, s + 2.0F);
            GuiShapes.hairline(g, x0 + ux * s, y0 + uy * s, x0 + ux * e, y0 + uy * e, lw, argb);
        }
    }

    // ---------- 状态行 ----------

    private void drawStatus(GuiGraphics g, Font f, int w, int h, LichStatePacket st,
                            @Nullable LichArchivePacket.Data data, boolean lich, boolean small, float a, long now) {
        if (a <= 0.01F) {
            return;
        }
        float tsB = tsOf(h, 0.018F);
        float tsS = tsOf(h, 0.016F);
        float cx = w * 0.5F;
        float sy = (small ? h * 0.93F : h * 0.920F) - 3.5F * tsB;
        boolean known = demo != null || data != null || ClientLichState.get() != ClientLichState.NONE;
        if (!known) {
            text(g, f, unknown(), cx, sy, tsB, FxPalette.TEXT_MID, a, 0.0F, 0);
        } else if (st.state == LichState.UNBORN) {
            text(g, f, I18n.get("kuvalich.archive.status.unborn"), cx, sy, tsB, FxPalette.TEXT_MID, a, 0.0F, 0);
            text(g, f, I18n.get("kuvalich.archive.status.unborn.hint"), cx, sy + 9.0F * tsB + 1.0F, tsS,
                    FxPalette.ASH, a, 0.0F, 0);
            String lastName = data != null ? data.lastName() : "";
            int fate = data != null ? data.lastFate() : 0;
            if (!lastName.isEmpty() && fate > 0) {
                String key = fate == 2 ? "kuvalich.archive.history.reset" : "kuvalich.archive.history.vanquished";
                text(g, f, I18n.get(key, upper(lastName)), cx, h * 0.890F - 3.5F * tsS - (small ? h * 0.04F : 0.0F),
                        tsS, FxPalette.ASH, a, 0.0F, 0);
            }
        } else {
            String line = statusLine(st);
            int col = switch (st.state) {
                case INCOMING, ARRIVING, PRESENT, DOWNED -> FxPalette.KUVA;
                default -> FxPalette.TEXT_MID;
            };
            text(g, f, line, cx, sy, tsB, col, a, 0.0F, 0);
        }
        // 关闭提示
        String close = I18n.get("kuvalich.archive.close", KuvaKeyMappings.ARCHIVE.getTranslatedKeyMessage().getString());
        text(g, f, close, w * 0.930F, h * 0.955F - 3.5F * tsS, tsS, FxPalette.ASH, a, 0.0F, 1);
    }

    private String statusLine(LichStatePacket st) {
        String s = switch (st.state) {
            case STALKING, DEPARTING -> I18n.get("kuvalich.archive.status.stalking",
                    I18n.get(RageTier.values()[Mth.clamp(st.tier, 0, RageTier.values().length - 1)].langKey()));
            case COOLDOWN -> I18n.get("kuvalich.archive.status.cooldown", Math.max(1, (st.cooldownSec + 59) / 60));
            case INCOMING -> switch (st.waitReason) {
                case DIMENSION -> I18n.get("kuvalich.archive.status.wait.dimension");
                case PEACEFUL -> I18n.get("kuvalich.archive.status.wait.peaceful");
                case CREATIVE -> I18n.get("kuvalich.archive.status.wait.creative");
                case BUSY, GRACE -> I18n.get("kuvalich.archive.status.wait.busy");
                default -> I18n.get("kuvalich.archive.status.incoming");
            };
            case ARRIVING, PRESENT -> I18n.get("kuvalich.archive.status.present");
            case DOWNED -> I18n.get("kuvalich.archive.status.downed");
            default -> I18n.get("kuvalich.archive.status.unborn");
        };
        if (st.paused) {
            s = s + " · " + I18n.get("kuvalich.archive.status.paused");
        }
        return s;
    }

    // ---------- 字幕 ----------

    private void drawSubtitle(GuiGraphics g, Font f, int w, int h, long now, LichStatePacket st) {
        if (!talkPlanned || now < subtitleStartClock || now >= subtitleEndClock + SUBTITLE_FADE_MS) {
            if (talkPlanned && now >= subtitleEndClock + SUBTITLE_FADE_MS) {
                talkPlanned = false;
            }
            return;
        }
        float ts = tsOf(h, 0.018F);
        if (subtitleLines == null) {
            int maxW = Math.max(40, Math.round(w * 0.40F / ts));
            List<FormattedCharSequence> lines = CommText.wrap(f, pendingRaw, maxW, st.name);
            subtitleLines = lines.size() > 2 ? new ArrayList<>(lines.subList(0, 2)) : lines;
            subtitleWidth = 0;
            for (FormattedCharSequence l : subtitleLines) {
                subtitleWidth = Math.max(subtitleWidth, f.width(l));
            }
        }
        if (!voiceStarted) {
            voiceStarted = true;
            voice = LichVoice.speak(talkSeed, pendingPlain, subtitleStartClock, null, true,
                    EffectsDisplayConfig.lichMutter());
        }
        float alpha = now < subtitleEndClock ? 1.0F
                : 1.0F - FxCurve.clamp01((now - subtitleEndClock) / (float) SUBTITLE_FADE_MS);
        float progress = CommText.progressFor(now - subtitleStartClock, wipeMs);
        float lineH = 10.0F;
        float blockW = subtitleWidth * ts;
        float x = w * 0.5F - blockW * 0.5F;
        float y = h * 0.835F - subtitleLines.size() * lineH * ts * 0.5F;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0.0F);
        ps.scale(ts, ts, 1.0F);
        CommText.drawWipe(g, f, subtitleLines, 0, 0, Math.round(lineH), GuiShapes.argb(CommText.SUBTITLE_RGB, alpha),
                progress);
        ps.popPose();
        // 字幕上方的全大写名牌，右对齐到字幕右缘
        float tsS = tsOf(h, 0.016F);
        text(g, f, upper(st.name), x + blockW, y - h * 0.022F, tsS, FxPalette.KUVA, alpha, 1.0F, 1);
    }

    // ==================== 小工具 ====================

    /**
     * 画一行字（{@code align}：-1 左对齐、0 居中、1 右对齐，相对 x），透明度太低时不画
     * （原版字体把 α 低于 4 的颜色当成不透明）
     */
    private float text(GuiGraphics g, Font f, String s, float x, float y, float scale, int rgb, float alpha,
                       float spacing, int align) {
        if (alpha * 255.0F < 5.0F || s.isEmpty()) {
            return 0.0F;
        }
        float tw = (spacing == 0.0F ? f.width(s) : SpacedText.width(f, s, spacing)) * scale;
        float left = align < 0 ? x : align == 0 ? x - tw * 0.5F : x - tw;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(left, y, 0.0F);
        ps.scale(scale, scale, 1.0F);
        SpacedText.draw(g, f, s, 0.0F, 0.0F, spacing, GuiShapes.argb(rgb, alpha), false);
        ps.popPose();
        return tw;
    }

    private void hover(Component line) {
        hoverLines = List.of(line);
    }

    private static boolean inRect(double mx, double my, float l, float t, float r, float b) {
        return mx >= l && mx <= r && my >= t && my <= b;
    }

    private static String unknown() {
        return I18n.get("kuvalich.archive.unknown");
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    // ==================== 能力图标 ====================

    /** 档案左栏四枚能力图标的线描；点存在一个复用的数组里，不分配 */
    private static final class ArchiveIcons {

        private static final float[] PTS = new float[64];

        private ArchiveIcons() {
        }

        /**
         * @param kind 0 适应（六边形）/ 1 交战增幅（两道向上的折线）/ 2 自愈（水滴）/ 3 震荡（三道同心短弧）
         * @param size 图标大小
         */
        static void ability(GuiGraphics g, int kind, float cx, float cy, float size, float lw, int argb) {
            float r = size * 0.5F;
            switch (kind) {
                case 0 -> {
                    for (int i = 0; i < 6; i++) {
                        double ang = Math.toRadians(90.0D + 60.0D * i);
                        PTS[i * 2] = cx + (float) Math.cos(ang) * r;
                        PTS[i * 2 + 1] = cy - (float) Math.sin(ang) * r;
                    }
                    GuiShapes.polyline(g, PTS, 6, true, lw, argb);
                }
                case 1 -> {
                    for (int k = 0; k < 2; k++) {
                        float y = cy - r * 0.2F + k * r * 0.55F;
                        PTS[0] = cx - r * 0.75F;
                        PTS[1] = y + r * 0.35F;
                        PTS[2] = cx;
                        PTS[3] = y - r * 0.35F;
                        PTS[4] = cx + r * 0.75F;
                        PTS[5] = y + r * 0.35F;
                        GuiShapes.polyline(g, PTS, 3, false, lw, argb);
                    }
                }
                case 2 -> {
                    // 尖朝上的水滴：顶点 + 下半个圆
                    float rr = r * 0.58F;
                    float ccy = cy + r * 0.3F;
                    int n = 0;
                    PTS[n++] = cx;
                    PTS[n++] = cy - r;
                    int seg = 12;
                    for (int i = 0; i <= seg; i++) {
                        double ang = Math.toRadians(-30.0D - 240.0D * i / seg);
                        PTS[n++] = cx + (float) Math.cos(ang) * rr;
                        PTS[n++] = ccy - (float) Math.sin(ang) * rr;
                    }
                    GuiShapes.polyline(g, PTS, n / 2, true, lw, argb);
                }
                default -> {
                    float oy = cy + r * 0.65F;
                    for (int k = 1; k <= 3; k++) {
                        float rad = r * 0.42F * k;
                        int n = 0;
                        for (int i = 0; i <= 6; i++) {
                            double ang = Math.toRadians(40.0D + 100.0D * i / 6.0D);
                            PTS[n++] = cx + (float) Math.cos(ang) * rad;
                            PTS[n++] = oy - (float) Math.sin(ang) * rad;
                        }
                        GuiShapes.polyline(g, PTS, n / 2, false, lw, argb);
                    }
                }
            }
        }
    }

    // ==================== 已揭示的密语（三角） ====================

    /**
     * 右栏下方的三枚：已揭示的画这张卡的符号（线描，不填色、不发光），未揭示的画赤毒菱晶。
     * 菱晶和解密 HUD、灭骸之扉是同一个画法（{@link MurmurTriangle#drawCrystal}）：正在解的那一枚外圈从上顶点
     * 顺时针点亮、液面同一比例，表示这一阶段的密语进度，后面还没轮到的是空的
     *
     * <p>位置和打开时的淡入跟着档案的版面走，所以换位、悬停在这里自己算，没有直接用 {@link MurmurTriangle} 的固定三角。</p>
     *
     * <p>每 3 秒（对齐 3 秒的整网格）随机挑两枚互换位置，五次里大约跳过一次；走 970 ms，前 730 ms 缓入缓出走完 95%，
     * 剩下的慢慢停住，两枚走弧线擦肩而过，一个往上、一个往下偏。某一格刚揭示时菱晶 300 ms 淡出、符号 750 ms 淡入。
     * 打开档案的那个不完整的周期不换，免得一打开就看到半截动作。</p>
     */
    private static final class ArchiveTriangles {

        private static final int CYCLE_MS = 3000;
        private static final int MOVE_MS = 970;
        private static final int EASE_MS = 730;
        private static final int REVEAL_OUT_MS = 300;
        private static final int REVEAL_IN_MS = 750;

        private final int[] riddles = {-1, -1, -1};
        private final long[] revealAt = {Long.MIN_VALUE / 4, Long.MIN_VALUE / 4, Long.MIN_VALUE / 4};
        private boolean primed;
        /** 第 i 枚现在在哪个位置（0 左 / 1 中 / 2 右） */
        private final int[] posOf = {0, 1, 2};
        private long cycle = Long.MIN_VALUE;
        private int swapA = -1;
        private int swapB = -1;
        private long swapStart;
        private final float[] bx = new float[3];
        private final float[] by = new float[3];

        void update(@Nullable int[] r, long now) {
            for (int i = 0; i < 3; i++) {
                int v = r == null || i >= r.length ? -1 : r[i];
                if (primed && riddles[i] < 0 && v >= 0) {
                    revealAt[i] = now;
                }
                riddles[i] = v;
            }
            if (r != null) {
                primed = true;
            }
            long c = Math.floorDiv(now, CYCLE_MS);
            if (c == cycle) {
                return;
            }
            boolean first = cycle == Long.MIN_VALUE;
            if (swapA >= 0) {
                int t = posOf[swapA];
                posOf[swapA] = posOf[swapB];
                posOf[swapB] = t;
            }
            swapA = -1;
            swapB = -1;
            cycle = c;
            if (first || hash((int) c, 91) < 0.2F) {
                return;
            }
            int pair = Math.min(2, (int) (hash((int) c, 92) * 3.0F));
            swapA = pair == 2 ? 0 : pair;
            swapB = pair == 0 ? 1 : 2;
            swapStart = c * CYCLE_MS;
        }

        /** @return 鼠标悬停的那一枚（第几条密语），没有为 -1 */
        int render(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float size,
                   float stageProgress, long now, float alpha, float lw, int mx, int my) {
            bx[0] = x0;
            by[0] = y0;
            bx[1] = x1;
            by[1] = y1;
            bx[2] = x2;
            by[2] = y2;
            int hovered = -1;
            int active = firstUnrevealed();
            for (int i = 0; i < 3; i++) {
                float x = bx[posOf[i]];
                float y = by[posOf[i]];
                if (i == swapA || i == swapB) {
                    int other = i == swapA ? swapB : swapA;
                    long t = now - swapStart;
                    float k = t >= MOVE_MS ? 1.0F : t <= 0 ? 0.0F : t < EASE_MS
                            ? 0.95F * easeInOutSine(t / (float) EASE_MS)
                            : 0.95F + 0.05F * FxCurve.easeOutCubic((t - EASE_MS) / (float) (MOVE_MS - EASE_MS));
                    float tx = bx[posOf[other]];
                    float ty = by[posOf[other]];
                    float bend = Mth.sin(k * Mth.PI) * size * 0.25F * (i == swapA ? -1.0F : 1.0F);
                    x = x + (tx - x) * k;
                    y = y + (ty - y) * k + bend;
                }
                drawOne(g, i, x, y, size, i == active ? stageProgress : 0.0F, now, alpha, lw);
                if (inRect(mx, my, x - size * 0.5F, y - size * 0.5F, x + size * 0.5F, y + size * 0.5F)) {
                    hovered = i;
                }
            }
            return hovered;
        }

        private void drawOne(GuiGraphics g, int i, float x, float y, float size, float p, long now, float alpha,
                             float lw) {
            int id = riddles[i];
            long since = now - revealAt[i];
            if (id < 0 || since < REVEAL_OUT_MS) {
                // 刚揭示的那一枚淡出时是满的（它这一阶段刚走完）
                float a = id < 0 ? alpha : alpha * (1.0F - since / (float) REVEAL_OUT_MS);
                if (a > 0.004F) {
                    MurmurTriangle.drawCrystal(g, x, y, size * 0.5F, id < 0 ? p : 1.0F, a, 0.0F, lw, now);
                }
                return;
            }
            float a = alpha * MurmurTriangle.easeInOutSine((since - REVEAL_OUT_MS) / (float) REVEAL_IN_MS);
            if (a > 0.004F) {
                RequiemSigils.draw(g, id, x, y, size, GuiShapes.argb(FxPalette.KUVA_PALE, a), 0);
            }
        }

        private int firstUnrevealed() {
            for (int i = 0; i < 3; i++) {
                if (riddles[i] < 0) {
                    return i;
                }
            }
            return -1;
        }

        private static float easeInOutSine(float t) {
            return MurmurTriangle.easeInOutSine(t);
        }
    }

    /**
     * 档案开着时整个 HUD 不画：底板只压暗到 85%，聊天栏的字、热栏会从底下透出来，正好叠在左栏和状态行上。
     * HUD 在界面之前画、中间清过深度，靠 z 挡不住，只能不画
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
    public static final class HideHud {
        private HideHud() {
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
            if (Minecraft.getInstance().screen instanceof LichArchiveScreen) {
                event.setCanceled(true);
            }
        }
    }
}
