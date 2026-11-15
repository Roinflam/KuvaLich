package pers.roinflam.kuvalich.client.ephemera;

import pers.roinflam.kuvalich.ephemera.EphemeraType;

import java.util.HashMap;
import java.util.Map;

/**
 * 款式 id → 画法。还没写画法的款一律用占位几何，加一款 = 写一个 {@link EphemeraStyle} 再在这里登记。
 */
public final class EphemeraStyles {

    public static final EphemeraStyle PLACEHOLDER = new PlaceholderStyle();

    private static final Map<String, EphemeraStyle> BY_ID = new HashMap<>();

    static {
        register(EphemeraType.SURGE, new SurgeStyle());
        register(EphemeraType.ORBIT, new OrbitStyle());
        register(EphemeraType.PLUME, new PlumeStyle());
    }

    private EphemeraStyles() {
    }

    public static void register(EphemeraType type, EphemeraStyle style) {
        BY_ID.put(type.id(), style);
    }

    public static EphemeraStyle of(String typeId) {
        return BY_ID.getOrDefault(typeId, PLACEHOLDER);
    }
}
