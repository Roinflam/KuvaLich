package pers.roinflam.kuvalich.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;

/**
 * 最终伤害捕获 Mixin
 * Final Damage Capture Mixin
 *
 * <p>本类替代原先的 {@code MixinLivingEntityFinalDamage} 与
 * {@code MixinPlayerFinalDamage} 两个类，将四处注入点合并为一处。</p>
 *
 * <h3>解决的问题</h3>
 *
 * <p>伤害数字必须显示实体真正掉的血量，但通过 {@code LivingDamageEvent}
 * 监听器读取存在无法消除的优先级竞争：</p>
 * <ul>
 *   <li>{@code l2damagetracker.AttackEventHandler.onDamagePost}
 *       （以 jar-in-jar 内嵌于 L2Hostility 莱特兰·恶意）注册在 <b>LOWEST</b>，
 *       其内部 {@code AttackCache.pushDamagePre} 会 {@code setAmount}，
 *       应用莱特兰词缀的最终减伤 / 免疫。</li>
 *   <li>本模组的 {@code WarframeEffectHandler.onLivingDamage} 同样为 <b>LOWEST</b>，
 *       「受害者是玩家」分支会应用火抗 / 电抗 / 同源抗性。</li>
 * </ul>
 * <p>Forge 对同优先级监听器按注册顺序执行，顺序不受控，
 * 因此任何基于 {@code LivingDamageEvent} 的读取都可能拿到中间值。</p>
 *
 * <h3>为什么改注入 ForgeHooks 本身（2.7.2 变更）</h3>
 *
 * <p>原实现注入的是 {@code LivingEntity.actuallyHurt} / {@code Player.actuallyHurt}
 * 内部的 {@code ForgeHooks.onLivingDamage} <b>调用点</b>，该方案在 Mohist 上完全失效：
 * 服务端一个伤害数字都不显示，单机却正常。原因是 Mohist 改写了 {@code actuallyHurt}：</p>
 *
 * <pre>
 * protected void actuallyHurt(DamageSource src, float amount) {
 *     if (injectedHurt) {                                   // ← protected boolean injectedHurt = true;
 *         canDamage.set(damageEntity0(src, amount));        //   CraftBukkit 逻辑接管
 *         if (damage.get() != -999) {
 *             this.setHealth(this.getHealth() - (float) damage.getAndSet(-999));
 *         }
 *     } else {
 *         ...
 *         f1 = ForgeHooks.onLivingDamage(this, src, f1);    // ← 原注入位置，永不执行
 *         ...
 *     }
 * }
 * </pre>
 *
 * <p>{@code injectedHurt} 恒为 {@code true}，原版分支是<b>编译进字节码但运行时不可达的死代码</b>，
 * 真正会执行的同名调用被搬进了 CraftBukkit 的 {@code damageEntity0}。
 * 这也解释了为什么注入成功、服务端不崩溃（{@code require = 1} 由死代码的字节码满足），
 * 却一次都不触发。</p>
 *
 * <p><b>本方案不再关心调用点在哪</b>，直接注入被调用方法本身。
 * {@code ForgeHooks} 是 Forge 自己的类，原版与混合端完全一致：</p>
 *
 * <pre>
 * public static float onLivingDamage(LivingEntity entity, DamageSource src, float amount) {
 *     LivingDamageEvent event = new LivingDamageEvent(entity, src, amount);
 *     return (MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount());
 * }
 * </pre>
 *
 * <p>在其 RETURN 处取返回值，即整个 {@code LivingDamageEvent} 事件链跑完后的结果，
 * 语义与原实现<b>完全一致</b>；而无论调用来自 {@code LivingEntity.actuallyHurt}、
 * {@code Player.actuallyHurt} 还是 {@code damageEntity0}，全部汇聚到这一处。</p>
 *
 * <p>由此带来的收益：</p>
 * <ul>
 *   <li>两个 Mixin 类合并为一个，四处注入点减为一处；</li>
 *   <li>纯原版环境编写，{@code ForgeHooks} 不参与混淆，无需 {@code remap} 与
 *       {@code require} 的特殊处理，也不依赖 MixinExtras；</li>
 *   <li>不再与其他模组争抢调用点，彻底规避 {@code @Redirect} 独占冲突
 *       （RevelationFix 的 {@code setHealthValue} 就 {@code @Redirect} 了同一调用点，
 *       原实现正是为此才从 {@code @Redirect} 改用 {@code @ModifyExpressionValue}）。</li>
 * </ul>
 *
 * <h3>行为说明</h3>
 *
 * <ul>
 *   <li><b>只读注入</b>：使用 {@code @Inject} 而非任何修改型注入器，
 *       只读取返回值不做修改，对伤害计算与事件派发零影响。</li>
 *   <li><b>护盾</b>：捕获的值已扣除吸收护盾（护盾吃掉的部分不计入），此为既定设计。</li>
 *   <li><b>零伤害</b>：事件被取消或伤害被减为 0 时返回值为 0，回调依然触发，
 *       用于清理 {@code pendingDisplays} 队列条目，避免残留到下次受击时被错误消费
 *       （颜色码与元素图标张冠李戴）。</li>
 *   <li><b>仅服务端</b>：客户端直接返回，不参与派发。</li>
 * </ul>
 *
 * @author RoinFlam
 */
@Mixin(value = ForgeHooks.class, remap = false)
public class MixinForgeHooksFinalDamage {

    /**
     * 在 {@code ForgeHooks.onLivingDamage} 返回时读取最终扣血量并派发伤害数字。
     *
     * <p>该返回值是 {@code LivingDamageEvent} 事件链执行完毕后的结果，
     * 紧接着就会被调用方用于扣血，因此等于实体实际掉的血量，
     * 与任何模组的监听器优先级、注册顺序均无关。</p>
     *
     * <p>本方法只读取不修改，{@code cir} 不做任何写入操作。</p>
     *
     * @param entity 受击实体（原方法第 1 个形参）
     * @param source 伤害来源（原方法第 2 个形参）
     * @param amount 进入事件链前的伤害值（原方法第 3 个形参，此处不使用）
     * @param cir    回调信息，用于读取返回值；不做任何修改
     */
    @Inject(method = "onLivingDamage", at = @At("RETURN"), require = 1)
    private static void kuvalich$captureFinalDamage(LivingEntity entity, DamageSource source,
                                                    float amount, CallbackInfoReturnable<Float> cir) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }

        Float finalDamage = cir.getReturnValue();
        if (finalDamage == null) {
            return;
        }

        WeaponCombatHandler.onFinalDamage(entity, source, finalDamage);
    }
}