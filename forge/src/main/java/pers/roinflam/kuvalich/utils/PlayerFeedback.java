package pers.roinflam.kuvalich.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家提示的统一出口：选通道（聊天 / 动作栏）、上 RGB 色、把句子里的值挑出来高亮
 *
 * <p><b>为什么要分通道</b>：改造前全部走 {@code sendSystemMessage}，也就是全进聊天框。
 * 其中「解密进度 +N」和「答案已全部解出」是<b>每杀一只奴仆就发一次</b>的，
 * 刷怪时聊天框会被这两条刷满，把玩家自己的对话和别的模组的提示全顶上去；
 * 而顶部本来就有一条带动画的解密 HUD（{@code DecryptionHudOverlay}）在显示同一件事，
 * 聊天框那份纯属重复。这类「看一眼就过去」的反馈改走动作栏
 * （{@code displayClientMessage(comp, true)}）—— 不进聊天历史、不互相堆叠。</p>
 *
 * <p>留在聊天框的是两类：<b>里程碑</b>（解密成功、等级上限提升、物品归还、解密失败）
 * 和<b>剧情台词</b>（玄骸的死亡台词与没收嘲讽）。这两类玩家会想往上翻着重看。</p>
 *
 * <p><b>值高亮</b>：{@code Component.translatable} 的参数可以是 {@code Component}，
 * 所以这里把每个参数都包成一个显式着色的子组件。子组件有自己的颜色，
 * 只继承父组件未设置的属性，因此「你的解密进度增加了 <b>15</b> 点」里那个 15
 * 会比整句更亮。译文用的是 {@code %s} 而不是 {@code %d}，正好接受 {@code Component}
 * —— 这一点不能改成 {@code %d}，原版的翻译格式化不支持。</p>
 *
 * @author RoinFlam
 */
public final class PlayerFeedback {

    private PlayerFeedback() {}

    /**
     * 发到聊天框：里程碑与剧情台词，玩家会想往上翻。
     *
     * @param player 目标玩家
     * @param rgb    整句的颜色，取自 {@link KuvaPalette}
     * @param key    译文键
     * @param args   译文参数，会被逐个包成高亮子组件
     */
    public static void chat(Player player, int rgb, String key, Object... args) {
        player.sendSystemMessage(translate(rgb, key, args));
    }

    /**
     * 发到动作栏：看一眼就过去的反馈，不进聊天历史。
     *
     * @param player 目标玩家
     * @param rgb    整句的颜色，取自 {@link KuvaPalette}
     * @param key    译文键
     * @param args   译文参数，会被逐个包成高亮子组件
     */
    public static void actionBar(Player player, int rgb, String key, Object... args) {
        player.displayClientMessage(translate(rgb, key, args), true);
    }

    /**
     * 拼一条着色并且值已高亮的译文组件。
     *
     * @param rgb  整句颜色
     * @param key  译文键
     * @param args 译文参数
     * @return 可直接发送的组件
     */
    public static MutableComponent translate(int rgb, String key, Object... args) {
        Object[] highlighted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            highlighted[i] = value(args[i]);
        }
        return Component.translatable(key, highlighted)
                .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
    }

    /**
     * 把一个译文参数包成高亮子组件。
     *
     * <p>已经是 {@code Component} 的（比如安魂卡名是一条 {@code translatable}）走 copy，
     * 否则按 {@code String.valueOf} 转字面量。两种都显式着色 ——
     * 不显式着色的子组件会整条继承父组件的颜色，也就看不出高亮。</p>
     *
     * @param raw 原始参数
     * @return 着上 {@link KuvaPalette#VALUE} 的子组件
     */
    public static MutableComponent value(Object raw) {
        MutableComponent component = (raw instanceof Component)
                ? ((Component) raw).copy()
                : Component.literal(String.valueOf(raw));
        return component.withStyle(style -> style.withColor(TextColor.fromRgb(KuvaPalette.VALUE)));
    }
}
