package pers.roinflam.kuvalich.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.client.lich.LichWeaponLayer;
import pers.roinflam.kuvalich.client.mercy.KuvaFuryLayer;
import pers.roinflam.kuvalich.client.model.KuvaMasterModel;
import pers.roinflam.kuvalich.client.renderer.layer.KuvaGlowLayer;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒玄骸渲染器
 *
 * <p>层：胸口晶体和面甲两点的发光（{@link KuvaGlowLayer}）、手里的赤毒武器、交战越久越明显的怒意纹路。
 * 淡入淡出、死亡不翻倒、元素染色、名牌这些和奴仆共用，见 {@link AbstractKuvaRenderer}。
 * 通讯肖像和档案立像也是 new 一个这个渲染器来画，所以层和状态都要容得下负数 id 的假实体。</p>
 */
public class KuvaMasterRenderer extends AbstractKuvaRenderer<KuvaMasterEntity> {

    /** 发光遮罩：只点胸口晶体和面甲上两点（tools/fx/make_glowmask.py 生成） */
    public static final ResourceLocation GLOW_MASK = new ResourceLocation(Reference.MOD_ID,
            "textures/entity/kuva_master_glowmask.png");
    /** 遮罩上有亮点的骨骼，其余骨骼发光层不画 */
    private static final String[] GLOW_BONES = {"Body", "Head"};

    public KuvaMasterRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KuvaMasterModel());
        addRenderLayer(new KuvaGlowLayer<>(this, GLOW_MASK, GLOW_BONES));
        addRenderLayer(new LichWeaponLayer(this));
        addRenderLayer(new KuvaFuryLayer<>(this));
    }
}
