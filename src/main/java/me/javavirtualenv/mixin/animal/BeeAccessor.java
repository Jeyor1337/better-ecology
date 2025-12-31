package me.javavirtualenv.mixin.animal;

import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for accessing protected/private methods in the Bee class.
 */
@Mixin(Bee.class)
public interface BeeAccessor {

    /**
     * Invokes the protected setHasNectar method.
     */
    @Invoker("setHasNectar")
    void invokeSetHasNectar(boolean hasNectar);
}
