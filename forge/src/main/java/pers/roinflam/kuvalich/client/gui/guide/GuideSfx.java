package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.Util;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.CommWho;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.GuideDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;

import javax.annotation.Nullable;

/**
 * 指南的声音挂点：开场、收场、翻页、回首页、点击、悬停、查阅、通讯
 *
 * <h3>挂在开场时间轴上</h3>
 * <p>开场、收场本来就是毫秒时间轴（{@link GuideIntro}），声音读同一个 {@code openT} / {@code closeT}，
 * 和画面天然同帧：卡顿时开场按比例变慢，声音也跟着晚。只在「这一帧开场时间轴前进不超过 60ms」时才放越过的 cue；
 * 一帧跳得太多（点一下跳过、卡顿、查阅短开场的映射）就把越过的 cue 静默作废 —— 晚到的声音比没有声音更难受。</p>
 *
 * <h3>只在第一次开书时放完整的两声</h3>
 * <p>完整开场原来每次都响一串，频繁开合、按 Ctrl 查阅时最先让人烦的就是它。现在本次游戏进程里第一次开书放
 * 「通电」（锁定环的一声烘在文件 420ms 处）和「就绪」（括号卡入 + 书名收束和弦，烘在一个文件里）两声，
 * 之后每次开书只放一声「快开」；查阅短开场也是快开，刚放过查阅音（300ms 内）就不叠。</p>
 *
 * <h3>开关</h3>
 * <p>{@code guide_sounds} 关掉时开场、收场、翻页、回首页都不响；点击、悬停、查阅照响 —— 它们是操作反馈，和原版按钮一样。
 * 通讯音归 {@code comm_sound} 管。全部声音走界面音量（{@code ui_volume}）。搜索框打字不配声。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideSfx {

    /** 开场 cue：通电 */
    private static final float CUE_POWER = 0f;
    /** 开场 cue：括号卡入 → 书名收束（文件里 340ms 处是收束和弦，正好落在开场时间轴 1260） */
    private static final float CUE_RESOLVE = 920f;
    /** 一帧前进超过这么多就不补放越过的 cue */
    private static final float MAX_STEP = 60f;
    /** 收场 cue：断电声从这里起播，文件里 240ms 处的「断电」落在收场 760ms 熄屏那一帧 */
    private static final float CUE_CLOSE = 520f;
    /** 跳过开场时，已经在响的开场声在这么久里淡掉（它们的后半段是照开场时间轴烘进文件的，跳过之后不能再响） */
    private static final int SKIP_FADE_MS = 60;
    /** 刚放过查阅音这么久之内，短开场不再放快开 */
    private static final long LOOKUP_QUIET_MS = 300L;

    /** 本次游戏进程里是不是已经开过书（不存盘） */
    private static boolean openedOnce;
    /** 上一次查阅音的时刻 */
    private static long lastLookupAt;

    private boolean begun;
    /** 这一次开书放完整的两个 cue */
    private boolean fullCues;
    private boolean powerDone;
    private boolean resolveDone;
    private float lastOpenT = -1f;
    @Nullable
    private KuvaSoundInstance power;
    @Nullable
    private KuvaSoundInstance resolve;
    private boolean closeDone;

    /**
     * 每帧，在 {@code fx.update} 之后：按开场 / 收场时间轴放 cue
     */
    void frame(GuideIntro fx, long now) {
        if (fx.closeT >= 0f) {
            if (!closeDone && fx.closeT >= CUE_CLOSE) {
                closeDone = true;
                if (GuideDisplayConfig.guideSounds()) {
                    play(KuvaLichSounds.UI_GUIDE_CLOSE_OFF.get(), 1f);
                }
            }
            return;
        }
        if (!begun) {
            begun = true;
            if (fx.quick) {
                openedOnce = true;
                if (now - lastLookupAt > LOOKUP_QUIET_MS) {
                    quickOpen();
                }
            } else if (!openedOnce) {
                openedOnce = true;
                fullCues = true;
            } else {
                quickOpen();
            }
        }
        if (!fullCues) {
            return;
        }
        float t = fx.openT;
        boolean smooth = t - lastOpenT <= MAX_STEP;
        if (!powerDone && t >= CUE_POWER && lastOpenT < CUE_POWER) {
            powerDone = true;
            if (smooth && GuideDisplayConfig.guideSounds()) {
                power = play(KuvaLichSounds.UI_GUIDE_OPEN_POWER.get(), 1f);
            }
        }
        if (!resolveDone && t >= CUE_RESOLVE && lastOpenT < CUE_RESOLVE) {
            resolveDone = true;
            if (smooth && GuideDisplayConfig.guideSounds()) {
                resolve = play(KuvaLichSounds.UI_GUIDE_OPEN_RESOLVE.get(), 1f);
            }
        }
        lastOpenT = t;
    }

    /**
     * 开场被跳过
     *
     * @param animOff 开场动画开关关着（第一帧就跳到终态）：放一声快开，开书照样有声音；
     *                否则是开场中被点击 / 按键跳过：已经在响的开场声掐掉、放跳过音，剩下的 cue 作废
     */
    void skipped(boolean animOff) {
        if (animOff) {
            if (!begun) {
                begun = true;
                openedOnce = true;
                quickOpen();
            }
            return;
        }
        if (!fullCues) {
            return;
        }
        fullCues = false;
        boolean anyPlayed = power != null || resolve != null;
        stopOpening();
        powerDone = true;
        resolveDone = true;
        if (anyPlayed && GuideDisplayConfig.guideSounds()) {
            play(KuvaLichSounds.UI_GUIDE_SKIP.get(), 1f);
        }
    }

    /**
     * 开始收场
     *
     * @param introRunning 开场还没走完：开场时间轴冻结在这一刻，烘进文件的开场声后半段不能再响，淡掉；
     *                     开场早已走完时不动它（就绪和弦的混响尾巴自然收掉）
     */
    void closing(boolean introRunning) {
        fullCues = false;
        if (introRunning) {
            stopOpening();
        }
        closeDone = false;
    }

    /** 收场被按键 / 点击提前关完（或开场动画关着时直接关）：断电声还没放就立即补放 */
    void closeFinished() {
        if (!closeDone) {
            closeDone = true;
            if (GuideDisplayConfig.guideSounds()) {
                play(KuvaLichSounds.UI_GUIDE_CLOSE_OFF.get(), 1f);
            }
        }
    }

    /** 换页：翻到一篇条目，或回到首页 */
    static void page(boolean home) {
        if (!GuideDisplayConfig.guideSounds() || !KuvaSoundPlayer.gate("guide.page", 80)) {
            return;
        }
        play(home ? KuvaLichSounds.UI_GUIDE_HOME.get() : KuvaLichSounds.UI_GUIDE_PAGE.get(), 1f);
    }

    /** 按钮（「Aa」、开场动画开关、调界面大小） */
    static void click() {
        if (KuvaSoundPlayer.gate("guide.click", 40)) {
            play(KuvaLichSounds.UI_GUIDE_CLICK.get(), 1f);
        }
    }

    /** 首页悬停的格子换成了另一个 */
    static void hover() {
        if (KuvaSoundPlayer.gate("guide.hover", 70)) {
            play(KuvaLichSounds.UI_GUIDE_HOVER.get(), 0.6f);
        }
    }

    /** 物品提示上按住 Ctrl 查阅，触发打开的那一刻 */
    static void lookup() {
        lastLookupAt = Util.getMillis();
        play(KuvaLichSounds.UI_GUIDE_LOOKUP.get(), 1f);
    }

    /** 通讯肖像开框那一刻的接通声（同一帧几条通讯一起进视野也只响一声） */
    static void comm(CommWho who) {
        if (!GuideDisplayConfig.commSound() || !KuvaSoundPlayer.gate("guide.comm", 400)) {
            return;
        }
        play(switch (who) {
            case LOTUS -> KuvaLichSounds.UI_COMM_LOTUS.get();
            case ORDIS -> KuvaLichSounds.UI_COMM_ORDIS.get();
            case LICH -> KuvaLichSounds.UI_COMM_LICH.get();
        }, 1f);
    }

    private void quickOpen() {
        if (GuideDisplayConfig.guideSounds()) {
            play(KuvaLichSounds.UI_GUIDE_OPEN_QUICK.get(), 1f);
        }
    }

    private void stopOpening() {
        if (power != null) {
            power.fadeOut(SKIP_FADE_MS);
            power = null;
        }
        if (resolve != null) {
            resolve.fadeOut(SKIP_FADE_MS);
            resolve = null;
        }
    }

    private static KuvaSoundInstance play(SoundEvent ev, float vol) {
        return KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, vol, 1f));
    }
}
