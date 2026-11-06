package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.RivenCyclePacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 安魂之融的裂罅循环（「卷宗对照」版）：页面不换，炉膛下面一排控件，右栏逐条对照新旧词条
 *
 * <ul>
 *   <li>炉膛下：[−] ×N [+]（1~10，滚轮也能调）、「循环」；洗出预览后变成「再循环」和「换上」，下面一行写这次要扣多少赤毒、
 *       背包要补多少。洗 1 张照旧出在结果格里，拿走也算换上；「换上」= 预览飞进物品格、原卡碎掉，卡留在台上接着洗；</li>
 *   <li>洗多张：背包淡出，候选小格（30×44）一张接一张从炉膛飞出来，落位时紫框故障跳一下、底下一颗颗色点亮起
 *       （一条词条一颗：绿 = 比原卡好，红 = 比原卡差或是负面，紫 = 原卡没有的）。鼠标停一格右栏就对照那一格，
 *       点一下选中（金框），按「换上第 N 张」：选中那张收成图标飞进物品格，原卡碎掉，其余几张按离它的远近依次碎掉往下掉，
 *       背包淡回来。「都不要」= 全碎掉、原卡留着；</li>
 *   <li>右栏：没有预览时列原卡的词条、循环次数和下一次的价格；有预览 / 停在候选上时每条词条两行（名字 + 涨跌，「旧 → 新」）。</li>
 * </ul>
 *
 * <p>以服务端为准：候选、价格、扣赤毒都在 {@link RequiemEvolveMenu} 里办，这里只发「循环 / 挑选 / 都不要 / 换上」请求（带状态序号），
 * 等状态包回来才算数。挑选、都不要、换上的演出点下去就先放（不等来回），服务端拒了（序号对不上、东西变了）就收回演出、照状态包重画。
 * 碎片每帧按格子下标算哈希摆位置，不进 {@code GuiFx} 的元素表；飞行、碎片、故障框都画在物品图标上面那一层（z = 300），
 * 时间用 {@code KuvaFxDirector} 的时钟，坐标按界面像素取整。各段动画的毫秒数写在下面的常量里。</p>
 */
@OnlyIn(Dist.CLIENT)
final class RivenCycleView {

    static final int RIVEN = 0xC864FF;
    private static final int RIVEN_INK = 0x150820;
    private static final int MAGENTA = 0xFF4FD8;
    private static final int CYAN = 0x4FE8FF;

    /** 自己的事件：这一次扣了多少赤毒（数量放在物品数里） */
    static final int EV_PAID = 303;

    private static final int DEAL_STEP = 60;
    private static final int DEAL_FLY = 240;
    private static final int DEAL_SETTLE = 300;
    private static final int PICK_FLY = 300;
    private static final int PICK_STEP = 30;
    private static final int SWAP_FLY = 300;
    private static final int SWAP_MS = 520;
    private static final int TILE_W = 30;
    private static final int TILE_GAP = 6;
    private static final int BTN_H = 14;
    /** 发出请求后最多等多久状态包（等的时候按钮不收） */
    private static final int WAIT_MS = 2000;

    private static final int IN = ForgeOpClassifier.EVOLVE_INPUT;
    private static final int MAT = ForgeOpClassifier.EVOLVE_MATERIAL;
    private static final int RES = ForgeOpClassifier.EVOLVE_RESULT;

    private static final int A_DEAL = 1;
    private static final int A_PICK = 2;
    private static final int A_DISMISS = 3;
    private static final int A_SWAP = 4;

    /** 一段演出：发牌 / 挑选 / 都不要 / 换上，以及那一刻的候选、选中哪张、原卡、飞的那张 */
    private static final class Anim {
        final int type;
        long t0;
        final int dur;
        final List<ItemStack> cands;
        final List<List<RivenCompare.Row>> rows;
        final int sel;
        final ItemStack old;
        final ItemStack card;

        Anim(int type, long t0, int dur, List<ItemStack> cands, List<List<RivenCompare.Row>> rows, int sel,
             ItemStack old, ItemStack card) {
            this.type = type;
            this.t0 = t0;
            this.dur = dur;
            this.cands = cands;
            this.rows = rows;
            this.sel = sel;
            this.old = old;
            this.card = card;
        }

        boolean blocking() {
            return type != A_DEAL;
        }
    }

    /** 一个界面打开期间记住张数，下次打开接着用 */
    private static int rememberedCount = 1;

    private final RequiemEvolveScreen s;
    private final RequiemEvolveMenu menu;

    private int count = rememberedCount;
    private int sel = -1;
    private int hover = -1;
    @Nullable
    private Anim anim;
    /** 画出来的候选（点了挑选 / 都不要以后先清掉，不等服务端） */
    private List<ItemStack> cands = List.of();
    private List<List<RivenCompare.Row>> candRows = List.of();
    private int seenVersion;
    /** 发出去还没回的请求带的序号（−1 = 没有） */
    private int sentSerial = -1;
    private long sentAt;
    private String pressId = "";
    private long pressAt;

    /** 预览对照的缓存：按两张卡的对象认（槽位里的物品一变就是新对象） */
    @Nullable
    private ItemStack cacheOrig;
    @Nullable
    private ItemStack cacheCard;
    private List<RivenCompare.Row> cacheRows = List.of();
    @Nullable
    private ItemStack attrsFor;
    private List<RivenCompare.Attr> attrsCache = List.of();

    /** 版式（RequiemEvolveScreen#layout 给） */
    private int inX;
    private int matX;
    private int outX;
    private int ocy;
    private int statusY;
    private int arc;

    RivenCycleView(RequiemEvolveScreen screen, RequiemEvolveMenu menu) {
        this.s = screen;
        this.menu = menu;
        this.seenVersion = menu.clientRivenVersion();
    }

    void layout(int inX, int matX, int outX, int ocy, int statusY, int arc) {
        this.inX = inX;
        this.matX = matX;
        this.outX = outX;
        this.ocy = ocy;
        this.statusY = statusY;
        this.arc = arc;
    }

    // ==================== 状态 ====================

    private static boolean isRiven(ItemStack st) {
        Item it = st.getItem();
        return it instanceof WeaponRivenModule || it instanceof WarframeRivenModule;
    }

    private ItemStack in() {
        return menu.getSlot(IN).getItem();
    }

    private ItemStack mat() {
        return menu.getSlot(MAT).getItem();
    }

    private ItemStack res() {
        return menu.getSlot(RES).getItem();
    }

    private boolean running(int type, long now) {
        return anim != null && anim.type == type && now - anim.t0 < anim.dur;
    }

    private boolean busy(long now) {
        return anim != null && anim.blocking() && now - anim.t0 < anim.dur;
    }

    private boolean waiting(long now) {
        return sentSerial >= 0 && now - sentAt < WAIT_MS;
    }

    private boolean spread() {
        return !cands.isEmpty();
    }

    /** 结果格里是一张付过赤毒的预览（裂罅 + 赤毒现在只有按「循环」才会出结果） */
    boolean preview() {
        return !spread() && isRiven(res()) && isRiven(in());
    }

    /** 这一页现在按裂罅循环画 */
    boolean active(long now) {
        if (menu.isPickingRiven() || spread() || busy(now)) {
            return true;
        }
        if (!RequiemEvolveMenu.isCyclableRiven(in())) {
            return false;
        }
        ItemStack m = mat();
        return m.isEmpty() || m.getItem() instanceof Kuva || preview();
    }

    /** 每帧开头：收状态包、收演出、定哪些格子这一帧不让原版画 */
    void frame(long now) {
        sync(now);
        if (anim != null && now - anim.t0 >= anim.dur && !holding(now)) {
            anim = null;
        }
        boolean on = active(now);
        long t = anim == null ? Long.MAX_VALUE / 4 : now - anim.t0;
        menu.clientHideInventory = on && inventoryAlpha(now) < 0.5F;
        // 换上 / 挑选：飞的那张落下来之前物品格里的东西不画（原卡在碎）；落下了服务端还没回，就一直画落下的那张
        menu.clientHideInput = anim != null && ((anim.type == A_PICK && (t < PICK_FLY || holding(now)))
                || (anim.type == A_SWAP && (t < SWAP_FLY || holding(now))));
        menu.clientHideResult = anim != null && anim.type == A_SWAP && t < anim.dur && !res().isEmpty();
    }

    /** 挑选 / 换上的演出放完了，但服务端还没回：物品格先画飞过去的那张 */
    private boolean holding(long now) {
        return anim != null && (anim.type == A_PICK || anim.type == A_SWAP) && waiting(now);
    }

    /** 收新的状态包 */
    private void sync(long now) {
        int v = menu.clientRivenVersion();
        if (v == seenVersion) {
            return;
        }
        seenVersion = v;
        boolean rejected = sentSerial >= 0 && menu.rivenSerial() == sentSerial;
        sentSerial = -1;
        byte reason = menu.clientRivenReason();
        List<ItemStack> fresh = menu.rivenCandidates();
        if (reason == RequiemEvolveMenu.RIVEN_OK && menu.clientRivenRolled() > 1 && !fresh.isEmpty()) {
            setCands(fresh);
            sel = -1;
            anim = new Anim(A_DEAL, now, dealMs(fresh.size()), List.of(), List.of(), -1, ItemStack.EMPTY, ItemStack.EMPTY);
            KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.FORGE_RIVEN_CYCLE.get(), 0.8F, 1.15F));
        } else if (fresh.isEmpty()) {
            setCands(List.of());
            sel = -1;
        } else {
            // 还在挑（挑选 / 都不要被拒了）：照服务端那份重新摆出来
            setCands(fresh);
            if (sel >= fresh.size()) {
                sel = -1;
            }
            if (anim != null && anim.blocking()) {
                anim = null;
            }
        }
        if (rejected && anim != null && anim.blocking()) {
            anim = null;
        }
        if (reason == RequiemEvolveMenu.RIVEN_OK && menu.clientRivenRolled() == 1) {
            s.rivenRolled();
        }
        int paid = menu.clientRivenPaid();
        if (paid > 0) {
            s.event(ArchivePageScreen.Ev.CUSTOM, EV_PAID, new ItemStack(KuvaLichItems.KUVA.get(), paid), 0L);
        }
        String why = switch (reason) {
            case RequiemEvolveMenu.RIVEN_KUVA_SHORT -> "kuvalich.evolve.riven.msg.short";
            case RequiemEvolveMenu.RIVEN_DISABLED -> "kuvalich.evolve.riven.msg.disabled";
            case RequiemEvolveMenu.RIVEN_INVALID -> "kuvalich.evolve.riven.msg.invalid";
            default -> null;
        };
        if (why != null) {
            s.message(Component.translatable(why), FxPalette.KUVA, 1800);
        }
    }

    private void setCands(List<ItemStack> list) {
        List<ItemStack> copy = new ArrayList<>(list.size());
        for (ItemStack st : list) {
            copy.add(st.copy());
        }
        cands = copy;
        List<RivenCompare.Attr> o = origAttrs(in());
        List<List<RivenCompare.Row>> rows = new ArrayList<>(copy.size());
        for (ItemStack st : copy) {
            rows.add(RivenCompare.compare(o, RivenCompare.attrs(st)));
        }
        candRows = rows;
    }

    private List<RivenCompare.Attr> origAttrs(ItemStack orig) {
        if (orig != attrsFor) {
            attrsFor = orig;
            attrsCache = RivenCompare.attrs(orig);
        }
        return attrsCache;
    }

    private List<RivenCompare.Row> previewRows(ItemStack orig, ItemStack card) {
        if (orig != cacheOrig || card != cacheCard) {
            cacheOrig = orig;
            cacheCard = card;
            cacheRows = RivenCompare.compare(RivenCompare.attrs(orig), RivenCompare.attrs(card));
        }
        return cacheRows;
    }

    // ==================== 请求 ====================

    private void send(long now, byte action, int arg) {
        sentSerial = menu.rivenSerial();
        sentAt = now;
        NetworkRegistryHandler.getChannel().sendToServer(new RivenCyclePacket(menu.containerId, action, sentSerial, arg));
    }

    private int price(int n) {
        return RequiemEvolveMenu.rivenCyclePrice(in(), n);
    }

    /** 背包 36 格里和材料格同一种的赤毒 */
    private int inventoryKuva(ItemStack like) {
        int n = 0;
        for (Slot sl : menu.slots) {
            if (sl.container instanceof Inventory && sl.getContainerSlot() < 36) {
                ItemStack st = sl.getItem();
                if (!st.isEmpty() && ItemStack.isSameItemSameTags(st, like)) {
                    n += st.getCount();
                }
            }
        }
        return n;
    }

    private boolean kuvaIn() {
        ItemStack m = mat();
        return !m.isEmpty() && m.getItem() instanceof Kuva;
    }

    private boolean canRoll(long now) {
        if (spread() || busy(now) || waiting(now) || menu.isPickingRiven() || !kuvaIn()
                || !RequiemEvolveMenu.isCyclableRiven(in())) {
            return false;
        }
        ItemStack m = mat();
        return m.getCount() + inventoryKuva(m) >= price(count);
    }

    private void roll(long now) {
        if (!canRoll(now)) {
            return;
        }
        rememberedCount = count;
        send(now, RivenCyclePacket.ROLL, count);
    }

    private void swap(long now) {
        if (!preview() || busy(now) || waiting(now)) {
            return;
        }
        anim = new Anim(A_SWAP, now, SWAP_MS, List.of(), List.of(), -1, in().copy(), res().copy());
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.FORGE_RIVEN_CYCLE.get(), 0.8F, 0.9F),
                Math.round(SWAP_FLY / 50.0F));
        send(now, RivenCyclePacket.SWAP, 0);
    }

    private void select(int i, long now) {
        if (!spread() || busy(now)) {
            return;
        }
        if (running(A_DEAL, now)) {
            anim.t0 = now - anim.dur;
        }
        sel = sel == i ? -1 : i;
    }

    private void pick(long now) {
        if (!spread() || sel < 0 || sel >= cands.size() || busy(now) || waiting(now)) {
            return;
        }
        int n = cands.size();
        anim = new Anim(A_PICK, now, Math.max(PICK_FLY + 400, 40 + (n - 1) * PICK_STEP + 320 + 220), cands, candRows,
                sel, in().copy(), cands.get(sel));
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.FORGE_RIVEN_CYCLE.get(), 0.85F, 0.9F),
                Math.round(PICK_FLY / 50.0F));
        int idx = sel;
        cands = List.of();
        candRows = List.of();
        sel = -1;
        send(now, RivenCyclePacket.PICK, idx);
    }

    private void dismiss(long now) {
        if (!spread() || busy(now) || waiting(now)) {
            return;
        }
        int n = cands.size();
        anim = new Anim(A_DISMISS, now, 40 + (n - 1) * PICK_STEP + 320 + 220, cands, candRows, -1, in().copy(),
                ItemStack.EMPTY);
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.FORGE_MODULE_REMOVE.get(), 0.8F, 0.8F));
        cands = List.of();
        candRows = List.of();
        sel = -1;
        send(now, RivenCyclePacket.DISMISS, 0);
    }

    /** 滚轮调张数（挑选中、演出中不收）；吃掉了返回 true */
    boolean scroll(double mx, double my, double delta, long now) {
        if (!active(now) || spread() || busy(now) || menu.isPickingRiven() || mx < s.sepL || mx > s.sepR) {
            return false;
        }
        int n = Mth.clamp(count + (delta > 0 ? 1 : -1), 1, RequiemEvolveMenu.RIVEN_MAX_ROLL);
        if (n != count) {
            count = n;
            rememberedCount = n;
        }
        return true;
    }

    /** 挑选中点物品格、材料格：不发点击，格子红一下 */
    boolean blocks(@Nullable Slot slot) {
        if (slot == null || !menu.isPickingRiven() || (slot.index != IN && slot.index != MAT)) {
            return false;
        }
        s.event(ArchivePageScreen.Ev.REJECT, slot.index, slot.getItem().copy(), 0L);
        return true;
    }

    /** 多张洗铺开时背包淡出，挑完 / 都不要的演出最后 220 ms 淡回来 */
    float inventoryAlpha(long now) {
        if (!active(now)) {
            return 1.0F;
        }
        long t = anim == null ? Long.MAX_VALUE / 4 : now - anim.t0;
        if (spread()) {
            return anim != null && anim.type == A_DEAL ? 1.0F - seg(t, 0, 160) : 0.0F;
        }
        if (anim != null && (anim.type == A_PICK || anim.type == A_DISMISS) && t < anim.dur) {
            return seg(t, anim.dur - 220, anim.dur);
        }
        return 1.0F;
    }

    private static int dealMs(int n) {
        return DEAL_STEP * (n - 1) + DEAL_FLY + DEAL_SETTLE;
    }

    // ==================== 中栏 ====================

    /** 炉膛下面的控件（替掉状态行）、铺开的候选和留在页面这一层的演出 */
    void drawMiddle(GuiGraphics g, int mx, int my, long now, float eF) {
        hover = -1;
        boolean spreadUi = spread() || running(A_PICK, now) || running(A_DISMISS, now);
        if (spreadUi) {
            drawSpreadActions(g, mx, my, now, eF);
        } else {
            drawControls(g, mx, my, now, eF);
        }
        drawTiles(g, mx, my, now);
        drawAnimPage(g, now);
    }

    private void drawControls(GuiGraphics g, int mx, int my, long now, float eF) {
        boolean pv = preview();
        int y = statusY - 3;
        String main = pv ? (count > 1 ? tr("kuvalich.evolve.riven.reroll_n", count) : tr("kuvalich.evolve.riven.reroll"))
                : (count > 1 ? tr("kuvalich.evolve.riven.roll_n", count) : tr("kuvalich.evolve.riven.roll"));
        String swapL = tr("kuvalich.evolve.riven.swap");
        // 主按钮按四种写法里最宽的那个定宽：张数一变字就变宽变窄，整行跟着重新居中，连点「+」「−」会点空
        int mw = mainWidth();
        int sw = 46;
        int swapW = pv ? ink(swapL) + 12 : 0;
        int total = sw + 6 + mw + (pv ? 6 + swapW : 0);
        int x = s.cx - total / 2;
        boolean dis = busy(now) || waiting(now) || menu.isPickingRiven();
        stepper(g, x, y, mx, my, now, dis, eF);
        x += sw + 6;
        int n = count;
        button(g, "roll", x, y, mw, main, true, RIVEN, canRoll(now), mx, my, now, eF, () -> roll(now),
                () -> tips(pv ? "kuvalich.evolve.riven.tip.reroll" : "kuvalich.evolve.riven.tip.roll",
                        Component.translatable("kuvalich.evolve.riven.tip.price", n),
                        Component.translatable("kuvalich.evolve.riven.tip.keep")));
        x += mw + 6;
        if (pv) {
            button(g, "swap", x, y, swapW, swapL, false, FxPalette.KUVA, !dis, mx, my, now, eF, () -> swap(now),
                    () -> tips("kuvalich.evolve.riven.tip.swap",
                            Component.translatable("kuvalich.evolve.riven.tip.swap2")));
        }
        if (!s.drawMessage(g, s.cx, y + 17, eF)) {
            drawPrice(g, y + 17, eF);
        }
    }

    private int mainWidth() {
        int n = RequiemEvolveMenu.RIVEN_MAX_ROLL;
        int w = Math.max(Math.max(ink(tr("kuvalich.evolve.riven.roll")), ink(tr("kuvalich.evolve.riven.roll_n", n))),
                Math.max(ink(tr("kuvalich.evolve.riven.reroll")), ink(tr("kuvalich.evolve.riven.reroll_n", n))));
        return w + 12;
    }

    /** 价格一行：材料格够就写总价，不够写背包补多少，总数都不够写差多少 */
    private void drawPrice(GuiGraphics g, int y, float eF) {
        ItemStack m = mat();
        if (!kuvaIn()) {
            s.textC(g, tr("kuvalich.evolve.riven.need_kuva"), s.cx, y, FxPalette.ASH, 0.85F * eF);
            return;
        }
        if (!RequiemEvolveMenu.isCyclableRiven(in())) {
            return;
        }
        int p = price(count);
        int slot = m.getCount();
        int total = slot + inventoryKuva(m);
        String text;
        int rgb = FxPalette.ASH;
        if (total < p) {
            text = tr("kuvalich.evolve.riven.short", p, total);
            rgb = KuvaPalette.DANGER;
        } else if (slot >= p) {
            text = tr("kuvalich.evolve.riven.price", p);
        } else {
            text = tr("kuvalich.evolve.riven.price_refill", p, p - slot);
        }
        s.textC(g, text, s.cx, y, rgb, 0.85F * eF);
    }

    /** [−] ×N [+]：三块拼在一起，中间写张数 */
    private void stepper(GuiGraphics g, int x, int y, int mx, int my, long now, boolean dis, float eF) {
        button(g, "minus", x, y, 12, "-", false, FxPalette.KUVA, !dis && count > 1, mx, my, now, eF,
                () -> setCount(count - 1), null);
        ArchivePageScreen.fill(g, x + 12, y, x + 34, y + BTN_H, ArchivePageScreen.INK, 0.85F * eF);
        box(g, x + 11, y, 24, BTN_H, RIVEN, 0.5F * eF);
        String c = "×" + count;
        int tx = x + 12 + (22 - ink(c)) / 2;
        s.text(g, c, tx, y + (BTN_H - 8) / 2 + 1, FxPalette.BONE, eF);
        s.hot(x + 12, y, x + 34, y + BTN_H, () -> tips("kuvalich.evolve.riven.tip.count",
                Component.translatable("kuvalich.evolve.riven.tip.count2")), null);
        button(g, "plus", x + 34, y, 12, "+", false, FxPalette.KUVA, !dis && count < RequiemEvolveMenu.RIVEN_MAX_ROLL,
                mx, my, now, eF, () -> setCount(count + 1), null);
    }

    private void setCount(int n) {
        count = Mth.clamp(n, 1, RequiemEvolveMenu.RIVEN_MAX_ROLL);
        rememberedCount = count;
    }

    private void drawSpreadActions(GuiGraphics g, int mx, int my, long now, float eF) {
        int y = s.sepB - 15;
        int shownSel = anim != null && anim.type == A_PICK && now - anim.t0 < anim.dur ? anim.sel : sel;
        String pickL = shownSel >= 0 ? tr("kuvalich.evolve.riven.pick_n", shownSel + 1) : tr("kuvalich.evolve.riven.pick");
        String dropL = tr("kuvalich.evolve.riven.dismiss");
        int w1 = ink(pickL) + 12;
        int w2 = ink(dropL) + 12;
        int x = s.cx - (w1 + 6 + w2) / 2;
        boolean live = spread() && !busy(now) && !waiting(now);
        boolean any = sel >= 0;
        button(g, "pick", x, y, w1, pickL, true, RIVEN, live && any, mx, my, now, eF, () -> pick(now),
                () -> any ? tips("kuvalich.evolve.riven.tip.pick", Component.translatable("kuvalich.evolve.riven.tip.pick2"))
                        : tips("kuvalich.evolve.riven.tip.pick_none"));
        button(g, "drop", x + w1 + 6, y, w2, dropL, false, FxPalette.KUVA, live, mx, my, now, eF, () -> dismiss(now),
                () -> tips("kuvalich.evolve.riven.tip.dismiss", Component.translatable("kuvalich.evolve.riven.tip.dismiss2")));
    }

    /** 候选小格的位置：按行居中，一行最多 5 个；整块在背包让出来的区域里竖直居中。返回 {x, y} 各一组 */
    private int[][] tiles(int n) {
        int actY = s.sepB - 15;
        int top = ocy + arc + 6;
        int bottom = actY - 6;
        int th = tileH();
        int rows = n <= 5 ? 1 : 2;
        int first = rows == 1 ? n : (n + 1) / 2;
        int[] per = {first, n - first};
        int blockH = rows * th + (rows - 1) * TILE_GAP;
        int y0 = top + Math.floorDiv(bottom - top - blockH, 2);
        int[] xs = new int[n];
        int[] ys = new int[n];
        int i = 0;
        for (int r = 0; r < rows; r++) {
            int m = per[r];
            int rowW = m * TILE_W + (m - 1) * TILE_GAP;
            int x0 = s.cx - rowW / 2;
            for (int c = 0; c < m; c++, i++) {
                xs[i] = x0 + c * (TILE_W + TILE_GAP);
                ys[i] = y0 + r * (th + TILE_GAP);
            }
        }
        return new int[][]{xs, ys};
    }

    private int tileH() {
        return s.small ? 40 : 44;
    }

    private void drawTiles(GuiGraphics g, int mx, int my, long now) {
        if (!spread()) {
            return;
        }
        int n = cands.size();
        int[][] lay = tiles(n);
        int th = tileH();
        boolean dealing = running(A_DEAL, now);
        long t = anim == null ? Long.MAX_VALUE / 4 : now - anim.t0;
        if (dealing) {
            // 发牌中点哪儿都先跳到发完（点在小格上的照样选中）
            s.hot(s.sepL + 1, ocy + arc + 2, s.sepR - 1, s.sepB, null, () -> {
                if (anim != null && anim.type == A_DEAL) {
                    anim.t0 = now - anim.dur;
                }
            });
        }
        for (int i = 0; i < n; i++) {
            long age = dealing ? t - (long) i * DEAL_STEP : Long.MAX_VALUE / 4;
            if (age < DEAL_FLY) {
                continue;
            }
            int x = lay[0][i];
            int y = lay[1][i];
            boolean hv = mx >= x && mx < x + TILE_W && my >= y && my < y + th;
            if (hv) {
                hover = i;
            }
            drawTile(g, x, y, th, cands.get(i), candRows.get(i), i, 1.0F, hv, sel == i, age);
            int idx = i;
            s.hot(x, y, x + TILE_W, y + th, null, () -> select(idx, now));
        }
    }

    /** 一张候选小格：墨紫底、细框、顶上一道紫，图标，一条词条一颗色点，底下编号 */
    private void drawTile(GuiGraphics g, int x, int y, int h, ItemStack card, List<RivenCompare.Row> rows, int i,
                          float a, boolean hv, boolean picked, long age) {
        int w = TILE_W;
        ArchivePageScreen.fill(g, x, y, x + w, y + h, RIVEN_INK, 0.92F * a);
        if (picked) {
            ArchivePageScreen.fill(g, x, y, x + w, y + h, FxPalette.ACCENT, 0.1F * a);
        } else if (hv) {
            ArchivePageScreen.fill(g, x, y, x + w, y + h, RIVEN, 0.12F * a);
        }
        box(g, x, y, w, h, picked ? FxPalette.ACCENT : RIVEN, (picked ? 1.0F : hv ? 0.95F : 0.55F) * a);
        if (picked) {
            box(g, x - 2, y - 2, w + 4, h + 4, FxPalette.ACCENT, 0.45F * a);
        }
        ArchivePageScreen.fill(g, x + 1, y, x + w - 1, y + 1, RIVEN, 0.9F * a);
        ArchivePageScreen.renderItemDim(g, card, x + (w - 16) / 2, y + 4, 1.0F, a);
        int k = 0;
        int shown = 0;
        for (RivenCompare.Row r : rows) {
            if (r.kind() != RivenCompare.LOST) {
                shown++;
            }
        }
        int pw = shown * 4 + Math.max(0, shown - 1) * 2;
        int px = x + (w - pw) / 2;
        for (RivenCompare.Row r : rows) {
            if (r.kind() == RivenCompare.LOST) {
                continue;
            }
            if (age - DEAL_FLY - k * 40L > 0) {
                ArchivePageScreen.fill(g, px, y + 24, px + 4, y + 28, RivenCompare.rgb(r), a);
            }
            px += 6;
            k++;
        }
        float show = clamp01((age - DEAL_FLY) / 120.0F);
        String num = String.valueOf(i + 1);
        s.text(g, num, x + (w - ink(num)) / 2, y + h - 12, picked ? FxPalette.ACCENT : FxPalette.ASH,
                a * (0.4F + 0.6F * show));
    }

    /** 留在页面这一层的演出：碎掉的候选、物品格里碎掉的原卡 */
    private void drawAnimPage(GuiGraphics g, long now) {
        Anim a = anim;
        if (a == null) {
            return;
        }
        long t = now - a.t0;
        if (t >= a.dur) {
            return;
        }
        int ix = inX - 8;
        int iy = ocy - 8;
        if (a.type == A_PICK || a.type == A_DISMISS) {
            int n = a.cands.size();
            int[][] lay = tiles(n);
            int th = tileH();
            for (int i = 0; i < n; i++) {
                if (a.type == A_PICK && i == a.sel) {
                    continue;
                }
                int order = a.type == A_PICK ? Math.abs(i - a.sel) : i;
                long st = 40L + (long) order * PICK_STEP;
                float u = seg(t, st, st + 320);
                int x = lay[0][i];
                int y = lay[1][i];
                ItemStack card = a.cands.get(i);
                List<RivenCompare.Row> rows = a.rows.get(i);
                int idx = i;
                dissolve(g, x, y, TILE_W, th, u, RIVEN, 101 + i * 7,
                        al -> drawTile(g, x, y, th, card, rows, idx, al, false, false, Long.MAX_VALUE / 4));
            }
            if (a.type == A_PICK) {
                dissolve(g, ix, iy, 16, 16, seg(t, 0, 300), FxPalette.KUVA, 13,
                        al -> ArchivePageScreen.renderItemDim(g, a.old, ix, iy, 1.0F, al));
            }
        } else if (a.type == A_SWAP) {
            dissolve(g, ix, iy, 16, 16, t / 300.0F, FxPalette.KUVA, 11,
                    al -> ArchivePageScreen.renderItemDim(g, a.old, ix, iy, 1.0F, al));
        }
    }

    // ==================== 叠在物品上面的一层 ====================

    void drawOverlay(GuiGraphics g, long now) {
        // 挑选中：物品格、材料格上一层斜纹，表示拿不动
        if (menu.isPickingRiven() || spread()) {
            hatch(g, inX - 9, ocy - 9);
            hatch(g, matX - 9, ocy - 9);
        }
        ArchivePageScreen.Fx paid = s.last(ArchivePageScreen.Ev.CUSTOM, EV_PAID);
        if (paid != null) {
            long age = now - paid.t0();
            if (age < 800) {
                float a = age < 400 ? 1.0F : 1.0F - (age - 400) / 400.0F;
                s.textR(g, "-" + paid.stack().getCount(), matX - 3, ocy + 11, FxPalette.KUVA, a);
            }
        }
        Anim a = anim;
        if (a == null) {
            return;
        }
        long t = now - a.t0;
        int ix = inX - 8;
        int iy = ocy - 8;
        int ox = outX - 8;
        if (a.type == A_DEAL && t < a.dur && spread()) {
            int n = cands.size();
            int[][] lay = tiles(n);
            int th = tileH();
            for (int i = 0; i < n; i++) {
                long age = t - (long) i * DEAL_STEP;
                if (age < 0) {
                    continue;
                }
                int x = lay[0][i];
                int y = lay[1][i];
                if (age < DEAL_FLY) {
                    // 从炉膛飞出来：图标沿弧线飞，最后 40% 框从图标大小张开到格子大小
                    float u = age / (float) DEAL_FLY;
                    int[] p = flyPos(ox, iy, x + (TILE_W - 16) / 2, y + 4, u, 10);
                    float open = seg01(u, 0.6F, 1.0F);
                    if (open > 0.0F) {
                        int ww = Math.round(Mth.lerp(easeOut(open), 18, TILE_W));
                        int hh = Math.round(Mth.lerp(easeOut(open), 18, th));
                        box(g, x + (TILE_W - ww) / 2, y + (th - hh) / 2 - Math.round((1.0F - open) * 8), ww, hh, RIVEN, 0.7F);
                    }
                    ArchivePageScreen.renderItemDim(g, cands.get(i), p[0], p[1], 1.0F, 1.0F);
                    continue;
                }
                glitchFrame(g, x - 1, y - 1, TILE_W + 2, th + 2, age - DEAL_FLY, 300, RIVEN);
            }
            return;
        }
        if (a.type == A_PICK) {
            int[][] lay = tiles(a.cands.size());
            int th = tileH();
            int x = lay[0][a.sel];
            int y = lay[1][a.sel];
            float u = clamp01(t / (float) PICK_FLY);
            float fold = seg01(u, 0.0F, 0.35F);
            if (fold < 1.0F) {
                int ww = Math.round(Mth.lerp(easeOut(fold), TILE_W, 18));
                int hh = Math.round(Mth.lerp(easeOut(fold), th, 18));
                box(g, x + (TILE_W - ww) / 2, y + (th - hh) / 2, ww, hh, FxPalette.ACCENT, 0.9F * (1.0F - fold));
            }
            if (t < PICK_FLY) {
                int[] p = flyPos(x + (TILE_W - 16) / 2, y + 4, ix, iy, seg01(u, 0.2F, 1.0F), 16);
                ArchivePageScreen.renderItemDim(g, a.card, p[0], p[1], 1.0F, 1.0F);
            } else if (holding(now)) {
                ArchivePageScreen.renderItemDim(g, a.card, ix, iy, 1.0F, 1.0F);
            }
            glitchFrame(g, ix - 2, iy - 2, 20, 20, t - PICK_FLY, 400, RIVEN);
            ringFx(g, ix + 8, iy + 8, 9, 17, t - PICK_FLY, 320, RIVEN);
            return;
        }
        if (a.type == A_SWAP) {
            if (t < SWAP_FLY) {
                // 拖尾：前几帧的位置淡淡留一下
                for (int k = 3; k >= 1; k--) {
                    long tk = t - k * 16L;
                    if (tk > 0) {
                        int[] q = flyPos(ox, iy, ix, iy, tk / (float) SWAP_FLY, 14);
                        float al = 0.12F / k;
                        ArchivePageScreen.additive(g, () -> ArchivePageScreen.fill(g, q[0] + 2, q[1] + 2, q[0] + 14,
                                q[1] + 14, RIVEN, al));
                    }
                }
                int[] p = flyPos(ox, iy, ix, iy, t / (float) SWAP_FLY, 14);
                ArchivePageScreen.renderItemDim(g, a.card, p[0], p[1], 1.0F, 1.0F);
            } else if (holding(now)) {
                ArchivePageScreen.renderItemDim(g, a.card, ix, iy, 1.0F, 1.0F);
            }
            glitchFrame(g, ix - 2, iy - 2, 20, 20, t - SWAP_FLY, 400, RIVEN);
            ringFx(g, ix + 8, iy + 8, 9, 17, t - SWAP_FLY, 320, RIVEN);
        }
    }

    private static void hatch(GuiGraphics g, int x0, int y0) {
        for (int i = 0; i < 18; i += 3) {
            ArchivePageScreen.fill(g, x0 + i, y0 + 17 - i, x0 + i + 2, y0 + 18 - i, FxPalette.KUVA_PALE, 0.25F);
        }
    }

    // ==================== 右栏 ====================

    /** 右栏：没有预览时原卡的词条、循环次数、下一次的价格；有预览 / 停在候选上时逐条对照 */
    void drawRight(GuiGraphics g, long now, float eF, int maxY) {
        int x = s.rx0;
        int w = s.rx1 - s.rx0;
        int y = s.hairY + 8;
        ItemStack orig = in();
        if (anim != null && anim.type == A_PICK && now - anim.t0 < anim.dur) {
            // 点了换上就当已经换好（服务端回来之前物品格里可能还是原卡）
            orig = anim.card;
        }
        ItemStack shown = null;
        List<RivenCompare.Row> rows = null;
        String label = null;
        long age = Long.MAX_VALUE / 4;
        if (spread()) {
            int idx = hover >= 0 ? hover : sel;
            if (idx >= 0 && idx < cands.size()) {
                shown = cands.get(idx);
                rows = candRows.get(idx);
                label = tr("kuvalich.evolve.riven.label.cand", idx + 1, cands.size())
                        + (idx == sel ? " · " + tr("kuvalich.evolve.riven.label.picked") : "");
            }
        } else if (preview()) {
            shown = res();
            rows = previewRows(orig, shown);
            label = tr("kuvalich.evolve.op.riven") + " · " + tr("kuvalich.evolve.preview");
            age = s.resultShown() ? s.resultAge() : -1L;
        }
        if (shown == null) {
            if (spread()) {
                s.text(g, tr("kuvalich.evolve.riven.label.cands", cands.size()), x, y, RIVEN, eF);
                y += 12;
                List<ForgePageScreen.Line> hint = List.of(
                        new ForgePageScreen.Line(tr("kuvalich.evolve.riven.hint"), KuvaPalette.MUTED, 0.85F, 4));
                y = s.drawLines(g, hint, x, y, w, eF, maxY);
                y = legend(g, x, y, w, eF) + 4;
                if (y + 9 <= maxY) {
                    s.text(g, tr("kuvalich.evolve.riven.orig"), x, y, FxPalette.ASH, eF);
                }
                y += 10;
                attrList(g, origAttrs(orig), x, y, w, 0.85F * eF, maxY);
                return;
            }
            if (orig.isEmpty()) {
                return;
            }
            s.text(g, tr("kuvalich.evolve.op.riven"), x, y, RIVEN, eF);
            y += 12;
            s.text(g, s.fit(orig.getHoverName().getString(), w), x, y, FxPalette.BONE, eF);
            y += 12;
            y = attrList(g, origAttrs(orig), x, y, w, eF, maxY) + 3;
            List<ForgePageScreen.Line> lines = new ArrayList<>(2);
            int cyc = RequiemEvolveMenu.rivenCycleOf(orig);
            lines.add(new ForgePageScreen.Line(tr("kuvalich.evolve.riven.cycles", cyc), FxPalette.TEXT_MID, 0.9F, 2));
            lines.add(new ForgePageScreen.Line(tr("kuvalich.evolve.riven.next", RequiemEvolveMenu.rivenCycleCost(orig, cyc)),
                    KuvaPalette.MUTED, 0.85F, 0));
            s.drawLines(g, lines, x, y, w, eF, maxY);
            return;
        }
        s.text(g, s.fit(label, w), x, y, RIVEN, eF);
        y += 12;
        s.text(g, s.fit(ForgePageScreen.typed(shown.getHoverName().getString(), age), w), x, y, FxPalette.BONE, eF);
        y += 13;
        // 宽屏下对照行不拉满整栏（涨跌离词条名太远就对不上了），最宽 150
        int cw = Math.min(w, 150);
        for (int i = 0; i < rows.size(); i++) {
            if (y + 18 > maxY) {
                break;
            }
            y = compareRow(g, rows.get(i), x, y, cw, eF, age - i * 50L) + 3;
        }
        if (preview()) {
            List<ForgePageScreen.Line> lines = List.of(
                    new ForgePageScreen.Line(tr("kuvalich.evolve.riven.orig_cycles", RequiemEvolveMenu.rivenCycleOf(orig)),
                            KuvaPalette.MUTED, 0.85F, 2),
                    new ForgePageScreen.Line(tr("kuvalich.evolve.riven.keep_warn"), FxPalette.KUVA, 0.9F, 0));
            s.drawLines(g, lines, x, y + 1, w, eF, maxY);
        }
    }

    /** 小格上的色点说明 */
    private int legend(GuiGraphics g, int x, int y, int w, float a) {
        int[] rgbs = {KuvaPalette.SUCCESS, KuvaPalette.DANGER, RIVEN};
        String[] keys = {"kuvalich.evolve.riven.legend.up", "kuvalich.evolve.riven.legend.down",
                "kuvalich.evolve.riven.legend.new"};
        for (int i = 0; i < 3; i++) {
            ArchivePageScreen.fill(g, x, y + 2, x + 4, y + 6, rgbs[i], a);
            s.text(g, s.fit(tr(keys[i]), w - 7), x + 7, y, KuvaPalette.MUTED, 0.85F * a);
            y += 10;
        }
        return y;
    }

    /** 一张卡的词条：一行「+142% 暴击几率」，负面红 */
    private int attrList(GuiGraphics g, List<RivenCompare.Attr> attrs, int x, int y, int w, float a, int maxY) {
        for (RivenCompare.Attr at : attrs) {
            if (y + 9 > maxY) {
                break;
            }
            String v = RivenCompare.fmt(at.key(), at.value());
            int vx = Math.round(s.textW(v, 0.0F));
            s.text(g, v, x, y, at.value() < 0 ? KuvaPalette.DANGER : RIVEN, a);
            s.text(g, s.fit(at.name(), w - vx - 3), x + vx + 3, y, FxPalette.TEXT_MID, 0.9F * a);
            y += 10;
        }
        return y;
    }

    /**
     * 对照行（两行式，右栏窄也放得下）：第一行词条名 + 右端涨跌（▲ 绿 / ▼ 红 / 紫「新」/ 灰「失去」/「=」），
     * 第二行「旧 → 新」，新值按涨跌上色，失去的旧值划一道线。返回画完后的 y
     */
    private int compareRow(GuiGraphics g, RivenCompare.Row r, int x, int y, int w, float a, long age) {
        boolean lost = r.kind() == RivenCompare.LOST;
        String nm = ArchivePageScreen.scramble(r.name(), age, 30);
        s.text(g, s.fit(nm, w - 22), x, y, lost ? FxPalette.ASH : FxPalette.BONE, (lost ? 0.6F : 1.0F) * a);
        int right = x + w;
        if (r.kind() == RivenCompare.UP || r.kind() == RivenCompare.DOWN) {
            String d = RivenCompare.delta(r);
            int tw = ink(d);
            int col = r.kind() == RivenCompare.UP ? KuvaPalette.SUCCESS : KuvaPalette.DANGER;
            s.text(g, d, right - tw, y, col, a);
            tri(g, right - tw - 7, y + 2, r.kind() == RivenCompare.UP, col, a);
        } else {
            String tag = switch (r.kind()) {
                case RivenCompare.NEW -> tr("kuvalich.evolve.riven.new");
                case RivenCompare.LOST -> tr("kuvalich.evolve.riven.lost");
                default -> "=";
            };
            int col = r.kind() == RivenCompare.NEW ? RIVEN : FxPalette.ASH;
            float al = r.kind() == RivenCompare.LOST ? 0.7F : 1.0F;
            s.text(g, tag, right - ink(tag), y, col, al * a);
        }
        int y2 = y + 10;
        String ov = r.old() == null ? "—" : RivenCompare.fmt(r.key(), r.old());
        String nv = r.nw() == null ? "—" : RivenCompare.fmt(r.key(), r.nw());
        int ow = Math.round(s.textW(ov, 0.0F));
        s.text(g, ov, x, y2, FxPalette.ASH, (lost ? 0.6F : 0.75F) * a);
        arrowR(g, x + ow + 2, y2 + 2, FxPalette.ASH, 0.6F * a);
        int xx = x + ow + 12;
        s.text(g, ArchivePageScreen.scramble(nv, age - 60, 30), xx, y2, lost ? FxPalette.ASH : RivenCompare.rgb(r),
                (lost ? 0.5F : 1.0F) * a);
        if (lost) {
            ArchivePageScreen.hl(g, x, x + ow - 1, y2 + 4, FxPalette.ASH, 0.6F * a);
        }
        return y + 20;
    }

    /** 右栏底下的流程三步 */
    List<ForgePageScreen.Step> steps() {
        boolean picking = spread() || menu.isPickingRiven();
        boolean pv = preview();
        boolean hasIn = !in().isEmpty() || pv || picking;
        boolean kuva = kuvaIn();
        List<ForgePageScreen.Step> out = new ArrayList<>(3);
        out.add(new ForgePageScreen.Step(tr("kuvalich.evolve.step.item"),
                hasIn ? ForgePageScreen.STEP_DONE : ForgePageScreen.STEP_ACTIVE));
        out.add(new ForgePageScreen.Step(tr("kuvalich.evolve.step.material"),
                kuva ? ForgePageScreen.STEP_DONE : hasIn ? ForgePageScreen.STEP_ACTIVE : ForgePageScreen.STEP_PENDING));
        String third = picking ? "kuvalich.evolve.riven.step.pick" : pv ? "kuvalich.evolve.riven.step.swap"
                : "kuvalich.evolve.riven.step.roll";
        out.add(new ForgePageScreen.Step(tr(third), picking || pv || (hasIn && kuva)
                ? ForgePageScreen.STEP_ACTIVE : ForgePageScreen.STEP_PENDING));
        return out;
    }

    // ==================== 小部件 ====================

    /**
     * 一个按钮：墨底细框，悬停亮框 + 底色，刚按下白一下，不可点发灰。文字按字墨居中（奇数余量多出的 1 格放右边），
     * 西文比汉字矮 1 格、往下挪 1 格
     */
    private void button(GuiGraphics g, String id, int x, int y, int w, String label, boolean solid, int rgb, boolean on,
                        int mx, int my, long now, float eF, Runnable click,
                        @Nullable Supplier<List<Component>> tip) {
        int h = BTN_H;
        boolean hv = on && mx >= x && mx < x + w && my >= y && my < y + h;
        boolean pressed = id.equals(pressId) && now - pressAt < 120;
        ArchivePageScreen.fill(g, x, y, x + w, y + h, ArchivePageScreen.INK, 0.85F * eF);
        if (solid) {
            ArchivePageScreen.fill(g, x, y, x + w, y + h, rgb, (hv ? 0.32F : 0.2F) * (on ? 1.0F : 0.35F) * eF);
        } else if (hv) {
            ArchivePageScreen.fill(g, x, y, x + w, y + h, rgb, 0.14F * eF);
        }
        if (pressed) {
            ArchivePageScreen.fill(g, x, y, x + w, y + h, FxPalette.WHITE, 0.2F * eF);
        }
        int frame = on ? (hv ? FxCurve.lerpColor(rgb, FxPalette.WHITE, 0.35F) : rgb) : FxPalette.ASH;
        box(g, x, y, w, h, frame, (on ? (hv ? 1.0F : 0.75F) : 0.35F) * eF);
        int tx = x + (w - ink(label)) / 2;
        int ty = y + (h - 8) / 2 + (hasCjk(label) ? 0 : 1);
        s.text(g, label, tx, ty, on ? FxPalette.BONE : FxPalette.ASH, (on ? 1.0F : 0.55F) * eF);
        s.hot(x, y, x + w, y + h, tip, on ? () -> {
            pressId = id;
            pressAt = now;
            click.run();
        } : null);
    }

    /** 细线框画在盒子里面（x、y、w、h 是外沿），线粗和别处的细线一样是整数个物理像素 */
    private static void box(GuiGraphics g, float x, float y, float w, float h, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        float t = GuiShapes.pixels(g, ArchivePageScreen.lw());
        int c = GuiShapes.argb(rgb, a);
        GuiShapes.begin(g);
        GuiShapes.block(g, x, y, x + w, y + t, c);
        GuiShapes.block(g, x, y + h - t, x + w, y + h, c);
        GuiShapes.block(g, x, y + t, x + t, y + h - t, c);
        GuiShapes.block(g, x + w - t, y + t, x + w, y + h - t, c);
        GuiShapes.end();
    }

    /** 上 / 下三角：5 宽 3 高，(x, y) 是外接框左上角 */
    private static void tri(GuiGraphics g, int x, int y, boolean up, int rgb, float a) {
        for (int r = 0; r < 3; r++) {
            int ww = up ? 1 + 2 * r : 5 - 2 * r;
            int x0 = x + (5 - ww) / 2;
            ArchivePageScreen.fill(g, x0, y + r, x0 + ww, y + r + 1, rgb, a);
        }
    }

    /** 右箭头「→」：7 宽 5 高 */
    private static void arrowR(GuiGraphics g, int x, int y, int rgb, float a) {
        ArchivePageScreen.fill(g, x, y + 2, x + 6, y + 3, rgb, a);
        ArchivePageScreen.fill(g, x + 4, y + 1, x + 5, y + 4, rgb, a);
        ArchivePageScreen.fill(g, x + 5, y + 2, x + 6, y + 3, rgb, a);
        ArchivePageScreen.fill(g, x + 3, y, x + 4, y + 1, rgb, a);
        ArchivePageScreen.fill(g, x + 3, y + 4, x + 4, y + 5, rgb, a);
    }

    /** 画消散的本体（带透明度） */
    private interface Body {
        void draw(float alpha);
    }

    /**
     * 消散：本体在前 30% 里淡掉，同时一把稀疏的像素碎片（每 2×2 格里约两成）往下掉、淡出。
     * 碎片的位置、延迟、速度全由 seed 和格子下标算出来，每帧一样、不存元素表
     */
    private static void dissolve(GuiGraphics g, int x, int y, int w, int h, float u, int rgb, int seed,
                                 @Nullable Body body) {
        if (u >= 1.0F) {
            return;
        }
        if (body != null) {
            float al = 1.0F - seg01(u, 0.0F, 0.3F);
            if (al > 0.0F) {
                body.draw(al);
            }
        }
        if (u <= 0.0F) {
            return;
        }
        int cols = (w + 1) / 2;
        int rows = (h + 1) / 2;
        int light = FxCurve.lerpColor(rgb, FxPalette.WHITE, 0.6F);
        ArchivePageScreen.additive(g, () -> {
            GuiShapes.begin(g);
            for (int i = 0; i < cols; i++) {
                for (int j = 0; j < rows; j++) {
                    float h1 = ArchivePageScreen.hash(seed + i * 31, j * 17 + 3);
                    if (h1 > 0.22F) {
                        continue;
                    }
                    float h2 = ArchivePageScreen.hash(seed + j * 13, i + 5);
                    float delay = h2 * 0.25F;
                    float t = seg01(u, delay, delay + 0.75F);
                    if (t <= 0.0F || t >= 1.0F) {
                        continue;
                    }
                    float v = 8.0F + 16.0F * ArchivePageScreen.hash(seed + j, i + 7);
                    float dx = (h2 - 0.5F) * 12.0F * t;
                    float dy = v * t * t * 1.2F - 3.0F * Mth.sin(Mth.PI * t);
                    int c = h1 < 0.04F ? light : rgb;
                    int sz = h2 > 0.6F ? 2 : 1;
                    int px = Math.round(x + i * 2 + dx);
                    int py = Math.round(y + j * 2 + dy);
                    GuiShapes.block(g, px, py, px + sz, py + sz, GuiShapes.argb(c, 0.95F * (1.0F - t)));
                }
            }
            GuiShapes.end();
        });
    }

    /** 故障描边：38 ms 一跳，左右乱跳三下，品红 / 青各错 1 格，最后定住、淡掉（GuiFx#rivenCycle 的画法） */
    private static void glitchFrame(GuiGraphics g, int x, int y, int w, int h, long age, int dur, int rgb) {
        if (age < 0 || age > dur) {
            return;
        }
        int[] jumps = {2, -1, 1, 0, 0};
        int j = (int) Math.min(jumps.length - 1, age / 38);
        int off = age < 38 * 4 ? jumps[j] : 0;
        float a = 1.0F - seg(age, dur * 0.6F, dur);
        ArchivePageScreen.additive(g, () -> {
            if (off != 0) {
                box(g, x + off - 1, y, w, h, MAGENTA, 0.6F * a);
                box(g, x + off + 1, y, w, h, CYAN, 0.6F * a);
            }
            box(g, x + off, y, w, h, rgb, 0.85F * a);
        });
    }

    /** 外扩一圈方框 */
    private static void ringFx(GuiGraphics g, int cx, int cy, int r0, int r1, long age, int dur, int rgb) {
        if (age < 0 || age > dur) {
            return;
        }
        float u = age / (float) dur;
        int r = Math.round(r0 + (r1 - r0) * easeOut(u));
        ArchivePageScreen.additive(g, () -> box(g, cx - r, cy - r, 2 * r, 2 * r, rgb, 0.9F * (1.0F - u)));
    }

    /** 飞行轨迹：从 (x0, y0) 到 (x1, y1)，中间往上拱 lift 格；坐标取整 */
    private static int[] flyPos(int x0, int y0, int x1, int y1, float u, int lift) {
        float e = inOut(clamp01(u));
        return new int[]{Math.round(Mth.lerp(e, x0, x1)), Math.round(Mth.lerp(e, y0, y1) - Mth.sin(Mth.PI * e) * lift)};
    }

    private List<Component> tips(String head, Component... rest) {
        List<Component> out = new ArrayList<>(1 + rest.length);
        out.add(Component.translatable(head).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FxPalette.BONE))));
        for (Component c : rest) {
            out.add(c.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(KuvaPalette.MUTED))));
        }
        return out;
    }

    /** 字墨宽：font.width 带着末字后面 1 格的字距，居中、贴右按字墨算 */
    private int ink(String str) {
        return str.isEmpty() ? 0 : Math.round(s.textW(str, 0.0F)) - 1;
    }

    private static boolean hasCjk(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }

    private static String tr(String key, Object... args) {
        return ArchivePageScreen.tr(key, args);
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }

    private static float seg(long t, float a, float b) {
        return clamp01((t - a) / (b - a));
    }

    private static float seg01(float u, float a, float b) {
        return clamp01((u - a) / (b - a));
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static float inOut(float t) {
        return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F;
    }
}
