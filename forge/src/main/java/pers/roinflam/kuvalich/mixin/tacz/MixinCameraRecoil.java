// 路径：forge/src/main/java/pers/roinflam/kuvalich/mixin/tacz/MixinCameraRecoil.java
package pers.roinflam.kuvalich.mixin.tacz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 CameraSetupEvent.initialCameraRecoil（客户端事件处理），实现 Warframe 后坐力降低模组。
 * <p>
 * 【必须在客户端 Mixin 配置中注册，服务端不可加载本类】
 * <p>
 * TACZ 后坐力计算链路（CameraSetupEvent.initialCameraRecoil）：
 *   1. attachmentRecoilModifier = cacheProperty.getCache(RecoilModifier.ID)  // 配件修正
 *   2. aimingRecoilModifier = 瞄准/趴下等状态修正
 *   3. pitchSpline = recoil.genPitchSplineFunction(attachmentMod.eval(aimingMod))
 *   4. yawSpline = recoil.genYawSplineFunction(attachmentMod.eval(aimingMod))
 * <p>
 * genPitchSplineFunction / genYawSplineFunction 的 float 参数即后坐力总倍率，
 * 通过 @ModifyArg 拦截该参数，乘以 (1 - recoil_reduction) 实现后坐力缩放。
 * <p>
 * recoil_reduction 语义：增量百分比（0.6 = +60% → 后坐力降低60%）
 * 公式：最终倍率 = 原始倍率 × (1 - recoil_reduction)，最低为 0
 * <p>
 * 因本方法是 @SubscribeEvent 静态方法，且必定在本地玩家上下文中执行，
 * 可安全通过 Minecraft.getInstance().player 获取玩家和枪械引用。
 */
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public class MixinCameraRecoil {

    /**
     * 修改传入 genPitchSplineFunction 的 modifier 参数（俯仰后坐力）。
     *
     * @param modifier TACZ 计算的俯仰后坐力倍率（含配件、瞄准、趴下修正）
     * @return 叠加 KuvaLich 后坐力降低后的最终俯仰倍率
     */
    @ModifyArg(
            method = "initialCameraRecoil",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genPitchSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"
            ),
            index = 0,
            require = 0
    )
    private static float applyWarframePitchRecoilReduction(float modifier) {
        return applyRecoilReduction(modifier);
    }

    /**
     * 修改传入 genYawSplineFunction 的 modifier 参数（偏航后坐力）。
     *
     * @param modifier TACZ 计算的偏航后坐力倍率（含配件、瞄准、趴下修正）
     * @return 叠加 KuvaLich 后坐力降低后的最终偏航倍率
     */
    @ModifyArg(
            method = "initialCameraRecoil",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genYawSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"
            ),
            index = 0,
            require = 0
    )
    private static float applyWarframeYawRecoilReduction(float modifier) {
        return applyRecoilReduction(modifier);
    }

    /**
     * 后坐力降低计算共用逻辑。
     * <p>
     * 从本地玩家主手物品读取 KuvaLich recoil_reduction 属性，
     * 缩放后坐力倍率。
     * <p>
     * initialCameraRecoil 事件处理已保证：
     * - player != null
     * - mainHandItem 是 IGun
     * - 在客户端 GunFireEvent 上下文中
     * 因此此处安全获取 player。
     *
     * @param modifier 原始后坐力倍率
     * @return 修正后的后坐力倍率
     */
    private static float applyRecoilReduction(float modifier) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return modifier;
        }

        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) {
            return modifier;
        }

        float recoilReductionMod = WarframeTaczBridge.getRecoilReductionMod(gunStack, player);

        // 无修正时直接返回
        if (Math.abs(recoilReductionMod) < 0.001f) {
            return modifier;
        }

        // 最终倍率 = 原始倍率 × (1 - recoilReduction)
        // recoilReduction = 0.6 → 乘以 0.4 → 后坐力降低60%
        // recoilReduction = -0.3 → 乘以 1.3 → 后坐力增加30%
        float result = modifier * (1f - recoilReductionMod);

        // 后坐力倍率不可低于 0
        return Math.max(result, 0f);
    }
}