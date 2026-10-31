package pers.roinflam.kuvalich.fx;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 暴击效果包的攻击者节流
 *
 * <p>同一 tick 里同一个攻击者只发最高的那一档（横扫一下打中五只，只演一次）；
 * 再按档限频：一档距上次一档至少 3 tick、二档 2 tick、三档 1 tick 才发。
 * 暴击结算时先登记，tick 末尾统一挑最高档发出去，所以「同 tick 后来居上」的高档不会被先到的低档占掉。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CritFxGate {

    /** 每档最小间隔（tick），下标 = 档位 - 1 */
    private static final int[] MIN_GAP = {3, 2, 1};
    /** 多久没动的攻击者记录清掉 */
    private static final int STALE_TICKS = 100;

    /** 每个攻击者：[一档上次, 二档上次, 三档上次, 最近一次登记的 tick] */
    private static final Int2ObjectOpenHashMap<long[]> STATE = new Int2ObjectOpenHashMap<>();
    /** 这一 tick 里每个攻击者登记的最高档 */
    private static final Int2ObjectOpenHashMap<Pending> PENDING = new Int2ObjectOpenHashMap<>();
    private static long tick;

    private record Pending(LivingEntity hurter, int tier) {
    }

    private CritFxGate() {
    }

    /**
     * 暴击分档以后调用
     *
     * @param tier 1 = 黄、2 = 橙、3 = 红
     */
    public static void tryEmit(LivingEntity attacker, LivingEntity hurter, int tier) {
        if (attacker == null || hurter == null || hurter.level().isClientSide || tier < 1 || tier > 3) {
            return;
        }
        Pending cur = PENDING.get(attacker.getId());
        if (cur == null || tier > cur.tier()) {
            PENDING.put(attacker.getId(), new Pending(hurter, tier));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick++;
        if (!PENDING.isEmpty()) {
            for (Int2ObjectOpenHashMap.Entry<Pending> e : PENDING.int2ObjectEntrySet()) {
                int attackerId = e.getIntKey();
                Pending p = e.getValue();
                long[] s = STATE.computeIfAbsent(attackerId, k -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2,
                        Long.MIN_VALUE / 2, 0L});
                s[3] = tick;
                int i = p.tier() - 1;
                if (tick - s[i] < MIN_GAP[i] || p.hurter().isRemoved()) {
                    continue;
                }
                s[i] = tick;
                KuvaFx.tracking(p.hurter(), FxId.CRIT, p.tier(), attackerId + 1);
            }
            PENDING.clear();
        }
        if (tick % 200 == 0) {
            STATE.int2ObjectEntrySet().removeIf(e -> tick - e.getValue()[3] > STALE_TICKS);
        }
    }
}
