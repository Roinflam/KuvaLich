package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;

import java.util.*;

/**
 * 把「一把武器 + 一份属性表 + 一份叠层数据」变成可渲染的面板数据
 *
 * <p>⭐ 本类**没有任何 I18n / Font / Minecraft 客户端依赖**，故意放在
 * {@code module.weapon.panel} 而不是 {@code client} 包下。
 * {@code WeaponModuleHandler} 类级 {@code @Mod.EventBusSubscriber} 并没有限定 Dist
 * （只有方法带 {@code @OnlyIn}），专用服务端的类加载安全全靠 RuntimeDistCleaner 剥离；
 * 数据层与展示层分包之后，就算哪天有人往数据层加了个不带 {@code @OnlyIn} 的辅助方法也不会炸服。</p>
 *
 * @author RoinFlam
 */
public final class WeaponPanelData {

    /**
     * 按描述表收集这把武器的所有面板 chip
     *
     * <p>这个方法就是改造前 {@code WeaponModuleHandler.onItemTooltip} 里
     * 那 40 多个 {@code if (attributes.getOrDefault(...) != 0)} 块的等价物。</p>
     *
     * @param stack  被查看的武器
     * @param attrs  已汇总的属性表（含等级缩放、上下限裁剪、额外槽位合并）
     * @param stacks 叠层数据来源
     * @return 按 {@link WeaponPanelCatalog#SPECS} 顺序排列的 chip 列表
     */
    public static List<PanelChip> collect(ItemStack stack, Map<String, Double> attrs, StackCounts stacks) {
        return collect(stack, attrs, stacks, true);
    }

    /**
     * @param applyGates 是否按武器类型过滤（近战武器不显示枪械词条）。
     *                   整合包作者可以在配置里关掉 —— 他们的包里
     *                   {@code reload_speed} 这类词条可能被别的模组另作他用。
     */
    public static List<PanelChip> collect(ItemStack stack, Map<String, Double> attrs, StackCounts stacks,
                                          boolean applyGates) {
        List<PanelChip> out = new ArrayList<>(WeaponPanelCatalog.SPECS.size());

        for (AttributeSpec spec : WeaponPanelCatalog.SPECS) {
            // ⭐ 门控：近战武器不生成枪械词条，这是压掉大半行数的关键一步
            if (applyGates && !spec.gate().accepts(stack)) {
                continue;
            }

            double base = spec.provider().get(stack, attrs);
            Double second = spec.secondary() == null ? null : spec.secondary().get(stack, attrs);

            double withStacks = base;
            if (spec.stackType() != null && spec.stackFn() != null && stacks.available()) {
                int n = stacks.stacks(spec.stackType());
                if (n > 0) {
                    withStacks = spec.stackFn().apply(stack, attrs, base, n);
                }
            }

            if (!isVisible(spec, attrs, base, second, withStacks)) {
                continue;
            }

            out.add(new PanelChip(spec, base, second, withStacks));
        }

        return out;
    }

    /**
     * 一条词条是否该出现在面板上
     *
     * <p>只有被 {@link AttributeSpec#always()} 标记的四条（基伤 / 暴击 / 暴伤 / 触发）是
     * 武器的身份信息，即使全是基础值也要显示；其余一律「真的有非零值」才占行。</p>
     *
     * <p>⭐ 这个判据以前挂在 {@link PanelGroup#alwaysVisible()} 上，是<b>组级</b>的，
     * 于是同在 PANEL 组的多重 / 攻速 / 射速 / 范围 / 触发时长也被顺带恒显 ——
     * 一把只插了伤害卡的武器会平白多出「多重 +0%  攻速 0.0%  射速 +0%  范围 +0%
     * 触发时长 100%」五个零值 chip，而改造前它们都是 {@code != 0} 才显示的。</p>
     */
    private static boolean isVisible(AttributeSpec spec, Map<String, Double> attrs,
                                     double base, Double second, double withStacks) {
        if (spec.always() && !spec.fmt().hiddenWhenZero()) {
            return true;
        }
        double eps = spec.fmt().epsilon();

        // ⭐ 显示值 ≠ 存在性判据的词条（triggerTime 的 provider 是 1+v、
        //    firing_rate 的 provider 乘了蓄力系数）必须按原始 key 判断有没有这条词条
        if (spec.visibilityKey() != null) {
            if (Math.abs(attrs.getOrDefault(spec.visibilityKey(), 0.0)) >= eps) {
                return true;
            }
        } else if (Math.abs(base) >= eps) {
            return true;
        }

        if (second != null && Math.abs(second) >= eps) {
            return true;
        }
        // 词条本身为 0 但叠层把它抬起来了（例如只靠叠层吃多重射击）
        return Math.abs(withStacks - base) >= eps;
    }

    /**
     * 收集「击杀叠层」区块的各行
     *
     * <p>只为这把武器**真的带了**的叠层词条生成行：没有 {@code killStackMultishot}
     * 词条的武器不会平白多出一行「多重 0/5」。</p>
     */
    public static List<StackRow> collectStacks(Map<String, Double> attrs, StackCounts stacks) {
        List<StackRow> rows = new ArrayList<>();

        for (Map.Entry<String, StackType> e : WeaponPanelCatalog.KILL_STACK_SPECS.entrySet()) {
            String key = e.getKey();
            Double perStack = attrs.get(key);
            if (perStack == null || Math.abs(perStack) < 1.0e-6) {
                continue;
            }
            StackType type = e.getValue();
            rows.add(new StackRow(
                    key,
                    type,
                    perStack,
                    stacks.available() ? stacks.stacks(type) : 0,
                    stacks.maxStacks(type),
                    stacks.decayTicks(type),
                    stacks.available(),
                    WeaponPanelCatalog.dependsOnTarget(key)
            ));
        }

        return rows;
    }

    /**
     * 收集描述表不认识的属性
     *
     * <p>⭐ 这是给<b>整合包作者</b>的兜底：{@code CustomModuleManager} 允许在
     * {@code kuvalich-custom-modules.json} 里写任意属性 key，战斗端会照常读它，
     * 但面板的描述表是写死的 —— 不兜底的话作者写的词条在武器面板上一行都不显示，
     * 只能靠翻代码才知道为什么。</p>
     *
     * <p>这些词条统一按「原始 key 名 + 带正号整数百分比」显示，
     * 并且不参与门控（没有元数据就无从判断它对哪类武器有效）。</p>
     *
     * @param attrs 已汇总的属性表
     * @return 未登记的属性（key → 值），按 key 排序保证顺序稳定
     */
    public static LinkedHashMap<String, Double> collectUnknown(Map<String, Double> attrs) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(attrs.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (WeaponPanelCatalog.covers(key)) {
                continue;
            }
            // 这几个是「被别的 spec 当作输入读取」的辅助 key，本身不该单独成条
            if (AUXILIARY_KEYS.contains(key)) {
                continue;
            }
            double v = attrs.getOrDefault(key, 0.0);
            if (Math.abs(v) >= 1.0e-3) {
                out.put(key, v);
            }
        }
        return out;
    }

    /**
     * 不单独显示的辅助 key
     *
     * <p>这几条是 {@code criticalStrikeProbability} / {@code criticalStrikeMultiplier}
     * 这两个成对 spec 的输入参数（面板显示的是「基础值 × (1 + 它们)」的结果），
     * 再单列一遍就是同一个信息显示两次。</p>
     */
    private static final Set<String> AUXILIARY_KEYS = Set.of(
            "meleeCriticalStrikeProbability", "remoteCriticalStrikeProbability",
            "meleeCriticalStrikeMultiplier", "remoteCriticalStrikeMultiplier");

    private WeaponPanelData() {
    }
}
