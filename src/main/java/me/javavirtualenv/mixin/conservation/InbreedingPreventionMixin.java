package me.javavirtualenv.mixin.conservation;

import me.javavirtualenv.ecology.conservation.LineageRegistry;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into Animal.canMate() to prevent inbreeding.
 * Checks relationship distance before allowing breeding to proceed.
 */
@Mixin(Animal.class)
public class InbreedingPreventionMixin {

    /**
     * Injects at the end of Animal.canMate() to check relatedness.
     * Returns false immediately if the animals are too closely related.
     */
    @Inject(
            method = "canMate",
            at = @At("RETURN"),
            cancellable = true
    )
    private void betterEcology$checkInbreeding(Animal other, CallbackInfoReturnable<Boolean> cir) {
        // If vanilla checks already passed, now verify they're not too closely related
        if (cir.getReturnValue()) {
            Animal self = (Animal) (Object) this;
            if (LineageRegistry.areTooCloselyRelated(self.getUUID(), other.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }
}
