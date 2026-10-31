package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.lich.BossBarTracker;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Locale;

/**
 * 玄骸升级时屏幕上方正中的横幅：一条全息读数，从左往右读「玄骸名 → 菱框里的新等级 → 升级」
 *
 * <p>开场时刻 Tb：怜悯之击判错（包里 b 的 bit 0 = 1）是处决时间轴上的 g_k + 2900 ms，本地已经没有这一场的时间轴
 * （处决者中途被清场之类）时按包里的 k 推：收到包 + (g_k + 2900 − 850) ms（这个包在处决第 850 ms 左右定案时发出）；
 * 直接判定判错是收到包 + 600 ms。跟着处决时间轴走的时候每帧重新对一次，冻帧时横幅也停住。</p>
 *
 * <p>样子：上下两道细线框出一条读数，两头收成尖括号，中间垫一块同形的暗红玻璃板（外面一圈软阴影，压在亮天空上也读得清）；
 * 括号外各拖一段渐隐的中轴线和几个青色刻度。读数里左边是大写、加了字距的玄骸名，中间一颗双线菱框装着等级数字，右边「升级」，
 * 整条居中（名字长短不同，菱框会跟着左右挪一点）。和处决界面的托架细线是同一套画法，不用贴图。</p>
 *
 * <p>尺寸跟界面缩放走：下面的长度常量是「参考屏高」{@link #REF_H} GUI 像素的比例，换成物理像素 = 比例 × 270 × 界面缩放
 * （界面缩放 4 的 1080p 就是屏高的比例）；字按整数倍物理像素画，不糊。没有 Boss 条时菱框上顶点离屏幕顶 26 GUI 像素，
 * 有 Boss 条（玄骸自己的、原版的、别的模组的）时让到最下面那条的下沿下面，Boss 条出现、消失时滑过去，不跳。</p>
 *
 * <p>时间轴（毫秒，0 = Tb）：0–160 中心一点亮光；0–280 中轴细线从中心往两边弹开；120–400 框线和玻璃板从中间展开、
 * 尖括号合上；150–450 菱框从上顶点往两边描出来，内框反着描；300 起名字整排乱码滚动，380 起每 40 ms 锁定一个字
 * （锁定那一下发橙）；760 数字带红、青两色错位砸进菱框，120 ms 内合拢，菱框弹一下、里面一闪，升级音落在这一拍；
 * 880–1100「升级」字距从宽收到正常；停留时一道扫描线每 1.8 s 从上往下扫过菱框；5600 起名字从右往左重新乱码、
 * 一个个消失，框线、玻璃板、中轴收回中心，菱框压扁，6100 画完。同时只有一条，新的直接顶掉旧的。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RankUpBanner {

    /** 覆盖层 id */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "rank_banner");
    /** 整个横幅往屏幕外抬多少：原版聊天行画在 z = 50，要比它高 */
    private static final float ABOVE_CHAT_Z = 200.0F;

    /** 怜悯之击判错：横幅在判错那一刀填满时刻之后多久出来 */
    public static final int AFTER_WRONG_MS = 2900;
    /** 定案（发包）大约在处决第几毫秒 */
    private static final int VERDICT_MS = 850;
    /** 直接判定：收到包之后多久出来 */
    public static final int DIRECT_DELAY_MS = 600;

    /** 数字砸进菱框的那一拍 */
    public static final int IMPACT_MS = 760;
    /** 升级音自带一段前奏，重音正好落在 IMPACT_MS，所以横幅一出来就放 */
    private static final int SOUND_AT_MS = 0;
    /** 横幅已经过了放声时刻这么久才排上（冻帧、迟到的包）就不补放升级音 */
    private static final int SOUND_LATE_MS = 400;
    /** 停留到这一刻开始退场 */
    public static final int HOLD_UNTIL = 5600;
    /** 退场画完 */
    public static final int TOTAL_MS = 6100;
    private static final int SCRAMBLE_STEP_MS = 40;
    private static final int NAME_AT = 300;
    private static final int NAME_LOCK_AT = 380;
    private static final int NAME_LOCK_STEP = 40;
    private static final int NAME_HOT_MS = 120;
    private static final int LABEL_AT = 880;
    private static final int LABEL_MS = 220;
    private static final int SCAN_AT = 1200;
    private static final int SCAN_PERIOD = 1800;
    private static final float POP = 0.12F;
    private static final float POP_BACK = 3.0F;

    /** 长度单位：界面缩放 1 时的参考屏高（GUI 像素）；乘界面缩放就是物理像素 */
    private static final float REF_H = 270.0F;
    /** 没有 Boss 条时菱框上顶点离屏幕顶多少 GUI 像素 */
    private static final int TOP_GUI = 26;
    /** 在 Boss 条下沿下面再空几个 GUI 像素（Boss 条记的下沿已经带了下一条标题的那一截） */
    private static final int BOSS_GAP_GUI = 2;
    /** Boss 条出现、消失时横幅滑过去的时间常数（真实毫秒） */
    private static final float FOLLOW_MS = 90.0F;
    /** 整条连两侧渐隐线最多占屏宽多少，名字太长时名字的字先降一档 */
    private static final float MAX_WIDTH = 0.94F;

    // 下面都是参考屏高的比例
    private static final float DIA_HH = 0.068F;
    private static final float DIA_HW = 0.060F;
    private static final float DIA_INSET = 0.010F;
    private static final float BAND_HH = 0.036F;
    private static final float CHEV = 0.022F;
    private static final float BRACKET_PAD = 0.022F;
    private static final float WING = 0.16F;
    private static final float GAP = 0.030F;
    private static final float FRAME_GAP = 0.008F;
    private static final float AXIS_TICK_STEP = 0.03F;
    private static final float AXIS_TICK_HALF = 0.008F;
    private static final float FRAME_TICK_STEP = 0.012F;
    private static final float FRAME_TICK_LEN = 0.006F;
    private static final float SHADOW_SOFT = 0.012F;
    private static final float NAME_PX = 0.040F;
    private static final float NUMBER_PX = 0.060F;
    private static final float SPLIT = 0.014F;
    private static final float NAME_GLOW = 0.004F;
    private static final float NUMBER_GLOW = 0.006F;
    private static final float SPARK = 0.010F;
    /** 玻璃板外圈软阴影的范围（物理像素里是 0.012 × 参考屏高 的几倍） */
    private static final float SHADOW_IN = 1.5F;
    private static final float SHADOW_OUT = 2.0F;

    private static final int GLASS = 0x2A050C;
    private static final int NUMBER_WHITE = 0xF4F7F8;
    private static final int NUMBER_SHADOW = 0x2A0308;
    private static final int NAME_SHADOW = 0x1A0206;
    private static final int LABEL_SHADOW = 0x101418;

    /** 名字解码时滚动的乱码字 */
    private static final String[] SCRAMBLE = split("ABCDEFGHJKLMNPQRSTVWXZ0123456789#%&?");
    private static final int[] SCRAMBLE_ADV = new int[SCRAMBLE.length];

    private static final float[] POLY = new float[12];
    private static final float[] POLY_A = new float[6];
    private static final float[] RING_IN = new float[12];
    private static final float[] RING_OUT = new float[12];
    private static final float[] PATH = new float[6];

    private static boolean active;
    private static int rank;
    private static String rankText = "";
    private static String[] nameChars = new String[0];
    private static int[] nameAdv = new int[0];
    private static boolean[] nameWide = new boolean[0];
    private static String[] labelChars = new String[0];
    private static int[] labelAdv = new int[0];
    private static boolean fromMercy;
    private static int targetId = -1;
    private static int wrongStab = 1;
    /** t = 0（Tb）那一刻的演出钟 */
    private static long zeroClock;
    /** 调试用：冻在横幅的这一毫秒；小于 0 = 照常走 */
    private static long frozenAt = -1L;
    private static boolean soundDone;
    /** 菱框上顶点现在离屏幕顶多少物理像素（跟着 Boss 条滑动）；NaN = 下一帧直接定位 */
    private static float topPx = Float.NaN;
    private static long followMs;

    private RankUpBanner() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(OVERLAY_ID.getPath(), RankUpBanner::render);
    }

    /** 升级的效果包到了：a = 新等级，b 的 bit 0 = 来自怜悯之击、bit 1~2 = 判错的是第几刀 */
    public static void onPacket(KuvaFxPacket p) {
        int id = p.entityId();
        show(p.a, (p.b & 1) != 0, (p.b >> 1) & 3, id, nameOf(id), -1L);
    }

    /**
     * 排一条横幅（新的顶掉旧的）
     *
     * @param mercy    来自怜悯之击（按处决时间轴排）；否则是直接判定
     * @param k        判错的是第几刀（1~3）
     * @param target   玄骸的实体 id（跟处决时间轴对时用），没有给 -1
     * @param frozenMs 调试用：冻在横幅的这一毫秒，小于 0 照常走
     */
    static void show(int newRank, boolean mercy, int k, int target, String name, long frozenMs) {
        long now = KuvaFxDirector.clockMs();
        Font font = Minecraft.getInstance().font;
        active = true;
        rank = newRank;
        rankText = Integer.toString(newRank);
        // 读数里的名字一律大写；汉字之类没有大小写，原样
        nameChars = split(name.toUpperCase(Locale.ROOT));
        nameAdv = new int[nameChars.length];
        nameWide = new boolean[nameChars.length];
        for (int i = 0; i < nameChars.length; i++) {
            nameAdv[i] = font.width(nameChars[i]);
            nameWide[i] = MercyPrompt.hasWide(nameChars[i]);
        }
        labelChars = split(I18n.get("kuvalich.mercy.banner.rank_up"));
        labelAdv = new int[labelChars.length];
        for (int i = 0; i < labelChars.length; i++) {
            labelAdv[i] = font.width(labelChars[i]);
        }
        for (int i = 0; i < SCRAMBLE.length; i++) {
            SCRAMBLE_ADV[i] = font.width(SCRAMBLE[i]);
        }
        fromMercy = mercy;
        wrongStab = Mth.clamp(k, 1, 3);
        targetId = target;
        frozenAt = frozenMs;
        soundDone = false;
        topPx = Float.NaN;
        if (!mercy) {
            zeroClock = now + DIRECT_DELAY_MS;
            return;
        }
        int at = MercyHud.fillMs(wrongStab) + AFTER_WRONG_MS;
        long e = MercyHud.elapsedFor(target);
        zeroClock = e >= 0L ? now - e + at : now + at - VERDICT_MS;
    }

    /** 撤掉当前的横幅 */
    static void clear() {
        active = false;
    }

    /** 玄骸的名字：实体身上同步的优先，其次是自己那只玄骸的状态，都没有就写「赤毒玄骸」 */
    static String nameOf(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null || entityId < 0 ? null : mc.level.getEntity(entityId);
        if (e instanceof KuvaMasterEntity lich && !lich.getLichName().isEmpty()) {
            return lich.getLichName();
        }
        String own = ClientLichState.nameTitle();
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return I18n.get("entity.kuvalich.kuva_master");
    }

    /** 按码点拆成一个个字（逐字画，字距才好调） */
    private static String[] split(String s) {
        int[] cps = s.codePoints().toArray();
        String[] out = new String[cps.length];
        for (int i = 0; i < cps.length; i++) {
            out[i] = new String(Character.toChars(cps[i]));
        }
        return out;
    }

    /** 横幅现在的时刻（毫秒，0 = Tb）；没有横幅返回 {@link Long#MIN_VALUE} */
    static long nowMs() {
        if (!active) {
            return Long.MIN_VALUE;
        }
        if (frozenAt >= 0L) {
            return frozenAt;
        }
        long now = KuvaFxDirector.clockMs();
        if (fromMercy) {
            long e = MercyHud.elapsedFor(targetId);
            if (e >= 0L) {
                zeroClock = now - e + MercyHud.fillMs(wrongStab) + AFTER_WRONG_MS;
            }
        }
        return now - zeroClock;
    }

    // ==================== 画 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active) {
            return;
        }
        if (mc.level == null) {
            active = false;
            return;
        }
        long t = nowMs();
        if (t >= TOTAL_MS) {
            active = false;
            return;
        }
        if (t < 0L) {
            return;
        }
        if (!soundDone && t >= SOUND_AT_MS) {
            soundDone = true;
            if (EffectsDisplayConfig.mercyRankupSound() && frozenAt < 0L && t < SOUND_AT_MS + SOUND_LATE_MS) {
                KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.UI_BANNER_RANKUP.get(), 1.0F, 1.0F));
            }
        }
        if (mc.options.hideGui) {
            return;
        }
        Window win = mc.getWindow();
        float gs = (float) win.getGuiScale();
        follow(Math.max(TOP_GUI, BossBarTracker.recentBottomY() + BOSS_GAP_GUI) * gs);
        // 聊天栏画在 z = 50、开着深度测试；整段抬到聊天之上，聊天栏拉得很高时也不会被它的底挡掉
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ABOVE_CHAT_Z);
        draw(g, mc.font, win, t);
        g.pose().popPose();
    }

    /** 菱框上顶点往目标位置（物理像素）滑过去；第一帧直接到位 */
    private static void follow(float target) {
        long now = Util.getMillis();
        if (Float.isNaN(topPx)) {
            topPx = target;
        } else {
            float dt = Math.min(200.0F, Math.max(0.0F, now - followMs));
            topPx += (target - topPx) * (1.0F - (float) Math.exp(-dt / FOLLOW_MS));
            if (Math.abs(target - topPx) < 0.5F) {
                topPx = target;
            }
        }
        followMs = now;
    }

    /** t 在 [a, b] 里走到哪（0~1） */
    private static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
    }

    private static float easeOutCubic(float x) {
        float u = 1.0F - x;
        return 1.0F - u * u * u;
    }

    private static float easeInCubic(float x) {
        return x * x * x;
    }

    private static float easeOutExpo(float x) {
        return x >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * x);
    }

    private static float easeOutBack(float x, float s) {
        float u = x - 1.0F;
        return 1.0F + (s + 1.0F) * u * u * u + s * u * u;
    }

    /** 横排一行逐字画出来的可见宽（物理像素）：每个字步进 × p 加字距，扣掉最后一个字后面的字距和 1 字体像素空隙 */
    private static float rowWidth(int[] adv, int p, float spacing) {
        if (adv.length == 0) {
            return 0.0F;
        }
        float w = 0.0F;
        for (int a : adv) {
            w += a * p + spacing;
        }
        return w - spacing - p;
    }

    /** 线宽是奇数像素时，横线的 y（竖线的 x）放到半像素上，线才落在整像素里不发虚 */
    private static float crisp(float v, int lw) {
        return (lw & 1) == 1 ? Math.round(v) + 0.5F : Math.round(v);
    }

    /** 按横幅时刻 {@code t}（毫秒，0 = Tb）画一帧；全部按物理像素量 */
    static void draw(GuiGraphics g, Font font, Window win, float t) {
        if (t < 0.0F || t >= TOTAL_MS) {
            return;
        }
        float gs = (float) win.getGuiScale();
        int igs = Math.max(1, Math.round(gs));
        float S = REF_H * gs;
        int W = win.getWidth();
        int lw = Math.max(1, igs / 2);

        // ---- 排版 ----
        int dh = Math.round(DIA_HH * S);
        int dw = Math.round(DIA_HW * S);
        int gap = Math.round(GAP * S);
        int bh = Math.round(BAND_HH * S);
        int chev = Math.round(CHEV * S);
        int pad = Math.round(BRACKET_PAD * S);
        int wing = Math.round(WING * S);
        int inset = Math.round(DIA_INSET * S);
        int spN = igs;
        int pl = igs;
        int spL = 4 * igs;
        float labelW = rowWidth(labelAdv, pl, spL);
        int pn = Math.max(igs, Math.round(NAME_PX * S / 8.0F));
        float nameW = rowWidth(nameAdv, pn, spN);
        while (pn > 1 && nameW + 2 * gap + 2 * dw + labelW + 2 * pad + 2 * wing > W * MAX_WIDTH) {
            pn--;
            nameW = rowWidth(nameAdv, pn, spN);
        }
        float span = nameW + gap + 2 * dw + gap + labelW;
        float nameL = Math.round(W * 0.5F - span * 0.5F);
        float nameR = nameL + nameW;
        float cx = nameR + gap + dw;
        float labelL = cx + dw + gap;
        float leftC = nameL - pad;
        float rightC = labelL + labelW + pad;
        float ay = Math.round(topPx + dh);
        float halfSpan = Math.max(1.0F, (rightC - leftC) * 0.5F);

        // ---- 进度 ----
        boolean exiting = t >= HOLD_UNTIL;
        float axisK = !exiting ? easeOutExpo(seg(t, 0, 280))
                : 1.0F - easeInCubic(seg(t, HOLD_UNTIL + 150, HOLD_UNTIL + 400));
        float frameK = !exiting ? easeOutCubic(seg(t, 120, 400))
                : 1.0F - easeInCubic(seg(t, HOLD_UNTIL + 100, HOLD_UNTIL + 350));
        float bandA = !exiting ? seg(t, 120, 350) : 1.0F - seg(t, HOLD_UNTIL + 150, HOLD_UNTIL + 420);
        float diaK = easeOutCubic(seg(t, 150, 450));
        float diaV = !exiting ? 1.0F : 1.0F - easeOutCubic(seg(t, HOLD_UNTIL + 260, HOLD_UNTIL + 440));
        float fillA = seg(t, 300, 450) * (!exiting ? 1.0F : 1.0F - seg(t, HOLD_UNTIL + 250, HOLD_UNTIL + 450));
        float glow = glowScale();
        boolean additive = EffectsDisplayConfig.additiveGlow();

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        Matrix4f m = g.pose().last().pose();

        float lx = cx - (cx - leftC) * frameK;
        float rx = cx + (rightC - cx) * frameK;

        // ---- 底：和框线同形的暗红玻璃板，外面垫一圈软阴影 ----
        if (bandA > 0.002F && frameK > 0.0F) {
            float ch = chev * Math.min(1.0F, (rx - lx) / (2 * chev + 1));
            hexagon(POLY, lx, rx, ay, bh, ch);
            float soft = SHADOW_SOFT * S;
            // 刚展开时板子很窄，往里缩的那一圈不能比板子还宽
            float in = Math.min(SHADOW_IN * soft, Math.min(0.5F * bh, 0.25F * (rx - lx)));
            softPolygon(g, m, POLY, 6, in, SHADOW_OUT * soft, GuiShapes.argb(FxPalette.INK, 0.45F * bandA));
            // 横向渐变：中间 0.80、两头 0.62（按展开完的半宽量）
            for (int i = 0; i < 6; i++) {
                float d = Math.min(1.0F, Math.abs(POLY[i * 2] - cx) / halfSpan);
                POLY_A[i] = (0.80F - 0.18F * d) * bandA;
            }
            glass(g, m, POLY, POLY_A, cx, ay, bh, bandA);
            float hy = ay - bh + lw;
            GuiShapes.hairline(g, lx + ch, crisp(hy, lw), rx - ch, crisp(hy, lw), lw,
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.12F * bandA));
        }

        GuiShapes.begin(g);
        // ---- 中轴细线 + 括号外的刻度 ----
        int cyan = GuiShapes.argb(FxPalette.TECH, 0.75F * (exiting ? bandA : frameK));
        float axisY = crisp(ay, lw);
        if (axisK > 0.0F) {
            float reachL = (cx - (leftC - wing)) * axisK;
            float reachR = ((rightC + wing) - cx) * axisK;
            // 内段：入场时整根从中心弹出去，玻璃板展开后就藏到板子后面
            float innerA = exiting ? 0.0F : 0.9F * (1.0F - bandA);
            if (innerA > 0.01F) {
                GuiShapes.hairline(g, Math.max(cx - reachL, leftC), axisY, Math.min(cx + reachR, rightC), axisY, lw,
                        GuiShapes.argb(FxPalette.KUVA, innerA));
            }
            // 外段：括号以外一路渐隐
            for (int side = -1; side <= 1; side += 2) {
                float c0 = side < 0 ? leftC : rightC;
                float end = cx + side * (side < 0 ? reachL : reachR);
                if (side < 0 ? end < c0 : end > c0) {
                    GuiShapes.hairline(g, c0, axisY, end, axisY, lw, GuiShapes.argb(FxPalette.KUVA, 0.85F),
                            GuiShapes.argb(FxPalette.KUVA, 0.0F));
                    int step = Math.round(AXIS_TICK_STEP * S);
                    int half = Math.round(AXIS_TICK_HALF * S);
                    for (int j = 1; j <= 4; j++) {
                        float tx = c0 + side * j * step;
                        if (side < 0 ? tx >= end : tx <= end) {
                            float x = crisp(tx, lw);
                            GuiShapes.hairline(g, x, ay - half, x, ay + half, lw, cyan);
                        }
                    }
                }
            }
        }
        // ---- 上下框线 + 尖括号 ----
        if (frameK > 0.0F) {
            int red = GuiShapes.argb(FxPalette.KUVA, 0.9F * (exiting ? bandA : 1.0F));
            boolean closed = frameK >= 0.999F;
            float gapX = dw * (1.0F - (float) bh / dh) + Math.round(FRAME_GAP * S);
            for (int s = -1; s <= 1; s += 2) {
                float yy = crisp(ay + s * bh, lw);
                if (lx < cx - gapX) {
                    GuiShapes.hairline(g, lx + (closed ? chev : 0), yy, cx - gapX, yy, lw, red);
                }
                if (rx > cx + gapX) {
                    GuiShapes.hairline(g, cx + gapX, yy, rx - (closed ? chev : 0), yy, lw, red);
                }
            }
            if (closed) {
                for (int side = -1; side <= 1; side += 2) {
                    float x = side < 0 ? leftC : rightC;
                    PATH[0] = x - side * chev;
                    PATH[1] = ay - bh;
                    PATH[2] = x;
                    PATH[3] = ay;
                    PATH[4] = x - side * chev;
                    PATH[5] = ay + bh;
                    GuiShapes.polyline(g, PATH, 3, false, lw, red);
                }
                // 框线两端各三个冷色小刻度：左边挂在底线上、右边挂在顶线上
                int step = Math.round(FRAME_TICK_STEP * S);
                int len = Math.round(FRAME_TICK_LEN * S);
                for (int j = 1; j <= 3; j++) {
                    float tl = crisp(leftC + chev + step * j, lw);
                    GuiShapes.hairline(g, tl, ay + bh - len, tl, ay + bh, lw, cyan);
                    float tr = crisp(rightC - chev - step * j, lw);
                    GuiShapes.hairline(g, tr, ay - bh, tr, ay - bh + len, lw, cyan);
                }
            }
        }
        GuiShapes.end();

        // ---- 菱框 ----
        float pop = t >= IMPACT_MS && t < IMPACT_MS + 140
                ? 1.0F + POP * (1.0F - easeOutBack(seg(t, IMPACT_MS, IMPACT_MS + 140), POP_BACK)) : 1.0F;
        float ddh = dh * pop * diaV, ddw = dw * pop;
        float ihw = ddw - inset, ihh = Math.max(ddh - inset * (float) dh / dw, 0.0F);
        if (fillA > 0.002F && ddh > 2.0F) {
            // 竖向渐变：上亮下暗（按菱框上下各再留 0.03 参考屏高的那一截量）
            float top = ay - dh - 0.03F * S, h = 2 * dh + 0.06F * S;
            diamondFill(g, m, cx, ay, ihw, ihh, FxPalette.KUVA_DEEP, (0.75F - 0.3F * (ay - ihh - top) / h) * fillA,
                    (0.75F - 0.3F * (ay - top) / h) * fillA, (0.75F - 0.3F * (ay + ihh - top) / h) * fillA, false);
            diamondFill(g, m, cx, ay, ihw, ihh, FxPalette.INK, 0.25F * fillA, 0.25F * fillA, 0.25F * fillA, false);
        }
        if (diaK > 0.0F && ddh > 1.0F) {
            GuiShapes.begin(g);
            int outer = GuiShapes.argb(FxPalette.KUVA, 0.95F * Math.min(1.0F, diaV * 1.5F));
            trace(g, cx, ay - ddh, cx + ddw, ay, cx, ay + ddh, diaK, lw, outer);
            trace(g, cx, ay - ddh, cx - ddw, ay, cx, ay + ddh, diaK, lw, outer);
            // 内框反着描：从下顶点往上
            int inner = GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F);
            trace(g, cx, ay + ihh, cx + ihw, ay, cx, ay - ihh, diaK, lw, inner);
            trace(g, cx, ay + ihh, cx - ihw, ay, cx, ay - ihh, diaK, lw, inner);
            GuiShapes.end();
        }
        // 落定那一拍里面一闪
        if (t >= IMPACT_MS && t < IMPACT_MS + 250 && EffectsDisplayConfig.screenFlash()) {
            float a = 0.55F * (1.0F - seg(t, IMPACT_MS, IMPACT_MS + 250)) * glow;
            diamondFill(g, m, cx, ay, ihw, ihh, FxPalette.KUVA_PALE, a, a, a, additive);
        }
        // 停留时的扫描线
        if (t >= SCAN_AT && t < HOLD_UNTIL) {
            float ph = ((t - SCAN_AT) % SCAN_PERIOD) / SCAN_PERIOD;
            if (ph < 0.5F) {
                float yy = ay - dh + 2 * dh * (ph / 0.5F);
                float half = dw * (1.0F - Math.abs(yy - ay) / dh) - inset;
                if (half > 2.0F) {
                    float y0 = Math.round(yy - lw * 0.5F);
                    softRect(g, m, cx - half, y0, cx + half, y0 + lw, 0.0F,
                            GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * glow), additive);
                }
            }
        }

        // ---- 等级数字：红、青两色错位砸进来 ----
        if (t >= IMPACT_MS && diaV > 0.3F) {
            float insetW = dw - inset, insetH = dh - inset * (float) dh / dw;
            int adv = font.width(rankText);
            int p = Math.max(1, Math.round(NUMBER_PX * S / 8.0F));
            while (p > igs && ((adv * p - p) * 0.5F + 2.0F) / insetW + (3.5F * p + 2.0F) / insetH > 0.92F) {
                p--;
            }
            float tw = adv * p - p;
            float x = Math.round(cx - tw * 0.5F);
            float y = Math.round(ay - 3.5F * p);
            float k = seg(t, IMPACT_MS, IMPACT_MS + 120);
            float a = Math.min(1.0F, k * 4.0F) * (!exiting ? 1.0F : 1.0F - seg(t, HOLD_UNTIL + 200, HOLD_UNTIL + 350));
            if (a >= 0.02F) {
                float d = Math.round(SPLIT * S * (1.0F - easeOutCubic(k)));
                float sg = NUMBER_GLOW * S;
                softRect(g, m, x - sg, y - sg, x + tw + sg, y + 7 * p + sg, 2.0F * sg,
                        GuiShapes.argb(FxPalette.KUVA, 0.3F * a * glow), additive);
                if (d > 0.0F) {
                    text(g, font, rankText, x - d, y, p, GuiShapes.argb(FxPalette.KUVA, 0.8F * a));
                    text(g, font, rankText, x + d, y, p, GuiShapes.argb(FxPalette.TECH, 0.6F * a));
                }
                text(g, font, rankText, x + igs, y + igs, p, GuiShapes.argb(NUMBER_SHADOW, 0.7F * a));
                text(g, font, rankText, x, y, p, GuiShapes.argb(NUMBER_WHITE, a));
            }
        }

        // ---- 名字：逐字解码 ----
        if (t >= NAME_AT && nameChars.length > 0) {
            drawName(g, m, font, t, nameL, ay, pn, spN, igs, S, exiting, glow, additive);
        }

        // ---- 升级：字距从宽收到正常 ----
        if (t >= LABEL_AT) {
            float k = easeOutCubic(seg(t, LABEL_AT, LABEL_AT + LABEL_MS));
            float a = k * (!exiting ? 1.0F : 1.0F - seg(t, HOLD_UNTIL, HOLD_UNTIL + 200));
            if (a >= 0.02F) {
                float sp = Math.round(spL + (1.0F - k) * 6 * igs);
                float x = labelL;
                float y = ay - Math.round(4.0F * pl);
                int shadow = GuiShapes.argb(LABEL_SHADOW, a);
                int col = GuiShapes.argb(FxPalette.BONE, a);
                for (int i = 0; i < labelChars.length; i++) {
                    text(g, font, labelChars[i], x + pl, y + pl, pl, shadow);
                    text(g, font, labelChars[i], x, y, pl, col);
                    x += labelAdv[i] * pl + sp;
                }
            }
        }

        // ---- 入场时中心一点亮光 ----
        if (t < 160.0F) {
            float r = Math.round(SPARK * S);
            softRect(g, m, cx - 0.5F * r, ay - 0.5F * r, cx + 0.5F * r, ay + 0.5F * r, 1.2F * r,
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * (1.0F - seg(t, 0, 160)) * glow), additive);
        }
        GuiShapes.flush(g);
        g.pose().popPose();
    }

    /**
     * 玄骸名：300 起整排乱码，380 起每 40 ms 从左往右锁定一个字（锁定那一下发橙，120 ms 后变粉白）；
     * 退场时从右往左重新乱码、一个个消失。汉字之类的宽字不滚乱码，直接显示原字
     */
    private static void drawName(GuiGraphics g, Matrix4f m, Font font, float t, float nameL, float ay, int p, int sp,
                                 int igs, float S, boolean exiting, float glow, boolean additive) {
        int n = nameChars.length;
        float y = ay - Math.round(3.5F * p);
        float outK = seg(t, HOLD_UNTIL, HOLD_UNTIL + 250);
        int bucket = (int) (t / SCRAMBLE_STEP_MS);
        // 先量还看得见的那一段，辉光垫在字下面
        float x = nameL, gx0 = Float.MAX_VALUE, gx1 = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float adv = nameAdv[i] * p + sp;
            boolean gone = exiting && (n - 1 - i) < outK * (n + 1);
            if (!gone && !" ".equals(nameChars[i])) {
                gx0 = Math.min(gx0, x);
                gx1 = Math.max(gx1, x + nameAdv[i] * p - p);
            }
            x += adv;
        }
        if (gx1 > gx0) {
            float sg = Math.max(1.0F, NAME_GLOW * S);
            softRect(g, m, gx0 - sg, y - sg, gx1 + sg, y + 7 * p + sg, 2.0F * sg,
                    GuiShapes.argb(FxPalette.KUVA, 0.2F * glow), additive);
        }
        x = nameL;
        for (int i = 0; i < n; i++) {
            String ch = nameChars[i];
            float adv = nameAdv[i] * p + sp;
            boolean gone = exiting && (n - 1 - i) < outK * (n + 1);
            if (gone || " ".equals(ch)) {
                x += adv;
                continue;
            }
            int lock = NAME_LOCK_AT + i * NAME_LOCK_STEP;
            boolean scrambled = t < lock || (exiting && (n - 1 - i) < outK * (n + 1) + 2);
            String shown = ch;
            int shownAdv = nameAdv[i];
            int col;
            float a;
            if (scrambled) {
                if (!nameWide[i]) {
                    int s = scrambleIndex(bucket, i);
                    shown = SCRAMBLE[s];
                    shownAdv = SCRAMBLE_ADV[s];
                }
                col = FxPalette.KUVA;
                a = 0.7F;
            } else {
                float hot = 1.0F - seg(t, lock, lock + NAME_HOT_MS);
                col = hot > 0.5F ? FxPalette.KUVA_HOT : FxPalette.KUVA_PALE;
                a = 1.0F;
            }
            // 乱码字和真字不一样宽：按真字的宽居中，整排不跳
            float xx = x + Math.round(((nameAdv[i] - 1) - (shownAdv - 1)) * p * 0.5F);
            text(g, font, shown, xx + igs, y + igs, p, GuiShapes.argb(NAME_SHADOW, 0.6F * a));
            text(g, font, shown, xx, y, p, GuiShapes.argb(col, a));
            x += adv;
        }
    }

    /** 第 {@code i} 个字在第 {@code bucket} 个 40 ms 里显示哪个乱码字（固定种子，同一时刻画出来一样） */
    private static int scrambleIndex(int bucket, int i) {
        int h = bucket * 7919 + 13 + i * 104729;
        h ^= h >>> 13;
        h *= 0x5BD1E995;
        h ^= h >>> 15;
        return Math.floorMod(h, SCRAMBLE.length);
    }

    /** 画在 HUD 上的加法辉光该乘的系数：白天露天按相机处压暗；关了加法辉光时改普通混合、再淡一些 */
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

    /** 读数条的外形：左右两个尖、上下两条平边，顺序是左尖 → 左上 → 右上 → 右尖 → 右下 → 左下 */
    private static void hexagon(float[] out, float lx, float rx, float cy, float hh, float ch) {
        out[0] = lx;
        out[1] = cy;
        out[2] = lx + ch;
        out[3] = cy - hh;
        out[4] = rx - ch;
        out[5] = cy - hh;
        out[6] = rx;
        out[7] = cy;
        out[8] = rx - ch;
        out[9] = cy + hh;
        out[10] = lx + ch;
        out[11] = cy + hh;
    }

    /** 沿 a → b → c 两段折线描出 {@code k} 比例（0~1）的长度，两段各算一半 */
    private static void trace(GuiGraphics g, float ax, float ay, float bx, float by, float cx, float cy, float k,
                              float lw, int argb) {
        float d = Math.min(1.0F, k) * 2.0F;
        PATH[0] = ax;
        PATH[1] = ay;
        if (d <= 1.0F) {
            PATH[2] = ax + (bx - ax) * d;
            PATH[3] = ay + (by - ay) * d;
            GuiShapes.polyline(g, PATH, 2, false, lw, argb);
            return;
        }
        PATH[2] = bx;
        PATH[3] = by;
        PATH[4] = bx + (cx - bx) * (d - 1.0F);
        PATH[5] = by + (cy - by) * (d - 1.0F);
        GuiShapes.polyline(g, PATH, 3, false, lw, argb);
    }

    // ==================== 顶点色图元（GuiShapes 只有单色、普通混合，这几样要渐变或加法） ====================

    private static BufferBuilder open(GuiGraphics g, boolean additive) {
        GuiShapes.flush(g);
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    private static void close(BufferBuilder buf) {
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vtx(BufferBuilder buf, Matrix4f m, float x, float y, int rgb, float a) {
        int al = Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        buf.vertex(m, x, y, 0.0F).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al).endVertex();
    }

    /**
     * 玻璃板：六边形按 {@code alpha[i]}（每个顶点的透明度）横向渐变。从中线劈成左右两半各自扇形三角化，
     * 中线上两点的透明度是 0.80 × bandA（透明度只随 x 线性变，三角形插值是准的）
     */
    private static void glass(GuiGraphics g, Matrix4f m, float[] p, float[] alpha, float cx, float cy, float hh,
                              float bandA) {
        float mid = 0.80F * bandA;
        BufferBuilder buf = open(g, false);
        // 左半：左尖、左上、中上、中下、左下
        tri(buf, m, p[0], p[1], alpha[0], p[2], p[3], alpha[1], cx, cy - hh, mid);
        tri(buf, m, p[0], p[1], alpha[0], cx, cy - hh, mid, cx, cy + hh, mid);
        tri(buf, m, p[0], p[1], alpha[0], cx, cy + hh, mid, p[10], p[11], alpha[5]);
        // 右半：右尖、右上、中上、中下、右下
        tri(buf, m, p[6], p[7], alpha[3], p[4], p[5], alpha[2], cx, cy - hh, mid);
        tri(buf, m, p[6], p[7], alpha[3], cx, cy - hh, mid, cx, cy + hh, mid);
        tri(buf, m, p[6], p[7], alpha[3], cx, cy + hh, mid, p[8], p[9], alpha[4]);
        close(buf);
    }

    private static void tri(BufferBuilder buf, Matrix4f m, float x0, float y0, float a0, float x1, float y1, float a1,
                            float x2, float y2, float a2) {
        vtx(buf, m, x0, y0, GLASS, a0);
        vtx(buf, m, x1, y1, GLASS, a1);
        vtx(buf, m, x2, y2, GLASS, a2);
    }

    /** 实心菱形，上、中、下三档透明度（中间那一档给左右两个顶点），竖向渐变 */
    private static void diamondFill(GuiGraphics g, Matrix4f m, float cx, float cy, float hw, float hh, int rgb,
                                    float aTop, float aMid, float aBottom, boolean additive) {
        if (hh <= 0.0F || hw <= 0.0F || Math.max(aTop, Math.max(aMid, aBottom)) <= 0.002F) {
            return;
        }
        BufferBuilder buf = open(g, additive);
        vtx(buf, m, cx, cy - hh, rgb, aTop);
        vtx(buf, m, cx + hw, cy, rgb, aMid);
        vtx(buf, m, cx, cy + hh, rgb, aBottom);
        vtx(buf, m, cx, cy - hh, rgb, aTop);
        vtx(buf, m, cx, cy + hh, rgb, aBottom);
        vtx(buf, m, cx - hw, cy, rgb, aMid);
        close(buf);
    }

    /**
     * 凸多边形的软边：往里缩 {@code in} 那一圈实色，往外扩 {@code out} 那一圈透明，中间顶点色插值。
     * 当阴影用，原多边形本身随后会被玻璃板盖住
     */
    private static void softPolygon(GuiGraphics g, Matrix4f m, float[] p, int n, float in, float out, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        offset(p, n, -in, RING_IN);
        offset(p, n, out, RING_OUT);
        int rgb = argb & 0xFFFFFF;
        float a = (argb >>> 24) / 255.0F;
        BufferBuilder buf = open(g, false);
        for (int i = 1; i < n - 1; i++) {
            vtx(buf, m, RING_IN[0], RING_IN[1], rgb, a);
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_IN[i * 2 + 2], RING_IN[i * 2 + 3], rgb, a);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_OUT[i * 2], RING_OUT[i * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_OUT[j * 2], RING_OUT[j * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_IN[i * 2], RING_IN[i * 2 + 1], rgb, a);
            vtx(buf, m, RING_OUT[j * 2], RING_OUT[j * 2 + 1], rgb, 0.0F);
            vtx(buf, m, RING_IN[j * 2], RING_IN[j * 2 + 1], rgb, a);
        }
        close(buf);
    }

    /** 凸多边形每条边往外平移 {@code d}（负数往里），转角按斜接算，太尖的截到 3 倍 */
    private static void offset(float[] p, int n, float d, float[] out) {
        float area = 0.0F;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        // 屏幕坐标 y 朝下：面积为正是顺时针，边的外法线是 (dy, -dx)
        float sign = area >= 0.0F ? 1.0F : -1.0F;
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            float n1x = p[i * 2 + 1] - p[a * 2 + 1], n1y = -(p[i * 2] - p[a * 2]);
            float n2x = p[b * 2 + 1] - p[i * 2 + 1], n2y = -(p[b * 2] - p[i * 2]);
            float l1 = Mth.sqrt(n1x * n1x + n1y * n1y), l2 = Mth.sqrt(n2x * n2x + n2y * n2y);
            if (l1 < 1.0E-4F || l2 < 1.0E-4F) {
                out[i * 2] = p[i * 2];
                out[i * 2 + 1] = p[i * 2 + 1];
                continue;
            }
            n1x = n1x / l1 * sign;
            n1y = n1y / l1 * sign;
            n2x = n2x / l2 * sign;
            n2y = n2y / l2 * sign;
            float bx = n1x + n2x, by = n1y + n2y;
            float bl = Mth.sqrt(bx * bx + by * by);
            if (bl < 1.0E-4F) {
                bx = n1x;
                by = n1y;
                bl = 1.0F;
            }
            bx /= bl;
            by /= bl;
            float cos = bx * n1x + by * n1y;
            float len = d / Math.max(cos, 1.0F / 3.0F);
            out[i * 2] = p[i * 2] + bx * len;
            out[i * 2 + 1] = p[i * 2 + 1] + by * len;
        }
    }

    /** 矩形，四周再各有 {@code edge} 宽从实色渐到透明；{@code edge} 为 0 就是硬边 */
    private static void softRect(GuiGraphics g, Matrix4f m, float x0, float y0, float x1, float y1, float edge,
                                 int argb, boolean additive) {
        if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        float a = (argb >>> 24) / 255.0F;
        BufferBuilder buf = open(g, additive);
        if (edge <= 0.0F) {
            quad(buf, m, x0, y0, x1, y1, rgb, a, a, a, a);
        } else {
            float ox0 = x0 - edge, oy0 = y0 - edge, ox1 = x1 + edge, oy1 = y1 + edge;
            quad(buf, m, x0, y0, x1, y1, rgb, a, a, a, a);
            quad(buf, m, ox0, y0, x0, y1, rgb, 0.0F, a, a, 0.0F);
            quad(buf, m, x1, y0, ox1, y1, rgb, a, 0.0F, 0.0F, a);
            quad(buf, m, x0, oy0, x1, y0, rgb, 0.0F, 0.0F, a, a);
            quad(buf, m, x0, y1, x1, oy1, rgb, a, a, 0.0F, 0.0F);
            quad(buf, m, ox0, oy0, x0, y0, rgb, 0.0F, 0.0F, a, 0.0F);
            quad(buf, m, x1, oy0, ox1, y0, rgb, 0.0F, 0.0F, 0.0F, a);
            quad(buf, m, x1, y1, ox1, oy1, rgb, a, 0.0F, 0.0F, 0.0F);
            quad(buf, m, ox0, y1, x0, oy1, rgb, 0.0F, a, 0.0F, 0.0F);
        }
        close(buf);
    }

    /** 轴对齐四边形：四个角的透明度依次是 左上、右上、右下、左下 */
    private static void quad(BufferBuilder buf, Matrix4f m, float x0, float y0, float x1, float y1, int rgb,
                             float aTL, float aTR, float aBR, float aBL) {
        vtx(buf, m, x0, y0, rgb, aTL);
        vtx(buf, m, x1, y0, rgb, aTR);
        vtx(buf, m, x1, y1, rgb, aBR);
        vtx(buf, m, x0, y0, rgb, aTL);
        vtx(buf, m, x1, y1, rgb, aBR);
        vtx(buf, m, x0, y1, rgb, aBL);
    }

    private static void text(GuiGraphics g, Font font, String s, float x, float y, int p, int argb) {
        if ((argb >>> 24) < 5) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    /** 调试用：现在有没有横幅、等级多少 */
    @Nullable
    static String debugState() {
        return active ? "rank " + rank + (fromMercy ? " mercy k=" + wrongStab : " direct") + " t=" + nowMs() : null;
    }
}
