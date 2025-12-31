package me.javavirtualenv.ecology.handles;

import me.javavirtualenv.behavior.villager.*;
import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.ecology.EcologyHandle;
import me.javavirtualenv.ecology.EcologyProfile;
import me.javavirtualenv.ecology.api.EcologyAccess;
import me.javavirtualenv.mixin.MobAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

/**
 * Handle for registering villager-specific behavior goals.
 */
public class VillagerBehaviorHandle implements EcologyHandle {

    @Override
    public String id() {
        return "villager_behavior";
    }

    @Override
    public boolean supports(EcologyProfile profile) {
        return profile != null && profile.getBool("villager.enabled", false);
    }

    @Override
    public void initialize(Mob mob, EcologyComponent component, EcologyProfile profile) {
        if (!(mob instanceof Villager villager)) {
            return;
        }

        EcologyAccess access = (EcologyAccess) villager;

        // Get behavior systems from mixin
        TradingReputation tradingReputation = access.betterEcology$getTradingReputation();
        GossipSystem gossipSystem = access.betterEcology$getGossipSystem();
        WorkStationAI workStationAI = access.betterEcology$getWorkStationAI();
        DailyRoutine dailyRoutine = access.betterEcology$getDailyRoutine();
        EnhancedFarming enhancedFarming = access.betterEcology$getEnhancedFarming();

        if (tradingReputation == null || gossipSystem == null || workStationAI == null ||
            dailyRoutine == null || enhancedFarming == null) {
            return;
        }

        // Register custom goals
        registerGoals(villager, tradingReputation, gossipSystem, dailyRoutine, enhancedFarming);
    }

    /**
     * Registers villager behavior goals.
     */
    private void registerGoals(
        Villager villager,
        TradingReputation tradingReputation,
        GossipSystem gossipSystem,
        DailyRoutine dailyRoutine,
        EnhancedFarming enhancedFarming
    ) {
        // Register enhanced trading goal using accessor
        MobAccessor accessor = (MobAccessor) villager;
        accessor.betterEcology$getGoalSelector().addGoal(
            2, // High priority during trading
            new EnhancedTradingGoal(villager, tradingReputation, gossipSystem)
        );

        // Register socialize goal
        accessor.betterEcology$getGoalSelector().addGoal(
            5, // Medium priority
            new SocializeGoal(villager, gossipSystem, dailyRoutine)
        );

        // Note: Work and farming behaviors are handled through tick updates,
        // not goals, as they are continuous behaviors
    }
}
