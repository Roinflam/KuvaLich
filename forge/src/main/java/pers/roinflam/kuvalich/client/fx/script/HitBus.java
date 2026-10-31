package pers.roinflam.kuvalich.client.fx.script;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 命中总线：一刀下去同一 tick 里会同时到暴击、元素两种效果包，各放各的就是一堵噪音墙。
 * 这里把同一 tick、同一攻击者打同一目标的事件攒起来，在 tick 末尾合并后再放——一次命中只有一个主音。
 *
 * <ul>
 *   <li>画面：每个事件的画面都照放，但同一次命中里只有排在最前的那个元素能画几何，其余只出粒子
 *       （暴击在世界里只有三档那个小闪，本来就不画几何）。</li>
 *   <li>主音：三档暴击 &gt; 爆炸 / 电 &gt; 其它元素；开了「战斗声增强」时后面还排着二档、一档。</li>
 *   <li>次音：主音是暴击、另有元素（或主音是爆炸 / 电、另有暴击）时，留一个不同家族的次音，−8 dB、晚 40 ms；
 *       一档暴击永远不当次音；别人的命中不叠次音。</li>
 *   <li>每 tick 至多 8 次暴击画面，超出的只放声音；元素声在合并之后再数，每 100 ms 至多 6 个。</li>
 * </ul>
 *
 * <p>网络包在 tick 开头处理完，所以 tick 末尾合并不会多等一帧。tick 里只复用两张表，不按命中分配容器。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class HitBus {

    /** 每 tick 暴击画面的上限 */
    private static final int MAX_CRITS_PER_TICK = 8;
    /** 次音的增益（−8 dB）和延后 */
    public static final float SECONDARY_GAIN = 0.4F;
    public static final int SECONDARY_DELAY_MS = 40;
    /** 元素声：每 100 ms 至多这么多个 */
    private static final int ELEMENT_SOUND_WINDOW_MS = 100;
    private static final int ELEMENT_SOUNDS_PER_WINDOW = 6;

    /** 事件种类 */
    public enum Kind {
        CRIT, ELEMENT
    }

    /** 元素序号里的爆炸和电（它们的声音排在其它元素前面） */
    static final int ELEMENT_ELECTRICITY = 3;
    static final int ELEMENT_EXPLOSION = 11;

    /** 一次待合并的命中事件 */
    public static final class Hit {
        public final int attackerId;
        public final int victimId;
        public final Kind kind;
        public final KuvaFxPacket packet;
        /** 暴击：1 / 2 / 3；元素：元素序号 */
        public int variant;
        /** 合并后：要不要放声音、增益、延后 */
        public boolean sound;
        public float soundGain = 1.0F;
        public int soundDelayMs;
        /** 合并后：能不能画几何 */
        public boolean geometry;
        /** 合并后：画面也不放（每 tick 上限超了） */
        public boolean visuals = true;
        /** 合并用：已经归到前面某一组里 */
        boolean grouped;

        Hit(int attackerId, int victimId, Kind kind, KuvaFxPacket packet) {
            this.attackerId = attackerId;
            this.victimId = victimId;
            this.kind = kind;
            this.packet = packet;
        }

        /** 声音的优先级；0 表示这次不出声（一档、二档没开增强） */
        int priority() {
            if (kind == Kind.CRIT) {
                return switch (variant) {
                    case 3 -> 10;
                    case 2 -> CombatFx.critCanSound(2) ? 4 : 0;
                    default -> CombatFx.critCanSound(1) ? 2 : 0;
                };
            }
            return variant == ELEMENT_EXPLOSION || variant == ELEMENT_ELECTRICITY ? 8 : 6;
        }
    }

    private static final List<Hit> PENDING = new ArrayList<>();
    private static final List<Hit> GROUP = new ArrayList<>();

    /** 最近元素声的时刻（环形） */
    private static final long[] ELEMENT_SOUND_TIMES = new long[ELEMENT_SOUNDS_PER_WINDOW];
    private static int elementSoundCursor;

    private HitBus() {
    }

    /** 收到暴击 / 元素效果包时投递（不直接起演出） */
    public static void post(int attackerId, int victimId, Kind kind, int variant, KuvaFxPacket p) {
        Hit h = new Hit(attackerId, victimId, kind, p);
        h.variant = kind == Kind.CRIT && variant == CombatFx.LITE ? 3 : variant;
        PENDING.add(h);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        int crits = 0;
        for (int i = 0, n = PENDING.size(); i < n; i++) {
            Hit first = PENDING.get(i);
            if (first.grouped) {
                continue;
            }
            GROUP.clear();
            for (int j = i; j < n; j++) {
                Hit h = PENDING.get(j);
                if (!h.grouped && h.attackerId == first.attackerId && h.victimId == first.victimId) {
                    h.grouped = true;
                    GROUP.add(h);
                }
            }
            crits = resolve(GROUP, crits);
        }
        PENDING.clear();
        GROUP.clear();
    }

    /** 合并一组（同一攻击者打同一目标），定主音、次音、谁画几何，然后起演出；返回累计的暴击画面数 */
    private static int resolve(List<Hit> g, int crits) {
        // 按声音优先级从高到低排（组里一般只有一两个，插入排序）
        for (int i = 1; i < g.size(); i++) {
            Hit h = g.get(i);
            int j = i - 1;
            while (j >= 0 && g.get(j).priority() < h.priority()) {
                g.set(j + 1, g.get(j));
                j--;
            }
            g.set(j + 1, h);
        }
        Hit main = g.get(0).priority() > 0 ? g.get(0) : null;
        boolean local = CombatFx.isLocal(g.get(0).attackerId);
        boolean geometryTaken = false;
        for (int i = 0; i < g.size(); i++) {
            Hit h = g.get(i);
            h.sound = false;
            if (h.kind == Kind.ELEMENT && !geometryTaken) {
                h.geometry = true;
                geometryTaken = true;
            }
        }
        if (main != null) {
            main.sound = main.kind != Kind.ELEMENT || takeElementSound();
            if (local && g.size() > 1) {
                Hit second = pickSecondary(main, g);
                if (second != null && (second.kind != Kind.ELEMENT || takeElementSound())) {
                    second.sound = true;
                    second.soundGain = SECONDARY_GAIN;
                    second.soundDelayMs = SECONDARY_DELAY_MS;
                }
            }
        }
        for (int i = 0; i < g.size(); i++) {
            Hit h = g.get(i);
            if (h.kind == Kind.CRIT) {
                if (h.variant == 3 && ++crits > MAX_CRITS_PER_TICK) {
                    h.visuals = false;
                }
                CombatFx.playCrit(h);
            } else {
                ElementFx.playProc(h);
            }
        }
        return crits;
    }

    /** 元素声的限频：每 100 ms 至多 6 个 */
    private static boolean takeElementSound() {
        long now = KuvaFxDirector.clockMs();
        long oldest = ELEMENT_SOUND_TIMES[elementSoundCursor];
        if (oldest != 0L && now - oldest < ELEMENT_SOUND_WINDOW_MS) {
            return false;
        }
        ELEMENT_SOUND_TIMES[elementSoundCursor] = Math.max(1L, now);
        elementSoundCursor = (elementSoundCursor + 1) % ELEMENT_SOUND_TIMES.length;
        return true;
    }

    /** 次音：不同家族里优先级最高的那个；只有「暴击 + 元素」或「爆炸 / 电 + 暴击」这两种组合才留，一档暴击不当次音 */
    private static Hit pickSecondary(Hit main, List<Hit> g) {
        boolean mainIsBigElement = main.kind == Kind.ELEMENT && main.priority() >= 8;
        if (main.kind != Kind.CRIT && !mainIsBigElement) {
            return null;
        }
        for (int i = 0; i < g.size(); i++) {
            Hit h = g.get(i);
            if (h == main || h.kind == main.kind || h.priority() <= 0) {
                continue;
            }
            if (h.kind == Kind.CRIT && h.variant == 1) {
                continue;
            }
            return h;
        }
        return null;
    }
}
