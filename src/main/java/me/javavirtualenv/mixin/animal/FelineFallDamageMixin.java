package me.javavirtualenv.mixin.animal;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.api.EcologyAccess;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for LivingEntity to handle fall damage for felines.
 * <p>
 * Cats and ocelots always land on their feet, taking no fall damage.
 * Since causeFallDamage is declared on LivingEntity and not overridden in Cat/Ocelot,
 * we inject into LivingEntity and filter for feline instances.
 */
@Mixin(targets = "net.minecraft.world.entity.LivingEntity")
public class FelineFallDamageMixin {

    /**
     * Cancel fall damage for cats and ocelots - they always land on their feet.
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void betterEcology$onFelineFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource,
                                                   CallbackInfoReturnable<Boolean> cir) {
        Object entity = this;
        if (entity instanceof Cat || entity instanceof Ocelot) {
            EcologyComponent component = getEcologyComponent(entity);
            if (component != null) {
                // Felines take no fall damage - they always land on their feet
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Helper method to get the ecology component for an entity.
     */
    private EcologyComponent getEcologyComponent(Object entity) {
        try {
            if (entity instanceof EcologyAccess extensions) {
                return extensions.betterEcology$getEcologyComponent();
            }
        } catch (Exception e) {
            // Component system not available
        }
        return null;
    }
}
