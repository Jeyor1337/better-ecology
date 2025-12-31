package me.javavirtualenv.ecology.api;

import me.javavirtualenv.ecology.EcologyComponent;
import me.javavirtualenv.behavior.villager.*;

public interface EcologyAccess {
	EcologyComponent betterEcology$getEcologyComponent();

	// Villager-specific behavior getters - default implementations return null
	default TradingReputation betterEcology$getTradingReputation() { return null; }
	default GossipSystem betterEcology$getGossipSystem() { return null; }
	default WorkStationAI betterEcology$getWorkStationAI() { return null; }
	default DailyRoutine betterEcology$getDailyRoutine() { return null; }
	default EnhancedFarming betterEcology$getEnhancedFarming() { return null; }
	default VillagerThreatResponse betterEcology$getThreatResponse() { return null; }
}
