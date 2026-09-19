# Famulus

An incremental Minecraft agent: a future LLM planner creates structured tasks, a future Jev policy
selects valid actions, and Baritone performs deterministic execution.

**Target: Minecraft Java 26.2, Fabric, Java 25.** Support for a specific 1.21.x release is
undecided. Work lives entirely in this directory.

## Status

Milestone 1 is done and verified in a real client. `/famulus gather minecraft:oak_log 32` reaches at
least 32 oak logs in the player's inventory; existing items count toward the goal. On 2026-09-19 it
was observed going from 0 to 32 logs in 25 seconds in one attempt, declining to mine when the target
was already met, and cancelling cleanly on `/famulus stop`. Evidence is in
[docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

This is a gather prototype, not an autonomous farm builder. Jev and LLM calls are deliberately
deferred until the deterministic path is validated, and no endpoint has been invented for either.

## Commands

| Command | Effect |
| --- | --- |
| `/famulus gather <item> <count>` | Hold at least `count` of `item`. Tab completion lists supported items. |
| `/famulus status` | Current status, inventory progress and attempt count. |
| `/famulus stop` | Cancel the running task and the Baritone work it started. |

Requires survival mode and a supported item. Anything else is refused with a reason rather than
half-attempted.

## Building and testing

```bash
./scripts/fetch-baritone.sh                                  # pinned, checksummed dependency
GRADLE_USER_HOME=.cache/gradle ./gradlew build               # compile and unit test
./scripts/run-client-gametest.sh                             # client acceptance
```

Install the matching Baritone API Fabric jar alongside Famulus and Fabric API in the same
instance. `docs/DEPENDENCIES.md` explains which Baritone distribution is correct and why.

Read [HANDOFF.md](HANDOFF.md) first when continuing development, and [BUGS.md](BUGS.md) before
trusting a red build: the client test's gradle task exits non-zero even when every assertion passes,
for a reason that belongs to Baritone rather than this project.

## Name

*Famulus* is Latin for an attendant or servant, and the root of "familiar". It names what the
project is rather than any one task: an agent that acts for the player. Gathering is simply the
first thing it was taught to do.

## License

Famulus is licensed under the GNU Lesser General Public License v3.0. See [LICENSE](LICENSE), which
incorporates the terms of the GNU GPL v3.0 in [LICENSE.GPL](LICENSE.GPL).

Baritone is a separate LGPL-3.0 dependency and is **not** redistributed here. `libs/*.jar` is
ignored by git, and `scripts/fetch-baritone.sh` downloads the pinned upstream artifact and verifies
its checksum. Famulus calls Baritone's public API; it copies none of its source.
