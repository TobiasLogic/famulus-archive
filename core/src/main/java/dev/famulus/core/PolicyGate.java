package dev.famulus.core;

import java.util.Objects;

/**
 * Wraps a {@link PolicyClient} with the rules that decide whether its answer may be acted on.
 *
 * <p>A policy answer is advice, not authority. Three things can make it unusable: the call failed,
 * the policy was not confident enough, or it chose something this system cannot execute. In each
 * case the gate produces a deterministic outcome instead of guessing, and it counts how often that
 * happens so a confused agent escalates and eventually stops rather than looping forever.
 *
 * <p>Not thread safe. Call it from one thread, off the Minecraft client thread.
 */
public final class PolicyGate {
    private final PolicyClient client;
    private final PolicyGateConfig config;
    private final AgentAction fallback;
    private int consecutiveEscalations;
    private String lastReason = "No decision yet";

    /**
     * @param fallback the deterministic action taken when the policy cannot be reached at all.
     *                 It must be executable, because it is used precisely when nothing else works.
     */
    public PolicyGate(PolicyClient client, PolicyGateConfig config, AgentAction fallback) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        if (!fallback.isExecutable()) {
            throw new IllegalArgumentException("Fallback action must be executable: " + fallback);
        }
    }

    /**
     * Never throws for a policy problem. The returned action is always safe to dispatch, which is
     * why callers may use it directly.
     */
    public AgentAction next(PolicyRequest request) {
        if (consecutiveEscalations >= config.maxConsecutiveEscalations()) {
            lastReason = "Escalated " + consecutiveEscalations + " times without progress; aborting";
            return AgentAction.ABORT_TASK;
        }
        PolicyDecision decision;
        try {
            decision = client.decide(request);
        } catch (PolicyException failure) {
            // A dead policy must not stop the agent: fall back deterministically and say why.
            consecutiveEscalations++;
            lastReason = "Policy unavailable, using " + fallback + ": " + failure.getMessage();
            return fallback;
        }
        if (!request.options().containsKey(decision.action())) {
            consecutiveEscalations++;
            lastReason = "Policy chose " + decision.action() + ", which was not offered";
            return AgentAction.REQUEST_REPLAN;
        }
        if (decision.replanUrgency() >= config.replanUrgencyCeiling()) {
            consecutiveEscalations++;
            lastReason = "Policy reported replan urgency " + round(decision.replanUrgency());
            return AgentAction.REQUEST_REPLAN;
        }
        if (decision.confidence() < config.confidenceFloor()) {
            consecutiveEscalations++;
            lastReason = "Confidence " + round(decision.confidence()) + " below floor "
                    + round(config.confidenceFloor()) + " for " + decision.action();
            return AgentAction.REQUEST_REPLAN;
        }
        if (!decision.action().isExecutable()) {
            // Offering an action with no executor is a programming error, not a policy error.
            consecutiveEscalations++;
            lastReason = "No executor exists for " + decision.action();
            return AgentAction.REQUEST_REPLAN;
        }
        consecutiveEscalations = 0;
        lastReason = "Accepted " + decision.action() + " at confidence " + round(decision.confidence());
        return decision.action();
    }

    /** Why the most recent call returned what it did. Intended for logs and the status command. */
    public String lastReason() {
        return lastReason;
    }

    public int consecutiveEscalations() {
        return consecutiveEscalations;
    }

    /** Call when a task genuinely progresses, so earlier confusion does not accumulate forever. */
    public void reset() {
        consecutiveEscalations = 0;
        lastReason = "Reset after progress";
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
