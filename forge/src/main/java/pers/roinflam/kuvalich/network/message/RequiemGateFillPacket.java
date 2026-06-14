package pers.roinflam.kuvalich.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemGate;

import java.util.function.Supplier;

/**
 * 灭骸之扉一键补全答案网络包（客户端 → 服务器）
 * Requiem Gate auto-fill answer packet (Client → Server)
 *
 * <p>创造模式玩家在灭骸之扉界面按 TAB 时发送本包，请求服务端把当前玄骸的
 * 正确安魂卡答案（正确顺序）直接填入三个卡槽，省去手动查看答案并从背包找卡的步骤。</p>
 *
 * <p>服务端严格校验：
 * <ol>
 *   <li>必须创造模式（防止生存玩家通过伪造包作弊）；</li>
 *   <li>当前打开的菜单必须是 {@link MenuRequiemGate}；</li>
 *   <li>答案必须已生成（玩家至少解锁过第一阶段），否则静默忽略。</li>
 * </ol></p>
 *
 * <p>本包不携带任何字段，是纯触发型包。所有答案数据均由服务端从玩家
 * RequiemCard 能力中读取，客户端无法伪造答案内容。</p>
 */
public class RequiemGateFillPacket {

    /**
     * 构造触发包（无字段）
     */
    public RequiemGateFillPacket() {
    }

    /**
     * 编码到字节缓冲（无字段，空实现）
     *
     * @param msg 待编码包
     * @param buf 字节缓冲
     */
    public static void encode(RequiemGateFillPacket msg, FriendlyByteBuf buf) {
        // 无字段，无需写入
    }

    /**
     * 从字节缓冲解码（无字段）
     *
     * @param buf 字节缓冲
     * @return 解码得到的包
     */
    public static RequiemGateFillPacket decode(FriendlyByteBuf buf) {
        return new RequiemGateFillPacket();
    }

    /**
     * 服务端处理：校验创造模式与当前菜单后，填入正确答案卡片
     *
     * @param msg 收到的包
     * @param ctx 网络事件上下文
     */
    public static void handle(RequiemGateFillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            // 安全校验：必须创造模式
            if (!player.isCreative()) {
                LogUtil.warn("非创造模式玩家 " + player.getName().getString()
                        + " 尝试一键补全灭骸之扉答案，已拒绝");
                return;
            }

            // 当前打开的菜单必须是灭骸之扉，否则忽略
            if (player.containerMenu instanceof MenuRequiemGate menu) {
                menu.autoFillAnswerCards(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
