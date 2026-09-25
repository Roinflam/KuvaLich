package pers.roinflam.kuvalich.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 赤毒玄骸 冒险指南
 *
 * <p>右键打开自带的指南界面（{@code client/gui/guide/GuideScreen}）。首次进服由
 * {@link pers.roinflam.kuvalich.event.BookGiveHandler} 发放一本。</p>
 *
 * <p>界面类只在客户端存在，这里经 {@link DistExecutor} 间接引用，
 * 专用服务端加载本类时不会去解析客户端类。</p>
 *
 * @author RoinFlam
 */
public class GuideBook extends Item {

    public GuideBook(@Nonnull Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Nonnull
    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> pers.roinflam.kuvalich.client.gui.guide.GuideScreenOpener::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level,
                                @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
