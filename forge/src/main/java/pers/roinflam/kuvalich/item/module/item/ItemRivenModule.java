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

import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractModule;
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
import java.util.Set;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

/**
 * 裂罅武器模组（1.20.1版本）
 * 支持三种模式：近战(0)、远程(1)、通用(2)
 * 通用模式近战和远程词条均可出现，形成大混池
 *
 * ⭐ 负面词条排除规则（本次新增）：
 *    以下词条不会作为负面词条被洗出，原因分两类：
 *    1. 机制类新词条（true_bullet / gun_loot_drop / execute_threshold / purge_buff / execute_chance）——
 *       除 gun_loot_drop 外，其余在战斗代码中均有正值守卫，负值完全无效（死负面），
 *       玩家白吃 ×1.2 正面加成；且"负的处决阈值"等语义不通，tooltip 显示也很怪。
 *       gun_loot_drop 虽然负值有效，但为保持五个新词条规则统一，一并排除。
 *    2. 击杀叠层词条（killStack* 前缀）——
 *       战斗代码以 containsKey 判定是否累加叠层，负值词条会导致"越杀越弱"的毒词条，
 *       与"无伤负"设计意图相悖，故全部排除。
 *
 * Riven weapon module (1.20.1 version)
 * Supports three modes: Melee(0), Remote(1), Universal(2)
 * Universal mode allows both melee and remote attributes in a single mixed pool
 */
public class ItemRivenModule extends AbstractItemModule {
    private static final String[] PREFIXES = {"Croni", "Sati", "Vexi", "Locti", "Magna", "Crita", "Geli", "Rupti", "Arma", "Venxi", "Furi", "Praesi", "Nexi", "Draco", "Spira", "Phasa", "Lunari", "Solara", "Terron", "Aquix", "Ventra", "Ignis", "Frosti", "Voltic", "Plasma", "Ethera", "Radi", "Mortal", "Divin", "Spectra", "Celest", "Infern", "Obliv", "Eclip", "Cosmi", "Stella", "Astron", "Nebula", "Galax", "Orbit", "Nova", "Lunar", "Solar", "Comet", "Astro", "Stellar", "Void", "Quantum", "Gluon", "Gravi", "Photon", "Pulsar", "Quark", "Radian", "Sigma", "Tau", "Upsilon", "Vecti", "Warp", "Xenon", "Yotta", "Zetta", "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega", "Axion", "Baryon", "Charm", "Dynami", "Electro", "Fluxi", "Gyro", "Halo", "Ioni", "Joule", "Kineti", "Lepto", "Mytho", "Neuro", "Omni", "Penta", "Quanta", "Retro", "Syntho", "Tri", "Umbra", "Vecta", "Wyrm", "Xero", "Yield", "Zephyr"};

    private static final String[] SUFFIXES = {"cron", "ata", "icor", "tis", "tron", "cak", "nus", "vex", "mira", "ton", "sera", "phix", "gara", "luxe", "moto", "zora", "fyre", "glacia", "volt", "terra", "aqua", "nebula", "stellar", "cosmo", "sol", "lunar", "astral", "void", "nether", "ether", "flux", "halo", "vortex", "quantum", "sigma", "omega", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega", "alpha", "beta", "axion", "baryon", "charm", "dynami", "electro", "fluxi", "gyro", "halo", "ioni", "joule", "kineti", "lepto", "mytho", "neuro", "omni", "penta", "quanta", "retro", "syntho", "tri", "umbra", "vecta", "wyrm", "xero", "yield", "zephyr", "ara", "bolo", "ceta", "dome", "ergo", "foti", "glow", "hype", "ille", "juno", "kilo", "lima", "mote", "nano", "oxi", "pico", "quark", "rune", "solo", "tome", "uni", "volo", "watt", "xene", "yotta", "zetta"};

    public static final String RIVEN_TYPE = "riven_weapon_module";

    public static final int MODE_MELEE = 0;
    public static final int MODE_REMOTE = 1;
    public static final int MODE_UNIVERSAL = 2;

    /**
     * 需要 TACZ 模组才能生效的词条集合。
     * 未安装 TACZ 时，这些词条不会出现在紫卡随机池中，避免洗出无效废词条。
     * <p>
     * ⭐ 第三批新增：true_bullet（真实伤害）、gun_loot_drop（枪械战利品掉落）为 TACZ 专属。
     *    execute_threshold / purge_buff / execute_chance 为通用词条，不在此集合内。
     */
    private static final Set<String> TACZ_EXCLUSIVE_ATTRIBUTES = Set.of(
            "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction",
            "firing_rate", "bursting_radius", "multishot",
            "killStackMultishot", "killStackBurstingRadius", "killStackFiringRate",
            "gun_damage", "headshot_damage", "aim_time", "accuracy",
            "true_bullet", "gun_loot_drop"
    );

    /**
     * ⭐ 禁止作为负面词条出现的机制类新词条集合。
     * <p>
     * true_bullet / execute_threshold / purge_buff / execute_chance 在战斗代码中
     * 均有 {@code <= 0} 正值守卫，负值完全无效（死负面）；
     * gun_loot_drop 负值虽然有效（会真实扣掉枪械击杀掉落倍率），
     * 但为保持五个新词条规则统一、避免玩家困惑，一并排除。
     * 正面词条池不受影响，这些词条仍可正常作为正面词条洗出。
     */
    private static final Set<String> NEGATIVE_EXCLUDED_ATTRIBUTES = Set.of(
            "true_bullet", "gun_loot_drop",
            "execute_threshold", "purge_buff", "execute_chance"
    );

    /** TACZ 模组加载状态缓存 */
    private static Boolean taczLoadedCache = null;

    /**
     * 检测 TACZ 模组是否已加载
     *
     * @return true 表示 TACZ 已安装
     */
    public static boolean isTaczLoaded() {
        if (taczLoadedCache == null) {
            taczLoadedCache = net.minecraftforge.fml.ModList.get().isLoaded("tacz");
        }
        return taczLoadedCache;
    }

    public ItemRivenModule(Properties properties) {
        super(properties);
    }

    public static String generateRivenName() {
        String prefix = PREFIXES[RandomUtil.getInt(0, PREFIXES.length - 1)];
        String suffix = SUFFIXES[RandomUtil.getInt(0, SUFFIXES.length - 1)];
        return prefix + "-" + suffix;
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("kuvaweapon.item_type_riven_random.name").getString()));
        AbstractModule.setRandom(itemStack, true);
        return itemStack;
    }

    public static boolean isRivenDisabled() {
        return ModuleConfig.isTypeDisabled(RIVEN_TYPE);
    }

    public static ItemStack initModule() {
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("item.item_type_riven_random.name").getString() + " " + generateRivenName()));

        if (RandomUtil.percentageChance(2.5)) {
            setTrend(itemStack, 5);
        } else if (RandomUtil.percentageChance(5)) {
            setTrend(itemStack, 4);
        } else {
            setTrend(itemStack, RandomUtil.getInt(1, 3));
        }
        setCycle(itemStack, 0);

        int modeRoll = RandomUtil.getInt(0, 3);
        int mode;
        if (modeRoll <= 1) {
            mode = MODE_UNIVERSAL;
        } else if (modeRoll == 2) {
            mode = MODE_MELEE;
        } else {
            mode = MODE_REMOTE;
        }
        setRivenMode(itemStack, mode);

        return cycleModule(getTrend(itemStack), getCycle(itemStack) - 1, getRivenMode(itemStack));
    }

    public static void setRivenMode(ItemStack itemStack, int mode) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("RivenMode", mode);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    public static int getRivenMode(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        if (kuvalichModule.contains("RivenMode")) {
            return kuvalichModule.getInt("RivenMode");
        }
        if (kuvalichModule.contains("Melee")) {
            return kuvalichModule.getBoolean("Melee") ? MODE_MELEE : MODE_REMOTE;
        }
        return MODE_MELEE;
    }

    @Deprecated
    public static void setMelee(ItemStack itemStack, boolean isMelee) {
        setRivenMode(itemStack, isMelee ? MODE_MELEE : MODE_REMOTE);
    }

    @Deprecated
    public static boolean isMelee(ItemStack itemStack) {
        return getRivenMode(itemStack) == MODE_MELEE;
    }

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
                || attributeType.equals("killStackFiringRate")
                // ===== TACZ 枪械新属性（远程专属）=====
                || attributeType.equals("reload_speed")
                || attributeType.equals("magazine_size")
                || attributeType.equals("projectile_speed")
                || attributeType.equals("recoil_reduction")
                // ===== TACZ 枪械新属性（第二批，远程专属）=====
                || attributeType.equals("gun_damage")
                || attributeType.equals("headshot_damage")
                || attributeType.equals("aim_time")
                || attributeType.equals("accuracy")
                // ===== TACZ 枪械新属性（第三批，远程专属）=====
                || attributeType.equals("true_bullet")
                || attributeType.equals("gun_loot_drop");
    }

    private static boolean shouldFilterAttribute(String attributeType, int rivenMode) {
        if (rivenMode == MODE_UNIVERSAL) {
            return false;
        }
        if (rivenMode == MODE_MELEE) {
            return isRemoteExclusiveAttr(attributeType);
        }
        if (rivenMode == MODE_REMOTE) {
            return isMeleeExclusiveAttr(attributeType);
        }
        return false;
    }

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
     * 洗卡/生成裂罅词条
     * 根据倾向性、洗卡次数和裂罅模式重新随机所有词条
     * 未安装 TACZ 时自动过滤枪械专属词条
     * <p>
     * ⭐ 负面词条额外排除（仅影响负面槽，正面词条池不变）：
     *    机制类新词条（NEGATIVE_EXCLUDED_ATTRIBUTES）与所有击杀叠层词条（killStack* 前缀）。
     */
    public static ItemStack cycleModule(int trend, int cycleNumber, int rivenMode) {
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + getRivenNamePrefix(rivenMode) + " " + generateRivenName()));
        setTrend(itemStack, trend);
        setCycle(itemStack, cycleNumber + 1);
        setRivenMode(itemStack, rivenMode);

        double globalMultiplier = ModConfig.KUVA_LICH.moduleAttributeMultiplier.get();

        // 缓存 TACZ 加载状态，避免循环内重复查询
        boolean taczAvailable = isTaczLoaded();

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

        List<String> itemAttributeType = new ArrayList<>(AbstractItemModule.ITEM_ATTRIBUTE_TYPES);
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

            // 根据裂罅模式过滤专属词条
            if (shouldFilterAttribute(attributeType, rivenMode)) {
                continue;
            }

            // 未安装 TACZ 时过滤枪械专属词条，避免洗出无效废词条
            if (!taczAvailable && TACZ_EXCLUSIVE_ATTRIBUTES.contains(attributeType)) {
                continue;
            }

            double randomDouble = 0.9 + (Math.random() * (1.1 - 0.9));
            randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

            hasAttributeType.add(attributeType);
            AbstractItemModule.addAttributes(itemStack, attributeType,
                    getBaseAttributeValue(attributeType) * trendMagnification * randomDouble * globalMultiplier);
            add++;
        }

        if (negative) {
            for (String attributeType : itemAttributeType) {
                if (hasAttributeType.contains(attributeType)) {
                    continue;
                }

                if (attributeType.equals("gas") || attributeType.equals("radiation") ||
                        attributeType.equals("magnetic") || attributeType.equals("corrosion") ||
                        attributeType.equals("explosion") || attributeType.equals("virus")) {
                    continue;
                }

                if (shouldFilterAttribute(attributeType, rivenMode)) {
                    continue;
                }

                // 未安装 TACZ 时过滤枪械专属词条（负面同样过滤）
                if (!taczAvailable && TACZ_EXCLUSIVE_ATTRIBUTES.contains(attributeType)) {
                    continue;
                }

                if (attributeType.equals("fire") || attributeType.equals("ice") ||
                        attributeType.equals("poison") || attributeType.equals("electricity")) {
                    continue;
                }

                // ⭐ 机制类新词条不出负面：
                //    true_bullet / execute_threshold / purge_buff / execute_chance 负值在战斗代码中
                //    被正值守卫拦截，属于完全无效的"死负面"（白吃 ×1.2 正面加成）；
                //    gun_loot_drop 负值虽有效，但为保持新词条规则统一一并排除。
                if (NEGATIVE_EXCLUDED_ATTRIBUTES.contains(attributeType)) {
                    continue;
                }

                // ⭐ 击杀叠层词条不出负面：
                //    战斗代码以 containsKey 判定累加叠层，负值词条会让玩家"越杀越弱"，
                //    属于惩罚击杀行为的毒词条，与无伤负设计意图相悖。
                if (attributeType.startsWith("killStack")) {
                    continue;
                }

                double randomDouble = 0.8 + (Math.random() * (1.2 - 0.8));
                randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

                AbstractItemModule.addAttributes(itemStack, attributeType,
                        -(getBaseAttributeValue(attributeType) * negativeTrendMagnification * randomDouble * globalMultiplier));
                break;
            }
        }

        AbstractItemModule.setType(itemStack, RIVEN_TYPE);

        return itemStack;
    }

    @Deprecated
    public static ItemStack cycleModule(int trend, int cycleNumber, boolean isMelee) {
        return cycleModule(trend, cycleNumber, isMelee ? MODE_MELEE : MODE_REMOTE);
    }

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
        if (attributeType.equals("gas") || attributeType.equals("radiation") ||
                attributeType.equals("magnetic") || attributeType.equals("corrosion") ||
                attributeType.equals("explosion") || attributeType.equals("virus")) {
            return 0.6;
        }
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
        // ===== TACZ 枪械新属性（第一批）基础数值 =====
        if (attributeType.equals("reload_speed")) {
            return 0.3;
        }
        if (attributeType.equals("magazine_size")) {
            return 0.3;
        }
        if (attributeType.equals("projectile_speed")) {
            return 0.6;
        }
        if (attributeType.equals("recoil_reduction")) {
            return 0.6;
        }
        // ===== TACZ 枪械新属性（第二批）基础数值 =====
        if (attributeType.equals("gun_damage")) {
            return 2.475;
        }
        if (attributeType.equals("headshot_damage")) {
            return 1.2;
        }
        if (attributeType.equals("aim_time")) {
            return 0.3;
        }
        if (attributeType.equals("accuracy")) {
            return 0.3;
        }
        // ===== 第三批新词条基础数值（= 对应单词条银卡数值）=====
        // 真实伤害（TACZ 专属）：银卡 20%
        if (attributeType.equals("true_bullet")) {
            return 0.20;
        }
        // 枪械战利品掉落（TACZ 专属）：银卡 30%
        if (attributeType.equals("gun_loot_drop")) {
            return 0.30;
        }
        // 收集者阈值（通用）：银卡 2%，无上限，绕生命上限百分比处决
        if (attributeType.equals("execute_threshold")) {
            return 0.02;
        }
        // 净化驱散（通用）：银卡 10% 概率
        if (attributeType.equals("purge_buff")) {
            return 0.10;
        }
        // 致命斩首（通用）：银卡 0.01% 概率
        if (attributeType.equals("execute_chance")) {
            return 0.001;
        }

        return 0;
    }

    public static void setCycle(ItemStack itemStack, int cycleNumber) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("Cycle", cycleNumber);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    public static int getCycle(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule.getInt("Cycle");
    }

    private static void setTrend(ItemStack itemStack, int trend) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putInt("Trend", trend);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    public static int getTrend(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule.getInt("Trend");
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (!isRivenDisabled()) {
            output.accept(getRandomModule());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && AbstractItemModule.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            if (isRivenDisabled()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemStack module = initModule();

            if (module.isEmpty()) {
                return InteractionResultHolder.fail(itemstack);
            }


// ⭐ 模组等级系统：赋予揭示等级
            ModuleLevelHelper.applyRevealLevel(module, player);
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