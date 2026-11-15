package pers.roinflam.kuvalich.client.ephemera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaParticleShaders;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraStateTable;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 幻纹渲染：{@code AFTER_PARTICLES} 阶段自画，只遍历状态表里登记过的实体，不走玩家渲染层
 * （换了玩家模型的模组下渲染层不一定被调用）。
 *
 * <p>每帧：按距离、视锥、隐身 / 旁观、开关筛一遍，自己的排最前、其余由近到远，取前 {@code ephemera_max_visible} 个；
 * 每个按完整 / 精简档的上限裁过以后并进两批，半透明一批、加法一批各提交一次。24 格外、或开关选了精简的一律精简档。
 * 第一人称看自己只画脚下和身后，贴近镜头的淡出；潜行压到三成亮。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class EphemeraRenderer {

    private static final float NEAR_FADE = 1.2F;
    private static final float SNEAK_ALPHA = 0.3F;
    /** 太久没画到的状态丢掉 */
    private static final long STALE_MS = 3000L;
    private static final int STAT_FRAMES = 120;

    private static final Int2ObjectOpenHashMap<EphemeraInstance> INSTANCES = new Int2ObjectOpenHashMap<>();
    private static final IntArrayList IDS = new IntArrayList();
    private static final EphemeraStaging STAGING = new EphemeraStaging();
    private static final EphemeraDraw DRAW = new EphemeraDraw(STAGING);
    private static final EntityHost HOST = new EntityHost();

    private static LivingEntity[] candidates = new LivingEntity[32];
    private static double[] candDist = new double[32];
    private static boolean[] candSelf = new boolean[32];
    private static Integer[] order = new Integer[32];

    private static int seenRevision = -1;
    private static boolean failedOnce;

    private static final long[] FRAME_NS = new long[STAT_FRAMES];
    private static int frameCursor;
    private static int frameCount;
    private static int lastDrawn;
    private static int lastQuads;
    private static int lastDropped;

    private static RenderType typeAdd;
    private static RenderType typeTranslucent;

    private EphemeraRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        EphemeraStateTable table = EphemeraStateTable.CLIENT;
        if (table.revision() != seenRevision) {
            seenRevision = table.revision();
            dropChanged(table);
        }
        EffectsDisplayConfig.EphemeraMode mode = EffectsDisplayConfig.ephemera();
        Minecraft mc = Minecraft.getInstance();
        if (mode == EffectsDisplayConfig.EphemeraMode.OFF || table.isEmpty() || mc.level == null) {
            lastDrawn = 0;
            lastQuads = 0;
            return;
        }
        long t0 = System.nanoTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Frustum frustum = event.getFrustum();
        float pt = event.getPartialTick();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        double maxDist = EffectsDisplayConfig.ephemeraDistance();
        double maxDist2 = maxDist * maxDist;

        int n = 0;
        table.ids(IDS);
        for (int k = 0, size = IDS.size(); k < size; k++) {
            Entity e = mc.level.getEntity(IDS.getInt(k));
            if (!(e instanceof LivingEntity le) || le.isRemoved() || le.isDeadOrDying() || le.isSpectator()) {
                continue;
            }
            boolean self = e == mc.player;
            if (mode == EffectsDisplayConfig.EphemeraMode.SELF && !self) {
                continue;
            }
            if (mc.player != null && le.isInvisibleTo(mc.player)) {
                continue;
            }
            double d2 = le.distanceToSqr(cam);
            if (d2 > maxDist2) {
                continue;
            }
            EphemeraLook look = table.get(le.getId());
            EphemeraStyle style = EphemeraStyles.of(look.type());
            double grow = style.extent() * look.scale() * Math.max(1.0F, le.getBbHeight() / EphemeraHost.Pose.STAND.vanillaHeight);
            if (frustum != null && !frustum.isVisible(le.getBoundingBox().inflate(grow, grow + Math.abs(look.yOffset()), grow))) {
                continue;
            }
            if (n == candidates.length) {
                grow();
            }
            candidates[n] = le;
            candDist[n] = d2;
            candSelf[n] = self;
            order[n] = n;
            n++;
        }
        if (n == 0) {
            lastDrawn = 0;
            lastQuads = 0;
            record(System.nanoTime() - t0);
            return;
        }
        Arrays.sort(order, 0, n, (a, b) -> candSelf[a] != candSelf[b] ? (candSelf[a] ? -1 : 1)
                : Double.compare(candDist[a], candDist[b]));
        int limit = Math.min(n, EffectsDisplayConfig.ephemeraMaxVisible());

        long now = KuvaFxDirector.clockMs();
        EphemeraDraw d = DRAW;
        d.camX = cam.x;
        d.camY = cam.y;
        d.camZ = cam.z;
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        d.rightX = -left.x();
        d.rightY = -left.y();
        d.rightZ = -left.z();
        d.upX = up.x();
        d.upY = up.y();
        d.upZ = up.z();
        d.timeMs = now;
        d.tier = EphemeraTier.CONVERGED;
        d.emitScale = EffectsDisplayConfig.intensity().budgetScale;
        float daylight = (mc.level.getSkyDarken(pt) - 0.2F) / 0.8F;
        boolean additive = EffectsDisplayConfig.additiveGlow() && KuvaParticleShaders.geometry() != null;

        STAGING.clearFrame();
        STAGING.additiveAllowed = additive;
        int kept = 0;
        int dropped = 0;
        for (int i = 0; i < limit; i++) {
            int idx = order[i];
            LivingEntity le = candidates[idx];
            boolean self = candSelf[idx];
            EphemeraLook look = table.get(le.getId());
            EphemeraInstance inst = INSTANCES.get(le.getId());
            if (inst == null || !inst.look.equals(look)) {
                inst = new EphemeraInstance(le.getId(), look, EphemeraStyles.of(look.type()));
                INSTANCES.put(le.getId(), inst);
            }
            EphemeraHost host = HOST.bind(le, pt, self);
            inst.update(host, now, d.tier);
            inst.lastDrawnMs = now;

            d.look = look;
            d.full = mode != EffectsDisplayConfig.EphemeraMode.REDUCED
                    && (self || candDist[idx] <= EphemeraTier.REDUCED_BEYOND * EphemeraTier.REDUCED_BEYOND);
            d.firstPersonSelf = self && firstPerson;
            BlockPos at = le.blockPosition();
            d.glowAlpha = EphemeraTier.glowAlpha(daylight, mc.level.getBiome(at).value().coldEnoughToSnow(at));
            STAGING.nearFade = d.firstPersonSelf ? NEAR_FADE : 0.0F;
            float dist = (float) Math.sqrt(candDist[idx]);
            float farFade = Math.min(1.0F, Math.max(0.0F, ((float) maxDist - dist) / 4.0F));
            STAGING.alphaMul = farFade * (le.isCrouching() ? SNEAK_ALPHA : 1.0F);
            try {
                inst.style.draw(inst, host, d);
            } catch (RuntimeException ex) {
                STAGING.discardPending();
                if (!failedOnce) {
                    failedOnce = true;
                    LogUtil.error("幻纹画法出错：" + look.type(), ex);
                }
                continue;
            }
            kept += STAGING.commit(d.full ? EphemeraTier.CAP_FULL : EphemeraTier.CAP_REDUCED);
            dropped += STAGING.lastDropped();
        }
        Arrays.fill(candidates, 0, n, null);

        submit(event.getPoseStack(), mc, additive);
        dropStale(now);
        lastDrawn = limit;
        lastQuads = kept;
        lastDropped = dropped;
        record(System.nanoTime() - t0);
    }

    private static void submit(PoseStack poseStack, Minecraft mc, boolean additive) {
        EphemeraStaging.Layer trans = STAGING.layer(EphemeraStaging.TRANSLUCENT);
        EphemeraStaging.Layer add = STAGING.layer(EphemeraStaging.ADDITIVE);
        if (trans.quads() == 0 && add.quads() == 0) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f m = poseStack.last().pose();
        // 同 KuvaStateFx：粒子阶段全局模型视图矩阵里可能已经乘了相机旋转，临时换成单位阵，免得转两次
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            flush(buffers, typeTranslucent(), trans, m);
            if (additive) {
                flush(buffers, typeAdd(), add, m);
            }
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void flush(MultiBufferSource.BufferSource buffers, RenderType type, EphemeraStaging.Layer l, Matrix4f m) {
        int quads = l.quads();
        if (quads == 0) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(type);
        float[] p = l.positions();
        int[] c = l.colors();
        for (int v = 0, verts = quads * 4; v < verts; v++) {
            int argb = c[v];
            vc.vertex(m, p[v * 3], p[v * 3 + 1], p[v * 3 + 2])
                    .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24)
                    .endVertex();
        }
        buffers.endBatch(type);
    }

    private static void grow() {
        int cap = candidates.length * 2;
        candidates = Arrays.copyOf(candidates, cap);
        candDist = Arrays.copyOf(candDist, cap);
        candSelf = Arrays.copyOf(candSelf, cap);
        order = Arrays.copyOf(order, cap);
    }

    /** 表里卸下、换款、改了颜色的，丢掉留着的状态 */
    private static void dropChanged(EphemeraStateTable table) {
        INSTANCES.int2ObjectEntrySet().removeIf(en -> !table.get(en.getIntKey()).equals(en.getValue().look));
    }

    private static void dropStale(long now) {
        if (INSTANCES.size() > 0) {
            INSTANCES.values().removeIf(inst -> now - inst.lastDrawnMs > STALE_MS);
        }
    }

    public static void clearAll() {
        EphemeraStateTable.CLIENT.clear();
        INSTANCES.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearAll();
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            EphemeraStateTable.CLIENT.remove(event.getEntity().getId());
            INSTANCES.remove(event.getEntity().getId());
        }
    }

    // ==================== 量耗时（开发命令读） ====================

    private static void record(long ns) {
        FRAME_NS[frameCursor] = ns;
        frameCursor = (frameCursor + 1) % STAT_FRAMES;
        frameCount = Math.min(STAT_FRAMES, frameCount + 1);
    }

    public static void resetStats() {
        frameCount = 0;
        frameCursor = 0;
    }

    /** 平均毫秒、最大毫秒、帧数、上一帧画了几个、几个四边形、砍掉几个 */
    public static String stats() {
        long sum = 0L;
        long max = 0L;
        for (int i = 0; i < frameCount; i++) {
            sum += FRAME_NS[i];
            max = Math.max(max, FRAME_NS[i]);
        }
        double avg = frameCount == 0 ? 0.0D : sum / (double) frameCount / 1.0E6D;
        return String.format("ephemera avg %.3f ms, max %.3f ms over %d frames; drawn %d, quads %d, trimmed %d, table %d, states %d",
                avg, max / 1.0E6D, frameCount, lastDrawn, lastQuads, lastDropped, EphemeraStateTable.CLIENT.size(),
                INSTANCES.size());
    }

    // ==================== 渲染状态 ====================

    private static RenderType typeAdd() {
        if (typeAdd == null) {
            typeAdd = Types.createAdditive();
        }
        return typeAdd;
    }

    private static RenderType typeTranslucent() {
        if (typeTranslucent == null) {
            typeTranslucent = Types.createTranslucent();
        }
        return typeTranslucent;
    }

    /** 借 RenderType 的子类拿到受保护的渲染状态；写法同 KuvaStateFx，光影包下加法批也照它的方式提交 */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        static RenderType createAdditive() {
            return RenderType.create("kuvalich_ephemera_add", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    8192, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(KuvaParticleShaders::geometry))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        static RenderType createTranslucent() {
            return RenderType.create("kuvalich_ephemera_translucent", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 8192, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }
    }
}
