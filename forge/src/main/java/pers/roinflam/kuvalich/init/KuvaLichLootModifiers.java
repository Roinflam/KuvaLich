package pers.roinflam.kuvalich.init;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.loot.OreDropMultiplierModifier;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 全局战利品修饰器（Global Loot Modifier）注册
 *
 * <p>注册的是<b>序列化器</b>，不是修饰器实例本身。实例由数据包里的
 * {@code data/kuvalich/loot_modifiers/*.json} 描述，再由
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json} 列出启用哪些。</p>
 *
 * @author RoinFlam
 */
public final class KuvaLichLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Reference.MOD_ID);

    /** 挖矿倍率：战甲词条 oreDropMultiplier 的结算入口 */
    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ORE_DROP_MULTIPLIER =
            LOOT_MODIFIERS.register("ore_drop_multiplier", OreDropMultiplierModifier.CODEC);

    private KuvaLichLootModifiers() {
    }
}
