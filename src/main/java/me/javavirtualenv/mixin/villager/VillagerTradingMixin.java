package me.javavirtualenv.mixin.villager;

import me.javavirtualenv.behavior.villager.TradingReputation;
import me.javavirtualenv.ecology.api.EcologyAccess;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to modify villager trading prices based on reputation.
 */
@Mixin(Villager.class)
public class VillagerTradingMixin {

    /**
     * Modifies the price of trades based on player reputation from Better Ecology system.
     * Hooks into updateSpecialPrices which is called when a player starts trading.
     */
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void modifyTradingPrices(Player player, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;

        if (!(villager instanceof EcologyAccess access)) {
            return;
        }

        TradingReputation reputation = access.betterEcology$getTradingReputation();
        if (reputation == null) {
            return;
        }

        // Apply additional price modifications based on Better Ecology's trading reputation
        float reputationModifier = reputation.getReputationModifier(player.getUUID());
        if (reputationModifier != 0.0f) {
            for (MerchantOffer offer : villager.getOffers()) {
                // Apply reputation-based discount or markup
                int adjustment = Math.round(offer.getBaseCostA().getCount() * reputationModifier);
                offer.addToSpecialPriceDiff(-adjustment);
            }
        }
    }
}
