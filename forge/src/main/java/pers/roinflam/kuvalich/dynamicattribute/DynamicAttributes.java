package pers.roinflam.kuvalich.dynamicattribute;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.module.weapon.element.ElementParticleEffects;
import pers.roinflam.kuvalich.module.weapon.element.ParticleEmissionGuard;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Comparator;
import java.util.List;

/**
 * 动态属性注册（v7）
 * Dynamic Attribute Registration
 *
 * <p>⭐ v7 变更（性能 + 正确性）：
 * <ul>
 *     <li><b>取消动态事件监听器。</b>MAGNETIC / RADIATION / PUNCTURE 原先用
 *         {@code withEventHandler} 在每次施加 debuff 时往 Forge 事件总线 register 一个
 *         绑定该实体 UUID 的匿名监听器，总线规模随刷怪量无限增长，且存在反注册不到的泄漏路径。
 *         现在改为本类内 {@link ElementCombatHandler} 的<b>一个</b>静态监听器，
 *         判定条件从「UUID 相等」换成等价的「{@code getAmplifier(...) >= 0}」，
 *         数值公式与触发条件逐条照搬，行为不变。</li>
 *     <li><b>DOT 粒子走 {@link ParticleEmissionGuard} 限流。</b>onTick 每 10 tick 每实体要发
 *         十几个粒子包，AoE 上 debuff 会让所有实体的 tick 相位对齐，
 *         出现单 tick 上千次 {@code sendParticles} 的尖峰（该类的 javadoc 记载过同类型的 Watchdog 卡死）。
 *         限流只挡粒子，debuff 的数值效果、火焰升级、辐射改仇恨一律照常执行。</li>
 * </ul></p>
 *
 * <p>v6 变更保留：{@code MAGNETIC} 没有 {@code onTick}，
 * 磁力视觉由 {@code ElementGeometryRenderer} 的几何双环线条承担，不依赖粒子系统。</p>
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

                // ⭐ 粒子走限流门控；被挡掉也绝不能影响下面的等级递增逻辑
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
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
                // ⭐ 粒子走限流门控
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
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
                // ⭐ 粒子走限流门控
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
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
     * ⭐ v7：护盾增伤逻辑搬到 {@link ElementCombatHandler}，数值与触发条件不变。
     */
    public static final DynamicAttribute MAGNETIC = new DynamicAttribute("magnetic");

    /**
     * 辐射效果 - 混乱攻击同类并增伤（0级100% → 9级550%）
     * ⭐ 视觉：每 0.5 秒生成黄→绿渐变 + 灵魂 + 金光 + 电火花 + 发光点
     * ⭐ v7：同类增伤逻辑搬到 {@link ElementCombatHandler}，数值与触发条件不变。
     */
    public static final DynamicAttribute RADIATION = new DynamicAttribute("radiation")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();

                // ⭐ 粒子走限流门控；被挡掉也绝不能影响下面的改仇恨逻辑
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
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
            });

    /**
     * 腐蚀效果 - 降低护甲（0级26% → 9级80%）
     * ⭐ 视觉：每 0.5 秒生成深绿酸液 + 粘液 + 黑曜石泪滴 + 酸液落地
     */
    public static final DynamicAttribute CORROSION = new DynamicAttribute("corrosion")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                // ⭐ 粒子走限流门控
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
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
     * 穿刺效果 - 挂着它的生物<b>造成</b>的伤害降低（0级40% → 3级70%，每级 +10%）
     * ⭐ 2026-09-25：原先是挂着它的生物<b>受到</b>的近战伤害降低 —— 玩家越打越打不动，方向反了；
     *    照《星际战甲》原作改成削弱它的输出。注释原写「3级80%」，按常量实际是 70%
     * ⭐ 视觉：每 0.5 秒生成白色针状 + 金属闪烁 + 浅银尘埃 + 发光点
     * ⭐ v7：减伤逻辑搬到 {@link ElementCombatHandler}，数值与触发条件不变。
     */
    public static final DynamicAttribute PUNCTURE = new DynamicAttribute("puncture")
            .setTickInterval(10)
            .onTick(ctx -> {
                LivingEntity entity = ctx.getEntity();
                // ⭐ 粒子走限流门控
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
                    ElementParticleEffects.spawnPunctureEffect(entity, sl, ctx.getAmplifier());
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
                // ⭐ 粒子走限流门控
                if (entity.level() instanceof ServerLevel sl && ParticleEmissionGuard.tryAcquire(sl, entity)) {
                    ElementParticleEffects.spawnVirusEffect(entity, sl, ctx.getAmplifier());
                }
            })
            .addModifier(Attributes.MAX_HEALTH, AttributeModifier.Operation.MULTIPLY_TOTAL, (level) -> {
                return level >= 9 ? -0.5 : 0.0;
            });

    // ========== 元素 debuff 的战斗效果（静态监听器）==========

    /**
     * 元素 debuff 战斗效果处理器
     *
     * <p>⭐ 这里取代了原先的 {@code withEventHandler}「运行时往事件总线动态 register 监听器」方案。</p>
     *
     * <p>旧方案的问题：
     * <ol>
     *   <li>每施加一次磁力 / 辐射 / 穿刺就 register 一个绑定实体 UUID 的匿名对象，
     *       怪基本都在 debuff 到期前被打死，死后不再 tick，自然过期的反注册永远不会执行；
     *       {@code DynamicAttributeManager.apply} 的 orphan 分支更是连反注册的机会都没有。
     *       挂机刷怪几小时，总线上能累积成千上万个僵尸监听器，
     *       <b>每一次</b> LivingHurtEvent 都要把它们全遍历一遍。</li>
     *   <li>走进 orphan 分支后 {@code getAmplifier} 恒返回 -1，三个效果<b>静默失效</b>，
     *       而客户端特效还在转——玩家看到"特效在转但 debuff 不生效"。</li>
     * </ol></p>
     *
     * <p>新方案：总线上永远只有这<b>一个</b>监听器，判定从「UUID 相等」换成完全等价的
     * 「{@code getAmplifier(...) >= 0}」（旧代码本来也要查一次等级，
     * 只是多了一层 UUID 绑定的壳）。三段效果的触发条件、判定顺序、数值公式逐条照搬，行为不变。</p>
     *
     * <p>三段效果都是对 {@code event.getAmount()} 做乘法，彼此独立、互不依赖，
     * 因此合并到同一个方法里按固定顺序执行，与旧方案下三个监听器的任意执行顺序等价，
     * 且省掉两次事件分发。</p>
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ElementCombatHandler {

        /** 磁力：有护盾时的增伤上限等级（0→9），可后续提为配置项 */
        private static final int MAGNETIC_MAX_LEVEL = 9;
        /** 磁力：每级额外增伤，可后续提为配置项 */
        private static final double MAGNETIC_PER_LEVEL = 0.25;

        /** 辐射：同类增伤上限等级（0→9），可后续提为配置项 */
        private static final int RADIATION_MAX_LEVEL = 9;
        /** 辐射：每级额外增伤，可后续提为配置项 */
        private static final double RADIATION_PER_LEVEL = 0.5;

        /** 穿刺：攻击方输出削减的上限等级（0→3），可后续提为配置项 */
        private static final int PUNCTURE_MAX_LEVEL = 3;
        /** 穿刺：基础减伤，可后续提为配置项 */
        private static final double PUNCTURE_BASE_REDUCTION = 0.4;
        /** 穿刺：每级额外减伤，可后续提为配置项 */
        private static final double PUNCTURE_PER_LEVEL = 0.1;

        /**
         * 元素 debuff 对伤害数值的三段修正
         *
         * @param event 生物受伤事件
         */
        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim == null || victim.level().isClientSide()) {
                return;
            }

            // 受击方身上的动态属性（磁力看受击方；穿刺、辐射看攻击方，在各自方法里查）
            List<DynamicAttributeInstance> victimAttributes = DynamicAttributeManager.getInstances(victim);
            if (victimAttributes != null) {
                applyMagnetic(event, victim, victimAttributes);
            }

            applyPuncture(event);
            applyRadiation(event, victim);
        }

        /**
         * 磁力：受击方有护盾（吸收伤害）时增伤
         * 等价于旧 {@code onMagneticHurt}：先判吸收量，再取等级，等级 &lt; 0 直接放弃
         *
         * @param event           生物受伤事件
         * @param victim          受击方
         * @param victimAttributes 受击方的动态属性列表
         */
        private static void applyMagnetic(LivingHurtEvent event, LivingEntity victim,
                                          List<DynamicAttributeInstance> victimAttributes) {
            if (victim.getAbsorptionAmount() <= 0) {
                return;
            }
            int level = DynamicAttributeManager.getAmplifier(victimAttributes, MAGNETIC);
            if (level < 0) {
                return;
            }
            double damageMultiplier = 1.0 + (1.0 + Math.min(level, MAGNETIC_MAX_LEVEL) * MAGNETIC_PER_LEVEL);
            event.setAmount(event.getAmount() * (float) damageMultiplier);
        }

        /**
         * 穿刺：<b>攻击方</b>身上挂着穿刺时，它造成的伤害降低
         *
         * <p>⭐ 2026-09-25 改方向：原先判的是受击方（挂穿刺的目标被生物近战打中时减伤），
         * 玩家给怪挂上穿刺反而削弱自己的近战。现在看伤害归属者（{@code getEntity()}，
         * 含它射出的箭、火球等），与原作「穿刺削弱敌人输出」一致；没有归属者的伤害（摔落、DoT）不受影响。</p>
         *
         * @param event 生物受伤事件
         */
        private static void applyPuncture(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
                return;
            }
            // 溅射以主目标最终伤害为基数：主目标那一下（本监听器 NORMAL 先于 WeaponCombatHandler 的 LOWEST）
            // 已经按攻击方的穿刺削过，溅射再削一次就成了 (1-r)²
            if (pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler.isApplyingSplash()) {
                return;
            }
            int level = DynamicAttributeManager.getAmplifier(attacker, PUNCTURE);
            if (level < 0) {
                return;
            }
            double reduction = PUNCTURE_BASE_REDUCTION + Math.min(level, PUNCTURE_MAX_LEVEL) * PUNCTURE_PER_LEVEL;
            event.setAmount(event.getAmount() * (float) (1.0 - reduction));
        }

        /**
         * 辐射：<b>攻击方</b>带辐射且打的是同类型生物时增伤
         * 等价于旧 {@code onAttack}（那里绑定的是攻击方的 UUID，不是受击方）
         *
         * <p>⭐ 旧代码把 {@code event.getSource().getEntity()} 直接强转成 LivingEntity，
         * 只因为它一定等于当初绑定的那个 LivingEntity 才没炸；
         * 静态监听器没有这层保证，这里必须用 instanceof 兜住（箭矢、TNT 等非生物伤害源）。</p>
         *
         * @param event  生物受伤事件
         * @param victim 受击方
         */
        private static void applyRadiation(LivingHurtEvent event, LivingEntity victim) {
            Entity sourceEntity = event.getSource().getEntity();
            if (!(sourceEntity instanceof LivingEntity attacker)) {
                return;
            }
            if (!victim.getType().equals(attacker.getType())) {
                return;
            }
            int level = DynamicAttributeManager.getAmplifier(attacker, RADIATION);
            if (level < 0) {
                return;
            }
            double damageMultiplier = 1.0 + (1.0 + Math.min(level, RADIATION_MAX_LEVEL) * RADIATION_PER_LEVEL);
            event.setAmount(event.getAmount() * (float) damageMultiplier);
        }

        private ElementCombatHandler() {
        }
    }
}
