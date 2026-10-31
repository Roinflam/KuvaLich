package pers.roinflam.kuvalich.comm;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 玄骸说话时肖像要用的外观
 *
 * <p>{@code weapon} 是 {@code lichWeapon} 的下标，{@code -1} 表示不画武器；{@code element} 是元素序号，
 * {@code -1} 表示没有元素；{@code entityId} 是世界里那只玄骸的实体 id，{@code -1} 表示没有关联的实体。
 * 网络上这三项都按「值 + 1」或带符号的小整数写，所以 {@code -1} 不占多余的字节。</p>
 */
public record LichLook(int seed, int weapon, byte element, int entityId) {

    /** 没有任何外观信息（服务端没法解析玄骸时用） */
    public static final LichLook NONE = new LichLook(0, -1, (byte) -1, -1);

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(seed);
        buf.writeVarInt(Math.max(0, weapon + 1));
        buf.writeByte(element);
        buf.writeVarInt(Math.max(0, entityId + 1));
    }

    public static LichLook read(FriendlyByteBuf buf) {
        int seed = buf.readInt();
        int weapon = buf.readVarInt() - 1;
        byte element = buf.readByte();
        int entityId = buf.readVarInt() - 1;
        return new LichLook(seed, weapon, element, entityId);
    }
}
