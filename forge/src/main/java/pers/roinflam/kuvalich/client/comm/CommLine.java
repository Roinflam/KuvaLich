package pers.roinflam.kuvalich.client.comm;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.KuvaComm;
import pers.roinflam.kuvalich.comm.LichLook;

/**
 * 客户端收到的一句通讯：包里的内容原样拷贝，再加上收到的时刻
 *
 * @param receivedMs 收到时的演出钟毫秒（{@code KuvaFxDirector.clockMs()}），队列据此判断等太久要丢掉
 */
@OnlyIn(Dist.CLIENT)
public record CommLine(CommSpeaker speaker, int flags, @Nullable Component name, Component line,
                       @Nullable LichLook look, int holdMs, long receivedMs) {

    public boolean urgent() {
        return (flags & KuvaComm.URGENT) != 0;
    }

    public boolean arrival() {
        return (flags & KuvaComm.ARRIVAL) != 0;
    }

    public boolean ambient() {
        return (flags & KuvaComm.AMBIENT) != 0;
    }

    public boolean chatCopy() {
        return (flags & KuvaComm.CHAT_COPY) != 0;
    }

    public boolean noMutter() {
        return (flags & KuvaComm.NO_MUTTER) != 0;
    }
}
