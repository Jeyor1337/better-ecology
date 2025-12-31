package me.javavirtualenv.ecology.conservation;

/**
 * Conservation status categories for species population monitoring.
 * Based on the Minimum Viable Population (MVP) theory from conservation biology.
 *
 * Thresholds are defined as percentages of the species' MVP threshold:
 * - HEALTHY: Above 100% of MVP
 * - VULNERABLE: 50-100% of MVP
 * - ENDANGERED: 30-50% of MVP (Allee threshold range)
 * - CRITICAL: Below 30% of MVP (Allee effect dominates)
 */
public enum ConservationStatus {
    HEALTHY(1.0, 2.0),
    VULNERABLE(0.5, 1.0),
    ENDANGERED(0.3, 0.5),
    CRITICAL(0.0, 0.3);

    private final double minThreshold;
    private final double maxThreshold;

    ConservationStatus(double minThreshold, double maxThreshold) {
        this.minThreshold = minThreshold;
        this.maxThreshold = maxThreshold;
    }

    /**
     * Calculate conservation status based on current population and MVP threshold.
     *
     * @param currentPopulation Current population count
     * @param mvpThreshold Minimum viable population threshold
     * @return Conservation status for the species
     */
    public static ConservationStatus calculateStatus(int currentPopulation, int mvpThreshold) {
        double ratio = (double) currentPopulation / mvpThreshold;

        for (ConservationStatus status : values()) {
            if (ratio >= status.minThreshold && ratio < status.maxThreshold) {
                return status;
            }
        }
        return ratio >= HEALTHY.maxThreshold ? HEALTHY : CRITICAL;
    }

    /**
     * Check if the population is in a state requiring conservation intervention.
     *
     * @return true if status is VULNERABLE or worse
     */
    public boolean requiresIntervention() {
        return this != HEALTHY;
    }

    /**
     * Check if the population is in critical state requiring emergency breeding.
     *
     * @return true if status is ENDANGERED or CRITICAL
     */
    public boolean isEmergencyState() {
        return this == ENDANGERED || this == CRITICAL;
    }

    /**
     * Get the breeding success multiplier for this status.
     * Critical populations get 2.0x breeding success.
     *
     * @return Breeding success multiplier
     */
    public double getBreedingBonus() {
        return switch (this) {
            case CRITICAL -> 2.0;
            case ENDANGERED -> 1.5;
            case VULNERABLE -> 1.2;
            case HEALTHY -> 1.0;
        };
    }
}
