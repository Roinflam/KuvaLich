package pers.roinflam.kuvalich.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxTier;

/**
 * 本模组客户端放的每个声音都用这个实例
 *
 * <p>和原版 {@code SimpleSoundInstance} 的区别是每个 tick 重新算音量：{@code 基础音量 × 混音增益 × 淡出系数}。
 * 声音引擎对 tickable 的声音每个 tick 都会重新读音量、音高和位置（原版机制），所以混音让位、淡出、跟随实体都只要改字段。
 * 时间按 tick 数算（每 tick 50 ms），游戏暂停时声音引擎不跑 tick，计时也就跟着停。</p>
 *
 * <p>衰减距离由 {@code sounds.json} 的 {@code attenuation_distance} 决定，听得见的范围是
 * {@code attenuation_distance × max(开始播放时的音量, 1)}，所以大事件把距离写在 sounds.json 里、代码音量保持 ≤ 1。</p>
 */
@OnlyIn(Dist.CLIENT)
public class KuvaSoundInstance extends AbstractTickableSoundInstance {

    /** 一个 tick 的毫秒数 */
    private static final int TICK_MS = 50;

    private float baseVolume;
    private float basePitch;
    private FxTier tier = FxTier.B;
    private Entity follow;
    private boolean followHead;

    /** 已经过的 tick 数 */
    private int age;
    /** 到这个 tick 数就停；小于 0 = 不限 */
    private int stopAtAge = -1;
    /** 淡出开始的 tick 数；小于 0 = 没在淡出 */
    private int fadeStartAge = -1;
    private int fadeTicks;
    /** 淡入用多少个 tick；0 = 不淡入 */
    private int fadeInTicks;
    /** 指定嘟囔文件的下标（从 1 起）；0 = 照 sounds.json 抽 */
    private int variantIndex;

    /**
     * @param volume 基础音量
     * @param pitch  音高
     */
    protected KuvaSoundInstance(ResourceLocation location, SoundSource source, float volume, float pitch, RandomSource random) {
        super(SoundEvent.createVariableRangeEvent(location), source, random);
        this.baseVolume = volume;
        this.basePitch = pitch;
        this.volume = volume;
        this.pitch = pitch;
    }

    /** 世界声 / 界面声的统一创建入口，位置、衰减方式、是否相对听者由 {@link KuvaSoundPlayer} 的几个方法填 */
    static KuvaSoundInstance create(SoundEvent event, SoundSource source, float volume, float pitch) {
        return new KuvaSoundInstance(event.getLocation(), source, volume, pitch, SoundInstance.createUnseededRandom());
    }

    /**
     * 复制一个别处创建的声音实例（位置、类别、音量、音高、衰减、是否相对听者、循环、延迟都照抄），
     * 用来把服务端放的本模组声音换成可以被混音器调音量的版本。原实例如果是自己会 tick 的（跟着实体动之类）会丢掉那部分行为
     */
    public static KuvaSoundInstance copyOf(SoundInstance orig) {
        KuvaSoundInstance c = new KuvaSoundInstance(orig.getLocation(), orig.getSource(),
                orig.getVolume(), orig.getPitch(), SoundInstance.createUnseededRandom());
        c.x = orig.getX();
        c.y = orig.getY();
        c.z = orig.getZ();
        c.attenuation = orig.getAttenuation();
        c.relative = orig.isRelative();
        c.looping = orig.isLooping();
        c.delay = orig.getDelay();
        return c;
    }

    // ==================== 链式设置 ====================

    /** 档位（混音器据此决定谁让谁），默认 B */
    public KuvaSoundInstance tier(FxTier t) {
        this.tier = t;
        return this;
    }

    public FxTier tier() {
        return tier;
    }

    /** 定位（世界声） */
    public KuvaSoundInstance position(Vec3 p) {
        this.x = p.x;
        this.y = p.y;
        this.z = p.z;
        return this;
    }

    /** 声音位置每 tick 跟着实体走（取身体中心）；实体没了就停在最后的位置 */
    public KuvaSoundInstance follow(Entity e) {
        this.follow = e;
        this.followHead = false;
        return this;
    }

    /** 循环播放（要靠 {@link #stopAfter} 或 {@link #end} 结束） */
    public KuvaSoundInstance loop(boolean loop) {
        this.looping = loop;
        return this;
    }

    /** 衰减方式：不衰减（W∞ 和界面声用） */
    KuvaSoundInstance noAttenuation() {
        this.attenuation = SoundInstance.Attenuation.NONE;
        return this;
    }

    /** 不定位，相对听者（界面声用） */
    KuvaSoundInstance relativeToListener() {
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        return this;
    }

    /** 改基础音量（比如倒地低鸣随剩余时间线性降） */
    public void setBaseVolume(float v) {
        this.baseVolume = v;
    }

    public float baseVolume() {
        return baseVolume;
    }

    public void setBasePitch(float p) {
        this.basePitch = p;
    }

    // ==================== 结束方式 ====================

    /** 从现在起 {@code ms} 毫秒后掐掉（按 tick 取整，至少 1 tick） */
    public KuvaSoundInstance stopAfter(int ms) {
        this.stopAtAge = age + Math.max(1, Math.round(ms / (float) TICK_MS));
        return this;
    }

    /** 从现在起 {@code ms} 毫秒内线性淡到 0 再掐掉（指南开场被跳过时用来掐掉已烘进文件的后半段） */
    public KuvaSoundInstance fadeOut(int ms) {
        this.fadeStartAge = age;
        this.fadeTicks = Math.max(1, Math.round(ms / (float) TICK_MS));
        return this;
    }

    /**
     * 开头 {@code ms} 毫秒内音量从 0 线性涨到目标（按 tick 取整，至少 1 tick）。
     * 循环底噪进出界面时用，免得一开就是满音量
     */
    public KuvaSoundInstance fadeIn(int ms) {
        this.fadeInTicks = Math.max(1, Math.round(ms / (float) TICK_MS));
        this.volume = 0.0F;
        return this;
    }

    /**
     * 指定播 {@code ui/comm/lich/mutter/} 下的第 {@code index} 个文件（1~12）。
     * 声音管理器里同一个事件的几个文件挂在一张私有的权重表里，取不到某一个，
     * 所以这里在解析完事件之后直接换成指定文件。{@code index} 不大于 0 时照常按 sounds.json 抽
     */
    public KuvaSoundInstance variant(int index) {
        this.variantIndex = index;
        return this;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        WeighedSoundEvents events = super.resolve(manager);
        if (events != null && variantIndex > 0) {
            this.sound = new Sound("kuvalich:ui/comm/lich/mutter/" + variantIndex,
                    ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, true, 16);
        }
        return events;
    }

    /**
     * 带淡入的声音开播那一刻音量是 0，原版遇到音量为 0 的声音会直接跳过不放（之后也不会再起），
     * 所以淡入的声音要声明「可以静音起播」，之后每 tick 再把音量涨上去
     */
    @Override
    public boolean canStartSilent() {
        return fadeInTicks > 0;
    }

    /** 立刻结束 */
    public void end() {
        stop();
    }

    // ==================== 每 tick ====================

    @Override
    public void tick() {
        age++;
        if (stopAtAge >= 0 && age >= stopAtAge) {
            stop();
            return;
        }
        if (follow != null && !follow.isRemoved()) {
            this.x = follow.getX();
            this.y = follow.getY() + follow.getBbHeight() * 0.5D;
            this.z = follow.getZ();
        }
        float fade = 1.0F;
        if (fadeStartAge >= 0) {
            float k = (age - fadeStartAge) / (float) fadeTicks;
            if (k >= 1.0F) {
                stop();
                return;
            }
            fade = 1.0F - k;
        }
        float in = fadeInTicks > 0 ? Math.min(1.0F, age / (float) fadeInTicks) : 1.0F;
        this.volume = baseVolume * KuvaMixer.gain(this) * fade * in;
        this.pitch = basePitch;
    }
}
