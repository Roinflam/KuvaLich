package pers.roinflam.kuvalich.render.damagedisplay;

import net.minecraft.util.math.Vec3d;

/**
 * 伤害信息数据类（不可变）
 *
 * 存储单个伤害显示的所有信息

 */
public class DamageInfo {
    /** 伤害数值 */
    public final float damage;

    /** 显示颜色 */
    public final int color;

    /** 世界坐标位置 */
    public final Vec3d position;

    /** 结束时间（毫秒） */
    public final long endTime;

    /** 开始时间（毫秒） */
    public final long startTime;

    /**
     * 构造伤害信息
     *
     * @param damage 伤害数值
     * @param color 显示颜色
     * @param position 世界坐标
     * @param endTime 结束时间
     * @param startTime 开始时间
     */
    public DamageInfo(float damage, int color, Vec3d position, long endTime, long startTime) {
        this.damage = damage;
        this.color = color;
        this.position = position;
        this.endTime = endTime;
        this.startTime = startTime;
    }

    /**
     * 检查是否过期
     *
     * @param currentTime 当前时间
     * @return true表示已过期
     */
    public boolean isExpired(long currentTime) {
        return currentTime > endTime;
    }

    /**
     * 计算上升高度
     *
     * @param currentTime 当前时间
     * @param riseSpeed 上升速度
     * @return 上升高度
     */
    public double getRiseOffset(long currentTime, double riseSpeed) {
        return (currentTime - startTime) * riseSpeed;
    }

    /**
     * 伤害颜色枚举
     */
    public enum DamageColor {
        /** 白色 - 普通伤害 */
        WHITE(0xFFFFFFFF),

        /** 黄色 - 暴击伤害 */
        YELLOW(0xFFFFFF00),

        /** 橙色 - 高额伤害 */
        ORANGE(0xFFFFA500),

        /** 红色 - 超高伤害 */
        RED(0xFFFF0000),

        /** 蓝色 - 魔法伤害 */
        BLUE(0x00FFFF);

        private final int color;

        DamageColor(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }
}