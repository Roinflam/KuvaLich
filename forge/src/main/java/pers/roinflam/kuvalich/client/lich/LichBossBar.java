package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.ElementStatusRow;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 专属玄骸的 Boss 条：名字、三段密语副条、血条、等级徽章、状态行
 *
 * <p>服务端发的是普通的 Boss 条，名字用 {@code kuvalich.bossbar.lich} 这个键、带四个参数（全大写名字、等级、
 * 主人名、主人 UUID），原版画法下只显示名字。这里认出这个键就取消原版绘制、自己画，并把这一条的行高改成
 * {@code max(24, 0.095 × 屏高)}，后面的 Boss 条照原版往下排。别的 Boss 条不动。</p>
 *
 * <p>纵向排布：名字在最上，下面是三段副条（主人看：已揭示几条亮几段）、血条（掉血后一段淡色残影停 250 ms 再追上）、
 * 压在血条中间的菱形等级徽章，最下一行是适应层数、交战增幅和元素状态。没有淡入、没有进场动画。
 * 倒地时血条和副条一起变灰，每段副条中点钉一颗小菱。</p>
 *
 * <p>别人的玄骸（客户端比对第 4 个参数和本地玩家 UUID）名字后面接「· 某某的玄骸」，副条全暗、小晶是灰的。
 * 处决运镜中整条不画（行高照样留着，免得别的条跳位）。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichBossBar {

    /** 服务端 Boss 条名字用的语言键 */
    public static final String KEY = "kuvalich.bossbar.lich";

    /** 掉血后残影停多久再追 */
    private static final int TRAIL_HOLD_MS = 250;
    /** 残影追上用多久 */
    private static final int TRAIL_CHASE_MS = 600;
    /** 多久重新在世界里找一次对应的实体 */
    private static final int RESCAN_MS = 500;
    /** 多久没画过的条从缓存里清掉 */
    private static final int FORGET_MS = 5000;
    /** 交战增幅到这个千分比才显示小牌 */
    private static final int FURY_SHOW_PERMILLE = 50;

    /** 每一条 Boss 条自己的状态：解析好的参数、对应实体、残影 */
    private static final class Bar {
        String nameUpper = "";
        String rankText = "";
        @Nullable
        UUID owner;
        String ownerName = "";
        boolean mine;
        /** 别人看时名字后面那半句（含前面的「 · 」），按语言缓存 */
        String rest = "";
        String restLang = "";
        Component lastName;
        boolean otherView;

        int entityId = -1;
        long nextScanMs;

        float trail = -1.0F;
        float lastProgress = -1.0F;
        long holdUntilMs;
        float chaseFrom;
        long lastSeenMs;
    }

    private static final Map<UUID, Bar> BARS = new HashMap<>();
    private static long lastPruneMs;

    /** 0~99 的数字字串，层数和百分比用，免得每帧拼字串 */
    private static final String[] NUM = new String[100];
    private static final String[] PLUS_PCT = new String[1000];

    static {
        for (int i = 0; i < NUM.length; i++) {
            NUM[i] = Integer.toString(i);
        }
    }

    private LichBossBar() {
    }

    /** 这条 Boss 条是不是本模组的玄骸条 */
    public static boolean isLichBar(LerpingBossEvent event) {
        return event.getName().getContents() instanceof TranslatableContents tc && KEY.equals(tc.getKey());
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!isLichBar(boss)) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        int h = g.guiHeight();
        int inc = Math.max(24, Math.round(0.095F * h));
        event.setIncrement(inc);
        if (MercyClient.hidesHud()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        Bar bar = barOf(boss, now);
        int used = draw(g, boss, bar, event.getY(), now);
        if (used > inc) {
            event.setIncrement(used);
        }
    }

    // ==================== 画 ====================

    /** @return 实际用掉的高度（元素状态那一行可能超出固定行高） */
    private static int draw(GuiGraphics g, LerpingBossEvent boss, Bar bar, int y0, long now) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float cx = w * 0.5F;
        float bw = Math.round(0.264F * w);
        float left = cx - bw * 0.5F;
        float lw = GuiShapes.pixels(g, GuiShapes.lineWidthPx());

        AbstractKuva entity = entityOf(bar, now);
        boolean downed = entity != null && entity.getMercyState() != MercyState.NONE
                && entity.getMercyState() != MercyState.LEAVING;
        float progress = boss.getProgress();
        updateTrail(bar, progress, now);

        // ---- 名字一行（量好位置，字最后画） ----
        float ts = SpacedText.round8(0.024F * h / 7.0F, 0.5F);
        SpacedText.Glyphs nameGl = SpacedText.of(font, bar.nameUpper);
        float nameSpacing = nameGl.chars.length == 0 ? 0.0F : 0.35F * nameGl.plainWidth / nameGl.chars.length;
        float nameW = nameGl.width(nameSpacing) * ts;
        float restW = bar.mine ? 0.0F : font.width(bar.rest) * ts;
        float nameX = cx - (nameW + restW) * 0.5F;
        float nameMid = y0 + 3.5F * ts;

        float segY = y0 + 0.023F * h;
        float barY = y0 + 0.036F * h;
        float barH = Math.max(2, Math.round(0.007F * h));
        float badgeY = y0 + 0.055F * h;
        float a = Math.max(6, Math.round(0.022F * h));
        float statusY = y0 + 0.072F * h;
        float iconH = Math.max(6.0F, 0.016F * h);

        GuiShapes.begin(g);
        // 名字右边的小晶
        float ca = 0.006F * h;
        int crystal = bar.mine ? FxPalette.KUVA : FxPalette.ASH;
        GuiShapes.diamondFill(g, nameX + nameW + restW + 4.0F + ca * 0.6F, nameMid, ca * 0.6F, ca,
                GuiShapes.argb(crystal, 0.95F));

        // 三段副条
        float gap = 0.004F * w;
        float segW = (bw - 2.0F * gap) / 3.0F;
        int clues = bar.mine ? ClientLichState.get().clues() : 0;
        for (int i = 0; i < 3; i++) {
            float sx = left + i * (segW + gap);
            int col;
            if (downed) {
                col = GuiShapes.argb(FxPalette.ASH, 0.6F);
            } else {
                col = GuiShapes.argb(FxPalette.KUVA_PALE, i < clues ? 0.95F : 0.3F);
            }
            GuiShapes.rect(g, sx, segY, sx + segW, segY + lw, col);
            if (downed) {
                float pin = Math.max(1.5F, 1.5F * lw);
                GuiShapes.diamondFill(g, sx + segW * 0.5F, segY + lw * 0.5F, pin, pin * 1.4F,
                        GuiShapes.argb(FxPalette.ASH, 0.9F));
            }
        }

        // 血条：底、残影、填充、上沿
        GuiShapes.rect(g, left, barY, left + bw, barY + barH, GuiShapes.argb(0x000000, 0.55F));
        if (bar.trail > progress + 1.0E-3F) {
            GuiShapes.rect(g, left + bw * progress, barY, left + bw * Math.min(1.0F, bar.trail), barY + barH,
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.6F));
        }
        if (progress > 0.0F) {
            int fill = downed ? GuiShapes.argb(FxPalette.ASH, 0.6F) : GuiShapes.argb(FxPalette.KUVA, 1.0F);
            GuiShapes.rect(g, left, barY, left + bw * progress, barY + barH, fill);
            if (!downed) {
                GuiShapes.rect(g, left, barY, left + bw * progress, barY + Math.min(barH, lw),
                        GuiShapes.argb(FxPalette.KUVA_HOT, 0.35F));
            }
        }

        // 等级徽章：压在血条正中
        float hw = a * 0.75F;
        GuiShapes.diamondFill(g, cx, badgeY, hw, a, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F));
        GuiShapes.diamondOutline(g, cx, badgeY, hw, a, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 1.0F));

        // 状态行的图标（字在后面一起画）
        int adapt = entity != null ? entity.getAdaptPacked() : 0;
        int fury = entity != null ? entity.getFuryPermille() : 0;
        float rowW = statusRowWidth(font, adapt, fury, iconH);
        float rx = cx - rowW * 0.5F;
        float iconGap = iconH * 0.35F;
        float digitTs = SpacedText.round8(iconH / 7.0F, 0.5F);
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n <= 0) {
                continue;
            }
            drawCategoryIcon(g, c, rx + iconH * 0.5F, statusY + iconH * 0.5F, iconH, lw,
                    GuiShapes.argb(FxPalette.BONE, 0.85F));
            rx += iconH + 1.0F + font.width(NUM[n]) * digitTs + iconGap;
        }
        float furyX = rx;
        String furyText = null;
        if (fury >= FURY_SHOW_PERMILLE) {
            furyText = plusPct(Math.round(fury / 10.0F));
            float fw = font.width(furyText) * digitTs + 4.0F;
            GuiShapes.rect(g, furyX, statusY, furyX + fw, statusY + iconH, GuiShapes.argb(FxPalette.KUVA_MIST, 0.7F));
            GuiShapes.rect(g, furyX, statusY, furyX + fw, statusY + lw, GuiShapes.argb(FxPalette.KUVA, 0.6F));
        }
        GuiShapes.end();

        // ---- 字 ----
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(nameX, y0, 0.0F);
        ps.scale(ts, ts, 1.0F);
        SpacedText.draw(g, font, bar.nameUpper, 0.0F, 0.0F, nameSpacing, GuiShapes.argb(FxPalette.BONE, 0.95F), true);
        if (!bar.mine && !bar.rest.isEmpty()) {
            float rx0 = nameW / ts;
            font.drawInBatch(bar.rest, rx0, 0.0F, GuiShapes.argb(FxPalette.ASH, 1.0F), true, ps.last().pose(),
                    g.bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
        ps.popPose();

        // 等级数字：缩到菱形里放得下
        float rankScale = a * 1.1F / 7.0F;
        int rankW = font.width(bar.rankText);
        if (rankW > 0) {
            rankScale = Math.min(rankScale, hw * 1.25F / rankW);
        }
        ps.pushPose();
        ps.translate(cx, badgeY, 0.0F);
        ps.scale(rankScale, rankScale, 1.0F);
        font.drawInBatch(bar.rankText, -rankW * 0.5F + 0.5F, -3.5F, GuiShapes.argb(FxPalette.BONE, 1.0F), false,
                ps.last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ps.popPose();

        // 层数、增幅
        rx = cx - rowW * 0.5F;
        ps.pushPose();
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n <= 0) {
                continue;
            }
            float tx = rx + iconH + 1.0F;
            drawSmall(g, font, NUM[n], tx, statusY + iconH * 0.5F - 3.5F * digitTs, digitTs,
                    GuiShapes.argb(FxPalette.BONE, 0.85F));
            rx = tx + font.width(NUM[n]) * digitTs + iconGap;
        }
        if (furyText != null) {
            drawSmall(g, font, furyText, furyX + 2.0F, statusY + iconH * 0.5F - 3.5F * digitTs, digitTs,
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F));
        }
        ps.popPose();
        g.flush();

        // 元素状态：图标行下面再一行
        int used = Math.round(statusY + iconH - y0) + 1;
        if (entity != null) {
            int rowY = Math.round(statusY + (rowW > 0.0F ? iconH + 1.0F : 0.0F));
            int eh = ElementStatusRow.draw(g, entity, Math.round(cx), rowY, 1.0F);
            if (eh > 0) {
                used = Math.max(used, rowY + eh - y0 + 1);
            }
        }
        return used;
    }

    private static void drawSmall(GuiGraphics g, Font font, String s, float x, float y, float scale, int argb) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0.0F);
        ps.scale(scale, scale, 1.0F);
        font.drawInBatch(s, 0.0F, 0.0F, argb, false, ps.last().pose(), g.bufferSource(), Font.DisplayMode.NORMAL,
                0, 0xF000F0);
        ps.popPose();
    }

    private static float statusRowWidth(Font font, int adapt, int fury, float iconH) {
        float digitTs = SpacedText.round8(iconH / 7.0F, 0.5F);
        float iconGap = iconH * 0.35F;
        float wsum = 0.0F;
        boolean any = false;
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n > 0) {
                wsum += iconH + 1.0F + font.width(NUM[n]) * digitTs + iconGap;
                any = true;
            }
        }
        if (fury >= FURY_SHOW_PERMILLE) {
            wsum += font.width(plusPct(Math.round(fury / 10.0F))) * digitTs + 4.0F;
        } else if (any) {
            wsum -= iconGap;
        }
        return wsum;
    }

    /**
     * 伤害类别的小线描：近战 = 一把剑、弓箭 = 一支箭、其他弹射物 = 带尾迹的小球、魔法 = 四角星、其他 = 空心小菱
     *
     * @param size 图标边长
     */
    static void drawCategoryIcon(GuiGraphics g, int category, float cx, float cy, float size, float lw, int argb) {
        float r = size * 0.5F;
        switch (category) {
            case 0 -> {
                GuiShapes.hairline(g, cx - r * 0.75F, cy + r * 0.75F, cx + r * 0.8F, cy - r * 0.8F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.75F, cy + r * 0.05F, cx - r * 0.05F, cy + r * 0.75F, lw, argb);
            }
            case 1 -> {
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx + r * 0.8F, cy - r * 0.8F, lw, argb);
                GuiShapes.hairline(g, cx + r * 0.8F, cy - r * 0.8F, cx + r * 0.15F, cy - r * 0.65F, lw, argb);
                GuiShapes.hairline(g, cx + r * 0.8F, cy - r * 0.8F, cx + r * 0.65F, cy - r * 0.15F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx - r * 0.8F, cy + r * 0.3F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx - r * 0.3F, cy + r * 0.8F, lw, argb);
            }
            case 2 -> {
                GuiShapes.ring(g, cx + r * 0.35F, cy - r * 0.35F, r * 0.42F, lw, argb, 1.0F);
                GuiShapes.hairline(g, cx - r * 0.85F, cy + r * 0.35F, cx - r * 0.1F, cy - r * 0.15F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.35F, cy + r * 0.85F, cx + r * 0.15F, cy + r * 0.1F, lw, argb);
            }
            case 3 -> {
                GuiShapes.hairline(g, cx, cy - r * 0.9F, cx, cy + r * 0.9F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.9F, cy, cx + r * 0.9F, cy, lw, argb);
                GuiShapes.diamondFill(g, cx, cy, r * 0.32F, r * 0.32F, argb);
            }
            default -> GuiShapes.diamondOutline(g, cx, cy, r * 0.5F, r * 0.75F, lw, argb);
        }
    }

    private static String plusPct(int n) {
        int i = Math.max(0, Math.min(PLUS_PCT.length - 1, n));
        String s = PLUS_PCT[i];
        if (s == null) {
            s = "+" + i + "%";
            PLUS_PCT[i] = s;
        }
        return s;
    }

    // ==================== 状态 ====================

    private static Bar barOf(LerpingBossEvent boss, long now) {
        Bar bar = BARS.get(boss.getId());
        if (bar == null) {
            bar = new Bar();
            BARS.put(boss.getId(), bar);
        }
        bar.lastSeenMs = now;
        Component name = boss.getName();
        Minecraft mc = Minecraft.getInstance();
        String lang = mc.options.languageCode;
        if (name != bar.lastName || !lang.equals(bar.restLang) || bar.otherView != ArchiveDevCommands.forceOtherView) {
            parse(bar, name, lang);
        }
        if (now - lastPruneMs > 1000L) {
            lastPruneMs = now;
            Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastSeenMs > FORGET_MS) {
                    it.remove();
                }
            }
        }
        return bar;
    }

    /** 名字变了（服务端每 20 tick 重设一次，内容一样也是新对象）或者换了语言才重新解析 */
    private static void parse(Bar bar, Component name, String lang) {
        bar.lastName = name;
        bar.otherView = ArchiveDevCommands.forceOtherView;
        bar.restLang = lang;
        Object[] args = name.getContents() instanceof TranslatableContents tc ? tc.getArgs() : new Object[0];
        String upper = args.length > 0 ? argString(args[0]) : name.getString();
        String rank = args.length > 1 ? argString(args[1]) : "";
        String ownerName = args.length > 2 ? argString(args[2]) : "";
        UUID owner = null;
        if (args.length > 3) {
            try {
                owner = UUID.fromString(argString(args[3]));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        if (!upper.equals(bar.nameUpper) || !ownerName.equals(bar.ownerName) || bar.rest.isEmpty()) {
            String full = Component.translatable("kuvalich.nameplate.lich.other", upper, ownerName).getString();
            int at = full.indexOf(upper);
            bar.rest = at >= 0 ? full.substring(at + upper.length()) : " · " + full;
        }
        bar.nameUpper = upper;
        bar.rankText = rank;
        bar.ownerName = ownerName;
        if (owner == null || !owner.equals(bar.owner)) {
            bar.entityId = -1;
            bar.nextScanMs = 0L;
        }
        bar.owner = owner;
        Minecraft mc = Minecraft.getInstance();
        bar.mine = !ArchiveDevCommands.forceOtherView
                && (owner == null || (mc.player != null && owner.equals(mc.player.getUUID())));
    }

    private static String argString(Object arg) {
        if (arg instanceof Component c) {
            return c.getString();
        }
        return String.valueOf(arg);
    }

    /** 世界里对应的那只玄骸（按主人 UUID 找），离得太远没加载时为 null */
    @Nullable
    private static AbstractKuva entityOf(Bar bar, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || bar.owner == null) {
            return null;
        }
        if (bar.entityId >= 0) {
            Entity e = mc.level.getEntity(bar.entityId);
            if (e instanceof KuvaMasterEntity k && !k.isRemoved() && bar.owner.equals(k.getOwnerUUID())) {
                return k;
            }
            bar.entityId = -1;
        }
        if (now < bar.nextScanMs) {
            return null;
        }
        bar.nextScanMs = now + RESCAN_MS;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof KuvaMasterEntity k && e.getId() >= 0 && bar.owner.equals(k.getOwnerUUID())) {
                bar.entityId = e.getId();
                return k;
            }
        }
        return null;
    }

    private static void updateTrail(Bar bar, float progress, long now) {
        if (bar.trail < 0.0F || progress >= bar.trail) {
            bar.trail = progress;
            bar.lastProgress = progress;
            bar.holdUntilMs = 0L;
            return;
        }
        if (progress < bar.lastProgress - 1.0E-4F) {
            // 又掉了一截：残影停在原处，重新等 250 ms
            bar.holdUntilMs = now + TRAIL_HOLD_MS;
            bar.chaseFrom = bar.trail;
        }
        bar.lastProgress = progress;
        if (now >= bar.holdUntilMs) {
            float t = FxCurve.clamp01((now - bar.holdUntilMs) / (float) TRAIL_CHASE_MS);
            bar.trail = bar.chaseFrom + (progress - bar.chaseFrom) * FxCurve.easeOutCubic(t);
            if (t >= 1.0F) {
                bar.trail = progress;
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BARS.clear();
    }
}
