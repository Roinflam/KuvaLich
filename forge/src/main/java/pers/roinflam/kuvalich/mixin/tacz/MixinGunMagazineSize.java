// 路径：forge/src/main/java/pers/roinflam/kuvalich/mixin/tacz/MixinGunMagazineSize.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 AttachmentDataUtils.getAmmoCountWithAttachment，实现 Warframe 弹夹容量模组。
 * <p>
 * 该静态方法是 TACZ 计算枪械最大弹药数的唯一入口，
 * 所有装填逻辑、HUD 显示、弹药检查均通过此方法获取弹夹容量。
 * 修改其返回值即可全局生效。
 * <p>
 * magazine_size 语义：增量百分比（0.5 = +50% → 弹夹容量提升50%）
 * 公式：最终容量 = 原始容量 × (1 + magazine_size)，最低为 1
 * <p>
 * 例：原始 30 发，magazine_size = 0.5 → 30 × 1.5 = 45 发
 *     原始 30 发，magazine_size = -0.3 → 30 × 0.7 = 21 发
 */
@Mixin(targets = "com.tacz.guns.util.AttachmentDataUtils", remap = false)
public class MixinGunMagazineSize {

    /**
     * 修改 getAmmoCountWithAttachment 返回值，叠加 KuvaLich 弹夹容量模组。
     * <p>
     * 方法签名：public static int getAmmoCountWithAttachment(ItemStack gunItem, GunData gunData)
     * MixinExtras 的 @ModifyReturnValue 支持注入静态方法并捕获原始参数。
     *
     * @param original 原始弹夹容量（已含 TACZ 配件扩容）
     * @param gunItem  枪械 ItemStack（第一个参数，用于读取 KuvaLich 模组属性）
     * @param gunData  枪械数据（第二个参数）
     * @return 叠加 KuvaLich 弹夹容量后的最终容量
     */
    @ModifyReturnValue(method = "getAmmoCountWithAttachment", at = @At("RETURN"), require = 0)
    private static int applyWarframeMagazineSize(int original, ItemStack gunItem, GunData gunData) {
        if (gunItem == null || gunItem.isEmpty()) {
            return original;
        }

        float magazineSizeMod = WarframeTaczBridge.getMagazineSizeMod(gunItem);

        // 无修正时直接返回
        if (Math.abs(magazineSizeMod) < 0.001f) {
            return original;
        }

        // 计算新容量，至少为 1
        int result = (int) (original * (1f + magazineSizeMod));
        return Math.max(result, 1);
    }
}