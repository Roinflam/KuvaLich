// TaczCompatEventHandler.java
// forge/src/main/java/pers/roinflam/kuvalich/compat/tacz/TaczCompatEventHandler.java
package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.modifier.custom.DamageModifier;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
 * 5. 击杀叠层实时追踪 → 射速/多重射击叠层变化时立即刷新 TACZ 缓存
 * 6. TACZ枪械永久强化 → AttachmentPropertyEvent 独立处理伤害缓存 + Tooltip显示
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

    // ========== 击杀叠层实时追踪 / Kill Stack Real-time Tracking ==========

    /**
     * 记录每个玩家上一次检测到的击杀叠层值。
     * <p>
     * 用于检测射速（FIRING_RATE）和多重射击（MULTISHOT）的击杀叠层变化，
     * 变化时立即触发 TACZ 缓存刷新，使 Mixin 拦截的 getShootInterval / shootOnce
     * 在 TACZ 脚本重新初始化后能读取到最新的叠层加成。
     * <p>
     * int[0] = FIRING_RATE 叠层数
     * int[1] = MULTISHOT 叠层数
     */
    private static final Map<UUID, int[]> lastKillStackValues = new HashMap<>();

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
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

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
     *
     * @param event 玩家重生事件
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

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
     * 玩家 Tick 事件处理：包含两层刷新机制
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

        UUID uuid = player.getUUID();

        // ═══ 第一层：击杀叠层实时追踪（每 tick 执行，开销极低） ═══
        ItemStack gunStack = player.getMainHandItem();
        if (!gunStack.isEmpty()) {
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun != null && WeaponModuleHandler.hasBase(gunStack)) {
                int currentFiringRateStacks = KillStackManager.getStacks(player, KillStackManager.StackType.FIRING_RATE);
                int currentMultishotStacks = KillStackManager.getStacks(player, KillStackManager.StackType.MULTISHOT);

                int[] lastValues = lastKillStackValues.get(uuid);
                boolean stacksChanged;
                if (lastValues == null) {
                    stacksChanged = (currentFiringRateStacks > 0 || currentMultishotStacks > 0);
                } else {
                    stacksChanged = (lastValues[0] != currentFiringRateStacks || lastValues[1] != currentMultishotStacks);
                }

                if (stacksChanged) {
                    triggerTaczCacheRefresh(player);
                    lastKillStackValues.put(uuid, new int[]{currentFiringRateStacks, currentMultishotStacks});
                    lastCacheRefreshTick.put(uuid, player.level().getGameTime());

                    LogUtil.debug(String.format(
                            "[TaczCompat] 击杀叠层变化，立即刷新: firingRate=%d, multishot=%d, 玩家=%s",
                            currentFiringRateStacks, currentMultishotStacks, player.getName().getString()
                    ));
                }
            }
        }

        // ═══ 第二层：常规周期性刷新（每 100 tick，兜底机制） ═══
        long currentTick = player.level().getGameTime();

        if (currentTick % 20 != 0) {
            return;
        }

        Long lastRefresh = lastCacheRefreshTick.get(uuid);

        if (lastRefresh != null && (currentTick - lastRefresh) < CACHE_REFRESH_INTERVAL) {
            return;
        }

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

        triggerTaczCacheRefresh(player);
        lastCacheRefreshTick.put(uuid, currentTick);
    }

    /**
     * 触发 TACZ 配件属性缓存刷新。
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
     * 玩家退出时清理所有缓存数据，防止内存泄漏。
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        lastCacheRefreshTick.remove(uuid);
        lastKillStackValues.remove(uuid);
    }

    // ========== AttachmentPropertyEvent 处理（KuvaLich模组属性） ==========

    /**
     * 监听 AttachmentPropertyEvent，修改 TACZ 缓存中的 ADS_TIME、INACCURACY、HEADSHOT_MULTIPLIER。
     * <p>
     * 仅处理装有KuvaLich模组系统（hasBase）的枪械。
     * 与枪械永久强化（onGunEnhancePropertyEvent）完全独立。
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

        float scaleFactor = Math.max(0f, 1f - accuracyMod);

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

        float newMultiplier = originalMultiplier * (1f + headshotMod);

        newMultiplier = Math.max(newMultiplier, 0f);
        cacheProperty.setCache(GunProperties.HEADSHOT_MULTIPLIER, newMultiplier);

        LogUtil.debug(String.format(
                "[爆头倍率] headshot_damage=%.2f, HEADSHOT_MULTIPLIER=%.4f → %.4f",
                headshotMod, originalMultiplier, newMultiplier
        ));
    }

    /**
     * 为TACZ枪械添加玄骸强化信息Tooltip
     * <p>显示格式：§4✦ 玄骸之力 §c×3 §4(§c伤害+75%§4)</p>
     *
     * @param event 物品Tooltip事件
     */
    @SubscribeEvent
    public void onTaczGunTooltip(ItemTooltipEvent event) {
        if (!ModConfig.KUVA_LICH.taczGunEnhanceEnable.get()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        // 仅处理TACZ枪械
        if (!TaczGunEnhanceUtil.isTaczGun(stack)) {
            return;
        }
        int enhanceCount = TaczGunEnhanceUtil.getEnhanceCount(stack);
        if (enhanceCount <= 0) {
            return;
        }
        double totalPercent = TaczGunEnhanceUtil.getTotalEnhancePercent(stack);
        List<Component> tooltip = event.getToolTip();
        // 单行显示：§4✦ 玄骸之力 §c×3 §4(§c伤害+75%§4)
        tooltip.add(1, Component.translatable("tooltip.kuvalich.gun_enhance.info",
                enhanceCount, String.format("+%.0f%%", totalPercent * 100)));
    }
}