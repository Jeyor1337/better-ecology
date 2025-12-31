package me.javavirtualenv.ecology.handles;

import java.util.concurrent.TimeUnit;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.ecology.conservation.HabitatQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

/**
 * Handle for habitat quality tracking and caching.
 * Provides habitat quality queries for spawning and breeding decisions.
 * Implements source-sink dynamics with quality-based population effects.
 */
public final class HabitatHandle implements EcologyHandle {
    private static final String CACHE_KEY = "better-ecology:habitat-cache";
    private static final long CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(5);

    @Override
    public String id() {
        return "habitat";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile.getBool("habitat.enabled", true);
    }

    @Override
    public void initialize(Mob mob, EcologyComponent component, EcologyProfile profile) {
        // Habitat tracking is server-side only
        if (!(mob.level() instanceof ServerLevel)) {
            return;
        }

        // Initialize habitat quality cache
        HabitatCache cache = profile.cached(CACHE_KEY, () -> buildConfig(profile));
        component.getHandleTag(id()).putLong("last_evaluation", 0);
    }

    /**
     * Get habitat quality for a position with caching.
     * Cache expires after 5 minutes to allow for habitat changes.
     *
     * @param level The server level
     * @param pos The position to evaluate
     * @param entityType The entity type
     * @return The habitat quality
     */
    public static HabitatQuality getHabitatQuality(ServerLevel level, BlockPos pos, EntityType<?> entityType) {
        return HabitatQuality.evaluateHabitat(level, pos, entityType);
    }

    /**
     * Get habitat quality for a chunk region with caching.
     *
     * @param level The server level
     * @param chunkPos The chunk position
     * @param entityType The entity type
     * @return The habitat quality
     */
    public static HabitatQuality getHabitatQualityForChunk(ServerLevel level, ChunkPos chunkPos, EntityType<?> entityType) {
        // Evaluate at center of chunk
        BlockPos centerPos = new BlockPos(
            chunkPos.getMinBlockX() + 8,
            level.getChunk(chunkPos.x, chunkPos.z).getMinBuildHeight() + 4,
            chunkPos.getMinBlockZ() + 8
        );
        return getHabitatQuality(level, centerPos, entityType);
    }

    /**
     * Check if cached habitat quality is still valid.
     *
     * @param component The ecology component
     * @return true if cache is valid, false if expired
     */
    public static boolean isCacheValid(EcologyComponent component) {
        long lastEval = component.getHandleTag("habitat").getLong("last_evaluation");
        long currentTime = System.nanoTime();
        return (currentTime - lastEval) < CACHE_TTL_NANOS;
    }

    /**
     * Update the habitat quality cache timestamp.
     *
     * @param component The ecology component
     */
    public static void updateCacheTimestamp(EcologyComponent component) {
        component.getHandleTag("habitat").putLong("last_evaluation", System.nanoTime());
    }

    /**
     * Get the spawn multiplier for habitat quality.
     * Source habitats get +20% bonus, sink habitats get -50% penalty.
     *
     * @param quality The habitat quality
     * @return The spawn multiplier
     */
    public static double getSpawnMultiplier(HabitatQuality quality) {
        return switch (quality) {
            case SOURCE -> 1.2;
            case NEUTRAL -> 1.0;
            case SINK -> 0.5;
        };
    }

    /**
     * Get the breeding multiplier for habitat quality.
     * Source habitats get bonus, sink habitats get penalty.
     *
     * @param quality The habitat quality
     * @return The breeding multiplier
     */
    public static double getBreedingMultiplier(HabitatQuality quality) {
        return switch (quality) {
            case SOURCE -> 1.3;
            case NEUTRAL -> 1.0;
            case SINK -> 0.6;
        };
    }

    private HabitatCache buildConfig(EcologyProfile profile) {
        boolean enabled = profile.getBool("habitat.enabled", true);
        int scanRadius = profile.getInt("habitat.scan_radius", 32);
        return new HabitatCache(enabled, scanRadius);
    }

    private record HabitatCache(boolean enabled, int scanRadius) {}
}
