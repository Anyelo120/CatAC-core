package dev.catac.state;

import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;

/** A digging operation is tied to world, block state, tool and timing context. */
public final class DiggingState {
    private int x, y, z, ticks, blockState;
    private long started;
    private boolean active;
    private Instance instance;
    private ItemStack tool;

    public void start(int x, int y, int z, int ticks, long now) {
        start(x, y, z, ticks, null, 0, null, now);
    }

    public void start(
            int x,
            int y,
            int z,
            int ticks,
            Instance instance,
            int blockState,
            ItemStack tool,
            long now) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ticks = ticks;
        this.instance = instance;
        this.blockState = blockState;
        this.tool = tool;
        started = now;
        active = true;
    }

    public boolean matches(int x, int y, int z) {
        return active && this.x == x && this.y == y && this.z == z;
    }

    public boolean contextMatches(Instance world, int block, ItemStack tool) {
        return active
                && instance == world
                && blockState == block
                && java.util.Objects.equals(this.tool, tool);
    }

    public int expectedTicks() {
        return ticks;
    }

    public long startedNanos() {
        return started;
    }

    public boolean active() {
        return active;
    }

    public void clear() {
        active = false;
        tool = null;
        instance = null;
        ticks = 0;
    }
}
