package pers.roinflam.kuvalich.client.fx.script;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 玄骸 / 奴仆的现身、到场、离场、死亡与消散演出
 *
 * <p>坐标约定：chest = 脚底 + 0.62 × 身高，head = + 0.9 × 身高，R = 碰撞箱宽。</p>
 *
 * <p>世界里的画面一律收着做：玄骸到场靠 Boss 条和通讯窗交代，世界里只留胸口一点光；所有「离开」都走同一套
 * 「赤毒晶化 → 碎裂 → 雾散」（{@link #dissolve}），只是各段长短不同。模型的淡入淡出交给 {@link LichRenderState}，
 * 发光层的提亮交给 {@link KuvaGlow}，这里只管粒子、几何和声音，再把节奏告诉那两处。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class LichFx {

    private static final RandomSource RANDOM = RandomSource.create();

    /** 奴仆被盯上的刺音：120 s 只放一次，玄骸刚警示过的 10 s 内也不放（刷奴仆时不能一直响） */
    private static final int THRALL_SOUND_GATE_MS = 120_000;
    private static final int LICH_ALERT_QUIET_MS = 10_000;

    /** 密语流完整版每秒最多几次（一刀一只连着处决时，其余只出两个火花） */
    private static final int MURMUR_FULL_PER_SEC = 6;

    private static long lastLichAlert = Long.MIN_VALUE / 2;
    private static long lastThrallSound = Long.MIN_VALUE / 2;
    private static long murmurWindowStart;
    private static int murmurWindowCount;

    /** 正在演、或刚演完的消散，按实体 id 记；用来挡掉重复的消散、让死亡白烟的兜底让位 */
    private static final Int2ObjectOpenHashMap<Dissolving> DISSOLVING = new Int2ObjectOpenHashMap<>();
    /** 消散记录留多久（死亡白烟在死后 1 s 才到，留 10 s 足够） */
    private static final long DISSOLVE_MEMORY_MS = 10_000L;
    /** 正在原地踉跄的实体：id → 踉跄到哪一刻（导演钟） */
    private static final Int2LongOpenHashMap STAGGER_UNTIL = new Int2LongOpenHashMap();

    private LichFx() {
    }

    // ==================== 坐标 ====================

    static Vec3 chest(Entity e) {
        return e.position().add(0.0D, e.getBbHeight() * 0.62D, 0.0D);
    }

    static Vec3 head(Entity e) {
        return e.position().add(0.0D, e.getBbHeight() * 0.9D, 0.0D);
    }

    static double rand(double lo, double hi) {
        return lo + RANDOM.nextDouble() * (hi - lo);
    }

    /** 按倍数缩粒子数，至少留 1 个 */
    private static int scaled(int n, float k) {
        return Math.max(1, Math.round(n * k));
    }

    static Entity entityOf(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
    }

    // ==================== V03 召唤：雾中现身 ====================

    /**
     * 安魂通牒召来玄骸（a = 召唤者 id + 1）：雾从四周往脚下收，模型 600 ms 里从雾中显形，450 ms 一声，
     * 显形后雾往外慢慢散，胸口跳两下。不画光柱、闪光、屏闪，也没有刺音。
     *
     * <p>生成和这个包是同一 tick 发的，包常常比实体先到，所以淡入按实体 id 交给渲染状态，实体晚到也接得上；
     * 没有实体（开发命令没指着东西）时以包的位置为脚底照样演。</p>
     */
    public static void summon(KuvaFxPacket p) {
        Entity e = entityOf(p);
        int id = p.entityId();
        Vec3 feet = e != null ? e.position() : p.pos();
        if (KuvaFxDirector.camDist(feet) > 64.0D) {
            return;
        }
        if (id >= 0) {
            LichRenderState.hide(id);
            LichRenderState.fadeIn(id, 600);
        }
        FxTimeline tl = KuvaFxDirector.start("V03 lich summon", feet.add(0.0D, 1.2D, 0.0D), FxTier.S).until(1800);
        if (e != null) {
            tl.follow(e);
        }

        tl.at(0, () -> {
            // 半径 2 的圆周、0~2.5 格高处 16 团雾往中心收，600 ms 收到脚边半格左右
            // （起速 0.15、阻力 0.96，12 tick 走约 1.4 格；起速太慢的话雾还没收拢就散了）。
            // 透明度先保持再淡：默认的平方淡出到第 8 tick 只剩一成，看上去是雾半路没了，不是收进脚下
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2.0D * i / 16.0D + rand(-0.15D, 0.15D);
                double h = rand(0.0D, 2.5D);
                KuvaParticles.mist(feet.x + Math.cos(a) * 2.0D, feet.y + h, feet.z + Math.sin(a) * 2.0D)
                        .vel(-Math.cos(a) * 0.15D, (1.0D - h) * 0.012D, -Math.sin(a) * 0.15D).drag(0.96F)
                        .color(FxPalette.KUVA_MIST).alpha(0.5F).size(0.8F, 1.6F).life(14 + RANDOM.nextInt(3))
                        .alphaCurve(FxCurve.HOLD_THEN_FADE).spin(RANDOM.nextBoolean() ? 0.02F : -0.02F).spawn();
            }
            for (int i = 0; i < 6; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0D;
                double r = rand(0.3D, 1.2D);
                KuvaParticles.mote(feet.x + Math.cos(a) * r, feet.y + rand(0.1D, 0.6D), feet.z + Math.sin(a) * r)
                        .vel(0.0D, rand(0.03D, 0.05D), 0.0D).drag(0.97F).color(FxPalette.KUVA).size(0.08F, 0.08F)
                        .life(24 + RANDOM.nextInt(10)).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
            }
        });

        tl.at(450, () -> {
            Vec3 c = e != null ? chest(e) : feet.add(0.0D, 1.3D, 0.0D);
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_LICH_SUMMON.get(), SoundSource.HOSTILE, c, 1.0F, 1.0F)
                    .tier(FxTier.S));
        });

        // 显形之后：剩下的雾从脚下往外慢慢散开
        tl.at(600, () -> {
            Vec3 f = e != null ? e.position() : feet;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0D * i / 8.0D + rand(-0.3D, 0.3D);
                KuvaParticles.mist(f.x + Math.cos(a) * 0.4D, f.y + rand(0.1D, 1.6D), f.z + Math.sin(a) * 0.4D)
                        .vel(Math.cos(a) * 0.03D, 0.006D, Math.sin(a) * 0.03D).drag(0.96F)
                        .color(FxPalette.KUVA_MIST).alpha(0.45F).size(1.2F, 2.0F).life(24 + RANDOM.nextInt(8))
                        .spin(RANDOM.nextBoolean() ? 0.015F : -0.015F).spawn();
            }
        });

        // 胸口两下脉动：发光层提亮，外加一团不大的胸口光（发光层没装上时也看得到心跳）
        for (int t : new int[]{600, 900}) {
            tl.at(t, () -> {
                if (id >= 0) {
                    KuvaGlow.boost(id, 1.5F, 200);
                }
                Vec3 c = e != null ? chest(e) : feet.add(0.0D, 1.3D, 0.0D);
                KuvaFxGeometry.Glow g = KuvaFxGeometry.glow(c).color(FxPalette.KUVA)
                        .sizeFn(ms -> 0.4F + 0.15F * FxCurve.easeOutCubic(FxCurve.clamp01(ms / 80.0F)))
                        .alphaFn(ms -> ms < 60.0F ? 0.35F * ms / 60.0F : 0.35F * FxCurve.fadeOut((ms - 60.0F) / 160.0F))
                        .life(220);
                if (e != null) {
                    g.follow(e, 0.62F);
                }
                g.add();
            });
        }
    }

    // ==================== 狩猎到场 / 重定位 ====================

    /**
     * 有主玄骸找上门（a = 0）或卡住后换位重新出现（a = 1）：脚边 8 团雾原地慢慢往外扩，模型 400 ms 淡入；
     * 到场那次胸口亮一下、发光层 400 ms 内提到 ×1.75。无声：到场的「声音」是通讯窗。
     */
    public static void arrive(KuvaFxPacket p) {
        Entity e = entityOf(p);
        int id = p.entityId();
        Vec3 feet = e != null ? e.position() : p.pos();
        if (KuvaFxDirector.camDist(feet) > 64.0D) {
            return;
        }
        if (id >= 0) {
            // 换位是同一只实体先演离场（a = 99）再在新位置到场：离场留下的消散记录和发光模式在这里清掉，
            // 不然它在新位置一直按「消散中」画，之后真死了也会被当成重复消散
            DISSOLVING.remove(id);
            KuvaGlow.mode(id, KuvaGlow.Mode.NORMAL);
            LichRenderState.hide(id);
            LichRenderState.fadeIn(id, 400);
        }
        boolean fresh = p.a == 0;
        FxTimeline tl = KuvaFxDirector.start("lich arrive", feet.add(0.0D, 1.2D, 0.0D), FxTier.A).until(400);
        if (e != null) {
            tl.follow(e);
        }
        tl.at(0, () -> {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0D * i / 8.0D + rand(-0.3D, 0.3D);
                double r = rand(0.2D, 0.6D);
                KuvaParticles.mist(feet.x + Math.cos(a) * r, feet.y + rand(0.05D, 0.5D), feet.z + Math.sin(a) * r)
                        .vel(Math.cos(a) * 0.012D, 0.004D, Math.sin(a) * 0.012D).drag(0.98F)
                        .color(FxPalette.KUVA_MIST).alpha(0.45F).size(1.0F, 1.8F).life(30)
                        .spin(RANDOM.nextBoolean() ? 0.012F : -0.012F).spawn();
            }
            if (fresh) {
                if (id >= 0) {
                    KuvaGlow.boost(id, 1.75F, 400);
                }
                chestGlow(e, e != null ? chest(e) : feet.add(0.0D, 1.3D, 0.0D));
            }
        });
    }

    // ==================== 离场：赤毒雾瞬移 ====================

    /**
     * 离场（a = 遭遇结束原因的序号；99 = 卡住后换位，98 = 无主玄骸自然离开），画面都一样：
     * 胸口光往里收，模型 350 ms 淡出，身上 16 团雾原地卷起再散。服务端发包后实体只留 8 tick。
     */
    public static void depart(KuvaFxPacket p) {
        Entity e = entityOf(p);
        if (e != null) {
            dissolve(e, Kind.DEPART);
            return;
        }
        // 实体已经不在这个客户端上了：在包的位置放一小团雾和声音，不记消散
        Vec3 c = p.pos();
        double d = KuvaFxDirector.camDist(c);
        if (d > 64.0D) {
            return;
        }
        KuvaFxDirector.start("lich depart (no entity)", c, FxTier.A).at(0, () -> {
            departSound(c, d);
            for (int i = 0; i < 8; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0D;
                KuvaParticles.mist(c.x + Math.cos(a) * 0.3D, c.y + rand(-0.9D, 0.4D), c.z + Math.sin(a) * 0.3D)
                        .vel(Math.cos(a) * 0.04D, rand(0.0D, 0.015D), Math.sin(a) * 0.04D).drag(0.93F)
                        .color(FxPalette.KUVA_MIST).alpha(0.55F).size(1.0F, 2.0F).life(18 + RANDOM.nextInt(9))
                        .spin(RANDOM.nextBoolean() ? 0.03F : -0.03F).spawn();
            }
        });
    }

    private static void departSound(Vec3 c, double d) {
        float vol = 0.6F * KuvaSoundPlayer.distFalloff(d, 16.0D, 48.0D, 0.35F);
        KuvaSoundPlayer.play(KuvaSoundPlayer.atFixed(KuvaLichSounds.MERCY_ESCAPE.get(), SoundSource.HOSTILE, c, vol, 1.0F)
                .tier(FxTier.A));
    }

    // ==================== 死亡 ====================

    /**
     * 玄骸死亡。b = 0 非玩家击杀、1 卡没放齐：{@link Kind#DEATH} 消散 + 死亡声；
     * b = 2 怜悯之击成功收尾：{@link Kind#REQUIEM} 消散，不放死亡声（处决时间轴在抽刀那一刻已经起了这段，
     * 这时再到的包会被忽略；晚进视野、没跑时间轴的人靠这个包看到消散）
     */
    public static void lichDeath(KuvaFxPacket p) {
        Entity e = entityOf(p);
        boolean requiem = p.b == 2;
        if (e != null) {
            dissolve(e, requiem ? Kind.REQUIEM : Kind.DEATH);
            return;
        }
        Vec3 c = p.pos();
        if (KuvaFxDirector.camDist(c) > 48.0D) {
            return;
        }
        KuvaFxDirector.start("lich death (no entity)", c, FxTier.A).at(0, () -> {
            SoundEvent ev = requiem ? KuvaLichSounds.REQUIEM_DISSOLVE.get() : KuvaLichSounds.ENTITY_LICH_DEATH.get();
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(ev, SoundSource.HOSTILE, c, 1.0F, 1.0F).tier(FxTier.A));
            looseMist(c, 6);
        });
    }

    /**
     * 奴仆死亡：{@link Kind#THRALL} 消散。b bit0 = 这次真的给了解密进度（才放飞向击杀者的密语光点），
     * bit1 = 怜悯之击之后（死亡声降到 0.6，处决那一刀已经够响了）。a = 击杀者 id + 1
     */
    public static void thrallDeath(KuvaFxPacket p) {
        Entity e = entityOf(p);
        boolean progress = (p.b & 1) != 0;
        boolean afterMercy = (p.b & 2) != 0;
        float vol = afterMercy ? 0.6F : 1.0F;
        Vec3 c = e != null ? chest(e) : p.pos();
        if (e != null) {
            // 远处也要走一遍：模型得在实体移除前淡掉（粒子和声音按距离自己取舍）
            Spec s = spec(Kind.THRALL, e);
            s.vol = vol;
            play(e, s);
        } else if (KuvaFxDirector.camDist(c) <= 48.0D) {
            KuvaFxDirector.start("thrall death (no entity)", c, FxTier.B).at(0, () -> {
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_THRALL_DEATH.get(), SoundSource.HOSTILE, c, vol,
                        KuvaSoundPlayer.jitter(1.0F, 0.07F)).tier(FxTier.B));
                looseMist(c, 3);
            });
        }
        if (progress && KuvaFxDirector.camDist(c) <= 48.0D) {
            murmurStream(c, p.a - 1);
        }
    }

    /**
     * 密语流：3~5 个光点先往上浮，再飞进击杀者胸口，到了在胸口亮一圈。芯 {@code KUVA_PALE}、晕 {@code KUVA}——
     * 解密这一组不用金色，金色只留给奖励。进度音由解密 HUD 自己按圆环的节奏放，这里不放。
     * 击杀者不在、或离得超过 32 格时不放；每秒超过 6 次时只出两个火花
     */
    private static void murmurStream(Vec3 c, int killerId) {
        Minecraft mc = Minecraft.getInstance();
        Entity killer = mc.level != null && killerId >= 0 ? mc.level.getEntity(killerId) : null;
        if (killer == null || killer.position().distanceToSqr(c) > 32.0D * 32.0D) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (now - murmurWindowStart >= 1000L) {
            murmurWindowStart = now;
            murmurWindowCount = 0;
        }
        boolean full = murmurWindowCount++ < MURMUR_FULL_PER_SEC;
        FxTimeline tl = KuvaFxDirector.start("V07 murmur stream", c, FxTier.B).until(1100);
        if (!full) {
            tl.at(150, () -> {
                for (int i = 0; i < 2; i++) {
                    KuvaParticles.spark(c.x, c.y, c.z).vel(rand(-0.05D, 0.05D), rand(0.08D, 0.14D), rand(-0.05D, 0.05D))
                            .drag(0.86F).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.3F, 0.1F)
                            .orient(KuvaParticles.Orient.VELOCITY).life(5).spawn();
                }
            });
            return;
        }
        int n = 3 + RANDOM.nextInt(3);
        tl.at(150, () -> {
            for (int i = 0; i < n; i++) {
                double x = c.x + rand(-0.25D, 0.25D), y = c.y + rand(-0.1D, 0.2D), z = c.z + rand(-0.25D, 0.25D);
                double vx = rand(-0.02D, 0.02D), vy = rand(0.05D, 0.08D), vz = rand(-0.02D, 0.02D);
                float home = (float) rand(0.12D, 0.18D);
                KuvaParticles.mote(x, y, z).vel(vx, vy, vz).drag(0.95F).home(killer, home)
                        .color(FxPalette.KUVA_PALE).size(0.09F, 0.07F).life(16).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
                KuvaParticles.glow(x, y, z).vel(vx, vy, vz).drag(0.95F).home(killer, home)
                        .color(FxPalette.KUVA).alpha(0.3F).size(0.22F, 0.16F).life(16).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
            }
        });
        tl.at(950, () -> {
            if (killer.isRemoved()) {
                return;
            }
            Vec3 kc = chest(killer);
            KuvaParticles.ring(kc.x, kc.y, kc.z).color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).alpha(0.7F)
                    .size(0.1F, 0.5F).sizeCurve(FxCurve.OUT_QUART).life(5).spawn();
        });
    }

    /** 没有实体可依附时的一小团雾 */
    private static void looseMist(Vec3 c, int n) {
        for (int i = 0; i < n; i++) {
            KuvaParticles.mist(c.x + rand(-0.4D, 0.4D), c.y + rand(-0.5D, 0.3D), c.z + rand(-0.4D, 0.4D))
                    .vel(rand(-0.01D, 0.01D), rand(0.01D, 0.025D), rand(-0.01D, 0.01D)).color(FxPalette.KUVA_MIST)
                    .alpha(0.5F).size(0.9F, 1.8F).life(30 + RANDOM.nextInt(15)).spin(0.02F).spawn();
        }
    }

    // ==================== 统一消散 ====================

    /** 玄骸 / 奴仆离场时统一的消散演出，按种类决定各段长短 */
    public enum Kind {
        /** 非玩家击杀、卡没放齐 */
        DEATH,
        /** 倒地超时 */
        EXPIRE,
        /** 判错后逃走 */
        ESCAPE,
        /** 怜悯之击成功收尾 */
        REQUIEM,
        /** 奴仆死亡 */
        THRALL,
        /** 主人下线、跑远、换维度等原因离场 */
        DEPART
    }

    /** 一次消散的参数：各段长短、数量、声音 */
    static final class Spec {
        final Kind kind;
        /** 晶化段长；0 = 没有晶化，直接雾散 */
        int crystalMs;
        /** 碎裂闪光的半径；0 = 没有碎裂段 */
        float shatterR;
        /** 模型从晶化结束起淡出用多久 */
        int fadeMs;
        /** 雾、光点的数量 */
        int mist = 12;
        int motes = 10;
        /** 粒子数倍数（奴仆 0.6） */
        float scale = 1.0F;
        SoundEvent sound;
        float vol = 1.0F;
        /** 雾往外推（逃走） */
        boolean pushOut;
        /** 雾原地卷起（离场） */
        boolean roll;
        /** 晶化段开头胸口跳两下（死亡） */
        boolean pulses;
        FxTier tier = FxTier.A;

        Spec(Kind kind) {
            this.kind = kind;
        }

        int endMs() {
            return crystalMs + fadeMs;
        }
    }

    /** 记下来的一次消散 */
    private record Dissolving(Kind kind, long startMs, int crystalMs, int endMs) {
    }

    /**
     * 各种消散的基本参数（{@code e} 用来分玄骸 / 奴仆）。
     * <ul>
     *   <li>DEATH：晶化 350 → 碎裂 r 0.8 → 雾散到 950 ms；晶化段胸口跳两下；死亡声。</li>
     *   <li>EXPIRE：没有碎裂，晶化 400 直接接雾散，总长 1.2 s（慢慢散掉，不是胜利）；奴仆粒子 × 0.6、声音 0.6。</li>
     *   <li>ESCAPE：没有晶化，雾散 400 ms、雾往外推；声音由前面的冲击放过了。</li>
     *   <li>REQUIEM：晶化 500 → 碎裂 r 0.6 → 雾散到 1500 ms。</li>
     *   <li>THRALL：数量 × 0.6，总长 800 ms；奴仆死亡声。</li>
     *   <li>DEPART：没有晶化，350 ms 淡出，16 团雾原地卷起再散；赤毒雾瞬移那一声 × 0.6。</li>
     * </ul>
     */
    static Spec spec(Kind kind, Entity e) {
        boolean thrall = !(e instanceof KuvaMasterEntity) && e instanceof AbstractKuva;
        Spec s = new Spec(kind);
        switch (kind) {
            case DEATH -> {
                s.crystalMs = 350;
                s.shatterR = 0.8F;
                s.fadeMs = 600;
                s.pulses = true;
                s.sound = KuvaLichSounds.ENTITY_LICH_DEATH.get();
                s.tier = FxTier.S;
            }
            case EXPIRE -> {
                s.crystalMs = 400;
                s.fadeMs = 800;
                s.mist = 14;
                s.sound = KuvaLichSounds.MERCY_EXPIRE.get();
                if (thrall) {
                    s.scale = 0.6F;
                    s.vol = 0.6F;
                }
            }
            case ESCAPE -> {
                s.fadeMs = 400;
                s.pushOut = true;
                s.tier = FxTier.S;
            }
            case REQUIEM -> {
                s.crystalMs = 500;
                s.shatterR = 0.6F;
                s.fadeMs = 1000;
                s.sound = KuvaLichSounds.REQUIEM_DISSOLVE.get();
                s.tier = FxTier.S;
            }
            case THRALL -> {
                s.crystalMs = 200;
                s.shatterR = 0.6F;
                s.fadeMs = 600;
                s.scale = 0.6F;
                s.sound = KuvaLichSounds.ENTITY_THRALL_DEATH.get();
                s.tier = FxTier.B;
            }
            case DEPART -> {
                s.fadeMs = 350;
                s.mist = 16;
                s.motes = 0;
                s.roll = true;
            }
        }
        return s;
    }

    /** 一段消散演完要多久（模型淡到 0 的时刻，毫秒） */
    public static int lengthMs(Kind kind, Entity e) {
        return spec(kind, e).endMs();
    }

    /** 统一的消散演出；同一个实体演出中再调直接忽略 */
    public static void dissolve(Entity entity, Kind kind) {
        if (entity == null) {
            return;
        }
        play(entity, spec(kind, entity));
    }

    /**
     * 按给定参数演一段消散；这只实体正在演就忽略并返回 false。安魂判定（{@link RequiemFx}）用它把收尾消散接在自己的爆发上
     */
    static boolean play(Entity e, Spec s) {
        int id = e.getId();
        long now = KuvaFxDirector.clockMs();
        Dissolving prev = DISSOLVING.get(id);
        if (prev != null && now < prev.startMs() + prev.endMs()) {
            if (!FMLEnvironment.production) {
                LogUtil.info("[LichFx] dissolve " + s.kind + " ignored: entity " + id + " is already dissolving ("
                        + prev.kind() + ", " + (now - prev.startMs()) + " ms in)");
            }
            return false;
        }
        if (DISSOLVING.size() > 64) {
            DISSOLVING.values().removeIf(d -> now - d.startMs() > DISSOLVE_MEMORY_MS);
        }
        DISSOLVING.put(id, new Dissolving(s.kind, now, s.crystalMs, s.endMs()));

        Vec3 c0 = chest(e);
        double d = KuvaFxDirector.camDist(c0);
        // 远处不画粒子，但模型照样要淡出（实体离场后客户端还在画它就不对了）
        int c = s.crystalMs;
        float k = s.scale;
        int end = s.endMs();
        FxTimeline tl = KuvaFxDirector.start("dissolve " + s.kind.name().toLowerCase(), c0, s.tier).follow(e)
                .until(end + (s.roll ? 950 : 300));

        tl.at(0, () -> {
            KuvaGlow.mode(id, KuvaGlow.Mode.DISSOLVE);
            if (s.sound != null && d <= 48.0D) {
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(s.sound, SoundSource.HOSTILE, chest(e), s.vol,
                        KuvaSoundPlayer.jitter(1.0F, s.kind == Kind.THRALL ? 0.07F : 0.0F)).tier(s.tier));
            }
            if (s.kind == Kind.DEPART && d <= 64.0D) {
                departSound(chest(e), d);
            }
        });

        // ---------- 晶化：发光层提亮到 ×2.2，模型偏向深赤（渲染器读 crystallize），胸口冒火花 ----------
        if (c > 0) {
            for (int i = 1; i <= 3; i++) {
                final float f = 1.0F + 1.2F * i / 3.0F;
                final int at = c * (i - 1) / 3;
                tl.at(at, () -> KuvaGlow.boost(id, f, end - at));
            }
            tl.at(0, () -> crystalSparks(e, scaled(2, k)));
            tl.at(c / 2, () -> crystalSparks(e, scaled(2, k)));
            if (s.pulses) {
                tl.at(0, () -> chestPulse(e));
                tl.at(160, () -> chestPulse(e));
            }
        }

        // ---------- 碎裂 ----------
        if (s.shatterR > 0.0F) {
            tl.at(c, () -> shatter(e, s.shatterR, k));
        }

        // ---------- 雾散：模型淡出，雾往上扩散，光点升起 ----------
        tl.at(c, () -> LichRenderState.fadeOut(id, s.fadeMs));
        if (s.roll) {
            tl.at(0, () -> departMist(e, s.mist));
        } else {
            int mist = scaled(s.mist, k);
            int half = mist / 2;
            tl.at(c, () -> fogMist(e, half, s.pushOut));
            tl.at(c + Math.min(200, s.fadeMs / 3), () -> fogMist(e, mist - half, s.pushOut));
            if (s.motes > 0) {
                // 光点分四拍在雾散段的前 60% 里升起
                int motes = scaled(s.motes, k);
                for (int i = 0; i < 4; i++) {
                    int n = motes / 4 + (i < motes % 4 ? 1 : 0);
                    if (n > 0) {
                        tl.at(c + Math.round(s.fadeMs * 0.6F * i / 3.0F), () -> fogMotes(e, n));
                    }
                }
            }
        }
        return true;
    }

    /** 胸口 2 个白热火花（晶化段） */
    private static void crystalSparks(Entity e, int n) {
        Vec3 c = chest(e);
        for (int i = 0; i < n; i++) {
            Vec3 v = randomDir(0.3D).scale(rand(0.06D, 0.14D));
            KuvaParticles.spark(c.x + rand(-0.15D, 0.15D), c.y + rand(-0.15D, 0.15D), c.z + rand(-0.15D, 0.15D))
                    .vel(v.x, v.y, v.z).drag(0.84F).color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA)
                    .size(0.4F, 0.12F).orient(KuvaParticles.Orient.VELOCITY).life(5 + RANDOM.nextInt(4)).spawn();
        }
    }

    /** 死亡晶化段开头胸口的一下脉动 */
    private static void chestPulse(Entity e) {
        KuvaFxGeometry.glow(chest(e)).follow(e, 0.62F).color(FxPalette.KUVA)
                .sizeFn(ms -> 0.45F + 0.15F * FxCurve.easeOutCubic(FxCurve.clamp01(ms / 60.0F)))
                .alphaFn(ms -> ms < 40.0F ? 0.6F * ms / 40.0F : 0.6F * FxCurve.fadeOut((ms - 40.0F) / 80.0F))
                .life(120).add();
    }

    /** 碎裂：闪光（不用白）、碎片、火花 */
    private static void shatter(Entity e, float r, float k) {
        Vec3 c = chest(e);
        KuvaFxGeometry.flash(c).follow(e, 0.62F).radius(r, 60).core(FxPalette.KUVA_HOT).color(FxPalette.KUVA)
                .alpha(0.9F, FxCurve.FADE_SQ).life(220).core().add();
        for (int i = 0; i < scaled(10, k); i++) {
            Vec3 v = randomDir(0.3D).scale(rand(0.08D, 0.22D)).add(0.0D, 0.05D, 0.0D);
            float sz = (float) rand(0.18D, 0.34D);
            KuvaParticles.shard(c.x, c.y, c.z).vel(v.x, v.y, v.z).gravity(0.05F).drag(0.95F)
                    .spin((float) rand(-0.3D, 0.3D)).color(FxPalette.KUVA_DEEP).color2(FxPalette.KUVA)
                    .alpha(0.95F).size(sz, sz * 0.8F).life(12 + RANDOM.nextInt(9)).spawn();
        }
        for (int i = 0; i < scaled(6, k); i++) {
            Vec3 v = randomDir(0.0D).scale(rand(0.25D, 0.45D));
            KuvaParticles.spark(c.x, c.y, c.z).vel(v.x, v.y, v.z).drag(0.82F).gravity(0.02F)
                    .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).alpha(1.0F).size((float) rand(0.4D, 0.7D), 0.12F)
                    .orient(KuvaParticles.Orient.VELOCITY).life(5 + RANDOM.nextInt(5)).spawn();
        }
    }

    /** 雾散的雾：从身体里往上扩散；{@code out} 时往四周推开 */
    private static void fogMist(Entity e, int n, boolean out) {
        Vec3 feet = e.position();
        double w = e.getBbWidth() * 0.5D, h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double rr = rand(0.0D, w);
            double x = feet.x + Math.cos(a) * rr, y = feet.y + rand(0.15D, 0.9D) * h, z = feet.z + Math.sin(a) * rr;
            double vx, vy, vz;
            if (out) {
                vx = Math.cos(a) * rand(0.06D, 0.1D);
                vy = rand(0.02D, 0.05D);
                vz = Math.sin(a) * rand(0.06D, 0.1D);
            } else {
                vx = Math.cos(a) * 0.012D;
                vy = rand(0.015D, 0.03D);
                vz = Math.sin(a) * 0.012D;
            }
            KuvaParticles.mist(x, y, z).vel(vx, vy, vz).drag(out ? 0.93F : 0.98F)
                    .color(FxPalette.KUVA_MIST).color2(FxPalette.KUVA_DEEP).alpha(0.5F).size(0.9F, 2.1F)
                    .life(30 + RANDOM.nextInt(16)).spin(RANDOM.nextBoolean() ? 0.02F : -0.02F).spawn();
        }
    }

    /** 雾散段升起的光点 */
    private static void fogMotes(Entity e, int n) {
        Vec3 feet = e.position();
        double w = e.getBbWidth() * 0.6D, h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            KuvaParticles.mote(feet.x + rand(-w, w), feet.y + rand(0.2D, 0.95D) * h, feet.z + rand(-w, w))
                    .vel(0.0D, rand(0.03D, 0.06D), 0.0D).drag(0.98F).color(FxPalette.KUVA).size(0.09F, 0.09F)
                    .life(20 + RANDOM.nextInt(16)).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
        }
    }

    /**
     * 离场：胸口光往里收；身上的雾从碰撞箱里随机点往外卷，地面留 4 团慢慢散。
     * 胸口光钉在原地不跟实体：换位时实体下一 tick 就传送走了，跟着走的话光会跳到新位置
     */
    private static void departMist(Entity e, int n) {
        Vec3 c = chest(e);
        KuvaFxGeometry.glow(c).color(FxPalette.KUVA).size(0.9F, 0.3F, FxCurve.IN_CUBIC)
                .alpha(0.7F, FxCurve.FADE_SQ).life(350).add();
        Vec3 feet = e.position();
        double w = e.getBbWidth() * 0.5D, h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2.0D;
            double sp = rand(0.05D, 0.09D);
            KuvaParticles.mist(feet.x + rand(-w, w), feet.y + rand(0.1D, 0.95D) * h, feet.z + rand(-w, w))
                    .vel(Math.cos(a) * sp, rand(0.0D, 0.02D), Math.sin(a) * sp).drag(0.9F)
                    .color(FxPalette.KUVA_MIST).alpha(0.55F).size(0.9F, 1.9F).life(18 + RANDOM.nextInt(9))
                    .spin(RANDOM.nextBoolean() ? 0.05F : -0.05F).spawn();
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5D * i + rand(-0.4D, 0.4D);
            KuvaParticles.mist(feet.x + Math.cos(a) * 0.5D, feet.y + 0.15D, feet.z + Math.sin(a) * 0.5D)
                    .vel(Math.cos(a) * 0.006D, 0.002D, Math.sin(a) * 0.006D).drag(0.98F)
                    .color(FxPalette.KUVA_MIST).alpha(0.45F).size(1.2F, 1.8F).life(30).spin(0.01F).spawn();
        }
    }

    /**
     * 晶化程度 0~1：消散的晶化段里从 0 涨到 1，之后保持到模型淡完。渲染器把本体顶点色往 (1.0, 0.55, 0.6) 拉这么多；
     * 没在消散、或这种消散没有晶化段（逃走、离场）时为 0
     */
    public static float crystallize(Entity entity, float partialTick) {
        Dissolving d = DISSOLVING.get(entity.getId());
        if (d == null || d.crystalMs() <= 0) {
            return 0.0F;
        }
        long t = KuvaFxDirector.clockMs() - d.startMs();
        if (t < 0L) {
            return 0.0F;
        }
        if (t >= d.crystalMs()) {
            return t > d.endMs() + 1000L ? 0.0F : 1.0F;
        }
        float x = t / (float) d.crystalMs();
        return x * x * (3.0F - 2.0F * x);
    }

    /** 让这只实体原地踉跄 {@code ms} 毫秒（判错后挣脱前） */
    static void stagger(int entityId, int ms) {
        long until = KuvaFxDirector.clockMs() + ms;
        if (STAGGER_UNTIL.size() > 32) {
            long now = KuvaFxDirector.clockMs();
            STAGGER_UNTIL.values().removeIf(t -> t < now);
        }
        STAGGER_UNTIL.put(entityId, until);
    }

    /**
     * 踉跄的幅度（格）：在踉跄期间是 0.02，渲染器每帧在水平方向随机抖这么多；不在踉跄时为 0
     */
    public static float staggerAmplitude(Entity entity) {
        long until = STAGGER_UNTIL.get(entity.getId());
        return KuvaFxDirector.clockMs() < until ? 0.02F : 0.0F;
    }

    /** 这只实体有没有在演（或刚演完）消散 */
    public static boolean isDissolving(int entityId) {
        Dissolving d = DISSOLVING.get(entityId);
        return d != null && KuvaFxDirector.clockMs() - d.startMs() < DISSOLVE_MEMORY_MS;
    }

    /** 忘掉这只实体的消散记录，恢复显形和正常发光（开发命令演完复原用） */
    public static void forgetDissolve(int entityId) {
        DISSOLVING.remove(entityId);
        KuvaGlow.mode(entityId, KuvaGlow.Mode.NORMAL);
        LichRenderState.fadeIn(entityId, 0);
    }

    // ==================== 实体事件 ====================

    /** 实体事件字节 96（登场警示）、103（近战命中）和原版的 60（死亡白烟，换成自制小消散） */
    public static void onEntityEvent(AbstractKuva entity, byte id) {
        if (id == AbstractKuva.EV_ALERT) {
            if (entity instanceof KuvaMasterEntity) {
                lichAlert(entity);
            } else {
                thrallAlert(entity);
            }
        } else if (id == KuvaAnimFx.EV_HIT) {
            KuvaAnimFx.onHit(entity);
        } else if (id == AbstractKuva.EV_DEATH_POOF) {
            deathPoof(entity);
        }
    }

    // ==================== V01 玄骸登场（无主玄骸） ====================

    /**
     * 只留胸口一点光：胸口光 120 ms 涨起、280 ms 落下（α 峰值 0.45），发光层 400 ms 内提到 ×1.75。
     * 不放刺音、不画扫描环 / 地面环 / 闪光 / 暗角——玄骸来了靠 Boss 条和通讯窗交代，世界里不加大特效
     */
    public static void lichAlert(AbstractKuva e) {
        Vec3 chest = chest(e);
        if (KuvaFxDirector.camDist(chest) > 64.0D) {
            return;
        }
        lastLichAlert = KuvaFxDirector.clockMs();
        KuvaFxDirector.start("V01 lich alert", chest, FxTier.A).follow(e).at(0, () -> {
            KuvaGlow.boost(e.getId(), 1.75F, 400);
            chestGlow(e, chest(e));
        });
    }

    /** 到场的胸口光：尺寸 0.4 → 0.8，120 ms 涨到 α 0.45，再 280 ms 落下 */
    private static void chestGlow(Entity e, Vec3 c) {
        KuvaFxGeometry.Glow g = KuvaFxGeometry.glow(c).color(FxPalette.KUVA)
                .sizeFn(ms -> 0.4F + 0.4F * FxCurve.easeOutCubic(FxCurve.clamp01(ms / 120.0F)))
                .alphaFn(ms -> ms < 120.0F ? 0.45F * FxCurve.easeOutCubic(ms / 120.0F)
                        : 0.45F * FxCurve.fadeOut((ms - 120.0F) / 280.0F))
                .life(400);
        if (e != null) {
            g.follow(e, 0.62F);
        }
        g.add();
    }

    // ==================== 奴仆被盯上 ====================

    /**
     * 奴仆没有出场演出（靠常驻名牌和描边认）；只在「奴仆警示音」开着时（默认关）放一声，
     * 120 s 只放一次，玄骸刚警示过的 10 s 内也不放
     */
    private static void thrallAlert(AbstractKuva e) {
        if (!EffectsDisplayConfig.thrallAlert()) {
            return;
        }
        Vec3 c = chest(e);
        double d = KuvaFxDirector.camDist(c);
        long now = KuvaFxDirector.clockMs();
        if (d > 40.0D || now - lastThrallSound < THRALL_SOUND_GATE_MS || now - lastLichAlert < LICH_ALERT_QUIET_MS) {
            return;
        }
        lastThrallSound = now;
        float vol = 0.8F * KuvaSoundPlayer.distFalloff(d, 16.0D, 40.0D, 0.375F);
        KuvaSoundPlayer.play(KuvaSoundPlayer.atFixed(KuvaLichSounds.ENTITY_THRALL_ALERT.get(), SoundSource.HOSTILE, c, vol, 1.0F)
                .tier(FxTier.A));
    }

    // ==================== 死亡第 20 tick 的小消散（替掉原版白烟） ====================

    /** 只在这只实体没收到任何离场包、没演过消散时兜底 */
    private static void deathPoof(AbstractKuva e) {
        if (isDissolving(e.getId())) {
            return;
        }
        Vec3 c = chest(e);
        if (KuvaFxDirector.camDist(c) > 48.0D) {
            return;
        }
        FxTimeline tl = KuvaFxDirector.start("lich poof", c, FxTier.B);
        tl.at(0, () -> {
            looseMist(c, 4);
            for (int i = 0; i < 6; i++) {
                KuvaParticles.mote(c.x + rand(-0.5D, 0.5D), c.y + rand(-0.6D, 0.4D), c.z + rand(-0.5D, 0.5D))
                        .vel(0.0D, rand(0.03D, 0.05D), 0.0D).drag(0.97F).color(FxPalette.KUVA).size(0.08F, 0.08F)
                        .life(20 + RANDOM.nextInt(10)).alphaCurve(FxCurve.HOLD_THEN_FADE).spawn();
            }
        });
    }

    /** 随机单位方向；{@code upBias} 越大越往上偏 */
    static Vec3 randomDir(double upBias) {
        double y = RANDOM.nextDouble() * 2.0D - 1.0D;
        y = Math.min(1.0D, y + upBias * (1.0D - Math.abs(y)));
        double a = RANDOM.nextDouble() * Math.PI * 2.0D;
        double h = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        return new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
    }

    /** 身体朝向的水平单位向量 */
    static Vec3 facing(Entity e) {
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        return new Vec3(-Mth.sin((float) r), 0.0D, Mth.cos((float) r));
    }
}
