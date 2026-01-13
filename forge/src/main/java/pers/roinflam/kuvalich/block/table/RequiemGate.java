package pers.roinflam.kuvalich.block.table;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemGate;

/**
 * 灭骸之扉方块（1.20.1版本，业务逻辑100%不变）
 * Requiem Gate Block (1.20.1 version, business logic 100% unchanged)
 *
 * 属性保持不变：
 * Properties unchanged:
 * - 硬度: 100.0F
 * - 抗性: 2000.0F
 * - 需要工具: 钻石镐
 * - 碰撞箱: (0, 0, 0) -> (1, 1.15, 1)
 */
public class RequiemGate extends Block {

    /**
     * 碰撞箱形状（业务逻辑100%不变）
     * Collision shape (business logic 100% unchanged)
     *
     * 原1.12.2: new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.15D, 1D)
     * 1.20.1: Shapes.box(0, 0, 0, 1, 1.15, 1)
     */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.15D, 1.0D);

    public RequiemGate(Properties properties) {
        super(properties);
    }

    /**
     * 获取碰撞箱形状（1.20.1新方法，业务逻辑100%不变）
     * Get collision shape (1.20.1 new method, business logic 100% unchanged)
     */
    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    /**
     * 方块被右键点击（业务逻辑100%不变：打开GUI）
     * Block right-clicked (business logic 100% unchanged: open GUI)
     */
    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level,
                                          @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 打开GUI（1.20.1新方法）
            // Open GUI (1.20.1 new method)
            NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (windowId, playerInventory, p) ->
                                    new MenuRequiemGate(windowId, playerInventory, level, pos),
                            net.minecraft.network.chat.Component.translatable("container.kuvalich.requiem_gate")
                    ),
                    pos);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}