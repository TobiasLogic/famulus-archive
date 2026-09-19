# TODO

Milestones follow the project brief. Do not skip ahead; the architecture is meant to grow into the
iron farm without a rewrite, and that only holds if each layer is reliable before the next is added.

## Done

- **Milestone 1: collect 32 oak logs.** Verified in a real client, evidence in `docs/acceptance/`.
- Jev identified, its API contract verified and measured, documented in `docs/JEV.md`.

## Next

1. **Jev client module.** No Minecraft imports, a fake for tests, and hard validation of the returned
   choice against the action enum before dispatch. Build against `docs/JEV.md`.
2. **One real Jev decision** above the existing engine, off-thread: at the milestone 2 boundary,
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

## Ongoing

- Extend `GatherCatalog` only together with the tool and drop rules a wider item set requires.
- Decide the exact 1.21.x target before writing a second adapter.
- Tune Jev confidence thresholds against real runs rather than fixing them by guess.
