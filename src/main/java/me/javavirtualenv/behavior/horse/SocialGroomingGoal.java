package me.javavirtualenv.behavior.horse;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHooks;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * AI Goal for social grooming behavior in horse herds.
 * Horses groom each other to strengthen social bonds.
 */
public class SocialGroomingGoal extends Goal {
    private final AbstractHorse horse;
    private final GroomingConfig config;
    private AbstractHorse groomingPartner;
    private int groomingTicks;
    private int groomingCooldown;

    public SocialGroomingGoal(AbstractHorse horse, GroomingConfig config) {
        this.horse = horse;
        this.config = config;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (groomingCooldown > 0) {
            groomingCooldown--;
            return false;
        }

        if (!horse.isAlive()) {
            return false;
        }

        // Cannot groom while being ridden
        if (horse.isVehicle()) {
            return false;
        }

        // Cannot groom if panicked
        EcologyComponent component = EcologyHooks.getEcologyComponent(horse);
        if (component != null && component.getHandleTag("fleeing").getBoolean("is_fleeing")) {
            return false;
        }

        // Find a grooming partner
        groomingPartner = findGroomingPartner();
        return groomingPartner != null && groomingPartner.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (groomingPartner == null || !groomingPartner.isAlive()) {
            return false;
        }

        if (horse.isVehicle() || groomingPartner.isVehicle()) {
            return false;
        }

        double distance = horse.distanceToSqr(groomingPartner);
        if (distance > config.maxGroomingDistance * config.maxGroomingDistance) {
            return false;
        }

        return groomingTicks < config.groomingDuration;
    }

    @Override
    public void start() {
        groomingTicks = 0;
    }

    @Override
    public void stop() {
        groomingPartner = null;
        groomingTicks = 0;
        groomingCooldown = config.groomingCooldown;
    }

    @Override
    public void tick() {
        if (groomingPartner == null) {
            return;
        }

        double distance = horse.distanceTo(groomingPartner);

        // Move towards partner if too far
        if (distance > 2.0) {
            horse.getNavigation().moveTo(groomingPartner, 0.8);
        } else {
            // Stop movement and groom
            horse.getNavigation().stop();

            // Look at partner
            horse.getLookControl().setLookAt(groomingPartner);

            groomingTicks++;

            // Play grooming sound periodically
            if (groomingTicks % 60 == 0) {
                playGroomingSound();
            }

            // Create heart particles occasionally
            if (groomingTicks % 40 == 0 && horse.getRandom().nextFloat() < 0.3) {
                spawnSocialParticles();
            }

            // Partner also grooms back
            if (groomingTicks % 20 == 0) {
                // Check if partner is also interested in grooming
                if (!groomingPartner.isVehicle() &&
                    groomingPartner.getRandom().nextFloat() < config.mutualGroomingChance) {
                    // Mutual grooming - partner looks back
                    groomingPartner.getLookControl().setLookAt(horse);
                }
            }
        }
    }

    private AbstractHorse findGroomingPartner() {
        Level level = horse.level();
        List<AbstractHorse> nearbyHorses = level.getEntitiesOfClass(
            AbstractHorse.class,
            horse.getBoundingBox().inflate(config.groomingSearchRadius)
        );

        // Filter for valid grooming partners
        for (AbstractHorse other : nearbyHorses) {
            if (other == horse || !other.isAlive()) {
                continue;
            }

            // Must be same species
            if (other.getType() != horse.getType()) {
                continue;
            }

            // Must be wild or tame together (social bonds form within groups)
            if (horse.isTamed() != other.isTamed()) {
                continue;
            }

            // Don't groom if panicking
            EcologyComponent otherComponent = EcologyHooks.getEcologyComponent(other);
            if (otherComponent != null && otherComponent.getHandleTag("fleeing").getBoolean("is_fleeing")) {
                continue;
            }

            // Don't interrupt if partner is busy
            if (other.isVehicle()) {
                continue;
            }

            // Check if partner wants to groom
            double distance = horse.distanceTo(other);
            if (distance > config.maxGroomingDistance) {
                continue;
            }

            // Random chance for mutual interest
            if (horse.getRandom().nextFloat() < config.groomingInitiationChance) {
                return other;
            }
        }

        return null;
    }

    private void playGroomingSound() {
        Level level = horse.level();
        if (level.isClientSide) {
            return;
        }

        // Use a soft breathing/snorting sound
        net.minecraft.sounds.SoundEvent sound = getGroomingSound();
        level.playSound(null, horse.blockPosition(), sound,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            0.5f, 1.0f
        );
    }

    private net.minecraft.sounds.SoundEvent getGroomingSound() {
        net.minecraft.world.entity.EntityType<?> type = horse.getType();

        // Note: DONKEY_BREATHE doesn't exist in 1.21.1, using DONKEY_AMBIENT instead
        if (type == net.minecraft.world.entity.EntityType.DONKEY) {
            return net.minecraft.sounds.SoundEvents.DONKEY_AMBIENT;
        } else if (type == net.minecraft.world.entity.EntityType.MULE) {
            return net.minecraft.sounds.SoundEvents.DONKEY_AMBIENT;
        } else {
            return net.minecraft.sounds.SoundEvents.HORSE_BREATHE;
        }
    }

    private void spawnSocialParticles() {
        Level level = horse.level();
        if (level.isClientSide) {
            return;
        }

        // Spawn particles between the two horses
        Vec3 startPos = horse.position().add(0, horse.getBbHeight() * 0.6, 0);
        Vec3 endPos = groomingPartner.position().add(0, groomingPartner.getBbHeight() * 0.6, 0);

        Vec3 between = startPos.add(endPos).scale(0.5);

        for (int i = 0; i < 2; i++) {
            double offsetX = (level.getRandom().nextDouble() - 0.5) * 0.3;
            double offsetY = (level.getRandom().nextDouble() - 0.5) * 0.3;
            double offsetZ = (level.getRandom().nextDouble() - 0.5) * 0.3;

            ((net.minecraft.server.level.ServerLevel) level).sendParticles(
                net.minecraft.core.particles.ParticleTypes.HEART,
                between.x + offsetX,
                between.y + offsetY,
                between.z + offsetZ,
                1, 0, 0, 0, 0
            );
        }
    }

    public static class GroomingConfig {
        public double groomingSearchRadius = 8.0;
        public double maxGroomingDistance = 4.0;
        public int groomingDuration = 200; // ticks
        public int groomingCooldown = 600; // ticks
        public double groomingInitiationChance = 0.15;
        public double mutualGroomingChance = 0.7;

        public static GroomingConfig createDefault() {
            return new GroomingConfig();
        }
    }
}
