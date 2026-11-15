package pers.roinflam.kuvalich.mercy;

/**
 * 怜悯之击用到的动画片段名
 *
 * <p>实体里选片段的代码和动画资源文件都照这里的名字，改一处两边对得上。片段都放在
 * {@code kuva_mercy.animation.json}，名字一律以 {@code mercy.} 开头，模型据此把它们从共用动画文件里区分出来。
 * 玄骸套路片段名里的 a ~ e 对应 {@link MercyVariant#animKey}。</p>
 */
public final class MercyAnimNames {

    /** 所有处决片段的名字前缀 */
    public static final String PREFIX = "mercy.";

    // ==================== 玄骸 ====================

    /** 倒地进入，播完接 {@link #LICH_DOWNED} */
    public static final String LICH_DOWN_ENTER = "mercy.lich.down_enter";
    /** 倒地循环 */
    public static final String LICH_DOWNED = "mercy.lich.downed";
    /** 被抓住，末帧保持 */
    public static final String LICH_GRAB = "mercy.lich.grab";
    /** C 套路的被抓：从倒地姿势被顶到贴身上挑的起点 */
    public static final String LICH_C_GRAB = "mercy.lich.c.grab";
    /** 判错之后挣脱 */
    public static final String LICH_BREAKFREE = "mercy.lich.breakfree";
    /** 倒地到时没人处决：从倒地姿势站起来接着打（不化雾），末帧是日常站姿 */
    public static final String LICH_RISE = "mercy.lich.rise";
    /** 成功后跪地循环 */
    public static final String LICH_KNEEL = "mercy.lich.kneel";
    /** 成功后瘫倒，末帧保持 */
    public static final String LICH_SLUMP = "mercy.lich.slump";
    /** 选了感化：拔刀时一挺，伏低喘，抬头、撑直上身、右拳按胸目送，垂首，化雾时还在播 */
    public static final String LICH_CONVERT = "mercy.lich.convert";

    // ==================== 奴仆 ====================

    public static final String THRALL_DOWN_ENTER = "mercy.thrall.down_enter";
    public static final String THRALL_DOWNED = "mercy.thrall.downed";
    /** 抓肩直刺 */
    public static final String THRALL_T1 = "mercy.thrall.t1";
    /** 锁喉背刺 */
    public static final String THRALL_T2 = "mercy.thrall.t2";
    /** 压颈下刺 */
    public static final String THRALL_T3 = "mercy.thrall.t3";

    private MercyAnimNames() {
    }

    /**
     * 玄骸某个套路第 {@code stab} 刀的片段名，例如 {@code mercy.lich.a.hit1}
     *
     * @param variant 玄骸的套路（A ~ E）
     * @param stab    第几刀，1~3
     */
    public static String lichHit(MercyVariant variant, int stab) {
        return "mercy.lich." + variant.animKey + ".hit" + stab;
    }

    /**
     * 这次验的几刀全对、还没到第三刀：从第 {@code stab} 刀停下的姿势挣开站起来，例如 {@code mercy.lich.a.rise1}
     *
     * @param variant 玄骸的套路（A ~ E）
     * @param stab    这次捅了几刀，1~2
     */
    public static String lichRise(MercyVariant variant, int stab) {
        return "mercy.lich." + variant.animKey + ".rise" + stab;
    }

    /** 奴仆某个套路的处决片段名 */
    public static String thrallExecute(MercyVariant variant) {
        return "mercy.thrall." + variant.animKey;
    }
}
