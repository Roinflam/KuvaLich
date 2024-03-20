package pers.roinflam.kuvalich.itemstack;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.config.ConfigKuvaWeapon;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class KuvaWeapon {
    private final static List<String> damageType = new ArrayList<String>(Arrays.asList("fire", "poison", "ice", "electricity", "impact", "magnetic", "radiation"));

    public static @NotNull ItemStack getItem(@NotNull Item item, int min, int max) {
        return getItem(item, damageType.get(RandomUtil.getInt(0, 6)), RandomUtil.getInt(min, max));
    }

    public static @NotNull ItemStack getItem(@NotNull Item item, @NotNull String type, int number) {
        @NotNull ItemStack itemStack = new ItemStack(item);

        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);

        kuvalich.setString("Type", type);
        kuvalich.setInteger("Number", number);

        tag.setTag(Reference.MOD_ID, kuvalich);
        nbtTagCompound.setTag("tag", tag);

        itemStack.deserializeNBT(nbtTagCompound);

        if (item instanceof KuvaWeaponBase) {
            ((KuvaWeaponBase) item).getBaseAttribute(itemStack);
        }

        return itemStack;
    }


    public static @NotNull String getType(@NotNull ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);

        return kuvalich.getString("Type");
    }

    public static void setType(@NotNull ItemStack itemStack, String type) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);

        kuvalich.setString("Type", type);

        tag.setTag(Reference.MOD_ID, kuvalich);
        nbtTagCompound.setTag("tag", tag);

        itemStack.deserializeNBT(nbtTagCompound);
    }

    public static boolean isElementalWeapon(@NotNull ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);
        return kuvalich.hasKey("Type") && kuvalich.hasKey("Number");
    }

    public static @NotNull TextFormatting getColor(int number) {
        if (number < ConfigKuvaLich.benchmarkLevel * 0.77777) {
            return TextFormatting.GRAY;
        } else if (number < ConfigKuvaLich.benchmarkLevel) {
            return TextFormatting.AQUA;
        } else if (number < ConfigKuvaLich.benchmarkLevel * 1.22222) {
            return TextFormatting.DARK_PURPLE;
        } else if (number < ConfigKuvaLich.benchmarkLevel * 1.33333) {
            return TextFormatting.GOLD;
        } else {
            return TextFormatting.RED;
        }
    }

    public static @Nullable TextFormatting getColor(@NotNull String type) {
        switch (type) {
            case "fire": {
                return TextFormatting.DARK_RED;
            }
            case "poison": {
                return TextFormatting.DARK_GREEN;
            }
            case "ice": {
                return TextFormatting.AQUA;
            }
            case "electricity": {
                return TextFormatting.BLUE;
            }
            case "impact": {
                return TextFormatting.WHITE;
            }
            case "magnetic": {
                return TextFormatting.DARK_BLUE;
            }
            case "radiation": {
                return TextFormatting.YELLOW;
            }
            case "slash": {
                return TextFormatting.GRAY;
            }
            case "puncture": {
                return TextFormatting.DARK_GRAY;
            }
            case "virus": {
                return TextFormatting.RED;
            }
            case "corrosion": {
                return TextFormatting.DARK_GREEN;
            }
            case "explosion": {
                return TextFormatting.RED;
            }
            case "gas": {
                return TextFormatting.GREEN;
            }
            default: {
                return TextFormatting.DARK_GRAY;
            }
        }
    }

    public static int getNumber(@NotNull ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);

        return kuvalich.getInteger("Number");
    }

    public static void setNumber(@NotNull ItemStack itemStack, int number) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID);

        kuvalich.setInteger("Number", number);

        tag.setTag(Reference.MOD_ID, kuvalich);
        nbtTagCompound.setTag("tag", tag);

        itemStack.deserializeNBT(nbtTagCompound);
    }

    public static boolean hasType(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound kuvalich = nbtTagCompound.getCompoundTag("tag").getCompoundTag(Reference.MOD_ID);
        return kuvalich.hasKey("Type") && kuvalich.hasKey("Number");
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        if (hasType(itemStack)) {
            @NotNull String type = getType(itemStack);
            int number = getNumber(itemStack);
            if (!damageType.contains(type)) {
                type = "unknown";
            }
            evt.getToolTip().add(1, I18n.format("kuvaweapon.type") + " " + getColor(type) + I18n.format("kuvaweapon.type." + type) + TextFormatting.GRAY + getColor(number) + TextFormatting.BOLD + " " + number);
        }
    }

    public static float getMagnification(@NotNull ItemStack itemStack) {
        return (float) ((getNumber(itemStack) - ConfigKuvaLich.benchmarkLevel) / 100.0f * ConfigKuvaWeapon.attributeMultiplier);
    }

    public static float getMagnification(@NotNull ItemStack itemStack, double number) {
        return (float) (number + number * getMagnification(itemStack));
    }

    public static float getMagnification(@NotNull ItemStack itemStack, double number, double magnification) {
        return (float) (number + number * (float) ((getNumber(itemStack) - ConfigKuvaLich.benchmarkLevel) / 100.0f * ConfigKuvaWeapon.attributeMultiplier / magnification));
    }

}
