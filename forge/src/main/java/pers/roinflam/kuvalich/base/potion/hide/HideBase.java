package pers.roinflam.kuvalich.base.potion.hide;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import pers.roinflam.kuvalich.base.potion.PotionBase;

import java.util.function.Consumer;

/**
 * 隐藏药水效果基类
 * Hidden potion effect base class
 *
 * 用于创建完全隐藏的药水效果（不显示在界面上）
 * Used to create completely hidden potion effects (not shown in UI)
 */
public abstract class HideBase extends PotionBase {

    protected HideBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    /**
     * 初始化客户端扩展，控制效果在GUI中的渲染
     * Initialize client extensions to control effect rendering in GUI
     */
    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            /**
             * 控制是否在物品栏中渲染效果
             * Control whether to render effect in inventory
             *
             * @return false表示不渲染 / false means don't render
             */
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) {
                return false;
            }

            /**
             * 控制是否在HUD中渲染效果（屏幕右上角）
             * Control whether to render effect in HUD (top right of screen)
             *
             * @return false表示不渲染 / false means don't render
             */
            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) {
                return false;
            }
        });
    }

    /**
     * 创建隐藏的效果实例（不显示粒子和图标）
     * Create hidden effect instance (no particles and no icon)
     *
     * @param duration 持续时间(tick) / duration in ticks
     * @param amplifier 效果等级 / amplifier level
     * @return 完全隐藏的效果实例 / completely hidden effect instance
     */
    public MobEffectInstance createInstance(int duration, int amplifier) {
        return new MobEffectInstance(this, duration, amplifier, false, false, false);
    }

    /**
     * 创建隐藏的效果实例（默认等级0）
     * Create hidden effect instance (default amplifier 0)
     *
     * @param duration 持续时间(tick) / duration in ticks
     * @return 完全隐藏的效果实例 / completely hidden effect instance
     */
    public MobEffectInstance createInstance(int duration) {
        return createInstance(duration, 0);
    }
}