package me.javavirtualenv.mixin.ecology;

import java.util.UUID;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.ecology.conservation.LineageRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture animal breeding events and track parent-offspring relationships.
 * When a baby is born from breeding, we store both parents' UUIDs in the baby's ecology component
 * and register the birth with the LineageRegistry for genetic diversity tracking.
 * This enables behaviors like following parents, parent protection, separation distress, and inbreeding prevention.
 */
@Mixin(Animal.class)
public class AnimalBreedingMixin {

    /**
     * Injects into finalizeSpawnChildFromBreeding to capture the baby and set parent UUIDs.
     * This method is called after the baby is created, so it's guaranteed to exist at this point.
     * The parent entity is always the mother in this method (the other parent is the parameter).
     * Registers the birth with the LineageRegistry to track family trees.
     */
    @Inject(
            method = "finalizeSpawnChildFromBreeding",
            at = @At("HEAD")
    )
    private void betterEcology$onChildBorn(
            ServerLevel level,
            Animal otherParent,
            @Nullable AgeableMob baby,
            CallbackInfo ci
    ) {
        if (baby == null) {
            return;
        }

        Animal mother = (Animal) (Object) this;
        UUID motherUuid = mother.getUUID();
        UUID fatherUuid = otherParent != null ? otherParent.getUUID() : null;

        if (baby instanceof EcologyAccess access) {
            EcologyComponent component = access.betterEcology$getEcologyComponent();
            if (component != null) {
                me.javavirtualenv.ecology.handles.ParentChildHandle.setMotherUuid(component, motherUuid);

                component.getGeneticData().setParents(motherUuid, fatherUuid);
                LineageRegistry.registerBirth(baby.getUUID(), motherUuid, fatherUuid);
            }
        }
    }
}
