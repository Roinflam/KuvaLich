package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 开发用：逐帧把自己身上那份幻纹的跟随状态写进游戏目录下的 csv，量跟随顺不顺。
 * 池心 / 盘旋中心这类各款自己的挂点按字段名反射取，没有就留空。
 */
public final class EphemeraTrace {

    static boolean on;
    private static BufferedWriter out;
    private static int left;

    private EphemeraTrace() {
    }

    static synchronized String start(String name, int frames) {
        stop();
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("ephemera-trace").resolve(name + ".csv");
        try {
            Files.createDirectories(p.getParent());
            out = Files.newBufferedWriter(p, StandardCharsets.UTF_8);
            out.write("ns,clock,dt,pt,x,y,z,tickY,ground,groundY,velX,velZ,rawVx,rawVz,ax,ay,az\n");
        } catch (IOException ex) {
            out = null;
            return "trace failed: " + ex;
        }
        left = frames;
        on = true;
        return p.toString();
    }

    static synchronized void stop() {
        on = false;
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
            }
            out = null;
        }
    }

    static synchronized void record(EphemeraInstance inst, EphemeraHost h, LivingEntity le, float pt, long clock) {
        if (out == null || !(inst.styleState instanceof ProtoStyle.State st) || st.m == null) {
            return;
        }
        FxMotion m = st.m;
        Object s = inst.styleState;
        double ax = field(s, "cx"), ay = field(s, "cy"), az = field(s, "cz");
        if (Double.isNaN(ax)) {
            double ox = field(s, "offX");
            if (!Double.isNaN(ox)) {
                ax = m.x + ox;
                az = m.z + field(s, "offZ");
            }
        }
        try {
            out.write(String.format(Locale.ROOT, "%d,%d,%.5f,%.4f,%.5f,%.5f,%.5f,%.5f,%d,%.5f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s%n",
                    System.nanoTime(), clock, m.dt, pt, h.x(), h.y(), h.z(), le.getY(), h.onGround() ? 1 : 0, m.groundY,
                    m.velX, m.velZ, h.vx() * 20, h.vz() * 20, num(ax), num(ay), num(az)));
        } catch (IOException ex) {
            stop();
            return;
        }
        if (--left <= 0) {
            stop();
        }
    }

    private static String num(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.5f", v);
    }

    private static double field(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return ((Number) f.get(o)).doubleValue();
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | ClassCastException ex) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }
}
