package dev.catac.check.builtin;

import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.CheckPolicy;
import dev.catac.internal.*;
import dev.catac.state.*;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.*;

import java.util.Set;

/** Status-only ground claims do not emit PlayerMoveEvent in the pinned Minestom. */
public final class GroundStatusCheck implements PacketCheck {
    private static final CheckDescriptor D =
            new CheckDescriptor(
                    "packet.ground-status",
                    "Ground status",
                    CheckCategory.PACKET,
                    new CheckPolicy(true, 5, 10, 30, .2, 1200),
                    false,
                    CheckCapabilities.PACKET);
    private final CollisionAnalyzer analyzer;

    public GroundStatusCheck(CollisionAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public CheckDescriptor descriptor() {
        return D;
    }

    public Set<Class<? extends ClientPacket>> packetTypes() {
        return Set.of(ClientPlayerPositionStatusPacket.class, ClientPlayerRotationPacket.class);
    }

    public CheckResult evaluate(PlayerPacketEvent e, PlayerData d, long now) {
        boolean ground;
        if (e.getPacket() instanceof ClientPlayerPositionStatusPacket p) ground = p.onGround();
        else if (e.getPacket() instanceof ClientPlayerRotationPacket p) ground = p.onGround();
        else return CheckResult.skip();
        if (!ground) return CheckResult.pass();
        if (d.synchronization().movementUncertain(now) || d.exemptions().movementExempt(now))
            return CheckResult.uncertain(SkipReason.SYNCHRONIZING);
        CollisionSnapshot s = new CollisionSnapshot();
        analyzer.analyze(e.getPlayer(), e.getPlayer().getPosition(), s);
        if (MovementExemptions.physicalBypass(e.getPlayer(), s))
            return CheckResult.uncertain(SkipReason.UNMODELED);
        return !s.supported() && d.airFrames() > 2
                ? CheckResult.cancel(1, "status claimed ground without support")
                : CheckResult.pass();
    }
}
