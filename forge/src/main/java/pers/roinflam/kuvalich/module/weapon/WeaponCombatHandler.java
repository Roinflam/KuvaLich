package pers.roinflam.kuvalich.module.weapon;

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
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributes;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.utils.SynchronizationTask;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.LivingEntityUtil;
import pers.roinflam.kuvalich.utils.EntityUtil;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器战斗事件处理器
 * 负责所有伤害事件、弓箭多重射击、射速加速、攻击速度/攻击距离tick
 *
 * <p>⭐ 本次改动（魔法伤害识别与分支）：</p>
 *
 * <p><b>一、魔法判定交给 {@link MagicDamageClassifier}。</b><br>
 * 原实现只认「message_id 含 magic」或 {@code witch_resistant_to} tag，
 * Ars Nouveau（message_id 是 player/fire/freeze）、Goety 大部分法术（goety.xxx）
 * 以及 ISB 的火场/毒云等残留伤害全都认不出来。现改为原版规则 + 第三方规则
 * （{@code forge:is_magic} tag、伤害类型命名空间、白/黑名单，均走配置），
 * 且召唤物替主人挥砍的命中一律不算魔法。不引入任何模组依赖。</p>
 *
 * <p><b>二、结算结构不变：近战/远程是基础分支，魔法是独立叠加层。</b><br>
 * 直接实体是攻击者本人 → 近战分支；否则归属实体是攻击者 → 远程分支（含箭矢/弹射物细分与溅射）。
 * 魔法只决定要不要在此之上再加 {@code magicDamage}，与基础分支互不排斥：
 * 施法者亲手打出的射线/触碰法术 = 近战 + 魔法，弹射物法术 = 远程 + 弹射物 + 魔法。</p>
 *
 * <p>⭐ 此前改动（伤害数字，均为显示层，不改变任何伤害数值）：</p>
 *
 * <p><b>一、登记与配对迁至 {@link DamageDisplayTracker}。</b><br>
 * 原实现按「先进先出」配对：受击事件里入队、最终伤害回调里取队首。
 * 只要某一下入了队却没走到扣血（溅射目标处于受击无敌、举盾格挡、被其他模组或插件取消等），
 * 条目就会残留，之后每一发都拿到上一发的颜色与元素图标，甚至把数字发给别的玩家。<br>
 * 修复：登记记录本次的 {@link DamageSource} 对象，回调只认同一个对象；
 * 溅射改用 {@link DamageDisplayTracker#hurtWithDisplay}，{@code hurt} 返回后未被消费的登记立即撤回；
 * 最终伤害 ≤ 0 时不再登记；未消费登记每 tick 末统一清空。</p>
 *
 * <p><b>二、护盾部分计入数字。</b><br>
 * 登记时记下目标的吸收量，回调时补回护盾吃掉的部分，并在数字后追加护盾图标。</p>
 *
 * <p><b>三、真伤数字改为实测。</b><br>
 * 原实现直接显示预算值；现改为「扣之前血量 − 扣之后血量」，
 * Boss 锁血、伤害上限等拦截扣血时如实变小或不显示；目标因此死亡时按溢出规则显示完整预算值。</p>
 *
 * <p><b>四、普通白字（{@code damageDisplay}）也走登记</b>，同样计入护盾。</p>
 *
 * <p>⭐ 此前改动（三项，均为近战 / 远程词条串用修复）：</p>
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
 * 溅射目标的伤害数字会主动登记并复用主目标的暴击颜色码，因此暴击色保留；
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
 * <p>⭐ 此前改动：伤害数字的取值点改为 {@code MixinForgeHooksFinalDamage}
 * 捕获 {@code ForgeHooks.onLivingDamage} 的返回值（整个 {@code LivingDamageEvent} 事件链跑完后的结果），
 * 不受任何监听器优先级、注册顺序影响。登记、配对与发包现已全部由 {@link DamageDisplayTracker} 负责。</p>
 *
 * <p>⭐ 此前修复（两项）：</p>
 *
 * <p><b>一、伤害显示队列内存泄漏。</b><br>
 * 已被 {@link DamageDisplayTracker} 的机制取代：登记只存玩家 UUID，未消费登记每 tick 末统一清空。</p>
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

    // ========== 多槽位属性缓存 / Multi-Slot Attribute Cache ==========

    /**
     * 额外槽位属性缓存（已含倍率预计算）
     * <p>key = 实体UUID，value = 所有额外槽位模组属性合并后的结果（倍率已乘入）。
     * 每 EXTRA_SLOT_CACHE_INTERVAL tick 清空一次。</p>
     */
    private static final Map<UUID, HashMap<String, Double>> EXTRA_SLOT_CACHE_SERVER = new ConcurrentHashMap<>();

    /**
     * 额外槽位属性缓存 · 客户端侧
     *
     * <p>⭐ 必须与服务端分开：单人游戏里逻辑服务端玩家与客户端玩家<b>UUID 相同</b>，
     * 但持有的是两份不同的 {@code ItemStack} 实例、两份不同的 gameTime。
     * 共用一张表时 {@code computeIfAbsent} 谁先跑谁定胜负，另一侧直接吃到对面的计算结果。
     * 这个缺陷本来就存在（Tooltip 早就在客户端调它），但现在 TACZ 桥的 12 个 getter
     * 也走这条路、<b>HUD 每帧都会触发</b>，撞面从「打开 tooltip 的瞬间」扩大到「一直」，
     * 表现为单人游戏里弹匣数 / 装填速度间歇性地在两个值之间跳。</p>
     */
    private static final Map<UUID, HashMap<String, Double>> EXTRA_SLOT_CACHE_CLIENT = new ConcurrentHashMap<>();

    /**
     * 上次清空额外槽位缓存的 gameTick（服务端侧 / 客户端侧各一份）
     *
     * <p>⭐ volatile：本字段被服务端主线程（{@link #onLivingHurt}）与客户端渲染线程
     * （Tooltip 经 {@code ExtraSlotTooltipHelper}、TACZ 面板经 {@code WarframeTaczBridge}）读写。
     * long 的非 volatile 读写在 32 位语义下允许撕裂，且无 volatile 时一侧的写对另一侧可能永久不可见，
     * 会让缓存要么永不过期（面板长期显示旧值）、要么每次都判定过期（每帧全量重算）。</p>
     */
    private static volatile long extraSlotCacheTickServer = -1;
    private static volatile long extraSlotCacheTickClient = -1;

    /** 额外槽位缓存刷新间隔（tick）：40 tick = 2秒 */
    private static final long EXTRA_SLOT_CACHE_INTERVAL = 40L;

    // ========== 元素触发硬上限 / Element Trigger Hard Cap ==========

    /**
     * ⭐ 单次命中的元素触发次数上限（原 {@code MAX_ELEMENT_TRIGGER_COUNT} 的「单次」语义）
     *
     * <p>旧实现只有一个 20 的常量，同时承担「单次命中上限」与「事实上的全局上限」两个职责，
     * 结果两边都不合格：单次命中允许 20 次触发，而切割与带盾时的毒素走
     * {@code damageHealthDirectly} <b>绕过无敌帧</b>，20 份 DOT 是真·线性叠加，直接秒杀；
     * 群体命中时又完全没有全局约束，10 个目标就是 200 次触发。</p>
     *
     * <p>现拆成两个常量：本常量管单次命中，{@link #MAX_ELEMENT_TRIGGER_PER_TICK} 管单 tick 全局。
     * 5 的取值可后续提为配置项。</p>
     *
     * <p>⭐ public：ALT 实时视图要用它把触发几率的分解结果钳到同一个上限，
     * 两边各写一个 5 就又是一次「面板与实战对不上」的分叉。</p>
     */
    public static final int MAX_ELEMENT_TRIGGER_PER_HIT = 5;

    /**
     * ⭐ 单 tick 内全局元素触发次数上限（跨所有攻击者与所有目标）
     *
     * <p>专治群体命中：横扫 / 霰弹一次打十几只怪时，单次命中上限管不住总量。
     * 100 的取值可后续提为配置项。</p>
     */
    private static final int MAX_ELEMENT_TRIGGER_PER_TICK = 100;

    /** 当前全局触发预算对应的 gameTime，与 {@link #elementTriggerBudgetUsed} 成对使用 */
    private static volatile long elementTriggerBudgetTick = Long.MIN_VALUE;

    /** 当前 tick 已消耗的全局触发次数 */
    private static volatile int elementTriggerBudgetUsed = 0;

    /**
     * ⭐ 向单 tick 全局触发预算申请触发次数
     *
     * <p>不另起 tick 事件监听：以 {@code gameTime} 变化作为「新的一 tick」的判据，
     * 惰性重置计数器即可。本方法只在服务端伤害结算线程调用，字段加 volatile 只为
     * 与可能的跨线程读保持可见性，计数本身不要求严格原子（超发一两次无害）。</p>
     *
     * @param level 目标所在世界，用于取 gameTime
     * @param want  本次命中想要的触发次数
     * @return 实际获批的次数，预算耗尽时返回 0
     */
    private static int acquireElementTriggerBudget(@Nonnull Level level, int want) {
        if (want <= 0) {
            return 0;
        }
        long now = level.getGameTime();
        if (now != elementTriggerBudgetTick) {
            elementTriggerBudgetTick = now;
            elementTriggerBudgetUsed = 0;
        }
        int remaining = MAX_ELEMENT_TRIGGER_PER_TICK - elementTriggerBudgetUsed;
        if (remaining <= 0) {
            return 0;
        }
        int granted = Math.min(want, remaining);
        elementTriggerBudgetUsed += granted;
        return granted;
    }

    // ========== 额外槽位属性 / Extra Slot Attributes ==========

    /**
     * 获取缓存的额外槽位属性（含倍率已预计算）
     *
     * @param entity 实体
     * @return 额外槽位属性表（只读使用，不要修改）
     */
    static HashMap<String, Double> getCachedExtraSlotAttributes(LivingEntity entity) {
        boolean clientSide = entity.level().isClientSide();
        long currentTick = entity.level().getGameTime();

        // ⭐ 两侧各用一张表、各记一个 tick：单人游戏里两边 UUID 相同但数据源不同
        Map<UUID, HashMap<String, Double>> cache = clientSide ? EXTRA_SLOT_CACHE_CLIENT : EXTRA_SLOT_CACHE_SERVER;
        long lastTick = clientSide ? extraSlotCacheTickClient : extraSlotCacheTickServer;

        if (Math.abs(currentTick - lastTick) >= EXTRA_SLOT_CACHE_INTERVAL) {
            cache.clear();
            if (clientSide) {
                extraSlotCacheTickClient = currentTick;
            } else {
                extraSlotCacheTickServer = currentTick;
            }
        }
        return cache.computeIfAbsent(entity.getUUID(), uuid -> computeExtraSlotAttributes(entity));
    }

    /**
     * ⭐ 额外槽位属性的<b>跨包</b>公开入口
     *
     * <p>{@link #getCachedExtraSlotAttributes} 是包级私有，同包的
     * {@code ExtraSlotTooltipHelper} 能直接用，但 {@code compat.tacz.WarframeTaczBridge}
     * 不在本包内。TACZ 枪械的 12 个属性读取方法此前只读枪本体，
     * 而枪的 tooltip 面板已经把副手 / 护甲 / 饰品的加成算进去了 ——
     * 于是「面板数字涨了、实际开枪毫无变化」。本方法就是给桥接类补上这条读取路径，
     * 让面板与实际生效值取自同一份数据。</p>
     *
     * <p><b>返回的是缓存实例本身，调用方只读，不要修改。</b>需要合并时请先拷贝。</p>
     *
     * @param entity 实体，可为 null
     * @return 额外槽位属性表；entity 为 null 时返回空表
     */
    @Nonnull
    public static HashMap<String, Double> getExtraSlotAttributes(@Nullable LivingEntity entity) {
        if (entity == null) {
            return new HashMap<>();
        }
        return getCachedExtraSlotAttributes(entity);
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
     * 获取伤害数字的显示前缀
     *
     * @param attacker 攻击者实体
     * @return 显示前缀字符串
     */
    static String getDamageDisplayPrefix(LivingEntity attacker) {
        return (attacker instanceof Player) ? "" : "🎀";
    }

    /**
     * ⭐ 把本次攻击触发的元素拼成伤害数字后缀（各元素图标自带颜色码）
     *
     * @param triggeredElements 本次攻击触发的元素集合（可为空集合）
     * @return 后缀字符串；无元素时返回空串
     */
    private static String buildElementSuffix(Set<String> triggeredElements) {
        if (triggeredElements.isEmpty()) {
            return "";
        }
        StringBuilder suffix = new StringBuilder();
        for (String element : triggeredElements) {
            suffix.append(WeaponElementSystem.getElementEmoji(element));
        }
        return suffix.toString();
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
     *
     * <p>⭐ 非开光武器造成的伤害：交给 {@link DamageDisplayTracker#registerPlain} 登记普通白字，
     * 该方法在 {@code damageDisplay} 关闭时直接返回，默认配置下没有任何开销。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        // ⭐ 溅射派生伤害不做模组结算，避免远程溅射被当成近战重复计算
        if (SPLASH_REENTRY.get()) {
            return;
        }

        // ⭐ 0 伤害直接放行。
        //    TACZ 把一发子弹拆成两次 hurt()：普通段 damage×(1−armorIgnore) 与
        //    破甲段 damage×armorIgnore，中间还把无敌帧清零。armor_ignore 为 0 或 1 的枪
        //    必有一段是 0 伤害，而 Forge 的 ForgeHooks.onLivingHurt 是在
        //    LivingEntity.actuallyHurt 里 `damage <= 0` 判断<b>之前</b>调用的，
        //    所以那一段照样会跑完整套结算 —— 伤害数字有 totalDamage > 0 保护不会重复，
        //    但元素触发、净化驱散、处决会各多掷一次，debuff 挂载频率直接翻倍。
        //    0 伤害本来也算不出任何东西，提前返回即可。
        if (evt.getAmount() <= 0) {
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
                    return;
                }
            }

            // ⭐ 非开光武器：普通白字同样走登记，最终伤害回调才能算上护盾吃掉的部分
            DamageDisplayTracker.registerPlain(evt.getEntity(), damageSource, evt.getAmount());
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
     * <p>⭐ 魔法伤害：由 {@link MagicDamageClassifier#isMagic} 判定，是独立于近战/远程的叠加层：
     * 先照常走近战或远程分支，判定为魔法再额外加 {@code magicDamage}。</p>
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

        // ========== 伤害类型加成（基础分支：近战 / 远程） ==========
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

        // ========== 魔法叠加层（独立于基础分支，见 MagicDamageClassifier：原版规则 + 第三方规则） ==========
        if (MagicDamageClassifier.isMagic(damageSource)) {
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

        // ⭐ 元素触发硬上限（单次命中）：所有加成乘算完成后，强制截断
        triggerChance = Math.min(triggerChance, MAX_ELEMENT_TRIGGER_PER_HIT * 100.0);

        Set<String> triggeredElements = new LinkedHashSet<>();

        // ⭐ 元素池只构建一次；池为空时整段触发逻辑直接跳过
        if (triggerChance > 0) {
            WeaponElementSystem.ElementPool elementPool = WeaponElementSystem.buildElementPool(weapon, modules);
            if (!elementPool.isEmpty()) {
                // ⭐ 先把本次命中要掷的总次数算清（整数部分 + 小数部分的一次概率掷），
                //    再一次性向单 tick 全局预算申请。若改成逐次申请，同一 tick 的前几个目标
                //    会把预算抢光，后面的目标一次都触发不了，群体命中的表现会变得极不稳定。
                int rolls;
                if (triggerChance > 100) {
                    rolls = (int) triggerChance / 100;
                    if (RandomUtil.percentageChance(triggerChance - rolls * 100)) {
                        rolls++;
                    }
                } else {
                    rolls = RandomUtil.percentageChance(triggerChance) ? 1 : 0;
                }

                rolls = acquireElementTriggerBudget(hurter.level(), rolls);

                for (int i = 0; i < rolls; i++) {
                    String element = WeaponElementSystem.triggerElementEffect(damageSource, hurter, attacker, weapon,
                            elementPool, triggerTime, coreDamage, attributes, baneMultiplier);
                    if (element != null) triggeredElements.add(element);
                }
            }
        }

        totalDamage = Math.max(totalDamage, 0);
        evt.setAmount(totalDamage);

        // ⭐ 提前取得伤害数字接收者，溅射与主目标登记共用同一个引用
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

        // ⭐ 登记伤害数字，以本次 DamageSource 对象作为配对凭据。
        //    最终伤害 ≤ 0 时原版会在 actuallyHurt 里提前返回、根本走不到最终伤害回调，登记了只会白白作废，故跳过
        if (displayTarget != null && totalDamage > 0) {
            DamageDisplayTracker.register(hurter, damageSource, displayTarget, colorCode,
                    getDamageDisplayPrefix(attacker), buildElementSuffix(triggeredElements),
                    DamagePacket.Channel.PRIMARY);
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
     * 伤害数字复用主目标的暴击颜色码，故溅射数字与主目标同色；
     * 因不触发元素，数字后不带元素图标。</p>
     *
     * <p>⭐ 每个溅射目标都走 {@link DamageDisplayTracker#hurtWithDisplay}：登记 → {@code hurt} → 未消费即撤回。
     * 连射时溅射目标大多处于受击无敌，{@code hurt} 会直接作废；
     * 旧实现在这里留下残留条目，导致该目标之后的数字整体错位，现已杜绝。</p>
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

        SPLASH_REENTRY.set(Boolean.TRUE);
        try {
            for (LivingEntity entity : entities) {
                // 溅射不触发元素，故无元素后缀；颜色码沿用主目标的暴击结果。
                // displayTarget 为 null 或跳字关闭时，内部只造成伤害、不登记
                DamageDisplayTracker.hurtWithDisplay(entity, splashSource, splashDamage, displayTarget,
                        colorCode, prefix, "", DamagePacket.Channel.PRIMARY);
            }
        } finally {
            // 异常时也必须复位，否则该线程后续所有攻击都会被跳过结算
            SPLASH_REENTRY.set(Boolean.FALSE);
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
     * <p>⭐ 伤害数字改为实测：扣血前记下血量，扣完显示实际掉了多少
     * （见 {@link DamageDisplayTracker#resolveDirectLoss}）。
     * Boss 锁血、伤害上限、其他模组拦截 {@code setHealth} 时数字如实变小或不显示；
     * 目标因此死亡时显示完整预算值。真伤直接改血量、不经过护盾，因此不带护盾图标。</p>
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

                // ⭐ 先记下扣血前的血量，扣完再测实际掉了多少
                float healthBefore = hurter.getHealth();
                if (healthBefore - finalTrueDamage > 0.01f) {
                    LivingEntityUtil.damageHealthDirectly(hurter, finalTrueDamage);
                } else {
                    LivingEntityUtil.kill(hurter, trueSource);
                }
                float shown = DamageDisplayTracker.resolveDirectLoss(hurter, healthBefore, finalTrueDamage);

                DamageDisplayTracker.sendDirect(findDamageDisplayTarget(trueAttacker), hurter, shown,
                        "\u00a75", getDamageDisplayPrefix(trueAttacker), getTrueBulletEmoji(), false,
                        DamagePacket.Channel.PRIMARY);
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
                LivingEntityUtil.kill(hurter, exSource);
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

    // ========== ⭐ 元素 DOT 去重注册表 / Element DOT Deduplication Registry ==========

    /**
     * 元素 DOT 单轮伤害执行体
     *
     * <p>注册表只负责「同一目标的同一元素永远只有一个任务在跑」这件事，
     * 具体一轮怎么扣血、怎么发跳字、要不要放粒子，仍由元素系统自己决定，
     * 通过本接口回传。这样注册表不需要访问元素系统的任何私有显示逻辑。</p>
     */
    @FunctionalInterface
    public interface DotTickAction {

        /**
         * 执行一轮 DOT 伤害
         *
         * @param target 目标实体（调用前已确认未死亡、未移除）
         * @param damage 本轮伤害（取自注册表中的当前值）
         * @return 返回 false 表示请求立即终止本条 DOT（例如本轮已把目标打死）
         */
        boolean run(@Nonnull LivingEntity target, float damage);
    }

    /**
     * ⭐ 元素 DOT 去重注册表：按「实体 + 元素」合并持续伤害
     *
     * <p><b>要解决的问题。</b>元素系统此前每触发一次 fire / poison / slash 就
     * {@code new SynchronizationTask(20, 20)} 起一个<b>独立</b>任务，完全不去重，后果有两层：</p>
     * <ol>
     *   <li><b>平衡性（更严重）</b>：切割与带盾时的毒素走
     *       {@code LivingEntityUtil.damageHealthDirectly}，<b>绕过无敌帧</b>，
     *       所以重复 DOT 的伤害是真·线性叠加 —— 一次命中触发 6 次切割，
     *       就是 6 份切割 DOT 同时扣血，秒杀任何血量。</li>
     *   <li><b>性能</b>：群体命中（横扫 / 霰弹打十几只怪）时几百个任务并存，
     *       其中 gas 的每个重复任务每轮还各做一次 AABB 实体扫描，刷怪塔场景能拉出可测的 tick 时间。</li>
     * </ol>
     *
     * <p><b>合并规则。</b>同一目标的同一元素只保留一条状态：
     * 伤害<b>取 max</b>、剩余轮数<b>刷新为满</b>（取与新轮数的较大者，
     * 免得一次短 triggerTime 的触发把正在跑的长 DOT 截断）。
     * <b>绝不相加</b> —— 相加等于没修平衡问题。</p>
     *
     * <p><b>生命周期。</b>任务自身在目标死亡 / 被移除 / 轮数耗尽时 {@code cancel()} 并摘除自己的条目；
     * 另有 {@link WeaponCombatHandler#onLivingDeathClearDot} 与
     * {@link WeaponCombatHandler#onEntityLeaveLevelClearDot} 两道兜底，
     * 覆盖怪物在 DOT 跑完前被打死、区块卸载、跨维度传送等场景，Map 不会泄漏。
     * 兜底写法对齐 {@code DynamicAttributeManager} 的同名处理。</p>
     *
     * <p><b>接入状态。</b>本注册表已可用，但实际起 DOT 任务的三处代码在
     * {@code WeaponElementSystem}（fire / poison / slash，另有 gas 的 AABB 扫描任务），
     * 那个文件不在本次改动范围内，需由主线把那几处的
     * {@code new SynchronizationTask(20, 20) { ... }} 换成
     * {@code WeaponCombatHandler.ElementDotRegistry.apply(hurter, "slash", dotDamage, rounds, (t, d) -> { ...原轮体... })}。
     * 在那之前本类不被调用，无任何运行时开销。</p>
     */
    public static final class ElementDotRegistry {

        /** DOT 轮间隔（tick），与元素系统原有的 {@code new SynchronizationTask(20, 20)} 保持一致 */
        private static final int DOT_ROUND_INTERVAL = 20;

        /**
         * key = 实体 UUID，value = （元素名 → 状态）
         *
         * <p>用 UUID 而非 entityId：entityId 只在单个世界内唯一且会被回收，
         * 跨维度时可能撞号；这一点与 {@code EXTRA_SLOT_CACHE} 的选择一致。</p>
         */
        private static final Map<UUID, Map<String, DotState>> STATES = new ConcurrentHashMap<>();

        private ElementDotRegistry() {
        }

        /** 单条 DOT 的可变状态 */
        private static final class DotState {

            /** 每轮伤害，重复触发时取 max */
            private volatile float damage;

            /** 剩余轮数，重复触发时刷新为满 */
            private volatile int remainingRounds;

            /** 单轮执行体，重复触发时替换为最新的一份（攻击者 / 跳字接收者可能已变） */
            private volatile DotTickAction action;

            /** 正在跑的任务，用于兜底清理时取消 */
            private volatile SynchronizationTask task;

            /**
             * 目标死亡时是否终止本条 DOT
             *
             * <p>绝大多数元素是「附着在目标身上」的，目标死了自然结束；
             * 但毒气（gas）是**以命中位置为中心的 AoE 云**，锚点怪死掉之后云本来还会继续
             * 伤害圈内其它实体 —— 改造前那条任务的判定也确实只有 {@code isRemoved()}
             * 而没有 {@code isDeadOrDying()}。若在这里一刀切地按死亡终止，
             * 毒气在实战中（锚点通常最先死）会几乎失效。</p>
             */
            private volatile boolean stopOnDeath = true;
        }

        /**
         * 施加 / 刷新一条元素 DOT
         *
         * @param target  目标实体
         * @param element 元素名（如 {@code "fire"} / {@code "poison"} / {@code "slash"}）
         * @param damage  每轮伤害
         * @param rounds  总轮数
         * @param action  单轮执行体
         */
        public static void apply(@Nullable LivingEntity target, @Nonnull String element,
                                 float damage, int rounds, @Nonnull DotTickAction action) {
            apply(target, element, damage, rounds, action, true);
        }

        /**
         * 施加 / 刷新一条元素 DOT（可指定目标死亡后是否继续）
         *
         * @param stopOnDeath 目标死亡时是否终止；毒气这类 AoE 云传 false
         */
        public static void apply(@Nullable LivingEntity target, @Nonnull String element,
                                 float damage, int rounds, @Nonnull DotTickAction action,
                                 boolean stopOnDeath) {
            if (target == null || rounds <= 0 || damage <= 0
                    || Float.isNaN(damage) || Float.isInfinite(damage)) {
                return;
            }
            if (target.level().isClientSide()) {
                return;
            }

            final UUID uuid = target.getUUID();
            Map<String, DotState> perEntity = STATES.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());

            DotState existing = perEntity.get(element);
            if (existing != null) {
                // ⭐ 去重核心：取 max + 刷新为满，不新起任务
                if (damage > existing.damage) {
                    existing.damage = damage;
                }
                if (rounds > existing.remainingRounds) {
                    existing.remainingRounds = rounds;
                }
                existing.action = action;
                existing.stopOnDeath = stopOnDeath;
                return;
            }

            final DotState state = new DotState();
            state.damage = damage;
            state.remainingRounds = rounds;
            state.action = action;
            state.stopOnDeath = stopOnDeath;
            perEntity.put(element, state);

            SynchronizationTask task = new SynchronizationTask(DOT_ROUND_INTERVAL, DOT_ROUND_INTERVAL) {
                @Override
                public void run() {
                    if (state.remainingRounds <= 0 || target.isRemoved()
                            || (state.stopOnDeath && target.isDeadOrDying())) {
                        finish();
                        return;
                    }
                    state.remainingRounds--;
                    boolean keepGoing = state.action.run(target, state.damage);
                    if (!keepGoing || state.remainingRounds <= 0) {
                        finish();
                    }
                }

                private void finish() {
                    detach(uuid, element, this);
                    this.cancel();
                }
            };
            state.task = task;
            task.start();
        }

        /**
         * 摘除条目（仅当条目仍属于该任务时，避免误删刷新后新建的条目）
         */
        private static void detach(@Nonnull UUID uuid, @Nonnull String element, @Nonnull SynchronizationTask task) {
            Map<String, DotState> perEntity = STATES.get(uuid);
            if (perEntity == null) {
                return;
            }
            DotState state = perEntity.get(element);
            if (state != null && state.task == task) {
                perEntity.remove(element);
            }
            if (perEntity.isEmpty()) {
                STATES.remove(uuid);
            }
        }

        /**
         * 清空某实体身上的全部元素 DOT（幂等，重复调用无副作用）
         *
         * @param target 目标实体
         */
        public static void clear(@Nullable LivingEntity target) {
            if (target == null) {
                return;
            }
            Map<String, DotState> perEntity = STATES.remove(target.getUUID());
            if (perEntity == null) {
                return;
            }
            for (DotState state : perEntity.values()) {
                cancelState(state);
            }
        }

        /**
         * 目标死亡时的清理：只清「死亡即终止」的条目
         *
         * <p>毒气云（{@code stopOnDeath = false}）不在此列 —— 锚点怪死了云还要继续，
         * 它由轮数耗尽或 {@code EntityLeaveLevelEvent} 收尾。</p>
         *
         * @param target 死亡的实体
         */
        public static void clearOnDeath(@Nullable LivingEntity target) {
            if (target == null) {
                return;
            }
            Map<String, DotState> perEntity = STATES.get(target.getUUID());
            if (perEntity == null) {
                return;
            }
            perEntity.entrySet().removeIf(e -> {
                if (!e.getValue().stopOnDeath) {
                    return false;
                }
                cancelState(e.getValue());
                return true;
            });
            if (perEntity.isEmpty()) {
                STATES.remove(target.getUUID());
            }
        }

        private static void cancelState(@Nonnull DotState state) {
            state.remainingRounds = 0;
            SynchronizationTask task = state.task;
            if (task != null) {
                task.cancel();
            }
        }

        /**
         * 当前正在跑的 DOT 条数（调试 / 性能观测用）
         *
         * @return DOT 总条数
         */
        public static int activeCount() {
            int count = 0;
            for (Map<String, DotState> perEntity : STATES.values()) {
                count += perEntity.size();
            }
            return count;
        }
    }

    /**
     * ⭐ 实体死亡时清空其元素 DOT
     *
     * <p>这是防泄漏的主路径：怪物几乎总在 DOT 跑完前被打死，
     * 任务自身的轮数递减逻辑永远走不到头。LOWEST 优先级，让其它模组的死亡处理先跑完。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeathClearDot(LivingDeathEvent evt) {
        LivingEntity entity = evt.getEntity();
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        // ⭐ 只清「死亡即终止」的条目：毒气云要在锚点死后继续伤害圈内其它实体
        ElementDotRegistry.clearOnDeath(entity);
    }

    /**
     * ⭐ 实体离开世界时清空其元素 DOT（兜底）
     *
     * <p>覆盖死亡之外的移除场景：区块卸载、指令 kill、跨维度传送、实体 discard。</p>
     */
    @SubscribeEvent
    public static void onEntityLeaveLevelClearDot(EntityLeaveLevelEvent evt) {
        if (evt.getLevel().isClientSide()) {
            return;
        }
        if (evt.getEntity() instanceof LivingEntity living) {
            ElementDotRegistry.clear(living);
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
        // ⭐ 修复 4：用「游戏时间 + 实体 ID」错开，避免所有持开光武器的实体挤在同一个 tick 里算属性
        if (Math.floorMod(entity.level().getGameTime() + entity.getId(), 20L) != 0) return;

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
        ItemStack weapon = firingRateSource(entity, usingItem);
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
        // 总射速为负是「这把武器被废掉」的设计，弓弩也一样：-100% 直接放下手，
        // 中间值按概率让这一 tick 的蓄力 / 使用凭空消失。掷骰用确定性的那套，
        // 不然客户端和服务端各掷各的，蓄力进度两端对不上（和下面 tick 加速那条同一个理由）
        if (firingRate <= -1.0) { entity.stopUsingItem(); }
        else if (firingRate < 0) { if (deterministicChance(entity, Math.min(1.0, Math.abs(firingRate)))) { evt.setCanceled(true); } }
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
        ItemStack weapon = firingRateSource(entity, usingItem);
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
        for (int i = 0; i < extraUpdates; i++) { LivingEntityUtil.updateHeld(entity); }
        double fractionalPart = firingRate - extraUpdates;
        // ⭐ 修复 2：本方法客户端、服务端都在跑（客户端跑是为了蓄力动画跟上实际进度）。
        //    小数部分原来用 RandomUtil 掷骰，两端各掷各的，动画和实际进度会对不上；
        //    现改为按「游戏时间 + 实体 ID」做确定性掷骰，两端同一 tick 得到同一结果
        if (fractionalPart > 0 && deterministicChance(entity, fractionalPart)) { LivingEntityUtil.updateHeld(entity); }
    }

    /**
     * 射速该按哪件武器算。
     *
     * <p>优先用<b>正在使用的那一件</b>：副手拿弓、主手拿别的武器时，原版会把右键转给副手，
     * 此时该生效的是副手那张弓自己的模组。只看主手的话，副手弓会莫名其妙吃到主手武器的射速，
     * 换一把主手武器同一张弓的手感就变了。
     *
     * <p>正在用的那件没有开光（没有模组基座）时退回主手，保持老行为——
     * 拿着开了光的枪、同时在吃东西之类的场景不受影响。
     *
     * @param entity    使用者
     * @param usingItem 正在使用的物品
     * @return 用来读射速属性的那件武器
     */
    @Nonnull
    private static ItemStack firingRateSource(@Nonnull LivingEntity entity, @Nonnull ItemStack usingItem) {
        return WeaponModuleHandler.hasBase(usingItem) ? usingItem : entity.getMainHandItem();
    }

    /**
     * 确定性掷骰：同一实体在同一游戏 tick 上，客户端和服务端算出的结果一定相同
     *
     * <p>把游戏时间和实体 ID 混成一个 64 位散列，取高 53 位当 [0,1) 的随机数。
     * 游戏时间两端由原版同步，实体 ID 两端一致，所以两端结果一致。</p>
     *
     * @param entity 实体
     * @param chance 概率（0 ~ 1）
     * @return 命中返回 true
     */
    private static boolean deterministicChance(LivingEntity entity, double chance) {
        long seed = entity.level().getGameTime() * 0x9E3779B97F4A7C15L + entity.getId();
        seed ^= (seed >>> 29);
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= (seed >>> 32);
        double roll = (seed >>> 11) * 0x1.0p-53;
        return roll < chance;
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
