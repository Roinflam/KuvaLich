package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;

/**
 * 声音引擎的最终音量乘上本模组的混音增益（全局压混、主角让位、命中闪避），见 {@link KuvaMixer}
 *
 * <p>目标是原版类，写 Mojmap 名、不带 {@code remap = false}，由混入自己重映射。{@code calculateVolume} 有两个私有重载，
 * 这里挑以声音实例为参数的那个 {@code (SoundInstance)F}：开播、会 tick 的声音每 tick、以及调类别音量时重算在播的声音，都走它。
 * 只用 Forge 自带的混入注解，不依赖别的模组带进来的扩展库。</p>
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngineMixer {

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"),
            cancellable = true)
    private void kuvalich$applyMixerGain(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
        float before = cir.getReturnValueF();
        float v = before;
        float g = KuvaMixer.gainFor(instance);
        if (g != 1.0F) {
            v *= g;
            cir.setReturnValue(v);
            if (KuvaMixer.tracing()) {
                KuvaMixer.trace(instance, before, v);
            }
        }
        // 淡入的声音开播那一刻音量是 0；这时要是正好赶上一次「在播声音全部重算」，原版会把算出 0 的直接停掉，
        // 淡入的循环底噪就再也起不来了。类别音量没被玩家关掉时给一个听不见的保底值
        if (v <= 0.0F && instance instanceof KuvaSoundInstance && instance.canStartSilent()
                && Minecraft.getInstance().options.getSoundSourceVolume(instance.getSource()) > 0.0F) {
            cir.setReturnValue(1.0E-5F);
        }
    }
}
