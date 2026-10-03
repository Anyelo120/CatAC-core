package dev.catac.state;

import static org.junit.jupiter.api.Assertions.*;

import net.minestom.server.coordinate.Vec;

import org.junit.jupiter.api.Test;

class SynchronizationRegressionTest {
    @Test
    void aPreviousTeleportIdCannotConfirmANewTeleport() {
        var t = new TeleportTracker(1000);
        t.markPending(-1000, 7);
        t.captureExpectedId(7);
        assertFalse(t.acknowledge(7, -900));
        assertTrue(t.pending(-900));
        t.captureExpectedId(8);
        assertFalse(t.acknowledge(7, -800));
        assertTrue(t.acknowledge(8, -800));
        assertFalse(t.pending(-800));
    }

    @Test
    void expiredTeleportAndVelocityOutcomesAreCountedOnce() {
        var t = new TeleportTracker(1000);
        t.sent(8, -1000);
        assertFalse(t.pending(0));
        assertEquals(1, t.timeouts());
        assertFalse(t.pending(10));
        assertEquals(1, t.timeouts());
        var v = new VelocityTracker(1000);
        v.markVelocity(new Vec(.4, 0, 0), -1000);
        assertEquals(0, v.pendingCount(0));
        assertEquals(1, v.timeouts());
    }

    @Test
    void newestOutgoingTeleportSupersedesTheEarlierConfirm() {
        var t = new TeleportTracker(1000);
        t.sent(8, -1000);
        t.sent(9, -900);
        assertFalse(t.acknowledge(8, -800));
        assertTrue(t.acknowledge(9, -800));
    }

    @Test
    void negativeClockDoesNotDelayTheFirstProbe() {
        var l = new LatencyTracker(1000, 2000);
        int probe = l.createProbe(-10000, false);
        assertNotEquals(LatencyTracker.NO_PROBE, probe);
        assertEquals(LatencyTracker.NO_PROBE, l.createProbe(-9999, false));
        assertTrue(l.acknowledge(probe, -9500));
        assertFalse(l.acknowledge(probe, -9000));
    }

    @Test
    void onlyTheRightPongAcknowledgesTheVelocityVector() {
        var v = new VelocityTracker(1000);
        var vector = new Vec(.4, .42, .2);
        v.markVelocity(vector, -1000);
        v.arm(12, -900);
        v.acknowledge(13, -800);
        assertEquals(1, v.pendingCount(-800));
        assertNull(v.consumeAcknowledged());
        v.acknowledge(12, -700);
        assertEquals(0, v.pendingCount(-700));
        assertEquals(vector, v.consumeAcknowledged());
        assertNull(v.consumeAcknowledged());
    }

    @Test
    void acknowledgementsCannotCreateUnlimitedPendingVelocity() {
        var v = new VelocityTracker(1000);
        for (int i = 0; i < 1000; i++) v.markVelocity(new Vec(.4, 0, 0), -1000 + i / 2);
        assertEquals(8, v.pendingCount(-400));
        assertEquals(0, v.pendingCount(1000));
    }

    @Test
    void controlReserveAndNotificationRateAreBounded() {
        var budget = new dev.catac.config.PacketBudget(1, 1);
        var f = new PacketFloodState(budget, budget, -1000);
        assertTrue(f.tryConsume(dev.catac.api.PacketCost.NORMAL, budget, budget, -1000));
        assertFalse(f.tryConsume(dev.catac.api.PacketCost.NORMAL, budget, budget, -1000));
        for (int i = 0; i < 24; i++) assertTrue(f.tryControl(-1000));
        assertFalse(f.tryControl(-1000));
        assertTrue(f.notifyAllowed(-1000, 500));
        assertFalse(f.notifyAllowed(-900, 500));
        assertTrue(f.notifyAllowed(-500, 500));
    }
}
