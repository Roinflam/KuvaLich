package pers.roinflam.kuvalich.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemWarframeTable;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemWeaponTable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 模组冲突高亮渲染器（客户端专用）
 * Module Conflict Highlight Renderer (Client-only)
 * <p>
 * 两种触发方式，两种动画效果：
 * <p>
 * 1. 鼠标持有模组时 → 冲突槽位持续呼吸脉冲（放下即停）
 * 2. Shift+左键快速转移失败时 → 冲突槽位快速闪烁2次后渐弱消失（约1.5秒）
 * <p>
 * 冲突条件与 mayPlace 逻辑完全一致：
 * - 双向冲突检测（AbstractModule.hasConflict）
 * - 裂罅模组不可重复装备（同类型裂罅互斥）
 * <p>
 * 关键特性：如果存在任何冲突，模组无法放入任何槽位（冲突是全局的，
 * 不依赖目标槽位），因此Shift+点击时检测到冲突即代表转移必定失败。
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ModuleConflictHighlighter {

    // ==================== 连续脉冲常量（鼠标持有时）====================

    /** 呼吸脉冲周期（毫秒），1500ms = 1.5秒一个完整呼吸循环 */
    private static final double PULSE_PERIOD_MS = 1500.0;

    /** 呼吸脉冲最小透明度（0~255） */
    private static final int PULSE_ALPHA_MIN = 30;

    /** 呼吸脉冲最大透明度（0~255） */
    private static final int PULSE_ALPHA_MAX = 100;

    // ==================== 快速闪烁常量（Shift+点击时）====================

    /** 闪烁总持续时间（毫秒） */
    private static final long FLASH_DURATION_MS = 3000L;

    /** 闪烁最大透明度（0~255），首次脉冲峰值 */
    private static final int FLASH_ALPHA_MAX = 150;

    /** 闪烁脉冲次数（正弦完整周期数），5次 = 快速闪烁5下后渐弱 */
    private static final double FLASH_PULSE_CYCLES = 5.0;

    // ==================== 通用常量 ====================

    /** 高亮颜色RGB部分（不含alpha），柔和的红色 */
    private static final int HIGHLIGHT_RGB = 0xFF4444;

    // ==================== 闪烁状态 ====================

    /**
     * 闪烁状态：模组槽位索引（0~7）→ 闪烁开始时间戳（毫秒）
     * <p>
     * 由 Shift+点击触发写入，每帧渲染时读取并清理过期条目。
     * 不跨菜单保留：菜单切换时自动清空。
     */
    private static final Map<Integer, Long> flashStartTimes = new HashMap<>();

    /** 闪烁所属的菜单实例引用，用于检测菜单切换 */
    private static AbstractContainerMenu flashMenu = null;

    // ==================== 渲染事件 ====================

    /**
     * 在军械库界面的前景层渲染冲突高亮
     * <p>
     * Foreground事件在renderLabels()内触发，坐标系已平移至容器左上角，
     * slot.x / slot.y 可直接作为绘制坐标。
     * 渲染层级在物品图标之上、鼠标携带物和Tooltip之下。
     * <p>
     * 同时处理两种高亮模式：连续脉冲（鼠标持有）和快速闪烁（Shift+点击后）。
     *
     * @param event 容器屏幕前景渲染事件
     */
    @SubscribeEvent
    public static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        AbstractContainerMenu menu = screen.getMenu();
        GuiGraphics guiGraphics = event.getGuiGraphics();

        // 非军械库界面直接跳过
        boolean isWeaponTable = menu instanceof MenuRequiemWeaponTable;
        boolean isWarframeTable = menu instanceof MenuRequiemWarframeTable;
        if (!isWeaponTable && !isWarframeTable) { return; }

        // 菜单切换时清空闪烁状态
        if (flashMenu != null && flashMenu != menu) {
            flashStartTimes.clear();
            flashMenu = null;
        }

        // ─── 模式1：鼠标持有模组时的连续脉冲 ───
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            if (isWeaponTable) {
                renderWeaponConflictPulse(guiGraphics, (MenuRequiemWeaponTable) menu, carried);
            } else {
                renderWarframeConflictPulse(guiGraphics, (MenuRequiemWarframeTable) menu, carried);
            }
        }

        // ─── 模式2：Shift+点击后的衰减闪烁 ───
        if (!flashStartTimes.isEmpty() && flashMenu == menu) {
            renderFlashHighlights(guiGraphics, menu);
        }
    }

    // ==================== Shift+点击检测 ====================

    /**
     * 检测Shift+左键点击，触发冲突闪烁
     * <p>
     * 监听 ScreenEvent.MouseButtonPressed.Pre（点击处理前），
     * 此时物品尚未移动，可以安全读取当前槽位内容。
     * <p>
     * 仅当点击的是玩家背包区域（非模组槽位）中的模组物品时才触发检测。
     * getSlotUnderMouse() 为 Forge 添加的方法，返回当前鼠标悬停的槽位。
     *
     * @param event 鼠标按键事件
     */
    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        // 仅处理 Shift + 左键
        if (event.getButton() != 0 || !Screen.hasShiftDown()) { return; }
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) { return; }

        AbstractContainerMenu menu = screen.getMenu();
        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (hoveredSlot == null || !hoveredSlot.hasItem()) { return; }

        ItemStack clickedItem = hoveredSlot.getItem();

        // 计算槽位在菜单中的索引，判断是否来自玩家背包
        int menuIndex = menu.slots.indexOf(hoveredSlot);

        if (menu instanceof MenuRequiemWeaponTable weaponMenu) {
            // 武器军械库：模组槽0~7，武器槽8，玩家背包从索引9开始
            if (menuIndex < 9) { return; }
            triggerWeaponFlash(weaponMenu, clickedItem);
        } else if (menu instanceof MenuRequiemWarframeTable warframeMenu) {
            // 战甲军械库：模组槽0~7，玩家背包从索引8开始
            if (menuIndex < 8) { return; }
            triggerWarframeFlash(warframeMenu, clickedItem);
        }
    }

    // ==================== 武器军械库 ====================

    /**
     * 武器军械库：鼠标持有时的连续脉冲渲染
     *
     * @param guiGraphics 图形上下文
     * @param menu        武器军械库菜单
     * @param carried     鼠标上拿着的物品
     */
    private static void renderWeaponConflictPulse(GuiGraphics guiGraphics,
                                                  MenuRequiemWeaponTable menu,
                                                  ItemStack carried) {
        if (!(carried.getItem() instanceof AbstractItemModule)) { return; }
        if (AbstractModule.isRandom(carried)) { return; }

        for (int i = 0; i < 8; i++) {
            ItemStack existing = menu.moduleHandler.getStackInSlot(i);
            if (existing.isEmpty()) { continue; }
            if (isWeaponModuleConflict(existing, carried)) {
                Slot slot = menu.getSlot(i);
                drawContinuousPulse(guiGraphics, slot.x, slot.y);
            }
        }
    }

    /**
     * 武器军械库：Shift+点击时触发闪烁
     *
     * @param menu        武器军械库菜单
     * @param clickedItem 被Shift+点击的物品
     */
    private static void triggerWeaponFlash(MenuRequiemWeaponTable menu, ItemStack clickedItem) {
        if (!(clickedItem.getItem() instanceof AbstractItemModule)) { return; }
        if (AbstractModule.isRandom(clickedItem)) { return; }

        boolean hasConflict = false;
        long now = System.currentTimeMillis();

        for (int i = 0; i < 8; i++) {
            ItemStack existing = menu.moduleHandler.getStackInSlot(i);
            if (existing.isEmpty()) { continue; }
            if (isWeaponModuleConflict(existing, clickedItem)) {
                flashStartTimes.put(i, now);
                hasConflict = true;
            }
        }

        if (hasConflict) {
            flashMenu = menu;
        }
    }

    /**
     * 判断武器模组是否冲突（与 MenuRequiemWeaponTable.ModuleSlot.mayPlace 逻辑一致）
     *
     * @param existing 槽位中已有的模组
     * @param carried  鼠标上/正在转移的模组
     * @return 是否冲突
     */
    private static boolean isWeaponModuleConflict(ItemStack existing, ItemStack carried) {
        // 裂罅互斥：只能装1个裂罅
        if (carried.getItem() instanceof ItemRivenModule
                && existing.getItem() instanceof ItemRivenModule) {
            return true;
        }
        // 双向冲突检测（同类型模组互斥）
        return AbstractModule.hasConflict(existing, carried);
    }

    // ==================== 战甲军械库 ====================

    /**
     * 战甲军械库：鼠标持有时的连续脉冲渲染
     *
     * @param guiGraphics 图形上下文
     * @param menu        战甲军械库菜单
     * @param carried     鼠标上拿着的物品
     */
    private static void renderWarframeConflictPulse(GuiGraphics guiGraphics,
                                                    MenuRequiemWarframeTable menu,
                                                    ItemStack carried) {
        if (!(carried.getItem() instanceof AbstractWarframeModule)) { return; }
        if (AbstractModule.isRandom(carried)) { return; }

        for (int i = 0; i < 8; i++) {
            ItemStack existing = menu.moduleHandler.getStackInSlot(i);
            if (existing.isEmpty()) { continue; }
            if (isWarframeModuleConflict(existing, carried)) {
                Slot slot = menu.getSlot(i);
                drawContinuousPulse(guiGraphics, slot.x, slot.y);
            }
        }
    }

    /**
     * 战甲军械库：Shift+点击时触发闪烁
     *
     * @param menu        战甲军械库菜单
     * @param clickedItem 被Shift+点击的物品
     */
    private static void triggerWarframeFlash(MenuRequiemWarframeTable menu, ItemStack clickedItem) {
        if (!(clickedItem.getItem() instanceof AbstractWarframeModule)) { return; }
        if (AbstractModule.isRandom(clickedItem)) { return; }

        boolean hasConflict = false;
        long now = System.currentTimeMillis();

        for (int i = 0; i < 8; i++) {
            ItemStack existing = menu.moduleHandler.getStackInSlot(i);
            if (existing.isEmpty()) { continue; }
            if (isWarframeModuleConflict(existing, clickedItem)) {
                flashStartTimes.put(i, now);
                hasConflict = true;
            }
        }

        if (hasConflict) {
            flashMenu = menu;
        }
    }

    /**
     * 判断战甲模组是否冲突（与 MenuRequiemWarframeTable.ModuleSlot.mayPlace 逻辑一致）
     *
     * @param existing 槽位中已有的模组
     * @param carried  鼠标上/正在转移的模组
     * @return 是否冲突
     */
    private static boolean isWarframeModuleConflict(ItemStack existing, ItemStack carried) {
        // 裂罅互斥：只能装1个裂罅
        if (carried.getItem() instanceof WarframeRivenModule
                && existing.getItem() instanceof WarframeRivenModule) {
            return true;
        }
        // 双向冲突检测（同类型模组互斥）
        return AbstractModule.hasConflict(existing, carried);
    }

    // ==================== 闪烁渲染 ====================

    /**
     * 渲染所有活跃的Shift+点击闪烁效果，并清理过期条目
     *
     * @param guiGraphics 图形上下文
     * @param menu        当前菜单
     */
    private static void renderFlashHighlights(GuiGraphics guiGraphics, AbstractContainerMenu menu) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = flashStartTimes.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            long elapsed = now - entry.getValue();

            // 超时清除
            if (elapsed >= FLASH_DURATION_MS) {
                it.remove();
                continue;
            }

            Slot slot = menu.getSlot(entry.getKey());
            drawDecayFlash(guiGraphics, slot.x, slot.y, elapsed);
        }

        // 全部过期后清理菜单引用
        if (flashStartTimes.isEmpty()) {
            flashMenu = null;
        }
    }

    // ==================== 绘制方法 ====================

    /**
     * 绘制连续呼吸脉冲（鼠标持有模组时）
     * <p>
     * 正弦波动画：alpha 在 PULSE_ALPHA_MIN ~ PULSE_ALPHA_MAX 之间平滑循环，
     * 周期约1.5秒，视觉上类似"呼吸灯"效果。
     * <p>
     * 使用 GuiGraphics.fill() 绘制，内部使用 RenderType.gui() 已自带混合模式，
     * 无需手动开关 RenderSystem.enableBlend()。
     *
     * @param guiGraphics 图形上下文
     * @param x           槽位X坐标（容器内相对坐标）
     * @param y           槽位Y坐标（容器内相对坐标）
     */
    private static void drawContinuousPulse(GuiGraphics guiGraphics, int x, int y) {
        long time = System.currentTimeMillis();
        // 正弦脉冲：值域 0.0 ~ 1.0
        double pulse = (Math.sin(time * Math.PI * 2.0 / PULSE_PERIOD_MS) + 1.0) / 2.0;
        // 线性插值计算当前alpha
        int alpha = PULSE_ALPHA_MIN + (int) ((PULSE_ALPHA_MAX - PULSE_ALPHA_MIN) * pulse);
        // 组装ARGB颜色值
        int color = (alpha << 24) | HIGHLIGHT_RGB;
        // 绘制16×16的半透明色块覆盖在槽位上
        guiGraphics.fill(x, y, x + 16, y + 16, color);
    }

    /**
     * 绘制衰减闪烁（Shift+点击失败时）
     * <p>
     * 动画效果：二次衰减包络 × 快速正弦脉冲
     * <ul>
     *   <li>包络：(1-t)² 从亮到暗的自然衰减曲线</li>
     *   <li>脉冲：2个完整正弦周期，形成"闪→暗→闪→暗"的节奏</li>
     *   <li>叠加：第一次闪烁最亮，第二次明显减弱，之后自然消失</li>
     * </ul>
     *
     * @param guiGraphics 图形上下文
     * @param x           槽位X坐标（容器内相对坐标）
     * @param y           槽位Y坐标（容器内相对坐标）
     * @param elapsed     自闪烁开始经过的毫秒数
     */
    private static void drawDecayFlash(GuiGraphics guiGraphics, int x, int y, long elapsed) {
        // 归一化时间进度 0.0 ~ 1.0
        double t = (double) elapsed / (double) FLASH_DURATION_MS;
        if (t >= 1.0) { return; }

        // 二次衰减包络：开头最亮，逐渐变暗
        double envelope = (1.0 - t) * (1.0 - t);

        // 快速正弦脉冲：FLASH_PULSE_CYCLES 个完整周期
        double pulse = (Math.sin(t * Math.PI * 2.0 * FLASH_PULSE_CYCLES) + 1.0) / 2.0;

        int alpha = (int) (FLASH_ALPHA_MAX * envelope * pulse);
        if (alpha <= 1) { return; }

        int color = (alpha << 24) | HIGHLIGHT_RGB;
        guiGraphics.fill(x, y, x + 16, y + 16, color);
    }
}