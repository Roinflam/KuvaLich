package pers.roinflam.kuvalich.client.fx.script;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;

/**
 * 物品与模组相关的时刻：模组揭示、模组首次发现、冲刺武器蓄满
 */
@OnlyIn(Dist.CLIENT)
public final class ItemFx {

    /** 首次发现的提示音：界面放法、音量 0.5、2 秒至多一次（背包扫描一次补录好几张时只响一声） */
    private static final float DISCOVERY_VOLUME = 0.5F;
    private static final int DISCOVERY_GATE_MS = 2000;

    private ItemFx() {
    }

    /** 模组揭示（服务端发给手前 16 格内的人） */
    public static void moduleReveal(KuvaFxPacket p) {
        WarframeFx.moduleReveal(p);
    }

    /** 增量的模组发现包里有以前没见过的模组：补一声提示（图鉴格子自己的解锁动画照旧） */
    public static void onModuleDiscovered() {
        if (Minecraft.getInstance().level == null || !KuvaSoundPlayer.gate("module_discovered", DISCOVERY_GATE_MS)) {
            return;
        }
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.ITEM_MODULE_REVEAL.get(), DISCOVERY_VOLUME, 1.0F));
    }

    /**
     * 冲刺武器（旗帜、收割者·Prime）蓄满的那一 tick，只给持有者本人：手前一个小光环、几个光点和提示音
     *
     * @param holder 正在蓄力的实体（只有本地玩家会触发）
     */
    public static void chargeReady(LivingEntity holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || holder != mc.player) {
            return;
        }
        WarframeFx.chargeReady(holder);
    }
}
