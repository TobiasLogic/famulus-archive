# Architecture

```
User goal
  -> LLM planner        long-term reasoning, research, task graph, failure analysis, replanning
  -> Jev policy         short-term typed decision: pick the next action from a defined set
  -> Task engine        owns the task, bounds retries and deadlines, decides completion
  -> Baritone           deterministic pathfinding, movement, mining, building
  -> Minecraft
  -> Observations       inventory, world state, entities, failures, progress
  -> back to Jev, escalating to the LLM when the plan looks invalid
```

**Implementation status.** The task engine and Baritone execution are built and verified in a real
client. The Jev policy client is built and tested against the live API, but **nothing in the mod
calls it yet**: no command consults it. The LLM planner is not written and its model is unassigned.
Do not read this document as a description of working software above the task engine line.

## Layer responsibilities

| Layer | Owns | Must not |
| --- | --- | --- |
| LLM planner | Research, deciding what an iron farm even is, generating a task graph, analysing repeated failures | Run in any loop; pick individual actions; be called when a cheaper layer suffices |
| Jev policy | Choosing the next action from an explicitly enumerated set, reacting to observed state, flagging that replanning is needed | Invent actions; design farms; control movement keys; be trusted without re-validation |
| Task engine | Task ownership, retry and deadline bounds, what counts as done, cancellation | Know about Minecraft or Baritone types |
| Baritone | Pathfinding, movement, mining, gathering, building | Define success |
| Adapter | Observation, registry lookup, command registration, calling Baritone's public API | Contain decision logic |

The important line is between **deciding** and **executing**. Anything a deterministic algorithm
already solves well stays deterministic. A model is used where a judgement call is genuinely
required, and never to confirm arithmetic the code can do itself.

## Module boundaries

`core` contains no Minecraft and no Baritone types, and performs no I/O. It takes observations and a
monotonic clock and returns typed results. This is what makes it unit-testable without a game, and
what will let a 1.21.x adapter be added without touching control logic.

`fabric` owns everything version-specific: observation, registry lookup, command registration and
the Baritone adapter. It is the only module that imports Minecraft.

`jev` holds the Jev client. It depends on `core` for the policy types and imports no Minecraft. The
LLM planner will get its own module on the same footing. Neither may be called from the tick loop.

The policy seam mirrors the execution seam deliberately: `core` declares `PolicyClient` exactly as it
declares `GatherExecutor`, and an outer module supplies the implementation. That is what keeps `core`
testable without a game or a network.

## Why the network layers cannot be in the tick loop

Minecraft runs 20 ticks per second, so a tick budget is 50 ms. Jev's measured median latency is
0.78 s, roughly 15 ticks. An LLM planner is far slower. Both must run off-thread and hand a
validated decision back to the client thread at a task boundary.

`GatherController` is already built for this. It has no I/O, takes `WorldSnapshot` plus a timestamp,
and returns a `TaskResult`. A policy layer can sit above it without changing it.

## Data flow, as built today

1. `/famulus gather <item> <count>` parses and validates the request, rejecting unsupported items,
   creative mode and a busy Baritone with an actionable message.
2. `GatherController.start` records an inventory baseline and begins an attempt.
3. Every `observationIntervalTicks` ticks, `MinecraftObserver` builds a `WorldSnapshot` and
   `GatherController.tick` advances the state machine.
4. Completion is an **observed inventory count**, never a pathing event.
5. Terminal status cancels any execution the controller started, and only that execution.

## Schemas

A gather task is a target inventory total, not a number of items to mine:

```json
{"id": "<uuid>", "itemId": "minecraft:oak_log", "blockId": "minecraft:oak_log", "targetCount": 32}
```

`itemId` and `blockId` are separate because they diverge as soon as gathering covers ores or crops.
See [../examples/README.md](../examples/README.md).

Task results use the `TaskStatus` enum: `SUCCESS`, `FAILED`, `BLOCKED`, `INVALID_TARGET`,
`RESOURCE_MISSING`, `PATH_NOT_FOUND`, `TIMEOUT`, `WORLD_CHANGED`, `REPLAN_REQUIRED`, `CANCELLED`,
plus the in-flight `IDLE`, `RUNNING` and `RECOVERING`. `REPLAN_REQUIRED` is the escalation signal to
the planner and is already emitted when attempts are exhausted.

The planned action set for Jev is `GATHER`, `MINE`, `CRAFT`, `TRAVEL`, `BUILD`, `PLACE_BLOCK`,
`INTERACT`, `DEPOSIT_ITEM`, `WITHDRAW_ITEM`, `WAIT`, `VERIFY`, `RECOVER`, `COMPLETE_TASK`,
`REQUEST_REPLAN`, `ABORT_TASK`. Only gather is executable today; the rest must be rejected rather
than silently accepted until an executor exists for them.

## Building from a supplied schematic

Baritone 1.19.0 already provides the whole pipeline, confirmed by inspecting the pinned jar:

| Need | API |
| --- | --- |
| Find a parser for a file | `BaritoneAPI.getProvider().getSchematicSystem().getByFile(File)` |
| Which formats are registered | `ISchematicSystem.getFileExtensions()` |
| Parse | `ISchematicFormat.parse(InputStream)` returning `IStaticSchematic` |
| Read a block | `IStaticSchematic.getDirect(x, y, z)`, sized by `widthX/heightY/lengthZ` |
| Build it | `IBuilderProcess.build(String name, File schematic, Vec3i origin)` |
| Pause, resume, clear | `pause()`, `resume()`, `clearArea(BlockPos, BlockPos)` |
| What it can place now | `IBuilderProcess.getApproxPlaceable()` |

This makes the material list a **deterministic** computation: walk every position, count block
states, diff against the inventory. No model is involved, and per the design principles none should
be. A supplied blueprint is already a structured plan, so schematic building does not depend on the
LLM planner existing; the planner is needed to *design* a farm, not to build a given one.

Query `getFileExtensions()` at runtime and report what is supported rather than hardcoding a list.
The jar also contains a `LitematicaHelper`, and `buildOpenSchematic()` / `buildOpenLitematic(int)`
read a projection from the Litematica or Schematica mods when those are installed, which is a
different path from reading a file directly.

## Rules that must not be undone

- Completion is an observed inventory count. Baritone going inactive means execution stopped, which
  happens on success, on a missing target and on path failure alike. It is never proof of success.
- Disconnect, death, dimension or world change, and explicit stop all terminate the owned task.
- Cancel only work this system started. A user may have started their own Baritone process.
- Bound everything: retries, a per-attempt stall timeout, and an absolute task deadline that
  includes recovery time.
- Validate any model output against the typed action set before dispatch. A returned choice is
  untrusted input.
- Only direct block-drop gathering is supported. Anything needing a tool, a recipe or an entity
  interaction is rejected with an explanation until that capability genuinely exists.
