package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 「它来了」的屏幕边缘提示：四边一圈淡淡的渐变光晕，一下亮起、很快落下，不出声
 *
 * <p>两种情况会亮：</p>
 * <ul>
 *   <li><b>自己的玄骸要到了</b>：玄骸状态进入「即将到场」的那 2 s 里，每秒一下；</li>
 *   <li><b>无主玄骸</b>：生成在附近、或走进 64 格，但还没进视野时（每 5 tick 看一次在不在视野里、有没有被挡），
 *       每秒一下，近到 24 格内变成每 0.5 秒一下；一旦被看见就不再提示，最长 15 s。</li>
 * </ul>
 *
 * <p>一下的形状：不到一帧亮到顶，133 ms 落到一半，233 ms 落到一成，400 ms 熄灭。光晕厚度左右是屏宽的 1.0%~1.7%、
 * 上下是屏高的 1.8%~3.7%，越近越厚。颜色用科技青：赤毒红和受伤红屏太像。
 * 选项 {@code lich_edge_pulse}（默认关）打开、且 {@code screen_effects} 开着才画。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichEdgePulse {

    /** 光晕颜色 */
    public static final int COLOR = FxPalette.TECH;
    /** 光晕最外沿的不透明度 */
    private static final float PEAK_ALPHA = 0.42F;
    private static final double WILD_RANGE = 64.0D;
    private static final double NEAR_RANGE = 24.0D;
    private static final int WILD_MAX_MS = 15_000;
    private static final int OWNED_MS = 2_000;
    private static final int TEST_MS = 3_000;

    /** 自己的玄骸到场 / 调试：从什么时候亮到什么时候 */
    private static long ownedStart = -1L;
    private static long ownedEnd = -1L;
    private static boolean ownedNear;
    private static LichState lastState = LichState.UNBORN;

    /** 无主玄骸：第一次在 64 格内发现的时刻；已经被看见过的 */
    private static final Int2LongOpenHashMap FIRST_SEEN_NEAR = new Int2LongOpenHashMap();
    private static final IntOpenHashSet NOTICED = new IntOpenHashSet();
    /** 最近一只还没被看见的无主玄骸的距离，没有是 −1 */
    private static double wildDist = -1.0D;
    private static long wildStart = -1L;
    private static int ticks;

    private LichEdgePulse() {
    }

    /** 演一遍给开发命令看：不看选项、不看附近有没有玄骸 */
    public static void test(boolean near) {
        ownedStart = KuvaFxDirector.clockMs();
        ownedEnd = ownedStart + TEST_MS;
        ownedNear = near;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        LichState st = ClientLichState.get().state();
        if (st == LichState.ARRIVING && lastState != LichState.ARRIVING && enabled()) {
            ownedStart = now;
            ownedEnd = now + OWNED_MS;
            ownedNear = false;
        } else if (lastState == LichState.ARRIVING && st != LichState.ARRIVING && ownedEnd > now) {
            // 到场了：把这一下演完就停
            ownedEnd = Math.min(ownedEnd, now + 400L);
        }
        lastState = st;

        if (++ticks % 5 != 0) {
            return;
        }
        if (!enabled()) {
            wildDist = -1.0D;
            return;
        }
        scanWild(mc, mc.player, now);
    }

    private static void scanWild(Minecraft mc, LocalPlayer player, long now) {
        double best = -1.0D;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof KuvaMasterEntity lich) || !lich.isWild() || !lich.isAlive()) {
                continue;
            }
            int id = lich.getId();
            if (NOTICED.contains(id)) {
                continue;
            }
            double d = lich.distanceTo(player);
            if (d > WILD_RANGE) {
                continue;
            }
            if (!FIRST_SEEN_NEAR.containsKey(id)) {
                FIRST_SEEN_NEAR.put(id, now);
            }
            if (inView(mc, player, lich)) {
                NOTICED.add(id);
                continue;
            }
            if (now - FIRST_SEEN_NEAR.get(id) > WILD_MAX_MS) {
                continue;
            }
            if (best < 0.0D || d < best) {
                best = d;
            }
        }
        if (best >= 0.0D && wildDist < 0.0D) {
            wildStart = now;
        }
        wildDist = best;
    }

    /** 在不在画面里、有没有被方块挡住 */
    private static boolean inView(Minecraft mc, LocalPlayer player, Entity e) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = e.position().add(0.0D, e.getBbHeight() * 0.6D, 0.0D).subtract(eye);
        double len = to.length();
        if (len < 1.0E-3D) {
            return true;
        }
        Vec3 fwd = player.getViewVector(1.0F);
        double z = to.dot(fwd);
        if (z <= 0.0D) {
            return false;
        }
        Vec3 right = fwd.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        double vHalf = Math.toRadians(mc.options.fov().get() * 0.5D);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double hHalf = Math.atan(Math.tan(vHalf) * aspect);
        // 包围盒有宽度，视角边缘放宽一点
        double margin = Math.atan2(e.getBbWidth(), len);
        if (Math.abs(Math.atan2(to.dot(right), z)) > hHalf + margin || Math.abs(Math.atan2(to.dot(up), z)) > vHalf + margin) {
            return false;
        }
        return player.hasLineOfSight(e);
    }

    private static boolean enabled() {
        return EffectsDisplayConfig.lichEdgePulse() && EffectsDisplayConfig.screenEffects();
    }

    /** 一下的亮度：不到一帧亮到顶，133 ms 一半，233 ms 一成，400 ms 熄灭 */
    private static float pulse(long msInPeriod) {
        float t = msInPeriod;
        if (t < 133.0F) {
            return 1.0F - 0.5F * t / 133.0F;
        }
        if (t < 233.0F) {
            return 0.5F - 0.4F * (t - 133.0F) / 100.0F;
        }
        if (t < 400.0F) {
            return 0.1F * (1.0F - (t - 233.0F) / 167.0F);
        }
        return 0.0F;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        long now = KuvaFxDirector.clockMs();
        float v = 0.0F;
        float prox = 0.5F;
        if (ownedEnd > now && ownedStart >= 0L) {
            long t = now - ownedStart;
            long period = ownedNear ? 500L : 1000L;
            v = pulse(t % period);
            prox = ownedNear ? 1.0F : 0.5F;
        }
        if (wildDist >= 0.0D && enabled()) {
            long period = wildDist < NEAR_RANGE ? 500L : 1000L;
            float w = pulse((now - wildStart) % period);
            if (w > v) {
                v = w;
                prox = (float) Mth.clamp((WILD_RANGE - wildDist) / (WILD_RANGE - NEAR_RANGE), 0.0D, 1.0D);
            }
        }
        if (v <= 0.003F) {
            return;
        }
        float side = width * (0.010F + 0.007F * prox);
        float edge = height * (0.018F + 0.019F * prox);
        float a = PEAK_ALPHA * v;
        float r = ((COLOR >> 16) & 0xFF) / 255.0F;
        float gr = ((COLOR >> 8) & 0xFF) / 255.0F;
        float b = (COLOR & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        // 上、下：竖直渐变
        quad(vc, m, 0, 0, width, edge, r, gr, b, a, a, 0.0F, 0.0F);
        quad(vc, m, 0, height - edge, width, height, r, gr, b, 0.0F, 0.0F, a, a);
        // 左、右：水平渐变
        quad(vc, m, 0, 0, side, height, r, gr, b, a, 0.0F, 0.0F, a);
        quad(vc, m, width - side, 0, width, height, r, gr, b, 0.0F, a, a, 0.0F);
        g.flush();
        RenderSystem.disableBlend();
    }

    /**
     * 一个矩形，四角各自的不透明度：左上、右上、右下、左下
     */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                             float r, float g, float b, float aTl, float aTr, float aBr, float aBl) {
        // 顶点顺序和 GuiGraphics#fill 一样：左上、左下、右下、右上
        vc.vertex(m, x0, y0, 0.0F).color(r, g, b, aTl).endVertex();
        vc.vertex(m, x0, y1, 0.0F).color(r, g, b, aBl).endVertex();
        vc.vertex(m, x1, y1, 0.0F).color(r, g, b, aBr).endVertex();
        vc.vertex(m, x1, y0, 0.0F).color(r, g, b, aTr).endVertex();
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            int id = event.getEntity().getId();
            FIRST_SEEN_NEAR.remove(id);
            NOTICED.remove(id);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FIRST_SEEN_NEAR.clear();
        NOTICED.clear();
        wildDist = -1.0D;
        ownedEnd = -1L;
        lastState = LichState.UNBORN;
    }

    /** 覆盖层注册在模组总线上（类名和同包的另一个覆盖层错开：Forge 按「类名_方法名_事件」生成调用类，撞名会只调其中一个） */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class EdgePulseRegistration {
        private EdgePulseRegistration() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll("lich_edge_pulse", LichEdgePulse::render);
        }
    }
}
