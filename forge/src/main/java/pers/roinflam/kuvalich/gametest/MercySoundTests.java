package pers.roinflam.kuvalich.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercySoundCues;
import pers.roinflam.kuvalich.mercy.MercySoundCues.Cue;
import pers.roinflam.kuvalich.mercy.MercySoundCues.Layer;
import pers.roinflam.kuvalich.mercy.MercySoundCues.Result;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 处决声音叠层：时刻跟着节拍表走、事件都注册了、sounds.json 引用的文件都在、字幕键四种语言都有
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercySoundTests {

    private static final String[] LANGS = {"zh_cn", "en_us", "zh_tw", "zh_hk"};

    private MercySoundTests() {
    }

    /**
     * 玄骸每种结果、每一刀：挥刀、刀声、刺入、低频四层的「那一下」都落在这一刀的刺入时刻上，
     * 出结果那一刀有尾音（判错换成受阻那一声），挣开站起没有；起播不早于切镜、不晚于交还；动作声只在做了那个动作的结果里有
     */
    @GameTest(template = "empty")
    public static void lichCuesFollowBeats(GameTestHelper h) {
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                continue;
            }
            MercyBeats.Lich b = MercyBeats.lich(v);
            check(h, v, b, Result.SUCCESS, 3, b.successEnd);
            check(h, v, b, Result.CONVERT, 3, b.convertEnd);
            for (int k = 1; k <= 3; k++) {
                check(h, v, b, Result.FAIL, k, b.fill(k) + b.failEnd);
            }
            for (int s = 1; s <= 2; s++) {
                check(h, v, b, Result.ADVANCE, s, b.fill(s) + b.advanceEnd);
            }
        }
        h.succeed();
    }

    private static void check(GameTestHelper h, MercyVariant v, MercyBeats.Lich b, Result r, int stabs, int end) {
        String c = v + " " + r + " 刀数 " + stabs;
        List<Cue> cues = MercySoundCues.cues(v, r, stabs);
        for (int i = 0; i < stabs; i++) {
            int s = b.stab(i);
            boolean heavy = i == stabs - 1 && r != Result.ADVANCE;
            for (Layer l : new Layer[]{Layer.SWING, Layer.PIERCE, Layer.THUD,
                    heavy ? Layer.STAB_FINAL : (i == 0 ? Layer.STAB_1 : Layer.STAB_2)}) {
                h.assertTrue(has(cues, l, i, s), c + "：第 " + (i + 1) + " 刀缺 " + l + " 或没落在 " + s + " ms");
            }
            boolean tail = has(cues, Layer.TAIL, i, s), wrong = has(cues, Layer.TAIL_WRONG, i, s);
            if (heavy) {
                h.assertTrue(r == Result.FAIL ? wrong && !tail : tail && !wrong, c + "：出结果那一刀的尾音不对");
            } else {
                h.assertTrue(!tail && !wrong, c + "：第 " + (i + 1) + " 刀不该有尾音");
            }
        }
        h.assertTrue(cues.stream().filter(q -> q.layer() == Layer.BREAKFREE).count()
                == (r == Result.FAIL || r == Result.ADVANCE ? 1 : 0), c + "：刀被挣出来那一声的个数不对");
        int prev = Integer.MIN_VALUE;
        for (Cue q : cues) {
            h.assertTrue(q.startMs() >= b.cutIn && q.ms() <= end,
                    c + "：" + q.layer() + " 在 " + q.startMs() + "～" + q.ms() + " ms，超出切镜 " + b.cutIn + " 到收尾 " + end);
            h.assertTrue(q.stab() < stabs, c + "：" + q.layer() + " 挂在没捅的第 " + (q.stab() + 1) + " 刀上");
            h.assertTrue(q.startMs() >= prev, c + "：时刻表没排好序");
            prev = q.startMs();
        }
        if (r != Result.SUCCESS && r != Result.CONVERT) {
            // 后空翻、落地只在成功收尾时有
            int s3 = b.stab(2);
            h.assertTrue(cues.stream().noneMatch(q -> q.stab() < 0 && q.layer() != Layer.BREAKFREE && q.ms() > s3),
                    c + "：第三刀之后不该有动作声");
        }
        if (r == Result.CONVERT) {
            // 感化从跪地特写接着演，成功收尾那套动作声不该混进来
            int s3 = b.stab(2);
            h.assertTrue(cues.stream().noneMatch(q -> q.stab() < 0 && q.ms() > s3 && q.ms() < b.closeup),
                    c + "：第三刀到跪地特写之间不该有动作声");
        }
    }

    /**
     * 感化收尾的节拍：拔刀、后跃、落地、刀弹回、转身、几步、回头、拳按胸口、化雾、离场、收尾依次排开；
     * 服务端离场后移除那一拍正好是化雾演完；各个动作声都落在 MercyBeats 的时刻上
     */
    @GameTest(template = "empty")
    public static void convertOutroFollowsBeats(GameTestHelper h) {
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                continue;
            }
            MercyBeats.Lich b = MercyBeats.lich(v);
            String c = v + " 感化";
            int[] order = {b.closeup, b.convertYank, b.convertHop, b.convertCut, b.convertLand, b.convertSnap,
                    b.convertPivot};
            for (int i = 1; i < order.length; i++) {
                h.assertTrue(order[i - 1] < order[i], c + "：第 " + i + " 拍没排在前一拍之后");
            }
            int prev = b.convertPivot;
            for (int i = 0; i < b.convertStepCount(); i++) {
                h.assertTrue(b.convertStep(i) > prev, c + "：第 " + (i + 1) + " 步没排好");
                prev = b.convertStep(i);
            }
            h.assertTrue(prev < b.convertLook && b.convertFist < b.convertBow && b.convertBow < b.convertMist
                    && b.convertLook < b.convertMist && b.convertMist < b.convertLeave && b.convertLeave < b.convertEnd,
                    c + "：回头 / 拳按胸口 / 垂首 / 化雾 / 离场 / 收尾的先后不对");
            int removed = (b.convertLeaveTick + KuvaMasterEntity.DEPART_TICKS) * 50;
            int faded = b.convertMist + MercyBeats.CONVERT_MIST_MS;
            h.assertTrue(Math.abs(removed - faded) <= 100, c + "：离场移除 " + removed + " ms 和化雾演完 " + faded + " ms 对不上");
            List<Cue> cues = MercySoundCues.cues(v, Result.CONVERT, 3);
            int[] at = {b.convertYank, b.convertHop, b.convertLand, b.convertSnap, b.convertFist, b.convertMist};
            for (int ms : at) {
                h.assertTrue(cues.stream().anyMatch(q -> q.stab() < 0 && q.ms() == ms), c + "：" + ms + " ms 缺动作声");
            }
            for (int i = 0; i < b.convertStepCount(); i++) {
                int ms = b.convertStep(i);
                h.assertTrue(cues.stream().anyMatch(q -> q.layer() == Layer.LAND && q.ms() == ms), c + "：第 " + (i + 1) + " 步缺脚步声");
            }
            h.assertTrue(MercySoundCues.cues(v, Result.SUCCESS, 3).stream().noneMatch(q -> q.ms() == b.convertMist
                    && q.layer() == Layer.TAIL && q.stab() < 0), v + "：成功收尾不该带感化的化雾声");
        }
        h.succeed();
    }

    private static boolean has(List<Cue> cues, Layer l, int stab, int ms) {
        return cues.stream().anyMatch(q -> q.layer() == l && q.stab() == stab && q.ms() == ms
                && q.startMs() + Math.round(MercySoundCues.lead(l) / q.pitch()) == ms);
    }

    /** 奴仆一刀：同样四层落在刺入时刻上，比玄骸轻（音量低、音高高），没有尾音；动作声都在交还之前 */
    @GameTest(template = "empty")
    public static void thrallCuesLighter(GameTestHelper h) {
        List<Cue> lich = MercySoundCues.cues(MercyVariant.LICH_A, Result.SUCCESS, 3);
        for (MercyVariant v : MercyVariant.values()) {
            if (!v.thrall) {
                continue;
            }
            MercyBeats.Thrall b = MercyBeats.thrall(v);
            List<Cue> cues = MercySoundCues.cues(v, Result.THRALL, 1);
            for (Layer l : new Layer[]{Layer.SWING, Layer.PIERCE, Layer.THUD, Layer.THRALL}) {
                h.assertTrue(has(cues, l, 0, b.stab), v + "：缺 " + l + " 或没落在刺入 " + b.stab + " ms");
            }
            for (Layer l : new Layer[]{Layer.SWING, Layer.PIERCE, Layer.THUD}) {
                Cue t = cues.stream().filter(q -> q.layer() == l).findFirst().orElseThrow();
                Cue m = lich.stream().filter(q -> q.layer() == l && q.stab() == 0).findFirst().orElseThrow();
                h.assertTrue(t.vol() < m.vol() && t.pitch() > m.pitch(), v + "：" + l + " 应当比玄骸第一刀轻、短");
            }
            h.assertTrue(cues.stream().noneMatch(q -> q.layer() == Layer.TAIL || q.layer() == Layer.TAIL_WRONG),
                    v + "：奴仆不该有尾音");
            for (Cue q : cues) {
                h.assertTrue(q.startMs() >= 0 && q.ms() <= b.end, v + "：" + q.layer() + " 在 " + q.ms() + " ms，超出 0～" + b.end);
            }
            if (b.land >= 0) {
                // 整身轨道走完（服务端换站位）不早于脚着地
                h.assertTrue(cues.stream().anyMatch(q -> q.layer() == Layer.LAND && q.ms() > b.stab && q.ms() <= b.land),
                        v + "：落地声应当在刺入之后、整身轨道走完 " + b.land + " ms 之前");
            }
        }
        h.succeed();
    }

    /** 每一层的事件都注册了、id 带 mercy. 前缀（全局压混按前缀豁免），sounds.json 里引用的文件都在，字幕键四种语言都有 */
    @GameTest(template = "empty")
    public static void layerSoundsPresent(GameTestHelper h) {
        JsonObject sounds = json(h, "/assets/" + Reference.MOD_ID + "/sounds.json");
        JsonObject[] langs = new JsonObject[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) {
            langs[i] = json(h, "/assets/" + Reference.MOD_ID + "/lang/" + LANGS[i] + ".json");
        }
        for (Layer l : Layer.values()) {
            h.assertTrue(l.id.startsWith("mercy."), l + " 的事件 id 没带 mercy. 前缀");
            h.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation(Reference.MOD_ID, l.id)),
                    l.id + " 没注册");
            JsonObject e = sounds.getAsJsonObject(l.id);
            h.assertTrue(e != null && e.has("sounds") && e.getAsJsonArray("sounds").size() > 0, "sounds.json 缺 " + l.id);
            for (JsonElement s : e.getAsJsonArray("sounds")) {
                String name = s.isJsonObject() ? s.getAsJsonObject().get("name").getAsString() : s.getAsString();
                ResourceLocation rl = new ResourceLocation(name);
                String path = "/assets/" + rl.getNamespace() + "/sounds/" + rl.getPath() + ".ogg";
                try (InputStream in = MercySoundTests.class.getResourceAsStream(path)) {
                    h.assertTrue(in != null && in.read() >= 0, l.id + " 引用的 " + path + " 不存在");
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            String sub = e.get("subtitle").getAsString();
            for (int i = 0; i < LANGS.length; i++) {
                h.assertTrue(langs[i].has(sub), LANGS[i] + " 缺字幕 " + sub);
            }
        }
        h.succeed();
    }

    private static JsonObject json(GameTestHelper h, String path) {
        try (InputStream in = MercySoundTests.class.getResourceAsStream(path)) {
            h.assertTrue(in != null, "找不到 " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
