package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 模组卡悬浮展开卡：{@code kuvalich-tooltip.toml} 的 {@code [hover_card]} 小节（CLIENT 配置）
 *
 * <p>和 {@link EffectsDisplayConfig} 一样在 Loading / Reloading 里缓存进 volatile 字段，渲染线程每帧读缓存值。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoverCardConfig {

    private static ForgeConfigSpec.BooleanValue enabled;
    private static ForgeConfigSpec.IntValue sizePercent;
    private static ForgeConfigSpec.BooleanValue tilt;

    private static volatile boolean cachedEnabled = true;
    private static volatile int cachedSizePercent = 45;
    private static volatile boolean cachedTilt = true;

    private HoverCardConfig() {
    }

    static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Module card hover view", "模组卡悬停展开卡").push("hover_card");
        enabled = builder.comment(
                        "When the pointer rests on a module in an inventory slot, the icon unfolds into a full card in place. When off, the regular tooltip panel is shown instead",
                        "鼠标停在物品栏格子里的模组上时，图标原地展开为完整的模组卡。关闭后改为显示常规悬浮面板")
                .define("enabled", true);
        sizePercent = builder.comment(
                        "Size of the unfolded card in percent. At small sizes the card width snaps to the nearest step that has a matching frame",
                        "展开后卡片的大小（百分比）。较小的尺寸会就近取到有对应边框的宽度")
                .defineInRange("sizePercent", 45, 35, 100);
        tilt = builder.comment(
                        "Tilt the unfolded card toward the pointer",
                        "展开后的卡片随鼠标位置倾斜")
                .define("tilt", true);
        builder.pop();
    }

    public static boolean enabled() {
        return cachedEnabled;
    }

    public static int sizePercent() {
        return cachedSizePercent;
    }

    public static boolean tilt() {
        return cachedTilt;
    }

    private static void refresh(ModConfigEvent event) {
        if (enabled != null && event.getConfig().getSpec() == TooltipConfig.CLIENT_CONFIG) {
            cachedEnabled = enabled.get();
            cachedSizePercent = sizePercent.get();
            cachedTilt = tilt.get();
        }
    }

    @SubscribeEvent
    public static void onHoverCardConfigLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onHoverCardConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }
}
