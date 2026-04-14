package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.*;

/**
 * 安魂之铸菜单（1.20.1版本）
 * Requiem Recast Menu (1.20.1 version)
 *
 * ⭐ 关闭时返还物品优先快捷栏（Inventory.add）
 */
public class MenuRequiemRecast extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler cardHandler;

    private ItemStack lastInput0 = ItemStack.EMPTY;
    private ItemStack lastInput1 = ItemStack.EMPTY;
    private ItemStack lastInput2 = ItemStack.EMPTY;

    public MenuRequiemRecast(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_RECAST.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;
        this.cardHandler = new ItemStackHandler(4);

        this.addSlot(new InputSlot(this.cardHandler, 0, 31, 54));
        this.addSlot(new InputSlot(this.cardHandler, 1, 80, 54));
        this.addSlot(new InputSlot(this.cardHandler, 2, 129, 54));
        this.addSlot(new OutputSlot(this.cardHandler, 3, 80, 10));

        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 152 - 10));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 - 10 + row * 18));
            }
        }

        LogUtil.debugEvent("安魂之铸菜单创建", player.getName().getString(), "位置: " + pos.toString());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        if (index < 4) {
            if (!this.moveItemStackTo(slotStack, 4, 40, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotStack);
        return itemstack;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!level.isClientSide) {
            checkAndProcessCrafting();
        }
    }

    private void checkAndProcessCrafting() {
        ItemStack slot0 = cardHandler.getStackInSlot(0);
        ItemStack slot1 = cardHandler.getStackInSlot(1);
        ItemStack slot2 = cardHandler.getStackInSlot(2);
        ItemStack output = cardHandler.getStackInSlot(3);

        boolean inputChanged = !ItemStack.matches(slot0, lastInput0) ||
                !ItemStack.matches(slot1, lastInput1) ||
                !ItemStack.matches(slot2, lastInput2);

        if (!output.isEmpty()) {
            lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
            return;
        }

        if (slot0.isEmpty() || slot1.isEmpty() || slot2.isEmpty()) {
            lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
            return;
        }

        if (inputChanged) {
            processCrafting(slot0, slot1, slot2);
            lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
        }
    }

    private void processCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        if (slot0.getItem() instanceof AbstractRequiemCard &&
                slot1.getItem() instanceof AbstractRequiemCard &&
                slot2.getItem() instanceof AbstractRequiemCard) {
            processRequiemCardCrafting(); return;
        }
        if (slot0.getItem() instanceof AbstractItemModule &&
                slot1.getItem() instanceof AbstractItemModule &&
                slot2.getItem() instanceof AbstractItemModule) {
            processWeaponModuleCrafting(slot0, slot1, slot2); return;
        }
        if (ModConfig.KUVA_LICH.enableWarframeModuleRecast.get() &&
                slot0.getItem() instanceof AbstractWarframeModule &&
                slot1.getItem() instanceof AbstractWarframeModule &&
                slot2.getItem() instanceof AbstractWarframeModule) {
            processWarframeModuleCrafting(slot0, slot1, slot2); return;
        }
    }

    private void processRequiemCardCrafting() {
        try {
            int randomId = level.random.nextInt(8);
            ItemStack result = new ItemStack(AbstractRequiemCard.getCard(randomId));
            cardHandler.setStackInSlot(3, result);
            cardHandler.setStackInSlot(0, ItemStack.EMPTY);
            cardHandler.setStackInSlot(1, ItemStack.EMPTY);
            cardHandler.setStackInSlot(2, ItemStack.EMPTY);
            broadcastFullState();
            LogUtil.debugEvent("安魂卡合成", "成功", result.getHoverName().getString());
        } catch (Exception e) { LogUtil.error("安魂卡合成失败", e); }
    }

    private static final int TIER_COMMON = 0;
    private static final int TIER_UNCOMMON = 1;
    private static final int TIER_RARE = 2;
    private static final int TIER_PRIME = 3;
    private static final int TIER_RIVEN = 4;
    private static final int TIER_UNKNOWN = -1;

    private int getWeaponModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof ItemRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof ItemPrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof ItemRareModule) return TIER_RARE;
        if (stack.getItem() instanceof ItemUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof ItemCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

    private ItemStack getRandomWeaponModuleByTier(int tier) {
        switch (tier) {
            case TIER_COMMON: return ItemCommonModule.getRandomModule();
            case TIER_UNCOMMON: return ItemUncommonModule.getRandomModule();
            case TIER_RARE: return ItemRareModule.getRandomModule();
            case TIER_PRIME: return ItemPrimeModule.getRandomModule();
            case TIER_RIVEN: return ItemRivenModule.getRandomModule();
            default: return null;
        }
    }

    private void processWeaponModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWeaponModuleTier(slot0);
            int tier1 = getWeaponModuleTier(slot1);
            int tier2 = getWeaponModuleTier(slot2);
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) return;

            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableRivenModuleRecast.get()) return;
                ItemStack result = getRandomWeaponModuleByTier(TIER_RIVEN);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器裂罅三合一", "成功", result.getHoverName().getString()); }
                return;
            }
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enablePrimeModuleRecast.get()) return;
                ItemStack result = getRandomWeaponModuleByTier(TIER_PRIME);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器Prime三合一", "成功", result.getHoverName().getString()); }
                return;
            }

            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) return;

            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);
            if (allSameTier) {
                ItemStack result = getRandomWeaponModuleByTier(tier0);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器模组三合一", "同品质", result.getHoverName().getString()); }
            } else {
                if (!ModConfig.KUVA_LICH.enableCrossTierRecast.get()) return;
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWeaponModuleByTier(selectedTier);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString()); }
            }
        } catch (Exception e) { LogUtil.error("武器模组三合一失败", e); }
    }

    private int getWarframeModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof WarframeRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof WarframePrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof WarframeRareModule) return TIER_RARE;
        if (stack.getItem() instanceof WarframeUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof WarframeCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

    private ItemStack getRandomWarframeModuleByTier(int tier) {
        switch (tier) {
            case TIER_COMMON: return WarframeCommonModule.getRandomModule();
            case TIER_UNCOMMON: return WarframeUncommonModule.getRandomModule();
            case TIER_RARE: return WarframeRareModule.getRandomModule();
            case TIER_PRIME: return WarframePrimeModule.getRandomModule();
            case TIER_RIVEN: return WarframeRivenModule.getRandomModule();
            default: return null;
        }
    }

    private void processWarframeModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWarframeModuleTier(slot0);
            int tier1 = getWarframeModuleTier(slot1);
            int tier2 = getWarframeModuleTier(slot2);
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) return;

            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast.get()) return;
                ItemStack result = getRandomWarframeModuleByTier(TIER_RIVEN);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲裂罅三合一", "成功", result.getHoverName().getString()); }
                return;
            }
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast.get()) return;
                ItemStack result = getRandomWarframeModuleByTier(TIER_PRIME);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲Prime三合一", "成功", result.getHoverName().getString()); }
                return;
            }

            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) return;

            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);
            if (allSameTier) {
                ItemStack result = getRandomWarframeModuleByTier(tier0);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲模组三合一", "同品质", result.getHoverName().getString()); }
            } else {
                if (!ModConfig.KUVA_LICH.enableWarframeCrossTierRecast.get()) return;
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWarframeModuleByTier(selectedTier);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString()); }
            }
        } catch (Exception e) { LogUtil.error("战甲模组三合一失败", e); }
    }

    private boolean isBaseTier(int tier) {
        return tier == TIER_COMMON || tier == TIER_UNCOMMON || tier == TIER_RARE;
    }

    private int selectTierByRatio(int tier0, int tier1, int tier2) {
        Map<Integer, Integer> tierCounts = new HashMap<>();
        tierCounts.merge(tier0, 1, Integer::sum);
        tierCounts.merge(tier1, 1, Integer::sum);
        tierCounts.merge(tier2, 1, Integer::sum);
        double random = level.random.nextDouble() * 3.0;
        double cumulative = 0.0;
        for (Map.Entry<Integer, Integer> entry : tierCounts.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) return entry.getKey();
        }
        return tier0;
    }

    private void consumeAndOutput(ItemStack result) {
        cardHandler.setStackInSlot(3, result);
        cardHandler.setStackInSlot(0, ItemStack.EMPTY);
        cardHandler.setStackInSlot(1, ItemStack.EMPTY);
        cardHandler.setStackInSlot(2, ItemStack.EMPTY);
        broadcastFullState();
    }

    public void broadcastFullState() {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    /**
     * ⭐ 关闭时返还物品：使用 Inventory.add() 优先快捷栏
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            for (int i = 0; i < 4; i++) {
                ItemStack stack = cardHandler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                    cardHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    public static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (stack.getItem() instanceof AbstractRequiemCard) return super.mayPlace(stack);
            if (stack.getItem() instanceof AbstractItemModule) {
                if (stack.getItem() instanceof ItemRivenModule) return ModConfig.KUVA_LICH.enableRivenModuleRecast.get() && super.mayPlace(stack);
                if (stack.getItem() instanceof ItemPrimeModule) return ModConfig.KUVA_LICH.enablePrimeModuleRecast.get() && super.mayPlace(stack);
                return super.mayPlace(stack);
            }
            if (stack.getItem() instanceof AbstractWarframeModule) {
                if (!ModConfig.KUVA_LICH.enableWarframeModuleRecast.get()) return false;
                if (stack.getItem() instanceof WarframeRivenModule) return ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast.get() && super.mayPlace(stack);
                if (stack.getItem() instanceof WarframePrimeModule) return ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast.get() && super.mayPlace(stack);
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    public static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) { return false; }
    }
}
