package dev.famulus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a blueprint needs, diffed against what the player holds.
 *
 * <p>This is a deterministic computation over a parsed schematic and an inventory count. No model is
 * involved and none should be: asking a language model to add up block counts would be slower, more
 * expensive and less correct than doing it here.
 *
 * <p>Ordering is stable, largest shortfall first then by item id, so the same blueprint always
 * produces the same gather order and the same screen.
 */
public record MaterialList(List<MaterialRequirement> requirements) {
    private static final Comparator<MaterialRequirement> BY_SHORTFALL =
            Comparator.comparingInt(MaterialRequirement::shortfall).reversed()
                    .thenComparing(MaterialRequirement::itemId);

    public MaterialList {
        Objects.requireNonNull(requirements, "requirements");
        List<MaterialRequirement> sorted = new ArrayList<>(requirements);
        sorted.sort(BY_SHORTFALL);
        requirements = Collections.unmodifiableList(sorted);
    }

    /**
     * @param needed block counts from the blueprint; entries of zero or less are ignored
     * @param have   inventory counts; missing items are treated as zero
     */
    public static MaterialList of(Map<String, Integer> needed, Map<String, Integer> have) {
        Objects.requireNonNull(needed, "needed");
        Objects.requireNonNull(have, "have");
        List<MaterialRequirement> requirements = new ArrayList<>(needed.size());
        needed.forEach((itemId, count) -> {
            if (count != null && count > 0) {
                requirements.add(new MaterialRequirement(
                        itemId, count, Math.max(0, have.getOrDefault(itemId, 0))));
            }
        });
        return new MaterialList(requirements);
    }

    /** Only the items still short, in gather order. */
    public List<MaterialRequirement> shortfalls() {
        return requirements.stream().filter(requirement -> !requirement.satisfied()).toList();
    }

    public boolean isSatisfied() {
        return shortfalls().isEmpty();
    }

    public int distinctItems() {
        return requirements.size();
    }

    public int totalNeeded() {
        return requirements.stream().mapToInt(MaterialRequirement::needed).sum();
    }

    public int totalShortfall() {
        return requirements.stream().mapToInt(MaterialRequirement::shortfall).sum();
    }

    /**
     * Shortfalls this system can currently gather, in order. The rest need crafting, a specific tool
     * or an entity interaction, so they are reported separately rather than attempted and failed.
     */
    public List<MaterialRequirement> gatherable(java.util.function.Predicate<String> supported) {
        return shortfalls().stream().filter(r -> supported.test(r.itemId())).toList();
    }

    /** Shortfalls with no gather path yet. A build cannot start while this is non-empty. */
    public List<MaterialRequirement> unobtainable(java.util.function.Predicate<String> supported) {
        return shortfalls().stream().filter(r -> !supported.test(r.itemId())).toList();
    }
}
