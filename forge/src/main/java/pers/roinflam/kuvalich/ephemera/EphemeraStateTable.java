package pers.roinflam.kuvalich.ephemera;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * 客户端「哪个实体带着什么幻纹」的表，按实体 id 存。玩家的由 EphemeraStatePacket 写，玄骸、盟友的由它们的同步数据写。
 * 包到的时候实体还没进世界也照存，渲染时取不到实体就跳过；实体离开世界删那一条，登出、世界卸载整表清空
 * （换维度后实体 id 会在新世界复用）。
 *
 * <p>只在客户端主线程上读写。放在公共包里是为了 GameTest 能直接测，类本身不碰任何客户端类。</p>
 */
public final class EphemeraStateTable {

    public static final EphemeraStateTable CLIENT = new EphemeraStateTable();

    private final Int2ObjectOpenHashMap<EphemeraLook> looks = new Int2ObjectOpenHashMap<>();
    /** 每变一次加一，渲染器据此知道哪几条要丢掉自己留的状态 */
    private int revision;

    public void put(int entityId, EphemeraLook look) {
        if (look == null || look.isNone()) {
            remove(entityId);
            return;
        }
        EphemeraLook old = looks.put(entityId, look);
        if (!look.equals(old)) {
            revision++;
        }
    }

    public void remove(int entityId) {
        if (looks.remove(entityId) != null) {
            revision++;
        }
    }

    public void clear() {
        if (!looks.isEmpty()) {
            looks.clear();
            revision++;
        }
    }

    public EphemeraLook get(int entityId) {
        EphemeraLook look = looks.get(entityId);
        return look == null ? EphemeraLook.NONE : look;
    }

    public boolean isEmpty() {
        return looks.isEmpty();
    }

    public int size() {
        return looks.size();
    }

    public int revision() {
        return revision;
    }

    /** 拷一份 id 出来，遍历期间表被改（实体离开世界的事件）也不出错 */
    public IntArrayList ids(IntArrayList into) {
        into.clear();
        into.addAll(looks.keySet());
        return into;
    }
}
