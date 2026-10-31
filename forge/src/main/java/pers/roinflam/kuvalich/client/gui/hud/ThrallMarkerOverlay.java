package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 奴仆头顶的路标（可选，{@code thrall_marker} 默认关）：一枚赤毒晶体剪影，下面红字写距离
 *
 * <ul>
 *   <li>挂在奴仆头顶上方 0.6 格处，投到屏幕上画，大小固定（1080p 下 32×32 像素，按屏高等比缩放）；</li>
 *   <li>只给 32 格内、离自己最近的 12 只；每 5 tick 重新挑一次、看一次有没有被挡，被挡住的半透明；</li>
 *   <li>颜色赤毒红 α 0.9，距离字高为屏高的 1.2%；本体在淡出时路标跟着淡。</li>
 * </ul>
 *
 * <p>世界坐标到屏幕的换算用世界渲染那一帧的视图、投影矩阵（{@code RenderLevelStageEvent} 里记下来），
 * 所以视角晃动、改视野都对得上。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ThrallMarkerOverlay {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID, "textures/gui/thrall_marker.png");
    private static final int MAX_MARKERS = 12;
    private static final double RANGE = 32.0D;
    private static final float HEAD_GAP = 0.6F;
    private static final float ALPHA_SEEN = 0.9F;
    private static final float ALPHA_HIDDEN = 0.45F;

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static double camX;
    private static double camY;
    private static double camZ;
    private static boolean matricesValid;

    /** 每 5 tick 挑出来的奴仆：id、看不看得见 */
    private static final int[] IDS = new int[MAX_MARKERS];
    private static final boolean[] VISIBLE = new boolean[MAX_MARKERS];
    private static final double[] DIST = new double[MAX_MARKERS];
    private static int count;
    private static int ticks;

    /** 距离文字按整数米缓存，换语言时清 */
    private static final String[] DIST_TEXT = new String[(int) RANGE + 2];
    private static String textLang = "";

    private ThrallMarkerOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !EffectsDisplayConfig.thrallMarker()) {
            return;
        }
        VIEW.set(event.getPoseStack().last().pose());
        PROJ.set(event.getProjectionMatrix());
        Vec3 cam = event.getCamera().getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        matricesValid = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 5 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || !EffectsDisplayConfig.thrallMarker()) {
            count = 0;
            return;
        }
        // 按距离插入排序，只留最近的 12 只
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof KuvaSlaveEntity) || !e.isAlive()) {
                continue;
            }
            double d = e.distanceTo(player);
            if (d > RANGE) {
                continue;
            }
            int at = n;
            while (at > 0 && DIST[at - 1] > d) {
                at--;
            }
            if (at >= MAX_MARKERS) {
                continue;
            }
            int last = Math.min(n, MAX_MARKERS - 1);
            for (int i = last; i > at; i--) {
                IDS[i] = IDS[i - 1];
                DIST[i] = DIST[i - 1];
            }
            IDS[at] = e.getId();
            DIST[at] = d;
            if (n < MAX_MARKERS) {
                n++;
            }
        }
        for (int i = 0; i < n; i++) {
            Entity e = mc.level.getEntity(IDS[i]);
            VISIBLE[i] = e != null && player.hasLineOfSight(e);
        }
        count = n;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        count = 0;
        matricesValid = false;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (count == 0 || !matricesValid || !EffectsDisplayConfig.thrallMarker()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Font font = mc.font;
        String lang = mc.getLanguageManager().getSelected();
        if (!lang.equals(textLang)) {
            textLang = lang;
            Arrays.fill(DIST_TEXT, null);
        }
        float size = 32.0F * height / 1080.0F;
        float textScale = 0.012F * height / 8.0F;
        float r = ((FxPalette.KUVA >> 16) & 0xFF) / 255.0F;
        float gg = ((FxPalette.KUVA >> 8) & 0xFF) / 255.0F;
        float b = (FxPalette.KUVA & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < count; i++) {
            Entity e = mc.level.getEntity(IDS[i]);
            if (e == null || !e.isAlive()) {
                continue;
            }
            float fade = LichRenderState.alpha(e, partialTick);
            if (fade <= 0.02F) {
                continue;
            }
            double x = Mth.lerp(partialTick, e.xo, e.getX());
            double y = Mth.lerp(partialTick, e.yo, e.getY()) + e.getBbHeight() + HEAD_GAP;
            double z = Mth.lerp(partialTick, e.zo, e.getZ());
            SCRATCH.set((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1.0F);
            VIEW.transform(SCRATCH);
            PROJ.transform(SCRATCH);
            if (SCRATCH.w() <= 0.05F) {
                continue;
            }
            float sx = (SCRATCH.x() / SCRATCH.w() + 1.0F) * 0.5F * width;
            float sy = (1.0F - SCRATCH.y() / SCRATCH.w()) * 0.5F * height;
            if (sx < -size || sx > width + size || sy < -size || sy > height + size) {
                continue;
            }
            float a = (VISIBLE[i] ? ALPHA_SEEN : ALPHA_HIDDEN) * fade;

            g.pose().pushPose();
            g.pose().translate(sx - size * 0.5F, sy - size * 0.5F, 0.0F);
            g.pose().scale(size / 32.0F, size / 32.0F, 1.0F);
            g.setColor(r, gg, b, a);
            g.blit(TEXTURE, 0, 0, 0.0F, 0.0F, 32, 32, 32, 32);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.pose().popPose();

            int meters = (int) Math.min(DIST_TEXT.length - 1, Math.round(e.distanceTo(mc.player)));
            String text = DIST_TEXT[meters];
            if (text == null) {
                text = I18n.get("kuvalich.marker.distance", meters);
                DIST_TEXT[meters] = text;
            }
            int alphaByte = Math.max(4, Math.round(a * 255.0F));
            g.pose().pushPose();
            g.pose().translate(sx, sy + size * 0.5F + 1.0F, 0.0F);
            g.pose().scale(textScale, textScale, 1.0F);
            g.drawString(font, text, -font.width(text) / 2, 0, (alphaByte << 24) | FxPalette.KUVA, true);
            g.pose().popPose();
        }
        g.flush();
        RenderSystem.disableBlend();
    }

    /** 覆盖层注册在模组总线上（类名和同包的另一个覆盖层错开：Forge 按「类名_方法名_事件」生成调用类，撞名会只调其中一个） */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class MarkerRegistration {
        private MarkerRegistration() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("thrall_marker", ThrallMarkerOverlay::render);
        }
    }
}
