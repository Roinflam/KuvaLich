package pers.roinflam.kuvalich.compat.tacz;

import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.LichReliquary;


/**
 * TACZ枪械永久强化工具类
 * 处理枪械基础伤害永久增幅的NBT读写、伤害乘数计算、合法性校验
 *
 * <p>强化数据直接存储在TACZ枪械的NBT中（跟着枪走），不依赖玩家Capability。</p>
 *
 * <p>伤害计算方式（线性叠加，非复利）：
 * <br>最终伤害 = 原始伤害 × (1 + 强化次数 × 每次增幅百分比)</p>
 *
 * TACZ Gun Permanent Enhancement Utility
 */
public final class TaczGunEnhanceUtil {

    // ==================== NBT常量 ====================

    /** NBT标签键：枪械强化次数 */
    public static final String TAG_ENHANCE_COUNT = "kuvalich_gun_enhance_count";

    /** TACZ 是否已加载；null 表示还没查过 */
    private static volatile Boolean taczLoaded;

    /** 私有构造，纯工具类禁止实例化 */
    private TaczGunEnhanceUtil() {
    }

    // ==================== NBT读写 ====================

    /**
     * 获取枪械已强化次数
     *
     * @param gunStack 枪械物品堆
     * @return 强化次数，无标签时返回0
     */
    public static int getEnhanceCount(ItemStack gunStack) {
        if (gunStack.isEmpty() || !gunStack.hasTag()) {
            return 0;
        }
        return gunStack.getTag().getInt(TAG_ENHANCE_COUNT);
    }

    /**
     * 设置枪械强化次数
     *
     * @param gunStack 枪械物品堆
     * @param count    强化次数
     */
    public static void setEnhanceCount(ItemStack gunStack, int count) {
        gunStack.getOrCreateTag().putInt(TAG_ENHANCE_COUNT, Math.max(0, count));
    }

    // ==================== 伤害计算 ====================

    /**
     * 获取伤害乘数
     * <p>乘数 = 1.0 + 强化次数 × 每次增幅百分比</p>
     * <p>例：3次强化，每次25% → 1.0 + 3×0.25 = 1.75</p>
     *
     * @param gunStack 枪械物品堆
     * @return 伤害乘数，无强化时返回1.0
     */
    public static double getDamageMultiplier(ItemStack gunStack) {
        int count = getEnhanceCount(gunStack);
        if (count <= 0) {
            return 1.0;
        }
        double percentPerEnhance = ModConfig.KUVA_LICH.taczGunEnhancePercent.get();
        return 1.0 + count * percentPerEnhance;
    }

    // ==================== 校验逻辑 ====================

    /**
     * 检查物品是否为TACZ枪械（不含配置和上限检查，仅类型判断）
     * <p>用于菜单槽位的 mayPlace 判断</p>
     *
     * @param stack 待检查物品堆
     * @return 是否为TACZ枪械
     */
    public static boolean isTaczGun(ItemStack stack) {
        if (stack.isEmpty() || !isTaczLoaded()) {
            return false;
        }
        try {
            return TaczTypeProbe.isGun(stack);
        } catch (LinkageError | Exception e) {
            // 装了个签名对不上的 TACZ 版本时兜底。注意必须连 LinkageError 一起接 ——
            // 改造前这里只 catch (Exception)，而类解析失败抛的是 NoClassDefFoundError，
            // 属于 Error 不是 Exception，那个 catch 根本接不住。
            return false;
        }
    }

    /**
     * TACZ 装了没有（结果缓存，运行期不会变）
     */
    public static boolean isTaczLoaded() {
        Boolean cached = taczLoaded;
        if (cached == null) {
            cached = net.minecraftforge.fml.ModList.get().isLoaded("tacz");
            taczLoaded = cached;
        }
        return cached;
    }

    /**
     * ⭐ 全类<b>唯一</b>触碰 TACZ 类型的地方，单独成一个内部类。
     *
     * <p>为什么非要拆出去：本工具类是<b>无条件</b>被加载的 —— tooltip 对每个物品都调
     * {@code appendEnhanceLine}，面板的 {@code PanelRowGate.RANGED} 和安魂之融的槽位判定也在调。
     * 只要 {@code IGun} 出现在本类的常量池里，没装 TACZ 的玩家就有在类解析阶段
     * 吃 {@code NoClassDefFoundError} 的风险。
     *
     * <p>放进内部类之后，{@code IGun} 只存在于 {@code TaczTypeProbe.class} 的常量池，
     * 而这个类<b>只可能</b>在 {@link #isTaczLoaded()} 返回 true 之后才被触碰，
     * JVM 的惰性加载保证它在没装 TACZ 时根本不会被加载。</p>
     */
    private static final class TaczTypeProbe {

        static boolean isGun(ItemStack stack) {
            return com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null;
        }

        private TaczTypeProbe() {
        }
    }

    /**
     * 检查物品是否为可强化的TACZ枪械
     * <p>条件：
     * <br>1. 功能已在配置中启用
     * <br>2. 物品是TACZ枪械（IGun接口）
     * <br>3. 未达到强化上限</p>
     *
     * @param stack 待检查物品堆
     * @return 是否可强化
     */
    public static boolean isEnhanceableTaczGun(ItemStack stack) {
        // 功能开关
        if (!ModConfig.KUVA_LICH.taczGunEnhanceEnable.get()) {
            return false;
        }
        // 必须是TACZ枪械
        if (!isTaczGun(stack)) {
            return false;
        }
        // 检查上限（-1 = 无限）
        int maxCount = ModConfig.KUVA_LICH.taczGunEnhanceMaxCount.get();
        if (maxCount > 0 && getEnhanceCount(stack) >= maxCount) {
            return false;
        }
        return true;
    }

    /**
     * 检查物品是否为玄骸之遗
     *
     * @param stack 待检查物品堆
     * @return 是否为玄骸之遗
     */
    public static boolean isLichReliquary(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LichReliquary;
    }

    /**
     * 对枪械执行一次强化
     * <p>强化次数+1，消耗1个玄骸之遗</p>
     *
     * @param gunStack       枪械物品堆
     * @param reliquaryStack 玄骸之遗物品堆
     * @return 强化是否成功
     */
    public static boolean performEnhance(ItemStack gunStack, ItemStack reliquaryStack) {
        if (!isEnhanceableTaczGun(gunStack) || !isLichReliquary(reliquaryStack)) {
            return false;
        }
        // 强化次数+1
        int newCount = getEnhanceCount(gunStack) + 1;
        setEnhanceCount(gunStack, newCount);
        // 消耗1个玄骸之遗
        reliquaryStack.shrink(1);
        return true;
    }

    /**
     * 获取当前强化百分比（用于Tooltip显示）
     * <p>例：3次强化，每次25% → 返回 0.75 (即75%)</p>
     *
     * @param gunStack 枪械物品堆
     * @return 总增幅百分比，无强化时返回0.0
     */
    public static double getTotalEnhancePercent(ItemStack gunStack) {
        int count = getEnhanceCount(gunStack);
        if (count <= 0) {
            return 0.0;
        }
        return count * ModConfig.KUVA_LICH.taczGunEnhancePercent.get();
    }

    // ==================== Tooltip ====================

    /**
     * 追加「玄骸之力」行
     *
     * <p>显示格式：§4✦ 玄骸之力 §c×3 §4(§c伤害+75%§4)</p>
     *
     * <p>⭐ 改造前这是 {@code TaczCompatEventHandler} 上的一个 {@code @SubscribeEvent}，
     * 往 {@code tooltip.add(1, ...)} 这个硬编码下标插行，与
     * {@code WeaponModuleHandler} / {@code KuvaWeaponUtil} 抢同一个位置，
     * 三者的相对顺序取决于 Forge 的注解扫描顺序。
     * 现在改为被 {@code client.tooltip.KuvaTooltipCoordinator} 按固定顺序调用。</p>
     *
     * <p>⭐ 放在本类而不是 {@code TaczCompatEventHandler}：后者直接 import 了
     * {@code com.tacz.guns.api.event.*} 等一整套 TACZ 类型，而 tooltip 是每帧走的路径，
     * 不该只为了画一行字就把那个类拖进来（本类只依赖 {@code IGun}，
     * 且已经被 {@code RequiemEvolveMenu} 等无条件调用）。</p>
     *
     * @param lines 待写入的行列表
     * @param stack 被查看的物品
     */
    public static void appendEnhanceLine(java.util.List<net.minecraft.network.chat.Component> lines, ItemStack stack) {
        if (!ModConfig.KUVA_LICH.taczGunEnhanceEnable.get()) {
            return;
        }
        if (!isTaczGun(stack)) {
            return;
        }
        int enhanceCount = getEnhanceCount(stack);
        if (enhanceCount <= 0) {
            return;
        }
        double totalPercent = getTotalEnhancePercent(stack);
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.kuvalich.gun_enhance.info",
                enhanceCount, String.format(java.util.Locale.ROOT, "+%.0f%%", totalPercent * 100)));
    }
}
