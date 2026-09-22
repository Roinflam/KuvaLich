package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 武器面板的结构化内容 —— 交给 {@link ClientPanelGridTooltip} 做像素级对齐渲染
 *
 * <p><b>为什么要走自定义 {@link TooltipComponent} 而不是继续塞 {@code Component} 行</b>：
 * 纯文本行想让「值」对齐只能靠空格凑，而中文一个字约等于两个字符宽、不同字体包宽度又不一样，
 * 必然错位。第一版因此只能做「流式打包」——把词条一个挨一个塞进一行，
 * 结果属性少的时候反而更难读：名字被迫缩成两个字（基伤 / 暴伤），
 * 整行挤成一堵墙，tooltip 还被撑得很宽。</p>
 *
 * <p>现在把内容按「分组 + 单元格」描述出来，渲染时用 {@code Font#width} 按像素算列宽，
 * 标签左对齐、数值右对齐，中文和任何字体包下都不会错位；
 * 列数由 {@link ClientPanelGridTooltip} 根据内容多少自适应：
 * 属性少就一条一行（宽松好读），多到快超屏才自动切两列 / 三列。</p>
 *
 * <p>兼容性：整合包里的 {@code obscure_tooltips} 在 {@code wrapLines} 与 {@code compact}
 * 两处都只处理 {@code ClientTextTooltip}，非文本组件原样放行（已核实源码），
 * 所以自定义组件不会被它吃掉。代价是它的「压缩行距」不会作用到本组件，
 * 行高由 {@code panel.rowHeight} 配置自己控制。</p>
 *
 * @param sections 分组内容，按显示顺序排列
 *
 * @author RoinFlam
 */
public record PanelGridTooltip(List<Section> sections, int maxColumns) implements TooltipComponent {

    /** 不限列数（由内容多少自适应） */
    public static final int COLUMNS_AUTO = 0;

    /**
     * 四个视图共用同一种构造 —— 排版必须一致
     *
     * <p>⭐ 这里曾经分过 {@code compact} / {@code detail} 两种，给功能键视图更宽松的
     * 行数预算。但列数是按行数预算反推的，预算不同就等于排版不同：屏幕一高，
     * SHIFT 视图停在一列、每条一行，默认视图还是两列，同一把武器按下 SHIFT
     * 像换了个模组。现在预算统一在 {@link ClientPanelGridTooltip#layout} 里算。</p>
     */
    public PanelGridTooltip(List<Section> sections) {
        this(sections, COLUMNS_AUTO);
    }

    /** 一个分组 */
    public sealed interface Section {

        /** 分组标题；null 表示这一组不要标题行 */
        @Nullable
        Component header();

        /** 这一组有几行内容（用于自适应列数时估算总行数） */
        int cellCount();
    }

    /**
     * 「标签 + 数值」成对的分组（最终面板 / 伤害增益 / 枪械 / 稀有 / 额外 / 其它）
     *
     * <p>渲染时标签左对齐、数值右对齐，列数自适应。</p>
     */
    public record Pairs(@Nullable Component header, List<Cell> cells) implements Section {
        @Override
        public int cellCount() {
            return cells.size();
        }
    }

    /**
     * 列结构固定的表格分组（击杀叠层 / 元素）
     *
     * <p>叠层每行是「名称 | 进度条 | 当前加成 | 倒计时」四列，
     * 这种结构用成对单元格表达不了，但它同样需要列对齐 ——
     * 否则几行叠层的圆点条会参差不齐。</p>
     *
     * @param rows   每行的单元格；各行列数必须一致
     * @param aligns 每列的对齐方式，长度与列数一致
     */
    public record Table(@Nullable Component header, List<List<Component>> rows, Align[] aligns) implements Section {
        @Override
        public int cellCount() {
            return rows.size();
        }
    }

    /**
     * 单值多列的分组（已装模组名单）
     *
     * <p>一串没有「值」的条目，按列数自适应排成对齐的几列。</p>
     */
    public record Columns(@Nullable Component header, List<Component> items) implements Section {
        @Override
        public int cellCount() {
            return items.size();
        }
    }

    /**
     * 自由文本行的分组
     *
     * <p>内容已经在构建时按宽度折好行，渲染时逐行原样画出。</p>
     */
    public record Flow(@Nullable Component header, List<Component> lines) implements Section {
        @Override
        public int cellCount() {
            return lines.size();
        }
    }

    /**
     * 一个「标签 + 数值」单元格
     *
     * @param wide 这一条是否独占一整行
     *
     *             <p>⭐ 为什么需要它：多列网格的列宽是<b>按该列最宽的内容</b>定的，
     *             所以一条特别宽的词条会把整列撑开，害得同列其它词条的标签与数值之间
     *             拉出一大段空白。最典型的是成对词条（{@code x4.5 / x2.8}）——
     *             它比同组的 {@code 113%} 宽两三倍，一挤进列里整组都变得松垮。
     *             这类词条干脆整行独占，反而比硬塞进格子好看。</p>
     */
    public record Cell(Component label, Component value, boolean wide) {

        /** 普通单元格：参与多列排版 */
        public Cell(Component label, Component value) {
            this(label, value, false);
        }

        /** 同一条内容，改为独占一整行 */
        public Cell asWide() {
            return new Cell(label, value, true);
        }
    }

    /** 列对齐方式 */
    public enum Align {
        LEFT,
        RIGHT
    }

    /** 本组件总共要占多少行（含标题行） */
    public int totalRowsSingleColumn() {
        int n = 0;
        for (Section s : sections) {
            if (s.header() != null) {
                n++;
            }
            n += s.cellCount();
        }
        return n;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
