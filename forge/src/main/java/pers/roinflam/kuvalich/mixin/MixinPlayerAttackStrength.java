package pers.roinflam.kuvalich.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;

/**
 * 标出「这次近战走的是原版 {@code Player.attack}」
 *
 * <p>蓄力折算只该对原版挥击生效：蓄力比例是原版攻击冷却的概念，只有走 {@code Player.attack} 的攻击才受它约束。
 * 拔刀剑这类自带连段的武器不走 {@code Player.attack}，自己调 {@code ForgeHooks.onPlayerAttackTarget}
 * 发出 {@code AttackEntityEvent} 再直接 {@code hurt}，原版冷却根本不归它管，几乎每一刀都停在刚重置的状态
 * （攻速低的刀只有 0.0 几）。以前在事件里照读冷却，开光后的拔刀剑伤害被乘成 0.0 几，比没开光还低。</p>
 *
 * <p>{@code Player.class} 不带 {@code remap}，方法写 Mojmap 名 {@code attack}，由 Loom 出包时换成 SRG。
 * HEAD 置位、RETURN 清位（含事件取消的提前返回）；中途抛异常留下的标记由服务端 tick END 兜底清掉。</p>
 */
@Mixin(Player.class)
public abstract class MixinPlayerAttackStrength {

    @Inject(method = "attack", at = @At("HEAD"))
    private void kuvalich$beginVanillaAttack(Entity target, CallbackInfo ci) {
        WeaponCombatHandler.beginVanillaAttack();
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void kuvalich$endVanillaAttack(Entity target, CallbackInfo ci) {
        WeaponCombatHandler.endVanillaAttack();
    }
}
