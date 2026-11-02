package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 逐字画、带额外字距的一行字：名牌、Boss 条、档案里的全大写名字和标签都用
 *
 * <p>原版字体没有字距参数，只能一个字一个字地画。每个字串拆好的字形和字宽按字串缓存（最近用过的 96 条），
 * 同一个名字每帧画不再拆、不再量。坐标都是字体像素，可以是小数。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpacedText {

    private static final int CACHE_SIZE = 96;

    /** 拆好的一行：每个码点一个字串，外加各自的宽 */
    static final class Glyphs {
        final String[] chars;
        final float[] widths;
        final float plainWidth;

        Glyphs(String[] chars, float[] widths, float plainWidth) {
            this.chars = chars;
            this.widths = widths;
            this.plainWidth = plainWidth;
        }

        /** 加上字距之后的总宽 */
        float width(float spacing) {
            return chars.length == 0 ? 0.0F : plainWidth + spacing * (chars.length - 1);
        }
    }

    private static final Map<String, Glyphs> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Glyphs> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private static Font cachedFont;

    private SpacedText() {
    }

    static Glyphs of(Font font, String text) {
        if (font != cachedFont) {
            CACHE.clear();
            cachedFont = font;
        }
        Glyphs g = CACHE.get(text);
        if (g == null) {
            int n = text.codePointCount(0, text.length());
            String[] chars = new String[n];
            float[] widths = new float[n];
            float total = 0.0F;
            int i = 0;
            for (int off = 0; off < text.length(); ) {
                int cp = text.codePointAt(off);
                int len = Character.charCount(cp);
                chars[i] = text.substring(off, off + len);
                widths[i] = font.width(chars[i]);
                total += widths[i];
                off += len;
                i++;
            }
            g = new Glyphs(chars, widths, total);
            CACHE.put(text, g);
        }
        return g;
    }

    /** 这行字加上字距之后有多宽（字体像素） */
    public static float width(Font font, String text, float spacing) {
        return of(font, text).width(spacing);
    }

    /**
     * 画进界面：左上角 (x, y)，用当前 {@code g.pose()}。字形攒在 {@code g.bufferSource()} 里，
     * 调用方在要和别的画法分先后的地方自己 {@code g.flush()}
     */
    public static float draw(GuiGraphics g, Font font, String text, float x, float y, float spacing, int argb,
                      boolean shadow) {
        if ((argb >>> 24) < 4 || text.isEmpty()) {
            return 0.0F;
        }
        Glyphs gl = of(font, text);
        Matrix4f m = g.pose().last().pose();
        MultiBufferSource.BufferSource buf = g.bufferSource();
        float cx = x;
        for (int i = 0; i < gl.chars.length; i++) {
            font.drawInBatch(gl.chars[i], cx, y, argb, shadow, m, buf, Font.DisplayMode.NORMAL, 0,
                    LightTexture.FULL_BRIGHT);
            cx += gl.widths[i] + spacing;
        }
        return gl.width(spacing);
    }

    /** 同上，以 {@code cx} 为中心 */
    public static float drawCentered(GuiGraphics g, Font font, String text, float cx, float y, float spacing, int argb,
                              boolean shadow) {
        float w = width(font, text, spacing);
        draw(g, font, text, cx - w * 0.5F, y, spacing, argb, shadow);
        return w;
    }

    /**
     * 画进世界：{@code poseStack} 已经转向相机、缩到字体像素，(x, y) 是这一行的左上角
     */
    static void drawInWorld(PoseStack poseStack, MultiBufferSource buffer, Font font, String text, float x, float y,
                            float spacing, int argb, Font.DisplayMode mode, int light) {
        if ((argb >>> 24) < 4 || text.isEmpty()) {
            return;
        }
        Glyphs gl = of(font, text);
        Matrix4f m = poseStack.last().pose();
        float cx = x;
        for (int i = 0; i < gl.chars.length; i++) {
            font.drawInBatch(gl.chars[i], cx, y, argb, false, m, buffer, mode, 0, light);
            cx += gl.widths[i] + spacing;
        }
    }

    /** 把 0~1 的大小折成 1/8 的整数倍，下限 {@code min}（字号对齐到 1/8，小字不发虚） */
    static float round8(float v, float min) {
        return Math.max(min, Math.round(v * 8.0F) / 8.0F);
    }
}
