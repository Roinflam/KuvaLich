package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 阿卡提龙（1.20.1版本，使用动态属性系统）
 * Arca Titron (1.20.1 version, using dynamic attribute system)
 */
@Mod.EventBusSubscriber
public class ArcaTitron extends AbstractKuvaWeapon {

    /** 击杀叠层上限（amplifier 从 0 起算，9 即第 10 层） */
    private static final int MAX_AMPLIFIER = 9;
    /** 每层给暴击伤害倍率的加成 */
    private static final float CRIT_BONUS_PER_STACK = 0.05f;

    public ArcaTitron(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.24, 2.0, 0.38);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            // ⭐ 修复叠层：原来首杀直接 5 层、之后永远 6 层。现在每次击杀 +1 层，最高 9（共 10 层）；
            //    到顶后只刷新持续时间
            int current = DynamicAttributeManager.getAmplifier(attacker, DynamicAttributes.ARCA_TITRON);
            int newAmplifier = current < 0 ? 0 : Math.min(MAX_AMPLIFIER, current + 1);

            DynamicAttributeManager.apply(
                    attacker,
                    DynamicAttributes.ARCA_TITRON.createInstance(
                            (int) KuvaWeaponUtil.getMagnification(weapon, 400),
                            newAmplifier
                    )
            );
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        Player attacker = event.getEntity();
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            // ⭐ 修复叠层：暴击加成按实际层数算（原来写死 6 层）。每层 +5%，10 层 +50%
            int amplifier = DynamicAttributeManager.getAmplifier(attacker, DynamicAttributes.ARCA_TITRON);
            if (amplifier >= 0) {
                int level = amplifier + 1;
                float bonusDamage = KuvaWeaponUtil.getMagnification(weapon,
                        event.getDamageModifier() * level * CRIT_BONUS_PER_STACK);

                event.setDamageModifier(event.getDamageModifier() + bonusDamage);
            }
        }
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageArcaTitron.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeaponUtil.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedArcaTitron.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeaponUtil.getMagnification(itemStack,
                1 + ModConfig.KUVA_WEAPON.movementSpeedArcaTitron.get(), 2));
    }

    @Override
    public AttributeModifier.Operation getMovementSpeedOperation() {
        return AttributeModifier.Operation.MULTIPLY_BASE;
    }
}
