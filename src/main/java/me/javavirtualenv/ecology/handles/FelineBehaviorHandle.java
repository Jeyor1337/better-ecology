package me.javavirtualenv.ecology.handles;

import me.javavirtualenv.behavior.feline.*;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.mixin.MobAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;

/**
 * Handle for managing feline-specific behaviors.
 * <p>
 * This handle registers special behaviors for cats and ocelots:
 * - Hunting behaviors (stalking, pouncing, creeping)
 * - Stealth behaviors (quiet movement, squeezing through gaps)
 * - Social behaviors (purring, hissing, rubbing affection)
 * - Gift giving behavior (tamed cats)
 * - Creeper detection and phantom repelling
 * - Play behavior (kittens and bored cats)
 * - Climbing behavior (ocelots)
 * - Sleep behavior (cats on furniture)
 * - Fall damage reduction (cats land on feet)
 */
public class FelineBehaviorHandle implements EcologyHandle {

    private static final String AFFECTION_KEY = "affection";
    private static final String IS_SLEEPING_KEY = "is_sleeping";
    private static final String IS_CLIMBING_KEY = "is_climbing";

    @Override
    public String id() {
        return "feline_behavior";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        String entityType = profile.getString("entity_type", "");
        return "cat".equals(entityType) || "ocelot".equals(entityType);
    }

    @Override
    public void registerGoals(Mob mob, EcologyComponent component, EcologyProfile profile) {
        // Create affection component for social behaviors
        CatAffectionComponent affection = loadOrCreateAffection(mob, component);

        // Register the main feline behavior goal
        FelineBehaviorGoal felineGoal = new FelineBehaviorGoal(mob, affection);
        int priority = profile.getInt("feline.priority", 2);

        MobAccessor accessor = (MobAccessor) mob;
        accessor.betterEcology$getGoalSelector().addGoal(priority, felineGoal);
    }

    @Override
    public void tick(Mob mob, EcologyComponent component, EcologyProfile profile) {
        // Update feline state each tick
        CompoundTag tag = component.getHandleTag(id());

        // Fall damage is handled in the behavior goal
        // Additional tick-based updates can go here
    }

    @Override
    public void readNbt(Mob mob, EcologyComponent component, EcologyProfile profile, CompoundTag tag) {
        if (tag.contains(AFFECTION_KEY)) {
            CompoundTag affectionTag = tag.getCompound(AFFECTION_KEY);
            CatAffectionComponent affection = new CatAffectionComponent();
            affection.fromNbt(affectionTag);

            // Store affection in entity data for later retrieval
            storeAffectionComponent(mob, component, affection);
        }

        // Read sleeping and climbing states
        CompoundTag felineTag = component.getHandleTag(id());
        if (tag.contains(IS_SLEEPING_KEY)) {
            felineTag.putBoolean(IS_SLEEPING_KEY, tag.getBoolean(IS_SLEEPING_KEY));
        }
        if (tag.contains(IS_CLIMBING_KEY)) {
            felineTag.putBoolean(IS_CLIMBING_KEY, tag.getBoolean(IS_CLIMBING_KEY));
        }
    }

    @Override
    public void writeNbt(Mob mob, EcologyComponent component, EcologyProfile profile, CompoundTag tag) {
        CompoundTag handleTag = component.getHandleTag(id());

        // Save affection data
        CatAffectionComponent affection = getAffectionComponent(mob, component);
        if (affection != null) {
            CompoundTag affectionTag = new CompoundTag();
            affection.toNbt(affectionTag);
            tag.put(AFFECTION_KEY, affectionTag);
        }

        // Save sleeping and climbing states
        tag.put(IS_SLEEPING_KEY, handleTag.copy());
        tag.put(IS_CLIMBING_KEY, handleTag.copy());
    }

    /**
     * Load or create the affection component for a cat.
     */
    private CatAffectionComponent loadOrCreateAffection(Mob mob, EcologyComponent component) {
        // Try to load from existing data
        CatAffectionComponent affection = getAffectionComponent(mob, component);
        if (affection == null) {
            affection = new CatAffectionComponent();
            storeAffectionComponent(mob, component, affection);
        }
        return affection;
    }

    /**
     * Store the affection component for persistent access.
     */
    private void storeAffectionComponent(Mob mob, EcologyComponent component, CatAffectionComponent affection) {
        // Store in component data for persistence
        CompoundTag tag = component.getHandleTag(id());
        CompoundTag affectionTag = new CompoundTag();
        affection.toNbt(affectionTag);
        tag.put(AFFECTION_KEY, affectionTag);
    }

    /**
     * Get the affection component for a cat.
     */
    public static CatAffectionComponent getAffectionComponent(Mob mob, EcologyComponent component) {
        if (component == null) {
            return new CatAffectionComponent();
        }

        CompoundTag tag = component.getHandleTag("feline_behavior");
        if (tag != null && tag.contains(AFFECTION_KEY)) {
            CatAffectionComponent affection = new CatAffectionComponent();
            affection.fromNbt(tag.getCompound(AFFECTION_KEY));
            return affection;
        }

        return new CatAffectionComponent();
    }

    /**
     * Check if a cat is currently sleeping.
     */
    public static boolean isSleeping(Mob mob, EcologyComponent component) {
        if (component == null) {
            return false;
        }

        CompoundTag tag = component.getHandleTag("feline_behavior");
        return tag != null && tag.getBoolean(IS_SLEEPING_KEY);
    }

    /**
     * Set the sleeping state for a cat.
     */
    public static void setSleeping(Mob mob, EcologyComponent component, boolean sleeping) {
        if (component == null) {
            return;
        }

        CompoundTag tag = component.getHandleTag("feline_behavior");
        tag.putBoolean(IS_SLEEPING_KEY, sleeping);
    }

    /**
     * Check if an ocelot is currently climbing.
     */
    public static boolean isClimbing(Mob mob, EcologyComponent component) {
        if (component == null) {
            return false;
        }

        CompoundTag tag = component.getHandleTag("feline_behavior");
        return tag != null && tag.getBoolean(IS_CLIMBING_KEY);
    }

    /**
     * Set the climbing state for an ocelot.
     */
    public static void setClimbing(Mob mob, EcologyComponent component, boolean climbing) {
        if (component == null) {
            return;
        }

        CompoundTag tag = component.getHandleTag("feline_behavior");
        tag.putBoolean(IS_CLIMBING_KEY, climbing);
    }
}
