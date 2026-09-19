package dev.famulus.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One decision point. {@code state} is plain text describing the world and the current plan;
 * {@code options} maps each permitted action to the description the policy is shown.
 *
 * <p>Cost is driven by state length, so keep it compact and structured rather than dumping raw
 * game data into it.
 */
public record PolicyRequest(String state, Map<AgentAction, String> options) {
    public PolicyRequest {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(options, "options");
        if (state.isBlank()) {
            throw new IllegalArgumentException("State must describe the situation");
        }
        if (options.size() < 2) {
            throw new IllegalArgumentException("A decision needs at least two options");
        }
        options.forEach((action, description) -> {
            Objects.requireNonNull(action, "option action");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Option " + action + " needs a description");
            }
        });
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }
}
