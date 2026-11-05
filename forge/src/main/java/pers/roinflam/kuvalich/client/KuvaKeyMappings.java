package pers.roinflam.kuvalich.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.mercy.MercyKeyContext;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 本模组的按键。这里只声明和注册，按下之后做什么由各自的界面类自己处理
 *
 * <ul>
 *   <li>{@link #ARCHIVE}：在灭骸之扉界面里打开玄骸档案、在档案里关掉它，默认 K。只在界面里认，
 *       冲突范围取 {@link KeyConflictContext#GUI}：游戏里按 K 不会开档案，也不和别的模组游戏中用的 K
 *       （常见的是百科、图鉴类）互相报冲突；</li>
 *   <li>{@link #MERCY}：对倒地的玄骸或奴仆动手处决，默认 X（和星际战甲一样）。冲突范围取 {@link MercyKeyContext}：
 *       只在处决小牌亮着、或自己正在处决时才算激活，这时同键的别的按键这一下不触发；别的时候 X 照旧归原版
 *       「加载快捷栏」和别的模组，不用为了它改键。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KuvaKeyMappings {

    /** 控制设置里的分类名（语言键） */
    public static final String CATEGORY = "key.categories.kuvalich";

    /** 玄骸档案（只在灭骸之扉和档案界面里认） */
    public static final KeyMapping ARCHIVE = new KeyMapping("key.kuvalich.lich_archive",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** 怜悯之击（只在有目标可处决时激活） */
    public static final KeyMapping MERCY = new KeyMapping("key.kuvalich.mercy",
            MercyKeyContext.INSTANCE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private KuvaKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ARCHIVE);
        event.register(MERCY);
    }
}
