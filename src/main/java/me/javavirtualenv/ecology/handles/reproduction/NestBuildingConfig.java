package me.javavirtualenv.ecology.handles.reproduction;

import java.util.Map;

/**
 * Configuration for nest building behavior.
 */
public record NestBuildingConfig(
    String nestType,
    double buildingSpeed,
    int nestSize,
    int searchRadius,
    int maxMaterials,
    boolean preferShelter,
    boolean territorialDefense,
    Map<String, Integer> requiredMaterials
) {
    public boolean isTreeNest() {
        return "tree".equals(nestType) || "canopy".equals(nestType);
    }

    public boolean isGroundNest() {
        return "ground".equals(nestType) || "scrape".equals(nestType);
    }

    public boolean isBurrowNest() {
        return "burrow".equals(nestType) || "underground".equals(nestType);
    }

    public boolean isSandNest() {
        return "sand".equals(nestType) || "beach".equals(nestType);
    }
}
