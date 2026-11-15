package pers.roinflam.kuvalich.ephemera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.KuvaPalette;

import java.util.ArrayList;
import java.util.List;

/**
 * 幻纹物品：款式和外观参数都在 NBT 里，每件各存各的，不能叠
 */
public class EphemeraItem extends Item {

    public static final String TAG_TYPE = "Ephemera";
    public static final String TAG_ELEMENT = "Element";
    public static final String TAG_ORIGIN = "Origin";
    public static final String TAG_COLOR1 = "Color1";
    public static final String TAG_COLOR2 = "Color2";
    public static final String TAG_GRADIENT = "Gradient";
    public static final String TAG_SCALE = "Scale";
    public static final String TAG_Y_OFFSET = "YOffset";

    public static final int GRADIENT_OFF = 0;
    public static final int GRADIENT_VERTICAL = 1;
    public static final int GRADIENT_RADIAL = 2;
    public static final int GRADIENT_FLOW = 3;

    public static final float SCALE_MIN = 0.5F;
    public static final float SCALE_MAX = 2.0F;
    public static final float Y_OFFSET_MIN = -0.5F;
    public static final float Y_OFFSET_MAX = 0.5F;
    /** 三个通道里最亮的那个至少到这个值，免得调成黑色看不见 */
    public static final int MIN_BRIGHTNESS = 0x40;

    public EphemeraItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(EphemeraType type, String element, String origin) {
        ItemStack stack = new ItemStack(KuvaLichItems.EPHEMERA.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_TYPE, type.id());
        tag.putString(TAG_ELEMENT, EphemeraType.normalizeElement(element));
        if (origin != null && !origin.isEmpty()) {
            tag.putString(TAG_ORIGIN, origin);
        }
        return stack;
    }

    // 读的时候一律不 getOrCreateTag：只读处建空 tag 会让同一件物品前后 NBT 不等，堆叠和比较都出问题

    @Nullable
    public static EphemeraType getType(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : EphemeraType.byId(tag.getString(TAG_TYPE));
    }

    /** 缺键或不认识的元素按默认元素 */
    public static String getElement(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return EphemeraType.normalizeElement(tag == null ? null : tag.getString(TAG_ELEMENT));
    }

    public static String getOrigin(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG_ORIGIN);
    }

    public static int getColor1(ItemStack stack) {
        return readColor(stack, TAG_COLOR1);
    }

    public static int getColor2(ItemStack stack) {
        return readColor(stack, TAG_COLOR2);
    }

    public static int getGradient(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_GRADIENT, Tag.TAG_ANY_NUMERIC)
                ? GRADIENT_OFF : clampGradient(tag.getInt(TAG_GRADIENT));
    }

    public static float getScale(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_SCALE, Tag.TAG_ANY_NUMERIC) ? 1.0F : clampScale(tag.getFloat(TAG_SCALE));
    }

    public static float getYOffset(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_Y_OFFSET, Tag.TAG_ANY_NUMERIC)
                ? 0.0F : clampYOffset(tag.getFloat(TAG_Y_OFFSET));
    }

    public static void setColors(ItemStack stack, int color1, int color2) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_COLOR1, clampColor(color1));
        tag.putInt(TAG_COLOR2, clampColor(color2));
    }

    public static void setGradient(ItemStack stack, int gradient) {
        stack.getOrCreateTag().putInt(TAG_GRADIENT, clampGradient(gradient));
    }

    public static void setScale(ItemStack stack, float scale) {
        stack.getOrCreateTag().putFloat(TAG_SCALE, clampScale(scale));
    }

    public static void setYOffset(ItemStack stack, float yOffset) {
        stack.getOrCreateTag().putFloat(TAG_Y_OFFSET, clampYOffset(yOffset));
    }

    /** 恢复默认：颜色、渐变、大小、高度的键全删掉，读出来就是缺省值 */
    public static void resetLook(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_COLOR1);
            tag.remove(TAG_COLOR2);
            tag.remove(TAG_GRADIENT);
            tag.remove(TAG_SCALE);
            tag.remove(TAG_Y_OFFSET);
        }
    }

    private static int readColor(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(key, Tag.TAG_ANY_NUMERIC)) {
            return clampColor(tag.getInt(key));
        }
        return getType(stack) == null ? KuvaPalette.LICH : EphemeraType.defaultColor(getElement(stack));
    }

    /** 只留 RGB 三个字节；太暗的按比例提亮到 {@link #MIN_BRIGHTNESS}，色相不变 */
    public static int clampColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max >= MIN_BRIGHTNESS) {
            return rgb & 0xFFFFFF;
        }
        if (max == 0) {
            return (MIN_BRIGHTNESS << 16) | (MIN_BRIGHTNESS << 8) | MIN_BRIGHTNESS;
        }
        r = r * MIN_BRIGHTNESS / max;
        g = g * MIN_BRIGHTNESS / max;
        b = b * MIN_BRIGHTNESS / max;
        return (r << 16) | (g << 8) | b;
    }

    public static int clampGradient(int gradient) {
        return gradient < GRADIENT_OFF || gradient > GRADIENT_FLOW ? GRADIENT_OFF : gradient;
    }

    public static float clampScale(float scale) {
        return Float.isFinite(scale) ? Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale)) : 1.0F;
    }

    public static float clampYOffset(float yOffset) {
        return Float.isFinite(yOffset) ? Math.max(Y_OFFSET_MIN, Math.min(Y_OFFSET_MAX, yOffset)) : 0.0F;
    }

    /** tooltip 的正文几行；服务端也能调（GameTest 直接比这里的文本和颜色） */
    public static List<Component> tooltipLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        EphemeraType type = getType(stack);
        TextColor muted = TextColor.fromRgb(KuvaPalette.MUTED);
        if (type == null) {
            lines.add(Component.translatable("item.kuvalich.ephemera.unknown")
                    .withStyle(s -> s.withColor(muted)));
            return lines;
        }
        String element = getElement(stack);
        TextColor elementColor = TextColor.fromRgb(EphemeraType.defaultColor(element));
        lines.add(Component.translatable("item.kuvalich.ephemera.style",
                        Component.translatable(type.nameKey()).withStyle(s -> s.withColor(elementColor)))
                .withStyle(s -> s.withColor(muted)));
        lines.add(Component.translatable("item.kuvalich.ephemera.element",
                        Component.translatable("kuvaweapon.type." + element).withStyle(s -> s.withColor(elementColor)))
                .withStyle(s -> s.withColor(muted)));
        String origin = getOrigin(stack);
        if (!origin.isEmpty()) {
            lines.add(Component.translatable("item.kuvalich.ephemera.origin",
                            Component.literal(origin).withStyle(s -> s.withColor(TextColor.fromRgb(KuvaPalette.LICH))))
                    .withStyle(s -> s.withColor(muted)));
        }
        lines.add(Component.translatable("item.kuvalich.ephemera.colors", swatch(getColor1(stack)), swatch(getColor2(stack)))
                .withStyle(s -> s.withColor(muted)));
        return lines;
    }

    public static MutableComponent swatch(int rgb) {
        return Component.literal("■").withStyle(s -> s.withColor(TextColor.fromRgb(rgb)));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        tooltip.addAll(tooltipLines(stack));
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}
