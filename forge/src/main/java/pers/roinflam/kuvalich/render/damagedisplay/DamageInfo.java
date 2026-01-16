package pers.roinflam.kuvalich.render.damagedisplay;

import net.minecraft.world.phys.Vec3;

/**
 * 伤害信息数据类（简化版，只存储文本）
 * Damage information data class (simplified, only stores text)
 */
public class DamageInfo {

    /** 显示文本（已包含§颜色代码和格式化） / Display text */
    public final String text;

    /** 世界坐标位置 / World position */
    public final Vec3 position;

    /** 结束时间（毫秒） / End time (milliseconds) */
    public final long endTime;

    /** 开始时间（毫秒） / Start time (milliseconds) */
    public final long startTime;

    /**
     * 构造函数
     */
    public DamageInfo(String text, Vec3 position, long endTime, long startTime) {
        this.text = text;
        this.position = position;
        this.endTime = endTime;
        this.startTime = startTime;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired(long currentTime) {
        return currentTime > endTime;
    }

    /**
     * 计算上升高度
     */
    public double getRiseOffset(long currentTime, double riseSpeed) {
        return (currentTime - startTime) * riseSpeed;
    }

    /**
     * 伤害颜色枚举（保留用于兼容）
     */
    public enum DamageColor {
        WHITE(0xFFFFFFFF),
        YELLOW(0xFFFFFF00),
        ORANGE(0xFFFFA500),
        RED(0xFFFF0000),
        BLUE(0xFF00FFFF);

        private final int color;

        DamageColor(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }
}