package me.javavirtualenv.mixin.client;

import me.javavirtualenv.client.renderer.layer.WolfHeldItemLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for WolfRenderer to add the held item layer.
 * <p>
 * This mixin injects into WolfRenderer's constructor to add the WolfHeldItemLayer,
 * which renders any items carried by wolves in their mouth.
 */
@Mixin(WolfRenderer.class)
public class WolfRendererMixin {

    /**
     * Inject after the constructor to add the WolfHeldItemLayer.
     * The layer renders items stored in AnimalItemStorage component.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityRendererProvider.Context context, CallbackInfo ci) {
        WolfRenderer renderer = (WolfRenderer) (Object) this;
        renderer.addLayer(new WolfHeldItemLayer(renderer, context.getItemInHandRenderer()));
    }
}
