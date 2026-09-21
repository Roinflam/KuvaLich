package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 击杀叠层如何抬高一条面板属性
 *
 * <p>⭐ 这里的每一条公式都必须与 {@code WeaponCombatHandler.applyKillStackEffects}
 * 以及散落在 {@code :529 / :550 / :674} 的三处叠层计算**逐字对应**。
 * 面板一旦自己重写一份加法，就会重演项目里已经发生过一次的
 * 「tooltip 自己累加、战斗走另一套」的分叉 bug。</p>
 *
 * <p>叠层公式并不统一，实测有三种形态：
 * <ul>
 *   <li><b>加算</b>：{@code multishot / attackSpeed / attackRange / firing_rate}
 *       —— {@code attributes.put(k, v + stackValue * stacks)}</li>
 *   <li><b>乘算</b>：{@code criticalStrikeMultiplier / triggerChance}
 *       —— {@code value *= (1 + stackValue * stacks)}</li>
 *   <li><b>带系数的加算</b>：{@code bursting_radius} 的 range 是 {@code 1 + v*2}，
 *       叠层加的是 {@code stackValue * stacks * 2}；弓的射速显示要再乘 2</li>
 * </ul>
 * </p>
 *
 * @author RoinFlam
 */
@FunctionalInterface
public interface StackFn {

    /**
     * @param stack  被查看的武器
     * @param attrs  已汇总的属性表（含 {@code killStackXxx} 的每层值）
     * @param base   不含叠层的面板数值
     * @param stacks 当前层数
     * @return 含叠层的面板数值
     */
    double apply(ItemStack stack, Map<String, Double> attrs, double base, int stacks);

    /** 加算：{@code base + 每层值 × 层数} */
    static StackFn add(String perStackKey) {
        return (stack, attrs, base, stacks) -> base + attrs.getOrDefault(perStackKey, 0.0) * stacks;
    }

    /** 乘算：{@code base × (1 + 每层值 × 层数)} */
    static StackFn mul(String perStackKey) {
        return (stack, attrs, base, stacks) -> base * (1 + attrs.getOrDefault(perStackKey, 0.0) * stacks);
    }

    /**
     * 带系数的加算：{@code base + 每层值 × 层数 × factor}
     * <p>{@code bursting_radius} 的 provider 返回的是原始词条值 v（格式化器再算 {@code 1 + v*2}），
     * 而战斗端给 range 加的是 {@code stackValue * stacks * 2}，
     * 换算回原始值域正好是 {@code v + stackValue * stacks}，所以这里 factor 传 1。</p>
     */
    static StackFn addScaled(String perStackKey, double factor) {
        return (stack, attrs, base, stacks) -> base + attrs.getOrDefault(perStackKey, 0.0) * stacks * factor;
    }
}
