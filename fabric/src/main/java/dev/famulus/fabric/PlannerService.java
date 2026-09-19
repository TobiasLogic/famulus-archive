package dev.famulus.fabric;

import dev.famulus.core.PlanRequest;
import dev.famulus.core.PlannerException;
import dev.famulus.core.TaskPlan;
import dev.famulus.jev.CredentialStore;
import dev.famulus.planner.ChatPlanner;
import dev.famulus.planner.PlannerConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;

/**
 * Turns a sentence into a plan, off the client thread.
 *
 * <p>Planning takes seconds to tens of seconds, so it never runs inline. The screen starts a
 * request and then reads {@link #state()} and {@link #takePlan()} on later ticks.
 *
 * <p>The endpoint and model are configuration, not code, because every option worth having speaks
 * the same chat completions shape. A local server needs no key, which is why a missing key is only
 * an error for a remote one.
 */
public final class PlannerService {
    private final CredentialStore credentials;
    private final ExecutorService worker;
    private final AtomicReference<TaskPlan> ready = new AtomicReference<>();
    private volatile String endpoint;
    private volatile String model;
    private volatile String state = "idle";
    private volatile boolean busy;

    public PlannerService(CredentialStore credentials, String endpoint, String model) {
        this.credentials = credentials;
        this.endpoint = endpoint;
        this.model = model;
        // Daemon, so a pending plan can never stop the client from exiting.
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Famulus-planner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String endpoint() {
        return endpoint;
    }

    public String model() {
        return model;
    }

    public boolean isBusy() {
        return busy;
    }

    /** A line for the screen. Never contains the key. */
    public String state() {
        return state;
    }

    public void configure(String newEndpoint, String newModel) {
        this.endpoint = newEndpoint.trim();
        this.model = newModel.trim();
        this.state = "set to " + this.model;
    }

    /** Collects a finished plan exactly once. */
    public Optional<TaskPlan> takePlan() {
        return Optional.ofNullable(ready.getAndSet(null));
    }

    /**
     * Starts a planning request. Returns false if one is already running, so a second click cannot
     * queue up a duplicate.
     */
    public boolean request(String goal, Minecraft client) {
        if (busy) {
            return false;
        }
        PlannerConfig config;
        try {
            config = buildConfig();
        } catch (RuntimeException invalid) {
            state = invalid.getMessage();
            return false;
        }
        String context = describeWorld(client);
        busy = true;
        state = "asking " + model + "...";
        worker.execute(() -> {
            try {
                TaskPlan plan = new ChatPlanner(config).plan(
                        new PlanRequest(goal, context, GatherCatalog.items()));
                ready.set(plan);
                state = "plan ready: " + plan.tasks().size() + " tasks";
            } catch (PlannerException refused) {
                state = refused.getMessage();
            } catch (RuntimeException unexpected) {
                state = "planner failed: " + unexpected;
            } finally {
                busy = false;
            }
        });
        return true;
    }

    private PlannerConfig buildConfig() {
        boolean local = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
        String key = credentials.resolve().orElse("");
        if (key.isBlank() && !local) {
            throw new IllegalStateException("No API key. Set one in Settings, or point the planner "
                    + "at a local server.");
        }
        return new PlannerConfig(endpoint, model, key, Duration.ofSeconds(local ? 180 : 90));
    }

    /** Brief, because the planner is billed by input and a long dump helps nobody. */
    private static String describeWorld(Minecraft client) {
        if (client.player == null || client.level == null) {
            return "Not in a world.";
        }
        return "Dimension: " + client.level.dimension().identifier()
                + "\nPosition: " + client.player.blockPosition().toShortString()
                + "\nHealth: " + Math.round(client.player.getHealth())
                + "\nGame mode: " + client.player.gameMode();
    }
}
