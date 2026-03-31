package pers.roinflam.kuvalich.module.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.HashMap;

/**
 * 内融核心（Endo）掉落事件处理器
 * <p>
 * 条件：等级系统启用 + 玩家持开光武器 + 击杀Monster → 概率掉落内融核心
 * 掉落概率受战甲战利品倍率模组影响
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
        if (!(event.getSource().getEntity() instanceof Player player)) { return; }
        if (!(event.getEntity() instanceof Monster)) { return; }

        // 玩家主手必须持有已开光的武器
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) { return; }

        // 基础掉落概率
        double baseChance = ModConfig.KUVA_LICH.endoDropChance.get();
        if (baseChance <= 0) { return; }

        // 应用战甲战利品倍率模组
        double effectiveChance = baseChance;
        HashMap<String, Double> warframeAttrs = WarframeModuleHandler.getCachedAttributes(player);
        WarframeModuleHandler.applyWarframeKillStackEffects(player, warframeAttrs);
        double rawDropMultiplier = warframeAttrs.getOrDefault("itemDropMultiplier", 0.0);
        double effectPercent = ModConfig.KUVA_LICH.itemDropEffectMultiplier.get() / 100.0;
        effectiveChance *= (1.0 + rawDropMultiplier * effectPercent);

        if (!RandomUtil.percentageChance(effectiveChance)) { return; }

        // 根据配置计算掉落数量
        int minAmount = ModConfig.KUVA_LICH.endoDropMinAmount.get();
        int maxAmount = ModConfig.KUVA_LICH.endoDropMaxAmount.get();
        int amount = (minAmount >= maxAmount) ? minAmount : RandomUtil.getInt(minAmount, maxAmount);

        // 掉落内融核心
        ItemStack endoStack = new ItemStack(KuvaLichItems.ENDO.get(), amount);
        ServerLevel level = (ServerLevel) event.getEntity().level();
        ItemEntity entityItem = new ItemEntity(level,
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), endoStack);
        level.addFreshEntity(entityItem);
    }
}
