package pers.roinflam.kuvalich.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端放声音的帮手：造出 {@link KuvaSoundInstance}，再交给声音管理器
 *
 * <p>三种放法：</p>
 * <ul>
 *   <li>{@link #ui}：不定位、{@code MASTER}、乘界面音量，可以是立体声文件；</li>
 *   <li>{@link #at}：定位在演出坐标、按 sounds.json 的衰减距离线性衰减（文件必须是单声道）；</li>
 *   <li>{@link #atFixed}：定位在演出坐标、不随距离衰减，OpenAL 照样按方位声像，字幕箭头也指得对，
 *       音量由调用方按距离自己插值（{@link #distFalloff}）。「攻击者本人听自己的命中」和登场刺音用它。
 *       不用原版的 {@code forUI} 做这类声音：它写死了 MASTER、相对听者、坐标 (0,0,0)，没有方位。</li>
 * </ul>
 * <p>前三个只造实例、不播放，调用方可以接着设档位、跟随、淡出，最后 {@link #play}。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaSoundPlayer {

    private static final Map<String, Long> GATES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private KuvaSoundPlayer() {
    }

    /** 界面声：不定位、MASTER、乘界面音量（{@code ui_volume}）。不会被 S 级演出压低 */
    public static KuvaSoundInstance ui(SoundEvent ev, float vol, float pitch) {
        return KuvaSoundInstance.create(ev, SoundSource.MASTER, vol * EffectsDisplayConfig.uiVolume(), pitch)
                .relativeToListener()
                .tier(FxTier.A);
    }

    /**
     * 循环的界面声：同 {@link #ui}，立体声、MASTER、乘界面音量，循环播放，开头 {@code fadeInMs} 毫秒淡入。
     * 只造实例、不播放；调用方 {@link #play} 之后用 {@link KuvaSoundInstance#fadeOut} 收尾
     */
    public static KuvaSoundInstance uiLoop(SoundEvent ev, float vol, int fadeInMs) {
        KuvaSoundInstance s = ui(ev, vol, 1.0F).loop(true);
        return fadeInMs > 0 ? s.fadeIn(fadeInMs) : s;
    }

    /** 世界声：定位在 {@code p}，按 sounds.json 的衰减距离线性衰减 */
    public static KuvaSoundInstance at(SoundEvent ev, SoundSource src, Vec3 p, float vol, float pitch) {
        return KuvaSoundInstance.create(ev, src, vol, pitch).position(p);
    }

    /**
     * 本人听的演出声：不定位、走 {@code src} 的音量分类（不像 {@link #ui} 那样走 MASTER）。
     * 处决时镜头绕着走，定位声会跟着机位左右乱飘，处决者自己听的用它
     */
    public static KuvaSoundInstance self(SoundEvent ev, SoundSource src, float vol, float pitch) {
        return KuvaSoundInstance.create(ev, src, vol, pitch).relativeToListener();
    }

    /** 世界声：定位在 {@code p}，但不随距离衰减 */
    public static KuvaSoundInstance atFixed(SoundEvent ev, SoundSource src, Vec3 p, float vol, float pitch) {
        return KuvaSoundInstance.create(ev, src, vol, pitch).position(p).noAttenuation();
    }

    /** 立刻播放 */
    public static KuvaSoundInstance play(KuvaSoundInstance sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    /** 延后 {@code ticks} 个 tick 播放 */
    public static KuvaSoundInstance play(KuvaSoundInstance sound, int ticks) {
        if (ticks <= 0) {
            return play(sound);
        }
        Minecraft.getInstance().getSoundManager().playDelayed(sound, ticks);
        return sound;
    }

    /** 不随距离衰减的声音自己插值音量：{@code d0} 以内 1，{@code d1} 处降到 {@code min}，中间线性 */
    public static float distFalloff(double d, double d0, double d1, float min) {
        if (d <= d0) {
            return 1.0F;
        }
        if (d >= d1) {
            return min;
        }
        float k = (float) ((d - d0) / (d1 - d0));
        return 1.0F + (min - 1.0F) * k;
    }

    /** 在 {@code base} 上下各 {@code halfRange} 的范围里随机（音高、音量用） */
    public static float jitter(float base, float halfRange) {
        return base + (RANDOM.nextFloat() * 2.0F - 1.0F) * halfRange;
    }

    /**
     * 防刷屏：同一个 {@code key} 在 {@code ms} 毫秒内只放行一次。放行时返回 true 并记下时间
     */
    public static boolean gate(String key, int ms) {
        long now = System.nanoTime() / 1_000_000L;
        Long last = GATES.get(key);
        if (last != null && now - last < ms) {
            return false;
        }
        if (GATES.size() > 256) {
            GATES.values().removeIf(t -> now - t > 60_000L);
        }
        GATES.put(key, now);
        return true;
    }

    /**
     * 传了音高的声音，画面上的爆发点（{@code T*}）要跟着缩放：OpenAL 的音高是变速播放，音高 0.5 时文件长一倍，爆发点也晚一倍
     */
    public static int scaleT(int tStarMs, float pitch) {
        return Math.round(tStarMs / Math.max(pitch, 0.1F));
    }
}
