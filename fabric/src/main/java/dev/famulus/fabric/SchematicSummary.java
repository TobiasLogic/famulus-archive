package dev.famulus.fabric;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed blueprint reduced to what the rest of the system needs: its size and how many of each
 * item it consumes.
 *
 * @param name        display name, normally the file name
 * @param widthX      size along X
 * @param heightY     size along Y
 * @param lengthZ     size along Z
 * @param itemCounts  namespaced item id to the number required, air and item-less blocks excluded
 */
public record SchematicSummary(String name, int widthX, int heightY, int lengthZ,
                               Map<String, Integer> itemCounts) {
    public SchematicSummary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(itemCounts, "itemCounts");
        if (widthX < 1 || heightY < 1 || lengthZ < 1) {
            throw new IllegalArgumentException("Schematic dimensions must be positive");
        }
        itemCounts = Collections.unmodifiableMap(new LinkedHashMap<>(itemCounts));
    }

    /** Total placeable blocks, which is not the same as the bounding volume. */
    public int totalBlocks() {
        return itemCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public long volume() {
        return (long) widthX * heightY * lengthZ;
    }

    public String dimensions() {
        return widthX + "x" + heightY + "x" + lengthZ;
    }
}
