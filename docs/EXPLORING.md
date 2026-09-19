# Exploring for a resource that is not nearby

Measured 2026-09-19 against Baritone 1.19.0. Evidence in `.cache/explore-probe*.log` and the probe at
`fabric/src/gametest/java/dev/famulus/fabric/ExploreProbeGameTest.java`.

## Why this exists

Baritone's mine process **does not give up when a block type is absent**. With every oak log removed
from the world it emitted a single "unable to find any path" and kept its process active anyway, so
`isActive()` never went false. Only the engine's 60 second stall timeout caught it, three times over,
taking 2m13s to fail. See `BUGS.md`.

The consequence is that retrying a gather in the same place finds the same nothing. The only useful
recovery is to go somewhere else, which is why `EXPLORE` is offered to the policy layer.

## What was measured

A superflat world has no sand at any distance, so a patch was placed deliberately at (260, 260),
about 368 blocks from spawn. A control gather confirmed it was genuinely out of reach first, since
without that a later failure would be meaningless.

**Exploring works.** The process engaged immediately and stayed active, and the player covered
hundreds of blocks under its control.

**The gather still failed.** `control=0 afterExploring=0`.

## Why it failed, and what that means

The first version of this probe measured **distance from spawn**, which was the wrong metric. It
showed the player reaching 259 blocks and looked like progress, but exploring is undirected: 259
blocks from spawn is usually 259 blocks the wrong way. The probe now measures distance to the sand.

So the honest reading is not "exploring is broken". It is:

> Baritone's explore is an undirected outward search. It is the right tool for "I have no idea what
> is around me". It is a poor tool for "find me sand specifically", because finding one patch in one
> direction is luck, and a bounded exploration is a random walk with a time limit.

## What to do about it

Options, roughly in order of promise. None are implemented yet.

1. **`IGetToBlockProcess.getToBlock(Block)`.** Pathfinds to the nearest instance Baritone knows
   about, including from its chunk cache. This is directed rather than random, and is probably the
   right primary strategy whenever the resource has ever been seen.
2. **Explore, then retry, repeatedly.** The agent already does one round of this. Several shorter
   rounds with a gather attempt between them would convert the random walk into repeated sampling,
   which at least gets more chances.
3. **Biome-aware targeting.** Sand means a desert or a beach. Choosing a direction from world data
   rather than wandering would beat both of the above, and is the most work.
4. **`exploreForBlocks`.** A Boolean setting, not a target list, so it is not the aimed search its
   name suggests. Worth testing but do not expect it to solve this.

## Honest status

`EXPLORE` is implemented, unit tested, and confirmed to engage Baritone and move the player. It has
**not** been shown to make a specific distant resource obtainable, and on the evidence so far a blind
exploration is unlikely to do so reliably. Treat it as a way to see more of the world, not as a
solution to "go and find me this block".
