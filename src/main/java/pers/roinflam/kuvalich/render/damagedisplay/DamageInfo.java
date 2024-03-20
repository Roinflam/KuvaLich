package pers.roinflam.kuvalich.render.damagedisplay;

import net.minecraft.util.math.Vec3d;

public class DamageInfo {
    public float damage;
    public int color;
    public Vec3d position;
    public long endTime;
    public long startTime;

    public DamageInfo(float damage, int color, Vec3d position, long endTime, long startTime) {
        this.damage = damage;
        this.color = color;
        this.position = position;
        this.endTime = endTime;
        this.startTime = startTime;
    }

    public enum DamageColor {
        WHITE(0xFFFFFFFF),
        YELLOW(0xFFFFFF00),
        ORANGE(0xFFFFA500),
        RED(0xFFFF0000),
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
