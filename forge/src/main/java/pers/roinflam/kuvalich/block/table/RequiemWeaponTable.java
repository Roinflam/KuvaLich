package pers.roinflam.kuvalich.block.table;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemWeaponTable;

import java.util.Collections;
import java.util.List;

/**
 * 武器军械库方块（1.20.1版本，业务逻辑100%不变）
 * Weapon Table Block (1.20.1 version, business logic 100% unchanged)
 */
public class RequiemWeaponTable extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.15D, 1.0D);

    public RequiemWeaponTable(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    /**
     * ✅ 修复：方块被破坏时掉落自己
     * Fix: Drop self when broken
     */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        return Collections.singletonList(new ItemStack(KuvaLichBlocks.REQUIEM_WEAPON_TABLE_ITEM.get()));
    }

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