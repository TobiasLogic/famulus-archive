# Architecture

`User goal -> Planner -> structured tasks -> Policy -> Executor -> Minecraft -> observations`

The initial planner accepts one structured gather task, and a deterministic policy handles it. Future Jev and LLM integrations must preserve the explicit task/action/observation boundary. Minecraft operations run on the client thread; network reasoning must eventually run off-thread and submit validated proposals.

`core` contains no Minecraft or Baritone dependencies. `fabric` owns version-specific observation, registry lookup, command registration and the public Baritone API adapter. Baritone handles movement, pathfinding and mining; the task engine verifies inventory and enforces time/retry bounds.

Only gathering direct block-drop items is initially supported. Arbitrary items may require crafting, entity interactions, tools or recipes and must be rejected with an actionable explanation until those capabilities exist.

Do not mark completion based on a pathing event alone. Completion is an observed inventory count. Disconnect, death, dimension/world change and explicit stop must terminate the owned task.
