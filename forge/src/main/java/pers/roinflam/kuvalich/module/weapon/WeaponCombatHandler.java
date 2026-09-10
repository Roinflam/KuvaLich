package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.projectile.AbstractArrow;
import pers.roinflam.kuvalich.compat.curios.CuriosCompat;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器战斗事件处理器
 * 负责所有伤害事件、弓箭多重射击、射速加速、攻击速度/攻击距离tick
 *
 * <p>⭐ 本次改动（三项，均为近战 / 远程词条串用修复）：</p>
 *
 * <p><b>一、爆炸半径溅射的伤害口径与重复结算。</b><br>
 * 原实现在远程分支内立即执行溅射：
 * {@code entity.hurt(getAttackDamageSource(attacker), evt.getAmount() * 0.5f)}。
 * 这里有两个独立问题：</p>
 * <ul>
 *   <li><b>取值时机错误</b>：{@code evt.setAmount(totalDamage)} 在方法末尾才执行，
 *       所以此处的 {@code evt.getAmount()} 仍是<b>未经任何模组加成的原始伤害</b>。</li>
 *   <li><b>被当成近战重复结算</b>：{@code getAttackDamageSource} 对玩家返回
 *       {@code playerAttack(player)}，其 {@code directEntity} 就是玩家本人，
 *       该伤害重新进入 {@link #onLivingHurt} 时 {@code directEntity == attacker} 成立，
 *       于是走进近战分支，去吃 {@code meleeDamage}、近战暴击率、近战暴伤、
 *       {@code MELEE_CRIT_MULT} 叠层，并重复跑一遍元素触发、处决判定与击杀叠层累加。</li>
 * </ul>
 * <p>两个问题方向恰好相反：取的是未加成的低值，却被近战面板再放大一遍，
 * 结果既不是远程口径也不是近战口径。<br>
 * 修复：溅射整体移到 {@code evt.setAmount(totalDamage)} <b>之后</b>，
 * 改取主目标最终伤害的一半（见 {@link #applySplashDamage}），
 * 自然继承远程面板、远程暴击与克制倍率；同时用 {@link #SPLASH_REENTRY}
 * 线程标记让本处理器跳过派生伤害，杜绝二次放大。<br>
 * 溅射目标的伤害数字会主动入队并复用主目标的暴击颜色码，因此暴击色保留；
 * 但溅射<b>不再</b>对每个目标独立掷元素触发，也不累加击杀叠层，只结算伤害。</p>
 *
 * <p><b>二、{@code dashMeleeCriticalStrikeProbability} 名实不符。</b><br>
 * 词条名为「冲刺近战暴击<b>率</b>」，原实现却把它加到了暴击<b>伤害</b>倍率上，
 * 与 {@code meleeCriticalStrikeMultiplier} 混在同一个括号内。<br>
 * 修复：移到暴击率计算上，且仅在 {@code isSprinting()} 时叠加；
 * 暴击伤害只保留 {@code meleeCriticalStrikeMultiplier}。</p>
 *
 * <p><b>三、{@code attackStrength} 是死代码。</b><br>
 * {@code onLivingHurt} 调用 {@code processDamage} 时恒传 {@code 1.0f}，
 * 导致 {@code criticalStrikeProbability *= attackStrength} 永远无效，
 * 近战蓄力程度对暴击率毫无影响。<br>
 * 无法在 {@code LivingHurtEvent} 内补救：原版 {@code Player.attack()} 会在
 * {@code target.hurt(...)} <b>之前</b>调用 {@code resetAttackStrengthTicker()}，
 * 此时再读 {@code getAttackStrengthScale} 只会得到重置后的值。<br>
 * 修复：在 {@link #onAttackEntity} 中提前捕获真实蓄力比例（该事件由
 * {@code ForgeHooks.onPlayerAttackTarget} 在 {@code attack()} 开头触发，
 * 冷却尚未重置），经 {@link #MELEE_ATTACK_STRENGTH} 传递到 {@code processDamage}，
 * 取用后立即清除，陈旧值最多被消费一次。<br>
 * <b>该项会改变数值平衡</b>（连点轻击的暴击率将按蓄力比例衰减）。
 * 如需维持修复前行为，把 {@link #ENABLE_ATTACK_STRENGTH_SCALING} 改为 {@code false} 即可，
 * 其余两项修复不受影响。</p>
 *
 * <p>⭐ 此前改动：伤害数字的读取时机由「{@code LivingDamageEvent} 监听器」
 * 改为「Mixin 捕获 {@code ForgeHooks.onLivingDamage} 的返回值」。</p>
 *
 * <p><b>为什么必须换：</b>基于事件监听器读取存在无法消除的优先级竞争。
 * Forge 的最低优先级是 {@code LOWEST}，而下列监听器同样注册在 {@code LOWEST}
 * 且会修改伤害数值：</p>
 * <ul>
 *   <li>{@code l2damagetracker.AttackEventHandler.onDamagePost}
 *       （以 jar-in-jar 内嵌于 L2Hostility 莱特兰·恶意）——
 *       其内部 {@code AttackCache.pushDamagePre} 会 {@code setAmount}，
 *       应用莱特兰词缀的最终减伤 / 免疫。</li>
 *   <li>本模组的 {@code WarframeEffectHandler.onLivingDamage} ——
 *       「受害者是玩家」分支会应用火抗 / 电抗 / 同源抗性。</li>
 * </ul>
 * <p>Forge 对同优先级监听器按注册顺序执行，顺序不受控，
 * 因此无论把本处理器设为何种优先级，都有概率读到中间值。</p>
 *
 * <p><b>新方案：</b>{@code MixinLivingEntityFinalDamage} 与
 * {@code MixinPlayerFinalDamage} 拦截 {@code ForgeHooks.onLivingDamage}
 * 的返回值后回调 {@link #onFinalDamage}。该返回值是整个事件链跑完后的结果，
 * 紧接着下一行就是 {@code setHealth}，因此必然等于实体实际掉的血量，
 * 与任何模组的监听器优先级、注册顺序均无关。</p>
 *
 * <p>该方案顺带堵死了原先的队列错位窗口：即便最终伤害被减为 0，
 * 回调依然会触发并清理 {@code pendingDisplays} 条目，
 * 不会再残留到下次受击时被错误消费（颜色码与元素图标张冠李戴）。</p>
 *
 * <p><b>护盾说明</b>：捕获的值已扣除吸收护盾（护盾吃掉的部分不计入），此为既定设计。</p>
 *
 * <p>⭐ 此前修复（两项）：</p>
 *
 * <p><b>一、伤害显示队列内存泄漏。</b><br>
 * {@code pendingDisplays} 在 {@code onLivingHurt} 入队、靠最终伤害回调出队消费。
 * 但当实体在两个时机之间被移除、或伤害在 {@code LivingHurtEvent} 后被归零
 * 导致后续流程提前返回时，条目会残留。
 * 更糟的是原条目直接持有 {@link ServerPlayer} <b>强引用</b>，
 * 玩家下线后整个玩家实体（含背包、Capability）都无法被 GC。<br>
 * 修复：条目改存玩家 UUID + 入队 tick，发包时按 UUID 现查；
 * 并新增 {@link #onServerTick} 定期清扫过期条目。</p>
 *
 * <p><b>二、元素池重复构建。</b><br>
 * 元素池在一次攻击内是常量，但原实现每次元素触发都重建一遍
 * （最多 20 次/目标）。现改为在 {@link #processDamage} 里预计算一次，
 * 通过 {@link WeaponElementSystem.ElementPool} 传入；
 * 且池为空时直接跳过整个触发循环。</p>
 *
 * <p>其余行为（多槽位加成、击杀叠层、处决、真伤等）与原版完全一致。</p>
 */
@Mod.EventBusSubscriber
public class WeaponCombatHandler {

    // ========== ⭐ 溅射重入防护 / Splash Re-entry Guard ==========

    /**
     * 爆炸半径溅射伤害的重入标记
     *
     * <p>溅射伤害源为 {@code playerAttack(player)}，其 {@code directEntity} 是玩家本人，
     * 会被 {@link #onLivingHurt} 误判为近战攻击并再次进入完整的模组结算流程。
     * 溅射期间置位，{@code onLivingHurt} 见到标记直接返回。</p>
     *
     * <p>{@code entity.hurt(...)} 在同一线程同一调用栈内同步触发事件链，
     * 因此 ThreadLocal 判定不会误伤其他玩家的并行攻击。</p>
     */
    private static final ThreadLocal<Boolean> SPLASH_REENTRY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 溅射伤害占主目标最终伤害的比例 */
    private static final float SPLASH_DAMAGE_RATIO = 0.5f;

    // ========== ⭐ 近战攻击强度传递 / Melee Attack Strength Relay ==========

    /**
     * 是否启用近战蓄力强度对暴击率的缩放
     *
     * <p>{@code true}：暴击率按 {@code getAttackStrengthScale} 缩放，连点轻击暴击率下降；<br>
     * {@code false}：维持修复前行为（恒按满蓄力 1.0 计算）。</p>
     *
     * <p>注意：原版 {@code Player.attack()} 已用 {@code 0.2 + f² × 0.8} 缩放过基础伤害，
     * 本项是在此之上对暴击率的<b>额外</b>约束，开启后近战连点流派会被明显削弱。</p>
     */
    private static final boolean ENABLE_ATTACK_STRENGTH_SCALING = true;

    /**
     * 本次近战攻击的蓄力比例（0.0 ~ 1.0）
     *
     * <p>在 {@link #onAttackEntity} 中写入，在 {@link #onLivingHurt} 中取出后立即清除。
     * 原版会在 {@code target.hurt(...)} 之前重置攻击冷却，因此必须在
     * {@code AttackEntityEvent} 阶段捕获，不能等到伤害事件里现读。</p>
     */
    private static final ThreadLocal<Float> MELEE_ATTACK_STRENGTH = new ThreadLocal<>();

    // ========== 伤害显示缓冲 / Damage Display Buffer ==========

    /**
     * 临时存储待显示的伤害信息
     *
     * <p>⭐ 只存玩家 UUID 而非 {@link ServerPlayer} 引用，避免玩家实体被队列残留条目钉住无法 GC。</p>
     */
    private static class DamageDisplayInfo {
        /** 暴击颜色代码 */
        final String colorCode;
        /** 触发的元素集合 */
        final Set<String> triggeredElements;
        /** 显示前缀（玩家为空，宠物/女仆为🎀，无颜色代码，继承暴击颜色） */
        final String prefix;
        /** 接收伤害数字的玩家 UUID（弱引用语义，发包时现查） */
        final UUID displayTargetId;
        /** 入队时的服务端 tick 序号，用于兜底清理 */
        final long createdTick;

        DamageDisplayInfo(String colorCode, Set<String> triggeredElements,
                          String prefix, UUID displayTargetId, long createdTick) {
            this.colorCode = colorCode;
            this.triggeredElements = triggeredElements;
            this.prefix = prefix;
            this.displayTargetId = displayTargetId;
            this.createdTick = createdTick;
        }
    }

    /**
     * 使用 ConcurrentHashMap 存储每个受害者实体的待显示伤害信息
     * 正常情况下条目在最终伤害回调中被立即消费，异常情况由定期清扫兜底
     */
    private static final Map<Integer, Deque<DamageDisplayInfo>> pendingDisplays = new ConcurrentHashMap<>();

    /** 服务端 tick 序号（跨维度统一基准，仅用于队列过期判定）*/
    private static volatile long serverTick = 0L;

    /** 队列清扫间隔（tick）：100 tick = 5 秒 */
    private static final int DISPLAY_CLEANUP_INTERVAL = 100;

    /** 队列条目最大存活时长（tick）：超过即视为无人消费，直接丢弃 */
    private static final int DISPLAY_MAX_AGE_TICKS = 40;

    /** 清扫计时器 */
    private static int displayCleanupCounter = 0;

    // ========== Mixin 自检 / Mixin Self-Check ==========

    /**
     * ⭐ 诊断标志：最终伤害 Mixin 回调是否至少成功触发过一次
     *
     * <p>用于区分「Mixin 注入失败」与「其他原因导致不显示」两类问题。
     * 首次触发时打印一条 INFO，之后不再输出。</p>
     */
    private static volatile boolean finalDamageHookVerified = false;

    /** ⭐ 诊断标志：Mixin 缺失告警是否已打印过（避免刷屏，全程仅告警一次） */
    private static volatile boolean hookMissingWarned = false;

    // ========== 多槽位属性缓存 / Multi-Slot Attribute Cache ==========

    /**
     * 额外槽位属性缓存（已含倍率预计算）
     * <p>key = 实体UUID，value = 所有额外槽位模组属性合并后的结果（倍率已乘入）。
     * 每 EXTRA_SLOT_CACHE_INTERVAL tick 清空一次。</p>
     */
    private static final Map<UUID, HashMap<String, Double>> EXTRA_SLOT_CACHE = new ConcurrentHashMap<>();

    /** 上次清空额外槽位缓存的 gameTick */
    private static long extraSlotCacheTick = -1;

    /** 额外槽位缓存刷新间隔（tick）：40 tick = 2秒 */
    private static final long EXTRA_SLOT_CACHE_INTERVAL = 40L;

    // ========== 元素触发硬上限 / Element Trigger Hard Cap ==========

    /**
     * 单次伤害事件中元素效果的最大触发次数（硬上限）
     *
     * <p>防止超高 triggerChance 在群体攻击场景下触发数百次元素效果导致主线程卡死。</p>
     */
    private static final int MAX_ELEMENT_TRIGGER_COUNT = 20;

    // ========== 服务端 Tick：计时 + 队列清扫 ==========

    /**
     * ⭐ 服务端 tick 末推进计时器并定期清扫伤害显示队列的僵尸条目
     *
     * @param evt 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }
        serverTick++;

        if (pendingDisplays.isEmpty()) {
            displayCleanupCounter = 0;
            return;
        }
        if (++displayCleanupCounter < DISPLAY_CLEANUP_INTERVAL) {
            return;
        }
        displayCleanupCounter = 0;

        long now = serverTick;
        Iterator<Map.Entry<Integer, Deque<DamageDisplayInfo>>> iterator = pendingDisplays.entrySet().iterator();
        boolean droppedAny = false;
        while (iterator.hasNext()) {
            Deque<DamageDisplayInfo> queue = iterator.next().getValue();
            // 队列按入队顺序递增，从队首丢弃过期条目即可
            while (!queue.isEmpty() && now - queue.peekFirst().createdTick > DISPLAY_MAX_AGE_TICKS) {
                queue.pollFirst();
                droppedAny = true;
            }
            if (queue.isEmpty()) {
                iterator.remove();
            }
        }

        // ⭐ 自检告警：有条目超时被丢弃，且 Mixin 回调从未触发过 → 几乎可以断定 Mixin 未生效
        if (droppedAny && !finalDamageHookVerified && !hookMissingWarned) {
            hookMissingWarned = true;
            LogUtil.error("[伤害显示] 伤害显示队列出现超时丢弃，且最终伤害 Mixin 回调从未触发。");
            LogUtil.error("[伤害显示] 判定：MixinLivingEntityFinalDamage / MixinPlayerFinalDamage 未生效，伤害数字将无法显示。");
            LogUtil.error("[伤害显示] 排查：1) 确认 kuvalich.mixins.json 的 mixins 数组已包含这两个类；");
            LogUtil.error("[伤害显示]       2) 确认已执行 clean build 重新生成 refmap；");
            LogUtil.error("[伤害显示]       3) 在启动日志中搜索 kuvalich 与 mixin 关键字查看注入报错。");
        }
    }

    // ========== 额外槽位属性 / Extra Slot Attributes ==========

    /**
     * 获取缓存的额外槽位属性（含倍率已预计算）
     *
     * @param entity 实体
     * @return 额外槽位属性表（只读使用，不要修改）
     */
    static HashMap<String, Double> getCachedExtraSlotAttributes(LivingEntity entity) {
        long currentTick = entity.level().getGameTime();
        if (Math.abs(currentTick - extraSlotCacheTick) >= EXTRA_SLOT_CACHE_INTERVAL) {
            EXTRA_SLOT_CACHE.clear();
            extraSlotCacheTick = currentTick;
        }
        return EXTRA_SLOT_CACHE.computeIfAbsent(entity.getUUID(), uuid -> computeExtraSlotAttributes(entity));
    }

    /**
     * 计算实体所有额外槽位的模组属性合并结果（含倍率）
     *
     * @param entity 实体
     * @return 合并后的额外属性表
     */
    private static HashMap<String, Double> computeExtraSlotAttributes(LivingEntity entity) {
        HashMap<String, Double> result = new HashMap<>();

        // 副手 / Off-hand
        if (ModConfig.KUVA_LICH.enableOffhandModule.get()) {
            double offhandMult = ModConfig.KUVA_LICH.offhandEffectMultiplier.get() / 100.0;
            mergeSlotAttributes(entity.getOffhandItem(), result, offhandMult);
        }

        // 护甲统一倍率 / Armor unified multiplier
        double armorMult = ModConfig.KUVA_LICH.armorEffectMultiplier.get() / 100.0;

        if (ModConfig.KUVA_LICH.enableHelmetModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.HEAD), result, armorMult);
        }
        if (ModConfig.KUVA_LICH.enableChestplateModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.CHEST), result, armorMult);
        }
        if (ModConfig.KUVA_LICH.enableLeggingsModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.LEGS), result, armorMult);
        }
        if (ModConfig.KUVA_LICH.enableBootsModule.get()) {
            mergeSlotAttributes(entity.getItemBySlot(EquipmentSlot.FEET), result, armorMult);
        }

        // Curios饰品栏（需要Curios模组） / Curios trinket slots (requires Curios mod)
        if (ModConfig.KUVA_LICH.enableCuriosModule.get()) {
            double curiosMult = ModConfig.KUVA_LICH.curiosEffectMultiplier.get() / 100.0;
            int maxSlots = ModConfig.KUVA_LICH.curiosModuleMaxSlots.get();
            List<ItemStack> curiosItems = CuriosCompat.getEquippedCurios(entity, maxSlots);
            for (ItemStack curio : curiosItems) {
                mergeSlotAttributes(curio, result, curiosMult);
            }
        }

        return result;
    }

    /**
     * 将缓存的额外槽位属性合并到战斗属性表
     *
     * @param entity     实体
     * @param attributes 要合并到的属性表
     */
    private static void mergeAdditionalSlotAttributes(LivingEntity entity, HashMap<String, Double> attributes) {
        HashMap<String, Double> cached = getCachedExtraSlotAttributes(entity);
        for (Map.Entry<String, Double> entry : cached.entrySet()) {
            attributes.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
    }

    /**
     * 将单个物品的模组属性按倍率合并到目标属性表
     *
     * @param itemStack        要合并的物品（可为null或空）
     * @param attributes       目标属性表
     * @param effectMultiplier 生效倍率（0.0~1.0）
     */
    private static void mergeSlotAttributes(ItemStack itemStack, HashMap<String, Double> attributes, double effectMultiplier) {
        if (itemStack == null || itemStack.isEmpty() || !WeaponModuleHandler.hasBase(itemStack)) {
            return;
        }
        if (effectMultiplier <= 0.0) {
            return;
        }
        double baseDamage = WeaponModuleHandler.getBaseAttribute(itemStack, "damage");
        if (baseDamage <= 0.0) {
            baseDamage = 1.0;
        }
        double finalMultiplier = effectMultiplier * baseDamage;
        HashMap<String, Double> slotAttrs = WeaponModuleHandler.getWeaponAttributes(itemStack);
        for (Map.Entry<String, Double> entry : slotAttrs.entrySet()) {
            attributes.merge(entry.getKey(), entry.getValue() * finalMultiplier, Double::sum);
        }
    }

    // ========== 通用工具方法 / Utility ==========

    /**
     * 获取攻击者的通用攻击伤害源
     *
     * @param attacker 攻击者实体
     * @return 对应类型的伤害源
     */
    private static DamageSource getAttackDamageSource(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return player.damageSources().playerAttack(player);
        } else if (attacker instanceof Mob mob) {
            return mob.damageSources().mobAttack(mob);
        } else {
            return attacker.damageSources().generic();
        }
    }

    /**
     * 查找应该接收伤害数字的玩家
     *
     * @param attacker 攻击者实体
     * @return 应接收伤害数字的玩家，无则返回null
     */
    @Nullable
    static ServerPlayer findDamageDisplayTarget(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        if (attacker instanceof TamableAnimal tamable) {
            LivingEntity owner = tamable.getOwner();
            if (owner instanceof ServerPlayer player && attacker.level() == player.level()) {
                return player;
            }
        }
        return null;
    }

    /**
     * ⭐ 按 UUID 现查在线玩家（替代原来直接持有 ServerPlayer 强引用）
     *
     * @param reference 用于取得服务器实例的参考实体
     * @param uuid      玩家 UUID
     * @return 在线玩家，不在线返回 null
     */
    @Nullable
    private static ServerPlayer resolveDisplayTarget(LivingEntity reference, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (!(reference.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(uuid);
    }

    /**
     * 获取伤害数字的显示前缀
     *
     * @param attacker 攻击者实体
     * @return 显示前缀字符串
     */
    static String getDamageDisplayPrefix(LivingEntity attacker) {
        return (attacker instanceof Player) ? "" : "🎀";
    }

    // ========== ⭐ 近战攻击强度捕获 / Melee Attack Strength Capture ==========

    /**
     * ⭐ 在玩家发起近战攻击的最早时机捕获蓄力比例
     *
     * <p>{@code AttackEntityEvent} 由 {@code ForgeHooks.onPlayerAttackTarget} 在
     * {@code Player.attack()} 开头触发，此时 {@code resetAttackStrengthTicker()} 尚未执行，
     * {@code getAttackStrengthScale} 仍是本次挥击的真实蓄力比例。
     * 等到 {@code LivingHurtEvent} 再读已经被重置，只能拿到接近 0 的值。</p>
     *
     * <p>本方法只记录不修改任何数值，取消事件与否都不影响后续流程；
     * 若攻击被其他模组取消，残留值会在下一次 {@link #onLivingHurt} 中被消费并清除，
     * 最多影响一次判定，不会持续累积。</p>
     *
     * @param evt 玩家攻击实体事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent evt) {
        Player player = evt.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        MELEE_ATTACK_STRENGTH.set(player.getAttackStrengthScale(0.5F));
    }

    /**
     * ⭐ 取出并清除本次近战攻击的蓄力比例
     *
     * <p>无论是否为近战都会执行清除，保证陈旧值最多被消费一次：
     * 若某个模组绕过 {@code Player.attack()} 直接造成近战伤害，
     * 也只会误用一次上一次挥击的比例，不会长期错算。</p>
     *
     * @param isMelee 本次伤害是否为近战（直接实体即攻击者）
     * @return 蓄力比例；非近战、功能关闭或无记录时返回 1.0
     */
    private static float consumeMeleeAttackStrength(boolean isMelee) {
        Float captured = MELEE_ATTACK_STRENGTH.get();
        MELEE_ATTACK_STRENGTH.set(null);
        if (!isMelee || !ENABLE_ATTACK_STRENGTH_SCALING) {
            return 1.0f;
        }
        return captured != null ? captured : 1.0f;
    }

    // ========== 伤害事件 / Damage Events ==========

    /**
     * LivingHurt 事件入口：任何LivingEntity持有开光武器造成伤害时触发武器模组伤害计算
     *
     * <p>⭐ 溅射重入防护：由 {@link #applySplashDamage} 派生的伤害
     * 会带着 {@code playerAttack} 伤害源重新进入本方法并被误判为近战，
     * 见到 {@link #SPLASH_REENTRY} 标记直接放行，交由原版流程结算。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        // ⭐ 溅射派生伤害不做模组结算，避免远程溅射被当成近战重复计算
        if (SPLASH_REENTRY.get()) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (!evt.getEntity().level().isClientSide()) {
            LivingEntity attacker = null;
            boolean isMelee = false;

            if (damageSource.getDirectEntity() instanceof LivingEntity direct) {
                attacker = direct;
                isMelee = true;
            } else if (damageSource.getEntity() instanceof LivingEntity indirect) {
                attacker = indirect;
                isMelee = false;
            }

            if (attacker != null) {
                ItemStack weapon = attacker.getMainHandItem();
                if (!weapon.isEmpty() && WeaponModuleHandler.hasBase(weapon)) {
                    // ⭐ 传入真实蓄力比例，替代原先恒为 1.0f 的死代码
                    processDamage(evt, attacker, weapon, damageSource,
                            consumeMeleeAttackStrength(isMelee), isMelee);
                }
            }
        }
    }

    /**
     * ⭐ 最终伤害回调：读取待显示信息，发送伤害数字到客户端
     *
     * <p>由 {@code MixinLivingEntityFinalDamage} 与 {@code MixinPlayerFinalDamage}
     * 在 {@code ForgeHooks.onLivingDamage} 返回后调用。此时整个
     * {@code LivingDamageEvent} 事件链已跑完（含 l2damagetracker 的莱特兰词缀
     * 减伤 / 免疫，以及本模组 {@code WarframeEffectHandler} 的各类抗性），
     * 紧接着原版就会执行 {@code setHealth}，因此传入的 {@code finalDamage}
     * 就是实体实际掉的血量，不受任何监听器优先级与注册顺序影响。</p>
     *
     * <p>{@code finalDamage} 为 0 时同样会触发本方法，此时只清理队列、不发送数字，
     * 避免条目残留导致后续伤害显示错位。</p>
     *
     * <p><b>仅由 Mixin 在服务端调用</b>，调用方已完成 {@code isClientSide} 判断。</p>
     *
     * @param hurter      受击实体
     * @param source      伤害来源
     * @param finalDamage 事件链处理完毕后的最终扣血量
     */
    public static void onFinalDamage(@Nonnull LivingEntity hurter,
                                     @Nonnull DamageSource source,
                                     float finalDamage) {
        // ⭐ 自检：首次触发时确认 Mixin 已生效，仅打印一次
        if (!finalDamageHookVerified) {
            finalDamageHookVerified = true;
            LogUtil.info("[伤害显示] 最终伤害 Mixin 回调已生效，伤害数字将显示实际扣血值");
        }

        int entityId = hurter.getId();

        Deque<DamageDisplayInfo> queue = pendingDisplays.get(entityId);
        if (queue != null && !queue.isEmpty()) {
            DamageDisplayInfo displayInfo = queue.pollFirst();

            if (queue.isEmpty()) {
                pendingDisplays.remove(entityId);
            }

            ServerPlayer serverPlayer = resolveDisplayTarget(hurter, displayInfo.displayTargetId);
            if (serverPlayer != null && serverPlayer.isAlive()
                    && ModConfig.KUVA_LICH.enableDamageNumbers.get()) {

                if (finalDamage > 0 && !Float.isNaN(finalDamage) && !Float.isInfinite(finalDamage)) {
                    StringBuilder displayText = new StringBuilder();
                    displayText.append(displayInfo.colorCode);
                    displayText.append(displayInfo.prefix);
                    displayText.append(DamagePacket.formatDamage(finalDamage));
                    for (String element : displayInfo.triggeredElements) {
                        displayText.append(WeaponElementSystem.getElementEmoji(element));
                    }
                    Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
                    DamagePacket.sendToPlayer(serverPlayer, displayText.toString(), position);
                }
            }
        } else {
            // 非模组武器的普通伤害显示（仅玩家攻击者）
            Player player = null;

            if (source.getDirectEntity() instanceof Player directPlayer) {
                player = directPlayer;
            } else if (source.getEntity() instanceof Player indirectPlayer) {
                player = indirectPlayer;
            }

            if (player instanceof ServerPlayer serverPlayer && ModConfig.KUVA_LICH.damageDisplay.get()) {
                displayDamage(serverPlayer, hurter, finalDamage);
            }
        }
    }

    // ========== 核心伤害计算 / Core Damage Calculation ==========

    /**
     * 核心伤害处理流程：
     * 基础伤害 → 暴击计算 → 克制倍率 → 元素伤害 → 元素触发 → 最终伤害 → 溅射 → 击杀叠层
     *
     * <p>⭐ modules 只解析一次；元素池只构建一次（{@link WeaponElementSystem.ElementPool}）。</p>
     *
     * <p>⭐ 爆炸半径溅射在远程分支只做半径计算并登记，实际结算延后到
     * {@code evt.setAmount(totalDamage)} 之后，以主目标最终伤害为基数。</p>
     *
     * @param attackStrength 近战蓄力比例（0.0 ~ 1.0），远程恒为 1.0
     */
    private static void processDamage(LivingHurtEvent evt, LivingEntity attacker, ItemStack weapon,
                                      DamageSource damageSource, float attackStrength, boolean isMelee) {
        LivingEntity hurter = evt.getEntity();
        double baseDamage = 1;

        double criticalStrikeProbability = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeProbability") * 100;
        double criticalStrikeMultiplier = WeaponModuleHandler.getBaseAttribute(weapon, "criticalStrikeMultiplier");
        double triggerChance = WeaponModuleHandler.getBaseAttribute(weapon, "triggerChance") * 100;

        // ⭐ 待结算的溅射半径，<=1 表示本次不溅射（实际结算见方法末尾）
        double splashRange = 0;

        if (isMelee) {
            criticalStrikeProbability *= attackStrength;
        }

        // ⭐ modules 只解析一次，元素池构建与后续复用共享
        List<ItemStack> modules = WeaponModuleHandler.getModules(weapon);
        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(attacker, weapon);

        // 合并额外装备槽位的模组属性（副手/护甲/Curios饰品栏）
        mergeAdditionalSlotAttributes(attacker, attributes);

        boolean isPlayer = attacker instanceof Player;
        if (isPlayer) {
            applyKillStackEffects((Player) attacker, weapon, attributes, hurter);
        }

        // ========== 伤害类型加成 ==========
        if (damageSource.getDirectEntity() == attacker) {
            baseDamage += attributes.getOrDefault("meleeDamage", 0.0);

            // ⭐ 暴击率：冲刺时叠加「冲刺近战暴击率」词条
            //    原实现错误地把 dashMeleeCriticalStrikeProbability 加到了暴击伤害上，此处归位
            if (attacker.isSprinting()) {
                criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0)
                        + attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0));
            } else {
                criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
            }

            if (isPlayer && attributes.containsKey("killStackMeleeCriticalMultiplier")) {
                int stacks = KillStackManager.getStacks((Player) attacker, StackType.MELEE_CRIT_MULT);
                double stackValue = attributes.get("killStackMeleeCriticalMultiplier");
                criticalStrikeMultiplier *= (1 + stackValue * stacks);
            }

            // ⭐ 暴击伤害：只受 meleeCriticalStrikeMultiplier 影响，不再混入冲刺暴击率词条
            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
        } else if (damageSource.getEntity() == attacker) {
            baseDamage += attributes.getOrDefault("remoteDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));

            if (damageSource.getDirectEntity() instanceof Arrow) {
                baseDamage += attributes.getOrDefault("arrowDamage", 0.0);
            } else if (damageSource.getDirectEntity() instanceof Projectile) {
                baseDamage += attributes.getOrDefault("projectileDamage", 0.0);
            }

            // 爆炸半径溅射
            double range = 1 + attributes.getOrDefault("bursting_radius", 0.0) * 2;
            if (isPlayer && attributes.containsKey("killStackBurstingRadius")) {
                int stacks = KillStackManager.getStacks((Player) attacker, StackType.BURSTING_RADIUS);
                double stackValue = attributes.get("killStackBurstingRadius");
                range += stackValue * stacks * 2;
            }

            if (WarframeTaczBridge.isBurstRadiusSuppressed()) {
                range = 1;
                WarframeTaczBridge.clearBurstRadiusSuppressed();
            }

            if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                if (range > 1) {
                    // ⭐ 此处只登记半径：evt.getAmount() 此刻仍是未经模组加成的原始伤害，
                    //    直接结算会得到远低于主目标的溅射值，必须等最终伤害算完再执行
                    splashRange = range;
                }
            } else if (range < 0) {
                evt.setCanceled(true);
                return;
            }
        }

        if (damageSource.getMsgId().toLowerCase().contains("magic") || damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            baseDamage += attributes.getOrDefault("magicDamage", 0.0);
        }

        // ========== 元素伤害总量 ==========
        double elementDamage = 0;
        if (KuvaWeaponUtil.hasType(weapon)) {
            elementDamage += WeaponElementSystem.getKuvaWeaponElementDamage(weapon);
        }
        elementDamage += attributes.getOrDefault("fire", 0.0);
        elementDamage += attributes.getOrDefault("ice", 0.0);
        elementDamage += attributes.getOrDefault("poison", 0.0);
        elementDamage += attributes.getOrDefault("electricity", 0.0);
        elementDamage += attributes.getOrDefault("slash", 0.0);
        elementDamage += attributes.getOrDefault("puncture", 0.0);
        elementDamage += attributes.getOrDefault("impact", 0.0);
        elementDamage += attributes.getOrDefault("gas", 0.0);
        elementDamage += attributes.getOrDefault("radiation", 0.0);
        elementDamage += attributes.getOrDefault("magnetic", 0.0);
        elementDamage += attributes.getOrDefault("corrosion", 0.0);
        elementDamage += attributes.getOrDefault("explosion", 0.0);
        elementDamage += attributes.getOrDefault("virus", 0.0);

        // ========== 基础面板倍率 ==========
        if (WeaponModuleHandler.getBaseAttribute(weapon, "damage") > 0) {
            if (WeaponModuleHandler.getBaseAttribute(weapon, "damage") >= 1) {
                baseDamage *= WeaponModuleHandler.getBaseAttribute(weapon, "damage");
                elementDamage *= WeaponModuleHandler.getBaseAttribute(weapon, "damage");
            } else {
                baseDamage *= Math.pow(WeaponModuleHandler.getBaseAttribute(weapon, "damage"), 2);
                elementDamage *= Math.pow(WeaponModuleHandler.getBaseAttribute(weapon, "damage"), 2);
            }
        }

        // ⭐ 真实伤害：在暴击计算前捕获基伤快照
        double trueBulletBaseDamage = baseDamage;

        // ========== 暴击计算 ==========
        String colorCode;
        if (criticalStrikeProbability > 300) {
            baseDamage *= criticalStrikeMultiplier * 3;
            colorCode = "§c";
        } else if (criticalStrikeProbability > 200) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 200)) {
                baseDamage *= criticalStrikeMultiplier * 3;
                colorCode = "§c";
            } else {
                baseDamage *= criticalStrikeMultiplier * 2;
                colorCode = "§6";
            }
        } else if (criticalStrikeProbability > 100) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 100)) {
                baseDamage *= criticalStrikeMultiplier * 2;
                colorCode = "§6";
            } else {
                baseDamage *= criticalStrikeMultiplier;
                colorCode = "§e";
            }
        } else {
            if (RandomUtil.percentageChance(criticalStrikeProbability)) {
                baseDamage *= criticalStrikeMultiplier;
                colorCode = "§e";
            } else {
                if (hurter.getAbsorptionAmount() > 0) {
                    colorCode = "§b";
                } else {
                    colorCode = "§f";
                }
                baseDamage += attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0);
            }
        }

        // ========== 克制倍率 ==========
        double baneMultiplier = 1.0;
        if (hurter.getMobType().equals(MobType.UNDEFINED)) {
            baneMultiplier += attributes.getOrDefault("bane_of_undefined", 0.0);
        } else if (hurter.getMobType().equals(MobType.UNDEAD)) {
            baneMultiplier += attributes.getOrDefault("bane_of_undead", 0.0);
        } else if (hurter.getMobType().equals(MobType.ARTHROPOD)) {
            baneMultiplier += attributes.getOrDefault("bane_of_arthropod", 0.0);
        } else {
            baneMultiplier += attributes.getOrDefault("bane_of_illager", 0.0);
        }

        // ========== 最终伤害合算 ==========
        float originalDamage = evt.getAmount();
        double physicalDamage = originalDamage * baseDamage * baneMultiplier;
        double elementalDamage = originalDamage * elementDamage * baneMultiplier;
        float totalDamage = (float) (physicalDamage + elementalDamage);
        double coreDamage = physicalDamage;

        // ========== 元素触发 ==========
        double triggerTime = 1 + attributes.getOrDefault("triggerTime", 0.0);

        if (attacker.isSprinting()) {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0) + attributes.getOrDefault("dashTriggerChance", 0.0));
        } else {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0));
        }

        if (isPlayer && attributes.containsKey("killStackTriggerChance")) {
            int stacks = KillStackManager.getStacks((Player) attacker, StackType.TRIGGER_CHANCE);
            double stackValue = attributes.get("killStackTriggerChance");
            triggerChance *= (1 + stackValue * stacks);
        }

        // ⭐ 元素触发硬上限：所有加成乘算完成后，强制截断
        triggerChance = Math.min(triggerChance, MAX_ELEMENT_TRIGGER_COUNT * 100.0);

        Set<String> triggeredElements = new LinkedHashSet<>();

        // ⭐ 元素池只构建一次；池为空时整段触发逻辑直接跳过
        if (triggerChance > 0) {
            WeaponElementSystem.ElementPool elementPool = WeaponElementSystem.buildElementPool(weapon, modules);
            if (!elementPool.isEmpty()) {
                if (triggerChance > 100) {
                    int number = (int) triggerChance / 100;
                    for (int i = 0; i < number; i++) {
                        String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon,
                                elementPool, triggerTime, coreDamage, attributes, baneMultiplier);
                        if (element != null) triggeredElements.add(element);
                    }
                    if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                        String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon,
                                elementPool, triggerTime, coreDamage, attributes, baneMultiplier);
                        if (element != null) triggeredElements.add(element);
                    }
                } else if (RandomUtil.percentageChance(triggerChance)) {
                    String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon,
                            elementPool, triggerTime, coreDamage, attributes, baneMultiplier);
                    if (element != null) triggeredElements.add(element);
                }
            }
        }

        totalDamage = Math.max(totalDamage, 0);
        evt.setAmount(totalDamage);

        // ⭐ 提前取得伤害数字接收者，溅射与主目标入队共用同一个引用
        ServerPlayer displayTarget = findDamageDisplayTarget(attacker);

        // ⭐ 爆炸半径溅射：以主目标最终伤害为基数，自动继承远程面板与暴击结果
        applySplashDamage(attacker, hurter, splashRange, totalDamage, colorCode, displayTarget);

        // ⭐ 真实伤害：普通伤害结算后额外结算真伤
        applyTrueBulletDamage(attacker, hurter, damageSource, attributes, originalDamage, trueBulletBaseDamage, baneMultiplier);

        // ⭐ 净化驱散：攻击命中时按概率移除目标增益效果
        applyPurgeBuff(hurter, attributes);

        // ========== 武器击杀叠层累加（击杀检测）==========
        if (isPlayer && hurter.getHealth() - totalDamage <= 0) {
            addWeaponKillStacks((Player) attacker, attributes);
        }

        // ⭐ 处决：处决阈值 + 秒杀概率，延迟1tick执行
        applyExecuteEffects(attacker, hurter, attributes, isPlayer, totalDamage);

        // ⭐ 只入队玩家 UUID，避免持有 ServerPlayer 强引用
        if (displayTarget != null) {
            String prefix = getDamageDisplayPrefix(attacker);
            pendingDisplays.computeIfAbsent(hurter.getId(), k -> new ArrayDeque<>())
                    .addLast(new DamageDisplayInfo(colorCode, triggeredElements, prefix,
                            displayTarget.getUUID(), serverTick));
        }
    }

    // ========== ⭐ 爆炸半径溅射 / Bursting Radius Splash ==========

    /**
     * ⭐ 爆炸半径溅射结算
     *
     * <p>以主目标的<b>最终伤害</b>（{@code evt.setAmount} 写入的值）乘
     * {@link #SPLASH_DAMAGE_RATIO} 作为溅射伤害，因此自动继承远程面板加成、
     * 远程暴击结果与克制倍率，无需重复计算，也不会像原实现那样取到未加成的原始伤害。</p>
     *
     * <p>溅射伤害源仍是 {@code playerAttack}，其 {@code directEntity} 是攻击者本人，
     * 不加防护会被 {@link #onLivingHurt} 当成近战攻击再走一遍完整模组结算，
     * 故整段以 {@link #SPLASH_REENTRY} 包裹；try/finally 保证异常时标记必被复位，
     * 否则该线程后续所有攻击都会被跳过结算。</p>
     *
     * <p>行为约定：溅射<b>只结算伤害</b>，不独立掷元素触发、不累加武器击杀叠层、
     * 不做处决判定，避免一次群体命中把叠层与元素触发次数乘上目标数。
     * 伤害数字主动入队并复用主目标的暴击颜色码，故溅射数字与主目标同色；
     * 因不触发元素，数字后不带元素图标。</p>
     *
     * <p>入队必须在 {@code hurt} 之前：{@code hurt} 会同步跑完事件链并回调
     * {@link #onFinalDamage}，届时直接消费刚入队的条目。
     * 若溅射伤害被其他模组完全取消导致回调未触发，残留条目由
     * {@link #onServerTick} 的定期清扫兜底。</p>
     *
     * @param attacker      攻击者
     * @param hurter        主目标（溅射中心，自身不重复受伤）
     * @param range         溅射半径，{@code <= 1} 表示本次不溅射
     * @param mainDamage    主目标最终伤害
     * @param colorCode     主目标暴击颜色码，溅射数字复用
     * @param displayTarget 伤害数字接收者，可为 null（非玩家攻击者）
     */
    private static void applySplashDamage(LivingEntity attacker, LivingEntity hurter, double range,
                                          float mainDamage, String colorCode,
                                          @Nullable ServerPlayer displayTarget) {
        if (range <= 1 || mainDamage <= 0) {
            return;
        }

        float splashDamage = mainDamage * SPLASH_DAMAGE_RATIO;
        if (splashDamage <= 0 || Float.isNaN(splashDamage) || Float.isInfinite(splashDamage)) {
            return;
        }

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                e -> !e.equals(hurter) && !e.equals(attacker));
        if (entities.isEmpty()) {
            return;
        }

        DamageSource splashSource = getAttackDamageSource(attacker);
        String prefix = getDamageDisplayPrefix(attacker);
        boolean showNumbers = displayTarget != null && ModConfig.KUVA_LICH.enableDamageNumbers.get();
        UUID displayTargetId = showNumbers ? displayTarget.getUUID() : null;

        SPLASH_REENTRY.set(Boolean.TRUE);
        try {
            for (LivingEntity entity : entities) {
                if (showNumbers) {
                    // 溅射不触发元素，故元素集合为空；颜色码沿用主目标的暴击结果
                    pendingDisplays.computeIfAbsent(entity.getId(), k -> new ArrayDeque<>())
                            .addLast(new DamageDisplayInfo(colorCode, Collections.emptySet(),
                                    prefix, displayTargetId, serverTick));
                }
                entity.hurt(splashSource, splashDamage);
            }
        } finally {
            // 异常时也必须复位，否则该线程后续所有攻击都会被跳过结算
            SPLASH_REENTRY.set(Boolean.FALSE);
        }
    }

    /**
     * 普通伤害显示（无模组武器的默认白色伤害数字，仅玩家可见）
     *
     * @param serverPlayer 接收伤害数字的玩家
     * @param hurter       受击实体
     * @param damage       最终扣血量
     */
    private static void displayDamage(ServerPlayer serverPlayer, LivingEntity hurter, float damage) {
        if (damage > 0 && !Float.isNaN(damage) && !Float.isInfinite(damage)) {
            String displayText = "§f" + DamagePacket.formatDamage(damage);
            Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
            DamagePacket.sendToPlayer(serverPlayer, displayText, position);
        }
    }

    // ========== 击杀叠层效果 / Kill Stack Effects ==========

    /**
     * 将击杀叠层的增益应用到武器属性上（仅对玩家生效）
     */
    private static void applyKillStackEffects(Player player, ItemStack weapon,
                                              HashMap<String, Double> attributes,
                                              LivingEntity target) {
        if (!WeaponModuleHandler.hasBase(weapon)) return;

        if (attributes.containsKey("killStackBaseDamage")) {
            int stacks = KillStackManager.getStacks(player, StackType.BASE_DAMAGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackBaseDamage");
                int debuffCount = 0;
                for (MobEffectInstance effect : target.getActiveEffects()) {
                    if (effect.getEffect().getCategory().equals(MobEffectCategory.HARMFUL)) {
                        debuffCount++;
                    }
                }
                if (debuffCount > 0) {
                    double bonusDamage = stackValue * stacks * debuffCount;
                    attributes.put("meleeDamage", attributes.getOrDefault("meleeDamage", 0.0) + bonusDamage);
                    attributes.put("remoteDamage", attributes.getOrDefault("remoteDamage", 0.0) + bonusDamage);
                }
            }
        }

        if (attributes.containsKey("killStackMultishot")) {
            int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackMultishot");
                attributes.put("multishot", attributes.getOrDefault("multishot", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackSpeed")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackSpeed");
                attributes.put("attackSpeed", attributes.getOrDefault("attackSpeed", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackRange")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_RANGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackRange");
                attributes.put("attackRange", attributes.getOrDefault("attackRange", 0.0) + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFiringRate");
                attributes.put("firing_rate", attributes.getOrDefault("firing_rate", 0.0) + stackValue * stacks);
            }
        }
    }

    // ========== 武器击杀叠层累加 / Weapon Kill Stack Addition ==========

    /**
     * 击杀时为玩家累加武器击杀叠层。
     *
     * @param player     击杀者
     * @param attributes 当前武器的运行时属性（已含模组词条的合并值）
     */
    private static void addWeaponKillStacks(Player player, HashMap<String, Double> attributes) {
        if (attributes.containsKey("killStackBaseDamage")) {
            KillStackManager.addStack(player, StackType.BASE_DAMAGE);
        }
        if (attributes.containsKey("killStackMultishot")) {
            KillStackManager.addStack(player, StackType.MULTISHOT);
        }
        if (attributes.containsKey("killStackMeleeCriticalMultiplier")) {
            KillStackManager.addStack(player, StackType.MELEE_CRIT_MULT);
        }
        if (attributes.containsKey("killStackTriggerChance")) {
            KillStackManager.addStack(player, StackType.TRIGGER_CHANCE);
        }
        if (attributes.containsKey("killStackAttackRange")) {
            KillStackManager.addStack(player, StackType.ATTACK_RANGE);
        }
        if (attributes.containsKey("killStackAttackSpeed")) {
            KillStackManager.addStack(player, StackType.ATTACK_SPEED);
        }
        if (attributes.containsKey("killStackBurstingRadius")) {
            KillStackManager.addStack(player, StackType.BURSTING_RADIUS);
        }
        if (attributes.containsKey("killStackFiringRate")) {
            KillStackManager.addStack(player, StackType.FIRING_RATE);
        }
    }

    // ========== 真实伤害 / 净化驱散 / 处决 ==========

    /**
     * 真实伤害（true_bullet）结算。
     *
     * @param attacker             攻击者
     * @param hurter               受击者
     * @param damageSource         伤害来源
     * @param attributes           武器运行时属性
     * @param originalDamage       本次攻击的原始伤害
     * @param trueBulletBaseDamage 暴击前的基伤快照
     * @param baneMultiplier       克制倍率
     */
    private static void applyTrueBulletDamage(LivingEntity attacker, LivingEntity hurter,
                                              DamageSource damageSource,
                                              HashMap<String, Double> attributes,
                                              float originalDamage, double trueBulletBaseDamage,
                                              double baneMultiplier) {
        double trueBulletValue = attributes.getOrDefault("true_bullet", 0.0);
        if (trueBulletValue <= 0) { return; }

        // ⭐ 真实子弹仅对 TACZ 子弹命中生效
        if (!isTaczBullet(damageSource)) { return; }

        float trueDamage = (float) (originalDamage * trueBulletBaseDamage * baneMultiplier * trueBulletValue);
        if (trueDamage <= 0 || Float.isNaN(trueDamage) || Float.isInfinite(trueDamage)) { return; }

        final float finalTrueDamage = trueDamage;
        final LivingEntity trueAttacker = attacker;
        final DamageSource trueSource = getAttackDamageSource(attacker);

        new SynchronizationTask(1, 1) {
            @Override
            public void run() {
                this.cancel();
                if (hurter.isDeadOrDying()) { return; }
                if (hurter.getHealth() - finalTrueDamage > 0.01f) {
                    EntityLivingUtil.damageHealthDirectly(hurter, finalTrueDamage);
                } else {
                    EntityLivingUtil.kill(hurter, trueSource);
                }
                ServerPlayer target = findDamageDisplayTarget(trueAttacker);
                if (target != null && target.isAlive() && ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
                    String prefix = getDamageDisplayPrefix(trueAttacker);
                    String displayText = "\u00a75" + prefix + DamagePacket.formatDamage(finalTrueDamage) + getTrueBulletEmoji();
                    Vec3 position = WeaponElementSystem.getRandomDamagePosition(hurter);
                    DamagePacket.sendToPlayer(target, displayText, position);
                }
            }
        }.start();
    }

    /**
     * 判断本次伤害是否由 TACZ 子弹直接造成。
     *
     * @param source 伤害源
     * @return 直接实体为 TACZ 子弹时返回 true
     */
    private static boolean isTaczBullet(DamageSource source) {
        if (source == null || source.getDirectEntity() == null) { return false; }
        return "com.tacz.guns.entity.EntityKineticBullet".equals(source.getDirectEntity().getClass().getName());
    }

    /**
     * 获取真实伤害的伤害数字后缀
     *
     * @return 带颜色代码的匕首符号
     */
    private static String getTrueBulletEmoji() {
        return "\u00a75\ud83d\udde1"; // §5 🗡
    }

    /**
     * 净化驱散（purge_buff）结算。
     *
     * @param hurter     受击者
     * @param attributes 武器运行时属性
     */
    private static void applyPurgeBuff(LivingEntity hurter, HashMap<String, Double> attributes) {
        double purgeValue = attributes.getOrDefault("purge_buff", 0.0);
        if (purgeValue <= 0) { return; }

        double chance = purgeValue * 100.0;
        int removeCount = (int) (chance / 100.0);
        double fraction = chance - removeCount * 100.0;
        if (RandomUtil.percentageChance(fraction)) { removeCount++; }
        if (removeCount <= 0) { return; }

        List<MobEffect> beneficial = new ArrayList<>();
        for (MobEffectInstance instance : hurter.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) {
                beneficial.add(instance.getEffect());
            }
        }
        if (beneficial.isEmpty()) { return; }

        Collections.shuffle(beneficial);
        int actual = Math.min(removeCount, beneficial.size());
        for (int i = 0; i < actual; i++) {
            hurter.removeEffect(beneficial.get(i));
        }
    }

    /**
     * 处决结算（处决阈值 execute_threshold + 秒杀概率 execute_chance）。
     *
     * @param attacker    攻击者
     * @param hurter      受击者
     * @param attributes  武器运行时属性
     * @param isPlayer    攻击者是否为玩家
     * @param totalDamage 本次攻击的最终伤害（用于叠层去重）
     */
    private static void applyExecuteEffects(LivingEntity attacker, LivingEntity hurter,
                                            HashMap<String, Double> attributes, boolean isPlayer, float totalDamage) {
        double thresholdValue = attributes.getOrDefault("execute_threshold", 0.0);
        double chanceValue = attributes.getOrDefault("execute_chance", 0.0);
        if (thresholdValue <= 0 && chanceValue <= 0) { return; }

        boolean shouldExecute = false;
        boolean byThreshold = false;
        boolean byChance = false;

        if (thresholdValue > 0) {
            float maxHealth = hurter.getMaxHealth();
            if (maxHealth > 0 && hurter.getHealth() > 0 && hurter.getHealth() <= maxHealth * thresholdValue) {
                shouldExecute = true;
                byThreshold = true;
            }
        }
        if (!shouldExecute && chanceValue > 0) {
            if (RandomUtil.percentageChance(chanceValue * 100.0)) {
                shouldExecute = true;
                byChance = true;
            }
        }
        if (!shouldExecute) { return; }

        boolean killedByNormalHit = (hurter.getHealth() - totalDamage) <= 0;
        if (!killedByNormalHit && isPlayer && attacker instanceof Player) {
            addWeaponKillStacks((Player) attacker, attributes);
        }

        spawnExecuteParticles(hurter, byThreshold, byChance);

        final DamageSource exSource = getAttackDamageSource(attacker);

        new SynchronizationTask(1, 1) {
            @Override
            public void run() {
                this.cancel();
                if (hurter.isDeadOrDying()) { return; }
                EntityLivingUtil.kill(hurter, exSource);
            }
        }.start();
    }

    /**
     * 播放处决/秒杀特效（服务端粒子，单次少量）
     *
     * @param target      被处决目标
     * @param byThreshold 是否由处决阈值（收割）触发
     * @param byChance    是否由秒杀概率（斩杀）触发
     */
    private static void spawnExecuteParticles(LivingEntity target, boolean byThreshold, boolean byChance) {
        if (!(target.level() instanceof ServerLevel server)) { return; }

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double rx = target.getBbWidth() * 0.5 + 0.1;
        double ry = target.getBbHeight() * 0.4;

        if (byChance) {
            server.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 14, rx, ry, rx, 0.1);
            server.sendParticles(ParticleTypes.CRIT, x, y, z, 10, rx, ry, rx, 0.25);
        } else if (byThreshold) {
            server.sendParticles(ParticleTypes.SOUL, x, y, z, 10, rx, ry, rx, 0.02);
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 8, rx, ry, rx, 0.1);
        }
    }

    // ========== 攻击速度 & 攻击距离 Tick ==========

    /**
     * 每秒检测一次武器攻击速度和攻击距离属性，通过动态属性系统应用
     */
    @SubscribeEvent
    public static void onLivingTick(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity.level().isClientSide()) return;
        if (!entity.isAlive()) return;
        if (entity.level().getGameTime() % 20 != 0) return;

        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        mergeAdditionalSlotAttributes(entity, attributes);

        boolean isPlayer = entity instanceof Player;

        // ========== 攻击速度 ==========
        if (isPlayer && attributes.containsKey("killStackAttackSpeed")) {
            int stacks = KillStackManager.getStacks((Player) entity, StackType.ATTACK_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackSpeed");
                attributes.put("attackSpeed", attributes.getOrDefault("attackSpeed", 0.0) + stackValue * stacks);
            }
        }

        double attackSpeed = attributes.getOrDefault("attackSpeed", 0.0);
        if (attackSpeed >= 0.1) {
            int level = (int) (attackSpeed / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.ATTACK_SPEED.createInstance(30, level));
        } else if (attackSpeed <= -0.1) {
            int level = (int) (-attackSpeed / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.NEGATIVE_ATTACK_SPEED.createInstance(30, level));
        }

        // ========== 攻击距离 ==========
        double attackRange = attributes.getOrDefault("attackRange", 0.0);
        if (entity.isSprinting()) {
            attackRange += attributes.getOrDefault("dashAttackRange", 0.0);
        }
        if (isPlayer && attributes.containsKey("killStackAttackRange")) {
            int rangeStacks = KillStackManager.getStacks((Player) entity, StackType.ATTACK_RANGE);
            if (rangeStacks > 0) {
                double rangeStackValue = attributes.get("killStackAttackRange");
                attackRange += rangeStackValue * rangeStacks;
            }
        }
        if (attackRange >= 0.1) {
            int rangeLevel = (int) (attackRange / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.ATTACK_RANGE.createInstance(30, rangeLevel));
        } else if (attackRange <= -0.1) {
            int rangeLevel = (int) (-attackRange / 0.1) - 1;
            DynamicAttributeManager.apply(entity, DynamicAttributes.NEGATIVE_ATTACK_RANGE.createInstance(30, rangeLevel));
        }
    }

    // ========== 弓箭 / 射速事件 / Bow & Firing Rate ==========

    /**
     * 使用物品Tick事件：处理射速减速
     */
    @SubscribeEvent
    public static void onLivingEntityUseItemTick(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();
        ItemStack usingItem = evt.getItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        mergeAdditionalSlotAttributes(entity, attributes);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (entity instanceof Player player && attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) { firingRate *= 2.0; }
        if (Math.abs(firingRate) < 0.001) return;
        if (firingRate <= -1.0) { entity.stopUsingItem(); }
        else if (firingRate < 0) { if (RandomUtil.percentageChance(Math.abs(firingRate) * 100)) { evt.setCanceled(true); } }
    }

    /**
     * 生物Tick事件：处理射速加速
     */
    @SubscribeEvent
    public static void onLivingTickForFiringRate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (!entity.isUsingItem()) return;
        ItemStack usingItem = entity.getUseItem();
        if (usingItem.isEmpty()) return;
        ItemStack weapon = entity.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) return;

        HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(entity, weapon);
        mergeAdditionalSlotAttributes(entity, attributes);

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (entity instanceof Player player && attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) { firingRate *= 2.0; }
        if (firingRate <= 0) return;
        int extraUpdates = (int) firingRate;
        for (int i = 0; i < extraUpdates; i++) { EntityLivingUtil.updateHeld(entity); }
        double fractionalPart = firingRate - extraUpdates;
        if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) { EntityLivingUtil.updateHeld(entity); }
    }

    /**
     * 弓箭释放事件：处理多重射击（仅Player触发）
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        Player player = evt.getEntity();
        if (!evt.getEntity().level().isClientSide()) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && WeaponModuleHandler.hasBase(bow)) {
                HashMap<String, Double> attributes = WeaponModuleHandler.getCachedWeaponAttributes(player, bow);
                mergeAdditionalSlotAttributes(player, attributes);

                double multishot = attributes.getOrDefault("multishot", 0.0);
                if (attributes.containsKey("killStackMultishot")) {
                    int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
                    double stackValue = attributes.get("killStackMultishot");
                    multishot += stackValue * stacks;
                }
                if (multishot > 0) {
                    int charge = evt.getCharge();
                    float velocity = getBowVelocity(charge);
                    if (velocity < 0.1f) return;
                    if (multishot > 1) {
                        int number = (int) multishot;
                        for (int i = 0; i < number; i++) { fireArrow(player, player.level(), velocity, bow, true); }
                        if (RandomUtil.percentageChance((multishot - number) * 100)) { fireArrow(player, player.level(), velocity, bow, true); }
                    } else {
                        if (RandomUtil.percentageChance(multishot * 100)) { fireArrow(player, player.level(), velocity, bow, true); }
                    }
                } else if (multishot < 0 && multishot > -1) {
                    if (RandomUtil.percentageChance(Math.abs(multishot) * 100)) { evt.setCanceled(true); }
                } else if (multishot <= -1) { evt.setCanceled(true); }
            }
        }
    }

    /**
     * 计算弓箭蓄力速度
     */
    private static float getBowVelocity(int charge) {
        float f = (float) charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) f = 1.0F;
        return f;
    }

    /**
     * 发射一支额外箭矢（多重射击用）
     */
    private static void fireArrow(Player player, Level level, float velocity, ItemStack bow, boolean infiniteArrows) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        ArrowItem arrowItem = (arrowStack.getItem() instanceof ArrowItem) ? (ArrowItem) arrowStack.getItem() : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, arrowStack, player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity * 3.0F, 5.0F);
        arrow.addTag("multishot");
        if (velocity == 1.0F) arrow.setCritArrow(true);
        applyBowEnchantments(arrow, bow);
        if (infiniteArrows) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        level.addFreshEntity(arrow);
    }

    /**
     * 将弓的附魔效果应用到额外箭矢上
     */
    private static void applyBowEnchantments(AbstractArrow arrow, ItemStack bow) {
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        int knockback = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (knockback > 0) arrow.setKnockback(knockback);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) { arrow.setSecondsOnFire(100); }
    }
}
