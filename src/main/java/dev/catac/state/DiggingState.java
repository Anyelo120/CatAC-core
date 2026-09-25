package dev.catac.state;

public final class DiggingState {
    private int blockX;
    private int blockY;
    private int blockZ;
    private int expectedTicks;
    private long startedNanos;
    private boolean active;

    public void start(int blockX, int blockY, int blockZ, int expectedTicks, long nowNanos) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.expectedTicks = expectedTicks;
        this.startedNanos = nowNanos;
        this.active = true;
    }

    public boolean matches(int blockX, int blockY, int blockZ) {
        return active && this.blockX == blockX && this.blockY == blockY && this.blockZ == blockZ;
    }

    public int expectedTicks() {
        return expectedTicks;
    }

    public long startedNanos() {
        return startedNanos;
    }

    public boolean active() {
        return active;
    }

    public void clear() {
        active = false;
        expectedTicks = 0;
        startedNanos = 0;
    }
}
