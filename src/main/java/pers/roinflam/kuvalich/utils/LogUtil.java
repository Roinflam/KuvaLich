package pers.roinflam.kuvalich.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;

/**
 * 日志工具类 - 双层日志系统
 *
 * 普通日志：始终输出（info/warn/error）
 * 详细日志：受配置控制（debug系列）
 *
 * @author RoinFlam
 */
public class LogUtil {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    // ========== 普通日志（始终输出）==========

    /**
     * 输出普通信息日志
     * @param message 日志消息
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * 输出警告日志
     * @param message 警告消息
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }

    /**
     * 输出错误日志
     * @param message 错误消息
     */
    public static void error(String message) {
        LOGGER.error(message);
    }

    /**
     * 输出错误日志（带异常堆栈）
     * @param message 错误消息
     * @param throwable 异常对象
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    // ========== 详细日志（配置控制）==========

    /**
     * 输出详细调试日志
     * 仅在enableDetailedLogging=true时输出
     * @param message 调试消息
     */
    public static void debug(String message) {
        if (ConfigKuvaLich.enableDetailedLogging) {
            LOGGER.info("[详细] " + message);
        }
    }

    /**
     * 输出详细计算日志
     * 用于记录数值计算过程
     * @param type 计算类型（如"伤害计算"、"等级计算"）
     * @param entity 实体名称
     * @param base 基础数值
     * @param modifier 修正值
     * @param result 最终结果
     */
    public static void debugCalculation(String type, String entity,
                                        double base, double modifier, double result) {
        if (ConfigKuvaLich.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][%s] 实体: %s, 基础: %.2f, 修正: %.2f, 结果: %.2f",
                    type, entity, base, modifier, result));
        }
    }

    /**
     * 输出详细事件日志
     * 用于记录游戏事件触发情况
     * @param event 事件名称
     * @param entity 实体名称
     * @param details 详细信息
     */
    public static void debugEvent(String event, String entity, String details) {
        if (ConfigKuvaLich.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][事件] %s - 实体: %s, 详情: %s",
                    event, entity, details));
        }
    }

    /**
     * 输出详细物品日志
     * 用于记录物品操作和生成
     * @param action 操作类型（如"生成"、"掉落"）
     * @param item 物品名称
     * @param details 详细信息
     */
    public static void debugItem(String action, String item, String details) {
        if (ConfigKuvaLich.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][物品] %s - 物品: %s, 详情: %s",
                    action, item, details));
        }
    }

    /**
     * 输出详细解密日志
     * 用于记录赤毒玄骸解密过程
     * @param stage 解密阶段
     * @param progress 当前进度
     * @param details 详细信息
     */
    public static void debugDecryption(String stage, int progress, String details) {
        if (ConfigKuvaLich.enableDetailedLogging) {
            LOGGER.info(String.format("[详细][解密] 阶段: %s, 进度: %d, 详情: %s",
                    stage, progress, details));
        }
    }
}