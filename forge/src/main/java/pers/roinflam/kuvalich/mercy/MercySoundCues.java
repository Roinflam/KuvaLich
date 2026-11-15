package pers.roinflam.kuvalich.mercy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 处决声音叠层的时刻表。每一刀 = 挥刀（刀风 + 衣甲）+ 原有的刀声 + 刺入（肉 + 金属）+ 低频闷响，出结果那一刀加尾音；
 * 另有揪、腾跃、落地这些动作声。时刻全部从 {@link MercyBeats} 的刀时刻推出来，改刀时刻声音跟着走。
 *
 * <p>{@link Cue#ms} 是声音里「那一下」该落在演出钟上的时刻；文件里那一下离文件开头有 {@link #lead} 毫秒，
 * 播放端要提前这么多起播（音高不是 1 时按变速折算，{@link Cue#startMs}）。</p>
 */
public final class MercySoundCues {

    public enum Layer {
        SWING("mercy.swing"), STAB_1("mercy.stab.1"), STAB_2("mercy.stab.2"), STAB_FINAL("mercy.stab.final"),
        PIERCE("mercy.pierce"), THUD("mercy.thud"), TAIL("mercy.tail"), TAIL_WRONG("mercy.tail.wrong"),
        BREAKFREE("mercy.breakfree"), THRALL("mercy.thrall"), GRAB("mercy.grab"), LEAP("mercy.leap"), LAND("mercy.land");

        /** 声音事件 id（模组命名空间下）；必须带 mercy. 前缀，全局压混按前缀豁免 */
        public final String id;

        Layer(String id) {
            this.id = id;
        }
    }

    /** 处决这一场的结果，和客户端时间轴的分法一致 */
    public enum Result { SUCCESS, CONVERT, FAIL, ADVANCE, THRALL }

    // 文件里「那一下」的位置：挥刀层刀风在 240 ms 被截断；原有三档刀声的刺入点是配方里的 T（0 / 50 / 150）；
    // 腾跃层在蹬地后 100 ms 最响，文件里最响点在 220 ms
    private static final Map<Layer, Integer> LEAD = new EnumMap<>(Layer.class);

    static {
        for (Layer l : Layer.values()) {
            LEAD.put(l, 0);
        }
        LEAD.put(Layer.SWING, 240);
        LEAD.put(Layer.STAB_2, 50);
        LEAD.put(Layer.STAB_FINAL, 150);
        LEAD.put(Layer.LEAP, 120);
    }

    public static int lead(Layer l) {
        return LEAD.get(l);
    }

    /**
     * @param stab 第几刀（从 0 起）的叠层；动作声是 −1
     * @param impact 刺入那一下的层，顿帧压混时不压它
     */
    public record Cue(Layer layer, int ms, float vol, float pitch, int stab, boolean impact) {
        /** 起播时刻：音高是变速播放，文件里的提前量跟着缩放 */
        public int startMs() {
            return ms - Math.round(lead(layer) / Math.max(pitch, 0.1F));
        }
    }

    // 动作声：挂在第 anchor 刀（从 0 起，THRALL_LAND = 奴仆整身轨道落地）上的偏移；这一场至少捅到 need 刀才有，
    // successOnly 的只在成功收尾时有（判错、挣开时人没有做那个动作）
    private record Foley(Layer layer, int anchor, int offset, int need, boolean successOnly) {
    }

    private static final int THRALL_LAND = -2;
    private static final Map<MercyVariant, List<Foley>> FOLEY = new EnumMap<>(MercyVariant.class);

    static {
        // 正面直刺：第三刀前甩上头顶，第三刀后蹬开后空翻、落地
        foley(MercyVariant.LICH_A, f(Layer.GRAB, 2, -420, 3, false), f(Layer.LEAP, 2, 260, 3, true),
                f(Layer.LAND, 2, 700, 3, true));
        // 背后锁颈：第一刀拔出来往后勒住，第二、三刀之间原地转一圈（转到一半最响）再扣住
        foley(MercyVariant.LICH_B, f(Layer.GRAB, 0, 520, 2, false), f(Layer.LEAP, 2, -300, 3, false),
                f(Layer.GRAB, 2, -20, 3, false));
        // 贴身上挑：第三刀后蹬开后空翻、落地
        foley(MercyVariant.LICH_C, f(Layer.LEAP, 2, 260, 3, true), f(Layer.LAND, 2, 700, 3, true));
        // 翻滚绕背：第二刀后蹬出去翻滚、落地，第三刀前拧身按住肩
        foley(MercyVariant.LICH_D, f(Layer.LEAP, 1, 540, 3, false), f(Layer.LAND, 1, 990, 3, false),
                f(Layer.GRAB, 2, -170, 3, false));
        // 踏背下刺：第一刀后蹬地前空翻、落在背上，第三刀后蹬开后空翻（化光、现身有自己的声音）
        foley(MercyVariant.LICH_E, f(Layer.LEAP, 0, 540, 2, false), f(Layer.LAND, 1, -240, 2, false),
                f(Layer.LEAP, 2, 260, 3, true));
        // 奴仆：抓肩直刺 揪肩、拽直、拔刀推开
        foley(MercyVariant.THRALL_1, f(Layer.GRAB, 0, -620, 1, false), f(Layer.GRAB, 0, -340, 1, false),
                f(Layer.GRAB, 0, 540, 1, false));
        // 锁喉背刺 勒住、再拽、松手蹬出去
        foley(MercyVariant.THRALL_2, f(Layer.GRAB, 0, -650, 1, false), f(Layer.GRAB, 0, -330, 1, false),
                f(Layer.LAND, 0, 580, 1, false));
        // 压颈下刺 按下、再压、横推
        foley(MercyVariant.THRALL_3, f(Layer.GRAB, 0, -700, 1, false), f(Layer.GRAB, 0, -320, 1, false),
                f(Layer.GRAB, 0, 530, 1, false));
        // 穿胸绕背 揪肩、抽刀就滚、落地
        foley(MercyVariant.THRALL_4, f(Layer.GRAB, 0, -530, 1, false), f(Layer.LEAP, 0, 270, 1, false),
                f(Layer.LAND, THRALL_LAND, 0, 1, false));
        // 飞跃下刺 蹬地、落地
        foley(MercyVariant.THRALL_5, f(Layer.LEAP, 0, -520, 1, false), f(Layer.LAND, 0, 480, 1, false));
    }

    private MercySoundCues() {
    }

    private static Foley f(Layer l, int anchor, int offset, int need, boolean successOnly) {
        return new Foley(l, anchor, offset, need, successOnly);
    }

    private static void foley(MercyVariant v, Foley... f) {
        FOLEY.put(v, List.of(f));
    }

    /**
     * 一场处决的全部叠层声音，按起播时刻排好
     *
     * @param stabsDone 这一场实际捅几刀（判错捅到判错那一刀为止）
     */
    public static List<Cue> cues(MercyVariant v, Result r, int stabsDone) {
        List<Cue> out = new ArrayList<>();
        if (v.thrall) {
            MercyBeats.Thrall b = MercyBeats.thrall(v);
            int s = b.stab;
            // 奴仆轻、短：音高往上（文件变短），音量压低，没有尾音
            out.add(new Cue(Layer.SWING, s, 0.5F, 1.15F, 0, false));
            out.add(new Cue(Layer.THRALL, s, 0.8F, 1.0F, 0, true));
            out.add(new Cue(Layer.PIERCE, s, 0.55F, 1.12F, 0, true));
            out.add(new Cue(Layer.THUD, s, 0.45F, 1.2F, 0, true));
            for (Foley f : FOLEY.getOrDefault(v, List.of())) {
                int at = f.anchor == THRALL_LAND ? b.land : s + f.offset;
                if (at >= 0) {
                    out.add(new Cue(f.layer, at, foleyVol(f.layer, true), 1.05F, -1, false));
                }
            }
            return sorted(out);
        }
        MercyBeats.Lich b = MercyBeats.lich(v);
        int last = Math.max(1, Math.min(3, stabsDone));
        for (int i = 0; i < last; i++) {
            int n = i + 1;
            int s = b.stab(i);
            boolean result = n == last;
            // 定生死的那一刀：成功 / 感化的第三刀、判错那一刀；挣开站起的最后一刀还是普通的一刀
            boolean heavy = result && r != Result.ADVANCE;
            Layer stab = heavy ? Layer.STAB_FINAL : (n == 1 ? Layer.STAB_1 : Layer.STAB_2);
            float k = heavy ? 1.0F : (n == 1 ? 0.0F : 0.5F);
            out.add(new Cue(Layer.SWING, s, lerp(0.7F, 1.0F, k), heavy ? 0.94F : 1.0F, i, false));
            // 出结果那一刀四五层同时起，录下来判错那刀碰到过 0 dBFS，重的这几层整体收 2～3 dB
            out.add(new Cue(stab, s, n == 1 && !result ? 0.6F : (heavy ? 0.7F : 0.85F), 1.0F, i, true));
            out.add(new Cue(Layer.PIERCE, s, heavy ? 0.78F : lerp(0.7F, 0.95F, k), 1.0F, i, true));
            out.add(new Cue(Layer.THUD, s, heavy ? 0.85F : lerp(0.6F, 1.0F, k), heavy ? 0.88F : 1.0F, i, true));
            if (heavy) {
                out.add(new Cue(r == Result.FAIL ? Layer.TAIL_WRONG : Layer.TAIL, s, r == Result.FAIL ? 0.6F : 0.75F,
                        1.0F, i, true));
            }
        }
        if (r == Result.FAIL) {
            out.add(new Cue(Layer.BREAKFREE, b.fill(last) + b.failBreakFree, 1.0F, 1.0F, -1, false));
        } else if (r == Result.ADVANCE) {
            out.add(new Cue(Layer.BREAKFREE, b.fill(last) + b.advanceBreakFree, 0.9F, 0.95F, -1, false));
        }
        boolean success = r == Result.SUCCESS;
        for (Foley f : FOLEY.getOrDefault(v, List.of())) {
            if (f.need > last || f.successOnly && !success) {
                continue;
            }
            out.add(new Cue(f.layer, b.stab(f.anchor) + f.offset, foleyVol(f.layer, false), 1.0F, -1, false));
        }
        return sorted(out);
    }

    private static float foleyVol(Layer l, boolean thrall) {
        float v = l == Layer.LAND ? 0.9F : 0.8F;
        return thrall ? v * 0.75F : v;
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static List<Cue> sorted(List<Cue> out) {
        out.sort((a, b) -> Integer.compare(a.startMs(), b.startMs()));
        return Collections.unmodifiableList(out);
    }
}
