# Changelog

## 0.1.0 — Unreleased

- **The agent now chains tasks.** `PlanRunner` walks a `TaskPlan` and asks the policy layer when a
  task fails; `FamulusAgent` drives it in-game and consults Jev off-thread on a daemon worker.
- New commands: `/famulus queue <item>=<n>, ...` runs several gather tasks as one plan,
  `/famulus materials <file>` reports a blueprint's shortfall, and `/famulus collect <file>` gathers
  what a blueprint is missing.
- Without `OPENROUTER_API_KEY` the agent still runs and simply retries failures deterministically,
  so the policy layer is an improvement rather than a requirement.
- Measured Baritone's behaviour when building off the ground, with a control and a treatment build.
  It succeeds, contrary to its reputation, but leaves its pillar behind. `docs/SCAFFOLDING.md`.
- Added `MaterialList` and `MaterialRequirement` to `core`: a deterministic diff of what a blueprint
  needs against what is held, splitting shortfalls into gatherable and not-yet-obtainable.
- Added `SchematicAnalyzer` and `SchematicSummary`, which parse a blueprint through Baritone's own
  schematic registry and count the items it consumes. Supported extensions are queried at runtime.
- Added the policy seam in `core`: `AgentAction` (the full action set, each marked executable or
  not), `PolicyRequest`, `PolicyDecision`, `PolicyClient` and `PolicyGate`, which turns confidence
  into escalation and aborts after repeated escalation so a confused agent cannot loop forever.
- Added the `:jev` module: `JevClient` calls the real decisions endpoint and re-validates the
  returned choice against the offered options, because a network response is untrusted input.
- 27 new offline tests plus a live smoke test that is skipped without an API key. Verified against
  the real Jev API end to end.
- **Not yet wired into gameplay.** No command consults the policy layer.
- Identified **Jev** as `typesafe/jev-1.13`, a TypeSafe System One structured decision model, and
  verified its contract by direct API calls: `POST https://openrouter.ai/api/alpha/decisions`,
  median latency 0.78s, $0.000026 per call. Documented in `docs/JEV.md`. No integration code yet.
- Rewrote `ARCHITECTURE.md` around the three verified layers, marking clearly which exist.
- Named the project **Famulus**, Latin for attendant. The working name BariModel described the
  dependency rather than the project; packages are now `dev.famulus.*`, the mod id is `famulus` and
  the command is `/famulus`. Verified in a real client after the rename.
- Licensed LGPL-3.0, matching Baritone, which stays a separate and non-redistributed dependency.
- Established Famulus project scope, documentation and module boundaries for Minecraft 26.2.
- Minecraft-independent gather task engine with inventory-observed completion, bounded retries,
  an absolute task deadline and explicit handling of disconnect, death and world change.
- Fabric client adapter: `/famulus gather <item> <count>`, `/famulus status`, `/famulus stop`, tab completion
  from `GatherCatalog`, and a Baritone adapter that only cancels work it started.
- 42 task engine unit tests against a fake executor.
- Client game test driving the real commands in a real client.
- **Verified in Minecraft 26.2 on 2026-09-19:** gather reaches an observed inventory total of 32 oak
  logs, an already satisfied request does not mine, and `/famulus stop` cancels cleanly. See
  `docs/ACCEPTANCE.md`.
- `scripts/run-client-gametest.sh` runs client acceptance, archives evidence, and distinguishes the
  known upstream Baritone shutdown failure from a real one.
- `examples/gather-task.json` records the structured task shape for the future planner boundary.
