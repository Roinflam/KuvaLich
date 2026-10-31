package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Link;
import pers.roinflam.kuvalich.client.gui.guide.GuideTextLayout.Line;
import pers.roinflam.kuvalich.client.gui.guide.GuideTextLayout.Piece;

import javax.annotation.Nullable;

/**
 * 一帧的绘制上下文：画布、鼠标、可见范围、透明度，以及本帧「鼠标压着什么」
 *
 * <h3>悬停为什么延迟一帧</h3>
 * <p>一条链接折行后是好几段，鼠标压在第二段上时第一段也要一起变色 —— 可画第一段时还不知道
 * 鼠标压在第二段上。与其先把整页走一遍做命中测试再画，不如用上一帧的结果决定样式、
 * 这一帧边画边记录新的结果。差一帧（十几毫秒）肉眼看不出来，却省掉了一整遍遍历。
 * 点击同理：直接用最近一帧记下的链接 / 物品，画的位置和点的位置不可能对不上。</p>
 *
 * <h3>故障段（Ordis 的 {@code <x>…</x>}）</h3>
 * <p>先把整行的故障字画两层残影（左下错 1px 的品红、右上错 1px 的青，各 45% 不透明），再画本体：
 * 本体逐字按排版时算好的错位水平挪 1px。偏移全在 {@link Piece} 里，这里只查表。
 * 画法是静态的，不闪不动；只有通讯框擦出前沿走到故障段的那两拍会通过 {@link #glitchKick} 让整段再横跳一下。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideRender {

    GuiGraphics g;
    Font font;
    /** 配方 / 实体预览的缓存（随界面实例） */
    GuideMedia media;
    /** 鼠标位置（面板开合只裁剪、不平移，所以就是屏幕坐标） */
    int mouseX;
    int mouseY;
    /** 鼠标是否在可交互区域内（被裁掉的部分、动画进行中都不算） */
    boolean mouseActive;
    /**
     * 左键现在按着（直接问 GLFW；点击在 mouseClicked 里就生效了，这只决定按住时画成什么样）：
     * 格子、顶栏按钮、翻页卡、继续阅读条按住时沉回原位、底色转暗
     */
    boolean mouseDown;
    /** 可见区域的上下边界（面板坐标），用来跳过看不见的行 */
    int clipTop;
    int clipBottom;
    /** 文字与填充的整体透明度（换页淡入用） */
    float alpha = 1f;
    long now;
    /** 这一帧的插值进度（通讯肖像里的玄骸模型用） */
    float partialTick;
    /** 悬停锁定括号的限速（整个界面共用一个） */
    GuideLockBudget lockBudget;
    /** 故障段额外的整体水平错位（通讯框擦到故障段时的两拍横跳），平时为 0；谁设的谁画完清回 0 */
    int glitchKick;

    /** 故障段残影：偏红的品红、青，45% 不透明 */
    static final int GHOST_MAGENTA = 0x73FF4FD8;
    static final int GHOST_CYAN = 0x734FE8FF;

    /** 上一帧鼠标下的链接：决定本帧哪条链接画成悬停样式 */
    @Nullable
    Link hoverLink;
    /** 本帧检测到的链接 */
    @Nullable
    Link nextHoverLink;
    /** 本帧鼠标下的物品：在 drawManaged 之外画原版 tooltip */
    ItemStack hoverStack = ItemStack.EMPTY;

    /** 帧开始：把上一帧的检测结果转正 */
    void beginFrame() {
        hoverLink = nextHoverLink;
        nextHoverLink = null;
        hoverStack = ItemStack.EMPTY;
    }

    int text(int argb) {
        return GuideTheme.textAlpha(argb, alpha);
    }

    int fill(int argb) {
        return GuideTheme.fade(argb, alpha);
    }

    boolean over(int x, int y, int w, int h) {
        return mouseActive && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseY >= clipTop && mouseY < clipBottom;
    }

    boolean visible(int y, int h) {
        return y + h > clipTop && y < clipBottom;
    }

    /**
     * 这一帧画不画物品图标 / 实体模型
     *
     * <p>它们没有透明度可调：换篇淡入时文字从 25% 慢慢浮现，图标却一上来就是实心的，
     * 像先于正文蹦出来。淡到八成再出现（约 60ms），和首页格子「浮现到一半再出图标」是同一个做法。</p>
     */
    boolean solidReady() {
        return alpha >= 0.8f;
    }

    /**
     * 画一行排好的文字
     *
     * @param line   行
     * @param x      行首 x
     * @param y      行顶 y
     * @param shadow 是否带阴影
     */
    void line(Line line, int x, int y, boolean shadow) {
        Piece[] pieces = line.pieces;
        int n = pieces.length;
        if (line.glitch) {
            // 残影整行先画，免得后一个字的残影叠在前一个字的本体上
            int magenta = text(GHOST_MAGENTA);
            int cyan = text(GHOST_CYAN);
            for (int i = 0; i < n; i++) {
                Piece p = pieces[i];
                if (p.ghost != null) {
                    int px = x + p.x + glitchKick;
                    g.drawString(font, p.ghost, px - 1, y + 1, magenta, false);
                    g.drawString(font, p.ghost, px + 1, y - 1, cyan, false);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            Piece p = pieces[i];
            int px = x + p.x;
            Link link = p.link;
            boolean live = link != null && link.isLive();
            if (link != null && over(px, y - 1, Math.max(1, p.w), GuideTheme.LINE_H)) {
                nextHoverLink = link;
                if (link.isItem()) {
                    // 物品链接：悬停出物品提示（和物品格一样）
                    hoverStack = link.stack;
                }
            }
            boolean hovered = live && link == hoverLink;
            int dx = p.glitch ? p.jx + glitchKick : 0;
            g.drawString(font, hovered && p.hoverSeq != null ? p.hoverSeq : p.seq, px + dx, y, text(0xFFFFFFFF), shadow);
            if (live) {
                if (hovered) {
                    g.fill(px, y + 9, px + p.w, y + 10, fill(GuideTheme.KUVA));
                } else {
                    int under = link.isItem() ? GuideTheme.ITEM_LINK : GuideTheme.LINK;
                    GuideTheme.dotted(g, px, y + 9, p.w, fill(GuideTheme.withAlpha(under, 0x90)));
                }
            }
        }
    }

    /**
     * 画多行（只画可见的）
     *
     * @return 画完之后的 y
     */
    int lines(Line[] lines, int x, int y, int lineH, boolean shadow) {
        for (Line l : lines) {
            if (visible(y, lineH)) {
                line(l, x, y, shadow);
            }
            y += lineH;
        }
        return y;
    }

    /**
     * 多行居中
     */
    int linesCentered(Line[] lines, int x, int w, int y, int lineH, boolean shadow) {
        for (Line l : lines) {
            if (visible(y, lineH)) {
                line(l, x + (w - l.width) / 2, y, shadow);
            }
            y += lineH;
        }
        return y;
    }
}
