package pers.roinflam.kuvalich.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 赤毒实体的自然生成密度限制
 *
 * <p>原版自然生成是无记忆的按权重抽签：只要地方够暗、怪物上限没满，每个 tick 每个区块都在抽，
 * 抽到谁就刷谁。赤毒实体又不怕日晒、血厚不好清，所以黑处会一口气刷出一堆，而亮处、白天、
 * 怪物上限被别的怪占满的时候一只都没有。权重只能调「抽到的频率」，调不出「身边同时有几只」。</p>
 *
 * <p>这里补一道「在场数量」：生成点附近同类已经够多、或者离同类太近，就拒绝这次生成。
 * 有了它权重可以放心定得高一点（被杀了很快补上），但不会堆积。</p>
 *
 * <p>只管自然生成（{@code MobSpawnType.NATURAL}）。刷怪笼、刷怪蛋、安魂通牒、命令不受影响，
 * 但它们造出来的赤毒实体照样计入「附近同类」。</p>
 *
 * <p>在场名单由 {@link AbstractKuva#onAddedToWorld()} / {@link AbstractKuva#onRemovedFromWorld()}
 * 维护，只有几十只的量级，遍历比一次区域实体查询便宜得多（自然生成每秒会走很多次）。
 * 名单用线程安全的容器，万一有别的线程碰到也不会出错。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class KuvaSpawnLimiter {

    /**
     * 纵向只看这么远。洞穴层层叠叠，脚下几十格深或头顶几十格高的同类，
     * 和这个生成点之间隔着实心岩层，算不上「附近」。
     */
    private static final double VERTICAL_REACH = 48.0;

    /** 每个维度里现存的赤毒实体 */
    private static final Map<ServerLevel, CopyOnWriteArrayList<AbstractKuva>> LIVE = new ConcurrentHashMap<>();

    private KuvaSpawnLimiter() {}

    /**
     * 实体进入世界时登记（服务端）
     *
     * @param kuva 赤毒实体
     */
    public static void track(AbstractKuva kuva) {
        if (kuva.level() instanceof ServerLevel level) {
            LIVE.computeIfAbsent(level, l -> new CopyOnWriteArrayList<>()).addIfAbsent(kuva);
        }
    }

    /**
     * 实体离开世界时注销（死亡、被清除、区块卸载都会走到）
     *
     * @param kuva 赤毒实体
     */
    public static void untrack(AbstractKuva kuva) {
        if (kuva.level() instanceof ServerLevel level) {
            List<AbstractKuva> live = LIVE.get(level);
            if (live != null) {
                live.remove(kuva);
            }
        }
    }

    /**
     * 这个生成点还能不能再刷一只同类
     *
     * @param accessor  生成所在的世界
     * @param type      要刷的实体类型
     * @param pos       生成点
     * @param maxNearby 半径内最多已有几只（≤0 不限）
     * @param radius    数量统计半径（格）
     * @param spacing   与同类的最小间距（格，≤0 不限）
     * @return true 表示允许生成
     */
    public static boolean hasRoom(ServerLevelAccessor accessor, EntityType<?> type, BlockPos pos,
                                  int maxNearby, int radius, int spacing) {
        if (maxNearby <= 0 && spacing <= 0) {
            return true;
        }
        List<AbstractKuva> live = LIVE.get(accessor.getLevel());
        if (live == null || live.isEmpty()) {
            return true;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        double radiusSq = (double) radius * radius;
        double spacingSq = (double) spacing * spacing;

        int nearby = 0;
        for (AbstractKuva other : live) {
            if (other.isRemoved()) {
                // 正常都会走 onRemovedFromWorld 注销；万一有路径没走到，这里顺手清掉，免得名单只增不减
                live.remove(other);
                continue;
            }
            if (other.getType() != type || !other.isAlive()) {
                continue;
            }
            if (Math.abs(other.getY() - y) > VERTICAL_REACH) {
                continue;
            }
            double dx = other.getX() - x;
            double dz = other.getZ() - z;
            double distSq = dx * dx + dz * dz;
            if (spacing > 0 && distSq < spacingSq) {
                return false;
            }
            if (maxNearby > 0 && distSq <= radiusSq && ++nearby >= maxNearby) {
                return false;
            }
        }
        return true;
    }

    /**
     * 服务端关闭后清空名单。单人游戏每次进世界都是新的 ServerLevel 对象，
     * 不清的话上一局的维度会一直被名单拽着。
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LIVE.clear();
    }
}
