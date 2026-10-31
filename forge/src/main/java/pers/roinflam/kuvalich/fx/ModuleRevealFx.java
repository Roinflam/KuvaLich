package pers.roinflam.kuvalich.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.item.module.warframe.WarframePrimeModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponPrimeModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRareModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponUncommonModule;

/**
 * 模组揭示的效果包（通用代码，只在服务端发）
 *
 * <p>十种模组卡的「使用」都经过揭示等级那一处，所以只在那里调一次：手前方冒光点和火花，
 * Prime 加光环、光点上升，紫卡另加紫色故障光点和横向光晕。发给揭示者手前 16 格内的玩家。</p>
 */
public final class ModuleRevealFx {

    /** 揭示画面的可见半径（格） */
    private static final double RANGE = 16.0D;

    private ModuleRevealFx() {
    }

    /**
     * 揭示了一张模组：服务端玩家才发，稀有度按物品类判（普通 0 / 罕见 1 / 稀有 2 / Prime 3 / 紫卡 4）
     */
    public static void send(Player player, ItemStack module) {
        if (!(player instanceof ServerPlayer sp) || module == null || module.isEmpty()
                || !(sp.level() instanceof ServerLevel sl)) {
            return;
        }
        Vec3 look = sp.getLookAngle();
        Vec3 at = sp.getEyePosition().add(look.x * 0.6D, look.y * 0.6D - 0.35D, look.z * 0.6D);
        KuvaFx.near(sl, at, RANGE, FxId.MODULE_REVEAL, sp, rarityOf(module.getItem()), 0);
    }

    /** 物品类 → 稀有度序号 */
    static int rarityOf(Item item) {
        if (item instanceof WeaponRivenModule || item instanceof WarframeRivenModule) {
            return 4;
        }
        if (item instanceof WeaponPrimeModule || item instanceof WarframePrimeModule) {
            return 3;
        }
        if (item instanceof WeaponRareModule || item instanceof WarframeRareModule) {
            return 2;
        }
        if (item instanceof WeaponUncommonModule || item instanceof WarframeUncommonModule) {
            return 1;
        }
        return 0;
    }
}
