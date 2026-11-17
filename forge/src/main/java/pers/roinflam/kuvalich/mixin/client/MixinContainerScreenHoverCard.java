package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.tooltip.card.HoverCardController;

/**
 * 展开中、收起中的卡所在格子不画物品，卡自己在同一位置画同一张图标。目标是原版类，写 Mojmap 名，不带 remap = false。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreenHoverCard {

    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V", at = @At("HEAD"), cancellable = true)
    private void kuvalich$hoverCardHideSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (HoverCardController.hidesSlot((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
        }
    }
}
