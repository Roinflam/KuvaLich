package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 ModernKineticGunScriptAPI.shootOnce，检测满弹夹第一发射击。
 * Inject into shootOnce to detect first bullet from full magazine.
 * <p>
 * 在 shootOnce 方法头部判断当前弹药数是否等于最大弹夹容量：
 *   - 满弹夹 → 读取 first_bullet_damage 属性，写入 ThreadLocal
 *   - 非满弹夹 → 清除 ThreadLocal
 * <p>
 * 在 shootOnce 方法返回时清除 ThreadLocal，防止泄漏到下一次射击。
 * <p>
 * ThreadLocal 值由 MixinFirstBulletDamage 在 EntityKineticBullet 构造器中读取，
 * 直接修改子弹的 damageAmount（基础伤害）。
 * <p>
 * 散弹枪：shootOnce 内 for 循环创建多颗子弹，全部读到同一个 ThreadLocal 值，
 * 因此满弹夹第一次射击的所有弹丸均获得加成。符合直觉——一次扣扳机即一次射击。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinFirstBulletDetect {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * shootOnce 方法头部：检测是否满弹夹射击，设置 ThreadLocal。
     * <p>
     * 判定逻辑：当前弹药数 >= 最大弹夹容量（含 TACZ 配件扩容 + KuvaLich 弹夹模组）。
     * 使用 >= 而非 == 以兼容弹药被外部系统增加到超过上限的极端情况。
     *
     * @param isAiming shootOnce 的参数（是否瞄准状态）
     * @param ci       CallbackInfo
     */
    @Inject(method = "shootOnce", at = @At("HEAD"), require = 0)
    private void detectFirstBullet(boolean isAiming, CallbackInfo ci) {
        // 默认清除，确保每次射击重新判定
        WarframeTaczBridge.clearFirstBulletDamageBonus();

        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // 先读取模组属性，无模组直接返回（避免无谓的弹药查询开销）
        float firstBulletMod = WarframeTaczBridge.getFirstBulletDamageMod(itemStack, shooter);
        if (firstBulletMod <= 0f) {
            return;
        }

        IGun iGun = IGun.getIGunOrNull(itemStack);
        if (iGun == null) {
            return;
        }

        // 获取当前弹药数
        int currentAmmo = iGun.getCurrentAmmoCount(itemStack);

        // 获取最大弹夹容量（含 TACZ 配件 + KuvaLich 弹夹模组，因为 MixinGunMagazineSize 已注入）
        ResourceLocation gunId = iGun.getGunId(itemStack);
        int maxAmmo = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> AttachmentDataUtils.getAmmoCountWithAttachment(itemStack, index.getGunData()))
                .orElse(0);

        // 满弹夹 → 设置第一发加成
        if (maxAmmo > 0 && currentAmmo >= maxAmmo) {
            WarframeTaczBridge.setFirstBulletDamageBonus(firstBulletMod);
        }
    }

    /**
     * shootOnce 方法返回时清除 ThreadLocal，防止泄漏到后续射击。
     * <p>
     * 即使 shootOnce 内部抛异常，Mixin 的 RETURN 注入也会在正常返回路径上清除。
     * 异常路径由 Java 异常处理机制保证不会执行后续射击，因此不会产生误判。
     *
     * @param isAiming shootOnce 的参数
     * @param ci       CallbackInfo
     */
    @Inject(method = "shootOnce", at = @At("RETURN"), require = 0)
    private void clearFirstBulletFlag(boolean isAiming, CallbackInfo ci) {
        WarframeTaczBridge.clearFirstBulletDamageBonus();
    }
}