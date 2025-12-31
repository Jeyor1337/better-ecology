package me.javavirtualenv.ecology.conservation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-wide registry for tracking family trees and genetic relationships.
 * Maintains genetic data for all entities and calculates relationship distances.
 *
 * Relationship distance degrees:
 * - Degree 0: Same animal
 * - Degree 1: Parent-child, full siblings, half-siblings
 * - Degree 2: Grandparent-grandchild, aunt/uncle-niece/nephew, first cousins
 * - Degree 3: Great-grandparent, great-aunt/uncle, second cousins
 * - Degree 4+: More distant relations
 */
public class LineageRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("BetterEcology");

    private static final Map<UUID, GeneticDiversityComponent> geneticData = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastAccessTime = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes
    private static long lastCleanupTime = System.currentTimeMillis();

    /**
     * Registers a birth event, recording parentage of the offspring.
     *
     * @param babyUuid UUID of the baby
     * @param motherUuid UUID of the mother
     * @param fatherUuid UUID of the father (may be null)
     */
    public static void registerBirth(UUID babyUuid, UUID motherUuid, @Nullable UUID fatherUuid) {
        GeneticDiversityComponent babyData = getOrCreateGeneticData(babyUuid);
        babyData.setParents(motherUuid, fatherUuid);

        GeneticDiversityComponent motherData = getOrCreateGeneticData(motherUuid);
        motherData.addOffspring(babyUuid);

        if (fatherUuid != null) {
            GeneticDiversityComponent fatherData = getOrCreateGeneticData(fatherUuid);
            fatherData.addOffspring(babyUuid);
        }

        LOGGER.debug("Registered birth: baby={}, mother={}, father={}", babyUuid, motherUuid, fatherUuid);
    }

    /**
     * Registers genetic data loaded from NBT.
     *
     * @param entityUuid UUID of the entity
     * @param geneticTag NBT tag containing genetic data
     */
    public static void registerFromNbt(UUID entityUuid, CompoundTag geneticTag) {
        if (geneticTag == null || geneticTag.isEmpty()) {
            return;
        }

        GeneticDiversityComponent data = getOrCreateGeneticData(entityUuid);
        if (geneticTag.contains("mother_uuid")) {
            data.setMother(geneticTag.getUUID("mother_uuid"));
        }
        if (geneticTag.contains("father_uuid")) {
            data.setFather(geneticTag.getUUID("father_uuid"));
        }
        if (geneticTag.contains("offspring")) {
            int[] uuidArray = geneticTag.getIntArray("offspring");
            for (int i = 0; i < uuidArray.length; i += 4) {
                if (i + 3 < uuidArray.length) {
                    long mostSigBits = ((long) uuidArray[i] << 32) | (uuidArray[i + 1] & 0xFFFFFFFFL);
                    long leastSigBits = ((long) uuidArray[i + 2] << 32) | (uuidArray[i + 3] & 0xFFFFFFFFL);
                    data.addOffspring(new UUID(mostSigBits, leastSigBits));
                }
            }
        }
    }

    /**
     * Gets or creates genetic data for an entity.
     *
     * @param entityUuid UUID of the entity
     * @return Genetic data component
     */
    public static GeneticDiversityComponent getOrCreateGeneticData(UUID entityUuid) {
        return geneticData.computeIfAbsent(entityUuid, uuid -> {
            CompoundTag tag = new CompoundTag();
            return new GeneticDiversityComponent(tag);
        });
    }

    /**
     * Gets genetic data for an entity without creating it.
     *
     * @param entityUuid UUID of the entity
     * @return Genetic data component, or null if not found
     */
    @Nullable
    public static GeneticDiversityComponent getGeneticData(UUID entityUuid) {
        GeneticDiversityComponent data = geneticData.get(entityUuid);
        if (data != null) {
            lastAccessTime.put(entityUuid, System.currentTimeMillis());
        }
        return data;
    }

    /**
     * Calculates relationship distance between two entities using breadth-first search.
     *
     * @param uuid1 UUID of first entity
     * @param uuid2 UUID of second entity
     * @return Relationship distance (0 = same, 1 = immediate family, etc.), or -1 if unrelated
     */
    public static int getRelationshipDistance(UUID uuid1, UUID uuid2) {
        if (uuid1.equals(uuid2)) {
            return 0;
        }

        Map<UUID, Integer> visited1 = new HashMap<>();
        Map<UUID, Integer> visited2 = new HashMap<>();

        List<UUID> queue1 = new ArrayList<>();
        List<UUID> queue2 = new ArrayList<>();

        queue1.add(uuid1);
        visited1.put(uuid1, 0);

        queue2.add(uuid2);
        visited2.put(uuid2, 0);

        while (!queue1.isEmpty() || !queue2.isEmpty()) {
            Integer distance = expandSearch(queue1, visited1, visited2);
            if (distance != null) {
                return distance;
            }

            distance = expandSearch(queue2, visited2, visited1);
            if (distance != null) {
                return distance;
            }
        }

        return -1;
    }

    /**
     * Expands the BFS search one level from the given queue.
     *
     * @param queue Queue to expand
     * @param visited Visited map for this side
     * @param otherVisited Visited map from other side (to check for intersection)
     * @return Total distance if intersection found, null otherwise
     */
    @Nullable
    private static Integer expandSearch(List<UUID> queue, Map<UUID, Integer> visited, Map<UUID, Integer> otherVisited) {
        if (queue.isEmpty()) {
            return null;
        }

        UUID current = queue.remove(0);
        int currentDistance = visited.get(current);

        if (otherVisited.containsKey(current)) {
            return currentDistance + otherVisited.get(current);
        }

        if (currentDistance >= 3) {
            return null;
        }

        GeneticDiversityComponent data = geneticData.get(current);
        if (data == null) {
            return null;
        }

        List<UUID> relatives = new ArrayList<>();

        UUID mother = data.getMother();
        if (mother != null) {
            relatives.add(mother);
        }

        UUID father = data.getFather();
        if (father != null) {
            relatives.add(father);
        }

        relatives.addAll(data.getOffspring());

        for (UUID relative : relatives) {
            if (!visited.containsKey(relative)) {
                visited.put(relative, currentDistance + 1);
                queue.add(relative);

                if (otherVisited.containsKey(relative)) {
                    return currentDistance + 1 + otherVisited.get(relative);
                }
            }
        }

        return null;
    }

    /**
     * Finds the most recent common ancestor between two entities.
     *
     * @param uuid1 UUID of first entity
     * @param uuid2 UUID of second entity
     * @return UUID of common ancestor, or null if none found
     */
    @Nullable
    public static UUID getCommonAncestor(UUID uuid1, UUID uuid2) {
        Set<UUID> ancestors1 = getAllAncestors(uuid1, 4);
        Set<UUID> ancestors2 = getAllAncestors(uuid2, 4);

        ancestors1.retainAll(ancestors2);

        if (ancestors1.isEmpty()) {
            return null;
        }

        return ancestors1.iterator().next();
    }

    /**
     * Gets all ancestors of an entity up to a certain depth.
     *
     * @param entityUuid UUID of the entity
     * @param maxDepth Maximum depth to search
     * @return Set of ancestor UUIDs
     */
    private static Set<UUID> getAllAncestors(UUID entityUuid, int maxDepth) {
        Set<UUID> ancestors = new HashSet<>();
        List<UUID> currentGeneration = List.of(entityUuid);

        for (int depth = 0; depth < maxDepth && !currentGeneration.isEmpty(); depth++) {
            List<UUID> nextGeneration = new ArrayList<>();

            for (UUID uuid : currentGeneration) {
                GeneticDiversityComponent data = geneticData.get(uuid);
                if (data == null) {
                    continue;
                }

                UUID mother = data.getMother();
                if (mother != null) {
                    ancestors.add(mother);
                    nextGeneration.add(mother);
                }

                UUID father = data.getFather();
                if (father != null) {
                    ancestors.add(father);
                    nextGeneration.add(father);
                }
            }

            currentGeneration = nextGeneration;
        }

        return ancestors;
    }

    /**
     * Calculates relatedness coefficient between two entities.
     * Based on the proportion of shared genes (0.0 = unrelated, 1.0 = clones/identical twins).
     *
     * @param uuid1 UUID of first entity
     * @param uuid2 UUID of second entity
     * @return Relatedness coefficient (0.5 = parent-child or siblings, 0.25 = half-siblings, etc.)
     */
    public static double calculateRelatedness(UUID uuid1, UUID uuid2) {
        int distance = getRelationshipDistance(uuid1, uuid2);

        if (distance < 0) {
            return 0.0;
        }

        if (distance == 0) {
            return 1.0;
        }

        return switch (distance) {
            case 1 -> 0.5;
            case 2 -> 0.25;
            case 3 -> 0.125;
            case 4 -> 0.0625;
            default -> 0.0;
        };
    }

    /**
     * Checks if two entities are too closely related for breeding.
     *
     * @param uuid1 UUID of first entity
     * @param uuid2 UUID of second entity
     * @return true if too closely related (degree 0-2)
     */
    public static boolean areTooCloselyRelated(UUID uuid1, UUID uuid2) {
        int distance = getRelationshipDistance(uuid1, uuid2);
        return distance >= 0 && distance <= 2;
    }

    /**
     * Cleans up entries for entities that no longer exist in the level.
     * Should be called periodically to prevent memory leaks.
     *
     * @param level The server level
     */
    public static void cleanup(ServerLevel level) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }

        lastCleanupTime = currentTime;
        Set<UUID> toRemove = new HashSet<>();

        for (Map.Entry<UUID, Long> entry : lastAccessTime.entrySet()) {
            UUID uuid = entry.getKey();
            long lastAccess = entry.getValue();

            if (currentTime - lastAccess > CLEANUP_INTERVAL_MS) {
                Entity entity = level.getEntity(uuid);
                if (entity == null || !entity.isAlive()) {
                    toRemove.add(uuid);
                }
            }
        }

        for (UUID uuid : toRemove) {
            geneticData.remove(uuid);
            lastAccessTime.remove(uuid);
        }

        if (!toRemove.isEmpty()) {
            LOGGER.debug("Cleaned up {} genetic data entries", toRemove.size());
        }
    }

    /**
     * Resets all registry data (called on server shutdown).
     */
    public static void reset() {
        geneticData.clear();
        lastAccessTime.clear();
        lastCleanupTime = System.currentTimeMillis();
        LOGGER.debug("Lineage registry reset");
    }
}
