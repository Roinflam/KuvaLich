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
import java.util.Random;

public class ItemRivenModule extends ItemModuleBase {
    private static final String[] PREFIXES = {"Croni", "Sati", "Vexi", "Locti", "Magna", "Crita", "Geli", "Rupti", "Arma", "Venxi", "Furi", "Praesi", "Nexi", "Draco", "Spira", "Phasa", "Lunari", "Solara", "Terron", "Aquix", "Ventra", "Ignis", "Frosti", "Voltic", "Plasma", "Ethera", "Radi", "Mortal", "Divin", "Spectra", "Celest", "Infern", "Obliv", "Eclip", "Cosmi", "Stella", "Astron", "Nebula", "Galax", "Orbit", "Nova", "Lunar", "Solar", "Comet", "Astro", "Stellar", "Void", "Quantum", "Gluon", "Gravi", "Photon", "Pulsar", "Quark", "Radian", "Sigma", "Tau", "Upsilon", "Vecti", "Warp", "Xenon", "Yotta", "Zetta", "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega", "Axion", "Baryon", "Charm", "Dynami", "Electro", "Fluxi", "Gyro", "Halo", "Ioni", "Joule", "Kineti", "Lepto", "Mytho", "Neuro", "Omni", "Penta", "Quanta", "Retro", "Syntho", "Tri", "Umbra", "Vecta", "Wyrm", "Xero", "Yield", "Zephyr"};

    private static final String[] SUFFIXES = {"cron", "ata", "icor", "tis", "tron", "cak", "nus", "vex", "mira", "ton", "sera", "phix", "gara", "luxe", "moto", "zora", "fyre", "glacia", "volt", "terra", "aqua", "nebula", "stellar", "cosmo", "sol", "lunar", "astral", "void", "nether", "ether", "flux", "halo", "vortex", "quantum", "sigma", "omega", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega", "alpha", "beta", "axion", "baryon", "charm", "dynami", "electro", "fluxi", "gyro", "halo", "ioni", "joule", "kineti", "lepto", "mytho", "neuro", "omni", "penta", "quanta", "retro", "syntho", "tri", "umbra", "vecta", "wyrm", "xero", "yield", "zephyr", "ara", "bolo", "ceta", "dome", "ergo", "foti", "glow", "hype", "ille", "juno", "kilo", "lima", "mote", "nano", "oxi", "pico", "quark", "rune", "solo", "tome", "uni", "volo", "watt", "xene", "yotta", "zetta"};

    /**
     * Riven模组的固定type值
     * 用于配置文件禁用检查
     */
    public static final String RIVEN_TYPE = "riven_weapon_module";

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
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    /**
     * 检查武器Riven模组是否被禁用
     *
     * @return true表示被禁用
     */
    public static boolean isRivenDisabled() {
        return ModuleConfig.isTypeDisabled(RIVEN_TYPE);
    }

    public static ItemStack initModule() {
        // 检查是否被禁用
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
        setMelee(itemStack, new Random().nextBoolean());
        return cycleModule(getTrend(itemStack), getCycle(itemStack) - 1, isMelee(itemStack));
    }

    public static void setMelee(ItemStack itemStack, boolean isMelee) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.putBoolean("Melee", isMelee);
        tag.put(Reference.MOD_ID + "_modules", kuvalichModule);
    }

    public static boolean isMelee(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        CompoundTag kuvalichModule = tag.getCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule.getBoolean("Melee");
    }

    /**
     * 判断某属性是否为近战专属（远程Riven不可出现）
     * Check if an attribute is melee-exclusive (cannot appear on remote Riven)
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

    public static ItemStack cycleModule(int trend, int cycleNumber, boolean isMelee) {
        // 检查是否被禁用
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        if (isMelee) {
            itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + "MeleeRiven " + generateRivenName()));
        } else {
            itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + "RemoteRiven " + generateRivenName()));
        }
        setTrend(itemStack, trend);
        setCycle(itemStack, cycleNumber + 1);
        setMelee(itemStack, isMelee);

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

            // 根据近战/远程过滤专属词条（正面）
            // Filter exclusive attributes for melee/remote (positive)
            if (isMelee && isRemoteExclusiveAttr(attributeType)) {
                continue;
            }
            if (!isMelee && isMeleeExclusiveAttr(attributeType)) {
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

                // 根据近战/远程过滤专属词条（负面）
                // Filter exclusive attributes for melee/remote (negative)
                if (isMelee && isRemoteExclusiveAttr(attributeType)) {
                    continue;
                }
                if (!isMelee && isMeleeExclusiveAttr(attributeType)) {
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