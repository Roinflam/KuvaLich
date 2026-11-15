package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.larvling.LarvlingTag;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.network.packet.AllyCommandPacket;
import pers.roinflam.kuvalich.nemesis.LichPersona;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.LichTraits;
import pers.roinflam.kuvalich.nemesis.RageTier;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * 玄骸档案的左列「宿敌卷宗」：左上一枚台阶菱形等级徽记，右边压着名字、第几只 · 性格、状态三行；
 * 下面怒气五段条，再往下一叠卷宗卡（武器、体质、弱点 · 抗性、能力、交战），卡名压在上框线上，像档案袋的标签。
 * 「被它拿走的东西」也做成同样的卡，画在右列最下面（{@link #drawTaken}）：左列在 360 字像素高的版面
 * （1080p 缩放 3、720p 缩放 2）里五张卡已经排满，再加一张会顶出屏幕底边。盟友名册同理：版面高、放得下时
 * 排在左列最后，放不下就在标题行右端留一个入口，点开居中的名册面板（{@link #drawRosterPanel}）。
 *
 * <p>全部按物理像素画：姿态缩成 1 / 界面缩放，坐标和尺寸都是整数。字号 {@link #u} = 1 字像素占几个物理像素，
 * 跟着界面缩放走，但版面至少要 360 字像素高（1080p 缩放 4 也按 3 画），最小 2（汉字的 16 点阵才不丢笔画）；
 * 开了「强制 Unicode 字体」时凑成偶数（unifont 一个点是半个字像素，奇数倍会粗细不匀）。
 * 线宽 {@link #lw} = max(1, round(屏高 / 720))。</p>
 *
 * <p>对齐：居中一律走 {@link #hmid} / {@link #vmid}（余下的奇数像素放在右边、下边）；等分格走 {@link #split}
 * （取整，段挨段不留缝）。字宽按字墨宽算（{@code font.width} 减去末字后面那 1 字像素），
 * 行高有汉字或下伸字母时 8 字像素、其余 7。</p>
 */
@OnlyIn(Dist.CLIENT)
final class ArchiveDossier {

    /** 抗性：灰蓝，和弱点的金色、元素色都拉得开 */
    private static final int RESIST = 0x8FB8CC;
    /** 物品格：底、左上暗边、右下亮边（原版格子的明暗，换成赤毒色调） */
    private static final int SLOT_INNER = 0x1E0B0F;
    private static final int SLOT_DARK = 0x0A0204;
    private static final int SLOT_LIGHT = 0x4A1F27;
    /** 怒气五档的分界（和 {@link RageTier} 一致） */
    private static final float[] RAGE_TICKS = {0.12F, 0.5F, 0.8F, 0.96F};
    /** 伤害类别的名字，顺序和档案包里 {@code affinityPct} 一致（近战、弓箭、其他弹射物、魔法） */
    private static final String[] CATEGORY_KEYS = {
            "kuvalich.archive.cat.melee", "kuvalich.archive.cat.arrow",
            "kuvalich.archive.cat.projectile", "kuvalich.archive.cat.magic"
    };

    // ==================== 小图形（'#' 亮；7×7 或 9×9 字像素） ====================

    private static final Glyph[] CATEGORY = {
            glyph(".....##", "....###", "...###.", "#.###..", ".###...", "..##...", ".#..#.."),
            glyph("...####", ".....##", "....#.#", "...#..#", "#.#....", ".##....", "###...."),
            glyph(".......", "...###.", "#.#####", "..#####", "#.#####", "...###.", "......."),
            glyph("...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#...")
    };
    /** 能力，下标和 {@link LichTraits} 的技能序号一致 */
    private static final Glyph[] ABILITY = {
            glyph("....#....", "..##.##..", ".#.....#.", ".#..#..#.", ".#.###.#.", ".#..#..#.", ".#.....#.",
                    "..##.##..", "....#...."),
            glyph("....#....", "...###...", "..##.##..", ".##...##.", "....#....", "...###...", "..##.##..",
                    ".##...##.", "........."),
            glyph("....#....", "...###...", "..#####..", ".##.####.", ".#.#####.", ".#######.", ".#######.",
                    "..#####..", "...###..."),
            glyph("..#####..", ".#.....#.", "#..###..#", "..#...#..", ".#..#..#.", "...#.#...", "....#....",
                    ".........", "........."),
            glyph("#########", "##.....##", ".##...##.", ".##...##.", "..#...#..", "..#...#..", ".........",
                    ".........", ".........")
    };
    /** 能力卡下面两行说明用的小号图形：9×9 去掉一圈边 */
    private static final Glyph ADAPT_SMALL = ABILITY[LichTraits.ADAPT].crop();
    private static final Glyph FURY_SMALL = ABILITY[LichTraits.FURY].crop();

    private static final Glyph HEALTH = glyph(".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#...");
    private static final Glyph ARMOR = glyph("#######", "#..#..#", "#..#..#", "#..#..#", ".#.#.#.", "..###..", "...#...");
    private static final Glyph HIT = glyph("......#", ".....##", "....##.", "...##..", "..##...", ".##....", "#......");
    private static final Glyph BIRTH = glyph("..###..", ".#####.", "##...##", "##...##", ".#####.", "..###..", "...#...");
    private static final Glyph ENCOUNTERS = glyph("#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", "##...##",
            "##...##");
    private static final Glyph DEALT = glyph("...#...", "...#...", ".#.#.#.", "..###..", "...#...", ".......", "#######");
    private static final Glyph TAKEN = glyph("#######", ".......", "...#...", "..###..", ".#.#.#.", "...#...", "...#...");
    private static final Glyph RAGE = glyph("...#...", "..##...", "..###.#", ".#####.", ".##.##.", ".#...#.", "..###..");
    // 状态
    private static final Glyph EYE = glyph(".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", ".......");
    private static final Glyph MOON = glyph("..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###..");
    private static final Glyph INCOMING = glyph("#..#...", ".#..#..", "..#..#.", "...#..#", "..#..#.", ".#..#..",
            "#..#...");
    private static final Glyph PRESENT = glyph("..##...", "..##...", "..##...", "..##...", ".......", "..##...", "..##...");
    private static final Glyph DOWNED = glyph("#.....#", ".#...#.", "..#.#..", "...#...", ".......", "#######", ".......");
    private static final Glyph UNBORN = glyph("...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#...");
    private static final Glyph PAUSED = glyph(".##.##.", ".##.##.", ".##.##.", ".##.##.", ".##.##.", ".##.##.", ".##.##.");

    private static final int VITALS = 3;
    private static final Glyph[] VITAL_GLYPH = {HEALTH, ARMOR, HIT};
    private static final int[] VITAL_RGB = {FxPalette.KUVA, RESIST, FxPalette.ACCENT};

    // ---- 左列的卡 ----
    private static final int C_WEAPON = 0;
    private static final int C_VITAL = 1;
    private static final int C_AFFINITY = 2;
    private static final int C_ABILITY = 3;
    private static final int C_FIGHT = 4;
    /** 盟友名册：左列放得下时排在交战后面，放不下就收成标题行右端的入口，点开面板 */
    private static final int C_ROSTER = 5;
    private static final int CARD_KINDS = 6;

    private static final Glyph ACTIVE = glyph("#......", "###....", "#####..", "#######", "#####..", "###....", "#......");
    private static final Glyph EPHEMERA = glyph("...#...", "...#...", "..###..", "###.###", "..###..", "...#...", "...#...");
    /** 遣散要在这么久之内再点一次 */
    private static final long CONFIRM_MS = 3000L;

    /** 名册里的操作交给界面去发（调试假数据时界面自己改假数据） */
    interface RosterActions {
        void command(int action, int generation);
    }

    // ==================== 这一帧的版面（物理像素） ====================

    private GuiGraphics g;
    private Font font;
    private float gs = 1.0F;
    private boolean evenOnly;
    /** 1 字像素 = 几个物理像素 */
    int u = 2;
    /** 细线粗细（物理像素） */
    int lw = 1;
    private int screenW;
    private int screenH;
    private int top;
    private int bottom;
    /** 左列左边、右边（物理像素） */
    private int l0;
    private int l1;
    private int mxp;
    private int myp;
    /** 上一张 {@link #cardFrame} 的内容区 */
    private int cx;
    private int cy;
    private int cw;
    private final int[] kinds = new int[CARD_KINDS];

    // ---- 「被它拿走的东西」能点开的地方（物理像素，每帧重算；宽 0 = 这一帧没有） ----
    private int moreX;
    private int moreY;
    private int moreW;
    private int moreH;
    /** 卡里画出了几件（卡名上的悬停也写「还有几件」） */
    private int moreShown;
    private int legX;
    private int legY;
    private int legW;
    private int legH;

    // ---- 「查看全部」面板 ----
    /** 面板开着没有 */
    boolean takenOpen;
    private long takenOpenMs;
    /** 最上面露出的是第几行；窗口改了大小、列数变了时按 {@link #takenFirst} 重新换算 */
    private int takenScroll;
    /** 最上面一行第一件的下标（滚动位置按物品记，列数变了也停在同一批物品上） */
    private int takenFirst;
    /** 上一帧面板的外框（含卡名那一行）、关闭键、滚动条（物理像素）；面板没画出来时宽 0 */
    private int panX;
    private int panY;
    private int panW;
    private int panH;
    private int closeX;
    private int closeY;
    private int closeS;
    private int barX;
    private int barY;
    private int barH;
    private int thumbH;
    /** 滚动条能滚几行（0 = 不用滚） */
    private int maxScroll;
    private boolean barDrag;

    // ---- 盟友名册（物理像素，每帧重算） ----
    @Nullable
    RosterActions rosterActions;
    /** 这一帧名册排在左列里（否则标题行右端是入口） */
    private boolean rosterInline;
    private int rowCount;
    private final int[] rowX = new int[LichArchivePacket.MAX_ALLIES];
    private final int[] rowY = new int[LichArchivePacket.MAX_ALLIES];
    private final int[] rowW = new int[LichArchivePacket.MAX_ALLIES];
    private final int[] rowGen = new int[LichArchivePacket.MAX_ALLIES];
    private final boolean[] rowActive = new boolean[LichArchivePacket.MAX_ALLIES];
    /** 行尾遣散键的左上角，键是 {@link #hitS} 见方 */
    private final int[] relX = new int[LichArchivePacket.MAX_ALLIES];
    private final int[] relY = new int[LichArchivePacket.MAX_ALLIES];
    private int hitS;
    private int entryX;
    private int entryY;
    private int entryW;
    private int entryH;
    /** 等着第二次点击确认遣散的那一代，-1 = 没有 */
    private int confirmGen = -1;
    private long confirmUntil;
    boolean rosterOpen;
    private long rosterOpenMs;
    private int rpX;
    private int rpY;
    private int rpW;
    private int rpH;
    private int rcX;
    private int rcY;
    private int rcS;

    /** 这一次调用里鼠标停在哪：提示行、或者一件物品（物品优先） */
    @Nullable
    List<Component> hoverLines;
    ItemStack hoverStack = ItemStack.EMPTY;
    /** 悬停的那件被没收物品在包里去掉过 NBT（提示框末尾补一行说明） */
    boolean hoverSlim;

    // ==================== 随档案和语言变的字 ====================

    private boolean textsBuilt;
    @Nullable
    private LichArchivePacket.Data textsFor;
    private String textsLang = "";
    private String legWeapon = "";
    private String legVital = "";
    private String legWeak = "";
    private String legResist = "";
    private String legAbility = "";
    private String legFight = "";
    private String taken = "";
    private String wName = "";
    private String wLevel = "";
    @Nullable
    private String wTip;
    private String wElemName = "";
    private int wElemRgb;
    @Nullable
    private int[] wElemMask;
    private int wLines;
    /** 「幻纹：款名」，不带为空 */
    private String wEphemera = "";
    private int wEphemeraRgb;
    private final String[] vitalValue = new String[VITALS];
    private final String[] vitalCap = new String[VITALS];
    private final String[] multText = new String[LichTraits.CATEGORIES];
    private final String[] catTip = new String[LichTraits.CATEGORIES];
    private int extraN;
    private final Glyph[] extraGlyph = new Glyph[2];
    private final String[] extraText = new String[2];
    private int fightN;
    private final Glyph[] fightGlyph = new Glyph[4];
    private final int[] fightRgb = new int[4];
    private final String[] fightText = new String[4];

    // ==================== 版面 ====================

    /** 按窗口算这一帧的字号、线宽和左列的左右边；{@code small} 是窗口太小、左右两栏各占一半的排法 */
    void layout(Minecraft mc, boolean small) {
        Window win = mc.getWindow();
        screenW = win.getWidth();
        screenH = win.getHeight();
        gs = (float) win.getGuiScale();
        evenOnly = mc.options.forceUnicodeFont().get();
        int p = Mth.clamp(Math.max(1, Math.round(gs)), 2, Math.max(2, screenH / 360));
        if (evenOnly && (p & 1) == 1) {
            p = Math.max(2, p - 1);
        }
        u = p;
        lw = GuiShapes.lineWidthPx();
        moreW = 0;
        legW = 0;
        rowCount = 0;
        entryW = 0;
        top =Math.max(10 * u, snap(screenH * 0.035F));
        bottom = screenH - top;
        if (small) {
            l0 = Math.round(screenW * 0.04F);
            l1 = l0 + Math.round(screenW * 0.44F);
        } else {
            l0 = Math.max(12 * u, snap(screenW * 0.03F));
            l1 = l0 + Mth.clamp(snap(screenW * 0.28F), 180 * u, 236 * u);
        }
    }

    /** 左列右边，换成界面坐标（拖模型的范围、字幕的宽度要躲开它） */
    float leftEdgeGui() {
        return l1 / gs;
    }

    // ==================== 左列 ====================

    /**
     * 画左列：标题一行跟着框架淡入（{@code aFrame}），其余跟着模型最后出来（{@code aBody}）
     *
     * @param known 收到过档案或状态（没收到过时名字处写「……」，其余不画）
     */
    void drawLeft(GuiGraphics graphics, Font f, int mx, int my, LichStatePacket st, boolean lich, boolean known,
                  @Nullable LichArchivePacket.Data data, long now, float aFrame, float aBody) {
        begin(graphics, f, mx, my, data);
        try {
            rosterInline = known && data != null && rosterFitsLeft(lich, data);
            if (rosterInline) {
                rosterOpen = false;
            }
            if (Util.getMillis() > confirmUntil) {
                confirmGen = -1;
            }
            if (aFrame > 0.01F) {
                drawTitle(aFrame, known && !rosterInline ? data : null);
            }
            if (aBody > 0.01F) {
                drawBody(st, lich, known, data, now, aBody);
            }
        } finally {
            finish();
        }
    }

    /** 标题：一颗小菱形、「玄骸档案」，后面一条细线拖到左列右边 */
    private void drawTitle(float a, @Nullable LichArchivePacket.Data rosterEntry) {
        diamond(l0, top + u, 3, FxPalette.KUVA, -1, a);
        String title = I18n.get("kuvalich.archive.title");
        int tx = l0 + 9 * u;
        int tw = text(title, tx, top, u, FxPalette.TEXT_MID, a);
        int lineEnd = l1;
        if (rosterEntry != null) {
            // 名册在左列放不下：标题行右端放一个入口，右边对齐左列右边线，细线让到它左边 4 字像素。
            // 不用卡名那种全角括号：全角右括号的字形右半边是空的，右对齐时看着差半个字
            String full = I18n.get("kuvalich.archive.roster.entry", rosterEntry.roster().size(), rosterEntry.rosterCap());
            int room = l1 - (tx + tw + 12 * u) - 10 * u;
            String s = fit(full, Math.max(1, room / u));
            int sw = 10 * u + inkW(s) * u;
            int ex = l1 - sw;
            boolean hot = in(ex - 2 * u, top - 2 * u, sw + 4 * u, 12 * u);
            int rgb = hot ? FxPalette.KUVA : FxPalette.TEXT_MID;
            glyphLine(ACTIVE, hot ? FxPalette.KUVA : FxPalette.KUVA_PALE, s, rgb, ex, top, 8 * u, a);
            if (a >= 0.5F) {
                entryX = ex - 2 * u;
                entryY = top - 2 * u;
                entryW = sw + 4 * u;
                entryH = 12 * u;
            }
            if (hot) {
                hoverLines = List.of(Component.literal(full),
                        Component.translatable("kuvalich.archive.roster.open").withStyle(ChatFormatting.GRAY));
            }
            lineEnd = ex - 4 * u;
        }
        if (lineEnd > tx + tw + 4 * u) {
            hline(tx + tw + 4 * u, lineEnd, top + 4 * u, FxPalette.KUVA_PALE, 0.3F * a);
        }
    }

    private void drawBody(LichStatePacket st, boolean lich, boolean known, @Nullable LichArchivePacket.Data data,
                          long now, float a) {
        // ---- 徽记 + 名字、第几只、状态 ----
        int hy = top + 14 * u;
        int bn = 18;
        int badge = 2 * bn * u;
        String rank = !known ? LichArchiveScreen.unknown() : lich ? Integer.toString(st.rank) : "—";
        drawBadge(l0, hy, bn, lich, rank, a);
        if (lich && data != null && in(l0, hy, badge, badge)) {
            hoverLines = LichArchiveScreen.rankTooltip(st.rank, data);
        }

        int nx = l0 + badge + 8 * u;
        int nameMax = l1 - nx;
        String line1;
        int k1;
        int nameRgb;
        if (!known) {
            line1 = LichArchiveScreen.unknown();
            k1 = 2 * u;
            nameRgb = FxPalette.KUVA;
        } else if (lich) {
            line1 = LichArchiveScreen.upper(st.name);
            // 放得下两倍就两倍，放不下取能放下的最大整数倍（最小一倍），大名字不超出左列右边线
            int ink = Math.max(1, inkW(line1));
            k1 = Mth.clamp(nameMax / ink, u, 2 * u);
            if (evenOnly && (k1 & 1) == 1 && k1 > u) {
                k1--;
            }
            nameRgb = FxPalette.KUVA;
        } else {
            line1 = I18n.get("kuvalich.lich.state.unborn");
            k1 = 2 * u;
            nameRgb = FxPalette.TEXT_MID;
        }
        String name = fit(line1, nameMax / k1);
        // 名字、第几只、状态三行一起在徽记高度里竖直居中
        int h1 = textH(name) * k1;
        int blockH = h1 + 4 * u + 8 * u + 3 * u + 8 * u;
        int by = vmid(hy, badge, blockH);
        text(name, nx, by, k1, nameRgb, a);
        if (name != line1 && in(nx, by, inkW(name) * k1, h1)) {
            hoverLines = List.of(Component.literal(line1));
        }
        int y2 = by + h1 + 4 * u;
        int y3 = y2 + 11 * u;
        if (known && lich) {
            LichPersona persona = LichPersona.of(st.seed);
            String head = I18n.get("kuvalich.archive.generation", Math.max(1, st.generation)) + " · ";
            text(head, nx, y2, u, FxPalette.ASH, a);
            int px = nx + font.width(head) * u;
            String per = I18n.get("kuvalich.archive.persona." + persona.id);
            text(per, px, y2, u, persona == LichPersona.JOVIAL ? FxPalette.KUVA_HOT : FxPalette.TECH, a);
            if (data != null && LichTraits.unpackVaried(data.stats().skills()) && in(px, y2, inkW(per) * u, 8 * u)) {
                String key = "kuvalich.archive.persona." + persona.id + ".tooltip.";
                hoverLines = List.of(Component.translatable(key + "0"), Component.translatable(key + "1"));
            }
        } else if (known) {
            text(fit(I18n.get("kuvalich.archive.status.unborn"), nameMax / u), nx, y2, u, FxPalette.TEXT_MID, a);
        }
        if (known) {
            String full = lich ? LichArchiveScreen.statusLine(st) : I18n.get("kuvalich.archive.status.unborn.hint");
            int rgb = lich ? (statusHot(st.state) ? FxPalette.KUVA : FxPalette.TEXT_MID) : FxPalette.ASH;
            String s = fit(full, (nameMax - 10 * u) / u);
            glyphLine(lich ? statusGlyph(st) : UNBORN, rgb, s, rgb, nx, y3, 8 * u, a);
            if (s != full && in(nx, y3, nameMax, 8 * u)) {
                hoverLines = List.of(Component.literal(full));
            }
        }

        // ---- 怒气 + 卷宗卡 ----
        int y = hy + badge + 6 * u;
        if (known && lich) {
            drawRage(st, data, y, now, a);
            y += 16 * u + 6 * u;
            if (data != null) {
                ensureTexts(data);
                drawCards(data, y, a);
            }
        } else if (known && data != null) {
            if (hasHistory(data)) {
                // 放不下时截名字，结局那几个字留着
                String key = historyKey(data.lastFate());
                String last = LichArchiveScreen.upper(data.lastName());
                String full = I18n.get(key, last);
                int maxW = (l1 - l0) / u;
                String s = inkW(full) <= maxW ? full
                        : I18n.get(key, fit(last, Math.max(1, maxW - inkW(I18n.get(key, "")) - 1)));
                text(fit(s, maxW), l0, y, u, FxPalette.ASH, a);
                if (s != full && in(l0, y, l1 - l0, 8 * u)) {
                    hoverLines = List.of(Component.literal(full));
                }
            }
            if (rosterInline) {
                rosterCard(l0, unbornRosterY(data), l1 - l0, data, a);
            }
        }
    }

    private static boolean hasHistory(LichArchivePacket.Data d) {
        return !d.lastName().isEmpty() && d.lastFate() > 0;
    }

    private static String historyKey(int fate) {
        return switch (fate) {
            case 2 -> "kuvalich.archive.history.reset";
            case 3 -> "kuvalich.archive.history.converted";
            default -> "kuvalich.archive.history.vanquished";
        };
    }

    /** 名册卡顶边：没有玄骸时在「上一只」那行下面空 8 字像素，没有那行就从那行的位置起 */
    private int unbornRosterY(LichArchivePacket.Data d) {
        int y = top + 14 * u + 36 * u + 6 * u;
        return hasHistory(d) ? y + 16 * u : y;
    }

    /** 名册在左列放不放得下：有玄骸时五张卡按最小间距排完还放得下名册卡，没有玄骸时「上一只」下面放得下 */
    private boolean rosterFitsLeft(boolean lich, LichArchivePacket.Data d) {
        int h = rosterCardH(d);
        if (!lich) {
            return unbornRosterY(d) + h <= bottom;
        }
        int y = top + 14 * u + 36 * u + 6 * u + 22 * u;
        int n = 0;
        kinds[n++] = C_WEAPON;
        kinds[n++] = C_VITAL;
        kinds[n++] = C_AFFINITY;
        kinds[n++] = C_ABILITY;
        kinds[n++] = C_FIGHT;
        return cardsHeight(d, n) + h + n * 4 * u <= bottom - y;
    }

    /** 台阶菱形徽记（外接 2n × 2n 字像素）+ 往里缩 3 格的内圈；数字 1~2 位用两倍字，3 位起一倍，按字墨宽居中 */
    private void drawBadge(int x, int y, int n, boolean lich, String s, float a) {
        int size = 2 * n * u;
        diamond(x, y, n, lich ? FxPalette.KUVA_DEEP : FxPalette.KUVA_MIST, -1, a);
        diamond(x, y, n, -1, lich ? FxPalette.KUVA_PALE : FxPalette.ASH, a);
        diamond(x + 3 * u, y + 3 * u, n - 3, -1, FxPalette.KUVA_PALE, 0.5F * a);
        int k = s.length() <= 2 && inkW(s) * 2 * u <= size - 10 * u ? 2 * u : u;
        text(s, hmid(x, size, inkW(s) * k), vmid(y, size, textH(s) * k), k, lich ? FxPalette.BONE : FxPalette.ASH, a);
    }

    /** 怒气一行（图形 + 「怒气」、右边档位字和「2 / 4」）+ 下面五段条，每段按这一档里的进度填 */
    private void drawRage(LichStatePacket st, @Nullable LichArchivePacket.Data data, int y, long now, float a) {
        int colW = l1 - l0;
        glyphLine(RAGE, FxPalette.KUVA, I18n.get("kuvalich.archive.rage"), FxPalette.ASH, l0, y, 8 * u, a);
        int tier = Mth.clamp(st.tier, 0, RageTier.values().length - 1);
        String cnt = tier + " / " + (RageTier.values().length - 1);
        int cntW = inkW(cnt) * u;
        text(cnt, l1 - cntW, vmid(y, 8 * u, 7 * u), u, FxPalette.ASH, a);
        String word = I18n.get(RageTier.values()[tier].langKey());
        text(word, l1 - cntW - 6 * u - inkW(word) * u, vmid(y, 8 * u, textH(word) * u), u,
                tier >= 3 ? FxPalette.KUVA : FxPalette.BONE, a);

        int barY = y + 11 * u;
        int barH = 5 * u;
        int gap = u;
        float r = Mth.clamp(st.ragePermille / 1000.0F, 0.0F, 1.0F);
        int filling = 0;
        for (float t : RAGE_TICKS) {
            if (r >= t) {
                filling++;
            }
        }
        float pulse = 1.0F;
        if (st.state == LichState.INCOMING) {
            pulse = 0.8F + 0.2F * (0.5F + 0.5F * Mth.sin((now % 1600L) / 1600.0F * Mth.TWO_PI));
        }
        for (int i = 0; i < 5; i++) {
            int x0 = l0 + split(colW + gap, 5, i);
            int x1 = l0 + split(colW + gap, 5, i + 1) - gap;
            float lo = i == 0 ? 0.0F : RAGE_TICKS[i - 1];
            float hi = i == 4 ? 1.0F : RAGE_TICKS[i];
            float f = Mth.clamp((r - lo) / (hi - lo), 0.0F, 1.0F);
            rect(x0, barY, x1 - x0, barH, FxPalette.KUVA_MIST, 0.7F * a);
            int fw = Math.round((x1 - x0) * f);
            if (fw > 0) {
                rect(x0, barY, fw, barH, i == filling && f < 1.0F ? FxPalette.KUVA : FxPalette.KUVA_DEEP, pulse * a);
            }
        }
        if (in(l0, barY - 10 * u, colW, barH + 10 * u)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("kuvalich.archive.rage.tooltip.head"));
            int src = data != null ? data.rageSources() : 0;
            if (src != 0) {
                lines.add(Component.translatable("kuvalich.archive.rage.tooltip.sources"));
                String[] keys = {"thrall", "progress", "reveal", "time"};
                for (int i = 0; i < 4; i++) {
                    if ((src & (1 << i)) != 0) {
                        lines.add(Component.literal("· ").append(Component.translatable("kuvalich.archive.rage.src." + keys[i])));
                    }
                }
            }
            hoverLines = lines;
        }
    }

    /**
     * 一列卷宗卡往下排：卡间距在 4~10 字像素之间按剩余空间放开；窗口太矮放不下时从最后一张起不画
     * （正常的 360 字像素高版面里五张都放得下）
     */
    private void drawCards(LichArchivePacket.Data d, int y, float a) {
        int n = 0;
        kinds[n++] = C_WEAPON;
        kinds[n++] = C_VITAL;
        kinds[n++] = C_AFFINITY;
        kinds[n++] = C_ABILITY;
        kinds[n++] = C_FIGHT;
        if (rosterInline) {
            kinds[n++] = C_ROSTER;
        }
        int avail = bottom - y;
        int total = cardsHeight(d, n);
        while (n > 1 && total + (n - 1) * 4 * u > avail) {
            n--;
            total = cardsHeight(d, n);
        }
        int gap = n > 1 ? Mth.clamp(Math.floorDiv(Math.floorDiv(avail - total, n - 1), u) * u, 4 * u, 10 * u) : 0;
        int colW = l1 - l0;
        int yy = y;
        for (int i = 0; i < n; i++) {
            int kind = kinds[i];
            switch (kind) {
                case C_WEAPON -> weaponCard(l0, yy, colW, d, a);
                case C_VITAL -> vitalCard(l0, yy, colW, a);
                case C_AFFINITY -> affinityCard(l0, yy, colW, d, a);
                case C_ABILITY -> abilityCard(l0, yy, colW, d, a);
                case C_FIGHT -> fightCard(l0, yy, colW, a);
                default -> rosterCard(l0, yy, colW, d, a);
            }
            yy += cardHeight(d, kind) + gap;
        }
    }

    private int cardsHeight(LichArchivePacket.Data d, int n) {
        int s = 0;
        for (int i = 0; i < n; i++) {
            s += cardHeight(d, kinds[i]);
        }
        return s;
    }

    /** 卡的外框高：压在上框线上的卡名 4、上框线到内容 7、内容、底边 5（字像素） */
    private int cardHeight(LichArchivePacket.Data d, int kind) {
        return switch (kind) {
            case C_WEAPON -> 16 * u + weaponContentH();
            case C_VITAL -> 16 * u + 20 * u;
            case C_AFFINITY -> 16 * u + 18 * u;
            case C_ABILITY -> 16 * u + abilityRowH(d) + extraH();
            case C_FIGHT -> 16 * u + fightN * 8 * u + Math.max(0, fightN - 1) * 4 * u;
            default -> rosterCardH(d);
        };
    }

    /** 武器卡的内容高：图标格 34 字像素，右边的字多到四行（带幻纹）时按字撑高 */
    private int weaponContentH() {
        return Math.max(34 * u, 8 * u + (weaponRows() - 1) * 11 * u);
    }

    private int weaponRows() {
        return wLines + (wLines > 0 && !wEphemera.isEmpty() ? 1 : 0);
    }

    private int abilityRowH(LichArchivePacket.Data d) {
        return 13 * u + (LichTraits.unpackVaried(d.stats().skills()) ? 4 * u : 0);
    }

    private int extraH() {
        return extraN > 0 ? 4 * u + extraN * 8 * u + (extraN - 1) * 4 * u : 0;
    }

    /**
     * 卡的外框，记下内容区（{@link #cx} / {@link #cy} / {@link #cw}）。卡名最多三段（不同颜色），段间空 4 字像素。
     * 淡框：细框、四角短刻度、极淡的底都压得很淡（右栏现状没有框，压淡了才不抢），上框线在卡名处断开，左上一小段赤毒色
     */
    private void cardFrame(int x, int y, int w, int contentH, float a, String s0, int c0,
                           @Nullable String s1, int c1, @Nullable String s2, int c2) {
        cardFrame(x, y, w, contentH, a, s0, c0, s1, c1, s2, c2, 0);
    }

    /**
     * 同上；{@code gapR} 大于 0 时上框线右端空出这么宽的一个缺口（离右边 6 字像素，和左边卡名一样两头各空 2 字像素），
     * 给面板的关闭键
     */
    private void cardFrame(int x, int y, int w, int contentH, float a, String s0, int c0,
                           @Nullable String s1, int c1, @Nullable String s2, int c2, int gapR) {
        int lineY = y + 4 * u;
        int legW = inkW(s0) * u + (s1 != null ? 4 * u + inkW(s1) * u : 0) + (s2 != null ? 4 * u + inkW(s2) * u : 0);
        int fh = 16 * u + contentH - 4 * u;
        int edge = FxPalette.KUVA_PALE;
        rect(x, lineY, w, fh, FxPalette.INK, 0.3F * a);
        hline(x, x + 4 * u, lineY, edge, 0.09F * a);
        if (gapR > 0) {
            hline(x + 8 * u + legW, x + w - 8 * u - gapR, lineY, edge, 0.09F * a);
            hline(x + w - 4 * u, x + w, lineY, edge, 0.09F * a);
        } else {
            hline(x + 8 * u + legW, x + w, lineY, edge, 0.09F * a);
        }
        hline(x, x + w, lineY + fh - lw, edge, 0.09F * a);
        vline(x, lineY + lw, lineY + fh - lw, edge, 0.09F * a);
        vline(x + w - lw, lineY + lw, lineY + fh - lw, edge, 0.09F * a);
        corners(x, lineY, w, fh, 3 * u, edge, 0.3F * a);
        rect(x, lineY, 2 * u, 6 * u, FxPalette.KUVA, 0.6F * a);
        int lx = x + 6 * u;
        cx = lx;
        cw = w - 12 * u;
        int tx = lx + text(s0, lx, y, u, c0, a);
        if (s1 != null) {
            tx += 4 * u;
            tx += text(s1, tx, y, u, c1, a);
        }
        if (s2 != null) {
            text(s2, tx + 4 * u, y, u, c2, a);
        }
        cy = lineY + 7 * u;
    }

    /** 武器：左边一格放物品图标（32 字像素见方），右边名字、等级、元素；诞生时定死的那一把才有元素这一行 */
    private void weaponCard(int x, int y, int w, LichArchivePacket.Data d, float a) {
        int icon = 32 * u;
        int box = icon + 2 * u;
        int ch = weaponContentH();
        cardFrame(x, y, w, ch, a, legWeapon, FxPalette.ASH, null, 0, null, 0);
        int by = vmid(cy, ch, box);
        rect(cx, by, box, box, FxPalette.KUVA_MIST, 0.55F * a);
        frame(cx, by, box, box, FxPalette.KUVA_PALE, 0.35F * a);
        ItemStack stack = d.weaponStack();
        if (stack.isEmpty()) {
            return;
        }
        int tx = cx + box + 6 * u;
        int tw = cx + cw - tx;
        int rows = weaponRows();
        int bh = 8 * u + (rows - 1) * 11 * u;
        int ty = vmid(cy, ch, bh);
        String nm = fit(wName, tw / u);
        text(nm, tx, ty, u, FxPalette.BONE, a);
        if (wLines >= 2) {
            text(fit(wLevel, tw / u), tx, ty + 11 * u, u, FxPalette.TEXT_MID, a);
        }
        if (wLines >= 3 && wElemMask != null) {
            glyphLine(wElemMask, wElemRgb, fit(wElemName, (tw - 10 * u) / u), wElemRgb, tx, ty + 22 * u, 8 * u, a);
        }
        if (rows > wLines) {
            int ey = ty + wLines * 11 * u;
            String es = fit(wEphemera, (tw - 10 * u) / u);
            glyphLine(EPHEMERA, wEphemeraRgb, es, wEphemeraRgb, tx, ey, 8 * u, a);
            if (in(tx, ey, tw, 8 * u)) {
                hoverLines = List.of(Component.literal(wEphemera),
                        Component.translatable("kuvalich.archive.ephemera.tooltip").withStyle(ChatFormatting.GRAY));
            }
        }
        item(stack, cx + u, by + u, icon, false, a);
        if (in(cx, by, box, box)) {
            hoverLines = wTip != null ? List.of(Component.literal(wName), Component.literal(wTip))
                    : List.of(Component.literal(wName));
        } else if (nm != wName && in(tx, ty, tw, 8 * u)) {
            hoverLines = List.of(Component.literal(wName));
        }
    }

    /** 体质：生命、护甲、一刀三个数，各占三等分的一格，图形 + 数在上、小标签在下，都在格里居中 */
    private void vitalCard(int x, int y, int w, float a) {
        cardFrame(x, y, w, 20 * u, a, legVital, FxPalette.ASH, null, 0, null, 0);
        for (int i = 0; i < VITALS; i++) {
            int sl = cx + split(cw, VITALS, i);
            int sw = split(cw, VITALS, i + 1) - split(cw, VITALS, i);
            String val = vitalValue[i];
            int gx = hmid(sl, sw, 10 * u + inkW(val) * u);
            glyphLine(VITAL_GLYPH[i], VITAL_RGB[i], val, FxPalette.BONE, gx, cy, 8 * u, a);
            String cap = vitalCap[i];
            text(cap, hmid(sl, sw, inkW(cap) * u), cy + 12 * u, u, FxPalette.ASH, a);
        }
    }

    /**
     * 弱点 · 抗性：四类伤害各占一格，图形在上、倍率在下；倍率前一个小三角（弱点朝上、抗性朝下），
     * 三角 5 格 + 2 格空 + 字一起居中。弱点金、抗性灰蓝，一倍的压暗
     */
    private void affinityCard(int x, int y, int w, LichArchivePacket.Data d, float a) {
        cardFrame(x, y, w, 18 * u, a, legWeak, FxPalette.ACCENT, "·", FxPalette.ASH, legResist, RESIST);
        int[] pct = d.stats().affinityPct();
        for (int i = 0; i < LichTraits.CATEGORIES; i++) {
            int sl = cx + split(cw, LichTraits.CATEGORIES, i);
            int sw = split(cw, LichTraits.CATEGORIES, i + 1) - split(cw, LichTraits.CATEGORIES, i);
            int p = i < pct.length ? pct[i] : 100;
            boolean weak = p > 100;
            boolean res = p < 100;
            int rgb = weak ? FxPalette.ACCENT : res ? RESIST : FxPalette.ASH;
            float al = weak || res ? 1.0F : 0.45F;
            glyph(CATEGORY[i], hmid(sl, sw, 7 * u), cy, rgb, al * a);
            String s = multText[i];
            int tri = weak || res ? 7 * u : 0;
            int gx = hmid(sl, sw, tri + inkW(s) * u);
            int ty = cy + 10 * u;
            if (weak) {
                rect(gx + 2 * u, ty + 2 * u, u, u, rgb, a);
                rect(gx + u, ty + 3 * u, 3 * u, u, rgb, a);
                rect(gx, ty + 4 * u, 5 * u, u, rgb, a);
            } else if (res) {
                rect(gx, ty + 2 * u, 5 * u, u, rgb, a);
                rect(gx + u, ty + 3 * u, 3 * u, u, rgb, a);
                rect(gx + 2 * u, ty + 4 * u, u, u, rgb, a);
            }
            text(s, gx + tri, ty, u, rgb, al * a);
            if (in(sl, cy, sw, 18 * u)) {
                hoverLines = List.of(Component.literal(catTip[i]));
            }
        }
    }

    /**
     * 能力：一排方块（13 字像素见方、9×9 图形居中），各在等分格里居中；面板差异化时下面三个档位点
     * （弱 1 / 中 2 / 强和专长 3），专长那块深红底 + 赤毒描边。下面再写适应、交战增幅的具体数
     */
    private void abilityCard(int x, int y, int w, LichArchivePacket.Data d, float a) {
        LichArchivePacket.Stats stats = d.stats();
        boolean varied = LichTraits.unpackVaried(stats.skills());
        boolean fury = stats.furyPermille() > 0;
        int rowH = abilityRowH(d);
        cardFrame(x, y, w, rowH + extraH(), a, legAbility, FxPalette.ASH, null, 0, null, 0);
        int count = fury ? LichTraits.SKILLS : LichTraits.SKILLS - 1;
        int tile = 13 * u;
        int j = 0;
        for (int abi = 0; abi < LichTraits.SKILLS; abi++) {
            if (abi == LichTraits.FURY && !fury) {
                continue;
            }
            int sl = cx + split(cw, count, j);
            int sw = split(cw, count, j + 1) - split(cw, count, j);
            j++;
            int tx = hmid(sl, sw, tile);
            int tier = LichTraits.unpackTier(stats.skills(), abi);
            boolean sig = varied && tier == LichTraits.SIGNATURE;
            rect(tx, cy, tile, tile, sig ? FxPalette.KUVA_DEEP : FxPalette.KUVA_MIST, (sig ? 0.95F : 0.6F) * a);
            if (sig) {
                frame(tx, cy, tile, tile, FxPalette.KUVA, a);
            }
            glyph(ABILITY[abi], hmid(tx, tile, 9 * u), vmid(cy, tile, 9 * u), FxPalette.KUVA_PALE, a);
            if (varied) {
                int lit = Math.min(3, tier + 1);
                int pw = 2 * u;
                int pg = u;
                int px = hmid(tx, tile, 3 * pw + 2 * pg);
                int py = cy + tile + 2 * u;
                for (int k = 0; k < 3; k++) {
                    rect(px + k * (pw + pg), py, pw, pw,
                            k < lit ? (sig ? FxPalette.KUVA : FxPalette.KUVA_PALE) : FxPalette.KUVA_MIST, a);
                }
            }
            if (in(tx, cy, tile, tile + 4 * u)) {
                hoverLines = LichArchiveScreen.abilityTooltip(abi, stats, varied);
            }
        }
        int ey = cy + rowH + 4 * u;
        for (int i = 0; i < extraN; i++) {
            String s = fit(extraText[i], (cw - 10 * u) / u);
            glyphLine(extraGlyph[i], FxPalette.KUVA_PALE, s, FxPalette.TEXT_MID, cx, ey, 8 * u, a);
            if (s != extraText[i] && in(cx, ey, cw, 8 * u)) {
                hoverLines = List.of(Component.literal(extraText[i]));
            }
            ey += 12 * u;
        }
    }

    /** 交战：出生地、交手几次、你对它 / 它对你的伤害，一行一个小图形 */
    private void fightCard(int x, int y, int w, float a) {
        int rh = fightN * 8 * u + Math.max(0, fightN - 1) * 4 * u;
        cardFrame(x, y, w, rh, a, legFight, FxPalette.ASH, null, 0, null, 0);
        for (int i = 0; i < fightN; i++) {
            int ry = cy + i * 12 * u;
            String s = fit(fightText[i], (cw - 10 * u) / u);
            glyphLine(fightGlyph[i], fightRgb[i], s, FxPalette.TEXT_MID, cx, ry, 8 * u, a);
            if (s != fightText[i] && in(cx, ry, cw, 8 * u)) {
                hoverLines = List.of(Component.literal(fightText[i]));
            }
        }
    }

    // ==================== 盟友名册 ====================

    private String rosterLegend(LichArchivePacket.Data d) {
        return I18n.get("kuvalich.archive.roster", d.roster().size(), d.rosterCap());
    }

    /** 每只一行 8 字像素、行距 12，和交战卡同一个排法；没有盟友时一行说明 */
    private int rosterContentH(int n) {
        return n <= 0 ? 8 * u : n * 8 * u + (n - 1) * 4 * u;
    }

    private int rosterCardH(LichArchivePacket.Data d) {
        return 16 * u + rosterContentH(Math.min(LichArchivePacket.MAX_ALLIES, d.roster().size()));
    }

    private void rosterCard(int x, int y, int w, LichArchivePacket.Data d, float a) {
        String full = rosterLegend(d);
        String leg = fit(full, Math.max(1, (w - 12 * u) / u));
        int n = Math.min(LichArchivePacket.MAX_ALLIES, d.roster().size());
        cardFrame(x, y, w, rosterContentH(n), a, leg, FxPalette.ASH, null, 0, null, 0);
        if (leg != full && in(cx, y, inkW(leg) * u, 8 * u)) {
            hoverLines = List.of(Component.literal(full));
        }
        rosterRows(cx, cy, cw, d.roster(), n, a);
    }

    /**
     * 名册的行：出战标记、元素图形、名字（出战的亮一些）、「第几只 · 等级」，行尾一个遣散键。
     * 名字和后面那段放不下时先截名字，名字剩不到三个字宽就不写后面那段；点了遣散键那一行换成确认的话
     */
    private void rosterRows(int x, int y, int w, List<LichArchivePacket.Ally> roster, int shown, float a) {
        if (roster.isEmpty()) {
            String none = I18n.get("kuvalich.archive.roster.none");
            String s = fit(none, w / u);
            text(s, x, y, u, FxPalette.TEXT_MID, 0.7F * a);
            if (in(x, y, w, 8 * u)) {
                hoverLines = List.of(Component.literal(none),
                        Component.translatable("kuvalich.archive.roster.none.tooltip").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        hitS = 11 * u;
        int glyphX = x + 10 * u;
        int textX = glyphX + 10 * u;
        int kx = x + w - 7 * u;
        int textR = kx - 4 * u;
        int avail = textR - textX;
        // 后面那段排成一列：名字列宽取最长的名字，放不下就把名字列压窄（名字截断），压到不足三个字宽就不写后面那段
        int nameMax = 0;
        int metaMax = 0;
        for (int i = 0; i < shown && i < roster.size(); i++) {
            LichArchivePacket.Ally al = roster.get(i);
            nameMax = Math.max(nameMax, inkW(LichArchiveScreen.upper(al.name())) * u);
            metaMax = Math.max(metaMax, inkW(I18n.get("kuvalich.archive.roster.meta", al.generation(), al.rank())) * u);
        }
        int nameCap = avail - metaMax - 6 * u;
        boolean withMeta = nameCap >= 24 * u;
        int nameCol = withMeta ? Math.min(nameMax, nameCap) : avail;
        for (int i = 0; i < shown && i < roster.size(); i++) {
            LichArchivePacket.Ally al = roster.get(i);
            int ry = y + i * 12 * u;
            int ky = vmid(ry, 8 * u, 7 * u);
            boolean confirming = confirmGen == al.generation();
            boolean overRel = in(kx - 2 * u, ky - 2 * u, hitS, hitS);
            boolean overRow = !overRel && in(x - 2 * u, ry - 2 * u, kx - 2 * u - (x - 2 * u), 12 * u);
            if (overRow || overRel) {
                rect(x - 2 * u, ry - 2 * u, w + 4 * u, 12 * u, FxPalette.KUVA_MIST, (overRel ? 0.5F : 0.35F) * a);
            }
            if (al.active()) {
                glyph(ACTIVE, x, ky, FxPalette.KUVA, a);
            }
            String el = al.element();
            int[] mask = el.isEmpty() ? null : LarvlingTag.elementGlyph(el);
            int elRgb = el.isEmpty() ? FxPalette.ASH : KuvaPalette.element(el);
            if (mask != null) {
                glyph(mask, 7, glyphX, ky, elRgb, a);
            } else {
                glyph(UNBORN, glyphX, ky, FxPalette.ASH, a);
            }
            String name = LichArchiveScreen.upper(al.name());
            if (confirming) {
                String s = fit(I18n.get("kuvalich.archive.roster.confirm", name), avail / u);
                text(s, textX, vmid(ry, 8 * u, textH(s) * u), u, FxPalette.KUVA, a);
            } else {
                String meta = I18n.get("kuvalich.archive.roster.meta", al.generation(), al.rank());
                String nm = fit(name, nameCol / u);
                // 名字和后面那段同一条基线：按两段里高的那段竖直居中
                int ty = vmid(ry, 8 * u, Math.max(textH(nm), withMeta ? textH(meta) : 0) * u);
                text(nm, textX, ty, u, al.active() ? FxPalette.BONE : FxPalette.TEXT_MID, a);
                if (withMeta) {
                    text(meta, textX + nameCol + 6 * u, ty, u, FxPalette.ASH, a);
                }
            }
            boolean hotKey = overRel || confirming;
            glyph(CLOSE, kx, ky, hotKey ? FxPalette.KUVA : FxPalette.ASH, (hotKey ? 1.0F : 0.7F) * a);

            if (a >= 0.5F && rowCount < rowX.length) {
                rowX[rowCount] = x - 2 * u;
                rowY[rowCount] = ry - 2 * u;
                rowW[rowCount] = kx - 2 * u - (x - 2 * u);
                rowGen[rowCount] = al.generation();
                rowActive[rowCount] = al.active();
                relX[rowCount] = kx - 2 * u;
                relY[rowCount] = ky - 2 * u;
                rowCount++;
            }
            if (overRel) {
                hoverLines = confirming
                        ? List.of(Component.translatable("kuvalich.archive.roster.release.confirm", name))
                        : List.of(Component.translatable("kuvalich.archive.roster.release", name),
                        Component.translatable("kuvalich.archive.roster.release.hint").withStyle(ChatFormatting.GRAY));
            } else if (overRow) {
                hoverLines = allyTooltip(al, name);
            }
        }
    }

    private static List<Component> allyTooltip(LichArchivePacket.Ally al, String name) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name));
        lines.add(Component.translatable("kuvalich.archive.roster.meta", al.generation(), al.rank())
                .withStyle(ChatFormatting.GRAY));
        if (al.weapon() >= 0 && al.weapon() < KuvaLichItems.KUVA_WEAPONS.size()) {
            String weapon = new ItemStack(KuvaLichItems.KUVA_WEAPONS.get(al.weapon())).getHoverName().getString();
            String el = al.element();
            lines.add(Component.literal(el.isEmpty() ? weapon : weapon + " · " + I18n.get("kuvaweapon.type." + el))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!al.ephemera().isEmpty()) {
            lines.add(Component.translatable("kuvalich.archive.ephemera", ephemeraName(al.ephemera()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (al.present()) {
            lines.add(Component.translatable("kuvalich.archive.roster.present").withStyle(ChatFormatting.AQUA));
        }
        lines.add(al.active()
                ? Component.translatable("kuvalich.archive.roster.active").withStyle(ChatFormatting.GOLD)
                : Component.translatable("kuvalich.archive.roster.select").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** 幻纹款名；款式表还没有名字的按元素名顶上 */
    static String ephemeraName(String id) {
        String key = "kuvalich.ephemera.name." + id;
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        EphemeraType t = EphemeraType.byId(id);
        return t != null ? I18n.get("kuvaweapon.type." + t.element()) : id;
    }

    private static int ephemeraRgb(String id) {
        EphemeraType t = EphemeraType.byId(id);
        return t != null ? t.defaultColor() : FxPalette.ACCENT;
    }

    /** 鼠标（界面坐标）落在名册入口或名册的行上就处理，返回处理了没有 */
    boolean clickRoster(double mx, double my) {
        int px = phys(mx);
        int py = phys(my);
        if (entryW > 0 && inRect(px, py, entryX, entryY, entryW, entryH)) {
            rosterOpen = true;
            rosterOpenMs = Util.getMillis();
            confirmGen = -1;
            rpW = 0;
            return true;
        }
        return rowClick(px, py);
    }

    /** 点行设为出战（已经是出战的不发）；点遣散键先进确认，确认期内再点一次才遣散；点别处取消确认 */
    private boolean rowClick(int px, int py) {
        long now = Util.getMillis();
        for (int i = 0; i < rowCount; i++) {
            if (inRect(px, py, relX[i], relY[i], hitS, hitS)) {
                if (confirmGen == rowGen[i] && now <= confirmUntil) {
                    confirmGen = -1;
                    command(AllyCommandPacket.RELEASE, rowGen[i]);
                } else {
                    confirmGen = rowGen[i];
                    confirmUntil = now + CONFIRM_MS;
                }
                return true;
            }
            if (inRect(px, py, rowX[i], rowY[i], rowW[i], 12 * u)) {
                confirmGen = -1;
                if (!rowActive[i]) {
                    command(AllyCommandPacket.SET_ACTIVE, rowGen[i]);
                }
                return true;
            }
        }
        confirmGen = -1;
        return false;
    }

    private void command(int action, int generation) {
        if (rosterActions != null) {
            rosterActions.command(action, generation);
        }
    }

    void closeRoster() {
        rosterOpen = false;
        rpW = 0;
        confirmGen = -1;
    }

    /** 名册面板开着时的点击：关闭键或面板外面关上，面板里落到行上照常处理；都由面板吃掉 */
    boolean rosterPanelClick(double mx, double my, int button) {
        if (rpW <= 0) {
            return false;
        }
        int px = phys(mx);
        int py = phys(my);
        if (button == 0 && inRect(px, py, rcX, rcY, rcS, rcS) || !inRect(px, py, rpX, rpY, rpW, rpH)) {
            closeRoster();
            return true;
        }
        return button == 0 && rowClick(px, py);
    }

    /**
     * 名册面板：左列放不下名册时从标题行的入口打开。和「查看全部」同一个样子：压暗、正中一张卷宗卡、右上关闭键；
     * 宽度按最长的一行（名字 + 后面那段）定，不超出窗口；窗口矮到行放不下时少排几行，底下写还有几只
     */
    void drawRosterPanel(GuiGraphics graphics, Font f, int mx, int my, @Nullable LichArchivePacket.Data data) {
        if (!rosterOpen) {
            return;
        }
        if (data == null) {
            closeRoster();
            return;
        }
        begin(graphics, f, mx, my, data);
        try {
            if (Util.getMillis() > confirmUntil) {
                confirmGen = -1;
            }
            float a = Mth.clamp((Util.getMillis() - rosterOpenMs) / (float) PANEL_FADE_MS, 0.0F, 1.0F);
            List<LichArchivePacket.Ally> roster = data.roster();
            int n = Math.min(LichArchivePacket.MAX_ALLIES, roster.size());
            int margin = 16 * u;
            int maxW = screenW - 2 * margin;
            // n 行的卡高 16 + 12n - 4 字像素；放不下全部时再让出底下那行字的 12
            int room = screenH - 2 * margin - 12 * u;
            int shown = n <= Math.max(1, room / (12 * u)) ? n : Math.max(1, (room - 12 * u) / (12 * u));
            String foot = shown < n ? I18n.get("kuvalich.archive.roster.omitted", n - shown) : null;
            int footH = foot != null ? 12 * u : 0;

            String legend = rosterLegend(data);
            int nameMax = 0;
            int metaMax = 0;
            int confirmMax = 0;
            for (int i = 0; i < n; i++) {
                LichArchivePacket.Ally al = roster.get(i);
                String name = LichArchiveScreen.upper(al.name());
                nameMax = Math.max(nameMax, inkW(name));
                metaMax = Math.max(metaMax, inkW(I18n.get("kuvalich.archive.roster.meta", al.generation(), al.rank())));
                confirmMax = Math.max(confirmMax, inkW(I18n.get("kuvalich.archive.roster.confirm", name)));
            }
            int need = Math.max(nameMax + 6 + metaMax, confirmMax) * u;
            int rowNeed = n == 0 ? inkW(I18n.get("kuvalich.archive.roster.none")) * u : 20 * u + need + 11 * u;
            int footInk = foot != null ? inkW(foot) * u : 0;
            int w = Math.min(maxW, Math.max(rowNeed + 12 * u, Math.max(inkW(legend) * u + 27 * u,
                    Math.max(footInk + 12 * u, 140 * u))));
            if (((screenW - w) & 1) != 0 && w < maxW) {
                w++;
            }
            int contentH = rosterContentH(shown);
            int h = 16 * u + contentH + footH;
            int x = hmid(0, screenW, w);
            int y = vmid(0, screenH, h - 4 * u) - 4 * u;
            rpX = x;
            rpY = y;
            rpW = w;
            rpH = h;

            rect(0, 0, screenW, screenH, 0x000000, 0.6F * a);
            rect(x, y + 4 * u, w, h - 4 * u, FxPalette.INK, 0.94F * a);
            String leg = fit(legend, Math.max(1, (w - 27 * u) / u));
            cardFrame(x, y, w, contentH + footH, a, leg, FxPalette.ASH, null, 0, null, 0, 7 * u);

            int kx = x + w - 13 * u;
            int ky = vmid(y, 8 * u, 7 * u);
            rcS = 11 * u;
            rcX = kx - 2 * u;
            rcY = ky - 2 * u;
            boolean overClose = in(rcX, rcY, rcS, rcS);
            glyph(CLOSE, kx, ky, overClose ? FxPalette.KUVA : FxPalette.ASH, a);
            if (overClose) {
                hoverLines = List.of(Component.translatable("kuvalich.archive.confiscated.close"));
            }

            int ry0 = cy;
            rosterRows(cx, ry0, cw, roster, shown, a);
            if (foot != null) {
                String fs = fit(foot, cw / u);
                text(fs, hmid(cx, cw, inkW(fs) * u), ry0 + contentH + 4 * u, u, FxPalette.TEXT_MID, 0.8F * a);
            }
        } finally {
            finish();
        }
    }

    // ==================== 右列最下面：被它拿走的东西 ====================

    /**
     * 被它拿走的东西：一张同样的卷宗卡，里面是原版式的物品格（18 字像素见方，格挨格），物品图标按字号整数倍、
     * 照原版写数量。行数按件数、最多三行，放不下就少排一行（至少一行）；装不下时最后一格画三颗点，
     * 剩下的件数照原版数量的写法贴在右下角（超过两位只留三颗点，总数在卡名里）。装不下时这一格和卡名都能点，
     * 点开「查看全部」面板（{@link #drawTakenPanel}）。坐标是界面坐标（右列的左右边、能用的上下边）
     */
    void drawTaken(GuiGraphics graphics, Font f, int mx, int my, float x0, float x1, float y0, float y1,
                   @Nullable LichArchivePacket.Data data, float a) {
        if (a <= 0.01F) {
            return;
        }
        begin(graphics, f, mx, my, data);
        try {
            int x = Math.round(x0 * gs);
            int w = Math.round(x1 * gs) - x;
            int y = Math.round(y0 * gs);
            int maxY = Math.round(y1 * gs);
            int total = data != null ? data.confiscatedCount() : 0;
            String legend = data != null ? taken : I18n.get("kuvalich.archive.confiscated", LichArchiveScreen.unknown());
            if (data == null) {
                cardFrame(x, y, w, 8 * u, a, legend, FxPalette.ASH, null, 0, null, 0);
                text(LichArchiveScreen.unknown(), cx, cy, u, FxPalette.ASH, a);
                return;
            }
            List<ItemStack> items = data.confiscated();
            if (total <= 0 || items.isEmpty()) {
                cardFrame(x, y, w, 8 * u, a, legend, FxPalette.ASH, null, 0, null, 0);
                String none = I18n.get("kuvalich.archive.confiscated.none");
                text(fit(none, cw / u), cx, cy, u, FxPalette.TEXT_MID, 0.7F * a);
                return;
            }
            int s = 18 * u;
            int cols = Math.max(1, (w - 12 * u) / s);
            int rows = Math.min(3, (total + cols - 1) / cols);
            while (rows > 1 && y + 16 * u + rows * s > maxY) {
                rows--;
            }
            String shownLegend = fitLegend(legend, total, (w - 12 * u) / u);
            cardFrame(x, y, w, rows * s, a, shownLegend, FxPalette.ASH, null, 0, null, 0);
            int lx = cx;
            int lwid = inkW(shownLegend) * u;
            itemGrid(hmid(cx, cw, cols * s), cy, cols, rows, items, data.confiscatedSlim(), total, a);
            if (moreW > 0) {
                // 卡名一行也能点：和卡名的字墨同宽、同高
                legX = lx;
                legY = y;
                legW = lwid;
                legH = 8 * u;
                if (in(legX, legY, legW, legH)) {
                    hoverLines = List.of(Component.translatable("kuvalich.archive.confiscated.more", total - moreShown));
                }
            } else if (shownLegend != legend && in(lx, y, lwid, 8 * u)) {
                hoverLines = List.of(Component.literal(legend));
            }
        } finally {
            finish();
        }
    }

    /**
     * 卡名放不下时截掉前面的说明、留住括号和里面的件数（「被它拿走的…（128）」）；找不到件数就照常从末尾截。
     * 没截断时原样返回同一个对象
     */
    private String fitLegend(String legend, int total, int maxW) {
        if (inkW(legend) <= maxW) {
            return legend;
        }
        String num = Integer.toString(total);
        int at = legend.lastIndexOf(num);
        if (at > 0) {
            int open = Math.max(legend.lastIndexOf('（', at), legend.lastIndexOf('(', at));
            int cut = open >= 0 && at - open <= 2 ? open : at;
            String tail = legend.substring(cut);
            String head = legend.substring(0, cut).stripTrailing();
            String gap = cut < legend.length() && legend.charAt(cut) == '(' ? " " : "";
            int room = maxW - inkW(tail) - font.width(gap);
            if (room > 0 && !head.isEmpty()) {
                return fit(head, room) + gap + tail;
            }
        }
        return fit(legend, maxW);
    }

    private void itemGrid(int x, int y, int cols, int rows, List<ItemStack> items, BitSet slim, int total, float a) {
        int s = 18 * u;
        int slots = cols * rows;
        int shown = Math.min(items.size(), total > slots ? slots - 1 : slots);
        for (int i = 0; i < slots; i++) {
            slot(x + (i % cols) * s, y + (i / cols) * s, a);
        }
        for (int i = 0; i < shown; i++) {
            int sx = x + (i % cols) * s;
            int sy = y + (i / cols) * s;
            ItemStack st = items.get(i);
            item(st, sx + u, sy + u, 16 * u, true, a);
            if (in(sx, sy, s, s)) {
                hoverStack = st;
                hoverSlim = slim.get(i);
            }
        }
        if (total > shown) {
            int sx = x + (shown % cols) * s;
            int sy = y + (shown / cols) * s;
            int n = total - shown;
            int dw = 3 * 2 * u + 2 * 2 * u;
            int dx = hmid(sx, s, dw);
            for (int k = 0; k < 3; k++) {
                rect(dx + k * 4 * u, sy + 4 * u, 2 * u, 2 * u, FxPalette.TEXT_MID, a);
            }
            String cnt = Integer.toString(n);
            if (inkW(cnt) <= 11) {
                // 和原版物品数量同一个位置：右缘贴格子内右缘、底贴物品底，带阴影
                shadowText(cnt, sx + 17 * u - font.width(cnt) * u, sy + 9 * u, FxPalette.BONE, a);
            }
            if (a >= 0.5F) {
                moreX = sx;
                moreY = sy;
                moreW = s;
                moreH = s;
                moreShown = shown;
            }
            if (in(sx, sy, s, s)) {
                hoverLines = List.of(Component.translatable("kuvalich.archive.confiscated.more", n));
            }
        }
    }

    /** 一个物品格（18 字像素见方）：底色、左上暗边、右下亮边 */
    private void slot(int sx, int sy, float a) {
        int s = 18 * u;
        rect(sx, sy, s, s, SLOT_INNER, a);
        rect(sx, sy, s - u, u, SLOT_DARK, a);
        rect(sx, sy, u, s - u, SLOT_DARK, a);
        rect(sx + u, sy + s - u, s - u, u, SLOT_LIGHT, a);
        rect(sx + s - u, sy + u, u, s - u, SLOT_LIGHT, a);
    }

    // ==================== 「查看全部」面板 ====================

    /** 面板最多几列、最多露几行（行多了滚动） */
    private static final int PANEL_COLS = 12;
    private static final int PANEL_ROWS = 8;
    /** 面板淡入用多久（ms） */
    private static final int PANEL_FADE_MS = 120;
    /** 关闭键：7×7 的叉 */
    private static final Glyph CLOSE = glyph("#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.",
            "#.....#");

    /** 滚轮攒下的零头（触控板一次只给零点几） */
    private double scrollAcc;
    /** 上一帧面板的列数（滚动时把行换算成物品下标） */
    private int lastCols = 1;

    /** 鼠标（界面坐标）落在卡上能点开面板的地方就打开，返回打开了没有 */
    boolean clickTaken(double mx, double my) {
        int px = phys(mx);
        int py = phys(my);
        if (moreW <= 0 || !(inRect(px, py, moreX, moreY, moreW, moreH) || inRect(px, py, legX, legY, legW, legH))) {
            return false;
        }
        takenOpen = true;
        takenOpenMs = Util.getMillis();
        takenScroll = 0;
        takenFirst = 0;
        scrollAcc = 0.0D;
        barDrag = false;
        panW = 0;
        return true;
    }

    void closeTaken() {
        takenOpen = false;
        barDrag = false;
        panW = 0;
    }

    /**
     * 面板开着时的点击：点关闭键或面板外面就关上，在滚动条上按下开始拖，面板里其余地方不做事。
     * 不管点在哪都由面板吃掉，不落到底下的档案
     */
    void panelClick(double mx, double my, int button) {
        if (panW <= 0) {
            return;
        }
        int px = phys(mx);
        int py = phys(my);
        if (button == 0 && inRect(px, py, closeX, closeY, closeS, closeS)) {
            closeTaken();
            return;
        }
        if (!inRect(px, py, panX, panY, panW, panH)) {
            closeTaken();
            return;
        }
        if (button == 0 && maxScroll > 0 && inRect(px, py, barX - 2 * u, barY, 6 * u, barH)) {
            barDrag = true;
            dragTo(py);
        }
    }

    void panelDrag(double my) {
        if (barDrag) {
            dragTo(phys(my));
        }
    }

    void panelRelease() {
        barDrag = false;
    }

    /** 滚轮：一格一行，往上滚看前面的 */
    void panelScroll(double delta) {
        if (maxScroll <= 0) {
            scrollAcc = 0.0D;
            return;
        }
        scrollAcc += delta;
        int steps = (int) scrollAcc;
        if (steps != 0) {
            scrollAcc -= steps;
            takenScroll = Mth.clamp(takenScroll - steps, 0, maxScroll);
            takenFirst = takenScroll * lastCols;
        }
    }

    /** 拖滚动条：滑块中心跟着鼠标，换算成整行 */
    private void dragTo(int py) {
        int range = barH - thumbH;
        if (range <= 0 || maxScroll <= 0) {
            return;
        }
        float t = (py - barY - thumbH / 2.0F) / range;
        takenScroll = Mth.clamp(Math.round(t * maxScroll), 0, maxScroll);
        takenFirst = takenScroll * lastCols;
    }

    /**
     * 「查看全部」面板：先压暗整个档案，正中盖一张和卷宗卡一个样子的卡（卡名压在上框线上），右上角一个关闭键。
     * 里面是和卡里一样的物品格，列数按窗口宽度算（最多 {@link #PANEL_COLS} 列，件数少就按件数），
     * 最多露 {@link #PANEL_ROWS} 行，多了用滚轮或右边的滚动条一行一行翻。包里没写全（总数比列表多）时底下写
     * 「另有 N 件未列出」。档案刷新后没有东西了（玄骸被终结、东西还回来）就自己关上
     */
    void drawTakenPanel(GuiGraphics graphics, Font f, int mx, int my, @Nullable LichArchivePacket.Data data) {
        if (!takenOpen) {
            return;
        }
        if (data == null || data.confiscatedCount() <= 0 || data.confiscated().isEmpty()) {
            closeTaken();
            return;
        }
        begin(graphics, f, mx, my, data);
        try {
            float a = Mth.clamp((Util.getMillis() - takenOpenMs) / (float) PANEL_FADE_MS, 0.0F, 1.0F);
            List<ItemStack> items = data.confiscated();
            int n = items.size();
            int total = data.confiscatedCount();
            int s = 18 * u;
            int margin = 16 * u;
            int maxW = screenW - 2 * margin;
            String foot = total > n ? I18n.get("kuvalich.archive.confiscated.omitted", total - n) : null;
            int footH = foot != null ? 12 * u : 0;

            int cols = Math.min(n, Mth.clamp((maxW - 12 * u) / s, 1, PANEL_COLS));
            int rowsAll = (n + cols - 1) / cols;
            int rows = Math.min(rowsAll, Mth.clamp((screenH - 2 * margin - 16 * u - footH) / s, 1, PANEL_ROWS));
            maxScroll = rowsAll - rows;
            lastCols = cols;
            takenScroll = Mth.clamp(takenFirst / cols, 0, maxScroll);

            // 宽：放得下网格、卡名加关闭键（两边各留一段框线）、底下那行字，三者取最宽，不超出窗口
            int legInk = inkW(taken) * u;
            int footInk = foot != null ? inkW(foot) * u : 0;
            int w = Math.min(maxW, Math.max(cols * s + 12 * u, Math.max(legInk + 27 * u, footInk + 12 * u)));
            // 卡名或底下那行字撑宽时宽度可能和窗口宽一奇一偶，补 1 像素让左右留白相等，网格在卡里也就正好居中
            if (((screenW - w) & 1) != 0 && w < maxW) {
                w++;
            }
            int h = 16 * u + rows * s + footH;
            int x = hmid(0, screenW, w);
            // 看得见的框从卡名中线（y + 4 字像素）开始，按框居中，卡名伸在框线上面
            int y = vmid(0, screenH, h - 4 * u) - 4 * u;
            panX = x;
            panY = y;
            panW = w;
            panH = h;

            // 压暗底下的档案，再垫一层实底（卡片自己的底色是半透明的）
            rect(0, 0, screenW, screenH, 0x000000, 0.6F * a);
            rect(x, y + 4 * u, w, h - 4 * u, FxPalette.INK, 0.94F * a);
            String legend = fit(taken, Math.max(1, (w - 27 * u) / u));
            cardFrame(x, y, w, rows * s + footH, a, legend, FxPalette.ASH, null, 0, null, 0, 7 * u);

            // 关闭键：压在上框线右端留出的缺口里，和卡名同一行竖直居中
            int kx = x + w - 13 * u;
            int ky = vmid(y, 8 * u, 7 * u);
            closeS = 11 * u;
            closeX = kx - 2 * u;
            closeY = ky - 2 * u;
            boolean overClose = in(closeX, closeY, closeS, closeS);
            glyph(CLOSE, kx, ky, overClose ? FxPalette.KUVA : FxPalette.ASH, a);
            if (overClose) {
                hoverLines = List.of(Component.translatable("kuvalich.archive.confiscated.close"));
            }

            int gw = cols * s;
            int gx = hmid(cx, cw, gw);
            int gy = cy;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    slot(gx + c * s, gy + r * s, a);
                }
            }
            int first = takenScroll * cols;
            int last = Math.min(n, first + rows * cols);
            for (int i = first; i < last; i++) {
                int sx = gx + ((i - first) % cols) * s;
                int sy = gy + ((i - first) / cols) * s;
                ItemStack st = items.get(i);
                item(st, sx + u, sy + u, 16 * u, true, a);
                if (in(sx, sy, s, s)) {
                    hoverStack = st;
                    hoverSlim = data.confiscatedSlim().get(i);
                }
            }

            // 滚动条：贴在网格右边，空 2 字像素，落在卡的右内边距里
            if (maxScroll > 0) {
                barX = gx + gw + 2 * u;
                barY = gy;
                barH = rows * s;
                thumbH = Math.max(6 * u, Math.round(barH * rows / (float) rowsAll));
                int thumbY = barY + Math.round((barH - thumbH) * takenScroll / (float) maxScroll);
                boolean hot = barDrag || in(barX - 2 * u, barY, 6 * u, barH);
                rect(barX, barY, 2 * u, barH, FxPalette.KUVA_MIST, 0.8F * a);
                rect(barX, thumbY, 2 * u, thumbH, hot ? FxPalette.KUVA : FxPalette.KUVA_PALE, (hot ? 0.9F : 0.5F) * a);
            } else {
                barH = 0;
            }

            if (foot != null) {
                String fs = fit(foot, cw / u);
                text(fs, hmid(cx, cw, inkW(fs) * u), gy + rows * s + 4 * u, u, FxPalette.TEXT_MID, 0.8F * a);
            }
        } finally {
            finish();
        }
    }

    /**
     * 点击给的界面坐标换成物理像素：先取整成界面整数坐标再乘缩放，和画面时悬停用的坐标（render 拿到的是整数）
     * 同一个算法，提示框出现的地方就一定点得中
     */
    private int phys(double guiCoord) {
        return (int) Math.floor((int) guiCoord * gs);
    }

    private static boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    // ==================== 字的缓存 ====================

    private void ensureTexts(LichArchivePacket.Data d) {
        String lang = Minecraft.getInstance().options.languageCode;
        if (textsBuilt && d == textsFor && lang.equals(textsLang)) {
            return;
        }
        textsBuilt = true;
        textsFor = d;
        textsLang = lang;
        legWeapon = I18n.get("kuvalich.archive.attr.weapon");
        legVital = I18n.get("kuvalich.archive.card.vital");
        legWeak = I18n.get("kuvalich.archive.attr.weakness");
        legResist = I18n.get("kuvalich.archive.attr.resistance");
        legAbility = I18n.get("kuvalich.archive.card.ability");
        legFight = I18n.get("kuvalich.archive.card.fight");
        taken = I18n.get("kuvalich.archive.confiscated", d.confiscatedCount());

        // 武器：诞生时定下的那一把，服务端把上下限都填成它的等级、物品上带着元素
        ItemStack ws = d.weaponStack();
        wName = ws.isEmpty() ? "" : ws.getHoverName().getString();
        String type = ws.isEmpty() ? "" : KuvaWeaponUtil.getType(ws);
        boolean fixed = !type.isEmpty() && d.weaponLvMin() == d.weaponLvMax() && d.weaponLvMax() > 0;
        wElemMask = fixed ? LarvlingTag.elementGlyph(type) : null;
        wElemRgb = fixed ? KuvaPalette.element(type) : 0;
        wElemName = fixed ? I18n.get("kuvaweapon.type." + type) : "";
        if (ws.isEmpty()) {
            wLines = 0;
            wTip = null;
            wLevel = "";
        } else if (d.weaponLvMax() <= 0) {
            wLines = 1;
            wTip = null;
            wLevel = "";
        } else {
            wTip = fixed ? I18n.get("kuvalich.archive.val.weapon_fixed", d.weaponLvMax(), wElemName)
                    : I18n.get("kuvalich.archive.val.weapon_levels", d.weaponLvMin(), d.weaponLvMax());
            if (wElemMask != null) {
                wLines = 3;
                wLevel = I18n.get("kuvalich.archive.val.weapon_level", d.weaponLvMax());
            } else {
                wLines = 2;
                wLevel = wTip;
            }
        }

        String eph = d.state().ephemera;
        wEphemera = eph.isEmpty() ? "" : I18n.get("kuvalich.archive.ephemera", ephemeraName(eph));
        wEphemeraRgb = ephemeraRgb(eph);

        LichArchivePacket.Stats st = d.stats();
        vitalValue[0] = st.health() > 0 ? Integer.toString(st.health()) : "—";
        vitalValue[1] = st.health() > 0 ? LichArchiveScreen.tenths(st.armorTenths()) : "—";
        vitalValue[2] = st.hitTenths() > 0
                ? I18n.get("kuvalich.archive.val.hit", LichArchiveScreen.tenths(st.hitTenths())) : "—";
        vitalCap[0] = I18n.get("kuvalich.archive.attr.health");
        vitalCap[1] = I18n.get("kuvalich.archive.attr.armor");
        vitalCap[2] = I18n.get("kuvalich.archive.attr.hit");

        int[] pct = st.affinityPct();
        for (int i = 0; i < LichTraits.CATEGORIES; i++) {
            int p = i < pct.length ? pct[i] : 100;
            String m = String.format(Locale.ROOT, "%.2f", p / 100.0D);
            multText[i] = "×" + m;
            catTip[i] = I18n.get("kuvalich.archive.val.mult", I18n.get(CATEGORY_KEYS[i]), m);
        }

        extraN = 0;
        if (st.adaptPct() > 0 && st.adaptStacks() > 0) {
            extraGlyph[extraN] = ADAPT_SMALL;
            extraText[extraN++] = I18n.get("kuvalich.archive.val.adaptive", st.adaptPct(), st.adaptStacks());
        }
        if (st.furyPermille() > 0) {
            extraGlyph[extraN] = FURY_SMALL;
            extraText[extraN++] = I18n.get("kuvalich.archive.val.fury", LichArchiveScreen.pct1(st.furyPermille()));
        }

        fightN = 0;
        if (!d.birthBiome().isEmpty() || !d.birthDim().isEmpty()) {
            addFight(BIRTH, FxPalette.TEXT_MID, I18n.get("kuvalich.archive.val.birthplace",
                    LichArchiveScreen.biomeName(d.birthBiome()), LichArchiveScreen.dimName(d.birthDim())));
        }
        addFight(ENCOUNTERS, FxPalette.TEXT_MID, I18n.get("kuvalich.archive.attr.encounters") + " "
                + I18n.get("kuvalich.archive.val.encounters", d.encounters()));
        if (d.dealtPct() > 0) {
            addFight(DEALT, RESIST, I18n.get("kuvalich.archive.attr.dealt") + " "
                    + I18n.get("kuvalich.archive.val.minus", d.dealtPct()));
        }
        if (d.takenPct() > 0) {
            addFight(TAKEN, FxPalette.KUVA, I18n.get("kuvalich.archive.attr.taken") + " "
                    + I18n.get("kuvalich.archive.val.plus", d.takenPct()));
        }
    }

    private void addFight(Glyph gl, int rgb, String s) {
        fightGlyph[fightN] = gl;
        fightRgb[fightN] = rgb;
        fightText[fightN++] = s;
    }

    // ==================== 状态 ====================

    private static boolean statusHot(LichState s) {
        return switch (s) {
            case INCOMING, ARRIVING, PRESENT, DOWNED -> true;
            default -> false;
        };
    }

    private static Glyph statusGlyph(LichStatePacket st) {
        if (st.paused) {
            return PAUSED;
        }
        return switch (st.state) {
            case STALKING, DEPARTING -> EYE;
            case COOLDOWN -> MOON;
            case INCOMING -> INCOMING;
            case ARRIVING, PRESENT -> PRESENT;
            case DOWNED -> DOWNED;
            default -> UNBORN;
        };
    }

    // ==================== 图元（物理像素，整数坐标） ====================

    /** 开画：姿态缩成物理像素、开一批图元 */
    private void begin(GuiGraphics graphics, Font f, int mx, int my, @Nullable LichArchivePacket.Data data) {
        g = graphics;
        font = f;
        mxp = (int) Math.floor(mx * gs);
        myp = (int) Math.floor(my * gs);
        hoverLines = null;
        hoverStack = ItemStack.EMPTY;
        hoverSlim = false;
        if (data != null) {
            ensureTexts(data);
        }
        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        GuiShapes.begin(g);
    }

    private void finish() {
        GuiShapes.end();
        g.pose().popPose();
    }

    private boolean in(int x, int y, int w, int h) {
        return mxp >= x && mxp < x + w && myp >= y && myp < y + h;
    }

    private void rect(int x, int y, int w, int h, int rgb, float a) {
        if (a <= 0.003F || w <= 0 || h <= 0) {
            return;
        }
        GuiShapes.block(g, x, y, x + w, y + h, GuiShapes.argb(rgb, Math.min(1.0F, a)));
    }

    private void hline(int x0, int x1, int y, int rgb, float a) {
        rect(Math.min(x0, x1), y, Math.abs(x1 - x0), lw, rgb, a);
    }

    private void vline(int x, int y0, int y1, int rgb, float a) {
        rect(x, Math.min(y0, y1), lw, Math.abs(y1 - y0), rgb, a);
    }

    private void frame(int x, int y, int w, int h, int rgb, float a) {
        rect(x, y, w, lw, rgb, a);
        rect(x, y + h - lw, w, lw, rgb, a);
        rect(x, y + lw, lw, h - 2 * lw, rgb, a);
        rect(x + w - lw, y + lw, lw, h - 2 * lw, rgb, a);
    }

    /** 四角短刻度（取景框式），角长 len */
    private void corners(int x, int y, int w, int h, int len, int rgb, float a) {
        rect(x, y, len, lw, rgb, a);
        rect(x, y + lw, lw, len - lw, rgb, a);
        rect(x + w - len, y, len, lw, rgb, a);
        rect(x + w - lw, y + lw, lw, len - lw, rgb, a);
        rect(x, y + h - lw, len, lw, rgb, a);
        rect(x, y + h - len, lw, len - lw, rgb, a);
        rect(x + w - len, y + h - lw, len, lw, rgb, a);
        rect(x + w - lw, y + h - len, lw, len - lw, rgb, a);
    }

    /** 台阶菱形：外接 2n × 2n 个字像素，台阶 1 字像素；{@code fill}、{@code edge} 给 -1 就不画那一样 */
    private void diamond(int x, int y, int n, int fill, int edge, float a) {
        int mid = x + n * u;
        for (int j = 0; j < n; j++) {
            int half = (j + 1) * u;
            int ya = y + j * u;
            int yb = y + (2 * n - 1 - j) * u;
            if (fill >= 0) {
                rect(mid - half, ya, 2 * half, u, fill, a);
                rect(mid - half, yb, 2 * half, u, fill, a);
            }
            if (edge >= 0) {
                rect(mid - half, ya, u, u, edge, a);
                rect(mid + half - u, ya, u, u, edge, a);
                rect(mid - half, yb, u, u, edge, a);
                rect(mid + half - u, yb, u, u, edge, a);
            }
        }
    }

    /** 小图形，每格 u 个物理像素；同一行连着亮的格并成一块画 */
    private void glyph(Glyph gl, int x, int y, int rgb, float a) {
        glyph(gl.rows, gl.w, x, y, rgb, a);
    }

    private void glyph(int[] rows, int w, int x, int y, int rgb, float a) {
        if (a <= 0.003F) {
            return;
        }
        for (int j = 0; j < rows.length; j++) {
            int i = 0;
            while (i < w) {
                if ((rows[j] & (1 << (w - 1 - i))) == 0) {
                    i++;
                    continue;
                }
                int s = i;
                while (i < w && (rows[j] & (1 << (w - 1 - i))) != 0) {
                    i++;
                }
                rect(x + s * u, y + j * u, (i - s) * u, u, rgb, a);
            }
        }
    }

    /** 一行「小图形 + 字」：图形 7×7，和字按各自高度在行里竖直居中，字在图形右边空 3 格；返回占宽 */
    private int glyphLine(Glyph gl, int rgbG, String s, int rgbT, int x, int y, int rowH, float a) {
        return glyphLine(gl.rows, rgbG, s, rgbT, x, y, rowH, a);
    }

    private int glyphLine(int[] rows, int rgbG, String s, int rgbT, int x, int y, int rowH, float a) {
        glyph(rows, 7, x, vmid(y, rowH, 7 * u), rgbG, a);
        text(s, x + 10 * u, vmid(y, rowH, textH(s) * u), u, rgbT, a);
        return 10 * u + inkW(s) * u;
    }

    /** 原版字体，1 字像素 = {@code k} 个物理像素，不画阴影；返回字墨宽（物理像素） */
    private int text(String s, int x, int y, int k, int rgb, float a) {
        if (s.isEmpty()) {
            return 0;
        }
        int argb = GuiShapes.argb(rgb, Math.min(1.0F, a));
        // 原版字体把 α 低于 4 的颜色当成不透明
        if ((argb >>> 24) >= 5) {
            // drawString 画完立刻提交，攒着的底色、徽记要先提交，不然会盖到字上
            GuiShapes.flush(g);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(x, y, 0.0F);
            ps.scale(k, k, 1.0F);
            g.drawString(font, s, 0, 0, argb, false);
            ps.popPose();
        }
        return inkW(s) * k;
    }

    /** 带阴影的字（只给物品数量用，和原版一样阴影往右下 1 字像素） */
    private void shadowText(String s, int x, int y, int rgb, float a) {
        int argb = GuiShapes.argb(rgb, Math.min(1.0F, a));
        if ((argb >>> 24) < 5) {
            return;
        }
        GuiShapes.flush(g);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0.0F);
        ps.scale(u, u, 1.0F);
        ps.translate(0.0F, 0.0F, 200.0F);
        g.drawString(font, s, 0, 0, argb, true);
        ps.popPose();
    }

    /**
     * 物品图标，边长 {@code size} 物理像素（16 的整数倍）。物品没法跟着半透明，等这一块亮到一半再出来；
     * 先把攒着的图元提交掉，图标才盖在格子上面
     */
    private void item(ItemStack stack, int x, int y, int size, boolean decorations, float a) {
        if (a < 0.5F || stack.isEmpty()) {
            return;
        }
        GuiShapes.flush(g);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0.0F);
        float k = size / 16.0F;
        ps.scale(k, k, 1.0F);
        g.renderItem(stack, 0, 0);
        if (decorations) {
            g.renderItemDecorations(font, stack, 0, 0);
        }
        ps.popPose();
    }

    // ==================== 量尺 ====================

    /** 字墨宽（字像素）：{@code font.width} 去掉末字后面那 1 字像素字距 */
    private int inkW(String s) {
        return s.isEmpty() ? 0 : font.width(s) - 1;
    }

    /** 行高（字像素）：有汉字或下伸字母 8，其余 7 */
    private static int textH(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c >= 0x2E80 || c == 'g' || c == 'j' || c == 'p' || c == 'q' || c == 'y' || c == ',' || c == ';') {
                return 8;
            }
        }
        return 7;
    }

    /** 超宽（字墨宽大于 {@code maxW} 字像素）就截断并补「…」；没截断时原样返回同一个对象 */
    private String fit(String s, int maxW) {
        if (inkW(s) <= maxW) {
            return s;
        }
        int end = s.length();
        while (end > 0) {
            end = s.offsetByCodePoints(end, -1);
            String head = s.substring(0, end);
            if (inkW(head + "…") <= maxW) {
                int k = head.length();
                while (k > 0 && (Character.isWhitespace(head.charAt(k - 1)) || head.charAt(k - 1) == '·')) {
                    k--;
                }
                return head.substring(0, k) + "…";
            }
        }
        return "…";
    }

    /** 取整到字像素 */
    private int snap(float v) {
        return Math.round(v / u) * u;
    }

    /** 水平居中：容器左 {@code l}、宽 {@code cw}，内容宽 {@code w} → 内容左（余下的奇数像素放在右边） */
    private static int hmid(int l, int cw, int w) {
        return l + Math.floorDiv(cw - w, 2);
    }

    /** 竖直居中：行顶 {@code top}、行高 {@code rh}，内容高 {@code h} → 内容顶（余下的奇数像素放在下面） */
    private static int vmid(int top, int rh, int h) {
        return top + Math.floorDiv(rh - h, 2);
    }

    /** 把 {@code total} 均分成 {@code n} 格：第 {@code i} 格的左边（取整，余数给右边的格子） */
    private static int split(int total, int n, int i) {
        return (int) Math.floorDiv((long) total * i, n);
    }

    // ==================== 小图形 ====================

    private static Glyph glyph(String... rows) {
        int w = rows[0].length();
        int[] mask = new int[rows.length];
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < w; i++) {
                if (rows[j].charAt(i) == '#') {
                    mask[j] |= 1 << (w - 1 - i);
                }
            }
        }
        return new Glyph(w, mask);
    }

    /** 一个小图形：宽 {@code w} 格，每行一个整数（最高位是最左一格） */
    private static final class Glyph {
        final int w;
        final int[] rows;

        Glyph(int w, int[] rows) {
            this.w = w;
            this.rows = rows;
        }

        /** 去掉一圈边（9×9 → 7×7） */
        Glyph crop() {
            int[] out = new int[rows.length - 2];
            for (int j = 0; j < out.length; j++) {
                out[j] = (rows[j + 1] >> 1) & ((1 << (w - 2)) - 1);
            }
            return new Glyph(w - 2, out);
        }
    }
}
