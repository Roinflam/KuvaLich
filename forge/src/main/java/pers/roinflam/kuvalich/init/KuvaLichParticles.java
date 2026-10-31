package pers.roinflam.kuvalich.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 本模组的自制粒子类型
 *
 * <p>注册表的唯一作用是让 {@code particles/<id>.json} 列出的贴图进粒子图集、拿到 {@code SpriteSet}。
 * 演出里的粒子不走 {@code ParticleOptions}：客户端脚本直接构造粒子再交给粒子引擎，
 * 颜色、尺寸、寿命、曲线都不需要网络序列化，服务端从此不再发本模组的任何粒子包。</p>
 */
public final class KuvaLichParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Reference.MOD_ID);

    private KuvaLichParticles() {
    }

    private static RegistryObject<SimpleParticleType> simple(String id) {
        return PARTICLES.register(id, () -> new SimpleParticleType(false));
    }

    /** 软光点 / 光晕 */
    public static final RegistryObject<SimpleParticleType> KL_GLOW = simple("kl_glow");
    /** 火花（沿速度拉长） */
    public static final RegistryObject<SimpleParticleType> KL_SPARK = simple("kl_spark");
    /** 碎晶 */
    public static final RegistryObject<SimpleParticleType> KL_SHARD = simple("kl_shard");
    /** 细环 */
    public static final RegistryObject<SimpleParticleType> KL_RING = simple("kl_ring");
    /** 六边形 */
    public static final RegistryObject<SimpleParticleType> KL_HEX = simple("kl_hex");
    /** 液滴 */
    public static final RegistryObject<SimpleParticleType> KL_DROP = simple("kl_drop");
    /** 溅痕 */
    public static final RegistryObject<SimpleParticleType> KL_SPLAT = simple("kl_splat");
    /** 雾 / 烟 */
    public static final RegistryObject<SimpleParticleType> KL_MIST = simple("kl_mist");
    /** 泡泡 */
    public static final RegistryObject<SimpleParticleType> KL_BUBBLE = simple("kl_bubble");
    /** 安魂序号印记 */
    public static final RegistryObject<SimpleParticleType> KL_GLYPH = simple("kl_glyph");
    /** 光尘 */
    public static final RegistryObject<SimpleParticleType> KL_MOTE = simple("kl_mote");
}
