package pers.roinflam.kuvalich.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 赤毒武器工具类（1.20.1版本，业务逻辑100%不变）
 * Kuva Weapon Utility Class (1.20.1 version, business logic 100% unchanged)
 */
public class KuvaWeaponUtil {

    private static final List<String> DAMAGE_TYPE = new ArrayList<>(Arrays.asList(
            "fire", "poison", "ice", "electricity", "impact", "magnetic", "radiation"
    ));

    /**
     * 生成随机赤毒武器（业务逻辑100%不变）
     * Generate random Kuva weapon (business logic 100% unchanged)
     */
    public static ItemStack getItem(Item item, int min, int max) {
        return getItem(item, DAMAGE_TYPE.get(RandomUtil.getInt(0, 6)), RandomUtil.getInt(min, max));
    }

    /**
     * 生成指定类型和等级的赤毒武器（1.20.1新API）
     * Generate Kuva weapon with specific type and level (1.20.1 new API)
     */
    public static ItemStack getItem(Item item, String type, int number) {
        ItemStack itemStack = new ItemStack(item);

        // ✅ 1.20.1：直接使用getOrCreateTag()
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalich = tag.getCompound(Reference.MOD_ID);

        kuvalich.putString("Type", type);
        kuvalich.putInt("Number", number);

        tag.put(Reference.MOD_ID, kuvalich);

        if (item instanceof AbstractKuvaWeapon) {
            ((AbstractKuvaWeapon) item).getBaseAttribute(itemStack);
        }

        return itemStack;
    }

    /**
     * 获取武器元素类型（业务逻辑100%不变）
     * Get weapon element type (business logic 100% unchanged)
     */
    public static String getType(ItemStack itemStack) {
        // ⭐ 只读：用 getTag() 判空，不再用 getOrCreateTag() 给没有 NBT 的物品凭空塞一个空标签
        CompoundTag tag = itemStack.getTag();
        if (tag == null) {
            return "";
        }
        return tag.getCompound(Reference.MOD_ID).getString("Type");
    }

    /**
     * 设置武器元素类型（业务逻辑100%不变）
     * Set weapon element type (business logic 100% unchanged)
     */
    public static void setType(ItemStack itemStack, String type) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalich = tag.getCompound(Reference.MOD_ID);

        kuvalich.putString("Type", type);
        tag.put(Reference.MOD_ID, kuvalich);
    }

    /**
     * 检查是否为赤毒武器（业务逻辑100%不变）
     * Check if is Kuva weapon (business logic 100% unchanged)
     */
    public static boolean isElementalWeapon(ItemStack itemStack) {
        // ⭐ 只读：与 hasType 同一判定，用 getTag() 判空
        return hasType(itemStack);
    }

    /**
     * 根据赤毒等级取强度梯度色（档位划分与改造前逐字一致，只换颜色值）
     *
     * <p>五档是拿 number 与配置项 {@code benchmarkLevel}（默认 45）的比值分的，
     * 不是玩家等级。颜色值见 {@link KuvaPalette#TIER}。</p>
     *
     * @param number 该武器自身的赤毒等级词条
     * @return RGB（不再是 ChatFormatting）
     */
    public static int getColor(int number) {
        // ✅ 注意：所有配置值必须加.get()
        int benchmark = ModConfig.KUVA_LICH.benchmarkLevel.get();

        if (number < benchmark * 0.77777) {
            return KuvaPalette.TIER[0];
        } else if (number < benchmark) {
            return KuvaPalette.TIER[1];
        } else if (number < benchmark * 1.22222) {
            return KuvaPalette.TIER[2];
        } else if (number < benchmark * 1.33333) {
            return KuvaPalette.TIER[3];
        } else {
            return KuvaPalette.TIER[4];
        }
    }

    /**
     * 根据元素 / 物理伤害类型取标识色
     *
     * <p>直接委托 {@link KuvaPalette#element(String)}，与 tooltip 面板走同一张表。</p>
     *
     * <p><b>注意：这里只有 7 种类型真正会被走到。</b>唯一调用方
     * {@link #appendTypeLine} 会先用 {@code DAMAGE_TYPE.contains(type)} 把不在
     * {@code fire / poison / ice / electricity / impact / magnetic / radiation}
     * 里的值统一替换成 {@code "unknown"}，所以 slash / puncture / virus /
     * corrosion / explosion / gas 这 6 种在当前代码里走不到，会落到兜底。
     * 委托给 KuvaPalette 之后这件事不再是问题 —— 那张表本来就是全的，
     * 将来放开 DAMAGE_TYPE 就能直接用上。</p>
     *
     * @param type 伤害类型名
     * @return RGB（不再是 ChatFormatting）
     */
    public static int getColor(String type) {
        return KuvaPalette.element(type);
    }

    /**
     * 获取武器等级（业务逻辑100%不变）
     * Get weapon level (business logic 100% unchanged)
     */
    public static int getNumber(ItemStack itemStack) {
        // ⭐ 只读：用 getTag() 判空
        CompoundTag tag = itemStack.getTag();
        if (tag == null) {
            return 0;
        }
        return tag.getCompound(Reference.MOD_ID).getInt("Number");
    }

    /**
     * 设置武器等级（业务逻辑100%不变）
     * Set weapon level (business logic 100% unchanged)
     */
    public static void setNumber(ItemStack itemStack, int number) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalich = tag.getCompound(Reference.MOD_ID);

        kuvalich.putInt("Number", number);
        tag.put(Reference.MOD_ID, kuvalich);
    }

    /**
     * 检查是否有类型标签（业务逻辑100%不变）
     * Check if has type tag (business logic 100% unchanged)
     */
    public static boolean hasType(ItemStack itemStack) {
        // ⭐ 只读：用 getTag() 判空。Tooltip 事件对鼠标划过的每个物品都会调本方法，
        //    以前的 getOrCreateTag() 会给所有没有 NBT 的物品塞一个空 {} 标签
        CompoundTag tag = itemStack.getTag();
        if (tag == null) {
            return false;
        }
        CompoundTag kuvalich = tag.getCompound(Reference.MOD_ID);
        return kuvalich.contains("Type") && kuvalich.contains("Number");
    }

    /**
     * 追加「赤毒类型 + 等级」行
     *
     * <p>⭐ 改造前这里是一个独立的 {@code @SubscribeEvent}，往
     * {@code tooltip.add(1, ...)} 这个硬编码下标插行，与
     * {@code WeaponModuleHandler} / {@code TaczCompatEventHandler} 抢同一个位置，
     * 三者的相对顺序取决于 Forge 的注解扫描顺序（当前恰好对，但随时可能无声翻转）。
     * 现在改为被 {@code client.tooltip.KuvaTooltipCoordinator} 按固定顺序调用。</p>
     *
     * @param lines     待写入的行列表
     * @param itemStack 被查看的物品
     */
    public static void appendTypeLine(List<Component> lines, ItemStack itemStack) {
        if (!hasType(itemStack)) {
            return;
        }

        String type = getType(itemStack);
        int number = getNumber(itemStack);

        if (!DAMAGE_TYPE.contains(type)) {
            type = "unknown";
        }

        lines.add(Component.translatable("kuvaweapon.type")
                .append(" ")
                .append(Component.translatable("kuvaweapon.type." + type)
                        .withStyle(KuvaPalette.style(getColor(type))))
                .append(" ")
                // 颜色 + 粗体原先是 withStyle(ChatFormatting...) 变长参数一次传两个枚举值，
                // 颜色换成 int 之后只能先拼出 Style 再传。
                .append(Component.literal(String.valueOf(number))
                        .withStyle(KuvaPalette.bold(getColor(number))))
                // 这一句只作用于最外层「类型:」这个标签词本身，
                // 不会级联覆盖上面 append 进来的子组件颜色（它们各自带 Style）。
                .withStyle(KuvaPalette.style(KuvaPalette.MUTED)));
    }

    /**
     * 获取倍率（业务逻辑100%不变）
     * Get magnification (business logic 100% unchanged)
     */
    public static float getMagnification(ItemStack itemStack) {
        // ✅ 注意：配置值必须加.get()
        return (float) ((getNumber(itemStack) - ModConfig.KUVA_LICH.benchmarkLevel.get())
                / 100.0f * ModConfig.KUVA_WEAPON.attributeMultiplier.get());
    }

    public static float getMagnification(ItemStack itemStack, double number) {
        return (float) (number + number * getMagnification(itemStack));
    }

    public static float getMagnification(ItemStack itemStack, double number, double magnification) {
        // ✅ 注意：配置值必须加.get()
        return (float) (number + number * (float) ((getNumber(itemStack) - ModConfig.KUVA_LICH.benchmarkLevel.get())
                / 100.0f * ModConfig.KUVA_WEAPON.attributeMultiplier.get() / magnification));
    }
}
