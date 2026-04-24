package pers.roinflam.kuvalich.render.particle;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 元素触发视觉特效工具类（星际战甲风格 · v8）
 * Element Trigger Visual Effects Utility
 *
 * <p>⭐ v8 新增：粒子密度按 amplifier 分层。
 * <ul>
 *     <li>Effect（持续特效）：按 amplifier 计算 {@code densityFactor ∈ [0.5, 1.0]}，
 *         所有 sendParticles 的 count 参数乘以该 factor。最低级粒子数约为满级的 50%。</li>
 *     <li>Burst（命中瞬间）：保持满强度，每击都是完整的视觉爆发。</li>
 *     <li>单层元素（POISON）、SynchronizationTask 驱动的元素（slash/gas）：保持原密度。</li>
 * </ul></p>
 *
 * <p>⭐ v6 色调：病毒粉色、毒气青薄荷色、移除所有紫色 DRAGON_BREATH、磁力完全
 * 去粒子（视觉改由 ElementGeometryRenderer 的几何多边形承担）。</p>
 *
 * <p>粒子均由服务端 {@link ServerLevel#sendParticles} 广播，32 格内客户端可见。
 * 染色与身上装饰由 {@code ElementRenderHandler} 负责，基于
 * {@code ClientElementDebuffTracker} 实现零延迟响应。</p>
 *
 * @author RoinFlam
 */
public final class ElementParticleEffects {

    // ==================== 色板 ====================

    /** 切割血红 / Slash blood red */
    private static final Vector3f COLOR_SLASH = new Vector3f(0.70f, 0.05f, 0.05f);

    /** 切割血红亮调（核心用）/ Slash bright red */
    private static final Vector3f COLOR_SLASH_CORE = new Vector3f(1.00f, 0.15f, 0.15f);

    /** 毒气青薄荷 / Gas mint-cyan */
    private static final Vector3f COLOR_GAS = new Vector3f(0.30f, 1.00f, 0.80f);

    /** 毒气深青薄荷（核心用）/ Gas deep mint-cyan */
    private static final Vector3f COLOR_GAS_CORE = new Vector3f(0.15f, 0.85f, 0.65f);

    /** 辐射亮黄 / Radiation bright yellow */
    private static final Vector3f COLOR_RADIATION_FROM = new Vector3f(0.95f, 1.00f, 0.20f);

    /** 辐射荧光绿 / Radiation neon green */
    private static final Vector3f COLOR_RADIATION_TO = new Vector3f(0.50f, 1.00f, 0.40f);

    /** 腐蚀深绿 / Corrosion dark green */
    private static final Vector3f COLOR_CORROSION = new Vector3f(0.30f, 0.60f, 0.10f);

    /** 腐蚀亮酸绿 / Corrosion bright acid green */
    private static final Vector3f COLOR_CORROSION_BRIGHT = new Vector3f(0.55f, 0.95f, 0.15f);

    /** 病毒粉色 / Virus pink */
    private static final Vector3f COLOR_VIRUS = new Vector3f(1.00f, 0.40f, 0.75f);

    /** 病毒浅粉 / Virus light pink */
    private static final Vector3f COLOR_VIRUS_TOXIC = new Vector3f(1.00f, 0.55f, 0.85f);

    /** 穿刺浅银 / Puncture pale silver */
    private static final Vector3f COLOR_PUNCTURE = new Vector3f(0.85f, 0.85f, 0.95f);

    /** 穿刺亮白（核心用）/ Puncture bright white */
    private static final Vector3f COLOR_PUNCTURE_CORE = new Vector3f(1.00f, 1.00f, 1.00f);

    /** 冰冻冰蓝 / Ice blue */
    private static final Vector3f COLOR_ICE_RING = new Vector3f(0.55f, 0.80f, 1.00f);

    /** 冰冻深蓝（核心用）/ Ice deep blue */
    private static final Vector3f COLOR_ICE_CORE = new Vector3f(0.20f, 0.55f, 1.00f);

    /** 火焰橙红 / Fire orange */
    private static final Vector3f COLOR_FIRE_RING = new Vector3f(1.00f, 0.50f, 0.10f);

    /** 火焰深红（核心用）/ Fire deep red */
    private static final Vector3f COLOR_FIRE_CORE = new Vector3f(1.00f, 0.15f, 0.00f);

    /** 毒素亮绿 / Poison bright green */
    private static final Vector3f COLOR_POISON_RING = new Vector3f(0.30f, 1.00f, 0.20f);

    /** 毒素深绿（核心用）/ Poison deep green */
    private static final Vector3f COLOR_POISON_CORE = new Vector3f(0.10f, 0.85f, 0.10f);

    /** 冲击白（核心用）/ Impact white */
    private static final Vector3f COLOR_IMPACT_CORE = new Vector3f(0.95f, 0.95f, 1.00f);

    // ==================== 分层工具 ====================

    /**
     * 根据 amplifier 和 maxLevel 计算粒子密度倍率（0.5 ~ 1.0）
     * <p>最低级（amplifier=0）返回 0.5，满级返回 1.0，其间线性插值。
     * 单层元素（maxLevel=0）直接返回 1.0 保持满密度。</p>
     *
     * @param amplifier 当前等级
     * @param maxLevel  元素最大等级
     * @return 密度倍率
     */
    private static float densityFactor(int amplifier, int maxLevel) {
        if (maxLevel <= 0) return 1.0f;
        float ratio = Math.min((float) amplifier / maxLevel, 1.0f);
        return 0.5f + 0.5f * ratio;
    }

    /**
     * 将粒子 count 乘以倍率，保证至少为 1（避免完全不显示）
     *
     * @param base   原始 count
     * @param factor 倍率
     * @return 缩放后的 count，最小 1
     */
    private static int scale(int base, float factor) {
        if (base <= 0) return 0;
        int scaled = (int) (base * factor);
        return Math.max(1, scaled);
    }

    // ==================== 通用粒子工具 ====================

    /**
     * 能量核：在指定位置生成多层大尺寸 Dust 叠加形成"光球"效果
     */
    private static void spawnEnergyCore(ServerLevel level, Vec3 center, Vector3f color) {
        DustParticleOptions outer = new DustParticleOptions(color, 3.0f);
        level.sendParticles(outer, center.x, center.y, center.z, 3,
                0.15, 0.15, 0.15, 0.0);
        DustParticleOptions mid = new DustParticleOptions(color, 2.0f);
        level.sendParticles(mid, center.x, center.y, center.z, 5,
                0.08, 0.08, 0.08, 0.0);
        DustParticleOptions inner = new DustParticleOptions(color, 1.2f);
        level.sendParticles(inner, center.x, center.y, center.z, 8,
                0.04, 0.04, 0.04, 0.0);
    }

    /**
     * 螺旋上升：从脚底螺旋向上生成粒子，类似能量柱
     */
    private static void spawnSpiralRise(ServerLevel level, Vec3 base, ParticleOptions particle,
                                        double height, double turns, int points, double radius) {
        int total = (int) (points * turns);
        for (int i = 0; i < total; i++) {
            double progress = (double) i / total;
            double angle = progress * turns * 2.0 * Math.PI;
            double px = base.x + Math.cos(angle) * radius;
            double pz = base.z + Math.sin(angle) * radius;
            double py = base.y + progress * height;
            level.sendParticles(particle, px, py, pz, 1, 0.0, 0.05, 0.0, 0.0);
        }
    }

    /**
     * 脚下圆环粒子（8 点均匀分布 + 时间相关旋转）
     */
    private static void spawnFootRing(ServerLevel level, LivingEntity entity,
                                      ParticleOptions particle, double radiusFactor, double heightOffset) {
        double cx = entity.getX();
        double cy = entity.getY() + heightOffset;
        double cz = entity.getZ();
        double radius = entity.getBbWidth() * radiusFactor;
        double timeOffset = (level.getGameTime() % 80) * Math.PI / 40.0;

        for (int i = 0; i < 8; i++) {
            double angle = timeOffset + i * Math.PI / 4.0;
            double px = cx + Math.cos(angle) * radius;
            double pz = cz + Math.sin(angle) * radius;
            level.sendParticles(particle, px, cy, pz, 1, 0.0, 0.02, 0.0, 0.0);
        }
    }

    /**
     * 在实体中心爆发式发射粒子（指定方向数量 + 向外速度）
     */
    private static void spawnRadialBurst(ServerLevel level, ParticleOptions particle,
                                         Vec3 center, int rays, double outSpeed) {
        for (int i = 0; i < rays; i++) {
            double yaw = i * 2.0 * Math.PI / rays;
            double pitch = (Math.random() - 0.5) * Math.PI * 0.3;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            level.sendParticles(particle, center.x, center.y, center.z, 1,
                    dx * outSpeed, dy * outSpeed, dz * outSpeed, outSpeed);
        }
    }

    // ========================================================================
    // ==================== 火焰 / Fire（maxLevel=3） ====================
    // ========================================================================

    /**
     * 火焰命中瞬间爆发（满强度，不分层）
     */
    public static void spawnFireBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);
        Vec3 base = new Vec3(x, target.getY() + 0.1, z);

        spawnEnergyCore(level, center, COLOR_FIRE_CORE);
        spawnRadialBurst(level, ParticleTypes.FLAME, center, 10, 0.3);
        spawnRadialBurst(level, ParticleTypes.SMALL_FLAME, center, 10, 0.22);
        spawnSpiralRise(level, base, ParticleTypes.SMALL_FLAME, h * 1.2, 2.0, 6, w * 0.35);
        level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, w * 0.2, h * 0.2, w * 0.2, 0.0);
        level.sendParticles(ParticleTypes.ASH, x, y, z, 12, w * 0.6, h * 0.4, w * 0.6, 0.08);
    }

    /**
     * 生成火焰持续特效（v8 按 amplifier 分层）
     *
     * @param target    目标实体
     * @param level     服务端世界
     * @param amplifier 当前等级（0-3）
     */
    public static void spawnFireEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight() * 0.5;
        float f = densityFactor(amplifier, 3);

        level.sendParticles(ParticleTypes.FLAME, x, y, z, scale(10, f), w, h, w, 0.02);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, scale(4, f), w * 0.8, h * 0.8, w * 0.8, 0.01);
        level.sendParticles(ParticleTypes.LAVA, x, y + h, z, scale(2, f), w * 0.3, 0.1, w * 0.3, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, x, y + h, z, scale(6, f), w * 0.5, 0.2, w * 0.5, 0.08);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + h * 1.5, z, scale(3, f),
                w * 0.3, 0.1, w * 0.3, 0.03);
        level.sendParticles(ParticleTypes.ASH, x, y, z, scale(4, f), w, h * 0.6, w, 0.05);

        DustParticleOptions fireRing = new DustParticleOptions(COLOR_FIRE_RING, 1.5f);
        spawnFootRing(level, target, fireRing, 0.55, 0.05);
    }

    // ========================================================================
    // ==================== 冰冻 / Ice（maxLevel=8） ====================
    // ========================================================================

    /**
     * 冰冻命中瞬间爆发（满强度）
     */
    public static void spawnIceBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);
        Vec3 base = new Vec3(x, target.getY() + 0.1, z);

        spawnEnergyCore(level, center, COLOR_ICE_CORE);
        spawnRadialBurst(level, ParticleTypes.END_ROD, center, 14, 0.45);
        spawnRadialBurst(level, ParticleTypes.SNOWFLAKE, center, 14, 0.35);
        spawnSpiralRise(level, base, ParticleTypes.SNOWFLAKE, h * 1.1, 1.5, 8, w * 0.4);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 10,
                w * 0.4, h * 0.3, w * 0.4, 0.2);
    }

    /**
     * 生成冰冻持续特效（v8 按 amplifier 分层）
     */
    public static void spawnIceEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        float f = densityFactor(amplifier, 8);

        level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + h, z, scale(8, f), w, h * 0.3, w, 0.05);
        // 冰晶数量原本就和 amplifier 有关，这里沿用但再乘 f 做分层
        int crystalCount = scale(3 + Math.min(amplifier / 2, 4), f);
        level.sendParticles(ParticleTypes.END_ROD, x, y + h * 0.3, z, crystalCount,
                w * 0.6, h * 0.4, w * 0.6, 0.01);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y + h * 0.7, z, scale(2, f),
                w, 0.1, w, 0.02);
        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, x, y + h, z, scale(2, f),
                w * 0.7, 0.0, w * 0.7, 0.0);

        DustParticleOptions iceRing = new DustParticleOptions(COLOR_ICE_RING, 1.4f);
        spawnFootRing(level, target, iceRing, 0.55, 0.05);
    }

    // ========================================================================
    // ==================== 毒素 / Poison（单层，不分层） ====================
    // ========================================================================

    /**
     * 毒素命中瞬间爆发
     */
    public static void spawnPoisonBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);
        Vec3 base = new Vec3(x, target.getY() + 0.1, z);

        spawnEnergyCore(level, center, COLOR_POISON_CORE);

        DustColorTransitionOptions poisonGrad = new DustColorTransitionOptions(
                COLOR_POISON_RING, COLOR_POISON_CORE, 1.8f);
        spawnRadialBurst(level, poisonGrad, center, 14, 0.35);

        DustParticleOptions poisonDust = new DustParticleOptions(COLOR_POISON_CORE, 1.5f);
        spawnSpiralRise(level, base, poisonDust, h * 1.1, 1.8, 7, w * 0.4);

        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 12,
                w * 0.4, h * 0.4, w * 0.4, 0.15);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 8,
                w * 0.5, h * 0.3, w * 0.5, 0.2);
        spawnRadialBurst(level, ParticleTypes.SNEEZE, center, 8, 0.2);
    }

    /**
     * 生成毒素持续特效（单层元素，不分层）
     */
    public static void spawnPoisonEffect(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth() * 0.4;
        double h = target.getBbHeight();

        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + h * 0.3, z, 5,
                w * 0.6, h * 0.5, w * 0.6, 0.05);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y + h * 0.5, z, 3,
                w * 0.8, h * 0.4, w * 0.8, 0.02);
        level.sendParticles(ParticleTypes.SNEEZE, x, y + h * 0.8, z, 3,
                w * 0.6, 0.2, w * 0.6, 0.05);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y + 0.1, z, 2,
                w * 1.2, 0.05, w * 1.2, 0.01);
        DustParticleOptions poisonMist = new DustParticleOptions(COLOR_POISON_CORE, 1.3f);
        level.sendParticles(poisonMist, x, y + h * 0.5, z, 4,
                w * 0.7, h * 0.3, w * 0.7, 0.02);

        DustParticleOptions poisonRing = new DustParticleOptions(COLOR_POISON_RING, 1.5f);
        spawnFootRing(level, target, poisonRing, 0.60, 0.05);
    }

    // ========================================================================
    // ==================== 切割 / Slash（不分层，SynchronizationTask 管理） ====================
    // ========================================================================

    /**
     * 切割命中瞬间爆发
     */
    public static void spawnSlashBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_SLASH_CORE);

        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + h * 0.3, z, 1, 0.0, 0.0, 0.0, 0.0);

        DustColorTransitionOptions bloodGrad = new DustColorTransitionOptions(
                COLOR_SLASH_CORE, COLOR_SLASH, 1.8f);
        spawnRadialBurst(level, bloodGrad, center, 16, 0.4);

        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 6,
                w * 0.5, h * 0.3, w * 0.5, 0.2);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 10,
                w * 0.6, h * 0.5, w * 0.6, 0.1);
    }

    /**
     * 生成切割持续特效（SynchronizationTask 管理，不分层）
     */
    public static void spawnSlashEffect(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth() * 0.4;
        double h = target.getBbHeight() * 0.4;

        DustParticleOptions bloodDust = new DustParticleOptions(COLOR_SLASH, 1.4f);
        level.sendParticles(bloodDust, x, y, z, 12, w, h, w, 0.15);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 3,
                w * 0.5, h * 0.3, w * 0.5, 0.05);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, target.getY() + target.getBbHeight(), z, 4,
                w * 0.8, 0.2, w * 0.8, 0.02);
    }

    // ========================================================================
    // ==================== 穿刺 / Puncture（maxLevel=3） ====================
    // ========================================================================

    /**
     * 穿刺命中瞬间爆发
     */
    public static void spawnPunctureBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_PUNCTURE_CORE);
        spawnRadialBurst(level, ParticleTypes.END_ROD, center, 14, 0.6);

        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 14,
                w * 0.5, h * 0.4, w * 0.5, 0.35);

        DustColorTransitionOptions silverGrad = new DustColorTransitionOptions(
                COLOR_PUNCTURE_CORE, COLOR_PUNCTURE, 2.0f);
        spawnRadialBurst(level, silverGrad, center, 12, 0.35);
    }

    /**
     * 生成穿刺持续特效（v8 按 amplifier 分层）
     */
    public static void spawnPunctureEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        float f = densityFactor(amplifier, 3);

        // 针状粒子数量原本和 amplifier 有关，再乘 f 分层
        level.sendParticles(ParticleTypes.END_ROD, x, y + h * 0.5, z,
                scale(4 + amplifier, f), w, h * 0.5, w, 0.02);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y + h * 0.6, z, scale(3, f),
                w, h * 0.4, w, 0.1);
        DustParticleOptions punctureDust = new DustParticleOptions(COLOR_PUNCTURE, 1.2f);
        level.sendParticles(punctureDust, x, y + h * 0.5, z, scale(6, f), w * 0.7, h * 0.4, w * 0.7, 0.0);
        level.sendParticles(ParticleTypes.GLOW, x, y + h * 0.5, z, scale(3, f),
                w * 0.6, h * 0.3, w * 0.6, 0.02);

        DustParticleOptions punctureRing = new DustParticleOptions(COLOR_PUNCTURE, 1.4f);
        spawnFootRing(level, target, punctureRing, 0.55, 0.05);
    }

    // ========================================================================
    // ==================== 冲击 / Impact（瞬发，不分层） ====================
    // ========================================================================

    /**
     * 冲击波瞬发特效
     */
    public static void spawnImpactShockwave(ServerLevel level, LivingEntity target) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.3;
        double z = target.getZ();
        double w = target.getBbWidth();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_IMPACT_CORE);

        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y + 0.3, z, 1, 0.0, 0.0, 0.0, 0.0);

        double[] radii = {0.3, 0.6, 0.9};
        for (double rFactor : radii) {
            double r = w * rFactor;
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6.0;
                double px = x + Math.cos(angle) * r;
                double pz = z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.POOF, px, y, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        level.sendParticles(ParticleTypes.CLOUD, x, target.getY() + 0.2, z, 10,
                w * 0.5, 0.2, w * 0.5, 0.22);
    }

    // ========================================================================
    // ==================== 毒气 / Gas（不分层，SynchronizationTask 管理） ====================
    // ========================================================================

    /**
     * 毒气初始释放爆发
     */
    public static void spawnGasBurst(ServerLevel level, Vec3 center, double radius) {
        Vec3 aboveCenter = new Vec3(center.x, center.y + 1.0, center.z);

        spawnEnergyCore(level, aboveCenter, COLOR_GAS_CORE);

        DustColorTransitionOptions gasGrad = new DustColorTransitionOptions(
                COLOR_GAS, COLOR_GAS_CORE, 2.5f);
        level.sendParticles(gasGrad, center.x, center.y + 0.8, center.z, 45,
                radius * 0.9, radius * 0.6, radius * 0.9, 0.12);

        level.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 0.5, center.z, 18,
                radius * 0.8, 0.4, radius * 0.8, 0.15);

        spawnRadialBurst(level, ParticleTypes.SPORE_BLOSSOM_AIR, aboveCenter, 12, radius * 0.25);
    }

    /**
     * 生成毒气云 AOE 特效
     */
    public static void spawnGasCloudEffect(ServerLevel level, Vec3 center, double radius) {
        DustParticleOptions gasDust = new DustParticleOptions(COLOR_GAS, 2.0f);
        level.sendParticles(gasDust, center.x, center.y + 0.5, center.z, 25,
                radius * 0.8, radius * 0.5, radius * 0.8, 0.0);
        level.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 0.3, center.z, 10,
                radius * 0.7, 0.3, radius * 0.7, 0.02);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, center.x, center.y + 1.0, center.z, 8,
                radius * 0.5, 0.5, radius * 0.5, 0.0);
        level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, center.x, center.y + 1.8, center.z, 6,
                radius * 0.6, 0.0, radius * 0.6, 0.0);
    }

    // ========================================================================
    // ==================== 磁力 / Magnetic（v6 已移除粒子） ====================
    // ========================================================================

    // ⭐ 磁力视觉完全由 ElementGeometryRenderer 承担（v8 升级：主六边形 +
    //   副六边形 + 卫星三角形 + 呼吸动画）。无粒子调用。

    // ========================================================================
    // ==================== 辐射 / Radiation（maxLevel=9） ====================
    // ========================================================================

    /**
     * 辐射命中瞬间爆发
     */
    public static void spawnRadiationBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_RADIATION_TO);

        DustColorTransitionOptions radDust = new DustColorTransitionOptions(
                COLOR_RADIATION_FROM, COLOR_RADIATION_TO, 2.0f);
        spawnRadialBurst(level, radDust, center, 16, 0.45);

        level.sendParticles(ParticleTypes.ENCHANT, x, y + h * 1.5, z, 20,
                w * 0.5, 0.2, w * 0.5, 1.0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 10,
                w * 0.6, h * 0.4, w * 0.6, 0.2);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 8,
                w * 0.5, h * 0.4, w * 0.5, 0.25);
        level.sendParticles(ParticleTypes.GLOW, x, y, z, 12,
                w * 0.6, h * 0.4, w * 0.6, 0.15);
    }

    /**
     * 生成辐射持续特效（v8 按 amplifier 分层）
     */
    public static void spawnRadiationEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        float f = densityFactor(amplifier, 9);

        DustColorTransitionOptions radDust = new DustColorTransitionOptions(
                COLOR_RADIATION_FROM, COLOR_RADIATION_TO, 1.4f);
        int dustCount = scale(10 + Math.min(amplifier, 6), f);
        level.sendParticles(radDust, x, y + h * 0.5, z, dustCount,
                w * 0.7, h * 0.5, w * 0.7, 0.0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + h * 0.5, z, scale(3, f),
                w * 0.5, h * 0.3, w * 0.5, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + h * 1.2, z, scale(6, f),
                w * 0.5, 0.3, w * 0.5, 0.5);
        level.sendParticles(ParticleTypes.GLOW, x, y + h * 0.5, z, scale(4, f),
                w * 0.6, h * 0.4, w * 0.6, 0.05);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + h * 0.5, z, scale(3, f),
                w * 0.5, h * 0.3, w * 0.5, 0.1);

        spawnFootRing(level, target, ParticleTypes.SCULK_SOUL, 0.60, 0.05);
    }

    // ========================================================================
    // ==================== 腐蚀 / Corrosion（maxLevel=9） ====================
    // ========================================================================

    /**
     * 腐蚀命中瞬间爆发
     */
    public static void spawnCorrosionBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_CORROSION);

        DustColorTransitionOptions corrosionGrad = new DustColorTransitionOptions(
                COLOR_CORROSION, COLOR_CORROSION_BRIGHT, 2.0f);
        spawnRadialBurst(level, corrosionGrad, center, 14, 0.4);

        level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y, z, 12,
                w * 0.5, h * 0.5, w * 0.5, 0.15);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 10,
                w * 0.5, h * 0.4, w * 0.5, 0.25);
        level.sendParticles(ParticleTypes.LANDING_LAVA, x, target.getY() + 0.1, z, 8,
                w * 0.5, 0.05, w * 0.5, 0.08);
        level.sendParticles(ParticleTypes.FALLING_NECTAR, x, y + h * 0.5, z, 8,
                w * 0.5, h * 0.4, w * 0.5, 0.1);
    }

    /**
     * 生成腐蚀持续特效（v8 按 amplifier 分层）
     */
    public static void spawnCorrosionEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        float f = densityFactor(amplifier, 9);

        DustParticleOptions corrosionDust = new DustParticleOptions(COLOR_CORROSION, 1.6f);
        int dustCount = scale(8 + Math.min(amplifier / 2, 4), f);
        level.sendParticles(corrosionDust, x, y + h * 0.6, z, dustCount,
                w, h * 0.4, w, 0.05);
        level.sendParticles(ParticleTypes.ITEM_SLIME, x, y + h * 0.3, z, scale(3, f),
                w * 0.8, h * 0.3, w * 0.8, 0.05);
        level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y + h, z, scale(2, f),
                w * 0.6, 0.1, w * 0.6, 0.0);
        level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_WATER, x, y + h, z, scale(2, f),
                w * 0.5, 0.0, w * 0.5, 0.0);
        level.sendParticles(ParticleTypes.LANDING_LAVA, x, y + 0.05, z, scale(2, f),
                w * 0.8, 0.03, w * 0.8, 0.0);
        level.sendParticles(ParticleTypes.FALLING_NECTAR, x, y + h * 0.6, z, scale(3, f),
                w * 0.7, h * 0.3, w * 0.7, 0.0);

        DustParticleOptions corrosionRing = new DustParticleOptions(COLOR_CORROSION_BRIGHT, 1.5f);
        spawnFootRing(level, target, corrosionRing, 0.60, 0.05);
    }

    // ========================================================================
    // ==================== 病毒 / Virus（maxLevel=9，粉色） ====================
    // ========================================================================

    /**
     * 病毒命中瞬间爆发
     */
    public static void spawnVirusBurst(LivingEntity target, ServerLevel level) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double w = target.getBbWidth();
        double h = target.getBbHeight();
        Vec3 center = new Vec3(x, y, z);

        spawnEnergyCore(level, center, COLOR_VIRUS);

        DustColorTransitionOptions virusGrad = new DustColorTransitionOptions(
                COLOR_VIRUS, COLOR_VIRUS_TOXIC, 2.0f);
        spawnRadialBurst(level, virusGrad, center, 14, 0.4);

        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 18,
                w * 0.5, h * 0.5, w * 0.5, 0.15);
        level.sendParticles(ParticleTypes.WARPED_SPORE, x, y, z, 20,
                w * 0.6, h * 0.5, w * 0.6, 0.25);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 10,
                w * 0.4, h * 0.4, w * 0.4, 0.2);

        DustParticleOptions pinkDust = new DustParticleOptions(COLOR_VIRUS_TOXIC, 1.5f);
        level.sendParticles(pinkDust, x, y, z, 15,
                w * 0.7, h * 0.5, w * 0.7, 0.1);
    }

    /**
     * 生成病毒持续特效（v8 按 amplifier 分层）
     */
    public static void spawnVirusEffect(LivingEntity target, ServerLevel level, int amplifier) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        double w = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        float f = densityFactor(amplifier, 9);

        DustParticleOptions virusDust = new DustParticleOptions(COLOR_VIRUS, 1.3f);
        level.sendParticles(virusDust, x, y + h * 0.5, z, scale(10, f), w, h * 0.5, w, 0.03);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + h * 0.7, z, scale(4, f),
                w * 0.7, h * 0.3, w * 0.7, 0.0);
        int warpedCount = scale(5 + Math.min(amplifier / 2, 4), f);
        level.sendParticles(ParticleTypes.WARPED_SPORE, x, y + h * 0.5, z, warpedCount,
                w * 0.8, h * 0.5, w * 0.8, 0.02);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y + h * 0.6, z, scale(3, f),
                w * 0.6, h * 0.3, w * 0.6, 0.05);
        DustParticleOptions pinkMist = new DustParticleOptions(COLOR_VIRUS_TOXIC, 1.2f);
        level.sendParticles(pinkMist, x, y + h * 0.5, z, scale(3, f),
                w * 0.7, h * 0.3, w * 0.7, 0.02);

        DustParticleOptions virusRing = new DustParticleOptions(COLOR_VIRUS_TOXIC, 1.4f);
        spawnFootRing(level, target, virusRing, 0.60, 0.05);
    }

    /** 工具类禁止实例化 / Utility class, no instantiation */
    private ElementParticleEffects() {
    }
}
