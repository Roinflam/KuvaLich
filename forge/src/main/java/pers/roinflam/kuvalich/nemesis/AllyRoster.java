package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.kuvalich.config.ModConfig;

import java.util.List;

/**
 * 盟友名册（{@link PlayerLedger#getAllies}）的条目格式和追加规则。感化落定时追加一条；
 * 满了挤掉最早感化的那只，出战的那只排在最后挤。上限只在追加时生效，读档、改配置不截。
 */
public final class AllyRoster {

    public static final String SEED = "seed";
    public static final String NAME = "name";
    public static final String GENERATION = "gen";
    public static final String RANK = "rank";
    public static final String WEAPON = "weapon";
    public static final String WEAPON_LEVEL = "weaponLevel";
    public static final String WEAPON_TYPE = "weaponType";
    public static final String EPHEMERA = "ephemera";
    public static final String CONVERTED_AT = "convertedAt";

    private AllyRoster() {
    }

    public static int capacity() {
        try {
            return ModConfig.KUVA_LICH.allyRosterSize.get();
        } catch (RuntimeException e) {
            return 3;
        }
    }

    public static CompoundTag entryOf(Finale f, long convertedAt) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(SEED, f.seed());
        tag.putString(NAME, f.name());
        tag.putInt(GENERATION, f.generation());
        tag.putInt(RANK, f.rank());
        tag.putInt(WEAPON, f.weapon());
        tag.putInt(WEAPON_LEVEL, f.weaponLevel());
        tag.putString(WEAPON_TYPE, f.weaponType());
        tag.putString(EPHEMERA, f.ephemera());
        tag.putLong(CONVERTED_AT, convertedAt);
        return tag;
    }

    /** 再追加一只时会被挤掉的那只的名字；不会挤掉返回空串 */
    public static String wouldRelease(Player player) {
        List<CompoundTag> allies = PlayerLedger.getAllies(player);
        if (allies.size() < capacity()) {
            return "";
        }
        int i = releaseIndex(allies, PlayerLedger.getActiveAlly(player));
        return i < 0 ? "" : allies.get(i).getString(NAME);
    }

    /**
     * 追加一条，超出上限的按规则挤掉。同一代已经在名册里的不重复加
     *
     * @return 被挤掉的那只的名字，没有挤掉返回空串（一次超出不止一只时返回最后挤掉的）
     */
    public static String add(Player player, CompoundTag entry) {
        List<CompoundTag> allies = PlayerLedger.getAllies(player);
        int gen = entry.getInt(GENERATION);
        for (CompoundTag a : allies) {
            if (a.getInt(GENERATION) == gen) {
                return "";
            }
        }
        allies.add(entry.copy());
        int active = PlayerLedger.getActiveAlly(player);
        String released = "";
        int cap = capacity();
        while (allies.size() > cap) {
            int i = releaseIndex(allies.subList(0, allies.size() - 1), active);
            if (i < 0) {
                break;
            }
            CompoundTag gone = allies.remove(i);
            released = gone.getString(NAME);
            if (gone.getInt(GENERATION) == active) {
                active = -1;
                PlayerLedger.setActiveAlly(player, -1);
            }
            AllyDirector.onReleased(player.getUUID(), gone.getInt(GENERATION));
        }
        PlayerLedger.setAllies(player, allies);
        return released;
    }

    /**
     * 遣散：删掉这一代，正在场上就马上退去；出战的指向它就改回默认（最新感化的那只）。
     * 被挤出名册走的也是同样的处理（见 {@link #add}）
     *
     * @return 名册里原来有这一代
     */
    public static boolean release(Player player, int generation) {
        List<CompoundTag> allies = PlayerLedger.getAllies(player);
        if (!allies.removeIf(a -> a.getInt(GENERATION) == generation)) {
            return false;
        }
        PlayerLedger.setAllies(player, allies);
        if (PlayerLedger.getActiveAlly(player) == generation) {
            PlayerLedger.setActiveAlly(player, -1);
        }
        AllyDirector.onReleased(player.getUUID(), generation);
        return true;
    }

    // 名册按感化先后排，最早的在前；出战的那只放到最后才挤
    private static int releaseIndex(List<CompoundTag> allies, int activeGeneration) {
        for (int i = 0; i < allies.size(); i++) {
            if (allies.get(i).getInt(GENERATION) != activeGeneration) {
                return i;
            }
        }
        return allies.isEmpty() ? -1 : 0;
    }
}
