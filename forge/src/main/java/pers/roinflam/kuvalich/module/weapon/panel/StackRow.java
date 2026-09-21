package pers.roinflam.kuvalich.module.weapon.panel;

import pers.roinflam.kuvalich.module.KillStackManager.StackType;

/**
 * 面板「击杀叠层」区块的一行
 *
 * @param key             叠层词条 key（如 {@code killStackMultishot}）
 * @param type            对应的叠层类型
 * @param perStack        每层加成（原始值域，未乘层数）
 * @param current         当前层数
 * @param max             上限层数
 * @param decayTicks      距离掉一层的剩余 tick，未知为 -1
 * @param available       叠层数据是否可用（false 时显示「未同步」而不是 0 层）
 * @param dependsOnTarget 最终加成是否依赖目标状态（{@code killStackBaseDamage} 专属）
 *
 * @author RoinFlam
 */
public record StackRow(String key,
                       StackType type,
                       double perStack,
                       int current,
                       int max,
                       int decayTicks,
                       boolean available,
                       boolean dependsOnTarget) {

    /** 当前总加成（原始值域）。{@link #dependsOnTarget} 为 true 时这个值不完整，渲染层要另作处理。 */
    public double total() {
        return perStack * current;
    }

    public boolean isFull() {
        return max > 0 && current >= max;
    }

    public boolean isIdle() {
        return current <= 0;
    }

    /** 短名翻译键，与普通词条共用同一套命名 */
    public String shortKey() {
        return "kuvalich.attr.short." + key;
    }
}
