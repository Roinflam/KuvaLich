package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.renderer.ElementDebuffTracker;

/**
 * 元素状态对整只生物的染色
 *
 * <p>原版生物由 {@code ElementRenderHandler} 在渲染前后设着色器颜色；玄骸和奴仆走 GeckoLib 的渲染器，
 * 不发那个渲染事件，所以它们的渲染器覆写取色方法，调这里拿颜色。两边用的是同一份计算，冰冻的玄骸和旁边冰冻的僵尸同色。</p>
 *
 * <p>算法：每种元素先按等级从白往满色插（最低级 70%，满级 100%；毒素只有一层，直接满色），多种元素等权平均。
 * 64 格外不染。零分配：遍历状态表用静态累加器，结果打成一个 int。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ElementTint {

    // 满级的染色（r, g, b）
    private static final float[] TINT_ICE = {0.55F, 0.75F, 1.00F};
    private static final float[] TINT_POISON = {0.25F, 1.00F, 0.25F};
    private static final float[] TINT_FIRE = {1.00F, 0.70F, 0.50F};
    private static final float[] TINT_RADIATION = {0.85F, 1.00F, 0.45F};
    private static final float[] TINT_CORROSION = {0.60F, 0.90F, 0.40F};
    private static final float[] TINT_VIRUS = {1.00F, 0.50F, 0.80F};
    /** 磁力跟着身上的横向光带改成淡青（以前是紫，和青色光带放在一起像两种元素） */
    private static final float[] TINT_MAGNETIC = {0.70F, 0.94F, 0.97F};
    private static final float[] TINT_PUNCTURE = {0.75F, 0.80F, 0.90F};

    private static final double MAX_DIST_SQR = 64.0D * 64.0D;

    /** [r, g, b, 个数]；渲染只在客户端主线程，静态复用安全 */
    private static final float[] ACC = new float[4];

    private static final ElementDebuffTracker.ElementVisitor VISITOR = (element, amplifier) -> {
        float[] base = baseTint(element);
        if (base == null) {
            return;
        }
        int max = maxLevel(element);
        float factor = 1.0F;
        if (max > 0 && amplifier >= 0) {
            factor = 0.7F + 0.3F * Math.min((float) amplifier / max, 1.0F);
        }
        ACC[0] += 1.0F - factor * (1.0F - base[0]);
        ACC[1] += 1.0F - factor * (1.0F - base[1]);
        ACC[2] += 1.0F - factor * (1.0F - base[2]);
        ACC[3] += 1.0F;
    };

    private ElementTint() {
    }

    /**
     * 这只生物此刻的元素染色，{@code 0xRRGGBB}；没有元素状态（或在 64 格外）返回 -1
     */
    public static int tintRgb(LivingEntity entity) {
        if (entity == null || beyondRange(entity)) {
            return -1;
        }
        ACC[0] = 0.0F;
        ACC[1] = 0.0F;
        ACC[2] = 0.0F;
        ACC[3] = 0.0F;
        ElementDebuffTracker.forEachActiveElement(entity, VISITOR);
        if (ACC[3] <= 0.0F) {
            return -1;
        }
        float inv = 1.0F / ACC[3];
        int r = Math.round(Math.min(1.0F, ACC[0] * inv) * 255.0F);
        int g = Math.round(Math.min(1.0F, ACC[1] * inv) * 255.0F);
        int b = Math.round(Math.min(1.0F, ACC[2] * inv) * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    private static boolean beyondRange(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return false;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - entity.getX();
        double dy = cam.y - entity.getY();
        double dz = cam.z - entity.getZ();
        return dx * dx + dy * dy + dz * dz > MAX_DIST_SQR;
    }

    private static float[] baseTint(String element) {
        return switch (element) {
            case "ice" -> TINT_ICE;
            case "fire" -> TINT_FIRE;
            case "poison" -> TINT_POISON;
            case "radiation" -> TINT_RADIATION;
            case "corrosion" -> TINT_CORROSION;
            case "virus" -> TINT_VIRUS;
            case "magnetic" -> TINT_MAGNETIC;
            case "puncture" -> TINT_PUNCTURE;
            default -> null;
        };
    }

    /** 各元素的最高等级（和 {@code DynamicAttributes} 里的上限一致）；只有一层的返回 0 */
    private static int maxLevel(String element) {
        return switch (element) {
            case "fire", "puncture" -> 3;
            case "ice" -> 8;
            case "radiation", "corrosion", "virus", "magnetic" -> 9;
            default -> 0;
        };
    }
}
