// 文件：RequiemCard.java
// 路径：src/main/java/pers/roinflam/kuvalich/blocks/capability/RequiemCard.java
package pers.roinflam.kuvalich.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.config.ModConfig;
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
        reset();
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone evt) {
        Entity entity = evt.getEntity();
        if (entity == null || entity.world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer entityPlayer = (EntityPlayer) entity;
        RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        RequiemCard originalRequiemCard = evt.getOriginal().getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);

        if (requiemCard != null && originalRequiemCard != null) {
            requiemCard.clone(originalRequiemCard);
        }
    }

    public void clone(RequiemCard requiemCard) {
        if (requiemCard == null) {
            return;
        }

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
        this.oneCard = oneCard != null ? oneCard : ItemStack.EMPTY;
    }

    public ItemStack getTwoCard() {
        return twoCard;
    }

    public void setTwoCard(ItemStack twoCard) {
        this.twoCard = twoCard != null ? twoCard : ItemStack.EMPTY;
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
        this.threeCard = threeCard != null ? threeCard : ItemStack.EMPTY;
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
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
        RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
        RequiemCardBase three = (RequiemCardBase) threeCard.getItem();

        return one.getID() == oneAnswer && two.getID() == twoAnswer && three.getID() == threeAnswer;
    }

    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
        return one.getID() == oneAnswer;
    }

    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
        return two.getID() == twoAnswer;
    }

    public int getLockCard(int level) {
        switch (level) {
            case 1: return oneAnswer;
            case 2: return twoAnswer;
            case 3: return threeAnswer;
            default: return -1;
        }
    }

    public int getPointsRequired() {
        switch (unlockedCardStatus) {
            case 0: return ModConfig.KUVA_LICH.firstStage;
            case 1: return ModConfig.KUVA_LICH.secondStage;
            case 2: return ModConfig.KUVA_LICH.thirdStage;
            default: return -1;
        }
    }

    public boolean addPotion(int potion) {
        switch (unlockedCardStatus) {
            case 0:
                return processStage(potion, ModConfig.KUVA_LICH.firstStage);
            case 1:
                return processStage(potion, ModConfig.KUVA_LICH.secondStage);
            case 2:
                return processStage(potion, ModConfig.KUVA_LICH.thirdStage);
            default:
                return false;
        }
    }

    /**
     * 处理阶段进度
     */
    private boolean processStage(int potion, int threshold) {
        decryptionProgress += potion;

        if (decryptionProgress >= threshold) {
            if (unlockedCardStatus == 0) {
                generateAnswers();
            }

            unlockedCardStatus++;
            decryptionProgress -= threshold;
            addCard(unlockedCardStatus);
        }

        return true;
    }

    /**
     * 生成答案
     */
    private void generateAnswers() {
        int one, two, three;
        do {
            one = RandomUtil.getInt(0, 7);
            two = RandomUtil.getInt(0, 7);
            three = RandomUtil.getInt(0, 7);
        } while (one == two || one == three || two == three);

        this.oneAnswer = one;
        this.twoAnswer = two;
        this.threeAnswer = three;
    }

    public void reset() {
        // 降低卡片耐久度
        degradeCard(oneCard);
        degradeCard(twoCard);
        degradeCard(threeCard);

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

    /**
     * 降低卡片耐久度
     */
    private void degradeCard(ItemStack card) {
        if (card == null || card.isEmpty()) {
            return;
        }

        if (card.getItemDamage() < 2) {
            card.setItemDamage(card.getItemDamage() + 1);
        } else if (card.isItemStackDamageable()) {
            card = ItemStack.EMPTY;
        }
    }

    public void addCard(int level) {
        if (isUnlockAll()) {
            return;
        }

        int id = getLockCard(level);
        if (id == -1) {
            return;
        }

        // 随机选择槽位
        while (true) {
            int slot = RandomUtil.getInt(0, 2);

            if (slot == 0 && oneRiddle == -1) {
                oneRiddle = id;
                return;
            } else if (slot == 1 && twoRiddle == -1) {
                twoRiddle = id;
                return;
            } else if (slot == 2 && threeRiddle == -1) {
                threeRiddle = id;
                return;
            }
        }
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("oneCard", oneCard.serializeNBT());
        nbt.setTag("twoCard", twoCard.serializeNBT());
        nbt.setTag("threeCard", threeCard.serializeNBT());
        nbt.setInteger("oneRiddle", oneRiddle);
        nbt.setInteger("twoRiddle", twoRiddle);
        nbt.setInteger("threeRiddle", threeRiddle);
        nbt.setInteger("oneAnswer", oneAnswer);
        nbt.setInteger("twoAnswer", twoAnswer);
        nbt.setInteger("threeAnswer", threeAnswer);
        nbt.setInteger("unlockedCardStatus", unlockedCardStatus);
        nbt.setInteger("decryptionProgress", decryptionProgress);
        nbt.setInteger("kuvaLevel", kuvaLevel);
        nbt.setInteger("minimumLevelWeapon", minimumLevelWeapon);
        nbt.setInteger("maximumLevelWeapon", maximumLevelWeapon);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }

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