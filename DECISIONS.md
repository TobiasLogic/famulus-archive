# Decisions

## 2026-09-19 — Fabric and exact version adapters

Use Fabric for Minecraft 26.2 and upstream Baritone v1.19.0. Keep a pure Java task engine separate from Minecraft classes. Future 1.21.x support requires its own tested adapter/dependency pins, and remains undecided.

## 2026-09-19 — First milestone before model APIs

Implement gather deterministically before adding Jev or an LLM. Keep typed policy/planner seams, but make no API calls and invent no Jev endpoint.

## 2026-09-19 — Inventory target semantics

`gather item N` means the player should hold at least N of that item. Existing stacks count. Inventory is authoritative; Baritone finishing is not proof of successful collection.

## 2026-09-19 — Name and license

The project is named **Famulus**, Latin for attendant or servant. The earlier working name BariModel
described the dependency rather than the project, and would have aged badly once execution stops
being Baritone-only. Java packages are `dev.famulus.*`, the mod id is `famulus`, and the command is
`/famulus`.

Licensed LGPL-3.0, matching Baritone. Famulus calls Baritone's public API as a separate dependency
and copies no upstream source, so a permissive license was available; staying aligned with upstream
was preferred instead.

## 2026-09-19 — Jev is a real model, and what that implies

Jev is `typesafe/jev-1.13`, a TypeSafe "System One" structured decision model, called through
OpenRouter at `POST /api/alpha/decisions`. It was verified by direct API calls rather than assumed;
`docs/JEV.md` records the contract and the measurements.

It was chosen for the policy layer because its option set is the schema. A `choice` question can
only return one of the enumerated actions, so the valid action set is enforced by the model's output
type instead of by asking a text model to behave. That property is the reason this layer exists.

Measured median latency is 0.78 s, roughly 15 Minecraft ticks, so Jev runs at task boundaries and
off-thread, never in the tick loop. At $0.000026 per call cost is not a design constraint; latency
and determinism are. Where a deterministic check answers the question, the deterministic check wins,
so Jev is never asked to confirm arithmetic the engine already performs.

Confidence is used for escalation to the planner. The thresholds are deliberately not fixed yet,
because guessing them once and treating them as settled would be worse than tuning them against
real runs.
