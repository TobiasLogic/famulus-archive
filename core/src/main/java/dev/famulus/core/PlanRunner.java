package dev.famulus.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Walks a {@link TaskPlan} one task at a time, deciding when the policy layer needs to be asked.
 *
 * <p>It never calls the policy itself. {@link #onTaskResult} returns {@link PlanStep#CONSULT_POLICY}
 * and the caller performs that call off-thread, handing the answer back through
 * {@link #onPolicyDecision}. That is what keeps this class free of I/O and threading, and therefore
 * testable without a game or a network.
 *
 * <p>Not thread safe. Drive it from one thread.
 */
public final class PlanRunner {
    private final TaskPlan plan;
    private final int maxAttemptsPerTask;
    private final List<TaskResult> completed = new ArrayList<>();

    private int index;
    private int attempts;
    private PlanStep step = PlanStep.NOT_STARTED;
    private String reason = "Not started";

    public PlanRunner(TaskPlan plan, int maxAttemptsPerTask) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (maxAttemptsPerTask < 1) {
            throw new IllegalArgumentException("Each task needs at least one attempt");
        }
        this.maxAttemptsPerTask = maxAttemptsPerTask;
        if (!plan.isExecutable()) {
            // Refuse up front rather than running half the plan and stopping at the first task with
            // no executor, which would leave the world in a partly changed state.
            throw new IllegalArgumentException("Plan contains tasks with no executor: "
                    + plan.unexecutable().stream().map(PlannedTask::describe).toList());
        }
    }

    public PlanStep start() {
        if (step != PlanStep.NOT_STARTED) {
            throw new IllegalStateException("This plan has already been started");
        }
        attempts = 1;
        reason = "Starting " + current().describe();
        return step = PlanStep.RUN_CURRENT;
    }

    public PlannedTask current() {
        if (index >= plan.tasks().size()) {
            return null;
        }
        return plan.tasks().get(index);
    }

    /**
     * Records the outcome of the current task.
     *
     * @return what the caller must do next
     */
    public PlanStep onTaskResult(TaskResult result) {
        Objects.requireNonNull(result, "result");
        requireActive();
        completed.add(result);
        return switch (result.status()) {
            case SUCCESS -> advance("Finished " + describeCurrent());
            // A user stop is an instruction, not a problem to reason about.
            case CANCELLED -> fail(PlanStep.PLAN_CANCELLED, "Cancelled during " + describeCurrent());
            default -> {
                if (attempts >= maxAttemptsPerTask) {
                    yield fail(PlanStep.PLAN_FAILED, describeCurrent() + " failed "
                            + attempts + " times: " + result.message());
                }
                reason = describeCurrent() + " returned " + result.status() + ": " + result.message();
                yield step = PlanStep.CONSULT_POLICY;
            }
        };
    }

    /** The choices to offer the policy layer while {@link PlanStep#CONSULT_POLICY} is pending. */
    public Map<AgentAction, String> policyOptions() {
        if (step != PlanStep.CONSULT_POLICY) {
            throw new IllegalStateException("No decision is pending");
        }
        Map<AgentAction, String> options = new LinkedHashMap<>();
        options.put(AgentAction.RECOVER, "Retry the current task; the problem looks temporary");
        options.put(AgentAction.EXPLORE,
                "Nothing suitable is nearby; range outward to find some, then retry the task");
        options.put(AgentAction.COMPLETE_TASK,
                "Treat the current task as finished and move on to the next one");
        options.put(AgentAction.REQUEST_REPLAN,
                "The plan itself looks wrong; hand the situation to the planner");
        options.put(AgentAction.ABORT_TASK, "Give up on the whole plan");
        return options;
    }

    /** Applies the policy's answer. Any action that was not offered aborts, rather than being guessed at. */
    public PlanStep onPolicyDecision(AgentAction action) {
        Objects.requireNonNull(action, "action");
        if (step != PlanStep.CONSULT_POLICY) {
            throw new IllegalStateException("No decision is pending");
        }
        return switch (action) {
            case RECOVER -> {
                attempts++;
                reason = "Retrying " + describeCurrent() + ", attempt " + attempts
                        + " of " + maxAttemptsPerTask;
                yield step = PlanStep.RUN_CURRENT;
            }
            case EXPLORE -> {
                // Exploring counts as an attempt. Otherwise a policy that keeps choosing it would
                // wander forever without the plan ever giving up.
                attempts++;
                reason = "Exploring for " + describeCurrent() + ", attempt " + attempts
                        + " of " + maxAttemptsPerTask;
                yield step = PlanStep.EXPLORE;
            }
            case COMPLETE_TASK -> advance("Policy accepted " + describeCurrent() + " as finished");
            case REQUEST_REPLAN -> fail(PlanStep.REPLAN_REQUIRED,
                    "Policy escalated during " + describeCurrent());
            default -> fail(PlanStep.PLAN_FAILED,
                    "Policy chose " + action + " during " + describeCurrent());
        };
    }

    /**
     * Called when an exploration has finished, whether or not it found anything. The task is retried
     * either way: the observed inventory, not the search, decides whether it succeeds.
     */
    public PlanStep onExploreComplete(String summary) {
        if (step != PlanStep.EXPLORE) {
            throw new IllegalStateException("Not exploring, the plan is " + step);
        }
        reason = summary + "; retrying " + describeCurrent();
        return step = PlanStep.RUN_CURRENT;
    }

    private PlanStep advance(String why) {
        index++;
        attempts = 1;
        if (index >= plan.tasks().size()) {
            reason = "Plan complete: " + plan.goal();
            return step = PlanStep.PLAN_COMPLETE;
        }
        reason = why + "; next is " + current().describe();
        return step = PlanStep.RUN_CURRENT;
    }

    private PlanStep fail(PlanStep terminal, String why) {
        reason = why;
        return step = terminal;
    }

    private void requireActive() {
        if (step.isTerminal() || step == PlanStep.NOT_STARTED) {
            throw new IllegalStateException("Plan is not running, it is " + step);
        }
    }

    private String describeCurrent() {
        PlannedTask task = current();
        return task == null ? "the plan" : task.describe();
    }

    public PlanStep step() {
        return step;
    }

    public String reason() {
        return reason;
    }

    public TaskPlan plan() {
        return plan;
    }

    /** Index of the task being worked on, 0-based, for progress display. */
    public int taskIndex() {
        return Math.min(index, plan.tasks().size() - 1);
    }

    public int taskCount() {
        return plan.tasks().size();
    }

    public int completedCount() {
        return Math.min(index, plan.tasks().size());
    }

    public int attempts() {
        return attempts;
    }

    public List<TaskResult> results() {
        return List.copyOf(completed);
    }

    /** One line for the status command and the screen. */
    public String progress() {
        return "[" + completedCount() + "/" + taskCount() + "] " + step + " - " + reason;
    }
}
