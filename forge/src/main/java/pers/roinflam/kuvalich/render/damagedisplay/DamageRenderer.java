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
 *
 * 渲染特性：
 * Rendering features:
 * - 字体放大50%，更清晰可见
 * - 使用SEE_THROUGH模式，文字始终显示在最上层，不会被实体遮挡
 * - Font enlarged by 50% for better visibility
 * - Uses SEE_THROUGH mode, text always renders on top, won't be blocked by entities
 */
@OnlyIn(Dist.CLIENT)
public class DamageRenderer {

    private final List<DamageInfo> damageInfos = new ArrayList<>();
    private static volatile DamageRenderer instance;
    private static final double RISE_SPEED = 1.0 / 4000.0;
    private static final int MAX_DAMAGE_INFOS = 500;
    private static final int CLEANUP_INTERVAL = 20;
    private int cleanupTimer = 0;

    // ✅ 字体放大50%（乘以1.5）
    // Font enlarged by 50% (multiply by 1.5)
    private static final float SCALE_WHITE = 0.03F * 1.5F;           // 普通伤害 / Normal damage
    private static final float SCALE_YELLOW_BLUE = 0.038F * 1.5F;    // 暴击/护盾 / Critical/Shield
    private static final float SCALE_ORANGE = 0.048F * 1.5F;         // 2倍暴击 / 2x Critical
    private static final float SCALE_RED = 0.06F * 1.5F;             // 3倍暴击 / 3x Critical

    private DamageRenderer() {
    }

    /**
     * 获取单例实例
     * Get singleton instance
     */
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
     * 添加伤害信息
     * Add damage info
     */
    public void addDamageInfo(DamageInfo info) {
        if (info == null) {
            return;
        }

        // 限制最大数量，防止内存溢出
        // Limit max count to prevent memory overflow
        if (damageInfos.size() >= MAX_DAMAGE_INFOS) {
            damageInfos.remove(0);
        }

        damageInfos.add(info);
    }

    /**
     * 客户端Tick事件处理
     * Client tick event handler
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // 世界为空时清理所有数据
        // Clear all data when world is null
        if (mc.level == null) {
            clear();
            DiggingSpeedPacket.cleanupCache();
            return;
        }

        // 定期清理过期的伤害信息
        // Periodically cleanup expired damage info
        if (++cleanupTimer >= CLEANUP_INTERVAL) {
            cleanupTimer = 0;
            cleanupExpired();
        }
    }

    /**
     * 清理过期的伤害信息
     * Cleanup expired damage info
     */
    private void cleanupExpired() {
        if (damageInfos.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        damageInfos.removeIf(info -> info.isExpired(currentTime));
    }

    /**
     * 渲染关卡阶段事件处理
     * Render level stage event handler
     */
    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        // 在粒子渲染之后绘制伤害数字
        // Draw damage numbers after particles
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.isClientSide) {
            renderDamageInfos(event.getPoseStack(), event.getPartialTick());
        }
    }

    /**
     * 渲染所有伤害信息
     * Render all damage info
     */
    private void renderDamageInfos(PoseStack poseStack, float partialTicks) {
        if (damageInfos.isEmpty()) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // 计算玩家的插值位置（平滑移动）
        // Calculate player's interpolated position (smooth movement)
        double playerX = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double playerY = player.yOld + (player.getY() - player.yOld) * partialTicks;
        double playerZ = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        // 渲染每个伤害数字
        // Render each damage number
        for (DamageInfo info : damageInfos) {
            if (info.isExpired(currentTime)) {
                continue;
            }

            // 计算上升偏移（伤害数字会向上飘）
            // Calculate rise offset (damage numbers float upward)
            double riseOffset = info.getRiseOffset(currentTime, RISE_SPEED);

            // 计算相对于玩家的位置
            // Calculate position relative to player
            double x = info.position.x - playerX;
            double y = info.position.y - playerY + riseOffset;
            double z = info.position.z - playerZ;

            renderDamageText(poseStack, bufferSource, info.damage, info.color, x, y, z);
        }

        // 提交所有渲染批次
        // Submit all render batches
        bufferSource.endBatch();
    }

    /**
     * 渲染单个伤害文本
     * Render single damage text
     *
     * ✅ 修改：使用SEE_THROUGH模式，让文字始终显示在最上层
     * Modified: Use SEE_THROUGH mode to always render text on top
     */
    private void renderDamageText(PoseStack poseStack, MultiBufferSource bufferSource,
                                  float damage, int color, double x, double y, double z) {
        EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Font font = Minecraft.getInstance().font;

        // 根据颜色获取对应的缩放大小
        // Get scale based on color
        float scale = getScaleByColor(color);

        poseStack.pushPose();
        try {
            // 平移到伤害位置
            // Translate to damage position
            poseStack.translate(x, y, z);

            // 旋转文本使其始终面向玩家
            // Rotate text to always face player
            poseStack.mulPose(Axis.YP.rotationDegrees(-renderDispatcher.camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(renderDispatcher.camera.getXRot()));

            // 应用缩放（负值使文本正确显示）
            // Apply scale (negative values to display text correctly)
            poseStack.scale(-scale, -scale, scale);

            // 格式化伤害数字（保留1位小数）
            // Format damage number (1 decimal place)
            String damageText = String.format("%.1f", damage);

            // 计算文本宽度用于居中
            // Calculate text width for centering
            int stringWidth = font.width(damageText);

            // ✅ 关键修改：使用SEE_THROUGH模式
            // Critical change: Use SEE_THROUGH mode
            // SEE_THROUGH模式会：
            // SEE_THROUGH mode will:
            // 1. 禁用深度测试，文字不会被实体遮挡
            // 2. 文字始终渲染在最上层
            // 1. Disable depth testing, text won't be blocked by entities
            // 2. Text always renders on top layer
            font.drawInBatch(
                    damageText,                    // 要渲染的文本 / Text to render
                    -stringWidth / 2.0f,           // X位置（居中）/ X position (centered)
                    0,                             // Y位置 / Y position
                    color,                         // 文字颜色 / Text color
                    false,                         // 是否有阴影 / Has shadow
                    poseStack.last().pose(),       // 变换矩阵 / Transformation matrix
                    bufferSource,                  // 缓冲源 / Buffer source
                    Font.DisplayMode.SEE_THROUGH,  // ✅ 修改：穿透模式，不会被遮挡 / SEE_THROUGH mode, won't be occluded
                    0,                             // 背景色（0=透明）/ Background color (0=transparent)
                    15728880,                      // 光照等级（满亮）/ Light level (full bright)
                    true                           // 是否双向渲染 / Bidirectional rendering
            );
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 根据颜色获取缩放大小
     * Get scale based on color
     *
     * 不同伤害类型使用不同大小：
     * Different damage types use different sizes:
     * - 白色：普通伤害，最小
     * - 黄色/蓝色：暴击/护盾伤害，中等
     * - 橙色：2倍暴击，较大
     * - 红色：3倍暴击，最大
     */
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

    /**
     * 清空所有伤害信息
     * Clear all damage info
     */
    public void clear() {
        damageInfos.clear();
        cleanupTimer = 0;
    }

    /**
     * 获取当前伤害信息数量
     * Get current damage info count
     */
    public int size() {
        return damageInfos.size();
    }
}