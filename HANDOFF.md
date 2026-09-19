# Famulus handoff

## Status — 2026-09-19, milestone 1 verified in a real client

`/famulus gather minecraft:oak_log 32` works in Minecraft 26.2. It was observed collecting 0 to 32 oak
logs in 25 seconds in one attempt, refusing to mine when the target was already satisfied, and
cancelling cleanly on `/famulus stop`. Evidence, including screenshots, is in
`docs/acceptance/2026-09-19/` and the procedure is in `docs/ACCEPTANCE.md`.

This is a gather prototype. There is no planner, no Jev, no crafting and no farm logic.

## Non-negotiable scope

- All project work and downloaded build tools belong under `/home/htfi/Documents/CODE/openchat/empty/Famulus`.
- Target Minecraft **26.2**. Exact future 1.21.x support remains undecided; do not silently change target.
- Planner -> policy -> deterministic executor -> observed state. Keep network calls out of the tick loop.
- First gather, then Jev, then LLM; do not start iron-farm logic yet.
- No API keys or user Minecraft account credentials in repository or logs.

## What is verified, and what is only claimed

Verified: 42 engine unit tests; compilation against real 26.2 + Baritone 1.19.0; the gather,
already-satisfied and user-stop paths in a real client.

Not verified in-game: every recovery path. Retry, stall timeout, task timeout, death, disconnect,
dimension change and inventory-full exist and are unit-tested against a fake executor, but have
never run against real Baritone. Do not describe them as working.

## Decisions and current work

- Fabric client integration; an independent Java task engine allows a later version adapter without rewriting control logic.
- Gather count means an inventory target, including existing stacks; this matches Baritone quantity semantics.
- Commands: `/famulus gather <item> <count>`, `/famulus status`, `/famulus stop`.
- Upstream Baritone v1.19.0 supports Minecraft 26.2. Pin the official API Fabric artifact; inspect metadata and checksums before use.
- Java 25.0.3 is installed. No system Gradle or Maven executable was found.
- Network downloads need the execution tool's network escalation in this environment. Keep Gradle user cache inside `.cache/gradle`.
- `./gradlew :fabric:runClientGameTest` exits 248 on success because of an upstream Baritone shutdown defect. Run `./scripts/run-client-gametest.sh` instead; see `BUGS.md`.

## Relevant paths

- `core/`: typed tasks, observations, results, bounded recovery, policy seam, deterministic tests.
- `fabric/`: Minecraft state collection, commands and Baritone API adapter.
- `scripts/`: `fetch-baritone.sh` pins and checksums the dependency, `run-client-gametest.sh` runs and classifies client acceptance.
- `docs/`: dependency evidence, the acceptance procedure, and archived run evidence under `docs/acceptance/`.
- `examples/`: the structured task shape. No code parses it; it documents the planner boundary.

## Next work

1. Decide whether Jev is real and reachable. Its model identity, API contract and latency budget are still unknown, and nothing should be built against a guess.
2. Exercise a recovery path in a real client, so the retry and timeout logic stops being unverified. Breaking Baritone's path mid-task is the cheapest way in.
3. Put the project under version control. Nothing here is committed yet.
4. Extend `GatherCatalog` only alongside the drop and tool rules a wider set needs.
5. Decide the exact 1.21.x target before writing a second adapter.

## Sources checked

- https://github.com/cabaletta/baritone/releases/tag/v1.19.0
- https://fabricmc.net/2026/06/15/262.html

## Unresolved

Jev model/service identity, its API contract and latency budget; planner provider; supported gather
item scope beyond direct block drops; exact 1.21.x target. None blocks the current prototype.

## Environment warning

The `.git` directory at the workspace root is a stub containing only `info/`, so **this project is
not under version control** and no work here is committed. Initialize a repository inside
`Famulus/` before relying on history. `.gitignore` is already written for it.
