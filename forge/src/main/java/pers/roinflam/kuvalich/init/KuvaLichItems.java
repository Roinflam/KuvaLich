package pers.roinflam.kuvalich.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.item.*;
import pers.roinflam.kuvalich.item.card.*;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.item.weapon.*;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 赤毒玄骸物品注册类（1.20.1完整版）
 * Kuva Lich Items Registration (1.20.1 complete version)
 */
public class KuvaLichItems {

    /**
     * DeferredRegister用于注册物品
     * DeferredRegister for item registration
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

    /**
     * 赤毒武器列表（用于随机生成）
     * Kuva weapons list (for random generation)
     */
    public static final List<Item> KUVA_WEAPONS = new ArrayList<>();

    // ==================== 安魂卡片 / Requiem Cards ====================

    public static final RegistryObject<Item> KHRA_CARD = ITEMS.register("khra",
            () -> new KHRA(new Item.Properties()));

    public static final RegistryObject<Item> JAHU_CARD = ITEMS.register("jahu",
            () -> new JAHU(new Item.Properties()));

    public static final RegistryObject<Item> FASS_CARD = ITEMS.register("fass",
            () -> new FASS(new Item.Properties()));

    public static final RegistryObject<Item> LOHK_CARD = ITEMS.register("lohk",
            () -> new LOHK(new Item.Properties()));

    public static final RegistryObject<Item> NETRA_CARD = ITEMS.register("netra",
            () -> new NETRA(new Item.Properties()));

    public static final RegistryObject<Item> VOME_CARD = ITEMS.register("vome",
            () -> new VOME(new Item.Properties()));

    public static final RegistryObject<Item> XATA_CARD = ITEMS.register("xata",
            () -> new XATA(new Item.Properties()));

    public static final RegistryObject<Item> RIS_CARD = ITEMS.register("ris",
            () -> new RIS(new Item.Properties()));

    /**
     * 根据ID获取安魂卡片（业务逻辑100%不变）
     * Get requiem card by ID (business logic 100% unchanged)
     *
     * @param id 卡片ID (0-7)
     * @return 对应的安魂卡片
     */
    public static Item getRequiemCard(int id) {
        switch (id) {
            case 0: return KHRA_CARD.get();
            case 1: return JAHU_CARD.get();
            case 2: return FASS_CARD.get();
            case 3: return LOHK_CARD.get();
            case 4: return NETRA_CARD.get();
            case 5: return VOME_CARD.get();
            case 6: return XATA_CARD.get();
            case 7: return RIS_CARD.get();
            default: return KHRA_CARD.get();
        }
    }

    // ==================== 赤毒武器 / Kuva Weapons ====================

    public static final RegistryObject<Item> KUVA_SHILDEG = ITEMS.register("kuva_shildeg",
            () -> {
                Item weapon = new KuvaShildeg(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> PENNANT = ITEMS.register("pennant",
            () -> {
                Item weapon = new Pennant(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> GUANDAO_PRIME = ITEMS.register("guandao_prime",
            () -> {
                Item weapon = new GuandaoPrime(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> PARACESIS = ITEMS.register("paracesis",
            () -> {
                Item weapon = new Paracesis(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> ARCA_TITRON = ITEMS.register("arca_titron",
            () -> {
                Item weapon = new ArcaTitron(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> REAPER_PRIME = ITEMS.register("reaper_prime",
            () -> {
                Item weapon = new ReaperPrime(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> VITRICA = ITEMS.register("vitrica",
            () -> {
                Item weapon = new Vitrica(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> GRAM_PRIME = ITEMS.register("gram_prime",
            () -> {
                Item weapon = new GramPrime(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> SANCTI_MAGISTAR = ITEMS.register("sancti_magistar",
            () -> {
                Item weapon = new SanctiMagistar(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> DESTREZA_PRIME = ITEMS.register("destreza_prime",
            () -> {
                Item weapon = new DestrezaPrime(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> PRISMA_VERITUX = ITEMS.register("prisma_veritux",
            () -> {
                Item weapon = new PrismaVeritux(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> MACHETE = ITEMS.register("machete",
            () -> {
                Item weapon = new Machete(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    public static final RegistryObject<Item> CAUSTACYST = ITEMS.register("caustacyst",
            () -> {
                Item weapon = new Caustacyst(new Item.Properties());
                KUVA_WEAPONS.add(weapon);
                return weapon;
            });

    // ==================== 材料物品 / Material Items ====================

    public static final RegistryObject<Item> REQUIEM_GEM = ITEMS.register("requiem_gem",
            () -> new RequiemGem(new Item.Properties()));

    public static final RegistryObject<Item> REQUIEM_RIDDLE = ITEMS.register("requiem_riddle",
            () -> new RequiemRiddle(new Item.Properties()));

    public static final RegistryObject<Item> REQUIEM_ULTIMATUM = ITEMS.register("requiem_ultimatum",
            () -> new RequiemUltimatum(new Item.Properties()));

    public static final RegistryObject<Item> LICH_RELIQUARY = ITEMS.register("lich_reliquary",
            () -> new LichReliquary(new Item.Properties()));

    public static final RegistryObject<Item> KUVA = ITEMS.register("kuva",
            () -> new Kuva(new Item.Properties()));

    public static final RegistryObject<Item> RIVEN_SLIVER = ITEMS.register("riven_sliver",
            () -> new RivenSliver(new Item.Properties()));

    // ==================== 武器模组 / Item Modules ====================

    public static final RegistryObject<Item> ITEM_COMMON_MODULE = ITEMS.register("item_common_module",
            () -> new ItemCommonModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_UNCOMMON_MODULE = ITEMS.register("item_uncommon_module",
            () -> new ItemUncommonModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_RARE_MODULE = ITEMS.register("item_rare_module",
            () -> new ItemRareModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_PRIME_MODULE = ITEMS.register("item_prime_module",
            () -> new ItemPrimeModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_RIVEN_MODULE = ITEMS.register("item_riven_module",
            () -> new ItemRivenModule(new Item.Properties()));

    // ==================== 战甲模组 / Warframe Modules ====================

    public static final RegistryObject<Item> WARFRAME_COMMON_MODULE = ITEMS.register("warframe_common_module",
            () -> new WarframeCommonModule(new Item.Properties()));

    public static final RegistryObject<Item> WARFRAME_UNCOMMON_MODULE = ITEMS.register("warframe_uncommon_module",
            () -> new WarframeUncommonModule(new Item.Properties()));

    public static final RegistryObject<Item> WARFRAME_RARE_MODULE = ITEMS.register("warframe_rare_module",
            () -> new WarframeRareModule(new Item.Properties()));

    public static final RegistryObject<Item> WARFRAME_PRIME_MODULE = ITEMS.register("warframe_prime_module",
            () -> new WarframePrimeModule(new Item.Properties()));

    public static final RegistryObject<Item> WARFRAME_RIVEN_MODULE = ITEMS.register("warframe_riven_module",
            () -> new WarframeRivenModule(new Item.Properties()));

    /**
     * 赤毒奴仆刷怪蛋
     * Kuva Slave Spawn Egg
     *
     * 颜色：主色 0x8B0000 (深红), 次色 0x2F4F4F (深灰)
     */
    public static final RegistryObject<Item> KUVA_SLAVE_SPAWN_EGG = ITEMS.register("kuva_slave_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    KuvaLichEntities.KUVA_SLAVE,
                    0x8B0000,  // 主色：深红色
                    0x2F4F4F,  // 次色：深灰色
                    new Item.Properties()
            ));

    /**
     * 赤毒玄骸刷怪蛋
     * Kuva Master Spawn Egg
     *
     * 颜色：主色 0xDC143C (猩红), 次色 0x1C1C1C (黑色)
     */
    public static final RegistryObject<Item> KUVA_MASTER_SPAWN_EGG = ITEMS.register("kuva_master_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    KuvaLichEntities.KUVA_MASTER,
                    0xDC143C,  // 主色：猩红色
                    0x1C1C1C,  // 次色：黑色
                    new Item.Properties()
            ));

    public static final RegistryObject<Item> FORMA = ITEMS.register("forma",
            () -> new Forma(new Item.Properties()));

    // ⭐ 内融核心（模组等级升级消耗品）/ Endo (Module level upgrade material)
    public static final RegistryObject<Item> ENDO = ITEMS.register("endo",
            () -> new pers.roinflam.kuvalich.item.Endo(new Item.Properties()));
}