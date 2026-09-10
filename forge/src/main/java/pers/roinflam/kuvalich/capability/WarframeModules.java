package pers.roinflam.kuvalich.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 战甲模组Capability
 * Warframe Modules Capability
 *
 * 存储玩家的战甲模组数据（8个槽位）
 * Stores player's warframe module data (8 slots)
 *
 * <p>⭐ 性能修复（本次）：新增 {@code version} 脏标记计数器。</p>
 *
 * <p>问题：{@code WarframeModuleSyncPacket.syncIfChanged} 原先每 5 tick、每玩家
 * 都要对 8 个 {@link ItemStack} 做 {@code tag.hashCode()} 递归全树遍历来做脏检测。
 * 60 人在线时约为每秒 4 × 60 × 8 = 1920 次深度 hash，纯属白烧主线程。</p>
 *
 * <p>修复：所有 setter 在内容<b>真正发生变化</b>时才递增 {@code version}，
 * 同步包只需比较一个 int。变更判定用 {@link ItemStack#matches}，
 * 同引用会短路返回，因此菜单打开期间高频调用 setter 也几乎零开销。</p>
 */
public class WarframeModules {

    private ItemStack one;
    private ItemStack two;
    private ItemStack three;
    private ItemStack four;
    private ItemStack five;
    private ItemStack six;
    private ItemStack seven;
    private ItemStack eight;

    /**
     * ⭐ 数据版本号（脏标记）
     * <p>任意槽位内容发生实际变化时递增。用于同步包做 O(1) 脏检测，
     * 替代原先每 5 tick 的全量 NBT 深度 hash。</p>
     */
    private int version = 0;

    public WarframeModules() {
        resetAll();
    }

    /**
     * 重置所有槽位
     * Reset all slots
     */
    private void resetAll() {
        this.one = ItemStack.EMPTY;
        this.two = ItemStack.EMPTY;
        this.three = ItemStack.EMPTY;
        this.four = ItemStack.EMPTY;
        this.five = ItemStack.EMPTY;
        this.six = ItemStack.EMPTY;
        this.seven = ItemStack.EMPTY;
        this.eight = ItemStack.EMPTY;
    }

    // ==================== 版本号 / Version ====================

    /**
     * 获取当前数据版本号
     *
     * @return 版本号，内容变化时递增
     */
    public int getVersion() {
        return version;
    }

    /**
     * 强制标记为已变更（用于克隆、反序列化等整体替换场景）
     */
    private void markDirty() {
        version++;
    }

    /**
     * 判断新值是否与旧值内容不同（相同引用会短路，开销极低）
     *
     * @param oldStack 旧值
     * @param newStack 新值
     * @return 内容不同返回 true
     */
    private static boolean isChanged(ItemStack oldStack, ItemStack newStack) {
        return !ItemStack.matches(oldStack, newStack);
    }

    // ==================== Getters and Setters ====================

    public ItemStack getOne() { return one; }

    public void setOne(ItemStack one) {
        ItemStack newStack = one != null ? one : ItemStack.EMPTY;
        if (isChanged(this.one, newStack)) { markDirty(); }
        this.one = newStack;
    }

    public ItemStack getTwo() { return two; }

    public void setTwo(ItemStack two) {
        ItemStack newStack = two != null ? two : ItemStack.EMPTY;
        if (isChanged(this.two, newStack)) { markDirty(); }
        this.two = newStack;
    }

    public ItemStack getThree() { return three; }

    public void setThree(ItemStack three) {
        ItemStack newStack = three != null ? three : ItemStack.EMPTY;
        if (isChanged(this.three, newStack)) { markDirty(); }
        this.three = newStack;
    }

    public ItemStack getFour() { return four; }

    public void setFour(ItemStack four) {
        ItemStack newStack = four != null ? four : ItemStack.EMPTY;
        if (isChanged(this.four, newStack)) { markDirty(); }
        this.four = newStack;
    }

    public ItemStack getFive() { return five; }

    public void setFive(ItemStack five) {
        ItemStack newStack = five != null ? five : ItemStack.EMPTY;
        if (isChanged(this.five, newStack)) { markDirty(); }
        this.five = newStack;
    }

    public ItemStack getSix() { return six; }

    public void setSix(ItemStack six) {
        ItemStack newStack = six != null ? six : ItemStack.EMPTY;
        if (isChanged(this.six, newStack)) { markDirty(); }
        this.six = newStack;
    }

    public ItemStack getSeven() { return seven; }

    public void setSeven(ItemStack seven) {
        ItemStack newStack = seven != null ? seven : ItemStack.EMPTY;
        if (isChanged(this.seven, newStack)) { markDirty(); }
        this.seven = newStack;
    }

    public ItemStack getEight() { return eight; }

    public void setEight(ItemStack eight) {
        ItemStack newStack = eight != null ? eight : ItemStack.EMPTY;
        if (isChanged(this.eight, newStack)) { markDirty(); }
        this.eight = newStack;
    }

    /**
     * 克隆数据
     * Clone data
     *
     * <p>⭐ 无条件标记脏，保证重生/维度传送后必定重新同步一次。</p>
     */
    public void clone(WarframeModules warframeModules) {
        if (warframeModules == null) {
            return;
        }

        this.setOne(warframeModules.getOne());
        this.setTwo(warframeModules.getTwo());
        this.setThree(warframeModules.getThree());
        this.setFour(warframeModules.getFour());
        this.setFive(warframeModules.getFive());
        this.setSix(warframeModules.getSix());
        this.setSeven(warframeModules.getSeven());
        this.setEight(warframeModules.getEight());

        markDirty();
    }

    // ==================== NBT序列化 / NBT Serialization ====================

    /**
     * 序列化到NBT
     * Serialize to NBT
     */
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        nbt.put("one", getOne().save(new CompoundTag()));
        nbt.put("two", getTwo().save(new CompoundTag()));
        nbt.put("three", getThree().save(new CompoundTag()));
        nbt.put("four", getFour().save(new CompoundTag()));
        nbt.put("five", getFive().save(new CompoundTag()));
        nbt.put("six", getSix().save(new CompoundTag()));
        nbt.put("seven", getSeven().save(new CompoundTag()));
        nbt.put("eight", getEight().save(new CompoundTag()));

        return nbt;
    }

    /**
     * 从NBT反序列化
     * Deserialize from NBT
     *
     * <p>⭐ 无条件标记脏，保证读档后必定重新同步一次。</p>
     */
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }

        this.one = ItemStack.of(nbt.getCompound("one"));
        this.two = ItemStack.of(nbt.getCompound("two"));
        this.three = ItemStack.of(nbt.getCompound("three"));
        this.four = ItemStack.of(nbt.getCompound("four"));
        this.five = ItemStack.of(nbt.getCompound("five"));
        this.six = ItemStack.of(nbt.getCompound("six"));
        this.seven = ItemStack.of(nbt.getCompound("seven"));
        this.eight = ItemStack.of(nbt.getCompound("eight"));

        markDirty();
    }

    // ==================== Provider内部类 / Provider Inner Class ====================

    /**
     * Capability Provider
     */
    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private final WarframeModules instance = new WarframeModules();
        private final LazyOptional<WarframeModules> optional = LazyOptional.of(() -> instance);

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CapabilityRegistryHandler.WARFRAME_MODULES.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            instance.deserializeNBT(nbt);
        }

        /**
         * 使LazyOptional失效
         * Invalidate LazyOptional
         */
        public void invalidate() {
            optional.invalidate();
        }
    }
}
