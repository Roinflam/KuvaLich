package pers.roinflam.kuvalich.client.comm;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.lich.LichPuppet;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.LichLook;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;


/**
 * 通讯肖像：三位发言人的窗内画面，通讯窗和冒险指南共用
 *
 * <h3>怎么画</h3>
 * <p>每个窗 = 一张离屏缓冲（和窗在屏幕上的物理像素一样大）里画好的「窗内画面」（{@link CommPortraitArt}），
 * 再用合成着色器（{@link CommShaders}）贴到屏幕上：四边羽化、开收框、错色重影、水平错位带、溶解都在合成这一步做。
 * 缓冲统一在每帧开头（{@code RenderTickEvent} 的 START，世界和界面都还没开始画）画，画的是上一帧 {@link #draw} 登记过的窗；
 * 版面要到界面 render 里才知道，差一帧看不出来。这一刻外面没有攒着没上屏的界面几何，切换渲染目标、投影最干净，
 * 标题画面打开指南也走同一条路。</p>
 *
 * <h3>缓冲池</h3>
 * <p>按（发言人、外观、调用方给的框尺寸、是否静止态、朝向）认缓冲，一个窗从开到收用同一张，说话 / 擦出这些状态只记在快照里，
 * 状态一变也不会闪一帧空的。缓冲的像素尺寸按当前姿态矩阵折算，开收框时调用方压扁、拉宽窗不会换缓冲。
 * 最多 6 张，10 s 没人用就释放，窗口尺寸一变全部释放重建；一帧里最多 3 个玄骸肖像在渲模型，
 * 多出来的 {@link #draw} 返回 false，调用方画自己的徽记。Lotus / Ordis 的静止态画一次就不再重画。</p>
 *
 * <h3>两种调法</h3>
 * <p>通讯窗用十个参数的 {@link #draw(GuiGraphics, CommSpeaker, LichLook, int, int, int, int, float, boolean, float)}：
 * 开框、收起的形变它自己用姿态矩阵做，溶解、提亮、到场的青色故障在它画窗期间从 {@link CommHud#currentDrawFx()} 读。
 * 指南这类自己不排动画的用带 {@link Frame} 的那个，开框、收起、到场故障都由这里按时间做。</p>
 *
 * <h3>窗外不画就零开销</h3>
 * <p>没有窗登记时不建缓冲、不渲模型；合成着色器没建成时退回原版着色器 + 5×5 网格顶点羽化，
 * 重影、错位带、溶解都不画（玄骸收起改成 130 ms 淡出）。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CommPortraits {

    /** 友方开框：从中线上下展开 */
    public static final int OPEN_FRIEND_MS = 100;
    /** 玄骸开框：横向拖影三拍到位 */
    public static final int OPEN_LICH_MS = 130;
    /** 到场那一次的青色故障在开框后持续到这一刻 */
    public static final int ARRIVAL_GLITCH_END_MS = 330;
    /** 玄骸窗的红色微粒在开框后这么久才开始淡入 */
    public static final int PARTICLES_IN_MS = 400;
    /** 友方收起：压成一条横线 */
    public static final int CLOSE_FRIEND_MS = 130;
    /** 玄骸收起：先亮这么久 */
    public static final int CLOSE_LICH_FLASH_MS = 60;
    /** 玄骸收起总长（先亮 60 ms，再按噪声溶解 270 ms） */
    public static final int CLOSE_LICH_MS = 330;
    /** 四边羽化：横向占窗宽、纵向占窗高 */
    public static final float FEATHER_X = 0.09F;
    public static final float FEATHER_Y = 0.14F;

    private static final int MAX_SLOTS = 6;
    private static final int MAX_LICH_PER_FRAME = 3;
    private static final long IDLE_RELEASE_MS = 10_000L;
    /** 缓冲尺寸的小变化要持续这么久才重建（比开收框都长） */
    private static final long RESIZE_SETTLE_MS = 250L;
    /**
     * 玄骸缓冲最多每这么多毫秒重画一次（约 30 帧）。假实体一次要 0.3 ms 多，每帧都画超出预算；
     * 通讯画面本来就像一路视频，idle 和烟都慢，30 帧看不出差别，合成那一步（羽化、重影、错位带）照样每帧做
     */
    private static final long LICH_FRAME_MS = 33L;
    /** 一张缓冲隔了这么久没人要又被要：当成重新开的窗 */
    private static final long REOPEN_GAP_MS = 250L;

    private static final Color LICH_RED = new Color(1.0F, 0.23F, 0.32F);
    private static final Color GLITCH_CYAN = new Color(0.31F, 0.91F, 1.0F);
    private static final Color GLITCH_MAGENTA = new Color(1.0F, 0.31F, 0.85F);
    /** 溶解亮边：KUVA_HOT */
    private static final Color EDGE_HOT = new Color(1.0F, 0.478F, 0.353F);

    /**
     * 一个窗这一帧的状态。调用方各自留一个、每帧改字段再交给 {@link #draw}，不用每帧新建；draw 只读不改。
     *
     * <p>时间都是毫秒：通讯窗用演出钟（单人暂停时停住），指南用墙钟。</p>
     */
    public static final class Frame {
        /** 整窗透明度（指南换篇淡入之类） */
        public float alpha = 1.0F;
        /** 说话态：Lotus 亮线扫描、Ordis 涟漪和核心明暗、碎块转三倍快，玄骸播说话动画 */
        public boolean talking;
        /** 台词正在擦出：Lotus 最上一瓣提亮 */
        public boolean wiping;
        /** 静止态（指南）：Lotus / Ordis 画一次就不再动，玄骸 idle 照动；也不出错位带 */
        public boolean still;
        /** 到场那一次：开框后接 200 ms 青色故障 */
        public boolean arrival;
        /** 故障段 {@code <x>} 的那两拍：Ordis 碎块再错开、整窗品 / 青色差 */
        public boolean kick;
        /** 开框后过了多少毫秒；-1 = 早就开好了，不播开框 */
        public long openMs = -1L;
        /** 收起开始后过了多少毫秒；-1 = 没在收。超过 {@link #closeDurationMs} 就什么都不画 */
        public long closeMs = -1L;
        /** 玄骸朝向：0 = 按右边的窗朝左（屏幕中间），1 = 朝右，-1 = 朝左 */
        public int facing;

        // 下面四样给自己做开收框的调用方（通讯窗用姿态矩阵压扁、错开），只把合成着色器才做得出来的效果交进来

        /** 按噪声溶解的进度 0~1；没有合成着色器时改成不透明度乘 {@code 1 − dissolve} */
        public float dissolve;
        /** 整窗提亮倍数，1 = 不提亮 */
        public float brighten = 1.0F;
        /** 下半窗染青的量 0~1（横向错位由调用方自己挪） */
        public float glitch;
        /** 这一次画的是重影副本：常驻的错色重影、错位带都不画，免得叠两份 */
        public boolean ghostPass;

        /** 恢复默认值，返回自己 */
        public Frame reset() {
            alpha = 1.0F;
            talking = false;
            wiping = false;
            still = false;
            arrival = false;
            kick = false;
            openMs = -1L;
            closeMs = -1L;
            facing = 0;
            dissolve = 0.0F;
            brighten = 1.0F;
            glitch = 0.0F;
            ghostPass = false;
            return this;
        }

        void copyFrom(Frame o) {
            alpha = o.alpha;
            talking = o.talking;
            wiping = o.wiping;
            still = o.still;
            arrival = o.arrival;
            kick = o.kick;
            openMs = o.openMs;
            closeMs = o.closeMs;
            facing = o.facing;
            dissolve = o.dissolve;
            brighten = o.brighten;
            glitch = o.glitch;
            ghostPass = o.ghostPass;
        }
    }

    private static final Frame SHELL = new Frame();
    private static final Slot[] SLOTS = new Slot[MAX_SLOTS];
    private static final Matrix4f PROJ = new Matrix4f();
    private static final PoseStack STACK = new PoseStack();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(4096));

    private static long frameNo;
    private static int lastWinW = -1;
    private static int lastWinH = -1;

    private CommPortraits() {
    }

    /**
     * 在 (x, y, w, h) 这个框里画发言人的肖像（不播开收框，只按 alpha 淡入淡出）
     *
     * <p>坐标是当前 pose 下的单位，缓冲按实际物理像素建：通讯窗先 {@code scale(1/g)} 传物理像素，指南直接传指南像素都行。</p>
     *
     * @param look    发言人是玄骸时的外观，其他人可为空
     * @param talking 正在说话
     * @return 画了返回 true；没画（玄骸但还没有世界、玄骸肖像太多）返回 false，调用方画自己的退路
     */
    public static boolean draw(GuiGraphics g, CommSpeaker who, @Nullable LichLook look, int x, int y, int w, int h,
                               float alpha, boolean talking, float partialTick) {
        SHELL.reset();
        SHELL.alpha = alpha;
        SHELL.talking = talking;
        // 通讯窗：开收框的压扁、错开已经在姿态矩阵里，溶解、提亮、到场故障、故障段两拍这几样在它画窗期间读得到
        CommHud.DrawFx fx = CommHud.currentDrawFx();
        if (fx != null) {
            SHELL.wiping = talking;
            SHELL.dissolve = fx.dissolve();
            SHELL.brighten = fx.brighten();
            SHELL.glitch = fx.glitch();
            SHELL.ghostPass = fx.ghostPass();
            SHELL.kick = fx.kick();
        }
        return draw(g, who, look, x, y, w, h, SHELL, partialTick);
    }

    /**
     * 带完整状态画肖像：开框、到场故障、收起、说话 / 擦出 / 故障段都按 {@code frame} 做
     *
     * @return 画了返回 true（收起已经结束、透明度为 0 也算画了）；没画返回 false，调用方画自己的退路
     */
    public static boolean draw(GuiGraphics g, CommSpeaker who, @Nullable LichLook look, int x, int y, int w, int h,
                               Frame frame, float partialTick) {
        if (w <= 0 || h <= 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean lich = who == CommSpeaker.LICH;
        if (lich && mc.level == null) {
            return false;
        }
        if (frame.closeMs >= closeDurationMs(who) || frame.alpha <= 0.003F) {
            return true;
        }
        Matrix4f m = g.pose().last().pose();
        // 缓冲按物理像素建。调用方开收框时会压扁 / 拉宽姿态矩阵，所以取两个轴里较大的缩放，
        // 窗不会因为正在压扁就换一张低分辨率的缓冲；收起压成线时缩放只在一个轴上变小，取大值后不变
        float gs = (float) mc.getWindow().getGuiScale();
        float scale = Math.max((float) Math.hypot(m.m00(), m.m01()), (float) Math.hypot(m.m10(), m.m11())) * gs;
        int wantW = Math.max(1, Math.round(w * scale));
        int wantH = Math.max(1, Math.round(h * scale));
        int seed = look != null ? look.seed() : 0;
        int weapon = look != null ? look.weapon() : -1;
        int facing = lich ? Integer.signum(frame.facing) : 0;
        long now = Util.getMillis();

        Slot slot = acquire(who, seed, weapon, w, h, frame.still, facing);
        if (slot == null) {
            return false;
        }
        resize(slot, wantW, wantH, now);
        if (now - slot.lastUseMs > REOPEN_GAP_MS) {
            // 隔了一阵才又要画：当成新开的窗，微粒重新等 400 ms 再淡入
            slot.shownSinceMs = now;
        }
        slot.snap.copyFrom(frame);
        if (frame.openMs < 0L) {
            slot.snap.openMs = now - slot.shownSinceMs;
        }
        slot.lastUseMs = now;
        slot.requestFrame = frameNo;
        if (lich && (slot.pose == null || slot.pose.talking() != frame.talking)) {
            slot.pose = new LichPuppet.Pose(seed, weapon, frame.talking, 0.0F);
        }

        Slot src = slot.valid ? slot : similar(slot);
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            if (src == null || src.target == null) {
                placeholder(m, x, y, w, h, who, frame);
            } else {
                composite(m, x, y, w, h, who, frame, src, slot, now, mc.getWindow().getHeight());
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        return true;
    }

    /** 开框动画多长（毫秒） */
    public static int openDurationMs(CommSpeaker who) {
        return who == CommSpeaker.LICH ? OPEN_LICH_MS : OPEN_FRIEND_MS;
    }

    /** 收起动画多长（毫秒）；合成着色器没建成时玄骸也是 130 ms 淡出 */
    public static int closeDurationMs(CommSpeaker who) {
        return who == CommSpeaker.LICH && CommShaders.composite() != null ? CLOSE_LICH_MS : CLOSE_FRIEND_MS;
    }

    /** 作废缓存的离屏缓冲和假实体（换世界、资源重载时调） */
    public static void invalidate() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            release(i);
        }
        LichPuppet.invalidate();
        CommPortraitArt.invalidate();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            release(i);
        }
    }

    // ==================== 每帧开头：把上一帧登记的窗画进缓冲 ====================

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        frameNo++;
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        long now = Util.getMillis();
        if (win.getWidth() != lastWinW || win.getHeight() != lastWinH) {
            lastWinW = win.getWidth();
            lastWinH = win.getHeight();
            for (int i = 0; i < MAX_SLOTS; i++) {
                release(i);
            }
            return;
        }
        boolean began = false;
        for (int i = 0; i < MAX_SLOTS; i++) {
            Slot s = SLOTS[i];
            if (s == null) {
                continue;
            }
            if (now - s.lastUseMs > IDLE_RELEASE_MS) {
                release(i);
                continue;
            }
            if (s.requestFrame != frameNo - 1) {
                continue;
            }
            boolean lich = s.who == CommSpeaker.LICH;
            boolean fresh = s.valid && s.target != null && s.target.width == s.pw && s.target.height == s.ph;
            if (lich ? mc.level == null || (fresh && now - s.lastRenderMs < LICH_FRAME_MS) : (s.still && fresh)) {
                continue;
            }
            if (!began) {
                began = true;
                beginOffscreen();
            }
            renderSlot(s, event.renderTickTime, now, Math.max(1, Math.round(win.getHeight() / 1080.0F)));
        }
        if (began) {
            endOffscreen(mc);
        }
    }

    private static void beginOffscreen() {
        RenderSystem.backupProjectionMatrix();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
    }

    private static void endOffscreen(Minecraft mc) {
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void renderSlot(Slot s, float partialTick, long now, int px) {
        if (s.target == null || s.target.width != s.pw || s.target.height != s.ph) {
            if (s.target != null) {
                s.target.destroyBuffers();
            }
            s.target = new TextureTarget(s.pw, s.ph, true, Minecraft.ON_OSX);
            s.target.setFilterMode(GlConst.GL_LINEAR);
            s.target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            s.valid = false;
        }
        s.target.clear(Minecraft.ON_OSX);
        s.target.bindWrite(true);
        PROJ.setOrtho(0.0F, s.pw, s.ph, 0.0F, 1000.0F, 21000.0F);
        RenderSystem.setProjectionMatrix(PROJ, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        CommPortraitArt.render(s.who, s.pose, s.snap, s.pw, s.ph, px, now, partialTick, STACK, BUFFERS);
        s.valid = true;
        s.lastRenderMs = now;
    }

    // ==================== 合成 ====================

    private static void composite(Matrix4f m, int x, int y, int w, int h, CommSpeaker who, Frame f, Slot src,
                                  Slot owner, long now, int screenH) {
        boolean lich = who == CommSpeaker.LICH;
        ShaderInstance shader = CommShaders.composite();
        float px = Math.max(1.0F, Math.round(screenH / 1080.0F));

        float sx = 1.0F;
        float sy = 1.0F;
        float mainA = 1.0F;
        float flash = 1.0F;
        float dragDx = 0.0F;
        float dragA = 0.0F;
        float glitchAmt = 0.0F;
        float glitchShift = 0.0F;
        float dissolve = -1.0F;

        long o = f.openMs;
        if (o >= 0L) {
            if (!lich) {
                if (o < OPEN_FRIEND_MS) {
                    float p = easeOutCubic(o / (float) OPEN_FRIEND_MS);
                    sy = lerp(0.25F, 1.0F, p);
                    sx = lerp(1.3F, 1.0F, p);
                    mainA = lerp(0.6F, 1.0F, p);
                }
            } else if (o < 45L) {
                mainA = 0.4F;
                dragDx = 0.06F;
                dragA = 0.4F;
            } else if (o < 90L) {
                mainA = 0.75F;
                dragDx = 0.03F;
                dragA = 0.25F;
            } else if (o < OPEN_LICH_MS) {
                dragA = 0.25F * (1.0F - (o - 90L) / 40.0F);
            }
            if (f.arrival && o >= OPEN_LICH_MS && o < ARRIVAL_GLITCH_END_MS) {
                glitchAmt = 0.35F * (1.0F - (o - OPEN_LICH_MS) / (float) (ARRIVAL_GLITCH_END_MS - OPEN_LICH_MS));
                if (now >= owner.glitchNext) {
                    owner.glitchNext = now + 40L;
                    owner.glitchShift = (owner.nextFloat() < 0.5F ? -1.0F : 1.0F) * (owner.nextFloat() < 0.5F ? 1.0F : 2.0F);
                }
                glitchShift = owner.glitchShift * px / owner.pw;
            }
        }
        long c = f.closeMs;
        if (c >= 0L) {
            if (!lich || shader == null) {
                float p = clamp01(c / (float) CLOSE_FRIEND_MS);
                if (!lich) {
                    sy *= lerp(1.0F, 0.04F, p * p);
                }
                mainA *= 1.0F - p;
            } else if (c < CLOSE_LICH_FLASH_MS) {
                flash = 1.35F;
            } else {
                dissolve = (c - CLOSE_LICH_FLASH_MS) / (float) (CLOSE_LICH_MS - CLOSE_LICH_FLASH_MS);
            }
        }
        // 调用方交进来的效果（通讯窗自己排的时间轴）
        flash *= f.brighten;
        glitchAmt = Math.max(glitchAmt, clamp01(f.glitch));
        if (f.dissolve > 0.0F) {
            if (shader == null) {
                mainA *= 1.0F - clamp01(f.dissolve);
            } else {
                dissolve = Math.max(dissolve, clamp01(f.dissolve));
            }
        }

        float cx = x + w * 0.5F;
        float cy = y + h * 0.5F;
        float hw = w * 0.5F * sx;
        float hh = h * 0.5F * sy;
        float x0 = cx - hw;
        float x1 = cx + hw;
        float y0 = cy - hh;
        float y1 = cy + hh;

        RenderSystem.setShaderTexture(0, src.target.getColorTextureId());
        if (shader == null) {
            gridComposite(m, x0, y0, x1, y1, mainA * f.alpha, true, 0);
            return;
        }

        // 错色重影（玄骸常驻、弱）/ Ordis 故障段的品青色差
        float ghostA = 0.0F;
        float ghostB = 0.0F;
        float dxA = 0.0F;
        float dxB = 0.0F;
        Color tintA = LICH_RED;
        Color tintB = GLITCH_CYAN;
        // 重影副本那一次（f.ghostPass）只画本体，常驻重影和错位带交给主体那一次
        if (f.ghostPass) {
            ghostA = 0.0F;
        } else if (lich) {
            float strength = 0.3F * EffectsDisplayConfig.commGhost();
            if (strength > 0.0F) {
                if (!f.still && now >= owner.jitterNext) {
                    owner.jitterNext = now + 80L + (long) (owner.nextFloat() * 40.0F);
                    float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
                    owner.jitterA = (owner.nextFloat() - 0.5F) * gs;
                    owner.jitterB = (owner.nextFloat() - 0.5F) * gs;
                }
                ghostA = strength;
                ghostB = strength;
                dxA = -0.02F + owner.jitterA / owner.pw;
                dxB = 0.01F + owner.jitterB / owner.pw;
            }
        } else if (f.kick && who == CommSpeaker.ORDIS) {
            tintA = GLITCH_MAGENTA;
            ghostA = 0.4F;
            ghostB = 0.4F;
            dxA = -px / owner.pw;
            dxB = px / owner.pw;
        }

        // 水平错位带：每 1.5~3 s 一次，高 2~3、横移 2~4 个像素，持续 40~80 ms；玄骸那条带 1 像素红青色差
        float bandC = 0.5F;
        float bandH = 0.0F;
        float bandS = 0.0F;
        float bandX = 0.0F;
        if (!f.still && !f.ghostPass) {
            if (owner.bandNext == 0L) {
                owner.bandNext = now + 400L + (long) (owner.nextFloat() * 1600.0F);
            }
            if (now >= owner.bandNext) {
                owner.bandEnd = now + 40L + (long) (owner.nextFloat() * 40.0F);
                owner.bandNext = now + 1500L + (long) (owner.nextFloat() * 1500.0F);
                owner.bandCenter = 0.15F + owner.nextFloat() * 0.7F;
                owner.bandHalf = (2.0F + owner.nextFloat()) * px * 0.5F / owner.ph;
                owner.bandShift = (owner.nextFloat() < 0.5F ? -1.0F : 1.0F) * (2.0F + owner.nextFloat() * 2.0F) * px / owner.pw;
            }
            if (now < owner.bandEnd) {
                bandC = owner.bandCenter;
                bandH = owner.bandHalf;
                bandS = owner.bandShift;
                bandX = lich ? px / owner.pw : 0.0F;
            }
        }

        shader.safeGetUniform("Feather").set(FEATHER_X, FEATHER_Y);
        shader.safeGetUniform("Alpha").set(mainA);
        shader.safeGetUniform("Flash").set(flash);
        shader.safeGetUniform("GhostA").set(tintA.r(), tintA.g(), tintA.b(), dxA);
        shader.safeGetUniform("GhostB").set(tintB.r(), tintB.g(), tintB.b(), dxB);
        shader.safeGetUniform("GhostAlpha").set(ghostA, ghostB);
        shader.safeGetUniform("Drag").set(dragDx, dragA);
        shader.safeGetUniform("Band").set(bandC, bandH, bandS, bandX);
        shader.safeGetUniform("Dissolve").set(Math.max(0.0F, dissolve), 0.06F, dissolve >= 0.0F ? 1.0F : 0.0F);
        shader.safeGetUniform("EdgeColor").set(EDGE_HOT.r(), EDGE_HOT.g(), EDGE_HOT.b());
        shader.safeGetUniform("Glitch").set(glitchAmt, glitchShift);
        shader.safeGetUniform("NoiseScale").set(1.5F, 1.0F);

        AbstractTexture noise = Minecraft.getInstance().getTextureManager().getTexture(CommPortraitArt.NOISE);
        noise.setFilter(true, false);
        RenderSystem.setShaderTexture(1, noise.getId());
        RenderSystem.setShaderTexture(0, src.target.getColorTextureId());

        // 拖影往右错出窗外，四边形往右多铺一截
        float ext = dragA > 0.0F ? dragDx : 0.0F;
        float xe = x1 + (x1 - x0) * ext;
        int a = Math.round(clamp01(f.alpha) * 255.0F);
        BufferBuilder b = CommPortraitArt.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, x0, y0, 0.0F).uv(0.0F, 0.0F).color(255, 255, 255, a).endVertex();
        b.vertex(m, x0, y1, 0.0F).uv(0.0F, 1.0F).color(255, 255, 255, a).endVertex();
        b.vertex(m, xe, y1, 0.0F).uv(1.0F + ext, 1.0F).color(255, 255, 255, a).endVertex();
        b.vertex(m, xe, y0, 0.0F).uv(1.0F + ext, 0.0F).color(255, 255, 255, a).endVertex();
        CommShaders.draw(b, shader, CommShaders.BLEND_PREMULT);
        RenderSystem.setShaderTexture(1, 0);
    }

    /** 缓冲还没画好的那一帧：只画发言人的暗底色块，不留白 */
    private static void placeholder(Matrix4f m, int x, int y, int w, int h, CommSpeaker who, Frame f) {
        gridComposite(m, x, y, x + w, y + h, f.alpha * 0.9F, false, CommPortraitArt.baseColor(who));
    }

    /**
     * 退路：原版着色器，窗切成 5×5 网格（分界在羽化宽度的一半和整段），在顶点 alpha 上做羽化。
     * 颜色按预乘写（顶点色 = α, α, α, α），和着色器那条路的混合方式一致。
     */
    private static void gridComposite(Matrix4f m, float x0, float y0, float x1, float y1, float alpha,
                                      boolean textured, int rgb) {
        float[] us = GRID_U;
        float[] vs = GRID_V;
        us[1] = FEATHER_X * 0.5F;
        us[2] = FEATHER_X;
        us[3] = 1.0F - FEATHER_X;
        us[4] = 1.0F - FEATHER_X * 0.5F;
        vs[1] = FEATHER_Y * 0.5F;
        vs[2] = FEATHER_Y;
        vs[3] = 1.0F - FEATHER_Y;
        vs[4] = 1.0F - FEATHER_Y * 0.5F;
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float bl = (rgb & 0xFF) / 255.0F;
        BufferBuilder b = CommPortraitArt.begin(VertexFormat.Mode.QUADS,
                textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 5; i++) {
                gridVertex(b, m, x0, y0, x1, y1, us[i], vs[j], alpha, textured, r, g, bl);
                gridVertex(b, m, x0, y0, x1, y1, us[i], vs[j + 1], alpha, textured, r, g, bl);
                gridVertex(b, m, x0, y0, x1, y1, us[i + 1], vs[j + 1], alpha, textured, r, g, bl);
                gridVertex(b, m, x0, y0, x1, y1, us[i + 1], vs[j], alpha, textured, r, g, bl);
            }
        }
        CommShaders.draw(b, textured ? GameRenderer.getPositionTexColorShader() : GameRenderer.getPositionColorShader(),
                CommShaders.BLEND_PREMULT);
    }

    private static final float[] GRID_U = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
    private static final float[] GRID_V = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};

    private static void gridVertex(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float u, float v,
                                   float alpha, boolean textured, float r, float g, float bl) {
        float fu = smooth(Math.min(u, 1.0F - u) / FEATHER_X);
        float fv = smooth(Math.min(v, 1.0F - v) / FEATHER_Y);
        float a = clamp01(alpha * fu * fv);
        b.vertex(m, x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, 0.0F);
        if (textured) {
            // 缓冲是 GL 纹理，原点在左下，取样翻 v
            b.uv(u, 1.0F - v).color(a, a, a, a).endVertex();
        } else {
            b.color(r * a, g * a, bl * a, a).endVertex();
        }
    }

    // ==================== 缓冲池 ====================

    /**
     * 按调用方给的框（它自己坐标系里的 w、h，开收框期间不变）认缓冲；缓冲的像素尺寸另算，见 {@link #resize}
     */
    @Nullable
    private static Slot acquire(CommSpeaker who, int seed, int weapon, int w, int h, boolean still, int facing) {
        int free = -1;
        int lichThisFrame = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            Slot s = SLOTS[i];
            if (s == null) {
                if (free < 0) {
                    free = i;
                }
                continue;
            }
            if (s.who == who && s.seed == seed && s.weapon == weapon && s.boxW == w && s.boxH == h
                    && s.still == still && s.facing == facing) {
                return s;
            }
            if (s.who == CommSpeaker.LICH && s.requestFrame == frameNo) {
                lichThisFrame++;
            }
        }
        if (who == CommSpeaker.LICH && lichThisFrame >= MAX_LICH_PER_FRAME) {
            return null;
        }
        if (free < 0) {
            // 满了：挤掉最久没用、而且这两帧都没人要的
            long oldest = Long.MAX_VALUE;
            for (int i = 0; i < MAX_SLOTS; i++) {
                Slot s = SLOTS[i];
                if (s.requestFrame < frameNo - 1 && s.lastUseMs < oldest) {
                    oldest = s.lastUseMs;
                    free = i;
                }
            }
            if (free < 0) {
                return null;
            }
            release(free);
        }
        Slot s = new Slot();
        s.who = who;
        s.seed = seed;
        s.weapon = weapon;
        s.boxW = w;
        s.boxH = h;
        s.still = still;
        s.facing = facing;
        s.rng = System.nanoTime() ^ ((long) free << 32) ^ 0x9E3779B97F4A7C15L;
        SLOTS[free] = s;
        return s;
    }

    /**
     * 定缓冲的像素尺寸。第一次直接用；之后要大出一截（指南放大、窗口拉大）马上跟，
     * 小一点的变化要持续 {@link #RESIZE_SETTLE_MS} 才跟——开框那 100 ms 的拉宽过去之后再缩回正常大小，中间不反复建缓冲
     */
    private static void resize(Slot s, int wantW, int wantH, long now) {
        if (s.pw == 0 || s.ph == 0) {
            s.pw = wantW;
            s.ph = wantH;
            return;
        }
        if (wantW == s.pw && wantH == s.ph) {
            s.mismatchSinceMs = 0L;
            return;
        }
        if (wantW > s.pw * 1.25F || wantH > s.ph * 1.25F) {
            s.pw = wantW;
            s.ph = wantH;
            s.mismatchSinceMs = 0L;
            return;
        }
        if (s.mismatchSinceMs == 0L) {
            s.mismatchSinceMs = now;
        } else if (now - s.mismatchSinceMs >= RESIZE_SETTLE_MS) {
            s.pw = wantW;
            s.ph = wantH;
            s.mismatchSinceMs = 0L;
        }
    }

    /** 同一个人、同一外观的另一张已经画好的缓冲（状态切换时先拿它顶一帧；尺寸不同也行，合成按比例取样） */
    @Nullable
    private static Slot similar(Slot slot) {
        for (Slot s : SLOTS) {
            if (s != null && s != slot && s.valid && s.target != null && s.who == slot.who && s.seed == slot.seed
                    && s.weapon == slot.weapon) {
                return s;
            }
        }
        return null;
    }

    private static void release(int i) {
        Slot s = SLOTS[i];
        if (s != null && s.target != null) {
            s.target.destroyBuffers();
            s.target = null;
        }
        SLOTS[i] = null;
    }

    private static final class Slot {
        CommSpeaker who;
        int seed;
        int weapon;
        /** 调用方给的框（它自己的坐标单位） */
        int boxW;
        int boxH;
        /** 缓冲要的像素尺寸；和 target 的实际尺寸不同时下一次画缓冲前重建 */
        int pw;
        int ph;
        long mismatchSinceMs;
        boolean still;
        int facing;
        @Nullable
        TextureTarget target;
        boolean valid;
        long requestFrame = Long.MIN_VALUE;
        long lastUseMs;
        long shownSinceMs;
        long lastRenderMs;
        final Frame snap = new Frame();
        @Nullable
        LichPuppet.Pose pose;

        long rng;
        long bandNext;
        long bandEnd;
        float bandCenter;
        float bandHalf;
        float bandShift;
        long jitterNext;
        float jitterA;
        float jitterB;
        long glitchNext;
        float glitchShift;

        float nextFloat() {
            long x = rng;
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            rng = x;
            return ((x >>> 40) & 0xFFFFFF) / 16777216.0F;
        }
    }

    private record Color(float r, float g, float b) {
    }

    // ==================== 小工具 ====================

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    private static float smooth(float t) {
        float x = clamp01(t);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - clamp01(t);
        return 1.0F - u * u * u;
    }
}
