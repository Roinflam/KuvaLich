package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.lich.LichNameplates;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.MercyRequestPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 倒地的玄骸 / 奴仆身上的提示，和处决键
 *
 * <p><b>世界里的标记</b>（所有人都看得到）：目标头顶一枚赤毒晶体下挂三滴，一道细光束往下连到锚点（玄骸胸口、奴仆头），
 * 锚点上一圈倒计时弧（从 12 点顺时针，剩下的亮、过去的暗，最后 10 秒剩下那段按 2 Hz 呼吸）。本地玩家能处决它时是猩红，
 * 别人的猎物是灰的；隔着墙再淡淡画一遍标记。投影到屏幕上太小（不到 0.046H）就放大，远处也认得出。没有资格人的奴仆
 * 只画灰色标记，没有光束和圆环。都是加法混合的面片（关掉「加法发光」时改普通透明混合）。</p>
 *
 * <p><b>屏幕上的小牌</b>：挂在锚点圆环的右边。本地玩家是资格人、站得够近（不超过同步下来的可处决距离）、
 * 视线和锚点夹角不超过 30°（或者准星正指着它）时，画骨白底的「[键] 安魂 怜悯之击」（奴仆「密语」）；
 * 别人的猎物在 8 格内看着它时画暗灰底的「某某 的猎物」。同时有好几只倒地时只给夹角最小的一只画，处决键也只对它发请求。
 * 字和线都按物理像素画，GUI 缩放大的时候不糊、不变粗。</p>
 *
 * <p><b>处决键</b>：小牌显示着时按一下发一个 {@link MercyRequestPacket}，客户端每秒最多 4 个（按住不放的自动连发也算在内）；
 * 小牌没显示时按了什么都不做。能不能捅以服务端校验为准。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyPrompt {

    /** 覆盖层 id */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "mercy_prompt");

    /** 锚点高度（占身高的比例，画倒地标记时按姿势头顶算）：玄骸胸口、奴仆头 */
    private static final double LICH_ANCHOR = 0.62D;
    private static final double THRALL_ANCHOR = 0.82D;
    /** 标记最下沿在头顶（有名牌时是名牌最上沿）上方多高 */
    private static final double MARKER_GAP = 0.1D;
    /** 有名牌时光束的上端在头顶上方多高 */
    private static final double BEAM_FROM_HEAD = 0.05D;
    /** 本地玩家的猎物 / 别人的猎物：多远以内画世界标记 */
    private static final double DRAW_RANGE_OWN = 32.0D;
    private static final double DRAW_RANGE_OTHER = 16.0D;
    /** 别人的猎物：多远以内、看着它时画小牌 */
    private static final double OTHER_CARD_RANGE = 8.0D;
    /** 视线夹角上限 */
    private static final double MAX_ANGLE_COS = Math.cos(Math.toRadians(30.0D));
    /** 选中的那只要被别的超过这么多（余弦）才换，免得两只差不多时小牌来回跳 */
    private static final double SWITCH_MARGIN = 0.006D;
    /** 扫倒地实体的半径 */
    private static final double SCAN_RANGE = 40.0D;

    /** 标记的尺寸（格）：整体高约 0.52，晶体 0.12 × 0.32，三滴 0.04 */
    private static final float MARK_TOP = 0.27F;
    private static final float CRYSTAL_H = 0.32F;
    private static final float CRYSTAL_HW = 0.06F;
    private static final float DROP_HALF = 0.02F;
    private static final float[] DROP_X = {-0.06F, 0.0F, 0.06F};
    private static final float[] DROP_FALL = {0.10F, 0.18F, 0.12F};
    private static final float THREAD_HW = 0.005F;
    /** 标记底部（最低那一滴的下沿）相对标记中心 */
    private static final float MARK_BOTTOM = MARK_TOP - CRYSTAL_H - 0.18F - DROP_HALF;
    private static final float MARK_HEIGHT = MARK_TOP - MARK_BOTTOM;
    /** 标记投影到屏幕上的最小高度（屏幕高的比例） */
    private static final float MIN_SCREEN_H = 0.046F;
    private static final float BEAM_HW = 0.006F;
    private static final float RING_R = 0.16F;
    private static final float RING_HW = 0.01F;
    private static final int RING_SEGMENTS = 48;
    /** 隔墙那一遍的透明度系数 */
    private static final float XRAY = 0.35F;

    /** 小牌：淡入 / 淡出时长 */
    private static final float CARD_IN_MS = 80.0F;
    private static final float CARD_OUT_MS = 100.0F;
    /** 资格人小牌的字色（框色同字色）、别人小牌的底色 */
    private static final int CARD_INK = 0x2A2F33;
    private static final int CARD_OTHER_BG = 0x5E6870;

    /** 每秒最多发几个请求 */
    private static final int SEND_PER_SECOND = 4;

    /** 这一 tick 视野里倒地的目标（每 tick 重扫；画的时候只读） */
    private static final List<AbstractKuva> DOWNED = new ArrayList<>();

    /** 选中的那只（画小牌、处决键对它）；-1 = 没有 */
    private static int selectedId = -1;
    /** 选中的那只本地玩家能不能处决（能 = 资格人小牌；不能 = 「某某 的猎物」） */
    private static boolean selectedOwn;
    /** 正在画（或淡出中）的小牌对应的实体 */
    private static int cardId = -1;
    private static boolean cardOwn;
    private static float cardAlpha;
    private static long cardLastMs;

    // 小牌文字：只在语言、按键、名字变了的时候重算
    private static String cardLangKey = "";
    private static String keyText = "";
    private static String lichText = "";
    private static String thrallText = "";
    private static String otherText = "";
    private static String otherName = null;
    private static String cachedLang = null;
    private static InputConstants.Key cachedKey = null;
    private static KeyModifier cachedModifier = null;

    /** 最近几次发请求的时刻（真实毫秒，环形） */
    private static final long[] SENT = new long[SEND_PER_SECOND];
    private static int sentIdx;

    // 这一帧的投影矩阵、视图矩阵和相机位置（世界阶段记下，界面阶段投影小牌用）
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Vector4f TMP4 = new Vector4f();
    private static double camX, camY, camZ;
    private static boolean matricesValid;

    // 画世界标记时复用的向量（只在渲染线程用）
    private static final Vector3f LEFT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f LOOK = new Vector3f();
    private static final float[] HEX = new float[12];

    private MercyPrompt() {
    }

    // ==================== 选目标、处决键 ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        DOWNED.clear();
        if (player == null || level == null) {
            selectedId = -1;
            drainKey();
            return;
        }
        double r2 = SCAN_RANGE * SCAN_RANGE;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof AbstractKuva kuva && kuva.isAlive() && kuva.getMercyState() == MercyState.DOWNED
                    && kuva.distanceToSqr(player) <= r2) {
                DOWNED.add(kuva);
            }
        }
        select(mc, player);
        refreshTexts(mc);
        handleKey();
    }

    /** 本地玩家眼下能不能看小牌、按处决键：活着、不是旁观、没骑东西、没睡觉、没开界面、不在处决里 */
    private static boolean playerReady(Minecraft mc, LocalPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping()
                && mc.screen == null && !MercyClient.isActive();
    }

    private static void select(Minecraft mc, LocalPlayer player) {
        int best = -1;
        boolean bestOwn = false;
        double bestCos = -2.0D;
        double keepCos = -2.0D;
        boolean keepOwn = false;
        if (!DOWNED.isEmpty() && playerReady(mc, player)) {
            double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
            float yaw = player.getYRot() * Mth.DEG_TO_RAD, pitch = player.getXRot() * Mth.DEG_TO_RAD;
            double lx = -Mth.sin(yaw) * Mth.cos(pitch), ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * Mth.cos(pitch);
            Entity aimed = mc.crosshairPickEntity;
            for (int i = 0, n = DOWNED.size(); i < n; i++) {
                AbstractKuva k = DOWNED.get(i);
                double ax = k.getX() - ex, ay = k.getY() + k.getBbHeight() * anchorFrac(k) - ey, az = k.getZ() - ez;
                double d = Math.sqrt(ax * ax + ay * ay + az * az);
                boolean own = isOwn(k, player);
                double range = own ? k.getMercyReach() : OTHER_CARD_RANGE;
                if (d > range || (!own && !hasClaimant(k))) {
                    continue;
                }
                double cos = d < 1.0E-4D ? 1.0D : (ax * lx + ay * ly + az * lz) / d;
                if (k == aimed) {
                    // 准星正指着的那只排第一
                    cos = 2.0D;
                } else if (cos < MAX_ANGLE_COS) {
                    continue;
                }
                if (k.getId() == selectedId) {
                    keepCos = cos;
                    keepOwn = own;
                }
                if (cos > bestCos) {
                    bestCos = cos;
                    best = k.getId();
                    bestOwn = own;
                }
            }
        }
        if (keepCos > -2.0D && keepCos + SWITCH_MARGIN >= bestCos) {
            best = selectedId;
            bestOwn = keepOwn;
        }
        selectedId = best;
        selectedOwn = bestOwn;
    }

    /** 本地玩家是不是这只的资格人（玄骸 = 主人，奴仆 = 打倒它的人） */
    private static boolean isOwn(AbstractKuva k, LocalPlayer player) {
        if (k.getMercyActorId() == player.getId()) {
            return true;
        }
        if (k instanceof KuvaMasterEntity lich) {
            UUID owner = lich.getOwnerUUID();
            return owner != null && owner.equals(player.getUUID());
        }
        return false;
    }

    /** 这只有没有资格人（玄骸看有没有主人，奴仆看同步下来的资格人） */
    private static boolean hasClaimant(AbstractKuva k) {
        if (k instanceof KuvaMasterEntity lich) {
            return lich.getOwnerUUID() != null || !lich.getOwnerName().isEmpty();
        }
        return k.getMercyActorId() >= 0;
    }

    /** 别人看到的「某某 的猎物」里的名字；拿不到（资格人不在本客户端的视野里）返回 null */
    private static String claimantName(AbstractKuva k) {
        if (k instanceof KuvaMasterEntity lich) {
            String name = lich.getOwnerName();
            return name.isEmpty() ? null : name;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Entity actor = level == null || k.getMercyActorId() < 0 ? null : level.getEntity(k.getMercyActorId());
        return actor == null ? null : actor.getDisplayName().getString();
    }

    private static double anchorFrac(AbstractKuva k) {
        return k instanceof KuvaMasterEntity ? LICH_ANCHOR : THRALL_ANCHOR;
    }

    /** 按键在这一 tick 里被按了几下：小牌显示着就发请求（限流），没显示就丢掉 */
    private static void handleKey() {
        int presses = 0;
        while (KuvaKeyMappings.MERCY.consumeClick()) {
            presses++;
        }
        if (presses == 0 || selectedId < 0 || !selectedOwn) {
            return;
        }
        for (int i = 0; i < presses; i++) {
            long now = Util.getMillis();
            if (now - SENT[sentIdx] < 1000L) {
                break;
            }
            SENT[sentIdx] = now;
            sentIdx = (sentIdx + 1) % SENT.length;
            NetworkRegistryHandler.getChannel().sendToServer(new MercyRequestPacket(selectedId));
            if (!FMLEnvironment.production) {
                LogUtil.info("mercy request sent: entity " + selectedId);
            }
        }
    }

    private static void drainKey() {
        while (KuvaKeyMappings.MERCY.consumeClick()) {
            // 没进世界时按的不攒到进世界以后
        }
    }

    /** 语言、键位、资格人名字变了才重算小牌上的字 */
    private static void refreshTexts(Minecraft mc) {
        String lang = mc.options.languageCode;
        InputConstants.Key key = KuvaKeyMappings.MERCY.getKey();
        KeyModifier mod = KuvaKeyMappings.MERCY.getKeyModifier();
        if (!lang.equals(cachedLang) || !key.equals(cachedKey) || mod != cachedModifier) {
            cachedLang = lang;
            cachedKey = key;
            cachedModifier = mod;
            keyText = KuvaKeyMappings.MERCY.getTranslatedKeyMessage().getString();
            lichText = I18n.get("kuvalich.mercy.prompt.lich");
            thrallText = I18n.get("kuvalich.mercy.prompt.thrall");
            otherName = null;
        }
        if (selectedId >= 0 && !selectedOwn && mc.level != null
                && mc.level.getEntity(selectedId) instanceof AbstractKuva k) {
            String name = claimantName(k);
            if (name != null && !name.equals(otherName)) {
                otherName = name;
                otherText = I18n.get("kuvalich.mercy.prompt.other", name);
            }
        }
    }

    // ==================== 世界标记 ====================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Camera cam = event.getCamera();
        PROJ.set(event.getProjectionMatrix());
        VIEW.set(event.getPoseStack().last().pose());
        camX = cam.getPosition().x;
        camY = cam.getPosition().y;
        camZ = cam.getPosition().z;
        matricesValid = true;

        Minecraft mc = Minecraft.getInstance();
        if (DOWNED.isEmpty() || mc.options.hideGui || mc.player == null || MercyClient.hidesHud()) {
            return;
        }
        LEFT.set(cam.getLeftVector());
        UP.set(cam.getUpVector());
        LOOK.set(cam.getLookVector());
        float pt = event.getPartialTick();
        boolean additive = EffectsDisplayConfig.additiveGlow();

        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            Matrix4f m = event.getPoseStack().last().pose();
            RenderSystem.enableBlend();
            if (additive) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            // 第一遍照常被方块挡；第二遍关深度测试只画标记、淡一些，隔着墙也认得出在哪
            drawAll(m, mc.player, pt, additive, false);
            RenderSystem.disableDepthTest();
            drawAll(m, mc.player, pt, additive, true);
            RenderSystem.enableDepthTest();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawAll(Matrix4f m, LocalPlayer player, float pt, boolean additive, boolean xray) {
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0, n = DOWNED.size(); i < n; i++) {
            AbstractKuva k = DOWNED.get(i);
            if (k.isRemoved() || k.getMercyState() != MercyState.DOWNED) {
                continue;
            }
            boolean own = isOwn(k, player);
            boolean claimed = own || hasClaimant(k);
            double x = Mth.lerp(pt, k.xo, k.getX());
            double y = Mth.lerp(pt, k.yo, k.getY());
            double z = Mth.lerp(pt, k.zo, k.getZ());
            // 按这一帧模型实际的姿势算头顶：倒地动画还没接上（刚进视野、开发命令摆的倒地）时它还站着，
            // 碰撞箱却已经矮了，按碰撞箱算标记会埋进头里
            double h = LichNameplates.poseTop(k);
            // 名牌也按这个头顶摆，标记放在名牌上面，不压字
            double plateTop = LichNameplates.plateTopAboveHead(k);
            double lift = plateTop + MARKER_GAP - MARK_BOTTOM;
            double mx = x - camX, my = y + h + lift - camY, mz = z - camZ;
            double range = own ? DRAW_RANGE_OWN : DRAW_RANGE_OTHER;
            if (mx * mx + my * my + mz * mz > range * range) {
                continue;
            }
            float add = additive ? KuvaFxDirector.additiveScale(x, y + h, z) : 1.0F;
            int markRgb = own ? FxPalette.KUVA : FxPalette.ASH;
            float markA = (own ? 0.95F : 0.7F) * add * (xray ? XRAY : 1.0F);
            float fx = (float) mx, fy = (float) my, fz = (float) mz;
            float scale = markerScale(fx, fy, fz);
            // 放大时以底部为基准往上长（沿相机的上方向挪中心），不往实体身上压
            float shift = MARK_BOTTOM * (1.0F - scale);
            drawMarker(buf, m, fx + UP.x() * shift, fy + UP.y() * shift, fz + UP.z() * shift, scale, markRgb, markA, own);
            if (!xray && claimed) {
                // 锚点在身体里面，圆环往相机方向挪出身体表面，免得被模型挡住
                float ax = fx, ay = (float) (y + h * anchorFrac(k) - camY), az = fz;
                float pull = towardCamera(ax, ay, az, k.getBbWidth());
                float dist = Mth.sqrt(ax * ax + ay * ay + az * az);
                if (dist > 1.0E-4F) {
                    float f = 1.0F - pull / dist;
                    ax *= f;
                    ay *= f;
                    az *= f;
                }
                // 光束从标记底连到圆环顶（两端都沿相机的上方向量）；头顶有名牌时从头顶起，不从字中间穿过
                float sx = fx + UP.x() * MARK_BOTTOM, sy = fy + UP.y() * MARK_BOTTOM, sz = fz + UP.z() * MARK_BOTTOM;
                if (plateTop > 0.0D) {
                    sx = fx;
                    sy = (float) (y + h + BEAM_FROM_HEAD - camY);
                    sz = fz;
                }
                float ex = ax + UP.x() * RING_R, ey = ay + UP.y() * RING_R, ez = az + UP.z() * RING_R;
                int beamRgb = own ? FxPalette.KUVA_PALE : FxPalette.ASH;
                beam(buf, m, sx, sy, sz, ex, ey, ez, beamRgb, (own ? 0.45F : 0.3F) * add);
                float total = k.getMercyTotalSeconds() * 20.0F;
                float left = total > 0.0F ? Mth.clamp(k.getMercyRemainingTicks(pt) / total, 0.0F, 1.0F) : 1.0F;
                float leftA = 0.9F;
                if (k.getMercyRemainingTicks(pt) <= 200.0F) {
                    // 最后 10 秒：剩下那段 0.6 ↔ 1.0 按 2 Hz 呼吸
                    float phase = (KuvaFxDirector.clockMs() % 500L) / 500.0F;
                    leftA = 0.8F + 0.2F * Mth.cos(phase * Mth.TWO_PI);
                }
                ring(buf, m, ax, ay, az, left, leftA * add, 0.2F * add);
            }
        }
        BufferBuilder.RenderedBuffer rb = buf.endOrDiscardIfEmpty();
        if (rb != null) {
            BufferUploader.drawWithShader(rb);
        }
    }

    /**
     * 锚点（相机相对坐标）要往相机方向挪多远才在身体外面：碰撞箱半宽多一点，但不越过相机前 0.3 格
     */
    private static float towardCamera(float x, float y, float z, float bbWidth) {
        float dist = Mth.sqrt(x * x + y * y + z * z);
        return Math.max(0.0F, Math.min(bbWidth * 0.6F, dist - 0.3F));
    }

    /** 标记投影后不到屏幕高 0.046 时要放大多少倍（1 = 不放大） */
    private static float markerScale(float x, float y, float z) {
        float depth = x * LOOK.x() + y * LOOK.y() + z * LOOK.z();
        if (depth <= 0.05F) {
            return 1.0F;
        }
        float frac = MARK_HEIGHT * PROJ.m11() / (2.0F * depth);
        return frac >= MIN_SCREEN_H ? 1.0F : MIN_SCREEN_H / frac;
    }

    /** 晶体（竖长六边形，外层淡、里层亮）+ 三根细线挂三颗小菱形 */
    private static void drawMarker(BufferBuilder buf, Matrix4f m, float cx, float cy, float cz, float s, int rgb,
                                   float a, boolean own) {
        int core = FxCurve.lerpColor(rgb, own ? FxPalette.KUVA_PALE : FxPalette.BONE, 0.35F);
        float top = MARK_TOP * s;
        float ccy = top - CRYSTAL_H * 0.5F * s;
        float hh = CRYSTAL_H * 0.5F * s, hw = CRYSTAL_HW * s;
        hexagon(buf, m, cx, cy, cz, 0.0F, ccy, hw, hh, rgb, a * 0.55F);
        hexagon(buf, m, cx, cy, cz, 0.0F, ccy + hh * 0.12F, hw * 0.5F, hh * 0.62F, core, a);
        float bottom = ccy - hh;
        float shoulder = ccy - hh * 0.5F;
        for (int i = 0; i < 3; i++) {
            float dx = DROP_X[i] * s;
            float from = i == 1 ? bottom : shoulder;
            float dy = bottom - DROP_FALL[i] * s;
            float half = DROP_HALF * s;
            // 细线
            bquad(buf, m, cx, cy, cz, dx - THREAD_HW * s, from, dx + THREAD_HW * s, dy + half, rgb, a * 0.6F);
            // 小菱形
            bvert(buf, m, cx, cy, cz, dx, dy + half, rgb, a);
            bvert(buf, m, cx, cy, cz, dx + half, dy, rgb, a);
            bvert(buf, m, cx, cy, cz, dx, dy - half, rgb, a);
            bvert(buf, m, cx, cy, cz, dx, dy + half, rgb, a);
            bvert(buf, m, cx, cy, cz, dx, dy - half, rgb, a);
            bvert(buf, m, cx, cy, cz, dx - half, dy, rgb, a);
        }
    }

    /** 面向相机平面里的竖长六边形（上下两尖、左右两条竖边），扇形三角 */
    private static void hexagon(BufferBuilder buf, Matrix4f m, float ox, float oy, float oz, float x, float y,
                                float hw, float hh, int rgb, float a) {
        HEX[0] = x;
        HEX[1] = y + hh;
        HEX[2] = x + hw;
        HEX[3] = y + hh * 0.45F;
        HEX[4] = x + hw;
        HEX[5] = y - hh * 0.45F;
        HEX[6] = x;
        HEX[7] = y - hh;
        HEX[8] = x - hw;
        HEX[9] = y - hh * 0.45F;
        HEX[10] = x - hw;
        HEX[11] = y + hh * 0.45F;
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            bvert(buf, m, ox, oy, oz, x, y, rgb, a);
            bvert(buf, m, ox, oy, oz, HEX[i * 2], HEX[i * 2 + 1], rgb, a);
            bvert(buf, m, ox, oy, oz, HEX[j * 2], HEX[j * 2 + 1], rgb, a);
        }
    }

    /** 面向相机平面里的一个点：原点 (ox, oy, oz) 加上 右 × u + 上 × v（相机相对坐标） */
    private static void bvert(BufferBuilder buf, Matrix4f m, float ox, float oy, float oz, float u, float v, int rgb,
                              float a) {
        float px = ox - LEFT.x() * u + UP.x() * v;
        float py = oy - LEFT.y() * u + UP.y() * v;
        float pz = oz - LEFT.z() * u + UP.z() * v;
        vert(buf, m, px, py, pz, rgb, a);
    }

    /** 面向相机平面里的轴对齐矩形（两个三角） */
    private static void bquad(BufferBuilder buf, Matrix4f m, float ox, float oy, float oz, float u0, float v0,
                              float u1, float v1, int rgb, float a) {
        bvert(buf, m, ox, oy, oz, u0, v0, rgb, a);
        bvert(buf, m, ox, oy, oz, u1, v0, rgb, a);
        bvert(buf, m, ox, oy, oz, u1, v1, rgb, a);
        bvert(buf, m, ox, oy, oz, u0, v0, rgb, a);
        bvert(buf, m, ox, oy, oz, u1, v1, rgb, a);
        bvert(buf, m, ox, oy, oz, u0, v1, rgb, a);
    }

    private static void vert(BufferBuilder buf, Matrix4f m, float x, float y, float z, int rgb, float a) {
        int alpha = Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        buf.vertex(m, x, y, z).color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha).endVertex();
    }

    /** 光束：两端之间一条细带，宽度方向取「光束方向 × 视线」，从哪个角度看都是正面 */
    private static void beam(BufferBuilder buf, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             int rgb, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        // 宽度方向 = 光束方向 × 视线（视线取这段中点到相机的方向，近处也对）
        float vx = (x0 + x1) * 0.5F, vy = (y0 + y1) * 0.5F, vz = (z0 + z1) * 0.5F;
        float wx = dy * vz - dz * vy, wy = dz * vx - dx * vz, wz = dx * vy - dy * vx;
        float len = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (len < 1.0E-5F) {
            return;
        }
        float k = BEAM_HW / len;
        wx *= k;
        wy *= k;
        wz *= k;
        vert(buf, m, x0 - wx, y0 - wy, z0 - wz, rgb, a);
        vert(buf, m, x0 + wx, y0 + wy, z0 + wz, rgb, a);
        vert(buf, m, x1 + wx, y1 + wy, z1 + wz, rgb, a);
        vert(buf, m, x0 - wx, y0 - wy, z0 - wz, rgb, a);
        vert(buf, m, x1 + wx, y1 + wy, z1 + wz, rgb, a);
        vert(buf, m, x1 - wx, y1 - wy, z1 - wz, rgb, a);
    }

    /** 倒计时圈：从 12 点顺时针，前 {@code left} 圈是剩下的（{@code leftA}），其余是过去的（{@code goneA}） */
    private static void ring(BufferBuilder buf, Matrix4f m, float x, float y, float z, float left, float leftA,
                             float goneA) {
        int rgb = FxPalette.TECH;
        float split = left * Mth.TWO_PI;
        arc(buf, m, x, y, z, 0.0F, split, rgb, leftA);
        arc(buf, m, x, y, z, split, Mth.TWO_PI, rgb, goneA);
    }

    private static void arc(BufferBuilder buf, Matrix4f m, float x, float y, float z, float a0, float a1, int rgb,
                            float a) {
        if (a1 - a0 < 1.0E-4F || a <= 0.002F) {
            return;
        }
        int n = Math.max(2, Mth.ceil(RING_SEGMENTS * (a1 - a0) / Mth.TWO_PI));
        float r0 = RING_R - RING_HW, r1 = RING_R + RING_HW;
        float prevS = Mth.sin(a0), prevC = Mth.cos(a0);
        for (int i = 1; i <= n; i++) {
            float ang = a0 + (a1 - a0) * i / n;
            float s = Mth.sin(ang), c = Mth.cos(ang);
            // 角度从上方顺时针：u = sin（往右），v = cos（往上）
            bvert(buf, m, x, y, z, prevS * r0, prevC * r0, rgb, a);
            bvert(buf, m, x, y, z, prevS * r1, prevC * r1, rgb, a);
            bvert(buf, m, x, y, z, s * r1, c * r1, rgb, a);
            bvert(buf, m, x, y, z, prevS * r0, prevC * r0, rgb, a);
            bvert(buf, m, x, y, z, s * r1, c * r1, rgb, a);
            bvert(buf, m, x, y, z, s * r0, c * r0, rgb, a);
            prevS = s;
            prevC = c;
        }
    }

    // ==================== 小牌 ====================

    /** 覆盖层注册（MOD 总线） */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Overlay {

        private Overlay() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), OVERLAY_ID.getPath(), MercyPrompt::renderCard);
        }
    }

    private static void renderCard(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        long now = KuvaFxDirector.clockMs();
        float dt = Mth.clamp(now - cardLastMs, 0L, 100L);
        cardLastMs = now;
        boolean blocked = mc.options.hideGui || mc.screen != null || MercyClient.isActive() || mc.level == null;
        if (blocked) {
            cardAlpha = 0.0F;
            return;
        }
        if (selectedId >= 0 && (selectedId != cardId || selectedOwn != cardOwn)) {
            // 换了一只（或者身份变了）：直接换过去重新淡入
            cardId = selectedId;
            cardOwn = selectedOwn;
            cardAlpha = 0.0F;
        }
        boolean show = selectedId >= 0 && selectedId == cardId;
        cardAlpha = show ? Math.min(1.0F, cardAlpha + dt / CARD_IN_MS) : Math.max(0.0F, cardAlpha - dt / CARD_OUT_MS);
        if (cardAlpha <= 0.0F || cardId < 0 || !matricesValid) {
            return;
        }
        if (!(mc.level.getEntity(cardId) instanceof AbstractKuva k) || k.isRemoved()) {
            cardAlpha = 0.0F;
            return;
        }
        String text = cardOwn ? (k instanceof KuvaMasterEntity ? lichText : thrallText) : otherText;
        if (!cardOwn && otherName == null) {
            return;
        }
        // 锚点投到屏幕
        double x = Mth.lerp(partialTick, k.xo, k.getX());
        double y = Mth.lerp(partialTick, k.yo, k.getY()) + k.getBbHeight() * anchorFrac(k);
        double z = Mth.lerp(partialTick, k.zo, k.getZ());
        float rx = (float) (x - camX), ry = (float) (y - camY), rz = (float) (z - camZ);
        float dist = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist > 1.0E-4F) {
            // 和世界里的圆环挪到同一处，小牌才贴着圆环
            float f = 1.0F - towardCamera(rx, ry, rz, k.getBbWidth()) / dist;
            rx *= f;
            ry *= f;
            rz *= f;
        }
        TMP4.set(rx, ry, rz, 1.0F);
        VIEW.transform(TMP4);
        PROJ.transform(TMP4);
        if (TMP4.w() <= 0.0F) {
            return;
        }
        float ndcX = TMP4.x() / TMP4.w(), ndcY = TMP4.y() / TMP4.w();
        Window win = mc.getWindow();
        float physW = win.getWidth(), physH = win.getHeight();
        float sx = (ndcX + 1.0F) * 0.5F * physW;
        float sy = (1.0F - ndcY) * 0.5F * physH;
        // 圆环外缘在屏幕上离锚点多远：小牌从外缘再往右 0.0107H 起
        float ringPx = (RING_R + RING_HW) * PROJ.m11() / TMP4.w() * physH * 0.5F;
        drawCard(g, mc.font, win, sx + ringPx, sy, physH, cardOwn, text, cardAlpha);
    }

    /**
     * 画一块小牌（全部按物理像素量）：圆环外缘右边 0.0107H 起，竖直居中对齐锚点
     *
     * @param ax 圆环外缘最右点的屏幕 x（物理像素）
     * @param ay 锚点的屏幕 y（物理像素）
     */
    static void drawCard(GuiGraphics g, Font font, Window win, float ax, float ay, float physH, boolean own,
                         String text, float alpha) {
        float gs = (float) win.getGuiScale();
        // 汉字的位图是 16 像素的，按 1 倍（8 像素）画就糊成一团认不出：带宽字符的时候至少 2 倍
        int p = Math.max(hasWide(text) ? 2 : 1, Math.round(physH / 540.0F));
        int lw = GuiShapes.lineWidthPx();
        int textH = 8 * p;
        int padV = Math.max(1, Math.round(physH * 0.004F));
        int padH = Math.max(1, Math.round(physH * 0.0053F));
        int boxPad = Math.max(1, Math.round(physH * 0.002F));
        int cardH = textH + padV * 2;
        int labelW = font.width(text) * p;
        int boxW = own ? font.width(keyText) * p + boxPad * 2 + lw * 2 : 0;
        int gap = own ? p * 3 : 0;
        int cardW = padH * 2 + boxW + gap + labelW;
        int x0 = Math.round(ax + physH * 0.0107F);
        int y0 = Math.round(ay - cardH * 0.5F);

        int bg = own ? GuiShapes.argb(FxPalette.BONE, 0.92F * alpha) : GuiShapes.argb(CARD_OTHER_BG, 0.75F * alpha);
        int inkRgb = own ? CARD_INK : FxPalette.BONE;
        int ink = GuiShapes.argb(inkRgb, alpha);

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        GuiShapes.begin(g);
        GuiShapes.rect(g, x0, y0, x0 + cardW, y0 + cardH, bg);
        int tx = x0 + padH;
        if (own) {
            // 键名外面的细框：上下贴着牌的上下内边，左右留 0.2%H
            int bx0 = tx, bx1 = tx + boxW, by0 = y0 + padV - p, by1 = y0 + cardH - padV + p;
            GuiShapes.rect(g, bx0, by0, bx1, by0 + lw, ink);
            GuiShapes.rect(g, bx0, by1 - lw, bx1, by1, ink);
            GuiShapes.rect(g, bx0, by0 + lw, bx0 + lw, by1 - lw, ink);
            GuiShapes.rect(g, bx1 - lw, by0 + lw, bx1, by1 - lw, ink);
        }
        GuiShapes.end();
        if (alpha >= 0.02F) {
            int ty = y0 + padV;
            if (own) {
                text(g, font, keyText, tx + lw + boxPad, ty, p, ink);
                tx += boxW + gap;
            }
            text(g, font, text, tx, ty, p, ink);
        }
        g.pose().popPose();
    }

    /** 有没有汉字之类的宽字符（只看首个落在 CJK 区段的字，不分配） */
    static boolean hasWide(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            if (s.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }

    /** 在当前（物理像素）坐标下按 {@code p} 倍画一行字，不带阴影 */
    private static void text(GuiGraphics g, Font font, String s, float x, float y, int p, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** 调试命令用：现在选中的那只（-1 = 没有）和本地玩家能不能处决它 */
    static int selectedId() {
        return selectedId;
    }

    static boolean selectedOwn() {
        return selectedOwn;
    }
}
