package pers.roinflam.kuvalich.client.renderer.damage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.network.packet.DamageInfo;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 伤害数字渲染器
 *
 * <p>暴击分档全在数字上，世界里不再另加特效：颜色直接写 RGB（白 {@code #FCFBFC}、护盾蓝 {@code #55FFFF}、
 * 一档黄 {@code #F8FA2E}、二档橙 {@code #E8833A}、三档红 {@code #E6131E}、紫 {@code #FF55FF}），
 * 黄 / 橙 / 红三档外面先描一圈暗边再画正文（白天亮背景上一档黄字也能一眼分出来），
 * 新建的暴击数字第一帧放大、100 ms 内缩回常驻字号，不回弹、首帧就是满不透明。</p>
 *
 * <p>字号：白 0.045、蓝和黄 0.057、橙 0.072、红 0.09，档与档之间约 1.26 / 1.58 倍；持续伤害（白色的元素伤害）再小一号、不弹。</p>
 *
 * <p>数字的坐标原点是相机位置：这个渲染阶段的矩阵栈以相机为原点。新建时在相机平面里随机偏一点、之后不变，
 * 每秒只上升 0.15 格，几条同时冒出来的数字不会排成一条竖线。</p>
 *
 * <p><b>合并</b>（{@code mergeDamageNumbers}，默认开）：同一只怪身上、合并窗口内的多条数字并成一条，
 * 数值累加、档位取最高、图标取并集；本模组的元素伤害单独分组（同元素才互相合并）。合并进来的不再弹。</p>
 *
 * <p>描边开销：每个彩色数字 9 次绘制；只给最新的 60 个彩色数字描边，再多的不描（满屏刷怪时看不出来）。
 * 渲染循环里不分配：字形序列和字宽都缓存在每条数字上。</p>
 */
@OnlyIn(Dist.CLIENT)
public class DamageRenderer {

    private final ArrayDeque<DamageInfo> damageInfos = new ArrayDeque<>();
    /** 调试用的停表时刻，小于 0 = 不停 */
    private static long devFreezeMs = -1L;

    /** 合并槽位：受击实体 + 分组 → 该分组当前还能接收合并的那条数字 */
    private final Map<DamageInfo.MergeKey, DamageInfo> mergeSlots = new HashMap<>();

    private static volatile DamageRenderer instance;

    /** 上升速度：0.15 格/秒 */
    private static final double RISE_PER_MS = 0.15D / 1000.0D;

    /** 新建时在相机平面里的随机偏移（格） */
    private static final double SPREAD = 0.25D;

    private static final int MAX_DAMAGE_INFOS = 500;

    /** 只给最新的这么多个彩色数字描边 */
    private static final int MAX_OUTLINED = 60;

    private static final int CLEANUP_INTERVAL = 20;

    private int cleanupTimer = 0;

    // 字号（白色是基准）
    private static final float SCALE_WHITE = 0.045F;
    private static final float SCALE_BLUE = 0.057F;
    private static final float SCALE_YELLOW = 0.057F;
    private static final float SCALE_ORANGE = 0.072F;
    private static final float SCALE_RED = 0.09F;
    /** 持续伤害数字再小一号 */
    private static final float DOT_SCALE = 0.85F;

    /** 第一帧放大的量（黄 / 橙 / 红），100 ms 缩回 */
    private static final float POP_YELLOW = 0.30F;
    private static final float POP_ORANGE = 0.35F;
    private static final float POP_RED = 0.40F;
    private static final float POP_MS = 100.0F;

    /** 紫字（真实伤害），和原来的颜色码不同：原来的暗紫在夜里看不清 */
    private static final int PURPLE = 0xFF55FF;
    private static final int BLUE = 0x55FFFF;

    /** 描边色：比原作粗、暗，白天亮背景上更显眼 */
    private static final int OUTLINE_YELLOW = 0x3E3F0B;
    private static final int OUTLINE_ORANGE = 0x3A2110;
    private static final int OUTLINE_RED = 0x3A0610;
    private static final float OUTLINE_ALPHA = 0.85F;

    /** 描边的 8 个方向（字体像素） */
    private static final float[] OUT_DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final float[] OUT_DY = {-1, -1, -1, 0, 0, 1, 1, 1};

    private static final int FULL_BRIGHT = 15728880;

    private final RandomSource random = RandomSource.create();

    private DamageRenderer() {
    }

    public static DamageRenderer getInstance() {
        if (instance == null) {
            synchronized (DamageRenderer.class) {
                if (instance == null) {
                    instance = new DamageRenderer();
                    MinecraftForge.EVENT_BUS.register(instance);
                }
            }
        }
        return instance;
    }

    /**
     * 收到一条伤害数字：能合并就并入已有的那条，不能就新起一条（客户端主线程调用）
     *
     * @param entityId        受击实体 ID；小于 0 表示未知，此时不参与合并
     * @param mergeGroup      合并分组；普通伤害为空串，元素伤害为元素名
     * @param amount          伤害数值
     * @param colorCode       颜色码（只用来判档位）
     * @param prefix          数字前缀，可为空串
     * @param suffix          数字后缀（图标串），可为空串
     * @param hitShield       是否打到护盾
     * @param position        显示位置
     * @param displayDuration 包里带的显示时长（不再使用，寿命由档位定）
     */
    public void addDamage(int entityId, @Nonnull String mergeGroup, float amount, @Nonnull String colorCode,
                          @Nonnull String prefix, @Nonnull String suffix, boolean hitShield,
                          @Nonnull Vec3 position, long displayDuration) {
        long now = now();

        DamageInfo.MergeKey key = null;
        long windowMs = 0L;
        if (entityId >= 0 && ModConfig.KUVA_LICH.mergeDamageNumbers.get()) {
            windowMs = ModConfig.KUVA_LICH.mergeDamageWindowMs.get();
            if (windowMs > 0L) {
                key = new DamageInfo.MergeKey(entityId, mergeGroup);
                DamageInfo slot = mergeSlots.get(key);
                if (slot != null && slot.canMergeAt(now)) {
                    slot.merge(amount, colorCode, prefix, suffix, hitShield, now);
                    return;
                }
            }
        }

        DamageInfo info = new DamageInfo(amount, colorCode, prefix, suffix, hitShield,
                position, now, key, mergeGroup, windowMs, EffectsDisplayConfig.damageNumberPop());
        scatter(info);
        addDamageInfo(info);
        if (key != null) {
            mergeSlots.put(key, info);
        }
    }

    /** 新建时在相机平面内随机偏 ±0.25 格 */
    private void scatter(DamageInfo info) {
        Camera cam = KuvaFxDirector.camera();
        if (cam == null) {
            return;
        }
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        double a = (random.nextDouble() * 2.0D - 1.0D) * SPREAD;
        double b = (random.nextDouble() * 2.0D - 1.0D) * SPREAD;
        info.offX = left.x() * a + up.x() * b;
        info.offY = left.y() * a + up.y() * b;
        info.offZ = left.z() * a + up.z() * b;
    }

    public void addDamageInfo(DamageInfo info) {
        if (info == null) {
            return;
        }
        if (damageInfos.size() >= MAX_DAMAGE_INFOS) {
            DamageInfo evicted = damageInfos.pollFirst();
            // 被挤掉的条目如果还占着合并槽位，一并释放，后续数字不会并进一条已经不显示的记录
            if (evicted != null && evicted.getMergeKey() != null) {
                mergeSlots.remove(evicted.getMergeKey(), evicted);
            }
        }
        damageInfos.addLast(info);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        if (++cleanupTimer >= CLEANUP_INTERVAL) {
            cleanupTimer = 0;
            cleanupExpired();
        }
    }

    private void cleanupExpired() {
        long now = now();
        if (!mergeSlots.isEmpty()) {
            mergeSlots.values().removeIf(info -> !info.canMergeAt(now));
        }
        if (damageInfos.isEmpty()) {
            return;
        }
        Iterator<DamageInfo> iterator = damageInfos.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.isClientSide) {
            renderDamageInfos(event.getPoseStack());
        }
    }

    /**
     * 画全部伤害数字。相机位置已由原版按 partialTick 插值，第三人称的镜头后移也处理好了
     */
    private void renderDamageInfos(PoseStack poseStack) {
        if (damageInfos.isEmpty() || MercyClient.hidesHud()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) {
            return;
        }
        long now = now();
        Vec3 cameraPos = camera.getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        float yaw = -camera.getYRot();
        float pitch = camera.getXRot();

        // 先数一遍彩色数字，只有最新的 MAX_OUTLINED 个描边
        int colored = 0;
        for (DamageInfo info : damageInfos) {
            if (info.tier() >= DamageInfo.TIER_YELLOW) {
                colored++;
            }
        }
        int outlineFrom = colored - MAX_OUTLINED;

        RenderSystem.disableDepthTest();
        // 分两遍画：先全部的描边 / 阴影，冲掉批次再画正文。文字的渲染类型上传时按远近重排四边形，
        // 同一批里描边、阴影和正文谁先谁后说不准，正文会被压成零星几个点
        for (int pass = 0; pass < 2; pass++) {
            renderPass(poseStack, bufferSource, font, now, cameraPos, yaw, pitch, outlineFrom, pass == 0);
            bufferSource.endBatch();
        }
        RenderSystem.enableDepthTest();
    }

    private void renderPass(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Font font, long now,
                            Vec3 cameraPos, float yaw, float pitch, int outlineFrom, boolean back) {
        int coloredIndex = 0;
        for (DamageInfo info : damageInfos) {
            int tier = info.tier();
            boolean isColored = tier >= DamageInfo.TIER_YELLOW;
            boolean outline = isColored && coloredIndex++ >= outlineFrom;
            float alpha = info.alphaAt(now);
            if (alpha < DamageInfo.MIN_ALPHA) {
                continue;
            }
            long age = info.ageMs(now);
            double rise = age * RISE_PER_MS;
            double x = info.position.x + info.offX - cameraPos.x;
            double y = info.position.y + info.offY - cameraPos.y + rise;
            double z = info.position.z + info.offZ - cameraPos.z;

            float scale = scaleOf(tier);
            if (info.isDot()) {
                scale *= DOT_SCALE;
            } else if (info.pop) {
                scale *= popScaleAt(tier, age);
            }

            if (info.width < 0) {
                info.width = font.width(info.body());
            }
            renderOne(poseStack, bufferSource, font, info, x, y, z, yaw, pitch, scale, alpha, outline, back);
        }
    }

    private void renderOne(PoseStack poseStack, MultiBufferSource bufferSource, Font font, DamageInfo info,
                           double x, double y, double z, float yaw, float pitch, float scale, float alpha,
                           boolean outline, boolean back) {
        int tier = info.tier();
        int rgb = colorOf(tier);
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.scale(-scale, -scale, scale);
            Matrix4f mat = poseStack.last().pose();
            float x0 = -info.width / 2.0F;
            if (back) {
                if (outline) {
                    int oa = Mth.clamp((int) (alpha * OUTLINE_ALPHA * 255.0F), 0, 255);
                    int oc = (oa << 24) | outlineOf(tier);
                    for (int i = 0; i < OUT_DX.length; i++) {
                        font.drawInBatch(info.outline(), x0 + OUT_DX[i], OUT_DY[i], oc, false, mat, bufferSource,
                                Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
                    }
                } else {
                    // 没描边的（白、蓝、紫）垫一层原版同款阴影（颜色 × 0.25，右下各偏一像素）压住背景
                    int sc = (a << 24) | ((rgb & 0xFCFCFC) >> 2);
                    font.drawInBatch(info.outline(), x0 + 1.0F, 1.0F, sc, false, mat, bufferSource,
                            Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
                }
            } else {
                font.drawInBatch(info.body(), x0, 0.0F, (a << 24) | rgb, false, mat, bufferSource,
                        Font.DisplayMode.SEE_THROUGH, 0, FULL_BRIGHT);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static float scaleOf(int tier) {
        return switch (tier) {
            case DamageInfo.TIER_BLUE -> SCALE_BLUE;
            case DamageInfo.TIER_YELLOW -> SCALE_YELLOW;
            case DamageInfo.TIER_ORANGE -> SCALE_ORANGE;
            case DamageInfo.TIER_RED -> SCALE_RED;
            default -> SCALE_WHITE;
        };
    }

    /**
     * 弹出阶段的放大倍数：刚出现时是 1 + k，按 easeOutCubic 单调回落，满 100 ms 正好回到 1，中间不回弹
     */
    public static float popScaleAt(int tier, long ageMs) {
        if (ageMs >= POP_MS) {
            return 1.0F;
        }
        float t = Math.max(0.0F, ageMs / POP_MS);
        float inv = 1.0F - t;
        float easeOutCubic = 1.0F - inv * inv * inv;
        return 1.0F + popOf(tier) * (1.0F - easeOutCubic);
    }

    /** 调试命令用：让数字的时钟停在 {@code ms} 这一刻（小于 0 恢复走动），弹出过程能逐帧截图 */
    public static void devFreeze(long ms) {
        devFreezeMs = ms;
    }

    /** 数字用的时钟：平时就是演出时钟 */
    public static long now() {
        return devFreezeMs >= 0L ? devFreezeMs : KuvaFxDirector.clockMs();
    }

    private static float popOf(int tier) {
        return switch (tier) {
            case DamageInfo.TIER_YELLOW -> POP_YELLOW;
            case DamageInfo.TIER_ORANGE -> POP_ORANGE;
            case DamageInfo.TIER_RED -> POP_RED;
            default -> 0.0F;
        };
    }

    private static int colorOf(int tier) {
        return switch (tier) {
            case DamageInfo.TIER_BLUE -> BLUE;
            case DamageInfo.TIER_PURPLE -> PURPLE;
            case DamageInfo.TIER_YELLOW -> FxPalette.CRIT_Y;
            case DamageInfo.TIER_ORANGE -> FxPalette.CRIT_O;
            case DamageInfo.TIER_RED -> FxPalette.CRIT_R;
            default -> FxPalette.DMG_WHITE;
        };
    }

    private static int outlineOf(int tier) {
        return switch (tier) {
            case DamageInfo.TIER_ORANGE -> OUTLINE_ORANGE;
            case DamageInfo.TIER_RED -> OUTLINE_RED;
            default -> OUTLINE_YELLOW;
        };
    }

    public void clear() {
        damageInfos.clear();
        mergeSlots.clear();
        cleanupTimer = 0;
    }

    public int size() {
        return damageInfos.size();
    }
}
