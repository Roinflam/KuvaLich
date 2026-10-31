package pers.roinflam.kuvalich.mixin.client;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.MouseHandler;
import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.mercy.MercyClient;

/**
 * 处决时锁住鼠标转视角。目标是原版类，写 Mojmap 名，不带 remap = false。
 *
 * <p>{@code turnPlayer} 每帧把攒下的鼠标位移转成视角变化；处决期间开头直接取消，并把攒下的位移清零，
 * 免得交还那一刻一下子甩出去。</p>
 *
 * <p>原版每次进来先拿「距上次调用过了多久」当平滑镜头（电影视角）的插值步长，再记下这一刻。整段取消会让这个时刻
 * 停在处决开始前，交还后第一帧的步长就成了整段演出的时长，开着平滑镜头时会猛地转一下；所以取消时照样把时刻记成现在，
 * 平滑镜头里攒着的余量也清掉（关着平滑镜头时原版每帧本来就在清）。</p>
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerMercy {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    private double lastMouseEventTime;

    @Shadow
    @Final
    private SmoothDouble smoothTurnX;

    @Shadow
    @Final
    private SmoothDouble smoothTurnY;

    @Inject(method = "turnPlayer()V", at = @At("HEAD"), cancellable = true)
    private void kuvalich$lockDuringMercy(CallbackInfo ci) {
        if (MercyClient.isActive()) {
            this.accumulatedDX = 0.0D;
            this.accumulatedDY = 0.0D;
            this.lastMouseEventTime = Blaze3D.getTime();
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            ci.cancel();
        }
    }
}
