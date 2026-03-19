package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 * 4. 玩家登录/重生/周期性 → 强制刷新 TACZ 缓存，确保 KuvaLich 属性及时生效
 */
public class TaczCompatEventHandler {

    // ========== TACZ 缓存刷新追踪 / TACZ Cache Refresh Tracking ==========

    /**
     * 记录每个玩家上次 TACZ 缓存刷新的 gameTick。
     * 用于避免每 tick 都刷新，只在间隔到期后刷新一次。
     */
    private static final Map<UUID, Long> lastCacheRefreshTick = new HashMap<>();

    /** TACZ 缓存刷新间隔（tick）：100 tick = 5 秒 */
    private static final long CACHE_REFRESH_INTERVAL = 100L;

    // ========== 伤害事件 / Damage Events ==========

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
     *
     * @param iGun     枪械接口
     * @param gunStack 枪械物品栈
     * @param shooter  射击者
     * @return 是否有爆炸
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

    // ========== 射击事件 / Gun Fire Events ==========

    /**
     * 监听 GunFireEvent，处理负值多重射击（子弹概率消失）。
     *
     * @param event 枪械射击事件
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

    // ========== TACZ 缓存主动刷新 / TACZ Cache Proactive Refresh ==========

    /**
     * 玩家登录时延迟刷新 TACZ 缓存。
     * <p>
     * 解决问题：玩家登录时枪械已在手中，TACZ 在登录过程中初始化了枪械脚本，
     * 但此时 KuvaLich 的模组属性尚未被 TACZ 缓存纳入计算。
     * 延迟 1 秒（20 tick）后触发 postChangeEvent，强制 TACZ 重新计算配件属性缓存，
     * 使 KuvaLich 的 ADS_TIME、INACCURACY、HEADSHOT_MULTIPLIER 等修改生效。
     * <p>
     * 同时也会触发 TACZ 内部重新初始化枪械脚本状态，
     * 使 getShootInterval 等被 Mixin 拦截的方法有机会读取到最新 KuvaLich 属性。
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        // 延迟 20 tick（1秒）后刷新，确保所有数据已加载完毕
        // period=1 避免 SynchronizationTask 底层 Timer 因 period=0 抛异常，
        // run 内第一行 cancel() 保证只执行一次
        new SynchronizationTask(20, 1) {
            @Override
            public void run() {
                this.cancel();

                if (!player.isAlive() || player.isRemoved()) {
                    return;
                }

                triggerTaczCacheRefresh(player);
                LogUtil.debug("[TaczCompat] 玩家登录后 TACZ 缓存已刷新: " + player.getName().getString());
            }
        }.start();
    }

    /**
     * 玩家重生时刷新 TACZ 缓存。
     * <p>
     * 重生后物品栏恢复（keepInventory 或墓碑模组），TACZ 脚本可能使用旧的缓存数据，
     * 需要刷新以应用 KuvaLich 属性。
     *
     * @param event 玩家重生事件
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        // 延迟 10 tick（0.5秒）后刷新
        // period=1 避免 SynchronizationTask 底层 Timer 因 period=0 抛异常，
        // run 内第一行 cancel() 保证只执行一次
        new SynchronizationTask(10, 1) {
            @Override
            public void run() {
                this.cancel();

                if (!player.isAlive() || player.isRemoved()) {
                    return;
                }

                triggerTaczCacheRefresh(player);
                LogUtil.debug("[TaczCompat] 玩家重生后 TACZ 缓存已刷新: " + player.getName().getString());
            }
        }.start();
    }

    /**
     * 周期性检查并刷新 TACZ 缓存。
     * <p>
     * 每 5 秒检查一次持有 TACZ 枪械且装载了 KuvaLich 模组的玩家，
     * 触发 postChangeEvent 强制刷新 TACZ 配件属性缓存。
     * <p>
     * 必要性：
     * - 击杀叠层（killStack）随时间变化，影响 aim_time、accuracy 等通过缓存修改的属性
     * - 模组热更换后需要刷新
     * - 作为登录刷新的兜底机制，防止任何边界情况导致属性不同步
     *
     * @param event 玩家 Tick 事件
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }

        long currentTick = player.level().getGameTime();

        // 使用全局 tick 对齐，减少 HashMap 查询频率（每秒才检查一次）
        if (currentTick % 20 != 0) {
            return;
        }

        UUID uuid = player.getUUID();
        Long lastRefresh = lastCacheRefreshTick.get(uuid);

        // 未到刷新间隔则跳过
        if (lastRefresh != null && (currentTick - lastRefresh) < CACHE_REFRESH_INTERVAL) {
            return;
        }

        // 检查是否持有带 KuvaLich 模组的 TACZ 枪械
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) {
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return;
        }

        if (!WeaponModuleHandler.hasBase(gunStack)) {
            return;
        }

        // 触发 TACZ 缓存刷新
        triggerTaczCacheRefresh(player);
        lastCacheRefreshTick.put(uuid, currentTick);
    }

    /**
     * 触发 TACZ 配件属性缓存刷新。
     * <p>
     * 调用 AttachmentPropertyManager.postChangeEvent 会：
     * 1. 触发 TACZ 完整的配件属性重新计算流程
     * 2. 发送 AttachmentPropertyEvent 事件（被本类的 onAttachmentPropertyEvent 捕获）
     * 3. 我们在事件中叠加 KuvaLich 的 aim_time、accuracy、headshot_damage 修改
     * <p>
     * 同时，MixinAttachmentPropertyContext（HEAD注入）会在 postChangeEvent 开头
     * 保存 shooter 和 gunItem 到 ThreadLocal，确保事件处理器能读取到正确的上下文。
     *
     * @param player 持枪玩家
     */
    private void triggerTaczCacheRefresh(Player player) {
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty()) {
            return;
        }

        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return;
        }

        try {
            AttachmentPropertyManager.postChangeEvent(player, gunStack);
        } catch (Exception e) {
            LogUtil.debug("[TaczCompat] TACZ 缓存刷新异常: " + e.getMessage());
        }
    }

    /**
     * 玩家退出时清理缓存刷新记录，防止内存泄漏。
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        lastCacheRefreshTick.remove(event.getEntity().getUUID());
    }

    // ========== AttachmentPropertyEvent 处理 / AttachmentPropertyEvent Handling ==========

    /**
     * 监听 AttachmentPropertyEvent，修改 TACZ 缓存中的 ADS_TIME、INACCURACY、HEADSHOT_MULTIPLIER。
     * <p>
     * 在 TACZ 配件属性计算完毕后触发，此时 cacheProperty 中已有配件修改后的值。
     * 本方法在此基础上叠加 KuvaLich 模组属性修改。
     *
     * @param event 配件属性事件
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
     *
     * @param cacheProperty TACZ 配件缓存属性
     * @param gunItem       枪械物品栈
     * @param shooter       射击者
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
     *
     * @param cacheProperty TACZ 配件缓存属性
     * @param gunItem       枪械物品栈
     * @param shooter       射击者
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
     *
     * @param cacheProperty TACZ 配件缓存属性
     * @param gunItem       枪械物品栈
     * @param shooter       射击者
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