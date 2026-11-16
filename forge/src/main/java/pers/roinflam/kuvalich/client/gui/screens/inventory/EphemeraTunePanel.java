package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraStateTable;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.network.packet.EphemeraTunePacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 军械库里调幻纹外观的那块面板：两色（色相 / 饱和度 / 明度 + 色值 + 预设）、渐变方式、大小、高度、恢复默认。
 * 拖动时只在本地变（人像上的预览和世界里自己身上的那份一起变），松手、点选时才发包；发包间隔不低于
 * {@link #SEND_INTERVAL_MS}，并且按服务端的限频攒着，没发出去的最后一次等到能发时补上，关界面时一定发出去。
 */
@OnlyIn(Dist.CLIENT)
final class EphemeraTunePanel {

    private static final String K = "kuvalich.ephemera.tune.";

    private static final int PAD = 8;
    private static final int ROW = 11;
    private static final int GAP = 4;
    private static final int COL = 6;
    private static final int SECTION = 9;
    private static final int TRACK_W = 120;
    private static final int BTN_H = 13;
    private static final int CLOSE = 7;
    private static final int CHIP = 9;
    private static final int CHIP_GAP = 4;
    private static final int SEG_GAP = 2;
    private static final int CHAMFER = 5;
    /** 一行里字的上沿：字形高 7（汉字 8），行高 11 时上下各空 2 */
    private static final int TEXT_DY = 2;
    private static final long SEND_INTERVAL_MS = 300L;
    // 服务端每人每秒只收 MAX_PER_SECOND 个，多的丢掉。平时最多用到预算减一，留一个给关界面时补发的最后一下；
    // 窗口比一秒宽一点扛网络抖动。放静态的，关了马上再开是新面板

    private static final int SEND_BUDGET = EphemeraTunePacket.MAX_PER_SECOND;
    private static final long SEND_WINDOW_MS = 1200L;
    private static final long[] SENT = new long[SEND_BUDGET];
    private static int sentNext;

    static {
        Arrays.fill(SENT, Long.MIN_VALUE / 4);
    }
    private static final float MIN_V = EphemeraItem.MIN_BRIGHTNESS / 255.0F;

    private static final int R_TARGET = 0;
    private static final int R_HUE = 1;
    private static final int R_SAT = 2;
    private static final int R_VAL = 3;
    private static final int R_HEX = 4;
    private static final int R_PRESET = 5;
    private static final int R_GRADIENT = 6;
    private static final int R_SCALE = 7;
    private static final int R_HEIGHT = 8;
    private static final int ROWS = 9;

    private static final String[] LABELS = {"color", "hue", "saturation", "value", "hex", "presets", "gradient",
            "scale", "height"};

    /** 赤毒武器的七种元素色，最后一格是模组自己的赤毒红 */
    private static final int[] PRESETS;
    private static final String[] PRESET_KEYS;

    static {
        List<String> els = KuvaWeaponUtil.LICH_ELEMENTS;
        PRESETS = new int[els.size() + 1];
        PRESET_KEYS = new String[els.size() + 1];
        for (int i = 0; i < els.size(); i++) {
            // 磁力（0x7A8CFF）和电击（0x8296FF）的元素色几乎一样，色块并排分不出来；磁力的色块换成深一档的钴蓝
            PRESETS[i] = els.get(i).equals("magnetic") ? 0x3F66FF : EphemeraType.defaultColor(els.get(i));
            PRESET_KEYS[i] = "kuvaweapon.type." + els.get(i);
        }
        PRESETS[els.size()] = KuvaPalette.LICH;
        PRESET_KEYS[els.size()] = K + "preset.kuva";
    }

    private final ArchivePageScreen<?> screen;
    private Font font;
    private EditBox hex;
    private boolean syncingHex;

    private boolean open;
    private int containerId;
    private String type = "";
    private String element = EphemeraType.DEFAULT_ELEMENT;
    private String styleName = "";
    private final int[] colors = new int[2];
    private int target;
    private float hue;
    private float sat;
    private float val;
    private int gradient;
    private float scale = 1.0F;
    private float yOffset;

    private int drag = -1;
    private boolean pending;
    private boolean pendingReset;
    private long lastSendMs = Long.MIN_VALUE / 4;

    private int x0;
    private int y0;
    private int x1;
    private int y1;
    private int cx0;
    private int cx1;
    private int tx;
    private int titleY;
    private int ruleY;
    private int sectionY;
    private int footY;
    private int resetX1;
    private int doneX0;
    private int hexW;
    private int areaX0;
    private final int[] rowY = new int[ROWS];

    EphemeraTunePanel(ArchivePageScreen<?> screen) {
        this.screen = screen;
    }

    boolean isOpen() {
        return open;
    }

    /** 每次 init（含改窗口大小）重排；areaX0 往右是面板能占的地方，左边留给人像 */
    void layout(Font font, int width, int height, int areaX0) {
        this.font = font;
        this.areaX0 = areaX0;
        int labelW = 0;
        for (String l : LABELS) {
            labelW = Math.max(labelW, font.width(tr(K + l)));
        }
        int valueW = 0;
        for (String v : new String[]{"360°", "100%", "2.00×", "+0.50", "-0.50"}) {
            valueW = Math.max(valueW, font.width(v));
        }
        int contentW = labelW + COL + TRACK_W + COL + valueW;
        int resetW = font.width(tr(K + "reset")) + 12;
        int doneW = font.width(tr(K + "done")) + 12;
        contentW = Math.max(contentW, resetW + COL + doneW);
        int w = contentW + 2 * PAD;
        int h = PAD + 12 + 6 + 6 * ROW + 5 * GAP + SECTION + 3 * ROW + 2 * GAP + 8 + BTN_H + PAD;

        x0 = Math.round((areaX0 + width) * 0.5F - w * 0.5F);
        x0 = Mth.clamp(x0, areaX0 + 4, Math.max(areaX0 + 4, width - w - 4));
        y0 = Math.max(2, (height - h) / 2);
        x1 = x0 + w;
        y1 = y0 + h;
        cx0 = x0 + PAD;
        cx1 = x1 - PAD;
        tx = cx0 + labelW + COL;
        titleY = y0 + PAD;
        ruleY = titleY + 12;
        int y = ruleY + 6;
        for (int i = 0; i < ROWS; i++) {
            if (i == R_GRADIENT) {
                sectionY = y - GAP + Math.round((SECTION + GAP) * 0.5F) - 1;
                y += SECTION - GAP;
            }
            rowY[i] = y;
            y += ROW + GAP;
        }
        footY = rowY[R_HEIGHT] + ROW + 8;
        resetX1 = cx0 + resetW;
        doneX0 = cx1 - doneW;

        hexW = font.width("#DDDDDD") + 6;
        String keep = hex == null ? "" : hex.getValue();
        boolean focused = hex != null && hex.isFocused();
        hex = new EditBox(font, tx + 3, rowY[R_HEX] + TEXT_DY, hexW - 6, ROW - TEXT_DY, Component.empty());
        hex.setBordered(false);
        hex.setMaxLength(7);
        hex.setTextColor(FxPalette.BONE);
        hex.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setValue(keep);
        hex.setFocused(focused);
        hex.setResponder(this::onHexTyped);
    }

    // ==================== 开关 ====================

    void open(ItemStack stack, int containerId) {
        EphemeraType t = EphemeraItem.getType(stack);
        if (t == null) {
            return;
        }
        this.open = true;
        this.containerId = containerId;
        this.type = t.id();
        this.element = EphemeraItem.getElement(stack);
        this.styleName = tr(t.nameKey());
        colors[0] = EphemeraItem.getColor1(stack);
        colors[1] = EphemeraItem.getColor2(stack);
        gradient = EphemeraItem.getGradient(stack);
        scale = EphemeraItem.getScale(stack);
        yOffset = EphemeraItem.getYOffset(stack);
        target = 0;
        drag = -1;
        hue = 0.0F;
        loadHsv();
        if (hex != null) {
            hex.setFocused(false);
        }
        refreshHex();
    }

    /** 还没发出去的那次留着，由界面每帧的 {@link #flush} 按间隔补发 */
    void close() {
        if (drag >= 0) {
            drag = -1;
            pending = true;
        }
        open = false;
        if (hex != null) {
            hex.setFocused(false);
        }
    }

    void tick() {
        if (hex != null) {
            hex.tick();
        }
    }

    /** force：界面要关了，等不到下一个间隔 */
    void flush(long now, boolean force) {
        if (!pending || (!force && (now - lastSendMs < SEND_INTERVAL_MS || sentWithin(now) >= SEND_BUDGET - 1))) {
            return;
        }
        pending = false;
        lastSendMs = now;
        SENT[sentNext] = now;
        sentNext = (sentNext + 1) % SENT.length;
        EphemeraTunePacket.send(pendingReset ? EphemeraTunePacket.reset(containerId)
                : new EphemeraTunePacket(containerId, EphemeraTunePacket.SET, colors[0], colors[1], gradient, scale,
                yOffset));
        pendingReset = false;
    }

    private static int sentWithin(long now) {
        int n = 0;
        for (long t : SENT) {
            if (now - t < SEND_WINDOW_MS) {
                n++;
            }
        }
        return n;
    }

    EphemeraLook look() {
        return new EphemeraLook(type, element, colors[0], colors[1], gradient, scale, yOffset);
    }

    // ==================== 改值 ====================

    /** commit：松手 / 点选，这次要发给服务端 */
    private void changed(boolean commit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // 服务端下一次同步会盖回真值
            EphemeraStateTable.CLIENT.put(mc.player.getId(), look());
        }
        if (commit) {
            pending = true;
            pendingReset = false;
        }
    }

    private void loadHsv() {
        float[] hsv = rgbToHsv(colors[target]);
        if (hsv[1] > 0.0F) {
            hue = hsv[0];
        }
        sat = hsv[1];
        val = Math.max(MIN_V, hsv[2]);
    }

    private void applyHsv() {
        colors[target] = EphemeraItem.clampColor(Mth.hsvToRgb(Math.min(hue, 0.9999F), sat, val));
        refreshHex();
    }

    private void setColor(int rgb) {
        colors[target] = EphemeraItem.clampColor(rgb);
        loadHsv();
        refreshHex();
    }

    private void refreshHex() {
        if (hex == null || hex.isFocused()) {
            return;
        }
        syncingHex = true;
        hex.setValue(String.format(Locale.ROOT, "#%06X", colors[target]));
        syncingHex = false;
    }

    private void onHexTyped(String s) {
        if (syncingHex || !open) {
            return;
        }
        String digits = s.startsWith("#") ? s.substring(1) : s;
        if (digits.length() != 6) {
            return;
        }
        colors[target] = EphemeraItem.clampColor(Integer.parseInt(digits, 16));
        loadHsv();
        changed(true);
    }

    private void reset() {
        int def = EphemeraType.defaultColor(element);
        colors[0] = def;
        colors[1] = def;
        gradient = EphemeraItem.GRADIENT_OFF;
        scale = 1.0F;
        yOffset = 0.0F;
        loadHsv();
        if (hex != null) {
            hex.setFocused(false);
        }
        refreshHex();
        changed(true);
        pendingReset = true;
    }

    private static boolean isSlider(int row) {
        return row == R_HUE || row == R_SAT || row == R_VAL || row == R_SCALE || row == R_HEIGHT;
    }

    private float sliderU(int row) {
        return switch (row) {
            case R_HUE -> hue;
            case R_SAT -> sat;
            case R_VAL -> (val - MIN_V) / (1.0F - MIN_V);
            case R_SCALE -> (scale - EphemeraItem.SCALE_MIN) / (EphemeraItem.SCALE_MAX - EphemeraItem.SCALE_MIN);
            case R_HEIGHT -> (yOffset - EphemeraItem.Y_OFFSET_MIN) / (EphemeraItem.Y_OFFSET_MAX - EphemeraItem.Y_OFFSET_MIN);
            default -> 0.0F;
        };
    }

    /** 拖到哪就是哪，按刻度取整：色相 1°、饱和度 / 明度 1%、大小 / 高度 0.05 */
    private void dragTo(int row, double mx) {
        float u = Mth.clamp((float) (mx - tx) / (TRACK_W - 1), 0.0F, 1.0F);
        switch (row) {
            case R_HUE -> hue = Math.round(u * 360.0F) / 360.0F;
            case R_SAT -> sat = Math.round(u * 100.0F) / 100.0F;
            case R_VAL -> val = Math.max(MIN_V, Math.round((MIN_V + u * (1.0F - MIN_V)) * 100.0F) / 100.0F);
            case R_SCALE -> scale = snap(Mth.lerp(u, EphemeraItem.SCALE_MIN, EphemeraItem.SCALE_MAX));
            case R_HEIGHT -> yOffset = snap(Mth.lerp(u, EphemeraItem.Y_OFFSET_MIN, EphemeraItem.Y_OFFSET_MAX));
            default -> {
                return;
            }
        }
        if (row == R_HUE || row == R_SAT || row == R_VAL) {
            applyHsv();
        }
        changed(false);
    }

    private static float snap(float v) {
        return Math.round(v * 20.0F) / 20.0F;
    }

    private String valueText(int row) {
        return switch (row) {
            case R_HUE -> Math.round(hue * 360.0F) + "°";
            case R_SAT -> Math.round(sat * 100.0F) + "%";
            case R_VAL -> Math.round(val * 100.0F) + "%";
            case R_SCALE -> String.format(Locale.ROOT, "%.2f×", scale);
            case R_HEIGHT -> String.format(Locale.ROOT, "%+.2f", yOffset);
            default -> "";
        };
    }

    // ==================== 输入 ====================

    private boolean inside(double mx, double my) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    private static boolean in(double mx, double my, float ax0, float ay0, float ax1, float ay1) {
        return mx >= ax0 && mx < ax1 && my >= ay0 && my < ay1;
    }

    private int chipX0(int i) {
        int chipW = (cx1 - tx - COL) / 2;
        return i == 0 ? tx : cx1 - chipW;
    }

    private int chipX1(int i) {
        int chipW = (cx1 - tx - COL) / 2;
        return i == 0 ? tx + chipW : cx1;
    }

    /**
     * 渐变四格先各给够自己的字，剩下的平分（平分时英文 Vertical 会被截成 Verti…）；字太宽放不下就退回平分。
     * 左右两头和别的行对齐
     */
    private int segX0(int i) {
        int avail = cx1 - tx + SEG_GAP;
        float[] need = new float[4];
        float sum = 0.0F;
        for (int k = 0; k < 4; k++) {
            need[k] = screen.textW(tr(K + "gradient." + k), 0.0F) + 4 + SEG_GAP;
            sum += need[k];
        }
        if (sum > avail) {
            return tx + Math.round(i * avail / 4.0F);
        }
        float extra = (avail - sum) / 4.0F;
        float at = 0.0F;
        for (int k = 0; k < i; k++) {
            at += need[k] + extra;
        }
        return tx + Math.round(at);
    }

    private int segX1(int i) {
        return segX0(i + 1) - SEG_GAP;
    }

    private int presetX(int i) {
        return tx + i * (CHIP + CHIP_GAP);
    }

    private boolean onClose(double mx, double my) {
        return in(mx, my, cx1 - CLOSE - 2, titleY - 2, cx1 + 2, titleY + CLOSE + 2);
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (!inside(mx, my)) {
            close();
            return true;
        }
        boolean onHex = in(mx, my, tx, rowY[R_HEX], tx + hexW, rowY[R_HEX] + ROW);
        if (hex.isFocused() && !onHex) {
            hex.setFocused(false);
            refreshHex();
        }
        if (onHex) {
            hex.setFocused(true);
            hex.mouseClicked(mx, my, button);
            return true;
        }
        if (onClose(mx, my)) {
            close();
            return true;
        }
        if (in(mx, my, doneX0, footY, cx1, footY + BTN_H)) {
            close();
            return true;
        }
        if (in(mx, my, cx0, footY, resetX1, footY + BTN_H)) {
            reset();
            return true;
        }
        for (int row = 0; row < ROWS; row++) {
            int ry = rowY[row];
            if (my < ry || my >= ry + ROW) {
                continue;
            }
            if (isSlider(row) && mx >= tx - 2 && mx < tx + TRACK_W + 2) {
                drag = row;
                dragTo(row, mx);
                return true;
            }
            if (row == R_TARGET) {
                for (int i = 0; i < 2; i++) {
                    if (mx >= chipX0(i) && mx < chipX1(i)) {
                        target = i;
                        loadHsv();
                        refreshHex();
                    }
                }
            } else if (row == R_PRESET) {
                for (int i = 0; i < PRESETS.length; i++) {
                    if (in(mx, my, presetX(i), ry + 1, presetX(i) + CHIP, ry + 1 + CHIP)) {
                        setColor(PRESETS[i]);
                        changed(true);
                    }
                }
            } else if (row == R_GRADIENT) {
                for (int i = 0; i < 4; i++) {
                    if (mx >= segX0(i) && mx < segX1(i) && gradient != i) {
                        gradient = i;
                        changed(true);
                    }
                }
            }
            return true;
        }
        return true;
    }

    boolean mouseDragged(double mx) {
        if (drag >= 0) {
            dragTo(drag, mx);
        }
        return true;
    }

    boolean mouseReleased(double mx) {
        if (drag >= 0) {
            dragTo(drag, mx);
            drag = -1;
            changed(true);
        }
        return true;
    }

    /** 面板开着时键盘全归它：数字键换格、丢弃键都不能落到底下的格子上 */
    boolean keyPressed(int key, int scan, int mods) {
        boolean focused = hex.isFocused();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (focused) {
                hex.setFocused(false);
                refreshHex();
            } else {
                close();
            }
            return true;
        }
        if (focused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                hex.setFocused(false);
                refreshHex();
            } else {
                hex.keyPressed(key, scan, mods);
            }
            return true;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            close();
        }
        return true;
    }

    boolean charTyped(char c, int mods) {
        if (hex.isFocused()) {
            hex.charTyped(c, mods);
        }
        return true;
    }

    // ==================== 画 ====================

    void render(GuiGraphics g, int mx, int my, float partialTick) {
        List<Component> tip = new ArrayList<>();
        float lineW = GuiShapes.pixels(g, ArchivePageScreen.lw());
        GuiShapes.begin(g);
        // 人像那一栏不压暗，调的时候看得清
        ArchivePageScreen.fill(g, areaX0, 0, screen.width, screen.height, 0x000000, 0.55F);
        float[] p = {x0 + CHAMFER, y0, x1, y0, x1, y1 - CHAMFER, x1 - CHAMFER, y1, x0, y1, x0, y0 + CHAMFER};
        GuiShapes.convexFill(g, p, 6, GuiShapes.argb(ArchivePageScreen.INK, 0.95F));
        GuiShapes.polyline(g, p, 6, true, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.22F));
        ArchivePageScreen.fill(g, x0 + CHAMFER + 1, y0, x1 - 1, y0 + 1, FxPalette.KUVA, 0.9F);
        ArchivePageScreen.hl(g, cx0, cx1, ruleY, FxPalette.KUVA_PALE, 0.2F);
        ArchivePageScreen.hl(g, cx0, cx1, sectionY, FxPalette.KUVA_PALE, 0.08F);

        boolean hvClose = onClose(mx, my);
        int xc = hvClose ? FxPalette.KUVA : FxPalette.ASH;
        GuiShapes.hairline(g, cx1 - CLOSE, titleY, cx1, titleY + CLOSE, lineW, GuiShapes.argb(xc, 0.9F));
        GuiShapes.hairline(g, cx1, titleY, cx1 - CLOSE, titleY + CLOSE, lineW, GuiShapes.argb(xc, 0.9F));

        for (int i = 0; i < 2; i++) {
            int ry = rowY[R_TARGET];
            boolean sel = target == i;
            boolean hv = in(mx, my, chipX0(i), ry, chipX1(i), ry + ROW);
            if (sel) {
                ArchivePageScreen.fill(g, chipX0(i), ry, chipX1(i), ry + ROW, FxPalette.KUVA_PALE, 0.08F);
            }
            ArchivePageScreen.frame(g, chipX0(i), ry, chipX1(i), ry + ROW, FxPalette.KUVA_PALE,
                    sel ? 0.6F : hv ? 0.4F : 0.18F);
            int sx = chipX0(i) + 3;
            ArchivePageScreen.fill(g, sx, ry + 2, sx + 7, ry + 9, colors[i], 1.0F);
            ArchivePageScreen.frame(g, sx, ry + 2, sx + 7, ry + 9, FxPalette.KUVA_PALE, 0.3F);
        }

        drawColorTrack(g, R_HUE);
        drawColorTrack(g, R_SAT);
        drawColorTrack(g, R_VAL);
        drawPlainTrack(g, R_SCALE, (1.0F - EphemeraItem.SCALE_MIN) / (EphemeraItem.SCALE_MAX - EphemeraItem.SCALE_MIN));
        drawPlainTrack(g, R_HEIGHT, 0.5F);
        for (int row : new int[]{R_HUE, R_SAT, R_VAL, R_SCALE, R_HEIGHT}) {
            boolean hv = drag == row || (drag < 0 && in(mx, my, tx - 2, rowY[row], tx + TRACK_W + 2, rowY[row] + ROW));
            int kx = tx + Math.round(Mth.clamp(sliderU(row), 0.0F, 1.0F) * (TRACK_W - 1));
            int ry = rowY[row];
            ArchivePageScreen.fill(g, kx - 1, ry + 1, kx + 2, ry + 10, hv ? FxPalette.KUVA_PALE : FxPalette.BONE, 1.0F);
            ArchivePageScreen.fill(g, kx, ry + 2, kx + 1, ry + 9, ArchivePageScreen.INK, 1.0F);
        }

        int hy = rowY[R_HEX];
        ArchivePageScreen.fill(g, tx, hy, tx + hexW, hy + ROW, ArchivePageScreen.INK, 0.6F);
        ArchivePageScreen.frame(g, tx, hy, tx + hexW, hy + ROW, FxPalette.KUVA_PALE,
                hex.isFocused() ? 0.7F : in(mx, my, tx, hy, tx + hexW, hy + ROW) ? 0.45F : 0.25F);

        int py = rowY[R_PRESET] + 1;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX(i);
            boolean hv = in(mx, my, px, py, px + CHIP, py + CHIP);
            ArchivePageScreen.fill(g, px, py, px + CHIP, py + CHIP, PRESETS[i], 1.0F);
            if (colors[target] == PRESETS[i]) {
                ArchivePageScreen.frame(g, px - 1, py - 1, px + CHIP + 1, py + CHIP + 1, FxPalette.BONE, 0.95F);
            } else if (hv) {
                ArchivePageScreen.frame(g, px - 1, py - 1, px + CHIP + 1, py + CHIP + 1, FxPalette.KUVA_PALE, 0.55F);
            }
            if (hv) {
                tip.add(Component.translatable(PRESET_KEYS[i]));
            }
        }

        int gy = rowY[R_GRADIENT];
        for (int i = 0; i < 4; i++) {
            boolean sel = gradient == i;
            boolean hv = in(mx, my, segX0(i), gy, segX1(i), gy + ROW);
            if (sel) {
                ArchivePageScreen.fill(g, segX0(i), gy, segX1(i), gy + ROW, FxPalette.KUVA, 0.22F);
            }
            ArchivePageScreen.frame(g, segX0(i), gy, segX1(i), gy + ROW, sel ? FxPalette.KUVA : FxPalette.KUVA_PALE,
                    sel ? 0.8F : hv ? 0.45F : 0.18F);
            if (hv) {
                tip.add(Component.translatable(K + "gradient." + i));
                tip.add(Component.translatable(K + "gradient." + i + ".desc").withStyle(s -> s.withColor(FxPalette.ASH)));
            }
        }

        boolean hvReset = in(mx, my, cx0, footY, resetX1, footY + BTN_H);
        boolean hvDone = in(mx, my, doneX0, footY, cx1, footY + BTN_H);
        ArchivePageScreen.fill(g, cx0, footY, resetX1, footY + BTN_H, FxPalette.KUVA_PALE, hvReset ? 0.1F : 0.04F);
        ArchivePageScreen.frame(g, cx0, footY, resetX1, footY + BTN_H, FxPalette.KUVA_PALE, hvReset ? 0.55F : 0.28F);
        ArchivePageScreen.fill(g, doneX0, footY, cx1, footY + BTN_H, FxPalette.KUVA, hvDone ? 0.3F : 0.16F);
        ArchivePageScreen.frame(g, doneX0, footY, cx1, footY + BTN_H, FxPalette.KUVA, hvDone ? 0.95F : 0.7F);
        GuiShapes.end();

        screen.text(g, screen.fit(tr(K + "title", styleName), cx1 - CLOSE - COL - cx0), cx0, titleY, FxPalette.BONE, 1.0F);
        for (int row = 0; row < ROWS; row++) {
            screen.text(g, tr(K + LABELS[row]), cx0, rowY[row] + TEXT_DY, FxPalette.ASH, 0.95F);
            if (isSlider(row)) {
                // 字宽里带着末尾 1 格字距，往右补 1 格，看得见的字边才和上下几行的框对齐
                screen.textR(g, valueText(row), cx1 + 1, rowY[row] + TEXT_DY, FxPalette.TEXT_MID, 0.95F);
            }
        }
        for (int i = 0; i < 2; i++) {
            int lx = chipX0(i) + 3 + 7 + 4;
            String s = screen.fit(tr(K + (i == 0 ? "primary" : "secondary")), chipX1(i) - 3 - lx);
            screen.text(g, s, lx, rowY[R_TARGET] + TEXT_DY, target == i ? FxPalette.BONE : FxPalette.ASH, 1.0F);
        }
        for (int i = 0; i < 4; i++) {
            int w = segX1(i) - segX0(i);
            String s = screen.fit(tr(K + "gradient." + i), w - 4);
            float sw = screen.textW(s, 0.0F);
            screen.text(g, s, segX0(i) + Math.round((w - sw) * 0.5F), gy + TEXT_DY,
                    gradient == i ? FxPalette.BONE : FxPalette.ASH, 1.0F);
        }
        drawButtonText(g, tr(K + "reset"), cx0, resetX1, hvReset ? FxPalette.BONE : FxPalette.TEXT_MID);
        drawButtonText(g, tr(K + "done"), doneX0, cx1, FxPalette.BONE);
        hex.render(g, mx, my, partialTick);

        if (!tip.isEmpty()) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private void drawButtonText(GuiGraphics g, String s, int bx0, int bx1, int rgb) {
        float w = screen.textW(s, 0.0F);
        screen.text(g, s, bx0 + Math.round((bx1 - bx0 - w) * 0.5F), footY + (BTN_H - 7) / 2, rgb, 1.0F);
    }

    /** 色相 / 饱和度 / 明度三条：每一列按这一列的取值上色，看得出往哪拖会变成什么色 */
    private void drawColorTrack(GuiGraphics g, int row) {
        int ry = rowY[row];
        for (int i = 0; i < TRACK_W; i++) {
            float u = i / (float) (TRACK_W - 1);
            int rgb = switch (row) {
                case R_HUE -> Mth.hsvToRgb(Math.min(u, 0.9999F), 1.0F, 1.0F);
                case R_SAT -> Mth.hsvToRgb(Math.min(hue, 0.9999F), u, val);
                default -> Mth.hsvToRgb(Math.min(hue, 0.9999F), sat, MIN_V + u * (1.0F - MIN_V));
            };
            ArchivePageScreen.fill(g, tx + i, ry + 3, tx + i + 1, ry + 8, rgb, 1.0F);
        }
        ArchivePageScreen.frame(g, tx - 1, ry + 2, tx + TRACK_W + 1, ry + 9, FxPalette.KUVA_PALE, 0.15F);
    }

    /** 大小、高度：一条底槽，默认值处一道刻度，从默认值到当前值用主色填上 */
    private void drawPlainTrack(GuiGraphics g, int row, float defU) {
        int ry = rowY[row];
        ArchivePageScreen.fill(g, tx, ry + 4, tx + TRACK_W, ry + 7, FxPalette.KUVA_PALE, 0.12F);
        int dx = tx + Math.round(defU * (TRACK_W - 1));
        int kx = tx + Math.round(Mth.clamp(sliderU(row), 0.0F, 1.0F) * (TRACK_W - 1));
        ArchivePageScreen.fill(g, Math.min(dx, kx), ry + 4, Math.max(dx, kx) + 1, ry + 7, colors[0], 0.75F);
        ArchivePageScreen.fill(g, dx, ry + 2, dx + 1, ry + 9, FxPalette.ASH, 0.7F);
    }

    // ==================== 小工具 ====================

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0.0F;
        if (d > 0.0F) {
            if (max == r) {
                h = (g - b) / d;
                if (h < 0.0F) {
                    h += 6.0F;
                }
            } else if (max == g) {
                h = (b - r) / d + 2.0F;
            } else {
                h = (r - g) / d + 4.0F;
            }
            h /= 6.0F;
        }
        return new float[]{h, max == 0.0F ? 0.0F : d / max, max};
    }
}
