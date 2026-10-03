package example;

import dev.catac.CatAC;
import dev.catac.api.*;
import dev.catac.check.*;
import dev.catac.config.*;
import dev.catac.state.PlayerData;

import net.minestom.server.event.player.PlayerPacketEvent;

/**
 * Compilation and runtime smoke test for the actual installed JAR, outside the core source tree.
 */
public final class Consumer {
    private Consumer() {}

    public static CatAC install() {
        return CatAC.builder()
                .config(
                        CatACConfig.builder()
                                .enforcementMode(EnforcementMode.MONITOR)
                                .checkMode("combat.ray", EnforcementMode.MONITOR)
                                .traceCapacity(16)
                                .build())
                .addCheck(new HostObserver())
                .build()
                .start();
    }

    public static final class HostObserver implements PacketCheck {
        public CheckDescriptor descriptor() {
            return new CheckDescriptor(
                    "host.observer",
                    "Host observer",
                    CheckCategory.PACKET,
                    CheckPolicy.standard(2, 4, 8),
                    false,
                    CheckCapabilities.OBSERVE);
        }

        public CheckResult evaluate(PlayerPacketEvent event, PlayerData data, long nowNanos) {
            return CheckResult.skip();
        }
    }
}
