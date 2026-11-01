package pers.roinflam.kuvalich.client.lich;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.WaitReason;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 档案的客户端调试命令，只在开发环境注册（{@link FMLEnvironment#production} 为真时不注册）
 *
 * <ul>
 *   <li>{@code /kuvafxc archive demo [full|empty|满|空] [状态]}：本地塞一份假档案打开界面（截图用，不碰服务端）。
 *       满的那份什么都有（两条已揭示的密语、三张卡、五条处决记录、二十多件被拿走的东西）；
 *       空的那份是还没有玄骸、上一只已被终结。状态写 {@code LichState} 的名字（如 {@code incoming}），只对满的那份有用。</li>
 *   <li>{@code /kuvafxc archive open}：照常打开（等于按一下档案键）。</li>
 *   <li>{@code /kuvafxc archive demoweapon <下标>}：满的那份假档案换成第几把赤毒武器（看不同武器拿在手里的样子）。</li>
 *   <li>{@code /kuvafxc archive weapon [ox oy oz rx ry rz scale]}：现场改玄骸手里武器的挂点，不带参数时打印当前值。</li>
 *   <li>{@code /kuvafxc archive other on|off}：名牌和 Boss 条一律按「别人的玄骸」画（只有一个客户端时看第二行用）。</li>
 * </ul>
 *
 * <p>命令在聊天框里执行，执行完聊天框会关掉、顺手把刚开的界面也关了，所以界面挪到下一个客户端 tick 再开。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ArchiveDevCommands {

    /** 名牌、Boss 条按「别人在看」画 */
    static boolean forceOtherView;

    /** 满的那份假档案用第几把赤毒武器 */
    private static int demoWeapon;

    private static LichArchivePacket.Data pending;
    private static boolean pendingOpen;

    private ArchiveDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> demo = Commands.literal("demo")
                .executes(ctx -> demo(ctx.getSource(), true, null));
        for (String full : new String[]{"full", "满"}) {
            demo.then(Commands.literal(full)
                    .executes(ctx -> demo(ctx.getSource(), true, null))
                    .then(Commands.argument("state", StringArgumentType.word())
                            .executes(ctx -> demo(ctx.getSource(), true, StringArgumentType.getString(ctx, "state")))));
        }
        for (String empty : new String[]{"empty", "空"}) {
            demo.then(Commands.literal(empty).executes(ctx -> demo(ctx.getSource(), false, null)));
        }
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("archive")
                        .then(demo)
                        .then(Commands.literal("open").executes(ctx -> {
                            pending = null;
                            pendingOpen = true;
                            return 1;
                        }))
                        .then(Commands.literal("demoweapon")
                                .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
                                    demoWeapon = IntegerArgumentType.getInteger(ctx, "index");
                                    int n = KuvaLichItems.KUVA_WEAPONS.size();
                                    ctx.getSource().sendSuccess(() -> Component.literal("demo weapon "
                                            + (n == 0 ? "-" : Integer.toString(demoWeapon % n)) + " / " + n), false);
                                    return 1;
                                })))
                        .then(Commands.literal("other")
                                .then(Commands.literal("on").executes(ctx -> other(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> other(ctx.getSource(), false))))
                        .then(weaponCommand("weapon", false))
                        .then(weaponCommand("weaponportrait", true))));
    }

    /** 调玄骸手里的刀：weapon 调世界里的，weaponportrait 调肖像和档案里的 */
    private static LiteralArgumentBuilder<CommandSourceStack> weaponCommand(String literal, boolean portrait) {
        String[] names = {"ox", "oy", "oz", "rx", "ry", "rz", "scale"};
        ArgumentBuilder<CommandSourceStack, ?> tail =
                Commands.argument(names[6], FloatArgumentType.floatArg(0.1F, 5.0F)).executes(ctx -> {
                    float[] v = new float[names.length];
                    for (int i = 0; i < names.length; i++) {
                        v[i] = FloatArgumentType.getFloat(ctx, names[i]);
                    }
                    LichWeaponLayer.tune(portrait, v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
                    ctx.getSource().sendSuccess(() -> Component.literal(LichWeaponLayer.describe(portrait)), false);
                    return 1;
                });
        for (int i = names.length - 2; i >= 0; i--) {
            tail = Commands.argument(names[i], FloatArgumentType.floatArg()).then(tail);
        }
        return Commands.literal(literal)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(LichWeaponLayer.describe(portrait)), false);
                    return 1;
                })
                .then(tail);
    }

    private static int other(CommandSourceStack src, boolean on) {
        forceOtherView = on;
        src.sendSuccess(() -> Component.literal("archive other view " + (on ? "on" : "off")), false);
        return 1;
    }

    private static int demo(CommandSourceStack src, boolean full, String stateName) {
        LichState state = LichState.STALKING;
        if (stateName != null) {
            try {
                state = LichState.valueOf(stateName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("unknown state " + stateName));
                return 0;
            }
        }
        pending = full ? fullDemo(state) : emptyDemo();
        pendingOpen = true;
        return 1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingOpen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        pendingOpen = false;
        mc.setScreen(new LichArchiveScreen(pending));
        pending = null;
    }

    // ==================== 假数据 ====================

    private static LichArchivePacket.Data fullDemo(LichState state) {
        int weapon = KuvaLichItems.KUVA_WEAPONS.isEmpty() ? -1 : demoWeapon % KuvaLichItems.KUVA_WEAPONS.size();
        int cooldown = state == LichState.COOLDOWN ? 7 * 60 + 20 : 0;
        WaitReason wait = WaitReason.NONE;
        LichStatePacket st = new LichStatePacket(state, wait, "Drokag Tuzz", 0x5EED1234, weapon, 7, 620, 2, 2,
                cooldown, 3, false);
        ItemStack weaponStack = weapon >= 0 ? new ItemStack(KuvaLichItems.KUVA_WEAPONS.get(weapon)) : ItemStack.EMPTY;
        int[] attempts = {
                RequiemCard.packAttempt(new int[]{3, 6, 1}, new byte[]{1, 2, 0}, true),
                RequiemCard.packAttempt(new int[]{3, 0, 5}, new byte[]{1, 1, 2}, true),
                RequiemCard.packAttempt(new int[]{2, 6, 1}, new byte[]{2, 0, 0}, false),
                RequiemCard.packAttempt(new int[]{7, 4, 1}, new byte[]{2, 0, 0}, true),
                RequiemCard.packAttempt(new int[]{-1, 4, 0}, new byte[]{2, 0, 0}, false),
        };
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] pool = {
                new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLDEN_APPLE, 2),
                new ItemStack(Items.BREAD, 12), new ItemStack(Items.EMERALD, 7), new ItemStack(Items.BOW),
                new ItemStack(Items.ENDER_PEARL, 4), new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.SHIELD),
                new ItemStack(Items.TORCH, 32), new ItemStack(Items.COOKED_BEEF, 9), new ItemStack(Items.REDSTONE, 20),
                new ItemStack(Items.ARROW, 48), new ItemStack(Items.LAPIS_LAZULI, 11), new ItemStack(Items.GOLD_INGOT, 5),
                new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.OAK_LOG, 30), new ItemStack(Items.COAL, 25),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 6), new ItemStack(Items.NAME_TAG), new ItemStack(Items.SADDLE),
                new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ELYTRA)
        };
        for (ItemStack s : pool) {
            taken.add(s);
        }
        return new LichArchivePacket.Data(st, "minecraft:dark_forest", "minecraft:overworld", weaponStack, 3, 7,
                0xC2, 5, 30, 75, 21, 35, 15, 4,
                2, 7, 11, new int[]{3, -1, 6},
                new LichArchivePacket.Card[]{
                        new LichArchivePacket.Card(3, 1, 3), new LichArchivePacket.Card(6, 0, 3),
                        new LichArchivePacket.Card(1, 2, 3)},
                attempts, taken.size() + 9, taken, "", 0);
    }

    private static LichArchivePacket.Data emptyDemo() {
        LichStatePacket st = new LichStatePacket(LichState.UNBORN, WaitReason.NONE, "", 0, -1, 0, 0, 0, 0, 0, 2, false);
        return new LichArchivePacket.Data(st, "", "", ItemStack.EMPTY, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 2, 6, new int[]{-1, -1, -1},
                new LichArchivePacket.Card[]{new LichArchivePacket.Card(5, 0, 3), LichArchivePacket.Card.EMPTY,
                        LichArchivePacket.Card.EMPTY},
                new int[0], 0, List.of(), "Varokk Ruhl", 1);
    }
}
