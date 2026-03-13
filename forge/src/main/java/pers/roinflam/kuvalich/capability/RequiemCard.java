package pers.roinflam.kuvalich.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 安魂卡片Capability
 * Requiem Card Capability
 *
 * 存储玩家的安魂卡片数据、解密进度、没收物品等
 * Stores player's requiem card data, decryption progress, confiscated items, etc.
 */
public class RequiemCard {

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

    /** 被玄骸没收的物品列表 / Confiscated items list */
    private List<ItemStack> confiscatedItems;

    public RequiemCard() {
        reset();
        this.confiscatedItems = new ArrayList<>();
    }

    /**
     * 克隆数据
     * Clone data
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

        // 克隆没收物品列表（深拷贝每个ItemStack）
        // Clone confiscated items list (deep copy each ItemStack)
        this.confiscatedItems = new ArrayList<>();
        for (ItemStack item : requiemCard.confiscatedItems) {
            if (item != null && !item.isEmpty()) {
                this.confiscatedItems.add(item.copy());
            }
        }
    }

    // ==================== Getters and Setters ====================

    public int getUnlockedCardStatus() { return unlockedCardStatus; }
    public void setUnlockedCardStatus(int level) { this.unlockedCardStatus = level; }

    public int getDecryptionProgress() { return decryptionProgress; }
    public void setDecryptionProgress(int decryptionProgress) { this.decryptionProgress = decryptionProgress; }

    public ItemStack getOneCard() { return oneCard; }
    public void setOneCard(ItemStack oneCard) { this.oneCard = oneCard != null ? oneCard : ItemStack.EMPTY; }

    public ItemStack getTwoCard() { return twoCard; }
    public void setTwoCard(ItemStack twoCard) { this.twoCard = twoCard != null ? twoCard : ItemStack.EMPTY; }

    public ItemStack getThreeCard() { return threeCard; }
    public void setThreeCard(ItemStack threeCard) { this.threeCard = threeCard != null ? threeCard : ItemStack.EMPTY; }

    public int getOneAnswer() { return oneAnswer; }
    public void setOneAnswer(int oneAnswer) { this.oneAnswer = oneAnswer; }

    public int getTwoAnswer() { return twoAnswer; }
    public void setTwoAnswer(int twoAnswer) { this.twoAnswer = twoAnswer; }

    public int getThreeAnswer() { return threeAnswer; }
    public void setThreeAnswer(int threeAnswer) { this.threeAnswer = threeAnswer; }

    public int getKuvaLevel() { return kuvaLevel; }
    public void setKuvaLevel(int kuvaLevel) { this.kuvaLevel = kuvaLevel; }

    public int getOneRiddle() { return oneRiddle; }
    public void setOneRiddle(int oneRiddle) { this.oneRiddle = oneRiddle; }

    public int getTwoRiddle() { return twoRiddle; }
    public void setTwoRiddle(int twoRiddle) { this.twoRiddle = twoRiddle; }

    public int getThreeRiddle() { return threeRiddle; }
    public void setThreeRiddle(int threeRiddle) { this.threeRiddle = threeRiddle; }

    public int getMinimumLevelWeapon() { return minimumLevelWeapon; }
    public void setMinimumLevelWeapon(int minimumLevelWeapon) { this.minimumLevelWeapon = minimumLevelWeapon; }

    public int getMaximumLevelWeapon() { return maximumLevelWeapon; }
    public void setMaximumLevelWeapon(int maximumLevelWeapon) { this.maximumLevelWeapon = maximumLevelWeapon; }

    // ==================== 逻辑方法 / Logic Methods ====================

    /**
     * 检查是否有任意一个谜语被解开
     * Check if any riddle is unlocked
     *
     * @return true表示至少有一个谜语被解开
     */
    public boolean hasAnyRiddleUnlocked() {
        return oneRiddle != -1 || twoRiddle != -1 || threeRiddle != -1;
    }

    /**
     * 检查是否所有谜语都已解开
     * Check if all riddles are unlocked
     *
     * @return true表示三个谜语全部解开
     */
    public boolean isUnlockAll() {
        return oneRiddle != -1 && twoRiddle != -1 && threeRiddle != -1;
    }

    /**
     * 检查是否包含指定卡片
     * Check if contains specified card
     *
     * @param id 卡片ID
     * @return true表示谜语中包含该卡片
     */
    public boolean containCard(int id) {
        return oneRiddle == id || twoRiddle == id || threeRiddle == id;
    }

    /**
     * 检查卡片槽位是否已准备好
     * Check if card slots are ready
     *
     * @return true表示三个槽位都有卡片
     */
    public boolean isReadyCard() {
        return !oneCard.isEmpty() && !twoCard.isEmpty() && !threeCard.isEmpty();
    }

    /**
     * 检查答案是否完全正确
     * Check if answer is completely correct
     *
     * @return true表示三张卡片顺序和内容完全正确
     */
    public boolean isCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        AbstractRequiemCard one = (AbstractRequiemCard) oneCard.getItem();
        AbstractRequiemCard two = (AbstractRequiemCard) twoCard.getItem();
        AbstractRequiemCard three = (AbstractRequiemCard) threeCard.getItem();

        return one.getID() == oneAnswer && two.getID() == twoAnswer && three.getID() == threeAnswer;
    }

    /**
     * 检查第一个答案是否正确
     * Check if first answer is correct
     *
     * @return true表示第一张卡片正确
     */
    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        AbstractRequiemCard one = (AbstractRequiemCard) oneCard.getItem();
        return one.getID() == oneAnswer;
    }

    /**
     * 检查第二个答案是否正确
     * Check if second answer is correct
     *
     * @return true表示第二张卡片正确
     */
    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }

        AbstractRequiemCard two = (AbstractRequiemCard) twoCard.getItem();
        return two.getID() == twoAnswer;
    }

    /**
     * 获取指定等级的锁定卡片
     * Get locked card for specified level
     *
     * @param level 等级(1/2/3)
     * @return 对应的答案卡片ID，-1表示无效
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
     * 获取当前阶段所需点数
     * Get points required for current stage
     *
     * @return 当前阶段所需的解密点数，-1表示已全部解锁
     */
    public int getPointsRequired() {
        switch (unlockedCardStatus) {
            case 0: return ModConfig.KUVA_LICH.firstStage.get();
            case 1: return ModConfig.KUVA_LICH.secondStage.get();
            case 2: return ModConfig.KUVA_LICH.thirdStage.get();
            default: return -1;
        }
    }

    /**
     * 添加药水进度
     * Add potion progress
     *
     * @param potion 要添加的进度点数
     * @return true表示添加成功
     */
    public boolean addPotion(int potion) {
        switch (unlockedCardStatus) {
            case 0:
                return processStage(potion, ModConfig.KUVA_LICH.firstStage.get());
            case 1:
                return processStage(potion, ModConfig.KUVA_LICH.secondStage.get());
            case 2:
                return processStage(potion, ModConfig.KUVA_LICH.thirdStage.get());
            default:
                return false;
        }
    }

    /**
     * 处理阶段进度
     * Process stage progress
     *
     * @param potion    新增的进度点数
     * @param threshold 当前阶段的阈值
     * @return true表示处理成功
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
     * 生成随机答案（保证三个答案互不相同）
     * Generate random answers (ensure all three are different)
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
     * 消耗卡片耐久并返回仍有耐久的卡片列表
     * Consume card durability and return cards that still have durability remaining
     *
     * 每张卡片耐久降低1点：
     * - 如果卡片仍有剩余耐久，加入返回列表（归还给玩家）
     * - 如果卡片耐久耗尽，不加入列表（卡片消失）
     *
     * Each card loses 1 durability:
     * - If card still has remaining durability, add to return list (give back to player)
     * - If card durability is depleted, don't add to list (card is consumed)
     *
     * @return 仍有耐久的卡片列表 / List of cards that still have durability
     */
    public List<ItemStack> consumeCardsAndGetSurvivors() {
        List<ItemStack> survivors = new ArrayList<>();

        // 依次处理三张卡的耐久
        // Process durability for all three cards
        oneCard = processCardConsumption(oneCard, survivors);
        twoCard = processCardConsumption(twoCard, survivors);
        threeCard = processCardConsumption(threeCard, survivors);

        return survivors;
    }

    /**
     * 处理单张卡片的耐久消耗
     * Process single card's durability consumption
     *
     * @param card      要处理的卡片 / Card to process
     * @param survivors 存活卡片收集列表 / Surviving cards collection list
     * @return ItemStack.EMPTY（卡片已从槽位移除）/ ItemStack.EMPTY (card removed from slot)
     */
    private ItemStack processCardConsumption(ItemStack card, List<ItemStack> survivors) {
        if (card == null || card.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 如果卡片不可损坏（无耐久条），直接消耗掉
        // If card is not damageable (no durability bar), consume it directly
        if (!card.isDamageableItem()) {
            return ItemStack.EMPTY;
        }

        // 增加1点损坏值（降低1点耐久）
        // Increase damage by 1 (reduce 1 durability)
        int newDamage = card.getDamageValue() + 1;

        // 判断卡片是否还有剩余耐久
        // Check if card still has remaining durability
        // maxDamage=3 时：damage 0→1→2 仍可用，damage 3 时耗尽
        // When maxDamage=3: damage 0→1→2 still usable, damage 3 means depleted
        if (newDamage < card.getMaxDamage()) {
            // 卡片仍有耐久，设置新损坏值并加入存活列表
            // Card still has durability, set new damage and add to survivors
            card.setDamageValue(newDamage);
            survivors.add(card.copy());
        }
        // 否则耐久耗尽，卡片消失，不加入survivors

        // 无论如何都从槽位清除（卡片要么归还玩家背包，要么消失）
        // Clear from slot regardless (card either returns to player inventory or disappears)
        return ItemStack.EMPTY;
    }

    /**
     * 重置所有数据（不处理卡片耐久，耐久由consumeCardsAndGetSurvivors单独处理）
     * Reset all data (does NOT handle card durability, durability is handled separately)
     */
    public void reset() {
        // 初始化ItemStack字段为EMPTY（防止null）
        // Initialize ItemStack fields to EMPTY (prevent null)
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
        // 注意：reset时不清空没收物品
        // Note: don't clear confiscated items on reset
    }

    /**
     * 添加卡片到随机槽位
     * Add card to random slot
     *
     * @param level 解锁的等级
     */
    public void addCard(int level) {
        if (isUnlockAll()) {
            return;
        }

        int id = getLockCard(level);
        if (id == -1) {
            return;
        }

        // 随机选择槽位
        // Randomly select slot
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

    // ==================== 没收物品相关方法 / Confiscated Items Methods ====================

    /**
     * 获取被没收的物品列表（返回副本，防止外部修改）
     * Get confiscated items list (returns copy to prevent external modification)
     *
     * @return 没收物品列表的副本
     */
    public List<ItemStack> getConfiscatedItems() {
        return new ArrayList<>(confiscatedItems);
    }

    /**
     * 添加被没收的物品
     * 上限从配置文件 maxConfiscatedItems 读取，不再硬编码
     *
     * Add confiscated item
     * Limit is read from config maxConfiscatedItems, no longer hardcoded
     *
     * @param itemStack 要没收的物品 / item to confiscate
     * @return true表示成功没收，false表示已达上限无法没收
     *         true = successfully confiscated, false = limit reached
     */
    public boolean addConfiscatedItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }

        // 从配置读取没收上限（默认64，配置范围0~1024）
        // Read confiscation limit from config (default 64, range 0~1024)
        int maxCount = ModConfig.KUVA_LICH.maxConfiscatedItems.get();
        if (maxCount <= 0 || confiscatedItems.size() >= maxCount) {
            return false;
        }

        confiscatedItems.add(itemStack.copy());
        return true;
    }

    /**
     * 检查是否还能没收更多物品
     * 上限从配置文件 maxConfiscatedItems 读取
     *
     * Check if more items can be confiscated
     * Limit is read from config maxConfiscatedItems
     *
     * @return true表示还能没收，false表示已达上限或配置为0
     *         true = can confiscate more, false = limit reached or disabled
     */
    public boolean canConfiscateMore() {
        int maxCount = ModConfig.KUVA_LICH.maxConfiscatedItems.get();
        if (maxCount <= 0) {
            return false;
        }
        return confiscatedItems.size() < maxCount;
    }

    /**
     * 清空没收物品列表并返回所有物品
     * Clear and get all confiscated items
     *
     * @return 之前被没收的所有物品
     */
    public List<ItemStack> clearAndGetConfiscatedItems() {
        List<ItemStack> items = new ArrayList<>(confiscatedItems);
        confiscatedItems.clear();
        return items;
    }

    /**
     * 检查是否有被没收的物品
     * Check if has confiscated items
     *
     * @return true表示有被没收的物品
     */
    public boolean hasConfiscatedItems() {
        return !confiscatedItems.isEmpty();
    }

    /**
     * 获取被没收物品的数量
     * Get confiscated item count
     *
     * @return 当前被没收的物品总数
     */
    public int getConfiscatedItemCount() {
        return confiscatedItems.size();
    }

    // ==================== NBT序列化 / NBT Serialization ====================

    /**
     * 序列化到NBT
     * Serialize to NBT
     *
     * @return 包含所有数据的CompoundTag
     */
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        // 添加null检查，确保安全序列化
        // Add null check to ensure safe serialization
        nbt.put("oneCard", (oneCard != null ? oneCard : ItemStack.EMPTY).save(new CompoundTag()));
        nbt.put("twoCard", (twoCard != null ? twoCard : ItemStack.EMPTY).save(new CompoundTag()));
        nbt.put("threeCard", (threeCard != null ? threeCard : ItemStack.EMPTY).save(new CompoundTag()));

        nbt.putInt("oneRiddle", oneRiddle);
        nbt.putInt("twoRiddle", twoRiddle);
        nbt.putInt("threeRiddle", threeRiddle);
        nbt.putInt("oneAnswer", oneAnswer);
        nbt.putInt("twoAnswer", twoAnswer);
        nbt.putInt("threeAnswer", threeAnswer);
        nbt.putInt("unlockedCardStatus", unlockedCardStatus);
        nbt.putInt("decryptionProgress", decryptionProgress);
        nbt.putInt("kuvaLevel", kuvaLevel);
        nbt.putInt("minimumLevelWeapon", minimumLevelWeapon);
        nbt.putInt("maximumLevelWeapon", maximumLevelWeapon);

        // 序列化没收物品列表
        // Serialize confiscated items list
        ListTag confiscatedList = new ListTag();
        for (ItemStack item : confiscatedItems) {
            if (!item.isEmpty()) {
                confiscatedList.add(item.save(new CompoundTag()));
            }
        }
        nbt.put("confiscatedItems", confiscatedList);

        return nbt;
    }

    /**
     * 从NBT反序列化
     * Deserialize from NBT
     *
     * @param nbt 要反序列化的CompoundTag
     */
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }

        this.oneCard = ItemStack.of(nbt.getCompound("oneCard"));
        this.twoCard = ItemStack.of(nbt.getCompound("twoCard"));
        this.threeCard = ItemStack.of(nbt.getCompound("threeCard"));

        this.oneRiddle = nbt.getInt("oneRiddle");
        this.twoRiddle = nbt.getInt("twoRiddle");
        this.threeRiddle = nbt.getInt("threeRiddle");
        this.oneAnswer = nbt.getInt("oneAnswer");
        this.twoAnswer = nbt.getInt("twoAnswer");
        this.threeAnswer = nbt.getInt("threeAnswer");
        this.unlockedCardStatus = nbt.getInt("unlockedCardStatus");
        this.decryptionProgress = nbt.getInt("decryptionProgress");
        this.kuvaLevel = nbt.getInt("kuvaLevel");
        this.minimumLevelWeapon = nbt.getInt("minimumLevelWeapon");
        this.maximumLevelWeapon = nbt.getInt("maximumLevelWeapon");

        // 反序列化没收物品列表
        // Deserialize confiscated items list
        this.confiscatedItems = new ArrayList<>();
        if (nbt.contains("confiscatedItems", Tag.TAG_LIST)) {
            ListTag confiscatedList = nbt.getList("confiscatedItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < confiscatedList.size(); i++) {
                ItemStack item = ItemStack.of(confiscatedList.getCompound(i));
                if (!item.isEmpty()) {
                    confiscatedItems.add(item);
                }
            }
        }
    }

    // ==================== Provider内部类 / Provider Inner Class ====================

    /**
     * Capability Provider
     * 负责提供RequiemCard实例和NBT序列化/反序列化
     */
    public static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

        private final RequiemCard instance = new RequiemCard();
        private final LazyOptional<RequiemCard> optional = LazyOptional.of(() -> instance);

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CapabilityRegistryHandler.REQUIEM_CARD.orEmpty(cap, optional);
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