package dev.famulus.core;

/** What the caller must do next with a {@link PlanRunner}. */
public enum PlanStep {
    NOT_STARTED,
    /** Execute {@link PlanRunner#current()}. */
    RUN_CURRENT,
    /** Ask the policy layer, off-thread, using {@link PlanRunner#policyOptions()}. */
    CONSULT_POLICY,
    PLAN_COMPLETE,
    PLAN_FAILED,
    PLAN_CANCELLED,
    /** The plan itself looks wrong; the planner should produce a new one. */
    REPLAN_REQUIRED;

    public boolean isTerminal() {
        return this == PLAN_COMPLETE || this == PLAN_FAILED
                || this == PLAN_CANCELLED || this == REPLAN_REQUIRED;
    }

    public boolean isSuccess() {
        return this == PLAN_COMPLETE;
    }
}
