package pers.roinflam.kuvalich.module.weapon.panel;

import pers.roinflam.kuvalich.module.KillStackManager.StackType;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 击杀叠层词条 key → 叠层类型的完整映射（武器 8 种 + 战甲 12 种）
 *
 * <p>改造前这份对应关系以 {@code switch} 的形式在四个地方各写了一遍：
 * {@code AbstractItemModule.getMaxStacksForAttribute}、
 * {@code AbstractWarframeModule.getMaxStacksForAttribute}、
 * {@code WeaponCombatHandler.applyKillStackEffects}、
 * {@code WeaponCombatHandler.addWeaponKillStacks}。
 * 加一种叠层要同时改四处，漏改任何一处都不报错。</p>
 *
 * @author RoinFlam
 */
public final class KillStackKeys {

    /** 武器类叠层：词条 key → 叠层类型 */
    public static final Map<String, StackType> WEAPON;

    /** 战甲类叠层：词条 key → 叠层类型 */
    public static final Map<String, StackType> WARFRAME;

    /** 两者合并（查询用） */
    public static final Map<String, StackType> ALL;

    static {
        LinkedHashMap<String, StackType> w = new LinkedHashMap<>();
        w.put("killStackBaseDamage", StackType.BASE_DAMAGE);
        w.put("killStackMultishot", StackType.MULTISHOT);
        w.put("killStackMeleeCriticalMultiplier", StackType.MELEE_CRIT_MULT);
        w.put("killStackTriggerChance", StackType.TRIGGER_CHANCE);
        w.put("killStackAttackRange", StackType.ATTACK_RANGE);
        w.put("killStackAttackSpeed", StackType.ATTACK_SPEED);
        w.put("killStackBurstingRadius", StackType.BURSTING_RADIUS);
        w.put("killStackFiringRate", StackType.FIRING_RATE);
        WEAPON = Collections.unmodifiableMap(w);

        LinkedHashMap<String, StackType> f = new LinkedHashMap<>();
        f.put("killStackHealth", StackType.WARFRAME_HEALTH);
        f.put("killStackShield", StackType.WARFRAME_SHIELD);
        f.put("killStackArmor", StackType.WARFRAME_ARMOR);
        f.put("killStackSprintSpeed", StackType.WARFRAME_SPRINT_SPEED);
        f.put("killStackShieldRecoveryRate", StackType.WARFRAME_SHIELD_RECOVERY_RATE);
        f.put("killStackShieldRecoveryDelay", StackType.WARFRAME_SHIELD_RECOVERY_DELAY);
        f.put("killStackFireProtection", StackType.WARFRAME_FIRE_PROTECTION);
        f.put("killStackElectricProtection", StackType.WARFRAME_ELECTRIC_PROTECTION);
        f.put("killStackHomologousProtection", StackType.WARFRAME_HOMOLOGOUS_PROTECTION);
        f.put("killStackResponseRate", StackType.WARFRAME_RESPONSE_RATE);
        f.put("killStackItemDropMultiplier", StackType.WARFRAME_ITEM_DROP_MULTIPLIER);
        f.put("killStackDiggingSpeed", StackType.WARFRAME_DIGGING_SPEED);
        WARFRAME = Collections.unmodifiableMap(f);

        LinkedHashMap<String, StackType> all = new LinkedHashMap<>(w);
        all.putAll(f);
        ALL = Collections.unmodifiableMap(all);
    }

    /**
     * 查这个属性 key 对应哪种叠层
     *
     * @param attributeKey 属性 key
     * @return 叠层类型；不是叠层词条时返回 null
     */
    @Nullable
    public static StackType typeOf(String attributeKey) {
        return ALL.get(attributeKey);
    }

    private KillStackKeys() {
    }
}
