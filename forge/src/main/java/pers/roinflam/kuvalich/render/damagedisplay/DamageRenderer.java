package pers.roinflam.kuvalich.render.damagedisplay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * 伤害数字/文本渲染器(1.20.1)
 * Damage number/text renderer (1.20.1)
 *
 * 性能优化：使用 ArrayDeque 替代 ArrayList，移除首元素从 O(n) → O(1)
 * Performance: ArrayDeque replaces ArrayList, remove-first from O(n) → O(1)
 */
@OnlyIn(Dist.CLIENT)
public class DamageRenderer {

    /** 伤害信息队列（ArrayDeque 支持高效的头部/尾部操作） / Damage info deque */
    private final ArrayDeque<DamageInfo> damageInfos = new ArrayDeque<>();

    /** 单例实例 / Singleton instance */
    private static volatile DamageRenderer instance;

    /** 上升速度 / Rise speed */
    private static final double RISE_SPEED = 1.0 / 4000.0;

    /** 最大伤害信息数量 / Max damage info count */
    private static final int MAX_DAMAGE_INFOS = 500;

    /** 清理间隔（tick） / Cleanup interval (ticks) */
    private static final int CLEANUP_INTERVAL = 20;

    /** 清理计时器 / Cleanup timer */
    private int cleanupTimer = 0;

    // 字体缩放(白色基准值×1.5)
    private static final float SCALE_WHITE = 0.03F * 1.5F;
    private static final float SCALE_YELLOW_BLUE = 0.038F * 1.5F;
    private static final float SCALE_ORANGE = 0.048F * 1.5F;
    private static final float SCALE_RED = 0.06F * 1.5F;

    private DamageRenderer() {
    }

    /**
     * 获取单例实例
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
     *
     * 性能优化：pollFirst() 是 O(1)（原 ArrayList.remove(0) 是 O(n)）
     * Performance: pollFirst() is O(1) (was ArrayList.remove(0) which is O(n))
     */
    public void addDamageInfo(DamageInfo info) {
        if (info == null) {
            return;
        }

        if (damageInfos.size() >= MAX_DAMAGE_INFOS) {
            damageInfos.pollFirst();  // O(1)，原 remove(0) 是 O(n)
        }

        damageInfos.addLast(info);
    }

    /**
     * 客户端Tick事件处理
     */
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

    /**
     * 清理过期的伤害信息
     *
     * 使用 Iterator 遍历 ArrayDeque 安全移除过期元素
     * Use Iterator to safely remove expired elements from ArrayDeque
     */
    private void cleanupExpired() {
        if (damageInfos.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Iterator<DamageInfo> iterator = damageInfos.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isExpired(currentTime)) {
                iterator.remove();
            }
        }
    }

    /**
     * 渲染关卡阶段事件处理
     */
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

    /**
     * 渲染所有伤害信息
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

        double playerX = player.xOld + (player.getX() - player.xOld) * partialTicks;
        double playerY = player.yOld + (player.getY() - player.yOld) * partialTicks;
        double playerZ = player.zOld + (player.getZ() - player.zOld) * partialTicks;

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        RenderSystem.disableDepthTest();

        for (DamageInfo info : damageInfos) {
            if (info.isExpired(currentTime)) {
                continue;
            }

            double riseOffset = info.getRiseOffset(currentTime, RISE_SPEED);

            double x = info.position.x - playerX;
            double y = info.position.y - playerY + riseOffset;
            double z = info.position.z - playerZ;

            renderDamageText(poseStack, bufferSource, info.text, x, y, z);
        }

        bufferSource.endBatch();
        RenderSystem.enableDepthTest();
    }

    /**
     * 渲染单个伤害文本（支持§颜色代码）
     */
    private void renderDamageText(PoseStack poseStack, MultiBufferSource bufferSource,
                                  String text, double x, double y, double z) {
        EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Font font = Minecraft.getInstance().font;

        // 根据文本开头的颜色代码判断缩放
        float scale = getScaleByText(text);

        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);

            poseStack.mulPose(Axis.YP.rotationDegrees(-renderDispatcher.camera.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(renderDispatcher.camera.getXRot()));

            poseStack.scale(-scale, -scale, scale);

            int stringWidth = font.width(text);

            // Minecraft会自动处理§颜色代码
            font.drawInBatch(
                    text,
                    -stringWidth / 2.0f,
                    0,
                    0xFFFFFF,  // 白色底色（颜色由§代码控制）
                    false,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.SEE_THROUGH,
                    0,
                    15728880,
                    font.isBidirectional()
            );
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 根据文本开头的颜色代码获取缩放大小
     */
    private float getScaleByText(String text) {
        if (text.startsWith("§f")) {
            return SCALE_WHITE;
        } else if (text.startsWith("§e") || text.startsWith("§b")) {
            return SCALE_YELLOW_BLUE;
        } else if (text.startsWith("§6")) {
            return SCALE_ORANGE;
        } else if (text.startsWith("§c")) {
            return SCALE_RED;
        }
        return SCALE_WHITE;
    }

    /**
     * 清空所有伤害信息
     */
    public void clear() {
        damageInfos.clear();
        cleanupTimer = 0;
    }

    /**
     * 获取当前伤害信息数量
     */
    public int size() {
        return damageInfos.size();
    }
}