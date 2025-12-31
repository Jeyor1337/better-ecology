package me.javavirtualenv.behavior.villager;

import me.javavirtualenv.mixin.villager.VillagerMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Manages villager work station behavior with visual animations and productivity tracking.
 * Enhanced with profession-specific work behaviors and effects.
 */
public class WorkStationAI {
    private final Villager villager;
    private int workTicks = 0;
    private int productivity = 0;
    private int breakTimeRemaining = 0;
    private boolean isWorking = false;
    private BlockPos workStationPos;
    private int workSessionTicks = 0;

    private static final int WORK_DURATION = 600;
    private static final int BREAK_DURATION = 200;
    private static final int PRODUCTIVITY_PER_TICK = 1;
    private static final int MAX_PRODUCTIVITY = 1000;
    private static final int MAX_WORK_SESSION = 2400; // 2 minutes max work session

    public WorkStationAI(Villager villager) {
        this.villager = villager;
    }

    /**
     * Called each tick to update work behavior.
     */
    public void tick() {
        if (breakTimeRemaining > 0) {
            breakTimeRemaining--;
            return;
        }

        BlockPos jobSite = getJobSite();
        if (jobSite == null) {
            stopWorking();
            return;
        }

        double distance = villager.position().distanceTo(
            new Vec3(jobSite.getX() + 0.5, jobSite.getY(), jobSite.getZ() + 0.5)
        );

        if (distance > 3.0) {
            stopWorking();
            return;
        }

        startWorking(jobSite);
        performWork(jobSite);

        workTicks++;
        workSessionTicks++;

        if (workTicks >= WORK_DURATION) {
            takeBreak();
        }

        if (workSessionTicks >= MAX_WORK_SESSION) {
            // Long work session, take extended break
            breakTimeRemaining = BREAK_DURATION * 2;
            workSessionTicks = 0;
            stopWorking();
        }
    }

    /**
     * Performs work at the station with profession-specific behaviors.
     */
    private void performWork(BlockPos station) {
        if (!isWorking) {
            return;
        }

        productivity = Math.min(MAX_PRODUCTIVITY, productivity + PRODUCTIVITY_PER_TICK);

        BlockState block = villager.level().getBlockState(station);

        // Profession-specific work behavior
        VillagerProfession profession = villager.getVillagerData().getProfession();
        performProfessionWork(profession, station, block);

        // Visual and audio feedback
        if (workTicks % 20 == 0) {
            performWorkAnimation(profession, block);
        }

        if (workTicks % 60 == 0) {
            playWorkSound(profession);
        }

        // Special effects based on productivity
        if (productivity >= 500 && workTicks % 100 == 0) {
            spawnProductivityEffect(profession);
        }
    }

    /**
     * Performs profession-specific work behavior.
     */
    private void performProfessionWork(VillagerProfession profession, BlockPos station, BlockState block) {
        if (villager.level().isClientSide) {
            return;
        }

        if (profession == VillagerProfession.FARMER) {
            // Farmers check nearby crops
            if (workTicks % 40 == 0) {
                checkNearbyCrops(station);
            }
        } else if (profession == VillagerProfession.LIBRARIAN) {
            // Librarians occasionally show enchantment particles
            if (villager.getRandom().nextDouble() < 0.05) {
                spawnEnchantParticles();
            }
        } else if (profession == VillagerProfession.WEAPONSMITH ||
                   profession == VillagerProfession.ARMORER ||
                   profession == VillagerProfession.TOOLSMITH) {
            // Smiths have higher productivity
            productivity += PRODUCTIVITY_PER_TICK;
        } else if (profession == VillagerProfession.CLERIC) {
            // Clerics occasionally show holy particles
            if (workTicks % 80 == 0) {
                spawnHolyParticles();
            }
        } else if (profession == VillagerProfession.FISHERMAN) {
            // Fishermen check for water
            if (workTicks % 60 == 0) {
                checkNearbyWater(station);
            }
        } else if (profession == VillagerProfession.SHEPHERD) {
            // Shepherds think about sheep
            if (workTicks % 50 == 0) {
                checkNearbyAnimals(station);
            }
        }
    }

    /**
     * Checks nearby crops for farmers.
     */
    private void checkNearbyCrops(BlockPos station) {
        for (BlockPos pos : BlockPos.betweenClosed(
            station.offset(-3, -1, -3),
            station.offset(3, 1, 3)
        )) {
            BlockState state = villager.level().getBlockState(pos);
            if (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) ||
                state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)) {
                // Farmer found crops, small productivity boost
                productivity = Math.min(MAX_PRODUCTIVITY, productivity + 5);
                break;
            }
        }
    }

    /**
     * Checks for nearby water for fishermen.
     */
    private void checkNearbyWater(BlockPos station) {
        for (BlockPos pos : BlockPos.betweenClosed(
            station.offset(-5, -2, -5),
            station.offset(5, 2, 5)
        )) {
            if (villager.level().getBlockState(pos).getFluidState().isSource()) {
                productivity = Math.min(MAX_PRODUCTIVITY, productivity + 3);
                break;
            }
        }
    }

    /**
     * Checks for nearby animals for shepherds.
     */
    private void checkNearbyAnimals(BlockPos station) {
        // Check if EnhancedFarming exists and has hungry villagers
        EnhancedFarming farming = VillagerMixin.getEnhancedFarming(villager);
        if (farming != null && farming.getKnownFarms().size() > 0) {
            productivity = Math.min(MAX_PRODUCTIVITY, productivity + 5);
        }
    }

    /**
     * Spawns enchantment particles for librarians.
     */
    private void spawnEnchantParticles() {
        if (villager.level().isClientSide) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            double offsetX = villager.getRandom().nextGaussian() * 0.3;
            double offsetY = villager.getRandom().nextDouble() * 0.5;
            double offsetZ = villager.getRandom().nextGaussian() * 0.3;

            villager.level().addParticle(
                net.minecraft.core.particles.ParticleTypes.ENCHANT,
                villager.getX() + offsetX,
                villager.getY() + 1.0 + offsetY,
                villager.getZ() + offsetZ,
                0, 0.1, 0
            );
        }
    }

    /**
     * Spawns holy particles for clerics.
     */
    private void spawnHolyParticles() {
        if (villager.level().isClientSide) {
            return;
        }

        villager.level().addParticle(
            net.minecraft.core.particles.ParticleTypes.HEART,
            villager.getX(),
            villager.getY() + 1.0,
            villager.getZ(),
            0, 0.1, 0
        );
    }

    /**
     * Plays work animation based on profession.
     */
    private void performWorkAnimation(VillagerProfession profession, BlockState block) {
        if (villager.level().isClientSide) {
            return;
        }

        net.minecraft.core.particles.ParticleOptions particleType;
        if (profession == VillagerProfession.FARMER) {
            particleType = net.minecraft.core.particles.ParticleTypes.COMPOSTER;
        } else if (profession == VillagerProfession.LIBRARIAN) {
            particleType = net.minecraft.core.particles.ParticleTypes.ENCHANT;
        } else if (profession == VillagerProfession.WEAPONSMITH ||
                   profession == VillagerProfession.ARMORER ||
                   profession == VillagerProfession.TOOLSMITH) {
            particleType = net.minecraft.core.particles.ParticleTypes.SMOKE;
        } else if (profession == VillagerProfession.CLERIC) {
            particleType = net.minecraft.core.particles.ParticleTypes.HEART;
        } else if (profession == VillagerProfession.MASON) {
            particleType = net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
        } else if (profession == VillagerProfession.CARTOGRAPHER) {
            particleType = net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
        } else {
            particleType = net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
        }

        for (int i = 0; i < 2; i++) {
            double offsetX = villager.getRandom().nextGaussian() * 0.2;
            double offsetY = villager.getRandom().nextDouble() * 0.5;
            double offsetZ = villager.getRandom().nextGaussian() * 0.2;

            villager.level().addParticle(
                particleType,
                villager.getX() + offsetX,
                villager.getY() + 1.0 + offsetY,
                villager.getZ() + offsetZ,
                0, 0.1, 0
            );
        }
    }

    /**
     * Plays appropriate work sound for the profession.
     */
    private void playWorkSound(VillagerProfession profession) {
        if (villager.level().isClientSide) {
            return;
        }

        SoundEvent sound;
        if (profession == VillagerProfession.FARMER) {
            sound = SoundEvents.VILLAGER_WORK_FARMER;
        } else if (profession == VillagerProfession.LIBRARIAN) {
            sound = SoundEvents.VILLAGER_WORK_LIBRARIAN;
        } else if (profession == VillagerProfession.WEAPONSMITH ||
                   profession == VillagerProfession.ARMORER ||
                   profession == VillagerProfession.TOOLSMITH) {
            sound = SoundEvents.VILLAGER_WORK_WEAPONSMITH;
        } else if (profession == VillagerProfession.CLERIC) {
            sound = SoundEvents.VILLAGER_WORK_CLERIC;
        } else if (profession == VillagerProfession.BUTCHER) {
            sound = SoundEvents.VILLAGER_WORK_BUTCHER;
        } else if (profession == VillagerProfession.CARTOGRAPHER) {
            sound = SoundEvents.VILLAGER_WORK_CARTOGRAPHER;
        } else if (profession == VillagerProfession.FISHERMAN) {
            sound = SoundEvents.VILLAGER_WORK_FISHERMAN;
        } else if (profession == VillagerProfession.FLETCHER) {
            sound = SoundEvents.VILLAGER_WORK_FLETCHER;
        } else if (profession == VillagerProfession.LEATHERWORKER) {
            sound = SoundEvents.VILLAGER_WORK_LEATHERWORKER;
        } else if (profession == VillagerProfession.MASON) {
            sound = SoundEvents.VILLAGER_WORK_MASON;
        } else if (profession == VillagerProfession.SHEPHERD) {
            sound = SoundEvents.VILLAGER_WORK_SHEPHERD;
        } else {
            sound = SoundEvents.VILLAGER_YES;
        }

        villager.level().playSound(
            null,
            villager.blockPosition(),
            sound,
            SoundSource.NEUTRAL,
            0.5f,
            1.0f
        );
    }

    /**
     * Spawns special productivity effects.
     */
    private void spawnProductivityEffect(VillagerProfession profession) {
        if (villager.level().isClientSide) {
            return;
        }

        // Spawn extra particle effects when highly productive
        for (int i = 0; i < 5; i++) {
            double offsetX = villager.getRandom().nextGaussian() * 0.4;
            double offsetY = villager.getRandom().nextDouble() * 0.8;
            double offsetZ = villager.getRandom().nextGaussian() * 0.4;

            villager.level().addParticle(
                net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                villager.getX() + offsetX,
                villager.getY() + 1.0 + offsetY,
                villager.getZ() + offsetZ,
                0, 0.15, 0
            );
        }
    }

    /**
     * Starts working at a station.
     */
    private void startWorking(BlockPos station) {
        if (!isWorking || workStationPos == null || !workStationPos.equals(station)) {
            isWorking = true;
            workStationPos = station;
            workTicks = 0;

            villager.getLookControl().setLookAt(
                station.getX() + 0.5,
                station.getY() + 0.5,
                station.getZ() + 0.5,
                10.0f,
                villager.getMaxHeadXRot()
            );
        }
    }

    /**
     * Stops working.
     */
    private void stopWorking() {
        isWorking = false;
        workStationPos = null;
        workTicks = 0;
    }

    /**
     * Takes a break from work.
     */
    private void takeBreak() {
        breakTimeRemaining = BREAK_DURATION;
        workTicks = 0;

        if (villager.getRandom().nextDouble() < 0.5) {
            villager.getNavigation().moveTo(
                villager.getX() + (villager.getRandom().nextDouble() - 0.5) * 4,
                villager.getY(),
                villager.getZ() + (villager.getRandom().nextDouble() - 0.5) * 4,
                0.5
            );
        }
    }

    /**
     * Gets the villager's job site position.
     */
    private BlockPos getJobSite() {
        return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
    }

    /**
     * Gets current productivity level.
     */
    public int getProductivity() {
        return productivity;
    }

    /**
     * Resets productivity (called when inventory is used).
     */
    public void resetProductivity() {
        productivity = 0;
    }

    /**
     * Checks if the villager is currently working.
     */
    public boolean isWorking() {
        return isWorking;
    }

    /**
     * Gets the trade discount based on productivity.
     */
    public float getProductivityBonus() {
        return Math.min(0.2f, productivity / 5000.0f);
    }

    /**
     * Serializes work state to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("WorkTicks", workTicks);
        tag.putInt("Productivity", productivity);
        tag.putInt("BreakTimeRemaining", breakTimeRemaining);
        tag.putBoolean("IsWorking", isWorking);
        tag.putInt("WorkSessionTicks", workSessionTicks);
        if (workStationPos != null) {
            tag.putInt("WorkStationX", workStationPos.getX());
            tag.putInt("WorkStationY", workStationPos.getY());
            tag.putInt("WorkStationZ", workStationPos.getZ());
        }
        return tag;
    }

    /**
     * Loads work state from NBT.
     */
    public void load(CompoundTag tag) {
        workTicks = tag.getInt("WorkTicks");
        productivity = tag.getInt("Productivity");
        breakTimeRemaining = tag.getInt("BreakTimeRemaining");
        isWorking = tag.getBoolean("IsWorking");
        workSessionTicks = tag.getInt("WorkSessionTicks");

        if (tag.contains("WorkStationX")) {
            int x = tag.getInt("WorkStationX");
            int y = tag.getInt("WorkStationY");
            int z = tag.getInt("WorkStationZ");
            workStationPos = new BlockPos(x, y, z);
        } else {
            workStationPos = null;
        }
    }
}
