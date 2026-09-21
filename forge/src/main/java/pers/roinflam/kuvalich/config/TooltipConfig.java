package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 面板（tooltip）显示配置 —— 纯客户端
 *
 * <p>⭐ 刻意注册为 {@code Type.CLIENT} 而不是塞进已有的 {@code COMMON_CONFIG}：
 * 这些全是观感项，一个都不该被服务端的 toml 左右。
 * （项目里既有的 {@code kuvalich-common.toml} / {@code kuvalich-modules.toml}
 * 都注册成了 COMMON，而 Forge 只同步 {@code Type.SERVER}，
 * 所以联机时客户端读的其实一直是自己那份——观感项这样没问题，
 * 但数值项这么做会让面板和服务端实际生效值对不上。）</p>
 *
 * @author RoinFlam
 */
public final class TooltipConfig {

    /** 客户端配置规范 */
    public static final ForgeConfigSpec CLIENT_CONFIG;

    /** 面板显示配置 */
    public static final PanelConfig PANEL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        PANEL = new PanelConfig(builder);
        CLIENT_CONFIG = builder.build();
    }

    /** 面板密度 */
    public enum Density {
        /** 分组紧凑（默认）：同组词条打包同行，无关组不生成 */
        COMPACT,
        /** 经典逐条：与改造前逐字一致，一条词条一行 */
        CLASSIC
    }

    /** 叠层进度的显示方式 */
    public enum StackStyle {
        /** 圆点条：{@code ●●●○○ 3/5} */
        DOTS,
        /** 纯数字：{@code 3/5}（自装字体包把 unifont 回退去掉时用这个，避免豆腐块） */
        NUMERIC
    }

    /** 面板在 tooltip 里的位置 */
    public enum Position {
        /**
         * 自动（默认）：与改造前完全一致
         *
         * <p>实现上是在 {@code ItemTooltipEvent} 里放一个占位行，再在渲染阶段换成面板。
         * 于是本模组自己的武器面板落在物品名下面，而 TACZ 枪械因为 TACZ 会重建整个
         * tooltip，面板自然落在它那块弹药 / 枪种信息之后 —— 两边都是玩家熟悉的位置。</p>
         */
        AUTO,
        /**
         * 强制贴在物品名下面
         * <p>注意：TACZ 枪械会因此把它自带的弹药 / 枪种 / 基础伤害整块挤下去。</p>
         */
        TOP,
        /**
         * 接在其它内容后面（默认）
         * <p>TACZ 枪械这类<b>本身就有一大块自带信息</b>的物品适合这个位置：
         * 弹药、枪种、基础伤害这些是枪的身份信息，不该被本模组的面板挤到下面去。</p>
         */
        BOTTOM
    }

    /** 面板里用到的符号字符 */
    public enum SymbolStyle {
        /** Unicode：装订线 ▎、箭头 →、圆点 ●○、分隔 ·（原版 unifont 回退可覆盖） */
        UNICODE,
        /** 纯 ASCII：| -&gt; * o ,（整合包自装字体包移除了 unifont 回退时用这个） */
        ASCII
    }

    public static class PanelConfig {

        // ========== 总体 ==========

        /**
         * 本模组的武器面板总开关
         * <p>整合包如果用自己的 HUD / 属性展示方案，可以在这里整体关掉，
         * 而不必去动每一个子开关。</p>
         */
        public final ForgeConfigSpec.BooleanValue enabled;

        /** 面板在 tooltip 里的位置 */
        public final ForgeConfigSpec.EnumValue<Position> position;
        /** 面板密度；CLASSIC 是与其它 tooltip 模组冲突时的总退路 */
        public final ForgeConfigSpec.EnumValue<Density> density;
        /** 一个物品的 KuvaLich 面板最多占多少行（硬上限兜底） */
        public final ForgeConfigSpec.IntValue maxLines;
        /** 面板占屏宽的比例上限 */
        public final ForgeConfigSpec.DoubleValue widthRatio;
        /** 面板每行的高度（像素） */
        public final ForgeConfigSpec.IntValue rowHeight;
        /** 是否显示行首的分组装订线 */
        public final ForgeConfigSpec.BooleanValue showGutter;
        /** 是否显示底部的按键提示行 */
        public final ForgeConfigSpec.BooleanValue showKeyHint;
        /** 面板符号字符集 */
        public final ForgeConfigSpec.EnumValue<SymbolStyle> symbolStyle;
        /** 是否按武器类型隐藏无关词条组（近战武器不显示枪械词条等） */
        public final ForgeConfigSpec.BooleanValue hideIrrelevantGroups;

        // ========== 分组开关（与 PanelGroup 一一对应）==========

        /** 伤害增益组 */
        public final ForgeConfigSpec.BooleanValue showDamageGroup;
        /** 枪械专属组 */
        public final ForgeConfigSpec.BooleanValue showGunGroup;
        /** 稀有效果组 */
        public final ForgeConfigSpec.BooleanValue showRareGroup;
        /** 描述表不认识的属性（整合包自定义词条） */
        public final ForgeConfigSpec.BooleanValue showUnknownAttributes;

        // ========== 叠层 ==========

        /** 是否显示击杀叠层区块 */
        public final ForgeConfigSpec.BooleanValue showStacks;
        /** 叠层进度的显示方式 */
        public final ForgeConfigSpec.EnumValue<StackStyle> stackStyle;
        /** 是否显示衰减倒计时 */
        public final ForgeConfigSpec.BooleanValue showDecayTimer;
        /** 是否给分组加淡背景条 */
        public final ForgeConfigSpec.BooleanValue showGroupBands;

        // ========== 区块开关 ==========

        /** 是否显示元素伤害行 */
        public final ForgeConfigSpec.BooleanValue showElements;
        /** 是否显示额外装备槽位加成 */
        public final ForgeConfigSpec.BooleanValue showExtraSlots;
        /** 是否显示已装备的模组卡名单 */
        public final ForgeConfigSpec.BooleanValue showModuleList;
        /** 是否在词条被配置上限截断时给出提示（改造前是静默裁剪，面板会显示高于实际生效的值） */
        public final ForgeConfigSpec.BooleanValue showClampWarning;

        PanelConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("═══════════════ 面板显示 / Tooltip Panel ═══════════════")
                    .push("panel");

            builder.comment(
                    "本模组武器面板的总开关 / Master switch for the weapon panel",
                    "整合包若使用自己的属性展示方案，可在此整体关闭。",
                    "关闭后仍保留：模组卡本身的词条、赤毒类型行、玄骸之力行。");
            enabled = builder.define("enabled", true);

            builder.comment(
                    "面板在 tooltip 里的位置 / Where the panel goes in the tooltip",
                    "AUTO（默认）：与改造前完全一致 —— 本模组自己的武器面板贴在物品名下面；",
                    "　　　　　　　TACZ 枪械因为 TACZ 会重建整个 tooltip，面板落在它那块",
                    "　　　　　　　弹药 / 枪种信息之后。两边都是玩家熟悉的位置。",
                    "TOP：强制贴在物品名下面（TACZ 枪械的自带信息块会被挤下去）。",
                    "BOTTOM：强制接在所有内容之后。");
            position = builder.defineEnum("position", Position.AUTO);

            builder.comment(
                    "面板密度 / Panel density",
                    "COMPACT: 分组紧凑，同组词条打包同行，无关组不生成（默认）",
                    "CLASSIC: 经典逐条，与旧版本排版一致；与其它 tooltip 模组冲突时切到这个");
            density = builder.defineEnum("density", Density.COMPACT);

            builder.comment(
                    "本模组面板最多占多少行 / Max panel lines",
                    "注意：1.20.1 原版对超高 tooltip 只做纵向钳位，不裁剪也不滚动，",
                    "超屏时上下同时溢出，连物品名都会被切掉，所以这是一条硬约束。",
                    "参考：1080p·GUI缩放3 约 32 行可用；1280x720·GUI缩放4 只有约 14 行。");
            maxLines = builder.defineInRange("maxLines", 24, 8, 80);

            builder.comment(
                    "面板允许占屏幕宽度的比例上限 / Max panel width as a ratio of screen width",
                    "放不下时面板会自动多排一列（更宽、更矮），这个值就是「宽」的上限。");
            widthRatio = builder.defineInRange("widthRatio", 0.5, 0.3, 0.9);

            builder.comment(
                    "面板每行的高度（像素）/ Panel row height in pixels",
                    "原版一行文本是 10px。若整合包里的 tooltip 模组开了「压缩行距」",
                    "（例如 Vibrant/Obscure Tooltips 的 removeAllSpacing，它把文本行压到 8px），",
                    "本面板是自定义渲染组件、不受其影响，把这里也调成 8 可以与周围对齐。");
            rowHeight = builder.defineInRange("rowHeight", 10, 7, 14);

            builder.comment(
                    "是否在分组标题前加一条彩色装订线 / Prefix group headers with a coloured gutter bar",
                    "默认关闭：分组已经靠「暗灰色标题独立成行 + 内容缩进」表达层次了，",
                    "再加一竖反而显得噪。想要一点颜色提示的话可以打开（只画在标题行上）。");
            showGutter = builder.define("showGutter", false);

            builder.comment("是否显示底部的按键提示行（SHIFT/CTRL/ALT）/ Show the key hint line");
            showKeyHint = builder.define("showKeyHint", true);

            builder.comment(
                    "面板符号字符集 / Symbol character set",
                    "UNICODE: 装订线 ▎、箭头 →、圆点 ●○、分隔 ·（默认，依赖原版 unifont 回退）",
                    "ASCII:   | -> * o ,  —— 整合包自装字体包移除了 unifont 回退时用这个");
            symbolStyle = builder.defineEnum("symbolStyle", SymbolStyle.UNICODE);

            builder.comment(
                    "是否按武器类型隐藏无关词条组 / Hide attribute groups irrelevant to the weapon type",
                    "开启（默认）：近战武器不显示装填/弹夹/爆头/瞄准等枪械词条，一把铁剑能少掉近十行。",
                    "关闭：全部词条一律显示。整合包里若有别的模组把这些词条另作他用，关掉它。");
            hideIrrelevantGroups = builder.define("hideIrrelevantGroups", true);

            builder.comment("").comment("─────────── 分组开关 / Attribute Groups ───────────");

            builder.comment("伤害增益组（近战/远程/箭矢/特攻/冲刺）/ Damage bonus group");
            showDamageGroup = builder.define("showDamageGroup", true);

            builder.comment("枪械专属组（装填/弹夹/后坐/爆头/瞄准等）/ Firearm group");
            showGunGroup = builder.define("showGunGroup", true);

            builder.comment("稀有效果组（处决阈值/抹除增益/秒杀概率）/ Rare effect group");
            showRareGroup = builder.define("showRareGroup", true);

            builder.comment(
                    "是否显示描述表不认识的属性 / Show attributes the panel catalog does not know",
                    "整合包通过 kuvalich-custom-modules.json 添加的自定义词条属于此类。",
                    "关闭后它们在面板上不显示，但在战斗结算里依然生效 —— 除非你确认不需要，否则别关。");
            showUnknownAttributes = builder.define("showUnknownAttributes", true);

            builder.comment("").comment("─────────── 击杀叠层 / Kill Stacks ───────────");

            builder.comment("是否显示击杀叠层区块 / Show the kill-stack section");
            showStacks = builder.define("showStacks", true);

            builder.comment(
                    "叠层进度的显示方式 / Kill-stack progress style",
                    "DOTS: ●●●○○ 3/5 （默认）",
                    "NUMERIC: 3/5 —— 自装字体包移除 unifont 回退导致 ●○ 变豆腐块时用这个");
            stackStyle = builder.defineEnum("stackStyle", StackStyle.DOTS);

            builder.comment(
                    "是否显示衰减倒计时 / Show the decay countdown",
                    "倒计时目前由客户端本地推算（同步包里还没有该字段），",
                    "服务端 TPS 偏低时会数得偏快，介意的话可以关掉。");
            showDecayTimer = builder.define("showDecayTimer", true);

            builder.comment(
                    "是否给分组加淡背景条 / Tint alternating attribute groups",
                    "相邻两组交替上一层极淡的底色，块与块之间一眼分得开。",
                    "本面板是自定义渲染组件才画得出来，原版的纯文本 tooltip 做不到。");
            showGroupBands = builder.define("showGroupBands", true);

            builder.comment("").comment("─────────── 区块开关 / Sections ───────────");

            builder.comment("是否显示元素伤害行 / Show the elemental damage line");
            showElements = builder.define("showElements", true);

            builder.comment("是否显示额外装备槽位加成 / Show extra equipment slot bonuses");
            showExtraSlots = builder.define("showExtraSlots", true);

            builder.comment("是否显示已装备的模组卡名单 / Show the installed module list");
            showModuleList = builder.define("showModuleList", true);

            builder.comment(
                    "词条被配置上限截断时是否提示 / Warn when an attribute is clamped by config",
                    "改造前是静默裁剪：面板显示 +300%、实际战斗按 +150% 结算，玩家无从得知。");
            showClampWarning = builder.define("showClampWarning", true);

            builder.pop();
        }
    }

    private TooltipConfig() {
    }
}
