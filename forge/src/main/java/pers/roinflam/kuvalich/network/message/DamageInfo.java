package pers.roinflam.kuvalich.network.message;

import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.module.weapon.DamageDisplayTracker;
import pers.roinflam.kuvalich.network.message.DamagePacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 伤害信息数据类
 * Damage information data class
 *
 * <p>⭐ 本次改动：不再只存一段拼好的文本，而是把「数值、颜色码、前缀、图标、护盾」拆开保存，
 * 这样同一只怪身上短时间内的多条数字可以在客户端<b>就地合并</b>：
 * 数值累加、颜色取最高一档、图标取并集，然后重新拼出显示文本。</p>
 *
 * <p>本类不引用任何客户端类，服务端也能安全加载（合并逻辑本身只是纯计算）。</p>
 */
public class DamageInfo {

    // ========== 颜色档位 / Color Rank ==========

    /** 白字（普通命中） */
    private static final String COLOR_WHITE = "\u00a7f";
    /** 蓝字（未暴击但打到护盾） */
    private static final String COLOR_BLUE = "\u00a7b";
    /** 紫字（真实伤害） */
    private static final String COLOR_PURPLE = "\u00a75";
    /** 黄字（暴击） */
    private static final String COLOR_YELLOW = "\u00a7e";
    /** 橙字（双重暴击） */
    private static final String COLOR_ORANGE = "\u00a76";
    /** 红字（三重暴击） */
    private static final String COLOR_RED = "\u00a7c";

    // ========== 渲染字段 / Render Fields ==========

    /** 显示文本（已包含§颜色代码和格式化；合并后会重建） / Display text */
    public String text;

    /** 世界坐标位置（合并时保留第一条的位置） / World position */
    public final Vec3 position;

    /** 结束时间（毫秒；合并时会顺延，保证最后一次合并后仍完整显示一段时间） / End time (milliseconds) */
    public long endTime;

    /** 开始时间（毫秒） / Start time (milliseconds) */
    public final long startTime;

    // ========== 合并状态 / Merge State ==========

    /** 合并分组键；为 null 表示本条不参与合并 */
    @Nullable
    private final MergeKey mergeKey;

    /** 合并截止时间（毫秒）：超过这个时间后到来的数字不再并入本条，而是新起一条 */
    private final long mergeDeadline;

    /** 累计数值 */
    private float amount;

    /** 当前颜色码（合并时取最高一档） */
    private String colorCode;

    /** 数字前缀（宠物、女仆攻击时为🎀；合并时只要有一条带前缀就保留） */
    private String prefix;

    /** 图标集合（按首次出现顺序去重；每个元素形如 {@code "§c🔥"}） */
    private final Set<String> icons;

    /** 是否打到过护盾（合并时取「或」） */
    private boolean hitShield;

    /**
     * 构造函数
     *
     * @param amount          伤害数值
     * @param colorCode       颜色码（如 {@code "§c"}）
     * @param prefix          数字前缀，可为空串
     * @param suffix          数字后缀（图标串，每个图标自带颜色码），可为空串
     * @param hitShield       是否追加护盾图标
     * @param position        世界坐标
     * @param startTime       开始时间（毫秒）
     * @param displayDuration 显示时长（毫秒）
     * @param mergeKey        合并分组键；为 null 表示不参与合并
     * @param mergeWindowMs   合并窗口（毫秒），从本条创建起算；mergeKey 为 null 时忽略
     */
    public DamageInfo(float amount, @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                      boolean hitShield, @Nonnull Vec3 position, long startTime, long displayDuration,
                      @Nullable MergeKey mergeKey, long mergeWindowMs) {
        this.position = position;
        this.startTime = startTime;
        this.endTime = startTime + displayDuration;
        this.mergeKey = mergeKey;
        this.mergeDeadline = mergeKey == null ? startTime : startTime + mergeWindowMs;

        this.amount = amount;
        this.colorCode = colorCode;
        this.prefix = prefix;
        this.icons = new LinkedHashSet<>(4);
        addIcons(this.icons, suffix);
        this.hitShield = hitShield;

        this.text = buildText();
    }

    // ========== 合并 / Merge ==========

    /**
     * 本条现在还能不能接收合并
     *
     * @param now 当前时间（毫秒）
     * @return 参与合并、未过合并截止时间、且自身尚未过期时返回 true
     */
    public boolean canMergeAt(long now) {
        return mergeKey != null && now <= mergeDeadline && !isExpired(now);
    }

    /**
     * 把一条新伤害并入本条，并重建显示文本
     *
     * <ul>
     *   <li>数值：累加</li>
     *   <li>颜色：取两者中档位更高的（白 &lt; 蓝 &lt; 紫 &lt; 黄 &lt; 橙 &lt; 红）</li>
     *   <li>前缀：只要有一条带前缀就保留</li>
     *   <li>图标：取并集，按首次出现顺序排列</li>
     *   <li>护盾：任一条打到护盾即显示护盾图标</li>
     *   <li>结束时间：顺延到「现在 + 显示时长」，保证最后一次合并后仍能完整看到</li>
     * </ul>
     *
     * @param amount          新伤害数值
     * @param colorCode       新伤害颜色码
     * @param prefix          新伤害前缀
     * @param suffix          新伤害后缀（图标串）
     * @param hitShield       新伤害是否打到护盾
     * @param now             当前时间（毫秒）
     * @param displayDuration 显示时长（毫秒）
     */
    public void merge(float amount, @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                      boolean hitShield, long now, long displayDuration) {
        this.amount += amount;
        if (colorRank(colorCode) > colorRank(this.colorCode)) {
            this.colorCode = colorCode;
        }
        if (!prefix.isEmpty()) {
            this.prefix = prefix;
        }
        addIcons(this.icons, suffix);
        this.hitShield |= hitShield;
        this.endTime = Math.max(this.endTime, now + displayDuration);

        this.text = buildText();
    }

    /**
     * 获取合并分组键
     *
     * @return 分组键；不参与合并时为 null
     */
    @Nullable
    public MergeKey getMergeKey() {
        return mergeKey;
    }

    /**
     * 拼出显示文本：颜色码 + 前缀 + 数字 + 图标 + 护盾图标
     *
     * <p>与改动前服务端拼文本的顺序完全一致，关闭合并时看到的东西和以前一样。</p>
     *
     * @return 显示文本
     */
    private String buildText() {
        StringBuilder builder = new StringBuilder(24);
        builder.append(colorCode).append(prefix).append(DamagePacket.formatDamage(amount));
        for (String icon : icons) {
            builder.append(icon);
        }
        if (hitShield) {
            builder.append(DamageDisplayTracker.SHIELD_ICON);
        }
        return builder.toString();
    }

    /**
     * 颜色档位：数字越大越「猛」，合并时保留档位高的
     *
     * @param code 颜色码
     * @return 档位
     */
    private static int colorRank(@Nonnull String code) {
        switch (code) {
            case COLOR_RED:
                return 5;
            case COLOR_ORANGE:
                return 4;
            case COLOR_YELLOW:
                return 3;
            case COLOR_PURPLE:
                return 2;
            case COLOR_BLUE:
                return 1;
            case COLOR_WHITE:
            default:
                return 0;
        }
    }

    /**
     * 把后缀图标串拆成单个图标塞进集合
     *
     * <p>后缀形如 {@code "§c🔥§3❄"}：每个图标以自己的 § 颜色码开头，
     * 所以按 § 的位置切开就是一个个图标。若开头没有 §（理论上不会出现），开头那一段单独算一个图标。</p>
     *
     * @param out    目标集合
     * @param suffix 后缀串，可为空串
     */
    private static void addIcons(@Nonnull Set<String> out, @Nullable String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return;
        }
        int start = 0;
        int length = suffix.length();
        for (int i = 1; i < length; i++) {
            if (suffix.charAt(i) == '\u00a7') {
                out.add(suffix.substring(start, i));
                start = i;
            }
        }
        out.add(suffix.substring(start));
    }

    // ========== 原有接口 / Existing API ==========

    /**
     * 检查是否过期
     */
    public boolean isExpired(long currentTime) {
        return currentTime > endTime;
    }

    /**
     * 计算上升高度
     */
    public double getRiseOffset(long currentTime, double riseSpeed) {
        return (currentTime - startTime) * riseSpeed;
    }

    /**
     * 合并分组键：同一只受击生物 + 同一分组的数字才会合并
     *
     * @param entityId 受击实体 ID
     * @param group    分组；普通伤害为空串，本模组元素伤害为元素名（如 {@code "fire"}）
     */
    public record MergeKey(int entityId, @Nonnull String group) {
    }

}
