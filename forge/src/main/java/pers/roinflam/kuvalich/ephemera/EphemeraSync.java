package pers.roinflam.kuvalich.ephemera;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.network.packet.EphemeraStatePacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家的幻纹给别人看（服务端）。每 5 tick 拿幻纹格里那件和上次发出去的比，变了才发给本人和所有追踪者；
 * 不看能力的版本号，换一张模组它也会变，会给所有追踪者白发。晚进视野的由 StartTracking 单独补给那一个人；
 * 登录、重生、换维度之后忘掉「上次发的」，下一轮给本人补一次（追踪者那边会重新开始追踪，自己补）。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class EphemeraSync {

    private static final int POLL_TICKS = 5;

    /** 只在服务端主线程上动 */
    private static final Map<UUID, EphemeraLook> LAST_SENT = new HashMap<>();
    private static int sent;

    private EphemeraSync() {
    }

    public static EphemeraLook lookOf(Player player) {
        WarframeModules wm = player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElse(null);
        return wm == null ? EphemeraLook.NONE : EphemeraLook.of(wm.getEphemera());
    }

    /** 变了就广播，返回发没发 */
    public static boolean poll(ServerPlayer player) {
        EphemeraLook now = lookOf(player);
        EphemeraLook last = LAST_SENT.get(player.getUUID());
        if (now.equals(last) || (last == null && now.isNone())) {
            LAST_SENT.put(player.getUUID(), now);
            return false;
        }
        LAST_SENT.put(player.getUUID(), now);
        send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new EphemeraStatePacket(player.getId(), now));
        return true;
    }

    public static void forget(Player player) {
        LAST_SENT.remove(player.getUUID());
    }

    /** 测试用：一共发出去几个包 */
    public static int sentCount() {
        return sent;
    }

    private static void send(PacketDistributor.PacketTarget target, EphemeraStatePacket msg) {
        sent++;
        if (KuvaLich.network == null) {
            return;
        }
        try {
            KuvaLich.network.send(target, msg);
        } catch (RuntimeException e) {
            LogUtil.error("幻纹状态发送失败", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                || !(event.player instanceof ServerPlayer player) || player.tickCount % POLL_TICKS != 0) {
            return;
        }
        poll(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            sendTo(viewer, target);
        }
    }

    /** 给 viewer 补一份 target 现在的幻纹；没带就不发。返回发没发 */
    public static boolean sendTo(ServerPlayer viewer, ServerPlayer target) {
        EphemeraLook look = lookOf(target);
        if (look.isNone()) {
            return false;
        }
        send(PacketDistributor.PLAYER.with(() -> viewer), new EphemeraStatePacket(target.getId(), look));
        return true;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        forget(event.getEntity());
    }

    // 排在能力克隆之后，读到的已经是新实体身上那份
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClone(PlayerEvent.Clone event) {
        forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity());
    }
}
