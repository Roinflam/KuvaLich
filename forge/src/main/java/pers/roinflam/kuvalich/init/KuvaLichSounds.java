package pers.roinflam.kuvalich.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 本模组的声音事件（共 110 个）
 *
 * <p>常量名 = 事件 id 转大写、点换下划线：{@code entity.lich.alert} → {@link #ENTITY_LICH_ALERT}。
 * 事件 id 同时也是 {@code sounds.json} 的键、{@code subtitles.kuvalich.*} 字幕键的来源。
 * 声音只在客户端播放，服务端只注册事件、不碰任何音频资源。</p>
 *
 * <p>按音高分文件的几组事件不做成一个事件靠变速播放：安魂印记（3 个音高）、模组升级（4 个音区），
 * 用 {@link #glyphLock}、{@link #rankup} 按序号取；13 种元素触发的声音用 {@link #elementProc} 按元素序号取。</p>
 */
public final class KuvaLichSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Reference.MOD_ID);

    private KuvaLichSounds() {
    }

    /** 注册一个声音事件：id 即资源路径，射程可变（衰减距离写在 sounds.json 里） */
    private static RegistryObject<SoundEvent> reg(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Reference.MOD_ID, id)));
    }

    // ==================== 赤毒 / 玄骸 / 奴仆 ====================
    public static final RegistryObject<SoundEvent> ENTITY_LICH_ALERT = reg("entity.lich.alert");
    public static final RegistryObject<SoundEvent> ENTITY_THRALL_ALERT = reg("entity.thrall.alert");
    /** 幼体借奴仆的几种声音，单独几个事件只为字幕能写「幼体」 */
    public static final RegistryObject<SoundEvent> ENTITY_LARVLING_ALERT = reg("entity.larvling.alert");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_SUMMON = reg("entity.lich.summon");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_AMBIENT = reg("entity.lich.ambient");
    public static final RegistryObject<SoundEvent> ENTITY_THRALL_AMBIENT = reg("entity.thrall.ambient");
    public static final RegistryObject<SoundEvent> ENTITY_LARVLING_AMBIENT = reg("entity.larvling.ambient");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_HURT = reg("entity.lich.hurt");
    public static final RegistryObject<SoundEvent> ENTITY_THRALL_HURT = reg("entity.thrall.hurt");
    public static final RegistryObject<SoundEvent> ENTITY_LARVLING_HURT = reg("entity.larvling.hurt");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_DEATH = reg("entity.lich.death");
    public static final RegistryObject<SoundEvent> ENTITY_THRALL_DEATH = reg("entity.thrall.death");
    public static final RegistryObject<SoundEvent> ENTITY_LARVLING_DEATH = reg("entity.larvling.death");
    public static final RegistryObject<SoundEvent> ENTITY_KUVA_SWING = reg("entity.kuva.swing");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_STRIKE = reg("entity.lich.strike");
    public static final RegistryObject<SoundEvent> ENTITY_THRALL_STRIKE = reg("entity.thrall.strike");
    public static final RegistryObject<SoundEvent> ENTITY_LARVLING_STRIKE = reg("entity.larvling.strike");
    public static final RegistryObject<SoundEvent> ENTITY_KUVA_STEP = reg("entity.kuva.step");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_RESIST = reg("entity.lich.resist");
    public static final RegistryObject<SoundEvent> ENTITY_LICH_CONFISCATE = reg("entity.lich.confiscate");

    // ==================== 安魂与解密 ====================
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_LOCK_1 = reg("requiem.glyph.lock.1");
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_LOCK_2 = reg("requiem.glyph.lock.2");
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_LOCK_3 = reg("requiem.glyph.lock.3");
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_REJECT = reg("requiem.glyph.reject");
    public static final RegistryObject<SoundEvent> REQUIEM_SHATTER = reg("requiem.shatter");
    public static final RegistryObject<SoundEvent> REQUIEM_TAUNT = reg("requiem.taunt");
    public static final RegistryObject<SoundEvent> REQUIEM_RETURN = reg("requiem.return");
    public static final RegistryObject<SoundEvent> REQUIEM_CARD_WEAR = reg("requiem.card.wear");
    public static final RegistryObject<SoundEvent> UI_DECRYPT_PROGRESS = reg("ui.decrypt.progress");
    public static final RegistryObject<SoundEvent> UI_DECRYPT_REVEAL1 = reg("ui.decrypt.reveal1");
    public static final RegistryObject<SoundEvent> UI_DECRYPT_REVEAL2 = reg("ui.decrypt.reveal2");
    public static final RegistryObject<SoundEvent> UI_DECRYPT_REVEAL3 = reg("ui.decrypt.reveal3");
    public static final RegistryObject<SoundEvent> UI_DECRYPT_COMPLETE = reg("ui.decrypt.complete");
    public static final RegistryObject<SoundEvent> MERCY_DOWNED = reg("mercy.downed");
    /** 奴仆倒地：和玄骸同一个声音，单独一个事件只为字幕能写「奴仆」 */
    public static final RegistryObject<SoundEvent> MERCY_DOWNED_THRALL = reg("mercy.downed.thrall");
    /** 幼体倒地：同上，字幕写「幼体」 */
    public static final RegistryObject<SoundEvent> MERCY_DOWNED_LARVLING = reg("mercy.downed.larvling");
    public static final RegistryObject<SoundEvent> MERCY_DOWNED_LOOP = reg("mercy.downed.loop");
    public static final RegistryObject<SoundEvent> MERCY_DRAW = reg("mercy.draw");
    public static final RegistryObject<SoundEvent> MERCY_STAB = reg("mercy.stab");
    public static final RegistryObject<SoundEvent> MERCY_EXPIRE = reg("mercy.expire");

    // ==================== 战斗 ====================
    public static final RegistryObject<SoundEvent> COMBAT_CRIT_YELLOW = reg("combat.crit.yellow");
    public static final RegistryObject<SoundEvent> COMBAT_CRIT_ORANGE = reg("combat.crit.orange");
    public static final RegistryObject<SoundEvent> COMBAT_CRIT_RED = reg("combat.crit.red");
    public static final RegistryObject<SoundEvent> COMBAT_CRIT_RED_LITE = reg("combat.crit.red.lite");
    public static final RegistryObject<SoundEvent> COMBAT_EXECUTE = reg("combat.execute");
    public static final RegistryObject<SoundEvent> ELEMENT_FIRE_PROC = reg("element.fire.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_ICE_PROC = reg("element.ice.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_POISON_PROC = reg("element.poison.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_ELECTRICITY_PROC = reg("element.electricity.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_SLASH_PROC = reg("element.slash.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_PUNCTURE_PROC = reg("element.puncture.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_IMPACT_PROC = reg("element.impact.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_GAS_PROC = reg("element.gas.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_RADIATION_PROC = reg("element.radiation.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_MAGNETIC_PROC = reg("element.magnetic.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_CORROSION_PROC = reg("element.corrosion.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_EXPLOSION_PROC = reg("element.explosion.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_VIRUS_PROC = reg("element.virus.proc");
    public static final RegistryObject<SoundEvent> ELEMENT_EXPLOSION_TAIL = reg("element.explosion.tail");
    public static final RegistryObject<SoundEvent> WEAPON_CHARGE_READY = reg("weapon.charge.ready");
    public static final RegistryObject<SoundEvent> WEAPON_DASH = reg("weapon.dash");
    public static final RegistryObject<SoundEvent> WEAPON_DASH_HIT = reg("weapon.dash.hit");

    // ==================== 战甲与玩家 ====================
    public static final RegistryObject<SoundEvent> WARFRAME_SHIELD_BREAK = reg("warframe.shield.break");
    public static final RegistryObject<SoundEvent> WARFRAME_SHIELD_RECHARGE = reg("warframe.shield.recharge");
    public static final RegistryObject<SoundEvent> WARFRAME_LAST_STAND = reg("warframe.last_stand");
    public static final RegistryObject<SoundEvent> WARFRAME_STACK_MAX = reg("warframe.stack.max");

    // ==================== 工作台、物品、方块 ====================
    public static final RegistryObject<SoundEvent> FORGE_MODULE_INSTALL = reg("forge.module.install");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_REMOVE = reg("forge.module.remove");
    public static final RegistryObject<SoundEvent> FORGE_WEAPON_MOUNT = reg("forge.weapon.mount");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_RANKUP_1 = reg("forge.module.rankup.1");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_RANKUP_2 = reg("forge.module.rankup.2");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_RANKUP_3 = reg("forge.module.rankup.3");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_RANKUP_4 = reg("forge.module.rankup.4");
    public static final RegistryObject<SoundEvent> FORGE_MODULE_MAXRANK = reg("forge.module.maxrank");
    public static final RegistryObject<SoundEvent> FORGE_FORMA = reg("forge.forma");
    public static final RegistryObject<SoundEvent> FORGE_FORMA_SHADOW = reg("forge.forma.shadow");
    public static final RegistryObject<SoundEvent> FORGE_FORMA_LOCK = reg("forge.forma.lock");
    public static final RegistryObject<SoundEvent> FORGE_FUSION = reg("forge.fusion");
    public static final RegistryObject<SoundEvent> FORGE_RIVEN_CYCLE = reg("forge.riven.cycle");
    public static final RegistryObject<SoundEvent> FORGE_IMBUE = reg("forge.imbue");
    public static final RegistryObject<SoundEvent> FORGE_RECAST_MERGE = reg("forge.recast.merge");
    public static final RegistryObject<SoundEvent> FORGE_RECAST_DECOMPOSE = reg("forge.recast.decompose");
    public static final RegistryObject<SoundEvent> ITEM_MODULE_REVEAL = reg("item.module.reveal");
    public static final RegistryObject<SoundEvent> ITEM_MODULE_REVEAL_EPIC = reg("item.module.reveal.epic");
    public static final RegistryObject<SoundEvent> BLOCK_REQUIEM_GATE_OPEN = reg("block.requiem_gate.open");
    public static final RegistryObject<SoundEvent> BLOCK_FORGE_OPEN = reg("block.forge.open");
    public static final RegistryObject<SoundEvent> BLOCK_KUVA_MACHINE_PLACE = reg("block.kuva_machine.place");
    public static final RegistryObject<SoundEvent> BLOCK_KUVA_MACHINE_BREAK = reg("block.kuva_machine.break");
    public static final RegistryObject<SoundEvent> BLOCK_KUVA_MACHINE_STEP = reg("block.kuva_machine.step");

    // ==================== 指南与通讯 ====================
    public static final RegistryObject<SoundEvent> UI_GUIDE_OPEN_POWER = reg("ui.guide.open.power");
    public static final RegistryObject<SoundEvent> UI_GUIDE_OPEN_RESOLVE = reg("ui.guide.open.resolve");
    public static final RegistryObject<SoundEvent> UI_GUIDE_OPEN_QUICK = reg("ui.guide.open.quick");
    public static final RegistryObject<SoundEvent> UI_GUIDE_SKIP = reg("ui.guide.skip");
    public static final RegistryObject<SoundEvent> UI_GUIDE_CLOSE_OFF = reg("ui.guide.close.off");
    public static final RegistryObject<SoundEvent> UI_GUIDE_PAGE = reg("ui.guide.page");
    public static final RegistryObject<SoundEvent> UI_GUIDE_HOME = reg("ui.guide.home");
    public static final RegistryObject<SoundEvent> UI_GUIDE_HOVER = reg("ui.guide.hover");
    public static final RegistryObject<SoundEvent> UI_GUIDE_CLICK = reg("ui.guide.click");
    public static final RegistryObject<SoundEvent> UI_GUIDE_LOOKUP = reg("ui.guide.lookup");
    public static final RegistryObject<SoundEvent> UI_COMM_LOTUS = reg("ui.comm.lotus");
    public static final RegistryObject<SoundEvent> UI_COMM_ORDIS = reg("ui.comm.ordis");
    public static final RegistryObject<SoundEvent> UI_COMM_LICH = reg("ui.comm.lich");

    // ==================== 怜悯之击、通讯与界面底噪（第二期新增） ====================
    /** 处决第一刀 */
    public static final RegistryObject<SoundEvent> MERCY_STAB_1 = reg("mercy.stab.1");
    /** 处决第二刀 */
    public static final RegistryObject<SoundEvent> MERCY_STAB_2 = reg("mercy.stab.2");
    /** 处决第三刀，以及判错的那一刀 */
    public static final RegistryObject<SoundEvent> MERCY_STAB_FINAL = reg("mercy.stab.final");
    /** 奴仆被处决时的每一记刺 */
    public static final RegistryObject<SoundEvent> MERCY_THRALL = reg("mercy.thrall");
    /** 幼体被处决时的每一记刺：声音同奴仆，字幕写「幼体」 */
    public static final RegistryObject<SoundEvent> MERCY_LARVLING = reg("mercy.larvling");
    /** 玄骸挣脱后遁入赤毒雾 */
    public static final RegistryObject<SoundEvent> MERCY_ESCAPE = reg("mercy.escape");
    /** 怜悯之击成功的裁决（只给处决者） */
    public static final RegistryObject<SoundEvent> MERCY_VERDICT = reg("mercy.verdict");
    /** 玄骸升级横幅 */
    public static final RegistryObject<SoundEvent> UI_BANNER_RANKUP = reg("ui.banner.rankup");
    /** 玄骸通讯的嘟囔，一个音节放一次，12 个文件 */
    public static final RegistryObject<SoundEvent> UI_COMM_LICH_MUTTER = reg("ui.comm.lich.mutter");
    /** 灭骸之扉界面的循环底噪 */
    public static final RegistryObject<SoundEvent> BLOCK_REQUIEM_GATE_AMBIENCE = reg("block.requiem_gate.ambience");
    /** 电击麻痹期间的循环 */
    public static final RegistryObject<SoundEvent> ELEMENT_ELECTRICITY_LOOP = reg("element.electricity.loop");
    /** 玄骸字幕在屏期间垫的静电 */
    public static final RegistryObject<SoundEvent> UI_COMM_STATIC = reg("ui.comm.static");
    /** 怜悯之击每一刀的格子锁扣 */
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_LOCK_MERCY = reg("requiem.glyph.lock.mercy");
    /** 怜悯之击成功收尾，玄骸消散 */
    public static final RegistryObject<SoundEvent> REQUIEM_DISSOLVE = reg("requiem.dissolve");
    /** 怜悯之击判错的格子变红 */
    public static final RegistryObject<SoundEvent> REQUIEM_GLYPH_REJECT_MERCY = reg("requiem.glyph.reject.mercy");
    /** 某一类伤害第一次把玄骸的适应叠满 */
    public static final RegistryObject<SoundEvent> ENTITY_LICH_ADAPT_FULL = reg("entity.lich.adapt.full");

    // ==================== 按序号取的几组 ====================

    private static final List<RegistryObject<SoundEvent>> ELEMENT_PROC = List.of(
            ELEMENT_FIRE_PROC,
            ELEMENT_ICE_PROC,
            ELEMENT_POISON_PROC,
            ELEMENT_ELECTRICITY_PROC,
            ELEMENT_SLASH_PROC,
            ELEMENT_PUNCTURE_PROC,
            ELEMENT_IMPACT_PROC,
            ELEMENT_GAS_PROC,
            ELEMENT_RADIATION_PROC,
            ELEMENT_MAGNETIC_PROC,
            ELEMENT_CORROSION_PROC,
            ELEMENT_EXPLOSION_PROC,
            ELEMENT_VIRUS_PROC);

    private static final List<RegistryObject<SoundEvent>> GLYPH_LOCK = List.of(
            REQUIEM_GLYPH_LOCK_1, REQUIEM_GLYPH_LOCK_2, REQUIEM_GLYPH_LOCK_3);

    private static final List<RegistryObject<SoundEvent>> RANKUP = List.of(
            FORGE_MODULE_RANKUP_1, FORGE_MODULE_RANKUP_2, FORGE_MODULE_RANKUP_3, FORGE_MODULE_RANKUP_4);

    /**
     * 元素触发的声音，元素序号和 {@code kuvaweapon.type.*} 的顺序一致：
     * 0 fire / 1 ice / 2 poison / 3 electricity / 4 slash / 5 puncture / 6 impact / 7 gas /
     * 8 radiation / 9 magnetic / 10 corrosion / 11 explosion / 12 virus。越界按最近的一端取。
     */
    public static SoundEvent elementProc(int idx) {
        return ELEMENT_PROC.get(Math.max(0, Math.min(ELEMENT_PROC.size() - 1, idx))).get();
    }

    /** 安魂印记点亮：序号 0~2 对应第 1~3 个印记（D5 / F5 / A5 三个音高），越界按最近的一端取 */
    public static SoundEvent glyphLock(int n) {
        return GLYPH_LOCK.get(Math.max(0, Math.min(GLYPH_LOCK.size() - 1, n))).get();
    }

    /** 模组升级：音区 0~3 对应四个文件（由低到高），越界按最近的一端取 */
    public static SoundEvent rankup(int group) {
        return RANKUP.get(Math.max(0, Math.min(RANKUP.size() - 1, group))).get();
    }

    /**
     * 五个机器方块（通牒门和四个工作台）的方块声音：放置、破坏、脚步（挖的过程也用脚步），摔落沿用石头。
     * 必须放在所有事件常量之后声明，里面的供应器才取得到值。
     */
    public static final ForgeSoundType MACHINE = new ForgeSoundType(1.0F, 1.0F,
            BLOCK_KUVA_MACHINE_BREAK, BLOCK_KUVA_MACHINE_STEP, BLOCK_KUVA_MACHINE_PLACE, BLOCK_KUVA_MACHINE_STEP,
            () -> SoundEvents.STONE_FALL);
}
