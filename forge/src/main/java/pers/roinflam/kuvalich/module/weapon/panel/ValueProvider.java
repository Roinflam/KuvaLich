package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;

import java.util.Map;

/**
 * 面板上一条属性的取值方式
 *
 * <p>以前 {@code WeaponModuleHandler.onItemTooltip} 里的 40 多个 if 块，
 * 差别其实只有三种：直接读词条、基础值乘词条、1 加词条。
 * 把这三种抽成工厂方法之后，那 40 个 if 块就退化成一张表 + 一个循环。</p>
 *
 * @author RoinFlam
 */
@FunctionalInterface
public interface ValueProvider {

    /**
     * @param stack 被查看的武器
     * @param attrs 已汇总（含等级缩放、上下限裁剪、额外槽位合并）的属性表
     * @return 该条属性的面板数值
     */
    double get(ItemStack stack, Map<String, Double> attrs);

    /**
     * 直接读模组词条的累加值。
     * <p>对应原先绝大多数 {@code attributes.getOrDefault("xxx", 0.0)} 的写法。</p>
     */
    static ValueProvider modifier(String key) {
        return (stack, attrs) -> attrs.getOrDefault(key, 0.0);
    }

    /**
     * 武器基础值 × (1 + 词条)。
     * <p>对应暴击率 / 暴击倍率 / 触发几率这三条「基础值被百分比放大」的属性。</p>
     */
    static ValueProvider scaledBase(String baseKey, String modKey) {
        return (stack, attrs) -> WeaponModuleHandler.getBaseAttribute(stack, baseKey)
                * (1 + attrs.getOrDefault(modKey, 0.0));
    }

    /** 纯武器基础值，对应面板的「基础伤害」。 */
    static ValueProvider base(String baseKey) {
        return (stack, attrs) -> WeaponModuleHandler.getBaseAttribute(stack, baseKey);
    }

    /**
     * 1 + 词条。
     * <p>对应 {@code triggerTime}：词条是增量，但面板要显示成「相对原时长的百分比」。</p>
     */
    static ValueProvider onePlus(String key) {
        return (stack, attrs) -> 1 + attrs.getOrDefault(key, 0.0);
    }

    /**
     * 词条 × 倍数。
     * <p>对应弓的 {@code firing_rate}：弓的蓄力加速是双倍生效的，面板要跟着翻倍。</p>
     */
    static ValueProvider scaled(String key, double factor) {
        return (stack, attrs) -> attrs.getOrDefault(key, 0.0) * factor;
    }
}
