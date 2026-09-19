package dev.famulus.fabric;

import baritone.api.BaritoneAPI;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a blueprint file into a material list, using Baritone's own schematic parsers.
 *
 * <p>Deliberately deterministic. Counting blocks is arithmetic, and the design principles say a
 * model must not be used where an algorithm already answers the question.
 *
 * <p>Supported file types are whatever Baritone registers at runtime, which is why
 * {@link #supportedExtensions()} asks rather than hardcoding a list. Baritone ships a
 * {@code LitematicaHelper}, but whether {@code .litematic} is in the file registry or only reachable
 * through the Litematica mod is a runtime fact, not an assumption to bake in.
 */
public final class SchematicAnalyzer {
    /** Refuse absurd blueprints rather than freezing the client thread counting them. */
    public static final long MAX_VOLUME = 8_000_000L;

    private SchematicAnalyzer() {}

    /** {@code config/famulus/schematics}, created on demand. */
    public static Path schematicDirectory() throws IOException {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("famulus").resolve("schematics");
        Files.createDirectories(directory);
        return directory;
    }

    /** File extensions Baritone can actually parse in this installation, without leading dots. */
    public static List<String> supportedExtensions() {
        try {
            return List.copyOf(BaritoneAPI.getProvider().getSchematicSystem().getFileExtensions());
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    /** Blueprint files in the schematic directory that Baritone claims it can parse. */
    public static List<Path> listSchematics() throws IOException {
        List<String> extensions = supportedExtensions();
        try (var entries = Files.list(schematicDirectory())) {
            List<Path> found = new ArrayList<>(entries.filter(Files::isRegularFile)
                    .filter(path -> matches(path, extensions))
                    .toList());
            found.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return found;
        }
    }

    private static boolean matches(Path path, List<String> extensions) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return extensions.stream().anyMatch(extension -> name.endsWith("." + extension.toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Parses a blueprint and counts the items it consumes.
     *
     * @throws IOException with an actionable message when the file is missing, unreadable, of an
     *                     unregistered type, or too large to count
     */
    public static SchematicSummary analyze(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("No such schematic file: " + file);
        }
        Optional<ISchematicFormat> format;
        try {
            format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(file.toFile());
        } catch (RuntimeException failure) {
            throw new IOException("Baritone could not inspect " + file.getFileName() + ": " + failure, failure);
        }
        if (format.isEmpty()) {
            throw new IOException("Baritone has no parser for " + file.getFileName()
                    + ". Supported here: " + String.join(", ", supportedExtensions()));
        }

        IStaticSchematic schematic;
        try (InputStream in = Files.newInputStream(file)) {
            schematic = format.get().parse(in);
        } catch (RuntimeException malformed) {
            throw new IOException("Could not parse " + file.getFileName() + ": " + malformed, malformed);
        }
        if (schematic == null) {
            throw new IOException("Baritone returned no schematic for " + file.getFileName());
        }
        return summarise(file.getFileName().toString(), schematic);
    }

    /** Walks every position and counts the item each block state would consume. */
    public static SchematicSummary summarise(String name, IStaticSchematic schematic) throws IOException {
        int widthX = schematic.widthX();
        int heightY = schematic.heightY();
        int lengthZ = schematic.lengthZ();
        long volume = (long) widthX * heightY * lengthZ;
        if (volume > MAX_VOLUME) {
            throw new IOException(name + " is " + widthX + "x" + heightY + "x" + lengthZ
                    + " (" + volume + " positions), beyond the " + MAX_VOLUME + " limit");
        }

        // Sorted so the same blueprint always yields the same order on screen and in logs.
        Map<String, Integer> counts = new TreeMap<>();
        for (int y = 0; y < heightY; y++) {
            for (int x = 0; x < widthX; x++) {
                for (int z = 0; z < lengthZ; z++) {
                    BlockState state;
                    try {
                        state = schematic.getDirect(x, y, z);
                    } catch (RuntimeException outOfRange) {
                        continue;
                    }
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    Item item = state.getBlock().asItem();
                    if (item == Items.AIR) {
                        // Fire, water, piston heads and similar have no placeable item of their own.
                        continue;
                    }
                    counts.merge(BuiltInRegistries.ITEM.getKey(item).toString(), 1, Integer::sum);
                }
            }
        }
        return new SchematicSummary(name, widthX, heightY, lengthZ, new LinkedHashMap<>(counts));
    }
}
