package pers.roinflam.kuvalich.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo.BiomeInfo;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.guide.GuideConfigSnapshot;
import pers.roinflam.kuvalich.init.KuvaLichBiomeModifierSerializers;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;

import java.util.List;

/**
 * 赤毒玄骸自定义生物群系修改器
 * KuvaLich Custom Biome Modifier
 *
 * 生成规则：
 * - 实体生成：不再往任何群系的刷怪表里加东西。玄骸只由玄骸导演放，奴仆只由奴仆小队放给有玄骸的玩家
 *   （nemesis/ThrallDirector），幼体只由幼体导演放给还没有玄骸的玩家（nemesis/LarvlingDirector）
 * - 矿石生成：所有维度（依赖方块Tag自动过滤，无石头的维度自然不会生成），但排除末地
 *
 * Spawn rules:
 * - Entity spawning: nothing is added to biome spawn lists any more (Liches, Thralls and Larvlings are placed by directors)
 * - Ore generation: All dimensions except The End (block tag filtering handles incompatible biomes naturally)
 */
public class KuvaLichBiomeModifier implements BiomeModifier {

    // ═══ 单例 / Singleton ═══

    public static final KuvaLichBiomeModifier INSTANCE = new KuvaLichBiomeModifier();

    public static final Codec<KuvaLichBiomeModifier> CODEC = Codec.unit(INSTANCE);

    /**
     * 末地Tag / The End biome tag
     */
    private static final TagKey<Biome> IS_END_TAG = TagKey.create(
            Registries.BIOME,
            new ResourceLocation("minecraft", "is_end")
    );

    private KuvaLichBiomeModifier() {}

    // ═══ 核心修改逻辑 / Core modification logic ═══

    @Override
    public void modify(Holder<Biome> biome, Phase phase, BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;

        // 末地不做任何处理 / Skip The End entirely
        if (biome.is(IS_END_TAG) || isEnd(biome)) {
            return;
        }

        // 矿石生成：除末地外所有维度（无石头的维度靠Tag自动过滤）
        // Ore gen: all dims except The End (block tags handle incompatible biomes naturally)
        addOreGeneration(builder);

        // 实体生成：不再加。奴仆以前在主世界（蘑菇岛除外）的刷怪表里，现在改成只给有玄骸的玩家按小队放，
        // kuvaSlaveSpawnWeight 等几项旧配置留着不删（老配置文件不报错），已经不读了
    }

    /**
     * 读一项配置，并登记给冒险指南
     *
     * <p>这里的值只在服务端启动时读一次、烘焙进世界生成，热重载改 toml 要重启才生效。
     * 登记后指南显示的是这次启动实际用上的值，而不是热重载后还没生效的新值。</p>
     *
     * @param spec 配置项
     * @return 当前值
     */
    private static <T> T pinned(ForgeConfigSpec.ConfigValue<T> spec) {
        T value = spec.get();
        GuideConfigSnapshot.pinStartupValue(spec, value);
        return value;
    }

    // ═══ 生物群系判定辅助方法 / Biome identification helpers ═══

    /**
     * 判断是否为末地生物群系（Tag + 注册ID双重判定）
     * Check if the biome is an End biome (dual check: tag + registry ID)
     *
     * @param biome 生物群系Holder / biome holder
     * @return 是否为末地 / whether it's The End
     */
    private static boolean isEnd(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> {
                    String path = key.location().getPath();
                    return path.equals("the_end") || path.equals("end_barrens")
                            || path.equals("end_highlands") || path.equals("end_midlands")
                            || path.equals("small_end_islands");
                })
                .orElse(false);
    }

    // ═══ 矿石生成 / Ore generation ═══

    /**
     * 添加矿石生成
     * Add ore generation
     *
     * @param builder 生物群系信息构建器 / biome info builder
     */
    private void addOreGeneration(BiomeInfo.Builder builder) {
        addRequiemOre(builder);
        addExperienceOre(builder);
    }

    /**
     * 添加安魂矿石生成
     * Add Requiem Ore generation
     *
     * @param builder 生物群系信息构建器 / biome info builder
     */
    private void addRequiemOre(BiomeInfo.Builder builder) {
        int veinCount = pinned(ModConfig.ORE_GEN.requiemOreVeinCount);
        if (veinCount <= 0) return;

        List<OreConfiguration.TargetBlockState> targets = List.of(
                OreConfiguration.target(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        KuvaLichBlocks.REQUIEM_ORE.get().defaultBlockState()
                ),
                OreConfiguration.target(
                        new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                        KuvaLichBlocks.REQUIEM_ORE.get().defaultBlockState()
                )
        );

        ConfiguredFeature<?, ?> configuredFeature = new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(targets, pinned(ModConfig.ORE_GEN.requiemOreVeinSize), 0.0f));

        PlacedFeature placedFeature = new PlacedFeature(
                Holder.direct(configuredFeature),
                List.of(
                        CountPlacement.of(veinCount),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(pinned(ModConfig.ORE_GEN.requiemOreMinHeight)),
                                VerticalAnchor.absolute(pinned(ModConfig.ORE_GEN.requiemOreMaxHeight))
                        ),
                        BiomeFilter.biome()
                )
        );

        builder.getGenerationSettings().addFeature(
                GenerationStep.Decoration.UNDERGROUND_ORES,
                Holder.direct(placedFeature)
        );
    }

    /**
     * 添加经验矿石生成
     * Add Experience Ore generation
     *
     * @param builder 生物群系信息构建器 / biome info builder
     */
    private void addExperienceOre(BiomeInfo.Builder builder) {
        int veinCount = pinned(ModConfig.ORE_GEN.experienceOreVeinCount);
        if (veinCount <= 0) return;

        List<OreConfiguration.TargetBlockState> targets = List.of(
                OreConfiguration.target(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        KuvaLichBlocks.EXPERIENCE_ORE.get().defaultBlockState()
                ),
                OreConfiguration.target(
                        new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                        KuvaLichBlocks.EXPERIENCE_ORE.get().defaultBlockState()
                )
        );

        ConfiguredFeature<?, ?> configuredFeature = new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(targets, pinned(ModConfig.ORE_GEN.experienceOreVeinSize), 0.0f));

        PlacedFeature placedFeature = new PlacedFeature(
                Holder.direct(configuredFeature),
                List.of(
                        CountPlacement.of(veinCount),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(
                                VerticalAnchor.absolute(pinned(ModConfig.ORE_GEN.experienceOreMinHeight)),
                                VerticalAnchor.absolute(pinned(ModConfig.ORE_GEN.experienceOreMaxHeight))
                        ),
                        BiomeFilter.biome()
                )
        );

        builder.getGenerationSettings().addFeature(
                GenerationStep.Decoration.UNDERGROUND_ORES,
                Holder.direct(placedFeature)
        );
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return KuvaLichBiomeModifierSerializers.KUVALICH_MODIFIER_CODEC.get();
    }
}