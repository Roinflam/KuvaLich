package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;

/**
 * 一条面板属性是否该为这件武器生成
 *
 * <p>⭐ 这是把 tooltip 从五六十行压回来的**性价比最高的单点改动**：
 * 改造前，{@code reload_speed / magazine_size / recoil_reduction / gun_damage /
 * headshot_damage / aim_time / accuracy / true_bullet / gun_loot_drop / projectile_speed}
 * 这 10 条枪械词条对一把铁剑也会无条件生成——只要玩家在别处堆了这些词条，
 * 近战武器的面板上就会平白多出 10 行永远不会生效的数字。</p>
 *
 * <p>判定只看武器本身，不看玩家状态，所以纯函数、可缓存。</p>
 *
 * @author RoinFlam
 */
public enum PanelRowGate {

    /** 任何武器都生成 */
    ANY {
        @Override
        public boolean accepts(ItemStack stack) {
            return true;
        }
    },

    /**
     * 仅远程武器：TACZ 枪械、弓、弩。
     * <p>{@link TaczGunEnhanceUtil#isTaczGun} 可以无条件调用：TACZ 在 mods.toml 里是
     * {@code mandatory=true} 的硬依赖，缺它时本模组根本不会加载；
     * 项目里 {@code RequiemEvolveMenu} 也是这么直接调的。</p>
     */
    RANGED {
        @Override
        public boolean accepts(ItemStack stack) {
            // ⭐ 用 ProjectileWeaponItem 而不是 BowItem/CrossbowItem：
            //    arrowDamage 在战斗端的生效条件跟武器是什么类无关，只看弹体
            //    （{@code damageSource.getDirectEntity() instanceof Arrow}）。
            //    整合包里不继承 BowItem 但发射原版 Arrow 的自定义弓并不罕见，
            //    按具体类判会把它们的 arrowDamage 误隐藏。
            //    原版 BowItem / CrossbowItem 都是 ProjectileWeaponItem 的子类，多数模组弓也是。
            return stack.getItem() instanceof ProjectileWeaponItem
                    || TaczGunEnhanceUtil.isTaczGun(stack);
        }
    },

    /** 仅 TACZ 枪械（弓弩没有弹夹 / 后坐力 / ADS 这些概念） */
    GUN_ONLY {
        @Override
        public boolean accepts(ItemStack stack) {
            return TaczGunEnhanceUtil.isTaczGun(stack);
        }
    },

    /** 仅弓：射速在弓上是双倍生效的，需要单独一条 spec */
    BOW_ONLY {
        @Override
        public boolean accepts(ItemStack stack) {
            return stack.getItem() instanceof BowItem;
        }
    },

    /** 除弓以外（与 {@link #BOW_ONLY} 互补，保证射速永远只显示一条） */
    NOT_BOW {
        @Override
        public boolean accepts(ItemStack stack) {
            return !(stack.getItem() instanceof BowItem);
        }
    };

    public abstract boolean accepts(ItemStack stack);
}
