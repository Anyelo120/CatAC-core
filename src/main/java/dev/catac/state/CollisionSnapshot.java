package dev.catac.state;

public final class CollisionSnapshot {
    private boolean complete;
    private boolean supported;
    private boolean insideSolid;
    private boolean sweptIntoSolid;
    private boolean touchingLiquid;
    private boolean touchingClimbable;
    private boolean touchingSlowBlock;
    private float surfaceFriction;
    private float surfaceSpeedFactor;

    public void reset() {
        complete = true;
        supported = false;
        insideSolid = false;
        sweptIntoSolid = false;
        touchingLiquid = false;
        touchingClimbable = false;
        touchingSlowBlock = false;
        surfaceFriction = 0.6f;
        surfaceSpeedFactor = 1.0f;
    }

    public boolean complete() {
        return complete;
    }

    public void complete(boolean complete) {
        this.complete = complete;
    }

    public boolean supported() {
        return supported;
    }

    public void supported(boolean supported) {
        this.supported = supported;
    }

    public boolean insideSolid() {
        return insideSolid;
    }

    public void insideSolid(boolean insideSolid) {
        this.insideSolid = insideSolid;
    }

    /** True when an intermediate AABB in the movement path touched a solid shape. */
    public boolean sweptIntoSolid() { return sweptIntoSolid; }
    public void sweptIntoSolid(boolean sweptIntoSolid) { this.sweptIntoSolid = sweptIntoSolid; }

    public boolean touchingLiquid() {
        return touchingLiquid;
    }

    public void touchingLiquid(boolean touchingLiquid) {
        this.touchingLiquid = touchingLiquid;
    }

    public boolean touchingClimbable() {
        return touchingClimbable;
    }

    public void touchingClimbable(boolean touchingClimbable) {
        this.touchingClimbable = touchingClimbable;
    }

    public boolean touchingSlowBlock() {
        return touchingSlowBlock;
    }

    public void touchingSlowBlock(boolean touchingSlowBlock) {
        this.touchingSlowBlock = touchingSlowBlock;
    }

    public float surfaceFriction() {
        return surfaceFriction;
    }

    public void surfaceFriction(float surfaceFriction) {
        this.surfaceFriction = surfaceFriction;
    }

    public float surfaceSpeedFactor() {
        return surfaceSpeedFactor;
    }

    public void surfaceSpeedFactor(float surfaceSpeedFactor) {
        this.surfaceSpeedFactor = surfaceSpeedFactor;
    }
}
