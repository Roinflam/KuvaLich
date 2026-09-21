package pers.roinflam.kuvalich.module.weapon.panel;

import java.util.Locale;

/**
 * 面板数值的格式化方式
 *
 * <p>⭐ 本枚举收敛了以前散在三个文件里的重复格式化逻辑：
 * {@code execute_chance} 的「保留三位小数再去尾零」在
 * {@code WeaponModuleHandler}、{@code AbstractItemModule}、{@code ExtraSlotTooltipHelper}
 * 里各写了一份，改一处另外两处不会跟着变。</p>
 *
 * <p>⭐ 一律显式传 {@link Locale#ROOT}：原先的 {@code String.format("%.3f", v)}
 * 用的是默认 Locale，在德语 / 法语等把小数点写成逗号的区域会产出 {@code 0,010}，
 * 后面那句 {@code replaceAll("0+$", "").replaceAll("\\.$", "")} 就去不掉尾零，
 * 也可能把逗号留在末尾。</p>
 *
 * @author RoinFlam
 */
public enum ValueFmt {

    /** 百分比整数，带正号：{@code +180%} */
    PERCENT_SIGNED {
        @Override
        public String format(double v) {
            return sign(v) + Math.round(v * 100) + "%";
        }
    },

    /** 百分比整数，不带正号（面板最终值）：{@code 120%} */
    PERCENT {
        @Override
        public String format(double v) {
            return Math.round(v * 100) + "%";
        }
    },

    /** 一位小数百分比，对应原 {@code attackSpeed} 的显示口径 */
    PERCENT_1F {
        @Override
        public String format(double v) {
            return String.format(Locale.ROOT, "%.1f%%", v * 100);
        }
    },

    /** 倍率：{@code x2.4} */
    MULTIPLIER {
        @Override
        public String format(double v) {
            return "x" + String.format(Locale.ROOT, "%.1f", v);
        }
    },

    /**
     * 绝对半径（米）：{@code 3.0m}
     * <p>语义是「最终爆炸半径」，公式 {@code 1 + v * 2}，对应武器面板的口径。</p>
     */
    METERS_ABS {
        @Override
        public String format(double v) {
            return String.format(Locale.ROOT, "%.1fm", 1 + v * 2);
        }
        @Override
        public boolean hiddenWhenZero() {
            return true;
        }
    },

    /**
     * 增量半径（米）：{@code +1.0m}
     * <p>语义是「加了多少」，公式 {@code v * 2}，对应额外槽位的口径。</p>
     * <p>⭐ 与 {@link #METERS_ABS} 是两种不同的语义，**不要统一**：
     * 面板要回答「这把武器炸多大」，额外槽位要回答「这身装备加了多少」。
     * 强行统一会让其中一边的数字变错。</p>
     */
    METERS_DELTA {
        @Override
        public String format(double v) {
            return sign(v) + String.format(Locale.ROOT, "%.1fm", v * 2);
        }
    },

    /**
     * 极小概率：保留三位小数并去掉尾零（{@code 0.010%} → {@code 0.01%}）
     * <p>秒杀概率这类词条数值极小，按整数百分比显示会一律变成 0%。</p>
     */
    MICRO_PERCENT {
        @Override
        public String format(double v) {
            String s = String.format(Locale.ROOT, "%.3f", v * 100);
            if (s.indexOf('.') >= 0) {
                s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return sign(v) + s + "%";
        }
        @Override
        public double epsilon() {
            return 1.0e-6;
        }
    },

    /** 纯整数（固定值加成，如战甲的 fixedHealth）：{@code +40} */
    FLAT {
        @Override
        public String format(double v) {
            return sign(v) + Math.round(v);
        }
    };

    public abstract String format(double v);

    /**
     * 低于此绝对值视为「没有这条词条」，不生成 chip。
     * <p>沿用项目里既有的 0.001 阈值（见 ExtraSlotTooltipHelper），
     * 只有 {@link #MICRO_PERCENT} 因为数值本身就很小而放宽。</p>
     */
    public double epsilon() {
        return 1.0e-3;
    }

    /**
     * 该格式化器在原始值为 0 时是否应当隐藏。
     * <p>{@link #METERS_ABS} 在 v=0 时会显示成 {@code 1.0m}（基础半径），
     * 看起来像「有这条词条」，所以要显式隐藏。</p>
     */
    public boolean hiddenWhenZero() {
        return false;
    }

    static String sign(double v) {
        return v >= 0 ? "+" : "";
    }
}
