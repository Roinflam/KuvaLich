package pers.roinflam.kuvalich.module.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 内融核心（Endo）掉落事件处理器
 * <p>
 * 条件：等级系统启用 + 击杀者持开光武器 + 击杀Monster → 概率掉落内融核心
 * 击杀者可以是任何LivingEntity（玩家、女仆、铁傀儡、驯服狼等均可触发）
 * 掉落数量由配置 endoDropMinAmount / endoDropMaxAmount 控制
 * </p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
public class EndoDropHandler {

    /**
     * 监听生物死亡事件，处理内融核心掉落
     */
    @SubscribeEvent
    public static void onLivingDeath(@Nonnull LivingDeathEvent event) {
        if (!ModuleLevelHelper.isLevelSystemEnabled()) { return; }
        if (event.getEntity().level().isClientSide()) { return; }
        if (!(event.getEntity() instanceof Monster)) { return; }

        // 击杀者必须是LivingEntity（玩家、女仆、铁傀儡等均可）
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) { return; }

        // 击杀者主手必须持有已开光的武器
        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) { return; }

        // 基础掉落概率判定
        double chance = ModConfig.KUVA_LICH.endoDropChance.get();
        if (chance <= 0 || !RandomUtil.percentageChance(chance)) { return; }

        // 根据配置计算掉落数量
        int minAmount = ModConfig.KUVA_LICH.endoDropMinAmount.get();
        int maxAmount = ModConfig.KUVA_LICH.endoDropMaxAmount.get();
        int amount = (minAmount >= maxAmount) ? minAmount : RandomUtil.getInt(minAmount, maxAmount);

        // 掉落内融核心（在被击杀怪物的位置）
        ItemStack endoStack = new ItemStack(KuvaLichItems.ENDO.get(), amount);
        ServerLevel level = (ServerLevel) event.getEntity().level();
        ItemEntity entityItem = new ItemEntity(level,
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), endoStack);
        level.addFreshEntity(entityItem);
    }
}