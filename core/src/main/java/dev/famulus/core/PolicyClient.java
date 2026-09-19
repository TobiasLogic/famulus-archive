package dev.famulus.core;

/**
 * The decision boundary. Implementations perform network calls and therefore must never be
 * invoked from the Minecraft client thread; see ARCHITECTURE.md.
 */
public interface PolicyClient {
    /**
     * @throws PolicyException when no trustworthy decision could be obtained. Callers treat this
     *                         as a reason to fall back deterministically, never as a decision.
     */
    PolicyDecision decide(PolicyRequest request) throws PolicyException;
}
