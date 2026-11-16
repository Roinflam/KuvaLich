package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 玄骸和奴仆的头顶名牌
 *
 * <p>渲染器在 {@code super.render(...)} 之后调用，这时 {@code poseStack} 在脚底，名牌自己往上平移到头顶 0.5 格。
 * 原版名牌已经由渲染器的 {@code shouldShowName} 关掉（被命名牌改过名的除外，那种照原版画、这里不画）。</p>
 *
 * <ul>
 *   <li><b>有主玄骸</b>：32 格内、看得见才显示。第一行全大写名字（{@code KUVA_PALE}、字距 +1、带一层墨色底影）；
 *       不是主人在看时第二行写「某某的玄骸」（灰、小一号）。</li>
 *   <li><b>无主玄骸</b>：不显示。</li>
 *   <li><b>奴仆</b>：16 格内满显示、16 → 20 格淡出。只有一行名字，字底离头顶 0.1 格：看的人自己有玄骸写
 *       「DROKAG TUZZ 奴仆」（名字取他自己那只的），还没有就写「赤毒奴仆」。不挂血条和元素状态：奴仆几刀就倒，
 *       挂着只会把名字顶得离头老高。被墙挡住时降到 α 0.3、透过墙照样画（同原版名牌）。</li>
 *   <li><b>幼体</b>：同奴仆，写「赤毒幼体」；倒地起换成屏幕标注，名牌不画。</li>
 * </ul>
 *
 * <p>倒地、处决、挣脱离场时名牌照留，高度跟着姿势走：站姿身高减去脖子此刻比站着时低了多少（渲染器画本体时量的），
 * 倒下去、跪下、被按倒、再撑起来都贴着头顶；这一帧没量到（本体没画）时按倒地后的碰撞箱。客户端选项 {@code lich_nameplates} 关掉、按了 F1、处决运镜中都不画。
 * 视线检测每只实体 250 ms 做一次，名字的大写、拆字都缓存，渲染里不分配。</p>
 *
 * <p>名牌之间在屏幕上叠住、或者名牌躲在更近的实体身后时，后面那块淡到几乎看不见（{@link #crowdFade}）：
 * 奴仆小队并排站时名字都一样，远处的名牌也会落进近处名牌那一行。谁清楚每帧统一排定一次，清楚着的那块占先，
 * 横着走过一排奴仆时不会来回换（{@link #resolve}）。名牌照常近大远小，不设最小字号。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichNameplates {

    /** 玄骸名牌的字号：原版名牌 0.025 的 0.9 倍 */
    private static final float LICH_SCALE = 0.025F * 0.9F;
    /** 奴仆名牌的字号（原版 0.025） */
    private static final float THRALL_SCALE = 0.020F;
    /** 玄骸名牌离头顶多高（格） */
    private static final float ABOVE_HEAD = 0.5F;
    /** 奴仆、幼体名牌的上沿离头顶多高（格）：只有一行字，字底离头顶 0.1 格 */
    private static final float THRALL_ABOVE_HEAD = 0.1F + 9.0F * THRALL_SCALE;
    /** 被更近的名牌、更近的实体身子在屏幕上挡住时淡到多少 */
    private static final float CROWDED_ALPHA = 0.12F;
    /** 变淡用多久（毫秒） */
    private static final float CROWD_OUT_MS = 80.0F;
    /** 复原前先等多久（毫秒）：换人的那一下等旧的先退下去，不和新的一起半透明叠成一团 */
    private static final long CROWD_RISE_DELAY_MS = 60L;
    /** 复原用多久（毫秒） */
    private static final float CROWD_IN_MS = 140.0F;
    /**
     * 正清楚着的名牌占的先：别的名牌要比它近出这么多（格；远处按距离的比例，取大的）才轮到别人。
     * 横着走过一排差不多远的奴仆时，谁最近每走一两格就换一次，不让一下就整排跟着来回翻
     */
    private static final double CROWD_STICKY_MIN = 0.6D;
    private static final double CROWD_STICKY_FRAC = 0.08D;
    /** 实体身子要比名牌近出这么多（格）才算挡在前面：并排站的身子不挡旁边的名牌 */
    private static final double BODY_DEPTH = 0.75D;
    /** 两块名牌之间至少要空出这么多（字体像素，按后面那块的字号），贴着也算挡 */
    private static final float CROWD_PAD = 2.0F;
    /**
     * 奴仆站着时模型头顶离脚底多高（格）：kuva_slave.geo.json 里 Head 骨骼最高到 40.75 像素。
     * 奴仆的碰撞箱有 2.9 格，比模型高出三分之一格，名牌按碰撞箱放会悬在头上
     */
    private static final float THRALL_HEAD_TOP = 40.75F / 16.0F;
    private static final double LICH_RANGE = 32.0D;
    /** 玄骸名牌在最后这 4 格里淡出，免得走到 32 格一下没了 */
    private static final double LICH_FADE_FROM = 28.0D;
    private static final double THRALL_FULL = 16.0D;
    private static final double THRALL_RANGE = 20.0D;
    /** 被挡住时的透明度 */
    private static final float OCCLUDED_ALPHA = 0.3F;
    /** 字距（字体像素） */
    private static final float SPACING = 1.0F;
    /** 多久重做一次视线检测 */
    private static final long LOS_INTERVAL_MS = 250L;
    private static final long FORGET_MS = 10_000L;
    /** 屏幕顶上 Boss 条那块的半宽至少这么多（GUI 像素，原版条宽 182），按屏宽算的比例更大时取比例：玄骸条占屏宽 0.264 */
    private static final float BOSS_BAND_MIN_HALF = 91.0F;
    private static final float BOSS_BAND_HALF_OF_WIDTH = 0.16F;
    /** 名牌叠进 Boss 条多深完全淡掉：至少这么多 GUI 像素，近处字大时按一行字高 */
    private static final float BOSS_FADE_MIN_SPAN = 4.0F;

    /** 每只实体缓存的名牌内容 */
    private static final class Plate {
        String source = "";
        String source2 = "";
        String line1 = "";
        String line2 = "";
        String lang = "";
        long losAtMs = Long.MIN_VALUE / 4;
        boolean los;
        long lastUseMs;
        /** 被挡住变淡的透明度倍数（1 = 没被挡），按 {@link #CROWD_OUT_MS} / {@link #CROWD_IN_MS} 慢慢走 */
        float crowd = 1.0F;
        long crowdAtMs = Long.MIN_VALUE;
        /** 不再被挡、开始等着复原的时刻（{@link Long#MIN_VALUE} = 没在等） */
        long riseFromMs = Long.MIN_VALUE;
        /** 最近一次排定时这块是不是清楚的那块（下一次排定时占先） */
        boolean winner = true;
        /** 最近一次排定是第几帧（{@link #frameNo}） */
        long resolvedFrame = -1L;
    }

    /** 一块名牌某一帧在屏幕上占的框（GUI 像素），下一帧开头拿来排定谁挡谁 */
    private static final class Box {
        int id;
        double dist;
        /** 排序用：离镜头的距离，上一次是清楚的再减去占先的那一截 */
        double key;
        /** 名牌本身 */
        float x0, y0, x1, y1;
        /** 实体的身子（头顶到脚底、碰撞箱那么宽），挡后面的名牌 */
        float sx0, sy0, sx1, sy1;
        /** 名牌之间要空出的间距（GUI 像素） */
        float pad;
        /** 淡到一半以下的（隔着墙、快出范围）不去挡别人的名牌 */
        boolean blocking;
        /** 排定的结果：清楚 */
        boolean win;
    }

    /** 这一帧画名牌时记下的框；下一帧开头换成 {@link #lastBoxes} 统一排定。用上一帧的框，免得先排序再画 */
    private static Box[] boxes = new Box[16];
    private static int boxN;
    private static Box[] lastBoxes = new Box[16];
    private static int lastBoxN;
    /** 排定时按 key 排好的顺序（复用，不分配） */
    private static Box[] order = new Box[16];
    /** 第几帧（每帧开头加一） */
    private static long frameNo;
    /** 这一帧主镜头的投影；光影的阴影那一遍也会画实体，投影对不上的那一遍不记框、不改透明度 */
    private static final Matrix4f MAIN_PROJ = new Matrix4f();

    private static final Int2ObjectOpenHashMap<Plate> PLATES = new Int2ObjectOpenHashMap<>();
    private static long lastPruneMs;

    /**
     * 倒地、处决里的实体这一帧摆出来的头顶高度（名牌底下那一点，格）和记下的时刻。渲染器画本体时记，
     * 倒地标记在世界渲染后段读，所以读到的就是这一帧的；名牌选项关着也照记
     */
    private static final Int2FloatOpenHashMap POSE_TOP = new Int2FloatOpenHashMap();
    private static final Int2LongOpenHashMap POSE_TOP_AT = new Int2LongOpenHashMap();
    /** 超过这么久没记（这一帧没画它）就不信，退回碰撞箱 */
    private static final long POSE_TOP_STALE_MS = 250L;

    private LichNameplates() {
    }

    /**
     * 画名牌
     *
     * @return 画了返回 true；选项关掉或不该显示时返回 false
     */
    public static boolean render(AbstractKuva entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                 float partialTick, float headDrop) {
        notePoseTop(entity, headDrop);
        if (!EffectsDisplayConfig.lichNameplates() || LichPuppet.isPuppet(entity) || entity.getId() < 0
                || entity.hasCustomName() || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof KuvaLarvlingEntity && entity.getMercyState() != MercyState.NONE) {
            // 倒地的幼体有屏幕标注（LarvlingTag），小标题已经说明是什么，名牌收起来免得和标注挤在头顶；
            // 处决时也不再冒出来
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // 奴仆处决是第一人称，飞跃下刺时镜头就在它头顶，名牌会糊满整屏
        if (player == null || mc.options.hideGui || MercyClient.isCinematic()
                || MercyClient.isLocalTarget(entity.getId()) || entity.isInvisibleTo(player)) {
            return false;
        }
        float fade = LichRenderState.alpha(entity, partialTick);
        if (fade <= 0.02F) {
            return false;
        }
        double dist = Math.sqrt(mc.getEntityRenderDispatcher().distanceToSqr(entity));
        long now = KuvaFxDirector.clockMs();
        prune(now);
        float height = plateHeight(entity, headDrop);
        if (entity instanceof KuvaMasterEntity lich) {
            return renderLich(lich, poseStack, buffer, dist, fade, now, mc, player, height);
        }
        if (entity instanceof KuvaSlaveEntity) {
            return renderThrall(entity, poseStack, buffer, dist, fade, now, mc, player, height);
        }
        return false;
    }

    // ==================== 玄骸 ====================

    private static boolean renderLich(KuvaMasterEntity lich, PoseStack ps, MultiBufferSource buffer, double dist,
                                      float fade, long now, Minecraft mc, LocalPlayer player, float height) {
        boolean ally = lich.isAlly();
        if (!lich.isOwned() && !ally || dist > LICH_RANGE) {
            return false;
        }
        Plate p = plateOf(lich.getId(), now);
        if (!lineOfSight(p, player, lich, now)) {
            return false;
        }
        boolean mine = (ally ? lich.isAllyOf(player) : lich.isOwner(player)) && !ArchiveDevCommands.forceOtherView;
        String name = lich.getLichName();
        String owner = mine ? "" : lich.getOwnerName();
        String lang = mc.options.languageCode;
        if (!name.equals(p.source) || !owner.equals(p.source2) || !lang.equals(p.lang) || p.line1.isEmpty()) {
            p.source = name;
            p.source2 = owner;
            p.lang = lang;
            String upper = name.isEmpty() ? Component.translatable("entity.kuvalich.kuva_master").getString()
                    : name.toUpperCase(java.util.Locale.ROOT);
            if (ally) {
                // 「名字 · 盟友」；别人的盟友第二行写是谁的
                p.line1 = Component.translatable("kuvalich.nameplate.ally", upper).getString();
                p.line2 = mine ? "" : Component.translatable("kuvalich.nameplate.ally.other", owner).getString();
            } else {
                p.line1 = upper;
                p.line2 = mine ? "" : ownerLine(p.line1, owner);
            }
        }
        float a = fade;
        if (dist > LICH_FADE_FROM) {
            a *= (float) Mth.clamp((LICH_RANGE - dist) / (LICH_RANGE - LICH_FADE_FROM), 0.0D, 1.0D);
        }
        if (a <= 0.02F) {
            return false;
        }
        Font font = mc.font;
        float w1 = SpacedText.width(font, p.line1, SPACING);
        // 有第二行时整块往上挪一行，第二行贴着原来的位置
        float y1 = p.line2.isEmpty() ? 0.0F : -9.0F;
        float anchor = height + ABOVE_HEAD;
        if (project(ps, mc, anchor, LICH_SCALE)) {
            a *= bossBarFade(mc, w1 * 0.5F, -y1);
            // 第二行比第一行矮四分之一、往下错开一行，下沿和只有一行时差不多
            a *= crowdFade(p, lich.getId(), dist, w1 * 0.5F, -y1, p.line2.isEmpty() ? 9.0F : 8.0F,
                    lich.getBbWidth() * 0.5F / LICH_SCALE, screenY(ps, mc, height), screenY(ps, mc, 0.0F), a, now);
        }
        if (a <= 0.02F) {
            return false;
        }
        ps.pushPose();
        ps.translate(0.0F, anchor, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-LICH_SCALE, -LICH_SCALE, LICH_SCALE);
        shadowedLine(ps, buffer, font, p.line1, -w1 * 0.5F, y1, ally ? FxPalette.ALLY : FxPalette.KUVA_PALE,
                0.95F * a, Font.DisplayMode.NORMAL);
        if (!p.line2.isEmpty()) {
            ps.pushPose();
            ps.translate(0.0F, y1 + 10.0F, 0.0F);
            ps.scale(0.75F, 0.75F, 0.75F);
            float w2 = font.width(p.line2);
            font.drawInBatch(p.line2, -w2 * 0.5F, 0.0F, GuiShapes.argb(FxPalette.ASH, a), false, ps.last().pose(),
                    buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        ps.popPose();
        return true;
    }

    /** 「某某的玄骸」：从 {@code kuvalich.nameplate.lich.other} 里取名字后面那半句，去掉前面的分隔号 */
    private static String ownerLine(String nameUpper, String owner) {
        String full = Component.translatable("kuvalich.nameplate.lich.other", nameUpper, owner).getString();
        int at = full.indexOf(nameUpper);
        String rest = at >= 0 ? full.substring(at + nameUpper.length()) : full;
        int i = 0;
        while (i < rest.length() && (Character.isSpaceChar(rest.charAt(i)) || rest.charAt(i) == '·')) {
            i++;
        }
        return rest.substring(i);
    }

    // ==================== 奴仆 ====================

    private static boolean renderThrall(AbstractKuva thrall, PoseStack ps, MultiBufferSource buffer, double dist,
                                        float fade, long now, Minecraft mc, LocalPlayer player, float height) {
        if (dist > THRALL_RANGE) {
            return false;
        }
        Plate p = plateOf(thrall.getId(), now);
        // 幼体还不属于哪只玄骸，只写「赤毒幼体」
        boolean larvling = thrall instanceof KuvaLarvlingEntity;
        String lichName = larvling ? "" : ClientLichState.nameUpper();
        String lang = mc.options.languageCode;
        if (!lichName.equals(p.source) || !lang.equals(p.lang) || p.line1.isEmpty()) {
            p.source = lichName;
            p.lang = lang;
            p.line1 = lichName.isEmpty() ? Component.translatable(larvling ? "entity.kuvalich.kuva_larvling"
                    : "entity.kuvalich.kuva_slave").getString()
                    : Component.translatable("kuvalich.nameplate.thrall", lichName).getString();
        }
        float a = fade * 0.9F;
        if (dist > THRALL_FULL) {
            a *= (float) Mth.clamp((THRALL_RANGE - dist) / (THRALL_RANGE - THRALL_FULL), 0.0D, 1.0D);
        }
        boolean visible = lineOfSight(p, player, thrall, now);
        if (!visible) {
            a *= OCCLUDED_ALPHA;
        }
        if (a <= 0.02F) {
            return false;
        }
        Font.DisplayMode mode = visible ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH;
        Font font = mc.font;
        float top = height + THRALL_ABOVE_HEAD;
        float w = SpacedText.width(font, p.line1, SPACING);
        if (project(ps, mc, top, THRALL_SCALE)) {
            a *= bossBarFade(mc, w * 0.5F, 0.0F);
            a *= crowdFade(p, thrall.getId(), dist, w * 0.5F, 0.0F, 9.0F,
                    thrall.getBbWidth() * 0.5F / THRALL_SCALE, screenY(ps, mc, height), screenY(ps, mc, 0.0F), a, now);
        }
        if (a <= 0.02F) {
            return false;
        }
        ps.pushPose();
        ps.translate(0.0F, top, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-THRALL_SCALE, -THRALL_SCALE, THRALL_SCALE);
        shadowedLine(ps, buffer, font, p.line1, -w * 0.5F, 0.0F, FxPalette.BONE, a, mode);
        ps.popPose();
        return true;
    }

    // ==================== 小工具 ====================

    private static final Matrix4f PROJ_POSE = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();

    /** {@link #project} 的结果：名牌锚点在屏幕上的位置（GUI 像素）、一个字体像素此刻占多少 GUI 像素 */
    private static float projX, projY, projUnit;
    /** 这次投影用的是不是主镜头的投影（光影的阴影那一遍不是） */
    private static boolean projMain;

    /**
     * 把名牌锚点（脚底往上 {@code lift} 格）投到屏幕上，结果放进 {@link #projX} / {@link #projY} / {@link #projUnit}。
     * 用此刻的投影矩阵乘锚点的姿势矩阵；锚点在镜头后面时返回 false
     *
     * @param scale 名牌的字号（格 / 字体像素）
     */
    private static boolean project(PoseStack ps, Minecraft mc, float lift, float scale) {
        ps.pushPose();
        ps.translate(0.0F, lift, 0.0F);
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        PROJ_POSE.set(proj).mul(ps.last().pose());
        ps.popPose();
        CLIP.set(0.0F, 0.0F, 0.0F, 1.0F).mul(PROJ_POSE);
        if (CLIP.w <= 0.05F) {
            return false;
        }
        float gw = mc.getWindow().getGuiScaledWidth();
        float gh = mc.getWindow().getGuiScaledHeight();
        projX = (CLIP.x / CLIP.w * 0.5F + 0.5F) * gw;
        projY = (0.5F - CLIP.y / CLIP.w * 0.5F) * gh;
        projUnit = scale * 0.5F * gh * proj.m11() / CLIP.w;
        // 只比视角和画幅：光影可能给主镜头的投影加一点抖动；阴影那一遍是正交投影（m33 = 1），一比就分得开
        projMain = proj.m33() == 0.0F && Math.abs(proj.m11() - MAIN_PROJ.m11()) < 1.0E-3F
                && Math.abs(proj.m00() - MAIN_PROJ.m00()) < 1.0E-3F;
        return true;
    }

    /**
     * 名牌投影到屏幕顶上 Boss 条那块时淡出：离奴仆很近、平视时，头顶的名牌会投到屏幕最上沿，和 Boss 条叠成一团。
     * 名牌上沿叠进 Boss 条下沿越深越淡，叠进一行字高就完全不画；横向只算屏幕中间 Boss 条那一段，偏到两边的照常画。
     * Boss 条的下沿取上一帧的（世界比 HUD 先画）。要先 {@link #project}
     *
     * @param halfW 名牌半宽（字体像素）
     * @param up    名牌在锚点上方还伸出多少（字体像素；两行的玄骸名牌第一行在锚点上面）
     */
    private static float bossBarFade(Minecraft mc, float halfW, float up) {
        int bottom = BossBarTracker.recentBottomY();
        if (bottom <= 0) {
            return 1.0F;
        }
        float gw = mc.getWindow().getGuiScaledWidth();
        float unit = projUnit;
        float span = Math.max(BOSS_FADE_MIN_SPAN, 9.0F * unit);
        float band = Math.max(BOSS_BAND_MIN_HALF, BOSS_BAND_HALF_OF_WIDTH * gw);
        float v = (bottom - (projY - up * unit)) / span;
        float h = (band + halfW * unit - Math.abs(projX - gw * 0.5F)) / span;
        return 1.0F - Mth.clamp(v, 0.0F, 1.0F) * Mth.clamp(h, 0.0F, 1.0F);
    }

    private static final Vector4f CLIP_Y = new Vector4f();

    /** 脚底往上 {@code lift} 格那一点投到屏幕上的 y（GUI 像素）；在镜头后面时当作屏幕最下沿 */
    private static float screenY(PoseStack ps, Minecraft mc, float lift) {
        CLIP_Y.set(0.0F, lift, 0.0F, 1.0F).mul(ps.last().pose()).mul(RenderSystem.getProjectionMatrix());
        float gh = mc.getWindow().getGuiScaledHeight();
        return CLIP_Y.w <= 0.05F ? gh : (0.5F - CLIP_Y.y / CLIP_Y.w * 0.5F) * gh;
    }

    /**
     * 被挡住时淡下去：这块名牌在屏幕上和一块更靠前、清楚着的名牌叠上（或者只隔不到 {@link #CROWD_PAD} 个字体像素），
     * 或者落在一只更近的实体身上（会被它的头、身子切掉一截），就淡到 {@link #CROWDED_ALPHA}，不挡了再慢慢回来。
     * 奴仆小队并排站时名字都一样，只留一块；远处的名牌落进近处名牌那一行、躲在近处奴仆身后时让开。
     *
     * <p>谁清楚由每帧开头的 {@link #resolve} 拿上一帧所有的框统一排定（这里只按结果淡入淡出、记下这一帧的框）；
     * 上一帧没画过的新名牌，就地和上一帧排定的结果比一次。要先 {@link #project}；不是主镜头那一遍（光影的阴影）
     * 只读当前值，不记框。</p>
     *
     * @param halfW     名牌半宽（字体像素）
     * @param up        名牌在锚点上方伸出多少（字体像素）
     * @param down      名牌在锚点下方伸出多少（字体像素）
     * @param bodyHalfW 实体身子的半宽（字体像素，按碰撞箱宽）
     * @param bodyTop   实体头顶在屏幕上的 y（GUI 像素）
     * @param bodyFeet  实体脚底在屏幕上的 y（GUI 像素）
     * @param baseAlpha 算这一项之前的透明度
     * @return 透明度倍数
     */
    private static float crowdFade(Plate p, int id, double dist, float halfW, float up, float down,
                                   float bodyHalfW, float bodyTop, float bodyFeet, float baseAlpha, long now) {
        if (!projMain) {
            return p.crowd;
        }
        float u = projUnit;
        float x0 = projX - halfW * u, x1 = projX + halfW * u;
        float y0 = projY - up * u, y1 = projY + down * u;
        float pad = CROWD_PAD * u;
        boolean crowded;
        if (p.resolvedFrame == frameNo) {
            crowded = !p.winner;
        } else {
            crowded = blocked(id, dist, dist, x0, y0, x1, y1, pad, lastBoxes, lastBoxN);
            p.winner = !crowded;
            p.resolvedFrame = frameNo;
        }
        float target = crowded ? CROWDED_ALPHA : 1.0F;
        long dt = now - p.crowdAtMs;
        if (p.crowdAtMs == Long.MIN_VALUE || dt < 0L || dt > 1000L) {
            // 刚进视野、或者好久没画过：直接到位，不从头淡一遍
            p.crowd = target;
            p.riseFromMs = Long.MIN_VALUE;
        } else if (target > p.crowd) {
            if (p.riseFromMs == Long.MIN_VALUE) {
                p.riseFromMs = now + CROWD_RISE_DELAY_MS;
            }
            long rise = now - Math.max(p.crowdAtMs, p.riseFromMs);
            if (rise > 0L) {
                p.crowd = Math.min(target, p.crowd + rise / CROWD_IN_MS * (1.0F - CROWDED_ALPHA));
            }
        } else {
            p.crowd = Math.max(target, p.crowd - dt / CROWD_OUT_MS * (1.0F - CROWDED_ALPHA));
            p.riseFromMs = Long.MIN_VALUE;
        }
        p.crowdAtMs = now;
        Box b = addBox(id);
        b.dist = dist;
        b.x0 = x0;
        b.y0 = y0;
        b.x1 = x1;
        b.y1 = y1;
        b.sx0 = projX - bodyHalfW * u;
        b.sx1 = projX + bodyHalfW * u;
        b.sy0 = Math.min(bodyTop, bodyFeet);
        b.sy1 = Math.max(bodyTop, bodyFeet);
        b.pad = pad;
        b.blocking = baseAlpha >= 0.5F;
        return p.crowd;
    }

    /**
     * 一块名牌（框 x0..y1，离镜头 {@code dist}，排序值 {@code key}）在 {@code arr} 的前 {@code n} 块里有没有被挡住：
     * 排在它前面（排序值更小，一样时编号小的在前）、清楚着、淡得不多的名牌和它叠上；或者有一只比它近出
     * {@link #BODY_DEPTH} 的实体身子和它叠上（身子不管自己的名牌清不清楚，模型总是画的）
     */
    private static boolean blocked(int id, double dist, double key, float x0, float y0, float x1, float y1, float pad,
                                   Box[] arr, int n) {
        for (int i = 0; i < n; i++) {
            Box o = arr[i];
            if (o.id == id) {
                continue;
            }
            boolean ahead = o.key < key || (o.key == key && o.id < id);
            if (ahead && o.win && o.blocking
                    && o.x0 < x1 + pad && x0 - pad < o.x1 && o.y0 < y1 + pad && y0 - pad < o.y1) {
                return true;
            }
            if (o.dist < dist - BODY_DEPTH && o.sx0 < x1 && x0 < o.sx1 && o.sy0 < y1 && y0 < o.sy1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拿上一帧所有名牌的框排定这一帧谁清楚：按离镜头的距离排，上一次清楚的那块减去占先的一截
     * （{@link #CROWD_STICKY_MIN} / {@link #CROWD_STICKY_FRAC}），从前往后一块块看，没被前面清楚的挡住就清楚。
     * 一次排完整组，一块让位时后面跟着变的也同一帧定下来，不会隔一帧翻一块
     */
    private static void resolve() {
        int n = lastBoxN;
        if (order.length < n) {
            order = new Box[lastBoxes.length];
        }
        for (int i = 0; i < n; i++) {
            Box b = lastBoxes[i];
            Plate p = PLATES.get(b.id);
            boolean wasWinner = p == null || p.winner;
            b.key = wasWinner ? b.dist - Math.max(CROWD_STICKY_MIN, b.dist * CROWD_STICKY_FRAC) : b.dist;
            b.win = false;
            // 插入排序：一帧就几块到几十块，不分配
            int j = i;
            while (j > 0 && (order[j - 1].key > b.key || (order[j - 1].key == b.key && order[j - 1].id > b.id))) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = b;
        }
        for (int i = 0; i < n; i++) {
            Box b = order[i];
            // 排在前面的已经定了；身子不分先后，整组都看
            b.win = !blocked(b.id, b.dist, b.key, b.x0, b.y0, b.x1, b.y1, b.pad, order, n);
            Plate p = PLATES.get(b.id);
            if (p != null) {
                p.winner = b.win;
                p.resolvedFrame = frameNo;
            }
        }
    }

    /** 这一帧这只实体的框（这一帧已经记过就覆盖：光影多画一遍等） */
    private static Box addBox(int id) {
        Box b = null;
        for (int i = 0; i < boxN; i++) {
            if (boxes[i].id == id) {
                b = boxes[i];
                break;
            }
        }
        if (b == null) {
            if (boxN == boxes.length) {
                boxes = Arrays.copyOf(boxes, boxN * 2);
            }
            b = boxes[boxN];
            if (b == null) {
                b = new Box();
                boxes[boxN] = b;
            }
            boxN++;
        }
        b.id = id;
        return b;
    }

    /** 每帧开头把上一帧记的框换到 {@link #lastBoxes} 并排定谁清楚，这一帧从空的开始记 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Box[] t = lastBoxes;
        lastBoxes = boxes;
        lastBoxN = boxN;
        boxes = t;
        boxN = 0;
        frameNo++;
        resolve();
    }

    /** 记下这一帧主镜头的投影（实体在实心方块之后画），用来认出光影阴影那一遍 */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            MAIN_PROJ.set(event.getProjectionMatrix());
        }
    }

    /**
     * 名牌底下那一点的高度（不含离头顶的空隙）：平时是站姿身高；倒地、处决、离场时是站姿身高减去脖子下降的量，
     * 量不到时退回倒地后的碰撞箱（它本来就是按倒地姿势的高度缩的）
     */
    private static float plateHeight(AbstractKuva e, float headDrop) {
        boolean posed = posed(e);
        if (posed && Float.isNaN(headDrop)) {
            return e.getBbHeight();
        }
        float standing = standingTop(e);
        return posed ? Mth.clamp(standing - headDrop, 0.3F, standing + 0.5F) : standing;
    }

    /** 站着时头顶离脚底多高（格）：奴仆按模型（见 {@link #THRALL_HEAD_TOP}），其余按碰撞箱 */
    private static float standingTop(AbstractKuva e) {
        if (e instanceof KuvaSlaveEntity && !(e instanceof KuvaLarvlingEntity)) {
            return THRALL_HEAD_TOP * e.getScale();
        }
        return e.getType().getDimensions().height * e.getScale();
    }

    /**
     * 姿势不是平常那套：倒地、处决、离场，或者本地正在对它演一场处决
     * （开发命令对没倒地的目标直接演时，同步状态一直是 NONE，动画却在跪、躺）
     */
    private static boolean posed(AbstractKuva e) {
        return e.getMercyState() != MercyState.NONE || MercyClient.elapsedMs(e.getId()) >= 0L;
    }

    private static void notePoseTop(AbstractKuva e, float headDrop) {
        if (e.getId() < 0 || !posed(e)) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        POSE_TOP.put(e.getId(), plateHeight(e, headDrop));
        POSE_TOP_AT.put(e.getId(), now);
        // 名牌选项关着时下面画名牌那条路走不到，清理也在这里顺带做
        prune(now);
    }

    /**
     * 倒地、处决里的实体此刻头顶的高度（离脚底，格）：这一帧画过本体就按模型实际的姿势，否则按碰撞箱
     */
    public static double poseTop(AbstractKuva e) {
        int id = e.getId();
        long at = POSE_TOP_AT.getOrDefault(id, Long.MIN_VALUE);
        if (at != Long.MIN_VALUE && KuvaFxDirector.clockMs() - at <= POSE_TOP_STALE_MS) {
            return POSE_TOP.get(id);
        }
        return e.getBbHeight();
    }

    /**
     * 名牌最上沿离头顶多高（格）：倒地标记从这里再往上放，免得晶体压在字上。
     * 不画名牌的（选项关着、F1、没主的玄骸）返回 0，标记照旧贴着头顶
     */
    public static double plateTopAboveHead(AbstractKuva e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !EffectsDisplayConfig.lichNameplates()
                || e.hasCustomName() || LichPuppet.isPuppet(e)) {
            return 0.0D;
        }
        if (e instanceof KuvaMasterEntity lich) {
            if (!lich.isOwned() && !lich.isAlly()) {
                return 0.0D;
            }
            // 别人的玄骸多一行「某某的玄骸」，整块往上挪一行
            boolean mine = (lich.isAlly() ? lich.isAllyOf(player) : lich.isOwner(player))
                    && !ArchiveDevCommands.forceOtherView;
            return ABOVE_HEAD + (mine ? 0.0F : 9.0F * LICH_SCALE);
        }
        // 幼体倒地后名牌不画（换成屏幕标注）
        return e instanceof KuvaSlaveEntity && !(e instanceof KuvaLarvlingEntity) ? THRALL_ABOVE_HEAD : 0.0D;
    }

    /**
     * 一行带墨色底影的字：先在 (+1, +1) 画 {@code INK} α 0.6 的影，再把正文往镜头那边挪一点画上去。
     *
     * <p>乘过镜头朝向以后 +z 是镜头看出去的方向（和界面里相反），所以往镜头挪是 −z；挪成 +z 时正文落到影子后面，
     * 半透明文字按远近排序后影子压在笔画上，中文笔画密，一半笔画被压暗，看着发糊。
     * 挪 0.25 个字体像素：32 格外深度缓冲也分得开，大小的变化看不出来。</p>
     */
    private static void shadowedLine(PoseStack ps, MultiBufferSource buffer, Font font, String text, float x, float y,
                                     int rgb, float alpha, Font.DisplayMode mode) {
        SpacedText.drawInWorld(ps, buffer, font, text, x + 1.0F, y + 1.0F, SPACING,
                GuiShapes.argb(FxPalette.INK, 0.6F * alpha), mode, LightTexture.FULL_BRIGHT);
        ps.pushPose();
        ps.translate(0.0F, 0.0F, -0.25F);
        SpacedText.drawInWorld(ps, buffer, font, text, x, y, SPACING, GuiShapes.argb(rgb, alpha), mode,
                LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private static Plate plateOf(int id, long now) {
        Plate p = PLATES.get(id);
        if (p == null) {
            p = new Plate();
            PLATES.put(id, p);
        }
        p.lastUseMs = now;
        return p;
    }

    /** 视线检测结果按实体缓存 250 ms */
    private static boolean lineOfSight(Plate p, LocalPlayer player, AbstractKuva e, long now) {
        if (now - p.losAtMs >= LOS_INTERVAL_MS || now < p.losAtMs) {
            p.losAtMs = now;
            p.los = player.hasLineOfSight(e);
        }
        return p.los;
    }

    private static void prune(long now) {
        if (now - lastPruneMs < 5000L && now >= lastPruneMs) {
            return;
        }
        lastPruneMs = now;
        ObjectIterator<Int2ObjectMap.Entry<Plate>> it = PLATES.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastUseMs > FORGET_MS) {
                it.remove();
            }
        }
        ObjectIterator<Int2LongMap.Entry> tops = POSE_TOP_AT.int2LongEntrySet().fastIterator();
        while (tops.hasNext()) {
            Int2LongMap.Entry t = tops.next();
            if (now - t.getLongValue() > FORGET_MS) {
                POSE_TOP.remove(t.getIntKey());
                tops.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PLATES.clear();
        POSE_TOP.clear();
        POSE_TOP_AT.clear();
        boxN = 0;
        lastBoxN = 0;
    }
}
