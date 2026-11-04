package pers.roinflam.kuvalich.client.larvling;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.HoloShapes;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.client.lich.LichNameplates;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 幼体倒地时头顶亮出来的武器：处决它诞生的玄骸拿的就是这把
 *
 * <p>原作的幼体倒地时会亮出武器、元素加成和幻纹；这里诞生时只定下武器（元素和等级要到灭骸那一刻才掷，也没有幻纹），
 * 所以只亮武器。摆在倒地标记的上面（标记远了会放大，这里跟着它的上沿走），24 格内画，20 格外渐隐，
 * 被方块挡住照常挡。两种样子，开发命令 {@code /kuvafxc larvling style} 切换：</p>
 * <ul>
 *   <li><b>甲 浮刃</b>：武器本身浮在空中，6 秒转一圈、上下轻轻起伏，下面一行武器名（淡赤毒色、墨色底影）。
 *       出现时从小长到原大，收起时缩回去；</li>
 *   <li><b>乙 全息牌</b>（默认，原作倒地时也是武器图标加名字的小卡；离得近时不会出画面）：一块暗红玻璃小牌，左上、右下切角，上沿亮赤毒线、下沿暗赤毒线；左边方框里是武器图标，
 *       右边小字「玄骸武器」和剩余秒数、下面武器名，最底一条倒计时细条（最后 10 秒按 2 Hz 呼吸，同倒地标记的圆环）。
 *       出现、收起都是淡入淡出。</li>
 * </ul>
 *
 * <p>只在倒地（DOWNED）时画；开始处决、消散就收。F1、处决镜头里不画。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LarvlingOverhead {

    public static final int STYLE_FLOAT = 0;
    public static final int STYLE_CARD = 1;
    /** 现在用哪一种（开发命令切），默认乙 */
    static int style = STYLE_CARD;

    private static final double RANGE = 24.0D;
    private static final double FADE_FROM = 20.0D;
    /** 离倒地标记上沿多高（格） */
    private static final float GAP = 0.08F;
    /**
     * 倒地标记的尺寸，和 {@code MercyPrompt} 画的一致：最下沿在名牌上沿（没名牌时是头顶）上方 0.1 格，整体高 0.52 格，
     * 投影到屏幕上不到 0.046 屏高时以下沿为基准往上放大
     */
    private static final double MARKER_GAP = 0.1D;
    private static final float MARK_HEIGHT = 0.52F;
    private static final float MARK_MIN_SCREEN_H = 0.046F;
    /** 出现、收起各多长（毫秒） */
    private static final float SHOW_MS = 220.0F;
    private static final float HIDE_MS = 140.0F;

    // 甲
    /** 武器多大（格） */
    private static final float FLOAT_SIZE = 0.7F;
    private static final float SPIN_MS = 6000.0F;
    private static final float BOB = 0.04F;
    private static final float BOB_MS = 2400.0F;
    private static final float NAME_SCALE = 0.02F;
    /** 名字那行和武器之间空多少（格） */
    private static final float NAME_GAP = 0.05F;

    // 乙（下面的尺寸都是字体像素）
    private static final float CARD_SCALE = 0.016F;
    private static final float PAD = 5.0F;
    private static final float ICON = 16.0F;
    private static final float ICON_BOX = 20.0F;
    private static final float COL_GAP = 5.0F;
    private static final float CARD_H = 30.0F;
    private static final float CHAMFER = 4.0F;
    private static final float CAPTION_SCALE = 0.75F;
    private static final float MIN_TEXT_W = 48.0F;
    private static final float BAR_H = 1.5F;
    private static final float GLASS_ALPHA = 0.82F;

    /** 每只幼体的出现进度 */
    private static final class Slot {
        float show;
        long lastMs;
    }

    private static final Int2ObjectOpenHashMap<Slot> SLOTS = new Int2ObjectOpenHashMap<>();
    private static long lastPruneMs;

    // 文字缓存：武器名按语言，秒数按数值
    private static String[] names = new String[0];
    private static String namesLang = "";
    private static String caption = "";
    private static int secsShown = -1;
    private static String secsText = "";

    private LarvlingOverhead() {
    }

    /** 渲染器在画完本体（和名牌）之后调，{@code ps} 在实体脚底，没有转动 */
    public static void render(KuvaLarvlingEntity e, PoseStack ps, MultiBufferSource buffer, float pt) {
        if (e.getId() < 0) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        prune(now);
        MercyState state = e.getMercyState();
        boolean want = state == MercyState.DOWNED && e.isAlive();
        Slot s = SLOTS.get(e.getId());
        if (s == null) {
            if (!want) {
                return;
            }
            s = new Slot();
            s.lastMs = now;
            SLOTS.put(e.getId(), s);
        }
        float dt = Mth.clamp(now - s.lastMs, 0L, 100L);
        s.lastMs = now;
        if (state == MercyState.EXECUTING) {
            // 开始处决就直接收，不留在镜头里
            s.show = 0.0F;
        } else {
            s.show = want ? Math.min(1.0F, s.show + dt / SHOW_MS) : Math.max(0.0F, s.show - dt / HIDE_MS);
        }
        if (s.show <= 0.0F) {
            // 刚建的那一帧进度还是 0，倒地中就留着，下一帧才开始长；只有收完了才删
            if (!want) {
                SLOTS.remove(e.getId());
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || MercyClient.isCinematic() || e.isInvisibleTo(mc.player)) {
            return;
        }
        ItemStack stack = LarvlingWeaponLayer.stackOf(e.getWeaponIndex());
        if (stack == null) {
            return;
        }
        double dist = Math.sqrt(mc.getEntityRenderDispatcher().distanceToSqr(e));
        if (dist > RANGE) {
            return;
        }
        float a = LichRenderState.alpha(e, pt);
        if (dist > FADE_FROM) {
            a *= (float) ((RANGE - dist) / (RANGE - FADE_FROM));
        }
        if (a <= 0.02F) {
            return;
        }
        float base = markerTop(e, pt, mc) + GAP;
        String name = nameOf(e.getWeaponIndex(), stack, mc);
        if (style == STYLE_CARD) {
            drawCard(e, stack, name, ps, buffer, base, a * ease(s.show), pt, now, mc);
        } else {
            drawFloat(e, stack, name, ps, buffer, base, a, ease(s.show), now, mc);
        }
    }

    /** 倒地标记的上沿离脚底多高（格）：头顶 + 名牌 + 间隙 + 标记高（按此刻离相机多远，可能放大过） */
    private static float markerTop(KuvaLarvlingEntity e, float pt, Minecraft mc) {
        double bottom = LichNameplates.poseTop(e) + LichNameplates.plateTopAboveHead(e) + MARKER_GAP;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 c = cam.getPosition();
        Vector3f look = cam.getLookVector();
        double rx = Mth.lerp(pt, e.xo, e.getX()) - c.x;
        double ry = Mth.lerp(pt, e.yo, e.getY()) + bottom + MARK_HEIGHT * 0.5F - c.y;
        double rz = Mth.lerp(pt, e.zo, e.getZ()) - c.z;
        float depth = (float) (rx * look.x() + ry * look.y() + rz * look.z());
        float scale = 1.0F;
        if (depth > 0.05F) {
            float frac = MARK_HEIGHT * RenderSystem.getProjectionMatrix().m11() / (2.0F * depth);
            if (frac < MARK_MIN_SCREEN_H) {
                scale = MARK_MIN_SCREEN_H / frac;
            }
        }
        return (float) (bottom + MARK_HEIGHT * scale);
    }

    // ==================== 甲 浮刃 ====================

    private static void drawFloat(KuvaLarvlingEntity e, ItemStack stack, String name, PoseStack ps,
                                  MultiBufferSource buffer, float base, float a, float grow, long now, Minecraft mc) {
        Font font = mc.font;
        // 名字：贴着标记上沿，一行
        float top = base + 9.0F * NAME_SCALE;
        float w = font.width(name);
        ps.pushPose();
        ps.translate(0.0F, top, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-NAME_SCALE, -NAME_SCALE, NAME_SCALE);
        float ta = a * grow;
        Matrix4f m = ps.last().pose();
        font.drawInBatch(name, -w * 0.5F + 1.0F, 1.0F, argb(FxPalette.INK, 0.6F * ta), false, m, buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.translate(0.0F, 0.0F, -0.03F);
        font.drawInBatch(name, -w * 0.5F, 0.0F, argb(FxPalette.KUVA_PALE, 0.95F * ta), false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();

        // 武器：物品没法半透明，淡到一半以下就不画，出现、收起靠缩放
        if (a < 0.4F) {
            return;
        }
        float size = FLOAT_SIZE * grow;
        float bob = BOB * Mth.sin((now % (long) BOB_MS) / BOB_MS * Mth.TWO_PI);
        float cy = top + NAME_GAP + FLOAT_SIZE * 0.5F + bob;
        float spin = ((now + e.getId() * 977L) % (long) SPIN_MS) / SPIN_MS * 360.0F;
        ps.pushPose();
        ps.translate(0.0F, cy, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.scale(size, size, size);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ps, buffer, e.level(), e.getId());
        ps.popPose();
    }

    // ==================== 乙 全息牌 ====================

    private static void drawCard(KuvaLarvlingEntity e, ItemStack stack, String name, PoseStack ps,
                                 MultiBufferSource buffer, float base, float a, float pt, long now, Minecraft mc) {
        Font font = mc.font;
        float remaining = e.getMercyRemainingTicks(pt);
        String secs = secondsText(Mth.ceil(remaining / 20.0F));
        String cap = caption();
        float capW = (font.width(cap) + 6.0F + font.width(secs)) * CAPTION_SCALE;
        float textW = Math.max(MIN_TEXT_W, Math.max(font.width(name), capW));
        float w = PAD + ICON_BOX + COL_GAP + textW + PAD;
        float h = CARD_H;
        float x0 = -w * 0.5F;
        float x1 = x0 + w;
        float textX = x0 + PAD + ICON_BOX + COL_GAP;
        float textR = x1 - PAD;

        ps.pushPose();
        // 牌子下沿贴着 base
        ps.translate(0.0F, base + h * CARD_SCALE, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-CARD_SCALE, -CARD_SCALE, CARD_SCALE);
        Matrix4f m = ps.last().pose();

        // 这个坐标系里 z 越大离相机越远：底板 0，线和条往前一点，图标、字再往前
        VertexConsumer vc = buffer.getBuffer(RenderType.textBackground());
        chamferFill(vc, m, x0, 0.0F, x1, h, CHAMFER, 0.0F, HoloShapes.GLASS, GLASS_ALPHA * a);
        // 上沿亮线（含左上切角的斜边）、下沿暗线（含右下切角的斜边）
        line(vc, m, x0, CHAMFER, x0 + CHAMFER, 0.0F, 1.0F, -0.01F, FxPalette.KUVA, 0.8F * a);
        rect(vc, m, x0 + CHAMFER, 0.0F, x1, 1.0F, -0.01F, FxPalette.KUVA, 0.8F * a);
        rect(vc, m, x0, h - 1.0F, x1 - CHAMFER, h, -0.01F, FxPalette.KUVA_DEEP, 0.9F * a);
        line(vc, m, x1 - CHAMFER, h, x1, h - CHAMFER, 1.0F, -0.01F, FxPalette.KUVA_DEEP, 0.9F * a);
        // 图标方框
        float bx = x0 + PAD;
        float by = (h - ICON_BOX) * 0.5F;
        frame(vc, m, bx, by, bx + ICON_BOX, by + ICON_BOX, -0.01F, FxPalette.KUVA, 0.35F * a);
        // 倒计时条：剩下的亮，最后 10 秒呼吸
        float total = e.getMercyTotalSeconds() * 20.0F;
        float left = total > 0.0F ? Mth.clamp(remaining / total, 0.0F, 1.0F) : 1.0F;
        float barA = 1.0F;
        if (remaining <= 200.0F) {
            float phase = (now % 500L) / 500.0F;
            barA = 0.8F + 0.2F * Mth.cos(phase * Mth.TWO_PI);
        }
        float barY = h - 5.0F;
        rect(vc, m, textX, barY, textR, barY + BAR_H, -0.01F, FxPalette.KUVA_MIST, 0.8F * a);
        if (left > 0.0F) {
            rect(vc, m, textX, barY, textX + (textR - textX) * left, barY + BAR_H, -0.02F, FxPalette.KUVA, barA * a);
        }

        // 武器图标：直接贴物品的贴图，不打光，夜里也看得清
        TextureAtlasSprite sprite = mc.getItemRenderer().getModel(stack, e.level(), null, e.getId()).getParticleIcon();
        float ix = bx + (ICON_BOX - ICON) * 0.5F;
        float iy = by + (ICON_BOX - ICON) * 0.5F;
        VertexConsumer tex = buffer.getBuffer(RenderType.text(InventoryMenu.BLOCK_ATLAS));
        int ia = Mth.clamp(Math.round(a * 255.0F), 0, 255);
        sprite(tex, m, ix, iy, ix + ICON, iy + ICON, -0.03F, sprite, ia);

        // 字：小字说明 + 秒数一行，武器名一行
        ps.pushPose();
        ps.translate(textX, 5.0F, -0.04F);
        ps.scale(CAPTION_SCALE, CAPTION_SCALE, 1.0F);
        font.drawInBatch(cap, 0.0F, 0.0F, argb(FxPalette.ASH, a), false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        float sx = (textR - textX) / CAPTION_SCALE - font.width(secs);
        font.drawInBatch(secs, sx, 0.0F, argb(FxPalette.BONE, a), false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, -0.04F);
        font.drawInBatch(name, textX, 13.0F, argb(FxPalette.KUVA_PALE, a), false, ps.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
        ps.popPose();
    }

    // ==================== 图元 ====================

    /** 左上、右下两个角切掉的板子：左段、中段、右段三块 */
    private static void chamferFill(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float c,
                                    float z, int rgb, float a) {
        quad(vc, m, x0, y0 + c, x0, y1, x0 + c, y1, x0 + c, y0, z, rgb, a);
        quad(vc, m, x0 + c, y0, x0 + c, y1, x1 - c, y1, x1 - c, y0, z, rgb, a);
        quad(vc, m, x1 - c, y0, x1 - c, y1, x1, y1 - c, x1, y0, z, rgb, a);
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb,
                             float a) {
        quad(vc, m, x0, y0, x0, y1, x1, y1, x1, y0, z, rgb, a);
    }

    private static void frame(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb,
                              float a) {
        rect(vc, m, x0, y0, x1, y0 + 1.0F, z, rgb, a);
        rect(vc, m, x0, y1 - 1.0F, x1, y1, z, rgb, a);
        rect(vc, m, x0, y0 + 1.0F, x0 + 1.0F, y1 - 1.0F, z, rgb, a);
        rect(vc, m, x1 - 1.0F, y0 + 1.0F, x1, y1 - 1.0F, z, rgb, a);
    }

    /** 从 (ax, ay) 到 (bx, by) 一条 {@code lw} 粗的线（ax &lt; bx） */
    private static void line(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float lw, float z,
                             int rgb, float a) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4F) {
            return;
        }
        // 法向 = 方向转 90°，四个点的顺序和 rect 一样（左上、左下、右下、右上）
        float nx = -dy / len * lw * 0.5F;
        float ny = dx / len * lw * 0.5F;
        quad(vc, m, ax - nx, ay - ny, ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, z, rgb, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2,
                             float x3, float y3, float z, int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x2, y2, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x3, y3, z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static void sprite(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z,
                               TextureAtlasSprite s, int alpha) {
        vc.vertex(m, x0, y0, z).color(255, 255, 255, alpha).uv(s.getU0(), s.getV0()).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(255, 255, 255, alpha).uv(s.getU0(), s.getV1()).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(255, 255, 255, alpha).uv(s.getU1(), s.getV1()).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(255, 255, 255, alpha).uv(s.getU1(), s.getV0()).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    // ==================== 小工具 ====================

    private static float ease(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static int argb(int rgb, float a) {
        return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static String nameOf(int index, ItemStack stack, Minecraft mc) {
        String lang = mc.options.languageCode;
        int n = KuvaLichItems.KUVA_WEAPONS.size();
        if (names.length != n || !lang.equals(namesLang)) {
            names = new String[n];
            namesLang = lang;
            caption = "";
            secsShown = -1;
        }
        String s = names[index];
        if (s == null) {
            s = stack.getHoverName().getString();
            names[index] = s;
        }
        return s;
    }

    private static String caption() {
        if (caption.isEmpty()) {
            caption = I18n.get("kuvalich.larvling.overhead.caption");
        }
        return caption;
    }

    private static String secondsText(int secs) {
        if (secs != secsShown) {
            secsShown = secs;
            secsText = I18n.get("kuvalich.larvling.overhead.seconds", secs);
        }
        return secsText;
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

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SLOTS.clear();
    }
}
