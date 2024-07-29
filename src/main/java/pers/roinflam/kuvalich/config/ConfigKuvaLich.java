package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.kuvalich.utils.Reference;

@Config(modid = Reference.MOD_ID, category = "KuvaLich")
public class ConfigKuvaLich {
    @Config.Comment("Set the minimum number of progress points gained per kill.")
    @Config.RangeInt(min = 0)
    public static int minDecryptionProgress = 3;

    @Config.Comment("Set the maximum number of progress points gained per kill.")
    @Config.RangeInt(min = 0)
    public static int maxDecryptionProgress = 5;

    @Config.Comment("Set the number of points needed to decrypt the first stage.")
    @Config.RangeInt(min = 1)
    public static int firstStage = 60;

    @Config.Comment("Set the number of points needed to decrypt the second stage.")
    @Config.RangeInt(min = 1)
    public static int secondStage = 84;

    @Config.Comment("Set the number of points needed to decrypt the third stage.")
    @Config.RangeInt(min = 1)
    public static int thirdStage = 120;

    @Config.Comment("Set the extra point multiplier for killing the Kuva Master.")
    @Config.RangeDouble(min = 1)
    public static double masterPotionMultiplier = 5;

    @Config.Comment("Set whether to display all damage.")
    public static boolean damageDisplay = false;

    @Config.Comment("Set the priority of damage modification. If it is true, the priority will be the last modification.")
    public static boolean damagePriority = true;

    @Config.Comment("Set damage boost per second for both Kuva Slave and Kuva Master in battle.")
    @Config.RangeDouble(min = 0)
    public static float battleBoost = 0.075f;

    @Config.Comment("Set the reduced damage dealt to Kuva Slave and Kuva Master after each decryption failure. (99% damage reduction max)")
    @Config.RangeDouble(min = 0)
    public static float reducedDamage = 0.1f;

    @Config.Comment("Set the damage inflicted by the Kuva Slave and Kuva Master on the player to increase after each failed decryption.")
    @Config.RangeDouble(min = 0)
    public static float increaseDamage = 0.25f;

    @Config.Comment("Set the base level of all weapons. When the weapon level is this value, the value is the original attribute performance, etc. If it is lower than it can be judged as inferior equipment, if it is higher than it is excellent equipment.")
    @Config.RangeInt(min = 0)
    public static int benchmarkLevel = 45;

    @Config.Comment("Set the minimum obtainable weapon level when the Requiem Gate is not upgraded.")
    @Config.RangeInt(min = 0)
    public static int baseMinimumLevel = 5;

    @Config.Comment("Set the maximum weapon level that can be obtained when the Requiem Gate is not upgraded.")
    @Config.RangeInt(min = 0)
    public static int baseMaximumLevel = 25;

    @Config.Comment("Set the minimum upgrade level in the upper limit of the next weapon level after each successful decryption and upgrade of the Requiem Gate.")
    @Config.RangeInt(min = 0)
    public static int minimumLevelCapIncrease = 3;

    @Config.Comment("Set the maximum upgrade level in the upper limit of the next weapon level after each successful decryption and upgrade of the Requiem Gate.")
    @Config.RangeInt(min = 0)
    public static int maximumLevelCapIncrease = 6;

    @Config.Comment("Set the minimum obtainable weapon level after reaching the upper limit after multiple upgrades.")
    @Config.RangeInt(min = 0)
    public static int minimumLevel = 25;

    @Config.Comment("Set the maximum obtainable weapon level after reaching the upper limit after multiple upgrades.")
    @Config.RangeInt(min = 0)
    public static int maximumLevel = 60;

    @Config.Comment("Setting the boost multiplier each time a Requiem Evolve is used to upgrade a weapon.")
    @Config.RangeInt(min = 0)
    public static double upgradeMultiplier = 0.1;

    @Config.Comment("Setting a numerical limit for weapon upgrades via Requiem Evolve.")
    @Config.RangeInt(min = 0)
    public static int upgradeLimit = 999;

    @Config.Comment("Set the spawn weight for Kuva Lich")
    @Config.RangeInt(min = 1)
    public static int kuvaLichSpawnWeight = 5;

    @Config.Comment("Set the minimum spawn count for Kuva Lich")
    @Config.RangeInt(min = 1)
    public static int kuvaLichMinSpawnCount = 1;

    @Config.Comment("Set the maximum spawn count for Kuva Lich")
    @Config.RangeInt(min = 1)
    public static int kuvaLichMaxSpawnCount = 1;

    @Config.Comment("Set the spawn weight for Kuva Slave")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveSpawnWeight = 10;

    @Config.Comment("Set the minimum spawn count for Kuva Slave")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveMinSpawnCount = 1;

    @Config.Comment("Set the maximum spawn count for Kuva Slave")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveMaxSpawnCount = 3;
}