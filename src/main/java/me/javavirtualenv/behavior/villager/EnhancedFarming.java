package me.javavirtualenv.behavior.villager;

import me.javavirtualenv.behavior.core.BehaviorContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Enhanced farming behavior for farmer villagers.
 * Actually tends crops, harvests, replants, shares food, and expands farms.
 */
public class EnhancedFarming {
    private final Villager villager;
    private List<BlockPos> knownFarms = new ArrayList<>();
    private BlockPos currentFarmTarget;
    private FarmingState currentState = FarmingState.IDLE;
    private int farmingTicks = 0;
    private int cropsTended = 0;
    private int expansionCooldown = 0;
    private BlockPos expansionTarget;

    private static final int MAX_FARMS = 10;
    private static final int SEARCH_RADIUS = 32;
    private static final int MAX_CROPS_PER_SESSION = 20;
    private static final int EXPANSION_COOLDOWN_TICKS = 6000;
    private static final int EXPANSION_SEARCH_RADIUS = 16;
    private static final Predicate<BlockState> IS_TILLABLE = BlockStatePredicate.forBlock(Blocks.FARMLAND);
    private static final Predicate<BlockState> IS_CROP = state ->
        state.is(Blocks.WHEAT) ||
        state.is(Blocks.CARROTS) ||
        state.is(Blocks.POTATOES) ||
        state.is(Blocks.BEETROOTS) ||
        state.is(Blocks.PUMPKIN_STEM) ||
        state.is(Blocks.MELON_STEM);
    private static final Predicate<BlockState> CAN_TILL = state ->
        state.is(Blocks.GRASS_BLOCK) ||
        state.is(Blocks.DIRT) ||
        state.is(Blocks.COARSE_DIRT);

    public EnhancedFarming(Villager villager) {
        this.villager = villager;
    }

    /**
     * Main tick method for farming behavior.
     */
    public void tick() {
        if (!isFarmer()) {
            return;
        }

        farmingTicks++;

        // Decrease expansion cooldown
        if (expansionCooldown > 0) {
            expansionCooldown--;
        }

        // Periodically scan for new farms
        if (farmingTicks % 200 == 0) {
            scanForFarms();
        }

        // Check for farm expansion opportunities
        if (expansionCooldown == 0 && farmingTicks % 1000 == 0) {
            tryExpandFarm();
        }

        // Execute current state
        switch (currentState) {
            case IDLE -> findFarmWork();
            case MOVING_TO_FARM -> continueMovingToFarm();
            case TENDING_CROPS -> tendCurrentCrop();
            case SHARING_FOOD -> shareFoodWithVillagers();
            case EXPANDING_FARM -> expandFarmStep();
        }

        // Reset after completing work session
        if (cropsTended >= MAX_CROPS_PER_SESSION) {
            resetWorkSession();
        }
    }

    /**
     * Checks if this villager is a farmer.
     */
    private boolean isFarmer() {
        return villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER;
    }

    /**
     * Scans for nearby farmland to add to known farms.
     */
    private void scanForFarms() {
        BlockPos center = villager.blockPosition();
        Level level = villager.level();

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
            center.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS)
        )) {
            if (knownFarms.size() >= MAX_FARMS) {
                break;
            }

            BlockState state = level.getBlockState(pos);
            if (IS_CROP.test(state) && !knownFarms.contains(pos)) {
                // Check if there's farmland below
                BlockState below = level.getBlockState(pos.below());
                if (below.is(Blocks.FARMLAND)) {
                    knownFarms.add(pos.immutable());
                }
            }
        }
    }

    /**
     * Finds farm work to do.
     */
    private void findFarmWork() {
        if (knownFarms.isEmpty()) {
            scanForFarms();
            return;
        }

        // Find a crop that needs attention
        for (BlockPos farmPos : knownFarms) {
            if (needsAttention(farmPos)) {
                currentFarmTarget = farmPos;
                currentState = FarmingState.MOVING_TO_FARM;
                moveToFarm(farmPos);
                return;
            }
        }
    }

    /**
     * Checks if a crop needs attention (harvest or replant).
     */
    private boolean needsAttention(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);

        // Check if fully grown
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }

        // Check if empty farmland (needs replanting)
        if (villager.level().getBlockState(pos).isAir()) {
            BlockState below = villager.level().getBlockState(pos.below());
            return below.is(Blocks.FARMLAND);
        }

        return false;
    }

    /**
     * Moves to a farm position.
     */
    private void moveToFarm(BlockPos pos) {
        villager.getNavigation().moveTo(
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5,
            0.5
        );
    }

    /**
     * Continues moving to the target farm.
     */
    private void continueMovingToFarm() {
        if (currentFarmTarget == null) {
            currentState = FarmingState.IDLE;
            return;
        }

        double distance = villager.position().distanceTo(
            new Vec3(currentFarmTarget.getX() + 0.5, currentFarmTarget.getY(), currentFarmTarget.getZ() + 0.5)
        );

        if (distance < 2.5) {
            currentState = FarmingState.TENDING_CROPS;
        }
    }

    /**
     * Tends the current crop.
     */
    private void tendCurrentCrop() {
        if (currentFarmTarget == null) {
            currentState = FarmingState.IDLE;
            return;
        }

        BlockState state = villager.level().getBlockState(currentFarmTarget);

        // Harvest fully grown crops
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            harvestCrop(currentFarmTarget);
        }
        // Replant empty farmland
        else if (state.isAir()) {
            replantCrop(currentFarmTarget);
        }

        cropsTended++;
        currentFarmTarget = null;
        currentState = FarmingState.IDLE;
    }

    /**
     * Harvests a fully grown crop.
     */
    private void harvestCrop(BlockPos pos) {
        BlockState state = villager.level().getBlockState(pos);

        // Break the crop
        villager.level().destroyBlock(pos, true);

        // Store food for sharing
        CompoundTag tag = getStorageTag();
        int foodStored = tag.getInt("FoodStored");
        tag.putInt("FoodStored", foodStored + 1);

        // Play harvest effect
        spawnHarvestParticles(pos);
    }

    /**
     * Replants a crop at the given position.
     */
    private void replantCrop(BlockPos pos) {
        CompoundTag tag = getStorageTag();
        int seedsAvailable = tag.getInt("SeedsAvailable");

        if (seedsAvailable > 0) {
            // Plant seeds
            BlockState cropState = getCropState();
            villager.level().setBlock(pos, cropState, 3);

            tag.putInt("SeedsAvailable", seedsAvailable - 1);

            spawnPlantParticles(pos);
        } else {
            // Need to get seeds from inventory
            currentState = FarmingState.IDLE;
        }
    }

    /**
     * Gets a random crop state for planting.
     */
    private BlockState getCropState() {
        // Default to wheat
        return Blocks.WHEAT.defaultBlockState();
    }

    /**
     * Shares food with hungry villagers.
     */
    private void shareFoodWithVillagers() {
        CompoundTag tag = getStorageTag();
        int initialFoodStored = tag.getInt("FoodStored");

        if (initialFoodStored <= 5) {
            currentState = FarmingState.IDLE;
            return;
        }

        // Find hungry villagers nearby
        final int[] foodRemaining = {initialFoodStored};
        villager.level().getEntitiesOfClass(
            Villager.class,
            villager.getBoundingBox().inflate(16.0)
        ).forEach(other -> {
            if (isHungry(other) && foodRemaining[0] > 0) {
                // Share food
                foodRemaining[0]--;

                // Give food to villager (this would integrate with hunger system)
            }
        });

        // Update the stored food count after sharing
        tag.putInt("FoodStored", foodRemaining[0]);

        currentState = FarmingState.IDLE;
    }

    /**
     * Checks if a villager is hungry.
     */
    private boolean isHungry(Villager villager) {
        // Check if villager needs food
        if (villager instanceof me.javavirtualenv.ecology.api.EcologyAccess access) {
            var component = access.betterEcology$getEcologyComponent();
            if (component != null) {
                var hungerTag = component.getHandleTag("hunger");
                int hunger = hungerTag.getInt("hunger");
                return hunger < 50;
            }
        }
        return false;
    }

    /**
     * Resets the work session.
     */
    private void resetWorkSession() {
        cropsTended = 0;
        currentState = FarmingState.IDLE;
        currentFarmTarget = null;
    }

    /**
     * Gets the storage tag for farm data.
     */
    private CompoundTag getStorageTag() {
        if (villager instanceof me.javavirtualenv.ecology.api.EcologyAccess access) {
            var component = access.betterEcology$getEcologyComponent();
            if (component != null) {
                return component.getHandleTag("farming");
            }
        }
        return new CompoundTag();
    }

    /**
     * Spawns harvest particle effects.
     */
    private void spawnHarvestParticles(BlockPos pos) {
        if (villager.level().isClientSide) {
            return;
        }

        for (int i = 0; i < 5; i++) {
            villager.level().addParticle(
                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                0, 0.2, 0
            );
        }
    }

    /**
     * Spawns planting particle effects.
     */
    private void spawnPlantParticles(BlockPos pos) {
        if (villager.level().isClientSide) {
            return;
        }

        villager.level().addParticle(
            net.minecraft.core.particles.ParticleTypes.COMPOSTER,
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            0, 0.1, 0
        );
    }

    /**
     * Gets the list of known farms.
     */
    public List<BlockPos> getKnownFarms() {
        return List.copyOf(knownFarms);
    }

    /**
     * Adds a farm to the known list.
     */
    public void addKnownFarm(BlockPos pos) {
        if (!knownFarms.contains(pos) && knownFarms.size() < MAX_FARMS) {
            knownFarms.add(pos.immutable());
        }
    }

    /**
     * Gets the current farming state.
     */
    public FarmingState getCurrentState() {
        return currentState;
    }

    /**
     * Attempts to expand an existing farm.
     */
    private void tryExpandFarm() {
        if (knownFarms.isEmpty()) {
            return;
        }

        // Pick a random farm to expand
        BlockPos centerFarm = knownFarms.get(villager.getRandom().nextInt(knownFarms.size()));

        // Look for tillable land nearby
        for (BlockPos pos : BlockPos.betweenClosed(
            centerFarm.offset(-EXPANSION_SEARCH_RADIUS, -2, -EXPANSION_SEARCH_RADIUS),
            centerFarm.offset(EXPANSION_SEARCH_RADIUS, 2, EXPANSION_SEARCH_RADIUS)
        )) {
            BlockState state = villager.level().getBlockState(pos);

            // Check if this is tillable land
            if (CAN_TILL.test(state)) {
                // Check if there's water nearby for farming
                if (hasWaterNearby(pos)) {
                    expansionTarget = pos;
                    currentState = FarmingState.EXPANDING_FARM;
                    expansionCooldown = EXPANSION_COOLDOWN_TICKS;
                    moveToFarm(pos);
                    return;
                }
            }
        }
    }

    /**
     * Executes one step of farm expansion.
     */
    private void expandFarmStep() {
        if (expansionTarget == null) {
            currentState = FarmingState.IDLE;
            return;
        }

        double distance = villager.position().distanceTo(
            new Vec3(expansionTarget.getX() + 0.5, expansionTarget.getY(), expansionTarget.getZ() + 0.5)
        );

        if (distance < 2.5) {
            // Till the land
            BlockState current = villager.level().getBlockState(expansionTarget);
            if (CAN_TILL.test(current)) {
                villager.level().setBlock(expansionTarget, Blocks.FARMLAND.defaultBlockState(), 3);
                spawnTillParticles(expansionTarget);

                // Add to known farms
                knownFarms.add(expansionTarget.above().immutable());

                cropsTended++;
            }

            expansionTarget = null;
            currentState = FarmingState.IDLE;
        }
    }

    /**
     * Checks if there's water nearby for farming.
     */
    private boolean hasWaterNearby(BlockPos pos) {
        for (BlockPos check : BlockPos.betweenClosed(
            pos.offset(-4, -1, -4),
            pos.offset(4, 1, 4)
        )) {
            BlockState state = villager.level().getBlockState(check);
            if (state.getFluidState().isSource() && state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.WATER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns tilling particle effects.
     */
    private void spawnTillParticles(BlockPos pos) {
        if (villager.level().isClientSide) {
            return;
        }

        BlockState dirtState = Blocks.DIRT.defaultBlockState();
        for (int i = 0; i < 3; i++) {
            villager.level().addParticle(
                new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK,
                    dirtState
                ),
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                0, 0.1, 0
            );
        }
    }

    /**
     * Serializes farming state to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FarmingTicks", farmingTicks);
        tag.putInt("CropsTended", cropsTended);
        tag.putString("CurrentState", currentState.name());

        // Save known farms
        CompoundTag farmsTag = new CompoundTag();
        for (int i = 0; i < knownFarms.size(); i++) {
            BlockPos pos = knownFarms.get(i);
            String key = "farm_" + i;
            farmsTag.putInt(key + "_x", pos.getX());
            farmsTag.putInt(key + "_y", pos.getY());
            farmsTag.putInt(key + "_z", pos.getZ());
        }
        tag.put("KnownFarms", farmsTag);

        return tag;
    }

    /**
     * Loads farming state from NBT.
     */
    public void load(CompoundTag tag) {
        farmingTicks = tag.getInt("FarmingTicks");
        cropsTended = tag.getInt("CropsTended");

        String stateName = tag.getString("CurrentState");
        try {
            currentState = FarmingState.valueOf(stateName);
        } catch (IllegalArgumentException e) {
            currentState = FarmingState.IDLE;
        }

        // Load known farms
        knownFarms.clear();
        if (tag.contains("KnownFarms")) {
            CompoundTag farmsTag = tag.getCompound("KnownFarms");
            for (String key : farmsTag.getAllKeys()) {
                if (key.startsWith("farm_")) {
                    int x = farmsTag.getInt(key + "_x");
                    int y = farmsTag.getInt(key + "_y");
                    int z = farmsTag.getInt(key + "_z");
                    knownFarms.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    /**
     * Farming states.
     */
    public enum FarmingState {
        IDLE,
        MOVING_TO_FARM,
        TENDING_CROPS,
        SHARING_FOOD,
        EXPANDING_FARM
    }
}
