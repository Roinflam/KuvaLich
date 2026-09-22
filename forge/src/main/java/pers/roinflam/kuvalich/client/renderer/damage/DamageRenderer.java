package pers.roinflam.kuvalich.client.renderer.damage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.kuvalich.config.ModConfig;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import pers.roinflam.kuvalich.network.packet.DamageInfo;

/**
 * 伤害数字/文本渲染器(1.20.1)
 * Damage number/text renderer (1.20.1)
 *
 * 性能优化：使用 ArrayDeque 替代 ArrayList，移除首元素从 O(n) → O(1)
 * Performance: ArrayDeque replaces ArrayList, remove-first from O(n) → O(1)
 *
 * <p>数字的坐标原点为「相机位置」。本渲染阶段的 PoseStack 以相机为原点，
 * 服务端给出的位置为目标身体上半截，站立第一人称下的观感与早期版本基本一致。</p>
 *
 * <p>⭐ 本次改动：新增<b>伤害数字合并</b>（{@code mergeDamageNumbers}，默认开启）。
 * 同一只怪身上、合并窗口内（默认 0.2 秒）的多条数字会并成一条：数值累加、颜色取最高一档、图标取并集。
 * 本模组自己的元素伤害单独分组（同元素才互相合并），保证元素图标准确。
 * 关闭开关后每个包各自成一条，与改动前完全一致。合并发生在客户端，
 * 服务端照旧逐条发包，因此不会给数字显示带来任何延迟。</p>
 */
@OnlyIn(Dist.CLIENT)
public class DamageRenderer {

    /** 伤害信息队列（ArrayDeque 支持高效的头部/尾部操作） / Damage info deque */
    private final ArrayDeque<DamageInfo> damageInfos = new ArrayDeque<>();

    /**
     * 合并槽位：key = 受击实体 + 分组，value = 该分组当前还能接收合并的那条数字
     *
     * <p>只有最近创建、且仍在合并窗口内的条目会留在这里；过期条目在
     * {@link #cleanupExpired} 里定期清掉，或在下一条同 key 数字到来时被顶替。</p>
     */
    private final Map<DamageInfo.MergeKey, DamageInfo> mergeSlots = new HashMap<>();

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
     * ⭐ 收到一条伤害数字：能合并就并入已有的那条，不能就新起一条
     *
     * <p>仅在客户端主线程调用（网络包 handler 已用 {@code enqueueWork} 切回主线程）。</p>
     *
     * @param entityId        受击实体 ID；小于 0 表示未知，此时不参与合并
     * @param mergeGroup      合并分组；普通伤害为空串，元素伤害为元素名
     * @param amount          伤害数值
     * @param colorCode       颜色码（如 {@code "§c"}）
     * @param prefix          数字前缀，可为空串
     * @param suffix          数字后缀（图标串），可为空串
     * @param hitShield       是否打到护盾
     * @param position        显示位置
     * @param displayDuration 显示时长（毫秒）
     */
    public void addDamage(int entityId, @Nonnull String mergeGroup, float amount, @Nonnull String colorCode,
                          @Nonnull String prefix, @Nonnull String suffix, boolean hitShield,
                          @Nonnull Vec3 position, long displayDuration) {
        long now = System.currentTimeMillis();

        DamageInfo.MergeKey key = null;
        long windowMs = 0L;
        if (entityId >= 0 && ModConfig.KUVA_LICH.mergeDamageNumbers.get()) {
            windowMs = ModConfig.KUVA_LICH.mergeDamageWindowMs.get();
            if (windowMs > 0L) {
                key = new DamageInfo.MergeKey(entityId, mergeGroup);
                DamageInfo slot = mergeSlots.get(key);
                if (slot != null && slot.canMergeAt(now)) {
                    slot.merge(amount, colorCode, prefix, suffix, hitShield, now, displayDuration);
                    return;
                }
            }
        }

        DamageInfo info = new DamageInfo(amount, colorCode, prefix, suffix, hitShield,
                position, now, displayDuration, key, windowMs);
        addDamageInfo(info);
        if (key != null) {
            // 直接覆盖：旧槽位要么已过合并窗口，要么已过期，都不需要再保留
            mergeSlots.put(key, info);
        }
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
            DamageInfo evicted = damageInfos.pollFirst();  // O(1)，原 remove(0) 是 O(n)
            // 被挤掉的条目如果还占着合并槽位，一并释放，避免后续数字并进一条已经不显示的记录里
            if (evicted != null && evicted.getMergeKey() != null) {
                mergeSlots.remove(evicted.getMergeKey(), evicted);
            }
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
     * 清理过期的伤害信息与失效的合并槽位
     *
     * 使用 Iterator 遍历 ArrayDeque 安全移除过期元素
     * Use Iterator to safely remove expired elements from ArrayDeque
     */
    private void cleanupExpired() {
        long currentTime = System.currentTimeMillis();

        if (!mergeSlots.isEmpty()) {
            mergeSlots.values().removeIf(info -> !info.canMergeAt(currentTime));
        }

        if (damageInfos.isEmpty()) {
            return;
        }

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
            renderDamageInfos(event.getPoseStack());
        }
    }

    /**
     * 渲染所有伤害信息
     *
     * <p>以相机位置为原点换算相对坐标。相机位置已由原版按 partialTick 插值，
     * 第三人称时也已处理好镜头后移，这里不需要再手动插值。</p>
     *
     * @param poseStack 渲染阶段提供的矩阵栈（已含相机旋转，原点为相机）
     */
    private void renderDamageInfos(PoseStack poseStack) {
        if (damageInfos.isEmpty()) {
            return;
        }

        // 与 renderDamageText 里做朝向用的是同一个相机对象，位置与朝向保持一致
        Camera camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
        if (camera == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        Vec3 cameraPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        RenderSystem.disableDepthTest();

        for (DamageInfo info : damageInfos) {
            if (info.isExpired(currentTime)) {
                continue;
            }

            double riseOffset = info.getRiseOffset(currentTime, RISE_SPEED);

            double x = info.position.x - cameraPos.x;
            double y = info.position.y - cameraPos.y + riseOffset;
            double z = info.position.z - cameraPos.z;

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
        mergeSlots.clear();
        cleanupTimer = 0;
    }

    /**
     * 获取当前伤害信息数量
     */
    public int size() {
        return damageInfos.size();
    }
}
