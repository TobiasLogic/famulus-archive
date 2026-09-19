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

## Baritone leaves its scaffolding behind

**Upstream, measured 2026-09-19.** Affects any structure where stray blocks matter.

Baritone *can* build off the ground, contrary to its reputation: a 3x3 platform five blocks up was
built 9/9. It gets there by pillaring up, which is ordinary movement behaviour, and the pillar
incidentally supplies the face needed to place the first block. **It then leaves the pillar in
place**; the probe measured 5 stray blocks under a finished floating platform.

Cosmetic for a solid build, fatal for a flying machine, a redstone farm or any mob farm whose
spawning platform must be exactly as drawn. Baritone 1.19.0 has no scaffolding setting at all.

Full measurement, the cases still untested, and a proposed fix that needs no Baritone fork are in
`docs/SCAFFOLDING.md`. Reproduce with:

```bash
FAMULUS_PROBE_SCAFFOLDING=1 GRADLE_USER_HOME=.cache/gradle ./gradlew :fabric:runClientGameTest
```

## Baritone does not surrender when a resource is absent

**Upstream, measured 2026-09-19.** Makes every unobtainable gather slow.

With every oak log removed from the world, `mineByName` kept its process **active**. It emitted one
"Unable to find any path" message and carried on regardless, so `isActive()` never went false and the
inactive-pickup grace never fired. The only thing that caught it was the engine's 60 second stall
timeout, three times over: the task took **2m13s** to fail instead of seconds.

Two consequences, both already acted on:

- The stall timeout is not a nicety, it is the sole safety net for an absent resource. Do not raise
  it casually, and consider lowering it for short tasks.
- **Retrying in place is pointless when the resource simply is not there.** This is why `EXPLORE`
  exists as a policy option: the only useful recovery is to go somewhere else. See
  `BaritoneExplorer` and `docs/JEV.md`.

Reproduced by the policy phase of the client test, which clears the logs and asks for 64.

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
