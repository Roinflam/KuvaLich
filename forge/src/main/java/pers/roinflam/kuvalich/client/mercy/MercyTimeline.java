package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaScreenFx;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.fx.script.RequiemFx;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercySoundCues;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 一场处决的整条演出时间轴：每个时刻放什么声音、调哪段特效、什么时候压混音和交还控制
 *
 * <p>时间都是毫秒，0 = 收到开始处决的效果包那一帧。所有跑这条时间轴的客户端（处决者本人、旁观者）各自本地排一份，
 * 世界里的东西（刀口火花、消散、世界声）大家都放；镜头、界面声、压混音、屏幕效果只给处决者本人。</p>
 *
 * <p>不用通用的 {@code FxTimeline}：那条时间轴按导演的全局钟推进，冻帧调试时停不住、也回拨不了。这里按这一场处决
 * 自己的已过毫秒（冻帧时停住）逐帧执行到点的阶段；预算和主角压制另由一条空的 S 档时间轴占位。</p>
 *
 * <p>这个类同时放各分支的关键时刻（交还、藏界面到哪、刀模什么时候伸缩、目标什么时候本来就该消失），
 * 镜头、姿态、刀模都从这里取，保证几处的时刻对得上。</p>
 *
 * <p>玄骸第 k 次处决（打空了 k 段血）捅 k 刀，前面验过的刀照第一次那样从头演一遍，每一刀的时刻和三刀时一样，
 * 出结果的是最后捅的那一刀：k = 3 全对是成功；k &lt; 3 全对是「挣开站起」（{@link Outcome#ADVANCE}），
 * 那一格填满后 700 ms 玄骸挣开、处决者被推开 1 格，2300 ms 它站稳、交还；哪一刀判错照旧是失败。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MercyTimeline {

    // 各拍的时刻在 MercyBeats，按套路取；这里只留动作本身的长短和距离

    /** 刀模伸出用多久 */
    public static final int BLADE_GROW_MS = 150;
    /** 交还前刀模缩回用多久 */
    public static final int BLADE_RETRACT_MS = 150;
    /** 失败：处决者被甩开多远（格，和服务端推的一样） */
    public static final double FAIL_PUSH = 1.5D;
    /** 挣开站起：处决者被推开多远（格，比判错那一下轻，和服务端推的一样） */
    public static final double ADVANCE_PUSH = 1.0D;
    /** 短版收尾：交还后处决者的姿态再用多久回到站姿 */
    private static final int SHORT_OUTRO_SETTLE_MS = 400;

    /** 失败冲击的颜色（暖色） */
    public static final int ESCAPE_RGB = 0xFFB060;
    private static final int ESCAPE_SPARK_RGB = 0xFFD08A;

    /** 冻帧跳到某一刻时，往前这么多毫秒以内的一次性阶段补放一遍（截图时看得到刚放的火花） */
    private static final int SEEK_REPLAY_MS = 250;
    /** 感化：刀往下一甩以后多久缩完（弹回腕里） */
    private static final int CONVERT_BLADE_MS = 80;
    /** 感化：归还光点比交还早多少（服务端交还那一刻投递） */
    private static final int CONVERT_RETURN_LEAD_MS = 300;

    /** 这一场的结果分支 */
    public enum Outcome {
        /** 玄骸三刀全对 */
        SUCCESS,
        /** 玄骸这次验的几刀（不到三刀）全对，挣开站起来接着打 */
        ADVANCE,
        /** 玄骸第 k 刀判错 */
        FAIL,
        /** 奴仆 */
        THRALL,
        /** 玄骸三刀全对，选择窗口里选了感化 */
        CONVERT
    }

    private record Stage(int ms, Consumer<MercyClient.Exec> action, boolean sound) {
    }

    private final List<Stage> stages = new ArrayList<>();
    private int cursor;
    private boolean sorted = true;

    private MercyTimeline() {
    }

    // ==================== 分支与关键时刻 ====================

    public static Outcome outcome(MercyPlan.Plan p) {
        if (p.variant().thrall) {
            return Outcome.THRALL;
        }
        if (p.prefix() >= p.stabs()) {
            if (p.stabs() < 3) {
                return Outcome.ADVANCE;
            }
            return p.convert() ? Outcome.CONVERT : Outcome.SUCCESS;
        }
        return Outcome.FAIL;
    }

    /** 失败时判错的是第几刀（1~3）；不是失败返回 0 */
    public static int failK(MercyPlan.Plan p) {
        return outcome(p) == Outcome.FAIL ? Math.min(p.stabs(), p.prefix() + 1) : 0;
    }

    /** 失败时判错那一格「本该填满发光」的时刻 g_k；不是失败返回 -1 */
    public static int failFillMs(MercyPlan.Plan p) {
        int k = failK(p);
        return k == 0 ? -1 : lich(p).fill(k);
    }

    /** 玄骸这一套的节拍；奴仆会抛异常 */
    public static MercyBeats.Lich lich(MercyPlan.Plan p) {
        return MercyBeats.lich(p.variant());
    }

    /** 这一场实际捅几刀：成功三刀，挣开站起是这次验的刀数，失败捅到判错那一刀为止，奴仆一刀 */
    public static int lastStab(MercyPlan.Plan p) {
        return switch (outcome(p)) {
            case SUCCESS, CONVERT -> 3;
            case ADVANCE -> p.stabs();
            case FAIL -> failK(p);
            case THRALL -> 1;
        };
    }

    /** 失败、挣开站起：最后那一格填满的时刻 g（之后的挣脱、推开、宽镜头都从它算）；别的返回 -1 */
    public static int verdictFillMs(MercyPlan.Plan p) {
        Outcome oc = outcome(p);
        return oc == Outcome.FAIL || oc == Outcome.ADVANCE ? lich(p).fill(lastStab(p)) : -1;
    }

    /** 失败、挣开站起：玄骸挣开、处决者被推开的时刻；别的返回 -1 */
    public static int breakFreeMs(MercyPlan.Plan p) {
        int g = verdictFillMs(p);
        if (g < 0) {
            return -1;
        }
        MercyBeats.Lich b = lich(p);
        return g + (outcome(p) == Outcome.ADVANCE ? b.advanceBreakFree : b.failBreakFree);
    }

    /** 失败、挣开站起：切宽镜头的时刻；别的返回 -1 */
    public static int wideFromMs(MercyPlan.Plan p) {
        int g = verdictFillMs(p);
        if (g < 0) {
            return -1;
        }
        MercyBeats.Lich b = lich(p);
        return g + (outcome(p) == Outcome.ADVANCE ? b.advanceWide : b.failWide);
    }

    /** 处决者被推开多远（格）：失败 1.5，挣开站起 1.0，别的 0 */
    public static double pushDistance(MercyPlan.Plan p) {
        return switch (outcome(p)) {
            case FAIL -> FAIL_PUSH;
            case ADVANCE -> ADVANCE_PUSH;
            default -> 0.0D;
        };
    }

    /** 交还控制的时刻（镜头、视角类型改回来，刀模已经缩完） */
    public static int handbackMs(MercyPlan.Plan p, boolean shortOutro) {
        return switch (outcome(p)) {
            case SUCCESS -> shortOutro ? lich(p).third : lich(p).successEnd;
            case CONVERT -> lich(p).convertEnd;
            case ADVANCE -> verdictFillMs(p) + lich(p).advanceEnd;
            case FAIL -> failFillMs(p) + lich(p).failEnd;
            case THRALL -> MercyBeats.thrall(p.variant()).end;
        };
    }

    /** 原 HUD 藏到什么时候；奴仆不藏，返回 0。挣开站起没有横幅，交还时就回来 */
    public static int hudUntilMs(MercyPlan.Plan p, boolean shortOutro) {
        return switch (outcome(p)) {
            case SUCCESS, ADVANCE, CONVERT -> handbackMs(p, shortOutro);
            case FAIL -> failFillMs(p) + lich(p).failHud;
            case THRALL -> 0;
        };
    }

    /** 这场处决在客户端上整个结束（交还、HUD 回来都过了） */
    public static int endMs(MercyPlan.Plan p, boolean shortOutro) {
        int end = Math.max(handbackMs(p, shortOutro), hudUntilMs(p, shortOutro));
        if (shortOutro && outcome(p) == Outcome.SUCCESS) {
            // 短版在切第三人称那一刻交还，处决者的姿态再用 400 ms 回到站姿
            end = Math.max(end, lich(p).third + SHORT_OUTRO_SETTLE_MS);
        }
        return end;
    }

    /** 到了这一刻，目标实体消失是正常的（服务端已经让它死了或离场了），不当成中断；挣开站起的目标不会消失 */
    public static boolean targetMayVanish(MercyPlan.Plan p, long t) {
        return switch (outcome(p)) {
            case SUCCESS -> t >= lich(p).death;
            case CONVERT -> t >= lich(p).convertLeave;
            case ADVANCE -> false;
            case FAIL -> t >= failFillMs(p) + lich(p).failEscape;
            case THRALL -> t >= MercyBeats.thrall(p.variant()).death;
        };
    }

    /**
     * 每一刀刺入（刀尖接触）的时刻，按这一场实际会捅的刀数给：成功三个，挣开站起是这次验的几刀，
     * 失败捅到判错那一刀为止，奴仆一个
     */
    public static int[] stabTimes(MercyPlan.Plan p) {
        MercyVariant v = p.variant();
        if (outcome(p) == Outcome.THRALL) {
            return new int[]{v.stabMs(0)};
        }
        int k = lastStab(p);
        int[] out = new int[k];
        System.arraycopy(v.stabMs(), 0, out, 0, k);
        return out;
    }

    /** 第 {@code i} 刀（从 0 起）是不是「出结果的那一刀」（最后捅的那一刀）：推镜和震动加重 */
    public static boolean isHeavyStab(MercyPlan.Plan p, int i) {
        return outcome(p) != Outcome.THRALL && i == lastStab(p) - 1;
    }

    /**
     * 刀模此刻伸出了多少（0 = 收着，1 = 全长）。玄骸 400 ms 起用 150 ms 伸出，奴仆一开始就伸；交还前 150 ms 缩回
     */
    public static float bladeExtension(MercyPlan.Plan p, long t, boolean shortOutro) {
        int growAt = p.variant().thrall ? 0 : lich(p).cutIn;
        int end = handbackMs(p, shortOutro);
        if (outcome(p) == Outcome.SUCCESS && !shortOutro) {
            // 成功收尾抽刀以后刀还在手上，到交还前才缩
            end = lich(p).successEnd;
        }
        if (outcome(p) == Outcome.CONVERT) {
            // 感化：后跃落地、刀往下一甩就弹回腕里，不等交还
            end = lich(p).convertSnap + CONVERT_BLADE_MS;
        }
        if (p.variant().thrall) {
            // 奴仆收势时就先缩回，免得推开以后刀还亮着
            end = MercyBeats.thrall(p.variant()).bladeGone;
        }
        float grow = clamp01((t - growAt) / (float) BLADE_GROW_MS);
        float shrink = clamp01((end - t) / (float) BLADE_RETRACT_MS);
        return Math.min(easeOut(grow), easeOut(shrink));
    }

    private static float easeOut(float u) {
        float k = 1.0F - u;
        return 1.0F - k * k * k;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(1.0F, v);
    }

    // ==================== 排时间轴 ====================

    /** 按计划位排好整条时间轴 */
    static MercyTimeline build(MercyPlan.Plan p) {
        MercyTimeline tl = new MercyTimeline();
        Outcome oc = outcome(p);

        // 0：倒地低鸣收掉（处决开始）
        tl.at(0, x -> MercyFx.stopLoop(x.targetId, 150));

        scheduleSounds(tl, p);

        if (oc == Outcome.THRALL) {
            int stab = p.variant().stabMs(0);
            tl.at(stab, x -> {
                Entity t = x.target();
                if (t != null) {
                    RequiemFx.bladeSpark(t, 1, false);
                }
            });
            return tl.done();
        }

        // 400：硬切进机位、刀模伸出、抽刀声和手前一道短光
        MercyBeats.Lich b = lich(p);
        tl.at(b.cutIn, x -> {
            world(KuvaLichSounds.MERCY_DRAW.get(), x.handPos(), 1.0F, 1.0F);
            drawFlare(x);
        });

        int[] stab = b.stabMs();
        int k = failK(p);
        int last = lastStab(p);
        for (int i = 0; i < last; i++) {
            final int n = i + 1;
            final int idx = i;
            final boolean result = n == last;
            tl.at(stab[i], x -> {
                if (x.cinematic && x.hitstop != null && idx < x.hitstop.length) {
                    KuvaMixer.hitstop(hitstopGroup(x, idx), x.hitstop[idx]);
                }
                Entity t = x.target();
                if (t == null) {
                    return;
                }
                RequiemFx.bladeSpark(t, n, false);
                MercyImpactFx.stab(x, n, result);
            });
            // 格子填满（锁扣声）；判错那一格换成碎裂声
            final boolean wrong = oc == Outcome.FAIL && n == k;
            tl.at(b.fill(n), x -> ui(x, wrong ? KuvaLichSounds.REQUIEM_GLYPH_REJECT_MERCY.get()
                    : KuvaLichSounds.REQUIEM_GLYPH_LOCK_MERCY.get(), wrong ? 1.0F : 0.8F));
        }

        // 踏背下刺捅到第三刀（成功、第三刀判错）才有闪到正面那一下
        if (p.variant() == MercyVariant.LICH_E && last == 3) {
            tl.at(b.blink, MercyTimeline::blinkOut);
            tl.at(b.appear, MercyTimeline::blinkIn);
        }

        if (oc == Outcome.SUCCESS || oc == Outcome.CONVERT) {
            tl.at(b.verdict, x -> {
                ui(x, KuvaLichSounds.MERCY_VERDICT.get(), 1.0F);
                if (x.cinematic) {
                    KuvaMixer.duck(MercyClient.DUCK_KEY, -12.0F, 300);
                }
            });
            tl.at(b.lastSpark, x -> {
                Entity t = x.target();
                if (t != null) {
                    RequiemFx.bladeSpark(t, 3, true);
                    KuvaGlow.boost(x.targetId, 1.6F, 200);
                }
            });
            tl.at(b.commDismiss, x -> {
                if (x.cinematic) {
                    CommHud.dismiss(CommSpeaker.LICH);
                }
            });
            tl.at(b.closeup, x -> {
                if (!x.cinematic || !MercyCamera.takesOver(x)) {
                    return;
                }
                KuvaGlow.boost(x.targetId, 1.6F, b.third - b.closeup);
                Entity t = x.target();
                if (t != null) {
                    closeupMist(t);
                }
            });
            if (oc == Outcome.CONVERT) {
                // 感化的后段是从挂起点换上来的。化雾在本地起（比离场包的那团慢），服务端的离场包随后到、被挡掉
                tl.at(b.convertYank, x -> {
                    Entity t = x.target();
                    if (t != null) {
                        RequiemFx.bladeSpark(t, 3, true);
                        KuvaGlow.boost(x.targetId, 1.4F, 250);
                    }
                });
                tl.at(b.convertMist, x -> LichFx.convertMist(x.target()));
                if (p.returnsItems()) {
                    tl.at(b.convertEnd - CONVERT_RETURN_LEAD_MS, MercyTimeline::returnLight);
                }
                tl.at(b.convertEnd, x -> {
                    if (x.cinematic) {
                        KuvaMixer.release(MercyClient.DUCK_KEY, 300);
                    }
                });
                return tl.done();
            }
            tl.at(b.third, x -> {
                if (!x.cinematic) {
                    return;
                }
                if (x.shortOutro) {
                    KuvaMixer.release(MercyClient.DUCK_KEY, 300);
                } else {
                    KuvaMixer.duck(MercyClient.DUCK_KEY, -4.0F, 150);
                }
            });
            tl.at(b.drawOut, x -> {
                Entity t = x.target();
                if (t != null) {
                    LichFx.dissolve(t, LichFx.Kind.REQUIEM);
                }
            });
            if (p.returnsItems()) {
                tl.at(b.returnLight, MercyTimeline::returnLight);
            }
            tl.at(b.successEnd, x -> {
                if (x.cinematic) {
                    KuvaMixer.release(MercyClient.DUCK_KEY, 300);
                }
            });
            return tl.done();
        }

        if (oc == Outcome.ADVANCE) {
            // 挣开站起（g = 最后那一格填满的时刻）：低吼、刀被挣出来那一下的火花（声音在叠层表里）；不化雾，它站起来接着打
            int g = b.fill(last);
            tl.at(g + b.advanceGrowl, x -> {
                Entity t = x.target();
                if (t != null) {
                    KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_TAUNT.get(), SoundSource.HOSTILE,
                            chest(t), 1.0F, 1.0F).follow(t).tier(FxTier.S));
                }
            });
            tl.at(g + b.advanceBreakFree, x -> {
                Entity t = x.target();
                if (t != null) {
                    RequiemFx.bladeSpark(t, last, false);
                }
            });
            return tl.done();
        }

        // 失败（判错第 k 刀，g = 那一格本该填满的时刻）
        int g = b.fill(k);
        tl.at(g + b.failGrowl, x -> {
            Entity t = x.target();
            if (t != null) {
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_TAUNT.get(), SoundSource.HOSTILE,
                        chest(t), 1.0F, 1.0F).follow(t).tier(FxTier.S));
            }
        });
        final int kk = k;
        tl.at(g + b.failBreakFree, x -> {
            Entity t = x.target();
            if (t != null) {
                // 刀被挣出来那一下
                RequiemFx.bladeSpark(t, kk, false);
            }
        });
        tl.at(g + b.failEscape, x -> {
            Entity t = x.target();
            if (t != null) {
                RequiemFx.escape(t, ESCAPE_RGB);
                LichFx.dissolve(t, LichFx.Kind.ESCAPE);
                escapeSparks(x, t);
            }
            if (x.local) {
                KuvaScreenFx.flash(FxPalette.KUVA_HOT, 0.12F, 250, FxTier.S);
            }
        });
        return tl.done();
    }

    /** 各阶段的时刻（排好序），对比节拍用 */
    int[] stageTimes() {
        // 叠层声音的时刻另有 MercySoundCues 的表，不混进节拍导出
        return stages.stream().filter(s -> !s.sound).mapToInt(Stage::ms).toArray();
    }

    private MercyTimeline at(int ms, Consumer<MercyClient.Exec> action) {
        return add(new Stage(ms, action, false));
    }

    private MercyTimeline sound(int ms, Consumer<MercyClient.Exec> action) {
        return add(new Stage(ms, action, true));
    }

    private MercyTimeline add(Stage s) {
        if (!stages.isEmpty() && stages.get(stages.size() - 1).ms > s.ms) {
            sorted = false;
        }
        stages.add(s);
        return this;
    }

    private MercyTimeline done() {
        if (!sorted) {
            stages.sort((a, b) -> Integer.compare(a.ms, b.ms));
            sorted = true;
        }
        return this;
    }

    // ==================== 推进 ====================

    /** 换成另一条时间轴接着走时用：{@code t} 以前（含）的阶段算已经放过 */
    void skipTo(long t) {
        int i = 0;
        while (i < stages.size() && stages.get(i).ms <= t) {
            i++;
        }
        cursor = i;
    }

    /** 执行所有到点（{@code ms ≤ t}）还没执行的阶段 */
    void advance(MercyClient.Exec e, long t) {
        while (cursor < stages.size() && stages.get(cursor).ms <= t) {
            Stage s = stages.get(cursor++);
            try {
                s.action.accept(e);
            } catch (RuntimeException ex) {
                LogUtil.error("处决演出在 " + s.ms + " ms 的阶段出错", ex);
            }
        }
    }

    /**
     * 跳到某一刻（冻帧调试用）：更早的一次性阶段不再放，最近 {@value #SEEK_REPLAY_MS} ms 以内的下一帧补放；
     * 压混音这种有状态的直接按这一刻该有的样子设好
     */
    void seek(MercyClient.Exec e, long t) {
        int i = 0;
        while (i < stages.size() && stages.get(i).ms <= t - SEEK_REPLAY_MS) {
            i++;
        }
        cursor = i;
        if (!e.cinematic) {
            return;
        }
        Outcome oc = outcome(e.plan);
        if (oc != Outcome.SUCCESS && oc != Outcome.CONVERT) {
            KuvaMixer.release(MercyClient.DUCK_KEY, 0);
            return;
        }
        MercyBeats.Lich b = lich(e.plan);
        if (oc == Outcome.CONVERT) {
            if (t >= b.verdict && t < b.convertEnd) {
                KuvaMixer.duck(MercyClient.DUCK_KEY, -12.0F, 0);
            } else {
                KuvaMixer.release(MercyClient.DUCK_KEY, 0);
            }
            return;
        }
        if (t >= b.verdict && t < b.third) {
            KuvaMixer.duck(MercyClient.DUCK_KEY, -12.0F, 0);
        } else if (t >= b.third && t < b.successEnd && !e.shortOutro) {
            KuvaMixer.duck(MercyClient.DUCK_KEY, -4.0F, 0);
        } else {
            KuvaMixer.release(MercyClient.DUCK_KEY, 0);
        }
    }

    // ==================== 小工具 ====================

    private static final RandomSource RANDOM = RandomSource.create();

    /** 目标胸口（刀口）的位置 */
    static Vec3 chest(Entity t) {
        double h = t instanceof AbstractKuva k ? k.mercyChestHeight() : t.getBbHeight() * 0.62D;
        return t.position().add(0.0D, h, 0.0D);
    }

    private static void scheduleSounds(MercyTimeline tl, MercyPlan.Plan p) {
        MercySoundCues.Result r = switch (outcome(p)) {
            case SUCCESS -> MercySoundCues.Result.SUCCESS;
            case CONVERT -> MercySoundCues.Result.CONVERT;
            case FAIL -> MercySoundCues.Result.FAIL;
            case ADVANCE -> MercySoundCues.Result.ADVANCE;
            case THRALL -> MercySoundCues.Result.THRALL;
        };
        for (MercySoundCues.Cue c : MercySoundCues.cues(p.variant(), r, lastStab(p))) {
            tl.sound(Math.max(0, c.startMs()), x -> layer(x, c));
        }
    }

    /**
     * 处决者本人听的不定位（镜头绕着走也不会左右乱飘），别人听的定位在刀口、随距离衰减
     */
    private static void layer(MercyClient.Exec x, MercySoundCues.Cue c) {
        Entity t = x.target();
        if (t == null) {
            return;
        }
        if (c.layer() == MercySoundCues.Layer.THRALL && !KuvaSoundPlayer.gate("mercy.thrall." + x.targetId, 300)) {
            return;
        }
        SoundEvent ev = layerEvent(c.layer(), t);
        float pitch = KuvaSoundPlayer.jitter(c.pitch(), 0.02F);
        KuvaSoundInstance s = x.local ? KuvaSoundPlayer.self(ev, SoundSource.HOSTILE, c.vol(), pitch)
                : KuvaSoundPlayer.at(ev, SoundSource.HOSTILE, chest(t), c.vol(), pitch);
        if (c.impact() && c.stab() >= 0) {
            s.hitstopGroup(hitstopGroup(x, c.stab()));
        }
        KuvaSoundPlayer.play(s.tier(FxTier.S));
    }

    private static int hitstopGroup(MercyClient.Exec x, int stab) {
        return (System.identityHashCode(x) & 0x0FFFFFFF) * 4 + stab;
    }

    private static final Map<MercySoundCues.Layer, SoundEvent> LAYER_EVENTS = new EnumMap<>(MercySoundCues.Layer.class);

    private static SoundEvent layerEvent(MercySoundCues.Layer l, Entity t) {
        if (l == MercySoundCues.Layer.THRALL && t instanceof KuvaLarvlingEntity) {
            return KuvaLichSounds.MERCY_LARVLING.get();
        }
        return LAYER_EVENTS.computeIfAbsent(l, k -> ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation(Reference.MOD_ID, k.id)));
    }

    /** 定位在 {@code pos} 的世界声，S 档（不被主角压制压低） */
    private static void world(SoundEvent ev, Vec3 pos, float vol, float pitch) {
        KuvaSoundPlayer.play(KuvaSoundPlayer.at(ev, SoundSource.HOSTILE, pos, vol, pitch).tier(FxTier.S));
    }

    /** 只给处决者本人的界面声 */
    private static void ui(MercyClient.Exec e, SoundEvent ev, float vol) {
        if (e.cinematic) {
            KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, vol, 1.0F));
        }
    }

    private static int count(int full) {
        float scale = EffectsDisplayConfig.intensity().budgetScale;
        return Math.max(1, Math.round(full * scale));
    }

    private static double rand(double lo, double hi) {
        return lo + RANDOM.nextDouble() * (hi - lo);
    }

    /** 400 ms：处决者右手前一道短光 + 几颗火花 */
    private static void drawFlare(MercyClient.Exec e) {
        Vec3 hand = e.handPos();
        KuvaParticles.flare(hand.x, hand.y, hand.z).size(0.8F, 0.8F).color(FxPalette.KUVA_PALE)
                .alpha(0.85F).life(2).tier(FxTier.S).spawn();
        int n = count(4);
        for (int i = 0; i < n; i++) {
            Vec3 v = e.f.scale(-rand(0.05D, 0.12D)).add(rand(-0.04D, 0.04D), rand(0.0D, 0.06D), rand(-0.04D, 0.04D));
            KuvaParticles.spark(hand.x, hand.y, hand.z).vel(v.x, v.y, v.z).color(FxPalette.KUVA_PALE)
                    .color2(FxPalette.KUVA).size(0.07F, 0.02F).life(6 + RANDOM.nextInt(4))
                    .orient(KuvaParticles.Orient.VELOCITY).gravity(0.02F).drag(0.9F).tier(FxTier.S).spawn();
        }
    }

    private static final MercyPose.Body BODY_AT = new MercyPose.Body();

    /** 处决者在某一刻的胸口（整身轨道上的位置；轨道走完了就是真实站位） */
    private static Vec3 bodyChest(MercyClient.Exec e, int t) {
        Vec3 feet = MercyPose.body(e, t, BODY_AT) ? new Vec3(BODY_AT.x, BODY_AT.y, BODY_AT.z) : e.pe;
        return feet.add(0.0D, 1.0D, 0.0D);
    }

    /**
     * 踏背下刺在它背上化作红光：人散成一团火星和红雾，一道光从它头顶划到正面，划过去的时候在它身前横着劈出一道刀光
     * （和交还前那一刀的光是同一种颜色，看着是一个人闪过去顺手砍了一下）
     */
    private static void blinkOut(MercyClient.Exec e) {
        Entity t = e.target();
        if (t == null) {
            return;
        }
        MercyBeats.Lich beats = lich(e.plan);
        Vec3 from = bodyChest(e, beats.blink);
        Vec3 to = bodyChest(e, beats.appear);
        world(KuvaLichSounds.WEAPON_DASH.get(), from, 0.9F, KuvaSoundPlayer.jitter(1.1F, 0.03F));
        KuvaParticles.flare(from.x, from.y, from.z).size(1.3F, 1.3F).color(FxPalette.KUVA_PALE).alpha(0.9F).life(3)
                .tier(FxTier.S).spawn();
        int n = count(10);
        for (int i = 0; i < n; i++) {
            Vec3 v = new Vec3(rand(-1.0D, 1.0D), rand(-0.6D, 1.0D), rand(-1.0D, 1.0D)).normalize().scale(rand(0.12D, 0.26D));
            KuvaParticles.spark(from.x, from.y, from.z).vel(v.x, v.y, v.z).color(FxPalette.KUVA_PALE)
                    .color2(FxPalette.KUVA).size(0.09F, 0.02F).life(6 + RANDOM.nextInt(5))
                    .orient(KuvaParticles.Orient.VELOCITY).drag(0.86F).tier(FxTier.S).spawn();
        }
        int puffs = count(5);
        for (int i = 0; i < puffs; i++) {
            double y = from.y - 0.8D + 1.5D * i / Math.max(1, puffs - 1);
            KuvaParticles.mist(from.x, y, from.z).vel(rand(-0.02D, 0.02D), rand(0.0D, 0.02D), rand(-0.02D, 0.02D))
                    .color(FxPalette.KUVA_MIST).alpha(0.55F).size(0.45F, 0.8F).life(10 + RANDOM.nextInt(7))
                    .tier(FxTier.S).spawn();
        }
        // 光路：从后背越过它的头顶，落到正面
        Vec3 top = new Vec3((from.x + to.x) * 0.5D, t.getY() + 3.4D, (from.z + to.z) * 0.5D);
        int steps = count(18);
        for (int i = 0; i <= steps; i++) {
            double u = i / (double) Math.max(1, steps);
            double a = (1.0D - u) * (1.0D - u), b = 2.0D * u * (1.0D - u), c = u * u;
            Vec3 at = from.scale(a).add(top.scale(b)).add(to.scale(c));
            Vec3 tan = top.subtract(from).scale(2.0D * (1.0D - u)).add(to.subtract(top).scale(2.0D * u)).normalize();
            KuvaParticles.spark(at.x, at.y, at.z).vel(tan.x * 0.12D, tan.y * 0.12D, tan.z * 0.12D)
                    .color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.16F, 0.04F).life(4 + RANDOM.nextInt(3))
                    .orient(KuvaParticles.Orient.VELOCITY).drag(0.7F).tier(FxTier.S).spawn();
        }
        // 身前横着劈一道：从它左肩斜着划到右腰
        Vec3 c0 = chest(t);
        Vec3 cut = e.s.scale(0.9D).add(0.0D, -0.55D, 0.0D);
        int marks = count(12);
        for (int i = 0; i < marks; i++) {
            double u = -1.0D + 2.0D * i / Math.max(1, marks - 1);
            Vec3 at = c0.add(cut.scale(u)).add(e.f.scale(-0.35D));
            KuvaParticles.spark(at.x, at.y, at.z).vel(cut.x * 0.06D, cut.y * 0.06D, cut.z * 0.06D)
                    .color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.14F, 0.03F).life(5 + RANDOM.nextInt(3))
                    .orient(KuvaParticles.Orient.VELOCITY).drag(0.8F).tier(FxTier.S).spawn();
        }
    }

    /** 在它正面现身：火星从四周收拢，一下闪光 */
    private static void blinkIn(MercyClient.Exec e) {
        Vec3 at = bodyChest(e, lich(e.plan).appear);
        world(KuvaLichSounds.WEAPON_DASH_HIT.get(), at, 0.55F, KuvaSoundPlayer.jitter(1.2F, 0.03F));
        KuvaParticles.flare(at.x, at.y, at.z).size(1.0F, 1.0F).color(FxPalette.KUVA_PALE).alpha(0.8F).life(2)
                .tier(FxTier.S).spawn();
        int n = count(10);
        for (int i = 0; i < n; i++) {
            Vec3 off = new Vec3(rand(-1.0D, 1.0D), rand(-0.9D, 0.9D), rand(-1.0D, 1.0D)).normalize().scale(rand(0.6D, 0.9D));
            Vec3 p = at.add(off);
            Vec3 v = off.scale(-0.22D);
            KuvaParticles.spark(p.x, p.y, p.z).vel(v.x, v.y, v.z).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA)
                    .size(0.08F, 0.02F).life(4).orient(KuvaParticles.Orient.VELOCITY).tier(FxTier.S).spawn();
        }
    }

    /** 切跪地特写那一下：胸口一团雾，没有闪光 */
    private static void closeupMist(Entity t) {
        Vec3 c = chest(t);
        int n = count(6);
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double sp = rand(0.02D, 0.04D);
            KuvaParticles.mist(c.x, c.y, c.z).vel(Math.cos(a) * sp, rand(-0.005D, 0.01D), Math.sin(a) * sp)
                    .color(FxPalette.KUVA_MIST).alpha(0.5F).size(0.5F, 0.9F).life(20 + RANDOM.nextInt(11))
                    .tier(FxTier.S).spawn();
        }
    }

    /** 失败冲击：朝处决者方向喷的一锥火花，旁观者也看得到 */
    private static void escapeSparks(MercyClient.Exec e, Entity t) {
        Vec3 c = chest(t);
        int n = count(14);
        double cone = Math.toRadians(35.0D);
        for (int i = 0; i < n; i++) {
            double yaw = rand(-cone, cone);
            double pitch = rand(-cone, cone) * 0.6D;
            Vec3 dir = e.f.scale(Math.cos(yaw) * Math.cos(pitch)).add(e.s.scale(Math.sin(yaw)))
                    .add(0.0D, Math.sin(pitch) + 0.08D, 0.0D).normalize();
            Vec3 v = dir.scale(rand(0.35D, 0.6D));
            KuvaParticles.spark(c.x, c.y, c.z).vel(v.x, v.y, v.z).color(ESCAPE_SPARK_RGB).color2(FxPalette.KUVA_HOT)
                    .size(0.09F, 0.03F).life(6 + RANDOM.nextInt(7)).orient(KuvaParticles.Orient.VELOCITY)
                    .gravity(0.03F).drag(0.88F).tier(FxTier.S).spawn();
        }
    }

    /** 成功收尾归还没收物品：原点一团雾往外涌 + 几颗光点，配归还的声音 */
    private static void returnLight(MercyClient.Exec e) {
        Vec3 o = e.pl.add(0.0D, 0.6D, 0.0D);
        world(KuvaLichSounds.REQUIEM_RETURN.get(), o, 1.0F, 1.0F);
        int mist = count(6);
        for (int i = 0; i < mist; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double sp = rand(0.03D, 0.06D);
            KuvaParticles.mist(o.x, o.y, o.z).vel(Math.cos(a) * sp, rand(0.0D, 0.02D), Math.sin(a) * sp)
                    .color(FxPalette.KUVA_MIST).alpha(0.45F).size(0.4F, 0.8F).life(18 + RANDOM.nextInt(10))
                    .tier(FxTier.S).spawn();
        }
        int motes = count(4);
        for (int i = 0; i < motes; i++) {
            KuvaParticles.mote(o.x + rand(-0.3D, 0.3D), o.y + rand(0.0D, 0.4D), o.z + rand(-0.3D, 0.3D))
                    .vel(0.0D, rand(0.02D, 0.04D), 0.0D).color(FxPalette.MURMUR).color2(FxPalette.KUVA)
                    .size(0.08F, 0.03F).life(24 + RANDOM.nextInt(10)).tier(FxTier.S).spawn();
        }
    }
}
