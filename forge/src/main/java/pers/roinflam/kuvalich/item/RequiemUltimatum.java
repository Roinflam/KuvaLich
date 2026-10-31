package pers.roinflam.kuvalich.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 安魂通牒：召来自己的玄骸，不必等它攒满怒气
 *
 * <p>只对用的人自己那只有效：还没有玄骸、它已经在来的路上或就在附近、或者它到不了这里（这个维度它不来、
 * 和平难度、正在处决）时都不会用掉，动作栏说明原因。召的过程交给玄骸导演：先发一句通讯，
 * 过一小会儿在面前现身（对着方块用时优先落在点击面外侧那一格），带召唤的演出、显形期间不出手也不挨打。
 * 用了以后怒气直接填满、无视冷却。创造模式也能用（狩猎平时不找创造模式的玩家，通牒例外），不消耗。
 * 用完 2 秒冷却，防连点。</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class RequiemUltimatum extends Item {

    public RequiemUltimatum(@Nonnull Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        if (item instanceof RequiemUltimatum) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(1, Component.translatable(item.getDescriptionId() + ".tooltip")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(KuvaPalette.MUTED)).withItalic(true)));
        }
    }

    /** 用完以后的冷却（tick），防连点 */
    private static final int USE_COOLDOWN = 40;

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(itemstack);
        }
        if (player instanceof ServerPlayer serverPlayer && tryCall(serverPlayer, itemstack, null)) {
            return InteractionResultHolder.consume(itemstack);
        }
        return InteractionResultHolder.fail(itemstack);
    }

    /** 对着方块用：现身时优先落在点击面外侧那一格 */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        BlockPos feet = context.getClickedPos().relative(context.getClickedFace());
        return tryCall(player, context.getItemInHand(), feet) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * 召一次：条件不满足时动作栏说明原因、不消耗，返回 false
     *
     * @param preferredFeet 面前落点优先试的那一格，可以为空
     */
    public static boolean tryCall(ServerPlayer player, ItemStack stack, @Nullable BlockPos preferredFeet) {
        RequiemCard card = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (card == null || !card.hasLich()) {
            PlayerFeedback.actionBar(player, KuvaPalette.MUTED, "message.kuvalich.ultimatum.no_lich");
            return false;
        }
        LichState state = LichDirector.stateOf(player);
        if (state == LichState.ARRIVING || state == LichState.PRESENT || state == LichState.DOWNED
                || state == LichState.DEPARTING) {
            PlayerFeedback.actionBar(player, KuvaPalette.LICH, "message.kuvalich.ultimatum.already", card.getLichName());
            return false;
        }
        if (!canReach(player)) {
            PlayerFeedback.actionBar(player, KuvaPalette.DANGER, "message.kuvalich.ultimatum.blocked");
            return false;
        }

        float rageBefore = card.getLichRage();
        card.setLichRage(1.0F);
        if (!LichDirector.summonNow(player, SummonMode.ULTIMATUM, preferredFeet)) {
            card.setLichRage(rageBefore);
            PlayerFeedback.actionBar(player, KuvaPalette.LICH, "message.kuvalich.ultimatum.already", card.getLichName());
            return false;
        }
        // 先记下物品：最后一张用掉后 stack 变空，getItem() 就成了空气
        Item item = stack.getItem();
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(item, USE_COOLDOWN);
        PlayerFeedback.actionBar(player, KuvaPalette.LICH, "message.kuvalich.ultimatum.called", card.getLichName());
        return true;
    }

    /** 它能不能来：这个维度它来、不是和平难度、玩家没在处决。创造模式照样能召 */
    private static boolean canReach(ServerPlayer player) {
        String dim = player.level().dimension().location().toString();
        if (!ModConfig.KUVA_LICH.lichHuntDimensions.get().contains(dim)) {
            return false;
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        return !MercyServer.isBusy(player);
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}