package pers.roinflam.kuvalich.init;

import net.minecraft.item.Item;
import pers.roinflam.kuvalich.item.*;
import pers.roinflam.kuvalich.item.card.*;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.item.weapon.*;
import pers.roinflam.kuvalich.tabs.KuvaLichTab;

import java.util.ArrayList;
import java.util.List;

public class KuvaLichItems {
    public static final List<Item> ITEMS = new ArrayList<Item>();
    public static final List<Item> KUVA_WEAPONS = new ArrayList<Item>();

    public static final FASS FASS_CARD = new FASS("fass");
    public static final JAHU JAHU_CARD = new JAHU("jahu");
    public static final KHRA KHRA_CARD = new KHRA("khra");
    public static final LOHK LOHK_CARD = new LOHK("lohk");
    public static final NETRA NETRA_CARD = new NETRA("netra");
    public static final RIS RIS_CARD = new RIS("ris");
    public static final VOME VOME_CARD = new VOME("vome");
    public static final XATA XATA_CARD = new XATA("xata");

    public static final KuvaShildeg KUVA_SHILDEG = new KuvaShildeg("kuva_shildeg");
    public static final Pennant PENNANT = new Pennant("pennant");
    public static final GuandaoPrime GUANDAO_PRIME = new GuandaoPrime("guandao_prime");
    public static final Paracesis PARACESIS = new Paracesis("paracesis");
    public static final ArcaTitron ARCA_TITRON = new ArcaTitron("arca_titron");
    public static final ReaperPrime REAPER_PRIME = new ReaperPrime("reaper_prime");
    public static final Vitrica VITRICA = new Vitrica("vitrica");
    public static final GramPrime GRAM_PRIME = new GramPrime("gram_prime");
    public static final SanctiMagistar SANCTI_MAGISTAR = new SanctiMagistar("sancti_magistar");
    public static final DestrezaPrime DESTREZA_PRIME = new DestrezaPrime("destreza_prime");
    public static final PrismaVeritux PRISMA_VERITUX = new PrismaVeritux("prisma_veritux");
    public static final Machete MACHETE = new Machete("machete");
    public static final Caustacyst CAUSTACYST = new Caustacyst("caustacyst");
    public static final Item REQUIEM_GEM = new RequiemGem("requiem_gem", KuvaLichTab.getTab());
    public static final Item REQUIEM_RIDDLE = new RequiemRiddle("requiem_riddle", KuvaLichTab.getTab());
    public static final Item LICH_RELIQUARY = new LichReliquary("lich_reliquary", KuvaLichTab.getTab());
    public static final Item KUVA = new Kuva("kuva", KuvaLichTab.getTab());
    public static final Item RivenSliver = new RivenSliver("riven_sliver", KuvaLichTab.getTab());

    public static final ItemCommonModule ITEM_COMMON_MODULE = new ItemCommonModule("item_common_module");
    public static final ItemUncommonModule ITEM_UNCOMMON_MODULE = new ItemUncommonModule("item_uncommon_module");
    public static final ItemRareModule ITEM_RARE_MODULE = new ItemRareModule("item_rare_module");
    public static final ItemPrimeModule ITEM_PRIME_MODULE = new ItemPrimeModule("item_prime_module");
    public static final ItemRivenModule ITEM_RIVEN_MODULE = new ItemRivenModule("item_riven_module");

    public static final WarframeCommonModule WARFRAME_COMMON_MODULE = new WarframeCommonModule("warframe_common_module");
    public static final WarframeUncommonModule WARFRAME_UNCOMMON_MODULE = new WarframeUncommonModule("warframe_uncommon_module");
    public static final WarframeRareModule WARFRAME_RARE_MODULE = new WarframeRareModule("warframe_rare_module");
    public static final WarframePrimeModule WARFRAME_PRIME_MODULE = new WarframePrimeModule("warframe_prime_module");
    public static final WarframeRivenModule WARFRAME_RIVEN_MODULE = new WarframeRivenModule("warframe_riven_module");
}
