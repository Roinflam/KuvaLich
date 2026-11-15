package pers.roinflam.kuvalich.ephemera;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 别人看得到的那一份幻纹：款式、元素、两色、渐变、大小、高度。玩家的从幻纹格里那件物品读，玄骸、盟友的用默认色。
 * 服务端判「变没变」、包里传的、客户端状态表存的、渲染器读的都是它，比较用 record 自带的 equals。
 */
public record EphemeraLook(String type, String element, int color1, int color2, int gradient, float scale,
                           float yOffset) {

    public static final EphemeraLook NONE = new EphemeraLook("", EphemeraType.DEFAULT_ELEMENT, 0, 0,
            EphemeraItem.GRADIENT_OFF, 1.0F, 0.0F);

    private static final int MAX_ID = 32;

    public EphemeraLook {
        type = EphemeraType.byId(type) == null ? "" : type;
        element = EphemeraType.normalizeElement(element);
        color1 = EphemeraItem.clampColor(color1);
        color2 = EphemeraItem.clampColor(color2);
        gradient = EphemeraItem.clampGradient(gradient);
        scale = EphemeraItem.clampScale(scale);
        yOffset = EphemeraItem.clampYOffset(yOffset);
    }

    public boolean isNone() {
        return type.isEmpty();
    }

    @Nullable
    public EphemeraType styleType() {
        return EphemeraType.byId(type);
    }

    /** 幻纹格里那件；不是有效幻纹（空、别的东西、款式查不到）就是 NONE */
    public static EphemeraLook of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof EphemeraItem)) {
            return NONE;
        }
        EphemeraType t = EphemeraItem.getType(stack);
        if (t == null) {
            return NONE;
        }
        return new EphemeraLook(t.id(), EphemeraItem.getElement(stack), EphemeraItem.getColor1(stack),
                EphemeraItem.getColor2(stack), EphemeraItem.getGradient(stack), EphemeraItem.getScale(stack),
                EphemeraItem.getYOffset(stack));
    }

    /** 玄骸、盟友：款式 + 元素，颜色取元素默认色 */
    public static EphemeraLook ofDefault(@Nullable String type, @Nullable String element) {
        if (EphemeraType.byId(type) == null) {
            return NONE;
        }
        int c = EphemeraType.defaultColor(element);
        return new EphemeraLook(type, element, c, c, EphemeraItem.GRADIENT_OFF, 1.0F, 0.0F);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(type, MAX_ID);
        if (isNone()) {
            return;
        }
        buf.writeUtf(element, MAX_ID);
        buf.writeInt(color1);
        buf.writeInt(color2);
        buf.writeByte(gradient);
        buf.writeFloat(scale);
        buf.writeFloat(yOffset);
    }

    public static EphemeraLook read(FriendlyByteBuf buf) {
        String type = buf.readUtf(MAX_ID);
        if (type.isEmpty()) {
            return NONE;
        }
        EphemeraLook look = new EphemeraLook(type, buf.readUtf(MAX_ID), buf.readInt(), buf.readInt(), buf.readByte(),
                buf.readFloat(), buf.readFloat());
        return look.isNone() ? NONE : look;
    }

    /** 实体同步数据用的形式（玄骸的 LICH_EPHEMERA），键名和物品 NBT 一致 */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (isNone()) {
            return tag;
        }
        tag.putString(EphemeraItem.TAG_TYPE, type);
        tag.putString(EphemeraItem.TAG_ELEMENT, element);
        tag.putInt(EphemeraItem.TAG_COLOR1, color1);
        tag.putInt(EphemeraItem.TAG_COLOR2, color2);
        tag.putInt(EphemeraItem.TAG_GRADIENT, gradient);
        tag.putFloat(EphemeraItem.TAG_SCALE, scale);
        tag.putFloat(EphemeraItem.TAG_Y_OFFSET, yOffset);
        return tag;
    }

    public static EphemeraLook fromTag(@Nullable CompoundTag tag) {
        if (tag == null || EphemeraType.byId(tag.getString(EphemeraItem.TAG_TYPE)) == null) {
            return NONE;
        }
        float scale = tag.contains(EphemeraItem.TAG_SCALE) ? tag.getFloat(EphemeraItem.TAG_SCALE) : 1.0F;
        return new EphemeraLook(tag.getString(EphemeraItem.TAG_TYPE), tag.getString(EphemeraItem.TAG_ELEMENT),
                tag.getInt(EphemeraItem.TAG_COLOR1), tag.getInt(EphemeraItem.TAG_COLOR2),
                tag.getInt(EphemeraItem.TAG_GRADIENT), scale, tag.getFloat(EphemeraItem.TAG_Y_OFFSET));
    }
}
