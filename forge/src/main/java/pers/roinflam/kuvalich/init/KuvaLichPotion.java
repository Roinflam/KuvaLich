package pers.roinflam.kuvalich.init;

import net.minecraft.potion.Potion;
import pers.roinflam.kuvalich.potion.hide.*;

import java.util.ArrayList;
import java.util.List;

public class KuvaLichPotion {
    public static final List<Potion> POTIONS = new ArrayList<Potion>();

    public static final MobEffectFire FIRE = new MobEffectFire(true, 0xff2e48);
    public static final MobEffectIce ICE = new MobEffectIce(true, 0x2879c9);
    public static final MobEffectPoison POISON = new MobEffectPoison(true, 0x009921);
    public static final MobEffectGuandaoPrime GUANDAO_PRIME = new MobEffectGuandaoPrime(true, 0);
    public static final MobEffectArcaTitron ARCA_TITRON = new MobEffectArcaTitron(true, 0x00b3ff);

    public static final MobEffectVitrica VITRICA = new MobEffectVitrica(true, 0xd19100);
    public static final MobEffectMagnetic MAGNETIC = new MobEffectMagnetic(true, 0x6514ff);
    public static final MobEffectPuncture PUNCTURE = new MobEffectPuncture(true, 0xc2c2c2);
    public static final MobEffectCorrosion CORROSION = new MobEffectCorrosion(true, 0x008700);
    public static final MobEffectVirus VIRUS = new MobEffectVirus(true, 0xae1553);

    public static final MobEffectAttackSpeed ATTACK_SPEED = new MobEffectAttackSpeed(false, 0);
    public static final MobEffectNegativeAttackSpeed NEGATIVE_ATTACK_SPEED = new MobEffectNegativeAttackSpeed(true, 0);
    public static final MobEffectHealth HEALTH = new MobEffectHealth(false, 0);
    public static final MobEffectNegativeHealth NEGATIVE_HEALTH = new MobEffectNegativeHealth(true, 0);
    public static final MobEffectArmor ARMOR = new MobEffectArmor(false, 0);
    public static final MobEffectNegativeArmor NEGATIVE_ARMOR = new MobEffectNegativeArmor(true, 0);
    public static final MobEffectMovementSpeed MOVEMENT_SPEED = new MobEffectMovementSpeed(false, 0);
    public static final MobEffectNegativeMovementSpeed NEGATIVE_MOVEMENT_SPEED = new MobEffectNegativeMovementSpeed(true, 0);
    public static final MobEffectKnockbackResistance KNOCKBACK_RESISTANCE = new MobEffectKnockbackResistance(false, 0);
    public static final MobEffectNegativeKnockbackResistance NEGATIVE_KNOCKBACK_RESISTANCE = new MobEffectNegativeKnockbackResistance(true, 0);
    public static final MobEffectReachDistance REACH_DISTANCE = new MobEffectReachDistance(false, 0);
    public static final MobEffectNegativeReachDistance NEGATIVE_REACH_DISTANCE = new MobEffectNegativeReachDistance(true, 0);
}