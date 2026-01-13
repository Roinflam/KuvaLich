package pers.roinflam.kuvalich.itemstack;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMobEffects;
import pers.roinflam.kuvalich.itemstack.KillStackManager.StackType;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.HiddenEffectHelper;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 物品模组系统（1.20.1版本，业务逻辑100%不变）
 * Item Module System (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public class ItemModule {

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        if (hasBase(itemStack)) {
            List<Component> tooltip = evt.getToolTip();
            List<ItemStack> modules = getModules(itemStack);
            int index = 1;

            if (modules.size() > 0) {
                HashMap<String, Double> attributes = new HashMap<>();
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                tooltip.add(index++, Component.literal(I18n.get("item.module")).withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.module.damage") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("meleeDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.meleeDamage") + " ").append(Component.literal((int) (attributes.get("meleeDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("remoteDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.remoteDamage") + " ").append(Component.literal((int) (attributes.get("remoteDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("arrowDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.arrowDamage") + " ").append(Component.literal((int) (attributes.get("arrowDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("projectileDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.projectileDamage") + " ").append(Component.literal((int) (attributes.get("projectileDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("magicDamage", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.magicDamage") + " ").append(Component.literal((int) (attributes.get("magicDamage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.baseDamageWhenNotCriticalStrike") + " ").append(Component.literal((int) (attributes.get("baseDamageWhenNotCriticalStrike") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackRange", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackRange") + " ").append(Component.literal(String.format("%.1f", attributes.get("attackRange")) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("bursting_radius", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.bursting_radius") + " ").append(Component.literal(String.format("%.1f", 1 + attributes.get("bursting_radius") * 2) + "m").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("attackSpeed", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.attackSpeed") + " ").append(Component.literal(String.format("%.1f", attributes.get("attackSpeed") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }
                if (attributes.getOrDefault("firing_rate", 0.0) != 0) {
                    if (itemStack.getItem() instanceof BowItem) {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) (attributes.get("firing_rate") * 2 * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    } else {
                        tooltip.add(index++, Component.literal(I18n.get("item.module.firing_rate") + " ").append(Component.literal((int) (attributes.get("firing_rate") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                    }
                }

                double baseCriticalStrikeProbability = getBaseAttribute(itemStack, "criticalStrikeProbability");
                double meleeCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));
                double remoteCriticalStrikeProbability = baseCriticalStrikeProbability * (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeProbability") + " ").append(Component.literal((int) (meleeCriticalStrikeProbability * 100) + "% / " + (int) (remoteCriticalStrikeProbability * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                double baseCriticalStrikeMultiplier = getBaseAttribute(itemStack, "criticalStrikeMultiplier");
                double meleeCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
                double remoteCriticalStrikeMultiplier = baseCriticalStrikeMultiplier * (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));
                tooltip.add(index++, Component.literal(I18n.get("item.module.criticalStrikeMultiplier") + " ").append(Component.literal("x" + String.format("%.1f", meleeCriticalStrikeMultiplier) + " / x" + String.format("%.1f", remoteCriticalStrikeMultiplier)).withStyle(net.minecraft.ChatFormatting.GRAY)));

                if (attributes.getOrDefault("multishot", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.multishot") + " ").append(Component.literal((int) (attributes.get("multishot") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                tooltip.add(index++, Component.literal(I18n.get("item.module.triggerChance") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "triggerChance") * (1 + attributes.getOrDefault("triggerChance", 0.0)) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));

                if (attributes.getOrDefault("triggerTime", 0.0) != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerTime") + " ").append(Component.literal((int) ((1 + attributes.get("triggerTime")) * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                double elementDamage = 0;
                elementDamage += attributes.getOrDefault("fire", 0.0);
                elementDamage += attributes.getOrDefault("ice", 0.0);
                elementDamage += attributes.getOrDefault("poison", 0.0);
                elementDamage += attributes.getOrDefault("electricity", 0.0);
                elementDamage += attributes.getOrDefault("slash", 0.0);
                elementDamage += attributes.getOrDefault("puncture", 0.0);
                elementDamage += attributes.getOrDefault("impact", 0.0);
                elementDamage += attributes.getOrDefault("gas", 0.0);
                elementDamage += attributes.getOrDefault("radiation", 0.0);
                elementDamage += attributes.getOrDefault("magnetic", 0.0);
                elementDamage += attributes.getOrDefault("corrosion", 0.0);
                elementDamage += attributes.getOrDefault("explosion", 0.0);
                elementDamage += attributes.getOrDefault("virus", 0.0);

                if (elementDamage != 0) {
                    tooltip.add(index++, Component.literal(I18n.get("item.module.triggerDamage") + " ").append(Component.literal((int) (elementDamage * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                }

                HashMap<String, String> elements = getTriggerElements(itemStack);
                if (elements.size() > 0) {
                    Component triggerElements = Component.literal(I18n.get("item.module.triggerType") + " ").withStyle(net.minecraft.ChatFormatting.WHITE);
                    for (String element : elements.keySet()) {
                        triggerElements = triggerElements.copy().append(Component.literal(I18n.get("kuvaweapon.type." + element) + elements.get(element) + " ").withStyle(KuvaWeapon.getColor(element), net.minecraft.ChatFormatting.BOLD));
                    }
                    tooltip.add(index++, triggerElements);
                }

                tooltip.add(index++, Component.translatable("kuvaweapon.item_module_info").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
                List<ItemStack> itemStacks = getModules(itemStack);
                for (ItemStack module : itemStacks) {
                    tooltip.add(index++, Component.literal(" - ").append(module.getHoverName()).append(" ").withStyle(net.minecraft.ChatFormatting.WHITE));
                }
            } else {
                tooltip.add(index++, Component.translatable("item.base").withStyle(net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD));
                tooltip.add(index++, Component.literal(I18n.get("item.base.damage") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "damage") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeProbability") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "criticalStrikeProbability") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.criticalStrikeMultiplier") + " ").append(Component.literal("x" + getBaseAttribute(itemStack, "criticalStrikeMultiplier")).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
                tooltip.add(index++, Component.literal(I18n.get("item.base.triggerChance") + " ").append(Component.literal((int) (getBaseAttribute(itemStack, "triggerChance") * 100) + "%").withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.BOLD)));
            }
        }
    }

    public static List<ItemStack> getModules(ItemStack weaponItemStack) {
        List<ItemStack> itemStacks = new ArrayList<>();
        var nbt = weaponItemStack.getTag();
        if (nbt == null) return itemStacks;

        var weaponModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        var itemList = weaponModule.getList("modules", 10);

        for (int i = 0; i < 8; i++) {
            var itemTag = itemList.getCompound(i);
            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty()) {
                itemStacks.add(stack);
            }
        }
        return itemStacks;
    }

    public static boolean hasBase(ItemStack itemStack) {
        var nbt = itemStack.getTag();
        return nbt != null && nbt.contains(Reference.MOD_ID + "_weaponModules");
    }

    public static double getBaseAttribute(ItemStack itemStack, String attributeType) {
        var nbt = itemStack.getTag();
        if (nbt == null) return 0;

        var kuvalichModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");
        return kuvalichModule.getDouble(attributeType);
    }

    public static void setBaseAttribute(ItemStack itemStack) {
        var nbt = itemStack.getOrCreateTag();
        var weaponModule = nbt.getCompound(Reference.MOD_ID + "_weaponModules");

        if (RandomUtil.percentageChance(0.1)) {
            weaponModule.putDouble("damage", RandomUtil.getInt(150, 200) / 100.0);
            weaponModule.putDouble("criticalStrikeProbability", RandomUtil.getInt(40, 60) / 100.0);
            weaponModule.putDouble("criticalStrikeMultiplier", RandomUtil.getInt(30, 40) / 10.0);
            weaponModule.putDouble("triggerChance", RandomUtil.getInt(30, 40) / 100.0);
        } else if (RandomUtil.percentageChance(0.1)) {
            weaponModule.putDouble("damage", RandomUtil.getInt(50, 80) / 100.0);
            weaponModule.putDouble("criticalStrikeProbability", RandomUtil.getInt(5, 10) / 100.0);
            weaponModule.putDouble("criticalStrikeMultiplier", RandomUtil.getInt(12, 15) / 10.0);
            weaponModule.putDouble("triggerChance", RandomUtil.getInt(5, 10) / 100.0);
        } else {
            double damage = RandomUtil.getInt(80, 120) / 100.0;
            if (RandomUtil.percentageChance(60)) {
                damage = 1.0;
            }

            double criticalStrikeProbability = RandomUtil.getInt(10, 25) / 100.0;
            if (RandomUtil.percentageChance(30)) {
                criticalStrikeProbability = RandomUtil.getInt(25, 40) / 100.0;
            }

            double criticalStrikeMultiplier = RandomUtil.getInt(18, 25) / 10.0;
            if (criticalStrikeProbability >= 0.3) {
                criticalStrikeMultiplier = RandomUtil.getInt(25, 30) / 10.0;
                damage = RandomUtil.getInt(80, 90) / 100.0;
            } else if (criticalStrikeProbability <= 0.15) {
                if (RandomUtil.percentageChance(10)) {
                    criticalStrikeMultiplier = RandomUtil.getInt(30, 35) / 10.0;
                    damage = RandomUtil.getInt(70, 80) / 100.0;
                }
            }

            double triggerChance = RandomUtil.getInt(5, 20) / 100.0;
            if (criticalStrikeProbability > 0.3 && RandomUtil.percentageChance(60)) {
                triggerChance = RandomUtil.getInt(1, 5) / 100.0;
            } else if (criticalStrikeProbability <= 0.15 && RandomUtil.percentageChance(60)) {
                criticalStrikeMultiplier = RandomUtil.getInt(25, 30) / 10.0;
                damage = RandomUtil.getInt(110, 120) / 100.0;
            }

            weaponModule.putDouble("damage", damage);
            weaponModule.putDouble("criticalStrikeProbability", criticalStrikeProbability);
            weaponModule.putDouble("criticalStrikeMultiplier", criticalStrikeMultiplier);
            weaponModule.putDouble("triggerChance", triggerChance);
        }

        nbt.put(Reference.MOD_ID + "_weaponModules", weaponModule);
        itemStack.setTag(nbt);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        DamageSource damageSource = evt.getSource();
        if (!evt.getEntity().level().isClientSide() && (damageSource.getDirectEntity() instanceof Player || damageSource.getEntity() instanceof Player)) {
            Player player;
            if (damageSource.getDirectEntity() instanceof Player) {
                player = (Player) damageSource.getDirectEntity();
            } else if (damageSource.getEntity() instanceof Player) {
                player = (Player) damageSource.getEntity();
            } else {
                player = null;
            }
            if (player != null && EntityLivingUtil.getTicksSinceLastSwing(player) >= 0.25) {
                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                    processDamage(evt, player, weapon, damageSource);
                } else if (ModConfig.KUVA_LICH.damageDisplay.get()) {
                    displayDamage(evt, player);
                }
            }
        }
    }

    private static void processDamage(LivingDamageEvent evt, Player player, ItemStack weapon, DamageSource damageSource) {
        LivingEntity hurter = evt.getEntity();
        double baseDamage = 1;

        double criticalStrikeProbability = getBaseAttribute(weapon, "criticalStrikeProbability") * 100 * EntityLivingUtil.getTicksSinceLastSwing(player);
        double criticalStrikeMultiplier = getBaseAttribute(weapon, "criticalStrikeMultiplier");
        double triggerChance = getBaseAttribute(weapon, "triggerChance") * 100;

        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }

        applyKillStackEffects(player, weapon, attributes, hurter);

        if (damageSource.getDirectEntity() instanceof Player) {
            baseDamage += attributes.getOrDefault("meleeDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("meleeCriticalStrikeProbability", 0.0));

            if (attributes.containsKey("killStackMeleeCriticalMultiplier")) {
                int stacks = KillStackManager.getStacks(player, StackType.MELEE_CRIT_MULT);
                double stackValue = attributes.get("killStackMeleeCriticalMultiplier");
                criticalStrikeMultiplier *= (1 + stackValue * stacks);
            }

            if (player.isSprinting()) {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0) + attributes.getOrDefault("dashMeleeCriticalStrikeProbability", 0.0));
            } else {
                criticalStrikeMultiplier *= (1 + attributes.getOrDefault("meleeCriticalStrikeMultiplier", 0.0));
            }
        } else if (damageSource.getEntity() instanceof Player) {
            baseDamage += attributes.getOrDefault("remoteDamage", 0.0);
            criticalStrikeProbability *= (1 + attributes.getOrDefault("remoteCriticalStrikeProbability", 0.0));
            criticalStrikeMultiplier *= (1 + attributes.getOrDefault("remoteCriticalStrikeMultiplier", 0.0));

            if (damageSource.getDirectEntity() instanceof Arrow) {
                baseDamage += attributes.getOrDefault("arrowDamage", 0.0);
            } else if (damageSource.getDirectEntity() instanceof Projectile) {
                baseDamage += attributes.getOrDefault("projectileDamage", 0.0);
            }

            double range = 1 + attributes.getOrDefault("bursting_radius", 0.0) * 2;
            if (attributes.containsKey("killStackBurstingRadius")) {
                int stacks = KillStackManager.getStacks(player, StackType.BURSTING_RADIUS);
                double stackValue = attributes.get("killStackBurstingRadius");
                range += stackValue * stacks * 2;
            }

            if (!damageSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
                if (range > 1) {
                    List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                            e -> !e.equals(hurter) && !e.equals(player));
                    for (LivingEntity entity : entities) {
                        entity.hurt(player.damageSources().playerAttack(player), evt.getAmount() * 0.5f);
                    }
                }
            } else if (range < 0) {
                evt.setCanceled(true);
                return;
            }
        }

        if (damageSource.getMsgId().contains("magic") || damageSource.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            baseDamage += attributes.getOrDefault("magicDamage", 0.0);
        }

        if (player.isSprinting()) {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0) + attributes.getOrDefault("dashTriggerChance", 0.0));
        } else {
            triggerChance *= (1 + attributes.getOrDefault("triggerChance", 0.0));
        }

        if (attributes.containsKey("killStackTriggerChance")) {
            int stacks = KillStackManager.getStacks(player, StackType.TRIGGER_CHANCE);
            double stackValue = attributes.get("killStackTriggerChance");
            triggerChance *= (1 + stackValue * stacks);
        }

        double elementDamage = 0;
        elementDamage += attributes.getOrDefault("fire", 0.0);
        elementDamage += attributes.getOrDefault("ice", 0.0);
        elementDamage += attributes.getOrDefault("poison", 0.0);
        elementDamage += attributes.getOrDefault("electricity", 0.0);
        elementDamage += attributes.getOrDefault("slash", 0.0);
        elementDamage += attributes.getOrDefault("puncture", 0.0);
        elementDamage += attributes.getOrDefault("impact", 0.0);
        elementDamage += attributes.getOrDefault("gas", 0.0);
        elementDamage += attributes.getOrDefault("radiation", 0.0);
        elementDamage += attributes.getOrDefault("magnetic", 0.0);
        elementDamage += attributes.getOrDefault("corrosion", 0.0);
        elementDamage += attributes.getOrDefault("explosion", 0.0);
        elementDamage += attributes.getOrDefault("virus", 0.0);

        if (getBaseAttribute(weapon, "damage") > 0) {
            if (getBaseAttribute(weapon, "damage") >= 1) {
                baseDamage *= getBaseAttribute(weapon, "damage");
                elementDamage *= getBaseAttribute(weapon, "damage");
            } else {
                baseDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
                elementDamage *= Math.pow(getBaseAttribute(weapon, "damage"), 2);
            }
        }

        int color = DamageInfo.DamageColor.WHITE.getColor();
        if (criticalStrikeProbability > 300) {
            baseDamage *= criticalStrikeMultiplier * 3;
            color = DamageInfo.DamageColor.RED.getColor();
        } else if (criticalStrikeProbability > 200) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 200)) {
                baseDamage *= criticalStrikeMultiplier * 3;
                color = DamageInfo.DamageColor.RED.getColor();
            } else {
                baseDamage *= criticalStrikeMultiplier * 2;
                color = DamageInfo.DamageColor.ORANGE.getColor();
            }
        } else if (criticalStrikeProbability > 100) {
            if (RandomUtil.percentageChance(criticalStrikeProbability - 100)) {
                baseDamage *= criticalStrikeMultiplier * 2;
                color = DamageInfo.DamageColor.ORANGE.getColor();
            } else {
                baseDamage *= criticalStrikeMultiplier;
                color = DamageInfo.DamageColor.YELLOW.getColor();
            }
        } else {
            if (RandomUtil.percentageChance(criticalStrikeProbability)) {
                baseDamage *= criticalStrikeMultiplier;
                color = DamageInfo.DamageColor.YELLOW.getColor();
            } else {
                if (hurter.getAbsorptionAmount() > 0) {
                    color = DamageInfo.DamageColor.BLUE.getColor();
                }
                baseDamage += attributes.getOrDefault("baseDamageWhenNotCriticalStrike", 0.0);
            }
        }

        float damage = evt.getAmount();
        damage = (float) (damage * baseDamage + damage * elementDamage);

        if (hurter.getMobType().equals(MobType.UNDEFINED)) {
            damage *= 1 + (attributes.getOrDefault("bane_of_undefined", 0.0));
        } else if (hurter.getMobType().equals(MobType.UNDEAD)) {
            damage *= 1 + (attributes.getOrDefault("bane_of_undead", 0.0));
        } else if (hurter.getMobType().equals(MobType.ARTHROPOD)) {
            damage *= 1 + (attributes.getOrDefault("bane_of_arthropod", 0.0));
        } else {
            damage *= 1 + (attributes.getOrDefault("bane_of_illager", 0.0));
        }

        double triggerTime = 1 + (attributes.getOrDefault("triggerTime", 0.0));
        if (triggerChance > 100) {
            int number = (int) triggerChance / 100;
            for (int i = 0; i < number; i++) {
                damage = triggerElementEffect(damageSource, hurter, player, weapon, damage, triggerTime);
            }
            if (RandomUtil.percentageChance(triggerChance - number * 100)) {
                damage = triggerElementEffect(damageSource, hurter, player, weapon, damage, triggerTime);
            }
        } else if (RandomUtil.percentageChance(triggerChance)) {
            damage = triggerElementEffect(damageSource, hurter, player, weapon, damage, triggerTime);
        }

        damage = Math.max(damage, 0);
        evt.setAmount(damage);

        if (damage > 0 && !Float.isNaN(damage) && !Float.isInfinite(damage)) {
            double entityWidth = hurter.getBbWidth();
            double entityHeight = hurter.getBbHeight();  // ✅ 使用实体总高度
            double entityY = hurter.getY();

            double offsetX = (Math.random() - 0.5) * entityWidth * 1.2;
            double offsetZ = (Math.random() - 0.5) * entityWidth * 1.2;

            Vec3 position = new Vec3(
                    hurter.getX() + offsetX,
                    entityY + entityHeight * (-0.2 + Math.random() * 0.4),
                    hurter.getZ() + offsetZ
            );

            KuvaLich.network.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new DamagePacket(damage, position, color)
            );
        }
    }

    private static void displayDamage(LivingDamageEvent evt, Player player) {
        LivingEntity hurter = evt.getEntity();
        float damage = evt.getAmount();

        if (damage > 0 && !Float.isNaN(damage) && !Float.isInfinite(damage)) {
            double entityWidth = hurter.getBbWidth();
            double entityHeight = hurter.getBbHeight();  // ✅ 使用实体总高度
            double entityY = hurter.getY();

            double offsetX = (Math.random() - 0.5) * entityWidth * 1.2;
            double offsetZ = (Math.random() - 0.5) * entityWidth * 1.2;

            Vec3 position = new Vec3(
                    hurter.getX() + offsetX,
                    entityY + entityHeight * (-0.2 + Math.random() * 0.4),
                    hurter.getZ() + offsetZ
            );

            KuvaLich.network.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new DamagePacket(damage, position, DamageInfo.DamageColor.WHITE.getColor())
            );
        }
    }

    private static void addKillStacks(Player player, ItemStack weapon) {
        if (player == null || weapon.isEmpty() || !ItemModule.hasBase(weapon)) {
            return;
        }

        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }

        if (attributes.containsKey("killStackBaseDamage")) {
            KillStackManager.addStack(player, StackType.BASE_DAMAGE);
        }
        if (attributes.containsKey("killStackMultishot")) {
            KillStackManager.addStack(player, StackType.MULTISHOT);
        }
        if (attributes.containsKey("killStackMeleeCriticalMultiplier")) {
            KillStackManager.addStack(player, StackType.MELEE_CRIT_MULT);
        }
        if (attributes.containsKey("killStackTriggerChance")) {
            KillStackManager.addStack(player, StackType.TRIGGER_CHANCE);
        }
        if (attributes.containsKey("killStackAttackRange")) {
            KillStackManager.addStack(player, StackType.ATTACK_RANGE);
        }
        if (attributes.containsKey("killStackAttackSpeed")) {
            KillStackManager.addStack(player, StackType.ATTACK_SPEED);
        }
        if (attributes.containsKey("killStackBurstingRadius")) {
            KillStackManager.addStack(player, StackType.BURSTING_RADIUS);
        }
        if (attributes.containsKey("killStackFiringRate")) {
            KillStackManager.addStack(player, StackType.FIRING_RATE);
        }
    }

    private static void applyKillStackEffects(Player player, ItemStack weapon,
                                              HashMap<String, Double> attributes,
                                              LivingEntity target) {
        if (!ItemModule.hasBase(weapon)) {
            return;
        }

        if (attributes.containsKey("killStackBaseDamage")) {
            int stacks = KillStackManager.getStacks(player, StackType.BASE_DAMAGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackBaseDamage");

                int debuffCount = 0;
                for (MobEffectInstance effect : target.getActiveEffects()) {
                    if (effect.getEffect().getCategory().equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL)) {
                        debuffCount++;
                    }
                }

                if (debuffCount > 0) {
                    double bonusDamage = stackValue * stacks * debuffCount;
                    double currentMeleeDamage = attributes.getOrDefault("meleeDamage", 0.0);
                    double currentRemoteDamage = attributes.getOrDefault("remoteDamage", 0.0);
                    attributes.put("meleeDamage", currentMeleeDamage + bonusDamage);
                    attributes.put("remoteDamage", currentRemoteDamage + bonusDamage);
                }
            }
        }

        if (attributes.containsKey("killStackMultishot")) {
            int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackMultishot");
                double currentMultishot = attributes.getOrDefault("multishot", 0.0);
                attributes.put("multishot", currentMultishot + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackSpeed")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_SPEED);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackSpeed");
                double currentSpeed = attributes.getOrDefault("attackSpeed", 0.0);
                attributes.put("attackSpeed", currentSpeed + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackAttackRange")) {
            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_RANGE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackAttackRange");
                double currentRange = attributes.getOrDefault("attackRange", 0.0);
                attributes.put("attackRange", currentRange + stackValue * stacks);
            }
        }

        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            if (stacks > 0) {
                double stackValue = attributes.get("killStackFiringRate");
                double currentFiringRate = attributes.getOrDefault("firing_rate", 0.0);
                attributes.put("firing_rate", currentFiringRate + stackValue * stacks);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                Player player = evt.player;
                if (player.level().getGameTime() % 20 == 0 && player.isAlive()) {
                    ItemStack weapon = player.getMainHandItem();
                    if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                        HashMap<String, Double> attributes = new HashMap<>();
                        List<ItemStack> modules = getModules(weapon);
                        for (ItemStack module : modules) {
                            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                            }
                        }

                        if (attributes.containsKey("killStackAttackSpeed")) {
                            int stacks = KillStackManager.getStacks(player, StackType.ATTACK_SPEED);
                            if (stacks > 0) {
                                double stackValue = attributes.get("killStackAttackSpeed");
                                double currentSpeed = attributes.getOrDefault("attackSpeed", 0.0);
                                attributes.put("attackSpeed", currentSpeed + stackValue * stacks);
                            }
                        }

                        double attackSpeed = attributes.getOrDefault("attackSpeed", 0.0);
                        if (attackSpeed >= 0.1) {
                            int level = (int) (attackSpeed / 0.1) - 1;
                            HiddenEffectHelper.apply(player, KuvaLichMobEffects.ATTACK_SPEED.get(), 30, level);
                        } else {
                            if (attackSpeed <= -0.1) {
                                int level = (int) (-attackSpeed / 0.1) - 1;
                                HiddenEffectHelper.apply(player, KuvaLichMobEffects.NEGATIVE_ATTACK_SPEED.get(), 30, level);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        if (!evt.getEntity().level().isClientSide()) {
            if (evt.getTarget() instanceof LivingEntity) {
                LivingEntity hurter = (LivingEntity) evt.getTarget();
                Player player = evt.getEntity();

                ItemStack weapon = player.getMainHandItem();
                if (!weapon.isEmpty() && ItemModule.hasBase(weapon)) {
                    if (EntityLivingUtil.getTicksSinceLastSwing(player) <= 0.8) {
                        return;
                    }

                    HashMap<String, Double> attributes = new HashMap<>();
                    List<ItemStack> modules = getModules(weapon);
                    for (ItemStack module : modules) {
                        for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                            attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                        }
                    }

                    double range = attributes.getOrDefault("attackRange", 0.0);
                    if (player.isSprinting()) {
                        range += attributes.getOrDefault("dashAttackRange", 0.0);
                    }
                    if (attributes.containsKey("killStackAttackRange")) {
                        int stacks = KillStackManager.getStacks(player, StackType.ATTACK_RANGE);
                        double stackValue = attributes.get("killStackAttackRange");
                        range += stackValue * stacks;
                    }

                    if (range > 0) {
                        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, range,
                                e -> !e.equals(hurter) && !e.equals(player));
                        for (LivingEntity entity : entities) {
                            float damage = EntityPlayerUtil.getAttackDamage(player, entity);
                            entity.hurt(player.damageSources().playerAttack(player), damage * 0.5f * (float) EntityLivingUtil.getTicksSinceLastSwing(player));
                        }
                    } else if (range < 0) {
                        evt.setCanceled(true);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingEntityUseItem(@Nonnull LivingEvent.LivingTickEvent evt) {
        // 由于ItemInUse事件在1.20.1中变化较大，这部分需要重新实现
        // 暂时保留框架，实际逻辑需要根据新API调整
    }

    public static HashMap<String, String> getTriggerElements(ItemStack weapon) {
        Map<String, Double> elementValues = new LinkedHashMap<>();

        if (KuvaWeapon.hasType(weapon)) {
            String kuvaType = KuvaWeapon.getType(weapon);
            elementValues.put(kuvaType, 1.0);
        }

        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                String key = entry.getKey();
                double value = entry.getValue();
                if (isElemental(key) || isPhysical(key) || isCompound(key)) {
                    elementValues.merge(key, value, Double::sum);
                }
            }
        }

        elementValues.entrySet().removeIf(entry -> entry.getValue() <= 0);

        Map<String, Double> combinedElements = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : elementValues.entrySet()) {
            String currentElement = entry.getKey();
            double currentValue = entry.getValue();

            if (isCompound(currentElement)) {
                combinedElements.merge(currentElement, currentValue, Double::sum);
                continue;
            }

            boolean combined = false;
            for (String existingElement : new ArrayList<>(combinedElements.keySet())) {
                if (isCompound(existingElement)) {
                    continue;
                }

                String compoundElement = getCompoundElement(existingElement, currentElement);
                if (compoundElement != null) {
                    double existingValue = combinedElements.remove(existingElement);
                    combinedElements.merge(compoundElement, existingValue + currentValue, Double::sum);
                    combined = true;
                    break;
                }
            }

            if (!combined) {
                combinedElements.put(currentElement, currentValue);
            }
        }

        double totalValue = combinedElements.values().stream().mapToDouble(Double::doubleValue).sum();
        HashMap<String, String> result = new HashMap<>();
        for (Map.Entry<String, Double> entry : combinedElements.entrySet()) {
            double percentage = (entry.getValue() / totalValue) * 100;
            result.put(entry.getKey(), String.format("%.0f%%", percentage));
        }

        return result;
    }

    private static boolean isElemental(String element) {
        return element.equals("fire") || element.equals("ice") ||
                element.equals("poison") || element.equals("electricity");
    }

    private static boolean isPhysical(String element) {
        return element.equals("slash") || element.equals("puncture") || element.equals("impact");
    }

    private static boolean isCompound(String element) {
        return element.equals("gas") || element.equals("radiation") ||
                element.equals("magnetic") || element.equals("corrosion") ||
                element.equals("explosion") || element.equals("virus");
    }

    private static String getCompoundElement(String first, String second) {
        if ((first.equals("fire") && second.equals("poison")) ||
                (first.equals("poison") && second.equals("fire"))) {
            return "gas";
        } else if ((first.equals("fire") && second.equals("electricity")) ||
                (first.equals("electricity") && second.equals("fire"))) {
            return "radiation";
        } else if ((first.equals("ice") && second.equals("electricity")) ||
                (first.equals("electricity") && second.equals("ice"))) {
            return "magnetic";
        } else if ((first.equals("poison") && second.equals("electricity")) ||
                (first.equals("electricity") && second.equals("poison"))) {
            return "corrosion";
        } else if ((first.equals("fire") && second.equals("ice")) ||
                (first.equals("ice") && second.equals("fire"))) {
            return "explosion";
        } else if ((first.equals("poison") && second.equals("ice")) ||
                (first.equals("ice") && second.equals("poison"))) {
            return "virus";
        }
        return null;
    }

    public static String getTriggerElement(ItemStack weapon) {
        HashMap<String, String> elements = getTriggerElements(weapon);
        if (elements.isEmpty()) {
            return null;
        }

        List<Map.Entry<String, Double>> elementList = new ArrayList<>();
        for (Map.Entry<String, String> entry : elements.entrySet()) {
            double probability = Double.parseDouble(entry.getValue().replace("%", "")) / 100.0;
            elementList.add(new AbstractMap.SimpleEntry<>(entry.getKey(), probability));
        }

        double random = Math.random();
        double cumulativeProbability = 0.0;
        for (Map.Entry<String, Double> entry : elementList) {
            cumulativeProbability += entry.getValue();
            if (random <= cumulativeProbability) {
                return entry.getKey();
            }
        }

        return elementList.get(elementList.size() - 1).getKey();
    }

    public static float triggerElementEffect(DamageSource damageSource, LivingEntity hurter, Player attacker,
                                             ItemStack itemStack, double damage, double triggerTime) {
        String type = getTriggerElement(itemStack);
        if (type == null) {
            return (float) damage;
        }

        Level level = hurter.level();

        switch (type) {
            case "fire": {
                damage *= (EntityUtil.getFire(hurter) > 0 ? 1 : 0.5);
                if (hurter.getAbsorptionAmount() > 0) {
                    damage *= 0.5f;
                }
                if (EntityUtil.getFire(hurter) > 0) {
                    if (hurter.hasEffect(KuvaLichMobEffects.FIRE.get())) {
                        int currentLevel = hurter.getEffect(KuvaLichMobEffects.FIRE.get()).getAmplifier();
                        int newLevel = Math.min(5 - 1, currentLevel + 1);
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.FIRE.get(), (int) (120 * triggerTime), newLevel);
                    } else {
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.FIRE.get(), (int) (120 * triggerTime), 0);
                    }
                } else {
                    if (hurter.hasEffect(KuvaLichMobEffects.FIRE.get())) {
                        int currentLevel = hurter.getEffect(KuvaLichMobEffects.FIRE.get()).getAmplifier();
                        int newLevel = Math.min(5 - 1, currentLevel + 1);
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.FIRE.get(), (int) (60 * triggerTime), newLevel);
                    } else {
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.FIRE.get(), (int) (60 * triggerTime), 0);
                    }
                }
                hurter.setSecondsOnFire((int) (10 * triggerTime));
                break;
            }
            case "poison": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1;
                HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.POISON.get(), (int) (150 * triggerTime), 0);
                break;
            }
            case "ice": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                if (hurter.hasEffect(KuvaLichMobEffects.ICE.get())) {
                    int currentLevel = hurter.getEffect(KuvaLichMobEffects.ICE.get()).getAmplifier();
                    int newLevel = Math.min(90 - 1, currentLevel + 10);
                    HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.ICE.get(), (int) (120 * triggerTime), newLevel);
                } else {
                    HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.ICE.get(), (int) (120 * triggerTime), 10 - 1);
                }
                break;
            }
            case "electricity": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, 5 * triggerTime,
                        e -> !e.equals(hurter) && !e.equals(attacker));
                for (LivingEntity entity : entities) {
                    level.explode(null, entity.getX(), entity.getY(), entity.getZ(), 0, Level.ExplosionInteraction.NONE);
                    net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                    if (lightning != null) {
                        lightning.moveTo(entity.getX(), entity.getY(), entity.getZ());
                        lightning.setVisualOnly(true);
                        level.addFreshEntity(lightning);
                    }

                    entity.hurt(level.damageSources().lightningBolt(), (float) (damage * 0.5f));

                    double offsetX = (Math.random() - 0.5) * entity.getBbWidth();
                    double offsetY = entity.getBbHeight() * 0.25 + (Math.random() * entity.getBbHeight() * 0.75);
                    double offsetZ = (Math.random() - 0.5) * entity.getBbWidth();
                    Vec3 position = new Vec3(entity.getX() + offsetX, entity.getY() + offsetY, entity.getZ() + offsetZ);

                    KuvaLich.network.send(
                            PacketDistributor.PLAYER.with(() -> (ServerPlayer) attacker),
                            new DamagePacket((float) (damage * 0.5f), position, DamageInfo.DamageColor.WHITE.getColor())
                    );
                }
                break;
            }
            case "slash": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.25 : 1.5;

                if (hurter.hasEffect(KuvaLichMobEffects.VIRUS.get())) {
                    int amplifier = hurter.getEffect(KuvaLichMobEffects.VIRUS.get()).getAmplifier();
                    damage = damage + damage * 0.25f * (amplifier + 1);
                }

                float slashDamage = (float) (damage * 0.35f);
                new SynchronizationTask(20, 20) {
                    private int time = 0;

                    @Override
                    public void run() {
                        if (time++ >= 6 * triggerTime || hurter.isDeadOrDying()) {
                            this.cancel();
                            return;
                        }

                        double entityWidth = hurter.getBbWidth();
                        double entityHeight = hurter.getBbHeight();  // ✅ 使用实体总高度
                        double entityY = hurter.getY();

                        double offsetX = (Math.random() - 0.5) * entityWidth * 1.2;
                        double offsetZ = (Math.random() - 0.5) * entityWidth * 1.2;

                        Vec3 position = new Vec3(
                                hurter.getX() + offsetX,
                                entityY + entityHeight * (-0.2 + Math.random() * 0.4),
                                hurter.getZ() + offsetZ
                        );

                        KuvaLich.network.send(
                                PacketDistributor.PLAYER.with(() -> (ServerPlayer) attacker),
                                new DamagePacket(slashDamage, position, DamageInfo.DamageColor.WHITE.getColor())
                        );

                        if (hurter.getHealth() - slashDamage * 2 > 0) {
                            hurter.setHealth(hurter.getHealth() - slashDamage);
                        } else {
                            EntityLivingUtil.kill(hurter, attacker.damageSources().playerAttack(attacker));
                            this.cancel();
                        }
                    }
                }.start();
                break;
            }
            case "puncture": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1.25;
                HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.PUNCTURE.get(), (int) (120 * triggerTime), 0);
                break;
            }
            case "impact": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 1;

                double knockbackX = (hurter.getX() - attacker.getX()) / 6;
                double knockbackZ = (hurter.getZ() - attacker.getZ()) / 6;

                hurter.knockback(1.25f * EntityLivingUtil.getTicksSinceLastSwing(attacker), knockbackX, knockbackZ);
                break;
            }
            case "magnetic": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 2.0 : 0.25;
                HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.MAGNETIC.get(), (int) (120 * triggerTime), 0);
                break;
            }
            case "radiation": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.5 : 1.5;
                damage = damage + hurter.getHealth() * 0.02 + hurter.getMaxHealth() * 0.01;
                break;
            }
            case "virus": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.1 : 1.0;
                if (hurter.getAbsorptionAmount() <= 0) {
                    if (hurter.hasEffect(KuvaLichMobEffects.VIRUS.get())) {
                        int currentLevel = hurter.getEffect(KuvaLichMobEffects.VIRUS.get()).getAmplifier();
                        int newLevel = Math.min(13 - 1, currentLevel + 1);
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.VIRUS.get(), (int) (120 * triggerTime), newLevel);
                    } else {
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.VIRUS.get(), (int) (120 * triggerTime), 4 - 1);
                    }
                }
                break;
            }
            case "corrosion": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 0.1 : 1.25;
                if (hurter.getAbsorptionAmount() <= 0) {
                    if (hurter.hasEffect(KuvaLichMobEffects.CORROSION.get())) {
                        int currentLevel = hurter.getEffect(KuvaLichMobEffects.CORROSION.get()).getAmplifier();
                        int newLevel = Math.min(4 - 1, currentLevel + 1);
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.CORROSION.get(), (int) (160 * triggerTime), newLevel);
                    } else {
                        HiddenEffectHelper.apply(hurter, KuvaLichMobEffects.CORROSION.get(), (int) (160 * triggerTime), 0);
                    }
                }
                break;
            }
            case "explosion": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.75 : 1.5;
                List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, hurter, 6,
                        e -> !e.equals(hurter) && !e.equals(attacker));
                for (LivingEntity entity : entities) {
                    entity.hurt(level.damageSources().explosion((Explosion) null), (float) damage);

                    double offsetX = (Math.random() - 0.5) * entity.getBbWidth();
                    double offsetY = entity.getBbHeight() * 0.25 + (Math.random() * entity.getBbHeight() * 0.75);
                    double offsetZ = (Math.random() - 0.5) * entity.getBbWidth();
                    Vec3 position = new Vec3(entity.getX() + offsetX, entity.getY() + offsetY, entity.getZ() + offsetZ);

                    KuvaLich.network.send(
                            PacketDistributor.PLAYER.with(() -> (ServerPlayer) attacker),
                            new DamagePacket((float) damage, position, DamageInfo.DamageColor.WHITE.getColor())
                    );
                }
                level.explode(null, hurter.getX(), hurter.getY(), hurter.getZ(), 3, Level.ExplosionInteraction.NONE);
                break;
            }
            case "gas": {
                damage *= hurter.getAbsorptionAmount() > 0 ? 1.5 : 0.5;
                EntityUtil.getNearbyEntities(LivingEntity.class, attacker, RandomUtil.getInt(3, 6),
                        e -> !e.equals(attacker)).forEach((entity) -> {
                    if (entity.hasEffect(KuvaLichMobEffects.FIRE.get())) {
                        int currentLevel = entity.getEffect(KuvaLichMobEffects.FIRE.get()).getAmplifier();
                        int newLevel = Math.min(5 - 1, currentLevel + 1);
                        HiddenEffectHelper.apply(entity, KuvaLichMobEffects.FIRE.get(), (int) (120 * triggerTime), newLevel);
                    } else {
                        HiddenEffectHelper.apply(entity, KuvaLichMobEffects.FIRE.get(), (int) (120 * triggerTime), 0);
                    }
                });
                break;
            }
            default: {
                break;
            }
        }
        return (float) damage;
    }

    @SubscribeEvent
    public static void onLivingEntityUseItemTick(@Nonnull LivingEntityUseItemEvent.Tick evt) {
        LivingEntity entity = evt.getEntity();

        if (!(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;
        ItemStack usingItem = evt.getItem();

        if (usingItem.isEmpty()) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !ItemModule.hasBase(weapon)) {
            return;
        }

        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);

        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }

        // ✅ 弓和弩都翻倍
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) {
            firingRate *= 2.0;
        }

        if (Math.abs(firingRate) < 0.001) {
            return;
        }

        if (firingRate < 0) {
            double slowRate = Math.abs(firingRate);

            if (slowRate >= 1.0) {
                evt.setDuration(evt.getDuration() + 1);
                return;
            }

            if (RandomUtil.percentageChance(slowRate * 100)) {
                evt.setDuration(evt.getDuration() + 1);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTickForFiringRate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entity = evt.getEntity();

        if (!(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;

        if (!entity.isUsingItem()) {
            return;
        }

        ItemStack usingItem = entity.getUseItem();

        if (usingItem.isEmpty()) {
            return;
        }

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !ItemModule.hasBase(weapon)) {
            return;
        }

        HashMap<String, Double> attributes = new HashMap<>();
        List<ItemStack> modules = getModules(weapon);
        for (ItemStack module : modules) {
            for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }

        double firingRate = attributes.getOrDefault("firing_rate", 0.0);

        if (attributes.containsKey("killStackFiringRate")) {
            int stacks = KillStackManager.getStacks(player, StackType.FIRING_RATE);
            double stackValue = attributes.get("killStackFiringRate");
            firingRate += stackValue * stacks;
        }

        // ✅ 弓和弩都翻倍
        if (usingItem.getItem() instanceof BowItem || usingItem.getItem() instanceof CrossbowItem) {
            firingRate *= 2.0;
        }

        if (firingRate <= 0) {
            return;
        }

        int extraUpdates = (int) firingRate;

        for (int i = 0; i < extraUpdates; i++) {
            EntityLivingUtil.updateHeld(entity);
        }

        double fractionalPart = firingRate - extraUpdates;
        if (fractionalPart > 0 && RandomUtil.percentageChance(fractionalPart * 100)) {
            EntityLivingUtil.updateHeld(entity);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent evt) {
        Player player = evt.getEntity();
        if (!evt.getEntity().level().isClientSide()) {
            ItemStack bow = evt.getBow();
            if (!bow.isEmpty() && ItemModule.hasBase(bow)) {
                HashMap<String, Double> attributes = new HashMap<>();
                List<ItemStack> modules = getModules(bow);
                for (ItemStack module : modules) {
                    for (Map.Entry<String, Double> entry : ModuleBase.getAttributes(module)) {
                        attributes.put(entry.getKey(), attributes.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
                    }
                }

                double multishot = attributes.getOrDefault("multishot", 0.0);
                if (attributes.containsKey("killStackMultishot")) {
                    int stacks = KillStackManager.getStacks(player, StackType.MULTISHOT);
                    double stackValue = attributes.get("killStackMultishot");
                    multishot += stackValue * stacks;
                }

                if (multishot > 0) {
                    int charge = evt.getCharge();
                    float velocity = getBowVelocity(charge);
                    if (velocity < 0.1f) {
                        return;
                    }
                    if (multishot > 1) {
                        int number = (int) multishot;
                        for (int i = 0; i < number; i++) {
                            fireArrow(player, player.level(), velocity, bow, true);
                        }
                        if (RandomUtil.percentageChance((multishot - number) * 100)) {
                            fireArrow(player, player.level(), velocity, bow, true);
                        }
                    } else {
                        if (RandomUtil.percentageChance(multishot * 100)) {
                            fireArrow(player, player.level(), velocity, bow, true);
                        }
                    }
                } else if (multishot < 0 && multishot > -1) {
                    if (RandomUtil.percentageChance(Math.abs(multishot) * 100)) {
                        evt.setCanceled(true);
                    }
                } else if (multishot <= -1) {
                    evt.setCanceled(true);
                }
            }
        }
    }

    private static float getBowVelocity(int charge) {
        float f = (float) charge / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    private static void fireArrow(Player player, Level level, float velocity, ItemStack bow, boolean infiniteArrows) {
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        ArrowItem arrowItem = (arrowStack.getItem() instanceof ArrowItem) ? (ArrowItem) arrowStack.getItem() : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, arrowStack, player);

        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity * 3.0F, 5.0F);
        arrow.addTag("multishot");

        if (velocity == 1.0F) {
            arrow.setCritArrow(true);
        }

        applyBowEnchantments(arrow, bow);

        if (infiniteArrows) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }

        level.addFreshEntity(arrow);
    }

    private static void applyBowEnchantments(AbstractArrow arrow, ItemStack bow) {
        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() + (double) power * 0.5D + 0.5D);
        }

        int knockback = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (knockback > 0) {
            arrow.setKnockback(knockback);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }
    }
}