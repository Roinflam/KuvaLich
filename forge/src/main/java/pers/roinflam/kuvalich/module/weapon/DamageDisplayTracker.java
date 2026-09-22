package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 伤害数字登记与配对中心（仅服务端逻辑）
 *
 * <h3>它解决什么问题</h3>
 *
 * <p>伤害数字的「颜色、元素图标、发给谁」在受击事件里就能确定，
 * 但「实际扣了多少」要等整条伤害流程跑完才知道（由 {@code MixinForgeHooksFinalDamage}
 * 在 {@code ForgeHooks.onLivingDamage} 返回时回调 {@link #onFinalDamage}）。
 * 两个时机之间需要一张「待显示登记表」把它们对上。</p>
 *
 * <p>旧实现按先进先出配对：只要某一下登记了、却没走到扣血那一步
 * （溅射目标处于受击无敌、举盾格挡、被其他模组或插件取消等），条目就会残留，
 * 之后每一发都拿到上一发的颜色与图标，甚至把数字发给别的玩家。</p>
 *
 * <h3>本类的配对规则</h3>
 *
 * <ol>
 *   <li><b>按 DamageSource 对象配对</b>：同一次挨打，从受击事件到最终伤害回调，
 *       传递的是同一个 {@link DamageSource} 实例（不是内容相同，是同一个对象）。
 *       回调只取与本次对象相同的登记，对不上的一律不碰，残留条目因此不可能被别的伤害吃掉。</li>
 *   <li><b>主动撤回</b>：由本模组自己发起的伤害走 {@link #hurtWithDisplay}，
 *       {@code hurt} 返回后若登记仍未被消费（这一下被挡掉了），立即撤回。</li>
 *   <li><b>每 tick 清空</b>：登记与消费总在同一段同步调用里完成，
 *       tick 结束时还留在表里的必然是作废条目，统一清空，不会累积、也不会持有玩家引用过久。</li>
 *   <li><b>等价兜底</b>：若某服务端核心在伤害流程中替换了 DamageSource 对象导致对象对不上，
 *       再用「伤害类型 + 攻击者 + 直接实体」比对最近一次登记，保证数字不会整体消失，并打印一次告警。</li>
 * </ol>
 *
 * <h3>显示口径</h3>
 *
 * <ul>
 *   <li><b>走 hurt 的伤害</b>：显示最终伤害回调里的值，所有模组的减伤、护甲、受击无敌都已体现；
 *       目标有吸收护盾时，按「登记时吸收量 − 回调时吸收量」补回护盾吃掉的部分，并追加护盾图标。</li>
 *   <li><b>直接改血量的伤害</b>（真伤、切割、带盾毒素、武器持续伤害）：显示「扣之前血量 − 扣之后血量」，
 *       见 {@link #resolveDirectLoss}；目标因此死亡时按溢出规则显示完整预算值，与主伤害口径一致。</li>
 *   <li><b>多段同步伤害</b>（爆炸元素）：用 {@link HealthSnapshot} 前后对比，每个目标只跳一个合计数字。</li>
 * </ul>
 *
 * <h3>合并分组</h3>
 *
 * <p>⭐ 本次改动：每条数字多带一个「合并分组」（{@code mergeGroup}）发给客户端。
 * 普通伤害（命中本体、溅射、真伤、白字、武器持续伤害）用 {@link DamagePacket#MERGE_GROUP_GENERAL}，
 * 本模组自己的元素伤害用元素名。客户端开启合并后，只有同一只怪、同一分组的数字才会并成一条，
 * 这样元素图标不会被别的伤害混进去。不带分组参数的旧重载一律按普通伤害处理，调用方无需改动。</p>
 *
 * <p>本类不引用任何客户端类，可安全地在专用服务端加载。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
public final class DamageDisplayTracker {

    // ========== 常量 / Constants ==========

    /** 护盾图标（§b 🛡），由客户端拼文本时追加在伤害数字与元素图标之后（见 {@code DamageInfo}） */
    public static final String SHIELD_ICON = "\u00a7b\ud83d\udee1";

    /** 普通白字颜色码 */
    private static final String PLAIN_COLOR = "\u00a7f";

    /** 吸收量差值的最小有效值：低于该值视为浮点误差，不算打到护盾 */
    private static final float SHIELD_EPSILON = 0.001F;

    /**
     * Mixin 缺失判定阈值：回调从未触发过、且累计这么多个 tick 出现未消费登记时，才判定 Mixin 未生效。
     * 取 20 而不是 1，是为了避开「开服后第一下恰好被盾挡住」这类偶发情况造成误报。
     */
    private static final int HOOK_MISSING_TICK_THRESHOLD = 20;

    // ========== 登记表 / Pending Table ==========

    /**
     * 待显示登记表：key = 受击实体 ID，value = 该实体本 tick 内尚未消费的登记（按登记先后排列）
     *
     * <p>正常情况下登记会在同一段同步调用里被消费或撤回，表通常是空的；
     * 每个 tick 末统一清空作废条目，见 {@link #onServerTick}。</p>
     */
    private static final Map<Integer, ArrayDeque<PendingDisplay>> PENDING = new ConcurrentHashMap<>();

    // ========== 诊断标志 / Diagnostics ==========

    /** 最终伤害 Mixin 回调是否至少触发过一次（用于区分「Mixin 没生效」和「其他原因不显示」） */
    private static volatile boolean finalDamageHookVerified = false;

    /** Mixin 缺失告警是否已打印（全程只打印一次） */
    private static volatile boolean hookMissingWarned = false;

    /** 回调从未触发期间，出现未消费登记的 tick 数 */
    private static int leftoverTicksWithoutHook = 0;

    /** 等价兜底告警是否已打印（全程只打印一次） */
    private static volatile boolean identityFallbackWarned = false;

    private DamageDisplayTracker() {
    }

    // ========== 登记条目 / Pending Entry ==========

    /**
     * 一条待显示登记
     *
     * <p>不重写 {@code equals}：撤回时按对象身份移除，保证只撤回自己登记的那一条。</p>
     */
    public static final class PendingDisplay {
        /** 配对凭据：本次伤害的 DamageSource 对象 */
        final DamageSource source;
        /** 接收数字的玩家 UUID（发包时按 UUID 现查在线玩家，兼容重生后换了新对象的情况） */
        final UUID viewerId;
        /** 数字颜色码（暴击分档色 / 白字） */
        final String colorCode;
        /** 数字前缀（宠物、女仆攻击时为🎀） */
        final String prefix;
        /** 数字后缀（元素图标、真伤图标等，自带颜色码） */
        final String suffix;
        /** 登记时目标的吸收护盾量，用于回调时计算护盾吃掉了多少 */
        final float absorptionBefore;
        /** 发包通道，决定占用哪一份限流额度 */
        final DamagePacket.Channel channel;
        /** 合并分组：普通伤害为空串，元素伤害为元素名 */
        final String mergeGroup;

        private PendingDisplay(DamageSource source, UUID viewerId, String colorCode, String prefix,
                               String suffix, float absorptionBefore, DamagePacket.Channel channel,
                               String mergeGroup) {
            this.source = source;
            this.viewerId = viewerId;
            this.colorCode = colorCode;
            this.prefix = prefix;
            this.suffix = suffix;
            this.absorptionBefore = absorptionBefore;
            this.channel = channel;
            this.mergeGroup = mergeGroup;
        }
    }

    // ========== 登记 / 撤回 API ==========

    /**
     * 为即将发生（或正在进行）的一次伤害登记待显示信息
     *
     * <p>必须在该伤害走到最终伤害回调<b>之前</b>调用：
     * 主伤害在 {@code LivingHurtEvent} 里调用；本模组主动造成的伤害请直接用 {@link #hurtWithDisplay}。</p>
     *
     * @param victim    受击实体
     * @param source    本次伤害的 DamageSource 对象（配对凭据，必须与传给 {@code hurt} 的是同一个对象）
     * @param viewer    接收数字的玩家；为 null 时不登记
     * @param colorCode 数字颜色码
     * @param prefix    数字前缀，可为空串
     * @param suffix    数字后缀，可为空串
     * @param channel   发包通道
     * @return 登记条目（供 {@link #withdraw} 撤回用）；未登记时返回 null
     */
    @Nullable
    public static PendingDisplay register(@Nonnull LivingEntity victim, @Nonnull DamageSource source,
                                          @Nullable ServerPlayer viewer, @Nonnull String colorCode,
                                          @Nonnull String prefix, @Nonnull String suffix,
                                          @Nonnull DamagePacket.Channel channel) {
        return register(victim, source, viewer, colorCode, prefix, suffix, channel, DamagePacket.MERGE_GROUP_GENERAL);
    }

    /**
     * 为即将发生（或正在进行）的一次伤害登记待显示信息（可指定合并分组）
     *
     * @param victim     受击实体
     * @param source     本次伤害的 DamageSource 对象（配对凭据，必须与传给 {@code hurt} 的是同一个对象）
     * @param viewer     接收数字的玩家；为 null 时不登记
     * @param colorCode  数字颜色码
     * @param prefix     数字前缀，可为空串
     * @param suffix     数字后缀，可为空串
     * @param channel    发包通道
     * @param mergeGroup 合并分组：普通伤害传 {@link DamagePacket#MERGE_GROUP_GENERAL}，元素伤害传元素名
     * @return 登记条目（供 {@link #withdraw} 撤回用）；未登记时返回 null
     */
    @Nullable
    public static PendingDisplay register(@Nonnull LivingEntity victim, @Nonnull DamageSource source,
                                          @Nullable ServerPlayer viewer, @Nonnull String colorCode,
                                          @Nonnull String prefix, @Nonnull String suffix,
                                          @Nonnull DamagePacket.Channel channel, @Nonnull String mergeGroup) {
        if (viewer == null || victim.level().isClientSide()) {
            return null;
        }
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return null;
        }

        PendingDisplay entry = new PendingDisplay(source, viewer.getUUID(), colorCode, prefix, suffix,
                victim.getAbsorptionAmount(), channel, mergeGroup);
        // 同一实体同一 tick 内通常只有 1~2 条，初始容量给小一点
        PENDING.computeIfAbsent(victim.getId(), k -> new ArrayDeque<>(2)).addLast(entry);
        return entry;
    }

    /**
     * 撤回一条尚未被消费的登记（已被消费或已被清空时什么都不做）
     *
     * @param victim 登记时的受击实体
     * @param entry  {@link #register} 返回的条目，可为 null
     */
    public static void withdraw(@Nonnull LivingEntity victim, @Nullable PendingDisplay entry) {
        if (entry == null) {
            return;
        }
        int entityId = victim.getId();
        ArrayDeque<PendingDisplay> queue = PENDING.get(entityId);
        if (queue == null) {
            return;
        }
        // PendingDisplay 未重写 equals，按对象身份移除
        queue.removeLastOccurrence(entry);
        if (queue.isEmpty()) {
            PENDING.remove(entityId, queue);
        }
    }

    /**
     * 带数字显示地造成一次伤害：登记 → {@code hurt} → 未被消费则撤回
     *
     * <p>显示的是最终伤害回调里的实际值：免疫、受击无敌、护甲、其他模组减伤都会如实体现，
     * 这一下被整个挡掉就不跳字。</p>
     *
     * @param victim    受击实体
     * @param source    伤害来源（直接传给 {@code hurt}）
     * @param amount    伤害值（直接传给 {@code hurt}）
     * @param viewer    接收数字的玩家；为 null 时只造成伤害、不显示
     * @param colorCode 数字颜色码
     * @param prefix    数字前缀，可为空串
     * @param suffix    数字后缀，可为空串
     * @param channel   发包通道
     * @return {@code hurt} 的返回值
     */
    public static boolean hurtWithDisplay(@Nonnull LivingEntity victim, @Nonnull DamageSource source, float amount,
                                          @Nullable ServerPlayer viewer, @Nonnull String colorCode,
                                          @Nonnull String prefix, @Nonnull String suffix,
                                          @Nonnull DamagePacket.Channel channel) {
        return hurtWithDisplay(victim, source, amount, viewer, colorCode, prefix, suffix, channel,
                DamagePacket.MERGE_GROUP_GENERAL);
    }

    /**
     * 带数字显示地造成一次伤害（可指定合并分组）：登记 → {@code hurt} → 未被消费则撤回
     *
     * @param victim     受击实体
     * @param source     伤害来源（直接传给 {@code hurt}）
     * @param amount     伤害值（直接传给 {@code hurt}）
     * @param viewer     接收数字的玩家；为 null 时只造成伤害、不显示
     * @param colorCode  数字颜色码
     * @param prefix     数字前缀，可为空串
     * @param suffix     数字后缀，可为空串
     * @param channel    发包通道
     * @param mergeGroup 合并分组：普通伤害传 {@link DamagePacket#MERGE_GROUP_GENERAL}，元素伤害传元素名
     * @return {@code hurt} 的返回值
     */
    public static boolean hurtWithDisplay(@Nonnull LivingEntity victim, @Nonnull DamageSource source, float amount,
                                          @Nullable ServerPlayer viewer, @Nonnull String colorCode,
                                          @Nonnull String prefix, @Nonnull String suffix,
                                          @Nonnull DamagePacket.Channel channel, @Nonnull String mergeGroup) {
        PendingDisplay entry = register(victim, source, viewer, colorCode, prefix, suffix, channel, mergeGroup);
        try {
            return victim.hurt(source, amount);
        } finally {
            // hurt 是同步执行的：走到这里时，如果这一下真的扣到了，条目已在回调里被消费；
            // 还在就说明被挡掉了，必须撤回，否则会被同一 tick 内的其他伤害误用
            withdraw(victim, entry);
        }
    }

    /**
     * 为非开光武器造成的普通伤害登记白字（仅 {@code damageDisplay} 开启时生效）
     *
     * <p>在 {@code LivingHurtEvent} 里调用。走登记而不是在回调里直接显示，是为了能算上护盾吃掉的部分。</p>
     *
     * @param victim 受击实体
     * @param source 本次伤害来源
     * @param amount 受击事件里的当前伤害值；≤ 0 时原版不会走到最终伤害回调，直接不登记
     */
    public static void registerPlain(@Nonnull LivingEntity victim, @Nonnull DamageSource source, float amount) {
        if (!(amount > 0F)) {
            return;
        }
        if (!ModConfig.KUVA_LICH.damageDisplay.get()) {
            return;
        }
        ServerPlayer player = findPlayerAttacker(source);
        if (player == null) {
            return;
        }
        register(victim, source, player, PLAIN_COLOR, "", "", DamagePacket.Channel.PRIMARY);
    }

    // ========== 最终伤害回调 / Final Damage Callback ==========

    /**
     * 最终伤害回调：由 {@code MixinForgeHooksFinalDamage} 在 {@code ForgeHooks.onLivingDamage} 返回时调用
     *
     * <p>此时整个 {@code LivingDamageEvent} 事件链已跑完，{@code finalDamage} 即将被用于扣血。
     * 本方法对<b>所有</b>生物受到的伤害都会触发，因此无登记时必须尽快返回。</p>
     *
     * <p><b>仅服务端调用</b>，调用方已做 {@code isClientSide} 判断。</p>
     *
     * @param victim      受击实体
     * @param source      本次伤害来源（配对凭据）
     * @param finalDamage 事件链处理完毕后的扣血量（已扣除吸收护盾）
     */
    public static void onFinalDamage(@Nonnull LivingEntity victim, @Nonnull DamageSource source, float finalDamage) {
        if (!finalDamageHookVerified) {
            finalDamageHookVerified = true;
            LogUtil.info("[伤害显示] 最终伤害 Mixin 回调已生效，伤害数字将显示实际扣除量");
        }

        PendingDisplay entry = takeMatching(victim.getId(), source);
        if (entry != null) {
            // 护盾吃掉的部分：登记时的吸收量 − 当前吸收量（原版在调用 onLivingDamage 之前就已扣掉护盾）
            float absorbed = entry.absorptionBefore - victim.getAbsorptionAmount();
            if (!(absorbed > SHIELD_EPSILON)) {
                absorbed = 0F;
            }
            // 负数（被其他模组改成回血）或非有限值时，血量部分按 0 计
            float healthPart = (finalDamage > 0F && Float.isFinite(finalDamage)) ? finalDamage : 0F;
            send(entry.viewerId, victim, healthPart + absorbed, entry.colorCode, entry.prefix, entry.suffix,
                    absorbed > 0F, entry.channel, entry.mergeGroup);
            return;
        }

        // 兜底：没有任何登记的伤害，仅在 damageDisplay 开启时给造成伤害的玩家显示普通白字
        if (!ModConfig.KUVA_LICH.damageDisplay.get()) {
            return;
        }
        if (!(finalDamage > 0F) || !Float.isFinite(finalDamage)) {
            return;
        }
        ServerPlayer player = findPlayerAttacker(source);
        if (player != null) {
            send(player.getUUID(), victim, finalDamage, PLAIN_COLOR, "", "", false, DamagePacket.Channel.PRIMARY,
                    DamagePacket.MERGE_GROUP_GENERAL);
        }
    }

    /**
     * 取出与本次伤害配对的登记
     *
     * <p>先按对象身份从最近一条往前找；找不到再用等价来源比对<b>最近一条</b>作为兜底。
     * 只看最近一条，是因为同一段同步调用里合法的登记总是最后登记的那一条，更早的都是作废条目。</p>
     *
     * @param entityId 受击实体 ID
     * @param source   本次伤害来源
     * @return 配对到的登记；没有时返回 null
     */
    @Nullable
    private static PendingDisplay takeMatching(int entityId, @Nonnull DamageSource source) {
        // 本方法对全服每一次生物受伤都会调用：表空时直接返回，连 Integer 装箱都省掉
        if (PENDING.isEmpty()) {
            return null;
        }
        ArrayDeque<PendingDisplay> queue = PENDING.get(entityId);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        PendingDisplay found = null;

        // 第一轮：对象身份匹配（正常情况全部命中这一轮）
        Iterator<PendingDisplay> iterator = queue.descendingIterator();
        while (iterator.hasNext()) {
            PendingDisplay candidate = iterator.next();
            if (candidate.source == source) {
                found = candidate;
                iterator.remove();
                break;
            }
        }

        // 第二轮：等价兜底，只比对最近一条
        if (found == null) {
            PendingDisplay last = queue.peekLast();
            if (last != null && isEquivalentSource(last.source, source)) {
                found = queue.pollLast();
                if (!identityFallbackWarned) {
                    identityFallbackWarned = true;
                    LogUtil.warn("[伤害显示] 同一次伤害前后的 DamageSource 不是同一个对象（msgId="
                            + source.getMsgId() + "），已按「伤害类型 + 攻击者 + 直接实体」兜底配对。");
                    LogUtil.warn("[伤害显示] 本条只打印一次。偶发可忽略；若每次战斗都出现，说明服务端核心在伤害流程中替换了 DamageSource，请反馈。");
                }
            }
        }

        if (queue.isEmpty()) {
            PENDING.remove(entityId, queue);
        }
        return found;
    }

    /**
     * 判断两个伤害来源是否「等价」：伤害类型相同，且攻击者、直接实体都是同一个实体
     *
     * @param a 登记时的来源
     * @param b 回调时的来源
     * @return 等价返回 true
     */
    private static boolean isEquivalentSource(@Nonnull DamageSource a, @Nonnull DamageSource b) {
        return a.getEntity() == b.getEntity()
                && a.getDirectEntity() == b.getDirectEntity()
                && a.getMsgId().equals(b.getMsgId());
    }

    // ========== 直接改血量类伤害 / Direct Health Damage ==========

    /**
     * 计算「直接改血量」类伤害应显示的数值
     *
     * <p>用法：扣血前记下 {@code victim.getHealth()}，扣完（或处决）后调用本方法。</p>
     *
     * <ul>
     *   <li>目标没死：返回实际掉血 = 扣之前 − 扣之后。被 Boss 锁血、伤害上限等拦截时如实变小，拦完为 0。</li>
     *   <li>目标因此死亡：返回 max(实际掉血, 预算值)，即溢出伤害显示完整数值，与主伤害口径一致。</li>
     * </ul>
     *
     * @param victim       受击实体
     * @param healthBefore 扣血前的血量
     * @param budget       本次计划造成的伤害（预算值）
     * @return 应显示的数值；≤ 0 表示不显示
     */
    public static float resolveDirectLoss(@Nonnull LivingEntity victim, float healthBefore, float budget) {
        float lost = healthBefore - victim.getHealth();
        if (!(lost > 0F) || !Float.isFinite(lost)) {
            lost = 0F;
        }
        if (victim.isDeadOrDying() && Float.isFinite(budget) && budget > lost) {
            return budget;
        }
        return lost;
    }

    /**
     * 直接发送一条伤害数字（用于不经过最终伤害回调的伤害）
     *
     * @param viewer    接收数字的玩家；为 null 时不发送
     * @param victim    受击实体（决定数字位置）
     * @param amount    显示数值；≤ 0 或非有限值时不发送
     * @param colorCode 数字颜色码
     * @param prefix    数字前缀，可为空串
     * @param suffix    数字后缀，可为空串
     * @param hitShield 是否追加护盾图标
     * @param channel   发包通道
     */
    public static void sendDirect(@Nullable ServerPlayer viewer, @Nonnull LivingEntity victim, float amount,
                                  @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                                  boolean hitShield, @Nonnull DamagePacket.Channel channel) {
        sendDirect(viewer, victim, amount, colorCode, prefix, suffix, hitShield, channel,
                DamagePacket.MERGE_GROUP_GENERAL);
    }

    /**
     * 直接发送一条伤害数字（可指定合并分组）
     *
     * @param viewer     接收数字的玩家；为 null 时不发送
     * @param victim     受击实体（决定数字位置）
     * @param amount     显示数值；≤ 0 或非有限值时不发送
     * @param colorCode  数字颜色码
     * @param prefix     数字前缀，可为空串
     * @param suffix     数字后缀，可为空串
     * @param hitShield  是否追加护盾图标
     * @param channel    发包通道
     * @param mergeGroup 合并分组：普通伤害传 {@link DamagePacket#MERGE_GROUP_GENERAL}，元素伤害传元素名
     */
    public static void sendDirect(@Nullable ServerPlayer viewer, @Nonnull LivingEntity victim, float amount,
                                  @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                                  boolean hitShield, @Nonnull DamagePacket.Channel channel,
                                  @Nonnull String mergeGroup) {
        if (viewer == null) {
            return;
        }
        send(viewer.getUUID(), victim, amount, colorCode, prefix, suffix, hitShield, channel, mergeGroup);
    }

    // ========== 多段同步伤害快照 / Health Snapshot ==========

    /**
     * 一批实体的血量与护盾快照，用于「一次操作里打出多段伤害」的场景（如爆炸元素：原版爆炸 + 补充伤害）
     *
     * <p>操作前 {@link #capture}，操作后 {@link #sendDeltas}，每个实体只跳一个合计数字，
     * 数值为「扣之前（血量 + 护盾）− 扣之后（血量 + 护盾）」。</p>
     */
    public static final class HealthSnapshot {
        /** 快照中的实体 */
        private final LivingEntity[] entities;
        /** 各实体快照时的血量 */
        private final float[] health;
        /** 各实体快照时的吸收护盾量 */
        private final float[] absorption;

        private HealthSnapshot(LivingEntity[] entities, float[] health, float[] absorption) {
            this.entities = entities;
            this.health = health;
            this.absorption = absorption;
        }

        /**
         * 为一批实体拍快照（调用方负责过滤掉已死亡的实体）
         *
         * @param targets 需要记录的实体
         * @return 快照对象
         */
        @Nonnull
        public static HealthSnapshot capture(@Nonnull List<LivingEntity> targets) {
            int size = targets.size();
            LivingEntity[] entities = new LivingEntity[size];
            float[] health = new float[size];
            float[] absorption = new float[size];
            for (int i = 0; i < size; i++) {
                LivingEntity entity = targets.get(i);
                entities[i] = entity;
                health[i] = entity.getHealth();
                absorption[i] = entity.getAbsorptionAmount();
            }
            return new HealthSnapshot(entities, health, absorption);
        }

        /**
         * 对比快照，给每个确实掉了血或护盾的实体发一个合计数字
         *
         * @param viewer         接收数字的玩家；为 null 时不发送
         * @param budgetIfKilled 目标因此死亡时的最低显示值（溢出规则）
         * @param colorCode      数字颜色码
         * @param prefix         数字前缀，可为空串
         * @param suffix         数字后缀，可为空串
         * @param channel        发包通道
         */
        public void sendDeltas(@Nullable ServerPlayer viewer, float budgetIfKilled, @Nonnull String colorCode,
                               @Nonnull String prefix, @Nonnull String suffix, @Nonnull DamagePacket.Channel channel) {
            sendDeltas(viewer, budgetIfKilled, colorCode, prefix, suffix, channel, DamagePacket.MERGE_GROUP_GENERAL);
        }

        /**
         * 对比快照，给每个确实掉了血或护盾的实体发一个合计数字（可指定合并分组）
         *
         * @param viewer         接收数字的玩家；为 null 时不发送
         * @param budgetIfKilled 目标因此死亡时的最低显示值（溢出规则）
         * @param colorCode      数字颜色码
         * @param prefix         数字前缀，可为空串
         * @param suffix         数字后缀，可为空串
         * @param channel        发包通道
         * @param mergeGroup     合并分组：普通伤害传 {@link DamagePacket#MERGE_GROUP_GENERAL}，元素伤害传元素名
         */
        public void sendDeltas(@Nullable ServerPlayer viewer, float budgetIfKilled, @Nonnull String colorCode,
                               @Nonnull String prefix, @Nonnull String suffix, @Nonnull DamagePacket.Channel channel,
                               @Nonnull String mergeGroup) {
            if (viewer == null) {
                return;
            }
            UUID viewerId = viewer.getUUID();
            for (int i = 0; i < entities.length; i++) {
                LivingEntity entity = entities[i];

                float absorbed = absorption[i] - entity.getAbsorptionAmount();
                if (!(absorbed > SHIELD_EPSILON)) {
                    absorbed = 0F;
                }
                float healthLost = health[i] - entity.getHealth();
                if (!(healthLost > 0F) || !Float.isFinite(healthLost)) {
                    healthLost = 0F;
                }

                float shown = healthLost + absorbed;
                if (entity.isDeadOrDying() && Float.isFinite(budgetIfKilled) && budgetIfKilled > shown) {
                    shown = budgetIfKilled;
                }
                send(viewerId, entity, shown, colorCode, prefix, suffix, absorbed > 0F, channel, mergeGroup);
            }
        }
    }

    // ========== 发包 / Send ==========

    /**
     * 统一发包出口：现查接收者、选位置、把各字段拆开装进 {@link DamagePacket}
     *
     * <p>⭐ 本次改动：文本不再在服务端拼好，而是把数值、颜色码、前缀、后缀、护盾标记、合并分组
     * 原样发给客户端，由客户端决定是单独显示还是并进已有的数字里。</p>
     *
     * @param viewerId   接收数字的玩家 UUID
     * @param victim     受击实体
     * @param amount     显示数值；≤ 0 或非有限值时不发送
     * @param colorCode  数字颜色码
     * @param prefix     数字前缀
     * @param suffix     数字后缀
     * @param hitShield  是否追加护盾图标
     * @param channel    发包通道
     * @param mergeGroup 合并分组
     */
    private static void send(@Nullable UUID viewerId, @Nonnull LivingEntity victim, float amount,
                             @Nonnull String colorCode, @Nonnull String prefix, @Nonnull String suffix,
                             boolean hitShield, @Nonnull DamagePacket.Channel channel, @Nonnull String mergeGroup) {
        if (!(amount > 0F) || !Float.isFinite(amount)) {
            return;
        }
        ServerPlayer viewer = resolveViewer(victim, viewerId);
        if (viewer == null || !viewer.isAlive()) {
            return;
        }

        DamagePacket packet = new DamagePacket(victim.getId(), amount, colorCode, prefix, suffix, hitShield,
                mergeGroup, randomDisplayPosition(victim));
        DamagePacket.sendToPlayer(viewer, packet, channel);
    }

    /**
     * 按 UUID 现查在线玩家
     *
     * <p>不直接持有 ServerPlayer：DoT 持续期间玩家可能下线或死亡重生（重生后是新的 ServerPlayer 对象）。</p>
     *
     * @param reference 用于取得服务器实例的参考实体
     * @param uuid      玩家 UUID
     * @return 在线玩家；不在线或参数无效时返回 null
     */
    @Nullable
    private static ServerPlayer resolveViewer(@Nonnull LivingEntity reference, @Nullable UUID uuid) {
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
     * 从伤害来源里找出造成伤害的玩家（直接实体优先，其次间接实体）
     *
     * @param source 伤害来源
     * @return 服务端玩家；不是玩家造成的返回 null
     */
    @Nullable
    private static ServerPlayer findPlayerAttacker(@Nonnull DamageSource source) {
        if (source.getDirectEntity() instanceof ServerPlayer direct) {
            return direct;
        }
        if (source.getEntity() instanceof ServerPlayer indirect) {
            return indirect;
        }
        return null;
    }

    /**
     * 在实体身上随机取一个伤害数字的显示位置
     *
     * <p>高度取身体上半截（0.6 ~ 1.0 倍身高）。客户端渲染已改为以相机为原点，
     * 这里给出的就是数字在世界里的真实位置，不再依赖旧渲染器「抬高一个眼高」的偏差来凑位置。</p>
     *
     * @param entity 受击实体
     * @return 随机偏移后的世界坐标
     */
    @Nonnull
    public static Vec3 randomDisplayPosition(@Nonnull LivingEntity entity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();
        double offsetX = (random.nextDouble() - 0.5) * width * 1.2;
        double offsetZ = (random.nextDouble() - 0.5) * width * 1.2;
        double offsetY = height * (0.6 + random.nextDouble() * 0.4);
        return new Vec3(entity.getX() + offsetX, entity.getY() + offsetY, entity.getZ() + offsetZ);
    }

    // ========== 生命周期 / Lifecycle ==========

    /**
     * 服务端 tick 末：清空本 tick 内所有未消费的作废登记，并做 Mixin 自检
     *
     * @param evt 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }
        if (PENDING.isEmpty()) {
            return;
        }

        // ⭐ 自检：回调从未触发过、却反复出现未消费登记 → 几乎可以断定 Mixin 未生效
        if (!finalDamageHookVerified && !hookMissingWarned
                && ++leftoverTicksWithoutHook >= HOOK_MISSING_TICK_THRESHOLD) {
            hookMissingWarned = true;
            LogUtil.error("[伤害显示] 伤害数字登记持续未被消费，且最终伤害 Mixin 回调从未触发。");
            LogUtil.error("[伤害显示] 判定：MixinForgeHooksFinalDamage 未生效，伤害数字将无法显示。");
            LogUtil.error("[伤害显示] 排查：1) 确认 kuvalich.mixins.json 的 mixins 数组包含 MixinForgeHooksFinalDamage；");
            LogUtil.error("[伤害显示]       2) 确认已执行 clean build 重新生成 refmap；");
            LogUtil.error("[伤害显示]       3) 在启动日志中搜索 kuvalich 与 mixin 关键字查看注入报错。");
        }

        PENDING.clear();
    }

    /**
     * 玩家下线：清理该玩家的伤害数字限流状态（原先没有任何地方调用，限流表会随在线过的玩家数缓慢增长）
     *
     * @param evt 玩家下线事件（仅服务端触发）
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        DamagePacket.cleanupPlayer(evt.getEntity().getUUID());
    }
}
