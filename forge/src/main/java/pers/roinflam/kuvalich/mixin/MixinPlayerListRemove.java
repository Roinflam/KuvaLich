package pers.roinflam.kuvalich.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.world.inventory.RequiemMenuLogoutGuard;

/**
 * 下线时赶在 Tenet 收光标之前处理安魂界面，见 {@link RequiemMenuLogoutGuard}。Mojmap 名，由 Mixin 重映射
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListRemove {

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void kuvalich$beforeRemove(ServerPlayer player, CallbackInfo ci) {
        // 这里抛出去整个移除就断了，人会一直挂在在线名单里
        try {
            RequiemMenuLogoutGuard.beforeRemove(player);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vm) {
                throw vm;
            }
            LogUtil.error("下线前关闭 " + player.getGameProfile().getName() + " 的安魂界面出错", t);
        }
    }
}
