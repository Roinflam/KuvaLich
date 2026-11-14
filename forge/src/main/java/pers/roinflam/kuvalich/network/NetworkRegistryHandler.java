package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.kuvalich.network.packet.CodexGiveItemPacket;
import pers.roinflam.kuvalich.network.packet.CodexInstallModulePacket;
import pers.roinflam.kuvalich.network.packet.CodexActionResultPacket;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.packet.GuideConfigSyncPacket;
import pers.roinflam.kuvalich.network.packet.KuvaCommPacket;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichArchiveRequestPacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.network.packet.MercyChoiceOpenPacket;
import pers.roinflam.kuvalich.network.packet.MercyChoicePacket;
import pers.roinflam.kuvalich.network.packet.MercyRequestPacket;
import pers.roinflam.kuvalich.network.packet.ModuleDiscoveryPacket;
import pers.roinflam.kuvalich.network.packet.NoticePacket;
import pers.roinflam.kuvalich.network.packet.RequiemGateFillPacket;
import pers.roinflam.kuvalich.network.packet.RivenCyclePacket;
import pers.roinflam.kuvalich.network.packet.RivenCycleStatePacket;
import pers.roinflam.kuvalich.network.packet.WarframeModuleSyncPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Optional;

/**
 * 网络注册处理器
 * Network registry handler
 *
 * 统一管理所有网络包的注册
 *
 * <p>协议版本现为 "17"（各版本改了什么见 {@link #PROTOCOL_VERSION}）。"7" 时 {@link DamagePacket} 改为传拆开的字段
 * （伤害数字合并）、{@link WarframeModuleSyncPacket} 追加了武器类击杀叠层数组（客户端射速 / 多重射击与服务端对齐），
 * 字节流格式发生变化，与 "6" 及更早版本的客户端不兼容，必须提版本号强制握手拒绝，
 * 否则旧客户端会按老格式解码出错位数据。"8" 新增 {@link GuideConfigSyncPacket}（冒险指南的服务端配置快照），
 * "9" 新增 {@link KuvaFxPacket}（音画效果包），"10" 新增专属玄骸、档案、通讯、怜悯之击的五个包，
 * "11" 处决计划位新增四个套路编号和绕背方向位，"12" 档案包的玄骸属性改成整套战斗面板，"13" 玄骸血条分段、护盾，"14" 新增 {@link NoticePacket}（玩家提示条），"15" 解密 HUD 破解包带每张卡剩几次，"16" 新增安魂之融裂罅循环的请求包和状态包，"17" 档案包带上全部被没收的物品。</p>
 *
 * <p>安全：所有包在注册时都显式声明 {@link NetworkDirection}。
 * 不带方向的 5 参 {@code registerMessage} 重载会让 Forge 允许包双向收发，
 * 意味着改装客户端可以把任意 S2C 包发给服务端并触发服务端 {@code decode}。
 * 声明方向后，Forge 会在解码之前就拒绝方向不符的包。</p>
 */
public class NetworkRegistryHandler {

    /** 网络通道 / Network channel */
    private static SimpleChannel INSTANCE;

    /**
     * 网络协议版本
     * "6"：DamagePacket 改为传拆开的字段（伤害数字合并），字节流格式变更
     * "7"：WarframeModuleSyncPacket 追加武器类击杀叠层数组，字节流格式变更
     * "8"：新增 GuideConfigSyncPacket（冒险指南的服务端配置快照）。旧客户端收到不认识的包 id
     *         并不会断线（Forge 47 的 IndexedMessageCodec 只记一条 error 就丢弃；旧客户端本来也没有这套指南）；
     *         提版本是为了让旧客户端在握手阶段就被拒绝、看到明确的「版本不符」提示，强制所有人更新 jar。
     *         所以「新增 S2C 包」本身不是必须提版本的理由，改了已有包的字节格式才是
     * "9"：新增 KuvaFxPacket（音画效果包：玄骸 / 安魂 / 暴击 / 元素等演出的统一入口）。
     *         客户端要靠它放声音和特效，没有它的旧客户端会少掉整套音画，所以同样提版本强制更新
     * "10"：新增专属玄骸、档案、通讯、怜悯之击的包（LichStatePacket、LichArchiveRequestPacket、LichArchivePacket、
     *         KuvaCommPacket、MercyRequestPacket），DecryptionHudPacket 末尾追加揭示卡号。
     *         DecryptionHudPacket 的字节格式变了，旧客户端按老格式解码会错位，所以必须提版本
     * "11"：处决计划位新增四个套路编号和绕背方向位。字节长度没变，但旧客户端不认识新编号，
     *         会把新套路当成第一套演，和服务端的时间表、目标动作对不上，所以提版本强制更新
     * "12"：LichArchivePacket 的弱点抗性、适应、增幅几项换成整套战斗面板（四类倍率、生命、护甲、一刀、技能档位），
     *         字节格式变了，旧客户端解码会错位
     * "13"：玄骸血条分三段、加护盾：计划位的刀数改成 1~3（第 k 次处决捅 k 刀），怜悯之击多了「起身中」状态，
     *         玄骸多了护盾、段数的同步数据。旧客户端会把少刀数的处决演成三刀、实体同步数据也对不上，所以提版本
     * "14"：新增 NoticePacket（玩家提示改走提示条，聊天 / 动作栏退成客户端的备用显示）。
     *         服务端不再直接发聊天 / 动作栏，旧客户端不认识这个包，所有提示都会丢，所以提版本强制更新
     * "15"：DecryptionHudPacket 的破解（EVENT_COMPLETE）在名字后面追加三张卡扣完剩几次，
     *         字节格式变了，旧客户端解码会少读三个字节，所以提版本
     * "16"：新增 RivenCyclePacket（安魂之融裂罅循环的循环 / 挑选 / 都不要 / 换上请求）和 RivenCycleStatePacket（候选卡和结果码）。
     *         服务端不再在放进裂罅 + 赤毒时自动洗卡，旧客户端没有「循环」按钮、也不认识候选包，洗不了卡，所以提版本
     * "17"：LichArchivePacket 的没收物品从最多 54 件改成整份（最多 1024 件，另有字节预算），档案里能查看全部。
     *         旧客户端解码时按 54 截断，多出来的物品会被当成后面的字段读，整包错位，所以提版本
     * "17-dev"：开发中的版本，发布时改回正式的号。处决、感化这一批会给实体加同步数据，开发中途的 jar 混用时
     *         数据序号会错位而握手照过，用单独的串让握手直接拦下。
     *         这一段新增：AbstractKuva 的 DATA_MERCY_CHOICE、计划位 bit 31、MercyChoicePacket、MercyChoiceOpenPacket
     */
    private static final String PROTOCOL_VERSION = "17-dev";

    /** 消息ID计数器 / Message ID counter */
    private static int messageId = 0;

    /** 服务端 → 客户端 方向常量（避免每次注册重复装箱） */
    private static final Optional<NetworkDirection> TO_CLIENT =
            Optional.of(NetworkDirection.PLAY_TO_CLIENT);

    /** 客户端 → 服务端 方向常量 */
    private static final Optional<NetworkDirection> TO_SERVER =
            Optional.of(NetworkDirection.PLAY_TO_SERVER);

    /**
     * 注册网络通道和消息包
     * Register network channel and packets
     */
    public static void register() {
        LogUtil.info("开始注册网络通道...");

        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Reference.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        registerMessages();

        LogUtil.info("网络通道注册成功,共注册 " + messageId + " 个消息包");
    }

    /**
     * 注册所有消息包
     * Register all packets
     */
    private static void registerMessages() {
        // 伤害显示包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                DamagePacket.class,
                DamagePacket::encode,
                DamagePacket::decode,
                DamagePacket::handle,
                TO_CLIENT
        );

        // 模组发现记录同步包（服务器 → 客户端，支持全量/增量两种模式）
        INSTANCE.registerMessage(
                nextMessageId(),
                ModuleDiscoveryPacket.class,
                ModuleDiscoveryPacket::encode,
                ModuleDiscoveryPacket::decode,
                ModuleDiscoveryPacket::handle,
                TO_CLIENT
        );

        // 图鉴创造模式给予物品包（客户端 → 服务器）
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexGiveItemPacket.class,
                CodexGiveItemPacket::encode,
                CodexGiveItemPacket::decode,
                CodexGiveItemPacket::handle,
                TO_SERVER
        );

        // 战甲模组完整同步包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                WarframeModuleSyncPacket.class,
                WarframeModuleSyncPacket::encode,
                WarframeModuleSyncPacket::decode,
                WarframeModuleSyncPacket::handle,
                TO_CLIENT
        );

        // 顶部解密进度HUD同步包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                DecryptionHudPacket.class,
                DecryptionHudPacket::encode,
                DecryptionHudPacket::decode,
                DecryptionHudPacket::handle,
                TO_CLIENT
        );

        // 灭骸之扉一键补全答案包（客户端 → 服务器，创造模式专用）
        INSTANCE.registerMessage(
                nextMessageId(),
                RequiemGateFillPacket.class,
                RequiemGateFillPacket::encode,
                RequiemGateFillPacket::decode,
                RequiemGateFillPacket::handle,
                TO_SERVER
        );

        // 图鉴创造模式一键装配包（客户端 → 服务器）
        // 注意：新包必须追加在最后：messageId 是顺序分配的，插在中间会让已有包的 id 全部后移，
        //    与旧版客户端/服务端连接时就会对不上号。
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexInstallModulePacket.class,
                CodexInstallModulePacket::encode,
                CodexInstallModulePacket::decode,
                CodexInstallModulePacket::handle,
                TO_SERVER
        );

        // 一键装配结果包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexActionResultPacket.class,
                CodexActionResultPacket::encode,
                CodexActionResultPacket::decode,
                CodexActionResultPacket::handle,
                TO_CLIENT
        );

        // 冒险指南：服务端配置快照（服务器 → 客户端，登录与配置热重载时发送）
        INSTANCE.registerMessage(
                nextMessageId(),
                GuideConfigSyncPacket.class,
                GuideConfigSyncPacket::encode,
                GuideConfigSyncPacket::decode,
                GuideConfigSyncPacket::handle,
                TO_CLIENT
        );

        // 音画效果包（服务器 → 客户端）：只说「发生了什么」，声音、粒子、屏幕效果都由客户端按时间轴放
        INSTANCE.registerMessage(
                nextMessageId(),
                KuvaFxPacket.class,
                KuvaFxPacket::encode,
                KuvaFxPacket::decode,
                KuvaFxPacket::handle,
                TO_CLIENT
        );

        // 玩家自己的玄骸状态（服务器 → 客户端）：名字、等级、怒气、等待原因，HUD 和名牌读它
        INSTANCE.registerMessage(
                nextMessageId(),
                LichStatePacket.class,
                LichStatePacket::encode,
                LichStatePacket::decode,
                LichStatePacket::handle,
                TO_CLIENT
        );

        // 打开玄骸档案时向服务端要数据（客户端 → 服务器，空包，服务端每人 500 ms 限一次）
        INSTANCE.registerMessage(
                nextMessageId(),
                LichArchiveRequestPacket.class,
                LichArchiveRequestPacket::encode,
                LichArchiveRequestPacket::decode,
                LichArchiveRequestPacket::handle,
                TO_SERVER
        );

        // 玄骸档案的完整数据（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                LichArchivePacket.class,
                LichArchivePacket::encode,
                LichArchivePacket::decode,
                LichArchivePacket::handle,
                TO_CLIENT
        );

        // 通讯窗里的一句台词（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                KuvaCommPacket.class,
                KuvaCommPacket::encode,
                KuvaCommPacket::decode,
                KuvaCommPacket::handle,
                TO_CLIENT
        );

        // 对倒地的玄骸或奴仆请求怜悯之击（客户端 → 服务器）
        INSTANCE.registerMessage(
                nextMessageId(),
                MercyRequestPacket.class,
                MercyRequestPacket::encode,
                MercyRequestPacket::decode,
                MercyRequestPacket::handle,
                TO_SERVER
        );

        // 一条玩家提示（服务器 → 客户端）：客户端按设置画成提示条，或者退回聊天 / 动作栏
        INSTANCE.registerMessage(
                nextMessageId(),
                NoticePacket.class,
                NoticePacket::encode,
                NoticePacket::decode,
                NoticePacket::handle,
                TO_CLIENT
        );

        // 安魂之融裂罅循环：循环 / 挑选 / 都不要 / 换上（客户端 → 服务器，带状态序号）
        INSTANCE.registerMessage(
                nextMessageId(),
                RivenCyclePacket.class,
                RivenCyclePacket::encode,
                RivenCyclePacket::decode,
                RivenCyclePacket::handle,
                TO_SERVER
        );

        // 安魂之融裂罅循环的候选卡和处理结果（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                RivenCycleStatePacket.class,
                RivenCycleStatePacket::encode,
                RivenCycleStatePacket::decode,
                RivenCycleStatePacket::handle,
                TO_CLIENT
        );

        // 终结的感化 / 消灭选择（客户端 → 服务器，一个字节）
        INSTANCE.registerMessage(
                nextMessageId(),
                MercyChoicePacket.class,
                MercyChoicePacket::encode,
                MercyChoicePacket::decode,
                MercyChoicePacket::handle,
                TO_SERVER
        );

        // 选择窗口开了：能不能感化、剩余 tick、会被挤出名册的那只（服务器 → 处决者）
        INSTANCE.registerMessage(
                nextMessageId(),
                MercyChoiceOpenPacket.class,
                MercyChoiceOpenPacket::encode,
                MercyChoiceOpenPacket::decode,
                MercyChoiceOpenPacket::handle,
                TO_CLIENT
        );
    }

    private static int nextMessageId() {
        return messageId++;
    }

    public static SimpleChannel getChannel() {
        if (INSTANCE == null) {
            throw new IllegalStateException("网络通道未初始化！请先调用register()方法。");
        }
        return INSTANCE;
    }
}
