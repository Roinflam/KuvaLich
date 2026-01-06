package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import java.util.Collection;

/**
 * 砍刀（Machete）
 *
 * 武器特性：
 * - 击杀动物/怪物时，掉落物品数量随机 x1-3倍
 * - 不影响装备掉落（盔甲、武器、工具除外）
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：10%
 * - 暴击倍率：1.5x
 * - 触发几率：15%
 */
@Mod.EventBusSubscriber
public class Machete extends KuvaWeaponBase {

    public Machete(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        // 95%概率为标准伤害，5%概率为80%-120%浮动
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        // ✅ 使用基类方法统一设置属性
        return setBaseWeaponAttribute(itemStack, damage, 0.10, 1.5, 0.15);
    }

    /**
     * 掉落物增幅事件
     * 击杀非Boss生物时，非装备类掉落物数量x1-3倍
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        // 只在服务端处理
        if (evt.getEntity().world.isRemote) return;

        // 必须是玩家击杀
        if (!(evt.getSource().getImmediateSource() instanceof EntityPlayer)) return;

        // 必须是动物或怪物
        if (!(evt.getEntityLiving() instanceof EntityAnimal || evt.getEntityLiving() instanceof EntityMob)) return;

        // 不能是Boss
        if (!evt.getEntityLiving().isNonBoss()) return;

        EntityPlayer player = (EntityPlayer) evt.getSource().getImmediateSource();

        // ✅ 使用工具类安全获取武器
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(player);

        if (weapon != null && weapon.getItem() instanceof Machete) {
            Collection<EntityItem> drops = evt.getDrops();
            for (EntityItem drop : drops) {
                ItemStack dropStack = drop.getItem();
                // 只对非装备物品生效（排除盔甲、武器、工具）
                if (!(dropStack.getItem() instanceof ItemArmor) &&
                        !(dropStack.getItem() instanceof ItemSword) &&
                        !(dropStack.getItem() instanceof ItemTool)) {
                    // 随机1-3倍掉落
                    dropStack.setCount(dropStack.getCount() * RandomUtil.getInt(1, 3));
                }
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageMachete));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedMachete, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedPennant, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}