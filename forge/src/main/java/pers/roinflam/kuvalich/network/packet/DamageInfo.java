package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.module.weapon.DamageDisplayTracker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一条伤害数字
 *
 * <p>数值、档位、前缀、图标、护盾分开存，同一只怪身上短时间内的多条数字可以在客户端就地合并：
 * 数值累加、档位取最高、图标取并集，然后重建显示用的字形序列。</p>
 *
 * <p>颜色不再用 § 颜色码：包里的颜色码只用来判档位（白 / 蓝 / 紫 / 黄 / 橙 / 红），
 * 真正的 RGB 由渲染器按档位给。元素小图标和数字同色（红字后面的火焰图标是红的），
 * 只有护盾图标留着自己的蓝。正文和描边各缓存一份字形序列，只在新建和合并时重建，渲染时不再逐帧解析颜色码。</p>
 *
 * <p>寿命由客户端统一定：暴击三档 1050 ms、最后 280 ms 线性淡出；白、蓝、紫 1200 ms、最后 300 ms 淡出；
 * 淡到 α 0.1 就算结束（原版字体把极低的 alpha 当成不透明，不能一路淡到 0）。包里带的显示时长不再用。</p>
 *
 * <p>本类不引用任何客户端类，服务端也能安全加载。</p>
 */
public class DamageInfo {

    // ========== 档位 ==========

    public static final int TIER_WHITE = 0;
    public static final int TIER_BLUE = 1;
    public static final int TIER_PURPLE = 2;
    public static final int TIER_YELLOW = 3;
    public static final int TIER_ORANGE = 4;
    public static final int TIER_RED = 5;

    private static final String COLOR_BLUE = "§b";
    private static final String COLOR_PURPLE = "§5";
    private static final String COLOR_YELLOW = "§e";
    private static final String COLOR_ORANGE = "§6";
    private static final String COLOR_RED = "§c";

    /** 护盾图标的颜色（和护盾蓝字同色，只有它不跟数字同色） */
    private static final int SHIELD_RGB = 0x55FFFF;

    /** 暴击三档的寿命和淡出 */
    private static final long LIFE_CRIT_MS = 1050L;
    private static final long FADE_CRIT_MS = 280L;
    /** 白、蓝、紫的寿命和淡出 */
    private static final long LIFE_PLAIN_MS = 1200L;
    private static final long FADE_PLAIN_MS = 300L;
    /** 淡到这个透明度就不画了 */
    public static final float MIN_ALPHA = 0.1F;

    // ========== 位置与时间 ==========

    /** 世界坐标（合并时保留第一条的位置） */
    public final Vec3 position;

    /** 新建时在相机平面里随机偏的那一点（之后不变），由渲染器写入 */
    public double offX, offY, offZ;

    /** 开始时间（毫秒，客户端演出钟） */
    public final long startTime;

    /** 结束时间（毫秒；合并时顺延） */
    private long endTime;

    /** 新建时要不要弹一下（合并进来的不弹） */
    public final boolean pop;

    // ========== 合并状态 ==========

    @Nullable
    private final MergeKey mergeKey;

    private final long mergeDeadline;

    /** 持续伤害：合并组非空且是白字（元素的持续伤害），字小一号、不弹 */
    private final boolean elementGroup;

    private float amount;

    private int tier;

    /** 前缀（宠物、女仆的攻击带🎀；合并时只要有一条带前缀就保留），已去掉颜色码 */
    private String prefix;

    /** 图标（按首次出现顺序去重，已去掉颜色码） */
    private final Set<String> icons;

    private boolean hitShield;

    // ========== 缓存的显示 ==========

    /** 正文：数字和元素图标不带颜色（画的时候用档位色），护盾图标带自己的蓝 */
    private FormattedCharSequence body;

    /** 描边用：同一串字形，全部不带颜色（画的时候用描边色） */
    private FormattedCharSequence outline;

    /** 字宽缓存（字体像素），-1 表示要重量；字体只有客户端有，由渲染器量好写回 */
    public int width = -1;

    /**
     * @param amount        伤害数值
     * @param colorCode     颜色码（只用来判档位）
     * @param prefix        数字前缀，可为空串
     * @param suffix        数字后缀（图标串，每个图标可能自带颜色码，会被去掉），可为空串
     * @param hitShield     是否追加护盾图标
     * @param position      世界坐标
     * @param now           开始时间（毫秒）
     * @param mergeKey      合并分组键；为 null 表示不参与合并
     * @param mergeGroup    合并分组（普通伤害为空串，本模组元素伤害为元素名）
     * @param mergeWindowMs 合并窗口（毫秒），从本条创建起算；mergeKey 为 null 时忽略
     * @param popEnabled    暴击数字弹出开关
     */
    public DamageInfo(float amount, @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                      boolean hitShield, @Nonnull Vec3 position, long now,
                      @Nullable MergeKey mergeKey, @Nonnull String mergeGroup, long mergeWindowMs,
                      boolean popEnabled) {
        this.position = position;
        this.startTime = now;
        this.mergeKey = mergeKey;
        this.mergeDeadline = mergeKey == null ? now : now + mergeWindowMs;
        this.elementGroup = !mergeGroup.isEmpty();

        this.amount = amount;
        this.tier = tierOf(colorCode);
        this.prefix = stripCodes(prefix);
        this.icons = new LinkedHashSet<>(4);
        addIcons(this.icons, suffix);
        this.hitShield = hitShield;
        this.endTime = now + lifeMs(this.tier);
        this.pop = popEnabled && this.tier >= TIER_YELLOW;

        rebuild();
    }

    // ========== 合并 ==========

    /** 本条现在还能不能接收合并 */
    public boolean canMergeAt(long now) {
        return mergeKey != null && now <= mergeDeadline && !isExpired(now);
    }

    /**
     * 把一条新伤害并入本条：数值累加、档位取高、前缀有就留、图标取并集、护盾取「或」，结束时间顺延
     */
    public void merge(float amount, @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                      boolean hitShield, long now) {
        this.amount += amount;
        this.tier = Math.max(this.tier, tierOf(colorCode));
        String p = stripCodes(prefix);
        if (!p.isEmpty()) {
            this.prefix = p;
        }
        addIcons(this.icons, suffix);
        this.hitShield |= hitShield;
        this.endTime = Math.max(this.endTime, now + lifeMs(this.tier));
        rebuild();
    }

    @Nullable
    public MergeKey getMergeKey() {
        return mergeKey;
    }

    // ========== 显示 ==========

    /** 档位：0 白 / 1 蓝 / 2 紫 / 3 黄 / 4 橙 / 5 红 */
    public int tier() {
        return tier;
    }

    /** 是不是持续伤害数字（白色的元素伤害） */
    public boolean isDot() {
        return elementGroup && tier == TIER_WHITE;
    }

    public FormattedCharSequence body() {
        return body;
    }

    public FormattedCharSequence outline() {
        return outline;
    }

    /** 此刻的透明度：最后一段线性淡出 */
    public float alphaAt(long now) {
        long fade = tier >= TIER_YELLOW ? FADE_CRIT_MS : FADE_PLAIN_MS;
        long left = endTime - now;
        if (left >= fade) {
            return 1.0F;
        }
        return Math.max(0.0F, (float) left / fade);
    }

    /** 过期：淡到 {@link #MIN_ALPHA} 以下 */
    public boolean isExpired(long now) {
        return alphaAt(now) < MIN_ALPHA;
    }

    /** 新建以来过去了多少毫秒 */
    public long ageMs(long now) {
        return now - startTime;
    }

    private void rebuild() {
        StringBuilder sb = new StringBuilder(24);
        sb.append(prefix).append(DamagePacket.formatDamage(amount));
        for (String icon : icons) {
            sb.append(icon);
        }
        String plain = sb.toString();
        String shield = hitShield ? stripCodes(DamageDisplayTracker.SHIELD_ICON) : "";
        if (shield.isEmpty()) {
            body = Component.literal(plain).getVisualOrderText();
            outline = body;
        } else {
            MutableComponent c = Component.literal(plain);
            c.append(Component.literal(shield).withStyle(Style.EMPTY.withColor(SHIELD_RGB)));
            body = c.getVisualOrderText();
            outline = Component.literal(plain + shield).getVisualOrderText();
        }
        width = -1;
    }

    private static long lifeMs(int tier) {
        return tier >= TIER_YELLOW ? LIFE_CRIT_MS : LIFE_PLAIN_MS;
    }

    /** 颜色码 → 档位 */
    public static int tierOf(@Nonnull String code) {
        switch (code) {
            case COLOR_RED:
                return TIER_RED;
            case COLOR_ORANGE:
                return TIER_ORANGE;
            case COLOR_YELLOW:
                return TIER_YELLOW;
            case COLOR_PURPLE:
                return TIER_PURPLE;
            case COLOR_BLUE:
                return TIER_BLUE;
            default:
                return TIER_WHITE;
        }
    }

    /** 去掉串里所有的 § 颜色码（§ 加后面一个字符） */
    private static String stripCodes(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.indexOf('§') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§') {
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 把后缀图标串拆成单个图标塞进集合：后缀形如 {@code "§c🔥§3❄"}，按 § 的位置切开就是一个个图标，再去掉颜色码
     */
    private static void addIcons(@Nonnull Set<String> out, @Nullable String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return;
        }
        int start = 0;
        int length = suffix.length();
        for (int i = 1; i < length; i++) {
            if (suffix.charAt(i) == '§') {
                addIcon(out, suffix.substring(start, i));
                start = i;
            }
        }
        addIcon(out, suffix.substring(start));
    }

    private static void addIcon(Set<String> out, String raw) {
        String g = stripCodes(raw);
        if (!g.isEmpty()) {
            out.add(g);
        }
    }

    // ========== 元素图标表 ==========

    /**
     * 元素序号对应的图标字形（不带颜色）。和伤害数字后缀里的元素图标是同一套，状态行也用它。
     * 序号：0 fire / 1 ice / 2 poison / 3 electricity / 4 slash / 5 puncture / 6 impact / 7 gas /
     * 8 radiation / 9 magnetic / 10 corrosion / 11 explosion / 12 virus
     */
    private static final String[] ELEMENT_GLYPHS = {
            "🔥", "❄", "☠", "⚡", "☾", "†", "🔨",
            "💨", "☢", "🧲", "🧪", "💥", "🦠"
    };

    /** 元素名（{@code kuvaweapon.type.*} 的写法）按序号排 */
    private static final String[] ELEMENT_NAMES = {
            "fire", "ice", "poison", "electricity", "slash", "puncture", "impact",
            "gas", "radiation", "magnetic", "corrosion", "explosion", "virus"
    };

    /** 元素种数 */
    public static final int ELEMENT_COUNT = ELEMENT_NAMES.length;

    /** 序号 → 图标；越界返回空串 */
    public static String elementGlyph(int idx) {
        return idx >= 0 && idx < ELEMENT_GLYPHS.length ? ELEMENT_GLYPHS[idx] : "";
    }

    /** 序号 → 元素名；越界返回空串 */
    public static String elementName(int idx) {
        return idx >= 0 && idx < ELEMENT_NAMES.length ? ELEMENT_NAMES[idx] : "";
    }

    /** 元素名 → 序号；不认识返回 -1 */
    public static int elementIndex(@Nullable String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < ELEMENT_NAMES.length; i++) {
            if (ELEMENT_NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 合并分组键：同一只受击生物 + 同一分组的数字才会合并 */
    public record MergeKey(int entityId, @Nonnull String group) {
    }
}
