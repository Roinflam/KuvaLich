package pers.roinflam.kuvalich.client.fx;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.script.ElementStatusFx;
import pers.roinflam.kuvalich.network.packet.DamageInfo;

/**
 * 一行「元素小图标 + 层数」的状态行：玄骸 Boss 条下面用
 *
 * <p>图标和伤害数字后面跟的元素图标是同一套，白色小字；层数是元素等级 + 1，只有 1 层的不写数字
 * （电击、切割只看有没有）。元素按固定顺序排，一行里同一种元素只出现一次。</p>
 *
 * <p>同一只实体的状态没变时直接用缓存好的字串和字宽，渲染里不拼字串。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ElementStatusRow {

    /** 一行的颜色：白，带一点点冷 */
    private static final int TEXT_RGB = FxPalette.DMG_WHITE;
    /** 层数最多记到 15（4 位） */
    private static final int MAX_STACKS = 15;

    private static final class Row {
        long signature = -1L;
        String text = "";
        int width;
        long lastUseMs;
    }

    private static final Int2ObjectOpenHashMap<Row> CACHE = new Int2ObjectOpenHashMap<>();
    private static final StringBuilder SB = new StringBuilder(48);
    private static long lastPruneMs;

    private ElementStatusRow() {
    }

    /**
     * 在 HUD 上以 (cx, y) 为上沿中心画一行
     *
     * @return 画出来的高度（像素），没有可画的元素状态为 0
     */
    public static int draw(GuiGraphics g, LivingEntity entity, int cx, int y, float alpha) {
        Row row = rowOf(entity);
        if (row == null) {
            return 0;
        }
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (a < 8) {
            return 0;
        }
        Font font = Minecraft.getInstance().font;
        g.drawString(font, row.text, cx - row.width / 2, y, (a << 24) | TEXT_RGB, true);
        return font.lineHeight + 1;
    }

    /** 这只实体此刻的状态行；没有任何元素状态时返回 null */
    private static Row rowOf(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        long sig = 0L;
        for (int idx = 0; idx < DamageInfo.ELEMENT_COUNT; idx++) {
            int n = Math.min(MAX_STACKS, ElementStatusFx.stacks(entity, idx));
            sig |= (long) n << (idx * 4);
        }
        long now = KuvaFxDirector.clockMs();
        prune(now);
        if (sig == 0L) {
            return null;
        }
        Row row = CACHE.get(entity.getId());
        if (row == null) {
            row = new Row();
            CACHE.put(entity.getId(), row);
        }
        row.lastUseMs = now;
        if (row.signature != sig) {
            row.signature = sig;
            SB.setLength(0);
            for (int idx = 0; idx < DamageInfo.ELEMENT_COUNT; idx++) {
                int n = (int) ((sig >>> (idx * 4)) & 0xF);
                if (n <= 0) {
                    continue;
                }
                if (SB.length() > 0) {
                    SB.append(' ');
                }
                SB.append(DamageInfo.elementGlyph(idx));
                if (n > 1) {
                    SB.append(n);
                }
            }
            row.text = SB.toString();
            row.width = Minecraft.getInstance().font.width(row.text);
        }
        return row;
    }

    /** 每 5 秒清一次 10 秒没画过的缓存 */
    private static void prune(long now) {
        if (now - lastPruneMs < 5000L || CACHE.isEmpty()) {
            return;
        }
        lastPruneMs = now;
        ObjectIterator<Int2ObjectMap.Entry<Row>> it = CACHE.int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastUseMs > 10_000L) {
                it.remove();
            }
        }
    }
}
