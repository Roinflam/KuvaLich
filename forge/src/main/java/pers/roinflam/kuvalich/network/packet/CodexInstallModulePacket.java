package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.event.ModuleDiscoveryHandler;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;
import pers.roinflam.kuvalich.world.inventory.RequiemWeaponTableMenu;

import java.util.function.Supplier;

/**
 * 图鉴创造模式一键装配包（客户端 → 服务器）
 *
 * <p>创造模式玩家在图鉴里 Shift+点击一个模组时，请求服务端把它装进
 * <b>当前打开的军械库</b>的第一个可用空槽。没有可用空槽、或与已装的模组冲突，
 * 就什么也不做。</p>
 *
 * <h3>为什么目标是军械库而不是主手</h3>
 * <p>图鉴只能从军械库界面进去（{@code RequiemWeaponTableScreen} /
 * {@code RequiemWarframeTableScreen} 里各有一个入口按钮，
 * {@code new ModuleCodexScreen(this, ...)} 把军械库界面作为 parent 传进去）。
 * 玩家此刻的操作语境就是「给台面上这把武器插卡」，而不是给手上拿的东西插卡。</p>
 *
 * <p>客户端切到图鉴 Screen 时并不会关掉容器：{@code Minecraft.setScreen} 走的是
 * {@code removed()} 而不是 {@code onClose()}，只有后者才会发关闭容器的包。
 * 所以服务端的 {@code player.containerMenu} 仍然是那个军械库菜单，
 * 这正是本包定位目标的依据。</p>
 *
 * <h3>服务端校验</h3>
 * <ol>
 *   <li><b>创造模式</b>由服务端判定。</li>
 *   <li><b>物品实例</b>只从服务端自己的注册列表里按 type 查（复用
 *       {@code CodexGiveItemPacket.findModule}），绝不根据客户端传来的字符串或 NBT
 *       构造物品。这条是整套安全模型的底线。</li>
 *   <li><b>目标容器</b>由服务端当前打开的菜单决定，<b>不看</b>客户端传来的
 *       {@code isWeapon}。那个布尔只用于在服务端列表里查物品，
 *       伪造它最多只能让查找失败，不可能把战甲模组塞进武器。</li>
 *   <li><b>空槽与冲突</b>走 {@code ModulePlacementValidator}，与两个军械库菜单
 *       自己的 {@code mayPlace} 是同一份实现。</li>
 *   <li><b>类型与容器匹配</b>再校验一次 {@code instanceof}。</li>
 * </ol>
 *
 * <p>写入与刷新都交给菜单自己的 {@code installFirstAvailable}：它走的是菜单既有的
 * 回写路径（武器 {@code syncAllModulesToWeapon}、战甲 {@code saveModulesToCapability}），
 * 与玩家手动拖卡进槽位完全同一条路。这样既不用再写一份 NBT 序列化，
 * 也不会漏掉那两条路径里已有的防御（比如非武器模组会被写成空槽、
 * 非法物品会被返还给玩家）。</p>
 *
 * @author RoinFlam
 */
public class CodexInstallModulePacket {

    /** 目标模组的 type 标识 */
    private final String moduleType;
    /** 图鉴当前在看武器页还是战甲页；只用于在服务端列表里查物品，不决定往哪个容器塞 */
    private final boolean isWeapon;
    /** 模组品质序号（0=青铜 1=白银 2=黄金 3=Prime） */
    private final int rarityOrder;

    public CodexInstallModulePacket(String moduleType, boolean isWeapon, int rarityOrder) {
        this.moduleType = moduleType;
        this.isWeapon = isWeapon;
        this.rarityOrder = rarityOrder;
    }

    // ==================== 编解码 ====================

    public static void encode(CodexInstallModulePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.moduleType);
        buf.writeBoolean(msg.isWeapon);
        buf.writeInt(msg.rarityOrder);
    }

    public static CodexInstallModulePacket decode(FriendlyByteBuf buf) {
        return new CodexInstallModulePacket(buf.readUtf(256), buf.readBoolean(), buf.readInt());
    }

    // ==================== 服务端处理 ====================

    public static void handle(CodexInstallModulePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!player.isCreative()) {
                LogUtil.debug("非创造模式玩家 " + player.getName().getString() + " 请求一键装配，已拒绝");
                return;
            }
            if (msg.rarityOrder < 0 || msg.rarityOrder > 3) {
                LogUtil.debug("一键装配收到非法品质序号 " + msg.rarityOrder + "，已丢弃");
                return;
            }

            // 目标容器由服务端当前打开的菜单决定
            AbstractContainerMenu menu = player.containerMenu;
            if (!(menu instanceof RequiemWeaponTableMenu) && !(menu instanceof RequiemWarframeTableMenu)) {
                LogUtil.debug("一键装配时玩家并未打开军械库，已拒绝");
                return;
            }

            // 权威实例只从服务端列表里取
            ItemStack found = CodexGiveItemPacket.findModule(msg.moduleType, msg.isWeapon, msg.rarityOrder);
            if (found.isEmpty()) {
                LogUtil.debug("一键装配找不到模组 type=" + msg.moduleType
                        + " isWeapon=" + msg.isWeapon + " rarity=" + msg.rarityOrder);
                return;
            }
            ItemStack module = found.copy();

            boolean installed = false;
            if (menu instanceof RequiemWeaponTableMenu weaponMenu) {
                // 类型与容器必须匹配：武器军械库只收武器模组
                if (!(module.getItem() instanceof AbstractWeaponModule)) {
                    LogUtil.debug("一键装配：武器军械库收到非武器模组，已拒绝");
                    return;
                }
                installed = weaponMenu.installFirstAvailable(module);
            } else if (menu instanceof RequiemWarframeTableMenu warframeMenu) {
                if (!(module.getItem() instanceof AbstractWarframeModule)) {
                    LogUtil.debug("一键装配：战甲军械库收到非战甲模组，已拒绝");
                    return;
                }
                installed = warframeMenu.installFirstAvailable(player, module);
            }

            if (installed) {
                // 一键装配直接进槽位、不经过背包，不会触发原有的「拾取时发现」路径，
                // 漏了的话图鉴会少标一个「已发现」
                ModuleDiscoveryHandler.tryDiscoverSingle(player, module);
                LogUtil.debugEvent("图鉴一键装配", player.getName().getString(),
                        (msg.isWeapon ? "武器" : "战甲") + "模组 " + msg.moduleType);
            }
        });
    }
}
