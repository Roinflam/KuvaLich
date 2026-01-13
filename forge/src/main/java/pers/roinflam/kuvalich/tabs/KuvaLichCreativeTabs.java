package pers.roinflam.kuvalich.tabs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 创造模式标签页注册类（1.20.1版本）
 * Creative mode tabs registration class (1.20.1 version)
 *
 * 1.20.1引入了新的创造标签页系统
 * 1.20.1 introduced new creative tab system
 */
public class KuvaLichCreativeTabs {

    /**
     * 创造标签页延迟注册器
     * Creative tabs deferred register
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Reference.MOD_ID);

    /**
     * 赤毒玄骸标签页
     * Kuva Lich Creative Tab
     */
    public static final RegistryObject<CreativeModeTab> KUVA_LICH_TAB = CREATIVE_MODE_TABS.register("kuvalich_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kuvalich_tab"))
                    .icon(() -> new ItemStack(KuvaLichItems.KUVA.get()))
                    .displayItems((parameters, output) -> {
                        // ========== 安魂卡 / Requiem Cards ==========
                        output.accept(KuvaLichItems.FASS_CARD.get());
                        output.accept(KuvaLichItems.JAHU_CARD.get());
                        output.accept(KuvaLichItems.KHRA_CARD.get());
                        output.accept(KuvaLichItems.LOHK_CARD.get());
                        output.accept(KuvaLichItems.NETRA_CARD.get());
                        output.accept(KuvaLichItems.RIS_CARD.get());
                        output.accept(KuvaLichItems.VOME_CARD.get());
                        output.accept(KuvaLichItems.XATA_CARD.get());

                        // ========== 基础物品 / Basic Items ==========
                        output.accept(KuvaLichItems.KUVA.get());
                        output.accept(KuvaLichItems.REQUIEM_GEM.get());
                        output.accept(KuvaLichItems.REQUIEM_RIDDLE.get());
                        output.accept(KuvaLichItems.REQUIEM_ULTIMATUM.get());
                        output.accept(KuvaLichItems.LICH_RELIQUARY.get());
                        output.accept(KuvaLichItems.RIVEN_SLIVER.get());

                        // ========== 赤毒武器 / Kuva Weapons ==========
                        // 使用KuvaWeapon.getItem生成带属性的武器实例
                        int minLevel = ModConfig.KUVA_LICH.baseMinimumLevel.get();
                        int maxLevel = ModConfig.KUVA_LICH.baseMaximumLevel.get();

                        output.accept(KuvaWeapon.getItem(KuvaLichItems.KUVA_SHILDEG.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.PENNANT.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.GUANDAO_PRIME.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.PARACESIS.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.ARCA_TITRON.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.REAPER_PRIME.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.VITRICA.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.GRAM_PRIME.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.SANCTI_MAGISTAR.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.DESTREZA_PRIME.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.PRISMA_VERITUX.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.MACHETE.get(), minLevel, maxLevel));
                        output.accept(KuvaWeapon.getItem(KuvaLichItems.CAUSTACYST.get(), minLevel, maxLevel));

                        // ========== 方块 / Blocks ==========
                        output.accept(KuvaLichBlocks.REQUIEM_ORE.get());
                        output.accept(KuvaLichBlocks.EXPERIENCE_ORE.get());
                        output.accept(KuvaLichBlocks.REQUIEM_GATE.get());
                        output.accept(KuvaLichBlocks.REQUIEM_RECAST.get());
                        output.accept(KuvaLichBlocks.REQUIEM_EVOLVE.get());
                        output.accept(KuvaLichBlocks.REQUIEM_WEAPON_TABLE.get());
                        output.accept(KuvaLichBlocks.REQUIEM_WARFRAME_TABLE.get());

                        // ========== 武器模组 / Item Modules ==========
                        ItemCommonModule.registerCreativeTabItems(output);
                        ItemUncommonModule.registerCreativeTabItems(output);
                        ItemRareModule.registerCreativeTabItems(output);
                        ItemPrimeModule.registerCreativeTabItems(output);
                        ItemRivenModule.registerCreativeTabItems(output);

                        // ========== 战甲模组 / Warframe Modules ==========
                        WarframeCommonModule.registerCreativeTabItems(output);
                        WarframeUncommonModule.registerCreativeTabItems(output);
                        WarframeRareModule.registerCreativeTabItems(output);
                        WarframePrimeModule.registerCreativeTabItems(output);
                        WarframeRivenModule.registerCreativeTabItems(output);
                    })
                    .build());
}