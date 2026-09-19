# Testing

Three layers, in increasing order of what they prove. Keep them distinct: the first two say nothing
about whether the mod works in Minecraft.

## 1. Task engine unit tests

```bash
GRADLE_USER_HOME=.cache/gradle ./gradlew :core:test
```

**42 tests, all passing as of 2026-09-19.** `core` has no Minecraft or Baritone types, so the state
machine runs against a fake executor and hand-written observations. Coverage includes existing
inventory, progress, stalled and failed execution, retry exhaustion, the absolute task deadline,
cancellation, cancellation *failure*, disconnect, death, dimension change, pickup grace after the
executor goes inactive, and clock anomalies including a backwards clock and an overflowing jump.

JUnit XML lands in `core/build/test-results/test/`.

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

**Recorded 2026-09-19: all assertions held.** Full procedure, evidence and host details are in
[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

Use the script, not `./gradlew :fabric:runClientGameTest`. The gradle task exits non-zero even on a
completely successful run because of the Baritone shutdown defect in `BUGS.md`; the script separates
that known teardown failure from a genuine one and fails on everything else.

## What is still unverified

The engine's recovery paths are unit-tested but have never been exercised against real Baritone.
Nothing has been tested on a multiplayer server, in the Nether or the End, with a non-default
`observationIntervalTicks`, or with a Baritone version other than the pinned 1.19.0.
