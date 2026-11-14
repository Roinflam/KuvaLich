package pers.roinflam.kuvalich.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.comm.CommLine;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.hud.DecryptionHudOverlay;
import pers.roinflam.kuvalich.client.gui.hud.NoticeHud;
import pers.roinflam.kuvalich.client.lich.ClientLichArchive;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.packet.KuvaCommPacket;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.network.packet.MercyChoiceOpenPacket;
import pers.roinflam.kuvalich.network.packet.NoticePacket;

/**
 * 几个服务端 → 客户端的包到了以后的客户端处理
 *
 * <p>包类里只经 {@code DistExecutor} 调到这里，专用服务端不会加载这个类和它引用的客户端类。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    /** 正在派发的解密 HUD 包；只在 {@link #onDecryptionHud} 执行期间非空 */
    @Nullable
    private static DecryptionHudPacket currentDecryption;

    private ClientPacketHandlers() {
    }

    /** 通讯台词到了：记下收到的时刻，排进通讯窗的队列 */
    public static void onComm(KuvaCommPacket msg) {
        CommHud.enqueue(new CommLine(msg.speaker, msg.flags, msg.name, msg.line, msg.look, msg.holdMs,
                KuvaFxDirector.clockMs()));
    }

    /** 一条玩家提示到了：开着提示条就排进去，关着就交回原版聊天 / 动作栏 */
    public static void onNotice(NoticePacket msg) {
        NoticeHud.accept(msg);
    }

    /** 玄骸状态到了 */
    public static void onLichState(LichStatePacket msg) {
        ClientLichState.accept(msg);
    }

    /** 终结的选择窗口开了 */
    public static void onMercyChoiceOpen(MercyChoiceOpenPacket msg) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        pers.roinflam.kuvalich.client.mercy.MercyChoiceClient.accept(msg, level == null ? 0L : level.getGameTime());
    }

    /** 档案数据到了 */
    public static void onArchive(LichArchivePacket msg) {
        ClientLichArchive.accept(msg.data());
    }

    /**
     * 解密进度包到了。按旧的五个参数交给 HUD；协议 10 起追加的揭示卡号、三枚密语和玄骸名字
     * 在 HUD 处理这一次调用期间可以从 {@link #currentDecryption()} 读到
     */
    public static void onDecryptionHud(DecryptionHudPacket msg) {
        currentDecryption = msg;
        try {
            DecryptionHudOverlay.onPacket(msg.eventType(), msg.level(), msg.progress(), msg.threshold(), msg.added());
        } finally {
            currentDecryption = null;
        }
    }

    /**
     * 正在处理的那个解密进度包，用来读 {@link DecryptionHudPacket#revealedCard()}、
     * {@link DecryptionHudPacket#riddles()}、{@link DecryptionHudPacket#lichName()}。
     * 只在 {@code DecryptionHudOverlay.onPacket} 同步执行的期间有值，其余时刻为 {@code null}
     */
    @Nullable
    public static DecryptionHudPacket currentDecryption() {
        return currentDecryption;
    }
}
