package pers.roinflam.kuvalich.fx;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 服务端延时队列：让奖励、聊天这些「结果」落在客户端演出的高潮之后
 *
 * <p>任务在服务端 tick 末尾到点执行，执行时实体可能已经死了，所以任务自己记好世界、坐标、玩家。
 * 为了奖励不会因为延时丢掉：玩家下线时立刻执行他名下的全部任务（在存档之前），关服时执行全部。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ServerFxQueue {

    private record Task(long runAt, @Nullable UUID owner, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long ticks;

    private ServerFxQueue() {
    }

    /**
     * {@code delayTicks} 个服务端 tick 之后执行 {@code action}
     *
     * @param owner 任务属于哪个玩家（他下线时立刻执行）；可以为 null
     */
    public static void schedule(int delayTicks, @Nullable ServerPlayer owner, Runnable action) {
        TASKS.add(new Task(ticks + Math.max(1, delayTicks), owner == null ? null : owner.getUUID(), action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.runAt() <= ticks) {
                due.add(t);
                it.remove();
            }
        }
        due.forEach(ServerFxQueue::run);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        List<Task> mine = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (id.equals(t.owner())) {
                mine.add(t);
                it.remove();
            }
        }
        mine.forEach(ServerFxQueue::run);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        List<Task> all = new ArrayList<>(TASKS);
        TASKS.clear();
        all.forEach(ServerFxQueue::run);
        ticks = 0;
    }

    private static void run(Task t) {
        try {
            t.action().run();
        } catch (RuntimeException e) {
            LogUtil.error("延时任务执行出错", e);
        }
    }
}
