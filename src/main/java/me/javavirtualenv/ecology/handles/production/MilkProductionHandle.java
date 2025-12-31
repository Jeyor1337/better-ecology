package me.javavirtualenv.ecology.handles.production;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Simple milk production system for cows and mooshrooms.
 * <p>
 * This handle manages:
 * - Milk capacity that regenerates over time
 * - Simple cooldown system
 * - Visual feedback when milk is ready (particles/heart animation)
 * - Mooshrooms produce mushroom stew with bowls
 * <p>
 * Design principles:
 * - No hidden quality tiers - all milk is the same vanilla item
 * - Observable state - players can see when cows are ready to milk
 * - Simple cooldown - easy to understand without documentation
 * - Vanilla-friendly - works like other animal resources (wool, eggs)
 */
public class MilkProductionHandle implements EcologyHandle {
    private static final String NBT_MILK_AMOUNT = "milkAmount";
    private static final String NBT_LAST_MILKED = "lastMilkedTick";
    private static final String NBT_READY_PARTICLE_TICK = "readyParticleTick";

    // Configuration - readable and simple
    private static final int MAX_MILK_AMOUNT = 100;
    private static final int MILKING_COOLDOWN = 1200; // 60 seconds (1 minute)
    private static final int MILK_NEEDED = 20; // Amount consumed per milking
    private static final int BASE_MILK_REGEN_RATE = 1; // Per tick interval

    // Visual feedback settings
    private static final int PARTICLE_INTERVAL = 100; // Show particles every 5 seconds when ready
    private static final int READY_THRESHOLD = 20; // Minimum milk to be considered "ready"

    @Override
    public String id() {
        return "milk_production";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile.getBool("milk_production.enabled", false);
    }

    @Override
    public int tickInterval() {
        return 100; // Update every 5 seconds
    }

    @Override
    public void tick(Mob mob, EcologyComponent component, EcologyProfile profile) {
        CompoundTag tag = component.getHandleTag(id());

        regenerateMilk(mob, tag, component);
        showReadyParticles(mob, tag);
    }

    @Override
    public void writeNbt(Mob mob, EcologyComponent component, EcologyProfile profile, CompoundTag outputTag) {
        CompoundTag handleTag = component.getHandleTag(id());
        outputTag.put(id(), handleTag.copy());
    }

    /**
     * Check if this animal can be milked.
     * Called from interaction mixin when player uses bucket.
     */
    public boolean canBeMilked(Mob mob, EcologyComponent component) {
        if (!(mob instanceof Cow)) {
            return false;
        }

        CompoundTag tag = component.getHandleTag(id());
        int milkAmount = getMilkAmount(tag);
        int lastMilked = getLastMilkedTick(tag);
        int currentTick = mob.tickCount;

        boolean hasMilk = milkAmount >= MILK_NEEDED;
        boolean cooldownPassed = (currentTick - lastMilked) >= MILKING_COOLDOWN;

        return hasMilk && cooldownPassed;
    }

    /**
     * Called when player milks this cow.
     */
    public ItemStack onMilked(Mob mob, EcologyComponent component, Player player) {
        CompoundTag tag = component.getHandleTag(id());
        int milkAmount = getMilkAmount(tag);

        if (milkAmount < MILK_NEEDED) {
            return ItemStack.EMPTY;
        }

        // Determine milk type based on entity
        ItemStack milkBucket = getMilkBucket(mob);

        // Consume milk
        setMilkAmount(tag, milkAmount - MILK_NEEDED);
        setLastMilkedTick(tag, mob.tickCount);

        // Play sound
        mob.level().playSound(null, mob.blockPosition(),
                getMilkingSound(mob),
                SoundSource.NEUTRAL, 1.0F, 1.0F);

        return milkBucket;
    }

    /**
     * Get the appropriate milk product for this animal.
     */
    protected ItemStack getMilkBucket(Mob mob) {
        return new ItemStack(Items.MILK_BUCKET);
    }

    /**
     * Get the sound effect for milking this animal.
     */
    protected SoundEvents getMilkingSound(Mob mob) {
        return mob instanceof MushroomCow
                ? SoundEvents.MOOSHROOM_MILK
                : SoundEvents.COW_MILK;
    }

    /**
     * Regenerate milk over time.
     * Simple linear regeneration - easy to predict.
     */
    private void regenerateMilk(Mob mob, CompoundTag tag, EcologyComponent component) {
        int currentAmount = getMilkAmount(tag);
        if (currentAmount >= MAX_MILK_AMOUNT) {
            return;
        }

        // Base regeneration
        int regenAmount = BASE_MILK_REGEN_RATE;

        // Health bonus - healthier animals produce milk faster
        double healthPercent = mob.getHealth() / mob.getMaxHealth();
        regenAmount *= healthPercent;

        setMilkAmount(tag, Math.min(MAX_MILK_AMOUNT, currentAmount + regenAmount));
    }

    /**
     * Show visual particles when milk is ready.
     * This makes the system observable without needing a HUD.
     */
    private void showReadyParticles(Mob mob, CompoundTag tag) {
        int milkAmount = getMilkAmount(tag);
        int lastMilked = getLastMilkedTick(tag);
        int currentTick = mob.tickCount;

        // Only show if ready to milk
        if (milkAmount < READY_THRESHOLD || (currentTick - lastMilked) < MILKING_COOLDOWN) {
            return;
        }

        int lastParticle = tag.getInt(NBT_READY_PARTICLE_TICK);
        if (currentTick - lastParticle >= PARTICLE_INTERVAL) {
            tag.putInt(NBT_READY_PARTICLE_TICK, currentTick);

            // Show heart particles - classic Minecraft "ready" signal
            if (!mob.level().isClientSide) {
                mob.level().broadcastEntityEvent(mob, (byte) 7); // Heart particles
            }
        }
    }

    /**
     * Get current milk amount for debugging/UI purposes.
     */
    public int getMilkAmount(EcologyComponent component) {
        CompoundTag tag = component.getHandleTag(id());
        return getMilkAmount(tag);
    }

    /**
     * Check if this cow is ready to be milked.
     * Useful for AI decisions and external queries.
     */
    public boolean isReadyToMilk(Mob mob, EcologyComponent component) {
        return canBeMilked(mob, component);
    }

    // NBT helpers
    private int getMilkAmount(CompoundTag tag) {
        return tag.getInt(NBT_MILK_AMOUNT);
    }

    private void setMilkAmount(CompoundTag tag, int amount) {
        tag.putInt(NBT_MILK_AMOUNT, Math.max(0, Math.min(MAX_MILK_AMOUNT, amount)));
    }

    private int getLastMilkedTick(CompoundTag tag) {
        return tag.getInt(NBT_LAST_MILKED);
    }

    private void setLastMilkedTick(CompoundTag tag, int tick) {
        tag.putInt(NBT_LAST_MILKED, tick);
    }
}
