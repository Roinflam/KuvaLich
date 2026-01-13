package pers.roinflam.kuvalich.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 实体注册类（尺寸修正版）
 * Entity registration class (size corrected)
 */
public class KuvaLichEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Reference.MOD_ID);

    /**
     * 赤毒奴仆
     * Kuva Slave
     *
     * 原代码尺寸：1.5F x 2.9F
     * Original size: 1.5F x 2.9F
     */
    public static final RegistryObject<EntityType<EntityKuvaSlave>> KUVA_SLAVE = ENTITY_TYPES.register("kuva_slave",
            () -> EntityType.Builder.of(EntityKuvaSlave::new, MobCategory.MONSTER)
                    .sized(1.5F, 2.9F)  // ✅ 修正尺寸
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("kuva_slave"));

    /**
     * 赤毒玄骸
     * Kuva Master
     *
     * 原代码尺寸：1.75F x 3.4F
     * Original size: 1.75F x 3.4F
     */
    public static final RegistryObject<EntityType<EntityKuvaMaster>> KUVA_MASTER = ENTITY_TYPES.register("kuva_master",
            () -> EntityType.Builder.of(EntityKuvaMaster::new, MobCategory.MONSTER)
                    .sized(1.75F, 3.4F)  // ✅ 修正尺寸
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .fireImmune()
                    .build("kuva_master"));
}