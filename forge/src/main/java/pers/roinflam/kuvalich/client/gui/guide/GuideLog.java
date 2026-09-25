package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指南的「同一件事只报一次」日志
 *
 * <p>内容问题（缺配置键、坏块、未知物品）是在排版和渲染路径上发现的，而这两条路径会被反复走 ——
 * 窗口一缩放就重排一次，搜索一次就把全书解析一遍。不去重的话，一个写错的键能在日志里刷出几百行，
 * 真正有用的那条反而被淹掉。资源重载（F3+T）时清空，改完内容重载一次就能重新看到还剩哪些问题。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideLog {

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private GuideLog() {}

    /**
     * 以 key 去重地打一条警告
     *
     * @param key     去重键（同一个问题每次调用都应当算出同一个键）
     * @param message 日志内容
     */
    static void warnOnce(String key, String message) {
        if (SEEN.add(key)) {
            LogUtil.warn("[冒险指南] " + message);
        }
    }

    /** 资源重载时清空，让作者改完内容后能重新看到剩下的问题 */
    static void reset() {
        SEEN.clear();
    }
}
