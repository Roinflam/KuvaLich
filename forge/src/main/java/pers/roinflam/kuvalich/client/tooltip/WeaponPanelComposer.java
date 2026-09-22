package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.config.TooltipConfig;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.weapon.ExtraSlotTooltipHelper;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;
import pers.roinflam.kuvalich.module.weapon.WeaponElementSystem;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.module.weapon.panel.*;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 武器面板的编排器 —— 把面板数据变成一组 {@code Component} 行
 *
 * <p>四个视图（类似匠魂的分层 tooltip）：
 * <ul>
 *   <li><b>默认</b> {@link TooltipView#COMPACT}：分组紧凑，同组 chip 打包同行，无关组不生成</li>
 *   <li><b>SHIFT</b> {@link TooltipView#FULL}：逐条完整，与改造前排版一致</li>
 *   <li><b>CTRL</b> {@link TooltipView#SOURCE}：来源分解，每张卡贡献了什么、哪条被上限截断了</li>
 *   <li><b>ALT</b> {@link TooltipView#LIVE}：实时状态，叠层层数与衰减、元素组合链、触发几率分解</li>
 * </ul>
 * </p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class WeaponPanelComposer {

    // ==================== 入口 ====================

    /**
     * 面板产出：要么是一组文本行（SHIFT / CTRL / ALT 视图与无模组的基础面板），
     * 要么是一个结构化网格（默认视图，交给 {@link ClientPanelGridTooltip} 做像素对齐渲染）
     *
     * @param textLines 文本行
     * @param grid      结构化网格；null 表示本次没有网格
     */
    public record PanelResult(List<Component> textLines, @Nullable PanelGridTooltip grid) {

        static final PanelResult EMPTY = new PanelResult(List.of(), null);

        static PanelResult text(List<Component> lines) {
            return new PanelResult(lines, null);
        }

        static PanelResult grid(PanelGridTooltip grid) {
            return new PanelResult(List.of(), grid);
        }

        public boolean isEmpty() {
            return textLines.isEmpty() && (grid == null || grid.isEmpty());
        }
    }

    /**
     * 为一把武器生成面板内容
     *
     * @param stack  被查看的武器
     * @param viewer 查看者（可能为 null：JEI 配方页 / 创造栏）
     * @param view   当前视图
     * @return 面板内容；{@link PanelResult#isEmpty()} 表示这件物品不归本面板管
     */
    public static PanelResult compose(ItemStack stack, @Nullable Player viewer, TooltipView view) {
        List<Component> out = new ArrayList<>();

        if (!WeaponModuleHandler.hasBase(stack) || !TooltipConfig.PANEL.enabled.get()) {
            return PanelResult.EMPTY;
        }

        List<ItemStack> modules = WeaponModuleHandler.getModules(stack);
        if (modules.isEmpty()) {
            return PanelResult.grid(buildBaseGrid(stack));
        }

        // CLASSIC 密度：一律走逐条视图，作为与其它 tooltip 模组冲突时的总退路
        TooltipView effective = TooltipConfig.PANEL.density.get() == TooltipConfig.Density.CLASSIC
                && view == TooltipView.COMPACT ? TooltipView.FULL : view;

        HashMap<String, Double> attrs = WeaponModuleHandler.getWeaponAttributes(stack, modules);

        // ⭐ 额外槽位加成**无条件**合并进面板数值：showExtraSlots 只控制「明细区块显不显示」，
        //    不该让主面板的数字跟着少算。改造前也是这么分的
        //    （mergeExtraSlotIntoAttributes 无条件合并，appendExtraSlotTooltip 才是明细渲染）。
        HashMap<String, Double> extra = ExtraSlotTooltipHelper.getExtraSlotAttributes(viewer, stack);
        for (Map.Entry<String, Double> e : extra.entrySet()) {
            if (Math.abs(e.getValue()) >= 1.0e-3) {
                attrs.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }

        StackCounts stacks = TooltipConfig.PANEL.showStacks.get()
                ? LiveStackView.of(viewer)
                : StackCounts.NONE;

        // ⭐ 门控可关：整合包里若有别的模组把 reload_speed 这类词条另作他用，
        //    「近战武器不显示枪械词条」就不再成立。
        boolean applyGates = TooltipConfig.PANEL.hideIrrelevantGroups.get();

        // ⭐ 四个视图<b>全部</b>走同一套结构化网格：列宽按像素实测、标签左对齐数值右对齐、
        //    同一套 RGB 配色。改造中途只有默认视图换了新排版，三个功能键视图还留着
        //    老的纯文本 + ChatFormatting，按下 SHIFT 就像换了个模组 —— 那是个遗留状态，
        //    不是设计。
        return PanelResult.grid(switch (effective) {
            case FULL -> buildFullGrid(stack, modules, attrs, extra, stacks);
            case SOURCE -> buildSourceGrid(modules, extra);
            case LIVE -> buildLiveGrid(stack, modules, attrs, stacks);
            default -> buildGrid(stack, modules, attrs, extra, stacks, applyGates);
        });
    }

    // ==================== 默认视图：结构化网格 ====================

    /**
     * 构建默认视图的结构化网格
     *
     * <p>这里只负责「有哪些分组、每组有哪些单元格」，
     * 列数、列宽、对齐全部交给 {@link ClientPanelGridTooltip} 在渲染时按字体实测决定。</p>
     *
     * <p><b>第一版的教训</b>：当时是在这里就把词条流式打包成 {@code Component} 行，
     * 结果属性少的武器（比如只插一张卡的枪）也被挤成一行 ——
     * 名字被迫缩成两个字、tooltip 被撑得比周围都宽、还没有任何层次。
     * 「行数少」本身不是目标，好读才是；行数只在快超屏时才需要管。</p>
     */
    private static PanelGridTooltip buildGrid(ItemStack stack, List<ItemStack> modules,
                                                HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                                StackCounts stacks, boolean applyGates) {
        return buildGrid(stack, modules, attrs, extra, stacks, applyGates, true);
    }

    /**
     * @param useStackedValues true 显示「含当前叠层」的值（默认视图），
     *                         false 显示不含叠层的基础值（SHIFT 视图）
     */
    private static PanelGridTooltip buildGrid(ItemStack stack, List<ItemStack> modules,
                                                HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                                StackCounts stacks, boolean applyGates,
                                                boolean useStackedValues) {
        List<PanelGridTooltip.Section> sections = new ArrayList<>();
        int budget = ChipPacker.budget(TooltipConfig.PANEL.widthRatio.get());

        // Forma 锁定：独立一行，不参与任何分组与压缩
        if (WeaponModuleHandler.isFormaLocked(stack)) {
            sections.add(new PanelGridTooltip.Flow(null,
                    List.of(Component.translatable("item.kuvalich.forma_locked")
                            .withStyle(PanelPalette.bold(PanelPalette.LOCKED)))));
        }

        // ---- 成对词条的各组 ----
        EnumMap<PanelGroup, List<PanelGridTooltip.Cell>> byGroup = new EnumMap<>(PanelGroup.class);
        for (PanelChip chip : WeaponPanelData.collect(stack, attrs, stacks, applyGates)) {
            byGroup.computeIfAbsent(chip.spec().group(), g -> new ArrayList<>())
                    .add(pairCell(chip, useStackedValues));
        }

        // ⭐ 未登记的词条（整合包通过 JSON 自定义的）归入 OTHER 组，
        //    不兜底的话它们在面板上会静默消失，而战斗结算里又确实算数。
        if (TooltipConfig.PANEL.showUnknownAttributes.get()) {
            for (Map.Entry<String, Double> e : WeaponPanelData.collectUnknown(attrs).entrySet()) {
                byGroup.computeIfAbsent(PanelGroup.OTHER, g -> new ArrayList<>())
                        .add(new PanelGridTooltip.Cell(
                                label(PanelStyle.shortNameOf(e.getKey())),
                                value(ValueFormat.PERCENT_SIGNED.format(e.getValue()),
                                        e.getValue() >= 0 ? PanelPalette.LABEL : PanelPalette.PENALTY)));
            }
        }

        // ⭐ 分组顺序：核心面板 → 叠层 → 元素 → 其余增益。
        //    叠层与元素刻意排在伤害增益之前 —— 它们是「此刻生效的实时状态」，
        //    而且万一内容多到要截断，先丢掉的应该是静态词条明细而不是实时信息。
        addPairs(sections, byGroup, PanelGroup.PANEL);

        // ---- 击杀叠层：四列表格（名称 | 进度 | 当前加成 | 状态）----
        if (isGroupEnabled(PanelGroup.STACK)) {
            List<List<Component>> rows = stackTableRows(attrs, stacks);
            if (!rows.isEmpty()) {
                sections.add(new PanelGridTooltip.Table(header(PanelGroup.STACK), rows,
                        new PanelGridTooltip.Align[]{
                                PanelGridTooltip.Align.LEFT,
                                PanelGridTooltip.Align.LEFT,
                                PanelGridTooltip.Align.RIGHT,
                                PanelGridTooltip.Align.RIGHT}));
            }
        }

        // ---- 元素：与其它分组一样的对齐网格 ----
        if (isGroupEnabled(PanelGroup.ELEMENT)) {
            List<PanelGridTooltip.Cell> cells = elementCells(stack, modules, attrs);
            if (!cells.isEmpty()) {
                sections.add(new PanelGridTooltip.Pairs(header(PanelGroup.ELEMENT), cells));
            }
        }

        for (PanelGroup group : new PanelGroup[]{PanelGroup.DAMAGE,
                PanelGroup.GUN, PanelGroup.RARE, PanelGroup.OTHER}) {
            addPairs(sections, byGroup, group);
        }

        // ---- 额外装备槽位 ----
        if (TooltipConfig.PANEL.showExtraSlots.get() && !extra.isEmpty()) {
            List<PanelGridTooltip.Cell> cells = extraCells(extra);
            if (!cells.isEmpty()) {
                sections.add(new PanelGridTooltip.Pairs(header(PanelGroup.EXTRA), cells));
            }
        }

        // ---- 已装备模组 ----
        if (isGroupEnabled(PanelGroup.MODULES) && !modules.isEmpty()) {
            sections.add(new PanelGridTooltip.Columns(
                    headerWithCount(PanelGroup.MODULES, modules.size(), 8),
                    moduleNames(modules)));
        }

        return new PanelGridTooltip(sections);
    }

    // ==================== SHIFT：逐条完整 ====================

    /**
     * SHIFT 视图：与默认视图<b>完全一致</b>的排版、名字与条目，唯一区别是
     * 数值显示<b>不含击杀叠层</b>的基础面板。
     *
     * <p>⭐ 这里曾经还额外关掉门控（近战武器也显示枪械词条）。那让 SHIFT 比默认视图
     * 多出一堆条目，位置全对不上，玩家没法把两组数字并排比较 ——
     * 而「按住 SHIFT 看看这些数字里有多少是叠层撑起来的」正是这个视图的唯一用途。
     * 门控现在跟随同一个配置项，两个视图条目逐行对齐。</p>
     *
     * <p>⭐ 这里曾经额外做过一件事：把标签换成 {@code item.module.<key>} 那套「长名」。
     * 那是个错误设计 —— 同一条属性在默认视图叫「触发时长」、按下 SHIFT 变成「触发时间」，
     * 全项目有 17 条这样措辞不同的，玩家只会以为那是两条不同的属性。
     * 视图之间该变的是<b>看到多少</b>，不是<b>怎么称呼</b>。</p>
     */
    private static PanelGridTooltip buildFullGrid(ItemStack stack, List<ItemStack> modules,
                                                    HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                                    StackCounts stacks) {
        boolean applyGates = TooltipConfig.PANEL.hideIrrelevantGroups.get();
        return new PanelGridTooltip(
                buildGrid(stack, modules, attrs, extra, stacks, applyGates, false).sections());
    }

    // ==================== CTRL：词条来源 ====================

    /**
     * CTRL 视图：每张模组卡各贡献了什么、额外槽位来自哪里、哪条被配置上限截断了
     */
    private static PanelGridTooltip buildSourceGrid(List<ItemStack> modules,
                                                      HashMap<String, Double> extra) {
        List<PanelGridTooltip.Section> sections = new ArrayList<>();

        for (ItemStack module : modules) {
            List<PanelGridTooltip.Cell> cells = new ArrayList<>();
            double levelMult = ModuleLevelHelper.getEffectiveMultiplier(module);
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(module)) {
                double v = ModuleConfig.clampAttributeValue(e.getKey(), e.getValue()) * levelMult;
                if (Math.abs(v) < 1.0e-3) {
                    continue;
                }
                cells.add(new PanelGridTooltip.Cell(
                        label(PanelStyle.shortNameOf(e.getKey())),
                        value(sourceFormat(e.getKey()).format(v),
                                v >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY)));
            }
            if (cells.isEmpty()) {
                continue;
            }

            // 卡名保留它自己的品质颜色；等级跟在后面用次要色
            MutableComponent header = module.getHoverName().copy();
            if (ModuleLevelHelper.isLevelSystemEnabled()) {
                header.append(Component.literal(
                        " Lv." + ModuleLevelHelper.getModuleLevel(module) + "/" + ModuleLevelHelper.getMaxLevel())
                        .withStyle(PanelPalette.style(PanelPalette.MUTED)));
            }
            sections.add(new PanelGridTooltip.Pairs(header, cells));
        }

        if (!extra.isEmpty()) {
            List<PanelGridTooltip.Cell> cells = extraCells(extra);
            if (!cells.isEmpty()) {
                sections.add(new PanelGridTooltip.Pairs(header(PanelGroup.EXTRA), cells));
            }
        }

        // ⭐ 被配置上限截断的词条：改造前是静默裁剪，面板显示 +300%、实战按 +150% 结算，
        //    玩家会以为是 bug 或被偷偷削弱。这里把它显式说出来。
        if (TooltipConfig.PANEL.showClampWarning.get()) {
            List<Component> warnings = clampWarnings(modules);
            if (!warnings.isEmpty()) {
                sections.add(new PanelGridTooltip.Flow(
                        Component.translatable("kuvalich.panel.clamped.header")
                                .withStyle(PanelPalette.style(PanelPalette.WARN)),
                        warnings));
            }
        }

        if (sections.isEmpty()) {
            sections.add(new PanelGridTooltip.Flow(header(PanelGroup.OTHER),
                    List.of(Component.translatable("kuvalich.panel.source.none")
                            .withStyle(PanelPalette.style(PanelPalette.MUTED)))));
        }
        return new PanelGridTooltip(sections);
    }

    /** 来源视图一律用「加了多少」的口径，而不是「最终是多少」 */
    private static ValueFormat sourceFormat(String key) {
        AttributeSpec spec = WeaponPanelCatalog.byKey(key);
        if (spec == null) {
            return ValueFormat.PERCENT_SIGNED;
        }
        ValueFormat fmt = spec.fmt();
        if (fmt == ValueFormat.METERS_ABS) {
            return ValueFormat.METERS_DELTA;
        }
        if (fmt == ValueFormat.PERCENT || fmt == ValueFormat.MULTIPLIER) {
            return ValueFormat.PERCENT_SIGNED;
        }
        return fmt;
    }

    /** 找出「各卡之和」被 clampAttributeTotal 砍掉的词条 */
    private static List<Component> clampWarnings(List<ItemStack> modules) {
        HashMap<String, Double> raw = new HashMap<>();
        for (ItemStack module : modules) {
            double levelMult = ModuleLevelHelper.getEffectiveMultiplier(module);
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(module)) {
                raw.merge(e.getKey(),
                        ModuleConfig.clampAttributeValue(e.getKey(), e.getValue()) * levelMult, Double::sum);
            }
        }
        List<Component> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(raw.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            double before = raw.get(key);
            double after = ModuleConfig.clampAttributeTotal(key, before);
            if (Math.abs(before - after) < 1.0e-3) {
                continue;
            }
            out.add(Component.translatable("kuvalich.panel.clamped",
                            PanelStyle.shortNameOf(key),
                            ValueFormat.PERCENT_SIGNED.format(before),
                            ValueFormat.PERCENT_SIGNED.format(after))
                    .withStyle(PanelPalette.style(PanelPalette.WARN)));
        }
        return out;
    }

    // ==================== ALT：实时状态 ====================

    /**
     * ALT 视图：此刻真正生效的东西 —— 叠层详情、元素怎么合成的、触发几率怎么算出来的
     */
    private static PanelGridTooltip buildLiveGrid(ItemStack stack, List<ItemStack> modules,
                                                    HashMap<String, Double> attrs, StackCounts stacks) {
        List<PanelGridTooltip.Section> sections = new ArrayList<>();
        int budget = ChipPacker.budget(TooltipConfig.PANEL.widthRatio.get());

        // ---- 叠层详情：比默认视图多一列「每层多少」----
        if (isGroupEnabled(PanelGroup.STACK)) {
            List<StackRow> rows = WeaponPanelData.collectStacks(attrs, stacks);
            if (rows.isEmpty()) {
                sections.add(new PanelGridTooltip.Flow(header(PanelGroup.STACK),
                        List.of(Component.translatable("kuvalich.panel.stack.none")
                                .withStyle(PanelPalette.style(PanelPalette.MUTED)))));
            } else {
                List<List<Component>> table = new ArrayList<>(rows.size());
                for (int i = 0; i < rows.size(); i++) {
                    List<Component> row = new ArrayList<>(stackTableRows(attrs, stacks).get(i));
                    row.add(2, Component.translatable("kuvalich.panel.stack.per",
                                    ValueFormat.PERCENT_SIGNED.format(rows.get(i).perStack()))
                            .withStyle(PanelPalette.style(PanelPalette.MUTED)));
                    table.add(row);
                }
                sections.add(new PanelGridTooltip.Table(header(PanelGroup.STACK), table,
                        new PanelGridTooltip.Align[]{
                                PanelGridTooltip.Align.LEFT,
                                PanelGridTooltip.Align.LEFT,
                                PanelGridTooltip.Align.LEFT,
                                PanelGridTooltip.Align.RIGHT,
                                PanelGridTooltip.Align.RIGHT}));
            }
        }

        // ---- 元素：投入 → 成池，看清哪两个基础元素合成了什么 ----
        if (isGroupEnabled(PanelGroup.ELEMENT)) {
            List<Component> input = new ArrayList<>();
            for (String key : WeaponPanelCatalog.ELEMENTS) {
                double v = attrs.getOrDefault(key, 0.0);
                if (Math.abs(v) >= 1.0e-3) {
                    input.add(Component.literal(I18n.get("kuvaweapon.type." + key)
                                    + " " + ValueFormat.PERCENT_SIGNED.format(v))
                            .withStyle(PanelPalette.style(PanelPalette.element(key))));
                }
            }
            HashMap<String, String> pool = WeaponElementSystem.getTriggerElements(stack, modules);
            List<Component> poolChips = new ArrayList<>(pool.size());
            for (Map.Entry<String, String> e : pool.entrySet()) {
                poolChips.add(Component.literal(I18n.get("kuvaweapon.type." + e.getKey()) + " " + e.getValue())
                        .withStyle(PanelPalette.bold(PanelPalette.element(e.getKey()))));
            }

            List<Component> lines = new ArrayList<>();
            if (!input.isEmpty()) {
                lines.addAll(prefixed("kuvalich.panel.element.input", input, budget));
            }
            if (!poolChips.isEmpty()) {
                lines.addAll(prefixed("kuvalich.panel.element.pool", poolChips, budget));
            }
            if (!lines.isEmpty()) {
                sections.add(new PanelGridTooltip.Flow(header(PanelGroup.ELEMENT), lines));
            }
        }

        // ---- 触发几率分解 ----
        sections.add(new PanelGridTooltip.Flow(
                Component.translatable("item.module.triggerChance")
                        .withStyle(PanelPalette.style(PanelPalette.header(PanelGroup.PANEL))),
                triggerBreakdown(stack, attrs, stacks)));

        return new PanelGridTooltip(sections);
    }

    /** 在一组 chip 前加一个小标题，然后按宽度打包 */
    private static List<Component> prefixed(String headerKey, List<Component> chips, int budget) {
        Component prefix = Component.translatable(headerKey)
                .withStyle(PanelPalette.style(PanelPalette.MUTED))
                .copy().append(Component.literal("  "));
        return ChipPacker.pack(chips, prefix, Component.literal("   "), budget);
    }

    /** 触发几率的完整算式 */
    private static List<Component> triggerBreakdown(ItemStack stack, HashMap<String, Double> attrs,
                                                    StackCounts stacks) {
        double baseTrigger = WeaponModuleHandler.getBaseAttribute(stack, "triggerChance");
        double modTrigger = attrs.getOrDefault("triggerChance", 0.0);
        int stackN = stacks.available() ? stacks.stacks(StackType.TRIGGER_CHANCE) : 0;
        double stackTrigger = attrs.getOrDefault("killStackTriggerChance", 0.0) * stackN;
        double uncapped = baseTrigger * (1 + modTrigger) * (1 + stackTrigger);

        // ⭐ 战斗端在所有乘算做完后还有一道硬上限。本视图的卖点就是「这个数就是实战的数」，
        //    不钳就会出现面板 700% / 实战 500%。
        double cap = WeaponCombatHandler.MAX_ELEMENT_TRIGGER_PER_HIT;
        double finalTrigger = Math.min(uncapped, cap);

        List<Component> out = new ArrayList<>(3);
        MutableComponent line = Component.translatable("kuvalich.panel.trigger.breakdown",
                        ValueFormat.PERCENT.format(baseTrigger),
                        ValueFormat.PERCENT_SIGNED.format(modTrigger),
                        ValueFormat.PERCENT_SIGNED.format(stackTrigger),
                        ValueFormat.PERCENT.format(finalTrigger))
                .withStyle(PanelPalette.style(PanelPalette.MUTED));
        if (uncapped > cap + 1.0e-6) {
            line.append(Component.literal(" "))
                    .append(Component.translatable("kuvalich.panel.trigger.capped")
                            .withStyle(PanelPalette.style(PanelPalette.WARN)));
        }
        out.add(line);

        // 冲刺攻击有额外的触发加成（战斗端在冲刺分支里多乘一个 dashTriggerChance）
        double dash = attrs.getOrDefault("dashTriggerChance", 0.0);
        if (Math.abs(dash) >= 1.0e-3) {
            double dashFinal = Math.min(baseTrigger * (1 + modTrigger + dash) * (1 + stackTrigger), cap);
            out.add(Component.translatable("kuvalich.panel.trigger.dash",
                            ValueFormat.PERCENT_SIGNED.format(dash),
                            ValueFormat.PERCENT.format(dashFinal))
                    .withStyle(PanelPalette.style(PanelPalette.MUTED)));
        }
        return out;
    }

    // ==================== 网格的各种单元格 ====================

    private static void addPairs(List<PanelGridTooltip.Section> sections,
                                 EnumMap<PanelGroup, List<PanelGridTooltip.Cell>> byGroup,
                                 PanelGroup group) {
        List<PanelGridTooltip.Cell> cells = byGroup.get(group);
        if (cells == null || cells.isEmpty() || !isGroupEnabled(group)) {
            return;
        }
        sections.add(new PanelGridTooltip.Pairs(header(group), cells));
    }


    /**
     * 分组标题：独立成行，内容缩进在它下面 —— 层次靠位置和颜色，不靠符号
     *
     * <p>每组有自己的色相（见 {@link PanelPalette#header}），扫视时不用读字
     * 就知道翻到哪一组了。统一比数值暗一档，但明显比原版 {@code DARK_GRAY} 亮 ——
     * 后者在深色背景上基本读不出来。</p>
     */
    private static Component header(PanelGroup group) {
        MutableComponent c = Component.empty();
        if (TooltipConfig.PANEL.showGutter.get()) {
            c.append(Component.literal(PanelStyle.gutter())
                    .withStyle(PanelPalette.style(PanelPalette.header(group))));
        }
        c.append(Component.translatable(group.titleKey())
                .withStyle(PanelPalette.style(PanelPalette.header(group))));
        return c;
    }

    /** 带计数的分组标题，如「已装备模组 8/8」 */
    private static Component headerWithCount(PanelGroup group, int current, int max) {
        return header(group).copy()
                .append(Component.literal(" " + current + "/" + max)
                        .withStyle(PanelPalette.style(PanelPalette.MUTED)));
    }

    private static Component label(String text) {
        return Component.literal(text).withStyle(PanelPalette.style(PanelPalette.LABEL));
    }

    private static Component value(String text, int rgb) {
        return Component.literal(text).withStyle(PanelPalette.bold(rgb));
    }

    /**
     * 一个「标签 + 数值」单元格
     *
     * <p>成对词条（近战 / 远程暴击）的两个值写在同一个数值格里；
     * 叠层箭头紧跟在<b>主值</b>后面，因为叠层只抬高近战那一侧。</p>
     */
    /**
     * 一个「标签 + 数值」单元格
     *
     * <p><b>默认视图给的是「含当前叠层」的值</b>，也就是「我现在有多强」；
     * 按住 SHIFT 给的是不含叠层的基础值，也就是「这把武器的底子」。
     * 两个视图分工明确，面板上不再出现一个需要额外解释的箭头。</p>
     *
     * <p>被叠层抬高的那几条用青色标出来 —— 否则玩家看不出数字里哪些是临时的。
     * 具体抬高了多少，下面的「击杀叠层」一组里逐条写着。</p>
     *
     * @param useStackedValues 取含叠层的值还是基础值
     */
    private static PanelGridTooltip.Cell pairCell(PanelChip chip, boolean useStackedValues) {
        AttributeSpec spec = chip.spec();
        boolean boosted = useStackedValues && chip.hasStackBonus();

        int color = chip.isNegative() ? PanelPalette.PENALTY
                : boosted ? PanelPalette.STACKED
                : PanelPalette.value(spec.group());

        String text = useStackedValues ? chip.stackedText() : chip.baseText();
        MutableComponent v = Component.literal(text).withStyle(PanelPalette.bold(color));

        // 成对词条的第二个值（远程侧）不吃叠层 —— killStackMeleeCriticalMultiplier
        // 在战斗端只在近战分支生效，所以这一侧永远是基础值
        if (spec.isPaired() && chip.secondaryText() != null) {
            v.append(Component.literal(" / ").withStyle(PanelPalette.style(PanelPalette.FAINT)));
            v.append(Component.literal(chip.secondaryText())
                    .withStyle(PanelPalette.bold(chip.isNegative()
                            ? PanelPalette.PENALTY : PanelPalette.value(spec.group()))));
            // ⭐ 「x4.5 / x2.8」比同组的「113%」宽两三倍，挤进列里会把整列撑开，
            //    害得旁边的词条标签与数值之间拉出一大段空白。让它整行独占反而齐整
            return new PanelGridTooltip.Cell(label(PanelStyle.shortNameOf(spec.key())), v, true);
        }

        return new PanelGridTooltip.Cell(label(PanelStyle.shortNameOf(spec.key())), v);
    }

    /**
     * 元素：每种元素一个「名称 + 占比」单元格，末尾跟一个总伤害
     *
     * <p>原先是把元素名和百分比拼成一串流式排开，末尾还硬接一个「元素伤害 +150%」——
     * 后者是<b>总量</b>，跟前面那些<b>占比</b>不是一回事，混在一行里容易读串。
     * 现在走和其它分组一样的对齐网格，列数自适应，总量单独成一格。</p>
     */
    private static List<PanelGridTooltip.Cell> elementCells(ItemStack stack, List<ItemStack> modules,
                                                              HashMap<String, Double> attrs) {
        HashMap<String, String> pool = WeaponElementSystem.getTriggerElements(stack, modules);

        double total = 0;
        for (String key : WeaponPanelCatalog.ELEMENTS) {
            total += attrs.getOrDefault(key, 0.0);
        }
        if (KuvaWeaponUtil.hasType(stack)) {
            total += WeaponElementSystem.getKuvaWeaponElementDamage(stack);
        }

        if (pool.isEmpty() && Math.abs(total) < 1.0e-3) {
            return List.of();
        }

        List<PanelGridTooltip.Cell> cells = new ArrayList<>(pool.size() + 1);
        for (Map.Entry<String, String> e : pool.entrySet()) {
            // ⭐ 元素色走自己的 RGB 表：原版的 DARK_RED / DARK_GREEN / DARK_GRAY
            //    在深色背景上读不出来，而元素是靠颜色认的
            int rgb = PanelPalette.element(e.getKey());
            cells.add(new PanelGridTooltip.Cell(
                    Component.literal(I18n.get("kuvaweapon.type." + e.getKey())).withStyle(PanelPalette.style(rgb)),
                    Component.literal(e.getValue()).withStyle(PanelPalette.bold(rgb))));
        }
        if (Math.abs(total) >= 1.0e-3) {
            // ⭐ 独占一行：它是元素<b>总量</b>，与上面那些<b>占比</b>不是一回事，
            //    而且「元素伤害」这个标签比「冲击」「病毒」长一倍，
            //    混进同一列会把列宽撑开、把短元素的标签与数值拉散
            cells.add(new PanelGridTooltip.Cell(
                    label(I18n.get("item.module.triggerDamage")),
                    value((total >= 0 ? "+" : "") + Math.round(total * 100) + "%",
                            PanelPalette.value(PanelGroup.ELEMENT)),
                    true));
        }

        return cells;
    }

    /** 叠层表格的行：名称 | 进度 | 当前加成 | 状态 */
    private static List<List<Component>> stackTableRows(HashMap<String, Double> attrs, StackCounts stacks) {
        List<List<Component>> rows = new ArrayList<>();
        for (StackRow row : WeaponPanelData.collectStacks(attrs, stacks)) {
            Component name = label(PanelStyle.shortNameOf(row.key()));

            if (!row.available()) {
                // ⭐「不知道」与「确实是 0 层」必须可区分，否则玩家会把没数据误读成机制坏了
                rows.add(List.of(name,
                        Component.literal("?????").withStyle(PanelPalette.style(PanelPalette.FAINT)),
                        Component.literal("\u2014/" + row.max()).withStyle(PanelPalette.style(PanelPalette.MUTED)),
                        Component.translatable("kuvalich.panel.stack.unsynced")
                                .withStyle(PanelPalette.style(PanelPalette.MUTED))));
                continue;
            }

            Component progress = PanelStyle.progress(row.current(), row.max());

            Component bonus;
            if (row.isIdle()) {
                bonus = Component.translatable("kuvalich.panel.stack.per",
                        ValueFormat.PERCENT_SIGNED.format(row.perStack()))
                        .withStyle(PanelPalette.style(PanelPalette.MUTED));
            } else if (row.dependsOnTarget()) {
                // ⭐ killStackBaseDamage 的最终加成 = 每层 × 层数 × 目标身上的负面效果数，
                //    tooltip 时没有目标，所以只给「每负面效果」的口径，不编一个总百分比
                bonus = Component.literal(ValueFormat.PERCENT_SIGNED.format(row.total()))
                        .withStyle(PanelPalette.bold(PanelPalette.STACK_ACTIVE))
                        .append(Component.literal("/").withStyle(PanelPalette.style(PanelPalette.FAINT)))
                        .append(Component.translatable("kuvalich.panel.stack.per_debuff")
                                .withStyle(PanelPalette.style(PanelPalette.MUTED)));
            } else {
                bonus = Component.literal(ValueFormat.PERCENT_SIGNED.format(row.total()))
                        .withStyle(PanelPalette.bold(PanelPalette.STACK_ACTIVE));
            }

            Component state;
            if (row.isIdle()) {
                state = Component.translatable("kuvalich.panel.stack.idle")
                        .withStyle(PanelPalette.style(PanelPalette.FAINT));
            } else if (row.isFull()) {
                // 满层时不显示秒数：满层继续击杀会刷新计时器但不改变层数，服务端此时不发包，
                // 客户端的本地推算会一路数到 0 —— 显示「满层」永远不会错
                state = Component.translatable("kuvalich.panel.stack.full")
                        .withStyle(PanelPalette.bold(PanelPalette.STACK_ACTIVE));
            } else if (TooltipConfig.PANEL.showDecayTimer.get() && row.decayTicks() > 0) {
                double sec = row.decayTicks() / 20.0;
                state = sec < 1.0
                        ? Component.translatable("kuvalich.panel.stack.decaying")
                                .withStyle(PanelPalette.style(PanelPalette.WARN))
                        : Component.literal(String.format(Locale.ROOT, "%.1fs", sec))
                                .withStyle(PanelPalette.style(PanelPalette.MUTED));
            } else {
                state = Component.empty();
            }

            rows.add(List.of(name, progress, bonus, state));
        }
        return rows;
    }

    /** 额外装备槽位的单元格 */
    private static List<PanelGridTooltip.Cell> extraCells(HashMap<String, Double> extra) {
        List<PanelGridTooltip.Cell> cells = new ArrayList<>();
        double elementSum = 0;

        List<String> keys = new ArrayList<>(extra.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            double v = extra.getOrDefault(key, 0.0);
            if (WeaponPanelCatalog.isElement(key)) {
                elementSum += v;
                continue;
            }
            if (WeaponPanelCatalog.KILL_STACK_SPECS.containsKey(key)) {
                continue;
            }

            AttributeSpec spec = WeaponPanelCatalog.byKey(key);
            // 额外槽位用「增量」口径：爆炸半径是「加了多少米」而不是「最终多少米」
            ValueFormat fmt = ValueFormat.PERCENT_SIGNED;
            if (spec != null) {
                fmt = spec.fmt() == ValueFormat.METERS_ABS ? ValueFormat.METERS_DELTA : spec.fmt();
                if (fmt == ValueFormat.PERCENT || fmt == ValueFormat.MULTIPLIER) {
                    fmt = ValueFormat.PERCENT_SIGNED;
                }
            }
            if (Math.abs(v) < fmt.epsilon()) {
                continue;
            }
            cells.add(new PanelGridTooltip.Cell(label(PanelStyle.shortNameOf(key)),
                    value(fmt.format(v), v >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY)));
        }

        if (Math.abs(elementSum) >= 1.0e-3) {
            cells.add(new PanelGridTooltip.Cell(label(I18n.get("item.module.triggerDamage")),
                    value(ValueFormat.PERCENT_SIGNED.format(elementSum),
                            elementSum >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY)));
        }

        // 叠层词条在额外槽位里用「每层」口径，避免与主面板的「当前总加成」混淆
        for (String key : WeaponPanelCatalog.KILL_STACK_SPECS.keySet()) {
            Double v = extra.get(key);
            if (v == null || Math.abs(v) < 1.0e-3) {
                continue;
            }
            cells.add(new PanelGridTooltip.Cell(label(PanelStyle.shortNameOf(key)),
                    Component.translatable("kuvalich.panel.stack.per", ValueFormat.PERCENT_SIGNED.format(v))
                            .withStyle(PanelPalette.style(v >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY))));
        }

        return cells;
    }

    /**
     * 模组名单：排成对齐的几列
     *
     * <p>原先是流式打包 + 名字之间加「·」，结果行尾会挂一个孤零零的分隔符，
     * 名字的左边界也参差不齐，跟面板其余部分那套列对齐完全不是一路。
     * 现在交给 {@link PanelGridTooltip.Columns} 排列，分隔符也不需要了 ——
     * 卡名本来就各有品质颜色，靠颜色和列位就分得开。</p>
     */
    private static List<Component> moduleNames(List<ItemStack> modules) {
        List<Component> names = new ArrayList<>(modules.size());
        for (ItemStack module : modules) {
            // ⭐ 不覆盖模组卡自己的品质颜色（铜 / 银 / 金 / Prime / 裂罅），
            //    那是玩家一眼认卡的依据；只补一个默认色兜底没有样式的名字
            MutableComponent name = module.getHoverName().copy();
            if (name.getStyle().getColor() == null) {
                name.withStyle(PanelPalette.style(PanelPalette.LABEL));
            }
            names.add(name);
        }
        return names;
    }

    /**
     * 分组是否启用（给整合包作者的开关）
     *
     * <p>PANEL 组是武器的身份信息，不提供关闭 —— 要整体关请用 {@code enabled}。</p>
     */
    private static boolean isGroupEnabled(PanelGroup group) {
        return switch (group) {
            case DAMAGE -> TooltipConfig.PANEL.showDamageGroup.get();
            case GUN -> TooltipConfig.PANEL.showGunGroup.get();
            case RARE -> TooltipConfig.PANEL.showRareGroup.get();
            case ELEMENT -> TooltipConfig.PANEL.showElements.get();
            case STACK -> TooltipConfig.PANEL.showStacks.get();
            case EXTRA -> TooltipConfig.PANEL.showExtraSlots.get();
            case MODULES -> TooltipConfig.PANEL.showModuleList.get();
            case OTHER -> TooltipConfig.PANEL.showUnknownAttributes.get();
            default -> true;
        };
    }

    // ==================== 无模组时的基础面板 ====================

    /**
     * 还没装模组的武器：只有四条基础数值
     *
     * <p>也走网格，与装了模组之后的面板是同一套排版和配色 ——
     * 玩家插上第一张卡时不应该觉得换了个界面。</p>
     */
    private static PanelGridTooltip buildBaseGrid(ItemStack stack) {
        List<PanelGridTooltip.Section> sections = new ArrayList<>();

        if (WeaponModuleHandler.isFormaLocked(stack)) {
            sections.add(new PanelGridTooltip.Flow(null,
                    List.of(Component.translatable("item.kuvalich.forma_locked")
                            .withStyle(PanelPalette.bold(PanelPalette.LOCKED)))));
        }

        sections.add(new PanelGridTooltip.Pairs(header(PanelGroup.PANEL), List.of(
                baseCell(stack, "damage", ValueFormat.PERCENT),
                baseCell(stack, "criticalStrikeProbability", ValueFormat.PERCENT),
                baseCell(stack, "criticalStrikeMultiplier", ValueFormat.MULTIPLIER),
                baseCell(stack, "triggerChance", ValueFormat.PERCENT))));

        return new PanelGridTooltip(sections);
    }

    private static PanelGridTooltip.Cell baseCell(ItemStack stack, String attr, ValueFormat fmt) {
        return new PanelGridTooltip.Cell(
                label(PanelStyle.shortNameOf(attr)),
                value(fmt.format(WeaponModuleHandler.getBaseAttribute(stack, attr)), PanelPalette.VALUE));
    }

    private WeaponPanelComposer() {
    }
}
