package pers.roinflam.kuvalich.ephemera;

import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 幻纹款式表。id 写进玄骸卡和物品 NBT，定了就不能改名；只能往后加。
 * 首批一个赤毒武器元素一款，id 就是元素名。
 */
public final class EphemeraType {

    private static final Map<String, EphemeraType> BY_ID = new LinkedHashMap<>();

    static {
        for (String element : KuvaWeaponUtil.LICH_ELEMENTS) {
            register(new EphemeraType(element, element));
        }
    }

    private final String id;
    private final String element;

    private EphemeraType(String id, String element) {
        this.id = id;
        this.element = element;
    }

    private static void register(EphemeraType type) {
        BY_ID.put(type.id, type);
    }

    public String id() {
        return id;
    }

    public String element() {
        return element;
    }

    /** 这款的默认主色：元素色 */
    public int defaultColor() {
        return KuvaPalette.element(element);
    }

    @Nullable
    public static EphemeraType byId(@Nullable String id) {
        return id == null || id.isEmpty() ? null : BY_ID.get(id);
    }

    public static List<EphemeraType> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<EphemeraType> forElement(String element) {
        List<EphemeraType> out = new ArrayList<>();
        for (EphemeraType t : BY_ID.values()) {
            if (t.element.equals(element)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 按种子掷这只玄骸的幻纹，返回款式 id，空串 = 不带。
     * element 为空按种子取元素（老玄骸没定元素）；元素查不到款式也按不带
     */
    public static String roll(long seed, String element, double chance) {
        if (!LichProfile.ephemeraRolls(seed, chance)) {
            return "";
        }
        String el = element == null || element.isEmpty() ? LichProfile.weaponElement(seed) : element;
        List<EphemeraType> candidates = forElement(el);
        if (candidates.isEmpty()) {
            return "";
        }
        return candidates.get(LichProfile.ephemeraPick(seed, candidates.size())).id;
    }
}
