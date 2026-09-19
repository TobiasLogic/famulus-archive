package dev.famulus.core;

/** A decision could not be obtained or could not be trusted. Never thrown to signal a choice. */
public class PolicyException extends Exception {
    public PolicyException(String message) {
        super(message);
    }

    public PolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
