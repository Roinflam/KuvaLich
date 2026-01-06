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
 * 管理所有击杀叠层效果：
 * - 武器叠层：10秒/层衰减
 * - 战甲叠层：20秒/层衰减
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
        // ========== 武器叠层（10秒/层） ==========
        BASE_DAMAGE,           // 基础伤害（根据目标负面效果数量加成）
        MULTISHOT,             // 多重射击
        MELEE_CRIT_MULT,       // 近战暴击伤害
        TRIGGER_CHANCE,        // 触发几率
        ATTACK_RANGE,          // 攻击范围
        ATTACK_SPEED,          // 攻击速度
        BURSTING_RADIUS,       // 爆炸半径
        FIRING_RATE,           // 射速

        // ========== 战甲叠层（20秒/层） ==========
        WARFRAME_HEALTH,                    // 生命值
        WARFRAME_SHIELD,                    // 护盾容量
        WARFRAME_ARMOR,                     // 护甲
        WARFRAME_SPRINT_SPEED,              // 冲刺速度
        WARFRAME_SHIELD_RECOVERY_RATE,      // 护盾恢复速率
        WARFRAME_SHIELD_RECOVERY_DELAY,     // 护盾恢复延迟
        WARFRAME_FIRE_PROTECTION,           // 火焰抗性
        WARFRAME_ELECTRIC_PROTECTION,       // 电击抗性
        WARFRAME_HOMOLOGOUS_PROTECTION,     // 同源抗性
        WARFRAME_RESPONSE_RATE,             // 恢复生命值倍率
        WARFRAME_ITEM_DROP_MULTIPLIER,      // 战利品掉落倍率
        WARFRAME_DIGGING_SPEED;             // 挖掘速度

        /**
         * 获取该类型的最大层数（从配置文件读取）
         * @return 最大层数
         */
        public int getMaxStacks() {
            switch (this) {
                // 武器叠层
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

                // 战甲叠层
                case WARFRAME_HEALTH:
                    return ModConfig.KUVA_LICH.maxStacksHealth;
                case WARFRAME_SHIELD:
                    return ModConfig.KUVA_LICH.maxStacksShield;
                case WARFRAME_ARMOR:
                    return ModConfig.KUVA_LICH.maxStacksArmor;
                case WARFRAME_SPRINT_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksSprintSpeed;
                case WARFRAME_SHIELD_RECOVERY_RATE:
                    return ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate;
                case WARFRAME_SHIELD_RECOVERY_DELAY:
                    return ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay;
                case WARFRAME_FIRE_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksFireProtection;
                case WARFRAME_ELECTRIC_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksElectricProtection;
                case WARFRAME_HOMOLOGOUS_PROTECTION:
                    return ModConfig.KUVA_LICH.maxStacksHomologousProtection;
                case WARFRAME_RESPONSE_RATE:
                    return ModConfig.KUVA_LICH.maxStacksResponseRate;
                case WARFRAME_ITEM_DROP_MULTIPLIER:
                    return ModConfig.KUVA_LICH.maxStacksItemDropMultiplier;
                case WARFRAME_DIGGING_SPEED:
                    return ModConfig.KUVA_LICH.maxStacksDiggingSpeed;

                default:
                    return 5;
            }
        }

        /**
         * 获取该类型的衰减时间（从配置文件读取）
         * @return 衰减时间（ticks）
         */
        public int getDecayTicks() {
            switch (this) {
                // 武器叠层：使用weaponStackDecayTicks配置
                case BASE_DAMAGE:
                case MULTISHOT:
                case MELEE_CRIT_MULT:
                case TRIGGER_CHANCE:
                case ATTACK_RANGE:
                case ATTACK_SPEED:
                case BURSTING_RADIUS:
                case FIRING_RATE:
                    return ModConfig.KUVA_LICH.weaponStackDecayTicks;

                // 战甲叠层：使用warframeStackDecayTicks配置
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
                    return ModConfig.KUVA_LICH.warframeStackDecayTicks;

                default:
                    return 200;
            }
        }
    }

    /**
     * 单个叠层数据
     */
    private static class StackData {
        int stacks;                // 当前层数
        int ticksUntilDecay;       // 距离下次衰减的tick数
        final StackType stackType; // 叠层类型（用于动态获取最大层数和衰减时间）

        /**
         * 构造函数
         * @param stackType 叠层类型
         */
        StackData(StackType stackType) {
            this.stacks = 0;
            this.ticksUntilDecay = stackType.getDecayTicks();
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
            ticksUntilDecay = stackType.getDecayTicks();
        }

        /**
         * Tick更新
         * @return 是否完全衰减（可以移除此数据）
         */
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
     * 武器叠层：每10秒（默认）减少1层
     * 战甲叠层：每20秒（默认）减少1层
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

        playerData.entrySet().removeIf(entry -> {
            StackData stackData = entry.getValue();
            return stackData.tick();
        });

        if (playerData.isEmpty()) {
            PLAYER_STACKS.remove(playerUUID);
        }
    }
}