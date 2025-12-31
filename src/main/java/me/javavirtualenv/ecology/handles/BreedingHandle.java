package me.javavirtualenv.ecology.handles;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.ecology.ai.EcologyBreedGoal;
import me.javavirtualenv.mixin.MobAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.List;

public final class BreedingHandle implements EcologyHandle {
	private static final String CACHE_KEY = "better-ecology:breeding-cache";

	@Override
	public String id() {
		return "breeding";
	}

	@Override
	public boolean supports(EcologyProfile profile) {
		return profile.getBool("reproduction.enabled", false);
	}

	@Override
	public void registerGoals(Mob mob, EcologyComponent component, EcologyProfile profile) {
		if (!(mob instanceof Animal animal)) {
			return;
		}
		BreedingConfig config = profile.cached(CACHE_KEY, () -> buildConfig(profile));
		int priority = profile.getInt("ai_priority_framework.reproduction.breed", 8);
		MobAccessor accessor = (MobAccessor) mob;
		accessor.betterEcology$getGoalSelector().addGoal(priority,
			new EcologyBreedGoal(animal, config.moveSpeed(),
				config.minAge(), config.minHealth(), (int) config.minCondition(), config.cooldown(),
				config.densityConfig()));
	}

	private BreedingConfig buildConfig(EcologyProfile profile) {
		int minAge = profile.getInt("reproduction.requirements.min_age", 0);
		double minHealth = profile.getDouble("reproduction.requirements.min_health", 0.0);
		double minCondition = profile.getDouble("reproduction.requirements.min_condition", 0.0);
		int cooldown = profile.getInt("reproduction.breeding.cooldown", 6000);
		double moveSpeed = profile.getDouble("reproduction.breeding.move_speed", 1.0);
		PopulationDensityHandle.DensityConfig densityConfig = buildDensityConfig(profile);
		return new BreedingConfig(minAge, minHealth, minCondition, cooldown, moveSpeed, densityConfig);
	}

	private PopulationDensityHandle.DensityConfig buildDensityConfig(EcologyProfile profile) {
		boolean enabled = profile.getBool("reproduction.density_effects.enabled", false);
		int checkRadius = profile.getInt("reproduction.density_effects.check_radius", 48);

		List<PopulationDensityHandle.DensityThreshold> densityCurve = buildDensityCurve(profile);

		// Allee threshold: minimum population to find mates (default 2 for K-selected species)
		// Can be overridden via config
		int alleeThreshold = profile.getInt("reproduction.density_effects.allee_threshold", 2);

		// Carrying capacity: maximum population before breeding stops
		// Defaults to the highest threshold in the curve
		int carryingCapacity = profile.getInt("reproduction.density_effects.carrying_capacity", 30);
		if (carryingCapacity == 30 && !densityCurve.isEmpty()) {
			// If not explicitly set, use the max count from the last curve entry
			carryingCapacity = densityCurve.get(densityCurve.size() - 1).maxCount();
		}

		return new PopulationDensityHandle.DensityConfig(enabled, checkRadius, densityCurve,
			alleeThreshold, carryingCapacity);
	}

	private List<PopulationDensityHandle.DensityThreshold> buildDensityCurve(EcologyProfile profile) {
		List<?> curveList = profile.getList("reproduction.density_effects.density_curve");
		if (curveList == null || curveList.isEmpty()) {
			// Return default curve if not configured
			return getDefaultDensityCurve();
		}

		List<PopulationDensityHandle.DensityThreshold> curve = new ArrayList<>();
		for (Object entry : curveList) {
			if (!(entry instanceof java.util.Map<?, ?> map)) {
				continue;
			}
			Object countObj = map.get("count");
			Object multiplierObj = map.get("multiplier");
			if (countObj instanceof Number count && multiplierObj instanceof Number multiplier) {
				curve.add(new PopulationDensityHandle.DensityThreshold(count.intValue(), multiplier.doubleValue()));
			}
		}
		return curve.isEmpty() ? getDefaultDensityCurve() : curve;
	}

	private List<PopulationDensityHandle.DensityThreshold> getDefaultDensityCurve() {
		// Default density curve for K-selected species (cows, sheep, etc.)
		List<PopulationDensityHandle.DensityThreshold> curve = new ArrayList<>();
		curve.add(new PopulationDensityHandle.DensityThreshold(2, 1.3));   // Underpopulated - encourage growth
		curve.add(new PopulationDensityHandle.DensityThreshold(6, 1.0));   // Optimal - normal rate
		curve.add(new PopulationDensityHandle.DensityThreshold(12, 0.5));  // Crowded - reduced breeding
		curve.add(new PopulationDensityHandle.DensityThreshold(20, 0.1));  // Overcrowded - severely reduced
		curve.add(new PopulationDensityHandle.DensityThreshold(30, 0.0));  // Carrying capacity - no breeding
		return curve;
	}

	private record BreedingConfig(
		int minAge,
		double minHealth,
		double minCondition,
		int cooldown,
		double moveSpeed,
		PopulationDensityHandle.DensityConfig densityConfig
	) {}
}
