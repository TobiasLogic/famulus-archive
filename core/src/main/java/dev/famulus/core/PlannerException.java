package dev.famulus.core;

/** No usable plan could be produced. Carries a message fit to show the user. */
public class PlannerException extends Exception {
    public PlannerException(String message) {
        super(message);
    }

    public PlannerException(String message, Throwable cause) {
        super(message, cause);
    }
}
