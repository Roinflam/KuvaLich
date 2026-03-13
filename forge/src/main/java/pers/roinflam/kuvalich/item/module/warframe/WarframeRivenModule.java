package pers.roinflam.kuvalich.item.module.warframe;

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
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
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

public class WarframeRivenModule extends AbstractWarframeModule {
    private static final String[] PREFIXES = {"Croni", "Sati", "Vexi", "Locti", "Magna", "Crita", "Geli", "Rupti", "Arma", "Venxi", "Furi", "Praesi", "Nexi", "Draco", "Spira", "Phasa", "Lunari", "Solara", "Terron", "Aquix", "Ventra", "Ignis", "Frosti", "Voltic", "Plasma", "Ethera", "Radi", "Mortal", "Divin", "Spectra", "Celest", "Infern", "Obliv", "Eclip", "Cosmi", "Stella", "Astron", "Nebula", "Galax", "Orbit", "Nova", "Lunar", "Solar", "Comet", "Astro", "Stellar", "Void", "Quantum", "Gluon", "Gravi", "Photon", "Pulsar", "Quark", "Radian", "Sigma", "Tau", "Upsilon", "Vecti", "Warp", "Xenon", "Yotta", "Zetta", "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega", "Axion", "Baryon", "Charm", "Dynami", "Electro", "Fluxi", "Gyro", "Halo", "Ioni", "Joule", "Kineti", "Lepto", "Mytho", "Neuro", "Omni", "Penta", "Quanta", "Retro", "Syntho", "Tri", "Umbra", "Vecta", "Wyrm", "Xero", "Yield", "Zephyr"};

    private static final String[] SUFFIXES = {"cron", "ata", "icor", "tis", "tron", "cak", "nus", "vex", "mira", "ton", "sera", "phix", "gara", "luxe", "moto", "zora", "fyre", "glacia", "volt", "terra", "aqua", "nebula", "stellar", "cosmo", "sol", "lunar", "astral", "void", "nether", "ether", "flux", "halo", "vortex", "quantum", "sigma", "omega", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega", "alpha", "beta", "axion", "baryon", "charm", "dynami", "electro", "fluxi", "gyro", "halo", "ioni", "joule", "kineti", "lepto", "mytho", "neuro", "omni", "penta", "quanta", "retro", "syntho", "tri", "umbra", "vecta", "wyrm", "xero", "yield", "zephyr", "ara", "bolo", "ceta", "dome", "ergo", "foti", "glow", "hype", "ille", "juno", "kilo", "lima", "mote", "nano", "oxi", "pico", "quark", "rune", "solo", "tome", "uni", "volo", "watt", "xene", "yotta", "zetta"};

    /**
     * 战甲Riven模组的固定type值
     * 用于配置文件禁用检查
     */
    public static final String RIVEN_TYPE = "riven_warframe_module";

    public WarframeRivenModule(Properties properties) {
        super(properties);
    }

    public static String generateRivenName() {
        String prefix = PREFIXES[RandomUtil.getInt(0, PREFIXES.length - 1)];
        String suffix = SUFFIXES[RandomUtil.getInt(0, SUFFIXES.length - 1)];
        return prefix + "-" + suffix;
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("kuvaweapon.warframe_type_riven_random.name").getString()));
        AbstractModule.setRandom(itemStack, true);
        return itemStack;
    }

    /**
     * 检查战甲Riven模组是否被禁用
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

        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + Component.translatable("item.warframe_type_riven_random.name").getString() + " " + generateRivenName()));
        if (RandomUtil.percentageChance(2.5)) {
            setTrend(itemStack, 5);
        } else if (RandomUtil.percentageChance(5)) {
            setTrend(itemStack, 4);
        } else {
            setTrend(itemStack, RandomUtil.getInt(1, 3));
        }
        setCycle(itemStack, 0);
        return cycleModule(getTrend(itemStack), getCycle(itemStack) - 1);
    }

    public static ItemStack cycleModule(int trend, int cycleNumber) {
        // 检查是否被禁用
        if (isRivenDisabled()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RIVEN_MODULE.get());
        itemStack.setHoverName(Component.literal(ChatFormatting.DARK_PURPLE + "Warframe " + generateRivenName()));
        setTrend(itemStack, trend);
        setCycle(itemStack, cycleNumber + 1);

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

        List<String> warframeAttributeType = new ArrayList<>(AbstractWarframeModule.WARFRAME_ATTRIBUTE_TYPES);

        // 紫卡不能刷出固定属性
        warframeAttributeType.remove("fixedHealth");
        warframeAttributeType.remove("fixedShield");
        warframeAttributeType.remove("fixedArmor");

        Collections.shuffle(warframeAttributeType);

        List<String> hasAttributeType = new ArrayList<>();
        int number = more ? 3 : 2;
        int add = 0;
        for (String attributeType : warframeAttributeType) {
            if (add >= number) {
                break;
            }
            double randomDouble = 0.9 + (Math.random() * (1.1 - 0.9));
            randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

            hasAttributeType.add(attributeType);
            // 应用全局属性倍率（正面属性）
            // Apply global attribute multiplier (positive attributes)
            AbstractModule.addAttributes(itemStack, attributeType,
                    getBaseAttributeValue(attributeType) * trendMagnification * randomDouble * globalMultiplier);
            add++;
        }

        if (negative) {
            for (String attributeType : warframeAttributeType) {
                if (hasAttributeType.contains(attributeType)) {
                    continue;
                }
                double randomDouble = 0.8 + (Math.random() * (1.2 - 0.8));
                randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

                // 应用全局属性倍率（负面属性）
                // Apply global attribute multiplier (negative attributes)
                AbstractModule.addAttributes(itemStack, attributeType,
                        -(getBaseAttributeValue(attributeType) * negativeTrendMagnification * randomDouble * globalMultiplier));
                break;
            }
        }

        // 设置战甲Riven专用type
        AbstractModule.setType(itemStack, RIVEN_TYPE);

        return itemStack;
    }

    /**
     * 获取战甲属性的基础数值
     * 固定属性返回0(紫卡不能刷出)
     */
    public static double getBaseAttributeValue(String attributeType) {
        // ========== 基础属性 ==========
        if (attributeType.equals("health") || attributeType.equals("shield") || attributeType.equals("armor")) {
            return 1.65;
        }
        if (attributeType.equals("sprintSpeed")) {
            return 0.2;
        }
        if (attributeType.equals("shieldRecoveryRate")) {
            return 0.9;
        }
        if (attributeType.equals("shieldRecoveryDelay")) {
            return -0.45;
        }
        if (attributeType.equals("knockbackResistance")) {
            return 0.6;
        }
        if (attributeType.equals("fireProtection") || attributeType.equals("electricProtection")) {
            return 0.6;
        }
        if (attributeType.equals("homologousProtection")) {
            return 0.3;
        }
        if (attributeType.equals("reachDistance") || attributeType.equals("diggingSpeed")) {
            return 0.3;
        }
        if (attributeType.equals("responseRate")) {
            return 0.9;
        }
        if (attributeType.equals("itemDropMultiplier")) {
            return 0.9;
        }
        if (attributeType.equals("jumpBoost")) {
            return 2.0;
        }
        if (attributeType.equals("fallProtection")) {
            return 0.4;
        }

        // 固定属性返回0(紫卡不能刷出)
        if (attributeType.equals("fixedHealth") ||
                attributeType.equals("fixedShield") ||
                attributeType.equals("fixedArmor")) {
            return 0;
        }

        // ========== 击杀叠层属性(执刑官系列单层加成值)==========
        if (attributeType.equals("killStackHealth")) {
            return 0.08;
        }
        if (attributeType.equals("killStackShield")) {
            return 0.08;
        }
        if (attributeType.equals("killStackArmor")) {
            return 0.08;
        }
        if (attributeType.equals("killStackSprintSpeed")) {
            return 0.02;
        }
        if (attributeType.equals("killStackShieldRecoveryRate")) {
            return 0.02;
        }
        if (attributeType.equals("killStackShieldRecoveryDelay")) {
            return -0.02;
        }
        if (attributeType.equals("killStackFireProtection")) {
            return 0.02;
        }
        if (attributeType.equals("killStackElectricProtection")) {
            return 0.02;
        }
        if (attributeType.equals("killStackHomologousProtection")) {
            return 0.03;
        }
        if (attributeType.equals("killStackResponseRate")) {
            return 0.02;
        }
        if (attributeType.equals("killStackItemDropMultiplier")) {
            return 0.03;
        }
        if (attributeType.equals("killStackDiggingSpeed")) {
            return 0.02;
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
        if (!level.isClientSide() && AbstractItemModule.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            // 检查Riven是否被禁用
            if (isRivenDisabled()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemStack module = initModule();

            // 如果返回空物品,说明被禁用了
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
        return true;
    }
}