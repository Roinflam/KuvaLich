package pers.roinflam.kuvalich.utils.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 属性工具类
 * Attributes utility class
 */
public class AttributesUtil {

    /**
     * 获取伤害值（减去基础值1）
     * Get damage value (subtract base value 1)
     */
    public static double getDamage(double damage) {
        return damage - 1;
    }

    /**
     * 获取攻击速度（减去基础值4）
     * Get attack speed (subtract base value 4)
     */
    public static double getDamageSpeed(double damageSpeed) {
        return damageSpeed - 4;
    }

    /**
     * 获取实体的属性值
     * Get entity's attribute value
     *
     * @param livingEntity 生物实体 / living entity
     * @param attribute 属性 / attribute
     * @return 属性值 / attribute value
     */
    public static float getAttributeValue(@Nonnull LivingEntity livingEntity, @Nonnull Attribute attribute) {
        return getAttributeValue(livingEntity, attribute, 0);
    }

    /**
     * 获取实体的属性值（带默认值）
     * Get entity's attribute value (with default value)
     *
     * @param livingEntity 生物实体 / living entity
     * @param attribute 属性 / attribute
     * @param value 默认值 / default value
     * @return 属性值 / attribute value
     */
    public static float getAttributeValue(@Nonnull LivingEntity livingEntity, @Nonnull Attribute attribute, double value) {
        double base = attribute.getDefaultValue() + value;

        // 1.20.1中使用getAttributeValue
        if (livingEntity.getAttribute(attribute) != null) {
            base = livingEntity.getAttributeValue(attribute);
        }

        @Nonnull List<Double> zero = new ArrayList<>();
        @Nonnull List<Double> one = new ArrayList<>();
        @Nonnull List<Double> two = new ArrayList<>();

        // 主手物品属性
        @Nonnull Multimap<Attribute, AttributeModifier> attributeInstance =
                livingEntity.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute)) {
            switch (attributeModifier.getOperation()) {
                case ADDITION -> zero.add(attributeModifier.getAmount());
                case MULTIPLY_BASE -> one.add(attributeModifier.getAmount());
                case MULTIPLY_TOTAL -> two.add(attributeModifier.getAmount());
            }
        }

        // 副手物品属性
        attributeInstance = livingEntity.getOffhandItem().getAttributeModifiers(EquipmentSlot.OFFHAND);
        for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute)) {
            switch (attributeModifier.getOperation()) {
                case ADDITION -> zero.add(attributeModifier.getAmount());
                case MULTIPLY_BASE -> one.add(attributeModifier.getAmount());
                case MULTIPLY_TOTAL -> two.add(attributeModifier.getAmount());
            }
        }

        // 装备栏属性
        int index = 0;
        for (@Nonnull ItemStack i : livingEntity.getArmorSlots()) {
            EquipmentSlot slot = switch (index) {
                case 0 -> EquipmentSlot.FEET;
                case 1 -> EquipmentSlot.LEGS;
                case 2 -> EquipmentSlot.CHEST;
                case 3 -> EquipmentSlot.HEAD;
                default -> EquipmentSlot.MAINHAND;
            };

            attributeInstance = i.getAttributeModifiers(slot);
            for (@Nonnull AttributeModifier attributeModifier : attributeInstance.get(attribute)) {
                switch (attributeModifier.getOperation()) {
                    case ADDITION -> zero.add(attributeModifier.getAmount());
                    case MULTIPLY_BASE -> one.add(attributeModifier.getAmount());
                    case MULTIPLY_TOTAL -> two.add(attributeModifier.getAmount());
                }
            }
            index++;
        }

        // 计算最终值
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

    /**
     * 获取物品的所有属性修饰符
     * Get all attribute modifiers from item
     *
     * ⚠️ 注意：返回类型改为Multimap<String, AttributeModifier>以保持业务逻辑一致
     *
     * @param itemStack 物品堆栈 / item stack
     * @return 属性修饰符映射 / attribute modifiers map
     */
    public static Multimap<String, AttributeModifier> getAnyAttributeModifiers(ItemStack itemStack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        CompoundTag compoundTag = itemStack.getTag();

        if (itemStack.hasTag() && compoundTag.contains("AttributeModifiers", 9)) {
            ListTag listTag = compoundTag.getList("AttributeModifiers", 10);

            for (int i = listTag.size() - 1; i >= 0; --i) {
                CompoundTag modifierTag = listTag.getCompound(i);

                // ✅ 与1.12.2逻辑完全一致：从NBT读取AttributeModifier
                UUID uuid = modifierTag.hasUUID("UUID") ?
                        modifierTag.getUUID("UUID") : UUID.randomUUID();

                AttributeModifier attributeModifier = new AttributeModifier(
                        uuid,
                        modifierTag.getString("Name"),
                        modifierTag.getDouble("Amount"),
                        AttributeModifier.Operation.fromValue(modifierTag.getInt("Operation"))
                );

                // ✅ 与1.12.2完全一致：使用属性名称字符串作为key
                String attributeName = modifierTag.getString("AttributeName");
                multimap.put(attributeName, attributeModifier);
            }
        }
        return multimap;
    }

    /**
     * 根据名称获取属性（辅助方法）
     * Get attribute by name (helper method)
     *
     * @param name 属性名称 / attribute name
     * @return 属性对象，未找到返回null / attribute object, null if not found
     */
    @Nullable
    private static Attribute getAttributeByName(String name) {
        // 这里简化处理，实际应该从Forge的属性注册表查找
        // 常用属性映射
        return switch (name.toLowerCase()) {
            case "generic.max_health" -> Attributes.MAX_HEALTH;
            case "generic.attack_damage" -> Attributes.ATTACK_DAMAGE;
            case "generic.attack_speed" -> Attributes.ATTACK_SPEED;
            case "generic.armor" -> Attributes.ARMOR;
            case "generic.movement_speed" -> Attributes.MOVEMENT_SPEED;
            case "generic.knockback_resistance" -> Attributes.KNOCKBACK_RESISTANCE;
            default -> null;
        };
    }
}