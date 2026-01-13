package pers.roinflam.kuvalich.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.roinflam.kuvalich.config.ModConfig;

/**
 * 日志工具类 - 双层日志系统
 * Log utility class - Dual-layer logging system
 *
 * 普通日志：始终输出（info/warn/error）
 * Normal logs: Always output (info/warn/error)
 * 详细日志：受配置控制（debug系列）
 * Detailed logs: Controlled by config (debug series)
 */
public class LogUtil {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    // ========== 普通日志（始终输出）/ Normal Logs (Always Output) ==========

    /**
     * 输出普通信息日志
     * Output normal info log
     *
     * @param message 日志消息 / log message
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * 输出警告日志
     * Output warning log
     *
     * @param message 警告消息 / warning message
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }

    /**
     * 输出错误日志
     * Output error log
     *
     * @param message 错误消息 / error message
     */
    public static void error(String message) {
        LOGGER.error(message);
    }

    /**
     * 输出错误日志（带异常堆栈）
     * Output error log (with exception stack)
     *
     * @param message 错误消息 / error message
     * @param throwable 异常对象 / exception object
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    // ========== 详细日志（配置控制）/ Detailed Logs (Config Controlled) ==========

    /**
     * 检查配置是否已加载
     * Check if config is loaded
     *
     * @return 配置是否可用 / whether config is available
     */
    private static boolean isConfigLoaded() {
        try {
            // 尝试访问配置，如果抛出异常则说明配置未加载
            // Try to access config, if exception is thrown then config is not loaded
            ModConfig.KUVA_LICH.enableDetailedLogging.get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * 输出详细调试日志
     * Output detailed debug log
     *
     * 仅在enableDetailedLogging=true时输出
     * Only output when enableDetailedLogging=true
     *
     * @param message 调试消息 / debug message
     */
    public static void debug(String message) {
        // ✅ 添加配置加载检查
        if (isConfigLoaded() && ModConfig.KUVA_LICH.enableDetailedLogging.get()) {
            LOGGER.info("[详细] " + message);
        }
    }

    /**
     * 输出详细计算日志
     * Output detailed calculation log
     *
     * 用于记录数值计算过程
     * Used to record numerical calculation process
     *
     * @param type 计算类型（如"伤害计算"、"等级计算"）/ calculation type
     * @param entity 实体名称 / entity name
     * @param base 基础数值 / base value
     * @param modifier 修正值 / modifier value
     * @param result 最终结果 / final result
     */
    public static void debugCalculation(String type, String entity,
                                        double base, double modifier, double result) {
        // ✅ 添加配置加载检查
        if (isConfigLoaded() && ModConfig.KUVA_LICH.enableDetailedLogging.get()) {
            LOGGER.info(String.format("[详细][%s] 实体: %s, 基础: %.2f, 修正: %.2f, 结果: %.2f",
                    type, entity, base, modifier, result));
        }
    }

    /**
     * 输出详细事件日志
     * Output detailed event log
     *
     * 用于记录游戏事件触发情况
     * Used to record game event triggers
     *
     * @param event 事件名称 / event name
     * @param entity 实体名称 / entity name
     * @param details 详细信息 / details
     */
    public static void debugEvent(String event, String entity, String details) {
        // ✅ 添加配置加载检查
        if (isConfigLoaded() && ModConfig.KUVA_LICH.enableDetailedLogging.get()) {
            LOGGER.info(String.format("[详细][事件] %s - 实体: %s, 详情: %s",
                    event, entity, details));
        }
    }

    /**
     * 输出详细物品日志
     * Output detailed item log
     *
     * 用于记录物品操作和生成
     * Used to record item operations and generation
     *
     * @param action 操作类型（如"生成"、"掉落"）/ action type
     * @param item 物品名称 / item name
     * @param details 详细信息 / details
     */
    public static void debugItem(String action, String item, String details) {
        // ✅ 添加配置加载检查
        if (isConfigLoaded() && ModConfig.KUVA_LICH.enableDetailedLogging.get()) {
            LOGGER.info(String.format("[详细][物品] %s - 物品: %s, 详情: %s",
                    action, item, details));
        }
    }

    /**
     * 输出详细解密日志
     * Output detailed decryption log
     *
     * 用于记录赤毒玄骸解密过程
     * Used to record Kuva Lich decryption process
     *
     * @param stage 解密阶段 / decryption stage
     * @param progress 当前进度 / current progress
     * @param details 详细信息 / details
     */
    public static void debugDecryption(String stage, int progress, String details) {
        // ✅ 添加配置加载检查
        if (isConfigLoaded() && ModConfig.KUVA_LICH.enableDetailedLogging.get()) {
            LOGGER.info(String.format("[详细][解密] 阶段: %s, 进度: %d, 详情: %s",
                    stage, progress, details));
        }
    }
}