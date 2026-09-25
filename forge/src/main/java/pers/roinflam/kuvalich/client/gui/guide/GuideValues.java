package pers.roinflam.kuvalich.client.gui.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文里的占位符与条件求值：{@code {cfg:键|格式}}、{@code {item:…}}、{@code {lang:…}} 和各级 {@code if}
 *
 * <p>一个实例对应<b>一份</b>配置快照（{@link GuideClientConfig#snapshot()}），界面打开时建一次，
 * 快照版本变了就整份换掉 —— 所以这里可以放心按键缓存解析结果，不用考虑失效。</p>
 *
 * <h3>数字怎么显示</h3>
 * <p>快照里的 double 是 {@code String.valueOf} 出来的（{@code 1.0}、{@code 0.075}、偶尔 {@code 1.0E-4}），
 * 直接显示会满书都是「1.0 倍」。统一走 {@link #formatNumber}：最多两位小数（{@code round:D} 可改）、
 * 四舍五入、去掉尾零。用 {@link BigDecimal} 而不是 {@code String.format}：后者在 0.075×100 这种
 * 浮点误差（7.499999…）上会给出 7.5 还是 7.49 取决于位数，前者先按十进制字面量转换再舍入，结果稳定。</p>
 *
 * <h3>缺键</h3>
 * <p>内容写手写错键名、或者服务端版本比客户端旧没有这一项时，显示警告色的「?」并按键只报一次。
 * 不显示默认值：默认值恰恰是「服主改过的配置」最可能不等于的那个数，显示出来就是误导。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideValues {

    /** 占位符的解析结果 */
    public static final class Resolved {
        public final String text;
        /** 缺键 / 缺物品等：界面用警告色显示 */
        public final boolean missing;

        Resolved(String text, boolean missing) {
            this.text = text;
            this.missing = missing;
        }
    }

    private static final Resolved MISSING = new Resolved("?", true);
    /** 条件 {@code cfg:键}、{@code cfg:键>=数}；冒号后、运算符两边的空白都容忍（整合包作者常写成 {@code cfg:a > 0}） */
    private static final Pattern CFG_COND = Pattern.compile(
            "^cfg:\\s*([A-Za-z0-9_.]+)\\s*(?:(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?))?$");
    private static final String DISABLED_TYPES_KEY = "modules.disabledModuleTypes";

    private final Map<String, String> snapshot;
    private final boolean synced;
    /** 键 → 解析后的值（Boolean / Double / List&lt;String&gt; / String） */
    private final Map<String, Object> typed = new HashMap<>();
    private final Set<String> disabledModuleTypes;
    private final Map<String, Resolved> itemNames = new HashMap<>();

    // 本地化字样，构造时取一次：实例的寿命不会跨过语言切换（切语言会重建整个视图）
    private final String wordOn;
    private final String wordOff;
    private final String wordYes;
    private final String wordNo;
    private final String wordNone;
    private final String listSeparator;

    /**
     * @param snapshot 配置快照（键 → 字符串值）
     * @param synced   是否来自服务端
     */
    public GuideValues(@Nonnull Map<String, String> snapshot, boolean synced) {
        this.snapshot = snapshot;
        this.synced = synced;
        this.wordOn = I18n.get("kuvalich.guide.fmt.on");
        this.wordOff = I18n.get("kuvalich.guide.fmt.off");
        this.wordYes = I18n.get("kuvalich.guide.fmt.yes");
        this.wordNo = I18n.get("kuvalich.guide.fmt.no");
        this.wordNone = I18n.get("kuvalich.guide.fmt.none");
        this.listSeparator = I18n.get("kuvalich.guide.fmt.list_sep");
        Set<String> disabled = new HashSet<>();
        Object list = value(DISABLED_TYPES_KEY);
        if (list instanceof List<?> l) {
            for (Object o : l) {
                // 不 trim：ModuleConfig.rebuildCache 是原样放进集合、按 type 精确比较的，
                // 服主写成 " vitality" 时游戏里并不会禁用 vitality，书也不该说它被禁用了
                disabled.add(String.valueOf(o));
            }
        }
        this.disabledModuleTypes = Collections.unmodifiableSet(disabled);
    }

    /** 当前数值是否来自服务端下发 */
    public boolean isSynced() {
        return synced;
    }

    // ==================== 占位符 ====================

    /**
     * 解析一个占位符（花括号里的全部内容）
     *
     * @param token 例如 {@code cfg:kuva_lich.firstStage|int}、{@code item:kuvalich:forma}
     * @return 显示文本；解析不了时是警告色的「?」
     */
    @Nonnull
    public Resolved placeholder(@Nonnull String token) {
        if (token.startsWith("cfg:")) {
            return cfg(token.substring(4));
        }
        if (token.startsWith("item:")) {
            return itemName(token.substring(5).trim());
        }
        if (token.startsWith("lang:")) {
            String key = token.substring(5).trim();
            Language language = Language.getInstance();
            if (!language.has(key)) {
                GuideLog.warnOnce("lang:" + key, "语言文件里没有键 " + key);
                return MISSING;
            }
            // 取原文不走 I18n.get：带 %s 的翻译在无参数格式化时会变成「Format error」
            String text = ChatFormatting.stripFormatting(language.getOrDefault(key));
            return new Resolved(text == null ? "" : text, false);
        }
        GuideLog.warnOnce("ph:" + token, "未知占位符 {" + token + "}");
        return MISSING;
    }

    /**
     * {@code cfg:键|运算…|显示格式}
     */
    private Resolved cfg(String spec) {
        String[] parts = spec.split("\\|", -1);
        String key = parts[0].trim();
        Object v = value(key);
        if (v == null) {
            GuideLog.warnOnce("cfg:" + key, "配置快照里没有键 " + key
                    + (synced ? "（来自服务端，可能服务端版本较旧）" : "（本地配置）"));
            return MISSING;
        }
        int decimals = 2;
        String format = "raw";
        boolean sign = false;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            int colon = p.indexOf(':');
            if (colon < 0) {
                if ("sign".equals(p)) {
                    // 修饰而不是格式：可以和 pct / pctv / x 等任意组合（{cfg:…|sign|pct}）
                    sign = true;
                } else {
                    format = p;
                }
                continue;
            }
            String op = p.substring(0, colon);
            double arg;
            try {
                arg = Double.parseDouble(p.substring(colon + 1));
            } catch (NumberFormatException e) {
                GuideLog.warnOnce("op:" + spec, "{cfg:" + spec + "} 里的运算参数不是数字: " + p);
                continue;
            }
            if (!(v instanceof Double d)) {
                GuideLog.warnOnce("opnum:" + spec, "{cfg:" + spec + "}：值不是数字，忽略运算 " + p);
                continue;
            }
            switch (op) {
                case "mul" -> v = d * arg;
                case "div" -> v = arg == 0 ? d : d / arg;
                case "add" -> v = d + arg;
                case "round" -> {
                    decimals = Math.max(0, Math.min(6, (int) arg));
                    v = round(d, decimals);
                }
                default -> GuideLog.warnOnce("opname:" + spec, "{cfg:" + spec + "}：未知运算 " + op);
            }
        }
        String text = display(v, format, decimals, spec);
        if (sign && v instanceof Double d && d > 0 && hasNonZeroDigit(text)) {
            // 带符号显示：正数补「+」，负数本来就有「-」。内容里别再在占位符前写死「+」——
            // 服主把数值改成负数时会显示成「+-10%」；默认为负、没写符号的改成正数后又看不出是加是减。
            // 舍入后显示为 0 的不补，免得出现「+0%」
            text = "+" + text;
        }
        return new Resolved(text, false);
    }

    private static boolean hasNonZeroDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '1' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    private String display(Object v, String format, int decimals, String spec) {
        switch (format) {
            case "", "raw" -> {
                return auto(v, decimals);
            }
            case "int" -> {
                // formatNumber 按 HALF_UP 舍入：2.5 → 3，与「四舍五入」的直觉一致（Math.rint 会给 2）
                return v instanceof Double d ? formatNumber(d, 0) : auto(v, decimals);
            }
            case "pct" -> {
                if (v instanceof Double d) {
                    return formatNumber(d * 100.0, decimals) + "%";
                }
            }
            case "pctv" -> {
                if (v instanceof Double d) {
                    return formatNumber(d, decimals) + "%";
                }
            }
            case "x" -> {
                if (v instanceof Double d) {
                    return formatNumber(d, decimals) + "×";
                }
            }
            case "sec" -> {
                if (v instanceof Double d) {
                    return I18n.get("kuvalich.guide.fmt.sec", formatNumber(d / 20.0, decimals));
                }
            }
            case "bool" -> {
                return truthy(v) ? wordOn : wordOff;
            }
            case "yesno" -> {
                return truthy(v) ? wordYes : wordNo;
            }
            case "list" -> {
                return auto(v, decimals);
            }
            case "count" -> {
                if (v instanceof List<?> l) {
                    return String.valueOf(l.size());
                }
            }
            default -> {
                GuideLog.warnOnce("fmt:" + spec, "{cfg:" + spec + "}：未知显示格式 " + format + "，按原样显示");
                return auto(v, decimals);
            }
        }
        GuideLog.warnOnce("fmtv:" + spec, "{cfg:" + spec + "}：格式 " + format + " 不适用于这个值，按原样显示");
        return auto(v, decimals);
    }

    private String auto(Object v, int decimals) {
        if (v instanceof Boolean b) {
            return b ? wordOn : wordOff;
        }
        if (v instanceof Double d) {
            return formatNumber(d, decimals);
        }
        if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                return wordNone;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(listSeparator);
                }
                sb.append(l.get(i));
            }
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private Resolved itemName(String id) {
        return itemNames.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
                GuideLog.warnOnce("item:" + key, "{item:" + key + "}：物品不存在");
                return MISSING;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            try {
                return new Resolved(new ItemStack(item).getHoverName().getString(), false);
            } catch (RuntimeException e) {
                // 别的模组的物品取名字可能依赖世界 / NBT，抛出来会让整篇排版失败：只把这一处换成「?」
                GuideLog.warnOnce("itemname:" + key, "{item:" + key + "}：取物品名失败 —— " + e);
                return MISSING;
            }
        });
    }

    // ==================== 条件 ====================

    /**
     * 一组条件全部成立（空表 = 无条件）
     *
     * @param conds 条件列表
     * @return 是否成立
     */
    public boolean test(@Nonnull List<String> conds) {
        for (String c : conds) {
            if (!test(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 单个条件，可带前缀 {@code !} 取反；用 {@code ||} 连接的几项任一成立即成立
     *
     * <p>{@code ||} 是为「任一额外槽位开着」这类说明加的：副手、四个盔甲位、饰品栏各有开关，
     * 只写「并且」的话要拆成一堆互斥的块。</p>
     *
     * <h3>判定不了时</h3>
     * <p>键不在快照里、语法不对、前缀不认识，这三种是「不知道」而不是「不成立」：<b>不管前面有没有 {@code !}
     * 都按不成立处理</b>（{@code ||} 里的这一项同样算不成立）。要是先算成 false 再取反，
     * {@code !cfg:缺失键} 就成立了 —— 书里成对写的「开着时这样 / 关着时那样」会两头都错：
     * 服务端版本旧、缺一个键，正面的说明全藏掉，「已关闭」的替代说法反而全显示出来。
     * 写坏了宁可藏起来，和加载器的原则一致。</p>
     *
     * @param cond 条件
     * @return 是否成立
     */
    public boolean test(@Nonnull String cond) {
        if (cond.contains("||")) {
            for (String part : cond.split(java.util.regex.Pattern.quote("||"))) {
                if (!part.isBlank() && test(part)) {
                    return true;
                }
            }
            return false;
        }
        String s = cond.trim();
        boolean negate = false;
        while (s.startsWith("!")) {
            negate = !negate;
            s = s.substring(1).trim();
        }
        Boolean result = evaluate(s);
        return result != null && negate != result;
    }

    /**
     * @return 成立 / 不成立；判定不了（缺键、语法错、不认识的前缀）为 null
     */
    @Nullable
    private Boolean evaluate(String s) {
        if (s.startsWith("cfg:")) {
            Matcher m = CFG_COND.matcher(s);
            if (!m.matches()) {
                GuideLog.warnOnce("cond:" + s, "条件语法不对，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            String key = m.group(1);
            Object v = value(key);
            if (v == null) {
                GuideLog.warnOnce("cfg:" + key, "条件里的配置键不存在，按不成立处理（取反也不成立）: " + key);
                return null;
            }
            if (m.group(2) == null) {
                return truthy(v);
            }
            double left;
            if (v instanceof Double d) {
                left = d;
            } else if (v instanceof Boolean b) {
                left = b ? 1 : 0;
            } else if (v instanceof List<?> l) {
                left = l.size();
            } else {
                GuideLog.warnOnce("condnum:" + s, "条件比较的不是数字，按不成立处理（取反也不成立）: " + s);
                return null;
            }
            double right = Double.parseDouble(m.group(3));
            final double eps = 1e-9;
            return switch (m.group(2)) {
                case ">" -> left > right + eps;
                case ">=" -> left >= right - eps;
                case "<" -> left < right - eps;
                case "<=" -> left <= right + eps;
                case "==" -> Math.abs(left - right) <= eps;
                case "!=" -> Math.abs(left - right) > eps;
                default -> false;
            };
        }
        if (s.startsWith("mod:")) {
            return ModList.get().isLoaded(s.substring(4).trim());
        }
        if (s.startsWith("module:")) {
            return !disabledModuleTypes.contains(s.substring(7).trim());
        }
        GuideLog.warnOnce("cond:" + s, "不认识的条件，按不成立处理（取反也不成立）: " + s);
        return null;
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Double d) {
            return d != 0.0;
        }
        if (v instanceof List<?> l) {
            return !l.isEmpty();
        }
        return v != null && !String.valueOf(v).isEmpty();
    }

    // ==================== 取值 ====================

    /**
     * 键对应的值，按形状转成 Boolean / Double / List / String
     *
     * @param key 配置键
     * @return 值；快照里没有这个键时为 null
     */
    @Nullable
    private Object value(String key) {
        Object cached = typed.get(key);
        if (cached != null) {
            return cached;
        }
        String raw = snapshot.get(key);
        if (raw == null) {
            return null;
        }
        Object v = parse(raw);
        typed.put(key, v);
        return v;
    }

    private static Object parse(String raw) {
        String s = raw.trim();
        if (s.startsWith("[")) {
            try {
                JsonElement el = JsonParser.parseString(s);
                if (el.isJsonArray()) {
                    JsonArray array = el.getAsJsonArray();
                    List<String> out = new ArrayList<>(array.size());
                    for (JsonElement x : array) {
                        out.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
                    }
                    return Collections.unmodifiableList(out);
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON 就当普通字符串
            }
        }
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        try {
            double d = Double.parseDouble(s);
            if (!Double.isNaN(d)) {
                return d;
            }
        } catch (NumberFormatException ignored) {
            // 不是数字
        }
        return raw;
    }

    // ==================== 数字 ====================

    private static double round(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return v;
        }
        return new BigDecimal(Double.toString(v)).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 数字显示：最多 maxDecimals 位小数、四舍五入、去尾零（{@code 1.0 → 1}、{@code 0.250 → 0.25}）
     *
     * @param v           值
     * @param maxDecimals 最多几位小数
     * @return 文本
     */
    public static String formatNumber(double v, int maxDecimals) {
        if (Double.isNaN(v)) {
            return "?";
        }
        if (Double.isInfinite(v) || Math.abs(v) >= 1e15) {
            // Double.MAX_VALUE 这类「无上限」写法，展开成三百多位数字毫无意义
            return v < 0 ? "-∞" : "∞";
        }
        BigDecimal bd = new BigDecimal(Double.toString(v)).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.signum() == 0) {
            return "0";
        }
        return bd.toPlainString();
    }
}
