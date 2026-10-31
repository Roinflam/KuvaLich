package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 一段演出的时间轴：到点执行的阶段、定时放的声音。原点和档位由 {@link KuvaFxDirector#start} 给定，
 * 由导演每帧按自己的单调时钟推进，所以声音和画面落在同一帧。
 *
 * <p>阶段在「第一帧满足 t ≥ ms」时执行一次；一帧跨过好几个阶段（卡顿）时按时间顺序依次执行。
 * 阶段里新建的粒子、几何会自动记到这条时间轴的档位和计数上（调试叠层读这个数核预算）。</p>
 *
 * <p>时间轴在最后一个阶段执行完、并过了 {@link #until} 设的时刻以后结束；
 * S 级时间轴在结束之前一直算「主角」，会压低附近 B / C 级的粒子、几何和声音。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FxTimeline {

    private final String name;
    private Vec3 origin;
    private final FxTier tier;
    /** 被导演拒收的时间轴（同时活跃数满了）什么都不做 */
    private final boolean dead;

    private Entity follow;
    private long startMs;
    private boolean cancelled;
    private int endMs;

    private final List<Stage> stages = new ArrayList<>();
    private boolean sorted = true;

    /** 这条时间轴生成过的粒子 / 几何数（只用于调试核预算） */
    int particleCount;
    int geometryCount;

    private static final class Stage {
        final int ms;
        final Runnable action;
        boolean done;

        Stage(int ms, Runnable action) {
            this.ms = ms;
            this.action = action;
        }
    }

    FxTimeline(String name, Vec3 origin, FxTier tier, boolean dead) {
        this.name = name;
        this.origin = origin;
        this.tier = tier;
        this.dead = dead;
    }

    public String name() {
        return name;
    }

    public Vec3 origin() {
        return origin;
    }

    public FxTier tier() {
        return tier;
    }

    /** 从开始到现在的毫秒数 */
    public int ageMs() {
        return (int) (KuvaFxDirector.clockMs() - startMs);
    }

    /** 原点跟随实体（实体没了就停在最后位置） */
    public FxTimeline follow(Entity e) {
        this.follow = e;
        return this;
    }

    /** 第一帧满足 {@code t ≥ ms} 时执行一次 */
    public FxTimeline at(int ms, Runnable stage) {
        if (dead) {
            return this;
        }
        if (!stages.isEmpty() && stages.get(stages.size() - 1).ms > ms) {
            sorted = false;
        }
        stages.add(new Stage(ms, stage));
        endMs = Math.max(endMs, ms);
        return this;
    }

    /** 在 [{@code from}, {@code to}] 内每隔 {@code step} 毫秒执行一次，参数是当时的毫秒数 */
    public FxTimeline every(int from, int to, int step, IntConsumer stage) {
        int s = Math.max(1, step);
        for (int ms = from; ms <= to; ms += s) {
            final int t = ms;
            at(ms, () -> stage.accept(t));
        }
        return this;
    }

    /** 在 {@code ms} 毫秒放一个定位在原点的声音（按 sounds.json 的衰减距离衰减） */
    public FxTimeline sound(int ms, SoundEvent ev, SoundSource src, float vol, float pitch) {
        return at(ms, () -> KuvaSoundPlayer.play(KuvaSoundPlayer.at(ev, src, origin, vol, pitch).tier(tier)));
    }

    /** 在 {@code ms} 毫秒放一个不定位的界面声（乘界面音量） */
    public FxTimeline uiSound(int ms, SoundEvent ev, float vol, float pitch) {
        return at(ms, () -> KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, vol, pitch)));
    }

    /**
     * 只在本地玩家的实体 id 等于 {@code playerEntityId} 时立刻执行 {@code stage}（屏幕效果只给相关的人）。
     * 写在某个 {@link #at} 阶段里面，就是「到点时只给这个人」
     */
    public FxTimeline onlyFor(int playerEntityId, Runnable stage) {
        Minecraft mc = Minecraft.getInstance();
        if (!dead && mc.player != null && mc.player.getId() == playerEntityId) {
            stage.run();
        }
        return this;
    }

    /** 让时间轴至少活到 {@code ms}（余韵还在画的时候继续算主角） */
    public FxTimeline until(int ms) {
        endMs = Math.max(endMs, ms);
        return this;
    }

    /** 提前结束（没执行的阶段都不再执行） */
    public void cancel() {
        cancelled = true;
    }

    // ==================== 导演调用 ====================

    void begin(long nowMs) {
        this.startMs = nowMs;
    }

    boolean isDead() {
        return dead;
    }

    boolean finished(long nowMs) {
        if (cancelled || dead) {
            return true;
        }
        if (nowMs - startMs < endMs) {
            return false;
        }
        for (Stage s : stages) {
            if (!s.done) {
                return false;
            }
        }
        return true;
    }

    /** 跟随实体时更新原点 */
    void updateOrigin() {
        if (follow != null) {
            if (follow.isRemoved()) {
                follow = null;
            } else {
                origin = follow.position().add(0.0D, follow.getBbHeight() * 0.5D, 0.0D);
            }
        }
    }

    /** 执行所有到点的阶段 */
    void advance(long nowMs) {
        if (cancelled || dead) {
            return;
        }
        if (!sorted) {
            stages.sort((a, b) -> Integer.compare(a.ms, b.ms));
            sorted = true;
        }
        int t = (int) (nowMs - startMs);
        // 阶段里可能再加阶段（at 里套 at），所以按下标遍历；新加的如果更早，下一帧排序后照样执行
        for (int i = 0; i < stages.size(); i++) {
            Stage s = stages.get(i);
            if (s.done) {
                continue;
            }
            if (s.ms > t) {
                if (sorted) {
                    break;
                }
                continue;
            }
            s.done = true;
            try {
                s.action.run();
            } catch (RuntimeException e) {
                LogUtil.error("特效时间轴 " + name + " 在 " + s.ms + " ms 的阶段出错", e);
            }
        }
    }
}
