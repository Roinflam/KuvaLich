package pers.roinflam.kuvalich.base.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 赤毒武器基类（1.20.1版本，业务逻辑100%不变）
 * Kuva Weapon Base Class (1.20.1 version, business logic 100% unchanged)
 */
public abstract class AbstractKuvaWeapon extends SwordItem {

    // ✅ 1.20.1使用Tier代替ToolMaterial
    // ✅ 1.20.1 uses Tier instead of ToolMaterial
    public static final Tier KUVA = new ForgeTier(
            3,                              // 采集等级 / Harvest level
            23333,                          // 耐久度 / Durability
            100.0f,                         // 挖掘速度 / Mining speed
            23.33f,                         // 攻击伤害加成 / Attack damage bonus
            50,                             // 附魔能力 / Enchantability
            null,                           // 方块标签 / Block tag
            () -> Ingredient.EMPTY          // 修复材料 / Repair ingredient
    );

    // UUID常量（业务逻辑100%不变）/ UUID constants (business logic 100% unchanged)
    public static UUID MOVEMENT_SPEED = UUID.fromString("ea068571-91fe-da31-18c4-ca86c557bb58");
    public static UUID KNOCKBACK_RESISTANCE = UUID.fromString("d9bb87cf-b828-2154-215b-e8ca2b21d875");
    public static UUID MAX_HEALTH = UUID.fromString("157634e9-f993-110e-8705-dab08f43c437");
    public static UUID REACH_DISTANCE = UUID.fromString("e4e74f53-41a0-f8ae-41e8-8de5cfd7f2ed");

    public AbstractKuvaWeapon(@Nonnull Item.Properties properties) {
        super(KUVA, 0, 0, properties);  // 攻击伤害和速度由getAttributeModifiers控制
    }

    public abstract ItemStack getBaseAttribute(ItemStack itemStack);

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }

    /**
     * 获取属性修饰符（1.20.1新API）
     * Get attribute modifiers (1.20.1 new API)
     */
    @Override
    public @NotNull Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            @NotNull EquipmentSlot slot,
            @NotNull ItemStack stack) {

        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();

        if (slot == EquipmentSlot.MAINHAND) {
            // 攻击伤害 / Attack damage
            multimap.put(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            BASE_ATTACK_DAMAGE_UUID,
                            Reference.MOD_ID + ":" + "attack_damage",
                            getAttackDamageAmount(stack),
                            getAttackDamageOperation()
                    )
            );

            // 攻击速度 / Attack speed
            multimap.put(
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            BASE_ATTACK_SPEED_UUID,
                            Reference.MOD_ID + ":" + "attack_speed",
                            getAttackSpeedAmount(stack),
                            getAttackSpeedOperation()
                    )
            );

            // 移动速度 / Movement speed
            if (getMovementSpeedAmount(stack) != 0) {
                multimap.put(
                        Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(
                                MOVEMENT_SPEED,
                                Reference.MOD_ID + ":" + "movement_speed",
                                getMovementSpeedAmount(stack),
                                getMovementSpeedOperation()
                        )
                );
            }

            // 击退抗性 / Knockback resistance
            if (getKnockbackResistanceAmount(stack) != 0) {
                multimap.put(
                        Attributes.KNOCKBACK_RESISTANCE,
                        new AttributeModifier(
                                KNOCKBACK_RESISTANCE,
                                Reference.MOD_ID + ":" + "knockback_resistance",
                                getKnockbackResistanceAmount(stack),
                                getKnockbackResistanceOperation()
                        )
                );
            }

            // 最大生命值 / Max health
            if (getMaxHealthAmount(stack) != 0) {
                multimap.put(
                        Attributes.MAX_HEALTH,
                        new AttributeModifier(
                                MAX_HEALTH,
                                Reference.MOD_ID + ":" + "max_health",
                                getMaxHealthAmount(stack),
                                getMaxHealthOperation()
                        )
                );
            }
        }

        return multimap;
    }

    // ========== 抽象方法（子类实现）/ Abstract methods (implemented by subclass) ==========

    public abstract double getAttackDamageAmount(ItemStack itemStack);

    public AttributeModifier.Operation getAttackDamageOperation() {
        return AttributeModifier.Operation.ADDITION;
    }

    public abstract double getAttackSpeedAmount(ItemStack itemStack);

    public AttributeModifier.Operation getAttackSpeedOperation() {
        return AttributeModifier.Operation.ADDITION;
    }

    public double getMovementSpeedAmount(ItemStack itemStack) {
        return 0;
    }

    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.ADDITION;
    }

    public double getKnockbackResistanceAmount(ItemStack itemStack) {
        return 0;
    }

    public AttributeModifier.Operation getKnockbackResistanceOperation() {
        return AttributeModifier.Operation.ADDITION;
    }

    public double getMaxHealthAmount(ItemStack itemStack) {
        return 0;
    }

    public AttributeModifier.Operation getMaxHealthOperation() {
        return AttributeModifier.Operation.ADDITION;
    }

    /**
     * 统一设置武器基础属性（业务逻辑100%不变）
     * Set base weapon attributes uniformly (business logic 100% unchanged)
     */
    protected ItemStack setBaseWeaponAttribute(ItemStack itemStack,
                                               double damage,
                                               double critProb,
                                               double critMult,
                                               double triggerChance) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag weaponModule = tag.getCompound(Reference.MOD_ID + "_weaponModules");

        weaponModule.putDouble("damage", damage);
        weaponModule.putDouble("criticalStrikeProbability", critProb);
        weaponModule.putDouble("criticalStrikeMultiplier", critMult);
        weaponModule.putDouble("triggerChance", triggerChance);

        tag.put(Reference.MOD_ID + "_weaponModules", weaponModule);
        return itemStack;
    }
}