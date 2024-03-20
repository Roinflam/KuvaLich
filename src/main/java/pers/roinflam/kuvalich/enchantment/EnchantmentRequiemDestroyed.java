package pers.roinflam.kuvalich.enchantment;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.ItemCommonModule;
import pers.roinflam.kuvalich.item.module.item.ItemRareModule;
import pers.roinflam.kuvalich.item.module.item.ItemUncommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeCommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.Random;

@Mod.EventBusSubscriber
public class EnchantmentRequiemDestroyed extends EnchantmentBase {

    public EnchantmentRequiemDestroyed(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots) {
        super(rarityIn, typeIn, slots, "requiem_destroyed");
        KuvaLichEnchantments.ENCHANTMENTS.add(this);
    }

    public static @NotNull Enchantment getEnchantment() {
        return KuvaLichEnchantments.REQUIEM_DESTROYED;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.@NotNull BreakSpeed evt) {
        @NotNull Block block = evt.getState().getBlock();
        if (block.equals(KuvaLichBlocks.REQUIEM_ORE) || block.equals(KuvaLichBlocks.REQUIEM_GATE) || block.equals(KuvaLichBlocks.REQUIEM_RECAST) || block.equals(KuvaLichBlocks.REQUIEM_EVOLVE)) {
            EntityPlayer entityPlayer = evt.getEntityPlayer();
            if (entityPlayer.swingingHand != null) {
                @NotNull ItemStack itemStack = entityPlayer.getHeldItem(entityPlayer.swingingHand);
                if (!itemStack.isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    if (bonusLevel > 0) {
                        evt.setNewSpeed(300);
                    } else {
                        IBlockState iBlockState = evt.getState();
                        evt.setNewSpeed(Math.min(itemStack.getDestroySpeed(iBlockState), 1));
                    }
                }
            }
        }
    }


    @SubscribeEvent
    public static void onBreak(BlockEvent.@NotNull BreakEvent evt) {
        @NotNull Block block = evt.getState().getBlock();
        if (block.equals(KuvaLichBlocks.REQUIEM_ORE)) {
            EntityPlayer entityPlayer = evt.getPlayer();
            @NotNull ItemStack itemStack = entityPlayer.getHeldItem(entityPlayer.getActiveHand());
            if (!itemStack.isEmpty()) {
                if (EnchantmentHelper.getEnchantmentLevel(KuvaLichEnchantments.REQUIEM_DESTROYED, itemStack) > 0) {
                    evt.setExpToDrop(evt.getExpToDrop() * 2);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onHarvestDrops(BlockEvent.@NotNull HarvestDropsEvent evt) {
        @NotNull Block block = evt.getState().getBlock();
        if (block.equals(KuvaLichBlocks.REQUIEM_ORE)) {
            EntityPlayer entityPlayer = evt.getHarvester();
            @NotNull ItemStack itemStack = entityPlayer.getHeldItem(entityPlayer.getActiveHand());
            if (!itemStack.isEmpty()) {
                if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, itemStack) <= 0) {
                    int level = EnchantmentHelper.getEnchantmentLevel(KuvaLichEnchantments.REQUIEM_DESTROYED, itemStack);
                    if (level > 0 ? RandomUtil.percentageChance(75) : RandomUtil.percentageChance(Math.max(25 - 2.5 * EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, itemStack), 5))) {
                        Item @NotNull [] items = new Item[]{
                                KuvaLichItems.FASS_CARD,
                                KuvaLichItems.JAHU_CARD,
                                KuvaLichItems.KHRA_CARD,
                                KuvaLichItems.LOHK_CARD,
                                KuvaLichItems.NETRA_CARD,
                                KuvaLichItems.RIS_CARD,
                                KuvaLichItems.VOME_CARD,
                                KuvaLichItems.XATA_CARD
                        };
                        evt.getDrops().add(new ItemStack(items[new Random().nextInt(8)], 1, block.damageDropped(evt.getState())));

                        World world = evt.getWorld();
                        @NotNull BlockPos blockPos = evt.getPos();
                        EntityItem entityItem;
                        if (RandomUtil.percentageChance(15)) {
                            entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemCommonModule.getRandomModule() : WarframeCommonModule.getRandomModule());
                            world.spawnEntity(entityItem);
                        } else if (RandomUtil.percentageChance(10)) {
                            entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemUncommonModule.getRandomModule() : WarframeUncommonModule.getRandomModule());
                            world.spawnEntity(entityItem);
                        } else if (RandomUtil.percentageChance(5)) {
                            entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), RandomUtil.percentageChance(75) ? ItemRareModule.getRandomModule() : WarframeRareModule.getRandomModule());
                            world.spawnEntity(entityItem);
                        }

                        entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.RivenSliver, 1));
                        world.spawnEntity(entityItem);

                        entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(2, 4)));
                        world.spawnEntity(entityItem);
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 35;
    }

}
