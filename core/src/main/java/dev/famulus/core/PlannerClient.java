package dev.famulus.core;

/**
 * The planning boundary. Turns a goal in plain language into a structured, validated plan.
 *
 * <p>Implementations perform network calls and must never run on the Minecraft client thread. A
 * planner is far slower than the policy layer, so it belongs at the start of a plan or at an
 * explicit escalation, never in any loop.
 */
public interface PlannerClient {
    /**
     * @throws PlannerException when no usable plan could be obtained. A plan that cannot be
     *                          validated is a failure, never a partially accepted plan.
     */
    TaskPlan plan(PlanRequest request) throws PlannerException;
}
