package pers.roinflam.kuvalich.base.potion.hide;

import net.minecraft.potion.PotionEffect;
import pers.roinflam.kuvalich.base.potion.PotionBase;

/**
 * 隐藏药水效果基类
 *
 * 用于创建完全隐藏的药水效果（不显示在界面上）
 *
 * 特性：
 * 1. 不在物品栏显示
 * 2. 不在HUD显示
 * 3. 不显示粒子效果
 * 4. 玩家完全感知不到（除非通过其他方式检测）
 *
 * 使用场景：
 * - 内部状态标记（如：免疫某种伤害）
 * - 被动效果（如：持续改变属性）
 * - 隐藏的增益/减益
 * - 游戏机制标记
 *
 * 示例用法：
 * ```java
 * public class MobEffectIce extends HideBase {
 *     public MobEffectIce() {
 *         super(true, 0xFF00FFFF, "ice");
 *         // 减速效果
 *         this.registerPotionAttributeModifier(
 *             SharedMonsterAttributes.MOVEMENT_SPEED,
 *             "uuid-here",
 *             -0.15,
 *             2
 *         );
 *     }
 * }
 * ```
 *
 * 注意事项：
 * - 虽然不显示图标，但效果仍然有效
 * - 可以通过命令 /effect 检测到
 * - 可以通过代码 entity.getActivePotionEffect(this) 检测到
 * - 属性修改器仍然会生效

 */
public abstract class HideBase extends PotionBase {

    /**
     * 构造隐藏药水效果
     *
     * @param isBadEffectIn 是否为负面效果
     *                      - true: 负面效果（用于免疫判断等）
     *                      - false: 正面效果
     * @param liquidColorIn 药水液体颜色（虽然不显示，但仍需要设置）
     *                      - 用于药水瓶等物品的颜色
     * @param name 药水注册名
     */
    protected HideBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    /**
     * 效果触发时机
     *
     * 对于隐藏效果，默认每tick都准备好执行
     * 但由于performEffect()为空，实际不会有任何操作
     *
     * 子类可以重写此方法来控制执行频率：
     * ```java
     * @Override
     * public boolean isReady(int duration, int amplifier) {
     *     return duration % 20 == 0; // 改为每秒执行
     * }
     * ```
     *
     * @param duration 剩余持续时间（tick）
     * @param amplifier 效果等级
     * @return true - 默认始终准备好
     */
    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    /**
     * 是否渲染效果图标
     *
     * 控制是否在以下位置显示：
     * - 物品栏界面（E键界面）
     * - HUD（屏幕右上角）
     * - 药水效果列表
     *
     * @param effect 药水效果实例
     * @return false - 永不渲染
     */
    @Override
    public boolean shouldRender(PotionEffect effect) {
        return false;
    }

    /**
     * 是否在物品栏显示文本
     *
     * 控制是否显示效果名称和持续时间
     *
     * @param effect 药水效果实例
     * @return false - 不显示文本
     */
    @Override
    public boolean shouldRenderInvText(PotionEffect effect) {
        return false;
    }

    /**
     * 是否在HUD显示
     *
     * 控制是否在屏幕右上角显示图标
     *
     * @param effect 药水效果实例
     * @return false - 不在HUD显示
     */
    @Override
    public boolean shouldRenderHUD(PotionEffect effect) {
        return false;
    }
}