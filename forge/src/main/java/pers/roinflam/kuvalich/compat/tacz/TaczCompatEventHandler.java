package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * TACZ 兼容事件处理器（由 KuvaLich 主类在检测到 TACZ 后手动注册到 Forge 事件总线）
 * TACZ compat event handler (manually registered to Forge event bus when TACZ is detected)
 * <p>
 * 本类不使用 @Mod.EventBusSubscriber，避免无 TACZ 时类加载失败。
 * <p>
 * 功能：
 * 1. 负值多重射击 → GunFireEvent 概率取消
 * 2. TACZ 枪械自带爆炸 → 设置 suppressBurstRadius
 * 3. AttachmentPropertyEvent → 修改 ADS_TIME、INACCURACY、HEADSHOT_MULTIPLIER 缓存
 */
public class TaczCompatEventHandler {

    /**
     * 在伤害结算前（NORMAL 优先级）检测 TACZ 枪击中情况，设置 bursting_radius 抑制标志。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtByTaczGun(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity shooter)) {
            WarframeTaczBridge.clearBurstRadiusSuppressed();
            return;
        }

        ItemStack gunStack = shooter.getMainHandItem();
        if (gunStack.isEmpty()) {
            WarframeTaczBridge.clearBurstRadiusSuppressed();
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            WarframeTaczBridge.clearBurstRadiusSuppressed();
            return;
        }

        if (!WeaponModuleHandler.hasBase(gunStack)) {
            WarframeTaczBridge.clearBurstRadiusSuppressed();
            return;
        }

        WarframeTaczBridge.setSuppressBurstRadius(checkTaczGunHasExplosion(iGun, gunStack, shooter));
    }

    /**
     * 检查 TACZ 枪械是否具有自带爆炸逻辑。
     */
    private boolean checkTaczGunHasExplosion(IGun iGun, ItemStack gunStack, LivingEntity shooter) {
        ResourceLocation gunId = iGun.getGunId(gunStack);
        boolean hasExplosionInData = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    ExplosionData expData = index.getGunData().getBulletData().getExplosionData();
                    return expData != null && expData.isExplode();
                })
                .orElse(false);

        if (hasExplosionInData) {
            return true;
        }

        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator == null) {
            return false;
        }

        AttachmentCacheProperty cacheProperty = operator.getCacheProperty();
        if (cacheProperty == null) {
            return false;
        }

        ExplosionData cachedExplosion = cacheProperty.getCache(GunProperties.EXPLOSION);
        return cachedExplosion != null && cachedExplosion.isExplode();
    }

    /**
     * 监听 GunFireEvent，处理负值多重射击（子弹概率消失）。
     */
    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) {
            return;
        }

        LivingEntity shooter = event.getShooter();
        ItemStack gunStack = event.getGunItemStack();

        if (gunStack == null || gunStack.isEmpty()) {
            return;
        }

        float multishotMod = WarframeTaczBridge.getMultishotMod(gunStack, shooter);
        if (multishotMod >= 0f) {
            return;
        }

        float absMod = Math.abs(multishotMod);
        if (absMod >= 1.0f) {
            event.setCanceled(true);
            return;
        }

        float vanishChance = absMod / (1.0f + absMod);
        if (Math.random() < vanishChance) {
            event.setCanceled(true);
        }
    }

    /**
     * 监听 AttachmentPropertyEvent，修改 TACZ 缓存中的 ADS_TIME、INACCURACY、HEADSHOT_MULTIPLIER。
     * <p>
     * 在 TACZ 配件属性计算完毕后触发，此时 cacheProperty 中已有配件修改后的值。
     * 本方法在此基础上叠加 KuvaLich 模组属性修改。
     */
    @SubscribeEvent
    public void onAttachmentPropertyEvent(AttachmentPropertyEvent event) {
        // 从 ThreadLocal 获取 MixinAttachmentPropertyContext 保存的上下文
        LivingEntity shooter = WarframeTaczBridge.getCacheContextShooter();
        ItemStack gunItem = WarframeTaczBridge.getCacheContextGunItem();

        // 无上下文或枪械无 KuvaLich 模组数据则跳过
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return;
        }

        AttachmentCacheProperty cacheProperty = event.getCacheProperty();
        if (cacheProperty == null) {
            return;
        }

        try {
            // ========== 瞄准时间修改 ==========
            applyAimTimeModification(cacheProperty, gunItem, shooter);

            // ========== 精准度修改（散布） ==========
            applyAccuracyModification(cacheProperty, gunItem, shooter);

            // ========== 爆头倍率修改 ==========
            applyHeadshotModification(cacheProperty, gunItem, shooter);

        } catch (Exception e) {
            LogUtil.debug("[TaczCompat] AttachmentPropertyEvent 处理异常: " + e.getMessage());
        }
    }

    /**
     * 修改缓存中的 ADS_TIME 值。
     * <p>
     * aim_time = 0.3 → ADS_TIME / 1.3（瞄准快30%）
     * aim_time = -0.3 → ADS_TIME / 0.7（瞄准慢）
     */
    private void applyAimTimeModification(AttachmentCacheProperty cacheProperty, ItemStack gunItem, LivingEntity shooter) {
        float aimTimeMod = WarframeTaczBridge.getAimTimeMod(gunItem, shooter);
        if (Math.abs(aimTimeMod) < 0.001f) {
            return;
        }

        Float originalAdsTime = cacheProperty.getCache(GunProperties.ADS_TIME);
        if (originalAdsTime == null || originalAdsTime <= 0f) {
            return;
        }

        float newAdsTime;
        if (aimTimeMod <= -1.0f) {
            newAdsTime = Float.MAX_VALUE / 2f;
        } else {
            // newAdsTime = originalAdsTime / (1 + aimTimeMod)
            newAdsTime = originalAdsTime / (1f + aimTimeMod);
        }

        newAdsTime = Math.max(newAdsTime, 0f);
        cacheProperty.setCache(GunProperties.ADS_TIME, newAdsTime);

        LogUtil.debug(String.format(
                "[瞄准速度] aim_time=%.2f, ADS_TIME=%.4f → %.4f",
                aimTimeMod, originalAdsTime, newAdsTime
        ));
    }

    /**
     * 修改缓存中的 INACCURACY Map（所有散布类型统一缩放）。
     * <p>
     * accuracy = 0.6 → 所有散布 × 0.4（降低60%）
     * accuracy = -0.3 → 所有散布 × 1.3（增大30%）
     */
    @SuppressWarnings("unchecked")
    private void applyAccuracyModification(AttachmentCacheProperty cacheProperty, ItemStack gunItem, LivingEntity shooter) {
        float accuracyMod = WarframeTaczBridge.getAccuracyMod(gunItem, shooter);
        if (Math.abs(accuracyMod) < 0.001f) {
            return;
        }

        Map<InaccuracyType, Float> originalInaccuracy = cacheProperty.getCache(GunProperties.INACCURACY);
        if (originalInaccuracy == null || originalInaccuracy.isEmpty()) {
            return;
        }

        // 缩放因子 = max(0, 1 - accuracy)
        float scaleFactor = Math.max(0f, 1f - accuracyMod);

        // 创建新 Map 避免修改原始引用
        HashMap<InaccuracyType, Float> modifiedInaccuracy = new HashMap<>();
        for (Map.Entry<InaccuracyType, Float> entry : originalInaccuracy.entrySet()) {
            float newValue = entry.getValue() * scaleFactor;
            modifiedInaccuracy.put(entry.getKey(), Math.max(newValue, 0f));
        }

        cacheProperty.setCache(GunProperties.INACCURACY, modifiedInaccuracy);

        LogUtil.debug(String.format(
                "[精准度] accuracy=%.2f, scaleFactor=%.2f, 散布已缩放",
                accuracyMod, scaleFactor
        ));
    }

    /**
     * 修改缓存中的 HEADSHOT_MULTIPLIER 值。
     * <p>
     * headshot_damage = 0.5 → 爆头倍率 × 1.5（爆头伤害+50%）
     * headshot_damage = -0.3 → 爆头倍率 × 0.7（爆头伤害-30%）
     * <p>
     * 公式：newMultiplier = originalMultiplier * (1 + headshot_damage)
     */
    private void applyHeadshotModification(AttachmentCacheProperty cacheProperty, ItemStack gunItem, LivingEntity shooter) {
        float headshotMod = WarframeTaczBridge.getHeadshotDamageMod(gunItem, shooter);
        if (Math.abs(headshotMod) < 0.001f) {
            return;
        }

        Float originalMultiplier = cacheProperty.getCache(GunProperties.HEADSHOT_MULTIPLIER);
        if (originalMultiplier == null || originalMultiplier <= 0f) {
            return;
        }

        // newMultiplier = originalMultiplier * (1 + headshotMod)
        float newMultiplier = originalMultiplier * (1f + headshotMod);

        // 爆头倍率不可低于 0
        newMultiplier = Math.max(newMultiplier, 0f);
        cacheProperty.setCache(GunProperties.HEADSHOT_MULTIPLIER, newMultiplier);

        LogUtil.debug(String.format(
                "[爆头倍率] headshot_damage=%.2f, HEADSHOT_MULTIPLIER=%.4f → %.4f",
                headshotMod, originalMultiplier, newMultiplier
        ));
    }
}