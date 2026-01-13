package pers.roinflam.kuvalich.init;

import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 生物生成位置规则注册（无亮度限制版）
 * Spawn placement rules registration (no light level restriction)
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KuvaLichSpawnPlacements {

    /**
     * 注册生物生成规则
     * 设置为无亮度限制，白天黑夜都能生成
     *
     * Register spawn placement rules
     * Set to no light level restriction, can spawn day and night
     */
    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        // 赤毒奴仆：无亮度限制（白天黑夜都能生成）
        // Kuva Slave: no light level restriction (can spawn day and night)
        event.register(
                KuvaLichEntities.KUVA_SLAVE.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnType, pos, random) -> true,  // 无条件生成
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );

        // 赤毒玄骸：无亮度限制（白天黑夜都能生成）
        // Kuva Master: no light level restriction (can spawn day and night)
        event.register(
                KuvaLichEntities.KUVA_MASTER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (entityType, level, spawnType, pos, random) -> true,  // 无条件生成
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }
}