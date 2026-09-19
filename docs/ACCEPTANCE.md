# Client acceptance

This is the only document that may state whether Famulus works **in Minecraft**. A green build
is not evidence of in-game behavior; see `TESTING.md` for that distinction.

## Running it

```bash
./scripts/run-client-gametest.sh
```

This launches a real Minecraft 26.2 client, creates a fresh flat survival world, drives the shipped
`/famulus` commands through simulated keyboard input, and classifies the outcome. A game window opens
and moves on its own for roughly three minutes. There is no headless mode: the client needs a
display, and no Xvfb was available on the machine this was recorded on.

Add `--classify-only` to re-classify the last run without launching Minecraft again.

The script archives screenshots, a filtered transcript and any crash report into
`docs/acceptance/<date>/`, because Loom clears the run directory on every launch.

## What the test asserts

The fixture teleports the player to `0.5 -60 0.5`, clears the inventory, grants a diamond axe and
places exactly 32 oak logs with `fill 2 -60 2 9 -60 5`.

| Phase | Assertion |
| --- | --- |
| Gather | `/famulus gather minecraft:oak_log 32` reaches exactly 32 logs and releases Baritone. |
| Already satisfied | Re-requesting 32 does not mine, does not change the inventory, and leaves a sentinel log at `2 -60 0` standing, observed for 40 ticks. |
| Stop | With 64 more logs placed, `/famulus gather minecraft:oak_log 96` starts mining, then `/famulus stop` cancels it and Baritone stays inactive for 20 ticks. |

The middle phase is the one that matters most: it is the difference between a gather target and a
mining loop. The sentinel proves the mod stopped because the inventory goal was met, not because it
ran out of blocks.

## Recorded result, 2026-09-19

**All assertions held.** Evidence in `docs/acceptance/2026-09-19/`. This run was recorded after the
rename from the working name BariModel, so it also proves the new mod id, entrypoint and `/famulus`
command still load and register correctly.

| Observation | Value |
| --- | --- |
| Gather | 0 to 32 oak logs in 25 seconds (16:29:57 to 16:30:22), one attempt, no retries |
| Terminal result | `{"status":"SUCCESS","message":"Inventory target reached","currentCount":32,"targetCount":32,"attempts":1}` |
| Already satisfied | `SUCCESS ... attempts 0`, Baritone never started, sentinel intact |
| Stop | `CANCELLED` at 32 of 96 after 1 attempt, Baritone inactive afterwards |
| In-game assertion failures | 0 |
| Screenshots | 5 of 5 captured |

Host: Arch Linux 7.1.3-arch1-3, OpenJDK 25.0.3, GeForce RTX 3050 Mobile, Wayland session driving
`DISPLAY=:0`. Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Baritone 1.19.0, Loom 1.17.21.

`0002_famulus-inventory-32-oak-logs.png` shows the open inventory holding a single stack of 32
oak logs, with the Baritone and Famulus chat lines visible in the same frame.

## Why the gradle task is still red

`./gradlew :fabric:runClientGameTest` exits **248** even though every assertion passed. The cause is
upstream and is recorded in `BUGS.md`: Baritone 1.19.0 does not shut down its non-daemon thread
pool, the JVM cannot exit, and Minecraft's `ClientShutdownWatchdog` kills the process 16 seconds
after the test has already finished.

`scripts/run-client-gametest.sh` exists so this does not turn into a permanently ignored red build.
It reports PASS only when that failure is exactly the known signature:

- the crash description is `Client shutdown from post-main`
- the thread dump contains a frame in `knot//baritone.`
- the only non-daemon threads left are `DestroyJavaVM` and the `pool-N-thread-N` workers

Any other non-zero exit, any surviving non-daemon thread outside that pool, any missing screenshot
and any `AssertionError` all fail the run. Those four rejection paths were each exercised against
doctored copies of this run's log and crash report before the script was accepted.
