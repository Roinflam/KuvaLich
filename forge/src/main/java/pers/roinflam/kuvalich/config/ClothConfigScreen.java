package pers.roinflam.kuvalich.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * 赤毒玄骸 Cloth Config 配置界面
 * Kuva Lich Cloth Config Screen
 *
 * @author RoinFlam
 */
public class ClothConfigScreen {

    /**
     * 创建配置界面
     * Create configuration screen
     *
     * @param parent 父屏幕 / Parent screen
     * @return 配置屏幕 / Configuration screen
     */
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.kuvalich.title"))
                .setSavingRunnable(() -> {
                    // 保存配置
                    ModConfig.COMMON_CONFIG.save();
                    ModuleConfig.MODULE_CONFIG.save();
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ═══════════════════════════════════════════════════════════════
        // 调试设置
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory debugCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.debug"));

        debugCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.kuvalich.enableDetailedLogging"),
                        ModConfig.KUVA_LICH.enableDetailedLogging.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.kuvalich.enableDetailedLogging.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.enableDetailedLogging::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 护盾系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory shieldCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.shield"));

        shieldCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.shieldCapMultiplier"),
                        ModConfig.KUVA_LICH.shieldCapMultiplier.get())
                .setDefaultValue(0.5)
                .setMin(0.0)
                .setMax(10.0)
                .setTooltip(Component.translatable("config.kuvalich.shieldCapMultiplier.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.shieldCapMultiplier::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 物品没收系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory confiscationCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.confiscation"));

        confiscationCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.confiscationChance"),
                        ModConfig.KUVA_LICH.confiscationChance.get())
                .setDefaultValue(1.0)
                .setMin(0.0)
                .setMax(100.0)
                .setTooltip(Component.translatable("config.kuvalich.confiscationChance.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.confiscationChance::set)
                .build());

        confiscationCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.requiemUltimatumDropChance"),
                        ModConfig.KUVA_LICH.requiemUltimatumDropChance.get())
                .setDefaultValue(25.0)
                .setMin(0.0)
                .setMax(100.0)
                .setTooltip(Component.translatable("config.kuvalich.requiemUltimatumDropChance.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.requiemUltimatumDropChance::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 解密进度系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory decryptionCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.decryption"));

        decryptionCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.minDecryptionProgress"),
                        ModConfig.KUVA_LICH.minDecryptionProgress.get())
                .setDefaultValue(3)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.minDecryptionProgress.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.minDecryptionProgress::set)
                .build());

        decryptionCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxDecryptionProgress"),
                        ModConfig.KUVA_LICH.maxDecryptionProgress.get())
                .setDefaultValue(5)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.maxDecryptionProgress.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxDecryptionProgress::set)
                .build());

        decryptionCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.firstStage"),
                        ModConfig.KUVA_LICH.firstStage.get())
                .setDefaultValue(60)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.firstStage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.firstStage::set)
                .build());

        decryptionCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.secondStage"),
                        ModConfig.KUVA_LICH.secondStage.get())
                .setDefaultValue(84)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.secondStage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.secondStage::set)
                .build());

        decryptionCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.thirdStage"),
                        ModConfig.KUVA_LICH.thirdStage.get())
                .setDefaultValue(120)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.thirdStage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.thirdStage::set)
                .build());

        decryptionCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.masterPotionMultiplier"),
                        ModConfig.KUVA_LICH.masterPotionMultiplier.get())
                .setDefaultValue(5.0)
                .setMin(1.0)
                .setTooltip(Component.translatable("config.kuvalich.masterPotionMultiplier.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.masterPotionMultiplier::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 伤害系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory damageCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.damage"));

        damageCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.kuvalich.damageDisplay"),
                        ModConfig.KUVA_LICH.damageDisplay.get())
                .setDefaultValue(false)
                .setTooltip(Component.translatable("config.kuvalich.damageDisplay.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.damageDisplay::set)
                .build());

        // ✅ 新增:伤害跳字开关
        damageCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.kuvalich.enableDamageNumbers"),
                        ModConfig.KUVA_LICH.enableDamageNumbers.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.kuvalich.enableDamageNumbers.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.enableDamageNumbers::set)
                .build());

        damageCategory.addEntry(entryBuilder.startBooleanToggle(
                        Component.translatable("config.kuvalich.enableTrueDamage"),
                        ModConfig.KUVA_LICH.enableTrueDamage.get())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("config.kuvalich.enableTrueDamage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.enableTrueDamage::set)
                .build());

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.battleBoost"),
                        ModConfig.KUVA_LICH.battleBoost.get())
                .setDefaultValue(0.075)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.battleBoost.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.battleBoost::set)
                .build());

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.reducedDamage"),
                        ModConfig.KUVA_LICH.reducedDamage.get())
                .setDefaultValue(0.1)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.reducedDamage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.reducedDamage::set)
                .build());

        damageCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.increaseDamage"),
                        ModConfig.KUVA_LICH.increaseDamage.get())
                .setDefaultValue(0.25)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.increaseDamage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.increaseDamage::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 武器等级系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory weaponLevelCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.weaponLevel"));

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.benchmarkLevel"),
                        ModConfig.KUVA_LICH.benchmarkLevel.get())
                .setDefaultValue(45)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.benchmarkLevel.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.benchmarkLevel::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.baseMinimumLevel"),
                        ModConfig.KUVA_LICH.baseMinimumLevel.get())
                .setDefaultValue(5)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.baseMinimumLevel.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.baseMinimumLevel::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.baseMaximumLevel"),
                        ModConfig.KUVA_LICH.baseMaximumLevel.get())
                .setDefaultValue(25)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.baseMaximumLevel.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.baseMaximumLevel::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.minimumLevelCapIncrease"),
                        ModConfig.KUVA_LICH.minimumLevelCapIncrease.get())
                .setDefaultValue(3)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.minimumLevelCapIncrease.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.minimumLevelCapIncrease::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maximumLevelCapIncrease"),
                        ModConfig.KUVA_LICH.maximumLevelCapIncrease.get())
                .setDefaultValue(6)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.maximumLevelCapIncrease.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maximumLevelCapIncrease::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.minimumLevel"),
                        ModConfig.KUVA_LICH.minimumLevel.get())
                .setDefaultValue(25)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.minimumLevel.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.minimumLevel::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maximumLevel"),
                        ModConfig.KUVA_LICH.maximumLevel.get())
                .setDefaultValue(60)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.maximumLevel.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maximumLevel::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.upgradeMultiplier"),
                        ModConfig.KUVA_LICH.upgradeMultiplier.get())
                .setDefaultValue(0.1)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.upgradeMultiplier.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.upgradeMultiplier::set)
                .build());

        weaponLevelCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.upgradeLimit"),
                        ModConfig.KUVA_LICH.upgradeLimit.get())
                .setDefaultValue(999)
                .setMin(0)
                .setTooltip(Component.translatable("config.kuvalich.upgradeLimit.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.upgradeLimit::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 武器击杀叠层系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory weaponStackCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.weaponStack"));

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.weaponStackDecayTicks"),
                        ModConfig.KUVA_LICH.weaponStackDecayTicks.get())
                .setDefaultValue(200)
                .setMin(20)
                .setMax(6000)
                .setTooltip(Component.translatable("config.kuvalich.weaponStackDecayTicks.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.weaponStackDecayTicks::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksBaseDamage"),
                        ModConfig.KUVA_LICH.maxStacksBaseDamage.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksBaseDamage.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksBaseDamage::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksMultishot"),
                        ModConfig.KUVA_LICH.maxStacksMultishot.get())
                .setDefaultValue(5)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksMultishot.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksMultishot::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksMeleeCritMult"),
                        ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get())
                .setDefaultValue(4)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksMeleeCritMult.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksMeleeCritMult::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksTriggerChance"),
                        ModConfig.KUVA_LICH.maxStacksTriggerChance.get())
                .setDefaultValue(4)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksTriggerChance.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksTriggerChance::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksAttackRange"),
                        ModConfig.KUVA_LICH.maxStacksAttackRange.get())
                .setDefaultValue(5)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksAttackRange.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksAttackRange::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksAttackSpeed"),
                        ModConfig.KUVA_LICH.maxStacksAttackSpeed.get())
                .setDefaultValue(5)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksAttackSpeed.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksAttackSpeed::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksBurstingRadius"),
                        ModConfig.KUVA_LICH.maxStacksBurstingRadius.get())
                .setDefaultValue(5)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksBurstingRadius.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksBurstingRadius::set)
                .build());

        weaponStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksFiringRate"),
                        ModConfig.KUVA_LICH.maxStacksFiringRate.get())
                .setDefaultValue(10)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksFiringRate.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksFiringRate::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 战甲击杀叠层系统
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory warframeStackCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.warframeStack"));

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.warframeStackDecayTicks"),
                        ModConfig.KUVA_LICH.warframeStackDecayTicks.get())
                .setDefaultValue(400)
                .setMin(20)
                .setMax(6000)
                .setTooltip(Component.translatable("config.kuvalich.warframeStackDecayTicks.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.warframeStackDecayTicks::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksHealth"),
                        ModConfig.KUVA_LICH.maxStacksHealth.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksHealth.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksHealth::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksShield"),
                        ModConfig.KUVA_LICH.maxStacksShield.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksShield.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksShield::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksArmor"),
                        ModConfig.KUVA_LICH.maxStacksArmor.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksArmor.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksArmor::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksSprintSpeed"),
                        ModConfig.KUVA_LICH.maxStacksSprintSpeed.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksSprintSpeed.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksSprintSpeed::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksShieldRecoveryRate"),
                        ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksShieldRecoveryRate.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksShieldRecoveryDelay"),
                        ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksShieldRecoveryDelay.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksFireProtection"),
                        ModConfig.KUVA_LICH.maxStacksFireProtection.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksFireProtection.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksFireProtection::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksElectricProtection"),
                        ModConfig.KUVA_LICH.maxStacksElectricProtection.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksElectricProtection.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksElectricProtection::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksHomologousProtection"),
                        ModConfig.KUVA_LICH.maxStacksHomologousProtection.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksHomologousProtection.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksHomologousProtection::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksResponseRate"),
                        ModConfig.KUVA_LICH.maxStacksResponseRate.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksResponseRate.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksResponseRate::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksItemDropMultiplier"),
                        ModConfig.KUVA_LICH.maxStacksItemDropMultiplier.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksItemDropMultiplier.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksItemDropMultiplier::set)
                .build());

        warframeStackCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.maxStacksDiggingSpeed"),
                        ModConfig.KUVA_LICH.maxStacksDiggingSpeed.get())
                .setDefaultValue(20)
                .setMin(1)
                .setMax(100)
                .setTooltip(Component.translatable("config.kuvalich.maxStacksDiggingSpeed.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.maxStacksDiggingSpeed::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 实体生成
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory spawnCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.spawn"));

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaLichSpawnWeight"),
                        ModConfig.KUVA_LICH.kuvaLichSpawnWeight.get())
                .setDefaultValue(5)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaLichSpawnWeight.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaLichSpawnWeight::set)
                .build());

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaLichMinSpawnCount"),
                        ModConfig.KUVA_LICH.kuvaLichMinSpawnCount.get())
                .setDefaultValue(1)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaLichMinSpawnCount.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaLichMinSpawnCount::set)
                .build());

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaLichMaxSpawnCount"),
                        ModConfig.KUVA_LICH.kuvaLichMaxSpawnCount.get())
                .setDefaultValue(1)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaLichMaxSpawnCount.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaLichMaxSpawnCount::set)
                .build());

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaSlaveSpawnWeight"),
                        ModConfig.KUVA_LICH.kuvaSlaveSpawnWeight.get())
                .setDefaultValue(10)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaSlaveSpawnWeight.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaSlaveSpawnWeight::set)
                .build());

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaSlaveMinSpawnCount"),
                        ModConfig.KUVA_LICH.kuvaSlaveMinSpawnCount.get())
                .setDefaultValue(1)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaSlaveMinSpawnCount.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaSlaveMinSpawnCount::set)
                .build());

        spawnCategory.addEntry(entryBuilder.startIntField(
                        Component.translatable("config.kuvalich.kuvaSlaveMaxSpawnCount"),
                        ModConfig.KUVA_LICH.kuvaSlaveMaxSpawnCount.get())
                .setDefaultValue(3)
                .setMin(1)
                .setTooltip(Component.translatable("config.kuvalich.kuvaSlaveMaxSpawnCount.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_LICH.kuvaSlaveMaxSpawnCount::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 赤毒武器配置
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory weaponAttributeCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.weaponAttribute"));

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attributeMultiplier"),
                        ModConfig.KUVA_WEAPON.attributeMultiplier.get())
                .setDefaultValue(1.5)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attributeMultiplier.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attributeMultiplier::set)
                .build());

        // 赤毒希尔德
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageKuvaShildeg"),
                        ModConfig.KUVA_WEAPON.attackDamageKuvaShildeg.get())
                .setDefaultValue(44.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageKuvaShildeg.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageKuvaShildeg::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedKuvaShildeg"),
                        ModConfig.KUVA_WEAPON.attackSpeedKuvaShildeg.get())
                .setDefaultValue(0.7)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedKuvaShildeg.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedKuvaShildeg::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedKuvaShildeg"),
                        ModConfig.KUVA_WEAPON.movementSpeedKuvaShildeg.get())
                .setDefaultValue(-0.2)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedKuvaShildeg.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedKuvaShildeg::set)
                .build());

        // 尖幡
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamagePennant"),
                        ModConfig.KUVA_WEAPON.attackDamagePennant.get())
                .setDefaultValue(28.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamagePennant.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamagePennant::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedPennant"),
                        ModConfig.KUVA_WEAPON.attackSpeedPennant.get())
                .setDefaultValue(1.1)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedPennant.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedPennant::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedPennant"),
                        ModConfig.KUVA_WEAPON.movementSpeedPennant.get())
                .setDefaultValue(0.025)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedPennant.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedPennant::set)
                .build());

        // 关刀Prime
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageGuandaoPrime"),
                        ModConfig.KUVA_WEAPON.attackDamageGuandaoPrime.get())
                .setDefaultValue(16.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageGuandaoPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageGuandaoPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedGuandaoPrime"),
                        ModConfig.KUVA_WEAPON.attackSpeedGuandaoPrime.get())
                .setDefaultValue(2.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedGuandaoPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedGuandaoPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedGuandaoPrime"),
                        ModConfig.KUVA_WEAPON.movementSpeedGuandaoPrime.get())
                .setDefaultValue(0.075)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedGuandaoPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedGuandaoPrime::set)
                .build());

        // 心智之殁
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageParacesis"),
                        ModConfig.KUVA_WEAPON.attackDamageParacesis.get())
                .setDefaultValue(34.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageParacesis.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageParacesis::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedParacesis"),
                        ModConfig.KUVA_WEAPON.attackSpeedParacesis.get())
                .setDefaultValue(0.9)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedParacesis.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedParacesis::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedParacesis"),
                        ModConfig.KUVA_WEAPON.movementSpeedParacesis.get())
                .setDefaultValue(-0.1)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedParacesis.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedParacesis::set)
                .build());

        // 弧电振子锤
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageArcaTitron"),
                        ModConfig.KUVA_WEAPON.attackDamageArcaTitron.get())
                .setDefaultValue(38.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageArcaTitron.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageArcaTitron::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedArcaTitron"),
                        ModConfig.KUVA_WEAPON.attackSpeedArcaTitron.get())
                .setDefaultValue(0.75)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedArcaTitron.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedArcaTitron::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedArcaTitron"),
                        ModConfig.KUVA_WEAPON.movementSpeedArcaTitron.get())
                .setDefaultValue(-0.15)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedArcaTitron.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedArcaTitron::set)
                .build());

        // 收割者Prime
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageReaperPrime"),
                        ModConfig.KUVA_WEAPON.attackDamageReaperPrime.get())
                .setDefaultValue(18.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageReaperPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageReaperPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedReaperPrime"),
                        ModConfig.KUVA_WEAPON.attackSpeedReaperPrime.get())
                .setDefaultValue(1.75)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedReaperPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedReaperPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedReaperPrime"),
                        ModConfig.KUVA_WEAPON.movementSpeedReaperPrime.get())
                .setDefaultValue(0.05)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedReaperPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedReaperPrime::set)
                .build());

        // 金璃剑
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageVitrica"),
                        ModConfig.KUVA_WEAPON.attackDamageVitrica.get())
                .setDefaultValue(50.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageVitrica.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageVitrica::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedVitrica"),
                        ModConfig.KUVA_WEAPON.attackSpeedVitrica.get())
                .setDefaultValue(0.65)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedVitrica.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedVitrica::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedVitrica"),
                        ModConfig.KUVA_WEAPON.movementSpeedVitrica.get())
                .setDefaultValue(-0.2)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedVitrica.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedVitrica::set)
                .build());

        // 格拉姆Prime
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageGramPrime"),
                        ModConfig.KUVA_WEAPON.attackDamageGramPrime.get())
                .setDefaultValue(46.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageGramPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageGramPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedGramPrime"),
                        ModConfig.KUVA_WEAPON.attackSpeedGramPrime.get())
                .setDefaultValue(0.7)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedGramPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedGramPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedGramPrime"),
                        ModConfig.KUVA_WEAPON.movementSpeedGramPrime.get())
                .setDefaultValue(-0.15)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedGramPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedGramPrime::set)
                .build());

        // 圣洁执法者
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageSanctiMagistar"),
                        ModConfig.KUVA_WEAPON.attackDamageSanctiMagistar.get())
                .setDefaultValue(36.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageSanctiMagistar.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageSanctiMagistar::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedSanctiMagistar"),
                        ModConfig.KUVA_WEAPON.attackSpeedSanctiMagistar.get())
                .setDefaultValue(0.85)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedSanctiMagistar.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedSanctiMagistar::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedSanctiMagistar"),
                        ModConfig.KUVA_WEAPON.movementSpeedSanctiMagistar.get())
                .setDefaultValue(-0.075)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedSanctiMagistar.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedSanctiMagistar::set)
                .build());

        // 技巧之剑Prime
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageDestrezaPrime"),
                        ModConfig.KUVA_WEAPON.attackDamageDestrezaPrime.get())
                .setDefaultValue(24.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageDestrezaPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageDestrezaPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedDestrezaPrime"),
                        ModConfig.KUVA_WEAPON.attackSpeedDestrezaPrime.get())
                .setDefaultValue(1.4)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedDestrezaPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedDestrezaPrime::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedDestrezaPrime"),
                        ModConfig.KUVA_WEAPON.movementSpeedDestrezaPrime.get())
                .setDefaultValue(0.125)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedDestrezaPrime.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedDestrezaPrime::set)
                .build());

        // 棱晶真理巨剑
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamagePrismaVeritux"),
                        ModConfig.KUVA_WEAPON.attackDamagePrismaVeritux.get())
                .setDefaultValue(100.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamagePrismaVeritux.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamagePrismaVeritux::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedPrismaVeritux"),
                        ModConfig.KUVA_WEAPON.attackSpeedPrismaVeritux.get())
                .setDefaultValue(0.45)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedPrismaVeritux.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedPrismaVeritux::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedPrismaVeritux"),
                        ModConfig.KUVA_WEAPON.movementSpeedPrismaVeritux.get())
                .setDefaultValue(-0.6)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedPrismaVeritux.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedPrismaVeritux::set)
                .build());

        // 马谢特砍刀
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageMachete"),
                        ModConfig.KUVA_WEAPON.attackDamageMachete.get())
                .setDefaultValue(32.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageMachete.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageMachete::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedMachete"),
                        ModConfig.KUVA_WEAPON.attackSpeedMachete.get())
                .setDefaultValue(1.55)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedMachete.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedMachete::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedMachete"),
                        ModConfig.KUVA_WEAPON.movementSpeedMachete.get())
                .setDefaultValue(0.025)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedMachete.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedMachete::set)
                .build());

        // 灼蚀变体镰
        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackDamageCaustacyst"),
                        ModConfig.KUVA_WEAPON.attackDamageCaustacyst.get())
                .setDefaultValue(28.0)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackDamageCaustacyst.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackDamageCaustacyst::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.attackSpeedCaustacyst"),
                        ModConfig.KUVA_WEAPON.attackSpeedCaustacyst.get())
                .setDefaultValue(1.3)
                .setMin(0.0)
                .setTooltip(Component.translatable("config.kuvalich.attackSpeedCaustacyst.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.attackSpeedCaustacyst::set)
                .build());

        weaponAttributeCategory.addEntry(entryBuilder.startDoubleField(
                        Component.translatable("config.kuvalich.movementSpeedCaustacyst"),
                        ModConfig.KUVA_WEAPON.movementSpeedCaustacyst.get())
                .setDefaultValue(0.125)
                .setMin(-1.0)
                .setMax(1.0)
                .setTooltip(Component.translatable("config.kuvalich.movementSpeedCaustacyst.tooltip"))
                .setSaveConsumer(ModConfig.KUVA_WEAPON.movementSpeedCaustacyst::set)
                .build());

        // ═══════════════════════════════════════════════════════════════
        // 模组管理
        // ═══════════════════════════════════════════════════════════════
        ConfigCategory moduleCategory = builder.getOrCreateCategory(
                Component.translatable("config.kuvalich.category.module"));

        moduleCategory.addEntry(entryBuilder.startStrList(
                        Component.translatable("config.kuvalich.disabledModuleTypes"),
                        new java.util.ArrayList<>(ModuleConfig.DISABLED_MODULE_TYPES.get()))
                .setDefaultValue(java.util.Arrays.asList())
                .setTooltip(Component.translatable("config.kuvalich.disabledModuleTypes.tooltip"))
                .setSaveConsumer(list -> {
                    ModuleConfig.DISABLED_MODULE_TYPES.set(list);
                    ModuleConfig.rebuildCache();
                })
                .build());

        return builder.build();
    }
}