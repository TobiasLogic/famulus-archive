package dev.famulus.fabric;

import dev.famulus.core.AgentAction;
import dev.famulus.core.GatherConfig;
import dev.famulus.core.GatherController;
import dev.famulus.core.GatherTask;
import dev.famulus.core.PlanRunner;
import dev.famulus.core.PlanStep;
import dev.famulus.core.PlannedTask;
import dev.famulus.core.PolicyClient;
import dev.famulus.core.PolicyException;
import dev.famulus.core.PolicyGate;
import dev.famulus.core.PolicyGateConfig;
import dev.famulus.core.PolicyRequest;
import dev.famulus.core.TaskPlan;
import dev.famulus.core.TaskResult;
import dev.famulus.core.TaskStatus;
import dev.famulus.core.WorldSnapshot;
import dev.famulus.jev.CredentialStore;
import dev.famulus.jev.JevClient;
import dev.famulus.jev.JevConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;

/**
 * Runs a whole plan: drives each task through the deterministic engine, and consults the policy
 * layer when a task fails.
 *
 * <p>All state here is touched only from the Minecraft client thread. The single exception is
 * {@link #pendingDecision}, which a worker thread writes once per policy call and the client thread
 * reads. Nothing else crosses the boundary, which is what keeps a network call out of the tick loop
 * without needing locks.
 */
public final class FamulusAgent {
    /** Kept short so a wedged policy call cannot stall a plan indefinitely. */
    private static final int POLICY_TIMEOUT_TICKS = 300;

    private final GatherController gather;
    private final PolicyGate gate;
    private final CredentialStore credentials;
    private final ExecutorService policyThread;
    /** Swapped when a key is saved, so a new key takes effect without restarting Minecraft. */
    private final AtomicReference<PolicyClient> policyClient = new AtomicReference<>();
    private volatile String policyState = "not configured";
    private final Deque<String> log = new ArrayDeque<>();

    /**
     * A decision tagged with the request it answers. Without the tag, an answer that arrived after
     * its request timed out would be applied to whatever situation came next.
     */
    private record Answer(long generation, AgentAction action) {}

    private final AtomicReference<Answer> pendingDecision = new AtomicReference<>();
    private long policyGeneration;
    private PlanRunner runner;
    private boolean taskStarted;
    private boolean policyDispatched;
    private int policyWaitTicks;

    public FamulusAgent(GatherConfig gatherConfig, PolicyGateConfig policyConfig,
                        CredentialStore credentials) {
        this.gather = new GatherController(new BaritoneGatherExecutor(), gatherConfig);
        this.credentials = credentials;
        reloadPolicy();
        // The gate holds a stable delegate, so reloading a key never has to rebuild the gate and
        // lose its escalation counter mid-plan.
        this.gate = new PolicyGate(request -> policyClient.get().decide(request),
                policyConfig, AgentAction.RECOVER);
        // Daemon threads on purpose: Baritone's own non-daemon pool is why the client cannot exit
        // cleanly (see BUGS.md), and this project will not add a second instance of that bug.
        this.policyThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Famulus-policy");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Rebuilds the policy client from the stored or environment key. Safe to call at any time; a
     * plan in progress keeps running either way, because an unconfigured policy just falls back.
     */
    public void reloadPolicy() {
        String key = credentials.resolve().orElse(null);
        if (key == null || key.isBlank()) {
            policyState = "offline, no API key";
            policyClient.set(request -> {
                throw new PolicyException("No API key. Set one in the Settings tab or "
                        + JevConfig.API_KEY_VARIABLE + ".");
            });
            return;
        }
        policyClient.set(new JevClient(JevConfig.withKey(key)));
        policyState = "ready (" + CredentialStore.mask(key)
                + (credentials.isOverriddenByEnvironment() ? ", from environment)" : ")");
    }

    public boolean isPolicyConfigured() {
        return credentials.resolve().isPresent();
    }

    /** A line for the Settings tab. Contains a masked key, never a usable one. */
    public String policyState() {
        return policyState;
    }

    public boolean isRunning() {
        return runner != null && !runner.step().isTerminal() && runner.step() != PlanStep.NOT_STARTED;
    }

    /** @throws IllegalStateException if a plan is already running, or the plan cannot be executed */
    public void start(TaskPlan plan) {
        if (isRunning()) {
            throw new IllegalStateException("A plan is already running. Use /famulus stop first.");
        }
        runner = new PlanRunner(plan, 3);
        taskStarted = false;
        policyDispatched = false;
        policyGeneration++;
        pendingDecision.set(null);
        gate.reset();
        log.clear();
        note("plan started: " + plan.goal() + " (" + plan.tasks().size() + " tasks)");
        runner.start();
    }

    public void tick(Minecraft client, long nowMillis) {
        if (!isRunning()) {
            return;
        }
        switch (runner.step()) {
            case RUN_CURRENT -> runCurrent(client, nowMillis);
            case CONSULT_POLICY -> consultPolicy(client, nowMillis);
            default -> { }
        }
    }

    private void runCurrent(Minecraft client, long nowMillis) {
        PlannedTask task = runner.current();
        if (!(task instanceof PlannedTask.Gather gatherTask)) {
            // PlanRunner refuses unexecutable plans, so this means a task type gained an action
            // before it gained an executor here.
            finishTask(new TaskResult(TaskStatus.INVALID_TARGET,
                    "No executor for " + task.describe(), 0, 0, 0));
            return;
        }
        WorldSnapshot snapshot = FamulusClient.OBSERVER.observe(client, gatherTask.itemId());
        if (!taskStarted) {
            taskStarted = true;
            note("running " + task.describe());
            try {
                gather.start(new GatherTask(UUID.randomUUID().toString(),
                        gatherTask.itemId(), gatherTask.itemId(), gatherTask.count()),
                        snapshot, nowMillis);
            } catch (RuntimeException failure) {
                finishTask(new TaskResult(TaskStatus.FAILED,
                        "Could not start: " + failure.getMessage(), 0, gatherTask.count(), 0));
                return;
            }
        } else {
            gather.tick(snapshot, nowMillis);
        }
        if (!gather.isRunning()) {
            finishTask(gather.result());
        }
    }

    private void finishTask(TaskResult result) {
        taskStarted = false;
        note(result.status() + ": " + result.message());
        PlanStep next = runner.onTaskResult(result);
        if (next.isTerminal()) {
            note("plan " + next + ": " + runner.reason());
        }
    }

    private void consultPolicy(Minecraft client, long nowMillis) {
        Answer answer = pendingDecision.getAndSet(null);
        if (answer != null && answer.generation() == policyGeneration) {
            policyDispatched = false;
            note("policy chose " + answer.action() + " (" + gate.lastReason() + ")");
            PlanStep next = runner.onPolicyDecision(answer.action());
            if (next.isTerminal()) {
                note("plan " + next + ": " + runner.reason());
            }
            return;
        }
        if (!policyDispatched) {
            policyDispatched = true;
            policyWaitTicks = 0;
            long generation = ++policyGeneration;
            PolicyRequest request = new PolicyRequest(
                    describeSituation(client), runner.policyOptions());
            // The gate never throws, so the worker always produces an action and the plan never
            // wedges waiting for a reply that is not coming.
            policyThread.execute(() -> pendingDecision.set(new Answer(generation, gate.next(request))));
            return;
        }
        if (++policyWaitTicks > POLICY_TIMEOUT_TICKS) {
            // Bump the generation so the abandoned request's answer is discarded when it lands.
            policyGeneration++;
            policyDispatched = false;
            note("policy did not answer in time; retrying the task");
            runner.onPolicyDecision(AgentAction.RECOVER);
        }
    }

    /** The state string handed to the policy. Kept compact: cost is driven by input tokens. */
    private String describeSituation(Minecraft client) {
        TaskResult last = gather.result();
        StringBuilder text = new StringBuilder(256);
        text.append("Minecraft agent state.\n");
        text.append("Goal: ").append(runner.plan().goal()).append('\n');
        text.append("Plan: task ").append(runner.completedCount() + 1)
                .append(" of ").append(runner.taskCount()).append('\n');
        text.append("Current task: ").append(runner.current().describe()).append('\n');
        text.append("Attempt ").append(runner.attempts()).append(" of 3\n");
        text.append("Last result: ").append(last.status()).append(", ")
                .append(last.currentCount()).append('/').append(last.targetCount())
                .append(", ").append(last.message()).append('\n');
        if (client.player != null) {
            text.append("Player: alive=").append(client.player.isAlive())
                    .append(", health=").append(Math.round(client.player.getHealth())).append('\n');
        }
        List<String> remaining = runner.plan().tasks().stream()
                .skip(runner.completedCount() + 1L).map(PlannedTask::describe).toList();
        if (!remaining.isEmpty()) {
            text.append("Remaining after this: ").append(String.join("; ", remaining)).append('\n');
        }
        return text.toString();
    }

    public void stop(String reason) {
        gather.stop(reason);
        if (isRunning()) {
            runner.onTaskResult(new TaskResult(TaskStatus.CANCELLED, reason, 0, 0, 0));
            note("stopped: " + reason);
        }
        pendingDecision.set(null);
        policyDispatched = false;
    }

    public String status() {
        if (runner == null) {
            return "No plan. Policy " + policyState + ".";
        }
        return runner.progress();
    }

    public PlanRunner runner() {
        return runner;
    }

    /** Most recent events, newest last, for the status command and the screen. */
    public List<String> recentLog() {
        return List.copyOf(log);
    }

    private void note(String line) {
        log.addLast(line);
        while (log.size() > 40) {
            log.removeFirst();
        }
        FamulusClient.LOGGER.info("[agent] {}", line);
    }
}
