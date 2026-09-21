package pers.roinflam.kuvalich.module.weapon.panel;

import javax.annotation.Nullable;

/**
 * 面板上的一个「数值块」——紧凑视图里同一组的多个 chip 会被打包进同一行
 *
 * @param spec       它来自哪条属性描述
 * @param base       不含击杀叠层的数值
 * @param secondary  成对词条的第二个数值（近战 / 远程），无则 null
 * @param withStacks 含当前击杀叠层的数值；与 {@code base} 相等表示当前没有叠层加成
 *
 * @author RoinFlam
 */
public record PanelChip(AttrSpec spec,
                        double base,
                        @Nullable Double secondary,
                        double withStacks) {

    /**
     * 当前是否有叠层在生效（决定是否画「基础 → 含叠层」的箭头）。
     * <p>用 {@link ValueFmt#epsilon()} 而不是 {@code !=}：浮点相等判断在这里
     * 会因为 {@code stacks * perStack} 的累加误差而误判成「有叠层」。</p>
     */
    public boolean hasStackBonus() {
        return Math.abs(withStacks - base) >= spec.fmt().epsilon();
    }

    /** 格式化后的基础值文本 */
    public String baseText() {
        return spec.fmt().format(base);
    }

    /** 格式化后的第二个值文本（成对词条），无则 null */
    @Nullable
    public String secondaryText() {
        return secondary == null ? null : spec.fmt().format(secondary);
    }

    /** 格式化后的含叠层值文本 */
    public String stackedText() {
        return spec.fmt().format(withStacks);
    }

    /** 该 chip 是否代表一个负值（裂罅惩罚词条），渲染层据此上红色 */
    public boolean isNegative() {
        return base < 0 || (secondary != null && secondary < 0);
    }
}
