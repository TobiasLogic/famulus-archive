package dev.famulus.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A typed choice from the policy layer.
 *
 * @param action        the selected action, always one of the requested options
 * @param confidence    how certain the policy is, 0 to 1; drives escalation, not correctness
 * @param probabilities probability per offered action
 * @param replanUrgency probability that this situation needs the planner, 0 to 1
 */
public record PolicyDecision(AgentAction action, double confidence,
                             Map<AgentAction, Double> probabilities, double replanUrgency) {
    public PolicyDecision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(probabilities, "probabilities");
        requireUnitInterval(confidence, "confidence");
        requireUnitInterval(replanUrgency, "replanUrgency");
        probabilities = Collections.unmodifiableMap(new EnumMap<>(probabilities));
    }

    public double probabilityOf(AgentAction candidate) {
        return probabilities.getOrDefault(candidate, 0.0);
    }

    private static void requireUnitInterval(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
