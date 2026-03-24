package pers.roinflam.kuvalich.block.table;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemWeaponTable;

import java.util.Collections;
import java.util.List;

/**
 * 武器军械库方块（1.20.1版本，支持朝向旋转）
 * Weapon Table Block (1.20.1 version, supports directional rotation)
 *
 * 属性：
 * - 碰撞箱: 宽度满格，高度1.15格
 * - 朝向: 根据放置时玩家方向，默认北（兼容旧存档）
 */
public class RequiemWeaponTable extends HorizontalDirectionalBlock {

    /** 碰撞箱形状：高度1.15格（与原逻辑一致） */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.15D, 1.0D);

    /**
     * 构造函数，默认朝向为北（兼容旧存档中无facing属性的方块）
     *
     * @param properties 方块属性
     */
    public RequiemWeaponTable(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** 注册FACING属性 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 放置时根据玩家朝向设置方块朝向（面向玩家的反方向）
     *
     * @param context 放置上下文
     * @return 带有朝向属性的方块状态
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 碰撞箱 */
    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    /** 方块被破坏时掉落自己 */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        return Collections.singletonList(new ItemStack(KuvaLichBlocks.REQUIEM_WEAPON_TABLE_ITEM.get()));
    }

    /** 方块被右键点击（打开GUI） */
    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level,
                                          @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider(
                            (windowId, playerInventory, p) ->
                                    new MenuRequiemWeaponTable(windowId, playerInventory, level, pos),
                            net.minecraft.network.chat.Component.translatable("container.kuvalich.requiem_weapon_table")
                    ),
                    pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}