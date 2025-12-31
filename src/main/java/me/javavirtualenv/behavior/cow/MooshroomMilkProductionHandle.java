package me.javavirtualenv.behavior.cow;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.ecology.handles.production.MilkProductionHandle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Extended milk production for mooshrooms.
 * <p>
 * Mooshrooms produce:
 * - Standard milk (with bucket)
 * - Mushroom stew (with bowl)
 * - Suspicious stew (with bowl after feeding flower)
 * <p>
 * Suspicious stew effects vary based on flower type fed to the mooshroom.
 * This is vanilla behavior with our milk production system integrated.
 */
public class MooshroomMilkProductionHandle extends MilkProductionHandle {
    private static final String NBT_STEW_FLOWER = "stewFlower";
    private static final String NBT_LAST_STEW_TICK = "lastStewTick";

    private static final int STEW_COOLDOWN = 24000; // 20 minutes
    private static final int MILK_FOR_STEW = 30; // More milk needed for stew than bucket

    @Override
    public String id() {
        return "mooshroom_milk_production";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile.getBool("mooshroom_milk_production.enabled", false);
    }

    /**
     * Get milk or stew based on item used.
     * Called from mooshroom interaction mixin.
     */
    public ItemStack onMooshroomMilked(Mob mob, @Nullable EcologyComponent component, ItemStack usedItem) {
        if (!(mob instanceof MushroomCow)) {
            return ItemStack.EMPTY;
        }

        if (component == null) {
            return ItemStack.EMPTY;
        }

        // Check if player is using bowl (stew)
        if (usedItem.is(Items.BOWL)) {
            return getStew(mob, component);
        }

        // Otherwise return milk bucket
        return onMilked(mob, component, null);
    }

    /**
     * Get mushroom stew (regular or suspicious) from mooshroom.
     */
    private ItemStack getStew(Mob mob, EcologyComponent component) {
        CompoundTag tag = component.getHandleTag(id());
        int milkAmount = getMilkAmountFromTag(tag);

        if (milkAmount < MILK_FOR_STEW) {
            return ItemStack.EMPTY;
        }

        int currentTick = mob.tickCount;
        int lastStew = tag.getInt(NBT_LAST_STEW_TICK);
        boolean canMakeSuspicious = (currentTick - lastStew) >= STEW_COOLDOWN;

        ItemStack stew;
        if (canMakeSuspicious && tag.contains(NBT_STEW_FLOWER)) {
            // Create suspicious stew with effect
            String flowerType = tag.getString(NBT_STEW_FLOWER);
            stew = createSuspiciousStew(flowerType);
            tag.putInt(NBT_LAST_STEW_TICK, currentTick);
            tag.remove(NBT_STEW_FLOWER);
        } else {
            // Regular mushroom stew
            stew = new ItemStack(Items.MUSHROOM_STEW);
        }

        // Consume milk
        setMilkAmountInTag(tag, milkAmount - MILK_FOR_STEW);
        setLastMilkedTickInTag(tag, currentTick);

        // Play sound
        mob.level().playSound(null, mob.blockPosition(),
                getMilkingSound(mob),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);

        return stew;
    }

    /**
     * Feed a flower to mooshroom for suspicious stew.
     * Called from interaction mixin.
     */
    public boolean onFlowerFed(Mob mob, @Nullable EcologyComponent component, ItemStack flower) {
        if (!isFlower(flower)) {
            return false;
        }

        if (component == null) {
            return false;
        }

        // Store flower type in component data
        CompoundTag tag = component.getHandleTag(id());
        tag.putString(NBT_STEW_FLOWER, flower.getItem().toString());

        // Play eating sound
        mob.level().playSound(null, mob.blockPosition(),
                net.minecraft.sounds.SoundEvents.MOOSHROOM_EAT,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);

        return true;
    }

    /**
     * Create suspicious stew with effect based on flower.
     */
    private ItemStack createSuspiciousStew(String flowerType) {
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
        MobEffect effect = getEffectForFlower(flowerType);
        net.minecraft.world.item.alchemy.SuspiciousStewEffect.addToStew(stew, effect, 160);
        return stew;
    }

    /**
     * Get the potion effect for a given flower type.
     * Matches vanilla suspicious stew brewing.
     */
    private MobEffect getEffectForFlower(String flowerType) {
        return switch (flowerType) {
            case "minecraft:allium" -> MobEffects.FIRE_RESISTANCE;
            case "minecraft:azure_bluet" -> MobEffects.BLINDNESS;
            case "minecraft:red_tulip", "minecraft:white_tulip",
                 "minecraft:pink_tulip", "minecraft:orange_tulip" -> MobEffects.WEAKNESS;
            case "minecraft:cornflower" -> MobEffects.JUMP;
            case "minecraft:lily_of_the_valley" -> MobEffects.POISON;
            case "minecraft:oxeye_daisy" -> MobEffects.REGENERATION;
            case "minecraft:poppy" -> MobEffects.NIGHT_VISION;
            case "minecraft:dandelion", "minecraft:blue_orchid" -> MobEffects.SATURATION;
            case "minecraft:wither_rose" -> MobEffects.WITHER;
            default -> MobEffects.SATURATION;
        };
    }

    /**
     * Check if item is a flower that can be fed to mooshroom.
     */
    private boolean isFlower(ItemStack stack) {
        return stack.is(Items.ALLIUM) ||
               stack.is(Items.AZURE_BLUET) ||
               stack.is(Items.RED_TULIP) ||
               stack.is(Items.CORNFLOWER) ||
               stack.is(Items.LILY_OF_THE_VALLEY) ||
               stack.is(Items.OXEYE_DAISY) ||
               stack.is(Items.POPPY) ||
               stack.is(Items.DANDELION) ||
               stack.is(Items.WHITE_TULIP) ||
               stack.is(Items.PINK_TULIP) ||
               stack.is(Items.ORANGE_TULIP) ||
               stack.is(Items.BLUE_ORCHID) ||
               stack.is(Items.WITHER_ROSE);
    }

    // NBT helpers to avoid ambiguity with parent methods
    private int getMilkAmountFromTag(CompoundTag tag) {
        return tag.contains(NBT_MILK_AMOUNT) ? tag.getInt(NBT_MILK_AMOUNT) : 0;
    }

    private void setMilkAmountInTag(CompoundTag tag, int amount) {
        tag.putInt(NBT_MILK_AMOUNT, amount);
    }

    private void setLastMilkedTickInTag(CompoundTag tag, int tick) {
        tag.putInt(NBT_LAST_MILKED, tick);
    }
}
