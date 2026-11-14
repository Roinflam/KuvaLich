package pers.roinflam.kuvalich.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.item.*;
import pers.roinflam.kuvalich.item.card.*;
import pers.roinflam.kuvalich.item.module.weapon.*;
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

    /** 万能卡：不在下面 0~7 的随机池里，只靠处决失败时掉 */
    public static final RegistryObject<Item> OULL_CARD = ITEMS.register("oull",
            () -> new OULL(new Item.Properties()));

    /**
     * 根据ID获取安魂卡片
     * Get requiem card by ID
     *
     * @param id 卡片ID (0-8，8 是 Oull)
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
            case OULL.ID: return OULL_CARD.get();
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
            () -> new WeaponCommonModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_UNCOMMON_MODULE = ITEMS.register("item_uncommon_module",
            () -> new WeaponUncommonModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_RARE_MODULE = ITEMS.register("item_rare_module",
            () -> new WeaponRareModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_PRIME_MODULE = ITEMS.register("item_prime_module",
            () -> new WeaponPrimeModule(new Item.Properties()));

    public static final RegistryObject<Item> ITEM_RIVEN_MODULE = ITEMS.register("item_riven_module",
            () -> new WeaponRivenModule(new Item.Properties()));

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
     * 颜色：主色 0x8B0000 (深红，红甲), 次色 0x35363F (枪灰，连体服)
     */
    public static final RegistryObject<Item> KUVA_SLAVE_SPAWN_EGG = ITEMS.register("kuva_slave_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    KuvaLichEntities.KUVA_SLAVE,
                    0x8B0000,  // 主色：深红色
                    0x35363F,  // 次色：枪灰色
                    new Item.Properties()
            ));

    /**
     * 赤毒玄骸刷怪蛋
     * Kuva Master Spawn Egg
     *
     * 颜色：主色 0x3D3B4B (暗钢), 次色 0xC09846 (旧金)
     */
    public static final RegistryObject<Item> KUVA_MASTER_SPAWN_EGG = ITEMS.register("kuva_master_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    KuvaLichEntities.KUVA_MASTER,
                    0x3D3B4B,  // 主色：暗钢
                    0xC09846,  // 次色：旧金
                    new Item.Properties()
            ));

    /**
     * 赤毒幼体刷怪蛋。蛋孵出来的幼体没有主人，谁打倒谁处决（自己还没有玄骸才行）
     * Kuva Larvling Spawn Egg
     *
     * 颜色：主色 0x6E1A22（暗赤），次色 0xC9B79C（骨白）
     */
    public static final RegistryObject<Item> KUVA_LARVLING_SPAWN_EGG = ITEMS.register("kuva_larvling_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    KuvaLichEntities.KUVA_LARVLING,
                    0xBEB09A,  // 主色：灰沙（兜帽）
                    0xC41A36,  // 次色：赤毒红（晶体）
                    new Item.Properties()
            ));

    public static final RegistryObject<Item> FORMA = ITEMS.register("forma",
            () -> new Forma(new Item.Properties()));

    // 暗影塑形块：无视锁定洗面板并解锁；无配方，仅创造模式获取
    public static final RegistryObject<Item> SHADOW_FORMA = ITEMS.register("shadow_forma",
            () -> new pers.roinflam.kuvalich.item.ShadowForma(new Item.Properties()));

    // 内融核心（模组等级升级消耗品）/ Endo (Module level upgrade material)
    public static final RegistryObject<Item> ENDO = ITEMS.register("endo",
            () -> new pers.roinflam.kuvalich.item.Endo(new Item.Properties()));

    // 冒险指南：右键打开自带指南界面（取代帕秋莉教程书；注册名沿用旧书的模型/贴图名 kuvalich_guide）
    public static final RegistryObject<Item> KUVALICH_GUIDE = ITEMS.register("kuvalich_guide",
            () -> new pers.roinflam.kuvalich.item.GuideBook(new Item.Properties()));

    // 幻纹：款式在 NBT 里，没有配方
    public static final RegistryObject<Item> EPHEMERA = ITEMS.register("ephemera",
            () -> new pers.roinflam.kuvalich.ephemera.EphemeraItem(new Item.Properties()));
}