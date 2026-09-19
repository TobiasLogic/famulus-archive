package dev.famulus.fabric;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.famulus.core.GatherController;
import dev.famulus.core.GatherTask;
import dev.famulus.core.TaskResult;
import dev.famulus.core.TaskStatus;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class FamulusClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Famulus");
    private static final Gson JSON = new Gson();
    private final MinecraftObserver observer = new MinecraftObserver();
    private final BaritoneGatherExecutor executor = new BaritoneGatherExecutor();
    private GatherController controller;
    private FamulusConfig config;
    private String configurationError;
    private TaskStatus lastStatus;
    private int ticks;

    @Override
    public void onInitializeClient() {
        try {
            config = FamulusConfig.load(FabricLoader.getInstance().getConfigDir().resolve("famulus.properties"));
            controller = new GatherController(executor, config.gather());
        } catch (Exception e) {
            configurationError = "Fix config/famulus.properties and restart: " + e.getMessage();
            LOGGER.error("Famulus configuration is invalid. {}", configurationError, e);
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(literal("famulus")
                        .requires(FabricClientCommandSource::attended)
                        .executes(context -> status(context.getSource()))
                        .then(literal("status").executes(context -> status(context.getSource())))
                        .then(literal("stop").executes(context -> stop(context.getSource())))
                        .then(literal("gather")
                                .then(argument("item", IdentifierArgument.id())
                                        .suggests((context, builder) -> {
                                            GatherCatalog.items().stream()
                                                    .filter(id -> id.startsWith(builder.getRemainingLowerCase())
                                                            || id.substring("minecraft:".length()).startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .then(argument("count", IntegerArgumentType.integer(1, 2304))
                                                .executes(context -> gather(context.getSource(),
                                                        context.getArgument("item", Identifier.class),
                                                        IntegerArgumentType.getInteger(context, "count"))))))));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LOGGER.info("Famulus 0.1.0 initialized for Minecraft 26.2; deterministic gather only.");
    }

    private int gather(FabricClientCommandSource source, Identifier item, int count) {
        if (!ready(source)) return 0;
        Minecraft client = Minecraft.getInstance();
        if (controller.isRunning()) return error(source, "A gather task is running. Use /famulus stop first.");
        if (client.player == null || client.level == null) return error(source, "Join a world first.");
        if (client.player.gameMode() != GameType.SURVIVAL) {
            return error(source, "Gather requires survival mode; creative mining does not drop items.");
        }
        if (!BuiltInRegistries.ITEM.containsKey(item)) return error(source, "Unknown item: " + item);
        if (!GatherCatalog.supports(item.toString())) {
            return error(source, "Unsupported gather item. Prototype supports logs, dirt, sand and red sand. Use tab completion.");
        }
        try {
            if (executor.isBusy()) return error(source, "Baritone is already busy. Stop its current operation first.");
            GatherTask task = new GatherTask(UUID.randomUUID().toString(), item.toString(), item.toString(), count);
            controller.start(task, observer.observe(client, task.itemId()), now());
            lastStatus = controller.result().status();
            report(source, controller.result());
            LOGGER.info("task={} item={} result={}", task.id(), task.itemId(), JSON.toJson(controller.result()));
            return controller.result().status() == TaskStatus.FAILED ? 0 : 1;
        } catch (RuntimeException e) {
            LOGGER.error("Cannot start gather", e);
            return error(source, "Cannot start gather: " + e.getMessage());
        }
    }

    private void tick(Minecraft client) {
        if (controller == null || !controller.isRunning()) return;
        if (++ticks % config.observationIntervalTicks() != 0) return;
        try {
            controller.tick(observer.observe(client, controller.task().itemId()), now());
            TaskResult result = controller.result();
            if (result.status() != lastStatus) {
                lastStatus = result.status();
                LOGGER.info("task={} result={}", controller.task().id(), JSON.toJson(result));
                if (client.player != null) client.player.sendSystemMessage(Component.literal(format(result)));
            }
        } catch (RuntimeException e) {
            // Observation/integration exceptions also stop owned movement instead of escaping every tick.
            controller.stop("Minecraft observation failed: " + e.getMessage());
            LOGGER.error("Gather stopped after integration failure", e);
        }
    }

    private int status(FabricClientCommandSource source) {
        if (!ready(source)) return 0;
        report(source, controller.result());
        return 1;
    }

    private int stop(FabricClientCommandSource source) {
        if (!ready(source)) return 0;
        controller.stop("Stopped by user");
        lastStatus = controller.result().status();
        report(source, controller.result());
        LOGGER.info("Stopped: {}", JSON.toJson(controller.result()));
        return 1;
    }

    private boolean ready(FabricClientCommandSource source) {
        if (controller != null) return true;
        error(source, configurationError);
        return false;
    }

    private void report(FabricClientCommandSource source, TaskResult result) {
        source.sendFeedback(Component.literal(format(result)));
    }

    private String format(TaskResult result) {
        return "[Famulus] " + result.status() + " | " + result.currentCount() + "/" + result.targetCount()
                + " | attempts " + result.attempts() + " | " + result.message();
    }

    private int error(FabricClientCommandSource source, String message) {
        source.sendError(Component.literal("[Famulus] " + message));
        return 0;
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }
}
