package me.javavirtualenv.behavior.fox;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.api.EcologyAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;

/**
 * Component for storing items carried by foxes.
 * <p>
 * Foxes can carry one item at a time in their mouth.
 * The item is visible to players and can be dropped or gifted.
 * <p>
 * This component attaches to fox entities and persists across saves.
 */
public class FoxItemStorage {

    private static final String STORAGE_KEY = "fox_item_storage";
    private static final String ITEM_KEY = "carried_item";

    private final Mob fox;
    private ItemStack carriedItem = ItemStack.EMPTY;

    private FoxItemStorage(Mob fox) {
        this.fox = fox;
    }

    /**
     * Get or create the item storage component for a fox.
     */
    public static FoxItemStorage get(Mob fox) {
        EcologyComponent component = ((EcologyAccess) fox).betterEcology$getEcologyComponent();
        CompoundTag tag = component.getHandleTag(STORAGE_KEY);

        if (tag != null && tag.contains(ITEM_KEY)) {
            return loadFromTag(fox, tag);
        }

        FoxItemStorage storage = new FoxItemStorage(fox);
        component.setHandleTag(STORAGE_KEY, storage.saveToTag());
        return storage;
    }

    /**
     * Check if fox has an item stored.
     */
    public boolean hasItem() {
        return !carriedItem.isEmpty();
    }

    /**
     * Get the carried item.
     */
    public ItemStack getItem() {
        return carriedItem.copy();
    }

    /**
     * Set the carried item.
     */
    public void setItem(ItemStack itemStack) {
        this.carriedItem = itemStack.copy();
        saveToFox();
    }

    /**
     * Remove the carried item.
     */
    public void clearItem() {
        this.carriedItem = ItemStack.EMPTY;
        saveToFox();
    }

    /**
     * Save storage data to NBT.
     */
    public CompoundTag saveToTag() {
        CompoundTag tag = new CompoundTag();
        if (!carriedItem.isEmpty()) {
            HolderLookup.Provider registries = fox.registryAccess();
            CompoundTag itemTag = (CompoundTag) carriedItem.save(registries);
            tag.put(ITEM_KEY, itemTag);
        }
        return tag;
    }

    /**
     * Save storage data to fox's persistent data.
     */
    private void saveToFox() {
        EcologyComponent component = ((EcologyAccess) fox).betterEcology$getEcologyComponent();
        component.setHandleTag(STORAGE_KEY, saveToTag());
    }

    /**
     * Load storage data from NBT.
     */
    private static FoxItemStorage loadFromTag(Mob fox, CompoundTag tag) {
        FoxItemStorage storage = new FoxItemStorage(fox);

        if (tag.contains(ITEM_KEY)) {
            CompoundTag itemTag = tag.getCompound(ITEM_KEY);
            HolderLookup.Provider registries = fox.registryAccess();
            storage.carriedItem = ItemStack.parseOptional(registries, itemTag);
        }

        return storage;
    }

    /**
     * Remove storage from fox (for cleanup).
     */
    public static void remove(Mob fox) {
        EcologyComponent component = ((EcologyAccess) fox).betterEcology$getEcologyComponent();
        component.setHandleTag(STORAGE_KEY, new CompoundTag());
    }
}
