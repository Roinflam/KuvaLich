package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 资源重载时让指南内容失效
 *
 * <p>F3+T、换资源包、<b>切换语言</b>都会走一遍客户端资源重载（1.20 的语言界面点完成就是
 * {@code reloadResourcePacks}），所以挂在这里一处就同时覆盖了「改完 JSON 想看效果」和「换语言」。
 * 只做失效、不做加载：真正读文件等到下次打开指南时才做，没人看书时重载不多花一毫秒。</p>
 *
 * <p>单独成类而不是塞进 {@code ClientSetup}：指南的全部代码都在本包里，删掉 / 挪走本包不用去别处拆线。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideReloadListener implements ResourceManagerReloadListener {

    private GuideReloadListener() {}

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GuideReloadListener());
    }

    @Override
    public void onResourceManagerReload(@Nonnull ResourceManager manager) {
        GuideContentLoader.invalidate();
    }
}
