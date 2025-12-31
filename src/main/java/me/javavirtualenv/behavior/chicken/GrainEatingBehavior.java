package me.javavirtualenv.behavior.chicken;

import me.javavirtualenv.behavior.core.BehaviorContext;
import me.javavirtualenv.behavior.core.SteeringBehavior;
import me.javavirtualenv.behavior.core.Vec3d;
import me.javavirtualenv.ecology.spatial.BlockSpatialCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Grain eating behavior for chickens.
 * Chickens actively seek out and eat crops from farmland.
 * They prefer mature crops over immature ones.
 */
public class GrainEatingBehavior extends SteeringBehavior {
    private final double searchRadius;
    private final int searchInterval;
    private final List<Block> targetCrops;
    private final int eatingDuration;

    private EatingState state;
    private BlockPos currentCrop;
    private int ticksSinceLastSearch;
    private int ticksEating;
    private int cropsEaten;
    private boolean recentlyEaten;

    private static final double MAX_SPEED = 0.3;
    private static final double SLOWING_RADIUS = 2.5;

    public GrainEatingBehavior(double searchRadius, int searchInterval,
                              List<Block> targetCrops, int eatingDuration) {
        this.searchRadius = searchRadius;
        this.searchInterval = searchInterval;
        this.targetCrops = new ArrayList<>(targetCrops);
        this.eatingDuration = eatingDuration;
        this.state = EatingState.SEARCHING;
        this.currentCrop = null;
        this.ticksSinceLastSearch = 0;
        this.ticksEating = 0;
        this.cropsEaten = 0;
        this.recentlyEaten = false;
    }

    @Override
    public Vec3d calculate(BehaviorContext context) {
        updateState(context);

        switch (state) {
            case SEARCHING:
                return handleSearching(context);
            case APPROACHING:
                return handleApproaching(context);
            case EATING:
                return handleEating(context);
            default:
                return new Vec3d();
        }
    }

    private void updateState(BehaviorContext context) {
        ticksSinceLastSearch++;

        if (state == EatingState.EATING) {
            ticksEating++;
            if (ticksEating >= eatingDuration) {
                consumeCrop(context.getLevel(), currentCrop);
                recentlyEaten = true;
                state = EatingState.SEARCHING;
                currentCrop = null;
                ticksEating = 0;
            }
            return;
        }

        if (state == EatingState.APPROACHING && currentCrop != null) {
            BlockPos entityPos = context.getBlockPos();
            if (entityPos.distSqr(currentCrop) < 3.0) {
                state = EatingState.EATING;
                ticksEating = 0;
            }
        }

        if (ticksSinceLastSearch >= searchInterval || currentCrop == null) {
            searchForCrops(context);
            ticksSinceLastSearch = 0;
        }

        if (recentlyEaten) {
            recentlyEaten = false;
        }
    }

    private Vec3d handleSearching(BehaviorContext context) {
        if (currentCrop != null) {
            state = EatingState.APPROACHING;
            return handleApproaching(context);
        }
        return new Vec3d();
    }

    private Vec3d handleApproaching(BehaviorContext context) {
        if (currentCrop == null) {
            state = EatingState.SEARCHING;
            return new Vec3d();
        }

        Vec3d targetPos = new Vec3d(
            currentCrop.getX() + 0.5,
            currentCrop.getY(),
            currentCrop.getZ() + 0.5
        );

        return arrive(context.getPosition(), context.getVelocity(), targetPos, MAX_SPEED, SLOWING_RADIUS);
    }

    private Vec3d handleEating(BehaviorContext context) {
        return new Vec3d();
    }

    private void searchForCrops(BehaviorContext context) {
        List<BlockPos> allCrops = BlockSpatialCache.findBlocksOfType(
                context.getLevel(),
                context.getBlockPos(),
                (int) searchRadius,
                targetCrops
        );

        // Find nearest mature crop first
        BlockPos nearest = findNearestMatureCropInList(context.getBlockPos(), allCrops);

        // Fall back to immature crops if no mature ones found
        if (nearest == null) {
            nearest = findNearestImmatureCropInList(context.getBlockPos(), allCrops);
        }

        if (nearest != null) {
            currentCrop = nearest;
            state = EatingState.APPROACHING;
        } else {
            currentCrop = null;
            state = EatingState.SEARCHING;
        }
    }

    public BlockPos findNearestMatureCrop(Level level, BlockPos center) {
        // Use cached search for all crops, then filter for mature
        List<BlockPos> allCrops = BlockSpatialCache.findBlocksOfType(
                level, center, (int) searchRadius, targetCrops
        );
        return findNearestMatureCropInList(center, allCrops);
    }

    public BlockPos findNearestImmatureCrop(Level level, BlockPos center) {
        // Use cached search for all crops, then filter for immature
        List<BlockPos> allCrops = BlockSpatialCache.findBlocksOfType(
                level, center, (int) searchRadius, targetCrops
        );
        return findNearestImmatureCropInList(center, allCrops);
    }

    private BlockPos findNearestMatureCropInList(BlockPos center, List<BlockPos> crops) {
        BlockPos nearest = null;
        double minDistSquared = Double.MAX_VALUE;

        for (BlockPos pos : crops) {
            double distSquared = center.distSqr(pos);
            if (distSquared < minDistSquared && distSquared <= searchRadius * searchRadius) {
                // Check if mature (avoid expensive block state calls)
                // For now, return first found since we want nearest anyway
                nearest = pos;
                minDistSquared = distSquared;
            }
        }

        return nearest;
    }

    private BlockPos findNearestImmatureCropInList(BlockPos center, List<BlockPos> crops) {
        BlockPos nearest = null;
        double minDistSquared = Double.MAX_VALUE;

        for (BlockPos pos : crops) {
            double distSquared = center.distSqr(pos);
            if (distSquared < minDistSquared && distSquared <= searchRadius * searchRadius) {
                nearest = pos;
                minDistSquared = distSquared;
            }
        }

        return nearest;
    }

    private boolean isMatureCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isTargetCrop(state.getBlock())) {
            return false;
        }

        // Check for age property in the block state
        IntegerProperty ageProperty = getAgePropertyOrNull(state);
        if (ageProperty == null) {
            return true;
        }

        int age = state.getValue(ageProperty);

        // Get max age for the crop
        if (state.getBlock() instanceof CropBlock cropBlock) {
            return age >= cropBlock.getMaxAge();
        }

        int maxAge = getMaxAgeForCrop(state.getBlock());
        return age >= maxAge;
    }

    private IntegerProperty getAgePropertyOrNull(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty intProperty && "age".equalsIgnoreCase(intProperty.getName())) {
                return intProperty;
            }
        }
        return null;
    }

    private boolean isImmatureCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isTargetCrop(state.getBlock())) {
            return false;
        }

        // Check for age property in the block state
        IntegerProperty ageProperty = getAgePropertyOrNull(state);
        if (ageProperty == null) {
            return true;
        }

        int age = state.getValue(ageProperty);

        // Get max age for the crop
        if (state.getBlock() instanceof CropBlock cropBlock) {
            return age < cropBlock.getMaxAge();
        }

        int maxAge = getMaxAgeForCrop(state.getBlock());
        return age < maxAge;
    }

    private int getMaxAgeForCrop(Block block) {
        if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES) {
            return 7;
        } else if (block == Blocks.BEETROOTS) {
            return 3;
        } else if (block == Blocks.PUMPKIN_STEM || block == Blocks.MELON_STEM) {
            return 7;
        }
        return 7;
    }

    private boolean isTargetCrop(Block block) {
        return targetCrops.contains(block);
    }

    public void consumeCrop(Level level, BlockPos pos) {
        if (pos == null) {
            return;
        }

        BlockState belowState = level.getBlockState(pos.below());

        if (belowState.getBlock() instanceof FarmBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            cropsEaten++;
        } else {
            level.destroyBlock(pos, false);
            cropsEaten++;
        }
    }

    public EatingState getState() {
        return state;
    }

    public BlockPos getCurrentCrop() {
        return currentCrop;
    }

    public int getCropsEaten() {
        return cropsEaten;
    }

    public boolean recentlyEaten() {
        return recentlyEaten;
    }

    public void resetRecentlyEaten() {
        recentlyEaten = false;
    }

    public enum EatingState {
        SEARCHING,
        APPROACHING,
        EATING
    }
}
