package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.tooltip.card.HoverCardController;

import java.util.List;

/**
 * 卡展开着的时候不画这件物品的悬浮提示。
 * 只靠 RenderTooltipEvent.Pre 取消不够：Obscure Tooltips 这类模组在这个方法开头就自己画完并返回了，事件发不出来，所以要抢在最前面。
 * 方法是原版的，写 Mojmap 名；tooltipStack 是 Forge 补上的字段，没有混淆名。
 */
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphicsHoverCard {

    @Shadow(remap = false)
    private ItemStack tooltipStack;

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true, require = 0)
    private void kuvalich$hoverCardHideTooltip(Font font, List<ClientTooltipComponent> components, int x, int y,
                                               ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (HoverCardController.hidesTooltip(this.tooltipStack)) {
            ci.cancel();
        }
    }
}
