// MixinGunShootContext.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 ModernKineticGunScriptAPI 的射击流程，管理射击期间的 ThreadLocal 状态。
 * <p>
 * 每一发开火前：检测满弹夹第一发 + 设置枪械伤害加成 + 设置玄骸强化乘数；这一发打完：清除。
 *
 * <h3>为什么要注入 {@code lambda$shootOnce$2}（2026-09-25）</h3>
 * <p>TACZ 的 {@code shootOnce} 把「开一发」包成一个 lambda 交给 {@code CycleTaskHelper.addCycleTask}：
 * 第一发在 addCycleTask 里当场执行，<b>多连发（BURST）的后几发排进队列、在之后的服务端 tick 里执行</b>。
 * 原先只在 shootOnce 的 HEAD 设、RETURN 清，后几发执行时 ThreadLocal 早已清空 ——
 * 一轮点射只有第一发吃到枪械伤害和女皇之力（javap 核对过 1.1.7-hotfix 的字节码：
 * addCycleTask 先 {@code ticker.tick()} 一次再入队；lambda 里先 {@code reduceAmmoOnce} 后生成子弹）。</p>
 * <p>现在每一发（这个 lambda）进门时按当前状态重新设置、出门时清除。满弹夹判定在进门时做，
 * 此时这一发还没扣弹，所以点射只有第一发满足「满弹夹」，与单发一致。
 * shootOnce 上的两处注入保留作兜底：TACZ 换版本导致 lambda 编号变了、注入落空时（{@code require = 0}），
 * 至少回到「第一发生效」的旧行为，不会一发都不生效。</p>
 *
 * <h3>为什么 method 要写完整描述符</h3>
 * <p>javac 给 lambda 编号是全类共用、内层先于外层：1.1.7-hotfix / hotfix2 里 $0、$1 是这个 lambda 体内
 * 处理热量的两个小 lambda（{@code (LuaTable)LuaFunction}、{@code (LuaFunction)V}），外层才是 $2。
 * TACZ 只要在 shootOnce 或排在它前面的方法里多写一个 lambda，名字 $2 就会落到别的 lambda 上。
 * 只写名字时：落到返回 void 的那个上，处理方法的 {@code CallbackInfoReturnable} 对不上签名，Mixin 抛
 * {@code InvalidInjectionException}（{@code require = 0} 管不了），这个 mixin 整个不生效，连 shootOnce 兜底也没了；
 * 落到有返回值的热量 lambda 上，注入能成功，但它在生成子弹之前就返回，RETURN 提前清空加成，
 * 有热量数据的枪连第一发也吃不到。写死描述符后，编号错位就是「0 处匹配」，才真正退回上面说的兜底。
 * 换 TACZ 版本后 lambda 编号可能变，仍要用 {@code javap -p} 看 {@code ModernKineticGunScriptAPI} 核对。</p>
 *
 * <h3>已知的小漏洞：异常路径不清除</h3>
 * <p>HEAD 设、RETURN 清之间没有 finally（{@code @Inject} 做不到）。这一发里抛异常时（GunFireEvent 的别的监听、
 * Lua 热量脚本、生成子弹时别的模组的事件），加成会留在服务端主线程上，直到下一次任何人开枪在 HEAD 重置。
 * 这期间不经过 shootOnce 构造的 {@code EntityKineticBullet}（别的模组自己生成的子弹）会读到残留值。
 * 第一发的异常被 TACZ 的网包处理吞掉，服务器照常运行；后几发在 {@code CycleTaskHelper.tick} 里抛会直接进服务器 tick，
 * 谈不上残留。改 shootOnce 前的旧写法也一样，影响很小，暂不改结构。</p>
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public abstract class MixinGunShootContext {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * TACZ 自己的「这把枪最多装多少发」。
     *
     * <p>不要自己去调 {@code AttachmentDataUtils.getAmmoCountWithAttachment}：别的模组
     *（比如 AlbionMastery 的弹匣特化）是挂在<b>这个方法内部的调用点</b>上改容量的，
     * 绕过去算出来的是没算别人加成的数。满弹判定拿它当阈值，会从「只有第一发」
     * 变成「弹药量还很高时每一发都算第一发」，或者反过来永远判不到满弹。
     *
     * @return 这把枪当前的弹匣上限
     */
    @Shadow
    public abstract int getMaxAmmoCount();

    /**
     * shootOnce 方法头部：检测满弹夹射击 + 设置枪械伤害 + 设置玄骸强化乘数 ThreadLocal。
     */
    @Inject(method = "shootOnce", at = @At("HEAD"), require = 0)
    private void kuvalich$detectFirstBullet(boolean isAiming, CallbackInfo ci) {
        kuvalich$setupShot();
    }

    /**
     * 每一发开火前（含多连发排在后面 tick 里的那几发）重新设置射击期间的加成
     */
    @Inject(method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/pojo/data/gun/GunData;ILcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFIZ)Z", at = @At("HEAD"), require = 0)
    private void kuvalich$setupEachShot(CallbackInfoReturnable<Boolean> cir) {
        kuvalich$setupShot();
    }

    /**
     * 每一发打完就清掉，免得漏到同一 tick 里别的射手身上
     */
    @Inject(method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/pojo/data/gun/GunData;ILcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFIZ)Z", at = @At("RETURN"), require = 0)
    private void kuvalich$clearEachShot(CallbackInfoReturnable<Boolean> cir) {
        kuvalich$clearShot();
    }

    private void kuvalich$setupShot() {
        // 默认清除，确保每次射击重新判定
        WarframeTaczBridge.clearFirstBulletDamageBonus();
        WarframeTaczBridge.clearGunDamageBonus();
        WarframeTaczBridge.clearGunEnhanceMultiplier();

        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // ========== 玄骸强化乘数（不依赖开光，仅检查NBT） ==========
        // 独立于模组系统，任何带有 kuvalich_gun_enhance_count 标记的TACZ枪均可生效
        double enhanceMultiplier = TaczGunEnhanceUtil.getDamageMultiplier(itemStack);
        if (enhanceMultiplier != 1.0) {
            WarframeTaczBridge.setGunEnhanceMultiplier(enhanceMultiplier);
        }

        // ========== 满弹夹第一发检测 ==========
        float firstBulletMod = WarframeTaczBridge.getFirstBulletDamageMod(itemStack, shooter);
        if (firstBulletMod > 0f) {
            IGun iGun = IGun.getIGunOrNull(itemStack);
            if (iGun != null) {
                int currentAmmo = iGun.getCurrentAmmoCount(itemStack);
                int maxAmmo = getMaxAmmoCount();

                if (maxAmmo > 0 && currentAmmo >= maxAmmo) {
                    WarframeTaczBridge.setFirstBulletDamageBonus(firstBulletMod);
                }
            }
        }

        // ========== 枪械伤害加成（每次射击都生效） ==========
        float gunDamageMod = WarframeTaczBridge.getGunDamageMod(itemStack, shooter);
        if (gunDamageMod > 0f) {
            WarframeTaczBridge.setGunDamageBonus(gunDamageMod);
        }
    }

    /**
     * shootOnce 方法返回时清除所有 ThreadLocal，防止泄漏到后续射击。
     */
    @Inject(method = "shootOnce", at = @At("RETURN"), require = 0)
    private void kuvalich$clearShootFlags(boolean isAiming, CallbackInfo ci) {
        kuvalich$clearShot();
    }

    private static void kuvalich$clearShot() {
        WarframeTaczBridge.clearFirstBulletDamageBonus();
        WarframeTaczBridge.clearGunDamageBonus();
        WarframeTaczBridge.clearGunEnhanceMultiplier();
    }
}