package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 处决时把整个玩家模型挪到整身轨道算出来的位置（见 {@link MercyPose} 的说明）
 *
 * <p>在原版转身子之前往矩阵里插一段：先挪到轨道上的位置，转到轨道的朝向，绕腰翻转，再把原版接下来要转的身体朝向抵掉。
 * 这样护甲、手里的东西、皮肤外层都跟着走，刀口的特效和服务端都不用管模型在哪。</p>
 *
 * <p>这一段插在原版 {@code setupRotations} 前面，那里的坐标系 x 朝右、y 朝上、z 朝后，所以往前翻是绕 x 转负角。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyBodyRender {

    /** 翻转绕的点离脚底多高（格） */
    private static final float PIVOT = 0.85F;

    private static final MercyPose.Body BODY = new MercyPose.Body();
    private static boolean pushed;

    private MercyBodyRender() {
    }

    /** 排在最后：别的模组取消这次渲染时就不进来，矩阵不会留下没弹的一层 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        MercyClient.Exec e = MercyClient.byExecutor(player.getId());
        if (e == null || !MercyPose.body(e, e.elapsed(), BODY)) {
            return;
        }
        float pt = event.getPartialTick();
        float k = BODY.blend;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        pushed = true;
        ps.translate((BODY.x - Mth.lerp(pt, player.xo, player.getX())) * k,
                (BODY.y - Mth.lerp(pt, player.yo, player.getY())) * k,
                (BODY.z - Mth.lerp(pt, player.zo, player.getZ())) * k);
        float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - (bodyYaw + Mth.wrapDegrees(BODY.yaw - bodyYaw) * k)));
        if (BODY.flip != 0.0F || BODY.roll != 0.0F) {
            ps.translate(0.0F, PIVOT, 0.0F);
            ps.mulPose(Axis.XP.rotationDegrees(-BODY.flip * k));
            ps.mulPose(Axis.ZP.rotationDegrees(-BODY.roll * k));
            ps.translate(0.0F, -PIVOT, 0.0F);
        }
        ps.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180.0F));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushed) {
            pushed = false;
            event.getPoseStack().popPose();
        }
    }
}
