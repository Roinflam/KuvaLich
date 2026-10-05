package pers.roinflam.kuvalich.init;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.worldgen.KuvaSpawnLimiter;

/**
 * 生物生成位置规则注册
 * Spawn placement rules registration
 *
 * <p>亮度规则与原版僵尸完全一致：直接复用 {@link Monster#checkMonsterSpawnRules}，
 * 也就是 {@code 难度 != 和平 && 亮度足够暗 && 通用怪物判定} 三条。
 * 原版 {@code EntityType.ZOMBIE} 注册的就是这个方法引用，放置类型与高度图同样是
 * {@code ON_GROUND + MOTION_BLOCKING_NO_LEAVES}，所以这里三项全部对齐僵尸。</p>
 *
 * <p>⚠ 不要再改回 {@code (type, level, spawnType, pos, random) -> true}。
 * 对怪物来说亮度判定只存在于 SpawnPlacements 这一处（{@code Monster} 没有重写实例方法
 * {@code checkSpawnRules}），无条件返回 true 会让赤毒实体在大白天的地表、火把底下、
 * 点满灯的基地地板上照刷，并且连"和平难度不生成"也一起失效。</p>
 *
 * <p>自然生成额外多一道密度限制：附近同类已经够多、或离同类太近就不刷，见 {@link KuvaSpawnLimiter}。
 * 只对 {@code MobSpawnType.NATURAL} 生效，刷怪笼等其它来源走原有规则。</p>
 *
 * <p>Light rules are identical to vanilla zombies: reuse {@link Monster#checkMonsterSpawnRules}
 * (difficulty != PEACEFUL && dark enough && generic monster checks). Vanilla registers that exact
 * method reference for {@code EntityType.ZOMBIE}, with the same placement type and heightmap.</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KuvaLichSpawnPlacements {

    /**
     * 注册生物生成规则
     * Register spawn placement rules
     */
    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // 赤毒奴仆：亮度规则同僵尸（需要足够暗），自然生成时再看附近同类够不够多
        // Kuva Slave: same light rules as a zombie (needs darkness); natural spawns also respect the crowding limit
        event.register(
                KuvaLichEntities.KUVA_SLAVE.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)
                                && (spawnType != MobSpawnType.NATURAL
                                || KuvaSpawnLimiter.hasRoom(level, type, pos,
                                ModConfig.KUVA_LICH.kuvaSlaveMaxNearby.get(),
                                ModConfig.KUVA_LICH.kuvaSlaveNearbyRadius.get(),
                                ModConfig.KUVA_LICH.kuvaSlaveMinSpacing.get())),
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );

        // 赤毒玄骸：同上
        // Kuva Master: same as above
        event.register(
                KuvaLichEntities.KUVA_MASTER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) ->
                        Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)
                                && (spawnType != MobSpawnType.NATURAL
                                || KuvaSpawnLimiter.hasRoom(level, type, pos,
                                ModConfig.KUVA_LICH.kuvaLichMaxNearby.get(),
                                ModConfig.KUVA_LICH.kuvaLichNearbyRadius.get(),
                                ModConfig.KUVA_LICH.kuvaLichMinSpacing.get())),
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }
}
