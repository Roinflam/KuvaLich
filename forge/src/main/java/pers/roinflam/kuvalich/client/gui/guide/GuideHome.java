package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.CatView;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：分类大格子 + 「继续阅读」
 *
 * <h3>布局（照御炎指南的菜单）</h3>
 * <p>一行 3 格，窗口太窄降成 2 列、1 列，最后一行不满时整行居中。格子高度按屏高在
 * {@value #TILE_H_MIN}~{@value #TILE_H_MAX} 之间伸缩：够高时是「徽框在上、标题居中」的竖格子，
 * 矮了换成「徽框在左、标题副标题在右」的横格子 —— 1080p 配自动 GUI 缩放时界面只有 480×270，
 * 十个分类排四行，竖格子根本放不下，与其出滚动条不如换一种更紧凑的排法。最矮也放不下时才滚动。</p>
 *
 * <h3>动效：全息成形</h3>
 * <p>每个格子（和底下的「继续阅读」条）各自走一段 {@value #FORM_MS}ms 的成形，进度 f 从 0 到 1：</p>
 * <ul>
 *   <li>f 0 ~ {@value #TRACE_END}：描边先到 —— 外框从顶边中点往两侧描，沿侧边下行，在底边中点合拢，
 *       两个笔头各 3px 亮白；描完后外框在剩下的时间里由科技青冷却成常态的结构线色；</li>
 *   <li>f 0 ~ {@value #SCAN_END}：一道小扫描线自上而下扫过格子，越往下越淡；</li>
 *   <li>f {@value #CONTENT_FROM} ~ 1：内容后到 —— 底色、徽框、图标、文字带一点回弹地浮现
 *       （easeOutBack，从 0.85 倍放大到原大；外框不跟着缩放，内容在框里长出来）。</li>
 * </ul>
 * <p><b>什么时候开始</b>：开场时按开场扫描光束（{@link GuideIntro#BEAM_START} 起，
 * {@link GuideIntro#BEAM_MS}ms 匀速扫过面板）到达格子顶边的时刻，同一行里再从左到右各错开
 * {@value #INTRO_COL_MS}ms —— 光束扫到哪、哪里的格子就成形；回首页时不跟光束，
 * 按序号从 {@value #TILE_DELAY_MS}ms 起每格错开 {@value #TILE_STAGGER_MS}ms。</p>
 * <p>悬停时上浮 2px、描边转猩红并外溢一圈辉光、徽框描边一起转猩红。悬停过渡用指数趋近，与帧率无关。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideHome {

    private static final int GAP = 8;
    /** 矮格子的间距：480×270 时省下的这几像素正好让四行格子不用滚动 */
    private static final int GAP_TIGHT = 6;
    private static final int TILE_MIN_W = 118;
    private static final int TILE_MAX_W = 172;
    /**
     * 最矮的横格子（徽框 20、只有标题、没有副标题）。不能再高：720p 全屏或默认 854×480 窗口配自动缩放
     * 时界面只有 427×240，装了 TACZ 是十个分类排四行，格子最多 31px 高 ——
     * 下限卡在 36 时整块被撑高，「继续阅读 / 新手从这里开始」那一条掉到折线下面，得先滚一下才看得到
     */
    private static final int TILE_H_MIN = 30;
    private static final int TILE_H_MAX = 96;
    /** 竖格子需要的最低高度：徽框 30 + 标题 + 一行副标题 */
    private static final int VERTICAL_MIN = 80;
    private static final int CONT_H = 20;

    /** 一个格子从开始成形到完全成形 */
    private static final float FORM_MS = 200f;
    /** 成形进度到这里时外框描完（描边先到） */
    private static final float TRACE_END = 0.55f;
    /** 成形进度到这里时格子里的小扫描线走到底 */
    private static final float SCAN_END = 0.6f;
    /** 成形进度从这里开始内容浮现（内容后到） */
    private static final float CONTENT_FROM = 0.3f;
    /** 回首页（非开场）时第一格的延迟与逐格错开 */
    private static final float TILE_DELAY_MS = 20f;
    private static final float TILE_STAGGER_MS = 24f;
    private static final int TILE_STAGGER_CAP = 12;
    /** 开场时同一行相邻两格的错开：光束同时扫到一整行，行内再从左到右依次点亮 */
    private static final float INTRO_COL_MS = 22f;
    /** 描线笔头：带一点青的白 */
    private static final int HEAD = GuideIntro.HOT;

    private static final int TILE_BG = 0xF0090D10;
    private static final int TILE_BG_HOVER = 0xF4141C24;

    int x;
    int y;
    int w;
    int h;

    private List<CatView> cats = List.of();
    private int cols = 3;
    private int rows = 1;
    private int gap = GAP;
    private int tileW = TILE_MAX_W;
    private int tileH = TILE_H_MAX;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private boolean vertical;
    private int emblem;

    private String[] titles = new String[0];
    private String[] counts = new String[0];
    private String[] indices = new String[0];
    private List<List<String>> subtitles = new ArrayList<>();
    // 字宽在排版时量好：每帧十几个格子 × 三四段文字逐个 font.width 是纯浪费
    private int[] titleW = new int[0];
    private int[] countW = new int[0];
    private int[][] subtitleW = new int[0][];
    private int contLabelW;
    private int contCatW;
    private String emptyText = "";
    private int emptyW;

    @Nullable
    private EntryView cont;
    /** 还没读过任何一篇：继续阅读条改成「新手从这里开始」，指向全书第一篇 */
    private boolean contFresh;
    private int contY;
    private String contLabel = "";
    private String contTitle = "";
    private String contCat = "";

    private float scroll;
    private float target;
    private final GuideScrollDrag bar = new GuideScrollDrag();
    private float[] hover = new float[0];
    private float contHover;
    private long shownAt;
    /** 这一轮成形是不是开场：是就跟着开场光束走 */
    private boolean intro;
    /** 开场光束扫过的范围（就是整块面板） */
    private int beamTop;
    private int beamH = 1;

    // ==================== 排版 ====================

    /**
     * @param view 视图
     * @param cont 「继续阅读」的目标，没有为 null
     * @param text 排版器（副标题折行）
     */
    void layout(GuideView view, @Nullable EntryView cont, GuideTextLayout text, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = text.font();
        boolean changed = cats != view.categories;
        this.cats = view.categories;
        int n = cats.size();
        if (changed || hover.length != n) {
            hover = new float[n];
        }

        cols = 3;
        tileW = Math.min(TILE_MAX_W, (w - GAP * 2) / 3);
        if (tileW < TILE_MIN_W) {
            cols = 2;
            tileW = Math.min(TILE_MAX_W, (w - GAP) / 2);
            if (tileW < TILE_MIN_W) {
                cols = 1;
                tileW = Math.min(w, 260);
            }
        }
        rows = Math.max(1, (n + cols - 1) / cols);
        contFresh = cont == null && !view.entryList.isEmpty();
        if (contFresh) {
            cont = view.entryList.get(0);
        }
        this.cont = cont;
        gap = GAP;
        int contBlock = cont != null ? CONT_H + gap + 2 : 0;
        int avail = h - contBlock;
        tileH = (avail - (rows - 1) * gap) / rows;
        if (tileH < 56) {
            gap = GAP_TIGHT;
            contBlock = cont != null ? CONT_H + gap + 2 : 0;
            avail = h - contBlock;
            tileH = (avail - (rows - 1) * gap) / rows;
        }
        tileH = Mth.clamp(tileH, TILE_H_MIN, TILE_H_MAX);
        vertical = tileH >= VERTICAL_MIN;
        gridW = cols * tileW + (cols - 1) * gap;
        gridX = x + (w - gridW) / 2;
        gridH = rows * tileH + (rows - 1) * gap;
        int total = gridH + contBlock;
        // 放得下时整块垂直居中（略偏上：视觉中心比几何中心高一点）；放不下时顶格、可滚动
        gridY = total <= h ? y + (h - total) * 2 / 5 : y;
        contY = gridY + gridH + gap + 2;

        emblem = vertical ? 30 : Mth.clamp(tileH - 14, 20, 30);
        titles = new String[n];
        counts = new String[n];
        indices = new String[n];
        subtitles = new ArrayList<>(n);
        titleW = new int[n];
        countW = new int[n];
        subtitleW = new int[n][];
        for (int i = 0; i < n; i++) {
            CatView cat = cats.get(i);
            counts[i] = I18n.get("kuvalich.guide.entry_count", cat.entries.size());
            countW[i] = font.width(counts[i]);
            indices[i] = String.format("%02d", i + 1);
            List<String> sub;
            if (vertical) {
                titles[i] = GuideTheme.ellipsize(font, cat.title(), tileW - 14);
                int lines = tileH >= 92 ? 2 : 1;
                sub = text.wrapPlain(cat.subtitle(), tileW - 16, lines);
            } else {
                int tw = tileW - (8 + emblem + 8) - 6;
                titles[i] = GuideTheme.ellipsize(font, cat.title(), tw - countW[i] - 6);
                int lines = tileH >= 56 ? 2 : tileH >= 34 ? 1 : 0;
                sub = text.wrapPlain(cat.subtitle(), tw, lines);
            }
            subtitles.add(sub);
            titleW[i] = font.width(titles[i]);
            int[] sw = new int[sub.size()];
            for (int k = 0; k < sw.length; k++) {
                sw[k] = font.width(sub.get(k));
            }
            subtitleW[i] = sw;
        }

        if (cont != null) {
            contLabel = I18n.get(contFresh ? "kuvalich.guide.start_here" : "kuvalich.guide.continue");
            contCat = cont.cat.title();
            int room = gridW - 20 - font.width(contLabel) - 8 - font.width(contCat) - 14;
            contTitle = GuideTheme.ellipsize(font, cont.title(), Math.max(20, room));
            if (room < 20) {
                contCat = "";
            }
            contLabelW = font.width(contLabel);
            contCatW = font.width(contCat);
        }
        emptyText = I18n.get("kuvalich.guide.empty");
        emptyW = font.width(emptyText);
        target = Mth.clamp(target, 0, maxScroll());
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    /** 重新播放格子成形（回到首页时）：按序号错开 */
    void restart(long now) {
        shownAt = now;
        intro = false;
        scroll = target = 0;
    }

    /**
     * 开场：格子跟着开场扫描光束成形
     *
     * @param openedAt 界面打开的时刻（开场时间轴的零点）
     */
    void restartIntro(long openedAt) {
        shownAt = openedAt;
        intro = true;
        scroll = target = 0;
    }

    /** 开场光束扫过的范围：面板的上沿与高度（尺寸变了要重设） */
    void setIntroBeam(int top, int height) {
        beamTop = top;
        beamH = Math.max(1, height);
    }

    /** 跳过开场：所有格子直接成形完毕 */
    void finishAppear() {
        shownAt = Long.MIN_VALUE / 4;
    }

    private float maxScroll() {
        int bottom = cont != null ? contY + CONT_H : gridY + gridH;
        return Math.max(0, bottom - (y + h));
    }

    private int tileX(int i) {
        int row = i / cols;
        int inRow = Math.min(cols, cats.size() - row * cols);
        int rowLeft = gridX + (cols - inRow) * (tileW + gap) / 2;
        return rowLeft + (i % cols) * (tileW + gap);
    }

    private int tileY(int i) {
        return gridY + (i / cols) * (tileH + gap) - Math.round(scroll);
    }

    /**
     * 第 i 格（i == 分类数时是「继续阅读」条）从 shownAt 起多久开始成形
     */
    private float formDelay(int i) {
        boolean isCont = i >= cats.size();
        if (intro) {
            // 光束匀速扫过面板：到达这一格顶边的时刻 = 起点 + 相对高度 × 扫完用时
            int top = isCont ? contY : gridY + (i / cols) * (tileH + gap);
            float rel = Mth.clamp((top - beamTop) / (float) beamH, 0f, 1f);
            return GuideIntro.BEAM_START + rel * GuideIntro.BEAM_MS + (isCont ? 0 : i % cols) * INTRO_COL_MS;
        }
        return TILE_DELAY_MS + Math.min(i, TILE_STAGGER_CAP) * TILE_STAGGER_MS;
    }

    /** 成形进度 0~1（线性） */
    private float form(int i, long now) {
        float elapsed = (float) (now - shownAt) - formDelay(i);
        return elapsed <= 0f ? 0f : Math.min(1f, elapsed / FORM_MS);
    }

    /** 内容浮现量（easeOutBack，中途会略超过 1） */
    private static float content(float form) {
        return form <= CONTENT_FROM ? 0f : GuideTheme.easeOutBack((form - CONTENT_FROM) / (1f - CONTENT_FROM));
    }

    private float appear(int i, long now) {
        return content(form(i, now));
    }

    // ==================== 命中 ====================

    /**
     * @return 鼠标下的格子序号；没浮现完的格子不响应
     */
    int tileAt(double mx, double my, long now) {
        if (my < y || my >= y + h) {
            return -1;
        }
        for (int i = 0; i < cats.size(); i++) {
            int tx = tileX(i);
            int ty = tileY(i);
            if (mx >= tx && mx < tx + tileW && my >= ty && my < ty + tileH) {
                return appear(i, now) >= 0.5f ? i : -1;
            }
        }
        return -1;
    }

    @Nullable
    CatView cat(int i) {
        return i >= 0 && i < cats.size() ? cats.get(i) : null;
    }

    boolean overContinue(double mx, double my, long now) {
        if (cont == null || my < y || my >= y + h) {
            return false;
        }
        int cy = contY - Math.round(scroll);
        return mx >= gridX && mx < gridX + gridW && my >= cy && my < cy + CONT_H && appear(cats.size(), now) >= 0.5f;
    }

    @Nullable
    EntryView continueTarget() {
        return cont;
    }

    boolean mouseScrolled(double delta) {
        if (maxScroll() <= 0) {
            return false;
        }
        target = Mth.clamp(target - (float) delta * (tileH + gap) / 2f, 0, maxScroll());
        return true;
    }

    /**
     * 滚动条画在格子区右侧的留白里（{@code x + w + 1}），命中区就是那一条留白，和格子不重叠
     */
    private boolean overScrollbar(double mx, double my) {
        return maxScroll() > 0 && mx >= x + w && mx < x + w + GuideScrollDrag.HIT_W && my >= y && my < y + h;
    }

    /** 按在滚动条上：点轨道跳过去 / 抓住滑块（与正文、左栏的滚动条同一手感） */
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overScrollbar(mx, my)) {
            return false;
        }
        scroll = target = bar.press(my, y, h, scroll, maxScroll());
        return true;
    }

    boolean mouseDragged(double my, int button) {
        if (!bar.dragging()) {
            return false;
        }
        if (button != 0) {
            bar.release();
            return false;
        }
        scroll = target = bar.drag(my, h, maxScroll());
        return true;
    }

    void release() {
        bar.release();
    }

    // ==================== 绘制 ====================

    void render(GuideRender r, float delta, int offsetY, boolean settled) {
        GuiGraphics g = r.g;
        long now = r.now;
        scroll = GuideTheme.approach(scroll, target, delta, 18f);

        if (cats.isEmpty()) {
            g.drawString(r.font, emptyText, x + (w - emptyW) / 2, y + h / 2 - 4, GuideTheme.FAINT, false);
            return;
        }

        int hovered = settled && r.mouseActive ? tileAt(r.mouseX, r.mouseY, now) : -1;
        for (int i = 0; i < cats.size(); i++) {
            hover[i] = GuideTheme.approach(hover[i], i == hovered ? 1f : 0f, delta, 16f);
        }
        boolean contOver = settled && r.mouseActive && overContinue(r.mouseX, r.mouseY, now);
        contHover = GuideTheme.approach(contHover, contOver ? 1f : 0f, delta, 16f);

        // 裁剪框用屏幕坐标（offsetY 现在恒为 0：面板开合只裁剪不平移）；上下各多留几像素给悬停上浮和辉光
        g.enableScissor(x - 4, y - 3 + offsetY, x + w + 4, y + h + offsetY);
        for (int i = 0; i < cats.size(); i++) {
            int tx = tileX(i);
            int ty = tileY(i);
            if (ty + tileH < y - 4 || ty > y + h + 4) {
                continue;
            }
            float f = form(i, now);
            if (f <= 0f) {
                continue;
            }
            drawTile(r, i, tx, ty, f);
        }
        if (cont != null) {
            float f = form(cats.size(), now);
            if (f > 0f) {
                drawContinue(r, contY - Math.round(scroll), f);
            }
        }
        g.disableScissor();

        float max = maxScroll();
        if (max > 0) {
            boolean active = bar.dragging() || (settled && r.mouseActive && overScrollbar(r.mouseX, r.mouseY));
            CodexTheme.scrollbar(g, x + w + 1, y, h, scroll, max, active);
        }
    }

    private void drawTile(GuideRender r, int i, int tx, int ty, float form) {
        float appear = content(form);
        if (appear > 0.01f) {
            drawTileContent(r, i, tx, ty, appear, form >= 1f);
        }
        if (form < 1f) {
            // 成形中的外框画在内容之上：底色和外框是同一圈像素，后画的底色会把描线盖掉
            formFrame(r.g, tx, ty, tileW, tileH, 6, form, hover[i]);
        }
    }

    /**
     * @param withOutline 画不画常态外框：成形中外框由 {@link #formFrame} 画（不跟内容缩放）
     */
    private void drawTileContent(GuideRender r, int i, int tx, int ty, float appear, boolean withOutline) {
        GuiGraphics g = r.g;
        CatView cat = cats.get(i);
        float a = Mth.clamp(appear, 0f, 1f);
        float hv = hover[i];

        // 浮现时从 0.85 倍回弹到原大，悬停时上浮 2px；用 pose 做，格子里的所有东西一起动
        float cx = tx + tileW / 2f;
        float cy = ty + tileH / 2f;
        float scale = 0.85f + 0.15f * appear;
        g.pose().pushPose();
        g.pose().translate(cx, cy - 2f * hv, 0f);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0f);

        int cut = 6;
        CodexTheme.chamferFill(g, tx, ty, tileW, tileH, cut, GuideTheme.fade(GuideTheme.lerpColor(TILE_BG, TILE_BG_HOVER, hv), a));
        if (hv > 0.01f) {
            // 自上而下一道极淡的猩红，像被点亮
            for (int k = 0; k < 5; k++) {
                g.fill(tx + cut, ty + 1 + k * 3, tx + tileW - cut, ty + 4 + k * 3,
                        GuideTheme.withAlpha(GuideTheme.KUVA, (int) (18 * hv * a * (1f - k / 5f))));
            }
            for (int layer = 2; layer >= 1; layer--) {
                CodexTheme.chamferOutline(g, tx - layer, ty - layer, tileW + layer * 2, tileH + layer * 2, cut + layer,
                        GuideTheme.withAlpha(GuideTheme.KUVA, (int) (46 * hv * a / layer)));
            }
        }
        if (withOutline) {
            int edge = GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF), GuideTheme.KUVA, hv);
            CodexTheme.chamferOutline(g, tx, ty, tileW, tileH, cut, GuideTheme.fade(edge, a));
        }

        int titleColor = GuideTheme.textAlpha(GuideTheme.lerpColor(GuideTheme.BONE, 0xFFFFFFFF, hv), a);
        int subColor = GuideTheme.textAlpha(GuideTheme.ASH, a);
        int countColor = GuideTheme.textAlpha(GuideTheme.lerpColor(GuideTheme.FAINT, GuideTheme.TECH, hv), a);
        int emblemEdge = GuideTheme.fade(GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.TECH, 0xA0), GuideTheme.KUVA, hv), a);
        List<String> sub = subtitles.get(i);
        if (vertical) {
            int ex = (int) cx - emblem / 2;
            int ey = ty + 9;
            GuideTheme.emblem(g, ex, ey, emblem, GuideTheme.fade(GuideTheme.PLATE_DEEP, a), emblemEdge);
            icon(g, cat.icon(), ex + emblem / 2f, ey + emblem / 2f, a);
            g.drawString(r.font, indices[i], tx + 6, ty + 5, GuideTheme.textAlpha(GuideTheme.FAINT, a), false);
            g.drawString(r.font, counts[i], tx + tileW - 6 - countW[i], ty + 5, countColor, false);
            g.drawString(r.font, titles[i], (int) (cx - titleW[i] / 2f), ey + emblem + 7, titleColor, true);
            int ly = ey + emblem + 20;
            int[] sw = subtitleW[i];
            for (int k = 0; k < sub.size(); k++) {
                g.drawString(r.font, sub.get(k), (int) (cx - sw[k] / 2f), ly, subColor, false);
                ly += 10;
            }
        } else {
            int ex = tx + 8;
            int ey = ty + (tileH - emblem) / 2;
            GuideTheme.emblem(g, ex, ey, emblem, GuideTheme.fade(GuideTheme.PLATE_DEEP, a), emblemEdge);
            icon(g, cat.icon(), ex + emblem / 2f, ey + emblem / 2f, a);
            int textX = ex + emblem + 8;
            int block = 9 + sub.size() * 10 + (sub.isEmpty() ? 0 : 3);
            int titleY = ty + (tileH - block) / 2 + 1;
            g.drawString(r.font, titles[i], textX, titleY, titleColor, true);
            g.drawString(r.font, counts[i], tx + tileW - 7 - countW[i], titleY, countColor, false);
            int ly = titleY + 12;
            for (String s : sub) {
                g.drawString(r.font, s, textX, ly, subColor, false);
                ly += 10;
            }
        }
        g.pose().popPose();
    }

    private void icon(GuiGraphics g, ItemStack stack, float cx, float cy, float alpha) {
        // 物品图标没有透明度可调：浮现到一半再出现，免得一个实心图标先于半透明的格子蹦出来
        if (alpha < 0.35f) {
            return;
        }
        float s = Mth.clamp((emblem - 8) / 16f, 0.75f, 1.375f);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0f);
        g.pose().scale(s, s, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    private void drawContinue(GuideRender r, int cy, float form) {
        float appear = content(form);
        if (appear > 0.01f) {
            drawContinueContent(r, cy, appear, form >= 1f);
        }
        if (form < 1f) {
            formFrame(r.g, gridX, cy, gridW, CONT_H, 4, form, contHover);
        }
    }

    private void drawContinueContent(GuideRender r, int cy, float appear, boolean withOutline) {
        GuiGraphics g = r.g;
        float a = Mth.clamp(appear, 0f, 1f);
        float hv = contHover;
        int bx = gridX;
        // 不再上浮滑入：外框先在最终位置描出来，内容在框里淡入，滑动会让底色探出框外
        int by = cy;
        CodexTheme.chamferFill(g, bx, by, gridW, CONT_H, 4,
                GuideTheme.fade(GuideTheme.lerpColor(TILE_BG, TILE_BG_HOVER, hv), a));
        if (withOutline) {
            CodexTheme.chamferOutline(g, bx, by, gridW, CONT_H, 4,
                    GuideTheme.fade(GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF), GuideTheme.KUVA, hv), a));
        }
        int ty = by + (CONT_H - 8) / 2;
        GuideTheme.chevron(g, bx + 10 + Math.round(hv * 2f), by + CONT_H / 2, false,
                GuideTheme.fade(GuideTheme.lerpColor(GuideTheme.TECH, GuideTheme.KUVA, hv), a));
        int tx = bx + 20;
        g.drawString(r.font, contLabel, tx, ty, GuideTheme.textAlpha(GuideTheme.ASH, a), false);
        tx += contLabelW + 8;
        g.drawString(r.font, contTitle, tx, ty, GuideTheme.textAlpha(GuideTheme.lerpColor(GuideTheme.BONE, 0xFFFFFFFF, hv), a), true);
        if (!contCat.isEmpty()) {
            g.drawString(r.font, contCat, bx + gridW - 8 - contCatW, ty, GuideTheme.textAlpha(GuideTheme.FAINT, a), false);
        }
    }

    /**
     * 成形中的外框与小扫描线（成形完就不再调用，外框回到内容里按常态画）
     *
     * <ul>
     *   <li>f 0 ~ {@value #TRACE_END}：从顶边中点往两侧描线（缓出），两个笔头各 3px 亮白；</li>
     *   <li>f {@value #TRACE_END} ~ 1：外框完整，颜色由科技青冷却成常态的结构线色 —— 到 f = 1 时
     *       正好等于常态外框，交接那一帧看不出跳变；</li>
     *   <li>f 0 ~ {@value #SCAN_END}：一道小扫描线自上而下扫过，越往下越淡。</li>
     * </ul>
     *
     * @param form 成形进度 0~1
     * @param hv   悬停量：回首页时格子可能边成形边被悬停，外框照常转猩红
     */
    private static void formFrame(GuiGraphics g, int x, int y, int w, int h, int cut, float form, float hv) {
        int cool = GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.TECH, 0xE0), GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF),
                (form - TRACE_END) / (1f - TRACE_END));
        int edge = GuideTheme.lerpColor(cool, GuideTheme.KUVA, hv);
        float tp = GuideTheme.easeOutCubic(form / TRACE_END);
        if (tp >= 1f) {
            CodexTheme.chamferOutline(g, x, y, w, h, cut, edge);
        } else {
            float d = tp * CodexTheme.chamferPerimeter(w, h, cut) / 2f;
            CodexTheme.chamferArc(g, x, y, w, h, cut, -d, d, edge);
            if (d > 3f) {
                CodexTheme.chamferArc(g, x, y, w, h, cut, d - 3f, d, HEAD);
                CodexTheme.chamferArc(g, x, y, w, h, cut, -d, -d + 3f, HEAD);
            }
        }
        if (form < SCAN_END) {
            float s = form / SCAN_END;
            int sy = y + 1 + Math.min(h - 3, Math.round((h - 3) * s));
            // 斜切角那几行要往里收，扫描线不能画到切掉的角上
            int top = sy - y;
            int bottom = y + h - 1 - sy;
            int in = Math.max(1, Math.max(cut - top, cut - bottom));
            GuideTheme.hairline(g, x + in, sy, w - in * 2, GuideTheme.withAlpha(GuideTheme.TECH, (int) (0xC0 * (1f - s))));
        }
    }
}
