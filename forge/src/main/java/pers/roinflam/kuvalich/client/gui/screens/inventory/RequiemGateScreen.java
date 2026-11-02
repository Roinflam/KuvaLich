package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.MurmurTriangle;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils;
import pers.roinflam.kuvalich.client.lich.LichArchiveKeys;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.RequiemGateFillPacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.world.inventory.RequiemGateMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 灭骸之扉界面（放安魂卡的方块界面），画成玄骸档案的另一页
 *
 * <ul>
 *   <li>左栏：安魂密语三角（和档案同一个部件，未揭示的菱晶液面就是这一阶段的进度，每 3 秒随机两枚换位，
 *       排列不代表顺序），下面三段解密进度；</li>
 *   <li>中栏：三个卡槽，每个槽上面是这张卡的大符号投影，下面三颗点是还能用几次、再下面是卡名；
 *       放进去时符号从下往上扫出来、卡名逐字打出、次数点一颗颗亮；拿走时投影往中线收拢。下部是背包；</li>
 *   <li>右栏：悬停的那张卡（没悬停时看最近放进去的）的说明；</li>
 *   <li>底部「[K] 玄骸档案」，点了（或者按档案键）关掉这个界面、打开档案；创造模式下按 [TAB] 一键补上正确答案卡
 *       （服务端会再核一次创造模式）；</li>
 *   <li>开界面放一声开门，再垫一层很轻的循环底噪，400 ms 淡入，关界面 300 ms 淡出。</li>
 * </ul>
 *
 * <p>数据走 {@link RequiemGateMenu} 同步的谜语三格、阶段和进度；阈值按同一套配置在客户端算。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemGateScreen extends ArchivePageScreen<RequiemGateMenu> {

    private static final String[] ROMAN = {"I", "II", "III"};
    private static final int DOT_COUNT = 3;
    /** 密语三角、卡槽投影、右栏的符号尺寸 */
    private static final float TRI_SIZE = 20.0F;
    private static final float SOCKET_SIGIL = 22.0F;
    private static final float DETAIL_SIGIL = 20.0F;
    /** 自己的事件 id：揭示第 k 枚是 REVEAL + k，三张放齐是 COMPLETE */
    private static final int EV_REVEAL = 100;
    private static final int EV_COMPLETE = 200;

    /** 循环底噪的音量和淡入、淡出 */
    private static final float AMBIENCE_VOLUME = 0.6F;
    private static final int AMBIENCE_FADE_IN_MS = 400;
    private static final int AMBIENCE_FADE_OUT_MS = 300;

    private final MurmurTriangle triangle = new MurmurTriangle(TRI_SIZE);
    private final int[] riddleBuffer = new int[3];
    /** 上一帧的谜语三格，揭示时比出来；−2 = 还没读过 */
    private final int[] lastRiddles = {-2, -2, -2};
    /** 谜语卡的物品缓存（悬停时显示卡的说明），只在卡 id 变了时重建 */
    private final ItemStack[] riddleCards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] riddleCardIds = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    private boolean wasComplete;

    private KuvaSoundInstance ambience;
    private boolean opened;
    private Component linkText = Component.empty();

    /** 版式：卡槽那一块的顶、三个卡槽的中线 x、卡槽格子的 y；左栏三角的中心、进度行距 */
    private int pTop;
    private final int[] sockX = new int[3];
    private int sockIY;
    private int triX;
    private int triY;
    private int rowSp;

    // ==================== 调试（开发环境的截图命令用） ====================

    private static boolean debugOverride;
    private static final int[] DEBUG_RIDDLES = {-1, -1, -1};
    private static float debugProgress;

    public RequiemGateScreen(RequiemGateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 改窗口大小时 init 会再调一次，声音只在第一次打开时放
        if (!opened) {
            opened = true;
            KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.BLOCK_REQUIEM_GATE_OPEN.get(), 1.0F, 1.0F));
            ambience = KuvaSoundPlayer.play(KuvaSoundPlayer.uiLoop(KuvaLichSounds.BLOCK_REQUIEM_GATE_AMBIENCE.get(),
                    AMBIENCE_VOLUME, AMBIENCE_FADE_IN_MS));
        }
        linkText = Component.translatable("kuvalich.archive.open_hint", KuvaKeyMappings.ARCHIVE.getTranslatedKeyMessage());
    }

    @Override
    protected void layout() {
        pTop = small ? hairY + 1 : hairY + 5;
        int sp = small ? 54 : 60;
        sockIY = pTop + 54;
        for (int i = 0; i < 3; i++) {
            sockX[i] = cx + (i - 1) * sp;
            place(menu.getSlot(i), sockX[i] - 8, sockIY);
        }
        placeInventory();
        triX = lcx;
        triY = hairY + 50;
        rowSp = small ? 16 : 19;
        track(0, 1, 2);
    }

    @Override
    public void removed() {
        super.removed();
        if (ambience != null) {
            ambience.fadeOut(AMBIENCE_FADE_OUT_MS);
            ambience = null;
        }
    }

    // ==================== 事件 ====================

    @Override
    protected void afterScan(long now) {
        int filled = filledCount();
        if (filled == 3 && !wasComplete) {
            // 创造模式一键补全时三格错开放进去，等最后一格放完再闪
            int waiting = 0;
            for (int i = 0; i < 3; i++) {
                if (pending(Ev.PLACE, i)) {
                    waiting++;
                }
            }
            event(Ev.CUSTOM, EV_COMPLETE, ItemStack.EMPTY, 90L * waiting + 200L);
        }
        wasComplete = filled == 3;
    }

    @Override
    protected void onEvent(Ev type, int id, ItemStack stack, long delayMs) {
        if (type == Ev.PLACE && id >= 0 && id < 3) {
            Slot s = menu.getSlot(id);
            burst(s.x + 8, s.y - 2, 6, -Mth.HALF_PI, 1.2F, 22.0F, 520.0F, 0.0F, FxPalette.KUVA, delayMs);
        }
    }

    @Override
    protected Component rejectMessage(Slot slot, ItemStack carried) {
        // 安魂卡本身也被拒，只会是同一种卡不能装两张（Oull）
        return Component.translatable(carried.getItem() instanceof AbstractRequiemCard
                ? "kuvalich.gate.one_oull" : "kuvalich.gate.only_cards");
    }

    // ==================== 画 ====================

    @Override
    protected void drawPage(GuiGraphics g, int mx, int my, long now, long t, float eF) {
        float eT = phase(t, T_TEXT_IN, T_TEXT_DONE);
        int[] rd = readRiddles();
        for (int k = 0; k < 3; k++) {
            if (lastRiddles[k] != -2 && lastRiddles[k] < 0 && rd[k] >= 0) {
                event(Ev.CUSTOM, EV_REVEAL + k, ItemStack.EMPTY, 0L);
            }
            lastRiddles[k] = rd[k];
        }

        drawLeft(g, now, t, eF, eT, rd);

        // ---- 中栏：背光、三个卡槽、连接线、状态行 ----
        drawBacklight(g, cx - 110, pTop - 30, 220, 150, 0.85F * eT);
        for (int i = 0; i < 3; i++) {
            drawSocket(g, i, mx, my, now, t);
        }
        GuiShapes.begin(g);
        float lineW = GuiShapes.pixels(g, lw());
        float eC = phase(t, 900, 1200);
        for (int i = 0; i < 2; i++) {
            int a = sockX[i];
            int b = sockX[i + 1];
            int y = sockIY + 8;
            hl(g, a + 12, b - 12, y, FxPalette.KUVA_PALE, 0.12F * eC);
            float mid = Math.round((a + b) * 0.5F);
            boolean on = menu.getSlot(i).hasItem() && menu.getSlot(i + 1).hasItem();
            int c = GuiShapes.argb(on ? FxPalette.KUVA : FxPalette.ASH, (on ? 0.85F : 0.35F) * eC);
            GuiShapes.hairline(g, mid - 1.5F, y - 2.5F, mid + 1.0F, y, lineW, c);
            GuiShapes.hairline(g, mid + 1.0F, y, mid - 1.5F, y + 2.5F, lineW, c);
        }
        GuiShapes.end();
        int sy = pTop + 95;
        if (!drawMessage(g, cx, sy, eF)) {
            int filled = filledCount();
            if (filled == 3) {
                textC(g, Component.translatable("kuvalich.gate.ready").getString(), cx, sy, FxPalette.BONE, 0.85F * eF);
                long ca = age(Ev.CUSTOM, EV_COMPLETE);
                if (ca < 600) {
                    additive(g, () -> hl(g, cx - 70, cx + 70, sy + 11, FxPalette.KUVA_PALE, 0.8F * (1.0F - ca / 600.0F)));
                }
            } else {
                textC(g, Component.translatable("kuvalich.gate.place_cards", filled).getString(), cx, sy,
                        FxPalette.ASH, 0.7F * eF);
            }
        }
        drawDrift(g, cx, pTop + 6, 150, 92, now, eF);

        // ---- 右栏：卡的说明 ----
        heading(g, Component.translatable("kuvalich.gate.cards").getString(), rx0, eF, 2.0F);
        hl(g, rx0, rx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
        drawDetail(g, mx, my, now, eF, rd);

        // ---- 底部：档案入口、创造补全 ----
        if (!KuvaKeyMappings.ARCHIVE.isUnbound()) {
            String s = linkText.getString();
            float w = textW(s, 0.0F);
            float x0 = rx1 - w;
            boolean hv = mx >= x0 && mx < rx1 && my >= botY - 1 && my < botY + 10;
            text(g, s, x0, botY, hv ? FxPalette.BONE : FxPalette.ASH, eF);
            hot(x0, botY - 1, rx1, botY + 10, () -> List.of(tip("kuvalich.gate.tip.archive", FxPalette.BONE)),
                    this::openArchive);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            textC(g, "[TAB] " + Component.translatable("kuvalich.gate.fill_hint").getString(), cx, botY,
                    FxPalette.ASH, 0.8F * eF);
        }
    }

    /** 左栏：标题、密语三角、三段进度 */
    private void drawLeft(GuiGraphics g, long now, long t, float eF, float eT, int[] rd) {
        heading(g, this.title.getString(), lx0, eF, 2.0F);
        hl(g, lx0, lx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
        int y0 = hairY + 8;
        text(g, Component.translatable("kuvalich.gate.murmurs").getString(), lx0, y0, FxPalette.ASH, eT);
        hlFade(g, lx0, lx0 + 64, y0 + 11, FxPalette.KUVA_PALE, 0.35F * eT);

        triangle.update(rd, stageProgress(), now);
        triangle.render(g, triX, triY, now, eT);
        float lineW = GuiShapes.pixels(g, lw());
        for (int k = 0; k < 3; k++) {
            long ag = age(Ev.CUSTOM, EV_REVEAL + k);
            if (ag < 700) {
                float e = ag / 700.0F;
                float x = triangle.lastX(k);
                float y = triangle.lastY(k);
                additive(g, () -> GuiShapes.ring(g, x, y, 6.0F + 16.0F * easeOut(e), lineW,
                        GuiShapes.argb(FxPalette.KUVA_PALE, 0.7F * (1.0F - e)), 1.0F));
            }
            final int slot = k;
            float x = triangle.lastX(k);
            float y = triangle.lastY(k);
            float hw = rd[k] >= 0 ? RequiemSigils.width(TRI_SIZE) * 0.5F : TRI_SIZE * 0.5F * GuiShapes.DIAMOND_ASPECT;
            hot(x - hw, y - TRI_SIZE * 0.5F, x + hw, y + TRI_SIZE * 0.5F, () -> murmurTip(slot), null);
        }
        text(g, Component.translatable("kuvalich.gate.order_hint").getString(), lx0, triY + 24, FxPalette.ASH, 0.5F * eT);

        int ly = triY + 38;
        text(g, Component.translatable("kuvalich.gate.progress").getString(), lx0, ly, FxPalette.ASH, eT);
        hlFade(g, lx0, lx0 + 64, ly + 11, FxPalette.KUVA_PALE, 0.35F * eT);
        int level = menu.getLevel();
        int w = lx1 - lx0;
        for (int k = 0; k < 3; k++) {
            int y = ly + 15 + k * rowSp;
            int by = y + 11;
            boolean done = k < level || rd[k] >= 0;
            boolean active = !done && k == level;
            float eb = quick ? eT : easeOut(seg(t, 700 + 110 * k, 1300 + 110 * k));
            text(g, stageName(k), lx0, y, done || active ? FxPalette.BONE : FxPalette.ASH, (done || active ? 0.9F : 0.5F) * eT);
            long rv = age(Ev.CUSTOM, EV_REVEAL + k);
            if (done) {
                if (rd[k] >= 0) {
                    RequiemSigils.draw(g, rd[k], lx0 + 36, y + 4, 8.0F, GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * eT), 0);
                }
                // 刚揭示时「已揭示」逐字打出
                String v = Component.translatable("kuvalich.gate.revealed").getString();
                int n = rv < 400 ? (int) Math.min(v.length(), rv / 90) : v.length();
                text(g, v.substring(0, n), lx1 - textW(v, 0.0F), y, FxPalette.KUVA_PALE, 0.85F * eT);
            } else if (active) {
                textR(g, menu.getSchedule() + " / " + threshold(k), lx1, y, FxPalette.BONE, 0.9F * eT);
            } else {
                textR(g, Component.translatable("kuvalich.gate.not_started").getString(), lx1, y, FxPalette.ASH, 0.45F * eT);
            }
            GuiShapes.begin(g);
            fill(g, lx0, by, lx1, by + 2, FxPalette.ASH, 0.16F * eT);
            float f = (done ? 1.0F : active ? triangle.shown(now) : 0.0F) * eb;
            int fx = lx0 + Math.round(w * f);
            if (f > 0.0F) {
                fill(g, lx0, by, fx, by + 2, done ? FxPalette.KUVA_DEEP : FxPalette.KUVA, 0.95F * eT);
                if (done) {
                    fill(g, lx0, by, fx, by + 1, FxPalette.KUVA, 0.6F * eT);
                } else {
                    fill(g, fx - 1, by - 1, fx, by + 3, FxPalette.KUVA_PALE, 0.9F * eT);
                }
            }
            GuiShapes.end();
            if (f > 0.0F && !done) {
                additive(g, () -> fill(g, fx - 2, by - 1, fx + 1, by + 3, FxPalette.KUVA, 0.3F * eT));
            }
            if (rv < 500) {
                additive(g, () -> fill(g, lx0, by - 1, lx1, by + 3, FxPalette.KUVA_PALE, 0.7F * (1.0F - rv / 500.0F)));
            }
            final int stage = k;
            hot(lx0, y - 1, lx1, by + 3, () -> stageTip(stage), null);
        }
    }

    /** 一个卡槽：编号、投影框、往下的细线、格子（物品由原版画在上面）、次数点、卡名 */
    private void drawSocket(GuiGraphics g, int i, int mx, int my, long now, long t) {
        float eS = phase(t, 650 + 120 * i, 1050 + 120 * i);
        if (eS <= 0.0F) {
            return;
        }
        Slot slot = menu.getSlot(i);
        ItemStack st = pending(Ev.PLACE, i) ? ItemStack.EMPTY : slot.getItem();
        boolean has = !st.isEmpty();
        long rj = age(Ev.REJECT, i);
        int dx = rj < 300 ? Math.round(Mth.sin(rj / 22.0F) * 3.0F * (1.0F - rj / 300.0F)) : 0;
        int x = sockX[i] + dx;
        int bx0 = x - 17;
        int by0 = pTop + 13;
        int bx1 = x + 17;
        int by1 = pTop + 47;
        int pcy = pTop + 30;
        textC(g, ROMAN[i], x, pTop, has ? FxPalette.BONE : FxPalette.ASH, (has ? 0.9F : 0.55F) * eS);

        long ins = age(Ev.PLACE, i);
        if (has) {
            GuiShapes.begin(g);
            fill(g, bx0, by0, bx1, by1, INK, 0.85F * eS);
            frame(g, bx0, by0, bx1, by1, FxPalette.KUVA_PALE, 0.5F * eS);
            GuiShapes.end();
            int id = cardId(st);
            long cons = age(Ev.CONSUME, i);
            float flick = cons < 260 ? ((cons / 65) % 2 == 1 ? 0.45F : 1.0F) : 1.0F;
            float boost = 1.0F + 1.4F * (1.0F - seg(ins, 0, 600));
            if (ins < 320) {
                // 放进去：符号从下往上扫出来，扫描线带一道红光
                int cy0 = Math.round(by1 - (by1 - by0) * easeOut(ins / 320.0F));
                g.enableScissor(bx0, cy0, bx1, by1);
                drawCardSigil(g, id, x, pcy, SOCKET_SIGIL, eS * flick, boost);
                g.disableScissor();
                hl(g, bx0 + 1, bx1 - 1, cy0, FxPalette.KUVA_PALE, 0.9F);
                additive(g, () -> GuiShapes.hline(g, bx0, bx1, cy0, lw() * 3, GuiShapes.argb(FxPalette.KUVA, 0.5F)));
            } else {
                drawCardSigil(g, id, x, pcy, SOCKET_SIGIL, eS * flick, boost);
            }
        } else {
            float br = 0.75F + 0.25F * Mth.sin((now % 2400L) / 2400.0F * Mth.TWO_PI);
            GuiShapes.begin(g);
            GuiShapes.dashedFrame(g, bx0, by0, bx1, by1, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * eS * br));
            GuiShapes.diamondOutline(g, x, pcy, 4.8F, 8.0F, GuiShapes.pixels(g, lw()),
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.12F * eS));
            GuiShapes.end();
            // 刚拿走：投影往中线收拢
            Fx taken = last(Ev.TAKE, i);
            long rm = taken == null ? Long.MAX_VALUE : now - taken.t0();
            if (taken != null && rm < 180) {
                float e = 1.0F - rm / 180.0F;
                int hh = Math.round((by1 - by0) * 0.5F * e);
                if (hh > 0) {
                    g.enableScissor(bx0, pcy - hh, bx1, pcy + hh);
                    drawCardSigil(g, cardId(taken.stack()), x, pcy, SOCKET_SIGIL, e, 1.0F);
                    g.disableScissor();
                }
                additive(g, () -> hl(g, bx0, bx1, pcy, FxPalette.KUVA_PALE, 0.8F * e));
            }
        }

        // 格子不跟着抖：物品画在槽位的固定坐标上
        int sx = sockX[i];
        int cx0 = sx - 9;
        int cy0 = sockIY - 1;
        boolean hv = isHovering(slot.x, slot.y, 16, 16, mx, my);
        GuiShapes.begin(g);
        vl(g, x, by1, sockIY - 1, FxPalette.KUVA_PALE, 0.3F * eS);
        fill(g, cx0, cy0, cx0 + 18, cy0 + 18, INK, 0.7F * eS);
        if (hv) {
            fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA_PALE, 0.1F);
        }
        frame(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA_PALE, (hv ? 0.6F : has ? 0.4F : 0.18F) * eS);

        // 次数点：亮的带一圈暗红晕；刚用掉的那颗白闪一下
        int dy = sockIY + 21;
        int left = has ? usesLeft(st) : 0;
        long ca = age(Ev.CONSUME, i);
        for (int k = 0; k < DOT_COUNT; k++) {
            int px = sx + (k - 1) * 4;
            boolean lit = has && k < left && ins >= 120 + 70L * k;
            if (lit) {
                fill(g, px - 2, dy - 1, px + 2, dy + 3, FxPalette.KUVA_DEEP, 0.6F * eS);
                fill(g, px - 1, dy, px + 1, dy + 2, FxPalette.KUVA, eS);
            } else {
                fill(g, px - 1, dy, px + 1, dy + 2, has ? FxPalette.KUVA_DEEP : FxPalette.ASH, (has ? 0.55F : 0.2F) * eS);
            }
        }
        GuiShapes.end();
        if (rj < 300) {
            additive(g, () -> fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA, 0.5F * (1.0F - rj / 300.0F)));
        }
        if (has && left < DOT_COUNT && ca < 300) {
            int px = sx + (left - 1) * 4;
            additive(g, () -> fill(g, px - 2, dy - 1, px + 2, dy + 3, FxPalette.WHITE, 1.0F - ca / 300.0F));
        }

        // 卡名：放进去时逐字打出
        if (has) {
            String name = st.getHoverName().getString();
            float fullW = textW(name, 1.0F);
            int n = ins < 400 ? (int) Math.min(name.length(), ins / 40) : name.length();
            text(g, name.substring(0, n), Math.round(sx - fullW * 0.5F), sockIY + 26, FxPalette.BONE, 0.85F * eS, 1.0F);
        } else {
            textC(g, "—", sx, sockIY + 26, FxPalette.ASH, 0.4F * eS);
        }
    }

    /** 压在物品上面：放进去那一下的白闪、开场时卡槽里卡片的盖板、补全时还没轮到的格子 */
    @Override
    protected void drawOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        for (int i = 0; i < 3; i++) {
            Slot slot = menu.getSlot(i);
            int cx0 = sockX[i] - 9;
            int cy0 = sockIY - 1;
            if (pending(Ev.PLACE, i)) {
                GuiShapes.begin(g);
                fill(g, cx0 + 1, cy0 + 1, cx0 + 17, cy0 + 17, INK, 1.0F);
                GuiShapes.end();
                continue;
            }
            long ins = age(Ev.PLACE, i);
            if (slot.hasItem() && ins < 250) {
                additive(g, () -> fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA_PALE, 0.5F * (1.0F - ins / 250.0F)));
            }
            float cov = 1.0F - phase(t, 800 + 120 * i, 1100 + 120 * i);
            if (cov > 0.0F && slot.hasItem()) {
                GuiShapes.begin(g);
                fill(g, cx0 + 1, cy0 + 1, cx0 + 17, cy0 + 17, INK, cov);
                GuiShapes.end();
            }
        }
    }

    /** 右栏：悬停的卡 → 悬停的已揭示密语 → 最近放进去的 → 第一个有卡的槽；都没有时一段说明 */
    private void drawDetail(GuiGraphics g, int mx, int my, long now, float a, int[] rd) {
        ItemStack focus = ItemStack.EMPTY;
        Component where = Component.empty();
        for (Slot s : menu.slots) {
            if (isHovering(s.x, s.y, 16, 16, mx, my) && s.getItem().getItem() instanceof AbstractRequiemCard) {
                focus = s.getItem();
                where = s.index < 3 ? Component.translatable("kuvalich.gate.where.slot", ROMAN[s.index])
                        : Component.translatable("kuvalich.gate.where.inventory");
                break;
            }
        }
        if (focus.isEmpty()) {
            Integer m = triangle.hoveredSlot(mx, my);
            if (m != null && rd[m] >= 0 && !riddleCards[m].isEmpty()) {
                focus = riddleCards[m];
                where = Component.translatable("kuvalich.gate.where.murmur");
            }
        }
        if (focus.isEmpty()) {
            Fx lp = lastPlace();
            if (lp != null && lp.id() >= 0 && lp.id() < 3 && ItemStack.matches(menu.getSlot(lp.id()).getItem(), lp.stack())) {
                focus = lp.stack();
                where = Component.translatable("kuvalich.gate.where.slot", ROMAN[lp.id()]);
            }
        }
        if (focus.isEmpty()) {
            for (int i = 0; i < 3; i++) {
                if (menu.getSlot(i).hasItem()) {
                    focus = menu.getSlot(i).getItem();
                    where = Component.translatable("kuvalich.gate.where.slot", ROMAN[i]);
                    break;
                }
            }
        }
        int x0 = rx0;
        float cw = rx1 - rx0;
        int y = hairY + 8;
        if (focus.isEmpty()) {
            for (String line : wrap(Component.translatable("kuvalich.gate.detail_empty").getString(), cw)) {
                text(g, line, x0, y, FxPalette.ASH, 0.6F * a);
                y += 10;
            }
            return;
        }
        int id = cardId(focus);
        int left = usesLeft(focus);
        drawCardSigil(g, id, x0 + 12, y + 13, DETAIL_SIGIL, a, 1.0F);
        text(g, focus.getHoverName().getString(), x0 + 28, y + 3, FxPalette.BONE, a, 1.0F);
        GuiShapes.begin(g);
        for (int k = 0; k < DOT_COUNT; k++) {
            int px = x0 + 30 + k * 5;
            if (k < left) {
                fill(g, px - 1, y + 15, px + 3, y + 19, FxPalette.KUVA_DEEP, 0.6F * a);
                fill(g, px, y + 16, px + 2, y + 18, FxPalette.KUVA, a);
            } else {
                fill(g, px, y + 16, px + 2, y + 18, FxPalette.ASH, 0.25F * a);
            }
        }
        GuiShapes.end();
        y += 30;
        text(g, Component.translatable("kuvalich.gate.uses_left", left).getString(), x0, y, FxPalette.KUVA, 0.9F * a);
        y += 14;
        String key = focus.getItem().getDescriptionId();
        for (String part : new String[]{key + ".first_tooltip", key + ".second_tooltip"}) {
            for (String line : wrapEven(Component.translatable(part).getString(), cw)) {
                text(g, line, x0, y, KuvaPalette.MUTED, 0.85F * a);
                y += 10;
            }
            y += 3;
        }
        text(g, where.getString(), x0, y + 2, FxPalette.ASH, 0.55F * a);
    }

    /** 卡面的样子：深红填 + 粉白线 + 红光；{@code boost} > 1 时光更亮（刚放进去） */
    private static void drawCardSigil(GuiGraphics g, int id, float x, float y, float size, float a, float boost) {
        if (a <= 0.002F) {
            return;
        }
        RequiemSigils.drawLayers(g, id, x, y, size, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.92F * a),
                GuiShapes.argb(FxPalette.KUVA, Math.min(1.0F, 0.45F * boost) * a),
                GuiShapes.argb(FxPalette.KUVA, Math.min(1.0F, 0.25F * boost) * a),
                GuiShapes.argb(FxPalette.KUVA_PALE, a));
    }

    // ==================== 提示框 ====================

    private static MutableComponent tip(String key, int rgb, Object... args) {
        return Component.translatable(key, args).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private static MutableComponent line(String s, int rgb) {
        return Component.literal(s).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private List<Component> stageTip(int k) {
        List<Component> out = new ArrayList<>();
        int level = menu.getLevel();
        int rd = riddleBuffer[k];
        if (k < level || rd >= 0) {
            out.add(line(stageName(k) + " · " + Component.translatable("kuvalich.gate.revealed").getString(), FxPalette.BONE));
            if (rd >= 0 && !riddleCards[k].isEmpty()) {
                out.add(tip("kuvalich.gate.tip.murmur_of", KuvaPalette.MUTED, riddleCards[k].getHoverName()));
            }
        } else if (k == level) {
            out.add(line(stageName(k) + " · " + menu.getSchedule() + " / " + threshold(k), FxPalette.BONE));
            out.add(tip("kuvalich.gate.tip.stage_hint", KuvaPalette.MUTED));
        } else {
            out.add(line(stageName(k) + " · " + Component.translatable("kuvalich.gate.not_started").getString(), FxPalette.ASH));
            out.add(tip("kuvalich.gate.tip.stage_locked", KuvaPalette.MUTED));
        }
        return out;
    }

    private List<Component> murmurTip(int k) {
        List<Component> out = new ArrayList<>();
        if (riddleBuffer[k] >= 0 && !riddleCards[k].isEmpty()) {
            ItemStack card = riddleCards[k];
            out.add(card.getHoverName().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FxPalette.BONE))));
            out.add(tip(card.getItem().getDescriptionId() + ".first_tooltip", KuvaPalette.MUTED));
            out.add(tip("kuvalich.gate.where.murmur", FxPalette.ASH));
            return out;
        }
        String state = Component.translatable("kuvalich.gate.tip.unrevealed").getString();
        if (k == menu.getLevel()) {
            out.add(line(state + " · " + menu.getSchedule() + " / " + threshold(k), FxPalette.BONE));
        } else {
            out.add(line(state + " · " + Component.translatable("kuvalich.gate.tip.waiting").getString(), FxPalette.ASH));
        }
        out.add(tip("kuvalich.gate.tip.order", KuvaPalette.MUTED));
        return out;
    }

    // ==================== 数据 ====================

    private int[] readRiddles() {
        if (debugOverride) {
            System.arraycopy(DEBUG_RIDDLES, 0, riddleBuffer, 0, 3);
        } else {
            riddleBuffer[0] = menu.getOneRiddle();
            riddleBuffer[1] = menu.getTwoRiddle();
            riddleBuffer[2] = menu.getThreeRiddle();
        }
        for (int i = 0; i < 3; i++) {
            if (riddleBuffer[i] != riddleCardIds[i]) {
                riddleCardIds[i] = riddleBuffer[i];
                riddleCards[i] = riddleBuffer[i] < 0 ? ItemStack.EMPTY
                        : new ItemStack(AbstractRequiemCard.getCard(riddleBuffer[i]));
            }
        }
        return riddleBuffer;
    }

    private int filledCount() {
        int n = 0;
        for (int i = 0; i < 3; i++) {
            if (menu.getSlot(i).hasItem()) {
                n++;
            }
        }
        return n;
    }

    private static String stageName(int k) {
        return Component.translatable("kuvalich.gate.stage." + (k + 1)).getString();
    }

    private static int cardId(ItemStack stack) {
        return stack.getItem() instanceof AbstractRequiemCard c ? c.getID() : -1;
    }

    /** 这一阶段的进度 0~1；三条都揭示了算满 */
    private float stageProgress() {
        if (debugOverride) {
            return debugProgress;
        }
        int level = menu.getLevel();
        if (level >= 3) {
            return 1.0F;
        }
        int threshold = threshold(level);
        if (threshold <= 0) {
            return 0.0F;
        }
        return Mth.clamp(menu.getSchedule() / (float) threshold, 0.0F, 1.0F);
    }

    /**
     * 指定阶段的解密阈值。读的是客户端本地的配置值，实际升阶在服务端判定；
     * 两边配置不一致时这里的比例会和服务端对不上
     *
     * @param level 当前阶段（0~2）
     * @return 该阶段所需总点数，越界返回 0
     */
    private static int threshold(int level) {
        switch (level) {
            case 0:
                return ModConfig.KUVA_LICH.firstStage.get();
            case 1:
                return ModConfig.KUVA_LICH.secondStage.get();
            case 2:
                return ModConfig.KUVA_LICH.thirdStage.get();
            default:
                return 0;
        }
    }

    /** 卡还能用几次，按三颗点折算 */
    private static int usesLeft(ItemStack card) {
        if (!card.isDamageableItem()) {
            return DOT_COUNT;
        }
        int max = card.getMaxDamage();
        int remain = max - card.getDamageValue();
        if (max == DOT_COUNT) {
            return Mth.clamp(remain, 0, DOT_COUNT);
        }
        return Mth.clamp(Mth.ceil(remain * DOT_COUNT / (float) max), 0, DOT_COUNT);
    }

    // ==================== 玄骸档案入口、按键 ====================

    /**
     * 关掉灭骸之扉、打开玄骸档案。先关界面（顺带通知服务端关容器），再走档案键同一个入口，
     * 能不能开（处决中不能开之类）的规矩只在 {@link LichArchiveKeys#open} 一处
     */
    private void openArchive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KuvaKeyMappings.ARCHIVE.isUnbound()) {
            return;
        }
        mc.player.closeContainer();
        LichArchiveKeys.open(mc);
    }

    /**
     * 创造模式下按 TAB 请求服务端一键补全正确答案卡（服务端收到 {@link RequiemGateFillPacket} 后会再核一次创造模式）；
     * 按档案键打开玄骸档案；其余交给父类（背包键关界面等）
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isCreative()) {
                NetworkRegistryHandler.getChannel().sendToServer(new RequiemGateFillPacket());
                return true;
            }
        }
        if (KuvaKeyMappings.ARCHIVE.matches(keyCode, scanCode)) {
            openArchive();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 调试入口 ====================

    /**
     * 让界面里的三角改画给定的数据（不碰服务端），截图用
     *
     * @param riddles  三格卡 id，−1 为未揭示
     * @param progress 这一阶段的进度 0~1
     */
    public static void debugOverride(int[] riddles, float progress) {
        for (int i = 0; i < 3; i++) {
            DEBUG_RIDDLES[i] = riddles != null && i < riddles.length ? riddles[i] : -1;
        }
        debugProgress = Mth.clamp(progress, 0.0F, 1.0F);
        debugOverride = true;
    }

    /** 恢复读菜单里的真实数据 */
    public static void debugClear() {
        debugOverride = false;
    }
}
