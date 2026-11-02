package pers.roinflam.kuvalich.nemesis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;

import java.util.List;

/**
 * 玄骸档案的服务端：收到打开档案的请求后，把这个玩家自己的档案数据组好发回去
 *
 * <p>请求的限流（每人 500 ms 一次）在请求包里已经做了，这里只管组包发送。数值都按服务端当前的配置算好再发，
 * 客户端只管显示。档案里那句嘲讽是客户端自己抽、自己放的（只有本人看得到，服务端不用知道）。</p>
 */
public final class LichArchiveService {

    /** 怒气来源的位：处决奴仆、解密进度、揭示密语、在线时间 */
    public static final int RAGE_FROM_THRALLS = 1;
    public static final int RAGE_FROM_PROGRESS = 2;
    public static final int RAGE_FROM_REVEAL = 4;
    public static final int RAGE_FROM_TIME = 8;

    private LichArchiveService() {
    }

    /** 回给请求者一份他自己的档案 */
    public static void handleRequest(ServerPlayer player) {
        LichArchivePacket.Data data = build(player);
        if (data != null) {
            LichArchivePacket.send(player, new LichArchivePacket(data));
        }
    }

    /** 组一份这个玩家的档案；拿不到他的卡（极少见，比如刚死还没重生的那一刻）返回 null */
    public static LichArchivePacket.Data build(ServerPlayer player) {
        RequiemCard card = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (card == null) {
            return null;
        }
        boolean hasLich = card.hasLich();

        ItemStack weapon = ItemStack.EMPTY;
        int weaponIndex = card.getLichWeapon();
        if (hasLich && weaponIndex >= 0 && weaponIndex < KuvaLichItems.KUVA_WEAPONS.size()) {
            Item item = KuvaLichItems.KUVA_WEAPONS.get(weaponIndex);
            weapon = new ItemStack(item);
        }

        int kuvaLevel = card.getKuvaLevel();
        int dealt = (int) Math.round(kuvaLevel * ModConfig.KUVA_LICH.reducedDamage.get() * 100.0D);
        int taken = (int) Math.round(kuvaLevel * ModConfig.KUVA_LICH.increaseDamage.get() * 100.0D);

        int sources = 0;
        if (ModConfig.KUVA_LICH.lichRagePerThrallExecution.get() > 0.0D) {
            sources |= RAGE_FROM_THRALLS;
        }
        if (ModConfig.KUVA_LICH.lichRagePerProgressPoint.get() > 0.0D) {
            sources |= RAGE_FROM_PROGRESS;
        }
        if (ModConfig.KUVA_LICH.lichRagePerClueRevealed.get() > 0.0D) {
            sources |= RAGE_FROM_REVEAL;
        }
        if (ModConfig.KUVA_LICH.lichRageOnlineMinutes.get() > 0.0D) {
            sources |= RAGE_FROM_TIME;
        }

        int[] riddles = {card.getOneRiddle(), card.getTwoRiddle(), card.getThreeRiddle()};
        LichArchivePacket.Card[] cards = {slot(card.getOneCard()), slot(card.getTwoCard()), slot(card.getThreeCard())};

        List<ItemStack> confiscated = card.getConfiscatedItems();
        if (confiscated.size() > LichArchivePacket.MAX_CONFISCATED) {
            confiscated = confiscated.subList(0, LichArchivePacket.MAX_CONFISCATED);
        }

        RequiemCard.HistoryEntry last = card.getLastHistory();

        return new LichArchivePacket.Data(state(player, card),
                hasLich ? card.getLichBirthBiome() : "", hasLich ? card.getLichBirthDim() : "",
                weapon, card.getMinimumLevelWeapon(), card.getMaximumLevelWeapon(),
                hasLich ? stats(player, card) : LichArchivePacket.Stats.NONE, dealt, taken, sources, hasLich ? card.getLichEncounters() : 0,
                card.getUnlockedCardStatus(), card.getDecryptionProgress(), card.getPointsRequired(),
                riddles, cards, card.getAttempts(),
                card.getConfiscatedItemCount(), confiscated,
                last == null ? "" : last.name(), last == null ? 0 : last.fate());
    }

    /**
     * 这只玄骸的战斗面板，按卡上的种子、等级现算（和实体那边 {@link LichTraits#current} 同一个函数）。
     * 「一刀」按主人所在世界的难度，再乘主人身上的等级加成，不含交战增幅
     */
    static LichArchivePacket.Stats stats(ServerPlayer player, RequiemCard card) {
        LichTraits traits = LichTraits.current(card.getLichSeed(), card.getLichRank());
        double taken = 1.0D + card.getKuvaLevel() * ModConfig.KUVA_LICH.increaseDamage.get();
        double hit = KuvaMasterEntity.hitDamage(traits.attack, player.level().getDifficulty()) * taken;
        double fury = ModConfig.KUVA_LICH.battleBoost.get() * traits.furyScale();
        return new LichArchivePacket.Stats(traits.affinityPercents(), traits.adaptPercent(), traits.adaptStacks(),
                (int) Math.round(fury * 1000.0D), (int) Math.round(traits.maxHealth),
                (int) Math.round(traits.armor * 10.0D), (int) Math.round(hit * 10.0D), traits.packedTiers(),
                (int) Math.round(ModConfig.KUVA_LICH.lichRankHealthStep.get() * 100.0D),
                (int) Math.round(ModConfig.KUVA_LICH.increaseDamage.get() * 100.0D));
    }

    /** 和状态包同一套内容，按此刻现算（不走状态包的限流） */
    private static LichStatePacket state(ServerPlayer player, RequiemCard card) {
        if (!card.hasLich()) {
            return new LichStatePacket(LichState.UNBORN, WaitReason.NONE, "", 0, -1, 0, 0, 0,
                    card.getUnlockedCardStatus(), 0, card.getLichGeneration(), card.isLichPaused());
        }
        LichState state = LichDirector.stateOf(player);
        WaitReason wait = WaitReason.NONE;
        if (state == LichState.INCOMING) {
            LichDirector.HuntView view = LichDirector.view(player);
            if (view != null) {
                wait = view.waitReason();
            }
        }
        long now = player.server.overworld().getGameTime();
        long left = card.getLichCooldownUntil() - now;
        int cooldownSec = left > 0 ? (int) Math.min(Integer.MAX_VALUE, (left + 19) / 20) : 0;
        return new LichStatePacket(state, wait, card.getLichName(), (int) card.getLichSeed(), card.getLichWeapon(),
                card.getLichRank(), Math.round(card.getLichRage() * 1000.0F), card.getRageTier(),
                card.getUnlockedCardStatus(), cooldownSec, card.getLichGeneration(), card.isLichPaused());
    }

    private static LichArchivePacket.Card slot(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof AbstractRequiemCard requiem)) {
            return LichArchivePacket.Card.EMPTY;
        }
        return new LichArchivePacket.Card(requiem.getID(), stack.getDamageValue(), stack.getMaxDamage());
    }
}
