import dev.catac.CatAC;
import dev.catac.api.EnforcementMode;
import dev.catac.config.CatACConfig;
import net.minestom.server.MinecraftServer;

public final class MinimalServerIntegration {
    private MinimalServerIntegration() {
    }

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();

        CatACConfig config = CatACConfig.builder()
                .enforcementMode(EnforcementMode.MONITOR)
                .violationHandler(event -> System.out.printf(
                        "[CatAC] player=%s check=%s buffer=%.2f action=%s evidence=%s%n",
                        event.player().getUsername(), event.check().id(), event.buffer(),
                        event.action(), event.evidence()))
                .build();

        CatAC catac = CatAC.install(config);
        Runtime.getRuntime().addShutdownHook(new Thread(catac::close, "catac-shutdown"));

        // Configure instances and player spawning here.
        server.start("0.0.0.0", 25565);
    }
}
