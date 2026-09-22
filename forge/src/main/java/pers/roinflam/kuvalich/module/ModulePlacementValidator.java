package pers.roinflam.kuvalich.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

/**
 * 模组能不能装进某个槽位 —— 唯一判定实现
 *
 * <p><b>为什么要抽出来。</b>这套判定原先逐字写在两个军械库菜单各自的
 * {@code ModuleSlot.mayPlace()} 内部类里（{@code RequiemWeaponTableMenu} 与
 * {@code RequiemWarframeTableMenu}），两份代码八成重复。图鉴的「创造模式一键装上」
 * 是第三个需要同一套判定的地方 —— 如果再复制一份，以后改一条规则就要同步改三处，
 * 漏掉任何一处都会变成「军械库里装不上、但一键装能装进去」这种能写坏存档的不一致。</p>
 *
 * <p><b>本类是双端的</b>，因为校验必须在服务端跑：客户端说「看起来有空位」不能信。</p>
 *
 * <p>判定规则从两个菜单里逐字搬来，没有增删，只是把「槽位内容怎么取」抽成了
 * {@link SlotView}，好让三种不同的存储（武器 NBT 列表 / 战甲 Capability /
 * 菜单的 ItemStackHandler）都能接上。</p>
 *
 * @author RoinFlam
 */
public final class ModulePlacementValidator {

    /** 固定 8 槽。仓库里没有「一个模组占多槽」或「额外槽位」的实现，{@code ExtraSlotTooltipHelper} 指的是副手/盔甲/Curios 的属性来源，不占模组槽。 */
    public static final int SLOT_COUNT = 8;

    /** 武器顶层 NBT 里收紧可用槽位数的字段名，与 {@code RequiemWeaponTableMenu} 保持同名同义。 */
    private static final String MODULE_LIMIT_KEY = "moduleLimit";

    private ModulePlacementValidator() {}

    /**
     * 取某个槽位当前装着什么。空槽返回 {@link ItemStack#EMPTY}。
     *
     * <p>三种存储各自适配：武器是 NBT 里的 modules 列表、战甲是 Capability 的
     * one~eight 八个字段、菜单是 ItemStackHandler。</p>
     */
    @FunctionalInterface
    public interface SlotView {
        ItemStack at(int slotIndex);
    }

    // ==================== 武器 ====================

    /**
     * 读武器的可用槽位上限
     *
     * <p>与 {@code RequiemWeaponTableMenu.getModuleLimit()} 同款逻辑：读**顶层** NBT
     * （不是模组子 tag）的 {@code moduleLimit}，不存在则 8（不限制），存在则夹到 [0,8]。</p>
     *
     * <p>⚠ 这个字段目前全仓库只有读、没有任何写入点，等价于恒定 8 —— 是个预留挂钩。
     * 这里照样尊重它，免得将来某个强化系统开始写它之后，一键装上成了绕过限制的后门。</p>
     *
     * @param weapon 武器物品（空则返回 0，与菜单一致）
     * @return 可用槽位数 [0,8]
     */
    public static int readModuleLimit(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0;
        }
        // 显式写在 NBT 上的上限优先：这是留给「某把武器单独设定」的口子
        CompoundTag tag = weapon.getTag();
        if (tag != null && tag.contains(MODULE_LIMIT_KEY)) {
            return Math.max(0, Math.min(SLOT_COUNT, tag.getInt(MODULE_LIMIT_KEY)));
        }
        return limitFromLevel(KuvaWeaponUtil.getNumber(weapon));
    }

    /**
     * 按赤毒等级算可用槽位数
     *
     * <p>总槽位永远是 8，等级决定其中有几个已解锁：
     * {@code base + level / perUnlock}，夹到 [1, 8]。
     * {@code perUnlock} 配成 0 就是关闭等级门槛、8 个全开。</p>
     *
     * <p><b>已装在锁定槽里的模组不会被清掉、也照常生效。</b>
     * {@code WeaponModuleHandler.getModules} 读的是 NBT 里的全部 8 格，不看这个上限。
     * 这是刻意的：玩家的武器可能是在挂上等级门槛之前装满的，
     * 因为一条配置就把他已经装好的模组作废，比门槛本身更让人难受。
     * 上限只拦「往锁定槽里放新的」。</p>
     *
     * @param level 赤毒等级
     * @return 可用槽位数 [1,8]
     */
    public static int limitFromLevel(int level) {
        int perUnlock = ModConfig.KUVA_LICH.moduleSlotLevelsPerUnlock.get();
        if (perUnlock <= 0) {
            return SLOT_COUNT;
        }
        int base = ModConfig.KUVA_LICH.moduleSlotBaseCount.get();
        int unlocked = base + Math.max(0, level) / perUnlock;
        return Math.max(1, Math.min(SLOT_COUNT, unlocked));
    }

    /**
     * 武器模组能否放进指定槽位
     *
     * <p>判定顺序与 {@code RequiemWeaponTableMenu.ModuleSlot.mayPlace()} 逐字一致：
     * 空物品 → 槽位未解锁 → 不是武器模组 → 未鉴定的随机模组 → 目标槽已占用 →
     * 其余槽里已有裂罅（裂罅唯一）→ 与其余槽冲突。</p>
     *
     * <p>菜单里那两条「物品为空」「军械库还没放武器」由调用方各自负责：
     * 前者本方法也查，后者只对菜单成立。</p>
     *
     * @param candidate   要装的模组
     * @param slots       当前八槽的内容
     * @param slotIndex   目标槽位
     * @param moduleLimit 可用槽位上限，见 {@link #readModuleLimit}
     * @return 能否放置
     */
    public static boolean canPlaceWeapon(ItemStack candidate, SlotView slots, int slotIndex, int moduleLimit) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return false;
        if (slotIndex >= moduleLimit) return false;
        if (!(candidate.getItem() instanceof AbstractWeaponModule)) return false;
        if (AbstractWeaponModule.isRandom(candidate)) return false;
        if (!slots.at(slotIndex).isEmpty()) return false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == slotIndex) continue;
            ItemStack existing = slots.at(i);
            if (existing.isEmpty()) continue;
            // 裂罅唯一：一把武器上只能有一张裂罅卡
            if (candidate.getItem() instanceof WeaponRivenModule
                    && existing.getItem() instanceof WeaponRivenModule) {
                return false;
            }
            // 同 type 重复 / 冲突标签有交集
            if (AbstractModule.hasConflict(existing, candidate)) return false;
        }
        return true;
    }

    /**
     * 找武器上第一个能放下这个模组的空槽
     *
     * @param candidate   要装的模组
     * @param slots       当前八槽的内容
     * @param moduleLimit 可用槽位上限
     * @return 槽位下标，没有可用槽位时返回 -1
     */
    public static int firstPlaceableWeaponSlot(ItemStack candidate, SlotView slots, int moduleLimit) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (canPlaceWeapon(candidate, slots, i, moduleLimit)) return i;
        }
        return -1;
    }

    // ==================== 战甲 ====================

    /**
     * 战甲模组能否放进指定槽位
     *
     * <p>与 {@code RequiemWarframeTableMenu.ModuleSlot.mayPlace()} 逐字一致。
     * 战甲侧没有「先放武器才解锁槽位」那一层，也没有 moduleLimit 的概念。</p>
     *
     * @param candidate 要装的模组
     * @param slots     当前八槽的内容
     * @param slotIndex 目标槽位
     * @return 能否放置
     */
    public static boolean canPlaceWarframe(ItemStack candidate, SlotView slots, int slotIndex) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return false;
        if (!(candidate.getItem() instanceof AbstractWarframeModule)) return false;
        if (AbstractWarframeModule.isRandom(candidate)) return false;
        if (!slots.at(slotIndex).isEmpty()) return false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == slotIndex) continue;
            ItemStack existing = slots.at(i);
            if (existing.isEmpty()) continue;
            if (candidate.getItem() instanceof WarframeRivenModule
                    && existing.getItem() instanceof WarframeRivenModule) {
                return false;
            }
            if (AbstractModule.hasConflict(existing, candidate)) return false;
        }
        return true;
    }

    /**
     * 找战甲上第一个能放下这个模组的空槽
     *
     * @param candidate 要装的模组
     * @param slots     当前八槽的内容
     * @return 槽位下标，没有可用槽位时返回 -1
     */
    public static int firstPlaceableWarframeSlot(ItemStack candidate, SlotView slots) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (canPlaceWarframe(candidate, slots, i)) return i;
        }
        return -1;
    }
}
