package pers.roinflam.kuvalich.nemesis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
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

    /** 适应抗性：每层减伤百分比、满层减伤百分比（实体那边是每层 5%、至多 6 层） */
    static final int ADAPT_PCT_PER_STACK = 5;
    static final int ADAPT_PCT_MAX = 30;

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
        int fury = (int) Math.round(ModConfig.KUVA_LICH.battleBoost.get() * 1000.0D);

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
                LichProfile.affinities(card.getLichSeed()), ADAPT_PCT_PER_STACK, ADAPT_PCT_MAX, fury,
                dealt, taken, sources, hasLich ? card.getLichEncounters() : 0,
                card.getUnlockedCardStatus(), card.getDecryptionProgress(), card.getPointsRequired(),
                riddles, cards, card.getAttempts(),
                card.getConfiscatedItemCount(), confiscated,
                last == null ? "" : last.name(), last == null ? 0 : last.fate());
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
