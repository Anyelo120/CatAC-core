package dev.catac.testing;

/** Primitive hostile-boundary case for a server adapter to turn into actual packets. */
public record PacketFuzzCase(double x, double y, double z, float cursorX, float cursorY, float cursorZ,
                             int windowId, short slot) {
    public boolean hasUnsafeCoordinate() {
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) ||
                Math.abs(x) > 30_000_000.0 || Math.abs(y) > 30_000_000.0 || Math.abs(z) > 30_000_000.0;
    }

    public boolean hasUnsafeCursor() {
        return !Float.isFinite(cursorX) || !Float.isFinite(cursorY) || !Float.isFinite(cursorZ) ||
                cursorX < 0.0f || cursorX > 1.0f || cursorY < 0.0f || cursorY > 1.0f || cursorZ < 0.0f || cursorZ > 1.0f;
    }
}
