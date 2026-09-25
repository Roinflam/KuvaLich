package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 打开冒险指南的唯一入口（物品右键经 DistExecutor 调到这里）
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideScreenOpener {

    private GuideScreenOpener() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }
}
