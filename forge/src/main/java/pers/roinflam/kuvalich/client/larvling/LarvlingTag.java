package pers.roinflam.kuvalich.client.larvling;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.HoloShapes;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.client.mercy.MercyPrompt;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒地幼体的引线标注（屏幕覆盖层）：头的中心一颗小菱形，一根 45° 细线斜拉到头的右上方，
 * 一条底线托住「玄骸武器」小标题、武器贴图、武器名、「Lv 18 · 火焰」；底线本身就是倒计时（剩下的一段粗亮、
 * 过去的一段细暗，往引线那头缩），秒数挂在底线尾巴上，最后 10 秒变赤毒红、按 2 Hz 呼吸；处决提示在底线下面。
 *
 * <p><b>位置</b>：底线和头顶齐平，信息块整块在头的右边，右边放不下就翻到左边；上下夹进安全区
 * （顶上留 3% 屏高，底下让开快捷栏、血条那几行），近处也不会顶出画面，不往天上摞。</p>
 *
 * <p><b>远近</b>：12 格内完整；11~12 格完整版淡出、12~13 格单行版（贴图 + 等级 + 元素图形 + 秒数）淡入，
 * 两版不同时出现；20~24 格整个淡出；锚点出了画面在 6% 屏高内淡掉。</p>
 *
 * <p><b>像素</b>：姿态缩成 1 / 界面倍数，全按物理像素画。字是原版字体的整数倍 {@code p = max(2, round(屏高 / 540))}
 * （汉字至少 2 倍，16 像素的位图 1:1 不糊），线宽 {@link GuiShapes#lineWidthPx()}，斜线按线宽一格一格走台阶，
 * 武器贴图按贴图像素的整数倍放大，坐标全是整数。</p>
 *
 * <p><b>处决提示</b>：资格人「[键] 诞生 怜悯之击」、别人的猎物「某某 的猎物」跟着 {@link MercyPrompt} 选中的那只淡入淡出
 * （处决键也只对它发请求）；没人能处决（打倒它的人和它找的人都已经有玄骸）时，自己有玄骸的玩家在 8 格内看着它会看到「你已有玄骸」。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LarvlingTag {

    /** 覆盖层 id */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "larvling_tag");

    /** 每 tick 收集这么远以内的幼体（比画的范围大一点，淡出那段也要跟着） */
    private static final double SCAN_RANGE = 26.0D;
    /** 超过这个距离不画；从 {@link #FADE_FROM} 起淡出 */
    private static final double RANGE = 24.0D;
    private static final double FADE_FROM = 20.0D;
    /** 完整版在这一段淡出，单行版在下一段淡入 */
    private static final double FULL_OUT_FROM = 11.0D;
    private static final double COMPACT_FROM = 12.0D;
    private static final double COMPACT_FULL = 13.0D;
    /** 「你已有玄骸」：8 格内、视线和锚点夹角不超过 30° 才显示（同别人的猎物） */
    private static final double LICH_PROMPT_RANGE = 8.0D;
    private static final double LICH_PROMPT_COS = Math.cos(Math.toRadians(30.0D));
    /** 头的半边长（格）：头部包围盒按锚点（头的中心）外扩这么多投到屏幕，信息块不压在头上 */
    private static final float HEAD_HALF = 0.2F;
    /** 锚点出了画面，在这么多屏幕高度以内淡掉 */
    private static final float OFFSCREEN_FADE = 0.06F;

    private static final float APPEAR_MS = 340.0F;
    private static final float HIDE_MS = 160.0F;
    private static final float PROMPT_IN_MS = 80.0F;
    private static final float PROMPT_OUT_MS = 100.0F;
    private static final float GLASS_ALPHA = 0.62F;

    /** 7 种元素各一个 7×7 字像素的小图形（每行低 7 位，最高位是最左一格），形状拉开，颜色以外也认得出 */
    private static final Map<String, int[]> GLYPHS = new HashMap<>();

    static {
        glyph("fire", "..#....", "..##...", ".###.#.", ".#####.", "##.####", "##..###", ".#####.");
        glyph("ice", "...#...", ".#.#.#.", "..###..", "###.###", "..###..", ".#.#.#.", "...#...");
        glyph("poison", "...#...", "..###..", "..###..", ".#####.", ".#.###.", ".#####.", "..###..");
        glyph("electricity", "....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##....");
        glyph("impact", "...#...", "..###..", ".##.##.", "###.###", ".##.##.", "..###..", "...#...");
        glyph("magnetic", "##...##", ".......", "##...##", "##...##", "##...##", ".#####.", "..###..");
        glyph("radiation", "##...##", "###.###", ".##.##.", "...#...", ".......", "..###..", "..###..");
    }

    private static final class Slot {
        float show;
        float lichPrompt;
        long lastMs;
    }

    private static final List<KuvaLarvlingEntity> NEAR = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<Slot> SLOTS = new Int2ObjectOpenHashMap<>();
    private static long lastPruneMs;

    /** 随语言变的字 */
    private static String lang = "";
    private static String caption = "";
    private static String hasLichText = "";
    private static String[] weaponNames = new String[0];
    private static final Map<String, String> ELEMENT_NAMES = new HashMap<>();
    private static final Int2ObjectOpenHashMap<String> LEVEL_TEXT = new Int2ObjectOpenHashMap<>();
    private static ItemStack[] stacks = new ItemStack[0];

    private static final double[] AW = new double[3];
    private static final float[] SP = new float[3];
    private static final Frame F = new Frame();

    private LarvlingTag() {
    }

    /** 元素小图形（7 行，每行低 7 位、最高位是最左一格）；不认识的元素返回 null。玄骸档案的武器卡也用这一套 */
    @Nullable
    public static int[] elementGlyph(String type) {
        return GLYPHS.get(type);
    }

    private static void glyph(String type, String... rows) {
        int[] mask = new int[7];
        for (int j = 0; j < 7; j++) {
            for (int i = 0; i < 7; i++) {
                if (rows[j].charAt(i) == '#') {
                    mask[j] |= 1 << (6 - i);
                }
            }
        }
        GLYPHS.put(type, mask);
    }

    // ==================== 收集 ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        NEAR.clear();
        if (mc.player == null || mc.level == null) {
            return;
        }
        double r2 = SCAN_RANGE * SCAN_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof KuvaLarvlingEntity l && l.distanceToSqr(mc.player) <= r2) {
                NEAR.add(l);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NEAR.clear();
        SLOTS.clear();
    }

    /** 覆盖层注册（MOD 总线） */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Overlay {

        private Overlay() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), OVERLAY_ID.getPath(), LarvlingTag::render);
        }
    }

    // ==================== 每帧 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        long now = KuvaFxDirector.clockMs();
        prune(now);
        if (player == null || mc.level == null || NEAR.isEmpty()) {
            return;
        }
        boolean blocked = mc.options.hideGui || mc.screen != null || MercyClient.isActive() || MercyClient.hidesHud();
        refreshTexts(mc);
        for (int i = 0, n = NEAR.size(); i < n; i++) {
            KuvaLarvlingEntity l = NEAR.get(i);
            MercyState state = l.getMercyState();
            boolean want = state == MercyState.DOWNED && l.isAlive() && !l.isRemoved();
            Slot s = SLOTS.get(l.getId());
            if (s == null) {
                if (!want) {
                    continue;
                }
                s = new Slot();
                s.lastMs = now;
                SLOTS.put(l.getId(), s);
            }
            float dt = Mth.clamp(now - s.lastMs, 0L, 100L);
            s.lastMs = now;
            if (state == MercyState.EXECUTING) {
                // 开始处决就直接收，不留在镜头里
                s.show = 0.0F;
            } else {
                s.show = want ? Math.min(1.0F, s.show + dt / APPEAR_MS) : Math.max(0.0F, s.show - dt / HIDE_MS);
            }
            boolean lichWant = want && l.getMercyActorId() < 0 && ClientLichState.hasLich() && lookingAt(player, l, pt);
            s.lichPrompt = lichWant ? Math.min(1.0F, s.lichPrompt + dt / PROMPT_IN_MS)
                    : Math.max(0.0F, s.lichPrompt - dt / PROMPT_OUT_MS);
            if (s.show <= 0.0F) {
                if (!want) {
                    SLOTS.remove(l.getId());
                }
                continue;
            }
            if (!blocked && !l.isInvisibleTo(player)) {
                draw(g, mc, player, l, s, pt, now);
            }
        }
    }

    /** 自己 8 格内、视线和锚点夹角不超过 30°（「你已有玄骸」用；资格人和别人的猎物走 MercyPrompt 的选择） */
    private static boolean lookingAt(LocalPlayer player, KuvaLarvlingEntity l, float pt) {
        MercyPrompt.anchorOf(l, pt, AW);
        double dx = AW[0] - player.getX(), dy = AW[1] - player.getEyeY(), dz = AW[2] - player.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > LICH_PROMPT_RANGE) {
            return false;
        }
        if (d < 1.0E-4D) {
            return true;
        }
        Vec3 look = player.getViewVector(pt);
        return (dx * look.x + dy * look.y + dz * look.z) / d >= LICH_PROMPT_COS;
    }

    private static void draw(GuiGraphics g, Minecraft mc, LocalPlayer player, KuvaLarvlingEntity l, Slot s, float pt,
                             long now) {
        ItemStack stack = stackOf(l.getWeaponIndex());
        int[] mask = GLYPHS.get(l.getWeaponType());
        if (stack == null || mask == null) {
            return;
        }
        Window win = mc.getWindow();
        int w = win.getWidth(), h = win.getHeight();
        int gs = Math.max(1, (int) Math.round(win.getGuiScale()));

        // 锚点（头的中心）投到屏幕
        MercyPrompt.anchorOf(l, pt, AW);
        if (!MercyPrompt.toScreen(AW[0], AW[1], AW[2], SP)) {
            return;
        }
        float ax = SP[0], ay = SP[1];
        float out = Math.max(Math.max(0.0F, Math.max(-ax, ax - w)), Math.max(-ay, ay - h));
        float onScreen = Mth.clamp(1.0F - out / (OFFSCREEN_FADE * h), 0.0F, 1.0F);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(pt, l.xo, l.getX()) - cam.x;
        double ey = Mth.lerp(pt, l.yo, l.getY()) - cam.y;
        double ez = Mth.lerp(pt, l.zo, l.getZ()) - cam.z;
        double dist = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (dist > RANGE) {
            return;
        }
        float distA = (float) (dist > FADE_FROM ? (RANGE - dist) / (RANGE - FADE_FROM) : 1.0D) * onScreen
                * LichRenderState.alpha(l, pt);
        if (distA <= 0.003F) {
            return;
        }

        Frame f = F;
        f.g = g;
        f.font = mc.font;
        f.p = Math.max(2, Math.round(h / 540.0F));
        f.lw = GuiShapes.lineWidthPx();
        f.safeL = Math.round(w * 0.02F);
        f.safeR = w - Math.round(w * 0.02F);
        f.safeT = Math.round(h * 0.03F);
        f.safeB = h - 50 * gs - 2 * f.p;
        f.ax = Math.round(ax);
        f.ay = Math.round(ay);
        headBox(f);

        int actor = l.getMercyActorId();
        f.own = actor >= 0 && actor == player.getId();
        f.show = s.show;
        f.now = now;
        f.stack = stack;
        f.sprite = mc.getItemRenderer().getModel(stack, mc.level, null, 0).getParticleIcon();
        int tex = Math.max(1, f.sprite.contents().width());
        f.icon = tex * Math.max(1, (16 * f.p) / tex);
        f.name = weaponName(l.getWeaponIndex(), stack);
        f.lv = levelText(l.getWeaponLevel());
        String type = l.getWeaponType();
        f.elName = elementName(type);
        f.elRgb = KuvaPalette.element(type);
        f.elMask = mask;
        f.caption = caption;

        float remaining = l.getMercyRemainingTicks(pt);
        int totalSecs = Math.max(1, l.getMercyTotalSeconds());
        f.secs = Integer.toString(Math.max(0, Mth.ceil(remaining / 20.0F - 1.0E-4F)));
        f.secReserve = f.w(Integer.toString(totalSecs));
        f.frac = Mth.clamp(remaining / (totalSecs * 20.0F), 0.0F, 1.0F);
        f.urgent = remaining <= 200.0F;
        f.urgentA = f.urgent ? 0.8F + 0.2F * Mth.cos((now % 500L) / 500.0F * Mth.TWO_PI) : 1.0F;

        // 处决提示：资格人 / 别人的猎物跟着 MercyPrompt 选中的那只；没人能处决时自己有玄骸的看到「你已有玄骸」
        f.promptKey = false;
        f.promptText = null;
        f.promptA = 0.0F;
        if (actor >= 0) {
            float a = MercyPrompt.promptAlpha(l.getId());
            if (a > 0.0F) {
                if (MercyPrompt.promptOwn(l.getId())) {
                    f.promptKey = true;
                    f.promptText = MercyPrompt.larvlingText();
                    f.promptA = a;
                } else {
                    f.promptText = MercyPrompt.otherText(l);
                    f.promptA = f.promptText == null ? 0.0F : a;
                }
            }
        } else if (s.lichPrompt > 0.0F) {
            f.promptText = hasLichText;
            f.promptA = s.lichPrompt;
        }

        float fullA = 1.0F - seg(dist, FULL_OUT_FROM, COMPACT_FROM);
        float compactA = seg(dist, COMPACT_FROM, COMPACT_FULL);

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        try {
            if (fullA > 0.004F) {
                form(f, false, distA * fullA);
            }
            if (compactA > 0.004F) {
                form(f, true, distA * compactA);
            }
        } finally {
            g.pose().popPose();
        }
    }

    /** 头部包围盒（锚点外扩 {@link #HEAD_HALF} 的方块 8 个角）投到屏幕的外接矩形，投不出来就退成锚点那一点 */
    private static void headBox(Frame f) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        boolean any = false;
        for (int c = 0; c < 8; c++) {
            double cx = AW[0] + ((c & 1) == 0 ? -HEAD_HALF : HEAD_HALF);
            double cy = AW[1] + ((c & 2) == 0 ? -HEAD_HALF : HEAD_HALF);
            double cz = AW[2] + ((c & 4) == 0 ? -HEAD_HALF : HEAD_HALF);
            if (MercyPrompt.toScreen(cx, cy, cz, SP)) {
                any = true;
                x0 = Math.min(x0, SP[0]);
                y0 = Math.min(y0, SP[1]);
                x1 = Math.max(x1, SP[0]);
                y1 = Math.max(y1, SP[1]);
            }
        }
        if (!any) {
            x0 = x1 = f.ax;
            y0 = y1 = f.ay;
        }
        f.hbX0 = x0;
        f.hbY0 = y0;
        f.hbX1 = x1;
        f.hbY1 = y1;
    }

    // ==================== 排版和画 ====================

    /** 一帧里画一只用到的量（只在渲染线程用，复用一个实例） */
    private static final class Frame {
        GuiGraphics g;
        Font font;
        int p;
        int lw;
        int safeL;
        int safeR;
        int safeT;
        int safeB;
        int ax;
        int ay;
        float hbX0;
        float hbY0;
        float hbX1;
        float hbY1;
        boolean own;
        float show;
        long now;
        ItemStack stack;
        TextureAtlasSprite sprite;
        int icon;
        String name;
        String lv;
        String elName;
        int elRgb;
        int[] elMask;
        String caption;
        String secs;
        int secReserve;
        float frac;
        boolean urgent;
        float urgentA;
        boolean promptKey;
        @Nullable
        String promptText;
        float promptA;

        /** 字墨宽（物理像素）：原版字宽含末字后面那 1 像素字距，去掉 */
        int w(String s) {
            return s.isEmpty() ? 0 : (font.width(s) - 1) * p;
        }

        /** 字墨高：带汉字的行 8 字像素，纯数字字母 7 */
        int h(String s) {
            return (hasWide(s) ? 8 : 7) * p;
        }

        /** 放进 8 字像素高的行里：纯数字字母的中线在 3.5，汉字在 4，差半格往下取整 */
        int rowOff(String s) {
            return hasWide(s) ? 0 : p / 2;
        }
    }

    /**
     * 画一版（完整或单行）。所有坐标是整数物理像素；{@code formA} 已经乘过距离、出画面的淡出
     */
    private static void form(Frame f, boolean compact, float formA) {
        int p = f.p, lw = f.lw;
        float s = f.show;
        float a = formA;
        if (a <= 0.003F) {
            return;
        }
        int accent = f.own ? FxPalette.KUVA : FxPalette.ASH;
        int ax = f.ax, ay = f.ay;
        int pipR = 3 * p;

        // ---- 量尺寸 ----
        int icon = f.icon;
        int lvW = f.w(f.lv);
        int elW = 7 * p + 2 * p + f.w(f.elName);
        int metaW = compact ? lvW + 3 * p + 7 * p : lvW + 4 * p + elW;
        int rowsH = compact ? icon : Math.max(icon, 19 * p);
        int colGap = 4 * p;
        int textColW = compact ? metaW : Math.max(f.w(f.name), metaW);
        int promptW = 0;
        int keyBoxW = 0;
        int padH = 2 * p;
        int keyGap = 3 * p;
        if (!compact && f.promptText != null) {
            if (f.promptKey) {
                keyBoxW = f.w(MercyPrompt.keyText()) + padH * 2 + lw * 2;
                promptW = keyBoxW + keyGap + f.w(f.promptText);
            } else {
                promptW = f.w(f.promptText);
            }
        }
        int blockW = Math.max(icon + colGap + textColW, Math.max(compact ? 0 : f.w(f.caption), promptW));
        int secGap = 3 * p;
        int totalW = blockW + secGap + f.secReserve;
        // 相对底线（UY = 底线上沿）往上、往下伸出多少；提示行的位置一直留着，出现、消失时信息块不跳
        int above = 3 * p + rowsH + (compact ? 0 : 3 * p + 8 * p);
        int below = compact ? lw : lw + 3 * p + 10 * p;
        int clear = 6 * p;

        // ---- 摆位置：底线和头顶齐平，信息块整个在头的右边（放不下就左边），上下夹进安全区 ----
        int uy = clampLoHi(Math.min(Math.round(f.hbY0) - 2 * p, ay - pipR / 2 - p - 8 * p),
                f.safeT + above, f.safeB - below);
        int dir = uy <= ay ? -1 : 1;
        int q = pipR / 2 + p;
        boolean flip = false;
        int sx = ax + q;
        int sy = ay + dir * q;
        int len = Math.max(0, Math.floorDiv((uy - sy) * dir, lw) * lw);
        int joinX = sx + len;
        int left = Math.max(joinX, Mth.ceil(f.hbX1) + clear);
        if (left + totalW > f.safeR) {
            flip = true;
            sx = ax - q;
            joinX = sx - len;
            left = Math.min(joinX, Mth.floor(f.hbX0) - clear) - blockW;
        }
        int groupL = flip ? left - secGap - f.secReserve : left;
        int shift = Math.max(0, f.safeL - groupL) - Math.max(0, groupL + totalW - f.safeR);
        left += shift;
        groupL += shift;
        int right = left + blockW;
        int secX = flip ? groupL + f.secReserve - f.w(f.secs) : right + secGap;
        int knee = flip ? right : left;

        // ---- 出现动画：菱形 → 引线 → 底线 → 字 ----
        float kPip = easeOut(seg(s, 0.0D, 0.3D));
        float kLead = seg(s, 0.15D, 0.45D);
        float kLine = easeOut(seg(s, 0.35D, 0.75D));
        float kText = easeOut(seg(s, 0.5D, 1.0D));
        int lift = Math.round((1.0F - kText) * 2.0F) * p;
        float ta = a * kText;

        GuiGraphics g = f.g;

        // 软边暗红玻璃：下沿跟着提示行一起伸缩（排版仍按留着提示行算，字和线不跳）
        float promptVis = compact || f.promptText == null ? 0.0F : f.promptA;
        int top = uy - above, bottom = compact ? uy + below : uy + lw + Math.round((3 * p + 10 * p) * promptVis);
        int gx0 = groupL - 6 * p, gy0 = top - 4 * p, gx1 = groupL + totalW + 6 * p, gy1 = bottom + 4 * p;
        int edge = 4 * p;
        HoloShapes.softRect(g, g.pose().last().pose(), gx0 + edge, gy0 + edge, gx1 - edge, gy1 - edge, edge,
                GuiShapes.argb(HoloShapes.GLASS, GLASS_ALPHA * a * kLine), false);

        GuiShapes.begin(g);
        try {
            // 锚点菱形 + 中心点：都以 (ax, ay) 这条像素边界为中心左右、上下对称，中心点边长取偶数
            int r = Math.max(p, Math.round(pipR * kPip / lw) * lw);
            diamond(g, ax, ay, r, lw, accent, 0.95F * a * kPip);
            int dot = (p + 1) / 2;
            block(g, ax - dot, ay - dot, 2 * dot, 2 * dot, accent, a * kPip);

            // 引线：45° 斜段，再水平接到底线端头；拐点一颗小方点
            int leadRgb = f.own ? FxPalette.BONE : FxPalette.ASH;
            int dlen = Math.round(len * kLead / lw) * lw;
            diag(g, sx, sy, dlen, flip ? -1 : 1, dir, lw, leadRgb, 0.9F * a);
            if (kLead >= 1.0F) {
                if (joinX != knee) {
                    block(g, Math.min(joinX, knee), uy, Math.abs(knee - joinX), lw, leadRgb, 0.9F * a);
                }
                block(g, joinX - p / 2, uy + lw / 2 - p / 2, p, p, leadRgb, a);
            }

            // 底线 = 倒计时：剩下的一段 2 线宽亮、过去的一段细暗；从引线那头往外长，倒计时往引线那头缩
            int lineW = Math.round(blockW * kLine);
            int lx0 = flip ? right - lineW : left;
            block(g, lx0, uy, lineW, lw, FxPalette.ASH, 0.55F * a);
            int progW = Math.round(lineW * f.frac);
            if (progW > 0) {
                block(g, flip ? right - progW : left, uy - lw, progW, 2 * lw, f.urgent ? FxPalette.KUVA : FxPalette.BONE,
                        f.urgentA * a);
            }

            // 元素小图形（单行版跟在等级后面，完整版在等级和元素名中间）
            int rowsTop = uy - 3 * p - rowsH;
            int colX = left + icon + colGap;
            if (compact) {
                int ry = rowsTop + (rowsH - 7 * p) / 2;
                glyph(g, f.elMask, colX + lvW + 3 * p, ry - lift, p, f.elRgb, ta);
            } else {
                int nameY = rowsTop + (rowsH - 19 * p) / 2;
                int metaY = nameY + 11 * p;
                glyph(g, f.elMask, colX + lvW + 4 * p, metaY + p / 2 - lift, p, f.elRgb, ta);
            }

            // 处决键的细框
            if (!compact && f.promptKey && f.promptA > 0.003F) {
                int py = uy + lw + 3 * p;
                frame(g, left, py, keyBoxW, 10 * p, lw, accent, 0.95F * ta * f.promptA);
            }
        } finally {
            GuiShapes.end();
        }

        // 武器贴图：贴图像素的整数倍
        int rowsTop = uy - 3 * p - rowsH;
        int iy = rowsTop + (rowsH - icon) / 2;
        if (ta > 0.003F) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0F, 1.0F, 1.0F, ta);
            g.blit(left, iy - lift, 0, icon, icon, f.sprite);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }

        // 秒数：挂在底线尾巴上，数字竖直中线对准底线
        int secY = uy + lw / 2 - (7 * p) / 2;
        text(f, f.secs, secX, secY - lift, f.urgent ? FxPalette.KUVA : FxPalette.BONE, f.urgentA * ta);

        int colX = left + icon + colGap;
        if (compact) {
            int ry = rowsTop + (rowsH - 7 * p) / 2;
            text(f, f.lv, colX, ry - lift, FxPalette.TEXT_MID, ta);
            return;
        }
        int nameY = rowsTop + (rowsH - 19 * p) / 2;
        int metaY = nameY + 11 * p;
        text(f, f.name, colX, nameY - lift, FxPalette.BONE, ta);
        text(f, f.lv, colX, metaY + f.rowOff(f.lv) - lift, FxPalette.TEXT_MID, ta);
        text(f, f.elName, colX + lvW + 4 * p + 9 * p, metaY - lift, f.elRgb, ta);

        // 小标题：和贴图、底线左端同一列
        text(f, f.caption, left, rowsTop - 3 * p - 8 * p - lift, FxPalette.TEXT_MID, 0.85F * ta);

        // 处决提示：底线下面，左对齐
        if (f.promptText != null && f.promptA > 0.003F) {
            int py = uy + lw + 3 * p;
            float pa = ta * f.promptA;
            if (f.promptKey) {
                String key = MercyPrompt.keyText();
                int ky = py + (10 * p - f.h(key)) / 2;
                text(f, key, left + lw + padH, ky, FxPalette.BONE, pa);
                text(f, f.promptText, left + keyBoxW + keyGap, py + p, FxPalette.BONE, pa);
            } else {
                text(f, f.promptText, left, py + p, FxPalette.ASH, pa);
            }
        }
    }

    // ==================== 图元（物理像素，整数坐标） ====================

    private static void block(GuiGraphics g, int x, int y, int w, int h, int rgb, float a) {
        if (a <= 0.003F || w <= 0 || h <= 0) {
            return;
        }
        GuiShapes.block(g, x, y, x + w, y + h, GuiShapes.argb(rgb, Math.min(1.0F, a)));
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int t, int rgb, float a) {
        block(g, x, y, w, t, rgb, a);
        block(g, x, y + h - t, w, t, rgb, a);
        block(g, x, y + t, t, h - 2 * t, rgb, a);
        block(g, x + w - t, y + t, t, h - 2 * t, rgb, a);
    }

    /** 45° 斜线，按 t×t 的方块一级一级走（像素画的斜线，不糊） */
    private static void diag(GuiGraphics g, int x0, int y0, int len, int dx, int dy, int t, int rgb, float a) {
        for (int i = 0; i < len; i += t) {
            block(g, x0 + dx * i - (dx < 0 ? t : 0), y0 + dy * i - (dy < 0 ? t : 0), t, t, rgb, a);
        }
    }

    /**
     * 空心菱形：半径 r，线宽 t。中心是 (cx, cy) 这条像素边界，左半往左、上半往上各占 t，
     * 线宽是奇数时也左右、上下严格对称
     */
    private static void diamond(GuiGraphics g, int cx, int cy, int r, int t, int rgb, float a) {
        for (int i = 0; i <= r; i += t) {
            int x = r - i;
            block(g, cx - x - t, cy - i - t, t, t, rgb, a);
            block(g, cx + x, cy - i - t, t, t, rgb, a);
            block(g, cx - x - t, cy + i, t, t, rgb, a);
            block(g, cx + x, cy + i, t, t, rgb, a);
        }
    }

    /** 元素小图形：7×7 字像素 */
    private static void glyph(GuiGraphics g, int[] mask, int x, int y, int p, int rgb, float a) {
        if (a <= 0.003F) {
            return;
        }
        for (int j = 0; j < 7; j++) {
            for (int i = 0; i < 7; i++) {
                if ((mask[j] & (1 << (6 - i))) != 0) {
                    block(g, x + i * p, y + j * p, p, p, rgb, a);
                }
            }
        }
    }

    private static void text(Frame f, String s, int x, int y, int rgb, float a) {
        if (a <= 0.003F || s.isEmpty()) {
            return;
        }
        HoloShapes.text(f.g, f.font, s, x, y, f.p, GuiShapes.argb(rgb, Math.min(1.0F, a)));
    }

    // ==================== 字和物品 ====================

    private static void refreshTexts(Minecraft mc) {
        String code = mc.options.languageCode;
        if (code.equals(lang)) {
            return;
        }
        lang = code;
        caption = I18n.get("kuvalich.larvling.overhead.caption");
        hasLichText = I18n.get("kuvalich.mercy.prompt.has_lich");
        weaponNames = new String[0];
        ELEMENT_NAMES.clear();
        LEVEL_TEXT.clear();
    }

    private static String weaponName(int index, ItemStack stack) {
        int n = KuvaLichItems.KUVA_WEAPONS.size();
        if (weaponNames.length != n) {
            weaponNames = new String[n];
        }
        String s = weaponNames[index];
        if (s == null) {
            s = stack.getHoverName().getString();
            weaponNames[index] = s;
        }
        return s;
    }

    private static String levelText(int level) {
        String s = LEVEL_TEXT.get(level);
        if (s == null) {
            s = I18n.get("kuvalich.larvling.tag.level", level > 0 ? Integer.toString(level) : "?");
            LEVEL_TEXT.put(level, s);
        }
        return s;
    }

    private static String elementName(String type) {
        return ELEMENT_NAMES.computeIfAbsent(type, t -> I18n.get("kuvaweapon.type." + t));
    }

    /** 武器下标对应的物品堆；下标无效返回 null */
    @Nullable
    private static ItemStack stackOf(int index) {
        int n = KuvaLichItems.KUVA_WEAPONS.size();
        if (index < 0 || index >= n) {
            return null;
        }
        if (stacks.length != n) {
            stacks = new ItemStack[n];
        }
        ItemStack s = stacks[index];
        if (s == null) {
            s = new ItemStack(KuvaLichItems.KUVA_WEAPONS.get(index));
            stacks[index] = s;
        }
        return s;
    }

    private static void prune(long now) {
        if (now - lastPruneMs < 1000L || SLOTS.isEmpty()) {
            return;
        }
        lastPruneMs = now;
        for (var it = SLOTS.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Int2ObjectMap.Entry<Slot> en = it.next();
            if (now - en.getValue().lastMs > 1000L) {
                it.remove();
            }
        }
    }

    // ==================== 小工具 ====================

    /** 有没有汉字之类的宽字符 */
    private static boolean hasWide(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            if (s.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }

    /** {@code t} 在 [{@code a}, {@code b}] 里走到哪（0~1） */
    private static float seg(double t, double a, double b) {
        return (float) Mth.clamp((t - a) / (b - a), 0.0D, 1.0D);
    }

    private static float easeOut(float x) {
        float k = 1.0F - x;
        return 1.0F - k * k * k;
    }

    /** 夹进 [lo, hi]；hi 比 lo 还小（屏幕太矮放不下）时取 lo */
    private static int clampLoHi(int v, int lo, int hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
