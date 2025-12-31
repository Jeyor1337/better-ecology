package me.javavirtualenv.ecology.ai;

import me.javavirtualenv.ecology.conservation.RefugeSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.function.Predicate;

/**
 * Extended target goal that checks refuge status before allowing attacks.
 * Predation is reduced or prevented in refuge areas based on refuge level.
 */
public class RefugeAwareTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final Mob predator;
    private final Predicate<LivingEntity> originalPredicate;

    public RefugeAwareTargetGoal(Mob mob, Class<LivingEntity> targetClass, boolean mustSee) {
        super(mob, targetClass, mustSee);
        this.predator = mob;
        this.originalPredicate = null;
    }

    public RefugeAwareTargetGoal(Mob mob, Class<LivingEntity> targetClass,
                                  boolean mustSee, boolean mustReach) {
        super(mob, targetClass, mustSee, mustReach);
        this.predator = mob;
        this.originalPredicate = null;
    }

    public RefugeAwareTargetGoal(Mob mob, Class<LivingEntity> targetClass,
                                  int randomInterval, boolean mustSee, boolean mustReach) {
        super(mob, targetClass, randomInterval, mustSee, mustReach);
        this.predator = mob;
        this.originalPredicate = null;
    }

    public RefugeAwareTargetGoal(Mob mob, Class<LivingEntity> targetClass,
                                  int randomInterval, boolean mustSee, boolean mustReach,
                                  Predicate<LivingEntity> predicate) {
        super(mob, targetClass, randomInterval, mustSee, mustReach, predicate);
        this.predator = mob;
        this.originalPredicate = predicate;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        if (target == null) {
            return true;
        }

        // Check if prey is in a refuge
        if (!(predator.level() instanceof ServerLevel level)) {
            return true;
        }

        if (RefugeSystem.isInRefuge(level, target.blockPosition(), target.getType())) {
            // Apply predation reduction based on refuge level
            double predationMultiplier = RefugeSystem.getPredationReduction(level, target.blockPosition());

            // If multiplier is very low, skip targeting
            if (predationMultiplier < 0.2) {
                mob.setTarget(null);
                return false;
            }

            // If multiplier is moderate, apply probability check
            if (predationMultiplier < 1.0) {
                if (mob.getRandom().nextDouble() > predationMultiplier) {
                    mob.setTarget(null);
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }

        // Check refuge status during pursuit
        if (!(predator.level() instanceof ServerLevel level)) {
            return true;
        }

        if (RefugeSystem.isInRefuge(level, target.blockPosition(), target.getType())) {
            double predationMultiplier = RefugeSystem.getPredationReduction(level, target.blockPosition());

            // High protection refuges may cause predator to give up
            if (predationMultiplier < 0.3 && mob.getRandom().nextDouble() < 0.1) {
                mob.setTarget(null);
                return false;
            }
        }

        return true;
    }
}
