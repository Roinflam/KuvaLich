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
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

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
 * 渲染组件（{@link PanelGridTooltip} → {@link ClientPanelGridTooltip}），
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
        memoStack = null;
        memoTag = null;
        memoElements = null;
        memoTick = Long.MIN_VALUE;
    }

    // ==================== 渲染组件注册 ====================

    /**
     * 把 {@link PanelGridTooltip}（纯数据）接到 {@link ClientPanelGridTooltip}（渲染器）上
     *
     * <p>排版在这里做而不是在构建内容时做，是因为列宽必须按<b>当前字体</b>实测，
     * 而字体随语言与资源包变化。</p>
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {

        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(PanelGridTooltip.class, data -> {
                Minecraft mc = Minecraft.getInstance();
                int screenWidth = mc.getWindow().getGuiScaledWidth();

                // ⭐ 四个视图共用同一个宽度上限。
                //    曾经给功能键视图单独放宽过，想让它靠「变宽」换「变矮」而不是截断，
                //    但宽度上限同样会影响列数：默认视图卡在两列、SHIFT 能排到三列，
                //    于是同一把武器两种排版。一致性比那点余量重要。
                int maxWidth = (int) (screenWidth * TooltipConfig.PANEL.widthRatio.get());

                // ⭐ 高度也要传进去：面板超屏时原版不裁剪也不滚动，只会把顶部推到屏幕外面
                return ClientPanelGridTooltip.layout(data, mc.font, maxWidth, mc.getWindow().getGuiScaledHeight());
            });
        }

        private Registration() {
        }
    }

    // ==================== 事件 ====================

    /**
     * ⭐ {@link EventPriority#LOW}：让其它模组先写完，我们最后落位，插入结果才是确定的。
     *
     * <p><b>插到哪里</b>见 {@link #insertIndex}。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> ours = build(stack);
        if (ours.isEmpty()) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.addAll(insertIndex(elements), ours);
    }

    /**
     * 插入点：物品名之后，但要排在物品<b>自带的渲染组件</b>之后
     *
     * <p><b>为什么是这条规则</b>：物品可以通过 {@code Item#getTooltipImage} 返回一个
     * {@code TooltipComponent}，Forge 会在 {@code ForgeHooksClient.gatherTooltipComponents}
     * 里用 {@code elements.add(1, Either.right(c))} 把它塞到下标 1，而且这一步<b>发生在
     * 本事件 post 之前</b>。TACZ 的枪械信息（弹药 / 枪种 / 基础伤害 / 穿甲 / 爆头 / 重量 / 等级）
     * 整块就是这么来的 —— 它是一个 {@code ClientGunTooltip} 组件画完的，不是若干文本行。</p>
     *
     * <p>那一块是这把枪的<b>身份信息</b>，不该被本模组的面板挤到下面去；
     * 而对没有自带组件的武器（本模组自己的近战武器），下标 1 就是物品名的正下方 ——
     * 正是改造前面板所在的位置。所以一条规则同时满足两边：
     * <b>从下标 1 开始，跳过所有连续的渲染组件，插在它们后面。</b></p>
     *
     * <p>这样写还有一个好处：它不认 TACZ，只认「物品自带的组件」这个通用形态，
     * 任何用同一套机制的模组都能正确避让。整合包里的 Iceberg 会以 HIGHEST 优先级
     * 在物品名后插一个分隔组件，也一并被跳过。</p>
     *
     * <p>⭐ 这里曾经用过一套「在 ItemTooltipEvent 里放占位行、再在本事件里替换」的做法，
     * 前提是「TACZ 会重建整个列表并把未知行挪到末尾」—— 那个前提是<b>错的</b>：
     * 实测 TACZ 全 jar 里没有任何一处订阅 {@code RenderTooltipEvent}，
     * 它唯一的 tooltip 监听器只是在 F3+H 时往末尾追加一行 {@code GunId: "..."}。
     * 而跨两个事件用 static 字段做对象身份匹配本身也不可靠：JEI 有自己的
     * {@code gatherTooltipComponents} 实现，根本不走 {@code getTooltipImage} 那条链路，
     * 一进去就必然失配、落到兜底的「追加到末尾」。</p>
     */
    private static int insertIndex(List<Either<FormattedText, TooltipComponent>> elements) {
        TooltipConfig.Position position = TooltipConfig.PANEL.position.get();
        if (position == TooltipConfig.Position.BOTTOM) {
            return elements.size();
        }
        if (elements.isEmpty()) {
            return 0;
        }
        if (position == TooltipConfig.Position.TOP) {
            return Math.min(1, elements.size());
        }
        int at = Math.min(1, elements.size());
        while (at < elements.size() && elements.get(at).right().isPresent()) {
            at++;
        }
        return at;
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
