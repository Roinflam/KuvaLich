package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.event.ModuleDiscoveryHandler;

import java.util.function.Supplier;

/**
 * 图鉴操作结果包（服务器 → 客户端）
 *
 * <p>回应玩家在图鉴里的两种点击：左键拿到背包（{@link CodexGiveItemPacket}）、
 * Shift+左键装到空槽（{@link CodexInstallModulePacket}）。</p>
 *
 * <p><b>为什么需要它。</b>这两条路径在服务端都是「做完就 return」，客户端完全不知道
 * 结果 —— 装不上、背包满了掉在脚下，玩家看到的都是「点了没反应」。
 * 有了结果回包，界面上才有得画。</p>
 *
 * <p>结果用一个字节的序号传，对应客户端 {@code CodexFeedback.Kind} 里的动画类型。
 * 不传失败原因：界面上「装不上」一种动画就够了，把原因细分成没空位 / 冲突 /
 * 没放武器只会让客户端多几条它并不需要区分的状态；真要查原因服务端日志里有。</p>
 *
 * @author RoinFlam
 */
public class CodexActionResultPacket {

    // ==================== 结果类型（与客户端 CodexFeedback.Kind 对应） ====================

    /** 装配成功 */
    public static final byte INSTALLED = 0;
    /** 装不上（没空位 / 冲突 / 没打开军械库） */
    public static final byte REJECTED = 1;
    /** 已放进背包 */
    public static final byte TAKEN = 2;
    /** 背包满了，掉在脚下 */
    public static final byte DROPPED = 3;

    /** 模组的发现键（{@code type:rarityOrder}），客户端用它定位是哪个格子 */
    private final String discoveryKey;
    /** 结果类型 */
    private final byte result;

    public CodexActionResultPacket(String discoveryKey, byte result) {
        this.discoveryKey = discoveryKey;
        this.result = result;
    }

    /**
     * 用 type + 品质序号拼出发现键再构造。
     *
     * @param moduleType  模组 type
     * @param rarityOrder 品质序号
     * @param result      结果类型
     * @return 结果包
     */
    public static CodexActionResultPacket of(String moduleType, int rarityOrder, byte result) {
        return new CodexActionResultPacket(
                ModuleDiscoveryHandler.buildDiscoveryKey(moduleType, rarityOrder), result);
    }

    // ==================== 编解码 ====================

    public static void encode(CodexActionResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.discoveryKey);
        buf.writeByte(msg.result);
    }

    public static CodexActionResultPacket decode(FriendlyByteBuf buf) {
        return new CodexActionResultPacket(buf.readUtf(256), buf.readByte());
    }

    // ==================== 客户端处理 ====================

    public static void handle(CodexActionResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 反馈表是客户端专有的，用 DistExecutor 隔开，避免专用服务端 classload 到它
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.push(
                                msg.discoveryKey, kindOf(msg.result))));
        ctx.get().setPacketHandled(true);
    }

    /**
     * 把线上的结果序号翻译成客户端的动画类型。
     *
     * @param result 结果序号
     * @return 动画类型
     */
    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind kindOf(byte result) {
        switch (result) {
            case INSTALLED: return pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.INSTALLED;
            case TAKEN: return pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.TAKEN;
            case DROPPED: return pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.DROPPED;
            default: return pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.Kind.REJECTED;
        }
    }
}
