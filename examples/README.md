# Example task input

`gather-task.json` records the field names and value shapes of `dev.famulus.core.GatherTask`,
the structured task the client builds when a user runs `/famulus gather minecraft:oak_log 32`.

**No code parses this file.** There is no planner, no task file loader and no network input yet.
The file exists so that the future planner boundary described in `ARCHITECTURE.md` has a written
target shape instead of an invented one, and so that the task record can be reviewed without
reading Java.

Field meanings, as enforced by the `GatherTask` constructor:

| Field | Rule |
| --- | --- |
| `id` | Non-blank. The client uses a random UUID; it correlates log lines for one task. |
| `itemId` | Namespaced identifier of the item counted in the inventory. |
| `blockId` | Namespaced identifier of the block Baritone mines. Equal to `itemId` for every currently supported target. |
| `targetCount` | Positive. A **total** inventory count, not a number of additional items to collect. |

`itemId` and `blockId` are separate fields because they diverge as soon as gathering covers ores,
crops or anything whose drop differs from the mined block. `GatherCatalog` currently accepts only
targets where they are identical, so the prototype never has to choose a tool or a drop rule.

Adding a file here does not widen the supported set. `GatherCatalog.SUPPORTED` is the authority on
what `/famulus gather` accepts.
