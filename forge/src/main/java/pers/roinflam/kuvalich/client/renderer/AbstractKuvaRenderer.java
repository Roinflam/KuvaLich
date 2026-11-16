package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.ElementTint;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.lich.LichNameplates;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.client.mercy.MercyPrompt;
import pers.roinflam.kuvalich.mercy.MercyState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 玄骸和奴仆渲染器的共同部分
 *
 * <ul>
 *   <li><b>淡入淡出</b>：每帧先算一次本体透明度（{@link LichRenderState}），小于 1 时本体换成半透明渲染类型、颜色带上 α；
 *       α &lt; 0.9 时把 {@code helmet} 骨骼藏起来（半透明下头盔里面的面会透出来），画完复原，
 *       因为烘焙好的模型是所有同类实体（连肖像）共用的。</li>
 *   <li><b>颜色</b>：元素状态的整只染色（{@link ElementTint}）、消散晶化时往深赤拉（{@link LichFx#crystallize}），
 *       都乘在顶点色上；什么都没有时直接用白色，不新建颜色对象。</li>
 *   <li><b>死亡</b>：不翻倒，也不整只泛红（活着时受伤的红闪照旧，致命那一下不闪），离场的样子全交给消散演出。
 *       消散包万一没到、实体死了 2 s 还在，才自己慢慢淡掉。</li>
 *   <li><b>名牌</b>：没被命名牌改过名的不画原版名牌，{@code super.render} 之后交给 {@link LichNameplates}。</li>
 *   <li><b>踉跄</b>：判错挣脱前每帧在水平方向随机抖一点（{@link LichFx#staggerAmplitude}）。</li>
 *   <li><b>外壳</b>：描边层要把模型往外撑一圈再画一遍，{@link #setHull} 设了厚度后每个立方体各自往外撑这么多。
 *       不用整体按中心放大：那样头顶、脚底的边很粗，躯干和手臂几乎没有边。
 *       每个立方体各有一圈壳，前面的小块（面甲、胸甲、腿甲）那圈壳会压在后面的本体上，身上画出一道道线；
 *       所以壳的顶点再沿视线往后推一段（从相机出发的射线方向，屏幕上的位置不变，只是更远），
 *       后面不远处有本体的地方就被挡住，只剩真正的外轮廓。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public abstract class AbstractKuvaRenderer<T extends AbstractKuva> extends GeoEntityRenderer<T> {

    /** 透明度低于它时藏头盔 */
    public static final float HIDE_HELMET_BELOW = 0.9F;
    /** 晶化到底时本体顶点色往这个颜色拉 */
    private static final float CRYSTAL_G = 0.55F;
    private static final float CRYSTAL_B = 0.6F;
    /** 死亡兜底淡出：从第几 tick 开始、到第几 tick 淡完（正常情况下消散演出早就接手了） */
    private static final float STUCK_DEATH_FADE_FROM = 40.0F;
    private static final float STUCK_DEATH_FADE_TO = 56.0F;
    /** 站位最远 1.25 格、玄骸半宽 0.875，交还时眼睛离包围盒约 0.4 格，留点余量 */
    private static final double UP_CLOSE = 0.6D;

    /** 这一帧本体的透明度、元素染色（−1 = 没有）、晶化程度，render 开头算好，后面各处读 */
    protected float frameAlpha = 1.0F;
    protected int frameTint = -1;
    protected float frameCrystal;

    private float hullInflate;
    private float hullPush;
    @Nullable
    private BakedGeoModel helmetOf;
    @Nullable
    private GeoBone helmet;
    private boolean helmetHiddenByUs;

    private final Vector4f scratchPos = new Vector4f();
    private final Vector3f scratchNormal = new Vector3f();
    /** 撑壳用的立方体缓存：烘好的模型不会变，重载资源后旧立方体没人引用了，条目跟着回收 */
    private final Map<GeoCube, HullCube> hullCache = new WeakHashMap<>();

    // 名牌跟着姿势走：画本体时顺手记下脖子（头骨的支点）在站姿时和此刻各在哪
    @Nullable
    private BakedGeoModel headOf;
    @Nullable
    private GeoBone head;
    private boolean neckRestSeen;
    private boolean neckSeen;
    private final Vector3f neckRest = new Vector3f();
    private final Vector3f neckNow = new Vector3f();
    /** 世界的「上」在这一帧实体坐标里的方向 */
    private float upX;
    private float upY;
    private float upZ;

    // 倒地圈跟着姿势走：倒地时画本体顺手记下锚点（挂在某根骨骼上的一个模型点）此刻在哪，交给 MercyPrompt
    @Nullable
    private String anchorBoneName;
    private float anchorX;
    private float anchorY;
    private float anchorZ;
    @Nullable
    private BakedGeoModel anchorOf;
    @Nullable
    private GeoBone anchorBone;
    private boolean anchorWanted;
    private boolean anchorSeen;
    private final Vector3f anchorNow = new Vector3f();
    /** 这一帧实体原点的矩阵（相机的转动 + 平移），把记下的点换回世界方向 */
    private final Matrix4f origin = new Matrix4f();

    protected AbstractKuvaRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    /**
     * 倒地圈的锚点：挂在 {@code bone} 上、模型坐标（像素，和几何文件一样）{@code (x, y, z)} 的那一点，跟着骨骼的转动和位移走
     */
    protected void setMercyAnchor(String bone, float x, float y, float z) {
        this.anchorBoneName = bone;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    /** 这一帧本体的透明度（层要跟着淡） */
    public float frameAlpha() {
        return frameAlpha;
    }

    /**
     * 描边层用：{@code inflate} 大于 0 时每个立方体各自往外撑这么多格再画，顶点再沿视线往后推 {@code push} 格；
     * 画完两个都设回 0。只在世界里（相机在原点的透视画法）推，界面里的正交画法推了会挪位置，传 0
     */
    public void setHull(float inflate, float push) {
        this.hullInflate = inflate;
        this.hullPush = push;
    }

    /** 本体此刻的透明度：淡入淡出状态，再加上死了很久还没被删时的兜底淡出 */
    public static float bodyAlpha(AbstractKuva entity, float partialTick) {
        float a = LichRenderState.alpha(entity, partialTick);
        if (entity.deathTime > 0) {
            float t = entity.deathTime + partialTick;
            if (t > STUCK_DEATH_FADE_FROM && !LichFx.isDissolving(entity.getId())) {
                float x = Mth.clamp((t - STUCK_DEATH_FADE_FROM) / (STUCK_DEATH_FADE_TO - STUCK_DEATH_FADE_FROM), 0.0F, 1.0F);
                a = Math.min(a, 1.0F - x * x * (3.0F - 2.0F * x));
            }
        }
        return a;
    }

    /**
     * 处决成功交还第一人称时，处决者还站在它收尾时两臂环着他的位置上，它的晶化碎裂还要演半秒多，镜头整个裹在它的模型里，
     * 满屏是红的。这段消散里第一人称镜头离它的包围盒不到 {@link #UP_CLOSE} 格就不画本体；第三人称、旁观者照常看得到
     */
    private static boolean upCloseDissolve(AbstractKuva entity) {
        if (!LichFx.inRequiemDissolve(entity.getId())) {
            return false;
        }
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return !cam.isDetached() && entity.getBoundingBox().inflate(UP_CLOSE).contains(cam.getPosition());
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        this.frameAlpha = bodyAlpha(entity, partialTick);
        if (this.frameAlpha > 0.003F && entity.getId() >= 0 && upCloseDissolve(entity)) {
            this.frameAlpha = 0.0F;
        }
        this.frameTint = ElementTint.tintRgb(entity);
        this.frameCrystal = LichFx.crystallize(entity, partialTick);
        // 实体坐标只带相机的转动和平移、没有缩放，矩阵第二列就是世界朝上的方向
        Matrix4f origin = poseStack.last().pose();
        this.upX = origin.m10();
        this.upY = origin.m11();
        this.upZ = origin.m12();
        this.neckRestSeen = false;
        this.neckSeen = false;
        this.origin.set(origin);
        this.anchorSeen = false;
        this.anchorWanted = this.anchorBoneName != null && entity.getId() >= 0
                && entity.getMercyState() == MercyState.DOWNED;

        if (this.frameAlpha > 0.003F) {
            float shake = LichFx.staggerAmplitude(entity);
            if (shake > 0.0F) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                poseStack.pushPose();
                poseStack.translate((r.nextFloat() * 2.0F - 1.0F) * shake, 0.0F, (r.nextFloat() * 2.0F - 1.0F) * shake);
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                poseStack.popPose();
            } else {
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            }
        }

        if (this.anchorSeen) {
            // 记下的点减去实体原点，再用相机转动的逆（正交矩阵 = 转置）换回世界方向
            Matrix4f o = this.origin;
            float dx = this.anchorNow.x - o.m30(), dy = this.anchorNow.y - o.m31(), dz = this.anchorNow.z - o.m32();
            MercyPrompt.noteAnchor(entity.getId(),
                    dx * o.m00() + dy * o.m01() + dz * o.m02(),
                    dx * o.m10() + dy * o.m11() + dz * o.m12(),
                    dx * o.m20() + dy * o.m21() + dz * o.m22());
        }

        // 肖像里的假实体（负数 id）不画名牌
        if (entity.getId() >= 0) {
            LichNameplates.render(entity, poseStack, bufferSource, packedLight, partialTick, headDrop());
        }
    }

    /**
     * 这一帧脖子比站姿时低了多少格（倒地、跪下、被按倒时是正数），这一帧没画本体时为 {@link Float#NaN}
     *
     * <p>两个点都在 {@link #renderRecursively} 里记：站姿的点是模型原点处、所有骨骼都没动时头骨支点的位置，
     * 此刻的点是父骨骼都摆好以后头骨支点的位置（头自己的转动绕这一点转，不挪它），两点之差投到世界的「上」。</p>
     */
    private float headDrop() {
        if (!this.neckRestSeen || !this.neckSeen) {
            return Float.NaN;
        }
        return this.upX * (this.neckRest.x - this.neckNow.x) + this.upY * (this.neckRest.y - this.neckNow.y)
                + this.upZ * (this.neckRest.z - this.neckNow.z);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, float red, float green,
                                  float blue, float alpha) {
        GeoBone h = this.head;
        if (!isReRender && h != null) {
            if (!this.neckRestSeen && bone.getParent() == null) {
                // 顶层骨骼进来时坐标还在模型原点：骨骼都没动时头骨支点就在这里
                poseStack.last().pose().transformPosition(h.getPivotX() / 16.0F, h.getPivotY() / 16.0F,
                        h.getPivotZ() / 16.0F, this.neckRest);
                this.neckRestSeen = true;
            }
            if (bone == h) {
                // 父骨骼已经摆好，再加上头骨自己的位移（和 GeckoLib 摆骨骼的顺序、符号一致）
                poseStack.last().pose().transformPosition((h.getPivotX() - h.getPosX()) / 16.0F,
                        (h.getPivotY() + h.getPosY()) / 16.0F, (h.getPivotZ() + h.getPosZ()) / 16.0F, this.neckNow);
                this.neckSeen = true;
            }
        }
        if (!isReRender && this.anchorWanted && bone == this.anchorBone) {
            // 父骨骼已经摆好，再按 GeckoLib 的顺序摆上这根骨骼自己的位移和转动，锚点就在它上面
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, bone);
            poseStack.last().pose().transformPosition(-this.anchorX / 16.0F, this.anchorY / 16.0F,
                    this.anchorZ / 16.0F, this.anchorNow);
            poseStack.popPose();
            this.anchorSeen = true;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public Color getRenderColor(T animatable, float partialTick, int packedLight) {
        Color base = super.getRenderColor(animatable, partialTick, packedLight);
        if (this.frameTint == -1 && this.frameCrystal <= 0.0F && this.frameAlpha >= 1.0F) {
            return base;
        }
        float r = base.getRedFloat();
        float g = base.getGreenFloat();
        float b = base.getBlueFloat();
        if (this.frameTint != -1) {
            r *= ((this.frameTint >> 16) & 0xFF) / 255.0F;
            g *= ((this.frameTint >> 8) & 0xFF) / 255.0F;
            b *= (this.frameTint & 0xFF) / 255.0F;
        }
        if (this.frameCrystal > 0.0F) {
            g *= 1.0F + (CRYSTAL_G - 1.0F) * this.frameCrystal;
            b *= 1.0F + (CRYSTAL_B - 1.0F) * this.frameCrystal;
        }
        return Color.ofRGBA(r, g, b, base.getAlphaFloat() * Mth.clamp(this.frameAlpha, 0.0F, 1.0F));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick) {
        if (this.frameAlpha < 1.0F) {
            return RenderType.entityTranslucent(texture);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    /**
     * 活着时受伤照常红闪；死了不红：原版死亡期间整只泛红，和消散时往深赤拉的颜色打架。
     * 致命那一下的受伤红闪也不画，否则倒下后头 10 tick 还是整只通红
     */
    @Override
    public int getPackedOverlay(T animatable, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u),
                OverlayTexture.v(animatable.hurtTime > 0 && !animatable.isDeadOrDying()));
    }

    /** 死亡不翻倒，站着化掉 */
    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0F;
    }

    /** 被命名牌改过名的照原版画名字；其余的名牌由 {@link LichNameplates} 自己画 */
    @Override
    public boolean shouldShowName(T animatable) {
        if (!animatable.hasCustomName()) {
            return false;
        }
        return super.shouldShowName(animatable);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
        if (!isReRender) {
            if (model != this.headOf) {
                this.headOf = model;
                this.head = model.getBone("Head").orElse(null);
            }
            if (model != this.anchorOf) {
                this.anchorOf = model;
                this.anchorBone = this.anchorBoneName == null ? null : model.getBone(this.anchorBoneName).orElse(null);
            }
            GeoBone h = helmetBone(model);
            if (h != null && this.frameAlpha < HIDE_HELMET_BELOW && !h.isHidden()) {
                h.setHidden(true);
                this.helmetHiddenByUs = true;
            }
        }
    }

    @Override
    public void postRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        super.postRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
        if (!isReRender && this.helmetHiddenByUs) {
            // 烘焙模型是共用的，藏了就得在这一只画完时放回去
            if (this.helmet != null) {
                this.helmet.setHidden(false);
            }
            this.helmetHiddenByUs = false;
        }
    }

    @Nullable
    private GeoBone helmetBone(BakedGeoModel model) {
        if (model != this.helmetOf) {
            this.helmetOf = model;
            this.helmet = model.getBone("helmet").orElse(null);
        }
        return this.helmet;
    }

    @Override
    public void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        if (this.hullInflate <= 0.0F) {
            super.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }
        renderHullCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha, this.hullInflate,
                this.hullPush);
    }

    /**
     * 把立方体每个顶点沿它所在的三个轴各往外推 {@code t} 格再画：盒子各面都均匀撑大 {@code t}，转角接得上。
     * 片状立方体（某个轴厚度为 0）在那个轴上按面的朝向分开，两片各退 {@code t}，免得撑出来的壳和原片重合。
     * {@code push} 大于 0 时变换后的顶点再沿相机到它的射线往远处推这么多格。
     * 往哪边推、顶点按什么顺序发只和模型有关，按立方体算一次存在 {@link #hullCache} 里，每帧只做变换
     */
    private void renderHullCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight,
                                int packedOverlay, float red, float green, float blue, float alpha, float t, float push) {
        HullCube hc = this.hullCache.computeIfAbsent(cube, AbstractKuvaRenderer::bakeHull);
        if (hc.quadCount == 0) {
            return;
        }
        RenderUtils.translateToPivotPoint(poseStack, cube);
        RenderUtils.rotateMatrixAroundCube(poseStack, cube);
        RenderUtils.translateAwayFromPivotPoint(poseStack, cube);

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();
        float[] pos = hc.pos;
        float[] dir = hc.dir;
        float[] uv = hc.uv;
        int i = 0;
        for (int q = 0; q < hc.quadCount; q++) {
            this.scratchNormal.set(hc.normal[q * 3], hc.normal[q * 3 + 1], hc.normal[q * 3 + 2]);
            normalMat.transform(this.scratchNormal);
            int end = hc.quadEnd[q];
            for (; i < end; i++) {
                int j = i * 3;
                this.scratchPos.set(pos[j] + dir[j] * t, pos[j + 1] + dir[j + 1] * t, pos[j + 2] + dir[j + 2] * t, 1.0F);
                pose.transform(this.scratchPos);
                if (push > 0.0F) {
                    // 实体渲染时姿态矩阵的原点就是相机，沿原点到顶点的方向放大：投影后的屏幕位置不变，深度变远
                    float x = this.scratchPos.x(), y = this.scratchPos.y(), z = this.scratchPos.z();
                    float len = Mth.sqrt(x * x + y * y + z * z);
                    if (len > 1.0E-4F) {
                        float stretch = (len + push) / len;
                        this.scratchPos.set(x * stretch, y * stretch, z * stretch, 1.0F);
                    }
                }
                buffer.vertex(this.scratchPos.x(), this.scratchPos.y(), this.scratchPos.z(), red, green, blue, alpha,
                        uv[i * 2], uv[i * 2 + 1], packedOverlay, packedLight,
                        this.scratchNormal.x(), this.scratchNormal.y(), this.scratchNormal.z());
            }
        }
    }

    /**
     * 一个立方体撑壳要用的、只和模型有关的量：按发出顺序排好的顶点（模型坐标）、每个顶点往外推的方向（各轴 ±1 或 0）、
     * 贴图坐标，以及每个面的原始法线和它的最后一个顶点在哪
     */
    private record HullCube(int quadCount, int[] quadEnd, float[] pos, float[] dir, float[] uv, float[] normal) {
    }

    private static HullCube bakeHull(GeoCube cube) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        GeoQuad[] quads = cube.quads();
        int quadCount = 0;
        int vertexCount = 0;
        for (GeoQuad quad : quads) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex v : quad.vertices()) {
                Vector3f p = v.position();
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
                maxZ = Math.max(maxZ, p.z());
            }
            if (quad.vertices().length >= 3) {
                quadCount++;
                vertexCount += quad.vertices().length;
            }
        }
        if (minX > maxX || quadCount == 0) {
            return new HullCube(0, new int[0], new float[0], new float[0], new float[0], new float[0]);
        }
        float cx = (minX + maxX) * 0.5F, cy = (minY + maxY) * 0.5F, cz = (minZ + maxZ) * 0.5F;
        boolean flatX = maxX - minX < 1.0E-4F, flatY = maxY - minY < 1.0E-4F, flatZ = maxZ - minZ < 1.0E-4F;

        int[] quadEnd = new int[quadCount];
        float[] pos = new float[vertexCount * 3];
        float[] dir = new float[vertexCount * 3];
        float[] uv = new float[vertexCount * 2];
        float[] normal = new float[quadCount * 3];
        int q = 0;
        int i = 0;
        for (GeoQuad quad : quads) {
            if (quad == null) {
                continue;
            }
            GeoVertex[] vs = quad.vertices();
            if (vs.length < 3) {
                continue;
            }
            // 绕向算出来的法线：外壳只留背面，靠的是每个面都按「从外面看是逆时针」排；
            // 模型里尺寸写成负数的立方体整个是翻过来的，这里发现朝里就倒着发顶点
            Vector3f a = vs[0].position(), b = vs[1].position(), c = vs[2].position();
            float e1x = b.x() - a.x(), e1y = b.y() - a.y(), e1z = b.z() - a.z();
            float e2x = c.x() - b.x(), e2y = c.y() - b.y(), e2z = c.z() - b.z();
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            float qx = 0.0F, qy = 0.0F, qz = 0.0F;
            for (GeoVertex v : vs) {
                qx += v.position().x();
                qy += v.position().y();
                qz += v.position().z();
            }
            float inv = 1.0F / vs.length;
            float out = (qx * inv - cx) * nx + (qy * inv - cy) * ny + (qz * inv - cz) * nz;
            boolean reverse = out < 0.0F;
            // 片状轴按（排正以后的）绕向朝外推；不用 quad.normal()，镜像、负尺寸的立方体那里会反
            float s = reverse ? -1.0F : 1.0F;
            float wx = Math.signum(nx) * s, wy = Math.signum(ny) * s, wz = Math.signum(nz) * s;
            Vector3f n = quad.normal();
            normal[q * 3] = n.x();
            normal[q * 3 + 1] = n.y();
            normal[q * 3 + 2] = n.z();
            for (int k = 0; k < vs.length; k++, i++) {
                GeoVertex v = vs[reverse ? vs.length - 1 - k : k];
                Vector3f p = v.position();
                pos[i * 3] = p.x();
                pos[i * 3 + 1] = p.y();
                pos[i * 3 + 2] = p.z();
                dir[i * 3] = flatX ? wx : Math.signum(p.x() - cx);
                dir[i * 3 + 1] = flatY ? wy : Math.signum(p.y() - cy);
                dir[i * 3 + 2] = flatZ ? wz : Math.signum(p.z() - cz);
                uv[i * 2] = v.texU();
                uv[i * 2 + 1] = v.texV();
            }
            quadEnd[q++] = i;
        }
        return new HullCube(quadCount, quadEnd, pos, dir, uv, normal);
    }
}
