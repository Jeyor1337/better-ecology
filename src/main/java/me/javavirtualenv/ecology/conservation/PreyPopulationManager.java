package me.javavirtualenv.ecology.conservation;

import me.javavirtualenv.ecology.spatial.SpatialIndex;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized manager for prey population queries.
 * Uses SpatialIndex for efficient local density checks and caches results for performance.
 * <p>
 * This manager implements conservation biology principles to prevent prey extinction:
 * - Allee effect threshold: minimum viable population for reproduction
 * - Prey switching: predators switch to alternative prey when primary is scarce
 * - Density-dependent predation: hunting pressure decreases as prey density drops
 * <p>
 * Based on research:
 * - Allee et al. (1949) - Principles of Animal Ecology
 * - Murdoch (1969) - Switching in general predators
 * - Holling (1959) - The components of predation as revealed by a study of small-mammal predation
 */
public final class PreyPopulationManager {

    // Cache for population counts to avoid recalculating every tick
    private static final Map<PopulationCacheKey, PopulationData> populationCache = new HashMap<>();
    private static final int CACHE_TTL_TICKS = 100; // Cache expires after 5 seconds

    // Conservation thresholds (from research)
    private static final double ALLEE_THRESHOLD = 0.25; // 25% of baseline - minimum viable population
    private static final double PREY_PROTECTION_THRESHOLD = 0.30; // 30% of baseline - stop hunting below this
    private static final double PREY_SWITCHING_THRESHOLD = 0.40; // 40% of baseline - switch to alternative prey
    private static final double BASELINE_PREY_PER_CHUNK = 4.0; // Expected prey density for healthy population

    private PreyPopulationManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the count of prey entities of a specific type within radius.
     * Uses caching for performance - results are valid for 5 seconds.
     *
     * @param predator The predator entity (center of search)
     * @param preyType The type of prey to count
     * @param radius Search radius in blocks
     * @return Count of prey entities
     */
    public static int getPreyCount(Mob predator, Class<?> preyType, int radius) {
        PopulationCacheKey key = new PopulationCacheKey(
                predator.level().dimension().location().toString(),
                preyType,
                predator.chunkPosition().x,
                predator.chunkPosition().z,
                radius
        );

        PopulationData cached = populationCache.get(key);
        int currentTick = predator.tickCount;

        // Return cached data if still valid
        if (cached != null && (currentTick - cached.tickRecorded) < CACHE_TTL_TICKS) {
            return cached.count;
        }

        // Calculate actual count using SpatialIndex
        int count = countPreyOfType(predator, preyType, radius);

        // Cache the result
        populationCache.put(key, new PopulationData(count, currentTick));

        return count;
    }

    /**
     * Checks if prey population is scarce (below protection threshold).
     * When true, predators should avoid hunting this prey type.
     *
     * @param predator The predator entity
     * @param preyType The type of prey to check
     * @param radius Search radius in blocks
     * @return true if prey population is below protection threshold
     */
    public static boolean isPreyScarce(Mob predator, Class<?> preyType, int radius) {
        int currentCount = getPreyCount(predator, preyType, radius);
        double expectedCount = calculateExpectedPreyCount(radius);
        double ratio = currentCount / expectedCount;

        return ratio < PREY_PROTECTION_THRESHOLD;
    }

    /**
     * Checks if prey population is healthy (above Allee threshold).
     * Healthy populations can sustain predation and reproduce.
     *
     * @param predator The predator entity
     * @param preyType The type of prey to check
     * @param radius Search radius in blocks
     * @return true if prey population is healthy
     */
    public static boolean isPreyPopulationHealthy(Mob predator, Class<?> preyType, int radius) {
        int currentCount = getPreyCount(predator, preyType, radius);
        double expectedCount = calculateExpectedPreyCount(radius);
        double ratio = currentCount / expectedCount;

        return ratio >= ALLEE_THRESHOLD;
    }

    /**
     * Gets the population ratio (current/expected) for a prey type.
     * Used for prey switching decisions - lower values indicate scarcer prey.
     *
     * @param predator The predator entity
     * @param preyType The type of prey to check
     * @param radius Search radius in blocks
     * @return Population ratio (0.0 to >1.0, where 1.0 is expected baseline)
     */
    public static double getPopulationRatio(Mob predator, Class<?> preyType, int radius) {
        int currentCount = getPreyCount(predator, preyType, radius);
        double expectedCount = calculateExpectedPreyCount(radius);

        if (expectedCount == 0) {
            return 1.0; // No expectation, treat as healthy
        }

        return currentCount / expectedCount;
    }

    /**
     * Gets all available prey types in the area, sorted by abundance.
     * Used for prey switching when primary prey is scarce.
     *
     * @param predator The predator entity
     * @param radius Search radius in blocks
     * @param possiblePreyTypes List of prey types to check
     * @return List of prey types sorted by population ratio (most abundant first)
     */
    public static List<Class<?>> getAvailablePreyTypes(Mob predator, int radius, List<Class<?>> possiblePreyTypes) {
        List<PreyTypeScore> scoredTypes = new ArrayList<>();

        for (Class<?> preyType : possiblePreyTypes) {
            if (!isPreyScarce(predator, preyType, radius)) {
                double ratio = getPopulationRatio(predator, preyType, radius);
                scoredTypes.add(new PreyTypeScore(preyType, ratio));
            }
        }

        // Sort by ratio (highest abundance first)
        scoredTypes.sort((a, b) -> Double.compare(b.score, a.score));

        List<Class<?>> result = new ArrayList<>();
        for (PreyTypeScore scored : scoredTypes) {
            result.add(scored.preyType);
        }

        return result;
    }

    /**
     * Calculates the expected prey count for a given radius based on baseline density.
     * This represents a healthy, sustainable population level.
     */
    private static double calculateExpectedPreyCount(int radius) {
        // Calculate area in chunks
        double chunkRadius = (radius + 15.0) / 16.0;
        double chunkArea = Math.PI * chunkRadius * chunkRadius;

        // Expected count = baseline density per chunk * chunk area
        return BASELINE_PREY_PER_CHUNK * chunkArea;
    }

    /**
     * Counts prey of a specific type using SpatialIndex.
     */
    private static int countPreyOfType(Mob predator, Class<?> preyType, int radius) {
        List<Mob> nearbyMobs = SpatialIndex.getNearbyMobs(predator, radius);
        int count = 0;

        for (Mob mob : nearbyMobs) {
            if (preyType.isInstance(mob) && mob.isAlive()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Clears expired cache entries.
     * Should be called periodically to prevent memory leaks.
     */
    public static void cleanupCache(int currentTick) {
        populationCache.entrySet().removeIf(entry -> {
            PopulationData data = entry.getValue();
            return (currentTick - data.tickRecorded) >= CACHE_TTL_TICKS;
        });
    }

    /**
     * Clears all cached population data.
     * Call when unloading a world or dimension.
     */
    public static void clearCache() {
        populationCache.clear();
    }

    /**
     * Cache key for population queries.
     */
    private static class PopulationCacheKey {
        private final String dimensionId;
        private final Class<?> preyType;
        private final int chunkX;
        private final int chunkZ;
        private final int radius;

        PopulationCacheKey(String dimensionId, Class<?> preyType, int chunkX, int chunkZ, int radius) {
            this.dimensionId = dimensionId;
            this.preyType = preyType;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.radius = radius;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PopulationCacheKey other)) return false;
            return chunkX == other.chunkX &&
                    chunkZ == other.chunkZ &&
                    radius == other.radius &&
                    dimensionId.equals(other.dimensionId) &&
                    preyType.equals(other.preyType);
        }

        @Override
        public int hashCode() {
            int result = dimensionId.hashCode();
            result = 31 * result + preyType.hashCode();
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            result = 31 * result + radius;
            return result;
        }
    }

    /**
     * Cached population data with timestamp.
     */
    private static class PopulationData {
        private final int count;
        private final int tickRecorded;

        PopulationData(int count, int tickRecorded) {
            this.count = count;
            this.tickRecorded = tickRecorded;
        }
    }

    /**
     * Helper class for scoring prey types by abundance.
     */
    private static class PreyTypeScore {
        private final Class<?> preyType;
        private final double score;

        PreyTypeScore(Class<?> preyType, double score) {
            this.preyType = preyType;
            this.score = score;
        }
    }
}
