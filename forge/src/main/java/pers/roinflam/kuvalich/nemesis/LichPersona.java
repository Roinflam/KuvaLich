package pers.roinflam.kuvalich.nemesis;

/**
 * 玄骸的性格，决定它说哪一套台词
 *
 * <ul>
 *   <li>{@link #JOVIAL 活泼}：管你叫「朋友」，爱炫耀、爱演、输了也嘴上不饶人，像在陪你玩一场它稳赢的游戏；</li>
 *   <li>{@link #LOGICAL 理性}：管你叫「对手」，说话短、像下判决，把你当一道算题，挨了刀也只是「误差」。</li>
 * </ul>
 *
 * <p>由玄骸种子的低 32 位算出来，不单独存盘：老存档里的玄骸照样有性格，服务端（实体、档案卡）和客户端
 * （档案界面只拿到低 32 位）算出来一样。外观也从这个种子取位，所以先乘一个奇数把各位打散再取最高位，
 * 免得性格和某种外观绑在一起。</p>
 */
public enum LichPersona {
    JOVIAL("jovial"),
    LOGICAL("logical");

    /** 语言键里的那一段：{@code kuvalich.comm.lich.<id>.<场合>.<序号>} */
    public final String id;

    LichPersona(String id) {
        this.id = id;
    }

    public static LichPersona of(int seed) {
        return seed * 0x9E3779B9 < 0 ? LOGICAL : JOVIAL;
    }
}
