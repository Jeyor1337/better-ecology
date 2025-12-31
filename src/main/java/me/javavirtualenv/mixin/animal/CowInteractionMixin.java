package me.javavirtualenv.mixin.animal;

import me.javavirtualenv.behavior.cow.MooshroomMilkProductionHandle;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHooks;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.ecology.handles.production.MilkProductionHandle;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for cow and mooshroom interaction with milking.
 *
 * Handles:
 * - Bucket milking for cows and mooshrooms
 * - Bowl milking for mooshrooms (mushroom stew)
 * - Flower feeding for mooshrooms (suspicious stew)
 * - Integration with simplified milk production handles
 */
@Mixin(value = {Cow.class, MushroomCow.class})
public abstract class CowInteractionMixin {

    /**
     * Inject into mobInteract to handle custom milking behavior.
     * This runs before the vanilla milking logic, allowing us to override it.
     * Only runs on the server side to prevent client/server desync.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void betterEcology$handleMilking(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // Only handle on server side - client will sync from server
        if (player.level().isClientSide) {
            return;
        }

        if (!(this instanceof Cow cow)) {
            return;
        }

        ItemStack heldItem = player.getItemInHand(hand);

        // Handle empty bucket (standard milking)
        if (heldItem.is(Items.BUCKET)) {
            handleBucketMilking(cow, player, hand, cir);
            return;
        }

        // Handle mooshroom-specific interactions
        if (cow instanceof MushroomCow mooshroom) {
            handleMooshroomInteractions(mooshroom, player, hand, heldItem, cir);
        }
    }

    /**
     * Handle bucket milking for both cows and mooshrooms.
     */
    private void handleBucketMilking(Cow cow, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        EcologyComponent component = EcologyHooks.getEcologyComponent(cow);
        if (component == null || !component.hasProfile()) {
            return; // Fall back to vanilla behavior
        }

        EcologyProfile profile = component.profile();
        if (profile == null) {
            return;
        }

        // Find the milk production handle
        MilkProductionHandle milkHandle = findMilkProductionHandle(component);
        if (milkHandle == null) {
            return; // Fall back to vanilla
        }

        // Check if can be milked
        if (!milkHandle.canBeMilked(cow, component)) {
            return; // Let vanilla handle the failure case
        }

        // Perform milking
        ItemStack milkProduct = milkHandle.onMilked(cow, component, player);
        if (!milkProduct.isEmpty()) {
            // Use vanilla ItemUtils pattern for proper inventory handling
            ItemStack heldItemStack = player.getItemInHand(hand);
            ItemStack resultStack = ItemUtils.createFilledResult(heldItemStack, player, milkProduct);

            // Play milking sound
            cow.level().playSound(null, cow, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);

            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    /**
     * Handle mooshroom-specific interactions (bowl for stew, flowers).
     */
    private void handleMooshroomInteractions(MushroomCow mooshroom, Player player, InteractionHand hand,
                                             ItemStack heldItem, CallbackInfoReturnable<InteractionResult> cir) {
        EcologyComponent component = EcologyHooks.getEcologyComponent(mooshroom);
        if (component == null || !component.hasProfile()) {
            return;
        }

        EcologyProfile profile = component.profile();
        if (profile == null) {
            return;
        }

        // Handle bowl (stew milking)
        if (heldItem.is(Items.BOWL)) {
            handleBowlMilking(mooshroom, player, hand, component, cir);
            return;
        }

        // Handle flower feeding
        MooshroomMilkProductionHandle mooshroomHandle = findMooshroomMilkHandle(component);
        if (mooshroomHandle != null && mooshroomHandle.onFlowerFed(mooshroom, heldItem)) {
            // Consume flower
            if (!player.getAbilities().instabuild) {
                heldItem.shrink(1);
            }
            // Play eating sound
            mooshroom.level().playSound(null, mooshroom, SoundEvents.MOOSHROOM_EAT,
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    /**
     * Handle bowl milking for mooshrooms (mushroom stew).
     */
    private void handleBowlMilking(MushroomCow mooshroom, Player player, InteractionHand hand,
                                   EcologyComponent component, CallbackInfoReturnable<InteractionResult> cir) {
        MooshroomMilkProductionHandle mooshroomHandle = findMooshroomMilkHandle(component);
        if (mooshroomHandle == null) {
            return;
        }

        ItemStack stew = mooshroomHandle.onMooshroomMilked(mooshroom, component, player.getItemInHand(hand));
        if (!stew.isEmpty()) {
            // Use vanilla ItemUtils pattern
            ItemStack heldItemStack = player.getItemInHand(hand);
            ItemStack resultStack = ItemUtils.createFilledResult(heldItemStack, player, stew);

            // Play milking sound
            mooshroom.level().playSound(null, mooshroom, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);

            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    /**
     * Find the milk production handle from the component.
     */
    private MilkProductionHandle findMilkProductionHandle(EcologyComponent component) {
        for (var handle : component.handles()) {
            if (handle instanceof MilkProductionHandle milkHandle) {
                return milkHandle;
            }
        }
        return null;
    }

    /**
     * Find the mooshroom-specific milk handle from the component.
     */
    private MooshroomMilkProductionHandle findMooshroomMilkHandle(EcologyComponent component) {
        for (var handle : component.handles()) {
            if (handle instanceof MooshroomMilkProductionHandle mooshroomHandle) {
                return mooshroomHandle;
            }
        }
        return null;
    }
}
