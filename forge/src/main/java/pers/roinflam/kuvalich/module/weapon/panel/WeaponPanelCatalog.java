package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 武器面板的属性描述表 —— 全项目唯一的「面板上有哪些词条、各自怎么算怎么显示」的真相来源
 *
 * <p>改造前这份信息以 40 多个手写 if 块的形式散在
 * {@code WeaponModuleHandler.onItemTooltip}（约 200 行）里，每条词条要复制粘贴一整段
 * {@code Component.literal(I18n.get(...)).append(Component.literal(Math.round(v*100) + "%")...)}。
 * 加一条新词条要同时改这里、{@code ExtraSlotTooltipHelper.ATTRIBUTE_ORDER}、
 * {@code AbstractItemModule.ITEM_ATTRIBUTE_TYPES} 三处，漏改任何一处都不报错。</p>
 *
 * <p>现在这三处统一读这张表，{@link #findGaps} 在启动期自检漏登记的词条。</p>
 *
 * <p><b>加新词条的做法</b>：往 {@link #SPECS} 里加一行，再补两条 lang key
 * （{@code item.module.<key>} 长名、{@code kuvalich.attr.short.<key>} 短名）即可。</p>
 *
 * @author RoinFlam
 */
public final class WeaponPanelCatalog {

    // ==================== 面板属性表 ====================

    /**
     * 面板属性描述表。**顺序即显示顺序**（组内），分组顺序见 {@link PanelGroup} 的声明顺序。
     */
    public static final List<AttrSpec> SPECS = List.of(

            // ── PANEL：最终面板核心，即使为 0 也显示 ────────────────────────
            AttrSpec.of("damage", PanelGroup.PANEL, ValueFmt.PERCENT)
                    .withProviderInternal(ValueProvider.base("damage"))
                    .markAlways(),

            AttrSpec.of("criticalStrikeProbability", PanelGroup.PANEL, ValueFmt.PERCENT)
                    .withProviderInternal(ValueProvider.scaledBase("criticalStrikeProbability", "meleeCriticalStrikeProbability"))
                    .paired(ValueProvider.scaledBase("criticalStrikeProbability", "remoteCriticalStrikeProbability"))
                    .markAlways(),

            AttrSpec.of("criticalStrikeMultiplier", PanelGroup.PANEL, ValueFmt.MULTIPLIER)
                    .withProviderInternal(ValueProvider.scaledBase("criticalStrikeMultiplier", "meleeCriticalStrikeMultiplier"))
                    .paired(ValueProvider.scaledBase("criticalStrikeMultiplier", "remoteCriticalStrikeMultiplier"))
                    .stacked(StackType.MELEE_CRIT_MULT, StackFn.mul("killStackMeleeCriticalMultiplier"))
                    .markAlways(),

            AttrSpec.of("triggerChance", PanelGroup.PANEL, ValueFmt.PERCENT)
                    .withProviderInternal(ValueProvider.scaledBase("triggerChance", "triggerChance"))
                    .stacked(StackType.TRIGGER_CHANCE, StackFn.mul("killStackTriggerChance"))
                    .markAlways(),

            AttrSpec.pct("multishot", PanelGroup.PANEL)
                    .stacked(StackType.MULTISHOT, StackFn.add("killStackMultishot")),

            AttrSpec.of("attackSpeed", PanelGroup.PANEL, ValueFmt.PERCENT_1F)
                    .stacked(StackType.ATTACK_SPEED, StackFn.add("killStackAttackSpeed")),

            // ⭐ 射速：弓的蓄力加速是双倍生效的，显示口径跟着翻倍（与改造前一致）。
            //    叠层部分同样要乘这个系数，否则弓上的箭头会指向一个偏小的数。
            AttrSpec.of("firing_rate", PanelGroup.PANEL, ValueFmt.PERCENT_SIGNED)
                    .withProviderInternal((stack, attrs) -> attrs.getOrDefault("firing_rate", 0.0) * chargeFactor(stack))
                    .stacked(StackType.FIRING_RATE,
                            (stack, attrs, base, stacks) ->
                                    base + attrs.getOrDefault("killStackFiringRate", 0.0) * stacks * chargeFactor(stack))
                    .visibleWhen("firing_rate"),

            AttrSpec.pct("attackRange", PanelGroup.PANEL)
                    .stacked(StackType.ATTACK_RANGE, StackFn.add("killStackAttackRange")),

            // ⭐ 爆炸半径：面板口径是「最终半径」= 1 + v*2（绝对值），
            //    与额外槽位的「加了多少」= v*2（增量）是两种不同语义，不要统一。
            AttrSpec.of("bursting_radius", PanelGroup.PANEL, ValueFmt.METERS_ABS)
                    .stacked(StackType.BURSTING_RADIUS, StackFn.addScaled("killStackBurstingRadius", 1.0)),

            // ⭐ provider 是 1+v，词条为 0 时结果是 1.0，直接拿它跟 epsilon 比会永远过阈值，
            //    渲染出一条「触发时长 100%」的空行，所以要显式指出存在性判据。
            AttrSpec.of("triggerTime", PanelGroup.PANEL, ValueFmt.PERCENT)
                    .withProviderInternal(ValueProvider.onePlus("triggerTime"))
                    .visibleWhen("triggerTime"),

            // ── DAMAGE：伤害增益 ────────────────────────────────────────────
            AttrSpec.pct("meleeDamage", PanelGroup.DAMAGE),
            AttrSpec.pct("remoteDamage", PanelGroup.DAMAGE),
            AttrSpec.pct("arrowDamage", PanelGroup.DAMAGE).gated(Gate.RANGED),
            AttrSpec.pct("projectileDamage", PanelGroup.DAMAGE),
            AttrSpec.pct("magicDamage", PanelGroup.DAMAGE),
            AttrSpec.pct("baseDamageWhenNotCriticalStrike", PanelGroup.DAMAGE),
            AttrSpec.pct("first_bullet_damage", PanelGroup.DAMAGE).gated(Gate.RANGED),
            AttrSpec.pct("bane_of_undefined", PanelGroup.DAMAGE),
            AttrSpec.pct("bane_of_undead", PanelGroup.DAMAGE),
            AttrSpec.pct("bane_of_arthropod", PanelGroup.DAMAGE),
            AttrSpec.pct("bane_of_illager", PanelGroup.DAMAGE),
            AttrSpec.pct("dashMeleeCriticalStrikeProbability", PanelGroup.DAMAGE),
            AttrSpec.pct("dashAttackRange", PanelGroup.DAMAGE),
            AttrSpec.pct("dashTriggerChance", PanelGroup.DAMAGE),

            // ── GUN：枪械专属，近战武器整组不生成 ────────────────────────────
            AttrSpec.pct("reload_speed", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("magazine_size", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("projectile_speed", PanelGroup.GUN).gated(Gate.RANGED),
            AttrSpec.pct("recoil_reduction", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("gun_damage", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("headshot_damage", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("aim_time", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("accuracy", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("true_bullet", PanelGroup.GUN).gated(Gate.GUN_ONLY),
            AttrSpec.pct("gun_loot_drop", PanelGroup.GUN).gated(Gate.GUN_ONLY),

            // ── RARE：稀有效果 ──────────────────────────────────────────────
            AttrSpec.pct("execute_threshold", PanelGroup.RARE),
            AttrSpec.pct("purge_buff", PanelGroup.RARE),
            AttrSpec.of("execute_chance", PanelGroup.RARE, ValueFmt.MICRO_PERCENT)
    );

    // ==================== 击杀叠层 ====================

    /**
     * 武器击杀叠层词条 → 叠层类型。
     * <p>顺序即 STACK 区块的显示顺序，由 {@link KillStackKeys#WEAPON} 的 LinkedHashMap 保证
     * （{@code Map.of} 不保证迭代顺序，会让面板上叠层行的先后每次启动都不一样）。</p>
     */
    public static final Map<String, StackType> KILL_STACK_SPECS = KillStackKeys.WEAPON;

    /**
     * {@code killStackBaseDamage} 的最终加成依赖**目标身上的负面效果数量**
     * （{@code WeaponCombatHandler}：{@code stackValue * stacks * debuffCount}），
     * tooltip 时没有目标，算不出确定值。
     * <p>面板上这条只显示「每层 × 层数 /负面」，**不编一个总百分比**——
     * 编了就是第二次让面板和实战对不上。</p>
     */
    public static boolean dependsOnTarget(String killStackKey) {
        return "killStackBaseDamage".equals(killStackKey);
    }

    // ==================== 元素 ====================

    /** 元素词条：紧凑视图里合并为一行彩色缩写 */
    public static final List<String> ELEMENTS = List.of(
            "fire", "ice", "poison", "electricity", "slash", "puncture",
            "impact", "gas", "radiation", "magnetic", "corrosion", "explosion", "virus");

    private static final Set<String> ELEMENT_SET = Set.copyOf(ELEMENTS);

    public static boolean isElement(String key) {
        return ELEMENT_SET.contains(key);
    }

    // ==================== 查询 ====================

    private static final Map<String, AttrSpec> BY_KEY;

    static {
        LinkedHashMap<String, AttrSpec> m = new LinkedHashMap<>();
        for (AttrSpec s : SPECS) {
            m.put(s.key(), s);
        }
        BY_KEY = Collections.unmodifiableMap(m);
    }

    @Nullable
    public static AttrSpec byKey(String key) {
        return BY_KEY.get(key);
    }

    /**
     * 被成对 spec 间接消费的底层 key
     *
     * <p>面板上的「暴击」「暴伤」两条显示的是
     * {@code 基础值 × (1 + 这四个词条)}，所以它们虽然不是 {@code SPECS} 的 key，
     * 但确实已经被面板覆盖了。不登记的话 {@link #findGaps} 会每次启动都把它们误报成
     * 「不会显示在武器面板上」—— 一个恒定误报的自检很快就会被无视。</p>
     */
    private static final Set<String> CONSUMED_BY_PAIRED_SPECS = Set.of(
            "meleeCriticalStrikeProbability", "remoteCriticalStrikeProbability",
            "meleeCriticalStrikeMultiplier", "remoteCriticalStrikeMultiplier");

    /** 该 key 是否被这张表以任何形式覆盖（普通词条 / 叠层 / 元素 / 成对 spec 的输入） */
    public static boolean covers(String key) {
        return BY_KEY.containsKey(key)
                || KILL_STACK_SPECS.containsKey(key)
                || ELEMENT_SET.contains(key)
                || CONSUMED_BY_PAIRED_SPECS.contains(key);
    }

    /**
     * 启动期自检：找出被模组系统声明、但这张表没覆盖的属性 key。
     *
     * <p>这类不一致是真实存在过的：{@code first_bullet_damage} 被
     * {@code WeaponModuleHandler} 读取并显示，却不在
     * {@code AbstractItemModule.ITEM_ATTRIBUTE_TYPES} 的声明里。
     * 以前这种漏登记完全静默，现在会在日志里点名。</p>
     *
     * @param declaredTypes 模组系统声明支持的属性集合
     * @return 漏登记的 key（已排序，便于日志比对）
     */
    public static List<String> findGaps(Set<String> declaredTypes) {
        List<String> gaps = new ArrayList<>();
        for (String t : declaredTypes) {
            if (!covers(t)) {
                gaps.add(t);
            }
        }
        Collections.sort(gaps);
        return gaps;
    }

    // ==================== 内部工具 ====================

    /**
     * 蓄力类武器的射速显示系数
     *
     * <p>⭐ 弓<b>与弩</b>都吃双倍：战斗端的两处射速结算用的是
     * {@code usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem}
     * （见 {@code WeaponCombatHandler} 里的蓄力速度计算）。
     * 改造前的 tooltip 只判了 {@code BowItem}，所以弩的面板值一直只有实际生效值的一半。
     * 既然现在这里是唯一真相来源，就不要把这个口径差异一起封装进来。</p>
     */
    private static double chargeFactor(ItemStack stack) {
        return (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) ? 2.0 : 1.0;
    }

    private WeaponPanelCatalog() {
    }
}
