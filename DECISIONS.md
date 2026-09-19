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
