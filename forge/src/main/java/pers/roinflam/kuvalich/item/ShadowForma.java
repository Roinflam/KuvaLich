// ShadowForma.java
// forge/src/main/java/pers/roinflam/kuvalich/item/ShadowForma.java
package pers.roinflam.kuvalich.item;

import net.minecraft.world.item.Item;

import javax.annotation.Nonnull;

/**
 * 暗影塑形块（Shadow Forma）
 * 与塑形块相同，在安魂之融中重新随机已开光武器的基础面板属性，
 * 但无视面板锁定，洗完后解除该武器的锁定，且本次不再掷锁定概率
 *
 * <p>继承 {@link Forma}：材料槽放置、Shift 快速转移、tooltip 描述行等所有
 * {@code instanceof Forma} 判定自动覆盖本物品。洗面板分支见
 * {@code RequiemEvolveMenu#processCrafting}，本物品的分支必须排在普通 Forma 之前。</p>
 *
 * <p>无配方、无掉落，仅通过创造模式获取。</p>
 */
public class ShadowForma extends Forma {

    public ShadowForma(@Nonnull Item.Properties properties) {
        super(properties);
    }
}
