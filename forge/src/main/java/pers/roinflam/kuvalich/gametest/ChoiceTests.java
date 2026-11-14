package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.nemesis.Finale;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.network.packet.MercyChoicePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;
import java.util.function.BooleanSupplier;

/** 终结的感化 / 消灭选择窗口（C2a） */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChoiceTests {

    private static final String MAIN = "kl_choice_main";
    private static final String HOT = "kl_choice_hot";
    private static final String OFF_AT_OPEN = "kl_choice_off_at_open";
    private static final String STOPPING = "kl_choice_stopping";
    private static final String FAULT = "kl_choice_fault";
    private static final String ROSTER = "kl_choice_roster";

    private ChoiceTests() {
    }

    private interface Opened {
        void run(ServerPlayer p, RequiemCard card, KuvaMasterEntity lich);
    }

    private static RequiemCard card(Player p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
    }

    private static int owned(ServerPlayer p, Item item) {
        int n = p.getInventory().countItem(item);
        for (ItemStack s : PlayerLedger.getInbox(p)) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int ownedWeapons(ServerPlayer p) {
        int n = 0;
        for (Item w : KuvaLichItems.KUVA_WEAPONS) {
            n += owned(p, w);
        }
        return n;
    }

    private static void waitUntil(GameTestHelper h, BooleanSupplier cond, int left, String what, Runnable then) {
        if (cond.getAsBoolean()) {
            then.run();
        } else if (left <= 0) {
            h.fail("等不到：" + what);
        } else {
            h.runAfterDelay(1, () -> waitUntil(h, cond, left - 1, what, then));
        }
    }

    /** 三刀全对开一场（第一套、带一件没收物品），定案后第 {@code earlyAt} tick 先做 {@code early}，开窗时做 {@code opened} */
    private static void execute(GameTestHelper h, int earlyAt, Opened early, Opened opened) {
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.convertEnabled, true);
        RequiemCard card = card(p);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            KuvaTestKit.override(ModConfig.KUVA_LICH.convertEnabled, true);
            MercyCoreTests.setCards(card, 3);
            card.addConfiscatedItem(new ItemStack(Items.DIAMOND, 5));
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            h.assertTrue(MercyPlan.hasChoiceWindow(lich.getMercyPlan()), "开着感化时计划位 bit 31 应当置位");
            Runnable waitOpen = () -> waitUntil(h, () -> lich.getMercyChoice() == AbstractKuva.CHOICE_WAITING, 200,
                    "选择窗口开", () -> opened.run(p, card, lich));
            if (early == null) {
                waitOpen.run();
            } else {
                h.runAfterDelay(earlyAt, () -> {
                    early.run(p, card, lich);
                    if (opened != null) {
                        waitOpen.run();
                    }
                });
            }
        });
    }

    private static void assertVanquishedOnce(GameTestHelper h, ServerPlayer p, KuvaMasterEntity lich, String when) {
        h.assertTrue(!PlayerLedger.hasFinale(p), when + "：账应当落定");
        h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_VANQUISH, when + "：结果应当是消灭，实际 " + lich.getMercyChoice());
        h.assertTrue(owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1, when + "：圣骸匣只有一个，实际 "
                + owned(p, KuvaLichItems.LICH_RELIQUARY.get()));
        h.assertTrue(ownedWeapons(p) == 1, when + "：武器一把");
        h.assertTrue(owned(p, Items.DIAMOND) == 5, when + "：没收的东西还回来");
        h.assertTrue(PlayerLedger.getAllies(p).isEmpty(), when + "：名册不该多东西");
    }

    // ==================== 窗口本身 ====================

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 900)
    public static void windowHoldsThenTimesOutAsVanquish(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.convertChoiceSeconds, 20);
        execute(h, 0, null, (p, card, lich) -> h.runAfterDelay(300, () -> {
            h.assertTrue(MercyServer.hasActiveExec(p.getUUID()) && MercyServer.isBusy(p), "窗口开着 15 秒，这一场还在、处决者还锁着");
            h.assertTrue(lich.isAlive() && lich.getMercyChoice() == AbstractKuva.CHOICE_WAITING, "还在等");
            h.assertTrue(PlayerLedger.hasFinale(p), "没选之前账不动");
            waitUntil(h, () -> !PlayerLedger.hasFinale(p), 220, "截止后按消灭落定", () -> {
                assertVanquishedOnce(h, p, lich, "截止");
                waitUntil(h, () -> !lich.isAlive(), 120, "消灭后玄骸死亡",
                        () -> waitUntil(h, () -> !MercyServer.hasActiveExec(p.getUUID()), 80, "这一场走完", () -> {
                            assertVanquishedOnce(h, p, lich, "走完");
                            h.succeed();
                        }));
            });
        }));
    }

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 500)
    public static void convertGivesOnlyOwnThingsAndLeaves(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        int[] caps = new int[3];
        execute(h, 0, null, (p, card, lich) -> {
            caps[0] = card.getMinimumLevelWeapon();
            caps[1] = card.getMaximumLevelWeapon();
            caps[2] = PlayerLedger.getFinale(p).generation();
            MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
            h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_CONVERT, "结果应当是感化");
            h.assertTrue(!PlayerLedger.hasFinale(p), "选了当场落定");
            h.assertTrue(ownedWeapons(p) == 0 && owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 0
                    && owned(p, KuvaLichItems.KUVA.get()) == 0 && owned(p, KuvaLichItems.RIVEN_SLIVER.get()) == 0,
                    "感化不给击杀奖励");
            h.assertTrue(owned(p, Items.DIAMOND) == 5, "被没收的东西照还");
            h.assertTrue(owned(p, KuvaLichItems.EPHEMERA.get()) == 1, "幻纹照给");
            List<CompoundTag> allies = PlayerLedger.getAllies(p);
            h.assertTrue(allies.size() == 1 && allies.get(0).getInt(AllyRoster.GENERATION) == caps[2], "名册多一条，是这一代");
            RequiemCard.HistoryEntry last = card.getLastHistory();
            h.assertTrue(last != null && last.generation() == caps[2] && last.fate() == RequiemCard.FATE_CONVERTED,
                    "历史首条记成感化");
            h.assertTrue(card.getMinimumLevelWeapon() == caps[0] && card.getMaximumLevelWeapon() == caps[1], "上下限不提");
            waitUntil(h, lich::isRemoved, 160, "感化后玄骸离场移除", () -> {
                h.assertTrue(lich.getRemovalReason() != Entity.RemovalReason.KILLED, "感化的玄骸不该被杀死");
                h.assertTrue(LichDirector.getLich(p).isEmpty(), "导演那边的引用清掉");
                waitUntil(h, () -> !MercyServer.hasActiveExec(p.getUUID()), 80, "这一场走完", () -> {
                    h.assertTrue(ownedWeapons(p) == 0 && owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 0, "走完也没有奖励");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 500)
    public static void choicePacketsOutsideRulesIgnored(GameTestHelper h) {
        ServerPlayer other = KuvaTestKit.player(h);
        execute(h, 20, (p, card, lich) -> {
            MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
            h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_NONE && PlayerLedger.hasFinale(p), "开窗前发的无效");
        }, (p, card, lich) -> {
            MercyServer.handleChoice(other, MercyChoicePacket.CONVERT);
            h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_WAITING, "别人替发无效");
            MercyServer.handleChoice(p, (byte) 5);
            MercyServer.handleChoice(p, (byte) -1);
            h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_WAITING, "越界的值无效");
            MercyServer.handleChoice(p, MercyChoicePacket.VANQUISH);
            MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
            assertVanquishedOnce(h, p, lich, "选了消灭再发感化");
            waitUntil(h, () -> !lich.isAlive(), 120, "消灭后玄骸死亡", () -> {
                MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
                assertVanquishedOnce(h, p, lich, "窗口之后再发");
                h.succeed();
            });
        });
    }

    // ==================== 打断 ====================

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 400)
    public static void interruptedBeforeOpenVanquishes(GameTestHelper h) {
        execute(h, 20, (p, card, lich) -> {
            h.assertTrue(PlayerLedger.hasFinale(p) && lich.getMercyChoice() == AbstractKuva.CHOICE_NONE, "已定案、还没开窗");
            MercyServer.onExecutorGone(p, MercyServer.Gone.DIMENSION);
            assertVanquishedOnce(h, p, lich, "开窗前换维度");
            h.assertTrue(!lich.isAlive(), "快进跑完后段，玄骸死亡");
            h.assertTrue(!MercyServer.hasActiveExec(p.getUUID()), "这一场结束");
            h.runAfterDelay(160, () -> {
                assertVanquishedOnce(h, p, lich, "等过原来的时间轴");
                h.succeed();
            });
        }, null);
    }

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 500)
    public static void logoutInWindowVanquishes(GameTestHelper h) {
        execute(h, 0, null, (p, card, lich) -> {
            MercyServer.onExecutorGone(p, MercyServer.Gone.LOGOUT);
            assertVanquishedOnce(h, p, lich, "窗口里掉线");
            h.assertTrue(!lich.isAlive() && !MercyServer.hasActiveExec(p.getUUID()), "快进到死、这一场结束");
            h.runAfterDelay(120, () -> {
                assertVanquishedOnce(h, p, lich, "之后");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = MAIN, timeoutTicks = 500)
    public static void targetDiscardedInWindowVanquishes(GameTestHelper h) {
        execute(h, 0, null, (p, card, lich) -> {
            lich.discard();
            assertVanquishedOnce(h, p, lich, "窗口里玄骸被移除");
            h.assertTrue(!MercyServer.hasActiveExec(p.getUUID()), "这一场结束");
            h.runAfterDelay(60, () -> {
                assertVanquishedOnce(h, p, lich, "之后");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = MAIN)
    public static void afterMain(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 配置热改 ====================

    @GameTest(template = "empty", batch = HOT, timeoutTicks = 500)
    public static void configChangedAfterOpenKeepsThisWindow(GameTestHelper h) {
        execute(h, 0, null, (p, card, lich) -> {
            KuvaTestKit.override(ModConfig.KUVA_LICH.convertEnabled, false);
            MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
            h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_CONVERT, "开窗时允许，窗口里关掉不影响这一场");
            h.assertTrue(PlayerLedger.getAllies(p).size() == 1, "照样进名册");
            h.succeed();
        });
    }

    @AfterBatch(batch = HOT)
    public static void afterHot(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = OFF_AT_OPEN, timeoutTicks = 500)
    public static void disabledAtOpenRefusesConvert(GameTestHelper h) {
        execute(h, 20, (p, card, lich) -> KuvaTestKit.override(ModConfig.KUVA_LICH.convertEnabled, false),
                (p, card, lich) -> {
                    MercyServer.handleChoice(p, MercyChoicePacket.CONVERT);
                    h.assertTrue(lich.getMercyChoice() == AbstractKuva.CHOICE_WAITING, "开窗时已关掉，感化无效");
                    MercyServer.handleChoice(p, MercyChoicePacket.VANQUISH);
                    assertVanquishedOnce(h, p, lich, "只能消灭");
                    h.succeed();
                });
    }

    @AfterBatch(batch = OFF_AT_OPEN)
    public static void afterOffAtOpen(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 关服、推进出错 ====================

    /** 关服会清掉所有进行中的处决，单独一个 batch */
    @GameTest(template = "empty", batch = STOPPING, timeoutTicks = 400)
    public static void serverStoppingInWindowVanquishes(GameTestHelper h) {
        execute(h, 0, null, (p, card, lich) -> {
            MercyServer.onServerStopping(new ServerStoppingEvent(p.server));
            h.assertTrue(!PlayerLedger.hasFinale(p) && owned(p, KuvaLichItems.LICH_RELIQUARY.get()) == 1, "关服时按消灭落定");
            h.assertTrue(!MercyServer.hasActiveExec(p.getUUID()), "关服后不该还有进行中的处决");
            lich.discard();
            h.succeed();
        });
    }

    @AfterBatch(batch = STOPPING)
    public static void afterStopping(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = FAULT, timeoutTicks = 500)
    public static void tickErrorInWindowRecovers(GameTestHelper h) {
        execute(h, 0, null, (p, card, lich) -> {
            MercyServer.faultOnTick = () -> {
                MercyServer.faultOnTick = null;
                throw new IllegalStateException("测试：推进出错");
            };
            h.runAfterDelay(2, () -> {
                h.assertTrue(MercyServer.faultOnTick == null, "钩子应当已经触发");
                assertVanquishedOnce(h, p, lich, "推进出错后的兜底");
                h.assertTrue(!MercyServer.hasActiveExec(p.getUUID()), "这一场结束、处决者解锁");
                waitUntil(h, lich::isRemoved, 60, "兜底让玄骸离场", h::succeed);
            });
        });
    }

    @AfterBatch(batch = FAULT)
    public static void afterFault(ServerLevel level) {
        MercyServer.faultOnTick = null;
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 名册 ====================

    private static Finale sample(int gen, String name) {
        return new Finale(gen, 7L + gen, name, 3, 0, 20, "fire", "", List.of(), List.of(), "minecraft:overworld", 0, 64, 0);
    }

    @GameTest(template = "empty", batch = ROSTER)
    public static void rosterReleasesEarliestButActiveLast(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyRosterSize, 2);
        ServerPlayer p = KuvaTestKit.player(h);
        h.assertTrue(AllyRoster.add(p, AllyRoster.entryOf(sample(1, "Aaa"), 10L)).isEmpty(), "第一只");
        h.assertTrue(AllyRoster.add(p, AllyRoster.entryOf(sample(2, "Bbb"), 20L)).isEmpty(), "第二只");
        h.assertTrue(AllyRoster.add(p, AllyRoster.entryOf(sample(2, "Bbb"), 30L)).isEmpty()
                && PlayerLedger.getAllies(p).size() == 2, "同一代不重复加");
        PlayerLedger.setActiveAlly(p, 1);
        h.assertTrue("Bbb".equals(AllyRoster.wouldRelease(p)), "出战的最后挤，先挤第二只");
        h.assertTrue("Bbb".equals(AllyRoster.add(p, AllyRoster.entryOf(sample(3, "Ccc"), 40L))), "满了挤掉第二只");
        List<CompoundTag> allies = PlayerLedger.getAllies(p);
        h.assertTrue(allies.size() == 2 && allies.get(0).getInt(AllyRoster.GENERATION) == 1
                && allies.get(1).getInt(AllyRoster.GENERATION) == 3, "剩第一只和新的");
        PlayerLedger.setActiveAlly(p, -1);
        h.assertTrue("Aaa".equals(AllyRoster.wouldRelease(p)), "没有出战的就挤最早的");
        h.succeed();
    }

    @GameTest(template = "empty", batch = ROSTER)
    public static void pendingConvertResolvesAsConvert(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        PlayerLedger.setFinale(p, new Finale(9, 9L, "Ddd", 3, 0, 20, "fire", "",
                List.of(new ItemStack(KuvaLichItems.KUVA.get(), 30)), List.of(new ItemStack(Items.DIAMOND, 2)),
                "minecraft:overworld", 0, 64, 0));
        PlayerLedger.markFinaleConvert(p, 8);
        h.assertTrue(!PlayerLedger.finaleMarkedConvert(p), "代数对不上不记");
        PlayerLedger.markFinaleConvert(p, 9);
        h.assertTrue(pers.roinflam.kuvalich.nemesis.FinaleSettlement.resolvePending(p), "补落定");
        h.assertTrue(owned(p, KuvaLichItems.KUVA.get()) == 0 && owned(p, Items.DIAMOND) == 2, "账上记了感化：不给掉落、照还没收的");
        h.assertTrue(PlayerLedger.getAllies(p).stream().anyMatch(t -> t.getInt(AllyRoster.GENERATION) == 9), "进名册");
        h.succeed();
    }

    @AfterBatch(batch = ROSTER)
    public static void afterRoster(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
