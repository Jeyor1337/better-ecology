package me.javavirtualenv.ecology.ai;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.ecology.conservation.PopulationRegistry;
import me.javavirtualenv.ecology.handles.AgeHandle;
import me.javavirtualenv.ecology.handles.ConditionHandle;
import me.javavirtualenv.ecology.handles.ConservationHandle;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Emergency breeding goal for endangered species.
 * Activates when population drops below threshold and uses relaxed breeding requirements.
 *
 * Features:
 * - Lower age/health/condition requirements (50% of normal)
 * - Shorter breeding cooldown
 * - Higher movement speed to find mates
 * - Only active when species is endangered
 */
public class EmergencyBreedingGoal extends BreedGoal {
    private final int mvpThreshold;
    private final int minAge;
    private final double minHealth;
    private final int minCondition;
    private final int cooldownTicks;

    public EmergencyBreedingGoal(Animal animal, double speedModifier,
                                 int minAge, double minHealth, int minCondition,
                                 int cooldownTicks, int mvpThreshold) {
        super(animal, speedModifier);
        this.minAge = minAge;
        this.minHealth = minHealth;
        this.minCondition = minCondition;
        this.cooldownTicks = cooldownTicks;
        this.mvpThreshold = mvpThreshold;
    }

    @Override
    public boolean canUse() {
        // Only activate if species is endangered
        if (!isSpeciesEndangered()) {
            return false;
        }

        if (!super.canUse()) {
            return false;
        }

        return meetsEmergencyRequirements(this.animal);
    }

    @Override
    public boolean canContinueToUse() {
        if (!isSpeciesEndangered()) {
            return false;
        }

        if (!super.canContinueToUse()) {
            return false;
        }

        return meetsEmergencyRequirements(this.animal);
    }

    /**
     * Check if the species is currently endangered.
     *
     * @return true if population is below emergency threshold
     */
    private boolean isSpeciesEndangered() {
        return PopulationRegistry.isEndangered(this.animal, mvpThreshold);
    }

    /**
     * Check if animal meets relaxed emergency breeding requirements.
     *
     * @param animal The animal to check
     * @return true if requirements are met
     */
    private boolean meetsEmergencyRequirements(Animal animal) {
        if (!checkHealthRequirement(animal)) {
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

    /**
     * Check health requirement (relaxed threshold).
     *
     * @param animal The animal to check
     * @return true if health is sufficient
     */
    private boolean checkHealthRequirement(Animal animal) {
        double healthPercent = animal.getHealth() / animal.getMaxHealth();
        return healthPercent >= minHealth;
    }

    /**
     * Check age requirement (relaxed threshold).
     *
     * @param component The ecology component
     * @return true if age is sufficient
     */
    private boolean checkAgeRequirement(EcologyComponent component) {
        int ageTicks = AgeHandle.getAgeTicks(component);
        return ageTicks >= minAge;
    }

    /**
     * Check condition requirement (relaxed threshold).
     *
     * @param component The ecology component
     * @return true if condition is sufficient
     */
    private boolean checkConditionRequirement(EcologyComponent component) {
        int condition = ConditionHandle.getConditionLevel(component);
        return condition >= minCondition;
    }

    /**
     * Get ecology component from animal.
     *
     * @param animal The animal
     * @return Ecology component or null
     */
    private EcologyComponent getEcologyComponent(Animal animal) {
        if (!(animal instanceof EcologyAccess access)) {
            return null;
        }
        return access.betterEcology$getEcologyComponent();
    }
}
