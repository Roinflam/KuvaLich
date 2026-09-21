package pers.roinflam.kuvalich.client.tooltip;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.config.TooltipConfig;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 本模组唯一的武器 tooltip 入口
 *
 * <p><b>为什么收成一个入口</b>：改造前有三个独立的监听器都往
 * {@code tooltip.add(1, ...)} 这个<b>硬编码下标</b>插行 ——
 * {@code WeaponModuleHandler}（面板）、{@code KuvaWeaponUtil}（玄骸类型行）、
 * {@code TaczCompatEventHandler}（玄骸之力行）。三者同为默认 NORMAL 优先级，
 * 相对顺序取决于 Forge 的注解扫描顺序与手工注册时机，换个 Forge 版本或改个包名
 * 就可能无声翻转。现在三段内容按固定顺序由这一处一次性写入。</p>
 *
 * <p><b>为什么用 {@link RenderTooltipEvent.GatherComponents} 而不是
 * {@code ItemTooltipEvent}</b>：后者的列表只收 {@code Component}，也就是纯文本行，
 * 想让「值」对齐只能靠空格凑 —— 中文一个字约等于两个字符宽，必然错位。
 * 本事件的列表是 {@code Either<FormattedText, TooltipComponent>}，可以塞进自定义
 * 渲染组件（{@link PanelGridComponent} → {@link ClientPanelGrid}），
 * 由它按字体实测宽度做像素级列对齐。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaTooltipCoordinator {

    // ==================== 单条目 memo ====================

    /**
     * tooltip 在 1.20.1 是<b>每帧重建</b>的，悬停一把满配武器时每帧要做
     * 8 次 {@code ItemStack.of} 反序列化 + 几十个 {@code Component} 构造。
     * 单帧成本只有几十微秒（不会掉帧），但持续的对象分配会抬高 young GC 频率，
     * 在本来就 GC 压力大的整合包里表现为翻背包时的微卡顿。
     *
     * <p>玩家一次只会悬停一个物品，所以单条目 memo 的命中率接近 100%，
     * 未命中时也只是走回老路，没有任何行为风险。</p>
     */
    private static ItemStack memoStack;
    private static CompoundTag memoTag;
    private static TooltipView memoView;
    private static int memoWidth;
    private static long memoTick = Long.MIN_VALUE;
    private static List<Either<FormattedText, TooltipComponent>> memoElements;

    /** memo 有效期（tick）。叠层层数最快 5 tick 变一次，与同步周期对齐。 */
    private static final int MEMO_TTL_TICKS = 5;

    private static void clearMemo() {
        placeholder = null;
        memoStack = null;
        memoTag = null;
        memoElements = null;
        memoTick = Long.MIN_VALUE;
    }

    // ==================== 渲染组件注册 ====================

    /**
     * 把 {@link PanelGridComponent}（纯数据）接到 {@link ClientPanelGrid}（渲染器）上
     *
     * <p>排版在这里做而不是在构建内容时做，是因为列宽必须按<b>当前字体</b>实测，
     * 而字体随语言与资源包变化。</p>
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {

        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(PanelGridComponent.class, data -> {
                Minecraft mc = Minecraft.getInstance();
                int screen = mc.getWindow().getGuiScaledWidth();
                int maxWidth = (int) (screen * TooltipConfig.PANEL.widthRatio.get());
                return ClientPanelGrid.layout(data, mc.font, maxWidth);
            });
        }

        private Registration() {
        }
    }

    // ==================== 占位符 ====================

    /**
     * 占位行的标记文本
     *
     * <p><b>为什么要绕这一道</b>：面板的位置必须与改造前一致，而改造前是在
     * {@code ItemTooltipEvent} 里插到 index 1 的 —— 那个事件发生在
     * {@code ItemStack#getTooltipLines} 内部，所以：</p>
     * <ul>
     *   <li>本模组自己的武器：面板落在物品名下面（属性修饰符「在主手时」之前）</li>
     *   <li>TACZ 枪械：TACZ 会在 {@code GatherComponents} 里<b>重建</b>整个 tooltip
     *       （它那块带弹药图标的信息只能走那条路），把不认识的行挪到末尾 ——
     *       于是面板自然落在 TACZ 信息块之后</li>
     * </ul>
     *
     * <p>这两种位置都是玩家熟悉且合理的，但它们不是同一条规则能算出来的。
     * 直接在 {@code GatherComponents} 里插入无论选 index 1 还是末尾都只能满足一边。</p>
     *
     * <p>所以：在 {@code ItemTooltipEvent} 里放一个<b>占位行</b>（位置规则完全复刻改造前），
     * 再在 {@code GatherComponents} 里把它替换成渲染组件。
     * 占位行经历了 TACZ 的重建之后停在哪儿，面板就在哪儿。</p>
     *
     * <p>占位行是一个<b>空的</b> {@code Component}，靠<b>对象身份</b>而不是文本内容识别：
     * 万一哪条路径只调 {@code getTooltipLines} 而不走渲染（例如图鉴建索引），
     * 留下的也只是一个空串，不会把一串标记文字暴露给玩家。</p>
     */
    private static Component placeholder;

    // ==================== 事件 ====================

    /**
     * 第一步：在原版的 tooltip 装配过程中占好位置
     *
     * <p>⭐ {@link EventPriority#LOW}：让其它模组先插完，我们的占位行落在它们之上，
     * 与改造前三个监听器的净效果一致。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty() || !hasPanel(stack)
                || TooltipConfig.PANEL.position.get() != TooltipConfig.Position.AUTO) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        Component marker = Component.empty();
        placeholder = marker;
        tooltip.add(Math.min(1, tooltip.size()), marker);
    }

    /**
     * 第二步：把占位行换成真正的渲染组件
     *
     * <p>找不到占位行时追加到末尾兜底 —— 万一哪个模组把它吃掉了，
     * 面板也不该整块消失。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        int slot = indexOfPlaceholder(elements);
        if (slot < 0 && !hasPanel(stack)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> ours = build(stack);
        if (slot >= 0) {
            elements.remove(slot);
            if (!ours.isEmpty()) {
                elements.addAll(slot, ours);
            }
            return;
        }
        if (ours.isEmpty()) {
            return;
        }
        // 没有占位行：要么玩家选了强制位置，要么占位行被别的模组吃掉了（兜底）
        if (TooltipConfig.PANEL.position.get() == TooltipConfig.Position.TOP) {
            elements.addAll(Math.min(1, elements.size()), ours);
        } else {
            elements.addAll(ours);
        }
    }

    /** 这件物品归本面板管吗（占位前的快速判断，避免给无关物品塞空行） */
    private static boolean hasPanel(ItemStack stack) {
        return pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler.hasBase(stack)
                || KuvaWeaponUtil.hasType(stack)
                || TaczGunEnhanceUtil.getEnhanceCount(stack) > 0;
    }

    private static int indexOfPlaceholder(List<Either<FormattedText, TooltipComponent>> elements) {
        Component marker = placeholder;
        if (marker == null) {
            return -1;
        }
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> e = elements.get(i);
            if (e.left().isPresent() && e.left().get() == marker) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 构建本模组要插入的全部内容
     *
     * <p>顺序由这里保证：玄骸类型 → 玄骸之力 → 武器面板。</p>
     */
    private static List<Either<FormattedText, TooltipComponent>> build(ItemStack stack) {
        TooltipView view = TooltipView.current();
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        long now = mc.level != null ? mc.level.getGameTime() : 0L;

        if (memoElements != null
                && memoStack == stack
                && memoTag == stack.getTag()
                && memoView == view
                && memoWidth == width
                && now - memoTick < MEMO_TTL_TICKS) {
            return memoElements;
        }

        // ⭐ 本事件拿不到「正在看 tooltip 的实体」，只能用本地玩家。
        //    叠层本来就是玩家级状态（不绑定具体物品），所以在 JEI 配方页上
        //    显示「你当前的层数」在语义上也是成立的。
        Player viewer = mc.player;

        List<Component> lead = new ArrayList<>(2);
        KuvaWeaponUtil.appendTypeLine(lead, stack);
        TaczGunEnhanceUtil.appendEnhanceLine(lead, stack);

        WeaponPanelComposer.PanelResult panel = WeaponPanelComposer.compose(stack, viewer, view);

        List<Either<FormattedText, TooltipComponent>> out =
                new ArrayList<>(lead.size() + panel.textLines().size() + 1);
        for (Component c : lead) {
            out.add(Either.left(c));
        }
        for (Component c : panel.textLines()) {
            out.add(Either.left(c));
        }
        if (panel.grid() != null && !panel.grid().isEmpty()) {
            out.add(Either.right(panel.grid()));
        }

        memoStack = stack;
        memoTag = stack.getTag();
        memoView = view;
        memoWidth = width;
        memoTick = now;
        memoElements = out;
        return out;
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
