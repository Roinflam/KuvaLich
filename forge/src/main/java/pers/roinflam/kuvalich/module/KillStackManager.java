// KillStackManager.java - 1.20.1版本
package pers.roinflam.kuvalich.module;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击杀叠加效果管理器（1.20.1版本，业务逻辑100%不变）
 * Kill Stack Manager (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class KillStackManager {

    /**
     * 击杀叠层类型枚举
     */
    public enum StackType {
        // 武器叠层（10秒/层）
        BASE_DAMAGE,
        MULTISHOT,
        MELEE_CRIT_MULT,
        TRIGGER_CHANCE,
        ATTACK_RANGE,
        ATTACK_SPEED,
        BURSTING_RADIUS,
        FIRING_RATE,

        // 战甲叠层（20秒/层）
        WARFRAME_HEALTH,
        WARFRAME_SHIELD,
        WARFRAME_ARMOR,
        WARFRAME_SPRINT_SPEED,
        WARFRAME_SHIELD_RECOVERY_RATE,
        WARFRAME_SHIELD_RECOVERY_DELAY,
        WARFRAME_FIRE_PROTECTION,
        WARFRAME_ELECTRIC_PROTECTION,
        WARFRAME_HOMOLOGOUS_PROTECTION,
        WARFRAME_RESPONSE_RATE,
        WARFRAME_ITEM_DROP_MULTIPLIER,
        WARFRAME_DIGGING_SPEED;

        /**
         * 获取该类型的最大层数（从配置文件读取）
         */
        public int getMaxStacks() {
            switch (this) {
                case BASE_DAMAGE:
                    return ModConfig.KUVA_LICH.maxStacksBaseDamage.get();
                case MULTISHOT:
                    return ModConfig.KUVA_LICH.maxStacksMultishot.get();
                case MELEE_CRIT_MULT:
                    return ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get();
                case TRIGGER_CHANCE:
                    return ModConfig.KUVA_LICH.maxStacksTriggerChance.get();
                case ATTACK_RANGE:
                    return ModConfig.KUVA_LICH.maxStacksAttackRange.get();
                case ATTACK_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksAttackSpeed.get();
                case BURSTING_RADIUS:
                    return ModConfig.KUVA_LICH.maxStacksBurstingRadius.get();
                case FIRING_RATE:
                    return ModConfig.KUVA_LICH.maxStacksFiringRate.get();

                case WARFRAME_HEALTH:
                    return ModConfig.KUVA_LICH.maxStacksHealth.get();
                case WARFRAME_SHIELD:
                    return ModConfig.KUVA_LICH.maxStacksShield.get();
                case WARFRAME_ARMOR:
                    return ModConfig.KUVA_LICH.maxStacksArmor.get();
                case WARFRAME_SPRINT_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksSprintSpeed.get();
                case WARFRAME_SHIELD_RECOVERY_RATE:
                    return ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate.get();
                case WARFRAME_SHIELD_RECOVERY_DELAY:
                    return ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay.get();
                case WARFRAME_FIRE_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksFireProtection.get();
                case WARFRAME_ELECTRIC_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksElectricProtection.get();
                case WARFRAME_HOMOLOGOUS_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksHomologousProtection.get();
                case WARFRAME_RESPONSE_RATE:
                    return ModConfig.KUVA_LICH.maxStacksResponseRate.get();
                case WARFRAME_ITEM_DROP_MULTIPLIER:
                    return ModConfig.KUVA_LICH.maxStacksItemDropMultiplier.get();
                case WARFRAME_DIGGING_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksDiggingSpeed.get();

                default:
                    return 5;
            }
        }

        /**
         * 获取该类型的衰减时间（从配置文件读取）
         */
        public int getDecayTicks() {
            switch (this) {
                case BASE_DAMAGE:
                case MULTISHOT:
                case MELEE_CRIT_MULT:
                case TRIGGER_CHANCE:
                case ATTACK_RANGE:
                case ATTACK_SPEED:
                case BURSTING_RADIUS:
                case FIRING_RATE:
                    return ModConfig.KUVA_LICH.weaponStackDecayTicks.get();

                case WARFRAME_HEALTH:
                case WARFRAME_SHIELD:
                case WARFRAME_ARMOR:
                case WARFRAME_SPRINT_SPEED:
                case WARFRAME_SHIELD_RECOVERY_RATE:
                case WARFRAME_SHIELD_RECOVERY_DELAY:
                case WARFRAME_FIRE_PROTECTION:
                case WARFRAME_ELECTRIC_PROTECTION:
                case WARFRAME_HOMOLOGOUS_PROTECTION:
                case WARFRAME_RESPONSE_RATE:
                case WARFRAME_ITEM_DROP_MULTIPLIER:
                case WARFRAME_DIGGING_SPEED:
                    return ModConfig.KUVA_LICH.warframeStackDecayTicks.get();

                default:
                    return 200;
            }
        }
    }

    /**
     * 单个叠层数据
     */
    private static class StackData {
        int stacks;
        int ticksUntilDecay;
        final StackType stackType;

        StackData(StackType stackType) {
            this.stacks = 0;
            this.ticksUntilDecay = stackType.getDecayTicks();
            this.stackType = stackType;
        }

        void addStack() {
            if (stacks < stackType.getMaxStacks()) {
                stacks++;
            }
            ticksUntilDecay = stackType.getDecayTicks();
        }

        boolean tick() {
            if (stacks <= 0) {
                return true;
            }

            ticksUntilDecay--;
            if (ticksUntilDecay <= 0) {
                stacks--;
                ticksUntilDecay = stackType.getDecayTicks();
            }

            return stacks <= 0;
        }
    }

    /**
     * 玩家叠层数据存储（仅服务端写入）
     */
    private static final Map<UUID, Map<StackType, StackData>> PLAYER_STACKS = new ConcurrentHashMap<>();

    /**
     * ⭐ 武器类叠层的固定顺序表：同步包按这个顺序打包 / 解包，两端必须一致，
     * 所以只能追加、不能重排或删除。
     */
    public static final StackType[] WEAPON_STACK_TYPES = {
            StackType.BASE_DAMAGE,
            StackType.MULTISHOT,
            StackType.MELEE_CRIT_MULT,
            StackType.TRIGGER_CHANCE,
            StackType.ATTACK_RANGE,
            StackType.ATTACK_SPEED,
            StackType.BURSTING_RADIUS,
            StackType.FIRING_RATE
    };

    /**
     * ⭐ 客户端镜像：本地玩家的武器叠层数量，由 {@code WarframeModuleSyncPacket} 每次同步时覆盖。
     * key = 本地玩家 UUID（只会有本地玩家一个条目），value 按 {@link #WEAPON_STACK_TYPES} 顺序排列。
     *
     * <p>以前客户端拿不到叠层数据，射速 / 多重射击等在客户端一律按 0 层算，
     * 与服务端不一致，蓄力动画会对不上；现在两端看到的是同一份数据。</p>
     */
    private static final Map<UUID, int[]> CLIENT_WEAPON_STACKS = new ConcurrentHashMap<>();

    /**
     * 该玩家是否是本客户端的本地玩家（仅在客户端调用有意义）
     *
     * <p>⭐ 用「镜像里有没有这个 UUID」来判断，而不是碰 {@code Minecraft.getInstance()} ——
     * 本类是双端共用的，不能引用任何客户端专用类。
     * {@code CLIENT_WEAPON_STACKS} 只会有本地玩家一个条目
     * （{@link #onClientSyncReceived} 每次同步先 clear 再 put），所以这个判断是准的。</p>
     */
    private static boolean isLocalPlayer(Player player) {
        return CLIENT_WEAPON_STACKS.containsKey(player.getUUID());
    }

    /**
     * 玩家击杀时添加叠层
     */
    public static void addStack(Player player, StackType stackType) {
        if (player == null || player.level().isClientSide()) {
            return;
        }

        UUID playerUUID = player.getUUID();

        Map<StackType, StackData> playerData = PLAYER_STACKS.computeIfAbsent(
                playerUUID,
                k -> new HashMap<>()
        );

        StackData stackData = playerData.computeIfAbsent(
                stackType,
                k -> new StackData(stackType)
        );

        stackData.addStack();
    }

    /**
     * 获取玩家的叠层数量
     */
    public static int getStacks(Player player, StackType stackType) {
        if (player == null) {
            return 0;
        }
        // ⭐ 客户端：武器类叠层读本类的镜像，战甲类转发给 WarframeModuleHandler 的镜像。
        //
        //    改造前这里遍历完 WEAPON_STACK_TYPES 找不到就 `return 0` —— 传战甲类型进来会
        //    **静默返回 0 且不报错**。这类 bug 的现象是「数字不对」但没有任何异常线索，
        //    很容易先去怀疑同步包、怀疑 NBT、绕一大圈才想到是读取入口选错了。
        //    客户端的战甲叠层其实一直都有（WarframeModuleHandler.clientSyncedKillStacks），
        //    只是存在另一条独立的镜像通道里。现在这个方法覆盖全部 20 种类型。
        if (player.level().isClientSide()) {
            int[] mirror = CLIENT_WEAPON_STACKS.get(player.getUUID());
            if (mirror != null) {
                for (int i = 0; i < WEAPON_STACK_TYPES.length; i++) {
                    if (WEAPON_STACK_TYPES[i] == stackType) {
                        return i < mirror.length ? mirror[i] : 0;
                    }
                }
            }
            // ⭐ 战甲类镜像是一个单例数组（不按 UUID 索引），里面只可能是本地玩家的数据。
            //    不加这道判定的话，传其它玩家进来会拿到本地玩家的层数，
            //    与上面武器类分支「别的玩家返回 0」的语义不一致。
            if (!isLocalPlayer(player)) {
                return 0;
            }
            return pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler.getClientKillStacks(stackType);
        }

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(player.getUUID());
        if (playerData == null) {
            return 0;
        }

        StackData stackData = playerData.get(stackType);
        return stackData != null ? stackData.stacks : 0;
    }

    /**
     * ⭐ 服务端：按 {@link #WEAPON_STACK_TYPES} 顺序收集玩家当前的武器叠层数量（供同步包打包）
     *
     * @param player 服务端玩家
     * @return 各武器叠层数量，顺序与 {@link #WEAPON_STACK_TYPES} 一致
     */
    public static int[] collectWeaponStacks(Player player) {
        int[] counts = new int[WEAPON_STACK_TYPES.length];
        if (player == null) {
            return counts;
        }
        for (int i = 0; i < WEAPON_STACK_TYPES.length; i++) {
            counts[i] = getStacks(player, WEAPON_STACK_TYPES[i]);
        }
        return counts;
    }

    /**
     * ⭐ 客户端：收到同步包时覆盖本地玩家的武器叠层镜像
     *
     * @param localPlayerId 本地玩家 UUID
     * @param weaponStacks  服务端发来的数量数组，顺序与 {@link #WEAPON_STACK_TYPES} 一致；为 null 时清空
     */
    public static void onClientSyncReceived(UUID localPlayerId, int[] weaponStacks) {
        if (localPlayerId == null) {
            return;
        }
        if (weaponStacks == null) {
            CLIENT_WEAPON_STACKS.remove(localPlayerId);
            return;
        }
        // 只保留本地玩家一个条目：换存档 / 换服务器后旧 UUID 的数据不会残留
        CLIENT_WEAPON_STACKS.clear();
        CLIENT_WEAPON_STACKS.put(localPlayerId, weaponStacks.clone());
    }

    /**
     * ⭐ 客户端：清空叠层镜像（退出世界 / 断线时调用）
     */
    public static void clearClientStacks() {
        CLIENT_WEAPON_STACKS.clear();
    }

    /**
     * 清空玩家的所有叠层
     */
    public static void clearStacks(Player player) {
        if (player != null) {
            PLAYER_STACKS.remove(player.getUUID());
        }
    }

    /**
     * 清空玩家指定类型的叠层
     */
    public static void clearStack(Player player, StackType stackType) {
        if (player == null) {
            return;
        }

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(player.getUUID());
        if (playerData != null) {
            playerData.remove(stackType);
        }
    }

    /**
     * 玩家退出时清空其叠层
     *
     * <p>⭐ 改造前 {@link #clearStacks} 全项目**零调用**，{@code PLAYER_STACKS}
     * 里带着非零层数下线的玩家条目永远不会被移除。后果有两层：</p>
     * <ul>
     *   <li><b>玩法</b>（主要）：刷到满层 → 下线 → 隔天上线，头几十秒仍带着满层伤害 /
     *       多重射击 / 射速加成，然后才开始掉。武器类叠层有客户端镜像、面板上直接看得见，
     *       所以这是会被发现并当成「可存档的爆发技」来用的 —— 满层时下线，打 boss 前上线。</li>
     *   <li><b>内存</b>（次要）：每个残留条目是一个最多 20 项的小 Map，量级几百字节，
     *       重启即清零，实际运营中不会因此 OOM。</li>
     * </ul>
     * <p>另一种设计是把 {@code ticksUntilDecay} 与下线时间戳一起持久化到 capability，
     * 登录时按真实流逝时间补扣层数。现状是两头不靠，所以这里选「下线即清空」。</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent evt) {
        clearStacks(evt.getEntity());
    }

    /**
     * 玩家Tick事件 - 处理叠层衰减
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || evt.player.level().isClientSide()) {
            return;
        }

        Player player = evt.player;
        UUID playerUUID = player.getUUID();

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(playerUUID);
        if (playerData == null || playerData.isEmpty()) {
            return;
        }

        playerData.entrySet().removeIf(entry -> {
            StackData stackData = entry.getValue();
            return stackData.tick();
        });

        if (playerData.isEmpty()) {
            PLAYER_STACKS.remove(playerUUID);
        }
    }
}
