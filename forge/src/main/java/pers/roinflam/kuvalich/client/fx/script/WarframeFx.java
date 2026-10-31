package pers.roinflam.kuvalich.client.fx.script;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaScreenFx;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.module.warframe.WarframeModuleHandler;
import pers.roinflam.kuvalich.network.packet.DamageInfo;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Map;

/**
 * 战甲与武器的几个小时刻：模组揭示、死亡抵抗、护盾破碎、处决、溅射、净化、冲刺武器
 *
 * <p>都是「亲手做了一件事、要有个回应」的时刻，分量比安魂演出小得多：单次粒子 20 个以内、几何至多 1 个，
 * 屏幕效果（四角括号、闪金）只给本人。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class WarframeFx {

    private static final RandomSource RANDOM = RandomSource.create();

    /** 模组稀有度色：普通铜 / 罕见银 / 稀有金 / Prime 白金 / 紫卡 */
    private static final int[] RARITY = {0xC08A5A, 0xC8D2DA, 0xFFC861, 0xE8ECFF, 0xC864FF};
    /** 紫卡的故障色差 */
    private static final int GLITCH_MAGENTA = 0xFF4FD8;
    private static final int GLITCH_CYAN = 0x4FE8FF;

    /** 护盾破碎判定：上一 tick 本地玩家的吸收值 */
    private static float lastAbsorption;

    private WarframeFx() {
    }

    // ==================== V19 模组揭示 ====================

    /** 模组揭示：a = 稀有度（0 普通 / 1 罕见 / 2 稀有 / 3 Prime / 4 紫卡），实体是揭示的玩家，坐标是手前方 */
    static void moduleReveal(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int rarity = Math.max(0, Math.min(4, p.a));
        Entity who = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 at = who != null ? handFront(who) : p.pos();
        boolean epic = rarity >= 3;
        int c = RARITY[rarity];
        FxTimeline tl = KuvaFxDirector.start("V19 reveal " + rarity, at, epic ? FxTier.A : FxTier.B);
        if (epic) {
            tl.sound(0, KuvaLichSounds.ITEM_MODULE_REVEAL_EPIC.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            float pitch = rarity == 0 ? 1.0F : rarity == 1 ? 1.06F : 1.12F;
            tl.sound(0, KuvaLichSounds.ITEM_MODULE_REVEAL.get(), SoundSource.PLAYERS, 0.8F, pitch);
        }
        tl.at(0, () -> {
            KuvaParticles.glow(at.x, at.y, at.z).color(c).alpha(0.9F).size(0.18F, 0.3F).life(8).spawn();
            for (int i = 0; i < 5; i++) {
                KuvaParticles.spark(at.x, at.y, at.z).vel(rnd(0.08D), 0.04D + RANDOM.nextDouble() * 0.06D, rnd(0.08D))
                        .drag(0.85F).color(FxPalette.WHITE).color2(c).size(0.16F, 0.05F)
                        .orient(KuvaParticles.Orient.VELOCITY).life(6 + RANDOM.nextInt(3)).spawn();
            }
            if (epic) {
                KuvaParticles.ring(at.x, at.y, at.z).color(c).alpha(0.85F).size(0.2F, 0.9F)
                        .sizeCurve(FxCurve.OUT_QUART).life(10).spawn();
                for (int i = 0; i < 8; i++) {
                    KuvaParticles.mote(at.x + rnd(0.25D), at.y + rnd(0.15D), at.z + rnd(0.25D))
                            .vel(0.0D, 0.03D + RANDOM.nextDouble() * 0.02D, 0.0D).color(c).alpha(0.9F)
                            .size(0.06F, 0.03F).life(20 + RANDOM.nextInt(10)).spawn();
                }
            }
            if (rarity == 4) {
                for (int i = 0; i < 4; i++) {
                    int g = (i & 1) == 0 ? GLITCH_MAGENTA : GLITCH_CYAN;
                    KuvaParticles.mote(at.x + rnd(0.3D), at.y + rnd(0.3D), at.z + rnd(0.3D)).color(g).alpha(0.9F)
                            .size(0.07F, 0.07F).alphaCurve(FxCurve.FLICKER).life(8 + RANDOM.nextInt(6)).spawn();
                }
                KuvaParticles.flare(at.x, at.y, at.z).color(c).alpha(0.8F).size(1.2F, 0.8F).life(4).spawn();
            }
        });
    }

    /** 实体的手前方：眼睛往视线方向 0.6 格、往下 0.35 格 */
    private static Vec3 handFront(Entity e) {
        Vec3 look = e.getLookAngle();
        return e.getEyePosition().add(look.x * 0.6D, look.y * 0.6D - 0.35D, look.z * 0.6D);
    }

    // ==================== V23 死亡抵抗 ====================

    /** 死亡抵抗：金色六边形护壳闪现 + 地面环 + 光点上升；本人另闪一下金 */
    static void lastStand(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity who = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 c = who != null ? who.position().add(0.0D, who.getBbHeight() * 0.5D, 0.0D) : p.pos();
        Vec3 foot = who != null ? who.position().add(0.0D, 0.05D, 0.0D) : c;
        float h = who != null ? who.getBbHeight() : 1.8F;
        FxTimeline tl = KuvaFxDirector.start("V23 last stand", c, FxTier.A);
        if (who != null) {
            tl.follow(who);
        }
        tl.sound(0, KuvaLichSounds.WARFRAME_LAST_STAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        // 0~200 ms：护壳上的六边形一片片亮起
        tl.every(0, 160, 40, ms -> {
            Vec3 base = who != null ? who.position().add(0.0D, h * 0.5D, 0.0D) : c;
            for (int i = 0; i < 3; i++) {
                double th = RANDOM.nextDouble() * Math.PI * 2.0D;
                double ph = Math.acos(RANDOM.nextDouble() * 1.6D - 0.8D);
                double r = 0.95D;
                KuvaParticles.hex(base.x + Math.sin(ph) * Math.cos(th) * r, base.y + Math.cos(ph) * r * h * 0.55D,
                                base.z + Math.sin(ph) * Math.sin(th) * r).color(FxPalette.ACCENT).alpha(0.85F)
                        .size(0.22F, 0.26F).variant(RANDOM.nextInt(2)).alphaCurve(FxCurve.HOLD_THEN_FADE).life(14)
                        .spawn();
            }
        });
        tl.at(200, () -> {
            Vec3 f = who != null ? who.position().add(0.0D, 0.05D, 0.0D) : foot;
            KuvaFxGeometry.ring(f).normal(KuvaFxGeometry.Normal.UP).radius(0.3F, 1.8F, FxCurve.OUT_QUART)
                    .thick(0.1F, 0.02F).color(FxPalette.ACCENT).alpha(0.9F, FxCurve.FADE_SQ).life(500).add();
            for (int i = 0; i < 6; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2.0D;
                KuvaParticles.mote(f.x + Math.cos(a) * 0.4D, f.y + 0.2D, f.z + Math.sin(a) * 0.4D)
                        .vel(0.0D, 0.04D + RANDOM.nextDouble() * 0.03D, 0.0D).color(FxPalette.ACCENT).alpha(0.9F)
                        .size(0.07F, 0.03F).life(24 + RANDOM.nextInt(8)).spawn();
            }
            tl.onlyFor(p.entityId(), () -> KuvaScreenFx.flash(FxPalette.ACCENT, 0.16F, 160, FxTier.A));
        });
        tl.until(1200);
    }

    // ==================== V24 护盾破碎（本人，客户端自己判） ====================

    /**
     * 本地玩家的吸收值从大于 0 变成 0、而且这一两 tick 刚挨了打（自然到时归零不算），并且装着护盾类战甲模组
     * （金苹果、吸收药水给的吸收不算）：四角青色括号闪一下 + 自身周围 8 片六边形碎片 + 破盾声
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lastAbsorption = 0.0F;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        float now = player.getAbsorptionAmount();
        if (lastAbsorption > 0.0F && now <= 0.0F && player.hurtTime >= 8 && hasShieldModule(player)) {
            shieldBreak(player);
        }
        lastAbsorption = now;
    }

    private static boolean hasShieldModule(LocalPlayer player) {
        Map<String, Double> attr = WarframeModuleHandler.getCachedAttributes(player);
        return attr.getOrDefault("shield", 0.0D) > 0.0D || attr.getOrDefault("fixedShield", 0.0D) > 0.0D;
    }

    private static void shieldBreak(LocalPlayer player) {
        if (!KuvaSoundPlayer.gate("shield_break", 1500)) {
            return;
        }
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.WARFRAME_SHIELD_BREAK.get(), 0.9F,
                KuvaSoundPlayer.jitter(1.0F, 0.03F)));
        KuvaScreenFx.edgeBrackets(FxPalette.TECH, 300, FxTier.A);
        Vec3 c = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);
        // 碎片只有第三人称看得到（第一人称时在相机里面）
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0D * i / 8.0D + rnd(0.2D);
            double y = rnd(0.6D);
            KuvaParticles.hex(c.x + Math.cos(a) * 0.7D, c.y + y, c.z + Math.sin(a) * 0.7D)
                    .vel(Math.cos(a) * 0.08D, 0.02D + y * 0.03D, Math.sin(a) * 0.08D).gravity(0.02F).drag(0.92F)
                    .spin((RANDOM.nextBoolean() ? 1 : -1) * 0.3F).color(FxPalette.TECH_PALE).color2(FxPalette.TECH)
                    .alpha(0.9F).size(0.2F, 0.12F).life(12 + RANDOM.nextInt(6)).tier(FxTier.A).spawn();
        }
    }

    // ==================== V25 处决（收割 / 斩杀） ====================

    /** 处决：一道 2.5 格的细光带在 60 ms 里自上而下描出来 + 火花；a = 0 收割 / 1 斩杀 */
    static void execute(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity victim = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 c = victim != null ? victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D) : p.pos();
        FxTimeline tl = KuvaFxDirector.start("V25 execute", c, FxTier.B);
        tl.sound(0, KuvaLichSounds.COMBAT_EXECUTE.get(), SoundSource.PLAYERS, 1.0F, KuvaSoundPlayer.jitter(1.0F, 0.03F));
        if (KuvaFxDirector.camDist(c) > KuvaFxDirector.LOD_CULL_DIST) {
            return;
        }
        tl.at(30, () -> {
            Vec3 top = c.add(0.0D, 1.25D, 0.0D);
            Vec3 bottom = c.add(0.0D, -1.25D, 0.0D);
            // 连线从 B 端往 A 端描：B 在上，就是自上而下
            KuvaFxGeometry.tether(bottom, top).draw(0, 0, 60).width(0.05F).color(FxPalette.WHITE)
                    .color2(FxPalette.KUVA).alpha(1.0F, FxCurve.FADE_SQ).life(360).add();
        });
        tl.at(90, () -> {
            for (int i = 0; i < 10; i++) {
                KuvaParticles.spark(c.x, c.y + rnd(0.6D), c.z).vel(rnd(0.15D), rnd(0.1D), rnd(0.15D)).drag(0.8F)
                        .color(FxPalette.WHITE).color2(FxPalette.KUVA).size(0.2F, 0.06F)
                        .orient(KuvaParticles.Orient.VELOCITY).life(5 + RANDOM.nextInt(4)).spawn();
            }
        });
    }

    // ==================== V28 溅射 ====================

    /** 溅射：地面一道细环，半径就是溅射半径（a = 半径 × 10），颜色取攻击者手里武器的元素 */
    static void splash(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity victim = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Entity attacker = p.b > 0 ? mc.level.getEntity(p.b - 1) : null;
        Vec3 foot = victim != null ? victim.position().add(0.0D, 0.05D, 0.0D) : p.pos();
        float r = Math.max(0.5F, Math.min(16.0F, p.a / 10.0F));
        int color = weaponColor(attacker);
        if (KuvaFxDirector.camDist(foot) > KuvaFxDirector.LOD_CULL_DIST) {
            return;
        }
        FxTimeline tl = KuvaFxDirector.start("V28 splash", foot, FxTier.C);
        tl.at(0, () -> KuvaFxGeometry.ring(foot).normal(KuvaFxGeometry.Normal.UP).radius(r * 0.3F, r, FxCurve.OUT_QUART)
                .thick(0.05F, 0.02F).color(color).alpha(0.8F, FxCurve.FADE_SQ).life(250).add());
    }

    // ==================== V29 净化 ====================

    /** 净化驱散：6 个白色光点从目标身上散开上升 */
    static void purge(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity victim = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 c = victim != null ? victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D) : p.pos();
        if (KuvaFxDirector.camDist(c) > KuvaFxDirector.LOD_CULL_DIST) {
            return;
        }
        FxTimeline tl = KuvaFxDirector.start("V29 purge", c, FxTier.C);
        tl.at(0, () -> {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2.0D * i / 6.0D + rnd(0.3D);
                KuvaParticles.mote(c.x, c.y, c.z).vel(Math.cos(a) * 0.06D, 0.05D + RANDOM.nextDouble() * 0.03D,
                                Math.sin(a) * 0.06D).drag(0.9F).color(FxPalette.BONE).alpha(0.95F).size(0.08F, 0.03F)
                        .life(14 + RANDOM.nextInt(6)).spawn();
            }
        });
    }

    // ==================== V31 冲刺武器 ====================

    /**
     * 冲刺：身后拖尾火花 + 起点地面环；打中了（b &gt; 0）再加一道冲击环和 8 个火花。
     * a = 0 旗帜 / 1 收割者·Prime，b = 这次冲刺打中的个数；实体是冲刺的玩家
     */
    static void dash(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity who = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 start = who != null ? who.position() : p.pos().subtract(0.0D, 0.9D, 0.0D);
        int color = weaponColor(who);
        boolean local = who != null && mc.player == who;
        double d = KuvaFxDirector.camDist(start);
        FxTimeline tl = KuvaFxDirector.start("V31 dash", start, FxTier.B);
        float pitch = p.a == 1 ? 0.94F : 1.0F;
        tl.sound(0, KuvaLichSounds.WEAPON_DASH.get(), SoundSource.PLAYERS, 1.0F, pitch);
        if (p.b > 0) {
            Vec3 c = start.add(0.0D, 0.9D, 0.0D);
            float hitPitch = KuvaSoundPlayer.jitter(1.0F, 0.03F);
            if (local) {
                // 自己的冲刺命中：定位但不随距离衰减
                tl.at(0, () -> CombatFx.playCombat(KuvaLichSounds.WEAPON_DASH_HIT.get(), c, d, true,
                        0.9F / CombatFx.LOCAL_GAIN, hitPitch, FxTier.B));
            } else {
                tl.sound(0, KuvaLichSounds.WEAPON_DASH_HIT.get(), SoundSource.PLAYERS, 0.9F, hitPitch);
            }
        }
        if (d > KuvaFxDirector.LOD_CULL_DIST) {
            return;
        }
        tl.at(0, () -> {
            Vec3 f = start.add(0.0D, 0.05D, 0.0D);
            KuvaFxGeometry.ring(f).normal(KuvaFxGeometry.Normal.UP).radius(0.3F, 1.2F, FxCurve.OUT_QUART)
                    .thick(0.07F, 0.02F).color(color).alpha(0.85F, FxCurve.FADE_SQ).life(300).add();
            if (p.b > 0) {
                KuvaParticles.ring(start.x, start.y + 0.9D, start.z).color(color).alpha(0.85F).size(0.3F, 0.9F)
                        .sizeCurve(FxCurve.OUT_QUART).life(8).spawn();
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2.0D * i / 8.0D;
                    KuvaParticles.spark(start.x, start.y + 0.9D, start.z).vel(Math.cos(a) * 0.22D, rnd(0.05D),
                                    Math.sin(a) * 0.22D).drag(0.82F).color(FxPalette.WHITE).color2(color)
                            .size(0.3F, 0.08F).orient(KuvaParticles.Orient.VELOCITY).life(6 + RANDOM.nextInt(3)).spawn();
                }
            }
        });
        // 冲出去的 300 ms 里在身后留 6 个拖尾火花
        tl.every(40, 290, 50, ms -> {
            if (who == null || who.isRemoved()) {
                return;
            }
            Vec3 v = who.getDeltaMovement();
            Vec3 back = v.lengthSqr() > 1.0E-4D ? v.normalize().scale(-0.5D) : who.getLookAngle().scale(-0.5D);
            Vec3 at = who.position().add(back.x, 0.7D + rnd(0.3D), back.z);
            KuvaParticles.spark(at.x, at.y, at.z).vel(back.x * 0.3D, 0.0D, back.z * 0.3D).drag(0.7F)
                    .color(FxPalette.WHITE).color2(color).size(0.45F, 0.12F).orient(KuvaParticles.Orient.VELOCITY)
                    .life(6).spawn();
        });
    }

    /** 冲刺武器蓄满（只给本人）：手前一个小光环 + 4 个光点 + 提示音 */
    static void chargeReady(LivingEntity who) {
        Vec3 at = handFront(who);
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.WEAPON_CHARGE_READY.get(), 0.8F, 1.0F));
        int color = weaponColor(who);
        KuvaParticles.ring(at.x, at.y, at.z).color(color).alpha(0.8F).size(0.12F, 0.35F).sizeCurve(FxCurve.OUT_QUART)
                .life(6).tier(FxTier.B).spawn();
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 0.5D * i;
            KuvaParticles.mote(at.x + Math.cos(a) * 0.15D, at.y, at.z + Math.sin(a) * 0.15D)
                    .vel(0.0D, 0.015D, 0.0D).color(color).alpha(0.9F).size(0.05F, 0.03F).life(10).tier(FxTier.B).spawn();
        }
    }

    /** 实体手里武器的元素主色；没有元素的武器（或不是赤毒武器）用骨白 */
    private static int weaponColor(Entity e) {
        if (e instanceof LivingEntity le) {
            ItemStack stack = le.getMainHandItem();
            if (!stack.isEmpty() && KuvaWeaponUtil.hasType(stack)) {
                int idx = DamageInfo.elementIndex(KuvaWeaponUtil.getType(stack));
                if (idx >= 0) {
                    return ElementFx.mainColor(idx);
                }
            }
        }
        return FxPalette.BONE;
    }

    private static double rnd(double half) {
        return (RANDOM.nextDouble() * 2.0D - 1.0D) * half;
    }
}
