package pers.roinflam.kuvalich.module.weapon.panel;

import pers.roinflam.kuvalich.module.KillStackManager.StackType;

import javax.annotation.Nullable;

/**
 * 一条面板属性的完整描述
 *
 * <p>这个 record 是替掉 {@code WeaponModuleHandler.onItemTooltip} 里那 40 多个
 * 重复 if 块的核心：以前每加一条词条就要复制粘贴一整段
 * {@code if (attributes.getOrDefault("x", 0.0) != 0) { tooltip.add(index++, ...) }}，
 * 现在只需要往 {@link WeaponPanelCatalog#SPECS} 里加一行。</p>
 *
 * @param key           属性键，同时也是 i18n 后缀（长名 {@code item.module.<key>}，短名 {@code kuvalich.attr.short.<key>}）
 * @param group         所属语义分组，决定装订线颜色与整组是否生成
 * @param provider      取值方式
 * @param fmt           格式化方式
 * @param gate          生成条件（近战武器不生成枪械词条等）
 * @param secondary     成对词条的第二个取值方式（近战 / 远程并排显示），无则 null
 * @param stackType     该属性会被哪种击杀叠层抬高，无则 null
 * @param stackFn       叠层的具体计算方式，{@code stackType} 非 null 时必须同时提供
 * @param always        即使数值为 0 也显示（武器的身份信息）
 * @param visibilityKey 判断「有没有这条词条」时该读哪个原始 key；null 表示直接用 {@code provider} 的结果
 *
 * @author RoinFlam
 */
public record AttributeSpec(String key,
                       PanelGroup group,
                       ValueProvider provider,
                       ValueFormat fmt,
                       PanelRowGate gate,
                       @Nullable ValueProvider secondary,
                       @Nullable StackType stackType,
                       @Nullable StackFunction stackFn,
                       boolean always,
                       @Nullable String visibilityKey) {

    /** 最常见的一种：直接读词条、带正号的整数百分比、任何武器都显示 */
    public static AttributeSpec of(String key, PanelGroup group, ValueFormat fmt) {
        return new AttributeSpec(key, group, ValueProvider.modifier(key), fmt, PanelRowGate.ANY,
                null, null, null, false, null);
    }

    /** 直接读词条 + 带正号整数百分比（最高频的写法） */
    public static AttributeSpec pct(String key, PanelGroup group) {
        return of(key, group, ValueFormat.PERCENT_SIGNED);
    }

    public AttributeSpec gated(PanelRowGate gate) {
        return new AttributeSpec(key, group, provider, fmt, gate, secondary, stackType, stackFn, always, visibilityKey);
    }

    public AttributeSpec stacked(StackType type, StackFunction fn) {
        return new AttributeSpec(key, group, provider, fmt, gate, secondary, type, fn, always, visibilityKey);
    }

    public AttributeSpec paired(ValueProvider second) {
        return new AttributeSpec(key, group, provider, fmt, gate, second, stackType, stackFn, always, visibilityKey);
    }

    /**
     * 标记为「数值为 0 也显示」
     *
     * <p>⭐ 只有基伤 / 暴击 / 暴伤 / 触发这四条是武器的<b>身份信息</b>，
     * 哪怕全是基础值也必须让玩家看到。
     * 这个标志以前挂在 {@code PanelGroup.alwaysVisible()}（已删除）上，是<b>组级</b>的，
     * 于是同在 PANEL 组的多重 / 攻速 / 射速 / 范围 / 触发时长也被顺带恒显了 ——
     * 一把只插了伤害卡的武器会平白多出「多重 +0%  攻速 0.0%  射速 +0%  范围 +0%」
     * 四个零值 chip，而改造前这几条都是 {@code != 0} 才显示的。</p>
     */
    public AttributeSpec markAlways() {
        return new AttributeSpec(key, group, provider, fmt, gate, secondary, stackType, stackFn, true, visibilityKey);
    }

    /**
     * 指定判断「有没有这条词条」时读哪个原始 key
     *
     * <p>{@code triggerTime} 的 provider 是 {@code 1 + 词条}，词条为 0 时结果是 1.0，
     * 直接拿它跟 epsilon 比会<b>永远</b>过阈值、渲染出一条「触发时长 100%」的空行。
     * 这种「显示值 ≠ 存在性判据」的词条必须显式指出判据。</p>
     */
    public AttributeSpec visibleWhen(String rawKey) {
        return new AttributeSpec(key, group, provider, fmt, gate, secondary, stackType, stackFn, always, rawKey);
    }

    /** 替换取值方式（catalog 内部构造用） */
    public AttributeSpec withProviderInternal(ValueProvider p) {
        return new AttributeSpec(key, group, p, fmt, gate, secondary, stackType, stackFn, always, visibilityKey);
    }

    /** 长名翻译键（SHIFT 逐条视图用，与改造前逐字一致） */
    public String longKey() {
        return "item.module." + key;
    }

    /** 短名翻译键（默认紧凑视图用），缺失时渲染层回落到长名 */
    public String shortKey() {
        return "kuvalich.attr.short." + key;
    }

    /** 是否为成对词条（近战 / 远程两个数值并排） */
    public boolean isPaired() {
        return secondary != null;
    }
}
