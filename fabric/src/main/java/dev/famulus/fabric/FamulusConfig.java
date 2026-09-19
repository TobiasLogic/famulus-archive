package dev.famulus.fabric;

import dev.famulus.core.GatherConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record FamulusConfig(GatherConfig gather, int observationIntervalTicks) {
    public static FamulusConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    # Famulus deterministic gather settings. Restart the client after editing.
                    # Timeouts use monotonic wall time, including time spent paused.
                    stallTimeoutMillis=60000
                    taskTimeoutMillis=600000
                    retryDelayMillis=2000
                    maxAttempts=3
                    observationIntervalTicks=5
                    """);
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { values.load(reader); }
        GatherConfig gather = new GatherConfig(
                number(values, "stallTimeoutMillis", 60000),
                number(values, "taskTimeoutMillis", 600000),
                number(values, "retryDelayMillis", 2000),
                Math.toIntExact(number(values, "maxAttempts", 3)));
        int interval = Math.toIntExact(number(values, "observationIntervalTicks", 5));
        if (interval < 1 || interval > 20) {
            throw new IllegalArgumentException("observationIntervalTicks must be between 1 and 20");
        }
        return new FamulusConfig(gather, interval);
    }

    private static long number(Properties values, String key, long fallback) {
        try { return Long.parseLong(values.getProperty(key, Long.toString(fallback)).trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be an integer", e); }
    }
}
