package pers.roinflam.kuvalich.client.tooltip.card;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashMap;
import java.util.Map;

/**
 * 词条到卡面图主题的对照表。改卡面图归属只改这里。
 *
 * <p>主题图在 {@code textures/gui/hover_card/art/<主题>.png}。表里没有的词条（整合包自定义词条）按卡的档位给默认图。</p>
 */
public final class HoverCardArt {

    public static final String FIRE = "fire";
    public static final String ICE = "ice";
    public static final String ELEC = "elec";
    public static final String STORM = "storm";
    public static final String VOID = "void";
    public static final String SPARK = "spark";
    public static final String AEGIS = "aegis";
    public static final String CRIT = "crit";
    public static final String FINISHER = "finisher";
    public static final String DASH = "dash";
    public static final String MELEE = "melee";

    private static final Map<String, String> THEMES = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    static {
        // 武器模组
        put(MELEE, "meleeDamage", "attackSpeed", "attackRange", "slash", "puncture", "impact",
                "bane_of_undefined", "bane_of_undead", "bane_of_arthropod", "bane_of_illager",
                "killStackBaseDamage", "killStackAttackRange", "killStackAttackSpeed");
        put(CRIT, "meleeCriticalStrikeProbability", "meleeCriticalStrikeMultiplier",
                "remoteCriticalStrikeProbability", "remoteCriticalStrikeMultiplier",
                "killStackMeleeCriticalMultiplier", "headshot_damage");
        put(SPARK, "remoteDamage", "arrowDamage", "projectileDamage", "multishot", "firing_rate",
                "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction", "gun_damage",
                "aim_time", "accuracy", "true_bullet", "killStackMultishot", "killStackFiringRate");
        put(STORM, "magicDamage", "triggerChance", "triggerTime", "killStackTriggerChance");
        put(FIRE, "fire", "explosion", "bursting_radius", "killStackBurstingRadius");
        put(ICE, "ice");
        put(ELEC, "electricity", "magnetic");
        put(VOID, "poison", "gas", "radiation", "corrosion", "virus", "gun_loot_drop");
        put(FINISHER, "execute_threshold", "execute_chance", "purge_buff", "baseDamageWhenNotCriticalStrike");
        put(DASH, "dashMeleeCriticalStrikeProbability", "dashAttackRange", "dashTriggerChance");

        // 战甲模组
        put(AEGIS, "health", "shield", "armor", "fixedHealth", "fixedShield", "fixedArmor",
                "shieldRecoveryRate", "shieldRecoveryDelay", "knockbackResistance", "homologousProtection",
                "killStackHealth", "killStackShield", "killStackArmor", "killStackShieldRecoveryRate",
                "killStackShieldRecoveryDelay", "killStackHomologousProtection");
        put(FIRE, "fireProtection", "killStackFireProtection");
        put(ELEC, "electricProtection", "killStackElectricProtection");
        put(DASH, "sprintSpeed", "killStackSprintSpeed", "jumpBoost", "fallProtection", "responseRate",
                "killStackResponseRate");
        put(VOID, "reachDistance", "diggingSpeed", "itemDropMultiplier", "oreDropMultiplier",
                "killStackItemDropMultiplier", "killStackDiggingSpeed");
    }

    private HoverCardArt() {
    }

    private static void put(String theme, String... attributes) {
        for (String a : attributes) {
            THEMES.put(a, theme);
        }
    }

    /** 词条对应的主题；表里没有返回 null */
    public static String themeOf(String attribute) {
        return THEMES.get(attribute);
    }

    static String defaultTheme(CardTier tier) {
        switch (tier) {
            case BRONZE:
                return MELEE;
            case SILVER:
                return ICE;
            case GOLD:
                return SPARK;
            case PRIME:
                return STORM;
            case RIVEN:
                return VOID;
            default:
                return FIRE;
        }
    }

    static ResourceLocation texture(String theme) {
        return TEXTURES.computeIfAbsent(theme,
                t -> new ResourceLocation(Reference.MOD_ID, "textures/gui/hover_card/art/" + t + ".png"));
    }
}
