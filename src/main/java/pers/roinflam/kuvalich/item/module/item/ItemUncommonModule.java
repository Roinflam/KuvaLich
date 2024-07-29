    package pers.roinflam.kuvalich.item.module.item;

    import net.minecraft.creativetab.CreativeTabs;
    import net.minecraft.entity.item.EntityItem;
    import net.minecraft.entity.player.EntityPlayer;
    import net.minecraft.item.EnumRarity;
    import net.minecraft.item.ItemStack;
    import net.minecraft.util.ActionResult;
    import net.minecraft.util.EnumActionResult;
    import net.minecraft.util.EnumHand;
    import net.minecraft.util.NonNullList;
    import net.minecraft.util.text.TextFormatting;
    import net.minecraft.util.text.translation.I18n;
    import net.minecraft.world.World;
    import org.jetbrains.annotations.NotNull;
    import pers.roinflam.kuvalich.base.item.ItemModuleBase;
    import pers.roinflam.kuvalich.base.item.ModuleBase;
    import pers.roinflam.kuvalich.init.KuvaLichItems;
    import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

    import java.util.ArrayList;
    import java.util.List;

    public class ItemUncommonModule extends ItemModuleBase {
        public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

        public ItemUncommonModule(@NotNull String name) {
            super(name);
        }

        public static ItemStack getRandomModule() {
            ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_UNCOMMON_MODULE);
            itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
            ModuleBase.setRandom(itemStack, true);
            return itemStack;
        }

        @Override
        public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            if (this.isInCreativeTab(tab)) {
                ItemStack itemStack = getRandomModule();
                items.add(itemStack);

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.serration");
                ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.65001f);
                ItemModuleBase.setType(itemStack, "serration");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.speed_trigger");
                ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.6001f);
                ItemModuleBase.setType(itemStack, "speed_trigger");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.molten_impact");
                ItemModuleBase.addAttributes(itemStack, "fire", 0.9001f);
                ItemModuleBase.setType(itemStack, "molten_impact");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.shocking_touch");
                ItemModuleBase.addAttributes(itemStack, "electricity", 0.9001f);
                ItemModuleBase.setType(itemStack, "shocking_touch");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.fever_strike");
                ItemModuleBase.addAttributes(itemStack, "poison", 0.9001f);
                ItemModuleBase.setType(itemStack, "fever_strike");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.north_wind");
                ItemModuleBase.addAttributes(itemStack, "ice", 0.9001f);
                ItemModuleBase.setType(itemStack, "north_wind");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undefined");
                ItemModuleBase.addAttributes(itemStack, "bane_of_undefined", 0.3001f);
                ItemModuleBase.setType(itemStack, "bane_of_undefined");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undead");
                ItemModuleBase.addAttributes(itemStack, "bane_of_undead", 0.3001f);
                ItemModuleBase.setType(itemStack, "bane_of_undead");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_arthropod");
                ItemModuleBase.addAttributes(itemStack, "bane_of_arthropod", 0.3001f);
                ItemModuleBase.setType(itemStack, "bane_of_arthropod");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_illager");
                ItemModuleBase.addAttributes(itemStack, "bane_of_illager", 0.3001f);
                ItemModuleBase.setType(itemStack, "bane_of_illager");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.weapon_aptitude");
                ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.9001f);
                ItemModuleBase.setType(itemStack, "weapon_aptitude");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.fury");
                ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.3001f);
                ItemModuleBase.setType(itemStack, "fury");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.roundhouse_chop");
                ItemModuleBase.addAttributes(itemStack, "attackRange", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
                ItemModuleBase.setType(itemStack, "roundhouse_chop");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.magic_swordsman");
                ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.20001f);
                ItemModuleBase.setType(itemStack, "magic_swordsman");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.triple_whammy");
                ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
                ItemModuleBase.addAttributes(itemStack, "puncture", 0.60001f);
                ItemModuleBase.addAttributes(itemStack, "impact", 0.60001f);
                ItemModuleBase.setType(itemStack, "triple_whammy");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.small_caliber");
                ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.80001f);
                ItemModuleBase.setType(itemStack, "small_caliber");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.stream");
                ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 5.00001f);
                ItemModuleBase.setType(itemStack, "stream");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.sure_shot");
                ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
                ItemModuleBase.setType(itemStack, "sure_shot");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.arrow_fire");
                ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
                ItemModuleBase.addAttributes(itemStack, "fire", 0.90001f);
                ItemModuleBase.setType(itemStack, "arrow_fire");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.arrow_ice");
                ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
                ItemModuleBase.addAttributes(itemStack, "ice", 0.90001f);
                ItemModuleBase.setType(itemStack, "arrow_ice");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.arrow_poison");
                ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
                ItemModuleBase.addAttributes(itemStack, "poison", 0.90001f);
                ItemModuleBase.setType(itemStack, "arrow_poison");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.arrow_electricity");
                ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
                ItemModuleBase.addAttributes(itemStack, "electricity", 0.90001f);
                ItemModuleBase.setType(itemStack, "arrow_electricity");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.cryo_rounds");
                ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "ice", 0.60001f);
                ItemModuleBase.setType(itemStack, "cryo_rounds");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.spring_loaded_chamber");
                ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.55001f);
                ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.75001f);
                ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.6001f);
                ItemModuleBase.setType(itemStack, "serration");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.serrated_rounds");
                ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "slash", 0.9001f);
                ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.6001f);
                ItemModuleBase.setType(itemStack, "serrated_rounds");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.devastated");
                ItemModuleBase.addAttributes(itemStack, "attackRange", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "puncture", 0.6001f);
                ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.6001f);
                ItemModuleBase.setType(itemStack, "devastated");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());

                itemStack = new ItemStack(this);
                itemStack.setTranslatableName("kuvaweapon.item_module.fission_shot");
                ItemModuleBase.addAttributes(itemStack, "multishot", 1.20001f);
                ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.6001f);
                ItemModuleBase.setType(itemStack, "fission_shot");
                items.add(itemStack);
                itemStackList.add(itemStack.copy());
            }
        }

        @Override
        public @NotNull ActionResult<ItemStack> onItemRightClick(World worldIn, @NotNull EntityPlayer playerIn, EnumHand handIn) {
            @NotNull ItemStack itemstack = playerIn.getHeldItem(handIn);
            if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
                ItemStack module = itemStackList.get(RandomUtil.getInt(0, itemStackList.size() - 1));

                EntityItem entityItem = new EntityItem(worldIn, playerIn.posX, playerIn.posY, playerIn.posZ, module);
                worldIn.spawnEntity(entityItem);

                playerIn.setHeldItem(handIn, ItemStack.EMPTY);
                return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
            }
            return super.onItemRightClick(worldIn, playerIn, handIn);
        }

        @Override
        public @NotNull EnumRarity getRarity(@NotNull ItemStack stack) {
            return EnumRarity.UNCOMMON;
        }

        @Override
        public boolean isWarframe() {
            return false;
        }
    }
