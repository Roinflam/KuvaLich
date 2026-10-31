package pers.roinflam.kuvalich.fx;

import javax.annotation.Nullable;

/**
 * 音画效果包的编号（{@link pers.roinflam.kuvalich.network.packet.KuvaFxPacket} 的 {@code fx} 字段）
 *
 * <p>编号稳定、只追加不改。通用代码，不碰任何客户端类。每个编号的参数含义（entity 一栏存的是实体 id+1，0 表示没有）：</p>
 * <pre>
 *  id  名字             entity               a                                       b
 *   1  LICH_SUMMON      玄骸                 召唤者 id+1                             0
 *   2  REQUIEM_SUCCESS  玄骸                 击杀者 id+1                             bit0 怜悯之击后 / bit1 有没收物品归还 / bit2~3 用完消失的安魂卡张数 / bit8~19 三格的卡 id（每格 4 位，第 0 格在 bit8~11，15 = 不知道）
 *   3  REQUIEM_FAIL     玄骸                 击杀者 id+1                             bit0~1 猜对的前几位 / bit2 怜悯之击后 / bit8~19 三格的卡 id（同上）
 *   4  LICH_DEATH       玄骸                 击杀者 id+1（0=非玩家）                 0 非玩家击杀 / 1 卡没放齐 / 2 怜悯之击成功收尾
 *   5  THRALL_DEATH     奴仆                 击杀者 id+1                             bit0 这次给了密语进度 / bit1 怜悯之击之后
 *   6  CONFISCATE       无                   被没收的玩家 id+1                       物品稀有度序号
 *   7  CRIT             受击者               档位 1/2/3                              攻击者 id+1
 *   8  ELEMENT_PROC     受击者               元素序号 | 等级（层数 − 1）&lt;&lt; 8 | extra &lt;&lt; 16（extra：电击 = 麻痹 tick，切割 = 流血轮数，其余 0）   攻击者 id+1
 *   9  GAS_CLOUD        无（原点=云心）      持续 tick                               攻击者 id+1
 *  10  EXECUTE          受击者               0 收割 / 1 斩杀                         攻击者 id+1
 *  11  LAST_STAND       玩家                 0                                       0
 *  12  MODULE_REVEAL    玩家                 稀有度 0 普通 / 1 罕见 / 2 稀有 / 3 Prime / 4 紫卡   0
 *  13  LICH_COMM        （退役）             —                                       —          不再发，客户端空分支
 *  14  SPLASH           主目标               半径 x10                                攻击者 id+1
 *  15  PURGE            受击者               0                                       攻击者 id+1
 *  16  LICH_DOWNED      倒地的目标           资格人 id+1（0=没有）                   bit0~15 剩余 tick / bit16 处决被打断后续上（客户端要掐掉正在播的演出）
 *  17  MERCY_BEGIN      目标（坐标=处决者脚底站位）  处决者 id+1                     计划位：bit0~1 刀数 / bit2~3 前缀长度 / bit4~7 套路 / bit8~19 三张卡 / bit20~25 各卡处决前剩余次数 / bit26 有没收物品归还 / bit27~28 用完消失的卡数 / bit29 演示
 *  18  MERCY_STAB       （保留不用）         —                                       —          编号不回收，客户端空分支
 *  19  LICH_EXPIRE      目标                 0 奴仆倒地超时消散                      消散 tick 数
 *  20  WEAPON_DASH      玩家                 武器 0 旗帜 / 1 收割者·Prime            命中个数
 *  21  RIDDLE_SOLVED    无                   0                                       0
 *  22  LICH_ARRIVE      玄骸                 0 到场 / 1 重定位                       0
 *  23  LICH_DEPART      玄骸                 离场原因（EncounterEnd 序号）/ 99 重定位  0
 *  24  KUVA_STATE       目标（坐标=命中点）  种类 | 参数 1 &lt;&lt; 8（0 适应 / 1 挡住 / 2 命中反馈 / 3 弱点）  参数 2（多为攻击者 id+1）
 *  25  RANK_UP          玄骸（可为 0）       新的玄骸等级                            bit0 来自怜悯之击 / bit1~2 判错的是第几刀
 *  99  DEV_SHOWCASE     无                   展示项序号                              0   （只由开发环境的调试命令发）
 * </pre>
 * <p>元素序号（{@code a & 0xFF}）：0 fire / 1 ice / 2 poison / 3 electricity / 4 slash / 5 puncture / 6 impact /
 * 7 gas / 8 radiation / 9 magnetic / 10 corrosion / 11 explosion / 12 virus。</p>
 */
public enum FxId {
    LICH_SUMMON(1),
    REQUIEM_SUCCESS(2),
    REQUIEM_FAIL(3),
    LICH_DEATH(4),
    THRALL_DEATH(5),
    CONFISCATE(6),
    CRIT(7),
    ELEMENT_PROC(8),
    GAS_CLOUD(9),
    EXECUTE(10),
    LAST_STAND(11),
    MODULE_REVEAL(12),
    LICH_COMM(13),
    SPLASH(14),
    PURGE(15),
    LICH_DOWNED(16),
    MERCY_BEGIN(17),
    MERCY_STAB(18),
    LICH_EXPIRE(19),
    WEAPON_DASH(20),
    RIDDLE_SOLVED(21),
    LICH_ARRIVE(22),
    LICH_DEPART(23),
    KUVA_STATE(24),
    RANK_UP(25),
    /** 只在开发环境由调试命令发，用来单独看特效引擎 */
    DEV_SHOWCASE(99);

    /** 线上的稳定编号 */
    public final int id;

    FxId(int id) {
        this.id = id;
    }

    private static final FxId[] BY_ID = new FxId[100];

    static {
        for (FxId f : values()) {
            BY_ID[f.id] = f;
        }
    }

    /** 按编号取；不认识的编号返回 null（客户端收到后来追加的编号时静默忽略） */
    @Nullable
    public static FxId byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }
}
