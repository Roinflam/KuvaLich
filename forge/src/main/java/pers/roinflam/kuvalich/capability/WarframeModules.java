// 文件：WarframeModules.java
// 路径：src/main/java/pers/roinflam/kuvalich/blocks/capability/WarframeModules.java
package pers.roinflam.kuvalich.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber
public class WarframeModules implements INBTSerializable<NBTTagCompound> {
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

    // Getters and Setters
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

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("one", getOne().serializeNBT());
        nbt.setTag("two", getTwo().serializeNBT());
        nbt.setTag("three", getThree().serializeNBT());
        nbt.setTag("four", getFour().serializeNBT());
        nbt.setTag("five", getFive().serializeNBT());
        nbt.setTag("six", getSix().serializeNBT());
        nbt.setTag("seven", getSeven().serializeNBT());
        nbt.setTag("eight", getEight().serializeNBT());
        return nbt;
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone evt) {
        Entity entity = evt.getEntity();
        if (entity == null || entity.world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer entityPlayer = (EntityPlayer) entity;
        WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
        WarframeModules originalWarframeModules = evt.getOriginal().getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

        if (warframeModules != null && originalWarframeModules != null) {
            warframeModules.clone(originalWarframeModules);
        }
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }

        this.one = new ItemStack(nbt.getCompoundTag("one"));
        this.two = new ItemStack(nbt.getCompoundTag("two"));
        this.three = new ItemStack(nbt.getCompoundTag("three"));
        this.four = new ItemStack(nbt.getCompoundTag("four"));
        this.five = new ItemStack(nbt.getCompoundTag("five"));
        this.six = new ItemStack(nbt.getCompoundTag("six"));
        this.seven = new ItemStack(nbt.getCompoundTag("seven"));
        this.eight = new ItemStack(nbt.getCompoundTag("eight"));
    }
}