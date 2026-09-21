package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.module.weapon.panel.KillStackKeys;
import pers.roinflam.kuvalich.module.weapon.panel.StackCounts;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * 客户端的叠层数据入口 —— tooltip / HUD 一律走这里，不要直接读两套镜像
 *
 * <p><b>为什么必须有这一层</b>：客户端的叠层数据存在**两套互不知情的镜像**——
 * 武器 8 类在 {@code KillStackManager.CLIENT_WEAPON_STACKS}，
 * 战甲 12 类在 {@code WarframeModuleHandler.clientSyncedKillStacks}。
 * 改造前 {@code KillStackManager.getStacks} 的客户端分支只遍历 {@code WEAPON_STACK_TYPES}，
 * 传战甲类型进去会**静默返回 0 且不报错**——这种 bug 现象是「数字不对」但没有任何异常线索，
 * 很容易先去怀疑同步包、怀疑 NBT、绕一大圈才想到是读取入口选错了。
 * （该 fallback 已在 {@code KillStackManager} 里一并修掉，这里是第二道保险。）</p>
 *
 * <p><b>衰减倒计时</b>：{@code ticksUntilDecay} 目前既没有 getter 也没进同步包，
 * 所以这里用「层数变化即重置」的本地推算。推算在唯一一种场景下会偏离服务端：
 * <b>满层时继续击杀</b>（{@code addStack} 在满层时不增加层数但仍重置计时器，
 * 层数 hash 不变 → 不发包 → 客户端会一路数到 0）。
 * 因此满层时**不显示秒数、只显示「满层」**——这样本地推算永远不会显示出一个错误的数字。
 * 真正的倒计时字段留到协议提版时再加。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class LiveStackView implements StackCounts {

    // ==================== 本地衰减推算 ====================

    /** 上次看到的层数 */
    private static final Map<StackType, Integer> LAST_SEEN = new EnumMap<>(StackType.class);
    /** 层数最后一次变化时的 gameTime；尚未观测到变化时不写入 */
    private static final Map<StackType, Long> CHANGED_AT = new EnumMap<>(StackType.class);

    /**
     * 刷新本地推算的基准
     *
     * <p>⭐ 重要：本方法只在构建 tooltip 时被调到，所以它观测到的不是
     * 「层数真正变化的时刻」，而是「你把鼠标移上去的时刻」。
     * <b>因此第一次看到某一种叠层时不建立基准</b> ——
     * 否则叠层已经涨到 5 层、过了 4 秒才去悬停，面板会编出一个
     * 「还有 5.0 秒」，而服务端 1 秒后就掉层了。
     * 没基准时 {@link #decayTicks} 返回 -1，渲染层不显示秒数 ——
     * 与本类「本地推算永远不显示一个错误的数字」的自述一致。</p>
     */
    private static void refreshClock(StackType type, int current, long gameTime) {
        Integer last = LAST_SEEN.get(type);
        if (last == null) {
            // 首次观测：只记层数，不记时间戳
            LAST_SEEN.put(type, current);
            return;
        }
        if (last != current) {
            LAST_SEEN.put(type, current);
            CHANGED_AT.put(type, gameTime);
        }
    }

    /** 切换世界 / 断线时清空推算基准，避免把上一个存档的计时带过来 */
    public static void resetClock() {
        LAST_SEEN.clear();
        CHANGED_AT.clear();
    }

    // ==================== 实例 ====================

    private final Player player;
    private final long gameTime;
    private final boolean available;

    private LiveStackView(Player player, long gameTime, boolean available) {
        this.player = player;
        this.gameTime = gameTime;
        this.available = available;
    }

    /**
     * 为「正在看 tooltip 的玩家」建一个叠层视图
     *
     * <p>⭐ 叠层是**玩家级状态**，不是物品级状态，所以不做主手判定
     * （{@code ExtraSlotTooltipHelper.isMainHandWeapon} 在主副手物品完全相同时会直接放弃，
     * 那套逻辑套到叠层上会让双持同款武器时叠层莫名消失）。
     * 但必须是本地玩家：JEI 配方页 / 创造栏里 {@code entity} 可能为 null 或不是本地玩家，
     * 那时返回一个 {@code available=false} 的视图，整个叠层区块不会生成。</p>
     */
    public static StackCounts of(@Nullable Player entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc.player == null || entity != mc.player || mc.level == null) {
            return StackCounts.NONE;
        }
        return new LiveStackView(entity, mc.level.getGameTime(), true);
    }

    @Override
    public int stacks(StackType type) {
        // ⭐ 统一入口：KillStackManager 的客户端分支已修为「武器类读自己的镜像，
        //    其余转发给 WarframeModuleHandler」，所以这里一次调用覆盖全部 20 种类型。
        int n = KillStackManager.getStacks(player, type);
        refreshClock(type, n, gameTime);
        return n;
    }

    @Override
    public int maxStacks(StackType type) {
        // ⚠️ 已知局限：ModConfig 注册为 COMMON，Forge 只同步 Type.SERVER，
        //    所以联机时这个分母读的是**客户端自己的 toml**。服主改过上限而玩家没改时，
        //    可能出现分子大于分母的情况；渲染层会对此做钳制。
        //    根治要把 maxStacks 随登录包下发（协议提版时一并做）。
        return type.getMaxStacks();
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public int decayTicks(StackType type) {
        Long changedAt = CHANGED_AT.get(type);
        if (changedAt == null) {
            return -1;
        }
        int total = type.getDecayTicks();
        long elapsed = gameTime - changedAt;
        int remaining = (int) (total - elapsed);
        return remaining > 0 ? remaining : -1;
    }

    /**
     * 剩余秒数（保留一位小数）；未知返回 -1
     *
     * <p>服务端 TPS 掉到 10 时客户端仍按 20tps 数，会跑得偏快，
     * 所以渲染层在 &lt; 1s 时统一显示「即将衰减」而不是一个抖动的具体数字。</p>
     */
    public static double decaySeconds(StackCounts counts, StackType type) {
        int ticks = counts.decayTicks(type);
        return ticks < 0 ? -1 : ticks / 20.0;
    }

    /**
     * 模组卡上叠层词条的「当前层数」后缀
     *
     * <p>卡面原先只写「至多 N 层」，玩家看不出这条词条此刻生效了多少 ——
     * 一张「每层 +5%、至多 20 层」的卡，满层是 +100%，但面板上永远只有那个 5%，
     * 会让玩家系统性低估这类词条的强度。</p>
     *
     * <p>⭐ 叠层是**玩家级**状态而非物品级：在背包里看一张没装备的卡，
     * 显示的是「你当前的层数」，所以文案要用「当前 3/5」这种明确措辞，
     * 不能只画一个进度条让人误以为层数属于这张卡。</p>
     *
     * @param attributeKey 属性 key
     * @param viewer       查看者
     * @return 后缀组件；不是叠层词条、或拿不到本地玩家数据时返回 null
     */
    @Nullable
    public static Component liveStackSuffix(String attributeKey, @Nullable Player viewer) {
        StackType type = KillStackKeys.typeOf(attributeKey);
        if (type == null) {
            return null;
        }
        StackCounts counts = of(viewer);
        if (!counts.available()) {
            return null;
        }
        int cur = counts.stacks(type);
        int max = Math.max(1, counts.maxStacks(type));
        return Component.literal(" ")
                .append(Component.translatable("kuvalich.panel.stack.current",
                                Math.min(cur, max), max)
                        .withStyle(PanelPalette.style(
                                cur >= max ? PanelPalette.STACK_ACTIVE : PanelPalette.MUTED)));
    }
}
