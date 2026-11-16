package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.client.ephemera.EphemeraDraw;
import pers.roinflam.kuvalich.client.ephemera.EphemeraHost;
import pers.roinflam.kuvalich.client.ephemera.EphemeraInstance;
import pers.roinflam.kuvalich.client.ephemera.EphemeraSprites;
import pers.roinflam.kuvalich.client.ephemera.EphemeraStaging;
import pers.roinflam.kuvalich.client.ephemera.EphemeraStyle;
import pers.roinflam.kuvalich.client.ephemera.EphemeraStyles;
import pers.roinflam.kuvalich.client.ephemera.EphemeraTier;
import pers.roinflam.kuvalich.client.ephemera.FxMotion;
import pers.roinflam.kuvalich.client.ephemera.CoilStyle;
import pers.roinflam.kuvalich.client.ephemera.OrbitStyle;
import pers.roinflam.kuvalich.client.ephemera.PlumeStyle;
import pers.roinflam.kuvalich.client.ephemera.SurgeStyle;
import pers.roinflam.kuvalich.client.ephemera.VortexStyle;
import pers.roinflam.kuvalich.client.ephemera.WingStyle;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.function.Supplier;

/**
 * 幻纹正式画法里不碰客户端类的部分：拿一个按脚本动的假宿主（站、走、疾跑、起跳落地、挥砍两下、急停、受击、潜行）
 * 逐帧画，核四边形数在预算内、收敛档比全开少、各元素走到各自的画法、尺寸跟着包围盒走。
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EphemeraStyleTests {

    private static final String BATCH = "kl_ephemera_style";
    private static final float FPS = 30.0F;
    private static final float SCRIPT_SECONDS = 15.0F;

    private EphemeraStyleTests() {
    }

    /** 照原型的自动演示排的动作：0 站、2 走、4.5 疾跑、6.8 跳、8.3 / 8.75 挥砍、9.7 急停、11.2 受击、12.4 潜行 */
    static class ScriptHost implements EphemeraHost {
        final float w, h;
        double x, y = 64, z, px, py = 64, pz;
        boolean ground = true;
        float vyUp;
        float t;
        Pose pose = Pose.STAND;

        ScriptHost(float w, float h) {
            this.w = w;
            this.h = h;
        }

        void step(float now, float dt) {
            t = now;
            px = x;
            py = y;
            pz = z;
            float speed = now < 2 ? 0 : now < 4.5F ? 4.317F : now < 9.7F ? 5.612F : now < 12.4F ? 0 : 1.295F;
            pose = now >= 12.4F ? Pose.CROUCH : Pose.STAND;
            float k = h / 1.8F;
            z += speed * k * dt;
            if (ground && now >= 6.8F && now - dt < 6.8F) {
                ground = false;
                vyUp = 8.4F * (float) Math.sqrt(k);
            }
            if (!ground) {
                vyUp -= 32 * dt;
                y += vyUp * dt;
                if (y <= 64) {
                    y = 64;
                    ground = true;
                }
            }
        }

        @Override
        public int id() {
            return 1;
        }

        @Override
        public double x() {
            return x;
        }

        @Override
        public double y() {
            return y;
        }

        @Override
        public double z() {
            return z;
        }

        @Override
        public float bodyYaw() {
            return 0;
        }

        @Override
        public float width() {
            return w;
        }

        @Override
        public float height() {
            return pose == Pose.CROUCH ? h * 1.5F / 1.8F : h;
        }

        @Override
        public double vx() {
            return (x - px) / (1 / FPS) / 20;
        }

        @Override
        public double vy() {
            return (y - py) / (1 / FPS) / 20;
        }

        @Override
        public double vz() {
            return (z - pz) / (1 / FPS) / 20;
        }

        @Override
        public Pose pose() {
            return pose;
        }

        @Override
        public boolean onGround() {
            return ground;
        }

        @Override
        public double groundY() {
            return 64;
        }

        @Override
        public boolean riding() {
            return false;
        }

        @Override
        public float swing() {
            for (float at : new float[]{8.3F, 8.75F}) {
                if (t >= at && t < at + 0.3F) {
                    return (t - at) / 0.3F;
                }
            }
            return 0;
        }

        @Override
        public float hurt() {
            return t >= 11.2F && t < 11.7F ? 1 - (t - 11.2F) / 0.5F : 0;
        }

        @Override
        public boolean self() {
            return false;
        }
    }

    static final class Result {
        int maxTotal, maxBase, sumTotal;
        final boolean[] sprites = new boolean[EphemeraSprites.COUNT];
        float firstQuadWidth = -1;
    }

    /** 整段脚本画一遍；每帧取提交前的四边形数，提交后按顶点 UV 记用到了哪些贴图 */
    static Result run(EphemeraStyle style, String type, String element, boolean full, EphemeraTier tier, float w, float h) {
        EphemeraStaging out = new EphemeraStaging();
        EphemeraDraw d = new EphemeraDraw(out);
        EphemeraLook look = EphemeraLook.ofDefault(type, element);
        EphemeraInstance inst = new EphemeraInstance(1, look, style);
        ScriptHost host = new ScriptHost(w, h);
        d.look = look;
        d.full = full;
        d.tier = tier;
        d.emitScale = 1;
        d.glowAlpha = EphemeraTier.GLOW_NIGHT;
        d.rightX = 1;
        d.upY = 1;
        Result r = new Result();
        int frames = (int) (SCRIPT_SECONDS * FPS);
        for (int f = 0; f < frames; f++) {
            float t = f / FPS;
            host.step(t, 1 / FPS);
            d.timeMs = 100_000L + Math.round(t * 1000);
            d.camX = host.x;
            d.camY = host.y + 1.5;
            d.camZ = host.z - 4;
            out.clearFrame();
            inst.update(host, d.timeMs, tier);
            style.draw(inst, host, d);
            int total = out.pending();
            int base = out.pending(EphemeraStaging.BASE);
            r.maxTotal = Math.max(r.maxTotal, total);
            r.maxBase = Math.max(r.maxBase, base);
            r.sumTotal += total;
            out.commit(Integer.MAX_VALUE);
            for (int layer = 0; layer < 2; layer++) {
                EphemeraStaging.Layer l = out.layer(layer);
                float[] uv = l.uvs();
                for (int q = 0; q < l.quads(); q++) {
                    float u = (uv[q * 8] + uv[q * 8 + 4]) * 0.5F, v = (uv[q * 8 + 1] + uv[q * 8 + 5]) * 0.5F;
                    int cell = (int) (u * (EphemeraSprites.ATLAS / EphemeraSprites.CELL))
                            + (int) (v * (EphemeraSprites.ATLAS / EphemeraSprites.CELL)) * (EphemeraSprites.ATLAS / EphemeraSprites.CELL);
                    if (cell >= 0 && cell < EphemeraSprites.COUNT) {
                        r.sprites[cell] = true;
                    }
                }
            }
            if (f == 0) {
                EphemeraStaging.Layer l = out.layer(EphemeraStaging.TRANSLUCENT);
                if (l.quads() > 0) {
                    float[] p = l.positions();
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int k = 0; k < 4; k++) {
                        lo = Math.min(lo, p[k * 3]);
                        hi = Math.max(hi, p[k * 3]);
                    }
                    r.firstQuadWidth = hi - lo;
                }
            }
        }
        return r;
    }

    private static final String[] TYPES = {"surge", "orbit", "plume", "wing", "coil", "vortex"};

    private static Supplier<EphemeraStyle> fresh(String type) {
        return switch (type) {
            case "surge" -> SurgeStyle::new;
            case "orbit" -> OrbitStyle::new;
            case "plume" -> PlumeStyle::new;
            case "wing" -> WingStyle::new;
            case "coil" -> CoilStyle::new;
            default -> VortexStyle::new;
        };
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void stylesRegisteredAndWithinBudget(GameTestHelper h) {
        for (String type : TYPES) {
            h.assertTrue(EphemeraStyles.of(type) != EphemeraStyles.PLACEHOLDER, type + " 应走正式画法");
        }
        h.assertTrue(EphemeraStyles.of("no_such_style") == EphemeraStyles.PLACEHOLDER, "查不到的款式应走占位");
        StringBuilder log = new StringBuilder("幻纹四边形（收敛档，整段脚本最大值，完整 / 精简，底座）：");
        for (String type : TYPES) {
            for (String el : new String[]{"fire", "ice", "electricity", "impact"}) {
                Result full = run(fresh(type).get(), type, el, true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
                Result lite = run(fresh(type).get(), type, el, false, EphemeraTier.CONVERGED, 0.6F, 1.8F);
                log.append(' ').append(type).append('/').append(el).append(' ').append(full.maxTotal).append('/')
                        .append(lite.maxTotal).append(" 底座 ").append(full.maxBase).append('/').append(lite.maxBase).append(';');
                h.assertTrue(full.maxTotal <= EphemeraTier.CAP_FULL, type + "/" + el + " 完整档超预算：" + full.maxTotal);
                // 精简档超出的部分由渲染器砍粒子和拖尾，底座本身必须放得下
                h.assertTrue(lite.maxBase <= EphemeraTier.CAP_REDUCED, type + "/" + el + " 精简档底座超预算：" + lite.maxBase);
                h.assertTrue(lite.maxTotal < full.maxTotal, type + "/" + el + " 精简档应比完整档少");
                h.assertTrue(full.sumTotal > 0, type + "/" + el + " 什么都没画");
            }
        }
        LogUtil.info(log.toString());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void convergedDrawsLessThanFullOpen(GameTestHelper h) {
        for (String type : TYPES) {
            Result calm = run(fresh(type).get(), type, "fire", true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
            Result open = run(fresh(type).get(), type, "fire", true, EphemeraTier.FULL_OPEN, 0.6F, 1.8F);
            h.assertTrue(calm.sumTotal < open.sumTotal, type + " 收敛档应比全开少：" + calm.sumTotal + " / " + open.sumTotal);
            h.assertTrue(open.maxTotal <= EphemeraTier.CAP_FULL * 1.25F, type + " 全开档也不该离预算太远：" + open.maxTotal);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void elementsPickTheirOwnLook(GameTestHelper h) {
        for (String type : TYPES) {
            Result fire = run(fresh(type).get(), type, "fire", true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
            Result ice = run(fresh(type).get(), type, "ice", true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
            Result zap = run(fresh(type).get(), type, "electricity", true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
            h.assertTrue(ice.sprites[EphemeraSprites.CRYSTAL], type + " 冰冻应长出冰晶");
            h.assertTrue(zap.sprites[EphemeraSprites.CRACK_0] || zap.sprites[EphemeraSprites.CRACK_1], type + " 电击应有电弧");
            h.assertTrue(!fire.sprites[EphemeraSprites.CRACK_0] && !fire.sprites[EphemeraSprites.CRACK_1], type + " 火焰不该有电弧");
            switch (type) {
                case "surge" -> h.assertTrue(fire.sprites[EphemeraSprites.TONGUE] && !ice.sprites[EphemeraSprites.TONGUE], "踏涌：火是光舌、冰换成冰晶");
                case "orbit", "plume" -> h.assertTrue(fire.sprites[EphemeraSprites.FLAME_0] || fire.sprites[EphemeraSprites.FLAME_0 + 1], type + " 火焰应带火舌");
                case "wing", "coil" -> h.assertTrue(smoke(fire) && !fire.sprites[EphemeraSprites.CRYSTAL], type + " 火焰应冒烟、不带冰晶");
                default -> {
                    h.assertTrue(ice.sprites[EphemeraSprites.FLOW_2] && !fire.sprites[EphemeraSprites.FLOW_2], "踏涡：冰的旋臂换成冰纹");
                    h.assertTrue(fire.sprites[EphemeraSprites.SHEATH] && fire.sprites[EphemeraSprites.RUNE], "踏涡：应有涡幕和足印");
                }
            }
        }
        h.succeed();
    }

    private static boolean smoke(Result r) {
        for (int k = 0; k < 4; k++) {
            if (r.sprites[EphemeraSprites.SMOKE_0 + k]) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void sizeFollowsBoundingBox(GameTestHelper h) {
        for (String type : TYPES) {
            Result one = run(fresh(type).get(), type, "fire", true, EphemeraTier.CONVERGED, 0.6F, 1.8F);
            Result two = run(fresh(type).get(), type, "fire", true, EphemeraTier.CONVERGED, 1.2F, 3.6F);
            float ratio = two.firstQuadWidth / one.firstQuadWidth;
            h.assertTrue(one.firstQuadWidth > 0 && Math.abs(ratio - 2) < 0.05F, type + " 两倍包围盒应两倍大：" + ratio);
        }
        h.succeed();
    }

    /** 贴地层的基准：跳一下、跳过沟留在起跳那层地面上；创造飞行、泡水时跟着脚走；中间不许一帧跳过去；瞬移当新的一份 */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void groundLayerFollowsFeetWhenAirborne(GameTestHelper h) {
        final class Host extends ScriptHost {
            boolean fly, wet;
            double found = 64;

            Host() {
                super(0.6F, 1.8F);
            }

            @Override
            public double groundY() {
                return ground ? y : found;
            }

            @Override
            public boolean flying() {
                return fly;
            }

            @Override
            public boolean inFluid() {
                return wet;
            }
        }
        Host host = new Host();
        EphemeraDraw d = new EphemeraDraw(new EphemeraStaging());
        d.tier = EphemeraTier.CONVERGED;
        d.emitScale = 1;
        EphemeraLook look = EphemeraLook.ofDefault("surge", "fire");
        EphemeraInstance inst = new EphemeraInstance(1, look, EphemeraStyles.of("surge"));
        FxMotion m = new FxMotion(0L);
        double[] last = {Double.NaN};
        int[] frame = {0};
        Runnable step = () -> {
            frame[0]++;
            d.timeMs = Math.round(frame[0] * 1000 / FPS);
            m.update(inst, host, d);
            if (!Double.isNaN(last[0]) && !m.fresh) {
                h.assertTrue(Math.abs(m.groundY - last[0]) < 0.6, "贴地层一帧跳了 " + (m.groundY - last[0]) + " 格，第 " + frame[0] + " 帧");
            }
            last[0] = m.groundY;
        };
        for (int i = 0; i < 10; i++) {
            step.run();
        }
        h.assertTrue(Math.abs(m.groundY - 64) < 1.0E-6, "站着时贴地层应在脚底");
        // 跳过一条两格深的沟：脚底下找到的地面比起跳处低
        host.ground = false;
        host.found = 62;
        for (int i = 0; i < 16; i++) {
            float tt = i / FPS;
            host.y = 64 + 8.4 * tt - 16 * tt * tt;
            step.run();
            h.assertTrue(Math.abs(m.groundY - 64) < 0.05, "跳过沟时贴地层应留在起跳处：" + m.groundY);
        }
        host.y = 64;
        host.ground = true;
        host.found = 64;
        for (int i = 0; i < 10; i++) {
            step.run();
        }
        // 创造飞行升到 6 格高悬停
        host.ground = false;
        host.fly = true;
        for (int i = 0; i < 60; i++) {
            host.y = Math.min(70, 64 + i * 0.2);
            step.run();
        }
        h.assertTrue(Math.abs(m.groundY - host.y) < 0.02 && m.attach > 0.98F, "飞行时贴地层应挂到脚下：" + m.groundY + " / " + host.y);
        // 落回地面再下水，浮在水面上，水底在 4 格以下
        while (host.y > 64) {
            host.y = Math.max(64, host.y - 0.25);
            step.run();
        }
        host.fly = false;
        host.ground = true;
        for (int i = 0; i < 30; i++) {
            step.run();
        }
        h.assertTrue(m.attach < 0.02F, "落地后贴地层应回到地上：" + m.attach);
        host.ground = false;
        host.wet = true;
        host.found = Double.NaN;
        for (int i = 0; i < 45; i++) {
            host.y = 64 - Math.min(1, i * 0.05);
            step.run();
        }
        h.assertTrue(Math.abs(m.groundY - host.y) < 0.02, "泡在水里时贴地层应挂到脚下：" + m.groundY + " / " + host.y);
        host.x += 120;
        step.run();
        h.assertTrue(m.fresh, "瞬移以后应当新的一份");
        h.succeed();
    }
}
