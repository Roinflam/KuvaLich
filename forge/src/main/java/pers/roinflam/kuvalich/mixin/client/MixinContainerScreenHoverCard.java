package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.tooltip.card.HoverCardController;

/**
 * 模组卡悬停展开时的两处改动。目标是原版类，写 Mojmap 名，不带 remap = false。
 *
 * <p>卡展开后鼠标挪到卡面上（原格子以外）时，{@code render} 拿到的鼠标坐标换成原格子中心：
 * 格子高亮、{@code hoveredSlot}、数字键和丢弃键都还作用在原格子上，被卡盖住的格子不会被当成悬停。
 * 展开中、收起中的卡所在格子不画物品，卡自己在同一位置画同一张图标。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreenHoverCard {

    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int kuvalich$hoverCardMouseX(int mouseX) {
        return HoverCardController.mouseX((AbstractContainerScreen<?>) (Object) this, mouseX);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int kuvalich$hoverCardMouseY(int mouseY) {
        return HoverCardController.mouseY((AbstractContainerScreen<?>) (Object) this, mouseY);
    }

    @Inject(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V", at = @At("HEAD"), cancellable = true)
    private void kuvalich$hoverCardHideSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (HoverCardController.hidesSlot((AbstractContainerScreen<?>) (Object) this, slot)) {
            ci.cancel();
        }
    }
}
