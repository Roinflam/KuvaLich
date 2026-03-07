package pers.roinflam.kuvalich.compat.tacz.client;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 装填动画速度缩放器（客户端专用）。
 * Reload animation speed scaler (client-only).
 * <p>
 * 当玩家正在装填或拉栓，且枪械装有 KuvaLich reload_speed 模组时，
 * 返回对应的动画播放速度倍率，使动画与逻辑同步加速/减速。
 * When player is reloading or bolting and gun has KuvaLich reload_speed module,
 * returns corresponding animation playback speed multiplier.
 * <p>
 * 加速：reload_speed = +0.5 → scale = 1.5 → 动画 1.5 倍速，装填 1.5 倍速完成
 * 减速：reload_speed = -0.3 → scale = 0.7 → 动画 0.7 倍速，装填 0.7 倍速完成
 * 极端：reload_speed = -1.0 → scale ≈ 0 → 动画接近暂停，装填永远完不成
 * <p>
 * 由 MixinObjectAnimationRunner 在客户端动画系统中调用，
 * 通过 TimeTracker 缩放时间流逝实现动画快进/慢放。
 */
@OnlyIn(Dist.CLIENT)
public class AnimationSpeedScaler {

    /**
     * 获取当前动画播放速度缩放倍率。
     * Get current animation playback speed scale.
     * <p>
     * 生效条件：玩家正在装填（feed/cooldown 阶段）或拉栓（bolting 阶段）。
     * 其他状态（射击、瞄准、待机等）返回 1.0，不影响对应动画。
     * <p>
     * Active conditions: player is reloading (feed/cooldown phase) or bolting.
     * Returns 1.0 for all other states (shooting, aiming, idle), leaving those animations unaffected.
     *
     * @return 动画速度倍率（1.0 = 正常，>1 加速，<1 减速）
     */
    public static double getAnimationSpeedScale() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 1.0;
        }

        // 获取枪械操作接口
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);

        // 检查是否正在装填
        ReloadState reloadState = gunOperator.getSynReloadState();
        boolean isReloading = reloadState.getStateType() != ReloadState.StateType.NOT_RELOADING;

        // 检查是否正在拉栓
        boolean isBolting = gunOperator.getSynIsBolting();

        // 非装填且非拉栓状态，不缩放动画
        if (!isReloading && !isBolting) {
            return 1.0;
        }

        // 获取主手枪械
        ItemStack gunStack = player.getMainHandItem();
        if (gunStack.isEmpty() || !(gunStack.getItem() instanceof IGun)) {
            return 1.0;
        }

        // 从 KuvaLich 模组系统读取 reload_speed 属性
        float reloadSpeedMod = WarframeTaczBridge.getReloadSpeedMod(gunStack, player);

        // 无修正时正常速度
        if (Math.abs(reloadSpeedMod) < 0.001f) {
            return 1.0;
        }

        // reload_speed <= -1.0：完全无法装填，动画接近暂停
        // 不能返回 0 或负数，否则 TimeTracker 时间会停止或倒流
        if (reloadSpeedMod <= -1.0f) {
            return 0.001;
        }

        // 动画速度 = (1 + reload_speed)
        // +0.5 → 1.5 倍速（加速）
        // -0.3 → 0.7 倍速（减速）
        return 1.0 + reloadSpeedMod;
    }

    /**
     * 时间追踪器，用于缩放动画系统的时间流逝。
     * Time tracker for scaling animation system's time passage.
     * <p>
     * 核心原理：维护两条时间轴——真实时间轴（lastUnscaled）和虚拟时间轴（lastScaled）。
     * 每次调用 updateAndGet 时：
     *   1. 计算真实时间增量：deltaUnscaled = original - lastUnscaled
     *   2. 缩放增量并累加到虚拟时间：lastScaled += deltaUnscaled × scale
     *   3. 返回虚拟时间
     * <p>
     * 动画系统用虚拟时间计算帧增量 → 增量被缩放 → 动画进度按倍率推进。
     * <p>
     * 重要：同一 ObjectAnimationRunner 实例的所有 nanoTime() 调用
     * 必须经过同一个 TimeTracker 实例，确保 lastUpdateNs 和 currentNs
     * 处于同一缩放时间空间，增量计算才正确。
     * <p>
     * 当 scale = 1.0 时，虚拟时间 = 真实时间，无计算误差。
     */
    public static abstract sealed class TimeTracker {

        /**
         * 创建毫秒级时间追踪器（对应 System.currentTimeMillis）
         *
         * @return 毫秒级 TimeTracker
         */
        public static TimeTracker createMillisTracker() {
            return new Millis();
        }

        /**
         * 创建纳秒级时间追踪器（对应 System.nanoTime）
         *
         * @return 纳秒级 TimeTracker
         */
        public static TimeTracker createNanosTracker() {
            return new Nanos();
        }

        /** 上次未缩放的真实时间 */
        private long lastUnscaled;

        /** 上次缩放后的虚拟时间 */
        private long lastScaled;

        public TimeTracker() {
            lastUnscaled = lastScaled = getUnscaledCurrentTime();
        }

        /**
         * 更新并返回缩放后的时间。
         * <p>
         * 例：scale=1.5，上次真实时间=1000，当前真实时间=1100
         *   → deltaUnscaled = 100
         *   → lastScaled += 100 × 1.5 = 150
         *   → 返回的虚拟时间比真实多走了 50，动画多播了 50% 的进度
         *
         * @param original 当前真实时间（来自 System.nanoTime）
         * @param scale    缩放系数（>1 加速，<1 减速，=1 不变）
         * @return 缩放后的虚拟时间
         */
        public long updateAndGet(long original, double scale) {
            long deltaUnscaled = original - lastUnscaled;
            lastUnscaled += deltaUnscaled;
            lastScaled += (long) (deltaUnscaled * scale);
            return lastScaled;
        }

        /**
         * 获取当前未缩放的真实时间
         *
         * @return 真实时间
         */
        protected abstract long getUnscaledCurrentTime();

        /** 毫秒级实现（System.currentTimeMillis） */
        public static final class Millis extends TimeTracker {
            @Override
            public long getUnscaledCurrentTime() {
                return System.currentTimeMillis();
            }
        }

        /** 纳秒级实现（System.nanoTime） */
        public static final class Nanos extends TimeTracker {
            @Override
            public long getUnscaledCurrentTime() {
                return System.nanoTime();
            }
        }
    }
}