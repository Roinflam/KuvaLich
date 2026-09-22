package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.event.ModuleDiscoveryHandler;

import java.util.function.Supplier;

/**
 * 一键装配结果包（服务器 → 客户端）
 *
 * <p>回应 {@link CodexInstallModulePacket}：告诉客户端这次到底装上没有。</p>
 *
 * <p><b>为什么需要它。</b>改造前服务端判定装不上就直接 return，客户端完全不知道
 * 请求被拒绝了 —— 玩家点一下、什么都没发生，分不清是「没空位」「冲突了」
 * 还是「模组没点中」。现在无论成败都回一个结果，界面上才有得画。</p>
 *
 * <p>包里只带 {@code discoveryKey} 与成败，不带拒绝原因：界面上「装不上」这一种
 * 动画就够了，把原因细分成没空位 / 冲突 / 没放武器只会让客户端多几条它并不需要
 * 区分的状态。真要查原因，服务端日志里有。</p>
 *
 * @author RoinFlam
 */
public class CodexInstallResultPacket {

    /** 模组的发现键（{@code type:rarityOrder}），客户端用它定位是哪个格子 */
    private final String discoveryKey;
    /** 是否装上了 */
    private final boolean installed;

    public CodexInstallResultPacket(String discoveryKey, boolean installed) {
        this.discoveryKey = discoveryKey;
        this.installed = installed;
    }

    /**
     * 用 type + 品质序号拼出发现键再构造。
     *
     * @param moduleType  模组 type
     * @param rarityOrder 品质序号
     * @param installed   是否装上了
     * @return 结果包
     */
    public static CodexInstallResultPacket of(String moduleType, int rarityOrder, boolean installed) {
        return new CodexInstallResultPacket(
                ModuleDiscoveryHandler.buildDiscoveryKey(moduleType, rarityOrder), installed);
    }

    // ==================== 编解码 ====================

    public static void encode(CodexInstallResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.discoveryKey);
        buf.writeBoolean(msg.installed);
    }

    public static CodexInstallResultPacket decode(FriendlyByteBuf buf) {
        return new CodexInstallResultPacket(buf.readUtf(256), buf.readBoolean());
    }

    // ==================== 客户端处理 ====================

    public static void handle(CodexInstallResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 反馈表是客户端专有的，用 DistExecutor 隔开，避免专用服务端 classload 到它
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.push(
                                msg.discoveryKey,
                                msg.installed
                                        ? pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.INSTALLED
                                        : pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.REJECTED)));
        ctx.get().setPacketHandled(true);
    }
}
