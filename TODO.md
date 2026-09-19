# TODO

Done: deterministic gather with bounded retries and observed completion; compilation against pinned
Minecraft 26.2, Fabric and Baritone; `/famulus gather minecraft:oak_log 32` verified in a real client
with preserved evidence.

1. Exercise at least one recovery path in a real client. Retry, stall timeout, task timeout, death,
   disconnect, dimension change and inventory-full are unit-tested only, so the engine's most
   valuable behavior is its least verified.
2. Put the project under version control. See the environment warning in HANDOFF.md.
3. Identify whether Jev is a real, reachable model or service before building anything against it.
   Integrate only after deterministic acceptance, and never invent an endpoint.
4. Integrate an LLM planner behind a validated structured plan boundary. `examples/gather-task.json`
   holds the task shape it must produce.
5. Add chest deposit, platform, wheat farm and sugar cane farm incrementally, each with its own
   client acceptance phase.
6. Extend `GatherCatalog` only together with the tool and drop rules a wider item set requires.
7. Decide the exact 1.21.x target before implementing another adapter.
