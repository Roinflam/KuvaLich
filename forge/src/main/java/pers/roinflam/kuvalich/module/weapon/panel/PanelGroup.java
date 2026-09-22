package pers.roinflam.kuvalich.module.weapon.panel;

import net.minecraft.ChatFormatting;

/**
 * 面板属性的语义分组
 * <p>
 * 紧凑视图按组打包：同一组的词条尽量塞进同一行，组与组之间用装订线（gutter）分隔。
 * 一个组里一条有效词条都没有时，**整组不生成**——这是把一把普通铁剑的 tooltip
 * 从十几行压到五行的主要手段（枪械组的 10 条词条对近战武器永远是噪音）。
 * </p>
 *
 * <p>⭐ 本枚举刻意放在 {@code module.weapon.panel} 而不是 {@code client} 包下：
 * 它只依赖 {@link ChatFormatting}（双端可用），数据层与客户端展示层分家，
 * 专用服务端不会因为引用到面板描述表而 classload 到 {@code Font} / {@code I18n}。</p>
 *
 * @author RoinFlam
 */
public enum PanelGroup {

    /** 最终面板核心：基伤、暴击、暴伤、触发、多重、攻速、射速、范围 —— 永远显示 */
    PANEL("panel", ChatFormatting.DARK_GRAY, ChatFormatting.WHITE, ChatFormatting.WHITE),

    /** 伤害增益：近战/远程/箭矢/魔法/特攻/冲刺 */
    DAMAGE("damage", ChatFormatting.DARK_RED, ChatFormatting.RED, ChatFormatting.GREEN),

    /** 元素伤害：13 种元素合并为一行彩色缩写 */
    ELEMENT("element", ChatFormatting.DARK_PURPLE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE),

    /** 枪械专属：装填、弹夹、后坐、爆头、瞄准、精准等（仅 TACZ 枪与弓弩生成） */
    GUN("gun", ChatFormatting.DARK_BLUE, ChatFormatting.BLUE, ChatFormatting.GREEN),

    /** 稀有效果：处决阈值、抹除增益、秒杀概率 */
    RARE("rare", ChatFormatting.DARK_GRAY, ChatFormatting.GOLD, ChatFormatting.GOLD),

    /** 击杀叠层：显示实时层数与当前生效加成 */
    STACK("stack", ChatFormatting.GOLD, ChatFormatting.GOLD, ChatFormatting.GOLD),

    /** 额外装备槽位（副手 / 护甲 / Curios）带来的加成 */
    EXTRA("extra", ChatFormatting.DARK_GREEN, ChatFormatting.GREEN, ChatFormatting.GREEN),

    /**
     * 描述表不认识的属性
     *
     * <p>⭐ {@code CustomModuleManager} 允许整合包作者在 JSON 里写**任意**属性 key
     * （{@code CustomModuleManager.createModuleStack} 直接把 {@code entry.attributes}
     * 透传给 {@code AbstractModule.addAttributes}，不做任何校验）。
     * 这些 key 不在 {@link WeaponPanelCatalog#SPECS} 里，如果不兜底就会在武器面板上
     * **静默消失** —— 作者会以为自己写的词条没生效，而实际上它在战斗里是算数的。
     * 这一组把它们按原始 key 名 + 通用百分比格式显示出来。</p>
     */
    OTHER("other", ChatFormatting.DARK_GRAY, ChatFormatting.GRAY, ChatFormatting.GRAY),

    /** 已装备的模组卡名单 */
    MODULES("modules", ChatFormatting.DARK_GRAY, ChatFormatting.DARK_GRAY, ChatFormatting.GRAY);

    /** i18n 后缀，完整键为 {@code kuvalich.panel.group.<id>} */
    private final String id;
    /** 装订线颜色（行首那一竖） */
    private final ChatFormatting gutterColor;
    /** 组标题颜色 */
    private final ChatFormatting titleColor;
    /** 正值的默认颜色（负值一律 RED，由渲染层处理） */
    private final ChatFormatting valueColor;

    PanelGroup(String id, ChatFormatting gutterColor, ChatFormatting titleColor, ChatFormatting valueColor) {
        this.id = id;
        this.gutterColor = gutterColor;
        this.titleColor = titleColor;
        this.valueColor = valueColor;
    }

    public String id() {
        return id;
    }

    /** 组标题的翻译键 */
    public String titleKey() {
        return "kuvalich.panel.group." + id;
    }

    public ChatFormatting gutterColor() {
        return gutterColor;
    }

    public ChatFormatting titleColor() {
        return titleColor;
    }

    public ChatFormatting valueColor() {
        return valueColor;
    }

    /**
     * 该组是否属于「武器身份信息」
     *
     * <p>⚠️ <b>这个判据不再用于决定单条词条的可见性</b>。
     * 以前 {@code WeaponPanelData.isVisible} 直接拿它做短路，导致同在 PANEL 组的
     * 多重 / 攻速 / 射速 / 范围 / 触发时长也被恒显 —— 一把只插了伤害卡的武器
     * 会平白多出四五个零值 chip。现在恒显标志下放到了
     * {@link AttributeSpec#always()}，只给基伤 / 暴击 / 暴伤 / 触发四条。</p>
     *
     * <p>保留本方法给「整组是否生成」一类的判断使用。</p>
     */
    public boolean alwaysVisible() {
        return this == PANEL;
    }
}
