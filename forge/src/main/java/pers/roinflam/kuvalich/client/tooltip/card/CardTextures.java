package pers.roinflam.kuvalich.client.tooltip.card;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import pers.roinflam.kuvalich.utils.Reference;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 展开卡的贴图路径、各段边框的偏移（{@code frames.json}），以及给缩小着画的贴图补多级渐远。
 */
final class CardTextures {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "textures/gui/hover_card/";

    static final ResourceLocation SHADOW = rl("shadow.png");
    static final ResourceLocation PLATE_TOP = rl("plate_top.png");
    static final ResourceLocation PLATE_BOT = rl("plate_bot.png");
    static final ResourceLocation GLOW = rl("glow.png");
    static final ResourceLocation MOTE = rl("mote.png");
    static final ResourceLocation STAR = rl("star.png");
    static final ResourceLocation CYCLE = rl("cycle.png");

    /** 小卡一档边框：横向伸出 ex，上框、底托各自的纵向偏移和贴图大小 */
    record Compact(int ex, int topOy, int botOy, int tw, int th, int bw, int bh) {
    }

    /** 大卡边框：按 ts 像素/单位烘的，el/et 伸出，up 是底托顶比 bt 高出的单位数 */
    record Large(int el, int et, int fw, int up, int ts, int tw, int th, int bw, int bh) {
    }

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Set<Integer> MIPMAPPED = new HashSet<>();
    private static Map<String, Compact> compact;
    private static Map<String, Large> large;
    private static boolean loadFailed;

    private CardTextures() {
    }

    static ResourceLocation rl(String path) {
        return new ResourceLocation(Reference.MOD_ID, DIR + path);
    }

    static ResourceLocation cached(String path) {
        return CACHE.computeIfAbsent(path, CardTextures::rl);
    }

    static ResourceLocation compactTop(CardTier t, int wp) {
        return cached("frame/" + t.id + "/c" + wp + "_top.png");
    }

    static ResourceLocation compactBot(CardTier t, int wp) {
        return cached("frame/" + t.id + "/c" + wp + "_bot.png");
    }

    static ResourceLocation largeTop(CardTier t, boolean thin) {
        return cached("frame/" + t.id + (thin ? "/l_thin_top.png" : "/l_full_top.png"));
    }

    static ResourceLocation largeBot(CardTier t, boolean thin) {
        return cached("frame/" + t.id + (thin ? "/l_thin_bot.png" : "/l_full_bot.png"));
    }

    static ResourceLocation veil(CardTier t) {
        return cached("veil/" + t.veilId() + ".png");
    }

    static ResourceLocation iconRing(CardModel m) {
        return cached("icon/ring_" + (m.warframe ? "warframe_" : "item_") + m.rarity + ".png");
    }

    static ResourceLocation iconCore(CardModel m) {
        return cached("icon/core_" + (m.warframe ? "warframe_" : "item_") + m.rarity + ".png");
    }

    static Compact compact(CardTier t, int wp) {
        ensureLoaded();
        return compact == null ? null : compact.get(t.id + "/" + wp);
    }

    static Large large(CardTier t, boolean thin) {
        ensureLoaded();
        return large == null ? null : large.get(t.id + (thin ? "/thin" : "/full"));
    }

    private static void ensureLoaded() {
        if (compact != null || loadFailed) {
            return;
        }
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl("frames.json"));
            if (res.isEmpty()) {
                loadFailed = true;
                return;
            }
            Map<String, Compact> c = new HashMap<>();
            Map<String, Large> l = new HashMap<>();
            try (Reader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject cj = root.getAsJsonObject("compact");
                for (String tier : cj.keySet()) {
                    JsonObject byW = cj.getAsJsonObject(tier);
                    for (String w : byW.keySet()) {
                        JsonObject o = byW.getAsJsonObject(w);
                        c.put(tier + "/" + w, new Compact(i(o, "ex"), i(o, "topOy"), i(o, "botOy"),
                                i(o, "tw"), i(o, "th"), i(o, "bw"), i(o, "bh")));
                    }
                }
                JsonObject lj = root.getAsJsonObject("large");
                for (String tier : lj.keySet()) {
                    JsonObject byK = lj.getAsJsonObject(tier);
                    for (String k : byK.keySet()) {
                        JsonObject o = byK.getAsJsonObject(k);
                        l.put(tier + "/" + k, new Large(i(o, "el"), i(o, "et"), i(o, "fw"), i(o, "up"), i(o, "ts"),
                                i(o, "tw"), i(o, "th"), i(o, "bw"), i(o, "bh")));
                    }
                }
            }
            compact = c;
            large = l;
        } catch (Exception e) {
            LOGGER.warn("Failed to read hover card frame offsets", e);
            loadFailed = true;
        }
    }

    private static int i(JsonObject o, String k) {
        return o.get(k).getAsInt();
    }

    /**
     * 绑定贴图并设过滤方式。{@code smooth} 时用线性 + 多级渐远：原版贴图只有第 0 级，第一次用到时补生成。
     */
    static void bind(ResourceLocation tex, boolean smooth) {
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
        RenderSystem.setShaderTexture(0, t.getId());
        RenderSystem.bindTexture(t.getId());
        if (smooth) {
            if (MIPMAPPED.add(t.getId())) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 4);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } else {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    static void reset() {
        compact = null;
        large = null;
        loadFailed = false;
        MIPMAPPED.clear();
    }
}
