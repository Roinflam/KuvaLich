// WarframeTaczBridge.java
package pers.roinflam.kuvalich.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.module.KillStackManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Warframe 武器模组系统与 TACZ 枪械的属性桥接工具类。
 * Bridge between Warframe weapon module system and TACZ gun attributes.
 * <p>
 * 本类不引用任何 TACZ 类，无 TACZ 时也可安全加载（类加载安全）。
 * This class has ZERO TACZ imports; safe to load even without TACZ (classload-safe).
 */
public class WarframeTaczBridge {

    // ========== 射击期间 ThreadLocal / Shoot-time ThreadLocal ==========

    /** bursting_radius 抑制标志 */
    private static final ThreadLocal<Boolean> suppressBurstRadius = ThreadLocal.withInitial(() -> false);

    /** TACZ 原始弹丸数（多重射击膨胀前的值） */
    private static final ThreadLocal<Integer> originalBulletAmount = ThreadLocal.withInitial(() -> 0);

    /** 满弹夹第一发射击时的额外伤害倍率 */
    private static final ThreadLocal<Float> firstBulletDamageBonus = ThreadLocal.withInitial(() -> 0f);

    /** 枪械伤害加成倍率（独立乘区） */
    private static final ThreadLocal<Float> gunDamageBonus = ThreadLocal.withInitial(() -> 0f);

    /**
     * 玄骸强化伤害乘数（独立乘区）。
     * <p>
     * 语义：最终伤害直接乘以此值。1.0 = 无强化，1.75 = 伤害×1.75。
     * 不依赖开光（hasBase），仅检查 NBT 中的 kuvalich_gun_enhance_count 标记。
     * <p>
     * 由 MixinFirstBulletDetect（HEAD）写入，
     * 由 MixinBulletDamageSpread / MixinTaczBulletExplosion 读取，
     * 由 MixinFirstBulletDetect（RETURN）清除。
     */
    private static final ThreadLocal<Double> gunEnhanceMultiplier = ThreadLocal.withInitial(() -> 1.0);

    // ========== 缓存刷新期间 ThreadLocal / Cache Refresh ThreadLocal ==========

    /**
     * AttachmentPropertyManager.postChangeEvent 的 shooter 上下文。
     * 由 MixinAttachmentPropertyContext（HEAD）写入，
     * 由 TaczCompatEventHandler.onAttachmentPropertyEvent 读取并清除。
     */
    private static final ThreadLocal<LivingEntity> cacheContextShooter = new ThreadLocal<>();

    /**
     * AttachmentPropertyManager.postChangeEvent 的 gunItem 上下文。
     * 生命周期同 cacheContextShooter。
     */
    private static final ThreadLocal<ItemStack> cacheContextGunItem = new ThreadLocal<>();

    // ========== 抑制标志 API ==========

    public static boolean isBurstRadiusSuppressed() {
        return suppressBurstRadius.get();
    }

    public static void setSuppressBurstRadius(boolean suppress) {
        suppressBurstRadius.set(suppress);
    }

    public static void clearBurstRadiusSuppressed() {
        suppressBurstRadius.set(false);
    }

    // ========== 原始弹丸数 API ==========

    public static int getOriginalBulletAmount() {
        return originalBulletAmount.get();
    }

    public static void setOriginalBulletAmount(int amount) {
        originalBulletAmount.set(amount);
    }

    public static void clearOriginalBulletAmount() {
        originalBulletAmount.set(0);
    }

    // ========== 第一发子弹伤害 API ==========

    public static float getFirstBulletDamageBonus() {
        return firstBulletDamageBonus.get();
    }

    public static void setFirstBulletDamageBonus(float bonus) {
        firstBulletDamageBonus.set(bonus);
    }

    public static void clearFirstBulletDamageBonus() {
        firstBulletDamageBonus.set(0f);
    }

    // ========== 枪械伤害 API ==========

    public static float getGunDamageBonus() {
        return gunDamageBonus.get();
    }

    public static void setGunDamageBonus(float bonus) {
        gunDamageBonus.set(bonus);
    }

    public static void clearGunDamageBonus() {
        gunDamageBonus.set(0f);
    }

    // ========== 玄骸强化乘数 API ==========

    /**
     * 获取当前射击的玄骸强化伤害乘数。
     *
     * @return 伤害乘数，1.0 表示无强化
     */
    public static double getGunEnhanceMultiplier() {
        return gunEnhanceMultiplier.get();
    }

    /**
     * 设置玄骸强化伤害乘数。
     *
     * @param multiplier 伤害乘数（如 1.75 = 伤害×1.75）
     */
    public static void setGunEnhanceMultiplier(double multiplier) {
        gunEnhanceMultiplier.set(multiplier);
    }

    /**
     * 清除玄骸强化伤害乘数（重置为 1.0）。
     */
    public static void clearGunEnhanceMultiplier() {
        gunEnhanceMultiplier.set(1.0);
    }

    // ========== 缓存上下文 API ==========

    public static LivingEntity getCacheContextShooter() {
        return cacheContextShooter.get();
    }

    public static void setCacheContextShooter(LivingEntity shooter) {
        cacheContextShooter.set(shooter);
    }

    public static ItemStack getCacheContextGunItem() {
        return cacheContextGunItem.get();
    }

    public static void setCacheContextGunItem(ItemStack gunItem) {
        cacheContextGunItem.set(gunItem);
    }

    public static void clearCacheContext() {
        cacheContextShooter.remove();
        cacheContextGunItem.remove();
    }

    // ========== ⭐ 属性读取 helper / Attribute Read Helper ==========

    /**
     * ⭐ 单条目 memo 的有效期（纳秒），40ms < 一 tick 的 50ms
     *
     * <p>用挂钟窗口而不是 gameTime：本类有一半方法（如 {@link #getMagazineSizeMod(ItemStack)}）
     * 签名里根本没有 shooter，拿不到 {@code Level}，也就取不到 gameTime。
     * 40ms 保证 memo 最多跨不到一 tick，玩家换模组卡后下一 tick 必定重算。
     * 可后续提为配置项。</p>
     */
    private static final long MEMO_VALID_NANOS = 40_000_000L;

    /**
     * ⭐ 单条目属性 memo（不可变快照）
     *
     * <p><b>为什么是「一个 volatile 的不可变持有者」而不是四个 volatile 字段：</b>
     * 四个独立 volatile 字段每个都是独立的读，线程 A 读到新的 stackIdentity、
     * 旧的 result 是完全可能的，等于把错误的属性表判成命中。
     * 换成整体替换的不可变对象后，一次 volatile 读就拿到自洽的快照，
     * 跨线程竞争最坏结果只是 miss（重算一次），永远不会读出错配的值。</p>
     *
     * <p><b>为什么不用 ThreadLocal：</b>本 memo 的意义是把同一帧内连着调用的
     * 12 个 getter 压成一次全量重算，这些调用本来就在同一个线程上（客户端渲染线程或服务端主线程），
     * ThreadLocal 能用，但每次 get 都要走一次 ThreadLocalMap 查找，
     * 而这里最热的路径（HUD 每帧的 {@code getAmmoCountWithAttachment → getMagazineSizeMod}）
     * 恰恰是要省掉这点开销。volatile 读在 x86 上就是普通读，更便宜。</p>
     */
    private static final class AttrMemo {

        /** 枪械 ItemStack 的对象身份 */
        private final int stackIdentity;

        /** 枪械 NBT 的对象引用（换枪 / setTag 会换对象，直接比引用） */
        private final CompoundTag tag;

        /** 射手的对象身份（存身份码而非引用：静态字段持有实体会把整个 Level 一起钉住） */
        private final int shooterIdentity;

        /** 快照时刻 */
        private final long stamp;

        /** 合并后的属性表（构造后只读，不再修改） */
        private final HashMap<String, Double> result;

        private AttrMemo(int stackIdentity, CompoundTag tag, int shooterIdentity,
                         long stamp, HashMap<String, Double> result) {
            this.stackIdentity = stackIdentity;
            this.tag = tag;
            this.shooterIdentity = shooterIdentity;
            this.stamp = stamp;
            this.result = result;
        }
    }

    /** 当前 memo 快照，整体替换 */
    private static volatile AttrMemo attrMemo;

    /**
     * ⭐ 取枪械的<b>完整</b>模组属性：枪本体 + 副手 / 护甲 / 饰品额外槽位
     *
     * <p>此前 12 个属性读取方法都只读枪本体，而枪的 tooltip 面板
     * （{@code ExtraSlotTooltipHelper.mergeExtraSlotIntoAttributes}）已经把额外槽位算进去了。
     * 结果是往护甲 / 副手 / 饰品插装填、弹匣、精准度、爆头卡，
     * <b>面板数字会涨，实际开枪毫无变化</b> —— 装填时间、弹匣容量、后坐力、ADS 时间
     * 这几项玩家能直接体感验证，很容易被当成 bug 报。现在两边取自同一份数据。</p>
     *
     * <p>额外槽位只在枪确实握在主手时合并，与 tooltip 的
     * {@code isMainHandWeapon} 判定保持一致（详见 {@link #isMainHandGun}）。</p>
     *
     * @param gunItem 枪械物品栈
     * @param shooter 射手，可为 null（签名里拿不到时只返回枪本体属性）
     * @return 属性表（<b>调用方只读，不要修改</b>，可能是 memo 中的共享实例）
     */
    @Nonnull
    private static HashMap<String, Double> gunAttrs(@Nonnull ItemStack gunItem, @Nullable LivingEntity shooter) {
        // ⭐ 签名里没有 shooter 时（如 getMagazineSizeMod 的旧重载），
        //    退而用「属性缓存刷新上下文」里的射手；该 ThreadLocal 只在
        //    AttachmentPropertyManager.postChangeEvent 期间有值，出了那段就是 null，不会串场
        LivingEntity resolvedShooter = shooter != null ? shooter : cacheContextShooter.get();

        final int stackIdentity = System.identityHashCode(gunItem);
        final CompoundTag tag = gunItem.getTag();
        final int shooterIdentity = resolvedShooter == null ? 0 : System.identityHashCode(resolvedShooter);
        final long now = System.nanoTime();

        // ⭐ 单次 volatile 读拿到自洽快照
        AttrMemo memo = attrMemo;
        if (memo != null
                && memo.stackIdentity == stackIdentity
                && memo.tag == tag
                && memo.shooterIdentity == shooterIdentity
                && now - memo.stamp < MEMO_VALID_NANOS) {
            return memo.result;
        }

        // getWeaponAttributes 每次返回新 HashMap，可以安全地就地合并
        HashMap<String, Double> result = WeaponModuleHandler.getWeaponAttributes(gunItem);

        if (resolvedShooter != null && isMainHandGun(gunItem, resolvedShooter)) {
            HashMap<String, Double> extra = WeaponCombatHandler.getExtraSlotAttributes(resolvedShooter);
            for (Map.Entry<String, Double> entry : extra.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        attrMemo = new AttrMemo(stackIdentity, tag, shooterIdentity, now, result);
        return result;
    }

    /**
     * 判断这把枪是不是射手当前的主手物品
     *
     * <p>与 {@code ExtraSlotTooltipHelper.isMainHandWeapon} 同一套判据：
     * 先比对象引用，再比内容；主副手内容完全相同时无法区分，按「不是主手」处理，
     * 保证面板与实际生效值在边界情况下也不会打架。</p>
     */
    private static boolean isMainHandGun(@Nonnull ItemStack gunItem, @Nonnull LivingEntity shooter) {
        ItemStack mainHand = shooter.getMainHandItem();
        if (gunItem == mainHand) {
            return true;
        }
        if (ItemStack.matches(gunItem, mainHand)) {
            return !ItemStack.matches(gunItem, shooter.getOffhandItem());
        }
        return false;
    }

    // ========== 模组属性读取 ==========

    public static float getFireRateMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (shooter instanceof Player player) {
            Double stackBonus = attributes.get("killStackFiringRate");
            if (stackBonus != null) {
                int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.FIRING_RATE);
                if (stacks > 0) {
                    firingRate += stackBonus * stacks;
                }
            }
        }
        return (float) firingRate;
    }

    public static float getMultishotMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        double multishot = attributes.getOrDefault("multishot", 0.0);
        if (shooter instanceof Player player) {
            Double stackBonus = attributes.get("killStackMultishot");
            if (stackBonus != null) {
                int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.MULTISHOT);
                if (stacks > 0) {
                    multishot += stackBonus * stacks;
                }
            }
        }
        return (float) multishot;
    }

    public static float getBurstingRadiusMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("bursting_radius", 0.0).floatValue();
    }

    // ========== TACZ 枪械属性读取（第一批）==========

    public static float getReloadSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("reload_speed", 0.0).floatValue();
    }

    /**
     * 弹匣容量修正（无射手上下文的旧签名，保留供现有调用方使用）
     *
     * <p>⭐ 本重载拿不到 shooter，只能退化为「枪本体 + 当前线程的缓存刷新上下文」。
     * 想让护甲 / 副手 / 饰品上的弹匣卡也吃到，请改调
     * {@link #getMagazineSizeMod(ItemStack, LivingEntity)}。</p>
     */
    public static float getMagazineSizeMod(ItemStack gunItem) {
        return getMagazineSizeMod(gunItem, null);
    }

    /**
     * 弹匣容量修正（带射手上下文）
     *
     * @param gunItem 枪械物品栈
     * @param shooter 射手，可为 null
     */
    public static float getMagazineSizeMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("magazine_size", 0.0).floatValue();
    }

    public static float getProjectileSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("projectile_speed", 0.0).floatValue();
    }

    public static float getRecoilReductionMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("recoil_reduction", 0.0).floatValue();
    }

    public static float getFirstBulletDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("first_bullet_damage", 0.0).floatValue();
    }

    // ========== TACZ 枪械属性读取（第二批）==========

    /**
     * 获取枪械上装载的 gun_damage 模组合计修正值。
     * 语义：独立伤害乘区。damageModifier *= (1 + gun_damage)。
     */
    public static float getGunDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("gun_damage", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 headshot_damage 模组合计修正值。
     * 语义：爆头倍率加成。headShotMultiplier *= (1 + headshot_damage)。
     */
    public static float getHeadshotDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("headshot_damage", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 aim_time 模组合计修正值。
     * 语义：瞄准速度加成。newAdsTime = originalAdsTime / (1 + aim_time)。
     */
    public static float getAimTimeMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("aim_time", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 accuracy 模组合计修正值。
     * 语义：精准度提升。newInaccuracy = oldInaccuracy * max(0, 1 - accuracy)。
     */
    public static float getAccuracyMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = gunAttrs(gunItem, shooter);
        return attributes.getOrDefault("accuracy", 0.0).floatValue();
    }
}