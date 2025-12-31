package me.javavirtualenv.behavior.feline;

import me.javavirtualenv.behavior.core.BehaviorContext;
import me.javavirtualenv.behavior.core.Vec3d;
import me.javavirtualenv.behavior.steering.SteeringBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

/**
 * Sleep behavior for cats that love sleeping on furniture.
 * <p>
 * Cats prefer to sleep on:
 * - Beds (especially when player is nearby)
 * - Chests (blocking access, of course)
 * - Furnaces (warmth)
 * - Other warm elevated surfaces
 * <p>
 * This behavior:
 * - Finds suitable sleeping spots
 * - Moves to sleep on blocks
 * - Repels phantoms while sleeping
 * - Wakes up when disturbed
 */
public class SleepOnBlocksBehavior extends SteeringBehavior {

    private final double searchRange;
    private final int sleepDuration;
    private final double wakeDistance;

    private boolean isSleeping = false;
    private BlockPos sleepPosition;
    private int sleepTicks = 0;
    private SleepBlockType currentBlockType;

    public SleepOnBlocksBehavior(double searchRange, int sleepDuration, double wakeDistance) {
        super();
        setWeight(0.6);
        this.searchRange = searchRange;
        this.sleepDuration = sleepDuration;
        this.wakeDistance = wakeDistance;
    }

    public SleepOnBlocksBehavior() {
        this(16.0, 1200, 3.0);
    }

    @Override
    public Vec3d calculate(BehaviorContext context) {
        Mob mob = context.getEntity();
        Level level = context.getLevel();

        // Check if should wake up
        if (isSleeping) {
            if (shouldWakeUp(mob, level)) {
                wakeUp();
                return new Vec3d();
            }
            sleepTicks++;

            // While sleeping, no movement
            return new Vec3d();
        }

        // Check if should go to sleep
        if (!shouldSleep(mob, level)) {
            return new Vec3d();
        }

        // Find sleep position if not sleeping
        if (sleepPosition == null) {
            sleepPosition = findSleepPosition(mob, level);
            if (sleepPosition == null) {
                return new Vec3d();
            }
            currentBlockType = identifyBlockType(level, sleepPosition);
        }

        // Move to sleep position
        return moveToSleep(context, sleepPosition);
    }

    private boolean shouldSleep(Mob mob, Level level) {
        // Only tame cats sleep on furniture
        if (!(mob instanceof net.minecraft.world.entity.animal.Cat cat)) {
            return false;
        }

        if (!cat.isTame()) {
            return false;
        }

        // Cats sleep during day more often
        long dayTime = level.getDayTime() % 24000;
        boolean isDay = dayTime >= 0 && dayTime < 13000;

        // Higher chance to sleep during day
        double sleepChance = isDay ? 0.02 : 0.005;

        return RANDOM.nextDouble() < sleepChance;
    }

    private boolean shouldWakeUp(Mob mob, Level level) {
        // Wake up after sleep duration
        if (sleepTicks >= sleepDuration) {
            return true;
        }

        // Wake up if player gets too close (unless tamed to that player)
        for (Player player : level.players()) {
            double distance = mob.position().distanceTo(player.position());
            if (distance < wakeDistance) {
                // If tamed to this player, let them approach closer
                if (mob instanceof net.minecraft.world.entity.animal.Cat cat) {
                    if (cat.isTame() && cat.getOwnerUUID() != null &&
                        cat.getOwnerUUID().equals(player.getUUID())) {
                        continue;
                    }
                }
                return true;
            }
        }

        // Wake up if threatened
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return true;
        }

        return false;
    }

    private BlockPos findSleepPosition(Mob mob, Level level) {
        BlockPos mobPos = mob.blockPosition();
        AABB searchArea = mob.getBoundingBox().inflate(searchRange);

        // Priority order: beds, chests, furnaces, other warm blocks
        BlockPos bestPos = null;
        SleepBlockType bestType = null;
        double bestScore = -1;

        // Check all blocks in range
        for (BlockPos pos : BlockPos.betweenClosed(
            (int) searchArea.minX, (int) searchArea.minY, (int) searchArea.minZ,
            (int) searchArea.maxX, (int) searchArea.maxY, (int) searchArea.maxZ)) {

            SleepBlockType type = identifyBlockType(level, pos);
            if (type == null) {
                continue;
            }

            // Calculate score based on distance and type priority
            double distance = mobPos.distSqr(pos);
            double typePriority = getTypePriority(type);
            double score = typePriority * 100 - distance;

            if (score > bestScore) {
                bestPos = pos;
                bestType = type;
                bestScore = score;
            }
        }

        return bestPos;
    }

    private SleepBlockType identifyBlockType(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // Check for beds
        if (state.getBlock() instanceof BedBlock) {
            // Only sleep on the head of the bed
            if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
                return SleepBlockType.BED;
            }
        }

        // Check for chests
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) ||
            state.is(Blocks.ENDER_CHEST) || state.is(Blocks.BARREL)) {
            return SleepBlockType.CHEST;
        }

        // Check for furnaces (warm)
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) ||
            state.is(Blocks.SMOKER)) {
            return SleepBlockType.FURNACE;
        }

        // Check for other warm blocks (lit campfires, etc)
        if (state.is(Blocks.CAMPFIRE) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
            return SleepBlockType.WARM_BLOCK;
        }

        return null;
    }

    private double getTypePriority(SleepBlockType type) {
        return switch (type) {
            case BED -> 10.0;
            case CHEST -> 8.0;
            case FURNACE -> 6.0;
            case WARM_BLOCK -> 4.0;
        };
    }

    private Vec3d moveToSleep(BehaviorContext context, BlockPos target) {
        Mob mob = context.getEntity();
        Vec3d mobPos = context.getPosition();
        Vec3d targetPos = new Vec3d(target.getX() + 0.5, target.getY() + 0.1, target.getZ() + 0.5);

        Vec3d toTarget = Vec3d.sub(targetPos, mobPos);
        double distance = toTarget.magnitude();

        // If close enough, start sleeping
        if (distance < 0.5) {
            isSleeping = true;
            sleepTicks = 0;

            // Set cat to lying down if possible
            if (mob instanceof net.minecraft.world.entity.animal.Cat cat) {
                cat.setInSittingPose(true);
            }

            return new Vec3d();
        }

        // Move toward sleep position slowly
        toTarget.normalize();
        toTarget.mult(0.3);

        return toTarget;
    }

    public void wakeUp() {
        isSleeping = false;
        sleepPosition = null;
        sleepTicks = 0;
        currentBlockType = null;
    }

    public boolean isSleeping() {
        return isSleeping;
    }

    public BlockPos getSleepPosition() {
        return sleepPosition;
    }

    public SleepBlockType getCurrentBlockType() {
        return currentBlockType;
    }

    public int getSleepTicks() {
        return sleepTicks;
    }

    /**
     * Check if this cat is currently repelling phantoms.
     * Phantom repelling occurs while sleeping.
     */
    public boolean isRepellingPhantoms() {
        return isSleeping && sleepTicks > 60;
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    public enum SleepBlockType {
        BED,
        CHEST,
        FURNACE,
        WARM_BLOCK
    }
}
