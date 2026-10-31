package pers.roinflam.kuvalich.client.gui.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通讯框的状态：哪些通讯已经看过（存盘），哪些正在演开框和擦出（不存盘）
 *
 * <h3>看过的记录</h3>
 * <p>一条通讯只在第一次看到时演一遍开框和擦出、响一声，之后不管翻回来、关书再开、重启游戏，都直接是静止的全文。
 * 记录放在客户端本地的 {@code config/kuvalich-guide-seen.json}（一个字符串数组），不跟服务端同步 ——
 * 这是读者自己的阅读进度，换服务器也不该重打一遍。</p>
 * <p>键是「分类/条目#n」，n 是这一篇里第几条通讯（从 0 数，按内容文件里的块顺序）。
 * 不用可见块序号：条件显隐、窗口缩放、配置重载都可能让同一条通讯的可见序号变掉；也不带语言，换语言不会重打。</p>
 * <p>第一次用到时读一次，读不到（没有、坏了）就当都没看过；新记一条只标脏，关书时写回，写不进去只警告一次。</p>
 *
 * <h3>正在打的</h3>
 * <p>演出按墙钟走、和看不看得见无关：演到一半滚出视口，回来时已经演到相应位置或已经演完。
 * 记录不能放在排好的块里 —— 排版结果按（条目、宽度）缓存，改窗口大小、换界面倍数、重载配置都会换一份新的，
 * 放在里面的话重排一次就重演一遍、重响一声。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class CommMemory {

    private static final String FILE = "kuvalich-guide-seen.json";

    /**
     * 一条正在演的通讯
     *
     * @param start  第一次看到的时刻（{@code Util.getMillis()}）
     * @param length 从开始到进静止态的毫秒数（开框、擦出、擦完后说话态那一段都算）
     */
    record Typing(long start, int length) {}

    private static final Set<String> SEEN = new HashSet<>();
    private static final Map<String, Typing> STARTED = new HashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static boolean warned;

    private CommMemory() {}

    /** 这条看过了吗 */
    static boolean seen(String key) {
        ensureLoaded();
        return SEEN.contains(key);
    }

    /** 正在演的记录；没在演为 null */
    @Nullable
    static Typing typing(String key) {
        return STARTED.get(key);
    }

    /** 第一次看到，开始演 */
    static void start(String key, long now, int length) {
        STARTED.put(key, new Typing(now, length));
    }

    /** 记为看过（演完了、被点击 / 按键打断、搜索直接跳到它、开框与擦出关着时第一次看到） */
    static void markSeen(String key) {
        ensureLoaded();
        STARTED.remove(key);
        if (SEEN.add(key)) {
            dirty = true;
        }
    }

    /** 点击阅读栏 / 按键：正在演的通讯一律直接进静止态 */
    static void finishAll() {
        if (STARTED.isEmpty()) {
            return;
        }
        ensureLoaded();
        for (String key : STARTED.keySet()) {
            if (SEEN.add(key)) {
                dirty = true;
            }
        }
        STARTED.clear();
    }

    /**
     * 关书：已经按墙钟演完的记为看过（可能是演完时刚好滚出了视口，没来得及记），没演完的丢掉、下次再演；然后写回文件
     */
    static void close(long now) {
        for (Iterator<Map.Entry<String, Typing>> it = STARTED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Typing> e = it.next();
            if (now - e.getValue().start() >= e.getValue().length() && SEEN.add(e.getKey())) {
                dirty = true;
            }
            it.remove();
        }
        save();
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        dirty = false;
        SEEN.clear();
        Path p = path();
        if (!Files.isRegularFile(p)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) {
                        SEEN.add(el.getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // 坏了就当都没看过：大不了每条通讯再演一遍
            LogUtil.warn("[冒险指南] 读不了 " + FILE + "，当作还没看过任何通讯: " + e);
        }
    }

    private static void save() {
        if (!dirty) {
            return;
        }
        List<String> keys = new ArrayList<>(SEEN);
        Collections.sort(keys);
        JsonArray arr = new JsonArray();
        for (String k : keys) {
            arr.add(k);
        }
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, arr.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException | RuntimeException e) {
            if (!warned) {
                warned = true;
                LogUtil.warn("[冒险指南] 写不进 " + FILE + "，看过的通讯下次启动会重新演一遍: " + e);
            }
        }
    }
}
