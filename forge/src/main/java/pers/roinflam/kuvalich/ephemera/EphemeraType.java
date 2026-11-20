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
 * 幻纹款式表，一款一个造型。id 写进玄骸卡、名册和物品 NBT，定了就不能改名，只能往后加；
 * 元素不归款式管，跟着玄骸的武器元素走，单独存（物品上是 {@link EphemeraItem#TAG_ELEMENT}）。
 * 诞生时按种子在全表里挑，所以往表里加款会改变以后诞生的玄骸挑到哪款，已经写进卡的不受影响。
 */
public final class EphemeraType {

    /** 创造栏、管理命令不写元素时用的元素 */
    public static final String DEFAULT_ELEMENT = "fire";

    private static final Map<String, EphemeraType> BY_ID = new LinkedHashMap<>();

    public static final EphemeraType SURGE = register("surge");
    public static final EphemeraType ORBIT = register("orbit");
    public static final EphemeraType PLUME = register("plume");
    public static final EphemeraType WING = register("wing");
    public static final EphemeraType COIL = register("coil");
    public static final EphemeraType VORTEX = register("vortex");
    public static final EphemeraType HALO = register("halo");
    public static final EphemeraType PLACARD = register("placard");
    public static final EphemeraType TORQUE = register("torque");
    public static final EphemeraType HORN = register("horn");
    public static final EphemeraType DISC = register("disc");
    public static final EphemeraType CRYSTAL = register("crystal");
    public static final EphemeraType SCREEN = register("screen");
    public static final EphemeraType TAILS = register("tails");
    public static final EphemeraType HOOK = register("hook");
    public static final EphemeraType THORN = register("thorn");
    public static final EphemeraType HELIX = register("helix");
    public static final EphemeraType VINE = register("vine");

    private final String id;

    private EphemeraType(String id) {
        this.id = id;
    }

    private static EphemeraType register(String id) {
        EphemeraType type = new EphemeraType(id);
        BY_ID.put(id, type);
        return type;
    }

    public String id() {
        return id;
    }

    public String nameKey() {
        return "kuvalich.ephemera.name." + id;
    }

    public String descKey() {
        return "kuvalich.ephemera.desc." + id;
    }

    @Nullable
    public static EphemeraType byId(@Nullable String id) {
        return id == null || id.isEmpty() ? null : BY_ID.get(id);
    }

    public static List<EphemeraType> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static boolean isElement(@Nullable String element) {
        return element != null && KuvaWeaponUtil.LICH_ELEMENTS.contains(element);
    }

    /** 不是赤毒武器的元素一律按默认元素 */
    public static String normalizeElement(@Nullable String element) {
        return isElement(element) ? element : DEFAULT_ELEMENT;
    }

    /** 某个元素下幻纹的默认颜色 */
    public static int defaultColor(@Nullable String element) {
        return KuvaPalette.element(normalizeElement(element));
    }

    /** 玄骸幻纹的元素：卡上定下的武器元素；老玄骸没定元素就按种子取，和它掉的武器一致 */
    public static String elementFor(long seed, @Nullable String weaponType) {
        return isElement(weaponType) ? weaponType : normalizeElement(LichProfile.weaponElement(seed));
    }

    /** 按种子掷这只玄骸的幻纹，返回款式 id，空串 = 不带 */
    public static String roll(long seed, double chance) {
        if (!LichProfile.ephemeraRolls(seed, chance)) {
            return "";
        }
        List<EphemeraType> types = all();
        return types.get(LichProfile.ephemeraPick(seed, types.size())).id;
    }
}
