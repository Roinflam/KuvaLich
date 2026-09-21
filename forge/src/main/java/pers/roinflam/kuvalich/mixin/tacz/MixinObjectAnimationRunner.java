package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.client.AnimationSpeedScaler;

import java.util.Locale;

/**
 * 注入 ObjectAnimationRunner，实现装填动画速度缩放（客户端专用）。
 * Inject into ObjectAnimationRunner to scale reload animation speed (client-only).
 * <p>
 * 【必须在客户端 Mixin 配置的 "client" 数组中注册】
 * <p>
 * 原理：ObjectAnimationRunner 通过 System.nanoTime() 计算动画帧间增量（alphaProgress），
 * 驱动动画进度推进。通过 TimeTracker 缩放所有 nanoTime 调用的返回值，
 * 使动画系统认为时间过得更快/更慢，从而实现动画播放速度缩放。
 * <p>
 * Principle: ObjectAnimationRunner uses System.nanoTime() to compute frame delta (alphaProgress)
 * which drives animation progress. By scaling all nanoTime return values through TimeTracker,
 * the animation system perceives time passing faster/slower, achieving animation speed scaling.
 * <p>
 * 关键方法调用链：
 *   run()            → lastUpdateNs = System.nanoTime()           // 初始化基准时间
 *   update()         → currentNs = System.nanoTime()              // 计算帧增量
 *                       alphaProgress = currentNs - lastUpdateNs
 *                       updateProgress(alphaProgress)              // 推进动画进度
 *   updateSoundOnly()→ 同上，仅更新音效                             // 同理
 * <p>
 * 所有 nanoTime() 调用必须经过同一个 TimeTracker 实例，
 * 确保 lastUpdateNs 和 currentNs 处于同一缩放时间空间，增量计算正确。
 * All nanoTime() calls must go through the same TimeTracker instance,
 * ensuring lastUpdateNs and currentNs are in the same scaled time space.
 */
@Mixin(targets = "com.tacz.guns.api.client.animation.ObjectAnimationRunner", remap = false)
public class MixinObjectAnimationRunner {

    /**
     * 每个 ObjectAnimationRunner 实例独立的时间追踪器。
     * Per-instance time tracker for independent animation scaling.
     * <p>
     * 使用纳秒级追踪器（与 TACZ 动画系统的 System.nanoTime 匹配）。
     * Uses nanosecond tracker (matching TACZ animation system's System.nanoTime).
     */
    @Unique
    private final AnimationSpeedScaler.TimeTracker kuvalich$timeTracker =
            AnimationSpeedScaler.TimeTracker.createNanosTracker();

    /**
     * 这段动画该不该跟着装填速度走。
     *
     * <p>缩放倍率是「玩家此刻在不在装填」算出来的一个全局值，可每个 Runner 播的是不同的动画：
     * 不分段的话，装填那一两秒里待机呼吸、走路摆枪、冲刺收枪全都跟着快起来，
     * 装填一结束又瞬间掉回原速——看上去就是整把枪抖了一下。
     * 动画名在 Runner 造出来时就定了（{@code animation} 是 final），所以只判一次。
     */
    @Unique
    private boolean kuvalich$scalable;

    /**
     * Runner 刚造出来：按动画名决定这一段要不要跟着装填速度走。
     *
     * @param animation 这段动画
     * @param ci        回调
     */
    @Inject(method = "<init>(Lcom/tacz/guns/api/client/animation/ObjectAnimation;)V",
            at = @At("RETURN"), require = 0)
    private void kuvalich$classify(ObjectAnimation animation, CallbackInfo ci) {
        String name = animation == null || animation.name == null
                ? "" : animation.name.toLowerCase(Locale.ROOT);
        this.kuvalich$scalable = !kuvalich$isAmbient(name);
    }

    /**
     * 这段动画是不是「和装填无关、不该跟着装填速度走」的那一类。
     *
     * <p>为什么排黑名单而不是白名单：装填动画的名字各枪包自己取，
     * 整合包里就有 {@code left_reload_tactical}、{@code combine_reload_tactical}、
     * {@code special_load}（逐发霰弹枪）、{@code sound_reload_1}、{@code unloading} 这些，
     * 按「reload / bolt 开头」挑的话会把它们全漏掉——那还不如不分段。
     * 反过来列「肯定不该加速」的循环动作和无关动作，漏了一个最多是它跟着快一点，
     * 不会出现「装填动画没跟上装填速度」这种真正看得出来的毛病。
     *
     * @param name 动画名，已转小写
     * @return 是无关动作返回 true
     */
    @Unique
    private static boolean kuvalich$isAmbient(String name) {
        if (name.isEmpty()) {
            return true;
        }
        return name.startsWith("idle") || name.startsWith("static") || name.startsWith("walk")
                || name.startsWith("run") || name.startsWith("inspect") || name.startsWith("put_away")
                || name.startsWith("put_down") || name.startsWith("draw") || name.startsWith("shoot")
                || name.startsWith("fire") || name.startsWith("aim") || name.startsWith("sight")
                || name.startsWith("spin") || name.startsWith("jump") || name.startsWith("slide")
                || name.startsWith("crawl") || name.contains("heat");
    }

    /**
     * 拦截 run()、update()、updateSoundOnly() 中所有 System.nanoTime() 调用，
     * 通过 TimeTracker 缩放返回值实现动画速度调整。
     * <p>
     * Intercept all System.nanoTime() calls in run(), update(), updateSoundOnly(),
     * scaling return values through TimeTracker to adjust animation speed.
     * <p>
     * 当 scale = 1.0（非装填状态）时，TimeTracker 原样追踪真实时间，无额外开销。
     * 当 scale = 1.5（+50% reload_speed）时，每帧增量被放大 1.5 倍，动画加速播放。
     * 当 scale = 0.7（-30% reload_speed）时，每帧增量被缩小，动画减速播放。
     * <p>
     * When scale = 1.0 (not reloading): TimeTracker tracks real time as-is, no overhead.
     * When scale = 1.5 (+50% reload_speed): frame delta is multiplied by 1.5, animation speeds up.
     * When scale = 0.7 (-30% reload_speed): frame delta is reduced, animation slows down.
     *
     * @param original System.nanoTime() 的原始返回值
     * @return 缩放后的纳秒时间戳
     */
    @ModifyExpressionValue(
            method = {"run", "update", "updateSoundOnly"},
            at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"),
            require = 0
    )
    private long kuvalich$scaleAnimationTime(long original) {
        if (!kuvalich$scalable) {
            // 不是装填 / 拉栓那几段，原样走真实时间，连追踪器都不碰
            return original;
        }
        double scale = AnimationSpeedScaler.getAnimationSpeedScale();
        return kuvalich$timeTracker.updateAndGet(original, scale);
    }
}