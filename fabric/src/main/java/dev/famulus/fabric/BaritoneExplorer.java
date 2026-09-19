package dev.famulus.fabric;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.minecraft.client.Minecraft;

/**
 * Ranges outward looking for resources that are not near the player.
 *
 * <p>Baritone's mine process does not give up when a target block is absent: it stays active
 * searching what it can already see, so the task engine only ever sees a stall. Exploring loads new
 * area so a later attempt has something to find. Whether that worked is decided by the inventory
 * afterwards, never by the search itself.
 *
 * <p>Ownership is tracked exactly as {@link BaritoneGatherExecutor} does, so a user's own Baritone
 * process is never cancelled by this class.
 */
public final class BaritoneExplorer {
    private boolean ownsExploration;

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /** Explores outward from the player's current column. */
    public void start(Minecraft client) {
        if (client.player == null) {
            throw new IllegalStateException("Cannot explore without a player");
        }
        int centerX = client.player.blockPosition().getX();
        int centerZ = client.player.blockPosition().getZ();
        ownsExploration = true;
        baritone().getExploreProcess().explore(centerX, centerZ);
    }

    public boolean isActive() {
        return ownsExploration && baritone().getExploreProcess().isActive();
    }

    public void cancel() {
        if (!ownsExploration) {
            return;
        }
        IBaritone api = baritone();
        api.getExploreProcess().onLostControl();
        // Only stop pathing if nothing else took over, so a user's own task keeps running.
        if (!api.getMineProcess().isActive() && !api.getBuilderProcess().isActive()
                && !api.getCustomGoalProcess().isActive()) {
            api.getPathingBehavior().cancelEverything();
        }
        ownsExploration = false;
    }
}
