package me.javavirtualenv.behavior.villager;

import me.javavirtualenv.mixin.villager.VillagerMixin;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Handles villager threat detection and bell-ringing behavior.
 * Villagers ring bells to warn others of nearby threats.
 */
public class VillagerThreatResponse {
    private final Villager villager;
    private long lastBellRingTime = 0;
    private int alertLevel = 0;
    private static final long BELL_COOLDOWN = 600; // 30 seconds between bell rings
    private static final int ALERT_DECAY_RATE = 1;
    private static final double THREAT_DETECTION_RANGE = 16.0;
    private static final double EMERGENCY_RANGE = 8.0;

    public VillagerThreatResponse(Villager villager) {
        this.villager = villager;
    }

    /**
     * Called each tick to check for threats.
     */
    public void tick() {
        // Decay alert level over time
        if (alertLevel > 0 && villager.level().getGameTime() % 20 == 0) {
            alertLevel = Math.max(0, alertLevel - ALERT_DECAY_RATE);
        }

        // Check for threats
        List<net.minecraft.world.entity.Mob> nearbyThreats = detectNearbyThreats();

        if (!nearbyThreats.isEmpty()) {
            onThreatDetected(nearbyThreats);
        }
    }

    /**
     * Detects hostile mobs nearby.
     */
    private List<net.minecraft.world.entity.Mob> detectNearbyThreats() {
        List<net.minecraft.world.entity.Mob> threats = villager.level().getEntitiesOfClass(
            net.minecraft.world.entity.Mob.class,
            villager.getBoundingBox().inflate(THREAT_DETECTION_RANGE)
        );

        // Filter for hostile mobs
        threats.removeIf(entity -> {
            if (entity instanceof Villager) {
                return true; // Not a threat
            }
            return !(entity instanceof Enemy) && !isHostileMob(entity);
        });

        return threats;
    }

    /**
     * Checks if an entity is a hostile mob type.
     */
    private boolean isHostileMob(net.minecraft.world.entity.Mob entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE ||
               type == EntityType.HUSK ||
               type == EntityType.DROWNED ||
               type == EntityType.ZOMBIE_VILLAGER ||
               type == EntityType.VINDICATOR ||
               type == EntityType.EVOKER ||
               type == EntityType.PILLAGER ||
               type == EntityType.RAVAGER ||
               type == EntityType.WITCH ||
               type == EntityType.ILLUSIONER ||
               type == EntityType.CREEPER ||
               type == EntityType.PHANTOM ||
               type == EntityType.SILVERFISH ||
               type == EntityType.ENDERMITE;
    }

    /**
     * Called when a threat is detected.
     */
    private void onThreatDetected(List<net.minecraft.world.entity.Mob> threats) {
        double closestDistance = Double.MAX_VALUE;

        for (net.minecraft.world.entity.Mob threat : threats) {
            double distance = villager.position().distanceTo(threat.position());
            closestDistance = Math.min(closestDistance, distance);

            // Add gossip about the threat
            GossipSystem gossip = VillagerMixin.getGossipSystem(villager);
            if (gossip != null && villager.getRandom().nextDouble() < 0.1) {
                // Share threat info with other villagers
                gossip.addGossip(
                    GossipSystem.GossipType.THREAT,
                    threat.getUUID(),
                    10
                );
            }
        }

        // Increase alert level based on proximity
        if (closestDistance < EMERGENCY_RANGE) {
            alertLevel = Math.min(100, alertLevel + 20);
        } else {
            alertLevel = Math.min(100, alertLevel + 5);
        }

        // Ring bell if alert is high enough
        if (alertLevel >= 50 && canRingBell()) {
            ringBell();
            spreadEmergencyAlert();
        }

        // Panic behavior - run away from threats
        if (closestDistance < EMERGENCY_RANGE) {
            panicAndFlee(threats.get(0));
        }
    }

    /**
     * Checks if the villager can ring the bell.
     */
    private boolean canRingBell() {
        long currentTime = villager.level().getGameTime();
        return currentTime - lastBellRingTime >= BELL_COOLDOWN;
    }

    /**
     * Rings the nearby bell to warn other villagers.
     */
    public void ringBell() {
        BlockPos bellPos = findNearestBell();
        if (bellPos == null) {
            return;
        }

        lastBellRingTime = villager.level().getGameTime();

        // Play bell sound
        villager.level().playSound(
            null,
            bellPos,
            SoundEvents.BELL_BLOCK,
            SoundSource.BLOCKS,
            1.0f,
            1.0f
        );

        // Visual bell ringing effect
        if (!villager.level().isClientSide) {
            villager.level().blockUpdated(bellPos, Blocks.BELL);
        }

        // Alert nearby villagers
        alertNearbyVillagers();
    }

    /**
     * Finds the nearest village bell.
     */
    private BlockPos findNearestBell() {
        BlockPos searchCenter = villager.blockPosition();
        int searchRadius = 32;

        BlockPos closestBell = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
            searchCenter.offset(-searchRadius, -4, -searchRadius),
            searchCenter.offset(searchRadius, 4, searchRadius)
        )) {
            if (villager.level().getBlockState(pos).is(Blocks.BELL)) {
                double distance = villager.blockPosition().distSqr(pos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestBell = pos.immutable();
                }
            }
        }

        return closestBell;
    }

    /**
     * Alerts nearby villagers to the threat.
     */
    private void alertNearbyVillagers() {
        List<Villager> nearbyVillagers = villager.level().getEntitiesOfClass(
            Villager.class,
            villager.getBoundingBox().inflate(32.0)
        );

        for (Villager other : nearbyVillagers) {
            if (other != villager) {
                VillagerThreatResponse otherResponse = VillagerMixin.getThreatResponse(other);
                if (otherResponse != null) {
                    otherResponse.onExternalAlert();
                }
            }
        }
    }

    /**
     * Called when another villager sounds the alarm.
     */
    public void onExternalAlert() {
        alertLevel = Math.min(100, alertLevel + 30);

        // Run towards the nearest bell
        BlockPos bellPos = findNearestBell();
        if (bellPos != null) {
            villager.getNavigation().moveTo(
                bellPos.getX() + 0.5,
                bellPos.getY(),
                bellPos.getZ() + 0.5,
                0.8
            );
        }
    }

    /**
     * Spreads emergency gossip about the threat.
     */
    private void spreadEmergencyAlert() {
        List<Villager> nearbyVillagers = villager.level().getEntitiesOfClass(
            Villager.class,
            villager.getBoundingBox().inflate(16.0)
        );

        for (Villager other : nearbyVillagers) {
            GossipSystem gossip = VillagerMixin.getGossipSystem(other);
            if (gossip != null) {
                gossip.addGossip(
                    GossipSystem.GossipType.EMERGENCY,
                    villager.getUUID(),
                    20
                );
            }
        }
    }

    /**
     * Panics and flees from a threat.
     */
    private void panicAndFlee(net.minecraft.world.entity.Mob threat) {
        // Calculate direction away from threat
        double dx = villager.getX() - threat.getX();
        double dz = villager.getZ() - threat.getZ();

        // Normalize and scale
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 0) {
            dx /= length;
            dz /= length;
        }

        // Move away from threat
        villager.getNavigation().moveTo(
            villager.getX() + dx * 16,
            villager.getY(),
            villager.getZ() + dz * 16,
            1.0
        );

        // Play panic sound
        villager.level().playSound(
            null,
            villager.blockPosition(),
            SoundEvents.VILLAGER_NO,
            SoundSource.NEUTRAL,
            1.0f,
            1.0f
        );
    }

    /**
     * Gets the current alert level.
     */
    public int getAlertLevel() {
        return alertLevel;
    }

    /**
     * Checks if the villager is currently alarmed.
     */
    public boolean isAlarmed() {
        return alertLevel > 25;
    }

    /**
     * Forces the villager to calm down.
     */
    public void calmDown() {
        alertLevel = 0;
    }
}
