package pers.roinflam.kuvalich.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 幻纹物品图标：按款式换模型，发光那两层按主色染
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EphemeraIconSetup {

    public static final ResourceLocation STYLE_PROPERTY = new ResourceLocation(Reference.MOD_ID, "ephemera_style");

    // 序号写死在这里和 models/item/ephemera.json 的 overrides 里，往款式表加款时两边一起补
    private static final List<EphemeraType> ORDER = List.of(
            EphemeraType.SURGE, EphemeraType.ORBIT, EphemeraType.PLUME,
            EphemeraType.WING, EphemeraType.COIL, EphemeraType.VORTEX);

    // 乘法染色到不了比主色更亮的颜色，所以亮芯单放一层，染成主色往白里混一半的颜色；贴图脚本按同一个比例出预览
    private static final float HOT_MIX = 0.5F;

    private EphemeraIconSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 属性值会被夹到 0~1，所以第 n 款返回 n/10
        event.enqueueWork(() -> ItemProperties.register(KuvaLichItems.EPHEMERA.get(), STYLE_PROPERTY,
                (stack, level, entity, seed) -> {
                    EphemeraType type = EphemeraItem.getType(stack);
                    int index = type == null ? -1 : ORDER.indexOf(type);
                    return index < 0 ? 0.0F : (index + 1) / 10.0F;
                }));
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> switch (tintIndex) {
            case 1 -> EphemeraItem.getColor1(stack);
            case 2 -> mixWhite(EphemeraItem.getColor1(stack), HOT_MIX);
            default -> -1;
        }, KuvaLichItems.EPHEMERA.get());
    }

    static int mixWhite(int rgb, float t) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.round(r + (255 - r) * t);
        g = Math.round(g + (255 - g) * t);
        b = Math.round(b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }
}
