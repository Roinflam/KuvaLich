package pers.roinflam.kuvalich.client.fx;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端导演：维护演出用的单调时钟，逐帧推进所有时间轴，再把这一帧的几何画出来
 *
 * <p><b>时钟</b>：每帧加上真实的帧间隔（单帧最多算 100 ms），游戏暂停时不走。不用世界的 gameTime：
 * 服务器掉 TPS 时客户端的 gameTime 会被时间同步包往回拨，扩到一半的冲击环会缩回去再扩。</p>
 *
 * <p><b>推进</b>：{@code RenderLevelStageEvent} 的 {@code AFTER_WEATHER} 阶段先执行到点的阶段，
 * 紧接着画几何，所以爆发帧的闪光和声音同一帧出现；粒子要等下一个客户端 tick 才上屏，只当余韵用。</p>
 *
 * <p><b>上限</b>：同时活跃 24 条时间轴，满了按档位挤掉最低的；远近取舍（32 格外只留核心层、64 格外不画）
 * 在粒子和几何各自生成的时候按位置判断。</p>
 *
 * <p>自己订阅事件的客户端类必须写 {@code value = Dist.CLIENT}，只写 {@code @OnlyIn} 在专用服上照样会被扫描注册而崩。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaFxDirector {

    /** 同时活跃的时间轴上限 */
    public static final int MAX_TIMELINES = 24;
    /** 主角压制的范围（格） */
    public static final double HERO_RADIUS = 32.0D;
    /** 超过这个距离只保留核心层 */
    public static final double LOD_CORE_DIST = 32.0D;
    /** 超过这个距离不画，只放声音 */
    public static final double LOD_CULL_DIST = 64.0D;

    /** 白天露天时加法层最多压掉的比例（正午露天 × 0.6） */
    public static final float ADDITIVE_DAY_CUT = 0.4F;

    private static final List<FxTimeline> ACTIVE = new ArrayList<>();
    private static final BlockPos.MutableBlockPos SCRATCH_POS = new BlockPos.MutableBlockPos();

    private static long clockMs;
    private static long lastNanos = -1L;
    /** 还没凑够 1 ms 的纳秒零头，留到下一帧接着攒 */
    private static long carryNanos;

    /** 正在执行阶段的时间轴（阶段里新建的粒子 / 几何记到它头上） */
    private static FxTimeline current;

    /** 最近结束的一条时间轴的计数（调试叠层显示） */
    private static String lastName = "-";
    private static int lastParticles;
    private static int lastGeometry;

    private KuvaFxDirector() {
    }

    /** 开一条时间轴；{@code tier} 决定预算和远处的取舍。同时活跃数满了、又挤不掉别的时，返回一条什么都不做的时间轴 */
    public static FxTimeline start(String name, Vec3 origin, FxTier tier) {
        if (ACTIVE.size() >= MAX_TIMELINES && !evictFor(tier)) {
            return new FxTimeline(name, origin, tier, true);
        }
        FxTimeline tl = new FxTimeline(name, origin, tier, false);
        tl.begin(clockMs);
        ACTIVE.add(tl);
        return tl;
    }

    /** 导演自己的单调毫秒钟（只在游戏没暂停时走） */
    public static long clockMs() {
        return clockMs;
    }

    /**
     * 加法发光层在此处该乘的系数：加法叠在亮背景上会整片发白，白天露天的地方把加法层压暗一些
     *
     * <p>{@code 日照 × 天空光 / 15} 算出这里有多「晒」：日照取世界的天空亮度（正午 1，夜里 0，下雨打雷会低一些），
     * 天空光取这一格的天空光照等级（室内、地下、下界、末地是 0）。系数 = {@code 1 − 0.4 × clamp((晒 − 0.2) / 0.8, 0, 1)}，
     * 正午露天 0.6，夜里和室内 1。</p>
     *
     * <p>{@link KuvaParticles} 和 {@link KuvaFxGeometry} 已经在内部乘过（只乘加法混合的那些，透明混合的不乘），
     * 演出脚本不要再乘；自己另画加法层的（比如贴在实体上的护壁）才调这个。</p>
     */
    public static float additiveScale(Vec3 pos) {
        return additiveScale(pos.x, pos.y, pos.z);
    }

    /** 同 {@link #additiveScale(Vec3)}，不用先建一个向量 */
    public static float additiveScale(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 1.0F;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        // getSkyDarken(pt) 名字是「变暗」，实际是天空亮度：正午 1.0、夜里 0.2
        float day = Mth.clamp((level.getSkyDarken(pt) - 0.2F) / 0.8F, 0.0F, 1.0F);
        if (day <= 0.0F) {
            return 1.0F;
        }
        SCRATCH_POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        float sky = level.getBrightness(LightLayer.SKY, SCRATCH_POS) / 15.0F;
        float exposure = day * sky;
        return 1.0F - ADDITIVE_DAY_CUT * Mth.clamp((exposure - 0.2F) / 0.8F, 0.0F, 1.0F);
    }

    /** 点到相机的距离 */
    public static double camDist(Vec3 p) {
        Camera cam = camera();
        if (cam == null) {
            return 0.0D;
        }
        return cam.getPosition().distanceTo(p);
    }

    /** 32 格内有 S 级时间轴在跑（主角压制、混音让位都读它） */
    public static boolean heroActiveNear(Vec3 p) {
        return heroActiveNear(p.x, p.y, p.z);
    }

    /** 同 {@link #heroActiveNear(Vec3)}，不用先建一个向量 */
    public static boolean heroActiveNear(double x, double y, double z) {
        double r2 = HERO_RADIUS * HERO_RADIUS;
        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            FxTimeline tl = ACTIVE.get(i);
            if (tl.tier() == FxTier.S && !tl.isDead() && tl.origin().distanceToSqr(x, y, z) <= r2) {
                return true;
            }
        }
        return false;
    }

    /** 当前活跃的时间轴数 */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 当前相机；还没进世界时为 null */
    public static Camera camera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return null;
        }
        Camera cam = mc.gameRenderer.getMainCamera();
        return cam != null && cam.isInitialized() ? cam : null;
    }

    static FxTimeline current() {
        return current;
    }

    static String lastSummary() {
        return lastName + " p=" + lastParticles + " g=" + lastGeometry;
    }

    /** 挤掉一条比 {@code tier} 低（或同档、更早开始）的时间轴；没有可挤的返回 false */
    private static boolean evictFor(FxTier tier) {
        FxTimeline victim = null;
        for (FxTimeline tl : ACTIVE) {
            if (tl.tier().ordinal() < tier.ordinal()) {
                continue;
            }
            if (victim == null || tl.tier().ordinal() > victim.tier().ordinal()) {
                victim = tl;
            }
        }
        if (victim == null) {
            return false;
        }
        victim.cancel();
        ACTIVE.remove(victim);
        return true;
    }

    // ==================== 事件 ====================

    /** 每帧开头走时钟 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        long now = Util.getNanos();
        if (lastNanos < 0L) {
            lastNanos = now;
            return;
        }
        long elapsed = now - lastNanos;
        lastNanos = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) {
            return;
        }
        // 按纳秒累计再折成毫秒：每帧直接整除会把不足 1 ms 的零头丢掉，144 帧时钟要慢一成多。
        // 单帧最多记 100 ms，卡顿、切窗口回来不会让演出一下跳过一大段
        carryNanos += Math.max(0L, Math.min(100_000_000L, elapsed));
        long dt = carryNanos / 1_000_000L;
        carryNanos -= dt * 1_000_000L;
        clockMs += dt;
    }

    /**
     * 云和雨雪画完以后：推进时间轴，再画几何
     *
     * <p>几何不写深度，要是放在粒子阶段画，后面画的云会整片盖在光晕上（玩家在云上、或者光晕背后是云的时候很明显），
     * 所以挪到天气之后。云在光晕前面时照样靠深度测试挡住。</p>
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        long now = clockMs;
        // 阶段里可能再开新的时间轴，复制一份再遍历
        FxTimeline[] snapshot = ACTIVE.toArray(new FxTimeline[0]);
        for (FxTimeline tl : snapshot) {
            tl.updateOrigin();
            current = tl;
            try {
                tl.advance(now);
            } finally {
                current = null;
            }
        }
        KuvaFxGeometry.render(event, now);
    }

    /** 客户端 tick 末尾清掉结束的时间轴；换世界时全部丢掉 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            FxBudget.onTickStart();
            return;
        }
        if (Minecraft.getInstance().level == null) {
            if (!ACTIVE.isEmpty()) {
                ACTIVE.clear();
            }
            KuvaFxGeometry.clear();
            FxBudget.clear();
            return;
        }
        long now = clockMs;
        ACTIVE.removeIf(tl -> {
            if (!tl.finished(now)) {
                return false;
            }
            if (tl.particleCount > 0 || tl.geometryCount > 0) {
                lastName = tl.name();
                lastParticles = tl.particleCount;
                lastGeometry = tl.geometryCount;
                if (!FMLEnvironment.production) {
                    LogUtil.info("[KuvaFx] " + tl.name() + " (" + tl.tier() + ") particles=" + tl.particleCount
                            + " geometry=" + tl.geometryCount);
                }
            }
            return true;
        });
    }
}
