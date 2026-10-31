package pers.roinflam.kuvalich.client.fx.script;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaScreenFx;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;

/**
 * 安魂判定（没走怜悯之击的直接判定）、处决刀口、判错后的冲击和没收的世界演出
 *
 * <p>直接判定保留世界里的三角印记，但爆发收着做：白天过曝的根子是白色、粉白的加法层一层层叠上去
 * （大白闪芯、朝相机的大环、几十个粉白火花、击杀者整屏闪白），所以印记从粉白起、闪光和环都缩小、
 * 火花换成白热橙到赤毒、去掉整屏闪白。怜悯之击那一路的界面符号和三格由处决界面画，世界里只有刀口的小火花
 * （{@link #bladeSpark}）和收尾消散。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RequiemFx {

    private static final RandomSource RANDOM = RandomSource.create();

    /** 三枚印记离晶核的距离 */
    private static final double GLYPH_RADIUS = 1.4D;
    /** 印记的边长（格） */
    private static final float GLYPH_SIZE = 0.95F;
    /** 相邻两枚印记弹出的间隔 */
    private static final int GLYPH_STEP_MS = 140;
    /** 直接判定成功的爆发时刻（碎裂声的瞬态） */
    private static final int BURST_MS = 420;
    /** 判错之后多久起暖色冲击、玄骸化雾逃走 */
    private static final int IMPACT_AFTER_WRONG_MS = 1100;

    private RequiemFx() {
    }

    private static double rand(double lo, double hi) {
        return lo + RANDOM.nextDouble() * (hi - lo);
    }

    /**
     * 判定演出的取景：晶核放在身体表面朝看客的一侧，三枚印记在相机的右 / 上平面里排成正对看客的三角；
     * {@code cards} 是三格各自判的那张卡（包里 b 的 bit8~19，每格 4 位，15 = 不知道）
     */
    private record Frame(Entity lich, Vec3 chest, Vec3 feet, Vec3 core, Vec3[] glyph, int[] cards) {
    }

    /** 第 {@code n} 格判的卡 id：b 的 bit 8 + 4n 起 4 位，15 = 不知道（画中性占位） */
    private static int cardOf(int b, int n) {
        return (b >>> (8 + 4 * n)) & 15;
    }

    private static Frame frame(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        Entity lich = LichFx.entityOf(p);
        Vec3 chest = lich != null ? LichFx.chest(lich) : p.pos();
        if (KuvaFxDirector.camDist(chest) > 64.0D) {
            return null;
        }
        float width = lich != null ? lich.getBbWidth() : 1.0F;
        Vec3 feet = lich != null ? lich.position() : chest.subtract(0.0D, 1.3D, 0.0D);

        Camera cam = KuvaFxDirector.camera();
        Vec3 camPos = cam != null ? cam.getPosition() : chest.add(0.0D, 0.0D, 5.0D);
        Vec3 toCam = new Vec3(camPos.x - chest.x, 0.0D, camPos.z - chest.z);
        toCam = toCam.lengthSqr() < 1.0E-4D ? Vec3.ZERO : toCam.normalize();
        Vec3 core = chest.add(toCam.scale(width * 0.5D));

        // 90°、210°、330°：I 顶、II 左下、III 右下
        Vec3 right = cam != null ? new Vec3(cam.getLeftVector()).scale(-1.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = cam != null ? new Vec3(cam.getUpVector()) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3[] glyph = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(90.0D + 120.0D * i);
            glyph[i] = core.add(right.scale(Math.cos(a) * GLYPH_RADIUS)).add(up.scale(Math.sin(a) * GLYPH_RADIUS));
        }
        int[] cards = {cardOf(p.b, 0), cardOf(p.b, 1), cardOf(p.b, 2)};
        return new Frame(lich, chest, feet, core, glyph, cards);
    }

    /**
     * 第 {@code n} 枚印记弹出：画这一格判的那张卡的符号（深红填充 + 粉白线 + 红光，颜色在贴图里），
     * 1.4 → 1.0 回弹 90 ms，保持到 {@code holdUntilMs} 后淡出；脚下一小团红晕。寿命按「保持段占 60%」从保持时长反推
     */
    private static void popGlyph(Frame f, int n, int startMs, int holdUntilMs, boolean lockSound) {
        Vec3 g = f.glyph()[n];
        int life = Math.max(2, Math.round((holdUntilMs - startMs) / FxCurve.HOLD_END / 50.0F));
        KuvaParticles.glyph(f.cards()[n], KuvaParticles.GlyphKind.LIT, g.x, g.y, g.z)
                .color(FxPalette.WHITE).color2(FxPalette.KUVA_PALE).alpha(1.0F)
                .size(GLYPH_SIZE * 1.4F, GLYPH_SIZE).sizeCurve(FxCurve.POP)
                .alphaCurve(FxCurve.HOLD_THEN_FADE).life(life).core().spawn();
        KuvaParticles.glow(g.x, g.y, g.z).color(FxPalette.KUVA).alpha(0.5F).size(0.3F, 0.42F).life(6).spawn();
        if (lockSound) {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.glyphLock(n), SoundSource.PLAYERS, g, 1.2F, 1.0F)
                    .tier(FxTier.S));
        }
    }

    // ==================== V04 安魂处决成功（直接判定） ====================

    /**
     * 预兆（0–420 ms）：晶核按赤毒心跳跳两拍；三枚印记依次弹出、各自一条连线描向晶核，360 ms 连线绷紧。
     * 爆发（420 ms，碎裂声的瞬态）：晶核小闪光、朝相机的环和地面环、碎晶 / 火花 / 液滴，连线断开回缩；
     * 击杀者只压一圈暗红暗角（不闪白）。余韵：印记化成光点飘散，玄骸本体同时晶化，从爆发起 1 s 化雾散掉。
     *
     * <p>收尾消散和预兆同时起：晶化段正好 420 ms、碎裂由爆发本身代替，模型在 1.42 s 淡完，
     * 服务端判定后把实体留 32 tick 就够。</p>
     *
     * <p>b 的标志位：bit0 怜悯之击后（那一路不再发这个包，收到也不画）；bit1 有没收物品要归还
     * （600 ms 一团雾把东西「吐」出来）；bit2~3 这次用完消失的安魂卡张数（1100 ms 起给击杀者逐张一声）；
     * bit8~19 三格各自的卡 id（每格 4 位，15 = 不知道），三枚印记画各自那张卡的符号。</p>
     */
    public static void success(KuvaFxPacket p) {
        if ((p.b & 1) != 0) {
            return;
        }
        Frame f = frame(p);
        if (f == null) {
            return;
        }
        int killerId = p.a - 1;
        boolean returnItems = (p.b & 2) != 0;
        int wornCards = (p.b >> 2) & 3;
        Vec3 core = f.core();
        Vec3 chest = f.chest();
        Vec3 feet = f.feet();
        Vec3[] glyph = f.glyph();

        FxTimeline tl = KuvaFxDirector.start("V04 requiem success", core, FxTier.S).until(BURST_MS + 1600);

        if (f.lich() != null) {
            LichFx.Spec s = LichFx.spec(LichFx.Kind.REQUIEM, f.lich());
            s.crystalMs = BURST_MS;
            s.shatterR = 0.0F;
            s.fadeMs = 1000;
            s.sound = null;
            s.mist = 10;
            s.motes = 12;
            LichFx.play(f.lich(), s);
        }

        // ---------- 预兆 ----------
        tl.at(0, () -> {
            // 心跳两拍（0–360），360 起 60 ms 涨满「绷紧」，爆发后 100 ms 交给闪光
            KuvaFxGeometry.glow(core).color(FxPalette.KUVA)
                    .sizeFn(ms -> ms < 360.0F ? 0.5F : 0.5F + 0.4F * FxCurve.easeOutCubic(FxCurve.clamp01((ms - 360.0F) / 60.0F)))
                    .alphaFn(ms -> {
                        if (ms < 360.0F) {
                            return 0.25F + 0.65F * FxCurve.heartbeat(ms / 400.0F, 400.0F);
                        }
                        if (ms < 420.0F) {
                            return 0.9F;
                        }
                        return 0.9F * FxCurve.fadeOut((ms - 420.0F) / 100.0F);
                    })
                    .life(520).core().add();

            KuvaFxGeometry.tether(core, glyph[0], glyph[1], glyph[2])
                    .draw(0, 0, 80).draw(1, GLYPH_STEP_MS, 80).draw(2, GLYPH_STEP_MS * 2, 80)
                    .boost(360, 1.6F, 60).snap(-1, BURST_MS)
                    .width(0.035F).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).alpha(0.7F, FxCurve.HOLD_THEN_FADE)
                    .life(1100).core().add();
        });
        for (int i = 0; i < 3; i++) {
            final int n = i;
            tl.at(GLYPH_STEP_MS * n, () -> popGlyph(f, n, GLYPH_STEP_MS * n, BURST_MS + 60, true));
        }

        // ---------- 爆发 ----------
        tl.at(BURST_MS, () -> {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_SHATTER.get(), SoundSource.PLAYERS, core, 1.0F,
                    KuvaSoundPlayer.jitter(1.0F, 0.02F)).tier(FxTier.S));

            KuvaFxGeometry.flash(core).radius(1.5F, 70).core(FxPalette.KUVA_PALE).color(FxPalette.KUVA)
                    .alpha(0.7F, FxCurve.FADE_SQ).life(260).core().add();
            KuvaFxGeometry.ring(core).normal(KuvaFxGeometry.Normal.CAMERA).radius(0.3F, 2.4F, FxCurve.OUT_QUART)
                    .thick(0.12F, 0.02F).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).alpha(0.7F, FxCurve.FADE_SQ)
                    .life(260).core().add();
            KuvaFxGeometry.ring(feet.add(0.0D, 0.05D, 0.0D)).normal(KuvaFxGeometry.Normal.UP)
                    .radius(0.5F, 3.5F, FxCurve.OUT_QUART).thick(0.15F, 0.02F)
                    .color(FxPalette.KUVA).alpha(0.5F, FxCurve.FADE_SQ).life(400).add();
            KuvaParticles.flare(core.x, core.y, core.z).size(2.5F, 1.7F).sizeCurve(FxCurve.OUT_CUBIC)
                    .color(FxPalette.KUVA_HOT).alpha(0.6F).life(6).spawn();

            // 碎晶：带重力和自旋，往四周、略向上崩
            for (int i = 0; i < 9; i++) {
                Vec3 v = LichFx.randomDir(0.35D).scale(rand(0.12D, 0.3D)).add(0.0D, 0.08D, 0.0D);
                float s = (float) rand(0.25D, 0.45D);
                KuvaParticles.shard(core.x, core.y, core.z).vel(v.x, v.y, v.z).gravity(0.05F).drag(0.96F)
                        .spin((float) rand(-0.3D, 0.3D)).color(FxPalette.KUVA).color2(FxPalette.KUVA_DEEP)
                        .alpha(0.95F).size(s, s * 0.8F).life(14 + RANDOM.nextInt(11)).spawn();
            }
            // 火花：白热橙到赤毒，快、细、阻力大
            for (int i = 0; i < 12; i++) {
                Vec3 v = LichFx.randomDir(0.0D).scale(rand(0.3D, 0.55D));
                KuvaParticles.spark(core.x, core.y, core.z).vel(v.x, v.y, v.z).drag(0.82F).gravity(0.02F)
                        .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).alpha(1.0F)
                        .size((float) rand(0.5D, 0.85D), 0.14F).orient(KuvaParticles.Orient.VELOCITY)
                        .life(6 + RANDOM.nextInt(5)).spawn();
            }
            // 赤毒液滴
            for (int i = 0; i < 8; i++) {
                Vec3 v = LichFx.randomDir(0.5D).scale(rand(0.08D, 0.2D)).add(0.0D, 0.1D, 0.0D);
                KuvaParticles.drop(core.x, core.y, core.z).vel(v.x, v.y, v.z).gravity(0.06F).drag(0.98F)
                        .color(FxPalette.KUVA_DEEP).alpha(0.95F).size(0.12F, 0.12F)
                        .orient(KuvaParticles.Orient.VELOCITY).alphaCurve(FxCurve.HOLD_THEN_FADE)
                        .life(14 + RANDOM.nextInt(7)).spawn();
            }
            tl.onlyFor(killerId, () -> KuvaScreenFx.vignette(FxPalette.KUVA_DEEP, 0.18F, 0, 0, 900, FxTier.S));
        });

        // ---------- 余韵 ----------
        // 每枚印记化成 3 个光点向上外飘
        for (int i = 0; i < 3; i++) {
            final int n = i;
            tl.at(BURST_MS + 40 * n, () -> {
                Vec3 g = glyph[n];
                Vec3 out = g.subtract(core).normalize();
                for (int k = 0; k < 3; k++) {
                    KuvaParticles.mote(g.x + rand(-0.25D, 0.25D), g.y + rand(-0.3D, 0.3D), g.z + rand(-0.25D, 0.25D))
                            .vel(out.x * 0.03D + rand(-0.01D, 0.01D), rand(0.03D, 0.06D), out.z * 0.03D + rand(-0.01D, 0.01D))
                            .drag(0.96F).color(FxPalette.KUVA).size(0.1F, 0.1F)
                            .life(14 + RANDOM.nextInt(11)).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
                }
            });
        }
        if (f.lich() == null) {
            // 没有实体可消散（开发命令没指着玄骸）：照样在胸口位置散一团雾、升几个光点
            tl.every(BURST_MS + 30, BURST_MS + 330, 60, t -> {
                for (int k = 0; k < 2; k++) {
                    KuvaParticles.mist(chest.x + rand(-0.6D, 0.6D), chest.y + rand(-0.9D, 0.3D), chest.z + rand(-0.6D, 0.6D))
                            .vel(rand(-0.01D, 0.01D), rand(0.015D, 0.03D), rand(-0.01D, 0.01D)).drag(0.98F)
                            .color(FxPalette.KUVA_MIST).color2(FxPalette.KUVA_DEEP).alpha(0.5F).size(1.2F, 2.4F)
                            .life(40 + RANDOM.nextInt(21)).spin(RANDOM.nextBoolean() ? 0.02F : -0.02F).spawn();
                }
                KuvaParticles.mote(chest.x + rand(-0.7D, 0.7D), feet.y + rand(0.2D, 2.6D), chest.z + rand(-0.7D, 0.7D))
                        .vel(0.0D, rand(0.03D, 0.06D), 0.0D).drag(0.98F).color(FxPalette.KUVA).size(0.09F, 0.09F)
                        .life(20 + RANDOM.nextInt(21)).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
            });
        }

        // ---------- 归还没收物品（服务端在同一刻把东西吐出来；奖励这一项照用金色） ----------
        if (returnItems) {
            tl.at(BURST_MS + 180, () -> {
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_RETURN.get(), SoundSource.PLAYERS, chest, 1.0F, 1.0F)
                        .tier(FxTier.S));
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2.0D * i / 6.0D;
                    KuvaParticles.mist(chest.x, chest.y - 0.4D, chest.z)
                            .vel(Math.cos(a) * 0.09D, 0.01D, Math.sin(a) * 0.09D).drag(0.88F)
                            .color(FxPalette.KUVA_MIST).alpha(0.55F).size(0.8F, 1.8F).life(30).spin(0.02F).spawn();
                }
                for (int i = 0; i < 4; i++) {
                    Vec3 v = LichFx.randomDir(0.4D).scale(0.06D);
                    KuvaParticles.mote(chest.x, chest.y - 0.4D, chest.z).vel(v.x, Math.abs(v.y) + 0.03D, v.z).drag(0.95F)
                            .color(FxPalette.KUVA).color2(FxPalette.MURMUR).size(0.1F, 0.1F).life(24)
                            .alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
                }
            });
        }

        // ---------- 安魂卡用完：给击杀者逐张一声 ----------
        for (int i = 0; i < wornCards; i++) {
            tl.at(BURST_MS + 680 + 80 * i, () -> tl.onlyFor(killerId, () ->
                    KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.REQUIEM_CARD_WEAR.get(), 0.8F, 1.0F))));
        }
    }

    // ==================== V05 安魂处决失败（直接判定） ====================

    /**
     * 猜对的前 c 枚照成功那样依次锁定；判错的那一枚（t_r = 140·c）先照常弹出 70 ms，再换成同一张卡符号的红色霓虹 + 斜杠，外加一团红晕，
     * 不闪、不碎、不灰，后面的位置什么都不画。t_r + 70 判错声，t_r + 300 玄骸原地踉跄、挣脱的隆隆声，
     * t_r + 1100 暖色冲击（画面变亮、不加暗角），玄骸化雾逃走。
     *
     * <p>最晚的情况（猜对两枚）模型在 1.78 s 淡完，服务端判定后要把实体留 36 tick 以上。</p>
     *
     * <p>b：bit0~1 猜对的前几位（0~2），bit2 怜悯之击后（那一路不再发这个包，收到也不画），
     * bit8~19 三格各自的卡 id（每格 4 位，15 = 不知道）。a = 击杀者 id + 1。</p>
     */
    public static void fail(KuvaFxPacket p) {
        if ((p.b & 4) != 0) {
            return;
        }
        Frame f = frame(p);
        if (f == null) {
            return;
        }
        int c = Math.min(2, p.b & 3);
        int tr = GLYPH_STEP_MS * c;
        int impact = tr + IMPACT_AFTER_WRONG_MS;
        Vec3 core = f.core();
        Vec3[] glyph = f.glyph();
        FxTimeline tl = KuvaFxDirector.start("V05 requiem fail", core, FxTier.S).until(impact + 600);

        tl.at(0, () -> {
            // 晶核照常跳心跳，判错那一刻起慢慢暗下去
            KuvaFxGeometry.glow(core).color(FxPalette.KUVA).size(0.5F, 0.5F, FxCurve.LINEAR)
                    .alphaFn(ms -> ms < tr + 70.0F ? 0.25F + 0.65F * FxCurve.heartbeat(ms / 400.0F, 400.0F)
                            : 0.4F * FxCurve.fadeOut((ms - tr - 70.0F) / 400.0F))
                    .life(tr + 470).core().add();
            if (c > 0) {
                Vec3[] ok = new Vec3[c];
                System.arraycopy(glyph, 0, ok, 0, c);
                KuvaFxGeometry.Tether t = KuvaFxGeometry.tether(core, ok).width(0.035F)
                        .color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).alpha(0.7F, FxCurve.HOLD_THEN_FADE)
                        .life(Math.round(impact / FxCurve.HOLD_END)).core();
                for (int i = 0; i < c; i++) {
                    t.draw(i, GLYPH_STEP_MS * i, 80);
                }
                t.add();
            }
        });
        for (int i = 0; i < c; i++) {
            final int n = i;
            tl.at(GLYPH_STEP_MS * n, () -> popGlyph(f, n, GLYPH_STEP_MS * n, impact, true));
        }
        // 判错的那一枚：先和对的一样弹出 70 ms
        tl.at(tr, () -> popGlyph(f, c, tr, tr + 70, false));
        tl.at(tr + 70, () -> {
            Vec3 g = glyph[c];
            int life = Math.max(4, Math.round((impact - tr - 70) / FxCurve.HOLD_END / 50.0F));
            KuvaParticles.glyph(f.cards()[c], KuvaParticles.GlyphKind.WRONG, g.x, g.y, g.z).color(FxPalette.WHITE).alpha(1.0F)
                    .size(GLYPH_SIZE, GLYPH_SIZE).alphaCurve(FxCurve.HOLD_THEN_FADE).life(life).core().spawn();
            KuvaParticles.glow(g.x, g.y, g.z).color(FxPalette.KUVA).alpha(0.7F).size(0.6F, 0.6F)
                    .alphaCurve(FxCurve.HOLD_THEN_FADE).life(life).spawn();
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_GLYPH_REJECT.get(), SoundSource.PLAYERS, g, 1.2F, 1.0F)
                    .tier(FxTier.S));
        });
        tl.at(tr + 300, () -> {
            Vec3 c0 = f.lich() != null ? LichFx.chest(f.lich()) : f.chest();
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.REQUIEM_TAUNT.get(), SoundSource.HOSTILE, c0, 1.0F,
                    KuvaSoundPlayer.jitter(1.0F, 0.02F)).tier(FxTier.S));
            if (f.lich() != null) {
                LichFx.stagger(f.lich().getId(), 600);
            }
        });
        tl.at(impact, () -> {
            if (f.lich() != null) {
                escape(f.lich(), FxPalette.KUVA_HOT);
                LichFx.dissolve(f.lich(), LichFx.Kind.ESCAPE);
            } else {
                impactAt(f.chest(), f.feet(), FxPalette.KUVA_HOT);
            }
        });
    }

    /**
     * 判错后玄骸挣脱逃走时胸口的暖色冲击：闪光（芯是给的颜色，160 ms）、地面环、24 团雾往外推、
     * 三缕慢悠悠飘走的魂火花，同时放赤毒雾瞬移那一声。不加暗角——这一下画面是变亮的。
     * 玄骸本体的化雾由调用方另调 {@link LichFx#dissolve}（{@link LichFx.Kind#ESCAPE}）
     *
     * @param rgb 冲击的颜色 {@code 0xRRGGBB}
     */
    public static void escape(Entity entity, int rgb) {
        if (entity == null) {
            return;
        }
        impactAt(LichFx.chest(entity), entity.position(), rgb);
    }

    private static void impactAt(Vec3 c, Vec3 feet, int rgb) {
        double d = KuvaFxDirector.camDist(c);
        if (d > 64.0D) {
            return;
        }
        int edge = rgb == FxPalette.KUVA_HOT ? FxPalette.KUVA : FxPalette.KUVA_HOT;
        KuvaFxDirector.start("V05 escape impact", c, FxTier.S).until(400).at(0, () -> {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.MERCY_ESCAPE.get(), SoundSource.HOSTILE, c, 1.0F, 1.0F)
                    .tier(FxTier.S));
            KuvaFxGeometry.flash(c).radius(1.4F, 50).core(rgb).color(edge).alpha(0.8F, FxCurve.FADE_SQ).life(160).core().add();
            KuvaFxGeometry.ring(feet.add(0.0D, 0.05D, 0.0D)).normal(KuvaFxGeometry.Normal.UP)
                    .radius(0.5F, 3.0F, FxCurve.OUT_QUART).thick(0.14F, 0.02F)
                    .color(FxPalette.KUVA_HOT).alpha(0.6F, FxCurve.FADE_SQ).life(380).add();
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2.0D * i / 24.0D + rand(-0.12D, 0.12D);
                KuvaParticles.mist(c.x + Math.cos(a) * 0.3D, c.y + rand(-0.9D, 0.4D), c.z + Math.sin(a) * 0.3D)
                        .vel(Math.cos(a) * 0.08D, 0.05D * rand(0.5D, 1.0D), Math.sin(a) * 0.08D).drag(0.94F)
                        .color(FxPalette.KUVA_MIST).alpha(0.5F).size(1.0F, 2.2F).life(40 + RANDOM.nextInt(21))
                        .spin(RANDOM.nextBoolean() ? 0.02F : -0.02F).spawn();
            }
            // 三缕「魂」：慢速火花向远处上方飘走
            for (int i = 0; i < 3; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0D;
                KuvaParticles.spark(c.x, c.y, c.z).vel(Math.cos(a) * 0.1D, 0.11D, Math.sin(a) * 0.1D).drag(0.98F)
                        .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).alpha(0.9F).size(0.5F, 0.2F)
                        .orient(KuvaParticles.Orient.VELOCITY).alphaCurve(FxCurve.HOLD_THEN_FADE).life(30).spawn();
            }
        });
    }

    // ==================== 怜悯之击的刀口 ====================

    /**
     * 怜悯之击每一刀刺入时刀口上的小火花（胸前 0.4 格）：一个小闪光 + 4 个火花，旁观者也看得到；
     * 收尾那一下大一点（r 0.6）再带 12 片碎光。不要整只染成粉白的大白闪
     *
     * @param stab 第几刀（1 起）
     * @param last 是不是收尾那一下
     */
    public static void bladeSpark(Entity entity, int stab, boolean last) {
        if (entity == null) {
            return;
        }
        Vec3 tip = LichFx.chest(entity).add(LichFx.facing(entity).scale(0.4D));
        if (KuvaFxDirector.camDist(tip) > 48.0D) {
            return;
        }
        KuvaFxDirector.start(last ? "mercy blade spark (last)" : "mercy blade spark " + stab, tip, FxTier.A).at(0, () -> {
            float r = last ? 0.6F : 0.4F;
            KuvaFxGeometry.flash(tip).radius(r, 40).core(FxPalette.KUVA_HOT).color(FxPalette.KUVA)
                    .alpha(0.9F, FxCurve.FADE_SQ).life(last ? 160 : 120).core().add();
            for (int i = 0; i < 4; i++) {
                Vec3 v = LichFx.randomDir(0.2D).scale(rand(0.18D, 0.32D));
                KuvaParticles.spark(tip.x, tip.y, tip.z).vel(v.x, v.y, v.z).drag(0.8F).gravity(0.02F)
                        .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).size(0.4F, 0.1F)
                        .orient(KuvaParticles.Orient.VELOCITY).life(4 + RANDOM.nextInt(4)).spawn();
            }
            if (last) {
                for (int i = 0; i < 12; i++) {
                    Vec3 v = LichFx.randomDir(0.3D).scale(rand(0.08D, 0.22D));
                    float s = (float) rand(0.1D, 0.18D);
                    KuvaParticles.shard(tip.x, tip.y, tip.z).vel(v.x, v.y, v.z).gravity(0.03F).drag(0.94F)
                            .spin((float) rand(-0.3D, 0.3D)).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA)
                            .alpha(0.9F).size(s, s * 0.7F).life(8 + RANDOM.nextInt(7)).spawn();
                }
            }
        });
    }

    // ==================== V22 没收 ====================

    /**
     * 物品被玄骸没收（a = 被没收的玩家 id + 1，b = 物品稀有度）：物品处的光点螺旋着收进去，250 ms 一下小光晕、
     * 几粒碎晶，光点往上逸散；被没收的本人屏幕边缘压一圈暗红。没收声的瞬态在文件 250 ms 处，所以 0 ms 起播。
     * 嘲讽台词走通讯窗，接通声由通讯窗开框时自己放
     */
    public static void confiscate(KuvaFxPacket p) {
        Vec3 o = p.pos();
        if (KuvaFxDirector.camDist(o) > 24.0D) {
            return;
        }
        int victimId = p.a - 1;
        FxTimeline tl = KuvaFxDirector.start("V22 confiscate", o, FxTier.A).until(900);
        tl.at(0, () -> {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_LICH_CONFISCATE.get(), SoundSource.PLAYERS, o, 1.0F, 1.0F)
                    .tier(FxTier.A));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0D * i / 8.0D;
                double r = 0.8D;
                // 切向 + 向心：螺旋着 250 ms 收进去
                KuvaParticles.mote(o.x + Math.cos(a) * r, o.y + rand(-0.1D, 0.3D), o.z + Math.sin(a) * r)
                        .vel(-Math.cos(a) * 0.15D - Math.sin(a) * 0.08D, 0.0D, -Math.sin(a) * 0.15D + Math.cos(a) * 0.08D)
                        .drag(0.85F).color(FxPalette.KUVA).size(0.08F, 0.06F).life(5).spawn();
            }
            tl.onlyFor(victimId, () -> KuvaScreenFx.vignette(FxPalette.KUVA_DEEP, 0.2F, 120, 200, 600, FxTier.A));
        });
        tl.at(250, () -> {
            KuvaParticles.flare(o.x, o.y + 0.2D, o.z).size(1.2F, 0.8F).sizeCurve(FxCurve.OUT_CUBIC)
                    .color(FxPalette.KUVA_HOT).alpha(0.6F).life(5).spawn();
            for (int i = 0; i < 3; i++) {
                Vec3 v = LichFx.randomDir(0.5D).scale(rand(0.06D, 0.12D));
                KuvaParticles.shard(o.x, o.y + 0.2D, o.z).vel(v.x, v.y, v.z).gravity(0.04F).drag(0.95F)
                        .spin((float) rand(-0.3D, 0.3D)).color(FxPalette.KUVA).color2(FxPalette.KUVA_DEEP)
                        .size(0.18F, 0.14F).life(10 + RANDOM.nextInt(6)).spawn();
            }
            for (int i = 0; i < 5; i++) {
                KuvaParticles.mote(o.x + rand(-0.15D, 0.15D), o.y + 0.2D, o.z + rand(-0.15D, 0.15D))
                        .vel(rand(-0.01D, 0.01D), rand(0.08D, 0.12D), rand(-0.01D, 0.01D)).drag(0.93F)
                        .color(FxPalette.KUVA).size(0.08F, 0.08F).life(14 + RANDOM.nextInt(6))
                        .alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
            }
        });
    }

    /** 谜语解全：演出全在解密 HUD 里（滑入、三格一起揭示），世界里不加东西 */
    public static void riddleSolved(KuvaFxPacket p) {
    }
}
