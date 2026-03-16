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

/**
 * 裂罅武器模组（1.20.1版本）
 * 支持三种模式：近战(0)、远程(1)、通用(2)
 * 通用模式近战和远程词条均可出现，形成大混池
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
     */
    private static final Set<String> TACZ_EXCLUSIVE_ATTRIBUTES = Set.of(
            "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction",
            "firing_rate", "bursting_radius", "multishot",
            "killStackMultishot", "killStackBurstingRadius", "killStackFiringRate",
            "gun_damage", "headshot_damage", "aim_time", "accuracy"
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
                || attributeType.equals("accuracy");
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