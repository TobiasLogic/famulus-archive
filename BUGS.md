# Bugs and limitations

## Baritone strands non-daemon threads at client shutdown

**Upstream, not Famulus. Confirmed 2026-09-19.** Affects the build signal only, not gathering.

`./gradlew :fabric:runClientGameTest` exits **248** after a fully successful test run. Baritone
1.19.0 leaves its worker pool running when the client stops, the JVM therefore cannot exit, and
Minecraft 26.2's `ClientShutdownWatchdog` force-kills the process. In the recorded run the test
finished at 16:14:39 and the watchdog fired at 16:14:55.

The thread dump in `docs/acceptance/2026-09-19/shutdown-crash-report.txt` is conclusive:

- `DestroyJavaVM` is already `RUNNABLE`, so `main` had returned and shutdown was complete
- every thread is marked `daemon` except `DestroyJavaVM` and `pool-4-thread-1` through `-4`
- `pool-4-thread-1` is parked in `knot//baritone.o$a.run`, `pool-4-thread-2` in `knot//baritone.o.b`

Famulus creates no threads and no executors of its own; its only `Executor` is the `GatherExecutor`
interface, which is a plain synchronous seam. Nothing in this project can release another mod's
thread pool through the public Baritone API, so there is no fix to make here.

Run `./scripts/run-client-gametest.sh` rather than the gradle task directly. It reports the real
acceptance outcome and accepts a non-zero exit only when it matches this exact signature. Do not
widen that exemption to make an unrelated failure go quiet.

A desktop player never sees this. It costs a few seconds on quit, and the launcher reaps the process.

## Scope boundaries

These are deliberately unbuilt, not broken:

- No Jev policy and no LLM planner. Both seams are typed and unused; no endpoint has been invented.
- No crafting, chest deposit, building or farming.
- Gathering covers only the direct block drops in `GatherCatalog`. Anything needing a specific tool,
  a recipe or an entity interaction is rejected with an explanation instead of half-attempted.
- Minecraft 26.2 only. No 1.21.x adapter exists and the target version is still undecided.

## Untested paths

The task engine's recovery behavior is covered by 42 unit tests against a fake executor, but only
the success, already-satisfied and user-stop paths have been observed in a real client. Retry,
stall timeout, task timeout, death, disconnect, dimension change and inventory-full have never run
against real Baritone. They are plausible, not verified; treat them accordingly.

## Environment notes

The dev client logs a missing `libflite.so` narrator library, a Realms authorization failure and a
401 on `/player/certificates`. All three are expected for an offline development launch and none
affect the test.
