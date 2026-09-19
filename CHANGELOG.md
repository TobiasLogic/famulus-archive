# Changelog

## 0.1.0 — Unreleased

- **The LLM planner exists.** A Chat tab takes a sentence and produces a validated task plan, which
  can then be run. Verified against a real model: "build a small wooden shelter, get me the wood and
  some dirt" returned two executable gather tasks, and a request for diamonds was refused rather
  than turned into work that would fail.
- **Model selector** in Settings: a model field, an endpoint field, and presets for OpenRouter,
  Ollama and llama.cpp. Any OpenAI-compatible server works, and a local one needs no API key.
- `PlanParser` rejects anything a model gets wrong: prose, code fences, unknown task types, missing
  namespaces, absurd counts, unobtainable items, duplicate ids, and actions with no executor.
  24 tests on the planner module alone.
- **The policy layer now fires in a real client.** With every log removed, the gather exhausted its
  attempts and Jev was consulted in game, returning `REQUEST_REPLAN` at replan urgency 0.84. This
  was the last unverified link in the architecture.
- Added the `EXPLORE` action and `BaritoneExplorer`. When a resource is not nearby, retrying in the
  same spot finds the same nothing, so the policy can choose to range outward and then retry.
  Exploring counts as an attempt, so it cannot wander forever.
- Measured that Baritone keeps its mine process active when a block type is absent; only the stall
  timeout catches it. See `BUGS.md`.
- Measured what exploring actually achieves, and it is less than hoped: it engages and moves the
  player hundreds of blocks, but did not make a patch of sand 368 blocks away obtainable. Blind
  exploration is undirected. `docs/EXPLORING.md` records the result and the better options.
- **In-game control panel**, opened with **G**. Agent, Build and Settings tabs, built on vanilla's
  `MenuTabBar`. All three are photographed by the client test, because a compile proves nothing
  about a GUI.
- **API keys can be set in game.** `CredentialStore` keeps them in their own owner-only file, away
  from ordinary settings, masked on screen, never logged, and overridable by the environment.
  Saving a key reloads the policy client without restarting Minecraft.
- Measured which schematic formats Baritone actually registers: **litematic, schem, schematic**. A
  Litematica file needs no Litematica mod.
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
