package pers.roinflam.kuvalich.client.fx.script;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;

/**
 * 玄骸 / 奴仆的挥砍声、扫击弧和命中声，跟着挥臂走
 *
 * <p>不挂 GeckoLib 的声音关键帧：GeckoLib 4 的动画（连同关键帧事件）在渲染时才推进，实体不在屏幕上时关键帧不触发，
 * 从背后砍过来的那一刀反而没声音。改成实体在客户端 tick 里自己查挥臂的第一 tick（{@code swinging && swingTime == 0}，
 * 服务端出手时发的挥臂包到了才成立），不依赖渲染，背后、视野外都照放。</p>
 *
 * <p>时刻都对着 {@code attack} 动画的出手关键帧 {@value #STRIKE_MS} ms：挥砍声的能量峰在文件 100 ms 处，
 * 提前 {@value #SWING_SOUND_MS} ms 起播刚好落在出手；扫击弧和命中声也在出手那一刻。
 * 改了 {@code kuva.animation.json} 里 attack 的出手帧，这里的常量要跟着改。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaAnimFx {

    /** 近战命中的实体事件字节：服务端 {@code doHurtTarget} 打中以后广播 */
    public static final byte EV_HIT = 103;

    /** attack 动画的出手关键帧（0.1333 s） */
    static final int STRIKE_MS = 133;
    /** 挥砍声提前多少毫秒起播（文件里的能量峰在 100 ms） */
    static final int SWING_SOUND_MS = 33;
    /** 命中包晚于挥臂多久以内还算「这一刀」（3 tick） */
    private static final long HIT_PAIR_MS = 150L;
    /** 扫击弧只画给多近的人看（格） */
    private static final double ARC_RANGE = 32.0D;

    /** 奴仆的扫击弧颜色：比赤毒猩红暗一档，和玄骸分开 */
    private static final int THRALL_RED = 0xC8364A;

    private static final RandomSource RANDOM = RandomSource.create();
    /** 每只实体最近一次挥臂的起点（导演钟） */
    private static final Int2LongOpenHashMap SWING_T0 = new Int2LongOpenHashMap();

    static {
        SWING_T0.defaultReturnValue(Long.MIN_VALUE);
    }

    private KuvaAnimFx() {
    }

    /**
     * 挥臂的第一 tick（实体客户端 tick 里检测到时调）：+33 ms 挥砍声，+133 ms 扫击弧。
     * 玄骸：脚下地面弧，从身体朝向左 90° 扫到右 90°，半径 3（等于横扫伤害的范围），弧上带几团暗红尘雾；
     * 奴仆：半径 2、细一些、没有尘雾，挥砍声小一些、尖一些
     */
    public static void onSwing(AbstractKuva e) {
        long now = KuvaFxDirector.clockMs();
        if (SWING_T0.size() > 128) {
            SWING_T0.values().removeIf(t -> now - t > 5_000L);
        }
        SWING_T0.put(e.getId(), now);

        Vec3 c = LichFx.chest(e);
        double d = KuvaFxDirector.camDist(c);
        if (d > 48.0D) {
            return;
        }
        boolean lich = e instanceof KuvaMasterEntity;
        float pitch = lich ? KuvaSoundPlayer.jitter(0.98F, 0.04F) : KuvaSoundPlayer.jitter(1.17F, 0.05F);
        float vol = lich ? 1.0F : 0.7F;
        // 奴仆的挥砍声音高高，起播点按音高缩一点（33 → 28 ms）
        int soundAt = lich ? SWING_SOUND_MS : KuvaSoundPlayer.scaleT(SWING_SOUND_MS, 1.17F);

        FxTimeline tl = KuvaFxDirector.start(lich ? "V20 lich swing" : "V20 thrall swing", c, FxTier.C).follow(e)
                .until(STRIKE_MS + 250);
        tl.at(soundAt, () -> KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_KUVA_SWING.get(),
                SoundSource.HOSTILE, LichFx.chest(e), vol, pitch).tier(FxTier.C)));
        if (d <= ARC_RANGE) {
            tl.at(STRIKE_MS, () -> sweepArc(e, lich));
        }
    }

    /** 出手那一刻的地面扫击弧 */
    private static void sweepArc(AbstractKuva e, boolean lich) {
        Vec3 feet = e.position().add(0.0D, 0.06D, 0.0D);
        Vec3 f = LichFx.facing(e);
        // 水平弧的 0° 是 +X、往 +Z 增大；朝向角 = atan2(z, x)，从左 90° 起扫 180°
        float facingDeg = (float) Math.toDegrees(Math.atan2(f.z, f.x));
        float radius = lich ? 3.0F : 2.0F;
        float a0 = lich ? 0.8F : 0.65F;
        // 100 ms 扫完，再 250 ms 淡出
        KuvaFxGeometry.arc(feet).normal(KuvaFxGeometry.Normal.UP).start(facingDeg - 90.0F)
                .sweep(180.0F, 100, FxCurve.OUT_CUBIC).radius(radius).thick(lich ? 0.25F : 0.15F)
                .color(lich ? FxPalette.KUVA : THRALL_RED)
                .alphaFn(ms -> ms < 100.0F ? a0 : a0 * FxCurve.fadeOut((ms - 100.0F) / 250.0F)).life(350).add();
        if (!lich) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(facingDeg - 90.0F + 180.0F * (i + 0.5F) / 6.0F);
            double r = radius * LichFx.rand(0.85D, 1.05D);
            KuvaParticles.mist(feet.x + Math.cos(a) * r, feet.y + 0.1D, feet.z + Math.sin(a) * r)
                    .vel(Math.cos(a) * 0.02D, 0.01D, Math.sin(a) * 0.02D).drag(0.93F)
                    .color(FxPalette.KUVA_MIST).alpha(0.3F).size(0.6F, 1.1F).life(14 + RANDOM.nextInt(6)).spin(0.02F).spawn();
        }
    }

    /**
     * 近战打中了（实体事件 {@link #EV_HIT}）：3 tick 内挥过臂就把命中声排到挥臂起点 + 133 ms（和扫击弧同一刻），
     * 已经过了那一刻或者没收到挥臂就当场放。服务端打中立刻放的话会比挥砍声的能量峰早 133 ms，听起来是「嘭……呼」
     */
    public static void onHit(AbstractKuva e) {
        long now = KuvaFxDirector.clockMs();
        long t0 = SWING_T0.get(e.getId());
        if (e.swinging && e.swingTime < 0) {
            // 服务端出手时挥臂包和命中事件是同一 tick 先后到的：命中先处理，客户端 tick 里的挥臂检测还没轮到
            // （swingTime 还是 −1），这一刀的起点就是现在
            t0 = now;
        }
        long delay = t0 != Long.MIN_VALUE && now - t0 <= HIT_PAIR_MS ? t0 + STRIKE_MS - now : 0L;
        boolean lich = e instanceof KuvaMasterEntity;
        Runnable hit = () -> KuvaSoundPlayer.play(KuvaSoundPlayer.at(
                lich ? KuvaLichSounds.ENTITY_LICH_STRIKE.get() : e instanceof KuvaLarvlingEntity
                        ? KuvaLichSounds.ENTITY_LARVLING_STRIKE.get() : KuvaLichSounds.ENTITY_THRALL_STRIKE.get(), SoundSource.HOSTILE,
                LichFx.chest(e), lich ? 1.2F : 1.0F,
                lich ? KuvaSoundPlayer.jitter(0.98F, 0.06F) : KuvaSoundPlayer.jitter(1.02F, 0.06F)).tier(FxTier.C));
        if (delay <= 0L) {
            hit.run();
            return;
        }
        KuvaFxDirector.start("V20 hit", LichFx.chest(e), FxTier.C).follow(e).at((int) delay, hit);
    }
}
