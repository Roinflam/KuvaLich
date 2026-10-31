package pers.roinflam.kuvalich.client.fx;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.init.KuvaLichParticles;

/**
 * 自制粒子的提供者注册
 *
 * <p>注册的真正用处是让贴图进粒子图集、拿到贴图组（{@link KuvaSprites}）；提供者本身只给 {@code /particle} 命令调试用，
 * 演出里的粒子由脚本直接构造。</p>
 *
 * <p>注册了提供者的种类必须有贴图列表（{@code particles/<id>.json}），否则粒子引擎重载时会报错。
 * 11 种现在都有贴图（{@code tools/fx/make_particles.py} 生成）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaParticleProviders {

    private KuvaParticleProviders() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        sprite(event, KuvaLichParticles.KL_GLOW, KuvaParticles.Kind.GLOW);
        sprite(event, KuvaLichParticles.KL_SPARK, KuvaParticles.Kind.SPARK);
        sprite(event, KuvaLichParticles.KL_SHARD, KuvaParticles.Kind.SHARD);
        sprite(event, KuvaLichParticles.KL_RING, KuvaParticles.Kind.RING);
        sprite(event, KuvaLichParticles.KL_GLYPH, KuvaParticles.Kind.GLYPH);
        sprite(event, KuvaLichParticles.KL_MOTE, KuvaParticles.Kind.MOTE);
        sprite(event, KuvaLichParticles.KL_MIST, KuvaParticles.Kind.MIST);
        sprite(event, KuvaLichParticles.KL_HEX, KuvaParticles.Kind.HEX);
        sprite(event, KuvaLichParticles.KL_DROP, KuvaParticles.Kind.DROP);
        sprite(event, KuvaLichParticles.KL_SPLAT, KuvaParticles.Kind.SPLAT);
        sprite(event, KuvaLichParticles.KL_BUBBLE, KuvaParticles.Kind.BUBBLE);
    }

    private static void sprite(RegisterParticleProvidersEvent event, RegistryObject<SimpleParticleType> type,
                               KuvaParticles.Kind kind) {
        event.registerSpriteSet(type.get(), sprites -> {
            KuvaSprites.put(kind, sprites);
            return new KuvaParticle.Provider(sprites, kind);
        });
    }
}
