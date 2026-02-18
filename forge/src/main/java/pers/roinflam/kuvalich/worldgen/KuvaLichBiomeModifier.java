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
 * 统一处理以下功能，全部从配置文件读取参数：
 * Handles the following features, all parameters read from config:
 * 1. 矿石生成（安魂矿石、经验矿石）/ Ore generation (Requiem Ore, Experience Ore)
 * 2. 实体生成（赤毒玄骸、赤毒奴仆）/ Entity spawning (Kuva Master, Kuva Slave)
 * 3. 排除蘑菇岛生成，与僵尸行为一致 / Exclude mushroom islands, consistent with zombie behavior
 */
public class KuvaLichBiomeModifier implements BiomeModifier {

    // ═══ 单例 / Singleton ═══

    public static final KuvaLichBiomeModifier INSTANCE = new KuvaLichBiomeModifier();

    public static final Codec<KuvaLichBiomeModifier> CODEC = Codec.unit(INSTANCE);

    /**
     * 蘑菇岛生物群系Tag（手动构造，1.20.1的BiomeTags没有IS_MUSHROOM常量）
     * Mushroom biome tag (manually constructed, BiomeTags in 1.20.1 has no IS_MUSHROOM constant)
     */
    private static final TagKey<Biome> IS_MUSHROOM_TAG = TagKey.create(
            Registries.BIOME,
            new ResourceLocation("minecraft", "is_mushroom")
    );

    private KuvaLichBiomeModifier() {}

    // ═══ 核心修改逻辑 / Core modification logic ═══

    @Override
    public void modify(Holder<Biome> biome, Phase phase, BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;

        // 矿石生成不受蘑菇岛限制 / Ore gen applies to all biomes
        addOreGeneration(builder);

        // 蘑菇岛不生成敌对实体 / No hostile spawns on mushroom islands
        if (biome.is(IS_MUSHROOM_TAG)) {
            return;
        }

        addEntitySpawns(builder.getMobSpawnSettings());
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
                        // 梯形分布，与原JSON trapezoid一致 / Trapezoid distribution, matching original JSON
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
                        // 均匀分布，与原JSON uniform一致 / Uniform distribution, matching original JSON
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