package pers.roinflam.kuvalich.client.lich;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 档案键（默认 K）：游戏里没开界面时按下打开玄骸档案；档案里再按一次或按 Esc 关掉（档案自己处理）
 *
 * <p>正在处决（本地玩家是处决者、还没解锁）时按了没反应。按下时顺手向服务端要一份最新档案，
 * 档案打开后自己还会每 5 秒再要一次。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichArchiveKeys {

    private LichArchiveKeys() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (KuvaKeyMappings.ARCHIVE.consumeClick()) {
            open(mc);
        }
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
