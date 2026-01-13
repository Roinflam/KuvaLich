package pers.roinflam.kuvalich.utils.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.ReflectionCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

/**
 * 实体生物工具类
 * 提供攻击蓄力追踪、跳跃控制、物品使用加速等功能
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EntityLivingUtil {

    /**
     * 当前tick的攻击蓄力值
     */
    private static final HashMap<UUID, Float> NOW_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();

    /**
     * 上一tick的攻击蓄力值
     */
    private static final HashMap<UUID, Float> LAST_TICKS_SINCE_LAST_SWING_LIST = new HashMap<>();

    /**
     * 玩家Tick事件 - 记录攻击蓄力值
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START) {
            UUID uuid = evt.player.getUUID();
            LAST_TICKS_SINCE_LAST_SWING_LIST.put(uuid, NOW_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(uuid, 1f));
            NOW_TICKS_SINCE_LAST_SWING_LIST.put(uuid, evt.player.getAttackStrengthScale(0));
        }
    }

    /**
     * 玩家登出事件 - 清理数据
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        UUID uuid = evt.getEntity().getUUID();
        NOW_TICKS_SINCE_LAST_SWING_LIST.remove(uuid);
        LAST_TICKS_SINCE_LAST_SWING_LIST.remove(uuid);
    }

    /**
     * 获取上一tick的攻击蓄力值
     *
     * @param player 玩家
     * @return 攻击蓄力值（0.0-1.0）
     */
    public static float getTicksSinceLastSwing(@Nonnull Player player) {
        return LAST_TICKS_SINCE_LAST_SWING_LIST.getOrDefault(player.getUUID(), 1f);
    }

    /**
     * 设置实体已跳跃状态（阻止连续跳跃）
     *
     * @param livingEntity 生物实体
     */
    public static void setJumped(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.setJumpTicks(livingEntity, 10);
    }

    /**
     * 加速物品使用进度
     * 通过减少 useItemRemaining 来加速
     *
     * @param livingEntity 生物实体
     * @param ticks        要加速的tick数
     */
    public static void accelerateItemUse(@Nonnull LivingEntity livingEntity, int ticks) {
        ReflectionCache.reduceUseItemRemaining(livingEntity, ticks);
    }

    /**
     * 更新手持物品使用状态（对应1.12的updateActiveHand）
     * 通过反射调用updatingUsingItem方法来触发额外的物品使用tick
     * <p>
     * 这个方法会：
     * 1. 检查是否正在使用物品
     * 2. 触发物品的onUseTick
     * 3. 减少useItemRemaining计数
     * 4. 触发使用效果（粒子、声音等）
     *
     * @param livingEntity 生物实体
     */
    public static void updateHeld(@Nonnull LivingEntity livingEntity) {
        ReflectionCache.invokeUpdatingUsingItem(livingEntity);
    }

    /**
     * 强制杀死实体
     *
     * @param livingEntity 要杀死的实体
     * @param damageSource 伤害源
     */
    public static void kill(@Nullable LivingEntity livingEntity, @Nonnull DamageSource damageSource) {
        if (livingEntity != null) {
            livingEntity.hurt(damageSource, livingEntity.getMaxHealth() * 100);
            if (livingEntity.isAlive()) {
                livingEntity.die(damageSource);
                livingEntity.setHealth(0);
            }
        }
    }
}