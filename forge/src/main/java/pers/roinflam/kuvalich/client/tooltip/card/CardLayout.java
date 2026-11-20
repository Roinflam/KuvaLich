package pers.roinflam.kuvalich.client.tooltip.card;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 一张卡在某个界面缩放、某个卡大小下的版式。长度单位除注明外都是展开完时的物理像素。
 * 卡内单位：卡身宽 100 单位，ps 是每单位合多少物理像素。
 */
final class CardLayout {

    static final float W = 100;
    /** 浮起倍数：展开完比配置的大小再大 1.1 倍 */
    static final float FLOAT_S = 1.1F;
    static final int COMPACT_LIMIT = 154;

    /** 一行文字：位置、颜色，以及开头单独上色的数值 */
    static final class Line {
        final CardFonts.Run run;
        final CardFonts.Run value;
        final int x;
        final int boxTop;
        final int color;
        final int valueColor;
        final boolean name;
        /** 行顶，单位，用来算被底托盖住前的淡出 */
        final float yUnits;

        Line(CardFonts.Run run, CardFonts.Run value, int x, int boxTop, int color, int valueColor, boolean name, float yUnits) {
            this.run = run;
            this.value = value;
            this.x = x;
            this.boxTop = boxTop;
            this.color = color;
            this.valueColor = valueColor;
            this.name = name;
            this.yUnits = yUnits;
        }
    }

    final CardModel model;
    final int gs;
    final int base;
    final int sizePercent;
    final boolean fallbackFont;
    final int wp;
    final float ps;
    final boolean compact;
    final boolean thin;
    final int fA, fN, fP, fT;
    final int pillPx;
    final float pillH;
    final float pillUp;
    final int hp;
    final float hf;
    final float artEnd;
    final List<Line> lines = new ArrayList<>();
    final CardFonts.Run pillText;
    final CardFonts.Run cycleText;
    final CardFonts.Run tagText;
    /** 离屏画布大小和卡左上角在画布里的位置 */
    final int cw, ch, ox, oy;

    CardLayout(CardModel model, int gs, int base, int sizePercent, boolean fallbackFont) {
        this.model = model;
        this.gs = gs;
        this.base = base;
        this.sizePercent = sizePercent;
        this.fallbackFont = fallbackFont;
        CardTier t = model.tier;

        // 卡宽按窗口自动缩放时的倍数算，不跟玩家选的界面缩放走，缩放调小的人看到的卡也一样大；gs 只管坐标换算
        float raw = base * sizePercent / 100.0F * FLOAT_S;
        int w = 2 * Math.round(100 * raw / 2);
        boolean small = w < COMPACT_LIMIT;
        if (small) {
            // 小卡边框只按 40～152 每 4 像素烘了一档
            w = Math.max(40, Math.min(152, Math.round(w / 4.0F) * 4));
        }
        this.wp = w;
        this.compact = small;
        this.ps = w / 100.0F;
        this.thin = !small && ps < 3.2F;

        fA = Math.max(11, Math.round(0.07F * wp));
        fN = Math.max(Math.round(0.09F * wp), fA + 1);
        fP = Math.max(11, Math.round(0.065F * wp));
        fT = Math.max(9, Math.round(0.06F * wp));
        int lhA = Math.round(1.2F * fA);
        int lhN = Math.round(1.15F * fN);
        int pad = Math.max(3, Math.round(3.5F * ps));
        int maxP = wp - 2 * pad;
        boolean fb = fallbackFont;

        List<String> nameLines = CardFonts.wrapEven(model.name, maxP, fN, false, fb);
        int nameH = nameLines.size() * lhN;
        int gapN = Math.max(1, Math.round(0.15F * fA));

        // 正文：先排好行，算出总高，等卡高定了再定位
        List<String[]> body = new ArrayList<>();
        List<Integer> bodyOff = new ArrayList<>();
        int off = 0;
        if (model.veiled) {
            String h1 = Component.translatable("kuvalich.hover_card.veiled_hint.1").getString();
            String h2 = Component.translatable("kuvalich.hover_card.veiled_hint.2").getString();
            List<String> hint;
            if (CardFonts.width(h1, fA, false, fb) <= maxP && CardFonts.width(h2, fA, false, fb) <= maxP) {
                hint = List.of(h1, h2);
            } else {
                String joined = joinHint(h1, h2);
                hint = CardFonts.wrapEven(joined, maxP, fA, false, fb);
                if (hint.size() > 2) {
                    String s = Component.translatable("kuvalich.hover_card.veiled_hint.short").getString();
                    hint = CardFonts.wrapEven(s, maxP, fA, false, fb);
                }
            }
            for (String s : hint) {
                body.add(new String[]{s, null});
                bodyOff.add(off);
                off += lhA;
            }
        } else {
            List<List<String>> wrapped = new ArrayList<>();
            boolean anyWrapped = false;
            for (CardModel.Attr a : model.attrs) {
                List<String> ls = CardFonts.wrapEven(a.value() + " " + a.name(), maxP, fA, false, fb);
                anyWrapped |= ls.size() > 1;
                wrapped.add(ls);
            }
            int gap = anyWrapped && model.attrs.size() > 1 ? Math.max(2, Math.round(0.25F * fA)) : 0;
            for (int i = 0; i < wrapped.size(); i++) {
                if (i > 0) {
                    off += gap;
                }
                CardModel.Attr a = model.attrs.get(i);
                List<String> ls = wrapped.get(i);
                for (int j = 0; j < ls.size(); j++) {
                    String s = ls.get(j);
                    String val = j == 0 && s.startsWith(a.value()) ? a.value() : null;
                    body.add(new String[]{s, val, a.negative() ? "-" : "+"});
                    bodyOff.add(off);
                    off += lhA;
                }
            }
        }
        int bodyH = off;

        int ph = fP + 2 * Math.max(2, Math.round(0.22F * fP));
        this.pillPx = ph;
        this.pillH = Math.max(11, ph / ps);
        this.pillUp = pillH - 11;
        int restP = nameH + gapN + bodyH + Math.round((25 + pillUp) * ps);
        this.hp = Math.max(Math.round(144 * ps),
                Math.min((int) Math.ceil((restP - 3 * ps) / 0.48F), restP - Math.round(3 * ps) + Math.round(1.3F * wp)));
        int top = hp - restP;
        this.hf = hp / ps;
        this.artEnd = top / ps + 3;

        for (int i = 0; i < nameLines.size(); i++) {
            CardFonts.Run r = new CardFonts.Run(nameLines.get(i), fN, false, fb);
            int bt = top + i * lhN + (lhN - fN) / 2;
            lines.add(new Line(r, null, Math.round((wp - r.width) / 2.0F), bt, t.name, 0, true, bt / ps));
        }
        int bodyTop = top + nameH + gapN;
        int hintColor = mix(t.name, 0x8A93A3, 0.4F);
        for (int i = 0; i < body.size(); i++) {
            String[] b = body.get(i);
            CardFonts.Run r = new CardFonts.Run(b[0], fA, false, fb);
            int bt = bodyTop + bodyOff.get(i) + (lhA - fA) / 2;
            int x = Math.round((wp - r.width) / 2.0F);
            if (model.veiled) {
                lines.add(new Line(r, null, x, bt, hintColor, 0, false, bt / ps));
                continue;
            }
            CardFonts.Run v = null;
            if (b[1] != null) {
                v = new CardFonts.Run(b[1], fA, false, fb);
                // 数值之后的部分接着数值画，整行宽度按整串量，避免两段分开量出来的取整误差把行挤偏
                String rest = b[0].substring(b[1].length());
                r = new CardFonts.Run(rest, fA, false, fb);
            }
            int vc = "-".equals(b[2]) ? 0xFF7B7B : t.valueColor();
            lines.add(new Line(r, v, x, bt, t.textColor(), vc, false, bt / ps));
        }

        String pill = model.veiled ? "kuvalich.hover_card.type.veiled"
                : model.warframe ? "kuvalich.hover_card.type.warframe" : "kuvalich.hover_card.type.weapon";
        pillText = new CardFonts.Run(Component.translatable(pill).getString(), fP, true, fb);
        cycleText = model.riven && model.cycle > 0 ? new CardFonts.Run(String.valueOf(model.cycle), fP, true, fb) : null;
        tagText = model.veiled ? new CardFonts.Run("???", fT, true, fb) : null;

        cw = (int) Math.ceil((W + 2 * t.el + 16) * ps);
        ch = (int) Math.ceil((hf + t.et + t.eb + 16) * ps);
        ox = Math.round((t.el + 8) * ps);
        oy = Math.round((t.et + 8) * ps);
    }

    boolean matches(int gs, int base, int sizePercent, boolean fallbackFont) {
        return this.gs == gs && this.base == base && this.sizePercent == sizePercent && this.fallbackFont == fallbackFont;
    }

    private static String joinHint(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a + b;
        }
        char last = a.charAt(a.length() - 1);
        return last < 0x2E80 ? a + " " + b : a + b;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
