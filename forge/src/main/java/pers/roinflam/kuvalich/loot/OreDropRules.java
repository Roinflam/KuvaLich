package pers.roinflam.kuvalich.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「这块方块算不算矿石」与「这份掉落该不该被放大」的判定
 *
 * <p><b>为什么不能只看 {@code forge:ores}</b>：实测整合包里有 58 个方块的战利品表在用
 * 原版时运公式 {@code minecraft:ore_drops}，其中 <b>23 个不在 {@code forge:ores}</b>。
 * 滑稽纪元的 {@code huajiage:ore_huaji} 就是其中之一 —— 它只进了
 * {@code mineable/pickaxe}、{@code needs_diamond_tool}、{@code beacon_base_blocks}
 * 三个 tag，整合包层面也没有补。只靠 tag 判定会把这一大片全漏掉。</p>
 *
 * <p>所以判定是三层：<b>本模组自己的 block tag ∪ 配置白名单 − 配置黑名单</b>。
 * tag 走数据包，整合包作者可以直接覆盖；白名单走配置，不会玩数据包的人也能加。</p>
 *
 * @author RoinFlam
 */
public final class OreDropRules {

    /**
     * 本模组自己的矿石 tag
     *
     * <p>随包的 json 默认 include {@code forge:ores}，再单独补上那些没进 forge tag 的矿。
     * 整合包要增删，覆盖这个 tag 就行，不用动配置。</p>
     */
    public static final TagKey<Block> MINING_MULTIPLIER_ORES =
            BlockTags.create(new ResourceLocation(Reference.MOD_ID, "mining_multiplier_ores"));

    /** 配置列表解析后的缓存；配置重载时置空 */
    private static Set<ResourceLocation> whitelistCache;
    private static Set<ResourceLocation> blacklistCache;

    private OreDropRules() {
    }

    /** 配置重载后调用，丢掉解析缓存 */
    public static void invalidateCache() {
        whitelistCache = null;
        blacklistCache = null;
    }

    /**
     * 这块方块要不要吃挖矿倍率
     *
     * @param state 被挖掉的方块
     * @return 算矿石返回 true
     */
    public static boolean isApplicableOre(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) {
            return false;
        }
        // 黑名单优先级最高：整合包要排除某个「其实不该算矿」的方块时，不必去改 tag
        if (blacklist().contains(id)) {
            return false;
        }
        return state.is(MINING_MULTIPLIER_ORES) || whitelist().contains(id);
    }

    /**
     * 这一份掉落物要不要被放大
     *
     * <p><b>这是防自我复制的闸</b>。三种情况必须跳过，否则就是无限刷：</p>
     * <ol>
     *   <li>掉落物<b>就是方块自己</b> —— 精准采集最典型，挖一个钻石矿掉十个钻石矿，
     *       摆回去再挖，永动机。</li>
     *   <li>掉落物是<b>另一个可放置的矿石方块</b> —— 有些模组的矿掉的是「粗矿方块」
     *       而那玩意儿能摆回地上再挖，一样能刷。</li>
     * </ol>
     *
     * <p>精准采集本身在 {@link OreDropMultiplierModifier} 里另有一道判定：
     * 两道都留着，是因为有些模组的精准采集不走原版战利品表分支。</p>
     *
     * @param drop  掉落物
     * @param state 被挖掉的方块
     * @return 该放大返回 true
     */
    public static boolean isMultipliableDrop(ItemStack drop, BlockState state) {
        if (drop.isEmpty()) {
            return false;
        }
        if (!(drop.getItem() instanceof BlockItem blockItem)) {
            // 不是方块物品 —— 钻石、红石粉、粗铁这类，放心乘
            return true;
        }
        Block dropped = blockItem.getBlock();
        if (dropped == state.getBlock()) {
            return false;
        }
        // 掉出来的还是个矿 → 能摆回去再挖，同样是自己刷自己
        return !dropped.defaultBlockState().is(MINING_MULTIPLIER_ORES)
                && !whitelist().contains(ForgeRegistries.BLOCKS.getKey(dropped))
                && !dropped.defaultBlockState().is(Tags.Blocks.ORES);
    }

    // ==================== 配置列表解析 ====================

    private static Set<ResourceLocation> whitelist() {
        if (whitelistCache == null) {
            whitelistCache = parse(ModConfig.KUVA_LICH.oreDropWhitelist.get());
        }
        return whitelistCache;
    }

    private static Set<ResourceLocation> blacklist() {
        if (blacklistCache == null) {
            blacklistCache = parse(ModConfig.KUVA_LICH.oreDropBlacklist.get());
        }
        return blacklistCache;
    }

    /** 把配置里的字符串解析成方块 id；写错的条目直接忽略，不因为一行笔误让整份配置失效 */
    private static Set<ResourceLocation> parse(List<? extends String> raw) {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
