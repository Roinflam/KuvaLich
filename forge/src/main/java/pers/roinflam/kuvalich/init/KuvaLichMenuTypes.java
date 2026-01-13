package pers.roinflam.kuvalich.init;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.*;

/**
 * MenuType注册器（1.20.1新系统）
 * MenuType Registry (1.20.1 new system)
 *
 * 替代1.12.2的IGuiHandler
 * Replaces 1.12.2's IGuiHandler
 */
public class KuvaLichMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Reference.MOD_ID);

    /**
     * 灭骸之扉菜单
     * Requiem Gate Menu
     */
    public static final RegistryObject<MenuType<MenuRequiemGate>> REQUIEM_GATE = MENUS.register(
            "requiem_gate",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                Level level = inv.player.level();
                return new MenuRequiemGate(windowId, inv, level, pos);
            })
    );

    /**
     * 安魂之铸菜单
     * Requiem Recast Menu
     */
    public static final RegistryObject<MenuType<MenuRequiemRecast>> REQUIEM_RECAST = MENUS.register(
            "requiem_recast",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                Level level = inv.player.level();
                return new MenuRequiemRecast(windowId, inv, level, pos);
            })
    );

    /**
     * 安魂之融菜单
     * Requiem Evolve Menu
     */
    public static final RegistryObject<MenuType<MenuRequiemEvolve>> REQUIEM_EVOLVE = MENUS.register(
            "requiem_evolve",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                Level level = inv.player.level();
                return new MenuRequiemEvolve(windowId, inv, level, pos);
            })
    );

    /**
     * 武器军械库菜单
     * Weapon Table Menu
     */
    public static final RegistryObject<MenuType<MenuRequiemWeaponTable>> REQUIEM_WEAPON_TABLE = MENUS.register(
            "requiem_weapon_table",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                Level level = inv.player.level();
                return new MenuRequiemWeaponTable(windowId, inv, level, pos);
            })
    );

    /**
     * 战甲军械库菜单
     * Warframe Table Menu
     */
    public static final RegistryObject<MenuType<MenuRequiemWarframeTable>> REQUIEM_WARFRAME_TABLE = MENUS.register(
            "requiem_warframe_table",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                Level level = inv.player.level();
                return new MenuRequiemWarframeTable(windowId, inv, level, pos);
            })
    );
}