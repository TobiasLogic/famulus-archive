# Containers, deposits and shulkers

Researched 2026-09-19 against Minecraft 26.2 and Baritone 1.19.0. **Nothing here is implemented
yet.** This is the contract the `DEPOSIT_ITEM` work should be written against, recorded so the next
session does not have to rediscover it.

## Baritone cannot help with this

Baritone's processes are mine, build, farm, explore, follow, getToBlock, elytra and custom goal.
**There is no container process.** It will path to a chest for you with `getToBlock`, and that is the
end of its usefulness here. Opening a container and moving items is ours to write.

## Why shulker boxes are the interesting case

The current ceiling is an inventory: `/famulus collect` refuses any blueprint needing more than 2304
of an item, because there is nowhere to put the overflow. A chest fixes that only if a chest exists
somewhere reachable.

A shulker box **keeps its contents when broken**. So an agent carrying empty shulkers can place one,
move the overflow into it, break it, and pick it back up, with no base and no prepared storage. That
turns an inventory limit into a carrying-capacity limit.

A shulker is therefore not a special case: it is a chest you brought with you. Write one container
interaction layer and both `DEPOSIT_ITEM` into a chest and shulker overflow fall out of it.

## The API

| Step | Call |
| --- | --- |
| Reach a container | `IGetToBlockProcess.getToBlock(Block)` |
| Place a shulker | `MultiPlayerGameMode.useItemOn(LocalPlayer, InteractionHand, BlockHitResult)` |
| Open it | the same `useItemOn` against the placed block |
| Move a stack | `MultiPlayerGameMode.handleContainerInput(int containerId, int slotId, int button, ContainerInput, Player)` |
| Close | `LocalPlayer.closeContainer()` |
| Break it | `MultiPlayerGameMode.destroyBlock(BlockPos)` |

`handleContainerInput` is 26.2's replacement for the older `handleInventoryMouseClick`, and
`ContainerInput` replaces `ClickType`. Its quick-move form is the shift-click behaviour, which moves
a whole stack in one call and is far less error prone than simulating pick-up and put-down.

The open container's menu is `LocalPlayer.containerMenu`, whose `containerId` is what
`handleContainerInput` needs, and whose `slots` list says which slots belong to the container rather
than the player's own inventory.

## Rules this must follow

- **Verify by observation, as everywhere else.** A deposit succeeded when the item count in the
  player's inventory actually dropped, not when a click was sent. The server may reject a click.
- **Placement can fail.** There may be no valid face, the space may be occupied, or the player may
  be too far. Report `BLOCKED` with a reason rather than retrying blindly.
- **Breaking a shulker you just filled is the dangerous step.** If the pick-up fails the items are
  on the ground and the plan must notice, because silently losing a full shulker is the worst
  outcome available here.
- **Never open a container the user opened.** Track ownership the way `BaritoneGatherExecutor` and
  `BaritoneExplorer` already do.
- Container screens are a client screen. Opening one while the Famulus panel is open, or while the
  user is typing, needs care.

## Suggested order

1. `DEPOSIT_ITEM` into a chest the player is already standing near. Smallest useful step, and it
   completes milestone 2 properly.
2. Travel to a known chest with `getToBlock`, then deposit.
3. Shulker overflow: place, fill, break, collect, with the loss case handled explicitly.
4. Only then lift the 2304 limit in `/famulus collect`.
