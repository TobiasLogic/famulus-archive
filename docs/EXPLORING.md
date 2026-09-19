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

A superflat world has no sand at any distance, so a patch was placed deliberately at (260, 260).
The probe runs three checks, and all three are needed:

| Check | Purpose | Result |
| --- | --- | --- |
| **Positive control**: sand 5 blocks away | Prove gathering sand works at all | **8/8**, twice |
| **Negative control**: distant sand, no exploring | Prove it is genuinely out of reach | **0** |
| **Treatment**: explore, then gather | Does exploring bridge the gap | **0** |

The positive control is the one that makes the rest mean anything. Without it a final zero looks
identical whether exploring failed to help or gathering sand never worked in the first place.

**Exploring works, in the sense that it engages and moves.** The process stayed active throughout
and the player covered hundreds of blocks under its control.

**It did not find the sand**, across two runs with a 60s and a 240s budget.

## Why it failed, and what that means

Three versions of this probe were wrong before it measured anything trustworthy, and each failure
looked like a result. It waited for exploring to become inactive, which never happens. It measured
distance from **spawn** rather than to the target, so wandering the wrong way read as progress. It
had no positive control, so a zero was unattributable. It also counted sand dropped by the positive
control, which made the negative control read 3 instead of 0.

With the metric corrected, two runs give **opposite trends**:

- 60s budget: 326 to 189 blocks from the sand, closing steadily.
- 240s budget: 529 out to **645**, then back to 337. Target was 260 blocks out.

One run trending toward the target was not evidence of direction; it was luck, and reading it as
convergence was a mistake. Taken together the two runs are what an undirected search looks like.

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

`EXPLORE` is implemented, unit tested, and confirmed to engage Baritone and move the player a long
way. It has **not** been shown to make a specific distant resource obtainable, and on two runs with
opposite trajectories it should not be expected to. Treat it as a way to see more of the world, not
as a solution to "go and find me this block".

Gathering itself is fine: the positive control collected 8 of 8 from sand five blocks away, twice.
Nothing measured here indicates a problem in the gather engine.
