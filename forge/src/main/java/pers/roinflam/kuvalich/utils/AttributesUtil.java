package pers.roinflam.kuvalich.utils;

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

}