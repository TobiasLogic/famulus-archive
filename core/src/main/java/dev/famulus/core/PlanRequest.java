package dev.famulus.core;

import java.util.Objects;
import java.util.Set;

/**
 * What the planner is asked for.
 *
 * <p>{@code gatherableItems} is the honest list of what this system can actually obtain today. It is
 * sent so the planner proposes work that can be executed rather than work that merely sounds right,
 * and it is enforced again on the way back: a plan naming anything outside it is rejected.
 *
 * @param goal            the user's request, in their own words
 * @param context         current world and inventory state, kept brief
 * @param gatherableItems namespaced item ids the gather layer supports
 */
public record PlanRequest(String goal, String context, Set<String> gatherableItems) {
    public PlanRequest {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(gatherableItems, "gatherableItems");
        if (goal.isBlank()) {
            throw new IllegalArgumentException("A goal is required");
        }
        if (goal.length() > 2000) {
            throw new IllegalArgumentException("Goal is too long to be a goal");
        }
        gatherableItems = Set.copyOf(gatherableItems);
    }
}
