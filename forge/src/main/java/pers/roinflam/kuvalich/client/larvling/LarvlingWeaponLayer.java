package pers.roinflam.kuvalich.client.larvling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.client.mercy.MercyCamera;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.Locale;

/**
 * 幼体手里的那把赤毒武器：将来那只玄骸的武器，刷出来就定了（只画，不真的装备）
 *
 * <p>做法和玄骸手里的武器一样（见 {@code LichWeaponLayer}）：矩阵已经带着骨骼的转动，这里只平移到右前臂的枢轴（肘），
 * 再挪到拳心，照原版手持那一套摆好按「第三人称右手」画，各把武器自己的握持参数照样管用。
 * 奴仆骨架的右前臂叫 {@code RH}，拳头在肘下 5 格多一点、正对着肘。</p>
 *
 * <p>淡出到一半以下、处决特写要藏目标武器时不画，同玄骸。</p>
 */
@OnlyIn(Dist.CLIENT)
public class LarvlingWeaponLayer extends BlockAndItemGeoLayer<KuvaLarvlingEntity> {

    /** 挂刀的骨骼：右前臂 */
    static final String HAND_BONE = "RH";

    /**
     * 拳心离肘部枢轴多远（模型里的格，整只的缩放另外乘；x 朝右手外侧，y 朝上，z 朝前）。
     * 拳头在肘下 4.4～7.4 像素、左右前后正对着肘，取中间往上一点
     */
    static float gripX = 0.0F;
    static float gripY = -0.345F;
    static float gripZ = 0.0F;
    /** 绕拳心再转多少（度，依次绕 Z、Y、X）：小臂端平时刀尖顺着小臂往前、略往上挑，同玄骸世界里的摆法 */
    static float turnX = 70.0F;
    static float turnY = 0.0F;
    static float turnZ = 0.0F;
    /** 整只缩到 0.75，刀乘回 1.25：实际大小 0.9375，和玩家手里拿着的一样 */
    static float scale = 1.25F;

    private static ItemStack[] stacks = new ItemStack[0];

    public LarvlingWeaponLayer(GeoRenderer<KuvaLarvlingEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, KuvaLarvlingEntity animatable) {
        return HAND_BONE.equals(bone.getName()) ? stackOf(animatable.getWeaponIndex()) : null;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, KuvaLarvlingEntity animatable) {
        return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Override
    public void renderForBone(PoseStack poseStack, KuvaLarvlingEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!HAND_BONE.equals(bone.getName())) {
            return;
        }
        ItemStack stack = stackOf(animatable.getWeaponIndex());
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
        poseStack.translate(gripX, gripY, gripZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(turnZ));
        poseStack.mulPose(Axis.YP.rotationDegrees(turnY));
        poseStack.mulPose(Axis.XP.rotationDegrees(turnX));
        // 原版手持的原点在拳心往手臂末端 1 像素、往前 2 像素，跟着刀一起缩放
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

    /** 武器下标对应的物品堆（每种只建一个，渲染时不分配）；下标无效返回 null */
    @Nullable
    static ItemStack stackOf(int index) {
        int n = KuvaLichItems.KUVA_WEAPONS.size();
        if (index < 0 || index >= n) {
            return null;
        }
        if (stacks.length != n) {
            stacks = new ItemStack[n];
        }
        ItemStack s = stacks[index];
        if (s == null) {
            s = new ItemStack(KuvaLichItems.KUVA_WEAPONS.get(index));
            stacks[index] = s;
        }
        return s;
    }

    /** 开发命令现场调握法 */
    static void tune(float ox, float oy, float oz, float rx, float ry, float rz, float s) {
        gripX = ox;
        gripY = oy;
        gripZ = oz;
        turnX = rx;
        turnY = ry;
        turnZ = rz;
        scale = s;
    }

    static String describe() {
        return String.format(Locale.ROOT, "grip %.3f %.3f %.3f turn %.1f %.1f %.1f scale %.2f",
                gripX, gripY, gripZ, turnX, turnY, turnZ, scale);
    }
}
