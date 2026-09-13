package pers.roinflam.kuvalich.enchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.LogUtil;

import pers.roinflam.kuvalich.base.enchantment.AbstractEnchantment;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 死亡抵抗附魔
 * Death Resistance Enchantment
 *
 * 效果：提供对致命伤害的抵抗
 * Effect: Provide resistance to lethal damage
 *
 * 注意：具体的死亡抵抗逻辑需要在LivingDeathEvent中实现
 * Note: Specific death resistance logic needs to be implemented in LivingDeathEvent
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDeathResistance extends AbstractEnchantment {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 25;

    // ⭐ 以下三个数值是本次补实现时定的，语言文件只写了"牺牲经验值抵抗致命伤害,牺牲的数值基于附魔等级与伤害值"，
    //    没有给出公式，按需调整即可
    /** 1 级时每点致命伤害要消耗的经验点数；等级越高越便宜（除以等级） */
    private static final float XP_COST_PER_DAMAGE = 4.0F;
    /** 抵抗成功后保留的生命值 */
    private static final float HEALTH_LEFT_AFTER_RESIST = 1.0F;

    public EnchantmentDeathResistance() {
        super(Rarity.VERY_RARE,
                EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                },
                "death_resistance");
    }

    /**
     * ⭐ 本附魔原来只有注册、没有任何效果实现（应是移植时丢了）。补上：
     * 玩家受到致命伤害时，若护甲带本附魔且经验足够，扣除经验并把伤害压到只剩 1 点血。
     *
     * <p>用 LivingDamageEvent 而不是 LivingHurtEvent：这里的伤害已经扣过护甲、抗性和吸收，
     * 才能准确判断"这一下会不会死"。优先级最低，让其他减伤先算完。</p>
     *
     * <p>不拦截 {@code BYPASSES_INVULNERABILITY}（/kill、虚空）这类伤害。</p>
     *
     * @param evt 伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!(evt.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (evt.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float damage = evt.getAmount();
        if (damage < player.getHealth()) {
            return;
        }
        int level = getMaxEnchantmentLevel(player);
        if (level <= 0) {
            return;
        }
        int cost = (int) Math.ceil(damage * XP_COST_PER_DAMAGE / level);
        int available = getTotalExperiencePoints(player);
        if (cost <= 0 || available < cost) {
            return;
        }
        player.giveExperiencePoints(-cost);
        float newDamage = Math.max(0.0F, player.getHealth() - HEALTH_LEFT_AFTER_RESIST);
        evt.setAmount(newDamage);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("message.kuvalich.deathResistance", cost)
                .withStyle(ChatFormatting.GOLD), true);
        LogUtil.debugEvent("死亡抵抗", player.getName().getString(),
                "等级=" + level + " 致命伤害=" + damage + " 消耗经验=" + cost);
    }

    /**
     * 取四件护甲上本附魔的最高等级
     *
     * @param entity 目标实体
     * @return 最高等级，没有则为 0
     */
    private static int getMaxEnchantmentLevel(LivingEntity entity) {
        int maxLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor == null || armor.isEmpty()) {
                continue;
            }
            maxLevel = Math.max(maxLevel, EnchantmentHelper.getItemEnchantmentLevel(
                    KuvaLichEnchantments.DEATH_RESISTANCE.get(), armor));
        }
        return maxLevel;
    }

    /**
     * 按当前等级和进度实际算出玩家拥有的经验点数
     *
     * <p>不用 {@code player.totalExperience}：附魔台扣等级时原版不会同步更新它，数值会失真。</p>
     *
     * @param player 玩家
     * @return 经验点数
     */
    private static int getTotalExperiencePoints(Player player) {
        int total = 0;
        for (int lvl = 0; lvl < player.experienceLevel; lvl++) {
            total += xpNeededForLevel(lvl);
        }
        total += (int) (player.experienceProgress * player.getXpNeededForNextLevel());
        return total;
    }

    /**
     * 从第 lvl 级升到 lvl+1 级需要的经验（与原版 {@code Player.getXpNeededForNextLevel} 同一公式）
     *
     * @param lvl 当前等级
     * @return 所需经验
     */
    private static int xpNeededForLevel(int lvl) {
        if (lvl >= 30) {
            return 112 + (lvl - 30) * 9;
        }
        if (lvl >= 15) {
            return 37 + (lvl - 15) * 5;
        }
        return 7 + lvl * 2;
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }
}
