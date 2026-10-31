package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideBlocks.Laid;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.BlockType;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读页右栏：一篇条目的排版缓存、滚动与绘制
 *
 * <h3>缓存键</h3>
 * <p>（条目、排版宽度）。配置快照 / 内容 / 语言变了由界面整体 {@link #invalidate()}，
 * 所以键里不必带版本号。每帧只按缓存好的块坐标画可见的那几块，块内部再只画可见的行。</p>
 *
 * <h3>阅读栏限宽</h3>
 * <p>正文宽度最多 {@link GuideTheme#READ_MAX_W}，右栏更宽时居中、两侧留白。
 * 一行太长时眼睛换行容易找错下一行 —— 这和网页正文限宽是同一个道理。</p>
 *
 * <h3>查阅定位</h3>
 * <p>从物品提示上按住 Ctrl 查阅时，界面带着「条目 + 块序号」打开：{@link #scrollToBlock} 记下目标，
 * 排版好之后把那一块滚到阅读栏上方，并让它亮一下（约 600ms 的猩红描边淡出）。
 * 块序号是 {@link EntryView#blocks()} 里的下标，和查阅索引用的是同一份。</p>
 *
 * <h3>阅读位置锚定</h3>
 * <p>换界面大小、改窗口大小后阅读栏变宽变窄、整篇重新排版，像素滚动量对不上原来的位置：
 * 重排前 {@link #anchor()} 记下视口顶落在第几块、块内几成，排完 {@link #restoreAnchor} 换算回去。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideReader {

    private static final int PAD_TOP = 10;
    private static final int PAD_BOTTOM = 18;
    private static final int SCROLLBAR_ZONE = 8;
    /** 换篇时内容上浮淡入的时长 */
    private static final float SWAP_MS = 180f;
    /** 查阅定位时的「亮一下」 */
    private static final float FLASH_MS = 600f;

    /**
     * 一篇排好的版
     */
    static final class Page {
        final Laid[] blocks;
        final int[] ys;
        final int height;
        /** 可见块（{@link EntryView#blocks()}）的序号 → 它在页里的 y 与高度；整块隐藏的块（查不到配方）落在下一块的位置、高度 0 */
        final int[] blockY;
        final int[] blockH;
        /** 查阅滚到这一块时让它顶到的 y：紧挨在前面的是小标题就连小标题一起露出来 */
        final int[] anchorY;

        Page(Laid[] blocks, int[] ys, int height, int[] blockY, int[] blockH, int[] anchorY) {
            this.blocks = blocks;
            this.ys = ys;
            this.height = height;
            this.blockY = blockY;
            this.blockH = blockH;
            this.anchorY = anchorY;
        }
    }

    /**
     * 阅读位置的锚：视口顶落在第几块（排好的块，含篇首标题和篇末翻页卡）、块内几成
     *
     * @param entry 记锚时的那一篇（换篇了就不还原）
     * @param index 块序号（{@link Page#blocks}）
     * @param frac  视口顶在这一块里的比例（落在块前的空隙里时略小于 0）
     */
    record Anchor(EntryView entry, int index, float frac) {}

    private final GuideTextLayout text;
    private final GuideMedia media;
    private final Map<String, Page> cache = new HashMap<>();

    int x;
    int y;
    int w;
    int h;
    private int contentX;
    private int contentW;

    @Nullable
    EntryView entry;
    float scroll;
    float target;
    private long swappedAt;
    private boolean dragging;
    private double dragStartY;
    private float dragStartScroll;
    /** 上一帧算出的页高，给滚动条与点击判定用 */
    private int pageHeight;
    @Nullable
    private EntryView lastEntry;
    private int lastWidth;
    @Nullable
    private Page lastPage;
    @Nullable
    private String pickHint;
    private int pickHintW;
    /** 等排版好之后要滚到的块（-1 = 没有；-2 = 篇首也要亮一下） */
    private int pendingBlock = -1;
    /** 正在亮的块在页里的位置；flashAt = 0 表示没在亮 */
    private int flashY;
    private int flashH;
    private long flashAt;

    GuideReader(GuideTextLayout text, GuideMedia media) {
        this.text = text;
        this.media = media;
    }

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int avail = w - SCROLLBAR_ZONE - 12;
        this.contentW = Math.max(80, Math.min(GuideTheme.READ_MAX_W, avail));
        this.contentX = x + 6 + (avail - contentW) / 2;
    }

    /** 配置 / 内容 / 语言变了：所有排版作废 */
    void invalidate() {
        cache.clear();
        lastPage = null;
        lastEntry = null;
        pickHint = null;
    }

    /**
     * 换一篇
     *
     * @param ev      条目
     * @param scrollY 初始滚动位置
     * @param animate 是否播放换篇动画
     */
    void show(@Nullable EntryView ev, float scrollY, boolean animate) {
        this.entry = ev;
        this.scroll = scrollY;
        this.target = scrollY;
        this.dragging = false;
        this.pendingBlock = -1;
        if (animate) {
            swappedAt = System.currentTimeMillis();
        }
    }

    /**
     * 查阅定位：排版好之后把第 block 块（可见块序号，-1 = 篇首）滚到阅读栏上方并让它亮一下
     */
    void scrollToBlock(int block) {
        pendingBlock = block < 0 ? -2 : block;
    }

    /**
     * 记下当前阅读位置（重新排版之前调）；在篇首、还没排过版时返回 null（不用还原，滚动量 0 照旧是篇首）
     */
    @Nullable
    Anchor anchor() {
        Page p = lastPage;
        if (entry == null || p == null || lastEntry != entry || p.blocks.length == 0 || target <= 0.5f) {
            return null;
        }
        float vy = target - PAD_TOP;
        int idx = p.blocks.length - 1;
        for (int i = 0; i < p.blocks.length; i++) {
            if (p.ys[i] + p.blocks[i].height > vy) {
                idx = i;
                break;
            }
        }
        float bh = Math.max(1, p.blocks[idx].height);
        return new Anchor(entry, idx, (vy - p.ys[idx]) / bh);
    }

    /**
     * 按新的宽度排好版后，把滚动量换算回锚住的那一块（直接跳过去，不做平滑滚动）
     */
    void restoreAnchor(GuideView view, @Nullable Anchor a) {
        if (a == null || a.entry() != entry) {
            return;
        }
        Page p = page(view);
        if (p == null || p.blocks.length == 0) {
            return;
        }
        int idx = Math.min(a.index(), p.blocks.length - 1);
        float vy = p.ys[idx] + a.frac() * p.blocks[idx].height;
        float max = Math.max(0, p.height + PAD_TOP + PAD_BOTTOM - h);
        pageHeight = p.height;
        scroll = target = Math.max(0, Math.min(max, vy + PAD_TOP));
    }

    /**
     * 取（必要时排）当前条目的版
     */
    @Nullable
    Page page(GuideView view) {
        if (entry == null) {
            return null;
        }
        // 每帧都会来取：同一篇同一宽度直接返回上次那份，连缓存键的字符串都不拼
        if (entry == lastEntry && contentW == lastWidth && lastPage != null) {
            return lastPage;
        }
        String key = entry.key + "#" + contentW;
        Page p = cache.get(key);
        if (p == null) {
            try {
                p = build(view, entry);
            } catch (RuntimeException e) {
                // 块级的错误 build 里已经逐块兜住；能到这里的是标题区这类整篇共用的部分出错。
                // 换成一句提示并缓存下来，别让每一帧都重排、重抛。
                // 用「出错」而不是「没有可显示的内容」：后者读起来像这个玩法没开，会把书的毛病说成玩法的事
                GuideLog.warnOnce("page:" + entry.key, entry.key + " 排版失败: " + e);
                Laid msg = GuideBlocks.message(I18n.get("kuvalich.guide.entry_error"),
                        new GuideBlocks.Ctx(view, text, media, contentW));
                p = new Page(new Laid[]{msg}, new int[]{0}, msg.height, new int[0], new int[0], new int[0]);
            }
            cache.put(key, p);
        }
        lastEntry = entry;
        lastWidth = contentW;
        lastPage = p;
        return p;
    }

    private Page build(GuideView view, EntryView ev) {
        GuideBlocks.Ctx ctx = new GuideBlocks.Ctx(view, text, media, contentW);
        ctx.entryKey = ev.key;
        List<Laid> laid = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        int cy = 0;
        Laid title = GuideBlocks.title(ev, ctx);
        laid.add(title);
        ys.add(cy);
        cy += title.height;
        BlockType prev = null;
        int content = 0;
        // 一块都没有（加载时缺 blocks 数组 / 块全坏）或者有块排版抛了异常：整篇空着时要说「出错」，
        // 不能和「条件把块全藏了」共用一句提示
        boolean broken = ev.entry.blocks.isEmpty();
        List<Block> visible = ev.blocks();
        int[] blockY = new int[visible.size()];
        int[] blockH = new int[visible.size()];
        int[] kvHint = kvRunHints(visible, ctx);
        for (int bi = 0; bi < visible.size(); bi++) {
            Block b = visible.get(bi);
            blockY[bi] = -1;
            Laid l;
            try {
                ctx.kvLabelHint = kvHint[bi];
                l = GuideBlocks.layout(b, ctx);
            } catch (RuntimeException e) {
                // 单个块排版出错（内容写法超出预期）只丢这一块，不让整篇打不开
                GuideLog.warnOnce("layout:" + ev.key + ":" + bi, ev.key + " 的一个 " + b.type + " 块排版失败: " + e);
                broken = true;
                continue;
            }
            if (l == null || l.height <= 0) {
                continue;
            }
            cy += gapBefore(b.type, prev, content == 0);
            laid.add(l);
            ys.add(cy);
            blockY[bi] = cy;
            blockH[bi] = l.height;
            cy += l.height;
            prev = b.type;
            content++;
        }
        // 整块隐藏的块（查不到配方）：查阅落到它时停在下一块，后面都没有了就停在它前一块的末尾
        int after = cy;
        for (int bi = visible.size() - 1; bi >= 0; bi--) {
            if (blockY[bi] < 0) {
                blockY[bi] = after;
            } else {
                after = blockY[bi];
            }
        }
        int[] anchorY = new int[visible.size()];
        for (int bi = 0; bi < visible.size(); bi++) {
            boolean headed = bi > 0 && visible.get(bi - 1).type == BlockType.H && blockH[bi - 1] > 0;
            anchorY[bi] = headed ? blockY[bi - 1] : blockY[bi];
        }
        if (content > 0) {
            // 篇末翻页：按全书顺序接上一篇 / 下一篇（入门指南就是靠它串成一条教程线）
            int gi = view.entryList.indexOf(ev);
            GuideView.EntryView prevE = gi > 0 ? view.entryList.get(gi - 1) : null;
            GuideView.EntryView nextE = gi >= 0 && gi + 1 < view.entryList.size() ? view.entryList.get(gi + 1) : null;
            if (prevE != null || nextE != null) {
                try {
                    Laid pg = GuideBlocks.pager(prevE, nextE, ev, ctx);
                    cy += 16;
                    laid.add(pg);
                    ys.add(cy);
                    cy += pg.height;
                } catch (RuntimeException e) {
                    GuideLog.warnOnce("pager:" + ev.key, ev.key + " 翻页卡排版失败: " + e);
                }
            }
        }
        if (content == 0) {
            Laid msg = GuideBlocks.message(I18n.get(broken ? "kuvalich.guide.entry_error" : "kuvalich.guide.entry_empty"), ctx);
            cy += 10;
            laid.add(msg);
            ys.add(cy);
            cy += msg.height;
        }
        int[] yArr = new int[ys.size()];
        for (int i = 0; i < yArr.length; i++) {
            yArr[i] = ys.get(i);
        }
        return new Page(laid.toArray(new Laid[0]), yArr, cy, blockY, blockH, anchorY);
    }

    /**
     * 同一段里的几张键值表共用的标签列宽：取这一段所有键值表里最长标签的自然宽度
     *
     * <p>「一段」按可见块划分：小标题、段落、提示框、分隔线、公式这些没有自己列结构的块<b>不算隔断</b>，
     * 只有表格、列表、物品 / 配方 / 实体这类自带列或自带画面的块才把前后分开。
     * 「数值速查」是「小标题 + 键值表」交替排的，每张表前都隔着一个小标题 ——
     * 要是只让紧挨着的键值表共宽，这一页五张表的中缝会各在各的位置。
     * 条件藏掉的块本来就不在 visible 里，同样不算隔断。
     * 标签列最后仍受 {@code KvLaid.of} 里「最多占一半」的封顶。</p>
     *
     * @param visible 条件成立的块，按原顺序
     * @param ctx     排版上下文
     * @return 与 visible 等长；非键值表为 0
     */
    private static int[] kvRunHints(List<Block> visible, GuideBlocks.Ctx ctx) {
        int[] out = new int[visible.size()];
        List<Integer> run = new ArrayList<>();
        int max = 0;
        for (int i = 0; i <= visible.size(); i++) {
            BlockType t = i < visible.size() ? visible.get(i).type : null;
            if (t == BlockType.KV) {
                try {
                    max = Math.max(max, GuideBlocks.kvNaturalLabelWidth(visible.get(i), ctx));
                } catch (RuntimeException ignored) {
                    // 坏块交给正式排版去兜底、记日志
                }
                run.add(i);
                continue;
            }
            if (t != null && !breaksKvRun(t)) {
                continue;
            }
            for (int k : run) {
                out[k] = max;
            }
            run.clear();
            max = 0;
        }
        return out;
    }

    /**
     * 这种块会不会把前后的键值表分成两段（见 {@link #kvRunHints}）
     */
    private static boolean breaksKvRun(BlockType t) {
        return switch (t) {
            // 通讯框只是插在正文里的一句话，不该把前后的键值表分成两段
            case H, P, TIP, DIVIDER, FORMULA, KV, COMM -> false;
            case LIST, STEPS, TABLE, ITEMS, RECIPE, ENTITY -> true;
        };
    }

    /**
     * 块间距：小标题前多空一点把段落分开，小标题后少空一点让它贴着自己的内容
     */
    private static int gapBefore(BlockType type, @Nullable BlockType prev, boolean first) {
        if (first) {
            return 10;
        }
        if (type == BlockType.H) {
            return 15;
        }
        if (prev == BlockType.H) {
            return 6;
        }
        if (type == BlockType.DIVIDER || prev == BlockType.DIVIDER) {
            return 6;
        }
        // 块与块之间多留几像素：中文正文本来就密，段落再挤在一起读起来像一整面墙（行高 13 之后跟着加到 10）
        return 10;
    }

    float maxScroll() {
        return Math.max(0, pageHeight + PAD_TOP + PAD_BOTTOM - h);
    }

    void scrollBy(float dy) {
        target = Math.max(0, Math.min(maxScroll(), target + dy));
    }

    void scrollTo(float y) {
        target = Math.max(0, Math.min(maxScroll(), y));
    }

    // ==================== 绘制 ====================

    /**
     * @param r       绘制上下文（鼠标已换算到面板坐标）
     * @param view    当前视图
     * @param delta   距上一帧的秒数
     * @param offsetY 开合动画的整体位移（裁剪框用的是屏幕坐标，要自己加上）
     */
    void render(GuideRender r, GuideView view, float delta, int offsetY) {
        GuiGraphics g = r.g;
        if (entry == null) {
            if (pickHint == null) {
                // 换语言会走资源重载 → invalidate() 清掉它，所以缓存一次就够
                pickHint = I18n.get("kuvalich.guide.pick_entry");
                pickHintW = r.font.width(pickHint);
            }
            g.drawString(r.font, pickHint, x + (w - pickHintW) / 2, y + h / 2 - 4, GuideTheme.FAINT_TEXT, false);
            return;
        }
        Page page = page(view);
        pageHeight = page.height;
        float max = maxScroll();
        if (pendingBlock != -1) {
            // 查阅定位：目标块滚到阅读栏上方（留一行空），直接跳过去不做平滑滚动，然后亮一下
            int block = pendingBlock;
            pendingBlock = -1;
            if (block >= 0 && block < page.blockY.length) {
                flashY = page.blockY[block];
                flashH = Math.max(page.blockH[block], 8);
                scroll = target = Math.max(0, Math.min(max, page.anchorY[block] + PAD_TOP - 14));
            } else {
                flashY = 0;
                flashH = page.blocks.length > 0 ? page.blocks[0].height : 0;
                scroll = target = 0;
            }
            flashAt = System.currentTimeMillis();
        }
        target = Math.max(0, Math.min(max, target));
        scroll = GuideTheme.approach(Math.min(scroll, max), target, delta, 18f);

        float swap = GuideTheme.easeOutCubic((System.currentTimeMillis() - swappedAt) / SWAP_MS);
        g.enableScissor(x, y + offsetY, x + w, y + h + offsetY);
        int savedTop = r.clipTop;
        int savedBottom = r.clipBottom;
        r.clipTop = y;
        r.clipBottom = y + h;
        r.alpha = 0.25f + 0.75f * swap;
        int shift = Math.round((1f - swap) * 6f);

        int top = y + PAD_TOP - Math.round(scroll) + shift;
        float flash = flashAt == 0L ? 0f : 1f - (System.currentTimeMillis() - flashAt) / FLASH_MS;
        if (flash <= 0f) {
            flashAt = 0L;
        } else {
            // 被查阅的那一块：底下一层极淡的猩红，块画完后再描一圈边（见下）
            int fy = top + flashY - 4;
            g.fill(contentX - 4, fy, contentX + contentW + 4, fy + flashH + 8,
                    GuideTheme.withAlpha(GuideTheme.KUVA, (int) (0x22 * flash)));
        }
        for (int i = 0; i < page.blocks.length; i++) {
            Laid b = page.blocks[i];
            int by = top + page.ys[i];
            if (by + b.height < y - 4) {
                continue;
            }
            if (by > y + h + 4) {
                break;
            }
            b.render(r, contentX, by, contentW);
        }
        r.alpha = 1f;
        if (flash > 0f) {
            int fy = top + flashY - 4;
            // 描边抬到物品图标（z≈150）之上，但低于提示框（400）
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 300f);
            CodexTheme.brackets(g, contentX - 4, fy, contentW + 8, flashH + 8, 6,
                    GuideTheme.withAlpha(GuideTheme.KUVA, (int) (0xF0 * flash)));
            CodexTheme.border(g, contentX - 4, fy, contentW + 8, flashH + 8,
                    GuideTheme.withAlpha(GuideTheme.KUVA, (int) (0x90 * flash)));
            g.pose().popPose();
        }
        r.clipTop = savedTop;
        r.clipBottom = savedBottom;

        // 上下边缘的渐隐：告诉读者「上面 / 下面还有」，而不是让字被裁剪框一刀切断
        if (scroll > 1f) {
            fadeEdge(g, y, true);
        }
        if (scroll < max - 1f) {
            fadeEdge(g, y + h - 8, false);
        }
        g.disableScissor();

        if (max > 0) {
            boolean active = dragging || (r.mouseActive && overScrollbar(r.mouseX, r.mouseY));
            CodexTheme.scrollbar(g, x + w - SCROLLBAR_ZONE + 2, y + 2, h - 4, scroll, max, active);
        }
    }

    private void fadeEdge(GuiGraphics g, int ey, boolean top) {
        // 物品图标画在 z=150 上下并写深度，物品数量字在 z=200：渐隐条留在 z=0 会过不了深度测试，
        // 图标就实心地压在渐隐带上、到裁剪边被一刀切断。抬到它们前面（仍低于 tooltip 的 400）
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        for (int i = 0; i < 8; i++) {
            float k = top ? 1f - i / 8f : i / 8f;
            g.fill(x, ey + i, x + w - SCROLLBAR_ZONE, ey + i + 1, GuideTheme.fade(GuideTheme.PLATE, k * 0.85f));
        }
        g.pose().popPose();
    }

    // ==================== 输入 ====================

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean overScrollbar(double mx, double my) {
        int sx = x + w - SCROLLBAR_ZONE;
        return maxScroll() > 0 && mx >= sx && mx < x + w && my >= y && my < y + h;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) {
            return false;
        }
        scrollBy((float) (-delta * GuideTheme.LINE_H * 3));
        return true;
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overScrollbar(mx, my)) {
            return false;
        }
        float max = maxScroll();
        int trackH = h - 4;
        int thumbH = Math.max(CodexTheme.THUMB_MIN_H, (int) (trackH * (trackH / (trackH + max))));
        int thumbY = y + 2 + (int) ((trackH - thumbH) * (scroll / max));
        if (my < thumbY || my >= thumbY + thumbH) {
            // 点在轨道空白处：滑块中心直接跳过去，与图鉴的手感一致
            double t = (my - y - 2 - thumbH / 2.0) / Math.max(1, trackH - thumbH);
            scroll = target = (float) Math.max(0, Math.min(max, t * max));
        }
        dragging = true;
        dragStartY = my;
        dragStartScroll = scroll;
        return true;
    }

    boolean mouseDragged(double my, int button) {
        if (!dragging) {
            return false;
        }
        if (button != 0) {
            dragging = false;
            return false;
        }
        float max = maxScroll();
        int trackH = h - 4;
        int thumbH = Math.max(CodexTheme.THUMB_MIN_H, (int) (trackH * (trackH / (trackH + max))));
        float perPixel = max / Math.max(1, trackH - thumbH);
        scroll = target = (float) Math.max(0, Math.min(max, dragStartScroll + (my - dragStartY) * perPixel));
        return true;
    }

    void release() {
        dragging = false;
    }
}
