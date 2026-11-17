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

    private static final Map<String, String> THEMES = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    static {
        // 武器模组
        put("melee", "meleeDamage", "killStackBaseDamage");
        put("attack_speed", "attackSpeed", "killStackAttackSpeed");
        put("attack_range", "attackRange", "killStackAttackRange");
        put("slash", "slash");
        put("puncture", "puncture");
        put("impact", "impact");
        put("bane_other", "bane_of_undefined");
        put("bane_undead", "bane_of_undead");
        put("bane_arthropod", "bane_of_arthropod");
        put("bane_illager", "bane_of_illager");
        put("crit", "meleeCriticalStrikeProbability");
        put("crit_multiplier", "meleeCriticalStrikeMultiplier", "killStackMeleeCriticalMultiplier");
        put("remote_crit", "remoteCriticalStrikeProbability", "remoteCriticalStrikeMultiplier");
        put("headshot", "headshot_damage");
        put("remote", "remoteDamage");
        put("arrow", "arrowDamage");
        put("projectile", "projectileDamage");
        put("multishot", "multishot", "killStackMultishot");
        put("firing_rate", "firing_rate", "killStackFiringRate");
        put("reload", "reload_speed");
        put("magazine", "magazine_size");
        put("projectile_speed", "projectile_speed");
        put("recoil", "recoil_reduction");
        put("gun_damage", "gun_damage");
        put("aim", "aim_time");
        put("accuracy", "accuracy");
        put("true_bullet", "true_bullet");
        put("magic", "magicDamage");
        put("trigger", "triggerChance", "killStackTriggerChance");
        put("trigger_time", "triggerTime");
        put("fire", "fire");
        put("explosion", "explosion");
        put("burst_radius", "bursting_radius", "killStackBurstingRadius");
        put("ice", "ice");
        put("electricity", "electricity");
        put("magnetic", "magnetic");
        put("poison", "poison");
        put("gas", "gas");
        put("radiation", "radiation");
        put("corrosion", "corrosion");
        put("virus", "virus");
        put("loot", "gun_loot_drop");
        put("execute", "execute_threshold");
        put("execute_chance", "execute_chance");
        put("purge", "purge_buff");
        put("not_crit", "baseDamageWhenNotCriticalStrike");
        put("dash", "dashMeleeCriticalStrikeProbability", "dashAttackRange", "dashTriggerChance");

        // 战甲模组
        put("health", "health", "fixedHealth", "killStackHealth");
        put("armor", "armor", "fixedArmor", "killStackArmor");
        // 护盾和护盾回充共用信标那张
        put("shield_recharge", "shield", "fixedShield", "killStackShield", "shieldRecoveryRate", "shieldRecoveryDelay",
                "killStackShieldRecoveryRate", "killStackShieldRecoveryDelay");
        put("knockback", "knockbackResistance");
        put("homologous", "homologousProtection", "killStackHomologousProtection");
        put("fire_protection", "fireProtection", "killStackFireProtection");
        put("electric_protection", "electricProtection", "killStackElectricProtection");
        put("sprint", "sprintSpeed", "killStackSprintSpeed");
        put("jump", "jumpBoost");
        put("fall", "fallProtection");
        put("response", "responseRate", "killStackResponseRate");
        put("reach", "reachDistance");
        put("digging", "diggingSpeed", "killStackDiggingSpeed");
        put("item_drop", "itemDropMultiplier", "killStackItemDropMultiplier");
        put("ore_drop", "oreDropMultiplier");
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
                return "melee";
            case SILVER:
                return "ice";
            case GOLD:
                return "firing_rate";
            case PRIME:
                return "magic";
            case RIVEN:
                return "poison";
            default:
                return "execute";
        }
    }

    static ResourceLocation texture(String theme) {
        return TEXTURES.computeIfAbsent(theme,
                t -> new ResourceLocation(Reference.MOD_ID, "textures/gui/hover_card/art/" + t + ".png"));
    }
}
