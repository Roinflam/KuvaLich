package pers.roinflam.kuvalich.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo.BiomeInfo;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichEntities;

import java.util.List;

/**
 * 赤毒玄骸自定义生物群系修改器
 * KuvaLich Custom Biome Modifier
 *
 * 生成规则：
 * - 实体生成：主世界 + 地狱 + 暮色森林等所有维度，排除末地和蘑菇岛
 * - 矿石生成：所有维度（依赖方块Tag自动过滤，无石头的维度自然不会生成）
 *
 * Spawn rules:
 * - Entity spawning: Overworld + Nether + Twilight Forest etc., excludes The End and mushroom islands
 * - Ore generation: All dimensions (block tag filtering handles incompatible biomes naturally)
 */
public class KuvaLichBiomeModifier implements BiomeModifier {

    // ═══ 单例 / Singleton ═══

    public static final KuvaLichBiomeModifier INSTANCE = new KuvaLichBiomeModifier();

    public static final Codec<KuvaLichBiomeModifier> CODEC = Codec.unit(INSTANCE);

    /**
     * 蘑菇岛Tag / Mushroom biome tag
     */
    private static final TagKey<Biome> IS_MUSHROOM_TAG = TagKey.create(
            Registries.BIOME,
            new ResourceLocation("minecraft", "is_mushroom")
    );

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
        if (biome.is(IS_END_TAG)) {
            return;
        }

        // 矿石生成：除末地外所有维度（无石头的维度靠Tag自动过滤）
        // Ore gen: all dims except The End (block tags handle incompatible biomes naturally)
        addOreGeneration(builder);

        // 实体生成：排除蘑菇岛，其余维度（主世界/地狱/暮色森林等）均可生成
        // Entity spawning: exclude mushroom islands, spawn in all other dims (overworld/nether/twilight etc.)
        if (!biome.is(IS_MUSHROOM_TAG)) {
            addEntitySpawns(builder.getMobSpawnSettings());
        }
    }

    /**
     * 添加矿石生成
     * Add ore generation
     */
    private void addOreGeneration(BiomeInfo.Builder builder) {
        addRequiemOre(builder);
        addExperienceOre(builder);
    }

    /**
     * 添加安魂矿石生成
     * Add Requiem Ore generation
     */
    private void addRequiemOre(BiomeInfo.Builder builder) {
        int veinCount = ModConfig.ORE_GEN.requiemOreVeinCount.get();
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
                new OreConfiguration(targets, ModConfig.ORE_GEN.requiemOreVeinSize.get(), 0.0f));

        PlacedFeature placedFeature = new PlacedFeature(
                Holder.direct(configuredFeature),
                List.of(
                        CountPlacement.of(veinCount),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(ModConfig.ORE_GEN.requiemOreMinHeight.get()),
                                VerticalAnchor.absolute(ModConfig.ORE_GEN.requiemOreMaxHeight.get())
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
     */
    private void addExperienceOre(BiomeInfo.Builder builder) {
        int veinCount = ModConfig.ORE_GEN.experienceOreVeinCount.get();
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
                new OreConfiguration(targets, ModConfig.ORE_GEN.experienceOreVeinSize.get(), 0.0f));

        PlacedFeature placedFeature = new PlacedFeature(
                Holder.direct(configuredFeature),
                List.of(
                        CountPlacement.of(veinCount),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.uniform(
                                VerticalAnchor.absolute(ModConfig.ORE_GEN.experienceOreMinHeight.get()),
                                VerticalAnchor.absolute(ModConfig.ORE_GEN.experienceOreMaxHeight.get())
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
     * 添加实体生成
     * Add entity spawning
     */
    private void addEntitySpawns(MobSpawnSettingsBuilder spawnBuilder) {
        int lichWeight = ModConfig.KUVA_LICH.kuvaLichSpawnWeight.get();
        if (lichWeight > 0) {
            spawnBuilder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                    KuvaLichEntities.KUVA_MASTER.get(),
                    lichWeight,
                    ModConfig.KUVA_LICH.kuvaLichMinSpawnCount.get(),
                    ModConfig.KUVA_LICH.kuvaLichMaxSpawnCount.get()
            ));
        }

        int slaveWeight = ModConfig.KUVA_LICH.kuvaSlaveSpawnWeight.get();
        if (slaveWeight > 0) {
            spawnBuilder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                    KuvaLichEntities.KUVA_SLAVE.get(),
                    slaveWeight,
                    ModConfig.KUVA_LICH.kuvaSlaveMinSpawnCount.get(),
                    ModConfig.KUVA_LICH.kuvaSlaveMaxSpawnCount.get()
            ));
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return KuvaLichBiomeModifiers.KUVALICH_MODIFIER_CODEC.get();
    }
}