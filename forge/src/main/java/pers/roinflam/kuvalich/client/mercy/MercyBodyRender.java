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
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.LogUtil;
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
    /** 这一次 Pre 是被 {@link #onRenderPreFirst} 先取消掉的 */
    @Nullable
    private static RenderPlayerEvent.Pre forced;
    private static boolean warned;

    private MercyBodyRender() {
    }

    /**
     * 处决期间换回原版模型：改玩家模型的模组（Yes Steve Model 这类）在默认优先级的 Pre 里取消事件、自己画。
     * 这里抢在前面取消，它那个不收已取消事件的监听就被跳过，到 {@link #onRenderPre} 再把取消撤掉，原版渲染器照常往下画。
     * 不能反过来在事件里自己调原版渲染器：那会再发一次 Pre，又被它接走
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPreFirst(RenderPlayerEvent.Pre event) {
        forced = null;
        if (!EffectsDisplayConfig.mercyVanillaModel()) {
            return;
        }
        MercyClient.Exec e = MercyClient.byExecutor(event.getEntity().getId());
        // 到交还为止：判错时记录还要留几秒，那时人已经能走动了，得回到它自己的模型
        if (e == null || e.elapsed() >= e.handbackMs) {
            return;
        }
        event.setCanceled(true);
        forced = event;
    }

    /** 撤销和挪位放在同一个监听里，中间不留别人插手的空档 */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (event == forced) {
            event.setCanceled(false);
        }
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getEntity();
        MercyClient.Exec e = MercyClient.byExecutor(player.getId());
        if (e == null || !MercyPose.body(e, e.elapsed(), BODY)) {
            return;
        }
        // 化作红光闪过去的那一下：整个人连同手里的刀都不画（影子在站位上，本来就不跟着动）
        if (BODY.hidden) {
            event.setCanceled(true);
            forced = null;
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

    /**
     * 挪位之后又有人取消了这次渲染：Post 不会来，这里把矩阵弹回去。
     * 放行过的又被取消，说明有别的东西换了接管方式，处决期间会是它的模型站着不动，提示一次
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPreLast(RenderPlayerEvent.Pre event) {
        RenderPlayerEvent.Pre mine = forced;
        forced = null;
        if (!event.isCanceled()) {
            return;
        }
        if (pushed) {
            pushed = false;
            event.getPoseStack().popPose();
        }
        if (event == mine && !warned) {
            warned = true;
            LogUtil.warn("处决期间换回原版玩家模型没有生效：玩家 " + event.getEntity().getName().getString()
                    + " 的渲染在放行后又被取消，可能是改玩家模型的模组换了接管方式");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushed) {
            pushed = false;
            event.getPoseStack().popPose();
        }
    }
}
