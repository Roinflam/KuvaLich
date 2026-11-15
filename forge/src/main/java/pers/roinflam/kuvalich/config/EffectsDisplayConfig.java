package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import pers.roinflam.kuvalich.mercy.MercyCameraPath;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 特效与音效的观感项：{@code kuvalich-tooltip.toml} 的 {@code [effects]} 小节（CLIENT 配置，每台电脑自己定，服务器不管、不同步）
 *
 * <p>结构和 {@link GuideDisplayConfig} 一样：由 {@link TooltipConfig} 的静态块调 {@link #define} 加进同一个 builder，
 * 不另开第二个 CLIENT 文件；读值在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段，
 * 配置还没加载时直接 {@code get()} 会抛异常，缓存的默认值保证任何时候都能读。
 * 改文件后 Forge 的文件监视触发 Reloading，不用重启。</p>
 *
 * <p>只有 ForgeConfigSpec，不引用客户端类：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 服务端演出的强度只在客户端裁：服务端照发效果包，客户端按这些选项丢层。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EffectsDisplayConfig {

    /** 特效强度 */
    public enum Intensity {
        /** 完整 */
        FULL(1.0F),
        /** 减少：粒子预算约 0.55 倍，不画横向光晕 */
        REDUCED(0.55F),
        /** 最少：粒子预算约 0.2 倍，只留核心层 */
        MINIMAL(0.2F);

        /** 粒子预算倍数 */
        public final float budgetScale;

        Intensity(float budgetScale) {
            this.budgetScale = budgetScale;
        }
    }

    /** 幻纹画给谁看 */
    public enum EphemeraMode {
        /** 全部照常 */
        ALL,
        /** 所有人都按精简档画 */
        REDUCED,
        /** 只画自己的 */
        SELF,
        OFF
    }

    private static ForgeConfigSpec.EnumValue<Intensity> intensity;
    private static ForgeConfigSpec.BooleanValue additiveGlow;
    private static ForgeConfigSpec.BooleanValue screenEffects;
    private static ForgeConfigSpec.BooleanValue screenFlash;
    private static ForgeConfigSpec.DoubleValue uiVolume;
    private static ForgeConfigSpec.BooleanValue lichAlert;
    private static ForgeConfigSpec.BooleanValue damageNumberPop;
    private static ForgeConfigSpec.BooleanValue commHud;
    private static ForgeConfigSpec.BooleanValue commChatCopy;
    private static ForgeConfigSpec.DoubleValue commGhost;
    private static ForgeConfigSpec.BooleanValue commStatic;
    private static ForgeConfigSpec.BooleanValue combatSoundPlus;
    private static ForgeConfigSpec.BooleanValue thrallAlert;
    private static ForgeConfigSpec.BooleanValue thrallOutline;
    private static ForgeConfigSpec.BooleanValue thrallMarker;
    private static ForgeConfigSpec.BooleanValue lichEdgePulse;
    private static ForgeConfigSpec.BooleanValue lichMutter;
    private static ForgeConfigSpec.BooleanValue lichNameplates;
    private static ForgeConfigSpec.BooleanValue archiveIntro;
    private static ForgeConfigSpec.BooleanValue mercyCamera;
    private static ForgeConfigSpec.EnumValue<MercyCameraPath.Motion> mercyCameraMotion;
    private static ForgeConfigSpec.BooleanValue thrallThirdPerson;
    private static ForgeConfigSpec.BooleanValue thrallPushIn;
    private static ForgeConfigSpec.BooleanValue mercyShortOutro;
    private static ForgeConfigSpec.BooleanValue mercyHitstop;
    private static ForgeConfigSpec.DoubleValue mercyShake;
    private static ForgeConfigSpec.BooleanValue mercyRankupSound;
    private static ForgeConfigSpec.BooleanValue kuvaStateFx;
    private static ForgeConfigSpec.BooleanValue hudNotices;
    private static ForgeConfigSpec.EnumValue<EphemeraMode> ephemera;
    private static ForgeConfigSpec.IntValue ephemeraMaxVisible;
    private static ForgeConfigSpec.IntValue ephemeraDistance;

    private static volatile Intensity cachedIntensity = Intensity.FULL;
    private static volatile boolean cachedAdditiveGlow = true;
    private static volatile boolean cachedScreenEffects = true;
    private static volatile boolean cachedScreenFlash = true;
    private static volatile float cachedUiVolume = 1.0F;
    private static volatile boolean cachedLichAlert = true;
    private static volatile boolean cachedDamageNumberPop = true;
    private static volatile boolean cachedCommHud = true;
    private static volatile boolean cachedCommChatCopy = false;
    private static volatile float cachedCommGhost = 0.5F;
    private static volatile boolean cachedCommStatic = true;
    private static volatile boolean cachedCombatSoundPlus = false;
    private static volatile boolean cachedThrallAlert = false;
    private static volatile boolean cachedThrallOutline = true;
    private static volatile boolean cachedThrallMarker = false;
    private static volatile boolean cachedLichEdgePulse = false;
    private static volatile boolean cachedLichMutter = true;
    private static volatile boolean cachedLichNameplates = true;
    private static volatile boolean cachedArchiveIntro = true;
    private static volatile boolean cachedMercyCamera = true;
    private static volatile MercyCameraPath.Motion cachedMercyCameraMotion = MercyCameraPath.Motion.FULL;
    private static volatile boolean cachedThrallThirdPerson = false;
    private static volatile boolean cachedThrallPushIn = false;
    private static volatile boolean cachedMercyShortOutro = false;
    private static volatile boolean cachedMercyHitstop = true;
    private static volatile float cachedMercyShake = 1.0F;
    private static volatile boolean cachedMercyRankupSound = false;
    private static volatile boolean cachedKuvaStateFx = true;
    private static volatile boolean cachedHudNotices = true;
    private static volatile EphemeraMode cachedEphemera = EphemeraMode.ALL;
    private static volatile int cachedEphemeraMaxVisible = 12;
    private static volatile int cachedEphemeraDistance = 32;

    private EffectsDisplayConfig() {
    }

    /**
     * 往 CLIENT 配置的 builder 里加 {@code [effects]} 小节（{@link TooltipConfig} 的静态块调）
     */
    static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Effects and sounds (only affect what you see and hear)",
                "特效与音效（只影响自己的画面和声音）").push("effects");
        intensity = builder.comment(
                        "Particle and effect intensity: FULL / REDUCED / MINIMAL. MINIMAL also drops the Lich's battle fury markings and glow boost",
                        "特效强度：FULL 完整 / REDUCED 减少 / MINIMAL 最少。最少档玄骸身上没有战斗增幅的纹路，发光也不加亮")
                .defineEnum("intensity", Intensity.FULL);
        additiveGlow = builder.comment(
                        "Additive blending for glowing effects. Turn off if effects look washed out or too bright with a shader pack",
                        "发光特效用加法混合。装了光影后特效发灰或过曝可以关掉")
                .define("additive_glow", true);
        screenEffects = builder.comment(
                        "Screen-edge vignettes and corner brackets",
                        "屏幕边缘暗角与四角括号")
                .define("screen_effects", true);
        screenFlash = builder.comment(
                        "Full-screen flashes; when off, they become a vignette of the same color (turn off if you are sensitive to flashing). The critical-hit edge pulse has been removed",
                        "全屏闪白，关掉后改成同色暗角（对闪光敏感可以关）；暴击时的屏幕边缘脉冲已经删掉")
                .define("screen_flash", true);
        uiVolume = builder.comment(
                        "Volume of this mod's interface sounds (guide, transmissions, decryption, workbenches)",
                        "本模组界面音量（指南、通讯、解密提示、工作台）")
                .defineInRange("ui_volume", 1.0D, 0.0D, 1.0D);
        lichAlert = builder.comment(
                        "Master switch for the alert sting when a Thrall appears (thrall_alert must also be on). A Kuva Lich arriving no longer plays one",
                        "奴仆出现时的提示音总开关（还要打开 thrall_alert 才响）。玄骸到场不再放提示音")
                .define("lich_alert", true);
        damageNumberPop = builder.comment(
                        "Pop-in animation for critical damage numbers (all three tiers)",
                        "暴击数字弹出（三档）")
                .define("damage_number_pop", true);
        commHud = builder.comment(
                        "In-game transmission window. When off, lines go to the chat bar instead",
                        "游戏内通讯窗（关掉后台词进聊天栏）")
                .define("comm_hud", true);
        commChatCopy = builder.comment(
                        "Also copy transmission lines to the chat bar",
                        "通讯台词同时进聊天栏")
                .define("comm_chat_copy", false);
        commGhost = builder.comment(
                        "Strength of the color-fringe ghosting on Kuva Lich transmissions (0 = off)",
                        "玄骸通讯窗的错色重影强度（0 关掉）")
                .defineInRange("comm_ghost", 0.5D, 0.0D, 1.0D);
        commStatic = builder.comment(
                        "Static noise under the Kuva Lich while it speaks",
                        "玄骸说话时的静电底噪")
                .define("comm_static", true);
        combatSoundPlus = builder.comment(
                        "Extra combat sounds (tier 1 and 2 critical hits, electric stun)",
                        "战斗声增强（一档、二档暴击声，电击麻痹声）")
                .define("combat_sound_plus", false);
        thrallAlert = builder.comment(
                        "Alert sound when a Thrall appears",
                        "奴仆出现时的提示音")
                .define("thrall_alert", false);
        thrallOutline = builder.comment(
                        "Outline around Thralls",
                        "奴仆描边")
                .define("thrall_outline", true);
        thrallMarker = builder.comment(
                        "Marker above Thralls' heads",
                        "奴仆头顶路标")
                .define("thrall_marker", false);
        lichEdgePulse = builder.comment(
                        "Faint Kuva glow at the screen edges as a Kuva Lich closes in (silent)",
                        "玄骸接近时的屏幕边缘提示（不出声）")
                .define("lich_edge_pulse", false);
        lichMutter = builder.comment(
                        "The muttering voice when your Lich speaks",
                        "玄骸说话时的「赤毒嘟囔」声")
                .define("lich_mutter", true);
        lichNameplates = builder.comment(
                        "Name plates above Liches and Thralls",
                        "玄骸和奴仆头顶的名牌")
                .define("lich_nameplates", true);
        archiveIntro = builder.comment(
                        "Fade-in when opening the Lich Profile",
                        "打开玄骸档案时的渐显动画")
                .define("archive_intro", true);
        mercyCamera = builder.comment(
                        "Cinematic camera when you execute a Kuva Lich. When off, the execution plays in third person from your own view",
                        "处决玄骸时用电影镜头；关掉后在自己的第三人称背后视角里演完（界面、声音照常）")
                .define("mercy_camera", true);
        mercyCameraMotion = builder.comment(
                        "Camera movement of the cinematic camera when executing a Kuva Lich. FULL: the camera pushes in, orbits and follows the executor; REDUCED: push-in and pull-back only, with half-strength shake; STATIC: fixed shots with hard cuts",
                        "处决玄骸时电影镜头的运动方式：FULL 推拉、环绕并跟随处决者；REDUCED 仅保留推拉，震动减半；STATIC 固定机位，直接切换")
                .defineEnum("mercy_camera_motion", MercyCameraPath.Motion.FULL);
        thrallThirdPerson = builder.comment(
                        "Switch to third person while executing a Thrall",
                        "处决奴仆时切到第三人称背后视角")
                .define("thrall_third_person", false);
        thrallPushIn = builder.comment(
                        "Slight zoom on the stab when executing a Thrall",
                        "处决奴仆时刺入那一下轻微推近镜头")
                .define("thrall_push_in", false);
        mercyShortOutro = builder.comment(
                        "After a successful Mercy, skip the last third-person shot and take control back right after the close-up",
                        "怜悯之击成功后跳过最后的第三人称镜头，特写完就交还操作")
                .define("mercy_short_outro", false);
        mercyHitstop = builder.comment(
                        "Brief hit-stop on each stab during a Mercy: the motion pauses for a moment, then catches up",
                        "怜悯之击每一刀刺入时动作短暂停顿，随后追回")
                .define("mercy_hitstop", true);
        mercyShake = builder.comment(
                        "Strength of camera shake and zoom impact on each stab during a Mercy. 0 turns them off, 2 doubles the shake",
                        "怜悯之击每一刀刺入时镜头震动和推近冲击的强度；0 为关闭，2 为震动加倍")
                .defineInRange("mercy_shake", 1.0D, 0.0D, 2.0D);
        mercyRankupSound = builder.comment(
                        "Play the rank-up sound when the Lich rank-up banner appears",
                        "玄骸升级横幅出现时放升级音")
                .define("mercy_rankup_sound", true);
        kuvaStateFx = builder.comment(
                        "Show Lich and Thrall status effects: adaptation shield, blocked hits, weak points, battle fury, life steal. Also controls their sounds: turned off, the adaptation shield is silent too",
                        "显示玄骸和奴仆身上的状态：适应护壁、挡住、弱点、战斗增幅、吸血。也管这些状态的声音：关掉后适应护壁也不出声")
                .define("kuva_state_fx", true);
        hudNotices = builder.comment(
                        "Show this mod's messages (Ultimatum, Mercy, Death Resistance, confiscation, rewards...) as notice bars above the hotbar. When off, they go to the action bar and chat as before",
                        "本模组的提示（安魂通牒、处决、死亡抵抗、没收、奖励等）显示成物品栏上方的提示条；关掉后照旧进动作栏和聊天栏")
                .define("hud_notices", true);
        ephemera = builder.comment(
                        "Ephemera on players, Liches and allies: ALL / REDUCED (everyone in the light version) / SELF (only your own) / OFF",
                        "玩家、玄骸、盟友身上的幻纹：ALL 全部 / REDUCED 一律精简 / SELF 只显示自己的 / OFF 关闭")
                .defineEnum("ephemera", EphemeraMode.ALL);
        ephemeraMaxVisible = builder.comment(
                        "At most this many Ephemera are drawn at once, nearest first; your own always comes first",
                        "同时最多显示几个幻纹，按距离由近到远，自己的优先")
                .defineInRange("ephemera_max_visible", 12, 1, 64);
        ephemeraDistance = builder.comment(
                        "Ephemera farther than this many blocks are not drawn",
                        "超过这个距离（格）的幻纹不显示")
                .defineInRange("ephemera_distance", 32, 16, 64);
        builder.pop();
    }

    /** 特效强度 */
    public static Intensity intensity() {
        return cachedIntensity;
    }

    /** 发光特效用加法混合；关掉后粒子和几何改走普通透明混合 */
    public static boolean additiveGlow() {
        return cachedAdditiveGlow;
    }

    /** 屏幕暗角与四角括号 */
    public static boolean screenEffects() {
        return cachedScreenEffects;
    }

    /** 全屏闪白 */
    public static boolean screenFlash() {
        return cachedScreenFlash;
    }

    /** 界面音量（0~1） */
    public static float uiVolume() {
        return cachedUiVolume;
    }

    /** 奴仆出现时提示音的总开关（只管声音，画面照放） */
    public static boolean lichAlert() {
        return cachedLichAlert;
    }

    /** 暴击数字弹出动画（三档） */
    public static boolean damageNumberPop() {
        return cachedDamageNumberPop;
    }

    /** 游戏内通讯窗开着没有 */
    public static boolean commHud() {
        return cachedCommHud;
    }

    /** 通讯台词是否同时进聊天栏 */
    public static boolean commChatCopy() {
        return cachedCommChatCopy;
    }

    /** 玄骸通讯窗的错色重影强度（0~1） */
    public static float commGhost() {
        return cachedCommGhost;
    }

    /** 玄骸说话时垫静电底噪 */
    public static boolean commStatic() {
        return cachedCommStatic;
    }

    /** 战斗声增强 */
    public static boolean combatSoundPlus() {
        return cachedCombatSoundPlus;
    }

    /** 奴仆出现时的提示音（还要 lich_alert 开着才响） */
    public static boolean thrallAlert() {
        return cachedThrallAlert;
    }

    /** 奴仆描边 */
    public static boolean thrallOutline() {
        return cachedThrallOutline;
    }

    /** 奴仆头顶路标 */
    public static boolean thrallMarker() {
        return cachedThrallMarker;
    }

    /** 玄骸接近时的屏幕边缘提示 */
    public static boolean lichEdgePulse() {
        return cachedLichEdgePulse;
    }

    /** 玄骸说话时的嘟囔声（关掉只是不出声，点头和亮暗照常） */
    public static boolean lichMutter() {
        return cachedLichMutter;
    }

    /** 玄骸和奴仆头顶的名牌 */
    public static boolean lichNameplates() {
        return cachedLichNameplates;
    }

    /** 打开玄骸档案时的渐显动画 */
    public static boolean archiveIntro() {
        return cachedArchiveIntro;
    }

    /** 处决玄骸时用电影镜头 */
    public static boolean mercyCamera() {
        return cachedMercyCamera;
    }

    /** 处决电影镜头的运动方式 */
    public static MercyCameraPath.Motion mercyCameraMotion() {
        return cachedMercyCameraMotion;
    }

    /** 处决奴仆时切第三人称 */
    public static boolean thrallThirdPerson() {
        return cachedThrallThirdPerson;
    }

    /** 处决奴仆时刺入那一下轻微推近 */
    public static boolean thrallPushIn() {
        return cachedThrallPushIn;
    }

    /** 怜悯之击成功后跳过最后一个镜头 */
    public static boolean mercyShortOutro() {
        return cachedMercyShortOutro;
    }

    /** 怜悯之击每刀刺入的顿帧 */
    public static boolean mercyHitstop() {
        return cachedMercyHitstop;
    }

    /** 怜悯之击刺入时镜头震动和推近冲击的强度（0~2） */
    public static float mercyShake() {
        return cachedMercyShake;
    }

    /** 升级横幅出现时放一声低音 */
    public static boolean mercyRankupSound() {
        return cachedMercyRankupSound;
    }

    /** 显示玄骸和奴仆身上的状态特效 */
    public static boolean kuvaStateFx() {
        return cachedKuvaStateFx;
    }

    /** 本模组的提示画成提示条；关掉时交回原版动作栏 / 聊天栏 */
    public static boolean hudNotices() {
        return cachedHudNotices;
    }

    public static EphemeraMode ephemera() {
        return cachedEphemera;
    }

    public static int ephemeraMaxVisible() {
        return cachedEphemeraMaxVisible;
    }

    public static int ephemeraDistance() {
        return cachedEphemeraDistance;
    }

    private static void refresh(ModConfigEvent event) {
        if (intensity != null && event.getConfig().getSpec() == TooltipConfig.CLIENT_CONFIG) {
            cachedIntensity = intensity.get();
            cachedAdditiveGlow = additiveGlow.get();
            cachedScreenEffects = screenEffects.get();
            cachedScreenFlash = screenFlash.get();
            cachedUiVolume = uiVolume.get().floatValue();
            cachedLichAlert = lichAlert.get();
            cachedDamageNumberPop = damageNumberPop.get();
            cachedCommHud = commHud.get();
            cachedCommChatCopy = commChatCopy.get();
            cachedCommGhost = commGhost.get().floatValue();
            cachedCommStatic = commStatic.get();
            cachedCombatSoundPlus = combatSoundPlus.get();
            cachedThrallAlert = thrallAlert.get();
            cachedThrallOutline = thrallOutline.get();
            cachedThrallMarker = thrallMarker.get();
            cachedLichEdgePulse = lichEdgePulse.get();
            cachedLichMutter = lichMutter.get();
            cachedLichNameplates = lichNameplates.get();
            cachedArchiveIntro = archiveIntro.get();
            cachedMercyCamera = mercyCamera.get();
            cachedMercyCameraMotion = mercyCameraMotion.get();
            cachedThrallThirdPerson = thrallThirdPerson.get();
            cachedThrallPushIn = thrallPushIn.get();
            cachedMercyShortOutro = mercyShortOutro.get();
            cachedMercyHitstop = mercyHitstop.get();
            cachedMercyShake = mercyShake.get().floatValue();
            cachedMercyRankupSound = mercyRankupSound.get();
            cachedKuvaStateFx = kuvaStateFx.get();
            cachedHudNotices = hudNotices.get();
            cachedEphemera = ephemera.get();
            cachedEphemeraMaxVisible = ephemeraMaxVisible.get();
            cachedEphemeraDistance = ephemeraDistance.get();
        }
    }

    @SubscribeEvent
    public static void onEffectsDisplayConfigLoading(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onEffectsDisplayConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event);
    }
}
