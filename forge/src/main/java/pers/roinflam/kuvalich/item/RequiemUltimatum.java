package pers.roinflam.kuvalich.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.init.KuvaLichEntities;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 安魂通牒（1.20.1版本，业务逻辑100%不变）
 * Requiem Ultimatum (1.20.1 version, business logic 100% unchanged)
 *
 * 使用后可召唤赤毒玄骸
 * Use to summon Kuva Lich
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
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 右键空气使用（1.20.1新API）
     * Use on air (1.20.1 new API)
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // 在玩家面前生成赤毒玄骸 / Spawn Kuva Lich in front of player
            BlockPos spawnPos = player.blockPosition().relative(player.getDirection(), 3);

            EntityKuvaMaster kuvaMaster = new EntityKuvaMaster(KuvaLichEntities.KUVA_MASTER.get(), level);
            kuvaMaster.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            level.addFreshEntity(kuvaMaster);

            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
        }

        return InteractionResultHolder.success(itemstack);
    }

    /**
     * 右键方块使用（1.20.1新API）
     * Use on block (1.20.1 new API)
     */
    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        ItemStack itemstack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getClickedFace();

        // 在点击的方块上方生成赤毒玄骸 / Spawn Kuva Lich above clicked block
        BlockPos spawnPos = pos.relative(facing);

        EntityKuvaMaster kuvaMaster = new EntityKuvaMaster(KuvaLichEntities.KUVA_MASTER.get(), level);
        kuvaMaster.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        level.addFreshEntity(kuvaMaster);

        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}