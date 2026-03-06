package pers.roinflam.kuvalich.item.module.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 裂罅武器模组（1.20.1版本）
 * 支持三种模式：近战(0)、远程(1)、通用(2)
 * 通用模式近战和远程词条均可出现，形成大混池
 *
 * Riven weapon module (1.20.1 version)
 * Supports three modes: Melee(0), Remote(1), Universal(2)
 * Universal mode allows both melee and remote attributes in a single mixed pool
 */
public class ItemRivenModule extends ItemModuleBase {
    private static final String[] PREFIXES = {"Croni", "Sati", "Vexi", "Locti", "Magna", "Crita", "Geli", "Rupti", "Arma", "Venxi", "Furi", "Praesi", "Nexi", "Draco", "Spira", "Phasa", "Lunari", "Solara", "Terron", "Aquix", "Ventra", "Ignis", "Frosti", "Voltic", "Plasma", "Ethera", "Radi", "Mortal", "Divin", "Spectra", "Celest", "Infern", "Obliv", "Eclip", "Cosmi", "Stella", "Astron", "Nebula", "Galax", "Orbit", "Nova", "Lunar", "Solar", "Comet", "Astro", "Stellar", "Void", "Quantum", "Gluon", "Gravi", "Photon", "Pulsar", "Quark", "Radian", "Sigma", "Tau", "Upsilon", "Vecti", "Warp", "Xenon", "Yotta", "Zetta", "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega", "Axion", "Baryon", "Charm", "Dynami", "Electro", "Fluxi", "Gyro", "Halo", "Ioni", "Joule", "Kineti", "Lepto", "Mytho", "Neuro", "Omni", "Penta", "Quanta", "Retro", "Syntho", "Tri", "Umbra", "Vecta", "Wyrm", "Xero", "Yield", "Zephyr"};

    private static final String[] SUFFIXES = {"cron", "ata", "icor", "tis", "tron", "cak", "nus", "vex", "mira", "ton", "sera", "phix", "gara", "luxe", "moto", "zora", "fyre", "glacia", "volt", "terra", "aqua", "nebula", "stellar", "cosmo", "sol", "lunar", "astral", "void", "nether", "ether", "flux", "halo", "vortex", "quantum", "sigma", "omega", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega", "alpha", "beta", "axion", "baryon", "charm", "dynami", "electro", "fluxi", "gyro", "halo", "ioni", "joule", "kineti", "lepto", "mytho", "neuro", "omni", "penta", "quanta", "retro", "syntho", "tri", "umbra", "vecta", "wyrm", "xero", "yield", "zephyr", "ara", "bolo", "ceta", "dome", "ergo", "foti", "glow", "hype", "ille", "juno", "kilo", "lima", "mote", "nano", "oxi", "pico", "quark", "rune", "solo", "tome", "uni", "volo", "watt", "xene", "yotta", "zetta"};

    /**
     * Riven模组的固定type值
     * 用于配置文件禁用检查
     */
    public static final String RIVEN_TYPE = "riven_weapon_module";

    // ========== 裂罅模式常量 / Riven Mode Constants ==========

    /** 近战模式：只出近战词条 */
    public static final int MODE_MELEE = 0;

    /** 远程模式：只出远程词条 */
    public static final int MODE_REMOTE = 1;

    /** 通用模式：近战+远程大混池，所有词条均可出现 */
    public static final int MODE_UNIVERSAL = 2;

    public ItemRivenModule(Properties properties) {
        super(properties);
    }

    /**
     * 生成随机裂罅名称（前缀-后缀）
     * Generate random Riven name (prefix-suffix)
     *
     * @return 随机名称字符串 / random name string
     */
    public static String generateRivenName() {
        String prefix = PREFIXES[RandomUtil.getInt(0, PREFIXES.length - 1)];
        String suffix = SUFFIXES[RandomUtil.getInt(0, SUFFIXES.length - 1)];
        return prefix + "-" + suffix;
    }

    /**
     * 获取未揭示的随机裂罅物品
     * Get unrevealed random Riven item
     *
     * @return 未揭示状态的裂罅物品 / unrevealed Riven item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("kuvaweapon.item_type_riven_random.name").getString()));
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    /**
     * 检查武器Riven模组是否被禁用
     * Check if weapon Riven module is disabled
     *
     * @return true表示被禁用 / true if disabled
     */
    public static boolean isRivenDisabled() {
        return ModuleConfig.isTypeDisabled(RIVEN_TYPE);
    }

    /**
     * 初始化一个新的裂罅模组
     * 随机决定模式（近战/远程/通用各1/3概率）、倾向性、词条
     *
     * Initialize a new Riven module
     * Randomly determines mode (melee/remote/universal each 1/3 chance), trend, and attributes
     *
     * @return 初始化完毕的裂罅物品 / initialized Riven item
     */
    public static ItemStack initModule() {
        // 检查是否被禁用
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("item.item_type_riven_random.name").getString() + " " + generateRivenName()));

        // 随机倾向性：5点2.5%、4点5%、其余均分1-3点
        if (RandomUtil.percentageChance(2.5)) {
            setTrend(itemStack, 5);
        } else if (RandomUtil.percentageChance(5)) {
            setTrend(itemStack, 4);
        } else {
            setTrend(itemStack, RandomUtil.getInt(1, 3));
        }
        setCycle(itemStack, 0);

        // 随机模式：通用(2)占50%，近战(0)占25%，远程(1)占25%
        int modeRoll = RandomUtil.getInt(0, 3);
        int mode;
        if (modeRoll <= 1) {
            // 0,1 → 通用（50%）
            mode = MODE_UNIVERSAL;
        } else if (modeRoll == 2) {
            // 2 → 近战（25%）
            mode = MODE_MELEE;
        } else {
            // 3 → 远程（25%）
            mode = MODE_REMOTE;
        }
        setRivenMode(itemStack, mode);

        return cycleModule(getTrend(itemStack), getCycle(itemStack) - 1, getRivenMode(itemStack));
    }

    // ========== 裂罅模式NBT读写（替代旧版Melee布尔值）==========
    // ========== Riven Mode NBT read/write (replaces old Melee boolean) ==========

    /**
     * 设置裂罅模式到NBT
     * Set Riven mode to NBT
     *
     * @param itemStack 目标物品 / target item
     * @param mode 裂罅模式 / Riven mode (MODE_MELEE=0, MODE_REMOTE=1, MODE_UNIVERSAL=2)
     */
    public static void setRivenMode(ItemStack itemStack, int mode) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("RivenMode", mode);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    /**
     * 获取裂罅模式
     * 向下兼容：若NBT中无RivenMode但有旧版Melee字段，自动转换
     *
     * Get Riven mode
     * Backward compatible: if no RivenMode but old Melee field exists, auto-convert
     *
     * @param itemStack 目标物品 / target item
     * @return 裂罅模式 / Riven mode (0=melee, 1=remote, 2=universal)
     */
    public static int getRivenMode(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");

        // 新版字段优先
        if (kuvalichModule.contains("RivenMode")) {
            return kuvalichModule.getInt("RivenMode");
        }

        // 向下兼容旧版Melee布尔值
        if (kuvalichModule.contains("Melee")) {
            return kuvalichModule.getBoolean("Melee") ? MODE_MELEE : MODE_REMOTE;
        }

        // 默认近战
        return MODE_MELEE;
    }

    // ========== 保留旧版方法以兼容外部调用 ==========
    // ========== Keep old methods for external compatibility ==========

    /**
     * @deprecated 使用 {@link #setRivenMode(ItemStack, int)} 替代
     */
    @Deprecated
    public static void setMelee(ItemStack itemStack, boolean isMelee) {
        setRivenMode(itemStack, isMelee ? MODE_MELEE : MODE_REMOTE);
    }

    /**
     * @deprecated 使用 {@link #getRivenMode(ItemStack)} 替代
     */
    @Deprecated
    public static boolean isMelee(ItemStack itemStack) {
        return getRivenMode(itemStack) == MODE_MELEE;
    }

    /**
     * 判断某属性是否为近战专属（远程Riven不可出现）
     * Check if an attribute is melee-exclusive (cannot appear on remote Riven)
     *
     * @param attributeType 属性类型名 / attribute type name
     * @return true表示近战专属 / true if melee-exclusive
     */
    private static boolean isMeleeExclusiveAttr(String attributeType) {
        return attributeType.equals("meleeDamage")
                || attributeType.equals("attackSpeed")
                || attributeType.equals("attackRange")
                || attributeType.equals("meleeCriticalStrikeProbability")
                || attributeType.equals("meleeCriticalStrikeMultiplier")
                || attributeType.equals("dashMeleeCriticalStrikeProbability")
                || attributeType.equals("dashAttackRange")
                || attributeType.equals("dashTriggerChance")
                || attributeType.equals("killStackMeleeCriticalMultiplier")
                || attributeType.equals("killStackAttackRange")
                || attributeType.equals("killStackAttackSpeed");
    }

    /**
     * 判断某属性是否为远程专属（近战Riven不可出现）
     * Check if an attribute is remote-exclusive (cannot appear on melee Riven)
     *
     * @param attributeType 属性类型名 / attribute type name
     * @return true表示远程专属 / true if remote-exclusive
     */
    private static boolean isRemoteExclusiveAttr(String attributeType) {
        return attributeType.equals("remoteDamage")
                || attributeType.equals("arrowDamage")
                || attributeType.equals("projectileDamage")
                || attributeType.equals("multishot")
                || attributeType.equals("remoteCriticalStrikeProbability")
                || attributeType.equals("remoteCriticalStrikeMultiplier")
                || attributeType.equals("firing_rate")
                || attributeType.equals("bursting_radius")
                || attributeType.equals("killStackMultishot")
                || attributeType.equals("killStackBurstingRadius")
                || attributeType.equals("killStackFiringRate");
    }

    /**
     * 根据裂罅模式判断某属性是否应被过滤
     * 近战模式过滤远程专属，远程模式过滤近战专属，通用模式不过滤任何属性
     *
     * Check if an attribute should be filtered based on Riven mode
     * Melee filters remote-exclusive, Remote filters melee-exclusive, Universal filters nothing
     *
     * @param attributeType 属性类型名 / attribute type name
     * @param rivenMode 裂罅模式 / Riven mode
     * @return true表示应过滤（不出现）/ true if should be filtered out
     */
    private static boolean shouldFilterAttribute(String attributeType, int rivenMode) {
        if (rivenMode == MODE_UNIVERSAL) {
            // 通用模式：大混池，不过滤任何专属词条
            return false;
        }
        if (rivenMode == MODE_MELEE) {
            // 近战模式：过滤远程专属
            return isRemoteExclusiveAttr(attributeType);
        }
        if (rivenMode == MODE_REMOTE) {
            // 远程模式：过滤近战专属
            return isMeleeExclusiveAttr(attributeType);
        }
        return false;
    }

    /**
     * 根据裂罅模式获取名称前缀
     * Get name prefix based on Riven mode
     *
     * @param rivenMode 裂罅模式 / Riven mode
     * @return 名称前缀 / name prefix
     */
    private static String getRivenNamePrefix(int rivenMode) {
        if (rivenMode == MODE_MELEE) {
            return "MeleeRiven";
        } else if (rivenMode == MODE_REMOTE) {
            return "RemoteRiven";
        } else {
            return "OmniRiven";
        }
    }

    /**
     * 洗卡/生成裂罅词条（新版：接受int裂罅模式）
     * 根据倾向性、洗卡次数和裂罅模式重新随机所有词条
     *
     * Cycle/generate Riven attributes (new: accepts int Riven mode)
     * Re-randomize all attributes based on trend, cycle count, and Riven mode
     *
     * @param trend 倾向性（1-5） / trend (1-5)
     * @param cycleNumber 洗卡次数 / cycle count
     * @param rivenMode 裂罅模式（0=近战，1=远程，2=通用） / Riven mode (0=melee, 1=remote, 2=universal)
     * @return 生成完毕的裂罅物品 / generated Riven item
     */
    public static ItemStack cycleModule(int trend, int cycleNumber, int rivenMode) {
        // 检查是否被禁用
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + getRivenNamePrefix(rivenMode) + " " + generateRivenName()));
        setTrend(itemStack, trend);
        setCycle(itemStack, cycleNumber + 1);
        setRivenMode(itemStack, rivenMode);

        // 获取全局模组属性倍率
        // Get global module attribute multiplier
        double globalMultiplier = ModConfig.KUVA_LICH.moduleAttributeMultiplier.get();

        boolean more = false;
        boolean negative = false;

        double trendMagnification = 1;
        double negativeTrendMagnification = 0.75;
        int randomNumber = RandomUtil.getInt(0, 3);
        if (randomNumber == 1) {
            negative = true;
            trendMagnification *= 1.2;
        } else if (randomNumber == 2) {
            more = true;
            trendMagnification *= 0.75;
        } else if (randomNumber == 3) {
            more = true;
            negative = true;
            trendMagnification *= 0.9;
            negativeTrendMagnification *= 1.5;
        }
        if (trend == 1) {
            trendMagnification *= 0.6;
            negativeTrendMagnification *= 0.7;
        } else if (trend == 2) {
            trendMagnification *= 0.8;
            negativeTrendMagnification *= 0.9;
        } else if (trend == 3) {
            trendMagnification *= 1;
            negativeTrendMagnification *= 1.1;
        } else if (trend == 4) {
            trendMagnification *= 1.2;
            negativeTrendMagnification *= 1.3;
        } else if (trend == 5) {
            trendMagnification *= 1.4;
            negativeTrendMagnification *= 1.5;
        }

        List<String> itemAttributeType = new ArrayList<>(ItemModuleBase.ITEM_ATTRIBUTE_TYPES);
        Collections.shuffle(itemAttributeType);

        List<String> hasAttributeType = new ArrayList<>();
        int number = more ? 3 : 2;
        int add = 0;
        for (String attributeType : itemAttributeType) {
            if (add >= number) {
                break;
            }

            // Riven紫卡不能洗出复合元素词条
            if (attributeType.equals("gas") || attributeType.equals("radiation") ||
                    attributeType.equals("magnetic") || attributeType.equals("corrosion") ||
                    attributeType.equals("explosion") || attributeType.equals("virus")) {
                continue;
            }

            // 根据裂罅模式过滤专属词条（正面）
            // Filter exclusive attributes based on Riven mode (positive)
            if (shouldFilterAttribute(attributeType, rivenMode)) {
                continue;
            }

            double randomDouble = 0.9 + (Math.random() * (1.1 - 0.9));
            randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

            hasAttributeType.add(attributeType);
            // 应用全局属性倍率（正面属性）
            // Apply global attribute multiplier (positive attributes)
            ItemModuleBase.addAttributes(itemStack, attributeType,
                    getBaseAttributeValue(attributeType) * trendMagnification * randomDouble * globalMultiplier);
            add++;
        }

        if (negative) {
            for (String attributeType : itemAttributeType) {
                if (hasAttributeType.contains(attributeType)) {
                    continue;
                }

                // Riven紫卡不能洗出复合元素词条（包括负面）
                if (attributeType.equals("gas") || attributeType.equals("radiation") ||
                        attributeType.equals("magnetic") || attributeType.equals("corrosion") ||
                        attributeType.equals("explosion") || attributeType.equals("virus")) {
                    continue;
                }

                // 根据裂罅模式过滤专属词条（负面）
                // Filter exclusive attributes based on Riven mode (negative)
                if (shouldFilterAttribute(attributeType, rivenMode)) {
                    continue;
                }

                if (attributeType.equals("fire") || attributeType.equals("ice") ||
                        attributeType.equals("poison") || attributeType.equals("electricity")) {
                    continue;
                }

                double randomDouble = 0.8 + (Math.random() * (1.2 - 0.8));
                randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

                // 应用全局属性倍率（负面属性）
                // Apply global attribute multiplier (negative attributes)
                ItemModuleBase.addAttributes(itemStack, attributeType,
                        -(getBaseAttributeValue(attributeType) * negativeTrendMagnification * randomDouble * globalMultiplier));
                break;
            }
        }

        ItemModuleBase.setType(itemStack, RIVEN_TYPE);

        return itemStack;
    }

    /**
     * 兼容旧版签名的洗卡方法（布尔近战参数自动转换为模式）
     * Backward-compatible cycle method (boolean melee auto-converts to mode)
     *
     * @deprecated 使用 {@link #cycleModule(int, int, int)} 替代
     */
    @Deprecated
    public static ItemStack cycleModule(int trend, int cycleNumber, boolean isMelee) {
        return cycleModule(trend, cycleNumber, isMelee ? MODE_MELEE : MODE_REMOTE);
    }

    /**
     * 获取属性的基础数值（用于裂罅词条随机计算）
     * Get base attribute value (used for Riven attribute randomization)
     *
     * @param attributeType 属性类型名 / attribute type name
     * @return 基础数值 / base value
     */
    public static double getBaseAttributeValue(String attributeType) {
        if (attributeType.equals("meleeDamage") || attributeType.equals("remoteDamage")) {
            return 1.65;
        }
        if (attributeType.equals("arrowDamage") || attributeType.equals("projectileDamage") || attributeType.equals("magicDamage")) {
            return 1.8;
        }
        if (attributeType.equals("multishot")) {
            return 0.9;
        }
        if (attributeType.equals("attackSpeed")) {
            return 0.6;
        }
        if (attributeType.equals("attackRange")) {
            return 2.0;
        }
        if (attributeType.equals("triggerChance")) {
            return 0.9;
        }
        if (attributeType.equals("meleeCriticalStrikeProbability")) {
            return 1.8;
        }
        if (attributeType.equals("meleeCriticalStrikeMultiplier")) {
            return 0.9;
        }
        if (attributeType.equals("remoteCriticalStrikeProbability")) {
            return 1.5;
        }
        if (attributeType.equals("remoteCriticalStrikeMultiplier")) {
            return 1.2;
        }
        if (attributeType.equals("bane_of_undefined") || attributeType.equals("bane_of_undead") ||
                attributeType.equals("bane_of_arthropod") || attributeType.equals("bane_of_illager")) {
            return 0.45;
        }
        if (attributeType.equals("fire") || attributeType.equals("ice") ||
                attributeType.equals("poison") || attributeType.equals("electricity")) {
            return 0.9;
        }
        if (attributeType.equals("firing_rate")) {
            return 0.6;
        }
        if (attributeType.equals("triggerTime")) {
            return 1.0;
        }
        if (attributeType.equals("slash") || attributeType.equals("puncture") || attributeType.equals("impact")) {
            return 1.2;
        }
        if (attributeType.equals("dashMeleeCriticalStrikeProbability")) {
            return 2.25;
        }
        if (attributeType.equals("dashAttackRange")) {
            return 5.0;
        }
        if (attributeType.equals("dashTriggerChance")) {
            return 2.25;
        }
        if (attributeType.equals("baseDamageWhenNotCriticalStrike")) {
            return 2.0;
        }
        if (attributeType.equals("bursting_radius")) {
            return 0.34;
        }

        // 复合元素基础数值
        if (attributeType.equals("gas") || attributeType.equals("radiation") ||
                attributeType.equals("magnetic") || attributeType.equals("corrosion") ||
                attributeType.equals("explosion") || attributeType.equals("virus")) {
            return 0.6;
        }

        // 击杀叠加词条基础数值
        if (attributeType.equals("killStackBaseDamage")) {
            return 0.04;
        }
        if (attributeType.equals("killStackMultishot")) {
            return 0.30;
        }
        if (attributeType.equals("killStackMeleeCriticalMultiplier")) {
            return 0.30;
        }
        if (attributeType.equals("killStackTriggerChance")) {
            return 0.30;
        }
        if (attributeType.equals("killStackAttackRange")) {
            return 0.50;
        }
        if (attributeType.equals("killStackAttackSpeed")) {
            return 0.08;
        }
        if (attributeType.equals("killStackBurstingRadius")) {
            return 0.08;
        }
        if (attributeType.equals("killStackFiringRate")) {
            return 0.08;
        }

        return 0;
    }

    /**
     * 设置洗卡次数
     * Set cycle count
     *
     * @param itemStack 目标物品 / target item
     * @param cycleNumber 洗卡次数 / cycle count
     */
    public static void setCycle(ItemStack itemStack, int cycleNumber) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("Cycle", cycleNumber);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    /**
     * 获取洗卡次数
     * Get cycle count
     *
     * @param itemStack 目标物品 / target item
     * @return 洗卡次数 / cycle count
     */
    public static int getCycle(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule.getInt("Cycle");
    }

    /**
     * 设置倾向性
     * Set trend value
     *
     * @param itemStack 目标物品 / target item
     * @param trend 倾向性（1-5） / trend (1-5)
     */
    private static void setTrend(ItemStack itemStack, int trend) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("Trend", trend);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    /**
     * 获取倾向性
     * Get trend value
     *
     * @param itemStack 目标物品 / target item
     * @return 倾向性（1-5） / trend (1-5)
     */
    public static int getTrend(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule.getInt("Trend");
    }

    /**
     * 注册所有模组到创造标签页
     * Register all modules to creative tab
     *
     * @param output 创造标签页输出 / creative tab output
     */
    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        // 只有未被禁用时才显示随机Riven
        if (!isRivenDisabled()) {
            output.accept(getRandomModule());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && ItemModuleBase.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            // 检查Riven是否被禁用
            if (isRivenDisabled()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemStack module = initModule();

            // 如果返回空物品，说明被禁用了
            if (module.isEmpty()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemEntity entityItem = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), module);
            level.addFreshEntity(entityItem);

            player.setItemInHand(hand, ItemStack.EMPTY);
            return InteractionResultHolder.success(itemstack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}