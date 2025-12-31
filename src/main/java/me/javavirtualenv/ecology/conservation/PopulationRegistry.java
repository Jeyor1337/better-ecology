package me.javavirtualenv.ecology.conservation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide population tracking for conservation monitoring.
 * Tracks population counts per species and calculates conservation status.
 *
 * Uses the Minimum Viable Population (MVP) concept from conservation biology:
 * - 50/500 rule: 50 individuals to avoid inbreeding, 500 for long-term evolutionary potential
 * - Scaled for Minecraft gameplay: ~15-30 entities depending on species characteristics
 */
public class PopulationRegistry {
    private static final Map<ResourceLocation, SpeciesData> populations = new ConcurrentHashMap<>();

    /**
     * Get current population count for a species.
     *
     * @param entityType The entity type resource location
     * @return Current population count
     */
    public static int getPopulation(ResourceLocation entityType) {
        SpeciesData data = populations.get(entityType);
        return data != null ? data.count() : 0;
    }

    /**
     * Get population count for a mob entity.
     *
     * @param mob The mob entity
     * @return Current population count for this species
     */
    public static int getPopulation(Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return getPopulation(entityId);
    }

    /**
     * Get conservation status for a species.
     *
     * @param entityType The entity type resource location
     * @param mvpThreshold Minimum viable population threshold
     * @return Current conservation status
     */
    public static ConservationStatus getStatus(ResourceLocation entityType, int mvpThreshold) {
        int population = getPopulation(entityType);
        return ConservationStatus.calculateStatus(population, mvpThreshold);
    }

    /**
     * Get conservation status for a mob entity.
     *
     * @param mob The mob entity
     * @param mvpThreshold Minimum viable population threshold
     * @return Current conservation status
     */
    public static ConservationStatus getStatus(Mob mob, int mvpThreshold) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return getStatus(entityId, mvpThreshold);
    }

    /**
     * Check if a species is endangered (below Allee threshold).
     *
     * @param entityType The entity type resource location
     * @param mvpThreshold Minimum viable population threshold
     * @return true if population is below Allee threshold (50% of MVP)
     */
    public static boolean isEndangered(ResourceLocation entityType, int mvpThreshold) {
        ConservationStatus status = getStatus(entityType, mvpThreshold);
        return status == ConservationStatus.ENDANGERED || status == ConservationStatus.CRITICAL;
    }

    /**
     * Check if a mob's species is endangered.
     *
     * @param mob The mob entity
     * @param mvpThreshold Minimum viable population threshold
     * @return true if species is endangered
     */
    public static boolean isEndangered(Mob mob, int mvpThreshold) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return isEndangered(entityId, mvpThreshold);
    }

    /**
     * Check if a species is in critical state (emergency breeding required).
     *
     * @param entityType The entity type resource location
     * @param mvpThreshold Minimum viable population threshold
     * @return true if population is critical (below 30% of MVP)
     */
    public static boolean isCritical(ResourceLocation entityType, int mvpThreshold) {
        return getStatus(entityType, mvpThreshold) == ConservationStatus.CRITICAL;
    }

    /**
     * Notify registry that an entity has spawned.
     *
     * @param mob The spawned mob
     */
    public static void onEntitySpawned(Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        populations.compute(entityId, (key, data) -> {
            if (data == null) {
                return new SpeciesData(1);
            }
            return new SpeciesData(data.count() + 1);
        });
    }

    /**
     * Notify registry that an entity has despawned or died.
     *
     * @param mob The removed mob
     */
    public static void onEntityDespawned(Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        populations.computeIfPresent(entityId, (key, data) -> {
            int newCount = Math.max(0, data.count() - 1);
            return newCount > 0 ? new SpeciesData(newCount) : null;
        });
    }

    /**
     * Reset all population tracking (called on server shutdown).
     */
    public static void reset() {
        populations.clear();
    }

    /**
     * Get all tracked species data.
     *
     * @return Map of entity types to their population data
     */
    public static Map<ResourceLocation, SpeciesData> getAllPopulations() {
        return Map.copyOf(populations);
    }

    /**
     * Species population data record.
     *
     * @param count Current population count
     */
    public record SpeciesData(int count) {}
}
