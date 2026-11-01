package pers.roinflam.kuvalich.client.model;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.base.entity.KuvaBodyAnim;
import pers.roinflam.kuvalich.mercy.MercyState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * 关键帧算完以后再叠的两样：出手那层停下后的淡出、头跟着视线转
 *
 * <p><b>淡出</b>：起势 / 出手那层（{@code attack} 控制器）换片段、停下时不用 GeckoLib 的过渡（它从主控制器刚写的姿势起插，
 * 画面上会先闪回站姿），由这里从上一帧画出来的姿势淡过去，淡多久由 {@link KuvaBodyAnim} 定。</p>
 *
 * <p><b>转头</b>：渲染器只按身体朝向转整个模型，头看向别处时模型不跟。这里把视线和身体的夹角加到头上（玄骸分三成给腰），
 * 倒地 / 处决期间不加。这里只改旋转，不改位移：GeckoLib 把代码里写过的位移算作「这一帧动过」，下一帧不归零，
 * 写一次就累加一次。</p>
 */
@OnlyIn(Dist.CLIENT)
final class KuvaPoseLayer {

    /** 出手那层会动到的骨骼（淡的时候整套一起淡，走路那层的骨骼也在里面，淡出时不会漏） */
    static final String[] LICH_BONES = {"root", "waist", "Head", "helmet", "RArm", "RArmF", "LArm", "LArmF",
            "LLeg", "LLegF", "RLeg", "RLegF"};
    static final String[] THRALL_BONES = {"root", "waist", "Head", "RArm", "RH", "LArm", "LH",
            "LLeg", "LLegF", "RLeg", "RLegF"};

    /** 头最多转多少度 */
    private static final float MAX_YAW = 70.0F;
    private static final float MAX_PITCH = 35.0F;
    /** 玄骸转头时腰分走的比例：脖子短、头盔大，只转头像拧螺丝 */
    private static final float LICH_WAIST_SHARE = 0.3F;

    private KuvaPoseLayer() {
    }

    static void apply(GeoModel<?> model, AbstractKuva entity, AnimationState<?> state, boolean lich) {
        AnimationProcessor<?> processor = model.getAnimationProcessor();
        fade(processor, entity.getBodyAnim(), lich ? LICH_BONES : THRALL_BONES);
        if (entity.getMercyState() == MercyState.NONE && !entity.isDeadOrDying()) {
            look(processor, state, lich);
        }
    }

    /**
     * 出手那层切了一刀（换片段或停下）：从上一帧画出来的姿势起，在 {@code cutFadeMs} 内淡到关键帧算出来的姿势。
     * 每帧都把淡完的结果记成「上一帧」，下一刀从这里起
     */
    private static void fade(AnimationProcessor<?> processor, KuvaBodyAnim anim, String[] bones) {
        int n = bones.length * 6;
        long now = AbstractKuva.CLIENT_CLOCK.getAsLong();
        if (anim.cutPending) {
            anim.cutPending = false;
            if (anim.lastPose != null && anim.lastPose.length == n && anim.cutFadeMs > 0L) {
                if (anim.fromPose == null || anim.fromPose.length != n) {
                    anim.fromPose = new float[n];
                }
                System.arraycopy(anim.lastPose, 0, anim.fromPose, 0, n);
                anim.fadeStartMs = now;
                anim.fadeMs = anim.cutFadeMs;
            }
        }
        float u = 1.0F;
        if (anim.fadeMs > 0L) {
            long dt = now - anim.fadeStartMs;
            if (dt >= 0L && dt < anim.fadeMs) {
                u = (float) dt / anim.fadeMs;
                u = u * u * (3.0F - 2.0F * u);
            } else {
                anim.fadeMs = 0L;
            }
        }
        float[] from = anim.fromPose;
        float[] last = anim.lastPose;
        if (last == null || last.length != n) {
            last = new float[n];
            anim.lastPose = last;
        }
        for (int i = 0; i < bones.length; i++) {
            CoreGeoBone b = processor.getBone(bones[i]);
            if (b == null) {
                continue;
            }
            int o = i * 6;
            if (u < 1.0F && from != null) {
                b.setRotX(Mth.lerp(u, from[o], b.getRotX()));
                b.setRotY(Mth.lerp(u, from[o + 1], b.getRotY()));
                b.setRotZ(Mth.lerp(u, from[o + 2], b.getRotZ()));
                b.setPosX(Mth.lerp(u, from[o + 3], b.getPosX()));
                b.setPosY(Mth.lerp(u, from[o + 4], b.getPosY()));
                b.setPosZ(Mth.lerp(u, from[o + 5], b.getPosZ()));
            }
            last[o] = b.getRotX();
            last[o + 1] = b.getRotY();
            last[o + 2] = b.getRotZ();
            last[o + 3] = b.getPosX();
            last[o + 4] = b.getPosY();
            last[o + 5] = b.getPosZ();
        }
    }

    private static void look(AnimationProcessor<?> processor, AnimationState<?> state, boolean lich) {
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        CoreGeoBone head = processor.getBone("Head");
        if (data == null || head == null) {
            return;
        }
        // GeckoLib 给的两个角度已经换成骨骼的符号，直接加：yaw 正 = 朝自己右边，pitch 正 = 低头
        float yaw = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -MAX_YAW, MAX_YAW);
        float pitch = Mth.clamp(data.headPitch(), -MAX_PITCH, MAX_PITCH);
        if (lich) {
            CoreGeoBone waist = processor.getBone("waist");
            if (waist != null) {
                float share = yaw * LICH_WAIST_SHARE;
                waist.setRotY(waist.getRotY() + share * Mth.DEG_TO_RAD);
                yaw -= share;
            }
        }
        head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
    }
}
