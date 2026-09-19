# Famulus handoff

Read this first. It is the continuation document: assume the previous agent is gone and cannot be
asked anything.

## What this project is

An autonomous Minecraft agent in three layers. An LLM plans, **Jev** picks the next action from an
explicitly defined set, and **Baritone** executes deterministically. Famulus is Latin for attendant.
Target is **Minecraft Java 26.2, Fabric, Java 25**. A 1.21.x port is wanted eventually; the exact
version is undecided, so **do not lower the target to solve a build or dependency problem**.

The long-term goal is "build an iron farm" from a single user sentence. The path there is
incremental: gather, gather into a chest, a cobblestone platform, a wheat farm, a sugar cane farm,
multi-stage farms, then the iron farm. Do not skip ahead.

## Status, 2026-09-19

**Working and verified in a real client:** `/famulus gather <item> <count>`, `/famulus status`,
`/famulus stop`. The gather milestone was observed collecting 0 to 32 oak logs in 25 seconds in one
attempt, declining to mine when the target was already satisfied, and cancelling cleanly on stop.
Evidence including screenshots is committed in `docs/acceptance/2026-09-19/`.

**Built and tested, but not yet wired into gameplay:** the Jev policy layer. `:jev` and the `core`
policy seam have 27 offline tests and a live smoke test verified against the real API, but **no
command consults them yet**. Nothing in Minecraft currently calls Jev.

**Not written yet:** the LLM planner, the in-game screen and schematic support. No chest deposit, crafting, building
or farming. No task graph. Gathering covers only direct block drops listed in `GatherCatalog`.

**Do not claim these work:** every recovery path. Retry, stall timeout, task timeout, death,
disconnect, dimension change and inventory-full are covered by 42 unit tests against a fake
executor, but have never run against real Baritone in a live client.

## Most recent work

Built the Jev client module and the policy seam in `core`, verified end to end against the live API.
Before that: found and verified Jev. It was absent from OpenRouter's public model catalog, which made it look
non-existent; it is real, and the contract is now documented. Before that: renamed the project from
the working name BariModel to Famulus, added LGPL-3.0, and pushed to a private GitHub repository.

## Environment

| Thing | Value |
| --- | --- |
| Minecraft Java | 26.2 |
| Mod loader | Fabric, Loader 0.19.5, Fabric API 0.160.0+26.2 |
| Baritone | 1.19.0, the **API Fabric** distribution specifically |
| Java | 25 (25.0.3 installed) |
| Gradle / Loom | 9.5.1 / 1.17.21 |
| Host | Arch Linux, OpenJDK 25.0.3, RTX 3050 Mobile, Wayland driving `DISPLAY=:0` |
| Repository | private, `TobiasLogic/famulus`, LGPL-3.0 |

Minecraft 26.x is **unobfuscated**: use ordinary `implementation` / `compileOnly` configurations and
the plain `jar` task. Do not copy Yarn mappings, `modImplementation` or `remapJar` from older guides.

There is no system Gradle or Maven. Keep the Gradle user cache inside `.cache/gradle`. There is no
Xvfb, so the client test opens a real window for about four minutes.

## Commands

```bash
./scripts/fetch-baritone.sh                            # pinned, checksum-verified dependency
GRADLE_USER_HOME=.cache/gradle ./gradlew build         # compile + 69 unit tests
GRADLE_USER_HOME=.cache/gradle ./gradlew :core:test    # engine + policy gate tests
GRADLE_USER_HOME=.cache/gradle ./gradlew :jev:test     # Jev client tests, offline
OPENROUTER_API_KEY=... ./gradlew :jev:test --rerun-tasks   # adds the live Jev smoke test
./scripts/run-client-gametest.sh                       # client acceptance, archives evidence
./scripts/run-client-gametest.sh --classify-only       # re-classify the last run, no relaunch
```

**Do not run `./gradlew :fabric:runClientGameTest` directly and believe the result.** It exits 248
even when every assertion passes. See `BUGS.md`.

## Files that matter

- `core/src/main/java/dev/famulus/core/GatherController.java` — the state machine. No I/O, no
  Minecraft types, takes observations plus a monotonic clock. Everything important lives here.
- `core/src/main/java/dev/famulus/core/` — `GatherTask` (validates its own fields), `TaskStatus`,
  `TaskResult`, `WorldSnapshot`, `GatherConfig`, `GatherExecutor` (the execution seam).
- `core/.../AgentAction.java` — the full action set. Each constant records whether an executor
  exists; only `GATHER`, `WAIT`, `VERIFY`, `RECOVER`, `COMPLETE_TASK`, `REQUEST_REPLAN` and
  `ABORT_TASK` are executable today. Keep this honest as executors are added.
- `core/.../PolicyGate.java` — turns confidence into escalation, and aborts after
  `maxConsecutiveEscalations` so a confused agent cannot loop forever. Pure logic, 15 tests.
- `jev/src/main/java/dev/famulus/jev/JevClient.java` — the only file that calls Jev. Re-validates
  the returned choice against the offered options.
- `core/src/test/java/dev/famulus/core/GatherControllerTest.java` — 42 tests, fake executor.
- `fabric/src/main/java/dev/famulus/fabric/FamulusClient.java` — entrypoint, commands, tick loop.
- `fabric/src/main/java/dev/famulus/fabric/BaritoneGatherExecutor.java` — the only file calling
  Baritone. Tracks ownership so it never cancels a process the user started.
- `fabric/src/main/java/dev/famulus/fabric/MinecraftObserver.java` — builds `WorldSnapshot`.
- `fabric/src/gametest/java/dev/famulus/fabric/GatherClientGameTest.java` — the in-client test.
- `docs/JEV.md` — the verified Jev contract. Read before writing any Jev code.
- `docs/DEPENDENCIES.md` — why each version is pinned, with sources.

## API integration

Jev is `typesafe/jev-1.13`, called at **`POST https://openrouter.ai/api/alpha/decisions`**. It is a
decisions model, not a chat model; `/chat/completions` rejects it. Median latency 0.78 s, about
$0.000026 per call. Full request and response shapes, measurements and design consequences are in
`docs/JEV.md`. The planner LLM is not chosen yet; `deepseek/deepseek-v4.1-flash` is a cheap
candidate on the same key.

### Environment variables

`OPENROUTER_API_KEY` — required once Jev or the planner is wired in. **Never commit it.** No key
exists anywhere in this repository and none may be added. `.gitignore` already excludes `.env`.

## Decisions already made

- Fabric, with a Minecraft-independent task engine so a later version adapter does not require
  rewriting control logic.
- Gather count is a **target inventory total**, including items already held. This matches Baritone's
  own quantity semantics.
- Completion is an observed inventory count. Baritone going inactive is evidence that execution
  stopped, never that it succeeded.
- Cancel only execution this system started.
- Deterministic first, then Jev, then the LLM. No endpoint was ever invented for an unidentified
  service, which is why Jev was researched rather than stubbed.
- LGPL-3.0, matching Baritone, which stays a separate and non-redistributed dependency.

## Unresolved

- Which model is the planner, and its latency and cost budget.
- Confidence thresholds for escalating from Jev to the planner. `docs/JEV.md` explains the mechanism;
  the numbers must be tuned against real runs, not guessed.
- How a task graph is represented and persisted across sessions.
- Gather scope beyond direct block drops, which needs tool and drop rules.
- The exact 1.21.x target.

## Next agent: do these in order

1. **Schematic material list.** Parse a supplied file through Baritone's own schematic system, walk
   it with `getDirect`, count block states and diff against the inventory. Deterministic; no model.
   The exact API table is in `ARCHITECTURE.md`. Verify at runtime which extensions
   `getFileExtensions()` actually reports rather than assuming `.litematic` is registered.
2. **In-game screen.** A Fabric `Screen` on a keybind: schematics listed from a folder, the material
   table, gather and build buttons, and a chat box. The user chose an in-game UI over a web UI.
3. **Wire the policy layer in** for one real decision, not the whole action set. The natural first
   one is the milestone 2 boundary: with logs gathered and a chest nearby, choose between
   `DEPOSIT_ITEM` and `COMPLETE_TASK`. Run the call off-thread; it must not touch the tick loop.
4. **Implement `DEPOSIT_ITEM`** so milestone 2 can pass a client acceptance phase like milestone 1.
5. **LLM planner** behind the chat box, emitting typed tasks validated before dispatch.
6. **Exercise a recovery path in a real client** so the retry and timeout logic stops being the most
   valuable untested part of the system.

## Surprising things worth knowing

- Jev does not appear in OpenRouter's `/api/v1/models` listing at all. 447 models, 61 providers,
  zero mentions. Decision models seem to be excluded. Query the model URL directly.
- The client game test passes completely and the gradle task still exits 248, because Baritone
  strands non-daemon threads at shutdown. This is upstream. `BUGS.md` has the thread-dump proof.
- `BariModel` was the old name. If you find that string anywhere outside `docs/acceptance/`, it is a
  leftover and should be renamed. `Baritone` legitimately contains `Bari`; do not blanket-replace it.
- Baritone's `mineByName` quantity is a **total inventory count**, not a count of blocks to mine.

## Session log

### 2026-09-19

**Attempted:** continue an empty-session handover, verify milestone 1 in-client, name the project,
publish it, and identify Jev.

**Completed:** verified 42/42 engine tests and the real-client gather milestone, including the
already-satisfied and stop paths. Traced the client test's exit 248 to Baritone's non-daemon threads
via thread dump and proved Famulus creates no threads. Wrote `scripts/run-client-gametest.sh` to
classify that failure narrowly, and rejected four doctored inputs to prove it is not vacuously green.
Filled the empty `examples/` directory. Renamed BariModel to Famulus and re-ran the full client test
to prove the rename did not break mod loading. Added LGPL-3.0 and pushed to a private repository.
Located Jev, confirmed the endpoint and measured its latency, cost and decision quality.

**Files modified:** every `.md`; all Java packages moved to `dev.famulus`; `FamulusClient` and
`FamulusConfig` renamed; `scripts/run-client-gametest.sh`, `docs/ACCEPTANCE.md`, `docs/JEV.md`,
`examples/` added; `LICENSE` and `LICENSE.GPL` added.

**Tests:** `:core:test` 42 passed twice, before and after the rename. Client acceptance run twice,
all assertions holding both times. Four negative tests against the acceptance classifier. Four live
Jev calls plus three endpoint probes.

**Problems:** Baritone's shutdown defect, documented and worked around rather than hidden. Jev's
absence from the OpenRouter catalog made it look nonexistent until the model URL was queried
directly.

**Next action:** item 1 above, the schematic material list.

### 2026-09-19, later

**Attempted:** build the Jev policy layer, and establish whether Baritone can build from a supplied
schematic.

**Completed:** added `AgentAction`, `PolicyRequest`, `PolicyDecision`, `PolicyClient`,
`PolicyException`, `PolicyGate` and `PolicyGateConfig` to `core`, and the `:jev` module with
`JevClient` and `JevConfig`. 27 offline tests, including real HTTP round trips against an embedded
JDK server. Added a live smoke test that skips without a key and verified it against the real API:
Jev returned `DEPOSIT_ITEM` at confidence 0.98 for a met goal with a chest in reach, and the gate
accepted `GATHER` at 0.89 for a 31-of-32 state. Confirmed from the pinned jar that Baritone provides
a complete schematic pipeline, and recorded the API table in `ARCHITECTURE.md`.

**Files modified:** `settings.gradle`, `build.gradle`, `fabric/build.gradle`, new `jev/` module,
seven new `core` classes, two new test classes, and the documentation set.

**Tests:** 69 offline tests pass, plus 2 live tests against the real Jev API. Full `build` green and
the mod jar contains the `dev.famulus.jev` classes.

**Problems:** Gradle evaluates subprojects alphabetically, so `:fabric` was configured before `:jev`
and could not read its source sets; fixed with `evaluationDependsOn`. This is why `:core` worked and
`:jev` did not, and it will bite again for any module sorted after `fabric`.

**Next action:** the schematic material list, then the in-game screen.
