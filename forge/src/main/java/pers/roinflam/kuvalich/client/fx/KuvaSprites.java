package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.particle.SpriteSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * 每种自制粒子的贴图组。注册提供者的时候粒子引擎把空的贴图组交给我们，资源重载时它自己填好，
 * 这里只是记下引用，演出直接构造粒子时拿来用
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaSprites {

    private static final Map<KuvaParticles.Kind, SpriteSet> SETS = new EnumMap<>(KuvaParticles.Kind.class);

    private KuvaSprites() {
    }

    static void put(KuvaParticles.Kind kind, SpriteSet sprites) {
        SETS.put(kind, sprites);
    }

    /** 这种粒子的贴图组；资源还没加载完（或这种没有贴图）时返回 null */
    public static SpriteSet of(KuvaParticles.Kind kind) {
        return SETS.get(kind);
    }
}
