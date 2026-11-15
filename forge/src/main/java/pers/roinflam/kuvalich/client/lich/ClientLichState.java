package pers.roinflam.kuvalich.client.lich;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.RageTier;
import pers.roinflam.kuvalich.nemesis.WaitReason;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Locale;

/**
 * 客户端缓存的玩家自己的玄骸状态：解密 HUD、头顶名牌、档案状态行、通讯肖像都从这里读
 *
 * <p>状态包到了就整份替换；退出世界时清空，所以别在「包到达时缓存已经被清掉」的场合依赖它
 * （比如破解完成的名字行，那个名字随包带来）。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ClientLichState {

    /**
     * 状态的不可变快照
     *
     * @param weapon       武器下标，-1 = 没有
     * @param ragePermille 怒气千分比 0~1000
     * @param tier         怒气档位，对应 {@link RageTier} 的序号
     * @param clues        已揭示的密语条数 0~3
     * @param cooldownSec  冷却还剩几秒，0 = 没在冷却
     */
    public record State(LichState state, WaitReason waitReason, String name, int seed, int weapon, int rank,
                        int ragePermille, int tier, int clues, int cooldownSec, int generation, boolean paused,
                        String ephemera) {

        /** 有没有玄骸 */
        public boolean hasLich() {
            return !name.isEmpty();
        }

        /** 怒气 0~1 */
        public float rage() {
            return ragePermille / 1000.0F;
        }
    }

    /** 没有玄骸时的状态 */
    public static final State NONE = fromPacket(LichStatePacket.EMPTY);

    private static volatile State current = NONE;

    private ClientLichState() {
    }

    /** 当前状态；从没收到过时是 {@link #NONE} */
    public static State get() {
        return current;
    }

    public static boolean hasLich() {
        return current.hasLich();
    }

    /** 全大写的名字，名牌和 HUD 用；没有玄骸为空串 */
    public static String nameUpper() {
        return current.name().toUpperCase(Locale.ROOT);
    }

    /** 首字母大写的名字（档案标题用）；没有玄骸为空串 */
    public static String nameTitle() {
        return current.name();
    }

    /** 状态包到了 */
    public static void accept(LichStatePacket packet) {
        current = fromPacket(packet);
    }

    private static State fromPacket(LichStatePacket p) {
        return new State(p.state, p.waitReason, p.name == null ? "" : p.name, p.seed, p.weapon, p.rank,
                p.ragePermille, p.tier, p.clues, p.cooldownSec, p.generation, p.paused, p.ephemera);
    }

    /** 离开世界（退出、换服）时清空，免得下一个世界里短暂显示上一个世界的玄骸 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = NONE;
    }
}
