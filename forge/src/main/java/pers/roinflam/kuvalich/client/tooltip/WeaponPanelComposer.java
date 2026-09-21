package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.ChatFormatting;
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
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

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
     * 要么是一个结构化网格（默认视图，交给 {@link ClientPanelGrid} 做像素对齐渲染）
     *
     * @param textLines 文本行
     * @param grid      结构化网格；null 表示本次没有网格
     */
    public record PanelResult(List<Component> textLines, @Nullable PanelGridComponent grid) {

        static final PanelResult EMPTY = new PanelResult(List.of(), null);

        static PanelResult text(List<Component> lines) {
            return new PanelResult(lines, null);
        }

        static PanelResult grid(PanelGridComponent grid) {
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

        // Forma 锁定：永不参与 chip 压缩，固定排在最前的独立行
        if (WeaponModuleHandler.isFormaLocked(stack)) {
            out.add(Component.translatable("item.kuvalich.forma_locked").withStyle(ChatFormatting.DARK_RED));
        }

        List<ItemStack> modules = WeaponModuleHandler.getModules(stack);
        if (modules.isEmpty()) {
            appendBasePanel(stack, out);
            // ⭐ 没装模组的武器正是新手拿到的第一把，也是最需要这行提示的人群 ——
            //    不给入口的话，整套四视图对他们完全不可见。
            if (TooltipConfig.PANEL.showKeyHint.get()) {
                out.add(hintLine(view));
            }
            return PanelResult.text(out);
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

        // ⭐ 默认视图走结构化网格：列宽按像素实测、标签左对齐数值右对齐，
        //    列数由内容多少自适应（属性少就一条一行，多到快超屏才切多列）。
        if (effective == TooltipView.COMPACT) {
            return PanelResult.grid(buildGrid(stack, modules, attrs, extra, stacks, applyGates, view));
        }

        switch (effective) {
            case FULL -> appendFull(stack, modules, attrs, extra, stacks, applyGates, out);
            case SOURCE -> appendSource(stack, modules, attrs, extra, out);
            default -> appendLive(stack, modules, attrs, stacks, out);
        }

        if (TooltipConfig.PANEL.showKeyHint.get()) {
            out.add(hintLine(view));
        }
        return PanelResult.text(out);
    }

    // ==================== 默认视图：结构化网格 ====================

    /**
     * 构建默认视图的结构化网格
     *
     * <p>这里只负责「有哪些分组、每组有哪些单元格」，
     * 列数、列宽、对齐全部交给 {@link ClientPanelGrid} 在渲染时按字体实测决定。</p>
     *
     * <p><b>第一版的教训</b>：当时是在这里就把词条流式打包成 {@code Component} 行，
     * 结果属性少的武器（比如只插一张卡的枪）也被挤成一行 ——
     * 名字被迫缩成两个字、tooltip 被撑得比周围都宽、还没有任何层次。
     * 「行数少」本身不是目标，好读才是；行数只在快超屏时才需要管。</p>
     */
    private static PanelGridComponent buildGrid(ItemStack stack, List<ItemStack> modules,
                                                HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                                StackCounts stacks, boolean applyGates, TooltipView view) {
        List<PanelGridComponent.Section> sections = new ArrayList<>();
        int budget = ChipPacker.budget(TooltipConfig.PANEL.widthRatio.get());

        // Forma 锁定：独立一行，不参与任何分组与压缩
        if (WeaponModuleHandler.isFormaLocked(stack)) {
            sections.add(new PanelGridComponent.Flow(null,
                    List.of(Component.translatable("item.kuvalich.forma_locked").withStyle(ChatFormatting.DARK_RED))));
        }

        // ---- 成对词条的各组 ----
        EnumMap<PanelGroup, List<PanelGridComponent.Cell>> byGroup = new EnumMap<>(PanelGroup.class);
        for (PanelChip chip : WeaponPanelData.collect(stack, attrs, stacks, applyGates)) {
            byGroup.computeIfAbsent(chip.spec().group(), g -> new ArrayList<>()).add(pairCell(chip));
        }

        // ⭐ 未登记的词条（整合包通过 JSON 自定义的）归入 OTHER 组，
        //    不兜底的话它们在面板上会静默消失，而战斗结算里又确实算数。
        if (TooltipConfig.PANEL.showUnknownAttributes.get()) {
            for (Map.Entry<String, Double> e : WeaponPanelData.collectUnknown(attrs).entrySet()) {
                byGroup.computeIfAbsent(PanelGroup.OTHER, g -> new ArrayList<>())
                        .add(new PanelGridComponent.Cell(
                                label(PanelStyle.shortNameOf(e.getKey())),
                                value(ValueFmt.PERCENT_SIGNED.format(e.getValue()),
                                        e.getValue() >= 0 ? ChatFormatting.GRAY : ChatFormatting.RED)));
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
                sections.add(new PanelGridComponent.Table(header(PanelGroup.STACK), rows,
                        new PanelGridComponent.Align[]{
                                PanelGridComponent.Align.LEFT,
                                PanelGridComponent.Align.LEFT,
                                PanelGridComponent.Align.RIGHT,
                                PanelGridComponent.Align.RIGHT}));
            }
        }

        // ---- 元素：名称与百分比打包成一两行 ----
        if (isGroupEnabled(PanelGroup.ELEMENT)) {
            List<Component> elementLines = elementLines(stack, modules, attrs, budget);
            if (!elementLines.isEmpty()) {
                sections.add(new PanelGridComponent.Flow(header(PanelGroup.ELEMENT), elementLines));
            }
        }

        for (PanelGroup group : new PanelGroup[]{PanelGroup.DAMAGE,
                PanelGroup.GUN, PanelGroup.RARE, PanelGroup.OTHER}) {
            addPairs(sections, byGroup, group);
        }

        // ---- 额外装备槽位 ----
        if (TooltipConfig.PANEL.showExtraSlots.get() && !extra.isEmpty()) {
            List<PanelGridComponent.Cell> cells = extraCells(extra);
            if (!cells.isEmpty()) {
                sections.add(new PanelGridComponent.Pairs(header(PanelGroup.EXTRA), cells));
            }
        }

        // ---- 已装备模组 ----
        if (isGroupEnabled(PanelGroup.MODULES) && !modules.isEmpty()) {
            sections.add(new PanelGridComponent.Flow(
                    headerWithCount(PanelGroup.MODULES, modules.size(), 8),
                    moduleNameLines(modules, budget)));
        }

        // ---- 按键提示 ----
        if (TooltipConfig.PANEL.showKeyHint.get()) {
            sections.add(new PanelGridComponent.Flow(null, List.of(hintLine(view))));
        }

        return new PanelGridComponent(sections);
    }

    // ==================== 网格的各种单元格 ====================

    private static void addPairs(List<PanelGridComponent.Section> sections,
                                 EnumMap<PanelGroup, List<PanelGridComponent.Cell>> byGroup,
                                 PanelGroup group) {
        List<PanelGridComponent.Cell> cells = byGroup.get(group);
        if (cells == null || cells.isEmpty() || !isGroupEnabled(group)) {
            return;
        }
        sections.add(new PanelGridComponent.Pairs(header(group), cells));
    }


    /** 分组标题：暗灰色小标题独立成行，内容缩进在它下面 —— 层次靠位置和颜色，不靠符号 */
    private static Component header(PanelGroup group) {
        MutableComponent c = Component.empty();
        if (TooltipConfig.PANEL.showGutter.get()) {
            c.append(Component.literal(PanelStyle.gutter()).withStyle(group.gutterColor()));
        }
        c.append(Component.translatable(group.titleKey()).withStyle(ChatFormatting.DARK_GRAY));
        return c;
    }

    /** 带计数的分组标题，如「已装备模组 8/8」 */
    private static Component headerWithCount(PanelGroup group, int current, int max) {
        return header(group).copy()
                .append(Component.literal(" " + current + "/" + max).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color, ChatFormatting.BOLD);
    }

    /**
     * 一个「标签 + 数值」单元格
     *
     * <p>成对词条（近战 / 远程暴击）的两个值写在同一个数值格里；
     * 叠层箭头紧跟在<b>主值</b>后面，因为叠层只抬高近战那一侧。</p>
     */
    private static PanelGridComponent.Cell pairCell(PanelChip chip) {
        AttrSpec spec = chip.spec();
        ChatFormatting color = PanelStyle.valueColor(spec.group(), chip.isNegative());

        MutableComponent v = Component.literal(chip.baseText()).withStyle(color, ChatFormatting.BOLD);

        if (TooltipConfig.PANEL.showStackArrow.get() && chip.hasStackBonus()) {
            v.append(Component.literal(PanelStyle.arrow()).withStyle(ChatFormatting.DARK_GRAY));
            v.append(Component.literal(chip.stackedText()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }

        if (spec.isPaired() && chip.secondaryText() != null) {
            v.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY));
            v.append(Component.literal(chip.secondaryText()).withStyle(color, ChatFormatting.BOLD));
        }

        return new PanelGridComponent.Cell(label(PanelStyle.shortNameOf(spec.key())), v);
    }

    /** 元素行：彩色名称 + 百分比，末尾跟一个总量 */
    private static List<Component> elementLines(ItemStack stack, List<ItemStack> modules,
                                                HashMap<String, Double> attrs, int budget) {
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

        List<Component> chips = new ArrayList<>(pool.size() + 1);
        for (Map.Entry<String, String> e : pool.entrySet()) {
            chips.add(Component.literal(I18n.get("kuvaweapon.type." + e.getKey()) + " " + e.getValue())
                    .withStyle(KuvaWeaponUtil.getColor(e.getKey()), ChatFormatting.BOLD));
        }
        if (Math.abs(total) >= 1.0e-3) {
            chips.add(Component.literal(I18n.get("item.module.triggerDamage") + " "
                    + (total >= 0 ? "+" : "") + Math.round(total * 100) + "%")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        return ChipPacker.pack(chips, Component.empty(), Component.empty(), budget);
    }

    /** 叠层表格的行：名称 | 进度 | 当前加成 | 状态 */
    private static List<List<Component>> stackTableRows(HashMap<String, Double> attrs, StackCounts stacks) {
        List<List<Component>> rows = new ArrayList<>();
        for (StackRow row : WeaponPanelData.collectStacks(attrs, stacks)) {
            Component name = label(PanelStyle.shortNameOf(row.key()));

            if (!row.available()) {
                // ⭐「不知道」与「确实是 0 层」必须可区分，否则玩家会把没数据误读成机制坏了
                rows.add(List.of(name,
                        Component.literal("?????").withStyle(ChatFormatting.DARK_GRAY),
                        Component.literal("\u2014/" + row.max()).withStyle(ChatFormatting.DARK_GRAY),
                        Component.translatable("kuvalich.panel.stack.unsynced").withStyle(ChatFormatting.DARK_GRAY)));
                continue;
            }

            Component progress = PanelStyle.progress(row.current(), row.max());

            Component bonus;
            if (row.isIdle()) {
                bonus = Component.translatable("kuvalich.panel.stack.per",
                        ValueFmt.PERCENT_SIGNED.format(row.perStack())).withStyle(ChatFormatting.DARK_GRAY);
            } else if (row.dependsOnTarget()) {
                // ⭐ killStackBaseDamage 的最终加成 = 每层 × 层数 × 目标身上的负面效果数，
                //    tooltip 时没有目标，所以只给「每负面效果」的口径，不编一个总百分比
                bonus = Component.literal(ValueFmt.PERCENT_SIGNED.format(row.total()))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("kuvalich.panel.stack.per_debuff")
                                .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                bonus = Component.literal(ValueFmt.PERCENT_SIGNED.format(row.total()))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }

            Component state;
            if (row.isIdle()) {
                state = Component.translatable("kuvalich.panel.stack.idle").withStyle(ChatFormatting.DARK_GRAY);
            } else if (row.isFull()) {
                // 满层时不显示秒数：满层继续击杀会刷新计时器但不改变层数，服务端此时不发包，
                // 客户端的本地推算会一路数到 0 —— 显示「满层」永远不会错
                state = Component.translatable("kuvalich.panel.stack.full")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            } else if (TooltipConfig.PANEL.showDecayTimer.get() && row.decayTicks() > 0) {
                double sec = row.decayTicks() / 20.0;
                state = sec < 1.0
                        ? Component.translatable("kuvalich.panel.stack.decaying").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(String.format(Locale.ROOT, "%.1fs", sec)).withStyle(ChatFormatting.DARK_GRAY);
            } else {
                state = Component.empty();
            }

            rows.add(List.of(name, progress, bonus, state));
        }
        return rows;
    }

    /** 额外装备槽位的单元格 */
    private static List<PanelGridComponent.Cell> extraCells(HashMap<String, Double> extra) {
        List<PanelGridComponent.Cell> cells = new ArrayList<>();
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

            AttrSpec spec = WeaponPanelCatalog.byKey(key);
            // 额外槽位用「增量」口径：爆炸半径是「加了多少米」而不是「最终多少米」
            ValueFmt fmt = ValueFmt.PERCENT_SIGNED;
            if (spec != null) {
                fmt = spec.fmt() == ValueFmt.METERS_ABS ? ValueFmt.METERS_DELTA : spec.fmt();
                if (fmt == ValueFmt.PERCENT || fmt == ValueFmt.MULTIPLIER) {
                    fmt = ValueFmt.PERCENT_SIGNED;
                }
            }
            if (Math.abs(v) < fmt.epsilon()) {
                continue;
            }
            cells.add(new PanelGridComponent.Cell(label(PanelStyle.shortNameOf(key)),
                    value(fmt.format(v), v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        if (Math.abs(elementSum) >= 1.0e-3) {
            cells.add(new PanelGridComponent.Cell(label(I18n.get("item.module.triggerDamage")),
                    value(ValueFmt.PERCENT_SIGNED.format(elementSum),
                            elementSum >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        // 叠层词条在额外槽位里用「每层」口径，避免与主面板的「当前总加成」混淆
        for (String key : WeaponPanelCatalog.KILL_STACK_SPECS.keySet()) {
            Double v = extra.get(key);
            if (v == null || Math.abs(v) < 1.0e-3) {
                continue;
            }
            cells.add(new PanelGridComponent.Cell(label(PanelStyle.shortNameOf(key)),
                    Component.translatable("kuvalich.panel.stack.per", ValueFmt.PERCENT_SIGNED.format(v))
                            .withStyle(v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        return cells;
    }

    /** 模组名单：每个名字独立成 chip，超宽时才折行 */
    private static List<Component> moduleNameLines(List<ItemStack> modules, int budget) {
        List<Component> names = new ArrayList<>(modules.size());
        for (int i = 0; i < modules.size(); i++) {
            MutableComponent name = modules.get(i).getHoverName().copy().withStyle(ChatFormatting.GRAY);
            if (i < modules.size() - 1) {
                name.append(Component.literal(" " + PanelStyle.moduleSeparator()).withStyle(ChatFormatting.DARK_GRAY));
            }
            names.add(name);
        }
        return ChipPacker.pack(names, Component.empty(), Component.empty(), budget);
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

    // ==================== 元素 ====================

    private static void appendElementLine(ItemStack stack, List<ItemStack> modules,
                                          HashMap<String, Double> attrs, int budget, List<Component> out) {
        HashMap<String, String> pool = WeaponElementSystem.getTriggerElements(stack, modules);

        double total = 0;
        for (String key : WeaponPanelCatalog.ELEMENTS) {
            total += attrs.getOrDefault(key, 0.0);
        }
        if (KuvaWeaponUtil.hasType(stack)) {
            total += WeaponElementSystem.getKuvaWeaponElementDamage(stack);
        }

        if (pool.isEmpty() && Math.abs(total) < 1.0e-3) {
            return;
        }

        List<Component> chips = new ArrayList<>(pool.size() + 1);
        for (Map.Entry<String, String> e : pool.entrySet()) {
            chips.add(Component.literal(I18n.get("kuvaweapon.type." + e.getKey()) + e.getValue())
                    .withStyle(KuvaWeaponUtil.getColor(e.getKey()), ChatFormatting.BOLD));
        }
        if (Math.abs(total) >= 1.0e-3) {
            chips.add(Component.literal("(" + I18n.get("item.module.triggerDamage") + " "
                    + (total >= 0 ? "+" : "") + Math.round(total * 100) + "%)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        out.addAll(ChipPacker.pack(chips, PanelStyle.groupPrefix(PanelGroup.ELEMENT),
                PanelStyle.groupContinuation(PanelGroup.ELEMENT), budget));
    }

    // ==================== 叠层 ====================

    /**
     * 叠层区块
     *
     * @param detailed LIVE 视图为 true，会额外显示「每层 +X%」
     */
    private static void appendStackRows(HashMap<String, Double> attrs, StackCounts stacks,
                                        List<Component> out, boolean detailed) {
        List<StackRow> rows = WeaponPanelData.collectStacks(attrs, stacks);
        if (rows.isEmpty()) {
            return;
        }

        boolean first = true;
        for (StackRow row : rows) {
            Component prefix = first ? PanelStyle.groupPrefix(PanelGroup.STACK)
                    : PanelStyle.groupContinuation(PanelGroup.STACK);
            first = false;
            out.add(prefix.copy().append(stackRowBody(row, detailed)));
        }
    }

    private static Component stackRowBody(StackRow row, boolean detailed) {
        String name = PanelStyle.shortNameOf(row.key());
        MutableComponent c = Component.literal(name + " ").withStyle(ChatFormatting.GRAY);

        if (!row.available()) {
            // ⭐「不知道」与「确实是 0 层」必须可区分，否则玩家会把没数据误读成机制坏了
            c.append(Component.literal("????? ").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal("—/" + row.max()).withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal("  ").append(
                    Component.translatable("kuvalich.panel.stack.unsynced")).withStyle(ChatFormatting.DARK_GRAY));
            return c;
        }

        c.append(PanelStyle.progress(row.current(), row.max()));
        c.append(Component.literal("  "));

        if (detailed) {
            c.append(Component.translatable("kuvalich.panel.stack.per",
                    ValueFmt.PERCENT_SIGNED.format(row.perStack())).withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal("  "));
        }

        if (row.isIdle()) {
            // 零层：整行降为暗色，承诺值降级成「每层 +X%」
            c.append(Component.translatable("kuvalich.panel.stack.per",
                    ValueFmt.PERCENT_SIGNED.format(row.perStack())).withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal("  ").append(
                    Component.translatable("kuvalich.panel.stack.idle")).withStyle(ChatFormatting.DARK_GRAY));
            return c;
        }

        if (row.dependsOnTarget()) {
            // ⭐ killStackBaseDamage 的最终加成 = 每层 × 层数 × **目标身上的负面效果数**，
            //    tooltip 时没有目标，所以只给出「每负面效果」的口径，不编一个总百分比。
            c.append(Component.literal(ValueFmt.PERCENT_SIGNED.format(row.total()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            c.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.translatable("kuvalich.panel.stack.per_debuff").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            c.append(Component.literal(ValueFmt.PERCENT_SIGNED.format(row.total()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        c.append(Component.literal("  "));
        if (row.isFull()) {
            // 满层时不显示秒数：满层继续击杀会刷新计时器但不改变层数，
            // 服务端此时不发包，客户端的本地推算会一路数到 0 —— 显示「满层」永远不会错。
            c.append(Component.translatable("kuvalich.panel.stack.full")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else if (TooltipConfig.PANEL.showDecayTimer.get() && row.decayTicks() > 0) {
            double sec = row.decayTicks() / 20.0;
            if (sec < 1.0) {
                c.append(Component.translatable("kuvalich.panel.stack.decaying").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                c.append(Component.literal(String.format(Locale.ROOT, "%.1fs", sec))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return c;
    }

    // ==================== 额外槽位 ====================

    /**
     * 额外槽位加成的 chip
     *
     * <p>⭐ 遍历的是 {@code extra} 自己的 key，而<b>不是</b> {@code SPECS} 的 key。
     * 两者并不重合：SPECS 里暴击相关的 key 是 {@code criticalStrikeProbability} /
     * {@code criticalStrikeMultiplier}（它们的 provider 读的是 melee/remote 四个真实词条），
     * 而 {@code extra} 表里存的正是那四个真实 key。
     * 按 SPECS 取数的话 {@code extra.get("criticalStrikeProbability")} 永远是 null，
     * 四条额外槽位暴击加成一条都不会出现 —— 数值进了主面板，来源却看不见。
     * 顺带这样写也恢复了改造前「未登记词条兜底显示」的行为。</p>
     */
    private static List<Component> extraChips(HashMap<String, Double> extra) {
        List<Component> chips = new ArrayList<>();
        double elementSum = 0;

        List<String> keys = new ArrayList<>(extra.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            double v = extra.getOrDefault(key, 0.0);

            // 元素合并成一行，叠层单独用「每层」口径
            if (WeaponPanelCatalog.isElement(key)) {
                elementSum += v;
                continue;
            }
            if (WeaponPanelCatalog.KILL_STACK_SPECS.containsKey(key)) {
                continue;
            }

            AttrSpec spec = WeaponPanelCatalog.byKey(key);
            // 额外槽位用「增量」口径：爆炸半径是「加了多少米」而不是「最终多少米」
            ValueFmt fmt = ValueFmt.PERCENT_SIGNED;
            if (spec != null) {
                fmt = spec.fmt() == ValueFmt.METERS_ABS ? ValueFmt.METERS_DELTA : spec.fmt();
                if (fmt == ValueFmt.PERCENT || fmt == ValueFmt.MULTIPLIER) {
                    // 这几条在主面板是「最终值」口径，到额外槽位要换成「加了多少」
                    fmt = ValueFmt.PERCENT_SIGNED;
                }
            }
            if (Math.abs(v) < fmt.epsilon()) {
                continue;
            }
            chips.add(Component.literal(PanelStyle.shortNameOf(key) + " ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(fmt.format(v))
                            .withStyle(v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)));
        }

        if (Math.abs(elementSum) >= 1.0e-3) {
            chips.add(Component.literal(I18n.get("item.module.triggerDamage") + " ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(ValueFmt.PERCENT_SIGNED.format(elementSum))
                            .withStyle(elementSum >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)));
        }

        // 叠层词条在额外槽位里用「每层」口径，避免与主面板的「当前总加成」混淆
        for (String key : WeaponPanelCatalog.KILL_STACK_SPECS.keySet()) {
            Double v = extra.get(key);
            if (v == null || Math.abs(v) < 1.0e-3) {
                continue;
            }
            chips.add(Component.literal(PanelStyle.shortNameOf(key) + " ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("kuvalich.panel.stack.per", ValueFmt.PERCENT_SIGNED.format(v))
                            .withStyle(v >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }

        return chips;
    }

    // ==================== SHIFT：逐条完整 ====================

    private static void appendFull(ItemStack stack, List<ItemStack> modules,
                                   HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                   StackCounts stacks, boolean applyGates, List<Component> out) {
        out.add(Component.literal(I18n.get("item.module")).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

        for (PanelChip chip : WeaponPanelData.collect(stack, attrs, stacks, applyGates)) {
            AttrSpec spec = chip.spec();
            if (!isGroupEnabled(spec.group())) {
                continue;
            }
            MutableComponent line = Component.literal(" " + I18n.get(spec.longKey()) + " ")
                    .withStyle(ChatFormatting.GRAY);

            String value = spec.isPaired()
                    ? chip.baseText() + " / " + chip.secondaryText()
                    : chip.baseText();
            line.append(Component.literal(value).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));

            if (TooltipConfig.PANEL.showStackArrow.get() && chip.hasStackBonus()) {
                line.append(Component.literal(" " + PanelStyle.arrow() + " ").withStyle(ChatFormatting.DARK_GRAY));
                line.append(Component.literal(chip.stackedText()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                if (spec.isPaired()) {
                    // 只有主值（近战）被叠层抬高，远程那一侧照旧
                    line.append(Component.translatable("kuvalich.panel.stack.melee_only")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            out.add(line);
        }

        appendElementLine(stack, modules, attrs, ChipPacker.budget(0.9), out);

        if (TooltipConfig.PANEL.showStacks.get()) {
            List<StackRow> rows = WeaponPanelData.collectStacks(attrs, stacks);
            if (!rows.isEmpty()) {
                out.add(Component.translatable("kuvalich.panel.group.stack")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                for (StackRow row : rows) {
                    out.add(Component.literal(" ").append(stackRowBody(row, true)));
                }
            }
        }

        if (TooltipConfig.PANEL.showExtraSlots.get() && !extra.isEmpty()) {
            List<Component> chips = extraChips(extra);
            if (!chips.isEmpty()) {
                out.add(Component.translatable("item.module.extra_slot_header")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                for (Component chip : chips) {
                    out.add(Component.literal("  ").append(chip));
                }
            }
        }

        if (TooltipConfig.PANEL.showModuleList.get()) {
            out.add(Component.translatable("kuvaweapon.item_module_info")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (ItemStack module : modules) {
                out.add(Component.literal(" - ").append(module.getHoverName()).withStyle(ChatFormatting.WHITE));
            }
        }
    }

    // ==================== CTRL：来源分解 ====================

    private static void appendSource(ItemStack stack, List<ItemStack> modules,
                                     HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                     List<Component> out) {
        int budget = ChipPacker.budget(0.9);

        out.add(Component.translatable("kuvalich.panel.group.source")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        // 每张卡贡献了什么
        for (ItemStack module : modules) {
            double levelMult = ModuleLevelHelper.getEffectiveMultiplier(module);
            List<Component> chips = new ArrayList<>();
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(module)) {
                double v = ModuleConfig.clampAttributeValue(e.getKey(), e.getValue()) * levelMult;
                if (Math.abs(v) < 1.0e-3) {
                    continue;
                }
                chips.add(sourceChip(e.getKey(), v));
            }
            if (chips.isEmpty()) {
                continue;
            }

            MutableComponent prefix = Component.literal(" ")
                    .append(module.getHoverName().copy().withStyle(ChatFormatting.WHITE));
            if (ModuleLevelHelper.isLevelSystemEnabled()) {
                int lv = ModuleLevelHelper.getModuleLevel(module);
                int max = ModuleLevelHelper.getMaxLevel();
                prefix.append(Component.literal(" Lv." + lv + "/" + max).withStyle(ChatFormatting.DARK_GRAY));
            }
            prefix.append(Component.literal("  "));
            out.addAll(ChipPacker.pack(chips, prefix, Component.literal("   "), budget));
        }

        // 额外装备槽位的来源
        if (TooltipConfig.PANEL.showExtraSlots.get() && !extra.isEmpty()) {
            List<Component> chips = extraChips(extra);
            if (!chips.isEmpty()) {
                out.addAll(ChipPacker.pack(chips,
                        Component.literal(" ").append(Component.translatable("item.module.extra_slot_header")
                                .withStyle(ChatFormatting.GREEN)).append(Component.literal("  ")),
                        Component.literal("   "), budget));
            }
        }

        // ⭐ 被配置上限截断的词条：改造前是静默裁剪，面板显示 +300%、实战按 +150% 结算，
        //    玩家会以为是 bug 或被偷偷削弱。这里把它显式说出来。
        if (TooltipConfig.PANEL.showClampWarning.get()) {
            appendClampWarnings(modules, out);
        }
    }

    private static Component sourceChip(String key, double value) {
        AttrSpec spec = WeaponPanelCatalog.byKey(key);
        ValueFmt fmt = ValueFmt.PERCENT_SIGNED;
        if (spec != null) {
            fmt = spec.fmt() == ValueFmt.METERS_ABS ? ValueFmt.METERS_DELTA : spec.fmt();
            if (fmt == ValueFmt.PERCENT || fmt == ValueFmt.MULTIPLIER) {
                // 来源视图里这几条也是「加了多少」而不是「最终是多少」
                fmt = ValueFmt.PERCENT_SIGNED;
            }
        }
        String name = PanelStyle.shortNameOf(key);
        return Component.literal(name + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(fmt.format(value))
                        .withStyle(value >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    /** 找出「各卡之和」被 {@code clampAttributeTotal} 砍掉的词条 */
    private static void appendClampWarnings(List<ItemStack> modules, List<Component> out) {
        HashMap<String, Double> raw = new HashMap<>();
        for (ItemStack module : modules) {
            double levelMult = ModuleLevelHelper.getEffectiveMultiplier(module);
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(module)) {
                double v = ModuleConfig.clampAttributeValue(e.getKey(), e.getValue()) * levelMult;
                raw.merge(e.getKey(), v, Double::sum);
            }
        }

        for (Map.Entry<String, Double> e : raw.entrySet()) {
            double before = e.getValue();
            double after = ModuleConfig.clampAttributeTotal(e.getKey(), before);
            if (Math.abs(before - after) < 1.0e-3) {
                continue;
            }
            String name = PanelStyle.shortNameOf(e.getKey());
            out.add(Component.literal(" ").append(Component.translatable("kuvalich.panel.clamped",
                            name,
                            ValueFmt.PERCENT_SIGNED.format(before),
                            ValueFmt.PERCENT_SIGNED.format(after))
                    .withStyle(ChatFormatting.RED)));
        }
    }

    // ==================== ALT：实时状态 ====================

    private static void appendLive(ItemStack stack, List<ItemStack> modules,
                                   HashMap<String, Double> attrs, StackCounts stacks, List<Component> out) {
        int budget = ChipPacker.budget(0.9);

        out.add(Component.translatable("kuvalich.panel.group.live")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        // 叠层详情
        // ⭐ showStacks 关掉时 stacks 是 StackCounts.NONE，available() 恒为 false，
        //    直接渲染会把「玩家自己关掉了」误报成「????? （未同步）」——
        //    而那个状态的语义是「服务端没装这个模组 / 同步坏了」。
        List<StackRow> rows = isGroupEnabled(PanelGroup.STACK)
                ? WeaponPanelData.collectStacks(attrs, stacks)
                : List.of();
        if (rows.isEmpty()) {
            out.add(Component.literal(" ").append(
                    Component.translatable("kuvalich.panel.stack.none").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            for (StackRow row : rows) {
                out.add(Component.literal(" ").append(stackRowBody(row, true)));
            }
        }

        // 元素：投入 → 成池，看清哪两个基础元素合成了什么
        if (TooltipConfig.PANEL.showElements.get()) {
            List<Component> raw = new ArrayList<>();
            for (String key : WeaponPanelCatalog.ELEMENTS) {
                double v = attrs.getOrDefault(key, 0.0);
                if (Math.abs(v) >= 1.0e-3) {
                    raw.add(Component.literal(I18n.get("kuvaweapon.type." + key)
                                    + ValueFmt.PERCENT_SIGNED.format(v))
                            .withStyle(KuvaWeaponUtil.getColor(key)));
                }
            }
            if (!raw.isEmpty()) {
                out.addAll(ChipPacker.pack(raw,
                        Component.literal(" ").append(Component.translatable("kuvalich.panel.element.input")
                                .withStyle(ChatFormatting.GRAY)).append(Component.literal("  ")),
                        Component.literal("   "), budget));
            }

            HashMap<String, String> pool = WeaponElementSystem.getTriggerElements(stack, modules);
            if (!pool.isEmpty()) {
                List<Component> poolChips = new ArrayList<>();
                for (Map.Entry<String, String> e : pool.entrySet()) {
                    poolChips.add(Component.literal(I18n.get("kuvaweapon.type." + e.getKey()) + e.getValue())
                            .withStyle(KuvaWeaponUtil.getColor(e.getKey()), ChatFormatting.BOLD));
                }
                out.addAll(ChipPacker.pack(poolChips,
                        Component.literal(" ").append(Component.translatable("kuvalich.panel.element.pool")
                                .withStyle(ChatFormatting.GRAY)).append(Component.literal("  ")),
                        Component.literal("   "), budget));
            }
        }

        // 触发几率分解
        double baseTrigger = WeaponModuleHandler.getBaseAttribute(stack, "triggerChance");
        double modTrigger = attrs.getOrDefault("triggerChance", 0.0);
        int stackN = stacks.available() ? stacks.stacks(StackType.TRIGGER_CHANCE) : 0;
        double stackTrigger = attrs.getOrDefault("killStackTriggerChance", 0.0) * stackN;
        double uncapped = baseTrigger * (1 + modTrigger) * (1 + stackTrigger);

        // ⭐ 战斗端在所有乘算做完后还有一道硬上限：
        //    triggerChance = min(triggerChance, MAX_ELEMENT_TRIGGER_PER_HIT * 100)。
        //    LIVE 视图的卖点就是「这个数就是实战的数」，不钳就会出现面板 700% / 实战 500%。
        double cap = WeaponCombatHandler.MAX_ELEMENT_TRIGGER_PER_HIT;
        double finalTrigger = Math.min(uncapped, cap);

        MutableComponent breakdown = Component.literal(" ")
                .append(Component.translatable("kuvalich.panel.trigger.breakdown",
                        ValueFmt.PERCENT.format(baseTrigger),
                        ValueFmt.PERCENT_SIGNED.format(modTrigger),
                        ValueFmt.PERCENT_SIGNED.format(stackTrigger),
                        ValueFmt.PERCENT.format(finalTrigger))
                        .withStyle(ChatFormatting.DARK_GRAY));
        if (uncapped > cap + 1.0e-6) {
            breakdown.append(Component.literal(" ")).append(
                    Component.translatable("kuvalich.panel.trigger.capped").withStyle(ChatFormatting.RED));
        }
        out.add(breakdown);

        // 冲刺攻击有额外的触发加成（战斗端在冲刺分支里多乘一个 dashTriggerChance）
        double dash = attrs.getOrDefault("dashTriggerChance", 0.0);
        if (Math.abs(dash) >= 1.0e-3) {
            double dashFinal = Math.min(baseTrigger * (1 + modTrigger + dash) * (1 + stackTrigger), cap);
            out.add(Component.literal(" ").append(Component.translatable("kuvalich.panel.trigger.dash",
                            ValueFmt.PERCENT_SIGNED.format(dash),
                            ValueFmt.PERCENT.format(dashFinal))
                    .withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    // ==================== 无模组时的基础面板 ====================

    private static void appendBasePanel(ItemStack stack, List<Component> out) {
        out.add(Component.translatable("item.base").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        out.add(baseLine(stack, "item.base.damage", "damage", ValueFmt.PERCENT));
        out.add(baseLine(stack, "item.base.criticalStrikeProbability", "criticalStrikeProbability", ValueFmt.PERCENT));
        out.add(baseLine(stack, "item.base.criticalStrikeMultiplier", "criticalStrikeMultiplier", ValueFmt.MULTIPLIER));
        out.add(baseLine(stack, "item.base.triggerChance", "triggerChance", ValueFmt.PERCENT));
    }

    private static Component baseLine(ItemStack stack, String langKey, String attr, ValueFmt fmt) {
        return Component.literal(I18n.get(langKey) + " ")
                .append(Component.literal(fmt.format(WeaponModuleHandler.getBaseAttribute(stack, attr)))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
    }

    // ==================== 提示行与行预算 ====================

    private static Component hintLine(TooltipView view) {
        if (view != TooltipView.COMPACT) {
            return Component.translatable("kuvalich.panel.hint.release").withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.translatable("kuvalich.panel.hint.keys").withStyle(ChatFormatting.DARK_GRAY);
    }

    private WeaponPanelComposer() {
    }
}
