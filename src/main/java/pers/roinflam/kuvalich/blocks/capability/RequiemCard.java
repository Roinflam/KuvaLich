package pers.roinflam.kuvalich.blocks.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

@Mod.EventBusSubscriber
public class RequiemCard implements INBTSerializable<NBTTagCompound> {
    private ItemStack oneCard;
    private ItemStack twoCard;
    private ItemStack threeCard;

    private int oneRiddle;
    private int twoRiddle;
    private int threeRiddle;
    private int oneAnswer;
    private int twoAnswer;
    private int threeAnswer;
    private int unlockedCardStatus;
    private int decryptionProgress;

    private int kuvaLevel;
    private int minimumLevelWeapon;
    private int maximumLevelWeapon;


    public RequiemCard() {
        this.oneCard = ItemStack.EMPTY;
        this.twoCard = ItemStack.EMPTY;
        this.threeCard = ItemStack.EMPTY;
        this.oneRiddle = -1;
        this.twoRiddle = -1;
        this.threeRiddle = -1;
        this.oneAnswer = -1;
        this.twoAnswer = -1;
        this.threeAnswer = -1;
        this.unlockedCardStatus = 0;
        this.decryptionProgress = 0;
        this.kuvaLevel = 0;
        this.minimumLevelWeapon = ConfigKuvaLich.baseMinimumLevel;
        this.maximumLevelWeapon = ConfigKuvaLich.baseMaximumLevel;
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.@NotNull Clone evt) {
        Entity entity = evt.getEntity();
        if (!entity.world.isRemote && entity instanceof EntityPlayer) {
            @NotNull EntityPlayer entityPlayer = (EntityPlayer) entity;
            @Nullable RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
            @Nullable RequiemCard originalRequiemCard = evt.getOriginal().getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
            requiemCard.clone(originalRequiemCard);
        }
    }

    public void clone(@NotNull RequiemCard requiemCard) {
        this.setOneCard(requiemCard.getOneCard());
        this.setTwoCard(requiemCard.getTwoCard());
        this.setThreeCard(requiemCard.getThreeCard());
        this.setOneRiddle(requiemCard.getOneRiddle());
        this.setTwoRiddle(requiemCard.getTwoRiddle());
        this.setThreeRiddle(requiemCard.getThreeRiddle());
        this.setOneAnswer(requiemCard.getOneAnswer());
        this.setTwoAnswer(requiemCard.getTwoAnswer());
        this.setThreeAnswer(requiemCard.getThreeAnswer());
        this.setUnlockedCardStatus(requiemCard.getUnlockedCardStatus());
        this.setDecryptionProgress(requiemCard.getDecryptionProgress());
        this.setKuvaLevel(requiemCard.getKuvaLevel());
        this.setMinimumLevelWeapon(requiemCard.getMinimumLevelWeapon());
        this.setMaximumLevelWeapon(requiemCard.getMaximumLevelWeapon());
    }

    public int getUnlockedCardStatus() {
        return unlockedCardStatus;
    }

    public void setUnlockedCardStatus(int level) {
        this.unlockedCardStatus = level;
    }

    public int getDecryptionProgress() {
        return decryptionProgress;
    }

    public void setDecryptionProgress(int decryptionProgress) {
        this.decryptionProgress = decryptionProgress;
    }

    public ItemStack getOneCard() {
        return oneCard;
    }

    public void setOneCard(ItemStack oneCard) {
        this.oneCard = oneCard;
    }

    public ItemStack getTwoCard() {
        return twoCard;
    }

    public void setTwoCard(ItemStack twoCard) {
        this.twoCard = twoCard;
    }

    public int getOneAnswer() {
        return oneAnswer;
    }

    public void setOneAnswer(int oneAnswer) {
        this.oneAnswer = oneAnswer;
    }

    public int getTwoAnswer() {
        return twoAnswer;
    }

    public void setTwoAnswer(int twoAnswer) {
        this.twoAnswer = twoAnswer;
    }

    public int getThreeAnswer() {
        return threeAnswer;
    }

    public void setThreeAnswer(int threeAnswer) {
        this.threeAnswer = threeAnswer;
    }

    public int getKuvaLevel() {
        return kuvaLevel;
    }

    public void setKuvaLevel(int kuvaLevel) {
        this.kuvaLevel = kuvaLevel;
    }

    public ItemStack getThreeCard() {
        return threeCard;
    }

    public void setThreeCard(ItemStack threeCard) {
        this.threeCard = threeCard;
    }

    public int getOneRiddle() {
        return oneRiddle;
    }

    public void setOneRiddle(int oneRiddle) {
        this.oneRiddle = oneRiddle;
    }

    public int getTwoRiddle() {
        return twoRiddle;
    }

    public void setTwoRiddle(int twoRiddle) {
        this.twoRiddle = twoRiddle;
    }

    public int getThreeRiddle() {
        return threeRiddle;
    }

    public void setThreeRiddle(int threeRiddle) {
        this.threeRiddle = threeRiddle;
    }

    public boolean isUnlockAll() {
        return oneRiddle != -1 && twoRiddle != -1 && threeRiddle != -1;
    }

    public boolean containCard(int id) {
        return oneRiddle == id || twoRiddle == id || threeRiddle == id;
    }

    public boolean isReadyCard() {
        return !oneCard.isEmpty() && !twoCard.isEmpty() && !threeCard.isEmpty();
    }

    public boolean isCorrectAnswer() {
        if (unlockedCardStatus > 0 && isReadyCard()) {
            @NotNull RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
            @NotNull RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
            @NotNull RequiemCardBase three = (RequiemCardBase) threeCard.getItem();
            return one.getID() == oneAnswer && two.getID() == twoAnswer && three.getID() == threeAnswer;
        }
        return false;
    }

    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus > 0 && isReadyCard()) {
            @NotNull RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
            return one.getID() == oneAnswer;
        }
        return false;
    }

    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus > 0 && isReadyCard()) {
            @NotNull RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
            return two.getID() == twoAnswer;
        }
        return false;
    }

    public int getLockCard(int level) {
        if (level == 1) {
            return oneAnswer;
        }
        if (level == 2) {
            return twoAnswer;
        }
        if (level == 3) {
            return threeAnswer;
        }
        return -1;
    }

    public int getPointsRequired() {
        switch (unlockedCardStatus) {
            case 0: {
                return ConfigKuvaLich.firstStage;
            }
            case 1: {
                return ConfigKuvaLich.secondStage;
            }
            case 2: {
                return ConfigKuvaLich.thirdStage;
            }
            default: {
                return -1;
            }
        }
    }

    public boolean addPotion(int potion) {
        switch (unlockedCardStatus) {
            case 0: {
                decryptionProgress += potion;
                if (decryptionProgress >= ConfigKuvaLich.firstStage) {
                    int one, two, three;
                    do {
                        one = RandomUtil.getInt(0, 7);
                        two = RandomUtil.getInt(0, 7);
                        three = RandomUtil.getInt(0, 7);
                    } while (one == two || one == three || two == three);
                    this.oneAnswer = one;
                    this.twoAnswer = two;
                    this.threeAnswer = three;
                    unlockedCardStatus += 1;
                    decryptionProgress -= ConfigKuvaLich.firstStage;
                    addCard(unlockedCardStatus);
                }
                return true;
            }
            case 1: {
                decryptionProgress += potion;
                if (decryptionProgress >= ConfigKuvaLich.secondStage) {
                    unlockedCardStatus += 1;
                    decryptionProgress -= ConfigKuvaLich.secondStage;
                    addCard(unlockedCardStatus);
                }
                return true;
            }
            case 2: {
                decryptionProgress += potion;
                if (decryptionProgress >= ConfigKuvaLich.thirdStage) {
                    unlockedCardStatus += 1;
                    decryptionProgress = 0;
                    addCard(unlockedCardStatus);
                }
                return true;
            }
            default: {
                return false;
            }
        }
    }

    public void reset() {
        if (oneCard.getItemDamage() < 2) {
            oneCard.setItemDamage(oneCard.getItemDamage() + 1);
        } else {
            if (oneCard.isItemStackDamageable()) {
                this.oneCard = ItemStack.EMPTY;
            }
        }
        if (twoCard.getItemDamage() < 2) {
            twoCard.setItemDamage(twoCard.getItemDamage() + 1);
        } else {
            if (twoCard.isItemStackDamageable()) {
                this.twoCard = ItemStack.EMPTY;
            }
        }
        if (threeCard.getItemDamage() < 2) {
            threeCard.setItemDamage(threeCard.getItemDamage() + 1);
        } else {
            if (threeCard.isItemStackDamageable()) {
                this.threeCard = ItemStack.EMPTY;
            }
        }
        this.oneRiddle = -1;
        this.twoRiddle = -1;
        this.threeRiddle = -1;
        this.oneAnswer = -1;
        this.twoAnswer = -1;
        this.threeAnswer = -1;
        this.unlockedCardStatus = 0;
        this.decryptionProgress = 0;
        this.kuvaLevel = 0;
    }

    public void addCard(int level) {
        if (!isUnlockAll()) {
            while (true) {
                int id = getLockCard(level);
                int slot = RandomUtil.getInt(0, 2);
                if (slot == 0) {
                    if (oneRiddle == -1) {
                        oneRiddle = id;
                        return;
                    }
                } else if (slot == 1) {
                    if (twoRiddle == -1) {
                        twoRiddle = id;
                        return;
                    }
                } else if (slot == 2) {
                    if (threeRiddle == -1) {
                        threeRiddle = id;
                        return;
                    }
                }
            }
        }
    }

    @Override
    public @NotNull NBTTagCompound serializeNBT() {
        @NotNull NBTTagCompound nbtTagCompound = new NBTTagCompound();
        nbtTagCompound.setTag("oneCard", oneCard.serializeNBT());
        nbtTagCompound.setTag("twoCard", twoCard.serializeNBT());
        nbtTagCompound.setTag("threeCard", threeCard.serializeNBT());
        nbtTagCompound.setInteger("oneRiddle", oneRiddle);
        nbtTagCompound.setInteger("twoRiddle", twoRiddle);
        nbtTagCompound.setInteger("threeRiddle", threeRiddle);
        nbtTagCompound.setInteger("oneAnswer", oneAnswer);
        nbtTagCompound.setInteger("twoAnswer", twoAnswer);
        nbtTagCompound.setInteger("threeAnswer", threeAnswer);
        nbtTagCompound.setInteger("unlockedCardStatus", unlockedCardStatus);
        nbtTagCompound.setInteger("decryptionProgress", decryptionProgress);
        nbtTagCompound.setInteger("kuvaLevel", kuvaLevel);
        nbtTagCompound.setInteger("minimumLevelWeapon", minimumLevelWeapon);
        nbtTagCompound.setInteger("maximumLevelWeapon", maximumLevelWeapon);
        return nbtTagCompound;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound nbt) {
        this.oneCard = new ItemStack(nbt.getCompoundTag("oneCard"));
        this.twoCard = new ItemStack(nbt.getCompoundTag("twoCard"));
        this.threeCard = new ItemStack(nbt.getCompoundTag("threeCard"));
        this.oneRiddle = nbt.getInteger("oneRiddle");
        this.twoRiddle = nbt.getInteger("twoRiddle");
        this.threeRiddle = nbt.getInteger("threeRiddle");
        this.oneAnswer = nbt.getInteger("oneAnswer");
        this.twoAnswer = nbt.getInteger("twoAnswer");
        this.threeAnswer = nbt.getInteger("threeAnswer");
        this.unlockedCardStatus = nbt.getInteger("unlockedCardStatus");
        this.decryptionProgress = nbt.getInteger("decryptionProgress");
        this.kuvaLevel = nbt.getInteger("kuvaLevel");
        this.minimumLevelWeapon = nbt.getInteger("minimumLevelWeapon");
        this.maximumLevelWeapon = nbt.getInteger("maximumLevelWeapon");
    }

    public int getMinimumLevelWeapon() {
        return minimumLevelWeapon;
    }

    public void setMinimumLevelWeapon(int minimumLevelWeapon) {
        this.minimumLevelWeapon = minimumLevelWeapon;
    }

    public int getMaximumLevelWeapon() {
        return maximumLevelWeapon;
    }

    public void setMaximumLevelWeapon(int maximumLevelWeapon) {
        this.maximumLevelWeapon = maximumLevelWeapon;
    }
}
