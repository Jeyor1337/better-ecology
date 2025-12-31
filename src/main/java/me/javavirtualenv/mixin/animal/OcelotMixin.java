package me.javavirtualenv.mixin.animal;

import me.javavirtualenv.ecology.AnimalBehaviorRegistry;
import me.javavirtualenv.ecology.AnimalConfig;
import me.javavirtualenv.ecology.handles.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Ocelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ocelot-specific behavior registration.
 * <p>
 * Ocelots are shy jungle cats that:
 * - Hunt creepers (deterrent behavior) and chickens
 * - Exhibit crepuscular activity patterns (most active at dawn/dusk)
 * - Serve as solitary predators in jungle ecosystems
 * - Have high agility and speed, especially when fleeing or hunting
 * - Require jungle canopy for hunting and shelter
 * - Are distrustful of players and flee from approach
 * - Display stalking and pouncing behaviors
 * <p>
 * Special feline behaviors:
 * - Stalking and pouncing on prey
 * - Creeping through undergrowth
 * - Creeper detection and deterrence
 * - Quiet stealth movement
 * - Squeezing through gaps in vegetation
 * - Climbing trees to escape threats or ambush prey
 * - Landing on feet (no fall damage)
 * - Play behavior with prey and environmental objects
 */
@Mixin(Ocelot.class)
public abstract class OcelotMixin extends AnimalMixin {

    private static boolean ocelotBehaviorsRegistered = false;

    /**
     * Registers ocelot behaviors from JSON configuration.
     * Creates an AnimalConfig with handles for all ocelot-specific behaviors.
     */
    @Override
    protected void registerBehaviors() {
        if (areBehaviorsRegistered()) {
            return;
        }

        ResourceLocation ocelotId = ResourceLocation.withDefaultNamespace("ocelot");

        AnimalConfig config = AnimalConfig.builder(ocelotId)
            // Physical attributes
            .addHandle(new HealthHandle())
            .addHandle(new MovementHandle())

            // Internal state tracking
            .addHandle(new HungerHandle())
            .addHandle(new ThirstHandle())
            .addHandle(new ConditionHandle())
            .addHandle(new EnergyHandle())
            .addHandle(new AgeHandle())
            .addHandle(new SocialHandle())

            // Reproduction
            .addHandle(new BreedingHandle())

            // Temporal behaviors - crepuscular pattern
            .addHandle(new TemporalHandle())

            // Predation - both predator and prey
            .addHandle(new PredationHandle())

            // Diet - carnivorous hunter
            .addHandle(new DietHandle())

            // Feline behaviors - stalking, pouncing, climbing, stealth
            .addHandle(new FelineBehaviorHandle())

            .build();

        AnimalBehaviorRegistry.register(ocelotId, config);
        markBehaviorsRegistered();
    }

    /**
     * Inject after Ocelot constructor to register behaviors.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        registerBehaviors();
    }
}
