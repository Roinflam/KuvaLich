package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 冒险指南的观感项：{@code kuvalich-tooltip.toml} 的 {@code [guide]} 小节（CLIENT 配置，每台电脑自己定，服务器不管、不同步）
 *
 * <p>由 {@link TooltipConfig} 的静态块调 {@link #define} 加进同一个 builder —— 不另开第二个 CLIENT 文件，
 * 也不给 {@code kuvalich-tooltip.toml} 改名（改名会丢掉玩家已有的面板设置）。</p>
 * <ul>
 *   <li>{@code gui_scale = 0}（默认）：自动 —— 游戏选「自动」时取不超过游戏倍数、虚拟屏幕至少 560×320 的最大倍数（最小 2），
 *       游戏选了手动倍数就跟游戏；</li>
 *   <li>{@code -1}：总跟游戏的界面缩放一样；</li>
 *   <li>{@code 1~10}：指定倍数，不会比游戏的界面缩放更大（超过时按游戏倍数画、配置不改）。</li>
 * </ul>
 * <p>指南顶栏的「Aa」按钮、Ctrl + 滚轮、Ctrl − / = / 0 改的也是这一项（{@link #set}），会写回文件。</p>
 * <p>同一小节里还有 {@code intro_animation}：开场、收场动画开不开（默认开），顶栏「Aa」左边那颗按钮切换（{@link #setIntroAnimation}），也写回文件。</p>
 *
 * <p>只有 ForgeConfigSpec，不引用客户端类：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 读值在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段 —— 打开指南时配置万一还没加载，
 * 直接 {@code get()} 会抛异常。改文件后 Forge 的文件监视触发 Reloading，不用重启。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GuideDisplayConfig {

    /** 自动（同 {@code GuideScaleMath.AUTO}；这里是 common 类，不引用客户端包里的常量） */
    public static final int AUTO = 0;
    /** 总跟游戏一样 */
    public static final int FOLLOW_GAME = -1;
    /** 指定倍数的上限 */
    public static final int MAX_FIXED = 10;

    private static ForgeConfigSpec.IntValue guiScale;
    private static ForgeConfigSpec.BooleanValue introAnimation;
    private static ForgeConfigSpec.BooleanValue commTypewriter;
    private static ForgeConfigSpec.BooleanValue commSound;
    private static ForgeConfigSpec.BooleanValue guideSounds;
    private static volatile int cachedGuiScale = AUTO;
    private static volatile boolean cachedIntroAnimation = true;
    private static volatile boolean cachedCommTypewriter = true;
    private static volatile boolean cachedCommSound = true;
    private static volatile boolean cachedGuideSounds = true;

    private GuideDisplayConfig() {
    }

    /**
     * 往 CLIENT 配置的 builder 里加 {@code [guide]} 小节（{@link TooltipConfig} 的静态块调）
     */
    static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("═══════════════ 冒险指南 / Adventure Guide ═══════════════").push("guide");
        guiScale = builder.comment(
                        "Adventure guide size. 0 = auto, -1 = always follow the game's GUI scale, 1-10 = fixed (never larger than the game's GUI scale).",
                        "冒险指南的界面大小。0 = 自动；-1 = 总跟游戏的界面缩放一样；1~10 = 指定倍数（不会比游戏的界面缩放更大）。"
                                + "指南顶栏的「Aa」按钮、Ctrl + 滚轮也能调，会写回这里")
                .defineInRange("gui_scale", AUTO, FOLLOW_GAME, MAX_FIXED);
        introAnimation = builder.comment(
                        "Play the opening and closing animation of the adventure guide. Off: it opens and closes at once.",
                        "冒险指南开不开场、收场动画。关掉以后指南直接打开、直接合上。指南顶栏「Aa」左边的按钮也能切换，会写回这里")
                .define("intro_animation", true);
        commTypewriter = builder.comment(
                        "Play the opening frame and the line wipe-in the first time a transmission appears (off: shown at once, and silent)",
                        "通讯第一次出现时播开框和字幕擦出（关掉直接显示，也不响）")
                .define("comm_typewriter", true);
        commSound = builder.comment(
                        "Sound when a transmission connects (guide and in-game)",
                        "通讯接通声（指南和游戏内都管）")
                .define("comm_sound", true);
        guideSounds = builder.comment(
                        "Opening, closing and page-turn sounds of the guide",
                        "指南开合与翻页音效")
                .define("guide_sounds", true);
        builder.pop();
    }

    /** 开场、收场动画开着没有 */
    public static boolean introAnimation() {
        return cachedIntroAnimation;
    }

    /** 切换开场动画并写回文件（同 {@link #set}） */
    public static void setIntroAnimation(boolean value) {
        cachedIntroAnimation = value;
        if (introAnimation == null || !TooltipConfig.CLIENT_CONFIG.isLoaded()) {
            return;
        }
        introAnimation.set(value);
        introAnimation.save();
    }

    /** 通讯第一次出现时播开框和字幕擦出 */
    public static boolean commTypewriter() {
        return cachedCommTypewriter;
    }

    /** 通讯接通声（指南和游戏内都管） */
    public static boolean commSound() {
        return cachedCommSound;
    }

    /** 指南开合与翻页音效 */
    public static boolean guideSounds() {
        return cachedGuideSounds;
    }

    /** 配置值（0 自动 / -1 跟游戏 / 1~10 指定） */
    public static int guiScale() {
        return cachedGuiScale;
    }

    /**
     * 改配置并写回文件（指南里调大小时）。缓存马上更新，不等文件监视器回读；配置还没加载（不该发生）时只改缓存
     */
    public static void set(int value) {
        int v = Math.max(FOLLOW_GAME, Math.min(MAX_FIXED, value));
        cachedGuiScale = v;
        if (guiScale == null || !TooltipConfig.CLIENT_CONFIG.isLoaded()) {
            return;
        }
        guiScale.set(v);
        guiScale.save();
    }

    private static void refresh(ModConfigEvent event) {
        if (guiScale != null && event.getConfig().getSpec() == TooltipConfig.CLIENT_CONFIG) {
            cachedGuiScale = guiScale.get();
            cachedIntroAnimation = introAnimation == null || introAnimation.get();
            cachedCommTypewriter = commTypewriter == null || commTypewriter.get();
            cachedCommSound = commSound == null || commSound.get();
            cachedGuideSounds = guideSounds == null || guideSounds.get();
        }
    }

    @SubscribeEvent
    public static void onGuideDisplayConfigLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onGuideDisplayConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }
}
