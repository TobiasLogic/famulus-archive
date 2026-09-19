package dev.famulus.jev;

import java.time.Duration;
import java.util.Objects;

/**
 * Connection settings for the Jev decision model.
 *
 * <p>The API key is held in memory only. It is never written to a file by this class and must never
 * be committed; see HANDOFF.md.
 *
 * @param endpoint  full decisions endpoint URL
 * @param model     model identifier passed in the request body
 * @param apiKey    bearer token
 * @param timeout   per-request timeout; the caller stays responsive because this runs off-thread
 */
public record JevConfig(String endpoint, String model, String apiKey, Duration timeout) {
    /** OpenRouter's decisions endpoint. Note the {@code alpha} path segment; see docs/JEV.md. */
    public static final String OPENROUTER_DECISIONS = "https://openrouter.ai/api/alpha/decisions";

    /** TypeSafe's own endpoint, which takes a TypeSafe key and the model id {@code jev-latest}. */
    public static final String TYPESAFE_SYSTEMONE = "https://api.typesafe.ai/v1/systemone";

    public static final String DEFAULT_MODEL = "typesafe/jev-1.13";
    public static final String API_KEY_VARIABLE = "OPENROUTER_API_KEY";

    public JevConfig {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(timeout, "timeout");
        if (endpoint.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("Endpoint and model must not be blank");
        }
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing API key. Set " + API_KEY_VARIABLE + " in the environment.");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    /**
     * Reads the key from the environment.
     *
     * @throws IllegalStateException with an actionable message when the variable is unset, because
     *                               a blank key would otherwise fail later as an opaque 401.
     */
    public static JevConfig fromEnvironment() {
        String key = System.getenv(API_KEY_VARIABLE);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(API_KEY_VARIABLE
                    + " is not set. Export it before starting Minecraft; never commit it.");
        }
        return new JevConfig(OPENROUTER_DECISIONS, DEFAULT_MODEL, key, Duration.ofSeconds(10));
    }
}
