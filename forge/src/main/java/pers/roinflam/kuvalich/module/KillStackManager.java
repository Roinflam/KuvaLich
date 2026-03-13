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
     * 玩家叠层数据存储
     */
    private static final Map<UUID, Map<StackType, StackData>> PLAYER_STACKS = new ConcurrentHashMap<>();

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

        Map<StackType, StackData> playerData = PLAYER_STACKS.get(player.getUUID());
        if (playerData == null) {
            return 0;
        }

        StackData stackData = playerData.get(stackType);
        return stackData != null ? stackData.stacks : 0;
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