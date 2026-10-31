package pers.roinflam.kuvalich.client.fx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.item.Endo;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.LichReliquary;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.ShadowForma;
import pers.roinflam.kuvalich.item.module.warframe.WarframeCommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframePrimeModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponCommonModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponPrimeModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRareModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponUncommonModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;
import pers.roinflam.kuvalich.world.inventory.RequiemRecastMenu;

/**
 * 玩家从安魂之融 / 安魂之铸的输出格取东西时，判断这是哪一种操作，好放对应的演出和声音
 *
 * <p>只在本人客户端判，不改菜单类，也不发包：判错了顶多放错一次特效。
 * 判的顺序照抄 {@code RequiemEvolveMenu#processCrafting} 的分支顺序（暗影塑形块在普通塑形块前面，
 * 它是塑形块的子类）。</p>
 *
 * <p>安魂之融有两类操作，结果格的来历不一样：</p>
 * <ul>
 *   <li><b>预览类</b>（融合、紫卡循环、模组升级）：输入还留在格里，结果格是预览，输入一变结果就清掉重算，
 *       所以按<b>当前</b>输入判；</li>
 *   <li><b>当场处理类</b>（塑形块、暗影塑形块、开光、TACZ 枪械强化）：放进去那一刻服务端就把武器挪进结果格、
 *       材料扣掉一个，武器格空了，材料可能也用光了；之后换材料，结果格不会跟着变。所以这几类按
 *       <b>结果出现那一刻</b>材料格里是什么来判，这个由界面自己记下来传进来。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ForgeOpClassifier {

    private ForgeOpClassifier() {
    }

    /** 工作台操作 */
    public enum Op {
        NONE,
        /** 两把赤毒武器融合 */
        FUSION,
        /** 裂罅碎块开光 */
        IMBUE,
        /** 塑形块 */
        FORMA,
        /** 暗影塑形块 */
        SHADOW_FORMA,
        /** 紫卡循环（武器、战甲紫卡都算） */
        RIVEN_CYCLE,
        /** TACZ 枪械强化，演出同开光 */
        GUN_ENHANCE,
        /** 模组升级 */
        RANK_UP,
        /** 升到满级 */
        MAX_RANK,
        /** 三合一（同品质、跨品质、Prime、紫卡、安魂卡） */
        MERGE,
        /** 单张模组分解成内融核心 */
        DECOMPOSE
    }

    /** 安魂之融的格子：0 武器 / 模组，1 材料，2 结果 */
    public static final int EVOLVE_INPUT = 0;
    public static final int EVOLVE_MATERIAL = 1;
    public static final int EVOLVE_RESULT = 2;
    /** 安魂之铸的格子：0~2 输入，3 输出 */
    public static final int RECAST_OUTPUT = 3;

    /**
     * 安魂之融结果格里这件东西是哪种操作做出来的
     *
     * @param madeWith 结果出现那一刻材料格里的物品（材料当时已经用光时，是之前最后一次看到的材料）；不知道传 null
     */
    public static Op classify(RequiemEvolveMenu menu, @Nullable Item madeWith) {
        ItemStack input = menu.getSlot(EVOLVE_INPUT).getItem();
        ItemStack material = menu.getSlot(EVOLVE_MATERIAL).getItem();
        ItemStack result = menu.getSlot(EVOLVE_RESULT).getItem();
        if (result.isEmpty()) {
            return Op.NONE;
        }
        if (input.isEmpty()) {
            // 当场处理类：以结果出现时记下的材料为准
            Item made = madeWith != null ? madeWith : (material.isEmpty() ? null : material.getItem());
            return byMaterial(made);
        }
        // 预览类：输入都还在。紫卡循环会把赤毒扣到 0，那时材料格是空的，用记下的那个补上
        Item mat = !material.isEmpty() ? material.getItem() : madeWith;
        if (KuvaWeaponUtil.hasType(input) && KuvaWeaponUtil.hasType(material)) {
            return Op.FUSION;
        }
        Item in = input.getItem();
        if ((in instanceof WeaponRivenModule || in instanceof WarframeRivenModule) && mat instanceof Kuva) {
            return Op.RIVEN_CYCLE;
        }
        Op immediate = byMaterial(mat);
        if (immediate != Op.NONE) {
            return immediate;
        }
        if (mat instanceof Endo && in instanceof AbstractModule) {
            return ModuleLevelHelper.isMaxLevel(result) ? Op.MAX_RANK : Op.RANK_UP;
        }
        return Op.NONE;
    }

    /** 当场处理的四类，只看材料是什么 */
    private static Op byMaterial(@Nullable Item m) {
        if (m instanceof ShadowForma) {
            return Op.SHADOW_FORMA;
        }
        if (m instanceof Forma) {
            return Op.FORMA;
        }
        if (m instanceof RivenSliver) {
            return Op.IMBUE;
        }
        if (m instanceof LichReliquary) {
            return Op.GUN_ENHANCE;
        }
        return Op.NONE;
    }

    /**
     * 安魂之铸输出格里这件东西是哪种操作做出来的：输出是内融核心、输入里正好一张模组卡 = 分解预览；
     * 其余都是三合一（三合一当场就把三张输入扣掉了，之后玩家往输入里放什么都不影响输出格）
     */
    public static Op classify(RequiemRecastMenu menu) {
        ItemStack out = menu.getSlot(RECAST_OUTPUT).getItem();
        if (out.isEmpty()) {
            return Op.NONE;
        }
        if (out.getItem() instanceof Endo && singleModuleInput(menu) >= 0) {
            return Op.DECOMPOSE;
        }
        return Op.MERGE;
    }

    /** 安魂之铸三个输入格里正好只有一张模组卡（数量 1）时返回它的格号，否则 -1。和菜单判分解的条件一致 */
    public static int singleModuleInput(RequiemRecastMenu menu) {
        int found = -1;
        for (int i = 0; i < RECAST_OUTPUT; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (s.isEmpty()) {
                continue;
            }
            if (found >= 0) {
                return -1;
            }
            found = i;
        }
        if (found < 0) {
            return -1;
        }
        ItemStack card = menu.getSlot(found).getItem();
        boolean module = card.getItem() instanceof AbstractWeaponModule || card.getItem() instanceof AbstractWarframeModule;
        return module && card.getCount() == 1 ? found : -1;
    }

    /**
     * 这次点击会不会把输出格里的东西拿走：普通左右键（手上空着或能叠上去）、Shift 点击、
     * 数字键换到空的快捷栏 / 副手格
     */
    public static boolean takesOutput(Slot out, ItemStack carried, int button, ClickType type) {
        if (!out.hasItem()) {
            return false;
        }
        switch (type) {
            case PICKUP -> {
                if (carried.isEmpty()) {
                    return true;
                }
                return ItemStack.isSameItemSameTags(carried, out.getItem()) && carried.getCount() < carried.getMaxStackSize();
            }
            case QUICK_MOVE -> {
                return true;
            }
            case SWAP -> {
                Player player = Minecraft.getInstance().player;
                if (player == null || button < 0 || (button > 8 && button != 40)) {
                    return false;
                }
                return player.getInventory().getItem(button).isEmpty();
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 模组稀有度：0 铜 / 1 银 / 2 金 / 3 Prime / 4 紫卡；不是模组返回 -1。
     * 判的顺序和安魂之铸三合一判品质的顺序一致
     */
    public static int rarity(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        Item it = stack.getItem();
        if (it instanceof WeaponRivenModule || it instanceof WarframeRivenModule) {
            return 4;
        }
        if (it instanceof WeaponPrimeModule || it instanceof WarframePrimeModule) {
            return 3;
        }
        if (it instanceof WeaponRareModule || it instanceof WarframeRareModule) {
            return 2;
        }
        if (it instanceof WeaponUncommonModule || it instanceof WarframeUncommonModule) {
            return 1;
        }
        if (it instanceof WeaponCommonModule || it instanceof WarframeCommonModule) {
            return 0;
        }
        return -1;
    }

    /** 塑形块这次掷中了锁定（看结果物品上的锁定标记） */
    public static boolean formaLocked(ItemStack result) {
        return !result.isEmpty() && WeaponModuleHandler.isFormaLocked(result);
    }

    /** 模组等级（升级后的结果从这里读新等级） */
    public static int moduleLevel(ItemStack module) {
        return ModuleLevelHelper.getModuleLevel(module);
    }

    /**
     * 军械库（武器 / 战甲）的模组格差分：每 tick 和上一 tick 的快照比，认出装入、取出、武器上台
     *
     * <p>不挂在点击上，是因为装卸的路子太多：点击、Shift 点击、数字键、图鉴一键装配（从图鉴回来时才看到变化），
     * 比快照一处就全覆盖了。几条规则：</p>
     * <ul>
     *   <li>菜单的初始内容到了才建第一份快照（{@code getStateId()} 不再是 0），只建不放，
     *       免得打开界面时同步过来的模组被当成一排「装入」；</li>
     *   <li>武器格 空→有 的那一 tick 只放「武器上台」，模组格那一排是从武器上读出来的，不算装入；</li>
     *   <li>武器格是空的时候模组格的任何变化都不算：取下武器时服务端清空模组格的同步要晚一两个 tick 才到，
     *       没有武器本来也装不上模组；</li>
     *   <li>模组格 空→有 = 装入，有→空 = 取出，有→另一张 = 装入。同种模组是同一个物品、靠 NBT 区分，
     *       所以「另一张」连 NBT 一起比（只比哈希）。</li>
     * </ul>
     */
    public static final class TableWatch {

        private final int modules;
        private final int weaponSlot;
        @Nullable
        private final int[] pathToWeapon;
        private final Item[] items;
        /** 上次看到的 NBT 对象和它的哈希：对象没换就不重算（CompoundTag 算哈希要遍历一遍） */
        private final CompoundTag[] tagRef;
        private final int[] tagHash;
        private final int[] rarity;
        private boolean weapon;
        private boolean ready;

        /**
         * @param modules      模组格个数（菜单里的 0 ~ modules-1）
         * @param weaponSlot   武器格的菜单格号，没有武器格传 -1
         * @param pathToWeapon 每个模组格到武器格的路径号（{@link GuiFx#path}），没有武器格传 null
         */
        public TableWatch(int modules, int weaponSlot, @Nullable int[] pathToWeapon) {
            this.modules = modules;
            this.weaponSlot = weaponSlot;
            this.pathToWeapon = pathToWeapon;
            this.items = new Item[modules];
            this.tagRef = new CompoundTag[modules];
            this.tagHash = new int[modules];
            this.rarity = new int[modules];
        }

        /** 在界面的 {@code containerTick} 里调 */
        public void tick(AbstractContainerMenu menu, GuiFx fx) {
            if (!ready) {
                if (menu.getStateId() == 0) {
                    return;
                }
                snapshot(menu);
                ready = true;
                return;
            }
            if (weaponSlot >= 0) {
                boolean now = menu.getSlot(weaponSlot).hasItem();
                boolean had = weapon;
                if (!had && now) {
                    fx.mount(menu.getSlot(weaponSlot));
                    snapshot(menu);
                    return;
                }
                if (!now) {
                    snapshot(menu);
                    return;
                }
            }
            Slot weaponHolder = weaponSlot >= 0 ? menu.getSlot(weaponSlot) : null;
            for (int i = 0; i < modules; i++) {
                ItemStack s = menu.getSlot(i).getItem();
                Item it = s.isEmpty() ? null : s.getItem();
                int hash = hashOf(i, s);
                if (it != null && (items[i] == null || it != items[i] || hash != tagHash[i])) {
                    int r = ForgeOpClassifier.rarity(s);
                    fx.install(menu.getSlot(i), r, pathToWeapon != null ? pathToWeapon[i] : -1, weaponHolder);
                } else if (it == null && items[i] != null) {
                    fx.remove(menu.getSlot(i), rarity[i]);
                }
                remember(i, s, it, hash);
            }
        }

        private void snapshot(AbstractContainerMenu menu) {
            weapon = weaponSlot < 0 || menu.getSlot(weaponSlot).hasItem();
            for (int i = 0; i < modules; i++) {
                ItemStack s = menu.getSlot(i).getItem();
                remember(i, s, s.isEmpty() ? null : s.getItem(), hashOf(i, s));
            }
        }

        private void remember(int i, ItemStack s, @Nullable Item it, int hash) {
            items[i] = it;
            tagRef[i] = it == null ? null : s.getTag();
            tagHash[i] = hash;
            rarity[i] = it == null ? -1 : ForgeOpClassifier.rarity(s);
        }

        private int hashOf(int i, ItemStack s) {
            CompoundTag tag = s.isEmpty() ? null : s.getTag();
            if (tag == null) {
                return 0;
            }
            return tag == tagRef[i] ? tagHash[i] : tag.hashCode();
        }
    }
}
