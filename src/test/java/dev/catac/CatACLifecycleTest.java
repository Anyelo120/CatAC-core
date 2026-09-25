package dev.catac;

import dev.catac.api.CatACState;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatACLifecycleTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @Test
    void allowsOnlyOneGlobalInstallationAndMakesStopTerminal() {
        CatAC first = CatAC.builder().build();
        CatAC second = CatAC.builder().build();

        assertSame(first, first.start());
        assertEquals(CatACState.STARTED, first.state());
        assertSame(first, CatAC.current().orElseThrow());
        assertThrows(IllegalStateException.class, second::start);

        first.close();
        assertEquals(CatACState.STOPPED, first.state());
        assertTrue(CatAC.current().isEmpty());
        assertThrows(IllegalStateException.class, first::start);

        second.start();
        second.close();
    }
}
