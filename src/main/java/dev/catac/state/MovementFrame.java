package dev.catac.state;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

public final class MovementFrame {
    private Player player;
    private Pos from;
    private Pos to;
    private double deltaX;
    private double deltaY;
    private double deltaZ;
    private double horizontalDistance;
    private boolean clientOnGround;
    private long nowNanos;
    private long sequence;
    private CollisionSnapshot collision;

    public void reset(Player player, Pos from, Pos to, boolean clientOnGround,
                      long nowNanos, long sequence, CollisionSnapshot collision) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.deltaX = to.x() - from.x();
        this.deltaY = to.y() - from.y();
        this.deltaZ = to.z() - from.z();
        this.horizontalDistance = Math.hypot(deltaX, deltaZ);
        this.clientOnGround = clientOnGround;
        this.nowNanos = nowNanos;
        this.sequence = sequence;
        this.collision = collision;
    }

    public Player player() { return player; }
    public Pos from() { return from; }
    public Pos to() { return to; }
    public double deltaX() { return deltaX; }
    public double deltaY() { return deltaY; }
    public double deltaZ() { return deltaZ; }
    public double horizontalDistance() { return horizontalDistance; }
    public boolean clientOnGround() { return clientOnGround; }
    public long nowNanos() { return nowNanos; }
    public long sequence() { return sequence; }
    public CollisionSnapshot collision() { return collision; }
}
