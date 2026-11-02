package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.fx.KuvaParticleShaders;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaThrallRim;
import pers.roinflam.kuvalich.client.renderer.AbstractKuvaRenderer;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.mercy.KuvaStateKind;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 玄骸 / 奴仆身上机制的可见反馈：适应性抗性的护壁、「挡住了」、弱点命中、吸血与回血、击退、交战越久越明显的怒意
 *
 * <ul>
 *   <li><b>适应护壁</b>（服务端发包）：命中点那一面起一片六边形格子，层数越高亮的格子越多、越实；
 *       某类伤害第一次叠满时 19 格全亮，外加一圈光环、发光提亮和一声金属鸣响。</li>
 *   <li><b>挡住了</b>（服务端发包）：连击递减或单次伤害软上限削掉一大块时，命中点迸出一道十字白光（横长竖短）、
 *       一圈往外扩的白环、一串往攻击者那边弹回的火星，配一声低沉的金属闷响。</li>
 *   <li><b>弱点</b>（服务端按这一下实际吃到的倍率判了发包）：命中处炸开一圈金色的星形裂纹（70 ms 内从命中点往外裂开）、
 *       一下金色闪光、几片碎屑，附近的人都看得到。
 *       默认弱点是弓箭；开了「每只弱点抗性不同」以后按这只自己的弱点，所以不在客户端按伤害类型猜。</li>
 *   <li><b>抗性</b>（同上，只发给打它的那个人）：一个很小的偏转闪。</li>
 *   <li><b>奴仆固定减伤</b>（客户端自己判，只给打它的那个人看）：奴仆描边闪一下。</li>
 *   <li><b>吸血、击退</b>（服务端发包）：受击者胸口喷出一小团赤毒雾，一道红线连到玄骸胸口，一股雾流顺着它拉回去，
 *       到的那一刻红线断开回缩、它胸口一团红光、
 *       发光层提亮一下；击退明显时脚下先后两圈环、一圈贴地的雾。</li>
 *   <li><b>自然回血</b>（实体那边判涨幅）：胸口冒两个光点。</li>
 *   <li><b>怒意</b>（读同步的交战增幅千分比）：分 0~4 档，玄骸的发光层持续变亮、怒意纹路层越来越明显；
 *       2 档起身上不停往上飘赤红火星（档越高越密），3 档起身体轮廓外一圈一呼一吸的红光、脚下冒贴地的暗红雾；
 *       奴仆背囊冒光点。
 *       升到 2 档以上那一下玄骸脚下起一圈环、胸口炸开一圈火星。纹路层本身在 {@link KuvaFuryLayer}。</li>
 * </ul>
 *
 * <p>所有东西都贴着身体表面或脚下，一次性的最长 680 ms，头顶以上什么都不画（怒意的火星从腰到胸口冒出、飘不过头顶，轮廓外的红光到头顶已经淡没了），
 * 伤害数字从头顶往上飘，两者错开。几种反馈各占一种颜色：挡住白、弱点金、吸血和怒意赤毒红。
 * 倒地、处决中、离场时什么都不画。选项 {@code kuva_state_fx} 关掉后全部不画（纹路层、发光乘子也不加），护壁叠层、满层的声音也不放。
 * 特效强度「减少」时粒子减半、护壁只画加了层的和第一次叠满的；「最少」时只画第一次叠满的护壁和挡住的那道闪，
 * 没有纹路层，发光乘子按 1（怒意不加亮、叠满也不提亮）。声音只看开关，不看强度。</p>
 *
 * <p>护壁、偏转闪、裂纹是自己画的几何（{@code AFTER_PARTICLES} 阶段，加法混合、不写深度、照常做深度测试，隔墙看不见），
 * 关掉「加法发光」时改普通半透明，护壁 α 再乘 0.6。它们跟着实体走：记下生成时相对实体脚底的偏移，每帧按插值位置加回去。</p>
 *
 * <p>实体类两端都加载，不能直接引用这里：{@link #onDamageEvent}、{@link #onRegen} 由实体经 {@code DistExecutor} 的桥调用。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class KuvaStateFx {

    // ==================== 距离、限流 ====================

    /** 适应、挡住、吸血击退只画这么近的 */
    private static final double NEAR = 32.0D;
    /** 弱点、回血、奴仆光点、描边闪只画这么近的 */
    private static final double CLOSE = 24.0D;
    /** 弱点同一只最少隔多久（4 tick） */
    private static final long WEAK_GAP_MS = 200L;
    /** 奴仆描边闪同一只最少隔多久（8 tick） */
    private static final long RIM_GAP_MS = 400L;
    /** 弹射物 / 魔法偏转闪同一只最少隔多久（10 tick） */
    private static final long RESIST_GAP_MS = 500L;
    /** 吸血画过以后多久之内回血不再画（10 tick） */
    private static final long REGEN_AFTER_STRIKE_MS = 500L;
    /** 满层鸣响同一只最少隔多久 */
    private static final long FULL_SOUND_GAP_MS = 1000L;
    /** 挡住那一声同一只最少隔多久（服务端本来就 10 tick 才发一次，这里兜底） */
    private static final long GUARD_SOUND_GAP_MS = 250L;
    /** 吸血雾最快、最慢几 tick 飞到它胸口 */
    private static final float DRAIN_FAST_TICKS = 6.0F;
    private static final float DRAIN_SLOW_TICKS = 10.0F;
    /** 吸血那一下它胸口亮起来的时刻：雾流大半到了（毫秒） */
    private static final int DRAIN_ARRIVE_MS = 380;

    // ==================== 护壁 ====================

    /** 六边形外接圆半径（格） */
    private static final float HEX_R = 0.11F;
    /** 边线宽（格） */
    private static final float HEX_EDGE = 0.012F;
    /** 相邻两格之间留的缝（格），边线不叠在一起 */
    private static final float HEX_GAP = 0.004F;
    /** 整片沿法线往外推多少（格） */
    private static final double HEX_LIFT = 0.08D;
    /** 淡出时再往外漂多少（格） */
    private static final double HEX_DRIFT = 0.03D;
    /** 中心填充透明度上限，免得盖住伤害数字和模型 */
    private static final float HEX_FILL_CAP = 0.30F;
    private static final int SHIELDS_PER_ENTITY = 3;
    private static final int SHIELDS_TOTAL = 12;
    private static final int MARKS_TOTAL = 24;

    /** 平顶六边形网格的格心（以外接圆半径为单位）：第 0 圈 1 格、第 1 圈从正上方那格起顺时针 6 格、第 2 圈 12 格 */
    private static final float[] CELL_X = new float[19];
    private static final float[] CELL_Y = new float[19];
    private static final float[] HEX_COS = new float[7];
    private static final float[] HEX_SIN = new float[7];

    static {
        int[][] axial = {
                {0, 0},
                {0, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1},
                {0, 2}, {1, 1}, {2, 0}, {2, -1}, {2, -2}, {1, -2}, {0, -2}, {-1, -1}, {-2, 0}, {-2, 1}, {-2, 2}, {-1, 2}
        };
        float sqrt3 = (float) Math.sqrt(3.0D);
        for (int i = 0; i < axial.length; i++) {
            int q = axial[i][0], r = axial[i][1];
            CELL_X[i] = 1.5F * q;
            CELL_Y[i] = sqrt3 * (r + q * 0.5F);
        }
        for (int k = 0; k <= 6; k++) {
            double t = Math.toRadians(60.0D * k);
            HEX_COS[k] = (float) Math.cos(t);
            HEX_SIN[k] = (float) Math.sin(t);
        }
    }

    // ==================== 怒意 ====================

    /** 各档的千分比下限 */
    public static final int[] FURY_FLOOR = {0, 250, 750, 1500, 3000};
    /** 各档玄骸发光层的持续乘子 */
    private static final float[] FURY_GLOW = {1.0F, 1.2F, 1.5F, 1.85F, 2.3F};
    /** 各档纹路层的亮度（加法画法到 1 就封顶，4 档留出呼吸往上冲的余量） */
    private static final float[] FURY_LAYER = {0.0F, 0.12F, 0.45F, 0.75F, 0.9F};
    /** 档位变化后纹路层多久过渡完 */
    private static final float FURY_RAMP_MS = 1000.0F;
    /** 3 档起纹路层呼吸：3 档 1.2 Hz、±15%，4 档 1.6 Hz、±25% */
    private static final float[] FURY_BREATH_HZ = {0.0F, 0.0F, 0.0F, 1.2F, 1.6F};
    private static final float[] FURY_BREATH_SWING = {0.0F, 0.0F, 0.0F, 0.15F, 0.25F};
    /** 怒意火星每几 tick 冒一粒：2 档、3 档、4 档（4 档每次两粒；「减少」强度时间隔翻倍） */
    private static final int[] FURY_EMBER_EVERY = {0, 0, 3, 2, 1};
    /** 3 档起身体轮廓外那圈红光最亮多少（跟着纹路层的呼吸在 75%~100% 之间起伏） */
    private static final float[] FURY_AURA_ALPHA = {0.0F, 0.0F, 0.0F, 0.55F, 0.85F};
    /** 红光圆盘的半径（× 身高）、往身后推多远（× 身宽）：推到身子后面，中间整个被身体挡住，只从轮廓外透出来 */
    private static final float FURY_AURA_RADIUS = 0.7F;
    private static final float FURY_AURA_BACK = 0.9F;
    private static final int AURA_SEGMENTS = 24;
    private static final float[] AURA_COS = new float[AURA_SEGMENTS + 1];
    private static final float[] AURA_SIN = new float[AURA_SEGMENTS + 1];

    static {
        for (int i = 0; i <= AURA_SEGMENTS; i++) {
            double t = Math.PI * 2.0D * i / AURA_SEGMENTS;
            AURA_COS[i] = (float) Math.cos(t);
            AURA_SIN[i] = (float) Math.sin(t);
        }
    }

    // ==================== 状态 ====================

    private static final List<Shield> SHIELDS = new ArrayList<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<Fury> FURY = new Int2ObjectOpenHashMap<>();
    /** 调试命令设的本地显示档位：实体 id → 档位 */
    private static final Int2IntOpenHashMap FURY_OVERRIDE = new Int2IntOpenHashMap();
    private static final Int2LongOpenHashMap LAST_WEAK = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_RIM = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_RESIST = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_STRIKE = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_FULL_SOUND = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_GUARD_SOUND = new Int2LongOpenHashMap();
    /** 晚一点才做的事（吸血雾到了那一刻提亮、击退的第二圈环），到点后在客户端 tick 里做 */
    private static final List<Later> LATER = new ArrayList<>();
    private static final int LATER_CAP = 32;
    private static final RandomSource RANDOM = RandomSource.create();

    private static RenderType typeAdd;
    private static RenderType typeTranslucent;
    /** 调试用：护壁、偏转闪、裂纹的时间放慢几倍（截图看清楚这些不到 0.1 s 的东西），正常是 1 */
    private static float slow = 1.0F;

    static {
        FURY_OVERRIDE.defaultReturnValue(-1);
        LAST_WEAK.defaultReturnValue(Long.MIN_VALUE / 2);
        LAST_RIM.defaultReturnValue(Long.MIN_VALUE / 2);
        LAST_RESIST.defaultReturnValue(Long.MIN_VALUE / 2);
        LAST_STRIKE.defaultReturnValue(Long.MIN_VALUE / 2);
        LAST_FULL_SOUND.defaultReturnValue(Long.MIN_VALUE / 2);
        LAST_GUARD_SOUND.defaultReturnValue(Long.MIN_VALUE / 2);
    }

    private KuvaStateFx() {
    }

    private record Later(long atMs, Runnable run) {
    }

    private static void later(int ms, Runnable run) {
        if (LATER.size() < LATER_CAP) {
            LATER.add(new Later(KuvaFxDirector.clockMs() + ms, run));
        }
    }

    // ==================== 入口 ====================

    /** 状态效果包到了（参数编码见 {@link KuvaStateKind}） */
    public static void onPacket(KuvaFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        int kind = KuvaStateKind.kind(p.a);
        int param = KuvaStateKind.param(p.a);
        AbstractKuva target = level.getEntity(p.entityId()) instanceof AbstractKuva k ? k : null;
        if (target != null && quiet(target)) {
            return;
        }
        Vec3 at = p.pos();
        switch (kind) {
            case KuvaStateKind.ADAPT -> adapt(target, at, param, p.b > 0 ? level.getEntity(p.b - 1) : null);
            case KuvaStateKind.GUARD -> guard(target, at, param, p.b > 0 ? level.getEntity(p.b - 1) : null);
            case KuvaStateKind.STRIKE -> strike(target, at, param, p.b > 0 ? level.getEntity(p.b - 1) : null);
            case KuvaStateKind.WEAK -> {
                if (target != null) {
                    weakPoint(target, at);
                }
            }
            case KuvaStateKind.RESIST -> {
                if (target != null && mc.player != null && EffectsDisplayConfig.kuvaStateFx()) {
                    resistFlash(target, at, mc.player);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 客户端收到这只实体「受伤」的实体事件：只剩奴仆固定减伤的描边闪在这里自己判（它对什么伤害都一样）。
     * 弱点、抗性要看这一下实际吃到的倍率，由服务端发包（{@link KuvaStateKind#WEAK} / {@link KuvaStateKind#RESIST}）
     */
    public static void onDamageEvent(AbstractKuva entity, DamageSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (!EffectsDisplayConfig.kuvaStateFx() || mc.player == null || quiet(entity)) {
            return;
        }
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        boolean mine = owner == mc.player || (owner == null && direct == mc.player);
        if (mine && entity instanceof KuvaSlaveEntity && near(entity.position(), CLOSE)) {
            long now = KuvaFxDirector.clockMs();
            if (now - LAST_RIM.get(entity.getId()) >= RIM_GAP_MS) {
                LAST_RIM.put(entity.getId(), now);
                KuvaThrallRim.flash(entity.getId(), 120);
            }
        }
    }

    /** 检测到这只实体回了血（涨幅、间隔由实体那边判过） */
    public static void onRegen(AbstractKuva entity) {
        if (!EffectsDisplayConfig.kuvaStateFx() || quiet(entity) || minimal()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (now - LAST_STRIKE.get(entity.getId()) < REGEN_AFTER_STRIKE_MS) {
            return;
        }
        Vec3 c = chest(entity);
        if (!near(c, CLOSE)) {
            return;
        }
        int n = count(2);
        for (int i = 0; i < n; i++) {
            KuvaParticles.mote(c.x + spread(0.2D), c.y + spread(0.2D), c.z + spread(0.2D))
                    .color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.08F, 0.08F)
                    .vel(0.0D, 0.03D, 0.0D).drag(1.0F).life(20).alphaCurve(FxCurve.HOLD_THEN_FADE)
                    .tier(FxTier.C).spawn();
        }
    }

    // ==================== 适应护壁 ====================

    /**
     * 适应叠层：{@code at} 是命中点，{@code attacker} 用来定护壁朝哪一面（没有时按命中点在胸口哪一侧，再不行朝相机）
     */
    public static void adapt(@Nullable AbstractKuva target, Vec3 at, int param, @Nullable Entity attacker) {
        int layers = Mth.clamp(KuvaStateKind.adaptLayers(param), 0, 6);
        boolean added = KuvaStateKind.adaptAdded(param);
        boolean full = KuvaStateKind.adaptFirstFull(param);
        if (layers <= 0 && !full) {
            return;
        }
        // 选项关掉时叠层、满层的声音也不放；强度不管声音，「最少」档照样放
        if (!EffectsDisplayConfig.kuvaStateFx()) {
            return;
        }
        if (full) {
            int key = target != null ? target.getId() : Integer.MIN_VALUE;
            long now = KuvaFxDirector.clockMs();
            if (now - LAST_FULL_SOUND.get(key) >= FULL_SOUND_GAP_MS) {
                LAST_FULL_SOUND.put(key, now);
                KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_LICH_ADAPT_FULL.get(), SoundSource.HOSTILE,
                        at, 0.8F, 1.0F).tier(FxTier.B));
            }
        } else if (added && layers < 6) {
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_LICH_RESIST.get(), SoundSource.HOSTILE, at,
                    1.0F, KuvaSoundPlayer.jitter(1.0F + 0.04F * (layers - 1), 0.02F)).tier(FxTier.C));
        }

        if (!near(at, NEAR)) {
            return;
        }
        EffectsDisplayConfig.Intensity intensity = EffectsDisplayConfig.intensity();
        if (intensity == EffectsDisplayConfig.Intensity.MINIMAL && !full
                || intensity == EffectsDisplayConfig.Intensity.REDUCED && !full && !added) {
            return;
        }
        if (!full && KuvaFxDirector.heroActiveNear(at)) {
            return;
        }

        Vec3 n = facing(target, at, attacker);
        Shield s = new Shield();
        s.setFrame(n);
        s.attach(target, at);
        s.start = KuvaFxDirector.clockMs();
        s.full = full;
        if (full) {
            s.cells = 19;
            s.edgeA = 0.9F;
            s.fillA = HEX_FILL_CAP;
            s.inMs = 80;
            s.holdEnd = 280;
            s.outEnd = 680;
        } else {
            float boost = added ? 1.3F : 1.0F;
            s.cells = 1 + layers;
            s.edgeA = Math.min(1.0F, (0.30F + 0.09F * layers) * boost);
            s.fillA = Math.min(HEX_FILL_CAP, 0.05F * layers * boost);
            s.inMs = 60;
            s.holdEnd = added ? 240 : 140;
            s.outEnd = s.holdEnd + 240;
        }
        s.addScale = KuvaFxDirector.additiveScale(at);
        addShield(s);

        if (full) {
            Vec3 c = at.add(n.scale(HEX_LIFT));
            KuvaFxGeometry.ring(c).normal(n).radius(0.2F, 0.6F, FxCurve.OUT_CUBIC).thick(0.035F, 0.015F)
                    .color(FxPalette.KUVA_PALE).alpha(0.5F, FxCurve.FADE_SQ).life(250).tier(FxTier.B).core().add();
            // 「最少」档护壁和光环照画，发光不提亮
            if (target != null && intensity != EffectsDisplayConfig.Intensity.MINIMAL) {
                KuvaGlow.boost(target.getId(), 1.3F, 300);
            }
        }
    }

    private static void addShield(Shield s) {
        int mine = 0;
        Shield oldestMine = null;
        for (int i = 0, n = SHIELDS.size(); i < n; i++) {
            Shield o = SHIELDS.get(i);
            if (s.entity != null && o.entity == s.entity) {
                mine++;
                if (oldestMine == null) {
                    oldestMine = o;
                }
            }
        }
        if (mine >= SHIELDS_PER_ENTITY) {
            SHIELDS.remove(oldestMine);
        } else if (SHIELDS.size() >= SHIELDS_TOTAL) {
            SHIELDS.remove(0);
        }
        SHIELDS.add(s);
    }

    // ==================== 挡住了 ====================

    /**
     * 连击递减 / 软上限削掉了一大块：命中点一道十字白光、一圈往外扩的白环，一串火星往攻击者那边弹回，一声闷响。
     * 削掉一半以上（{@code heavy}）时光更宽、环更大、火星更多、声音更沉
     */
    public static void guard(@Nullable AbstractKuva target, Vec3 at, int param, @Nullable Entity attacker) {
        if (!EffectsDisplayConfig.kuvaStateFx()) {
            return;
        }
        boolean heavy = KuvaStateKind.guardPercent(param) >= 50;
        // 声音只看开关、不看强度（同护壁）；借叠层那一声往低压：叠层是越叠越高的「叮」，挡住是一声沉的「当」
        int key = target != null ? target.getId() : Integer.MIN_VALUE;
        long now = KuvaFxDirector.clockMs();
        if (now - LAST_GUARD_SOUND.get(key) >= GUARD_SOUND_GAP_MS) {
            LAST_GUARD_SOUND.put(key, now);
            KuvaSoundPlayer.play(KuvaSoundPlayer.at(KuvaLichSounds.ENTITY_LICH_RESIST.get(), SoundSource.HOSTILE, at,
                    0.9F, KuvaSoundPlayer.jitter(heavy ? 0.62F : 0.72F, 0.03F)).tier(FxTier.C));
        }
        if (!near(at, NEAR)) {
            return;
        }
        Vec3 n = facing(target, at, attacker);
        Vec3 p = at.add(n.scale(0.06D));
        float width = heavy ? 1.7F : 1.2F;
        int life = heavy ? 380 : 300;
        float addScale = KuvaFxDirector.additiveScale(at);
        // 横条两头衰减得快，白天只剩中间一点：底下再垫一层更宽更厚的淡晕，整条横光才撑得开
        Mark halo = Mark.flare(n, width * 1.1F, 0.32F, false, FxPalette.BONE, 0.4F, life);
        Mark across = Mark.flare(n, width, 0.13F, false, FxPalette.BONE, 1.0F, life);
        Mark upright = Mark.flare(n, width * 0.45F, 0.13F, true, FxPalette.BONE, 0.9F, life * 3 / 4);
        for (Mark m : new Mark[]{halo, across, upright}) {
            m.attach(target, p);
            m.addScale = addScale;
            addMark(m);
        }

        if (minimal()) {
            return;
        }
        KuvaFxGeometry.flash(p).radius(heavy ? 0.6F : 0.45F, 40).core(FxPalette.WHITE).color(FxPalette.BONE)
                .alpha(0.95F, FxCurve.FADE_SQ).life(200).tier(FxTier.B).add();
        KuvaFxGeometry.ring(p).normal(n).radius(0.15F, heavy ? 0.95F : 0.7F, FxCurve.OUT_CUBIC).thick(0.09F, 0.02F)
                .color(FxPalette.WHITE).color2(FxPalette.BONE).alpha(0.95F, FxCurve.FADE_SQ).life(280)
                .tier(FxTier.B).add();
        int sparks = count(heavy ? 14 : 9);
        for (int i = 0; i < sparks; i++) {
            double speed = 0.18D + RANDOM.nextDouble() * 0.16D;
            double sx = n.x + spread(0.55D), sy = n.y + spread(0.55D) + 0.2D, sz = n.z + spread(0.55D);
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-4D) {
                continue;
            }
            KuvaParticles.spark(p.x, p.y, p.z)
                    .vel(sx / len * speed, sy / len * speed, sz / len * speed)
                    .color(FxPalette.WHITE).color2(FxPalette.KUVA_PALE).size(0.55F, 0.14F)
                    .gravity(0.06F).drag(0.9F).life(9 + RANDOM.nextInt(8))
                    .orient(KuvaParticles.Orient.VELOCITY).tier(FxTier.C).spawn();
        }
    }

    /** 弹射物 / 魔法打在抗性上：只给打它的人看的一个很小的灰色偏转闪 */
    private static void resistFlash(AbstractKuva entity, @Nullable Vec3 directPos, Player viewer) {
        if (minimal()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (now - LAST_RESIST.get(entity.getId()) < RESIST_GAP_MS) {
            return;
        }
        Vec3 at = directPos != null ? clampToBox(entity.getBoundingBox(), directPos, chest(entity)) : chest(entity);
        if (!near(at, NEAR)) {
            return;
        }
        LAST_RESIST.put(entity.getId(), now);
        Vec3 n = facing(entity, at, viewer);
        Mark m = Mark.flare(n, 0.4F, 0.06F, false, FxPalette.ASH, 0.35F, 70);
        m.attach(entity, at.add(n.scale(0.04D)));
        m.addScale = KuvaFxDirector.additiveScale(at);
        addMark(m);
    }

    // ==================== 弱点 ====================

    /** 打中弱点：命中处炸开一圈金色星形裂纹、一下金色闪光、几片碎屑；附近的人都看得到 */
    public static void weakPoint(AbstractKuva entity, Vec3 hitPos) {
        if (!EffectsDisplayConfig.kuvaStateFx() || minimal() || quiet(entity)) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (now - LAST_WEAK.get(entity.getId()) < WEAK_GAP_MS) {
            return;
        }
        Vec3 c = chest(entity);
        Vec3 at = clampToBox(entity.getBoundingBox(), hitPos, c);
        if (!near(at, CLOSE)) {
            return;
        }
        LAST_WEAK.put(entity.getId(), now);
        Camera cam = KuvaFxDirector.camera();
        Vec3 toCam = cam != null ? cam.getPosition().subtract(at) : at.subtract(c);
        double tl = toCam.length();
        // 往相机那边抬一点：裂纹是平的，贴着包围盒画会有一半埋进模型里
        Vec3 p = tl < 1.0E-4D ? at : at.add(toCam.scale(0.05D / tl));
        Mark m = Mark.crack(toCam, count(5), 0.42F, 0.035F, FxPalette.ACCENT, 1.0F, 520);
        m.attach(entity, p);
        m.addScale = KuvaFxDirector.additiveScale(at);
        addMark(m);
        KuvaFxGeometry.flash(p).radius(0.4F, 30).core(FxPalette.WHITE).color(FxPalette.ACCENT)
                .alpha(0.85F, FxCurve.FADE_SQ).life(200).tier(FxTier.B).add();

        Vec3 out = at.subtract(c);
        double ol = out.length();
        out = ol < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : out.scale(1.0D / ol);
        int shards = count(6);
        for (int i = 0; i < shards; i++) {
            double vx = out.x + spread(0.7D), vy = out.y + spread(0.7D) + 0.3D, vz = out.z + spread(0.7D);
            double len = Math.max(1.0E-4D, Math.sqrt(vx * vx + vy * vy + vz * vz));
            float size = 0.07F + RANDOM.nextFloat() * 0.04F;
            KuvaParticles.shard(p.x, p.y, p.z)
                    .vel(vx / len * 0.12D, vy / len * 0.12D, vz / len * 0.12D)
                    .color(FxPalette.BONE).color2(FxPalette.ACCENT).size(size, size * 0.6F)
                    .gravity(0.05F).drag(0.95F).spin(0.35F).life(9 + RANDOM.nextInt(7)).tier(FxTier.C).spawn();
        }
    }

    // ==================== 吸血、击退 ====================

    /**
     * 玄骸 / 奴仆打中人：吸了血就有雾飞回它胸口，击退明显就在受击者脚下起环。
     * 真打时 {@code at} 是受击者脚底；演示命令发的是玄骸胸口朝下命令的人那一面，所以只要认得出受击者（b），
     * 雾的起点和环都以受击者为准，{@code at} 只在找不到受击者时兜底
     */
    public static void strike(@Nullable AbstractKuva attacker, Vec3 at, int param, @Nullable Entity victim) {
        if (attacker != null) {
            LAST_STRIKE.put(attacker.getId(), KuvaFxDirector.clockMs());
        }
        if (!EffectsDisplayConfig.kuvaStateFx() || minimal() || !near(at, NEAR)) {
            return;
        }
        int drain = KuvaStateKind.strikeDrainTenths(param);
        if (drain >= 5 && attacker != null) {
            Vec3 from = victim != null ? chest(victim) : at.add(0.0D, 1.0D, 0.0D);
            // 落点取它包围盒朝受击者那一面（胸口正中在模型里面，雾飞进去、亮光画在里面都会被身体挡住）
            Vec3 chest = chest(attacker);
            Vec3 to = attacker.getBoundingBox().clip(from, chest).orElse(chest);
            Vec3 path = to.subtract(from);
            // 受击者胸口先喷出一小团
            int puffs = count(3);
            for (int i = 0; i < puffs; i++) {
                KuvaParticles.mist(from.x + spread(0.1D), from.y + spread(0.1D), from.z + spread(0.1D))
                        .vel(spread(0.03D), spread(0.03D), spread(0.03D)).drag(0.85F).gravity(0.0F)
                        .color(FxPalette.KUVA_DEEP).color2(FxPalette.KUVA).alpha(0.55F).size(0.3F, 0.45F).life(8)
                        .tier(FxTier.C).spawn();
            }
            // 一道红线 0.22 s 内从受击者胸口描到它胸口，绷紧一下，雾流到了再从中间断开回缩。
            // 雾是半透明、吃光照的，白天露天淡得几乎看不见，这条加法发光的线才是主体
            KuvaFxGeometry.tether(to, from).draw(0, 0, 220).boost(220, 1.8F, 120).snap(0, DRAIN_ARRIVE_MS + 60)
                    .width(0.06F).color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA)
                    .alpha(1.0F, FxCurve.HOLD_THEN_FADE).life(DRAIN_ARRIVE_MS + 260).tier(FxTier.B).add();
            // 雾团快慢不一（6~10 tick 飞到），一出手就拉成一条从受击者连到它胸口的雾流；夹着几粒亮点
            int n = count(12);
            for (int i = 0; i < n; i++) {
                float ticks = DRAIN_FAST_TICKS + (DRAIN_SLOW_TICKS - DRAIN_FAST_TICKS) * i / Math.max(1, n - 1);
                Vec3 d = path.scale(1.0D / ticks);
                KuvaParticles.mist(from.x + spread(0.1D), from.y + spread(0.1D), from.z + spread(0.1D))
                        .vel(d.x, d.y, d.z).drag(1.0F).gravity(0.0F)
                        .color(FxPalette.KUVA).alpha(0.85F).size(0.45F, 0.3F).life(Math.round(ticks) + 1)
                        .alphaCurve(FxCurve.HOLD_THEN_FADE).tier(FxTier.C).spawn();
            }
            int motes = count(7);
            for (int i = 0; i < motes; i++) {
                float ticks = DRAIN_FAST_TICKS - 1.0F + (DRAIN_SLOW_TICKS - DRAIN_FAST_TICKS) * i / Math.max(1, motes - 1);
                Vec3 d = path.scale(1.0D / ticks);
                KuvaParticles.mote(from.x + spread(0.08D), from.y + spread(0.08D), from.z + spread(0.08D))
                        .vel(d.x, d.y, d.z).drag(1.0F)
                        .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA_PALE).size(0.15F, 0.06F)
                        .life(Math.round(ticks) + 1).alphaCurve(FxCurve.HOLD_THEN_FADE).tier(FxTier.C).spawn();
            }
            // 雾流大半到了的那一刻它胸口一团红光、发光层提亮，所以光晕前 DRAIN_ARRIVE_MS 透明
            KuvaFxGeometry.glow(to).size(1.1F, 0.7F, FxCurve.LINEAR)
                    .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA)
                    .alphaFn(age -> age < DRAIN_ARRIVE_MS ? 0.0F
                            : 0.85F * FxCurve.fadeOut((age - DRAIN_ARRIVE_MS) / 240.0F))
                    .life(DRAIN_ARRIVE_MS + 240).tier(FxTier.B).add();
            int id = attacker.getId();
            later(DRAIN_ARRIVE_MS, () -> KuvaGlow.boost(id, 1.6F, 380));
        }
        if (KuvaStateKind.strikeHeavyKnockback(param)) {
            Vec3 feet = (victim != null ? victim.position() : at).add(0.0D, 0.05D, 0.0D);
            KuvaFxGeometry.ring(feet).normal(KuvaFxGeometry.Normal.UP)
                    .radius(0.4F, 1.6F, FxCurve.OUT_CUBIC).thick(0.14F, 0.05F)
                    .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).alpha(0.6F, FxCurve.FADE_SQ).life(280)
                    .tier(FxTier.C).add();
            later(70, () -> KuvaFxGeometry.ring(feet).normal(KuvaFxGeometry.Normal.UP)
                    .radius(0.2F, 1.0F, FxCurve.OUT_CUBIC).thick(0.08F, 0.03F)
                    .color(FxPalette.KUVA).alpha(0.45F, FxCurve.FADE_SQ).life(220).tier(FxTier.C).add());
            int n = count(8);
            double phase = RANDOM.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < n; i++) {
                double t = phase + i * Math.PI * 2.0D / n;
                KuvaParticles.mist(feet.x, feet.y + 0.1D, feet.z)
                        .vel(Math.cos(t) * 0.1D, 0.0D, Math.sin(t) * 0.1D).drag(0.9F)
                        .color(FxPalette.KUVA_DEEP).alpha(0.55F).size(0.45F, 0.9F).life(14).tier(FxTier.C).spawn();
            }
        }
    }

    // ==================== 怒意 ====================

    /** 调试用：把自己画的护壁、偏转闪、裂纹放慢 {@code factor} 倍（1 = 正常；粒子和别的几何不受影响） */
    public static void setSlowMotion(float factor) {
        slow = Math.max(1.0F, factor);
    }

    /** 千分比 → 档位 0~4 */
    public static int furyTierOf(int permille) {
        for (int t = FURY_FLOOR.length - 1; t > 0; t--) {
            if (permille >= FURY_FLOOR[t]) {
                return t;
            }
        }
        return 0;
    }

    /** 这只此刻显示的怒意档位（调试命令设过的优先） */
    public static int furyTier(AbstractKuva e) {
        int o = FURY_OVERRIDE.get(e.getId());
        return o >= 0 ? o : furyTierOf(e.getFuryPermille());
    }

    /** 本地把这只的显示档位钉在 {@code tier}（调试用），−1 = 取消，回到同步的值 */
    public static void setFuryOverride(int entityId, int tier) {
        if (tier < 0) {
            FURY_OVERRIDE.remove(entityId);
        } else {
            FURY_OVERRIDE.put(entityId, Mth.clamp(tier, 0, 4));
        }
    }

    /** 纹路层此刻的亮度（已含档位过渡和 3 档起的呼吸，可能略超过 1，画的时候封顶）；没有怒意或不该画时为 0 */
    public static float furyLayerLevel(AbstractKuva e) {
        Fury f = FURY.get(e.getId());
        if (f == null) {
            return 0.0F;
        }
        long now = KuvaFxDirector.clockMs();
        float k = FxCurve.clamp01((now - f.layerStart) / FURY_RAMP_MS);
        float a = f.layerFrom + (f.layerTo - f.layerFrom) * k;
        int t = Mth.clamp(f.shown, 0, 4);
        if (FURY_BREATH_SWING[t] > 0.0F) {
            a *= 1.0F + FURY_BREATH_SWING[t] * Mth.sin(now / 1000.0F * FURY_BREATH_HZ[t] * Mth.TWO_PI);
        }
        return Math.max(0.0F, a);
    }

    /** 每只实体的怒意记录 */
    private static final class Fury {
        /** 上一次看到的档位，−1 = 还没看到过 */
        int synced = -1;
        /** 发光乘子、纹路层正在按哪一档显示（倒地、关掉选项、强度「最少」时是 0） */
        int shown;
        float layerFrom;
        float layerTo;
        long layerStart;
    }

    private static void tickFury(ClientLevel level) {
        boolean enabled = EffectsDisplayConfig.kuvaStateFx();
        boolean particles = !minimal();
        Camera cam = KuvaFxDirector.camera();
        Vec3 cp = cam != null ? cam.getPosition() : null;
        long now = KuvaFxDirector.clockMs();
        for (Entity ent : level.entitiesForRendering()) {
            if (!(ent instanceof AbstractKuva k) || k.getId() < 0) {
                continue;
            }
            int id = k.getId();
            int tier = furyTier(k);
            Fury f = FURY.get(id);
            if (f == null) {
                f = new Fury();
                FURY.put(id, f);
            }
            boolean master = k instanceof KuvaMasterEntity;
            boolean eligible = enabled && k.isAlive() && !k.isRemoved() && k.getMercyState() == MercyState.NONE;
            double d2 = cp == null ? 0.0D : cp.distanceToSqr(k.getX(), k.getY(), k.getZ());

            if (f.synced >= 0 && tier > f.synced && tier >= 2 && eligible && master && particles && d2 <= NEAR * NEAR) {
                furyRise(k, tier);
            }
            f.synced = tier;

            // 「最少」档按 0 档显示：发光乘子回 1、纹路层淡掉，档位记录照常跟着同步值走
            int show = eligible && master && particles ? tier : 0;
            if (show != f.shown) {
                KuvaGlow.sustain(id, FURY_GLOW[show]);
                f.layerFrom = furyLayerLevelRaw(f, now);
                f.layerTo = FURY_LAYER[show];
                f.layerStart = now;
                f.shown = show;
            }

            if (master && eligible && particles && tier >= 2 && d2 <= CLOSE * CLOSE) {
                furyEmbers(k, tier);
            }
            if (k instanceof KuvaSlaveEntity slave && eligible && particles && tier >= 2 && d2 <= CLOSE * CLOSE) {
                int period = tier >= 4 ? 2 : tier == 3 ? 4 : 6;
                if ((k.tickCount + id) % period == 0) {
                    slaveMote(slave, tier);
                }
            }
        }
    }

    /** 升到 2 档以上那一下：脚下一圈环（4 档再套一圈）、胸口一圈火星炸开、发光层冲一下 */
    private static void furyRise(AbstractKuva k, int tier) {
        Vec3 feet = k.position().add(0.0D, 0.05D, 0.0D);
        KuvaFxGeometry.ring(feet).normal(KuvaFxGeometry.Normal.UP)
                .radius(0.6F, tier >= 4 ? 2.6F : 2.0F, FxCurve.OUT_CUBIC).thick(0.16F, 0.05F)
                .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).alpha(0.65F, FxCurve.FADE_SQ).life(360)
                .tier(FxTier.B).add();
        if (tier >= 4) {
            later(90, () -> KuvaFxGeometry.ring(feet).normal(KuvaFxGeometry.Normal.UP)
                    .radius(0.4F, 1.6F, FxCurve.OUT_CUBIC).thick(0.1F, 0.03F)
                    .color(FxPalette.KUVA).alpha(0.5F, FxCurve.FADE_SQ).life(300).tier(FxTier.B).add());
        }
        KuvaGlow.boost(k.getId(), 1.9F, 400);
        Vec3 c = chest(k);
        int n = count(12);
        double phase = RANDOM.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < n; i++) {
            double t = phase + i * Math.PI * 2.0D / n;
            double speed = 0.1D + RANDOM.nextDouble() * 0.06D;
            KuvaParticles.mote(c.x + Math.cos(t) * 0.3D, c.y + spread(0.3D), c.z + Math.sin(t) * 0.3D)
                    .vel(Math.cos(t) * speed, 0.02D + RANDOM.nextDouble() * 0.04D, Math.sin(t) * speed).drag(0.88F)
                    .color(FxPalette.KUVA_PALE).color2(FxPalette.KUVA).size(0.12F, 0.03F).life(10 + RANDOM.nextInt(7))
                    .alphaCurve(FxCurve.HOLD_THEN_FADE).tier(FxTier.C).spawn();
        }
    }

    /**
     * 2 档起玄骸身上不停往上飘的赤红火星：从腰到胸口的身体边缘冒出，往外、往上飘大半格就灭（飘不过头顶）；
     * 4 档颜色偏橙、每次两粒。3 档起身体轮廓外一圈一呼一吸的红光、脚下冒一团贴地往外散的暗红雾
     */
    private static void furyEmbers(AbstractKuva e, int tier) {
        int every = FURY_EMBER_EVERY[tier];
        if (EffectsDisplayConfig.intensity() == EffectsDisplayConfig.Intensity.REDUCED) {
            every *= 2;
        }
        int phase = e.tickCount + e.getId();
        if (phase % every == 0) {
            int per = tier >= 4 ? 2 : 1;
            double r = e.getBbWidth() * 0.5D;
            for (int i = 0; i < per; i++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
                double ox = Math.cos(ang), oz = Math.sin(ang);
                double y = e.getY() + e.getBbHeight() * (0.2D + RANDOM.nextDouble() * 0.55D);
                KuvaParticles.mote(e.getX() + ox * r, y, e.getZ() + oz * r)
                        .vel(ox * 0.015D, 0.035D + RANDOM.nextDouble() * 0.025D, oz * 0.015D).drag(0.96F)
                        .color(tier >= 4 ? FxPalette.EMBER : FxPalette.KUVA_HOT).color2(FxPalette.KUVA)
                        .size(0.26F, 0.06F).life(14 + RANDOM.nextInt(9)).alphaCurve(FxCurve.HOLD_THEN_FADE)
                        .tier(FxTier.C).spawn();
            }
        }
        if (tier >= 3 && phase % (tier >= 4 ? 4 : 8) == 0) {
            double ang = RANDOM.nextDouble() * Math.PI * 2.0D;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            double r = e.getBbWidth() * 0.4D;
            KuvaParticles.mist(e.getX() + ox * r, e.getY() + 0.1D, e.getZ() + oz * r)
                    .vel(ox * 0.03D, 0.0D, oz * 0.03D).drag(0.94F)
                    .color(FxPalette.KUVA_DEEP).alpha(0.4F).size(0.5F, 1.0F).life(20).tier(FxTier.C).spawn();
        }
    }

    private static float furyLayerLevelRaw(Fury f, long now) {
        float k = FxCurve.clamp01((now - f.layerStart) / FURY_RAMP_MS);
        return f.layerFrom + (f.layerTo - f.layerFrom) * k;
    }

    /** 奴仆背囊冒一个光点：身体中心往后 0.3 格、往上 0.15 格；3 档起往外飘一点 */
    private static void slaveMote(KuvaSlaveEntity e, int tier) {
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        double bx = Mth.sin(yaw) * 0.3D, bz = -Mth.cos(yaw) * 0.3D;
        double x = e.getX() + bx, y = e.getY() + e.getBbHeight() * 0.5D + 0.15D, z = e.getZ() + bz;
        double drift = tier >= 3 ? 0.04D : 0.0D;
        KuvaParticles.mote(x + spread(0.05D), y, z + spread(0.05D))
                .vel(bx / 0.3D * drift + spread(drift * 0.5D), 0.04D, bz / 0.3D * drift + spread(drift * 0.5D))
                .drag(1.0F).color(FxPalette.KUVA).size(0.11F, 0.08F).life(16).alphaCurve(FxCurve.HOLD_THEN_FADE)
                .tier(FxTier.C).spawn();
    }

    // ==================== 事件 ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearAll();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        tickFury(mc.level);
        long now = KuvaFxDirector.clockMs();
        for (int i = 0; i < LATER.size(); ) {
            Later l = LATER.get(i);
            if (now >= l.atMs()) {
                LATER.remove(i);
                l.run().run();
            } else {
                i++;
            }
        }
        SHIELDS.removeIf(s -> (now - s.start) / slow >= s.outEnd);
        MARKS.removeIf(m -> (now - m.start) / slow >= m.life);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        int id = event.getEntity().getId();
        FURY.remove(id);
        FURY_OVERRIDE.remove(id);
        LAST_WEAK.remove(id);
        LAST_RIM.remove(id);
        LAST_RESIST.remove(id);
        LAST_STRIKE.remove(id);
        LAST_FULL_SOUND.remove(id);
        LAST_GUARD_SOUND.remove(id);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    private static void clearAll() {
        SHIELDS.clear();
        MARKS.clear();
        FURY.clear();
        FURY_OVERRIDE.clear();
        LAST_WEAK.clear();
        LAST_RIM.clear();
        LAST_RESIST.clear();
        LAST_STRIKE.clear();
        LAST_FULL_SOUND.clear();
        LAST_GUARD_SOUND.clear();
        LATER.clear();
    }

    // ==================== 画 ====================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        boolean auras = furyAuraShown();
        if (SHIELDS.isEmpty() && MARKS.isEmpty() && !auras) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        boolean additive = EffectsDisplayConfig.additiveGlow() && KuvaParticleShaders.geometry() != null;
        RenderType type = additive ? typeAdd() : typeTranslucent();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Draw d = DRAW;
        d.vc = buffers.getBuffer(type);
        d.m = event.getPoseStack().last().pose();
        Vec3 cp = event.getCamera().getPosition();
        d.cx = cp.x;
        d.cy = cp.y;
        d.cz = cp.z;
        float pt = event.getPartialTick();

        // 粒子阶段原版可能已把相机旋转乘进全局模型视图矩阵，顶点又按事件给的姿态矩阵变换一遍就转了两次；临时换成单位阵
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            if (auras) {
                drawFuryAuras(d, mc, event.getCamera(), pt, now, additive);
            }
            for (int i = 0, n = SHIELDS.size(); i < n; i++) {
                Shield s = SHIELDS.get(i);
                float age = (now - s.start) / slow;
                if (age >= 0.0F && age < s.outEnd) {
                    s.draw(d, age, pt, additive);
                }
            }
            for (int i = 0, n = MARKS.size(); i < n; i++) {
                Mark m = MARKS.get(i);
                float age = (now - m.start) / slow;
                if (age >= 0.0F && age < m.life) {
                    m.draw(d, age, pt, additive);
                }
            }
            buffers.endBatch(type);
        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 有没有哪只此刻按 3 档以上显示（要画轮廓外的红光）；「最少」强度不画 */
    private static boolean furyAuraShown() {
        if (FURY.isEmpty() || minimal()) {
            return false;
        }
        for (Fury f : FURY.values()) {
            if (f.shown >= 3) {
                return true;
            }
        }
        return false;
    }

    /**
     * 3 档起玄骸身体轮廓外一圈红光：朝相机的圆盘，中心在腰上、沿视线推到身子后面，中间整个被身体挡住，只从轮廓外透出来。
     * 每帧按插值位置画（跟着它走、不拖尾），亮度跟纹路层同一个呼吸，档位变了 1 s 内淡进淡出
     */
    private static void drawFuryAuras(Draw d, Minecraft mc, Camera cam, float pt, long now, boolean additive) {
        Vec3 left = new Vec3(cam.getLeftVector());
        Vec3 up = new Vec3(cam.getUpVector());
        for (Int2ObjectMap.Entry<Fury> en : FURY.int2ObjectEntrySet()) {
            Fury f = en.getValue();
            if (f.shown < 3) {
                continue;
            }
            if (!(mc.level.getEntity(en.getIntKey()) instanceof KuvaMasterEntity k) || quiet(k)
                    || mc.player != null && k.isInvisibleTo(mc.player)) {
                continue;
            }
            int t = Math.min(f.shown, 4);
            float ramp = FxCurve.clamp01((now - f.layerStart) / FURY_RAMP_MS);
            float breath = 0.875F + 0.125F * Mth.sin(now / 1000.0F * FURY_BREATH_HZ[t] * Mth.TWO_PI);
            double x = Mth.lerp(pt, k.xOld, k.getX());
            double y = Mth.lerp(pt, k.yOld, k.getY()) + k.getBbHeight() * 0.5D;
            double z = Mth.lerp(pt, k.zOld, k.getZ());
            float a = FURY_AURA_ALPHA[t] * ramp * breath * AbstractKuvaRenderer.bodyAlpha(k, pt)
                    * (additive ? KuvaFxDirector.additiveScale(x, y, z) : 0.6F);
            if (a <= 0.003F) {
                continue;
            }
            double dx = x - d.cx, dy = y - d.cy, dz = z - d.cz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1.0E-3D) {
                double back = k.getBbWidth() * FURY_AURA_BACK / len;
                x += dx * back;
                y += dy * back;
                z += dz * back;
            }
            auraDisc(d, x, y, z, left, up, k.getBbHeight() * FURY_AURA_RADIUS, FxPalette.KUVA, a);
        }
    }

    /** 朝相机的圆盘：中心 a → 45% 半径处 0.6a → 边缘 0 */
    private static void auraDisc(Draw d, double x, double y, double z, Vec3 left, Vec3 up, float r, int rgb, float a) {
        float rm = r * 0.45F;
        float am = a * 0.6F;
        for (int i = 0; i < AURA_SEGMENTS; i++) {
            double c0 = AURA_COS[i], s0 = AURA_SIN[i], c1 = AURA_COS[i + 1], s1 = AURA_SIN[i + 1];
            double ax = left.x * c0 + up.x * s0, ay = left.y * c0 + up.y * s0, az = left.z * c0 + up.z * s0;
            double bx = left.x * c1 + up.x * s1, by = left.y * c1 + up.y * s1, bz = left.z * c1 + up.z * s1;
            d.v(x, y, z, rgb, a);
            d.v(x, y, z, rgb, a);
            d.v(x + bx * rm, y + by * rm, z + bz * rm, rgb, am);
            d.v(x + ax * rm, y + ay * rm, z + az * rm, rgb, am);
            d.v(x + ax * rm, y + ay * rm, z + az * rm, rgb, am);
            d.v(x + bx * rm, y + by * rm, z + bz * rm, rgb, am);
            d.v(x + bx * r, y + by * r, z + bz * r, rgb, 0.0F);
            d.v(x + ax * r, y + ay * r, z + az * r, rgb, 0.0F);
        }
    }

    /** 一帧的绘制上下文（只在渲染线程用） */
    private static final class Draw {
        VertexConsumer vc;
        Matrix4f m;
        double cx, cy, cz;

        void v(double x, double y, double z, int rgb, float a) {
            vc.vertex(m, (float) (x - cx), (float) (y - cy), (float) (z - cz))
                    .color(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F,
                            Mth.clamp(a, 0.0F, 1.0F))
                    .endVertex();
        }
    }

    private static final Draw DRAW = new Draw();

    /** 贴在实体上的东西：记下生成时相对实体脚底的偏移，每帧按插值位置加回去；实体没了就停在最后的位置 */
    private abstract static class Attached {
        @Nullable
        Entity entity;
        double ox, oy, oz;
        double px, py, pz;
        long start;
        float addScale = 1.0F;
        /** 法线（朝攻击者 / 相机）、平面里的右、上 */
        double nx, ny, nz, ux, uy, uz, vx, vy, vz;

        /** 由法线定平面基：右 = 世界上方 × 法线（从法线那头看过来的右手方向），上 = 法线 × 右 */
        void setFrame(Vec3 n) {
            nx = n.x;
            ny = n.y;
            nz = n.z;
            double rx = nz, rz = -nx;
            double rl = Math.sqrt(rx * rx + rz * rz);
            if (rl < 1.0E-3D) {
                ux = 1.0D;
                uy = 0.0D;
                uz = 0.0D;
            } else {
                ux = rx / rl;
                uy = 0.0D;
                uz = rz / rl;
            }
            vx = ny * uz - nz * uy;
            vy = nz * ux - nx * uz;
            vz = nx * uy - ny * ux;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            vx /= vl;
            vy /= vl;
            vz /= vl;
            ux = vy * nz - vz * ny;
            uy = vz * nx - vx * nz;
            uz = vx * ny - vy * nx;
        }

        void attach(@Nullable Entity e, Vec3 at) {
            this.entity = e;
            this.px = at.x;
            this.py = at.y;
            this.pz = at.z;
            if (e != null) {
                this.ox = at.x - e.getX();
                this.oy = at.y - e.getY();
                this.oz = at.z - e.getZ();
            }
        }

        /** 把此刻的位置写进 px / py / pz */
        void follow(float pt) {
            Entity e = this.entity;
            if (e != null && !e.isRemoved()) {
                px = Mth.lerp(pt, e.xOld, e.getX()) + ox;
                py = Mth.lerp(pt, e.yOld, e.getY()) + oy;
                pz = Mth.lerp(pt, e.zOld, e.getZ()) + oz;
            }
        }

        /** 加法时乘白天补偿；半透明时乘 {@code translucentScale} */
        float alphaScale(boolean additive, float translucentScale) {
            return additive ? addScale : translucentScale;
        }
    }

    /** 适应护壁：命中点那一面的一片六边形格子 */
    private static final class Shield extends Attached {
        int cells;
        float edgeA;
        float fillA;
        int inMs;
        int holdEnd;
        int outEnd;
        boolean full;

        void draw(Draw d, float age, float pt, boolean additive) {
            float a;
            float scale;
            double drift = 0.0D;
            if (age < inMs) {
                float k = FxCurve.easeOutCubic(age / inMs);
                a = k;
                scale = 0.85F + 0.15F * k;
            } else if (age < holdEnd) {
                a = 1.0F;
                scale = 1.0F;
            } else {
                float k = FxCurve.clamp01((age - holdEnd) / (float) (outEnd - holdEnd));
                a = 1.0F - k * k * (3.0F - 2.0F * k);
                scale = 1.0F;
                drift = HEX_DRIFT * k;
            }
            a *= alphaScale(additive, 0.6F);
            if (a <= 0.003F) {
                return;
            }
            follow(pt);
            double lift = HEX_LIFT + drift;
            double cx = px + nx * lift, cy = py + ny * lift, cz = pz + nz * lift;
            float r = HEX_R * scale;
            float ro = (HEX_R - HEX_GAP) * scale;
            // 平顶六边形的边到中心的距离是外接圆半径 × cos30°，边线要宽 HEX_EDGE，内圈半径就再退 HEX_EDGE / cos30°
            float ri = Math.max(0.0F, ro - HEX_EDGE / 0.8660254F * scale);
            float edge = edgeA * a;
            float fill = fillA * a;
            int bone = FxPalette.BONE;
            int kuva = FxPalette.KUVA;
            for (int i = 0; i < cells; i++) {
                double ox = CELL_X[i] * r, oy = CELL_Y[i] * r;
                double x0 = cx + ux * ox + vx * oy, y0 = cy + uy * ox + vy * oy, z0 = cz + uz * ox + vz * oy;
                for (int k = 0; k < 6; k++) {
                    double c0 = HEX_COS[k], s0 = HEX_SIN[k], c1 = HEX_COS[k + 1], s1 = HEX_SIN[k + 1];
                    double ax = (ux * c0 + vx * s0), ay = (uy * c0 + vy * s0), az = (uz * c0 + vz * s0);
                    double bx = (ux * c1 + vx * s1), by = (uy * c1 + vy * s1), bz = (uz * c1 + vz * s1);
                    if (fill > 0.003F) {
                        d.v(x0, y0, z0, kuva, fill);
                        d.v(x0, y0, z0, kuva, fill);
                        d.v(x0 + bx * ri, y0 + by * ri, z0 + bz * ri, kuva, fill);
                        d.v(x0 + ax * ri, y0 + ay * ri, z0 + az * ri, kuva, fill);
                    }
                    d.v(x0 + ax * ri, y0 + ay * ri, z0 + az * ri, bone, edge);
                    d.v(x0 + bx * ri, y0 + by * ri, z0 + bz * ri, bone, edge);
                    d.v(x0 + bx * ro, y0 + by * ro, z0 + bz * ro, bone, edge);
                    d.v(x0 + ax * ro, y0 + ay * ro, z0 + az * ro, bone, edge);
                }
            }
        }
    }

    /** 偏转闪（平面里的横向 / 竖向光条）和弱点裂纹（朝相机的星形折线） */
    private static final class Mark extends Attached {
        static final int FLARE = 0;
        static final int CRACK = 1;
        private static final float[] ROWS = {-1.0F, -0.5F, 0.0F, 0.5F, 1.0F};
        private static final float[] ROW_ALPHA = {0.0F, 0.29F, 1.0F, 0.29F, 0.0F};
        private static final int COLS = 10;
        private static final float[] COL_ALPHA = new float[COLS + 1];

        static {
            for (int i = 0; i <= COLS; i++) {
                float x = -1.0F + 2.0F * i / COLS;
                COL_ALPHA[i] = (float) Math.pow(1.0F - Math.abs(x), 1.6F);
            }
        }

        /** 裂纹最多几道 */
        private static final int MAX_BRANCHES = 6;
        /** 裂纹从命中点往外裂开用多久（毫秒） */
        private static final float CRACK_GROW_MS = 70.0F;
        /** 裂纹的光晕：宽 × 3.2、α × 0.35 */
        private static final float HALO_WIDTH = 3.2F;
        private static final float HALO_ALPHA = 0.35F;

        int kind;
        int rgb;
        float alpha;
        int life;
        float width;
        /** 偏转闪：高 = 宽 × thick；竖向的那条长边沿平面里的「上」 */
        float thick;
        boolean upright;
        /** 裂纹：几道，每道两个折点（中间、末端）相对命中点的偏移 */
        int branches;
        final double[] pts = new double[MAX_BRANCHES * 6];

        static Mark flare(Vec3 n, float width, float thick, boolean upright, int rgb, float alpha, int lifeMs) {
            Mark m = new Mark();
            m.kind = FLARE;
            m.width = width;
            m.thick = thick;
            m.upright = upright;
            m.rgb = rgb;
            m.alpha = alpha;
            m.life = lifeMs;
            m.start = KuvaFxDirector.clockMs();
            m.setFrame(n);
            return m;
        }

        /**
         * 星形裂纹：{@code branches} 道从命中点往外裂，大致均分一圈、各自偏开一点；每道两段，长约 {@code radius}（±20%），
         * 中间折一下（15°~35°）。画在垂直于视线的平面里
         */
        static Mark crack(Vec3 toCam, int branches, float radius, float width, int rgb, float alpha, int lifeMs) {
            Mark m = new Mark();
            m.kind = CRACK;
            m.width = width;
            m.rgb = rgb;
            m.alpha = alpha;
            m.life = lifeMs;
            m.start = KuvaFxDirector.clockMs();
            double l = toCam.length();
            m.setFrame(l < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : toCam.scale(1.0D / l));
            m.branches = Mth.clamp(branches, 1, MAX_BRANCHES);
            double base = RANDOM.nextDouble() * Math.PI * 2.0D;
            double step = Math.PI * 2.0D / m.branches;
            for (int b = 0; b < m.branches; b++) {
                double ang = base + b * step + spread(step * 0.25D);
                double len = radius * (0.8D + RANDOM.nextDouble() * 0.4D);
                double first = len * (0.45D + RANDOM.nextDouble() * 0.15D);
                double x1 = Math.cos(ang) * first, y1 = Math.sin(ang) * first;
                double bend = Math.toRadians(15.0D + RANDOM.nextDouble() * 20.0D);
                ang += RANDOM.nextBoolean() ? bend : -bend;
                double x2 = x1 + Math.cos(ang) * (len - first), y2 = y1 + Math.sin(ang) * (len - first);
                int o = b * 6;
                m.pts[o] = m.ux * x1 + m.vx * y1;
                m.pts[o + 1] = m.uy * x1 + m.vy * y1;
                m.pts[o + 2] = m.uz * x1 + m.vz * y1;
                m.pts[o + 3] = m.ux * x2 + m.vx * y2;
                m.pts[o + 4] = m.uy * x2 + m.vy * y2;
                m.pts[o + 5] = m.uz * x2 + m.vz * y2;
            }
            return m;
        }

        void draw(Draw d, float age, float pt, boolean additive) {
            follow(pt);
            if (kind == FLARE) {
                drawFlare(d, age, additive);
            } else {
                drawCrack(d, age, additive);
            }
        }

        private void drawFlare(Draw d, float age, boolean additive) {
            // 12 ms 冲上去，撑到寿命的 45%，余下的时间平滑落回；一共不到 0.1 s，按平方淡出的话只剩中间一个亮点
            float hold = life * 0.45F;
            float a;
            if (age < 12.0F) {
                a = age / 12.0F;
            } else if (age < hold) {
                a = 1.0F;
            } else {
                float k = FxCurve.clamp01((age - hold) / Math.max(1.0F, life - hold));
                a = 1.0F - k * k * (3.0F - 2.0F * k);
            }
            a *= alpha * alphaScale(additive, 1.0F);
            if (a <= 0.003F) {
                return;
            }
            int mid = FxCurve.lerpColor(rgb, 0xFFFFFF, 0.6F);
            double hw = width * 0.5D, hh = width * thick;
            // 长边、短边方向：横向的沿平面里的「右」，竖向的沿「上」
            double lx = upright ? vx : ux, ly = upright ? vy : uy, lz = upright ? vz : uz;
            double sx = upright ? ux : vx, sy = upright ? uy : vy, sz = upright ? uz : vz;
            for (int r = 0; r < ROWS.length - 1; r++) {
                double y0 = ROWS[r] * hh, y1 = ROWS[r + 1] * hh;
                int c0 = ROWS[r] == 0.0F ? mid : rgb, c1 = ROWS[r + 1] == 0.0F ? mid : rgb;
                float ra0 = ROW_ALPHA[r] * a, ra1 = ROW_ALPHA[r + 1] * a;
                for (int i = 0; i < COLS; i++) {
                    double x0 = (-1.0D + 2.0D * i / COLS) * hw, x1 = (-1.0D + 2.0D * (i + 1) / COLS) * hw;
                    float ca0 = COL_ALPHA[i], ca1 = COL_ALPHA[i + 1];
                    d.v(px + lx * x0 + sx * y0, py + ly * x0 + sy * y0, pz + lz * x0 + sz * y0, c0, ra0 * ca0);
                    d.v(px + lx * x1 + sx * y0, py + ly * x1 + sy * y0, pz + lz * x1 + sz * y0, c0, ra0 * ca1);
                    d.v(px + lx * x1 + sx * y1, py + ly * x1 + sy * y1, pz + lz * x1 + sz * y1, c1, ra1 * ca1);
                    d.v(px + lx * x0 + sx * y1, py + ly * x0 + sy * y1, pz + lz * x0 + sz * y1, c1, ra1 * ca0);
                }
            }
        }

        private void drawCrack(Draw d, float age, boolean additive) {
            // 70 ms 内从命中点往外裂开；撑到寿命的 35%，余下的时间平滑淡掉
            float grow = FxCurve.easeOutCubic(FxCurve.clamp01(age / CRACK_GROW_MS));
            float hold = life * 0.35F;
            float a;
            if (age < hold) {
                a = 1.0F;
            } else {
                float k = FxCurve.clamp01((age - hold) / Math.max(1.0F, life - hold));
                a = 1.0F - k * k * (3.0F - 2.0F * k);
            }
            a *= alpha * alphaScale(additive, 1.0F);
            if (a <= 0.003F) {
                return;
            }
            int core = FxCurve.lerpColor(rgb, 0xFFFFFF, 0.55F);
            float g1 = Math.min(1.0F, grow * 2.0F), g2 = Math.max(0.0F, grow * 2.0F - 1.0F);
            for (int b = 0; b < branches; b++) {
                int o = b * 6;
                double mx = pts[o] * g1, my = pts[o + 1] * g1, mz = pts[o + 2] * g1;
                crackSegment(d, 0.0D, 0.0D, 0.0D, mx, my, mz, width * HALO_WIDTH, rgb, a * HALO_ALPHA);
                crackSegment(d, 0.0D, 0.0D, 0.0D, mx, my, mz, width, core, a);
                if (g2 > 0.0F) {
                    double tx = pts[o] + (pts[o + 3] - pts[o]) * g2, ty = pts[o + 1] + (pts[o + 4] - pts[o + 1]) * g2,
                            tz = pts[o + 2] + (pts[o + 5] - pts[o + 2]) * g2;
                    // 末段细一些，像裂到头
                    crackSegment(d, pts[o], pts[o + 1], pts[o + 2], tx, ty, tz, width * 0.6F * HALO_WIDTH, rgb,
                            a * HALO_ALPHA);
                    crackSegment(d, pts[o], pts[o + 1], pts[o + 2], tx, ty, tz, width * 0.6F, core, a);
                }
            }
        }

        /** 一段朝着相机的细带，端点是相对 {@code px/py/pz} 的偏移 */
        private void crackSegment(Draw d, double ox, double oy, double oz, double ex, double ey, double ez,
                                  float w, int color, float a) {
            double ax = px + ox, ay = py + oy, az = pz + oz;
            double bx = px + ex, by = py + ey, bz = pz + ez;
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double mx = (ax + bx) * 0.5D - d.cx, my = (ay + by) * 0.5D - d.cy, mz = (az + bz) * 0.5D - d.cz;
            // 侧向 = 线方向 × 视线，细带始终朝着相机
            double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-9D) {
                return;
            }
            double k = w * 0.5D / sl;
            sx *= k;
            sy *= k;
            sz *= k;
            d.v(ax - sx, ay - sy, az - sz, color, a);
            d.v(bx - sx, by - sy, bz - sz, color, a);
            d.v(bx + sx, by + sy, bz + sz, color, a);
            d.v(ax + sx, ay + sy, az + sz, color, a);
        }
    }

    private static void addMark(Mark m) {
        if (MARKS.size() >= MARKS_TOTAL) {
            MARKS.remove(0);
        }
        MARKS.add(m);
    }

    private static RenderType typeAdd() {
        if (typeAdd == null) {
            typeAdd = Types.createAdditive();
        }
        return typeAdd;
    }

    private static RenderType typeTranslucent() {
        if (typeTranslucent == null) {
            typeTranslucent = Types.createTranslucent();
        }
        return typeTranslucent;
    }

    /** 借 RenderType 的子类拿到受保护的渲染状态 */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        static RenderType createAdditive() {
            return RenderType.create("kuvalich_state_add", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    4096, false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(KuvaParticleShaders::geometry))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        static RenderType createTranslucent() {
            return RenderType.create("kuvalich_state_translucent", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }
    }

    // ==================== 小工具 ====================

    /** 倒地、处决中、离场、死了：这些时候什么都不画 */
    private static boolean quiet(AbstractKuva e) {
        return e.getMercyState() != MercyState.NONE || e.isDeadOrDying() || e.isRemoved();
    }

    private static boolean minimal() {
        return EffectsDisplayConfig.intensity() == EffectsDisplayConfig.Intensity.MINIMAL;
    }

    /** 粒子数：减少强度时减半（向上取整，至少 1） */
    private static int count(int full) {
        if (EffectsDisplayConfig.intensity() == EffectsDisplayConfig.Intensity.REDUCED) {
            return Math.max(1, (full + 1) / 2);
        }
        return full;
    }

    private static boolean near(Vec3 p, double r) {
        Camera cam = KuvaFxDirector.camera();
        return cam == null || cam.getPosition().distanceToSqr(p) <= r * r;
    }

    private static double spread(double r) {
        return (RANDOM.nextDouble() * 2.0D - 1.0D) * r;
    }

    /** 胸口：脚底 + 身高 × 0.62 */
    private static Vec3 chest(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.62D, e.getZ());
    }

    /**
     * 护壁、偏转闪朝哪一面：攻击者眼睛 − 目标胸口；没有攻击者时按命中点在胸口哪一侧，命中点就在胸口时朝相机
     */
    private static Vec3 facing(@Nullable Entity target, Vec3 at, @Nullable Entity attacker) {
        Vec3 c = target != null ? chest(target) : at;
        Vec3 n = null;
        if (attacker != null) {
            n = attacker.getEyePosition().subtract(c);
        } else if (target != null && at.distanceToSqr(c) > 0.01D) {
            n = at.subtract(c);
        } else {
            Camera cam = KuvaFxDirector.camera();
            if (cam != null) {
                n = cam.getPosition().subtract(c);
            }
        }
        if (n == null || n.lengthSqr() < 1.0E-6D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return n.normalize();
    }

    /** 把一个点夹到包围盒表面：在盒外就夹到最近的面上，在盒里就沿胸口到它的方向推到表面 */
    private static Vec3 clampToBox(AABB box, Vec3 p, Vec3 chest) {
        boolean inside = p.x > box.minX && p.x < box.maxX && p.y > box.minY && p.y < box.maxY
                && p.z > box.minZ && p.z < box.maxZ;
        if (!inside) {
            return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY),
                    Mth.clamp(p.z, box.minZ, box.maxZ));
        }
        double dx = p.x - chest.x, dy = p.y - chest.y, dz = p.z - chest.z;
        double t = Double.MAX_VALUE;
        t = Math.min(t, exit(chest.x, dx, box.minX, box.maxX));
        t = Math.min(t, exit(chest.y, dy, box.minY, box.maxY));
        t = Math.min(t, exit(chest.z, dz, box.minZ, box.maxZ));
        if (t == Double.MAX_VALUE) {
            return p;
        }
        return new Vec3(chest.x + dx * t, chest.y + dy * t, chest.z + dz * t);
    }

    private static double exit(double o, double d, double lo, double hi) {
        if (d > 1.0E-6D) {
            return (hi - o) / d;
        }
        if (d < -1.0E-6D) {
            return (lo - o) / d;
        }
        return Double.MAX_VALUE;
    }
}
