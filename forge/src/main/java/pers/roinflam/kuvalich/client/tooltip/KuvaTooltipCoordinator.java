package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 本模组唯一的武器 tooltip 事件入口
 *
 * <p><b>为什么要收成一个入口</b>：改造前有三个独立的监听器都往
 * {@code tooltip.add(1, ...)} 这个**硬编码下标**插行 ——
 * {@code WeaponModuleHandler}（面板）、{@code KuvaWeaponUtil}（玄骸类型行）、
 * {@code TaczCompatEventHandler}（玄骸之力行）。三者同为默认 NORMAL 优先级，
 * 相对顺序取决于 Forge 的注解扫描顺序。
 * 当前构建下恰好是想要的效果（类路径里 {@code module/weapon} 排在 {@code weapon} 前面），
 * 但升 Forge 版本、改包名或改打包方式都可能让它无声翻转 ——
 * 届时「赤毒类型 Xata 3」会从面板最上方掉到几十行属性下面，
 * 而代码里找不到任何显式依据去解释为什么。</p>
 *
 * <p>现在三段内容按固定顺序由这一个监听器一次性写入，顺序有代码保证。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaTooltipCoordinator {

    // ==================== 单条目 memo ====================

    /**
     * tooltip 在 1.20.1 是**每帧重建**的，悬停一把满配武器时每帧要做
     * 8 次 {@code ItemStack.of} 反序列化 + 几十个 {@code Component} 构造。
     * 单帧成本只有几十微秒（不会掉帧），但持续的对象分配会抬高 young GC 频率，
     * 在本来就 GC 压力大的整合包里表现为翻背包时的微卡顿。
     *
     * <p>玩家一次只会悬停一个物品，所以单条目 memo 的命中率接近 100%，
     * 未命中时也只是走回改造前的老路，没有任何行为风险。</p>
     */
    private static ItemStack memoStack;
    private static CompoundTag memoTag;
    private static TooltipView memoView;
    private static int memoWidth;
    private static long memoTick = Long.MIN_VALUE;
    private static List<Component> memoLines;

    /** memo 有效期（tick）。叠层层数最快 5 tick 变一次，与同步周期对齐。 */
    private static final int MEMO_TTL_TICKS = 5;

    private static void clearMemo() {
        memoStack = null;
        memoTag = null;
        memoLines = null;
        memoTick = Long.MIN_VALUE;
    }

    // ==================== 事件 ====================

    /**
     * ⭐ {@link EventPriority#LOW}：只是为了让插入<b>时机</b>确定（我们最后执行）。
     *
     * <p>插入<b>位置</b>固定在 index 1，所以净效果是「最后执行、但排在最上面」：
     * 其它模组（多为 NORMAL）先写完之后，我们再把整块塞到物品名后面，
     * 把它们统统下压。这是有意的 —— 面板贴着物品名最好读。
     * 若将来想让别的模组排在上面，改成 {@code addAll(lines)}（追加到末尾）即可。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        List<Component> lines = build(stack, event.getEntity());
        if (lines.isEmpty()) {
            return;
        }

        // 一次性 addAll 到名称行之后，不再有跨类的 index 约定
        int insertAt = Math.min(1, event.getToolTip().size());
        event.getToolTip().addAll(insertAt, lines);
    }

    /**
     * 构建本模组要插入的全部行
     *
     * <p>顺序由这里保证：玄骸类型 → 玄骸之力 → 武器面板。</p>
     */
    private static List<Component> build(ItemStack stack, @Nullable Player viewer) {
        TooltipView view = TooltipView.current();
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;

        if (memoLines != null
                && memoStack == stack
                && memoTag == stack.getTag()
                && memoView == view
                && memoWidth == width
                && now - memoTick < MEMO_TTL_TICKS) {
            return memoLines;
        }

        List<Component> lines = new ArrayList<>();
        KuvaWeaponUtil.appendTypeLine(lines, stack);
        TaczGunEnhanceUtil.appendEnhanceLine(lines, stack);
        lines.addAll(WeaponPanelComposer.compose(stack, viewer, view));

        memoStack = stack;
        memoTag = stack.getTag();
        memoView = view;
        memoWidth = width;
        memoTick = now;
        memoLines = lines;
        return lines;
    }

    /**
     * 断开连接 / 退出世界时清理客户端状态
     *
     * <p>⭐ 不能用 {@code PlayerLoggedOutEvent}：那个事件只在服务端触发
     * （{@code PlayerList#remove}），项目里既有的
     * {@code WarframeEffectHandler.onPlayerLoggedOut} 里那段
     * {@code if (isClientSide())} 分支是恒假的死代码。</p>
     */
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearMemo();
        LiveStackView.resetClock();
    }

    private KuvaTooltipCoordinator() {
    }
}
