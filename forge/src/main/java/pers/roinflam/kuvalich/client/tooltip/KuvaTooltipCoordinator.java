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

    // ==================== 事件 ====================

    /**
     * ⭐ {@link EventPriority#LOW}：让我们最后执行，这样插入位置相对其它模组是确定的。
     *
     * <p><b>插到哪里</b>由 {@code panel.position} 决定，默认 BOTTOM。
     * 这一条是踩过坑才定下来的：TACZ 枪械的弹药 / 枪种 / 基础伤害那一大块
     * 是它通过本事件加的（里面有弹药图标，只能走这条路）。
     * 我们既然跑在它后面，再插到 index 1 就会把它整块压下去 ——
     * 一把枪的身份信息被别的模组挤到第二屏，观感很差。
     * 默认接在后面，本模组自己的武器想贴着名字的话把配置改成 TOP。</p>
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
        if (TooltipConfig.PANEL.position.get() == TooltipConfig.Position.TOP) {
            elements.addAll(Math.min(1, elements.size()), ours);
        } else {
            elements.addAll(insertPoint(elements), ours);
        }
    }

    /**
     * BOTTOM 模式的插入点：末尾，但要排在「高级信息」之前
     *
     * <p>开了 F3+H 时原版会在最末尾追加注册名与 NBT 标签数
     * （{@code ItemStack#getTooltipLines} 在 {@code TooltipFlag#isAdvanced} 分支里加的）。
     * 直接 append 会让面板跑到那堆调试信息下面，很别扭。
     * 这里从末尾往回找，跳过那几行纯调试文本。</p>
     */
    private static int insertPoint(List<Either<FormattedText, TooltipComponent>> elements) {
        int at = elements.size();
        if (!Minecraft.getInstance().options.advancedItemTooltips) {
            return at;
        }
        // 高级信息最多两行（注册名 + NBT 标签数），且一定是纯文本
        for (int i = 0; i < 2 && at > 1; i++) {
            Either<FormattedText, TooltipComponent> last = elements.get(at - 1);
            if (last.left().isEmpty()) {
                break;
            }
            at--;
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
