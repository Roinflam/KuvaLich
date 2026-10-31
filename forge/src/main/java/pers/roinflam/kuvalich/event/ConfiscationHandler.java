package pers.roinflam.kuvalich.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.nemesis.LichComms;
import pers.roinflam.kuvalich.nemesis.LichLine;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.RandomUtil;

/**
 * 玄骸没收物品：有自己的玄骸时，捡起东西有概率被它拿走
 *
 * <p>只有这个玩家已经有玄骸（揭开过第一条密语）才没收，被拿走的东西记在他的卡上，终结那只玄骸时全部还回来。
 * 数量上限由 {@code maxConfiscatedItems} 管，到了上限就不再没收。</p>
 *
 * <p>没收成功的那一刻：掉落物原地消失，附近 16 格的人看到被拿走的演出（按物品稀有度分档）；
 * 本人的动作栏写「谁拿走了你的什么」（每次都写）；0.25 秒后它在通讯窗里嘲讽一句
 * （通讯有限流：没收类 30 秒一句，刚说过别的话也会让开，其余的只有动作栏）。</p>
 */
@Mod.EventBusSubscriber
public class ConfiscationHandler {

    /** 演出广播半径 */
    private static final double FX_RADIUS = 16.0D;
    /** 嘲讽排在没收演出开始后多久（tick），和演出里那一下对上 */
    private static final int COMM_DELAY_TICKS = 5;

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemEntity entityItem = event.getItem();

        double confiscationChance = ModConfig.KUVA_LICH.confiscationChance.get();
        if (confiscationChance <= 0) {
            return;
        }
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (requiemCard == null || !requiemCard.hasLich() || !requiemCard.canConfiscateMore()) {
            return;
        }
        if (!RandomUtil.percentageChance(confiscationChance)) {
            return;
        }
        ItemStack itemStack = entityItem.getItem();
        if (itemStack.isEmpty()) {
            return;
        }
        // addConfiscatedItem 里也查上限：到了上限返回 false，这次照常捡起
        ItemStack taken = itemStack.copy();
        if (!requiemCard.addConfiscatedItem(itemStack)) {
            return;
        }

        event.setCanceled(true);
        Vec3 where = entityItem.position().add(0.0D, entityItem.getBbHeight() * 0.5D, 0.0D);
        entityItem.discard();

        if (player.level() instanceof ServerLevel level) {
            KuvaFx.near(level, where, FX_RADIUS, FxId.CONFISCATE, -1, player.getId() + 1, taken.getRarity().ordinal());
        }
        PlayerFeedback.actionBar(player, KuvaPalette.LICH, "message.kuvalich.confiscation.stolen",
                requiemCard.getLichName(), taken.getHoverName());
        ServerFxQueue.schedule(COMM_DELAY_TICKS, player, () -> LichComms.say(player, LichLine.CONFISCATE));
    }
}
