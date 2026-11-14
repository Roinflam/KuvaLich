package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 一场终结定案后的账：定案时把这只玄骸的身份和要发的东西全部定好，落定时整条取走。
 *
 * @param drops   定案时掷好的击杀奖励
 * @param returns 被没收、要还给玩家的东西
 */
public record Finale(int generation, long seed, String name, int rank, int weapon, int weaponLevel, String weaponType,
                     String ephemera, List<ItemStack> drops, List<ItemStack> returns, String dim, double x, double y,
                     double z) {

    public Finale {
        name = name == null ? "" : name;
        weaponType = weaponType == null ? "" : weaponType;
        ephemera = ephemera == null ? "" : ephemera;
        dim = dim == null ? "" : dim;
        drops = PlayerLedger.splitStacks(drops == null ? List.of() : drops);
        returns = PlayerLedger.splitStacks(returns == null ? List.of() : returns);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("gen", generation);
        tag.putLong("seed", seed);
        tag.putString("name", name);
        tag.putInt("rank", rank);
        tag.putInt("weapon", weapon);
        tag.putInt("weaponLevel", weaponLevel);
        tag.putString("weaponType", weaponType);
        tag.putString("ephemera", ephemera);
        tag.put("drops", PlayerLedger.writeItems(drops));
        tag.put("returns", PlayerLedger.writeItems(returns));
        tag.putString("dim", dim);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }

    public static Finale load(CompoundTag tag) {
        String what = "终结账（第 " + tag.getInt("gen") + " 代 " + tag.getString("name") + "）";
        return new Finale(tag.getInt("gen"), tag.getLong("seed"), tag.getString("name"), tag.getInt("rank"),
                tag.contains("weapon", Tag.TAG_ANY_NUMERIC) ? tag.getInt("weapon") : -1, tag.getInt("weaponLevel"),
                tag.getString("weaponType"), tag.getString("ephemera"),
                PlayerLedger.readItems(tag.getList("drops", Tag.TAG_COMPOUND), what + "的掉落"),
                PlayerLedger.readItems(tag.getList("returns", Tag.TAG_COMPOUND), what + "的归还"),
                tag.getString("dim"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}
