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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安魂卡片Capability
 * Requiem Card Capability
 *
 * 存储玩家的安魂卡片数据、解密进度、没收物品、模组精通记录等
 * Stores player's requiem card data, decryption progress, confiscated items, module mastery records, etc.
 *
 * ⭐ 新增：模组精通记录（moduleMastery）
 * ⭐ NEW: Module mastery records (moduleMastery)
 * 记录玩家对每种模组type升级过的最高等级，揭示新模组时自动赋予精通等级。
 * Records the highest level a player has upgraded for each module type.
 * When revealing new modules, automatically assigns mastery level.
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

    /**
     * ⭐ 模组精通记录（模组type/精通键 → 最高等级）
     * ⭐ Module mastery records (module type/mastery key → highest level)
     * <p>
     * 键由 ModuleLevelHelper.getMasteryKey() 生成：
     * - 普通模组：直接使用type字符串（如 "vitality", "split_chamber"）
     * - 武器裂罅：按模式分3种独立键（"riven_weapon_module_melee/remote/universal"）
     * - 战甲裂罅：共享一个键（"riven_warframe_module"）
     * <p>
     * 死亡不丢失（跟随RequiemCard Capability持久化）。
     */
    private Map<String, Integer> moduleMastery;

    public RequiemCard() {
        reset();
        this.confiscatedItems = new ArrayList<>();
        this.moduleMastery = new HashMap<>();
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
        this.confiscatedItems = new ArrayList<>();
        for (ItemStack item : requiemCard.confiscatedItems) {
            if (item != null && !item.isEmpty()) {
                this.confiscatedItems.add(item.copy());
            }
        }

        // ⭐ 克隆精通记录 / Clone mastery records
        this.moduleMastery = new HashMap<>(requiemCard.moduleMastery);
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

    public boolean hasAnyRiddleUnlocked() {
        return oneRiddle != -1 || twoRiddle != -1 || threeRiddle != -1;
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
        AbstractRequiemCard one = (AbstractRequiemCard) oneCard.getItem();
        AbstractRequiemCard two = (AbstractRequiemCard) twoCard.getItem();
        AbstractRequiemCard three = (AbstractRequiemCard) threeCard.getItem();
        return one.getID() == oneAnswer && two.getID() == twoAnswer && three.getID() == threeAnswer;
    }

    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }
        AbstractRequiemCard one = (AbstractRequiemCard) oneCard.getItem();
        return one.getID() == oneAnswer;
    }

    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }
        AbstractRequiemCard two = (AbstractRequiemCard) twoCard.getItem();
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
            case 0: return ModConfig.KUVA_LICH.firstStage.get();
            case 1: return ModConfig.KUVA_LICH.secondStage.get();
            case 2: return ModConfig.KUVA_LICH.thirdStage.get();
            default: return -1;
        }
    }

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

    public List<ItemStack> consumeCardsAndGetSurvivors() {
        List<ItemStack> survivors = new ArrayList<>();
        oneCard = processCardConsumption(oneCard, survivors);
        twoCard = processCardConsumption(twoCard, survivors);
        threeCard = processCardConsumption(threeCard, survivors);
        return survivors;
    }

    private ItemStack processCardConsumption(ItemStack card, List<ItemStack> survivors) {
        if (card == null || card.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!card.isDamageableItem()) {
            return ItemStack.EMPTY;
        }
        int newDamage = card.getDamageValue() + 1;
        if (newDamage < card.getMaxDamage()) {
            card.setDamageValue(newDamage);
            survivors.add(card.copy());
        }
        return ItemStack.EMPTY;
    }

    public void reset() {
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
        // 注意：reset时不清空没收物品和精通记录
    }

    public void addCard(int level) {
        if (isUnlockAll()) {
            return;
        }
        int id = getLockCard(level);
        if (id == -1) {
            return;
        }
        while (true) {
            int slot = RandomUtil.getInt(0, 2);
            if (slot == 0 && oneRiddle == -1) { oneRiddle = id; return; }
            else if (slot == 1 && twoRiddle == -1) { twoRiddle = id; return; }
            else if (slot == 2 && threeRiddle == -1) { threeRiddle = id; return; }
        }
    }

    // ==================== 没收物品相关方法 / Confiscated Items Methods ====================

    public List<ItemStack> getConfiscatedItems() {
        return new ArrayList<>(confiscatedItems);
    }

    public boolean addConfiscatedItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        int maxCount = ModConfig.KUVA_LICH.maxConfiscatedItems.get();
        if (maxCount <= 0 || confiscatedItems.size() >= maxCount) {
            return false;
        }
        confiscatedItems.add(itemStack.copy());
        return true;
    }

    public boolean canConfiscateMore() {
        int maxCount = ModConfig.KUVA_LICH.maxConfiscatedItems.get();
        if (maxCount <= 0) {
            return false;
        }
        return confiscatedItems.size() < maxCount;
    }

    public List<ItemStack> clearAndGetConfiscatedItems() {
        List<ItemStack> items = new ArrayList<>(confiscatedItems);
        confiscatedItems.clear();
        return items;
    }

    public boolean hasConfiscatedItems() {
        return !confiscatedItems.isEmpty();
    }

    public int getConfiscatedItemCount() {
        return confiscatedItems.size();
    }

    // ==================== ⭐ 模组精通相关方法 / Module Mastery Methods ====================

    /**
     * 获取指定模组类型的精通等级
     * Get mastery level for a module type
     *
     * @param masteryKey 精通键（由 ModuleLevelHelper.getMasteryKey 生成）
     * @return 精通等级，无记录返回0
     */
    public int getMasteryLevel(String masteryKey) {
        if (masteryKey == null || masteryKey.isEmpty()) {
            return 0;
        }
        return moduleMastery.getOrDefault(masteryKey, 0);
    }

    /**
     * 设置指定模组类型的精通等级（仅当新等级高于已有记录时更新）
     * Set mastery level for a module type (only updates if new level is higher)
     *
     * @param masteryKey 精通键
     * @param level      精通等级
     */
    public void updateMasteryLevel(String masteryKey, int level) {
        if (masteryKey == null || masteryKey.isEmpty()) {
            return;
        }
        int current = moduleMastery.getOrDefault(masteryKey, 0);
        if (level > current) {
            moduleMastery.put(masteryKey, level);
        }
    }

    /**
     * 获取全部精通记录（返回副本）
     * Get all mastery records (returns copy)
     *
     * @return 精通记录Map的副本
     */
    public Map<String, Integer> getAllMastery() {
        return new HashMap<>(moduleMastery);
    }

    // ==================== NBT序列化 / NBT Serialization ====================

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

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
        ListTag confiscatedList = new ListTag();
        for (ItemStack item : confiscatedItems) {
            if (!item.isEmpty()) {
                confiscatedList.add(item.save(new CompoundTag()));
            }
        }
        nbt.put("confiscatedItems", confiscatedList);

        // ⭐ 序列化精通记录 / Serialize mastery records
        CompoundTag masteryTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : moduleMastery.entrySet()) {
            masteryTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("moduleMastery", masteryTag);

        return nbt;
    }

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

        // ⭐ 反序列化精通记录 / Deserialize mastery records
        this.moduleMastery = new HashMap<>();
        if (nbt.contains("moduleMastery", Tag.TAG_COMPOUND)) {
            CompoundTag masteryTag = nbt.getCompound("moduleMastery");
            for (String key : masteryTag.getAllKeys()) {
                this.moduleMastery.put(key, masteryTag.getInt(key));
            }
        }
    }

    // ==================== Provider内部类 / Provider Inner Class ====================

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

        public void invalidate() {
            optional.invalidate();
        }
    }
}
