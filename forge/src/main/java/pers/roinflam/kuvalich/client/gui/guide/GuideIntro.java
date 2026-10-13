package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;

/**
 * 冒险指南的开场 / 收场 / 翻页动效：军械库终端开机 v2（2026-10-13 重排）
 *
 * <p>面板本身从头到尾<b>不平移</b>，只改变「哪一部分被画出来」（裁剪）和叠加在上面的光效 ——
 * 所以任何时刻控件都在它最终的位置上，点击的命中区和看到的东西不会对不上。
 * 只用 {@link CodexTheme} 现有的原语（括号、斜切描边、填充）和 {@link GuideTheme#digits} 的像素数字，不旋转、不加新图元。</p>
 *
 * <h3>计时</h3>
 * <p>开场按<b>逐帧累加</b>的时钟走（{@code GuideScreen} 每帧加上本帧间隔、单帧最多记 {@value #MAX_FRAME_MS}ms）：打开指南的头几帧
 * （建视图、第一次画物品图标、字形图集）常常一帧一两百毫秒，按墙钟算会直接吃掉上电和锁定那一段。20fps 以上完全不受影响，
 * 更低时开场按比例变慢、不会被跳掉；最小化时没有帧，开场暂停。收场按墙钟算（{@code Util.getMillis()}），最小化时由界面的 tick 兜底关掉。</p>
 *
 * <h3>开场分镜（毫秒，开场时间轴，共 {@value #OPEN_MS}）</h3>
 * <p>原先 640ms 里亮点一闪就拉成扫描线，展开、描框、括号卡入、光束、解密全叠在一起。多出来的时间放在<b>最前面</b>，
 * 做一段「终端上电、锁定目标」；展开、光束、解密只比原先稍慢。四个瞄准括号从锁定那一刻起骑着扫描线、展开带的四角，
 * 一路被带到面板四角落位 —— 从头到尾是同一组括号，和赤毒「锁定 → 解密」的意象连起来。</p>
 * <pre>
 *  第一幕 上电锁定
 *    0 ─  220  遮罩淡入（缓出）
 *    0 ─  120  中心亮点通电：科技青亮点从无升到峰值，之后稳定亮着
 *   60 ─  420  锁定框收拢：四个角括号（臂长 6）从面板四角沿对角线收向亮点（缓出），左上 → 右上 → 右下 → 左下各晚 40ms 到位；
 *              途中科技青，到位那一下转猩红
 *  180 ─  420  自检条：亮点下方 9 格分段条从左往右点亮（亮格猩红、暗格结构线色），右侧像素数字 000 → 100
 *  420 ─  580  锁定：一圈斜切方环从亮点向外扩到约 40px、淡出，只一次；自检条和数字跟着淡出
 *  第二幕 展开
 *  460 ─  620  扫描线：亮点拉成科技青横线向两侧展开（缓出）；锁定框跟着横线两端往外滑
 *  580 ─  920  面板以横线为轴上下展开（缓出 340ms），两道亮边外推渐隐；锁定框骑在展开带四角外 15px（屏幕留白不够时收窄，
 *              见 fitMargin），被带到面板四角
 *  580 ─  880  外框描线：四个亮白笔头从左右侧边中点出发，在顶边 / 底边中点合拢
 *  920 ─ 1040  锁定框落位：用 easeOutBack 卡到外框外 3px，闪一下后留一层淡青
 *  880 ─ 1140  外框通电：辉光冲到 2.5 倍、偏白，再回落
 *  第三幕 扫描
 *  800 ─ 1280  扫描光束自上而下（480ms），光束后方几条跳动的信号干扰条（青 / 猩红错位 = 色偏）
 *  800 ─ 1160  全息闪烁：固定三次暗跳，在 860 / 980 / 1100，幅度 0.30 / 0.20 / 0.10，每次 38ms
 *  800 ─ ~1530 首页格子跟着光束逐个全息成形（每格 200ms，行内错开 22ms，见 {@link GuideHome}）
 *  860 ─  980  书名乱码从左到右铺开
 *  980 ─ 1330  逐字收束成真名（最后一个字 1260），收束那一下闪白、70ms 褪回；猩红下划线标出正在解密的那个字
 * 1120 ─ 1320  副标题打字式展开，前沿带青色光标块
 * 1280         就绪（{@value #READY_MS}）：悬停、提示框、按下态生效
 * 1600         开场结束：之后只画外框和四角括号
 * </pre>
 * <p>跳过：开场期间的点击 / 按键 / 滚轮 / 输入法上屏让动画跳到终态、这次输入照常处理；面板展开过半之前（{@value #HALF_UNFOLD}ms 前）
 * 的点击和滚轮<b>只跳过、不处理</b>（那时还看不见内容，终态那个位置可能正好是一个格子）；Esc 从当时的样子收场；F1~F12、单按修饰键不跳。
 * 见 {@code GuideScreen#skipIntro}。每次正常打开都放完整开场，要快就点一下。</p>
 * <p>查阅短开场（物品提示上按住 Ctrl 打开）：从「亮点拉成扫描线」那一刻开始，开场时间轴 {@value #QUICK_FROM} → {@value #OPEN_MS}
 * 用 {@value #QUICK_MS}ms 走完；不画上电、自检条和锁定方环，括号直接从亮点上被带出去；遮罩约 60ms 淡入。查阅是为了马上看到那一块。</p>
 *
 * <h3>收场分镜（毫秒，从按下关闭算起，共 {@value #CLOSE_MS}）：和开场倒着呼应，「擦除 → 合拢 → 断电」</h3>
 * <pre>
 *  第一幕 擦除
 *    0 ─  340  擦除光束自下而上扫过面板（开场光束倒过来）：光束之下画回空白板材，盖住顶栏、正文、底栏；余辉和干扰条拖在下面
 *    0 ─  220  书名从右往左逐字重新加密成乱码（猩红下划线标出下一个要加密的字）；0 ─ 180 副标题从右往左收起
 *    0 ─  200  外框充能：辉光升到约 2.2 倍、偏白，一直亮到外框收线
 *    0 ─  300  四角括号闪一下（0 ─ 120）转回猩红，随即从外框外 3px 松开到 15px（120 ─ 300）
 *  第二幕 合拢
 *  340 ─  620  面板以中线为轴上下收拢（缓入）；上下两道亮边相向合拢、越收越亮；括号骑在收拢带四角外
 *  320 ─  600  外框收线：四个笔头从顶边 / 底边中点倒着走回左右侧边中点（不受展开带裁剪，面板在收线的外框里合拢）
 *  第三幕 断电
 *  600 ─  760  扫描线两端向中心收回（缓入），括号跟着横线两端滑回亮点四周；700 ─ 760 收成一粒亮点
 *  760         重新锁定：括号闪一下；760 ─ 860 自检条倒数 100 → 000，860 ─ 900 淡掉
 *  820 ─ 1000  括号按左下 → 右下 → 右上 → 左上依次松开、沿对角线飞出淡掉（开场收拢的倒放）
 *  900 ─ 1000  亮点断电：横向闪一下后熄灭
 *  560 ─ 1000  遮罩淡出（缓入）
 * </pre>
 * <p>收场期间任意键（单按修饰键、F1~F12 除外）/ 点击直接关完（{@code GuideScreen}）。开场没走完就关：开场时间轴冻结在按下关闭的那一刻
 * （逐帧累加停止），第一幕的残留（还在路上的括号、自检条、锁定方环）在 {@value #STALE_MS}ms 内淡掉，其余从当时的样子接着收；
 * 面板还没开始展开（开场 {@value #UNFOLD_START}ms 前）就关的，没有可擦除、可收拢的面板，直接接第三幕，整个收场约 560ms，
 * 不让玩家对着一片遮罩干等。</p>
 *
 * <h3>翻页</h3>
 * <p>首页 ↔ 阅读页：正文区横向擦除 {@value #WIPE_MS}ms（缓出）。去阅读页从左往右擦，回首页从右往左；
 * 擦除线两侧分别是新页和旧页，前沿一道竖直亮线拖着余辉。</p>
 *
 * <h3>闪烁安全与开销</h3>
 * <p>没有全屏或大面积的闪：遮罩单调淡入、单调淡出；全息闪烁是固定三次、间隔 120ms、幅度递减（任意一秒至多 3 次，而且可预期）；
 * 锁定那一下、括号落位那一下都是小面积单次闪；锁定方环只扩一次；外框通电 / 充能是 1px 线条的辉光、单次起落。
 * 乱码字形和自检数字是静态预分配的字符串表，字宽在排版时量好；每帧零分配、零测宽。
 * 开场结束后除了四角括号（8 次 fill）和外框，这里什么都不画。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideIntro {

    // ==================== 开场时间轴（毫秒） ====================

    static final float SCRIM_MS = 220f;
    /** 中心亮点通电 */
    static final float DOT_RISE_MS = 120f;
    /** 锁定框：第一个括号出发、相邻两个括号错开、每个括号走多久（最后一个 420 到位） */
    static final float LOCK_START = 60f;
    static final float LOCK_STAGGER = 40f;
    static final float LOCK_TRAVEL = 240f;
    /** 自检条 */
    static final float TEST_START = 180f;
    static final float TEST_END = 420f;
    /** 锁定方环外扩（自检条同时淡出） */
    static final float PING_START = 420f;
    static final float PING_MS = 160f;
    /** 亮点拉成扫描线；亮点同时被横线接走 */
    static final float LINE_START = 460f;
    static final float LINE_MS = 160f;
    static final float DOT_OUT_MS = 60f;
    static final float UNFOLD_START = 580f;
    static final float UNFOLD_MS = 340f;
    static final float TRACE_START = 580f;
    static final float TRACE_MS = 300f;
    /** 外框描完后的通电辉光回落 */
    static final float FLASH_MS = 260f;
    /** 锁定框落位：最后一段 easeOutBack 卡入 */
    static final float SETTLE_START = 920f;
    static final float SETTLE_MS = 120f;
    /** 括号卡到位后那一下闪光的衰减 */
    static final float BRACKET_FLASH_MS = 180f;
    /** 光束匀速扫过整块面板；首页格子按光束到达自己顶边的时刻开始成形 */
    static final float BEAM_START = 800f;
    static final float BEAM_MS = 480f;
    static final float GLITCH_STEP_MS = 32f;
    /** 全息闪烁：固定三次暗跳的时刻、幅度、时长 */
    private static final float[] FLICKER_AT = {860f, 980f, 1100f};
    private static final float[] FLICKER_AMP = {0.30f, 0.20f, 0.10f};
    static final float FLICKER_MS = 38f;
    static final float DECRYPT_START = 860f;
    static final float DECRYPT_END = 1330f;
    /** 乱码从左到右铺满一行用的时间 */
    static final float SCRAMBLE_LEAD = 120f;
    /** 乱码换字形的间隔 */
    static final float SCRAMBLE_STEP_MS = 40f;
    /** 每个字收束时闪白、褪回常态色的时长 */
    static final float SETTLE_FLASH_MS = 70f;
    static final float SUB_START = 1120f;
    static final float SUB_MS = 200f;
    /** 就绪：悬停、提示框、按下态从这里生效（最后 320ms 只剩收尾，不挡操作） */
    static final float READY_MS = 1280f;
    /** 开场结束 */
    static final float OPEN_MS = 1600f;
    /** 面板展开过半：在这之前的点击 / 滚轮只跳过开场、不当成点到了什么 */
    static final float HALF_UNFOLD = UNFOLD_START + UNFOLD_MS / 2f;

    /** 逐帧累加时单帧最多记这么多毫秒 */
    static final float MAX_FRAME_MS = 50f;

    /** 查阅短开场：从开场时间轴的这一刻（亮点拉成扫描线）开始 */
    static final float QUICK_FROM = LINE_START;
    /** 查阅短开场：{@link #QUICK_FROM} → {@link #OPEN_MS} 用这么长的真实时间走完 */
    static final float QUICK_MS = 280f;
    /** 查阅短开场的遮罩淡入（开场时间轴上的毫秒，约合真实 60ms） */
    static final float QUICK_SCRIM_SPAN = 250f;

    // ==================== 收场时间轴（毫秒） ====================

    static final float CLOSE_MS = 1000f;
    /** 开场没走完就关：第一幕的残留在这么长时间里淡掉 */
    static final float STALE_MS = 160f;
    /** 第一幕：擦除光束自下而上 */
    static final float ERASE_MS = 340f;
    /** 外框充能升到峰值 */
    static final float CHARGE_MS = 200f;
    /** 书名逐字重新加密 / 副标题收起 */
    static final float RECRYPT_MS = 220f;
    static final float SUB_BACK_MS = 180f;
    /** 括号闪一下（0 ─ {@value #DETACH_FLASH_MS}），随即松开到外 15px */
    static final float DETACH_FLASH_MS = 120f;
    static final float DETACH_START = 120f;
    static final float DETACH_MS = 180f;
    /** 第二幕：面板收拢、外框收线 */
    static final float FOLD_START = 340f;
    static final float FOLD_MS = 280f;
    static final float RETRACT_START = 320f;
    static final float RETRACT_MS = 280f;
    /** 第三幕：扫描线收回、亮点重新亮起、重新锁定、自检倒数、括号松开、亮点断电 */
    static final float LINE_IN_START = 600f;
    static final float LINE_IN_MS = 160f;
    static final float DOT_BACK_START = 700f;
    static final float DOT_BACK_MS = 60f;
    static final float RELOCK_AT = 760f;
    static final float RELOCK_FLASH_MS = 120f;
    static final float COUNT_START = 760f;
    static final float COUNT_MS = 100f;
    static final float COUNT_FADE_MS = 40f;
    static final float RELEASE_START = 820f;
    static final float RELEASE_STAGGER = 25f;
    static final float RELEASE_MS = 100f;
    static final float DOT_OFF_START = 900f;
    static final float DOT_OFF_MS = 100f;
    static final float SCRIM_OUT_START = 560f;

    // ==================== 翻页 ====================

    static final float WIPE_MS = 140f;

    /** 笔头、光束芯、亮点：带一点青的白 */
    static final int HOT = 0xFFEFFFFF;

    /**
     * 光束、闪烁、擦除板材、中线亮边、亮点这几层叠在内容之上时抬到的 z：物品图标画在 z≈150、数量字在 200、
     * 实体预览更低，留在 z=0 会过不了深度测试 —— 擦除时图标会实心地浮在空白板材上，
     * 光束扫过图标时会从它背后穿过去。300 仍低于 tooltip 的 400（与正文上下渐隐带同一个值）
     */
    private static final float OVERLAY_Z = 300f;

    // ==================== 锁定框几何 ====================

    /** 锁定框锁住亮点时的半边长 */
    private static final int LOCK_R = 9;
    /** 骑在扫描线两端 / 展开带四角时离开带的距离（上限；屏幕留白不够时按 {@link #fitMargin} 收窄） */
    private static final float RIDE_OFF = 15f;
    /** 落位后离外框的距离 */
    private static final float REST_OFF = 3f;
    /** 锁定方环外扩到的半边长 */
    private static final int PING_R = 40;
    /** 收场松开时括号沿对角线飞出去的距离 */
    private static final float RELEASE_DIST = 30f;
    /** 四个括号的方位（左上、右上、右下、左下）：x、y 的符号 */
    private static final int[] SX = {-1, 1, 1, -1};
    private static final int[] SY = {-1, -1, 1, 1};

    // ==================== 乱码字形 / 自检数字（静态预分配） ====================

    /** 窄字位（英文标题）：终端风格的 ASCII */
    private static final char[] NOISE = "0123456789ABCDEF#$%&*+=<>/\\|?!".toCharArray();
    /** 宽字位（中文标题）：方块与斜块，占位感更强；和窄字形按一半概率混用 */
    private static final char[] NOISE_WIDE = "▓▒░█◢◣◤◥".toCharArray();
    private static final String[] NOISE_STR = strings(NOISE);
    private static final String[] NOISE_WIDE_STR = strings(NOISE_WIDE);
    /** 自检条右侧的三位数字 000 ~ 100 */
    private static final String[] PERCENT = new String[101];
    /** 自检条：格数、每格宽高、格间距 */
    private static final int TEST_CELLS = 9;
    private static final int TEST_CELL_W = 3;
    private static final int TEST_CELL_H = 2;

    static {
        for (int i = 0; i <= 100; i++) {
            PERCENT[i] = String.format("%03d", i);
        }
    }

    private static String[] strings(char[] chars) {
        String[] out = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            out[i] = String.valueOf(chars[i]);
        }
        return out;
    }

    // ==================== 本帧状态（update 算好，绘制时只读） ====================

    /** 开场时间轴上的位置（毫秒，封顶 {@link #OPEN_MS}）；收场时冻结在按下关闭的那一刻 */
    float openT;
    /** 收场开始后的毫秒数（面板没展开就关时往后拨到第三幕）；没在收场为 -1 */
    float closeT = -1f;
    float scrim;
    /** 中线宽度占面板宽的比例 */
    float line;
    /** 面板展开高度占面板高的比例 */
    float unfold;
    /** 外框描线进度（收场时倒着退回 0） */
    float trace;
    /** 扫描光束位置占面板高的比例；1 = 扫完 */
    float beam;
    /** 收场时擦除光束扫过的比例（开场光束跟着它淡掉） */
    float erase;
    /** 收场时第一幕残留的淡出程度（开场走完才关时用不上） */
    float stale;
    /** 短开场（查阅打开） */
    boolean quick;

    // ==================== 几何与书名排版 ====================

    private int px;
    private int py;
    private int pw;
    private int ph;

    private String title = "";
    private String subtitle = "";
    private int subtitleW;
    private String[] glyphs = new String[0];
    private int[] glyphX = new int[0];
    private int[] glyphW = new int[0];
    private boolean[] blank = new boolean[0];
    private final int[] noiseW = new int[NOISE.length];
    private final int[] noiseWideW = new int[NOISE_WIDE.length];

    void setPanel(int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
    }

    /**
     * 书名按字拆开、量好每个字的位置与宽度（只在排版时做一次）
     *
     * <p>解密时每个字位按<b>真名那个字的宽度</b>占位，乱码字形在字位里居中 ——
     * 乱码和真名宽窄不一，整串一起画的话后面的字会跟着左右抖。</p>
     */
    void prepareTitle(Font font, String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        this.subtitleW = font.width(subtitle);
        int n = title.codePointCount(0, title.length());
        glyphs = new String[n];
        glyphX = new int[n];
        glyphW = new int[n];
        blank = new boolean[n];
        int x = 0;
        int i = 0;
        for (int off = 0; off < title.length() && i < n; i++) {
            int cp = title.codePointAt(off);
            int len = Character.charCount(cp);
            String s = title.substring(off, off + len);
            glyphs[i] = s;
            glyphX[i] = x;
            glyphW[i] = font.width(s);
            blank[i] = Character.isWhitespace(cp);
            x += glyphW[i];
            off += len;
        }
        for (int k = 0; k < NOISE_STR.length; k++) {
            noiseW[k] = font.width(NOISE_STR[k]);
        }
        for (int k = 0; k < NOISE_WIDE_STR.length; k++) {
            noiseWideW[k] = font.width(NOISE_WIDE_STR[k]);
        }
    }

    // ==================== 时间轴 ====================

    /** 逐帧累加的开场时钟走到这里就停（完整开场 {@value #OPEN_MS}、短开场 {@value #QUICK_MS}） */
    float introEnd() {
        return quick ? QUICK_MS : OPEN_MS;
    }

    /**
     * 按当前时间算出本帧各段的进度
     *
     * @param introT    逐帧累加的开场时钟（毫秒；短开场是真实时间，这里换算到开场时间轴）
     * @param closingAt 开始收场的时刻（{@code Util.getMillis()}），没在收场为 0
     * @param now       {@code Util.getMillis()}
     */
    void update(float introT, long closingAt, long now) {
        float t = Math.max(0f, introT);
        float mapped = quick ? QUICK_FROM + t * (OPEN_MS - QUICK_FROM) / QUICK_MS : t;
        openT = Math.min(mapped, OPEN_MS);
        scrim = quick ? GuideTheme.easeOutCubic((openT - QUICK_FROM) / QUICK_SCRIM_SPAN) : GuideTheme.easeOutCubic(openT / SCRIM_MS);
        line = GuideTheme.easeOutCubic((openT - LINE_START) / LINE_MS);
        unfold = GuideTheme.easeOutCubic((openT - UNFOLD_START) / UNFOLD_MS);
        trace = GuideTheme.easeOutCubic((openT - TRACE_START) / TRACE_MS);
        beam = Mth.clamp((openT - BEAM_START) / BEAM_MS, 0f, 1f);
        erase = 0f;
        stale = 0f;
        if (closingAt == 0L) {
            closeT = -1f;
            return;
        }
        float closeRaw = Math.max(0f, now - closingAt);
        closeT = closeRaw;
        stale = GuideTheme.easeOutCubic(closeRaw / STALE_MS);
        if (unfold <= 0f) {
            // 面板还没开始展开就关了：没有可擦除、可收拢的面板，第一幕的残留淡掉后直接接第三幕（扫描线收回、重新锁定、断电），
            // 不让玩家对着一片遮罩干等 440ms
            closeT = closeRaw + (LINE_IN_START - STALE_MS);
        }
        erase = Mth.clamp(closeT / ERASE_MS, 0f, 1f);
        unfold *= 1f - GuideTheme.easeInCubic((closeT - FOLD_START) / FOLD_MS);
        trace *= 1f - GuideTheme.easeInCubic((closeT - RETRACT_START) / RETRACT_MS);
        line *= 1f - GuideTheme.easeInCubic((closeT - LINE_IN_START) / LINE_IN_MS);
        scrim *= 1f - GuideTheme.easeInCubic((closeT - SCRIM_OUT_START) / (CLOSE_MS - SCRIM_OUT_START));
    }

    /** 收场走完：可以真正关掉界面了 */
    boolean closed() {
        return closeT >= CLOSE_MS;
    }

    /** 就绪且没在收场：悬停、提示框、按下态生效 */
    boolean ready() {
        return closeT < 0f && openT >= READY_MS;
    }

    /** 面板还没展开过半（这时的点击 / 滚轮只跳过开场） */
    boolean early() {
        return closeT < 0f && openT < HALF_UNFOLD;
    }

    /** 面板当前露出来的高度（以面板中线为轴上下展开） */
    int bandHeight() {
        return unfold >= 1f ? ph : Math.round(ph * unfold);
    }

    int bandTop() {
        return py + (ph - bandHeight()) / 2;
    }

    /** 光束所在行：它上面的内容已经显现 */
    int revealBottom() {
        return beam >= 1f ? py + ph : py + Math.round(ph * beam);
    }

    /** 外框描完了（画常态外框）；收场收线时回到 false，改画倒着走的描线 */
    boolean traceDone() {
        return trace >= 1f;
    }

    private float outlineFlash() {
        float since = openT - TRACE_START - TRACE_MS;
        return since < 0f ? 1f : Math.max(0f, 1f - since / FLASH_MS);
    }

    /** 收场时外框充能的程度（0 ~ 1，升到顶后一直保持到收线） */
    private float charge() {
        return closeT < 0f ? 0f : GuideTheme.easeOutCubic(closeT / CHARGE_MS);
    }

    /** 外框辉光强度：开场描完那一刻冲到 2.5 倍、{@value #FLASH_MS}ms 内回落；收场充能到约 2.2 倍 */
    float glowStrength() {
        return Math.max(1f + 1.5f * outlineFlash(), 1f + 1.2f * charge());
    }

    /** 外框线色：通电 / 充能时偏白，常态科技青 */
    int outlineColor() {
        float hot = Math.max(outlineFlash() * 0.6f, charge() * 0.5f);
        return GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.TECH, 0xB0), HOT, hot);
    }

    // ==================== 中心：亮点、自检条、锁定方环、中线 ====================

    /**
     * 中心亮点、自检条、锁定方环，以及中线 / 上下两道亮边
     *
     * <p>面板还没展开时是屏幕正中的一条线；展开中分成两道，贴着裁剪带的上下沿外推、渐隐；
     * 收场时反过来相向合拢，再向中心缩回亮点。</p>
     *
     * <p>和盖层一样抬到 {@link #OVERLAY_Z}：两道亮边落在展开带<b>里面</b>，擦除板材（开场时的全息闪烁）
     * 已经在这些像素上写了 z=300 的深度，留在 z=0 画会整条过不了深度测试。</p>
     */
    void drawSeam(GuiGraphics g) {
        float dot = dotLevel();
        float test = testLevel();
        float ping = closeT < 0f || stale < 1f ? pingLevel() : 0f;
        float k = 1f - unfold;
        boolean lines = k > 0.002f && line > 0.002f;
        if (dot <= 0.01f && test <= 0.01f && ping <= 0f && !lines) {
            // 开场走完后每帧都走到这里：先判空再 pushPose（它每次都新建矩阵），常态下零分配
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        int cx = px + pw / 2;
        int cy = py + ph / 2;
        if (ping > 0f) {
            pingLayer(g, cx, cy, ping);
        }
        if (test > 0.01f) {
            testLayer(g, cx, cy, test);
        }
        if (lines) {
            seamLines(g, cy, k);
        }
        if (dot > 0.01f) {
            dotLayer(g, cx, cy, dot);
        }
        g.pose().popPose();
    }

    /** 中心亮点的亮度：开场通电后稳定亮着、被横线接走；收场在横线收回时重新亮起，最后断电 */
    private float dotLevel() {
        float open = 0f;
        if (!quick || openT < LINE_START + DOT_OUT_MS) {
            open = quick ? 1f : GuideTheme.easeOutCubic(openT / DOT_RISE_MS);
            open *= 1f - Mth.clamp((openT - LINE_START) / DOT_OUT_MS, 0f, 1f);
        }
        if (closeT < 0f) {
            return open;
        }
        float back = Mth.clamp((closeT - DOT_BACK_START) / DOT_BACK_MS, 0f, 1f);
        float off = 1f - Mth.clamp((closeT - DOT_OFF_START) / DOT_OFF_MS, 0f, 1f);
        return Math.max(open, back) * off;
    }

    /** 自检条的可见度：开场 180 ─ 420 点亮、420 ─ 580 淡出；收场 760 ─ 860 倒数、860 ─ 900 淡出 */
    private float testLevel() {
        float open = 0f;
        if (!quick && openT >= TEST_START) {
            open = 1f - Mth.clamp((openT - PING_START) / PING_MS, 0f, 1f);
            if (closeT >= 0f) {
                open *= 1f - stale;
            }
        }
        float close = 0f;
        if (closeT >= COUNT_START) {
            close = 1f - Mth.clamp((closeT - COUNT_START - COUNT_MS) / COUNT_FADE_MS, 0f, 1f);
        }
        return Math.max(open, close);
    }

    /** 锁定方环的进度（0 = 没在画）：开场 420 ─ 580 外扩一次 */
    private float pingLevel() {
        if (quick || openT < PING_START || openT >= PING_START + PING_MS) {
            return 0f;
        }
        return Math.max(0.001f, (openT - PING_START) / PING_MS);
    }

    private void dotLayer(GuiGraphics g, int cx, int cy, float a) {
        g.fill(cx - 4, cy - 1, cx + 4, cy + 2, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x60 * a)));
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x60 * a)));
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, CodexTheme.withAlpha(HOT, (int) (0xFF * a)));
        if (closeT >= DOT_OFF_START) {
            // 断电：像显像管关机那样横向闪一下再熄灭
            float p = Mth.clamp((closeT - DOT_OFF_START) / DOT_OFF_MS, 0f, 1f);
            float flare = p < 0.3f ? p / 0.3f : Math.max(0f, 1f - (p - 0.3f) / 0.7f);
            int half = 1 + Math.round(10f * flare);
            g.fill(cx - half, cy, cx + half + 1, cy + 1, CodexTheme.withAlpha(HOT, (int) (0xC0 * flare)));
        }
    }

    /**
     * 自检条：9 格分段条 + 三位像素数字。开场从左往右点亮、数字 000 → 100；收场倒着熄、数字 100 → 000
     */
    private void testLayer(GuiGraphics g, int cx, int cy, float a) {
        float p;
        if (closeT >= COUNT_START) {
            p = 1f - Mth.clamp((closeT - COUNT_START) / COUNT_MS, 0f, 1f);
        } else {
            p = Mth.clamp((openT - TEST_START) / (TEST_END - TEST_START), 0f, 1f);
        }
        int barW = TEST_CELLS * TEST_CELL_W + (TEST_CELLS - 1);
        String num = PERCENT[Math.round(p * 100f)];
        int numW = GuideTheme.digitsWidth(num);
        int total = barW + 4 + numW;
        int x = cx - total / 2;
        int y = cy + LOCK_R + 6;
        int lit = closeT >= COUNT_START ? (int) Math.ceil(TEST_CELLS * p - 1e-4f) : (int) (TEST_CELLS * p);
        int on = CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0xE0 * a));
        int off = CodexTheme.withAlpha(GuideTheme.EDGE, (int) (0xE0 * a));
        for (int i = 0; i < TEST_CELLS; i++) {
            int sx = x + i * (TEST_CELL_W + 1);
            g.fill(sx, y + 2, sx + TEST_CELL_W, y + 2 + TEST_CELL_H, i < lit ? on : off);
        }
        int nx = x + barW + 4;
        GuideTheme.digits(g, num, nx + (numW - 1) / 2, y, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0xE0 * a)));
    }

    /** 锁定方环：一圈斜切方环从锁定框的大小外扩到 {@value #PING_R}px、淡出 */
    private void pingLayer(GuiGraphics g, int cx, int cy, float p) {
        float e = GuideTheme.easeOutCubic(p);
        int r = Math.round(LOCK_R + (PING_R - LOCK_R) * e);
        float a = (1f - p) * (closeT >= 0f ? 1f - stale : 1f);
        if (a <= 0.01f) {
            return;
        }
        int cut = Math.max(2, r / 4);
        CodexTheme.chamferOutline(g, cx - r, cy - r, r * 2, r * 2, cut, CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0xC0 * a)));
        CodexTheme.chamferOutline(g, cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, cut + 1,
                CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0x30 * a)));
    }

    /**
     * @param k 亮边强度（1 − 展开量）
     */
    private void seamLines(GuiGraphics g, int cy, float k) {
        float a = (float) Math.sqrt(k);
        int lw = Math.max(1, Math.round(pw * line));
        int lx = px + (pw - lw) / 2;
        int band = bandHeight();
        if (band <= 1) {
            glowLine(g, lx, cy, lw, a);
            return;
        }
        int top = bandTop();
        glowLine(g, lx, top, lw, a);
        glowLine(g, lx, top + band - 1, lw, a);
    }

    /** 一道带上下辉光的亮线（中段实、两端渐隐） */
    private static void glowLine(GuiGraphics g, int x, int y, int w, float a) {
        int outer = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x24 * a));
        int inner = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x70 * a));
        GuideTheme.hairline(g, x, y - 2, w, outer);
        GuideTheme.hairline(g, x, y - 1, w, inner);
        GuideTheme.hairline(g, x, y, w, CodexTheme.withAlpha(HOT, (int) (0xF0 * a)));
        GuideTheme.hairline(g, x, y + 1, w, inner);
        GuideTheme.hairline(g, x, y + 2, w, outer);
    }

    // ==================== 外框描线 ====================

    /**
     * 外框描线（描完之后由界面画常态外框，这里不再画）；收场时同一段弧倒着收回左右侧边中点
     *
     * <p>从左右侧边中点（弧长 1/4、3/4 周处）出发，各自向上下两个方向走，
     * 走满 1/4 周时四个笔头在顶边、底边中点合拢。外面同步描一圈淡辉光，笔头 6px 亮白。
     * 不受展开带裁剪：开场时跑在展开前面，收场时面板在收线的外框里合拢。</p>
     */
    void drawTrace(GuiGraphics g, int cut) {
        if (trace <= 0f || trace >= 1f) {
            return;
        }
        float lift = charge();
        int per = CodexTheme.chamferPerimeter(pw, ph, cut);
        int perGlow = CodexTheme.chamferPerimeter(pw + 2, ph + 2, cut + 1);
        float d = trace * per / 4f;
        float dGlow = trace * perGlow / 4f;
        int lineColor = GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.TECH, 0xD0), HOT, lift * 0.4f);
        int glowColor = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x38 + 0x30 * lift));
        for (int side = 1; side <= 3; side += 2) {
            float o = per / 4f * side;
            float og = perGlow / 4f * side;
            CodexTheme.chamferArc(g, px - 1, py - 1, pw + 2, ph + 2, cut + 1, og - dGlow, og + dGlow, glowColor);
            CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o + d, lineColor);
            if (d > 6f) {
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o - d + 6f, HOT);
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o + d - 6f, o + d, HOT);
            }
        }
    }

    // ==================== 锁定框 ====================

    /**
     * 四个瞄准括号：开场从面板四角收拢锁住亮点 → 骑着扫描线两端、展开带四角被带到面板四角 → 卡到外框外 3px 落位，
     * 之后常驻一层淡青；收场倒着走一遍（松开 → 骑着收拢带、扫描线回到亮点 → 重新锁定 → 依次飞出）
     *
     * <p>位置用一个矩形描述：水平方向在「亮点四周 {@value #LOCK_R}px」和「面板左右外 off」之间按扫描线的长度插值，
     * 竖直方向按展开量插值 —— 扫描线和展开带收放时括号自然跟着走，开场、收场共用同一套算式。</p>
     */
    void drawBrackets(GuiGraphics g) {
        if (openT < LOCK_START && !quick) {
            return;
        }
        float cx = px + pw / 2f;
        float cy = py + ph / 2f;
        float off = offset();
        float offX = fitMargin(off, px);
        float offY = fitMargin(off, py);
        float left = Mth.lerp(line, cx - LOCK_R, px - offX);
        float right = Mth.lerp(line, cx + LOCK_R, px + pw + offX);
        float top = Mth.lerp(unfold, cy - LOCK_R, py - offY);
        float bottom = Mth.lerp(unfold, cy + LOCK_R, py + ph + offY);
        boolean rest = closeT < 0f && openT >= SETTLE_START + SETTLE_MS;
        int len = rest ? 7 : 6;
        for (int k = 0; k < 4; k++) {
            float travel = quick ? 1f : Mth.clamp((openT - LOCK_START - k * LOCK_STAGGER) / LOCK_TRAVEL, 0f, 1f);
            if (travel <= 0f) {
                continue;
            }
            boolean arrived = travel >= 1f;
            // 目标角上那个像素：左 / 上取矩形边，右 / 下取边内一格（同 CodexTheme.brackets 的画法）
            float tx = SX[k] < 0 ? left : right - 1;
            float ty = SY[k] < 0 ? top : bottom - 1;
            float x = tx;
            float y = ty;
            float alpha = 1f;
            if (!arrived) {
                // 还在路上：从面板四角沿对角线收向亮点（缓出），刚出发时淡入
                float sx = SX[k] < 0 ? px - REST_OFF : px + pw + REST_OFF - 1;
                float sy = SY[k] < 0 ? py - REST_OFF : py + ph + REST_OFF - 1;
                float e = GuideTheme.easeOutCubic(travel);
                x = Mth.lerp(e, sx, tx);
                y = Mth.lerp(e, sy, ty);
                alpha = Math.min(1f, travel / 0.25f);
                if (closeT >= 0f) {
                    // 开场第一幕就关了：还没锁上的括号算残留，跟着淡掉
                    alpha *= 1f - stale;
                }
            } else if (closeT >= RELEASE_START) {
                // 收场最后：左下 → 右下 → 右上 → 左上依次松开，沿对角线飞出淡掉
                int order = 3 - k;
                float r = Mth.clamp((closeT - RELEASE_START - order * RELEASE_STAGGER) / RELEASE_MS, 0f, 1f);
                float e = GuideTheme.easeInCubic(r);
                x += SX[k] * RELEASE_DIST * e;
                y += SY[k] * RELEASE_DIST * e;
                alpha = 1f - r;
            }
            if (alpha <= 0.01f) {
                continue;
            }
            int color = bracketColor(arrived, alpha);
            corner(g, Math.round(x), Math.round(y), SX[k], SY[k], len, color);
        }
    }

    /** 括号离面板（或展开带、扫描线两端）的距离：骑行时 15px，落位卡到 3px；收场先松开回 15px */
    private float offset() {
        float open = RIDE_OFF;
        if (openT >= SETTLE_START) {
            float s = Mth.clamp((openT - SETTLE_START) / SETTLE_MS, 0f, 1f);
            open = RIDE_OFF - (RIDE_OFF - REST_OFF) * GuideTheme.easeOutBack(s);
        }
        if (closeT < 0f) {
            return open;
        }
        return Mth.lerp(GuideTheme.easeInCubic((closeT - DETACH_START) / DETACH_MS), open, RIDE_OFF);
    }

    /**
     * 骑行距离按这一侧的屏幕留白收窄：面板离屏幕边只有 10~12px（小屏 4~6px），照 {@value #RIDE_OFF}px 骑的话，
     * 扫描线拉满、面板展开的整段里括号都在屏幕外，落位时才从屏幕外弹回来。
     * 把 [{@value #REST_OFF}, {@value #RIDE_OFF}] 按比例压进 [{@value #REST_OFF}, 留白 − 2]，角上那个像素离屏幕边至少 2px；
     * 落位时 easeOutBack 回弹到 {@value #REST_OFF}px 以内的那一下原样保留
     *
     * @param margin 面板这一侧到屏幕边的距离（面板居中，左右、上下各自相等）
     */
    private static float fitMargin(float off, int margin) {
        float ride = Mth.clamp(margin - 2f, REST_OFF, RIDE_OFF);
        if (off <= REST_OFF || ride >= RIDE_OFF) {
            return off;
        }
        return REST_OFF + (off - REST_OFF) * (ride - REST_OFF) / (RIDE_OFF - REST_OFF);
    }

    private int bracketColor(boolean arrived, float alpha) {
        if (!arrived) {
            // 途中科技青
            return CodexTheme.withAlpha(GuideTheme.TECH, (int) (0xE0 * alpha));
        }
        if (closeT >= 0f) {
            // 收场：落着位的括号先由淡青闪亮、再转成猩红（重新被「锁住」）；重新锁定那一刻再闪一下
            int kuva = CodexTheme.withAlpha(GuideTheme.KUVA, 0xE0);
            int c;
            if (openT >= SETTLE_START + SETTLE_MS && closeT < DETACH_FLASH_MS) {
                float p = closeT / DETACH_FLASH_MS;
                c = p < 0.4f ? GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.TECH, 0x60), CodexTheme.withAlpha(HOT, 0xF0), p / 0.4f)
                        : GuideTheme.lerpColor(CodexTheme.withAlpha(HOT, 0xF0), kuva, (p - 0.4f) / 0.6f);
            } else if (closeT >= RELOCK_AT && closeT < RELOCK_AT + RELOCK_FLASH_MS) {
                c = GuideTheme.lerpColor(kuva, CodexTheme.withAlpha(HOT, 0xF0), 0.7f * (1f - (closeT - RELOCK_AT) / RELOCK_FLASH_MS));
            } else {
                c = kuva;
            }
            return CodexTheme.withAlpha(c, (int) (((c >>> 24) & 0xFF) * alpha));
        }
        if (openT < SETTLE_START) {
            // 锁住了：猩红
            return CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0xE0 * alpha));
        }
        float s = Mth.clamp((openT - SETTLE_START) / SETTLE_MS, 0f, 1f);
        if (s < 1f) {
            // 卡入途中：猩红 → 亮白
            return GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.KUVA, 0xE0), CodexTheme.withAlpha(HOT, 0xFF), s);
        }
        // 落位：闪一下后留一层淡青
        float since = openT - SETTLE_START - SETTLE_MS;
        float flash = Math.max(0f, 1f - since / BRACKET_FLASH_MS);
        int a = (int) ((0x60 + 0x9F * flash) * alpha);
        return CodexTheme.withAlpha(GuideTheme.lerpColor(GuideTheme.TECH, HOT, flash * 0.5f), a);
    }

    /**
     * 一个角括号：(x, y) 是角上那个像素，两条臂朝面板中心伸出去
     *
     * @param sx 水平方位（-1 左、+1 右）
     * @param sy 竖直方位（-1 上、+1 下）
     */
    private static void corner(GuiGraphics g, int x, int y, int sx, int sy, int len, int color) {
        int hx = sx < 0 ? x : x - len + 1;
        g.fill(hx, y, hx + len, y + 1, color);
        int vy = sy < 0 ? y : y - len + 1;
        g.fill(x, vy, x + 1, vy + len, color);
    }

    // ==================== 光束、闪烁、擦除 ====================

    /**
     * 开场扫描光束 + 余辉 + 信号干扰条（只在光束扫动期间；收场时跟着擦除淡掉）
     */
    void drawBeam(GuiGraphics g, int cut) {
        if (beam <= 0f || beam >= 1f) {
            return;
        }
        float a = 1f - erase;
        if (a <= 0.01f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        beamLayer(g, cut, revealBottom(), a, true, openT);
        g.pose().popPose();
    }

    /**
     * 收场第一幕：擦除光束自下而上，光束之下画回空白板材（底色 + 全息扫描线），盖住顶栏、正文、底栏
     *
     * <p>板材往里收 1px：外框那一圈不写 z=300 的深度，之后在 z=0 画的外框、收线笔头才不会被挡住。</p>
     */
    void drawErase(GuiGraphics g, int cut) {
        if (closeT < 0f || erase <= 0f) {
            return;
        }
        int sealY = py + ph - Math.round(ph * erase);
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        if (sealY < py + ph - 1) {
            g.enableScissor(px, Math.max(py, sealY), px + pw, py + ph);
            CodexTheme.chamferFill(g, px + 1, py + 1, pw - 2, ph - 2, cut - 1, GuideTheme.PLATE);
            CodexTheme.holoSurface(g, px + 2, py + 2, pw - 4, ph - 4, 10);
            g.disableScissor();
        }
        if (erase < 1f) {
            beamLayer(g, cut, sealY, 1f, false, closeT);
        }
        g.pose().popPose();
    }

    /**
     * 一道扫过面板的光束：整行科技青、中段亮白芯，走过的一侧拖 14 行二次衰减的余辉，前方一点散光，
     * 走过的一侧几条跳动的信号干扰条（每 {@value #GLITCH_STEP_MS}ms 换一次位置；青 / 猩红错开 2px 做色偏）
     *
     * @param by   光束所在行
     * @param a    整体不透明度
     * @param down 向下扫（开场显现）；false = 向上扫（收场擦除）
     * @param t    换干扰条位置用的时钟
     */
    private void beamLayer(GuiGraphics g, int cut, int by, float a, boolean down, float t) {
        int behind = down ? -1 : 1;
        for (int k = 14; k >= 1; k--) {
            float f = 1f - k / 15f;
            row(g, by + behind * k, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x46 * f * f * a)));
        }
        row(g, by - behind, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x38 * a)));
        row(g, by - behind * 2, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x14 * a)));
        row(g, by, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0xF0 * a)));
        if (by > py && by < py + ph - 1) {
            int in = inset(by, cut);
            GuideTheme.hairline(g, px + in, by, pw - in * 2, CodexTheme.withAlpha(HOT, (int) (0xE0 * a)));
        }
        int span = pw - cut * 2 - 8;
        if (span <= 64) {
            return;
        }
        int bucket = (int) (t / GLITCH_STEP_MS) + (down ? 0 : 7919);
        for (int k = 0; k < 4; k++) {
            int h = hash(bucket * 7 + k);
            int yy = by + behind * (2 + h % 22);
            if (yy <= py + 1 || yy >= py + ph - 2) {
                continue;
            }
            int len = 10 + (h >>> 8) % 50;
            int xx = px + cut + 4 + (h >>> 14) % (span - len);
            boolean red = ((h >>> 24) & 3) == 0;
            int main = red ? CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0x60 * a))
                    : CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x70 * a));
            int ghost = red ? CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x30 * a))
                    : CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0x2A * a));
            g.fill(xx, yy, xx + len, yy + 1, main);
            g.fill(xx + 2, yy + 1, xx + len + 2, yy + 2, ghost);
        }
    }

    /**
     * 全息闪烁：固定三次暗跳（{@code FLICKER_AT}），幅度递减，每次 {@value #FLICKER_MS}ms ——
     * 原先每 38ms 随机抽一次、3/8 概率暗跳，运气差时一秒里能暗跳四五次
     */
    void drawFlicker(GuiGraphics g, int cut) {
        if (closeT >= 0f || openT < FLICKER_AT[0] || openT >= FLICKER_AT[FLICKER_AT.length - 1] + FLICKER_MS) {
            return;
        }
        for (int i = 0; i < FLICKER_AT.length; i++) {
            if (openT >= FLICKER_AT[i] && openT < FLICKER_AT[i] + FLICKER_MS) {
                overlay(g, cut, CodexTheme.fade(GuideTheme.PLATE, FLICKER_AMP[i]));
                return;
            }
        }
    }

    /** 盖满面板内部的一层（抬到 {@link #OVERLAY_Z}，连物品图标一起盖住） */
    private void overlay(GuiGraphics g, int cut, int color) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        CodexTheme.chamferFill(g, px + 1, py + 1, pw - 2, ph - 2, cut - 1, color);
        g.pose().popPose();
    }

    // ==================== 书名 ====================

    /**
     * 顶栏书名（解密 / 收场时重新加密）+ 副标题（打字式展开 / 收起）；开场走完、没在收场时就是两次普通的 drawString
     *
     * @param x    文字左
     * @param y    书名行顶
     * @param subY 副标题行顶
     */
    void drawTitle(GuiGraphics g, Font font, int x, int y, int subY) {
        if (openT >= DECRYPT_END && closeT < 0f) {
            g.drawString(font, title, x, y, GuideTheme.BONE, true);
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            return;
        }
        if (openT >= DECRYPT_START && glyphs.length > 0) {
            drawGlyphs(g, font, x, y);
        }
        float sp = GuideTheme.easeOutCubic((openT - SUB_START) / SUB_MS);
        if (closeT >= 0f) {
            sp *= 1f - GuideTheme.easeInCubic(closeT / SUB_BACK_MS);
        }
        if (sp <= 0f) {
            return;
        }
        if (sp >= 1f) {
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            return;
        }
        int rw = Math.round(subtitleW * sp);
        if (rw > 0) {
            g.enableScissor(x, subY - 1, x + rw, subY + 10);
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            g.disableScissor();
        }
        g.fill(x + rw, subY, x + rw + 2, subY + 8, CodexTheme.withAlpha(GuideTheme.TECH, 0xC0));
    }

    /**
     * 书名逐字：开场时乱码从左到右铺开、再逐字收束成真名；收场时从右往左逐字重新加密成乱码。
     * 猩红下划线标出正在变的那个字（开场是下一个要解密的，收场是下一个要加密的）
     */
    private void drawGlyphs(GuiGraphics g, Font font, int x, int y) {
        int n = glyphs.length;
        float t = openT - DECRYPT_START;
        float settleSpan = DECRYPT_END - DECRYPT_START - SCRAMBLE_LEAD - SETTLE_FLASH_MS;
        boolean closing = closeT >= 0f;
        float phase = closing ? t + closeT : t;
        int cursor = -1;
        for (int i = 0; i < n; i++) {
            if (t < SCRAMBLE_LEAD * i / n) {
                // 乱码还没铺到这里，后面的更没到
                break;
            }
            if (blank[i]) {
                continue;
            }
            int gx = x + glyphX[i];
            float settleAt = SCRAMBLE_LEAD + settleSpan * (i + 1) / n;
            boolean real = t >= settleAt;
            if (real && closing) {
                // 从右往左重新加密：最后一个字最先变回乱码
                if (closeT >= RECRYPT_MS * (n - 1 - i) / n) {
                    real = false;
                } else {
                    cursor = i;
                }
            }
            if (real) {
                float k = closing ? 1f : (t - settleAt) / SETTLE_FLASH_MS;
                g.drawString(font, glyphs[i], gx, y, GuideTheme.lerpColor(0xFFFFFFFF, GuideTheme.BONE, k), true);
                continue;
            }
            if (!closing && cursor < 0) {
                cursor = i;
            }
            // 每个字位各自的相位，免得整串乱码同一帧一起跳
            int h = hash(i * 131 + (int) ((phase + i * 17f) / SCRAMBLE_STEP_MS));
            String s;
            int sw;
            if (glyphW[i] >= 8 && (h & 1) == 0) {
                int idx = (h >>> 1) % NOISE_WIDE_STR.length;
                s = NOISE_WIDE_STR[idx];
                sw = noiseWideW[idx];
            } else {
                int idx = (h >>> 1) % NOISE_STR.length;
                s = NOISE_STR[idx];
                sw = noiseW[idx];
            }
            int nx = gx + (glyphW[i] - sw) / 2;
            // 色偏：猩红残影错开 1px 垫在底下
            g.drawString(font, s, nx + 1, y, CodexTheme.withAlpha(GuideTheme.KUVA, 0x70), false);
            g.drawString(font, s, nx, y, CodexTheme.withAlpha(GuideTheme.TECH, 0xE0), false);
        }
        if (cursor >= 0) {
            int cx = x + glyphX[cursor];
            g.fill(cx, y + 10, cx + Math.max(2, glyphW[cursor] - 1), y + 11, GuideTheme.KUVA);
        }
    }

    // ==================== 翻页 ====================

    /**
     * 翻页擦除的前沿：一道竖直亮线，新页那一侧拖 8px 余辉
     *
     * @param x         前沿所在列
     * @param y         上
     * @param h         高
     * @param rightward 是否从左往右擦（新页在左侧）
     * @param left      擦除区左边界（余辉不越界）
     * @param right     擦除区右边界（不含）
     */
    static void drawWipeFront(GuiGraphics g, int x, int y, int h, boolean rightward, int left, int right) {
        // 同样抬到 OVERLAY_Z：去阅读页时余辉拖在新页（正文）一侧，正文上下 8px 的渐隐带已经写了 z=300 的深度
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        wipeFrontLayer(g, x, y, h, rightward, left, right);
        g.pose().popPose();
    }

    private static void wipeFrontLayer(GuiGraphics g, int x, int y, int h, boolean rightward, int left, int right) {
        int dir = rightward ? -1 : 1;
        for (int k = 8; k >= 1; k--) {
            int xx = x + dir * k;
            if (xx < left || xx >= right) {
                continue;
            }
            float f = 1f - k / 9f;
            g.fill(xx, y, xx + 1, y + h, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x48 * f * f)));
        }
        if (x >= left && x < right) {
            g.fill(x, y, x + 1, y + h, CodexTheme.withAlpha(GuideTheme.TECH, 0xE0));
            g.fill(x, y + h / 4, x + 1, y + h - h / 4, CodexTheme.withAlpha(HOT, 0xB0));
        }
    }

    // ==================== 工具 ====================

    /** 面板在第 yy 行的左右内缩：斜切角那几行要往里收，光效不能画到切掉的角上 */
    private int inset(int yy, int cut) {
        int top = yy - py;
        int bottom = py + ph - 1 - yy;
        int in = 1;
        if (top < cut) {
            in = Math.max(in, cut - top);
        }
        if (bottom < cut) {
            in = Math.max(in, cut - bottom);
        }
        return in;
    }

    /** 面板内的一整行（按斜切角内缩；最上最下一行是外框，不画） */
    private void row(GuiGraphics g, int yy, int cut, int color) {
        if (yy <= py || yy >= py + ph - 1 || (color >>> 24) == 0) {
            return;
        }
        int in = inset(yy, cut);
        g.fill(px + in, yy, px + pw - in, yy + 1, color);
    }

    /** 整数哈希（非负）：乱码、干扰条用的伪随机，不分配 Random */
    static int hash(int v) {
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return v & 0x7FFFFFFF;
    }
}
