# Changelog

## 0.1.0 — Unreleased

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
