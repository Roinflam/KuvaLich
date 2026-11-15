package pers.roinflam.kuvalich.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * GameTest 的公用工具
 *
 * <p>写测试时的几条规矩：</p>
 * <ul>
 *   <li>玩家一律用 {@link #player}，不要调 {@code GameTestHelper#makeMockServerPlayerInLevel()}：
 *       它造的连接没有挂网络通道，Forge 的 {@code PlayerList#placeNewPlayer} 一进服就会空指针，
 *       而且它把 {@code isCreative()} 写死成了 true，创造模式的玩家不会触发到场、怪也不打。</li>
 *   <li>同一个 batch 里的测试在同一个世界里<b>同时</b>跑，所以下面三类测试各自单独一个 batch，
 *       并配 {@code @BeforeBatch} / {@code @AfterBatch}（按 batch 名匹配），收尾时调 {@link #afterBatch}：
 *       <ul>
 *         <li>改了全局配置的（{@code @GameTest(batch = "kl_cfg_<测试名>")}）；</li>
 *         <li>改难度的（难度全服共用）；</li>
 *         <li>玄骸会走动、追人的（到场、追击、换位，{@code kl_nemesis_iso_<测试名>}），免得闯进同批别的测试场地。</li>
 *       </ul>
 *       其余放默认 batch。</li>
 *   <li>测试类统一写 {@code @GameTestHolder("kuvalich")} 和 {@code @PrefixGameTestTemplate(false)}，模板用 {@code empty}。</li>
 * </ul>
 */
public final class KuvaTestKit {

    /** 本批次造出来的测试玩家，收尾时移出玩家列表 */
    private static final List<ServerPlayer> PLAYERS = new ArrayList<>();
    /** 改过的配置，收尾时按相反顺序改回 */
    private static final List<Runnable> RESTORES = new ArrayList<>();
    private static int counter;

    /** 场地内部最高一层（相对 y） */
    private static final int ARENA_TOP = 4;
    /**
     * 头顶封几层。玄骸找落点是从玩家脚下往上 6 格开始往下扫的，玩家站在场地里最高也就 y = 4，
     * 封到 y = 4 + 6 才扫不到顶上那一面；只封一层的话薄顶的上表面会被当成能站的地方
     */
    private static final int ARENA_ROOF = 6;
    /** 同一 tick 里各测试挖开的场地内部（绝对坐标），砌墙时跳过 */
    private static final Set<Long> CARVED = new HashSet<>();
    private static long carvedAt = Long.MIN_VALUE;
    @Nullable
    private static ServerLevel carvedLevel;

    private KuvaTestKit() {
    }

    /** 同 {@link #openArena(GameTestHelper, int, int, int, int, int)}，地板在 y = -1，场地内部是 y = 0~4 */
    public static void openArena(GameTestHelper h, int x0, int x1, int z0, int z1) {
        openArena(h, x0, x1, z0, z1, -1);
    }

    /**
     * 在相对坐标 [x0, x1] × [z0, z1] 挖出一块封闭的场地：{@code floorY} 那层铺石头地板，往上到 y = 4 挖空，
     * 四周砌一圈石墙，头顶封 {@link #ARENA_ROOF} 层石头。结构方块原样留着。
     *
     * <p>测试世界不是超平坦，测试结构摆在地下深处，GameTest 只把模板四周两三格清成平坦地层，再往外就是天然的
     * 深板岩、岩浆、洞穴、沙砾，场地一大就挖进那里面，结果随世界种子变（踩进岩浆、沙砾落下来、从洞穴走丢）。
     * 地板、围墙、封顶把场地和外面完全隔开。</p>
     *
     * <p>同一个 batch 的测试同一 tick 开场，场地可能互相重叠：后挖的照样挖穿先砌的墙，砌墙时跳过同一 tick
     * 里别的测试已经挖开的格子，重叠的几块就连成一整块封闭的场地，不会把墙砌进别人的场地里。</p>
     */
    public static void openArena(GameTestHelper h, int x0, int x1, int z0, int z1, int floorY) {
        ServerLevel level = h.getLevel();
        long now = level.getGameTime();
        if (now != carvedAt || level != carvedLevel) {
            CARVED.clear();
            carvedAt = now;
            carvedLevel = level;
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                for (int y = floorY; y <= ARENA_TOP + ARENA_ROOF; y++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    BlockPos abs = h.absolutePos(rel);
                    if (level.getBlockState(abs).is(Blocks.STRUCTURE_BLOCK)) {
                        continue;
                    }
                    if (y == floorY || y > ARENA_TOP) {
                        h.setBlock(rel, stone);
                    } else if (inside) {
                        h.setBlock(rel, air);
                        CARVED.add(abs.asLong());
                    } else if (!CARVED.contains(abs.asLong())) {
                        h.setBlock(rel, stone);
                    }
                }
            }
        }
    }

    /**
     * 造一个生存模式的真 {@link ServerPlayer}，放在测试结构的中心，并登记下来，batch 结束时移出玩家列表。
     *
     * <p>做法：{@code new Connection(SERVERBOUND)} 再把它挂进一个 {@code EmbeddedChannel}（{@code channelActive}
     * 会把通道填上），{@code new ServerPlayer}（不覆写 {@code isCreative}），走一遍
     * {@code PlayerList#placeNewPlayer}，最后设成生存模式。</p>
     */
    public static ServerPlayer player(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "kl_test_" + (++counter));
        ServerPlayer player = new ServerPlayer(server, level, profile);
        server.getPlayerList().placeNewPlayer(connection, player);
        // 测试一造出来就要能收东西，不等登录后的 10 秒
        pers.roinflam.kuvalich.nemesis.Inbox.skipLoginDelay(player);
        pers.roinflam.kuvalich.nemesis.AllyDirector.skipLoginDelay(player);
        player.setGameMode(GameType.SURVIVAL);
        // 老测试按没有选择窗口的时间轴写；测选择窗口的自己再打开
        override(pers.roinflam.kuvalich.config.ModConfig.KUVA_LICH.convertEnabled, false);
        // 同理，盟友会在挨打见底时随机冒出来，老测试不该碰到；测盟友的自己打开
        override(pers.roinflam.kuvalich.config.ModConfig.KUVA_LICH.allyEnabled, false);
        BlockPos size = size(h);
        Vec3 at = h.absoluteVec(new Vec3(size.getX() / 2.0D, 1.0D, size.getZ() / 2.0D));
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        PLAYERS.add(player);
        return player;
    }

    /** 结构的尺寸（格数）。GameTestHelper 没有公开尺寸，这里遍历结构里的方块位置取最大值 */
    private static BlockPos size(GameTestHelper h) {
        int[] max = new int[3];
        h.forEveryBlockInStructure(p -> {
            max[0] = Math.max(max[0], p.getX() + 1);
            max[1] = Math.max(max[1], p.getY() + 1);
            max[2] = Math.max(max[2], p.getZ() + 1);
        });
        return new BlockPos(max[0], max[1], max[2]);
    }

    /** 结构中心的相对坐标（y = 0，即地面那一层） */
    public static BlockPos center(GameTestHelper h) {
        BlockPos size = size(h);
        return new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
    }

    /** 以结构中心为心、边长 {@code 2 * radius + 1} 的方形地面铺上 {@code block}（y = 0） */
    public static void floor(GameTestHelper h, int radius, Block block) {
        BlockPos c = center(h);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                h.setBlock(c.offset(dx, 0, dz), block);
            }
        }
    }

    /**
     * 临时改一项配置，{@link #afterBatch} 时改回。
     *
     * <p>只改内存，不写配置文件：{@code ConfigValue#set} 会让带自动保存的配置文件当场整份重写，Forge 的文件监视线程
     * 再异步重读一遍。赶上读到写了一半的文件，它就判成「文件不完整」，备份之后按默认值补全、连同当时内存里被测试改过的值
     * 一起存回去，这个值就一直留在文件里，之后每次开测都带着它（某次测试挂掉以后，后面几轮一直挂同一批）；
     * 每次重读还会在监视线程上发一遍配置重载事件，和测试线程抢着重建缓存。测试只要 {@code get()} 读到这个值，
     * 所以直接写 {@code ConfigValue} 的缓存；改回时清掉缓存，下次 {@code get()} 重新读没动过的配置。</p>
     */
    public static <T> void override(ForgeConfigSpec.ConfigValue<T> v, T value) {
        RESTORES.add(v::clearCache);
        try {
            CACHED_VALUE.set(v, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("改不了配置缓存：" + v.getPath(), e);
        }
        if (!Objects.equals(v.get(), value)) {
            throw new IllegalStateException("配置缓存没生效：" + v.getPath() + " 读出来是 " + v.get());
        }
    }

    /** {@code ForgeConfigSpec.ConfigValue} 的缓存字段，{@link #override} 用 */
    private static final Field CACHED_VALUE;

    static {
        try {
            CACHED_VALUE = ForgeConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
            CACHED_VALUE.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 照真实的 {@code /kill} 命令打一遍（走 KillCommand，不是直接调 {@code entity.kill()}：
     * 赤毒实体只认命令调过来的那次是真死）。返回命令的结果数（打中几个）
     */
    public static int killCommand(GameTestHelper h, Entity target) {
        MinecraftServer server = h.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        return server.getCommands().performPrefixedCommand(source, "kill " + target.getStringUUID());
    }

    /**
     * 别的模组打致命一击的几种写法挨个来一遍，倒地保护要全部挡住（每种都照着真实写法，前面注明出处）。
     * 最后一种把血量直接写成 0、不调 die，要等 1 tick 由死亡计时补回来，调用方隔 1 tick 再查
     */
    public static void assaultLikeOtherMods(LivingEntity t, ServerPlayer p) {
        DamageSource hit = p.damageSources().playerAttack(p);
        // 1. 卡利亚式附魔 EntityLivingUtil.kill（命定之死、死亡之刃的持续伤害致死一击）：
        //    血量直接写成 0，写进去了就用攻击者的伤害源 die，没写进去就补一下 genericKill
        t.setHealth(0.0F);
        if (t.getHealth() <= 0.0F) {
            t.die(hit);
        } else {
            t.hurt(t.damageSources().genericKill(), Float.MAX_VALUE);
        }
        // 2. 绕过 setHealth、直接改同步数据里的血量（真伤扣血找不到 setHealth 时的退路），再用绕过无敌的伤害源 die
        writeRawHealth(t, 0.0F);
        t.die(t.damageSources().genericKill());
        // 3. 借 genericKill / fellOutOfWorld 当真伤（人在世界里面，没掉出去），每下之前清无敌帧
        t.invulnerableTime = 0;
        t.hurt(t.damageSources().genericKill(), Float.MAX_VALUE);
        t.invulnerableTime = 0;
        t.hurt(t.damageSources().fellOutOfWorld(), 1.0E6F);
        // 4. 不是 /kill 命令、模组自己调的 kill()
        t.kill();
        // 5. 持续掉血：一 tick 一截直接扣
        for (int i = 0; i < 5; i++) {
            t.setHealth(t.getHealth() - 50.0F);
        }
        // 6. 战斗修正献祭法杖的强杀：先打一下大的，没死就 die 再把血写成 0
        t.hurt(hit, t.getMaxHealth() * 100.0F);
        if (t.isAlive()) {
            t.die(hit);
            t.setHealth(0.0F);
        }
        // 7. 没走死亡、直接按「被杀」移除
        t.remove(Entity.RemovalReason.KILLED);
        // 8. 直接改同步数据把血量写成 0、什么都不调（等它自己按 0 血死掉）
        writeRawHealth(t, 0.0F);
    }

    /** 不走 setHealth，直接改同步数据里的血量（只在开发环境跑测试，按官方名找字段） */
    @SuppressWarnings("unchecked")
    private static void writeRawHealth(LivingEntity t, float health) {
        try {
            Field f = LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
            f.setAccessible(true);
            t.getEntityData().set((EntityDataAccessor<Float>) f.get(null), health);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("找不到血量的同步字段", e);
        }
    }

    /** 一个 batch 收尾：把改过的配置改回去，把登记的测试玩家移出玩家列表。可重复调用 */
    public static void afterBatch(ServerLevel level) {
        for (int i = RESTORES.size() - 1; i >= 0; i--) {
            try {
                RESTORES.get(i).run();
            } catch (RuntimeException e) {
                LogUtil.error("测试收尾时改回配置失败: " + e);
            }
        }
        RESTORES.clear();
        PlayerList list = level.getServer().getPlayerList();
        for (ServerPlayer p : new ArrayList<>(PLAYERS)) {
            if (list.getPlayers().contains(p)) {
                try {
                    list.remove(p);
                } catch (RuntimeException e) {
                    LogUtil.error("测试收尾时移除测试玩家失败: " + e);
                }
            }
        }
        PLAYERS.clear();
    }
}
