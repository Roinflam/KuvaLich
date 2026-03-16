package pers.roinflam.kuvalich.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;

/**
 * 横扫之刃搜索范围Mixin
 *
 * 仅当主手武器拥有赤毒武器模组系统（hasBase）时生效，不影响原版逻辑。
 *
 * 两处修改（仅对模组武器生效）：
 * 1. 搜索范围：按未减半的攻击距离比例扩大 getSweepHitBox 的 AABB
 * 2. 距离限制：取消 Forge 的 distanceToSqr < reach * reach 检查
 *
 * 无模组武器时两处均返回原版默认值，行为完全不变。
 */
@Mixin(Player.class)
public abstract class MixinPlayerSweep {

    /**
     * 重定向 getSweepHitBox 调用
     * 仅对模组武器生效：按未减半的攻击距离比例扩大搜索范围 X/Z 轴
     * 非模组武器：返回原版默认搜索范围
     *
     * 推导：
     *   减半后实际 ENTITY_REACH = 3.0 × (1 + attackRange × 0.5)
     *   ratio = actualReach / 3.0
     *   未减半 fullRatio = 2 × ratio - 1
     *
     * 举例（attackRange=1.1）：
     *   实际 reach = 4.65格，ratio = 1.55
     *   fullRatio = 2 × 1.55 - 1 = 2.1
     *   搜索范围等效 inflate(2.1, 0.25, 2.1)
     *
     * @param stack  主手物品栈（原始调用者）
     * @param player 攻击的玩家
     * @param target 被攻击的目标实体
     * @return 缩放后的搜索 AABB（非模组武器返回原版值）
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getSweepHitBox(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/AABB;"
            )
    )
    private AABB kuvalich$scaleSweepHitBox(ItemStack stack, Player player, Entity target) {
        AABB original = stack.getSweepHitBox(player, target);

        // 非模组武器，走原版逻辑
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) {
            return original;
        }

        double reach = player.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        double ratio = reach / 3.0;

        // 还原为未减半的完整比例
        double fullRatio = 2.0 * ratio - 1.0;

        // 没有额外攻击距离时不扩大
        if (fullRatio <= 1.0) {
            return original;
        }

        double extraInflate = fullRatio - 1.0;
        return original.inflate(extraInflate, 0, extraInflate);
    }

    /**
     * 取消横扫的距离限制（仅对模组武器生效）
     * 非模组武器返回真实距离，保持原版行为
     *
     * 横扫的实际范围完全由上面的 getSweepHitBox 搜索 AABB 控制。
     *
     * @param self  玩家实例
     * @param other 被检查距离的实体
     * @return 模组武器返回 0.0（取消限制），非模组武器返回真实距离的平方
     */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double kuvalich$removeSweepDistanceCheck(Player self, Entity other) {
        ItemStack weapon = self.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) {
            return self.distanceToSqr(other);
        }
        return 0.0;
    }
}