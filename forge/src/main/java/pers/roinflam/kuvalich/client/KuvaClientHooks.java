package pers.roinflam.kuvalich.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 客户端启动时把「通用代码里要用到客户端能力」的静态钩子接上
 *
 * <p>实体类两端都会加载，不能直接引用客户端类；需要的地方留一个静态钩子，默认值在服务端也能用，
 * 客户端启动完成后在这里换成真实现。别的包要设的钩子写在各自的客户端类里，不往这里加。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KuvaClientHooks {

    private KuvaClientHooks() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 玄骸说话时的动画计时读演出钟，游戏暂停时跟着停
        event.enqueueWork(() -> AbstractKuva.CLIENT_CLOCK = KuvaFxDirector::clockMs);
    }
}
