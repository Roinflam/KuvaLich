package pers.roinflam.kuvalich.guide;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.Gson;
import net.minecraftforge.common.ForgeConfigSpec;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冒险指南用的配置快照：把两份 COMMON 配置拍平成「键 → 字符串值」
 *
 * <h3>为什么要自己下发</h3>
 * <p>{@code kuvalich-common.toml} 与 {@code kuvalich-modules.toml} 注册为 {@code Type.COMMON}，
 * Forge 只同步 {@code Type.SERVER}，所以联机时客户端读到的是<b>它自己那份</b> toml。
 * 指南里写的数值（没收概率、解密阈值、叠层上限……）要和服务端实际生效的一致，
 * 就只能登录时把服务端的值打包发过去（{@code GuideConfigSyncPacket}）。</p>
 *
 * <h3>键名</h3>
 * <p>{@code 节.项}，与 toml 里的路径一致，例如 {@code kuva_lich.confiscationChance}、
 * {@code modules.disabledModuleTypes}。两份配置的顶层节（kuva_lich / ore_gen / kuva_weapon / modules）
 * 互不重名，所以不加文件前缀。</p>
 *
 * <h3>值的编码</h3>
 * <ul>
 *   <li>布尔、数字、字符串：{@code String.valueOf}（double 可能是 {@code 1.0E-4} 这种写法，客户端用
 *       {@code Double.parseDouble} 解析即可）</li>
 *   <li>枚举：{@code name()}</li>
 *   <li>列表：JSON 数组字符串（Gson），客户端按需解析</li>
 * </ul>
 *
 * <p>遍历 {@code ForgeConfigSpec.getValues()} 而不是手写 170 多项：以后加配置项不用回来改这里。</p>
 *
 * <h3>只在启动时生效的项</h3>
 * <p>矿物生成（{@code ore_gen.*}）和刷怪权重 / 数量是 {@code KuvaLichBiomeModifier} 在服务端启动时读一次、
 * 烘焙进世界生成的，热重载改了 toml 也要重启才生效。可热重载会立刻重发快照，书里就会先显示还没生效的新值
 * （权重改成 0 时甚至把刷怪说明整段藏掉，而怪照样在刷）。所以那边读值时顺手 {@link #pinStartupValue} 登记，
 * {@link #capture()} 用登记值盖掉同名键 —— 书里显示的是本次启动实际用上的值。集成服每开一次世界都会重跑
 * biome modifier、覆盖登记，不用额外清理。</p>
 *
 * @author RoinFlam
 */
public final class GuideConfigSnapshot {

    private static final Gson GSON = new Gson();

    /** 本次启动实际烘焙进世界生成的配置值：键 → 编码后的字符串（见类注释「只在启动时生效的项」） */
    private static final Map<String, String> STARTUP_PINNED = new ConcurrentHashMap<>();

    private GuideConfigSnapshot() {}

    /**
     * 登记一项「只在服务端启动时读取」的配置此刻实际用上的值
     *
     * <p>在 biome modifier 这类只跑一次的地方读完配置后调用；之后热重载改 toml，
     * {@link #capture()} 仍按这里登记的值下发，直到下次启动重新登记。</p>
     *
     * @param spec  配置项（用它的路径当键，和 {@link #capture()} 的键一致）
     * @param value 实际用上的值
     */
    public static void pinStartupValue(@Nonnull ForgeConfigSpec.ConfigValue<?> spec, Object value) {
        STARTUP_PINNED.put(String.join(".", spec.getPath()), encode(value));
    }

    /**
     * 读取当前生效的全部配置值
     *
     * <p>单项读取失败（配置尚未加载时 {@code get()} 会抛 {@link IllegalStateException}）只跳过那一项，
     * 不影响其余。</p>
     *
     * @return 键 → 字符串值，按配置文件里的声明顺序
     */
    @Nonnull
    public static Map<String, String> capture() {
        Map<String, String> out = new LinkedHashMap<>();
        collect(ModConfig.COMMON_CONFIG, out);
        collect(ModuleConfig.MODULE_CONFIG, out);
        // 只在启动时生效的项：以本次启动实际用上的值为准，不跟热重载后的 toml 走
        out.putAll(STARTUP_PINNED);
        return out;
    }

    private static void collect(ForgeConfigSpec spec, Map<String, String> out) {
        if (spec == null) {
            return;
        }
        walk(spec.getValues(), out);
    }

    private static void walk(UnmodifiableConfig config, Map<String, String> out) {
        for (Object node : config.valueMap().values()) {
            if (node instanceof UnmodifiableConfig sub) {
                walk(sub, out);
            } else if (node instanceof ForgeConfigSpec.ConfigValue<?> value) {
                try {
                    out.put(String.join(".", value.getPath()), encode(value.get()));
                } catch (RuntimeException ignored) {
                    // 配置未加载或值损坏：跳过这一项，客户端会按「缺值」处理
                }
            }
        }
    }

    private static String encode(Object value) {
        if (value instanceof List<?> list) {
            return GSON.toJson(list == null ? Collections.emptyList() : list);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return String.valueOf(value);
    }
}
