package pers.roinflam.kuvalich.nemesis;

/**
 * 怒气的五个档位。只影响显示和随机嘲讽的概率，不影响玄骸本身的强度
 */
public enum RageTier {
    /** 冷漠 */
    INDIFFERENT(0.0D),
    /** 恼怒 */
    IRRITATED(0.12D),
    /** 气得冒烟 */
    SEETHING(0.50D),
    /** 狂怒 */
    ENRAGED(0.80D),
    /** 暴怒 */
    LIVID(0.96D);

    /** 这一档的怒气下限（0~1） */
    public final double floor;

    RageTier(double floor) {
        this.floor = floor;
    }

    /** 名字的语言键，如 {@code kuvalich.lich.rage.2} */
    public String langKey() {
        return "kuvalich.lich.rage." + ordinal();
    }

    /** 按怒气取档：从高往低找第一个下限不超过它的 */
    public static RageTier of(double rage) {
        RageTier[] all = values();
        for (int i = all.length - 1; i > 0; i--) {
            if (rage >= all[i].floor) {
                return all[i];
            }
        }
        return INDIFFERENT;
    }
}
