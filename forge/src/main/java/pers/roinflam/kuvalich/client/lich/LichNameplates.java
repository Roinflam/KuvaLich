package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.ElementStatusRow;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;

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
 *   <li><b>奴仆</b>：16 格内满显示、16 → 20 格淡出。看的人自己有玄骸写「DROKAG TUZZ 奴仆」（名字取他自己那只的），
 *       还没有就写「赤毒奴仆」；下面一条细血条和一行元素状态。被墙挡住时整块降到 α 0.3、透过墙照样画（同原版名牌）。</li>
 * </ul>
 *
 * <p>倒地、处决、挣脱离场时名牌照留，高度跟着姿势走：站姿身高减去脖子此刻比站着时低了多少（渲染器画本体时量的），
 * 倒下去、跪下、被按倒、再撑起来都贴着头顶；这一帧没量到（本体没画）时按倒地后的碰撞箱。客户端选项 {@code lich_nameplates} 关掉、按了 F1、处决运镜中都不画。
 * 视线检测每只实体 250 ms 做一次，名字的大写、拆字都缓存，渲染里不分配。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichNameplates {

    /** 玄骸名牌的字号：原版名牌 0.025 的 0.9 倍 */
    private static final float LICH_SCALE = 0.025F * 0.9F;
    /** 奴仆名牌的字号（原版 0.025） */
    private static final float THRALL_SCALE = 0.020F;
    /** 名牌离头顶多高（格） */
    private static final float ABOVE_HEAD = 0.5F;
    private static final double LICH_RANGE = 32.0D;
    /** 玄骸名牌在最后这 4 格里淡出，免得走到 32 格一下没了 */
    private static final double LICH_FADE_FROM = 28.0D;
    private static final double THRALL_FULL = 16.0D;
    private static final double THRALL_RANGE = 20.0D;
    /** 被挡住时的透明度 */
    private static final float OCCLUDED_ALPHA = 0.3F;
    /** 字距（字体像素） */
    private static final float SPACING = 1.0F;
    /** 奴仆血条：宽、高（字体像素） */
    private static final float BAR_W = 40.0F;
    private static final float BAR_H = 2.0F;
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
    }

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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || MercyClient.isCinematic() || entity.isInvisibleTo(player)) {
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
        if (!lich.isOwned() || dist > LICH_RANGE) {
            return false;
        }
        Plate p = plateOf(lich.getId(), now);
        if (!lineOfSight(p, player, lich, now)) {
            return false;
        }
        boolean mine = lich.isOwner(player) && !ArchiveDevCommands.forceOtherView;
        String name = lich.getLichName();
        String owner = mine ? "" : lich.getOwnerName();
        String lang = mc.options.languageCode;
        if (!name.equals(p.source) || !owner.equals(p.source2) || !lang.equals(p.lang) || p.line1.isEmpty()) {
            p.source = name;
            p.source2 = owner;
            p.lang = lang;
            p.line1 = name.isEmpty() ? Component.translatable("entity.kuvalich.kuva_master").getString()
                    : name.toUpperCase(java.util.Locale.ROOT);
            p.line2 = mine ? "" : ownerLine(p.line1, owner);
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
        a *= bossBarFade(ps, mc, height + ABOVE_HEAD, LICH_SCALE, w1 * 0.5F, -y1);
        if (a <= 0.02F) {
            return false;
        }
        ps.pushPose();
        ps.translate(0.0F, height + ABOVE_HEAD, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-LICH_SCALE, -LICH_SCALE, LICH_SCALE);
        shadowedLine(ps, buffer, font, p.line1, -w1 * 0.5F, y1, FxPalette.KUVA_PALE, 0.95F * a, Font.DisplayMode.NORMAL);
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
        String lichName = ClientLichState.nameUpper();
        String lang = mc.options.languageCode;
        if (!lichName.equals(p.source) || !lang.equals(p.lang) || p.line1.isEmpty()) {
            p.source = lichName;
            p.lang = lang;
            p.line1 = lichName.isEmpty() ? Component.translatable("entity.kuvalich.kuva_slave").getString()
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
        float top = height + ABOVE_HEAD;
        float w = SpacedText.width(font, p.line1, SPACING);
        a *= bossBarFade(ps, mc, top, THRALL_SCALE, Math.max(w, BAR_W) * 0.5F, 0.0F);
        if (a <= 0.02F) {
            return false;
        }
        ps.pushPose();
        ps.translate(0.0F, top, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-THRALL_SCALE, -THRALL_SCALE, THRALL_SCALE);
        shadowedLine(ps, buffer, font, p.line1, -w * 0.5F, 0.0F, FxPalette.BONE, a, mode);

        // 细血条
        float hp = Mth.clamp(thrall.getHealth() / Math.max(1.0F, thrall.getMaxHealth()), 0.0F, 1.0F);
        VertexConsumer vc = buffer.getBuffer(visible ? RenderType.textBackground() : RenderType.textBackgroundSeeThrough());
        Matrix4f m = ps.last().pose();
        float bx = -BAR_W * 0.5F;
        float by = 10.0F;
        quad(vc, m, bx, by, bx + BAR_W, by + BAR_H, 0.0F, FxPalette.KUVA_MIST, 0.6F * a / 0.9F);
        if (hp > 0.0F) {
            quad(vc, m, bx, by, bx + BAR_W * hp, by + BAR_H, 0.01F, FxPalette.KUVA, a / 0.9F);
        }
        ps.popPose();

        // 元素状态：血条下面一行（被挡住时不画，元素那行只有正常深度的画法）
        if (visible) {
            float rowTop = top - (by + BAR_H + 1.0F) * THRALL_SCALE;
            ElementStatusRow.drawInWorld(ps, buffer, thrall, rowTop, LightTexture.FULL_BRIGHT, a / 0.9F);
        }
        return true;
    }

    // ==================== 小工具 ====================

    private static final Matrix4f PROJ_POSE = new Matrix4f();
    private static final Vector4f CLIP = new Vector4f();

    /**
     * 名牌投影到屏幕顶上 Boss 条那块时淡出：离奴仆很近、平视时，头顶的名牌会投到屏幕最上沿，和 Boss 条叠成一团。
     * 名牌上沿叠进 Boss 条下沿越深越淡，叠进一行字高就完全不画；横向只算屏幕中间 Boss 条那一段，偏到两边的照常画。
     * Boss 条的下沿取上一帧的（世界比 HUD 先画）。投影用此刻的投影矩阵乘名牌锚点（脚底往上 {@code lift} 格）的姿势矩阵
     *
     * @param scale 名牌的字号（格 / 字体像素）
     * @param halfW 名牌半宽（字体像素）
     * @param up    名牌在锚点上方还伸出多少（字体像素；两行的玄骸名牌第一行在锚点上面）
     */
    private static float bossBarFade(PoseStack ps, Minecraft mc, float lift, float scale, float halfW, float up) {
        int bottom = BossBarTracker.recentBottomY();
        if (bottom <= 0) {
            return 1.0F;
        }
        ps.pushPose();
        ps.translate(0.0F, lift, 0.0F);
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        PROJ_POSE.set(proj).mul(ps.last().pose());
        ps.popPose();
        CLIP.set(0.0F, 0.0F, 0.0F, 1.0F).mul(PROJ_POSE);
        if (CLIP.w <= 0.05F) {
            return 1.0F;
        }
        float gw = mc.getWindow().getGuiScaledWidth();
        float gh = mc.getWindow().getGuiScaledHeight();
        float sx = (CLIP.x / CLIP.w * 0.5F + 0.5F) * gw;
        float sy = (0.5F - CLIP.y / CLIP.w * 0.5F) * gh;
        // 一个字体像素此刻在屏幕上占多少 GUI 像素
        float unit = scale * 0.5F * gh * proj.m11() / CLIP.w;
        float span = Math.max(BOSS_FADE_MIN_SPAN, 9.0F * unit);
        float band = Math.max(BOSS_BAND_MIN_HALF, BOSS_BAND_HALF_OF_WIDTH * gw);
        float v = (bottom - (sy - up * unit)) / span;
        float h = (band + halfW * unit - Math.abs(sx - gw * 0.5F)) / span;
        return 1.0F - Mth.clamp(v, 0.0F, 1.0F) * Mth.clamp(h, 0.0F, 1.0F);
    }

    /**
     * 名牌底下那一点的高度（不含离头顶的空隙）：平时是碰撞箱高；倒地、处决、离场时是站姿身高减去脖子下降的量，
     * 量不到时退回倒地后的碰撞箱（它本来就是按倒地姿势的高度缩的）
     */
    private static float plateHeight(AbstractKuva e, float headDrop) {
        if (!posed(e) || Float.isNaN(headDrop)) {
            return e.getBbHeight();
        }
        float standing = e.getType().getDimensions().height * e.getScale();
        return Mth.clamp(standing - headDrop, 0.3F, standing + 0.5F);
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
            if (!lich.isOwned()) {
                return 0.0D;
            }
            // 别人的玄骸多一行「某某的玄骸」，整块往上挪一行
            boolean mine = lich.isOwner(player) && !ArchiveDevCommands.forceOtherView;
            return ABOVE_HEAD + (mine ? 0.0F : 9.0F * LICH_SCALE);
        }
        return e instanceof KuvaSlaveEntity ? ABOVE_HEAD : 0.0D;
    }

    /** 一行带墨色底影的字：先在 (+1, +1) 画 {@code INK} α 0.6 的影，再往前挪一点画正文（同原版带影文字的做法） */
    private static void shadowedLine(PoseStack ps, MultiBufferSource buffer, Font font, String text, float x, float y,
                                     int rgb, float alpha, Font.DisplayMode mode) {
        SpacedText.drawInWorld(ps, buffer, font, text, x + 1.0F, y + 1.0F, SPACING,
                GuiShapes.argb(FxPalette.INK, 0.6F * alpha), mode, LightTexture.FULL_BRIGHT);
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 0.03F);
        SpacedText.drawInWorld(ps, buffer, font, text, x, y, SPACING, GuiShapes.argb(rgb, alpha), mode,
                LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb,
                             float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
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
    }
}
