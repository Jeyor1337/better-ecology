package me.javavirtualenv.ecology.handles.reproduction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks nest state for an entity.
 */
public class NestData {
    private BlockPos nestLocation;
    private double nestQuality;
    private int buildingProgress;
    private int collectedMaterials;
    private final Map<String, Integer> materials;
    private final NestBuildingConfig config;
    private int disturbanceCount;
    private long lastDisturbanceTime;
    private boolean isAbandoned;

    public NestData(NestBuildingConfig config) {
        this.config = config;
        this.nestQuality = 0.0;
        this.buildingProgress = 0;
        this.collectedMaterials = 0;
        this.materials = new HashMap<>();
        this.disturbanceCount = 0;
        this.isAbandoned = false;
    }

    public boolean hasNest() {
        return nestLocation != null && !isAbandoned;
    }

    public BlockPos getNestLocation() {
        return nestLocation;
    }

    public void setNestLocation(BlockPos location) {
        this.nestLocation = location;
    }

    public double getNestQuality() {
        return nestQuality;
    }

    public void setNestQuality(double quality) {
        this.nestQuality = Math.max(0.0, Math.min(1.0, quality));
    }

    public int getBuildingProgress() {
        return buildingProgress;
    }

    public void addProgress(int progress) {
        this.buildingProgress = Math.min(100, this.buildingProgress + progress);
    }

    public int getCollectedMaterials() {
        return collectedMaterials;
    }

    public void addMaterial(String materialType, int amount) {
        int current = materials.getOrDefault(materialType, 0);
        materials.put(materialType, current + amount);
        collectedMaterials += amount;

        // Update quality based on materials
        recalculateQuality();
    }

    public int getMaterialCount(String materialType) {
        return materials.getOrDefault(materialType, 0);
    }

    public Map<String, Integer> getMaterials() {
        return new HashMap<>(materials);
    }

    public boolean isComplete() {
        return buildingProgress >= 100;
    }

    public void recordDisturbance(Level level) {
        disturbanceCount++;
        lastDisturbanceTime = level.getGameTime();

        // Abandon nest if disturbed too often
        if (disturbanceCount > 5) {
            isAbandoned = true;
        }
    }

    public boolean isAbandoned() {
        return isAbandoned;
    }

    public long getTimeSinceLastDisturbance(Level level) {
        return level.getGameTime() - lastDisturbanceTime;
    }

    public boolean canUseNest(Level level) {
        return !isAbandoned && hasNest() &&
               getTimeSinceLastDisturbance(level) > 600; // 30 seconds cooldown
    }

    private void recalculateQuality() {
        if (config.requiredMaterials().isEmpty()) {
            nestQuality = 0.5;
            return;
        }

        int totalRequired = 0;
        int totalCollected = 0;

        for (var entry : config.requiredMaterials().entrySet()) {
            String material = entry.getKey();
            int required = entry.getValue();
            int collected = materials.getOrDefault(material, 0);

            totalRequired += required;
            totalCollected += Math.min(collected, required);
        }

        if (totalRequired > 0) {
            nestQuality = (double) totalCollected / totalRequired;
        }

        // Bonus for having all required materials
        if (totalCollected >= totalRequired) {
            nestQuality = Math.min(1.0, nestQuality + 0.2);
        }
    }

    public void reset() {
        nestLocation = null;
        nestQuality = 0.0;
        buildingProgress = 0;
        collectedMaterials = 0;
        materials.clear();
        disturbanceCount = 0;
        isAbandoned = false;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        if (nestLocation != null) {
            tag.putInt("NestX", nestLocation.getX());
            tag.putInt("NestY", nestLocation.getY());
            tag.putInt("NestZ", nestLocation.getZ());
        }
        tag.putDouble("NestQuality", nestQuality);
        tag.putInt("BuildingProgress", buildingProgress);
        tag.putInt("CollectedMaterials", collectedMaterials);
        tag.putInt("DisturbanceCount", disturbanceCount);
        tag.putLong("LastDisturbanceTime", lastDisturbanceTime);
        tag.putBoolean("IsAbandoned", isAbandoned);

        CompoundTag materialsTag = new CompoundTag();
        for (var entry : materials.entrySet()) {
            materialsTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("Materials", materialsTag);

        return tag;
    }

    public static NestData fromNbt(CompoundTag tag, NestBuildingConfig config) {
        NestData data = new NestData(config);

        if (tag.contains("NestX")) {
            int x = tag.getInt("NestX");
            int y = tag.getInt("NestY");
            int z = tag.getInt("NestZ");
            data.nestLocation = new BlockPos(x, y, z);
        }

        data.nestQuality = tag.getDouble("NestQuality");
        data.buildingProgress = tag.getInt("BuildingProgress");
        data.collectedMaterials = tag.getInt("CollectedMaterials");
        data.disturbanceCount = tag.getInt("DisturbanceCount");
        data.lastDisturbanceTime = tag.getLong("LastDisturbanceTime");
        data.isAbandoned = tag.getBoolean("IsAbandoned");

        if (tag.contains("Materials")) {
            CompoundTag materialsTag = tag.getCompound("Materials");
            for (String key : materialsTag.getAllKeys()) {
                data.materials.put(key, materialsTag.getInt(key));
            }
        }

        return data;
    }

    public static NestData fromNbt(CompoundTag tag) {
        return fromNbt(tag, new NestBuildingConfig("ground", 1.0, 3, 32, 64, true, true, Map.of()));
    }
}
