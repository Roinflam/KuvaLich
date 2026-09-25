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
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo.BiomeInfo;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.guide.GuideConfigSnapshot;
import pers.roinflam.kuvalich.init.KuvaLichBiomeModifierSerializers;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichEntities;

import java.util.List;

/**
 * 赤毒玄骸自定义生物群系修改器
 * KuvaLich Custom Biome Modifier
 *
 * 生成规则：
 * - 实体生成：仅限主世界，排除蘑菇岛
 * - 矿石生成：所有维度（依赖方块Tag自动过滤，无石头的维度自然不会生成），但排除末地
 *
 * Spawn rules:
 * - Entity spawning: Overworld only, excludes mushroom islands
 * - Ore generation: All dimensions except The End (block tag filtering handles incompatible biomes naturally)
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

    /**
     * 主世界Tag / Overworld biome tag
     */
    private static final TagKey<Biome> IS_OVERWORLD_TAG = TagKey.create(
            Registries.BIOME,
            new ResourceLocation("minecraft", "is_overworld")
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

        // 实体生成：仅限主世界，排除蘑菇岛（同时使用Tag和注册ID双重判定，防止Tag未绑定）
        // Entity spawning: Overworld only, exclude mushroom islands (dual check: tag + registry ID)
        if (isOverworld(biome) && !isMushroom(biome)) {
            addEntitySpawns(builder.getMobSpawnSettings());
        }
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
     * 判断是否为蘑菇岛生物群系（Tag + 注册ID双重判定，避免Tag未绑定导致失效）
     * Check if the biome is a mushroom biome (dual check to avoid unbound tag issues)
     *
     * @param biome 生物群系Holder / biome holder
     * @return 是否为蘑菇岛 / whether it's a mushroom biome
     */
    private static boolean isMushroom(Holder<Biome> biome) {
        // 优先使用Tag判定
        if (biome.is(IS_MUSHROOM_TAG)) {
            return true;
        }
        // Tag可能未绑定，回退到注册ID判定
        return biome.unwrapKey()
                .map(key -> key.location().getPath().equals("mushroom_fields"))
                .orElse(false);
    }

    /**
     * 判断是否为主世界生物群系（Tag + 注册ID双重判定）
     * Check if the biome is an overworld biome (dual check: tag + registry ID)
     *
     * 回退逻辑采用保守策略：仅匹配原版命名空间下的已知主世界生物群系，
     * 模组维度（如暮色森林 twilightforest:xxx）的生物群系不会被误判为主世界。
     *
     * Fallback uses conservative strategy: only matches known vanilla overworld biomes,
     * mod dimensions (e.g. Twilight Forest) won't be misidentified as overworld.
     *
     * @param biome 生物群系Holder / biome holder
     * @return 是否为主世界 / whether it's overworld
     */
    private static boolean isOverworld(Holder<Biome> biome) {
        // 优先使用Tag判定
        if (biome.is(IS_OVERWORLD_TAG)) {
            return true;
        }
        // Tag未绑定时，保守处理：仅匹配原版命名空间下的已知主世界生物群系注册ID
        return biome.unwrapKey()
                .map(key -> {
                    String namespace = key.location().getNamespace();
                    String path = key.location().getPath();
                    // 非原版命名空间的生物群系直接返回false，避免模组维度被误判
                    if (!"minecraft".equals(namespace)) {
                        return false;
                    }
                    // 排除末地生物群系
                    if (path.equals("the_end") || path.equals("end_barrens")
                            || path.equals("end_highlands") || path.equals("end_midlands")
                            || path.equals("small_end_islands")) {
                        return false;
                    }
                    // 排除下界生物群系
                    if (path.equals("nether_wastes") || path.equals("soul_sand_valley")
                            || path.equals("crimson_forest") || path.equals("warped_forest")
                            || path.equals("basalt_deltas")) {
                        return false;
                    }
                    // 原版命名空间下，排除末地和下界后，剩余的就是主世界生物群系
                    return true;
                })
                .orElse(false);
    }

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

    // ═══ 实体生成 / Entity spawning ═══

    /**
     * 添加实体生成
     * Add entity spawning
     *
     * @param spawnBuilder 怪物生成设置构建器 / mob spawn settings builder
     */
    private void addEntitySpawns(MobSpawnSettingsBuilder spawnBuilder) {
        int lichWeight = pinned(ModConfig.KUVA_LICH.kuvaLichSpawnWeight);
        if (lichWeight > 0) {
            spawnBuilder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                    KuvaLichEntities.KUVA_MASTER.get(),
                    lichWeight,
                    pinned(ModConfig.KUVA_LICH.kuvaLichMinSpawnCount),
                    pinned(ModConfig.KUVA_LICH.kuvaLichMaxSpawnCount)
            ));
        }

        int slaveWeight = pinned(ModConfig.KUVA_LICH.kuvaSlaveSpawnWeight);
        if (slaveWeight > 0) {
            spawnBuilder.addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(
                    KuvaLichEntities.KUVA_SLAVE.get(),
                    slaveWeight,
                    pinned(ModConfig.KUVA_LICH.kuvaSlaveMinSpawnCount),
                    pinned(ModConfig.KUVA_LICH.kuvaSlaveMaxSpawnCount)
            ));
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return KuvaLichBiomeModifierSerializers.KUVALICH_MODIFIER_CODEC.get();
    }
}