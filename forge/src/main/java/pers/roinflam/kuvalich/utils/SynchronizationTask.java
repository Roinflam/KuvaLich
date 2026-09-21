package pers.roinflam.kuvalich.utils;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步任务调度器
 * Synchronous task scheduler
 *
 * 用于在服务器tick中延迟执行或周期执行任务。
 * 使用单一全局监听器管理所有任务，避免每个任务单独注册事件总线的性能开销。
 *
 * <p>⭐ 性能优化（本次）：{@code TaskManager.onServerTick} 原先每 tick 都无条件
 * {@code new ArrayList<>()} 来收集待删任务，即使本 tick 一个任务都没结束。
 * 元素 DOT 场景下任务数常年不为空，等于每 tick 白送一个短命对象给 GC。
 * 现改为惰性分配：只有真的有任务完成时才创建列表。</p>
 *
 * <p>使用示例 / Usage examples:</p>
 * <pre>
 * // 延迟20tick后执行一次
 * new SynchronizationTask(20) {
 *     &#64;Override
 *     public void run() {
 *         // 执行逻辑
 *     }
 * }.start();
 *
 * // 延迟10tick后开始，每5tick执行一次（周期任务）
 * new SynchronizationTask(10, 5) {
 *     &#64;Override
 *     public void run() {
 *         // 周期执行逻辑
 *     }
 * }.start();
 * </pre>
 */
public abstract class SynchronizationTask implements Runnable {

    /** 全局任务管理器（单例）/ Global task manager (singleton) */
    private static final TaskManager MANAGER = new TaskManager();

    /** 任务ID生成器 / Task ID generator */
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    /** 任务唯一ID / Task unique ID */
    protected final int taskId;

    /** 是否为周期任务 / Whether it's a periodic task */
    protected final boolean cycle;

    /** 初始延迟（tick）/ Initial delay (ticks) */
    protected final int initialDelay;

    /** 周期间隔（tick），-1表示一次性任务 / Period interval (ticks), -1 for one-time task */
    protected final int delay;

    /** 是否已启动 / Whether started */
    protected volatile boolean started = false;

    /** 是否为首次执行前 / Whether before first execution */
    protected boolean first = true;

    /** 当前计时器 / Current timer */
    protected int tick = 0;

    /**
     * 创建立即执行的一次性任务
     * Create an immediately executing one-time task
     */
    public SynchronizationTask() {
        this(0);
    }

    /**
     * 创建延迟执行的一次性任务
     *
     * @param initialDelay 初始延迟（tick），0表示下一tick执行
     */
    public SynchronizationTask(int initialDelay) {
        this(initialDelay, -1);
    }

    /**
     * 创建延迟执行的周期任务
     *
     * @param initialDelay 初始延迟（tick）
     * @param delay 周期间隔（tick），>=0表示周期任务，-1表示一次性任务
     */
    public SynchronizationTask(int initialDelay, int delay) {
        this.taskId = ID_GENERATOR.incrementAndGet();
        this.cycle = delay >= 0;
        this.initialDelay = initialDelay;
        this.delay = delay;
    }

    /**
     * 根据任务ID取消任务（静态方法）
     *
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public static boolean cancel(int taskId) {
        return MANAGER.cancelTask(taskId);
    }

    /**
     * 获取任务ID
     *
     * @return 任务ID
     */
    public int getTaskId() {
        return taskId;
    }

    /**
     * 启动任务
     */
    public synchronized void start() {
        if (!started) {
            started = true;
            MANAGER.addTask(this);
        }
    }

    /**
     * 取消任务
     * 可在run()方法内调用以停止周期任务
     */
    public synchronized void cancel() {
        if (started) {
            started = false;
            MANAGER.removeTask(taskId);
        }
    }

    /**
     * 内部tick处理（由TaskManager调用）
     *
     * @return true表示任务已完成需要移除，false表示继续执行
     */
    boolean onTick() {
        // 任务已被取消
        if (!started) {
            return true;
        }

        tick++;

        if (first) {
            // 首次执行前：等待初始延迟
            if (tick >= initialDelay) {
                first = false;
                tick = 0;

                // 执行任务（⭐ 修复 5：异常走 LogUtil，日志带模组前缀，方便排查；任务本身照常继续或结束）
                try {
                    this.run();
                } catch (Exception e) {
                    LogUtil.error("[定时任务] 任务 #" + taskId + " 首次执行时抛出异常", e);
                }

                // 一次性任务执行后结束
                if (!cycle) {
                    started = false;
                    return true;
                }
            }
        } else {
            // 周期执行
            if (cycle && tick >= delay) {
                tick = 0;

                try {
                    this.run();
                } catch (Exception e) {
                    LogUtil.error("[定时任务] 周期任务 #" + taskId + " 执行时抛出异常，下一周期将继续", e);
                }
            }
        }

        // 检查是否在run()中被取消
        return !started;
    }

    /**
     * 全局任务管理器
     * Global task manager
     *
     * 使用单一事件监听器管理所有任务，相比每个任务单独注册：
     * - 100个任务只有1个监听器，而不是100个
     * - 减少事件总线的遍历开销
     * - 线程安全
     */
    @Mod.EventBusSubscriber
    private static class TaskManager {

        /** 任务列表（线程安全）/ Task list (thread-safe) */
        private final Map<Integer, SynchronizationTask> tasks = new ConcurrentHashMap<>();

        /** 是否已注册到事件总线 / Whether registered to event bus */
        private volatile boolean registered = false;

        /**
         * 添加任务
         */
        void addTask(@Nonnull SynchronizationTask task) {
            tasks.put(task.getTaskId(), task);
            ensureRegistered();
        }

        /**
         * 移除任务
         */
        void removeTask(int taskId) {
            tasks.remove(taskId);
        }

        /**
         * 根据ID取消任务
         */
        boolean cancelTask(int taskId) {
            SynchronizationTask task = tasks.remove(taskId);
            if (task != null) {
                task.started = false;
                return true;
            }
            return false;
        }

        /**
         * 确保已注册到事件总线（只注册一次）
         */
        private synchronized void ensureRegistered() {
            if (!registered) {
                registered = true;
                MinecraftForge.EVENT_BUS.register(this);
            }
        }

        /**
         * 服务器tick事件处理
         *
         * <p>⭐ 待删列表惰性分配：本 tick 无任务结束时不产生任何对象。</p>
         */
        @SubscribeEvent
        public void onServerTick(@Nonnull TickEvent.ServerTickEvent evt) {
            // 只在START阶段处理（与原版一致）
            if (evt.phase != TickEvent.Phase.START) {
                return;
            }

            // 无任务时跳过
            if (tasks.isEmpty()) {
                return;
            }

            // ⭐ 惰性分配：只有真的有任务完成时才创建列表
            List<Integer> toRemove = null;

            for (Map.Entry<Integer, SynchronizationTask> entry : tasks.entrySet()) {
                SynchronizationTask task = entry.getValue();

                // onTick()返回true表示任务已完成
                if (task.onTick()) {
                    if (toRemove == null) {
                        toRemove = new ArrayList<>();
                    }
                    toRemove.add(entry.getKey());
                }
            }

            // 批量移除已完成的任务
            if (toRemove != null) {
                for (Integer taskId : toRemove) {
                    tasks.remove(taskId);
                }
            }
        }
    }
}
