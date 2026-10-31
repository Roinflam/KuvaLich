package pers.roinflam.kuvalich.client.fx;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.script.CombatFx;
import pers.roinflam.kuvalich.client.fx.script.ElementFx;
import pers.roinflam.kuvalich.client.fx.script.ItemFx;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.fx.script.RequiemFx;
import pers.roinflam.kuvalich.client.mercy.KuvaStateFx;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.client.mercy.MercyFx;
import pers.roinflam.kuvalich.client.mercy.RankUpBanner;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 音画效果包到了客户端以后的分派：按 {@link FxId} 交给各个演出脚本
 *
 * <p>不认识的编号（以后追加了新编号、旧客户端收到）静默忽略。脚本里出了错只记日志，
 * 不让一个演出的毛病带着整个网络线程的任务一起出错。{@code CRIT} 和 {@code ELEMENT_PROC} 的处理方法只往命中总线投递，
 * 真正的演出在那一 tick 末尾合并后再起。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaFxClient {

    private KuvaFxClient() {
    }

    public static void handle(KuvaFxPacket p) {
        FxId id = FxId.byId(p.fx);
        if (id == null) {
            return;
        }
        try {
            switch (id) {
                case LICH_SUMMON -> LichFx.summon(p);
                case REQUIEM_SUCCESS -> RequiemFx.success(p);
                case REQUIEM_FAIL -> RequiemFx.fail(p);
                case LICH_DEATH -> LichFx.lichDeath(p);
                case THRALL_DEATH -> LichFx.thrallDeath(p);
                case CONFISCATE -> RequiemFx.confiscate(p);
                case CRIT -> CombatFx.crit(p);
                case ELEMENT_PROC -> ElementFx.proc(p);
                case GAS_CLOUD -> ElementFx.gasCloud(p);
                case EXECUTE -> CombatFx.execute(p);
                case LAST_STAND -> CombatFx.lastStand(p);
                case MODULE_REVEAL -> ItemFx.moduleReveal(p);
                case LICH_COMM, MERCY_STAB -> {
                    // 退役 / 保留不用的编号：通讯改走专门的通讯包，每一刀的演出由开始处决的包排好整条时间轴
                }
                case SPLASH -> CombatFx.splash(p);
                case PURGE -> CombatFx.purge(p);
                case LICH_DOWNED -> MercyFx.downed(p);
                case MERCY_BEGIN -> MercyClient.onBegin(p);
                case LICH_EXPIRE -> MercyFx.expire(p);
                case LICH_ARRIVE -> LichFx.arrive(p);
                case LICH_DEPART -> LichFx.depart(p);
                case KUVA_STATE -> KuvaStateFx.onPacket(p);
                case RANK_UP -> RankUpBanner.onPacket(p);
                case WEAPON_DASH -> CombatFx.dash(p);
                case RIDDLE_SOLVED -> RequiemFx.riddleSolved(p);
                case DEV_SHOWCASE -> FxShowcase.play(p);
            }
        } catch (RuntimeException e) {
            LogUtil.error("处理音画效果包 " + id + " 时出错", e);
        }
    }
}
