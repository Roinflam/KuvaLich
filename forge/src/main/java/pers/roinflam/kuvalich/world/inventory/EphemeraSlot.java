package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 战甲军械库的幻纹格。和模组格分开写：模组格的 set 遇到不认识的东西直接清空，
 * 这里不论什么情况都不清掉东西，绕过 mayPlace 塞进来的别的物品退回玩家背包。
 * 放入、取出、内容变化都当场写回玩家能力（写的是拷贝）
 */
public class EphemeraSlot extends SlotItemHandler {

    private final RequiemWarframeTableMenu menu;

    public EphemeraSlot(RequiemWarframeTableMenu menu, int x, int y) {
        super(menu.ephemeraHandler, 0, x, y);
        this.menu = menu;
    }

    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof EphemeraItem;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return accepts(stack) && super.mayPlace(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return 1;
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        // 客户端照服务端发来的内容摆，不做判断
        if (menu.isClientSide() || stack.isEmpty() || accepts(stack)) {
            super.set(stack);
            return;
        }
        LogUtil.warn("幻纹格被直接放进了别的物品，退回背包：" + stack.getHoverName().getString());
        ((IItemHandlerModifiable) getItemHandler()).setStackInSlot(0, ItemStack.EMPTY);
        menu.returnToPlayer(stack.copy());
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        menu.writeBackEphemera();
    }

    @Override
    public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
        super.onTake(player, stack);
        menu.writeBackEphemera();
    }
}
