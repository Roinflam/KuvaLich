package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.renderer.KuvaMasterRenderer;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 不进世界的玄骸模型，给通讯肖像和档案中间的立像用
 *
 * <p><b>为什么不直接画世界里那只</b>：GeckoLib 的动画进度（{@code animTime}）记在模型实例上，
 * 和世界里的玄骸共用一个渲染器，两边会互相拖动画；肖像也不该跟着世界里那只的攻击、走路、受伤变色走。
 * 所以这里自己 new 一个 {@link KuvaMasterRenderer}（它会带自己的模型实例），配几只只在内存里的假实体。
 * 假实体用负数 id，发光层、淡入淡出这些按 id 查状态的地方都查不到它，走默认值。</p>
 *
 * <p><b>时钟</b>：假实体的动画按墙钟走（{@link Puppet#getTick} 返回墙钟刻数），单人暂停、打开指南时 idle 照样在动；
 * {@code tickCount} 每次画之前也按墙钟设，读它的发光层心跳一样跟得上。说话动画看 {@code talkUntilMs}，读的是演出钟。</p>
 *
 * <p>没有世界（标题画面打开指南）时不构造、直接返回 false：GeckoLib 套 molang 时要读世界。
 * 换世界（退出、换服）时作废重建；资源重载时连渲染器一起重建。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichPuppet {

    /**
     * 立像的姿态
     *
     * @param seed    外观种子
     * @param weapon  手里武器的下标，-1 = 不画
     * @param talking 正在说话
     * @param dim     0 正常，1 全黑剪影（档案空状态）
     */
    public record Pose(int seed, int weapon, boolean talking, float dim) {
    }

    // ---- 取景（模型单位，16 = 1 格）----
    // 模型实测：头盔顶 59.0，面部 Head 骨 42.6~48.4，躯干 20.2~45.2，两臂横向 -17.5~17.2。
    // 半身像窗高对应 33 个模型单位：上沿 61（头盔顶上留 2），下沿 28（胸口）。
    // 按原作的「头占窗高 0.41」取景会切到腰（我们的玄骸头盔很高），这组数是折中：头约占窗高一半、肩宽约 0.7 窗宽。

    /** 半身像上沿（模型单位） */
    public static final float BUST_TOP = 61.0F;
    /** 半身像窗高对应多少模型单位 */
    public static final float BUST_SPAN = 33.0F;
    /** 半身像绕这个高度转（模型单位，头和颈之间），转的时候取景不跑 */
    private static final float BUST_PIVOT = 50.0F;
    /** 半身像俯仰：镜头低于眼线这么多度，略仰 */
    public static final float BUST_TILT = 7.0F;
    /** 半身像默认朝向：往画面左边偏这么多度（右边的窗朝屏幕中间说话） */
    public static final float BUST_YAW = 17.0F;
    /**
     * 半身像的两盏灯（界面坐标：x 往右、y 往下、z 朝观众）。主灯从下前方略偏右打亮脸和胸口，头盔顶背光发暗；
     * 第二盏从右后方擦出轮廓。原版物品栏那套是从上往下打，脸是平的，压暗之后整个人糊成一片
     */
    private static final Vector3f BUST_LIGHT_0 = new Vector3f(0.15F, 0.5F, 0.7F).normalize();
    private static final Vector3f BUST_LIGHT_1 = new Vector3f(0.95F, -0.1F, -0.45F).normalize();
    /** 说话状态在最后一次「在说」之后再留多久（演出钟毫秒） */
    private static final long TALK_TAIL_MS = 400L;
    /** 正在画半身像（换成上面那两盏灯） */
    private static boolean bustLight;

    /** 全身像：脚底到头盔顶（模型单位） */
    private static final float FULL_HEIGHT = 59.0F;

    /** 同时留着的假实体上限（通讯窗 1 + 指南 2 + 档案 1） */
    private static final int MAX_PUPPETS = 4;
    /** 剪影色（档案空状态） */
    private static final float DIM_R = 0.06F;
    private static final float DIM_G = 0.01F;
    private static final float DIM_B = 0.02F;

    private static final Puppet[] PUPPETS = new Puppet[MAX_PUPPETS];
    private static final long[] LAST_USE = new long[MAX_PUPPETS];
    private static final long EPOCH_NANOS = Util.getNanos();

    @Nullable
    private static KuvaMasterRenderer renderer;
    private static boolean rendererFailed;
    @Nullable
    private static Level builtFor;
    private static int nextId = -1_000_000;

    // runAsFancy 要一个 Runnable，参数经静态字段传进去，免得每帧建 lambda
    private static Puppet drawPuppet;
    private static PoseStack drawStack;
    private static MultiBufferSource drawBuffers;
    private static float drawPartial;
    private static final Runnable DRAW = () -> renderer.render(drawPuppet, 0.0F, drawPartial, drawStack, drawBuffers,
            LightTexture.FULL_BRIGHT);

    private LichPuppet() {
    }

    /** 这个实体是不是肖像用的假实体（名牌、世界特效这类只该给世界里实体的东西用它跳过） */
    /** 正在画通讯窗的半身像（不是档案、指南里的全身像） */
    public static boolean drawingBust() {
        return bustLight;
    }

    public static boolean isPuppet(@Nullable Entity entity) {
        return entity instanceof Puppet;
    }

    /**
     * 半身像，给通讯窗
     *
     * <p>直接画在当前界面上，超出 (x, y, w, h) 的部分用 scissor 裁掉；朝向按右边的窗（往左偏 {@link #BUST_YAW}）。
     * 通讯窗自己是先画进离屏缓冲再合成的，走 {@link #renderBustInto}。</p>
     *
     * @return 画了返回 true；没有世界等原因没画返回 false
     */
    public static boolean renderBust(GuiGraphics g, int x, int y, int w, int h, float partialTick, Pose pose) {
        Puppet puppet = obtain(pose);
        if (puppet == null || w <= 0 || h <= 0) {
            return false;
        }
        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        PoseStack ps = g.pose();
        ps.pushPose();
        placeBust(ps, x, y, w, h, 100.0F + h * 0.8F, BUST_YAW);
        drawModel(ps, g.bufferSource(), puppet, partialTick, 180.0F + BUST_YAW, 1.0F, 1.0F, 1.0F, pose.dim());
        ps.popPose();
        g.disableScissor();
        return true;
    }

    /**
     * 半身像，画进调用方已经绑好的离屏缓冲（通讯肖像用）：缓冲左上角 (0, 0)、大小 w × h 像素，不裁剪
     *
     * @param yawDeg  往画面左边偏多少度（负数往右）
     * @param r       模型颜色乘子（压暗、染色），和剪影相乘
     * @return 画了返回 true
     */
    public static boolean renderBustInto(PoseStack ps, MultiBufferSource.BufferSource buffers, int w, int h,
                                         float partialTick, Pose pose, float yawDeg, float r, float g, float b) {
        Puppet puppet = obtain(pose);
        if (puppet == null || w <= 0 || h <= 0) {
            return false;
        }
        ps.pushPose();
        placeBust(ps, 0, 0, w, h, 1000.0F, yawDeg);
        bustLight = true;
        try {
            drawModel(ps, buffers, puppet, partialTick, 180.0F + yawDeg, r, g, b, pose.dim());
        } finally {
            bustLight = false;
        }
        ps.popPose();
        return true;
    }


    /**
     * 全身像，给档案：脚底中心 (cx, feetY)，身高 heightPx 像素
     *
     * @param yawDeg 0 = 正对屏幕，正数往画面左边转
     * @return 画了返回 true；没画返回 false
     */
    public static boolean renderFull(GuiGraphics g, int cx, int feetY, int heightPx, float yawDeg, float partialTick,
                                     Pose pose) {
        Puppet puppet = obtain(pose);
        if (puppet == null || heightPx <= 0) {
            return false;
        }
        g.flush();
        float s = heightPx / FULL_HEIGHT * 16.0F;
        PoseStack ps = g.pose();
        ps.pushPose();
        // 模型前后最厚约 1.2 格，往屏幕外推够这么多，界面上后画的东西才不会被深度挡住
        ps.translate(cx, feetY, 50.0F + 1.3F * s);
        ps.scale(s, s, -s);
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
        drawModel(ps, g.bufferSource(), puppet, partialTick, 180.0F + yawDeg, 1.0F, 1.0F, 1.0F, pose.dim());
        ps.popPose();
        return true;
    }

    /** 作废全部假实体和渲染器（换世界、资源重载） */
    public static void invalidate() {
        for (int i = 0; i < MAX_PUPPETS; i++) {
            PUPPETS[i] = null;
            LAST_USE[i] = 0L;
        }
        renderer = null;
        rendererFailed = false;
        builtFor = null;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        invalidate();
    }

    // ==================== 内部 ====================

    /** 窗高 h 对应 {@link #BUST_SPAN} 个模型单位，上沿在 {@link #BUST_TOP}；绕 {@link #BUST_PIVOT} 俯仰 */
    private static void placeBust(PoseStack ps, float x, float y, float w, float h, float z, float yawDeg) {
        float unit = h / BUST_SPAN;
        float s = unit * 16.0F;
        ps.translate(x + w * 0.5F, y + (BUST_TOP - BUST_PIVOT) * unit, z);
        ps.scale(s, s, -s);
        ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
        ps.mulPose(Axis.XP.rotationDegrees(BUST_TILT));
        ps.translate(0.0F, -BUST_PIVOT / 16.0F, 0.0F);
    }

    private static void drawModel(PoseStack ps, MultiBufferSource buffers, Puppet puppet, float partialTick,
                                  float bodyYaw, float r, float g, float b, float dim) {
        if (renderer == null) {
            return;
        }
        puppet.yBodyRot = bodyYaw;
        puppet.yBodyRotO = bodyYaw;
        puppet.yHeadRot = bodyYaw;
        puppet.yHeadRotO = bodyYaw;
        puppet.setYRot(bodyYaw);
        puppet.yRotO = bodyYaw;
        puppet.setXRot(0.0F);
        puppet.xRotO = 0.0F;
        puppet.tickCount = (int) wallTicks();

        float d = Math.max(0.0F, Math.min(1.0F, dim));
        float[] old = RenderSystem.getShaderColor();
        float oldR = old[0];
        float oldG = old[1];
        float oldB = old[2];
        float oldA = old[3];
        RenderSystem.setShaderColor(r * (1.0F + (DIM_R - 1.0F) * d), g * (1.0F + (DIM_G - 1.0F) * d),
                b * (1.0F + (DIM_B - 1.0F) * d), 1.0F);
        Lighting.setupForEntityInInventory();
        if (bustLight) {
            RenderSystem.setShaderLights(BUST_LIGHT_0, BUST_LIGHT_1);
        }
        drawPuppet = puppet;
        drawStack = ps;
        drawBuffers = buffers;
        drawPartial = partialTick;
        try {
            RenderSystem.runAsFancy(DRAW);
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        } finally {
            drawPuppet = null;
            drawStack = null;
            drawBuffers = null;
            RenderSystem.setShaderColor(oldR, oldG, oldB, oldA);
            Lighting.setupFor3DItems();
        }
    }

    /**
     * 取这个种子的假实体；没有世界返回 null。一个种子只留一只：说话 / 不说话换实例的话，idle 会从头播、画面跳一下
     */
    @Nullable
    private static Puppet obtain(Pose pose) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return null;
        }
        if (builtFor != level) {
            invalidate();
            builtFor = level;
        }
        if (renderer == null) {
            if (rendererFailed) {
                return null;
            }
            try {
                renderer = new KuvaMasterRenderer(new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(),
                        mc.getItemRenderer(), mc.getBlockRenderer(), mc.getEntityRenderDispatcher().getItemInHandRenderer(),
                        mc.getResourceManager(), mc.getEntityModels(), mc.font));
            } catch (Exception e) {
                rendererFailed = true;
                LogUtil.error("玄骸肖像的渲染器没建成，肖像退回徽记", e);
                return null;
            }
        }
        long now = Util.getMillis();
        int free = -1;
        int oldest = 0;
        for (int i = 0; i < MAX_PUPPETS; i++) {
            Puppet p = PUPPETS[i];
            if (p == null) {
                if (free < 0) {
                    free = i;
                }
                continue;
            }
            if (p.seed == pose.seed()) {
                LAST_USE[i] = now;
                return configure(p, pose);
            }
            if (LAST_USE[i] < LAST_USE[oldest]) {
                oldest = i;
            }
        }
        int slot = free >= 0 ? free : oldest;
        Puppet p;
        try {
            p = new Puppet(level, nextId--);
        } catch (Exception e) {
            LogUtil.error("玄骸肖像的假实体没建成", e);
            return null;
        }
        PUPPETS[slot] = p;
        LAST_USE[slot] = now;
        return configure(p, pose);
    }

    /**
     * 说话只往后续，不清零：通讯窗（在说）和指南（没在说）同一帧画同一只时，不会一帧开一帧关地来回切；
     * 停嘴后再过 {@link #TALK_TAIL_MS} 说话动画自然收掉
     */
    private static Puppet configure(Puppet p, Pose pose) {
        p.seed = pose.seed();
        p.weapon = pose.weapon();
        if (pose.talking()) {
            p.talkUntilMs = Math.max(p.talkUntilMs, AbstractKuva.CLIENT_CLOCK.getAsLong() + TALK_TAIL_MS);
        }
        return p;
    }

    /** 墙钟刻数（带小数） */
    private static double wallTicks() {
        return (Util.getNanos() - EPOCH_NANOS) / 50_000_000.0D;
    }

    /** 只在内存里的玄骸：外观由肖像给，不进世界、不 tick */
    private static final class Puppet extends KuvaMasterEntity {

        int seed;
        int weapon = -1;

        Puppet(Level level, int id) {
            super(KuvaLichEntities.KUVA_MASTER.get(), level);
            this.setId(id);
        }

        @Override
        public int getLichSeed() {
            return seed;
        }

        @Override
        public int getLichWeaponIndex() {
            return weapon;
        }

        @Override
        public boolean shouldPlayAnimsWhileGamePaused() {
            return true;
        }

        /**
         * GeckoLib 拿它加上帧内插值（暂停时冻住）当动画时间；先减掉帧内插值，加回去正好是墙钟，
         * 暂停、指南里照样连续地动，几只假实体也用同一个钟
         */
        @Override
        public double getTick(Object entity) {
            return wallTicks() - Minecraft.getInstance().getFrameTime();
        }
    }
}
