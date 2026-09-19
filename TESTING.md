# Testing

Three layers, in increasing order of what they prove. Keep them distinct: the first two say nothing
about whether the mod works in Minecraft.

## 1. Task engine unit tests

```bash
GRADLE_USER_HOME=.cache/gradle ./gradlew :core:test
```

**97 tests, all passing as of 2026-09-19**, across `:core` and `:jev`. `core` has no Minecraft or Baritone types, so the state
machine runs against a fake executor and hand-written observations. Coverage includes existing
inventory, progress, stalled and failed execution, retry exhaustion, the absolute task deadline,
cancellation, cancellation *failure*, disconnect, death, dimension change, pickup grace after the
executor goes inactive, and clock anomalies including a backwards clock and an overflowing jump.

`:jev` tests run the real HTTP path against an embedded JDK `HttpServer`, so request construction
and response parsing are covered without network access or spending anything. The canned bodies are
copied from a genuine Jev reply recorded in `docs/JEV.md`.

JUnit XML lands in `<module>/build/test-results/test/`.

## 1b. Live Jev smoke test

```bash
OPENROUTER_API_KEY=... ./gradlew :jev:test --rerun-tasks
```

`JevLiveSmokeTest` calls the real API and is **skipped** unless that variable is set, so ordinary
builds stay offline and deterministic. It exists because the offline tests only prove the client
agrees with responses written by hand; if the live contract changes, only this test notices. A call
costs about $0.000026.

Last run 2026-09-19, both tests passed: Jev returned `DEPOSIT_ITEM` at confidence 0.98 for a state
whose goal was met with a chest in reach, and `PolicyGate` accepted `GATHER` at 0.89 for a 31-of-32
state. It asserts the contract rather than an exact answer, since the model is free to disagree.

## 2. Compilation against the real dependencies

```bash
GRADLE_USER_HOME=.cache/gradle ./gradlew build
```

Compiles `core`, the Fabric adapter and the client game test against pinned Minecraft 26.2, Fabric
API 0.160.0+26.2 and Baritone 1.19.0, and verifies the Baritone jar checksum first. This catches
API drift and nothing else. A green build here has never meant the mod gathers anything.

## 3. Client acceptance

```bash
./scripts/run-client-gametest.sh
```

The only layer that demonstrates in-game behavior. It drives the real `/famulus` commands in a real
client and checks the inventory, not Baritone's own reported state.

**Recorded 2026-09-19: 18 checks held** with a key present, 17 without. Full procedure, evidence and host details are in
[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

Use the script, not `./gradlew :fabric:runClientGameTest`. The gradle task exits non-zero even on a
completely successful run because of the Baritone shutdown defect in `BUGS.md`; the script separates
that known teardown failure from a genuine one and fails on everything else.

## What the client test now covers

Four phases, all asserted against the inventory rather than against what Baritone reports:

1. Gather to an observed total of 32, in one attempt.
2. An already satisfied request that must not mine, verified against a sentinel log left standing.
3. `/famulus stop` cancelling an in-progress task.
4. **A two-task plan.** `/famulus queue minecraft:oak_log=8, minecraft:dirt=8` is sent once, and the
   agent must reach the second task by itself. That unprompted advance is the autonomy claim, so it
   is asserted directly: the log line `running gather 8 minecraft:dirt` only appears if the agent
   decided to move on without being told.

5. **The policy layer, for real.** Only when `OPENROUTER_API_KEY` is set. Every log is removed and
   64 are requested, so the task genuinely cannot succeed. The agent must exhaust its attempts and
   consult Jev in game. Recorded 2026-09-19: `policy chose REQUEST_REPLAN (replan urgency 0.84)`.
   Ordinary runs skip this and stay offline and free.
6. **The panel.** Each tab is opened and photographed. The screenshots are the test: a layout that
   silently breaks shows up there and nowhere else.

## Opt-in probes

Diagnostics rather than acceptance tests, each gated on an environment variable because they cost
minutes:

- `FAMULUS_PROBE_SCAFFOLDING=1` - can Baritone build off the ground? See `docs/SCAFFOLDING.md`.
- `FAMULUS_PROBE_EXPLORE=1` - does exploring make a distant resource reachable? A superflat world
  has no sand at any distance, so the probe places some 260 blocks away and first proves it is out
  of reach before exploring, otherwise the result would mean nothing.

## What is still unverified

Several of the engine's recovery paths are unit-tested but have never been exercised against real
Baritone.
Nothing has been tested on a multiplayer server, in the Nether or the End, with a non-default
`observationIntervalTicks`, or with a Baritone version other than the pinned 1.19.0.
