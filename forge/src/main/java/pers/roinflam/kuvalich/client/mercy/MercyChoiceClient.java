package pers.roinflam.kuvalich.client.mercy;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.network.packet.MercyChoiceOpenPacket;

/** 处决者本人最近一次收到的开窗信息；选择牌按它画，结果仍以目标身上的同步数据为准 */
@OnlyIn(Dist.CLIENT)
public final class MercyChoiceClient {

    @Nullable
    private static MercyChoiceOpenPacket open;
    private static long receivedAtTick;

    private MercyChoiceClient() {
    }

    public static void accept(MercyChoiceOpenPacket msg, long clientTick) {
        open = msg;
        receivedAtTick = clientTick;
    }

    /** 这只目标的开窗信息；不是它的返回 null */
    @Nullable
    public static MercyChoiceOpenPacket forEntity(int entityId) {
        return open != null && open.entityId == entityId ? open : null;
    }

    public static long receivedAtTick() {
        return receivedAtTick;
    }

    public static void clear() {
        open = null;
    }
}
