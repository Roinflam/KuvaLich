package pers.roinflam.kuvalich.client.tooltip.card;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡内文字：模组自带的 Noto Sans SC 子集，每个像素字号一份字体定义（{@code font/hover_card/r<f>.json}、{@code b<f>.json}），
 * 在物理像素坐标里一比一画。字体没加载上时整张卡改用原版字体按整数倍放大。
 */
final class CardFonts {

    static final int MIN_SIZE = 9;
    static final int MAX_SIZE = 40;

    /** 画布相对屏幕的放大倍数，CardPainter 每次画之前设 */
    static int ss = 1;

    private static final Style[] REGULAR = new Style[MAX_SIZE + 1];
    private static final Style[] BOLD = new Style[MAX_SIZE + 1];

    static {
        for (int f = MIN_SIZE; f <= MAX_SIZE; f++) {
            REGULAR[f] = Style.EMPTY.withFont(new ResourceLocation(Reference.MOD_ID, "hover_card/r" + f));
            BOLD[f] = Style.EMPTY.withFont(new ResourceLocation(Reference.MOD_ID, "hover_card/b" + f));
        }
    }

    private CardFonts() {
    }

    /**
     * 一行文字在某个字号下的画法。排版时建好，每帧直接画，不再分配。
     */
    static final class Run {
        final String text;
        final int f;
        final boolean bold;
        final boolean fallback;
        final FormattedCharSequence seq;
        /** 两倍字号的同一行字，画布放大两倍时用；字号太大或太小、没有对应字形时为 null */
        final FormattedCharSequence hiSeq;
        /** 实际用的字体字号与放大倍数 */
        final float scale;
        final int width;

        Run(String text, int f, boolean bold, boolean fallback) {
            this.text = text;
            this.f = f;
            this.bold = bold;
            this.fallback = fallback;
            Font font = Minecraft.getInstance().font;
            if (fallback) {
                this.scale = Math.max(1, Math.round(f / 9.0F));
                this.seq = Component.literal(text).getVisualOrderText();
                this.hiSeq = null;
                this.width = Math.round(font.getSplitter().stringWidth(text) * scale);
            } else {
                int af = Math.min(f, MAX_SIZE);
                this.scale = f / (float) af;
                Component c = Component.literal(text).withStyle(style(af, bold));
                this.seq = c.getVisualOrderText();
                // 宽度仍按一倍字号量，排版和不放大时一样
                this.hiSeq = f >= MIN_SIZE && f * 2 <= MAX_SIZE
                        ? Component.literal(text).withStyle(style(f * 2, bold)).getVisualOrderText() : null;
                this.width = (int) Math.ceil(font.getSplitter().stringWidth(c) * scale);
            }
        }

        /**
         * 画在字盒顶 {@code boxTop}（物理像素），字盒高 f，汉字墨迹在盒内上下居中。
         * MC 的 ttf 字形墨迹中线在绘制 y 下 0.73 × 字形字号 − 3，这 3 像素不随字号变，缩放着画时要跟着乘。
         */
        void draw(GuiGraphics g, int x, int boxTop, int rgb, float alpha) {
            if (alpha < 0.02F) {
                return;
            }
            int a = Math.max(4, Math.min(255, Math.round(alpha * 255)));
            int color = (a << 24) | (rgb & 0xFFFFFF);
            Font font = Minecraft.getInstance().font;
            boolean hi = ss == 2 && hiSeq != null;
            float y;
            if (fallback) {
                y = boxTop + Math.round((f - 7 * scale) / 2.0F);
            } else {
                // 差半像素时往上偏
                y = Math.round(boxTop + (f - 1) / 2.0F - 0.73F * f + 3 * (hi ? 0.5F : scale) - 0.01F);
            }
            if (hi) {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(0.5F, 0.5F, 1);
                g.drawString(font, hiSeq, 0, 0, color, false);
                g.pose().popPose();
            } else if (scale == 1.0F) {
                g.drawString(font, seq, x, (int) y, color, false);
            } else {
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, seq, 0, 0, color, false);
                g.pose().popPose();
            }
        }
    }

    static Style style(int f, boolean bold) {
        int af = Math.max(MIN_SIZE, Math.min(MAX_SIZE, f));
        return bold ? BOLD[af] : REGULAR[af];
    }

    /**
     * 卡字体有没有加载上：两套子集都带 ASCII，「W」在 40 号下约 35 像素宽，退回原版字体时只有 6 到 8 像素。
     * 别拿汉字量，子集只收语言文件里卡用到的字，换个字就可能不在里面。
     */
    static boolean available() {
        Font font = Minecraft.getInstance().font;
        float r = font.getSplitter().stringWidth(Component.literal("W").withStyle(REGULAR[MAX_SIZE]));
        float b = font.getSplitter().stringWidth(Component.literal("W").withStyle(BOLD[MAX_SIZE]));
        return r >= 28 && b >= 20;
    }

    static int width(String text, int f, boolean bold, boolean fallback) {
        Font font = Minecraft.getInstance().font;
        if (fallback) {
            return Math.round(font.getSplitter().stringWidth(text) * Math.max(1, Math.round(f / 9.0F)));
        }
        int af = Math.min(f, MAX_SIZE);
        float w = font.getSplitter().stringWidth(Component.literal(text).withStyle(style(af, bold)));
        return (int) Math.ceil(w * f / (float) af);
    }

    // ---------------------------------------------------------------- 均匀折行

    private static boolean isCjk(char c) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA;
    }

    /** 可断开的位置：空格后、连字符后、汉字前后。拉丁字母和数字连在一起不拆。 */
    private static List<Integer> breakPoints(String s) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < s.length(); i++) {
            char a = s.charAt(i - 1), b = s.charAt(i);
            if (a == ' ' || a == '-' || isCjk(a) || isCjk(b)) {
                if (b != ' ' && !isClosingPunct(b)) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private static boolean isClosingPunct(char c) {
        return "，。、；：！？）」』】,.;:!?)%".indexOf(c) >= 0;
    }

    /**
     * 先试一行；放不下就试两行、三行……在可断开处分配，使最长一行最短。
     * 单个词本身比可用宽度还宽时照样放（会超出），不拆词。
     */
    static List<String> wrapEven(String text, int maxW, int f, boolean bold, boolean fallback) {
        List<String> one = new ArrayList<>(1);
        String t = text.trim();
        if (t.isEmpty() || width(t, f, bold, fallback) <= maxW) {
            one.add(t);
            return one;
        }
        List<Integer> bp = breakPoints(t);
        int m = bp.size() + 2;
        int[] cut = new int[m];
        cut[0] = 0;
        for (int i = 0; i < bp.size(); i++) {
            cut[i + 1] = bp.get(i);
        }
        cut[m - 1] = t.length();
        // seg[i][j]：从第 i 个断点到第 j 个断点这一段的宽度
        int[][] seg = new int[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                seg[i][j] = width(t.substring(cut[i], cut[j]).trim(), f, bold, fallback);
            }
        }
        List<String> best = null;
        int maxLines = Math.min(m - 1, 12);
        for (int n = 2; n <= maxLines; n++) {
            // dp[k][j]：前 j 个断点分成 k 行时最长一行的最小值
            int[][] dp = new int[n + 1][m];
            int[][] from = new int[n + 1][m];
            for (int[] row : dp) {
                java.util.Arrays.fill(row, Integer.MAX_VALUE);
            }
            dp[0][0] = 0;
            for (int k = 1; k <= n; k++) {
                for (int j = 1; j < m; j++) {
                    for (int i = k - 1; i < j; i++) {
                        if (dp[k - 1][i] == Integer.MAX_VALUE) {
                            continue;
                        }
                        int v = Math.max(dp[k - 1][i], seg[i][j]);
                        if (v < dp[k][j]) {
                            dp[k][j] = v;
                            from[k][j] = i;
                        }
                    }
                }
            }
            List<String> lines = new ArrayList<>(n);
            int j = m - 1;
            for (int k = n; k >= 1; k--) {
                int i = from[k][j];
                lines.add(0, t.substring(cut[i], cut[j]).trim());
                j = i;
            }
            best = lines;
            if (dp[n][m - 1] <= maxW) {
                break;
            }
        }
        return best != null ? best : one;
    }
}
