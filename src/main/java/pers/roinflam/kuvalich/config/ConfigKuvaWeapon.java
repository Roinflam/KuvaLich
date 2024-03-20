package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.kuvalich.utils.Reference;

@Config(modid = Reference.MOD_ID, category = "KuvaWeapon")
public class ConfigKuvaWeapon {
    @Config.Comment("Set the degree of influence of the Kuva weapon attribute value, the greater the value, the greater the difference.")
    @Config.RangeDouble(min = 0)
    public static double attributeMultiplier = 1.5;

    @Config.Comment("Sets the base attack damage of Kuva Shildeg.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageKuvaShildeg = 44;

    @Config.Comment("Sets the base attack speed of Kuva Shildeg.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedKuvaShildeg = 0.7;

    @Config.Comment("Sets the base movement speed of Kuva Shildeg.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedKuvaShildeg = -0.2;

    @Config.Comment("Sets the base attack damage of Pennant.")
    @Config.RangeDouble(min = 0)
    public static double attackDamagePennant = 28;

    @Config.Comment("Sets the base attack speed of Pennant.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedPennant = 1.1;

    @Config.Comment("Sets the base movement speed of Pennant.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedPennant = 0.025;

    @Config.Comment("Sets the base attack damage of Guandao Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageGuandaoPrime = 16;

    @Config.Comment("Sets the base attack speed of Guandao Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedGuandaoPrime = 2;

    @Config.Comment("Sets the base movement speed of Guandao Prime.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedGuandaoPrime = 0.075;

    @Config.Comment("Sets the base attack damage of Paracesis.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageParacesis = 34;

    @Config.Comment("Sets the base attack speed of Paracesis.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedParacesis = 0.9;

    @Config.Comment("Sets the base movement speed of Paracesis.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedParacesis = -0.1;

    @Config.Comment("Sets the base attack damage of Arca Titron.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageArcaTitron = 38;

    @Config.Comment("Sets the base attack speed of Arca Titron.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedArcaTitron = 0.75;

    @Config.Comment("Sets the base movement speed of Arca Titron.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedArcaTitron = -0.15;

    @Config.Comment("Sets the base attack damage of Reaper Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageReaperPrime = 18;

    @Config.Comment("Sets the base attack speed of Reaper Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedReaperPrime = 1.75;

    @Config.Comment("Sets the base movement speed of Reaper Prime.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedReaperPrime = 0.05;

    @Config.Comment("Sets the base attack damage of Vitrica.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageVitrica = 50;

    @Config.Comment("Sets the base attack speed of Vitrica.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedVitrica = 0.65;

    @Config.Comment("Sets the base movement speed of Vitrica.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedVitrica = -0.2;

    @Config.Comment("Sets the base attack damage of Gram Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageGramPrime = 46;

    @Config.Comment("Sets the base attack speed of Gram Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedGramPrime = 0.7;

    @Config.Comment("Sets the base movement speed of Gram Prime.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedGramPrime = -0.15;

    @Config.Comment("Sets the base attack damage of Sancti Magistar.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageSanctiMagistar = 36;

    @Config.Comment("Sets the base attack speed of Sancti Magistar.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedSanctiMagistar = 0.85;

    @Config.Comment("Sets the base movement speed of Sancti Magistar.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedSanctiMagistar = -0.075;

    @Config.Comment("Sets the base attack damage of Destreza Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageDestrezaPrime = 24;

    @Config.Comment("Sets the base attack speed of Destreza Prime.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedDestrezaPrime = 1.4;

    @Config.Comment("Sets the base movement speed of Destreza Prime.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedDestrezaPrime = 0.125;

    @Config.Comment("Sets the base attack damage of Prisma Veritux.")
    @Config.RangeDouble(min = 0)
    public static double attackDamagePrismaVeritux = 100;

    @Config.Comment("Sets the base attack speed of Prisma Veritux.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedPrismaVeritux = 0.45;

    @Config.Comment("Sets the base movement speed of Prisma Veritux.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedPrismaVeritux = -0.6;

    @Config.Comment("Sets the base attack damage of Machete.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageMachete = 32;

    @Config.Comment("Sets the base attack speed of Machete.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedMachete = 1.55;

    @Config.Comment("Sets the base movement speed of Machete.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedMachete = 0.025;

    @Config.Comment("Sets the base attack damage of Caustacyst.")
    @Config.RangeDouble(min = 0)
    public static double attackDamageCaustacyst = 28;

    @Config.Comment("Sets the base attack speed of Caustacyst.")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedCaustacyst = 1.3;

    @Config.Comment("Sets the base movement speed of Caustacyst.")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedCaustacyst = 0.125;
}
