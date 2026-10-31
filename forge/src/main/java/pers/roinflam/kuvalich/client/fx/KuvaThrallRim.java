package pers.roinflam.kuvalich.client.fx;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 奴仆身上的描边闪：挨到固定减伤时描边短暂变亮
 *
 * <p>{@link #alphaBoost} 返回闪的强度 0~1（0 = 没在闪）：描边层按它把透明度从平时的 0.45~0.6 往 0.9 拉。
 * 一次闪前六成时间保持最亮，后四成落回。按实体 id 记，实体离开客户端世界时清掉。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaThrallRim {

    /** 闪的时候描边抬到的透明度 */
    public static final float FLASH_ALPHA = 0.9F;

    private static final Int2LongOpenHashMap START = new Int2LongOpenHashMap();
    private static final Int2IntOpenHashMap LENGTH = new Int2IntOpenHashMap();

    private KuvaThrallRim() {
    }

    /** 让这只奴仆的描边闪 {@code ms} 毫秒 */
    public static void flash(int entityId, int ms) {
        if (ms <= 0) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (START.size() > 64) {
            // 一次倒一片时这里会攒很多，顺手清掉闪完的
            START.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > LENGTH.get(e.getIntKey()));
            LENGTH.keySet().retainAll(START.keySet());
        }
        START.put(entityId, now);
        LENGTH.put(entityId, ms);
    }

    /** 描边此刻闪的强度 0~1；0 = 没有在闪 */
    public static float alphaBoost(int entityId) {
        int ms = LENGTH.get(entityId);
        if (ms <= 0) {
            return 0.0F;
        }
        long t = KuvaFxDirector.clockMs() - START.get(entityId);
        if (t < 0L || t >= ms) {
            return 0.0F;
        }
        float hold = ms * 0.6F;
        return t < hold ? 1.0F : 1.0F - (t - hold) / (ms - hold);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            int id = event.getEntity().getId();
            START.remove(id);
            LENGTH.remove(id);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        START.clear();
        LENGTH.clear();
    }
}
