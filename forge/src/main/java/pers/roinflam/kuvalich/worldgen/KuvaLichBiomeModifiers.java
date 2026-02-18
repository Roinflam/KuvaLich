package pers.roinflam.kuvalich.worldgen;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒玄骸生物群系修改器注册类
 * KuvaLich Biome Modifier Registry
 *
 * 注册自定义BiomeModifier的Codec，使Forge能识别我们的JSON修改器类型
 * Registers the Codec for our custom BiomeModifier so Forge can deserialize the JSON modifier type
 *
 * 在主Mod类构造函数中调用：KuvaLichBiomeModifiers.register(modEventBus)
 * Call in main Mod class constructor: KuvaLichBiomeModifiers.register(modEventBus)
 */
public class KuvaLichBiomeModifiers {

    /**
     * DeferredRegister用于注册BiomeModifier序列化器
     * DeferredRegister for registering BiomeModifier serializers
     */
    public static final DeferredRegister<Codec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Reference.MOD_ID);

    /**
     * 注册我们的自定义modifier codec
     * Register our custom modifier codec
     *
     * JSON中的 "type": "kuvalich:kuvalich_modifier" 会对应到这里
     * The JSON field "type": "kuvalich:kuvalich_modifier" maps to this
     */
    public static final RegistryObject<Codec<KuvaLichBiomeModifier>> KUVALICH_MODIFIER_CODEC =
            BIOME_MODIFIER_SERIALIZERS.register("kuvalich_modifier", () -> KuvaLichBiomeModifier.CODEC);

    /**
     * 注册到mod事件总线
     * Register to mod event bus
     *
     * @param modEventBus mod事件总线 / mod event bus
     */
    public static void register(IEventBus modEventBus) {
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}