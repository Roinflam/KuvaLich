package pers.roinflam.kuvalich.module.warframe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 战甲效果事件处理器
 * 负责护盾恢复、生命值/护甲系统、伤害抗性、跳跃增益、掉落物、治疗、挖掘速度等
 *
 * Warframe Effect Event Handler
 * Handles shield recovery, health/armor systems, damage resistance,
 * jump boost, drops, healing, digging speed, etc.
 */
@Mod.EventBusSubscriber
public class WarframeEffectHandler {

    /**
     * 护盾恢复冷却（UUID → 剩余秒数）
     */
    public static HashMap<UUID, Integer> cooldingHashMap = new HashMap<>();

    /**
     * 固定属性 AttributeModifier 的 UUID
     */
    private static final UUID FIXED_HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final UUID FIXED_ARMOR_MODIFIER_UUID = UUID.fromString("a1b2c3d4-5555-6666-7777-888888888888");

    // ========== 实体加入世界 / Entity Join Level ==========

    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Player) {
            // 预留位置：可以在这里初始化玩家数据
        }
    }

    // ========== 伤害事件 / Damage Events ==========

    /**
     * 玩家受伤/攻击时的战甲属性处理
     * - 受伤时：应用各种抗性、设置护盾冷却
     * - 攻击时：设置护盾冷却、击杀检测
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            DamageSource damageSource = evt.getSource();

            // 情况1：玩家受伤
            if (evt.getEntity() instanceof Player) {
                Player player = (Player) evt.getEntity();

                HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                // 护盾恢复冷却
                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    coolding *= 3; // 护盾破碎时冷却时间x3
                }
                cooldingHashMap.put(player.getUUID(), coolding);

                // 各种抗性
                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                    double damageMultiplier = attributes.getOrDefault("fireProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
                    double damageMultiplier = attributes.getOrDefault("electricProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.getEntity() instanceof Player) {
                    double damageMultiplier = attributes.getOrDefault("homologousProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }

                if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
                    double damageMultiplier = attributes.getOrDefault("fallProtection", 1.0);
                    evt.setAmount((float) (evt.getAmount() * damageMultiplier));
                }
            }

            // 情况2：玩家攻击
            if (damageSource.getEntity() instanceof Player) {
                Player player = (Player) damageSource.getEntity();

                HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                double delayMultiplier = attributes.getOrDefault("shieldRecoveryDelay", 1.0);
                int coolding = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    coolding *= 3;
                }
                cooldingHashMap.put(player.getUUID(), coolding);

                // 击杀检测
                if (evt.getEntity().getHealth() - evt.getAmount() <= 0) {
                    WarframeModuleHandler.addWarframeKillStacks(player);
                }
            }
        }
    }

    // ========== 跳跃增益 / Jump Boost ==========

    @SubscribeEvent
    public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getEntity() instanceof Player) {
            Player player = (Player) evt.getEntity();

            HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
            WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

            Double jumpBoostObj = attributes.get("jumpBoost");

            if (jumpBoostObj != null && jumpBoostObj != 0.0) {
                double jumpBoost = jumpBoostObj;

                player.setDeltaMovement(
                        player.getDeltaMovement().x,
                        player.getDeltaMovement().y * Math.sqrt(1.0 + jumpBoost),
                        player.getDeltaMovement().z
                );

                if (player instanceof ServerPlayer) {
                    ServerPlayer serverPlayer = (ServerPlayer) player;
                    serverPlayer.connection.send(
                            new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player)
                    );
                }
            }
        }
    }

    // ========== 掉落物倍率 / Drop Multiplier ==========

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getSource().getEntity() instanceof Player) {
            if (evt.getEntity() instanceof Animal || evt.getEntity() instanceof Monster) {
                Player player = (Player) evt.getSource().getEntity();

                HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                double itemDropMultiplier = 1 + attributes.getOrDefault("itemDropMultiplier", 0.0);
                Collection<ItemEntity> drops = evt.getDrops();
                for (ItemEntity drop : drops) {
                    ItemStack dropStack = drop.getItem();
                    if (!(dropStack.getItem() instanceof ArmorItem) &&
                            !(dropStack.getItem() instanceof SwordItem) &&
                            !(dropStack.getItem() instanceof TieredItem)) {
                        dropStack.setCount((int) (dropStack.getCount() * itemDropMultiplier));
                    }
                }
            }
        }
    }

    // ========== 治疗倍率 / Heal Multiplier ==========

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().level().isClientSide() && evt.getEntity() instanceof Player) {
            Player player = (Player) evt.getEntity();

            HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
            WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

            double responseRate = 1 + attributes.getOrDefault("responseRate", 0.0);
            if (responseRate <= 0) {
                evt.setCanceled(true);
            } else {
                evt.setAmount((float) (evt.getAmount() * responseRate));
            }
        }
    }

    // ========== 挖掘速度 / Digging Speed ==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        Player player = evt.getEntity();

        // 客户端：使用网络包缓存数据
        if (player.level().isClientSide()) {
            float cachedIncrement = DiggingSpeedPacket.getDiggingSpeedIncrement(player.getUUID());
            float speedMultiplier = 1.0f + cachedIncrement;
            evt.setNewSpeed(evt.getNewSpeed() * speedMultiplier);
            return;
        }

        // 服务端：使用缓存属性
        HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
        WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

        double diggingSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
        float speedMultiplier = (float) (1.0 + diggingSpeed);
        evt.setNewSpeed(evt.getNewSpeed() * speedMultiplier);
    }

    // ========== 固定上限方法 / Fixed Cap Methods ==========

    /**
     * 应用固定生命值上限（覆盖所有其他生命值加成）
     */
    private static void applyFixedHealthCap(Player player, double fixedHealth) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) return;

        AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
        if (oldModifier != null) {
            maxHealthAttribute.removeModifier(oldModifier);
        }

        if (fixedHealth > 0) {
            double cappedHealth = Math.max(1.0, fixedHealth);
            double baseHealth = maxHealthAttribute.getBaseValue();
            double operation = cappedHealth - baseHealth;

            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_HEALTH_MODIFIER_UUID,
                    "Warframe Fixed Health Cap",
                    operation,
                    AttributeModifier.Operation.ADDITION
            );

            maxHealthAttribute.addPermanentModifier(newModifier);

            if (player.getHealth() > cappedHealth) {
                player.setHealth((float) cappedHealth);
            }
        }
    }

    /**
     * 应用固定护甲上限（覆盖所有其他护甲加成）
     */
    private static void applyFixedArmorCap(Player player, double fixedArmor) {
        AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
        if (armorAttribute == null) return;

        AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
        if (oldModifier != null) {
            armorAttribute.removeModifier(oldModifier);
        }

        if (fixedArmor > 0) {
            double cappedArmor = Math.max(0.0, fixedArmor);
            double currentTotalArmor = armorAttribute.getValue();
            double operation = cappedArmor - currentTotalArmor;

            AttributeModifier newModifier = new AttributeModifier(
                    FIXED_ARMOR_MODIFIER_UUID,
                    "Warframe Fixed Armor Cap",
                    operation,
                    AttributeModifier.Operation.ADDITION
            );

            armorAttribute.addPermanentModifier(newModifier);
        }
    }

    /**
     * 限制生命值不超过当前最大生命值
     */
    private static void limitHealthToMax(Player player) {
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();

        if (currentHealth > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    // ========== 玩家Tick / Player Tick ==========

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull Player player = evt.player;
                if (player.isAlive()) {

                    // ═══ 每秒：护盾恢复 ═══
                    if (player.level().getGameTime() % 20 == 0) {
                        if (cooldingHashMap.containsKey(player.getUUID())) {
                            if (cooldingHashMap.get(player.getUUID()) > 1) {
                                cooldingHashMap.put(player.getUUID(),
                                        cooldingHashMap.get(player.getUUID()) - 1);
                            } else {
                                cooldingHashMap.remove(player.getUUID());
                            }
                        } else {
                            HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                            WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                            double shield = attributes.getOrDefault("shield", 0.0);
                            double fixedShield = attributes.getOrDefault("fixedShield", 0.0);

                            if (fixedShield > 0) {
                                // 固定护盾模式
                                double cappedShield = Math.max(0.0, fixedShield);
                                if (cappedShield > 0) {
                                    float currentShield = player.getAbsorptionAmount();
                                    if (currentShield < cappedShield) {
                                        double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                        player.setAbsorptionAmount((float) Math.min(
                                                cappedShield,
                                                currentShield + cappedShield * 0.01 * shieldRecoveryRate
                                        ));
                                    } else if (currentShield > cappedShield) {
                                        player.setAbsorptionAmount((float) cappedShield);
                                    }
                                }
                            } else if (shield > 0) {
                                // 百分比护盾模式
                                double shieldCap = player.getMaxHealth() * shield / 2;
                                float currentShield = player.getAbsorptionAmount();

                                if (currentShield < shieldCap) {
                                    double shieldRecoveryRate = 1 + attributes.getOrDefault("shieldRecoveryRate", 0.0);
                                    player.setAbsorptionAmount((float) Math.min(
                                            shieldCap,
                                            currentShield + shieldCap * 0.01 * shieldRecoveryRate
                                    ));
                                } else if (currentShield > shieldCap) {
                                    player.setAbsorptionAmount((float) shieldCap);
                                }
                            }
                        }
                    }

                    // ═══ 每0.25秒：属性效果 + 挖掘速度同步 + 固定上限 ═══
                    if (player.level().getGameTime() % 5 == 0) {
                        HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                        WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                        // ═══ 生命值系统 ═══
                        double fixedHealth = attributes.getOrDefault("fixedHealth", 0.0);
                        if (fixedHealth > 0) {
                            applyFixedHealthCap(player, fixedHealth);
                        } else {
                            AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
                            if (maxHealthAttribute != null) {
                                AttributeModifier oldModifier = maxHealthAttribute.getModifier(FIXED_HEALTH_MODIFIER_UUID);
                                if (oldModifier != null) {
                                    maxHealthAttribute.removeModifier(oldModifier);
                                }
                            }

                            double health = attributes.getOrDefault("health", 0.0);
                            if (health >= 0.1) {
                                int level = (int) (health / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.HEALTH.createInstance(6, level));
                            } else if (health <= -0.1) {
                                int level = (int) (-health / 0.1) - 1;
                                level = Math.min(level, 8);
                                DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_HEALTH.createInstance(6, level));
                            }

                            limitHealthToMax(player);
                        }

                        // ═══ 护甲系统 ═══
                        double fixedArmor = attributes.getOrDefault("fixedArmor", 0.0);
                        if (fixedArmor > 0) {
                            applyFixedArmorCap(player, fixedArmor);
                        } else {
                            AttributeInstance armorAttribute = player.getAttribute(Attributes.ARMOR);
                            if (armorAttribute != null) {
                                AttributeModifier oldModifier = armorAttribute.getModifier(FIXED_ARMOR_MODIFIER_UUID);
                                if (oldModifier != null) {
                                    armorAttribute.removeModifier(oldModifier);
                                }
                            }

                            double armor = attributes.getOrDefault("armor", 0.0);
                            if (armor >= 0.1) {
                                int level = (int) (armor / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.ARMOR.createInstance(6, level));
                            } else if (armor <= -0.1) {
                                int level = (int) (-armor / 0.1) - 1;
                                DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_ARMOR.createInstance(6, level));
                            }
                        }

                        // ═══ 其他属性 ═══
                        double sprintSpeed = attributes.getOrDefault("sprintSpeed", 0.0);
                        if (sprintSpeed >= 0.1) {
                            int level = (int) (sprintSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.MOVEMENT_SPEED.createInstance(6, level));
                        } else if (sprintSpeed <= -0.1) {
                            int level = (int) (-sprintSpeed / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_MOVEMENT_SPEED.createInstance(6, level));
                        }

                        double knockbackResistance = attributes.getOrDefault("knockbackResistance", 0.0);
                        if (knockbackResistance >= 0.1) {
                            int level = (int) (knockbackResistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.KNOCKBACK_RESISTANCE.createInstance(6, level));
                        } else if (knockbackResistance <= -0.1) {
                            int level = (int) (-knockbackResistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_KNOCKBACK_RESISTANCE.createInstance(6, level));
                        }

                        double reachDistance = attributes.getOrDefault("reachDistance", 0.0);
                        if (reachDistance >= 0.1) {
                            int level = (int) (reachDistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.REACH_DISTANCE.createInstance(6, level));
                        } else if (reachDistance <= -0.1) {
                            int level = (int) (-reachDistance / 0.1) - 1;
                            DynamicAttributeManager.apply(player, DynamicAttributes.NEGATIVE_REACH_DISTANCE.createInstance(6, level));
                        }

                        // ═══ 同步挖掘速度到客户端 ═══
                        double diggingSpeed = attributes.getOrDefault("diggingSpeed", 0.0);
                        if (player instanceof ServerPlayer) {
                            if (DiggingSpeedPacket.shouldSend(player.getUUID(), (float) diggingSpeed)) {
                                KuvaLich.network.send(
                                        PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                                        new DiggingSpeedPacket((float) diggingSpeed)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== 玩家退出清理 / Player Logout Cleanup ==========

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        UUID uuid = evt.getEntity().getUUID();
        cooldingHashMap.remove(uuid);
        DiggingSpeedPacket.cleanupPlayer(uuid);
        WarframeModuleHandler.cleanupCache(uuid);
    }
}
