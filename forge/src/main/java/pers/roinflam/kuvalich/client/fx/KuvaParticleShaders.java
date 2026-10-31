package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.function.Consumer;

/**
 * 特效用的三个自制着色器
 *
 * <ul>
 *   <li>{@code kl_particle_add}：加法粒子。原版 particle.fsh 会把 alpha &lt; 0.1 的片元丢掉，软边、拖尾、淡出的尾巴都会被切成实心硬边；
 *       这个不丢，雾里按距离把颜色乘到 0（加法下就是熄灭），不会在下界、水下往上加一层雾色。</li>
 *   <li>{@code kl_particle_soft}：雾、烟用的透明粒子，同样不丢片元，雾里正常混雾色。</li>
 *   <li>{@code kl_fx_add}：几何图元（位置 + 颜色）的加法着色器，写法同原版闪电。</li>
 * </ul>
 *
 * <p>混合方式写在各自的 json 里：着色器每次应用都会套一遍 json 里的混合方式（和上一次不同时），
 * 所以只靠渲染状态设 {@code blendFunc} 不可靠，混合必须和着色器一致。</p>
 *
 * <p>建失败（资源出错、光影包接管）时对应的值保持 null，粒子和几何退回原版着色器的透明混合，日志记一次。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KuvaParticleShaders {

    private static ShaderInstance particleAdd;
    private static ShaderInstance particleSoft;
    private static ShaderInstance fxAdd;

    private KuvaParticleShaders() {
    }

    /** 加法粒子着色器；没建成时为 null */
    public static ShaderInstance additive() {
        return particleAdd;
    }

    /** 柔和透明粒子着色器；没建成时为 null */
    public static ShaderInstance soft() {
        return particleSoft;
    }

    /** 几何图元的加法着色器；没建成时为 null */
    public static ShaderInstance geometry() {
        return fxAdd;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        particleAdd = null;
        particleSoft = null;
        fxAdd = null;
        register(event, "kl_particle_add", DefaultVertexFormat.PARTICLE, s -> particleAdd = s);
        register(event, "kl_particle_soft", DefaultVertexFormat.PARTICLE, s -> particleSoft = s);
        register(event, "kl_fx_add", DefaultVertexFormat.POSITION_COLOR, s -> fxAdd = s);
    }

    private static void register(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> sink) {
        try {
            ShaderInstance shader = new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(Reference.MOD_ID, name), format);
            event.registerShader(shader, sink);
        } catch (Exception e) {
            LogUtil.error("特效着色器 " + name + " 没建成，退回原版透明混合", e);
        }
    }
}
