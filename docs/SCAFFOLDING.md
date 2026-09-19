# Baritone and building off the ground

Measured 2026-09-19 against the pinned Baritone 1.19.0. Evidence in `docs/probes/2026-09-19-scaffolding/`.

## The question

Baritone has a long-standing reputation for being unable to build structures that do not rest on the
ground, because placing a block in vanilla Minecraft needs an existing face to place against, and a
position with no solid neighbour cannot be filled without a temporary supporting block first.

## What was actually measured

`fabric/src/gametest/java/dev/famulus/fabric/ScaffoldingProbeGameTest.java` builds the same 3x3
cobblestone platform twice in a flat survival world:

- **Control**, resting on the ground, proving the harness, the inventory and the builder all work.
- **Treatment**, five blocks up with nothing beneath it.

Both used `IBuilderProcess.build(name, ISchematic, Vec3i)` with an in-code schematic, so no file
format is involved. The probe is opt-in:

```bash
FAMULUS_PROBE_SCAFFOLDING=1 GRADLE_USER_HOME=.cache/gradle ./gradlew :fabric:runClientGameTest
```

## Result

```text
[FamulusProbe] RESULT grounded=9/9 floating=9/9
[FamulusProbe] scaffoldBlocksLeftBehind=5
```

**Baritone built the floating platform successfully.** The reputation, stated as "it cannot build off
the ground", is not accurate for this version and this case.

What it did instead is pillar up underneath, which is ordinary movement behaviour: Baritone places
blocks below itself to ascend. That pillar then incidentally provides the face needed to place the
first platform block, and the remaining eight chain off it.

**It left the pillar behind.** Five stray cobblestone blocks remained in the 3x3 column beneath the
finished platform. The screenshot `0006_famulus-scaffolding-floating.png` shows the player standing
on the result, and the structure is visibly larger than the 3x3 that was requested.

## Why this still matters

For a solid build, leftover scaffolding is cosmetic. For the structures this project is aimed at it
is not:

- A flying machine stops working if a stray block obstructs the pistons or slime blocks.
- A redstone farm mis-fires if an extra block changes what an observer or comparator sees.
- Any mob farm with a spawning platform gets its spawn area altered by unrequested blocks.

So the accurate statement is not "Baritone cannot build off the ground". It is **Baritone does not
manage its supporting blocks: it places them as a side effect of movement and never removes them.**
That is the defect worth fixing, and it is a narrower and more tractable problem.

## What was not tested

This probe used an easy case. A 3x3 platform five blocks up is reachable by a pillar that naturally
lands directly beneath a target position. The following remain unknown and should not be assumed:

- A position where no pillar can land adjacent to it, for example a single block far out over a void
  or beyond a ledge.
- Large horizontal spans with no support, where Baritone would need a bridge rather than a pillar.
- Whether `skipFailedLayers` silently abandons layers that cannot be reached, leaving a partial
  build reported as finished.
- Blueprints containing blocks that cannot support anything, such as torches or rails, as the first
  block of a floating section.

Baritone 1.19.0 exposes **no scaffolding setting**. All 230 settings are listed in
`docs/probes/2026-09-19-scaffolding/baritone-settings.txt`; the nearest are `buildInLayers`,
`layerOrder`, `startAtLayer` and `skipFailedLayers`, the last of which exists precisely because
layers do fail.

## Proposed fix, not yet implemented

This can be solved inside Famulus without forking Baritone, because `ISchematic.desiredState` may
return air, and Baritone's builder treats a wanted-air position as something to **break**. Cleanup is
therefore expressible in Baritone's own API.

1. Snapshot the build region plus a margin below and around it before building.
2. Build normally.
3. Diff the region against the blueprint: any position that is non-air but not wanted is stray.
4. Remove the strays with a cleanup pass, either a wrapping schematic that requests air at exactly
   those positions or `IBuilderProcess.clearArea` over a computed box.

Open questions for whoever implements it:

- Removing a pillar the player is standing on. Work top-down, or let Baritone path off first.
- Distinguishing our scaffolding from blocks that were already there. The pre-build snapshot is what
  makes that possible, so it must be taken before the builder starts, not inferred afterwards.
- Doing this per layer for tall builds, rather than accumulating scaffolding for the whole structure.

A stronger variant is to place scaffolding deliberately rather than relying on Baritone's pillaring:
compute unsupported positions from the blueprint, build a support schematic first, then the real one,
then remove the support. That is more work and should only be attempted if the cleanup approach turns
out to be insufficient, which is a question for measurement rather than speculation.
