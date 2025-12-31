package me.javavirtualenv.mixin.villager;

import me.javavirtualenv.behavior.villager.*;
import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.mixin.MobAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for WanderingTrader entity to add enhanced behaviors.
 */
@Mixin(WanderingTrader.class)
public class WanderingTraderMixin implements EcologyAccess {

    @Unique
    private TradingReputation betterEcology$tradingReputation;

    @Unique
    private GossipSystem betterEcology$gossipSystem;

    @Unique
    private boolean betterEcology$behaviorsInitialized = false;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onConstruct(EntityType<? extends WanderingTrader> entityType, Level level, CallbackInfo ci) {
        WanderingTrader trader = (WanderingTrader) (Object) this;
        betterEcology$tradingReputation = new TradingReputation(trader);
        betterEcology$gossipSystem = new GossipSystem(trader);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfo ci) {
        CompoundTag ecologyTag = new CompoundTag();

        ecologyTag.put("TradingReputation", betterEcology$tradingReputation.save());
        ecologyTag.put("GossipSystem", betterEcology$gossipSystem.save());

        tag.put("BetterEcology", ecologyTag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("BetterEcology")) {
            CompoundTag ecologyTag = tag.getCompound("BetterEcology");

            if (ecologyTag.contains("TradingReputation")) {
                betterEcology$tradingReputation.load(ecologyTag.getCompound("TradingReputation"));
            }
            if (ecologyTag.contains("GossipSystem")) {
                betterEcology$gossipSystem.load(ecologyTag.getCompound("GossipSystem"));
            }
        }
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        WanderingTrader trader = (WanderingTrader) (Object) this;

        // Initialize behaviors if not done
        if (!betterEcology$behaviorsInitialized) {
            initializeWanderingTraderGoals(trader);
            betterEcology$behaviorsInitialized = true;
        }

        // Decay gossip periodically (less frequent for wandering traders)
        if (trader.level().getGameTime() % 2400 == 0) {
            betterEcology$gossipSystem.decayGossip();
        }

        // Update supply/demand periodically
        if (trader.level().getGameTime() % 600 == 0) {
            betterEcology$tradingReputation.updateSupplyDemand();
        }
    }

    @Unique
    private void initializeWanderingTraderGoals(WanderingTrader trader) {
        GoalSelector goalSelector = ((MobAccessor) trader).betterEcology$getGoalSelector();

        // Register village seeking goal
        // TODO: Implement SeekVillageGoal class
        // goalSelector.addGoal(3, new SeekVillageGoal(trader));

        // Register socialization goal with villagers
        // TODO: Implement SocializeWithVillagersGoal class
        // goalSelector.addGoal(5, new SocializeWithVillagersGoal(trader, betterEcology$gossipSystem));

        // Register preferential trading goal
        // TODO: Implement PreferentialTradingGoal class
        // goalSelector.addGoal(2, new PreferentialTradingGoal(trader, betterEcology$tradingReputation));
    }

    // Getter methods for behavior systems - override interface defaults

    @Override
    public TradingReputation betterEcology$getTradingReputation() {
        return betterEcology$tradingReputation;
    }

    @Override
    public GossipSystem betterEcology$getGossipSystem() {
        return betterEcology$gossipSystem;
    }

    @Unique
    @Override
    public me.javavirtualenv.ecology.EcologyComponent betterEcology$getEcologyComponent() {
        return null;
    }

    /**
     * Custom accessor for trader-specific behavior systems.
     * These accessors use the EcologyAccess interface to safely retrieve behavior systems.
     */
    @Unique
    public static TradingReputation getTradingReputation(WanderingTrader trader) {
        if (trader instanceof EcologyAccess access) {
            return access.betterEcology$getTradingReputation();
        }
        return null;
    }

    @Unique
    public static GossipSystem getGossipSystem(WanderingTrader trader) {
        if (trader instanceof EcologyAccess access) {
            return access.betterEcology$getGossipSystem();
        }
        return null;
    }
}
