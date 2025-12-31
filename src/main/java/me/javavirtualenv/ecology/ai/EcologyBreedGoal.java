package me.javavirtualenv.ecology.ai;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.ecology.conservation.HabitatQuality;
import me.javavirtualenv.ecology.conservation.LineageRegistry;
import me.javavirtualenv.ecology.conservation.RefugeSystem;
import me.javavirtualenv.ecology.handles.AgeHandle;
import me.javavirtualenv.ecology.handles.ConditionHandle;
import me.javavirtualenv.ecology.handles.HabitatHandle;
import me.javavirtualenv.ecology.handles.PopulationDensityHandle;
import me.javavirtualenv.ecology.handles.SeasonalHandle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Extended breeding goal that enforces ecological requirements:
 * - Minimum age (maturity)
 * - Minimum health percentage
 * - Minimum body condition
 * - Breeding cooldown period
 * - Density-dependent breeding (Allee threshold, carrying capacity)
 * - Habitat quality considerations (source-sink dynamics)
 * - Refuge area breeding bonuses
 * - Genetic diversity (inbreeding prevention)
 */
public class EcologyBreedGoal extends BreedGoal {
    private final int minAge;
    private final double minHealth;
    private final int minCondition;
    private final int cooldownTicks;
    private final PopulationDensityHandle.DensityConfig densityConfig;

    public EcologyBreedGoal(Animal animal, double speedModifier,
                           int minAge, double minHealth, int minCondition, int cooldownTicks,
                           PopulationDensityHandle.DensityConfig densityConfig) {
        super(animal, speedModifier);
        this.minAge = minAge;
        this.minHealth = minHealth;
        this.minCondition = minCondition;
        this.cooldownTicks = cooldownTicks;
        this.densityConfig = densityConfig;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        return meetsBreedingRequirements(this.animal);
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canContinueToUse()) {
            return false;
        }
        return meetsBreedingRequirements(this.animal);
    }

    private boolean meetsBreedingRequirements(Animal animal) {
        if (!checkHealthRequirement(animal)) {
            return false;
        }

        if (!checkGeneticDiversityRequirement(animal)) {
            return false;
        }

        if (!checkDensityRequirements(animal)) {
            return false;
        }

        if (!checkSeasonalRequirements(animal)) {
            return false;
        }

        if (!checkHabitatRequirements(animal)) {
            return false;
        }

        EcologyComponent component = getEcologyComponent(animal);
        if (component == null || !component.hasProfile()) {
            return true;
        }

        if (!checkAgeRequirement(component)) {
            return false;
        }

        if (!checkConditionRequirement(component)) {
            return false;
        }

        return true;
    }

    private boolean checkHealthRequirement(Animal animal) {
        double healthPercent = animal.getHealth() / animal.getMaxHealth();
        return healthPercent >= minHealth;
    }

    private boolean checkDensityRequirements(Animal animal) {
        // If density effects are disabled, allow breeding
        if (densityConfig == null || !densityConfig.enabled()) {
            return true;
        }

        // Check Allee threshold - minimum population to find mates
        boolean meetsAllee = PopulationDensityHandle.meetsAlleeThreshold(
            animal.level(),
            animal.blockPosition(),
            animal.getType(),
            densityConfig.checkRadius(),
            densityConfig.alleeThreshold()
        );
        if (!meetsAllee) {
            return false;
        }

        // Check carrying capacity - stop breeding if population too high
        boolean belowCapacity = PopulationDensityHandle.belowCarryingCapacity(
            animal.level(),
            animal.blockPosition(),
            animal.getType(),
            densityConfig.checkRadius(),
            densityConfig.carryingCapacity()
        );
        if (!belowCapacity) {
            return false;
        }

        // Apply breeding probability multiplier based on density curve
        double multiplier = PopulationDensityHandle.getBreedingMultiplier(
            animal.level(),
            animal.blockPosition(),
            animal.getType(),
            densityConfig.checkRadius(),
            densityConfig.densityCurve()
        );

        // If multiplier is 0, breeding is prevented
        if (multiplier <= 0.0) {
            return false;
        }

        // If multiplier is less than 1.0, apply probability check
        if (multiplier < 1.0) {
            return animal.getRandom().nextDouble() < multiplier;
        }

        // Multiplier > 1.0 means enhanced breeding (underpopulated)
        // Always allow breeding in this case
        return true;
    }

    /**
     * Check habitat quality and refuge breeding bonuses.
     * Source habitats and refuge areas provide breeding bonuses.
     * Sink habitats impose breeding penalties.
     */
    private boolean checkHabitatRequirements(Animal animal) {
        if (!(animal.level() instanceof ServerLevel level)) {
            return true;
        }

        double totalMultiplier = 1.0;

        // Check habitat quality
        HabitatQuality quality = HabitatQuality.evaluateHabitat(level, animal.blockPosition(), animal.getType());
        double habitatMultiplier = HabitatHandle.getBreedingMultiplier(quality);
        totalMultiplier *= habitatMultiplier;

        // Check refuge status
        if (RefugeSystem.isInRefuge(level, animal.blockPosition(), animal.getType())) {
            double refugeBonus = RefugeSystem.getBreedingBonus(level, animal.blockPosition());
            totalMultiplier *= refugeBonus;
        }

        // Apply habitat-based breeding probability
        if (totalMultiplier <= 0.0) {
            return false;
        }

        if (totalMultiplier < 1.0) {
            return animal.getRandom().nextDouble() < totalMultiplier;
        }

        // Multiplier > 1.0 means enhanced breeding
        return true;
    }

    private boolean checkAgeRequirement(EcologyComponent component) {
        int ageTicks = AgeHandle.getAgeTicks(component);
        return ageTicks >= minAge;
    }

    private boolean checkConditionRequirement(EcologyComponent component) {
        int condition = ConditionHandle.getConditionLevel(component);
        return condition >= minCondition;
    }

    private boolean checkSeasonalRequirements(Animal animal) {
        // Check if current season is a breeding season
        if (!SeasonalHandle.isBreedingSeason(animal)) {
            return false;
        }

        // Apply seasonal breeding multiplier
        double seasonalMultiplier = SeasonalHandle.getBreedingMultiplier(animal);

        // If multiplier is 0 or very low, prevent breeding
        if (seasonalMultiplier <= 0.1) {
            return false;
        }

        // If multiplier is less than 1.0, apply probability check
        if (seasonalMultiplier < 1.0) {
            return animal.getRandom().nextDouble() < seasonalMultiplier;
        }

        // Multiplier >= 1.0, allow breeding
        return true;
    }

    /**
     * Checks genetic diversity requirement to prevent inbreeding.
     * Prevents breeding between animals related within 2 generations.
     */
    private boolean checkGeneticDiversityRequirement(Animal animal) {
        Animal partner = this.animal.getLoveCause();
        if (partner == null || partner == animal) {
            return true;
        }

        return !LineageRegistry.areTooCloselyRelated(animal.getUUID(), partner.getUUID());
    }

    private EcologyComponent getEcologyComponent(Animal animal) {
        if (!(animal instanceof EcologyAccess access)) {
            return null;
        }
        return access.betterEcology$getEcologyComponent();
    }
}
