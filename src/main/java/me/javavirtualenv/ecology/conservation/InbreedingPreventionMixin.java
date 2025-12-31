package me.javavirtualenv.ecology.conservation;

import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into Animal.breed() to prevent inbreeding.
 * Checks relationship distance before allowing breeding to proceed.
 */
@Mixin(Animal.class)
public class InbreedingPreventionMixin {

    /**
     * Injects at the beginning of Animal.breed() to check relatedness.
     * Returns false immediately if the animals are too closely related.
     */
    @Inject(
            method = "breed",
            at = @At("HEAD"),
            cancellable = true
    )
    private void betterEcology$checkInbreeding(Animal otherParent, CallbackInfoReturnable<Boolean> cir) {
        Animal self = (Animal) (Object) this;

        if (areTooCloselyRelated(self, otherParent)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Checks if two animals are too closely related for breeding.
     * Prevents breeding between:
     * - Parent and offspring (degree 1)
     * - Full siblings (degree 1)
     * - Half-siblings (degree 1)
     * - Grandparent-grandchild (degree 2)
     * - Aunt/uncle-niece/nephew (degree 2)
     * - First cousins (degree 2)
     *
     * @param animal1 First animal
     * @param animal2 Second animal
     * @return true if too closely related to breed
     */
    private boolean areTooCloselyRelated(Animal animal1, Animal animal2) {
        return LineageRegistry.areTooCloselyRelated(animal1.getUUID(), animal2.getUUID());
    }
}
