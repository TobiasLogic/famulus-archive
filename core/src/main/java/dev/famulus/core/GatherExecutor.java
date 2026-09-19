package dev.famulus.core;

/**
 * Deterministic execution boundary. Implementations must restrict cancellation to
 * work started through this instance and must reject unrelated busy execution.
 */
public interface GatherExecutor {
    void start(GatherTask task);

    void cancel();

    /** Activity is evidence of execution, never evidence of task completion. */
    boolean isActive();
}
