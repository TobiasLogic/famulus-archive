package dev.famulus.planner;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Where the planner lives and which model answers.
 *
 * <p>Deliberately just a base URL, a model id and an optional key, because every option worth having
 * speaks the same OpenAI style chat completions shape. OpenRouter, Ollama, llama.cpp's server and
 * LM Studio all work through the same two fields, so choosing a model is configuration rather than
 * code, and a local model needs no key at all.
 *
 * @param endpoint full chat completions URL
 * @param model    model identifier as the endpoint expects it
 * @param apiKey   bearer token, or blank for a local server that wants none
 * @param timeout  per request; planning is slow, so this is generous compared with the policy layer
 */
public record PlannerConfig(String endpoint, String model, String apiKey, Duration timeout) {
    public static final String OPENROUTER = "https://openrouter.ai/api/v1/chat/completions";
    public static final String OLLAMA = "http://localhost:11434/v1/chat/completions";
    public static final String LLAMA_CPP = "http://localhost:8080/v1/chat/completions";
    public static final String LM_STUDIO = "http://localhost:1234/v1/chat/completions";

    public static final String DEFAULT_MODEL = "deepseek/deepseek-v4.1-flash";

    /**
     * Suggestions for the model selector. Not a restriction: any model id the endpoint accepts
     * works, and this list exists only so the field does not start empty and unguessable.
     */
    public static List<String> suggestedModels() {
        return List.of(
                "deepseek/deepseek-v4.1-flash",
                "deepseek/deepseek-v4-flash",
                "deepseek/deepseek-v4-pro",
                "anthropic/claude-sonnet-5",
                "openai/gpt-5",
                "google/gemini-3-pro",
                "qwen/qwen3-max");
    }

    /** Endpoint presets for the selector, paired with a label. */
    public static List<String> suggestedEndpoints() {
        return List.of(OPENROUTER, OLLAMA, LLAMA_CPP, LM_STUDIO);
    }

    public PlannerConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(timeout, "timeout");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("A planner endpoint is required");
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("Endpoint must be an http or https URL: " + endpoint);
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("A model must be chosen");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    public static PlannerConfig openRouter(String apiKey, String model) {
        return new PlannerConfig(OPENROUTER, model, apiKey, Duration.ofSeconds(90));
    }

    /** A local server, which needs no key. */
    public static PlannerConfig local(String endpoint, String model) {
        return new PlannerConfig(endpoint, model, "", Duration.ofSeconds(180));
    }

    /** True when this configuration is talking to something on this machine. */
    public boolean isLocal() {
        return endpoint.contains("localhost") || endpoint.contains("127.0.0.1");
    }

    public boolean hasKey() {
        return !apiKey.isBlank();
    }
}
