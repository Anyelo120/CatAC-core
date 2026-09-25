package dev.catac.api;

/**
 * Lifecycle state of a {@link dev.catac.CatAC} instance.
 *
 * <p>A stopped instance is terminal. Build a new instance to install CatAC
 * again after a controlled shutdown.</p>
 */
public enum CatACState {
    NEW,
    STARTED,
    STOPPED
}
