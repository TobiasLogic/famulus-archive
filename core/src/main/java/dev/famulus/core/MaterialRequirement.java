package dev.famulus.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How much of one item a build needs, against how much is actually held.
 *
 * @param itemId namespaced item identifier
 * @param needed total required by the blueprint, always positive
 * @param have   currently held, never negative
 */
public record MaterialRequirement(String itemId, int needed, int have) {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public MaterialRequirement {
        Objects.requireNonNull(itemId, "itemId");
        if (!RESOURCE_ID.matcher(itemId).matches()) {
            throw new IllegalArgumentException("Item must be a namespaced identifier: " + itemId);
        }
        if (needed < 1) {
            throw new IllegalArgumentException("A requirement of zero should not exist: " + itemId);
        }
        if (have < 0) {
            throw new IllegalArgumentException("Held count must not be negative: " + itemId);
        }
    }

    /** Zero once the requirement is met; surplus is never reported as a negative shortfall. */
    public int shortfall() {
        return Math.max(0, needed - have);
    }

    public boolean satisfied() {
        return have >= needed;
    }

    /** Full stacks of 64 needed to cover the shortfall, rounded up. Useful for a readable summary. */
    public int shortfallStacks() {
        return (shortfall() + 63) / 64;
    }
}
