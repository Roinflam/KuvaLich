package pers.roinflam.kuvalich.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;

/**
 * 容器界面的自绘外观：把图鉴那套军械库全息风格套到 {@code AbstractContainerScreen} 上
 *
 * <h3>为什么要单独一层</h3>
 * <p>{@link CodexTheme} 管的是「视觉语言」——斜切、扫描线、发光线、配色。
 * 本类管的是「容器界面怎么用这套语言画出来」：槽位凹槽、槽位状态、走线、进度条。
 * 两者分开，是因为图鉴不需要槽位，而容器界面不需要滚动条与微粒。</p>
 *
 * <h3>为什么槽位是遍历出来的而不是写死坐标</h3>
 * <p>五个界面的槽位坐标全部写死在各自 Menu 的 {@code addSlot(x, y)} 里，
 * 动它们会让拖拽与 Shift 快捷移动错位。所以这里<b>一个坐标都不写</b>：
 * 直接遍历 {@code menu.slots} 拿每个槽的 {@code x/y}（那本来就是相对
 * {@code leftPos/topPos} 的），照着画凹槽。加了槽、挪了槽，外观自动跟上。</p>
 *
 * <h3>原先贴图里携带的信息</h3>
 * <p>这五张背景贴图不只是装饰，里面烘焙了<b>有信息量</b>的图形：武器军械库上
 * 8 个模组格汇聚到武器格的电路线、安魂之融/之铸上指向结果槽的箭头与汇聚线、
 * 灭骸之扉未揭示槽的「⊘」符号。删掉贴图而不补这些，玩家会看不出
 * 「东西要往哪个槽放」。{@link #busRoute} 与 {@link #forbidden} 就是它们的自绘等价物。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ContainerChrome {

    private ContainerChrome() {}

    /** 原版槽位的视觉边长（物品 16px + 1px 内边距 ×2） */
    public static final int SLOT = 18;

    /** 槽位的语义类型，决定凹槽的描边色与角标 */
    public enum SlotKind {
        /** 玩家背包 / 快捷栏 */
        PLAYER,
        /** 机器的普通输入槽（模组槽、材料槽） */
        INPUT,
        /** 产出槽 / 结果槽：玩家取走而不是放入 */
        OUTPUT,
        /** 主体槽：军械库里那把被改装的武器 */
        SUBJECT,
        /** 不可用（超出槽位上限等） */
        LOCKED
    }

    /**
     * 判定某个槽是什么类型
     *
     * <p>玩家背包由本类自动识别（{@code slot.container instanceof Inventory}），
     * 只有机器自己的槽才会问到调用方 —— 各个界面清楚自己的槽位分区，
     * 而槽位的内部类都是 private 的，从外面 {@code instanceof} 不到。</p>
     */
    @FunctionalInterface
    public interface SlotKindResolver {
        /**
         * @param slot  槽位
         * @param index 槽位在 {@code menu.slots} 里的下标
         * @return 类型
         */
        SlotKind kindOf(Slot slot, int index);
    }

    // ==================== 整体外观 ====================

    /**
     * 画容器面板的底：斜切板材 + 全息表面 + 发光轮廓
     *
     * <p>尺寸直接用 {@code imageWidth/imageHeight} —— 这两个字段除了决定面板大小，
     * 还被几个界面用来给 {@code [TAB]} 提示定位，必须原样保留，不能「顺手优化掉」。</p>
     *
     * @param g    画布
     * @param left 面板左（{@code leftPos}）
     * @param top  面板上（{@code topPos}）
     * @param w    面板宽（{@code imageWidth}）
     * @param h    面板高（{@code imageHeight}）
     */
    public static void panel(GuiGraphics g, int left, int top, int w, int h) {
        CodexTheme.chamferFill(g, left, top, w, h, CodexTheme.CHAMFER, CodexTheme.PLATE);
        CodexTheme.holoSurface(g, left + 2, top + 2, w - 4, h - 4, 12);
        CodexTheme.chamferGlow(g, left, top, w, h, CodexTheme.CHAMFER,
                CodexTheme.withAlpha(CodexTheme.TECH, 0xB0), 1f);
    }

    /**
     * 给一块区域画「分区底板」，用来把机器区和玩家背包区在视觉上分开
     *
     * @param g 画布
     * @param x 左
     * @param y 上
     * @param w 宽
     * @param h 高
     */
    public static void zone(GuiGraphics g, int x, int y, int w, int h) {
        CodexTheme.chamferFill(g, x, y, w, h, 4, CodexTheme.withAlpha(CodexTheme.PLATE_HI, 0xB0));
        CodexTheme.chamferOutline(g, x, y, w, h, 4, CodexTheme.withAlpha(CodexTheme.EDGE, 0xC0));
    }

    // ==================== 槽位 ====================

    /**
     * 遍历菜单里的所有槽位，逐个画出凹槽
     *
     * <p>玩家背包格的凹槽原先<b>完全来自那一张全景背景贴图</b>，没有任何独立绘制代码。
     * 删掉贴图却不补这一步，背包区会变成一片纯色，玩家看不出哪里能放东西 ——
     * 这是换皮最容易漏的一环。</p>
     *
     * @param g        画布
     * @param menu     菜单
     * @param left     面板左
     * @param top      面板上
     * @param resolver 机器槽的类型判定；传 null 则机器槽一律按 {@link SlotKind#INPUT}
     */
    public static void slots(GuiGraphics g, AbstractContainerMenu menu,
                             int left, int top, SlotKindResolver resolver) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            SlotKind kind = slot.container instanceof Inventory
                    ? SlotKind.PLAYER
                    : (resolver != null ? resolver.kindOf(slot, i) : SlotKind.INPUT);
            slot(g, left + slot.x - 1, top + slot.y - 1, kind);
        }
    }

    /**
     * 画一个槽位凹槽
     *
     * @param g    画布
     * @param x    凹槽左（槽位 x - 1）
     * @param y    凹槽上（槽位 y - 1）
     * @param kind 类型
     */
    public static void slot(GuiGraphics g, int x, int y, SlotKind kind) {
        // 凹陷底：比面板更暗，制造「嵌进去」的层次
        g.fill(x, y, x + SLOT, y + SLOT, CodexTheme.PLATE_DEEP);

        int edge;
        switch (kind) {
            case OUTPUT: edge = CodexTheme.EMBER; break;
            case SUBJECT: edge = CodexTheme.KUVA; break;
            case LOCKED: edge = CodexTheme.withAlpha(CodexTheme.EDGE, 0x80); break;
            case PLAYER: edge = CodexTheme.withAlpha(CodexTheme.EDGE, 0xC0); break;
            default: edge = CodexTheme.withAlpha(CodexTheme.TECH, 0x70); break;
        }
        CodexTheme.border(g, x, y, SLOT, SLOT, edge);

        // 机器槽额外给一个左上角标，让它和玩家背包一眼分得开
        if (kind == SlotKind.OUTPUT || kind == SlotKind.SUBJECT) {
            CodexTheme.cornerTab(g, x + 1, y + 1, 4, edge);
        }

        if (kind == SlotKind.LOCKED) {
            // ⭐ 锁定槽必须画出来。改造前超出 moduleLimit 的槽位**没有任何视觉区分**，
            //    玩家唯一的反馈是「放不进去」这个静默拒绝。既然要重画，顺手把它补上：
            //    压暗 + 斜线阴影，一眼看出「这格现在用不了」。
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xB0080A0C);
            for (int d = -SLOT; d < SLOT; d += 4) {
                for (int t = 0; t < SLOT; t++) {
                    int px = x + 1 + d + t;
                    int py = y + 1 + t;
                    if (px < x + 1 || px >= x + SLOT - 1 || py >= y + SLOT - 1) continue;
                    g.fill(px, py, px + 1, py + 1, CodexTheme.withAlpha(CodexTheme.EDGE, 0xA0));
                }
            }
        }
    }

    // ==================== 走线 ====================

    /**
     * 「总线走线」：从若干起点汇聚到一个终点，全程轴对齐，沿线跑一颗流动光点
     *
     * <p>这是原贴图上那些金色电路线的自绘等价物 —— 武器军械库的 8 个模组格汇聚到武器格、
     * 安魂之铸的三个输入槽汇聚到输出槽，表达的都是「这些东西往那里去」。
     * 丢了它，玩家看不出投料方向。</p>
     *
     * <p>用折线而不是斜线有两个原因：{@code GuiGraphics.fill} 只能画轴对齐矩形，
     * 斜线要逐像素堆；而且直角折线本来就更像电路板走线，比斜线更贴题。</p>
     *
     * @param g       画布
     * @param originX 各起点的 x（通常是槽位中心）
     * @param originY 各起点的 y（线从这里出发）
     * @param busY    汇流母线的 y
     * @param targetX 终点 x
     * @param targetY 终点 y
     * @param color   线色
     * @param millis  当前时间，用于推动流动光点
     */
    public static void busRoute(GuiGraphics g, int[] originX, int[] originY,
                                int busY, int targetX, int targetY, int color, long millis) {
        if (originX == null || originX.length == 0) return;
        int dim = CodexTheme.withAlpha(color, 0x50);

        int minX = targetX, maxX = targetX;
        for (int i = 0; i < originX.length; i++) {
            int ox = originX[i];
            minX = Math.min(minX, ox);
            maxX = Math.max(maxX, ox);
            // 起点竖线：从槽位出发走到母线
            int y0 = Math.min(originY[i], busY);
            int y1 = Math.max(originY[i], busY);
            g.fill(ox, y0, ox + 1, y1, dim);
        }
        // 母线
        g.fill(minX, busY, maxX + 1, busY + 1, dim);
        // 终点竖线
        int ty0 = Math.min(busY, targetY);
        int ty1 = Math.max(busY, targetY);
        g.fill(targetX, ty0, targetX + 1, ty1, dim);

        // 流动光点：沿母线朝终点方向走，2.2 秒一轮。
        // 它就是这条线的全部意义 —— 静态的灰线只是装饰，会动的才读得出「流向」。
        int span = maxX - minX;
        if (span > 0) {
            float phase = (millis % 2200L) / 2200f;
            int px = minX + (int) (phase * span);
            for (int i = -5; i <= 5; i++) {
                int x = px + i;
                if (x < minX || x > maxX) continue;
                int a = (int) (0xD0 * (1f - Math.abs(i) / 6f));
                g.fill(x, busY, x + 1, busY + 1, CodexTheme.withAlpha(color, a));
            }
        }
    }

    // ==================== 图标 ====================

    /**
     * 「禁止」符号：一个圆圈加一道斜杠
     *
     * <p>灭骸之扉的未揭示谜语槽原先是从贴图里 blit 一个金色的 ⊘。
     * 删掉贴图就必须自己画一个，否则未揭示的槽会和普通空槽长得一模一样，
     * 玩家会以为那里可以随便放卡。</p>
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param size  边长
     * @param color 颜色
     */
    public static void forbidden(GuiGraphics g, int x, int y, int size, int color) {
        int r = size / 2;
        int cx = x + r, cy = y + r;
        // 圆：按八分对称逐像素点出来，size 很小，成本可以忽略
        int rr = r - 1;
        for (int i = 0; i <= rr; i++) {
            int j = (int) Math.round(Math.sqrt((double) rr * rr - (double) i * i));
            plot(g, cx + i, cy + j, color); plot(g, cx - i, cy + j, color);
            plot(g, cx + i, cy - j, color); plot(g, cx - i, cy - j, color);
            plot(g, cx + j, cy + i, color); plot(g, cx - j, cy + i, color);
            plot(g, cx + j, cy - i, color); plot(g, cx - j, cy - i, color);
        }
        // 斜杠
        for (int i = -rr; i <= rr; i++) {
            plot(g, cx + i, cy - i, color);
        }
    }

    /**
     * 点一个像素。
     *
     * @param g     画布
     * @param x     x
     * @param y     y
     * @param color 颜色
     */
    private static void plot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
    }

    // ==================== 进度条 ====================

    /**
     * 进度条：斜切轨道 + 填充 + 端点刻度
     *
     * <p>原先轨道与填充都是从背景贴图下方 blit 出来的两条 152×18 子图。
     * 注意那几处 blit 把 {@code imageHeight} 当成了 UV 的 v 坐标用 ——
     * 换自绘之后这层耦合就断了，{@code imageHeight} 回归成单纯的画布高度。</p>
     *
     * @param g        画布
     * @param x        左
     * @param y        上
     * @param w        宽
     * @param h        高
     * @param progress 进度 0~1
     * @param color    填充色
     */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int color) {
        float p = Math.max(0f, Math.min(1f, progress));
        CodexTheme.chamferFill(g, x, y, w, h, 3, CodexTheme.PLATE_DEEP);
        CodexTheme.chamferOutline(g, x, y, w, h, 3, CodexTheme.withAlpha(CodexTheme.EDGE, 0xE0));
        int fillW = (int) ((w - 4) * p);
        if (fillW > 0) {
            g.fill(x + 2, y + 2, x + 2 + fillW, y + h - 2, CodexTheme.withAlpha(color, 0xD0));
            // 填充前沿一条亮边，让「涨到哪了」看得清
            g.fill(x + 1 + fillW, y + 1, x + 3 + fillW, y + h - 1, color);
        }
    }
}
