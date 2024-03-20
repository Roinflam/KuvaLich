package pers.roinflam.kuvalich.utils.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AttributesUtil {

    public static double getDamage(double damage) {
        return damage - 1;
    }

    public static double getDamageSpeed(double damageSpeed) {
        return damageSpeed - 4;
    }

    public static float getAttributeValue(@Nonnull EntityLivingBase entityLivingBase, @Nonnull IAttribute attribute) {
        return getAttributeValue(entityLivingBase, attribute, 0);
    }

    public static float getAttributeValue(@Nonnull EntityLivingBase entityLivingBase, @Nonnull IAttribute attribute, double value) {
        double base = attribute.getDefaultValue() + value;
        if (entityLivingBase.getEntityAttribute(attribute) != null) {
            base = entityLivingBase.getEntityAttribute(attribute).getAttributeValue();
        }

        @Nonnull List<Double> zero = new ArrayList<>();
        @Nonnull List<Double> one = new ArrayList<>();
        @Nonnull List<Double> two = new ArrayList<>();

        @Nonnull Multimap<String, AttributeModifier> attributeInstance = entityLivingBase.getHeldItemMainhand().getAttributeModifiers(EntityEquipmentSlot.MAINHAND);
        for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
            if (attributeModifier.getOperation() == 0) {
                zero.add(attributeModifier.getAmount());
            } else if (attributeModifier.getOperation() == 1) {
                one.add(attributeModifier.getAmount());
            } else {
                two.add(attributeModifier.getAmount());
            }
        }

        attributeInstance = entityLivingBase.getHeldItemOffhand().getAttributeModifiers(EntityEquipmentSlot.OFFHAND);
        for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
            if (attributeModifier.getOperation() == 0) {
                zero.add(attributeModifier.getAmount());
            } else if (attributeModifier.getOperation() == 1) {
                one.add(attributeModifier.getAmount());
            } else {
                two.add(attributeModifier.getAmount());
            }
        }
        int index = 0;
        for (@Nonnull ItemStack i : entityLivingBase.getArmorInventoryList()) {
            switch (index) {
                case 0: {
                    attributeInstance = i.getAttributeModifiers(EntityEquipmentSlot.FEET);
                    for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
                        if (attributeModifier.getOperation() == 0) {
                            zero.add(attributeModifier.getAmount());
                        } else if (attributeModifier.getOperation() == 1) {
                            one.add(attributeModifier.getAmount());
                        } else {
                            two.add(attributeModifier.getAmount());
                        }
                    }
                    break;
                }
                case 1: {
                    attributeInstance = i.getAttributeModifiers(EntityEquipmentSlot.LEGS);
                    for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
                        if (attributeModifier.getOperation() == 0) {
                            zero.add(attributeModifier.getAmount());
                        } else if (attributeModifier.getOperation() == 1) {
                            one.add(attributeModifier.getAmount());
                        } else {
                            two.add(attributeModifier.getAmount());
                        }
                    }
                    break;
                }
                case 2: {
                    attributeInstance = i.getAttributeModifiers(EntityEquipmentSlot.CHEST);
                    for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
                        if (attributeModifier.getOperation() == 0) {
                            zero.add(attributeModifier.getAmount());
                        } else if (attributeModifier.getOperation() == 1) {
                            one.add(attributeModifier.getAmount());
                        } else {
                            two.add(attributeModifier.getAmount());
                        }
                    }
                    break;
                }
                case 3: {
                    attributeInstance = i.getAttributeModifiers(EntityEquipmentSlot.HEAD);
                    for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute.getName())) {
                        if (attributeModifier.getOperation() == 0) {
                            zero.add(attributeModifier.getAmount());
                        } else if (attributeModifier.getOperation() == 1) {
                            one.add(attributeModifier.getAmount());
                        } else {
                            two.add(attributeModifier.getAmount());
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            index++;
        }

        for (double amount : zero) {
            base += amount;
        }

        double number = base;

        for (double amount : one) {
            number += base * amount;
        }

        for (double amount : two) {
            number *= 1.0D + amount;
        }

        return (float) number;
    }

    public static @NotNull Multimap<String, AttributeModifier> getAnyAttributeModifiers(@NotNull ItemStack itemStack) {
        @NotNull Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        @Nullable NBTTagCompound nbtTagCompound = itemStack.getTagCompound();
        if (itemStack.hasTagCompound() && nbtTagCompound.hasKey("AttributeModifiers", 9)) {
            @NotNull NBTTagList nbttaglist = nbtTagCompound.getTagList("AttributeModifiers", 10);
            for (int i = nbttaglist.tagCount() - 1; i >= 0; --i) {
                @NotNull NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
                @Nullable AttributeModifier attributemodifier = SharedMonsterAttributes.readAttributeModifierFromNBT(nbttagcompound);
                multimap.put(nbttagcompound.getString("AttributeName"), attributemodifier);
            }
        }
        return multimap;
    }
}
