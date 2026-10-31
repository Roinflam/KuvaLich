package pers.roinflam.kuvalich.client.lich;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 玄骸和奴仆的渲染淡入淡出状态：渲染器每帧问一次透明度
 *
 * <p>按实体 id 记：召唤包常常比实体先到，先记下的淡入等实体到了接着走。时间走导演的演出钟（暂停时冻结）。
 * 淡入淡出都按 smoothstep 走，从调用那一刻的透明度出发，所以中途反向（淡出一半又淡入）不会跳。</p>
 *
 * <ul>
 *   <li>{@link #hide}：立刻藏起来，等 {@link #fadeIn}；</li>
 *   <li>{@link #fadeOut}：淡到 0 后保持隐藏，直到 {@link #fadeIn} 或实体离开世界；</li>
 *   <li>{@code fadeIn(id, 0)}：立刻完全显形并忘掉这只的记录。</li>
 * </ul>
 *
 * <p>兜底：藏起来、淡出完的状态 {@link #STUCK_MS} 毫秒后还没人管（实体本该被删却还在），自己慢慢显形，
 * 不让一只活着的实体永久隐身。玄骸的离场同步数据变成真的那一刻，没收到离场包也按 350 ms 淡出。
 * 实体离开客户端世界时清掉它的记录，退出世界时全清。</p>
 *
 * <p>肖像里的假实体用负数 id，这里查不到，恒为完全显形。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichRenderState {

    /** 离场同步数据变真、但没收到离场包时的淡出时长 */
    public static final int DEPART_FADE_MS = 350;
    /** 隐身状态最多保持多久就自己显形 */
    private static final int STUCK_MS = 5000;
    /** 兜底显形的淡入时长 */
    private static final int STUCK_FADE_IN_MS = 400;
    /** 记录超过这么多条时顺手清掉早就结束的 */
    private static final int PRUNE_SIZE = 96;

    private static final Int2ObjectOpenHashMap<Fade> STATES = new Int2ObjectOpenHashMap<>();

    private LichRenderState() {
    }

    /** 一只实体的淡入淡出 */
    private static final class Fade {
        float from;
        float to;
        long startMs;
        int ms;
        /** 离场同步数据已经处理过（只在它变真的那一刻淡出一次） */
        boolean departSeen;

        float value(long now) {
            if (ms <= 0) {
                return to;
            }
            float t = FxCurve.clamp01((now - startMs) / (float) ms);
            return from + (to - from) * t * t * (3.0F - 2.0F * t);
        }

        /** 淡完之后多久了；还没淡完是负数 */
        long settledFor(long now) {
            return now - startMs - Math.max(0, ms);
        }

        void set(float from, float to, long now, int ms) {
            this.from = from;
            this.to = to;
            this.startMs = now;
            this.ms = Math.max(0, ms);
        }
    }

    /** 立刻藏起来，等 {@link #fadeIn} 再显形 */
    public static void hide(int entityId) {
        if (entityId < 0) {
            return;
        }
        Fade f = obtain(entityId);
        f.set(0.0F, 0.0F, KuvaFxDirector.clockMs(), 0);
    }

    /** 用 {@code ms} 毫秒淡入显形；{@code ms} ≤ 0 时立刻显形 */
    public static void fadeIn(int entityId, int ms) {
        if (entityId < 0) {
            return;
        }
        if (ms <= 0) {
            Fade f = STATES.get(entityId);
            if (f != null) {
                if (f.departSeen) {
                    // 留着离场标记，免得下一帧又按离场同步数据淡出一次
                    f.set(1.0F, 1.0F, KuvaFxDirector.clockMs(), 0);
                } else {
                    STATES.remove(entityId);
                }
            }
            return;
        }
        long now = KuvaFxDirector.clockMs();
        Fade f = STATES.get(entityId);
        float cur = f == null ? 0.0F : f.value(now);
        if (f == null) {
            f = obtain(entityId);
        }
        f.set(cur, 1.0F, now, ms);
    }

    /** 用 {@code ms} 毫秒淡出消失，淡完保持隐藏 */
    public static void fadeOut(int entityId, int ms) {
        if (entityId < 0) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        Fade f = STATES.get(entityId);
        float cur = f == null ? 1.0F : f.value(now);
        if (f == null) {
            f = obtain(entityId);
        }
        f.set(cur, 0.0F, now, ms);
    }

    /** 此刻的透明度 0~1，1 = 完全显形 */
    public static float alpha(Entity entity, float partialTick) {
        int id = entity.getId();
        if (id < 0) {
            return 1.0F;
        }
        Fade f = STATES.get(id);
        if (entity instanceof KuvaMasterEntity lich && lich.isDeparting() && (f == null || !f.departSeen)) {
            // 晚进视野、离场包没赶上的人也看得到它淡出去
            if (f == null || f.to > 0.0F) {
                fadeOut(id, DEPART_FADE_MS);
            }
            f = STATES.get(id);
            f.departSeen = true;
        }
        if (f == null) {
            return 1.0F;
        }
        long now = KuvaFxDirector.clockMs();
        if (f.to <= 0.0F && f.settledFor(now) > STUCK_MS && entity.isAlive()) {
            f.set(0.0F, 1.0F, now, STUCK_FADE_IN_MS);
        }
        return f.value(now);
    }

    /** 这只实体有没有任何淡入淡出记录（调试显示用） */
    public static boolean tracked(int entityId) {
        return STATES.containsKey(entityId);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            STATES.remove(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }

    private static Fade obtain(int id) {
        Fade f = STATES.get(id);
        if (f == null) {
            if (STATES.size() > PRUNE_SIZE) {
                prune();
            }
            f = new Fade();
            f.set(1.0F, 1.0F, KuvaFxDirector.clockMs(), 0);
            STATES.put(id, f);
        }
        return f;
    }

    /** 清掉早就结束、又已经显形的记录；隐身的留着（实体可能还没到） */
    private static void prune() {
        long now = KuvaFxDirector.clockMs();
        var it = STATES.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<Fade> e = it.next();
            Fade f = e.getValue();
            if (f.settledFor(now) > STUCK_MS * 2L) {
                it.remove();
            }
        }
    }
}
