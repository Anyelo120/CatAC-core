package dev.catac.api;

/** Capabilities are independent: packet cancellation is not movement correction. */
public record CheckCapabilities(
        boolean cancelAction, boolean correctMovement, boolean kick, boolean hardening) {
    public static final CheckCapabilities OBSERVE =
            new CheckCapabilities(false, false, false, false);
    public static final CheckCapabilities PACKET = new CheckCapabilities(true, false, true, false);
    public static final CheckCapabilities MOVEMENT = new CheckCapabilities(true, true, true, false);
    public static final CheckCapabilities CORRECT_ONLY =
            new CheckCapabilities(true, true, false, false);
    public static final CheckCapabilities PROTOCOL = new CheckCapabilities(true, false, true, true);

    public CheckCapabilities {
        if (correctMovement && !cancelAction)
            throw new IllegalArgumentException("Correction requires cancellation");
        if (hardening && !cancelAction)
            throw new IllegalArgumentException("Hardening must reject unsafe actions");
    }
}
