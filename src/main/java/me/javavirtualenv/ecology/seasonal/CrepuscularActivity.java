package me.javavirtualenv.ecology.seasonal;

/**
 * Interface for crepuscular animals that are primarily active at dawn and dusk.
 * Animals implementing this interface show increased activity during twilight periods.
 *
 * Examples: rabbits, deer, some rodents
 */
public interface CrepuscularActivity {

    /**
     * Check if this entity is active during dawn.
     *
     * @return true if active at dawn
     */
    boolean isActiveAtDawn();

    /**
     * Check if this entity is active during dusk.
     *
     * @return true if active at dusk
     */
    boolean isActiveAtDusk();

    /**
     * Get the activity multiplier during crepuscular periods.
     * Default is 1.5x normal activity during dawn/dusk.
     *
     * @return Activity multiplier (1.0 = normal, >1.0 = more active)
     */
    default double getActivityMultiplier() {
        return 1.5;
    }

    /**
     * Get the activity multiplier for a specific time period.
     *
     * @param period The time period
     * @return Activity multiplier
     */
    default double getActivityMultiplier(SeasonalContext.TimePeriod period) {
        if (period == SeasonalContext.TimePeriod.DAWN && isActiveAtDawn()) {
            return getActivityMultiplier();
        }
        if (period == SeasonalContext.TimePeriod.DUSK && isActiveAtDusk()) {
            return getActivityMultiplier();
        }
        // Reduced activity during other times
        return 0.3;
    }
}
