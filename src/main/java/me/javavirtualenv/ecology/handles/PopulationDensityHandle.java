package me.javavirtualenv.ecology.handles;

import me.javavirtualenv.ecology.spawning.RegionalDensityTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Centralized density checking for breeding decisions.
 * Interfaces with RegionalDensityTracker to get population counts
 * and calculates breeding probability multipliers based on density curves.
 */
public final class PopulationDensityHandle {

    private PopulationDensityHandle() {
        // Utility class - prevent instantiation
    }

    /**
     * Get breeding probability multiplier based on local population density.
     * Uses density_curve from config to determine multiplier.
     *
     * @param level The world level
     * @param pos Center position to check density
     * @param entityType Type of entity to count
     * @param checkRadius Radius in blocks to check for population
     * @param densityCurve List of density thresholds and multipliers
     * @return Breeding probability multiplier (0.0 to 1.3+)
     */
    public static double getBreedingMultiplier(Level level, BlockPos pos, EntityType<?> entityType,
                                               int checkRadius, List<DensityThreshold> densityCurve) {
        if (!(level instanceof ServerLevel serverLevel)) {
            // On client side, assume normal breeding rate
            return 1.0;
        }

        int populationCount = getPopulationCount(serverLevel, pos, entityType, checkRadius);
        return calculateMultiplier(populationCount, densityCurve);
    }

    /**
     * Get population count within radius using RegionalDensityTracker.
     * Counts entities of the same type within the specified radius.
     */
    private static int getPopulationCount(ServerLevel level, BlockPos center, EntityType<?> entityType, int radius) {
        RegionalDensityTracker tracker = getRegionalTracker();
        if (tracker == null) {
            // Fallback to direct entity counting if tracker unavailable
            return countEntitiesDirectly(level, center, entityType, radius);
        }

        // Update tracker before counting
        tracker.updateCounts(level);

        // Get count in region containing the center position
        return tracker.getCountInRegion(level, center, entityType);
    }

    /**
     * Fallback method: count entities directly by iterating nearby entities.
     * Less efficient but doesn't require tracker initialization.
     */
    private static int countEntitiesDirectly(ServerLevel level, BlockPos center, EntityType<?> entityType, int radius) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || mob.getType() != entityType) {
                continue;
            }
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            double dist = entity.blockPosition().distSqr(center);
            if (dist <= radius * radius) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculate breeding multiplier based on population count and density curve.
     * Density curve is ordered by count threshold - find first threshold where count <= max
     * and use its multiplier. If none match, use the last (most restrictive) multiplier.
     *
     * Example curve:
     * - count: 2, multiplier: 1.3  (under 2 animals = 1.3x breeding)
     * - count: 6, multiplier: 1.0  (2-6 animals = 1.0x breeding)
     * - count: 12, multiplier: 0.5 (6-12 animals = 0.5x breeding)
     * - count: 20, multiplier: 0.1 (12-20 animals = 0.1x breeding)
     * - count: 30, multiplier: 0.0 (20+ animals = 0.0x breeding)
     */
    private static double calculateMultiplier(int populationCount, List<DensityThreshold> densityCurve) {
        if (densityCurve == null || densityCurve.isEmpty()) {
            return 1.0;
        }

        double multiplier = 1.0;
        for (DensityThreshold threshold : densityCurve) {
            if (populationCount <= threshold.maxCount) {
                return threshold.multiplier;
            }
            multiplier = threshold.multiplier;
        }
        return multiplier;
    }

    /**
     * Check if Allee threshold is met (minimum population for breeding).
     * Returns false if population too low to find mates.
     */
    public static boolean meetsAlleeThreshold(Level level, BlockPos pos, EntityType<?> entityType,
                                             int checkRadius, int minPopulation) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        int populationCount = getPopulationCount(serverLevel, pos, entityType, checkRadius);
        return populationCount >= minPopulation;
    }

    /**
     * Check if carrying capacity has been reached (maximum population for breeding).
     * Returns false if population at or above carrying capacity.
     */
    public static boolean belowCarryingCapacity(Level level, BlockPos pos, EntityType<?> entityType,
                                               int checkRadius, int maxPopulation) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        int populationCount = getPopulationCount(serverLevel, pos, entityType, checkRadius);
        return populationCount < maxPopulation;
    }

    /**
     * Density threshold configuration from JSON.
     * Maps population count ranges to breeding multipliers.
     */
    public static class DensityThreshold {
        private final int maxCount;
        private final double multiplier;

        public DensityThreshold(int maxCount, double multiplier) {
            this.maxCount = maxCount;
            this.multiplier = multiplier;
        }

        public int maxCount() {
            return maxCount;
        }

        public double multiplier() {
            return multiplier;
        }
    }

    /**
     * Density configuration passed to breeding goals.
     */
    public static class DensityConfig {
        private final boolean enabled;
        private final int checkRadius;
        private final List<DensityThreshold> densityCurve;
        private final int alleeThreshold;
        private final int carryingCapacity;

        public DensityConfig(boolean enabled, int checkRadius, List<DensityThreshold> densityCurve,
                           int alleeThreshold, int carryingCapacity) {
            this.enabled = enabled;
            this.checkRadius = checkRadius;
            this.densityCurve = densityCurve;
            this.alleeThreshold = alleeThreshold;
            this.carryingCapacity = carryingCapacity;
        }

        public boolean enabled() {
            return enabled;
        }

        public int checkRadius() {
            return checkRadius;
        }

        public List<DensityThreshold> densityCurve() {
            return densityCurve;
        }

        public int alleeThreshold() {
            return alleeThreshold;
        }

        public int carryingCapacity() {
            return carryingCapacity;
        }
    }

    /**
     * Get RegionalDensityTracker from SpawnBootstrap.
     */
    private static RegionalDensityTracker getRegionalTracker() {
        return me.javavirtualenv.ecology.spawning.SpawnBootstrap.getRegionalTracker();
    }
}
