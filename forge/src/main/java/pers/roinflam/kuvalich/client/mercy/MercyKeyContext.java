package pers.roinflam.kuvalich.client.mercy;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

/**
 * 怜悯之击键的冲突范围：只在「眼前有自己能处决的目标、小牌亮着」或「自己正在处决」的时候算激活，别的时候这个键不归本模组
 *
 * <p>默认键和星际战甲一样是 X，而 X 在原版（创造模式的「加载快捷栏」）和不少模组里另有用处。Forge 只把按键分给激活中的映射
 * （{@code KeyMappingLookup.getAll} 按 {@code isActiveAndMatches} 过滤），所以平时按 X 根本落不到这里，X 原来干什么还干什么；
 * 只有小牌亮着时这一下才同时落到这里和同键的别的映射上，这时 {@link MercyPrompt} 在按键事件里当场把同键别的映射这一下的
 * 点击和按下状态清掉，免得处决的同时打开别的界面、放出别的技能。</p>
 *
 * <p>{@link #conflicts} 和 {@link KeyConflictContext#IN_GAME} 保持一致，不能只认自己：Forge 判断「不带修饰键」的映射能不能触发时，
 * 冲突范围不和 IN_GAME 冲突的，按住 Shift / Ctrl / Alt 就不算数（{@code KeyModifier.NONE.isActive}），那样蹲着按 X 会处决不了。
 * 代价是控制设置里同键的游戏内按键照旧标红，实际按的时候按上面那样分开，不会打架。</p>
 */
@OnlyIn(Dist.CLIENT)
public enum MercyKeyContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive() && (MercyPrompt.offering() || MercyClient.isActive());
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this || other == KeyConflictContext.IN_GAME;
    }
}
