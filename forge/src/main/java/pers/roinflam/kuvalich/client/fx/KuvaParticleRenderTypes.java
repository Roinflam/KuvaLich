package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 自制粒子的两种渲染方式（原版的六种里没有加法混合）
 *
 * <ul>
 *   <li>{@link #ADDITIVE}：加法混合、不写深度，着色器 {@code kl_particle_add}（不按低透明度切掉、雾里熄灭而不是加雾色）。
 *       光点、火花、光环、印记都走它。</li>
 *   <li>{@link #SOFT}：普通透明混合、不写深度，着色器 {@code kl_particle_soft}（不切硬边）。雾和烟走它，
 *       雾团互相叠也不出硬边。</li>
 * </ul>
 *
 * <p>{@code end} 里一定要把混合函数和深度写入还原：Forge 把自定义类型排在原版之后画，状态会漏给后面的东西。
 * 粒子引擎在每个类型 {@code begin} 之前设一次原版粒子着色器，我们在自己的 {@code begin} 里覆盖掉。</p>
 *
 * <p>同为自定义类型时，Forge 按对象的身份哈希排先后。暗的雾应该先画、亮的光叠在它上面才显得「发光」，
 * 所以建 {@link #SOFT} 时挑一个哈希比 {@link #ADDITIVE} 小的实例。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaParticleRenderTypes {

    private KuvaParticleRenderTypes() {
    }

    public static final ParticleRenderType ADDITIVE = new Additive();
    public static final ParticleRenderType SOFT = makeSoftBefore(ADDITIVE);

    private static ParticleRenderType makeSoftBefore(ParticleRenderType other) {
        int target = System.identityHashCode(other);
        Soft s = new Soft();
        for (int i = 0; i < 64 && System.identityHashCode(s) >= target; i++) {
            s = new Soft();
        }
        return s;
    }

    private static void begin(BufferBuilder buffer, ShaderInstance shader, boolean additive) {
        // 资源重载时着色器没建成：已经生成的粒子退回原版粒子着色器画完余下的寿命
        ShaderInstance sh = shader != null ? shader : GameRenderer.getParticleShader();
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    private static void end(Tesselator tesselator) {
        tesselator.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    private static final class Additive implements ParticleRenderType {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            KuvaParticleRenderTypes.begin(buffer, KuvaParticleShaders.additive(), true);
        }

        @Override
        public void end(Tesselator tesselator) {
            KuvaParticleRenderTypes.end(tesselator);
        }

        @Override
        public String toString() {
            return "KUVALICH_PARTICLE_ADDITIVE";
        }
    }

    private static final class Soft implements ParticleRenderType {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            KuvaParticleRenderTypes.begin(buffer, KuvaParticleShaders.soft(), false);
        }

        @Override
        public void end(Tesselator tesselator) {
            KuvaParticleRenderTypes.end(tesselator);
        }

        @Override
        public String toString() {
            return "KUVALICH_PARTICLE_SOFT";
        }
    }
}
