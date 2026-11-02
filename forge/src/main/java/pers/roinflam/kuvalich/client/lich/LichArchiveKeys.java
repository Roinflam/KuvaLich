package pers.roinflam.kuvalich.client.lich;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.mercy.MercyClient;

/**
 * 打开玄骸档案的唯一入口
 *
 * <p>档案只能从灭骸之扉进去：扉界面右下角那行字、或在扉里按档案键（默认 K），都是先关掉扉再调这里。
 * 游戏里没开界面时按 K 不开档案，免得和别的模组游戏中用的 K 抢键。档案里再按一次档案键或按 Esc 关掉
 * （档案自己处理）。正在处决（本地玩家是处决者、还没解锁）时打不开。档案打开后自己会向服务端要一份最新档案，
 * 之后每 5 秒再要一次。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LichArchiveKeys {

    private LichArchiveKeys() {
    }

    /** 能开就打开档案；返回有没有打开 */
    public static boolean open(Minecraft mc) {
        if (mc.screen != null || mc.player == null || MercyClient.isActive()) {
            return false;
        }
        mc.setScreen(new LichArchiveScreen());
        return true;
    }
}
