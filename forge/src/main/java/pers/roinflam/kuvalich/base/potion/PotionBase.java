// 文件：PotionBase.java
// 路径：src/main/java/pers/roinflam/kuvalich/base/potion/PotionBase.java
package pers.roinflam.kuvalich.base.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.util.PotionUtil;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * 注意：不再在实例级别注册事件，避免重复监听
 */
public abstract class PotionBase extends Potion {
    private final String registryName;

    protected PotionBase(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
        super(isBadEffectIn, liquidColorIn);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("药水注册名不能为空");
        }

        this.registryName = name;

        try {
            // 移除了实例级别的事件总线注册，避免性能问题
            // 子类如需监听事件，请在类级别使用@Mod.EventBusSubscriber

            PotionUtil.registerPotion(this, name);
            KuvaLichPotion.POTIONS.add(this);

            LogUtil.debug("药水效果注册成功: " + name +
                    " (负面效果: " + isBadEffectIn +
                    ", 颜色: 0x" + Integer.toHexString(liquidColorIn).toUpperCase() + ")");
        } catch (Exception e) {
            LogUtil.error("药水效果注册失败: " + name, e);
            throw new RuntimeException("无法注册药水效果: " + name, e);
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        // 默认无操作，子类可以重写
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        // 默认每秒执行一次（性能友好）
        return duration % 20 == 0;
    }

    @Override
    public String getName() {
        String baseName = super.getName();
        LogUtil.debug("获取药水名称: " + registryName + " -> " + baseName);
        return baseName;
    }
}