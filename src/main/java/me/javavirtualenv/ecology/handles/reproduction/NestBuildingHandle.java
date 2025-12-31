package me.javavirtualenv.ecology.handles.reproduction;

import me.javavirtualenv.behavior.reproduction.NestBuildingGoal;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.mixin.MobAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Handle for managing nest building behavior in animals.
 * <p>
 * This handle tracks nest locations, quality, materials, and building progress.
 * It provides the data layer for the NestBuildingGoal AI.
 */
public final class NestBuildingHandle implements EcologyHandle {
    private static final String CACHE_KEY = "better-ecology:nest-building-cache";
    private static final String NEST_DATA_KEY = "nestData";

    @Override
    public String id() {
        return "nest_building";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile.getBool("nest_building.enabled", false);
    }

    @Override
    public void registerGoals(Mob mob, EcologyComponent component, EcologyProfile profile) {
        if (!(mob instanceof Animal animal)) {
            return;
        }

        NestBuildingConfig config = profile.cached(CACHE_KEY, () -> buildConfig(profile));
        int priority = profile.getInt("ai_priority_framework.reproduction.nest_building", 7);
        MobAccessor accessor = (MobAccessor) mob;
        accessor.betterEcology$getGoalSelector().addGoal(priority,
            new NestBuildingGoal(animal, config));
    }

    @Override
    public void readNbt(Mob mob, EcologyComponent component, EcologyProfile profile, CompoundTag tag) {
        if (!tag.contains(id())) {
            return;
        }

        CompoundTag handleTag = tag.getCompound(id());
        if (handleTag.contains(NEST_DATA_KEY)) {
            NestData nestData = NestData.fromNbt(handleTag.getCompound(NEST_DATA_KEY));
            component.setData(id() + "." + NEST_DATA_KEY, nestData);
        }
    }

    @Override
    public void writeNbt(Mob mob, EcologyComponent component, EcologyProfile profile, CompoundTag tag) {
        NestData nestData = getNestData(component);
        if (nestData == null || !nestData.hasNest()) {
            return;
        }

        CompoundTag handleTag = new CompoundTag();
        handleTag.put(NEST_DATA_KEY, nestData.toNbt());
        tag.put(id(), handleTag);
    }

    /**
     * Get or create nest data for an entity.
     */
    public static NestData getOrCreateNestData(EcologyComponent component, NestBuildingConfig config) {
        String key = "nest_building." + NEST_DATA_KEY;
        NestData nestData = component.getData(key);

        if (nestData == null) {
            nestData = new NestData(config);
            component.setData(key, nestData);
        }

        return nestData;
    }

    /**
     * Get nest data for an entity without creating.
     */
    public static NestData getNestData(EcologyComponent component) {
        return component.getData("nest_building." + NEST_DATA_KEY);
    }

    private NestBuildingConfig buildConfig(EcologyProfile profile) {
        String nestType = profile.getString("nest_building.nest_type", "ground");
        double buildingSpeed = profile.getDouble("nest_building.building_speed", 1.0);
        int nestSize = profile.getInt("nest_building.nest_size", 3);
        int searchRadius = profile.getInt("nest_building.search_radius", 32);
        int maxMaterials = profile.getInt("nest_building.max_materials", 64);
        boolean preferShelter = profile.getBool("nest_building.prefer_shelter", true);
        boolean territorialDefense = profile.getBool("nest_building.territorial_defense", true);

        Map<String, Integer> requiredMaterials = new HashMap<>();
        var materialsList = profile.getList("nest_building.required_materials");
        for (var entry : materialsList.entrySet()) {
            if (entry.getValue() instanceof Number) {
                requiredMaterials.put(entry.getKey(), ((Number) entry.getValue()).intValue());
            }
        }

        return new NestBuildingConfig(
            nestType,
            buildingSpeed,
            nestSize,
            searchRadius,
            maxMaterials,
            preferShelter,
            territorialDefense,
            requiredMaterials
        );
    }
}
