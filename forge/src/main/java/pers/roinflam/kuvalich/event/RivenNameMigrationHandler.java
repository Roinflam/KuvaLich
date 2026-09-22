package pers.roinflam.kuvalich.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 把老裂罅卡的卡名从「§ 颜色码内嵌」迁移成组件级样式
 *
 * <h3>为什么会有两种格式</h3>
 * <p>裂罅卡的名字原先是这么生成的：{@code Component.literal(ChatFormatting.DARK_PURPLE + name)} ——
 * 颜色是个 {@code §5} 控制符，内嵌在纯文本里，{@code Style} 上并没有颜色。
 * 后来改成了组件级样式（{@code literal(name).withStyle(...withColor(...))}）。</p>
 *
 * <p>但 {@code setHoverName} 是把整段 Component 序列化成 JSON 写进物品 NBT 的
 * {@code tag.display.Name}，是<b>生成瞬间烘焙的快照</b> —— 已经在玩家背包、箱子、
 * 展示框里的老卡永远是旧格式，代码改动不会也无法回溯改写它们。</p>
 *
 * <h3>这个格式差异会造成什么</h3>
 * <p>两种格式渲染出来都是紫色，肉眼看不出区别，所以这不是显示问题。真正的影响在
 * {@code WeaponPanelComposer.moduleNames()}：那里有一句「如果卡名自身没带颜色，
 * 就补一个默认灰」。老卡的颜色藏在文本里、{@code getStyle().getColor()} 永远是 null，
 * 于是这句判断永远走错分支 —— 只是渲染时字符串里的 {@code §5} 又把灰盖了回去，
 * 误判但结果凑巧对。新卡则能被正确识别。</p>
 *
 * <p>迁移之后两种卡走同一条分支，这类「凑巧对」的脆弱逻辑就消失了。</p>
 *
 * <h3>为什么放在周期扫描里而不是渲染时</h3>
 * <p>渲染时改写意味着<b>每一帧都去动玩家的物品 NBT</b>，而且客户端改了也不会同步回服务端。
 * 这里放在服务端的周期性扫描上：改的是权威数据、只改一次（改完就不再匹配条件），
 * 通过正常的容器同步下发给客户端。</p>
 *
 * <p>扫描间隔刻意比模组发现那条（100 tick）慢得多：这是一次性的存量数据清理，
 * 不是需要及时响应的功能，没必要和发现扫描抢同一个节拍。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class RivenNameMigrationHandler {

    /** 扫描间隔（tick）。20 秒一次，存量清理不需要更快。 */
    private static final int SCAN_INTERVAL = 400;

    /** 传统颜色码的前导字符 */
    private static final char LEGACY_PREFIX = ChatFormatting.PREFIX_CODE;

    /**
     * 周期性扫描玩家背包，把旧格式的裂罅卡名改写成组件级样式。
     *
     * @param event 玩家 tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        // 错开模组发现那条扫描的节拍，别让两次全背包遍历撞在同一 tick
        if ((player.tickCount + 37) % SCAN_INTERVAL != 0) return;

        Inventory inv = player.getInventory();
        int migrated = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (migrate(inv.getItem(i))) {
                migrated++;
            }
        }
        if (migrated > 0) {
            LogUtil.debugEvent("裂罅卡名迁移", player.getName().getString(),
                    "本次改写 " + migrated + " 张");
        }
    }

    /**
     * 迁移一张卡；不是旧格式的裂罅卡就原样不动。
     *
     * @param stack 物品
     * @return 是否真的改写了
     */
    private static boolean migrate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof WeaponRivenModule)
                && !(stack.getItem() instanceof WarframeRivenModule)) {
            return false;
        }
        // 没有自定义名字的（比如未鉴定的随机卡）不碰
        if (!stack.hasCustomHoverName()) return false;

        String raw = stack.getHoverName().getString();
        if (raw.isEmpty() || raw.charAt(0) != LEGACY_PREFIX) return false;

        String stripped = stripLegacyCodes(raw);
        if (stripped.isEmpty()) return false;

        stack.setHoverName(Component.literal(stripped)
                .withStyle(KuvaPalette.style(KuvaPalette.rarity(4))));
        return true;
    }

    /**
     * 去掉字符串里所有的 {@code §x} 传统颜色码
     *
     * <p>生成时只会在最前面加一个 {@code §5}，但这里按通用情况处理 ——
     * 万一某张卡的名字在别处被再加工过，留下半截控制符会比没处理更难看。</p>
     *
     * @param raw 原始字符串
     * @return 去掉控制符后的纯文本
     */
    private static String stripLegacyCodes(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == LEGACY_PREFIX && i + 1 < raw.length()) {
                i++;   // 跳过控制符本身和它后面那个格式字符
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
