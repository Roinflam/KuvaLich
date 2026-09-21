package pers.roinflam.kuvalich.base.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.level.ModuleLevelTooltipHelper;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 武器模组基类（1.20.1版本）
 * Item Module Base Class
 *
 * ⭐ 属性词条根据等级动态缩放显示
 * ⭐ 满级不显示等级Tooltip
 * ⭐ 裂罅在安魂之融中显示洗卡费用（倾向+次数双维度）
 * ⭐ 升级费用根据品质缩放（铜25%/银50%/金75%/Prime&裂罅100%）
 *
 * ⭐ v6 改动：特定元素属性词条使用元素专属颜色（覆盖模组品质颜色）：
 *    - virus 病毒 → LIGHT_PURPLE 粉色
 *    - gas 毒气  → AQUA 青色
 *    - 其他属性保持原有品质颜色（getModuleColor）
 *
 * ⭐ 第三批新词条：true_bullet（真实伤害）、gun_loot_drop（枪械战利品掉落，TACZ 专属），
 *    execute_threshold（收集者阈值）、purge_buff（净化驱散）、execute_chance（致命斩首，通用）。
 *    其中 execute_chance 数值极小（如 0.01%），显示时保留小数避免取整为 0%。
 *
 * <p>⭐ 显示取整修正：百分比显示由 {@code (int) (x * 100)} 改为
 * {@code (int) Math.round(x * 100)}。
 * 原写法是向零截断，而 float 无法精确表示 0.9 / 0.7 / 0.35 这类十进制小数
 * （0.9f 的真实值是 0.89999997615814209），乘 100 后截断会掉一位，
 * 把 90% 显示成 89%、35% 显示成 34%。
 * 模组定义里普遍存在的 {@code 0.90001F} 这类"多余尾数"正是为绕开此问题而加的补丁，
 * 根因修掉之后那些尾数即可清理为整洁的 {@code 0.9F}。</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public abstract class AbstractItemModule extends AbstractModule {

    public static final Set<String> ITEM_ATTRIBUTE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "meleeDamage", "remoteDamage", "arrowDamage", "projectileDamage", "magicDamage",
                    "multishot", "attackSpeed", "attackRange", "triggerChance",
                    "meleeCriticalStrikeProbability", "meleeCriticalStrikeMultiplier",
                    "remoteCriticalStrikeProbability", "remoteCriticalStrikeMultiplier",
                    "bane_of_undefined", "bane_of_undead", "bane_of_arthropod", "bane_of_illager",
                    "fire", "ice", "poison", "electricity", "firing_rate", "triggerTime",
                    "slash", "puncture", "impact", "dashMeleeCriticalStrikeProbability",
                    "dashAttackRange", "dashTriggerChance", "baseDamageWhenNotCriticalStrike",
                    "bursting_radius",
                    "gas", "radiation", "magnetic", "corrosion", "explosion", "virus",
                    "killStackBaseDamage", "killStackMultishot", "killStackMeleeCriticalMultiplier",
                    "killStackTriggerChance", "killStackAttackRange", "killStackAttackSpeed",
                    "killStackBurstingRadius", "killStackFiringRate",
                    "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction",
                    "gun_damage", "headshot_damage", "aim_time", "accuracy",
                    "true_bullet", "gun_loot_drop", "execute_threshold", "purge_buff", "execute_chance"
            ))
    );

    public AbstractItemModule(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWarframe() {
        return false;
    }

    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) { return; }
        Item item = itemStack.getItem();
        if (!(item instanceof AbstractItemModule)) { return; }
        List<Component> tooltip = event.getToolTip();
        if (AbstractModule.isRandom(itemStack)) {
            // ⭐ 固定在名称后插入三行 "+??% ???"（模仿三条被遮住的词条）。
            //    原来的 i < tooltip.size() 条件让行数取决于 tooltip 当前有几行：
            //    普通模式下只有名称一行时一行都不显示，开了 F3+H 又会显示三行
            for (int i = 1; i <= 3; i++) {
                tooltip.add(Math.min(i, tooltip.size()), Component.translatable("kuvaweapon.item_type_random.tooltip")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            addAttributeTooltips(tooltip, itemStack, item, event.getEntity());
        }
    }

    // ⭐ @OnlyIn：本方法引用了 @OnlyIn(Dist.CLIENT) 的 LiveStackView。
    //    HotSpot 对 invokestatic 是惰性解析的，不标也不会在专用服务端炸；
    //    但标上之后 RuntimeDistCleaner 会直接把整段字节码剔掉，是零成本的保险，
    //    也与旧 WeaponModuleHandler 的做法一致。
    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static void addAttributeTooltips(List<Component> tooltip, ItemStack itemStack, Item item,
                                             net.minecraft.world.entity.player.Player viewer) {
        int number = 1;

        if (item instanceof ItemRivenModule) {
            number = addRivenTooltips(tooltip, itemStack, number);
        }

        // ⭐ 等级系统Tooltip（满级自动跳过）
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            int currentLevel = ModuleLevelHelper.getModuleLevel(itemStack);
            int maxLevel = ModuleLevelHelper.getMaxLevel();
            number += ModuleLevelTooltipHelper.appendLevelTooltip(tooltip, number, currentLevel, maxLevel);
            // ⭐ 传入itemStack以支持品质缩放费用显示
            number += ModuleLevelTooltipHelper.appendUpgradeCostTooltipIfInEvolve(tooltip, number, currentLevel, itemStack);
        }

        // ⭐ 属性值根据等级缩放后显示
        double levelMult = ModuleLevelHelper.getEffectiveMultiplier(itemStack);

        for (Map.Entry<String, Double> attributeTag : AbstractModule.getAttributes(itemStack)) {
            double scaledValue = attributeTag.getValue() * levelMult;
            String prefix = scaledValue >= 0 ? "+" : "";
            String attributeKey = attributeTag.getKey();
            // ⭐ 秒杀概率数值极小（如0.01%），保留小数避免取整后显示为0%，并去掉末尾多余的0（0.010%→0.01%）；其余词条整数显示
            String valueText;
            if (attributeKey.equals("execute_chance")) {
                String percentText = String.format("%.3f", scaledValue * 100);
                if (percentText.indexOf('.') >= 0) {
                    percentText = percentText.replaceAll("0+$", "").replaceAll("\\.$", "");
                }
                valueText = percentText + "%";
            } else {
                // ⭐ 用 Math.round 代替截断：避免 0.9 因 float 精度显示成 89%
                valueText = (int) Math.round(scaledValue * 100) + "%";
            }

            Component attributeName;
            if (attributeKey.startsWith("killStack")) {
                int maxStacks = getMaxStacksForAttribute(attributeKey);
                attributeName = Component.translatable("kuvaweapon.item_attribute_type." + attributeKey, maxStacks);
            } else {
                attributeName = Component.translatable("kuvaweapon.item_attribute_type." + attributeKey);
            }

            // ⭐ v6：特定元素使用专属颜色（病毒粉色、毒气青色），其他走原品质颜色
            ChatFormatting elementColor = getElementColor(attributeKey);
            ChatFormatting color = elementColor != null ? elementColor : getModuleColor(item);
            net.minecraft.network.chat.MutableComponent line = Component.literal(prefix + valueText + " ")
                    .append(attributeName)
                    .withStyle(color);

            // ⭐ 叠层词条追加玩家当前层数：卡面原先只写「至多 N 层」，
            //    玩家看不出这条词条现在到底生效了多少，容易低估它的强度。
            Component live = pers.roinflam.kuvalich.client.tooltip.LiveStackView.liveStackSuffix(attributeKey, viewer);
            if (live != null) {
                line.append(live);
            }
            tooltip.add(number++, line);
        }

        tooltip.add(number, Component.translatable("kuvaweapon.item_type.tooltip")
                .withStyle(ChatFormatting.WHITE));
    }

    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackBaseDamage": return ModConfig.KUVA_LICH.maxStacksBaseDamage.get();
            case "killStackMultishot": return ModConfig.KUVA_LICH.maxStacksMultishot.get();
            case "killStackMeleeCriticalMultiplier": return ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get();
            case "killStackTriggerChance": return ModConfig.KUVA_LICH.maxStacksTriggerChance.get();
            case "killStackAttackRange": return ModConfig.KUVA_LICH.maxStacksAttackRange.get();
            case "killStackAttackSpeed": return ModConfig.KUVA_LICH.maxStacksAttackSpeed.get();
            case "killStackBurstingRadius": return ModConfig.KUVA_LICH.maxStacksBurstingRadius.get();
            case "killStackFiringRate": return ModConfig.KUVA_LICH.maxStacksFiringRate.get();
            default: return 0;
        }
    }

    private static int addRivenTooltips(List<Component> tooltip, ItemStack itemStack, int startIndex) {
        int trend = ItemRivenModule.getTrend(itemStack);
        StringBuilder trendBar = new StringBuilder(15);
        for (int i = 0; i < trend; i++) { trendBar.append("●"); }
        for (int i = trend; i < 5; i++) { trendBar.append("○"); }
        tooltip.add(startIndex++,
                Component.translatable("kuvaweapon.item_type_riven_trend.tooltip")
                        .append(" ").append(Component.literal(trendBar.toString()).withStyle(ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE));
        int cycle = ItemRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            tooltip.add(startIndex++,
                    Component.translatable("kuvaweapon.item_type_riven_cycle.tooltip")
                            .append(" ").append(Component.literal(String.valueOf(cycle)).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }
        // ⭐ 在安魂之融中显示洗卡所需赤毒（武器裂罅公式：min(cycle,8) + trend² - (trend-1)²）
        startIndex += ModuleLevelTooltipHelper.appendRivenCycleCostTooltipIfInEvolve(
                tooltip, startIndex, trend, cycle, false);
        return startIndex;
    }

    private static ChatFormatting getModuleColor(Item item) {
        if (item instanceof ItemCommonModule) return ChatFormatting.GOLD;
        if (item instanceof ItemUncommonModule) return ChatFormatting.AQUA;
        if (item instanceof ItemRareModule) return ChatFormatting.YELLOW;
        if (item instanceof ItemPrimeModule) return ChatFormatting.WHITE;
        if (item instanceof ItemRivenModule) return ChatFormatting.LIGHT_PURPLE;
        return ChatFormatting.WHITE;
    }

    /**
     * 根据属性 key 返回元素专属颜色（仅病毒粉色、毒气青色应用覆盖）
     * <p>其他属性返回 null，调用方会 fallback 到 {@link #getModuleColor}。</p>
     *
     * @param attributeKey 属性 key（如 "virus"、"gas"、"meleeDamage"）
     * @return 元素专属颜色，无匹配时 null
     */
    private static ChatFormatting getElementColor(String attributeKey) {
        switch (attributeKey) {
            default:      return null;
        }
    }
}
