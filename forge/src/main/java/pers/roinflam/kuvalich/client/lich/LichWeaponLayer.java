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
 * 前臂一动刀就歪出去。这里只平移到前臂的枢轴（肘），再挪到手心、摆好刀的朝向。</p>
 *
 * <p>世界里的玄骸握住刀柄、刀身从拳头往前伸，手臂怎么挥刀就怎么走。肖像和档案（实体 id 为负）正对镜头，
 * 刀往前伸只看得到一个点，所以改成贴着身侧竖着，刀头在肩膀外侧。</p>
 *
 * <p>玄骸正在淡出、透明度不到一半时不画（物品渲染没法跟着半透明，留着一把实心的刀悬在半空更怪）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class LichWeaponLayer extends BlockAndItemGeoLayer<KuvaMasterEntity> {

    /** 挂刀的骨骼：右前臂 */
    private static final String HAND_BONE = "RArmF";

    /** 从肘部枢轴到手心（格；x 朝玄骸的右手外侧，y 朝上，z 朝前） */
    static float offsetX = 0.04F;
    static float offsetY = -0.62F;
    static float offsetZ = 0.0F;
    /** 刀的朝向（度，依次绕 X、Y、Z 转）：-90 时刀身水平朝前，这里再往下压一点 */
    static float rotX = -68.0F;
    static float rotY = 0.0F;
    static float rotZ = 0.0F;
    /** 肖像和档案里的朝向 */
    private static final float PORTRAIT_ROT_X = -10.0F;
    private static final float PORTRAIT_ROT_Z = -12.0F;
    /** 玄骸比玩家高一大截，刀跟着放大 */
    static float scale = 1.2F;

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
        ItemStack stack = stackOf(animatable.getLichWeaponIndex());
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
        poseStack.translate(offsetX, offsetY, offsetZ);
        boolean portrait = animatable.getId() < 0;
        poseStack.mulPose(Axis.XP.rotationDegrees(portrait ? PORTRAIT_ROT_X : rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(portrait ? 0.0F : rotY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(portrait ? PORTRAIT_ROT_Z : rotZ));
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

    /** 调试用：现场改挂点（只在开发环境的命令里调） */
    static void tune(float ox, float oy, float oz, float rx, float ry, float rz, float s) {
        offsetX = ox;
        offsetY = oy;
        offsetZ = oz;
        rotX = rx;
        rotY = ry;
        rotZ = rz;
        scale = s;
    }

    static String describe() {
        return String.format(java.util.Locale.ROOT, "offset %.3f %.3f %.3f rot %.1f %.1f %.1f scale %.2f",
                offsetX, offsetY, offsetZ, rotX, rotY, rotZ, scale);
    }
}
