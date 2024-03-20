package pers.roinflam.kuvalich.base.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.tabs.KuvaLichTab;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public abstract class ModuleBase extends Item implements IHasModel {
    private final static List<String> moduleType = new ArrayList<String>(Arrays.asList("common", "uncommon", "rare", "prime", "riven"));

    public ModuleBase(@Nonnull String name) {
        ItemUtil.registerItem(this, name, KuvaLichTab.getTab());

        setMaxStackSize(1);

        KuvaLichItems.ITEMS.add(this);
    }

    public abstract boolean isWarframe();

    public static boolean isRandom(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        return kuvalichModule.getBoolean("Random");
    }

    public static void setRandom(ItemStack itemStack, boolean random) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        kuvalichModule.setBoolean("Random", random);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

//    public static boolean isRiven(ItemStack itemStack) {
//        return getItemStackModuleType(itemStack).equalsIgnoreCase("riven");
//    }

//    public static String getItemStackModuleType(ItemStack itemStack) {
//        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
//        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
//        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");
//
//        return kuvalichModule.getString("moduleType");
//    }

    public static Set<Map.Entry<String, Double>> getAttributes(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalich = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        Map<String, Double> attributeMap = new LinkedHashMap<>();

        NBTTagList attributeList = kuvalich.getTagList("attributeList", 10);
        for (int i = 0; i < attributeList.tagCount(); i++) {
            NBTTagCompound attributeTag = attributeList.getCompoundTagAt(i);
            attributeMap.put(attributeTag.getString("attributeType"), attributeTag.getDouble("attributeValue"));
        }
        return attributeMap.entrySet();
    }

    public static void addAttributes(ItemStack itemStack, String attributeType, double attributeValue) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        NBTTagList attributeList = kuvalichModule.getTagList("attributeList", 10);

        NBTTagCompound attributeTag = new NBTTagCompound();
        attributeTag.setString("attributeType", attributeType);
        attributeTag.setDouble("attributeValue", attributeValue);
        attributeList.appendTag(attributeTag);

        kuvalichModule.setTag("attributeList", attributeList);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

    public static void setType(ItemStack itemStack, String type) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        kuvalichModule.setString("type", type);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

    public static String getType(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        return kuvalichModule.getString("type");
    }
}
