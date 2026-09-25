package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 配方与实体预览的数据来源（每个界面实例一份缓存）
 *
 * <p>配方从客户端的配方表查（服务端登录时同步过来），所以书里画出来的就是本服实际生效的配方 ——
 * 服主用数据包改了配方，书跟着变；删了配方，这一块整块不显示，而不是画一个做不出来的东西。</p>
 *
 * <p>实体预览要真的建一个客户端实体。建实体不便宜（GeckoLib 的模型要初始化动画状态），
 * 而且同一篇里滚动时每帧都要画，所以按类型缓存，界面关掉就随界面一起丢。
 * 建不出来（类型不存在、构造抛异常、不是活体）记一次失败，之后退化成只显示名字，不再重试。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideMedia {

    /**
     * 一张工作台配方：9 格（行优先），每格一组轮换候选；空格为 null
     */
    static final class RecipeData {
        final ItemStack[][] slots = new ItemStack[9][];
        ItemStack result = ItemStack.EMPTY;
    }

    /** 查过的配方；值为 EMPTY_RECIPE 表示查过但没有 */
    private final Map<String, RecipeData> recipes = new HashMap<>();
    private static final RecipeData EMPTY_RECIPE = new RecipeData();

    private final Map<String, LivingEntity> entities = new HashMap<>();
    private final Map<String, Boolean> failedEntities = new HashMap<>();

    /**
     * 查配方
     *
     * @param itemId   按产物查（可空）
     * @param recipeId 按配方 id 查（可空）
     * @return 配方；查不到为 null
     */
    @Nullable
    RecipeData recipe(@Nullable String itemId, @Nullable String recipeId) {
        String key = recipeId != null ? "r:" + recipeId : "i:" + itemId;
        RecipeData cached = recipes.get(key);
        if (cached == null) {
            cached = lookup(itemId, recipeId);
            // 没进世界时（理论上不会，书只能在世界里打开）别把「查不到」缓存下来
            if (cached != null || Minecraft.getInstance().level != null) {
                recipes.put(key, cached == null ? EMPTY_RECIPE : cached);
            }
        }
        return cached == EMPTY_RECIPE ? null : cached;
    }

    @Nullable
    private static RecipeData lookup(@Nullable String itemId, @Nullable String recipeId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        RegistryAccess access = level.registryAccess();
        CraftingRecipe found = null;
        try {
            if (recipeId != null) {
                ResourceLocation rl = ResourceLocation.tryParse(recipeId);
                Optional<? extends Recipe<?>> r = rl == null ? Optional.empty() : manager.byKey(rl);
                if (r.isPresent() && r.get() instanceof CraftingRecipe cr) {
                    found = cr;
                }
            } else if (itemId != null) {
                ItemStack target = GuideView.stack(itemId);
                if (!target.isEmpty()) {
                    for (CraftingRecipe cr : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                        ItemStack out;
                        try {
                            if (cr.isSpecial()) {
                                continue;
                            }
                            out = cr.getResultItem(access);
                        } catch (RuntimeException e) {
                            // 整合包里别的模组的配方实现千奇百怪：一张取产物就抛的配方只跳过它自己，
                            // 不能让它把整次查找搅黄（否则排在它后面的本模组配方也画不出来）
                            GuideLog.warnOnce("recipeitem:" + cr.getId(), "查配方时跳过一张出错的配方 " + cr.getId() + ": " + e);
                            continue;
                        }
                        if (out.getItem() != target.getItem()) {
                            continue;
                        }
                        found = cr;
                        // 同一产物有多个配方时优先本模组自己的（别的模组 / 数据包可能加了兼容配方）
                        if (Reference.MOD_ID.equals(cr.getId().getNamespace())) {
                            break;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            GuideLog.warnOnce("recipe:" + itemId + recipeId, "查配方失败: " + e);
            return null;
        }
        if (found == null) {
            return null;
        }
        NonNullList<Ingredient> ingredients = found.getIngredients();
        if (ingredients.isEmpty()) {
            return null;
        }
        RecipeData data = new RecipeData();
        data.result = found.getResultItem(access).copy();
        int width = 3;
        int height = 3;
        if (found instanceof IShapedRecipe<?> shaped) {
            width = Math.max(1, Math.min(3, shaped.getRecipeWidth()));
            height = Math.max(1, Math.min(3, shaped.getRecipeHeight()));
        }
        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            int col = i % width;
            int row = i / width;
            if (row >= height) {
                break;
            }
            ItemStack[] items = ingredients.get(i).getItems();
            data.slots[row * 3 + col] = items.length == 0 ? null : items;
        }
        return data.result.isEmpty() ? null : data;
    }

    /**
     * 取一个用于预览的实体
     *
     * @param id 实体类型 id
     * @return 实体；建不出来为 null
     */
    @Nullable
    LivingEntity entity(String id) {
        LivingEntity cached = entities.get(id);
        if (cached != null) {
            return cached;
        }
        if (failedEntities.containsKey(id)) {
            return null;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        try {
            Optional<EntityType<?>> type = EntityType.byString(id);
            Entity e = type.isPresent() ? type.get().create(level) : null;
            if (e instanceof LivingEntity living) {
                entities.put(id, living);
                return living;
            }
            GuideLog.warnOnce("entity:" + id, "实体预览：" + id + (type.isPresent() ? " 不是活体" : " 不存在") + "，只显示名字");
        } catch (Throwable t) {
            GuideLog.warnOnce("entity:" + id, "实体预览：创建 " + id + " 失败，只显示名字 —— " + t);
        }
        failedEntities.put(id, Boolean.TRUE);
        return null;
    }

    /** 渲染时抛了异常：以后只显示名字 */
    void markEntityFailed(String id, Throwable t) {
        entities.remove(id);
        failedEntities.put(id, Boolean.TRUE);
        GuideLog.warnOnce("entityrender:" + id, "实体预览：渲染 " + id + " 失败，改为只显示名字 —— " + t);
    }

    /**
     * 实体类型的本地化名字
     */
    static String entityName(String id) {
        // 内容里的 id 是人写的：格式不对、实体不存在都只退回原文，不能让排版抛出去
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                return id;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            return type == null ? id : type.getDescription().getString();
        } catch (RuntimeException e) {
            return id;
        }
    }
}
