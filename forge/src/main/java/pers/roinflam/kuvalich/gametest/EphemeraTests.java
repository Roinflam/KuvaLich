package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.nemesis.Finale;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/** 幻纹诞生、幻纹物品、玩家账本（C1a） */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EphemeraTests {

    private static final String BATCH = "kl_ephemera";

    private EphemeraTests() {
    }

    private static RequiemCard born(long seed, String element) {
        RequiemCard card = new RequiemCard();
        card.beginLich("Vesk Arru", seed, 0, 10, element, "minecraft:plains", "minecraft:overworld");
        return card;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void birthRollIsSeededAndFrozen(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        RequiemCard always = born(77L, "fire");
        String want = always.getLichEphemera();
        h.assertTrue(always.isLichEphemeraRolled() && EphemeraType.byId(want) != null
                        && want.equals(born(77L, "ice").getLichEphemera()),
                "概率 1：必带，款式只看种子、不看元素：" + want);

        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 0.0D);
        RequiemCard never = born(77L, "fire");
        h.assertTrue(never.isLichEphemeraRolled() && never.getLichEphemera().isEmpty(), "概率 0：必不带");

        // 同种子同结果：一半概率下挑 200 个种子，各诞生两次比
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 0.5D);
        int carried = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (long seed = 1000L; seed < 1200L; seed++) {
            String a = born(seed, "").getLichEphemera();
            String b = born(seed, "").getLichEphemera();
            h.assertTrue(a.equals(b), "同种子两次诞生结果应当相同：" + seed);
            if (!a.isEmpty()) {
                carried++;
                h.assertTrue(EphemeraType.byId(a) != null, "掷出来的应当是表里的款式：" + a);
                seen.add(a);
            }
        }
        h.assertTrue(carried > 60 && carried < 140, "一半概率下 200 只应当有一半上下带幻纹：" + carried);
        h.assertTrue(seen.size() == EphemeraType.all().size(), "六款都掷得到：" + seen);

        // 改配置后已有玄骸不变，存读也不变
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 0.0D);
        RequiemCard loaded = new RequiemCard();
        loaded.deserializeNBT(always.serializeNBT());
        h.assertTrue(want.equals(loaded.getLichEphemera()) && loaded.isLichEphemeraRolled(), "存读后幻纹应当还在");
        h.assertTrue(!loaded.ensureEphemeraRolled() && want.equals(loaded.getLichEphemera()),
                "改了概率，已有玄骸不应当重掷");
        RequiemCard cloned = new RequiemCard();
        cloned.clone(loaded);
        h.assertTrue(want.equals(cloned.getLichEphemera()) && cloned.isLichEphemeraRolled(), "克隆应当带上幻纹");

        // 结束这一轮清掉
        loaded.endLichRound(RequiemCard.FATE_VANQUISHED);
        h.assertTrue(loaded.getLichEphemera().isEmpty() && !loaded.isLichEphemeraRolled(), "结束一轮应当清掉幻纹");

        h.assertTrue(EphemeraType.all().size() == 6, "首批六款造型");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void typeTableAndElements(GameTestHelper h) {
        List<String> ids = EphemeraType.all().stream().map(EphemeraType::id).toList();
        h.assertTrue(ids.equals(List.of("surge", "orbit", "plume", "wing", "coil", "vortex")), "款式表的顺序和 id：" + ids);
        for (EphemeraType t : EphemeraType.all()) {
            h.assertTrue(EphemeraType.byId(t.id()) == t, "按 id 查得回：" + t.id());
        }
        // 改版前按元素名的开发档 id、空串、null 都查不到，按不带
        h.assertTrue(EphemeraType.byId("fire") == null && EphemeraType.byId("") == null && EphemeraType.byId(null) == null,
                "旧 id 查不到");
        h.assertTrue(EphemeraType.normalizeElement("slash").equals(EphemeraType.DEFAULT_ELEMENT)
                && EphemeraType.normalizeElement(null).equals(EphemeraType.DEFAULT_ELEMENT)
                && "ice".equals(EphemeraType.normalizeElement("ice")), "不是赤毒武器的元素按默认");
        for (long seed = 0L; seed < 50L; seed++) {
            h.assertTrue(EphemeraType.elementFor(seed, "").equals(LichProfile.weaponElement(seed)),
                    "元素没定的老玄骸按种子取元素：" + seed);
            h.assertTrue("magnetic".equals(EphemeraType.elementFor(seed, "magnetic")), "定了元素就用定下的");
        }
        for (String el : KuvaWeaponUtil.LICH_ELEMENTS) {
            h.assertTrue(EphemeraType.defaultColor(el) == KuvaPalette.element(el), "默认颜色 = 元素色：" + el);
        }

        // 卡上存着旧 id：读出来按不带，不崩
        RequiemCard card = born(77L, "fire");
        CompoundTag tag = card.serializeNBT();
        tag.putString("lichEphemera", "fire");
        RequiemCard old = new RequiemCard();
        old.deserializeNBT(tag);
        h.assertTrue(old.hasLich() && old.getLichEphemera().isEmpty(), "卡上旧 id 按不带");
        CompoundTag entry = new CompoundTag();
        entry.putString(pers.roinflam.kuvalich.nemesis.AllyRoster.EPHEMERA, "poison");
        h.assertTrue(pers.roinflam.kuvalich.nemesis.AllyRoster.ephemeraOf(entry).isEmpty(), "名册里旧 id 按不带");
        entry.putString(pers.roinflam.kuvalich.nemesis.AllyRoster.EPHEMERA, "coil");
        h.assertTrue("coil".equals(pers.roinflam.kuvalich.nemesis.AllyRoster.ephemeraOf(entry)), "名册里新 id 照读");

        // 物品上的旧 id：款式查不到、tooltip 只写一行，不崩
        ItemStack legacy = new ItemStack(KuvaLichItems.EPHEMERA.get());
        legacy.getOrCreateTag().putString(EphemeraItem.TAG_TYPE, "fire");
        h.assertTrue(EphemeraItem.getType(legacy) == null && EphemeraItem.tooltipLines(legacy).size() == 1, "物品上旧 id");

        // 元素 NBT 往返；坏元素按默认
        ItemStack stack = EphemeraItem.create(EphemeraType.VORTEX, "electricity", "Vesk Arru");
        ItemStack back = ItemStack.of(stack.save(new CompoundTag()));
        h.assertTrue(EphemeraItem.getType(back) == EphemeraType.VORTEX && "electricity".equals(EphemeraItem.getElement(back))
                && "Vesk Arru".equals(EphemeraItem.getOrigin(back)), "款式、元素、来历存读");
        h.assertTrue(ItemStack.matches(stack, back), "存读前后一致");
        h.assertTrue(EphemeraItem.getColor1(back) == KuvaPalette.element("electricity"), "缺省颜色跟元素");
        ItemStack bad = EphemeraItem.create(EphemeraType.SURGE, "slash", "");
        h.assertTrue(EphemeraType.DEFAULT_ELEMENT.equals(bad.getTag().getString(EphemeraItem.TAG_ELEMENT)), "造的时候坏元素按默认写");
        back.getOrCreateTag().putString(EphemeraItem.TAG_ELEMENT, "nonsense");
        h.assertTrue(EphemeraType.DEFAULT_ELEMENT.equals(EphemeraItem.getElement(back)), "读的时候坏元素按默认");
        back.getTag().remove(EphemeraItem.TAG_ELEMENT);
        h.assertTrue(EphemeraType.DEFAULT_ELEMENT.equals(EphemeraItem.getElement(back)), "缺元素键按默认");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void backfillOnlyOnce(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraBackfill, true);
        CompoundTag old = born(4242L, "ice").serializeNBT();
        old.remove("lichEphemera");
        old.remove("lichEphemeraRolled");
        RequiemCard legacy = new RequiemCard();
        legacy.deserializeNBT(old);
        h.assertTrue(legacy.hasLich() && !legacy.isLichEphemeraRolled() && legacy.getLichEphemera().isEmpty(),
                "老存档读进来：玄骸在、还没掷过");
        String want = EphemeraType.roll(4242L, 1.0D);
        h.assertTrue(legacy.ensureEphemeraRolled() && want.equals(legacy.getLichEphemera()), "第一次补掷应当生效");
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 0.0D);
        h.assertTrue(!legacy.ensureEphemeraRolled() && want.equals(legacy.getLichEphemera()), "补掷只发生一次");

        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraBackfill, false);
        RequiemCard off = new RequiemCard();
        off.deserializeNBT(old);
        h.assertTrue(off.ensureEphemeraRolled() && off.isLichEphemeraRolled() && off.getLichEphemera().isEmpty(),
                "补掷关着：只置标记，一律不带");

        RequiemCard empty = new RequiemCard();
        h.assertTrue(!empty.ensureEphemeraRolled() && !empty.isLichEphemeraRolled(), "没有玄骸不补掷");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void itemLookFieldsClamp(GameTestHelper h) {
        EphemeraType ice = EphemeraType.byId("plume");
        h.assertTrue(ice != null, "应当有曳翎款");
        ItemStack stack = EphemeraItem.create(ice, "ice", "Vesk Arru");
        h.assertTrue(stack.getMaxStackSize() == 1, "幻纹不能叠");
        h.assertTrue(EphemeraItem.getType(stack) == ice && "ice".equals(EphemeraItem.getElement(stack))
                && "Vesk Arru".equals(EphemeraItem.getOrigin(stack)), "款式、元素和来历");
        int iceColor = KuvaPalette.element("ice");
        h.assertTrue(EphemeraItem.getColor1(stack) == iceColor && EphemeraItem.getColor2(stack) == iceColor,
                "缺省颜色 = 元素色");
        h.assertTrue(EphemeraItem.getGradient(stack) == EphemeraItem.GRADIENT_OFF && EphemeraItem.getScale(stack) == 1.0F
                && EphemeraItem.getYOffset(stack) == 0.0F, "缺省：渐变关、大小 1、高度 0");

        EphemeraItem.setColors(stack, 0xFF123456, 0x000000);
        EphemeraItem.setGradient(stack, 7);
        EphemeraItem.setScale(stack, 5.0F);
        EphemeraItem.setYOffset(stack, -3.0F);
        h.assertTrue(EphemeraItem.getColor1(stack) == 0x123456, "颜色只留 RGB：" + Integer.toHexString(EphemeraItem.getColor1(stack)));
        h.assertTrue(EphemeraItem.getColor2(stack) == 0x404040, "纯黑提亮到下限");
        h.assertTrue(EphemeraItem.clampColor(0x100800) == 0x402000, "暗色按比例提亮、色相不变");
        h.assertTrue(EphemeraItem.getGradient(stack) == EphemeraItem.GRADIENT_OFF, "渐变越界按关");
        h.assertTrue(EphemeraItem.getScale(stack) == EphemeraItem.SCALE_MAX && EphemeraItem.getYOffset(stack) == EphemeraItem.Y_OFFSET_MIN,
                "大小、高度夹到范围");
        EphemeraItem.setScale(stack, Float.NaN);
        h.assertTrue(EphemeraItem.getScale(stack) == 1.0F, "NaN 按默认");

        // NBT 被人直接改成越界值，读出来也夹住
        stack.getOrCreateTag().putFloat(EphemeraItem.TAG_SCALE, 0.01F);
        stack.getOrCreateTag().putFloat(EphemeraItem.TAG_Y_OFFSET, 9.0F);
        stack.getOrCreateTag().putInt(EphemeraItem.TAG_GRADIENT, -2);
        h.assertTrue(EphemeraItem.getScale(stack) == EphemeraItem.SCALE_MIN && EphemeraItem.getYOffset(stack) == EphemeraItem.Y_OFFSET_MAX
                && EphemeraItem.getGradient(stack) == EphemeraItem.GRADIENT_OFF, "读的时候也夹范围");

        EphemeraItem.setGradient(stack, EphemeraItem.GRADIENT_FLOW);
        ItemStack back = ItemStack.of(stack.save(new CompoundTag()));
        h.assertTrue(EphemeraItem.getGradient(back) == EphemeraItem.GRADIENT_FLOW && EphemeraItem.getColor1(back) == 0x123456,
                "存读后外观参数还在");
        EphemeraItem.resetLook(back);
        h.assertTrue(EphemeraItem.getColor1(back) == iceColor && EphemeraItem.getGradient(back) == EphemeraItem.GRADIENT_OFF
                && EphemeraItem.getScale(back) == 1.0F, "恢复默认");

        // 只读不应当给没有 NBT 的物品建空 tag
        ItemStack bare = new ItemStack(KuvaLichItems.EPHEMERA.get());
        EphemeraItem.getColor1(bare);
        EphemeraItem.getScale(bare);
        h.assertTrue(!bare.hasTag() && EphemeraItem.getType(bare) == null, "只读不建 tag");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void tooltipLines(GameTestHelper h) {
        ItemStack stack = EphemeraItem.create(EphemeraType.ORBIT, "radiation", "Vesk Arru");
        EphemeraItem.setColors(stack, 0xAA3300, 0x22CCFF);
        List<Component> lines = EphemeraItem.tooltipLines(stack);
        h.assertTrue(lines.size() == 6, "款式、元素、来历、配色、描述五行，调过颜色再加一行：" + lines.size());
        translatable(h, lines.get(4), "item.kuvalich.ephemera.tuned");
        translatable(h, lines.get(5), "kuvalich.ephemera.desc.orbit");
        TextColor radiation = TextColor.fromRgb(KuvaPalette.element("radiation"));

        TranslatableContents style = translatable(h, lines.get(0), "item.kuvalich.ephemera.style");
        Component name = (Component) style.getArgs()[0];
        h.assertTrue(name.getContents() instanceof TranslatableContents t && "kuvalich.ephemera.name.orbit".equals(t.getKey()),
                "款式行写款名");
        h.assertTrue(radiation.equals(name.getStyle().getColor()), "款名按元素色");

        TranslatableContents elementLine = translatable(h, lines.get(1), "item.kuvalich.ephemera.element");
        Component element = (Component) elementLine.getArgs()[0];
        h.assertTrue(element.getContents() instanceof TranslatableContents t && "kuvaweapon.type.radiation".equals(t.getKey()),
                "元素行用元素译名");
        h.assertTrue(radiation.equals(element.getStyle().getColor()), "元素名按元素色");

        TranslatableContents origin = translatable(h, lines.get(2), "item.kuvalich.ephemera.origin");
        h.assertTrue("Vesk Arru".equals(((Component) origin.getArgs()[0]).getString()), "源自那只玄骸的名字");

        TranslatableContents colors = translatable(h, lines.get(3), "item.kuvalich.ephemera.colors");
        swatch(h, (Component) colors.getArgs()[0], 0xAA3300);
        swatch(h, (Component) colors.getArgs()[1], 0x22CCFF);

        List<Component> noOrigin = EphemeraItem.tooltipLines(EphemeraItem.create(EphemeraType.SURGE, "fire", ""));
        h.assertTrue(noOrigin.size() == 4, "没有来历（创造栏）就不写源自那行");
        List<Component> unknown = EphemeraItem.tooltipLines(new ItemStack(KuvaLichItems.EPHEMERA.get()));
        h.assertTrue(unknown.size() == 1, "款式认不出时只写一行");
        h.succeed();
    }

    private static TranslatableContents translatable(GameTestHelper h, Component line, String key) {
        h.assertTrue(line.getContents() instanceof TranslatableContents t && key.equals(t.getKey()),
                "应当是 " + key + "：" + line);
        return (TranslatableContents) line.getContents();
    }

    private static void swatch(GameTestHelper h, Component c, int rgb) {
        h.assertTrue(c.getContents() instanceof LiteralContents l && "■".equals(l.text())
                && TextColor.fromRgb(rgb).equals(c.getStyle().getColor()), "色块颜色应当是 " + Integer.toHexString(rgb));
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void ledgerSplitsStacks(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        PlayerLedger.addToInbox(p, List.of(new ItemStack(KuvaLichItems.KUVA.get(), 200)));
        List<ItemStack> inbox = PlayerLedger.getInbox(p);
        int total = inbox.stream().mapToInt(ItemStack::getCount).sum();
        h.assertTrue(total == 200 && inbox.size() == 4 && inbox.stream().allMatch(s -> s.getCount() <= 64),
                "200 个赤毒拆成 64/64/64/8：" + inbox);

        // 整份玩家数据存读一遍
        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        // 不进玩家列表：load 会把 UUID 改成和 p 一样
        ServerPlayer again = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        again.load(saved);
        h.assertTrue(PlayerLedger.getInbox(again).stream().mapToInt(ItemStack::getCount).sum() == 200,
                "存读后收件箱里的赤毒数量不变");

        Finale f = new Finale(3, 99L, "Vesk Arru", 4, 2, 30, "fire", "surge",
                List.of(new ItemStack(KuvaLichItems.KUVA.get(), 200)), List.of(new ItemStack(KuvaLichItems.ENDO.get(), 130)),
                "minecraft:overworld", 1.5D, 64.0D, -2.5D);
        PlayerLedger.setFinale(p, f);
        Finale read = PlayerLedger.getFinale(p);
        h.assertTrue(read != null && read.generation() == 3 && read.seed() == 99L && "surge".equals(read.ephemera())
                && read.drops().stream().mapToInt(ItemStack::getCount).sum() == 200
                && read.returns().stream().mapToInt(ItemStack::getCount).sum() == 130, "账的存读");
        PlayerLedger.setFinale(p, null);
        h.assertTrue(!PlayerLedger.hasFinale(p) && PlayerLedger.getFinale(p) == null, "清账");
        h.assertTrue(PlayerLedger.getActiveAlly(p) == -1 && PlayerLedger.getAllyCooldownUntil(p) == 0L
                && PlayerLedger.getAllies(p).isEmpty(), "缺键默认值");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void ledgerSurvivesRespawnAndEndReturn(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        card.beginLich("Vesk Arru", 515L, 0, 10, "poison", "minecraft:plains", "minecraft:overworld");
        fillLedger(p);

        ServerPlayer respawned = cloneOf(h, p, true);
        checkAll(h, respawned, "死亡重生");
        ServerPlayer fromEnd = cloneOf(h, respawned, false);
        checkAll(h, fromEnd, "从末地回来");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void rollbackThenUpgradeKeepsLedger(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraChance, 1.0D);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichEphemeraBackfill, true);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        card.beginLich("Vesk Arru", 515L, 0, 10, "poison", "minecraft:plains", "minecraft:overworld");
        String before = card.getLichEphemera();
        fillLedger(p);

        // 旧 jar 只写自己认识的键：能力里的两个新键没了，PlayerPersisted 原样
        CompoundTag oldCard = card.serializeNBT();
        oldCard.remove("lichEphemera");
        oldCard.remove("lichEphemeraRolled");
        card.deserializeNBT(oldCard);
        h.assertTrue(!card.isLichEphemeraRolled(), "回退后幻纹标记应当丢了");

        LichDirector.onLogin(p);
        h.assertTrue(card.isLichEphemeraRolled() && before.equals(card.getLichEphemera()),
                "再升级登录后应当补掷回同一个结果：" + before + " / " + card.getLichEphemera());
        // 登录时账上那条按消灭补落定了：40 个赤毒进了收件箱（登录后 10 秒内不投），其余照旧
        List<ItemStack> inbox = PlayerLedger.getInbox(p);
        h.assertTrue(!PlayerLedger.hasFinale(p)
                        && inbox.stream().filter(s -> s.is(KuvaLichItems.KUVA.get())).mapToInt(ItemStack::getCount).sum() == 240
                        && inbox.stream().filter(s -> s.is(KuvaLichItems.EPHEMERA.get())).count() == 1,
                "回退再升级：登录时应当补落定（赤毒 200 + 40，账上的幻纹 1）：" + inbox);
        h.assertTrue(PlayerLedger.getAllies(p).size() == 1 && PlayerLedger.getActiveAlly(p) == 1
                && PlayerLedger.getAllyCooldownUntil(p) == 123456L, "回退再升级：名册和冷却");
        h.succeed();
    }

    private static void fillLedger(ServerPlayer p) {
        PlayerLedger.addToInbox(p, List.of(new ItemStack(KuvaLichItems.KUVA.get(), 200)));
        PlayerLedger.setFinale(p, new Finale(1, 515L, "Vesk Arru", 2, 0, 10, "poison", "coil",
                List.of(new ItemStack(KuvaLichItems.KUVA.get(), 40)), List.of(), "minecraft:overworld", 0, 64, 0));
        CompoundTag ally = new CompoundTag();
        ally.putInt("gen", 1);
        ally.putString("name", "Vesk Arru");
        PlayerLedger.setAllies(p, List.of(ally));
        PlayerLedger.setActiveAlly(p, 1);
        PlayerLedger.setAllyCooldownUntil(p, 123456L);
    }

    private static void checkAll(GameTestHelper h, ServerPlayer p, String when) {
        RequiemCard card = card(p);
        h.assertTrue(card.hasLich() && EphemeraType.roll(515L, 1.0D).equals(card.getLichEphemera()) && card.isLichEphemeraRolled(),
                when + "：卡上的幻纹应当在");
        h.assertTrue(PlayerLedger.getInbox(p).stream().mapToInt(ItemStack::getCount).sum() == 200, when + "：收件箱");
        Finale f = PlayerLedger.getFinale(p);
        h.assertTrue(f != null && f.generation() == 1 && f.drops().stream().mapToInt(ItemStack::getCount).sum() == 40,
                when + "：账");
        h.assertTrue(PlayerLedger.getAllies(p).size() == 1 && PlayerLedger.getActiveAlly(p) == 1
                && PlayerLedger.getAllyCooldownUntil(p) == 123456L, when + "：名册和冷却");
    }

    /** 照原版重生 / 换维度的做法：新玩家对象 restoreFrom 旧的 */
    private static ServerPlayer cloneOf(GameTestHelper h, ServerPlayer old, boolean wasDeath) {
        ServerLevel level = h.getLevel();
        ServerPlayer fresh = new ServerPlayer(level.getServer(), level, old.getGameProfile());
        // 原版重生时新对象接手旧连接，Clone 事件里有处理器会给新玩家发包
        fresh.connection = old.connection;
        // restoreFrom 里 Forge 自己会发 Clone 事件，能力就是在那里抄过去的
        fresh.restoreFrom(old, wasDeath);
        return fresh;
    }

    private static RequiemCard card(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
    }

    @AfterBatch(batch = BATCH)
    public static void afterBatch(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
