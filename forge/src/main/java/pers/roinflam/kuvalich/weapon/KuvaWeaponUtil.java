package pers.roinflam.kuvalich.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

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
     * 根据等级获取颜色（业务逻辑100%不变）
     * Get color by level (business logic 100% unchanged)
     */
    public static ChatFormatting getColor(int number) {
        // ✅ 注意：所有配置值必须加.get()
        int benchmark = ModConfig.KUVA_LICH.benchmarkLevel.get();

        if (number < benchmark * 0.77777) {
            return ChatFormatting.GRAY;
        } else if (number < benchmark) {
            return ChatFormatting.AQUA;
        } else if (number < benchmark * 1.22222) {
            return ChatFormatting.DARK_PURPLE;
        } else if (number < benchmark * 1.33333) {
            return ChatFormatting.GOLD;
        } else {
            return ChatFormatting.RED;
        }
    }

    /**
     * 根据元素类型获取颜色（业务逻辑100%不变）
     * Get color by element type (business logic 100% unchanged)
     */
    public static ChatFormatting getColor(String type) {
        switch (type) {
            case "fire": return ChatFormatting.DARK_RED;
            case "poison": return ChatFormatting.DARK_GREEN;
            case "ice": return ChatFormatting.AQUA;
            case "electricity": return ChatFormatting.BLUE;
            case "impact": return ChatFormatting.WHITE;
            case "magnetic": return ChatFormatting.DARK_BLUE;
            case "radiation": return ChatFormatting.YELLOW;
            case "slash": return ChatFormatting.GRAY;
            case "puncture": return ChatFormatting.DARK_GRAY;
            case "virus": return ChatFormatting.RED;
            case "corrosion": return ChatFormatting.DARK_GREEN;
            case "explosion": return ChatFormatting.RED;
            case "gas": return ChatFormatting.GREEN;
            default: return ChatFormatting.DARK_GRAY;
        }
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
                        .withStyle(getColor(type)))
                .append(" ")
                .append(Component.literal(String.valueOf(number))
                        .withStyle(getColor(number), ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GRAY));
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
