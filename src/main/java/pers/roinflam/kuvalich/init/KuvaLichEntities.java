package pers.roinflam.kuvalich.init;

import net.minecraft.entity.EnumCreatureType;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;

import java.util.ArrayList;
import java.util.List;

public class KuvaLichEntities {

    public static final List<EntityEntry> ENTITIES = new ArrayList<EntityEntry>();

    public static final EntityEntry KUVA_SLAVE = EntityEntryBuilder.create()
            .entity(EntityKuvaSlave.class)
            .id(EntityKuvaSlave.ID, 0)
            .name(EntityKuvaSlave.NAME)
            .tracker(64, 1, true)
            .egg(0X4B0082, 0xDC143C)
            .spawn(EnumCreatureType.MONSTER,
                    ConfigKuvaLich.kuvaSlaveSpawnWeight,
                    ConfigKuvaLich.kuvaSlaveMinSpawnCount,
                    ConfigKuvaLich.kuvaSlaveMaxSpawnCount,
                    EntityKuvaSlave.BIOMES)
            .build();

    public static final EntityEntry KUVA_MASTER = EntityEntryBuilder.create()
            .entity(EntityKuvaMaster.class)
            .id(EntityKuvaMaster.ID, 1)
            .name(EntityKuvaMaster.NAME)
            .tracker(64, 1, true)
            .egg(0X9d00ff, 0X91001b)
            .spawn(EnumCreatureType.MONSTER,
                    ConfigKuvaLich.kuvaLichSpawnWeight,
                    ConfigKuvaLich.kuvaLichMinSpawnCount,
                    ConfigKuvaLich.kuvaLichMaxSpawnCount,
                    EntityKuvaSlave.BIOMES)
            .build();

    static {
        ENTITIES.add(KUVA_SLAVE);
        ENTITIES.add(KUVA_MASTER);
    }
}