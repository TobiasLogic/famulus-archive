package dev.famulus.core;

/**
 * The complete set of actions the policy layer may choose from. A policy never invents an action:
 * a returned name that is not in this enum is rejected rather than interpreted.
 *
 * <p>Most of these have no executor yet. {@link #isExecutable()} is the authority on what can
 * actually be dispatched today, so an unimplemented choice fails loudly instead of silently
 * doing nothing.
 */
public enum AgentAction {
    GATHER(true),
    MINE(false),
    CRAFT(false),
    TRAVEL(false),
    BUILD(false),
    PLACE_BLOCK(false),
    INTERACT(false),
    DEPOSIT_ITEM(false),
    WITHDRAW_ITEM(false),
    WAIT(true),
    VERIFY(true),
    RECOVER(true),
    COMPLETE_TASK(true),
    REQUEST_REPLAN(true),
    ABORT_TASK(true);

    private final boolean executable;

    AgentAction(boolean executable) {
        this.executable = executable;
    }

    /** False while no executor exists for this action. */
    public boolean isExecutable() {
        return executable;
    }

    /** Parse strictly. Returns null for anything unrecognised; callers must not guess. */
    public static AgentAction parse(String name) {
        if (name == null) {
            return null;
        }
        for (AgentAction action : values()) {
            if (action.name().equalsIgnoreCase(name.trim())) {
                return action;
            }
        }
        return null;
    }
}
