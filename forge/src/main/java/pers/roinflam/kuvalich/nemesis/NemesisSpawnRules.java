package pers.roinflam.kuvalich.nemesis;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 玄骸从哪儿来：只有玄骸导演生成的才是某个玩家的专属玄骸，其余一律是「无主」的野生玄骸。
 *
 * <p>自然生成、刷怪笼根本刷不出来，三道关哪一道都拦得住：</p>
 * <ol>
 *   <li>落点规则（{@code KuvaLichSpawnPlacements}）：只放行刷怪蛋、命令、发射器等「有人主动造」的来源；</li>
 *   <li>{@code MobSpawnEvent.PositionCheck}：刷怪笼的数据带自定义生成条件时，原版会跳过落点规则，
 *       Forge 在默认结果下也跳过，所以这里对刷怪笼一律 DENY。注意 Forge 47 给这个事件传的刷怪笼对象是 null，
 *       只能靠生成方式是 SPAWNER 来认；</li>
 *   <li>加入世界：记下的生成方式是自然生成、刷怪笼、结构、巡逻、增援、事件、骑乘的一律取消
 *       （别的模组「无视生成条件」的刷怪笼直接加进世界也挡得住）。</li>
 * </ol>
 * <p>刷怪蛋、命令、别的模组直接加进世界的按 {@code lichWildAllowed} 生成为无主玄骸或拒绝。
 * 老存档里以前自然生成、存在世界里的（没有 KuvaWild 标记）按 {@code lichPurgeLegacy} 在读档时拦下。
 * 演示用的目标一律跳过。拦的时候都用取消事件，不在事件里 discard()：那时实体已经算进世界，
 * 会多出一帧同步包和一次「移出世界」回调。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class NemesisSpawnRules {

    /** 这些来源的玄骸一律不让进世界 */
    private static final Set<MobSpawnType> BLOCKED = EnumSet.of(
            MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.SPAWNER, MobSpawnType.STRUCTURE,
            MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.EVENT, MobSpawnType.JOCKEY);

    /** 落点规则放行的来源：有人主动造的 */
    private static final Set<MobSpawnType> PLACEMENT_ALLOWED = EnumSet.of(
            MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND, MobSpawnType.DISPENSER, MobSpawnType.MOB_SUMMONED,
            MobSpawnType.BUCKET, MobSpawnType.CONVERSION);

    /** 这一 tick 读档时拦下的老玄骸，按区块合并成一行日志 */
    private static final Map<ChunkPos, Integer> PURGED = new LinkedHashMap<>();

    private NemesisSpawnRules() {
    }

    /** 落点规则：这种来源能不能走亮度判定（不能的一律不生成） */
    public static boolean placementAllows(MobSpawnType spawnType) {
        return PLACEMENT_ALLOWED.contains(spawnType);
    }

    /** 加入世界时，这种生成方式是不是一律拦下 */
    public static boolean isBlockedSource(@Nullable MobSpawnType spawnType) {
        return spawnType != null && BLOCKED.contains(spawnType);
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity() instanceof KuvaMasterEntity
                && (event.getSpawner() != null || event.getSpawnType() == MobSpawnType.SPAWNER)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof KuvaMasterEntity lich)) {
            return;
        }
        if (lich.isDemo() || lich.isOwned() || lich.isAlly()) {
            // 演示目标另有自己的上下文；主人、盟友只可能是导演在加进世界之前设好的
            return;
        }
        if (event.loadedFromDisk()) {
            if (lich.hasWildTag()) {
                return;
            }
            if (ModConfig.KUVA_LICH.lichPurgeLegacy.get()) {
                event.setCanceled(true);
                PURGED.merge(lich.chunkPosition(), 1, Integer::sum);
            } else {
                lich.markWild();
            }
            return;
        }
        if (isBlockedSource(lich.getFreshSpawnType())) {
            event.setCanceled(true);
            if (LogUtil.isDebugEnabled()) {
                LogUtil.debug("拦下一只来源为 " + lich.getFreshSpawnType() + " 的赤毒玄骸 @ " + lich.blockPosition());
            }
            return;
        }
        if (!ModConfig.KUVA_LICH.lichWildAllowed.get()) {
            event.setCanceled(true);
            return;
        }
        lich.markWild();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PURGED.isEmpty()) {
            return;
        }
        for (Map.Entry<ChunkPos, Integer> entry : PURGED.entrySet()) {
            LogUtil.info("读档时清掉了区块 " + entry.getKey() + " 里 " + entry.getValue()
                    + " 只以前自然生成的赤毒玄骸（lichPurgeLegacy）");
        }
        PURGED.clear();
    }
}
