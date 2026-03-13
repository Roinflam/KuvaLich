package pers.roinflam.kuvalich.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 棱晶维利图（1.20.1版本，修复攻击距离问题）
 * Prisma Veritux (1.20.1 version, fixed reach distance issue)
 */
@Mod.EventBusSubscriber
public class PrismaVeritux extends AbstractKuvaWeapon {

    /**
     * 实体交互距离UUID（用于攻击距离）
     * Entity reach UUID (for attack range)
     */
    private static final UUID ENTITY_REACH_UUID = UUID.fromString("e4e74f53-41a0-f8ae-41e8-8de5cfd7f2ee");

    public PrismaVeritux(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.30, 2.0, 0.20);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity hurter = event.getEntity();
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, PrismaVeritux.class);

        if (weapon != null) {
            if (hurter.getArmorValue() > 0) {
                // 目标有护甲时不额外增伤
                // No extra damage when target has armor
            } else {
                // 目标无护甲时增加25%伤害
                // +25% damage when target has no armor
                event.setAmount(event.getAmount() * KuvaWeaponUtil.getMagnification(weapon, 1.25f));
            }
        }
    }

    /**
     * 重写属性修饰符以添加交互距离
     * Override attribute modifiers to add reach distance
     */
    @Override
    public @NotNull Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            @NotNull EquipmentSlot slot,
            @NotNull ItemStack stack) {

        // 先获取基础属性（攻击力、攻速等）
        // First get base attributes (attack damage, speed, etc.)
        Multimap<Attribute, AttributeModifier> multimap = super.getAttributeModifiers(slot, stack);

        if (slot == EquipmentSlot.MAINHAND) {
            double reachBonus = KuvaWeaponUtil.getMagnification(stack, 1, 2);

            // ✅ 添加方块交互距离（挖掘、放置方块）
            // Add block reach (mining, placing blocks)
            multimap.put(
                    ForgeMod.BLOCK_REACH.get(),
                    new AttributeModifier(
                            REACH_DISTANCE,  // 使用基类定义的UUID
                            Reference.MOD_ID + ":block_reach",
                            reachBonus,
                            AttributeModifier.Operation.ADDITION
                    )
            );

            // ✅ 关键修复：添加实体交互距离（攻击、交互实体）
            // Critical fix: Add entity reach (attacking, interacting with entities)
            multimap.put(
                    ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(
                            ENTITY_REACH_UUID,  // 使用独立的UUID避免冲突
                            Reference.MOD_ID + ":entity_reach",
                            reachBonus,
                            AttributeModifier.Operation.ADDITION
                    )
            );
        }

        return multimap;
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamagePrismaVeritux.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedPrismaVeritux.get()));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeaponUtil.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedPrismaVeritux.get()));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}