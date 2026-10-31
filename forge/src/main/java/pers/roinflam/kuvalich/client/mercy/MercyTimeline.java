package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaScreenFx;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.fx.script.RequiemFx;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;
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
 */
@OnlyIn(Dist.CLIENT)
public final class MercyTimeline {

    // ==================== 共用时刻（毫秒） ====================

    /** 起手抓住之后硬切进本套机位 */
    public static final int CUT_IN_MS = 400;
    /** 刀模伸出用多久 */
    public static final int BLADE_GROW_MS = 150;
    /** 交还前刀模缩回用多久 */
    public static final int BLADE_RETRACT_MS = 150;

    /** 成功：第三刀后的不和谐垫音和压混音 */
    public static final int VERDICT_MS = 3220;
    /** 成功：第三刀后的刀口小火花 */
    public static final int LAST_SPARK_MS = 3240;
    /** 成功：收起玄骸的通讯窗 */
    public static final int COMM_DISMISS_MS = 3670;
    /** 成功：切跪地特写 */
    public static final int CLOSEUP_MS = 4670;
    /** 成功：切膝盖高度的第三人称 */
    public static final int THIRD_MS = 7320;
    /** 成功：抽刀，开始消散 */
    public static final int DRAW_OUT_MS = 7530;
    /** 成功：目标在服务端死亡（之后实体消失是正常的） */
    public static final int SUCCESS_DEATH_MS = 7550;
    /** 成功：归还没收物品的光点 */
    public static final int RETURN_MS = 7730;
    /** 成功：交还控制 */
    public static final int SUCCESS_END_MS = 8230;

    /** 单刀：唯一一刀刺入 */
    public static final int SINGLE_STAB_MS = 830;
    /** 单刀：目标在服务端死亡 */
    public static final int SINGLE_DEATH_MS = 1650;
    /** 单刀：交还 */
    public static final int SINGLE_END_MS = 2500;

    /** 失败（从判错那一格填满的时刻 g 算）：挣脱的隆隆声 */
    public static final int FAIL_TAUNT = 300;
    /** 失败：玄骸挣脱、处决者被推开 */
    public static final int FAIL_BREAKFREE = 700;
    /** 失败：切宽镜头 */
    public static final int FAIL_WIDE = 2000;
    /** 失败：暖色冲击，玄骸化雾 */
    public static final int FAIL_ESCAPE = 2400;
    /** 失败：交还 */
    public static final int FAIL_END = 2750;
    /** 失败：原 HUD 回来（比交还晚，横幅出来以后才回来） */
    public static final int FAIL_HUD = 3400;

    /** 奴仆：交还 */
    public static final int THRALL_END_MS = 2200;
    /** 奴仆：目标在服务端死亡 */
    public static final int THRALL_DEATH_MS = 1500;

    /** 失败冲击的颜色（暖色） */
    public static final int ESCAPE_RGB = 0xFFB060;
    private static final int ESCAPE_SPARK_RGB = 0xFFD08A;

    /** 冻帧跳到某一刻时，往前这么多毫秒以内的一次性阶段补放一遍（截图时看得到刚放的火花） */
    private static final int SEEK_REPLAY_MS = 250;

    /** 这一场的结果分支 */
    public enum Outcome {
        /** 玄骸三刀全对 */
        SUCCESS,
        /** 玄骸第 k 刀判错 */
        FAIL,
        /** 玄骸卡没放齐，只捅一刀 */
        SINGLE,
        /** 奴仆 */
        THRALL
    }

    private record Stage(int ms, Consumer<MercyClient.Exec> action) {
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
        if (p.stabs() == 1) {
            return Outcome.SINGLE;
        }
        return p.prefix() >= 3 ? Outcome.SUCCESS : Outcome.FAIL;
    }

    /** 失败时判错的是第几刀（1~3）；不是失败返回 0 */
    public static int failK(MercyPlan.Plan p) {
        return outcome(p) == Outcome.FAIL ? Math.min(3, p.prefix() + 1) : 0;
    }

    /** 失败时判错那一格「本该填满发光」的时刻 g_k；不是失败返回 -1 */
    public static int failFillMs(MercyPlan.Plan p) {
        int k = failK(p);
        return k == 0 ? -1 : MercyVariant.LICH_FILL_MS[k - 1];
    }

    /** 交还控制的时刻（镜头、视角类型改回来，刀模已经缩完） */
    public static int handbackMs(MercyPlan.Plan p, boolean shortOutro) {
        return switch (outcome(p)) {
            case SUCCESS -> shortOutro ? THIRD_MS : SUCCESS_END_MS;
            case FAIL -> failFillMs(p) + FAIL_END;
            case SINGLE -> SINGLE_END_MS;
            case THRALL -> THRALL_END_MS;
        };
    }

    /** 原 HUD 藏到什么时候；奴仆不藏，返回 0 */
    public static int hudUntilMs(MercyPlan.Plan p, boolean shortOutro) {
        return switch (outcome(p)) {
            case SUCCESS, SINGLE -> handbackMs(p, shortOutro);
            case FAIL -> failFillMs(p) + FAIL_HUD;
            case THRALL -> 0;
        };
    }

    /** 这场处决在客户端上整个结束（交还、HUD 回来都过了） */
    public static int endMs(MercyPlan.Plan p, boolean shortOutro) {
        int end = Math.max(handbackMs(p, shortOutro), hudUntilMs(p, shortOutro));
        if (shortOutro && outcome(p) == Outcome.SUCCESS) {
            // 短版在 7320 交还，处决者的姿态再用 400 ms 回到站姿
            end = Math.max(end, THIRD_MS + 400);
        }
        return end;
    }

    /** 到了这一刻，目标实体消失是正常的（服务端已经让它死了或离场了），不当成中断 */
    public static boolean targetMayVanish(MercyPlan.Plan p, long t) {
        return switch (outcome(p)) {
            case SUCCESS -> t >= SUCCESS_DEATH_MS;
            case FAIL -> t >= failFillMs(p) + FAIL_ESCAPE;
            case SINGLE -> t >= SINGLE_DEATH_MS;
            case THRALL -> t >= THRALL_DEATH_MS;
        };
    }

    /**
     * 每一刀刺入（刀尖接触）的时刻，按这一场实际会捅的刀数给：成功三个，失败捅到判错那一刀为止，单刀一个，奴仆一个
     */
    public static int[] stabTimes(MercyPlan.Plan p) {
        MercyVariant v = p.variant();
        return switch (outcome(p)) {
            case SUCCESS -> v.stabMs();
            case FAIL -> {
                int k = failK(p);
                int[] all = v.stabMs();
                int[] out = new int[k];
                System.arraycopy(all, 0, out, 0, k);
                yield out;
            }
            case SINGLE -> new int[]{SINGLE_STAB_MS};
            case THRALL -> new int[]{v.stabMs(0)};
        };
    }

    /** 第 {@code i} 刀（从 0 起）是不是「出结果的那一刀」（成功第三刀、判错那一刀、单刀）：推镜和震动加重 */
    public static boolean isHeavyStab(MercyPlan.Plan p, int i) {
        return switch (outcome(p)) {
            case SUCCESS -> i == 2;
            case FAIL -> i == failK(p) - 1;
            case SINGLE -> true;
            case THRALL -> false;
        };
    }

    /**
     * 刀模此刻伸出了多少（0 = 收着，1 = 全长）。玄骸 400 ms 起用 150 ms 伸出，奴仆一开始就伸；交还前 150 ms 缩回
     */
    public static float bladeExtension(MercyPlan.Plan p, long t, boolean shortOutro) {
        int growAt = p.variant().thrall ? 0 : CUT_IN_MS;
        int end = handbackMs(p, shortOutro);
        if (outcome(p) == Outcome.SUCCESS && !shortOutro) {
            // 成功收尾抽刀以后刀还在手上，到交还前才缩
            end = SUCCESS_END_MS;
        }
        if (p.variant().thrall) {
            // 奴仆收势时就先缩回，免得推开以后刀还亮着
            end = 2050;
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
    static MercyTimeline build(MercyClient.Exec e) {
        MercyTimeline tl = new MercyTimeline();
        MercyPlan.Plan p = e.plan;
        Outcome oc = outcome(p);

        // 0：倒地低鸣收掉（处决开始）
        tl.at(0, x -> MercyFx.stopLoop(x.targetId, 150));

        if (oc == Outcome.THRALL) {
            int stab = p.variant().stabMs(0);
            tl.at(stab, x -> {
                Entity t = x.target();
                if (t == null) {
                    return;
                }
                if (KuvaSoundPlayer.gate("mercy.thrall." + x.targetId, 300)) {
                    world(KuvaLichSounds.MERCY_THRALL.get(), chest(t), 0.9F, KuvaSoundPlayer.jitter(1.0F, 0.04F));
                }
                RequiemFx.bladeSpark(t, 1, false);
            });
            return tl.done();
        }

        // 400：硬切进机位、刀模伸出、抽刀声和手前一道短光
        tl.at(CUT_IN_MS, x -> {
            world(KuvaLichSounds.MERCY_DRAW.get(), x.handPos(), 1.0F, 1.0F);
            drawFlare(x);
        });

        if (oc == Outcome.SINGLE) {
            tl.at(SINGLE_STAB_MS, x -> {
                Entity t = x.target();
                if (t == null) {
                    return;
                }
                world(KuvaLichSounds.MERCY_STAB_FINAL.get(), chest(t), 1.0F, KuvaSoundPlayer.jitter(1.0F, 0.02F));
                RequiemFx.bladeSpark(t, 3, false);
            });
            return tl.done();
        }

        int[] stab = p.variant().stabMs();
        int[] fill = MercyVariant.LICH_FILL_MS;
        int k = failK(p);
        int last = oc == Outcome.SUCCESS ? 3 : k;
        for (int i = 0; i < last; i++) {
            final int n = i + 1;
            final boolean result = oc == Outcome.SUCCESS ? n == 3 : n == k;
            SoundEvent ev = result ? KuvaLichSounds.MERCY_STAB_FINAL.get()
                    : (n == 1 ? KuvaLichSounds.MERCY_STAB_1.get() : KuvaLichSounds.MERCY_STAB_2.get());
            final float vol = (n == 1 && !result) ? 0.7F : 1.0F;
            tl.at(stab[i], x -> {
                Entity t = x.target();
                if (t == null) {
                    return;
                }
                world(ev, chest(t), vol, KuvaSoundPlayer.jitter(1.0F, 0.02F));
                RequiemFx.bladeSpark(t, n, false);
            });
            // 格子填满（锁扣声）；判错那一格换成碎裂声
            final boolean wrong = oc == Outcome.FAIL && n == k;
            tl.at(fill[i], x -> ui(x, wrong ? KuvaLichSounds.REQUIEM_GLYPH_REJECT_MERCY.get()
                    : KuvaLichSounds.REQUIEM_GLYPH_LOCK_MERCY.get(), wrong ? 1.0F : 0.8F));
        }

        if (oc == Outcome.SUCCESS) {
            tl.at(VERDICT_MS, x -> {
                ui(x, KuvaLichSounds.MERCY_VERDICT.get(), 1.0F);
                if (x.cinematic) {
                    KuvaMixer.duck(MercyClient.DUCK_KEY, -12.0F, 300);
                }
            });
            tl.at(LAST_SPARK_MS, x -> {
                Entity t = x.target();
                if (t != null) {
                    RequiemFx.bladeSpark(t, 3, true);
                    KuvaGlow.boost(x.targetId, 1.6F, 200);
                }
            });
            tl.at(COMM_DISMISS_MS, x -> {
                if (x.cinematic) {
                    CommHud.dismiss(CommSpeaker.LICH);
                }
            });
            tl.at(CLOSEUP_MS, x -> {
                if (!x.cinematic || !MercyCamera.takesOver(x)) {
                    return;
                }
                KuvaGlow.boost(x.targetId, 1.6F, THIRD_MS - CLOSEUP_MS);
                Entity t = x.target();
                if (t != null) {
                    closeupMist(t);
                }
            });
            tl.at(THIRD_MS, x -> {
                if (!x.cinematic) {
                    return;
                }
                if (x.shortOutro) {
                    KuvaMixer.release(MercyClient.DUCK_KEY, 300);
                } else {
                    KuvaMixer.duck(MercyClient.DUCK_KEY, -4.0F, 150);
                }
            });
            tl.at(DRAW_OUT_MS, x -> {
                Entity t = x.target();
                if (t != null) {
                    LichFx.dissolve(t, LichFx.Kind.REQUIEM);
                }
            });
            if (p.returnsItems()) {
                tl.at(RETURN_MS, MercyTimeline::returnLight);
            }
            tl.at(SUCCESS_END_MS, x -> {
                if (x.cinematic) {
                    KuvaMixer.release(MercyClient.DUCK_KEY, 300);
                }
            });
            return tl.done();
        }

        // 失败（判错第 k 刀，g = 那一格本该填满的时刻）
        int g = fill[k - 1];
        tl.at(g + FAIL_TAUNT, x -> {
            Entity t = x.target();
            if (t != null) {
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_TAUNT.get(), SoundSource.HOSTILE,
                        chest(t), 1.0F, 1.0F).follow(t).tier(FxTier.S));
            }
        });
        final int kk = k;
        tl.at(g + FAIL_BREAKFREE, x -> {
            Entity t = x.target();
            if (t != null) {
                // 刀被挣出来那一下
                RequiemFx.bladeSpark(t, kk, false);
            }
        });
        tl.at(g + FAIL_ESCAPE, x -> {
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

    private MercyTimeline at(int ms, Consumer<MercyClient.Exec> action) {
        if (!stages.isEmpty() && stages.get(stages.size() - 1).ms > ms) {
            sorted = false;
        }
        stages.add(new Stage(ms, action));
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
        if (outcome(e.plan) != Outcome.SUCCESS) {
            KuvaMixer.release(MercyClient.DUCK_KEY, 0);
            return;
        }
        if (t >= VERDICT_MS && t < THIRD_MS) {
            KuvaMixer.duck(MercyClient.DUCK_KEY, -12.0F, 0);
        } else if (t >= THIRD_MS && t < SUCCESS_END_MS && !e.shortOutro) {
            KuvaMixer.duck(MercyClient.DUCK_KEY, -4.0F, 0);
        } else {
            KuvaMixer.release(MercyClient.DUCK_KEY, 0);
        }
    }

    // ==================== 小工具 ====================

    private static final RandomSource RANDOM = RandomSource.create();

    /** 目标胸口（刀口）的位置 */
    static Vec3 chest(Entity t) {
        return t.position().add(0.0D, t.getBbHeight() * 0.62D, 0.0D);
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
