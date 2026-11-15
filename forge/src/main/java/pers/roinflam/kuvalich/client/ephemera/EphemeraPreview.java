package pers.roinflam.kuvalich.client.ephemera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.KuvaParticleShaders;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;

/**
 * 界面里人像脚下的幻纹预览：用世界里同一套画法，给一个站着不动、面朝屏幕的假宿主跑一遍，再把四边形正交投到界面上。
 * 人像是立体的，所以分两次画：先画人像后面那一半（离镜头比宿主远的四边形），人像画完再画前面那一半。
 */
public final class EphemeraPreview {

    // 镜头放在宿主正前方这么远，朝镜头的片、丝带按这个方向展开；投影是正交的，远近不影响大小
    private static final float CAM_DIST = 8.0F;
    // 平视时地上的圆盘、水平的环都只剩一条横线，略微俯视才看得出形状
    private static final float PITCH = 0.32F;
    private static final float SIN_P = (float) Math.sin(PITCH), COS_P = (float) Math.cos(PITCH);

    private static final EphemeraStaging STAGING = new EphemeraStaging();
    private static final EphemeraDraw DRAW = new EphemeraDraw(STAGING);
    private static final FigureHost HOST = new FigureHost();
    private static EphemeraInstance inst;
    private static boolean[][] behind = {new boolean[0], new boolean[0]};
    private static float midY;

    private EphemeraPreview() {
    }

    /** 每帧先调一次（算这一帧、画后半），人像画完再调 {@link #drawFront} */
    public static void drawBack(GuiGraphics g, EphemeraLook look, float cx, float feetY, float unit, float bodyW, float bodyH, long now) {
        simulate(look, bodyW, bodyH, now);
        submit(g, cx, feetY, unit, false);
    }

    public static void drawFront(GuiGraphics g, float cx, float feetY, float unit) {
        submit(g, cx, feetY, unit, true);
    }

    private static void simulate(EphemeraLook look, float bodyW, float bodyH, long now) {
        EphemeraStyle style = EphemeraStyles.of(look.type());
        if (inst == null || !inst.look.equals(look) || inst.style != style) {
            EphemeraInstance next = new EphemeraInstance(-1, look, style);
            // 只是调颜色、大小时接着上一帧的状态画，不然拖滑条时粒子和拖尾每帧清零
            if (inst != null && inst.style == style) {
                next.styleState = inst.styleState;
            }
            inst = next;
        }
        HOST.w = bodyW;
        HOST.h = bodyH;
        midY = bodyH * 0.55F;
        DRAW.look = look;
        DRAW.full = true;
        DRAW.tier = EphemeraTier.CONVERGED;
        DRAW.emitScale = 1;
        // 界面底色是深色，按夜里的发光系数画
        DRAW.glowAlpha = EphemeraTier.GLOW_NIGHT;
        DRAW.firstPersonSelf = false;
        DRAW.camX = 0;
        DRAW.camY = midY + CAM_DIST * SIN_P;
        DRAW.camZ = -CAM_DIST * COS_P;
        // 镜头朝 +z 略往下看，屏幕右边是世界的 -x
        DRAW.rightX = -1;
        DRAW.rightY = 0;
        DRAW.rightZ = 0;
        DRAW.upX = 0;
        DRAW.upY = COS_P;
        DRAW.upZ = SIN_P;
        DRAW.timeMs = now;
        STAGING.clearFrame();
        STAGING.additiveAllowed = true;
        STAGING.nearFade = 0;
        STAGING.alphaMul = 1;
        inst.update(HOST, now, DRAW.tier);
        try {
            style.draw(inst, HOST, DRAW);
            if (style.postGradient()) {
                STAGING.gradePending(look.gradient(), look.color1(), look.color2(), 0, CAM_DIST * COS_P, (now % 600_000L) / 1000.0F);
            }
        } catch (RuntimeException ex) {
            STAGING.discardPending();
        }
        STAGING.commit(EphemeraTier.CAP_FULL);
        for (int k = 0; k < 2; k++) {
            EphemeraStaging.Layer l = STAGING.layer(k);
            int n = l.quads();
            if (behind[k].length < n) {
                behind[k] = new boolean[Math.max(n, behind[k].length * 2)];
            }
            float[] p = l.positions();
            for (int q = 0; q < n; q++) {
                float y = (p[q * 12 + 1] + p[q * 12 + 4] + p[q * 12 + 7] + p[q * 12 + 10]) * 0.25F;
                float z = (p[q * 12 + 2] + p[q * 12 + 5] + p[q * 12 + 8] + p[q * 12 + 11]) * 0.25F;
                behind[k][q] = z * COS_P - y * SIN_P > CAM_DIST;
            }
        }
    }

    private static void submit(GuiGraphics g, float cx, float feetY, float unit, boolean front) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f m = g.pose().last().pose();
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, EphemeraRenderer.atlas(mc));
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        try {
            // 半透明的托底先画，加法的光再叠上去；同一批里离镜头远的先画
            draw(m, STAGING.layer(EphemeraStaging.TRANSLUCENT), behind[EphemeraStaging.TRANSLUCENT], front, cx, feetY, unit,
                    KuvaParticleShaders.soft());
            draw(m, STAGING.layer(EphemeraStaging.ADDITIVE), behind[EphemeraStaging.ADDITIVE], front, cx, feetY, unit,
                    KuvaParticleShaders.additive());
        } finally {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    private static void draw(Matrix4f m, EphemeraStaging.Layer l, boolean[] back, boolean front, float cx, float feetY, float unit,
                             ShaderInstance shader) {
        int n = l.quads();
        if (n == 0) {
            return;
        }
        int drawn = 0;
        for (int q = 0; q < n; q++) {
            if (back[q] != front) {
                drawn++;
            }
        }
        if (drawn == 0) {
            return;
        }
        ShaderInstance s = shader != null ? shader : GameRenderer.getParticleShader();
        RenderSystem.setShader(() -> s);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        float[] p = l.positions();
        int[] c = l.colors();
        float[] uv = l.uvs();
        for (int q = 0; q < n; q++) {
            if (back[q] == front) {
                continue;
            }
            for (int v = q * 4; v < q * 4 + 4; v++) {
                int argb = c[v];
                // 竖直方向除以 cos 补回俯视压扁的那点，让身高方向和人像对齐
                float up = (p[v * 3 + 1] * COS_P + p[v * 3 + 2] * SIN_P) / COS_P;
                float sx = cx - p[v * 3] * unit, sy = feetY - (up + midY) * unit;
                bb.vertex(m, sx, sy, 0).uv(uv[v * 2], uv[v * 2 + 1])
                        .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24).uv2(0xF000F0).endVertex();
            }
        }
        BufferUploader.drawWithShader(bb.end());
    }

    /** 站在原点、面朝屏幕不动的假宿主 */
    private static final class FigureHost implements EphemeraHost {
        float w = VANILLA_WIDTH, h = 1.8F;

        @Override
        public int id() {
            return -1;
        }

        @Override
        public double x() {
            return 0;
        }

        @Override
        public double y() {
            return 0;
        }

        @Override
        public double z() {
            return 0;
        }

        // 朝 -z（镜头那边）
        @Override
        public float bodyYaw() {
            return 180;
        }

        @Override
        public float width() {
            return w;
        }

        @Override
        public float height() {
            return h;
        }

        @Override
        public double vx() {
            return 0;
        }

        @Override
        public double vy() {
            return 0;
        }

        @Override
        public double vz() {
            return 0;
        }

        @Override
        public Pose pose() {
            return Pose.STAND;
        }

        @Override
        public boolean onGround() {
            return true;
        }

        @Override
        public double groundY() {
            return 0;
        }

        @Override
        public boolean riding() {
            return false;
        }

        @Override
        public float swing() {
            return 0;
        }

        @Override
        public float hurt() {
            return 0;
        }

        @Override
        public boolean self() {
            return false;
        }
    }
}
