package dev.famulus.jev;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Reads and writes the API key, kept in its own file away from ordinary settings.
 *
 * <p>Separate on purpose. Settings get pasted into bug reports and screenshots; credentials should
 * not travel with them. The file is created with owner-only permissions where the filesystem
 * supports it, and nothing here ever writes a key to a log.
 *
 * <p>The environment variable wins over the stored file. That keeps a temporary key usable for one
 * session without overwriting the saved one, and lets tests and CI supply a key without touching
 * the user's disk.
 */
public final class CredentialStore {
    public static final String FILE_NAME = "credentials.properties";
    private static final String KEY_PROPERTY = "openrouter.api.key";
    private static final String HEADER =
            " Famulus credentials. Keep this file private and out of version control.\n"
            + "# The " + JevConfig.API_KEY_VARIABLE + " environment variable overrides what is stored here.";

    private final Path file;

    public CredentialStore(Path directory) {
        this.file = Objects.requireNonNull(directory, "directory").resolve(FILE_NAME);
    }

    /** The key to use: the environment first, then the stored file. Empty when neither is set. */
    public Optional<String> resolve() {
        String fromEnvironment = System.getenv(JevConfig.API_KEY_VARIABLE);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return Optional.of(fromEnvironment.trim());
        }
        return stored();
    }

    /** Only what is on disk, ignoring the environment. Used to show what would be cleared. */
    public Optional<String> stored() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            values.load(reader);
        } catch (IOException unreadable) {
            // An unreadable credentials file must not stop the mod from loading.
            return Optional.empty();
        }
        String stored = values.getProperty(KEY_PROPERTY, "").trim();
        return stored.isEmpty() ? Optional.empty() : Optional.of(stored);
    }

    public boolean isOverriddenByEnvironment() {
        String fromEnvironment = System.getenv(JevConfig.API_KEY_VARIABLE);
        return fromEnvironment != null && !fromEnvironment.isBlank();
    }

    /**
     * Writes the key, replacing any previous one.
     *
     * @throws IOException if the file cannot be written; the caller must surface this rather than
     *                     leaving the user believing a key was saved
     */
    public void save(String apiKey) throws IOException {
        Objects.requireNonNull(apiKey, "apiKey");
        String trimmed = apiKey.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("Refusing to save a blank key. Use clear() to remove it.");
        }
        Files.createDirectories(file.getParent());
        Properties values = new Properties();
        values.setProperty(KEY_PROPERTY, trimmed);
        // Create with restrictive permissions before writing, so the secret is never briefly
        // world readable between creation and the permission change.
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        restrictPermissions();
        try (Writer writer = Files.newBufferedWriter(file)) {
            values.store(writer, HEADER);
        }
        restrictPermissions();
    }

    /** Removes the stored key. Succeeds whether or not one was present. */
    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    public Path file() {
        return file;
    }

    private void restrictPermissions() {
        try {
            Set<PosixFilePermission> ownerOnly =
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, ownerOnly);
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows and some filesystems have no POSIX permissions. Saving still works; the file
            // simply relies on the directory's own protection.
        }
    }

    /**
     * A form safe to show on screen and in logs: enough to recognise which key is stored, never
     * enough to use it.
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "not set";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "*".repeat(trimmed.length());
        }
        return trimmed.substring(0, 6) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
