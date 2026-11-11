package pers.roinflam.kuvalich.gametest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.module.warframe.WarframeEffectHandler;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 战甲战利品倍率（{@code WarframeEffectHandler.onLivingDrops}）：同一种物品不同 NBT 各算各的，
 * 以及死者带「掉落归属玩家」标记时按标记上的玩家算。
 *
 * <p>直接造一个掉落事件交给处理方法，不真打死怪：结果只看事件里的掉落列表。
 * 战甲词条按 tick 缓存，改完模组隔一 tick 再算。
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootDropTests {

    private static final String BATCH = "kl_loot_drops";

    /** 写标记的一方用的键名，和 WarframeEffectHandler 里写死的一致 */
    private static final String LOOT_OWNER_KEY = "LootOwner";

    private LootDropTests() {
    }

    /** 在战甲第一格放一张只带 itemDropMultiplier 的模组（raw 0 清空）。配置默认生效 100%，raw 1 就是 ×2 */
    private static void dropModule(ServerPlayer p, double raw) {
        ItemStack stack = ItemStack.EMPTY;
        if (raw != 0) {
            stack = new ItemStack(Items.PAPER);
            CompoundTag mt = stack.getOrCreateTagElement("kuvalich_modules");
            mt.putInt("ModuleLevel", 10);
            ListTag list = new ListTag();
            CompoundTag a = new CompoundTag();
            a.putString("attributeType", "itemDropMultiplier");
            a.putDouble("attributeValue", raw);
            list.add(a);
            mt.put("attributeList", list);
        }
        ItemStack module = stack;
        p.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(m -> m.setOne(module));
    }

    private static ItemStack tagged(int count, String key) {
        ItemStack s = new ItemStack(Items.GOLD_NUGGET, count);
        s.getOrCreateTag().putString("kind", key);
        return s;
    }

    private static Zombie victim(GameTestHelper h) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        z.setPos(at.x, at.y, at.z);
        return z;
    }

    /** 跑一次处理方法，返回处理后的掉落 */
    private static List<ItemEntity> run(Zombie z, DamageSource source, ItemStack... stacks) {
        List<ItemEntity> drops = new ArrayList<>();
        for (ItemStack s : stacks) {
            drops.add(new ItemEntity(z.level(), z.getX(), z.getY(), z.getZ(), s));
        }
        LivingDropsEvent evt = new LivingDropsEvent(z, source, drops, 0, true);
        WarframeEffectHandler.onLivingDrops(evt);
        return new ArrayList<>(evt.getDrops());
    }

    /** 掉落里 NBT kind = key 的金粒合计（key 为 null 统计不带 NBT 的） */
    private static int count(List<ItemEntity> drops, String key) {
        int n = 0;
        for (ItemEntity e : drops) {
            ItemStack s = e.getItem();
            if (!s.is(Items.GOLD_NUGGET)) {
                continue;
            }
            String k = s.getTag() == null ? null : s.getTag().getString("kind");
            if (key == null ? s.getTag() == null : key.equals(k)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int countItem(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : drops) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    /**
     * 同一种物品不同 NBT 的几组各算各的（以前按物品种类合在一起，前一组拿走后一组的数量，倍率 1 也串）；
     * 同 NBT 的分成几个实体照旧合起来算；装备不翻倍。
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 60)
    public static void nbtGroupsDoNotMix(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        dropModule(p, 0);
        h.runAfterDelay(2, () -> {
            DamageSource src = h.getLevel().damageSources().playerAttack(p);
            List<ItemEntity> r1 = run(victim(h), src, tagged(10, "a"), tagged(30, "b"), tagged(5, "a"), new ItemStack(Items.GOLD_NUGGET, 7));
            h.assertTrue(count(r1, "a") == 15, "倍率 1：a 应为 10 + 5 = 15，实际 " + count(r1, "a"));
            h.assertTrue(count(r1, "b") == 30, "倍率 1：b 应为 30（不能被 a 拿走），实际 " + count(r1, "b"));
            h.assertTrue(count(r1, null) == 7, "倍率 1：不带 NBT 的应为 7，实际 " + count(r1, null));

            dropModule(p, 1.0);
            h.runAfterDelay(2, () -> {
                List<ItemEntity> r2 = run(victim(h), src, tagged(10, "a"), tagged(30, "b"), tagged(5, "a"),
                        new ItemStack(Items.GOLD_NUGGET, 7), new ItemStack(Items.IRON_SWORD));
                h.assertTrue(count(r2, "a") == 30, "×2：a 应为 30，实际 " + count(r2, "a"));
                h.assertTrue(count(r2, "b") == 60, "×2：b 应为 60，实际 " + count(r2, "b"));
                h.assertTrue(count(r2, null) == 14, "×2：不带 NBT 的应为 14，实际 " + count(r2, null));
                h.assertTrue(countItem(r2, Items.IRON_SWORD) == 1, "装备不翻倍：铁剑应为 1，实际 " + countItem(r2, Items.IRON_SWORD));

                // 溢出分堆照同组的 NBT 生成：a 50 ×2 = 100，超过一组，分出来的那堆也得是 a
                List<ItemEntity> r3 = run(victim(h), src, tagged(50, "a"), tagged(3, "b"));
                h.assertTrue(count(r3, "a") == 100, "×2 溢出：a 应为 100，实际 " + count(r3, "a"));
                h.assertTrue(count(r3, "b") == 6, "×2 溢出：b 应为 6，实际 " + count(r3, "b"));
                h.assertTrue(r3.size() == 3, "×2 溢出：应为 a 两堆 + b 一堆，实际 " + r3.size() + " 堆");
                h.succeed();
            });
        });
    }

    /**
     * 掉落归属玩家标记：有标记按标记上的玩家（在线）算，不看最后一击；标记上的人不在线就不乘；没标记照旧按最后一击的玩家。
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 60)
    public static void lootOwnerReplacesKiller(GameTestHelper h) {
        ServerPlayer owner = KuvaTestKit.player(h);
        ServerPlayer other = KuvaTestKit.player(h);
        dropModule(owner, 1.0);
        dropModule(other, 0);
        h.runAfterDelay(2, () -> {
            ServerLevel level = h.getLevel();
            DamageSource byOwner = level.damageSources().playerAttack(owner);
            DamageSource byOther = level.damageSources().playerAttack(other);
            DamageSource env = level.damageSources().generic();

            // 没标记：按最后一击
            h.assertTrue(count(run(victim(h), byOwner, tagged(10, "a")), "a") == 20, "没标记、有词条的人打死：应为 20");
            h.assertTrue(count(run(victim(h), byOther, tagged(10, "a")), "a") == 10, "没标记、没词条的人打死：应为 10");
            h.assertTrue(count(run(victim(h), env, tagged(10, "a")), "a") == 10, "没标记、环境伤害：应为 10");

            // 标记给有词条的人，别人补刀 / 环境伤害打死：照样乘
            Zombie z1 = victim(h);
            z1.getPersistentData().putUUID(LOOT_OWNER_KEY, owner.getUUID());
            h.assertTrue(count(run(z1, byOther, tagged(10, "a")), "a") == 20, "归属有词条、别人最后一击：应为 20");
            Zombie z2 = victim(h);
            z2.getPersistentData().putUUID(LOOT_OWNER_KEY, owner.getUUID());
            h.assertTrue(count(run(z2, env, tagged(10, "a")), "a") == 20, "归属有词条、环境伤害打死：应为 20");

            // 标记给没词条的人，有词条的人最后一击：不乘
            Zombie z3 = victim(h);
            z3.getPersistentData().putUUID(LOOT_OWNER_KEY, other.getUUID());
            h.assertTrue(count(run(z3, byOwner, tagged(10, "a")), "a") == 10, "归属没词条、有词条的人最后一击：应为 10");

            // 标记上的人不在线：不乘，也不退回最后一击的人
            Zombie z4 = victim(h);
            z4.getPersistentData().putUUID(LOOT_OWNER_KEY, UUID.randomUUID());
            h.assertTrue(count(run(z4, byOwner, tagged(10, "a")), "a") == 10, "归属玩家不在线：应为 10");
            h.succeed();
        });
    }

    @AfterBatch(batch = BATCH)
    public static void afterLoot(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
