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
        register(EphemeraType.WING, new WingStyle());
        register(EphemeraType.COIL, new CoilStyle());
        register(EphemeraType.VORTEX, new VortexStyle());
        register(EphemeraType.TORQUE, new TorqueStyle());
        register(EphemeraType.HORN, new HornStyle());
        register(EphemeraType.HALO, new HaloStyle());
        register(EphemeraType.PLACARD, new PlacardStyle());
        register(EphemeraType.DISC, new DiscStyle());
        register(EphemeraType.TAILS, new TailsStyle());
        register(EphemeraType.SCREEN, new ScreenStyle());
        register(EphemeraType.CRYSTAL, new CrystalStyle());
        register(EphemeraType.HOOK, new HookStyle());
        register(EphemeraType.HELIX, new HelixStyle());
        register(EphemeraType.THORN, new ThornStyle());
        register(EphemeraType.VINE, new VineStyle());
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
