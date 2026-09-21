package pers.roinflam.kuvalich.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.function.Supplier;

/**
 * 挖矿倍率：战甲词条 {@code oreDropMultiplier} 的结算入口
 *
 * <p><b>为什么用 Global Loot Modifier 而不是事件</b>：Forge 1.20.1 <b>没有方块掉落事件</b>。
 * {@code BlockEvent.BreakEvent} 在掉落计算<b>之前</b>触发、只带经验值，拿不到掉落列表；
 * 整个 {@code net.minecraftforge.event} 下只有 {@code LivingDropsEvent} 与
 * {@code LivingExperienceDropEvent} 两个 Drop 事件，都只管生物。
 * 而 {@code Block#getDrops} 会把 {@link LootContextParams#THIS_ENTITY}（挖的玩家）与
 * {@link LootContextParams#TOOL}（镐子）塞进 {@code LootParams}，
 * 所以 GLM 里能同时拿到战甲属性、工具附魔和方块状态 —— 这是唯一一个三者齐全的位置。</p>
 *
 * <p><b>独立乘区</b>：GLM 跑在战利品表<b>之后</b>，也就是原版时运
 * （{@code ApplyBonusCount$OreDrops}，时运 III 最大 ×4）已经算完之后，
 * 所以本倍率天然是「在时运结果之上再乘一次」，不会和时运挤同一个加法池。</p>
 *
 * <p><b>与战利品掉落倍率的关系</b>：{@code itemDropMultiplier} 挂在
 * {@code LivingDropsEvent} 上只管生物掉落，两者作用域不相交，各乘各的。
 * 世界等级模组的倍率同样只挂 {@code LivingDropsEvent}（已核实其字节码里
 * 没有任何方块破坏或战利品表钩子），所以挖矿这条路上目前没有第二个乘区。</p>
 *
 * @author RoinFlam
 */
public class OreDropMultiplierModifier extends LootModifier {

    public static final Supplier<Codec<OreDropMultiplierModifier>> CODEC = Suppliers.memoize(
            () -> RecordCodecBuilder.create(inst -> codecStart(inst)
                    .apply(inst, OreDropMultiplierModifier::new)));

    public OreDropMultiplierModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Nonnull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!ModConfig.KUVA_LICH.enableOreDropMultiplier.get()) {
            return loot;
        }

        // ⭐ 精准采集直接放行。挖矿倍率放大的是「矿产」，精准采集掉的是矿石方块本体，
        //    放大它就是摆回去再挖的永动机。OreDropRules.isMultipliableDrop 里还有一道
        //    「掉落物就是方块自己」的判定兜底，两道都留着是因为有些模组的精准采集
        //    不走原版战利品表的那个分支。
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool != null && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return loot;
        }

        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !OreDropRules.isApplicableOre(state)) {
            return loot;
        }

        // ⭐ 必须是玩家亲手挖的。TNT、爬行者、其它模组的区域破坏都拿不到 THIS_ENTITY
        //    或不是玩家 —— 放行它们，否则可以用炸药刷矿。
        Entity breaker = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(breaker instanceof Player player)) {
            return loot;
        }

        double multiplier = multiplierFor(player);
        if (multiplier <= 1.0) {
            return loot;
        }

        ObjectArrayList<ItemStack> out = new ObjectArrayList<>(loot.size());
        for (ItemStack stack : loot) {
            if (!OreDropRules.isMultipliableDrop(stack, state)) {
                out.add(stack);
                continue;
            }
            addMultiplied(out, stack, multiplier, context);
        }
        return out;
    }

    /**
     * 取这名玩家当前的挖矿倍率
     *
     * @return 最终倍率；1.0 表示没有加成
     */
    private static double multiplierFor(Player player) {
        HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
        WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

        double raw = attributes.getOrDefault("oreDropMultiplier", 0.0);
        if (Math.abs(raw) < 1.0e-6) {
            return 1.0;
        }
        // 与战利品掉落倍率同一套语义：配置里的百分比是「这条词条实际生效多少」
        double effectPercent = ModConfig.KUVA_LICH.oreDropEffectMultiplier.get() / 100.0;
        return 1.0 + raw * effectPercent;
    }

    /**
     * 按倍率放大一份掉落，并按堆叠上限拆成若干份
     *
     * <p>小数部分按概率进位 —— 不这么做的话 ×1.3 会被整数截断成 ×1，
     * 低倍率区间整条词条形同虚设。</p>
     *
     * <p>拆分只是<b>把总量分成几摞</b>，总数一件不少（作者明确说挖矿倍率不封顶）。
     * 不拆的话会造出一个数量超过 {@code maxStackSize} 的 ItemStack，
     * 捡起来的行为在各模组背包里不一致。</p>
     */
    private static void addMultiplied(ObjectArrayList<ItemStack> out, ItemStack stack,
                                      double multiplier, LootContext context) {
        int whole = (int) multiplier;
        if (context.getRandom().nextDouble() < multiplier - whole) {
            whole++;
        }
        if (whole <= 1) {
            out.add(stack);
            return;
        }

        int total = stack.getCount() * whole;
        int max = Math.max(1, stack.getMaxStackSize());
        while (total > 0) {
            int size = Math.min(total, max);
            ItemStack part = stack.copy();
            part.setCount(size);
            out.add(part);
            total -= size;
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
