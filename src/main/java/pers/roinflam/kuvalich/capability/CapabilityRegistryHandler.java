// 文件：CapabilityRegistryHandler.java - 优化版
package pers.roinflam.kuvalich.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.capability.provider.RequiemCardProvider;
import pers.roinflam.kuvalich.capability.provider.WarframeModuleProvider;
import pers.roinflam.kuvalich.utils.Reference;

@Mod.EventBusSubscriber
public class CapabilityRegistryHandler {
    @CapabilityInject(RequiemCard.class)
    public static Capability<RequiemCard> REQUIEM_CARD;

    @CapabilityInject(WarframeModules.class)
    public static Capability<WarframeModules> WARFRAME_MODULES;

    // 预创建ResourceLocation，避免重复创建
    private static final ResourceLocation REQUIEM_CARD_ID = new ResourceLocation(Reference.MOD_ID, "requiem_card");
    private static final ResourceLocation WARFRAME_MODULES_ID = new ResourceLocation(Reference.MOD_ID, "warframe_modules");

    public static void register() {
        // 注册 RequiemCard Capability
        CapabilityManager.INSTANCE.register(RequiemCard.class, new Capability.IStorage<RequiemCard>() {
            @Override
            public NBTBase writeNBT(Capability<RequiemCard> capability, RequiemCard instance, EnumFacing side) {
                return instance != null ? instance.serializeNBT() : new NBTTagCompound();
            }

            @Override
            public void readNBT(Capability<RequiemCard> capability, RequiemCard instance, EnumFacing side, NBTBase nbt) {
                if (instance != null && nbt instanceof NBTTagCompound) {
                    instance.deserializeNBT((NBTTagCompound) nbt);
                }
            }
        }, RequiemCard::new);

        // 注册 WarframeModules Capability
        CapabilityManager.INSTANCE.register(WarframeModules.class, new Capability.IStorage<WarframeModules>() {
            @Override
            public NBTBase writeNBT(Capability<WarframeModules> capability, WarframeModules instance, EnumFacing side) {
                return instance != null ? instance.serializeNBT() : new NBTTagCompound();
            }

            @Override
            public void readNBT(Capability<WarframeModules> capability, WarframeModules instance, EnumFacing side, NBTBase nbt) {
                if (instance != null && nbt instanceof NBTTagCompound) {
                    instance.deserializeNBT((NBTTagCompound) nbt);
                }
            }
        }, WarframeModules::new);
    }

    /**
     * 附加Capability到玩家实体
     * 优化：使用预创建的ResourceLocation，避免每次都创建新对象
     */
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof EntityPlayer)) {
            return;
        }

        // 附加RequiemCard
        if (!event.getObject().hasCapability(REQUIEM_CARD, null)) {
            event.addCapability(REQUIEM_CARD_ID, new RequiemCardProvider());
        }

        // 附加WarframeModules
        if (!event.getObject().hasCapability(WARFRAME_MODULES, null)) {
            event.addCapability(WARFRAME_MODULES_ID, new WarframeModuleProvider());
        }
    }
}