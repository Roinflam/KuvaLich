// 文件：DamageRenderer.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/render/damagedisplay/DamageRenderer.java

package pers.roinflam.kuvalich.render.damagedisplay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 伤害数字渲染器（1.20.1版本，高性能）
 * Damage number renderer (1.20.1 version, high performance)
 */
@OnlyIn(Dist.CLIENT)
public class DamageRenderer {

    private final List<DamageInfo> damageInfos = new ArrayList<>();
    private static volatile DamageRenderer instance;
    private static final double RISE_SPEED = 1.0 / 4000.0;
    private static final int MAX_DAMAGE_INFOS = 500;
    private static final int CLEANUP_INTERVAL = 20;
    private int cleanupTimer = 0;

    // ✅ 修改：所有字体放大50%（乘以1.5）
    private static final float SCALE_WHITE = 0.03F * 1.5F;           // 普通伤害
    private static final float SCALE_YELLOW_BLUE = 0.038F * 1.5F;    // 暴击/护盾
    private static final float SCALE_ORANGE = 0.048F * 1.5F;         // 2倍暴击
    private static final float SCALE_RED = 0.06F * 1.5F;             // 3倍暴击

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

    public void addDamageInfo(DamageInfo info) {
        if (info == null) {
            return;
        }

        if (damageInfos.size() >= MAX_DAMAGE_INFOS) {
            damageInfos.remove(0);
        }

        damageInfos.add(info);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            clear();
            DiggingSpeedPacket.cleanupCache();
            return;
        }

        if (++cleanupTimer >= CLEANUP_INTERVAL) {
            cleanupTimer = 0;
            cleanupExpired();
        }
    }

    private void cleanupExpired() {
        if (damageInfos.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        damageInfos.removeIf(info -> info.isExpired(currentTime));
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.isClientSide) {
            renderDamageInfos(event.getPoseStack(), event.getPartialTick());
        }
    }

    private void renderDamageInfos(PoseStack poseStack, float partialTicks) {
        if (damageInfos.isEmpty()) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        double playerX = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double playerY = player.yOld + (player.getY() - player.yOld) * partialTicks;
        double playerZ = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        for (DamageInfo info : damageInfos) {
            if (info.isExpired(currentTime)) {
                continue;
            }

            double riseOffset = info.getRiseOffset(currentTime, RISE_SPEED);

            double x = info.position.x - playerX;
            double y = info.position.y - playerY + riseOffset;
            double z = info.position.z - playerZ;

            renderDamageText(poseStack, bufferSource, info.damage, info.color, x, y, z);
        }

        bufferSource.endBatch();
    }

    private void renderDamageText(PoseStack poseStack, MultiBufferSource bufferSource,
                                  float damage, int color, double x, double y, double z) {
        EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Font font = Minecraft.getInstance().font;

        float scale = getScaleByColor(color);

        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);

            poseStack.mulPose(Axis.YP.rotationDegrees(-renderDispatcher.camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(renderDispatcher.camera.getXRot()));

            poseStack.scale(-scale, -scale, scale);

            String damageText = String.format("%.1f", damage);

            int stringWidth = font.width(damageText);

            font.drawInBatch(
                    damageText,
                    -stringWidth / 2.0f,
                    0,
                    color,
                    false,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880,
                    true
            );
        } finally {
            poseStack.popPose();
        }
    }

    private float getScaleByColor(int color) {
        if (color == DamageInfo.DamageColor.WHITE.getColor()) {
            return SCALE_WHITE;
        } else if (color == DamageInfo.DamageColor.YELLOW.getColor() ||
                color == DamageInfo.DamageColor.BLUE.getColor()) {
            return SCALE_YELLOW_BLUE;
        } else if (color == DamageInfo.DamageColor.ORANGE.getColor()) {
            return SCALE_ORANGE;
        } else if (color == DamageInfo.DamageColor.RED.getColor()) {
            return SCALE_RED;
        }
        return SCALE_WHITE;
    }

    public void clear() {
        damageInfos.clear();
        cleanupTimer = 0;
    }

    public int size() {
        return damageInfos.size();
    }
}