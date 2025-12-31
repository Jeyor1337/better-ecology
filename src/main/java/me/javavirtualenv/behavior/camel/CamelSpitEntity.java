package me.javavirtualenv.behavior.camel;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spit projectile fired by camels as a defense mechanism.
 * <p>
 * This projectile:
 * - Deals minor physical damage
 * - Applies a slow effect to hinder movement
 * - Creates splash particles on impact
 * - Has gravity and limited range
 */
public class CamelSpitEntity extends Projectile {

    private float damage = 1.0f;
    private int slowDuration = 100;

    public CamelSpitEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public CamelSpitEntity(Level level, Entity owner) {
        super(EntityType.LLAMA_SPIT, level);
        setOwner(owner);
    }

    public CamelSpitEntity(Level level, double x, double y, double z) {
        super(EntityType.LLAMA_SPIT, level);
        setPos(x, y, z);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No data to define
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 velocity = getDeltaMovement();
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        hitTargetOrDeflectSelf(hitResult);

        double newX = getX() + velocity.x;
        double newY = getY() + velocity.y;
        double newZ = getZ() + velocity.z;

        updateRotation();

        // Check if inside blocks or in water
        if (level().getBlockStates(getBoundingBox()).anyMatch(state -> !state.isAir())) {
            discard();
        } else if (isInWaterOrBubble()) {
            discard();
        } else {
            // Apply air resistance and gravity
            setDeltaMovement(velocity.x * 0.99, velocity.y * 0.99, velocity.z * 0.99);
            applyGravity();
            setPos(newX, newY, newZ);
        }

        // Spawn trail particles
        if (level().isClientSide) {
            spawnTrailParticles();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!level().isClientSide) {
            // Create splash effect
            level().broadcastEntityEvent(this, (byte) 3);

            if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) result;
                onEntityHit(entityHit);
            }
        }

        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!level().isClientSide) {
            discard();
        }
    }

    /**
     * Handles hitting an entity.
     */
    private void onEntityHit(EntityHitResult entityHit) {
        Entity target = entityHit.getEntity();
        Entity owner = getOwner();

        if (target instanceof LivingEntity livingTarget && owner instanceof LivingEntity livingOwner) {
            // Apply damage using spit damage source
            DamageSource damageSource = damageSources().mobProjectile(this, livingOwner);
            livingTarget.hurt(damageSource, damage);

            // Apply slow effect
            if (slowDuration > 0) {
                livingTarget.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    slowDuration,
                    0, // Amplifier 0 = 15% slowdown
                    false, false
                ));
            }
        }
    }

    /**
     * Spawns trail particles during flight.
     */
    private void spawnTrailParticles() {
        Vec3 pos = position();
        for (int i = 0; i < 2; i++) {
            double offsetX = (Math.random() - 0.5) * 0.1;
            double offsetY = (Math.random() - 0.5) * 0.1;
            double offsetZ = (Math.random() - 0.5) * 0.1;

            level().addParticle(
                ParticleTypes.SPIT,
                pos.x + offsetX,
                pos.y + offsetY,
                pos.z + offsetZ,
                -getDeltaMovement().x * 0.1,
                -getDeltaMovement().y * 0.1,
                -getDeltaMovement().z * 0.1
            );
        }
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setSlowDuration(int duration) {
        this.slowDuration = duration;
    }

    public float getDamage() {
        return damage;
    }

    public int getSlowDuration() {
        return slowDuration;
    }
}
