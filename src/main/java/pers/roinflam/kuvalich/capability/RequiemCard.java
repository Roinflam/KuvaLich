package pers.roinflam.kuvalich.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 赤毒卡片能力类
 * 用于存储玩家的赤毒卡片数据和解密进度
 */
@Mod.EventBusSubscriber
public class RequiemCard implements INBTSerializable<NBTTagCompound> {
    // 三个卡片槽位
    private ItemStack oneCard;
    private ItemStack twoCard;
    private ItemStack threeCard;

    // 三个谜语槽位（已解锁的卡片ID）
    private int oneRiddle;
    private int twoRiddle;
    private int threeRiddle;

    // 三个答案槽位（正确的卡片ID）
    private int oneAnswer;
    private int twoAnswer;
    private int threeAnswer;

    // 解锁状态和解密进度
    private int unlockedCardStatus;
    private int decryptionProgress;

    // 库瓦等级相关
    private int kuvaLevel;
    private int minimumLevelWeapon;
    private int maximumLevelWeapon;

    // 被玄骸没收的物品列表
    private List<ItemStack> confiscatedItems;

    /**
     * 构造函数
     * 初始化所有字段为默认值
     */
    public RequiemCard() {
        reset();
        this.confiscatedItems = new ArrayList<>();
    }

    /**
     * 玩家克隆事件监听器
     * 用于在玩家死亡重生时保留能力数据
     *
     * @param evt 玩家克隆事件
     */
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

    /**
     * 克隆能力数据
     *
     * @param requiemCard 要克隆的源数据
     */
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
        // 克隆没收物品列表
        this.confiscatedItems = new ArrayList<>(requiemCard.getConfiscatedItems());
    }

    // ==================== Getters and Setters ====================

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

    // ==================== 状态检查方法 ====================

    /**
     * 检查是否有任意一个谜语被解开
     * 用于判断是否触发没收机制
     *
     * @return 是否有谜语被解开
     */
    public boolean hasAnyRiddleUnlocked() {
        return oneRiddle != -1 || twoRiddle != -1 || threeRiddle != -1;
    }

    /**
     * 检查是否所有谜语都已解开
     *
     * @return 是否全部解开
     */
    public boolean isUnlockAll() {
        return oneRiddle != -1 && twoRiddle != -1 && threeRiddle != -1;
    }

    /**
     * 检查是否包含指定ID的卡片
     *
     * @param id 卡片ID
     * @return 是否包含
     */
    public boolean containCard(int id) {
        return oneRiddle == id || twoRiddle == id || threeRiddle == id;
    }

    /**
     * 检查是否三个卡片槽位都已放置卡片
     *
     * @return 是否准备就绪
     */
    public boolean isReadyCard() {
        return !oneCard.isEmpty() && !twoCard.isEmpty() && !threeCard.isEmpty();
    }

    /**
     * 检查当前卡片排列是否为正确答案
     *
     * @return 是否正确
     */
    public boolean isCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
        RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
        RequiemCardBase three = (RequiemCardBase) threeCard.getItem();

        return one.getID() == oneAnswer && two.getID() == twoAnswer && three.getID() == threeAnswer;
    }

    /**
     * 检查第一个卡片是否正确
     *
     * @return 是否正确
     */
    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase one = (RequiemCardBase) oneCard.getItem();
        return one.getID() == oneAnswer;
    }

    /**
     * 检查第二个卡片是否正确
     *
     * @return 是否正确
     */
    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        RequiemCardBase two = (RequiemCardBase) twoCard.getItem();
        return two.getID() == twoAnswer;
    }

    /**
     * 获取指定等级的锁定卡片ID
     *
     * @param level 等级（1-3）
     * @return 卡片ID，如果等级无效则返回-1
     */
    public int getLockCard(int level) {
        switch (level) {
            case 1: return oneAnswer;
            case 2: return twoAnswer;
            case 3: return threeAnswer;
            default: return -1;
        }
    }

    /**
     * 获取当前阶段所需的点数
     *
     * @return 所需点数，如果阶段无效则返回-1
     */
    public int getPointsRequired() {
        switch (unlockedCardStatus) {
            case 0: return ModConfig.KUVA_LICH.firstStage;
            case 1: return ModConfig.KUVA_LICH.secondStage;
            case 2: return ModConfig.KUVA_LICH.thirdStage;
            default: return -1;
        }
    }

    // ==================== 进度处理方法 ====================

    /**
     * 添加解密点数
     *
     * @param potion 点数
     * @return 是否添加成功
     */
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
     *
     * @param potion 添加的点数
     * @param threshold 阶段阈值
     * @return 是否处理成功
     */
    private boolean processStage(int potion, int threshold) {
        decryptionProgress += potion;

        if (decryptionProgress >= threshold) {
            // 第一阶段完成时生成答案
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
     * 生成随机的三个不重复答案
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

    /**
     * 重置所有数据
     * 注意：不会清空没收物品列表
     */
    public void reset() {
        // 降低卡片耐久度
        degradeCard(oneCard);
        degradeCard(twoCard);
        degradeCard(threeCard);

        // 重置卡片槽位（修复：确保初始化为EMPTY而不是null）
        this.oneCard = ItemStack.EMPTY;
        this.twoCard = ItemStack.EMPTY;
        this.threeCard = ItemStack.EMPTY;

        // 重置谜语和答案
        this.oneRiddle = -1;
        this.twoRiddle = -1;
        this.threeRiddle = -1;
        this.oneAnswer = -1;
        this.twoAnswer = -1;
        this.threeAnswer = -1;

        // 重置进度
        this.unlockedCardStatus = 0;
        this.decryptionProgress = 0;
        this.kuvaLevel = 0;

        // 注意：reset时不清空没收物品，因为解密成功时需要先掉落再reset
    }

    /**
     * 降低卡片耐久度
     *
     * @param card 要降低耐久的卡片
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

    /**
     * 添加解锁的卡片到随机槽位
     *
     * @param level 当前等级
     */
    public void addCard(int level) {
        if (isUnlockAll()) {
            return;
        }

        int id = getLockCard(level);
        if (id == -1) {
            return;
        }

        // 随机选择一个空槽位
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

    // ==================== 没收物品相关方法 ====================

    /**
     * 获取被没收的物品列表
     *
     * @return 没收物品列表的副本
     */
    public List<ItemStack> getConfiscatedItems() {
        return new ArrayList<>(confiscatedItems);
    }

    /**
     * 添加被没收的物品
     *
     * @param itemStack 要没收的物品
     */
    public void addConfiscatedItem(ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            confiscatedItems.add(itemStack.copy());
        }
    }

    /**
     * 清空没收物品列表并返回所有物品
     *
     * @return 被没收的所有物品
     */
    public List<ItemStack> clearAndGetConfiscatedItems() {
        List<ItemStack> items = new ArrayList<>(confiscatedItems);
        confiscatedItems.clear();
        return items;
    }

    /**
     * 检查是否有被没收的物品
     *
     * @return 是否有没收物品
     */
    public boolean hasConfiscatedItems() {
        return !confiscatedItems.isEmpty();
    }

    /**
     * 获取被没收物品的数量
     *
     * @return 没收物品数量
     */
    public int getConfiscatedItemCount() {
        return confiscatedItems.size();
    }

    // ==================== NBT序列化 ====================

    /**
     * 序列化为NBT
     *
     * @return NBT数据
     */
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();

        // 修复：添加空值检查，防止NullPointerException
        nbt.setTag("oneCard", oneCard != null ? oneCard.serializeNBT() : ItemStack.EMPTY.serializeNBT());
        nbt.setTag("twoCard", twoCard != null ? twoCard.serializeNBT() : ItemStack.EMPTY.serializeNBT());
        nbt.setTag("threeCard", threeCard != null ? threeCard.serializeNBT() : ItemStack.EMPTY.serializeNBT());

        // 序列化谜语和答案
        nbt.setInteger("oneRiddle", oneRiddle);
        nbt.setInteger("twoRiddle", twoRiddle);
        nbt.setInteger("threeRiddle", threeRiddle);
        nbt.setInteger("oneAnswer", oneAnswer);
        nbt.setInteger("twoAnswer", twoAnswer);
        nbt.setInteger("threeAnswer", threeAnswer);

        // 序列化进度
        nbt.setInteger("unlockedCardStatus", unlockedCardStatus);
        nbt.setInteger("decryptionProgress", decryptionProgress);

        // 序列化等级相关
        nbt.setInteger("kuvaLevel", kuvaLevel);
        nbt.setInteger("minimumLevelWeapon", minimumLevelWeapon);
        nbt.setInteger("maximumLevelWeapon", maximumLevelWeapon);

        // 序列化没收物品列表
        NBTTagList confiscatedList = new NBTTagList();
        for (ItemStack item : confiscatedItems) {
            if (!item.isEmpty()) {
                confiscatedList.appendTag(item.serializeNBT());
            }
        }
        nbt.setTag("confiscatedItems", confiscatedList);

        return nbt;
    }

    /**
     * 从NBT反序列化
     *
     * @param nbt NBT数据
     */
    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }

        // 反序列化卡片
        this.oneCard = new ItemStack(nbt.getCompoundTag("oneCard"));
        this.twoCard = new ItemStack(nbt.getCompoundTag("twoCard"));
        this.threeCard = new ItemStack(nbt.getCompoundTag("threeCard"));

        // 反序列化谜语和答案
        this.oneRiddle = nbt.getInteger("oneRiddle");
        this.twoRiddle = nbt.getInteger("twoRiddle");
        this.threeRiddle = nbt.getInteger("threeRiddle");
        this.oneAnswer = nbt.getInteger("oneAnswer");
        this.twoAnswer = nbt.getInteger("twoAnswer");
        this.threeAnswer = nbt.getInteger("threeAnswer");

        // 反序列化进度
        this.unlockedCardStatus = nbt.getInteger("unlockedCardStatus");
        this.decryptionProgress = nbt.getInteger("decryptionProgress");

        // 反序列化等级相关
        this.kuvaLevel = nbt.getInteger("kuvaLevel");
        this.minimumLevelWeapon = nbt.getInteger("minimumLevelWeapon");
        this.maximumLevelWeapon = nbt.getInteger("maximumLevelWeapon");

        // 反序列化没收物品列表
        this.confiscatedItems = new ArrayList<>();
        if (nbt.hasKey("confiscatedItems", Constants.NBT.TAG_LIST)) {
            NBTTagList confiscatedList = nbt.getTagList("confiscatedItems", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < confiscatedList.tagCount(); i++) {
                ItemStack item = new ItemStack(confiscatedList.getCompoundTagAt(i));
                if (!item.isEmpty()) {
                    confiscatedItems.add(item);
                }
            }
        }
    }
}