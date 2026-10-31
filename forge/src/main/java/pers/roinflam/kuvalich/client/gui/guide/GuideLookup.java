package pers.roinflam.kuvalich.client.gui.guide;

import com.mojang.datafixers.util.Either;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.config.TooltipConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 物品提示上按住 Ctrl 查阅：任何界面里物品提示框出来时（物品栏、箱子、创造模式物品栏、JEI 之类），指着的物品在指南里出现过、
 * 而且身上带着冒险指南，提示框末尾多一行「按住 Ctrl 在冒险指南中查看」；按住约 0.7 秒（行首的小圆圈转满）就翻到那一篇的那一块，
 * 关掉指南回到原来的界面。Mac 上是 Cmd（{@link Screen#hasControlDown()} 在 Mac 上查的就是 Cmd）。
 * 装了模组卡的武器要按 Ctrl + Shift（见下），那一行照实写。
 *
 * <ul>
 *   <li>挂在 Forge 的 {@link RenderTooltipEvent.GatherComponents} 上：只有真正画出来的提示框才触发（每帧一次），
 *       不会像 ItemTooltipEvent 那样混进 JEI 搜索索引、创造模式搜索之类的离屏提示里。最低优先级：排在本模组的武器面板和别的模组加的行后面，落在最末尾。</li>
 *   <li>进度圈做成提示框里的一行（{@link Hint}），位置、换行、遮挡都交给原版提示框排版，不在提示框旁边另画东西。</li>
 *   <li>计时按墙钟：连续按住、指着同一种物品才累计，中间有一帧没画这个提示（鼠标移开）或松开 Ctrl（要 Ctrl + Shift 的松开任一个）就从头来。</li>
 *   <li>打开一次后要先松开 Ctrl 才能再触发：关掉指南回到物品栏时手往往还按着 Ctrl，不能一回来又被打开。
 *       「松开了」每个客户端 tick 查一次（{@link #onGuideLookupClientTick}），不靠提示框：在指南里、在空白处松开的也算，
 *       不然下一次查阅要按两次。</li>
 *   <li>不在绘制途中换界面：到点后用 {@link Minecraft#tell} 排到下一帧开头再打开，届时界面已经换了就作罢。</li>
 * </ul>
 *
 * <p>「这个物品在哪一篇」查的是全局共用的 {@link GuideView#current()}（指南关着也能查）：条目手标的专篇优先，没标的按规则猜，
 * 只认当前配置下看得见的内容（规则见 {@link GuideView} 类注释），物品只出现在被条件藏掉的内容里时不显示这一行。</p>
 *
 * <p><b>装了模组卡的武器改按 Ctrl + Shift</b>：它们的提示框里有本模组的武器面板，单按 Ctrl 是切到「来源分解」视图
 * （{@code client.tooltip.TooltipView#SOURCE}），读那一屏要好几秒，按住 0.7 秒就跳走会让那个视图没法用。
 * Ctrl + Shift 时面板按 Shift 优先显示完整视图（{@code TooltipView#current}），不占用别的视图。
 * 没装模组卡的武器按住 Ctrl 面板不变，和别的物品一样按 Ctrl。指南自己的界面不走这里（它的提示框是自己画的）。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GuideLookup {

    /** 按住多久打开（毫秒） */
    public static final long HOLD_MS = 700L;
    /** 超过这么久没画同一个物品的提示，就当鼠标移开了，重新计时 */
    private static final long GAP_MS = 150L;

    @Nullable
    private static Item holdItem;
    private static long holdStart;
    private static long lastSeen;
    private static boolean armed = true;

    /** 「Ctrl 是不是被武器面板占着」的单条缓存：提示框每帧重建，同一个物品只算一次（要反序列化模组卡） */
    @Nullable
    private static ItemStack panelStack;
    @Nullable
    private static CompoundTag panelTag;
    private static boolean panelTaken;

    private GuideLookup() {
    }

    /**
     * 提示框里的那一行：行首一个进度圈，后面是说明
     *
     * @param progress  0~1，按住的进度
     * @param withShift 要按 Ctrl + Shift（装了模组卡的武器），否则只按 Ctrl
     */
    public record Hint(float progress, boolean withShift) implements TooltipComponent {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuideLookupGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (stack.isEmpty() || minecraft.player == null || screen == null || screen instanceof GuideScreen) {
            return;
        }
        if (!hasBook(minecraft.player)) {
            return;
        }
        GuideView.Target target = GuideView.current().find(stack.getItem());
        if (target == null) {
            return;
        }
        boolean withShift = ctrlTakenByPanel(stack);
        try {
            event.getTooltipElements().add(Either.right(new Hint(progress(minecraft, screen, stack, target, withShift), withShift)));
        } catch (UnsupportedOperationException e) {
            // 别的模组用 renderComponentTooltipFromElements 传了个不可变列表进来：这个提示框就不加这一行
        }
    }

    /** 更新按住的计时，到点就排一次打开；返回当前进度 */
    private static float progress(Minecraft minecraft, Screen screen, ItemStack stack, GuideView.Target target, boolean withShift) {
        long now = Util.getMillis();
        if (!Screen.hasControlDown()) {
            armed = true;
            holdStart = 0L;
            return 0F;
        }
        if (!armed || withShift && !Screen.hasShiftDown()) {
            // 要 Ctrl + Shift 的物品只按了 Ctrl：这是在看面板的来源分解视图，不计时；补按 Shift 时从头算
            holdStart = 0L;
            return 0F;
        }
        if (holdStart == 0L || holdItem != stack.getItem() || now - lastSeen > GAP_MS) {
            holdStart = now;
            holdItem = stack.getItem();
        }
        lastSeen = now;
        float progress = (now - holdStart) / (float) HOLD_MS;
        if (progress < 1F) {
            return progress;
        }
        armed = false;
        holdStart = 0L;
        String key = target.entryKey();
        int block = skipComm(key, target.block());
        minecraft.tell(() -> {
            if (minecraft.screen == screen) {
                GuideSfx.lookup();
                GuideScreenOpener.openAt(screen, key, block);
            }
        });
        return 1F;
    }

    /**
     * 松开 Ctrl 就重新允许触发。每 tick 查一次，不放在提示框里查：打开指南后提示框不画了（指南里、空白处、书里没有的物品上都不画），
     * 只在那里查的话，在这些地方松开的 Ctrl 都看不见，下一次查阅得先在提示框上按一下、松开、再按住
     */
    @SubscribeEvent
    public static void onGuideLookupClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !armed && !Screen.hasControlDown()) {
            armed = true;
            holdStart = 0L;
        }
    }

    /**
     * 查阅的落点不停在通讯框上：通讯只是氛围，查阅是来找讲这件东西的那一块的。落到通讯时往后挪到第一个不是通讯的块
     *
     * @param key   条目
     * @param block 可见块序号；-1 = 篇首
     * @return 挪过之后的块序号
     */
    private static int skipComm(String key, int block) {
        if (block < 0) {
            return block;
        }
        GuideView.EntryView ev = GuideView.current().entries.get(key);
        if (ev == null) {
            return block;
        }
        List<GuideContent.Block> blocks = ev.blocks();
        int b = block;
        while (b < blocks.size() && blocks.get(b).type == GuideContent.BlockType.COMM) {
            b++;
        }
        return b < blocks.size() ? b : block;
    }

    /** 身上（背包、快捷栏、副手）有没有冒险指南 */
    private static boolean hasBook(LocalPlayer player) {
        Item book = KuvaLichItems.KUVALICH_GUIDE.get();
        for (ItemStack s : player.getInventory().items) {
            if (s.is(book)) {
                return true;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(book)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这件物品的提示框里，Ctrl 已经被本模组的武器面板占着（装了模组卡的武器：按住 Ctrl 切到来源分解视图），查阅要改按 Ctrl + Shift
     */
    private static boolean ctrlTakenByPanel(ItemStack stack) {
        if (!WeaponModuleHandler.hasBase(stack) || !TooltipConfig.PANEL.enabled.get()) {
            return false;
        }
        if (panelStack != stack || panelTag != stack.getTag()) {
            panelStack = stack;
            panelTag = stack.getTag();
            panelTaken = !WeaponModuleHandler.getModules(stack).isEmpty();
        }
        return panelTaken;
    }

    /** {@link Hint} 的绘制器登记（模组总线） */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        public static void onRegisterGuideLookupTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(Hint.class, HintRenderer::new);
        }
    }

    /**
     * 提示框里的那一行：行首一个 7×7 的像素小圆圈（16 格，从正上方顺时针按进度点亮猩红），后面是说明文字
     */
    public static final class HintRenderer implements ClientTooltipComponent {

        /** 进度圈占的宽度（含右边空隙） */
        private static final int RING_W = 11;
        /** 圆圈上的 16 个像素，从正上方起顺时针（7×7 格子里的坐标） */
        private static final int[][] RING = {
                {3, 0}, {4, 0}, {5, 1}, {6, 2}, {6, 3}, {6, 4}, {5, 5}, {4, 6},
                {3, 6}, {2, 6}, {1, 5}, {0, 4}, {0, 3}, {0, 2}, {1, 1}, {2, 0}
        };
        /** 没按住时的字色：次要字（原版提示框的深紫底上 6.7:1）；按住时转科技青 */
        private static final int IDLE_TEXT = GuideTheme.ASH;
        private static final int ACTIVE_TEXT = GuideTheme.TECH;
        private static final int TRACK = GuideTheme.withAlpha(GuideTheme.ASH, 0x70);

        private final float progress;
        private final FormattedCharSequence text;

        public HintRenderer(Hint hint) {
            this.progress = hint.progress();
            String ctrl = Minecraft.ON_OSX ? "Cmd" : "Ctrl";
            Component keys = hint.withShift()
                    ? Component.translatable("kuvalich.guide.lookup.chord", ctrl, "Shift")
                    : Component.literal(ctrl);
            this.text = Component.translatable("kuvalich.guide.lookup.hold", keys).getVisualOrderText();
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return RING_W + font.width(text);
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
            int color = progress > 0F ? ACTIVE_TEXT : IDLE_TEXT;
            font.drawInBatch(text, x + RING_W, y, color, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int lit = Math.round(RING.length * Math.min(1F, progress));
            int ox = x + 1;
            int oy = y;
            for (int i = 0; i < RING.length; i++) {
                int px = ox + RING[i][0];
                int py = oy + RING[i][1];
                graphics.fill(px, py, px + 1, py + 1, i < lit ? GuideTheme.KUVA : TRACK);
            }
            if (lit >= RING.length) {
                graphics.fill(ox + 2, oy + 2, ox + 5, oy + 5, GuideTheme.KUVA);
            }
        }
    }
}
