package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * 打开冒险指南的入口：物品右键经 DistExecutor 调 {@link #open()}；物品提示上按住 Ctrl 查阅调 {@link #openAt}（{@link GuideLookup}）
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideScreenOpener {

    private GuideScreenOpener() {}

    /** 打开指南，回到上次看的位置；完整开场，关掉后回到游戏 */
    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    /**
     * 直接翻到某一篇的某一块（查阅用）：短开场，那一块滚到上方并亮一下，关掉后回到 parent
     *
     * @param parent   关掉指南后回到的界面（从物品栏查阅时就是物品栏），可为 null
     * @param entryKey 条目键（分类/条目）
     * @param block    块序号（{@code GuideView.EntryView#blocks()} 里的下标），-1 表示篇首
     */
    public static void openAt(@Nullable Screen parent, String entryKey, int block) {
        Minecraft.getInstance().setScreen(new GuideScreen(parent, entryKey, block, true));
    }
}
