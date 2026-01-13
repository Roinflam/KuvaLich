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

    // ==================== Getters and Setters ====================

    public ItemStack getOne() { return one; }
    public void setOne(ItemStack one) { this.one = one != null ? one : ItemStack.EMPTY; }

    public ItemStack getTwo() { return two; }
    public void setTwo(ItemStack two) { this.two = two != null ? two : ItemStack.EMPTY; }

    public ItemStack getThree() { return three; }
    public void setThree(ItemStack three) { this.three = three != null ? three : ItemStack.EMPTY; }

    public ItemStack getFour() { return four; }
    public void setFour(ItemStack four) { this.four = four != null ? four : ItemStack.EMPTY; }

    public ItemStack getFive() { return five; }
    public void setFive(ItemStack five) { this.five = five != null ? five : ItemStack.EMPTY; }

    public ItemStack getSix() { return six; }
    public void setSix(ItemStack six) { this.six = six != null ? six : ItemStack.EMPTY; }

    public ItemStack getSeven() { return seven; }
    public void setSeven(ItemStack seven) { this.seven = seven != null ? seven : ItemStack.EMPTY; }

    public ItemStack getEight() { return eight; }
    public void setEight(ItemStack eight) { this.eight = eight != null ? eight : ItemStack.EMPTY; }

    /**
     * 克隆数据
     * Clone data
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