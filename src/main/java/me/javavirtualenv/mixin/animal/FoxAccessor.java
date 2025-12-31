package me.javavirtualenv.mixin.animal;

import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

/**
 * Accessor for Fox-specific methods.
 */
@Mixin(Fox.class)
public interface FoxAccessor {
    @Invoker("setSleeping")
    void betterEcology$setSleeping(boolean sleeping);

    @Invoker("trusts")
    boolean betterEcology$trusts(UUID uuid);
}
