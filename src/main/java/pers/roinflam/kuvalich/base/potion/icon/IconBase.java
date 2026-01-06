package pers.roinflam.kuvalich.base.potion.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.kuvalich.base.potion.PotionBase;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 带自定义图标的药水效果基类
 *
 * 用于创建显示自定义图标的药水效果
 *
 * 特性：
 * 1. 自动渲染自定义图标
 * 2. 在物品栏和HUD都显示
 * 3. 支持18x18像素的PNG图标
 * 4. 图标位置自动计算
 *
 * 子类实现要求：
 * - 必须实现 getResourceLocation() 方法
 * - 返回图标的资源位置
 *
 * 图标文件要求：
 * - 格式：PNG
 * - 尺寸：18x18像素
 * - 位置：assets/kuvalich/textures/gui/container/potion/效果名.png
 *
 * 示例用法：
 * ```java
 * public class MobEffectHealth extends IconBase {
 *     public MobEffectHealth() {
 *         super(false, 0xFF00FF00, "health");
 *     }
 *
 *     @Override
 *     protected ResourceLocation getResourceLocation() {
 *         return new ResourceLocation(Reference.MOD_ID,
 *             "textures/gui/container/potion/health.png");
 *     }
 * }
 * ```
 *
 * 渲染位置：
 * - 物品栏：效果列表中，每个效果占据一行
 * - HUD：屏幕右上角，紧凑排列

 */
public abstract class IconBase extends PotionBase {

    /**
     * 构造带图标的药水效果
     *
     * @param isBadEffectIn 是否为负面效果
     *                      - true: 红色边框
     *                      - false: 蓝色边框
     * @param liquidColorIn 药水液体颜色（ARGB格式）
     * @param name 药水注册名
     */
    protected IconBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    /**
     * 获取图标资源位置
     *
     * 子类必须实现此方法，返回图标的ResourceLocation
     *
     * 标准实现模式：
     * ```java
     * @Override
     * protected ResourceLocation getResourceLocation() {
     *     return new ResourceLocation(
     *         Reference.MOD_ID,
     *         "textures/gui/container/potion/" + getName() + ".png"
     *     );
     * }
     * ```
     *
     * 错误处理：
     * - 如果返回null，将不渲染图标
     * - 如果文件不存在，会渲染紫黑色方格（Minecraft默认）
     *
     * @return 图标的ResourceLocation，如果没有图标则返回null
     */
    protected abstract ResourceLocation getResourceLocation();

    /**
     * 在物品栏界面渲染效果图标
     *
     * 渲染位置：
     * - 玩家物品栏界面（E键打开）
     * - 效果列表的左侧
     * - 每个效果一行
     *
     * 渲染细节：
     * - 图标尺寸：18x18像素
     * - 偏移量：x+6, y+7（居中对齐）
     * - 使用自定义纹理坐标
     *
     * 注意事项：
     * - 仅在客户端调用
     * - 如果getResourceLocation()返回null，不渲染
     * - 渲染前会自动绑定纹理
     *
     * @param x 基准X坐标
     * @param y 基准Y坐标
     * @param effect 药水效果实例
     * @param minecraft Minecraft游戏实例
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft minecraft) {
        // 防御性检查：确保参数有效
        if (minecraft == null) {
            LogUtil.warn("Minecraft实例为null，无法渲染药水图标");
            return;
        }

        if (effect == null) {
            LogUtil.warn("PotionEffect实例为null，无法渲染药水图标");
            return;
        }

        // 获取图标资源位置
        ResourceLocation resourceLocation = getResourceLocation();

        if (resourceLocation != null) {
            try {
                // 绑定纹理
                minecraft.getTextureManager().bindTexture(resourceLocation);

                // 渲染图标
                // 参数说明：
                // - x+6, y+7: 渲染位置（居中偏移）
                // - 0, 0: 纹理坐标起始点
                // - 18, 18: 渲染尺寸
                // - 18, 18: 纹理尺寸
                Gui.drawModalRectWithCustomSizedTexture(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
            } catch (Exception e) {
                LogUtil.error("渲染物品栏药水图标时发生错误: " + resourceLocation, e);
            }
        } else {
            LogUtil.debug("药水效果 " + effect.getPotion().getName() + " 没有图标资源");
        }
    }

    /**
     * 在HUD（屏幕右上角）渲染效果图标
     *
     * 渲染位置：
     * - 游戏主界面右上角
     * - 紧凑排列
     * - 不显示效果名称（节省空间）
     *
     * 渲染细节：
     * - 图标尺寸：18x18像素
     * - 偏移量：x+3, y+3（紧凑对齐）
     * - 使用自定义纹理坐标
     * - 支持透明度（alpha参数）
     *
     * 注意事项：
     * - 仅在客户端调用
     * - 如果getResourceLocation()返回null，不渲染
     * - alpha参数目前未使用（可扩展）
     *
     * @param x 基准X坐标
     * @param y 基准Y坐标
     * @param effect 药水效果实例
     * @param minecraft Minecraft游戏实例
     * @param alpha 透明度（0.0-1.0，目前未使用）
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void renderHUDEffect(int x, int y, PotionEffect effect, Minecraft minecraft, float alpha) {
        // 防御性检查：确保参数有效
        if (minecraft == null) {
            LogUtil.warn("Minecraft实例为null，无法渲染HUD药水图标");
            return;
        }

        if (effect == null) {
            LogUtil.warn("PotionEffect实例为null，无法渲染HUD药水图标");
            return;
        }

        // 获取图标资源位置
        ResourceLocation resourceLocation = getResourceLocation();

        if (resourceLocation != null) {
            try {
                // 绑定纹理
                minecraft.getTextureManager().bindTexture(resourceLocation);

                // 渲染图标
                // 参数说明：
                // - x+3, y+3: 渲染位置（紧凑偏移）
                // - 0, 0: 纹理坐标起始点
                // - 18, 18: 渲染尺寸
                // - 18, 18: 纹理尺寸
                Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 18, 18, 18, 18);

                // TODO: 如果需要支持alpha透明度，可以在这里添加：
                // GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            } catch (Exception e) {
                LogUtil.error("渲染HUD药水图标时发生错误: " + resourceLocation, e);
            }
        } else {
            LogUtil.debug("药水效果 " + effect.getPotion().getName() + " 没有HUD图标资源");
        }
    }
}