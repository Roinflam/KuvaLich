package pers.roinflam.kuvalich.base.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.tabs.KuvaLichTab;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

@Mod.EventBusSubscriber
public abstract class KuvaWeaponBase extends ItemSword implements IHasModel {
    public static final Item.ToolMaterial KUVA = EnumHelper.addToolMaterial("kuva", 3, 23333, 100.0f, 23.33f, 50);
    public static UUID MOVEMENT_SPEED = UUID.fromString("ea068571-91fe-da31-18c4-ca86c557bb58");
    public static UUID KNOCKBACK_RESISTANCE = UUID.fromString("d9bb87cf-b828-2154-215b-e8ca2b21d875");
    public static UUID MAX_HEALTH = UUID.fromString("157634e9-f993-110e-8705-dab08f43c437");
    public static UUID REACH_DISTANCE = UUID.fromString("e4e74f53-41a0-f8ae-41e8-8de5cfd7f2ed");

    public KuvaWeaponBase(@Nonnull String name) {
        super(KUVA);
        ItemUtil.registerItem(this, name, KuvaLichTab.getTab());
        KuvaLichItems.ITEMS.add(this);
        KuvaLichItems.KUVA_WEAPONS.add(this);
    }

    public abstract ItemStack getBaseAttribute(ItemStack itemStack);

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            items.add(KuvaWeapon.getItem(this, ModConfig.KUVA_LICH.baseMinimumLevel, ModConfig.KUVA_LICH.maximumLevel));
        }
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, Reference.MOD_ID + ":" + SharedMonsterAttributes.ATTACK_DAMAGE.getName(), getAttackDamageAmount(stack), getAttackDamageOperation()));
            multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, Reference.MOD_ID + ":" + SharedMonsterAttributes.ATTACK_SPEED.getName(), getAttackSpeedAmount(stack), getAttackSpeedOperation()));

            if (getMovementSpeedAmount(stack) != 0) {
                multimap.put(SharedMonsterAttributes.MOVEMENT_SPEED.getName(), new AttributeModifier(MOVEMENT_SPEED, Reference.MOD_ID + ":" + SharedMonsterAttributes.MOVEMENT_SPEED.getName(), getMovementSpeedAmount(stack), getMovementSpeedOperation()));
            }
            if (getKnockbackResistanceAmount(stack) != 0) {
                multimap.put(SharedMonsterAttributes.KNOCKBACK_RESISTANCE.getName(), new AttributeModifier(KNOCKBACK_RESISTANCE, Reference.MOD_ID + ":" + SharedMonsterAttributes.KNOCKBACK_RESISTANCE.getName(), getKnockbackResistanceAmount(stack), getKnockbackResistanceOperation()));
            }
            if (getMaxHealthAmount(stack) != 0) {
                multimap.put(SharedMonsterAttributes.MAX_HEALTH.getName(), new AttributeModifier(MAX_HEALTH, Reference.MOD_ID + ":" + SharedMonsterAttributes.MAX_HEALTH.getName(), getMaxHealthAmount(stack), getMaxHealthOperation()));
            }
        }
        return multimap;
    }

    public abstract double getAttackDamageAmount(ItemStack itemStack);

    public int getAttackDamageOperation() {
        return 0;
    }

    public abstract double getAttackSpeedAmount(ItemStack itemStack);

    public int getAttackSpeedOperation() {
        return 0;
    }

    public double getMovementSpeedAmount(ItemStack itemStack) {
        return 0;
    }

    public int getMovementSpeedOperation() {
        return 0;
    }

    public double getKnockbackResistanceAmount(ItemStack itemStack) {
        return 0;
    }

    public int getKnockbackResistanceOperation() {
        return 0;
    }

    public double getMaxHealthAmount(ItemStack itemStack) {
        return 0;
    }

    public int getMaxHealthOperation() {
        return 0;
    }

    /**
     * 统一设置武器基础属性（减少重复代码）
     *
     * @param itemStack 武器物品堆栈
     * @param damage 基础伤害倍率
     * @param critProb 暴击概率
     * @param critMult 暴击倍率
     * @param triggerChance 触发概率
     * @return 设置后的物品堆栈
     */
    protected ItemStack setBaseWeaponAttribute(ItemStack itemStack,
                                               double damage,
                                               double critProb,
                                               double critMult,
                                               double triggerChance) {
        NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

        weaponModule.setDouble("damage", damage);
        weaponModule.setDouble("criticalStrikeProbability", critProb);
        weaponModule.setDouble("criticalStrikeMultiplier", critMult);
        weaponModule.setDouble("triggerChance", triggerChance);

        tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
        return itemStack;
    }
}
