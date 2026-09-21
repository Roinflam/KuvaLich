package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 面板的四种视图 —— 按住不同功能键切换（类似匠魂 / Tinkers' Construct 的分层 tooltip）
 *
 * <p>设计取舍：**默认态不藏信息**。
 * 「按住 SHIFT 才显示全部属性」这种做法工程上最省事，但它把
 * 「排版舒服、信息全」这个本来就成立的优点当代价付掉了 ——
 * 玩家抱怨的是属性**铺满屏幕**（排版问题），不是信息**太多**（信息量问题）。
 * 所以默认态用「同组打包同行 + 无关组不生成」把 55~65 行压到 18 行，一条信息都不删；
 * 功能键提供的是**另外三种看同一份数据的角度**，而不是「把藏起来的东西放出来」。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public enum TooltipView {

    /** 默认：分组紧凑面板，同组词条打包同行 */
    COMPACT("compact"),

    /**
     * SHIFT：逐条完整视图，每条一行、完整长名
     *
     * <p>形态与改造前一致；<b>顺序改为按语义分组</b>
     * （核心面板 → 伤害增益 → 枪械 → 稀有 → 元素 → 叠层 → 额外 → 模组），
     * 而不是改造前那个按源码书写顺序的混排。数值无差异。</p>
     */
    FULL("full"),

    /** CTRL：来源分解 —— 每张模组卡贡献了什么、额外槽位来自哪里、哪条被配置上限截断了 */
    SOURCE("source"),

    /** ALT：实时状态 —— 击杀叠层层数与衰减、元素组合链、触发几率分解 */
    LIVE("live");

    private final String id;

    TooltipView(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 该视图的提示文案翻译键 */
    public String hintKey() {
        return "kuvalich.panel.hint." + id;
    }

    /**
     * 根据当前按键状态决定视图
     *
     * <p>同时按住多个键时按 SHIFT &gt; CTRL &gt; ALT 取优先级 ——
     * SHIFT 在背包里本来就常按（快速移动物品），让它对应「最保守、最接近老版本」的视图
     * 能保证误触时看到的是熟悉的排版而不是一个陌生界面。</p>
     */
    public static TooltipView current() {
        if (Screen.hasShiftDown()) {
            return FULL;
        }
        if (Screen.hasControlDown()) {
            return SOURCE;
        }
        if (Screen.hasAltDown()) {
            return LIVE;
        }
        return COMPACT;
    }
}
