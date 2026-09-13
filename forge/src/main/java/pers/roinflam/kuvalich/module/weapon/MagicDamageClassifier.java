package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 魔法伤害分类器（不依赖任何第三方模组）
 *
 * <p>回答一个问题：这次 {@link DamageSource} 算不算"魔法伤害"。
 * {@link WeaponCombatHandler} 用它决定要不要吃 {@code magicDamage} 词条，
 * {@code AbstractKuva} 用它给赤毒实体的适应性抗性分类。</p>
 *
 * <p><b>判定顺序</b>（命中一条即返回）：</p>
 * <ol>
 *   <li><b>召唤物/宠物挥砍排除</b>：直接实体是活物、但归属实体是另一个实体
 *       （典型：ISB 召唤的僵尸替玩家砍人，伤害类型却是 blood_magic）。
 *       这本质上是一次近战命中，不按魔法算。</li>
 *   <li><b>黑名单</b>（配置 {@code magicDamageTypeBlacklist}）：伤害类型 id 在黑名单里 → 一律不算魔法，
 *       优先级高于下面所有规则，方便服主一票否决。</li>
 *   <li><b>原版规则</b>（保留原有行为）：message_id 含 "magic"，或伤害类型在
 *       {@code minecraft:witch_resistant_to} 里。</li>
 *   <li><b>第三方规则</b>（总开关 {@code enableThirdPartyMagicRecognition}）：
 *       <ul>
 *         <li>白名单（配置 {@code magicDamageTypeWhitelist}）精确命中 id；</li>
 *         <li>伤害类型在 {@code forge:is_magic} tag 里（Ars Nouveau、Goety 都往这个社区约定 tag 里挂）；</li>
 *         <li>伤害类型 id 的命名空间在 {@code magicDamageNamespaces} 里（如 {@code goety:direct_shock} 的 {@code goety}）。</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p><b>为什么用命名空间而不是逐个列 id</b>：这几个魔法模组给每种法术都定义了自己的伤害类型，
 * 且 message_id 大多不带 "magic"（Ars Nouveau 直接叫 "player"、Goety 叫 "goety.xxx"），
 * 逐个列会随模组更新失效。命名空间规则只要模组 id 不变就一直有效，
 * 少数不该算魔法的（召唤、镰刀、战利品爆炸）用黑名单排掉。</p>
 *
 * <p><b>已知识别不了的情况</b>：SweetMagic 的魔弹打末影人/女巫时，会故意换成原版
 * {@code player_attack} 伤害源、直接实体就是玩家本人，在伤害事件里和真正的近战一模一样，
 * 没有任何办法区分，那两下会按近战结算。</p>
 *
 * <p><b>性能</b>：配置里的列表会被解析成 {@link Set} 缓存在 {@link Snapshot} 里，
 * 每次伤害事件只做几次 HashSet 查询；配置重载或配置界面保存时调用 {@link #invalidateCache()} 重建。</p>
 */
public final class MagicDamageClassifier {

    /**
     * 社区约定的"魔法伤害"tag：{@code forge:is_magic}
     *
     * <p>Forge 47 本身没有这个常量，Ars Nouveau 和 Goety 都是自己 {@code TagKey.create} 出来的，
     * 这里照样建一个即可。tag 在数据包里不存在时 {@link DamageSource#is(TagKey)} 只会返回 false，不会报错。</p>
     */
    public static final TagKey<DamageType> FORGE_IS_MAGIC =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("forge", "is_magic"));

    /**
     * 配置解析后的只读快照
     *
     * @param enabled    第三方魔法识别总开关
     * @param namespaces 按命名空间整体算魔法的模组 id 集合（已小写）
     * @param whitelist  精确算魔法的伤害类型 id 集合
     * @param blacklist  精确不算魔法的伤害类型 id 集合
     */
    private record Snapshot(boolean enabled,
                            Set<String> namespaces,
                            Set<ResourceLocation> whitelist,
                            Set<ResourceLocation> blacklist) {
    }

    /** 配置尚未加载时使用的保底快照：开关关闭、所有列表为空，只剩原版规则 */
    private static final Snapshot FALLBACK = new Snapshot(false,
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    /** 当前生效的快照；配置重载后置为 null，下一次判定时懒重建 */
    @Nullable
    private static volatile Snapshot cachedSnapshot = null;

    /** 配置未加载的告警只打一次，避免刷屏 */
    private static volatile boolean warnedConfigNotLoaded = false;

    private MagicDamageClassifier() {
    }

    // ========== 对外判定入口 / Public API ==========

    /**
     * 判断一次伤害是否算魔法伤害（原版规则 + 第三方规则）
     *
     * @param source 伤害源，可为 null（返回 false）
     * @return 是否按魔法伤害处理
     */
    public static boolean isMagic(@Nullable DamageSource source) {
        if (source == null) {
            return false;
        }

        // 1. 召唤物/宠物替主人挥砍：直接实体是活物但归属实体是别人，按近战不按魔法
        if (isMinionMelee(source)) {
            return false;
        }

        Snapshot current = snapshot();
        ResourceLocation typeId = getDamageTypeId(source);

        // 2. 黑名单一票否决（仅在第三方识别开启时读取配置；开关关闭时配置列表不生效）
        if (current.enabled() && typeId != null && current.blacklist().contains(typeId)) {
            return false;
        }

        // 3. 原版规则：保持原有行为不变
        if (isVanillaMagic(source)) {
            return true;
        }

        // 4. 第三方规则
        return isThirdPartyMagic(source, typeId, current);
    }

    /**
     * 原版魔法判定：message_id 含 "magic" 或在 {@code minecraft:witch_resistant_to} 里
     *
     * <p>覆盖：原版 magic / indirect_magic / sonic_boom / thorns，
     * 以及所有 message_id 里带 magic 的第三方类型（ISB 九大流派、Geomancy Plus 的 geo_magic、
     * SweetMagic 的 sm_magic / sm_addmagic、Goety 的 magic_bolt 等）。</p>
     *
     * @param source 伤害源
     * @return 是否命中原版规则
     */
    public static boolean isVanillaMagic(DamageSource source) {
        return source.getMsgId().toLowerCase(Locale.ROOT).contains("magic")
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /**
     * 第三方魔法判定：白名单 → forge:is_magic tag → 命名空间
     *
     * @param source  伤害源
     * @param typeId  伤害类型 id，可为 null（伤害类型没有注册键时）
     * @param current 当前配置快照
     * @return 是否命中第三方规则
     */
    private static boolean isThirdPartyMagic(DamageSource source, @Nullable ResourceLocation typeId, Snapshot current) {
        if (!current.enabled()) {
            return false;
        }
        if (typeId != null && current.whitelist().contains(typeId)) {
            return true;
        }
        if (source.is(FORGE_IS_MAGIC)) {
            return true;
        }
        return typeId != null && current.namespaces().contains(typeId.getNamespace());
    }

    /**
     * 判断是否为召唤物/宠物替主人造成的近战命中
     *
     * <p>条件：直接实体是活物，且归属实体存在、不是同一个实体。
     * 施法者自己的触碰/射线法术（直接实体 == 归属实体 == 施法者）不满足该条件，不会被误排。</p>
     *
     * @param source 伤害源
     * @return 是否为召唤物近战
     */
    private static boolean isMinionMelee(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        return direct instanceof LivingEntity && owner != null && owner != direct;
    }

    /**
     * 取伤害类型的注册 id（如 {@code irons_spellbooks:fire_magic}）
     *
     * @param source 伤害源
     * @return 注册 id；伤害类型是未注册的直接 Holder 时返回 null
     */
    @Nullable
    private static ResourceLocation getDamageTypeId(DamageSource source) {
        Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
        return key.map(ResourceKey::location).orElse(null);
    }

    // ========== 配置缓存 / Config Cache ==========

    /**
     * 使缓存失效；配置重载（{@code ModConfigEvent}）或配置界面保存后调用
     */
    public static void invalidateCache() {
        cachedSnapshot = null;
    }

    /**
     * 取当前快照，为空则从配置重建
     *
     * <p>重建失败（配置尚未加载）时返回 {@link #FALLBACK} 但<b>不缓存</b>，
     * 这样配置一旦加载完成，下一次判定就会拿到真实配置。</p>
     *
     * @return 当前生效的快照
     */
    private static Snapshot snapshot() {
        Snapshot current = cachedSnapshot;
        if (current != null) {
            return current;
        }
        Snapshot built = buildSnapshot();
        if (built == null) {
            return FALLBACK;
        }
        cachedSnapshot = built;
        return built;
    }

    /**
     * 从 {@link ModConfig} 读取并解析配置
     *
     * <p>配置尚未加载时 {@code ConfigValue.get()} 会抛 {@link IllegalStateException}，
     * 此时返回 null，由调用方退回 {@link #FALLBACK}（只保留原版规则）。</p>
     *
     * @return 解析后的快照；配置未加载时返回 null
     */
    @Nullable
    private static Snapshot buildSnapshot() {
        try {
            boolean enabled = ModConfig.KUVA_LICH.enableThirdPartyMagicRecognition.get();
            Set<String> namespaces = parseNamespaces(ModConfig.KUVA_LICH.magicDamageNamespaces.get());
            Set<ResourceLocation> whitelist = parseIds(ModConfig.KUVA_LICH.magicDamageTypeWhitelist.get(), "magicDamageTypeWhitelist");
            Set<ResourceLocation> blacklist = parseIds(ModConfig.KUVA_LICH.magicDamageTypeBlacklist.get(), "magicDamageTypeBlacklist");
            LogUtil.debug("[MagicDamageClassifier] 配置快照重建: enabled=" + enabled
                    + ", namespaces=" + namespaces + ", whitelist=" + whitelist.size()
                    + ", blacklist=" + blacklist.size());
            return new Snapshot(enabled, namespaces, whitelist, blacklist);
        } catch (IllegalStateException e) {
            // 配置未加载：交给调用方退回保底快照，且不缓存，留待下次重建
            if (!warnedConfigNotLoaded) {
                warnedConfigNotLoaded = true;
                LogUtil.warn("[MagicDamageClassifier] 配置尚未加载，暂时只按原版规则判定魔法伤害: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * 解析命名空间列表：去空白、转小写、丢弃空串
     *
     * @param raw 配置原始列表
     * @return 命名空间集合
     */
    private static Set<String> parseNamespaces(List<? extends String> raw) {
        Set<String> result = new HashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String namespace = entry.trim().toLowerCase(Locale.ROOT);
            if (!namespace.isEmpty()) {
                result.add(namespace);
            }
        }
        return result;
    }

    /**
     * 解析伤害类型 id 列表：非法 id 记 warn 并跳过，不让一条写错拖垮整份配置
     *
     * @param raw     配置原始列表
     * @param keyName 配置键名，仅用于日志
     * @return 伤害类型 id 集合
     */
    private static Set<ResourceLocation> parseIds(List<? extends String> raw, String keyName) {
        Set<ResourceLocation> result = new HashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                LogUtil.warn("[MagicDamageClassifier] 配置 " + keyName + " 里有非法的伤害类型 id，已跳过: \"" + entry + "\"");
                continue;
            }
            result.add(id);
        }
        return result;
    }
}
