# TODO

Milestones follow the project brief. Do not skip ahead; the architecture is meant to grow into the
iron farm without a rewrite, and that only holds if each layer is reliable before the next is added.

## Done

- **Milestone 1: collect 32 oak logs.** Verified in a real client, evidence in `docs/acceptance/`.
- Jev identified, its API contract verified and measured, documented in `docs/JEV.md`.
- **Agent loop.** `PlanRunner` plus `FamulusAgent`: multi-task plans, policy consulted off-thread on
  failure, bounded retries. `/famulus queue`, `/famulus materials`, `/famulus collect`.
- **Schematic material list.** `SchematicAnalyzer` parses through Baritone's registry;
  `MaterialList` diffs against inventory. 13 tests. Not yet reachable from any command or screen.
- **In-game panel** with Agent, Build and Settings tabs, and in-game API key entry.
- **Policy verified in a live client.** Jev consulted in game on a task that could not succeed.
- **`EXPLORE` action** backed by Baritone's explore process, for when a resource is not nearby.
- **Scaffolding measured.** Baritone builds off the ground but leaves its pillar; see
  `docs/SCAFFOLDING.md`.
- **Jev client module.** `:jev` plus the `core` policy seam, 27 offline tests and a live smoke test
  verified against the real API.

## Next

1. **Chat tab and the LLM planner.** The panel has room for it; the planner is the missing piece.
2. **Old item 1, now done: in-game screen.** A Fabric `Screen` on a keybind listing schematics from a folder, the material
   table, gather and build buttons, and a chat box driving the planner.
3. **One real Jev decision** above the existing engine, off-thread: at the milestone 2 boundary,
   choose between `DEPOSIT_ITEM` and `COMPLETE_TASK`. Not the whole action set at once.
3. **Milestone 2: collect 32 oak logs and place them in a chest.** Needs `DEPOSIT_ITEM` and a client
   acceptance phase matching milestone 1's rigour.
4. **Exercise a recovery path in a real client.** Retry, stall timeout, task timeout, death,
   disconnect, dimension change and inventory-full are unit-tested only. This is the largest gap
   between what is tested and what is claimed.
5. **LLM planner** behind a validated structured plan boundary. `examples/gather-task.json` holds the
   task shape it must produce. Choose the model and record its latency and cost budget.
6. **Milestone 3: build a 5x5 cobblestone platform.** First build task, so the first `BUILD` executor.
7. **Milestone 4: basic wheat farm.** Introduces crops, tools and a real task graph.
8. **Milestone 5: basic sugar cane farm.**
9. **Milestone 6: multi-stage farms.**
10. **Milestone 7: autonomously plan and construct an iron farm.**

## Also queued

- **Scaffolding cleanup.** Snapshot the build region, build, then remove blocks that are present but
  not in the blueprint. Expressible in Baritone's own API because a wanted-air position is treated as
  something to break. Needed before any flying machine or redstone farm is worth attempting.
- **Harder scaffolding cases.** The probe only covered an easy one. Test a position no pillar can
  reach, a long unsupported horizontal span, and whether `skipFailedLayers` hides a partial build.

## Ongoing

- Extend `GatherCatalog` only together with the tool and drop rules a wider item set requires.
- Decide the exact 1.21.x target before writing a second adapter.
- Tune Jev confidence thresholds against real runs rather than fixing them by guess.
