package dev.famulus.core;

/**
 * Thresholds for acting on a policy answer.
 *
 * <p>These numbers are <strong>not</strong> validated against real gameplay yet. The defaults are a
 * starting point chosen from a handful of observed calls, not a tuned result. Treat them as
 * something to measure and adjust, and see docs/JEV.md before changing them on a hunch.
 *
 * @param confidenceFloor          minimum confidence required to act on a choice
 * @param replanUrgencyCeiling     replan probability at or above which the planner is invoked
 * @param maxConsecutiveEscalations escalations in a row before the task is abandoned, which is what
 *                                  stops a confused agent from looping forever
 */
public record PolicyGateConfig(double confidenceFloor, double replanUrgencyCeiling,
                               int maxConsecutiveEscalations) {
    public PolicyGateConfig {
        requireUnitInterval(confidenceFloor, "confidenceFloor");
        requireUnitInterval(replanUrgencyCeiling, "replanUrgencyCeiling");
        if (maxConsecutiveEscalations < 1) {
            throw new IllegalArgumentException("Must allow at least one escalation");
        }
    }

    public static PolicyGateConfig defaults() {
        return new PolicyGateConfig(0.55, 0.70, 3);
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
