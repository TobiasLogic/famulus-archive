package dev.famulus.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An ordered plan. Whether it came from a blueprint's material list or an LLM, it is validated the
 * same way before anything runs: a plan is untrusted input until its ids are unique and every task
 * has an executor.
 */
public record TaskPlan(String goal, List<PlannedTask> tasks) {
    public TaskPlan {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(tasks, "tasks");
        if (goal.isBlank()) {
            throw new IllegalArgumentException("A plan needs a goal");
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("A plan needs at least one task");
        }
        Set<String> seen = new HashSet<>();
        for (PlannedTask task : tasks) {
            Objects.requireNonNull(task, "task");
            if (!seen.add(task.id())) {
                throw new IllegalArgumentException("Duplicate task id: " + task.id());
            }
        }
        tasks = List.copyOf(tasks);
    }

    /**
     * Tasks whose action has no executor yet. A plan containing any of these must be refused with
     * an explanation rather than started and abandoned partway.
     */
    public List<PlannedTask> unexecutable() {
        return tasks.stream().filter(task -> !task.action().isExecutable()).toList();
    }

    public boolean isExecutable() {
        return unexecutable().isEmpty();
    }
}
