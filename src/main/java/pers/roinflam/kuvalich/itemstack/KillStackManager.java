package pers.roinflam.kuvalich.itemstack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.kuvalich.config.ModConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 击杀叠加效果管理器
 *
 * 管理所有击杀叠层效果，每10秒衰减一层
 * 最大层数从配置文件读取，支持运行时修改
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
public class KillStackManager {

    /**
     * 击杀叠层类型枚举
     * 每种类型的最大层数从配置文件动态读取
     */
    public enum StackType {
        BASE_DAMAGE,           // 基础伤害（根据目标负面效果数量加成）
        MULTISHOT,             // 多重射击
        MELEE_CRIT_MULT,       // 近战暴击伤害
        TRIGGER_CHANCE,        // 触发几率
        ATTACK_RANGE,          // 攻击范围
        ATTACK_SPEED,          // 攻击速度
        BURSTING_RADIUS,       // 爆炸半径
        FIRING_RATE;           // 射速

        /**
         * 获取该类型的最大层数（从配置文件读取）
         * @return 最大层数
         */
        public int getMaxStacks() {
            switch (this) {
                case BASE_DAMAGE:
                    return ModConfig.KUVA_LICH.maxStacksBaseDamage;
                case MULTISHOT:
                    return ModConfig.KUVA_LICH.maxStacksMultishot;
                case MELEE_CRIT_MULT:
                    return ModConfig.KUVA_LICH.maxStacksMeleeCritMult;
                case TRIGGER_CHANCE:
                    return ModConfig.KUVA_LICH.maxStacksTriggerChance;
                case ATTACK_RANGE:
                    return ModConfig.KUVA_LICH.maxStacksAttackRange;
                case ATTACK_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksAttackSpeed;
                case BURSTING_RADIUS:
                    return ModConfig.KUVA_LICH.maxStacksBurstingRadius;
                case FIRING_RATE:
                    return ModConfig.KUVA_LICH.maxStacksFiringRate;
                default:
                    return 5; // 默认值（正常情况不会用到）
            }
        }
    }

    /**
     * 单个叠层数据
     */
    private static class StackData {
        int stacks;                // 当前层数
        int ticksUntilDecay;       // 距离下次衰减的tick数
        final StackType stackType; // 叠层类型（用于动态获取最大层数）

        /**
         * 构造函数
         * @param stackType 叠层类型
         */
        StackData(StackType stackType) {
            this.stacks = 0;
            this.ticksUntilDecay = 200; // 10秒（200 ticks = 10 seconds）
            this.stackType = stackType;
        }

        /**
         * 添加一层
         * 如果已达到最大层数则不再增加
         */
        void addStack() {
            if (stacks < stackType.getMaxStacks()) {
                stacks++;
            }
            ticksUntilDecay = 200; // 重置衰减计时
        }

        /**
         * Tick更新
         * @return 是否完全衰减（可以移除此数据）
         */
        boolean tick() {
            if (stacks <= 0) {
                return true; // 已经没有层数，可以移除
            }

            ticksUntilDecay--;
            if (ticksUntilDecay <= 0) {
                stacks--;
                ticksUntilDecay = 200; // 重置衰减计时
            }

            return stacks <= 0;
        }
    }

    /**
     * 玩家叠层数据存储
     * 结构：玩家UUID -> (叠层类型 -> 叠层数据)
     */
    private static final Map<UUID, Map<StackType, StackData>> PLAYER_STACKS = new ConcurrentHashMap<>();

    /**
     * 玩家击杀时添加叠层
     *
     * @param player 玩家
     * @param stackType 叠层类型
     */
    public static void addStack(EntityPlayer player, StackType stackType) {
        if (player == null || player.world.isRemote) {
            return;
        }

        UUID playerUUID = player.getUniqueID();

        // 获取或创建玩家的叠层数据
        Map<StackType, StackData> playerData = PLAYER_STACKS.computeIfAbsent(
                playerUUID,
                k -> new HashMap<>()
        );

        // 获取或创建该类型的叠层数据
        StackData stackData = playerData.computeIfAbsent(
                stackType,
                k -> new StackData(stackType)
        );

        // 添加层数
        stackData.addStack();
    }

    /**
     * 获取玩家的叠层数量
     *
     * @param player 玩家
     * @param stackType 叠层类型
     * @return 当前层数
     */
    public static int getStacks(EntityPlayer player, StackType stackType) {
        if (player == null) {
            return 0;
        }

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(player.getUniqueID());
        if (playerData == null) {
            return 0;
        }

        StackData stackData = playerData.get(stackType);
        return stackData != null ? stackData.stacks : 0;
    }

    /**
     * 清空玩家的所有叠层
     *
     * @param player 玩家
     */
    public static void clearStacks(EntityPlayer player) {
        if (player != null) {
            PLAYER_STACKS.remove(player.getUniqueID());
        }
    }

    /**
     * 清空玩家指定类型的叠层
     *
     * @param player 玩家
     * @param stackType 叠层类型
     */
    public static void clearStack(EntityPlayer player, StackType stackType) {
        if (player == null) {
            return;
        }

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(player.getUniqueID());
        if (playerData != null) {
            playerData.remove(stackType);
        }
    }

    /**
     * 玩家Tick事件 - 处理叠层衰减
     * 每10秒减少1层，直至归零
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || evt.player.world.isRemote) {
            return;
        }

        EntityPlayer player = evt.player;
        UUID playerUUID = player.getUniqueID();

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(playerUUID);
        if (playerData == null || playerData.isEmpty()) {
            return;
        }

        // 更新所有叠层
        playerData.entrySet().removeIf(entry -> {
            StackData stackData = entry.getValue();
            return stackData.tick(); // 如果完全衰减，移除该类型
        });

        // 如果玩家所有叠层都衰减完了，移除玩家数据
        if (playerData.isEmpty()) {
            PLAYER_STACKS.remove(playerUUID);
        }
    }
}