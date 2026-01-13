package pers.roinflam.kuvalich.render.damagedisplay;

import net.minecraft.world.phys.Vec3;

/**
 * 伤害信息数据类（不可变）
 * Damage information data class (immutable)
 *
 * 存储单个伤害显示的所有信息
 * Stores all information for a single damage display
 */
public class DamageInfo {

    /** 伤害数值 / Damage value */
    public final float damage;

    /** 显示颜色 / Display color */
    public final int color;

    /** 世界坐标位置 / World position */
    public final Vec3 position;

    /** 结束时间（毫秒）/ End time (milliseconds) */
    public final long endTime;

    /** 开始时间（毫秒）/ Start time (milliseconds) */
    public final long startTime;

    /**
     * 构造伤害信息
     * Construct damage info
     *
     * @param damage 伤害数值 / damage value
     * @param color 显示颜色 / display color
     * @param position 世界坐标 / world position
     * @param endTime 结束时间 / end time
     * @param startTime 开始时间 / start time
     */
    public DamageInfo(float damage, int color, Vec3 position, long endTime, long startTime) {
        this.damage = damage;
        this.color = color;
        this.position = position;
        this.endTime = endTime;
        this.startTime = startTime;
    }

    /**
     * 检查是否过期
     * Check if expired
     *
     * @param currentTime 当前时间 / current time
     * @return true表示已过期 / true if expired
     */
    public boolean isExpired(long currentTime) {
        return currentTime > endTime;
    }

    /**
     * 计算上升高度
     * Calculate rise offset
     *
     * @param currentTime 当前时间 / current time
     * @param riseSpeed 上升速度 / rise speed
     * @return 上升高度 / rise offset
     */
    public double getRiseOffset(long currentTime, double riseSpeed) {
        return (currentTime - startTime) * riseSpeed;
    }

    /**
     * 伤害颜色枚举
     * Damage color enum
     */
    public enum DamageColor {
        /** 白色 - 普通伤害 / White - Normal damage */
        WHITE(0xFFFFFFFF),

        /** 黄色 - 暴击伤害 / Yellow - Critical damage */
        YELLOW(0xFFFFFF00),

        /** 橙色 - 高额伤害 / Orange - High damage */
        ORANGE(0xFFFFA500),

        /** 红色 - 超高伤害 / Red - Very high damage */
        RED(0xFFFF0000),

        /** 蓝色 - 魔法伤害 / Blue - Magic damage */
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