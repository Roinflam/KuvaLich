package pers.roinflam.kuvalich.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.client.ephemera.EphemeraAnchor;
import pers.roinflam.kuvalich.client.ephemera.EphemeraHost;
import pers.roinflam.kuvalich.client.ephemera.EphemeraStaging;
import pers.roinflam.kuvalich.client.ephemera.EphemeraTier;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraStateTable;
import pers.roinflam.kuvalich.ephemera.EphemeraSync;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.network.packet.EphemeraStatePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 幻纹给别人看：状态包往返、客户端状态表、换款只发一次、晚进视野补发、玄骸 / 盟友的同步数据，
 * 以及渲染器里不碰客户端类的那几块（挂点按包围盒缩放、预算裁剪）
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EphemeraSyncTests {

    private static final String BATCH = "kl_ephemera_sync";

    private EphemeraSyncTests() {
    }

    private static WarframeModules wm(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElseThrow(IllegalStateException::new);
    }

    private static ItemStack eph(EphemeraType type, String element) {
        return EphemeraItem.create(type, element, "Vesk Arru");
    }

    private static EphemeraStatePacket roundTrip(EphemeraStatePacket pkt) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EphemeraStatePacket.encode(pkt, buf);
        EphemeraStatePacket back = EphemeraStatePacket.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new IllegalStateException("decode left " + buf.readableBytes() + " bytes");
        }
        return back;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void packetAndLookRoundTrip(GameTestHelper h) {
        ItemStack st = eph(EphemeraType.COIL, "ice");
        EphemeraItem.setColors(st, 0xFFD700, 0x8800FF);
        EphemeraItem.setGradient(st, EphemeraItem.GRADIENT_FLOW);
        EphemeraItem.setScale(st, 1.6F);
        EphemeraItem.setYOffset(st, -0.25F);
        EphemeraLook look = EphemeraLook.of(st);
        h.assertTrue("coil".equals(look.type()) && "ice".equals(look.element()), "款式和元素：" + look);
        h.assertTrue(look.color1() == 0xFFD700 && look.color2() == 0x8800FF
                && look.gradient() == EphemeraItem.GRADIENT_FLOW && look.scale() == 1.6F && look.yOffset() == -0.25F,
                "颜色、渐变、大小、高度都从物品上取：" + look);

        EphemeraStatePacket back = roundTrip(new EphemeraStatePacket(4321, look));
        h.assertTrue(back.entityId == 4321 && back.look.equals(look), "包往返一致：" + back.look);
        EphemeraStatePacket none = roundTrip(new EphemeraStatePacket(7, EphemeraLook.NONE));
        h.assertTrue(none.look.isNone(), "卸下的包往返还是空");

        // 对面发来查不到的款式（比如旧版的按元素 id）：整包读完、按不带
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        raw.writeVarInt(9);
        raw.writeUtf("fire", 32);
        raw.writeUtf("fire", 32);
        raw.writeInt(0xFF0000);
        raw.writeInt(0xFF0000);
        raw.writeByte(0);
        raw.writeFloat(1.0F);
        raw.writeFloat(0.0F);
        EphemeraStatePacket old = EphemeraStatePacket.decode(raw);
        h.assertTrue(old.look.isNone() && raw.readableBytes() == 0, "未知款式按不带，字节读完");

        h.assertTrue(EphemeraLook.fromTag(look.toTag()).equals(look), "同步数据形式往返一致");
        h.assertTrue(EphemeraLook.fromTag(new CompoundTag()).isNone(), "空标签 = 不带");
        h.assertTrue(EphemeraLook.of(new ItemStack(Items.DIAMOND)).isNone(), "不是幻纹 = 不带");
        h.assertTrue(EphemeraLook.ofDefault("orbit", "bogus").element().equals(EphemeraType.DEFAULT_ELEMENT),
                "非法元素落回默认");
        EphemeraLook def = EphemeraLook.ofDefault("orbit", "ice");
        h.assertTrue(def.color1() == EphemeraType.defaultColor("ice") && def.color2() == def.color1()
                && def.gradient() == EphemeraItem.GRADIENT_OFF, "玄骸、盟友用元素默认色、渐变关");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void clientTableLifecycle(GameTestHelper h) {
        EphemeraStateTable t = new EphemeraStateTable();
        EphemeraLook a = EphemeraLook.ofDefault("surge", "fire");
        EphemeraLook b = EphemeraLook.ofDefault("vortex", "ice");
        // 包比实体先到：实体还不存在也照存
        t.put(987654, a);
        h.assertTrue(t.get(987654).equals(a) && t.size() == 1, "实体不在也照存");
        int rev = t.revision();
        t.put(987654, a);
        h.assertTrue(t.revision() == rev, "内容没变不算变");
        t.put(987654, b);
        h.assertTrue(t.revision() == rev + 1 && t.get(987654).equals(b), "换款更新那一条");
        t.put(987654, EphemeraLook.NONE);
        h.assertTrue(t.isEmpty() && t.get(987654).isNone(), "卸下删掉那一条");
        t.put(1, a);
        t.put(2, b);
        t.put(3, a);
        t.remove(2);
        h.assertTrue(t.size() == 2 && t.get(2).isNone(), "实体离开世界删那一条");
        t.clear();
        h.assertTrue(t.isEmpty() && t.get(1).isNone() && t.get(3).isNone(), "登出、世界卸载整表清空");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void changeSendsOnce(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        int s0 = EphemeraSync.sentCount();
        h.assertTrue(!EphemeraSync.poll(p) && EphemeraSync.sentCount() == s0, "没带幻纹不发");

        wm(p).setEphemera(eph(EphemeraType.PLUME, "fire"));
        h.assertTrue(EphemeraSync.poll(p) && EphemeraSync.sentCount() == s0 + 1, "装上发一次");
        h.assertTrue(!EphemeraSync.poll(p) && !EphemeraSync.poll(p), "没变不再发");

        wm(p).setOne(new ItemStack(Items.PAPER));
        h.assertTrue(!EphemeraSync.poll(p), "换模组（版本号变了）不发");

        ItemStack recolored = wm(p).getEphemera().copy();
        EphemeraItem.setColors(recolored, 0x00FFAA, 0x00FFAA);
        wm(p).setEphemera(recolored);
        h.assertTrue(EphemeraSync.poll(p) && !EphemeraSync.poll(p), "改颜色发一次");

        wm(p).setEphemera(eph(EphemeraType.ORBIT, "fire"));
        h.assertTrue(EphemeraSync.poll(p) && !EphemeraSync.poll(p), "换款发一次");

        // 登录、重生、换维度之后忘掉上次发的，下一轮给本人补一次
        EphemeraSync.onClone(new PlayerEvent.Clone(p, p, false));
        h.assertTrue(EphemeraSync.poll(p) && !EphemeraSync.poll(p), "重生后补一次");
        EphemeraSync.onChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(p, p.level().dimension(), p.level().dimension()));
        h.assertTrue(EphemeraSync.poll(p), "换维度后补一次");

        int before = EphemeraSync.sentCount();
        wm(p).setEphemera(ItemStack.EMPTY);
        h.assertTrue(EphemeraSync.poll(p) && EphemeraSync.sentCount() == before + 1, "卸下发一次（空）");
        h.assertTrue(!EphemeraSync.poll(p), "卸下后不再发");

        // 坏档里的东西（不是幻纹）当没带
        wm(p).setEphemera(new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(!EphemeraSync.poll(p) && EphemeraSync.lookOf(p).isNone(), "格里不是幻纹按不带");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void lateTrackerGetsState(GameTestHelper h) {
        ServerPlayer viewer = KuvaTestKit.player(h);
        ServerPlayer target = KuvaTestKit.player(h);
        int s0 = EphemeraSync.sentCount();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, target));
        h.assertTrue(EphemeraSync.sentCount() == s0, "对方没带不补");

        wm(target).setEphemera(eph(EphemeraType.WING, "poison"));
        EphemeraSync.poll(target);
        int s1 = EphemeraSync.sentCount();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, target));
        h.assertTrue(EphemeraSync.sentCount() == s1 + 1, "晚进视野的补发一次");
        h.assertTrue(!EphemeraSync.poll(target), "补发不算换款，不再广播");
        // 追踪的不是玩家（被追踪的是别的实体）不发
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, lich));
        h.assertTrue(EphemeraSync.sentCount() == s1 + 1, "玄骸走同步数据，不发状态包");
        lich.discard();
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void lichEphemeraSyncedData(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = new RequiemCard();
        card.beginLich("Vesk Arru", 2024L, 0, 10, "ice", "minecraft:plains", "minecraft:overworld");
        card.setLichEphemera("orbit");
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        h.assertTrue(lich.getEphemeraLook().isNone(), "没绑主人前不带");
        lich.bindOwner(p, card);
        EphemeraLook look = lich.getEphemeraLook();
        h.assertTrue(look.equals(EphemeraLook.ofDefault("orbit", "ice")), "有主的从卡上读款式，元素跟武器：" + look);

        // 同步数据打包 → 另一只实体解包（客户端收到的就是这一份）
        List<SynchedEntityData.DataValue<?>> dirty = lich.getEntityData().packDirty();
        h.assertTrue(dirty != null, "绑主人后有脏数据要发");
        KuvaMasterEntity mirror = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(mirror != null, "应当能造出玄骸");
        mirror.getEntityData().assignValues(dirty);
        h.assertTrue(mirror.getEphemeraLook().equals(look), "同步数据往返一致");
        h.assertTrue(!lich.shouldBeSaved(), "有主的不存盘，每次现身都从卡上重读");

        // 卡上是查不到的款式：按不带
        RequiemCard stale = new RequiemCard();
        stale.beginLich("Ilk Dovan", 77L, 0, 10, "poison", "minecraft:plains", "minecraft:overworld");
        stale.setLichEphemera("fire");
        KuvaMasterEntity old = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(old != null, "应当能造出玄骸");
        old.bindOwner(p, stale);
        h.assertTrue(old.getEphemeraLook().isNone(), "旧 id 按不带");

        // 盟友：名册条目里的款式，元素跟条目的武器元素；没定元素的按种子
        CompoundTag entry = new CompoundTag();
        entry.putLong(AllyRoster.SEED, 31337L);
        entry.putString(AllyRoster.NAME, "Nox Imra");
        entry.putInt(AllyRoster.RANK, 3);
        entry.putInt(AllyRoster.WEAPON, 0);
        entry.putString(AllyRoster.WEAPON_TYPE, "poison");
        entry.putString(AllyRoster.EPHEMERA, "coil");
        KuvaMasterEntity ally = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(ally != null, "应当能造出玄骸");
        ally.setupAlly(p, entry, 1.0D, 1.0D);
        h.assertTrue(ally.getEphemeraLook().equals(EphemeraLook.ofDefault("coil", "poison")), "盟友从名册读");
        entry.putString(AllyRoster.WEAPON_TYPE, "");
        KuvaMasterEntity ally2 = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(ally2 != null, "应当能造出玄骸");
        ally2.setupAlly(p, entry, 1.0D, 1.0D);
        h.assertTrue(ally2.getEphemeraLook().element().equals(EphemeraType.elementFor(31337L, "")),
                "盟友没定元素按种子");
        lich.discard();
        mirror.discard();
        old.discard();
        ally.discard();
        ally2.discard();
        h.succeed();
    }

    /** 假宿主：只给包围盒和姿态 */
    private record Host(float width, float height, EphemeraHost.Pose pose, boolean riding) implements EphemeraHost {
        public int id() {
            return 1;
        }

        public double x() {
            return 10.0D;
        }

        public double y() {
            return 64.0D;
        }

        public double z() {
            return -5.0D;
        }

        public float bodyYaw() {
            return 0.0F;
        }

        public double vx() {
            return 0.0D;
        }

        public double vy() {
            return 0.0D;
        }

        public double vz() {
            return 0.0D;
        }

        public boolean onGround() {
            return true;
        }

        public float swing() {
            return 0.0F;
        }

        public float hurt() {
            return 0.0F;
        }

        public boolean self() {
            return false;
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void anchorsFollowBoundingBox(GameTestHelper h) {
        EphemeraLook look = EphemeraLook.ofDefault("surge", "fire");
        EphemeraAnchor.Frame f = new EphemeraAnchor.Frame();
        EphemeraAnchor.BODY.frame(new Host(0.6F, 1.8F, EphemeraHost.Pose.STAND, false), look, f);
        h.assertTrue(Math.abs(f.scale - 1.0F) < 1.0E-5F && Math.abs(f.radius - 1.0F) < 1.0E-5F
                && Math.abs(f.y - 64.9D) < 1.0E-6D, "原版体型缩放 1、腰在 0.9：" + f.scale + " " + f.y);
        // 改体型的模组把包围盒放大一倍：缩放、半径、高度都跟着翻倍
        EphemeraAnchor.BODY.frame(new Host(1.2F, 3.6F, EphemeraHost.Pose.STAND, false), look, f);
        h.assertTrue(Math.abs(f.scale - 2.0F) < 1.0E-5F && Math.abs(f.radius - 2.0F) < 1.0E-5F
                && Math.abs(f.y - 65.8D) < 1.0E-6D, "两倍体型：" + f.scale + " " + f.radius + " " + f.y);
        // 潜行时包围盒 1.5，缩放仍是 1（按这个姿态的原版高算）
        EphemeraAnchor.HEAD.frame(new Host(0.6F, 1.5F, EphemeraHost.Pose.CROUCH, false), look, f);
        h.assertTrue(Math.abs(f.scale - 1.0F) < 1.0E-5F, "潜行不缩小：" + f.scale);
        h.assertTrue(!EphemeraAnchor.FEET.frame(new Host(0.6F, 1.8F, EphemeraHost.Pose.STAND, true), look, f),
                "骑乘时脚下不画");
        // 物品上的大小、高度
        ItemStack st = eph(EphemeraType.SURGE, "fire");
        EphemeraItem.setScale(st, 1.5F);
        EphemeraItem.setYOffset(st, 0.5F);
        EphemeraAnchor.FEET.frame(new Host(0.6F, 1.8F, EphemeraHost.Pose.STAND, false), EphemeraLook.of(st), f);
        h.assertTrue(Math.abs(f.scale - 1.5F) < 1.0E-5F && Math.abs(f.y - 64.52D) < 1.0E-5D,
                "手调大小乘上、高度加上：" + f.scale + " " + f.y);
        h.assertTrue(EphemeraAnchor.FEET.visibleInFirstPerson() && EphemeraAnchor.BACK.visibleInFirstPerson()
                && !EphemeraAnchor.BODY.visibleInFirstPerson() && !EphemeraAnchor.HEAD.visibleInFirstPerson(),
                "第一人称只留脚下、身后");
        h.succeed();
    }

    private static void fill(EphemeraStaging s, int base, int trail, int particle) {
        for (int i = 0; i < base; i++) {
            s.quad(EphemeraStaging.BASE, EphemeraStaging.TRANSLUCENT, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0,
                    0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233);
        }
        for (int i = 0; i < trail; i++) {
            s.quad(EphemeraStaging.TRAIL, EphemeraStaging.ADDITIVE, i, 0, 0, i, 0, 0, i, 1, 0, i, 1, 0,
                    0xFF445566, 0xFF445566, 0xFF445566, 0xFF445566);
        }
        for (int i = 0; i < particle; i++) {
            s.quad(EphemeraStaging.PARTICLE, EphemeraStaging.ADDITIVE, 0, i, 0, 0, i, 0, 0, i, 1, 0, i, 1,
                    0xFF778899, 0xFF778899, 0xFF778899, 0xFF778899);
        }
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void budgetTrimsParticlesThenTrail(GameTestHelper h) {
        EphemeraStaging s = new EphemeraStaging();
        s.clearFrame();
        fill(s, 100, 50, 30);
        h.assertTrue(s.commit(EphemeraTier.CAP_FULL) == 180 && s.lastDropped() == 0, "没超不砍");
        h.assertTrue(s.layer(EphemeraStaging.TRANSLUCENT).quads() == 100
                && s.layer(EphemeraStaging.ADDITIVE).quads() == 80, "分进两批");

        s.clearFrame();
        fill(s, 100, 80, 600);
        int kept = s.commit(EphemeraTier.CAP_FULL);
        h.assertTrue(kept == EphemeraTier.CAP_FULL && s.layer(EphemeraStaging.TRANSLUCENT).quads() == 100,
                "超了压回上限、底座一个不砍：" + kept);
        h.assertTrue(s.layer(EphemeraStaging.ADDITIVE).quads() == 500, "拖尾全留、粒子砍到 420");

        s.clearFrame();
        fill(s, 100, 80, 40);
        kept = s.commit(EphemeraTier.CAP_REDUCED);
        h.assertTrue(kept == EphemeraTier.CAP_REDUCED, "精简档上限 150：" + kept);
        // 粒子全砍，拖尾留 50 段，留下的是最先画的（最新的那一截）
        EphemeraStaging.Layer add = s.layer(EphemeraStaging.ADDITIVE);
        h.assertTrue(add.quads() == 50 && add.positions()[0] == 0.0F && add.positions()[49 * 12] == 49.0F,
                "先砍粒子，再从拖尾尾巴砍");

        s.clearFrame();
        fill(s, 200, 10, 10);
        kept = s.commit(EphemeraTier.CAP_REDUCED);
        h.assertTrue(kept == 200, "光底座就超了也不砍底座（整款不会消失）：" + kept);

        // 关掉加法：加法那批改进半透明批，透明度打六折
        s.clearFrame();
        s.additiveAllowed = false;
        fill(s, 0, 1, 0);
        s.commit(EphemeraTier.CAP_FULL);
        h.assertTrue(s.layer(EphemeraStaging.ADDITIVE).quads() == 0 && s.layer(EphemeraStaging.TRANSLUCENT).quads() == 1
                && (s.layer(EphemeraStaging.TRANSLUCENT).colors()[0] >>> 24) == 153, "关加法退半透明");

        // 第一人称贴近镜头淡出、潜行压暗
        s.clearFrame();
        s.additiveAllowed = true;
        s.nearFade = 1.0F;
        s.alphaMul = 0.3F;
        s.quad(EphemeraStaging.BASE, EphemeraStaging.TRANSLUCENT, 0, 0, 0, 0.5F, 0, 0, 2, 0, 0, 0, 0, 2,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
        s.commit(EphemeraTier.CAP_FULL);
        int[] c = s.layer(EphemeraStaging.TRANSLUCENT).colors();
        h.assertTrue((c[0] >>> 24) == 0 && (c[1] >>> 24) == Math.round(0.3F * 0.5F * 255) && (c[2] >>> 24) == Math.round(0.3F * 255),
                "贴脸的顶点透明、半格处半透明、远处只压暗：" + (c[0] >>> 24) + " " + (c[1] >>> 24) + " " + (c[2] >>> 24));
        h.succeed();
    }
}
