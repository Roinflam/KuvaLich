package pers.roinflam.kuvalich.module.warframe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributes;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.network.packet.WarframeModuleSyncPacket;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战甲效果事件处理器
 * 负责护盾恢复、生命值/护甲系统、伤害抗性、跳跃增益、掉落物、治疗、挖掘速度等
 *
 * 重构：使用 WarframeModuleSyncPacket 替代 DiggingSpeedPacket
 *    客户端 BreakSpeed 恢复使用，与服务端使用完全相同的属性计算逻辑
 *    同步时机：登录、重生/维度传送、击杀叠层变化、每5tick脏检测
 *
 * 第三批新词条：枪械战利品掉落（gun_loot_drop，武器专属，仅 TACZ 子弹击杀生效）在
 *    onLivingDrops 中加法叠加到战甲的 itemDropMultiplier 上，与战甲共享同一套配置生效倍率。
 *
 * NPC 掉落放行：onLivingDrops 的实体类型判断新增自定义NPC模组(CustomNPCs)实体放行，
 *    使战甲 itemDropMultiplier 与武器 gun_loot_drop 对 NPC 掉落同样生效。
 *    通过注册表命名空间判断（见 {@link #isCustomNpc}），不引用该模组的 Java 类，
 *    因此本项目无需额外声明对自定义NPC模组的编译期依赖。
 *
 * 装备判断口径：onLivingDrops 的装备判断由「盔甲/剑/有阶工具」扩大为一切装备、武器、工具
 *    （见 {@link #isEquipment}），与常见的掉落加成模组 / 插件口径一致，
 *    确保同一次击杀中几套掉落倍率对物品的取舍相同。
 *
 * 本次改动：onLivingDrops 不再把普通生物的掉落钳到「最多一组」，
 *    超出堆叠上限的部分另起 ItemEntity 掉成好几堆；
 *    自定义NPC 的掉落仍照旧钳。
 *
 * <p>并发安全修复：{@code cooldownTicks} 由 {@link HashMap} 改为
 * {@link ConcurrentHashMap}，并加 {@code final} 防止被外部重新赋值。
 * 该 Map 是 public static 跨玩家共享的，在伤害事件与 tick 事件中并发读写，
 * 与 WeaponModuleHandler / WarframeModuleHandler 的属性缓存属于同一类风险：
 * 在 Mohist 这类混合端上被插件线程触碰时，可能在扩容时形成链表环，
 * 表现为主线程 CPU 100% 且不抛任何异常。</p>
 */
@Mod.EventBusSubscriber
public class WarframeEffectHandler {

    /**
     * 护盾恢复冷却（UUID → 剩余秒数）
     * <p>必须使用并发容器：public static 跨线程共享，且在多个事件处理器中并发读写。</p>
     */
    public static final Map<UUID, Integer> cooldownTicks = new ConcurrentHashMap<>();

    /**
     * 固定属性 AttributeModifier 的 UUID
     */
    private static final UUID FIXED_HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-1111-2222-3333-444444444444");
    private static final UUID FIXED_ARMOR_MODIFIER_UUID = UUID.fromString("a1b2c3d4-5555-6666-7777-888888888888");

    /**
     * TACZ 动能子弹实体类全限定名（用类名字符串判定，避免硬依赖 TACZ）
     * TACZ kinetic bullet entity FQN (matched by class name string to avoid hard dependency on TACZ)
     */
    private static final String TACZ_BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";

    /**
     * 自定义NPC模组(CustomNPCs)的注册表命名空间
     * <p>用命名空间字符串判定，避免对该模组产生硬编译依赖；未安装时安全返回 false。</p>
     */
    private static final String CUSTOM_NPC_NAMESPACE = "customnpcs";

    /**
     * 掉落归属玩家标记的键名（死者 {@code getPersistentData()} 里，值为玩家 UUID），见 {@link #lootOwner}。
     * 写标记的一方写死同一个字符串，改名要一起改。
     */
    private static final String LOOT_OWNER_KEY = "LootOwner";

    // ========== 实体加入世界 / Entity Join Level ==========

    @SubscribeEvent
    public static void onEntityJoinLevel(@Nonnull EntityJoinLevelEvent evt) {
        if (!evt.getLevel().isClientSide() && evt.getEntity() instanceof Player) {
            // 预留位置：可以在这里初始化玩家数据
        }
    }

    // ========== 同步触发点：登录 ==========

    /**
     * 玩家登录时同步战甲模组数据到客户端
     * 延迟1tick确保 Capability 已就绪
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getServer().execute(() -> {
            WarframeModuleSyncPacket.syncToPlayer(player);
        });
    }

    // ========== 同步触发点：重生/维度传送 ==========

    /**
     * 玩家 Clone 事件后同步战甲模组数据
     * 使用 LOW 优先级确保在 CapabilityRegistryHandler 的克隆逻辑之后执行
     * 延迟1tick确保新实体的 Capability 已完全初始化
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getServer().execute(() -> {
            WarframeModuleSyncPacket.syncToPlayer(player);
        });
    }

    // ========== 伤害事件 / Damage Events ==========

    /**
     * 玩家受伤/攻击时的战甲属性处理
     * - 受伤时：应用各种抗性、设置护盾冷却
     * - 攻击时：设置护盾冷却、击杀检测
     * 击杀时增加叠层后立即同步到客户端
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
                int cooldown = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    cooldown *= 3; // 护盾破碎时冷却时间x3
                }
                cooldownTicks.put(player.getUUID(), cooldown);

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
                int cooldown = (int) (10 * delayMultiplier);
                if (player.getAbsorptionAmount() <= 0) {
                    cooldown *= 3;
                }
                cooldownTicks.put(player.getUUID(), cooldown);

                // 击杀检测
                if (evt.getEntity().getHealth() - evt.getAmount() <= 0) {
                    WarframeModuleHandler.addWarframeKillStacks(player);
                    // 击杀叠层变化后立即同步到客户端
                    if (player instanceof ServerPlayer serverPlayer) {
                        WarframeModuleSyncPacket.syncToPlayer(serverPlayer);
                    }
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

    /**
     * 怪物掉落事件处理
     * <p>
     * 当玩家击杀动物、怪物或自定义NPC(CustomNPCs)时，根据战甲模组的 itemDropMultiplier 属性
     * 和配置中的 itemDropEffectMultiplier 缩放掉落物数量。
     * <p>
     * 第三批新增：若击杀来源为 TACZ 枪械子弹，则把玩家主手武器的 gun_loot_drop 词条
     *    加法叠加到 itemDropMultiplier 原始值上，与战甲共享同一套生效倍率与缩放逻辑。
     * <p>
     * 上次新增：自定义NPC不继承 Animal/Monster，通过 {@link #isCustomNpc} 按注册表命名空间
     *    "customnpcs" 判断后放行，使 itemDropMultiplier 与 gun_loot_drop 对 NPC 掉落同样生效。
     *    该判断置于 Animal/Monster 之后，普通生物走短路不产生额外注册表查询开销。
     * <p>
     * 缩放后倍率 &lt; 1 时按概率决定是否掉落（每组独立判定）。
     * <p>
     * 装备判断：装备判断改为调用 {@link #isEquipment}，覆盖范围由「盔甲/剑/有阶工具」
     *    扩大为一切装备、武器、工具。两个分支的语义保持不变——
     *    倍率 &lt;= 0 时装备仍被完整保留、只清除非装备掉落；倍率 &gt; 0 时装备不参与数量缩放。
     * <p>
     * 本次改动：「最多一组」上界已全面取消，普通生物（Animal / Monster）
     *    与自定义NPC（{@link #isCustomNpc}）一视同仁：词条堆到多少就掉多少；
     *    现有 ItemEntity 装不下的余量另起新实体，每堆不超过该物品的堆叠上限，
     *    而不再把余量堆成一个超堆叠的实体。
     *
     * @param evt 掉落事件
     */
    /**
     * 必须是 {@link EventPriority#LOWEST}：本方法要在<b>所有</b>改掉落数量的模组之后跑。
     *
     * <p>别的模组也常在同一个事件上挂一条战利品倍率，优先级 NORMAL、
     * 在 {@code onServerStarting} 才注册。而本模组走 {@code @Mod.EventBusSubscriber} 在 mod 构造期注册，
     * 同优先级下按注册顺序派发，
     * 结果是本模组<b>先</b>跑。先跑的一方无论怎么钳制总量，后跑的那一方都会在钳好的结果上
     * 再乘一次，钳制形同虚设（10 腐肉 → 本模组夹到 64 → 别家 ×5 → 320）。
     * 改成 LOWEST 之后本模组最后跑，看到的是各家都乘完的结果，这时候钳才钳得住。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent evt) {
        if (evt.getEntity().level().isClientSide()) {
            return;
        }
        if (!(evt.getEntity() instanceof Animal || evt.getEntity() instanceof Monster || isCustomNpc(evt.getEntity()))) {
            return;
        }
        Entity lastHit = evt.getSource().getEntity();
        Player player;
        if (evt.getEntity().getPersistentData().hasUUID(LOOT_OWNER_KEY)) {
            // 掉落归属玩家：按他的词条算，不看最后一击是谁；他不在线就不乘
            player = lootOwner(evt.getEntity());
            if (player == null) {
                return;
            }
        } else if (lastHit instanceof Player killer) {
            player = killer;
        } else {
            return;
        }

        HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
        WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

        // 从模组获取原始 itemDropMultiplier 值
        double rawModuleValue = attributes.getOrDefault("itemDropMultiplier", 0.0);

        // 枪械战利品掉落：仅 TACZ 枪械击杀时叠加武器词条 gun_loot_drop（加法合并，与战甲共享配置倍率）。
        // 只认这次伤害来源的玩家自己的枪：掉落归属给了别人时（别人补的刀），只算归属玩家的战甲词条
        // 按 UUID 比：重生、重连后伤害来源上挂的可能还是旧的玩家对象
        if (lastHit != null && lastHit.getUUID().equals(player.getUUID())) {
            rawModuleValue += getGunLootDropValue(evt.getSource(), player);
        }

        // 应用配置中的生效倍率百分比
        double effectPercent = ModConfig.KUVA_LICH.itemDropEffectMultiplier.get() / 100.0;
        double scaledModuleValue = rawModuleValue * effectPercent;

        // 最终掉落倍率 = 1 + 缩放后的模组值
        double itemDropMultiplier = 1.0 + scaledModuleValue;

        Collection<ItemEntity> drops = evt.getDrops();

        // 倍率 <= 0 时：清除所有非装备掉落物（装备、武器、工具原样保留）
        if (itemDropMultiplier <= 0) {
            drops.removeIf(drop -> !isEquipment(drop.getItem()));
            return;
        }

        // 倍率 > 0 时：缩放掉落物数量（装备、武器、工具不参与缩放）
        //
        // 不再设任何上界 —— 词条堆到多少就掉多少，
        //    超出堆叠上限的部分另起 ItemEntity 掉成好几堆（见下面的溢出分堆）。
        //    自定义NPC 曾经钳到 max(介入前总量, 堆叠上限)，现在跟普通生物走同一条路：
        //    两边都能超过一组。
        //    代价是装了别的掉落倍率时几套倍率会连乘：杀一只僵尸掉 10 腐肉，
        //    别家 ×5 变 50，战甲再 ×10 就是 500，也就是 8 堆。
        //    要收紧只能调配置里的 itemDropEffectMultiplier。
        //
        //    总量只能按「同一种物品」汇总来算：先跑的模组
        //    会把超堆叠的量拆成好几个 ItemEntity 再塞回列表，
        //    只看单个实体的 count 根本看不出总量。
        //
        //    「同一种」按物品 + NBT 认（ItemStack#isSameItemSameTags，和能不能叠成一堆是同一个口径）：
        //    只按物品种类合并时，同一种物品 NBT 不同的几组（不同口径的弹药、不同药水、不同附魔书）会被加成一个总数，
        //    再按列表顺序整份分回去 —— 前面那组拿走了后面那组的数量，后面那组变成 0，倍率是 1 也一样。

        List<DropGroup> groups = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack dropStack = drop.getItem();
            if (isEquipment(dropStack)) {
                continue;
            }
            DropGroup group = DropGroup.find(groups, dropStack);
            if (group == null) {
                group = new DropGroup(dropStack.copy());
                groups.add(group);
            }
            group.before += dropStack.getCount();
        }

        for (DropGroup group : groups) {
            double scaled = group.before * itemDropMultiplier;
            int scaledCount = (int) scaled;
            if (Math.random() < scaled - scaledCount) {
                scaledCount++;
            }
            group.remaining = Math.max(0, scaledCount);
        }

        // 把总量按原有实体逐个分配回去
        //
        // 收尾必须<b>按组各记各的</b>最后一个实体。
        //    这里原先只用了一个 lastOf 变量，循环里被逐个覆盖，结束时它代表的是
        //    「整批掉落里最后一个非装备实体」而不是「某一组物品的最后一个实体」。
        //    一次掉出两种以上物品、而需要补余量的那种恰好不是最后遍历到的那种时，
        //    多出来的数量会被静默丢弃 —— 玩家只会看到掉落莫名偏少，没有任何报错。
        for (ItemEntity drop : drops) {
            ItemStack dropStack = drop.getItem();
            if (isEquipment(dropStack)) {
                continue;
            }
            // 找组要在 setCount 之前：数量改成 0 的物品是空的，就认不出是哪一组了
            DropGroup group = DropGroup.find(groups, dropStack);
            if (group == null) {
                // 同一份物品在列表里出现两次、前一次已经分成 0 了
                continue;
            }
            int left = group.remaining;
            int give = Math.min(left, dropStack.getMaxStackSize());
            group.lastEntity = drop;
            // 复制必须在 setCount 之前：这份原型要拿去做溢出分堆，
            //    改完 count 再复制就只能得到被截断后的数量，而且附魔/自定义名等 NBT 也要跟着走。
            group.lastStack = dropStack.copy();
            dropStack.setCount(give);
            group.remaining = left - give;
        }

        // 溢出分堆：现有实体按各自堆叠上限装不下的余量，另起新的 ItemEntity 掉出来，
        //    每堆都不超过该物品的堆叠上限。
        //
        //    原先是把余量全加到该物品最后一个实体上、让它的 count 超过堆叠上限
        //    （原版 ItemEntity 容得下，玩家捡起时 Inventory#add 会自动分摊到多个格子）。
        //    改成分堆有两个理由：
        //    · 要的就是「不止一组」—— 地上看得见好几堆才是这个意思，
        //      一个 count=500 的实体在地上仍然只是一小堆。
        //    · count 超堆叠上限的 ItemEntity 在 Mohist 这类混合端上要过一遍
        //      CraftItemStack 转换，超量部分不保证能活着回来。
        List<ItemEntity> spilled = new ArrayList<>();
        for (DropGroup group : groups) {
            int left = group.remaining;
            ItemStack proto = group.lastStack;
            ItemEntity template = group.lastEntity;
            if (left <= 0 || proto == null || proto.isEmpty() || template == null) {
                continue;
            }
            int perStack = Math.max(1, proto.getMaxStackSize());
            while (left > 0) {
                int give = Math.min(left, perStack);
                ItemStack piece = proto.copy();
                piece.setCount(give);
                ItemEntity extra = new ItemEntity(template.level(),
                        template.getX(), template.getY(), template.getZ(), piece);
                extra.setDefaultPickUpDelay();
                spilled.add(extra);
                left -= give;
            }
        }

        // Forge 在事件返回 false 之后会 drops.forEach(level::addFreshEntity)，
        // 所以加进这个集合的新实体照常生成，不需要自己 addFreshEntity。
        drops.addAll(spilled);
        drops.removeIf(drop -> drop.getItem().isEmpty());
    }

    /**
     * 掉落倍率里的一组：物品和 NBT 都相同（{@link ItemStack#isSameItemSameTags}）的掉落合在一起算总数。
     */
    private static final class DropGroup {
        /** 认组用的样本（第一次见到时的副本，之后不改） */
        final ItemStack key;
        /** 乘倍率之前这一组的总数 */
        int before;
        /** 乘完还没分出去的数量 */
        int remaining;
        /** 这一组最后一个实体和它改数量之前的副本，溢出分堆照它的位置和 NBT 生成 */
        ItemEntity lastEntity;
        ItemStack lastStack;

        DropGroup(ItemStack key) {
            this.key = key;
        }

        /** 一次掉落里的组数很少，线性找就够 */
        static DropGroup find(List<DropGroup> groups, ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            for (DropGroup group : groups) {
                if (ItemStack.isSameItemSameTags(group.key, stack)) {
                    return group;
                }
            }
            return null;
        }
    }

    /**
     * 掉落归属玩家：死者 {@code getPersistentData()} 里 {@link #LOOT_OWNER_KEY} 记的玩家 UUID，取这个玩家（要在线）。
     *
     * <p>有的玩法里战利品不归最后一击的人（例如整份归伤害最高的人），这时由那个玩法在掉落事件更早的优先级写上这个标记，
     * 掉落倍率就按标记上的人的战甲词条算。本模组只读不写；没有标记时照旧按最后一击的玩家。
     *
     * @return 在线的那个玩家；不在线返回 null
     */
    private static Player lootOwner(Entity victim) {
        UUID owner = victim.getPersistentData().getUUID(LOOT_OWNER_KEY);
        if (victim.getServer() == null) {
            return null;
        }
        return victim.getServer().getPlayerList().getPlayer(owner);
    }

    /**
     * 判断实体是否为自定义NPC模组(CustomNPCs)的实体。
     * <p>
     * 通过注册表ID的命名空间判断（"customnpcs:xxx"），不直接引用该模组的 Java 类，
     * 避免本项目需要额外声明对自定义NPC模组的编译期依赖；未安装该模组时安全返回 false。
     *
     * @param entity 待判断的实体（可为 null）
     * @return true=是自定义NPC模组的实体
     */
    private static boolean isCustomNpc(Entity entity) {
        if (entity == null) { return false; }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && CUSTOM_NPC_NAMESPACE.equals(id.getNamespace());
    }

    /**
     * 判断物品是否为装备、武器或工具（一律不参与掉落数量缩放）。
     * <p>
     * 判定顺序：
     * <ol>
     *   <li>耐久度判断（主）：getMaxDamage() &gt; 0 的物品视为装备工具。
     *       ItemStack#getMaxDamage() 经 Forge 修补后会转调 Item#getMaxDamage(ItemStack)，
     *       模组自定义耐久的物品也能正确识别，无需逐个枚举模组类；
     *       同时不受 NBT 中 Unbreakable 标签影响（该标签只影响 isDamageableItem）。</li>
     *   <li>类型兜底：覆盖被设为无限耐久（maxDamage=0）的装备工具，
     *       包含盔甲、鞘翅、盾牌、有阶工具与剑、弓弩、三叉戟、剪刀、钓竿。</li>
     * </ol>
     * 头颅、南瓜等可穿戴但属于战利品材料的物品不在此范围内，仍会正常参与缩放。
     * <p>
     * 本方法与常见的掉落加成模组 / 插件用同一套判定口径，
     * 改口径时要留意，
     * 否则同一次击杀中几套掉落倍率对物品的取舍会不同。
     *
     * @param stack 掉落物品
     * @return true=是装备/武器/工具，跳过缩放
     */
    private static boolean isEquipment(ItemStack stack) {
        if (stack.isEmpty()) { return false; }

        // 1. 有耐久度的物品一律视为装备工具
        if (stack.getMaxDamage() > 0) { return true; }

        // 2. 无耐久度的装备工具类型兜底
        Item item = stack.getItem();
        return item instanceof ArmorItem
                || item instanceof ElytraItem
                || item instanceof ShieldItem
                || item instanceof TieredItem
                || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem
                || item instanceof ShearsItem
                || item instanceof FishingRodItem;
    }

    /**
     * 获取本次击杀对应的枪械战利品掉落加成（gun_loot_drop）。
     * <p>
     * 仅当伤害来源的直接实体为 TACZ 动能子弹时才生效，读取玩家主手武器的 gun_loot_drop 词条值。
     * 通过类名字符串判定 TACZ 子弹，避免对 TACZ 产生硬编译依赖；未安装 TACZ 时安全返回 0。
     *
     * @param source 伤害来源
     * @param player 击杀者
     * @return 枪械战利品掉落加成值（加法叠加到 itemDropMultiplier），不满足条件时返回 0
     */
    private static double getGunLootDropValue(DamageSource source, Player player) {
        if (source == null) { return 0.0; }
        var direct = source.getDirectEntity();
        // 类名字符串判定 TACZ 子弹，避免直接引用 TACZ 类
        if (direct == null || !TACZ_BULLET_CLASS.equals(direct.getClass().getName())) {
            return 0.0;
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !WeaponModuleHandler.hasBase(weapon)) { return 0.0; }
        return WeaponModuleHandler.getWeaponAttributes(weapon).getOrDefault("gun_loot_drop", 0.0);
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

    /**
     * 挖掘速度事件处理
     *
     * 客户端/服务端使用完全相同的计算路径：
     *    getCachedAttributes → applyWarframeKillStackEffects → 读取 diggingSpeed
     *    客户端数据来源为 WarframeModuleSyncPacket 同步的缓存，
     *    与服务端使用相同计算逻辑，天然一致，不会回弹。
     *
     * @param evt 挖掘速度事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        Player player = evt.getEntity();

        // 双端统一计算路径（客户端使用同步缓存，服务端使用实时数据）
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
                        if (cooldownTicks.containsKey(player.getUUID())) {
                            if (cooldownTicks.get(player.getUUID()) > 1) {
                                cooldownTicks.put(player.getUUID(),
                                        cooldownTicks.get(player.getUUID()) - 1);
                            } else {
                                cooldownTicks.remove(player.getUUID());
                            }
                        } else {
                            HashMap<String, Double> attributes = WarframeModuleHandler.getCachedAttributes(player);
                            WarframeModuleHandler.applyWarframeKillStackEffects(player, attributes);

                            double shield = attributes.getOrDefault("shield", 0.0);
                            double fixedShield = attributes.getOrDefault("fixedShield", 0.0);

                            if (fixedShield > 0) {
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
                                double shieldCap = player.getMaxHealth() * shield * ModConfig.KUVA_LICH.shieldCapMultiplier.get();
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

                    // ═══ 每0.25秒：属性效果 + 战甲模组同步 + 固定上限 ═══
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

                        // 战甲模组同步（替代原 DiggingSpeedPacket）
                        // 定期脏检测：模组槽变更、击杀叠层衰减等变化会被捕获
                        // 击杀时的叠层增加已在 onLivingDamage 中立即同步
                        if (player instanceof ServerPlayer serverPlayer) {
                            WarframeModuleSyncPacket.syncIfChanged(serverPlayer);
                        }
                    }
                }
            }
        }
    }

    // ========== 玩家退出清理 / Player Logout Cleanup ==========

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) {
        WarframeModuleHandler.clearServerCache();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent evt) {
        UUID uuid = evt.getEntity().getUUID();
        cooldownTicks.remove(uuid);
        WarframeModuleHandler.cleanupCache(uuid);

        if (evt.getEntity().level().isClientSide()) {
            // 客户端：清理同步缓存
            WarframeModuleHandler.clearClientCache();
            KillStackManager.clearClientStacks();
        } else {
            // 服务端：清理状态追踪
            WarframeModuleSyncPacket.cleanupPlayer(uuid);
        }
    }
}
