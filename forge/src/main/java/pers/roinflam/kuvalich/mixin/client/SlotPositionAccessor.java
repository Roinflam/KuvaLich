package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 改写槽位的界面坐标：安魂系列的方块界面铺满整屏、按窗口大小排槽位，每次 init 都要重新摆
 *
 * <p>{@code Slot.x / y} 是原版类的 final 字段，没带 {@code remap = false}，写 Mojmap 名由 Mixin 重映射；
 * {@code @Mutable} 去掉 final。只在客户端用（服务端不看槽位坐标）。</p>
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void kuvalich$setX(int x);

    @Mutable
    @Accessor("y")
    void kuvalich$setY(int y);
}
