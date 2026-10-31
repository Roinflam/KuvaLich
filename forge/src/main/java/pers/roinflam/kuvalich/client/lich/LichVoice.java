package pers.roinflam.kuvalich.client.lich;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.SplittableRandom;

/**
 * 玄骸的「嘟囔」：一句台词按字数排成一串音节，按表播放声音，并给点头和发光提供包络
 *
 * <p>排法：</p>
 * <ul>
 *   <li>音节数：汉字 c 个折 {@code ceil(c × 0.75)}，拉丁词 w 个折 {@code ceil(w × 1.3)}，两者相加，夹到 2~16；</li>
 *   <li>每个音节 150~190 ms，相邻两个音节的起点相隔 180~230 ms；</li>
 *   <li>标点按它在原文里的位置比例落到音节序列上：逗号一类后面停 260 ms，句号一类后面停 380 ms；</li>
 *   <li>音高以 {@link LichProfile#voicePitch} 为基准，逐音节随机游走 ±1.2 半音（夹到 ±3），句末一个音节落到 −3 半音，
 *       感叹号结尾的句末改成 +2 半音、音量 ×1.15；</li>
 *   <li>每只玄骸只用 {@link LichProfile#voiceAccent} 给的 7 个音节文件，随机挑，不连着用同一个。</li>
 * </ul>
 *
 * <p>同一只玄骸说同一句话，排出来的表每次都一样（随机数只由种子和台词决定），所以调试时把时间轴冻住再放开、重新排一遍，
 * 后半句接得上。</p>
 *
 * <p>时钟用演出钟：单人暂停时音节不往下走，继续后接着放。播得晚了超过 {@value #LATE_SKIP_MS} ms 的音节直接跳过，
 * 免得卡一下之后几个音节挤在一起响。</p>
 *
 * <p>选项 {@code lich_mutter} 关掉时不出声，时间表照排，点头和发光照常。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichVoice {

    /**
     * 一句话的音节时间表，毫秒
     *
     * @param startMs 开始时刻（演出钟）
     * @param onsetMs 每个音节的起点，相对 {@code startMs}
     * @param durMs   每个音节的长度
     * @param endMs   最后一个音节结束的时刻（演出钟）
     */
    public record Schedule(long startMs, int[] onsetMs, int[] durMs, long endMs) {

        /** 从第一个音节起到最后一个音节结束，一共多少毫秒 */
        public int lengthMs() {
            return (int) (endMs - startMs);
        }

        /** 音节数 */
        public int count() {
            return onsetMs.length;
        }
    }

    /** 包络的起音、释放 */
    public static final int ATTACK_MS = 30;
    public static final int RELEASE_MS = 90;
    /** 逗号一类、句号一类后面停多久 */
    public static final int PAUSE_COMMA_MS = 260;
    public static final int PAUSE_STOP_MS = 380;
    /** 音节数上下限 */
    public static final int MIN_SYLLABLES = 2;
    public static final int MAX_SYLLABLES = 16;

    /** 嘟囔的代码音量 */
    private static final float VOLUME = 0.8F;
    /** 感叹句句末那个音节的音量倍数 */
    private static final float SHOUT_VOLUME = 1.15F;
    /** 晚了这么多还没放的音节就不放了 */
    private static final int LATE_SKIP_MS = 120;
    /** 同时在念的句子最多几句（通讯窗一次只有一句，多出来的是调试命令连发） */
    private static final int MAX_ACTIVE = 4;
    /** 随机数加盐，免得和别的派生项撞同一串 */
    private static final long SALT = 0x6D75_7474_6572L;

    /** 正在念的句子 */
    private static final ArrayList<Voice> ACTIVE = new ArrayList<>(MAX_ACTIVE);

    private LichVoice() {
    }

    /** 一句话排好的全部数据：时间表加每个音节的文件、音高、音量 */
    private static final class Plan {
        final Schedule schedule;
        final int[] variant;
        final float[] pitch;
        final float[] volume;

        Plan(Schedule schedule, int[] variant, float[] pitch, float[] volume) {
            this.schedule = schedule;
            this.variant = variant;
            this.pitch = pitch;
            this.volume = volume;
        }
    }

    /** 正在念的一句 */
    private static final class Voice {
        final Plan plan;
        /** 跟随的实体 id；-1 = 没有关联世界里的实体 */
        final int entityId;
        /** 通讯窗上的那一句（肖像的点头读它） */
        final boolean hud;
        final boolean audible;
        int next;
        /** 从这一刻起不再出新音节（字幕提前消失）；Long.MAX_VALUE = 没掐 */
        long cutMs = Long.MAX_VALUE;
        @Nullable
        KuvaSoundInstance playing;

        Voice(Plan plan, int entityId, boolean hud, boolean audible) {
            this.plan = plan;
            this.entityId = entityId;
            this.hud = hud;
            this.audible = audible;
        }
    }

    // ==================== 对外 ====================

    /**
     * 为一句话排音节并按表排声音，返回时间表
     *
     * @param seed   这只玄骸的种子，决定音高和口音
     * @param source 声音和点头跟随的实体，可为空
     */
    public static Schedule speak(int seed, String text, long startMs, @Nullable Entity source) {
        return speak(seed, text, startMs, source, false, EffectsDisplayConfig.lichMutter());
    }

    /**
     * 同 {@link #speak(int, String, long, Entity)}，多两个开关
     *
     * @param hud     这是通讯窗上正在显示的那一句：肖像（不在世界里的假实体）的点头读它
     * @param audible 出不出声；不出声时时间表照排，包络照算
     */
    public static Schedule speak(int seed, String text, long startMs, @Nullable Entity source, boolean hud,
                                 boolean audible) {
        Plan plan = plan(seed, text, startMs);
        int entityId = source != null ? source.getId() : -1;
        // 同一个实体、或者通讯窗上一句还没念完又开新的一句：旧的立刻收掉
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Voice v = ACTIVE.get(i);
            if ((entityId >= 0 && v.entityId == entityId) || (hud && v.hud)) {
                cut(v, startMs, 80);
                ACTIVE.remove(i);
            }
        }
        while (ACTIVE.size() >= MAX_ACTIVE) {
            Voice v = ACTIVE.remove(0);
            cut(v, startMs, 80);
        }
        ACTIVE.add(new Voice(plan, entityId, hud, audible));
        return plan.schedule;
    }

    /**
     * 只排时间表，不出声、不登记（通讯窗提前算字幕停留时长用）。和 {@link #speak} 对同一句排出来的表完全一样
     */
    public static Schedule schedule(int seed, String text, long startMs) {
        return plan(seed, text, startMs).schedule;
    }

    /**
     * 掐掉一句：从现在起不再出新音节，正在响的那个 {@code fadeMs} 毫秒淡出。包络跟着在 {@value #RELEASE_MS} ms 里落下去
     */
    public static void stop(@Nullable Schedule schedule, int fadeMs) {
        if (schedule == null) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        for (Voice v : ACTIVE) {
            if (v.plan.schedule == schedule) {
                cut(v, now, fadeMs);
            }
        }
    }

    /** 把正在念的全部掐掉（换世界、关通讯窗时用） */
    public static void stopAll(int fadeMs) {
        long now = KuvaFxDirector.clockMs();
        for (Voice v : ACTIVE) {
            cut(v, now, fadeMs);
        }
    }

    /**
     * 此刻这只实体的嘟囔包络 0~1，没有在说话为 0
     *
     * <p>世界里的玄骸按实体 id 找它正在念的那句；传 {@code null}，或者传 id 为负数的假实体（肖像、档案里的模型），
     * 得到的是通讯窗上正在念的那一句。</p>
     */
    public static float envelope(@Nullable Entity entity, long nowMs) {
        boolean hud = entity == null || entity.getId() < 0;
        int id = hud ? -1 : entity.getId();
        float best = 0.0F;
        for (int i = 0, n = ACTIVE.size(); i < n; i++) {
            Voice v = ACTIVE.get(i);
            if (hud ? !v.hud : v.entityId != id) {
                continue;
            }
            float e = envelopeOf(v.plan.schedule, v.cutMs, nowMs);
            if (e > best) {
                best = e;
            }
        }
        return best;
    }

    /** 一张时间表在某一刻的包络（不看有没有登记），调试画线用 */
    public static float envelopeOf(Schedule s, long cutMs, long nowMs) {
        long rel = nowMs - s.startMs();
        if (rel < 0 || nowMs > s.endMs() + RELEASE_MS) {
            return 0.0F;
        }
        long cutRel = cutMs == Long.MAX_VALUE ? Long.MAX_VALUE : cutMs - s.startMs();
        float best = 0.0F;
        int[] on = s.onsetMs();
        int[] du = s.durMs();
        for (int k = 0; k < on.length; k++) {
            int a = on[k];
            if (a > rel || a >= cutRel) {
                break;
            }
            long end = Math.min(a + du[k], cutRel);
            float e;
            if (rel < a + ATTACK_MS && rel < end) {
                e = (rel - a) / (float) ATTACK_MS;
            } else if (rel < end) {
                e = 1.0F;
            } else {
                // 起音没走完就被掐的，从当时的高度往下落
                float top = end - a < ATTACK_MS ? (end - a) / (float) ATTACK_MS : 1.0F;
                e = top * (1.0F - (rel - end) / (float) RELEASE_MS);
            }
            if (e > best) {
                best = e;
            }
        }
        return Math.max(0.0F, Math.min(1.0F, best));
    }

    // ==================== 排音节 ====================

    private static Plan plan(int seed, String rawText, long startMs) {
        String text = stripTags(rawText);
        int len = text.codePointCount(0, text.length());

        // 数汉字和拉丁词
        int han = 0;
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isHan(cp)) {
                han++;
                inWord = false;
            } else if (Character.isLetterOrDigit(cp)) {
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            } else if (!(inWord && (cp == '\'' || cp == '’' || cp == '-'))) {
                inWord = false;
            }
        }
        int n = (int) Math.ceil(han * 0.75D) + (int) Math.ceil(words * 1.3D);
        n = Math.max(MIN_SYLLABLES, Math.min(MAX_SYLLABLES, n));

        // 标点按位置比例落到第几个音节后面
        int[] pauseAfter = new int[n];
        int idx = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int p = pauseOf(cp);
            if (p > 0 && len > 0) {
                int slot = (int) Math.round((idx + 1) / (double) len * n) - 1;
                // 落在最后一个音节后面的就是句末，不用停
                if (slot >= 0 && slot < n - 1 && p > pauseAfter[slot]) {
                    pauseAfter[slot] = p;
                }
            }
            idx++;
        }

        boolean shout = endsWithBang(text);
        SplittableRandom rng = new SplittableRandom(((long) seed << 32) ^ (text.hashCode() & 0xFFFFFFFFL) ^ SALT);
        int[] accent = LichProfile.voiceAccent(seed);
        float p0 = LichProfile.voicePitch(seed);

        int[] onset = new int[n];
        int[] dur = new int[n];
        int[] variant = new int[n];
        float[] pitch = new float[n];
        float[] volume = new float[n];
        int t = 0;
        double semi = 0.0D;
        int lastPick = -1;
        for (int k = 0; k < n; k++) {
            onset[k] = t;
            dur[k] = 150 + rng.nextInt(41);
            t += 180 + rng.nextInt(51) + pauseAfter[k];

            if (k > 0) {
                semi = Math.max(-3.0D, Math.min(3.0D, semi + (rng.nextDouble() * 2.4D - 1.2D)));
            }
            double s = k == n - 1 ? (shout ? 2.0D : -3.0D) : semi;
            pitch[k] = (float) Math.max(0.5D, Math.min(2.0D, p0 * Math.pow(2.0D, s / 12.0D)));
            volume[k] = VOLUME * (k == n - 1 && shout ? SHOUT_VOLUME : 1.0F);

            int pick;
            if (lastPick < 0) {
                pick = rng.nextInt(accent.length);
            } else {
                pick = rng.nextInt(accent.length - 1);
                if (pick >= lastPick) {
                    pick++;
                }
            }
            lastPick = pick;
            variant[k] = accent[pick];
        }
        long end = startMs + onset[n - 1] + dur[n - 1];
        return new Plan(new Schedule(startMs, onset, dur, end), variant, pitch, volume);
    }

    /** 标点后面停多久；不是这两类标点返回 0 */
    private static int pauseOf(int cp) {
        return switch (cp) {
            case '，', '、', '；', '：', '—', ',', ';', ':' -> PAUSE_COMMA_MS;
            case '。', '！', '？', '…', '.', '!', '?' -> PAUSE_STOP_MS;
            default -> 0;
        };
    }

    /** 汉字（连同日文假名、韩文，按一个字一个字念的文字） */
    public static boolean isHan(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL;
    }

    private static boolean endsWithBang(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '!' || c == '！') {
                return true;
            }
            // 句末的引号、括号、空白跳过去再看
            if (!Character.isWhitespace(c) && c != '"' && c != '”' && c != '」' && c != '』' && c != ')' && c != '）') {
                return false;
            }
        }
        return false;
    }

    /** 去掉故障段标记 {@code <x>} {@code </x>}（玄骸的台词里本来不该有，防一手） */
    private static String stripTags(String s) {
        if (s.indexOf('<') < 0) {
            return s;
        }
        return s.replace("<x>", "").replace("</x>", "");
    }

    private static void cut(Voice v, long now, int fadeMs) {
        if (v.cutMs > now) {
            v.cutMs = now;
        }
        v.next = v.plan.schedule.count();
        if (v.playing != null) {
            v.playing.fadeOut(fadeMs);
            v.playing = null;
        }
    }

    // ==================== 每帧放音节 ====================

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        long now = KuvaFxDirector.clockMs();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Voice v = ACTIVE.get(i);
            Schedule s = v.plan.schedule;
            int[] on = s.onsetMs();
            while (v.next < on.length && now >= s.startMs() + on[v.next]) {
                int k = v.next++;
                long due = s.startMs() + on[k];
                if (v.audible && due < v.cutMs && now - due <= LATE_SKIP_MS) {
                    v.playing = KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.UI_COMM_LICH_MUTTER.get(),
                            v.plan.volume[k], v.plan.pitch[k]).variant(v.plan.variant[k]));
                }
            }
            long over = Math.min(s.endMs(), v.cutMs) + RELEASE_MS;
            if (v.next >= on.length && now > over) {
                ACTIVE.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }
}
