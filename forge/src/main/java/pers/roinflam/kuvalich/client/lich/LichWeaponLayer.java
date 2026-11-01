package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.mercy.MercyCamera;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * 玄骸手里的赤毒武器（只画出来，不真的装备，免得武器的攻击属性叠到怪身上）
 *
 * <p>挂在右前臂 {@code RArmF} 上，物品取 {@code KUVA_WEAPONS} 里这只玄骸的武器下标，下标无效就不画。
 * 世界里的玄骸、通讯肖像、档案里的模型都走这一层，所以肖像「一侧斜着露出手里的武器」不用另做。</p>
 *
 * <p>没用 GeckoLib 自带的 {@code renderForBone}：调到这里时矩阵已经带着骨骼自己的旋转，它又转一遍，
 * 前臂一动刀就歪出去。这里只平移到前臂的枢轴（肘），再挪到拳心。</p>
 *
 * <p>拳心往下照原版玩家手持那一套摆（{@code ItemInHandLayer}：先转到原版手臂的坐标、绕 X 转 -90、绕 Y 转 180，
 * 原点在手臂末端前沿），再按「第三人称右手」画。各把武器自己的握持参数是对着这一套调的，这样每一把的刀柄都落在
 * 拳心里，不用一把一把配。GeckoLib 的模型坐标和原版模型坐标差一个 x、y 同时取反（原版实体渲染多一次
 * {@code scale(-1, -1, 1)}），正好是绕 Z 转 180°。</p>
 *
 * <p>摆好以后再绕拳心转一下，按摆法分：世界里的玄骸刀尖顺着小臂往前，手臂怎么挥刀就怎么走；肖像和档案
 * （实体 id 为负）正对镜头，刀往前伸只看得到一个点，档案、指南的全身像垂在身侧、刀尖朝下往外撇；
 * 通讯窗的半身像只拍到胸口，刀头从肩膀外侧竖起来。</p>
 *
 * <p>玄骸正在淡出、透明度不到一半时不画（物品渲染没法跟着半透明，留着一把实心的刀悬在半空更怪）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class LichWeaponLayer extends BlockAndItemGeoLayer<KuvaMasterEntity> {

    /** 挂刀的骨骼：右前臂 */
    private static final String HAND_BONE = "RArmF";

    /** 摆法：世界里、档案和指南的全身像、通讯窗的半身像 */
    static final int WORLD = 0, PORTRAIT = 1, BUST = 2;
    /** 拳心离肘部枢轴多远（格；x 朝玄骸的右手外侧，y 朝上，z 朝前），三种摆法共用 */
    static float gripX = 0.10F;
    static float gripY = -0.625F;
    static float gripZ = 0.0F;
    /** 绕拳心再转多少（度，按原版手臂的坐标：x 往手臂前方转、z 往外侧甩；依次绕 Z、Y、X），按摆法分 */
    static final float[][] TURN = {
            // 玄骸站着时小臂是往前端平的，照玩家那样刀身垂直于小臂会竖在脸前；顺着小臂往前转，刀尖略往上挑
            {70.0F, 0.0F, 0.0F},
            // 刀身转到顺着手臂朝下，再往外撇开，整把刀露在腿边
            {80.0F, 0.0F, 35.0F},
            // 刀头顺着手臂朝上，从肩膀外侧冒出来
            {-100.0F, 0.0F, -15.0F}};
    /** 玄骸比玩家高一大截，刀跟着放大 */
    static float scale = 1.2F;
    /** 调试用：世界里的玄骸一律画这把（-1 不改） */
    static int forceWeapon = -1;

    private static ItemStack[] stacks = new ItemStack[0];

    public LichWeaponLayer(GeoRenderer<KuvaMasterEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, KuvaMasterEntity animatable) {
        return HAND_BONE.equals(bone.getName()) ? stackOf(animatable.getLichWeaponIndex()) : null;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, KuvaMasterEntity animatable) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    public void renderForBone(PoseStack poseStack, KuvaMasterEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!HAND_BONE.equals(bone.getName())) {
            return;
        }
        ItemStack stack = stackOf(forceWeapon >= 0 && animatable.getId() >= 0 ? forceWeapon
                : animatable.getLichWeaponIndex());
        if (stack == null) {
            return;
        }
        if (animatable.getId() >= 0 && LichRenderState.alpha(animatable, partialTick) < 0.5F) {
            return;
        }
        if (MercyCamera.hidesTargetWeapon(animatable.getId())) {
            return;
        }
        poseStack.pushPose();
        RenderUtils.translateToPivotPoint(poseStack, bone);
        int mode = animatable.getId() >= 0 ? WORLD : LichPuppet.drawingBust() ? BUST : PORTRAIT;
        float[] turn = TURN[mode];
        poseStack.translate(gripX, gripY, gripZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(turn[2]));
        poseStack.mulPose(Axis.YP.rotationDegrees(turn[1]));
        poseStack.mulPose(Axis.XP.rotationDegrees(turn[0]));
        // 原版手持的原点在拳心往手臂末端 1 像素、往前 2 像素，跟着刀一起放大
        poseStack.translate(0.0F, scale / 16.0F, -scale * 2.0F / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, bufferSource, animatable.level(),
                packedLight, packedOverlay, animatable.getId());
        poseStack.popPose();
        // 物品可能换过缓冲，接着画剩下的骨骼前把实体自己的那份要回来
        bufferSource.getBuffer(renderType);
    }

    /** 武器下标对应的物品；下标无效返回 null。每种武器只建一个物品堆，渲染时不分配 */
    private static ItemStack stackOf(int index) {
        int n = KuvaLichItems.KUVA_WEAPONS.size();
        if (index < 0 || index >= n) {
            return null;
        }
        if (stacks.length != n) {
            stacks = new ItemStack[n];
        }
        ItemStack s = stacks[index];
        if (s == null) {
            Item item = KuvaLichItems.KUVA_WEAPONS.get(index);
            s = new ItemStack(item);
            stacks[index] = s;
        }
        return s;
    }

    /** 调试用：现场改拳心位置和某一种摆法的转角（只在开发环境的命令里调）；拳心和缩放三种摆法共用 */
    static void tune(int mode, float ox, float oy, float oz, float rx, float ry, float rz, float s) {
        gripX = ox;
        gripY = oy;
        gripZ = oz;
        TURN[mode][0] = rx;
        TURN[mode][1] = ry;
        TURN[mode][2] = rz;
        scale = s;
    }

    static String describe(int mode) {
        float[] t = TURN[mode];
        return String.format(java.util.Locale.ROOT, "mode %d grip %.3f %.3f %.3f turn %.1f %.1f %.1f scale %.2f force %d",
                mode, gripX, gripY, gripZ, t[0], t[1], t[2], scale, forceWeapon);
    }
}
