package pers.roinflam.kuvalich.dynamicattr.dynamiceffect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttribute;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeInstance;
import pers.roinflam.kuvalich.dynamicattr.DynamicAttributeManager;
import pers.roinflam.kuvalich.render.particle.ElementParticleEffects;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 动态属性注册（v6）
 * Dynamic Attribute Registration
 *
 * <p>⭐ v6 变更：
 * <ul>
 *     <li>{@code MAGNETIC} 移除 {@code setTickInterval} 和 {@code onTick} 链，
 *         粒子生成完全停止。磁力视觉改由 {@code ElementGeometryRenderer} 的
 *         几何双环线条承担，不再依赖粒子系统。</li>
 *     <li>磁力的护盾增伤逻辑（{@code withEventHandler}）完全保留不变。</li>
 * </ul></p>
 *
 * <p>其他元素（FIRE/ICE/POISON/RADIATION/CORROSION/VIRUS/PUNCTURE）的 onTick
 * 粒子生成保持不变。</p>
 */
public class DynamicAttributes {

    // ========== 基础属性 ==========

    public static final DynamicAttribute HEALTH = new DynamicAttribute("health")
            .addModifier(Attributes.MAX_HEALTH, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_HEALTH = new DynamicAttribute("negative_health")
            .addModifier(Attributes.MAX_HEALTH, -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute ARMOR = new DynamicAttribute("armor")
            .addModifier(Attributes.ARMOR, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_ARMOR = new DynamicAttribute("negative_armor")
            .addModifier(Attributes.ARMOR, -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute MOVEMENT_SPEED = new DynamicAttribute("movement_speed")
            .addModifier(Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_MOVEMENT_SPEED = new DynamicAttribute("negative_movement_speed")
            .addModifier(Attributes.MOVEMENT_SPEED, -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute ATTACK_SPEED = new DynamicAttribute("attack_speed")
            .addModifier(Attributes.ATTACK_SPEED, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_ATTACK_SPEED = new DynamicAttribute("negative_attack_speed")
            .addModifier(Attributes.ATTACK_SPEED, -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute KNOCKBACK_RESISTANCE = new DynamicAttribute("knockback_resistance")
            .addModifier(Attributes.KNOCKBACK_RESISTANCE, 0.1, AttributeModifier.Operation.ADDITION);

    public static final DynamicAttribute NEGATIVE_KNOCKBACK_RESISTANCE = new DynamicAttribute("negative_knockback_resistance")
            .addModifier(Attributes.KNOCKBACK_RESISTANCE, -0.1, AttributeModifier.Operation.ADDITION);

    public static final DynamicAttribute REACH_DISTANCE = new DynamicAttribute("reach_distance")
            .addModifier(ForgeMod.BLOCK_REACH.get(), 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_REACH_DISTANCE = new DynamicAttribute("negative_reach_distance")
            .addModifier(ForgeMod.BLOCK_REACH.get(), -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute ATTACK_RANGE = new DynamicAttribute("attack_range")
            .addModifier(ForgeMod.ENTITY_REACH.get(), 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute NEGATIVE_ATTACK_RANGE = new DynamicAttribute("negative_attack_range")
            .addModifier(ForgeMod.ENTITY_REACH.get(), -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ========== 持续伤害效果 ==========

    /**
     * 火焰效果 - 护甲削减升级（0->1->2->3）
     * ⭐ 视觉：每 0.5 秒生成火焰 + 灵魂火焰 + 熔岩 + 小火焰 + 烟雾 + 灰烬
     * ⭐ v8：粒子密度按 amplifier 分层（50% → 100%）
     */
    public static final DynamicAttribute FIRE = new DynamicAttribute("fire")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();

                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnFireEffect(entity, sl, ctx.getAmplifier());
                }

                int currentLevel = ctx.getAmplifier();
                if (currentLevel < 3) {
                    int newLevel = currentLevel + 1;
                    DynamicAttributeManager.apply(entity,
                            new DynamicAttributeInstance(
                                    ctx.getInstance().getAttribute(),
                                    ctx.getRemainingDuration(),
                                    newLevel
                            ));
                }
            })
            .addModifier(Attributes.ARMOR, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                double[] armorReductions = {0.15, 0.30, 0.40, 0.50};
                return -armorReductions[Math.min(level, 3)];
            })
            .addModifier(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                double[] armorReductions = {0.15, 0.30, 0.40, 0.50};
                return -armorReductions[Math.min(level, 3)];
            });

    /**
     * 冰冻效果 - 叠加式减速（0级50% → 8级90%）
     * ⭐ 视觉：每 0.5 秒生成雪花 + 冰晶 + 烟花闪光 + 悬浮冰雾；身上覆盖蓝冰装饰
     */
    public static final DynamicAttribute ICE = new DynamicAttribute("ice")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnIceEffect(entity, sl, ctx.getAmplifier());
                }
            })
            .addModifier(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return -(0.5 + Math.min(level, 8) * 0.05);
            })
            .addModifier(Attributes.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return -(0.5 + Math.min(level, 8) * 0.05);
            })
            .addModifier(Attributes.FLYING_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return -(0.5 + Math.min(level, 8) * 0.05);
            });

    /**
     * ⭐ 毒素持续标记 DynamicAttribute（纯视觉）
     * DOT 由 WeaponElementSystem 的 SynchronizationTask 负责
     */
    public static final DynamicAttribute POISON = new DynamicAttribute("poison")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnPoisonEffect(entity, sl);
                }
            });

    /**
     * 电击麻痹效果 - 移动速度降低99%（几乎定身）
     */
    public static final DynamicAttribute ELECTRICITY_PARALYSIS = new DynamicAttribute("electricity_paralysis")
            .addModifier(Attributes.MOVEMENT_SPEED, -0.99, AttributeModifier.Operation.MULTIPLY_BASE)
            .addModifier(Attributes.FLYING_SPEED, -0.99, AttributeModifier.Operation.MULTIPLY_BASE);

    /**
     * 磁力效果 - 有护盾时受到伤害增加（0级100% → 9级325%）
     * ⭐ v6：粒子完全移除，视觉改由 ElementGeometryRenderer 的几何双环承担。
     *        护盾增伤逻辑（withEventHandler）完全保留。
     */
    public static final DynamicAttribute MAGNETIC = new DynamicAttribute("magnetic")
            .withEventHandler(entity -> new Object() {
                private final UUID boundEntityId = entity.getUUID();

                @SubscribeEvent
                public void onMagneticHurt(LivingHurtEvent event) {
                    if (!event.getEntity().getUUID().equals(boundEntityId)) return;
                    if (event.getEntity().level().isClientSide()) return;

                    if (event.getEntity().getAbsorptionAmount() > 0) {
                        int level = DynamicAttributeManager.getAmplifier((LivingEntity) event.getEntity(), DynamicAttributes.MAGNETIC);
                        if (level < 0) return;
                        double damageMultiplier = 1.0 + (1.0 + Math.min(level, 9) * 0.25);
                        event.setAmount(event.getAmount() * (float) damageMultiplier);
                    }
                }
            });

    /**
     * 辐射效果 - 混乱攻击同类并增伤（0级100% → 9级550%）
     * ⭐ 视觉：每 0.5 秒生成黄→绿渐变 + 灵魂 + 金光 + 电火花 + 发光点
     */
    public static final DynamicAttribute RADIATION = new DynamicAttribute("radiation")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();

                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnRadiationEffect(entity, sl, ctx.getAmplifier());
                }

                if (!(entity instanceof Mob mob)) {
                    return;
                }

                List<LivingEntity> nearbyEntities = entity.level().getEntitiesOfClass(
                        LivingEntity.class,
                        entity.getBoundingBox().inflate(24),
                        e -> e.isAlive() && !e.equals(entity)
                );

                if (nearbyEntities.isEmpty()) {
                    return;
                }

                LivingEntity target = nearbyEntities.stream()
                        .filter(e -> e.getType().equals(entity.getType()))
                        .min(Comparator.comparingDouble(entity::distanceToSqr))
                        .orElseGet(() -> nearbyEntities.stream()
                                .min(Comparator.comparingDouble(entity::distanceToSqr))
                                .orElse(null));

                if (target != null) {
                    mob.setTarget(target);
                    mob.setLastHurtByMob(target);
                }
            })
            .withEventHandler(entity -> new Object() {
                private final UUID boundEntityId = entity.getUUID();

                @SubscribeEvent
                public void onAttack(LivingHurtEvent event) {
                    if (event.getSource().getEntity() == null) return;
                    if (!event.getSource().getEntity().getUUID().equals(boundEntityId)) return;
                    if (event.getEntity().level().isClientSide()) return;

                    if (event.getEntity() instanceof LivingEntity) {
                        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
                        LivingEntity target = (LivingEntity) event.getEntity();

                        if (target.getType().equals(attacker.getType())) {
                            int level = DynamicAttributeManager.getAmplifier(attacker, DynamicAttributes.RADIATION);
                            if (level < 0) return;
                            double damageMultiplier = 1.0 + (1.0 + Math.min(level, 9) * 0.5);
                            event.setAmount(event.getAmount() * (float) damageMultiplier);
                        }
                    }
                }
            });

    /**
     * 腐蚀效果 - 降低护甲（0级26% → 9级80%）
     * ⭐ 视觉：每 0.5 秒生成深绿酸液 + 粘液 + 黑曜石泪滴 + 酸液落地
     */
    public static final DynamicAttribute CORROSION = new DynamicAttribute("corrosion")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnCorrosionEffect(entity, sl, ctx.getAmplifier());
                }
            })
            .addModifier(Attributes.ARMOR, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return -(0.26 + Math.min(level, 9) * 0.06);
            })
            .addModifier(Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return -(0.26 + Math.min(level, 9) * 0.06);
            });

    /**
     * 穿刺效果 - 减少近战伤害（0级40% → 3级80%）
     * ⭐ 视觉：每 0.5 秒生成白色针状 + 金属闪烁 + 浅银尘埃 + 发光点
     */
    public static final DynamicAttribute PUNCTURE = new DynamicAttribute("puncture")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnPunctureEffect(entity, sl, ctx.getAmplifier());
                }
            })
            .withEventHandler(entity -> new Object() {
                private final UUID boundEntityId = entity.getUUID();

                @SubscribeEvent
                public void onPunctureHurt(LivingHurtEvent event) {
                    if (!event.getEntity().getUUID().equals(boundEntityId)) return;
                    if (event.getEntity().level().isClientSide()) return;

                    if (event.getSource().getDirectEntity() instanceof LivingEntity) {
                        int level = DynamicAttributeManager.getAmplifier((LivingEntity) event.getEntity(), DynamicAttributes.PUNCTURE);
                        if (level < 0) return;
                        double reduction = 0.4 + Math.min(level, 3) * 0.1;
                        event.setAmount(event.getAmount() * (float) (1.0 - reduction));
                    }
                }
            });

    public static final DynamicAttribute VITRICA = new DynamicAttribute("vitrica")
            .addModifier(Attributes.ATTACK_DAMAGE, -0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ATTACK_SPEED, -0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR, -0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, -0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.MOVEMENT_SPEED, -0.075, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute GUANDAO_PRIME = new DynamicAttribute("guandao_prime")
            .addModifier(Attributes.ATTACK_SPEED, 0.01, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static final DynamicAttribute ARCA_TITRON = new DynamicAttribute("arca_titron");

    /**
     * 病毒效果 - 满层降低50%生命上限（切割增伤在切割代码中处理）
     * ⭐ 视觉：每 0.5 秒生成病毒尘埃 + 孢子 + 监守者爆裂（v6 粉色主调）
     */
    public static final DynamicAttribute VIRUS = new DynamicAttribute("virus")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                if (entity.level() instanceof ServerLevel sl) {
                    ElementParticleEffects.spawnVirusEffect(entity, sl, ctx.getAmplifier());
                }
            })
            .addModifier(Attributes.MAX_HEALTH, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return level >= 9 ? -0.5 : 0.0;
            });
}
