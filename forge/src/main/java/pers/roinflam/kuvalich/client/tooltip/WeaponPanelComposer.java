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
     * 为一把武器生成面板行
     *
     * @param stack  被查看的武器
     * @param viewer 查看者（可能为 null：JEI 配方页 / 创造栏）
     * @param view   当前视图
     * @return 待插入 tooltip 的行；空列表表示这件物品不归本面板管
     */
    public static List<Component> compose(ItemStack stack, @Nullable Player viewer, TooltipView view) {
        List<Component> out = new ArrayList<>();

        if (!WeaponModuleHandler.hasBase(stack) || !TooltipConfig.PANEL.enabled.get()) {
            return out;
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
            return out;
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

        switch (effective) {
            case FULL -> appendFull(stack, modules, attrs, extra, stacks, applyGates, out);
            case SOURCE -> appendSource(stack, modules, attrs, extra, out);
            case LIVE -> appendLive(stack, modules, attrs, stacks, out);
            default -> appendCompact(stack, modules, attrs, extra, stacks, applyGates, out);
        }

        if (TooltipConfig.PANEL.showKeyHint.get()) {
            out.add(hintLine(view));
        }

        // ⭐ 行预算兜底只作用于默认视图。
        //    1.20.1 原版对超高 tooltip 只做纵向钳位、不裁剪不滚动，所以默认态必须有硬约束；
        //    但玩家按住功能键时是**明确要求看细节**，这时截断反而是帮倒忙
        //    （而且截断后的行数依然可能超屏，治标不治本）。
        if (effective == TooltipView.COMPACT) {
            return trim(out, TooltipConfig.PANEL.maxLines.get());
        }
        return out;
    }

    // ==================== 默认：分组紧凑 ====================

    private static void appendCompact(ItemStack stack, List<ItemStack> modules,
                                      HashMap<String, Double> attrs, HashMap<String, Double> extra,
                                      StackCounts stacks, boolean applyGates, List<Component> out) {
        int budget = ChipPacker.budget(TooltipConfig.PANEL.widthRatio.get());
        List<PanelChip> chips = WeaponPanelData.collect(stack, attrs, stacks, applyGates);

        // 按组聚合，保持 catalog 里的组内顺序
        EnumMap<PanelGroup, List<Component>> byGroup = new EnumMap<>(PanelGroup.class);
        for (PanelChip chip : chips) {
            byGroup.computeIfAbsent(chip.spec().group(), g -> new ArrayList<>()).add(compactChip(chip));
        }

        // ⭐ 未登记的词条（整合包通过 JSON 自定义的）归入 OTHER 组，
        //    不兑底的话它们在面板上会静默消失，而战斗结算里又确实算数。
        if (TooltipConfig.PANEL.showUnknownAttributes.get()) {
            for (Map.Entry<String, Double> e : WeaponPanelData.collectUnknown(attrs).entrySet()) {
                byGroup.computeIfAbsent(PanelGroup.OTHER, g -> new ArrayList<>()).add(unknownChip(e.getKey(), e.getValue()));
            }
        }

        for (PanelGroup group : new PanelGroup[]{PanelGroup.PANEL, PanelGroup.DAMAGE,
                PanelGroup.GUN, PanelGroup.RARE, PanelGroup.OTHER}) {
            if (!isGroupEnabled(group)) {
                continue;
            }
            List<Component> groupChips = byGroup.get(group);
            if (groupChips == null || groupChips.isEmpty()) {
                continue;
            }
            out.addAll(ChipPacker.pack(groupChips, PanelStyle.groupPrefix(group),
                    PanelStyle.groupContinuation(group), budget));
        }

        // 元素：一行彩色缩写 + 总量
        if (isGroupEnabled(PanelGroup.ELEMENT)) {
            appendElementLine(stack, modules, attrs, budget, out);
        }

        // 叠层：每条叠层词条一行（这是用户最关心的「当前叠了几层」）
        if (isGroupEnabled(PanelGroup.STACK)) {
            appendStackRows(attrs, stacks, out, false);
        }

        // 额外装备槽位
        if (TooltipConfig.PANEL.showExtraSlots.get() && !extra.isEmpty()) {
            List<Component> extraChips = extraChips(extra);
            if (!extraChips.isEmpty()) {
                out.addAll(ChipPacker.pack(extraChips, PanelStyle.groupPrefix(PanelGroup.EXTRA),
                        PanelStyle.groupContinuation(PanelGroup.EXTRA), budget));
            }
        }

        // 模组名单：8 行压成 1~2 行
        if (isGroupEnabled(PanelGroup.MODULES)) {
            appendModuleList(modules, budget, out);
        }
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

    /**
     * 描述表不认识的词条（整合包自定义）的 chip
     *
     * <p>没有元数据，只能按「原始 key 名 + 带正号整数百分比」显示。
     * 名字走 {@link PanelStyle#shortNameOf} 的回落链：
     * 作者如果自己补了 {@code kuvalich.attr.short.<key>} 翻译就会显示中文名，
     * 没补就显示裸 key（比显示一串 item.module.xxx 有用）。</p>
     */
    private static Component unknownChip(String key, double value) {
        return Component.literal(PanelStyle.shortNameOf(key) + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ValueFmt.PERCENT_SIGNED.format(value))
                        .withStyle(value >= 0 ? ChatFormatting.GRAY : ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /** 一个紧凑 chip：{@code 近战 +180%} 或 {@code 暴伤 x2.4→x3.0} */
    private static Component compactChip(PanelChip chip) {
        AttrSpec spec = chip.spec();
        PanelGroup group = spec.group();
        ChatFormatting valueColor = PanelStyle.valueColor(group, chip.isNegative());

        MutableComponent c = Component.literal(PanelStyle.shortName(spec) + " ")
                .withStyle(ChatFormatting.GRAY);

        boolean arrow = TooltipConfig.PANEL.showStackArrow.get() && chip.hasStackBonus();

        c.append(Component.literal(chip.baseText()).withStyle(valueColor, ChatFormatting.BOLD));

        // ⭐ 叠层只抬高主值（近战）那一侧 —— killStackMeleeCriticalMultiplier
        //    在战斗端只在近战分支生效。所以箭头必须紧跟在主值后面、
        //    而不是画在「主/副」这一对的末尾，否则 x2.4/x2.0→x3.0 会被读成两边都涨到 x3.0。
        if (arrow) {
            c.append(Component.literal(PanelStyle.arrow()).withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(chip.stackedText()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }

        if (spec.isPaired() && chip.secondaryText() != null) {
            // 近战 / 远程成对：45%/30%
            c.append(Component.literal("/").withStyle(ChatFormatting.DARK_GRAY));
            c.append(Component.literal(chip.secondaryText()).withStyle(valueColor, ChatFormatting.BOLD));
        }

        return c;
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

    // ==================== 模组名单 ====================

    private static void appendModuleList(List<ItemStack> modules, int budget, List<Component> out) {
        if (modules.isEmpty()) {
            return;
        }
        // ⭐ 每个模组名独立成一个 chip：ChipPacker 只能在 chip 与 chip 之间换行，
        //    拼成一个大 Component 的话无论多宽都只能挤在一行、必定超出像素预算。
        List<Component> names = new ArrayList<>(modules.size());
        for (int i = 0; i < modules.size(); i++) {
            MutableComponent name = modules.get(i).getHoverName().copy().withStyle(ChatFormatting.GRAY);
            if (i < modules.size() - 1) {
                name.append(Component.literal(PanelStyle.moduleSeparator()).withStyle(ChatFormatting.DARK_GRAY));
            }
            names.add(name);
        }

        Component prefix = Component.empty()
                .copy()
                .append(TooltipConfig.PANEL.showGutter.get()
                        ? Component.literal(PanelStyle.gutter()).withStyle(PanelGroup.MODULES.gutterColor())
                        : Component.empty())
                .append(Component.translatable("kuvalich.panel.group.modules").withStyle(PanelGroup.MODULES.titleColor()))
                .append(Component.literal(" " + modules.size() + "/8  ").withStyle(ChatFormatting.DARK_GRAY));

        out.addAll(ChipPacker.pack(names, prefix,
                PanelStyle.groupContinuation(PanelGroup.MODULES), budget));
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

    /**
     * 行预算硬上限
     *
     * <p>1.20.1 原版对超高 tooltip 只做纵向钳位，不裁剪也不滚动 ——
     * 高度超过屏幕时上下同时溢出，连物品名都会被切掉。
     * 压缩后正常不会触发，但必须有这条硬约束兜底
     * （GUI 缩放 4 的 1280×720 只有约 14 行可用，比直觉窄得多）。</p>
     */
    private static List<Component> trim(List<Component> lines, int max) {
        if (lines.size() <= max) {
            return lines;
        }
        List<Component> trimmed = new ArrayList<>(lines.subList(0, Math.max(1, max - 1)));
        trimmed.add(Component.translatable("kuvalich.panel.truncated", lines.size() - trimmed.size())
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return trimmed;
    }

    private WeaponPanelComposer() {
    }
}
