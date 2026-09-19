package dev.famulus.planner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.famulus.core.PlannedTask;
import dev.famulus.core.PlannerException;
import dev.famulus.core.TaskPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a model's reply into a validated {@link TaskPlan}, or refuses.
 *
 * <p>This is the security boundary of the planning layer. Everything arriving here is untrusted
 * text produced by a language model: it may be malformed, may name blocks that do not exist, may ask
 * for a million of something, and may wrap its answer in prose or markdown. A plan is either fully
 * valid or rejected with a reason. There is no partial acceptance, because a half-understood plan
 * executed in someone's world is worse than no plan.
 */
public final class PlanParser {
    /** Enough for a large blueprint's material list, small enough that nothing runs away. */
    public static final int MAX_TASKS = 40;
    /** One inventory. Beyond this there is nowhere to put the items; see docs/CONTAINERS.md. */
    public static final int MAX_COUNT = 2304;

    private PlanParser() {}

    /**
     * @param raw        the model's reply, possibly with prose or code fences around the JSON
     * @param gatherable namespaced items the gather layer can actually obtain
     * @throws PlannerException with a message fit to show the user
     */
    public static TaskPlan parse(String raw, Set<String> gatherable) throws PlannerException {
        JsonObject root = readObject(raw);

        String goal = string(root, "goal");
        if (goal == null || goal.isBlank()) {
            throw new PlannerException("The plan has no goal.");
        }
        if (goal.length() > 200) {
            goal = goal.substring(0, 200);
        }

        JsonElement tasksElement = root.get("tasks");
        if (tasksElement == null || !tasksElement.isJsonArray()) {
            throw new PlannerException("The plan has no task list.");
        }
        JsonArray array = tasksElement.getAsJsonArray();
        if (array.isEmpty()) {
            throw new PlannerException("The plan contains no tasks.");
        }
        if (array.size() > MAX_TASKS) {
            throw new PlannerException("The plan has " + array.size() + " tasks, more than the "
                    + MAX_TASKS + " allowed.");
        }

        List<PlannedTask> tasks = new ArrayList<>(array.size());
        List<String> rejected = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                throw new PlannerException("Task " + (i + 1) + " is not an object.");
            }
            tasks.add(readTask(array.get(i).getAsJsonObject(), i, gatherable, rejected));
        }
        if (!rejected.isEmpty()) {
            throw new PlannerException("The plan needs items this agent cannot obtain: "
                    + String.join(", ", rejected));
        }

        try {
            TaskPlan plan = new TaskPlan(goal, tasks);
            if (!plan.isExecutable()) {
                throw new PlannerException("The plan needs actions that are not implemented yet: "
                        + plan.unexecutable().stream().map(PlannedTask::describe).toList());
            }
            return plan;
        } catch (IllegalArgumentException invalid) {
            throw new PlannerException("The plan is not valid: " + invalid.getMessage(), invalid);
        }
    }

    private static PlannedTask readTask(JsonObject task, int index, Set<String> gatherable,
                                        List<String> rejected) throws PlannerException {
        String id = string(task, "id");
        if (id == null || id.isBlank()) {
            // Models often omit ids. That is not worth failing a whole plan over.
            id = "t" + (index + 1);
        }
        String type = string(task, "type");
        if (type == null) {
            throw new PlannerException("Task " + (index + 1) + " has no type.");
        }

        return switch (type.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "gather", "mine", "collect" -> {
                String item = requireItem(task, index);
                int count = requireCount(task, index);
                if (!gatherable.contains(item)) {
                    rejected.add(item);
                    // Recorded rather than thrown, so the user is told about every unobtainable
                    // item at once instead of discovering them one failed plan at a time.
                    yield new PlannedTask.Gather(id, item, count);
                }
                yield new PlannedTask.Gather(id, item, count);
            }
            case "deposit", "deposit_item" ->
                    new PlannedTask.Deposit(id, requireItem(task, index), requireCount(task, index));
            case "build" -> {
                String blueprint = string(task, "blueprint");
                if (blueprint == null || blueprint.isBlank()) {
                    throw new PlannerException("Task " + (index + 1) + " is a build with no blueprint.");
                }
                yield new PlannedTask.Build(id, blueprint,
                        integer(task, "x", 0), integer(task, "y", 0), integer(task, "z", 0));
            }
            default -> throw new PlannerException("Task " + (index + 1)
                    + " has an unknown type: " + type);
        };
    }

    private static String requireItem(JsonObject task, int index) throws PlannerException {
        String item = string(task, "item");
        if (item == null || item.isBlank()) {
            throw new PlannerException("Task " + (index + 1) + " names no item.");
        }
        item = item.trim().toLowerCase(java.util.Locale.ROOT);
        // Models frequently drop the namespace. Assuming vanilla is safe and saves a rejection.
        if (!item.contains(":")) {
            item = "minecraft:" + item;
        }
        if (!PlannedTask.RESOURCE_ID.matcher(item).matches()) {
            throw new PlannerException("Task " + (index + 1) + " has an invalid item id: " + item);
        }
        return item;
    }

    private static int requireCount(JsonObject task, int index) throws PlannerException {
        int count = integer(task, "count", -1);
        if (count < 1) {
            throw new PlannerException("Task " + (index + 1) + " has no usable count.");
        }
        if (count > MAX_COUNT) {
            throw new PlannerException("Task " + (index + 1) + " asks for " + count
                    + ", more than an inventory holds (" + MAX_COUNT + ").");
        }
        return count;
    }

    /**
     * Finds the JSON object in a reply. Models wrap answers in code fences and commentary even when
     * asked not to, so the outermost braces are located rather than trusting the whole string.
     */
    private static JsonObject readObject(String raw) throws PlannerException {
        if (raw == null || raw.isBlank()) {
            throw new PlannerException("The planner returned nothing.");
        }
        String text = raw.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new PlannerException("The planner did not return a plan: " + abbreviate(text));
        }
        String json = text.substring(start, end + 1);
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new PlannerException("The planner's reply was not a JSON object.");
            }
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException malformed) {
            throw new PlannerException("The planner's reply was not valid JSON: "
                    + abbreviate(json), malformed);
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject object, String field, int fallback) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            // Models write counts as "32" and as 32.0 about as often as they write 32.
            return (int) Math.round(value.getAsDouble());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private static String abbreviate(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}
