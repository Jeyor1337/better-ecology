package me.javavirtualenv.item;

import me.javavirtualenv.BetterEcology;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.food.FoodProperties;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;

/**
 * Custom items for the Better Ecology mod.
 * Includes truffles that pigs can find while rooting.
 */
public final class ModItems {

    private ModItems() {
        throw new AssertionError("ModItems should not be instantiated");
    }

    public static final Item TRUFFLE = new Item(new Item.Properties()
            .rarity(Rarity.RARE)
            .food(new FoodProperties.Builder()
                    .nutrition(6)
                    .saturationModifier(0.6f)
                    .build())) {
        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.better-ecology.truffle.tooltip"));
        }
    };

    public static void register() {
        registerItem("truffle", TRUFFLE);
        addToItemGroups();
        BetterEcology.LOGGER.info("Registered mod items");
    }

    private static void registerItem(String name, Item item) {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(BetterEcology.MOD_ID, name), item);
    }

    private static void addToItemGroups() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS)
            .register(content -> {
                content.accept(TRUFFLE);
            });
    }
}
