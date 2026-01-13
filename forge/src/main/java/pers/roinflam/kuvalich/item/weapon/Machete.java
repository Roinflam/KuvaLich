package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * 砍刀（1.20.1版本，业务逻辑100%不变）
 * Machete (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class Machete extends KuvaWeaponBase {

    public Machete(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.10, 1.5, 0.15);
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;
        if (!(event.getEntity() instanceof Animal || event.getEntity() instanceof Monster)) return;
        if (!event.getEntity().canChangeDimensions()) return;

        Player player = (Player) event.getSource().getEntity();
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(player);

        if (weapon != null && weapon.getItem() instanceof Machete) {
            Collection<ItemEntity> drops = event.getDrops();
            for (ItemEntity drop : drops) {
                ItemStack dropStack = drop.getItem();
                if (!(dropStack.getItem() instanceof ArmorItem) &&
                        !(dropStack.getItem() instanceof SwordItem) &&
                        !(dropStack.getItem() instanceof TieredItem)) {
                    dropStack.setCount(dropStack.getCount() * RandomUtil.getInt(1, 3));
                }
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        // ✅ 注意：配置值必须加.get()
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackDamageMachete.get()));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.attackSpeedMachete.get(), 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack,
                ModConfig.KUVA_WEAPON.movementSpeedMachete.get(), 2));
    }
}