package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.nemesis.Finale;
import pers.roinflam.kuvalich.nemesis.FinaleSettlement;
import pers.roinflam.kuvalich.nemesis.Inbox;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/** 终结的记账、落定、收件箱（C1b） */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinaleTests {

    private static final String LEDGER = "kl_finale_ledger";
    private static final String FAULT = "kl_finale_fault";
    private static final String TABLE = "kl_finale_table";
    private static final String SETTLE_FAULT = "kl_finale_settle_fault";
    private static final String BUILD_FAULT = "kl_finale_build_fault";
    private static final String RESET = "kl_finale_reset";
    private static final String STOP = "kl_finale_stop";
    private static final String DEATH = "kl_finale_death";
    private static final String GONE = "kl_finale_gone";
    private static final String DIM = "kl_finale_dim";
    private static final String STOPPING = "kl_finale_stopping";
    private static final String DIRECT = "kl_finale_direct";

    private FinaleTests() {
    }

    private static RequiemCard card(Player p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
    }

    /** 背包里加收件箱里一共有几个 */
    private static int owned(ServerPlayer p, Item item) {
        int n = p.getInventory().countItem(item);
        for (ItemStack s : PlayerLedger.getInbox(p)) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int inboxTotal(ServerPlayer p) {
        return PlayerLedger.getInbox(p).stream().mapToInt(ItemStack::getCount).sum();
    }

    private static Finale sample(int gen) {
        return new Finale(gen, 7L, "Vesk Arru", 3, 0, 20, "fire", "fire",
                List.of(new ItemStack(KuvaLichItems.KUVA.get(), 30)), List.of(new ItemStack(Items.DIAMOND, 2)),
                "minecraft:overworld", 0, 64, 0);
    }

    // ==================== 只动账本的 ====================

    @GameTest(template = "empty", batch = LEDGER)
    public static void resolveOnceOnlyAndChecksGeneration(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        card.setMinimumLevelWeapon(1);
        card.setMaximumLevelWeapon(2);
        PlayerLedger.setFinale(p, sample(5));

        h.assertTrue(FinaleSettlement.resolve(p, 4, FinaleSettlement.Choice.VANQUISH) == null && PlayerLedger.hasFinale(p),
                "代数对不上不该落定");
        h.assertTrue(FinaleSettlement.resolveNow(p, 5, FinaleSettlement.Choice.VANQUISH), "代数对上应当落定");
        int min = card.getMinimumLevelWeapon();
        int max = card.getMaximumLevelWeapon();
        h.assertTrue(!PlayerLedger.hasFinale(p) && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30
                        && p.getInventory().countItem(Items.DIAMOND) == 2
                        && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1 && !PlayerLedger.hasInbox(p),
                "落定后东西应当进背包：赤毒 30、归还的钻石 2、幻纹 1");
        h.assertTrue(min > 1 && max > 2, "武器等级上下限应当提了：" + min + " / " + max);
        ItemStack eph = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(KuvaLichItems.EPHEMERA.get())) {
                eph = s;
            }
        }
        h.assertTrue("Vesk Arru".equals(EphemeraItem.getOrigin(eph)), "幻纹应当写着源自哪只");

        h.assertTrue(!FinaleSettlement.resolveNow(p, 5, FinaleSettlement.Choice.VANQUISH), "第二次落定应当什么都不做");
        h.assertTrue(p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30 && card.getMinimumLevelWeapon() == min
                && card.getMaximumLevelWeapon() == max, "第二次不该多发、不该再提上下限");
        h.succeed();
    }

    @GameTest(template = "empty", batch = LEDGER)
    public static void creativeLastSlotLosesNothing(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        for (int i = 0; i < 35; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        PlayerLedger.addToInbox(p, List.of(new ItemStack(KuvaLichItems.KUVA.get(), 30)));
        Inbox.deliver(p, false);
        h.assertTrue(p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30 && !PlayerLedger.hasInbox(p),
                "创造模式只剩 1 格：30 个都该放进去");
        PlayerLedger.addToInbox(p, List.of(new ItemStack(KuvaLichItems.KUVA.get(), 100)));
        Inbox.deliver(p, false);
        int inInv = p.getInventory().countItem(KuvaLichItems.KUVA.get());
        h.assertTrue(inInv == 64 && inboxTotal(p) == 66, "再来 100 个：凑满那一格，其余留在收件箱：" + inInv + " + " + inboxTotal(p));
        h.succeed();
    }

    @GameTest(template = "empty", batch = LEDGER)
    public static void fullInventoryNeverDrops(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        PlayerLedger.setFinale(p, sample(2));
        h.assertTrue(FinaleSettlement.resolveNow(p, 2, FinaleSettlement.Choice.VANQUISH), "应当落定");
        AABB around = p.getBoundingBox().inflate(4.0D);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "背包满了也不该掉地上");
        h.assertTrue(inboxTotal(p) == 33, "全在收件箱里：" + inboxTotal(p));

        p.getInventory().clearContent();
        p.containerMenu = ChestMenu.threeRows(99, p.getInventory());
        FinaleSettlement.patrol(p, p.server.getTickCount());
        h.assertTrue(inboxTotal(p) == 33 && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 0,
                "开着别的容器界面时不投");
        p.containerMenu = p.inventoryMenu;
        FinaleSettlement.patrol(p, p.server.getTickCount());
        h.assertTrue(!PlayerLedger.hasInbox(p) && owned(p, KuvaLichItems.KUVA.get()) == 30
                && p.getInventory().countItem(Items.DIAMOND) == 2, "关了界面、有空位，下一次巡检到手");
        h.succeed();
    }

    @GameTest(template = "empty", batch = LEDGER)
    public static void loginWaitAppliesToCommandToo(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        PlayerLedger.addToInbox(p, List.of(new ItemStack(KuvaLichItems.KUVA.get(), 12)));
        LichDirector.onLogin(p);
        FinaleSettlement.patrol(p, p.server.getTickCount());
        h.assertTrue(inboxTotal(p) == 12, "登录 10 秒内巡检不投");
        int early = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "kuvalich inbox");
        h.assertTrue(early == 0 && inboxTotal(p) == 12 && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 0,
                "登录 10 秒内 /kuvalich inbox 也不投：" + early);
        Inbox.skipLoginDelay(p);
        int rc = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "kuvalich inbox");
        h.assertTrue(rc == 12 && !PlayerLedger.hasInbox(p) && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 12,
                "等待过后普通玩家用 /kuvalich inbox 应当马上收到：" + rc);
        int denied = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "kuvalich lich info " + p.getGameProfile().getName());
        h.assertTrue(denied == 0, "管理子命令对普通玩家仍然不可用");
        h.succeed();
    }

    @GameTest(template = "empty", batch = LEDGER)
    public static void unreadableFinaleItemsKeptInInbox(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        CompoundTag f = sample(6).save();
        CompoundTag ghost = new CompoundTag();
        ghost.putString("id", "klmissingmod:relic");
        ghost.putByte("Count", (byte) 1);
        f.getList("returns", Tag.TAG_COMPOUND).add(ghost.copy());
        f.getList("drops", Tag.TAG_COMPOUND).add(ghost.copy());
        PlayerLedger.putFinaleTag(p, f);

        h.assertTrue(FinaleSettlement.resolveNow(p, 6, FinaleSettlement.Choice.VANQUISH), "应当落定");
        h.assertTrue(!PlayerLedger.hasFinale(p) && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30
                && p.getInventory().countItem(Items.DIAMOND) == 2, "读得出的照常到手");
        ListTag inbox = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(PlayerLedger.ROOT)
                .getList("inbox", Tag.TAG_COMPOUND);
        int ghosts = 0;
        for (int i = 0; i < inbox.size(); i++) {
            if ("klmissingmod:relic".equals(inbox.getCompound(i).getString("id"))) {
                ghosts++;
            }
        }
        h.assertTrue(ghosts == 2 && inbox.size() == 2, "读不出来的两条原样留在收件箱：" + inbox);
        h.succeed();
    }

    // ==================== 落定中途抛异常 ====================

    @GameTest(template = "empty", batch = FAULT)
    public static void resolveFaultKeepsLedger(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        PlayerLedger.setFinale(p, sample(3));
        FinaleSettlement.faultBeforeCommit = () -> {
            throw new IllegalStateException("测试注入");
        };
        boolean threw = false;
        try {
            FinaleSettlement.resolveNow(p, 3, FinaleSettlement.Choice.VANQUISH);
        } catch (IllegalStateException e) {
            threw = true;
        } finally {
            FinaleSettlement.faultBeforeCommit = null;
        }
        h.assertTrue(threw && PlayerLedger.hasFinale(p) && !PlayerLedger.hasInbox(p)
                && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 0, "抛在计算段：账还在、箱子没多、背包没多");
        h.assertTrue(FinaleSettlement.resolveNow(p, 3, FinaleSettlement.Choice.VANQUISH)
                && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30, "重试一次到手");
        h.assertTrue(!FinaleSettlement.resolveNow(p, 3, FinaleSettlement.Choice.VANQUISH)
                && p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 30, "只发一次");
        h.succeed();
    }

    @AfterBatch(batch = FAULT)
    public static void afterFault(ServerLevel level) {
        FinaleSettlement.faultBeforeCommit = null;
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = LEDGER)
    public static void afterLedger(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 定案：掉落表、模拟 S1 登录补落定 ====================

    @GameTest(template = "empty", batch = TABLE, timeoutTicks = 100)
    public static void vanquishTableAndLoginResolve(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        KuvaTestKit.override(ModConfig.KUVA_LICH.masterKuvaMinAmount, 200);
        KuvaTestKit.override(ModConfig.KUVA_LICH.masterKuvaMaxAmount, 200);
        KuvaTestKit.override(ModConfig.KUVA_LICH.masterRivenSliverMinAmount, 3);
        KuvaTestKit.override(ModConfig.KUVA_LICH.masterRivenSliverMaxAmount, 3);
        KuvaTestKit.override(ModConfig.KUVA_LICH.requiemUltimatumDropChance, 100.0D);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        h.runAfterDelay(2, () -> {
            MercyCoreTests.setCards(card, 3);
            card.addConfiscatedItem(new ItemStack(Items.DIAMOND, 5));
            card.addConfiscatedItem(new ItemStack(Items.APPLE, 3));
            int gen = card.getLichGeneration();
            Item weapon = KuvaLichItems.KUVA_WEAPONS.get(card.getLichWeapon());
            int weaponLevel = card.getLichWeaponLevel();
            String weaponType = card.getLichWeaponType();
            String ephemera = card.getLichEphemera();
            int minBefore = card.getMinimumLevelWeapon();
            int maxBefore = card.getMaximumLevelWeapon();
            h.assertTrue(!ephemera.isEmpty(), "概率 1 时应当带幻纹");

            lich.computeAndApplySuccess(p, card, lich.blockPosition());
            Finale f = PlayerLedger.getFinale(p);
            h.assertTrue(f != null && f.generation() == gen && ephemera.equals(f.ephemera()) && !card.hasLich(),
                    "定案：账写好、这一轮结束");
            h.assertTrue(card.getMinimumLevelWeapon() == minBefore && card.getMaximumLevelWeapon() == maxBefore,
                    "上下限留到落定再提");
            h.assertTrue(!card.hasConfiscatedItems() && f.returns().size() == 2, "没收的东西搬进账里");

            // 模拟 S1：定案后存了盘、没落定。存档里能看到这条账
            CompoundTag saved = p.saveWithoutId(new CompoundTag());
            h.assertTrue(saved.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(PlayerLedger.ROOT).contains("finale"), "存档里应当带着账");

            LichDirector.onLogin(p);
            h.assertTrue(!PlayerLedger.hasFinale(p), "登录时应当补落定");
            h.assertTrue(p.getInventory().countItem(KuvaLichItems.KUVA.get()) == 0, "登录 10 秒内不投");
            LichDirector.onLogin(p);
            Inbox.skipLoginDelay(p);
            FinaleSettlement.patrol(p, p.server.getTickCount());
            h.assertTrue(!PlayerLedger.hasInbox(p), "空位够，巡检应当全部投完");

            ItemStack gotWeapon = ItemStack.EMPTY;
            int others = 0;
            List<Item> known = List.of(KuvaLichItems.LICH_RELIQUARY.get(), KuvaLichItems.RIVEN_SLIVER.get(),
                    KuvaLichItems.KUVA.get(), KuvaLichItems.REQUIEM_ULTIMATUM.get(), KuvaLichItems.EPHEMERA.get(),
                    KuvaLichItems.KUVALICH_GUIDE.get(), Items.DIAMOND, Items.APPLE);
            for (ItemStack s : p.getInventory().items) {
                if (s.isEmpty()) {
                    continue;
                }
                if (KuvaLichItems.KUVA_WEAPONS.contains(s.getItem())) {
                    h.assertTrue(gotWeapon.isEmpty(), "只该有一把武器");
                    gotWeapon = s;
                } else if (!known.contains(s.getItem())) {
                    others += s.getCount();
                }
            }
            h.assertTrue(gotWeapon.is(weapon) && KuvaWeaponUtil.getNumber(gotWeapon) == weaponLevel
                    && weaponType.equals(KuvaWeaponUtil.getType(gotWeapon)), "武器是诞生时定下的那把");
            h.assertTrue(owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1, "圣骸匣 1");
            h.assertTrue(owned(p, KuvaLichItems.RIVEN_SLIVER.get()) == 3, "裂罅碎片按配置 3");
            h.assertTrue(owned(p, KuvaLichItems.KUVA.get()) == 200, "赤毒 200 一个不少（拆堆存读）");
            h.assertTrue(owned(p, KuvaLichItems.REQUIEM_ULTIMATUM.get()) == 1, "概率 100 时给一张安魂通牒");
            h.assertTrue(owned(p, KuvaLichItems.EPHEMERA.get()) == 1, "幻纹 1");
            h.assertTrue(others == 1, "高级模组 1 个：" + others);
            h.assertTrue(owned(p, Items.DIAMOND) == 5 && owned(p, Items.APPLE) == 3, "没收的东西全部还回来");
            h.assertTrue(card.getMinimumLevelWeapon() > minBefore || minBefore >= ModConfig.KUVA_LICH.minimumLevel.get(),
                    "下限提了");
            h.assertTrue(card.getMaximumLevelWeapon() > maxBefore || maxBefore >= ModConfig.KUVA_LICH.maximumLevel.get(),
                    "上限提了");
            AABB around = lich.getBoundingBox().inflate(8.0D);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                    e -> !e.getItem().is(Items.DIRT)).isEmpty(), "地上不该有奖励");
            lich.discard();
            h.succeed();
        });
    }

    @AfterBatch(batch = TABLE)
    public static void afterTable(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = SETTLE_FAULT, timeoutTicks = 100)
    public static void settleFaultTouchesNothing(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        h.runAfterDelay(2, () -> {
            MercyCoreTests.setCards(card, 3);
            card.addConfiscatedItem(new ItemStack(Items.DIAMOND, 1));
            // 账上留着一条旧的，落定它时出错：这次定案应当整个不做
            PlayerLedger.setFinale(p, sample(99));
            FinaleSettlement.faultBeforeCommit = () -> {
                throw new IllegalStateException("测试注入");
            };
            boolean threw = false;
            try {
                lich.computeAndApplySuccess(p, card, lich.blockPosition());
            } catch (IllegalStateException e) {
                threw = true;
            } finally {
                FinaleSettlement.faultBeforeCommit = null;
            }
            h.assertTrue(threw && card.hasLich() && card.hasConfiscatedItems()
                            && card.getOneCard().getDamageValue() == 0 && lich.isAlive(),
                    "定案抛出：卡没扣、玄骸还在、没收的东西还在");
            Finale old = PlayerLedger.getFinale(p);
            h.assertTrue(old != null && old.generation() == 99, "旧账原样留着");

            lich.computeAndApplySuccess(p, card, lich.blockPosition());
            Finale now = PlayerLedger.getFinale(p);
            h.assertTrue(now != null && now.generation() == card.getLichGeneration() && !card.hasLich(),
                    "再来一次：先落定旧账，再写新账");
            h.assertTrue(owned(p, KuvaLichItems.KUVA.get()) == 30, "旧账的东西到手");
            h.assertTrue(FinaleSettlement.resolveNow(p, now.generation(), FinaleSettlement.Choice.VANQUISH)
                    && owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1, "新账落定");
            lich.discard();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = BUILD_FAULT, timeoutTicks = 100)
    public static void finaleBuildFaultTouchesNothing(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        h.runAfterDelay(2, () -> {
            MercyCoreTests.setCards(card, 3);
            card.addConfiscatedItem(new ItemStack(Items.DIAMOND, 4));
            int gen = card.getLichGeneration();
            Finale.faultOnSave = () -> {
                throw new IllegalStateException("测试注入");
            };
            boolean threw = false;
            try {
                lich.computeAndApplySuccess(p, card, lich.blockPosition());
            } catch (IllegalStateException e) {
                threw = true;
            } finally {
                Finale.faultOnSave = null;
            }
            h.assertTrue(threw && card.hasLich() && card.getLichGeneration() == gen && card.hasConfiscatedItems()
                            && card.getOneCard().getDamageValue() == 0 && lich.isAlive() && !PlayerLedger.hasFinale(p),
                    "造账抛出：这一轮没结束、卡没扣、没收的东西还在卡上、账上没有");

            lich.computeAndApplySuccess(p, card, lich.blockPosition());
            Finale f = PlayerLedger.getFinale(p);
            h.assertTrue(f != null && f.generation() == gen && !card.hasLich() && !card.hasConfiscatedItems()
                    && f.returns().size() == 1 && f.returns().get(0).getCount() == 4, "再来一次：没收的东西进了账");
            lich.discard();
            h.succeed();
        });
    }

    @AfterBatch(batch = BUILD_FAULT)
    public static void afterBuildFault(ServerLevel level) {
        Finale.faultOnSave = null;
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = SETTLE_FAULT)
    public static void afterSettleFault(ServerLevel level) {
        FinaleSettlement.faultBeforeCommit = null;
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 怜悯之击里的各种收尾 ====================

    private interface AfterSettle {
        void run(ServerPlayer p, KuvaMasterEntity lich);
    }

    /** 三刀全对开一场，第 20 tick（定案之后、发奖之前）做 {@code action}，再隔 {@code checkDelay} tick 核：账落定了、只发了一份 */
    private static void afterSettle(GameTestHelper h, AfterSettle action, int checkDelay, AfterSettle check) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            MercyCoreTests.setCards(card, 3);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            h.runAfterDelay(20, () -> {
                h.assertTrue(PlayerLedger.hasFinale(p) && !card.hasLich(), "tick 17 已定案、账上有一条");
                h.assertTrue(MercyServer.hasActiveExec(p.getUUID()), "处决还在走");
                action.run(p, lich);
                h.runAfterDelay(checkDelay, () -> {
                    check.run(p, lich);
                    h.succeed();
                });
            });
        });
    }

    private static void assertSettledOnce(GameTestHelper h, ServerPlayer p, String when) {
        h.assertTrue(!PlayerLedger.hasFinale(p), when + "：账应当落定");
        h.assertTrue(owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1, when + "：圣骸匣只有一个，实际 "
                + owned(p, KuvaLichItems.LICH_RELIQUARY.get()));
    }

    @GameTest(template = "empty", batch = RESET, timeoutTicks = 200)
    public static void mercyResetSettles(GameTestHelper h) {
        afterSettle(h, (p, lich) -> {
            int n = MercyServer.resetAround(h.getLevel(), lich.position(), 4.0D);
            h.assertTrue(n == 1, "应当处理了这一只");
            assertSettledOnce(h, p, "mercy reset");
            h.assertTrue(!lich.isAlive(), "已定案成功的复位就是快进到死");
            h.assertTrue(!MercyServer.isBusy(p) || !MercyServer.hasActiveExec(p.getUUID()), "处决结束");
        }, 160, (p, lich) -> assertSettledOnce(h, p, "等过原来发奖那一拍"));
    }

    @AfterBatch(batch = RESET)
    public static void afterReset(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = STOP, timeoutTicks = 200)
    public static void mercyStopSettles(GameTestHelper h) {
        afterSettle(h, (p, lich) -> {
            h.assertTrue(MercyServer.stop(p) == null, "定案成功后中止应当快进收尾");
            assertSettledOnce(h, p, "mercy stop");
        }, 160, (p, lich) -> assertSettledOnce(h, p, "等过原来发奖那一拍"));
    }

    @AfterBatch(batch = STOP)
    public static void afterStop(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = DEATH, timeoutTicks = 200)
    public static void executorDiesAfterSettle(GameTestHelper h) {
        List<ServerPlayer> respawned = new ArrayList<>();
        afterSettle(h, (p, lich) -> {
            p.setGameMode(GameType.SURVIVAL);
            KuvaTestKit.killCommand(h, p);
            h.assertTrue(!p.isAlive(), "处决者应当死了");
            h.assertTrue(!PlayerLedger.hasFinale(p) && owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1
                    && p.getInventory().countItem(KuvaLichItems.LICH_RELIQUARY.get()) == 0, "死了：落定进收件箱、不往死人背包里放");
            ServerPlayer fresh = new ServerPlayer(p.server, h.getLevel(), p.getGameProfile());
            fresh.connection = p.connection;
            fresh.restoreFrom(p, true);
            respawned.add(fresh);
            h.assertTrue(owned(fresh, KuvaLichItems.LICH_RELIQUARY.get()) == 1 && !PlayerLedger.hasFinale(fresh),
                    "重生后收件箱跟过来");
        }, 5, (p, lich) -> h.assertTrue(owned(respawned.get(0), KuvaLichItems.LICH_RELIQUARY.get()) == 1, "只有一份"));
    }

    @AfterBatch(batch = DEATH)
    public static void afterDeath(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = GONE, timeoutTicks = 200)
    public static void targetDiscardedAfterSettle(GameTestHelper h) {
        afterSettle(h, (p, lich) -> {
            lich.discard();
            assertSettledOnce(h, p, "玄骸被别的模组移除");
        }, 160, (p, lich) -> assertSettledOnce(h, p, "等过原来发奖那一拍"));
    }

    @AfterBatch(batch = GONE)
    public static void afterGone(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = DIM, timeoutTicks = 200)
    public static void executorChangesDimensionAfterSettle(GameTestHelper h) {
        afterSettle(h, (p, lich) -> {
            MercyServer.onExecutorGone(p, MercyServer.Gone.DIMENSION);
            assertSettledOnce(h, p, "换维度");
        }, 160, (p, lich) -> assertSettledOnce(h, p, "等过原来发奖那一拍"));
    }

    @AfterBatch(batch = DIM)
    public static void afterDim(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 关服那一下的处理要单独一个 batch：它会清掉所有进行中的处决 */
    @GameTest(template = "empty", batch = STOPPING, timeoutTicks = 200)
    public static void serverStoppingSettles(GameTestHelper h) {
        afterSettle(h, (p, lich) -> {
            MercyServer.onServerStopping(new ServerStoppingEvent(p.server));
            assertSettledOnce(h, p, "关服");
            h.assertTrue(!MercyServer.hasActiveExec(p.getUUID()), "关服后不该还有进行中的处决");
            lich.discard();
        }, 5, (p, lich) -> assertSettledOnce(h, p, "关服之后"));
    }

    @AfterBatch(batch = STOPPING)
    public static void afterStopping(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 怜悯之击关着：直接判定当场落定 ====================

    @GameTest(template = "empty", batch = DIRECT, timeoutTicks = 100)
    public static void directSuccessSettlesAtOnce(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        h.runAfterDelay(2, () -> {
            KuvaTestKit.override(ModConfig.KUVA_LICH.mercyEnabled, false);
            MercyCoreTests.setCards(card, 3);
            lich.resolveForOwner(p);
            h.assertTrue(!card.hasLich() && !PlayerLedger.hasFinale(p), "直接判定：定案后当场落定");
            h.assertTrue(owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1
                    && p.getInventory().countItem(KuvaLichItems.LICH_RELIQUARY.get()) == 0, "投递等碎裂之后");
            h.runAfterDelay(14, () -> {
                h.assertTrue(p.getInventory().countItem(KuvaLichItems.LICH_RELIQUARY.get()) == 1
                        && !PlayerLedger.hasInbox(p), "碎裂之后进背包");
                lich.discard();
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = DIRECT)
    public static void afterDirect(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
