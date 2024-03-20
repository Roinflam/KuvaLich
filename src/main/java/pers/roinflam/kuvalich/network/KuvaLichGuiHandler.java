package pers.roinflam.kuvalich.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.inventory.container.*;

import javax.annotation.Nullable;

public class KuvaLichGuiHandler implements IGuiHandler {
    public static final int REQUIEM_GATE = 1;
    public static final int REQUIEM_RECAST = 2;
    public static final int REQUIEM_EVOLVE = 3;
    public static final int REQUIEM_WEAPON_TABLE = 4;
    public static final int REQUIEM_WARFRAME_TABLE = 5;

    @Nullable
    @Override
    public Object getServerGuiElement(int ID, @NotNull EntityPlayer player, @NotNull World world, int x, int y, int z) {
        if (ID == REQUIEM_GATE) {
            return new ContainerRequiemGate(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_RECAST) {
            return new ContainerRequiemRecast(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_EVOLVE) {
            return new ContainerRequiemEvolve(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_WEAPON_TABLE) {
            return new ContainerRequiemWeaponTable(player, world, new BlockPos(x, y, z));
        }else if (ID == REQUIEM_WARFRAME_TABLE) {
            return new ContainerRequiemWarframeTable(player, world, new BlockPos(x, y, z));
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int ID, @NotNull EntityPlayer player, @NotNull World world, int x, int y, int z) {
        if (ID == REQUIEM_GATE) {
            return new GuiRequiemGate(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_RECAST) {
            return new GuiRequiemRecast(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_EVOLVE) {
            return new GuiRequiemEvolve(player, world, new BlockPos(x, y, z));
        } else if (ID == REQUIEM_WEAPON_TABLE) {
            return new GuiRequiemWeaponTable(player, world, new BlockPos(x, y, z));
        }else if (ID == REQUIEM_WARFRAME_TABLE) {
            return new GuiRequiemWarframeTable(player, world, new BlockPos(x, y, z));
        }
        return null;
    }

}
