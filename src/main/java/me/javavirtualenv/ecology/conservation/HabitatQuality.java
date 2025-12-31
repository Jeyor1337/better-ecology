package me.javavirtualenv.ecology.conservation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Enum representing habitat quality levels for source-sink dynamics.
 * Source habitats are high quality and export individuals,
 * Sink habitats are low quality and need immigration to persist.
 */
public enum HabitatQuality {
    /**
     * High quality habitat with resources above sustainable levels.
     * Net exporter of individuals - supports population growth.
     */
    SOURCE(1.2, "source"),

    /**
     * Medium quality habitat with balanced resources.
     * Population remains stable without immigration.
     */
    NEUTRAL(1.0, "neutral"),

    /**
     * Low quality habitat with insufficient resources.
     * Population declines without immigration - population sink.
     */
    SINK(0.5, "sink");

    private final double populationMultiplier;
    private final String serializedName;

    HabitatQuality(double populationMultiplier, String serializedName) {
        this.populationMultiplier = populationMultiplier;
        this.serializedName = serializedName;
    }

    /**
     * Get the population growth multiplier for this habitat quality.
     * Source habitats get bonus, sink habitats get penalty.
     */
    public double getPopulationMultiplier() {
        return populationMultiplier;
    }

    /**
     * Get the serializable name for this habitat quality.
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Evaluate habitat quality at a specific position for an entity type.
     * Considers food availability, water proximity, shelter coverage, and biome suitability.
     *
     * @param level The server level
     * @param pos The position to evaluate
     * @param entityType The entity type to evaluate for
     * @return The habitat quality assessment
     */
    public static HabitatQuality evaluateHabitat(ServerLevel level, BlockPos pos, EntityType<?> entityType) {
        HabitatFactors factors = calculateHabitatFactors(level, pos);

        // Calculate quality score (0-100)
        double foodScore = calculateFoodScore(factors);
        double waterScore = calculateWaterScore(factors);
        double shelterScore = calculateShelterScore(factors);
        double biomeScore = calculateBiomeScore(level, pos, entityType);

        // Weighted average of all factors
        double totalScore = (foodScore * 0.35) + (waterScore * 0.25) +
                           (shelterScore * 0.25) + (biomeScore * 0.15);

        // Determine habitat quality based on total score
        if (totalScore >= 70) {
            return SOURCE;
        } else if (totalScore >= 40) {
            return NEUTRAL;
        } else {
            return SINK;
        }
    }

    /**
     * Calculate all habitat factors for a position.
     * Scans the surrounding area for resources.
     */
    private static HabitatFactors calculateHabitatFactors(ServerLevel level, BlockPos pos) {
        int foodCount = 0;
        int waterDistance = -1;
        int shelterCount = 0;

        // Scan 32 block radius for food and shelter
        int scanRadius = 32;
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > scanRadius) {
                        continue;
                    }

                    BlockPos checkPos = pos.offset(x, y, z);
                    BlockState blockState = level.getBlockState(checkPos);

                    // Count food sources (grass, crops)
                    if (isFoodBlock(blockState)) {
                        foodCount++;
                    }

                    // Count shelter (trees, solid blocks above ground)
                    if (isShelterBlock(blockState) && y > 0) {
                        shelterCount++;
                    }

                    // Find nearest water source
                    if (waterDistance == -1 && isWaterBlock(blockState)) {
                        waterDistance = (int) Math.sqrt(x * x + y * y + z * z);
                    }
                }
            }
        }

        // If no water found, set large distance
        if (waterDistance == -1) {
            waterDistance = 100;
        }

        return new HabitatFactors(foodCount, waterDistance, shelterCount);
    }

    /**
     * Calculate food availability score (0-100).
     * High food: >50 blocks, Medium: 10-50, Low: <10
     */
    private static double calculateFoodScore(HabitatFactors factors) {
        int foodCount = factors.foodCount();

        if (foodCount >= 50) {
            return 100;
        } else if (foodCount >= 10) {
            // Linear interpolation between 10 and 50
            return 50 + ((foodCount - 10) / 40.0) * 50;
        } else {
            // Linear interpolation between 0 and 10
            return (foodCount / 10.0) * 50;
        }
    }

    /**
     * Calculate water proximity score (0-100).
     * Nearby water: <8 blocks, Medium: 8-32, Far: >32
     */
    private static double calculateWaterScore(HabitatFactors factors) {
        int waterDistance = factors.waterDistance();

        if (waterDistance <= 8) {
            return 100;
        } else if (waterDistance <= 32) {
            // Linear interpolation from 8 to 32
            return 100 - ((waterDistance - 8) / 24.0) * 60;
        } else {
            // Penalty for distant water
            return Math.max(0, 40 - ((waterDistance - 32) / 10.0) * 10);
        }
    }

    /**
     * Calculate shelter coverage score (0-100).
     * High shelter: >30 blocks, Medium: 10-30, Low: <10
     */
    private static double calculateShelterScore(HabitatFactors factors) {
        int shelterCount = factors.shelterCount();

        if (shelterCount >= 30) {
            return 100;
        } else if (shelterCount >= 10) {
            // Linear interpolation between 10 and 30
            return 50 + ((shelterCount - 10) / 20.0) * 50;
        } else {
            // Linear interpolation between 0 and 10
            return (shelterCount / 10.0) * 50;
        }
    }

    /**
     * Calculate biome suitability score (0-100).
     * Checks if the biome is appropriate for the entity type.
     */
    private static double calculateBiomeScore(ServerLevel level, BlockPos pos, EntityType<?> entityType) {
        // Default to neutral biome score
        // Specific entities could override this with biome preferences
        return 70.0;
    }

    /**
     * Check if a block is a food source.
     * Includes grass, crops, and other vegetation.
     */
    private static boolean isFoodBlock(BlockState blockState) {
        return blockState.is(BlockTags.LEAVES) ||
               blockState.is(Blocks.GRASS) ||
               blockState.is(Blocks.TALL_GRASS) ||
               blockState.is(Blocks.FERN) ||
               blockState.is(Blocks.LARGE_FERN) ||
               blockState.is(BlockTags.CROPS) ||
               blockState.is(Blocks.CARROTS) ||
               blockState.is(Blocks.POTATOES) ||
               blockState.is(Blocks.WHEAT) ||
               blockState.is(Blocks.BEETROOTS) ||
               blockState.is(Blocks.MELON_STEM) ||
               blockState.is(Blocks.PUMPKIN_STEM) ||
               blockState.is(Blocks.SWEET_BERRY_BUSH);
    }

    /**
     * Check if a block is a shelter.
     * Includes logs, planks, and solid blocks that provide cover.
     */
    private static boolean isShelterBlock(BlockState blockState) {
        return blockState.is(BlockTags.LOGS) ||
               blockState.is(BlockTags.PLANKS) ||
               blockState.is(BlockTags.LEAVES) ||
               blockState.is(Blocks.STONE) ||
               blockState.is(Blocks.DIRT) ||
               blockState.is(Blocks.COBBLESTONE) ||
               blockState.is(Blocks.OAK_PLANKS) ||
               blockState.is(Blocks.SPRUCE_PLANKS) ||
               blockState.is(Blocks.BIRCH_PLANKS) ||
               blockState.is(Blocks.JUNGLE_PLANKS) ||
               blockState.is(Blocks.ACACIA_PLANKS) ||
               blockState.is(Blocks.DARK_OAK_PLANKS) ||
               blockState.is(Blocks.MANGROVE_PLANKS);
    }

    /**
     * Check if a block is water.
     */
    private static boolean isWaterBlock(BlockState blockState) {
        return blockState.getFluidState().is(Fluids.WATER) ||
               blockState.is(Blocks.WATER) ||
               blockState.is(Blocks.SEAGRASS) ||
               blockState.is(Blocks.KELP_PLANT);
    }

    /**
     * Record holding calculated habitat factors.
     */
    private record HabitatFactors(int foodCount, int waterDistance, int shelterCount) {}
}
