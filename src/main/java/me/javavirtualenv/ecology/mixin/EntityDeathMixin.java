package me.javavirtualenv.ecology.mixin;

import me.javavirtualenv.ecology.conservation.PopulationRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to track entity deaths for the conservation system.
 * Hooks into Entity.setRemoved() to update population registry when mobs die.
 */
@Mixin(Entity.class)
public class EntityDeathMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void betterEcology$onEntityRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        // Only track mob entities
        if (entity.getType().isEnabled(EntityType.CATEGORY)) {
            return;
        }

        if (!(entity instanceof Mob mob)) {
            return;
        }

        // Only track actual deaths, not chunk unloads or other removals
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) {
            return;
        }

        // Update population registry
        PopulationRegistry.onEntityDespawned(mob);
    }
}
