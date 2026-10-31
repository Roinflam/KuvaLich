package pers.roinflam.kuvalich.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.client.mercy.KuvaFuryLayer;
import pers.roinflam.kuvalich.client.model.KuvaSlaveModel;
import pers.roinflam.kuvalich.client.renderer.layer.KuvaGlowLayer;
import pers.roinflam.kuvalich.client.renderer.layer.KuvaThrallRimLayer;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒奴仆渲染器
 *
 * <p>层：胸口一道赤毒裂纹的发光（{@link KuvaGlowLayer}）、常驻的冷色描边（{@link KuvaThrallRimLayer}）、
 * 交战越久越明显的怒意。淡入淡出、死亡不翻倒、元素染色、名牌这些和玄骸共用，见 {@link AbstractKuvaRenderer}。</p>
 */
public class KuvaSlaveRenderer extends AbstractKuvaRenderer<KuvaSlaveEntity> {

    /** 发光遮罩：只点胸口（tools/fx/make_glowmask.py 生成） */
    public static final ResourceLocation GLOW_MASK = new ResourceLocation(Reference.MOD_ID,
            "textures/entity/kuva_slave_glowmask.png");
    private static final String[] GLOW_BONES = {"chestArmor"};

    public KuvaSlaveRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KuvaSlaveModel());
        addRenderLayer(new KuvaGlowLayer<>(this, GLOW_MASK, GLOW_BONES));
        addRenderLayer(new KuvaThrallRimLayer(this));
        addRenderLayer(new KuvaFuryLayer<>(this));
    }
}
