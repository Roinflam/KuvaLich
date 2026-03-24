package pers.roinflam.kuvalich.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Curios 饰品栏兼容工具类
 * Curios trinket slot compatibility utility
 * <p>
 * 采用延迟类加载模式：外层类不引用任何 Curios API 类，
 * 内层 CuriosAccessor 仅在 Curios 确认已加载后才被 JVM 加载。
 * 这保证了 Curios 不存在时不会触发 NoClassDefFoundError。
 * <p>
 * Uses lazy class loading: outer class has ZERO Curios imports.
 * Inner CuriosAccessor is loaded by JVM only after Curios is confirmed present.
 * Guarantees no NoClassDefFoundError when Curios is absent.
 *
 * @author RoinFlam
 */
public final class CuriosCompat {

    /** Curios 是否已加载（延迟初始化）/ Whether Curios is loaded (lazy init) */
    private static Boolean curiosLoaded = null;

    /**
     * 检查 Curios 是否已加载（仅首次调用时查询 ModList，之后缓存结果）
     * Check if Curios is loaded (queries ModList only on first call, caches result)
     *
     * @return true 表示 Curios 已加载 / true if Curios is loaded
     */
    public static boolean isCuriosLoaded() {
        if (curiosLoaded == null) {
            curiosLoaded = ModList.get().isLoaded("curios");
        }
        return curiosLoaded;
    }

    /**
     * 获取实体身上装备的饰品栏物品列表
     * Get equipped curios items from entity
     * <p>
     * 安全封装：Curios 不存在时返回空列表，不会报错。
     * Safe wrapper: returns empty list when Curios is absent, no errors.
     *
     * @param entity   目标实体 / target entity
     * @param maxSlots 最大检查槽位数（超过此数的槽位不会被检查）/ max slots to check
     * @return 已装备饰品的物品栈列表（不含空栈）/ list of equipped curios ItemStacks (no empty stacks)
     */
    public static List<ItemStack> getEquippedCurios(LivingEntity entity, int maxSlots) {
        if (!isCuriosLoaded()) {
            return Collections.emptyList();
        }
        try {
            // 延迟加载内部类，仅当 Curios 确认存在时才触发类加载
            // Lazy-load inner class, only triggers classloading when Curios is confirmed present
            return CuriosAccessor.getEquippedCurios(entity, maxSlots);
        } catch (Throwable e) {
            // 兜底：任何 Curios API 调用失败都安全降级为空列表
            // Fallback: any Curios API failure gracefully degrades to empty list
            return Collections.emptyList();
        }
    }

    /**
     * 内部访问器类（延迟类加载）
     * Inner accessor class (lazy class loading)
     * <p>
     * 本类直接引用 Curios API 类型（top.theillusivec4.curios.api.CuriosApi）。
     * JVM 只有在首次执行到本类方法时才会尝试加载它，
     * 而外层 getEquippedCurios 已确保此时 Curios 一定存在。
     */
    private static final class CuriosAccessor {

        /**
         * 从 Curios 获取装备中的饰品物品
         * Fetch equipped curios from Curios API
         *
         * @param entity   目标实体 / target entity
         * @param maxSlots 最大检查槽位数 / max slots to check
         * @return 饰品物品栈列表 / list of curios ItemStacks
         */
        static List<ItemStack> getEquippedCurios(LivingEntity entity, int maxSlots) {
            List<ItemStack> result = new ArrayList<>();

            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                var equipped = handler.getEquippedCurios();
                int limit = Math.min(equipped.getSlots(), maxSlots);
                for (int i = 0; i < limit; i++) {
                    ItemStack stack = equipped.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        result.add(stack);
                    }
                }
            });

            return result;
        }
    }

    /** 工具类禁止实例化 / Utility class, no instantiation */
    private CuriosCompat() {
    }
}
