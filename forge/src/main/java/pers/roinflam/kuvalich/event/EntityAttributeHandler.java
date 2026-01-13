package pers.roinflam.kuvalich.event;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 实体属性注册事件处理器
 * Entity Attribute Registration Event Handler
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EntityAttributeHandler {

    /**
     * 注册实体属性（必须在MOD事件总线上监听）
     * Register entity attributes (must listen on MOD event bus)
     */
    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        // 注册赤毒奴仆的属性
        event.put(KuvaLichEntities.KUVA_SLAVE.get(), EntityKuvaSlave.createAttributes().build());

        // 注册赤毒玄骸的属性
        event.put(KuvaLichEntities.KUVA_MASTER.get(), EntityKuvaMaster.createAttributes().build());
    }
}