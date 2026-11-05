package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
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
 * <p><b>拳心和握法</b>：拳心取手那几块方块的中心。往下照原版玩家手持那一套摆（{@code ItemInHandLayer}：
 * 先转到原版手臂的坐标、绕 X 转 -90、绕 Y 转 180，原点在手臂末端前沿、比拳心往末端 2 像素、往前 2 像素），
 * 再按「第三人称右手」画。各把武器自己的握持参数是对着原版拳心调的（逐把核过：原版拳心都落在贴图的刀柄上），
 * 所以对齐了拳心，每一把的刀柄都穿过拳头，不用一把一把配。GeckoLib 的模型坐标和原版模型坐标差一个 x、y 同时取反
 * （原版实体渲染多一次 {@code scale(-1, -1, 1)}），正好是绕 Z 转 180°；前臂的方块在模型里自带往外撇的
 * {@link #FOREARM_TILT}，原版手臂是直的，这里再补上，刀柄才和拳头一个朝向（垂直于前臂、从拳眼穿出去）。</p>
 *
 * <p><b>按摆法只动人手做得到的</b>：手腕往小指那边压（尺偏，最多 40° 上下）、往拇指那边翘（桡偏，20° 上下）、
 * 前臂内外旋，以及武器绕自己的刀柄转（手里攥着柄，刀面朝哪都行）。手腕转到 70° 往上，刀柄就几乎顺着前臂、
 * 从拳头底下戳出来，长柄武器握在柄中段，另外半截会翻到上臂、肩膀旁边，看着像手腕折了。
 * 世界里手腕压 {@code 25°}，刀尖朝前略往下（垂手站着时长柄武器不插进地里，挥刀时刀顺着手臂甩出去）；
 * 立绘（实体 id 为负）的胳膊另外抬起来（{@link #PUPPET_ARM}、{@link #PUPPET_FOREARM}，在模型里叠），
 * 小臂端平、拳头在胸口右前方，刀竖在身侧、立在头盔旁边，刀面转向镜头。档案、指南的全身像和通讯窗的半身像
 * 用同一个胳膊姿势：同一只玄骸的两种立绘可能在同一帧里画（通讯窗在档案底下的 HUD 里），GeckoLib 同一帧
 * 不会为第二次画重算骨骼。</p>
 *
 * <p>玄骸正在淡出、透明度不到一半时不画（物品渲染没法跟着半透明，留着一把实心的刀悬在半空更怪）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class LichWeaponLayer extends BlockAndItemGeoLayer<KuvaMasterEntity> {

    /** 挂刀的骨骼：右前臂 */
    private static final String HAND_BONE = "RArmF";

    /** 摆法：世界里、档案和指南的全身像、通讯窗的半身像 */
    static final int WORLD = 0, PORTRAIT = 1, BUST = 2;
    /**
     * 拳心离肘部枢轴多远（格，GeckoLib 的骨骼坐标：x 朝玄骸的右手外侧，y 朝上，z 朝后），三种摆法共用。
     * 取 {@code kuva_master.geo.json} 里 RArmF 下面手的六块方块（旋转后）的包围盒中心 (-16.70, 21.5, 0.74)，
     * 减去枢轴 (-14.46, 31.525, 0.74)，x 取反、除以 16
     */
    static float gripX = 0.140F;
    static float gripY = -0.627F;
    static float gripZ = 0.0F;
    /** 前臂方块在模型里绕 z 转了 17.5°（往外撇），原版手臂是直的；补上它，刀柄才垂直于看得见的前臂 */
    private static final float FOREARM_TILT = 17.5F;
    /**
     * 按摆法分的握法（度）：{手腕, 前臂拧, 绕刀柄转}。
     * 手腕正 = 刀尖往前臂末端那边压（尺偏），负 = 往肘那边翘；前臂拧绕前臂自己转；最后一项绕刀柄转，只换刀面朝向
     */
    static final float[][] HOLD = {
            // 世界里：刀尖朝前略往下，挥刀时顺着手臂甩出去
            {25.0F, 0.0F, 0.0F},
            // 全身像：胳膊抬起来以后刀竖直立在身侧，刀面转向镜头；档案的模型往画面左边转了 20°，
            // 前臂往里拧一些刀才看着竖直，锤头、镰刀头这类往外伸的不会压到左栏
            {10.0F, -40.0F, 90.0F},
            // 半身像：同一个胳膊姿势、同一种握法，刀从肩膀外侧竖起来、立在头盔旁边。
            // 再往外倾就落进通讯窗两边压暗的渐变里，只剩个刀尖；再往里收会横过脸
            {10.0F, -40.0F, 90.0F}};
    /** 刀柄在原版手臂坐标里大致朝前、往手臂末端偏 12°（各把武器 10°~18°），绕刀柄转就绕这根轴 */
    private static final float HANDLE_AXIS_Y = Mth.sin(12.0F * Mth.DEG_TO_RAD);
    private static final float HANDLE_AXIS_Z = -Mth.cos(12.0F * Mth.DEG_TO_RAD);
    /**
     * 立绘里拿武器的上臂、前臂在动画之上叠的转角（度，照动画文件的写法：x 负 = 往前抬，z 正 = 往外张）。
     * 立绘正对镜头，垂着手刀只能朝前伸，看过去缩成一小截；把小臂端平，刀才能竖着立在身侧
     */
    public static final float[] PUPPET_ARM = {-12.0F, 0.0F, 8.0F};
    public static final float[] PUPPET_FOREARM = {-72.0F, 0.0F, 0.0F};
    /** 玄骸比玩家高一大截，刀跟着放大 */
    static float scale = 1.2F;
    /** 调试用：世界里的玄骸、手里有武器的立绘一律画这把（-1 不改） */
    static int forceWeapon = -1;

    private static ItemStack[] stacks = new ItemStack[0];
    /** 绕刀柄转用的四元数（只在渲染线程用，免得每帧新建） */
    private static final Quaternionf ROLL = new Quaternionf();

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
        int index = animatable.getLichWeaponIndex();
        // 立绘本来空着手（档案空状态的剪影）就不换，免得胳膊没抬、手里却多出一把刀
        ItemStack stack = stackOf(forceWeapon >= 0 && (animatable.getId() >= 0 || index >= 0) ? forceWeapon : index);
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
        float[] hold = HOLD[mode];
        poseStack.translate(gripX, gripY, gripZ);
        // 转到原版手臂的坐标（y 朝手臂末端、-z 朝前），顺着看得见的前臂摆正
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F + FOREARM_TILT));
        poseStack.mulPose(Axis.YP.rotationDegrees(hold[1]));
        poseStack.mulPose(Axis.XP.rotationDegrees(hold[0]));
        if (hold[2] != 0.0F) {
            poseStack.mulPose(ROLL.rotationAxis(hold[2] * Mth.DEG_TO_RAD, 0.0F, HANDLE_AXIS_Y, HANDLE_AXIS_Z));
        }
        // 原版手持的原点在拳心往手臂末端 2 像素、往前 2 像素，跟着刀一起放大
        poseStack.translate(0.0F, scale * 2.0F / 16.0F, -scale * 2.0F / 16.0F);
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

    /** 立绘（实体 id 为负）手里有武器：模型据此把拿武器的胳膊抬起来（{@link #PUPPET_ARM}、{@link #PUPPET_FOREARM}） */
    public static boolean puppetHoldsWeapon(KuvaMasterEntity entity) {
        return entity.getId() < 0 && stackOf(entity.getLichWeaponIndex()) != null;
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

    /**
     * 调试用：现场改拳心位置和某一种摆法的握法（只在开发环境的命令里调）；拳心和缩放三种摆法共用。
     * {@code wrist} 手腕、{@code twist} 前臂拧、{@code roll} 绕刀柄转，见 {@link #HOLD}
     */
    static void tune(int mode, float ox, float oy, float oz, float wrist, float twist, float roll, float s) {
        gripX = ox;
        gripY = oy;
        gripZ = oz;
        HOLD[mode][0] = wrist;
        HOLD[mode][1] = twist;
        HOLD[mode][2] = roll;
        scale = s;
    }

    /** 调试用：现场改立绘胳膊叠的转角（动画文件的写法） */
    static void tuneArm(float ax, float ay, float az, float fx, float fy, float fz) {
        PUPPET_ARM[0] = ax;
        PUPPET_ARM[1] = ay;
        PUPPET_ARM[2] = az;
        PUPPET_FOREARM[0] = fx;
        PUPPET_FOREARM[1] = fy;
        PUPPET_FOREARM[2] = fz;
    }

    static String describe(int mode) {
        float[] h = HOLD[mode];
        return String.format(java.util.Locale.ROOT,
                "mode %d grip %.3f %.3f %.3f wrist %.1f twist %.1f roll %.1f scale %.2f force %d arm %.0f %.0f %.0f / %.0f %.0f %.0f",
                mode, gripX, gripY, gripZ, h[0], h[1], h[2], scale, forceWeapon,
                PUPPET_ARM[0], PUPPET_ARM[1], PUPPET_ARM[2], PUPPET_FOREARM[0], PUPPET_FOREARM[1], PUPPET_FOREARM[2]);
    }
}
