package example;

import static org.junit.jupiter.api.Assertions.*;

import dev.catac.CatAC;
import dev.catac.api.CatACState;

import net.minestom.server.MinecraftServer;

import org.junit.jupiter.api.Test;

class ConsumerTest {
    @Test
    void installedJarWorksWithThePinnedMinestomAndAHostCheck() {
        MinecraftServer.init();
        try (var ac = Consumer.install()) {
            assertEquals(CatACState.STARTED, ac.state());
            assertEquals(21, ac.diagnostics().size());
            assertTrue(ac.health().healthy());
            assertSame(ac, CatAC.current().orElseThrow());
        }
        assertTrue(CatAC.current().isEmpty());
    }
}
