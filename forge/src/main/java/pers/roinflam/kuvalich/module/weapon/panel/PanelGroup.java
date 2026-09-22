package pers.roinflam.kuvalich.module.weapon.panel;


/**
 * 面板属性的语义分组
 * <p>
 * 紧凑视图按组打包：同一组的词条尽量塞进同一行，组与组之间用装订线（gutter）分隔。
 * 一个组里一条有效词条都没有时，**整组不生成**——这是把一把普通铁剑的 tooltip
 * 从十几行压到五行的主要手段（枪械组的 10 条词条对近战武器永远是噪音）。
 * </p>
 *
 * <p>⭐ 本枚举刻意放在 {@code module.weapon.panel} 而不是 {@code client} 包下：
 * 数据层与客户端展示层分家，专用服务端不会因为引用到面板描述表而
 * classload 到 {@code Font} / {@code I18n}。改造后它连 {@code ChatFormatting} 都不再依赖 ——
 * 现在是一个纯粹的「组身份 + i18n 键」枚举。</p>

 * <p>⭐ 曾经带过三个颜色字段（gutterColor / titleColor / valueColor）。它们已被
 * {@code client.tooltip.PanelPalette} 的 {@code header(PanelGroup)} / {@code value(PanelGroup)}
 * 两个 RGB 方法取代，到删除前是死代码：前两个只被 {@code PanelStyle.groupPrefix} /
 * {@code groupContinuation} 用，而那两个方法全树零调用者（死代码撑死代码），
 * valueColor 连一层调用者都没有。留着等于同一套「组→颜色」映射维护两份，已一并删除。</p>
 *
 * @author RoinFlam
 */
public enum PanelGroup {

    /** 最终面板核心：基伤、暴击、暴伤、触发、多重、攻速、射速、范围 —— 永远显示 */
    PANEL("panel"),

    /** 伤害增益：近战/远程/箭矢/魔法/特攻/冲刺 */
    DAMAGE("damage"),

    /** 元素伤害：13 种元素合并为一行彩色缩写 */
    ELEMENT("element"),

    /** 枪械专属：装填、弹夹、后坐、爆头、瞄准、精准等（仅 TACZ 枪与弓弩生成） */
    GUN("gun"),

    /** 稀有效果：处决阈值、抹除增益、秒杀概率 */
    RARE("rare"),

    /** 击杀叠层：显示实时层数与当前生效加成 */
    STACK("stack"),

    /** 额外装备槽位（副手 / 护甲 / Curios）带来的加成 */
    EXTRA("extra"),

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
    OTHER("other"),

    /** 已装备的模组卡名单 */
    MODULES("modules");

    /** i18n 后缀，完整键为 {@code kuvalich.panel.group.<id>} */
    private final String id;

    PanelGroup(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 组标题的翻译键 */
    public String titleKey() {
        return "kuvalich.panel.group." + id;
    }
}
