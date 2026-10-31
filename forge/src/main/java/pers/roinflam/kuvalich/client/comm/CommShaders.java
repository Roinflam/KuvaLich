package pers.roinflam.kuvalich.client.comm;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 通讯窗的合成着色器 {@code kuvalich:kl_comm_composite}，以及肖像代码共用的「按自己的混合方式画一批」
 *
 * <p><b>为什么混合方式不写进 json</b>：着色器应用时会套一遍 json 里的混合方式，而且只在「上一次套的不一样」时才套；
 * json 写了混合的着色器用过之后，下一个不写混合的原版着色器第一次应用会顺手关掉混合，
 * 紧跟在通讯窗后面画的那一样界面元素就会变成不透明。所以这里的 json 不写混合（和原版大多数着色器一样算「不透明」），
 * 每次画之前先手动应用着色器，再自己设混合，画完把混合还原成原版默认。</p>
 *
 * <p>建失败（资源包或光影包把文件换掉、编译出错、文件被删）时 {@link #composite()} 返回 null，
 * 肖像退回原版 {@code position_tex_color} 加网格顶点羽化，日志记一次，不报错。
 * 每次资源重载都会重新建，顺带作废肖像的离屏缓冲和假实体。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CommShaders {

    /** 预乘 alpha 叠上去（缓冲里是预乘色） */
    public static final int BLEND_PREMULT = 0;
    /** 普通混合，往透明缓冲里画出来的就是预乘色 */
    public static final int BLEND_NORMAL = 1;
    /** 加法，只加颜色不动 alpha */
    public static final int BLEND_ADD = 2;

    private static final ResourceLocation NAME = new ResourceLocation(Reference.MOD_ID, "kl_comm_composite");

    @Nullable
    private static ShaderInstance composite;
    private static boolean warned;

    private CommShaders() {
    }

    /** 合成着色器；没建成时为 null（走退路） */
    @Nullable
    public static ShaderInstance composite() {
        return composite;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        composite = null;
        try {
            ShaderInstance shader = new ShaderInstance(event.getResourceProvider(), NAME,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            event.registerShader(shader, s -> composite = s);
            warned = false;
        } catch (Exception e) {
            // 资源包、光影包换掉或删了文件就会走到这里，属于降级不是故障：只记一行，不带调用栈
            if (!warned) {
                warned = true;
                LogUtil.warn("通讯窗合成着色器没建成，肖像改用简化合成（没有重影、错位带和溶解）：" + e);
            }
        }
        // 资源重载：缓冲尺寸、贴图、模型都可能变了
        CommPortraits.invalidate();
    }

    /** 0~2 号采样器的名字（每画一次都要设，别每次拼字符串） */
    private static final String[] SAMPLERS = {"Sampler0", "Sampler1", "Sampler2"};

    /**
     * 用指定着色器和混合方式画掉 builder 里的这一批（builder 已经 begin 并填好顶点）
     *
     * <p>和原版 {@code BufferUploader.drawWithShader} 的区别只在顺序：先应用着色器（让它把自己的混合记录套完），
     * 再设我们要的混合，然后画。采样器取 {@code RenderSystem} 里 0~2 号贴图，矩阵和颜色乘子取当前值。</p>
     */
    static void draw(BufferBuilder builder, ShaderInstance shader, int blend) {
        BufferBuilder.RenderedBuffer rendered = builder.end();
        for (int i = 0; i < SAMPLERS.length; i++) {
            shader.setSampler(SAMPLERS[i], RenderSystem.getShaderTexture(i));
        }
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        shader.apply();
        applyBlend(blend);
        BufferUploader.draw(rendered);
        shader.clear();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void applyBlend(int blend) {
        RenderSystem.enableBlend();
        switch (blend) {
            case BLEND_PREMULT -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            case BLEND_ADD -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            default -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }
    }
}
