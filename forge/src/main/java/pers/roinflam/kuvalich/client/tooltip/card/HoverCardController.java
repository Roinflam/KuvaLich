package pers.roinflam.kuvalich.client.tooltip.card;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.HoverCardConfig;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组卡悬停展开：鼠标停在容器界面格子里的模组卡上时，图标原地展开成整张卡。
 *
 * <p>卡按格子坐标锚定，不跟鼠标；鼠标离开这个格子就收，卡只是画在上面，旁边的格子照常悬停、照常点。
 * 不是容器界面（JEI 列表、配方页等）、不是模组卡、配置关掉时都不介入，照旧走悬浮面板。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class HoverCardController {

    private static final float TILT_TAU_MS = 90;
    /** 透视距离：4 个卡宽 */
    private static final float PERSP = 4;
    /** 展开完还要再算上的倾斜放大，见 {@link #tiltGrow} */
    private static final float TILT_D = 400;
    private static final float SIN12 = (float) Math.sin(Math.toRadians(12));

    private static final class Card {
        final Slot slot;
        ItemStack stack;
        final CardModel model;
        CardLayout layout;
        final long hoverStart;
        boolean opening = true;
        float p0;
        long t0;
        float b0;
        float tiltX, tiltY;
        final CardState st = new CardState();

        Card(Slot slot, ItemStack stack, CardModel model, long now) {
            this.slot = slot;
            this.stack = stack;
            this.model = model;
            this.hoverStart = now;
            this.t0 = now;
        }

        float progress(long now) {
            return opening ? CardState.clamp(p0 + (now - t0) / (float) CardState.OPEN_MS, 0, 1)
                    : CardState.clamp(p0 - (now - t0) / (float) CardState.CLOSE_MS, 0, 1);
        }

        void close(long now) {
            if (!opening) {
                return;
            }
            float p = progress(now);
            opening = false;
            p0 = p;
            b0 = CardState.hump(p);
            t0 = now;
        }

        void reopen(long now) {
            float p = progress(now);
            opening = true;
            p0 = p;
            t0 = now;
        }
    }

    private static AbstractContainerScreen<?> screen;
    private static Card cur;
    private static final List<Card> closing = new ArrayList<>();
    private static long lastFrame;
    private static Boolean fontsOk;
    private static long fontsCheckedAt;
    /** 画卡出过异常就不再展开，退回悬浮面板，免得每帧崩一次 */
    private static boolean broken;
    private static final Matrix4f TILT = new Matrix4f();

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private HoverCardController() {
    }

    // ---------------------------------------------------------------- 给 Mixin 用

    /** 展开中、收起中的卡所在的格子不画物品，卡自己在原处画同一张图标 */
    public static boolean hidesSlot(AbstractContainerScreen<?> s, Slot slot) {
        if (s != screen) {
            return false;
        }
        if (cur != null && cur.slot == slot) {
            return true;
        }
        for (int i = 0; i < closing.size(); i++) {
            if (closing.get(i).slot == slot) {
                return true;
            }
        }
        return false;
    }

    static void onResourcesReloaded() {
        fontsOk = null;
        broken = false;
        clear();
    }

    private static void clear() {
        cur = null;
        closing.clear();
        screen = null;
    }

    // ---------------------------------------------------------------- 悬停判定

    @SubscribeEvent
    public static void onHoverCardRenderPre(ScreenEvent.Render.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs) || !HoverCardConfig.enabled() || broken) {
            if (screen != null) {
                clear();
            }
            return;
        }
        if (acs != screen) {
            clear();
            screen = acs;
        }
        if (!acs.getMenu().getCarried().isEmpty()) {
            cur = null;
            closing.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        double mx = mouseGuiX(mc), my = mouseGuiY(mc);

        // 格子里的东西变了（被拿走、换了一张）就直接撤掉，不演收起
        if (cur != null && !ItemStack.matches(cur.stack, cur.slot.getItem())) {
            cur = null;
        } else if (cur != null) {
            cur.stack = cur.slot.getItem();
        }
        for (int i = closing.size() - 1; i >= 0; i--) {
            Card c = closing.get(i);
            if (!ItemStack.matches(c.stack, c.slot.getItem()) || c.progress(now) <= 0) {
                closing.remove(i);
            }
        }

        if (cur != null && overSlot(acs, cur.slot, mx, my)) {
            return;
        }
        if (cur != null) {
            cur.close(now);
            closing.add(cur);
            cur = null;
        }
        Slot hot = slotAt(acs, mx, my);
        if (hot == null) {
            return;
        }
        Card back = null;
        for (Card c : closing) {
            if (c.slot == hot) {
                back = c;
                break;
            }
        }
        if (back != null) {
            closing.remove(back);
            back.reopen(now);
            cur = back;
        } else {
            CardModel model = CardModel.of(hot.getItem());
            if (model != null) {
                cur = new Card(hot, hot.getItem(), model, now);
            }
        }
    }

    /** 鼠标下能展开的格子：激活、有东西、是模组卡 */
    private static Slot slotAt(AbstractContainerScreen<?> s, double mx, double my) {
        List<Slot> slots = s.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.isActive() && overSlot(s, slot, mx, my)) {
                ItemStack st = slot.getItem();
                return !st.isEmpty() && st.getItem() instanceof AbstractModule ? slot : null;
            }
        }
        return null;
    }

    /** 和原版 isHovering 同一个范围：16 像素格子四周各放 1 像素 */
    private static boolean overSlot(AbstractContainerScreen<?> s, Slot slot, double mx, double my) {
        double x = mx - s.getGuiLeft(), y = my - s.getGuiTop();
        return x >= slot.x - 1 && x < slot.x + 17 && y >= slot.y - 1 && y < slot.y + 17;
    }

    private static double mouseGuiX(Minecraft mc) {
        Window w = mc.getWindow();
        return mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth();
    }

    private static double mouseGuiY(Minecraft mc) {
        Window w = mc.getWindow();
        return mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight();
    }

    // ---------------------------------------------------------------- 位置与判定范围（界面像素）

    private static CardLayout layout(Card c) {
        Minecraft mc = Minecraft.getInstance();
        int gs = (int) mc.getWindow().getGuiScale();
        int size = HoverCardConfig.sizePercent();
        // 资源重载途中量到的可能还是旧字体，没加载上的过一会儿再量一次
        long now = Util.getMillis();
        if (fontsOk == null || (!fontsOk && now - fontsCheckedAt > 2000)) {
            fontsOk = CardFonts.available();
            fontsCheckedAt = now;
        }
        boolean fallback = !fontsOk;
        if (c.layout == null || !c.layout.matches(gs, size, fallback)) {
            c.layout = new CardLayout(c.model, gs, size, fallback);
        }
        return c.layout;
    }

    private static float tiltGrow(CardLayout l) {
        CardTier t = l.model.tier;
        float z = (50 + t.el + l.hf / 2 + Math.max(t.et, t.eb)) * SIN12;
        return TILT_D / (TILT_D - z);
    }

    /** 展开完时卡身中心：以格子中心为中心，按峰值大小夹进屏幕，四周留 3 像素 */
    private static float finalCenter(AbstractContainerScreen<?> s, Card c, CardLayout l, boolean xAxis) {
        Minecraft mc = Minecraft.getInstance();
        CardTier t = l.model.tier;
        float fs = l.ps / l.gs;
        float g = fs * (1 + CardState.SCALE_OVER) * tiltGrow(l);
        if (xAxis) {
            float cx = s.getGuiLeft() + c.slot.x + 8;
            float hx = (50 + t.el) * g;
            float sw = mc.getWindow().getGuiScaledWidth();
            return hx * 2 + 6 > sw ? sw / 2 : CardState.clamp(cx, 3 + hx, sw - 3 - hx);
        }
        float cy = s.getGuiTop() + c.slot.y + 8;
        float ht = (l.hf / 2 + t.et) * g, hb = (l.hf / 2 + t.eb) * g;
        float sh = mc.getWindow().getGuiScaledHeight();
        return ht + hb + 6 > sh ? sh / 2 : CardState.clamp(cy, 3 + ht, sh - 3 - hb);
    }

    // ---------------------------------------------------------------- 面板与状态

    @SubscribeEvent
    public static void onHoverCardTooltip(RenderTooltipEvent.Pre event) {
        if (screen == null || event.getItemStack().isEmpty()) {
            return;
        }
        ItemStack st = event.getItemStack();
        if (cur != null && cur.slot.getItem() == st) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHoverCardScreenOpening(ScreenEvent.Opening event) {
        clear();
    }

    @SubscribeEvent
    public static void onHoverCardScreenClosing(ScreenEvent.Closing event) {
        clear();
    }

    @SubscribeEvent
    public static void onHoverCardLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        CardPainter.release();
    }

    // ---------------------------------------------------------------- 绘制

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHoverCardRenderPost(ScreenEvent.Render.Post event) {
        if (screen == null || event.getScreen() != screen || (cur == null && closing.isEmpty())) {
            return;
        }
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        double mx = mouseGuiX(mc), my = mouseGuiY(mc);
        event.getGuiGraphics().flush();
        try {
            for (int i = 0; i < closing.size(); i++) {
                drawCard(screen, closing.get(i), now, dt, mx, my);
            }
            if (cur != null) {
                drawCard(screen, cur, now, dt, mx, my);
            }
        } catch (RuntimeException e) {
            LOGGER.error("Hover card rendering failed; falling back to the tooltip panel", e);
            broken = true;
            clear();
            CardDraw.abort();
            CardPainter.release();
        } finally {
            CardDraw.flush();
            RenderSystem.disableScissor();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawCard(AbstractContainerScreen<?> s, Card c, long now, float dt, double mx, double my) {
        CardLayout l = layout(c);
        float p = c.progress(now);
        if (!c.opening && p <= 0) {
            return;
        }
        float fcx = finalCenter(s, c, l, true), fcy = finalCenter(s, c, l, false);

        // 倾斜输入：鼠标相对格子中心，按格子半宽半高（8）归一，指数缓动；收起的卡缓回 0
        float k = 1 - (float) Math.exp(-dt / TILT_TAU_MS);
        float wantX = 0, wantY = 0;
        if (c.opening && HoverCardConfig.tilt()) {
            wantX = CardState.clamp((float) ((mx - (s.getGuiLeft() + c.slot.x + 8)) / 8), -1, 1);
            wantY = CardState.clamp((float) ((my - (s.getGuiTop() + c.slot.y + 8)) / 8), -1, 1);
        }
        c.tiltX += (wantX - c.tiltX) * k;
        c.tiltY += (wantY - c.tiltY) * k;

        float bump = c.opening ? CardState.hump(p) : (c.p0 > 0 ? c.b0 * (p / c.p0) * (p / c.p0) : 0);
        long doneAt = c.t0 + (long) ((1 - c.p0) * CardState.OPEN_MS);
        CardState st = c.st;
        st.compute(l, p, c.opening, bump, now - doneAt, c.tiltX, c.tiltY, HoverCardConfig.tilt());

        CardPainter.paint(l, st, now, c.hoverStart);

        int gs = l.gs;
        float slotCx = (s.getGuiLeft() + c.slot.x + 8) * gs, slotCy = (s.getGuiTop() + c.slot.y + 8) * gs;
        float ocx = CardState.lerp(slotCx, fcx * gs, st.e), ocy = CardState.lerp(slotCy, fcy * gs, st.e);
        blit(l, st, ocx, ocy);
        if (p < 1) {
            drawIcon(c, l, st, ocx, ocy);
        }
    }

    /** 把画布贴到屏幕：显示范围中点压在 (ocx, ocy)（物理像素），展开完且不倾斜时一比一、对齐整像素 */
    private static void blit(CardLayout l, CardState st, float ocx, float ocy) {
        TextureTarget tgt = CardPainter.target();
        if (tgt == null) {
            return;
        }
        Window win = Minecraft.getInstance().getWindow();
        int gs = l.gs;
        float u = gs * st.s;
        float q = u / l.ps;
        float mid = (st.v0 + st.v1) / 2;
        float ax = l.ox + CardLayout.W / 2 * l.ps, ay = l.oy + mid * l.ps;
        boolean flat = st.flat();

        // 卡只在一个矩形里可见：起初是图标内侧，展开过程中放到卡身外加边框
        float k = (float) Math.sqrt(st.s / CardState.S0);
        int n = Math.max(2, Math.round(16 * gs * k));
        float iq = n / 64.0F;
        int iconL = Math.round(ocx + (-1 - 50) * u);
        int iconR = Math.max(iconL + n, Math.round(ocx + (101 - 50) * u));
        int iconT = Math.round(ocy + (st.v0 - mid) * u - 15 * iq);
        int iconB = Math.max(iconT + n, Math.round(ocy + (st.v1 - mid) * u + 15 * iq));
        float m = CardState.smooth(0.3F, 0.75F, st.p);
        float hin = (float) Math.ceil(3 * iq) * (1 - CardState.smooth(0, 0.25F, st.p));
        CardTier t = l.model.tier;
        float ext = (Math.max(Math.max(t.et, t.eb), t.el) + 10) * u;
        float vt = CardState.lerp(iconT + 16.5F * iq, ocy + (st.v0 - mid) * u - ext, m);
        float vb = CardState.lerp(iconB - 22.5F * iq, ocy + (st.v1 - mid) * u + ext, m);
        float vl = iconL + hin - m * ext, vr = iconR - hin + m * ext;
        int sx0 = Math.max(0, (int) Math.floor(vl)), sx1 = Math.min(win.getWidth(), (int) Math.ceil(vr));
        int sy0 = Math.max(0, (int) Math.floor(vt)), sy1 = Math.min(win.getHeight(), (int) Math.ceil(vb));
        if (sx1 <= sx0 || sy1 <= sy0) {
            return;
        }

        int tex = tgt.getColorTextureId();
        RenderSystem.bindTexture(tex);
        if (flat) {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        } else {
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }

        RenderSystem.enableScissor(sx0, win.getHeight() - sy1, sx1 - sx0, sy1 - sy0);
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        CardDraw.blendPremultiplied();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        float inv = 1.0F / gs;
        float cx, cy;
        if (flat) {
            cx = Math.round(ocx - ax) + ax;
            cy = Math.round(ocy - ay) + ay;
            q = 1;
            mv.scale(inv, inv, 1);
            mv.translate(cx, cy, 0);
        } else {
            mv.mulPoseMatrix(tiltMatrix(st, ocx, ocy, inv, 100 * u * PERSP));
        }
        RenderSystem.applyModelViewMatrix();
        try {
            float tw = tgt.width, th = tgt.height;
            CardDraw.textureId(tex);
            CardDraw.blit((0 - ax) * q, (0 - ay) * q, (l.cw - ax) * q, (l.ch - ay) * q,
                    0, 1, l.cw / tw, 1 - l.ch / th, 0xFFFFFF, 1);
            CardDraw.flush();
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableScissor();
        }
    }

    /**
     * 绕卡中心转 rx、ry 再做透视（视距 d 物理像素），平移到 (cx, cy)，最后缩回界面像素。
     * 顶点给的是相对中心的物理像素，z 恒为 0；w 由 z 算出来交给 GPU 做透视除法，贴图也就按透视插值。
     */
    private static Matrix4f tiltMatrix(CardState st, float cx, float cy, float inv, float d) {
        float cp = (float) Math.cos(st.ry), sp = (float) Math.sin(st.ry);
        float ct = (float) Math.cos(st.rx), stt = (float) Math.sin(st.rx);
        // R = Ry * Rx
        float r00 = cp, r01 = sp * stt;
        float r10 = 0, r11 = ct;
        float r20 = -sp, r21 = cp * stt;
        float a = -r20 / d, b = -r21 / d;
        float zc = 0;
        // JOML 按列给：m<列><行>
        return TILT.set(
                (r00 + cx * a) * inv, (r10 + cy * a) * inv, zc * a, a,
                (r01 + cx * b) * inv, (r11 + cy * b) * inv, zc * b, b,
                0, 0, 0, 0,
                cx * inv, cy * inv, zc, 1);
    }

    /**
     * 图标各块：底板等比放大并淡出，边框九宫格撑开。都是最近邻、整像素，第 0 帧和格子里画的图标逐像素相同。
     */
    private static void drawIcon(Card c, CardLayout l, CardState st, float ocx, float ocy) {
        int gs = l.gs;
        float u = gs * st.s;
        float mid = (st.v0 + st.v1) / 2;
        float k = (float) Math.sqrt(st.s / CardState.S0);
        int n = Math.max(2, Math.round(16 * gs * k));
        float q = n / 64.0F;
        int L = Math.round(ocx + (-1 - 50) * u);
        int R = Math.max(L + n, Math.round(ocx + (101 - 50) * u));
        int T = Math.round(ocy + (st.v0 - mid) * u - 15 * q);
        int B = Math.max(T + n, Math.round(ocy + (st.v1 - mid) * u + 15 * q));

        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        CardDraw.blendNormal();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.scale(1.0F / gs, 1.0F / gs, 1);
        RenderSystem.applyModelViewMatrix();
        try {
            float pa = 1 - CardState.smooth(0, 0.34F, st.p);
            if (pa > 0.003F) {
                int N = R - L;
                int y0 = T + Math.round((B - T - N) / 2.0F);
                CardDraw.texture(CardTextures.iconCore(c.model), false);
                CardDraw.blitAll(L, y0, L + N, y0 + N, 0xFFFFFF, pa);
            }
            float ra = 1 - CardState.smooth(0.6F, 1, st.p);
            if (ra > 0.003F) {
                int c1 = Math.round(22 * q), c2 = n - c1, cw = c2 - c1, r1 = Math.round(30 * q), bh = n - r1;
                int gL = Math.round((L + R - cw) / 2.0F), xR = R - c1, yB = B - bh;
                CardDraw.texture(CardTextures.iconRing(c.model), false);
                for (int row = 0; row < 2; row++) {
                    int sy0 = row == 0 ? 0 : r1, h = row == 0 ? r1 : bh, dy = row == 0 ? T : yB;
                    put(n, 0, sy0, c1, h, L, dy, c1, h, ra);
                    put(n, c1, sy0, cw, h, gL, dy, cw, h, ra);
                    put(n, c2, sy0, c1, h, xR, dy, c1, h, ra);
                    put(n, c1 - 1, sy0, 1, h, L + c1, dy, gL - L - c1, h, ra);
                    put(n, c2, sy0, 1, h, gL + cw, dy, xR - gL - cw, h, ra);
                }
                put(n, 0, r1 - 1, c1, 1, L, T + r1, c1, yB - T - r1, ra);
                put(n, c2, r1 - 1, c1, 1, xR, T + r1, c1, yB - T - r1, ra);
            }
            CardDraw.flush();
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 从放大到 n×n 的图标上取 (sx, sy, sw, sh) 一块，贴到 (dx, dy, dw, dh) */
    private static void put(int n, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh, float a) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
            return;
        }
        float fn = n;
        CardDraw.blit(dx, dy, dx + dw, dy + dh, sx / fn, sy / fn, (sx + sw) / fn, (sy + sh) / fn, 0xFFFFFF, a);
    }

    // ---------------------------------------------------------------- 资源重载

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class HoverCardReload {
        private HoverCardReload() {
        }

        @SubscribeEvent
        public static void onRegisterHoverCardReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> {
                CardTextures.reset();
                onResourcesReloaded();
            });
        }
    }
}
