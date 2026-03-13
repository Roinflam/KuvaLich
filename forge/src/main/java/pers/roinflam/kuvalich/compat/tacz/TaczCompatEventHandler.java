package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;

/**
 * TACZ 兼容事件处理器（由 KuvaLich 主类在检测到 TACZ 后手动注册到 Forge 事件总线）
 * TACZ compat event handler (manually registered to Forge event bus when TACZ is detected)
 * <p>
 * 本类不使用 @Mod.EventBusSubscriber，避免无 TACZ 时类加载失败。
 * <p>
 * 功能：
 * 1. 负值多重射击 → GunFireEvent 概率取消
 * 2. TACZ 枪械自带爆炸 + bursting_radius 模组 →
 *    爆炸半径放大由 MixinTaczBulletExplosion 在子弹构造时直接写入 explosionRadius 字段；
 *    此处仅设置 suppressBurstRadius，防止 WeaponModuleHandler 额外触发 bursting_radius AOE
 * 3. TACZ 枪械无爆炸 → 完全不干预
 */
public class TaczCompatEventHandler {

    /**
     * 在伤害结算前（NORMAL 优先级）检测 TACZ 枪击中情况，设置 bursting_radius 抑制标志。
     * <p>
     * 执行顺序：本方法（NORMAL）先于 WeaponModuleHandler.onLivingHurt（LOWEST）运行。
     * <p>
     * 有爆炸 → 设置 suppressBurstRadius = true，阻止 WeaponModuleHandler 重复 AOE；
     * 爆炸半径放大已由 MixinTaczBulletExplosion 在子弹创建时完成，此处无需处理。
     * <p>
     * 无爆炸 → 清除标志，与原来完全一致。
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

        // 有爆炸 → 抑制 WeaponModuleHandler 的 bursting_radius AOE（爆炸半径已在子弹创建时放大）
        // Has explosion → suppress WeaponModuleHandler's bursting_radius AOE (radius already enlarged at bullet spawn)
        // 无爆炸 → 清除标志，完全不干预
        // No explosion → clear flag, no intervention
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
}