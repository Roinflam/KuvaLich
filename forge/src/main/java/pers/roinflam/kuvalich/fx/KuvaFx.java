package pers.roinflam.kuvalich.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;

import javax.annotation.Nullable;

/**
 * 服务端发音画效果包的入口（通用代码，不碰任何客户端类）
 *
 * <p>服务端只说「发生了什么」，声音、粒子、几何和屏幕效果都由客户端按时间轴放。
 * {@code a}、{@code b} 由调用方按 {@link FxId} 里的说明自己编码（例如攻击者写 {@code id + 1}）；
 * 效果包的 entity 字段由本类写入，传进来的是实体的原始 id，内部存成 id+1。</p>
 *
 * <p>网络通道还没建好（{@code KuvaLich.network == null}）时什么都不发。</p>
 */
public final class KuvaFx {

    private KuvaFx() {
    }

    /**
     * 发给追踪这只实体的玩家（实体是玩家时也发给本人）。演出原点取实体的身体中心，效果包的 entity 字段就是这只实体
     */
    public static void tracking(Entity e, FxId id, int a, int b) {
        if (e == null || e.level().isClientSide || KuvaLich.network == null) {
            return;
        }
        Vec3 c = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
        KuvaFxPacket pkt = new KuvaFxPacket(id.id, e.getId() + 1, (float) c.x, (float) c.y, (float) c.z, a, b);
        KuvaLich.network.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), pkt);
    }

    /**
     * 发给以 {@code p} 为中心、{@code radius} 格内的玩家
     *
     * @param entityId 效果包里带的实体的原始 id，小于 0 表示没有
     */
    public static void near(ServerLevel lvl, Vec3 p, double radius, FxId id, int entityId, int a, int b) {
        if (lvl == null || KuvaLich.network == null) {
            return;
        }
        KuvaFxPacket pkt = new KuvaFxPacket(id.id, entityId < 0 ? 0 : entityId + 1,
                (float) p.x, (float) p.y, (float) p.z, a, b);
        KuvaLich.network.send(PacketDistributor.NEAR.with(
                () -> new PacketDistributor.TargetPoint(p.x, p.y, p.z, radius, lvl.dimension())), pkt);
    }

    /** 同上，实体直接传对象（可以为 null，表示没有） */
    public static void near(ServerLevel lvl, Vec3 p, double radius, FxId id, @Nullable Entity entity, int a, int b) {
        near(lvl, p, radius, id, entity == null ? -1 : entity.getId(), a, b);
    }

    /**
     * 只发给一个玩家
     *
     * @param entityId 效果包里带的实体的原始 id，小于 0 表示没有
     */
    public static void player(ServerPlayer sp, FxId id, Vec3 p, int entityId, int a, int b) {
        if (sp == null || KuvaLich.network == null) {
            return;
        }
        KuvaFxPacket pkt = new KuvaFxPacket(id.id, entityId < 0 ? 0 : entityId + 1,
                (float) p.x, (float) p.y, (float) p.z, a, b);
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
    }
}
