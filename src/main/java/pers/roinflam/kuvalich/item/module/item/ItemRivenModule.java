package pers.roinflam.kuvalich.item.module.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
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


    public ItemRivenModule(@NotNull String name) {
        super(name);
    }

    public static String generateRivenName() {
        String prefix = PREFIXES[RandomUtil.getInt(0, PREFIXES.length - 1)];
        String suffix = SUFFIXES[RandomUtil.getInt(0, SUFFIXES.length - 1)];
        return prefix + "-" + suffix;
    }


    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE);
        itemStack.setStackDisplayName(TextFormatting.DARK_PURPLE + I18n.translateToLocal("kuvaweapon.item_type_riven_random.name"));
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    public static ItemStack initModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE);
        itemStack.setStackDisplayName(TextFormatting.DARK_PURPLE + I18n.translateToLocal("item.item_type_riven_random.name") + " " + generateRivenName());
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
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        kuvalichModule.setBoolean("Melee", isMelee);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

    public static boolean isMelee(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        return kuvalichModule.getBoolean("Melee");
    }

    public static ItemStack cycleModule(int trend, int cycleNumber, boolean isMelee) {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE);
        if (isMelee) {
            itemStack.setStackDisplayName(TextFormatting.DARK_PURPLE + "MeleeRiven " + generateRivenName());
        } else {
            itemStack.setStackDisplayName(TextFormatting.DARK_PURPLE + "RemoteRiven " + generateRivenName());
        }
        setTrend(itemStack, trend);
        setCycle(itemStack, cycleNumber + 1);
        setMelee(itemStack, isMelee);

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

        List<String> itemAttributeType = new ArrayList<>(ItemModuleBase.itemAttributeType);
        Collections.shuffle(itemAttributeType);

        List<String> hasAttributeType = new ArrayList<>();
        int number = more ? 3 : 2;
        int add = 0;
        for (String attributeType : itemAttributeType) {
            if (add >= number) {
                break;
            }
            if (isMelee) {
                if (attributeType.equals("remoteDamage") || attributeType.equals("arrowDamage") || attributeType.equals("projectileDamage") || attributeType.equals("multishot") || attributeType.equals("remoteCriticalStrikeProbability") || attributeType.equals("remoteCriticalStrikeMultiplier") || attributeType.equals("firing_rate") || attributeType.equals("bursting_radius")) {
                    continue;
                }
            } else {
                if (attributeType.equals("meleeDamage") || attributeType.equals("attackSpeed") || attributeType.equals("attackRange") || attributeType.equals("meleeCriticalStrikeProbability") || attributeType.equals("meleeCriticalStrikeMultiplier") || attributeType.equals("dashMeleeCriticalStrikeProbability") || attributeType.equals("dashAttackRange") || attributeType.equals("dashTriggerChance")) {
                    continue;
                }
            }
            double randomDouble = 0.9 + (Math.random() * (1.1 - 0.9));
            randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

            hasAttributeType.add(attributeType);
            ItemModuleBase.addAttributes(itemStack, attributeType, getBaseAttributeValue(attributeType) * trendMagnification * randomDouble);
            add++;
        }

        if (negative) {
            for (String attributeType : itemAttributeType) {
                if (hasAttributeType.contains(attributeType)) {
                    continue;
                }
                if (isMelee) {
                    if (attributeType.equals("remoteDamage") || attributeType.equals("arrowDamage") || attributeType.equals("projectileDamage") || attributeType.equals("multishot") || attributeType.equals("remoteCriticalStrikeProbability") || attributeType.equals("remoteCriticalStrikeMultiplier") || attributeType.equals("firing_rate") || attributeType.equals("bursting_radius")) {
                        continue;
                    }
                } else {
                    if (attributeType.equals("meleeDamage") || attributeType.equals("attackSpeed") || attributeType.equals("attackRange") || attributeType.equals("meleeCriticalStrikeProbability") || attributeType.equals("meleeCriticalStrikeMultiplier") || attributeType.equals("dashMeleeCriticalStrikeProbability") || attributeType.equals("dashAttackRange") || attributeType.equals("dashTriggerChance")) {
                        continue;
                    }
                }
                if (attributeType.equals("fire") || attributeType.equals("ice") || attributeType.equals("poison") || attributeType.equals("electricity")) {
                    continue;
                }
                double randomDouble = 0.8 + (Math.random() * (1.2 - 0.8));
                randomDouble = new BigDecimal(Double.toString(randomDouble)).setScale(2, RoundingMode.HALF_UP).doubleValue();

                ItemModuleBase.addAttributes(itemStack, attributeType, -(getBaseAttributeValue(attributeType) * negativeTrendMagnification * randomDouble));
                break;
            }
        }
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
        if (attributeType.equals("bane_of_undefined") || attributeType.equals("bane_of_undead") || attributeType.equals("bane_of_arthropod") || attributeType.equals("bane_of_illager")) {
            return 0.45;
        }
        if (attributeType.equals("fire") || attributeType.equals("ice") || attributeType.equals("poison") || attributeType.equals("electricity")) {
            return 0.9;
        }
        if (attributeType.equals("firing_rate")) {
            return 0.6;
        }
        if (attributeType.equals("triggerTime")) {
            return 1.0;
        }
        if (attributeType.equals("slash")) {
            return 1.2;
        }
        if (attributeType.equals("puncture")) {
            return 1.2;
        }
        if (attributeType.equals("impact")) {
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
        return 0;
    }

    public static void setCycle(ItemStack itemStack, int cycleNumber) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        kuvalichModule.setInteger("Cycle", cycleNumber);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

    public static int getCycle(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        return kuvalichModule.getInteger("Cycle");
    }

    private static void setTrend(ItemStack itemStack, int trend) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        kuvalichModule.setInteger("Trend", trend);
        tag.setTag(Reference.MOD_ID + "_modules", kuvalichModule);
        nbtTagCompound.setTag("tag", tag);
        itemStack.setTagCompound(tag);
    }

    public static int getTrend(ItemStack itemStack) {
        @NotNull NBTTagCompound nbtTagCompound = itemStack.serializeNBT();
        @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
        @NotNull NBTTagCompound kuvalichModule = tag.getCompoundTag(Reference.MOD_ID + "_modules");

        return kuvalichModule.getInteger("Trend");
    }

    @Override
    public @NotNull ActionResult<ItemStack> onItemRightClick(World worldIn, @NotNull EntityPlayer playerIn, EnumHand handIn) {
        @NotNull ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            ItemStack module = initModule();

            EntityItem entityItem = new EntityItem(worldIn, playerIn.posX, playerIn.posY, playerIn.posZ, module);
            worldIn.spawnEntity(entityItem);

            playerIn.setHeldItem(handIn, ItemStack.EMPTY);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @Override
    public @NotNull EnumRarity getRarity(@NotNull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);
        }
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
