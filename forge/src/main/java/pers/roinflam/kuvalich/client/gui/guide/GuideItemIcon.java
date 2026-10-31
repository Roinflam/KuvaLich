package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 指南里画物品图标的统一入口
 *
 * <h3>为什么要绕一下</h3>
 * <p>标题画面打开指南时还没有世界。用自定义渲染器画的物品（GeckoLib 的 3D 物品、别的模组的
 * {@code builtin/entity} 物品）在渲染时常常直接读世界，GeckoLib 给 Molang 查询赋值那一步就是引用
 * {@code mc.level} 空指针，整个游戏跟着崩。本模组自己的两个 GeckoLib 物品在模型里已经处理过没有世界的情况，
 * 照常画真图标；别的模组的自定义渲染物品在没有世界时一律画一个小菱形占位，进了世界再画真图标。</p>
 *
 * <p>「是不是自定义渲染、是不是本模组的」按物品缓存，资源重载后（缺失模型换了一个实例）整张表作废重查，
 * 画图标的路径上不分配。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideItemIcon {

    /** 占位菱形：16×16 格里每一行的半宽（中线两边各几格） */
    private static final int[] HALF = {1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1};

    /** 物品 → 没有世界时要不要画占位 */
    private static final Map<Item, Boolean> PLACEHOLDER = new IdentityHashMap<>();
    /** 上次查表时的缺失模型；资源重载会换一个新的，借它判断缓存过期 */
    private static BakedModel modelsKey;

    private GuideItemIcon() {}

    /**
     * 在 (x, y) 画一个 16×16 的物品图标，用法同 {@link GuiGraphics#renderItem(ItemStack, int, int)}
     */
    static void render(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && needsPlaceholder(mc, stack)) {
            placeholder(g, x, y);
            return;
        }
        g.renderItem(stack, x, y);
    }

    private static boolean needsPlaceholder(Minecraft mc, ItemStack stack) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        if (missing != modelsKey) {
            modelsKey = missing;
            PLACEHOLDER.clear();
        }
        Item item = stack.getItem();
        Boolean hit = PLACEHOLDER.get(item);
        if (hit == null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            boolean ours = id != null && Reference.MOD_ID.equals(id.getNamespace());
            hit = !ours && mc.getItemRenderer().getModel(stack, null, null, 0).isCustomRenderer();
            PLACEHOLDER.put(item, hit);
        }
        return hit;
    }

    /** 小菱形：骨白填充、灰描边，和指南里别的占位一样不抢眼 */
    private static void placeholder(GuiGraphics g, int x, int y) {
        int fill = GuideTheme.BONE;
        int edge = GuideTheme.ASH;
        for (int row = 0; row < HALF.length; row++) {
            int h = HALF[row];
            int l = x + 8 - h;
            int r = x + 8 + h;
            boolean rim = row == 0 || row == HALF.length - 1;
            if (rim || h <= 1) {
                g.fill(l, y + row, r, y + row + 1, edge);
                continue;
            }
            g.fill(l, y + row, l + 1, y + row + 1, edge);
            g.fill(r - 1, y + row, r, y + row + 1, edge);
            g.fill(l + 1, y + row, r - 1, y + row + 1, fill);
        }
    }
}
