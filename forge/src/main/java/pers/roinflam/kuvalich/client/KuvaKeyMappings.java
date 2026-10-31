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
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 本模组的按键。这里只声明和注册，按下之后做什么由各自的界面类自己处理
 *
 * <ul>
 *   <li>{@link #ARCHIVE}：打开玄骸档案，默认 K；</li>
 *   <li>{@link #MERCY}：对倒地的玄骸或奴仆动手处决，默认 X。X 和原版「加载快捷栏」同键，
 *       冲突时去控制设置里改其中一个。</li>
 * </ul>
 *
 * <p>两个键都只在游戏中（没开界面）时生效，所以冲突范围取 {@link KeyConflictContext#IN_GAME}，
 * 不会和别的模组只在界面里用的同名按键互相报冲突。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KuvaKeyMappings {

    /** 控制设置里的分类名（语言键） */
    public static final String CATEGORY = "key.categories.kuvalich";

    /** 玄骸档案 */
    public static final KeyMapping ARCHIVE = new KeyMapping("key.kuvalich.lich_archive",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** 怜悯之击 */
    public static final KeyMapping MERCY = new KeyMapping("key.kuvalich.mercy",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private KuvaKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ARCHIVE);
        event.register(MERCY);
    }
}
