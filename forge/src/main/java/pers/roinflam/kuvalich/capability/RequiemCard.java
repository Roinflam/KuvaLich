package pers.roinflam.kuvalich.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import pers.roinflam.kuvalich.item.card.OULL;
import pers.roinflam.kuvalich.nemesis.RageTier;
import pers.roinflam.kuvalich.utils.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安魂卡片Capability
 * Requiem Card Capability
 *
 * 存储玩家的安魂卡片数据、解密进度、没收物品、模组精通记录、模组发现记录等
 * Stores player's requiem card data, decryption progress, confiscated items, module mastery records, module discovery records, etc.
 *
 * 新增：模组发现记录（discoveredModules）
 * NEW: Module discovery records (discoveredModules)
 * 记录玩家获取过的所有模组type，图鉴界面使用此数据显示已获得/未获得状态。
 * Records all module types the player has ever obtained. Codex screen uses this for discovered/undiscovered display.
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
     * 模组精通记录（模组type/精通键 → 最高等级）
     * Module mastery records (module type/mastery key → highest level)
     */
    private Map<String, Integer> moduleMastery;

    /**
     * 模组发现记录（已获得过的模组type集合）
     * Module discovery records (set of module types the player has obtained)
     *
     * 用于图鉴界面区分已获得/未获得模组。
     * 死亡不丢失，跟随RequiemCard Capability持久化。
     * Used by codex screen to distinguish discovered/undiscovered modules.
     * Not lost on death, persisted with RequiemCard Capability.
     */
    private Set<String> discoveredModules;

    // ==================== 专属玄骸的数据 ====================
    // 和上面的谜语数据存在同一个 NBT 里，玩家死亡不丢。reset() 只管谜语那一套，不碰这些

    /** 当前写盘的玄骸数据版本；读到比它小的老存档，玄骸字段一律取默认值 */
    public static final int LICH_DATA_VERSION = 1;
    /** 一轮玄骸的结局：被终结 */
    public static final byte FATE_VANQUISHED = 1;
    /** 一轮玄骸的结局：被管理命令解除 */
    public static final byte FATE_ADMIN = 2;
    /** 一轮玄骸的结局：被感化。定案时照常记成被终结，选了感化再由 {@link #markLastConverted} 改 */
    public static final byte FATE_CONVERTED = 3;
    /** 处决记录最多留几条 */
    public static final int MAX_ATTEMPTS = 5;
    /** 前几只的结局最多留几条 */
    public static final int MAX_HISTORY = 5;

    /** 前一只玄骸的结局 */
    public record HistoryEntry(String name, int generation, int rank, byte fate) {
    }

    private int lichDataVersion = LICH_DATA_VERSION;
    /** 这一轮玄骸的名字，首字母大写；空串表示没有玄骸 */
    private String lichName = "";
    /** 诞生时随机，外观、嗓音、武器都从它派生 */
    private long lichSeed;
    /** 这个玩家一共诞生过几只 */
    private int lichGeneration;
    /** 怒气 0~1 */
    private float lichRage;
    /** 冷却到哪一刻，主世界 gameTime（各维度的 gameTime 不一致，统一取主世界的） */
    private long lichCooldownUntil;
    /** 玄骸在场时为真；读档时还是真，说明上次关服或掉线时它在场 */
    private boolean lichEncounterOpen;
    /** 这一轮交手次数（每次现身加一） */
    private int lichEncounters;
    /** 它手里的赤毒武器在武器列表里的下标，-1 = 没有 */
    private int lichWeapon = -1;
    /** 那把武器定下的等级（幼体出现时就定了），0 = 没定：老存档的玄骸，成功时照旧在武器等级范围里随机 */
    private int lichWeaponLevel;
    /** 那把武器定下的元素（{@code KuvaWeaponUtil.LICH_ELEMENTS} 之一），空 = 没定 */
    private String lichWeaponType = "";
    private String lichBirthBiome = "";
    private String lichBirthDim = "";
    /** 这只玄骸带的幻纹款式 id，空 = 不带 */
    private String lichEphemera = "";
    /** 幻纹掷过了；回退旧 jar 后这两个键会丢，再升级时按种子补掷回同一个结果 */
    private boolean lichEphemeraRolled;
    /** 处决记录，最新在前，最多 {@link #MAX_ATTEMPTS} 个，编码见 {@link #packAttempt} */
    private int[] lichAttempts = new int[0];
    /** 前几只的结局，最新在前，最多 {@link #MAX_HISTORY} 条 */
    private List<HistoryEntry> lichHistory = new ArrayList<>();
    /** 被管理命令暂停：怒气不涨、不来找人、不随机嘲讽 */
    private boolean lichPaused;
    /**
     * 还要在线多少 tick 才出下一只赤毒幼体（只在没有玄骸时数）；-1 = 还没掷过。
     * 跟着卡存盘，下线再上来接着数，不会因为每次玩得短就永远等不到
     */
    private int larvlingWait = -1;

    /**
     * 存档标记：武器等级区间已按基础值初始化 / 迁移过
     *
     * <p>1.12.2 原版在构造函数里把区间设成 {@code baseMinimumLevel ~ baseMaximumLevel}，
     * {@code ae337d2}（1.12.2 分支「重构优化，新增镀层卡」，构造函数改成只调 {@code reset()}）时这两行丢了，
     * 之后（含整个 1.20.1 版本）的玩家区间都从 0 起算：
     * 第一把赤毒武器必是 0 级，面板只有基准的约 1/3。2026-09-25 补回初始化；
     * 没有这个标记的存档都是在缺初始化期间写的，读档时补上漏掉的基础值（见 {@link #deserializeNBT}）。</p>
     */
    private static final String LEVEL_RANGE_FIXED = "weaponLevelRangeFixed";

    public RequiemCard() {
        reset();
        this.minimumLevelWeapon = configInt(() -> ModConfig.KUVA_LICH.baseMinimumLevel.get(), 0);
        this.maximumLevelWeapon = Math.max(minimumLevelWeapon,
                configInt(() -> ModConfig.KUVA_LICH.baseMaximumLevel.get(), 0));
        this.confiscatedItems = new ArrayList<>();
        this.moduleMastery = new HashMap<>();
        this.discoveredModules = new HashSet<>();
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

        // 克隆精通记录 / Clone mastery records
        this.moduleMastery = new HashMap<>(requiemCard.moduleMastery);

        // 克隆发现记录 / Clone discovery records
        this.discoveredModules = new HashSet<>(requiemCard.discoveredModules);

        // 克隆玄骸数据
        this.lichDataVersion = requiemCard.lichDataVersion;
        this.lichName = requiemCard.lichName;
        this.lichSeed = requiemCard.lichSeed;
        this.lichGeneration = requiemCard.lichGeneration;
        this.lichRage = requiemCard.lichRage;
        this.lichCooldownUntil = requiemCard.lichCooldownUntil;
        this.lichEncounterOpen = requiemCard.lichEncounterOpen;
        this.lichEncounters = requiemCard.lichEncounters;
        this.lichWeapon = requiemCard.lichWeapon;
        this.lichWeaponLevel = requiemCard.lichWeaponLevel;
        this.lichWeaponType = requiemCard.lichWeaponType;
        this.lichBirthBiome = requiemCard.lichBirthBiome;
        this.lichBirthDim = requiemCard.lichBirthDim;
        this.lichEphemera = requiemCard.lichEphemera;
        this.lichEphemeraRolled = requiemCard.lichEphemeraRolled;
        this.lichAttempts = requiemCard.lichAttempts.clone();
        this.lichHistory = new ArrayList<>(requiemCard.lichHistory);
        this.lichPaused = requiemCard.lichPaused;
        this.larvlingWait = requiemCard.larvlingWait;
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
        return fits(oneCard, oneAnswer) && fits(twoCard, twoAnswer) && fits(threeCard, threeAnswer);
    }

    public boolean isFirstCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }
        return fits(oneCard, oneAnswer);
    }

    public boolean isTwoCorrectAnswer() {
        if (unlockedCardStatus <= 0 || !isReadyCard()) {
            return false;
        }
        return fits(twoCard, twoAnswer);
    }

    /**
     * 这一格放的卡算不算对：和答案同一张，或者是 Oull（放在哪一格都算对）。空格、不是安魂卡的都不算
     */
    public static boolean fits(@Nullable ItemStack placed, int answer) {
        if (placed == null || placed.isEmpty() || !(placed.getItem() instanceof AbstractRequiemCard card)) {
            return false;
        }
        return card.getID() == OULL.ID || card.getID() == answer;
    }

    /** 一格里放的是哪种卡（卡 id）；空格、不是安魂卡的为 -1 */
    public static int placedCardId(@Nullable ItemStack placed) {
        return placed != null && !placed.isEmpty() && placed.getItem() instanceof AbstractRequiemCard card ? card.getID() : -1;
    }

    /**
     * 三个卡位里同一种卡只留最前面那张，后面重复的从卡位上拿下来，交给调用方退还玩家。
     *
     * <p>灭骸之扉同一种卡只能放一张：三格答案各不相同，两张同种的至少有一张不对，Oull 本来就限一张。
     * 卡槽那边已经拦住了，这里清的是拦之前放进去、存在档里的。</p>
     *
     * @return 拿下来的卡（原物，不是副本）；没有重复的为空表
     */
    public List<ItemStack> takeDuplicateCards() {
        ItemStack[] placed = {oneCard, twoCard, threeCard};
        List<ItemStack> extra = new ArrayList<>();
        for (int i = 1; i < 3; i++) {
            int id = placedCardId(placed[i]);
            if (id < 0) {
                continue;
            }
            for (int j = 0; j < i; j++) {
                if (placedCardId(placed[j]) == id) {
                    extra.add(placed[i]);
                    placed[i] = ItemStack.EMPTY;
                    break;
                }
            }
        }
        oneCard = placed[0];
        twoCard = placed[1];
        threeCard = placed[2];
        return extra;
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

    /** {@link #wearPlacedCards()} 返回值里「这一格没放卡」 */
    public static final int USES_NO_CARD = -1;

    /**
     * 安魂成功扣卡：三格里的卡各用掉一次，<b>原地扣、留在原来那一格</b>，用完的那格清空。
     * 不可损耗的卡用一次就没；可损耗的再磨一点就到上限的也没（判据和 {@code KuvaMasterEntity#countCardsWornOut} 一致）。
     *
     * <p>以前成功后三格清空、还有次数的退回背包，玩家每杀一只都要重新摆一遍；现在卡一直待在扉上，
     * 下一只照样用（新一轮的答案还是重新随机，摆着的卡不一定对）。只改玩家数据里这一份，
     * 调用前要先关掉这个玩家开着的扉（{@code RequiemGateMenu#closeBeforeCardsChange}），不然卡槽会把扣之前的写回来。</p>
     *
     * @return 三格（按扉上的位置）扣完以后各剩几次：0 = 这次用完消失，{@link #USES_NO_CARD} = 这一格没放卡
     */
    public int[] wearPlacedCards() {
        int[] after = new int[3];
        ItemStack[] placed = {oneCard, twoCard, threeCard};
        for (int i = 0; i < 3; i++) {
            after[i] = wearInPlace(placed[i]);
            if (after[i] == 0) {
                placed[i] = ItemStack.EMPTY;
            }
        }
        oneCard = placed[0];
        twoCard = placed[1];
        threeCard = placed[2];
        return after;
    }

    /** 一张卡原地用掉一次，返回之后还剩几次（不可损耗的、磨到上限的为 0，空格为 {@link #USES_NO_CARD}） */
    private static int wearInPlace(@Nullable ItemStack card) {
        if (card == null || card.isEmpty()) {
            return USES_NO_CARD;
        }
        if (!card.isDamageableItem()) {
            return 0;
        }
        int newDamage = card.getDamageValue() + 1;
        if (newDamage >= card.getMaxDamage()) {
            return 0;
        }
        card.setDamageValue(newDamage);
        return card.getMaxDamage() - newDamage;
    }

    /**
     * 读配置；配置尚未加载（极早期构造）时用兜底值，不让能力对象构造失败
     */
    private static int configInt(java.util.function.IntSupplier getter, int fallback) {
        try {
            return getter.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double configDouble(java.util.function.DoubleSupplier getter, double fallback) {
        try {
            return getter.getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean configBool(java.util.function.BooleanSupplier getter, boolean fallback) {
        try {
            return getter.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** 整套清零，连扉上的三张卡一起清掉（卡就此没了，调用方要先自己处理那几张卡） */
    public void reset() {
        this.oneCard = ItemStack.EMPTY;
        this.twoCard = ItemStack.EMPTY;
        this.threeCard = ItemStack.EMPTY;
        resetKeepingCards();
    }

    /**
     * 开始新一轮解密：密语、答案、阶段、进度、玄骸等级清零，扉上的三张卡原样留着（安魂成功走这条，
     * 卡已经由 {@link #wearPlacedCards()} 扣过一次）。没收物品、精通记录和发现记录都不动
     */
    public void resetKeepingCards() {
        this.oneRiddle = -1;
        this.twoRiddle = -1;
        this.threeRiddle = -1;
        this.oneAnswer = -1;
        this.twoAnswer = -1;
        this.threeAnswer = -1;
        this.unlockedCardStatus = 0;
        this.decryptionProgress = 0;
        this.kuvaLevel = 0;
        // 没收物品、精通记录、发现记录留着；账、收件箱、盟友名册在 PlayerLedger 里，也不归这里管
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

    /**
     * 密语的前缀长度：从第 1 位起逐位比较放着的卡和答案，遇到第一个不对的就停；空着的格子算这一位不对，
     * Oull 在哪一位都算对（见 {@link #fits}）。3 表示全对；小于 3 时在第 {@code 前缀长度 + 1} 位判错。
     * 还没揭示任何阶段时恒为 0。注意 {@link #isTwoCorrectAnswer()} 只看第 2 位本身，不是前缀
     */
    public int correctPrefixLength() {
        if (unlockedCardStatus <= 0) {
            return 0;
        }
        ItemStack[] placed = {oneCard, twoCard, threeCard};
        int[] answers = {oneAnswer, twoAnswer, threeAnswer};
        int length = 0;
        for (int i = 0; i < 3; i++) {
            if (!fits(placed[i], answers[i])) {
                break;
            }
            length++;
        }
        return length;
    }

    // ==================== 专属玄骸 ====================

    /** 有没有玄骸 */
    public boolean hasLich() {
        return !lichName.isEmpty();
    }

    public int getLichDataVersion() {
        return lichDataVersion;
    }

    /** 玄骸的名字，首字母大写；没有为空串 */
    public String getLichName() {
        return lichName;
    }

    /**
     * 给已有的玄骸改名（管理命令用）。没有玄骸时不做任何事，免得凭空「诞生」一只；
     * 名字合不合规由调用方先查
     */
    public void setLichName(String name) {
        if (hasLich() && name != null && !name.isBlank()) {
            this.lichName = name;
        }
    }

    public long getLichSeed() {
        return lichSeed;
    }

    public int getLichGeneration() {
        return lichGeneration;
    }

    /** 所有给玩家看的「玄骸等级」一律用它（档案、Boss 条、横幅、失败提示），比内部的 kuvaLevel 大 1 */
    public int getLichRank() {
        return kuvaLevel + 1;
    }

    public float getLichRage() {
        return lichRage;
    }

    /** 怒气夹到 0~1 */
    public void setLichRage(float rage) {
        this.lichRage = Math.max(0.0F, Math.min(1.0F, rage));
    }

    /** 怒气档位的序号（{@link RageTier#ordinal()}） */
    public int getRageTier() {
        return RageTier.of(lichRage).ordinal();
    }

    public long getLichCooldownUntil() {
        return lichCooldownUntil;
    }

    public void setLichCooldownUntil(long gameTime) {
        this.lichCooldownUntil = gameTime;
    }

    public boolean isLichEncounterOpen() {
        return lichEncounterOpen;
    }

    public void setLichEncounterOpen(boolean open) {
        this.lichEncounterOpen = open;
    }

    public int getLichEncounters() {
        return lichEncounters;
    }

    public void incLichEncounters() {
        this.lichEncounters++;
    }

    /** 武器下标，-1 = 没有 */
    public int getLichWeapon() {
        return lichWeapon;
    }

    /** 那把武器定下的等级，0 = 没定 */
    public int getLichWeaponLevel() {
        return lichWeaponLevel;
    }

    /** 那把武器定下的元素，空 = 没定 */
    public String getLichWeaponType() {
        return lichWeaponType;
    }

    /** 幻纹款式 id，空 = 不带 */
    public String getLichEphemera() {
        return lichEphemera;
    }

    public boolean isLichEphemeraRolled() {
        return lichEphemeraRolled;
    }

    /** 管理命令直接指定；空串 = 不带 */
    public void setLichEphemera(String id) {
        this.lichEphemera = id == null ? "" : id;
        this.lichEphemeraRolled = true;
    }

    /** 按种子和给定概率掷一次幻纹，结果写死在卡上；没有玄骸时什么都不做 */
    public void rollEphemera(double chance) {
        if (!hasLich()) {
            return;
        }
        this.lichEphemera = pers.roinflam.kuvalich.ephemera.EphemeraType.roll(lichSeed, lichWeaponType, chance);
        this.lichEphemeraRolled = true;
    }

    /**
     * 已经诞生、还没掷过幻纹的玄骸补掷一次（登录、绑定主人、定案时都调，只有第一次生效）。
     * 补掷开关关着就只置标记、一律不带
     *
     * @return 这次是否真的补了
     */
    public boolean ensureEphemeraRolled() {
        if (!hasLich() || lichEphemeraRolled) {
            return false;
        }
        if (configBool(() -> ModConfig.KUVA_LICH.lichEphemeraBackfill.get(), true)) {
            rollEphemera(configDouble(() -> ModConfig.KUVA_LICH.lichEphemeraChance.get(), 0.0D));
        } else {
            this.lichEphemera = "";
            this.lichEphemeraRolled = true;
        }
        return true;
    }

    public String getLichBirthBiome() {
        return lichBirthBiome;
    }

    public String getLichBirthDim() {
        return lichBirthDim;
    }

    public boolean isLichPaused() {
        return lichPaused;
    }

    public void setLichPaused(boolean paused) {
        this.lichPaused = paused;
    }

    /** 记一行处决尝试，插到最前面，超过 {@link #MAX_ATTEMPTS} 个丢掉最旧的 */
    public void recordAttempt(int packed) {
        int size = Math.min(MAX_ATTEMPTS, lichAttempts.length + 1);
        int[] next = new int[size];
        next[0] = packed;
        System.arraycopy(lichAttempts, 0, next, 1, size - 1);
        this.lichAttempts = next;
    }

    /** 还要在线多少 tick 才出下一只幼体，-1 = 还没掷过 */
    public int getLarvlingWait() {
        return larvlingWait;
    }

    public void setLarvlingWait(int ticks) {
        this.larvlingWait = ticks;
    }

    /** 处决记录的副本，最新在前 */
    public int[] getAttempts() {
        return lichAttempts.clone();
    }

    /**
     * 诞生一只新玄骸：写名字、种子、武器、出生地，代数加一，怒气、冷却、交手次数、处决记录清零
     */
    public void beginLich(String name, long seed, int weaponIndex, String biome, String dim) {
        beginLich(name, seed, weaponIndex, 0, "", biome, dim);
    }

    /**
     * 同上，连那把武器定下的等级和元素一起写（{@code weaponLevel} 0、{@code weaponType} 空 = 不定，成功时照旧随机）
     */
    public void beginLich(String name, long seed, int weaponIndex, int weaponLevel, String weaponType, String biome,
                          String dim) {
        this.lichName = name == null ? "" : name;
        this.lichSeed = seed;
        this.lichWeapon = weaponIndex;
        this.lichWeaponLevel = Math.max(0, weaponLevel);
        this.lichWeaponType = weaponType == null ? "" : weaponType;
        this.lichBirthBiome = biome == null ? "" : biome;
        this.lichBirthDim = dim == null ? "" : dim;
        this.lichGeneration++;
        this.lichRage = 0.0F;
        this.lichCooldownUntil = 0L;
        this.lichEncounterOpen = false;
        this.lichEncounters = 0;
        this.lichAttempts = new int[0];
        rollEphemera(configDouble(() -> ModConfig.KUVA_LICH.lichEphemeraChance.get(), 0.0D));
    }

    /**
     * 结束这一轮玄骸：把它的名字、代数、等级和结局记进前几只的历史（最多留 {@link #MAX_HISTORY} 条），
     * 再清空名字、种子、武器、出生地、怒气、冷却、处决记录、交手次数和「遭遇进行中」记号
     * （没有玄骸就不可能还有一场没结束的遭遇，留着它下次登录会被当成掉线补算一次）。
     * 成功终结时的顺序是先调它、再 {@link #resetKeepingCards()}：等级要在它清掉 kuvaLevel 之前取
     *
     * @param fate {@link #FATE_VANQUISHED} 或 {@link #FATE_ADMIN}
     */
    public void endLichRound(byte fate) {
        if (hasLich()) {
            lichHistory.add(0, new HistoryEntry(lichName, lichGeneration, getLichRank(), fate));
            while (lichHistory.size() > MAX_HISTORY) {
                lichHistory.remove(lichHistory.size() - 1);
            }
        }
        this.lichName = "";
        this.lichSeed = 0L;
        this.lichWeapon = -1;
        this.lichWeaponLevel = 0;
        this.lichWeaponType = "";
        this.lichBirthBiome = "";
        this.lichBirthDim = "";
        this.lichEphemera = "";
        this.lichEphemeraRolled = false;
        this.lichRage = 0.0F;
        this.lichCooldownUntil = 0L;
        this.lichAttempts = new int[0];
        this.lichEncounters = 0;
        this.lichEncounterOpen = false;
        // 账、收件箱、盟友名册在 PlayerLedger 里，这里不碰
    }

    /** 历史首条是第 {@code generation} 代时，结局改成被感化；对不上返回 false */
    public boolean markLastConverted(int generation) {
        if (lichHistory.isEmpty() || lichHistory.get(0).generation() != generation) {
            return false;
        }
        HistoryEntry e = lichHistory.get(0);
        lichHistory.set(0, new HistoryEntry(e.name(), e.generation(), e.rank(), FATE_CONVERTED));
        return true;
    }

    /** 上一只玄骸的结局；还没有结束过任何一只返回 {@code null} */
    @Nullable
    public HistoryEntry getLastHistory() {
        return lichHistory.isEmpty() ? null : lichHistory.get(0);
    }

    /** 前几只的结局副本，最新在前 */
    public List<HistoryEntry> getHistory() {
        return new ArrayList<>(lichHistory);
    }

    // ==================== 处决记录的编码 ====================

    /** 记录里表示「这一位没放卡」的卡 id */
    public static final int ATTEMPT_NO_CARD = 15;
    /** 结果：没判到（前面已经判错了） */
    public static final int ATTEMPT_UNJUDGED = 0;
    /** 结果：这一位对 */
    public static final int ATTEMPT_RIGHT = 1;
    /** 结果：这一位错 */
    public static final int ATTEMPT_WRONG = 2;

    /**
     * 把一次判定压成一个 int。每一位占 6 比特：低 4 位是卡 id（0~8，15 = 没放），再 2 位是结果
     * （0 没判到 / 1 对 / 2 错）；第 18 位为 1 表示是怜悯之击，0 表示击杀时直接判定。
     * 判定按位置顺序、遇错即停，错的那一位之后记「没判到」。
     *
     * @param cardIds 三位用的卡 id，不足三位或为负的按「没放」处理
     * @param results 三位的结果，不足三位的按「没判到」处理
     * @param mercy   是否来自怜悯之击
     */
    public static int packAttempt(int[] cardIds, byte[] results, boolean mercy) {
        int packed = 0;
        for (int i = 0; i < 3; i++) {
            int id = i < cardIds.length && cardIds[i] >= 0 && cardIds[i] <= OULL.ID ? cardIds[i] : ATTEMPT_NO_CARD;
            int result = i < results.length ? results[i] & 3 : ATTEMPT_UNJUDGED;
            packed |= (id | (result << 4)) << (6 * i);
        }
        if (mercy) {
            packed |= 1 << 18;
        }
        return packed;
    }

    /** 记录里第 {@code pos} 位（0~2）用的卡 id，15 = 没放 */
    public static int attemptCardId(int packed, int pos) {
        return (packed >> (6 * pos)) & 0xF;
    }

    /** 记录里第 {@code pos} 位（0~2）的结果：0 没判到 / 1 对 / 2 错 */
    public static int attemptResult(int packed, int pos) {
        return (packed >> (6 * pos + 4)) & 3;
    }

    /** 这条记录是不是来自怜悯之击 */
    public static boolean attemptIsMercy(int packed) {
        return (packed & (1 << 18)) != 0;
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

    // ==================== 模组精通相关方法 / Module Mastery Methods ====================

    public int getMasteryLevel(String masteryKey) {
        if (masteryKey == null || masteryKey.isEmpty()) {
            return 0;
        }
        return moduleMastery.getOrDefault(masteryKey, 0);
    }

    public void updateMasteryLevel(String masteryKey, int level) {
        if (masteryKey == null || masteryKey.isEmpty()) {
            return;
        }
        int current = moduleMastery.getOrDefault(masteryKey, 0);
        if (level > current) {
            moduleMastery.put(masteryKey, level);
        }
    }

    public Map<String, Integer> getAllMastery() {
        return new HashMap<>(moduleMastery);
    }

    // ==================== 模组发现相关方法 / Module Discovery Methods ====================

    /**
     * 记录模组发现
     * Record module discovery
     *
     * @param moduleType 模组type标识符
     * @return true=新发现（之前没有记录），false=已有记录
     */
    public boolean discoverModule(String moduleType) {
        if (moduleType == null || moduleType.isEmpty()) {
            return false;
        }
        return discoveredModules.add(moduleType);
    }

    /**
     * 查询模组是否已发现
     * Check if module is discovered
     *
     * @param moduleType 模组type标识符
     * @return 是否已发现
     */
    public boolean isModuleDiscovered(String moduleType) {
        if (moduleType == null || moduleType.isEmpty()) {
            return false;
        }
        return discoveredModules.contains(moduleType);
    }

    /**
     * 获取所有已发现模组type的集合（返回副本）
     * Get all discovered module types (returns copy)
     *
     * @return 已发现模组type的Set副本
     */
    public Set<String> getDiscoveredModules() {
        return new HashSet<>(discoveredModules);
    }

    /**
     * 获取已发现模组数量
     * Get discovered module count
     *
     * @return 已发现数量
     */
    public int getDiscoveredModuleCount() {
        return discoveredModules.size();
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
        nbt.putBoolean(LEVEL_RANGE_FIXED, true);

        // 序列化没收物品列表
        ListTag confiscatedList = new ListTag();
        for (ItemStack item : confiscatedItems) {
            if (!item.isEmpty()) {
                confiscatedList.add(item.save(new CompoundTag()));
            }
        }
        nbt.put("confiscatedItems", confiscatedList);

        // 序列化精通记录 / Serialize mastery records
        CompoundTag masteryTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : moduleMastery.entrySet()) {
            masteryTag.putInt(entry.getKey(), entry.getValue());
        }
        nbt.put("moduleMastery", masteryTag);

        // 序列化发现记录 / Serialize discovery records
        ListTag discoveryList = new ListTag();
        for (String type : discoveredModules) {
            discoveryList.add(StringTag.valueOf(type));
        }
        nbt.put("discoveredModules", discoveryList);

        // 玄骸数据
        nbt.putInt("lichDataVersion", LICH_DATA_VERSION);
        nbt.putString("lichName", lichName);
        nbt.putLong("lichSeed", lichSeed);
        nbt.putInt("lichGeneration", lichGeneration);
        nbt.putFloat("lichRage", lichRage);
        nbt.putLong("lichCooldownUntil", lichCooldownUntil);
        nbt.putBoolean("lichEncounterOpen", lichEncounterOpen);
        nbt.putInt("lichEncounters", lichEncounters);
        nbt.putInt("lichWeapon", lichWeapon);
        nbt.putInt("lichWeaponLevel", lichWeaponLevel);
        nbt.putString("lichWeaponType", lichWeaponType);
        nbt.putString("lichBirthBiome", lichBirthBiome);
        nbt.putString("lichBirthDim", lichBirthDim);
        nbt.putString("lichEphemera", lichEphemera);
        nbt.putBoolean("lichEphemeraRolled", lichEphemeraRolled);
        nbt.putIntArray("lichAttempts", lichAttempts);
        ListTag historyList = new ListTag();
        for (HistoryEntry entry : lichHistory) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("name", entry.name());
            entryTag.putInt("gen", entry.generation());
            entryTag.putInt("rank", entry.rank());
            entryTag.putByte("fate", entry.fate());
            historyList.add(entryTag);
        }
        nbt.put("lichHistory", historyList);
        nbt.putBoolean("lichPaused", lichPaused);
        nbt.putInt("larvlingWait", larvlingWait);

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
        if (!nbt.getBoolean(LEVEL_RANGE_FIXED)) {
            // 缺初始化期间写的存档：区间 = 0 + 历次解密成功的累加。补上当初漏掉的基础值，
            // 再按各自的绝对上限封顶 —— 结果正好等于「一开始就正确初始化」会得到的区间。
            // 已经封顶的玩家不变；任何情况下只升不降
            int baseMin = configInt(() -> ModConfig.KUVA_LICH.baseMinimumLevel.get(), 0);
            int baseMax = configInt(() -> ModConfig.KUVA_LICH.baseMaximumLevel.get(), 0);
            int capMin = configInt(() -> ModConfig.KUVA_LICH.minimumLevel.get(), Integer.MAX_VALUE);
            int capMax = configInt(() -> ModConfig.KUVA_LICH.maximumLevel.get(), Integer.MAX_VALUE);
            this.minimumLevelWeapon = Math.max(minimumLevelWeapon,
                    (int) Math.min(capMin, (long) minimumLevelWeapon + baseMin));
            this.maximumLevelWeapon = Math.max(maximumLevelWeapon,
                    (int) Math.min(capMax, (long) maximumLevelWeapon + baseMax));
        }
        // 抽武器等级时是 random(min, max)，区间倒挂会出错
        if (maximumLevelWeapon < minimumLevelWeapon) {
            maximumLevelWeapon = minimumLevelWeapon;
        }

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

        // 反序列化精通记录 / Deserialize mastery records
        this.moduleMastery = new HashMap<>();
        if (nbt.contains("moduleMastery", Tag.TAG_COMPOUND)) {
            CompoundTag masteryTag = nbt.getCompound("moduleMastery");
            for (String key : masteryTag.getAllKeys()) {
                this.moduleMastery.put(key, masteryTag.getInt(key));
            }
        }

        // 反序列化发现记录 / Deserialize discovery records
        this.discoveredModules = new HashSet<>();
        if (nbt.contains("discoveredModules", Tag.TAG_LIST)) {
            ListTag discoveryList = nbt.getList("discoveredModules", Tag.TAG_STRING);
            for (int i = 0; i < discoveryList.size(); i++) {
                String type = discoveryList.getString(i);
                if (type != null && !type.isEmpty()) {
                    discoveredModules.add(type);
                }
            }
        }

        readLichData(nbt);
    }

    /**
     * 读玄骸数据。没有版本号或版本比当前小的是玄骸出现之前的老存档：所有玄骸字段取默认值、版本记为当前，
     * 这里拿不到玩家和世界，不在这里生成玄骸，登录时再按解密进度补生
     */
    private void readLichData(CompoundTag nbt) {
        resetLichData();
        this.larvlingWait = nbt.contains("larvlingWait") ? nbt.getInt("larvlingWait") : -1;
        if (nbt.getInt("lichDataVersion") < LICH_DATA_VERSION) {
            return;
        }
        this.lichName = nbt.getString("lichName");
        this.lichSeed = nbt.getLong("lichSeed");
        this.lichGeneration = nbt.getInt("lichGeneration");
        setLichRage(nbt.getFloat("lichRage"));
        this.lichCooldownUntil = nbt.getLong("lichCooldownUntil");
        this.lichEncounterOpen = nbt.getBoolean("lichEncounterOpen");
        this.lichEncounters = nbt.getInt("lichEncounters");
        this.lichWeapon = nbt.contains("lichWeapon") ? nbt.getInt("lichWeapon") : -1;
        this.lichWeaponLevel = nbt.getInt("lichWeaponLevel");
        this.lichWeaponType = nbt.getString("lichWeaponType");
        this.lichBirthBiome = nbt.getString("lichBirthBiome");
        this.lichBirthDim = nbt.getString("lichBirthDim");
        // 这两个键后加的，不升 lichDataVersion：缺键就是没掷过，登录时补掷
        this.lichEphemera = nbt.getString("lichEphemera");
        this.lichEphemeraRolled = nbt.getBoolean("lichEphemeraRolled");
        int[] attempts = nbt.getIntArray("lichAttempts");
        this.lichAttempts = attempts.length > MAX_ATTEMPTS ? Arrays.copyOf(attempts, MAX_ATTEMPTS) : attempts;
        ListTag historyList = nbt.getList("lichHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size() && lichHistory.size() < MAX_HISTORY; i++) {
            CompoundTag entryTag = historyList.getCompound(i);
            lichHistory.add(new HistoryEntry(entryTag.getString("name"), entryTag.getInt("gen"),
                    entryTag.getInt("rank"), entryTag.getByte("fate")));
        }
        this.lichPaused = nbt.getBoolean("lichPaused");
    }

    private void resetLichData() {
        this.lichDataVersion = LICH_DATA_VERSION;
        this.lichName = "";
        this.lichSeed = 0L;
        this.lichGeneration = 0;
        this.lichRage = 0.0F;
        this.lichCooldownUntil = 0L;
        this.lichEncounterOpen = false;
        this.lichEncounters = 0;
        this.lichWeapon = -1;
        this.lichWeaponLevel = 0;
        this.lichWeaponType = "";
        this.lichBirthBiome = "";
        this.lichBirthDim = "";
        this.lichEphemera = "";
        this.lichEphemeraRolled = false;
        this.lichAttempts = new int[0];
        this.lichHistory = new ArrayList<>();
        this.lichPaused = false;
        // 账、收件箱、盟友名册在 PlayerLedger 里，这里不碰
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
